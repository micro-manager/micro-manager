//-----------------------------------------------------------------//
// Name        | PCO_errt.h                  | Type: ( ) source    //
//-------------------------------------------|       (*) header    //
// Project     | PCO                         |       ( ) others    //
//-----------------------------------------------------------------//
// Platform    | - Embedded platforms like M16C, AVR32, PIC32 etc. //
//             | - PC with several Windows versions, Linux etc.    //
//-----------------------------------------------------------------//
// Environment | - Platform dependent                              //
//-----------------------------------------------------------------//
// Purpose     | PCO - Error text defines                          //
//-----------------------------------------------------------------//
// Author      | FRE, MBL, LWA, PCO AG                             //
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
//  00.01    | 26.06.2003 |  new file, FRE                         //
//-----------------------------------------------------------------//
//  00.03    | 14.10.2003 |  - FRE:Error text file changed (new V.)//
//           |            |    moved some errors to common area    //
//           | 23.10.2003 |  - LWA:added text strings for:         //
//           |            |  SC2_ERROR_CONNY                       //
//           |            |  PCO_ERROR_FIRMWARE_DEVICE_OPEN_FAILED // 
//-----------------------------------------------------------------//
//  00.04    | 04.11.2003 |  - LWA: completely new developed file  //
//-----------------------------------------------------------------//
//  00.05    | 12.12.2003 |  - FRE: changed GetErrorText to        //
//           |            |         PCO_GetErrorText               //
//           | 17.03.2004 |  - LWA added:                          //
//           |            |    Error text for:                     // 
//           |            |    PCO_ERROR_FIRMWARE_UNKNOWN_COMMAND  // 
//           |            |  (just addition, no new version!)      //
//           | 23.03.2004 |  - descriptions for temperature error  //
//           |            |    and temperature warning.            // 
//           | 24.03.2004 |  - FRW added:                          //
//           |            |  PCO_ERROR_SDKDLL_WRONGBUFFERNR        //
//           | 26.07.2004 |  - FRW added:                          //
//           |            |  PCO_ERROR_SDKDLL_DLLNOTFOUND          //
//           | 10.11.2004 |  - FRE added:                          //
//           |            |  PCO_ERROR_SDKDLL_BUFALREADYASSIGNED   //
//           |            |  PCO_ERROR_SDKDLL_EVENTALREADYASSIGNED //
//           | 08.03.2005 |  - FRE: Added                          //
//           |            |        PCO_ERROR_APPLICATION_WRONGRES  //
//           | 19.12.2005 | - FRE: Added                           //
//           |            |   PCO_ERROR_APPLICATION_DISKFULL       //
//           | 22.08.2006 | - FRE: Added                           //
//           |            |   PCO_ERROR_NOTINIT                    //
//           | 13.04.2007 | - FRE: Added                           //
//           |            |   PCO_ERROR_FILEDLL                    //
//           | 25.06.2008 | - FRE: Added                           //
//           |            |   PCO_ERROR_APPLICATION_SET_VALUES     //
//           | 08.12.2008 | - FRE: Added                           //
//           |            |   PCO_ERROR_SDKDLL_RECORDINGMUSTBEON   //
//           | 12.01.2009 | - FRE: Reviewed error/txt assembly     //
//           |            |   Enhanced error decoding due to       //
//           |            |   device and layer numbering           //
//-----------------------------------------------------------------//

#include "PCO_err.h"

#ifndef PCO_ERRT_H
#define PCO_ERRT_H

#if defined PCO_ERRT_H_CREATE_OBJECT


// Error messages are built with the error source + error code.
// In case of 'no error' the error source is not added.

/////////////////////////////////////////////////////////////////////
// error messages
/////////////////////////////////////////////////////////////////////



//========================================================================================================//
// Commmon error messages                                                                                 //
//========================================================================================================//

static char* PCO_ERROR_COMMON_TXT[] = 
{
  "OK.",                                           // 0x00000000  PCO_NOERROR
  "Function call with wrong parameter.",           // 0xA0000001  PCO_ERROR_WRONGVALUE
  "Handle is invalid.",                            // 0xA0000002  PCO_ERROR_INVALIDHANDLE
  "No memory available.",                          // 0xA0000003  PCO_ERROR_NOMEMORY

  "A file handle could not be opened.",            // 0xA0000004  PCO_ERROR_NOFILE
  "Timeout in function.",                          // 0xA0000005  PCO_ERROR_TIMEOUT
  "A buffer is to small.",                         // 0xA0000006  PCO_ERROR_BUFFERSIZE
  "The called module is not initialized.",         // 0xA0000007  PCO_ERROR_NOTINIT
  "Disk full.",                                    // 0xA0000008  PCO_ERROR_DISKFULL
  "",                                              // 0xA0000009
  "",                                              // 0xA000000A
  "",                                              // 0xA000000B
  "",                                              // 0xA000000C
  "",                                              // 0xA000000D
  "",                                              // 0xA000000E
  "",                                              // 0xA000000F
  "Validation of context failed",                  // 0xA0000010  PCO_ERROR_VALIDATION
  "Wrong library version",                         // 0xA0000011  PCO_ERROR_LIBRARYVERSION
  "Wrong camera version",                          // 0xA0000012  PCO_ERROR_CAMERAVERSION
  "Option is not available",                       // 0xA0000013  PCO_ERROR_NOTAVAILABLE
};

