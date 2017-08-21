//#pragma once

#ifndef _AMSCOPE_H_
#define _AMSCOPE_H_

// Micro-Manager specific includes
#include "MMDevice.h"
#include "MMDeviceConstants.h"
#include "ModuleInterface.h"
#include "DeviceBase.h"
#include "ImgBuffer.h"
#include "DeviceThreads.h"

// AmScope specific includes
#include "toupcam.h"

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102


class SequenceThread;

class AmScope : public CCameraBase<AmScope>
{ 
public:
	AmScope();
	~AmScope();

	HToupCam	m_Htoupcam;
	ToupcamInst m_ti[TOUPCAM_MAX];
	BYTE*				m_pData;
	BITMAPINFOHEADER	m_header;


	// MMDevice API
   // ------------
   int Initialize();
   int Shutdown();  
   void GetName(char* name) const;      
   
   // AmScope API
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
   //int PrepareSequenceAcqusition();
   int StartSequenceAcquisition(double interval);
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StopSequenceAcquisition();
   bool IsCapturing();
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
   //double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   double GetPixelSizeUm() const {return pixelSizeXUm_;}
   int GetBinning() const;
   int SetBinning(int binSize);
   int IsExposureSequenceable(bool& seq) const {seq = false; return DEVICE_OK;}
   int RunSequenceOnThread(MM::MMTime startTime);

   unsigned  GetNumberOfComponents() const { return nComponents_;};

   // action interface
   // ----------------
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAutoExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAutoExposureTarget(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposureTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAutoWhiteBalance(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhiteBalanceRGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhiteBalanceGGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhiteBalanceBGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCameraCCDXSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCameraCCDYSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelResolusion(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFlipHorizontal(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFlipVertical(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnAutoLevelRange(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLevelRangeRMin(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLevelRangeGMin(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLevelRangeBMin(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLevelRangeRMax(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLevelRangeGMax(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnLevelRangeBMax(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTransparency(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelSizeXUm(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelSizeYUm(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   friend class SequenceThread;
   long IMAGE_WIDTH;
   long IMAGE_HEIGHT;
   static const int MAX_BIT_DEPTH = 16;
   double nominalPixelSizeUm_;

   SequenceThread* thd_;
   int binning_;
   long autoExposure_;
   long exposureTarget_;
   long defaultExposureTime_;
   long autoLevelRange_;
   unsigned short aLow_[4]; // color level range low
   unsigned short aHigh_[4]; // color level range high
   long aGain_;
   long defaultAGain_;
   double dPhase_;
   int bytesPerPixel_;
   int resolution_;
   double exposureMs_;
   //double mmDisplayExposure_;
   float orgPixelSizeXUm_;
   float orgPixelSizeYUm_;
   float pixelSizeXUm_;
   float pixelSizeYUm_;
   bool initialized_;
   ImgBuffer img_;
   unsigned int roiX_, roiY_;
   int bitDepth_;
   MM::MMTime sequenceStartTime_;
   bool isSequenceable_;
   long sequenceMaxLength_;
   bool sequenceRunning_;
   bool stopOnOverflow_;
   long imageCounter_;
   MMThreadLock imgPixelsLock_;
   std::string triggerDevice_;
   std::string flipHorizontal_;
   std::string flipVertical_;
   bool fastImage_;
   std::vector<double> exposureSequence_;
   unsigned long sequenceIndex_;
   int nComponents_;
   long autoWhitebalance_;
   int wbGain_[3];
   long frameRate_;

   void CreateCameraImageBuffer();
   int ResizeImageBuffer();
   void GenerateEmptyImage();
   void GenerateCameraImage();
   int InsertImage();
   double GetSequenceExposure();
};

//////////////////////////////////////////////////////////////////////////////
// AmScopeAutoFocus class
// Simulation of the auto-focusing module
//////////////////////////////////////////////////////////////////////////////
class AmScopeAutoFocus : public CAutoFocusBase<AmScopeAutoFocus>
{
public:
   AmScopeAutoFocus() : 
      running_(false), 
      busy_(false), 
      initialized_(false)  
      {
         CreateHubIDProperty();
      }

   ~AmScopeAutoFocus() {}
      
   // MMDevice API
   bool Busy() {return busy_;}
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown(){initialized_ = false; return DEVICE_OK;}

   // AutoFocus API
   virtual int SetContinuousFocusing(bool state)
   {
      running_ = state;
      return DEVICE_OK;
   }
   virtual int GetContinuousFocusing(bool& state)
   {
      state = running_;
      return DEVICE_OK;
   }
   virtual bool IsContinuousFocusLocked() { return running_; }
   virtual int FullFocus() { return DEVICE_OK; }
   virtual int IncrementalFocus() { return DEVICE_OK; }
   virtual int GetLastFocusScore(double& score)
   {
      score = 0.0;
      return DEVICE_OK;
   }
   virtual int GetCurrentFocusScore(double& score)
   {
      score = 1.0;
      return DEVICE_OK;
   }
   virtual int GetOffset(double& /*offset*/) { return DEVICE_OK; }
   virtual int SetOffset(double /*offset*/) { return DEVICE_OK; }

private:
   bool running_;
   bool busy_;
   bool initialized_;
};

////////////////////////
// ASHub
//////////////////////

class ASHub : public HubBase<ASHub>
{
public:
   ASHub() :
      initialized_(false),
      busy_(false)
   {}
   ~ASHub() {}

   // Device API
   // ---------
   int Initialize();
   int Shutdown() {return DEVICE_OK;};
   void GetName(char* pName) const; 
   bool Busy() { return busy_;} ;

   // HUB api
   int DetectInstalledDevices();

private:
   void GetPeripheralInventory();

   std::vector<std::string> peripherals_;
   bool initialized_;
   bool busy_;
};


class SequenceThread : public MMDeviceThreadBase
{
	friend class AmScope;
   enum { default_numImages=1, default_intervalMS = 100 };
   public:
      SequenceThread(AmScope* pCam);
      ~SequenceThread();
      void Stop();
      void Start(long numImages, double intervalMs);
      bool IsStopped();
	  void Suspend();
      bool IsSuspended();
      void Resume();
      double GetIntervalMs(){return intervalMs_;}                               
      void SetLength(long images) {numImages_ = images;}                        
      long GetLength() const {return numImages_;}
      long GetImageCounter(){return imageCounter_;}
	  MM::MMTime GetStartTime(){return startTime_;}                             
      MM::MMTime GetActualDuration(){return actualDuration_;}

   private:                                                                     
      int svc(void) throw();
      AmScope* camera_;                                                     
      bool stop_;                                                               
      long numImages_;                                                          
      long imageCounter_;                                                       
      double intervalMs_;
	  bool suspend_;
	  MM::MMTime startTime_;                                                    
      MM::MMTime actualDuration_;                                               
      MM::MMTime lastFrameTime_;                                                
      MMThreadLock stopLock_;                                                   
      MMThreadLock suspendLock_;
}; 


#endif  // _AMSCOPE_H_