// Micro-Manager IIDC Device Adapter
//
// AUTHOR:        Mark A. Tsuchida
//
// COPYRIGHT:     University of California, San Francisco, 2014
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#include "IIDCCapture.h"

#include "IIDCError.h"
#include "IIDCInterface.h"

#include <boost/chrono.hpp>
#include <boost/thread.hpp>
#include <boost/throw_exception.hpp>


namespace IIDC {

FrameRetriever::FrameRetriever(dc1394camera_t* libdc1394camera,
      size_t desiredNrFrames,
      size_t expectedNrFrames,
      unsigned firstFrameTimeoutMs,
      FrameCallbackFunction frameCallback) :
   libdc1394camera_(libdc1394camera),
   requestedFrames_(desiredNrFrames),
   expectedFrames_(expectedNrFrames),
   retrievedFrames_(0),
   firstFrameTimeoutMs_(firstFrameTimeoutMs),
   started_(false),
   stopped_(false),
   frameCallback_(frameCallback)
{
}


void
FrameRetriever::Run()
{
   {
      boost::lock_guard<boost::mutex> lock(statusMutex_);
      started_ = true;
      if (stopped_)
         return;
   }

   try
   {
      // We poll for the first frame, with a timeout, in case the camera fails to
      // deliver images for the current settings. Once we've got the first frame,
      // we can be reasonably confident that subsequent frames will follow, so we
      // switch to the more efficient blocking method.

      if (!requestedFrames_ || retrievedFrames_ < requestedFrames_)
         RetrieveFrame(firstFrameTimeoutMs_, 1);

      while (!requestedFrames_ || retrievedFrames_ < requestedFrames_)
      {
         {
            boost::lock_guard<boost::mutex> lock(statusMutex_);
            if (stopped_)
               break;
         }
         RetrieveFrame();
      }
   }
   catch (const Error&)
   {
      {
         boost::lock_guard<boost::mutex> lock(statusMutex_);
         stopped_ = true;
      }
      throw;
   }

   {
      boost::lock_guard<boost::mutex> lock(statusMutex_);
      stopped_ = true;
   }
}


void
FrameRetriever::Stop()
{
   boost::lock_guard<boost::mutex> lock(statusMutex_);
   stopped_ = true;
}


bool
FrameRetriever::IsRunning()
{
   boost::lock_guard<boost::mutex> lock(statusMutex_);
   return started_ && !stopped_;
}


/*
 * Retrieve a single frame from the DMA ring buffer and call the frame
 * callback. This function manages the libdc1394 dequeue-enqueue cycle so that
 * DMA frame buffers are not leaked.
 */
void
FrameRetriever::RetrieveFrame(unsigned timeoutMs, unsigned pollingIntervalMs)
{
   if (expectedFrames_ && retrievedFrames_ >= expectedFrames_)
   {
      BOOST_THROW_EXCEPTION(Error("Failed to retrieved the requested number of frame(s) "
            "(possibly due to having skipped corrupted frame(s)"));
   }

   dc1394video_frame_t* dequeuedFrame;
   if (timeoutMs && pollingIntervalMs)
      dequeuedFrame = PollForFrame(timeoutMs, pollingIntervalMs);
   else
      dequeuedFrame = WaitForFrame();

   if (dc1394_capture_is_frame_corrupt(libdc1394camera_, dequeuedFrame))
      ; // Skip // TODO Log
   else
   {
      try // In case frameCallback_ throws
      {
         retrievedFrames_++;

         // Stop() may have been called while we've been dequeuing the frame.
         // Ensure that we don't call the frame callback after Stop() has been
         // called.
         bool stopped = false;
         {
            boost::lock_guard<boost::mutex> lock(statusMutex_);
            if (stopped_)
               stopped = true;
         }
         if (!stopped)
            frameCallback_(dequeuedFrame);
      }
      catch (...)
      {
         dc1394error_t enqErr;
         enqErr = dc1394_capture_enqueue(libdc1394camera_, dequeuedFrame);
         if (enqErr != DC1394_SUCCESS)
         {
            // TODO Log
         }
         throw;
      }
   }

   dc1394error_t enqErr;
   enqErr = dc1394_capture_enqueue(libdc1394camera_, dequeuedFrame);
   if (enqErr != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(enqErr, "Cannot recycle captured frame buffer"));
}


dc1394video_frame_t*
FrameRetriever::PollForFrame(unsigned timeoutMs, unsigned pollingIntervalMs)
{
   using boost::chrono::steady_clock;
   steady_clock::time_point deadline = steady_clock::now() +
         boost::chrono::milliseconds(timeoutMs);

   dc1394video_frame_t* dequeuedFrame = NULL;
   while (dequeuedFrame == NULL)
   {
      dc1394error_t err;
      err = dc1394_capture_dequeue(libdc1394camera_, DC1394_CAPTURE_POLICY_POLL, &dequeuedFrame);
      if (err != DC1394_SUCCESS)
         BOOST_THROW_EXCEPTION(Error(err, "Cannot obtain captured frame"));
      if (dequeuedFrame)
         break;
      if (steady_clock::now() > deadline)
         BOOST_THROW_EXCEPTION(Error(err, "Timeout of " + boost::lexical_cast<std::string>(timeoutMs) +
               " ms reached while waiting for frame"));
      // Note: With newer Boost versions, use
      // boost::this_thread::sleep_for(boost::chrono::milliseconds(pollingIntervalMs));
      boost::this_thread::sleep(boost::posix_time::milliseconds(pollingIntervalMs));
   }
   return dequeuedFrame;
}


dc1394video_frame_t*
FrameRetriever::WaitForFrame()
{
   dc1394video_frame_t* dequeuedFrame = NULL;
   dc1394error_t err;
   err = dc1394_capture_dequeue(libdc1394camera_, DC1394_CAPTURE_POLICY_WAIT, &dequeuedFrame);
   if (err != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(err, "Cannot obtain captured frame"));
   return dequeuedFrame;
}


void
CaptureImpl::Run()
{
   SetUp();
   try
   {
      retriever_.Run();
   }
   catch (...)
   {
      try
      {
         Finish();
      }
      catch (const Error& /*e*/)
      {
         // TODO Log e
      }
      throw;
   }
   Finish();
}


void
ContinuousCapture::SetUp()
{
   dc1394error_t err;
   err = dc1394_capture_setup(libdc1394camera_, nrDMABuffers_,
         DC1394_CAPTURE_FLAGS_DEFAULT);
   if (err != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(err, "Cannot start capture"));

   err = dc1394_video_set_transmission(libdc1394camera_, DC1394_ON);
   if (err != DC1394_SUCCESS)
   {
      dc1394_capture_stop(libdc1394camera_); // Ignore errors
      BOOST_THROW_EXCEPTION(Error(err, "Cannot start continuous transmission"));
   }

   dc1394switch_t flag;
   err = dc1394_video_get_transmission(libdc1394camera_, &flag);
   if (err != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(err, "Cannot check if continuous transmission started"));
   if (flag != DC1394_ON)
      BOOST_THROW_EXCEPTION(Error(err, "Continuous transmission did not start"));
}


void
ContinuousCapture::CleanUp()
{
   dc1394error_t err;
   err = dc1394_video_set_transmission(libdc1394camera_, DC1394_OFF);
   if (err != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(err, "Cannot stop continuous transmission"));

   dc1394switch_t flag;
   unsigned i = 0;
   do
   {
      err = dc1394_video_get_transmission(libdc1394camera_, &flag);
      if (err != DC1394_SUCCESS)
         BOOST_THROW_EXCEPTION(Error(err, "Cannot check if continuous transmission stopped"));
      boost::this_thread::sleep(boost::posix_time::milliseconds(100));
   } while (flag != DC1394_OFF && i++ < 5);

   // Whether or not transmission stopped, we attempt to stop the capture.
   err = dc1394_capture_stop(libdc1394camera_);
   if (err != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(err, "Error while stopping capture"));

   // Now try again to stop transmission.
   i = 0;
   do
   {
      err = dc1394_video_get_transmission(libdc1394camera_, &flag);
      if (err != DC1394_SUCCESS)
         BOOST_THROW_EXCEPTION(Error(err, "Cannot check if continuous transmission stopped"));
      boost::this_thread::sleep(boost::posix_time::milliseconds(100));
   } while (flag != DC1394_OFF && i++ < 50);

   if (flag != DC1394_OFF)
      BOOST_THROW_EXCEPTION(Error(err, "Continuous transmission did not stop"));
}


void
MultiShotCapture::SetUp()
{
   dc1394error_t err;
   err = dc1394_capture_setup(libdc1394camera_, nrDMABuffers_,
         DC1394_CAPTURE_FLAGS_CHANNEL_ALLOC |
         DC1394_CAPTURE_FLAGS_BANDWIDTH_ALLOC);
   if (err != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(err, "Cannot start capture"));

   err = dc1394_video_set_multi_shot(libdc1394camera_, nrFrames_, DC1394_ON);
   if (err != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(err, "Cannot start multi-shot transmission"));
}


void
MultiShotCapture::CleanUp()
{
   dc1394error_t err;
   err = dc1394_video_set_multi_shot(libdc1394camera_, 0, DC1394_OFF);
   if (err != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(err, "Cannot turn off multi-shot transmission"));

   err = dc1394_capture_stop(libdc1394camera_);
   if (err != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(err, "Error while stopping capture"));
}


void
OneShotCapture::SetUp()
{
   dc1394error_t err;
   err = dc1394_capture_setup(libdc1394camera_, nrDMABuffers_,
         DC1394_CAPTURE_FLAGS_CHANNEL_ALLOC |
         DC1394_CAPTURE_FLAGS_BANDWIDTH_ALLOC);
   if (err != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(err, "Cannot start capture"));

   err = dc1394_video_set_one_shot(libdc1394camera_, DC1394_ON);
   if (err != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(err, "Cannot start one-shot transmission"));
}


void
OneShotCapture::CleanUp()
{
   // Just in case, turn off one-shot (it should turn itself off after
   // transmitting a single frame).
   dc1394error_t err;
   err = dc1394_video_set_one_shot(libdc1394camera_, DC1394_OFF);
   if (err != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(err, "Cannot turn off multi-shot transmission"));

   err = dc1394_capture_stop(libdc1394camera_);
   if (err != DC1394_SUCCESS)
      BOOST_THROW_EXCEPTION(Error(err, "Error while stopping capture"));
}

} // namespace IIDC
