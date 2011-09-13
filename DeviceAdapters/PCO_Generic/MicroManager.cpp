///////////////////////////////////////////////////////////////////////////////
// FILE:          MicroManager.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   pco camera module
//                
// AUTHOR:        Franz Reitner, Franz.Reitner@pco.de, 11/01/2010
// COPYRIGHT:     PCO AG, Kelheim, 2010
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//


/*#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
#endif*/

#include "stdafx.h"

#define PCO_ERRT_H_CREATE_OBJECT

#include "..\..\MMDevice/ModuleInterface.h"
#include "MicroManager.h"
//#pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 

#include <string>
#include <sstream>
#include <cmath>	// Liisa: for ceil

using namespace std;

CPCOCam* CPCOCam::m_pInstance = 0;
const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";
const char* g_PixelType_RGB32bit = "RGB 32bit";


#ifdef WIN32
/////////////////////////////////////////////////////////////////////////////
// CPCOMicroManagerApp

BEGIN_MESSAGE_MAP(CPCOMicroManagerApp, CWinApp)
	//{{AFX_MSG_MAP(CPCOMicroManagerApp)
		// NOTE - the ClassWizard will add and remove mapping macros here.
		//    DO NOT EDIT what you see in these blocks of generated code!
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CPCOMicroManagerApp construction
CPCOMicroManagerApp::CPCOMicroManagerApp()
{
}

BOOL CPCOMicroManagerApp::InitInstance() 
{
//call base class
  return CWinApp::InitInstance();
}

int CPCOMicroManagerApp::ExitInstance() 
{

  return CWinApp::ExitInstance();
}



/////////////////////////////////////////////////////////////////////////////
// The one and only CPCOMicroManagerApp object

CPCOMicroManagerApp theApp;
#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
  AddAvailableDeviceName("pco_camera", "PCO generic camera adapter");
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
  delete pDevice;
}

MODULE_API MM::Device* CreateDevice(const char* pszDeviceName)
{
  if (pszDeviceName == 0)
    return 0;

  string strName(pszDeviceName);
   
  if (strName == "pco_camera")
    return CPCOCam::GetInstance();
   
  return 0;
}


///////////////////////////////////////////////////////////////////////////////
// CPCOCam constructor/destructor

CPCOCam::CPCOCam() :
  CCameraBase<CPCOCam> (),
  sequenceRunning_(false),
  m_bInitialized(false),
  m_bBusy(false),
  m_dExposure(0.0),
  sthd_(0),
  stopOnOverflow_(false),
  pixelDepth_(2),
  pictime_(0.0)
{
  // initialize the array of camera settings
  m_nBoard = 0;
  m_nCameraType = 0;
  m_pszTimes[0] = 0;
  m_nSubMode = 0;
  m_nMode = 0;
  m_nTrig = 0;
  m_nRoiXMax = 0;
  m_nRoiXMin = 0;
  m_nRoiYMax = 0;
  m_nRoiYMin = 0;
  m_nHBin = 0;
  m_nVBin = 0;
  m_iGain = 0;
  m_iEMGain = 1;
  m_iOffset = 0;
  m_uiFlags = 0;
  m_nCCDType = 0; // read-only
  m_bufnr = -1;
  m_pCamera = NULL;
  m_nTimesLen = MMSENSICAM_MAX_STRLEN;
  m_pCamera = new CCamera();
  m_bDemoMode = FALSE;
  m_bStartStopMode = FALSE;
  m_iSkipImages = 1;
  m_iFpsMode = 0;
  m_dFps = 10.0;
  m_iPixelRate = 0;


  // DemoMode (pre-initialization property)
  CPropertyAction* pAct = new CPropertyAction (this, &CPCOCam::OnDemoMode);
  CreateProperty("DemoMode", "Off", MM::String, false, pAct, true);
  AddAllowedValue("DemoMode","Off",0);
  AddAllowedValue("DemoMode","On",1);

  InitializeDefaultErrorMessages();
  sthd_ = new SequenceThread(this);
}

CPCOCam::~CPCOCam()
{
  InitLib(255, NULL, 0, NULL); // Removes allocated objects

  if (m_bInitialized)
    Shutdown();
  
  delete(sthd_);
  m_pInstance = 0;
}

// Camera type
int CPCOCam::OnCameraType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set(m_pCamera->szCamName);
  }
  return DEVICE_OK;
}

// CCD type
int CPCOCam::OnCCDType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set("pco CCD");
  }
  return DEVICE_OK;
}

