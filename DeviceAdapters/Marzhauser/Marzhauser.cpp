///////////////////////////////////////////////////////////////////////////////
// FILE:          Marzhauser.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Marzhauser Tango Controller Driver
//                XY Stage
//                Z  Stage
//                TTL Shutter
//                DAC-0 (e.g. Lamp 1)
//                DAC-1 (e.g. Lamp 2)
//                ADC   (e.g. 0..5 V)
//
// AUTHOR:        Falk Dettmar, falk.dettmar@marzhauser-st.de, 09/04/2009
// COPYRIGHT:     Marzhauser SensoTech GmbH, Wetzlar, 2010
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//

#ifdef WIN32
//   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Marzhauser.h"
#include <string>
//#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>


const char* g_ControllerName    = "Tango Controller";
const char* g_XYStageDeviceName = "XY Stage";
const char* g_ZStageDeviceName  = "Z Axis";
const char* g_ShutterName       = "Tango TTL Shutter";
const char* g_Lamp1Name         = "Tango Lamp-1";
const char* g_Lamp2Name         = "Tango Lamp-2";
const char* g_DACName           = "Tango DAC";
const char* g_ADCName           = "Tango ADC";


const char* g_Tango_Reset       = "Reset";
const char* g_Tango_Version     = "Version";


const char* g_TxTerm            = "\r"; //unique termination from MM to Tango communication
const char* g_RxTerm            = "\r"; //unique termination from Tango to MM communication


using namespace std;

bool g_DeviceTangoAvailable = false;
int  g_NumberOfAxes = 0;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_ControllerName,    "Tango Controller");             
   AddAvailableDeviceName(g_XYStageDeviceName, "XY Stage");
   AddAvailableDeviceName(g_ZStageDeviceName,  "Z Axis");
   AddAvailableDeviceName(g_ShutterName,       "Tango TTL Shutter");
   AddAvailableDeviceName(g_Lamp1Name,         "Tango Lamp-1 [0..100%]"); 
   AddAvailableDeviceName(g_Lamp2Name,         "Tango Lamp-2 [0..100%]"); 
   AddAvailableDeviceName(g_DACName,           "Tango DAC [0..10V]"); 
   AddAvailableDeviceName(g_ADCName,           "Tango ADC [0..5V]"); 
}                                                                            

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
   if (deviceName == 0) return 0;
   if (strcmp(deviceName, g_ControllerName)    == 0) return new Hub();                           
   if (strcmp(deviceName, g_XYStageDeviceName) == 0) return new XYStage();                                     
   if (strcmp(deviceName, g_ZStageDeviceName)  == 0) return new ZStage();
   if (strcmp(deviceName, g_ShutterName)       == 0) return new Shutter();
   if (strcmp(deviceName, g_Lamp1Name)         == 0) return new Lamp(g_Lamp1Name, 0); //legal range of id = [0..1]
   if (strcmp(deviceName, g_Lamp2Name)         == 0) return new Lamp(g_Lamp2Name, 1);
   if (strcmp(deviceName, g_DACName)           == 0) return new DAC();
   if (strcmp(deviceName, g_ADCName)           == 0) return new ADC();
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


// General utility function:
int ClearPort(MM::Device& device, MM::Core& core, std::string port)
{
   // Clear contents of serial port 
   const int bufSize = 255;
   unsigned char clear[bufSize];                                                        
   unsigned long read = bufSize;                                               
   int ret;                                                                    
   while ((int) read == bufSize)                                                     
   {                                                                           
      ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read); 
      if (ret != DEVICE_OK)                                                    
         return ret;                                                           
   }                                                                           
   return DEVICE_OK;                                                           
}





///////////////////////////////////////////////////////////////////////////////
// Tango Hub
///////////////////////////////////////////////////////////////////////////////

Hub::Hub() :
  answerTimeoutMs_(1000),
  initialized_(false)
{
   InitializeDefaultErrorMessages();

   // custom error messages:
   
   // pre-initialization properties

   // Port:
   CPropertyAction* pAct = new CPropertyAction(this, &Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

Hub::~Hub()
{
   Shutdown();
}

void Hub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_ControllerName);
}


bool Hub::Busy()
{
   return false;
}




int Hub::Initialize()
{
   // empty the Rx serial buffer before sending command
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, g_ControllerName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Tango Controller", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Version of the controller:
   const char* cm = "?version";
   ret = SendSerialCommand(port_.c_str(), cm, g_TxTerm);
   if (ret != DEVICE_OK) 
      return ret;

   // Read out result
   string response;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, response);
   if (ret != DEVICE_OK) 
      return ret;

   // Create read-only property with version info
   ret = CreateProperty(g_Tango_Version, response.c_str(), MM::String, true);
   if (ret != DEVICE_OK) 
      return ret;

   ret = SendSerialCommand(port_.c_str(), "!autostatus 0", g_TxTerm);
   if (ret !=DEVICE_OK) return ret;


   ret = SendSerialCommand(port_.c_str(), "?det", g_TxTerm);
   if (ret !=DEVICE_OK) return ret;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, response);
   if (ret != DEVICE_OK) return ret;
   if (response.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;
   istringstream is(response);
   int udet;
   is >> udet;
   g_NumberOfAxes = (udet >> 4) &0x0f;

   if (g_NumberOfAxes == 2)
   {
     ret = SendSerialCommand(port_.c_str(), "!encpos 1 1", g_TxTerm);
     if (ret !=DEVICE_OK) return ret;
   }
   else if (g_NumberOfAxes > 2)
   {
     ret = SendSerialCommand(port_.c_str(), "!encpos 1 1 1", g_TxTerm);
     if (ret !=DEVICE_OK) return ret;
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   g_DeviceTangoAvailable = true;

   return DEVICE_OK;
}


int Hub::Shutdown()
{ 
  initialized_ = false;
  g_DeviceTangoAvailable = false;
  return DEVICE_OK;
}



//////////////// Action Handlers (Hub) /////////////////

int Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str());
         return DEVICE_INVALID_INPUT_PARAM;
      }
      pProp->Get(port_);
   }
   return DEVICE_OK;
}




