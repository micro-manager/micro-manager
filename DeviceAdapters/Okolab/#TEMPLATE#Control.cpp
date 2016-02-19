///////////////////////////////////////////////////////////////////////////////
// FILE:          #TEMPLATE#Control.cpp
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
#include "#TEMPLATE#Control.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* g_#TEMPLATE#Control = "#TEMPLATE# Unit";

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// H201BLControl implementation
//

#TEMPLATE#Control::#TEMPLATE#Control() 
{
 this->product_id=8;        //    <<<<<<<<<<<<<<<< SETTARE QUESTO !!!!
 this->device_id=0;         //    <<<<<<<<<<<<<<<< SETTARE QUESTO !!!!
 this->initialized_=false;
 this->connected_=0;
 this->port_="Undefined";

 InitializeDefaultErrorMessages();

 CreateProperty(MM::g_Keyword_Name, g_#TEMPLATE#Control , MM::String, true);
 CreateProperty(MM::g_Keyword_Description, "#TEMPLATE#_____Es:Okolab H201 T Unit-BL", MM::String, true);

 CPropertyAction* pAct = new CPropertyAction (this, &#TEMPLATE#Control::OnPort);      
 CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);         
}


#TEMPLATE#Control::~#TEMPLATE#Control()
{
 if(initialized_) Shutdown();
}


/**
* Obtains device name.
* Required by the MM::Device API.
*/
void #TEMPLATE#Control::GetName(char* name) const
{
 CDeviceUtils::CopyLimitedString(name, g_#TEMPLATE#Control);
}


/**
* Intializes the hardware.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well.
* Required by the MM::Device API.
*/
int #TEMPLATE#Control::Initialize()
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

 CPropertyAction* pAct=new CPropertyAction(this, &#TEMPLATE#Control::OnGetTemp);
 ret=CreateProperty("Temperature", "0.0", MM::Float, true, pAct);
 if(ret!=DEVICE_OK) return ret;                                                            

 CPropertyAction* pAct1=new CPropertyAction(this, &#TEMPLATE#Control::OnGetConnected);
 ret=CreateProperty("Connected", "0", MM::Integer, true, pAct1);
 if(ret!=DEVICE_OK) return ret;                           

 CPropertyAction* pAct2=new CPropertyAction(this, &#TEMPLATE#Control::OnGetVersion);
 ret=CreateProperty("Version", "Undefined", MM::String, true, pAct2);
 if(ret!=DEVICE_OK) return ret;                           

 CPropertyAction* pAct3=new CPropertyAction(this, &#TEMPLATE#Control::OnCommPort);
 ret=CreateProperty("Serial Port", "1", MM::Integer, false, pAct3);
 if(ret!=DEVICE_OK) return ret;                           
 
 CPropertyAction* pAct4=new CPropertyAction(this, &#TEMPLATE#Control::OnSetPoint);
 ret=CreateProperty("Set-Point", "0.0", MM::Float, false, pAct4);
 if(ret!=DEVICE_OK) return ret;                           

 initialized_=true;

 rthread_ = new #TEMPLATE#Control_RefreshThread(*this);

 RefreshThread_Start();

 return DEVICE_OK;
}


/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
*/
int #TEMPLATE#Control::Shutdown()
{
 if(initialized_)
  {
   RefreshThread_Stop();
   delete(rthread_);
  }
 initialized_=false;
 return DEVICE_OK;
}


bool #TEMPLATE#Control::WakeUp()
{
 return true; 
}


bool #TEMPLATE#Control::Busy()
{
 return false;
}


///////////////////////////////////////////////////////////////////////////////
//  Action handlers
///////////////////////////////////////////////////////////////////////////////


int #TEMPLATE#Control::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int #TEMPLATE#Control::OnGetVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   GetVersion();
   pProp->Set(version_.c_str());
  }
 return DEVICE_OK;
}


int #TEMPLATE#Control::OnGetConnected(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   long connected=0;
   if(GetConnected(connected)==DEVICE_OK) pProp->Set(connected);
  }
 return DEVICE_OK;     
}


int #TEMPLATE#Control::OnCommPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int #TEMPLATE#Control::OnGetTemp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   double temperature=0;
   if(GetTemp(temperature)==DEVICE_OK) pProp->Set(temperature);
  }
 return DEVICE_OK;     
}


