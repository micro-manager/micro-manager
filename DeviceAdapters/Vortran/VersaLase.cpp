/*&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
// FILE:          VersaLase.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Controls Vortran VersaLase
// COPYRIGHT:     Applied Scientific Instrumentation, Eugene OR
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 05/2016
//                Complete re-write of the original VersaLase.cpp by David Sweeney
//                    with heavy influence from Jon's ASITiger device adapter
//                Summary of major changes from original:
//                   - more judicious in refreshing values from hardware than original
//                       -- still re-query often these read-only properties: interlock status and base plate temp,
//                               actual laser power for any laser that is on but not in digital mode
//                   - complete re-write of code structure, e.g. common methods for all 4 lasers,
//                         factored out repetative serial code, etc.
//                   - turn off laser emission during init per MM convention (also turn off when unloading, this was already done)
//                   - warn user of invalid operations (e.g. changing laser power when emission
//                         is off, changing normal power when digital modulation enabled, etc)
//                   - add DetectDevice() to allow automated detection of port settings
//                   - added property for user to type own commands (hidden until "SerialControlEnable" property is enabled)
//                   - removed redundant "LASER_*_DigitalPeakPower" read-only property (have separate read/write property already)
//                   - expose delay setting as property "LASER_*_LaserEmissionDelay"
//                   - added read-only max power property "LASER_*_PowerMaximum"; use this to bound power-setting properties
//                   - added property "RefreshAllProperties" which will force-refresh everything from hardware
//                   - removed lots of explicit delays and instead increase serial timeout when hardware may take a long time to respond
//                   - should work equally well when echo and propmt are enabled or disabled (original assumed echo was on)
// LICENSE:       This file is distributed under the LGPL license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
*/

#include "VersaLase.h"
#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf
#endif

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
#include <sstream>
#include <vector>

using namespace std;



//Required Micro-Manager API Functions&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceName, MM::ShutterDevice, g_DeviceDescription);
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceName) == 0)
   {
      return new VersaLase();
   }
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
// VersaLase Object Initialization
VersaLase::VersaLase() :
      port_("Undefined"),
      portTimeoutMs_(500),
      initialized_(false),
      open_(false),
      interlockOK_(false),
      refreshProps_(false),
      serialCommand_(""),
      serialResponse_(""),
      manualSerialCommand_(""),
      manualSerialResponse_("")
{

   // init state variables
   for (size_t i=0; i<MAX_LASERS; i++)
   {
      laserPresent_[i] = false;
      shutter_[i] = false;
      emissionOn_[i] = false;
      faultCode_[i] = 0;
      powerMax_[i] = 0.0;
      digitalMod_[i] = false;
      analogMod_[i] = false;
      laserPower_[i] = 0.0;
      digitalPower_[i] = 0.0;
   }

   InitializeDefaultErrorMessages();

   // Init any device-specific error messages
   SetErrorText(ERR_PORT_CHANGE, "Cannot update COM port after intialization.");
   SetErrorText(ERR_MISSING_DELIMITER, "Didn't find expected delimiter in serial reply.");
   SetErrorText(ERR_TOO_MANY_DELIMITERS, "Found more delimiters that expecting in serial reply.");
   SetErrorText(ERR_NON_ZERO_FAULT, "Cannot change setting unless laser emission is on, interlock is closed, and no other fault condition exists.");
   SetErrorText(ERR_CANNOT_CHANGE, "Cannot change setting right now.");

   // Name
   CreateProperty(MM::g_Keyword_Name, g_DeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, g_KeywordDescription, MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &VersaLase::OnPort);
   CreateProperty(MM::g_Keyword_Port, "No Port", MM::String, false, pAct, true);
}
//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&

VersaLase::~VersaLase()
{
   Shutdown();
}

void VersaLase::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_DeviceName);
}

