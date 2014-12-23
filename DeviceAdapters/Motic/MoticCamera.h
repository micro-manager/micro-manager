///////////////////////////////////////////////////////////////////////////////
// FILE:          MoticCamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Motic camera device adapter for Windows
// COPYRIGHT:     2012 Motic China Group Co., Ltd.
//                All rights reserved.
//
//                This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//
//                This library is distributed in the hope that it will be
//                useful, but WITHOUT ANY WARRANTY; without even the implied
//                warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
//                PURPOSE. See the GNU Lesser General Public License for more
//                details.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
//                LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
//                EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//                You should have received a copy of the GNU Lesser General
//                Public License along with this library; if not, write to the
//                Free Software Foundation, Inc., 51 Franklin Street, Fifth
//                Floor, Boston, MA 02110-1301 USA.
//
// AUTHOR:        Motic

#ifndef _MOTICAMERA_H_
#define _MOTICAMERA_H_

#include "DeviceBase.h"
#include "ImgBuffer.h"
#include "DeviceThreads.h"
#include "ImgBuffer.h"
using namespace std;
//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_NO_CAMERAS_FOUND			    100
#define ERR_SOFTWARE_TRIGGER_FAILED         1004
#define ERR_BUSY_ACQUIRING                  1003
#define ERR_NO_CAMERA_FOUND                 1005

//////////////////////////////////////////////////////////////////////////
//properties
#define	g_Keyword_Cooler					"Cooler"
#define g_Keyword_Cameras         "Devices"
#define g_Keyword_MoticUI         "MoticInterface"

class SequenceThread;

class CMoticCamera : public CCameraBase<CMoticCamera>  
{
public:
  CMoticCamera();
  ~CMoticCamera();

  // MMDevice API
  // ------------
  int Initialize();
  int Shutdown();

  void GetName(char* name) const;      

  // CMoticCamera API
  // ------------
  int SnapImage();
  const unsigned char* GetImageBuffer();
  unsigned GetImageWidth() const;
  unsigned GetImageHeight() const;
  unsigned GetImageBytesPerPixel() const;
  unsigned GetBitDepth() const;
  long GetImageBufferSize() const;
  double GetExposure() const;
  void SetExposure(double exp);
  int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
  int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize); 
  int ClearROI();
  int PrepareSequenceAcqusition();
  //int StartSequenceAcquisition(double interval);
 // int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
 // int StopSequenceAcquisition();
  bool IsCapturing();
  int GetBinning() const;
  int SetBinning(int binSize);
  int IsExposureSequenceable(bool& seq) const {seq = false; return DEVICE_OK;}

  //////////////////////////////////////////////////////////////////////////
  unsigned GetNumberOfComponents() const 
  {
    if(m_iBytesPerPixel == 1 || m_iBytesPerPixel == 2)return 1;
    return 4;
  }

  int GetComponentName(unsigned channel, char* name)
  {
    if(m_iBytesPerPixel == 1 || m_iBytesPerPixel == 2)
    {
       CDeviceUtils::CopyLimitedString(name, "Grayscale");
    }
    else if(channel == 0)
    {
      CDeviceUtils::CopyLimitedString(name, "Blue");
    }
    else if(channel == 1)
    {
      CDeviceUtils::CopyLimitedString(name, "Green");
    }
    else if(channel == 2)
    {
      CDeviceUtils::CopyLimitedString(name, "Red");
    }
    else if(channel == 3)
    {
      CDeviceUtils::CopyLimitedString(name, "Alpha");
    }
    else
    {
      return DEVICE_NONEXISTENT_CHANNEL;
    }
    return DEVICE_OK; 
  }
//    unsigned GetNumberOfChannels() const 
//    {
//       return 3;
//    }
// 
//    virtual int GetChannelName(unsigned  channel , char* name)
//    {
//      if(channel == 0)
//      {
//        CDeviceUtils::CopyLimitedString(name, "Blue");
//      }
//      else if(channel == 1)
//      {
//        CDeviceUtils::CopyLimitedString(name, "Green");
//      }
//      else if(channel == 2)
//      {
//        CDeviceUtils::CopyLimitedString(name, "Red");
//      }
//      else
//      {
//        return DEVICE_NONEXISTENT_CHANNEL;
//      }
//      return DEVICE_OK;      
//    }
// 
//    virtual const unsigned char* GetImageBuffer(unsigned /* channelNr */)
//    {
//       if (GetNumberOfChannels() == 1)
//          return GetImageBuffer();
//       return 0;
//    }

   const unsigned int* GetImageBufferAsRGB32()
   {
      return (unsigned int*)m_img.GetPixels();
   } 

  //////////////////////////////////////////////////////////////////////////
  // action interface
  // ----------------
  int OnDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
  int OnShowUI(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
 // friend class SequenceThread;
 
 // SequenceThread* m_thd;
  int m_iBinning;
  int m_iBytesPerPixel;
  float m_dGain;
  float m_dMinGain;
  float m_dMaxGain;
  long m_lMinExposure;
  long m_lMaxExposure;
  double m_dExposurems;
  bool m_bInitialized;
  ImgBuffer m_img;
  BYTE* m_pBuffer;
  int   m_iBitCounts;
  int m_iRoiX;
  int m_iRoiY;
  int m_iDevices;
  vector<long>m_vBinning;
  bool m_bShow;
  bool m_bROI;
  vector<string>m_vDevices;
  int m_iCurDeviceIdx;
  bool stopOnOverflow;

  int m_iBufferSize;
  bool m_bNeedPush;
private:
  int ResizeImageBuffer();
  void ReAllocalBuffer(int size);
 // void GenerateImage();
  int InsertImage();
  void InitBinning();
  void InitPixelType();
  void InitGain();
  void InitExposure();
  int InitDevice( );  
  void SaveToReg( int pixelsize );
  int ReadFromReg();
  void NeedToPush();
};
/*
class SequenceThread : public MMDeviceThreadBase
{
public:
  SequenceThread(CMoticCamera* pCam);
  ~SequenceThread();
  void Stop();
  void Start(long numImages, double intervalMs);
  bool IsStopped();
  double GetIntervalMs(){return m_dIntervalMs;}
  void SetLength(long images) {m_lNumImages = images;}                        
  long GetLength() const {return m_lNumImages;}
  long GetImageCounter(){return m_lImageCounter;} 

private:                                                                     
  int svc(void) throw();
  CMoticCamera* m_camera;
  bool m_bStop;
  long m_lNumImages;
  long m_lImageCounter;
  double m_dIntervalMs;
}; 
*/
#endif //_MMCAMERA_H_
