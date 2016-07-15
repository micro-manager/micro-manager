/*
Copyright 2015 Google Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf
#endif
#include "RAMPS.h"

#include "ZStage.h"
using namespace std;

#include "MMDevice.h"
#include "DeviceBase.h"

#include <boost/lexical_cast.hpp>

extern const char* g_ZStageDeviceName;
extern const char* g_Keyword_LoadSample;

RAMPSZStage::RAMPSZStage() :
    // http://www.shapeoko.com/wiki/index.php/Zaxis_ACME
    stepSize_um_ (5.),
    posZ_um_(0.0),
    initialized_ (false)
{
  InitializeDefaultErrorMessages();

  SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Focus drive to work");
  SetErrorText(ERR_NO_FOCUS_DRIVE, "No focus drive found in this microscopes");
}

RAMPSZStage::~RAMPSZStage()
{
  Shutdown();
}

void RAMPSZStage::GetName(char* Name) const
{
  CDeviceUtils::CopyLimitedString(Name, g_ZStageDeviceName);
}

int RAMPSZStage::Initialize()
{
  
  InitializeDefaultErrorMessages();

  // parent ID display
  CreateHubIDProperty();

  // set property list
  // ----------------

  // Name
  int ret = CreateProperty(MM::g_Keyword_Name, g_ZStageDeviceName, MM::String, true);
  if (DEVICE_OK != ret)
    return ret;

  // Description
  ret = CreateProperty(MM::g_Keyword_Description, "Z-drive", MM::String, true);
  if (DEVICE_OK != ret)
    return ret;

  // Position
  CPropertyAction* pAct = new CPropertyAction(this, &RAMPSZStage::OnPosition);
  ret = CreateProperty(MM::g_Keyword_Position, "0", MM::Float,false, pAct);
  if (ret != DEVICE_OK)
    return ret;

  // Update lower and upper limits.  These values are cached, so if they change during a session, the adapter will need to be re-initialized
  ret = UpdateStatus();
  if (ret != DEVICE_OK)
    return ret;

  initialized_ = true;

  return DEVICE_OK;
}

int RAMPSZStage::Shutdown()
{
  initialized_ = false;

  return DEVICE_OK;
}

bool RAMPSZStage::Busy()
{
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());

  return pHub->Busy();
}
int RAMPSZStage::SetPositionUm(double pos)
{
  long steps = (long)(pos / stepSize_um_ + 0.5);
  int ret = SetPositionSteps(steps);
  if (ret != DEVICE_OK)
    return ret;

  return DEVICE_OK;
}
int RAMPSZStage::GetPositionUm(double& pos)
{
  long steps;
  int ret = GetPositionSteps(steps);
  if (ret != DEVICE_OK)
    return ret;
  pos = steps * stepSize_um_;

  return DEVICE_OK;
}

double RAMPSZStage::GetStepSize() const {
	return stepSize_um_;
}

/*
 * Requests movement to new z postion from the controller.  This function does the actual communication
 */
int RAMPSZStage::SetPositionSteps(long steps)
{
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());
  if (pHub->Busy()) {
      return ERR_STAGE_MOVING;
  }

  posZ_um_ = steps * stepSize_um_;

  char buff[100];
  sprintf(buff, "G0 Z%f", posZ_um_/1000.);
  std::string buffAsStdStr = buff;
  int ret = pHub->SendCommand(buffAsStdStr);
  if (ret != DEVICE_OK)
    return ret;

  
  std::string answer;
  ret = pHub->ReadResponse(answer, 1000);
  if (ret != DEVICE_OK) {
	  LogMessage("Error sending Z move.");
	  return ret;
  }
  if (answer != "ok") {
	  LogMessage("Failed to get ok response to Z move.");
  }
  ret = OnStagePositionChanged(posZ_um_);
  if (ret != DEVICE_OK)
    return ret;

  return DEVICE_OK;
}

/*
 * Requests current z postion from the controller.  This function does the actual communication
 */
int RAMPSZStage::GetPositionSteps(long& steps)
{
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());
  pHub->GetStatus();
  steps = (long)(posZ_um_ / stepSize_um_);

  // TODO(dek): implement status to get Z position
  return DEVICE_OK;
}

int RAMPSZStage::Home() {
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());
  pHub->PurgeComPortH();
  int ret = pHub->SendCommand("G28 Z0");
  if (ret != DEVICE_OK) {
    LogMessage("Homing command failed.");
    return ret;
  }
  std::string answer;
  ret = pHub->ReadResponse(answer, 50000);
  if (ret != DEVICE_OK) {
    LogMessage("error getting response to homing command.");
    return ret;
  }
  if (answer != "ok") {
    LogMessage("Homing command: expected ok.");
    return DEVICE_ERR;
  }
  return DEVICE_OK;
}

int RAMPSZStage::SetOrigin() {
	return SetAdapterOriginUm(0);
}

int RAMPSZStage::SetAdapterOriginUm(double z) {
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());
  pHub->PurgeComPortH();
  std::string xval = boost::lexical_cast<std::string>((long double) z);
  std::string command = "G92 Z" + xval;
  int ret = pHub->SendCommand(command);
  if (ret != DEVICE_OK) {
    LogMessage("Origin command failed.");
    return ret;
  }
  std::string answer;
  ret = pHub->ReadResponse(answer);
  if (ret != DEVICE_OK) {
    LogMessage("error getting response to origin command.");
    return ret;
  }
  if (answer != "ok") {
    LogMessage("origin command: expected ok.");
    return DEVICE_ERR;
  }
  return DEVICE_OK;
}

int RAMPSZStage::GetLimits(double& lower, double& upper)
{
  lower = lowerLimit_;
  upper = upperLimit_;
  return DEVICE_OK;
}



bool RAMPSZStage::IsContinuousFocusDrive() const {return false;}

// TODO(dek): implement GetUpperLimit and GetLowerLimit

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Uses the Get and Set PositionUm functions to communicate with controller
 */
int RAMPSZStage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    double pos;
    int ret = GetPositionUm(pos);
    if (ret != DEVICE_OK)
      return ret;
    pProp->Set(pos);
  }
  else if (eAct == MM::AfterSet)
  {
    double pos;
    pProp->Get(pos);
    int ret = SetPositionUm(pos);
    if (ret != DEVICE_OK)
      return ret;
  }

  return DEVICE_OK;
}


// TODO(dek): implement OnStageLoad

// Sequence functions (unimplemented)
int RAMPSZStage::IsStageSequenceable(bool& isSequenceable) const {isSequenceable = true; return DEVICE_OK;}
int RAMPSZStage::GetStageSequenceMaxLength(long& nrEvents) const  {nrEvents = 0; return DEVICE_OK;}
int RAMPSZStage::StartStageSequence() {return DEVICE_OK;}
int RAMPSZStage::StopStageSequence() {return DEVICE_OK;}
int RAMPSZStage::ClearStageSequence() {return DEVICE_OK;}
int RAMPSZStage::AddToStageSequence(double /*position*/) {return DEVICE_OK;}
int RAMPSZStage::SendStageSequence() {return DEVICE_OK;}
