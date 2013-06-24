///////////////////////////////////////////////////////////////////////////////
/*!
 *
 * \file      CamUSB_API.h
 * \brief     API exported functions
              DEVELOPMENT SYS  "Microsoft Visual C++ 2005"
              and "Windows SDK 2003"
 * \version   1.08
 * \author    ABS GmbH Jena
 *
 * \date 30.03.12 -> updates HBau
 * \date 19.07.06 -> ansi c fixes HBau
 * \date 17.02.06 -> updates and bug fixes HBau
 * \date 15.11.05 -> reorganised HBau
 * \date 16.09.05 -> API minor interface modifications HBau
 * \date 14.09.05 -> API revised HBau
 * \date 01.04.05 -> API enhancements RG
 * \date 01.10.04 -> API developing TC
 *
 */
///////////////////////////////////////////////////////////////////////////////
#ifndef _CAMUSB_API_H_
#define _CAMUSB_API_H_

#ifndef _WINDOWS_
    #pragma message( " " )
    #pragma message( "      ===========================================" )
    #pragma message( "      |                                         |" )
    #pragma message( "      |    Standard Win32 defines not found!    |" )
    #pragma message( "      |                                         |" )
    #pragma message( "      | You have to include the windows.h       |" )
    #pragma message( "      | or over stdafx.h                        |" )
    #pragma message( "      |                                         |" )
    #pragma message( "      ===========================================" )
    #pragma message( " " )
    #error You have to include the "windows.h" directly or over "stdafx.h"
#endif


#ifdef CAM_USB_API_EXPORTS  // Import bzw. Export festlegen
  #include "camusb_api_call.h"
#else
  #ifdef __cplusplus                                  //!< imports for C++
    #define USBAPI extern "C" __declspec(dllimport)
  #else                                               //!< imports for C
    #define USBAPI __declspec(dllimport)
  #endif

  #ifndef CCONV
    #define CCONV  __cdecl                              //!< call convention
  #endif  
#endif


// -------------------------- Includes ----------------------------------------

#include "include\datatypes.h"              // base data types
#include "include\common_constants_exp.h"   // constants
#include "include\common_structs_exp.h"     // data structs
#include "include\pixeltypes.h"             // image pixeltypes


/////////////////////////////////////////////////////////////////////////////
// exported API functions
/////////////////////////////////////////////////////////////////////////////

/////////////////////////////////////////////////////////////////////////////
//! \name Functions: Error Handling
//!@{
// --------------------------------------------------------------------------
// CamUSB_GetErrorString
/*!
 * \brief   Returns a description for a given error ID.
 *
 * \param   szErrStr    Buffer to receive the error message
 * \param   dwMaxLen    Length of Buffer
 * \param   dwErrCode   Error code which string should be returned
 *
 * \return  error string size in bytes
 *
 * \par Example:
 * \code

 #include <string>
 
 const u32 maxPath = 260;
 u32 dwErrorCode; 
 u32 dwStrLenght;
 std::string strError;

 // set a error code, normally obtained by CamUSB_GetLastError 
 dwErrorCode = retOK; 
  
 strError.resize( maxPath, 0 );
 dwStrLenght = CamUSB_GetErrorString( (char*) strError.c_str(), maxPath, dwErrorCode ); 
 strError.resize( dwStrLenght ); // limit to real string lenght

 \endcode
 *
 */
USBAPI u32 CCONV CamUSB_GetErrorString( char* szErrStr,
                                        u32 dwMaxLen,
                                        u32 dwErrCode);

// --------------------------------------------------------------------------
// CamUSB_GetLastError
/*!
 * \brief   Returns the last error code from the specified camera device,
 *          if called the last error code will be automatically reset to
 *          retOK;
 *
 * \param   nDevNr  Camera index number, that identifies the
 *                  camera device which should be used with this
 *                  function.
 *
 *                  Use #GLOBAL_DEVNR as parameter to obtain error
 *                  informations if camera initialization failed.
 *
 * \return  last error code
 *
 * \par Example:
 * \code

 u32 dwErrCode; // error code
 dwErrCode = CamUSB_GetLastError( );

 \endcode
*/
USBAPI u32 CCONV CamUSB_GetLastError( u08 nDevNr = 0 );

//!@}



