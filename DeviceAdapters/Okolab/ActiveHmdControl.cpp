///////////////////////////////////////////////////////////////////////////////
// FILE:          ActiveHmdControl.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab device adapter for H301-HM-ACTIVE 
//                
// AUTHOR:        Marco Di Pasqua @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       This file is distributed under the BSD license.
//
// REVISIONS:     
//

#include "Okolab.h"
#include "ActiveHmdControl.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* g_HmdControl = "H301-HM-ACTIVE";

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// HmdControl implementation
//

HmdControl::HmdControl() 
{
 this->product_id=15;
 this->device_id=0;
 this->initialized_=false;
 this->connected_=0;
 this->port_="Undefined";

 InitializeDefaultErrorMessages();

 CreateProperty(MM::g_Keyword_Name, g_HmdControl , MM::String, true);
 CreateProperty(MM::g_Keyword_Description, "Okolab Active Humidity Control", MM::String, true);

 CPropertyAction* pAct = new CPropertyAction (this, &HmdControl::OnPort);      
 CreateProperty("COM Port", "Undefined", MM::String, false, pAct, true);      
}


HmdControl::~HmdControl()
{
 if(initialized_) Shutdown();
}


/**
* Obtains device name.
* Required by the MM::Device API.
*/
void HmdControl::GetName(char* name) const
{
 CDeviceUtils::CopyLimitedString(name, g_HmdControl);
}


/**
* Intializes the hardware.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well.
* Required by the MM::Device API.
*/
int HmdControl::Initialize()
{
 int ret;
 int iport;
 if(initialized_) return DEVICE_OK;

 if(!OCSRunning()) 
  { 
   LogMessage("OKO Control Server not running!",false);
   return DEVICE_COMM_HUB_MISSING;
  }

 if(sscanf(port_.c_str(),"COM%d",&iport)==1) TryConnectDevice(iport);
 else return DEVICE_INVALID_INPUT_PARAM;

 if(IsDeviceConnected(this->product_id)!=1)
  {
   return DEVICE_NOT_CONNECTED;
  }

 if(IsDeviceWorking(this->product_id)!=1)
  {
   return DEVICE_ERR;
  }

 CPropertyAction* pAct=new CPropertyAction(this, &HmdControl::OnGetConc);
 ret=CreateProperty("Humidity", "0.0", MM::Float, true, pAct);
 if(ret!=DEVICE_OK) return ret;                                                            

 CPropertyAction* pAct2=new CPropertyAction(this, &HmdControl::OnGetConnected);
 ret=CreateProperty("Connected", "0", MM::Integer, true, pAct2);
 if(ret!=DEVICE_OK) return ret;                           

 CPropertyAction* pAct3=new CPropertyAction(this, &HmdControl::OnGetCommPort);
 ret=CreateProperty("Serial Port", "Undefined", MM::String, true, pAct3);
 if(ret!=DEVICE_OK) return ret;                           

 CPropertyAction* pAct4=new CPropertyAction(this, &HmdControl::OnSetCommPort);
 ret=CreateProperty("Set Serial Port", "1", MM::Integer, false, pAct4, false);
 if(ret!=DEVICE_OK) return ret;                           

 CPropertyAction* pAct5=new CPropertyAction(this, &HmdControl::OnGetVersion);
 ret=CreateProperty("Version", "Undefined", MM::String, true, pAct5);
 if(ret!=DEVICE_OK) return ret;                           

 CPropertyAction* pAct6=new CPropertyAction(this, &HmdControl::OnGetSetPoint);
 ret=CreateProperty("Set-Point", "0.0", MM::Float, true, pAct6);
 if(ret!=DEVICE_OK) return ret;
 
 CPropertyAction* pAct7=new CPropertyAction(this, &HmdControl::OnSetSetPoint);
 ret=CreateProperty("Set Set-Point", "0.0", MM::Float, false, pAct7, false);
 if(ret!=DEVICE_OK) return ret;                           

 initialized_=true;

 rthread_ = new HmdControl_RefreshThread(*this);

 RefreshThread_Start();

 return DEVICE_OK;
}


/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
*/
int HmdControl::Shutdown()
{
 if(initialized_)
  {
   RefreshThread_Stop();
   delete(rthread_);
  }
 initialized_=false;
 return DEVICE_OK;
}


bool HmdControl::WakeUp()
{
 return true; 
}


bool HmdControl::Busy()
{
 return false;
}


///////////////////////////////////////////////////////////////////////////////
//  Action handlers
///////////////////////////////////////////////////////////////////////////////


int HmdControl::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   pProp->Set(port_.c_str());
  }
 else if(eAct==MM::AfterSet)
       {
		if(initialized_)
		 {
          // revert
          pProp->Set(port_.c_str());
          return ERR_PORT_CHANGE_FORBIDDEN;
		 }
        pProp->Get(port_);                                                     
       }                                                                         
 return DEVICE_OK;     
}


int HmdControl::OnGetVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   GetVersion();
   pProp->Set(version_.c_str());
  }
 return DEVICE_OK;
}


int HmdControl::OnGetConnected(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   long connected=0;
   if(GetConnected(connected)==DEVICE_OK) pProp->Set(connected);
  }
 return DEVICE_OK;     
}