int CPCOCam::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
    pProp->Set(m_dExposure);
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    double dhelp;
    pProp->Get(dhelp);
    if(dhelp != m_dExposure)
    {
      m_dExposure = dhelp;
      if(m_pCamera->iCamClass == 2)
        sprintf(m_pszTimes, "%f", m_dExposure / 1000.0);
      else
        sprintf(m_pszTimes, "0,%d,-1,-1", (int)m_dExposure);
      nErr = SetupCamera();
    }
	    
    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnFpsMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    if(m_iFpsMode == 0)
      pProp->Set("Off");
    else
      pProp->Set("On");
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    string tmp;
    long fpsModeTmp;
    int ihelp;
    pProp->Get(tmp);
    ((MM::Property *) pProp)->GetData(tmp.c_str(), fpsModeTmp);
    ihelp = (fpsModeTmp == 1);

    if(ihelp != m_iFpsMode)
    {

      m_iFpsMode = ihelp;
      if(m_iFpsMode == 1)
        sprintf(m_pszTimes, "%ffps,%f,-1,-1", m_dFps, m_dExposure);
      else
        sprintf(m_pszTimes, "0,%d,-1,-1", (int)m_dExposure);
      nErr = SetupCamera();
    }
	    
    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnFps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    pProp->Set(m_dFps);
  }
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    double dhelp;
    pProp->Get(dhelp);
    if(dhelp != m_dFps)
    {
      m_dFps = dhelp;
      if(m_iFpsMode == 1)
        sprintf(m_pszTimes, "%ffps,%f,-1,-1", m_dFps, m_dExposure);
      else
        sprintf(m_pszTimes, "0,%d,-1,-1", (int)m_dExposure);
      nErr = SetupCamera();
    }
	    
    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnPixelRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    if(m_iPixelRate == 286000000)
      pProp->Set("286MHz");
    else
      pProp->Set("95MHz");
  }
  else if (eAct == MM::AfterSet)
  {
    int ipixr[2] = {95333333, 286000000};
    int nErr = 0;
    string tmp;
    long fpsModeTmp;
    int ihelp;
    pProp->Get(tmp);
    ((MM::Property *) pProp)->GetData(tmp.c_str(), fpsModeTmp);
    ihelp = ipixr[fpsModeTmp];

    if(ihelp != m_iPixelRate)
    {

      m_iPixelRate = ihelp;
      nErr = SetupCamera();
    }
	    
    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}

int CPCOCam::OnDemoMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
    if(m_bDemoMode)
      pProp->Set("Off");
  }
  else if (eAct == MM::AfterSet)
  {
    string tmp;
    long demoModeTmp;
    pProp->Get(tmp);
    ((MM::Property *) pProp)->GetData(tmp.c_str(), demoModeTmp);
    m_bDemoMode = (demoModeTmp == 1);
  }
  return DEVICE_OK;
}

// Binning
int CPCOCam::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  int nMode;
  int nErr;

  if(m_bDemoMode)
    return DEVICE_OK;
  nErr = 0;
  if (eAct == MM::AfterSet)
  {
    long bin;
    pProp->Get(bin);
    if(m_pCamera->iCamClass == 2)
    {
      bin -= 1;
      if(bin < 0)
        bin = 0;
    }

    if(bin != m_nHBin)
    {
      m_nHBin = bin;
      m_nVBin = bin;
      m_nRoiXMin = 1;
      m_nRoiYMin = 1;
      if(m_pCamera->iCamClass == 1)// SensiCam
      {
        m_nRoiXMax = roiXMaxFull_;
        m_nRoiYMax = roiYMaxFull_;
      }
      else
      if(m_pCamera->iCamClass == 2)// PixelFly
      {
        m_nRoiXMax = roiXMaxFull_ / (m_nHBin + 1);
        m_nRoiYMax = roiYMaxFull_ / (m_nVBin + 1);
      }
      else
      if(m_pCamera->iCamClass == 3)// pco.camera
      {
        m_nRoiXMax = roiXMaxFull_ / m_nHBin;
        m_nRoiYMax = roiYMaxFull_ / m_nVBin;
      }

      nErr = SetupCamera();
      if(nErr != 0)
      {
        return nErr;
      }
      return ResizeImageBuffer();
    }
    return 0;
  }
  else if (eAct == MM::BeforeGet)
  {
    nErr = m_pCamera->getsettings(&nMode, &m_nTrig, &m_nRoiXMin, &m_nRoiXMax, &m_nRoiYMin, &m_nRoiYMax,
                           &m_nHBin, &m_nVBin, m_pszTimes, &m_iGain, &m_iOffset, &m_uiFlags);
    if (nErr != 0)
      return nErr;
    pProp->Set((long)m_nHBin);
  }
  return DEVICE_OK;
}

