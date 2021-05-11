#include "MicroManager.h"
#include "PCO_err.h"
#include "PCO_errt.h"

using namespace std;

int CPCOCam::GetSignalNum(std::string szSigName)
{
  int isc = 0;
  if (szSigName.find('1') != std::string::npos)
    isc = 0;
  if (szSigName.find('2') != std::string::npos)
    isc = 1;
  if (szSigName.find('3') != std::string::npos)
    isc = 2;
  if (szSigName.find('4') != std::string::npos)
    isc = 3;
  return isc;
}

char szSelectSignalTiming[4][40] = { "Show time of 'First Line'", "Show common time of 'All Lines'", "Show time of 'Last Line'", "Show overall time of 'All Lines'" };
#define HWIOBUFLEN 150
int CPCOCam::OnSelectSignal(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (m_pCamera->m_iCamClass != 3)// pco.camera
    return DEVICE_OK;

  long isc = 0;//GetSignalNum(pProp->GetName());
  string szval;
  pProp->Get(szval);
  MM::Property* pprophelper = (MM::Property*)pProp;
  pprophelper->GetData(szval.c_str(), isc);
  isc /= 0x100;
  isc--;

  if (eAct == MM::BeforeGet)
  {
    pProp->Set(m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].strSignalName[m_pCamera->m_strCamera.strTiming.strSignal[isc].wSelected]);
  }
  else if (eAct == MM::AfterSet)
  {
    std::string szselectedsignal;

    pProp->Get(szselectedsignal);

    string szsignal;
    for (int i = 0; i < 4; i++)
    {
      szsignal = m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].strSignalName[i];
      if (szsignal == szselectedsignal)
      {
        if (m_pCamera->m_strCamera.strTiming.strSignal[isc].wSelected != (WORD)i)
        {
          int isignal = i;
          int iflag = 1 << isignal;

          if (iflag & m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalDefinitions)
          {
            char csh[200];
            int ivalue_helper = (isc + 1) * 0x100;
            sprintf_s(csh, 200, "Signal %d (%s) Timing", isc + 1, m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].strSignalName[0]);
            ClearAllowedValues(csh);

            if (m_pCamera->m_strCamera.strTiming.strSignal[isc].dwSignalFunctionality[isignal] == 0x07)
            {
              AddAllowedValue(csh, szSelectSignalTiming[0], 0 + ivalue_helper);
              AddAllowedValue(csh, szSelectSignalTiming[1], 1 + ivalue_helper);
              AddAllowedValue(csh, szSelectSignalTiming[2], 2 + ivalue_helper);
              AddAllowedValue(csh, szSelectSignalTiming[3], 3 + ivalue_helper);
            }
            else
            {
              AddAllowedValue(csh, "Not available", ivalue_helper);
            }
          }
          m_pCamera->m_strCamera.strTiming.strSignal[isc].wSelected = (WORD)i;
          SetupCamera(true, false);
        }
        break;
      }
    }
  }

  return DEVICE_OK;
}

int iBitToIndex[16] = { 0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3 };
int CPCOCam::OnSelectSignalTiming(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (m_pCamera->m_iCamClass != 3)// pco.camera
    return DEVICE_OK;

  long isc = 0;//GetSignalNum(pProp->GetName());
  string szval;
  pProp->Get(szval);
  MM::Property* pprophelper = (MM::Property*)pProp;
  if (!pprophelper->GetData(szval.c_str(), isc))
    return DEVICE_OK;

  isc /= 0x100;
  isc--;

  int iselectedsignal = m_pCamera->m_strCamera.strTiming.strSignal[isc].wSelected;
  int iselectedpar = m_pCamera->m_strCamera.strTiming.strSignal[isc].dwParameter[iselectedsignal];
  if (eAct == MM::BeforeGet)
  {


    if (m_pCamera->m_strCamera.strTiming.strSignal[isc].dwSignalFunctionality[iselectedsignal] == 0x07)
      pProp->Set(szSelectSignalTiming[iselectedpar - 1]);
    else
      pProp->Set("Not available");
  }
  else if (eAct == MM::AfterSet)
  {
    std::string szselectedsignal;

    pProp->Get(szselectedsignal);

    string szsignal;
    for (int i = 1; i <= 4; i++)
    {
      szsignal = szSelectSignalTiming[i - 1];
      if (szsignal == szselectedsignal)
      {
        if (m_pCamera->m_strCamera.strTiming.strSignal[isc].dwParameter[iselectedsignal] != (DWORD)i)
        {
          m_pCamera->m_strCamera.strTiming.strSignal[isc].dwParameter[iselectedsignal] = (DWORD)i;
          SetupCamera(true, false);
        }
        break;
      }
    }
  }

  return DEVICE_OK;
}

