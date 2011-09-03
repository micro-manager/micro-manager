///////////////////////////////////////////////////////////////////////////////
// FILE:          SequenceThread.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters/MMCamera
//-----------------------------------------------------------------------------
// DESCRIPTION:   Impelements sequence thread for rendering live video.
//                Part of the skeleton code for the micro-manager camera adapter.
//                Use it as starting point for writing custom device adapters.
//                
// AUTHOR:        Nenad Amodaj, http://nenad.amodaj.com
//                
// COPYRIGHT:     University of California, San Francisco, 2011
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
//

#include "MMCamera.h"


SequenceThread::SequenceThread(MMCamera* pCam)
   :intervalMs_(100.0),
   numImages_(0),
   imageCounter_(0),
   stop_(true),
   camera_(pCam)
{};

SequenceThread::~SequenceThread() {};

void SequenceThread::Stop() {
   stop_=true;
}

void SequenceThread::Start(long numImages, double intervalMs)
{
   numImages_=numImages;
   intervalMs_=intervalMs;
   imageCounter_=0;
   stop_ = false;
   activate();
}

bool SequenceThread::IsStopped(){
   return stop_;
}

int SequenceThread::svc(void) throw()
{
   int ret=DEVICE_ERR;
   return ret;
}
