#include "MicroManager.h"
#include "PCO_err.h"
#include "PCO_errt.h"

using namespace std;

int CPCOCam::InitFlim()
{
  //bool bshutterchanged = false;
  //bool breadoutchanged = false;
  WORD wType = 0, wLen = 20;
  DWORD dwSetup[20] = { 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0,
    0, 0, 0, 0, 0,
    0, 0, 0, 0, 0 };
  int err = PCO_NOERROR;

  err = m_pCamera->GetCameraSetup(&wType, &dwSetup[0], &wLen);
  m_wFlimSourceSelect = (WORD)dwSetup[0]; // FLIM_MODULATION_SOURCE_EXTERN: 1; FLIM_MODULATION_SOURCE_INTERN: 0;
  m_wFlimOutputWaveform = (WORD)dwSetup[1]; // FLIM_MODULATION_OUTPUT_WAVEFORM_NONE: 0; FLIM_MODULATION_OUTPUT_WAVEFORM_SINE: 1; FLIM_MODULATION_OUTPUT_WAVEFORM_RECT: 2
  m_wFlimPhaseNumber = (WORD)dwSetup[2]; // FLIM_PHASE_MANUAL_SHIFTING: 0; FLIM_PHASE_NUMBER_2: 1; FLIM_PHASE_NUMBER_4: 2; FLIM_PHASE_NUMBER_8: 3; FLIM_PHASE_NUMBER_16: 4

  m_wFlimPhaseSymmetry = (WORD)dwSetup[3]; // FLIM_PHASE_SYMMETRY_SINGULAR: 0; FLIM_PHASE_SYMMETRY_TWICE: 1;

  m_wFlimPhaseOrder = (WORD)dwSetup[4]; // FLIM_PHASE_ORDER_ASCENDING: 0; FLIM_PHASE_ORDER_OPPOSITE:1;

  m_wFlimTapSelect = (WORD)dwSetup[5]; // FLIM_TAP_SELECT_BOTH: 0; FLIM_TAP_SELECT_0: 1; FLIM_TAP_SELECT_180: 2;

  m_wFlimAsymmetryCorrection = (WORD)dwSetup[6]; // FLIM_ASYMMETRY_CORRECTION_OFF: 0; FLIM_ASYMMETRY_CORRECTION_AVERAGE: 1;

  m_wFlimCalculationMode = (WORD)dwSetup[7];
  m_wFlimReferencingMode = (WORD)dwSetup[8];
  m_wFlimThresholdLow = (WORD)dwSetup[9];
  m_wFlimThresholdHigh = (WORD)dwSetup[10];
  m_wFlimOutputMode = (WORD)dwSetup[11];

  m_dwFlimFrequency = dwSetup[12];
  m_dwFlimPhaseMilliDeg = dwSetup[13];

  m_bFlimMasterFrequencyMHz = dwSetup[14] == 1 ? true : false;



  CPropertyAction* pAct;
  int nRet;
  string szhelp;
  string csh;
  char szh[100];

  pAct = new CPropertyAction(this, &CPCOCam::OnFlimModulationSource);

  if (m_wFlimSourceSelect == 0)
    szhelp = "(0) intern";
  else
    szhelp = "(1) extern";
  csh = "Flim (01) Modulation Source";
  nRet = CreateProperty(csh.c_str(), szhelp.c_str(), MM::String, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(0) intern", 0);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(1) extern", 1);
  if (nRet != DEVICE_OK)
    return nRet;

  pAct = new CPropertyAction(this, &CPCOCam::OnFlimFrequency);
  csh = "Flim (02) Master Frequency";
  sprintf_s(szh, 100, "%d", m_dwFlimFrequency);
  nRet = CreateProperty(csh.c_str(), szh, MM::Integer, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;


  pAct = new CPropertyAction(this, &CPCOCam::OnFlimMasterFrequencyMHz);
  CreateProperty("Flim (03) Master Frequency [MHz/kHz]", "(0) kHz", MM::String, false, pAct);
  AddAllowedValue("Flim (03) Master Frequency [MHz/kHz]", "(0) kHz", 0);
  AddAllowedValue("Flim (03) Master Frequency [MHz/kHz]", "(1) MHz", 1);

  pAct = new CPropertyAction(this, &CPCOCam::OnFlimRelativePhase);
  csh = "Flim (04) Relative Phase";
  sprintf_s(szh, 100, "%d", m_dwFlimPhaseMilliDeg);
  nRet = CreateProperty(csh.c_str(), szh, MM::Integer, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;

  pAct = new CPropertyAction(this, &CPCOCam::OnFlimOutputWaveForm);
  if (m_wFlimOutputWaveform == 0)
    szhelp = "(0) none";
  else
    if (m_wFlimOutputWaveform == 1)
      szhelp = "(1) sine wave";
    else
      szhelp = "(2) square wave";
  csh = "Flim (05) Output Waveform";
  nRet = CreateProperty(csh.c_str(), szhelp.c_str(), MM::String, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(0) none", 0);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(1) sine wave", 1);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(2) square wave", 2);

  switch (m_wFlimPhaseNumber)
  {
  case 0:
    szhelp = "(0) shiftable pair";
    break;
  case 1:
    szhelp = "(1) 2";
    break;
  case 2:
    szhelp = "(2) 4";
    break;
  case 3:
    szhelp = "(3) 8";
    break;
  case 4:
    szhelp = "(4) 16";
    break;
  }
  csh = "Flim (06) Number of Phase Samples";
  pAct = new CPropertyAction(this, &CPCOCam::OnFlimNumberOfPhaseSamples);
  nRet = CreateProperty(csh.c_str(), szhelp.c_str(), MM::String, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(0) shiftable pair", 0);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(1) 2", 1);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(2) 4", 2);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(3) 8", 3);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(4) 16", 4);
  if (nRet != DEVICE_OK)
    return nRet;

  if (m_wFlimPhaseSymmetry == 0)
    szhelp = "(0) no";
  else
    szhelp = "(1) yes";
  csh = "Flim (07) Additional Phase Sampling";
  pAct = new CPropertyAction(this, &CPCOCam::OnFlimPhaseSymmetry);
  nRet = CreateProperty(csh.c_str(), szhelp.c_str(), MM::String, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(0) no", 0);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(1) yes", 1);
  if (nRet != DEVICE_OK)
    return nRet;

  if (m_wFlimPhaseOrder == 0)
    szhelp = "(0) ascending";
  else
    szhelp = "(1) opposite";
  csh = "Flim (08) Phase Order";
  pAct = new CPropertyAction(this, &CPCOCam::OnFlimPhaseOrder);
  nRet = CreateProperty(csh.c_str(), szhelp.c_str(), MM::String, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(0) ascending", 0);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(1) opposite", 1);
  if (nRet != DEVICE_OK)
    return nRet;

  switch (m_wFlimTapSelect)
  {
  case 0:
    szhelp = "(0) Tap A + B";
    break;
  case 1:
    szhelp = "(1) Tap A";
    break;
  case 2:
    szhelp = "(2) Tap B";
    break;
  }
  csh = "Flim (09) Tap Selection";
  pAct = new CPropertyAction(this, &CPCOCam::OnFlimTapSelection);
  nRet = CreateProperty(csh.c_str(), szhelp.c_str(), MM::String, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(0) Tap A + B", 0);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(1) Tap A", 1);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(2) Tap B", 2);
  if (nRet != DEVICE_OK)
    return nRet;

  if (m_wFlimAsymmetryCorrection == 0)
    szhelp = "(0) off";
  else
    szhelp = "(1) on";
  csh = "Flim (10) Asym. Correction in Camera";
  pAct = new CPropertyAction(this, &CPCOCam::OnFlimAsymCorrection);
  nRet = CreateProperty(csh.c_str(), szhelp.c_str(), MM::String, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(0) off", 0);
  if (nRet != DEVICE_OK)
    return nRet;
  nRet = AddAllowedValue(csh.c_str(), "(1) on", 1);
  return nRet;
}

int CPCOCam::SetupFlim()
{
  //bool bshutterchanged = false;
  //bool breadoutchanged = false;
  WORD wType = 0, wLen = 20;
  DWORD dwSetup[20] = { 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0,
    0, 0, 0, 0, 0,
    0, 0, 0, 0, 0 };
  int err = PCO_NOERROR;
  DWORD dwflags = 0;

  if (m_wFlimPhaseNumber == 0)      // Must be 2,4,8 or 16 (!=0)
  {
    m_wFlimPhaseSymmetry = 0;
    m_wFlimPhaseOrder = 0;
    m_wFlimAsymmetryCorrection = 0;
  }

  if (m_wFlimPhaseSymmetry == 0)
  {
    m_wFlimAsymmetryCorrection = 0;
    m_wFlimPhaseOrder = 0;
  }

  if (m_wFlimPhaseOrder != 1)         // Must be Opposite (1)
    m_wFlimAsymmetryCorrection = 0;

  if (m_wFlimTapSelect != 0)          // Must be Tap A + B (0)
    m_wFlimAsymmetryCorrection = 0;

  err = m_pCamera->GetCameraSetup(&wType, &dwSetup[0], &wLen);
  dwSetup[0] = m_wFlimSourceSelect;
  dwSetup[1] = m_wFlimOutputWaveform;
  dwSetup[2] = m_wFlimPhaseNumber;
  dwSetup[3] = m_wFlimPhaseSymmetry;
  dwSetup[4] = m_wFlimPhaseOrder;
  dwSetup[5] = m_wFlimTapSelect;
  dwSetup[6] = m_wFlimAsymmetryCorrection;
  dwSetup[7] = m_wFlimCalculationMode;
  dwSetup[8] = m_wFlimReferencingMode;
  dwSetup[9] = m_wFlimThresholdLow;
  dwSetup[10] = m_wFlimThresholdHigh;
  dwSetup[11] = m_wFlimOutputMode;
  dwSetup[12] = m_dwFlimFrequency;
  dwSetup[13] = m_dwFlimPhaseMilliDeg;
  dwSetup[14] = m_bFlimMasterFrequencyMHz ? 1 : 0;
  err = m_pCamera->SetCameraSetup(wType, &dwSetup[0], wLen, dwflags);
  UpdateStatus();
  return 0;
}

int CPCOCam::OnFlimModulationSource(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    if (m_wFlimSourceSelect == 0)
      pProp->Set("(0) intern");
    else
      pProp->Set("(1) extern");
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    string tmp;
    long lparameter;
    pProp->Get(tmp);
    ((MM::Property*)pProp)->GetData(tmp.c_str(), lparameter);

    if (lparameter != m_wFlimSourceSelect)
    {
      m_wFlimSourceSelect = (WORD)lparameter;

      nErr = SetupFlim();
      if (nErr != 0) // A return error refuses to set to extern, so reset value and update settings
      {
        m_wFlimSourceSelect = 0;
        UpdateStatus();
      }
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnFlimMasterFrequencyMHz(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    if (m_bFlimMasterFrequencyMHz)
      pProp->Set("(1) Mhz");
    else
      pProp->Set("(0) kHz");
  }
  else if (eAct == MM::AfterSet)
  {
    string tmp;
    long frequTmp;
    pProp->Get(tmp);
    ((MM::Property*)pProp)->GetData(tmp.c_str(), frequTmp);
    if (m_bFlimMasterFrequencyMHz != (frequTmp == 1))
    {
      if (m_wFlimSourceSelect == 0) // Changes are transferred only when source is set to intern
      {
        m_bFlimMasterFrequencyMHz = (frequTmp == 1);
        SetupFlim();
      }
      else
        UpdateStatus();
    }
  }
  return DEVICE_OK;
}


int CPCOCam::OnFlimFrequency(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  long lhelp;
  if (eAct == MM::BeforeGet)
  {
    lhelp = m_dwFlimFrequency;
    if (m_bFlimMasterFrequencyMHz)
      lhelp /= 1000;
    pProp->Set(lhelp);
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;

    ((MM::IntegerProperty*)pProp)->Get(lhelp);
    if (lhelp != (long)m_dwFlimFrequency)
    {
      if (m_wFlimSourceSelect == 0) // Changes are transferred only when source is set to intern
      {
        if (m_bFlimMasterFrequencyMHz)
          lhelp *= 1000;

        if (lhelp < 0)
          lhelp = 0;
        if (lhelp > 50000000)
          lhelp = 50000000;
        m_dwFlimFrequency = lhelp;
        nErr = SetupFlim();
      }
      else
        UpdateStatus();
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnFlimRelativePhase(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set((long)m_dwFlimPhaseMilliDeg);
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    long lhelp;

    ((MM::IntegerProperty*)pProp)->Get(lhelp);
    if (lhelp != (long)m_dwFlimPhaseMilliDeg)
    {
      if (m_wFlimPhaseNumber == 0)// Must be 0 and not 2,4,8 or 16 (!=0)
      {
        if (lhelp < 0)
          lhelp = 0;
        if (lhelp > 359999)
          lhelp = 359999;

        m_dwFlimPhaseMilliDeg = lhelp;
        nErr = SetupFlim();
      }
      else
        UpdateStatus();
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnFlimOutputWaveForm(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    if (m_wFlimOutputWaveform == 0)
      pProp->Set("(0) none");
    else
      if (m_wFlimOutputWaveform == 1)
        pProp->Set("(1) sine wave");
      else
        pProp->Set("(2) square wave");
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    string tmp;
    long waveTmp;
    pProp->Get(tmp);
    ((MM::Property*)pProp)->GetData(tmp.c_str(), waveTmp);

    if (waveTmp != m_wFlimOutputWaveform)
    {
      if (waveTmp < 0)
        waveTmp = 0;
      if (waveTmp > 2)
        waveTmp = 2;
      m_wFlimOutputWaveform = (WORD)waveTmp;
      nErr = SetupFlim();
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnFlimNumberOfPhaseSamples(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    switch (m_wFlimPhaseNumber)
    {
    case 0:
      pProp->Set("(0) shiftable pair");
      break;
    case 1:
      pProp->Set("(1) 2");
      break;
    case 2:
      pProp->Set("(2) 4");
      break;
    case 3:
      pProp->Set("(3) 8");
      break;
    case 4:
      pProp->Set("(4) 16");
      break;
    }
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    string tmp;
    long waveTmp;
    pProp->Get(tmp);
    ((MM::Property*)pProp)->GetData(tmp.c_str(), waveTmp);

    if (waveTmp != m_wFlimPhaseNumber)
    {
      if (waveTmp < 0)
        waveTmp = 0;
      if (waveTmp > 4)
        waveTmp = 4;

      m_wFlimPhaseNumber = (WORD)waveTmp;
      nErr = SetupFlim();
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnFlimPhaseSymmetry(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    if (m_wFlimPhaseSymmetry == 0)
      pProp->Set("(0) no");
    else
      pProp->Set("(1) yes");
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    string tmp;
    long waveTmp;
    pProp->Get(tmp);
    ((MM::Property*)pProp)->GetData(tmp.c_str(), waveTmp);

    if (waveTmp != m_wFlimPhaseSymmetry)
    {
      if (m_wFlimPhaseNumber != 0)
      {
        if (waveTmp < 0)
          waveTmp = 0;
        if (waveTmp > 1)
          waveTmp = 1;

        m_wFlimPhaseSymmetry = (WORD)waveTmp;
        nErr = SetupFlim();
      }
      else
        UpdateStatus();
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnFlimPhaseOrder(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    if (m_wFlimPhaseOrder == 0)
      pProp->Set("(0) ascending");
    else
      pProp->Set("(1) opposite");
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    string tmp;
    long waveTmp;
    pProp->Get(tmp);
    ((MM::Property*)pProp)->GetData(tmp.c_str(), waveTmp);

    if (waveTmp != m_wFlimPhaseOrder)
    {
      if (m_wFlimPhaseSymmetry == 1)  // Must be Yes (1)
      {
        if (waveTmp < 0)
          waveTmp = 0;
        if (waveTmp > 1)
          waveTmp = 1;

        m_wFlimPhaseOrder = (WORD)waveTmp;
        nErr = SetupFlim();
      }
      else
      {
        m_wFlimPhaseOrder = 0;
        UpdateStatus();
      }
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnFlimTapSelection(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    switch (m_wFlimTapSelect)
    {
    case 0:
      pProp->Set("(0) Tap A + B");
      break;
    case 1:
      pProp->Set("(1) Tap A");
      break;
    case 2:
      pProp->Set("(2) Tap B");
      break;
    }
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    string tmp;
    long waveTmp;
    pProp->Get(tmp);
    ((MM::Property*)pProp)->GetData(tmp.c_str(), waveTmp);

    if (waveTmp != m_wFlimTapSelect)
    {
      if (waveTmp < 0)
        waveTmp = 0;
      if (waveTmp > 2)
        waveTmp = 2;

      m_wFlimTapSelect = (WORD)waveTmp;
      nErr = SetupFlim();
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnFlimAsymCorrection(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    if (m_wFlimAsymmetryCorrection == 0)
      pProp->Set("(0) off");
    else
      pProp->Set("(1) on");
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    string tmp;
    long waveTmp;
    pProp->Get(tmp);
    ((MM::Property*)pProp)->GetData(tmp.c_str(), waveTmp);

    if (waveTmp != m_wFlimAsymmetryCorrection)
    {
      if (waveTmp < 0)
        waveTmp = 0;
      if (waveTmp > 1)
        waveTmp = 1;

      m_wFlimAsymmetryCorrection = (WORD)waveTmp;
      nErr = SetupFlim();
    }

    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}
