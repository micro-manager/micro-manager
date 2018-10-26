//-----------------------------------------------------------------//
// Name        | Camera.h                    | Type: ( ) source    //
//-------------------------------------------|       (*) header    //
// Project     | PCO                         |       ( ) others    //
//-----------------------------------------------------------------//
// Platform    | PC, Windows                                       //
//-----------------------------------------------------------------//
// Environment | All 'C'-compiler used at PCO                      //
//-----------------------------------------------------------------//
// Purpose     | PCO - Error defines                               //
//-----------------------------------------------------------------//
// Author      |  FRE, PCO AG, Kelheim, Germany                    //
//-----------------------------------------------------------------//
// Revision    |  rev. 23 rel. 02                                  //
//-----------------------------------------------------------------//
// Notes       | This error defines should be used in every future //
//             | design. It is designed to hold a huge range of    //
//             | errors and warnings                               //
//-----------------------------------------------------------------//
// (c) 2002 PCO AG  *  Donaupark 11  *  D-93309 Kelheim / Germany  //
// Phone: +49 (0)9441 / 2005-0  *  Fax: +49 (0)9441 / 2005-20      //
// Email: info@pco.de  *  Web: www.pco.de                          //
//-----------------------------------------------------------------//


//-----------------------------------------------------------------//
// Revision History:                                               //
//-----------------------------------------------------------------//
// Rev.:     | Date:      | Changed:                               //
// --------- | ---------- | ---------------------------------------//
//  02.23    | 25.01.2009 |  new file, FRE                         //
//-----------------------------------------------------------------//
//  02.24    | 02.07.2010 |  added Kamlib version define, FRE      //
//-----------------------------------------------------------------//
//  02.25    | 28.11.2012 |  bugfixes for pco.edge, FRE            //
//-----------------------------------------------------------------//

#ifndef CAMERA_H
#define CAMERA_H

#define KAMLIBVERSION           251
#pragma once

#define WM_CHANGE_CAMERAVALUES WM_APP + 200

#define SET_RESULT_MUST_REINIT          1
#define SET_RESULT_MEMORY_CHANGED       2
#define SET_RESULT_SEGMENT_CHANGED      3

#define SOURCE_IS_PROPERTIESWND         1
#define SOURCE_IS_CAMERAVIEW            2
#define SOURCE_IS_RECORDER_TOOLS        3
#define SOURCE_IS_CAMWARE_VIEW          4
#define SOURCE_IS_OUTPUTWND             5
#define SOURCE_IS_RECORDER_SETTINGS     6

#define CAMERA_CP_MASK                  0x800000FF
#define CAMERA_CP_ACTIVE_SET_NAME       0x80000001
#define CAMERA_CP_DESCRIPTION           0x80000002

#define CAMERA_CP_SETTINGS_ALL          0x80000003
#define CAMERA_CP_CLOSE_CAMERA          0x80000004
#define CAMERA_CP_RESCAN_CAMERA         0x80000005
#define CAMERA_CP_ENABLE_CAMERA         0x80000006
#define CAMERA_CP_ENABLE_OUTPUTWND      0x80000007

#define CAMERA_CP_SET_PARAM_ONLY        0x00000100 // Set the parameters without transfer to camera
#define CAMERA_CP_AVOID_DIALOG_SWITCH   0x00000200 // Avoid switching of dialogs due to fast switch back and forth

//#define CAMERA_GENERAL_MASK             0xF0000000
#define CAMERA_GROUP_MASK               0xFF00
#define CAMERA_GROUP_DIVIDE             0x0100
#define CAMERA_ITEMS_MASK               0x00FF

#define CAMERA_TIMING                   0x0100
#define CAMERA_TIMING_TRIGGER           0x0001
#define CAMERA_TIMING_EXPOSURE          0x0002
#define CAMERA_TIMING_DELAY             0x0003
#define CAMERA_TIMING_FPS_EXP_MODE      0x0004
#define CAMERA_TIMING_MODULATE_MODE     0x0005
#define CAMERA_TIMING_MODULATE_NUM_EXP  0x0006
#define CAMERA_TIMING_MODULATE_PER_TIM  0x0007
#define CAMERA_TIMING_MODULATE_MON_OFFS 0x0008
#define CAMERA_TIMING_SYNCH_MODE        0x0009
#define CAMERA_TIMING_FPS               0x000A

#define CAMERA_SENSOR                   0x0200
#define CAMERA_SENSOR_BIN_HORZ          0x0001
#define CAMERA_SENSOR_BIN_VERT          0x0002
#define CAMERA_SENSOR_FORMAT            0x0003
#define CAMERA_SENSOR_ROI               0x0004
#define CAMERA_SENSOR_ROI_BIN           0x0005