/////////////////////////////////////////////////////////////////////////////
//! \name Functions: Version Information
//!@{
// --------------------------------------------------------------------------
// CamUSB_GetDllVersion
/*!
 * \brief   Returns the DLL version.
 *
 * \param   szVerStr    Buffer to receive the DLL version message
 * \param   wMaxLen     Length of Buffer
 * \param   pwBuild     Pointer to the returned build number
 *
 * \retval  high-word contains the major version and the low-word
 *          contains the minor version
 *
 * \par Example:
 * \code

 #include <string>

 const u32 maxPath = 260;
 u32 dwVersion; 
 u16 wMajorVersion, wMinorVersion, wBuildNo; 
 std::string strVersion;

 // obtain the Version as string
 strVersion.resize( maxPath, 0 );
 CamUSB_GetDllVersion( (char*) strVersion.c_str(), maxPath, 0 ); 
 strVersion.resize( dwStrLenght ); // limit to real string lenght


 // obtain Version as numeric values
 dwVersion = CamUSB_GetDllVersion( 0, 0, &wBuildNo );
 wMajorVersion = dwVersion >> 16;
 wMinorVersion = dwVersion & 0x0000FFFF;

 \endcode
*/
USBAPI u32 CCONV CamUSB_GetDllVersion( char* szVerStr = 0,
                                       u16   wMaxLen  = 0,
                                       u16 * pwBuild  = 0 );

// --------------------------------------------------------------------------
// CamUSB_GetDrvVersion
/*!
 * \brief  Returns the Driver version. => a ZERO value indicate that
 * \brief  not driver was detected
 *
 * \param    *szVerStr    Buffer to receive the Driver version message
 * \param    wMaxLen      Length of Buffer
 * \param    pwBuild      Pointer to the returned build number
 *
 * \retval  high-word contains the major version and the low-word
 *          contains the minor version
 *
 * \par Example:
 * \code

 #include <string>

 const u32 maxPath = 260;
 u32 dwVersion; 
 u16 wMajorVersion, wMinorVersion, wBuildNo; 
 std::string strVersion;

 // obtain the Version as string
 strVersion.resize( maxPath, 0 );
 CamUSB_GetDrvVersion( (char*) strVersion.c_str(), maxPath, 0 ); 
 strVersion.resize( dwStrLenght ); // limit to real string lenght


 // obtain Version as numeric values
 dwVersion = CamUSB_GetDrvVersion( 0, 0, &wBuildNo );
 wMajorVersion = dwVersion >> 16;
 wMinorVersion = dwVersion & 0x0000FFFF;

 \endcode
*/
USBAPI u32 CCONV CamUSB_GetDrvVersion( char* szVerStr = 0,
                                       u16   wMaxLen  = 0,
                                       u16 * pwBuild  = 0 );


// --------------------------------------------------------------------------
// CamUSB_GetCameraList
/*!
 * \brief   returns the number of connected cameras and if possible their
 *          Camera Version Information
 *
 * \param   pCamLst     Pointer to an S_CAMERA_LIST array
 *                      if NULL only the number of cameras is returned
 * \param   dwElements  Count of array elements
 *                      if 0 only the number of cameras is returned
 *
 *  \return number of connected cameras
 *
 * \remark  if the Serial number of the S_CAMERA_LIST struct is 0
 *          the camera is possibly already in use. If your application
 *          is using the camera call #CamUSB_FreeCamera to release
 *          the camera.
 */
USBAPI u32 CCONV CamUSB_GetCameraList( S_CAMERA_LIST *pCamLst,
                                       u32 dwElements );

// --------------------------------------------------------------------------
// CamUSB_GetCameraListEx
/*! \brief  returns the number of connected cameras and if possible their
 *          Camera Version Information and if available the user cfg
 *
 *  \param  pCamLstEx   Pointer to an S_CAMERA_LIST_EX array
 *                      if NULL only the number of cameras is returned
 *  \param  dwElements  Count of array elements
 *                      if 0 only the number of cameras is returned
 *
 *  \return  number of connected cameras
 *
 *  \remark if the Serial number of the #S_CAMERA_LIST_EX struct is 0
 *          the camera is possibly already in use. If your application
 *          is using the camera call #CamUSB_FreeCamera to release
 *          the camera.
 */
USBAPI u32 CCONV CamUSB_GetCameraListEx( S_CAMERA_LIST_EX *pCamLstEx,
                                         u32 dwElements );

