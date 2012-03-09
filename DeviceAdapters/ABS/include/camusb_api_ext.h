///////////////////////////////////////////////////////////////////////////////
/*!
 *
 * \file            CamUSB_API_Ext.h
 * \brief			API exported extended functions
					DEVELOPMENT SYS  "Microsoft Visual C++ V6.0 SP 6"
					and "Win2000 DDK"\n\n
					This function collection is used to simplify the 
					camera functionality for rapid application development. \n
					Internal they based on the "CamUSB_API.h" - functions.
 * \version			1.00
 * \author			ABS GmbH Jena (HBau)
 *
 * \date 15.11.05 -> reorganised
 *
 */
///////////////////////////////////////////////////////////////////////////////
#ifndef _CAMUSB_API_EXT_H_
#define _CAMUSB_API_EXT_H_

// -------------------------- Includes ----------------------------------------
//
#include "CamUSB_API.h"   //!< include base header


/////////////////////////////////////////////////////////////////////////////
//! \name Functions: Image Sensor
/////////////////////////////////////////////////////////////////////////////
//!@{
// --------------------------------------------------------------------------
// CamUSB_SetCameraResolution
//! \brief		Selects the region of interest (ROI) of the active image sensor.
//!
//! 			Valid from next image on.
//!
//! \param		wOffsetX	X offset of ROI (relative to visible area)
//!	\param		wOffsetY	Y offset of ROI (relative to visible area)	
//!	\param		wSizeX		X size (width, columns) of ROI
//!	\param		wSizeY		Y size (height, lines) of ROI
//!	\param		dwSkip		X- and Y- Skip settings  see #XY_SKIP_NONE
//!	\param		dwBin;		X- and Y- Bin settings see #XY_BIN_NONE
//! \param		bKeepExposure	if TRUE the exposure time is nearly constant
//!								during resolution changes
//!	\param		nDevNr			Camera index number, that identifies the 
//!								camera device which should be used with this
//!								function
//!								
//!
//! \retval		TRUE			success
//!	\retval		FALSE			error
//!
//! \see #FUNC_RESOLUTION for ROI details
//!
USBAPI BOOL CCONV CamUSB_SetCameraResolution ( SHORT wOffsetX, SHORT wOffsetY, 
                                               WORD  wSizeX,   WORD  wSizeY,									 
                                               DWORD dwSkip=0, DWORD dwBin=0, 
                                               BOOL bKeepExposure = TRUE,
                                               BYTE nDevNr=0);

// --------------------------------------------------------------------------
// CamUSB_GetCameraResolution
/*! \brief		Returns the region of interest (ROI) .
 * 
 *  			Valid from next image on.
 * 
 *  \param		pwOffsetX	X offset of ROI (relative to visible area)
 * 	\param		pwOffsetY	Y offset of ROI (relative to visible area)	
 * 	\param		pwSizeX		X size (width, columns) of ROI
 * 	\param		pwSizeY		Y size (height, lines) of ROI
 * 	\param		pdwSkip		X- and Y- Skip settings  see #XY_SKIP_NONE
 * 	\param		pdwBin;		X- and Y- Bin settings see #XY_BIN_NONE
 * 	\param		nDevNr			Camera index number, that identifies the 
 * 								camera device which should be used with this
 * 								function
 * 
 *  \retval		TRUE			success
 * 	\retval		FALSE			error
 * 
 *  \see #FUNC_RESOLUTION for ROI details
 *
 *  \par Example:
 *  \code
 
 SHORT wOffsetX, wOffsetY;
 WORD  wSizeX, wSizeY;
 DWORD dwSkip, dwBin;

 if ( CamUSB_GetCameraResolution( &wOffsetX, &wOffsetY, &wSizeX, 
                                  &wSizeY, &dwSkip, &dwBin) != TRUE )	
 {
   // error see CamUSB_GetLastError
 }
 else
 {
    // Camera Resolution sucessfull read
 }

 \endcode
*/
USBAPI BOOL CCONV CamUSB_GetCameraResolution ( SHORT*  pwOffsetX, SHORT*  pwOffsetY, 
                                               LPWORD  pwSizeX,   LPWORD  pwSizeY,									 
                                               LPDWORD pdwSkip,   LPDWORD pdwBin, 											  
                                               BYTE    nDevNr=0 );

