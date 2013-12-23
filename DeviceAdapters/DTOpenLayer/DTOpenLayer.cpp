///////////////////////////////////////////////////////////////////////////////
// FILE:          DTOpenLayer.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Data Translation IO board adapter 
// COPYRIGHT:     University of California, San Francisco, 2006
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 03/15/2006
//
// CVS:           $Id$
//

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "DTOpenLayer.h"
#include "../../MMDevice/ModuleInterface.h"

const char* g_DeviceNameDTOLSwitch = "DTOL-Switch";
const char* g_DeviceNameDTOLShutter = "DTOL-Shutter";
const char* g_DeviceNameDTOLDA0 = "DTOL-DAC-0";
const char* g_DeviceNameDTOLDA1 = "DTOL-DAC-1";
const char* g_DeviceNameDTOLDA2 = "DTOL-DAC-2";
const char* g_DeviceNameDTOLDA3 = "DTOL-DAC-3";

const char* g_volts = "Volts";
const char* g_PropertyMin = "MinV";
const char* g_PropertyMax = "MaxV";

// Global state of the DTOL switch to enable simulation of the shutter device.
// The virtual shutter device uses this global variable to restore state of the switch
unsigned g_switchState = 0;
unsigned g_shutterState = 0;

using namespace std;


#include "../../../3rdparty/DataTranslation/SDK/include/olmem.h"
#include "../../../3rdparty/DataTranslation/SDK/include/olerrors.h"
#include "../../../3rdparty/DataTranslation/SDK/include/oldaapi.h"

/* simple structure used with board */

typedef struct tag_board {
   HDEV hdrvr;         /* device handle            */
   HDASS hdass_do;        /* sub system handle digital out*/
   HDASS hdass_da;        /* sub system handle DAC */
   ECODE status;       /* board error status       */
   HBUF  hbuf;         /* sub system buffer handle */
   PWORD lpbuf;        /* buffer pointer           */
   char name[MAX_BOARD_NAME_LENGTH];  /* string for board name    */
   char entry[MAX_BOARD_NAME_LENGTH]; /* string for board name    */
} BOARD;

typedef BOARD* LPBOARD;
static BOARD board;


/*
this is a callback function of olDaEnumBoards, it gets the 
strings of the Open Layers board and attempts to initialize
the board.  If successful, enumeration is halted.
*/
BOOL CALLBACK GetDriver( LPSTR lpszName, LPSTR lpszEntry, LPARAM lParam )   

{
   LPBOARD lpboard = (LPBOARD)(LPVOID)lParam;
   
   /* fill in board strings */

#ifdef WIN32
   strncpy(lpboard->name,lpszName,MAX_BOARD_NAME_LENGTH-1);
   strncpy(lpboard->entry,lpszEntry,MAX_BOARD_NAME_LENGTH-1);
#else
   lstrcpyn(lpboard->name,lpszName,MAX_BOARD_NAME_LENGTH-1);
   lstrcpyn(lpboard->entry,lpszEntry,MAX_BOARD_NAME_LENGTH-1);
#endif

   /* try to open board */

   lpboard->status = olDaInitialize(lpszName,&lpboard->hdrvr);
   if   (lpboard->hdrvr != NULL)
      return FALSE;          /* false to stop enumerating */
   else                      
      return TRUE;           /* true to continue          */
}

/*
 * Initilize the global board handle
 */