const int COMMON_MSGNUM = sizeof(PCO_ERROR_COMMON_TXT) / sizeof(PCO_ERROR_COMMON_TXT[0]);


//========================================================================================================//
// Driver error messages                                                                                  //
//========================================================================================================//
  
static char* PCO_ERROR_DRIVER_TXT[] = 
{
  "OK.",                                           // 0x00002000  PCO_NOERROR
  "Initialization failed; no camera connected.",   // 0x80002001  PCO_ERROR_DRIVER_NOTINIT
  "",                                              // 0x80002002  
  "",                                              // 0x80002003  
  "",                                              // 0x80002004  
  "Wrong driver for this OS.",                     // 0x80002005  PCO_ERROR_DRIVER_WRONGOS
  "Open driver or driver class failed.",           // 0x80002006  PCO_ERROR_DRIVER_NODRIVER
  "I/O operation failed.",                         // 0x80002007  PCO_ERROR_DRIVER_IOFAILURE

  "Error in telegram checksum.",                   // 0x80002008  PCO_ERROR_DRIVER_CHECKSUMERROR
  "Invalid Camera mode.",                          // 0x80002009  PCO_ERROR_DRIVER_INVMODE
  "",                                              // 0x8000200A  
  "Device is hold by an other process.",           // 0x8000200B  PCO_ERROR_DRIVER_DEVICEBUSY

  "Error in reading or writing data to board.",    // 0x8000200C  PCO_ERROR_DRIVER_DATAERROR
  "No function specified.",                        // 0x8000200D  PCO_ERROR_DRIVER_NOFUNCTION
  "Kernel Memory allocation in driver failed.",    // 0x8000200E  PCO_ERROR_DRIVER_KERNELMEMALLOCFAILED
  "",                                              // 0x8000200F

  "Buffer was cancelled.",                         // 0x80002010  PCO_ERROR_DRIVER_BUFFER_CANCELLED
  "Input buffer too small for this IO-call.",      // 0x80002011  PCO_ERROR_DRIVER_INBUFFER_TO_SMALL
  "Output buffer too small for this IO-call.",     // 0x80002012  PCO_ERROR_DRIVER_OUTBUFFER_TO_SMALL
  "Driver IO-Function not supported.",             // 0x80002013  PCO_ERROR_DRIVER_FUNCTION_NOT_SUPPORTED
  "Buffer failed because device power off.",       // 0x80002014  PCO_ERROR_DRIVER_BUFFER_SYSTEMOFF

  "Device is disconnected or power off.",          // 0x80002015  PCO_ERROR_DRIVER_DEVICEOFF
  "Necessary system resource not available.",      // 0x80002016  PCO_ERROR_DRIVER_RESOURCE
  "Busreset occured during system call.",          // 0x80002017  PCO_ERROR_DRIVER_BUSRESET
  "Image(s) lost in transfer",                     // 0x80002018  PCO_ERROR_DRIVER_BUFFER_LOSTIMAGE

  "", "", "", "", "", "", "",                      // 0x80002019 - 0x8000201F

  "A call to a windows-function fails.",           // 0x80002020  PCO_ERROR_DRIVER_SYSERR
  "",                                              // 0x80002021  
  "Error in reading/writing to registry.",         // 0x80002022  PCO_ERROR_DRIVER_REGERR
  "Need newer called vxd or dll.",                 // 0x80002023  PCO_ERROR_DRIVER_WRONGVERS

  "Error while reading from file.",                // 0x80002024  PCO_ERROR_DRIVER_FILE_READ_ERR
  "Error while writing to file.",                  // 0x80002025  PCO_ERROR_DRIVER_FILE_WRITE_ERR
  "Camera and dll lut do not match.",              // 0x80002026  PCO_ERROR_DRIVER_LUT_MISMATCH
  "Grabber does not support transfer format.",     // 0x80002027  PCO_ERROR_DRIVER_FORMAT_NOT_SUPPORTED
  "DMA Error not enough data transferred.",        // 0x80002028  PCO_ERROR_DRIVER_BUFFER_DMASIZE

  "version verify failed wrong typ id.",           // 0x80002029  PCO_ERROR_DRIVER_WRONG_ATMEL_FOUND
  "version verify failed wrong size.",             // 0x8000202A  PCO_ERROR_DRIVER_WRONG_ATMEL_SIZE
  "version verify failed wrong device id.",        // 0x8000202B  PCO_ERROR_DRIVER_WRONG_ATMEL_DEVICE
  "firmware is not supported from this driver.",   // 0x8000202C  PCO_ERROR_DRIVER_WRONG_BOARD
  "board firmware verify failed.",                 // 0x8000202D  PCO_ERROR_DRIVER_READ_FLASH_FAILED
  "camera head is not recognized correctly.",      // 0x8000202E  PCO_ERROR_DRIVER_HEAD_VERIFY_FAILED
  "firmware does not support camera head.",        // 0x8000202F  PCO_ERROR_DRIVER_HEAD_BOARD_MISMATCH
  "camera head is not connected.",                 // 0x80002030  PCO_ERROR_DRIVER_HEAD_LOST
  "camera head power down.",                       // 0x80002031  PCO_ERROR_DRIVER_HEAD_POWER_DOWN
  "camera started."                                // 0x80002032  PCO_ERROR_DRIVER_CAMERA_BUSY
  "camera busy."                                   // 0x80002033  PCO_ERROR_DRIVER_BUFFERS_PENDING

};