///////////////////////////////////////////////////////////////////////////////
// XYStage
//
/*
* XYStage - two axis stage device.
* Note that this adapter uses two coordinate systems.  There is the adapters own coordinate
* system with the X and Y axis going the 'Micro-Manager standard' direction
* Then, there is the Tango native system.  All functions using 'steps' use the Tango system
* All functions using Um use the Micro-Manager coordinate system
*/

XYStage::XYStage() :
   initialized_(false),
   range_measured_(false),
   answerTimeoutMs_(1000),
   stepSizeXUm_(0.01), //  1000 * pitch/819200 
   stepSizeYUm_(0.01), //  1000 * pitch/819200 
   speedX_(20.0), //[mm/s]
   speedY_(20.0), //[mm/s]
   accelX_(0.2), //[m/s²]
   accelY_(0.2),
   originX_(0),
   originY_(0)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------
   // NOTE: pre-initialization properties contain parameters which must be defined fo
   // proper startup

   // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "Tango XY stage driver adapter", MM::String, true);
}

XYStage::~XYStage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// XYStage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void XYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceName);
}

/**
 * Performs device initialization.
 * Additional properties can be defined here too.
 */
int XYStage::Initialize()
{
   if (!g_DeviceTangoAvailable) return DEVICE_NOT_CONNECTED;

   // Step size
   // ---------
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnStepSizeX);
   // TODO: get current step size from the controller
   int ret = CreateProperty("StepSizeX [um]", "1.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   pAct = new CPropertyAction (this, &XYStage::OnStepSizeY);
   // TODO: get current step size from the controller
   ret = CreateProperty("StepSizeY [um]", "1.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Speed (in mm/s)
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnSpeedX);
   // TODO: get current speed from the controller
   ret = CreateProperty("SpeedX [mm/s]", "40.0", MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("SpeedX [mm/s]", 0.001, 100.0); // mm/s

   pAct = new CPropertyAction (this, &XYStage::OnSpeedY);
   // TODO: get current speed from the controller
   ret = CreateProperty("SpeedY [mm/s]", "40.0", MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("SpeedY [mm/s]", 0.001, 100.0); // mm/s


   // Accel (Acceleration (in m/s²)
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnAccelX);
   // TODO: get current Acceleration from the controller
   ret = CreateProperty("Acceleration X [m/s^2]", "0.2", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Acceleration X [m/s^2]", 0.01, 2.0);
   
   pAct = new CPropertyAction (this, &XYStage::OnAccelY);
   // TODO: get current Acceleration from the controller
   ret = CreateProperty("Acceleration Y [m/s^2]", "0.2", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Acceleration Y [m/s^2]", 0.01, 2.0);
   
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int XYStage::Shutdown()
{
   initialized_    = false;
   range_measured_ = false;

   return DEVICE_OK;
}

/**
 * Returns true if any axis (X or Y) is still moving.
 */
bool XYStage::Busy()
{
   string resp;
   int ret;

   // send command
   ret = GetCommand("?statusaxis", resp);
   if (ret != DEVICE_OK)
   {
      ostringstream os;
      os << "SendSerialCommand failed in XYStage::Busy, error code:" << ret;
      this->LogMessage(os.str().c_str(), false);
      // return false; // can't write, continue just so that we can read an answer in case write succeeded even though we received an error
   }

   if ((resp[0] == 'M') || (resp[1] == 'M')) return true;
   else return false;
}



/**
 * Returns current position in µm.
 */
int XYStage::GetPositionUm(double& x, double& y)
{
int ret;
   PurgeComPort(port_.c_str());
   
   // switch to µm
   ret = SendSerialCommand(port_.c_str(), "!dim 1 1", g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   const char* cmd = "?pos";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;
  
   // block/wait for acknowledge, or until we time out;
   string resp;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   float xx, yy;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f %f\r", &xx, &yy);
   x = xx;
   y = yy;
   return DEVICE_OK;
}


/**
 * Sets position in µm
 */
int XYStage::SetPositionUm(double x, double y)
{
   ostringstream os;
   os << "XYStage::SetPositionUm() " << x << " " << y;
   this->LogMessage(os.str().c_str());

   // switch to µm
   int ret = SendSerialCommand(port_.c_str(), "!dim 1 1", g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "!moa "<< x << " " << y;

   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret != DEVICE_OK)
     return ret;

   string resp;
   ret = GetCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}
  

/**
 * Sets position in steps.
 */
int XYStage::SetPositionSteps(long x, long y)
{
   // switch to steps
   int ret = SendSerialCommand(port_.c_str(), "!dim 0 0", g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "!moa "<< x << " " << y;
   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);

   string resp;
   ret = GetCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}
  
/**
 * Sets relative position in steps.
 */
int XYStage::SetRelativePositionSteps(long x, long y)
{
   // switch to steps
   int ret = SendSerialCommand(port_.c_str(), "!dim 0 0", g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "!mor "<< x << " " << y;

   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   return ret;
}

/**
 * Returns current position in steps.
 */
int XYStage::GetPositionSteps(long& x, long& y)
{
int ret;
   PurgeComPort(port_.c_str());
   
   // switch to steps
   ret = SendSerialCommand(port_.c_str(), "!dim 0 0", g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   const char* cmd = "?pos";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;
  
   string resp;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
   if (ret != DEVICE_OK) 
      return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   istringstream is(resp);

   is >> x;
   is >> y;
   return DEVICE_OK;
}

/**
 * Defines current position as origin (0,0) coordinate of the controller.
 */
int XYStage::SetOrigin()
{
   PurgeComPort(port_.c_str());

   int ret = SendSerialCommand(port_.c_str(), "!pos 0 0", g_TxTerm);
   if (ret != DEVICE_OK) return ret;
  
   string resp;
   ret = GetCommand("?status", resp);
   if (ret != DEVICE_OK) return ret;

   if (resp.compare("OK...") != 0)
   {
      return DEVICE_SERIAL_INVALID_RESPONSE;
   }

   return SetAdapterOrigin();
}

/**
 * Defines current position as origin (0,0) coordinate of our coordinate system
 * Get the current (stage-native) XY position
 * This is going to be the origin in our coordinate system
 * The Tango stage X axis is the same orientation as out coordinate system, the Y axis is reversed
 */
int XYStage::SetAdapterOrigin()
{
   double xx, yy;
   int ret = GetPositionUm(xx, yy);
   if (ret != DEVICE_OK)
      return ret;
   originX_ = xx;
   originY_ = yy;

   return DEVICE_OK;
}


int XYStage::Home()
{
   const char* cmd;
   int ret;

   range_measured_ = false;

   PurgeComPort(port_.c_str());

   // format the command
   cmd = "!cal x";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK) return ret;

   cmd = "!cal y";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK) return ret;
  
   bool status;
   int numTries=0, maxTries=400;
   long pollIntervalMs = 100;

   this->LogMessage("Starting read in XY-Stage HOME\n", true);

   do
   {
      status = Busy();
	  numTries++;
      CDeviceUtils::SleepMs(pollIntervalMs);
   }
   while(status && (numTries < maxTries)); // keep trying up to maxTries * pollIntervalMs ( = 20 sec)

   ostringstream os;
   os << "Tried reading "<< numTries << " times, and finally read " << status;
   this->LogMessage(os.str().c_str());



   // additional range measure to provide GetLimitsUm()

   cmd = "!rm x";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK) return ret;

   cmd = "!rm y";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK) return ret;
  
   this->LogMessage("Starting read in XY-Stage HOME\n", true);

   do
   {
      status = Busy();
	  numTries++;
      CDeviceUtils::SleepMs(pollIntervalMs);
   }
   while(status && (numTries < maxTries)); // keep trying up to maxTries * pollIntervalMs ( = 20 sec)

   os << "Tried reading "<< numTries << " times, and finally read " << status;
   this->LogMessage(os.str().c_str());

   if (status) return DEVICE_ERR;


   range_measured_ = true;
   return DEVICE_OK;
}


int XYStage::Stop()
{
   int ret;
   PurgeComPort(port_.c_str());
   ret = SendSerialCommand(port_.c_str(), "a x", g_TxTerm);
   ret = SendSerialCommand(port_.c_str(), "a y", g_TxTerm);
   return DEVICE_OK;
}


/**
 * Returns the stage position limits in um.
 */
int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
  int ret;

  if (!range_measured_) return DEVICE_UNKNOWN_POSITION;

  PurgeComPort(port_.c_str());

  // switch to µm
  ret = SendSerialCommand(port_.c_str(), "!dim 1 1", g_TxTerm);
  if (ret != DEVICE_OK) return ret;

  // format the command
  const char* cmd = "?lim x";
  ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
  if (ret != DEVICE_OK) return ret;
  
  // block/wait for acknowledge, or until we time out;
  string resp;
  ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
  if (ret != DEVICE_OK)  return ret;
  if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

  float lower, upper;
  char iBuf[256];
  strcpy(iBuf,resp.c_str());
  sscanf(iBuf, "%f %f\r", &lower, &upper);
  xMin = lower;
  xMax = upper;

  // format the command
  cmd = "?lim y";
  ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
  if (ret != DEVICE_OK) return ret;
  
  // block/wait for acknowledge, or until we time out;
  ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
  if (ret != DEVICE_OK)  return ret;
  if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

  strcpy(iBuf,resp.c_str());
  sscanf(iBuf, "%f %f\r", &lower, &upper);
  yMin = lower;
  yMax = upper;

  return DEVICE_OK;
}


int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
int ret;
   PurgeComPort(port_.c_str());
   
   // switch to steps
   ret = SendSerialCommand(port_.c_str(), "!dim 0 0", g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   const char* cmd = "?lim x";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;
  
   // block/wait for acknowledge, or until we time out;
   string resp;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   long lower, upper;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%d %d\r", &lower, &upper);
   xMin = lower;
   xMax = upper;

   // format the command
   cmd = "?lim y";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;
  
   // block/wait for acknowledge, or until we time out;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%i %i\r", &lower, &upper);
   yMin = lower;
   yMax = upper;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Internal, helper methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sends a specified command to the controller
 */
int XYStage::GetCommand(const string& cmd, string& response)
{
   PurgeComPort(port_.c_str());
   if (DEVICE_OK != SendSerialCommand(port_.c_str(), cmd.c_str(), g_TxTerm))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // block/wait for acknowledge, or until we time out;
   const unsigned bufLen = 256;
   char answer[bufLen];
   unsigned curIdx = 0;
   memset(answer, 0, bufLen);
   unsigned long read;
   unsigned long startTime = GetClockTicksUs();

   char* pLF = 0;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), reinterpret_cast<unsigned char *> (answer + curIdx), bufLen - curIdx, read))
         return DEVICE_SERIAL_COMMAND_FAILED;
      curIdx += read;

      // look for the LF
      pLF = strstr(answer, g_RxTerm);
      if (pLF)
         *pLF = 0; // terminate the string

	  CDeviceUtils::SleepMs(1);
   }
   while(!pLF && (((GetClockTicksUs() - startTime) / 1000) < answerTimeoutMs_));

   if (!pLF)
      return DEVICE_SERIAL_TIMEOUT;

   response = answer;
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int XYStage::OnStepSizeX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: modify this method to query the step size
      // from the controller
      pProp->Set(stepSizeXUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <= 0.0)
      {
         pProp->Set(stepSizeXUm_);
         return DEVICE_INVALID_INPUT_PARAM;
      }
      stepSizeXUm_ = stepSize;
   }

   return DEVICE_OK;
}

