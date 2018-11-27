///////////////////////////////////////////////////////////////////////////////
// FILE:          Hydra.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ITK Hydra Controller Driver
//                XY Stage
//
// AUTHOR:        Steven Fletcher, derived from Corvus adapter written by Johan Henriksson, mahogny@areta.org, derived from Märzhauser adapter
// COPYRIGHT:     Steven Fletcher, 2017
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

// Precision: 15.26 nm in programming mode, 23.7 fm in joystick mode
   //The command "identify" can be used to detect if it is a Hydra device

// Command "st" can check for machine error

// "st" gives 8bit value. bit 0 tells if ready to execute; busy if "1"

//TODO: NEED TO MAKE SURE COMMENDS SEND IN CR + LFKE SURE COMMENDS SEND IN CR + LF

#ifdef WIN32
//   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include <stdio.h>
#include <string>
#include <sstream>
#include "ITKHydra.h"
//#include <math.h>
#include "../../MMDevice/ModuleInterface.h"

// SG Namespace anonymous!

namespace
{

    const char* g_ControllerName    = "ITK Hydra Controller";
    const char* g_XYStageDeviceName = "XY Stage";


    const char* g_Hydra_Reset       = "Reset";
    const char* g_Hydra_Version     = "Version";


    const char* g_TxTerm            = " \n\r"; //unique termination from MM to Hydra communication
    const char* g_RxTerm            = "\r\n"; //unique termination from Hydra to MM communication


} // namespace


using namespace std;

bool g_DeviceHydraAvailable = false;
int  g_NumberOfAxes = 0;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	//change to only one?
   RegisterDevice(g_ControllerName, MM::GenericDevice, "ITK Hydra Controller");
   RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "XY Stage");
}                                                                            

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
   if (deviceName == 0) return 0;
   if (strcmp(deviceName, g_ControllerName)    == 0) return new Hub();                           
   if (strcmp(deviceName, g_XYStageDeviceName) == 0) return new XYStage();
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
   while ((int) read == bufSize)     // SG   cast -> static_cast<int>()                                                
   {                                                                           
      ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read); 
      if (ret != DEVICE_OK)                                                    
         return ret;                                                           
   }                                                                           
   return DEVICE_OK;                                                           
}





///////////////////////////////////////////////////////////////////////////////
// Hub
///////////////////////////////////////////////////////////////////////////////

Hub::Hub() :
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
   ret = CreateProperty(MM::g_Keyword_Description, "Hydra Controller", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Version of the controller:
   const char* cm = "version";
   ret = SendSerialCommand(port_.c_str(), cm, g_TxTerm);
   if (ret != DEVICE_OK) 
      return ret;

   // Read out result
   string response;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, response);
   if (ret != DEVICE_OK) 
      return ret;

   // Create read-only property with version info
   ret = CreateProperty(g_Hydra_Version, response.c_str(), MM::String, true);
   if (ret != DEVICE_OK) 
      return ret;

   // Clear out any errors
   ret = SendSerialCommand(port_.c_str(), "ge", g_TxTerm);
   checkError(string("init"));

/*
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
*/

   initialized_ = true;
   g_DeviceHydraAvailable = true;

   return DEVICE_OK;
}


int Hub::checkError(std::string triedWhat)
{
	int ret = SendSerialCommand(port_.c_str(), "ge", g_TxTerm);
   string response;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, response);

   int errCode;
   sscanf(response.c_str(), "%d\r\n", &errCode);
   if(errCode!=0)
		{
      ostringstream os;
      os << "Got error back from stage ("<<triedWhat<<") error code:" << errCode;
      this->LogMessage(os.str().c_str(), false);
		return 1;
		}
	else
		return 0;
}


int Hub::checkStatus()
{
   int ret = SendSerialCommand(port_.c_str(), "st", g_TxTerm);  // SG Why assigning to ret? and ret value is NEVER used

   if (ret != DEVICE_OK) 
       return ret;

   string response;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, response);

   if (ret != DEVICE_OK) 
      return ret;

   int errCode;
   sscanf(response.c_str(), "%d\r\n", &errCode);
   return errCode;
}