const int DRIVER_MSGNUM = sizeof(PCO_ERROR_DRIVER_TXT) / sizeof(PCO_ERROR_DRIVER_TXT[0]);


//========================================================================================================//
// Error messages for errors in SDK DLL                                                                   //
//========================================================================================================//

static char* PCO_ERROR_SDKDLL_TXT[] = 
{
  "OK.",                                           // 0x00000000  PCO_NOERROR
  "wSize of an embedded buffer is to small.",      // 0x80003001  PCO_ERROR_SDKDLL_NESTEDBUFFERSIZE
  "wSize of a buffer is to small.",                // 0x80003002  PCO_ERROR_SDKDLL_BUFFERSIZE
  "A dialog is not available.",                    // 0x80003003  PCO_ERROR_SDKDLL_DIALOGNOTAVAILABLE
  "Option is not available.",                      // 0x80003004  PCO_ERROR_SDKDLL_NOTAVAILABLE
  "A call to a windows-function fails.",           // 0x80003005  PCO_ERROR_SDKDLL_SYSERR
  "Memory area is invalid.",                       // 0x80003006  PCO_ERROR_SDKDLL_BADMEMORY
  "",                                              // 0x80003007    
  "Number of available buffers is exhausted.",     // 0x80003008  PCO_ERROR_SDKDLL_BUFCNTEXHAUSTED
  "Dialog is already open.",                       // 0x80003009  PCO_ERROR_SDKDLL_ALREADYOPENED
  "Error while destroying dialog.",                // 0x8000300A  PCO_ERROR_SDKDLL_ERRORDESTROYWND
  "A requested buffer is not available.",          // 0x8000300B  PCO_ERROR_SDKDLL_BUFFERNOTVALID
  "The buffer nr is out of range.",                // 0x8000300C  PCO_ERROR_SDKDLL_WRONGBUFFERNR
  "A DLL could not be found.",                     // 0x8000300D  PCO_ERROR_SDKDLL_DLLNOTFOUND  
  "Buffer already assigned to another buffernr.",  // 0x8000300E  PCO_ERROR_SDKDLL_BUFALREADYASSIGNED
  "Event already assigned to another buffernr.",   // 0x8000300F  PCO_ERROR_SDKDLL_EVENTALREADYASSIGNED
  "Recording must be active.",                     // 0x80003010  PCO_ERROR_SDKDLL_RECORDINGMUSTBEON
  "A DLL could not be found, due to div by zero.", // 0x80003011  PCO_ERROR_SDKDLL_DLLNOTFOUND_DIVZERO
  "Buffer is already queued.",                     // 0x80003012  PCO_ERROR_SDKDLL_BUFFERALREADYQUEUED
  "Buffer is not queued.",                         // 0x80003013  PCO_ERROR_SDKDLL_BUFFERNOTQUEUED
  "","","","","","","","","","","","",             // 0x80003014 - 0x8000301F
  "",                                              // 0x80003020
  "Recording of camera must be off for recorder",  // 0x80003021  PCO_ERROR_SDKDLL_RECORDER_RECORD_MUST_BE_OFF     
  "Acquisition in Recorder must be off",           // 0x80003022  PCO_ERROR_SDKDLL_RECORDER_ACQUISITION_MUST_BE_OFF
  "Camera settings changed outside of recorder",   // 0x80003023  PCO_ERROR_SDKDLL_RECORDER_SETTINGS_CHANGED       
  "No acquired images available in recorder",      // 0x80003024  PCO_ERROR_SDKDLL_RECORDER_NO_IMAGES_AVAILABLE    
};

const int SDKDLL_MSGNUM = sizeof(PCO_ERROR_SDKDLL_TXT) / sizeof(PCO_ERROR_SDKDLL_TXT[0]);


//========================================================================================================//
// Application error messages                                                                             //
//========================================================================================================//

static char* PCO_ERROR_APPLICATION_TXT[] = 
{
  "OK.",                                           // 0x00000000  PCO_NOERROR
  "Error while waiting for a picture.",            // 0x80004001  PCO_ERROR_APPLICATION_PICTURETIMEOUT
  "Error while saving file.",                      // 0x80004002  PCO_ERROR_APPLICATION_SAVEFILE
  "A function inside a DLL could not be found.",   // 0x80004003  PCO_ERROR_APPLICATION_FUNCTIONNOTFOUND

  "A DLL could not be found.",                     // 0x80004004  PCO_ERROR_APPLICATION_DLLNOTFOUND
  "The board number is out of range.",             // 0x80004005  PCO_ERROR_APPLICATION_WRONGBOARDNR
  "The decive does not support this function.",    // 0x80004006  PCO_ERROR_APPLICATION_FUNCTIONNOTSUPPORTED
  "Started Math with different resolution than reference.",// 0x80004007 PCO_ERROR_APPLICATION_WRONGRES
  "Disk full.",                                    // 0x80004008  PCO_ERROR_APPLICATION_DISKFULL
  "Error setting values to camera.",               // 0x80004009  PCO_ERROR_APPLICATION_SET_VALUES
};

