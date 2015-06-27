///////////////////////////////////////////////////////////////////////////////
// FILE:          SpectralLMM5.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Spectral LMM5 controller adapter
// COPYRIGHT:     University of California, San Francisco, 2008
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
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
// AUTHOR:        Nico Stuurman, 01/17/2008
//

#include "SpectralLMM5.h"
#include "SpectralLMM5Interface.h"

#ifdef WIN32
   #include <winsock.h>
#else
   #include <netinet/in.h>
#endif
#include <sstream>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_POSITION         10002
#define ERR_INVALID_SPEED            10003
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_SET_POSITION_FAILED      10005
#define ERR_INVALID_STEP_SIZE        10006
#define ERR_LOW_LEVEL_MODE_FAILED    10007
#define ERR_INVALID_MODE             10008

const char* g_DeviceNameLMM5Hub = "LMM5-Hub";
const char* g_DeviceNameLMM5Shutter = "LMM5-Shutter";
const char* g_DeviceNameLMM5Switch = "LMM5-Switch";

SpectralLMM5Interface* g_Interface = NULL;

////////////////////////////////
// Exported MMDevice API
////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceNameLMM5Hub, MM::GenericDevice, "LMM5 Hub");
   RegisterDevice(g_DeviceNameLMM5Shutter, MM::ShutterDevice, "LMM5 Shutter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameLMM5Hub) == 0)
      return new LMM5Hub();
   else if (strcmp(deviceName, g_DeviceNameLMM5Shutter) == 0)
      return new LMM5Shutter();

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice) 
{
   delete pDevice;
}

////////////////////////////////
// LMM5Hub implementation
////////////////////////////////

LMM5Hub::LMM5Hub() :
majorFWV_('0'),
minorFWV_('0'),
triggerOutConfig_ (0),
port_ ("Undefined"),
initialized_ (false),
flicrAvailable_(false),
nrOutputs_(0)
{
   InitializeDefaultErrorMessages();

   // SetErrorText(ERR_UNEXPECTED_ANSWER, "Unexpected answer.  Is the LMM5 controller connected and switched on?");

   CPropertyAction* pAct = new CPropertyAction(this, &LMM5Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

LMM5Hub::~LMM5Hub()
{
   Shutdown();
}

void LMM5Hub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameLMM5Hub);
}

