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

using namespace std;

int TsiCam::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      if (color)
         return DEVICE_OK; // ignore if color camera

      long bin = 1;
      pProp->Get(bin);
      roiBinData.XBin = bin;
      roiBinData.YBin = bin;
      roiBinData.XPixels = fullFrame.XPixels / bin;
      roiBinData.YPixels = fullFrame.YPixels / bin;
      roiBinData.XPixels = fullFrame.XPixels;
      roiBinData.YPixels = fullFrame.YPixels;
      return ResizeImageBuffer(roiBinData);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)roiBinData.XBin);
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


int TsiCam::OnTaps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long tapIdx(0);
      int ret = GetCurrentPropertyData(g_NumberOfTaps, tapIdx);
      if (ret != DEVICE_OK)
         return ret;
      bool bRet = camHandle_->SetParameter(TSI_PARAM_TAPS_INDEX, (uint32_t)tapIdx);
      if (!bRet)
         return camHandle_->GetErrorCode();
   }
   else if (eAct == MM::BeforeGet)
   {
      uint32_t idx;
      bool bRet = camHandle_->GetParameter(TSI_PARAM_TAPS_INDEX, sizeof(uint32_t), &idx);
      if (!bRet)
         return camHandle_->GetErrorCode();
      char val[MM::MaxStrLength];
      GetPropertyValueAt(g_NumberOfTaps, idx, val);
      pProp->Set(val);
   }
   return DEVICE_OK;
}

int TsiCam::OnTriggerMode(MM::PropertyBase* /*pProp*/, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

int TsiCam::OnFps(MM::PropertyBase* /*pProp*/, MM::ActionType eAct)
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

int TsiCam::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long gain;
      pProp->Get(gain);
      bool bRet = camHandle_->SetParameter(TSI_PARAM_GAIN, (uint32_t)gain);
      if (!bRet)
         return camHandle_->GetErrorCode();
   }
   else if (eAct == MM::BeforeGet)
   {
      uint32_t gain(0);
      bool bRet = camHandle_->GetParameter(TSI_PARAM_GAIN, sizeof(uint32_t), &gain);
      if (!bRet)
         return camHandle_->GetErrorCode();
      pProp->Set((long)gain);
   }
   return DEVICE_OK;
}

int TsiCam::OnWhiteBalance(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      if (val.compare(g_Off) == 0)
      {
         ClearWhiteBalance();
      }
      else if (val.compare(g_On) == 0)
      {
         if (!wb)
            return SetWhiteBalance();
      }
      else if (val.compare(g_Set) == 0)
      {
         return SetWhiteBalance();
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      if (wb)
         pProp->Set(g_On);
      else
         pProp->Set(g_Off);
   }
   return DEVICE_OK;
}

int TsiCam::OnTemperature(MM::PropertyBase* /*pProp*/, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

int TsiCam::OnTemperatureSetPoint(MM::PropertyBase* /*pProp*/, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

int TsiCam::OnColorEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      if (val.compare(g_Off) == 0)
      {
         color = false;
      }
      else if (val.compare(g_On) == 0)
      {
         if (!color)
         {
            // switch binning to 1
            int ret = SetProperty(MM::g_Keyword_Binning, "1");
            if (ret != DEVICE_OK)
               return ret;
         }
         color = true;
      }
      return ResizeImageBuffer();
   }
   else if (eAct == MM::BeforeGet)
   {
      if (color)
         pProp->Set(g_On);
      else
         pProp->Set(g_Off);
   }
   return DEVICE_OK;
}