#define CAMERA_SENSOR2                  0x0300
#define CAMERA_SENSOR2_NUM_ADC          0x0001
#define CAMERA_SENSOR2_DOUBLE_IMAGE     0x0002
#define CAMERA_SENSOR2_IR_SENS          0x0003
#define CAMERA_SENSOR2_OFFS_MODE        0x0004
#define CAMERA_SENSOR2_PIXELRATE        0x0005
#define CAMERA_SENSOR2_CONV_FACT        0x0006
#define CAMERA_SENSOR2_COOLING          0x0007
#define CAMERA_SENSOR2_CDI_MODE         0x0008
#define CAMERA_SENSOR2_BW_NOISE_FILTER  0x0009

#define CAMERA_MEMORY                   0x0400
#define CAMERA_MEMORY_SEG_SIZE          0x0001
#define CAMERA_MEMORY_SEG_NUM           0x0002

#define CAMERA_RECORDING                0x0500
#define CAMERA_RECORDING_STOR_MODE      0x0001
#define CAMERA_RECORDING_REC_SUBMODE    0x0002
#define CAMERA_RECORDING_ACQU_MODE      0x0003
#define CAMERA_RECORDING_TIMESTAMP      0x0004
#define CAMERA_RECORDING_REC_STOP_EVENT 0x0005

//#define CAMERA_CAMERALINK_              0x0600


#define CAMERA_HWIO                     0x0700
#define CAMERA_HWIO_SIGNALS             0x0001


#define CAMERA_DETAIL_MASK              0x0FF00000
#define CAMERA_DETAIL_TO_INDEX(det)     (det >> 20)
#define CAMERA_DETAIL_HELPER_1          0x00100000 // Will be added to some CAMERA_ defines
#define CAMERA_DETAIL_HELPER_2          0x00200000 // in order to help identification of items
#define CAMERA_DETAIL_HELPER_3          0x00300000
#define CAMERA_DETAIL_HELPER_4          0x00400000
#define CAMERA_DETAIL_HELPER_5          0x00500000
#define CAMERA_DETAIL_HELPER_6          0x00600000
#define CAMERA_DETAIL_HELPER_7          0x00700000
#define CAMERA_DETAIL_HELPER_8          0x00800000
#define CAMERA_DETAIL_HELPER_9          0x00900000
#define CAMERA_DETAIL_HELPER_10         0x00A00000

#define CAMERA_DETAIL_INDEX_MASK        0xF0000000
#define CAMERA_DETAIL_INDEX_0           0x00000000
#define CAMERA_DETAIL_INDEX_1           0x10000000
#define CAMERA_DETAIL_INDEX_2           0x20000000
#define CAMERA_DETAIL_INDEX_3           0x30000000
#define CAMERA_DETAIL_INDEX_4           0x40000000
#define CAMERA_DETAIL_INDEX_5           0x50000000
#define CAMERA_DETAIL_INDEX_6           0x60000000
#define CAMERA_DETAIL_INDEX_7           0x70000000
#define CAMERA_DETAIL_INDEX_8           0x80000000

typedef unsigned char byte;     /* 8-bit  */
typedef unsigned short word;    /* 16-bit */
typedef unsigned long dword;    /* 32-bit */

#define NOERR  0
//#define NOTINIT -1
#define TIMEOUT -2

#define PCO_ERRT_H_CREATE_OBJECT
#include "PCO_err.h"
#include "PCO_errt.h"
#include "sc2_SDKStructures.h"
#include "sc2_defs.h"

extern void InitLib(unsigned char, char*, int, char*);
#define MMIJ 5

class CKamSensi;
class CKamPcCam;
class CKamSC2;
class CKamBase;
class CCameraRegistry;

class CCameraWrapper
{
public:
  CKamSensi    *SensiCam;
  CKamPcCam    *PixelFly;
  CKamSC2      *SC2;
  CKamBase     *CamBase;
  CCameraRegistry *CamRegistry;

  // Variablendeklarationen
  int          m_iCamClass;  // 1: SensiCam, 2: PixelFly, 3: SC2
  int          m_iGenerationCount;
  bool         bDemoMode;
  bool         bCameraLost;
  int          iCamTypeCurrentNumber;
  int          m_iCameraType, m_iCCDType, m_iCameraID;

  bool         bNumber;
  bool         m_bIsInit;
  bool         GetInit() { return m_bIsInit; };
  char         szCamName[20];
protected:
  void FillDummy();
public:

