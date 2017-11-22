///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIFW1000.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASIFW1000 controller adapter
// COPYRIGHT:     University of California, San Francisco, 2006
//                All rights reserved
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                   
//
// AUTHOR:        Nico Stuurman (based on code by Nenad Amodaj), nico@cmp.ucsf.edu, April 2007
//                automatic device detection by Karl Hoover
//

#ifdef WIN32
   //#include <windows.h>
   #define snprintf _snprintf 
#endif

#include "ASIFW1000.h"
#include "ASIFW1000Hub.h"
#include <cstdio>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"

using namespace std;

const char* g_ASIFW1000Hub = "ASIFWController";
const char* g_ASIFW1000FilterWheel = "ASIFilterWheel";
const char* g_ASIFW1000FilterWheelNr = "ASIFilterWheelNumber";
const char* g_ASIFW1000Shutter = "ASIShutter";
const char* g_ASIFW1000ShutterNr = "ASIShutterNumber";
const char* g_ASIFW1000ShutterType = "ASIShutterType";

using namespace std;

ASIFW1000Hub g_hub;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ASIFW1000Hub, MM::GenericDevice, "ASIFW1000 Controller");
   RegisterDevice(g_ASIFW1000FilterWheel, MM::StateDevice, "ASI FilterWheel");   
   RegisterDevice(g_ASIFW1000Shutter, MM::ShutterDevice, "ASI Shutter"); 
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ASIFW1000Hub) == 0)
   {
      return new Hub();
   }
   else if (strcmp(deviceName, g_ASIFW1000FilterWheel) == 0 )
   {
      return new FilterWheel();
   }
   else if (strcmp(deviceName, g_ASIFW1000Shutter) == 0 )
   {
      return new Shutter();
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// ASIFW1000 Hub
///////////////////////////////////////////////////////////////////////////////

Hub::Hub() :
   initialized_(false),
   port_("Undefined")
{
   InitializeDefaultErrorMessages();

   // custom error messages
   SetErrorText(ERR_COMMAND_CANNOT_EXECUTE, "Command cannot be executed");
   SetErrorText(ERR_NO_ANSWER, "No answer received.  Is the FW1000 controller connected?  If so, try increasing the AnswerTimeout of the serial port.");
   SetErrorText(ERR_NOT_CONNECTED, "No answer received.  Is the FW1000 controller connected?  If so, try increasing the AnswerTimeout of the serial port.");

   // create pre-initialization properties
   // ------------------------------------

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

Hub::~Hub()
{
   Shutdown();
}

void Hub::GetName(char* name) const
{
   //assert(name_.length() < CDeviceUtils::GetMaxStringLength());   
   CDeviceUtils::CopyLimitedString(name, g_ASIFW1000Hub);
}

bool Hub::Busy()
{
   return false;
}

bool Hub::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus Hub::DetectDevice(void)
{
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

         // device specific default communication parameters
         // for ASI FW
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Off");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "500.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "DelayBetweenCharsMs", "0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());

         std::vector< std::string> possibleBauds;
         possibleBauds.push_back("115200");
         possibleBauds.push_back("28800");         
         possibleBauds.push_back("19200");
         possibleBauds.push_back("9600");


         for( std::vector< std::string>::iterator bit = possibleBauds.begin(); bit!= possibleBauds.end(); ++bit )
         {
            GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, (*bit).c_str() );
            pS->Initialize();
            PurgeComPort(port_.c_str());
            // Version
            char version[256];
            int ret = g_hub.GetVersion(*this, *GetCoreCallback(), version);
            if( DEVICE_OK != ret )
            {
               LogMessageCode(ret,true);
            }
            else
            {
               // to succeed must reach here....
               result = MM::CanCommunicate;
            }
            pS->Shutdown();
            CDeviceUtils::SleepMs(300);
            if( MM::CanCommunicate == result)
               break;
         }
         // always restore the AnswerTimeout to the default
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);

      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }
   return result;
}