const int APPLICATION_MSGNUM = sizeof(PCO_ERROR_APPLICATION_TXT) / sizeof(PCO_ERROR_APPLICATION_TXT[0]);



//========================================================================================================//
// Firmware error messages                                                                                //
//========================================================================================================//

static char* PCO_ERROR_FIRMWARE_TXT[] = 
{
  "OK.",                                           // 0x00000000  PCO_NOERROR
  "Timeout in telegram.",                          // 0x80001001  PCO_ERROR_FIRMWARE_TELETIMEOUT
  "Wrong checksum in telegram.",                   // 0x80001002  PCO_ERROR_FIRMWARE_WRONGCHECKSUM
  "No acknowledge.",                               // 0x80001003  PCO_ERROR_FIRMWARE_NOACK

  "Wrong size in array.",                          // 0x80001004  PCO_ERROR_FIRMWARE_WRONGSIZEARR
  "Data is inkonsistent.",                         // 0x80001005  PCO_ERROR_FIRMWARE_DATAINKONSISTENT
  "Unknown command telegram.",                     // 0x80001006  PCO_ERROR_FIRMWARE_UNKNOWN_COMMAND
  "",                                              // 0x80001007  

  "FPGA init failed.",                             // 0x80001008  PCO_ERROR_FIRMWARE_INITFAILED
  "FPGA configuration failed.",                    // 0x80001009  PCO_ERROR_FIRMWARE_CONFIGFAILED
  "High temperature.",                             // 0x8000100A  PCO_ERROR_FIRMWARE_HIGH_TEMPERATURE
  "Supply voltage out of range.",                  // 0x8000100B  PCO_ERROR_FIRMWARE_VOLTAGEOUTOFRANGE

  "No response from I2C Device.",                  // 0x8000100C  PCO_ERROR_FIRMWARE_I2CNORESPONSE
  "Checksum in code area is wrong.",               // 0x8000100D  PCO_ERROR_FIRMWARE_CHECKSUMCODEFAILED
  "An address is out of range.",                   // 0x8000100E  PCO_ERROR_FIRMWARE_ADDRESSOUTOFRANGE
  "No device is open for update.",                 // 0x8000100F  PCO_ERROR_FIRMWARE_NODEVICEOPENED

  "The delivered buffer is to small.",             // 0x80001010  PCO_ERROR_FIRMWARE_BUFFERTOSMALL
  "To much data delivered to function.",           // 0x80001011  PCO_ERROR_FIRMWARE_TOMUCHDATA
  "Error while writing to camera.",                // 0x80001012  PCO_ERROR_FIRMWARE_WRITEERROR
  "Error while reading from camera.",              // 0x80001013  PCO_ERROR_FIRMWARE_READERROR

  "Was not able to render graph.",                 // 0x80001014  PCO_ERROR_FIRMWARE_NOTRENDERED
  "The handle is not known.",                      // 0x80001015  PCO_ERROR_FIRMWARE_NOHANDLEAVAILABLE
  "Value is out of allowed range.",                // 0x80001016  PCO_ERROR_FIRMWARE_DATAOUTOFRANGE
  "Desired function not possible.",                // 0x80001017  PCO_ERROR_FIRMWARE_NOTPOSSIBLE

  "SDRAM type read from SPD unknown.",             // 0x80001018  PCO_ERROR_FIRMWARE_UNSUPPORTED_SDRAM
  "Different SDRAM modules mounted.",              // 0x80001019  PCO_ERROR_FIRMWARE_DIFFERENT_SDRAMS
  "For CMOS sensor two modules needed.",           // 0x8000101A  PCO_ERROR_FIRMWARE_ONLY_ONE_SDRAM
  "No SDRAM mounted.",                             // 0x8000101B  PCO_ERROR_FIRMWARE_NO_SDRAM_MOUNTED

  "Segment size is too large.",                    // 0x8000101C  PCO_ERROR_FIRMWARE_SEGMENTS_TOO_LARGE
  "Segment is out of range.",                      // 0x8000101D  PCO_ERROR_FIRMWARE_SEGMENT_OUT_OF_RANGE
  "Value is out of range.",                        // 0x8000101E  PCO_ERROR_FIRMWARE_VALUE_OUT_OF_RANGE
  "Image read not possible.",                      // 0x8000101F  PCO_ERROR_FIRMWARE_IMAGE_READ_NOT_POSSIBLE

  "Command/data not supported by this hardware.",  // 0x80001020  PCO_ERROR_FIRMWARE_NOT_SUPPORTED
  "Starting record failed due not armed.",         // 0x80001021  PCO_ERROR_FIRMWARE_ARM_NOT_SUCCESSFUL
  "Arm is not possible while record active.",      // 0x80001022  PCO_ERROR_FIRMWARE_RECORD_MUST_BE_OFF
  "",                                              // 0x80001023  

  "",                                              // 0x80001024  
  "Segment too small for image.",                  // 0x80001025  PCO_ERROR_FIRMWARE_SEGMENT_TOO_SMALL
  "COC built is too large for internal memory.",   // 0x80001026  PCO_ERROR_FIRMWARE_COC_BUFFER_TO_SMALL
  "COC has invalid data at fix position.",         // 0x80001027  PCO_ERROR_FIRMWARE_COC_DATAINKONSISTENT

  "Correction data not valid.",                    // 0x80001028  PCO_ERROR_FIRMWARE_CORRECTION_DATA_INVALID
  "CCD calibration not finished.",                 // 0x80001029  PCO_ERROR_FIRMWARE_CCDCAL_NOT_FINISHED
  "Image Transfer pending",                        // 0x8000102A  
  "",                                              // 0x8000102B  

  "",                                              // 0x8000102C  
  "",                                              // 0x8000102D  
  "",                                              // 0x8000102E  
  "",                                              // 0x8000102F  

  "Camera trigger setting invalid.",               // 0x80001030  PCO_ERROR_FIRMWARE_COC_TRIGGER_INVALID
  "Camera pixel rate invalid.",                    // 0x80001031  PCO_ERROR_FIRMWARE_COC_PIXELRATE_INVALID
  "Camera powerdown setting invalid.",             // 0x80001032  PCO_ERROR_FIRMWARE_COC_POWERDOWN_INVALID
  "Camera sensorformat invalid.",                  // 0x80001033  PCO_ERROR_FIRMWARE_COC_SENSORFORMAT_INVALID
  "Camera setting ROI to binning invalid.",        // 0x80001034  PCO_ERROR_FIRMWARE_COC_ROI_BINNING_INVALID
  "Camera setting ROI to double invalid.",         // 0x80001035  PCO_ERROR_FIRMWARE_COC_ROI_DOUBLE_INVALID
  "Camera mode setting invalid.",                  // 0x80001036  PCO_ERROR_FIRMWARE_COC_MODE_INVALID
  "Camera delay setting invalid.",                 // 0x80001037  PCO_ERROR_FIRMWARE_COC_DELAY_INVALID
  "Camera exposure setting invalid.",              // 0x80001038  PCO_ERROR_FIRMWARE_COC_EXPOS_INVALID
  "Camera timebase setting invalid.",              // 0x80001039  PCO_ERROR_FIRMWARE_COC_TIMEBASE_INVALID
  "Acquire mode setting invalid.",                 // 0x8000103A  PCO_ERROR_FIRMWARE_ACQUIRE_MODE_INVALID
  "Interface parameter setting invalid.",          // 0x8000103B  PCO_ERROR_FIRMWARE_IF_SETTINGS_INVALID
  "ROI setting is wrong",                          // 0x8000103C  PCO_ERROR_FIRMWARE_ROI_NOT_SYMMETRICAL
  "ROI steps do not match",                        // 0x8000103D  PCO_ERROR_FIRMWARE_ROI_STEPPING
  "ROI setting is wrong",                          // 0x8000103E  PCO_ERROR_FIRMWARE_ROI_SETTING
  "",                                              // 0x8000103F  

  "COC modulate period time invalid.",             // 0x80001040 PCO_ERROR_FIRMWARE_COC_PERIOD_INVALID
  "COC modulate monitor time invalid",             // 0x80001041 PCO_ERROR_FIRMWARE_COC_MONITOR_INVALID
  "", "", "", "", "", "",                          // 0x80001042 - 0x80001047
  "", "", "", "", "", "", "", "",                  // 0x80001048 - 0x8000104F

  "Attempt to open unknown device for update.",    // 0x80001050  PCO_ERROR_FIRMWARE_UNKNOWN_DEVICE     
  "Attempt to open device not available.",         // 0x80001051  PCO_ERROR_FIRMWARE_DEVICE_NOT_AVAIL   
  "This or other device is already open.",         // 0x80001052  PCO_ERROR_FIRMWARE_DEVICE_IS_OPEN
  "No device opened for update command.",          // 0x80001053  PCO_ERROR_FIRMWARE_DEVICE_NOT_OPEN

  "Device to open does not respond.",              // 0x80001054  PCO_ERROR_FIRMWARE_NO_DEVICE_RESPONSE 
  "Device to open is wrong device type.",          // 0x80001055  PCO_ERROR_FIRMWARE_WRONG_DEVICE_TYPE  
  "Erasing device flash/firmware failed.",         // 0x80001056  PCO_ERROR_FIRMWARE_ERASE_FLASH_FAILED 
  "Device to program is not blank.",               // 0x80001057  PCO_ERROR_FIRMWARE_DEVICE_NOT_BLANK   

  "Device address is out of range.",               // 0x80001058  PCO_ERROR_FIRMWARE_ADDRESS_OUT_OF_RANGE
  "Programming device flash/firmware failed.",     // 0x80001059  PCO_ERROR_FIRMWARE_PROG_FLASH_FAILED  
  "Programming device EEPROM failed.",             // 0x8000105A  PCO_ERROR_FIRMWARE_PROG_EEPROM_FAILED 
  "Reading device flash/firmware failed.",         // 0x8000105B  PCO_ERROR_FIRMWARE_READ_FLASH_FAILED  

  "Reading device EEPROM failed.",                 // 0x8000105C  PCO_ERROR_FIRMWARE_READ_EEPROM_FAILED 

  "", "", "",                                      // 0x8000105D - 0x8000105F
  "", "", "", "", "", "", "", "",                  // 0x80001060 - 0x80001067
  "", "", "", "", "", "", "", "",                  // 0x80001068 - 0x8000106F

  "", "", "", "", "", "", "", "",                  // 0x80001070 - 0x80001077
  "", "", "", "", "", "", "", "",                  // 0x80001078 - 0x8000107F

  "Command is invalid.",                           // 0x80001080  PCO_ERROR_FIRMWARE_GIGE_COMMAND_IS_INVALID
  "Camera UART not operational.",                  // 0x80001081  PCO_ERROR_FIRMWARE_GIGE_UART_NOT_OPERATIONAL         
  "Access denied. Debugging? See pco_err.h!",      // 0x80001082  PCO_ERROR_FIRMWARE_GIGE_ACCESS_DENIED                
  "Command unknown.",                              // 0x80001083  PCO_ERROR_FIRMWARE_GIGE_COMMAND_UNKNOWN              
  "Command group unknown.",                        // 0x80001084  PCO_ERROR_FIRMWARE_GIGE_COMMAND_GROUP_UNKNOWN        
  "Invalid command parameters.",                   // 0x80001085  PCO_ERROR_FIRMWARE_GIGE_INVALID_COMMAND_PARAMETERS   
  "Internal error.",                               // 0x80001086  PCO_ERROR_FIRMWARE_GIGE_INTERNAL_ERROR               
  "Interface blocked.",                            // 0x80001087  PCO_ERROR_FIRMWARE_GIGE_INTERFACE_BLOCKED            
  "Invalid session.",                              // 0x80001088  PCO_ERROR_FIRMWARE_GIGE_INVALID_SESSION              
  "Bad offset.",                                   // 0x80001089  PCO_ERROR_FIRMWARE_GIGE_BAD_OFFSET                   
  "NV write in progress.",                         // 0x8000108a  PCO_ERROR_FIRMWARE_GIGE_NV_WRITE_IN_PROGRESS         
  "Download block lost.",                          // 0x8000108b  PCO_ERROR_FIRMWARE_GIGE_DOWNLOAD_BLOCK_LOST          
  "Flash loader block invalid.",                   // 0x8000108c  PCO_ERROR_FIRMWARE_GIGE_DOWNLOAD_INVALID_LDR         
  "", "", "",                                      // 0x8000108d - 0x8000108F
  "Image packet lost.",                            // 0x80001090 PCO_ERROR_FIRMWARE_GIGE_DRIVER_IMG_PKT_LOST
  "GiGE Data bandwidth conflict.",                 // 0x80001091 PCO_ERROR_FIRMWARE_GIGE_BANDWIDTH_CONFLICT
  "", "", "", "", "",                              // 0x80001092 - 0x80001096
  "", "", "", "", "",                              // 0x80001097 - 0x8000109B
  "", "", "", "",                                  // 0x8000109C - 0x8000109F
  "External modulation frequency out of range.",   // 0x80001100 PCO_ERROR_FIRMWARE_FLICAM_EXT_MOD_OUT_OF_RANGE
  "Sync PLL not locked."                           // 0x80001101 PCO_ERROR_FIRMWARE_FLICAM_SYNC_PLL_NOT_LOCKED
};