int #TEMPLATE#Control::OnSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
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
int #TEMPLATE#Control::GetVersion()
{
 version_=OkolabDevice::version_;
 return DEVICE_OK;
}


/*
 *  Obtains ComPort (witch connects OCS to this device)
 */
int #TEMPLATE#Control::GetCommPort(char *strcommport)
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
int #TEMPLATE#Control::SetCommPort(long& commport)
{
 if(!WakeUp()) return DEVICE_SERIAL_INVALID_RESPONSE;
 return DEVICE_OK;     
}



/*
 *  Obtains connection status (betweeen OCS and this device)
 */
int #TEMPLATE#Control::GetConnected(long& conn)
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
int #TEMPLATE#Control::GetTemp(double& temp)
{
 if(!initialized_) return DEVICE_NOT_CONNECTED;
 int ret=GetValue(temp);
 if(ret<0) DEVICE_ERR;
 return DEVICE_OK;                                                         
} 


/*
 *  Obtains set-point value from OCS
 */
int #TEMPLATE#Control::GetSetPoint(double& sp)
{   
 if(!initialized_) return DEVICE_NOT_CONNECTED;
 int ret=OkolabDevice::GetSetPoint(sp);
 if(ret<0) DEVICE_ERR;
 return DEVICE_OK;                                                         
} 


/*
 *  Send set-point value to OCS
 */
int #TEMPLATE#Control::SetSetPoint(double sp)
{
 if(!initialized_) return DEVICE_NOT_CONNECTED;
 int ret=OkolabDevice::SetSetPoint(sp);
 if(ret<0) DEVICE_ERR;
 return DEVICE_OK;                                                         
} 


/*
 *  Test serial communication between OSC and device 
 */
MM::DeviceDetectionStatus #TEMPLATE#Control::DetectDevice(void)
{
 if(initialized_) return MM::CanCommunicate;
 return Detect();
}


int #TEMPLATE#Control::IsConnected()
{
 return this->connected_;
}


void #TEMPLATE#Control::UpdateGui()
{
 this->OnPropertiesChanged();
}


void #TEMPLATE#Control::UpdatePropertyGui(char *PropName, char *PropVal)
{
 this->OnPropertyChanged(PropName,PropVal);
}



void #TEMPLATE#Control::RefreshThread_Start()
{
 rthread_->Start();
}


void #TEMPLATE#Control::RefreshThread_Stop()
{
 rthread_->Stop();
}



///////////////////////////////////////////////////////////////////////////////
// Refresh Thread Class
///////////////////////////////////////////////////////////////////////////////


#TEMPLATE#Control_RefreshThread::#TEMPLATE#Control_RefreshThread(#TEMPLATE#Control &oDevice) :
   stop_(true), okoDevice_(oDevice)
{
 sleepmillis_=2000;
};


#TEMPLATE#Control_RefreshThread::~#TEMPLATE#Control_RefreshThread()
{
 Stop();
 wait();
}


int #TEMPLATE#Control_RefreshThread::svc() 
{
 char strVal[20]; 
 double v=0;
 while(!stop_)
  {
   if(okoDevice_.IsConnected()==1)
    {
     int ret=okoDevice_.GetValue(v);
     snprintf(strVal,20,"%f",v);
     okoDevice_.UpdatePropertyGui("Temperature",strVal);  // <<<< ADEGUARE !!!!
    }
/*
FILE *fp;
fp=fopen("log_test_thread.txt","a");
fprintf(fp,"thread running; val=%f\n",v);
fclose(fp);
*/
   CDeviceUtils::SleepMs(sleepmillis_);
  }
 return DEVICE_OK;
}


void #TEMPLATE#Control_RefreshThread::Start()
{
 if(stop_) { stop_=false; activate(); }
}