int XYStage::OnStepSizeY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // TODO: modify this method to query the step size
      // from the controller
      pProp->Set(stepSizeYUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <= 0.0)
      {
         pProp->Set(stepSizeYUm_);
         return DEVICE_INVALID_INPUT_PARAM;
      }
      stepSizeYUm_ = stepSize;
   }

   return DEVICE_OK;
}

/*
 * Speed as returned by device is in mm/s
 * We convert that to um per second using the factor 1000
 */
int XYStage::OnSpeedX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // switch to mm/s
      int ret = SendSerialCommand(port_.c_str(), "!dim x 9", g_TxTerm);
      if (ret != DEVICE_OK) return ret;

	  string resp;
      ret = GetCommand("?vel x", resp);
      if (ret != DEVICE_OK)
         return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double speedX = atof(tokens.at(0).c_str());
      speedX_ = speedX;
      pProp->Set(speedX_);
   }
   else if (eAct == MM::AfterSet)
   {
      // switch to mm/s
      int ret = SendSerialCommand(port_.c_str(), "!dim x 9", g_TxTerm);
      if (ret != DEVICE_OK) return ret;

	  double uiSpeed; // Speed in mm/sec
      pProp->Get(uiSpeed);
      double speed = uiSpeed;

      ostringstream cmd;
      cmd << "!vel x " << speed;
	  ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
      if (ret != DEVICE_OK)
         return ret;

      string resp;
      ret = GetCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;


      speedX_ = speed;
   }
   return DEVICE_OK;
}


