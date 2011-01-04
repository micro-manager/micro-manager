/*
+-----------------------------------------------------------------+
| NKRemoteLib.h                                                   |
+-----------------------------------------------------------------+
|                                                                 |
| Description:                                                    |
|   API for NKRemoteLib interface library for interfacing to      |
|   NKRemote                                                      |
|   Note: EVF stands for "electronic view finder" aka live view   |                                                                 |
|                                                                 |
| Copyright (c) 2009, Breeze Systems Limited                      |
| www.breezesys.com                                               |
+-----------------------------------------------------------------+
*/
#ifdef NKREMOTELIB_EXPORTS
#define NKREMOTELIB_API __declspec(dllexport)
#else
#define NKREMOTELIB_API __declspec(dllimport)
#endif

extern "C"
{

// Attributes for SetValue/GetValue/EnumSettings
enum NKRemoteSettings { 
	NKRemote_Tv = 0,
	NKRemote_Av,
	NKRemote_ExpMode,
	NKRemote_Iso,
	NKRemote_ExpComp,
	NKRemote_FlashExpComp,
	NKRemote_Quality,
	NKRemote_ImageSize,
	NKRemote_WhiteBalance,
	NKRemote_ColorTemp,
	NKRemote_MeteringMode,
	NKRemote_DriveMode,
	NKRemote_PictureControl,
	NKRemote_AfMode,
	NKRemote_AfAreaMode,
	NKRemote_LockCameraControls,	// SetValue/GetValue only
	NKRemote_Aeb,					// SetValue/GetValue only
	NKRemote_AebShots,				// SetValue/GetValue only
	NKRemote_AebSteps,				// SetValue/GetValue only
	NKRemote_Evf,					// SetValue/GetValue only
	NKRemote_EvfZoom,				// SetValue/GetValue only
	NKRemote_EvfFocusNear,			// SetValue only
	NKRemote_EvfFocusFar,			// SetValue only
	NKRemote_BatteryStatus,			// GetValue only
	NKRemote_FocalLength			// GetValue only
};

// Photobooth actions
enum NKRemotePhotoboothActions { 
	NKRemote_Photobooth_Start = 0,
	NKRemote_Photobooth_Exit,
	NKRemote_Photobooth_Release,
	NKRemote_Photobooth_BW_Release,
	NKRemote_Photobooth_ColorRelease,
	NKRemote_Photobooth_BW_Mode,
	NKRemote_Photobooth_Color_Mode,
	NKRemote_Photobooth_1_Print,
	NKRemote_Photobooth_2_Prints,
	NKRemote_Photobooth_3_Prints,
	NKRemote_Photobooth_4_Prints,
	NKRemote_Photobooth_5_Prints,
	NKRemote_Photobooth_Increase_Copies,
	NKRemote_Photobooth_Decrease_Copies,
	NKRemote_Photobooth_Profile_1,
	NKRemote_Photobooth_Profile_2,
	NKRemote_Photobooth_Profile_3,
	NKRemote_Photobooth_Profile_4,
	NKRemote_Photobooth_Profile_5,
	NKRemote_Photobooth_Profile_6
};

// Info about EVF JPEG images (only valid when EVF active)
struct NKRemoteEvfDisplayInfo
{
	int horizontalSize;			// size of EVF image
	int verticalSize;
	int totalHorizontalSize;	// total size of camera image
	int totalVerticalSize;
	int displayHorizontalSize;	// size of displayed area from camera (same
	int displayVerticalSize;	// as total size when not zoomed)
	int displayCentreX;			// centre of displayed area from camera
	int displayCentreY;			// (0, 0) when not zoomed
	int afWidth;				// size of AF area
	int afHeight;
	int afCentreX;				// centre of AF area
	int afCentreY;
	int afDriveState;			// 0 = not driving, 1 = driving
};

//-----------------------------------------------------------------------
// PingCamera()
// Inputs: none
//
// Returns:
//   0 - Success, camera is connected and ready
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//
// Description:
//   Test to see if NKRemote is running and whether the camera
//   is ready to take a photo.
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall PingCamera();

//-----------------------------------------------------------------------
// GetCameraModel()
// Inputs:
//   pszModel        string in which to store the camera model name
//   numChars        length of pszModel
//
// Returns:
//   0 - Success, camera model returned in pszModel
//   1 - NKRemote is not running
//   4 - Some other error
//
// Description:
//   Returns the name of the camera model NKRemote is connected to.
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall GetCameraModel(
							char* pszModel,
							int   numChars
							);

//-----------------------------------------------------------------------
// GetLensName()
// Inputs:
//   pszLens         string in which to store the camera model name
//   numChars        length of pszLens
//
// Returns:
//   0 - Success, camera model returned in pszLens
//   1 - NKRemote is not running
//   4 - Some other error
//
// Description:
//   Returns the name of the lens of the camera NKRemote is connected to.
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall GetLensName(
							char* pszLens,
							int   numChars
							);

//-----------------------------------------------------------------------
// ConnectDisconnect()
// Inputs:
//   connect	    Attempt to connect to the camera if true,
//				    disconnect if false
//   timeoutInSecs  Connection timeout in secs. Max 600 secs, recommended
//                  minimum setting of 15 secs.
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall ConnectDisconnect(bool connect, int timeoutInSecs);

//-----------------------------------------------------------------------
// ReleaseShutter()
// Inputs:
//   timeOutInSecs   timeout in secs to wait for picture to be
//                   taken and downloaded (max 60 secs)
//   pszFilename     optional string in which to store the name of the
//                   saved image. Set to NULL if not required
//   numChars        length of pszFilename if defined
//
// Returns:
//   0 - Success, image saved
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Timeout waiting for image to be saved
//   5 - AF failure
//   6 - Error releasing shutter
//
// Description:
//   Take a picture and optionally wait for it to be saved to disk.
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall ReleaseShutter(
							int   timeoutInSecs,
							char* pszFilename,
							int   numChars
							);



//-----------------------------------------------------------------------
// SetISO()
// Inputs:
//   iso		ISO setting, numbered from 0 in the same
//              order as the ISO dropdown list.
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall SetISO(int iso);

//-----------------------------------------------------------------------
// SetImageQuality()
// Inputs:
//   image		Image quality setting, numbered from 0 in the same
//              order as the image quality dropdown list.
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall SetImageQuality(int quality);


//-----------------------------------------------------------------------
// SetImageSize()
// Inputs:
//   image		Image size setting, numbered from 0 in the same
//              order as the image size dropdown list.
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall SetImageSize(int size);


//-----------------------------------------------------------------------
// SetExposureMode()
// Inputs:
//   mode		Exposure mode setting, numbered from 0 in the same
//              order as the exposure mode dropdown list.
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall SetExposureMode(int mode);

//-----------------------------------------------------------------------
// SetExposureCompensation()
// Inputs:
//   comp		Exposure compensation, numbered from 0 in the same
//              order as the exposure compensation dropdown list.
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall SetExposureCompensation(int comp);

//-----------------------------------------------------------------------
// SetShutterAperture()
// Inputs:
//   shutter	Shutter speed, numbered from 0 in the same
//              order as the shutter speed dropdown list. A value
//              of -1 leaves the shutter speed unchanged
//   aperture	Aperture, numbered from 0 in the same
//              order as the aperture dropdown list. A value
//              of -1 leaves the aperture unchanged
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall SetShutterAperture(int shutter, int aperture);

//-----------------------------------------------------------------------
// SetWhiteBalance()
// Inputs:
//   wb     	White balance, numbered from 0 in the same
//              order as the white balance dropdown list. Set this to
//				the color temperature to select kelvin white balance
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall SetWhiteBalance(int wb);

//-----------------------------------------------------------------------
// EnumSettings(char* pszValues, int numChars, NKRemoteSettings setting)
// Inputs:
//   pszValues     String in which to store the results which are returned
//                 as a list of 0 terminated strings with 0 at the end
//                 of the list
//   numChars      length of pszValues
//   setting       The setting to enumerate
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall EnumSettings(char* pszValues, int numChars, NKRemoteSettings setting);

//-----------------------------------------------------------------------
// int SetValue(NKRemoteSettings setting, int value)
// Inputs:
//   numChars      length of pszValues
//   setting       The setting
//   value         The value to set (0 based index)
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall SetValue(NKRemoteSettings setting, int value);

//-----------------------------------------------------------------------
// int GetValue(NKRemoteSettings setting, int& value)
// Inputs:
//   setting       The setting
//   value         Returned value (0 based index)
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall GetValue(NKRemoteSettings setting, int& value);

//-----------------------------------------------------------------------
// PhotoboothAction(NKRemotePhotoboothActions action)
// Inputs:
//   action     photobooth action to perform
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall PhotoboothAction(NKRemotePhotoboothActions action);

//-----------------------------------------------------------------------
// DisplayEVF()
// Inputs:
//   display     display or hide live view (EVF) window
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error e.g. EVF not supported
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall DisplayEVF(bool display);

//-----------------------------------------------------------------------
// SetLensFocus()
// Inputs:
//   focus     	Size of focus step: -32767 to 32767. -ve values focus
//              nearer, +ve values further away.
//	Note: EVF must be displayed before calling this function
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error e.g. EVF not supported or not active
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall SetLensFocus(int focus);

//-----------------------------------------------------------------------
// GetEvfFrame(const unsigned char*& buffer, size_t& size)
// Inputs:
//   buffer       buffer containing EVF JPEG returned by NKRemote
//   size		  size of the buffer returned by NKRemote
//   displayInfo  info about the size and position of the EVF image
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error e.g. EVF not supported or not active
//
// Description:
//   Returns the a JPEG frame from the camera's live view.
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall GetEvfFrame(
							const unsigned char*& buffer,
							size_t& size,
							NKRemoteEvfDisplayInfo& displayInfo
							);

//-----------------------------------------------------------------------
// GetEvfAfArea(int& x, int& y)
// Inputs:
//   x, y     coordinates of EVF AF contrast detect area
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error e.g. EVF not supported or not active
//
// Description:
//   Gets the position of the AF contrast detect area in live view
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall GetEvfAfArea(
							int& x,
							int& y
							);

//-----------------------------------------------------------------------
// SetEvfAfArea(int x, int y)
// Inputs:
//   x, y     coordinates of EVF AF contrast detect area
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error e.g. EVF not supported or not active
//
// Description:
//   Sets the position of the AF contrast detect area in live view
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall SetEvfAfArea(
							int x,
							int y
							);

//-----------------------------------------------------------------------
// GetPreviewJpeg(const unsigned char*& buffer, size_t& size, int timeoutInSecs)
// Inputs:
//   buffer         buffer containing EVF JPEG returned by NKRemote
//   size		    size of the buffer returned by NKRemote
//   timeoutInSecs  timeout in secs
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   2 - NKRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - AF failure
//   5 - Some other error e.g. EVF not supported or not active
//
// Description:
//   Returns the a JPEG frame from the camera's live view.
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall GetPreviewJpeg(
							const unsigned char*& buffer,
							size_t& size,
							int timeoutInSecs
							);

//-----------------------------------------------------------------------
// SetFilenamePrefix()
// Inputs:
//   pszPrefix       zero-terminated string specifying the filename
//                   prefix.
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   3 - Camera is busy
//
// Description:
//   Defines the prefix used for filenames when saving images to disk
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall SetFilenamePrefix(
							const char* pszPrefix
							);

//-----------------------------------------------------------------------
// SetOutputPath()
// Inputs:
//   pszPathname     zero-terminated string specifying the directory in
//                   which to save images.
//
// Returns:
//   0 - Success, pathname returned in pszPathname
//   1 - NKRemote is not running
//
// Description:
//   Defines the directory in which to store images.
//
//   NOTE: This function only sets the base part of the directory. The actual
//   directory in which images are saved will be be different if NKRemote
//   preferences are set to use a separate directory for the year, month or day.
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall SetOutputPath(
							const char* pszPathname
							);


//-----------------------------------------------------------------------
// GetOutputPath()
// Inputs:
//   pszPathname     string in which to store the pathname of the
//                   directory currently being used to save images
//   numChars        length of pszPathname
//
// Returns:
//   0 - Success, pathname returned in pszPathname
//   1 - NKRemote is not running
//   4 - Some other error
//
// Description:
//   Returns the full pathname of the directory used for saving images.
//   This is the base directory as specified by SetOutputPath() plus
//   any separate directories for year, month or day if selected in
//   preferences.
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall GetOutputPath(
							char* pszPathname,
							int   numChars
							);


//-----------------------------------------------------------------------
// SetComment()
// Inputs:
//   pszComment      zero-terminated string specifying the comment to be
//                   stored in the EXIF data of pictures.
//                   Max length 255 characters.
//
// Returns:
//   0 - Success, pathname returned in pszPathname
//   1 - NKRemote is not running
//   3 - Camera is busy
//   4 - Some other error
//   9 - String too long (max length 255 characters)
//
// Description:
//   Defines the comment string to be stored in the EXIF data of pictures
//   when they are taken. This function updates the "Comment" edit box in
//   the NKRemote window and does not affect the currently displayed image.
//
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall SetComment(
							const char* pszComment
							);

//-----------------------------------------------------------------------
// HWND GetNKRemoteHWND()
// Inputs: none
//
// Returns:
//   non-zero - the handle of the NKRemote window
//   0 - NKRemote is not running or the window handle can't be found
//
//-----------------------------------------------------------------------
NKREMOTELIB_API HWND __stdcall GetNKRemoteHWND();

//-----------------------------------------------------------------------
// ExitApp()
// Inputs:
//   connect	Disconnect from the camera and exit NKRemote
//
// Returns:
//   0 - Success
//   1 - NKRemote is not running
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
NKREMOTELIB_API int __stdcall ExitApp();

};
