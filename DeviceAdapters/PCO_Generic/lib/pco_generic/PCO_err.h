//-----------------------------------------------------------------//
// Name        | PCO_err.h                   | Type: ( ) source    //
//-------------------------------------------|       (*) header    //
// Project     | PCO                         |       ( ) others    //
//-----------------------------------------------------------------//
// Platform    | - Embedded platforms like M16C, AVR32, PIC32 etc. //
//             | - PC with several Windows versions, Linux etc.    //
//-----------------------------------------------------------------//
// Environment | - Platform dependent                              //
//-----------------------------------------------------------------//
// Purpose     | PCO - Error defines                               //
//-----------------------------------------------------------------//
// Author      | FRE, MBL, LWA, PCO AG, Kelheim, Germany           //
//-----------------------------------------------------------------//
// Revision    | versioned using SVN                               //
//-----------------------------------------------------------------//
// Notes       | This error defines should be used in every future //
//             | design. It is designed to hold a huge range of    //
//             | errors and warnings                               //
//-----------------------------------------------------------------//
// (c) 2003-2014 PCO AG * Donaupark 11 * D-93309 Kelheim / Germany //
// *  Phone: +49 (0)9441 / 2005-0  *                               //
// *  Fax:   +49 (0)9441 / 2005-20 *  Email: info@pco.de           //
//-----------------------------------------------------------------//


//-----------------------------------------------------------------//
// Revision History:                                               //
//-----------------------------------------------------------------//
// Rev.:     | Date:      | Changed:                               //
// --------- | ---------- | ---------------------------------------//
//  00.01    | 25.06.2003 |  new file, FRE                         //
//-----------------------------------------------------------------//
//  00.02    | 05.09.2003 |  - LWa 05.09.2003: error code added:   //
//           |            |      PCO_ERROR_SEGMENT_TO_SMALL        //
//           |            |    (just addition, no new version!)    //
//-----------------------------------------------------------------//
//  00.03    | 14.10.2003 |  - FRE: Error text file changed        //
//           |            |    (new version)                       // 
//           | 23.10.2003 |  - LWA:added:                          //
//           |            |  SC2_ERROR_CONNY                       //
//           |            |  PCO_ERROR_FIRMWARE_DEVICE_OPEN_FAILED // 
//           |            |  (just addition, no new version!)      //
//-----------------------------------------------------------------//
//  00.04    | 23.10.2003 |  - LWA: reorganized error codes for    //
//           |            |    firmware update commands.           //
//-----------------------------------------------------------------//
//  00.05    | 12.12.2003 |  - FRE: changed PCO_errt.h             //
//           |            |                                        //
//           | 17.03.2004 |  - LWA added:                          //
//           |            |  PCO_ERROR_FIRMWARE_UNKNOWN_COMMAND    // 
//           |            |  (just addition, no new version!)      //
//           | 23.03.2004 |  - LWA added (by FRE):                 //
//           |            |  PCO_WARNING_FIRMWARE_HIGH_TEMPERATURE //
//           |            |  PCO_ERROR_FIRMWARE_HIGH_TEMPERATURE   //
//           |            |  Device codes:                         //
//           |            |  PCO_ERROR_PCOCAM_CCD                  // 
//           |            |  PCO_ERROR_PCOCAM_POWER                // 
//           |            |  MBL added:                            // 
//           |            |  PCO_WARNING_FIRMWARE_CCDCAL_NOT_LOCKED// 
//           | 24.03.2004 |  - FRE added:                          //
//           |            |  PCO_ERROR_SDKDLL_WRONGBUFFERNR        //
//           |            |  Commented SC2_xxx devices!            //
//           | 26.07.2004 |  - FRE added:                          //
//           |            |  PCO_ERROR_SDKDLL_DLLNOTFOUND          //
//           | 10.11.2004 |  - FRE added:                          //
//           |            |  PCO_ERROR_SDKDLL_BUFALREADYASSIGNED   //
//           |            |  PCO_ERROR_SDKDLL_EVENTALREADYASSIGNED //
//           | 08.03.2005 |  - FRE: Added                          //
//           |            |        PCO_ERROR_APPLICATION_WRONGRES  //
//           | 03.11.2005 | - LWA: Added new line at end of file   //
//           |            |   to suppress warnings (Gnu Compiler)  //
//           | 19.12.2005 | - FRE: Added                           //
//           |            |   PCO_ERROR_APPLICATION_DISKFULL       //
//           | 22.08.2006 | - FRE: Added                           //
//           |            |   PCO_ERROR_NOTINIT                    //
//           | 11.04.2007 | - FRE: Added                           //
//           |            |   PCO_ERROR_FILEDLL                    //
//           |            |   Added Disk Full to common            //
//           | 25.06.2008 | - FRE: Added                           //
//           |            |   PCO_ERROR_APPLICATION_SET_VALUES     //
//           | 08.12.2008 | - FRE: Added                           //
//           |            |   PCO_ERROR_SDKDLL_RECORDINGMUSTBEON   //
//           | 12.01.2009 | - FRE: Reviewed error/txt assembly     //
//           |            |   Enhanced error decoding due to       //
//           |            |   device and layer numbering           //
//-----------------------------------------------------------------//


