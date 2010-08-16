#include <windows.h>
#include <stdlib.h>
#include <shlwapi.h>
static HINSTANCE hInstDLL=NULL;
static int globalindex=0;
#include "MexExl.h"
#pragma warning(disable: 4100)  // bunch of nonsense
#pragma warning(disable: 4054)  // bunch of nonsense

static const wchar_t* szMexDLL[]=
{
  L"MexJCam.dll", // Version Jenoptik, all ProgRes cameras
  L"MexLCam.dll"  // Version for DFC500 (Leica/Cambridge)
};

const int FUNCOUNT[2]=
{
  MEX_FUNCTIONCOUNT_FULL, // Jenoptik
  MEX_FUNCTIONCOUNT_LC // Leica 
};

static void LoadFunction(int index);
static void ResetAllStaticFunctionPointers();
static void ResetStaticFunctionPointer(int index);

static mexTt Tt={0,0};

// 0
static long __stdcall  Y_FindCameras(
unsigned int* pNumber,
unsigned __int64 GUID_List[]
)
{
  return MEX_FAILED;
}

// 1
static long __stdcall  Y_FindCamerasEx(
const unsigned __int64 GUID_Mask,
unsigned int * pNumber,
unsigned __int64 GUID_List[]
)
{
  return MEX_FAILED;
}

// 2
static mexCamType __stdcall  Y_GetCameraType(
unsigned __int64 CamGUID
)
{
  return camunknown;
}

// 3
static long __stdcall  Y_Init(
mexInitCB pCB,
unsigned long UserValue
)
{
  return MEX_FAILED;
}

// 4
static void __stdcall  Y_Exit(
void
)
{
  return;
}

// 5
static long __stdcall  Y_OpenCamera(
unsigned __int64 CamGUID
)
{
  return MEX_FAILED;
}

// 6
static long __stdcall  Y_CloseCamera(
unsigned __int64 CamGUID
)
{
  return MEX_FAILED;
}

// 7
static BOOL __stdcall  Y_IsOpen(
unsigned __int64 CamGUID
)
{
  return MEX_FAILED;
}

// 8
static long __stdcall  Y_GetVersion(
mexCOMPONENT comp,
mexVERSION * pVer
)
{
  return MEX_FAILED;
}

// 9
static long __stdcall  Y_GetInfo(
unsigned __int64 CamGUID,
mexInfo * pmexInfo
)
{
  return MEX_FAILED;
}

// 10
static long __stdcall  Y_GetSerialNumberString(
unsigned __int64 CamGUID,
char szString[ 16 ]
)
{
  return MEX_FAILED;
}

// 11
static long __stdcall  Y_GetCameraInfo(
unsigned __int64 CamGUID,
mexCamInfo * pCamInfo
)
{
  return MEX_FAILED;
}

// 12
static long __stdcall  Y_GetCameraTypeInfo(
int type,
int subtype,
mexCameraTypeSummary* pCT
)
{
  return MEX_FAILED;
}

// 13
static long __stdcall  Y_GetCameraTypeSummary(
unsigned __int64 CamGUID,
mexCameraTypeSummary * pTypeSummary
)
{
  return MEX_FAILED;
}

// 14
static long __stdcall  Y_GetAcquisitionInfo(
unsigned __int64 CamGUID,
mexAcquisParams * pPars,
mexImageInfo * pImgInfo
)
{
  return MEX_FAILED;
}

// 15
static BOOL __stdcall  Y_IsAcquisitionModeSupported(
unsigned __int64 CamGUID,
mexAcqMode amode
)
{
  return MEX_FAILED;
}

// 16
static long __stdcall  Y_Acquire(
unsigned __int64 CamGUID,
mexAcquisParams* pPars,
mexImg* imgList[],
unsigned long listSize
)
{
  return MEX_FAILED;
}

// 17
static long __stdcall  Y_Grab(
unsigned __int64 CamGUID,
mexAcquisParams* pPars,
mexImg* pImg
)
{
  return MEX_FAILED;
}

// 18
static long __stdcall  Y_ModifyLiveAcquisParams(
unsigned __int64 CamGUID,
mexAcquisParams* pPars
)
{
  return MEX_FAILED;
}

// 19
static long __stdcall  Y_AbortAcquisition(
unsigned __int64 CamGUID
)
{
  return MEX_FAILED;
}

// 20
static long __stdcall  Y_CloseImageTransfer(
unsigned __int64 CamGUID
)
{
  return MEX_FAILED;
}

// 21
static long __stdcall  Y_FreeInternalMemory(
unsigned __int64 CamGUID,
int parts
)
{
  return MEX_FAILED;
}

// 22
static long __stdcall  Y_GetBlackRef(
unsigned __int64 CamGUID,
mexProgressProc pCallBack,
DWORD dwData
)
{
  return MEX_FAILED;
}

// 23
static long __stdcall  Y_GetBlackRefLength(
unsigned __int64 CamGUID,
unsigned int * pLength
)
{
  return MEX_FAILED;
}

// 24
static long __stdcall  Y_SaveBlackRef(
unsigned __int64 CamGUID,
void * ref
)
{
  return MEX_FAILED;
}

// 25
static long __stdcall  Y_RestoreBlackRef(
unsigned __int64 CamGUID,
void * ref
)
{
  return MEX_FAILED;
}

// 26
static long __stdcall  Y_GetWhiteRef(
unsigned __int64 CamGUID,
unsigned long exposureTicks,
mexProgressProc progressProc,
unsigned long progressUser
)
{
  return MEX_FAILED;
}

// 27
static long __stdcall  Y_GetWhiteRefLength(
unsigned __int64 CamGUID,
unsigned int * pLength
)
{
  return MEX_FAILED;
}

// 28
static long __stdcall  Y_GetWhiteTable(
unsigned __int64 CamGUID,
void * pTable
)
{
  return MEX_FAILED;
}

// 29
static long __stdcall  Y_SetWhiteTable(
unsigned __int64 CamGUID,
void * pTable
)
{
  return MEX_FAILED;
}

// 30
static long __stdcall  Y_SetImageWhiteBalance(
unsigned __int64 CamGUID,
double Red,
double Green,
double Blue
)
{
  return MEX_FAILED;
}

// 31
static long __stdcall  Y_SetWhiteBalance(
unsigned __int64 CamGUID,
double Red,
double Green,
double Blue
)
{
  return MEX_FAILED;
}

// 32
static long __stdcall  Y_GetCurrentWhiteBalance(
unsigned __int64 CamGUID,
double * pRed,
double * pGreen,
double * pBlue
)
{
  return MEX_FAILED;
}

// 33
static long __stdcall  Y_Dispose(
unsigned __int64 CamGUID,
void * pV,
unsigned long code
)
{
  return MEX_FAILED;
}

// 34
static long __stdcall  Y_SetPeltier(
unsigned __int64 CamGUID,
unsigned char value,
BOOL bOn
)
{
  return MEX_FAILED;
}

// 35
static long __stdcall  Y_ActivatePeltier(
unsigned __int64 CamGUID,
BOOL bActive
)
{
  return MEX_FAILED;
}

