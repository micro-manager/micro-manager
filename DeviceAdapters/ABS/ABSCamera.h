#ifndef __ABSCAMERA_H__
#define __ABSCAMERA_H__

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>   
   #define snprintf _snprintf 
   #pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 
#endif

#include "MMDevice.h"
#include "MMDeviceConstants.h"
#include "ModuleInterface.h"
#include "DeviceBase.h"
#include "ImgBuffer.h"
#include "DeviceThreads.h"
#include <string>
#include <map>
#include <algorithm>
using namespace std;

// ---------------------------Camera - API ------------------------------------
#include "camusb_api.h"				                // ABS Camera API
#include "camusb_api_util.h"			            // ABS Camera API utilities
#include "camusb_api_ext.h"			                // ABS Camera API extended
#include "safeutil.h"						        // macros to delete release pointers safely
#include "ABSDelayLoadDll.h"						// add support for Delay loading of an dll


//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE         102
#define ERR_UNKNOWN_POSITION     103
#define ERR_IN_SEQUENCE          104
#define ERR_SEQUENCE_INACTIVE    105
#define SIMULATED_ERROR				200
#define ERR_CAMERA_API_BASE      200

typedef vector<S_CAMERA_LIST_EX> CCameraList;

class CABSCamera : public CCameraBase<CABSCamera>  
{
public:
   CABSCamera();
   ~CABSCamera();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   
   // MMCamera API
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
   int PrepareSequenceAcqusition()
   {
         return DEVICE_OK;
   }
   int StartSequenceAcquisition(double interval);
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StopSequenceAcquisition();
   int InsertImage();
   int ThreadRun();
   bool IsCapturing();
   void OnThreadExiting() throw(); 
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
   double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   int GetBinning() const;
   int SetBinning(int bS);
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   unsigned  GetNumberOfComponents() const { return nComponents_;};

   // action interface
   // ----------------
	// floating point read-only properties for testing
	int OnTestProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnErrorSimulation(MM::PropertyBase* , MM::ActionType );
   int OnCameraCCDXSize(MM::PropertyBase* , MM::ActionType );
   int OnCameraCCDYSize(MM::PropertyBase* , MM::ActionType );
   int OnTriggerDevice(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDropPixels(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSaturatePixels(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFractionOfPixelsToDropOrSaturate(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
	
	

   MM::MMTime CurrentTime(void) { return GetCurrentMMTime(); };

   int OnCameraSelection(MM::PropertyBase* pProp, MM::ActionType eAct);
   static bool isApiDllAvailable( void );

   string buildCameraDeviceID( unsigned long serialNumber, const char* deviceName );
   void setCameraDeviceID( string cameraDeviceID );
   string cameraDeviceID( void ) const;
protected:
	bool isInitialized( void ) const;		
	void setInitialized( const bool bInitialized );
	bool isColor( void ) const;		
	void setColor( const bool colorCamera );
	void getAvailableCameras( CCameraList & cCameraList );
	void setDeviceNo( int deviceNo );
	int deviceNo( void ) const;

	static int accquireDeviceNumber( void );
	static void releaseDeviceNumber( int deviceNo );

	int convertApiErrorCode( unsigned long errorNumber );	
	
	unsigned long getCameraImage( ImgBuffer& img );
	unsigned long getCap( unsigned __int64 functionId, void* & capability );
	int setAllowedPixelTypes( void );
	int setAllowedBitDepth( void );
	int setAllowedExpsoure( void );
	int setAllowedGain( void );

private:
	static volatile int staticDeviceNo;
	string cameraDeviceID_;
	int	 deviceNo_;
	bool	 colorCamera_;
	string last16BitDepth;
	double temperatureUnit_;
	int	 temperatureIndex_;

	S_CAMERA_VERSION     cameraVersion_;
   /*S_RESOLUTION_CAPS*   resolutionCap;
   S_FLIP_CAPS*         flipCap;
   S_PIXELTYPE_CAPS*    pixelTypeCap;
   S_GAIN_CAPS*         gainCap;
   S_EXPOSURE_CAPS*     exposureCap;
   S_AUTOEXPOSURE_CAPS* autoExposureCap;
   S_TEMPERATURE_CAPS*  temperatureCap;
   S_FRAMERATE_CAPS*    framerateCap;*/

private:
   int SetAllowedBinning();
   void TestResourceLocking(const bool);
   void GenerateEmptyImage(ImgBuffer& img);
   void GenerateSyntheticImage(ImgBuffer& img, double exp);
   int ResizeImageBuffer();

   static const double nominalPixelSizeUm_;

   double dPhase_;
   ImgBuffer img_;
   bool busy_;
   bool stopOnOverFlow_;
   bool initialized_;
   double readoutUs_;
   MM::MMTime readoutStartTime_;
   string scanMode_;
   int bitDepth_;
   unsigned roiX_;
   unsigned roiY_;
   MM::MMTime sequenceStartTime_;
   long imageCounter_;
	long binSize_;
	long cameraCCDXSize_;
	long cameraCCDYSize_;
	std::string triggerDevice_;

	bool dropPixels_;
	bool saturatePixels_;
	double fractionOfPixelsToDropOrSaturate_;

	double testProperty_[10];
   MMThreadLock* pDemoResourceLock_;
   MMThreadLock imgPixelsLock_;
   int nComponents_;
   friend class CABSCameraSequenceThread;
   CABSCameraSequenceThread * thd_;
};

class CABSCameraSequenceThread : public MMDeviceThreadBase
{
   friend class CABSCamera;
   enum { default_numImages=1, default_intervalMS = 100 };
   public:
      CABSCameraSequenceThread(CABSCamera* pCam);
      ~CABSCameraSequenceThread();
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
      CABSCamera* camera_;                                                     
      bool stop_;                                                               
      bool suspend_;                                                            
      long numImages_;                                                          
      long imageCounter_;                                                       
      double intervalMs_;                                                       
      MM::MMTime startTime_;                                                    
      MM::MMTime actualDuration_;                                               
      MM::MMTime lastFrameTime_;                                                
      MMThreadLock stopLock_;                                                   
      MMThreadLock suspendLock_;                                                
}; 


#endif // __ABSCAMERA_H__