int XYStage::OnSpeedY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // switch to mm/s
      int ret = SendSerialCommand(port_.c_str(), "!dim y 9", g_TxTerm);
      if (ret != DEVICE_OK) return ret;

	  string resp;
      ret = GetCommand("?vel y", resp);
      if (ret != DEVICE_OK)
         return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double speedY = atof(tokens.at(0).c_str());
      speedY_ = speedY;
      pProp->Set(speedY_);
   }
   else if (eAct == MM::AfterSet)
   {
      // switch to mm/s
      int ret = SendSerialCommand(port_.c_str(), "!dim y 9", g_TxTerm);
      if (ret != DEVICE_OK) return ret;

	  double uiSpeed; // Speed in mm/sec
      pProp->Get(uiSpeed);
      double speed = uiSpeed;

      ostringstream cmd;
      cmd << "!vel y " << speed;
	  ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
      if (ret != DEVICE_OK)
         return ret;

      string resp;
      ret = GetCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;


      speedY_ = speed;
   }
   return DEVICE_OK;
}


int XYStage::OnAccelX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = GetCommand("?accel x", resp);
      if (ret != DEVICE_OK) return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double accelX = atof(tokens.at(0).c_str());
      accelX_ = accelX;
      pProp->Set(accelX_);
   }
   else if (eAct == MM::AfterSet)
   {
      double accel;
      pProp->Get(accel);
      if (accel < 0.001)  accel =  0.001; //clipping to useful values
      if (accel > 10.0 )  accel = 10.0;

	  ostringstream cmd;
      cmd << "!accel x " << accel;
	  int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
      if (ret != DEVICE_OK)
         return ret;

      string resp;
      ret = GetCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

      accelX_ = accel;
   }
   return DEVICE_OK;
}