int CPCOCam::OnEMLeftROI(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  int nMode;
  int nErr;

  if(m_bDemoMode)
    return DEVICE_OK;
  nErr = 0;
  if (eAct == MM::AfterSet)
  {
    long left;
    pProp->Get(left);
    if(m_pCamera->iCamClass == 2)
    {
      left -= 1;
      if(left < 0)
        left = 0;
    }

    m_nRoiXMin = left;
    m_nRoiYMin = 1;
    m_nRoiXMax = roiXMaxFull_;
    m_nRoiYMax = roiYMaxFull_;
    nErr = SetupCamera();
    if(nErr != 0)
    {
      return nErr;
    }
    return ResizeImageBuffer();
  }
  else if (eAct == MM::BeforeGet)
  {
    nErr = m_pCamera->getsettings(&nMode, &m_nTrig, &m_nRoiXMin, &m_nRoiXMax, &m_nRoiYMin, &m_nRoiYMax,
                           &m_nHBin, &m_nVBin, m_pszTimes, &m_iGain, &m_iOffset, &m_uiFlags);
    if (nErr != 0)
      return nErr;

    pProp->Set((long)m_nRoiXMin);
  }
  return DEVICE_OK;
}


int CPCOCam::SetupCamera()
{
  unsigned int uiMode;
  int nErr;
  int istopresult;
  int iOffsPxr;

  if(m_bDemoMode)
  {
    nErr = ResizeImageBuffer();
    if (nErr != 0)
      return nErr;
    return DEVICE_OK;
  }
  nErr = m_pCamera->StopCam(&istopresult);
  if (nErr != 0)
    return nErr;

  iOffsPxr = m_iOffset;


  m_nTimesLen = MMSENSICAM_MAX_STRLEN;
  nErr = m_pCamera->testcoc(&m_nMode, &m_nTrig, &m_nRoiXMin, &m_nRoiXMax, &m_nRoiYMin, &m_nRoiYMax,
                            &m_nHBin, &m_nVBin, m_pszTimes, &m_nTimesLen, &m_iGain, &iOffsPxr, &m_uiFlags);
  if ((nErr != 0) && (nErr != 103))
    return nErr;
  if((m_nCameraType == 0x1300) || (m_nCameraType == 0x1310))
    iOffsPxr = m_iPixelRate;
  nErr = m_pCamera->setcoc(m_nMode, m_nTrig, m_nRoiXMin, m_nRoiXMax, m_nRoiYMin, m_nRoiYMax,
                           m_nHBin, m_nVBin, m_pszTimes, m_iGain, iOffsPxr, m_uiFlags);
  if (nErr != 0)
    return nErr;
  nErr = ResizeImageBuffer();
  if (nErr != 0)
    return nErr;

  uiMode = 0x10000 + 0x0020;//Avoid adding buffers, Preview, Single
  nErr = m_pCamera->PreStartCam(uiMode, 0, 0, 0);            // schaltet automatisch auf internen Trigger
  if (nErr != 0)
    return nErr;
  nErr = m_pCamera->StartCam();
  return nErr;

}

// Pixel type
int CPCOCam::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::AfterSet)
  {
    string pixType;
    pProp->Get(pixType);
    if (pixType.compare(g_PixelType_16bit) == 0)
      pixelDepth_ = 2;
    else if (pixType.compare(g_PixelType_8bit) == 0)
      pixelDepth_ = 1;
    else if (pixType.compare(g_PixelType_RGB32bit) == 0)
      pixelDepth_ = 4;
    else
    {
      return DEVICE_INTERNAL_INCONSISTENCY;
    }
    return ResizeImageBuffer();
  }
  else if (eAct == MM::BeforeGet)
  {
    if (pixelDepth_ == 1)
      pProp->Set(g_PixelType_8bit);
    else if (pixelDepth_ == 2)
      pProp->Set(g_PixelType_16bit);
    else if (pixelDepth_ == 4)
      pProp->Set(g_PixelType_RGB32bit);
    else
    {
      return DEVICE_INTERNAL_INCONSISTENCY;
    }
  }
  return DEVICE_OK;
}

int CPCOCam::OnGain(MM::PropertyBase* /*pProp*/, MM::ActionType /*eAct*/)
{
  // nothing to do  - just use the default value
  return DEVICE_OK;
}

int CPCOCam::OnEMGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
    pProp->Set((long)m_iEMGain);
  else if (eAct == MM::AfterSet)
  {
    int nErr = 0;
    long ihelp;
    pProp->Get(ihelp);
    if(ihelp != m_iEMGain)
    {
      m_iEMGain = ihelp;
      sprintf(m_pszTimes, "0,%d,-1,-1\r\nmg%d", (int)m_dExposure, m_iEMGain);
      nErr = SetupCamera();
    }
	    
    if (nErr != 0)
      return nErr;
  }
  return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Function name   : CPCOCam::Initialize