// 36
static unsigned long __stdcall  Y_MSecToTicks(
unsigned __int64 CamGUID,
unsigned long msec,
mexAcqMode am,
unsigned long speed_code
)
{
  return 0;
}

// 37
static unsigned long __stdcall  Y_TicksToMSec(
unsigned __int64 CamGUID,
unsigned long ticks,
mexAcqMode am,
unsigned long speed_code
)
{
  return 0;
}

// 38
static mexTt __stdcall  Y_MSecToTt(
unsigned __int64 CamGUID,
unsigned long msec,
mexAcqMode am,
unsigned long speed_code
)
{
  return Tt;
}

// 39
static unsigned long __stdcall  Y_TtToMSec(
unsigned __int64 CamGUID,
mexTt T2,
mexAcqMode am,
unsigned long speed_code
)
{
  return 0;
}

// 40
static unsigned long __stdcall  Y_TtToMicroSeconds(
unsigned __int64 CamGUID,
mexTt T2,
mexAcqMode am,
unsigned long speed_code
)
{
  return 0;
}

// 41
static mexTt __stdcall  Y_MicroSecondsToTt(
unsigned __int64 CamGUID,
unsigned long MicroSeconds,
mexAcqMode am,
unsigned long speed_code,
int BinningCorrection
)
{
  return Tt;
}

// 42
static mexTt __stdcall  Y_NanoSecondsToTt(
unsigned __int64 CamGUID,
unsigned __int64 NanoSeconds,
mexAcqMode am,
unsigned long speed_code,
int BinningCorrection
)
{
  return Tt;
}

// 43
static unsigned __int64 __stdcall  Y_TtToNanoSeconds(
unsigned __int64 CamGUID,
mexTt T2,
mexAcqMode am,
unsigned long speed_code
)
{
  return 0;
}

// 44
static unsigned long __stdcall  Y_MicroSecondsToTicks(
unsigned __int64 CamGUID,
unsigned long Microseconds,
mexAcqMode am,
unsigned long speed_code
)
{
  return 0;
}

// 45
static unsigned long __stdcall  Y_TicksToMicroSeconds(
unsigned __int64 CamGUID,
unsigned long Ticks,
mexAcqMode am,
unsigned long speed_code
)
{
  return 0;
}

// 46
static unsigned long __stdcall  Y_SetExposureTime(
unsigned __int64 CamGUID,
unsigned long Microseconds
)
{
  return 0;
}
// 47
static unsigned long __stdcall  Y_SetExposureTime2(
unsigned __int64 CamGUID,
unsigned long Microseconds,
BOOL keepBrightness
)
{
  return 0;
}

// 47
static unsigned long __stdcall  Y_GetExposureTime(
unsigned __int64 CamGUID
)
{
  return 0;
}

// 48
static long __stdcall  Y_SetDIBLuts(
unsigned __int64 CamGUID,
unsigned char* pLutBW,
unsigned char* pLutRed,
unsigned char* pLutGreen,
unsigned char* pLutBlue,
int LutLength
)
{
  return MEX_FAILED;
}

// 49
static long __stdcall  Y_SetScannerPositions(
unsigned __int64 CamGUID,
mexScannerPositions * pSP
)
{
  return MEX_FAILED;
}

// 50
static long __stdcall  Y_GetScannerPositions(
unsigned __int64 CamGUID,
mexScannerPositions * pSP
)
{
  return MEX_FAILED;
}

// 51
static long __stdcall  Y_ResetScannerPositions(
unsigned __int64 CamGUID
)
{
  return MEX_FAILED;
}

// 52
static long __stdcall  Y_CalibrateScanner(
unsigned __int64 CamGUID,
mexAcquisParams * pAcqPars,
mexPiezoCalibSet * pCalibSet,
mexPiezoCB pCB,
unsigned long User
)
{
  return MEX_FAILED;
}

// 53
static long __stdcall  Y_SetEqualizer(
unsigned __int64 CamGUID,
mexEqualizer * pEQ
)
{
  return MEX_FAILED;
}

// 54
static long __stdcall  Y_GetEqualizer(
unsigned __int64 CamGUID,
mexEqualizer * pEQ
)
{
  return MEX_FAILED;
}

// 55
static long __stdcall  Y_ActivateTriggerOut(
unsigned __int64 CamGUID,
mexTriggerOut * pTOut
)
{
  return MEX_FAILED;
}

// 56
static long __stdcall  Y_SetTriggerOut(
unsigned __int64 CamGUID,
unsigned int Level
)
{
  return MEX_FAILED;
}

// 57
static long __stdcall  Y_GetTriggerOut(
unsigned __int64 CamGUID,
unsigned int * pLevel
)
{
  return MEX_FAILED;
}

// 58
static long __stdcall  Y_ActivateTriggerIn(
unsigned __int64 CamGUID,
unsigned int active
)
{
  return MEX_FAILED;
}

// 59
static long __stdcall  Y_SetGain(
unsigned __int64 CamGUID,
double Gain
)
{
  return MEX_FAILED;
}

// 60
static long __stdcall  Y_GetGain(
unsigned __int64 CamGUID,
double * pGain
)
{
  return MEX_FAILED;
}

// 61
static long __stdcall  Y_ActivateGammaProcessing(
unsigned __int64 CamGUID,
mexGamma * pGamma
)
{
  return MEX_FAILED;
}

// 62
static long __stdcall  Y_SetFocusCallback(
unsigned __int64 CamGUID,
mexFocus * pFocus,
unsigned long FocusUser
)
{
  return MEX_FAILED;
}

// 63
static long __stdcall  Y_GetOptimalExposureTime(
unsigned __int64 CamGUID,
mexExposureCtrl * pExpCtrl,
mexAcquisParams * pAcqPars,
unsigned long User
)
{
  return MEX_FAILED;
}

// 64
static long __stdcall  Y_ActivateExposureControl(
unsigned __int64 CamGUID,
mexExposureCtrl * pExpCtrl,
unsigned long ExpCrtlUser
)
{
  return MEX_FAILED;
}

// 65
static long __stdcall  Y_EnableExposureReports(
unsigned __int64 CamGUID,
mexExpCtrlCB pCB,
unsigned long User
)
{
  return MEX_FAILED;
}

// 66
static long __stdcall  Y_ActivateSaturationControl(
unsigned __int64 CamGUID,
mexSaturationCtrl * pSC
)
{
  return MEX_FAILED;
}

// 67
static long __stdcall  Y_CalibrateColors(
unsigned __int64 CamGUID,
mexMonitorType MonitorType,
double rgbchart[ 24 ][ 3 ],
double colmat[ 3 ][ 3 ],
mexColorQuality * pCQ
)
{
  return MEX_FAILED;
}

// 68
static long __stdcall  Y_SetColorMatrix(
unsigned __int64 CamGUID,
double colmat[ 3 ][ 3 ]
)
{
  return MEX_FAILED;
}

// 69
static long __stdcall  Y_ResetColorMatrix(
unsigned __int64 CamGUID
)
{
  return MEX_FAILED;
}

// 70
static long __stdcall  Y_UpdateFirmware(
unsigned __int64 CamGUID,
mexProgressProc pCallBack,
unsigned long UserValue
)
{
  return MEX_FAILED;
}