int VersaLase::Initialize()
{
   CPropertyAction* pAct;
   CPropertyActionEx* pActEx;
   string tmp_str;
   double tmp_float;


   // "gui1" serial command used by David Sweeney's original code but it's not in documentation
   RETURN_ON_MM_ERROR( SerialQuery("GUI1") ); //?GUI1=1*1*1*1*FV*PV => 7 tokens
   vector<string> gui1Tokens = SplitAnswerOnDelim("=*");
   if ( 7 == gui1Tokens.size())
   {
      for (size_t laserNum=0; laserNum<MAX_LASERS; laserNum++)
      {
         laserPresent_[laserNum] = Int2Bool(atol(gui1Tokens.at(laserNum+1).c_str()));
      }
   }

   // get the serial timeout value, usually 500ms (need to extend timeout in a few cases and then restore)
   // code uses our scratchpad variable
   GetCoreCallback()->GetDeviceProperty(port_.c_str(), "AnswerTimeout", propName_);
   portTimeoutMs_ = atol(propName_);

   // create per-laser properties
   for (size_t laserNum=0; laserNum<MAX_LASERS; laserNum++)
   {
      if (!laserPresent_[laserNum])
         continue;

      // laser info string, read-only and doesn't change
      RETURN_ON_MM_ERROR( SerialQuery(laserNum, "LI") );
      RETURN_ON_MM_ERROR( GetStringAfterEquals(tmp_str) );
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_LaserID_PN, laserNum) );
      CreateProperty(propName_, tmp_str.c_str(), MM::String, true);

      // laser max power, read-only and doesn't change (need both numeric and string versions)
      RETURN_ON_MM_ERROR( SerialQuery(laserNum, "MAXP") );
      RETURN_ON_MM_ERROR( GetFloatAfterEquals(tmp_float) );
      powerMax_[laserNum] = tmp_float;
      RETURN_ON_MM_ERROR( GetStringAfterEquals(tmp_str) );
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_PowerMax_PN, laserNum) );
      CreateProperty(propName_, tmp_str.c_str(), MM::Float, true);

      // laser hours, read-only (don't refresh during operation)
      RETURN_ON_MM_ERROR( SerialQuery(laserNum, "LH") );
      RETURN_ON_MM_ERROR( GetStringAfterEquals(tmp_str) );
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_Hours_PN, laserNum) );
      CreateProperty(propName_, tmp_str.c_str(), MM::String, true);

      // measured power at this moment, read-only (but sometimes force-refresh)
      pActEx = new CPropertyActionEx (this, &VersaLase::OnLaserPowerActual, (long)laserNum);
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_LaserPowerActual_PN, laserNum) );
      CreateProperty(propName_, "0.0", MM::Float, true, pActEx);

      // measured laser current read-only
      // firmware allows current control mode, when in this mode current can be user-set
      // but original device adapter didn't expose current control mode so we don't either
      // and without that mode the value of the current cannot be set
      pActEx = new CPropertyActionEx (this, &VersaLase::OnLaserCurrent, (long)laserNum);
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_LaserCurrent_PN, laserNum) );
      CreateProperty(propName_, "0.0", MM::Float, true, pActEx);

      // fault code, read-only
      pActEx = new CPropertyActionEx (this, &VersaLase::OnFaultCode, (long)laserNum);
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_FaultCode_PN, laserNum) );
      CreateProperty(propName_, "0", MM::Integer, true, pActEx);

      // fault description, read-only
      pActEx = new CPropertyActionEx (this, &VersaLase::OnFaultDescription, (long)laserNum);
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_FaultDesc_PN, laserNum) );
      CreateProperty(propName_, "No Fault", MM::String, true, pActEx);

      // shutter on/off, this controls emission via API's SetOpen()
      pActEx = new CPropertyActionEx (this, &VersaLase::OnShutter, (long)laserNum);
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_Shutter_PN, laserNum) );
      CreateProperty(propName_, g_OFF, MM::String, false, pActEx);
      AddAllowedValue(propName_, g_OFF);
      AddAllowedValue(propName_, g_ON);

      // emission on/off
      pActEx = new CPropertyActionEx (this, &VersaLase::OnEmission, (long)laserNum);
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_Emission_PN, laserNum) );
      CreateProperty(propName_, g_OFF, MM::String, false, pActEx);
      AddAllowedValue(propName_, g_OFF);
      AddAllowedValue(propName_, g_ON);

      // "normal" (non-digital) power setting
      pActEx = new CPropertyActionEx (this, &VersaLase::OnLaserPower, (long)laserNum);
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_LaserPower_PN, laserNum) );
      CreateProperty(propName_, "1.0", MM::Float, false, pActEx);
      SetPropertyLimits(propName_, 0.0, powerMax_[laserNum]);  // laser won't accept setting of 0.00 but it will read value of 0.0 so have to include that in range

      // digital modulation on/off
      pActEx = new CPropertyActionEx (this, &VersaLase::OnDigitalMod, (long)laserNum);
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_DigitalMod_PN, laserNum) );
      CreateProperty(propName_, g_OFF, MM::String, false, pActEx);
      AddAllowedValue(propName_, g_OFF);
      AddAllowedValue(propName_, g_ON);

      // pulse/digital power setting
      // documentation suggests this can be float, but practice suggests it's always truncated to integer
      // go ahead and store as integer so GUI isn't confusing to user
      pActEx = new CPropertyActionEx (this, &VersaLase::OnDigitalPower, (long)laserNum);
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_DigPeakPower_PN, laserNum) );
      CreateProperty(propName_, "1", MM::Integer, false, pActEx);
      SetPropertyLimits(propName_, 0, (long)powerMax_[laserNum]);

      // emission delay on/off
      pActEx = new CPropertyActionEx (this, &VersaLase::OnEmissionDelay, (long)laserNum);
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_EmissionDelay_PN, laserNum) );
      CreateProperty(propName_, g_OFF, MM::String, false, pActEx);
      AddAllowedValue(propName_, g_OFF);
      AddAllowedValue(propName_, g_ON);

      // analog modulation on/off
      pActEx = new CPropertyActionEx (this, &VersaLase::OnAnalogMod, (long)laserNum);
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_AnalogMod_PN, laserNum) );
      CreateProperty(propName_, g_OFF, MM::String, false, pActEx);
      AddAllowedValue(propName_, g_OFF);
      AddAllowedValue(propName_, g_ON);

   }

   // create global properties

   // base plate temp, read only but refreshed on request
   pAct = new CPropertyAction (this, &VersaLase::OnBaseT);
   CreateProperty(g_BaseTemp_PN, "0.0", MM::Float, true, pAct);

   // interlock status, read only but refreshed on request
   pAct = new CPropertyAction (this, &VersaLase::OnInterlock);
   CreateProperty(g_Interlock_PN, g_Interlock_Open, MM::String, true, pAct);
   AddAllowedValue(g_Interlock_PN, g_Interlock_Open);
   AddAllowedValue(g_Interlock_PN, g_Interlock_OK);

   // firmware version, read-only and doesn't change
   RETURN_ON_MM_ERROR( SerialQuery("SFV") );
   RETURN_ON_MM_ERROR( GetStringAfterEquals(tmp_str) );
   CreateProperty(g_FirmwareVersion_PN, tmp_str.c_str(), MM::String, true);

   // property to allow user to refresh all values from hardware
   pAct = new CPropertyAction (this, &VersaLase::OnForceRefresh);
   CreateProperty(g_RefreshAll_PN, g_IDLE, MM::String, false, pAct);
   AddAllowedValue(g_RefreshAll_PN, g_IDLE);
   AddAllowedValue(g_RefreshAll_PN, g_GO);

   // update all properties of this device (i.e. call BeforeGet branch of all properties we've created)
   RETURN_ON_MM_ERROR( UpdateStatus() );

   // property to allow user to refresh all values from hardware
   pAct = new CPropertyAction (this, &VersaLase::OnSerialEnable);
   CreateProperty(g_SerialEnable_PN, g_OFF, MM::String, false, pAct);
   AddAllowedValue(g_SerialEnable_PN, g_OFF);
   AddAllowedValue(g_SerialEnable_PN, g_ON);

   // it's good practice to turn off all light emission on initialization per Nico
   for (size_t laserNum=0; laserNum<MAX_LASERS; laserNum++)
   {
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_Emission_PN, laserNum) );
      RETURN_ON_MM_ERROR( SetProperty(propName_, g_OFF) );
   }

   initialized_ = true;
   return DEVICE_OK;
}

