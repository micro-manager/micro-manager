#include "MicroManager.h"
#include "PCO_err.h"
#include "PCO_errt.h"

using namespace std;


int CPCOCam::InitFanLED()
{
  DWORD dwLedParam, dwflagsout = 0;
  WORD wMode, wValue, wmin, wmax;
  m_pCamera->HandleFanAndLED(1, &wMode, &wValue, &wmin, &wmax, &dwLedParam, &dwflagsout);
  if ((dwflagsout & FAN_IS_POSSIBLE) != FAN_IS_POSSIBLE)
    return 0;

  CPropertyAction* pAct;
  int nRet;
  string szhelp;
  string csh;
  char szh[100];

  m_wFanControl = wMode;
  m_wFanSpeed = wValue;
  m_dwLEDControl = dwLedParam;

  pAct = new CPropertyAction(this, &CPCOCam::OnFanControl);

  if (wMode == FAN_CONTROL_MODE_AUTO)
    szhelp = "On";
  else
    szhelp = "Off";
  csh = "Fan/LED: Automatic Fan Control";
  nRet = CreateProperty(csh.c_str(), szhelp.c_str(), MM::String, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "Off", 0);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "On", 1);
  if (nRet != DEVICE_OK)
    return nRet;

  pAct = new CPropertyAction(this, &CPCOCam::OnFanSpeed);

  sprintf_s(szh, 100, "%d", wValue);
  csh = "Fan/LED: Fan Speed [%]";
  nRet = CreateProperty(csh.c_str(), szhelp.c_str(), MM::Integer, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;
  SetPropertyLimits(csh.c_str(), 0.0, 100.0);

  if ((dwflagsout & LED_IS_POSSIBLE) == LED_IS_POSSIBLE)
  {

    pAct = new CPropertyAction(this, &CPCOCam::OnLEDControl);

    if (dwLedParam == HW_LED_SIGNAL_ON)
      szhelp = "On";
    else
      szhelp = "Off";
    csh = "Fan/LED: Camera LED";
    nRet = CreateProperty(csh.c_str(), szhelp.c_str(), MM::String, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = AddAllowedValue(csh.c_str(), "Off", 0);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = AddAllowedValue(csh.c_str(), "On", 1);
    if (nRet != DEVICE_OK)
      return nRet;
  }
  return DEVICE_OK;
}

int CPCOCam::SetupFanLED()
{
  DWORD dwLedParam, dwflagsout = 0;
  WORD wMode, wValue, wmin, wmax;
  wMode = m_wFanControl;
  wValue = m_wFanSpeed;
  dwLedParam = m_dwLEDControl;
  return m_pCamera->HandleFanAndLED(2, &wMode, &wValue, &wmin, &wmax, &dwLedParam, &dwflagsout);
}

int CPCOCam::OnFanControl(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    if (m_wFanControl == FAN_CONTROL_MODE_AUTO)
      pProp->Set("On");
    else
      pProp->Set("Off");
  }
  else if (eAct == MM::AfterSet)
  {
    string tmp;
    long ModeTmp;
    int ihelp;
    pProp->Get(tmp);
    ((MM::Property*)pProp)->GetData(tmp.c_str(), ModeTmp);
    ihelp = (ModeTmp == 1);

    if (ihelp != 0)
      m_wFanControl = FAN_CONTROL_MODE_AUTO;
    else
      m_wFanControl = FAN_CONTROL_MODE_USER;

    int nErr = SetupFanLED();

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnFanSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set((long)m_wFanSpeed);
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    long lhelp;
    WORD whelp;
    pProp->Get(lhelp);
    whelp = (WORD)lhelp;
    if (lhelp != m_wFanSpeed)
    {
      m_wFanSpeed = whelp;
      nErr = SetupFanLED();
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnLEDControl(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    if (m_dwLEDControl == HW_LED_SIGNAL_ON)
      pProp->Set("On");
    else
      pProp->Set("Off");
  }
  else if (eAct == MM::AfterSet)
  {
    string tmp;
    long ModeTmp;
    int ihelp;
    pProp->Get(tmp);
    ((MM::Property*)pProp)->GetData(tmp.c_str(), ModeTmp);
    ihelp = (ModeTmp == 1);

    if (ihelp != 0)
      m_dwLEDControl = HW_LED_SIGNAL_ON;
    else
      m_dwLEDControl = HW_LED_SIGNAL_OFF;

    int nErr = SetupFanLED();

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}
