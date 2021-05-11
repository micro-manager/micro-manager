#include "dp_status.h"
#include "MicroManager.h"
#include "PCO_err.h"
#include "PCO_errt.h"

using namespace std;

HMODULE hDPCoreLib = nullptr;
int(*pdpcore_init)() = nullptr;
void(*pdpcore_set_loglevel)(int level) = nullptr;
dp_status(*pdpcore_set_logfile)(const TCHAR* filePath) = nullptr;
dp_status(*pdpcore_register_camhandle)(HANDLE ph) = nullptr;
dp_status(*pdpcore_prepare_image_with_camhandle)(WORD* imgbuf, DWORD imgsize, HANDLE ph, float error_bound) = nullptr;

int CPCOCam::UnInitDotPhoton()
{
  if (hDPCoreLib != nullptr)
    FreeLibrary(hDPCoreLib);
  hDPCoreLib = nullptr;
  return PCO_NOERROR;
}

int CPCOCam::InitDotPhoton()
{
  if (hDPCoreLib == nullptr)
  {
    TCHAR dotPhotonPath[_MAX_PATH];
		TCHAR Loadpath[_MAX_PATH];
    HMODULE hmod;

    CPropertyAction* pAct;
    string csh, szhelp;
    pAct = new CPropertyAction(this, &CPCOCam::OnDoDotPhoton);

    szhelp = "Off";
    csh = "DotPhoton Filter";
    int nRet = CreateProperty(csh.c_str(), szhelp.c_str(), MM::String, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = AddAllowedValue(csh.c_str(), "Off", 0);
    if (nRet != DEVICE_OK)
      return nRet;
    nRet = AddAllowedValue(csh.c_str(), "On", 1);
    if (nRet != DEVICE_OK)
      return nRet;

    GetModuleHandleEx(NULL, "mmgr_dal_PCO_Camera.dll", &hmod);
		GetModuleFileName(hmod, Loadpath, _MAX_PATH);
    char* pstop = strrchr(Loadpath, '\\');
    *pstop = 0;
    sprintf_s(dotPhotonPath, _MAX_PATH, "%s\\dpcore.dll", Loadpath);
    hDPCoreLib = LoadLibrary(dotPhotonPath);
    if (hDPCoreLib == nullptr)
    {
      char szmes[300];

      sprintf_s(szmes, sizeof(szmes), "MM InitDotPhoton: DotPhoton DLL could not be found, error %d", GetLastError());
      LogMessage(szmes);
      


      return PCO_ERROR_NOTAVAILABLE | PCO_ERROR_SDKDLL | PCO_ERROR_PCO_SDKDLL;
    }

		//Init DLL Functions
		pdpcore_init = (int(*)()) GetProcAddress(hDPCoreLib, "dpcore_init");

		pdpcore_set_loglevel = (void(*)(int level)) GetProcAddress(hDPCoreLib, "dpcore_set_loglevel");

		pdpcore_set_logfile = (dp_status(*)(const TCHAR * filePath)) GetProcAddress(hDPCoreLib, "dpcore_set_logfile");

		pdpcore_register_camhandle = (dp_status(*)(HANDLE ph)) GetProcAddress(hDPCoreLib, "dpcore_register_camhandle");

		pdpcore_prepare_image_with_camhandle = (dp_status(*)(WORD * imgbuf,
			DWORD imgsize,
			HANDLE ph,
			float error_bound))
			GetProcAddress(hDPCoreLib, "dpcore_prepare_image_with_camhandle");

		//Set log file path and level of dot photon
    //sprintf_s(dotPhotonPath, _MAX_PATH, "%s\\Micromanager_dpcore.log", Loadpath);
    //sprintf_s(dotPhotonPath, _MAX_PATH, "c:\\ProgramData\\pco\\\Micromanager_dpcore.log", Loadpath);
    sprintf_s(dotPhotonPath, _MAX_PATH, "%s\\MM_dpcore.log", Loadpath);
    WCHAR wdotPhotonPath[_MAX_PATH];
    size_t len = strlen(dotPhotonPath);
    
    MultiByteToWideChar(CP_OEMCP, 0, dotPhotonPath, -1, wdotPhotonPath, (int)len + 1);

    dp_status status = pdpcore_set_logfile((const TCHAR*)wdotPhotonPath);
		if (status != dp_success)
		{
      char szmes[300];

      sprintf_s(szmes, sizeof(szmes), "MM InitDotPhoton: DotPhoton: Set Logfile failed with %i", status);
      LogMessage(szmes);
			return PCO_ERROR_SDKDLL_SYSERR | PCO_ERROR_SDKDLL | PCO_ERROR_PCO_SDKDLL;
		}

    pdpcore_set_loglevel(2); //Off

		//Do DP init directly here
		pdpcore_init();
    m_bDotPhotonFilterAvailable = true;
  }
  return PCO_NOERROR;
}

int CPCOCam::InitDotPhotonCamera(HANDLE hcamera)
{
  dp_status status = pdpcore_register_camhandle(hcamera);
  if (status != dp_success)
  {
    char szmes[300];

    sprintf_s(szmes, sizeof(szmes), "MM InitDotPhotonCamera: DotPhoton: Register camera handle failed with %i", status);
    LogMessage(szmes);
    m_bDotPhotonFilterAvailable = false;
    return PCO_ERROR_SDKDLL_SYSERR | PCO_ERROR_SDKDLL | PCO_ERROR_PCO_SDKDLL;
  }
  return PCO_NOERROR;
}

int CPCOCam::CalcImageDotphoton(WORD *pb16, int iImageSize, HANDLE hCam)
{
  if (pdpcore_prepare_image_with_camhandle != nullptr)
  {
    float error_bound = 1.0;
    dp_status status = pdpcore_prepare_image_with_camhandle(pb16, iImageSize, hCam, error_bound);
    if (status != dp_success)
    {
      char szmes[300];

      sprintf_s(szmes, sizeof(szmes), "MM CalcImageDotphoton: Image preparation failed with %i", status);
      LogMessage(szmes);
      m_bDotPhotonFilterAvailable = false;
      return PCO_ERROR_SDKDLL_BUFFERNOTVALID | PCO_ERROR_SDKDLL | PCO_ERROR_PCO_SDKDLL;
    }
  }
  return PCO_NOERROR;
}

/*
The setting can be saved to the cfg file:

# Pre-init settings for devices
Property,pco_camera,DemoMode,Off
Property,pco_camera,DotPhoton Filter,On

*/
int CPCOCam::OnDoDotPhoton(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (!m_bDotPhotonFilterAvailable)
  {
    m_bDoDotPhoton = false;
    MM::Property* pProperty = dynamic_cast<MM::Property*>(pProp);
    pProperty->SetReadOnly(true);
  }

  if (eAct == MM::BeforeGet)
  {
    if (m_bDoDotPhoton)
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
    if (m_bDotPhotonFilterAvailable)
    {
      if (ihelp != 0)
        m_bDoDotPhoton = true;
      else
        m_bDoDotPhoton = false;
    }
  }
  return DEVICE_OK;
}