// Do not change any values after release! Only additions are allowed!

#ifndef PCO_ERR_H
#define PCO_ERR_H

// Error messages are built with the error source + error layer + error code.
// In case of 'no error' the error source is not added.

// The error itself is 32bit signed. 
// Bits 0-11 are used to indicate the error number.
// Bits 12-15 shows the layer of the error source.
// Bits 16-23 reflect the error source.
// Bits 24-28 are not used.
// Bit 29 is the common error group flag. This flag is used to lookup
// the error text inside the correct array.
// Bit 31 indicates an error.
// Bit 30 is set in addition to bit 31 and indicates a warning. 

// e.g.: 0xC0000080 indicates a warning,
//       0x800A3001 is an error inside the SC2-SDK-dll.
// There is a total of 15 layer sources
// There is a total of 4095 errors and warnings for each device.
// There is a total of 255 devices.

// MSB                                 LSB
// XXXX XXXX XXXX XXXX XXXX XXXX XXXX XXXX
// |||| |||| |||| |||| |||| |||| |||| ||||
// |||| |||| |||| |||| |||| --------------- Error or warning code
// |||| |||| |||| |||| ||||
// |||| |||| |||| |||| -------------------- Layer code
// |||| |||| |||| |||| 
// |||| |||| ------------------------------ Device code
// |||| |||| 
// |||------------------------------------- reserved for future use
// |||
// ||-------------------------------------- Common error code flag
// ||
// |--------------------------------------- Warning indication bit
// |
// ---------------------------------------- Error indication bit

// It should be reached that most error codes reside inside the common
// code range.

#if defined PCO_ERR_H_CREATE_OBJECT

DWORD GetError(DWORD dwerr)
{
  return dwerr & 0xC000FFFF;
}

DWORD GetErrorSource(DWORD dwerr)
{
  return dwerr & 0x3FFF0000;
}

#else

//#if defined _WIN32
//#pragma message( "Please define 'PCO_ERR_H_CREATE_OBJECT' in your files once," )
//#pragma message( "to avoid a linker error-message if you call GetError or GetErrorSource!" )
//#pragma message( "Compiling " __FILE__ ) 
//#endif

DWORD GetError(DWORD dwerr);
DWORD GetErrorSource(DWORD dwerr);

#endif


// ====================================================================================================== //
// -- 0. Code for Success: ------------------------------------------------------------------------------ //
// ====================================================================================================== //

#define PCO_NOERROR                        0x00000000    // no error



// ====================================================================================================== //
// -- 1. Masks for evaluating error layer, error device and error code: --------------------------------- //
// ====================================================================================================== //

#define PCO_ERROR_CODE_MASK                0x00000FFF    // in this bit range the error codes reside
#define PCO_ERROR_LAYER_MASK               0x0000F000    // in this bit range the layer codes reside
#define PCO_ERROR_DEVICE_MASK              0x00FF0000    // bit range for error devices / sources 
#define PCO_ERROR_RESERVED_MASK            0x1F000000    // reserved for future use
#define PCO_ERROR_IS_COMMON                0x20000000    // indicates error message common to all layers
#define PCO_ERROR_IS_WARNING               0x40000000    // indicates a warning
#define PCO_ERROR_IS_ERROR                 0x80000000    // indicates an error condition



// ====================================================================================================== //
// -- 2. Layer definitions: ----------------------------------------------------------------------------- //
// ====================================================================================================== //

#define PCO_ERROR_FIRMWARE                 0x00001000    // error inside the firmware
#define PCO_ERROR_DRIVER                   0x00002000    // error inside the driver
#define PCO_ERROR_SDKDLL                   0x00003000    // error inside the SDK-dll
#define PCO_ERROR_APPLICATION              0x00004000    // error inside the application

// Common device codes (should start with PCO_)
// Device codes in each layer group should be numbered in ascending order.
// No device code in a layer group MUST be used twice!!
// ====================================================================================================== //
// -- 3.1 FIRMWARE error sources / devices: ------------------------------------------------------------- //
// ====================================================================================================== //

// SC2 device codes (should start with SC2_)
#define SC2_ERROR_POWER_CPLD               0x00010000    // error at CPLD in pco.power unit
#define SC2_ERROR_HEAD_UP                  0x00020000    // error at uP of head board in pco.camera
#define SC2_ERROR_MAIN_UP                  0x00030000    // error at uP of main board in pco.camera 
#define SC2_ERROR_FWIRE_UP                 0x00040000    // error at uP of firewire board in pco.camera 
#define SC2_ERROR_MAIN_FPGA                0x00050000    // error at FPGA of main board in pco.camera 
#define SC2_ERROR_HEAD_FPGA                0x00060000    // error at FGPA of head board in pco.camera 
#define SC2_ERROR_MAIN_BOARD               0x00070000    // error at main board in pco.camera
#define SC2_ERROR_HEAD_CPLD                0x00080000    // error at CPLD of head board in pco.camera
#define SC2_ERROR_SENSOR                   0x00090000    // error at image sensor (CCD or CMOS)
#define SC2_ERROR_POWER                    0x000D0000    // error within power unit
#define SC2_ERROR_GIGE                     0x000E0000    // error at uP of GigE board GigE firmware
#define SC2_ERROR_USB                      0x000F0000    // error at uP of GigE board USB firmware
#define SC2_ERROR_BOOT_FPGA                0x00100000    // error at Boot FPGA in pco.camera 
#define SC2_ERROR_BOOT_UP                  0x00110000    // error at Boot FPGA in pco.camera 