// --------------------------------------------------------------------------
// CamUSB_GetCameraVersion
/*! \brief  Return basic camera information like sensor type and
 *          firmware version
 *
 *  \param  pCamVer pointer to a S_CAMERA_VERSION
 *  \param  nDevNr  Camera index number, that identifies the
 *                  camera device which should be used with this
 *                  function
 *
 *  \retval TRUE    success
 *  \retval FALSE   error
 *
 * \par Example: GetCameraVersion
 * \code

 S_CAMERA_VERSION sCV;
 sCV.dwStructSize = sizeof( S_CAMERA_VERSION );

 if (CamUSB_GetCameraVersion( &sCV ) != TRUE)
 {
   // error see CamUSB_GetLastError
 }
 else
 {
   // now you got the camera informations
 }

 \endcode
*/
USBAPI BOOL CCONV CamUSB_GetCameraVersion( S_CAMERA_VERSION *pCamVer,
                                           u08 nDevNr = 0 );


// --------------------------------------------------------------------------
// CamUSB_GetCameraStatus
/*! \brief    Return basic status information
 *
 *  \param    pCamStat  pointer to a S_CAMERA_STATUS
 *  \param    nDevNr    Camera index number, that identifies the
 *            camera device which should be used with this
 *            function
 *
 *  \retval   TRUE      success
 *  \retval   FALSE     error
 */
USBAPI BOOL CCONV CamUSB_GetCameraStatus( S_CAMERA_STATUS *pCamStat,
                                          u08 nDevNr = 0 );


// --------------------------------------------------------------------------
// CamUSB_GetCameraCfg
/*! \brief    Return the camera user configuration
 *
 *  \param    pCamCFG   pointer to a S_CAMERA_CFG
 *  \param    nDevNr    Camera index number, that identifies the
 *                      camera device which should be used with this
 *                      function
 *
 *  \retval    TRUE     success
 *  \retval    FALSE    error
 */
USBAPI BOOL CCONV CamUSB_GetCameraCfg( S_CAMERA_CFG *pCamCFG,
                                       u08 nDevNr = 0 );


//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Functions: Init and Free Cameras
//!@{
// ----------------------------------------------------------------------------
// InitCamera
/*!
 *  \brief  Resets and initializes the API internal camera data.
 *          If function flip (#FUNC_FLIP) is supported, it will be set
 *          to #FLIP_NONE.\n
 *          The capture mode will be set to #MODE_TRIGGERED_SW.\n
 *          On color sensors the resulting pixeltype will be initialized to
 *          #PIX_BGR8_PACKED (RGB-Color).\n
 *          If the parameter bBootFirmware is set to TRUE, all sensor internal
 *          settings will be cleared to default, if FALSE all sensor internal
 *          settings are unchanged.
 *
 *
 *  \param  nDevNr          Camera index number, that identifies the
 *                          camera device which should be used with this
 *                          function
 *  \param  dwSNR           Optional camera serial number. If no camera
 *                          with given serial number is available no camera
 *                          will be found. If dwSNR==NO_SERIAL_NUMBER the
 *                          serial number will be ignored!
 *  \param  bBootFirmware   True if the Firmware should be booted, else
 *                          pFirmware and dwFirmwareSize is ignored
 *  \param  pFirmware       Pointer to the Firmware which should be
 *                          uploaded and started, if NULL a flash memory
 *                          stored firmware will be started
 *  \param  dwFirmwareSize  firmware size in bytes,
 *                          this parameter is ignored if pFirmware==NULL
 *                             
 *  \retval TRUE            success
 *  \retval FALSE           error
 *
 *  \remark Warning: if #CamUSB_InitCamera return #retSLEEPMODE_ACTIVE 
 *  \remark a module of the connected camera is still or is already at 
 *  \remark a sleep mode. Try to avoid calling camera functions which 
 *  \remark may interact with the camera module at sleep mode.
 *  \remark See also #CamUSB_SetSleepMode / #CamUSB_GetSleepMode!
 *
 *  \par Example:
 *  \code

 if ( CamUSB_InitCamera() != TRUE )
 {
  // call CamUSB_GetLastError( GLOBAL_DEVNR ) and CamUSB_GetLastError( nDevNr ) 
  // to obtain the error reason
 }
 else
 {
   // init successful
 }

 \endcode
*/
USBAPI BOOL CCONV CamUSB_InitCamera( u08  nDevNr = 0,
                                     u32  dwSNR = NO_SERIAL_NUMBER,
                                     BOOL bBootFirmware = TRUE,
                                     u08* pFirmware = 0,
                                     u32  dwFirmwareSize = 0 );

