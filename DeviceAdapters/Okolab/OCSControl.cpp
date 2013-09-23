///////////////////////////////////////////////////////////////////////////////
// FILE:          OCSControl.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab device adapter for OCS 
//                
// AUTHOR:        Domenico Mastronardi @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       This file is distributed under the BSD license.
//
// REVISIONS:     2013-09-23
//

#include "Okolab.h"
#include "OCSControl.h"
#include "../../MMDevice/ModuleInterface.h"

const char* g_OCSControl = "OKO Control Server";

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// OCSControl implementation
//

OCSControl::OCSControl() 
{
 this->product_id=-1;
 this->device_id=-1; 
 this->initialized_=false;
 this->port_="Undefined";

 InitializeDefaultErrorMessages();

 CreateProperty(MM::g_Keyword_Name, g_OCSControl , MM::String, true);
 CreateProperty(MM::g_Keyword_Description, "OKO Control Server controller", MM::String, true);
}


OCSControl::~OCSControl()
{
 if(initialized_) Shutdown();
}


/**
* Obtains device name.
* Required by the MM::Device API.
*/
void OCSControl::GetName(char* name) const
{
 CDeviceUtils::CopyLimitedString(name, g_OCSControl);
}


/**
* Intializes the hardware.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well.
* Required by the MM::Device API.
*/
int OCSControl::Initialize()
{
 int ret;
 if(initialized_) return DEVICE_OK;

 CPropertyAction* pAct=new CPropertyAction(this, &OCSControl::OnStatus);
 ret=CreateProperty("Status", "Off", MM::String, false, pAct);
 if(ret!=DEVICE_OK) return ret;
 AddAllowedValue("Status", "Off");
 AddAllowedValue("Status", "On");

 initialized_=true;
 SetStatus(1); // comment this line if you don't want OCS auto start (if non running)

 return DEVICE_OK;
}


/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
*/
int OCSControl::Shutdown()
{
 initialized_=false;
 return DEVICE_OK;
}


bool OCSControl::WakeUp()
{
 return true; 
}


bool OCSControl::Busy()
{
 return false;
}


///////////////////////////////////////////////////////////////////////////////
//  Action handlers
///////////////////////////////////////////////////////////////////////////////


int OCSControl::OnStatus(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 std::string strStatus;
 if(eAct==MM::BeforeGet)
  {
   if(GetStatus()==1) pProp->Set("On");
   else pProp->Set("Off");
   return DEVICE_OK;     
  }
 if(eAct==MM::AfterSet)
  {
   pProp->Get(strStatus);
   if(strStatus.compare("On")==0) SetStatus(1);
   else SetStatus(0);
   return DEVICE_OK;     
  }
 return DEVICE_OK;     
}


///////////////////////////////////////////////////////////////////////////////
//  Internal API
///////////////////////////////////////////////////////////////////////////////


/*
 *  Get version of Oko Library (Okolab.cpp)
 */
int OCSControl::GetVersion()
{
 version_=OkolabDevice::version_;
 return DEVICE_OK;
}


/*
 *  Obtains status of OCS 
 *  @return   0=not running
 *            1=running
 *           -1=error
 */
int OCSControl::GetStatus()
{   
 if(!initialized_) return -1;
 if(OkolabDevice::OCSRunning()) return 1;
 return 0;                                                         
} 


/*
 *  Set status (start/stop) of OCS 
 *  @param  int status 0/1 = off/on
 *  @return  -1=error
 *            0=all ok
 */
int OCSControl::SetStatus(int status)
{
 if(!initialized_) return -1;
 if(status==1)
  {
   OkolabDevice::OCSStart();
  }
 if(status==0)
  {
   OkolabDevice::OCSStop();
  }
 return 0;                                                         
} 