int XYStage::OnAccelY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = GetCommand("?accel y", resp);
      if (ret != DEVICE_OK) return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double accelY = atof(tokens.at(0).c_str());
      accelY_ = accelY;
      pProp->Set(accelY_);
   }
   else if (eAct == MM::AfterSet)
   {
      double accel;
      pProp->Get(accel);
      if (accel < 0.001)  accel =  0.001; //clipping to useful values
      if (accel > 10.0 )  accel = 10.0;

	  ostringstream cmd;
      cmd << "!accel y " << accel;
	  int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
      if (ret != DEVICE_OK)
         return ret;

      string resp;
      ret = GetCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

      accelY_ = accel;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Z - Stage
///////////////////////////////////////////////////////////////////////////////

/**
 * Single axis stage.
 */
ZStage::ZStage() :
   initialized_(false),
   answerTimeoutMs_(1000),
   stepSizeUm_(0.1)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ZStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Tango Z axis driver", MM::String, true);
}

ZStage::~ZStage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void ZStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZStageDeviceName);
}

int ZStage::Initialize()
{
   if (!g_DeviceTangoAvailable) return DEVICE_NOT_CONNECTED;
   if (g_NumberOfAxes < 3) return DEVICE_NOT_CONNECTED;

   // set property list
   // -----------------
   
   // Position
   // --------
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnStepSize);
   int ret = CreateProperty("StepSize", "1.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;


   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int ZStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool ZStage::Busy()
{
  string resp;
  int ret;

   // send command
   ret = GetCommand("?statusaxis", resp);
   if (ret != DEVICE_OK)
   {
      ostringstream os;
      os << "SendSerialCommand failed in ZStage::Busy, error code:" << ret;
      this->LogMessage(os.str().c_str(), false);
      // return false; // can't write, continue just so that we can read an answer in case write succeeded even though we received an error
   }

   if (resp[2] == 'M') return true;
   else return false;
}


int ZStage::Stop()
{
   int ret;
   PurgeComPort(port_.c_str());
   ret = SendSerialCommand(port_.c_str(), "a z", g_TxTerm);
   return DEVICE_OK;
}


int ZStage::SetPositionUm(double pos)
{
   ostringstream os;
   os << "ZStage::SetPositionUm() " << pos;
   this->LogMessage(os.str().c_str());

   // switch to µm
   int ret = SendSerialCommand(port_.c_str(), "!dim z 1", g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "!moa z " << pos;

   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret != DEVICE_OK)
     return ret;

   string resp;
   ret = GetCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}

int ZStage::GetPositionUm(double& pos)
{
int ret;
   PurgeComPort(port_.c_str());
   
   // switch to µm
   ret = SendSerialCommand(port_.c_str(), "!dim z 1", g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   const char* cmd = "?pos z";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;
  
   // block/wait for acknowledge, or until we time out;
   string resp;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   float zz;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &zz);
   pos = zz;

   ostringstream os;
   os << "ZStage::GetPositionUm() " << pos;
   this->LogMessage(os.str().c_str());
   return DEVICE_OK;
}
  
int ZStage::SetPositionSteps(long pos)
{
int ret;
   // switch to steps
   ret = SendSerialCommand(port_.c_str(), "!dim z 0", g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "MOA Z " << pos;

   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;
  
   string resp;
   ret = GetCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}
  
int ZStage::GetPositionSteps(long& steps)
{
int ret;
   PurgeComPort(port_.c_str());
   
   // switch to steps
   ret = SendSerialCommand(port_.c_str(), "!dim z 0", g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   const char* cmd = "?pos z";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;
  
   string resp;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
   if (ret != DEVICE_OK) 
      return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   istringstream is(resp);
   is >> steps;
   return DEVICE_OK;
}
  
int ZStage::SetOrigin()
{
   return DEVICE_UNSUPPORTED_COMMAND;
}
 
int ZStage::GetLimits(double& /*min*/, double& /*max*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

///////////////////////////////////////////////////////////////////////////////
// Helper, internal methods
///////////////////////////////////////////////////////////////////////////////

int ZStage::GetCommand(const string& cmd, string& response)
{
   PurgeComPort(port_.c_str());
   if (DEVICE_OK != SendSerialCommand(port_.c_str(), cmd.c_str(), g_TxTerm))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // block/wait for acknowledge, or until we time out;
   const unsigned bufLen = 256;
   char answer[bufLen];
   unsigned curIdx = 0;
   memset(answer, 0, bufLen);
   unsigned long read;
   unsigned long startTime = GetClockTicksUs();

   char* pLF = 0;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), reinterpret_cast<unsigned char *> (answer + curIdx), bufLen - curIdx, read))
         return DEVICE_SERIAL_COMMAND_FAILED;
      curIdx += read;

      // look for the LF
      pLF = strstr(answer, g_RxTerm);
      if (pLF)
         *pLF = 0; // terminate the string

	  CDeviceUtils::SleepMs(1);
   }
   while(!pLF && (((GetClockTicksUs() - startTime) / 1000) < answerTimeoutMs_));

   if (!pLF)
      return DEVICE_SERIAL_TIMEOUT;

   response = answer;
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ZStage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <= 0.0)
      {
         pProp->Set(stepSizeUm_);
         return DEVICE_INVALID_INPUT_PARAM;
      }
      stepSizeUm_ = stepSize;
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Shutter 
// ~~~~~~~

Shutter::Shutter() : 
   name_(g_ShutterName), 
   initialized_(false), 
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   UpdateStatus();
}

Shutter::~Shutter()
{
   Shutdown();
}

void Shutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Shutter::Initialize()
{
   if (!g_DeviceTangoAvailable) return DEVICE_NOT_CONNECTED;

   // set property list
   // -----------------
   
   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0"); // low  = closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // high = open

//   EnableDelay();

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}


int Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}


bool Shutter::Busy()
{
  return false;
}


/**
 * Sends a specified command to the controller
 */
int Shutter::GetCommand(const string& cmd, string& response)
{
   PurgeComPort(port_.c_str());
   if (DEVICE_OK != SendSerialCommand(port_.c_str(), cmd.c_str(), g_TxTerm))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // block/wait for acknowledge, or until we time out;
   const unsigned bufLen = 256;
   char answer[bufLen];
   unsigned curIdx = 0;
   memset(answer, 0, bufLen);
   unsigned long read;
   unsigned long startTime = GetClockTicksUs();

   char* pLF = 0;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), reinterpret_cast<unsigned char *> (answer + curIdx), bufLen - curIdx, read))
         return DEVICE_SERIAL_COMMAND_FAILED;
      curIdx += read;

      // look for the LF
      pLF = strstr(answer, g_RxTerm);
      if (pLF)
         *pLF = 0; // terminate the string

	  CDeviceUtils::SleepMs(1);
   }
   while(!pLF && (((GetClockTicksUs() - startTime) / 1000) < answerTimeoutMs_));

   if (!pLF)
      return DEVICE_SERIAL_TIMEOUT;

   response = answer;
   return DEVICE_OK;
}



