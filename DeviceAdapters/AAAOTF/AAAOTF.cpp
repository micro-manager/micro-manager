///////////////////////////////////////////////////////////////////////////////
// FILE:          AOTF.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   AA AOTF
//                
// AUTHOR:        Erwin Peterman 02/02/2010
//

#ifdef WIN32
   #include <windows.h>
#endif
#include "FixSnprintf.h"

#include "AAAOTF.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

#include <iostream>
#include <fstream>

const char* g_AOTF = "AAAOTF";
const char* g_mAOTF = "multiAAAOTF";
const char* g_Int = "Power (% of max)";
const char* g_Maxint = "Maximum intensity (dB)";
const char* g_mChannel = "Channels (8 bit word 1..255)";
const char* g_Channel_1 = "1";	
const char* g_Channel_2 = "2";		
const char* g_Channel_3 = "3";		
const char* g_Channel_4 = "4";		
const char* g_Channel_5 = "5";			
const char* g_Channel_6 = "6";			
const char* g_Channel_7 = "7";			
const char* g_Channel_8 = "8";			
const char* g_DelayBetweenChannels = "Delay between channels (ms)";


using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_AOTF, MM::ShutterDevice, "AAAOTF");
   RegisterDevice(g_mAOTF, MM::ShutterDevice, "multiAAAOTF");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;
  
   if (strcmp(deviceName, g_AOTF) == 0)
   {
       AOTF* s = new AOTF();
       return s;
   }

   if (strcmp(deviceName, g_mAOTF) == 0)
   {
       multiAOTF* s = new multiAOTF();
       return s;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

// General utility function:
int ClearPort(MM::Device& device, MM::Core& core, std::string port)
{
   // Clear contents of serial port 
   const int bufSize = 255;
   unsigned char clear[bufSize];                      
   unsigned long read = bufSize;
   int ret;                                                                   
   while (read == (unsigned) bufSize) 
   {                                                                     
      ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)                               
         return ret;                                               
   }
   return DEVICE_OK;                                                           
} 



AOTF::AOTF() :
   port_("Undefined"),
   state_(0),
   initialized_(false),
   activeChannel_(g_Channel_1),
   intensity_(100),
   maxintensity_(1900)
   /*,*/
   /*version_("Undefined")*/
{
   InitializeDefaultErrorMessages();
                                                                             
   // create pre-initialization properties                                   
   // ------------------------------------                                   
                                                                             
   // Name                                                                   
   CreateProperty(MM::g_Keyword_Name, g_AOTF, MM::String, true); 
                                                                             
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "AA AOTF Shutter Controller driver adapter", MM::String, true);
                                                                             
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &AOTF::OnPort);      
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);  

}                                                                            
                                                                             
AOTF::~AOTF()                                                            
{                                                                            

	Shutdown();                                                               
} 

void AOTF::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_AOTF);
}  

int AOTF::Initialize()
{
	ostringstream command;
	int i;


	if (initialized_)
      return DEVICE_OK;
      
   // set property list
   // -----------------

   // State
   //------------------


   CPropertyAction* pAct = new CPropertyAction(this, &AOTF::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);

   if (ret!=DEVICE_OK)
	   return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   // Intensity
   //--------------------
   pAct = new CPropertyAction(this, &AOTF::OnIntensity);
   ret = CreateProperty(g_Int, "100", MM::Float, false, pAct);
   if (ret!=DEVICE_OK)
	   return ret;
   SetPropertyLimits(g_Int, 0, 100);

   // Maximumintensity (in dB)
   //-------------------
   pAct = new CPropertyAction(this, &AOTF::OnMaxintensity);
   ret = CreateProperty(g_Maxint, "1900", MM::Integer, false, pAct);
   if (ret!=DEVICE_OK)
	   return ret;
   SetPropertyLimits(g_Maxint, 0, 2200);

   // The Channel we will act on
   // -------

   pAct = new CPropertyAction (this, &AOTF::OnChannel);
   ret = CreateProperty(MM::g_Keyword_Channel, g_Channel_1, MM::String, false, pAct);  

   vector<string> commands;                                                  
   commands.push_back(g_Channel_1);                                           
   commands.push_back(g_Channel_2);                                            
   commands.push_back(g_Channel_3);   
   commands.push_back(g_Channel_4);  
   commands.push_back(g_Channel_5);  
   commands.push_back(g_Channel_6);                                           
   commands.push_back(g_Channel_7);                                            
   commands.push_back(g_Channel_8);   

   ret = SetAllowedValues(MM::g_Keyword_Channel, commands);
   if (ret != DEVICE_OK)                                                     
      return ret;


   //switch AOTF to internal mode
   SendSerialCommand(port_.c_str(), "I0", "\r");
   if (ret != DEVICE_OK)                                                     
      return ret;

   // switch all channels off on startup instead of querying which one is open
   SetProperty(MM::g_Keyword_State , "0");

   ret = UpdateStatus();                                                 
   if (ret != DEVICE_OK)                                                     
      return ret;


	command.str("");
	for(i=1;i<=8;i++) {
		command<< "L" << i << "O0\r";
	}

	ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
	if (ret!=DEVICE_OK)
	   return ret;


   initialized_ = true;

   return DEVICE_OK;                                                         
}  

