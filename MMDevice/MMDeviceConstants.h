///////////////////////////////////////////////////////////////////////////////
// FILE:          MMDeviceConstants.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMDevice - Device adapter kit
//-----------------------------------------------------------------------------
// DESCRIPTION:   Global constants and enumeration types
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 07/11/2005
// COPYRIGHT:     University of California, San Francisco, 2006
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
//
// CVS:           $Id$
//

#ifndef _MMDEVICE_CONSTANTS_H_
#define _MMDEVICE_CONSTANTS_H_

///////////////////////////////////////////////////////////////////////////////
// Global error codes
//
#define MM_CODE_OK 0       // command succeeded
#define MM_CODE_ERR 1      // undefined error occurred

//////////////////////////////////////////////////////////////////////////////
// Global constants
//
// common device error codes
// TODO: revise values - the range might clash with the native driver codes

#define DEVICE_OK                      0
#define DEVICE_ERR                     1 // generic, undefined error
#define DEVICE_INVALID_PROPERTY        2
#define DEVICE_INVALID_PROPERTY_VALUE  3
#define DEVICE_DUPLICATE_PROPERTY      4
#define DEVICE_INVALID_PROPERTY_TYPE   5
#define DEVICE_NATIVE_MODULE_FAILED    6 // unable to load or initialize native module
#define DEVICE_UNSUPPORTED_DATA_FORMAT 7 // we don't know how to handle camera data
#define DEVICE_INTERNAL_INCONSISTENCY  8 // internal data structures are corrupted
#define DEVICE_NOT_SUPPORTED           9
#define DEVICE_UNKNOWN_LABEL           10
#define DEVICE_UNSUPPORTED_COMMAND     11
#define DEVICE_UNKNOWN_POSITION        12
#define DEVICE_NO_CALLBACK_REGISTERED  13
#define DEVICE_SERIAL_COMMAND_FAILED   14
#define DEVICE_SERIAL_BUFFER_OVERRUN   15
#define DEVICE_SERIAL_INVALID_RESPONSE 16
#define DEVICE_SERIAL_TIMEOUT          17
#define DEVICE_SELF_REFERENCE          18
#define DEVICE_NO_PROPERTY_DATA        19
#define DEVICE_DUPLICATE_LABEL         20
#define DEVICE_INVALID_INPUT_PARAM     21
#define DEVICE_BUFFER_OVERFLOW         22
#define DEVICE_NONEXISTENT_CHANNEL     23
#define DEVICE_INVALID_PROPERTY_LIMTS  24
#define DEVICE_SNAP_IMAGE_FAILED       25
#define DEVICE_IMAGE_PARAMS_FAILED     26
#define DEVICE_CORE_FOCUS_STAGE_UNDEF  27
#define DEVICE_CORE_EXPOSURE_FAILED    28
#define DEVICE_CORE_CONFIG_FAILED      29
#define DEVICE_CAMERA_BUSY_ACQUIRING   30
#define DEVICE_INCOMPATIBLE_IMAGE      31
#define DEVICE_CAN_NOT_SET_PROPERTY    32
#define DEVICE_CORE_CHANNEL_PRESETS_FAILED  33
#define DEVICE_LOCALLY_DEFINED_ERROR   34
#define DEVICE_NOT_CONNECTED           35
#define DEVICE_COMM_HUB_MISSING        36
#define DEVICE_DUPLICATE_LIBRARY       37
#define DEVICE_PROPERTY_NOT_SEQUENCEABLE 38
#define DEVICE_SEQUENCE_TOO_LARGE      39
#define DEVICE_OUT_OF_MEMORY           40
#define DEVICE_NOT_YET_IMPLEMENTED     41


namespace MM {
   const int MaxStrLength = 1024;