// ----------------------------------------------------------------------------
// InitCameraEx
/*!
 *  \brief  Resets and initializes the API internal camera data.
 *          If function flip (#FUNC_FLIP) is supported, it will be set
 *          to #FLIP_NONE.\n
 *          The capture mode will be set to #MODE_TRIGGERED_SW.\n
 *          On color sensors the resulting pixeltype will be initialized to
 *          #PIX_BGR8_PACKED (RGB-Color).\n
 *          If the parameter bBootFirmware is set to TRUE, all sensor internal
 *          settings will be cleared to default, if FALSE all sensor internal
 *          settings are unchanged.
 *
 *
 *  \param  nDevNr          Camera index number, that identifies the
 *                          camera device which should be used with this
 *                          function
 *  \param  dwSNR           Optional camera serial number. If no camera
 *                          with given serial number is available no camera
 *                          will be found. If dwSNR==NO_SERIAL_NUMBER the
 *                          serial number will be ignored!
 *  \param  bBootFirmware   True if the Firmware should be booted, else
 *                          pFirmware and dwFirmwareSize is ignored
 *  \param  pFirmware       Pointer to the Firmware which should be
 *                          uploaded and started, if NULL a flash memory
 *                          stored firmware will be started
 *  \param  dwFirmwareSize  firmware size in bytes,
 *                          this parameter is ignored if pFirmware==NULL
 *  \param  bPlatformID     Optional camera platform id.  
 *                          if bPlatformID != #CPID_NONE, dwSNR must be 
 *                          different from #NO_SERIAL_NUMBER or bPlatformID
 *                          will be ignored!
 *                          If no camera match dwSNR and bPlatformID function 
 *                          return FALSE and last error is set to "no camera found".
 *                          
 *  \retval TRUE            success
 *  \retval FALSE           error
 *
 */
USBAPI BOOL CCONV CamUSB_InitCameraEx( u08  nDevNr = 0,
                                       u32  dwSNR = NO_SERIAL_NUMBER,
                                       BOOL bBootFirmware = TRUE,
                                       u08* pFirmware = 0,
                                       u32  dwFirmwareSize = 0,
                                       u08  bPlatformID = CPID_NONE );

// ----------------------------------------------------------------------------
// InitCameraExS
/*!
 *  \brief  Resets and initializes the API internal camera data.
 *          If function flip (#FUNC_FLIP) is supported, it will be set
 *          to #FLIP_NONE.\n
 *          The capture mode will be set to #MODE_TRIGGERED_SW.\n
 *          On color sensors the resulting pixeltype will be initialized to
 *          #PIX_BGR8_PACKED (RGB-Color).\n
 *          If the parameter bBootFirmware is set to TRUE, all sensor internal
 *          settings will be cleared to default, if FALSE all sensor internal
 *          settings are unchanged.
 *
 *
 *  \param  psCamInit   pointer to a #S_CAMERA_INIT structure, which setup initialization 
 *                      parameters and return the camera access device number "nDevNr",
 *                         
 *  \retval TRUE        success
 *  \retval FALSE       error   => call #CamUSB_GetLastError twice first with 
 *                        nDevNr = #GLOBAL_DEVNR if there is no error call with 
 *                        the returned device number nDevNr = psCamInit->nDevNr 
 *
 */
USBAPI BOOL CCONV CamUSB_InitCameraExS( S_CAMERA_INIT * psCamInit );


// ----------------------------------------------------------------------------
// CamUSB_FreeCamera
/*!
 *  \brief  Free allocated resources...
 *
 *  \param  nDevNr  Camera index number, that identifies the
 *                  camera device which should be used with this function
 *                  
 *  \retval TRUE    success
 *  \retval FALSE   error
 *
 *  \par Example:
 *  \code

 if ( CamUSB_FreeCamera() != TRUE )
 {
   // error see CamUSB_GetLastError
 }
 else
 {
   // free successful
 }

 \endcode
*/
USBAPI BOOL CCONV CamUSB_FreeCamera( u08 nDevNr = 0 );

//!@}


///////////////////////////////////////////////////////////////////////////////
//! \name Functions: Camera Functions
//!@{