// 71
static long __stdcall  Y_GetFirmwareVersionFromData(
unsigned __int64 CamGUID,
mexVERSION * pFWV
)
{
  return MEX_FAILED;
}

// 72
static long __stdcall  Y_GetFirmwareVersionFromCamera(
unsigned __int64 CamGUID,
mexVERSION * pFWV
)
{
  return MEX_FAILED;
}

// 70
static long __stdcall  Y_UpdateFirmware2(
unsigned __int64 CamGUID,
mexProgressProc pCallBack,
unsigned long UserValue,
void * pInfo
)
{
  return MEX_FAILED;
}

// 71
static long __stdcall  Y_GetFirmwareVersionFromData2(
unsigned __int64 CamGUID,
mexVERSION * pFWV,
void * pInfo
)
{
  return MEX_FAILED;
}

// 72
static long __stdcall  Y_GetFirmwareVersionFromCamera2(
unsigned __int64 CamGUID,
mexVERSION * pFWV,
void * pInfo
)
{
  return MEX_FAILED;
}
// 73
static BOOL __stdcall  Y_IsLiveSupported(
unsigned __int64 CamGUID,
mexAcqMode amode
)
{
  return MEX_FAILED;
}

// 74
static long __stdcall Y_GetMeasuredValue(unsigned __int64 CamGUID,int sensorType,unsigned int index, void* pV)
{
  return MEX_FAILED;
}
// 75
static long __stdcall Y_SetWhiteBalanceFromRect(unsigned __int64 CamGUID, RECT* pRect)
{
  return MEX_FAILED;
}
// 76
static long __stdcall Y_Init2(mexInitCB pCB,unsigned long UserValue, unsigned long options, unsigned long* pSuccess)
{
  return MEX_FAILED;
}

// 77
static unsigned long __stdcall  Y_MSecToTicks4CT3(
unsigned __int64 CamGUID,
unsigned long msec,
mexAcqMode am,
unsigned long speed_code,
unsigned long imgdimx
)
{
  return 0;
}

// 78
static unsigned long __stdcall  Y_TicksToMSec4CT3(
unsigned __int64 CamGUID,
unsigned long ticks,
mexAcqMode am,
unsigned long speed_code,
unsigned long imgdimx
)
{
  return 0;
}
// 79
static long __stdcall Y_SearchBlemishes(mexImg * pImg , mexBlemishSearch* pSearchPars, mexBlemishData * pBlemishData )
{
  return MEX_FAILED;
}

static long  __stdcall Y_internalUSBUpdateFirmware(unsigned __int64 CamGUID ,
                                                   unsigned char* pFirmware,
                                                   unsigned long firmwareSize, 
                                                   mexComponentInfo * pInf,
                                                   mexProgressProc pProc,
                                                   unsigned long User)
{
  return MEX_FAILED;
}

static long  __stdcall Y_USBUpdateFirmware(unsigned __int64 CamGUID , int components)
{
  return MEX_FAILED;
}

static long __stdcall Y_USBVersionFromCamera(unsigned __int64 CamGUID,mexUSBFirmwareVersion* pVer)
{
  return MEX_FAILED;
}

static long __stdcall Y_USBVersionFromData(unsigned __int64 CamGUID,mexUSBFirmwareVersion* pVer)
{
  return MEX_FAILED;
}

static long __stdcall  Y_GetColorMatrix(unsigned __int64 CamGUID,double colmat[ 3 ][ 3 ])
{
  return MEX_FAILED;
}


// 0
static MEX_VAR(FindCameras);
// 1
static MEX_VAR(FindCamerasEx);
// 2
static MEX_VAR(GetCameraType);
// 3
static MEX_VAR(Init);
// 4
static MEX_VAR(Exit);
// 5
static MEX_VAR(OpenCamera);
// 6
static MEX_VAR(CloseCamera);
// 7
static MEX_VAR(IsOpen);
// 8
static MEX_VAR(GetVersion);
// 9
static MEX_VAR(GetInfo);
// 10
static MEX_VAR(GetSerialNumberString);
// 11
static MEX_VAR(GetCameraInfo);
// 12
static MEX_VAR(GetCameraTypeInfo);
// 13
static MEX_VAR(GetCameraTypeSummary);
// 14
static MEX_VAR(GetAcquisitionInfo);
// 15
static MEX_VAR(IsAcquisitionModeSupported);
// 16
static MEX_VAR(Acquire);
// 17
static MEX_VAR(Grab);
// 18
static MEX_VAR(ModifyLiveAcquisParams);
// 19
static MEX_VAR(AbortAcquisition);
// 20
static MEX_VAR(CloseImageTransfer);
// 21
static MEX_VAR(FreeInternalMemory);
// 22
static MEX_VAR(GetBlackRef);
// 23
static MEX_VAR(GetBlackRefLength);
// 24
static MEX_VAR(SaveBlackRef);
// 25
static MEX_VAR(RestoreBlackRef);
// 26
static MEX_VAR(GetWhiteRef);
// 27
static MEX_VAR(GetWhiteRefLength);
// 28
static MEX_VAR(GetWhiteTable);
// 29
static MEX_VAR(SetWhiteTable);
// 30
static MEX_VAR(SetImageWhiteBalance);
// 31
static MEX_VAR(SetWhiteBalance);
// 32
static MEX_VAR(GetCurrentWhiteBalance);
// 33
static MEX_VAR(Dispose);
// 34
static MEX_VAR(SetPeltier);
// 35
static MEX_VAR(ActivatePeltier);
// 36
static MEX_VAR(MSecToTicks);
// 37
static MEX_VAR(TicksToMSec);
// 38
static MEX_VAR(MSecToTt);
// 39
static MEX_VAR(TtToMSec);
// 40
static MEX_VAR(TtToMicroSeconds);
// 41
static MEX_VAR(MicroSecondsToTt);
// 42
static MEX_VAR(NanoSecondsToTt);
// 43
static MEX_VAR(TtToNanoSeconds);
// 44
static MEX_VAR(MicroSecondsToTicks);
// 45
static MEX_VAR(TicksToMicroSeconds);
// 46
static MEX_VAR(SetExposureTime);
// 47
static MEX_VAR(SetExposureTime2);
// 48
static MEX_VAR(GetExposureTime);
// 49
static MEX_VAR(SetDIBLuts);
// 50
static MEX_VAR(SetScannerPositions);
// 51
static MEX_VAR(GetScannerPositions);
// 52
static MEX_VAR(ResetScannerPositions);
// 53
static MEX_VAR(CalibrateScanner);
// 54
static MEX_VAR(SetEqualizer);
// 55
static MEX_VAR(GetEqualizer);
// 56
static MEX_VAR(ActivateTriggerOut);
// 57
static MEX_VAR(SetTriggerOut);
// 58
static MEX_VAR(GetTriggerOut);
// 59
static MEX_VAR(ActivateTriggerIn);
// 60
static MEX_VAR(SetGain);
// 61
static MEX_VAR(GetGain);
// 62
static MEX_VAR(ActivateGammaProcessing);
// 63
static MEX_VAR(SetFocusCallback);
// 64
static MEX_VAR(GetOptimalExposureTime);
// 65
static MEX_VAR(ActivateExposureControl);
// 66
static MEX_VAR(EnableExposureReports);
// 67
static MEX_VAR(ActivateSaturationControl);
// 68
static MEX_VAR(CalibrateColors);
// 69
static MEX_VAR(SetColorMatrix);
// 70
static MEX_VAR(ResetColorMatrix);
// 71
static MEX_VAR(UpdateFirmware);
// 72
static MEX_VAR(GetFirmwareVersionFromData);
// 73
static MEX_VAR(GetFirmwareVersionFromCamera);
// 74
static MEX_VAR(UpdateFirmware2);
// 75
static MEX_VAR(GetFirmwareVersionFromData2);
// 76
static MEX_VAR(GetFirmwareVersionFromCamera2);
// 77
static MEX_VAR(IsLiveSupported);
// 78
static MEX_VAR(GetMeasuredValue);
// 79 
static MEX_VAR(SetWhiteBalanceFromRect);
// 80
static MEX_VAR(Init2);
// 81
static MEX_VAR(MSecToTicks4CT3);
// 82
static MEX_VAR(TicksToMSec4CT3);
// 83
static MEX_VAR(SearchBlemishes);
// 84
static MEX_VAR(internalUSBUpdateFirmware);
// 85
static MEX_VAR(USBVersionFromCamera);
// 86
static MEX_VAR(USBVersionFromData);