// Future camera/firmware device codes should be placed here:

// obsolete (will be removed in a future release):
// SC2_ERROR_SDKDLL renamed to PCO_ERROR_PCO_SDKDLL, since it was misplaced here
#define SC2_ERROR_SDKDLL                   0x000A0000    // error inside the SDKDLL

// ====================================================================================================== //
// -- 3.2 DRIVER devices -------------------------------------------------------------------------------- //
// ====================================================================================================== //

#define PCI540_ERROR_DRIVER                0x00200000    // error at pixelfly driver
//specific error codes are in file pccddk_e.h

#define PCI525_ERROR_DRIVER                0x00210000    // error at sensicam driver

#define PCO_ERROR_DRIVER_FIREWIRE          0x00300000    // error inside the firewire driver
#define PCO_ERROR_DRIVER_USB               0x00310000    // error inside the usb driver
#define PCO_ERROR_DRIVER_GIGE              0x00320000    // error inside the GigE driver
#define PCO_ERROR_DRIVER_CAMERALINK        0x00330000    // error inside the CameraLink driver
#define PCO_ERROR_DRIVER_USB3              0x00340000    // error inside the usb 3.0 driver
#define PCO_ERROR_DRIVER_WLAN              0x00350000    // error inside the usb 3.0 driver

// obsolete (will be removed in a future release):
// SC2_ERROR_DRIVER renamed to PCO_ERROR_DRIVER_xyz
#define SC2_ERROR_DRIVER                   0x000B0000    // error inside the driver

// ====================================================================================================== //
// -- 3.3 SDKDLL devices -------------------------------------------------------------------------------- //
// ====================================================================================================== //
#define PCO_ERROR_PCO_SDKDLL               0x000A0000    // error inside the camera sdk dll
#define PCO_ERROR_CONVERTDLL               0x00110000    // error inside the convert dll
#define PCO_ERROR_FILEDLL                  0x00120000    // error inside the file dll
#define PCO_ERROR_JAVANATIVEDLL            0x00130000    // error inside a java native dll

#define PCO_ERROR_PROGLIB                  0x00140000    // error inside the programmer library


// ====================================================================================================== //
// -- 3.4 Application devices --------------------------------------------------------------------------- //
// ====================================================================================================== //
#define PCO_ERROR_CAMWARE                  0x00100000    // error in CamWare (also some kind of "device")

#define PCO_ERROR_PROGRAMMER               0x00110000    // error in Programmer

#define PCO_ERROR_SDKAPPLICATION           0x00120000    // error in SDK Applikation

// ====================================================================================================== //
// -- 4. Error Messages: -------------------------------------------------------------------------------- //
// ====================================================================================================== //


  // Notes:
  //
  // 1. Common error codes
  //
  // The common error codes are codes which have been found inside more than one layer.
  // The resulting error code is built by adding the layer and source device to the error code.
  //
  // e.g. CamWare - file I/O error: error = PCO_ERROR_CAMWARE 
  //                                      + PCO_ERROR_APPLICATION 
  //                                      + PCO_NOERROR_NOFILE
  //
  //      SC2 Driver - No memory:   error = SC2_ERROR_DRIVER 
  //                                      + PCO_ERROR_DRIVER 
  //                                      + PCO_ERROR_NOMEMORY
  //
  // 2. Specific error codes
  //
  // The specific error codes are codes which have been found inside only one layer.
  // The resulting error code is built by adding the source device to the error code.
  //
  // e.g. CamWare - pic. timeout error: err = PCO_ERROR_CAMWARE 
  //                                        + PCO_ERROR_APPLICATION_PICTURETIMEOUT
  //
  //      SC2 Driver - Init failed:     err = SC2_ERROR_DRIVER 
  //                                        + PCO_ERROR_DRIVER_NOTINIT


// ------------------------------------------------------------------------------------------------------ //
// -- 4.1. Error codes common to all layers: ------------------------------------------------------------ //
// ------------------------------------------------------------------------------------------------------ //

  // Includes the error indication bits PCO_ERROR_IS_ERROR and PCO_ERROR_IS_COMMON!
  // Layer code and device code have to be added!

