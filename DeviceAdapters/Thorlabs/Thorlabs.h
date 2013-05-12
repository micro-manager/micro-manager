///////////////////////////////////////////////////////////////////////////////
// FILE:          Thorlabs.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs device adapters: BBD102 Controller
//
// COPYRIGHT:     Thorlabs Inc, 2011
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 2011
//                http://nenad.amodaj.com
//

#ifndef _THORLABS_H_
#define _THORLABS_H_

#include <MMDevice.h>
#include <DeviceBase.h>
#include <string>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_UNRECOGNIZED_ANSWER      10009
#define ERR_UNSPECIFIED_ERROR        10010
#define ERR_HOME_REQUIRED            10011
#define ERR_INVALID_PACKET_LENGTH    10012
#define ERR_RESPONSE_TIMEOUT         10013
#define ERR_BUSY                     10014
#define ERR_STEPS_OUT_OF_RANGE       10015
#define ERR_STAGE_NOT_ZEROED         10016
#define ERR_INVALID_POSITION         10017
#define ERR_INVALID_NUMBER_OF_POS    10018
#define ERR_MOVE_FAILED              10019
#define ERR_UNRECOGNIZED_DEVICE      10020

// integrated filter wheel parameters
#define P_MOT_SB_CWHARDLIMIT        0x00000001        // CW hardware limit switch (0 - no contact, 1 - contact).
#define P_MOT_SB_CWHARDLIMIT_MASK   0xFFFFFFFE
#define P_MOT_SB_CCWHARDLIMIT       0x00000002        // CCW hardware limit switch (0 - no contact, 1 - contact).
#define P_MOT_SB_CCWHARDLIMIT_MASK  0xFFFFFFFD
#define P_MOT_SB_CWSOFTLIMIT        0x00000004        // CW software limit switch (0 - no contact, 1 - contact).
#define P_MOT_SB_CWSOFTLIMIT_MASK   0xFFFFFFFB
#define P_MOT_SB_CCWSOFTLIMIT       0x00000008        // CCW software limit switch (0 - no contact, 1 - contact).
#define P_MOT_SB_CCWSOFTLIMIT_MASK  0xFFFFFFF7
#define P_MOT_SB_INMOTIONCW         0x00000010        // Moving clockwise (1 - moving, 0 - stationary).
#define P_MOT_SB_INMOTIONCW_MASK    0xFFFFFFEF
#define P_MOT_SB_INMOTIONCCW        0x00000020        // Moving counterclockwise (1 - moving, 0 - stationary).
#define P_MOT_SB_INMOTIONCCW_MASK   0xFFFFFFDF
#define P_MOT_SB_JOGGINGCW          0x00000040        // Jogging clockwise (1 - moving, 0 - stationary).
#define P_MOT_SB_JOGGINGCW_MASK     0xFFFFFFBF
#define P_MOT_SB_JOGGINGCCW         0x00000080        // Jogging counterclockwise (1 - moving, 0 - stationary).
#define P_MOT_SB_JOGGINGCCW_MASK    0xFFFFFF7F
#define P_MOT_SB_CONNECTED          0x00000100        // Motor connected (1 - connected, 0 - not connected).
#define P_MOT_SB_CONNECTED_MASK     0xFFFFFEFF
#define P_MOT_SB_HOMING             0x00000200        // Motor homing (1 - homing, 0 - not homing).
#define P_MOT_SB_HOMING_MASK        0xFFFFFDFF
#define P_MOT_SB_HOMED              0x00000400        // Motor homed (1 - homed, 0 - not homed).
#define P_MOT_SB_HOMED_MASK         0xFFFFFBFF
#define P_MOT_SB_INTERLOCK          0x00001000        // Interlock state (1 - enabled, 0 - disabled)
#define P_MOT_SB_INTERLOCKMASK      0xFFFFEFFF
#define P_MOT_SB_POSITION_ERR       0x00010000         // Position error bit indicated
#define P_MOT_SB_POSITION_ERRMASK   0xFFFEFFFF         // Position error bit mask

///////////////////////////////////////////////////////////////////////////////
// fixed stage parameters
///////////////////////////////////////////////////////////////////////////////
const int cmdLength = 6;               // command block length

//////////////////////////////////////////////////////////////////////////////
// global utility
int ClearPort(MM::Device& device, MM::Core& core, std::string port);

#define DEVICE_HOSTPC     0x01
#define DEVICE_CONTROLLER 0x11
#define DEVICE_CHANNEL0   0x21

// convenience macro for using packed structures
#if   defined(__GNUC__)
# define PACK( __Declaration__ ) __Declaration__ __attribute__((__packed__))
#elif defined(_MSC_VER)
# define PACK( __Declaration__ ) __pragma( pack(push, 1) ) __Declaration__ __pragma( pack(pop) )
#else
# error "Do not know how to pack structures with this compiler."
#endif

PACK(
struct _ThorlabsCommand {
  short command      :16;
  char  param1       :8;
  char  param2       :8;
  char  destination  :7;
  char  data_follows :1;
  char  source       :8;
});
typedef struct _ThorlabsCommand ThorlabsCommand;


#endif //_THORLABS_H_
