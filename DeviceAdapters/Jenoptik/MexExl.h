#ifndef _MEXEXL_H
#define _MEXEXL_H

#define MEX_ALTERNATIVE

#include "MexCam.h"
#include "tchar.h"
#define MEX_FUNCTIONCOUNT_FULL   88
#define MEX_FUNCTIONCOUNT_LC   80

#define MEX_VAR(name) PFNmex##name pfnmex##name = &Y_##name

#define MEX_CS(name) {FALSE,(void**)&pfnmex##name,(void*)&Y_##name,"mex"#name}

typedef  struct tagMEXACC
{
  BOOL tryload;
  void** func;
  void* dummy;
  const char* name;
} MEXACC;
// 0
typedef long (__stdcall * PFNmexFindCameras)(unsigned int *,unsigned __int64 []);
// 1
typedef long (__stdcall * PFNmexFindCamerasEx)(const unsigned __int64,unsigned int *,unsigned __int64 []);
// 2
typedef mexCamType (__stdcall * PFNmexGetCameraType)(unsigned __int64);
// 3
typedef long (__stdcall * PFNmexInit)(mexInitCB,unsigned long);
// 4
typedef void (__stdcall * PFNmexExit)(void);
// 5
typedef long (__stdcall * PFNmexOpenCamera)(unsigned __int64);
// 6
typedef long (__stdcall * PFNmexCloseCamera)(unsigned __int64);
// 7
typedef int (__stdcall * PFNmexIsOpen)(unsigned __int64);
// 8
typedef long (__stdcall * PFNmexGetVersion)(enum mexCOMPONENT, mexVERSION *);
// 9
typedef long (__stdcall * PFNmexGetInfo)(unsigned __int64, mexInfo *);
// 10
typedef long (__stdcall * PFNmexGetSerialNumberString)(unsigned __int64,char []);
// 11
typedef long (__stdcall * PFNmexGetCameraInfo)(unsigned __int64, mexCamInfo *);
// 12
typedef long (__stdcall * PFNmexGetCameraTypeInfo)(int,int, mexCameraTypeSummary *);
// 13
typedef long (__stdcall * PFNmexGetCameraTypeSummary)(unsigned __int64, mexCameraTypeSummary *);
// 14
typedef long (__stdcall * PFNmexGetAcquisitionInfo)(unsigned __int64, mexAcquisParams *, mexImageInfo *);
// 15
typedef int (__stdcall * PFNmexIsAcquisitionModeSupported)(unsigned __int64,enum mexAcqMode);
// 16
typedef long (__stdcall * PFNmexAcquire)(unsigned __int64, mexAcquisParams *, mexImg *[],unsigned long);
// 17
typedef long (__stdcall * PFNmexGrab)(unsigned __int64, mexAcquisParams *, mexImg *);
// 18
typedef long (__stdcall * PFNmexModifyLiveAcquisParams)(unsigned __int64, mexAcquisParams *);
// 19
typedef long (__stdcall * PFNmexAbortAcquisition)(unsigned __int64);
// 20
typedef long (__stdcall * PFNmexCloseImageTransfer)(unsigned __int64);
// 21
typedef long (__stdcall * PFNmexFreeInternalMemory)(unsigned __int64,int);
// 22
typedef long (__stdcall * PFNmexGetBlackRef)(unsigned __int64,mexProgressProc,unsigned long);
// 23
typedef long (__stdcall * PFNmexGetBlackRefLength)(unsigned __int64,unsigned int *);
// 24
typedef long (__stdcall * PFNmexSaveBlackRef)(unsigned __int64,void *);
// 25
typedef long (__stdcall * PFNmexRestoreBlackRef)(unsigned __int64,void *);
// 26
typedef long (__stdcall * PFNmexGetWhiteRef)(unsigned __int64,unsigned long,mexProgressProc,unsigned long);
// 27
typedef long (__stdcall * PFNmexGetWhiteRefLength)(unsigned __int64,unsigned int *);
// 28
typedef long (__stdcall * PFNmexGetWhiteTable)(unsigned __int64,void *);
// 29
typedef long (__stdcall * PFNmexSetWhiteTable)(unsigned __int64,void *);
// 30
typedef long (__stdcall * PFNmexSetImageWhiteBalance)(unsigned __int64,double,double,double);
// 31
typedef long (__stdcall * PFNmexSetWhiteBalance)(unsigned __int64,double,double,double);
// 32
typedef long (__stdcall * PFNmexGetCurrentWhiteBalance)(unsigned __int64,double *,double *,double *);
// 33
typedef long (__stdcall * PFNmexDispose)(unsigned __int64,void *,unsigned long);
// 34
typedef long (__stdcall * PFNmexSetPeltier)(unsigned __int64,unsigned char,int);
// 35
typedef long (__stdcall * PFNmexActivatePeltier)(unsigned __int64,int);
// 36
typedef unsigned long (__stdcall * PFNmexMSecToTicks)(unsigned __int64,unsigned long,enum mexAcqMode,unsigned long);
// 37
typedef unsigned long (__stdcall * PFNmexTicksToMSec)(unsigned __int64,unsigned long,enum mexAcqMode,unsigned long);
// 38
typedef  mexTt (__stdcall * PFNmexMSecToTt)(unsigned __int64,unsigned long,enum mexAcqMode,unsigned long);
// 39
typedef unsigned long (__stdcall * PFNmexTtToMSec)(unsigned __int64, mexTt,enum mexAcqMode,unsigned long);
// 40
typedef unsigned long (__stdcall * PFNmexTtToMicroSeconds)(unsigned __int64, mexTt,enum mexAcqMode,unsigned long);
// 41
typedef  mexTt (__stdcall * PFNmexMicroSecondsToTt)(unsigned __int64,unsigned long,enum mexAcqMode,unsigned long,int);
// 42
typedef  mexTt (__stdcall * PFNmexNanoSecondsToTt)(unsigned __int64,unsigned __int64,enum mexAcqMode,unsigned long,int);
// 43
typedef unsigned __int64 (__stdcall * PFNmexTtToNanoSeconds)(unsigned __int64, mexTt,enum mexAcqMode,unsigned long);
// 44
typedef unsigned long (__stdcall * PFNmexMicroSecondsToTicks)(unsigned __int64,unsigned long,enum mexAcqMode,unsigned long);
// 45
typedef unsigned long (__stdcall * PFNmexTicksToMicroSeconds)(unsigned __int64,unsigned long,enum mexAcqMode,unsigned long);
// 46
typedef unsigned long (__stdcall * PFNmexSetExposureTime)(unsigned __int64,unsigned long);
// 47
typedef unsigned long (__stdcall * PFNmexSetExposureTime2)(unsigned __int64,unsigned long,BOOL);
// 48
typedef unsigned long (__stdcall * PFNmexGetExposureTime)(unsigned __int64);
// 49
typedef long (__stdcall * PFNmexSetDIBLuts)(unsigned __int64,unsigned char *,unsigned char *,unsigned char *,unsigned char *,int);
// 50
typedef long (__stdcall * PFNmexSetScannerPositions)(unsigned __int64, mexScannerPositions *);
// 51
typedef long (__stdcall * PFNmexGetScannerPositions)(unsigned __int64, mexScannerPositions *);
// 52
typedef long (__stdcall * PFNmexResetScannerPositions)(unsigned __int64);
// 53
typedef long (__stdcall * PFNmexCalibrateScanner)(unsigned __int64, mexAcquisParams *, mexPiezoCalibSet *,mexPiezoCB,unsigned long);
// 54
typedef long (__stdcall * PFNmexSetEqualizer)(unsigned __int64, mexEqualizer *);
// 55
typedef long (__stdcall * PFNmexGetEqualizer)(unsigned __int64, mexEqualizer *);
// 56
typedef long (__stdcall * PFNmexActivateTriggerOut)(unsigned __int64, mexTriggerOut *);
// 57
typedef long (__stdcall * PFNmexSetTriggerOut)(unsigned __int64,unsigned int);
// 58
typedef long (__stdcall * PFNmexGetTriggerOut)(unsigned __int64,unsigned int *);
// 59
typedef long (__stdcall * PFNmexActivateTriggerIn)(unsigned __int64,unsigned int);
// 60
typedef long (__stdcall * PFNmexSetGain)(unsigned __int64,double);
// 61
typedef long (__stdcall * PFNmexGetGain)(unsigned __int64,double *);
// 62
typedef long (__stdcall * PFNmexActivateGammaProcessing)(unsigned __int64, mexGamma *);
// 63
typedef long (__stdcall * PFNmexSetFocusCallback)(unsigned __int64, mexFocus *,unsigned long);
// 64
typedef long (__stdcall * PFNmexGetOptimalExposureTime)(unsigned __int64, mexExposureCtrl *, mexAcquisParams *,unsigned long);
// 65
typedef long (__stdcall * PFNmexActivateExposureControl)(unsigned __int64, mexExposureCtrl *,unsigned long);
// 66
typedef long (__stdcall * PFNmexEnableExposureReports)(unsigned __int64,mexExpCtrlCB,unsigned long);
// 67
typedef long (__stdcall * PFNmexActivateSaturationControl)(unsigned __int64, mexSaturationCtrl *);
// 68
typedef long (__stdcall * PFNmexCalibrateColors)(unsigned __int64,enum mexMonitorType,double [][3],double [][3], mexColorQuality *);
// 69
typedef long (__stdcall * PFNmexSetColorMatrix)(unsigned __int64,double [][3]);
// 70
typedef long (__stdcall * PFNmexResetColorMatrix)(unsigned __int64);
// 71
typedef long (__stdcall * PFNmexUpdateFirmware)(unsigned __int64,mexProgressProc,unsigned long);
// 72
typedef long (__stdcall * PFNmexGetFirmwareVersionFromData)(unsigned __int64, mexVERSION *);
// 73
typedef long (__stdcall * PFNmexGetFirmwareVersionFromCamera)(unsigned __int64, mexVERSION *);