#define PCO_ERROR_WRONGVALUE                        0xA0000001 // Function-call with wrong parameter
#define PCO_ERROR_INVALIDHANDLE                     0xA0000002 // Handle is invalid
#define PCO_ERROR_NOMEMORY                          0xA0000003 // No memory available
#define PCO_ERROR_NOFILE                            0xA0000004 // A file handle could not be opened.
#define PCO_ERROR_TIMEOUT                           0xA0000005 // Timeout in function
#define PCO_ERROR_BUFFERSIZE                        0xA0000006 // A buffer is to small
#define PCO_ERROR_NOTINIT                           0xA0000007 // The called module is not initialized
#define PCO_ERROR_DISKFULL                          0xA0000008 // Disk full.

#define PCO_ERROR_VALIDATION                        0xA0000010 // Validation after programming camera failed
#define PCO_ERROR_LIBRARYVERSION                    0xA0000011 // wrong library version 
#define PCO_ERROR_CAMERAVERSION                     0xA0000012 // wrong camera version
#define PCO_ERROR_NOTAVAILABLE                      0xA0000013 // Option is not available
 
// ------------------------------------------------------------------------------------------------------ //
// -- 4.2. Error codes generated by the driver: --------------------------------------------------------- //
// ------------------------------------------------------------------------------------------------------ //

  // Includes the error indication bit PCO_ERROR_IS_ERROR as well as the layer PCO_ERROR_DRIVER!
  // Device code has to be added!!!

#define PCO_ERROR_DRIVER_NOTINIT                    0x80002001 // Initialization failed; no camera connected
#define PCO_ERROR_DRIVER_WRONGOS                    0x80002005 // Wrong driver for this OS
#define PCO_ERROR_DRIVER_NODRIVER                   0x80002006 // Open driver or driver class failed
#define PCO_ERROR_DRIVER_IOFAILURE                  0x80002007 // I/O operation failed
#define PCO_ERROR_DRIVER_CHECKSUMERROR              0x80002008 // Error in telegram checksum
#define PCO_ERROR_DRIVER_INVMODE                    0x80002009 // Invalid Camera mode
#define PCO_ERROR_DRIVER_DEVICEBUSY                 0x8000200B // device is hold by an other process
#define PCO_ERROR_DRIVER_DATAERROR                  0x8000200C // Error in reading or writing data to board
#define PCO_ERROR_DRIVER_NOFUNCTION                 0x8000200D // No function specified
#define PCO_ERROR_DRIVER_KERNELMEMALLOCFAILED       0x8000200E // Kernel Memory allocation in driver failed

#define PCO_ERROR_DRIVER_BUFFER_CANCELLED           0x80002010 // buffer was cancelled
#define PCO_ERROR_DRIVER_INBUFFER_SIZE              0x80002011 // iobuffer in too small for DeviceIO call
#define PCO_ERROR_DRIVER_OUTBUFFER_SIZE             0x80002012 // iobuffer out too small for DeviceIO call
#define PCO_ERROR_DRIVER_FUNCTION_NOT_SUPPORTED     0x80002013 // this DeviceIO is not supported
#define PCO_ERROR_DRIVER_BUFFER_SYSTEMOFF           0x80002014 // buffer returned because system sleep
#define PCO_ERROR_DRIVER_DEVICEOFF                  0x80002015 // device is disconnected
#define PCO_ERROR_DRIVER_RESOURCE                   0x80002016 // required system resource not avaiable
#define PCO_ERROR_DRIVER_BUSRESET                   0x80002017 // busreset occured during system call
#define PCO_ERROR_DRIVER_BUFFER_LOSTIMAGE           0x80002018 // lost image status from grabber


#define PCO_ERROR_DRIVER_SYSERR                     0x80002020 // a call to a windows-function fails
#define PCO_ERROR_DRIVER_REGERR                     0x80002022 // error in reading/writing to registry
#define PCO_ERROR_DRIVER_WRONGVERS                  0x80002023 // need newer called vxd or dll 
#define PCO_ERROR_DRIVER_FILE_READ_ERR              0x80002024 // error while reading from file 
#define PCO_ERROR_DRIVER_FILE_WRITE_ERR             0x80002025 // error while writing to file 

#define PCO_ERROR_DRIVER_LUT_MISMATCH               0x80002026 // camera and dll lut do not match
#define PCO_ERROR_DRIVER_FORMAT_NOT_SUPPORTED       0x80002027 // grabber does not support the transfer format
#define PCO_ERROR_DRIVER_BUFFER_DMASIZE             0x80002028 // dmaerror not enough data transferred

//pixelfly driver
#define PCO_ERROR_DRIVER_WRONG_ATMEL_FOUND          0x80002029 // version information verify failed wrong typ id
#define PCO_ERROR_DRIVER_WRONG_ATMEL_SIZE           0x8000202A // version information verify failed wrong size
#define PCO_ERROR_DRIVER_WRONG_ATMEL_DEVICE         0x8000202B // version information verify failed wrong device id
#define PCO_ERROR_DRIVER_WRONG_BOARD                0x8000202C // board firmware not supported from this driver
#define PCO_ERROR_DRIVER_READ_FLASH_FAILED          0x8000202D // board firmware verify failed
#define PCO_ERROR_DRIVER_HEAD_VERIFY_FAILED         0x8000202E // camera head is not recognized correctly  
#define PCO_ERROR_DRIVER_HEAD_BOARD_MISMATCH        0x8000202F // firmware does not support connected camera head

