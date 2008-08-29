///////////////////////////////////////////////////////////////////////////////
// FILE:          AF100X.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Autofocus module
//                
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, August 28, 2008
//
// COPYRIGHT:     100X Imaging Inc, 2088, http://www.100ximaging.com 
//
// CVS:           $Id: DemoCamera.h 1493 2008-08-29 01:09:04Z nenad $
//

#pragma once;

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//



//////////////////////////////////////////////////////////////////////////////
// DemoAutoFocus class
// Simulation of the auto-focusing module
//////////////////////////////////////////////////////////////////////////////
class Demo100XAutoFocus : public CAutoFocusBase<Demo100XAutoFocus>
{
public:
   Demo100XAutoFocus() : 
      running_(false), 
      busy_(false), 
      initialized_(false)  
   {}

   ~Demo100XAutoFocus() {}
      
   // MMDevice API
   bool Busy() {return busy_;}
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown(){initialized_ = false; return DEVICE_OK;}

   // AutoFocus API
   virtual int SetContinuousFocusing(bool state) {running_ = state; return DEVICE_OK;}
   virtual int GetContinuousFocusing(bool& state) {state = running_; return DEVICE_OK;}
   virtual bool IsContinuousFocusLocked() {return running_;}
   virtual int FullFocus() {return DEVICE_UNSUPPORTED_COMMAND;}
   virtual int IncrementalFocus() {return DEVICE_UNSUPPORTED_COMMAND;}
   virtual int GetFocusScore(double& /*score*/) {return DEVICE_UNSUPPORTED_COMMAND;}

private:
   bool running_;
   bool busy_;
   bool initialized_;
};