MM::DeviceDetectionStatus VersaLase::DetectDevice()
{
   if (initialized_)
      return MM::CanCommunicate;

   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
   char answerTO[MM::MaxStrLength];

   try
   {
      std::string portLowerCase = port_;
      for( std::string::iterator its = portLowerCase.begin(); its != portLowerCase.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      if( 0< portLowerCase.length() &&  0 != portLowerCase.compare("undefined")  && 0 != portLowerCase.compare("unknown") )
      {
         result = MM::CanNotCommunicate;

         // record the default answer time out
         GetCoreCallback()->GetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);

         // device specific default communication parameters for ASI Tiger controller
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Off");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "19200" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "500.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "DelayBetweenCharsMs", "0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();
         PurgeComPort(port_.c_str());
         int ret = SerialQuery("GUI1");  // look for valid reply
         if( DEVICE_OK != ret || serialResponse_.length() < 7 || serialResponse_.substr(0, 6).compare("?GUI1=") != 0)
         {
            LogMessageCode(ret,true);
         }
         else
         {
            // to succeed must reach here....
            result = MM::CanCommunicate;
         }
         pS->Shutdown();
         // restore the AnswerTimeout to the default
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);
      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!");
   }

   return result;
}


int VersaLase::Shutdown()
{
   if (initialized_)
   {
      // like SetOpen(false) except we pay no attention to shutter_
      for (size_t laserNum=0; laserNum<MAX_LASERS; laserNum++)
      {
         if (!laserPresent_[laserNum])
            continue;
         RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_Emission_PN, laserNum) );
         SetProperty(propName_, g_OFF);
      }
   }
   return DEVICE_OK;
}

