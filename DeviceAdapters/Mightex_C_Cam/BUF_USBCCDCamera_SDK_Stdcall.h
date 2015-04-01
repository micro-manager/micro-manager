// The following ifdef block is the standard way of creating macros which make exporting 
// from a DLL simpler. All files within this DLL are compiled with the MTUSBDLL_EXPORTS
// symbol defined on the command line. this symbol should not be defined on any project
// that uses this DLL. This way any other project whose source files include this file see 
// MTUSBDLL_API functions as being imported from a DLL, wheras this DLL sees symbols
// defined with this macro as being exported.

#define GRAB_FRAME_FOREVER	0x8888

#pragma pack(1)

typedef struct {
    int CameraID;
    int Row;
    int Column;
    int Bin;
    int XStart;
    int YStart;
    int ExposureTime;
    int RedGain;
    int GreenGain;
    int BlueGain;
    int TimeStamp;
    int TriggerOccurred;
    int TriggerEventCount;
    int UserMark;
    int FrameTime;
    int CCDFrequency;
	
    int FrameProcessType;
    int tFilterAcceptForFile;
} TProcessedDataProperty;

#pragma pack()

typedef void (* DeviceFaultCallBack)( int DeviceType );
typedef void (* FrameDataCallBack)( TProcessedDataProperty* Attributes, unsigned char *BytePtr );

// Import functions:
typedef int (WINAPI * BUFCCDUSB_InitDevicePtr) ( void );
typedef void (WINAPI * BUFCCDUSB_UnInitDevicePtr) ( void );
typedef int (WINAPI * BUFCCDUSB_GetModuleNoSerialNoPtr) ( int DeviceID, char *ModuleNo, char *SerialNo);
typedef int (WINAPI * BUFCCDUSB_AddDeviceToWorkingSetPtr) ( int DeviceID );
typedef int (WINAPI * BUFCCDUSB_RemoveDeviceFromWorkingSetPtr) ( int DeviceID );
typedef int (WINAPI * BUFCCDUSB_ActiveDeviceInWorkingSetPtr) ( int DeviceID, int Active );
typedef int (WINAPI * BUFCCDUSB_SetCameraWorkModePtr) ( int DeviceID, int WorkMode );
typedef int (WINAPI * BUFCCDUSB_StartCameraEnginePtr) ( HWND ParentHandle, int CameraBitOption );
typedef int (WINAPI * BUFCCDUSB_StopCameraEnginePtr) ( void );
typedef int (WINAPI * BUFCCDUSB_StartFrameGrabPtr) ( int TotalFrames );
typedef int (WINAPI * BUFCCDUSB_StopFrameGrabPtr) ( void );
typedef int (WINAPI * BUFCCDUSB_SetCustomizedResolutionPtr) ( int deviceID, int RowSize, int ColSize, int Bin, int BufferCnt );
typedef int (WINAPI * BUFCCDUSB_SetExposureTimePtr) ( int DeviceID, int exposureTime );
typedef int (WINAPI * BUFCCDUSB_SetFrameTimePtr) ( int DeviceID, int frameTime );
typedef int (WINAPI * BUFCCDUSB_SetXYStartPtr) ( int deviceID, int XStart, int YStart );
typedef int (WINAPI * BUFCCDUSB_SetGainsPtr) ( int deviceID, int RedGain, int GreenGain, int BlueGain );
typedef int (WINAPI * BUFCCDUSB_SetGammaPtr) ( int Gamma, int Contrast, int Bright, int Sharp );
typedef int (WINAPI * BUFCCDUSB_SetBWModePtr) ( int BWMode, int H_Mirror, int V_Flip );
typedef int (WINAPI * BUFCCDUSB_InstallFrameHookerPtr) ( int FrameType, FrameDataCallBack FrameHooker );
typedef void (WINAPI * BUFCCDUSB_InstallUSBDeviceHookerPtr) ( DeviceFaultCallBack USBDeviceHooker );
typedef int (WINAPI * BUFCCDUSB_SetSoftTriggerPtr)( int deviceID );