// --------------------------------------------------------------------------
// CamUSB_GetCameraResolutionInfo
/*! \brief		Apply the camera resolution constrains at the passed camera
 *              resolution parameter. The adapted parameters and the resulting 
 *              image dimensions will be returned.
 *              The structure #S_RESOLUTION_INFO is used for data exchange.
 * 
 * \retval		TRUE			success
 * \retval		FALSE			error
 * \par Example:
 * \code

S_RESOLUTION_INFO sResInfo = {0};

// setup input sResInfo.sResIn e.g. 3mega pixel sensor
sResInfo.sResIn.wSizeX = 2080;
sResInfo.sResIn.wSizeY = 1542;
sResInfo.sResIn.dwSkip = XY_SKIP_2X;

// execute function
if ( CamUSB_GetCameraResolutionInfo( &sResInfo ) != TRUE )	
{
// error see CamUSB_GetLastError
}
else
{
  // camera resolution info successfully executed
  // sResInfo.sResOut, sResInfo.wImgWidth and sResInfo.wImgHeight
  // are updated
}

\endcode
*/
USBAPI BOOL CCONV CamUSB_GetCameraResolutionInfo (  S_RESOLUTION_INFO* pResInfo,  
                                                    BYTE    nDevNr=0 );


// --------------------------------------------------------------------------
// CamUSB_SetExposureTime
/*! \brief		Sets the integration time of the camera sensor of the active camera channel.
 * 
 *  			Valid from the next image on.
 * 
 *  \param		pdwExposure_us	Exposure / Integration time value in µs (us).
 * 	\param		nDevNr			Camera index number, that identifies the 
 * 								camera device which should be used with this
 * 								function
 * 								
 *  \retval		TRUE			success
 * 	\retval		FALSE			error
 * 
 *  \par Example:
 *  \code
 
 // Value of the Exposure to set
 // be aware 0 is not allowed
 // the function SetIntegrationTime
 // will correct this value to the
 // lowest allowed value

 DWORD nExposure;		
	
 // reading the exposure time							
	if (CamUSB_GetExposureTime( &nExposure ) != TRUE)
 { 
     // error see CamUSB_GetLastError
 }
 else
 {
    // nExposure contains now actual exposure time
 }

 nExposure = 20000;	// 20000µs = 20ms
 if (CamUSB_SetExposureTime( &nExposure ) != TRUE)
 {	
   // error see CamUSB_GetLastError
 }
 else
 {
	  // nExposure => has be changed and contains the real 
	  //              Exposure Time which could be set at the camera

 }
	
 \endcode
*/
USBAPI BOOL CCONV CamUSB_SetExposureTime( LPDWORD pdwExposure_us, 
                                          BYTE nDevNr=0);

// --------------------------------------------------------------------------
// CamUSB_GetExposureTime
//! \brief		returns the active Exposure Time.
//!
//! \param		pdwExposure_us	Exposure / Integration time value in µs (us).
//!	\param		nDevNr			Camera index number, that identifies the 
//!								camera device which should be used with this
//!								function
//!								
//! \retval		TRUE			success
//!	\retval		FALSE			error
//!
//! \par Example: see CamUSB_SetExposureTime
//!
USBAPI BOOL CCONV CamUSB_GetExposureTime( LPDWORD pdwExposure_us, 
                                          BYTE nDevNr=0);


