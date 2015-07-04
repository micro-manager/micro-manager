///////////////////////////////////////////////////////////////////////////////
// FILE:          Marzhauser.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Marzhauser Tango Controller Driver
//                XY Stage
//                Z  Stage
//                TTL Shutter
//                DAC-0 (e.g. LED100 #1)
//                DAC-1 (e.g. LED100 #2)
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
#pragma warning(disable: 4355)
#endif

#include "Marzhauser.h"
#include <string>
//#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>


const char* g_XYStageDeviceName = "XYStage";
const char* g_ZStageDeviceName  = "ZAxis";
const char* g_AStageDeviceName  = "AAxis";
const char* g_ShutterName       = "TTLShutter";
const char* g_LED1Name          = "LED100-1";
const char* g_LED2Name          = "LED100-2";
const char* g_DACName           = "DAC";
const char* g_ADCName           = "ADC";

using namespace std;



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice,  "Tango XY Stage");
   RegisterDevice(g_ZStageDeviceName,  MM::StageDevice,    "Tango Z Axis");
   RegisterDevice(g_AStageDeviceName,  MM::StageDevice,    "Tango A Axis");
   RegisterDevice(g_ShutterName,       MM::ShutterDevice,  "Tango TTL Shutter");
   RegisterDevice(g_LED1Name,          MM::ShutterDevice,  "Tango LED100-1 [0..100%]");
   RegisterDevice(g_LED2Name,          MM::ShutterDevice,  "Tango LED100-2 [0..100%]");
   RegisterDevice(g_DACName,           MM::SignalIODevice, "Tango DAC [0..10V]");
   RegisterDevice(g_ADCName,           MM::SignalIODevice, "Tango ADC [0..5V]");
}                                                                            

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
   if (deviceName == 0) return 0;
   if (strcmp(deviceName, g_XYStageDeviceName) == 0) return new XYStage();                                     
   if (strcmp(deviceName, g_ZStageDeviceName)  == 0) return new ZStage();
   if (strcmp(deviceName, g_AStageDeviceName)  == 0) return new AStage();
   if (strcmp(deviceName, g_ShutterName)       == 0) return new Shutter();
   if (strcmp(deviceName, g_LED1Name)          == 0) return new LED100(g_LED1Name, 0); //legal range of id = [0..1]
   if (strcmp(deviceName, g_LED2Name)          == 0) return new LED100(g_LED2Name, 1);
   if (strcmp(deviceName, g_DACName)           == 0) return new DAC();
   if (strcmp(deviceName, g_ADCName)           == 0) return new ADC();

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}





///////////////////////////////////////////////////////////////////////////////
// TangoBase (convenience parent class)
//
TangoBase::TangoBase(MM::Device *device) :
   initialized_(false),
   Configuration_(0),
   port_("Undefined"),
   device_(device),
   core_(0)
{
}

TangoBase::~TangoBase()
{
}