// ----------------------------------------------------------------------------
// CamUSB_GetCameraFunctions
/*! \brief  returns the mask of the supported functions
 *
 *  \param  pulFunctionMask   pointer to the function mask
 *  \param  nDevNr            Camera index number, that identifies the
 *                            camera device which should be used with this
 *                            function
 *
 *  \retval TRUE              success
 *  \retval FALSE             error
*/
#ifndef _VBASIC6
USBAPI BOOL CCONV CamUSB_GetCameraFunctions( u64 *pulFunctionMask,
                                             u08 nDevNr = 0 );
#else
USBAPI BOOL CCONV CamUSB_GetCameraFunctions( u32 *pulFunctionMask,
                                             u08 nDevNr = 0 );

#endif

// ----------------------------------------------------------------------------
// CamUSB_GetCameraFunctionsEx
/*! \brief  returns the mask of the supported functions
*
*   \param  pqwApiFunctionMask  pointer to the function mask supported by Api
*                               (includes emulated features)
*   \param  pqwEmuFunctionMask  pointer to the function mask emulated by the Api
*   \param  pqwCamFunctionMask  pointer to the function mask supported by camera
*   \param  nDevNr              Camera index number, that identifies the
*                               camera device which should be used with this
*                               function
*
* \retval    TRUE    success
* \retval    FALSE    error
*/
USBAPI BOOL CCONV CamUSB_GetCameraFunctionsEx( u64* pqwApiFunctionMask,
                                               u64* pqwEmuFunctionMask,
                                               u64* pqwCamFunctionMask,
                                               u08 nDevNr = 0 );

// --------------------------------------------------------------------------
// CamUSB_GetFunctionCaps
/*! \brief  returns the camera supported function capability
 *
 *  \param  ulCamFunctionID Function ID which capability should be returned
 *  \param  pData         Pointer to the function id specific capability data
 *                        if pData = NULL pdwDataSize returns the function
 *                        specific data size...
 *  \param  pdwDataSize   Pointer to the max size of pData
 *  \param  nDevNr        Camera index number, that identifies the
 *                        camera device which should be used with this
 *                        function
 *
 *  \retval TRUE          success
 *  \retval FALSE         error
 */
#ifndef _VBASIC6
USBAPI BOOL CCONV CamUSB_GetFunctionCaps( u64   ulCamFunctionID,
                                          void* pData,
                                          u32*  pdwDataSize,
                                          u08   nDevNr = 0 );
#else
USBAPI BOOL CCONV CamUSB_GetFunctionCaps( u32   ulCamFunctionID,
                                          void* pData,
                                          u32*  pdwDataSize,
                                          u08   nDevNr = 0 );
#endif

// --------------------------------------------------------------------------
// CamUSB_GetFunction
/*! \brief  returns the current value of the specified camera function by
 *          the function id
 *
 *  \param  ulCamFunctionID Function ID which capability should be returned
 *  \param  pMsgIn          Pointer to the incoming message  buffer
 *  \param  pdwMsgIn        Pointer to the max size of incoming message buffer
 *  \param  pCmdOut         Pointer to the outgoing command data buffer
 *  \param  dwCmdOut        count byte to send from command buffer
 *  \param  pDataIn         Pointer to the incoming data buffer
 *  \param  dwDataIn        max size of incoming data buffer
 *  \param  nDevNr          Camera index number, that identifies the
 *                          camera device which should be used with this
 *                          function
 *
 *  \retval TRUE            success
 *  \retval FALSE           error
 */
#ifndef _VBASIC6
USBAPI BOOL CCONV CamUSB_GetFunction( u64    ulCamFunctionID,
                                      void*  pMsgIn,
                                      u32*   pdwMsgIn,
                                      void*  pCmdOut  = 0,
                                      u32    dwCmdOut = 0,
                                      void*  pDataIn  = 0,
                                      u32    dwDataIn = 0,
                                      u08    nDevNr   = 0 );
#else
USBAPI BOOL CCONV CamUSB_GetFunction( u32    ulCamFunctionID,
                                      void*  pMsgIn,
                                      u32*   pdwMsgIn,
                                      void*  pCmdOut  = 0,
                                      u32    dwCmdOut = 0,
                                      void*  pDataIn  = 0,
                                      u32    dwDataIn = 0,
                                      u08    nDevNr   = 0 );
#endif