bool VersaLase::Busy()
{
   return false;
}

int VersaLase::SetOpen(bool open)
{
   for (size_t laserNum=0; laserNum<MAX_LASERS; laserNum++)
   {
      if (!laserPresent_[laserNum])
         continue;
      RETURN_ON_MM_ERROR( GetPropertyNameWithLetter(g_Emission_PN, laserNum) );
      if (open)
         // laser on/off controlled by shutter property if "open"
         SetProperty(propName_, (shutter_[laserNum] ? g_ON : g_OFF));
      else
         // laser always off if "closed"
         SetProperty(propName_, g_OFF);
   }
   open_ = open;
	return DEVICE_OK;
}

int VersaLase::GetOpen(bool& open)
{
   open = open_;
   return DEVICE_OK;
}

int VersaLase::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}


// Action Handlers &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&

// only controls internal variables, no serial communication
int VersaLase::OnShutter(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   if (eAct == MM::BeforeGet) {
      if (!pProp->Set(shutter_[laserNum] ? g_ON : g_OFF))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      shutter_[laserNum] = (0 == tmpstr.compare(g_ON));
   }
   return DEVICE_OK;
}

int VersaLase::OnEmission(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   bool tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR( SerialQuery(laserNum, "LE"));
      RETURN_ON_MM_ERROR( GetBoolAfterEquals(tmp) );
      emissionOn_[laserNum] = tmp;
      if (!pProp->Set(tmp ? g_ON : g_OFF))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      bool on = (0 == tmpstr.compare(g_ON));
      // don't need to do anything if we are "changing" to same state
      // especially critical here because we do this with MM shutter open/close
      if (on == emissionOn_[laserNum])
         return DEVICE_OK;
      RETURN_ON_MM_ERROR( SerialSet(laserNum, "LE", (on ? 1 : 0), 3000) );  // wait up to 3 sec for setting to take effect
      // refresh value based on reply
      RETURN_ON_MM_ERROR( GetBoolAfterEquals(tmp) );
      emissionOn_[laserNum] = tmp;
      if (!pProp->Set(tmp ? g_ON : g_OFF))
         return DEVICE_INVALID_PROPERTY_VALUE;
      // TODO should wait 5 seconds to update these if delay == 1 (but would rather not "hang" for 5 seconds either)
      RETURN_ON_MM_ERROR( ForcePropertyUpdate(g_FaultCode_PN, laserNum) ); // refresh fault code
      RETURN_ON_MM_ERROR( ForcePropertyUpdate(g_LaserPowerActual_PN, laserNum) ); // refresh actual laser power
      RETURN_ON_MM_ERROR( ForcePropertyUpdate(g_LaserCurrent_PN, laserNum) ); // refresh actual laser power
   }
   return DEVICE_OK;
}

