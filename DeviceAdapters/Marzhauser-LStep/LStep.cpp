///////////////////////////////////////////////////////////////////////////////
// FILE:          LStep.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Marzhauser L-Step Controller Driver
//                XY Stage
//                
//
// AUTHORS:			Original Marzhauser Tango adapter code by Falk Dettmar, falk.dettmar@marzhauser-st.de, 09/04/2009
//					Modifications for Marzhauser L-Step controller by Gilles Courtand, gilles.courtand@u-bordeaux.fr, 
//					and Brice Bonheur brice.bonheur@u-bordeaux.fr 08/03/2012
//
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

#include "LStep.h"
#include <string>
//#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>



const char* g_ControllerName    = "L-Step Controller";
const char* g_XYStageDeviceName = "XY Stage";




const char* g_Lstep_Reset       = "Reset";

const char* g_Lstep_Version     = "Version";

const char* g_TxTerm            = "\r"; //unique termination from MM to Tango communication //carriage return idem L-step
const char* g_RxTerm            = "\r"; //unique termination from Tango to MM communication


using namespace std;


bool g_DeviceLstepAvailable = false;
int  g_NumberOfAxes = 0;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
  
	AddAvailableDeviceName(g_ControllerName,    "L-Step Controller");             
    AddAvailableDeviceName(g_XYStageDeviceName, "XY Stage");
   
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
   while ((int) read == bufSize)                                                     
   {                                                                           
      ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read); 


      if (ret != DEVICE_OK) return ret;                                                           
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
   int ret = ClearPort(); //*this, *GetCoreCallback(), port_);


   if (ret != DEVICE_OK) return ret;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, g_ControllerName, MM::String, true);
   if (DEVICE_OK != ret) return ret;

   // Description
   
	ret = CreateProperty(MM::g_Keyword_Description, "L-Step Controller", MM::String, true);
    if (DEVICE_OK != ret) return ret;





   // Version of the controller:

   //const char* cm = "?version";
   const char* cm = "?ver"; 

   ret = SendSerialCommand(port_.c_str(), cm, g_TxTerm);
   if (ret != DEVICE_OK) return ret;

   // Read out result
   string response;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, response);
   if (ret != DEVICE_OK) return ret;

   // Create read-only property with version info

   //ret = CreateProperty(g_Tango_Version, response.c_str(), MM::String, true);
   ret = CreateProperty(g_Lstep_Version, response.c_str(), MM::String, true);
   if (ret != DEVICE_OK) return ret;

   ret = SendSerialCommand(port_.c_str(), "!autostatus 0", g_TxTerm);
   if (ret != DEVICE_OK) return ret;
/*
	possible problem when setting up the RS232 connexion Cf doc L-step
*/

   ret = SendSerialCommand(port_.c_str(), "?det", g_TxTerm); //det : read out detailed Firmware version number
   if (ret != DEVICE_OK) return ret;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, response);
   if (ret != DEVICE_OK) return ret;
   if (response.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;
   istringstream is(response);
   int udet;
   is >> udet;
   g_NumberOfAxes = (udet >> 4) &0x0f;

   if (g_NumberOfAxes == 2)
   {
     ret = SendSerialCommand(port_.c_str(), "!encpos 1 1", g_TxTerm); //display the position of the detected encoder
     if (ret != DEVICE_OK) return ret;
   }
   else if (g_NumberOfAxes > 2)
   {
     ret = SendSerialCommand(port_.c_str(), "!encpos 1 1 1", g_TxTerm);
     if (ret != DEVICE_OK) return ret;
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK) return ret;

   initialized_ = true;
   g_DeviceLstepAvailable = true;
   return DEVICE_OK;
}


int Hub::Shutdown()
{ 
  initialized_ = false;
  g_DeviceLstepAvailable = false;
  return DEVICE_OK;
}