// 87
static MEX_VAR(GetColorMatrix);

static MEXACC xacc[]=
{
// 0
  MEX_CS(FindCameras)
// 1
  ,MEX_CS(FindCamerasEx)
// 2
  ,MEX_CS(GetCameraType)
// 3
  ,MEX_CS(Init)
// 4
  ,MEX_CS(Exit)
// 5
  ,MEX_CS(OpenCamera)
// 6
  ,MEX_CS(CloseCamera)
// 7
  ,MEX_CS(IsOpen)
// 8
  ,MEX_CS(GetVersion)
// 9
  ,MEX_CS(GetInfo)
// 10
  ,MEX_CS(GetSerialNumberString)
// 11
  ,MEX_CS(GetCameraInfo)
// 12
  ,MEX_CS(GetCameraTypeInfo)
// 13
  ,MEX_CS(GetCameraTypeSummary)
// 14
  ,MEX_CS(GetAcquisitionInfo)
// 15
  ,MEX_CS(IsAcquisitionModeSupported)
// 16
  ,MEX_CS(Acquire)
// 17
  ,MEX_CS(Grab)
// 18
  ,MEX_CS(ModifyLiveAcquisParams)
// 19
  ,MEX_CS(AbortAcquisition)
// 20
  ,MEX_CS(CloseImageTransfer)
// 21
  ,MEX_CS(FreeInternalMemory)
// 22
  ,MEX_CS(GetBlackRef)
// 23
  ,MEX_CS(GetBlackRefLength)
// 24
  ,MEX_CS(SaveBlackRef)
// 25
  ,MEX_CS(RestoreBlackRef)
// 26
  ,MEX_CS(GetWhiteRef)
// 27
  ,MEX_CS(GetWhiteRefLength)
// 28
  ,MEX_CS(GetWhiteTable)
// 29
  ,MEX_CS(SetWhiteTable)
// 30
  ,MEX_CS(SetImageWhiteBalance)
// 31
  ,MEX_CS(SetWhiteBalance)
// 32
  ,MEX_CS(GetCurrentWhiteBalance)
// 33
  ,MEX_CS(Dispose)
// 34
  ,MEX_CS(SetPeltier)
// 35
  ,MEX_CS(ActivatePeltier)
// 36
  ,MEX_CS(MSecToTicks)
// 37
  ,MEX_CS(TicksToMSec)
// 38
  ,MEX_CS(MSecToTt)
// 39
  ,MEX_CS(TtToMSec)
// 40
  ,MEX_CS(TtToMicroSeconds)
// 41
  ,MEX_CS(MicroSecondsToTt)
// 42
  ,MEX_CS(NanoSecondsToTt)
// 43
  ,MEX_CS(TtToNanoSeconds)
// 44
  ,MEX_CS(MicroSecondsToTicks)
// 45
  ,MEX_CS(TicksToMicroSeconds)
// 46
  ,MEX_CS(SetExposureTime)
// 47
  ,MEX_CS(SetExposureTime2)
// 48
  ,MEX_CS(GetExposureTime)
// 49
  ,MEX_CS(SetDIBLuts)
// 50
  ,MEX_CS(SetScannerPositions)
// 51
  ,MEX_CS(GetScannerPositions)
// 52
  ,MEX_CS(ResetScannerPositions)
// 53
  ,MEX_CS(CalibrateScanner)
// 54
  ,MEX_CS(SetEqualizer)
// 55
  ,MEX_CS(GetEqualizer)
// 56
  ,MEX_CS(ActivateTriggerOut)
// 57
  ,MEX_CS(SetTriggerOut)
// 58
  ,MEX_CS(GetTriggerOut)
// 59
  ,MEX_CS(ActivateTriggerIn)
// 60
  ,MEX_CS(SetGain)
// 61
  ,MEX_CS(GetGain)
// 62
  ,MEX_CS(ActivateGammaProcessing)
// 63
  ,MEX_CS(SetFocusCallback)
// 64
  ,MEX_CS(GetOptimalExposureTime)
// 65
  ,MEX_CS(ActivateExposureControl)
// 66
  ,MEX_CS(EnableExposureReports)
// 67
  ,MEX_CS(ActivateSaturationControl)
// 68
  ,MEX_CS(CalibrateColors)
// 69
  ,MEX_CS(SetColorMatrix)
// 70
  ,MEX_CS(ResetColorMatrix)
// 71
  ,MEX_CS(UpdateFirmware)
// 72
  ,MEX_CS(GetFirmwareVersionFromData)
// 73
  ,MEX_CS(GetFirmwareVersionFromCamera)
// 74
  ,MEX_CS(UpdateFirmware2)
// 75
  ,MEX_CS(GetFirmwareVersionFromData2)
// 76
  ,MEX_CS(GetFirmwareVersionFromCamera2)
// 77
  ,MEX_CS(IsLiveSupported)
// 78  
  ,MEX_CS(GetMeasuredValue)
// 79
  ,MEX_CS(SetWhiteBalanceFromRect)
// 80
  ,MEX_CS(Init2)
// 81
  ,MEX_CS(MSecToTicks4CT3)
// 82
  ,MEX_CS(TicksToMSec4CT3)
// 83
  ,MEX_CS(SearchBlemishes)
// 84
  ,MEX_CS(internalUSBUpdateFirmware)
// 85
  ,MEX_CS(USBVersionFromCamera)
// 86
  ,MEX_CS(USBVersionFromData)
// 87
  ,MEX_CS(GetColorMatrix)
};


// MexCamJ functions ------------------------------------

// 0
long __stdcall mexFindCameras( unsigned int* pNumber,unsigned __int64 GUID_List[] )
{
  return (*pfnmexFindCameras)(pNumber, GUID_List);
}

