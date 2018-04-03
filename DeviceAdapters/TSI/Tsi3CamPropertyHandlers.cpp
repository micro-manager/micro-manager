///////////////////////////////////////////////////////////////////////////////
// FILE:          TSI3CamPropertyHandlers.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs Scientific Imaging compatible camera adapter
//                Handlers for get/set property events
//                
// AUTHOR:        Nenad Amodaj, 2017
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

#include "Tsi3Cam.h"

using namespace std;

int Tsi3Cam::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long bin = 1;
      pProp->Get(bin);
      if (tl_camera_set_hbin(camHandle, bin))
      {
         ResetImageBuffer();
         return ERR_ROI_BIN_FAILED;
      }

      if (tl_camera_set_vbin(camHandle, bin))
      {
         ResetImageBuffer();
         return ERR_ROI_BIN_FAILED;
      }
      return ResizeImageBuffer();
   }
   else if (eAct == MM::BeforeGet)
   {
      int bin(1);
      tl_camera_get_hbin(camHandle, &bin); // vbin is the same
      pProp->Set((long)bin);
   }
   return DEVICE_OK;
}

int Tsi3Cam::OnReadoutRate(MM::PropertyBase* /*pProp*/, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

int Tsi3Cam::OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      TRIGGER_TYPE ttype;
      TRIGGER_POLARITY tpol;
      if (tl_camera_get_hardware_trigger_mode(camHandle, &ttype, &tpol))
         ERR_TRIGGER_FAILED;

      string val;
      pProp->Get(val);
      if (val == string(g_Software))
      {
         if (tl_camera_set_hardware_trigger_mode(camHandle, NONE, tpol))
            return ERR_TRIGGER_FAILED;
      }
      else if (val == string(g_HardwareEdge))
      {
         if (tl_camera_set_hardware_trigger_mode(camHandle, STANDARD, tpol))
            return ERR_TRIGGER_FAILED;
      }
      else if (val == string(g_HardwareDuration))
      {
         if (tl_camera_set_hardware_trigger_mode(camHandle, BULB, tpol))
            return ERR_TRIGGER_FAILED;
      }
      else {
         return ERR_INTERNAL_ERROR;
      }

      trigger = ttype;
      triggerPolarity = tpol;
   }
   else if (eAct == MM::BeforeGet)
   {
      TRIGGER_TYPE ttype;
      TRIGGER_POLARITY tpol;
      if (tl_camera_get_hardware_trigger_mode(camHandle, &ttype, &tpol))
         ERR_TRIGGER_FAILED;

      if (ttype == NONE)
         pProp->Set(g_Software);
      else if (ttype == STANDARD)
         pProp->Set(g_HardwareEdge);
      else if (ttype == BULB)
         pProp->Set(g_HardwareDuration);
      else
         return ERR_INTERNAL_ERROR;

      trigger = ttype;
      triggerPolarity = tpol;

   }
   return DEVICE_OK;
}

int Tsi3Cam::OnTriggerPolarity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      TRIGGER_TYPE ttype;
      TRIGGER_POLARITY tpol;
      if (tl_camera_get_hardware_trigger_mode(camHandle, &ttype, &tpol))
         ERR_TRIGGER_FAILED;

      string val;
      pProp->Get(val);
      if (val == string(g_Positive))
      {
         if (tl_camera_set_hardware_trigger_mode(camHandle, ttype, ACTIVE_HIGH))
            return ERR_TRIGGER_FAILED;
      }
      else if (val == g_Negative)
      {
         if (tl_camera_set_hardware_trigger_mode(camHandle, ttype, ACTIVE_LOW))
            return ERR_TRIGGER_FAILED;
      }
      else
      {
         return ERR_INTERNAL_ERROR;
      }
      trigger = ttype;
      triggerPolarity = tpol;
   }
   else if (eAct == MM::BeforeGet)
   {
      TRIGGER_TYPE ttype;
      TRIGGER_POLARITY tpol;
      if (tl_camera_get_hardware_trigger_mode(camHandle, &ttype, &tpol))
         ERR_TRIGGER_FAILED;

      if (triggerPolarity == ACTIVE_HIGH)
         pProp->Set(g_Positive);
      else if (triggerPolarity == ACTIVE_LOW)
         pProp->Set(g_Negative);
      else
         return ERR_INTERNAL_ERROR;

      trigger = ttype;
      triggerPolarity = tpol;
   }
   return DEVICE_OK;
}

int Tsi3Cam::OnFps(MM::PropertyBase* /*pProp*/, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

int Tsi3Cam::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int Tsi3Cam::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int temp(0);
      if (tl_camera_get_temperature_degrees_c(camHandle, &temp))
         return ERR_TEMPERATURE_FAILED;
      pProp->Set((long)temp);
   }
   return DEVICE_OK;
}

// not implemented
int Tsi3Cam::OnTemperatureSetPoint(MM::PropertyBase* /*pProp*/, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

int Tsi3Cam::OnEEP( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      int ret = 0;
      if (val.compare(g_On) == 0)
         ret = tl_camera_set_eep_enabled(camHandle, 1);
      else
         ret = tl_camera_set_eep_enabled(camHandle, 0);
      if (ret != 0)
         return ERR_EEP_FAILED;
   }
   else if (eAct == MM::BeforeGet)
   {
      EEP_STATUS eepStat;
      if(tl_camera_get_eep_status(camHandle, &eepStat))
         return ERR_EEP_FAILED;
      if (eepStat == OFF)
         pProp->Set(g_Off);
      else
         pProp->Set(g_On);
   }
   return DEVICE_OK;
}

int Tsi3Cam::OnHotPixEnable( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if (eAct == MM::AfterSet)
   {
      string val;
      pProp->Get(val);
      int ret = 0;
      if (val.compare(g_On) == 0)
         ret = tl_camera_set_hot_pixel_correction(camHandle, 1);
      else
         ret = tl_camera_set_hot_pixel_correction(camHandle, 0);
      if (ret != 0)
         return ERR_HOT_PIXEL_FAILED;
   }
   else if (eAct == MM::BeforeGet)
   {
      int val(0);
      if(tl_camera_get_hot_pixel_correction(camHandle, &val))
         return ERR_HOT_PIXEL_FAILED;
      
      val == 0 ? pProp->Set(g_Off) : pProp->Set(g_On);
   }
   return DEVICE_OK;
}

int Tsi3Cam::OnHotPixThreshold( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if (eAct == MM::AfterSet)
   {
      long val(0);
      pProp->Get(val);
      if (tl_camera_set_hot_pixel_correction_threshold(camHandle, (int)val))
         return ERR_HOT_PIXEL_FAILED;
   }
   else if (eAct == MM::BeforeGet)
   {
      int hpt(0);
      if (tl_camera_get_hot_pixel_correction_threshold(camHandle, &hpt))
         return ERR_HOT_PIXEL_FAILED;
      pProp->Set((long)hpt);
   }
   return DEVICE_OK;
}