#define PCO_ERROR_DRIVER_HEAD_LOST                  0x80002030 // camera head is not connected
#define PCO_ERROR_DRIVER_HEAD_POWER_DOWN            0x80002031 // camera head power down 
#define PCO_ERROR_DRIVER_CAMERA_BUSY                0x80002032 // camera busy


// image transfer mode
#define PCO_ERROR_DRIVER_BUFFERS_PENDING            0x80002033 // camera busy




  // The following error messages have been shifted to the common error code range:
  //
  //    PCO_ERROR_DRIVER_TIMEOUT                    0x80002002 // Timeout in any function
  //    PCO_ERROR_DRIVER_NOFILE                     0x80002021 // cannot open file



// ------------------------------------------------------------------------------------------------------ //
// -- 4.3. Error codes generated by the SDK DLL: -------------------------------------------------------- //
// ------------------------------------------------------------------------------------------------------ //

  // Includes the error indication bit PCO_ERROR_IS_ERROR as well as the layer PCO_ERROR_FIRMWARE!
  // Device code has to be added!!!

#define PCO_ERROR_SDKDLL_NESTEDBUFFERSIZE           0x80003001 // The wSize of an embedded buffer is to small.
#define PCO_ERROR_SDKDLL_BUFFERSIZE                 0x80003002 // The wSize of a buffer is to small.
#define PCO_ERROR_SDKDLL_DIALOGNOTAVAILABLE         0x80003003 // A dialog is not available
#define PCO_ERROR_SDKDLL_NOTAVAILABLE               0x80003004 // Option is not available
#define PCO_ERROR_SDKDLL_SYSERR                     0x80003005 // a call to a windows-function fails
#define PCO_ERROR_SDKDLL_BADMEMORY                  0x80003006 // Memory area is invalid

#define PCO_ERROR_SDKDLL_BUFCNTEXHAUSTED            0x80003008 // Number of available buffers is exhausted

#define PCO_ERROR_SDKDLL_ALREADYOPENED              0x80003009 // Dialog is already open
#define PCO_ERROR_SDKDLL_ERRORDESTROYWND            0x8000300A // Error while destroying dialog.
#define PCO_ERROR_SDKDLL_BUFFERNOTVALID             0x8000300B // A requested buffer is not available.
#define PCO_ERROR_SDKDLL_WRONGBUFFERNR              0x8000300C // Buffer nr is out of range..
#define PCO_ERROR_SDKDLL_DLLNOTFOUND                0x8000300D // A DLL could not be found
#define PCO_ERROR_SDKDLL_BUFALREADYASSIGNED         0x8000300E // Buffer already assigned to another buffernr.
#define PCO_ERROR_SDKDLL_EVENTALREADYASSIGNED       0x8000300F // Event already assigned to another buffernr.
#define PCO_ERROR_SDKDLL_RECORDINGMUSTBEON          0x80003010 // Recording must be activated
#define PCO_ERROR_SDKDLL_DLLNOTFOUND_DIVZERO        0x80003011 // A DLL could not be found, due to div by zero

#define PCO_ERROR_SDKDLL_BUFFERALREADYQUEUED        0x80003012 // buffer is already queued
#define PCO_ERROR_SDKDLL_BUFFERNOTQUEUED            0x80003013 // buffer is not queued 

#define PCO_WARNING_SDKDLL_BUFFER_STILL_ALLOKATED   0xC0003001 // Buffers are still allocated

#define PCO_WARNING_SDKDLL_NO_IMAGE_BOARD           0xC0003002 // No Images are in the board buffer
#define PCO_WARNING_SDKDLL_COC_VALCHANGE            0xC0003003 // value change when testing COC
#define PCO_WARNING_SDKDLL_COC_STR_SHORT            0xC0003004 // string buffer to short for replacement


  // The following error messages have been shifted to the common error code range:
  //
  //    PCO_ERROR_SDKDLL_NOMEMORY                   0x80003007 // No memory available
  //    PCO_ERROR_SDKDLL_BUFFERSIZE                 0x80003002 // A buffer is to small


// ------------------------------------------------------------------------------------------------------ //
// -- 4.3.1 Error codes generated by the RECORDER SDK DLL: ---------------------------------------------- //
// ------------------------------------------------------------------------------------------------------ //
#define PCO_ERROR_SDKDLL_RECORDER_RECORD_MUST_BE_OFF        0x80003021 // Record must be stopped
#define PCO_ERROR_SDKDLL_RECORDER_ACQUISITION_MUST_BE_OFF   0x80003022 // Function call not possible while running
#define PCO_ERROR_SDKDLL_RECORDER_SETTINGS_CHANGED          0x80003023 // Some camera settings have been changed outside of the recorder
#define PCO_ERROR_SDKDLL_RECORDER_NO_IMAGES_AVAILABLE       0x80003024 // No images are avaialable for readout


