///////////////////////////////////////////////////////////////////////////////
// FILE:          SpinnakerSDK.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for cameras compatible with Spinnaker SDK
//                
// AUTHOR:        Nenad Amodaj, 2018
// COPYRIGHT:     Luminous Point LLC
//
// LICENSE:			LGPL v3
//						https://www.gnu.org/licenses/lgpl-3.0.en.html
//
// DISCLAIMER:    This file is provided WITHOUT ANY WARRANTY;
//                without even the implied warranty of MERCHANTABILITY or
//                FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//


#pragma once

#include <DeviceBase.h>
#include <ImgBuffer.h>
#include <DeviceUtils.h>
#include <DeviceThreads.h>
#include <cstdint>

#ifdef WIN32
//...
#endif

#ifdef __APPLE__
//...
#endif

#ifdef linux
//...
#endif

#include <string>
#include <map>

static const char* g_DeviceJAICam = "JAICamera";
static const char* g_ReadoutRate = "ReadoutRate";
static const char* g_Gain = "Gain";
static const char* g_NumberOfTaps = "Taps";
static const char* g_ColorFilterArray = "SensorArray";
static const char* g_WhiteBalance = "WhiteBalance";
static const char* g_TriggerMode = "TriggerMode";
static const char* g_TriggerPolarity = "TriggerPolarity";
static const char* g_Temperature = "Temperature";
static const char* g_TestPattern = "TestPattern";
static const char* g_Gamma = "Gamma";

static const char* g_Set = "SetNow";
static const char* g_Off = "Off";
static const char* g_On = "On";
static const char* g_Yes = "Yes";
static const char* g_No = "No";
static const char* g_Software = "Software";
static const char* g_HardwareEdge = "HardwareStandard";
static const char* g_HardwareDuration = "HardwareBulb";
static const char* g_Positive = "Positive";
static const char* g_Negative = "Negative";

static const char* g_PixelType_32bitRGB = "32bitRGB";
static const char* g_PixelType_64bitRGB = "64bitRGB";

static const char* g_pv_BinH = "BinningHorizontal";
static const char* g_pv_BinV = "BinningVertical";
static const char* g_pv_Width = "Width";
static const char* g_pv_Height = "Height";
static const char* g_pv_OffsetX = "OffsetX";
static const char* g_pv_OffsetY = "OffsetY";
static const char* g_pv_PixelFormat = "PixelFormat";

static const char* g_pv_PixelFormat_BGR8 = "BGR8";
static const char* g_pv_PixelFormat_BGR12 = "BGR12p";

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_CAMERA_NOT_FOUND          11010
#define ERR_CAMERA_OPEN_FAILED        11011
#define ERR_IMAGE_TIMED_OUT           11012
#define ERR_INVALID_CHANNEL_INDEX     11013
#define ERR_INTERNAL_ERROR            11014
#define ERR_CAMERA_UNKNOWN_PIXEL_FORMAT 11015
#define ERR_STREAM_OPEN_FAILED          11016
#define ERR_UNSUPPORTED_IMAGE_FORMAT	11017
#define ERR_NOT_ALLOWED_DURING_CAPTURE 11018

//////////////////////////////////////////////////////////////////////////////
// Region of Interest
struct ROI {
   unsigned x;
   unsigned y;
   unsigned xSize;
   unsigned ySize;

   ROI() : x(0), y(0), xSize(0), ySize(0) {}
   ROI(unsigned _x, unsigned _y, unsigned _xSize, unsigned _ySize )
      : x(_x), y(_y), xSize(_xSize), ySize(_ySize) {}
   ~ROI() {}

   bool isEmpty() {return x==0 && y==0 && xSize==0 && ySize == 0;}
   void clear() {x=0; y=0; xSize=0; ySize=0;}
};

enum TriggerSource {
	Software = 0,
	HardwareEdge,
	HardwareDuration
};

enum TriggerPolarity {
	Positive = 0,
	Negative
};

class AcqSequenceThread;

// Pleora classes
class PvGenParameterArray;
class PvDevice;
class PvStream;
class PvBuffer;
class PvResult;
class PvImage;

//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMCamera interfaces
// for all JAI eBus compatible cameras
//
class JAICamera : public CCameraBase<JAICamera>
{
   friend AcqSequenceThread;

public:
   JAICamera();
   ~JAICamera();