// --------------------------------------------------------------------------
// CamUSB_SetGain
/*! \brief		Sets the color gain level for the Bayer Pattern pixel 
 * 				group "nGainChannel".
 * 		
 * 				As an alternative for setting the individual color gains,
 * 				the global gain level can be set with this function.
 * 				IMPORTANT: calling this function invalidates the settings of
 * 				individual color gains.
 * 
 * 				Valid from the next image on.
 * 
 * 				Attention, not all gain values are supported by all sensor
 *              types. Call #CamUSB_GetFunctionCaps to determine the gain capability 
 * 				of camera sensor 
 * 
 * 				Generally a gain value is interpreted as fixed point value.
 * 				e.g. gain value \arg 1000 means   1.0   \n
 * 								\arg 1755 means   1.755 \n
 * 								\arg 6515 means   6.515 \n
 * 							  \arg 128000 means 128.0   \n
 * 
 * 				This function rounds the gain value to the next possible value
 * 				which is returned with "pGain". 
 * 
 * 
 *  \param		pdwGain			gain value
 *  \param		wGainChannel	color channel 
 *  \arg		\c GAIN_RED		red
 *  \arg		\c GAIN_GREEN1  green 1
 *  \arg		\c GAIN_GREEN2  green 2
 *  \arg		\c GAIN_BLUE    blue
 *  \arg		\c GAIN_GLOBAL  all
 * 
 * 	\param		nDevNr			Camera index number, that identifies the 
 * 								camera device which should be used with this
 * 								function
 * 
 *  \retval		TRUE			success
 * 	\retval		FALSE			error
 * 
 *  \par Example: Set- and GetGain
 *  \code
 
 // Value of the Gain to set.
 // Be aware 0 is not allowed.
 // The function SetGlobalGain
 // lowest allowed value.

 DWORD nGain;

 // global gain set
 if (CamUSB_GetGain( &nGain, GAIN_GLOBAL ) != TRUE)
   {
     // error see CamUSB_GetLastError
   }
   else
   {
     // nGain contains now actual gloabl gain value
   }
	
 nGain = 2000;  // (2.0)
 if (CamUSB_SetGain( &nGain, GAIN_GLOBAL ) != TRUE)
 {
   // error see CamUSB_GetLastError
 }
 else
 {
     // nGain contains now rounded and applied 
     // global gain value
 }
	
 \endcode
*/
USBAPI BOOL CCONV CamUSB_SetGain ( LPDWORD pdwGain, 
                                   WORD wGainChannel, 
                                   BYTE nDevNr=0);


// --------------------------------------------------------------------------
// CamUSB_GetGain
//! \brief		returns the color gain level for the Bayer Pattern pixel 
//!	\brief		group "nGainChannel".
//!				
//!				
//! \param		pdwGain			gain value
//! \param		wGainChannel	color channel 
//!	\param		nDevNr			Camera index number, that identifies the 
//!								camera device which should be used with this
//!								function
//!
//! \retval		TRUE			success
//!	\retval		FALSE			error
//!
//! \par Example: see GetGlobalGain
//!
USBAPI BOOL CCONV CamUSB_GetGain( LPDWORD pdwGain, 
                                  WORD wGainChannel, 
                                  BYTE nDevNr=0);


// --------------------------------------------------------------------------
// CamUSB_SetStandardRes
//! \brief		Set one of the standard camera resolutions
//!
//!				Valid from the next image on.
//!
//! \param		dwStdRes			Specifies working mode:
//! \arg		\c STDRES_QSXGA		= 0x001	- 2592 x 1944
//! \arg		\c STDRES_QXGA		= 0x002	- 2048 x 1536
//!	\arg		\c STDRES_UXGA		= 0x004	- 1600 x 1200
//!	\arg		\c STDRES_SXGA		= 0x008	- 1280 x 1024
//!	\arg		\c STDRES_XGA		= 0x010	- 1024 x  768
//!	\arg		\c STDRES_SVGA		= 0x020	-  800 x  600
//!	\arg		\c STDRES_VGA		= 0x040 -  640 x  480
//!	\arg		\c STDRES_CIF		= 0x080 -  352 x  288
//!	\arg		\c STDRES_QVGA		= 0x100 -  320 x  240
//!	\arg		\c STDRES_HDTV_1080 = 0x200 - 1920 x 1080
//!	\arg		\c STDRES_HDTV_720  = 0x400 - 1280 x  720
//!
//!	\param		nDevNr			Camera index number, that identifies the 
//!								camera device which should be used with this
//!								function
//!
//! \retval		TRUE			success
//!	\retval		FALSE			error
//!
USBAPI BOOL CCONV CamUSB_SetStandardRes( u32 dwStdResID, 
                                         u08 nDevNr = 0);


// --------------------------------------------------------------------------
// CamUSB_GetStandardRes
//! \brief		Get one of the standard camera resolutions or 
//!	\brief		that an ROI is active
//!
//! \param		pdwStdRes   Specifies working mode see #CamUSB_SetStandardRes
//!                       and #NO_STDRES
//!
//!	\param		nDevNr			Camera index number, that identifies the 
//!								        camera device which should be used with this
//!								        function
//!
//! \retval		TRUE			  success
//!	\retval		FALSE			  error
//!
USBAPI BOOL CCONV CamUSB_GetStandardRes( u32* pdwStdResID, 
                                         u08  nDevNr = 0);


// --------------------------------------------------------------------------
// CamUSB_GetStandardResCaps
//! \brief		Returns the mask of the supported standard resolutions
//!
//!
//! \param		pdwStdResMask see standard resolution defines #STDRES_VGA
//!
//!	\param		nDevNr			Camera index number, that identifies the 
//!								camera device which should be used with this
//!								function
//!
//! \retval		TRUE			success
//!	\retval		FALSE			error
//!	
USBAPI BOOL CCONV CamUSB_GetStandardResCaps( u32* pdwStdResMask, 
											                       u08  nDevNr = 0 );