int CPCOCam::OnSelectSignalOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (m_pCamera->m_iCamClass != 3)// pco.camera
    return DEVICE_OK;

  long isc = 0;//GetSignalNum(pProp->GetName());
  string szval;
  pProp->Get(szval);
  MM::Property* pprophelper = (MM::Property*)pProp;
  pprophelper->GetData(szval.c_str(), isc);
  isc /= 0x100;
  isc--;

  if (eAct == MM::BeforeGet)
  {
    if (m_pCamera->m_strCamera.strTiming.strSignal[isc].wEnabled)
      pProp->Set("on");
    else
      pProp->Set("off");
  }
  else if (eAct == MM::AfterSet)
  {
    std::string szselectedsignal;
    int inewval = 0;

    pProp->Get(szselectedsignal);

    if (szselectedsignal == "on")
      inewval = 1;
    else
      inewval = 0;
    if (m_pCamera->m_strCamera.strTiming.strSignal[isc].wEnabled != (WORD)inewval)
    {
      m_pCamera->m_strCamera.strTiming.strSignal[isc].wEnabled = (WORD)inewval;
      SetupCamera(true, false);
    }
  }

  return DEVICE_OK;
}

char szSelectSignalType[5][40] = { "TTL", "High Level", "Contact", "RS-485", "TTL/GND" };
int CPCOCam::OnSelectSignalType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (m_pCamera->m_iCamClass != 3)// pco.camera
    return DEVICE_OK;

  long isc = 0;//GetSignalNum(pProp->GetName());
  string szval;
  pProp->Get(szval);
  MM::Property* pprophelper = (MM::Property*)pProp;
  pprophelper->GetData(szval.c_str(), isc);
  isc /= 0x100;
  isc--;

  if (eAct == MM::BeforeGet)
  {
    int iselectedpar = iBitToIndex[m_pCamera->m_strCamera.strTiming.strSignal[isc].wType];
    pProp->Set(szSelectSignalType[iselectedpar]);
  }
  else if (eAct == MM::AfterSet)
  {
    std::string szselectedsignal;

    pProp->Get(szselectedsignal);

    string szsignal;
    int inewval = 1;
    for (int i = 0; i < 5; i++)
    {
      szsignal = szSelectSignalType[i];
      if (szsignal == szselectedsignal)
      {
        if (m_pCamera->m_strCamera.strTiming.strSignal[isc].wType != (WORD)inewval)
        {
          m_pCamera->m_strCamera.strTiming.strSignal[isc].wType = (WORD)inewval;
          SetupCamera(true, false);
        }
        break;
      }
      inewval *= 2;
    }
  }

  return DEVICE_OK;
}

char szSelectSignalFilter[3][40] = { "off", "medium", "high" };
int CPCOCam::OnSelectSignalFilter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (m_pCamera->m_iCamClass != 3)// pco.camera
    return DEVICE_OK;

  long isc = 0;//GetSignalNum(pProp->GetName());
  string szval;
  pProp->Get(szval);
  MM::Property* pprophelper = (MM::Property*)pProp;
  pprophelper->GetData(szval.c_str(), isc);
  isc /= 0x100;
  isc--;

  if (eAct == MM::BeforeGet)
  {
    int iselectedpar = iBitToIndex[m_pCamera->m_strCamera.strTiming.strSignal[isc].wFilterSetting];
    pProp->Set(szSelectSignalFilter[iselectedpar]);
  }
  else if (eAct == MM::AfterSet)
  {
    std::string szselectedsignal;

    pProp->Get(szselectedsignal);

    string szsignal;
    int inewval = 1;
    for (int i = 0; i < 3; i++)
    {
      szsignal = szSelectSignalFilter[i];
      if (szsignal == szselectedsignal)
      {
        if (m_pCamera->m_strCamera.strTiming.strSignal[isc].wFilterSetting != (WORD)inewval)
        {
          m_pCamera->m_strCamera.strTiming.strSignal[isc].wFilterSetting = (WORD)inewval;
          SetupCamera(true, false);
        }
        break;
      }
      inewval *= 2;
    }
  }

  return DEVICE_OK;
}

