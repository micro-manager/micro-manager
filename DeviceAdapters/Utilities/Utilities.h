///////////////////////////////////////////////////////////////////////////////
// FILE:          Utilities.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Various 'Meta-Devices' that add to or combine functionality of 
//                physcial devices.
//
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu, 11/07/2008
// COPYRIGHT:     University of California, San Francisco, 2008
//                2015 Open Imaging, Inc.
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

#ifndef _UTILITIES_H_
#define _UTILITIES_H_

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_INVALID_DEVICE_NAME            10001
#define ERR_NO_DA_DEVICE                   10002
#define ERR_VOLT_OUT_OF_RANGE              10003
#define ERR_POS_OUT_OF_RANGE               10004
#define ERR_NO_DA_DEVICE_FOUND             10005
#define ERR_NO_STATE_DEVICE                10006
#define ERR_NO_STATE_DEVICE_FOUND          10007
#define ERR_NO_AUTOFOCUS_DEVICE            10008
#define ERR_NO_AUTOFOCUS_DEVICE_FOUND      10009
#define ERR_NO_AUTOFOCUS_DEVICE_FOUND      10009
#define ERR_NO_PHYSICAL_CAMERA             10010
#define ERR_NO_EQUAL_SIZE                  10011
#define ERR_AUTOFOCUS_NOT_SUPPORTED        10012
#define ERR_NO_PHYSICAL_STAGE              10013
#define ERR_TIMEOUT                        10021


//////////////////////////////////////////////////////////////////////////////
// Max number of physical cameras
//
#define MAX_NUMBER_PHYSICAL_CAMERAS       4

/*
 * MultiShutter: Combines multiple physical shutters into one logical device
 */
class MultiShutter : public CShutterBase<MultiShutter>
{
public:
   MultiShutter();
   ~MultiShutter();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}
  
   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire (double /* deltaT */) { return DEVICE_UNSUPPORTED_COMMAND;}
   // ---------

   // action interface
   // ----------------
   int OnPhysicalShutter(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::vector<std::string> availableShutters_;
   std::vector<std::string> usedShutters_;
   std::vector<MM::Shutter*> physicalShutters_;
   long nrPhysicalShutters_;
   bool open_;
   bool initialized_;

   // Synchronize access to physical shutters. This is needed because
   // MultiShutter could be called from multiple threads at the same time if
   // used with a MultiCamera. Currently there is no other mechanism to prevent
   // concurrent access to the physical shutters.
   MMThreadLock physicalShutterLock_;
};

/**
 * CameraSnapThread: helper thread for MultiCamera
 */
class CameraSnapThread : public MMDeviceThreadBase
{
   public:
      CameraSnapThread() :
         camera_(0),
         started_(false)
      {}

      ~CameraSnapThread() { if (started_) wait(); }

      void SetCamera(MM::Camera* camera) { camera_ = camera; }

      int svc() { camera_->SnapImage(); return 0; }

      void Start() { activate(); started_ = true; }

   private:
      MM::Camera* camera_;
      bool started_;
};

/*
 * MultiCamera: Combines multiple physical cameras into one logical device
 */
class MultiCamera : public CCameraBase<MultiCamera>
{
public:
   MultiCamera();
   ~MultiCamera();

   int Initialize();
   int Shutdown();

   void GetName(char* name) const;

   int SnapImage();
   const unsigned char* GetImageBuffer();
   const unsigned char* GetImageBuffer(unsigned channelNr);
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
   int StartSequenceAcquisition(double interval);
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StopSequenceAcquisition();
   int GetBinning() const; 
   int SetBinning(int bS);                                    
   int IsExposureSequenceable(bool& isSequenceable) const;
   unsigned  GetNumberOfComponents() const;
   unsigned  GetNumberOfChannels() const;
   int GetChannelName(unsigned channel, char* name);
   bool IsCapturing();

   // action interface
   // ---------------
   int OnPhysicalCamera(MM::PropertyBase* pProp, MM::ActionType eAct, long nr);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int Logical2Physical(int logical);
   bool ImageSizesAreEqual();
   unsigned char* imageBuffer_;

   std::vector<std::string> availableCameras_;
   std::vector<std::string> usedCameras_;
   std::vector<int> cameraWidths_;
   std::vector<int> cameraHeights_;
   std::vector<MM::Camera*> physicalCameras_;
   unsigned int nrCamerasInUse_;
   bool initialized_;
   ImgBuffer img_;
};