int VersaLase::OnDigitalMod(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   // TODO determine whether digital and analog modulation should be mutually exclusive and implement this if so
   bool tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR( SerialQuery(laserNum, "PUL"));
      RETURN_ON_MM_ERROR( GetBoolAfterEquals(tmp) );
      digitalMod_[laserNum] = tmp;
      if (!pProp->Set(tmp ? g_ON : g_OFF))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      if (!faultCode_[laserNum])  // only take action if possible to change
      {
         string tmpstr;
         pProp->Get(tmpstr);
         bool on = (0 == tmpstr.compare(g_ON));
         RETURN_ON_MM_ERROR( SerialSet(laserNum, "PUL", (on ? 1 : 0), 3000) );   // wait up to 3 sec for setting to take effect
         // refresh value based on reply
         RETURN_ON_MM_ERROR( GetBoolAfterEquals(tmp) );
         digitalMod_[laserNum] = tmp;
         if (!pProp->Set(tmp ? g_ON : g_OFF))
            return DEVICE_INVALID_PROPERTY_VALUE;
         RETURN_ON_MM_ERROR( ForcePropertyUpdate(g_LaserPowerActual_PN, laserNum) ); // refresh actual laser power
      }
      else
      {
         if (!pProp->Set(digitalMod_[laserNum] ? g_ON : g_OFF))
            return DEVICE_INVALID_PROPERTY_VALUE;
         return ERR_NON_ZERO_FAULT;
      }
   }
   return DEVICE_OK;
}

int VersaLase::OnAnalogMod(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   // TODO determine whether digital and analog modulation should be mutually exclusive and implement this if so
   bool tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR( SerialQuery(laserNum, "EPC"));
      RETURN_ON_MM_ERROR( GetBoolAfterEquals(tmp) );
      analogMod_[laserNum] = tmp;
      if (!pProp->Set(tmp ? g_ON : g_OFF))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      // according to documentation this can only change when fault code is 0, but in testing that condition isn't enforced by firmware
      // here enforce the condition stated in the documentation
      if (!faultCode_[laserNum])  // only take action if possible to change
      {
         string tmpstr;
         pProp->Get(tmpstr);
         bool on = (0 == tmpstr.compare(g_ON));
         RETURN_ON_MM_ERROR( SerialSet(laserNum, "EPC", (on ? 1 : 0), 3000) );  // wait up to 3 sec for setting to take effect
         // refresh value based on reply
         RETURN_ON_MM_ERROR( GetBoolAfterEquals(tmp) );
         analogMod_[laserNum] = tmp;
         if (!pProp->Set(tmp ? g_ON : g_OFF))
            return DEVICE_INVALID_PROPERTY_VALUE;
         RETURN_ON_MM_ERROR( ForcePropertyUpdate(g_LaserPowerActual_PN, laserNum) ); // refresh actual laser power
      }
      else
      {
         if (!pProp->Set(analogMod_[laserNum] ? g_ON : g_OFF))
            return DEVICE_INVALID_PROPERTY_VALUE;
         return ERR_NON_ZERO_FAULT;
      }
   }
   return DEVICE_OK;
}

int VersaLase::OnEmissionDelay(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   bool tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR( SerialQuery(laserNum, "DELAY"));
      RETURN_ON_MM_ERROR( GetBoolAfterEquals(tmp) );
      emissionDelay_[laserNum] = tmp;
      if (!pProp->Set(tmp ? g_ON : g_OFF))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      // according to documentation this can only change when fault code is 0, but in testing that condition isn't enforced by firmware
      // here enforce the condition stated in the documentation
      if (!faultCode_[laserNum])  // only take action if possible to change
      {
         string tmpstr;
         pProp->Get(tmpstr);
         bool on = (0 == tmpstr.compare(g_ON));
         if (on == emissionDelay_[laserNum])  // don't need to do anything if we are "changing" to same state
            return DEVICE_OK;
         RETURN_ON_MM_ERROR( SerialSet(laserNum, "DELAY", (on ? 1 : 0)) );
         // refresh value based on reply
         RETURN_ON_MM_ERROR( GetBoolAfterEquals(tmp) );
         emissionDelay_[laserNum] = tmp;
         if (!pProp->Set(tmp ? g_ON : g_OFF))
            return DEVICE_INVALID_PROPERTY_VALUE;
      }
      else
      {
         if (!pProp->Set(emissionDelay_[laserNum] ? g_ON : g_OFF))
            return DEVICE_INVALID_PROPERTY_VALUE;
         return ERR_NON_ZERO_FAULT;
      }
   }
   return DEVICE_OK;
}

int VersaLase::OnFaultCode(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   long tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR( SerialQuery(laserNum, "FC"));
      RETURN_ON_MM_ERROR( GetIntAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
      if (faultCode_[laserNum] != tmp)  // if fault code changed from last check then update description too
      {
         RETURN_ON_MM_ERROR( ForcePropertyUpdate(g_FaultDesc_PN, laserNum) );
      }
      faultCode_[laserNum] = tmp;
   } else if (eAct == MM::AfterSet) {
      // read-only
   }
   return DEVICE_OK;
}