int Shutter::SetOpen(bool open)
{
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}


int Shutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos == 1 ? open = true : open = false;

   return DEVICE_OK;
}


int Shutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}


/**
 * Sends an open/close command through the serial port.
 */
int Shutter::SetShutterPosition(bool state)
{
   ostringstream  cmd;
   if (state)
      cmd << "!shutter 1";
   else
      cmd << "!shutter 0";
   int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret !=DEVICE_OK) return ret;

   string resp;
   ret = GetCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}


/**
 * Check the state of the shutter.
 */
int Shutter::GetShutterPosition(bool& state)
{
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;
   
   // request shutter status
   ostringstream  cmd;
   cmd << "?shutter";
   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret !=DEVICE_OK)
      return ret;

   // get result and interpret
   string result;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, result);
   if (ret != DEVICE_OK)
      return ret;

   if (result.size() != 1)
      return DEVICE_SERIAL_INVALID_RESPONSE;

   int x = atoi(result.substr(1,2).c_str());
   if      (x == 0) state = false;
   else if (x == 1) state = true;
   else return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}

//////////////// Action Handlers (Shutter) /////////////////
//
int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool open;
      int ret = GetShutterPosition(open);
      if (ret != DEVICE_OK)
         return ret;
      if (open)
         pProp->Set((long)1);
      else
         pProp->Set((long)0);
   }
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);
      if (state == 1)
         return SetShutterPosition(true);
      else if (state == 0)
         return SetShutterPosition(false);
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Tango Lamp
///////////////////////////////////////////////////////////////////////////////
Lamp::Lamp (const char* name, int id) :
   initialized_ (false),
   name_ (name),
   id_(id), 
   open_(false),
   answerTimeoutMs_(100)
{
   InitializeDefaultErrorMessages();
}

Lamp::~Lamp ()
{
   Shutdown();
}

void Lamp::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int Lamp::GetCommand(const string& cmd, string& response)
{
   PurgeComPort(port_.c_str());
   if (DEVICE_OK != SendSerialCommand(port_.c_str(), cmd.c_str(), g_TxTerm))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // block/wait for acknowledge, or until we time out;
   const unsigned bufLen = 256;
   char answer[bufLen];
   unsigned curIdx = 0;
   memset(answer, 0, bufLen);
   unsigned long read;
   unsigned long startTime = GetClockTicksUs();

   char* pLF = 0;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), reinterpret_cast<unsigned char *> (answer + curIdx), bufLen - curIdx, read))
         return DEVICE_SERIAL_COMMAND_FAILED;
      curIdx += read;

      // look for the LF
      pLF = strstr(answer, g_RxTerm);
      if (pLF)
         *pLF = 0; // terminate the string

	  CDeviceUtils::SleepMs(1);
   }
   while(!pLF && (((GetClockTicksUs() - startTime) / 1000) < answerTimeoutMs_));

   if (!pLF)
      return DEVICE_SERIAL_TIMEOUT;

   response = answer;
   return DEVICE_OK;
}


int Lamp::GetLampIntensity(double& intensity)
{
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;
   
   // request lamp intensity
   ostringstream  cmd;
   cmd << "?anaout c " << id_;
   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret !=DEVICE_OK)
      return ret;

   // get result and interpret
   string resp;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   float percent;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &percent);

   intensity = percent;

   return DEVICE_OK;
}


int Lamp::SetLampIntensity(double intensity)
{
   
   // format the command
   ostringstream  cmd;
   cmd << "!anaout c " << id_ << " " << intensity;
   int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret !=DEVICE_OK)
      return ret;

   string resp;
   ret = GetCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}


