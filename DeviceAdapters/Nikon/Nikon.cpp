///////////////////////////////////////////////////////////////////////////////
// FILE:          Nikon.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Nikon Remote Focus Accessory driver
//                TIRF shutter T-LUSU(2)
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/28/2006
// COPYRIGHT:     University of California, San Francisco, 2006
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
// CVS:           $Id$
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Nikon.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* g_ZStageDeviceName = "ZStage";
const char* g_TIRFShutterController = "TIRFShutter";
const char* g_TiTIRFShutterController = "TiTIRFShutter";
const char* g_IntensiLightShutter = "IntensiLightShutter";
const char* g_Channel_1 = "1";
const char* g_Channel_2 = "2";
const char* g_Channel_3 = "3";
const char* g_Nd1 = "1";
const char* g_Nd2 = "2";
const char* g_Nd3 = "4";
const char* g_Nd4 = "8";
const char* g_Nd5 = "16";
const char* g_Nd6 = "32";


using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ZStageDeviceName, MM::StageDevice, "Remote accessory Z-stage");
   RegisterDevice(g_TIRFShutterController, MM::ShutterDevice, "TIRF Laser Shutter controller T-LUSU(2)");
   RegisterDevice(g_TiTIRFShutterController, MM::ShutterDevice, "Ti-TIRF Laser Shutter controller T-LUSU(2)");
   RegisterDevice(g_IntensiLightShutter, MM::ShutterDevice, "IntensiLight Shutter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ZStageDeviceName) == 0)
   {
      ZStage* s = new ZStage();
      return s;
   }
   if (strcmp(deviceName, g_TIRFShutterController) == 0)
   {
      TIRFShutter* s = new TIRFShutter();
      return s;
   }
   if (strcmp(deviceName, g_TiTIRFShutterController) == 0)
   {
      TiTIRFShutter* s = new TiTIRFShutter();
      return s;
   }
   if (strcmp(deviceName, g_IntensiLightShutter) == 0)
   {
      IntensiLightShutter* s = new IntensiLightShutter();
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
 

///////////////////////////////////////////////////////////////////////////////
// ZStage

ZStage::ZStage() :
   port_("Undefined"),
   initialized_(false),
   stepSizeUm_(0.1),
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ZStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Nikon Remote Focus Accessory driver adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

ZStage::~ZStage()
{
   Shutdown();
}

void ZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZStageDeviceName);
}

int ZStage::Initialize()
{
   int ret = GetPositionSteps(curSteps_);
   if (ret != DEVICE_OK)
      return ret;

   // StepSize
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnStepSizeUm);
   CreateProperty("StepSizeUm", "0.1", MM::Float, false, pAct);
   stepSizeUm_ = 0.1;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int ZStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool ZStage::Busy()
{
   // never busy because all commands block
   return false;
}

int ZStage::SetPositionUm(double pos)
{
   long steps = (long) (pos / stepSizeUm_ + 0.5);
   return SetPositionSteps(steps);
}

int ZStage::GetPositionUm(double& pos)
{
   long steps;
   int ret = GetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;
   pos = steps * stepSizeUm_;
   return DEVICE_OK;
}
  
int ZStage::SetPositionSteps(long pos)
{
   ostringstream command;
   command << "MZ " << pos;

   // send command
   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.substr(0, 2).compare(":A") == 0)
   {
      curSteps_ = pos;
      return DEVICE_OK;
   }
   else if (answer.length() > 2 && answer.substr(0, 2).compare(":N") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }

   return ERR_UNRECOGNIZED_ANSWER;   
}
  
int ZStage::GetPositionSteps(long& steps)
{
   const char* command="WZ";

   // send command
   int ret = SendSerialCommand(port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return ret;

   // block/wait for acknowledge, or until we time out;
   string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer.length() > 2 && answer.substr(0, 2).compare(":N") == 0)
   {
      int errNo = atoi(answer.substr(2).c_str());
      return ERR_OFFSET + errNo;
   }
   else if (answer.length() > 2 && answer.substr(0, 2).compare(":A") == 0)
   {
      steps = atol(answer.substr(2).c_str());
      curSteps_ = steps;
      return DEVICE_OK;
   }

   return ERR_UNRECOGNIZED_ANSWER;
}

int ZStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int ZStage::GetLimits(double& /*min*/, double& /*max*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int ZStage::OnStepSizeUm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(stepSizeUm_);
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// TIRFShutter

TIRFShutter::TIRFShutter() :
   port_("Undefined"),
   state_(0),
   initialized_(false),
   activeChannel_(g_Channel_1),
   version_("Undefined")
{
   InitializeDefaultErrorMessages();
                                                                             
   // create pre-initialization properties                                   
   // ------------------------------------                                   
                                                                             
   // Name                                                                   
   CreateProperty(MM::g_Keyword_Name, g_TIRFShutterController, MM::String, true); 
                                                                             
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Nikon TIRFS Shutter Controller T-LUSU driver adapter", MM::String, true);
                                                                             
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &TIRFShutter::OnPort);      
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);         
}                                                                            
                                                                             
TIRFShutter::~TIRFShutter()                                                            
{                                                                            
   Shutdown();                                                               
} 

void TIRFShutter::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TIRFShutterController);
}  

int TIRFShutter::Initialize()
{
   if (initialized_)
      return DEVICE_OK;
      
   // set property list
   // -----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &TIRFShutter::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;                                                            
                                                                             
   AddAllowedValue(MM::g_Keyword_State, "0");                                
   AddAllowedValue(MM::g_Keyword_State, "1");                                
                                              
   // The Channel we will act on
   // ----
   pAct = new CPropertyAction (this, &TIRFShutter::OnChannel);
   ret=CreateProperty("Channel", g_Channel_1, MM::String, false, pAct);  

   vector<string> commands;                                                  
   commands.push_back(g_Channel_1);                                           
   commands.push_back(g_Channel_2);                                            
   commands.push_back(g_Channel_3);                                         
   ret = SetAllowedValues("Channel", commands);        
   if (ret != DEVICE_OK)                                                    
      return ret;

   // get the version number
   pAct = new CPropertyAction(this,&TIRFShutter::OnVersion);

   // If GetVersion fails we are not talking to the TIRFShutter
   ret = GetVersion();
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            

   ret = CreateProperty("Version", version_.c_str(), MM::String,true,pAct); 

   // switch all channels off on startup instead of querying which on is open
   SetProperty(MM::g_Keyword_State, "0");

   ret = UpdateStatus();                                                 
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
                                                                             
   initialized_ = true;                                                      
   return DEVICE_OK;                                                         
}  

int TIRFShutter::SetOpen(bool open)
{  
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
} 

int TIRFShutter::GetOpen(bool& open)
{     
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
   long pos = atol(buf);                                                     
   pos == 1 ? open = true : open = false;                                    
   return DEVICE_OK;                                                         
} 

/**
 * Here we set the shutter to open or close
 */
int TIRFShutter::SetShutterPosition(bool state)                              
{                                                                            
   std::string command;                                                    
                                                                             
   if (state == false)                                                       
   {                                                                         
      command = "cTSC"; // close                                                  
   }                                                                         
   else                                                                      
   {                                                                         
      command = "cTSO" + activeChannel_; // open                                       
      //command += 0x0D;
   }                                                                         
   //if (DEVICE_OK != WriteToComPort(port_.c_str(), command.c_str(), 1))   
   int ret = SendSerialCommand(port_.c_str(), command.c_str(), "\r");   
   if (ret != DEVICE_OK)
      return ret;
                                                                             
   // block/wait for acknowledge                     
   std::string answer;                                                          
   ret = GetSerialAnswer(port_.c_str(), "\n", answer); 
   if (ret != DEVICE_OK) {
      LogMessage("No answer from TIRF shutter");
      return ret;
   }
   
   // "oTSC" or "oTSO" signals success
   if (answer[0]=='o')
   {
      state_ = state ? 1 : 0;
      return DEVICE_OK;
   }

   if (answer[0]=='n') 
   { 
      int errNo = atoi(answer.substr(6).c_str());
      return ERR_TIRFSHUTTER_OFFSET + errNo;
   }

   return DEVICE_SERIAL_INVALID_RESPONSE;
}


int TIRFShutter::GetVersion()
{
   std::string command = "rVER";
   int ret = SendSerialCommand(port_.c_str(), command.c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;
                                                                             
   // block/wait for acknowledge                     
   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0]== 'a') 
   {
      version_ = answer.substr(4,5);
      return DEVICE_OK;
   }
   else if (answer[0] =='n') 
   {
      int errNo = atoi(answer.substr(4).c_str());
      return ERR_TIRFSHUTTER_OFFSET + errNo;
   }
   return DEVICE_SERIAL_INVALID_RESPONSE;
}