class MultiStage : public CStageBase<MultiStage>
{
public:
   MultiStage();
   virtual ~MultiStage();

public:
   virtual void GetName(char* name) const;

   virtual int Initialize();
   virtual int Shutdown();

   virtual bool Busy();

   virtual int Move(double) { return DEVICE_UNSUPPORTED_COMMAND; }
   virtual int Stop();
   virtual int Home();

   virtual int SetPositionUm(double pos);
   virtual int GetPositionUm(double& pos);
   virtual int SetPositionSteps(long steps);
   virtual int GetPositionSteps(long& steps);

   virtual int SetOrigin() { return DEVICE_UNSUPPORTED_COMMAND; }

   virtual int GetLimits(double& lower, double& upper);
   virtual bool IsContinuousFocusDrive() const;

   virtual int IsStageSequenceable(bool& isSequenceable) const;
   virtual int GetStageSequenceMaxLength(long& nrEvents) const;
   virtual int StartStageSequence();
   virtual int StopStageSequence();
   virtual int ClearStageSequence();
   virtual int AddToStageSequence(double position);
   virtual int SendStageSequence();

private:
   int OnNrStages(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPhysicalStage(MM::PropertyBase* pProp, MM::ActionType eAct, long nr);
   int OnScaling(MM::PropertyBase* pProp, MM::ActionType eAct, long nr);
   int OnTranslationUm(MM::PropertyBase* pProp, MM::ActionType eAct, long nr);
   int OnBringIntoSync(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   unsigned nrPhysicalStages_; // constant while initialized
   double simulatedStepSizeUm_;
   bool initialized_;

   // The following vectors should always have nrPhysicalStages_ elements while
   // initialized
   std::vector<std::string> usedStages_;
   std::vector<MM::Stage*> physicalStages_;
   std::vector<double> stageScalings_;
   std::vector<double> stageTranslations_;
};


class ComboXYStage : public CXYStageBase<ComboXYStage>
{
public:
   ComboXYStage();
   virtual ~ComboXYStage();

public:
   virtual void GetName(char* name) const;

   virtual int Initialize();
   virtual int Shutdown();

   virtual bool Busy();

   virtual int Move(double /* vx */, double /* vy */ ) { return DEVICE_UNSUPPORTED_COMMAND; }
   virtual int Stop();
   virtual int Home();

   virtual int SetPositionSteps(long x, long y);
   virtual int GetPositionSteps(long& x, long& y);

   virtual int SetOrigin() { return DEVICE_UNSUPPORTED_COMMAND; }
   virtual int SetXOrigin() { return DEVICE_UNSUPPORTED_COMMAND; }
   virtual int SetYOrigin() { return DEVICE_UNSUPPORTED_COMMAND; }

   virtual int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
   virtual int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);

   virtual double GetStepSizeXUm() { return simulatedXStepSizeUm_; }
   virtual double GetStepSizeYUm() { return simulatedYStepSizeUm_; }

   virtual int IsXYStageSequenceable(bool& isSequenceable) const;
   virtual int GetXYStageSequenceMaxLength(long& nrEvents) const;
   virtual int StartXYStageSequence();
   virtual int StopXYStageSequence();
   virtual int ClearXYStageSequence();
   virtual int AddToXYStageSequence(double positionX, double positionY);
   virtual int SendXYStageSequence();

private:
   // long xy is 0 for X and 1 for Y
   int OnPhysicalStage(MM::PropertyBase* pProp, MM::ActionType eAct, long xy);
   int OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct, long xy);
   int OnScaling(MM::PropertyBase* pProp, MM::ActionType eAct, long xy);
   int OnTranslationUm(MM::PropertyBase* pProp, MM::ActionType eAct, long xy);

private:
   double simulatedXStepSizeUm_;
   double simulatedYStepSizeUm_;
   bool initialized_;

   // The following vectors should always have 2 elements (0 = X, 1 = Y) while
   // initialized.
   std::vector<std::string> usedStages_;
   std::vector<MM::Stage*> physicalStages_;
   std::vector<double> stageScalings_;
   std::vector<double> stageTranslations_;
};


/**
 * DAMonochromator: Use DA device as monochromator
 * Also acts as a shutter (using a particular wavelength as "closed")
 */