// Description     : Initialize the camera
// Return type     : bool 

int CPCOCam::Initialize()
{
  // setup PCO camera
  // ----------------

  // Name
  int nRet = CreateProperty("Name", "pco camera", MM::String, true);
  if (nRet != DEVICE_OK)
    return nRet;

  // Description
  nRet = CreateProperty("Description", "pco generic driver module", MM::String, true);
  if (nRet != DEVICE_OK)
    return nRet;

  CPropertyAction* pAct;
  // Initialize the camera
  int nErr;
 
  InitLib(MMIJ, NULL, 0, NULL);

  if (! m_bDemoMode)
  {
    nErr = m_pCamera->PreInitSC2(0, 0, 0);
    if(nErr != PCO_NOERROR)
      nErr = m_pCamera->PreInitSen(0, 0, 0);
    if(nErr != PCO_NOERROR)
      nErr = m_pCamera->PreInitPcCam(0, 0, 0);
    if(nErr != PCO_NOERROR)
    {
      return DEVICE_NOT_CONNECTED;
    }
  }
  else
  {
    m_pCamera->SetDemoMode(TRUE, 1280, 1024, FALSE, FALSE, 12, 0);
    nErr = m_pCamera->PreInitSen(0, 0, 0);
    sprintf_s(m_pCamera->szCamName, 20, "SensiCam demo");

    if (nErr != 0)
      return DEVICE_ERR;
  }

  if(!m_bDemoMode)
  {
    try
    {
      LogMessage("pco_generic. Try to Init!");
      nErr = m_pCamera->Init(FALSE);
    }
    catch(...)
    {
      LogMessage("pco_generic. Failed to Init! Try catch error...Mr.Pco! What have you done??");
      delete(m_pCamera);
      SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR, "PCO_Camera's \"Convert dll\" missing. Please copy pco_conv.dll to MM directory!");
      return DEVICE_LOCALLY_DEFINED_ERROR;
    }
  }
  if (nErr != 0)
  {
    WriteLog("pco_generic. Error %x in Init!", nErr);
    return nErr;
  }
  // camera type (read-only)
  pAct = new CPropertyAction (this, &CPCOCam::OnCameraType);
  nRet = CreateProperty("CameraType", "", MM::String, true, pAct);
  if (nRet != DEVICE_OK)
    return nRet;
  UpdateProperty("CameraType");

  m_nTimesLen = MMSENSICAM_MAX_STRLEN;
  m_dExposure = 10;
  m_nCameraType = m_pCamera->GetCamType();
  m_nCCDType = m_pCamera->GetCCDType();
  if(m_bDemoMode)
    m_nCameraType = 7;

  if(m_pCamera->iCamClass == 3)
  {
    m_nHBin = m_nVBin = 1;
    m_nMode = 0;//M_LONG;
    m_nSubMode = 0;//NORMALLONG;
    m_nTrig = 0;
    snprintf(m_pszTimes, m_nTimesLen, "0,%.0f,-1,-1", m_dExposure);
  }
  if(m_pCamera->iCamClass == 2)
  {
    m_nHBin = m_nVBin = 0;
    m_nMode = 0x20000;//M_LONG;
    m_nSubMode = 0;//NORMALLONG;
    m_nTrig = 0;
    sprintf(m_pszTimes, "%f", m_dExposure / 1000.0);
  }
  if(m_pCamera->iCamClass == 1)
  {
    m_nHBin = m_nVBin = 1;
    switch(m_nCameraType)
    {
      case 1: // Fast Shutter
        m_nMode = 1;//M_FAST;
        m_nSubMode = 0;//NORMALFAST;
        m_nTrig = 0;
        //1ms exposure time
        m_dExposure = 1;
        snprintf(m_pszTimes, m_nTimesLen, "0,%.0f,-1,-1", m_dExposure * 1.0e06);
      break;

      case 2:
      case 7:
        // Long exposure 2; Long exposure QE 7
        m_nMode = 0;//M_LONG;
        m_nSubMode = 0;//NORMALLONG;
        m_nTrig = 0;
        //10ms exposure time

        if(m_nCCDType == 0x21)
          snprintf(m_pszTimes, m_nTimesLen, "0,%.0f,-1,-1\r\nmg%d", m_dExposure, m_iEMGain);
        else
          snprintf(m_pszTimes, m_nTimesLen, "0,%.0f,-1,-1", m_dExposure);
      break;

      case 3:
      // Long exposure OEM
        m_nMode = 0;//M_LONG;
        m_nSubMode = 0;//NORMALLONG;
        m_nTrig = 0;
        //10ms exposure time
        snprintf(m_pszTimes, m_nTimesLen, "0,%.0f,-1,-1", m_dExposure);
      break;

      case 8://FASTEXPQE
        m_nMode = 0;//M_LONG;
        m_nSubMode = 0;//NORMALLONG;
        m_nTrig = 0;
        //10ms exposure time
        snprintf(m_pszTimes, m_nTimesLen, "0,%.0f,-1,-1", m_dExposure);
      break;
        
      default:
        // invalid type
        return ERR_UNKNOWN_CAMERA_TYPE;
      break;
    }
  }
  int imode, isubmode;

  imode = m_nMode;
  isubmode = m_nSubMode;
  // CCD type (read-only)
  pAct = new CPropertyAction (this, &CPCOCam::OnCCDType);
  nRet = CreateProperty("CCDType", "", MM::String, true, pAct);
  if (nRet != DEVICE_OK)
    return nRet;
  UpdateProperty("CCDType");

  // Binning
  pAct = new CPropertyAction (this, &CPCOCam::OnBinning);
  nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;

  vector<string> binValues;
  binValues.push_back("1");
  if(m_pCamera->iCamClass == 1)
  {
    binValues.push_back("2");
    binValues.push_back("4");
    binValues.push_back("8");
  }
  if(m_pCamera->iCamClass == 2)
  {
    binValues.push_back("2");
  }
  if(m_pCamera->iCamClass == 3)
  {
    int iroixmax, iroiymax;
    int mode, trig, roix1, roix2, roiy1, roiy2, hbin, vbin, size, gain, offset;
    unsigned int flags;
    char table[400];

    m_pCamera->GetMaximumROI(&iroixmax, &iroiymax);

    mode = 0; trig = 0; roix1 = 1; roix2 = iroixmax;
    roiy1 = 1; roiy2 = iroiymax; size = 0; gain = 0; offset = 0; flags = 0;
    sprintf_s(table, 400, "0, 10, -1, -1");

    hbin = vbin = 2;
    roix2 /= 2;
    roiy2 /= 2;
    m_pCamera->testcoc(&mode, &trig, &roix1, &roix2, &roiy1, &roiy2,
      &hbin, &vbin, table, &size, &gain, &offset, &flags);
    if((hbin == 2) && (vbin == 2))
      binValues.push_back("2");

    hbin = vbin = 4;
    roix2 /= 2;
    roiy2 /= 2;
    m_pCamera->testcoc(&mode, &trig, &roix1, &roix2, &roiy1, &roiy2,
      &hbin, &vbin, table, &size, &gain, &offset, &flags);
    if((hbin == 4) && (vbin == 4))
      binValues.push_back("4");

    hbin = vbin = 8;
    roix2 /= 2;
    roiy2 /= 2;
    m_pCamera->testcoc(&mode, &trig, &roix1, &roix2, &roiy1, &roiy2,
      &hbin, &vbin, table, &size, &gain, &offset, &flags);
    if((hbin == 8) && (vbin == 8))
      binValues.push_back("8");
  }

  nRet = SetAllowedValues(MM::g_Keyword_Binning, binValues);
  if (nRet != DEVICE_OK)
    return nRet;

  m_nRoiXMin = m_nRoiYMin = 1;
  m_pCamera->GetMaximumROI(&m_nRoiXMax, &m_nRoiYMax);
  if(m_bDemoMode)
  {
    m_nRoiXMax = 1280;
    m_nRoiYMax = 1024;
  }

  // establish full frame limits
  roiXMaxFull_ = m_nRoiXMax;
  roiYMaxFull_ = m_nRoiYMax;

  // Pixel type
  pAct = new CPropertyAction (this, &CPCOCam::OnPixelType);
  nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;

  vector<string> pixTypes;
  pixTypes.push_back(g_PixelType_16bit);
  pixTypes.push_back(g_PixelType_8bit);