int Hub::Initialize()
{
   // Ensure a serial port has been set -- otherwise return an error.
   if (! g_hub.IsConnected())
      return DEVICE_ERR;

   // set property list
   // -----------------
   
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_ASIFW1000Hub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "ASIFW1000 controller", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Version
   char version[256];
   ret = g_hub.GetVersion(*this, *GetCoreCallback(), version);
   if (DEVICE_OK != ret)
      return ret;
   ret = CreateProperty("Firmware version", version, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Set verbose level to 6 to speed stuff up
   ret = g_hub.SetVerboseMode(*this, *GetCoreCallback(), 6);
   if (DEVICE_OK != ret)
      return ret;

   // Enquire about the current wheel, this is mainly to set the variable activeWheel_, private to g_hub.
   int wheelNr;
   ret = g_hub.GetCurrentWheel(*this, *GetCoreCallback(), wheelNr);
   if (DEVICE_OK != ret)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   
   return DEVICE_OK;
}

int Hub::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
         //return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
      g_hub.SetPort(port_.c_str());
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// ASIFW1000 FilterWheel
///////////////////////////////////////////////////////////////////////////////
FilterWheel::FilterWheel () :
   initialized_ (false),
   name_ (g_ASIFW1000FilterWheel),
   pos_ (0),
   wheelNr_ (0),
   numPos_ (6)
{
   InitializeDefaultErrorMessages();

   // Todo: Add custom messages
   //
   // create pre-initialization properties
   // ------------------------------------

   // FilterWheel Nr (0 or 1)
   CPropertyAction* pAct = new CPropertyAction (this, &FilterWheel::OnWheelNr);
   CreateProperty(g_ASIFW1000FilterWheelNr, "0", MM::Integer, false, pAct, true);

   AddAllowedValue(g_ASIFW1000FilterWheelNr, "0"); 
   AddAllowedValue(g_ASIFW1000FilterWheelNr, "1");

}

FilterWheel::~FilterWheel ()
{
   Shutdown();
}

void FilterWheel::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int FilterWheel::Initialize()
{
   // Ensure a serial port has been set -- otherwise return an error.
   if (! g_hub.IsConnected())
      return DEVICE_ERR;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "ASIFW1000 FilterWheel", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &FilterWheel::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   // Gate Closed Position
   ret = CreateProperty(MM::g_Keyword_Closed_Position,"", MM::String, false);

   // Get the number of filters in this wheel and add these as allowed values
   ret = g_hub.SetCurrentWheel(*this, *GetCoreCallback(), wheelNr_);
   if (ret != DEVICE_OK)
      return ret;
   ret = g_hub.GetNumberOfPositions(*this, *GetCoreCallback(), wheelNr_, numPos_);
   if (ret != DEVICE_OK) 
      return ret; 
   char pos[3];
   for (int i=0; i<numPos_; i++) 
   {
      sprintf(pos, "%d", i);
      AddAllowedValue(MM::g_Keyword_State, pos);
      AddAllowedValue(MM::g_Keyword_Closed_Position, pos);
   }

   // Get current position
   int tmp;
   ret = g_hub.GetFilterWheelPosition(*this, *GetCoreCallback(), wheelNr_, tmp);
   if (ret != DEVICE_OK)
      return ret;
   pos_ = tmp;

   // Label
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
   if (ret != DEVICE_OK) 
      return ret;

   // create default positions and labels
   char state[8];
   for (int i=0; i<numPos_; i++) 
   {
      sprintf(state, "State-%d", i);
      SetPositionLabel(i,state);
   }

   GetGateOpen(open_);

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool FilterWheel::Busy()
{
   bool busy(false);
   g_hub.FilterWheelBusy(*this, *GetCoreCallback(), busy);
   return busy;
}

int FilterWheel::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////


int FilterWheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      int ret;
      bool gateOpen;
      GetGateOpen(gateOpen);
      pProp->Get(pos);
      // sanity check
      if (pos < 0)
         pos = 0;
      if (pos >= numPos_)
         pos = numPos_ - 1;
      if ((pos == pos_) && (open_ == gateOpen))
         return DEVICE_OK;

      if (gateOpen)
         ret = g_hub.SetFilterWheelPosition(*this, *GetCoreCallback(), wheelNr_, pos);
      else {
         char closedPos[MM::MaxStrLength];
         GetProperty(MM::g_Keyword_Closed_Position, closedPos);
         int gateClosedPosition = atoi(closedPos);

         ret = g_hub.SetFilterWheelPosition(*this, *GetCoreCallback(), wheelNr_, gateClosedPosition);
      }
      if (ret != DEVICE_OK)
         return ret;

      pos_ = pos;
      open_ = gateOpen;
      pProp->Set(pos_);
   }
   return DEVICE_OK;
}