// --------------------------------------------------------------------------
// CamUSB_SetStandardRes2
//! \brief		Set one of the standard camera resolutions
//!
//!				Effective as of the next image.
//!
//! \param		pdwStdResID2		Specifies working mode:
//! \arg		\c STDRES2_QUXGA    = 3200 x 2400
//! \arg		\c STDRES2_QSXGA    = 2560 x 2048
//!	\arg		\c STDRES2_WQXGA    = 2560 x 1600
//!	\arg		\c STDRES2_QXGA     = 2048 x 1536
//!	\arg		\c STDRES2_WUXGA    = 1920 x 1200
//!	\arg		\c STDRES2_UXGA     = 1600 x 1200
//!	\arg		\c STDRES2_WSXGAP   = 1680 x 1050
//!	\arg		\c STDRES2_SXGA     = 1280 x 1024
//!	\arg		\c STDRES2_XGA2     = 1360 x 1024
//!	\arg		\c STDRES2_WXGA     = 1360 x 768
//!	\arg		\c STDRES2_XGA      = 1024 x 768
//!	\arg		\c STDRES2_SVGA     =  800 x 600
//!	\arg		\c STDRES2_WIDEVGA  =  752 x 480
//!	\arg		\c STDRES2_VGA      =  640 x 480
//!	\arg		\c STDRES2_WQVGA    =  384 x 240
//!	\arg		\c STDRES2_QVGA     =  320 x 240
//!
//!	\param  nDevNr  Camera index number, that identifies the 
//!								  camera device which should be used with this
//!								  function
//!
//! \retval		TRUE			success
//!	\retval		FALSE			error
//!
USBAPI BOOL CCONV CamUSB_SetStandardRes2( u64 qwStdResID2, 
                                          u08 nDevNr = 0);

// --------------------------------------------------------------------------
// CamUSB_GetStandardRes
//! \brief		Get one of the standard camera resolutions or 
//!	\brief		that an ROI is active
//!
//! \param		pdwStdResID2  Specifies working mode see #CamUSB_SetStandardRes2
//!                         and #STDRES2_NONE
//!
//!	\param		nDevNr			Camera index number, that identifies the 
//!								        camera device which should be used with this
//!								        function
//!
//! \retval		TRUE			  success
//!	\retval		FALSE			  error
//!
USBAPI BOOL CCONV CamUSB_GetStandardRes2( u64* pdwStdResID2, 
                                          u08  nDevNr = 0);

// --------------------------------------------------------------------------
// CamUSB_GetStandardRes2Caps
//! \brief		Returns the mask of the supported standard resolutions
//!
//!
//! \param		pdwStdResID2Mask see standard resolution defines #STDRES2_VGA
//!
//!	\param		nDevNr		Camera index number, that identifies the 
//!								      camera device which should be used with this
//!								      function
//!
//! \retval		TRUE			success
//!	\retval		FALSE			error
//!	
USBAPI BOOL CCONV CamUSB_GetStandardRes2Caps( u64* pdwStdResID2Mask, 
                                              u08  nDevNr = 0 );


// --------------------------------------------------------------------------
// CamUSB_SetSleepMode
//! \brief		Set an Camera sleep mode e.g. #SLEEPMODE_SENSOR to shut 
//!             down (power down) the sensor module
//!
//! \param		dwSleepMode		Sleep mode
//!
//!	\param		nDevNr			Camera index number, that identifies the 
//!								camera device which should be used with this
//!								function
//!
//! \retval		TRUE			success
//!	\retval		FALSE			error
//! 
//! \remark     If you set an sleep mode to active and turn off your program,
//! \remark     the sleep mode will be stay active till you reboot the camera 
//! \remark     or set the sleep mode by #CamUSB_SetSleepMode to inactive.
//! \remark     During an active sleep mode, most of the camera functions
//! \remark     are inaccessible. Calls to such functions will normally 
//! \remark     be fail with the error code #retOP_SLEEPMODE.
//! \remark     If you call #CamUSB_InitCamera for an camera device which is
//! \remark     currently at a sleep mode, the function return #retSLEEPMODE_ACTIVE.
//! \remark     In this case the default Pixeltype, Bitshift, Flip and IO-Port settings
//! \remark     hasn't been set. Also the internal Framerate state is undefined
//! \remark     till the first valid call to Get/Set #FUNC_FRAMERATE.
//! \remark     See also #CamUSB_InitCamera!
//!
//! \par Example:
//!     - \ref example_13
//!
USBAPI BOOL CCONV CamUSB_SetSleepMode( DWORD dwSleepMode, 
                                       BYTE nDevNr = 0);


