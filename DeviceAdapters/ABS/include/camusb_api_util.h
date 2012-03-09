///////////////////////////////////////////////////////////////////////////////
/*!
 *
 * \file            CamUSB_API_Util.h
 * \brief			API utility functions
					DEVELOPMENT SYS  "Microsoft Visual C++ V6.0 SP 6"
					and "Win2000 DDK"
					This function collection provide some usefull tools for
					pixeltypes, skipping and sensortypes.
 * \version			1.00
 * \author			ABS GmbH Jena
 *
 * \date		6.3.2006 \n
 * 			 -> created  \n
 *
 */
///////////////////////////////////////////////////////////////////////////////
#ifndef _CAMUSB_API_UTIL_H_
#define _CAMUSB_API_UTIL_H_

// -------------------------- Includes ----------------------------------------
//
#include "CamUSB_API.h"   //!< include base header

// ----------------------------------------------------------------------------
//! \name  API utility functions
//!@{
//
// ----------------------------------------------------------------------------
//
// IfNoError
//
//! \brief	Function checks if the return code is only a warning and return
//!			the boolean result
//!
//! \param	dwRC passed return code
//!
//! \retval	TRUE 	if no error (warning)
//! \retval	FALSE 	if error code
//!
USBAPI BOOL CCONV IsNoError( u32 dwRC );


//! \cond DOXYGEN_INTERN_API
//
// ----------------------------------------------------------------------------
//
// IsRecoveryNotWise
//
//! \brief	Function checks if for the passed return code a recovery attempt
//!	 \brief	is wise (return TRUE) or a error code should be returned (FALSE).
//!
//! \param	dwRC passed return code
//!
//! \retval	TRUE 	if recovery is recommended
//! \retval	FALSE 	if recovery is not wise
//!
BOOL IsRecoveryWise( u32 dwRC );

//! \endcond


// ----------------------------------------------------------------------------
//
// GetSkipBinValue
//
//! \brief	convert the skip mask value to the real skip value
//! \brief	Skip == none return = 1; skip == 2x return =2;
//!
//! \param	dwSkipBin		bin or skip settings see #S_RESOLUTION_PARAMS::dwSkip
//! \param	dwMask			bin or skip mask see #X_SKIP_MASK or #Y_BIN_MASK
//! \param	nShift			bin or skip shift value see #X_SKIP_SHIFT or #Y_BIN_SHIFT
//!
//! \retval	skip value (1 means no skip, 2 means Skip_2, 3 means Skip_3, ...)
//!
USBAPI u08 CCONV GetSkipBinValue( u32 dwSkipBin, u32 dwMask, u08 nShift );

// ----------------------------------------------------------------------------
//
// GetStdResString
//
//! \brief	return the standard resolution string
//!
//! \param	dwStdRes		standard resolution id
//! \param	szStdResStr		standard resolution string
//! \param	dwMaxLen			max size of standard resolution string
//!
//! \retval	count string elements
//!
USBAPI u32 CCONV GetStdResString( u32 dwStdRes,
                                  char *szStdResStr,
                                  u32 dwMaxLen );

// ----------------------------------------------------------------------------
//
// GetStdRes2String
//
//! \brief	return the standard resolution 2 string
//!
//! \param	dwStdResID2		standard resolution id
//! \param	szStdResStr		standard resolution string
//! \param	dwMaxLen			max size of standard resolution string
//!
//! \retval	count string elements
//!
USBAPI u32 CCONV GetStdRes2String( const u64 dwStdResID2,
                                   char *szStdResStr,
                                   const u32 dwMaxLen );

// ----------------------------------------------------------------------------
//
// Sensortype2String
//
//! \brief	convert the sensor id to as string value
//!
//! \param	wSensorType		    image sensor id
//! \param	pszSensorIDStr		standard resolution string
//! \param	dwMaxLen			max size of standard resolution string
//!
//! \retval	count string elements
//!
USBAPI u32 CCONV Sensortype2String( u16 wSensorType,
                                    char* pszSensorIDStr,
                                    u32 dwMaxLen);
                                    