int Lamp::Initialize()
{
   if (!g_DeviceTangoAvailable) return DEVICE_NOT_CONNECTED;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Tango Lamp", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Check current intensity of lamp
   ret = GetLampIntensity(intensity_);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &Lamp::OnState);
   if (intensity_ > 0) ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct); 
   else                ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   // Intensity
   pAct = new CPropertyAction(this, &Lamp::OnIntensity);
   CreateProperty("Intensity", "0.0", MM::Float, false, pAct);
   SetPropertyLimits("Intensity", 0.0, 100.0); // [0..100] percent (= [0..10V])

//   EnableDelay();

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool Lamp::Busy()
{
  return false;
}

int Lamp::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int Lamp::SetOpen(bool open)
{
   if (open) {
      int ret = SetLampIntensity(intensity_);
      if (ret != DEVICE_OK)
         return ret;
      open_ = true;
   } else {
      int ret = SetLampIntensity(0);
      if (ret != DEVICE_OK)
         return ret;
      open_ = false;
   }
   return DEVICE_OK;
}

int Lamp::GetOpen(bool &open)
{
   open = open_;
   return DEVICE_OK;
}

int Lamp::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;  
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int Lamp::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      bool open;
      GetOpen(open);
      if (open)
      {
         pProp->Set(1L);
      }
      else
      {
         pProp->Set(0L);
      }
   }
   else if (eAct == MM::AfterSet)
   {
      int ret;
      long pos;
      pProp->Get(pos);
      if (pos==1) {
         ret = this->SetOpen(true);
      } else {
         ret = this->SetOpen(false);
      }
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   return DEVICE_OK;
}


int Lamp::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      if (open_) {
         int ret = GetLampIntensity(intensity_);
         if (ret != DEVICE_OK)
            return ret;
      } else {
         // shutter is closed.  Return the cached value
         // TODO: check if user increased brightness
      }
      pProp->Set(intensity_); //(long)
   } else if (eAct == MM::AfterSet) {
      double intensity;
      pProp->Get(intensity);
      intensity_ = intensity;
      if (open_) {
         int ret = SetLampIntensity(intensity_);
         if (ret != DEVICE_OK)
            return ret;
      }
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Tango DAC
///////////////////////////////////////////////////////////////////////////////
DAC::DAC () :
   initialized_ (false),
   name_ (g_DACName),
   DACPort_(0),
   open_(false),
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // DAC Port
   CPropertyAction *pAct = new CPropertyAction (this, &DAC::OnDACPort);
   CreateProperty("DACPort", "0", MM::Integer, false, pAct,true);

   std::vector<std::string> vals; 
   vals.push_back("0");
   vals.push_back("1");
   vals.push_back("2");

   SetAllowedValues("DACPort", vals);

   
   
}

DAC::~DAC ()
{
   Shutdown();
}

void DAC::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int DAC::GetCommand(const string& cmd, string& response)
{
   PurgeComPort(port_.c_str());
   if (DEVICE_OK != SendSerialCommand(port_.c_str(), cmd.c_str(), g_TxTerm))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // block/wait for acknowledge, or until we time out;
   const unsigned bufLen = 256;
   char answer[bufLen];
   unsigned curIdx = 0;
   memset(answer, 0, bufLen);
   unsigned long read;
   unsigned long startTime = GetClockTicksUs();

   char* pLF = 0;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), reinterpret_cast<unsigned char *> (answer + curIdx), bufLen - curIdx, read))
         return DEVICE_SERIAL_COMMAND_FAILED;
      curIdx += read;

      // look for the LF
      pLF = strstr(answer, g_RxTerm);
      if (pLF)
         *pLF = 0; // terminate the string

	  CDeviceUtils::SleepMs(1);
   }
   while(!pLF && (((GetClockTicksUs() - startTime) / 1000) < answerTimeoutMs_));

   if (!pLF)
      return DEVICE_SERIAL_TIMEOUT;

   response = answer;
   return DEVICE_OK;
}


int DAC::GetSignal(double& volts)
{
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;
   
   // request DAC intensity
   ostringstream  cmd;
   cmd << "?anaout c " << DACPort_;
   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret !=DEVICE_OK)
      return ret;

   // get result and interpret
   string resp;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   float percent;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &percent);

   volts = percent / 10;

   return DEVICE_OK;
}


int DAC::SetSignal(double volts)
{
   
   // format the command
   ostringstream  cmd;
   cmd << "!anaout c " << DACPort_ << " " << (volts * 10);
   int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = GetCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}


int DAC::Initialize()
{
   if (!g_DeviceTangoAvailable) return DEVICE_NOT_CONNECTED;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Tango DAC", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Check current intensity of DAC
   ret = GetSignal(volts_);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &DAC::OnState);
   if (volts_ > 0) ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct); 
   else            ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   // Voltage
   pAct = new CPropertyAction(this, &DAC::OnVoltage);
   CreateProperty("Volts", "0.0", MM::Float, false, pAct);
   SetPropertyLimits("Volts", 0.0, 10.0); // [0..10V]

//   EnableDelay();

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool DAC::Busy()
{
  return false;
}

int DAC::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int DAC::SetGateOpen(bool open)
{
   if (open) {
      int ret = SetSignal(volts_);
      if (ret != DEVICE_OK)
         return ret;
      open_ = true;
   } else {
      int ret = SetSignal(0);
      if (ret != DEVICE_OK)
         return ret;
      open_ = false;
   }
   return DEVICE_OK;
}

