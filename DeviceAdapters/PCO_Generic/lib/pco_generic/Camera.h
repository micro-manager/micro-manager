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

#define KAMLIBVERSION           241

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

class CCamera
{
public:
  CKamSensi    *SensiCam;
  CKamPcCam    *PixelFly;
  CKamSC2      *SC2;
  CKamBase     *CamBase;
  // Variablendeklarationen
  int          iCamClass;  // 1: SensiCam, 2: PixelFly, 3: SC2
  char         szCamName[20];
  bool         bDemoMode;
  bool         bCameraLost;
  int          iCamTypeCurrentNumber;
  int          iCameraCnt;
  unsigned int uiLogfile;
  bool         bNumber;
 // Funktionsdeklarationen
  CCamera();
  ~CCamera();
  int PreInitSen(int numbersi, int iCamCnt, unsigned int uiLog);
  int PreInitPcCam(int numberpf, int iCamCnt, unsigned int uiLog);
  int PreInitSC2(int numbersc2, int iCamCnt, unsigned int uiLog);

  void SetDemoMode(bool bd, int iDemoXRes, int iDemoYRes, int iDemoCol, int iDemoDS,
                        int iDemoModeBitRes, int iDemoModeAlign);

  int Init(bool);
  int CloseCam();

// File related functions:

// Image buffer related functions:

  word * const GetPic12(WORD* wmean, double* dsigma, DWORD dwflags) const;
  byte * GetPic8();
  byte * GetPic8c();
  word * GetBuffer(int ibufnum);

  bool ReloadSize();
  int  WaitForImage(int *ilastbuffer, int *icurrentbuffer);
  int GetBitsPerPixel();

// Convert functions:
  void Convert(int ibufnum);
  void SetViewMode(bool bFlip, bool bMirror, bool bRotLeft, bool bRotRight);
  void GetViewMode(bool *bFlip, bool *bMirror, bool *bRotLeft, bool *bRotRight);
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
  int  GetMaximumROI(int *iRoiXMax, int *iRoiYMax);
  int setcoc(int mode, int trig, int roix1, int roix2, int roiy1, int roiy2, int hbin, int vbin, char* table, int gain, int offset, unsigned int flags);
  int getsettings(int* mode, int* trig, int* roix1, int* roix2, int* roiy1, int* roiy2, int* hbin, int* vbin, char* table, int *gain, int *offset, unsigned int* flags);
  int testcoc(int* mode, int* trig, int* roix1, int* roix2, int* roiy1, int* roiy2, int* hbin, int* vbin, char* table,int* size, int *gain, int *offset, unsigned int* flags);
  int getccdsize(int*, int*, int*);

  PCO_Camera strCam;
  int GetCameraStruct(PCO_Camera *strCamera);
  int SetCameraStruct(PCO_Camera *strCamera);
  int GetCameraNameNType(char* pname, int ilen, int *iCamTy, int *iCCDTy, int *iCamId) const;

// Dialog related functions:

// Recorder related functions:

// Administrative functions:
};
#endif
