//==========================================================================
//
//  File:       TOOLBERR.H
//  Purpose:    Error codes for the Carl Zeiss micro toolbox.
//
//  Copyright:  © CARL ZEISS 1994- 2000
//
//==========================================================================

#ifndef _TOOLBERR_H_
#define _TOOLBERR_H_

// preprocessor definitions
// no error occurred:
#ifndef _E_NO_ERROR
  #define _E_NO_ERROR        (0)
#endif  


// constants to check bits, returned by the [GetServoStat] function
//      Bit 0 == 1 - Hardware busy
//      Bit 1 == 1 - Hardware not ready for communication
//      Bit 2 == 1 - Output buffer of PC not empty
//      Bit 3 == 1 - Output overflow
//      Bit 4 == 1 - Communication error
//      Bit 5 == 1 - Syntax error
//      Bit 6 == 1 - Error during function
//      Bit 7 == 1 - Hardware is not available
#define _E_HARDW_BUSY           (1)
#define _E_HARDW_NOT_READY      (2)
#define _E_OUTBUFF_NOT_EMPTY    (4)
#define _E_OUTPUT_OVERFLOW      (8)
#define _E_COMMUNICATION       (16)
#define _E_SYNTAX_ERROR        (32)
#define _E_FUNC_ERROR          (64)
#define _E_HARDW_NOT_AVAIL    (128)


//==========================================================================
// errors 100 - 199 for general toolbox error codes
// by MBu
#define _E_OUT_OF_RANGE       (100)  // any item is out of range
#define _E_INVALID_RESPONSE   (101)  // PC address during a CAN response was wrong
#define _E_PROBLEMS           (102)  // nothing special

//==========================================================================
// errors 200 - 300 only for V24.DLL
// by MBu
// error numbers returned by the send/receive functions
#define _E_V24_CLOSED         (200)   // Selected port is not open

// Attention: Maximum value of [_E_V24_TIMEOUT] = 255 !!!
#define _E_V24_TIMEOUT        (201)   // Timeout on sending or receiving characters

// error numbers returned by a call to OpenComm
#define _E_V24_OP_BADID       (202)   // The device identifier is invalid
#define _E_V24_OP_BAUDRATE    (203)   // The device's baud rate is unsupported
#define _E_V24_OP_INV_BYSZ    (204)   // The specified byte size is invalid
#define _E_V24_OP_DEFAULT     (205)   // The default parameters are in error
#define _E_V24_OP_HARDWARE    (206)   // The hardware is not available
#define _E_V24_OP_NO_MEM      (207)   // The function cannot allocate the queues
#define _E_V24_OP_NOT_OPEN    (208)   // The device is not open
#define _E_V24_OP_OPEN        (209)   // The device is already open
#define _E_V24_BUILD_DCB      (210)   // error building the dcb
#define _E_V24_SET_COMM_STATE (211)   // error setting the actual comm state
#define _E_V24_EN_COMM_NOT    (212)   // error enabling the comm notification
#define _E_V24_GET_INFO       (213)   // error getting the device control block
#define _E_V24_INVALID_QUEUE  (214)   // an invalid queue has been specified


//==========================================================================
// errors 1200 - 1299 only for REVOLVER.DLL
// by MBu
#define _E_UNABLE_TO_LOCK     (1200)
#define _E_REV_NOT_MOTORIZED  (1201)


//==========================================================================
// errors 1300 - 1399 only for LINEAR.DLL
// by RnB
#define _E_FROM_LINEARDLL     (1300)  // nothing special
#define _E_INPUT_NULL         (1301)  // function input is null pointer
#define _E_NO_MAPVALUE        (1302)  // no map value found


//==========================================================================
// errors 1400 - 1499 only for PHOTO.DLL
// by MBu
#define _E_WRONG_TEXT_LOCATION    (1401)  // wrong location specified for text line
#define _E_ITEM_NBR_OUT_OF_RANGE  (1402)  // wrong predefined text line specified
#define _E_CASSETTE_NOT_AVAILABLE (1403)  // the desired film cassette is not connected

#endif // #ifndef _TOOLBERR_H_