// --------------------------------------------------------------------------
// CamUSB_SetFunction
/*! \brief  sets the current value of the specified camera function by
 *          the function id
 *
 *  \param  ulCamFunctionID Function ID which capability should be returned
 *  \param  pCmdOut         Pointer to the outgoing command data buffer
 *  \param  dwCmdOut        count byte to send from command buffer
 *  \param  pMsgIn          Pointer to the incoming message  buffer
 *  \param  pdwMsgIn        Pointer to the max size of incoming message buffer
 *  \param  pDataOut        Pointer to the outgoing data buffer
 *  \param  dwDataOut       count byte to send from data buffer
 *  \param  nDevNr          Camera index number, that identifies the
 *                          camera device which should be used with this
 *                          function
 *
 *  \retval TRUE            success
 *  \retval FALSE           error
 */
#ifndef _VBASIC6
USBAPI BOOL CCONV CamUSB_SetFunction(  u64    ulCamFunctionID,
                                       void*  pCmdOut,
                                       u32    dwCmdOut,
                                       void*  pMsgIn    = 0,
                                       u32*   pdwMsgIn  = 0,
                                       void*  pDataOut  = 0,
                                       u32    dwDataOut = 0,
                                       u08    nDevNr    = 0 );
#else
USBAPI BOOL CCONV CamUSB_SetFunction( u32    ulCamFunctionID,
                                      void*  pCmdOut,
                                      u32    dwCmdOut,
                                      void*  pMsgIn    = 0,
                                      u32*   pdwMsgIn  = 0,
                                      void*  pDataOut  = 0,
                                      u32    dwDataOut = 0,
                                      u08    nDevNr    = 0 );
#endif


//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Functions: Image Capture and Buffer Control
//!@{
// --------------------------------------------------------------------------
// CamUSB_GetImage
/*! \brief  Captures an image from the camera to the
 *          referenced image buffer or returns a pointer
 *          to the image buffer
 *
 *  \param  ppImageBuffer         Pointer to the Pointer to the image buffer
 *                                Point the pointer to NULL, a valid image
 *                                pointer will be returned, this pointer stay
 *                                valid to the next #CamUSB_GetImage
 *  \param  ppImageHeader         Pointer to the pointer to the image  header struct
 *                                point the pointer to NULL, a valid image header 
 *                                struct pointer will be returned, this pointer stay
 *                                valid to the next #CamUSB_GetImage
 *  \param  dwImageBufSize        Size in byte of the image buffer, if pImageBuffer
 *                                point to a valid buffer (is not 0)
 *  \param  nDevNr                Camera index number, that identifies the
 *                                camera device which should be used with this
 *                                function
 *  \param  dwTimeout             Time to wait for image completion before function  
 *                                return retNOIMG (time in ms)
 *  \param  dwImageBufferLineSize Size in bytes of one image buffer line to allow 
 *                                aligned image buffers (if parameter is 0 or if 
 *                                API-DLL provided buffers are used this parameter 
 *                                it is ignored)
 *
 *  \retval TRUE                  success
 *  \retval FALSE                 error
 */
USBAPI BOOL CCONV CamUSB_GetImage ( u08 **ppImageBuffer,
                                    S_IMAGE_HEADER **ppImageHeader,
                                    u32 dwImageBufSize = 0,
                                    u08 nDevNr = 0,
                                    u32 dwTimeout = GETIMAGE_DEFAULT_TIMEOUT,
                                    u32 dwImageBufferLineSize = 0 );


// --------------------------------------------------------------------------
// CamUSB_GetImage_SP
/*! \brief    Captures an image from the camera to the
 *  \brief    referenced image buffer (only for compatibility with VB6)
 *
 *  \param    pImageBuffer          Pointer to the image buffer
 *
 *  \param    pImageHeader          Pointer the image  header struct
 *
 *  \param    dwImageBufferSize     Size in Byte of the image buffer, if 
 *                                  pImageBuffer point to a valid buffer 
 *  \param    nDevNr                Camera index number, that identifies the
 *                                  camera device which should be used with
 *                                  this function
 *  \param    dwImageBufferLineSize Size in bytes of one image buffer line 
 *                                  to allow aligned image buffers (if 
 *                                  parameter is 0 it is ignored)
 *
 *  \retval    TRUE      success
 *  \retval    FALSE      error 
 */
USBAPI BOOL CCONV CamUSB_GetImage_SP ( u08  *pImageBuffer,
                                       S_IMAGE_HEADER *pImageHeader,
                                       u32  dwImageBufferSize = 0,
                                       u08  nDevNr = 0,
                                       u32  dwImageBufferLineSize = 0 );

