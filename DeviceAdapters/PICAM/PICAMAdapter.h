///////////////////////////////////////////////////////////////////////////////
// FILE:          PICAMAdapter.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PICAM camera module
//
// AUTHOR:        Toshio Suzuki
//
// PORTED from    PVCAMAdapter
//                (AUTHOR:        Nico Stuurman, Nenad Amodaj nenad@amodaj.com, 09/13/2005)
//                (COPYRIGHT:     University of California, San Francisco, 2006)
//
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

#ifndef _PICAMADAPTER_H_
#define _PICAMADAPTER_H_

#include "DeviceBase.h"
#include "ImgBuffer.h"
#include "Debayer.h"
#include "DeviceUtils.h"
#include "DeviceThreads.h"

#ifdef WIN64
#pragma warning(push)
#include "picam.h"
#include "picam_advanced.h"
#pragma warning(pop)
#endif

#include <functional> // for mem_fn

#ifdef linux
#include <pvcam/master.h>
#include <pvcam/pvcam.h>
#endif

#if(WIN64 && NDEBUG)
WINBASEAPI
BOOL
WINAPI
TryEnterCriticalSection(
      __inout LPCRITICAL_SECTION lpCriticalSection
      );
#endif

#ifdef WIN32
// FRAME_INFO is currently supported on Windows only (PICAM 2.9.5+)
//#define PICAM_FRAME_INFO_SUPPORTED
// Callbacks are not supported on Linux and Mac (as for 01/2014)
//#define PICAM_CALLBACKS_SUPPORTED
// The new parameter is implmented in PICAM for Windows only (PICAM 3+)
//#define PICAM_PARAM_EXPOSE_OUT_DEFINED
#endif

#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_INVALID_BUFFER            10002
#define ERR_INVALID_PARAMETER_VALUE   10003
#define ERR_BUSY_ACQUIRING            10004
#define ERR_STREAM_MODE_NOT_SUPPORTED 10005
#define ERR_CAMERA_NOT_FOUND          10006
#define ERR_ROI_SIZE_NOT_SUPPORTED    10007

/***
 * User selected region of interest
 */
struct ROI {
   piint x;
   piint newX;
   piint y;
   piint newY;
   piint xSize;
   piint newXSize;
   piint ySize;
   piint newYSize;
   piint binXSize;
   piint binYSize;

   // added this function to the ROI struct because it only applies to this data structure,
   //  and nothing else.
   void PICAMRegion(piint x_, piint y_, piint xSize_, piint ySize_, \
         unsigned binXSize_, unsigned binYSize_, PicamRoi &newRegion)
   {
      // set to full frame
      x = x_;
      y = y_;
      xSize = xSize_;
      ySize = ySize_;

      // set our member binning information
      binXSize = (piint) binXSize_;
      binYSize = (piint) binYSize_;

      // save ROI-related dimentions into other data members
      newX = x/binXSize;
      newY = y/binYSize;
      newXSize = xSize/binXSize;
      newYSize = ySize/binYSize;

      // round the sizes to the proper devisible boundaries
      x = newX * binXSize;
      y = newY * binYSize;
      xSize = newXSize * binXSize;
      ySize = newYSize * binYSize;

      // set PICAM-specific region
      newRegion.x = x;
      newRegion.width= xSize;
      newRegion.x_binning = binXSize;
      newRegion.y = y;
      newRegion.height=  ySize;
      newRegion.y_binning = binYSize;
   }
};

/***
 * Struct used for Universal Parameters definition
 */
typedef struct
{
   const char * name;
   PicamParameter id;
} ParamNameIdPair;

/***
 * Speed table row
 */
typedef struct
{
   piint pixTime;         // Readout rate in ns
   piint bitDepth;        // Bit depth
   piint gainMin;         // Min gain index for this speed
   piint gainMax;         // Max gain index for this speed
   piint spdIndex;        // Speed index
   piint portIndex;       // Port index
   piflt adcRate;         // MHz
   pibln bEnable;
   std::string spdString; // A string that describes this choice in GUI
} SpdTabEntry;


class AcqSequenceThread;
template<class T> class PvParam;
class PvUniversalParam;
class PvEnumParam;

/***
 * Class used by post processing, a list of these elements is built up one for each post processing function
 * so the call back function in CPropertyActionEx can get to information about that particular feature in
 * the call back function
 */
class PProc
{

   public:

      PProc(std::string name = "", int ppIndex = -1, int propIndex = -1)
      {
         mName = name, mppIndex = ppIndex, mpropIndex = propIndex, mcurValue = ppIndex;
      }
      std::string GetName()        { return mName; }
      int         GetppIndex()     { return mppIndex; }
      int         GetpropIndex()   { return mpropIndex; }
      int         GetRange()       { return mRange; }
      double      GetcurValue()    { return mcurValue; }
      void        SetName(std::string name)    { mName      = name; }
      void        SetppIndex(int ppIndex)      { mppIndex   = ppIndex; }
      void        SetpropInex(int propIndex)   { mpropIndex = propIndex; }
      void        SetcurValue(double curValue) { mcurValue  = curValue; }
      void        SetRange(int range)          { mRange     = range; }