char szSelectSignalPolarity[4][40] = { "high", "low", "rising", "falling" };
int CPCOCam::OnSelectSignalPolarity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (m_pCamera->m_iCamClass != 3)// pco.camera
    return DEVICE_OK;

  long isc = 0;//GetSignalNum(pProp->GetName());
  string szval;
  pProp->Get(szval);
  MM::Property* pprophelper = (MM::Property*)pProp;
  pprophelper->GetData(szval.c_str(), isc);
  isc /= 0x100;
  isc--;

  if (eAct == MM::BeforeGet)
  {
    int iselectedpar = iBitToIndex[m_pCamera->m_strCamera.strTiming.strSignal[isc].wPolarity];
    pProp->Set(szSelectSignalPolarity[iselectedpar]);
  }
  else if (eAct == MM::AfterSet)
  {
    std::string szselectedsignal;

    pProp->Get(szselectedsignal);

    string szsignal;
    int inewval = 1;
    for (int i = 0; i < 4; i++)
    {
      szsignal = szSelectSignalPolarity[i];
      if (szsignal == szselectedsignal)
      {
        if (m_pCamera->m_strCamera.strTiming.strSignal[isc].wPolarity != (WORD)inewval)
        {
          m_pCamera->m_strCamera.strTiming.strSignal[isc].wPolarity = (WORD)inewval;
          SetupCamera(true, false);
        }
        break;
      }
      inewval *= 2;
    }
  }

  return DEVICE_OK;
}