int VersaLase::OnFaultDescription(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   string tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR( SerialQuery(laserNum, "FD"));
      RETURN_ON_MM_ERROR( GetStringAfterEquals(tmp) );
      if (!pProp->Set(tmp.c_str()))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      // read-only
   }
   return DEVICE_OK;
}

int VersaLase::OnLaserPowerActual(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   // use LP for setting and LPS for querying set point (querying LP is actual power)
   double tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_ && !(emissionOn_[laserNum] && !digitalMod_[laserNum]))  // if non-digital emission is on then refresh
         return DEVICE_OK;
      RETURN_ON_MM_ERROR( SerialQuery(laserNum, "LP"));
      RETURN_ON_MM_ERROR( GetFloatAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      // read-only
   }
   return DEVICE_OK;
}

int VersaLase::OnLaserCurrent(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   double tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR( SerialQuery(laserNum, "LC"));
      RETURN_ON_MM_ERROR( GetFloatAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      // read-only
   }
   return DEVICE_OK;
}

int VersaLase::OnLaserPower(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   // use LP for setting and LPS for querying set point (querying LP is actual power)
   double tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR( SerialQuery(laserNum, "LPS"));
      RETURN_ON_MM_ERROR( GetFloatAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
      laserPower_[laserNum] = tmp;
   } else if (eAct == MM::AfterSet) {
      if (!faultCode_[laserNum] && !digitalMod_[laserNum])  // only take action if possible to change, note special condition that digital modulation be disabled
      {
         pProp->Get(tmp);
         RETURN_ON_MM_ERROR( SerialSet(laserNum, "LP", tmp, 3000) );  // wait up to 3 sec for setting to take effect
         // have to refresh using different command so call BeforeGet branch
         RETURN_ON_MM_ERROR( ForcePropertyUpdate(pProp->GetName()) );
         // update laser statuses too
         RETURN_ON_MM_ERROR( ForcePropertyUpdate(g_FaultCode_PN, laserNum) ); // refresh fault code
         RETURN_ON_MM_ERROR( ForcePropertyUpdate(g_LaserPowerActual_PN, laserNum) ); // refresh actual laser power
         RETURN_ON_MM_ERROR( ForcePropertyUpdate(g_LaserCurrent_PN, laserNum) ); // refresh actual laser power
      }
      else
      {
         if (!pProp->Set(laserPower_[laserNum]))
            return DEVICE_INVALID_PROPERTY_VALUE;
         if (faultCode_[laserNum])
            return ERR_NON_ZERO_FAULT;
         if (digitalMod_[laserNum])
            return ERR_CANNOT_CHANGE;
      }
   }
   return DEVICE_OK;
}

int VersaLase::OnDigitalPower(MM::PropertyBase* pProp, MM::ActionType eAct, long laserNum)
{
   double tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR( SerialQuery(laserNum, "PP"));
      RETURN_ON_MM_ERROR( GetFloatAfterEquals(tmp) );
      digitalPower_[laserNum] = tmp;
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      if (!faultCode_[laserNum])  // only take action if possible to change
      {
         pProp->Get(tmp);
         RETURN_ON_MM_ERROR( SerialSet(laserNum, "PP", tmp, 3000) );  // wait up to 3 sec for setting to take effect
         // refresh value based on reply
         RETURN_ON_MM_ERROR( GetFloatAfterEquals(tmp) );
         if (!pProp->Set(tmp))
            return DEVICE_INVALID_PROPERTY_VALUE;
      }
      else
      {
         if (!pProp->Set(digitalPower_[laserNum]))
            return DEVICE_INVALID_PROPERTY_VALUE;
         return ERR_NON_ZERO_FAULT;
      }
   }
   return DEVICE_OK;
}