int TIRFShutter::Shutdown()                                                
{                                                                            
   if (initialized_)                                                         
   {                                                                         
      initialized_ = false;                                                  
   }                                                                         
   return DEVICE_OK;                                                         
}                                                                            

// Never busy because all commands block
bool TIRFShutter::Busy()
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
int TIRFShutter::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int TIRFShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int TIRFShutter::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
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

 
int TIRFShutter::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet && version_ == "Undefined")
   {
      int ret = GetVersion();
      if (ret != DEVICE_OK) 
         return ret;
      pProp->Set(version_.c_str());
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// TiTIRFShutter

TiTIRFShutter::TiTIRFShutter() :
   port_("Undefined"),
   state_(0),
   initialized_(false),
   activeChannel_(g_Channel_1),
   mode_(0),
   version_("Undefined")
{
   InitializeDefaultErrorMessages();
                                                                             
   // create pre-initialization properties                                   
   // ------------------------------------                                   
                                                                             
   // Name                                                                   
   CreateProperty(MM::g_Keyword_Name, g_TiTIRFShutterController, MM::String, true); 
                                                                             
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Nikon Ti-TIRF Shutter Controller", MM::String, true);
                                                                             
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &TiTIRFShutter::OnPort);      
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);         
}                                                                            
                                                                             
TiTIRFShutter::~TiTIRFShutter()                                                            
{                                                                            
   Shutdown();                                                               
} 

void TiTIRFShutter::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TiTIRFShutterController);
}  

int TiTIRFShutter::Initialize()
{
   if (initialized_)
      return DEVICE_OK;
      
   // If GetVersion fails we are not talking to the TIRFShutter
   int ret = GetVersion();
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            

   // set property list
   // -----------------

   // State - on/off state
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &TiTIRFShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;                                                            
                                                                             
   AddAllowedValue(MM::g_Keyword_State, "0");                                
   AddAllowedValue(MM::g_Keyword_State, "1");                                
                                              
   // find out if this unit is set to shutter independent control mode
   ret = GetMode(mode_);
   if (ret != DEVICE_OK) 
      return ret;
   std::ostringstream os;
   os << "Ti-TIRF shutter operates in mode: " << mode_;
   LogMessage(os.str(), false);

   // The Channel we will act on
   // ----
   pAct = new CPropertyAction (this, &TiTIRFShutter::OnChannel);
   ret=CreateProperty("Channel", g_Channel_1, MM::String, false, pAct);  

   if (mode_ == 0) 
   {
      vector<string> commands;                                                  
      commands.push_back(g_Channel_1);                                           
      commands.push_back(g_Channel_2);                                            
      commands.push_back(g_Channel_3);                                         
      ret = SetAllowedValues("Channel", commands);        
      if (ret != DEVICE_OK)                                                    
         return ret;
   }
   else if (mode_ == 1) {
      vector<string> commands;                                                  
      commands.push_back(g_Channel_1);                                           
      commands.push_back(g_Channel_2);                                            
      commands.push_back(g_Channel_3);                                         
      commands.push_back(std::string(g_Channel_1) + "+" + std::string(g_Channel_2));
      commands.push_back(std::string(g_Channel_1) + "+" + std::string(g_Channel_3));
      commands.push_back(std::string(g_Channel_2) + "+" + std::string(g_Channel_3));
      commands.push_back(std::string(g_Channel_1) + "+" + std::string(g_Channel_2) + "+" + std::string(g_Channel_3) );
      ret = SetAllowedValues("Channel", commands);        
      if (ret != DEVICE_OK)                                                    
         return ret;
   }

   // get the version number
   pAct = new CPropertyAction(this,&TiTIRFShutter::OnVersion);
   ret = CreateProperty("Version", version_.c_str(), MM::String,true,pAct); 

   // switch all channels off on startup instead of querying which on is open
   SetProperty(MM::g_Keyword_State, "0");

   ret = UpdateStatus();                                                 
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
                                                                             
   initialized_ = true;                                                      
   return DEVICE_OK;                                                         
}  

