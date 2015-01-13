// DESCRIPTION:   Control devices using user-specified serial commands
//
// COPYRIGHT:     University of California San Francisco, 2014
//
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
//
// AUTHOR:        Mark Tsuchida

#include "UserDefinedSerial.h"
#include "UserDefinedSerialImpl.h"

#include "ResponseDetector.h"
#include "UserDefinedSerialConstants.h"

#include "ModuleInterface.h"

#include <boost/lexical_cast.hpp>


MODULE_API void
InitializeModuleData()
{
   RegisterDevice(g_DeviceName_GenericDevice, MM::GenericDevice,
         "Generic device using user-defined serial commands");
   RegisterDevice(g_DeviceName_Shutter, MM::ShutterDevice,
         "Generic shutter using user-defined serial commands");
   RegisterDevice(g_DeviceName_StateDevice, MM::StateDevice,
         "Generic switcher using user-defined serial commands");
}


MODULE_API MM::Device*
CreateDevice(const char* name)
{
   if (!name)
      return 0;

   if (strcmp(name, g_DeviceName_GenericDevice) == 0)
      return new UserDefSerialGenericDevice();
   else if (strcmp(name, g_DeviceName_Shutter) == 0)
      return new UserDefSerialShutter();
   else if (strcmp(name, g_DeviceName_StateDevice) == 0)
      return new UserDefSerialStateDevice();

   return 0;
}


MODULE_API void
DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


UserDefSerialGenericDevice::UserDefSerialGenericDevice()
{
}


void
UserDefSerialGenericDevice::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceName_GenericDevice);
}


UserDefSerialShutter::UserDefSerialShutter() :
   lastSetOpen_(false)
{
   CreatePreInitProperties();
}


void
UserDefSerialShutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceName_Shutter);
}


int
UserDefSerialShutter::SetOpen(bool open)
{
   int err;
   if (open)
      err = SendRecv(openCommand_, openResponse_);
   else
      err = SendRecv(closeCommand_, closeResponse_);
   if (err != DEVICE_OK)
      return err;
   StartBusy();

   lastSetOpen_ = open;
   err = OnPropertyChanged(MM::g_Keyword_State, (open ? "1" : "0"));
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


int
UserDefSerialShutter::GetOpen(bool& open)
{
   int err;

   if (!queryCommand_.empty() &&
         !queryOpenResponse_.empty() &&
         !queryCloseResponse_.empty())
   {
      std::vector< std::vector<char> > alternatives;
      alternatives.push_back(queryOpenResponse_);
      alternatives.push_back(queryCloseResponse_);
      size_t index;
      err = SendQueryRecvAlternative(queryCommand_, alternatives, index);
      if (err != DEVICE_OK)
         return err;
      open = (index == 0);
   }
   else
   {
      // Cannot ask device; use memorized state
      open = lastSetOpen_;
   }
   return DEVICE_OK;
}


int
UserDefSerialShutter::Initialize()
{
   int err;
   err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   err = SetOpen(false);
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


int
UserDefSerialShutter::Shutdown()
{
   int err;
   err = SetOpen(false);
   // Ignore error.

   err = Super::Shutdown();
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


void
UserDefSerialShutter::CreatePreInitProperties()
{
   CreateByteStringProperty(g_PropName_OpenCommand, openCommand_, true);
   CreateByteStringProperty(g_PropName_OpenResponse, openResponse_, true);
   CreateByteStringProperty(g_PropName_CloseCommand, closeCommand_, true);
   CreateByteStringProperty(g_PropName_CloseResponse, closeResponse_, true);
   CreateByteStringProperty(g_PropName_QueryStateCommand, queryCommand_, true);
   CreateByteStringProperty(g_PropName_QueryOpenResponse,
         queryOpenResponse_, true);
   CreateByteStringProperty(g_PropName_QueryCloseResponse,
         queryCloseResponse_, true);
}


int
UserDefSerialShutter::CreatePostInitProperties()
{
   int err;
   err = Super::CreatePostInitProperties();
   if (err != DEVICE_OK)
      return err;

   err = CreateIntegerProperty(MM::g_Keyword_State, (lastSetOpen_ ? 1 : 0),
         false, new CPropertyAction(this, &Self::OnState));
   if (err != DEVICE_OK)
      return err;
   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   return DEVICE_OK;
}


int
UserDefSerialShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool state;
      int err = GetOpen(state);
      if (err != DEVICE_OK)
         return err;
      pProp->Set(state ? 1L : 0L);
   }
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);
      return SetOpen(state != 0);
   }
   return DEVICE_OK;
}


UserDefSerialStateDevice::UserDefSerialStateDevice() :
   numPositions_(10),
   currentPosition_(0)
{
   CreatePreInitProperties();
}


