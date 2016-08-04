// Micro-Manager IIDC Device Adapter
//
// AUTHOR:        Mark A. Tsuchida
//
// COPYRIGHT:     2014-2015, Regents of the University of California
//                2016, Open Imaging, Inc.
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

#pragma once

#include <dc1394/dc1394.h>
#ifdef _MSC_VER
#undef restrict
#endif

#include <boost/thread.hpp>
#include <boost/function.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/utility.hpp>


namespace IIDC {

class Capture : boost::noncopyable
{
public:
   virtual ~Capture() {}

   /*
    * Run the capture, blocking until finished or asynchronously stopped.
    * Must be called from the thread that owns this instance.
    */
   virtual void Run() = 0;

   /*
    * Stop the capture; the frame handler will no longer be called once Stop()
    * has returned. May be called from any thread.
    */
   virtual void Stop() = 0;

   /*
    * Return true if capture is running (not all requested frames have been
    * acquired, and Stop() has not been called). May be called from any thread.
    */
   virtual bool IsRunning() = 0;
};


/*
 * Implementation class for frame retrieval from the DMA ring buffer.
 * This implementation is common to the different types of capture (continuous,
 * one-shot, and multi-shot), so it is factored out into its own class.
 */
class FrameRetriever : boost::noncopyable
{
public:
   typedef boost::function<void (dc1394video_frame_t*)> FrameCallbackFunction;

private:
   dc1394camera_t* libdc1394camera_;

   const size_t requestedFrames_;
   const size_t expectedFrames_;
   size_t retrievedFrames_;
   unsigned frameTimeoutMs_;
   bool alwaysUsePolling_;

   boost::mutex statusMutex_; // Protect started_ and stopped_
   bool started_;
   bool stopped_;

   FrameCallbackFunction frameCallback_;

public:
   /*
    * Once Run() is called, retrieve nrFrames from libdc1394camera, waiting
    * frameTimeoutMs for the first frame (and subsequent frames, if forcing
    * polling) to arrive, and call frameCallback with each frame as the
    * argument.
    */
   FrameRetriever(dc1394camera_t* libdc1394camera,
         size_t desiredNrFrames, size_t expectedNrFrames,
         unsigned frameTimeoutMs, bool alwaysUsePolling,
         FrameCallbackFunction frameCallback);

   /*
    * Run the retrieval cycle, blocking until finished. Must be called from the
    * thread that owns this instance.
    */
   void Run();

   /*
    * Stop the retrieval cycle and ensure that no further calls to
    * frameCallback will occur.
    */
   void Stop();

   /*
    * Return true if Run() has been called, retrieval of the requested frames
    * has not finished, and Stop() has not been called.
    */
   bool IsRunning();

private:
   void RetrieveFrame(unsigned timeoutMs = 0, unsigned pollingIntervalMs = 0);
   dc1394video_frame_t* PollForFrame(unsigned timeoutMs, unsigned pollingIntervalMs);
   dc1394video_frame_t* WaitForFrame();
};


class CaptureImpl : public Capture
{
public:
   typedef FrameRetriever::FrameCallbackFunction FrameCallbackFunction;
   typedef boost::function<void ()> FinishCallbackFunction;

private:
   FrameRetriever retriever_;
   FinishCallbackFunction finishCallback_;

public:
   CaptureImpl(dc1394camera_t* libdc1394camera,
         size_t nrFrames, size_t finiteNrFrames, unsigned frameTimeoutMs,
         bool alwaysUsePolling,
         FrameCallbackFunction frameHandler,
         FinishCallbackFunction finishHandler) :
      retriever_(libdc1394camera, nrFrames, finiteNrFrames, frameTimeoutMs,
            alwaysUsePolling, frameHandler),
      finishCallback_(finishHandler)
   {}

   virtual void Run();
   virtual void Stop() { retriever_.Stop(); }
   virtual bool IsRunning() { return retriever_.IsRunning(); }

private:
   // Subclasses override these to provide implementation:
   virtual void SetUp() = 0;
   virtual void CleanUp() = 0;

   void Finish()
   {
      if (finishCallback_)
         finishCallback_();
      CleanUp();
   }
};


class ContinuousCapture : public CaptureImpl
{
   dc1394camera_t* libdc1394camera_;
   const uint32_t nrDMABuffers_;

public:
   ContinuousCapture(dc1394camera_t* libdc1394camera,
         uint32_t nrDMABuffers, size_t nrFrames, unsigned frameTimeoutMs,
         bool alwaysUsePolling,
         FrameCallbackFunction frameHandler,
         FinishCallbackFunction finishHandler) :
      CaptureImpl(libdc1394camera, nrFrames, 0, frameTimeoutMs,
            alwaysUsePolling,
            frameHandler, finishHandler),
      libdc1394camera_(libdc1394camera),
      nrDMABuffers_(nrDMABuffers)
   {}

private:
   virtual void SetUp();
   virtual void CleanUp();
};


class MultiShotCapture : public CaptureImpl
{
   dc1394camera_t* libdc1394camera_;
   const uint32_t nrDMABuffers_;
   uint16_t nrFrames_;

public:
   MultiShotCapture(dc1394camera_t* libdc1394camera,
         uint32_t nrDMABuffers, uint16_t nrFrames, unsigned frameTimeoutMs,
         bool alwaysUsePolling,
         FrameCallbackFunction frameHandler,
         FinishCallbackFunction finishHandler) :
      CaptureImpl(libdc1394camera, nrFrames, nrFrames, frameTimeoutMs,
            alwaysUsePolling,
            frameHandler, finishHandler),
      libdc1394camera_(libdc1394camera),
      nrDMABuffers_(nrDMABuffers),
      nrFrames_(nrFrames)
   {}

private:
   virtual void SetUp();
   virtual void CleanUp();
};


class OneShotCapture : public CaptureImpl
{
   dc1394camera_t* libdc1394camera_;
   const uint32_t nrDMABuffers_;

public:
   OneShotCapture(dc1394camera_t* libdc1394camera,
         uint32_t nrDMABuffers, unsigned timeoutMs,
         FrameCallbackFunction frameHandler,
         FinishCallbackFunction finishHandler) :
      CaptureImpl(libdc1394camera, 1, 1, timeoutMs, true,
            frameHandler, finishHandler),
      libdc1394camera_(libdc1394camera),
      nrDMABuffers_(nrDMABuffers)
   {}

private:
   virtual void SetUp();
   virtual void CleanUp();
};

} // namespace IIDC
