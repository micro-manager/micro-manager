///////////////////////////////////////////////////////////////////////////////
// FILE:          H101CryoControl.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab device adapter.
//                
// AUTHOR:        Domenico Mastronardi @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       
//

#include "Okolab.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* g_H101CryoControl  = "H101 Cryo Control";


///////////////////////////////////////////////////////////////////////////////
// H101CryoControl implementation
//

H101CryoControl::H101CryoControl() :
   initialized_(false)  
{
 InitializeDefaultErrorMessages();

 CreateProperty(MM::g_Keyword_Name, g_H101CryoControl , MM::String, true);
 CreateProperty(MM::g_Keyword_Description, "Okolab H101 Cryo Control adapter", MM::String, true);
}


H101CryoControl::~H101CryoControl()
{
 if(initialized_) Shutdown();
}


/**
* Obtains device name.
* Required by the MM::Device API.
*/
void H101CryoControl::GetName(char* name) const
{
 CDeviceUtils::CopyLimitedString(name, g_H101CryoControl);
}


/**
* Intializes the hardware.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well.
* Required by the MM::Device API.
*/
int H101CryoControl::Initialize()
{
 if(initialized_) return DEVICE_OK;

 CPropertyAction* pAct=new CPropertyAction(this, &H101CryoControl::OnGetTemp);
 int ret=CreateProperty("Temperature", "0.0", MM::Float, true, pAct);
 if(ret!=DEVICE_OK) return ret;                                                            

 initialized_=true;
 return DEVICE_OK;
}


/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
*/
int H101CryoControl::Shutdown()
{
 initialized_ = false;
 return DEVICE_OK;
}


bool H101CryoControl::WakeUp()
{
 return true; 
}


bool H101CryoControl::Busy()
{
 return false;
}


int H101CryoControl::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct == MM::BeforeGet)
  {
   pProp->Set(port_.c_str());
  }
 else if(eAct == MM::AfterSet)
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


int H101CryoControl::OnGetTemp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   double temperature=0;
   if(GetTemp(temperature)==DEVICE_OK) pProp->Set(temperature);
  }
 return DEVICE_OK;     
}



int H101CryoControl::GetTemp(double& temp)
{   
 if(!initialized_) return DEVICE_ERR;
 if(!OCSConnectionOpen(ipport_,ipaddress_)) return DEVICE_ERR;
 if(!OCSSendRcvdCommand("readval","1","-1","-1")) return DEVICE_ERR; 
 OCSConnectionClose();
 if(rcv_statuscode!=0) return DEVICE_ERR;
 temp=atof(rcv_answer);
 return DEVICE_OK;                                                         
} 



MM::DeviceDetectionStatus H101CryoControl::DetectDevice(void)
{
 int iport=-1;
 char strPort[3];
 if(initialized_) return MM::CanCommunicate;

 // all conditions must be satisfied...
 MM::DeviceDetectionStatus result = MM::Misconfigured;
   
 try
  {
   std::string portLowerCase = port_;
   for(std::string::iterator its=portLowerCase.begin(); its!=portLowerCase.end(); ++its)
    {
     *its=(char)tolower(*its);
    }

   if(0<portLowerCase.length() &&  0!=portLowerCase.compare("undefined")  && 0!=portLowerCase.compare("unknown") )
    {
     result=MM::CanNotCommunicate;

	 if(portLowerCase.compare("com1")==0) iport=1;
     else if(portLowerCase.compare("com2")==0) iport=2;
     else if(portLowerCase.compare("com3")==0) iport=3;
     else if(portLowerCase.compare("com4")==0) iport=4;
     if(iport<0) return MM::CanNotCommunicate;

     sprintf(strPort,"%d",iport);
     if(!OCSConnectionOpen(ipport_,ipaddress_)) return MM::CanNotCommunicate;
     if(!OCSSendRcvdCommand("comport","1","0",strPort)) return MM::CanNotCommunicate; 
     OCSConnectionClose();
     if(rcv_statuscode!=0) return MM::CanNotCommunicate;
     if(!strcmp(rcv_answer,"1")) return MM::CanNotCommunicate;

     if(!OCSConnectionOpen(ipport_,ipaddress_)) return MM::CanNotCommunicate;
     if(!OCSSendRcvdCommand("checkcon","1","0","")) return MM::CanNotCommunicate; 
     OCSConnectionClose();
     if(rcv_statuscode!=0) return MM::CanNotCommunicate;
     if(!strcmp(rcv_answer,"1")) return MM::CanNotCommunicate;

	 result=MM::CanCommunicate;
    }
   }
  catch(...)
   {
    LogMessage("Exception in DetectDevice!",false);
   }
 return result;
}