// 1
long __stdcall mexFindCamerasEx( const unsigned __int64 GUID_Mask,unsigned int * pNumber,unsigned __int64 GUID_List[] )
{
  return (*pfnmexFindCamerasEx)(GUID_Mask, pNumber, GUID_List);
}

// 2
mexCamType __stdcall mexGetCameraType( unsigned __int64 CamGUID )
{
  return (*pfnmexGetCameraType)(CamGUID);
}

// 3
long __stdcall mexInit( mexInitCB pCB,unsigned long UserValue )
{
  return (*pfnmexInit)(pCB,UserValue);
}

// 4
void __stdcall mexExit(  )
{
  (*pfnmexExit)();
}

// 5
long __stdcall mexOpenCamera( unsigned __int64 CamGUID )
{
  return (*pfnmexOpenCamera)(CamGUID);
}

// 6
long __stdcall mexCloseCamera( unsigned __int64 CamGUID )
{
  return (*pfnmexCloseCamera)(CamGUID);
}

// 7
BOOL __stdcall mexIsOpen( unsigned __int64 CamGUID )
{
  return (*pfnmexIsOpen)(CamGUID);
}

// 8
long __stdcall mexGetVersion( mexCOMPONENT comp,mexVERSION * pVer )
{
  return (*pfnmexGetVersion)(comp,pVer);
}

// 9
long __stdcall mexGetInfo( unsigned __int64 CamGUID,mexInfo * pmexInfo )
{
  return (*pfnmexGetInfo)(CamGUID, pmexInfo);
}

// 10
long __stdcall mexGetSerialNumberString( unsigned __int64 CamGUID,char szString[ 16 ] )
{
  return (*pfnmexGetSerialNumberString)(CamGUID, szString);
}

// 11
long __stdcall mexGetCameraInfo( unsigned __int64 CamGUID,mexCamInfo * pCamInfo )
{
  return (*pfnmexGetCameraInfo)(CamGUID, pCamInfo);
}

// 12
long __stdcall mexGetCameraTypeInfo( int type,int subtype,mexCameraTypeSummary* pCT )
{
  return (*pfnmexGetCameraTypeInfo)(type, subtype, pCT);
}

// 13
long __stdcall mexGetCameraTypeSummary( unsigned __int64 CamGUID,mexCameraTypeSummary * pTypeSummary )
{
  return (*pfnmexGetCameraTypeSummary)(CamGUID, pTypeSummary);
}

// 14
long __stdcall mexGetAcquisitionInfo( unsigned __int64 CamGUID,mexAcquisParams * pPars,mexImageInfo * pImgInfo )
{
  return (*pfnmexGetAcquisitionInfo)(CamGUID, pPars, pImgInfo);
}

// 15
BOOL __stdcall mexIsAcquisitionModeSupported( unsigned __int64 CamGUID,mexAcqMode amode )
{
  return (*pfnmexIsAcquisitionModeSupported)(CamGUID, amode);
}

// 16
long __stdcall mexAcquire( unsigned __int64 CamGUID,mexAcquisParams* pPars,mexImg* imgList[],unsigned long listSize )
{
  return (*pfnmexAcquire)(CamGUID, pPars, imgList, listSize);
}

// 17
long __stdcall mexGrab( unsigned __int64 CamGUID,mexAcquisParams* pPars,mexImg* pImg )
{
  return (*pfnmexGrab)(CamGUID, pPars, pImg);
}

// 18
long __stdcall mexModifyLiveAcquisParams( unsigned __int64 CamGUID,mexAcquisParams* pPars )
{
  return (*pfnmexModifyLiveAcquisParams)(CamGUID, pPars);
}

// 19
long __stdcall mexAbortAcquisition( unsigned __int64 CamGUID )
{
  return (*pfnmexAbortAcquisition)(CamGUID);
}

// 20
long __stdcall mexCloseImageTransfer( unsigned __int64 CamGUID )
{
  return (*pfnmexCloseImageTransfer)(CamGUID);
}

// 21
long __stdcall mexFreeInternalMemory( unsigned __int64 CamGUID,int parts )
{
  return (*pfnmexFreeInternalMemory)(CamGUID, parts);
}

// 22
long __stdcall mexGetBlackRef( unsigned __int64 CamGUID,mexProgressProc pCallBack,DWORD dwData )
{
  return (*pfnmexGetBlackRef)(CamGUID, pCallBack, dwData);
}

// 23
long __stdcall mexGetBlackRefLength( unsigned __int64 CamGUID,unsigned int * pLength )
{
  return (*pfnmexGetBlackRefLength)(CamGUID, pLength);
}

// 24
long __stdcall mexSaveBlackRef( unsigned __int64 CamGUID,void * ref )
{
  return (*pfnmexSaveBlackRef)(CamGUID, ref);
}

// 25
long __stdcall mexRestoreBlackRef( unsigned __int64 CamGUID,void * ref )
{
  return (*pfnmexRestoreBlackRef)(CamGUID, ref);
}

// 26
long __stdcall mexGetWhiteRef( unsigned __int64 CamGUID,unsigned long exposureTicks,mexProgressProc progressProc,unsigned long progressUser )
{
  return (*pfnmexGetWhiteRef)(CamGUID, exposureTicks, progressProc, progressUser);
}

// 27
long __stdcall mexGetWhiteRefLength( unsigned __int64 CamGUID,unsigned int * pLength )
{
  return (*pfnmexGetWhiteRefLength)(CamGUID, pLength);
}

// 28
long __stdcall mexGetWhiteTable( unsigned __int64 CamGUID,void * pTable )
{
  return (*pfnmexGetWhiteTable)(CamGUID,pTable);
}

// 29
long __stdcall mexSetWhiteTable( unsigned __int64 CamGUID,void * pTable )
{
  return (*pfnmexSetWhiteTable)(CamGUID, pTable);
}

// 30
long __stdcall mexSetImageWhiteBalance( unsigned __int64 CamGUID,double Red,double Green,double Blue )
{
  return (*pfnmexSetImageWhiteBalance)(CamGUID, Red, Green, Blue);
}

// 31
long __stdcall mexSetWhiteBalance( unsigned __int64 CamGUID,double Red,double Green,double Blue )
{
  return (*pfnmexSetWhiteBalance)(CamGUID, Red, Green, Blue);
}

// 32
long __stdcall mexGetCurrentWhiteBalance( unsigned __int64 CamGUID,double * pRed,double * pGreen,double * pBlue )
{
  return (*pfnmexGetCurrentWhiteBalance)(CamGUID, pRed, pGreen, pBlue);
}

// 33
long __stdcall mexDispose( unsigned __int64 CamGUID,void * pV,unsigned long code )
{
  return (*pfnmexDispose)(CamGUID, pV, code);
}

// 34
long __stdcall mexSetPeltier( unsigned __int64 CamGUID,unsigned char value,BOOL bOn )
{
  return (*pfnmexSetPeltier)(CamGUID, value, bOn);
}