// Communication "clear buffer" utility function:
int TangoBase::ClearPort(void)
{

   // Clear contents of serial port
   const int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;

   // clear Tango Rx Buffer from any incomplete command or rubbish sent from PC OS
   // send \r to clear Tango Rx Buffer
   ret = SendCommand("");
   CDeviceUtils::SleepMs(10);
 
   // clear PC Rx buffer
   while ((int) read == bufSize)
   {
      ret = core_->ReadFromSerial(device_, port_.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;                                                           
} 


// Communication "send" utility function:
int TangoBase::SendCommand(const char *command) const
{
   const char* g_TxTerm = "\r"; //unique termination from MM to Tango communication
   int ret;

   std::string base_command = "";
   base_command += command;
   // send command
   ret = core_->SetSerialCommand(device_, port_.c_str(), base_command.c_str(), g_TxTerm);
   return ret;
}


// Communication "send & receive" utility function:
int TangoBase::QueryCommand(const char *command, std::string &answer) const
{
   const char* g_RxTerm = "\r"; //unique termination from Tango to MM communication   std::string base_command = "";
   int ret;

   // send command
   ret = SendCommand(command);

   if (ret != DEVICE_OK)
      return ret;
   // block/wait for acknowledge (or until we time out)
   const size_t BUFSIZE = 2048;
   char buf[BUFSIZE] = {'\0'};
   ret = core_->GetSerialAnswer(device_, port_.c_str(), BUFSIZE, buf, g_RxTerm);
   answer = buf;
   return ret;
}


int TangoBase::CheckDeviceStatus(void)
{
  int ret = ClearPort();
  if (ret != DEVICE_OK) return ret;

  // Tango Version
  string resp;
  ret = QueryCommand("?version",resp);
  if (ret != DEVICE_OK) return ret;
  if (resp.length() < 1) return  DEVICE_NOT_CONNECTED;
  if (resp.find("TANGO") == string::npos) return DEVICE_NOT_CONNECTED;

  ret = SendCommand("!autostatus 0"); //diasable autostatus
  if (ret != DEVICE_OK) return ret;

  ret = QueryCommand("?det", resp);
  if (ret != DEVICE_OK) return ret;
  if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;
  Configuration_ = atoi(resp.c_str());

  initialized_ = true;
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
   TangoBase(this),
   range_measured_(false),
   stepSizeXUm_(0.0012), //=1000*pitch[mm]/gear/(motorsteps*4096)  (assume gear=1 motorsteps=200 => stepsize=pitch/819.2)
   stepSizeYUm_(0.0012),
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

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

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

/*
MM::DeviceDetectionStatus XYStage::DetectDevice(void)
{

   return TangoCheckSerialPort(*this,*GetCoreCallback(), port_, answerTimeoutMs_);
}
*/

/**
 * Performs device initialization.
 * Additional properties can be defined here too.
 */
int XYStage::Initialize()
{
   core_ = GetCoreCallback();

   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK) 
      return ret;

   int NumberOfAxes = (Configuration_ >> 4) &0x0f;
   if (NumberOfAxes < 2) return DEVICE_NOT_CONNECTED; 

   if ((Configuration_ & 7) > 0)
   {
     ret = SendCommand("!encpos 1 1");
     if (ret != DEVICE_OK) return ret;
   }

   // Step size
   // ---------
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnStepSizeX);
   // get current step size from the controller
   string resp;
   ret = QueryCommand("?pitch x", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   float pitch;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &pitch);
   stepSizeXUm_ = pitch/819.2;
   ret = CreateProperty("StepSizeX [um]", CDeviceUtils::ConvertToString(stepSizeXUm_), MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;

   pAct = new CPropertyAction (this, &XYStage::OnStepSizeY);
   // get current step size from the controller
   ret = QueryCommand("?pitch y", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &pitch);
   stepSizeYUm_ = pitch/819.2;
   ret = CreateProperty("StepSizeY [um]", CDeviceUtils::ConvertToString(stepSizeYUm_), MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;


   // Speed (in mm/s)
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnSpeedX);

   // switch to mm/s
   ret = SendCommand("!dim x 9");
   if (ret != DEVICE_OK) return ret;

   ret = QueryCommand("?vel x", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   ret = CreateProperty("SpeedX [mm/s]", resp.c_str(), MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("SpeedX [mm/s]", 0.001, 100.0); // mm/s

   pAct = new CPropertyAction (this, &XYStage::OnSpeedY);
   // switch to mm/s
   ret = SendCommand("!dim y 9");
   if (ret != DEVICE_OK) return ret;

   ret = QueryCommand("?vel y", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   ret = CreateProperty("SpeedY [mm/s]", resp.c_str(), MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("SpeedY [mm/s]", 0.001, 100.0); // mm/s


   // Accel (Acceleration (in m/s²)
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnAccelX);

   ret = QueryCommand("?accel x", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   ret = CreateProperty("Acceleration X [m/s^2]", resp.c_str(), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("Acceleration X [m/s^2]", 0.01, 2.0);
   
   pAct = new CPropertyAction (this, &XYStage::OnAccelY);

   ret = QueryCommand("?accel y", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   ret = CreateProperty("Acceleration Y [m/s^2]", resp.c_str(), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("Acceleration Y [m/s^2]", 0.01, 2.0);


   // Backlash (in µm)
   ret = QueryCommand("?backlash x", resp);
   if (ret == DEVICE_OK)
   {
      pAct = new CPropertyAction (this, &XYStage::OnBacklashX);
	  ret = CreateProperty("Backlash X [um]", resp.c_str(), MM::Float, false, pAct);
      if (ret != DEVICE_OK) return ret;
   }

   ret = QueryCommand("?backlash y", resp);
   if (ret == DEVICE_OK)
   {
      pAct = new CPropertyAction (this, &XYStage::OnBacklashY);
	  ret = CreateProperty("Backlash Y [um]", resp.c_str(), MM::Float, false, pAct);
      if (ret != DEVICE_OK) return ret;
   }


   ret = UpdateStatus();
   if (ret != DEVICE_OK) return ret;

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
   ret = QueryCommand("?statusaxis", resp);
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
   // switch to µm
   ret = SendCommand("!dim 1 1");
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?pos", resp);

   if (ret != DEVICE_OK) return ret;
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
   int ret = SendCommand("!dim 1 1");
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!moa "<< x << " " << y;

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}
  

/**
 * Sets relative position in µm
 */
int XYStage::SetRelativePositionUm(double dx, double dy)
{
   ostringstream os;
   os << "XYStage::SetPositionUm() " << dx << " " << dy;
   this->LogMessage(os.str().c_str());

   // switch to µm
   int ret = SendCommand("!dim 1 1");
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!mor "<< dx << " " << dy;

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
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
   int ret = SendCommand("!dim 0 0");
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!moa "<< x << " " << y;
   ret = SendCommand(cmd.str().c_str());
   if (ret !=DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
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
   int ret = SendCommand("!dim 0 0");
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "!mor "<< x << " " << y;

   ret = SendCommand(cmd.str().c_str());
   return ret;
}

/**
 * Returns current position in steps.
 */
int XYStage::GetPositionSteps(long& x, long& y)
{
   // switch to steps
   int ret = SendCommand("!dim 0 0");
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?pos", resp);
   if (ret != DEVICE_OK) return ret;
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
   int ret = SendCommand("!pos 0 0");
   if (ret != DEVICE_OK) return ret;
  
   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

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
   if (ret != DEVICE_OK) return ret;
   originX_ = xx;
   originY_ = yy;

   return DEVICE_OK;
}


/**
 * Defines current position as (x,y) coordinate of the controller.
 */
int XYStage::SetAdapterOriginUm(double x, double y)
{
   // switch to steps
   int ret = SendCommand("!dim 1 1");
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!pos "<< x << " " << y;
   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}



int XYStage::Home()
{
   const char* cmd;
   range_measured_ = false;

   // format the command
   cmd = "!cal x";
   int ret = SendCommand(cmd);
   if (ret != DEVICE_OK) return ret;

   cmd = "!cal y";
   ret = SendCommand(cmd);
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
   ret = SendCommand(cmd);
   if (ret != DEVICE_OK) return ret;

   cmd = "!rm y";
   ret = SendCommand(cmd);
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
   int ret = SendCommand("a x");
   ret = SendCommand("a y");
   return ret;
}


/**
 * Returns the stage position limits in um.
 */
int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
  if (!range_measured_) return DEVICE_UNKNOWN_POSITION;

  // switch to µm
  int ret = SendCommand("!dim 1 1");
  if (ret != DEVICE_OK) return ret;

  string resp;
  ret = QueryCommand("?lim x", resp);
  if (ret != DEVICE_OK) return ret;
  if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

  float lower, upper;
  char iBuf[256];
  strcpy(iBuf,resp.c_str());
  sscanf(iBuf, "%f %f\r", &lower, &upper);
  xMin = lower;
  xMax = upper;

  ret = QueryCommand("?lim y", resp);
  if (ret != DEVICE_OK) return ret;
  if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

  strcpy(iBuf,resp.c_str());
  sscanf(iBuf, "%f %f\r", &lower, &upper);
  yMin = lower;
  yMax = upper;

  return DEVICE_OK;
}



int XYStage::Move(double vx, double vy)
{
   // switch to mm/s
   int ret = SendCommand("!dim 9 9");
   if (ret != DEVICE_OK) return ret;

   // if vx and vy are not in mm/s then please convert here to mm/s
   double vx_ = vx;
   double vy_ = vy;

   // format the command
   ostringstream cmd;
   cmd << "!speed "<< vx_ << " " << vy_;
   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}



int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
   // switch to steps
   int ret = SendCommand("!dim 0 0");
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?lim x", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   long lower, upper;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%ld %ld\r", &lower, &upper);
   xMin = lower;
   xMax = upper;

   ret = QueryCommand("?lim y", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%ld %ld\r", &lower, &upper);
   yMin = lower;
   yMax = upper;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int XYStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}


int XYStage::OnStepSizeX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
     // get current step size from the controller
     string resp;
     int ret = QueryCommand("?pitch x", resp);
     if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
     float pitch;
     char iBuf[256];
     strcpy(iBuf,resp.c_str());
     sscanf(iBuf, "%f\r", &pitch);
     double stepSizeXUm = pitch/819.2;
     pProp->Set(stepSizeXUm);
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
     // get current step size from the controller
     string resp;
     int ret = QueryCommand("?pitch y", resp);
     if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
     float pitch;
     char iBuf[256];
     strcpy(iBuf,resp.c_str());
     sscanf(iBuf, "%f\r", &pitch);
     double stepSizeYUm = pitch/819.2;
     pProp->Set(stepSizeYUm);
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
      int ret = SendCommand("!dim x 9");
      if (ret != DEVICE_OK) return ret;

	  string resp;
      ret = QueryCommand("?vel x", resp);
      if (ret != DEVICE_OK) return ret;

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
      int ret = SendCommand("!dim x 9");
      if (ret != DEVICE_OK) return ret;

	  double uiSpeed; // Speed in mm/sec
      pProp->Get(uiSpeed);
      double speed = uiSpeed;

      ostringstream cmd;
      cmd << "!vel x " << speed;
	  ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
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
      int ret = SendCommand("!dim y 9");
      if (ret != DEVICE_OK) return ret;

	  string resp;
      ret = QueryCommand("?vel y", resp);
      if (ret != DEVICE_OK) return ret;

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
      int ret = SendCommand("!dim y 9");
      if (ret != DEVICE_OK) return ret;

	  double uiSpeed; // Speed in mm/sec
      pProp->Get(uiSpeed);
      double speed = uiSpeed;

      ostringstream cmd;
      cmd << "!vel y " << speed;
	  ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
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
      int ret = QueryCommand("?accel x", resp);
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
	  int ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK)
         return ret;

      string resp;
      ret = QueryCommand("?err", resp);
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
      int ret = QueryCommand("?accel y", resp);
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
	  int ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK)
         return ret;

      string resp;
      ret = QueryCommand("?err", resp);
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



int XYStage::OnBacklashX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = QueryCommand("?backlash x", resp);
      if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double backlash_ = atof(tokens.at(0).c_str());
      pProp->Set(backlash_);
   }

   else if (eAct == MM::AfterSet)
   {
      double backlash;
      pProp->Get(backlash);

	  ostringstream cmd;
      cmd << "!backlash x " << backlash;
	  int ret = SendCommand(cmd.str().c_str());
      if (ret !=DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return DEVICE_UNSUPPORTED_COMMAND;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   }
   return DEVICE_OK;
}


int XYStage::OnBacklashY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = QueryCommand("?backlash y", resp);
      if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double backlash_ = atof(tokens.at(0).c_str());
      pProp->Set(backlash_);
   }

   else if (eAct == MM::AfterSet)
   {
      double backlash;
      pProp->Get(backlash);
      if (backlash < 0.0)  backlash =  0.0; //clipping to useful values
      if (backlash > 10.0) backlash = 10.0;

	  ostringstream cmd;
      cmd << "!backlash y " << backlash;
	  int ret = SendCommand(cmd.str().c_str());
      if (ret !=DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return DEVICE_UNSUPPORTED_COMMAND;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
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
   TangoBase(this),
   range_measured_(false),
   stepSizeUm_(0.1),
   speedZ_(20.0), //[mm/s]
   accelZ_(0.2), //[m/s²]
   originZ_(0),
   sequenceable_(false),
   nrEvents_(1024)



{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ZStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Tango Z axis driver", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
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


/*
MM::DeviceDetectionStatus ZStage::DetectDevice(void)
{

   return TangoCheckSerialPort(*this,*GetCoreCallback(), port_, answerTimeoutMs_);
}
*/

int ZStage::Initialize()
{
   core_ = GetCoreCallback();

   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK) 
      return ret;

   int NumberOfAxes = (Configuration_ >> 4) &0x0f;
   if (NumberOfAxes < 3) return DEVICE_NOT_CONNECTED; 

   if ((Configuration_ & 7) > 0)
   {
     ret = SendCommand("!encpos z 1");
     if (ret != DEVICE_OK) return ret;
   }


   // set property list
   // -----------------
   
   // Step size
   // ---------
   CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnStepSize);
   // get current step size from the controller
   string resp;
   ret = QueryCommand("?pitch z", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   float pitch;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &pitch);
   stepSizeUm_ = pitch/819.2;
   ret = CreateProperty("StepSize [um]", CDeviceUtils::ConvertToString(stepSizeUm_), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;

   // switch to mm/s
   ret = SendCommand("!dim z 9");
   if (ret != DEVICE_OK) return ret;

   ret = QueryCommand("?vel z", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   pAct = new CPropertyAction (this, &ZStage::OnSpeed);
   ret = CreateProperty("SpeedZ [mm/s]", resp.c_str(), MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("SpeedZ [mm/s]", 0.001, 100.0); // mm/s


   // Accel (Acceleration (in m/s²)
   // -----
   ret = QueryCommand("?accel z", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   pAct = new CPropertyAction (this, &ZStage::OnAccel);
   ret = CreateProperty("Acceleration Z [m/s^2]", resp.c_str(), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("Acceleration Z [m/s^2]", 0.01, 2.0);
   
   // Backlash (in µm)
   // get current Backlash from the controller
   ret = QueryCommand("?backlash z", resp);
   if (ret == DEVICE_OK)
   {
      pAct = new CPropertyAction (this, &ZStage::OnBacklash);
      ret = CreateProperty("Backlash Z [um]", resp.c_str(), MM::Float, false, pAct);
      if (ret != DEVICE_OK) return ret;
   }


   // verify if sequenceable e.g. if snapshot buffer is configured
   if ((Configuration_ & 0x0800) == 0x0800)
   {
	  sequenceable_ = true;
	  CPropertyAction* pAct = new CPropertyAction (this, &ZStage::OnSequence);
	  const char* spn = "Use Sequence";
	  CreateProperty(spn, "No", MM::String, false, pAct);
      AddAllowedValue(spn, "No");
	  AddAllowedValue(spn, "Yes");
   }


   ret = UpdateStatus();
   if (ret != DEVICE_OK) return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int ZStage::Shutdown()
{
   initialized_    = false;
   range_measured_ = false;
   return DEVICE_OK;
}

bool ZStage::Busy()
{
  string resp;
  int ret;

   // send command
   ret = QueryCommand("?statusaxis", resp);
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
   ret = SendCommand("a z");
   return ret;
}


int ZStage::SetPositionUm(double pos)
{
   ostringstream os;
   os << "ZStage::SetPositionUm() " << pos;
   this->LogMessage(os.str().c_str());

   // switch to µm
   int ret = SendCommand("!dim z 1");
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "!moa z " << pos;

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK)
     return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}



int ZStage::SetRelativePositionUm(double d)
{
   // switch to µm
   int ret = SendCommand("!dim z 1");
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!mor z " << d;

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
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
   // switch to µm
   ret = SendCommand("!dim z 1");
   if (ret != DEVICE_OK)
      return ret;

   string resp;
   ret = QueryCommand("?pos z", resp);
   if (ret != DEVICE_OK) return ret;
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
   ret = SendCommand("!dim z 0");
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "MOA Z " << pos;

   string resp;
   ret = QueryCommand(cmd.str().c_str(), resp);
   if (ret != DEVICE_OK) return ret;
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
   // switch to steps
   ret = SendCommand("!dim z 0");
   if (ret != DEVICE_OK)
      return ret;

   string resp;
   ret = QueryCommand("?pos z", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   istringstream is(resp);
   is >> steps;
   return DEVICE_OK;
}
  
int ZStage::SetAdapterOrigin()
{
   double zz;
   int ret = GetPositionUm(zz);
   if (ret != DEVICE_OK)
      return ret;
   originZ_ = zz;

   return DEVICE_OK;
}


int ZStage::SetOrigin()
{
   int ret = SendCommand("!pos z 0");
   if (ret != DEVICE_OK) return ret;
  
   string resp;
   ret = QueryCommand("?status", resp);
   if (ret != DEVICE_OK) return ret;

   if (resp.compare("OK...") != 0)
   {
      return DEVICE_SERIAL_INVALID_RESPONSE;
   }

   return SetAdapterOrigin();
}


/**
 * Defines current position as (d) coordinate of the controller.
 */
int ZStage::SetAdapterOriginUm(double d)
{
   // switch to steps
   int ret = SendCommand("!dim z 1");
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!pos z "<< d;
   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}




int ZStage::Move(double v)
{
   // switch to mm/s
   int ret = SendCommand("!dim z 9");
   if (ret != DEVICE_OK) return ret;

   // if v is not in mm/s then please convert here to mm/s
   double v_ = v;

   // format the command
   ostringstream cmd;
   cmd << "!speed z "<< v_;
   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}



/**
 * Returns the stage position limits in um.
 */
int ZStage::GetLimits(double& min, double& max)
{
  if (!range_measured_) return DEVICE_UNKNOWN_POSITION;

  // switch to µm
  int ret = SendCommand("!dim z 1");
  if (ret != DEVICE_OK) return ret;

  string resp;
  ret = QueryCommand("?lim z", resp);
  if (ret != DEVICE_OK) return ret;
  if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

  float lower, upper;
  char iBuf[256];
  strcpy(iBuf,resp.c_str());
  sscanf(iBuf, "%f %f\r", &lower, &upper);
  min = lower;
  max = upper;

  return DEVICE_OK;
}


int ZStage::StartStageSequence()
{
   ostringstream cmd;
   string resp;
   int err;

   cmd << "!snsi 0";   // ensure that ringbuffer pointer points to first entry and that we only trigger the Z axis
   int ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;


   cmd << "!axis 0 0"; //prevent XY from moving. So far no XY sequence is stored and this fast mode is for Z-stack application only

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   istringstream is2(resp);
   is2 >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;


   cmd << "!snsm 4";   // use Tango snapshot mode 4 to TTL step through list

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   istringstream is3(resp);
   is3 >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;


   return DEVICE_OK;
}


int ZStage::StopStageSequence() 
{
   ostringstream cmd;
   cmd << "!sns 0"; //disable snapshot mode

   int ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;


   cmd << "!axis 1 1"; //enable XY again

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   istringstream is2(resp);
   is2 >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}



int ZStage::SendStageSequence()
{
   // first stop any pending actions
   ostringstream cmd;
   cmd << "!snsm 0";
   int ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

   // clear the buffer in the device
   // format the command
   cmd << "!snsa 0";
   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   istringstream is2(resp);
   is2 >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

   // switch to µm
   ret = SendCommand("!dim z 1");
   if (ret != DEVICE_OK) return ret;

   ostringstream os;
   os.precision(1);
   for (unsigned i=0; i< sequence_.size(); i++)
   {
      os << "!snsa z " << sequence_[i]; 
      ret = SendCommand(os.str().c_str());
      if (ret != DEVICE_OK) return ret;

      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK) return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      istringstream is3(resp);
      is3 >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   }

   return DEVICE_OK;
}



int ZStage::ClearStageSequence()
{
   sequence_.clear();

   // clear the buffer in the device

   // format the command
   ostringstream cmd;
   cmd << "!snsa 0";
   int ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}



int ZStage::AddToStageSequence(double position)
{
   sequence_.push_back(position);

   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int ZStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}

int ZStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // switch to mm/s
      int ret = SendCommand("!dim z 9");
      if (ret != DEVICE_OK) return ret;

	  string resp;
      ret = QueryCommand("?vel z", resp);
      if (ret != DEVICE_OK) return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double speed = atof(tokens.at(0).c_str());
      speedZ_ = speed;
      pProp->Set(speedZ_);
   }
   else if (eAct == MM::AfterSet)
   {
      // switch to mm/s
      int ret = SendCommand("!dim z 9");
      if (ret != DEVICE_OK) return ret;

	  double uiSpeed; // Speed in mm/sec
      pProp->Get(uiSpeed);
      double speed = uiSpeed;

      ostringstream cmd;
      cmd << "!vel z " << speed;
	  ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

      speedZ_ = speed;
   }
   return DEVICE_OK;
}


int ZStage::OnAccel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = QueryCommand("?accel z", resp);
      if (ret != DEVICE_OK) return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double accel = atof(tokens.at(0).c_str());
      accelZ_ = accel;
      pProp->Set(accelZ_);
   }
   else if (eAct == MM::AfterSet)
   {
      double accel;
      pProp->Get(accel);
      if (accel < 0.001)  accel =  0.001; //clipping to useful values
      if (accel > 10.0 )  accel = 10.0;

	  ostringstream cmd;
      cmd << "!accel z " << accel;
	  int ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

      accelZ_ = accel;
   }
   return DEVICE_OK;
}


int ZStage::OnBacklash(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = QueryCommand("?backlash z", resp);
      if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double backlash_ = atof(tokens.at(0).c_str());
      pProp->Set(backlash_);
   }

   else if (eAct == MM::AfterSet)
   {
      double backlash;
      pProp->Get(backlash);
      if (backlash <  0.0) backlash =  0.0; //clipping to useful values
      if (backlash > 10.0) backlash = 10.0;

	  ostringstream cmd;
      cmd << "!backlash z " << backlash;
	  int ret = SendCommand(cmd.str().c_str());
      if (ret !=DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return DEVICE_UNSUPPORTED_COMMAND;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   }
   return DEVICE_OK;
}


int ZStage::OnSequence(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (sequenceable_)
         pProp->Set("Yes");
      else
         pProp->Set("No");
   }
   else if (eAct == MM::AfterSet)
   {
      std::string prop;
      pProp->Get(prop);
      sequenceable_ = false;
      if (prop == "Yes")
         sequenceable_ = true;
   }

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
// A - Stage
///////////////////////////////////////////////////////////////////////////////

/**
 * Single axis stage.
 */
AStage::AStage() :
   TangoBase(this),
   range_measured_(false),
   speed_(20.0), //[mm/s]
   accel_(0.2), //[m/s²]
   origin_(0),
   stepSizeUm_(0.1)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_AStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Tango A axis driver", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &AStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

AStage::~AStage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void AStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_AStageDeviceName);
}


/*
MM::DeviceDetectionStatus AStage::DetectDevice(void)
{

   return TangoCheckSerialPort(*this,*GetCoreCallback(), port_, answerTimeoutMs_);
}
*/

int AStage::Initialize()
{
   core_ = GetCoreCallback();

   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK) 
      return ret;

   int NumberOfAxes = (Configuration_ >> 4) &0x0f;
   if (NumberOfAxes < 4) return DEVICE_NOT_CONNECTED; 

   // set property list
   // -----------------
   // Step size
   // ---------
   CPropertyAction* pAct = new CPropertyAction (this, &AStage::OnStepSize);
   // get current step size from the controller
   string resp;
   ret = QueryCommand("?pitch a", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   float pitch;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &pitch);
   stepSizeUm_ = pitch/819.2;
   ret = CreateProperty("StepSize [um]", CDeviceUtils::ConvertToString(stepSizeUm_), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;

   // switch to mm/s
   ret = SendCommand("!dim a 9");
   if (ret != DEVICE_OK) return ret;

   ret = QueryCommand("?vel a", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   pAct = new CPropertyAction (this, &AStage::OnSpeed);
   ret = CreateProperty("SpeedA [mm/s]", resp.c_str(), MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("SpeedA [mm/s]", 0.001, 100.0); // mm/s


   // Accel (Acceleration (in m/s²)
   // -----
   ret = QueryCommand("?accel a", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   pAct = new CPropertyAction (this, &AStage::OnAccel);
   ret = CreateProperty("Acceleration A [m/s^2]", resp.c_str(), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("Acceleration A [m/s^2]", 0.01, 2.0);
   
   // Backlash (in µm)
   // get current Backlash from the controller
   ret = QueryCommand("?backlash a", resp);
   if (ret == DEVICE_OK)
   {
      pAct = new CPropertyAction (this, &AStage::OnBacklash);
      ret = CreateProperty("Backlash Z [um]", resp.c_str(), MM::Float, false, pAct);
      if (ret != DEVICE_OK) return ret;
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK) return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int AStage::Shutdown()
{
   initialized_    = false;
   range_measured_ = false;
   return DEVICE_OK;
}

bool AStage::Busy()
{
  string resp;
  int ret;

   // send command
   ret = QueryCommand("?statusaxis", resp);
   if (ret != DEVICE_OK)
   {
      ostringstream os;
      os << "SendSerialCommand failed in AStage::Busy, error code:" << ret;
      this->LogMessage(os.str().c_str(), false);
      // return false; // can't write, continue just so that we can read an answer in case write succeeded even though we received an error
   }

   if (resp[3] == 'M') return true;
   else return false;
}


int AStage::Stop()
{
   int ret;
   ret = SendCommand("a a");
   return ret;
}


int AStage::SetPositionUm(double pos)
{
   ostringstream os;
   os << "AStage::SetPositionUm() " << pos;
   this->LogMessage(os.str().c_str());

   // switch to µm
   int ret = SendCommand("!dim a 1");
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "!moa a " << pos;

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK)
     return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}



int AStage::SetRelativePositionUm(double d)
{
   // switch to µm
   int ret = SendCommand("!dim a 1");
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "!mor a " << d;

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK)
     return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}



int AStage::GetPositionUm(double& pos)
{
int ret;
   // switch to µm
   ret = SendCommand("!dim a 1");
   if (ret != DEVICE_OK)
      return ret;

   string resp;
   ret = QueryCommand("?pos a", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   float aa;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &aa);
   pos = aa;

   ostringstream os;
   os << "AStage::GetPositionUm() " << pos;
   this->LogMessage(os.str().c_str());
   return DEVICE_OK;
}
  
int AStage::SetPositionSteps(long pos)
{
int ret;
   // switch to steps
   ret = SendCommand("!dim a 0");
   if (ret != DEVICE_OK)
      return ret;

   // format the command
   ostringstream cmd;
   cmd << "MOA A " << pos;

   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK)
      return ret;
  
   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}
  
int AStage::GetPositionSteps(long& steps)
{
int ret;
   // switch to steps
   ret = SendCommand("!dim a 0");
   if (ret != DEVICE_OK)
      return ret;

   string resp;
   ret = QueryCommand("?pos a", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   istringstream is(resp);
   is >> steps;
   return DEVICE_OK;
}
  
int AStage::SetAdapterOrigin()
{
   double aa;
   int ret = GetPositionUm(aa);
   if (ret != DEVICE_OK)
      return ret;
   origin_ = aa;

   return DEVICE_OK;
}


int AStage::SetOrigin()
{
   int ret = SendCommand("!pos a 0");
   if (ret != DEVICE_OK) return ret;
  
   string resp;
   ret = QueryCommand("?status", resp);
   if (ret != DEVICE_OK) return ret;

   if (resp.compare("OK...") != 0)
   {
      return DEVICE_SERIAL_INVALID_RESPONSE;
   }

   return SetAdapterOrigin();
}


/**
 * Defines current position as (d) coordinate of the controller.
 */
int AStage::SetAdapterOriginUm(double d)
{
   // switch to steps
   int ret = SendCommand("!dim a 1");
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!pos a "<< d;
   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}




int AStage::Move(double v)
{
   // switch to mm/s
   int ret = SendCommand("!dim a 9");
   if (ret != DEVICE_OK) return ret;

   // if v is not in mm/s then please convert here to mm/s
   double v_ = v;

   // format the command
   ostringstream cmd;
   cmd << "!speed a "<< v_;
   ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}



/**
 * Returns the stage position limits in um.
 */
int AStage::GetLimits(double& min, double& max)
{
  if (!range_measured_) return DEVICE_UNKNOWN_POSITION;

  // switch to µm
  int ret = SendCommand("!dim a 1");
  if (ret != DEVICE_OK) return ret;

  string resp;
  ret = QueryCommand("?lim a", resp);
  if (ret != DEVICE_OK) return ret;
  if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

  float lower, upper;
  char iBuf[256];
  strcpy(iBuf,resp.c_str());
  sscanf(iBuf, "%f %f\r", &lower, &upper);
  min = lower;
  max = upper;

  return DEVICE_OK;
}





///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int AStage::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}


int AStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // switch to mm/s
      int ret = SendCommand("!dim a 9");
      if (ret != DEVICE_OK) return ret;

	  string resp;
      ret = QueryCommand("?vel a", resp);
      if (ret != DEVICE_OK) return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double speed = atof(tokens.at(0).c_str());
      speed_ = speed;
      pProp->Set(speed_);
   }
   else if (eAct == MM::AfterSet)
   {
      // switch to mm/s
      int ret = SendCommand("!dim a 9");
      if (ret != DEVICE_OK) return ret;

	  double uiSpeed; // Speed in mm/sec
      pProp->Get(uiSpeed);
      double speed = uiSpeed;

      ostringstream cmd;
      cmd << "!vel a " << speed;
	  ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

      speed_ = speed;
   }
   return DEVICE_OK;
}


int AStage::OnAccel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = QueryCommand("?accel a", resp);
      if (ret != DEVICE_OK) return ret;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double accel = atof(tokens.at(0).c_str());
      accel_ = accel;
      pProp->Set(accel_);
   }
   else if (eAct == MM::AfterSet)
   {
      double accel;
      pProp->Get(accel);
      if (accel < 0.001)  accel =  0.001; //clipping to useful values
      if (accel > 10.0 )  accel = 10.0;

	  ostringstream cmd;
      cmd << "!accel a " << accel;
	  int ret = SendCommand(cmd.str().c_str());
      if (ret != DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return ret;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;

      accel_ = accel;
   }
   return DEVICE_OK;
}


int AStage::OnBacklash(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = QueryCommand("?backlash a", resp);
      if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

	  // tokenize on space:
      stringstream ss(resp);
      string buf;
      vector<string> tokens;
      while (ss >> buf)
         tokens.push_back(buf);
      double backlash_ = atof(tokens.at(0).c_str());
      pProp->Set(backlash_);
   }

   else if (eAct == MM::AfterSet)
   {
      double backlash;
      pProp->Get(backlash);
      if (backlash <  0.0) backlash =  0.0; //clipping to useful values
      if (backlash > 10.0) backlash = 10.0;

	  ostringstream cmd;
      cmd << "!backlash a " << backlash;
	  int ret = SendCommand(cmd.str().c_str());
      if (ret !=DEVICE_OK) return ret;

      string resp;
      ret = QueryCommand("?err", resp);
      if (ret != DEVICE_OK)  return DEVICE_UNSUPPORTED_COMMAND;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   }
   return DEVICE_OK;
}




///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int AStage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
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
   TangoBase(this),
   name_(g_ShutterName)
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

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

/*
MM::DeviceDetectionStatus Shutter::DetectDevice(void)
{

   return TangoCheckSerialPort(*this,*GetCoreCallback(), port_, answerTimeoutMs_);
}
*/

int Shutter::Initialize()
{
   core_ = GetCoreCallback();

   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK) 
      return ret;

   // set property list
   // -----------------
   
   
   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);

   // get current Shutter state from the controller
   string resp;
   ret = QueryCommand("?shutter", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   ret = CreateProperty(MM::g_Keyword_State, resp.c_str(), MM::Integer, false, pAct);
   if (ret != DEVICE_OK) return ret;

   AddAllowedValue(MM::g_Keyword_State, "0"); // low  = closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // high = open

   ret = UpdateStatus();
   if (ret != DEVICE_OK) return ret;

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
   int ret = SendCommand(cmd.str().c_str());
   if (ret !=DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
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
   // request shutter status
   string resp;
   int ret = QueryCommand("?shutter", resp);
   if (ret !=DEVICE_OK) return ret;
   if (resp.size() != 1)
      return DEVICE_SERIAL_INVALID_RESPONSE;

   int x = atoi(resp.substr(1,2).c_str());
   if      (x == 0) state = false;
   else if (x == 1) state = true;
   else return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int Shutter::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}


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
// Tango LED100
///////////////////////////////////////////////////////////////////////////////
LED100::LED100 (const char* name, int id) :
   TangoBase(this),
   name_ (name),
   id_(id), 
   intensity_(0),
   fireT_(0)
{
   InitializeDefaultErrorMessages();

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &LED100::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

LED100::~LED100()
{
   Shutdown();
}

void LED100::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int LED100::GetIntensity(double& intensity)
{
   // request lamp intensity
   ostringstream  cmd;
   cmd << "?anaout c " << id_;

   string resp;
   int ret = QueryCommand(cmd.str().c_str(), resp);
   if (ret !=DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   float percent;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &percent);

   intensity = percent;

   return DEVICE_OK;
}


int LED100::SetIntensity(double intensity)
{
   // format the command
   ostringstream  cmd;
   cmd << "!anaout c " << id_ << " " << intensity;
   int ret = SendCommand(cmd.str().c_str());
   if (ret !=DEVICE_OK)
      return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}


/*
MM::DeviceDetectionStatus LED100::DetectDevice(void)
{

   return TangoCheckSerialPort(*this,*GetCoreCallback(), port_, answerTimeoutMs_);
}
*/

int LED100::Initialize()
{
   core_ = GetCoreCallback();

   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK) 
      return ret;
   CPropertyAction* pAct;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Tango LED100", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // get current intensity from the controller
   double intensity;
   ret = GetIntensity(intensity);
   if (ret == DEVICE_OK)
   {
     pAct = new CPropertyAction(this, &LED100::OnIntensity);
     CreateProperty("Intensity", CDeviceUtils::ConvertToString(intensity_), MM::Float, false, pAct);
     SetPropertyLimits("Intensity", 0.0, 100.0); // [0..100] percent (= [0..10V])

	 // State
     pAct = new CPropertyAction (this, &LED100::OnState);
     if (intensity_ > 0) ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct); 
     else                ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
     if (ret != DEVICE_OK) return ret; 
     AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
     AddAllowedValue(MM::g_Keyword_State, "1"); // Open
   }


   // Fire
   // create property Fire if Tango command flash is possible with this version
   ostringstream  cmd;
   cmd << "!flash -0.01"; //example 10µs pulse
   ret = SendCommand(cmd.str().c_str());
   if (ret !=DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;
   if (err == 0)
   {
     pAct = new CPropertyAction(this, &LED100::OnFire);
     CreateProperty("Fire", "0.0", MM::Float, false, pAct);
   }


   ret = UpdateStatus();
   if (ret != DEVICE_OK) return ret;

   initialized_ = true;

   return DEVICE_OK;
}


bool LED100::Busy()
{
  return false;
}


int LED100::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}


int LED100::SetOpen(bool open)
{
   ostringstream  cmd;
   if (open) cmd << "!adigout 0 0";
   else      cmd << "!adigout 0 1";

   int ret = SendCommand(cmd.str().c_str());
   if (ret !=DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;
   if (resp != "0")       return DEVICE_SERIAL_INVALID_RESPONSE;

   return DEVICE_OK;
}


int LED100::GetOpen(bool &open)
{
   string resp;
   int ret = QueryCommand("?adigout 0", resp);
   if (ret != DEVICE_OK)  return ret;

   if (resp == "0")
   {
	   open = true;
	   return DEVICE_OK;
   }
   else if (resp == "1")
   {
	   open = false;
	   return DEVICE_OK;
   }
   else return DEVICE_SERIAL_INVALID_RESPONSE;
}


// this supports Tango precision timer output (as used in conjunction with LED100 device)
int LED100::Fire(double deltaT) //assume unit is [ms]
{
	ostringstream  cmd;
    int ret;
    string resp;

	double dT = - fabs(deltaT); 	//LED100 hardware uses negative polarity to open shutter (see Tango reference manual). 

    cmd << "!flash " << dT;
    ret = SendCommand(cmd.str().c_str());
    return ret;  
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int LED100::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}


int LED100::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  // get LED100 shutter state from Tango
      bool open;
      GetOpen(open);
      if (open) pProp->Set(1L);
      else      pProp->Set(0L);
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
   }
   return DEVICE_OK;
}


int LED100::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet) {
    int ret = GetIntensity(intensity_);
    if (ret != DEVICE_OK) return ret;
    pProp->Set(intensity_); //(long)
  }
  else if (eAct == MM::AfterSet) {
    double intensity;
    pProp->Get(intensity);
    int ret = SetIntensity(intensity);
    if (ret != DEVICE_OK) return ret;
  }
  return DEVICE_OK;
}


