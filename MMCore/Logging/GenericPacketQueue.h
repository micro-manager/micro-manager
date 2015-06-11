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


namespace mm
{
namespace logging
{
namespace internal
{

template <typename TMetadata>
class GenericPacketQueue
{
   typedef GenericPacketArray<TMetadata> PacketArrayType;

private:
   // The "queue" for asynchronous sinks.
   boost::mutex mutex_;
   boost::condition_variable condVar_;
   PacketArrayType queue_;

   // Swapped with queue_ and accessed from receiving thread.
   PacketArrayType received_;

   bool shutdownRequested_; // Protected by mutex_

   // threadMutex_ protects the start/stop of loopThread_; it must be acquired
   // before mutex_.
   boost::mutex threadMutex_;
   boost::thread loopThread_; // Protected by threadMutex_

public:
   GenericPacketQueue() :
      shutdownRequested_(false)
   {}

   template <typename TPacketIter>
   void SendPackets(TPacketIter first, TPacketIter last)
   {
      boost::lock_guard<boost::mutex> lock(mutex_);
      queue_.Append(first, last);
      condVar_.notify_one();
   }

   void RunReceiveLoop(boost::function<void (PacketArrayType&)>
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

      boost::thread t(boost::bind(&GenericPacketQueue::ReceiveLoop,
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
   void ReceiveLoop(boost::function<void (PacketArrayType&)> consume)
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
               if (!shuttingDown && queue_.IsEmpty())
               {
                  timedWaitMode = false;
                  continue;
               }
               queue_.Swap(received_);
            }
            consume(received_);
            received_.Clear();

            if (shuttingDown)
               return;
         }
         else // untimed wait mode
         {
            {
               boost::unique_lock<boost::mutex> lock(mutex_);
               while (queue_.IsEmpty())
               {
                  condVar_.wait(lock);
                  if (shutdownRequested_)
                  {
                     shutdownRequested_ = false; // Allow for restarting
                     shuttingDown = true;
                     break;
                  }
               }
               queue_.Swap(received_);
            }
            consume(received_);
            received_.Clear();

            if (shuttingDown)
               return;

            timedWaitMode = true;
         }
      }
   }
};

} // namespace internal
} // namespace logging
} // namespace mm