// 35
long __stdcall mexActivatePeltier( unsigned __int64 CamGUID,BOOL bActive )
{
  return (*pfnmexActivatePeltier)(CamGUID, bActive);
}

// 36
unsigned long __stdcall mexMSecToTicks( unsigned __int64 CamGUID,unsigned long msec,mexAcqMode am,unsigned long speed_code )
{
  return (*pfnmexMSecToTicks)(CamGUID, msec,am,speed_code);
}

// 37
unsigned long __stdcall mexTicksToMSec( unsigned __int64 CamGUID,unsigned long ticks,mexAcqMode am,unsigned long speed_code )
{
  return (*pfnmexTicksToMSec)(CamGUID, ticks,am,speed_code);
}

// 38
mexTt __stdcall mexMSecToTt( unsigned __int64 CamGUID,unsigned long msec,mexAcqMode am,unsigned long speed_code )
{
  return (*pfnmexMSecToTt)(CamGUID, msec,am,speed_code);
}

// 39
unsigned long __stdcall mexTtToMSec( unsigned __int64 CamGUID,mexTt T2,mexAcqMode am,unsigned long speed_code )
{
  return (*pfnmexTtToMSec)(CamGUID, T2, am, speed_code);
}

// 40
unsigned long __stdcall mexTtToMicroSeconds( unsigned __int64 CamGUID,mexTt T2,mexAcqMode am,unsigned long speed_code )
{
  return (*pfnmexTtToMicroSeconds)(CamGUID, T2, am, speed_code);
}

// 41
mexTt __stdcall mexMicroSecondsToTt( unsigned __int64 CamGUID,unsigned long MicroSeconds,mexAcqMode am,unsigned long speed_code,int BinningCorrection )
{
  return (*pfnmexMicroSecondsToTt)(CamGUID, MicroSeconds, am, speed_code, BinningCorrection);
}

// 42
mexTt __stdcall mexNanoSecondsToTt( unsigned __int64 CamGUID,unsigned __int64 NanoSeconds,mexAcqMode am,unsigned long speed_code,int BinningCorrection )
{
  return (*pfnmexNanoSecondsToTt)(CamGUID, NanoSeconds, am, speed_code, BinningCorrection);
}

// 43
unsigned __int64 __stdcall mexTtToNanoSeconds( unsigned __int64 CamGUID,mexTt T2,mexAcqMode am,unsigned long speed_code )
{
  return (*pfnmexTtToNanoSeconds)(CamGUID, T2, am, speed_code);
}

// 44
unsigned long __stdcall mexMicroSecondsToTicks( unsigned __int64 CamGUID,unsigned long Microseconds,mexAcqMode am,unsigned long speed_code )
{
  return (*pfnmexMicroSecondsToTicks)(CamGUID, Microseconds, am,speed_code);
}

// 45
unsigned long __stdcall mexTicksToMicroSeconds( unsigned __int64 CamGUID,unsigned long Ticks,mexAcqMode am,unsigned long speed_code )
{
  return (*pfnmexTicksToMicroSeconds)(CamGUID, Ticks, am, speed_code);
}

// 46
unsigned long __stdcall mexSetExposureTime( unsigned __int64 CamGUID,unsigned long Microseconds )
{
  return (*pfnmexSetExposureTime)(CamGUID, Microseconds);
}

// 47
unsigned long __stdcall mexSetExposureTime2( unsigned __int64 CamGUID,unsigned long Microseconds, BOOL keepBrightness )
{
  return (*pfnmexSetExposureTime2)(CamGUID, Microseconds,keepBrightness);
}

// 48
unsigned long __stdcall mexGetExposureTime( unsigned __int64 CamGUID )
{
  return (*pfnmexGetExposureTime)(CamGUID);
}

// 49
long __stdcall mexSetDIBLuts( unsigned __int64 CamGUID,unsigned char* pLutBW,unsigned char* pLutRed,unsigned char* pLutGreen,unsigned char* pLutBlue,int LutLength )
{
  return (*pfnmexSetDIBLuts)(CamGUID, pLutBW, pLutRed, pLutGreen, pLutBlue, LutLength);
}

// 50
long __stdcall mexSetScannerPositions( unsigned __int64 CamGUID,mexScannerPositions * pSP )
{
  return (*pfnmexSetScannerPositions)(CamGUID, pSP);
}

// 51
long __stdcall mexGetScannerPositions( unsigned __int64 CamGUID,mexScannerPositions * pSP )
{
  return (*pfnmexGetScannerPositions)(CamGUID, pSP);
}

// 52
long __stdcall mexResetScannerPositions( unsigned __int64 CamGUID )
{
  return (*pfnmexResetScannerPositions)(CamGUID);
}

// 53
long __stdcall mexCalibrateScanner( unsigned __int64 CamGUID,mexAcquisParams * pAcqPars,mexPiezoCalibSet * pCalibSet,mexPiezoCB pCB,unsigned long User )
{
  return (*pfnmexCalibrateScanner)(CamGUID, pAcqPars, pCalibSet, pCB, User );
}

// 54
long __stdcall mexSetEqualizer( unsigned __int64 CamGUID,mexEqualizer * pEQ )
{
  return (*pfnmexSetEqualizer)(CamGUID, pEQ);
}

// 55
long __stdcall mexGetEqualizer( unsigned __int64 CamGUID,mexEqualizer * pEQ )
{
  return (*pfnmexGetEqualizer)(CamGUID, pEQ);
}

// 56
long __stdcall mexActivateTriggerOut( unsigned __int64 CamGUID,mexTriggerOut * pTOut )
{
  return (*pfnmexActivateTriggerOut)(CamGUID, pTOut);
}

// 57
long __stdcall mexSetTriggerOut( unsigned __int64 CamGUID,unsigned int Level )
{
  return (*pfnmexSetTriggerOut)(CamGUID, Level);
}

// 58
long __stdcall mexGetTriggerOut( unsigned __int64 CamGUID,unsigned int * pLevel )
{
  return (*pfnmexGetTriggerOut)(CamGUID, pLevel);
}

// 59
long __stdcall mexActivateTriggerIn( unsigned __int64 CamGUID,unsigned int active )
{
  return (*pfnmexActivateTriggerIn)(CamGUID, active);
}

// 60
long __stdcall mexSetGain( unsigned __int64 CamGUID,double Gain )
{
  return (*pfnmexSetGain)(CamGUID, Gain);
}

// 61
long __stdcall mexGetGain( unsigned __int64 CamGUID,double * pGain )
{
  return (*pfnmexGetGain)(CamGUID, pGain);
}

// 62
long __stdcall mexActivateGammaProcessing( unsigned __int64 CamGUID,mexGamma * pGamma )
{
  return (*pfnmexActivateGammaProcessing)(CamGUID, pGamma);
}

// 63
long __stdcall mexSetFocusCallback( unsigned __int64 CamGUID,mexFocus * pFocus,unsigned long FocusUser )
{
  return (*pfnmexSetFocusCallback)(CamGUID, pFocus, FocusUser);
}

// 64
long __stdcall mexGetOptimalExposureTime( unsigned __int64 CamGUID,mexExposureCtrl * pExpCtrl,mexAcquisParams * pAcqPars,unsigned long User )
{
  return (*pfnmexGetOptimalExposureTime)(CamGUID, pExpCtrl, pAcqPars, User);
}