int TiTIRFShutter::SetOpen(bool open)
{  
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
} 

int TiTIRFShutter::GetOpen(bool& open)
{     
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
   long pos = atol(buf);                                                     
   pos == 1 ? open = true : open = false;                                    
   return DEVICE_OK;                                                         
} 

/**
 * Here we set the shutter to open or close
 */
int TiTIRFShutter::SetShutterPosition(bool state)                              
{                                                                            
   std::string command;                                                    
                                                                             
   if (state == false)                                                       
   {                                                                         
      command = "cTSC"; // close                                                  
   }                                                                         
   else                                                                      
   {                                                                         
      if (mode_ == 0) 
         command = "cTSO" + activeChannel_; // open                                       
      else // mode_ == 1
      {
         std::vector<std::string> chs = Explode(activeChannel_, '+');
         int ch = 0;
         for (int i=0; i < chs.size(); i++) {
            if (chs[i] == g_Channel_1) 
               ch+=1;
            if (chs[i] == g_Channel_2)
               ch+=2;
            if (chs[i] == g_Channel_3)
               ch+=4;
         }
         std::ostringstream os;
         os << "cTSD" <<  ch;
         command = os.str();
      }
   }                                                                         
   int ret = SendSerialCommand(port_.c_str(), command.c_str(), "\r");   
   if (ret != DEVICE_OK)
      return ret;
                                                                             
   // block/wait for acknowledge                     
   std::string answer;                                                          
   ret = GetSerialAnswer(port_.c_str(), "\n", answer); 
   if (ret != DEVICE_OK) {
      LogMessage("No answer from TIRF shutter");
      return ret;
   }
   
   // "oTSC" or "oTSO" signals success
   if (answer[0]=='o')
   {
      state_ = state ? 1 : 0;
      return DEVICE_OK;
   }

   if (answer[0]=='n') 
   { 
      int errNo = atoi(answer.substr(6).c_str());
      return ERR_TIRFSHUTTER_OFFSET + errNo;
   }

   return DEVICE_SERIAL_INVALID_RESPONSE;
}


int TiTIRFShutter::GetVersion()
{
   std::string command = "rVER";
   int ret = SendSerialCommand(port_.c_str(), command.c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;
                                                                             
   // block/wait for acknowledge                     
   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0]== 'a') 
   {
      version_ = answer.substr(4,5);
      return DEVICE_OK;
   }
   else if (answer[0] =='n') 
   {
      int errNo = atoi(answer.substr(4).c_str());
      return ERR_TIRFSHUTTER_OFFSET + errNo;
   }
   return DEVICE_SERIAL_INVALID_RESPONSE;
}