// --------------------------------------------------------------------------
// CamUSB_ReleaseImage
/*! \brief  Release the image buffer for further use
 *          This function must be called to release the image buffers that
 *          are returned by a #CamUSB_GetImage call.
 *
 *  \param  pImageBuffer  Pointer to the image buffer to be released
 *  \param  pImageHeader  Pointer to the image header struct to be released
 *  \param  nDevNr        Camera index number, that identifies the
 *                        camera device which should be used with this
 *                        function
 *
 *  \retval TRUE          success
 *  \retval FALSE         error
 */
USBAPI BOOL CCONV CamUSB_ReleaseImage ( u08 *pImageBuffer,
                                        S_IMAGE_HEADER *pImageHeader,
                                        u08 nDevNr = 0 );

// --------------------------------------------------------------------------
// CamUSB_AbortGetImage
/*! \brief  This function abort a pending #CamUSB_GetImage call.
 *          If a pending #CamUSB_GetImage - call exists, #CamUSB_GetImage 
 *          abort it's internal wait and return the error code #retUSER_ABORT
 *  \brief  will be returned by #CamUSB_GetLastError.
 *
 *  \param  nDevNr  Camera index number, that identifies the
 *                  camera device which should be used with this
 *                  function
 *
 * \retval  TRUE    success
 *  \retval FALSE   error
 */
USBAPI BOOL CCONV CamUSB_AbortGetImage( u08 nDevNr = 0 );


// --------------------------------------------------------------------------
// CamUSB_TriggerImage
/*! \brief  This function sends a Software-Trigger-Event to the camera,
 *          which will start the image acquisition.
 *          If your capture mode (#CamUSB_SetCaptureMode) different from
 *          #MODE_ASYNC_TRIGGER the function will fail and the error code
 *          is #retCAPMODE_WRONG (see #CamUSB_GetLastError).
 *
 *  \param  pAsyncTrigDev   Pointer to a list of camera trigger struct,
                            which identifies the camera devices
                            to be triggered
 *  \param  nCntDevs        Number of device numbers at the list
 *  \param  dwTimeout       Time to wait for image completion before function  
 *
 * \retval  Return TRUE if successfully. On FALSE you have to call
 *          "CamUSB_GetLastError( #GLOBAL_DEVNR )" to check the
 *          global error value, if it "retOK" you have to check
 *          the error codes returned in pAsyncTrigDev - struct!
 */
USBAPI BOOL CCONV CamUSB_TriggerImage( S_ASYNC_TRIGGER_DEV *pAsyncTrigDev, 
                                       u08 nCntDevs, 
                                       u32 dwTimeout = TRIGGERIMAGE_DEFAULT_TIMEOUT );


// --------------------------------------------------------------------------
// CamUSB_SetCaptureMode
/*! \brief  Set camera specific capture modes, which are valid on the 
 *          next image.
 *
 *  \param  nCaptureMode      Specifies the mode see #MODE_TRIGGERED_SW
 *  \param  nImageCount       count images to shot per trigger event
 *                            nImageCount is ignored for #MODE_CONTINUOUS
 *                            and #MODE_ASYNC_TRIGGER
 *  \param  nDevNr            Camera index number, that identifies the
 *                            camera device which should be used with this
 *                            function
 *  \param  wTransferOption   this option change the camera image transfer
 *                            behavior at #MODE_CONTINUOUS and
 *                            #MODE_TRIGGERED_HW capture mode.
 *                            (see #TRANSFER_OPTION_DEFAULT or
 *                             #TRANSFER_OPTION_STATIC_IMAGE_DETECTION)
 *  \param  pnMaxImageCount   maximal number of image possible
 *                            to be recorded at trigger event
 *
 *  \retval TRUE              success
 *  \retval FALSE             error 
*/
USBAPI BOOL CCONV CamUSB_SetCaptureMode( u08  nCaptureMode,
                                         u08  nImageCount,
                                         u08  nDevNr = 0,
                                         u16  wTransferOption = TRANSFER_OPTION_DEFAULT,
                                         u08* pnMaxImageCount = 0 );

// --------------------------------------------------------------------------
// CamUSB_GetCaptureMode
/*! \brief      return the camera specific capture mode
 *
 *  \param [out]pCaptureMode  camera mode
 *  \param      pImageCount   count images to shot per trigger event
 *  \param      nDevNr        Camera index number, that identifies the
 *                            camera device which should be used with this
 *                            function
 *
 *  \retval     TRUE          success
 *  \retval     FALSE         error
 *
 */