int VersaLase::OnBaseT(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double tmp;
   if (eAct == MM::BeforeGet) {
      // always refresh this
      RETURN_ON_MM_ERROR( SerialQuery("BPT"));
      RETURN_ON_MM_ERROR( GetFloatAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      // read-only
   }
   return DEVICE_OK;
}

int VersaLase::OnInterlock(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   bool tmp;
   if (eAct == MM::BeforeGet) {
      // always refresh this
      RETURN_ON_MM_ERROR( SerialQuery("IL"));
      RETURN_ON_MM_ERROR( GetBoolAfterEquals(tmp) );
      if (!pProp->Set(tmp ? g_Interlock_OK : g_Interlock_Open))
         return DEVICE_INVALID_PROPERTY_VALUE;
      if (initialized_ && (interlockOK_ != tmp))  // if change in status then update fault codes
      {
         for (size_t laserNum=0; laserNum<MAX_LASERS; laserNum++)
         {
            RETURN_ON_MM_ERROR( ForcePropertyUpdate(g_FaultCode_PN, laserNum) );
         }
      }
      interlockOK_ = tmp;
   } else if (eAct == MM::AfterSet) {
      // read-only
   }
   return DEVICE_OK;
}

int VersaLase::OnForceRefresh(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_IDLE);
   } else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (0 == tmpstr.compare(g_GO))
      {
         bool origRefreshProps = refreshProps_;
         int retValue = DEVICE_OK;
         refreshProps_ = true;
         retValue = UpdateStatus(); // refreshes all properties of device
         refreshProps_ = origRefreshProps;
         return retValue;
      }
      RETURN_ON_MM_ERROR( ForcePropertyUpdate(pProp->GetName()) );
   }
   return DEVICE_OK;
}

int VersaLase::OnSerialEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      // do nothing, init to "OFF" and it will stay there until set to "ON"
   } else if (eAct == MM::AfterSet) {
      static bool triggered = false;
      // only can be set to "ON" once, after that stuck "ON" (until re-loading device)
      if (triggered) {
         pProp->Set(g_ON);
         return DEVICE_OK;
      }
      string tmpstr;
      pProp->Get(tmpstr);
      if (0 == tmpstr.compare(g_ON))
      {
         CPropertyAction* pAct;
         triggered = true;

         // property to allow sending arbitrary serial commands and receiving response
         pAct = new CPropertyAction (this, &VersaLase::OnSerialCommand);
         CreateProperty(g_SerialCommand_PN, "", MM::String, false, pAct);

         // this is only changed programmatically, never by user
         // contains last response to the OnSerialCommand action
         pAct = new CPropertyAction (this, &VersaLase::OnSerialResponse);
         CreateProperty(g_SerialResponse_PN, "", MM::String, true, pAct);
      }
   }
   return DEVICE_OK;
}

int VersaLase::OnSerialCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // do nothing
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      // only send the command if it has been updated
      if (tmpstr.compare(manualSerialCommand_) != 0)
      {
         manualSerialCommand_ = tmpstr;
         SerialTransaction(manualSerialCommand_);
         // TODO add some sort of check if command was successful, update manualSerialAnswer_ accordingly (e.g. leave blank for invalid command like aoeu)
         manualSerialResponse_ = serialResponse_;  // remember this reply even if SendCommand is called elsewhere
      }
   }
   return DEVICE_OK;
}

int VersaLase::OnSerialResponse(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet || eAct == MM::AfterSet)
   {
      // always read
      if (!pProp->Set(manualSerialResponse_.c_str()))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int VersaLase::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE;
      }
      pProp->Get(port_);
   }

   return DEVICE_OK;
}


// Private helper methods &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&

// queries a global parameter
int VersaLase::SerialQuery(string cmd)
{
   ostringstream command;
   command << "?" << cmd;
   return SerialTransaction(command.str());
}

// queries a parameter from a particular laser
int VersaLase::SerialQuery(size_t laser, string cmd)
{
   ostringstream command;
   command << g_NAMES[laser] << ".?" << cmd;
   return SerialTransaction(command.str());
}

// sets a value of a specified laser
int VersaLase::SerialSet(size_t laser, string cmd, double val, long maxTimeMs)
{
   ostringstream command;
   command << g_NAMES[laser] << "." << cmd << "=" << val;
   return SerialTransaction(command.str(), maxTimeMs);
}

