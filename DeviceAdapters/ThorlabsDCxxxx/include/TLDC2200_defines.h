//==============================================================================
//
// Title:		TLDC2200_defines.h
// Purpose:		A short description of the interface.
//
// Created on:	15.10.2014 at 15:29:35 by tempinstall.
// Copyright:	. All Rights Reserved.
//
//==============================================================================

#ifndef __TLDC2200_defines_H__
#define __TLDC2200_defines_H__

#ifdef __cplusplus
    extern "C" {
#endif

/*---------------------------------------------------------------------------
  USB stuff
---------------------------------------------------------------------------*/
#define TLDC2200_VID              (0x1313)    // Thorlabs

#define TLDC2200_PID_DFU          (0x80C0)    // Thorlabs DC2200 with DFU interface enabled
#define TLDC2200_PID              (0x80C8)    // Thorlabs DC2200 w/o DFU interface

/*---------------------------------------------------------------------------
 Buffers
---------------------------------------------------------------------------*/
#define TLDC2200_BUFFER_SIZE               256      // General buffer size
#define TLDC2200_ERR_DESCR_BUFFER_SIZE     512      // Error description buffer size

/*---------------------------------------------------------------------------
 Find Pattern
---------------------------------------------------------------------------*/
#define TLDC2200_FIND_PATTERN               ("USB?*?{VI_ATTR_MANF_ID==0x1313 && VI_ATTR_MODEL_CODE==0x80C8}")
		 
#define TLDC2200_SESSION_TIMEOUT	  3000
		 
/*---------------------------------------------------------------------------
 Error/Warning Codes
   Note: The instrument returns errors within the range -512 .. +1023.
   The driver adds the value VI_INSTR_ERROR_OFFSET (0xBFFC0900). So the
   driver returns instrumetn errors in the range 0xBFFC0700 .. 0xBFFC0CFF.
---------------------------------------------------------------------------*/
// Offsets
#undef VI_INSTR_WARNING_OFFSET
#undef VI_INSTR_ERROR_OFFSET

#define VI_INSTR_WARNING_OFFSET        (0x3FFC0900L)
#define VI_INSTR_ERROR_OFFSET          (_VI_ERROR + 0x3FFC0900L)   //0xBFFC0900

// Driver Error Codes 
#define TLDC2200_ERROR_UNKNOWN_ATTRIBUTE     	(VI_INSTR_ERROR_OFFSET + 1)
#define TLDC2200_ERROR_NOT_SUPPORTED         	(VI_INSTR_ERROR_OFFSET + 2)
#define TLDC2200_ERROR_PARAMETER_OUT_OF_RANGE	(VI_INSTR_ERROR_OFFSET + 3)  
#define TLDC2200_ERROR_LIMIT_COUNT_REACHED		(VI_INSTR_ERROR_OFFSET + 4)  
#define TLDC2200_ERROR_TEST_HEAD_FAILED			(VI_INSTR_ERROR_OFFSET + 5)  

// Driver warnings
#undef VI_INSTR_WARN_OVERFLOW
#undef VI_INSTR_WARN_UNDERRUN
#undef VI_INSTR_WARN_NAN
#undef VI_INSTR_WARN_LEGACY_FW

#define VI_INSTR_WARN_OVERFLOW         (VI_INSTR_WARNING_OFFSET + 0x01L)   //0x3FFC0901
#define VI_INSTR_WARN_UNDERRUN         (VI_INSTR_WARNING_OFFSET + 0x02L)   //0x3FFC0902
#define VI_INSTR_WARN_NAN              (VI_INSTR_WARNING_OFFSET + 0x03L)   //0x3FFC0903
#define VI_INSTR_WARN_LEGACY_FW        (VI_INSTR_WARNING_OFFSET + 0x10L)   //0x3FFC0910

/*---------------------------------------------------------------------------
 Register Values
---------------------------------------------------------------------------*/
// common status bits
#define STAT_VCC_FAIL_CHANGED          0x0001
#define STAT_VCC_FAIL                  0x0002
#define STAT_OTP_CHANGED               0x0004
#define STAT_OTP                       0x0008
#define STAT_NO_LED1_CHANGED           0x0010
#define STAT_NO_LED1                   0x0020
#define STAT_LED_OPEN1_CHANGED         0x0040
#define STAT_LED_OPEN1                 0x0080
#define STAT_LED_LIMIT1_CHANGED        0x0100
#define STAT_LED_LIMIT1                0x0200
#define STAT_IFC_REFRESH_CHANGED       0x1000
		 
/*---------------------------------------------------------------------------
 Value attributes
---------------------------------------------------------------------------*/
#define TLDC2200_ATTR_SET_VAL            (0)
#define TLDC2200_ATTR_MIN_VAL            (1)
#define TLDC2200_ATTR_MAX_VAL            (2)
#define TLDC2200_ATTR_DFLT_VAL           (3)
#define TLDC2200_ATTR_HOT_VAL            (4) 

/*---------------------------------------------------------------------------
 Driver attributes
---------------------------------------------------------------------------*/
#define TLDC2200_ATTR_AUTO_ERROR_QUERY   (10)

/*---------------------------------------------------------------------------
 Protection modes
---------------------------------------------------------------------------*/
#define TLDC2200_PROTMODE_NONE         (0)   // None (protection disabled)
#define TLDC2200_PROTMODE_PAUSE        (1)   // Pause laser (output enable)
#define TLDC2200_PROTMODE_SWITCH_OFF   (2)   // Switch off laser

/*---------------------------------------------------------------------------
 LED head types
---------------------------------------------------------------------------*/
#define NO_HEAD                  0     // no head at all
#define FOUR_CHANNEL_HEAD        1     // four channel head
#define ONE_CHANNEL_HEAD         2     // single channel head
#define NOT_SUPPORTED_HEAD       253   // head with unsupported forward bias
#define UNKNOWN_HEAD             254   // head with unknown type
#define HEAD_WITHOUT_EEPROM      255   // old standard heads

/*---------------------------------------------------------------------------
 Operation modes ( compatible with DC2100 )
---------------------------------------------------------------------------*/
#define MODUS_CONST_CURRENT      	(0)
#define MODUS_PWM                	(1)
#define MODUS_EXTERNAL_CONTROL   	(2)
#define MODUS_BRIGHTNESS	        (3) 
#define MODUS_PULSE					(4) 
#define MODUS_INTERNAL_MOD			(5) 
#define MODUS_TTL					(6) 
		 
/*---------------------------------------------------------------------------
 Operation modes (Internal for the SCPI commands)
---------------------------------------------------------------------------*/
#define TLDC2200_LD_OPMODE_CC			(1)   // Constant current mode
#define TLDC2200_LD_OPMODE_CB			(2)   // Brightness mode
#define TLDC2200_LD_OPMODE_PWM		(3)   // PWM mode 
#define TLDC2200_LD_OPMODE_PULS		(4)   // Pulse mode 
#define TLDC2200_LD_OPMODE_IMOD		(5)   // Internal modulation mode
#define TLDC2200_LD_OPMODE_EMOD		(6)   // External modulation mode
#define TLDC2200_LD_OPMODE_TTL		(7)   // TTL mode
		 
/*---------------------------------------------------------------------------
 LD/PD polarity
---------------------------------------------------------------------------*/
#define TLDC2200_POL_CG             (0)   // cathode grounded
#define TLDC2200_POL_AG             (1)   // anode grounded
		 
/*---------------------------------------------------------------------------
 Temperature types
---------------------------------------------------------------------------*/
#define TLDC2200_TEMPUNIT_CEL			(0)		// returns the temperature value in celsius
#define TLDC2200_TEMPUNIT_FAR			(1)		// returns the temperature value in fahrenheit
#define TLDC2200_TEMPUNIT_KEL			(2)		// returns the temperature value in kelvin
		 
/*---------------------------------------------------------------------------
 Output Terminal
---------------------------------------------------------------------------*/
#define TLDC2200_OUTPUT_TERMINAL_10A	(1)		// uses the output 10A with 12 Pins 
#define TLDC2200_OUTPUT_TERMINAL_2A	 	(2)		// uses the output 2A with 4 Pins

/*---------------------------------------------------------------------------
 Internal Modulation Shape
---------------------------------------------------------------------------*/
#define TLDC2200_INTERNAL_MODULATION_SHAPE_SINE 		(1)
#define TLDC2200_INTERNAL_MODULATION_SHAPE_SQUARE 		(2)    
#define TLDC2200_INTERNAL_MODULATION_SHAPE_TRIANGLE 	(3)    
  

#ifdef __cplusplus
    }
#endif

#endif  /* ndef __TLDC2200_defines_H__ */