   // system-wide property names
   const char* const g_Keyword_Name             = "Name";
   const char* const g_Keyword_Description      = "Description";
   const char* const g_Keyword_CameraName       = "CameraName";
   const char* const g_Keyword_CameraID         = "CameraID";
   const char* const g_Keyword_CameraChannelName     = "CameraChannelName";
   const char* const g_Keyword_CameraChannelIndex    = "CameraChannelIndex";
   const char* const g_Keyword_Binning          = "Binning";
   const char* const g_Keyword_Exposure         = "Exposure";
   const char* const g_Keyword_ActualExposure   = "ActualExposure";
   const char* const g_Keyword_ActualInterval_ms= "ActualInterval-ms";
   const char* const g_Keyword_Interval_ms      = "Interval-ms";
   const char* const g_Keyword_Elapsed_Time_ms  = "ElapsedTime-ms";
   const char* const g_Keyword_PixelType        = "PixelType";
   const char* const g_Keyword_ReadoutTime      = "ReadoutTime";
   const char* const g_Keyword_ReadoutMode      = "ReadoutMode";
   const char* const g_Keyword_Gain             = "Gain";
   const char* const g_Keyword_EMGain           = "EMGain";
   const char* const g_Keyword_Offset           = "Offset";
   const char* const g_Keyword_CCDTemperature   = "CCDTemperature";
   const char* const g_Keyword_CCDTemperatureSetPoint = "CCDTemperatureSetPoint";
   const char* const g_Keyword_State            = "State";
   const char* const g_Keyword_Label            = "Label";
   const char* const g_Keyword_Position         = "Position";
   const char* const g_Keyword_Type             = "Type";
   const char* const g_Keyword_Delay            = "Delay_ms";
   const char* const g_Keyword_BaudRate         = "BaudRate";
   const char* const g_Keyword_DataBits         = "DataBits";
   const char* const g_Keyword_StopBits         = "StopBits";
   const char* const g_Keyword_Parity           = "Parity";
   const char* const g_Keyword_Handshaking      = "Handshaking";
   const char* const g_Keyword_DelayBetweenCharsMs = "DelayBetweenCharsMs";
   const char* const g_Keyword_Port             = "Port";
   const char* const g_Keyword_AnswerTimeout    = "AnswerTimeout";
   const char* const g_Keyword_Speed            = "Speed";
   const char* const g_Keyword_CoreDevice       = "Core";
   const char* const g_Keyword_CoreInitialize   = "Initialize";
   const char* const g_Keyword_CoreCamera       = "Camera";
   const char* const g_Keyword_CoreShutter      = "Shutter";
   const char* const g_Keyword_CoreXYStage      = "XYStage";
   const char* const g_Keyword_CoreFocus        = "Focus";
   const char* const g_Keyword_CoreAutoFocus    = "AutoFocus";
   const char* const g_Keyword_CoreAutoShutter  = "AutoShutter";
   const char* const g_Keyword_CoreChannelGroup = "ChannelGroup";
   const char* const g_Keyword_CoreImageProcessor = "ImageProcessor";
   const char* const g_Keyword_CoreSLM          = "SLM";
   const char* const g_Keyword_CoreGalvo        = "Galvo";
   const char* const g_Keyword_CoreTimeoutMs    = "TimeoutMs";
   const char* const g_Keyword_Channel          = "Channel";
   const char* const g_Keyword_Version          = "Version";
   const char* const g_Keyword_ColorMode        = "ColorMode";
   const char* const g_Keyword_Transpose_SwapXY = "TransposeXY";
   const char* const g_Keyword_Transpose_MirrorX = "TransposeMirrorX";
   const char* const g_Keyword_Transpose_MirrorY = "TransposeMirrorY";
   const char* const g_Keyword_Transpose_Correction = "TransposeCorrection";
   const char* const g_Keyword_Closed_Position = "ClosedPosition";
   const char* const g_Keyword_HubID = "HubID";


   // image annotations
   const char* const g_Keyword_Meatdata_Exposure    = "Exposure-ms";
   const char* const g_Keyword_Metadata_Score       = "Score";
   const char* const g_Keyword_Metadata_ImageNumber = "ImageNumber";
   const char* const g_Keyword_Metadata_StartTime   = "StartTime-ms";
   const char* const g_Keyword_Metadata_ROI_X       = "ROI-X-start";
   const char* const g_Keyword_Metadata_ROI_Y       = "ROI-Y-start";