const int FIRMWARE_MSGNUM = sizeof(PCO_ERROR_FIRMWARE_TXT) / sizeof(PCO_ERROR_FIRMWARE_TXT[0]);


static char ERROR_CODE_OUTOFRANGE_TXT[] = "Error code out of range.";

/////////////////////////////////////////////////////////////////////
// end: error messages
/////////////////////////////////////////////////////////////////////


/////////////////////////////////////////////////////////////////////
// warnings:
/////////////////////////////////////////////////////////////////////

static char* PCO_ERROR_FWWARNING_TXT[] = 
{
  "OK.",
  "Function is already on.",                       // 0xC0001001 PCO_WARNING_FIRMWARE_FUNCALON     
  "Function is already off.",                      // 0xC0001002 PCO_WARNING_FIRMWARE_FUNCALOFF     
  "High temperature.",                             // 0xC0001003 PCO_WARNING_FIRMWARE_HIGH_TEMPERATURE
  "Offset regulation is not locked.",              // 0xC0001004 PCO_WARNING_FIRMWARE_OFFSET_NOT_LOCKED       
};

const int FWWARNING_MSGNUM = sizeof(PCO_ERROR_FWWARNING_TXT) / sizeof(PCO_ERROR_FWWARNING_TXT[0]);

static char* PCO_ERROR_DRIVERWARNING_TXT[] = 
{
  "OK.",                                           // 0x00000000  PCO_NOERROR
};

