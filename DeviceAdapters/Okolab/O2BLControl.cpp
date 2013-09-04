///////////////////////////////////////////////////////////////////////////////
// FILE:          O2BLControl.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab device adapter for H201-T Unit-BL 
//                
// AUTHOR:        Domenico Mastronardi @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       This file is distributed under the BSD license.
//
// REVISIONS:     
//

#include "Okolab.h"
#include "O2BLControl.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* g_O2BLControl = "O2 Unit-BL";

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// H201BLControl implementation
//

O2BLControl::O2BLControl() 
{
 this->product_id=11;
 this->device_id=0;
 this->initialized_=false;
 this->connected_=0;
 this->port_="Undefined";

 InitializeDefaultErrorMessages();

 CreateProperty(MM::g_Keyword_Name, g_O2BLControl , MM::String, true);
 CreateProperty(MM::g_Keyword_Description, "Okolab O2 Unit-BL [1-20]", MM::String, true);

 CPropertyAction* pAct = new CPropertyAction (this, &O2BLControl::OnPort);      
 CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);         
}


O2BLControl::~O2BLControl()
{
 if(initialized_) Shutdown();
}


/**
* Obtains device name.
* Required by the MM::Device API.
*/
void O2BLControl::GetName(char* name) const
{
 CDeviceUtils::CopyLimitedString(name, g_O2BLControl);
}


/**
* Intializes the hardware.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well.
* Required by the MM::Device API.
*/
int O2BLControl::Initialize()
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

 CPropertyAction* pAct=new CPropertyAction(this, &O2BLControl::OnGetConc);
 ret=CreateProperty("O2 Concentration", "0.0", MM::Float, true, pAct);
 if(ret!=DEVICE_OK) return ret;                                                            

 CPropertyAction* pAct2=new CPropertyAction(this, &O2BLControl::OnGetConnected);
 ret=CreateProperty("Connected", "0", MM::Integer, true, pAct2);
 if(ret!=DEVICE_OK) return ret;                           

 CPropertyAction* pAct3=new CPropertyAction(this, &O2BLControl::OnGetVersion);
 ret=CreateProperty("Version", "Undefined", MM::String, true, pAct3);
 if(ret!=DEVICE_OK) return ret;                           

 CPropertyAction* pAct4=new CPropertyAction(this, &O2BLControl::OnCommPort);
 ret=CreateProperty("Serial Port", "Undefined", MM::String, true, pAct4);
 if(ret!=DEVICE_OK) return ret;                           

 CPropertyAction* pAct5=new CPropertyAction(this, &O2BLControl::OnSetPoint);
 ret=CreateProperty("Set-Point", "0.0", MM::Float, false, pAct5);
 if(ret!=DEVICE_OK) return ret;                           

 initialized_=true;

 rthread_ = new O2BLControl_RefreshThread(*this);

 RefreshThread_Start();

 return DEVICE_OK;
}


/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
*/
int O2BLControl::Shutdown()
{
 if(initialized_)
  {
   RefreshThread_Stop();
   delete(rthread_);
  }
 initialized_=false;
 return DEVICE_OK;
}


bool O2BLControl::WakeUp()
{
 return true; 
}


bool O2BLControl::Busy()
{
 return false;
}


///////////////////////////////////////////////////////////////////////////////
//  Action handlers
///////////////////////////////////////////////////////////////////////////////


int O2BLControl::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int O2BLControl::OnGetVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   GetVersion();
   pProp->Set(version_.c_str());
  }
 return DEVICE_OK;
}


int O2BLControl::OnGetConnected(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   long connected=0;
   if(GetConnected(connected)==DEVICE_OK) pProp->Set(connected);
  }
 return DEVICE_OK;     
}


int O2BLControl::OnCommPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   char strComPort[21];
   GetCommPort(strComPort);
   pProp->Set(strComPort);
  }
 else if(eAct==MM::AfterSet)
  {
   long cport=0;
   pProp->Get(cport);
   SetCommPort(cport);
  }
 return DEVICE_OK;     
}


int O2BLControl::OnGetConc(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   double conc=0;
   if(GetConc(conc)==DEVICE_OK) pProp->Set(conc);
  }
 return DEVICE_OK;     
}