// ------------------------------------------------------------------------------------------------------ //
// -- 4.4. Error codes generated by the Application Software: ------------------------------------------- //
// ------------------------------------------------------------------------------------------------------ //

  // Includes the error indication bit PCO_ERROR_IS_ERROR as well as the layer PCO_ERROR_APPLICATION!
  // Device code has to be added!!!

#define PCO_ERROR_APPLICATION_PICTURETIMEOUT        0x80004001 // Error while waiting for a picture
#define PCO_ERROR_APPLICATION_SAVEFILE              0x80004002 // Error while saving file
#define PCO_ERROR_APPLICATION_FUNCTIONNOTFOUND      0x80004003 // A function inside a DLL could not be found
#define PCO_ERROR_APPLICATION_DLLNOTFOUND           0x80004004 // A DLL could not be found
#define PCO_ERROR_APPLICATION_WRONGBOARDNR          0x80004005 // The board number is out of range.
#define PCO_ERROR_APPLICATION_FUNCTIONNOTSUPPORTED  0x80004006 // The decive does not support this function.
#define PCO_ERROR_APPLICATION_WRONGRES              0x80004007 // Started Math with different resolution than reference.
#define PCO_ERROR_APPLICATION_DISKFULL              0x80004008 // Disk full.
#define PCO_ERROR_APPLICATION_SET_VALUES            0x80004009 // Error setting values to camera

#define PCO_WARNING_APPLICATION_RECORDERFULL        0xC0004001 // Memory recorder buffer is full
#define PCO_WARNING_APPLICATION_SETTINGSADAPTED     0xC0004002 // Settings have been adapted to valid values

// ------------------------------------------------------------------------------------------------------ //
// -- 4.5. Error codes generated by Camera (Camera firmware): ------------------------------------------- //
// ------------------------------------------------------------------------------------------------------ //

  // Includes the error indication bit PCO_ERROR_IS_ERROR as well as the layer PCO_ERROR_FIRMWARE!
  // Device code has to be added!!!

#define PCO_ERROR_FIRMWARE_TELETIMEOUT              0x80001001 // timeout in telegram
#define PCO_ERROR_FIRMWARE_WRONGCHECKSUM            0x80001002 // wrong checksum in telegram
#define PCO_ERROR_FIRMWARE_NOACK                    0x80001003 // no acknowledge

#define PCO_ERROR_FIRMWARE_WRONGSIZEARR             0x80001004 // wrong size in array
#define PCO_ERROR_FIRMWARE_DATAINKONSISTENT         0x80001005 // data is inkonsistent
#define PCO_ERROR_FIRMWARE_UNKNOWN_COMMAND          0x80001006 // unknown command telegram
//#define PCO_ERROR_FIRMWARE_0x80001007                 0x80001007 // free ...

#define PCO_ERROR_FIRMWARE_INITFAILED               0x80001008 // FPGA init failed
#define PCO_ERROR_FIRMWARE_CONFIGFAILED             0x80001009 // FPGA configuration failed  
#define PCO_ERROR_FIRMWARE_HIGH_TEMPERATURE         0x8000100A // device exceeds temp. range
#define PCO_ERROR_FIRMWARE_VOLTAGEOUTOFRANGE        0x8000100B // Supply voltage is out of allowed range

#define PCO_ERROR_FIRMWARE_I2CNORESPONSE            0x8000100C // no response from I2C Device
#define PCO_ERROR_FIRMWARE_CHECKSUMCODEFAILED       0x8000100D // checksum in code area is wrong
#define PCO_ERROR_FIRMWARE_ADDRESSOUTOFRANGE        0x8000100E // an address is out of range
#define PCO_ERROR_FIRMWARE_NODEVICEOPENED           0x8000100F // no device is open for update

#define PCO_ERROR_FIRMWARE_BUFFERTOSMALL            0x80001010 // the delivered buffer is to small
#define PCO_ERROR_FIRMWARE_TOMUCHDATA               0x80001011 // To much data delivered to function
#define PCO_ERROR_FIRMWARE_WRITEERROR               0x80001012 // Error while writing to camera
#define PCO_ERROR_FIRMWARE_READERROR                0x80001013 // Error while reading from camera

#define PCO_ERROR_FIRMWARE_NOTRENDERED              0x80001014 // Was not able to render graph
#define PCO_ERROR_FIRMWARE_NOHANDLEAVAILABLE        0x80001015 // The handle is not known
#define PCO_ERROR_FIRMWARE_DATAOUTOFRANGE           0x80001016 // Value is out of allowed range
#define PCO_ERROR_FIRMWARE_NOTPOSSIBLE              0x80001017 // Desired function not possible

#define PCO_ERROR_FIRMWARE_UNSUPPORTED_SDRAM        0x80001018 // SDRAM type read from SPD unknown
#define PCO_ERROR_FIRMWARE_DIFFERENT_SDRAMS         0x80001019 // different SDRAM modules mounted
#define PCO_ERROR_FIRMWARE_ONLY_ONE_SDRAM           0x8000101A // for CMOS sensor two modules needed
#define PCO_ERROR_FIRMWARE_NO_SDRAM_MOUNTED         0x8000101B // for CMOS sensor two modules needed