// ----------------------------------------------------------------------------
//
// TargetID2String
//
//! \brief	convert the sensor id to as string value
//!
//! \param	dwTargetID      [ in] target id to translate
//! \param	pszTargetIDStr  [out] target id string
//! \param	dwMaxLen        [ in] max size of target id string
//!
//! \retval	count string elements
//!
USBAPI u32 CCONV TargetID2String( u32 dwTargetID,
                                  char* pszTargetIDStr,
                                  u32 dwMaxLen,
                                  bool bShort = false );                                    

// ----------------------------------------------------------------------------
//
// GetBpp
//
//! \brief	Return the Bit per Pixel value for the passed pixeltype
//!
//! \remark <PRE>
//!	PIX_MONO8  => GetBpp     => 8 bit per pixel used in memory
//!	           => GetUsedBpp => 8 bit per pixel valid data
//!	PIX_BGR8_PACKED
//!	           => GetBpp     => 24 bit per pixel used in memory
//!	           => GetUsedBpp => 8 bit per R, G and B pixel valid data
//!	PIX_MONO10 => GetBpp     => 16 bit per pixel used in memory
//!	           => GetUsedBpp => 10 bit per pixel valid data
//!	PIX_BGR10_PACKED
//!	           => GetBpp     => 48 bit per pixel used in memory
//!	           => GetUsedBpp => 10 bit per R, G and B pixel valid data
//!	PIX_YUV422_PACKED
//!	           => GetBpp     => 16 bit per pixel used in memory
//!	              (8Bit Y value and a 8Bit U or V value)
//!	           => GetUsedBpp => 8 bit per Y, U and V values valid data
//!        </PRE>
//! \param	dwPixelType		pixeltype
//!
//! \retval	number of bit per pixel
//!
USBAPI u32 CCONV GetBpp( u32 dwPixelType );

// ---------------------------------------------------------------------------
//
// GetUsedBpp
//
//! \brief	returns used number of pixels
//!
//! Example: see #GetBpp
//!
//! \param	dwPixelType	pixel type see above
//!
//! \retval	count used bits
//!
USBAPI u08 CCONV GetUsedBpp( u32 dwPixelType);


// ---------------------------------------------------------------------------
//
// GetPixelTypeString
//
//! \brief	convert the pixeltype to string
//!
//! \param	dwPixelType		pixel type see above
//! \param	pszPixelType	pointer to a char field
//! \param	dwMaxLen		size of char field (max. count char)
//!
//! \retval	lenght of the pixeltype string
//!
USBAPI u32 CCONV GetPixelTypeString( u32 dwPixelType,
                                     char* pszPixelType,
                                     u32 dwMaxLen);

// ---------------------------------------------------------------------------
//
// GetPixelWidthString
//
//! \brief	convert the pixelwidth to string
//!
//! \param	dwPixelType		pixel type see above
//! \param	pszPixelWidth	pointer to a char field
//! \param	dwMaxLen		size of char field (max. count char)
//!
//! \retval	lenght of the pixelwidth string
//!
USBAPI u32 CCONV GetPixelWidthString( u32 dwPixelType,
                                      char* pszPixelWidth,
                                      u32 dwMaxLen);


// ---------------------------------------------------------------------------
//
// IsSingleChannel
//
//! \brief	return true if the selected pixeltype a single channel image
//!
//! \param	dwPixelType		pixel type see above
//!
//! \retval	TRUE	is single image
//!
USBAPI BOOL CCONV IsSingleChannel( u32 dwPixelType );


// ---------------------------------------------------------------------------
//
// IsPreview
//
//! \brief	return true if the selected pixeltype is a preview image type
//!
//! \param	dwPixelType		pixel type see above
//!
//! \retval	TRUE	if preview image
//!
USBAPI BOOL CCONV IsPreview( u32 dwPixelType );


// ---------------------------------------------------------------------------
//
// IsBayer
//
//! \brief	return true if the selected pixeltype is a bayer image type
//!
//! \param	dwPixelType		pixel type see above
//!
//! \retval	TRUE	if bayer image
//!
USBAPI BOOL CCONV IsBayer( u32 dwPixelType );


// ---------------------------------------------------------------------------
//
// IsPlanar
//
//! \brief	return true if the selected pixeltype is a planar image type
//!
//! \param	dwPixelType		pixel type see above
//!
//! \retval	TRUE	if planar image
//!
USBAPI BOOL CCONV IsPlanar( u32 dwPixelType );