// --------------------------------------------------------------------------
// CamUSB_GetSleepMode
//! \brief		Get the current camera sleep mode e.g. #SLEEPMODE_NONE
//!
//! \param		pdwSleepMode	return the sleep mode
//!
//!	\param		nDevNr			Camera index number, that identifies the 
//!								camera device which should be used with this
//!								function
//!
//! \retval		TRUE			success
//!	\retval		FALSE			error
//!
//! \remark See remarks on #CamUSB_SetSleepMode!
//!
//! \par Example:
//!     - \ref example_13
//!
USBAPI BOOL CCONV CamUSB_GetSleepMode( LPDWORD pdwSleepMode, 
                                       BYTE nDevNr = 0);


// --------------------------------------------------------------------------
// CamUSB_GetSleepModeCaps
//! \brief		Returns the mask of the supported sleep modes. If sleep mode
//! \brief		mask "zero" sleep modes are not supported and calls to 
//! \brief		#CamUSB_GetSleepMode and #CamUSB_SetSleepMode will be fail.
//!
//! \param		pdwStdResMask   return the mask of supported sleep modes
//!
//!	\param		nDevNr			Camera index number, that identifies the 
//!								camera device which should be used with this
//!								function
//!
//! \retval		TRUE			success
//!	\retval		FALSE			error
//!
//! \par Example:
//!     - \ref example_13
//!
USBAPI BOOL CCONV CamUSB_GetSleepModeCaps( LPDWORD pdwSleepModeMask, 
										   BYTE nDevNr=0);

// --------------------------------------------------------------------------
// CamUSB_SetPixelType
//! \brief		Set the camera pixel type 
//!				(attention call #CamUSB_GetFunctionCaps to get valid pixel types)
//!
//!				Valid from the next image on.
//!
//! \param		dwPixelType		Specifies working mode (see pixeltypes.h)
//!
//!	\param		nDevNr			Camera index number, that identifies the 
//!								camera device which should be used with this
//!								function
//!
//! \retval		TRUE			success
//!	\retval		FALSE			error
//!
//!
USBAPI BOOL CCONV CamUSB_SetPixelType( DWORD dwPixelType, 
                                       BYTE nDevNr=0);


// --------------------------------------------------------------------------
// CamUSB_GetPixelType
//! \brief		Get the camera pixel type 
//!
//! \param		pdwPixelType	pointer to the returned pixeltype 
//!								(see pixeltypes.h)
//!
//!	\param		nDevNr			Camera index number, that identifies the 
//!								camera device which should be used with this
//!								function
//!
//! \retval		TRUE			success
//!	\retval		FALSE			error
//!
//!
USBAPI BOOL CCONV CamUSB_GetPixelType( LPDWORD pdwPixelType, 
                                       BYTE nDevNr=0);


// --------------------------------------------------------------------------
// CamUSB_SetFramerateLimit
//! \brief		Set the maxium framerate, at which the dll try to read an 
//!	\brief		image from the camera. The resulting framerate depends on
//!	\brief		the integration (exposure) time and the maximum sensor 
//!	\brief		readout rate.
//!
//! \param		pwFps		maximum allowed framerate, return max. posible value
//!	\param		nDevNr		Camera index number, that identifies the 
//!							camera device which should be used with this
//!							function
//!
//! \retval		TRUE		success
//!	\retval		FALSE		error
//!
USBAPI BOOL CCONV CamUSB_SetFramerateLimit( LPWORD pwFps, 
                                            BYTE nDevNr=0);

