///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIDevice.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI generic device class, MODULE_API items, and common defines
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
// BASED ON:      ASIStage.cpp
//
//

#ifdef WIN32
#define snprintf _snprintf 
#pragma warning(disable: 4355)
#endif

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "ASIDevice.h"
#include "ASITiger.h"
#include "ASIFWheel.h"
#include "ASIHub.h"
#include "ASITigerComm.h"
#include <iostream>

////////////////////////////////////////////////////////////////
// *********** generic ASI device class *************************
//
ASIDevice::ASIDevice(MM::Device *device, const char* name) :
      initialized_(false),
      firmwareVersion_(0.0),
      hub_(NULL),
      ret_(DEVICE_OK),
      addressString_(g_EmptyCardAddressStr)
{
   // get pointer to object where we can call property and other methods
   deviceASI_ = static_cast<ASIDeviceBase *>(device);
   deviceASI_->InitializeDefaultErrorMessages();
   InitializeASIErrorMessages();

   // name property will be used to re-create the object by calling CreateDevice again with this parameter
   // if name isn't specified then skip this step (=> method for parent objects to delay setting name until child created)
   if (strcmp(name, "") != 0)
      deviceASI_->CreateProperty(MM::g_Keyword_Name, name, MM::String, true);


   // sometimes constructor gets called without the full name like in the case of the hub
   //   so only set up these properties if we have the required information
   if (IsExtendedName(name))
   {
      addressString_ = GetHexAddrFromExtName(name);
      if (addressString_.compare(g_EmptyCardAddressStr) != 0 )
      {
         deviceASI_->CreateProperty(g_TigerHexAddrPropertyName, addressString_.c_str(), MM::String, true);
         addressChar_ = ConvertToTigerRawAddress(addressString_);
      }
   }

}

int ASIDevice::Initialize(bool skipFirmware)
{
   // should be implemented in child class too, child should call this one
   // let child class decide whether to set initialized_ to true or not

   // get the hub information
   MM::Hub* genericHub = deviceASI_->GetParentHub();
   if (!genericHub)
      return DEVICE_COMM_HUB_MISSING;
   hub_ = dynamic_cast<ASIHub*>(genericHub);
   if (!hub_)
      return DEVICE_COMM_HUB_MISSING;

   // get the firmware version and expose that as property plus store it
   // skip in special cases (when called with true flag, false is default) including
   //    FWheel: different serial terminator => own Initialize() handles
   //    TigerCommTub: needs to return different value plus doesn't have address
   if (!skipFirmware)
   {
      ostringstream command; command.str("");
      command << addressChar_ << "V";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A v") );
      firmwareVersion_ = hub_->ParseAnswerAfterPosition(4);
      command.str("");
      command << firmwareVersion_;
      RETURN_ON_MM_ERROR ( deviceASI_->CreateProperty(g_FirmwareVersionPropertyName, command.str().c_str(), MM::Float, true) );
   }

   return DEVICE_OK;
}

ASIDevice::~ASIDevice()
{
   Shutdown();
}

int ASIDevice::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

void ASIDevice::GetName(char* pszName) const
{
   char name[MM::MaxStrLength];
   if (deviceASI_->HasProperty(MM::g_Keyword_Name))
      deviceASI_->GetProperty(MM::g_Keyword_Name, name);
   else
      strcpy(name, "Undefined");
   CDeviceUtils::CopyLimitedString(pszName, name);
}

bool ASIDevice::IsExtendedName(const char* name) const
{
   vector<string> vName;
   CDeviceUtils::Tokenize(name, vName, ":");
   return (vName.size() > 2);
}

string ASIDevice::GetAxisLetterFromExtName(const char* name, int position) const
// returns single-character string
{
   vector<string> vName;
   CDeviceUtils::Tokenize(name, vName, ":");
   if (vName.size() > 1)
      return (vName[1].substr(position,1));
   else
      return g_EmptyAxisLetterStr;
}

string ASIDevice::GetHexAddrFromExtName(const char* name) const
{
   vector<string> vName;
   CDeviceUtils::Tokenize(name, vName, ":");
   if (vName.size() > 2)
      return (vName[2]);
   else
      return g_EmptyCardAddressStr;
}

string ASIDevice::ConvertToTigerRawAddress(const string &s) const
{
   // Tiger addresses are 0x31 to 0x39 and then 0x81 to 0xF5 (i.e. '1' to '9' and then extended ASCII)
   // these addresses are appended (in extended ASCII char) to serial commands that are addressed
   // MM doesn't handle names with with extended ASCII, and we need to include address in MM's device name
   // so in MM device names we represent the address in 2-digit hex string (31..39, 81..F5)
   // use this function to get the serial char to send from a MM 2-digit hex string
   // returns g_EmptyCardAddressCode in case of error (set to space for minimum impact on Tiger operation)

   if (s.size() != 2)
      return g_EmptyCardAddressCode;
   unsigned int code;
   stringstream ss;
   ss << hex << s;
   ss >> code;
   if ((code >= 0x31 && code <= 0x39) || (code >= 0x81 && code <= 0xF5))
   {
      ss.str("");
      ss.clear(); // clears hex flag
      ss << (char) code;
      string s2;
      ss >> s2;
      return s2;
   }
   else
      return g_EmptyCardAddressCode;
}

void ASIDevice::InitializeASIErrorMessages()
{
   deviceASI_->SetErrorText(ERR_UNRECOGNIZED_ANSWER, g_Msg_ERR_UNRECOGNIZED_ANSWER);
   deviceASI_->SetErrorText(ERR_FILTER_WHEEL_NOT_READY, g_Msg_ERR_FILTER_WHEEL_NOT_READY);
   deviceASI_->SetErrorText(ERR_FILTER_WHEEL_SPINNING, g_Msg_ERR_FILTER_WHEEL_SPINNING);
   deviceASI_->SetErrorText(ERR_NOT_ENOUGH_AXES, g_Msg_ERR_NOT_ENOUGH_AXES);
   deviceASI_->SetErrorText(ERR_TOO_LARGE_ADDRESSES, g_Msg_ERR_TOO_LARGE_ADDRESSES);
   deviceASI_->SetErrorText(ERR_CRISP_NOT_CALIBRATED, g_Msg_ERR_CRISP_NOT_CALIBRATED);
   deviceASI_->SetErrorText(ERR_CRISP_NOT_LOCKED, g_Msg_ERR_CRISP_NOT_LOCKED);
}
