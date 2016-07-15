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

#include "DeviceUtils.h"

#include "RAMPS.h"
#include "XYStage.h"

#include <boost/lexical_cast.hpp>

const char* g_StepSizeProp = "Step Size";

///////////////////////////////////////////////////////////////////////////////
// RAMPSXYStage implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~

RAMPSXYStage::RAMPSXYStage() :
    CXYStageBase<RAMPSXYStage>(),
    stepSize_um_(0.025),
    posX_um_(0.0),
    posY_um_(0.0),
    initialized_(false),
    lowerLimit_(0.0),
    upperLimit_(20000.0),
	status_("")
{
  InitializeDefaultErrorMessages();

  // parent ID display
  CreateHubIDProperty();
}

RAMPSXYStage::~RAMPSXYStage()
{
  Shutdown();
}

extern const char* g_XYStageDeviceName;
const char* NoHubError = "Parent Hub not defined.";



void RAMPSXYStage::GetName(char* Name) const
{
  CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceName);
}

int RAMPSXYStage::Initialize()
{
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());
  if (pHub)
  {
    char hubLabel[MM::MaxStrLength];
    pHub->GetLabel(hubLabel);
    SetParentID(hubLabel); // for backward comp.
  }
  else
    LogMessage(NoHubError);

  if (initialized_)
    return DEVICE_OK;

  // set property list
  // -----------------

  // Name
  int ret = CreateStringProperty(MM::g_Keyword_Name, g_XYStageDeviceName, true);
  if (DEVICE_OK != ret)
    return ret;

  // Description
  ret = CreateStringProperty(MM::g_Keyword_Description, "RAMPS XY stage driver", true);
  if (DEVICE_OK != ret)
    return ret;

  CPropertyAction* pAct = new CPropertyAction (this, &RAMPSXYStage::OnStepSize);
  CreateProperty(g_StepSizeProp, CDeviceUtils::ConvertToString(stepSize_um_), MM::Float, false, pAct);

  // Update lower and upper limits.  These values are cached, so if they change during a session, the adapter will need to be re-initialized
  ret = UpdateStatus();
  if (ret != DEVICE_OK)
    return ret;

  
  initialized_ = true;

  return DEVICE_OK;
}

int RAMPSXYStage::Shutdown()
{
  if (initialized_)
  {
    initialized_ = false;
  }
  return DEVICE_OK;
}

bool RAMPSXYStage::Busy()
{
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());

  return pHub->Busy();
}

double RAMPSXYStage::GetStepSize() {return stepSize_um_;}

int RAMPSXYStage::SetPositionSteps(long x, long y)
{
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());
  std::string status = pHub->GetState();
  if (pHub->Busy()) {
      return ERR_STAGE_MOVING;
  }

  posX_um_ = x * stepSize_um_;
  posY_um_ = y * stepSize_um_;

  // TODO(dek): if no position change, don't send new position.
  char buff[100];
  sprintf(buff, "G0 X%f Y%f", posX_um_/1000., posY_um_/1000.);
  std::string buffAsStdStr = buff;
  int ret = pHub->SendCommand(buffAsStdStr);
  if (ret != DEVICE_OK)
    return ret;

  std::string answer;
  ret = pHub->ReadResponse(answer, 1000);
  if (ret != DEVICE_OK) {
	  LogMessage("Error sending XY move.");
	  return ret;
  }
  if (answer != "ok") {
	  LogMessage("Failed to get ok response to XY move.");
  }
  ret = OnXYStagePositionChanged(posX_um_, posY_um_);
  if (ret != DEVICE_OK)
    return ret;

  return DEVICE_OK;
}

int RAMPSXYStage::GetPositionSteps(long& x, long& y)
{
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());
  pHub->GetStatus();
  x = (long)(posX_um_ / stepSize_um_);
  y = (long)(posY_um_ / stepSize_um_);
  return DEVICE_OK;
}

int RAMPSXYStage::SetRelativePositionSteps(long x, long y)
{
  long xSteps, ySteps;
  GetPositionSteps(xSteps, ySteps);

  return this->SetPositionSteps(xSteps+x, ySteps+y);
}

int RAMPSXYStage::Home() {
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());
  pHub->PurgeComPortH();
  int ret = pHub->SendCommand("G28 X0 Y0");
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

int RAMPSXYStage::SetOrigin() {
  return SetAdapterOriginUm(0,0);
}

int RAMPSXYStage::SetAdapterOriginUm(double x, double y) {
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());
  pHub->PurgeComPortH();
  std::string xval = boost::lexical_cast<std::string>((long double) x);
  std::string yval = boost::lexical_cast<std::string>((long double) y);
  std::string command = "G92 X" + xval + " " + yval;  
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
    LogMessage("Origin command: expected ok.");
    return DEVICE_ERR;
  }
  return DEVICE_OK;
}

int RAMPSXYStage::Stop() { return DEVICE_OK; }

int RAMPSXYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
  xMin = lowerLimit_; xMax = upperLimit_;
  yMin = lowerLimit_; yMax = upperLimit_;
  return DEVICE_OK;
}

int RAMPSXYStage::GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
{return DEVICE_UNSUPPORTED_COMMAND; }

double RAMPSXYStage::GetStepSizeXUm() { return stepSize_um_; }
double RAMPSXYStage::GetStepSizeYUm() { return stepSize_um_; }
int RAMPSXYStage::Move(double /*vx*/, double /*vy*/) {return DEVICE_OK;}

int RAMPSXYStage::IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = true; return DEVICE_OK;}




int RAMPSXYStage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set(stepSize_um_);
  }
  else if (eAct == MM::AfterSet)
  {
    if (initialized_)
    {
      double stepSize_um;
      pProp->Get(stepSize_um);
      stepSize_um_ = stepSize_um;
    }

  }

  return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
// none implemented