int FilterWheel::OnWheelNr(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return wheel nr
      pProp->Set((long)wheelNr_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
         return ERR_CANNOT_CHANGE_PROPERTY;
      long pos;
      pProp->Get(pos);
      if (pos==0 || pos==1)
         wheelNr_ = pos;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// ASIFW1000 Shutter
///////////////////////////////////////////////////////////////////////////////
Shutter::Shutter () :
   initialized_ (false),
   name_ (g_ASIFW1000Shutter),
   shutterType_("Normally Open"),
   changedTime_(0.0),
   shutterNr_ (0)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_SHUTTER_NOT_FOUND, "Shutter was not found.  Is the ASI shutter controller attached?");

   // Todo: Add custom messages
   //
   // Shutter Nr (0 or 1)
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnShutterNr);
   CreateProperty(g_ASIFW1000ShutterNr, "0", MM::Integer, false, pAct, true);

   // Note: there can be more shutters in a controller, if there are multiple cards.  This adapter does not have the logic to deal with more than one card (which will not be hard to add)
   AddAllowedValue(g_ASIFW1000ShutterNr, "0"); 
   AddAllowedValue(g_ASIFW1000ShutterNr, "1");

   // Is this a normally open or normally closed shutter?
   pAct = new CPropertyAction (this, &Shutter::OnType);
   CreateProperty(g_ASIFW1000ShutterType, shutterType_.c_str(), MM::String, false, pAct, true);

   AddAllowedValue(g_ASIFW1000ShutterType, shutterType_.c_str()); 
   AddAllowedValue(g_ASIFW1000ShutterType, "Normally Closed"); 

   EnableDelay();
}

Shutter::~Shutter ()
{
   Shutdown();
}

void Shutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Shutter::Initialize()
{
   // Ensure a serial port has been set -- otherwise return an error.
   if (! g_hub.IsConnected())
      return DEVICE_ERR;

  // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "ASIFW1000 Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

  // Set timer for the Busy signal, or we'll get a time-out the first time we check the state of the shutter, for good measure, go back 'delay' time into the past
   changedTime_ = GetCurrentMMTime();   
   
   bool open;
   // Check current state of shutter:
   ret = g_hub.GetShutterPosition(*this, *GetCoreCallback(), shutterNr_, open);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);
   if (open)
      ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct); 
   else
      ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 

   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   //Label

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool Shutter::Busy()
{
   //TODO: using the SQ command and shutters with sensors, we can check wether the shutter is open or closed.  This need checking for sensors on initialization, and will also need caching of the last requested position
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   if (interval < (1000.0 * GetDelayMs() ))
      return true;
   else
      return false;
}

int Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int Shutter::SetOpen(bool open)
{
   
   if (shutterType_ == "Normally Closed")
      open = !open;

   changedTime_ = GetCurrentMMTime();
   if (open)
   {
      int ret = g_hub.OpenShutter(*this, *GetCoreCallback(), shutterNr_);
      if (ret != DEVICE_OK)
         return ret;
   } else
   {
      int ret = g_hub.CloseShutter(*this, *GetCoreCallback(), shutterNr_);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

int Shutter::GetOpen(bool &open)
{

   // Check current state of shutter: 
   int ret = g_hub.GetShutterPosition(*this, *GetCoreCallback(), shutterNr_, open);
   if (DEVICE_OK != ret)
      return ret;

   if (shutterType_ == "Normally Closed")
      open = !open;

   return DEVICE_OK;
}

int Shutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;  
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      bool open;
      GetOpen(open);
      if (open)
      {
         pProp->Set(1L);
      }
      else
      {
         pProp->Set(0L);
      }
   }
   else if (eAct == MM::AfterSet)
   {
      int ret;
      long pos;
      pProp->Get(pos);
      if (pos==1)
      {
         ret = this->SetOpen(true);
      }
      else
      {
         ret = this->SetOpen(false);
      }
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   return DEVICE_OK;
}

int Shutter::OnType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(shutterType_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      string type;
      pProp->Get(shutterType_);
   }
   return DEVICE_OK;
}

int Shutter::OnShutterNr(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return shutter nr
      pProp->Set((long)shutterNr_);
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
         return ERR_CANNOT_CHANGE_PROPERTY;
      long pos;
      pProp->Get(pos);
      if (pos==0 || pos==1)
         shutterNr_ = pos;
   }
   return DEVICE_OK;
}