   // configuration file format constants
   const char* const g_FieldDelimiters = ",";
   const char* const g_CFGCommand_Device = "Device";
   const char* const g_CFGCommand_Label = "Label";
   const char* const g_CFGCommand_Property = "Property";
   const char* const g_CFGCommand_Configuration = "Config";
   const char* const g_CFGCommand_ConfigGroup = "ConfigGroup";
   const char* const g_CFGCommand_Equipment = "Equipment";
   const char* const g_CFGCommand_Delay = "Delay";
   const char* const g_CFGCommand_ImageSynchro = "ImageSynchro";
   const char* const g_CFGCommand_ConfigPixelSize = "ConfigPixelSize";
   const char* const g_CFGCommand_PixelSize_um = "PixelSize_um";
   const char* const g_CFGCommand_PixelSizeAffine = "PixelSizeAffine";
   const char* const g_CFGCommand_ParentID = "Parent";
   const char* const g_CFGCommand_FocusDirection = "FocusDirection";

   // configuration groups
   const char* const g_CFGGroup_System = "System";
   const char* const g_CFGGroup_System_Startup = "Startup";
   const char* const g_CFGGroup_System_Shutdown = "Shutdown";
   const char* const g_CFGGroup_PixelSizeUm = "PixelSize_um";

   // serial port constants
   const int _DATABITS_5 = 5;
   const int _DATABITS_6 = 6;
   const int _DATABITS_7 = 7;
   const int _DATABITS_8 = 8;

   const int _FLOWCONTROL_NONE = 0;
   const int _FLOWCONTROL_RTSCTS_IN = 1;
   const int _FLOWCONTROL_RTSCTS_OUT = 2;
   const int _FLOWCONTROL_XONXOFF_IN = 4;
   const int _FLOWCONTROL_XONXOFF_OUT = 8;

   const int _PARITY_EVEN = 2;
   const int _PARITY_MARK = 3;
   const int _PARITY_NONE = 0;
   const int _PARITY_ODD = 1;
   const int _PARITY_SPACE = 4;

   const int _STOPBITS_1 = 1;
   const int _STOPBITS_1_5 = 3;
   const int _STOPBITS_2 = 2;


   //////////////////////////////////////////////////////////////////////////////
   // Type constants
   //
   enum DeviceType {
      UnknownType=0,
      AnyType,
      CameraDevice,
      ShutterDevice,
      StateDevice,
      StageDevice,
      XYStageDevice,
      SerialDevice,
      GenericDevice,
      AutoFocusDevice,
      CoreDevice,
      ImageProcessorDevice,
      SignalIODevice,
      MagnifierDevice,
      SLMDevice,
      HubDevice,
      GalvoDevice
   };

   enum PropertyType {
      Undef,
      String,
      Float,
      Integer
   };

   enum ActionType {
      NoAction,
      BeforeGet,
      AfterSet,
      IsSequenceable,
      AfterLoadSequence,
      StartSequence,
      StopSequence
   };

   enum PortType {
      InvalidPort,
      SerialPort,
      USBPort,
      HIDPort
   };

   enum FocusDirection {
      FocusDirectionUnknown,
      FocusDirectionTowardSample,
      FocusDirectionAwayFromSample,
   };

   //////////////////////////////////////////////////////////////////////////////
   // Notification constants
   //
   enum DeviceNotification {
      Attention,
      Done,
      StatusChanged
   };

   // Device discovery
   enum DeviceDetectionStatus{
      Unimplemented = -2,    // -- there is as yet no mechanism to programmatically detect the device
      Misconfigured = -1,    // -- some information needed to communicate with the device is invalid
      CanNotCommunicate = 0, // -- communication attributes are valid, but the device does not respond
      CanCommunicate = 1     // -- communication verified, parameters have been set to valid values.
   };

} // namespace MM

#endif //_MMDEVICE_CONSTANTS_H_
