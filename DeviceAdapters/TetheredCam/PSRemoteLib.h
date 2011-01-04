/*
+-----------------------------------------------------------------+
| PSRemoteLib.h                                                   |
+-----------------------------------------------------------------+
|                                                                 |
| Description:                                                    |
|   API for PSRemoteLib interface library for interfacing to      |
|   PSRemote.                                                     |
|                                                                 |
| Copyright (c) 2003, Breeze Systems Limited                      |
| www.breezesys.com                                               |
+-----------------------------------------------------------------+
*/
#ifdef PSRemoteLIB_EXPORTS
#define PSRemoteLIB_API __declspec(dllexport)
#else
#define PSRemoteLIB_API __declspec(dllimport)
#endif

extern "C"
{

//-----------------------------------------------------------------------
// PingCamera()
// Inputs: none
//
// Returns:
//   0 - Success, camera is connected and ready
//   1 - PSRemote is not running
//   2 - PSRemote is running but camera is not connected
//   3 - Camera is busy
//
// Description:
//   Test to see if PSRemoteIPC is running and whether the camera
//   is ready to take a photo.
//
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall PingCamera();


//-----------------------------------------------------------------------
// ReleaseShutter()
// Inputs:
//   timeOutInSecs   timeout in secs to wait for picture to be
//                   taken and downloaded (max 60 secs)
//   pszFilename     option string in which to store the name of the
//                   saved image. Set to NULL if not required
//   numChars        length of pszFilename if defined
//
// Returns:
//   0 - Success, image saved
//   1 - PSRemote is not running
//   2 - PSRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Timeout waiting for image to be saved
//   5 - Error releasing shutter
//
// Description:
//   Take a picture and optionally wait for it to be saved to disk.
//
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall ReleaseShutter(
							int   timeoutInSecs,
							char* pszFilename,
							int   numChars
							);


//-----------------------------------------------------------------------
// SetZoomPosition()
// Inputs:
//   zoomPos	Position to set zoom lens
//
// Returns:
//   0 - PSRemote is not running
//   1 - PSRemote is running but camera is not connected
//   2 - Camera is busy
//   6 - Success
//   7 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetZoomPosition(int zoomPos);


//-----------------------------------------------------------------------
// SetExposureMode()
// Inputs:
//   mode		Exposure mode, numbered from 0 in the same
//              order as the exposure mode dropdown list.
//
// Returns:
//   0 - Success
//   1 - PSRemote is not running
//   2 - PSRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetExposureMode(int mode);

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
//   1 - PSRemote is not running
//   2 - PSRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetShutterAperture(int shutter, int aperture);

//-----------------------------------------------------------------------
// SetISO()
// Inputs:
//   iso		ISO setting, numbered from 0 in the same
//              order as the ISO dropdown list.
//
// Returns:
//   0 - Success
//   1 - PSRemote is not running
//   2 - PSRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetISO(int iso);

//-----------------------------------------------------------------------
// SetExposureCompensation()
// Inputs:
//   comp		Exposure compensation, numbered from 0 in the same
//              order as the exposure compensation dropdown list.
//
// Returns:
//   0 - Success
//   1 - PSRemote is not running
//   2 - PSRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetExposureCompensation(int comp);

//-----------------------------------------------------------------------
// SetFilenamePrefix()
// Inputs:
//   pszPrefix       zero-terminated string specifying the filename
//                   prefix.
//
// Returns:
//   0 - Success
//   1 - PSRemote is not running
//   3 - Camera is busy
//
// Description:
//   Defines the prefix used for filenames when saving images to disk
//
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetFilenamePrefix(const char* pszPrefix);

//-----------------------------------------------------------------------
// SetOutputPath()
// Inputs:
//   pszPathname     zero-terminated string specifying the directory in
//                   which to save images.
//
// Returns:
//   0 - Success, pathname returned in pszPathname
//   1 - PSRemote is not running
//   3 - Camera is busy
//   8 - Error creating directory
//
// Description:
//   Defines the directory in which to store images. If the directory does
//   not exist this function will try to create it and return an error if
//   it fails.
//   NOTE: This function only sets the base part of the directory. The actual
//   directory in which images are saved will be be different if PSRemote
//   preferences are set to use a separate directory for the year, month or day.
//
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetOutputPath(
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
//   1 - PSRemote is not running
//   4 - Some other error
//
// Description:
//   Returns the full pathname of the directory used for saving images.
//   This is the base directory as specified by SetOutputPath() plus
//   any separate directories for year, month or day if selected in
//   preferences.
//
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall GetOutputPath(
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
//   1 - PSRemote is not running
//   3 - Camera is busy
//   4 - Some other error
//   9 - String too long (max length 255 characters)
//
// Description:
//   Defines the comment string to be stored in the EXIF data of pictures
//   when they are taken. This function updates the "Comment" edit box in
//   the PSRemote window and does not affect the currently displayed image.
//
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetComment(
							const char* pszComment
							);

//-----------------------------------------------------------------------
// GetCameraModel()
// Inputs:
//   pszModel        string in which to store the camera model name
//   numChars        length of pszModel
//
// Returns:
//   0 - Success, camera model returned in pszModel
//   1 - PSRemote is not running
//   4 - Some other error
//
// Description:
//   Returns the name of the camera model PSRemote is connected to.
//
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall GetCameraModel(
							char* pszModel,
							int   numChars
							);

//-----------------------------------------------------------------------
// SetSizeQuality()
// Inputs:
//   size		Image size/quality, numbered from 0 in the same
//              order as the Size/Quality dropdown list.
//
// Returns:
//   0 - Success
//   1 - PSRemote is not running
//   2 - PSRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetSizeQuality(int size);

//-----------------------------------------------------------------------
// SetWhiteBalance()
// Inputs:
//   wb  		White balance, numbered from 0 in the same
//              order as the "White balance" dropdown list.
//
// Returns:
//   0 - Success
//   1 - PSRemote is not running
//   2 - PSRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetWhiteBalance(int wb);

//-----------------------------------------------------------------------
// SetFlash()
// Inputs:
//   flash 		Flash, numbered from 0 in the same
//              order as the "Flash" dropdown list.
//
// Returns:
//   0 - Success
//   1 - PSRemote is not running
//   2 - PSRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetFlash(int flash);

//-----------------------------------------------------------------------
// SetAFDistance()
// Inputs:
//   distance	AF distance, numbered from 0 in the same
//              order as the "AF Distance" dropdown list.
// Returns:
//   0 - Success
//   1 - PSRemote is not running
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetAFDistance(int distance);

//-----------------------------------------------------------------------
// RefreshAEAF() - same as "Update AE/AF" button in live viewfinder window
// Inputs:
//   none	
//
// Returns:
//   0 - Success
//   1 - PSRemote is not running
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall RefreshAEAF();

//-----------------------------------------------------------------------
// SetAFLock()
// Inputs:
//   lock	false=unlock, true=lock AF	
//
// Returns:
//   0 - Success
//   1 - PSRemote is not running
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetAFLock(bool lock);

//-----------------------------------------------------------------------
// SetLiveViewfinder()
// Inputs:
//   viewfinder 0=off, 1=1x zoom, 2=2x zoom, 3=3x zoom, 4=4x zoom
//
// Returns:
//   0 - Success
//   1 - PSRemote is not running
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SetLiveViewfinder(int viewfinder);

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
//   1 - PSRemote is not running
//   2 - PSRemote is running but camera is not connected
//   3 - Camera is busy
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall ConnectDisconnect(bool connect, int timeoutInSecs);

//-----------------------------------------------------------------------
// SelectCamera() - only applicable for multiple camera versions of PSRemote
// Inputs:
//   camera		Camera number starting from 0
//
// Returns:
//   0 - Success
//   1 - PSRemote is not running
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall SelectCamera(int camera);

//-----------------------------------------------------------------------
// ExitApp()
// Inputs:
//   connect	Disconnect from the camera and exit PSRemote
//
// Returns:
//   0 - Success
//   1 - PSRemote is not running
//   4 - Some other error
//-----------------------------------------------------------------------
PSRemoteLIB_API int __stdcall ExitApp();

};