// --------------------------------------------------------------------------
// CamUSB_GetFramerateLimit
//! \brief		Get the maxium framerate, at which the dll can read an 
//!	\brief		image from the camera. The resulting framerate depends on
//!	\brief		the integration (exposure) time and the maximum sensor 
//!	\brief		readout rate.
//!
//! \param		pwFps		maximum allowed framerate
//!	\param		nDevNr		Camera index number, that identifies the 
//!							camera device which should be used with this
//!							function
//!
//! \retval		TRUE		success
//!	\retval		FALSE		error
//!
USBAPI BOOL CCONV CamUSB_GetFramerateLimit( LPWORD pwFps, 
                                            BYTE nDevNr=0);

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Functions: Look-Up Table (LUT) Control
/////////////////////////////////////////////////////////////////////////////
//!@{
// --------------------------------------------------------------------------
// CamUSB_SelectLUT
//! \brief		Selects the lookup table (LUT) that should be used for the next image.
//!
//!				The selected LUT is copied to an internal working LUT buffer
//!				so the function has to be called after every change to the
//!				desired LUT.
//!
//! \param		wLUTIndex		index of the lut to be used (see #CamUSB_GetFunctionCaps)
//!	\param		nDevNr			Camera index number, that identifies the 
//!								camera device which should be used with this
//!								function
//!
//! \retval		TRUE		success
//!	\retval		FALSE		error
//!
USBAPI BOOL CCONV CamUSB_SelectLUT( WORD wLUTIndex, 
                                    BYTE nDevNr=0);


// --------------------------------------------------------------------------
// CamUSB_WriteLUTData
//! \brief		Writes data to a given LUT.
//!
//!				The written LUT data is used after the next call of the
//!				SelectLUT() function with the appropriate LUT number,
//!				even if the last SelectLUT() call used the same LUT number.
//!				This is because SelectLUT() copies the selected LUT to an
//!				internal working LUT buffer.
//!
//! \param		wLUTIndex		index of the lut (0-3) which data should be updated
//!	\param		pwLUTData		data to be written
//!	\param		wDataSize       data size to be written
//!	\param		nDevNr			Camera index number, that identifies the 
//!								camera device which should be used with this
//!								function
//!
//! \retval		TRUE		success
//!	\retval		FALSE		error
//!
USBAPI BOOL CCONV CamUSB_WriteLUT(WORD wLUTIndex, 
                                  LPWORD pwLUTData, 
                                  WORD wDataSize, 
                                  BYTE nDevNr=0);
//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Functions: Configuration
/////////////////////////////////////////////////////////////////////////////
//!@{

// --------------------------------------------------------------------------
// CamUSB_SaveCameraSettingsToFile
//! \brief		Writes the current camera setting in a INI file which can
//! \brief		be applied by CamUSB_LoadCameraSettingsFromFile
//!
//! \param		szFileName	    file name of INI-file to write
//! \param		szSettingsName	name of settings data which should be used
//!	\param		nDevNr		Camera index number, that identifies the 
//!							camera device which should be used with this
//!							function
//!
//! \retval		TRUE		success
//!	\retval		FALSE		error
//!
USBAPI BOOL CCONV CamUSB_SaveCameraSettingsToFile(  char* szFileName, 
                                                    char* szSettingsName,
                                                    BYTE  nDevNr=0); 

// --------------------------------------------------------------------------
// CamUSB_SaveCameraSettingsToFileEx
//! \brief		Writes the current camera setting in a INI file which can
//! \brief		be applied by CamUSB_LoadCameraSettingsFromFile
//!
//! \param      dwFlags         optional flags to control the behavoir
//!                             default => 0
//! \param		szFileName	    file name of INI-file to write
//! \param		szSettingsName	name of settings data which should be used
//!	\param		nDevNr		Camera index number, that identifies the 
//!							camera device which should be used with this
//!							function
//!
//! \retval		TRUE		success
//!	\retval		FALSE		error
//!
USBAPI BOOL CCONV CamUSB_SaveCameraSettingsToFileEx(  
                                                  u32   dwFlags,
                                                  char* szFileName, 
                                                  char* szSettingsName,
                                                  BYTE  nDevNr=0); 


// --------------------------------------------------------------------------
// CamUSB_LoadCameraSettingsFromFile
//! \brief		Load the new camera setting from an INI file which can
//! \brief		be saved with CamUSB_SaveCameraSettingsToFile
//!
//! \param		szFileName	    file name of INI-file to read
//! \param		szSettingsName	name of settings data which should be used
//!	\param		nDevNr		Camera index number, that identifies the 
//!							camera device which should be used with this
//!							function
//!
//! \retval		TRUE		success
//!	\retval		FALSE		error
//!
USBAPI BOOL CCONV CamUSB_LoadCameraSettingsFromFile(  char* szFileName,
                                                      char* szSettingsName,
                                                      BYTE  nDevNr=0);

//!@}

#endif // _CAMUSB_API_EXT_H_