int CPCOCam::InitHWIO()
{
  CPropertyAction* pAct;
  int nRet;
  if (m_pCamera->m_strCamera.strSensor.strDescription.dwGeneralCapsDESC1 & GENERALCAPS1_HW_IO_SIGNAL_DESCRIPTOR)
  {
    for (int isc = 0; isc < m_pCamera->m_strCamera.strSensor.strSignalDesc.wNumOfSignals; isc++)
    {
      char csh[HWIOBUFLEN], csh2[HWIOBUFLEN];
      int ivalue_helper;

      string szSignalName, szhelp;

      ivalue_helper = (isc + 1) * 0x100;
      sprintf_s(csh, HWIOBUFLEN, "Signal %d (%s)", isc + 1, m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].strSignalName[0]);
      szSignalName = csh;

      pAct = new CPropertyAction(this, &CPCOCam::OnSelectSignalOnOff);
      sprintf_s(csh, HWIOBUFLEN, "%s Status", szSignalName.c_str());

      if (m_pCamera->m_strCamera.strTiming.strSignal[isc].wEnabled == 0)
        szhelp = "off";
      else
        szhelp = "on";

      nRet = CreateProperty(csh, szhelp.c_str(), MM::String, false, pAct);
      if (nRet != DEVICE_OK)
        return nRet;
      nRet = AddAllowedValue(csh, "off", 0 + ivalue_helper);
      if (nRet != DEVICE_OK)
        return nRet;
      nRet = AddAllowedValue(csh, "on", 1 + ivalue_helper);
      if (nRet != DEVICE_OK)
        return nRet;

      if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].strSignalName[1][0] != 0) // More than one signal name
      {
        int i;
        pAct = new CPropertyAction(this, &CPCOCam::OnSelectSignal);
        sprintf_s(csh, HWIOBUFLEN, "%s Selection", szSignalName.c_str());
        sprintf_s(csh2, HWIOBUFLEN, "%s", m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].strSignalName[m_pCamera->m_strCamera.strTiming.strSignal[isc].wSelected]);
        nRet = CreateProperty(csh, csh2, MM::String, false, pAct);
        if (nRet != DEVICE_OK)
          return nRet;

        for (i = 0; i < 4; i++)
        {
          if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].strSignalName[i][0] != 0)
          {
            sprintf_s(csh2, HWIOBUFLEN, "%s", m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].strSignalName[i]);

            nRet = AddAllowedValue(csh, csh2, i + ivalue_helper);
            if (nRet != DEVICE_OK)
              return nRet;
          }
          else
            break;
        }
        if (m_pCamera->m_strCamera.strTiming.strSignal[isc].wSelected >= i)
          m_pCamera->m_strCamera.strTiming.strSignal[isc].wSelected = 0;
      }
      else
        m_pCamera->m_strCamera.strTiming.strSignal[isc].wSelected = 0;

      int isignal = m_pCamera->m_strCamera.strTiming.strSignal[isc].wSelected;
      int iflag = 1 << isignal;
      if (iflag & m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalDefinitions)
      {
        if ((m_pCamera->m_strCamera.strTiming.strSignal[isc].dwSignalFunctionality[0] == 0x07) ||
          (m_pCamera->m_strCamera.strTiming.strSignal[isc].dwSignalFunctionality[1] == 0x07) ||
          (m_pCamera->m_strCamera.strTiming.strSignal[isc].dwSignalFunctionality[2] == 0x07) ||
          (m_pCamera->m_strCamera.strTiming.strSignal[isc].dwSignalFunctionality[3] == 0x07))
        {
          int isignaltextindex = m_pCamera->m_strCamera.strTiming.strSignal[isc].dwParameter[isignal] - 1;
          sprintf_s(csh, HWIOBUFLEN, "%s Timing", szSignalName.c_str());

          pAct = new CPropertyAction(this, &CPCOCam::OnSelectSignalTiming);
          if ((isignaltextindex >= 0) && (isignaltextindex <= 3))
            nRet = CreateProperty(csh, szSelectSignalTiming[isignaltextindex], MM::String, false, pAct);
          else
            nRet = CreateProperty(csh, "Not available", MM::String, false, pAct);
          if (nRet != DEVICE_OK)
            return nRet;

          if (m_pCamera->m_strCamera.strTiming.strSignal[isc].dwSignalFunctionality[isignal] == 0x07)
          {
            // We have got timing output settings

            nRet = AddAllowedValue(csh, szSelectSignalTiming[0], 0 + ivalue_helper);
            if (nRet != DEVICE_OK)
              return nRet;
            nRet = AddAllowedValue(csh, szSelectSignalTiming[1], 1 + ivalue_helper);
            if (nRet != DEVICE_OK)
              return nRet;
            nRet = AddAllowedValue(csh, szSelectSignalTiming[2], 2 + ivalue_helper);
            if (nRet != DEVICE_OK)
              return nRet;
            nRet = AddAllowedValue(csh, szSelectSignalTiming[3], 3 + ivalue_helper);
            if (nRet != DEVICE_OK)
              return nRet;
          }
          else
          {
            nRet = AddAllowedValue(csh, "Not available", ivalue_helper);
            if (nRet != DEVICE_OK)
              return nRet;
          }
        }
      }

      int count = 0;
      WORD wsignal = m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalTypes;
      WORD wh = wsignal;
      wh = wh - ((wh >> 1) & 0x5555);    // 0101 0101 0101 0101 
      wh = (wh & 0x3333) + ((wh >> 2) & 0x3333);// 0011 0011 0011 0011
      wh = (wh + (wh >> 4)) & 0x0F0F;  // 0000 1111 0000 1111
      count = (wh + (wh >> 8)) & 0xFF;

      // If wh has got more than 1 bit set, we can choose. Also the current signal type bit must be in descriptors signal types.
      if ((count > 1) && (m_pCamera->m_strCamera.strTiming.strSignal[isc].wType & wsignal))
      {
        pAct = new CPropertyAction(this, &CPCOCam::OnSelectSignalType);
        sprintf_s(csh, HWIOBUFLEN, "%s Type", szSignalName.c_str());

        nRet = CreateProperty(csh, szSelectSignalType[iBitToIndex[m_pCamera->m_strCamera.strTiming.strSignal[isc].wType]], MM::String, false, pAct);
        if (nRet != DEVICE_OK)
          return nRet;
        if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalTypes & SIGNAL_TYPE_TTL)
        {
          nRet = AddAllowedValue(csh, szSelectSignalType[0], 0 + ivalue_helper);
          if (nRet != DEVICE_OK)
            return nRet;
        }
        if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalTypes & SIGNAL_TYPE_HL_SIG)
        {
          nRet = AddAllowedValue(csh, szSelectSignalType[1], 1 + ivalue_helper);
          if (nRet != DEVICE_OK)
            return nRet;
        }
        if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalTypes & SIGNAL_TYPE_CONTACT)
        {
          nRet = AddAllowedValue(csh, szSelectSignalType[2], 2 + ivalue_helper);
          if (nRet != DEVICE_OK)
            return nRet;
        }
        if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalTypes & SIGNAL_TYPE_RS485)
        {
          nRet = AddAllowedValue(csh, szSelectSignalType[3], 3 + ivalue_helper);
          if (nRet != DEVICE_OK)
            return nRet;
        }
        if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalTypes & SIGNAL_TYPE_TTL_A_GND_B)
        {
          nRet = AddAllowedValue(csh, szSelectSignalType[4], 4 + ivalue_helper);
          if (nRet != DEVICE_OK)
            return nRet;
        }
      }
      WORD wfilter = m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalFilter;
      wh = wfilter;
      wh = wh - ((wh >> 1) & 0x5555);    // 0101 0101 0101 0101 
      wh = (wh & 0x3333) + ((wh >> 2) & 0x3333);// 0011 0011 0011 0011
      wh = (wh + (wh >> 4)) & 0x0F0F;  // 0000 1111 0000 1111
      count = (wh + (wh >> 8)) & 0xFF;

      // If wh has got more than 1 bit set, we can choose. Also the current signal filter bit must be in descriptors signal filters.
      if ((count > 1) && (m_pCamera->m_strCamera.strTiming.strSignal[isc].wFilterSetting & wfilter))
      {
        pAct = new CPropertyAction(this, &CPCOCam::OnSelectSignalFilter);
        sprintf_s(csh, HWIOBUFLEN, "%s Filter", szSignalName.c_str());

        nRet = CreateProperty(csh, szSelectSignalFilter[iBitToIndex[m_pCamera->m_strCamera.strTiming.strSignal[isc].wFilterSetting]], MM::String, false, pAct);
        if (nRet != DEVICE_OK)
          return nRet;
        if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalFilter & SIGNAL_FILTER_OFF)
        {
          nRet = AddAllowedValue(csh, szSelectSignalFilter[0], 0 + ivalue_helper);
          if (nRet != DEVICE_OK)
            return nRet;
        }
        if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalFilter & SIGNAL_FILTER_MED)
        {
          nRet = AddAllowedValue(csh, szSelectSignalFilter[1], 1 + ivalue_helper);
          if (nRet != DEVICE_OK)
            return nRet;
        }
        if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalFilter & SIGNAL_FILTER_HIGH)
        {
          nRet = AddAllowedValue(csh, szSelectSignalFilter[2], 2 + ivalue_helper);
          if (nRet != DEVICE_OK)
            return nRet;
        }
      }

      WORD wpolarity = m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalPolarity;
      wh = wpolarity;
      wh = wh - ((wh >> 1) & 0x5555);    // 0101 0101 0101 0101 
      wh = (wh & 0x3333) + ((wh >> 2) & 0x3333);// 0011 0011 0011 0011
      wh = (wh + (wh >> 4)) & 0x0F0F;  // 0000 1111 0000 1111
      count = (wh + (wh >> 8)) & 0xFF;

      // If wh has got more than 1 bit set, we can choose. Also the current signal polarity bit must be in descriptors signal polarities.
      if ((count > 1) && (m_pCamera->m_strCamera.strTiming.strSignal[isc].wPolarity & wpolarity))
      {
        pAct = new CPropertyAction(this, &CPCOCam::OnSelectSignalPolarity);
        sprintf_s(csh, HWIOBUFLEN, "%s Polarity", szSignalName.c_str());

        nRet = CreateProperty(csh, szSelectSignalPolarity[iBitToIndex[m_pCamera->m_strCamera.strTiming.strSignal[isc].wPolarity]], MM::String, false, pAct);
        if (nRet != DEVICE_OK)
          return nRet;
        if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalPolarity & SIGNAL_POL_HIGH)
        {
          nRet = AddAllowedValue(csh, szSelectSignalPolarity[0], 0 + ivalue_helper);
          if (nRet != DEVICE_OK)
            return nRet;
        }
        if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalPolarity & SIGNAL_POL_LOW)
        {
          nRet = AddAllowedValue(csh, szSelectSignalPolarity[1], 1 + ivalue_helper);
          if (nRet != DEVICE_OK)
            return nRet;
        }
        if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalPolarity & SIGNAL_POL_RISE)
        {
          nRet = AddAllowedValue(csh, szSelectSignalPolarity[2], 2 + ivalue_helper);
          if (nRet != DEVICE_OK)
            return nRet;
        }
        if (m_pCamera->m_strCamera.strSensor.strSignalDesc.strSingeSignalDesc[isc].wSignalPolarity & SIGNAL_POL_FALL)
        {
          nRet = AddAllowedValue(csh, szSelectSignalPolarity[3], 3 + ivalue_helper);
          if (nRet != DEVICE_OK)
            return nRet;
        }
      }
    }
  }

  return DEVICE_OK;
}