      void SetPostProc(PProc& tmp)
      {
         mName = tmp.GetName(), mppIndex = tmp.GetppIndex(), mpropIndex = tmp.GetpropIndex();
      }

   protected:

      std::string mName;
      int         mppIndex;
      int         mpropIndex;
      double      mcurValue;
      int         mRange;

};

/***
 * Implementation of the MMDevice and MMCamera interfaces for all PICAM cameras
 */
class Universal : public CCameraBase<Universal>
{

   public:

      Universal(short id, const char* name);
      ~Universal();

      // MMDevice API
      int  Initialize();
      int  Shutdown();
      void GetName(char* pszName) const;
      bool Busy();
      bool GetErrorText(int errorCode, char* text) const;

      // MMCamera API
      int SnapImage();
      const unsigned char* GetImageBuffer();
      unsigned GetImageWidth() const         { return img_.Width(); }
      unsigned GetImageHeight() const        { return img_.Height(); }
      unsigned GetImageBytesPerPixel() const { return rgbaColor_ ? colorImg_.Depth() : img_.Depth(); }
      long GetImageBufferSize() const;
      unsigned GetBitDepth() const;
      int GetBinning() const;
      int SetBinning(int binSize);
      double GetExposure() const;
      void SetExposure(double dExp);
      int IsExposureSequenceable(bool& isSequenceable) const { isSequenceable = false; return DEVICE_OK; }
      unsigned GetNumberOfComponents() const {return rgbaColor_ ? 4 : 1;}

#ifndef linux
      // micromanager calls the "live" acquisition a "sequence"
      //  don't get this confused with a PICAM sequence acquisition, it's actually circular buffer mode
      int PrepareSequenceAcqusition();
      int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
      int StopSequenceAcquisition();
#endif

