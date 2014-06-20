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

#include <boost/bind.hpp>
#include <boost/date_time/posix_time/posix_time_types.hpp>
#include <boost/function.hpp>
#include <boost/thread.hpp>

#include <algorithm>
#include <vector>


namespace mm
{
namespace logging
{
namespace detail
{

template <typename TLogLine>
class AsyncLoggingQueue
{
public:
   typedef TLogLine LogLineType;

private:
   // The "queue" for asynchronous sinks. It is a vector, because the async
   // backend dequeues all elements at once using std::swap.
   boost::mutex mutex_;
   boost::condition_variable condVar_;
   std::vector<LogLineType> queue_;

   // Swapped with queue_ and accessed from receiving thread.
   std::vector<LogLineType> received_;

   bool shutdownRequested_; // Protected by mutex_

   // threadMutex_ protects the start/stop of loopThread_; it must be acquired
   // before mutex_.
   boost::mutex threadMutex_;
   boost::thread loopThread_; // Protected by threadMutex_

public:
   AsyncLoggingQueue() :
      shutdownRequested_(false)
   {}

   void SendLines(typename std::vector<LogLineType>::const_iterator first,
         typename std::vector<LogLineType>::const_iterator last)
   {
      boost::lock_guard<boost::mutex> lock(mutex_);
      std::copy(first, last, std::back_inserter(queue_));
      condVar_.notify_one();
   }

   void RunReceiveLoop(boost::function<void (std::vector<LogLineType>&)>
         consume)
   {
      boost::lock_guard<boost::mutex> lock(threadMutex_);

      if (loopThread_.get_id() != boost::thread::id())
      {
         // Already running: stop and replace.
         {
            boost::lock_guard<boost::mutex> lock(mutex_);
            shutdownRequested_ = true;
            condVar_.notify_one();
         }
         loopThread_.join();
      }

      boost::thread t(boost::bind<void>(&AsyncLoggingQueue::ReceiveLoop,
               this, consume));
      boost::swap(loopThread_, t);
   }

   void ShutdownReceiveLoop()
   {
      boost::lock_guard<boost::mutex> lock(threadMutex_);

      if (!loopThread_.joinable())
         return;

      {
         boost::lock_guard<boost::mutex> lock(mutex_);
         shutdownRequested_ = true;
         condVar_.notify_one();
      }
      loopThread_.join();

      boost::thread t;
      boost::swap(loopThread_, t);
   }

private:
   void ReceiveLoop(boost::function<void (std::vector<LogLineType>&)> consume)
   {
      // The loop operates in one of two modes: timed wait and untimed wait.
      //
      // When in timed wait mode, the loop unconditionally waits for a fixed
      // interval before checking for data. If data is available, it is
      // processed and the loop repeats an unconditional wait. If no data is
      // available, the loop switches to untimed wait mode.
      //
      // In untimed wait mode, the loop waits on a condition variable until
      // notification from the frontend. Once data is available, the loop
      // switches back to timed wait mode.
      //
      // This way, data is processed in batches when logging occurs at high
      // frequency, preventing thrashing between the frontend and backend
      // threads and limiting the frequency of stream flushing.

      bool timedWaitMode = true;
      bool shuttingDown = false;

      for (;;)
      {
         if (timedWaitMode)
         {
            // TODO Make interval configurable
            boost::this_thread::sleep(boost::posix_time::milliseconds(10));

            {
               boost::lock_guard<boost::mutex> lock(mutex_);
               if (shutdownRequested_)
               {
                  shutdownRequested_ = false; // Allow for restarting
                  shuttingDown = true;
               }
               if (!shuttingDown && queue_.empty())
               {
                  timedWaitMode = false;
                  continue;
               }
               std::swap(queue_, received_);
            }
            consume(received_);
            received_.clear();

            if (shuttingDown)
               return;
         }
         else // untimed wait mode
         {
            {
               boost::unique_lock<boost::mutex> lock(mutex_);
               while (queue_.empty())
               {
                  condVar_.wait(lock);
                  if (shutdownRequested_)
                  {
                     shutdownRequested_ = false; // Allow for restarting
                     shuttingDown = true;
                     break;
                  }
               }
               std::swap(queue_, received_);
            }
            consume(received_);
            received_.clear();

            if (shuttingDown)
               return;

            timedWaitMode = true;
         }
      }
   }
};

} // namespace detail
} // namespace logging
} // namespace mm