// 71
typedef long (__stdcall * PFNmexUpdateFirmware2)(unsigned __int64,mexProgressProc,unsigned long, void *);
// 72
typedef long (__stdcall * PFNmexGetFirmwareVersionFromData2)(unsigned __int64, mexVERSION *, void *);
// 73
typedef long (__stdcall * PFNmexGetFirmwareVersionFromCamera2)(unsigned __int64, mexVERSION *, void *);

// 74
typedef int (__stdcall * PFNmexIsLiveSupported)(unsigned __int64,enum mexAcqMode);
// 75 
typedef long (_stdcall * PFNmexGetMeasuredValue)(unsigned __int64,int,unsigned int, void *);

// 76
typedef long (_stdcall * PFNmexSetWhiteBalanceFromRect)(unsigned __int64, RECT*);

// 77
typedef long (_stdcall * PFNmexInit2)(mexInitCB,unsigned long, unsigned long,unsigned long *);

// 78
typedef unsigned long (__stdcall * PFNmexMSecToTicks4CT3)(unsigned __int64,unsigned long,enum mexAcqMode,unsigned long,unsigned long);
// 79
typedef unsigned long (__stdcall * PFNmexTicksToMSec4CT3)(unsigned __int64,unsigned long,enum mexAcqMode,unsigned long,unsigned long);
// 80
typedef long (__stdcall * PFNmexSearchBlemishes)(mexImg *,mexBlemishSearch*, mexBlemishData * );