int LMM5Hub::Initialize()
{
   if (g_Interface == NULL)
      return DEVICE_NOT_CONNECTED;

   int ret = g_Interface->DetectLaserLines(*this, *GetCoreCallback());
   nrLines_= g_Interface->GetNrLines();
   if (ret != DEVICE_OK)
      return ret;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameLMM5Hub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Firmware version
   std::string version;
   ret = g_Interface->GetFirmwareVersion(*this, *GetCoreCallback(), version);
   if (ret != DEVICE_OK)
      return ret;
   ret = CreateStringProperty("Firmware Version", version.c_str(), true);
   if (ret != DEVICE_OK)
      return ret;

   // Does this controller support FLICR (i.e PWM)?
   ret = g_Interface->GetFLICRAvailable(*this, *GetCoreCallback(), flicrAvailable_);
   if (ret != DEVICE_OK)
      return ret;
   std::string msg = "This controller does not support FLICR";
   if (flicrAvailable_) 
   {
      msg = "This controller supports FLICR";
   }
   LogMessage(msg.c_str());

  

   // Power monitor
   /*
   CPropertyAction* pAct = new CPropertyAction(this, &LMM5Hub::OnPowerMonitor);
   CreateProperty("PowerMonitor", "0", MM::Float, false, pAct);
   */

   // For each laser line, create transmission properties 
   availableLines* lines = g_Interface->getAvailableLaserLines();
   for (int i=0; i < nrLines_; i++) 
   {
      if (lines[i].present) 
      {
			if (lines[i].waveLength >= 100) 
			{
			   CPropertyActionEx *pEx = new CPropertyActionEx(this, &LMM5Hub::OnTransmission, (long) i);
			   std::ostringstream propName;
			   propName << "Transmission (%) " << lines[i].name; 
			   ret = CreateProperty(propName.str().c_str(), "100.0", MM::Float, false, pEx);
			   if (ret != DEVICE_OK)
				   return ret;
			   SetPropertyLimits(propName.str().c_str(), 0.0, 100.0);
			}
         if (flicrAvailable_) 
         {
            // check if this line has flicr available
            ret = g_Interface->GetFLICRAvailableByLine(*this, *GetCoreCallback(), i, lines[i].flicrAvailable);
            if (ret != DEVICE_OK)
               return ret;
            if (lines[i].flicrAvailable) 
            {
               // check for maximum FLICR value
               ret = g_Interface->GetMaxFLICRValue(*this, *GetCoreCallback(), i, lines[i].maxFLICR);
               if (ret != DEVICE_OK)
                  return ret;
               std::ostringstream os;
               os << "Max FLICR for line " << i << " is: " << lines[i].maxFLICR;
               LogMessage(os.str().c_str());
               // ad FLICR/PWM property
               CPropertyActionEx *pEx = new CPropertyActionEx(this, &LMM5Hub::OnFlicr, (long) i);
               std::ostringstream fPropName;
               fPropName << "PWM (%) " << lines[i].name;
               ret = CreateProperty(fPropName.str().c_str(), "1.0", MM::String, false, pEx);
               if (ret != DEVICE_OK)
                  return ret;
               // populate with presets
               uint16_t val = 1;
               while (val <= lines[i].maxFLICR) 
               {
                  std::string valStr;
                  IntToPerc(val, valStr);
                  AddAllowedValue(fPropName.str().c_str(), valStr.c_str());
                  val = val * 10;
               }
            }
         }
      }
   }
   
   
   // Exposure Configuration
   /*
   pAct = new CPropertyAction(this, &LMM5Hub::OnExposureConfig);
   CreateProperty("ExposureConfig", "", MM::String, false, pAct);
   */

   // Some versions of the firmware, when trigger-out is unavailable, fail to
   // respond correctly when querying the trigger-out config. Only provide the
   // trigger-out properties when it appears to be working.
   unsigned char dummy[5];
   ret = g_Interface->GetTriggerOutConfig(*this, *GetCoreCallback(), dummy);
   if (ret == DEVICE_OK) {
      // Trigger configuration
      CPropertyAction *pAct = new CPropertyAction(this, &LMM5Hub::OnTriggerOutConfig);
      CreateProperty("TriggerOutConfig", "", MM::String, false, pAct);
      std::vector<std::string> triggerConfigs;
      triggerConfigs.push_back("Enable-State");
      triggerConfigMap_["Enable-State"] = 256;
      triggerConfigs.push_back("Enable-Clock");
      triggerConfigMap_["Enable-Clock"] = 257;
      triggerConfigs.push_back("Disable-State");
      triggerConfigMap_["Disable-State"] = 0;
      triggerConfigs.push_back("Disable-Clock");
      triggerConfigMap_["Disable-Clock"] = 1;
      SetAllowedValues("TriggerOutConfig", triggerConfigs);

      // Trigger Exposure Time
      pAct = new CPropertyAction(this, &LMM5Hub::OnTriggerOutExposureTime);
      CreateProperty("TriggerExpTime(0.1ms)", "", MM::Integer, false, pAct);
   }

   // outputs are available only since firmware 1.30.  Our interface will always return 1 for firmware that is older
   ret = g_Interface->GetNumberOfOutputs(*this, *GetCoreCallback(), nrOutputs_);
   if (nrOutputs_ > 1) 
   {
      CPropertyAction *pAct = new CPropertyAction(this, &LMM5Hub::OnOutputSelect);
      CreateProperty("FiberOutput", "0", MM::Integer, false, pAct);
      for (int i = 0; i < nrOutputs_; i++) {
         std::ostringstream os;
         os << i;
         AddAllowedValue("FiberOutput", os.str().c_str());
      }
   }

   ret = UpdateStatus();
   if (DEVICE_OK != ret)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int LMM5Hub::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

bool LMM5Hub::Busy()
{
   return false;
}

int LMM5Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(port_);
      MM::PortType portType = GetSerialPortType(port_.c_str());
      g_Interface = new SpectralLMM5Interface(port_, portType);
   }
   return DEVICE_OK;
}