   // MMDevice API
   int Initialize();
   int Shutdown();
   void GetName(char* pszName) const;
   bool Busy();
   
   // MMCamera API
   int SnapImage();
   const unsigned char* GetImageBuffer(unsigned chNum);
   const unsigned int* GetImageBufferAsRGB32();
   const unsigned char* GetImageBuffer();

   unsigned GetNumberOfComponents() const;
   unsigned GetNumberOfChannels() const;
   int GetChannelName(unsigned channel, char* name);

   unsigned GetImageWidth() const {return img.Width();}
   unsigned GetImageHeight() const {return img.Height();}
   unsigned GetImageBytesPerPixel() const {return img.Depth();} 
   long GetImageBufferSize() const;
   unsigned GetBitDepth() const;
   int GetBinning() const;
   int SetBinning(int binSize);
   double GetExposure() const;
   void SetExposure(double dExp);
   int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
   int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize);
   int ClearROI();

   // overrides the same in the base class
   int InsertImage();
   int PrepareSequenceAcqusition();
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
   int StartSequenceAcquisition(double interval);
   int StopSequenceAcquisition(); 
   bool IsCapturing();
   
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGamma(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWhiteBalance(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTestPattern(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemperatureSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFps(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTriggerPolarity(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int ResizeImageBuffer();
   int PushImage(unsigned char* imgBuf);
	int processPvError(const PvResult& pvr);
	static void convert_BGR8_RGBA32(const uint8_t* src, uint8_t* dest, unsigned w, unsigned h);
	static void convert_BGR12P_RGBA64(const uint8_t* src, uint8_t* dest, unsigned w, unsigned h);
	bool verifyPvFormat(const PvImage* pvimg);
	void ClearPvBuffers();

   ImgBuffer img;
   bool initialized;
   bool stopOnOverflow;
   long acquiring;
	int bitDepth;
	int pixelSize;

	PvDevice* camera;
	PvGenParameterArray* genParams;
	std::string connectionId;
	std::vector<PvBuffer*> pvBuffers;

   friend class AcqSequenceThread;
   AcqSequenceThread*   liveAcqThd_;
};

/*
 * Acquisition thread
 */
class AcqSequenceThread : public MMDeviceThreadBase
{
   public:
      AcqSequenceThread(JAICamera* camera) : 
         stopFlag(0), moduleInstance(camera), numFrames(0) {}
      ~AcqSequenceThread() {}
      int svc (void);

		void Stop();
      void Start() {stopFlag = false; activate();}
      void SetNumFrames(unsigned numf) {numFrames = numf;}
    
   private:
		int processPvError(PvResult pvr, std::shared_ptr<PvStream>& stream);
      long stopFlag;
      JAICamera* moduleInstance;
      unsigned numFrames;
};

/*
PARAMETERS:

1. Communication link parameters display

Using non-connected PvDevice
Retrieving communication link parameters array
Dumping communication link parameters array content

Array has 32 parameters
Root\Communication:AnswerTimeout, Integer: 1000
Root\Communication:CommandRetryCount, Integer: 3
Root\Communication:DefaultMCTT, Integer: 400
Root\Communication:DefaultMCRC, Integer: 3
Root\Communication:ReadMemPacketSize, Integer: 552
Root\Communication:WriteMemPacketSize, Integer: 552
Root\Heartbeat:HeartbeatInterval, Integer: 1500
Root\Heartbeat:DefaultHeartbeatTimeout, Integer: 5000
Root\Heartbeat:DisconnectOnAnyTimeout, Boolean: FALSE
Root\Connection:IPAddress, Integer: 0
Root\Connection:CommandPort, Integer: 0
Root\Connection:MessagingPort, Integer: 0
Root\Connection:ForcedCommandPortEnabled, Boolean: FALSE
Root\Connection:ForcedCommandPort, {Not Available}
Root\Connection:ForcedMessagingPortEnabled, Boolean: FALSE
Root\Connection:ForcedMessagingPort, {Not Available}
Root\Statistics:StatisticsReset, {Not readable}
Root\Statistics:CommandPendingAcknowledges, Integer: 0
Root\Statistics:CommandRetries, Integer: 0
Root\Statistics:CommandRetriesMax, Integer: 0
Root\Statistics:CommandSendFailures, Integer: 0
Root\Statistics:MessagingRetries, Integer: 0
Root\Statistics:MessagingRetriesMax, Integer: 0
Root\Statistics:MessagingAckSendFailures, Integer: 0
Root\StreamingPacketSize:AutoNegotiation, Boolean: TRUE
Root\StreamingPacketSize:DefaultPacketSize, {Not Available}
Root\DeviceGenICamXMLAccess:DeviceGenICamXMLLocation, Enum: Default
Root\DeviceGenICamXMLAccess:DeviceGenICamXMLFile, {Not Available}
Root\DeviceGenICamXMLAccess:DeviceGenICamXMLFileValid, {Not Available}
Root\DeviceGenICamXMLAccess:UseManifests, Boolean: FALSE
Root\Recovery:LinkRecoveryEnabled, Boolean: FALSE
Root\Recovery:RecoveryStatus, Enum: StatusOK

2. Device parameters display

Array has 227 parameters
* Root\DeviceControl:DeviceVendorName, String: JAI Corporation
> Root\DeviceControl:DeviceModelName, String: AP-3200T-USB
Root\DeviceControl:DeviceManufacturerInfo, String: See the possibilities
> Root\DeviceControl:DeviceVersion, String: 0.1.0.0
* Root\DeviceControl:DeviceFirmwareVersion, String: 0.8.0.0
* Root\DeviceControl:DeviceFpgaVersion, String: 0.1.2.6
* Root\DeviceControl:DeviceSerialNumber, String: U320378
Root\DeviceControl:DeviceUserID, String:
Root\DeviceControl:DeviceSFNCVersionMajor, Integer: 2
Root\DeviceControl:DeviceSFNCVersionMinor, Integer: 3
Root\DeviceControl:DeviceSFNCVersionSubMinor, Integer: 0
Root\DeviceControl:DeviceManifestEntrySelector, Integer: 1
Root\DeviceControl:DeviceManifestXMLMajorVersion, Integer: 0
Root\DeviceControl:DeviceManifestXMLMinorVersion, Integer: 3
Root\DeviceControl:DeviceManifestXMLSubMinorVersion, Integer: 3
Root\DeviceControl:DeviceManifestSchemaMajorVersion, Integer: 1
Root\DeviceControl:DeviceManifestSchemaMinorVersion, Integer: 1
Root\DeviceControl:DeviceTemperatureSelector, Enum: Mainboard
> Root\DeviceControl:DeviceTemperature, Float: 52.8125
Root\DeviceControl:Timestamp, Integer: 62810573619030
Root\DeviceControl:TimestampReset, {Not readable}
Root\DeviceControl:TimestampLatch, {Not readable}
Root\DeviceControl:TimestampLatchValue, Integer: 0
Root\DeviceControl:DeviceReset, {Not readable}
* Root\ImageFormatControl:SensorWidth, Integer: 2064
* Root\ImageFormatControl:SensorHeight, Integer: 1544
>> Root\ImageFormatControl:SensorDigitizationBits, Enum: Twelve
* Root\ImageFormatControl:WidthMax, Integer: 2064
* Root\ImageFormatControl:HeightMax, Integer: 1544
* Root\ImageFormatControl:Width, Integer: 2064
* Root\ImageFormatControl:Height, Integer: 1544
* Root\ImageFormatControl:OffsetX, Integer: 0
* Root\ImageFormatControl:OffsetY, Integer: 0
Root\ImageFormatControl:BinningHorizontalMode, Enum: Sum
* Root\ImageFormatControl:BinningHorizontal, Integer: 1
Root\ImageFormatControl:BinningVerticalMode, Enum: Sum
* Root\ImageFormatControl:BinningVertical, Integer: 1
Root\ImageFormatControl:PixelFormat, Enum: BGR8
Root\ImageFormatControl:TestPattern, Enum: Off
* Root\AcquisitionControl:AcquisitionMode, Enum: SingleFrame
* Root\AcquisitionControl:AcquisitionStart, {Not readable}
* Root\AcquisitionControl:AcquisitionStop, {Not readable}
* Root\AcquisitionControl:AcquisitionFrameCount, Integer: 1
> Root\AcquisitionControl:AcquisitionFrameRate, Float: 38.348
>> Root\AcquisitionControl:TriggerSelector, Enum: AcquisitionStart
>> Root\AcquisitionControl:TriggerMode, Enum: Off
Root\AcquisitionControl:TriggerSoftware, {Not readable}
>> Root\AcquisitionControl:TriggerSource, Enum: Low
>> Root\AcquisitionControl:TriggerActivation, Enum: RisingEdge
Root\AcquisitionControl:TriggerOverlap, Enum: Off
Root\AcquisitionControl:TriggerDelay, Float: 0
Root\AcquisitionControl:ExposureModeOption, Enum: Off
* Root\AcquisitionControl:ExposureMode, Enum: Timed
Root\AcquisitionControl:ExposureTimeMode, Enum: Common
Root\AcquisitionControl:ShortExposureMode, Enum: Off
Root\AcquisitionControl:ExposureTimeSelector, Enum: Common
>> Root\AcquisitionControl:ExposureTime, Float: 25845
* Root\AcquisitionControl:ExposureAuto, Enum: Off
Root\AnalogControl:IndividualGainMode, Enum: Off
Root\AnalogControl:GainSelector, Enum: AnalogAll
>> Root\AnalogControl:Gain, Float: 1
>> Root\AnalogControl:GainAuto, Enum: Off
>> Root\AnalogControl:BalanceWhiteAuto, Enum: Off
Root\AnalogControl:BlackLevelSelector, Enum: DigitalAll
Root\AnalogControl:BlackLevel, Float: 0
Root\AnalogControl:Gamma, Float: 0.45
Root\AnalogControl:LUTMode, Enum: Off
Root\LUTControl:LUTSelector, Enum: Red
Root\LUTControl:LUTIndex, Integer: 0
Root\LUTControl:LUTValue, Integer: 0
Root\ColorTransformationControl:ColorTransformationMode, Enum: RGB
Root\ColorTransformationControl:ColorTransformationRGBMode, Enum: Off
Root\ColorTransformationControl:ColorMatrixValueSelector, Enum: ColorMatrixRR
Root\ColorTransformationControl:ColorMatrixValue, Float: 1
Root\DigitalIOControl:LineSelector, Enum: Line2
Root\DigitalIOControl:LineSource, Enum: Low
Root\DigitalIOControl:LineInverter, Boolean: FALSE
Root\DigitalIOControl:LineStatus, Boolean: FALSE
Root\DigitalIOControl:LineMode, Enum: Output
Root\DigitalIOControl:LineFormat, Enum: OptoCoupled
Root\DigitalIOControl:LineStatusAll, Integer: 0
Root\DigitalIOControl:OptInFilterSelector, Enum: Off
Root\DigitalIOControl:UserOutputSelector, Enum: UserOutput0
Root\DigitalIOControl:UserOutputValue, Boolean: FALSE
Root\CounterAndTimerControl:CounterSelector, Enum: Counter0
Root\CounterAndTimerControl:CounterEventSource, Enum: Off
Root\CounterAndTimerControl:CounterEventActivation, Enum: RisingEdge
Root\CounterAndTimerControl:CounterReset, {Not readable}
Root\CounterAndTimerControl:CounterRefresh, {Not readable}
Root\CounterAndTimerControl:CounterValue, Integer: 0
Root\CounterAndTimerControl:CounterStatus, Enum: CounterIdle
Root\UserSetControl:UserSetSelector, Enum: Default
Root\UserSetControl:UserSetLoad, {Not readable}
Root\UserSetControl:UserSetSave, {Not Available}
Root\SequencerControl:SequencerMode, Enum: Off
Root\SequencerControl:SequencerModeSelect, Enum: TriggerSequenceMode
Root\SequencerControl:SequencerConfigurationMode, Enum: On
Root\SequencerControl:SequencerSetSelector, Integer: 1
Root\SequencerControl:SequencerFrameNumber, Integer: 1
Root\SequencerControl:SequencerSetNext, Integer: 2
Root\SequencerControl:SequencerWidth, Integer: 2064
Root\SequencerControl:SequencerHeight, Integer: 1544
Root\SequencerControl:SequencerOffsetX, Integer: 0
Root\SequencerControl:SequencerOffsetY, Integer: 0
Root\SequencerControl:SequencerGainAnalogAll, Float: 1
Root\SequencerControl:SequencerGainAnalogRed, Float: 1
Root\SequencerControl:SequencerGainAnalogGreen, Float: 1
Root\SequencerControl:SequencerGainAnalogBlue, Float: 1
Root\SequencerControl:SequencerExposureTimeCommon, Float: 25845
Root\SequencerControl:SequencerExposureTimeRed, Float: 25845
Root\SequencerControl:SequencerExposureTimeGreen, Float: 25845
Root\SequencerControl:SequencerExposureTimeBlue, Float: 25845
Root\SequencerControl:SequencerBlackLevelDigitalAll, Float: 0
Root\SequencerControl:SequencerLutEnable, Boolean: FALSE
Root\SequencerControl:SequencerBinningHorizontal, Integer: 1
Root\SequencerControl:SequencerBinningVertical, Integer: 1
Root\SequencerControl:SequencerRepetition, Integer: 1
Root\SequencerControl:SequencerLutMode, Enum: Gamma
Root\SequencerControl:SequencerSetActive, Integer: 1
Root\SequencerControl:SequencerCommandIndex, Integer: 1
Root\SequencerControl:SequencerSetStart, Integer: 1
Root\SequencerControl:SequencerReset, {Not Available}
Root\ChunkDataControl:ChunkModeActive, Boolean: FALSE
Root\ChunkDataControl:ChunkSelector, Enum: OffsetX
Root\ChunkDataControl:ChunkEnable, Boolean: FALSE
Root\ChunkDataControl:ChunkOffsetX, {Not Available}
Root\ChunkDataControl:ChunkOffsetY, {Not Available}
Root\ChunkDataControl:ChunkWidth, {Not Available}
Root\ChunkDataControl:ChunkHeight, {Not Available}
Root\ChunkDataControl:ChunkPixelFormat, {Not Available}
Root\ChunkDataControl:ChunkTimestamp, {Not Available}
Root\ChunkDataControl:ChunkLineStatusAll, {Not Available}
Root\ChunkDataControl:ChunkExposureTimeMode, {Not Available}
Root\ChunkDataControl:ChunkExposureTimeGreen, {Not Available}
Root\ChunkDataControl:ChunkExposureTimeRed, {Not Available}
Root\ChunkDataControl:ChunkExposureTimeBlue, {Not Available}
Root\ChunkDataControl:ChunkIndividualGainMode, {Not Available}
Root\ChunkDataControl:ChunkGainAnalogAll, {Not Available}
Root\ChunkDataControl:ChunkGainAnalogRed, {Not Available}
Root\ChunkDataControl:ChunkGainAnalogBlue, {Not Available}
Root\ChunkDataControl:ChunkBlackLevelDigitalAll, {Not Available}
Root\ChunkDataControl:ChunkBlackLevelDigitalRed, {Not Available}
Root\ChunkDataControl:ChunkBlackLevelDigitalBlue, {Not Available}
Root\ChunkDataControl:ChunkBinningHV_LUTEnable, {Not Available}
Root\ChunkDataControl:ChunkSequencerSetActive, {Not Available}
Root\ChunkDataControl:ChunkFrameTriggerCounter, {Not Available}
Root\ChunkDataControl:ChunkExposureStartCounter, {Not Available}
Root\ChunkDataControl:ChunkSensorReadOutStartCounter, {Not Available}
Root\ChunkDataControl:ChunkFrameTransferEndCounter, {Not Available}
Root\ChunkDataControl:ChunkLineStatusAllOnExposureStart, {Not Available}
Root\ChunkDataControl:ChunkLineStatusAllOnFVALStart, {Not Available}
Root\ChunkDataControl:ChunkDeviceTemperature, {Not Available}
Root\ChunkDataControl:ChunkDeviceSerialNumber, {Not Available}
Root\ChunkDataControl:ChunkDeviceUserID, {Not Available}
Root\TestControl:TestPendingAck, Integer: 0
Root\TransportLayerControl:PayloadSize, Integer: 9560448
Root\TransportLayerControl:DeviceTapGeometry, Enum: Geometry_1X_1Y
Root\JAICustomControlPulseGenerators:ClockPreScaler, Integer: 165
Root\JAICustomControlPulseGenerators:PulseGeneratorClock, Float: 0.45
Root\JAICustomControlPulseGenerators:PulseGeneratorSelector, Enum: PulseGenerator0
Root\JAICustomControlPulseGenerators:PulseGeneratorLength, Integer: 30000
Root\JAICustomControlPulseGenerators:PulseGeneratorLengthMs, Float: 66.6667
Root\JAICustomControlPulseGenerators:PulseGeneratorFrequency, Float: 15
Root\JAICustomControlPulseGenerators:PulseGeneratorStartPoint, Integer: 0
Root\JAICustomControlPulseGenerators:PulseGeneratorStartPointMs, Float: 0
Root\JAICustomControlPulseGenerators:PulseGeneratorEndPoint, Integer: 15000
Root\JAICustomControlPulseGenerators:PulseGeneratorEndPointMs, Float: 33.3333
Root\JAICustomControlPulseGenerators:PulseGeneratorPulseWidth, Float: 33.3333
Root\JAICustomControlPulseGenerators:PulseGeneratorRepeatCount, Integer: 0
Root\JAICustomControlPulseGenerators:PulseGeneratorClearActivation, Enum: Off
Root\JAICustomControlPulseGenerators:PulseGeneratorClearSource, Enum: Low
Root\JAICustomControlPulseGenerators:PulseGeneratorClearInverter, Boolean: FALSE
Root\JAICustomControlPulseGenerators:PulseGeneratorClearSyncMode, Enum: AsyncMode
Root\JAICustomControlALC:ALCReference, Integer: 50
Root\JAICustomControlALC:ALCAreaSelector, Enum: LowRight
Root\JAICustomControlALC:ALCAreaEnable, Boolean: TRUE
Root\JAICustomControlALC:ALCAreaEnableAll, Boolean: TRUE
Root\JAICustomControlALC:AutoShutterControlExposureMin, Integer: 100
Root\JAICustomControlALC:AutoShutterControlExposureMax, Integer: 25845
Root\JAICustomControlALC:AutoGainControlGainRawMin, Integer: 100
Root\JAICustomControlALC:AutoGainControlGainRawMax, Integer: 800
Root\JAICustomControlALC:ALCControlSpeed, Integer: 4
Root\JAICustomControlALC:ALCStatus, Enum: Off
Root\JAICustomControlALC:AutoControlStatus, Enum: Idle
Root\JAICustomControlAWB:AWBAreaSelector, Enum: LowRight
Root\JAICustomControlAWB:AWBAreaEnable, Boolean: TRUE
Root\JAICustomControlAWB:AWBAreaEnableAll, Boolean: TRUE
Root\JAICustomControlAWB:AWBControlSpeed, Integer: 4
Root\JAICustomControlAWB:AWBControlStatus, Enum: Idle
Root\JAICustomControlBlemish:BlemishEnable, Boolean: TRUE
Root\JAICustomControlBlemish:BlemishDetect, {Not Available}
Root\JAICustomControlBlemish:BlemishStore, {Not readable}
Root\JAICustomControlBlemish:BlemishSelector, Enum: Red
Root\JAICustomControlBlemish:BlemishDetectThreshold, Integer: 10
Root\JAICustomControlBlemish:BlemishCompensationIndex, Integer: 1
Root\JAICustomControlBlemish:BlemishCompensationPositionX, Integer: -1
Root\JAICustomControlBlemish:BlemishCompensationPositionY, Integer: -1
Root\JAICustomControlBlemish:BlemishCompensationDataClear, {Not readable}
Root\JAICustomControlBlemish:BlemishCompensationNumber, Integer: 0
Root\JAICustomControlShading:ShadingCorrectionMode, Enum: FlatShading
Root\JAICustomControlShading:ShadingMode, Enum: Off
Root\JAICustomControlShading:PerformShadingCalibration, {Not Available}
Root\JAICustomControlShading:ShadingDetectResult, Enum: Complete
Root\JAICustomControlOverlapMultiROI:MultiRoiMode, Enum: Off
Root\JAICustomControlOverlapMultiROI:MultiRoiIndex, Enum: Index1
Root\JAICustomControlOverlapMultiROI:MultiRoiWidth, Integer: 2064
Root\JAICustomControlOverlapMultiROI:MultiRoiHeight, Integer: 1544
Root\JAICustomControlOverlapMultiROI:MultiRoiOffsetX, Integer: 0
Root\JAICustomControlOverlapMultiROI:MultiRoiOffsetY, Integer: 0
Root\JAICustomControlOverlapMultiROI:MultiRoiIndexMax, Integer: 1
Root\JAICustomControlSensorMultiROI:SensorMultiRoiEnable, Boolean: FALSE
Root\JAICustomControlSensorMultiROI:SensorMultiRoiIndex, Enum: Index1
Root\JAICustomControlSensorMultiROI:SensorMultiRoiWidth, Integer: 2064
Root\JAICustomControlSensorMultiROI:SensorMultiRoiHeight, Integer: 1544
Root\JAICustomControlSensorMultiROI:SensorMultiRoiOffsetX, Integer: 0
Root\JAICustomControlSensorMultiROI:SensorMultiRoiOffsetY, Integer: 0
Root\JAICustomControlSensorMultiROI:SensorMultiRoiHEnable, Boolean: FALSE
Root\JAICustomControlSensorMultiROI:SensorMultiRoiVEnable, Boolean: FALSE
Root\JAICustomControlMisc:VideoProcessBypassMode, Enum: Off
Root\JAICustomControlMisc:EnhancerSelector, Enum: Edge
Root\JAICustomControlMisc:EnhancerEnable, Boolean: FALSE
Root\JAICustomControlMisc:ColorEnhancerSelector, Enum: Red
Root\JAICustomControlMisc:ColorEnhancerValue, Float: 0
Root\JAICustomControlMisc:EdgeEnhancerLevel, Enum: Middle
Root\JAICustomControlMisc:VideoSendMode, Enum: NormalMode
Root\JAICustomFactorySetting:FactorySensorLVDSChNum, Integer: 4

3. Image stream parameters display

Array has 41 parameters
* Root\Connection:DeviceGUID, String: 14FB0164E37A
Root\Connection:ConnectionSpeed, Enum: SuperSpeed
Root\Connection:MaxPacketSize, Integer: 1024
Root\Connection:MaxBurst, Integer: 15
Root\Connection:Channel, Integer: 0
Root\Statistics\General:Reset, {Not readable}
Root\Statistics\General:BitsCount, Integer: 0
Root\Statistics\General:BytesCount, Integer: 0
Root\Statistics\General:BlockCount, Integer: 0
Root\Statistics\General:SamplingTime, Integer: 0
Root\Statistics\General:AcquisitionRate, Float: 0
Root\Statistics\General:AcquisitionRateAverage, Float: 0
Root\Statistics\General:Bandwidth, Float: 0
Root\Statistics\General:BandwidthAverage, Float: 0
Root\Statistics\General:AbortCountedAsError, Boolean: FALSE
Root\Statistics\General:ResyncCountedAsError, Boolean: FALSE
Root\Statistics\General:ErrorCount, Integer: 0
Root\Statistics\General:LastError, Enum: None
Root\Statistics\General:TimestampSourceEffective, Enum: Hardware
Root\Statistics\General:TimestampSourcePreferred, Enum: Software
Root\Statistics\Counters\Status:DataOverrun, {Not Available}
Root\Statistics\Counters\Status:PartialLineMissing, {Not Available}
Root\Statistics\Counters\Status:FullLineMissing, {Not Available}
Root\Statistics\Counters\Status:BlocksDropped, {Not Available}
Root\Statistics\Counters\Status:InterlacedEven, {Not Available}
Root\Statistics\Counters\Status:InterlacedOdd, {Not Available}
Root\Statistics\Counters\Errors:ResultImageError, Integer: 0
Root\Statistics\Counters\Errors:ResultBufferTooSmall, Integer: 0
Root\Statistics\Counters\Errors:ResultAborted, Integer: 0
Root\Statistics\Counters\Errors:ResultResync, Integer: 0
Root\Statistics\Counters\Errors:ResultInvalidDataFormat, Integer: 0
Root\Statistics\Counters:BlockIDsMissing, Integer: 0
Root\Statistics\Counters:PipelineBlocksDropped, Integer: 0
*/