// 65
long __stdcall mexActivateExposureControl( unsigned __int64 CamGUID,mexExposureCtrl * pExpCtrl,unsigned long ExpCrtlUser )
{
  return (*pfnmexActivateExposureControl)(CamGUID, pExpCtrl, ExpCrtlUser);
}

// 66
long __stdcall mexEnableExposureReports( unsigned __int64 CamGUID,mexExpCtrlCB pCB,unsigned long User )
{
  return (*pfnmexEnableExposureReports)(CamGUID, pCB, User);
}

// 67
long __stdcall mexActivateSaturationControl( unsigned __int64 CamGUID,mexSaturationCtrl * pSC )
{
  return (*pfnmexActivateSaturationControl)(CamGUID, pSC);
}

// 68
long __stdcall mexCalibrateColors( unsigned __int64 CamGUID,mexMonitorType MonitorType,double rgbchart[ 24 ][ 3 ],double colmat[ 3 ][ 3 ],mexColorQuality * pCQ )
{
  return (*pfnmexCalibrateColors)(CamGUID, MonitorType, rgbchart, colmat, pCQ);
}

// 69
long __stdcall mexSetColorMatrix( unsigned __int64 CamGUID,double colmat[ 3 ][ 3 ] )
{
  return (*pfnmexSetColorMatrix)(CamGUID, colmat);
}

// 70
long __stdcall mexResetColorMatrix( unsigned __int64 CamGUID )
{
  return (*pfnmexResetColorMatrix)(CamGUID);
}

// 71
long __stdcall mexUpdateFirmware( unsigned __int64 CamGUID,mexProgressProc pCallBack,unsigned long UserValue )
{
  return (*pfnmexUpdateFirmware)(CamGUID, pCallBack,UserValue );
}

// 72
long __stdcall mexGetFirmwareVersionFromData( unsigned __int64 CamGUID,mexVERSION * pFWV )
{
  return (*pfnmexGetFirmwareVersionFromData)(CamGUID, pFWV);
}

// 73
long __stdcall mexGetFirmwareVersionFromCamera( unsigned __int64 CamGUID,mexVERSION * pFWV )
{
  return (*pfnmexGetFirmwareVersionFromCamera)(CamGUID, pFWV);
}
// 71
long __stdcall mexUpdateFirmware2( unsigned __int64 CamGUID,mexProgressProc pCallBack,unsigned long UserValue, void* pInfo )
{
  return (*pfnmexUpdateFirmware2)(CamGUID, pCallBack,UserValue, pInfo );
}

// 72
long __stdcall mexGetFirmwareVersionFromData2( unsigned __int64 CamGUID,mexVERSION * pFWV, void* pInfo )
{
  return (*pfnmexGetFirmwareVersionFromData2)(CamGUID, pFWV, pInfo);
}

// 73
long __stdcall mexGetFirmwareVersionFromCamera2( unsigned __int64 CamGUID,mexVERSION * pFWV, void* pInfo )
{
  return (*pfnmexGetFirmwareVersionFromCamera2)(CamGUID, pFWV, pInfo);
}

// 74
BOOL __stdcall mexIsLiveSupported( unsigned __int64 CamGUID,mexAcqMode amode )
{
  return (*pfnmexIsLiveSupported)(CamGUID, amode);
}

// 75
long __stdcall mexGetMeasuredValue(unsigned __int64 CamGUID,int sensorType,unsigned int index, void* pV)
{
  return (*pfnmexGetMeasuredValue)(CamGUID,sensorType,index,pV);
}


// 76 
long __stdcall mexSetWhiteBalanceFromRect(unsigned __int64 CamGUID,RECT* pRect)
{
  return (*pfnmexSetWhiteBalanceFromRect)(CamGUID,pRect);
}

// 77
long __stdcall mexInit2(mexInitCB pCB,unsigned long UserValue, unsigned long options,unsigned long* pSuccess)
{
  return (*pfnmexInit2)(pCB,UserValue,options,pSuccess);
}


// 78
unsigned long __stdcall mexMSecToTicks4CT3( unsigned __int64 CamGUID,unsigned long msec,mexAcqMode am,unsigned long speed_code,unsigned long imgdimx )
{
  return (*pfnmexMSecToTicks4CT3)(CamGUID, msec, am, speed_code, imgdimx);
}

// 79
unsigned long __stdcall mexTicksToMSec4CT3( unsigned __int64 CamGUID,unsigned long ticks,mexAcqMode am,unsigned long speed_code,unsigned long imgdimx )
{
  return (*pfnmexTicksToMSec4CT3)(CamGUID, ticks, am, speed_code, imgdimx);
}

// 80
long __stdcall mexSearchBlemishes(mexImg * pImg ,mexBlemishSearch* pSearchPars, mexBlemishData * pBlemishData )
{
  return (*pfnmexSearchBlemishes)(pImg,pSearchPars,pBlemishData);
}


long  __stdcall mexinternalUSBUpdateFirmware(unsigned __int64 CamGUID ,
                                             unsigned char* pFirmware,
                                             unsigned long firmwareSize, 
                                             mexComponentInfo * pInfo,
                                             mexProgressProc pProc,
                                             unsigned long User)
{
  return (*pfnmexinternalUSBUpdateFirmware)(CamGUID,pFirmware,firmwareSize,pInfo,pProc,User);
}

long  __stdcall mexUSBVersionFromCamera(unsigned __int64 CamGUID, mexUSBFirmwareVersion* pVer)
{
  return (*pfnmexUSBVersionFromCamera)(CamGUID, pVer);
}

long  __stdcall mexUSBVersionFromData(unsigned __int64 CamGUID, mexUSBFirmwareVersion* pVer)
{
  return (*pfnmexUSBVersionFromData)(CamGUID, pVer);

}

long __stdcall mexGetColorMatrix( unsigned __int64 CamGUID,double colmat[ 3 ][ 3 ] )
{
  return (*pfnmexGetColorMatrix)(CamGUID, colmat);
}
// end MexCamJ functions --------------------------------

void ResetStaticFunctionPointer(int index)
{
  if(0<=index && index < MEX_FUNCTIONCOUNT_FULL)
  {
    *xacc[index].func = xacc[index].dummy;
  }
}

void ResetStaticFunctionPointers()
{
  int i=0;
  for(i=0; i< MEX_FUNCTIONCOUNT_FULL; i++)
  {
    *xacc[i].func = xacc[i].dummy;
    xacc[i].tryload=FALSE;
  }
}