// Communication "clear buffer" utility function:
int  Hub::ClearPort(void)
{
   // Clear contents of serial port
   const int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while ((int) read == bufSize)
   {
      ret = ReadFromComPort(port_.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;                                                           
} 


/*
int SendCommand(const char *command)
{
   std::string base_command = command;
   int ret = SendSerialCommand(port_.c_str(), base_command.c_str(), g_TxTerm);
   return ret;
}


// Communication "send & receive" utility function:
int Hub::QueryCommand(const char *command, std::string &answer) const
{
   // send command
   int ret = SendCommand(command);
   if (ret != DEVICE_OK) return ret;

   // block/wait for acknowledge (or until we time out)
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, answer);
   return ret;
}
*/

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

   stepSizeXUm_(0.0012), //=1000*pitch[mm]/gear/(motorsteps*4096)  (assume gear=1 motorsteps=200 => stepsize=pitch/819.2)
   stepSizeYUm_(0.0012),
 /*old
	stepSizeXUm_(0.01), //  1000 * pitch/819200 
    stepSizeYUm_(0.01), //  1000 * pitch/819200 
 */
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
 
	CreateProperty(MM::g_Keyword_Description, "L-Step XY stage driver adapter", MM::String, true);

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
   string resp;

	 if (!g_DeviceLstepAvailable) return DEVICE_NOT_CONNECTED;

   // Step size
   // ---------
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnStepSizeX);
   // get current step size from the controller
   int ret = GetCommand("?pitch x", resp);
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
   ret = GetCommand("?pitch y", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   strcpy(iBuf,resp.c_str());
   sscanf(iBuf, "%f\r", &pitch);
   stepSizeYUm_ = pitch/819.2;
   ret = CreateProperty("StepSizeY [um]", CDeviceUtils::ConvertToString(stepSizeYUm_), MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;

//********************************************************************************************************************************************speed
   // Speed (in mm/s)
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnSpeedX);

   // switch to mm/s
   //ret = SendSerialCommand(port_.c_str(), "!dim x 9", g_TxTerm); //difference to "dim 2" : dim 9 = all velocity instructions in mm/s
	ret = SendSerialCommand(port_.c_str(), "!dim x 2", g_TxTerm);	//velocity (vel) in r/s (motor revolution)
    if (ret != DEVICE_OK) return ret;
/*
   ret = GetCommand("?vel x", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   ret = CreateProperty("SpeedX [mm/s]", resp.c_str(), MM::Float, false, pAct); // mm/s
*/
   //conversion revolution/s to mm/s
   ret = GetCommand("?vel x", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   float velocity;
   char iBufString[256];
   strcpy(iBufString,resp.c_str());
   sscanf(iBufString, "%f\r", &velocity);
   velocityXmm_ = velocity*2; //motor revolution = 2mm
   ret = CreateProperty("SpeedX [mm/s]", CDeviceUtils::ConvertToString(velocityXmm_), MM::Float, false, pAct); // mm/s
   if (ret != DEVICE_OK) return ret;



   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("SpeedX [mm/s]", 0.001, 100.0); // mm/s
//G//	SetPropertyLimits("SpeedX [mm/s]", 0.002, 80.0); // mm/s

   pAct = new CPropertyAction (this, &XYStage::OnSpeedY);

   // switch to mm/s
 
  //ret = SendSerialCommand(port_.c_str(), "!dim y 9", g_TxTerm);
  ret = SendSerialCommand(port_.c_str(), "!dim y 2", g_TxTerm);
 
   if (ret != DEVICE_OK) return ret;
/*
   ret = GetCommand("?vel y", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   ret = CreateProperty("SpeedY [mm/s]", resp.c_str(), MM::Float, false, pAct); // mm/s
*/
//conversion revolution/s to mm/s
   ret = GetCommand("?vel y", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;
   strcpy(iBufString,resp.c_str());
   sscanf(iBufString, "%f\r", &velocity);
   velocityYmm_ = velocity*2;
   ret = CreateProperty("SpeedY [mm/s]", CDeviceUtils::ConvertToString(velocityYmm_), MM::Float, false, pAct); // mm/s


   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("SpeedY [mm/s]", 0.001, 100.0); // mm/s
//******************************************************************************************************************************************

   // Accel (Acceleration (in m/s²)
   // -----
   pAct = new CPropertyAction (this, &XYStage::OnAccelX);


   ret = GetCommand("?accel x", resp); //Lstep : 0,01 to 20,00 m/s²
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   ret = CreateProperty("Acceleration X [m/s^2]", resp.c_str(), MM::Float, false, pAct);


   if (ret != DEVICE_OK) return ret;
   SetPropertyLimits("Acceleration X [m/s^2]", 0.01, 2.0);
   
   pAct = new CPropertyAction (this, &XYStage::OnAccelY);


   ret = GetCommand("?accel y", resp);
   if (ret != DEVICE_OK) return DEVICE_UNSUPPORTED_COMMAND;

   ret = CreateProperty("Acceleration Y [m/s^2]", resp.c_str(), MM::Float, false, pAct);
   if (ret != DEVICE_OK) return ret;

   SetPropertyLimits("Acceleration Y [m/s^2]", 0.01, 2.0);


   // Backlash (in µm)
 
 /* command "backlash" not recognized by L-step **********************************************************Mechanical backlash compensation
   ret = GetCommand("?backlash x", resp);
   if (ret == DEVICE_OK)
   {
      pAct = new CPropertyAction (this, &XYStage::OnBacklashX);
	  ret = CreateProperty("Backlash X [um]", resp.c_str(), MM::Float, false, pAct);
      if (ret != DEVICE_OK) return ret;
   }

   ret = GetCommand("?backlash y", resp);
   if (ret == DEVICE_OK)
   {
      pAct = new CPropertyAction (this, &XYStage::OnBacklashY);
	  ret = CreateProperty("Backlash Y [um]", resp.c_str(), MM::Float, false, pAct);
      if (ret != DEVICE_OK) return ret;
   }
*/


/* **********************************************************************************************************Snapshot
   ret = GetCommand("?configsnapshot", resp);
   if (ret == DEVICE_OK)
   {
      if (atoi(resp.c_str()) == 1) sequenceable_ = true;
      else                         sequenceable_ = false;
   }
*/

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


   // switch to µm
   ret = SendSerialCommand(port_.c_str(), "!dim 1 1", g_TxTerm);

   if (ret != DEVICE_OK) return ret;

   // format the command
   const char* cmd = "?pos";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);


   if (ret != DEVICE_OK) return ret;
  
   // block/wait for acknowledge, or until we time out;
   string resp;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);
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
   int ret = SendSerialCommand(port_.c_str(), "!dim 1 1", g_TxTerm);


   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!moa "<< x << " " << y;

   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);


   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = GetCommand("?err", resp);
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
   int ret = SendSerialCommand(port_.c_str(), "!dim 1 1", g_TxTerm);
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!mor "<< dx << " " << dy;

   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = GetCommand("?err", resp);
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
   int ret = SendSerialCommand(port_.c_str(), "!dim 0 0", g_TxTerm);


   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!moa "<< x << " " << y;
   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
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

   // switch to steps
   int ret = SendSerialCommand(port_.c_str(), "!dim 0 0", g_TxTerm);


   if (ret != DEVICE_OK) return ret;

   // format the command
   const char* cmd = "?pos";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);


   if (ret != DEVICE_OK) return ret;
  
   string resp;
   ret = GetSerialAnswer(port_.c_str(), g_RxTerm, resp);


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

   int ret = SendSerialCommand(port_.c_str(), "!pos 0 0", g_TxTerm);
   if (ret != DEVICE_OK) return ret;
  
   string resp;
   ret = GetCommand("?err", resp);
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
   int ret = SendSerialCommand(port_.c_str(), "!dim 1 1", g_TxTerm);
   if (ret != DEVICE_OK) return ret;

   // format the command
   ostringstream cmd;
   cmd << "!pos "<< x << " " << y;
   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
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