void
UserDefSerialStateDevice::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceName_StateDevice);
}


unsigned long
UserDefSerialStateDevice::GetNumberOfPositions() const
{
   return static_cast<long>(numPositions_);
}


int
UserDefSerialStateDevice::Initialize()
{
   int err;
   err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   // Provide default label names
   for (size_t i = 0; i < numPositions_; ++i)
   {
      SetPositionLabel(static_cast<long>(i),
            ("State-" + boost::lexical_cast<std::string>(i)).c_str());
   }

   return DEVICE_OK;
}


void
UserDefSerialStateDevice::CreatePreInitProperties()
{
   CreateIntegerProperty(g_PropName_NumPositions,
         static_cast<long>(numPositions_), false,
         new CPropertyAction(this, &Self::OnNumberOfPositions), true);
   SetPropertyLimits(g_PropName_NumPositions, 2.0, 256.0);

   // We cannot create the position commands here, since we don't know the
   // number of positions until after initialization.
}


int
UserDefSerialStateDevice::CreatePostInitProperties()
{
   int err;
   err = Super::CreatePostInitProperties();
   if (err != DEVICE_OK)
      return err;

   err = CreateIntegerProperty(MM::g_Keyword_State,
         static_cast<long>(currentPosition_), false,
         new CPropertyAction(this, &Self::OnState));
   if (err != DEVICE_OK)
      return err;
   std::vector<std::string> stateValues;
   stateValues.reserve(numPositions_);
   for (size_t i = 0; i < numPositions_; ++i)
      stateValues.push_back(boost::lexical_cast<std::string>(i));
   err = SetAllowedValues(MM::g_Keyword_State, stateValues);
   if (err != DEVICE_OK)
      return err;

   err = CreateStringProperty(MM::g_Keyword_Label, "", false,
         new CPropertyAction(this, &CStateBase::OnLabel));
   if (err != DEVICE_OK)
      return err;

   err = CreateByteStringProperty(g_PropName_QueryPositionCommand,
         queryCommand_);
   if (err != DEVICE_OK)
      return err;

   positionCommands_.reset(new std::vector<char>[numPositions_]);
   positionResponses_.reset(new std::vector<char>[numPositions_]);
   queryResponses_.reset(new std::vector<char>[numPositions_]);
   for (size_t i = 0; i < numPositions_; ++i)
   {
      err = CreateByteStringProperty(g_PropNamePrefix_SetPositionCommand +
            boost::lexical_cast<std::string>(i), positionCommands_[i]);
      if (err != DEVICE_OK)
         return err;

      err = CreateByteStringProperty(g_PropNamePrefix_SetPositionResponse +
            boost::lexical_cast<std::string>(i), positionResponses_[i]);
      if (err != DEVICE_OK)
         return err;

      err = CreateByteStringProperty(g_PropNamePrefix_QueryPositionResponse +
            boost::lexical_cast<std::string>(i), queryResponses_[i]);
      if (err != DEVICE_OK)
         return err;
   }

   return DEVICE_OK;
}


int
UserDefSerialStateDevice::OnNumberOfPositions(MM::PropertyBase* pProp,
      MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<long>(numPositions_));
   }
   else if (eAct == MM::AfterSet)
   {
      long num;
      pProp->Get(num);
      numPositions_ = num > 0 ? num : 0;
   }
   return DEVICE_OK;
}


int
UserDefSerialStateDevice::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool canQuery = false;
      // We can query if the query command and all possible responses are
      // nonempty
      if (!queryCommand_.empty())
      {
         canQuery = true;
         for (size_t i = 0; i < numPositions_; ++i)
         {
            if (queryResponses_[i].empty())
            {
               canQuery = false;
               break;
            }
         }
      }
      if (canQuery)
      {
         std::vector< std::vector<char> > alternatives;
         alternatives.reserve(numPositions_);
         for (size_t i = 0; i < numPositions_; ++i)
            alternatives.push_back(queryResponses_[i]);
         size_t index;
         int err;
         err = SendQueryRecvAlternative(queryCommand_, alternatives, index);
         if (err != DEVICE_OK)
            return err;
         pProp->Set(static_cast<long>(index));
      }
      else
      {
         // Use memorized position
         pProp->Set(static_cast<long>(currentPosition_));
      }
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos < 0 || static_cast<size_t>(pos) > numPositions_)
         return DEVICE_UNKNOWN_POSITION;
      int err;
      err = SendRecv(positionCommands_[pos], positionResponses_[pos]);
      if (err != DEVICE_OK)
         return err;
      StartBusy();

      currentPosition_ = pos;
      err = OnStateChanged(pos);
      if (err != DEVICE_OK)
         return err;

      return DEVICE_OK;
   }
   return DEVICE_OK;
}