USBAPI BOOL CCONV CamUSB_GetCaptureMode ( u08* pCaptureMode,
                                          u08* pImageCount,
                                          u08  nDevNr = 0 );

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Functions: Camera Device Notification
//!@{
// --------------------------------------------------------------------------
// CamUSB_SetDeviceNotifyMsg
/*! \brief  Initialize notification for device arrival/removal events.
 *
 *        This function initializes the messaging system for an arrival
 *        or removal event of the device. The Message defined by the
 *        parameter WM_ARRIVAL is sent when the device was connected to
 *        the Host PC. The Message defined by WM_REMOVAL is sent if the
 *        device is unplugged.\n
 *
 *        WM_REMOVAL:\n
 *        => WPARAM = #U_CAMERA_NOTIFY structure\n
 *        => LPARAM = device serial number\n
 *
 *        WM_ARRIVAL:\n
 *        => WPARAM = #U_CAMERA_NOTIFY structure\n
 *        => LPARAM = device serial number\n
 *
 *  \param  hWnd        handle of the window which should receive the
 *                      WM_ARRIVAL and  WM_REMOVAL message
 *  \param  WM_ARRIVAL  user defined message ID for device arrival
 *  \param  WM_REMOVAL  user defined message ID for device removal
 *
 *  \retval TRUE        success
 *  \retval FALSE       error
 *
 *  \remark On error case use #GLOBAL_DEVNR as "nDevNr" if you call
 *          #CamUSB_GetLastError
 *
 * \par Example:
 *  - \ref example_4
 */
USBAPI BOOL CCONV CamUSB_SetDeviceNotifyMsg(HWND  hWnd,
                                            u32   WM_ARRIVAL,
                                            u32   WM_REMOVAL );

// --------------------------------------------------------------------------
// CamUSB_ClearDeviceNotifyMsg
/*! \brief  Uninitialize notification for device arrival/removal events.
 *
 *          This function deactivates the notification message when the device
 *          was plugged in or out.
 *
 *  \retval TRUE    success
 *  \retval FALSE   error
 *
 *  \see  SetDeviceNotifyMsg
 */
USBAPI BOOL CCONV CamUSB_ClearDeviceNotifyMsg( void );


// --------------------------------------------------------------------------
// CamUSB_SetEventNotification
/*! \brief  Set and Reset camera event notifications.
 *
 *          This function setup notifications for events like image  
 *          transfer start and image transfer done for a specific camera.
 *          The passed S_EVENT_NOTIFICATION structures contains the Event-ID
 *          for which a notification should be send and the way to do this.
 *          There are to different methods:  sending an windows message
 *          or trigger an event provided. Only one method can be active, the
 *          other is automatically disabled.
 *
 *  \param  pEventNotify        pointer to a #S_EVENT_NOTIFICATION structure 
 *                              with necessary notification informations
 *  \param  dwCountEventNotify  count notifications passed with pEventNotify
 *
 *  \param  nDevNr              Camera index number, that identifies the
 *                              camera device which should be used with this
 *                              function
 *
 *  \retval TRUE                success
 *  \retval FALSE               error
 *
 * \par Example:
 *  - \ref example_12
 */                                        
USBAPI BOOL CCONV CamUSB_SetEventNotification( S_EVENT_NOTIFICATION* pEventNotify, 
                                               u32 dwCountEventNotify, 
                                               u08  nDevNr = 0 );


// --------------------------------------------------------------------------
// CamUSB_GetEventNotification
/*! \brief  Get the current camera event notifications.
 *
 *          This return the notifications setup values set by the application.
 *
 *  \param  pEventNotify        pointer to a #S_EVENT_NOTIFICATION structure 
 *                              in which the notification informations will 
 *                              be returned
 *  \param  pdwCountEventNotify count notifications which can be written to
 *                              the passed pEventNotify buffer
 *                              (if pEventNotify is NULL the number of 
 *                              #S_EVENT_NOTIFICATION's is return)
 *
 *  \param  nDevNr              Camera index number, that identifies the
 *                              camera device which should be used with this
 *                              function
 *
 *  \retval TRUE                success
 *  \retval FALSE               error
 *
 * \par Example:
 *  - \ref example_12
 */                         
USBAPI BOOL CCONV CamUSB_GetEventNotification( S_EVENT_NOTIFICATION* pEventNotify, 
                                               u32* pdwCountEventNotify, 
                                               u08  nDevNr = 0 );


//!@}

#endif // _CAMUSB_API_H_