const int DRIVERWARNING_MSGNUM = sizeof(PCO_ERROR_DRIVERWARNING_TXT) / sizeof(PCO_ERROR_DRIVERWARNING_TXT[0]);


static char* PCO_ERROR_SDKDLLWARNING_TXT[] = 
{
  "OK.",                                           // 0x00000000  PCO_NOERROR
  "Buffers are still allocated",                   // 0xC0003001 PCO_WARNING_SDKDLL_BUFFER_STILL_ALLOKATED
  "No Images are in the board buffer",             // 0xC0003002 PCO_WARNING_SDKDLL_NO_IMAGE_BOARD
  "value change when testing COC",                 // 0xC0003003 PCO_WARNING_SDKDLL_COC_VALCHANGE
  "string buffer to short for replacement"         // 0xC0003004 PCO_WARNING_SDKDLL_COC_STR_SHORT
};

const int SDKDLLWARNING_MSGNUM = sizeof(PCO_ERROR_SDKDLLWARNING_TXT) / sizeof(PCO_ERROR_SDKDLLWARNING_TXT[0]);

static char* PCO_ERROR_APPLICATIONWARNING_TXT[] = 
{
  "OK.",                                           // 0x00000000  PCO_NOERROR
  "Memory recorder buffer is full.",               // 0xC0004001  PCO_WARNING_APPLICATION_RECORDERFULL
  "Settings have been adapted to valid values.",   // 0xC0004002  PCO_WARNING_APPLICATION_SETTINGSADAPTED
};