  bool m_bAlive;
  bool m_bActive;
  int m_iCameraNumber;
  int m_iInterfaceType;
  int m_iCameraSerialNumber;

  // Funktionsdeklarationen
  CCameraWrapper();
  ~CCameraWrapper();

  int PreInitCamera(int *iCameraCnt, int *iLastScanned, unsigned int *uiResult);
  int iLastScanned;
  int PreInitSen(int numbersi, int iCamCnt, unsigned int *uiResult);
  int PreInitPcCam(int numberpf, int iCamCnt, unsigned int *uiResult);
  int PreInitSC2(int numbersc2, int iCamCnt, unsigned int *uiResult);


  void SetDemoMode(bool bd, int iDemoXRes, int iDemoYRes, int iDemoCol, int iDemoDS,
    int iDemoModeBitRes, int iDemoModeAlign);

  int InitCamera(bool);
  int ReInitCamera();
  int CloseCam();

  // File related functions:

  // Image buffer related functions:

  word * const GetPic12(WORD* wmean, double* dsigma, DWORD dwflags) const;
  byte * GetPic8();
  byte * GetPic8c();


  bool ReloadSize();
  int  WaitForImage(int *ilastbuffer, int *icurrentbuffer);
  int GetBitsPerPixel();

  // Convert functions:
  void Convert(int ibufnum);
  word * GetBuffer(int ibufnum);
  void SetViewMode(bool bFlip, bool bMirror, bool bRotLeft, bool bRotRight, double dZoom, bool bShowImageInfo);
  void GetViewMode(bool *bFlip, bool *bMirror, bool *bRotLeft, bool *bRotRight, double *dZoom, bool *bShowImageInfo);

  void SetConvert(int iconvert, int ifileconvert);
  void SetConvertBWCol(bool bBW, bool bCol);
  int  AutoBalance(int x1, int y1, int x2, int y2, int iAB);
  int  SetLutMinMax(bool bSmall, bool both);

  // Camera related functions:
  int PreStartCam(unsigned int uiMode, int iFirstPic, int iStartPic, int iEndPic);// Aufruf zum Vorbereiten des live Preview
  int StartCam();
  int  StopCam(int *istopresult);
  void ResetEvWait();
  int GetXRes() const;
  int GetYRes() const;

  int  GetCCDCol(int itype);//0: color CCD 1: color pattern type
  int  GetCamType() const;
  int  GetCCDType() const;
  int GetMaximumROI(int *m_nRoiXMax, int *m_nRoiYMax);
  int setcoc(int mode, int trig, int roix1, int roix2, int roiy1, int roiy2, int hbin, int vbin, TCHAR* table, int gain, int offset, unsigned int flags);
  int getsettings(int* mode, int* trig, int* roix1, int* roix2, int* roiy1, int* roiy2, int* hbin, int* vbin, TCHAR* table, int *gain, int *offset, unsigned int* flags);
  int testcoc(int* mode, int* trig, int* roix1, int* roix2, int* roiy1, int* roiy2, int* hbin, int* vbin, TCHAR* table, int* size, int *gain, int *offset, unsigned int* flags);
  int ForceTrigger();

  int GetExposureDelayNs(__int64* i64Del, __int64* i64Exp);
  double GetCurrentFPS();

  PCO_Camera m_strCamera;
  int m_iNumActiveSet;
  int LoadSettingsFromRegistryMM(int iSetNum, std::string &csSetName, PCO_Camera &strCamera);
  int WriteSettingsToRegistryMM(int iSetNum, std::string &csSetName, PCO_Camera &strCamera);

public:
  int DeleteSettingsFromRegistry(int iSetNum);
  int IsSettingsValidInRegsitry(int iSetNum);
  int GetSetsFoundInRegistry(int *iSetNums, int *ilen);
  int GetSettings(PCO_Camera &get);
  int SetSettings(PCO_Camera &set, DWORD *dwresult);
  int UpdateSettingCamera(PCO_Camera &set, int iitem, DWORD* dwResult);
  int ReloadFromCamera(PCO_Camera* set);
  int GetCameraStruct(PCO_Camera *strCamera);
  int SetCameraStruct(PCO_Camera *strCamera, DWORD *dwResult);
  int GetCameraNameNType(char* pname, int ilen, int *iCamTy, int *iCCDTy, int *iCamId) const;

  int getccdsize(int*, int*, int*);

  int GetCameraSetup(WORD* wType, DWORD* dwSetup, WORD *wLen);
  int SetCameraSetup(WORD wType, DWORD* dwSetup, WORD wLen, DWORD dwflags);

  // Dialog related functions:

  // Recorder related functions:

  // Administrative functions:
};
#endif