int Hub::Shutdown()
{ 
  initialized_ = false;
  g_DeviceHydraAvailable = false;
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
*/

XYStage::XYStage() :
   initialized_(false),
   range_measured_(false),
   answerTimeoutMs_(1000),
   stepSizeUm_(1.0),

//set speed & accel variables?

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
   CreateProperty(MM::g_Keyword_Description, "Hydra XY stage driver adapter", MM::String, true);


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
 * CHANGE SPEED & ACCELERATION VALUES
 */
int XYStage::Initialize()
{
   if (!g_DeviceHydraAvailable) return DEVICE_NOT_CONNECTED;

   // Speed (in mm/s)
   // -----
   CPropertyAction *pAct = new CPropertyAction (this, &XYStage::OnSpeed);
   // TODO: get current speed from the controller
   int ret = CreateProperty("Speed [mm/s]", "200.0", MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Speed [mm/s]", 0.001, 500.0); // mm/s

   // Accel (Acceleration (in m/s²)
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnAccel);
   // TODO: get current Acceleration from the controller
   ret = CreateProperty("Acceleration [mm/s^2]", "1000", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Acceleration [mm/s^2]", 0.01, 1000.0);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int XYStage::Shutdown()
{
   //SetProperty("Enable joystick?", "True");
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
   ret = GetCommand("st", resp);
   if (ret != DEVICE_OK)
   {
      ostringstream os;
      os << "SendSerialCommand failed in XYStage::Busy, error code:" << ret;
      this->LogMessage(os.str().c_str(), false);
      // return false; // can't write, continue just so that we can read an answer in case write succeeded even though we received an error
   }
	int code;
   sscanf(resp.c_str(), "%d\r\n", &code);
	return (code&1)==1;
}



/**
 * Returns current position in µm.
 */
int XYStage::GetPositionUm(double& x, double& y)
{
int ret;
   PurgeComPort(port_.c_str());
   
   // format the command
   ret = SendSerialCommand(port_.c_str(), "p", g_TxTerm);
   if (ret != DEVICE_OK)
      return ret;
  
   // block/wait for acknowledge, or until we time out;
   string resp;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
   if (ret != DEVICE_OK)  return ret;
   if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

   float xx, yy;
   char iBuf[256]; //?? what is this for
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f %f\r\n", &xx, &yy);
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

   // format the command
   ostringstream cmd;
   cmd << x << " " << y << " m";

   int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret != DEVICE_OK)
     return ret;

   return DEVICE_OK;
}
  

/**
 * Sets position in steps.
 */
int XYStage::SetPositionSteps(long x, long y)
{
   return SetPositionUm(x/stepSizeUm_,y/stepSizeUm_);
}
  

/**
 * Sets relative position in um.
 */
int XYStage::SetRelativePositionUm(double dx, double dy)
{
   ostringstream os;
   os << "XYStage::SetRelativePositionUm() " << dx << " " << dy;
   this->LogMessage(os.str().c_str());

   // format the command
   ostringstream cmd;
   cmd << dx << " " << dy << " " << "r";

   int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret != DEVICE_OK)
     return ret;

   return DEVICE_OK;
}


/**
 * Sets relative position in steps.
 */
int XYStage::SetRelativePositionSteps(long x, long y)
{
	return SetRelativePositionUm(x/stepSizeUm_,y/stepSizeUm_);
}

/**
 * Returns current position in steps.
 */
int XYStage::GetPositionSteps(long& x, long& y)
{
	double xx, yy;
	int ret=GetPositionUm(xx,yy);
	x = (long) (xx*stepSizeUm_);
	y = (long) (yy*stepSizeUm_);
	return ret;
}

/**
 * Defines current position as origin (0,0) coordinate of the controller.
 */
int XYStage::SetOrigin()
{
   PurgeComPort(port_.c_str());

   int ret = SendSerialCommand(port_.c_str(), "0 1 setnpos 0 2 setnpos", g_TxTerm);
   if (ret != DEVICE_OK) return ret;
 
   // raises problems when none should (i think)
   /*
   string resp;
   ret = GetCommand("?status", resp);
   if (ret != DEVICE_OK) return ret;  // NOT DEVICE_OK HYDRA_OK TO DEFINE!

   if (resp.compare("OK...") != 0)
   {
      return DEVICE_SERIAL_INVALID_RESPONSE; 
   }
	*/
   return SetAdapterOrigin();
   
}
//not sure i understand
/**
 * Defines current position as origin (0,0) coordinate of our coordinate system
 * Get the current (stage-native) XY position
 * This is going to be the origin in our coordinate system
 * The Hydra stage X axis is the same orientation as out coordinate system, the Y axis is reversed   ?????????????TODO
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


/**
 *
 */
int XYStage::Home()
{
   const char* cmd;
   int ret;

   range_measured_ = false;

   PurgeComPort(port_.c_str()); // SG no error code ?

   // format the command
   // not sure what ncal does : supposed to do : homing (search limit reverse) 
   cmd = "ncal"; //was 'cal' is ncla good replacement?
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

   cmd = "1 nrm"; //was 'rm' is nrm the correct replacement? 
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

/**
 * Stop current movement
 */
int XYStage::Stop()
{
   int ret;
   PurgeComPort(port_.c_str());
   ret = SendSerialCommand(port_.c_str(), "1 nabort 2 nabort", g_TxTerm);
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

  // format the command
  const char* cmd = "1 getnlimit";
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
  sscanf(iBuf, "%f %f\r\n", &lower, &upper);
  xMin = lower;
  xMax = upper;

  cmd = "2 getnlimit";
  ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
  if (ret != DEVICE_OK) return ret;

  // block/wait for acknowledge, or until we time out;
  //string resp;
  ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
  if (ret != DEVICE_OK)  return ret;
  if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

  //float lower, upper;
  //char iBuf[256];
  strcpy(iBuf,resp.c_str());
  sscanf(iBuf, "%f %f\r\n", &lower, &upper);
  yMin = lower;
  yMax = upper;

  // block/wait for acknowledge, or until we time out;
  //string resp;
  ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
  if (ret != DEVICE_OK)  return ret;
  if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

  return DEVICE_OK;
}

/**
 * Get range which stage can move within
 */
int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
	double dxMin, dxMax, dyMin, dyMax;
	int ret=GetLimitsUm(dxMin, dxMax, dyMin, dyMax);
	xMin = (long) (dxMin*stepSizeUm_);
    xMax = (long) (dxMax*stepSizeUm_);
	yMin = (long) (dyMin*stepSizeUm_);
	yMax = (long) (dyMax*stepSizeUm_);
	return ret;
}

///////////////////////////////////////////////////////////////////////////////
// Internal, helper methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sends a specified command to the controller NOT SURE WE NEED TO WAIT to send more commands
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
      if( bufLen <= curIdx )
         return DEVICE_SERIAL_BUFFER_OVERRUN;
      if (DEVICE_OK != ReadFromComPort(port_.c_str(), reinterpret_cast<unsigned char *> (answer + curIdx), bufLen - curIdx, read))
         return DEVICE_SERIAL_COMMAND_FAILED;
      curIdx += read;

      // look for the end
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

/*
 * Speed as returned by device is in mm/s
 */
int XYStage::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	string resp;
      int ret = GetCommand("gv", resp);
      if (ret != DEVICE_OK)
         return ret;

	float speed;
	char iBuf[256];
   	strcpy(iBuf,resp.c_str());
	sscanf(iBuf, "%f\r\n", &speed);

      speed_ = speed;
      pProp->Set(speed_);
   }
   else if (eAct == MM::AfterSet)
   {
	  double uiSpeed; // Speed in mm/sec
      pProp->Get(uiSpeed);
      double speed = uiSpeed;

      ostringstream cmd;
      cmd << (speed) << " sv";
	  int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
      if (ret != DEVICE_OK)
         return ret;

      speed_ = speed;
   }
   return DEVICE_OK;
}



int XYStage::OnAccel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = GetCommand("ga", resp);
      if (ret != DEVICE_OK) return ret;

   float faccel;
   char iBuf[256];
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r\n", &faccel);
   accel_ = faccel;
      pProp->Set(accel_);
   }
   else if (eAct == MM::AfterSet)
   {
      double accel;
      pProp->Get(accel);
      if (accel < 0.001)  accel =  0.001; //clipping to useful values
      if (accel > 1000.0 )  accel = 1000.0;

	  ostringstream cmd;
      cmd << accel << " sa";
	  int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
      if (ret != DEVICE_OK)
         return ret;

      accel_ = accel;
   }
   return DEVICE_OK;
}