int InitializeTheBoard()
{
   if (board.hdrvr != 0)
      return DEVICE_OK;

   // initialize the board
   /* Get first available Open Layers board */
   board.hdrvr = NULL;
   int ret = olDaEnumBoards(GetDriver,(LPARAM)(LPBOARD)&board);
   if (ret != OLNOERROR)
      return ret;

   /* check for error within callback function */
   if (board.status != OLNOERROR)
      return board.status;

   /* check for NULL driver handle - means no boards */
   if (board.hdrvr == NULL)
      return ERR_BOARD_NOT_FOUND;

   /* get handle to DOUT sub system */
   ret = olDaGetDASS(board.hdrvr, OLSS_DOUT, 0, &board.hdass_do);
   if (ret != OLNOERROR)
      return ret;

   ret = olDaGetDASS(board.hdrvr, OLSS_DA, 0, &board.hdass_da);
   if (ret != OLNOERROR)
      return ret;

   /* set subsystem for single value operation */
   ret = olDaSetDataFlow(board.hdass_do, OL_DF_SINGLEVALUE);
   if (ret != OLNOERROR)
      return ret;
   ret = olDaSetDataFlow(board.hdass_da, OL_DF_SINGLEVALUE);
   if (ret != OLNOERROR)
      return ret;

   ret = olDaConfig(board.hdass_do);
   if (ret != OLNOERROR)
      return ret;
   ret = olDaConfig(board.hdass_da);
   if (ret != OLNOERROR)
      return ret;

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceNameDTOLSwitch, MM::StateDevice, "DTOL Switch");
   RegisterDevice(g_DeviceNameDTOLShutter, MM::ShutterDevice, "DTOL Shutter");
   RegisterDevice(g_DeviceNameDTOLDA0, MM::SignalIODevice, "DTOL DAC 0");
   RegisterDevice(g_DeviceNameDTOLDA1, MM::SignalIODevice, "DTOL DAC 1");
   RegisterDevice(g_DeviceNameDTOLDA2, MM::SignalIODevice, "DTOL DAC 2");
   RegisterDevice(g_DeviceNameDTOLDA3, MM::SignalIODevice, "DTOL DAC 3");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameDTOLSwitch) == 0)
   {
      return new CDTOLSwitch;
   }
   else if (strcmp(deviceName, g_DeviceNameDTOLShutter) == 0)
   {
      return new CDTOLShutter;
   }
   else if (strcmp(deviceName, g_DeviceNameDTOLDA0) == 0)
   {
      return new CDTOLDA(0, g_DeviceNameDTOLDA0);
   }
   else if (strcmp(deviceName, g_DeviceNameDTOLDA1) == 0)
   {
      return new CDTOLDA(1, g_DeviceNameDTOLDA1);
   }
      else if (strcmp(deviceName, g_DeviceNameDTOLDA2) == 0)
   {
      return new CDTOLDA(2, g_DeviceNameDTOLDA2);
   }
   else if (strcmp(deviceName, g_DeviceNameDTOLDA3) == 0)
   {
      return new CDTOLDA(3, g_DeviceNameDTOLDA3);
   }


   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CDTOLSwitch implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

CDTOLSwitch::CDTOLSwitch() : numPos_(256), busy_(false)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");
}

CDTOLSwitch::~CDTOLSwitch()
{
   Shutdown();
}

void CDTOLSwitch::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameDTOLSwitch);
}


int CDTOLSwitch::Initialize()
{
   int ret = InitializeTheBoard();
   if (ret != DEVICE_OK)
      return ret;

   // set property list
   // -----------------
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceNameDTOLSwitch, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "DTOL digital output driver", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // create positions and labels
   const int bufSize = 1024;
   char buf[bufSize];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, bufSize, "%d", (unsigned)i);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CDTOLSwitch::OnState);
   nRet = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   nRet = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int CDTOLSwitch::Shutdown()
{
   olDaReleaseDASS(board.hdass_do);
   olDaReleaseDASS(board.hdass_da);
   olDaTerminate(board.hdrvr);
   board.hdrvr = 0;
   initialized_ = false;
   return DEVICE_OK;
}

int CDTOLSwitch::WriteToPort(long value)
{
   //Out32(g_addrLPT1, buf);
   int ret = olDaPutSingleValue(board.hdass_do, value, 0 /* channel */, 1.0 /*gain*/);
   if (ret != OLNOERROR)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CDTOLSwitch::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller to use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      g_switchState = pos;
      if (g_shutterState > 0)
         return WriteToPort(pos);
   }

   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// CDTOLDA implementation
// ~~~~~~~~~~~~~~~~~~~~~~

CDTOLDA::CDTOLDA(unsigned channel, const char* name) :
      busy_(false), minV_(0.0), maxV_(0.0), volts_(0.0), gatedVolts_(0.0), encoding_(0), resolution_(0), channel_(channel), name_(name), gateOpen_(true)
{
   InitializeDefaultErrorMessages();

   // add custom error messages
   SetErrorText(ERR_UNKNOWN_POSITION, "Invalid position (state) specified");
   SetErrorText(ERR_INITIALIZE_FAILED, "Initialization of the device failed");
   SetErrorText(ERR_WRITE_FAILED, "Failed to write data to the device");
   SetErrorText(ERR_CLOSE_FAILED, "Failed closing the device");

   CreateProperty(g_PropertyMin, "0.0", MM::Float, false, 0, true);
   CreateProperty(g_PropertyMax, "5.0", MM::Float, false, 0, true);
      
}

CDTOLDA::~CDTOLDA()
{
   Shutdown();
}