int XYStage::Home()
{
   const char* cmd;

   range_measured_ = false;


   // format the command
 /*  cmd = "!cal x";
   int ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK) return ret;

   cmd = "!cal y";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK) return ret;
  */
	cmd = "!cal";
   int ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
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
   while(status && (numTries < maxTries)); // keep trying up to maxTries * pollIntervalMs ( = 20 sec)--->40s?

   ostringstream os;
   os << "Tried reading "<< numTries << " times, and finally read " << status;
   this->LogMessage(os.str().c_str());



   // additional range measure to provide GetLimitsUm()
/*
   cmd = "!rm x";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK) return ret;

   cmd = "!rm y";
   ret = SendSerialCommand(port_.c_str(), cmd, g_TxTerm);
   if (ret != DEVICE_OK) return ret;
*/
	cmd = "!rm";
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

/*  
  int ret = SendSerialCommand(port_.c_str(), "a x", g_TxTerm); //command "a x" not recognized by L-Step

   ret = SendSerialCommand(port_.c_str(), "a y", g_TxTerm);
 */
int ret = SendSerialCommand(port_.c_str(), "!a", g_TxTerm);

   return ret;
}


/**
 * Returns the stage position limits in um.
 */
int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{

  if (!range_measured_) return DEVICE_UNKNOWN_POSITION;


  // switch to µm
  int ret = SendSerialCommand(port_.c_str(), "!dim 1 1", g_TxTerm);
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




int XYStage::Move(double vx, double vy)
{
   // switch to mm/s

 // int ret = SendSerialCommand(port_.c_str(), "!dim 9 9", g_TxTerm);
 int ret = SendSerialCommand(port_.c_str(), "!dim 2 2", g_TxTerm);

   if (ret != DEVICE_OK) return ret;

   // if vx and vy are not in mm/s then please convert here to mm/s
   double vx_ = vx;
   double vy_ = vy;

   // format the command
   ostringstream cmd;
   cmd << "!speed "<< vx_ << " " << vy_;
   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
   if (ret != DEVICE_OK) return ret;

   string resp;
   ret = GetCommand("?err", resp);
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
   int ret = SendSerialCommand(port_.c_str(), "!dim 0 0", g_TxTerm);


   if (ret != DEVICE_OK) return ret;

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

     // get current step size from the controller
     string resp;
     int ret = GetCommand("?pitch x", resp);
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
     int ret = GetCommand("?pitch y", resp);
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
      // switch to mm/s with Tango controller
     // int ret = SendSerialCommand(port_.c_str(), "!dim x 9", g_TxTerm); 
	 int ret = SendSerialCommand(port_.c_str(), "!dim x 2", g_TxTerm);
     if (ret != DEVICE_OK) return ret;

	  string resp;
      ret = GetCommand("?vel x", resp);
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

     // int ret = SendSerialCommand(port_.c_str(), "!dim x 9", g_TxTerm);
	 int ret = SendSerialCommand(port_.c_str(), "!dim x 2", g_TxTerm);
      if (ret != DEVICE_OK) return ret;

	  double uiSpeed; // Speed in mm/sec
      pProp->Get(uiSpeed);
      double speed = uiSpeed;

      ostringstream cmd;
      cmd << "!vel x " << speed;
	  ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
      if (ret != DEVICE_OK) return ret;

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
 
     // int ret = SendSerialCommand(port_.c_str(), "!dim y 9", g_TxTerm);
	 int ret = SendSerialCommand(port_.c_str(), "!dim y 2", g_TxTerm);

      if (ret != DEVICE_OK) return ret;

	  string resp;
      ret = GetCommand("?vel y", resp);


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
     // int ret = SendSerialCommand(port_.c_str(), "!dim y 9", g_TxTerm);
	 int ret = SendSerialCommand(port_.c_str(), "!dim y 2", g_TxTerm);
      
	 if (ret != DEVICE_OK) return ret;

	  double uiSpeed; // Speed in mm/sec
      pProp->Get(uiSpeed);
      double speed = uiSpeed;

      ostringstream cmd;
      cmd << "!vel y " << speed;
	  ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);


      if (ret != DEVICE_OK) return ret;

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

/*
int XYStage::OnBacklashX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      string resp;
      int ret = GetCommand("?backlash x", resp);
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
	  int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
      if (ret !=DEVICE_OK) return ret;

      string resp;
      ret = GetCommand("?err", resp);
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
      int ret = GetCommand("?backlash y", resp);
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
	  int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), g_TxTerm);
      if (ret !=DEVICE_OK) return ret;

      string resp;
      ret = GetCommand("?err", resp);
      if (ret != DEVICE_OK)  return DEVICE_UNSUPPORTED_COMMAND;
      if (resp.length() < 1) return DEVICE_SERIAL_INVALID_RESPONSE;

      int err;
      istringstream is(resp);
      is >> err;
      if (err != 0) return DEVICE_SERIAL_INVALID_RESPONSE;
   }
   return DEVICE_OK;
}
*/