// 81
typedef long (__stdcall * PFNmexinternalUSBUpdateFirmware)(unsigned __int64 ,
                                                           unsigned char* ,
                                                           unsigned long,
                                                           mexComponentInfo*,
                                                           mexProgressProc,
                                                           unsigned long);

// 82
typedef long (__stdcall * PFNmexUSBUpdateFirmware)(unsigned __int64 ,int );

// 83
typedef long (__stdcall * PFNmexUSBVersionFromCamera)(unsigned __int64, mexUSBFirmwareVersion* );
// 84
typedef long (__stdcall * PFNmexUSBVersionFromData)(unsigned __int64, mexUSBFirmwareVersion* );

typedef long (__stdcall * PFNmexGetColorMatrix)(unsigned __int64,double [][3]);


#ifdef __cplusplus
extern "C" {
#endif
int __stdcall LoadMexDLL(int index,
                         const TCHAR* szPrimarySearchPath,
                         const TCHAR* szCallingModul,
                         BOOL bContinueSearch);

void __stdcall FreeMexDLL();

int __stdcall CheckMexFunction(int index,char * szFunString, int BufferLength);
int __stdcall CheckMexFunction2(char * szFunString, int BufferLength);

BOOL _stdcall GetFullPathOfLoadedMexDLL(TCHAR* szFullPath);
BOOL _stdcall GetFileNameOfLoadedMexDLL(TCHAR* szName, size_t buffersize);


#ifdef __cplusplus
}
#endif

#endif