void CDTOLDA::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int CDTOLDA::Initialize()
{
   int ret = InitializeTheBoard();
   if (ret != DEVICE_OK)
      return ret;

   // obtain scaling info

   ret = olDaGetRange(board.hdass_da, &maxV_, &minV_);
   if (ret != OLNOERROR)
      return ret;

   ret = olDaGetEncoding(board.hdass_da, &encoding_);
   if (ret != OLNOERROR)
      return ret;

   ret = olDaGetResolution(board.hdass_da, &resolution_);
   if (ret != OLNOERROR)
      return ret;


   // set property list
   // -----------------
   
   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "DTOL DAC driver", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Voltage
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CDTOLDA::OnVolts);
   nRet = CreateProperty(g_volts, "0.0", MM::Float, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   double minV(0.0);
   nRet = GetProperty(g_PropertyMin, minV);
   assert (nRet == DEVICE_OK);

   double maxV(0.0);
   nRet = GetProperty(g_PropertyMax, maxV);
   assert (nRet == DEVICE_OK);

   nRet = SetPropertyLimits(g_volts, minV, maxV);
   if (nRet != DEVICE_OK)
      return nRet;

   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   initialized_ = true;

   return DEVICE_OK;
}

int CDTOLDA::Shutdown()
{
   olDaReleaseDASS(board.hdass_do);
   olDaReleaseDASS(board.hdass_da);
   olDaTerminate(board.hdrvr);
   board.hdrvr = 0;
   initialized_ = false;
   return DEVICE_OK;
}

int CDTOLDA::SetSignal(double volts)
{
   return SetProperty(g_volts, CDeviceUtils::ConvertToString(volts));
}

int CDTOLDA::WriteToPort(long value)
{
   int ret = olDaPutSingleValue(board.hdass_da, value, channel_, 1.0 /*gain*/);
   if (ret != OLNOERROR)
      return ret;

   return DEVICE_OK;
}

int CDTOLDA::SetVolts(double volts)
{
   if (volts > maxV_ || volts < minV_)
      return DEVICE_RANGE_EXCEEDED;

   long value = (long) ((1L<<resolution_)/((float)maxV_ - (float)minV_) * (volts - (float)minV_));
   value = min((1L<<resolution_)-1,value);

   if (encoding_ != OL_ENC_BINARY) {
      // convert to 2's comp by inverting the sign bit
      long sign = 1L << (resolution_ - 1);
      value ^= sign;
      if (value & sign)           //sign extend
         value |= 0xffffffffL << resolution_;
   }
   return WriteToPort(value);
}

int CDTOLDA::GetLimits(double& minVolts, double& maxVolts)
{
   int nRet = GetProperty(g_PropertyMin, minVolts);
   if (nRet == DEVICE_OK)
      return nRet;

   nRet = GetProperty(g_PropertyMax, maxVolts);
   if (nRet == DEVICE_OK)
      return nRet;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CDTOLDA::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // nothing to do, let the caller to use cached property
   }
   else if (eAct == MM::AfterSet)
   {
      double volts;
      pProp->Get(volts);
      return SetVolts(volts);
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CDTOLShutter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

CDTOLShutter::CDTOLShutter() : initialized_(false), name_(g_DeviceNameDTOLShutter), openTimeUs_(0)
{
   InitializeDefaultErrorMessages();
   EnableDelay();
}

CDTOLShutter::~CDTOLShutter()
{
   Shutdown();
}

void CDTOLShutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

bool CDTOLShutter::Busy()
{
   long interval = GetClockTicksUs() - openTimeUs_;

   if (interval/1000.0 < GetDelayMs() && interval > 0)
   {
      return true;
   }
   else
   {
       return false;
   }
}

int CDTOLShutter::Initialize()
{
   int ret = InitializeTheBoard();
   if (ret != DEVICE_OK)
      return ret;

   // set property list
   // -----------------
   
   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "DTOL shutter driver (LPT)", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // OnOff
   // ------
   CPropertyAction* pAct = new CPropertyAction (this, &CDTOLShutter::OnOnOff);
   ret = CreateProperty("OnOff", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // set shutter into the off state
   WriteToPort(0);

   vector<string> vals;
   vals.push_back("0");
   vals.push_back("1");
   ret = SetAllowedValues("OnOff", vals);
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   openTimeUs_ = GetClockTicksUs();

   return DEVICE_OK;
}

int CDTOLShutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int CDTOLShutter::SetOpen(bool open)
{
   if (open)
      return SetProperty("OnOff", "1");
   else
      return SetProperty("OnOff", "0");
}

int CDTOLShutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty("OnOff", buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos > 0 ? open = true : open = false;

   return DEVICE_OK;
}

int CDTOLShutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int CDTOLShutter::WriteToPort(long value)
{
   int ret = olDaPutSingleValue(board.hdass_do, value, 0 /* channel */, 1.0 /*gain*/);
   if (ret != OLNOERROR)
      return ret;
   //Out32(g_addrLPT1, buf);
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CDTOLShutter::OnOnOff(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // use cached state
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      int ret;
      if (pos == 0)
         ret = WriteToPort(0); // turn everything off
      else
         ret = WriteToPort(g_switchState); // restore old setting
      if (ret != DEVICE_OK)
         return ret;
      g_shutterState = pos;
      openTimeUs_ = GetClockTicksUs();
   }

   return DEVICE_OK;
}