int TiTIRFShutter::GetMode(int& mode)
{
   std::string command = "rTEX";
   int ret = SendSerialCommand(port_.c_str(), command.c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;
                                                                             
   // block/wait for acknowledge                     
   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0]== 'a') 
   {
      mode = atoi(answer.substr(4,5).c_str());
      return DEVICE_OK;
   }
   else if (answer[0] =='n') 
   {
      int errNo = atoi(answer.substr(4).c_str());
      return ERR_TIRFSHUTTER_OFFSET + errNo;
   }
   return DEVICE_SERIAL_INVALID_RESPONSE;
}


int TiTIRFShutter::Shutdown()                                                
{                                                                            
   if (initialized_)                                                         
   {                                                                         
      initialized_ = false;                                                  
   }                                                                         
   return DEVICE_OK;                                                         
}                                                                            

// Never busy because all commands block
bool TiTIRFShutter::Busy()
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
int TiTIRFShutter::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int TiTIRFShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int TiTIRFShutter::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
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

 
int TiTIRFShutter::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet && version_ == "Undefined")
   {
      int ret = GetVersion();
      if (ret != DEVICE_OK) 
         return ret;
      pProp->Set(version_.c_str());
   }
   return DEVICE_OK;
}

std::vector<std::string> TiTIRFShutter::Explode(std::string const & s, char delim)
{
    std::vector<std::string> result;
    std::istringstream iss(s);

    for (std::string token; std::getline(iss, token, delim); )
    {
        result.push_back(std::move(token));
    }

    return result;
}

///////////////////////////////////////////////////////////////////////////////
// IntensiLightShutter

IntensiLightShutter::IntensiLightShutter() :
   initialized_(false),
   port_("Undefined"),
   state_(0),
   version_("Undefined")
{
   InitializeDefaultErrorMessages();
                                                                             
   // create pre-initialization properties                                   
   // ------------------------------------                                   
                                                                             
   // Name                                                                   
   CreateProperty(MM::g_Keyword_Name, g_IntensiLightShutter, MM::String, true); 
                                                                             
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Nikon IntensiLight Shutter adapter", MM::String, true);
                                                                             
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &IntensiLightShutter::OnPort);      
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);         
}                                                                            
                                                                             
IntensiLightShutter::~IntensiLightShutter()                                                            
{                                                                            
   Shutdown();                                                               
} 

void IntensiLightShutter::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_IntensiLightShutter);
}  

int IntensiLightShutter::Initialize()
{
   if (initialized_)
      return DEVICE_OK;
      
   // get the version number
   CPropertyAction* pAct = new CPropertyAction(this,&IntensiLightShutter::OnVersion);

   // If GetVersion fails we are not talking to the IntensiLight
   int ret = GetVersion();
   if (ret >= 10300) 
   {
      // error coming from the IntesiLight, try once more.
      ret = GetVersion();
   }
   if (ret != DEVICE_OK)
   {
      return ret;                                                            
   }

   ret = CreateProperty("Version", version_.c_str(), MM::String,true,pAct); 

   // State
   // -----
   pAct = new CPropertyAction (this, &IntensiLightShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;                                                            
                                                                             
   AddAllowedValue(MM::g_Keyword_State, "0");                                
   AddAllowedValue(MM::g_Keyword_State, "1");                                
                                              

   pAct = new CPropertyAction(this, &IntensiLightShutter::OnND);
   ret = CreateProperty("ND", g_Nd1, MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue("ND", g_Nd1);
   AddAllowedValue("ND", g_Nd2);
   AddAllowedValue("ND", g_Nd3);
   AddAllowedValue("ND", g_Nd4);
   AddAllowedValue("ND", g_Nd5);
   AddAllowedValue("ND", g_Nd6);

   // switch all channels off on startup instead of querying which on is open
   // SetProperty(MM::g_Keyword_State, "0");

   // ret = UpdateStatus();                                                 
   // if (ret != DEVICE_OK)                                                     
   //   return ret;                                                            
                                                                             
   initialized_ = true;                                                      
   return DEVICE_OK;                                                         
}  

int IntensiLightShutter::SetOpen(bool open)
{  
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
} 

int IntensiLightShutter::GetOpen(bool& open)
{     
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
   long pos = atol(buf);                                                     
   pos == 1 ? open = true : open = false;                                    
   return DEVICE_OK;                                                         
} 

/**
 * Here we set the shutter to open or close
 * Note, we send a command that does not evoke a 'received command' response.
 * However, we do wait until the action is completed
 * This might not be the most optimal way of doing this, so change when you see a better way
 */
int IntensiLightShutter::SetShutterPosition(bool state)                              
{                                                                            
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str());
   std::string command;                                                    
                                                                             
   if (state == false) { 
      command = "cSXC2"; // close                                                  
   }                                                                         
   else {                                                                      
      command = "cSXC1"; // open                                       
   }                                                                         

   int ret = SendSerialCommand(port_.c_str(), command.c_str(), "\r");   
   if (ret != DEVICE_OK)
      return ret;
                                                                             
   // block/wait for acknowledge                     
   std::string answer;                                                          
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer); 
   if (ret != DEVICE_OK) {
      LogMessage("No answer from IntensiLight");
      return ret;
   }
   
   if (answer[0]=='n') {
      int errNo = atoi(answer.substr(4).c_str());
      return ERR_INTENSILIGHTSHUTTER_OFFSET + errNo;
   }

   if (answer.substr(1,3) =="SXC") {
      state_ = state ? 1 : 0;
      return DEVICE_OK;
   }


   return DEVICE_SERIAL_INVALID_RESPONSE;
}