      // action interface
      int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnBinningX(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnBinningY(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnReadoutRate(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnReadNoiseProperties(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnMultiplierGain(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnReadoutPort(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnTemperatureSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnUniversalProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
#ifdef WIN32 //This is only compiled for Windows at the moment
      int OnResetPostProcProperties(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnPostProcProperties(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
      int OnActGainProperties(MM::PropertyBase* pProp, MM::ActionType eAct);
#endif
      int OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnExposeOutMode(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnTriggerTimeOut(MM::PropertyBase* pProp, MM::ActionType eAct);
      int OnOutputTriggerFirstMissing(MM::PropertyBase* pProp, MM::ActionType eAct);
#ifdef PICAM_CALLBACKS_SUPPORTED
      int OnAcquisitionMethod(MM::PropertyBase* pProp, MM::ActionType eAct);
#endif

      bool IsCapturing();

      // Published to allow other classes access the camera
      PicamHandle Handle() { return hPICAM_; }
      // Utility logging functions (published to allow usage from other classes)
      piint LogCamError(int lineNr, std::string message="", bool debug=false) throw();
      int   LogMMError(int errCode, int lineNr, std::string message="", bool debug=false) const throw();
      void  LogMMMessage(int lineNr, std::string message="", bool debug=true) const throw();

   protected:

#ifndef linux
      int ThreadRun(void);
      void OnThreadExiting() throw();
#endif

      //   int FrameDone();
      int BuildMetadata( Metadata& md );
      int PushImage(const unsigned char* pixBuffer, Metadata* pMd );

   private:

      Universal(Universal&) {}
      int GetExposureValue(piflt& exposureValue);
      int ResizeImageBufferContinuous();
      int ResizeImageBufferSingle();
      bool WaitForExposureDone() throw();
      MM::MMTime GetCurrentTime() { return GetCurrentMMTime();}
      bool InitializeCalculatedBufferSize();

      static int      refCount_;             // This class reference counter
      static bool     PICAM_initialized_;    // Global PICAM initialization status

      bool            initialized_;          // Driver initialization status in this class instance
      long            numImages_;            // Number of images to acquire
      long            curImageCnt_;          // Current number of images acquired
      PicamHandle     hPICAM_;               // Camera handle
      HANDLE          hDataUpdatedEvent_;    // Win32 event to wait for data update
      ImgBuffer       img_;                  // Single image buffer
      ImgBuffer       colorImg_;             // color image buffer
      Debayer         debayer_;              // debayer processor

      MM::MMTime      startTime_;            // Acquisition start time

      short           cameraId_;             // 0-based camera ID, used to allow multiple cameras connected
      PicamCameraID   CameraInfo_;
      unsigned char*  circBuffer_;           // a buffer used for pl_exp_start_cont
      unsigned long   circBufferSize_;       // total byte-wise size of the circular buffer
      bool            stopOnOverflow_;       // Stop inserting images to MM buffer if it's full
      bool            snappingSingleFrame_;  // Single frame mode acquisition ongoing
      bool            singleFrameModeReady_; // Single frame mode acquisition prepared
      bool            sequenceModeReady_;    // Continuous acquisition prepared

      bool            isUsingCallbacks_;
      bool            isAcquiring_;

      long            triggerTimeout_;       // Max time to wait for an external trigger
      bool            microsecResSupported_; // True if camera supports microsecond exposures
#ifdef PICAM_FRAME_INFO_SUPPORTED
      PFRAME_INFO     pFrameInfo_;           // PICAM frame metadata
#endif
      friend class    AcqSequenceThread;
      AcqSequenceThread* uniAcqThd_;         // Pointer to the sequencing thread

      long            outputTriggerFirstMissing_;

      /// CAMERA PARAMETERS:
      ROI             roi_;                  // Current user-selected ROI
      PicamRoi        camRegion_;            // Current PICAM region based on ROI
      piint           camParSize_;           // CCD parallel size
      piint           camSerSize_;           // CCD serial size
      piint           camFWellCapacity_;     // CCD full well capacity
      double          exposure_;             // Current Exposure
      unsigned        binSize_;              // Symmetric binning value
      unsigned        binXSize_;             // Asymmetric binning value
      unsigned        binYSize_;             // Asymmetric binning value

      // These are cached values for binning. Used when changing binning during live mode
      unsigned        newBinSize_;
      unsigned        newBinXSize_;
      unsigned        newBinYSize_;

      std::string     deviceName_;
      char            camName_[PicamStringSize_SensorName];
      char            camChipName_[PicamStringSize_SerialNumber];
      PvParam<piflt>* prmTemp_;              // CCD temperature
      PvParam<piflt>* prmTempSetpoint_;      // Desired CCD temperature
      PvParam<piint>* prmGainIndex_;
      PvParam<piint>* prmGainMultFactor_;
      PvEnumParam*    prmTriggerMode_;       // (PARAM_EXPOSURE_MODE)
      PvParam<piint>* prmExpResIndex_;
      PvEnumParam*    prmExpRes_;
      PvEnumParam*    prmExposeOutMode_;
      PvEnumParam*    prmReadoutPort_;
      PvEnumParam*    prmColorMode_;

      //// for AcquisitionUpdated function
      piint readoutStride_ ;               // - stride to next readout (bytes)
      piint framesPerReadout_ ;            // - number of frames in a readout
      piint frameStride_ ;                 // - stride to next frame (bytes)
      piint frameSize_ ;                   // - size of frame (bytes)

      // color mode
      bool rgbaColor_;

      // List of post processing features
      std::vector<PProc> PostProc_;

      // Camera speed table
      //  usage: SpdTabEntry e = camSpdTable_[port][speed];
      std::map<piint, std::map<piint, SpdTabEntry> > camSpdTable_;
      // Reverse speed table to get the speed based on UI selection
      //  usage: SpdTabEntry e = camSpdTableReverse_[port][ui_selected_string];
      std::map<piint, std::map<std::string, SpdTabEntry> > camSpdTableReverse_;
      // Currently selected speed
      SpdTabEntry camCurrentSpeed_;

      // 'Universal' parameters
      std::vector<PvUniversalParam*> universalParams_;

      /// CAMERA PARAMETER initializers
      int initializeStaticCameraParams();
      int initializeUniversalParams();
      int initializePostProcessing();
      int refreshPostProcValues();
      int revertPostProcValue( long absoluteParamIdx, MM::PropertyBase* pProp);
      int buildSpdTable();
      int speedChanged();
      int portChanged();

      // other internal functions
      int ClearROI();
      int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize);
      int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize);

      struct {
         bool bOverruned;
         bool bAcquisitionInactive;
         int  readout_count;
      } dataUpdated_;
   public:
      PicamError AcquisitionUpdated(
            PicamHandle device,
            const PicamAvailableData* available,
            const PicamAcquisitionStatus* status );

};

/***
 * Acquisition thread
 */
class AcqSequenceThread : public MMDeviceThreadBase
{
   public:
      AcqSequenceThread(Universal* camera) :
         stop_(true), camera_(camera) {}
      ~AcqSequenceThread() {}
      int svc (void);


      void setStop(bool stop) {stop_ = stop;}
      bool getStop() {return stop_;}
      void Start() {
         stop_ = false;
         activate();

      }

   private:
      bool stop_;
      Universal* camera_;

};

#endif //_PICAMADAPTER_H_