int LMM5Hub::OnTransmission(MM::PropertyBase* pProp, MM::ActionType pAct, long line)
{
   double transmission;
   if (pAct == MM::BeforeGet)
   {
      int ret = g_Interface->GetTransmission(*this, *GetCoreCallback(), line, transmission);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(transmission);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(transmission);
      int ret = g_Interface->SetTransmission(*this, *GetCoreCallback(), line, transmission);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

int LMM5Hub::OnFlicr(MM::PropertyBase* pProp, MM::ActionType pAct, long line)
{  
   uint16_t flicr;
   if (pAct == MM::BeforeGet)
   {
      int ret = g_Interface->GetFLICRValue(*this, *GetCoreCallback(), line, flicr);
      if (ret != DEVICE_OK)
         return ret;
      // present to the user as a fraction
      std::string val;
      IntToPerc(flicr, val);
      pProp->Set(val.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      std::string val;
      pProp->Get(val);
      uint16_t flicr;
      PercToInt(val, flicr); 
      int ret = g_Interface->SetFLICRValue(*this, *GetCoreCallback(), line, flicr);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;


}

// converts integer to a percentage (i.e. 100 becomes 1%, 1000 becomes 0.1%, etc...
void LMM5Hub::IntToPerc(uint16_t value, std::string& result)
{
   double perc = 100.0 * 1.0 / (double) value;
   std::ostringstream os;
   os << perc << "%";
   result = os.str();
}

void LMM5Hub::PercToInt(std::string in, uint16_t& result) 
{
   std::string n = in.substr(0, n.size() - 1);
   double val;
   std::stringstream convert(n);
   convert >> val;
   val = val / 100.0;
   result = (uint16_t) (1.0 / val);
}


int LMM5Hub::OnExposureConfig(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   std::string exposureConfig;
   if (pAct == MM::BeforeGet)
   {
      int ret = g_Interface->GetExposureConfig(*this, *GetCoreCallback(), exposureConfig);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(exposureConfig.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(exposureConfig);
      int ret = g_Interface->SetExposureConfig(*this, *GetCoreCallback(), exposureConfig);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

int LMM5Hub::OnTriggerOutConfig(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      unsigned char buffer[4];
      int ret = g_Interface->GetTriggerOutConfig(*this, *GetCoreCallback(), buffer);
      if (ret != DEVICE_OK)
         return ret;
      uint16_t config;
      memcpy(&config, buffer, 2);
      config = ntohs(config);
      switch (config) {
         case 256 : pProp->Set("Enable-State"); break;
         case 257 : pProp->Set("Enable-Clock"); break;
         case 0 : pProp->Set("Disable-State"); break;
         case 1 : pProp->Set("Deisable-Clock"); break;
      }
   }
   else if (pAct == MM::AfterSet)
   {
      uint16_t config, time;
      std::string tmp;
      pProp->Get(tmp);
      std::map<std::string, uint16_t>::iterator iter = triggerConfigMap_.find(tmp.c_str());
      if (iter != triggerConfigMap_.end() )
         triggerOutConfig_ = iter->second;
      config = triggerOutConfig_;
      time = triggerOutExposureTime_;
      config = htons(config);
      time = htons(time);
      unsigned char buf[4];
      memcpy(buf, &config, 2);
      memcpy(buf + 2, &time, 2);
      int ret = g_Interface->SetTriggerOutConfig(*this, *GetCoreCallback(), buf);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

int LMM5Hub::OnTriggerOutExposureTime(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {

      unsigned char buffer[4];
      int ret = g_Interface->GetTriggerOutConfig(*this, *GetCoreCallback(), buffer);
      if (ret != DEVICE_OK)
         return ret;

      uint16_t time;
      memcpy(&time, buffer + 2, 2);
      time = ntohs(time);
      std::ostringstream os;
      os << time;
      printf ("ExposureTime: %s %x\n", os.str().c_str(), time);
      pProp->Set(os.str().c_str());
   } else if (pAct == MM::AfterSet)
   {
      uint16_t config, time;
      std::string tmp;
      pProp->Get(tmp);
      time = (uint16_t) atoi(tmp.c_str());
      triggerOutExposureTime_ = time;
      config = triggerOutConfig_;
      config = htons(config);
      time = htons(time);
      unsigned char buf[4];
      memcpy(buf, &config, 2);
      memcpy(buf + 2, &time, 2);
      int ret = g_Interface->SetTriggerOutConfig(*this, *GetCoreCallback(), buf);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

int LMM5Hub::OnOutputSelect(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   uint16_t output;
   if (pAct == MM::BeforeGet)
   {
      int ret = g_Interface->GetOutput(*this, *GetCoreCallback(), output);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long) output);
   }
   else if (pAct == MM::AfterSet)
   {
      long output;
      pProp->Get(output);
      int ret = g_Interface->SetOutput(*this, *GetCoreCallback(), (uint16_t) output);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

/*
int LMM5Hub::OnPowerMonitor(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   return DEVICE_OK;
}
*/

/////////////////// LMM5 Shutter /////////////////

LMM5Shutter::LMM5Shutter() :
   changedTime_(0.0),
   state_(0),
   open_ (false)
{
   InitializeDefaultErrorMessages();

   EnableDelay();
}

LMM5Shutter::~LMM5Shutter()
{
}

int LMM5Shutter::Initialize()
{
   if (g_Interface == NULL)
      return DEVICE_NOT_CONNECTED;

   // Name                                                                   
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);      
                                                                             
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Spectral LMM5 Shutter", MM::String, true);

   int ret = g_Interface->DetectLaserLines(*this, *GetCoreCallback()); 
   nrLines_= g_Interface->GetNrLines();
   if (ret != DEVICE_OK)
      return ret;

   availableLines* lines = g_Interface->getAvailableLaserLines();
   unsigned long lineMask = 0;
   for (int i=0; i < nrLines_; i++) 
      if (lines[i].present) 
         lineMask = lineMask | (1 << i);
   
   printf ("LineMask: %lx\n", lineMask);

   // We roll our own implementation of State and Label here (since we are a Shutter device, not a State Device
   // State
   CPropertyAction* pAct = new CPropertyAction(this, &LMM5Shutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   for (unsigned long i=0; i<=lineMask; i++)
   {
      if ((i & lineMask) == i)
      {
         std::stringstream tmp;
         tmp << i;
         AddAllowedValue(MM::g_Keyword_State, tmp.str().c_str());
      }
   }

   // Label
   pAct = new CPropertyAction(this, &LMM5Shutter::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   for (unsigned long i=0; i<=lineMask; i++)
   {
      if ((i & lineMask) == i)
      {
         std::string label = StateToLabel(i);;
         AddAllowedValue(MM::g_Keyword_Label, label.c_str());
      }
   }

   for (long i=0; i < nrLines_; i++) 
   {
      if (lines[i].present) 
      {
         CPropertyActionEx *pActEx = new CPropertyActionEx(this, &LMM5Shutter::OnStateEx, i);
         CreateProperty(lines[i].name.c_str(), "0", MM::Integer, false, pActEx);
         SetPropertyLimits(lines[i].name.c_str(), 0, 1);
      }
   }

   changedTime_ = GetCurrentMMTime();
 
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int LMM5Shutter::Shutdown()
{
   return DEVICE_OK;
}
  
void LMM5Shutter::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, name_.c_str());
}

bool LMM5Shutter::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   if (interval < (1000.0 * GetDelayMs() ))
      return true;
   return false;
}

int LMM5Shutter::SetOpen(bool open)
{
   int state = 0;
   if (open)
      state = state_;
   changedTime_ = GetCurrentMMTime();
   int ret = g_Interface->SetShutterState(*this, *GetCoreCallback(), state);
   if (ret == DEVICE_OK)
      open_ = open;
   return ret;
}

int LMM5Shutter::GetOpen(bool& open)
{
   int state;
   int ret = g_Interface->GetShutterState(*this, *GetCoreCallback(), state);
   if (ret != DEVICE_OK)
      return ret;

   // Only return true when all shutters corresponding to our current state are open
   //if ( (state > 0) && (state_ > 0) && (state_ & state) == state_)
   if (state > 0 && ((state_ & state) == state_)) 
      open = true;
   else
      open = false;
   //open_ = open;
  

   return DEVICE_OK;
}

int LMM5Shutter::Fire(double deltaT)
{
   // We need to referense deltaT to avoid a compiler warning:
   deltaT = 0.0;
   return DEVICE_UNSUPPORTED_COMMAND;
}

/////////////// LMM5 Shutter Utility functions ////////////
std::string LMM5Shutter::StateToLabel(int state)
{
   std::string label;
   availableLines* lines = g_Interface->getAvailableLaserLines();
   for (int j=0; j<nrLines_; j++)
   {
      if (state & (1 << j)) 
         label += lines[j].name + "_";
   }
   // printf ("StateToLabel: state %d label %s\n", state, label.c_str());
   return label.substr(0,label.length()-1);
}

int LMM5Shutter::LabelToState(std::string label)
{
   // printf ("LabelToState!!!!!!!!!!!!!!!!!!!!!!!!: label %s\n", label.c_str());
   int state = 0;
   availableLines* lines = g_Interface->getAvailableLaserLines();
   // tokenize the label string on "/"
   std::string::size_type lastPos = label.find_first_not_of("_", 0);
   std::string::size_type pos     = label.find_first_of("_", lastPos);
   while (std::string::npos != pos || std::string::npos != lastPos)
   {
      std::string wave = label.substr(lastPos, pos - lastPos);
      // find this wavelength in available wavelengths and set corresponding state bit high
      printf ("Wave: %s.\n", wave.c_str());
      for(int j=0; j<nrLines_; j++)
      {
         if (wave == lines[j].name)
            state |= (1 << j);
      }
      lastPos = label.find_first_not_of("_", pos);
      pos = label.find_first_of("_", lastPos);
   }
  
   return state;
}
    
int LMM5Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set((long) state_);
   }
   else if (pAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);
      state_ = (int) state;
      SetOpen(open_);
   }

   return DEVICE_OK;
}

int LMM5Shutter::OnLabel(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(StateToLabel(state_).c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      std::string label;
      pProp->Get(label);
      state_ = LabelToState(label);
      SetOpen(open_);
   }

   return DEVICE_OK;
}

int LMM5Shutter::OnStateEx(MM::PropertyBase* pProp, MM::ActionType pAct, long line)
{
   if (pAct == MM::BeforeGet)
   {
      long stateEx = (state_ >> line) & 1;
      pProp->Set(stateEx);
   }
   else if (pAct == MM::AfterSet)
   {
      long stateEx;
      pProp->Get(stateEx);
      if (stateEx == 1) 
      {
         stateEx <<= line;
         state_ |= stateEx;
      } else 
      {
         int invState = ~state_;
         int mask = 1 << line;
         invState |= mask;
         state_ = ~invState;
      }
      SetOpen(open_);
   }

   return DEVICE_OK;
}