void LoadFunction(int index)
{
  if(0<=index && index < MEX_FUNCTIONCOUNT_FULL)
  {
    xacc[index].tryload=TRUE;
    *xacc[index].func=(void*)GetProcAddress(hInstDLL, xacc[index].name);
    if(*xacc[index].func==NULL)ResetStaticFunctionPointer(index);
  }
}
#pragma warning(disable : 4996) 
int __stdcall LoadMexDLL(int index,
                         const TCHAR* szPrimarySearchPath,
                         const TCHAR* szCallingModul,
                         BOOL bContinueSearch)
{
  int i=0;
  int wl=0;
  int wwl=0;
  int l=0;
  int loadedfunctions=0;
  int check=0;
  int count=0;
  HINSTANCE hInst=NULL;
  WCHAR * wszPrimarySearchPath=NULL;
  WCHAR * wszCallingModul=NULL;
  WCHAR * p = NULL;
  WCHAR wszSearchPath[_MAX_PATH];
  memset(wszSearchPath,0,sizeof(wszSearchPath));

  // conversion of calling parameter to UNICODE
  if(szPrimarySearchPath)
  {
#ifdef  UNICODE
    l=wcslen(szPrimarySearchPath)+1;// terminating zero inclusive
    wszPrimarySearchPath=(WCHAR*)malloc(l*sizeof(WCHAR));//new WCHAR[l];
    if(wszPrimarySearchPath)
    {
      wcscpy(wszPrimarySearchPath,szPrimarySearchPath);
    }
#else
    l=strlen(szPrimarySearchPath)+1;// terminating zero inclusive
    wl=MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,szPrimarySearchPath,-1,wszPrimarySearchPath,0);
    if(wl>0)
    {
      wszPrimarySearchPath=(WCHAR*)malloc(wl*sizeof(WCHAR));//new WCHAR[wl];
      wwl=MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,szPrimarySearchPath,-1,wszPrimarySearchPath,wl);
    }
#endif
  }
  if(szCallingModul)
  {
#ifdef  UNICODE
    l=wcslen(szCallingModul)+1;// terminating zero inclusive
    wszCallingModul=(WCHAR*)malloc(l*sizeof(WCHAR));//new WCHAR[l];
    if(wszCallingModul)
    {
      wcscpy(wszCallingModul,szCallingModul);
    }
#else
    l=strlen(szCallingModul)+1;// terminating zero inclusive
    wl=MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,szCallingModul,-1,wszCallingModul,0);
    if(wl>0)
    {
      wszCallingModul=(WCHAR*)malloc(wl*sizeof(WCHAR));//new WCHAR[wl];
      wwl=MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,szCallingModul,-1,wszCallingModul,wl);
    }
#endif
  }
  
  if(0<= index && index <2)
  {
    if(NULL==hInstDLL)
    {
      if(wszPrimarySearchPath!=NULL)
      {
       wcscpy(wszSearchPath,wszPrimarySearchPath);
       l=wcslen(wszSearchPath);
      }
      else if(szCallingModul!=NULL)
      {
        hInst=GetModuleHandleW(wszCallingModul);
        l=GetModuleFileNameW(hInst,wszSearchPath,_MAX_PATH);
        if(l>0)
        {
          p=wcsrchr(wszSearchPath,'\\');
          if(p && p!=&wszSearchPath[l-1])
          {
            *(p+1)=L'\0';
            wcscat(wszSearchPath,szMexDLL[index]);
            l=wcslen(wszSearchPath);
          }
        }

      }
      else
      {
        l=GetModuleFileNameW(NULL,wszSearchPath,_MAX_PATH);
        p=wcsrchr(wszSearchPath,'\\');
        if(p)
        {
          *p=L'\0';
          --l;
        }
      }

      if(l>0)  // SearchPath to use
      {
        p=wcsrchr(wszSearchPath,'\\');
        if(p==NULL)
        {
         if(_wcsicmp(wszSearchPath,szMexDLL[index]))
          wcscat(wszSearchPath,L"\\");
          wcscat(wszSearchPath,szMexDLL[index]);
        }
        else
        {
         if(wszSearchPath[l-1]!=L'\\')
         {
          if(_wcsicmp(p+1,szMexDLL[index]))
          {
            wcscat(wszSearchPath,L"\\");
            wcscat(wszSearchPath,szMexDLL[index]);
          }
         }
         else
         {
           wcscat(wszSearchPath,szMexDLL[index]);
         }


        }

        hInstDLL=LoadLibraryExW(wszSearchPath,NULL,LOAD_WITH_ALTERED_SEARCH_PATH);
        if(hInstDLL==NULL && bContinueSearch)
        {
          hInstDLL=LoadLibraryExW(szMexDLL[index],NULL,0);
        }
      }
      else // no SearchPath to use
      {
        hInstDLL=LoadLibraryExW(szMexDLL[index],NULL,0);

      }
      // free local allocated string memory
      if(wszPrimarySearchPath)
      {
        free(wszPrimarySearchPath);
        wszPrimarySearchPath=NULL;
      }
      if(wszCallingModul)
      {
        free(wszCallingModul);
        wszCallingModul=NULL;
      }


      if(hInstDLL!=NULL)
      {
        count=FUNCOUNT[index];
        for(i=0; i<=count; i++)
        {
          LoadFunction(i);

          if((check=CheckMexFunction(i,NULL,0))==2)
            loadedfunctions++;
        }
        globalindex=index;
      }
    }
    else
    {
        loadedfunctions=0;
        if(0<=globalindex && globalindex < 2)
        {
          count=FUNCOUNT[globalindex];
          for(i=0; i<=count; i++)
          {
            if((check=CheckMexFunction(i,NULL,0))==2)
              loadedfunctions++;
          }
        }
    }
  }
  return loadedfunctions;
}

void __stdcall FreeMexDLL()
{
  if(hInstDLL)
  {
    FreeLibrary(hInstDLL);
    hInstDLL=NULL;
  }
  ResetStaticFunctionPointers();
}


int __stdcall CheckMexFunction(int index,char * szFunString, int BufferLength)
{
  int  Loaded=0;
  if(0<=index && index < MEX_FUNCTIONCOUNT_FULL)
  {
    Loaded=(xacc[index].tryload ? 1 :0);
    if(szFunString)
    {
      strncpy(szFunString,xacc[index].name,BufferLength);
      szFunString[BufferLength-1]='\0';
    }
    if(*xacc[index].func != xacc[index].dummy)
      Loaded=2;
  }
  return Loaded;
}

int __stdcall CheckMexFunction2(char * szFunString, int BufferLength)
{
  int Loaded=0;
  int i=0;
  for(i=0; i< FUNCOUNT[globalindex]; i++)
  {
    if(0==strncmp(szFunString,xacc[i].name,BufferLength))
    {
      if(*xacc[i].func != xacc[i].dummy)
        Loaded=2;
      break;
    }
  }
  return Loaded;
}

#pragma warning(default : 4996) 


BOOL _stdcall GetFullPathOfLoadedMexDLL(TCHAR* szFullPath)
{
  if(szFullPath)
  {
    if(hInstDLL)
    {
      GetModuleFileName(hInstDLL,szFullPath,_MAX_PATH);
    }
  }
  return (hInstDLL!=NULL);
}
BOOL _stdcall GetFileNameOfLoadedMexDLL(TCHAR* szName, size_t buffersize)
{
  TCHAR szFull[MAX_PATH];
  if(GetFullPathOfLoadedMexDLL(&szFull[0]))
  {
    strcpy_s(szName,buffersize,PathFindFileName(&szFull[0]));
    return TRUE;
  }
 return FALSE;
}