int HmdControl::OnGetCommPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   char strComPort[21];
   GetCommPort(strComPort);
   pProp->Set(strComPort);
  }
 return DEVICE_OK;
}


int HmdControl::OnSetCommPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::AfterSet)
  {
   long cport=0;
   pProp->Get(cport);
   SetCommPort(cport);
  }
 return DEVICE_OK;     
}


int HmdControl::OnGetConc(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   double co2conc=0;
   if(GetConc(co2conc)==DEVICE_OK) pProp->Set(co2conc);
  }
 return DEVICE_OK;     
}


int HmdControl::OnGetSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   double sp=0;
   if(GetSetPoint(sp)==DEVICE_OK) pProp->Set(sp);
  }
 return DEVICE_OK;     
}


int HmdControl::OnSetSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::AfterSet)
  {
   double sp;
   pProp->Get(sp);
   SetSetPoint(sp);
  }
 return DEVICE_OK;     
}


///////////////////////////////////////////////////////////////////////////////
//  Internal API
///////////////////////////////////////////////////////////////////////////////


/*
 *  Get version of Oko Library (Okolab.cpp)
 */
int HmdControl::GetVersion()
{
 version_=OkolabDevice::version_;
 return DEVICE_OK;
}


/*
 *  Obtains ComPort (witch connects OCS to this device)
 */
int HmdControl::GetCommPort(char *strcommport)
{   
 strcpy(strcommport,"Undefined");
 if(!initialized_) return DEVICE_NOT_CONNECTED;
 int ret=OkolabDevice::GetCommPort(strcommport);
 if(ret<=0) return DEVICE_ERR;
 return DEVICE_OK;
} 


/*
 *  Set com port (1=COM1, 2=COM2 and so on...)
 */
int HmdControl::SetCommPort(long& /*commport*/)
{
 if(!WakeUp()) return DEVICE_SERIAL_INVALID_RESPONSE;
 return DEVICE_OK;     
}



/*
 *  Obtains connection status (betweeen OCS and this device)
 */
int HmdControl::GetConnected(long& conn)
{   
 conn=0;
 connected_=0;
 if(!initialized_) return DEVICE_NOT_CONNECTED;
 int dc=IsDeviceConnected(this->product_id);
 if(dc<0) { return DEVICE_ERR; }
 if(dc>0) { conn=1; connected_=1; return DEVICE_OK; }
 return DEVICE_NOT_CONNECTED;
} 


/*
 *  Obtains humidity value from OCS
 */
int HmdControl::GetConc(double& val)
{
 if(!initialized_) return DEVICE_NOT_CONNECTED;
 int ret=GetValue(val);
 if(ret<0) DEVICE_ERR;
 return DEVICE_OK;                                                         
} 


/*
 *  Obtains set-point value from OCS
 */
int HmdControl::GetSetPoint(double& sp)
{   
 if(!initialized_) return DEVICE_NOT_CONNECTED;
 int ret=OkolabDevice::GetSetPoint(sp);
 if(ret<0) DEVICE_ERR;
 return DEVICE_OK;                                                         
} 


/*
 *  Send set-point value to OCS
 */
int HmdControl::SetSetPoint(double sp)
{
 if(!initialized_) return DEVICE_NOT_CONNECTED;
 int ret=OkolabDevice::SetSetPoint(sp);
 if(ret<0) DEVICE_ERR;
 return DEVICE_OK;                                                         
} 


/*
 *  Test serial communication between OSC and device 
 */
MM::DeviceDetectionStatus HmdControl::DetectDevice(void)
{
 if(initialized_) return MM::CanCommunicate;
 return Detect();
}


int HmdControl::IsConnected()
{
 return this->connected_;
}


void HmdControl::UpdateGui()
{
 this->OnPropertiesChanged();
}


void HmdControl::UpdatePropertyGui(char *PropName, char *PropVal)
{
 this->OnPropertyChanged(PropName,PropVal);
}



void HmdControl::RefreshThread_Start()
{
 rthread_->Start();
}


void HmdControl::RefreshThread_Stop()
{
 rthread_->Stop();
}



///////////////////////////////////////////////////////////////////////////////
// Refresh Thread Class
///////////////////////////////////////////////////////////////////////////////


HmdControl_RefreshThread::HmdControl_RefreshThread(HmdControl &oDevice) :
   stop_(true), okoDevice_(oDevice)
{
 sleepmillis_=2000;
};


HmdControl_RefreshThread::~HmdControl_RefreshThread()
{
 Stop();
 wait();
}


int HmdControl_RefreshThread::svc() 
{
 char strVal[20]; 
 double v=0;
 while(!stop_)
  {
   if(okoDevice_.IsConnected()==1)
    {
     (void)okoDevice_.GetValue(v);
     snprintf(strVal,20,"%.02f",v);
     okoDevice_.UpdatePropertyGui("Humidity value",strVal);  
    }
   CDeviceUtils::SleepMs(sleepmillis_);
  }
 return DEVICE_OK;
}


void HmdControl_RefreshThread::Start()
{
 if(stop_) { stop_=false; activate(); }
}