// low-level method for sending command and getting response
// cmd is the string to send
// looks for echo response and ignores it if present
// laser's response stored in serialResponse_ for further processing
// code a bit ugly because have to be careful to restore timeout if we change it
//   and then exit early due to some problem (C++ doesn't have a "finally" construct)
int VersaLase::SerialTransaction(string cmd, long maxTimeMs)
{
   // removes previous prompt or anything else from buffer
   RETURN_ON_MM_ERROR ( PurgeComPort(port_.c_str()) );

   // send requested string
   RETURN_ON_MM_ERROR ( SendSerialCommand(port_.c_str(), cmd.c_str(), "\r") );
   serialCommand_ = cmd;

   // increase serial port timeout temporarily if needed
   bool changeTimeout = maxTimeMs > portTimeoutMs_;
   if (changeTimeout)
   {
      SetSerialTimeout(maxTimeMs);
   }

   // get response
   int ret = GetSerialAnswer(port_.c_str(), "\r\n", serialResponse_);
   if (DEVICE_OK != ret )
   {
      if (changeTimeout)
         RestoreSerialTimeout();
      return ret;
   }

   // if command was echoed then ignore it and get next response
   if (serialCommand_.compare(serialResponse_) == 0)
   {
      ret = GetSerialAnswer(port_.c_str(), "\r\n", serialResponse_);
      if (DEVICE_OK != ret )
      {
         if (changeTimeout)
            RestoreSerialTimeout();
         return ret;
      }
   }

   // restore timeout if we changed it
   if (changeTimeout)
   {
      RestoreSerialTimeout();
   }

   return DEVICE_OK;
}

void VersaLase::SetSerialTimeout(long timeout) const
{
   ostringstream oss;
   oss << timeout;
   GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", oss.str().c_str());
}

void VersaLase::RestoreSerialTimeout() const
{
   SetSerialTimeout(portTimeoutMs_);
}

// breaks input string using list of single-char delimeters
vector<string> VersaLase::SplitAnswerOnDelim(string delims) const
{
   vector<string> elems;
   CDeviceUtils::Tokenize(serialResponse_, elems, delims);
   return elems;
}

// for parsing replies, gets everything after "=" in string form
int VersaLase::GetStringAfterEquals(string &str) const
{
   vector<string> elems;
   str = "";
   CDeviceUtils::Tokenize(serialResponse_, elems, "=");
   if (elems.size() == 2)
   {
      str = elems.at(1);
      return DEVICE_OK;
   }
   else if (elems.size() > 2)
   {
      return ERR_TOO_MANY_DELIMITERS;
   }
   else
   {
      return ERR_MISSING_DELIMITER;
   }
}

// for parsing replies, gets everything after "=" in double-precision floating point
int VersaLase::GetFloatAfterEquals(double &val) const
{
   string str;
   RETURN_ON_MM_ERROR( GetStringAfterEquals(str));
   val = atof(str.c_str());
   return DEVICE_OK;
}

// for parsing replies, gets everything after "=" in double-precision integer
int VersaLase::GetIntAfterEquals(long &val) const
{
   string str;
   RETURN_ON_MM_ERROR( GetStringAfterEquals(str));
   val = atol(str.c_str());
   return DEVICE_OK;
}

// for parsing replies, gets everything after "=" in boolean form (use when reply is 0 or 1)
// anything besides 0 is taken to be true
int VersaLase::GetBoolAfterEquals(bool &val) const
{
   string str;
   RETURN_ON_MM_ERROR( GetStringAfterEquals(str));
   val = Int2Bool(atol(str.c_str()));
   return DEVICE_OK;
}

// utility function to cast to boolean while avoiding compiler warnings
// anything other than 0 is considered true
bool VersaLase::Int2Bool(long val) const
{
   return (0 != val);
}

// replaces the generic "*" in the generic property name with the laser letter
// A for index 0, B for index 1, etc.
int VersaLase::GetPropertyNameWithLetter(const char* baseName, size_t num)
{
   CDeviceUtils::CopyLimitedString(propName_, baseName);
   char* pch = strchr(propName_, '*');
   if (NULL != pch)
      *pch = g_NAMES[num];
   return DEVICE_OK;
}

// forces the specified property to be updated from hardware (for global properties)
int VersaLase::ForcePropertyUpdate(const char* name)
{
   bool origRefreshProps = refreshProps_;
   int retValue = DEVICE_OK;
   refreshProps_ = true;
   retValue = UpdateProperty(name);
   refreshProps_ = origRefreshProps;
   return retValue;
}

// forces the specified property to be updated from hardware (for per-laser properties)
// this takes generic property name as input and figures out full name
int VersaLase::ForcePropertyUpdate(const char* name, size_t num)
{
   GetPropertyNameWithLetter(name, num);
   return ForcePropertyUpdate(propName_);
}