#define PCO_ERROR_FIRMWARE_SEGMENTS_TOO_LARGE       0x8000101C // Segment size is too large
#define PCO_ERROR_FIRMWARE_SEGMENT_OUT_OF_RANGE     0x8000101D // Segment is out of range
#define PCO_ERROR_FIRMWARE_VALUE_OUT_OF_RANGE       0x8000101E // Value is out of range
#define PCO_ERROR_FIRMWARE_IMAGE_READ_NOT_POSSIBLE  0x8000101F // Image read not possible

#define PCO_ERROR_FIRMWARE_NOT_SUPPORTED            0x80001020 // not supported by this hardware
#define PCO_ERROR_FIRMWARE_ARM_NOT_SUCCESSFUL       0x80001021 // starting record failed due not armed
#define PCO_ERROR_FIRMWARE_RECORD_MUST_BE_OFF       0x80001022 // arm is not possible while record active


#define PCO_ERROR_FIRMWARE_SEGMENT_TOO_SMALL        0x80001025 // Segment too small for image

#define PCO_ERROR_FIRMWARE_COC_BUFFER_TO_SMALL      0x80001026 // COC built is too large for internal memory
#define PCO_ERROR_FIRMWARE_COC_DATAINKONSISTENT     0x80001027 // COC has invalid data at fix position

#define PCO_ERROR_FIRMWARE_CORRECTION_DATA_INVALID  0x80001028 // Corr mode not possible due to invalid data
#define PCO_ERROR_FIRMWARE_CCDCAL_NOT_FINISHED      0x80001029 // calibration is not finished

#define PCO_ERROR_FIRMWARE_IMAGE_TRANSFER_PENDING   0x8000102A // no new image transfer can be started, because
                                                               //   the previous image transfer is pending


#define PCO_ERROR_FIRMWARE_COC_TRIGGER_INVALID      0x80001030 // Camera trigger setting invalid      
#define PCO_ERROR_FIRMWARE_COC_PIXELRATE_INVALID    0x80001031 // Camera pixel rate invalid            
#define PCO_ERROR_FIRMWARE_COC_POWERDOWN_INVALID    0x80001032 // Camera powerdown setting invalid     
#define PCO_ERROR_FIRMWARE_COC_SENSORFORMAT_INVALID 0x80001033 // Camera sensorformat invalid          
#define PCO_ERROR_FIRMWARE_COC_ROI_BINNING_INVALID  0x80001034 // Camera setting ROI to binning invalid
#define PCO_ERROR_FIRMWARE_COC_ROI_DOUBLE_INVALID   0x80001035 // Camera setting ROI to double invalid 
#define PCO_ERROR_FIRMWARE_COC_MODE_INVALID         0x80001036 // Camera mode setting invalid          
#define PCO_ERROR_FIRMWARE_COC_DELAY_INVALID        0x80001037 // Camera delay setting invalid         
#define PCO_ERROR_FIRMWARE_COC_EXPOS_INVALID        0x80001038 // Camera exposure setting invalid      
#define PCO_ERROR_FIRMWARE_COC_TIMEBASE_INVALID     0x80001039 // Camera timebase setting invalid      
#define PCO_ERROR_FIRMWARE_ACQUIRE_MODE_INVALID     0x8000103A // Acquire settings are invalid
#define PCO_ERROR_FIRMWARE_IF_SETTINGS_INVALID      0x8000103B // Interface settings are invalid
#define PCO_ERROR_FIRMWARE_ROI_NOT_SYMMETRICAL      0x8000103C // ROI is not symmetrical
#define PCO_ERROR_FIRMWARE_ROI_STEPPING             0x8000103D // ROI steps do not match
#define PCO_ERROR_FIRMWARE_ROI_SETTING              0x8000103E // ROI setting is wrong


#define PCO_ERROR_FIRMWARE_COC_PERIOD_INVALID       0x80001040 // 
#define PCO_ERROR_FIRMWARE_COC_MONITOR_INVALID      0x80001041 // 



// ------------------------------------------------------------------------------------------------------ //
// -- 4.5.a. Error codes for firmware update ------------------------------------------------------------ //
// ------------------------------------------------------------------------------------------------------ //


#define PCO_ERROR_FIRMWARE_UNKNOWN_DEVICE           0x80001050 // attempt to open an unknown device
#define PCO_ERROR_FIRMWARE_DEVICE_NOT_AVAIL         0x80001051 // device not avail. for this camera type
#define PCO_ERROR_FIRMWARE_DEVICE_IS_OPEN           0x80001052 // this or other device is already open
#define PCO_ERROR_FIRMWARE_DEVICE_NOT_OPEN          0x80001053 // no device opened for update commands