// ---------------------------------------------------------------------------
//
// GetErrorStatusString
//
//! \brief	return  a string based on the passed device error status (see
//! \brief  CamUSB_GetCameraStatus
//!
//!	\param		*szDevErrStat	Buffer to receive the error message
//!	\param		dwMaxLen		Length of Buffer
//!	\param		dwDevErrStat	Error status code which string should be returned
//!
//! \retval	device error status string size in bytes
//!
USBAPI u32 CCONV CamUSB_GetErrorStatusString( char *szDevErrStat,
                                              u32 dwMaxLen,
                                              u32 dwDevErrStat );


// ---------------------------------------------------------------------------
//
// GetVisibleSizeBySensorType
//
//! \brief	return  the visible size of a sensor by sensortype
//!
//!	\param		dwSensorType	[in] sensor type
//!	\param		pwSizeX		    [out] pointer to sensor size x
//!	\param		pwSizeY	        [out] pointer to sensor size y
//!
//! \retval	TRUE if sensor type was found
//!
USBAPI BOOL CCONV GetVisibleSizeBySensorType(u32 dwSensorType, u16 *pwSizeX, u16 *pwSizeY);


// ---------------------------------------------------------------------------
//
// GetStandardResSize
//
//! \brief	return the visible size of the passed standard resolution values
//!
//!	\param		dwStdRes	[in] standard resolution value
//!
//! \retval	SIZE of the standard resolution in pixel
//!
USBAPI SIZE CCONV GetStandardResSize ( u32 dwStdResID  );
USBAPI SIZE CCONV GetStandardRes2Size( u64 qwStdResID2 );


//!@}



// ---------------------------------------------------------------------------
//
// CamUSB_UpdateDllCfg
//
//! \brief	set and get CamUSB_Api.dll specific configuration values.
//!
//! \param	dwCMD		get / set / config value selector
//! \param	dwValue		value to set or to read to/at the selected config
//!
//! \retval	TRUE	if successful
//!
USBAPI BOOL CCONV CamUSB_UpdateDllCfg( u32 dwCMD, u32 &dwValue );


// ---------------------------------------------------------------------------
//
// CamUSB_HeartbeatCfg
//
//! \brief	set and get heartbeat specific configuration values.
//!
//! \param	dwCMD		get / set / config value selector
//! \param	dwValue		value to set or to read to/at the selected config
//!	\param	nDevNr		Camera index number, that identifies the 
//!						camera device which should be used with this
//!						function
//!
//! \retval	TRUE	if successful
//!
USBAPI BOOL CCONV CamUSB_HeartBeatCfg( u32 dwCMD, u32 &dwValue, BYTE nDevNr = 0 );




// ---------------------------------------------------------------------------
//
// CamUSB_GetShadingDataInfo
//
//! \brief	fill a #S_SHADING_CORRECTION_DATA_INFO structure based on the 
//!         selected shading data. Select will be done by dwFlag parameter.
//!
//! \param	psShadDataInfo  structure to return the shading data informations
//! \param	dwFlag		    select the shading data from which the return 
//!                         value psShadDataInfo is filled
//! \arg		\c #SHCO_FLAG_NONE          => shading data at pData
//! \arg		\c #SHCO_FLAG_DATA_STRING   => from file, file path at pData
//! \arg		\c #SHCO_FLAG_DARK_REF_SET  => dark ref. from selected camera by nDevNr
//! \arg		\c #SHCO_FLAG_WHITE_REF_SET => white ref. from selected camera by nDevNr
//! \param	pData		    pointer to the source data, path or device number
//! \param	dwDataSize      size of passed data (shading data size, path length
//!                         inclusive zero termination, device number)
//!	\param	nDevNr			Camera index number, that identifies the 
//!							camera device which should be used with this
//!							function. if dwFlag is #SHCO_FLAG_NONE or 
//!                         #SHCO_FLAG_DATA_STRING nDevNr is ignored.
//!                         In case of a error and is invalid #GLOBAL_DEVNR will 
//!                         be used to return error codes.
//!
//! \retval	TRUE	if successful
//!
USBAPI BOOL CCONV CamUSB_GetShadingDataInfo(  
                            S_SHADING_CORRECTION_DATA_INFO *pShadDataInfo,
                            u32 dwFlag, PVOID pData, u32 dwDataSize, 
                            BYTE nDevNr = 0);


#endif // _CAMUSB_API_UTIL_H_