/**
 *
 */
int IntensiLightShutter::GetShutterPosition(bool& state)                              
{                                                                            
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str());
   std::string command = "rSXR";;

   int ret = SendSerialCommand(port_.c_str(), command.c_str(), "\r");   
   if (ret != DEVICE_OK)
      return ret;
                                                                             
   // block/wait for acknowledge                     
   std::string answer;                                                          
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer); 
   if (ret != DEVICE_OK) {
      LogMessage("No answer from IntensiLight");
      return ret;
   }
   
   if (answer[0]=='n') {
      int errNo = atoi(answer.substr(4).c_str());
      return ERR_INTENSILIGHTSHUTTER_OFFSET + errNo;
   }

   if (answer.substr(1,3) =="SXR") {
      state = false;
      int nr = atoi(answer.substr(4,1).c_str());
      if (nr == 1)
         state = true;
      return DEVICE_OK;
   }

   return DEVICE_SERIAL_INVALID_RESPONSE;
}


/**
 * Here we set the shutter to open or close
 * Note, we send a command that does not evoke a 'received command' response.
 * However, we do wait until the action is completed
 * This might not be the most optimal way of doing this, so change when you see a better way
 */
int IntensiLightShutter::SetND(int nd)                              
{
   // sanity check, this error should not occur if the rest of the code is correct
   if (nd < 0 || nd > 6)
      return ERR_ND_OUTOFBOUNDS;

   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str());

   std::ostringstream command;                                                    
   command << "cNDM" << nd;

   int ret = SendSerialCommand(port_.c_str(), command.str().c_str(), "\r");   
   if (ret != DEVICE_OK)
      return ret;
                                                                             
   // block/wait for acknowledge                     
   std::string answer;                                                          
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer); 
   if (ret != DEVICE_OK) {
      LogMessage("No answer from IntensiLight");
      return ret;
   }
   
   if (answer[0]=='n') {
      int errNo = atoi(answer.substr(4).c_str());
      return ERR_INTENSILIGHTSHUTTER_OFFSET + errNo;
   }

   if (answer.substr(1,3) =="NDM") {
      return DEVICE_OK;
   }

   return DEVICE_SERIAL_INVALID_RESPONSE;
}

/**
 *
 */
