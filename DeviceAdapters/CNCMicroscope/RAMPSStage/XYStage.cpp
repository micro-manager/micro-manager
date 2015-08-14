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
const char* g_StepSizeProp = "Step Size";

///////////////////////////////////////////////////////////////////////////////
// RAMPSXYStage implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~

RAMPSXYStage::RAMPSXYStage() :
    CXYStageBase<RAMPSXYStage>(),
    stepSize_um_(0.025),
    posX_um_(0.0),
    posY_um_(0.0),
    busy_(false),
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
  LogMessage("XYStage: initialize");
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
  LogMessage("XYStage: Busy called");
  
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());
  std::string status = pHub->GetState();

  LogMessage("Status is:");
  LogMessage(status);
  status_ = status;

  if (status_ == "Idle") {
	  LogMessage("idle, return false.");
    return false;

  }
  else if (status_ == "Running") {
    LogMessage("Running, return true.");
    return true;
  }
  else {
	  LogMessage("Unexpected status.");
	  return false;
  }
}

double RAMPSXYStage::GetStepSize() {return stepSize_um_;}

int RAMPSXYStage::SetPositionSteps(long x, long y)
{
  LogMessage("XYStage: SetPositionSteps");
  RAMPSHub* pHub = static_cast<RAMPSHub*>(GetParentHub());
  std::string status = pHub->GetState();
  if (status == "Running") {
      return ERR_STAGE_MOVING;
  }

  /*
  double newPosX = x * stepSize_um_;
  double newPosY = y * stepSize_um_;
  double difX = newPosX - posX_um_;
  double difY = newPosY - posY_um_;
  double distance = sqrt( (difX * difX) + (difY * difY) );
  */

  posX_um_ = x * stepSize_um_;
  posY_um_ = y * stepSize_um_;

  // TODO(dek): if no position change, don't send new position.
  char buff[100];
  sprintf(buff, "G0 X%f Y%f", posX_um_/1000., posY_um_/1000.);
  std::string buffAsStdStr = buff;
  int ret = pHub->SendCommand(buffAsStdStr);
  if (ret != DEVICE_OK)
    return ret;

  pHub->SetTargetXY(posX_um_, posY_um_);

  ret = OnXYStagePositionChanged(posX_um_, posY_um_);
  if (ret != DEVICE_OK)
    return ret;

  return DEVICE_OK;
}

int RAMPSXYStage::GetPositionSteps(long& x, long& y)
{
  LogMessage("XYStage: GetPositionSteps");
  x = (long)(posX_um_ / stepSize_um_);
  y = (long)(posY_um_ / stepSize_um_);
  return DEVICE_OK;
}

int RAMPSXYStage::SetRelativePositionSteps(long x, long y)
{
  LogMessage("XYStage: SetRelativePositioNSteps");
  long xSteps, ySteps;
  GetPositionSteps(xSteps, ySteps);

  return this->SetPositionSteps(xSteps+x, ySteps+y);
}

int RAMPSXYStage::Home() {   LogMessage("RAMPS XYStage home.");
return DEVICE_OK; }
int RAMPSXYStage::Stop() { LogMessage("RAMPS XYStage stop.");
return DEVICE_OK; }

int RAMPSXYStage::SetOrigin() { LogMessage("RAMPS XYStage set origin."); return DEVICE_OK; }

int RAMPSXYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
  LogMessage("RAMPS XYStage get limits um");
  xMin = lowerLimit_; xMax = upperLimit_;
  yMin = lowerLimit_; yMax = upperLimit_;
  return DEVICE_OK;
}

int RAMPSXYStage::GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
{   LogMessage("RAMPS XYStage get limits um");
return DEVICE_UNSUPPORTED_COMMAND; }

double RAMPSXYStage::GetStepSizeXUm() {   LogMessage("RAMPS XYStage get step size x um");
return stepSize_um_; }
double RAMPSXYStage::GetStepSizeYUm() {   LogMessage("RAMPS XYStage get step size y um");
return stepSize_um_; }
int RAMPSXYStage::Move(double /*vx*/, double /*vy*/) {LogMessage("RAMPS XYStage move"); return DEVICE_OK;}

int RAMPSXYStage::IsXYStageSequenceable(bool& isSequenceable) const {isSequenceable = true; return DEVICE_OK;}




int RAMPSXYStage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
LogMessage("RAMPS XYStage OnStepSize");
  if (eAct == MM::BeforeGet)
  {
        LogMessage("RAMPS XYStage OnStepSizex");

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

   
  LogMessage("Set step size");

  return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
// none implemented
