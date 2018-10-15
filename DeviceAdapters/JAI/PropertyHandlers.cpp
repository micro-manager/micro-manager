///////////////////////////////////////////////////////////////////////////////
// FILE:          TSICam.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs Scientific Imaging compatible camera adapter
//                Handlers for get/set property events
//                
// AUTHOR:        Nenad Amodaj, 2018
// COPYRIGHT:     JAI
//
// DISCLAIMER:    This file is provided WITHOUT ANY WARRANTY;
//                without even the implied warranty of MERCHANTABILITY or
//                FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#include "JAI.h"
#include <PvInterface.h>
#include <PvDevice.h>
#include <PvSystem.h>
#include <PvAcquisitionStateManager.h>
#include <PvPipeline.h>

using namespace std;

int JAICamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long bin = 1;
      pProp->Get(bin);
      PvResult pvr = genParams->SetIntegerValue(g_pv_BinH, (int64_t)bin);
      if (pvr.IsFailure())
         return processPvError(pvr);
      pvr = genParams->SetIntegerValue(g_pv_BinV, (int64_t)bin);
      if (pvr.IsFailure())
         return processPvError(pvr);
      return ResizeImageBuffer();
   }
   else if (eAct == MM::BeforeGet)
   {
      int64_t hbin, vbin;
      PvResult pvr = genParams->GetIntegerValue(g_pv_BinH, hbin);
      if (pvr.IsFailure())
         return processPvError(pvr);
      pvr = genParams->GetIntegerValue(g_pv_BinV, vbin);
      assert(hbin == vbin);
      if (pvr.IsFailure())
         return processPvError(pvr);
      pProp->Set((long)hbin);
   }
   return DEVICE_OK;
}

int JAICamera::OnReadoutRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long rateIdx(0);
      int ret = GetCurrentPropertyData(g_ReadoutRate, rateIdx);
      if (ret != DEVICE_OK)
         return ret;
   }
   else if (eAct == MM::BeforeGet)
   {
      uint32_t idx;
     // pProp->Set(val);
   }
   return DEVICE_OK;
}

int JAICamera::OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
	}
	else if (eAct == MM::BeforeGet)
	{
	}
	return DEVICE_OK;
}

int JAICamera::OnTriggerPolarity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
	}
	else if (eAct == MM::BeforeGet)
	{
	}
	return DEVICE_OK;
}

int JAICamera::OnFps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		double fps;
		PvResult pvr = genParams->GetFloatValue("AcquisitionFrameRate", fps);
		if (!pvr.IsOK())
			return processPvError(pvr);
		pProp->Set(fps);
	}
	return DEVICE_OK;
}

int JAICamera::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		double val(0.0);
		pProp->Get(val);
		SetExposure(val);
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(GetExposure());
	}
	return DEVICE_OK;
}

int JAICamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		double val(1.0);
		pProp->Get(val);
		PvResult pvr = genParams->SetFloatValue("Gain", val);
		if (!pvr.IsOK())
			return processPvError(pvr);
	}
	else if (eAct == MM::BeforeGet)
	{
		double gain;
		PvResult pvr = genParams->GetFloatValue("Gain", gain);
		if (!pvr.IsOK())
			return processPvError(pvr);
		pProp->Set(gain);
	}
	return DEVICE_OK;
}

int JAICamera::OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		double val(1.0);
		pProp->Get(val);
		PvResult pvr = genParams->SetFloatValue("Gamma", val);
		if (!pvr.IsOK())
			return processPvError(pvr);
	}
	else if (eAct == MM::BeforeGet)
	{
		double gamma;
		PvResult pvr = genParams->GetFloatValue("Gamma", gamma);
		if (!pvr.IsOK())
			return processPvError(pvr);
		pProp->Set(gamma);
	}
	return DEVICE_OK;
}

int JAICamera::OnWhiteBalance(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	const char* pvCmd = "BalanceWhiteAuto";
	if (eAct == MM::AfterSet)
	{
		string val;
		long data;
		pProp->Get(val);
		GetPropertyData(g_WhiteBalance, val.c_str(), data);
		PvResult pvr = genParams->SetEnumValue(pvCmd, data);
		if (!pvr.IsOK())
			return processPvError(pvr);
	}
	else if (eAct == MM::BeforeGet)
	{
		PvString val;
		PvResult pvr = genParams->GetEnumValue(pvCmd, val);
		if (!pvr.IsOK())
			return processPvError(pvr);
		pProp->Set(val.GetAscii());
	}
	return DEVICE_OK;
}

int JAICamera::OnTestPattern(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	const char* pvCmd = "TestPattern";
	if (eAct == MM::AfterSet)
	{
		string val;
		long data;
		pProp->Get(val);
		GetPropertyData(g_TestPattern, val.c_str(), data);
		PvResult pvr = genParams->SetEnumValue(pvCmd, data);
		if (!pvr.IsOK())
			return processPvError(pvr);
	}
	else if (eAct == MM::BeforeGet)
	{
		PvString val;
		PvResult pvr = genParams->GetEnumValue(pvCmd, val);
		if (!pvr.IsOK())
			return processPvError(pvr);
		pProp->Set(val.GetAscii());
	}
	return DEVICE_OK;
}


int JAICamera::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		double tempC;
		PvResult pvr = genParams->GetFloatValue("DeviceTemperature", tempC);
		if (!pvr.IsOK())
			return processPvError(pvr);
		pProp->Set(tempC);
	}
	return DEVICE_OK;
}

int JAICamera::OnTemperatureSetPoint(MM::PropertyBase* /*pProp*/, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
   }
   else if (eAct == MM::BeforeGet)
   {
   }
   return DEVICE_OK;
}

int JAICamera::OnColorEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
	}
	else if (eAct == MM::BeforeGet)
	{
	}
	return DEVICE_OK;
}