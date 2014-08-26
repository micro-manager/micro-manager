// COPYRIGHT:     University of California, San Francisco, 2014,
//                All Rights reserved
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Mark Tsuchida

#pragma once

#include "AsyncLoggingQueue.h"
#include "LogLine.h"
#include "LogSink.h"
#include "Logger.h"

#include <boost/bind.hpp>
#include <boost/enable_shared_from_this.hpp>
#include <boost/make_shared.hpp>
#include <boost/shared_ptr.hpp>
#include <boost/thread.hpp>

#include <algorithm>
#include <set>
#include <string>
#include <vector>

namespace mm
{
namespace logging
{

enum SinkMode
{
   SinkModeSynchronous,
   SinkModeAsynchronous,
};

namespace detail
{

class LogEntryMetadata;


class LoggingCore :
   public boost::enable_shared_from_this<LoggingCore>
{
public:
   typedef LoggingCore Self;
   typedef detail::LogEntryMetadata MetadataType;
   typedef detail::GenericLogger<Self> LoggerType;

private:
   typedef detail::GenericLogLine<MetadataType> LogLineType;

public:
   typedef detail::GenericLogSink<LogLineType> SinkType;

private:
   // When acquiring both syncSinksMutex_ and asyncQueueMutex_, acquire in that
   // order.

   boost::mutex syncSinksMutex_; // Protect all access to synchronousSinks_
   std::vector< boost::shared_ptr<SinkType> > synchronousSinks_;

   boost::mutex asyncQueueMutex_; // Protect start/stop and sinks change
   detail::AsyncLoggingQueue<LogLineType> asyncQueue_;
   // Changes to asynchronousSinks_ must be made with asyncQueueMutex_ held
   // _and_ the queue receive loop stopped.
   std::vector< boost::shared_ptr<SinkType> > asynchronousSinks_;

   // Never remove strings from this set until destruction. Since we only ever
   // insert into this set, iterators (and thus const char* to the contained
   // strings) are never invalidated and can be used as a light-weight handle.
   // Thus, we only need to protect insertion by a mutex.
   std::set<std::string> componentLabels_;
   boost::mutex componentLabelsMutex_;

public:
   LoggingCore() { StartAsyncReceiveLoop(); }
   ~LoggingCore() { StopAsyncReceiveLoop(); }

   /**
    * Create a new Logger instance.
    */
   boost::shared_ptr<LoggerType> NewLogger(const std::string& componentLabel)
   {
      return boost::make_shared<LoggerType>(this->shared_from_this(),
            componentLabel);
   }

   /**
    * Add a synchronous or asynchronous sink.
    */
   void AddSink(boost::shared_ptr<SinkType> sink, SinkMode mode)
   {
      switch (mode)
      {
         case SinkModeSynchronous:
         {
            boost::lock_guard<boost::mutex> lock(syncSinksMutex_);
            synchronousSinks_.push_back(sink);
            break;
         }
         case SinkModeAsynchronous:
         {
            boost::lock_guard<boost::mutex> lock(asyncQueueMutex_);
            StopAsyncReceiveLoop();
            asynchronousSinks_.push_back(sink);
            StartAsyncReceiveLoop();
            break;
         }
      }
   }

   /**
    * Remove a synchronous or asynchronous sink.
    *
    * Nothing is done if the sink is not registered with the specified
    * concurrency mode.
    */
   void RemoveSink(boost::shared_ptr<SinkType> sink, SinkMode mode)
   {
      switch (mode)
      {
         case SinkModeSynchronous:
         {
            boost::lock_guard<boost::mutex> lock(syncSinksMutex_);
            std::vector< boost::shared_ptr<SinkType> >::iterator it =
               std::find(synchronousSinks_.begin(), synchronousSinks_.end(),
                     sink);
            if (it != synchronousSinks_.end())
               synchronousSinks_.erase(it);
            break;
         }
         case SinkModeAsynchronous:
         {
            boost::lock_guard<boost::mutex> lock(asyncQueueMutex_);
            StopAsyncReceiveLoop();
            std::vector< boost::shared_ptr<SinkType> >::iterator it =
               std::find(asynchronousSinks_.begin(), asynchronousSinks_.end(),
                     sink);
            if (it != asynchronousSinks_.end())
               asynchronousSinks_.erase(it);
            StartAsyncReceiveLoop();
            break;
         }
      }
   }

   /**
    * Add and/or remove multiple sinks, atomically.
    *
    * This function should be used for example to switch files when performing
    * log rotation, because it ensures that no entries are lossed during the
    * switch.
    *
    * SinkModePairIterator should be an iterator type over
    * std::pair<boost::shared_ptr<SinkType>, SinkMode>.
    */
   template <typename SinkModePairIterator>
   void AtomicSwapSinks(SinkModePairIterator firstToRemove,
         SinkModePairIterator lastToRemove,
         SinkModePairIterator firstToAdd,
         SinkModePairIterator lastToAdd)
   {
      // Lock both sink lists in the designated order. Since locking
      // syncSinksMutex_ causes logging to block, subsequently draining the
      // async queue by stopping the receive loop causes all sinks to
      // synchronize (emit up to the same log entry).
      boost::lock_guard<boost::mutex> lockSyncs(syncSinksMutex_);
      boost::lock_guard<boost::mutex> lockAsyncQ(asyncQueueMutex_);
      StopAsyncReceiveLoop();

      // Inefficient nested loop, but good enough for this purpose.
      for (SinkModePairIterator it = firstToRemove; it != lastToRemove; ++it)
      {
         typedef std::vector< boost::shared_ptr<SinkType> > SinkListType;
         SinkListType* pSinkList = 0;
         switch (it->second)
         {
            case SinkModeSynchronous:
               pSinkList = &synchronousSinks_;
               break;
            case SinkModeAsynchronous:
               pSinkList = &asynchronousSinks_;
               break;
         }
         typename SinkListType::iterator foundIt =
            std::find(pSinkList->begin(), pSinkList->end(), it->first);
         if (foundIt != pSinkList->end())
            pSinkList->erase(foundIt);
      }

      for (SinkModePairIterator it = firstToAdd; it != lastToAdd; ++it)
      {
         switch (it->second)
         {
            case SinkModeSynchronous:
               synchronousSinks_.push_back(it->first);
               break;
            case SinkModeAsynchronous:
               asynchronousSinks_.push_back(it->first);
               break;
         }
      }

      StartAsyncReceiveLoop();
   }

   /**
    * Set log entry filters for attached sinks, atomically.
    *
    * SinkModePairFilterPairIterator must be an iterator type over
    * std::pair<
    *    std::pair<boost::shared_ptr<SinkType>, SinkMode>,
    *    boost::shared_ptr<SinkType::FilterType>
    * >.
    */
   template <typename SinkModePairFilterPairIterator>
   void AtomicSetSinkFilters(SinkModePairFilterPairIterator first,
         SinkModePairFilterPairIterator last)
   {
      boost::lock_guard<boost::mutex> lockSyncs(syncSinksMutex_);
      boost::lock_guard<boost::mutex> lockAsyncQ(asyncQueueMutex_);
      StopAsyncReceiveLoop();

      for (SinkModePairFilterPairIterator it = first; it != last; ++it)
      {
         boost::shared_ptr<SinkType> sink = it->first.first;
         SinkMode mode = it->first.second;
         boost::shared_ptr<LogEntryFilter> filter = it->second;

         typedef std::vector< boost::shared_ptr<SinkType> > SinkListType;
         SinkListType* pSinkList = 0;
         switch (mode)
         {
            case SinkModeSynchronous:
               pSinkList = &synchronousSinks_;
               break;
            case SinkModeAsynchronous:
               pSinkList = &asynchronousSinks_;
               break;
         }
         typename SinkListType::iterator foundIt =
            std::find(pSinkList->begin(), pSinkList->end(), sink);
         if (foundIt != pSinkList->end())
            (*foundIt)->SetFilter(filter);
      }

      StartAsyncReceiveLoop();
   }

private:
   friend class GenericLogger<Self>;

   // Called by Logger
   const char* RegisterComponentLabel(const std::string& componentLabel)
   {
      boost::lock_guard<boost::mutex> lock(componentLabelsMutex_);

      const char* label =
         componentLabels_.insert(componentLabel).first->c_str();
      return label;
   }

   // Called by Logger
   void LogEntry(const MetadataType& metadata, const char* entryText)
   {
      std::vector<LogLineType> lines;
      SplitEntryIntoLines(lines, metadata, entryText);

      {
         boost::lock_guard<boost::mutex> lock(syncSinksMutex_);

         for (std::vector< boost::shared_ptr<SinkType> >::iterator
               it = synchronousSinks_.begin(), end = synchronousSinks_.end();
               it != end; ++it)
         {
            (*it)->Consume(lines);
         }
      }
      asyncQueue_.SendLines(lines.begin(), lines.end());
   }

   // Called on the receive thread of AsyncLoggingQueue
   void RunAsynchronousSinks(std::vector<LogLineType>& lines)
   {
      for (std::vector< boost::shared_ptr<SinkType> >::iterator
            it = asynchronousSinks_.begin(), end = asynchronousSinks_.end();
            it != end; ++it)
      {
         (*it)->Consume(lines);
      }
   }

   void StartAsyncReceiveLoop()
   {
      asyncQueue_.RunReceiveLoop(
            boost::bind<void>(&LoggingCore::RunAsynchronousSinks,
               this, _1));
   }

   void StopAsyncReceiveLoop()
   {
      asyncQueue_.ShutdownReceiveLoop();
   }
};

} // namespace detail
} // namespace logging
} // namespace mm