class DAMonochromator : public CShutterBase<DAMonochromator>
{
public:
   DAMonochromator();
   ~DAMonochromator();

   // Device API
   // ----------
   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}

   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire (double /* deltaT */) { return DEVICE_UNSUPPORTED_COMMAND;}
   // ---------

   // action interface
   // ----------------
   int OnDADevice(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnOpenWavelength(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnClosedWavelength(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMinWavelength(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxWavelength(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMinVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::vector<std::string> availableDAs_;
   MM::SignalIO* DADevice_;
   std::string DADeviceName_;
   bool initialized_;
   bool open_;
   double minVoltage_, maxVoltage_;
   double minWavelength_, maxWavelength_;
   double openWavelength_, closedWavelength_;
   double openVoltage_, closedVoltage_;

};

/**
 * DAShutter: Adds shuttering capabilities to a DA device
 */
class DAShutter : public CShutterBase<DAShutter>
{
public:
   DAShutter();
   ~DAShutter();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}
  
   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire (double /* deltaT */) { return DEVICE_UNSUPPORTED_COMMAND;}
   // ---------

   // action interface
   // ----------------
   int OnDADevice(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::vector<std::string> availableDAs_;
   MM::SignalIO* DADevice_;
   std::string DADeviceName_;
   bool initialized_;
};

/**
 * Allows a DA device to act like a Drive (better hook it up to a drive!)
 */
class DAZStage : public CStageBase<DAZStage>
{
public:
   DAZStage();
   ~DAZStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
  int SetPositionUm(double pos);
  int GetPositionUm(double& pos);
  int SetPositionSteps(long steps);
  int GetPositionSteps(long& steps);
  int SetOrigin();
  int GetLimits(double& min, double& max);

  bool IsContinuousFocusDrive() const {return false;}


   // action interface
   // ----------------
   int OnDADevice(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMinVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMaxVolt(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct);

   // Sequence functions
   int IsStageSequenceable(bool& isSequenceable) const;
   int GetStageSequenceMaxLength(long& nrEvents) const;
   int StartStageSequence();
   int StopStageSequence();
   int ClearStageSequence();
   int AddToStageSequence(double position);
   int SendStageSequence();

private:
   std::vector<std::string> availableDAs_;
   std::string DADeviceName_;
   MM::SignalIO* DADevice_;
   bool initialized_;
   double minDAVolt_;
   double maxDAVolt_;
   double minStageVolt_;
   double maxStageVolt_;
   double minStagePos_;
   double maxStagePos_;
   double pos_;
   double originPos_;
};

// DAXYStage 

class DAXYStage :
	/*public MM::SequenceableXYStage,*/
	public CXYStageBase<DAXYStage>
{
public:
   DAXYStage();
   ~DAXYStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // XYStage API
   // -----------
   int SetPositionUm(double x, double y);
   int GetPositionUm(double& x, double& y);
   int SetPositionSteps(long x, long y);
   int GetPositionSteps(long& x, long& y);
 
  int SetRelativePositionSteps(long x, long y);
 
  int Home();
  int Stop();
  int SetOrigin();
  int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
  int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
  double GetStepSizeXUm() {return stepSizeXUm_;}
  double GetStepSizeYUm() {return stepSizeYUm_;}

  // Sequence functions
   int IsXYStageSequenceable(bool& isSequenceable) const;
   
   int GetXYStageSequenceMaxLength(long& nrEvents) const;
   int StartXYStageSequence();
   int StopXYStageSequence();
   int ClearXYStageSequence();
   int AddToXYStageSequence(double positionX, double positionY);
   int SendXYStageSequence();
   
   // action interface
   // ----------------
   int OnPort(MM::PropertyBase* pProp, MM::ActionType eAct);

   int OnDADeviceX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDADeviceY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMinVoltX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMaxVoltX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMinPosX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMaxPosX(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMinVoltY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMaxVoltY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMinPosY(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStageMaxPosY(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   void UpdateStepSize();
   std::vector<std::string> availableDAs_;
   std::string DADeviceNameX_;
   std::string DADeviceNameY_;
   MM::SignalIO* DADeviceX_;
   MM::SignalIO* DADeviceY_;
   bool initialized_;
   double minDAVoltX_;
   double maxDAVoltX_;
   double minDAVoltY_;
   double maxDAVoltY_;
   double minStageVoltX_;
   double maxStageVoltX_;
   double minStageVoltY_;
   double maxStageVoltY_;
   double minStagePosX_;
   double maxStagePosX_;
   double minStagePosY_;
   double maxStagePosY_;
   double posX_;
   double posY_;
   double originPosX_;
   double originPosY_;
   double stepSizeXUm_;
   double stepSizeYUm_;

};


// Use several DA (SignalIO) devices as a TTL state device
class DATTLStateDevice : public CStateDeviceBase<DATTLStateDevice>
{
public:
   DATTLStateDevice();
   virtual ~DATTLStateDevice();

   virtual int Initialize();
   virtual int Shutdown();
   virtual void GetName(char* name) const;
   virtual bool Busy();

   virtual unsigned long GetNumberOfPositions() const;

private:
   // Pre-init property action handlers
   int OnNumberOfDADevices(MM::PropertyBase* pProp, MM::ActionType eAct);

   // Post-init property action handlers
   int OnDADevice(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   // Invariant: daDeviceLabels_ and daDevices_ are always size
   // numberOfDADevices_ once Initialize() returns.
   size_t numberOfDADevices_;
   std::vector<std::string> daDeviceLabels_;
   std::vector<MM::SignalIO*> daDevices_;

   bool initialized_;

   long mask_;

   MM::MMTime lastChangeTime_;
};


// Use several DA (SignalIO) devices as a state device with adjustable voltage
class MultiDAStateDevice : public CStateDeviceBase<MultiDAStateDevice>
{
public:
   MultiDAStateDevice();
   virtual ~MultiDAStateDevice();

   virtual int Initialize();
   virtual int Shutdown();
   virtual void GetName(char* name) const;
   virtual bool Busy();

   virtual unsigned long GetNumberOfPositions() const;

private:
   // Pre-init property action handlers
   int OnNumberOfDADevices(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMinVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMaxVoltage(MM::PropertyBase* pProp, MM::ActionType eAct);

   // Post-init property action handlers
   int OnDADevice(MM::PropertyBase* pProp, MM::ActionType eAct, long index);
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct, long index);

private:
   // Invariant: daDeviceLabels_, daDevices_, and voltages_ are always size
   // numberOfDADevices_ once Initialize() returns.
   size_t numberOfDADevices_;
   std::vector<std::string> daDeviceLabels_;
   std::vector<MM::SignalIO*> daDevices_;

   // Voltage range is common to all analog channels and is set before
   // initialization and remains constant.
   double minVoltage_;
   double maxVoltage_;

   bool initialized_;

   std::vector<double> voltages_;

   long mask_;

   MM::MMTime lastChangeTime_;
};


/**
 * Treats an AutoFocus device as a Drive.
 * Can be used to make the AutoFocus offset appear in the position list
 */
class AutoFocusStage : public CStageBase<AutoFocusStage>
{
public:
   AutoFocusStage();
   ~AutoFocusStage();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();

   // Stage API
   // ---------
  int SetPositionUm(double pos);
  int GetPositionUm(double& pos);
  int SetPositionSteps(long steps);
  int GetPositionSteps(long& steps);
  int SetOrigin();
  int GetLimits(double& min, double& max);

  int IsStageSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
  bool IsContinuousFocusDrive() const {return true;}

   // action interface
   // ----------------
   int OnAutoFocusDevice(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   std::vector<std::string> availableAutoFocusDevices_;
   std::string AutoFocusDeviceName_;
   MM::AutoFocus* AutoFocusDevice_;
   bool initialized_;
};

/**
 * StateDeviceShutter: Adds shuttering capabilities to a State Device
 */
class StateDeviceShutter : public CShutterBase<StateDeviceShutter>
{
public:
   StateDeviceShutter();
   ~StateDeviceShutter();
  
   // Device API
   // ----------
   int Initialize();
   int Shutdown() {initialized_ = false; return DEVICE_OK;}
  
   void GetName(char* pszName) const;
   bool Busy();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire (double /* deltaT */) { return DEVICE_UNSUPPORTED_COMMAND;}
   // ---------

   // action interface
   // ----------------
   int OnStateDevice(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int WaitWhileBusy();
   std::vector<std::string> availableStateDevices_;
   std::string stateDeviceName_;
   MM::State* stateDevice_;
   bool initialized_;
   MM::MMTime lastMoveStartTime_;
};

#endif //_UTILITIES_H_