int IntensiLightShutter::GetND(int& nd)                              
{                                                                            
   // empty the Rx serial buffer before sending command
   ClearPort(*this, *GetCoreCallback(), port_.c_str());
   std::string command = "rNAR";;

   int ret = SendSerialCommand(port_.c_str(), command.c_str(), "\r");   
   if (ret != DEVICE_OK)
      return ret;
                                                                             
   // block/wait for acknowledge                     
   std::string answer;                                                          
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer); 
   if (ret != DEVICE_OK) {
      LogMessage("No answer from IntensiLight");
      return ret;
   }
   
   if (answer[0]=='n') {
      int errNo = atoi(answer.substr(4).c_str());
      return ERR_INTENSILIGHTSHUTTER_OFFSET + errNo;
   }

   if (answer.substr(1,3) =="NAR") {
      nd = atoi(answer.substr(4).c_str());
      return DEVICE_OK;
   }

   return DEVICE_SERIAL_INVALID_RESPONSE;
}

int IntensiLightShutter::GetVersion()
{
   std::string command = "rVEN";
   int ret = SendSerialCommand(port_.c_str(), command.c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;
                                                                             
   // block/wait for acknowledge                     
   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0]== 'a') 
   {
      version_ = answer.substr(4,5);
      return DEVICE_OK;
   }
   else if (answer[0] =='n') 
   {
      int errNo = atoi(answer.substr(4).c_str());
      return ERR_INTENSILIGHTSHUTTER_OFFSET + errNo;
   }
   // if this fails, repeat once to deal with slow starting serial ports
   else {
      ret = SendSerialCommand(port_.c_str(), command.c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;
                                                                                
      ret = GetSerialAnswer(port_.c_str(), "\r\n", answer);
      if (ret != DEVICE_OK)
         return ret;

      if (answer[0]== 'a') 
      {
         version_ = answer.substr(4,5);
         return DEVICE_OK;
      }
      else if (answer[0] =='n') 
      {
         int errNo = atoi(answer.substr(4).c_str());
         return ERR_INTENSILIGHTSHUTTER_OFFSET + errNo;
      }
   }

   return DEVICE_SERIAL_INVALID_RESPONSE;
}


int IntensiLightShutter::Shutdown()                                                
{                                                                            
   if (initialized_)                                                         
   {                                                                         
      initialized_ = false;                                                  
   }                                                                         
   return DEVICE_OK;                                                         
}                                                                            

// Never busy because all commands block
bool IntensiLightShutter::Busy()
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
int IntensiLightShutter::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int IntensiLightShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      bool state;
      int ret = GetShutterPosition(state);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set(state ? 1l : 0l);                                                          
   }                                                                         
   else if (eAct == MM::AfterSet) {
      long pos;
      pProp->Get(pos);

      return SetShutterPosition(pos == 0 ? false : true);
   }
   return DEVICE_OK;
}

 
int IntensiLightShutter::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet && version_ == "Undefined") {
      int ret = GetVersion();
      if (ret != DEVICE_OK) 
         return ret;
      pProp->Set(version_.c_str());
   }
   return DEVICE_OK;
}

int IntensiLightShutter::OnND(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      int nd;
      int ret = GetND(nd);
      if (ret != DEVICE_OK)
         return ret;
      std::string ndMessage;
      switch (nd) { 
         case 1: ndMessage = g_Nd1; break;
         case 2: ndMessage = g_Nd2; break;
         case 3: ndMessage = g_Nd3; break;
         case 4: ndMessage = g_Nd4; break;
         case 5: ndMessage = g_Nd5; break;
         case 6: ndMessage = g_Nd6; break;
      }
      pProp->Set(ndMessage.c_str());
   } else if (eAct == MM::AfterSet) {
      std::string  ndMessage;
      int nd = 1;
      pProp->Get(ndMessage);
      if (ndMessage == g_Nd2) 
         nd = 2;
      if (ndMessage == g_Nd3) 
         nd = 3;
      if (ndMessage == g_Nd4) 
         nd = 4;
      if (ndMessage == g_Nd5) 
         nd = 5;
      if (ndMessage == g_Nd6) 
         nd = 6;
      return SetND(nd);
   }

   return DEVICE_OK;
}


