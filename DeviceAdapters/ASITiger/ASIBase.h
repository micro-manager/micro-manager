///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIBase.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI generic device class templates
//
// COPYRIGHT:     Applied Scientific Instrumentation, Eugene OR
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 09/2013
//
// BASED ON:      ASIStage.h
//

#ifndef _ASIBase_H_
#define _ASIBase_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "ASITiger.h"
#include <string>


////////////////////////////////////////////////////////////////
// *********** generic ASI device class templates **************
// Purpose: to to take care of all common/generic ASI functionality.
//
// Here is how the class hierarchy looks (using CXYStage as an example):
// MM::Device
// MM::XYStage
// CDeviceBase<MM::XYStage, CXYStage>
// CXYStageBase<CXYStage>
// ASIBase<CXYStageBase, CXYStage>
// ASIPeripheralBase<CXYStageBase, CXYStage>
// CXYStage
//
// In other words, we insert ASIBase<> and ASIPeripheralBase<> into the
// inheritance chain just before the concrete device class. (In the case of
// class ASIHub, we skip ASIPeripheralBase<> and inherit directly from
// ASIBase<HubBase, ASIHub>.)
//
// Thus, ASIBase<> and ASIPeripheralBase<> can implement common functionality
// by making use of CDeviceBase methods.
////////////////////////////////////////////////////////////////

template <template <typename> class TDeviceBase, class UConcreteDevice>
class ASIBase : public TDeviceBase<UConcreteDevice>
{
public:
   ASIBase(const char* name) :
      initialized_(false),
      firmwareVersion_(0.0)
   {
      this->InitializeDefaultErrorMessages();
      InitializeASIErrorMessages();

      // note that this name is different from the label that MM uses in e.g. SetProperty()

      // name property will be used to re-create the object by calling CreateDevice again with this parameter
      // if name isn't specified then skip this step (=> method for parent objects to delay setting name until child created)
      if (strcmp(name, "") != 0)
         this->CreateProperty(MM::g_Keyword_Name, name, MM::String, true);
   }

   virtual ~ASIBase() { }

   int Shutdown()
   {
      initialized_ = false;
      return DEVICE_OK;
   }

   void GetName(char* pszName) const
   {
      char name[MM::MaxStrLength];
      if (this->HasProperty(MM::g_Keyword_Name))
         this->GetProperty(MM::g_Keyword_Name, name);
      else
         strcpy(name, "Undefined");
      CDeviceUtils::CopyLimitedString(pszName, name);
   }

   bool Busy() { return false; } // should be implemented in child class

protected:
   bool initialized_;      // used to signal that device properties have been read from controller
   double firmwareVersion_; // firmware version
   string firmwareDate_;    // firmware compile date
   string firmwareBuild_;   // firmware build name

   bool FirmwareVersionAtLeast(double minimumFirmwareVersion)
   {
      return firmwareVersion_ > (minimumFirmwareVersion - 1e-6);  // 1e-6 to make sure match is counted as OK despite possible floating point arithmetic issues
   }

   void InitializeASIErrorMessages()
   {
      this->SetErrorText(ERR_UNRECOGNIZED_ANSWER, g_Msg_ERR_UNRECOGNIZED_ANSWER);
      this->SetErrorText(ERR_FILTER_WHEEL_NOT_READY, g_Msg_ERR_FILTER_WHEEL_NOT_READY);
      this->SetErrorText(ERR_FILTER_WHEEL_SPINNING, g_Msg_ERR_FILTER_WHEEL_SPINNING);
      this->SetErrorText(ERR_NOT_ENOUGH_AXES, g_Msg_ERR_NOT_ENOUGH_AXES);
      this->SetErrorText(ERR_TOO_LARGE_ADDRESSES, g_Msg_ERR_TOO_LARGE_ADDRESSES);
      this->SetErrorText(ERR_INFO_COMMAND_NOT_SUPPORTED, g_Msg_ERR_INFO_COMMAND_NOT_SUPPORTED);
      this->SetErrorText(ERR_TIGER_PAIR_NOT_PRESENT, g_Msg_ERR_TIGER_PAIR_NOT_PRESENT);
      this->SetErrorText(ERR_TIGER_DEV_NOT_SUPPORTED, g_Msg_ERR_TIGER_DEV_NOT_SUPPORTED);
      this->SetErrorText(ERR_CRISP_NOT_CALIBRATED, g_Msg_ERR_CRISP_NOT_CALIBRATED);
      this->SetErrorText(ERR_CRISP_NOT_LOCKED, g_Msg_ERR_CRISP_NOT_LOCKED);
      this->SetErrorText(ERR_UNKNOWN_COMMAND, g_Msg_ERR_UNKNOWN_COMMAND);
      this->SetErrorText(ERR_UNKNOWN_AXIS, g_Msg_ERR_UNKNOWN_AXIS);
      this->SetErrorText(ERR_MISSING_PARAM, g_Msg_ERR_MISSING_PARAM);
      this->SetErrorText(ERR_PARAM_OUT_OF_RANGE, g_Msg_ERR_PARAM_OUT_OF_RANGE);
      this->SetErrorText(ERR_OPERATION_FAILED, g_Msg_ERR_OPERATION_FAILED);
      this->SetErrorText(ERR_UNDEFINED_ERROR, g_Msg_ERR_UNDEFINED_ERROR);
      this->SetErrorText(ERR_INVALID_ADDRESS, g_Msg_ERR_INVALID_ADDRESS);
   }
};

#endif // _ASIBase_H_