int AOTF::SetOpen(bool open)
{  
   long pos;
   if(open)
	   pos=1;
   else
	   pos=0;

   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
} 

int AOTF::GetOpen(bool& open)
{     
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);

   if (ret != DEVICE_OK)                                                     
      return ret;
   long pos = atol(buf);
	   pos==1 ? open=true : open = false;
   
   return DEVICE_OK;                                                         
} 



/**
 * Here we set the shutter to open or close
 */
int AOTF::SetShutterPosition(bool state)                              
{                                                                            
	ostringstream command;

   int test;
   test = atoi(activeChannel_.c_str());

   if (state == false)
	   command<< "L" << test << "O0";
   else
	   command<< "L" << test << "O1";

   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret!=DEVICE_OK)
	   return ret;

   state_ = state ? 1 : 0;
   return DEVICE_OK;

}




/**
 * Here we set the intensity
 */
int AOTF::SetIntensity(double intensity)
{                                                                            	
   ostringstream command;
   int test;

   test = atoi(activeChannel_.c_str());

   //ofstream out("test.txt");

   //divide intensity by 100 to get dBm
   command<< "L" << test << "D" << intensity*maxintensity_/10000 ;

   //out << command.str().c_str() << "\n";

   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret!=DEVICE_OK)
	   return ret;

   intensity_ = intensity;
   return DEVICE_OK;
   
   //out.close();

}


int AOTF::Shutdown()                                                
{                                                                            
   if (initialized_)                                                         
   {                                                                         
      initialized_ = false;                                                  
   }                                                                         
   return DEVICE_OK;                                                         
}                                                                            

// Never busy because all commands block
bool AOTF::Busy()
{
   return false;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int AOTF::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
                                                                             
      pProp->Get(port_);                                                     
   }                                                                         
   return DEVICE_OK;     
}

int AOTF::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {                                                                         
      // instead of relying on stored state we could actually query the device
      pProp->Set((long)state_);                                                          
   }                                                                         
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);

      return SetShutterPosition(pos == 0 ? false : true);
   }
   return DEVICE_OK;
}


int AOTF::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{

	if (eAct == MM::BeforeGet)
   {                                                                         
      // instead of relying on stored state we could actually query the device
      pProp->Set((double)intensity_);                                                          
   }                                                                         
   else if (eAct == MM::AfterSet)
   {
      double pos;
      pProp->Get(pos);
      return SetIntensity(pos);
   }
   return DEVICE_OK;
}

int AOTF::OnMaxintensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((float)maxintensity_);
   }
   else if (eAct == MM::AfterSet)
   {
      long tmpMaxint;
      pProp->Get(tmpMaxint);
      if (tmpMaxint != maxintensity_) {
         maxintensity_ = tmpMaxint;
      }
   }
   return DEVICE_OK;;
}
int AOTF::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(activeChannel_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      // if there is a channel change and the shutter was open, re-open in the new position
      std::string tmpChannel;
      pProp->Get(tmpChannel);
      if (tmpChannel != activeChannel_) {
         activeChannel_ = tmpChannel;
		 if (state_ == 1)
            SetShutterPosition(true);
      }
      // It might be a good idea to close the shutter at this point...
   }
   return DEVICE_OK;
}


/// Here we define the multiline shutter device to operate multiple lines simultaneously

multiAOTF::multiAOTF() :
   port_("Undefined"),
   state_(0),
   initialized_(false),
   activeMultiChannels_(1),
   //intensity_(1900)
   /*,*/
   /*version_("Undefined")*/
   delayBetweenChannels_(0)
{
   InitializeDefaultErrorMessages();
                                                                             
   // create pre-initialization properties                                   
   // ------------------------------------                                   
                                                                             
   // Name                                                                   
   CreateProperty(MM::g_Keyword_Name, g_mAOTF, MM::String, true); 
                                                                             
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "multiline AA AOTF Shutter Controller driver adapter", MM::String, true);
                                                                             
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &multiAOTF::OnPort);      
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);  

}                                                                            
                                                                             