int LED100::OnFire(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(fireT_);
   }
   else if (eAct == MM::AfterSet)
   {
      double fireT;
      pProp->Get(fireT);
	  fireT_ = fireT;
      int ret = Fire(fireT_);
      if (ret != DEVICE_OK) return ret;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Tango DAC
///////////////////////////////////////////////////////////////////////////////
DAC::DAC () :
   TangoBase(this),
   DACPort_(0),
   name_ (g_DACName),
   open_(false)
{
   InitializeDefaultErrorMessages();

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &DAC::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // DAC Port
   pAct = new CPropertyAction (this, &DAC::OnDACPort);
   CreateProperty("DACPort", "0", MM::Integer, false, pAct,true);

   std::vector<std::string> vals; 
   vals.push_back("0");
   vals.push_back("1");
   vals.push_back("2");

   SetAllowedValues("DACPort", vals);

   
   
}

DAC::~DAC()
{
   Shutdown();
}

void DAC::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int DAC::GetSignal(double& volts)
{
   // request DAC intensity
   ostringstream  cmd;
   cmd << "?anaout c " << DACPort_;

   string resp;
   int ret = QueryCommand(cmd.str().c_str(), resp);
   if (ret !=DEVICE_OK) return ret;
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
   int ret = SendCommand(cmd.str().c_str());
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = QueryCommand("?err", resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int err;
   istringstream is(resp);
   is >> err;

   if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   else          return DEVICE_OK;
}


/*
MM::DeviceDetectionStatus DAC::DetectDevice(void)
{
   return TangoCheckSerialPort(*this,*GetCoreCallback(), port_, answerTimeoutMs_);
}
*/

int DAC::Initialize()
{
   core_ = GetCoreCallback();

   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK) 
      return ret;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
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
   if (ret != DEVICE_OK) return ret;

   initialized_ = true;

   return DEVICE_OK;
}

bool DAC::Busy()
{
  return false;
}

int DAC::Shutdown()
{
   initialized_ = false;
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
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int DAC::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}


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
   TangoBase(this),
   name_ (g_ADCName),
   volts_(0.0),
   ADCPort_(0)
{
   InitializeDefaultErrorMessages();

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &ADC::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // ADC Port
   pAct = new CPropertyAction (this, &ADC::OnADCPort);
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

ADC::~ADC()
{
   Shutdown();
}

void ADC::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int ADC::GetSignal(double& volts)
{
   // request ADC value
   ostringstream  cmd;
   cmd << "?anain c " << ADCPort_;

   string resp;
   int ret = QueryCommand(cmd.str().c_str(), resp);
   if (ret !=DEVICE_OK) return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   int raw;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%d\r", &raw);

   volts = (5.0 * raw) / 1023;

   return DEVICE_OK;
}


/*
MM::DeviceDetectionStatus ADC::DetectDevice(void)
{
   return TangoCheckSerialPort(*this,*GetCoreCallback(), port_, answerTimeoutMs_);
}
*/

int ADC::Initialize()
{
   core_ = GetCoreCallback();

   int ret = CheckDeviceStatus();
   if (ret != DEVICE_OK) 
      return ret;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
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
   if (ret != DEVICE_OK) return ret;

   initialized_ = true;

   return DEVICE_OK;
}


int ADC::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}


int ADC::SetGateOpen(bool /*open*/)
{
   return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// Action handlers
// Handle changes and updates to property values.
///////////////////////////////////////////////////////////////////////////////

int ADC::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}


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