// FRE / 09.02.09
// This is beta state up to now and will work with versions >=30
#if DEVICE_INTERFACE_VERSION >= 30

  if(m_pCamera->GetCCDCol(0))
    pixTypes.push_back(g_PixelType_RGB32bit);
#endif
  nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixTypes);
  if (nRet != DEVICE_OK)
    return nRet;

  // EMGain
  if((m_nCCDType == 0x21) ||  // TI EM 285
     (m_nCCDType == 0x27))
  {
    pAct = new CPropertyAction (this, &CPCOCam::OnEMGain);
    nRet = CreateProperty(MM::g_Keyword_EMGain, "1", MM::Integer, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = SetPropertyLimits(MM::g_Keyword_EMGain, 1.0, 9.0);
    if (nRet != DEVICE_OK)
      return nRet;

    pAct = new CPropertyAction (this, &CPCOCam::OnEMLeftROI);
    nRet = CreateProperty("EM left ROI", "1", MM::Integer, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;

    
    vector<string> roiValues;
    roiValues.push_back("1");
    roiValues.push_back("2");

    nRet = SetAllowedValues("EM left ROI", roiValues);
    if (nRet != DEVICE_OK)
      return nRet;
    UpdateProperty("EM left ROI");

  }
  // Exposure
  pAct = new CPropertyAction (this, &CPCOCam::OnExposure);
  nRet = CreateProperty(MM::g_Keyword_Exposure, "10", MM::Float, false, pAct);
  if (nRet != DEVICE_OK)
    return nRet;
  if((m_nCameraType == 0x1300) ||// fps setting for pco.edge
     (m_nCameraType == 0x1310))
  {
    pAct = new CPropertyAction (this, &CPCOCam::OnFps);
    nRet = CreateProperty("Fps", "1", MM::Float, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = SetPropertyLimits("Fps", 1.0, 100.0);
    if (nRet != DEVICE_OK)
      return nRet;

    pAct = new CPropertyAction (this, &CPCOCam::OnFpsMode);
    nRet = CreateProperty("Fps Mode", "Off", MM::String, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = AddAllowedValue("Fps Mode","Off",0);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = AddAllowedValue("Fps Mode","On",1);
    if (nRet != DEVICE_OK)
      return nRet;

    pAct = new CPropertyAction (this, &CPCOCam::OnPixelRate);
    nRet = CreateProperty("PixelRate", "95MHz", MM::String, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = AddAllowedValue("PixelRate","95MHz",0);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = AddAllowedValue("PixelRate","286MHz",1);
    if (nRet != DEVICE_OK)
      return nRet;
  }

  //test if SET_COC gets right values
  if(m_pCamera->iCamClass == 1)
    m_nMode = imode + (isubmode << 16);
  else
    m_nMode = imode;
  m_nSubMode = isubmode;
  nErr = SetupCamera();
  if (nErr != DEVICE_OK)
    return nErr;
  m_bInitialized = true;

  // set additional properties as read-only for now

  return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : CPCOCam::Shutdown
// Description     : Deactivate the camera, reverse the initialization process
// Return type     : bool 

int CPCOCam::Shutdown()
{
  int istopresult = 0;
  m_bInitialized = false;

  if(m_pCamera != NULL)
  {
    if(!m_bDemoMode)
      m_pCamera->StopCam(&istopresult);
    m_pCamera->CloseCam();
    delete(m_pCamera);
    m_pCamera = NULL;
  }
  return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : CPCOCam::SnapImage
// Description     : Acquires a single frame and stores it in the internal
//                   buffer
// Return type     : bool 

int CPCOCam::SnapImage()
{
  int nErr;
  int cnt = m_iSkipImages;

  if(m_bDemoMode)
    return DEVICE_OK;
  if(m_bStartStopMode == TRUE)
  {
    m_pCamera->StopCam(&nErr);
    m_pCamera->StartCam();
  }
  do
  {
    m_pCamera->ResetEvWait();
    nErr = m_pCamera->WaitForImage();
  }while(cnt-- > 0);

  if (nErr != 0)
    return nErr;
   
  return DEVICE_OK;
}

/**
* Returns the number of physical channels in the image.
*/
unsigned int CPCOCam::GetNumberOfComponents() const
{
/*  if(img_.Depth() == 4)// Faulty setting inside MM
    return 4;
  else*/
    return 1;
}

unsigned  CPCOCam::GetImageBytesPerPixel() const
{
/*  if(img_.Depth() == 4)// Faulty setting inside MM
    return 1;
  else*/
    return img_.Depth();
}


unsigned CPCOCam::GetBitDepth() const
{
  if (img_.Depth() == 1)
    return 8;
  else if (img_.Depth() == 2)
  {
    return m_pCamera->GetBitsPerPixel();
  }
  else if (img_.Depth() == 4)
  {
    return 32;
  }
  else
  {
    return 0; // should not happen
  }
}

int CPCOCam::GetBinning () const
{
  return m_nHBin;
}

int CPCOCam::SetBinning (int binSize) 
{
  ostringstream os;
  os << binSize;
  return SetProperty(MM::g_Keyword_Binning, os.str().c_str());
}

/**
* Returns pixel data with interleaved RGB pixels in 32 bpp format
*/
const unsigned int* CPCOCam::GetImageBufferAsRGB32()
{
  return (unsigned int*) GetImageBuffer();
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : char* CPCOCam::GetImageBuffer
// Description     : Returns the raw image buffer
// Return type     : const unsigned 

const unsigned char* CPCOCam::GetImageBuffer()
{
  int nErr = 0, iw, ih;

  m_pic = m_pCamera->GetPic12(NULL);

  if (img_.Depth() == 2)
  {
    memcpy((void*) const_cast<unsigned char*>(img_.GetPixels()), (const void*)m_pic, img_.Width() * img_.Height() * 2);
  }
  else if (img_.Depth() == 1)
  {
    unsigned char *pchar;
    unsigned short *ppic;
    iw = img_.Width();
    ih = img_.Height();

    ppic = (unsigned short*)m_pic;
    pchar = const_cast<unsigned char*>(img_.GetPixels());
    for(int y = 0; y < ih; y++)
    {
      for(int x = 0; x < iw; x++)
      {
        int ival;
        ival = ppic[y*iw + x];
        ival *= 255;
        ival /= 4095;
        if(ival > 255)
          ival = 255;
        *pchar++ = (unsigned char) ival;
      }
    }
  }
  else if (img_.Depth() == 4)
  {
    m_pCamera->SetConvertBWCol(FALSE, TRUE);
    m_pCamera->SetViewMode(TRUE, FALSE, FALSE, FALSE);//SetFlip(TRUE);
    m_pCamera->Convert();
    iw = img_.Width();
    ih = img_.Height();
    unsigned char *pchar;
    unsigned char *ppic8, *ph;
    int iadd;
    
    ppic8 = m_pCamera->GetPic8c();

    iadd = (iw * 3) % 4;
    if(iadd != 0)
      iadd = 4 - iadd;
    pchar = const_cast<unsigned char*>(img_.GetPixelsRW());
    for(int y = 0; y < ih; y++)
    {
      ph = &ppic8[y*(iw*3 + iadd)];
      for(int x = 0; x < iw; x++)
      {
        *pchar++ = (unsigned char) *ph++;
        *pchar++ = (unsigned char) *ph++;
        *pchar++ = (unsigned char) *ph++;
        *pchar++ = 0;
      }
    }
  }

  if (nErr != 0)
    return 0;

  return img_.GetPixels();
}

int CPCOCam::SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize)
{
  int nErr;

  if(m_pCamera->iCamClass == 2)
    return DEVICE_OK;
  if(m_bDemoMode)
    return DEVICE_OK;

  nErr = m_pCamera->getsettings(&m_nMode, &m_nTrig, &m_nRoiXMin, &m_nRoiXMax, &m_nRoiYMin, &m_nRoiYMax,
		&m_nHBin, &m_nVBin, m_pszTimes, &m_iGain, &m_iOffset, &m_uiFlags);
  if (nErr != 0)
    return nErr;

  if(m_pCamera->iCamClass == 1)
  {
    // Liisa: changed these to round up, else uX or uY < 32 rounds to zero, Sensicam needs min 1.
    m_nRoiXMin = (int) ceil( ( (double) uX * m_nHBin / 32) ) + 1;
    m_nRoiYMin = (int) ceil( ( (double) uY * m_nHBin / 32) ) + 1;
    m_nRoiXMax = (int) ceil( ( ( (double) uX + uXSize) * m_nHBin / 32) -1 );
    m_nRoiYMax = (int) ceil( ( ( (double) uY + uYSize) * m_nHBin / 32) -1 );
  }
  else
  {
    m_nRoiXMin = (int) ceil( ( (double) uX * m_nHBin) ) + 1;
    m_nRoiYMin = (int) ceil( ( (double) uY * m_nHBin) ) + 1;
    m_nRoiXMax = (int) ceil( ( ( (double) uX + uXSize) * m_nHBin) -1 );
    m_nRoiYMax = (int) ceil( ( ( (double) uY + uYSize) * m_nHBin) -1 );
  }
  nErr = SetupCamera();
  if (nErr != 0)
    return nErr;
  nErr = ResizeImageBuffer();
  if (nErr != 0)
    return nErr;

  return DEVICE_OK;
}

int CPCOCam::GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize)
{
  int nErr;
  if(m_bDemoMode)
  {
    uXSize = uX = 1280;
    uYSize = uY = 1024;

    return DEVICE_OK;
  }  
  nErr = m_pCamera->getsettings(&m_nMode, &m_nTrig, &m_nRoiXMin, &m_nRoiXMax, &m_nRoiYMin, &m_nRoiYMax,
	&m_nHBin, &m_nVBin, m_pszTimes, &m_iGain, &m_iOffset, &m_uiFlags);

  if (nErr != 0)
     return nErr;
  if(m_pCamera->iCamClass == 2)
  {
    uX = m_nRoiXMin * 32;
    uY = m_nRoiYMin * 32;

    uXSize = (m_nRoiXMax - m_nRoiXMin + 1) * 32;
    uYSize = (m_nRoiYMax - m_nRoiYMin + 1) * 32;
  }
  else
  {
    uX = m_nRoiXMin;
    uY = m_nRoiYMin;

    uXSize = m_nRoiXMax - m_nRoiXMin + 1;
    uYSize = m_nRoiYMax - m_nRoiYMin + 1;
  }
  return DEVICE_OK;
}

int CPCOCam::ClearROI()
{
  int nErr;
   
  if(m_bDemoMode)
    return DEVICE_OK;

  m_nRoiXMin = 1;
  m_nRoiYMin = 1;
  m_nRoiXMax = roiXMaxFull_;
  m_nRoiYMax = roiYMaxFull_;
  nErr = SetupCamera();

  if(nErr != 0)
    return nErr;
   
  // Liisa: read the current ROI to the variables to be used in SnapImage
  // Although the values set by SET_COC are correct here, it goes wrong somewhere later
  // and in SnapImage the old ROI is used
  nErr = m_pCamera->getsettings(&m_nMode, &m_nTrig, &m_nRoiXMin, &m_nRoiXMax, &m_nRoiYMin, &m_nRoiYMax,
                                &m_nHBin, &m_nVBin, m_pszTimes, &m_iGain, &m_iOffset, &m_uiFlags);
  // end Liisa

  if(nErr != 0)
    return nErr;

  nErr = ResizeImageBuffer();
   
  if (nErr != 0)
    return nErr;

  return DEVICE_OK;
}

void CPCOCam::SetExposure(double dExp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(dExp));
}

int CPCOCam::ResizeImageBuffer()
{
  // get image size
  int nWidth = 1, nHeight = 1;
  int as;

  int nErr;
  if(m_bDemoMode)
  {
    nWidth = 1280;
    nHeight = 1024;
  }
  else
  {
    nErr = m_pCamera->getccdsize(&as, &nWidth, &nHeight);
    nWidth = m_pCamera->GetXRes();
    nHeight = m_pCamera->GetYRes();
    if (nErr != 0)
    {
      return nErr;
    }
    m_pCamera->ReloadSize();
  }

  if(!(pixelDepth_ == 1 || pixelDepth_ == 2 || pixelDepth_ == 4))
    return -1;
  img_.Resize(nWidth, nHeight, pixelDepth_);

  return DEVICE_OK;
}

int CPCOCam::PrepareSequenceAcqusition()
{
  return DEVICE_OK;
}

int CPCOCam::StartSequenceAcquisition(long numImages, double /*interval_ms*/, bool stopOnOverflow)
{
  if (Busy() || sequenceRunning_)
    return DEVICE_CAMERA_BUSY_ACQUIRING;

  int ret = GetCoreCallback()->PrepareForAcq(this);
  if (ret != DEVICE_OK)
    return ret;
  sequenceRunning_ = true;
  sthd_->SetLength(numImages);
  sthd_->Start();
  stopOnOverflow_ = stopOnOverflow;
  return DEVICE_OK;
}

int CPCOCam::StopSequenceAcquisition()
{
  sthd_->Stop();
  sthd_->wait();
  sequenceRunning_ = false;
  return DEVICE_OK;
}

int CPCOCam::StoppedByThread()
{
  sequenceRunning_ = false;
  return DEVICE_OK;
}

bool CPCOCam::IsCapturing()
{
  return sequenceRunning_;
}

int CPCOCam::InsertImage()
{
  const unsigned char* img = GetImageBuffer();
  if (img == 0)
    return ERR_TIMEOUT;

  int ret = GetCoreCallback()->InsertImage(this, img, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
  if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
  {
    // do not stop on overflow - just reset the buffer
    GetCoreCallback()->ClearImageBuffer(this);
    return GetCoreCallback()->InsertImage(this, img, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel());
  } else
    return ret;
}


int CPCOCam::SequenceThread::svc()
{
  long count(0);
  int err = 0;
  while (!stop_ && count < numImages_)
  {
    int ret = camera_->SnapImage();
    if (ret != DEVICE_OK)
    {
      err = 1;
      break;
    }

    ret = camera_->InsertImage();
    if (ret != DEVICE_OK)
    {
      err = 1;
      break;
    }
    CDeviceUtils::SleepMs(20);
    count++;
  }

  camera_->StoppedByThread();
  return err;
}

void CPCOCam::WriteLog(char* message, int nErr)
{
  char szmes[300];

  if(nErr != 0)
    sprintf(szmes, "Error %x! %s", nErr, message);
  else
    sprintf(szmes, "%s", message);
  LogMessage(szmes);
}