#define PCO_ERROR_FIRMWARE_NO_DEVICE_RESPONSE       0x80001054 // attempt to open device failed
#define PCO_ERROR_FIRMWARE_WRONG_DEVICE_TYPE        0x80001055 // wrong/unexpected device type
#define PCO_ERROR_FIRMWARE_ERASE_FLASH_FAILED       0x80001056 // erasing flash failed
#define PCO_ERROR_FIRMWARE_DEVICE_NOT_BLANK         0x80001057 // device is not blank when programming

#define PCO_ERROR_FIRMWARE_ADDRESS_OUT_OF_RANGE     0x80001058 // address for program or read out of range
#define PCO_ERROR_FIRMWARE_PROG_FLASH_FAILED        0x80001059 // programming flash failed
#define PCO_ERROR_FIRMWARE_PROG_EEPROM_FAILED       0x8000105A // programming eeprom failed
#define PCO_ERROR_FIRMWARE_READ_FLASH_FAILED        0x8000105B // reading flash failed

#define PCO_ERROR_FIRMWARE_READ_EEPROM_FAILED       0x8000105C // reading eeprom failed

// ------------------------------------------------------------------------------------------------------ //
// -- 4.6 Error codes for GigE -------------------------------------------------------------------------- //
// ------------------------------------------------------------------------------------------------------ //

#define PCO_ERROR_FIRMWARE_GIGE_COMMAND_IS_INVALID           0x080001080 // command is invalid
#define PCO_ERROR_FIRMWARE_GIGE_UART_NOT_OPERATIONAL         0x080001081 // camera UART not operational
#define PCO_ERROR_FIRMWARE_GIGE_ACCESS_DENIED                0x080001082 // access denied
#define PCO_ERROR_FIRMWARE_GIGE_COMMAND_UNKNOWN              0x080001083 // command unknown
#define PCO_ERROR_FIRMWARE_GIGE_COMMAND_GROUP_UNKNOWN        0x080001084 // command group unknown
#define PCO_ERROR_FIRMWARE_GIGE_INVALID_COMMAND_PARAMETERS   0x080001085 // invalid command parameters
#define PCO_ERROR_FIRMWARE_GIGE_INTERNAL_ERROR               0x080001086 // internal error
#define PCO_ERROR_FIRMWARE_GIGE_INTERFACE_BLOCKED            0x080001087 // interface blocked
#define PCO_ERROR_FIRMWARE_GIGE_INVALID_SESSION              0x080001088 // invalid session
#define PCO_ERROR_FIRMWARE_GIGE_BAD_OFFSET                   0x080001089 // bad offset
#define PCO_ERROR_FIRMWARE_GIGE_NV_WRITE_IN_PROGRESS         0x08000108a // NV write in progress
#define PCO_ERROR_FIRMWARE_GIGE_DOWNLOAD_BLOCK_LOST          0x08000108b // download block lost
#define PCO_ERROR_FIRMWARE_GIGE_DOWNLOAD_INVALID_LDR         0x08000108c // flash loader block invalid

#define PCO_ERROR_FIRMWARE_GIGE_DRIVER_IMG_PKT_LOST			 0x080001090 // Image packet lost
#define PCO_ERROR_FIRMWARE_GIGE_BANDWIDTH_CONFLICT			 0x080001091 // GiGE Data bandwidth conflict

// In case you're dealing with breakpoints during a debug session you might encounter the ACCESS_DENIED error.
// To avoid this error, please create the following registry entry:
// REG_DWORD EthernetHeartbeatTimoutMs and set it to e.g. 0xFD00 under the following key:
// HKEY_CURRENT_USER\Software\PCO\SC2 <SN>\GigE
// where SN is the serial number of your GigE equipped camera.
// This sets the GigE ethernet connection timeout to somewhat near 65 seconds. Before this time runs out you'll
// have to set your application into run mode.

// ------------------------------------------------------------------------------------------------------ //
// -- 4.7 Error codes for FLICam ------------------------------------------------------------------------ //
// ------------------------------------------------------------------------------------------------------ //

#define PCO_ERROR_FIRMWARE_FLICAM_EXT_MOD_OUT_OF_RANGE 0x80001100 // external modulation frequency out of range
#define PCO_ERROR_FIRMWARE_FLICAM_SYNC_PLL_NOT_LOCKED  0x80001101 // sync PLL not locked

// ------------------------------------------------------------------------------------------------------ //
// -- 5. Warning codes generated by Camera (Camera firmware): ------------------------------------------- //
// ------------------------------------------------------------------------------------------------------ //

  // Device code has to be added!!!

#define PCO_WARNING_FIRMWARE_FUNC_ALREADY_ON        0xC0001001 // Function is already on
#define PCO_WARNING_FIRMWARE_FUNC_ALREADY_OFF       0xC0001002 // Function is already off
#define PCO_WARNING_FIRMWARE_HIGH_TEMPERATURE       0xC0001003 // High temperature warning

#define PCO_WARNING_FIRMWARE_OFFSET_NOT_LOCKED      0xC0001004 // offset regulation is not locked


#endif
// please leave last cr lf intact!!
// =========================================== end of file ============================================== //
