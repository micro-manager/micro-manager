///////////////////////////////////////////////////////////////////////////////
// FILE:          TSICam.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs Scientific Imaging compatible camera adapter
//                Handlers for get/set property events
//                
// AUTHOR:        Nenad Amodaj, 2012
// COPYRIGHT:     Thorlabs
//
// DISCLAIMER:    This file is provided WITHOUT ANY WARRANTY;
//                without even the implied warranty of MERCHANTABILITY or
//                FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#include "TsiCam.h"

extern const char* g_ReadoutRate;

using namespace std;

int TsiCam::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)binSize);
   }
   return DEVICE_OK;
}
int TsiCam::OnReadoutRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long rateIdx(0);
      int ret = GetCurrentPropertyData(g_ReadoutRate, rateIdx);
      if (ret != DEVICE_OK)
         return ret;
      bool bRet = camHandle_->SetParameter(TSI_PARAM_READOUT_SPEED_INDEX, (uint32_t)rateIdx);
      if (!bRet)
         return camHandle_->GetErrorCode();
   }
   else if (eAct == MM::BeforeGet)
   {
      uint32_t idx;
      bool bRet = camHandle_->GetParameter(TSI_PARAM_READOUT_SPEED_INDEX, sizeof(uint32_t), &idx);
      if (!bRet)
         return camHandle_->GetErrorCode();
      char val[MM::MaxStrLength];
      GetPropertyValueAt(g_ReadoutRate, idx, val);
      pProp->Set(val);
   }
   return DEVICE_OK;
}

int TsiCam::OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

int TsiCam::OnFps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

int TsiCam::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      double expMs;
      pProp->Get(expMs);
      SetExposure(expMs);
   }
   else if (eAct == MM::BeforeGet)
   {
      double expMs = GetExposure();
      pProp->Set(expMs);
   }
   return DEVICE_OK;
}

int TsiCam::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

int TsiCam::OnTemperatureSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}
