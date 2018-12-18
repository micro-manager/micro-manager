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
      if (tl_camera_set_binx(camHandle, bin))
      {
         ResetImageBuffer();
         return ERR_ROI_BIN_FAILED;
      }

      if (tl_camera_set_biny(camHandle, bin))
      {
         ResetImageBuffer();
         return ERR_ROI_BIN_FAILED;
      }
      return ResizeImageBuffer();
   }
   else if (eAct == MM::BeforeGet)
   {
      int bin(1);
      tl_camera_get_binx(camHandle, &bin); // vbin is the same
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
      TL_CAMERA_OPERATION_MODE ttype;
      if (tl_camera_get_operation_mode(camHandle, &ttype))
         ERR_TRIGGER_FAILED;

      string val;
      pProp->Get(val);
      if (val == string(g_Software))
      {
         if (tl_camera_set_operation_mode(camHandle, TL_CAMERA_OPERATION_MODE_SOFTWARE_TRIGGERED))
            return ERR_TRIGGER_FAILED;
      }
      else if (val == string(g_HardwareEdge))
      {
         if (tl_camera_set_operation_mode(camHandle, TL_CAMERA_OPERATION_MODE_HARDWARE_TRIGGERED))
            return ERR_TRIGGER_FAILED;
      }
      else if (val == string(g_HardwareDuration))
      {
         if (tl_camera_set_operation_mode(camHandle, TL_CAMERA_OPERATION_MODE_BULB))
            return ERR_TRIGGER_FAILED;
      }
      else {
         return ERR_INTERNAL_ERROR;
      }

      operationMode = ttype;
   }
   else if (eAct == MM::BeforeGet)
   {
      TL_CAMERA_OPERATION_MODE ttype;
      if (tl_camera_get_operation_mode(camHandle, &ttype))
         ERR_TRIGGER_FAILED;

      if (ttype == TL_CAMERA_OPERATION_MODE_SOFTWARE_TRIGGERED)
         pProp->Set(g_Software);
      else if (ttype == TL_CAMERA_OPERATION_MODE_HARDWARE_TRIGGERED)
         pProp->Set(g_HardwareEdge);
      else if (ttype == TL_CAMERA_OPERATION_MODE_BULB)
         pProp->Set(g_HardwareDuration);
      else
         return ERR_INTERNAL_ERROR;

      operationMode = ttype;
   }
   return DEVICE_OK;
}

int Tsi3Cam::OnTriggerPolarity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      TL_CAMERA_TRIGGER_POLARITY tpol;
      if (tl_camera_get_trigger_polarity(camHandle, &tpol))
         ERR_TRIGGER_FAILED;

      string val;
      pProp->Get(val);
      if (val == string(g_Positive))
      {
         if (tl_camera_set_trigger_polarity(camHandle, TL_CAMERA_TRIGGER_POLARITY_ACTIVE_HIGH))
            return ERR_TRIGGER_FAILED;
      }
      else if (val == g_Negative)
      {
         if (tl_camera_set_trigger_polarity(camHandle, TL_CAMERA_TRIGGER_POLARITY_ACTIVE_LOW))
            return ERR_TRIGGER_FAILED;
      }
      else
      {
         return ERR_INTERNAL_ERROR;
      }
      triggerPolarity = tpol;
   }
   else if (eAct == MM::BeforeGet)
   {
      TL_CAMERA_TRIGGER_POLARITY tpol;
      if (tl_camera_get_trigger_polarity(camHandle, &tpol))
         ERR_TRIGGER_FAILED;

      if (triggerPolarity == TL_CAMERA_TRIGGER_POLARITY_ACTIVE_HIGH)
         pProp->Set(g_Positive);
      else if (triggerPolarity == TL_CAMERA_TRIGGER_POLARITY_ACTIVE_LOW)
         pProp->Set(g_Negative);
      else
         return ERR_INTERNAL_ERROR;

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

int Tsi3Cam::OnTemperature(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/)
{
   //if (eAct == MM::BeforeGet)
   //{
   //   int temp(0);
   //   if (tl_camera_get_temperature_degrees_c(camHandle, &temp))
   //      return ERR_TEMPERATURE_FAILED;
   //   pProp->Set((long)temp);
   //}
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
         ret = tl_camera_set_is_eep_enabled(camHandle, 1);
      else
         ret = tl_camera_set_is_eep_enabled(camHandle, 0);
      if (ret != 0)
         return ERR_EEP_FAILED;
   }
   else if (eAct == MM::BeforeGet)
   {
      TL_CAMERA_EEP_STATUS eepStat;
      if(tl_camera_get_eep_status(camHandle, &eepStat))
         return ERR_EEP_FAILED;
      if (eepStat == TL_CAMERA_EEP_STATUS_DISABLED)
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
         ret = tl_camera_set_is_hot_pixel_correction_enabled(camHandle, 1);
      else
         ret = tl_camera_set_is_hot_pixel_correction_enabled(camHandle, 0);
      if (ret != 0)
         return ERR_HOT_PIXEL_FAILED;
   }
   else if (eAct == MM::BeforeGet)
   {
      int val(0);
      if(tl_camera_get_is_hot_pixel_correction_enabled(camHandle, &val))
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