const int APPLICATIONWARNING_MSGNUM = sizeof(PCO_ERROR_APPLICATIONWARNING_TXT) / sizeof(PCO_ERROR_APPLICATIONWARNING_TXT[0]);


/////////////////////////////////////////////////////////////////////
// end: warnings
/////////////////////////////////////////////////////////////////////

#if defined _MSC_VER
#if     _MSC_VER < 1400
int sprintf_s(char* buf, int dwlen, const char* cp, ...)
{
  va_list arglist;

  va_start(arglist, cp);
  return _vsnprintf(buf, dwlen, cp, arglist);
}
#endif
#endif

void PCO_GetErrorText(DWORD dwerr, char* pbuf, DWORD dwlen)
{
  char* layertxt;
  char* errortxt;
  char* devicetxt;
  char  msg[200];
  int   index;
  DWORD device;
  DWORD layer;

  if (dwlen < 40)
    return;

  index = dwerr & PCO_ERROR_CODE_MASK;

  if ((dwerr == PCO_NOERROR) || (index == 0))
  {
    sprintf_s(pbuf, dwlen, "OK.");
    return;
  }


  // -- evaluate device information within complete error code -- //
  // ------------------------------------------------------------ //

  device = dwerr & PCO_ERROR_DEVICE_MASK;
  layer = dwerr & PCO_ERROR_LAYER_MASK;
    
  // -- evaluate layer information within complete error code --- //
  // ------------------------------------------------------------ //

  switch(dwerr & PCO_ERROR_LAYER_MASK)   // evaluate layer
  {
    case PCO_ERROR_FIRMWARE:
    {
      layertxt =  "Firmware";
      switch(device)  
      {
        case SC2_ERROR_POWER_CPLD:   devicetxt = "SC2 Power CPLD";   break;
        case SC2_ERROR_HEAD_UP:      devicetxt = "SC2 Head uP";      break;
        case SC2_ERROR_MAIN_UP:      devicetxt = "SC2 Main uP";      break;
        case SC2_ERROR_FWIRE_UP:     devicetxt = "SC2 Firewire uP";  break;
        case SC2_ERROR_MAIN_FPGA:    devicetxt = "SC2 Main FPGA";    break;
        case SC2_ERROR_HEAD_FPGA:    devicetxt = "SC2 Head FPGA";    break;
        case SC2_ERROR_MAIN_BOARD:   devicetxt = "SC2 Main board";   break;
        case SC2_ERROR_HEAD_CPLD:    devicetxt = "SC2 Head CPLD";    break;
        case SC2_ERROR_SENSOR:       devicetxt = "SC2 Image sensor"; break;
        case SC2_ERROR_POWER:        devicetxt = "SC2 Power Unit";   break;
        case SC2_ERROR_GIGE:         devicetxt = "SC2 GigE board";   break;
        case SC2_ERROR_USB:          devicetxt = "SC2 GigE/USB board"; break;
        case SC2_ERROR_BOOT_FPGA:    devicetxt = "BOOT FPGA";        break;
        case SC2_ERROR_BOOT_UP:      devicetxt = "BOOT uP";          break;
        default: devicetxt = "Unknown device";
      }
      break;
    }
    case PCO_ERROR_DRIVER:
    {
      layertxt =  "Driver";
      switch(device)
      {
        case PCI540_ERROR_DRIVER:          devicetxt = "Pixelfly driver";    break;

        case SC2_ERROR_DRIVER:             devicetxt = "pco.camera driver";    break;
        case PCI525_ERROR_DRIVER:          devicetxt = "Sensicam driver";    break;

        case PCO_ERROR_DRIVER_FIREWIRE:    devicetxt = "Firewire driver";    break;
        case PCO_ERROR_DRIVER_USB:         devicetxt = "USB 2.0 driver";    break;
        case PCO_ERROR_DRIVER_GIGE:        devicetxt = "GigE driver";    break;
        case PCO_ERROR_DRIVER_CAMERALINK:  devicetxt = "CameraLink driver";    break;
        case PCO_ERROR_DRIVER_USB3:        devicetxt = "USB 3.0 driver";    break;
        case PCO_ERROR_DRIVER_WLAN:        devicetxt = "WLan driver";    break;
        default: devicetxt = "Unknown device";
      }
      break;
    }
    case PCO_ERROR_SDKDLL:
    {
      layertxt =  "SDK DLL";
      switch(device)
      {
        case  PCO_ERROR_PCO_SDKDLL:    devicetxt = "camera sdk dll";    break;
        case  PCO_ERROR_CONVERTDLL:    devicetxt = "convert dll";    break;
        case  PCO_ERROR_FILEDLL:       devicetxt = "file dll";    break;
        case  PCO_ERROR_JAVANATIVEDLL: devicetxt = "java native dll";    break;
        case  PCO_ERROR_PROGLIB:       devicetxt = "programmer library";   break;
        default: devicetxt = "Unknown device";
      }
      break;
    }
    case PCO_ERROR_APPLICATION:
    {
      layertxt =  "Application";
      switch(device)
      {
        case PCO_ERROR_CAMWARE:    devicetxt = "CamWare";    break;
        case PCO_ERROR_PROGRAMMER: devicetxt = "Programmer";    break;
        case PCO_ERROR_SDKAPPLICATION: devicetxt = "SDK Application";    break;
        default: devicetxt = "Unknown device";
      }

      break;
    }
    default:
    {
      layertxt =  "Undefined layer";
      devicetxt = "Unknown device";
    }
  }


  // -- evaluate error information within complete error code --- //
  // ------------------------------------------------------------ //

  if (dwerr & PCO_ERROR_IS_COMMON)
  {
    if (index < COMMON_MSGNUM)
      errortxt = PCO_ERROR_COMMON_TXT[index];
    else
      errortxt = ERROR_CODE_OUTOFRANGE_TXT;
  }
  else
  {     
    switch(dwerr & PCO_ERROR_LAYER_MASK)   // evaluate layer
    {
      case PCO_ERROR_FIRMWARE:

        if (dwerr & PCO_ERROR_IS_WARNING)
        {
          if (index < FWWARNING_MSGNUM)
            errortxt = PCO_ERROR_FWWARNING_TXT[index];
          else
            errortxt = ERROR_CODE_OUTOFRANGE_TXT;
        }
        else
        {
          if (index < FIRMWARE_MSGNUM)
            errortxt = PCO_ERROR_FIRMWARE_TXT[index];
          else
            errortxt = ERROR_CODE_OUTOFRANGE_TXT;
        }
        break;


      case PCO_ERROR_DRIVER:

        if (dwerr & PCO_ERROR_IS_WARNING)
        {
          if (index < DRIVERWARNING_MSGNUM)
            errortxt = PCO_ERROR_DRIVERWARNING_TXT[index];
          else
            errortxt = ERROR_CODE_OUTOFRANGE_TXT;
        }
        else
        {
          if (index < DRIVER_MSGNUM)
            errortxt = PCO_ERROR_DRIVER_TXT[index];
          else
            errortxt = ERROR_CODE_OUTOFRANGE_TXT;
        }
        break;


      case PCO_ERROR_SDKDLL:

        if (dwerr & PCO_ERROR_IS_WARNING)
        {
          if (index < SDKDLLWARNING_MSGNUM)
            errortxt = PCO_ERROR_SDKDLLWARNING_TXT[index];
          else
            errortxt = ERROR_CODE_OUTOFRANGE_TXT;
        }
        else
        {
          if (index < SDKDLL_MSGNUM)
            errortxt = PCO_ERROR_SDKDLL_TXT[index];
          else
            errortxt = ERROR_CODE_OUTOFRANGE_TXT;
        }
        break;


      case PCO_ERROR_APPLICATION:

        if (dwerr & PCO_ERROR_IS_WARNING)
        {
          if (index < APPLICATIONWARNING_MSGNUM)
            errortxt = PCO_ERROR_APPLICATIONWARNING_TXT[index];
          else
            errortxt = ERROR_CODE_OUTOFRANGE_TXT;
        }
        else
        {
          if (index < APPLICATION_MSGNUM)
            errortxt = PCO_ERROR_APPLICATION_TXT[index];
          else
            errortxt = ERROR_CODE_OUTOFRANGE_TXT;
        }
        break;


      default:

        errortxt = "No error text available!";
        break;
    }
  }

  if(dwerr & PCO_ERROR_IS_WARNING)
    sprintf_s(msg, 200, "%s warning %x at device '%s': %s",
            layertxt, dwerr, devicetxt, errortxt);
  else
    sprintf_s(msg, 200, "%s error %x at device '%s': %s",
            layertxt, dwerr, devicetxt, errortxt);

  if (dwlen <= strlen(msg))    // 1 byte more for zero at end of string
  {
    sprintf_s(pbuf, dwlen, "Error buffer too short. err: %x", dwerr);
    return;
  }
  sprintf_s(pbuf, dwlen, "%s", msg);

}

#else // PCO_ERRT_H_CREATE_OBJECT

// Please define 'PCO_ERRT_H_CREATE_OBJECT' in your files once,
// to avoid a linker error-message if you call GetErrorText!

void PCO_GetErrorText(DWORD dwerr, char* pbuf, DWORD dwlen);

#endif//PCO_ERRT_H_CREATE_OBJECT
#endif//PCO_ERRT_H
// please leave last cr lf intact!!
// =========================================== end of file ============================================== //