int CPCOCam::OnCmosParameter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    if (m_wCMOSParameter == 0)
      pProp->Set("Off");
    else
      pProp->Set("On");
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    string tmp;
    long lparameter;
    pProp->Get(tmp);
    ((MM::Property*)pProp)->GetData(tmp.c_str(), lparameter);

    if (lparameter != m_wCMOSParameter)
    {
      if (m_iPixelRate == 0)
        m_wCMOSParameter = (WORD)lparameter;
      if ((m_iPixelRate == 1) && (m_pCamera->m_strCamera.strTiming.dwCMOSFlags & 0x02))
        m_wCMOSParameter = (WORD)lparameter;
      m_pCamera->m_strCamera.strTiming.wCMOSParameter = m_wCMOSParameter;
      nErr = SetupCamera(true, false);
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnCmosTimeBase(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    if (m_wCMOSTimeBase == 0)
      pProp->Set("ns");
    else
      if (m_wCMOSTimeBase == 1)
        pProp->Set("us");
      else
        pProp->Set("ms");
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    string tmp;
    long lparameter;
    pProp->Get(tmp);
    ((MM::Property*)pProp)->GetData(tmp.c_str(), lparameter);

    if (lparameter != m_wCMOSTimeBase)
    {
      if (lparameter == 0)
      {
        if (m_wCMOSTimeBase >= 1)
          m_dwCMOSLineTime *= 1000;
        if (m_wCMOSTimeBase == 2)
          m_dwCMOSLineTime *= 1000;
      }
      if (lparameter == 1)
      {
        if (m_wCMOSTimeBase == 0)
          m_dwCMOSLineTime /= 1000;
        if (m_wCMOSTimeBase == 2)
          m_dwCMOSLineTime *= 1000;
      }
      if (lparameter == 2)
      {
        if (m_wCMOSTimeBase <= 1)
          m_dwCMOSLineTime /= 1000;
        if (m_wCMOSTimeBase == 0)
          m_dwCMOSLineTime /= 1000;
      }
      m_wCMOSTimeBase = (WORD)lparameter;
      m_pCamera->m_strCamera.strTiming.wCMOSTimeBase = m_wCMOSTimeBase;

      CheckLineTime(&m_dwCMOSLineTime);
      m_pCamera->m_strCamera.strTiming.dwCMOSLineTime = m_dwCMOSLineTime;
      nErr = SetupCamera(true, false);
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::CheckLineTime(DWORD* ptime)
{
  double dmintime = m_dwCMOSLineTimeMin;
  double dmaxtime = m_dwCMOSLineTimeMax;
  double dtime = *ptime;
  bool bchanged = false;
  if (m_pCamera->m_strCamera.strTiming.wCMOSTimeBase == 0)
  {
    dmaxtime *= 1000000.0;
  }
  if (m_pCamera->m_strCamera.strTiming.wCMOSTimeBase == 1)
  {
    dmaxtime *= 1000.0;
    dmintime /= 1000.0;
  }
  if (m_pCamera->m_strCamera.strTiming.wCMOSTimeBase == 2)
  {
    dmintime /= 1000000.0;
  }
  if (dtime > dmaxtime)
  {
    dtime = dmaxtime;
    bchanged = true;
  }
  if (dtime < dmintime)
  {
    dtime = dmintime;
    bchanged = true;
  }
  if (dtime < 1.0)
    dtime = 1.0;

  *ptime = (DWORD)dtime;
  if (bchanged)
    return DEVICE_ERR;
  return DEVICE_OK;
}

int CPCOCam::OnCmosLineTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set((long)m_dwCMOSLineTime);
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    long lhelp;

    ((MM::IntegerProperty*)pProp)->Get(lhelp);
    if (lhelp != (long)m_dwCMOSLineTime)
    {
      m_dwCMOSLineTime = (DWORD)lhelp;
      CheckLineTime(&m_dwCMOSLineTime);
      m_pCamera->m_strCamera.strTiming.dwCMOSLineTime = m_dwCMOSLineTime;
      nErr = SetupCamera(true, false);
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnCmosExposureLines(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set((long)m_dwCMOSExposureLines);
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    long lhelp;

    ((MM::IntegerProperty*)pProp)->Get(lhelp);
    if (lhelp != (long)m_dwCMOSExposureLines)
    {
      if (lhelp < 1)
        lhelp = 1;
      if (lhelp > 0x7FFFFFFF)
        lhelp = (long)0x7FFFFFFF;
      m_dwCMOSExposureLines = lhelp;
      m_pCamera->m_strCamera.strTiming.dwCMOSExposureLines = m_dwCMOSExposureLines;
      nErr = SetupCamera(false, false);
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnCmosDelayLines(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set((long)m_dwCMOSDelayLines);
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    long lhelp;

    ((MM::IntegerProperty*)pProp)->Get(lhelp);
    if (lhelp != (long)m_dwCMOSDelayLines)
    {
      if (lhelp < 0)
        lhelp = 0;
      if (lhelp > 0x7FFFFFFF)
        lhelp = (long)0x7FFFFFFF;
      m_dwCMOSDelayLines = lhelp;
      m_pCamera->m_strCamera.strTiming.dwCMOSDelayLines = m_dwCMOSDelayLines;
      nErr = SetupCamera(false, false);
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::InitLineTiming()
{
  CPropertyAction* pAct;
  int nRet;
  string szhelp;
  string csh;
  char szh[100];

  if ((m_pCamera->m_strCamera.strAPIManager.wAPIManagementFlags & APIMANAGEMENTFLAG_LINE_TIMING) == APIMANAGEMENTFLAG_LINE_TIMING)
  {
    pAct = new CPropertyAction(this, &CPCOCam::OnCmosParameter);

    if (m_pCamera->m_strCamera.strTiming.wCMOSParameter == 0)
      szhelp = "Off";
    else
      szhelp = "On";
    csh = "Light Sheet Mode";
    nRet = CreateProperty(csh.c_str(), szhelp.c_str(), MM::String, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = AddAllowedValue(csh.c_str(), "Off", 0);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = AddAllowedValue(csh.c_str(), "On", 1);
    if (nRet != DEVICE_OK)
      return nRet;

    pAct = new CPropertyAction(this, &CPCOCam::OnCmosTimeBase);

    if (m_pCamera->m_strCamera.strTiming.wCMOSTimeBase == 0)
      szhelp = "ns";
    else
      if (m_pCamera->m_strCamera.strTiming.wCMOSTimeBase == 1)
        szhelp = "us";
      else
        szhelp = "ms";
    csh = "Light Sheet Mode Timebase";
    nRet = CreateProperty(csh.c_str(), szhelp.c_str(), MM::String, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = AddAllowedValue(csh.c_str(), "ns", 0);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = AddAllowedValue(csh.c_str(), "us", 1);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = AddAllowedValue(csh.c_str(), "ms", 2);
    if (nRet != DEVICE_OK)
      return nRet;


    pAct = new CPropertyAction(this, &CPCOCam::OnCmosLineTime);
    csh = "Light Sheet Mode Line Time";
    sprintf_s(szh, 100, "%d", m_dwCMOSLineTime);
    nRet = CreateProperty(csh.c_str(), szh, MM::Integer, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;

    pAct = new CPropertyAction(this, &CPCOCam::OnCmosDelayLines);
    csh = "Light Sheet Mode Delay Lines";
    sprintf_s(szh, 100, "%d", m_dwCMOSDelayLines);
    nRet = CreateProperty(csh.c_str(), szh, MM::Integer, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;

    pAct = new CPropertyAction(this, &CPCOCam::OnCmosExposureLines);
    csh = "Light Sheet Mode Exposure Lines";
    sprintf_s(szh, 100, "%d", m_dwCMOSExposureLines);
    nRet = CreateProperty(csh.c_str(), szh, MM::Integer, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;
  }

  return DEVICE_OK;
}