int DAC::GetGateOpen(bool &open)
{
   open = open_;
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int DAC::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      bool open;
      GetGateOpen(open);
      if (open)
      {
         pProp->Set(1L);
      }
      else
      {
         pProp->Set(0L);
      }
   }
   else if (eAct == MM::AfterSet)
   {
      int ret;
      long pos;
      pProp->Get(pos);
      if (pos==1) {
         ret = this->SetGateOpen(true);
      } else {
         ret = this->SetGateOpen(false);
      }
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   return DEVICE_OK;
}


int DAC::OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      if (open_) {
         int ret = GetSignal(volts_);
         if (ret != DEVICE_OK)
            return ret;
      } else {
         // gate is closed.  Return the cached value
         // TODO: check if user changed voltage
      }
      pProp->Set(volts_);
   }
   else if (eAct == MM::AfterSet) {
      double intensity;
      pProp->Get(intensity);
      volts_ = intensity;
      if (open_) {
         int ret = SetSignal(volts_);
         if (ret != DEVICE_OK)
            return ret;
      }
   }
   return DEVICE_OK;
}


int DAC::OnDACPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set((long int)DACPort_);
    }
    else if (eAct == MM::AfterSet)
    {
        long channel;
        pProp->Get(channel);
        if ((channel >= 0) && (channel < MAX_DACHANNELS) )
        DACPort_ = channel;
    }
    return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// Tango ADC
///////////////////////////////////////////////////////////////////////////////
ADC::ADC () :
   initialized_ (false),
   name_ (g_ADCName),
   volts_(0.0),
   ADCPort_(0),
   answerTimeoutMs_(1000)
{
   InitializeDefaultErrorMessages();

   // ADC Port
   CPropertyAction *pAct = new CPropertyAction (this, &ADC::OnADCPort);
   CreateProperty("ADCPort", "0", MM::Integer, false, pAct,true);

   std::vector<std::string> vals; 
   vals.push_back("0");
   vals.push_back("1");
   vals.push_back("2");
   vals.push_back("3");
   vals.push_back("4");
   vals.push_back("5");
   vals.push_back("6");
   vals.push_back("7");
   vals.push_back("8");
   vals.push_back("9");
   vals.push_back("10");
   vals.push_back("11");
   vals.push_back("12");
   vals.push_back("13");
   vals.push_back("14");
   vals.push_back("15");

   SetAllowedValues("ADCPort", vals);
}

ADC::~ADC ()
{
   Shutdown();
}

void ADC::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int ADC::GetCommand(const string& cmd, string& response)
{
   PurgeComPort(port_.c_str());
   if (DEVICE_OK != SendSerialCommand(port_.c_str(), cmd.c_str(), g_TxTerm))
      return DEVICE_SERIAL_COMMAND_FAILED;

   // block/wait for acknowledge, or until we time out;
   const unsigned bufLen = 256;
   char answer[bufLen];
   unsigned curIdx = 0;
   memset(answer, 0, bufLen);
   unsigned long read;
   unsigned long startTime = GetClockTicksUs();

   char* pLF = 0;
   do {
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), reinterpret_cast<unsigned char *> (answer + curIdx), bufLen - curIdx, read))
         return DEVICE_SERIAL_COMMAND_FAILED;
      curIdx += read;

      // look for the LF
      pLF = strstr(answer, g_RxTerm);
      if (pLF)
         *pLF = 0; // terminate the string

	  CDeviceUtils::SleepMs(1);
   }
   while(!pLF && (((GetClockTicksUs() - startTime) / 1000) < answerTimeoutMs_));

   if (!pLF)
      return DEVICE_SERIAL_TIMEOUT;

   response = answer;
   return DEVICE_OK;
}


int ADC::GetSignal(double& volts)
{
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;
   
   // request ADC value
   ostringstream  cmd;
   cmd << "?anain c " << ADCPort_;
   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret !=DEVICE_OK)
      return ret;

   // get result and interpret
   string resp;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int raw;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%d\r", &raw);

   volts = (5.0 * raw) / 1023;

   return DEVICE_OK;
}


int ADC::Initialize()
{
   if (!g_DeviceTangoAvailable) return DEVICE_NOT_CONNECTED;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Tango ADC", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Check current intensity of ADC
   ret = GetSignal(volts_);
   if (DEVICE_OK != ret)
      return ret;

   // Voltage
   CPropertyAction* pAct = new CPropertyAction(this, &ADC::OnVolts);
   CreateProperty("Volts", "0.0", MM::Float, false, pAct);
   SetPropertyLimits("Volts", 0.0, 5.0); // [0..5V]

//   EnableDelay();

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}


int ADC::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}


int ADC::SetGateOpen(bool /*open*/)
{
   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int ADC::OnVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
         int ret = GetSignal(volts_);
         if (ret != DEVICE_OK)
            return ret;
      pProp->Set(volts_);
   }
   return DEVICE_OK;
}


int ADC::OnADCPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet)
    {
        pProp->Set((long int)ADCPort_);
    }
    else if (eAct == MM::AfterSet)
    {
        long channel;
        pProp->Get(channel);
        if ((channel >= 0) && (channel < MAX_ADCHANNELS) )
        ADCPort_ = channel;
    }
    return DEVICE_OK;
}