int O2BLControl::OnSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   double sp=0;
   if(GetSetPoint(sp)==DEVICE_OK) pProp->Set(sp);
  }
 else if(eAct==MM::AfterSet)
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
int O2BLControl::GetVersion()
{
 version_=OkolabDevice::version_;
 return DEVICE_OK;
}


/*
 *  Obtains ComPort (witch connects OCS to this device)
 */
int O2BLControl::GetCommPort(char *strcomport)
{   
 strcpy(strcomport,"Undefined");
 if(!initialized_) return DEVICE_NOT_CONNECTED;
 int ret=OkolabDevice::GetCommPort(strcomport);
 if(ret<=0) return DEVICE_ERR;
 return DEVICE_OK;
} 


/*
 *  Set com port (1=COM1, 2=COM2 and so on...)
 */
int O2BLControl::SetCommPort(long& comport)
{
 if(!WakeUp()) return DEVICE_SERIAL_INVALID_RESPONSE;
 if(!initialized_) return DEVICE_NOT_CONNECTED;
 int ret=OkolabDevice::SetCommPort((int)comport);
 if(ret<0) DEVICE_ERR;
 return DEVICE_OK;     
}



/*
 *  Obtains connection status (betweeen OCS and this device)
 */
int O2BLControl::GetConnected(long& conn)
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
 *  Obtains temperature value from OCS
 */
int O2BLControl::GetConc(double& conc)
{
 if(!initialized_) return DEVICE_NOT_CONNECTED;
 int ret=GetValue(conc);
 if(ret<0) DEVICE_ERR;
 return DEVICE_OK;                                                         
} 


/*
 *  Obtains set-point value from OCS
 */
int O2BLControl::GetSetPoint(double& sp)
{   
 if(!initialized_) return DEVICE_NOT_CONNECTED;
 int ret=OkolabDevice::GetSetPoint(sp);
 if(ret<0) DEVICE_ERR;
 return DEVICE_OK;                                                         
} 


/*
 *  Send set-point value to OCS
 */
int O2BLControl::SetSetPoint(double sp)
{
 if(!initialized_) return DEVICE_NOT_CONNECTED;
 int ret=OkolabDevice::SetSetPoint(sp);
 if(ret<0) DEVICE_ERR;
 return DEVICE_OK;                                                         
} 


/*
 *  Test serial communication between OSC and device 
 */
MM::DeviceDetectionStatus O2BLControl::DetectDevice(void)
{
 if(initialized_) return MM::CanCommunicate;
 return Detect();
}


int O2BLControl::IsConnected()
{
 return this->connected_;
}


void O2BLControl::UpdateGui()
{
 this->OnPropertiesChanged();
}


void O2BLControl::UpdatePropertyGui(char *PropName, char *PropVal)
{
 this->OnPropertyChanged(PropName,PropVal);
}



void O2BLControl::RefreshThread_Start()
{
 rthread_->Start();
}


void O2BLControl::RefreshThread_Stop()
{
 rthread_->Stop();
}



///////////////////////////////////////////////////////////////////////////////
// Refresh Thread Class
///////////////////////////////////////////////////////////////////////////////


O2BLControl_RefreshThread::O2BLControl_RefreshThread(O2BLControl &oDevice) :
   stop_(true), okoDevice_(oDevice)
{
 sleepmillis_=2000;
};


O2BLControl_RefreshThread::~O2BLControl_RefreshThread()
{
 Stop();
 wait();
}


int O2BLControl_RefreshThread::svc() 
{
 char strVal[20]; 
 double v=0;
 while(!stop_)
  {
   if(okoDevice_.IsConnected()==1)
    {
     int ret=okoDevice_.GetValue(v);
     snprintf(strVal,20,"%.02f",v);
     okoDevice_.UpdatePropertyGui("O2 Concentration",strVal);  
//     CDeviceUtils::SleepMs(sleepmillis_);
//	 ret=okoDevice_.GetSetPoint(v);
//     snprintf(strVal,20,"%.02f",v);
//     okoDevice_.UpdatePropertyGui("set-Point",strVal);  
    }
   CDeviceUtils::SleepMs(sleepmillis_);
  }
 return DEVICE_OK;
}


void O2BLControl_RefreshThread::Start()
{
 if(stop_) { stop_=false; activate(); }
}