multiAOTF::~multiAOTF()                                                            
{                                                                            

	Shutdown();                                                               
} 

void multiAOTF::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_mAOTF);
}  

int multiAOTF::Initialize()
{
	ostringstream command;
	int i;


	if (initialized_)
      return DEVICE_OK;
      
   // set property list
   // -----------------

   // State
   //------------------


   CPropertyAction* pAct = new CPropertyAction(this, &multiAOTF::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);

   if (ret!=DEVICE_OK)
	   return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   pAct = new CPropertyAction(this, &multiAOTF::OnDelayBetweenChannels);
   ostringstream dbc; dbc << delayBetweenChannels_;
   ret = CreateProperty(g_DelayBetweenChannels, dbc.str().c_str(), MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Intensity
   //--------------------
   //pAct = new CPropertyAction(this, &AOTF::OnIntensity);
   //ret = CreateProperty(g_Int, "200", MM::Integer, false, pAct);
   //if (ret!=DEVICE_OK)
	  // return ret;
   //SetPropertyLimits(g_Int, 0, 2200);

   // The Channel we will act on
   // -------

   pAct = new CPropertyAction (this, &multiAOTF::OnChannel);
   ret = CreateProperty(g_mChannel, "200", MM::Integer, false, pAct);     

   ret = SetPropertyLimits(g_mChannel, 1,255);
   if (ret != DEVICE_OK)                                                     
      return ret;


   //switch AOTF to internal mode
   SendSerialCommand(port_.c_str(), "I0", "\r");
   if (ret != DEVICE_OK)                                                     
      return ret;

   // switch all channels off on startup instead of querying which one is open
   SetProperty(MM::g_Keyword_State , "0");

   ret = UpdateStatus();                                                 
   if (ret != DEVICE_OK)                                                     
      return ret;

   for (i = 1; i <= 8; i++) {
      // Use fixed large delay here, since user has not had a chance to set
      // delayBetweenChannels_.
      CDeviceUtils::SleepMs(50);
      command.str("");
      command << "L" << i << "O0";
      ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;
   }

   initialized_ = true;

   return DEVICE_OK;                                                         
}  

int multiAOTF::SetOpen(bool open)
{  
   long pos;
   if(open)
	   pos=1;
   else
	   pos=0;

   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
} 

int multiAOTF::GetOpen(bool& open)
{     
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);

   if (ret != DEVICE_OK)                                                     
      return ret;
   long pos = atol(buf);
	   pos==1 ? open=true : open = false;
   
   return DEVICE_OK;                                                         
} 



/**
 * Here we set the shutter to open or close
 */
int multiAOTF::SetShutterPosition(bool state)                              
{                                                                            
	ostringstream command;

   int i;
   int ret;

   for (i = 1; i <= 8; i++) {
      command.str("");
      command << "L" << i;
      int channelBit = 1 << (i - 1);
      if (state && (activeMultiChannels_ & channelBit))
         command << "O1";
      else
         command << "O0";

      ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;

      if (delayBetweenChannels_ > 0.0)
         CDeviceUtils::SleepMs((long)ceil(delayBetweenChannels_));
   }

   state_ = state ? 1 : 0;
   return DEVICE_OK;

}





int multiAOTF::Shutdown()                                                
{                                                                            
   if (initialized_)                                                         
   {                                                                         
      initialized_ = false;                                                  
   }                                                                         
   return DEVICE_OK;                                                         
}                                                                            

// Never busy because all commands block
bool multiAOTF::Busy()
{
   return false;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int multiAOTF::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
                                                                             
      pProp->Get(port_);                                                     
   }                                                                         
   return DEVICE_OK;     
}

int multiAOTF::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {                                                                         
      // instead of relying on stored state we could actually query the device
      pProp->Set((long)state_);                                                          
   }                                                                         
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);

      return SetShutterPosition(pos == 0 ? false : true);
   }
   return DEVICE_OK;
}



int multiAOTF::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)activeMultiChannels_);
   }
   else if (eAct == MM::AfterSet)
   {
      // if there is a channel change and the shutter was open, re-open in the new position
      long tmpChannel;
      pProp->Get(tmpChannel);
      if (tmpChannel != activeMultiChannels_) {
         activeMultiChannels_ = tmpChannel;
		 if (state_ == 1)
            SetShutterPosition(true);
      }
      // It might be a good idea to close the shutter at this point...
   }
   return DEVICE_OK;
}

 
int multiAOTF::OnDelayBetweenChannels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(delayBetweenChannels_);
   else if (eAct == MM::AfterSet) {
      double wk;
      pProp->Get(wk);
      delayBetweenChannels_ = max(0.0, wk);
   }
   return DEVICE_OK;
}
