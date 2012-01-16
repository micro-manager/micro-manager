///////////////////////////////////////////////////////////////////////////////
// FILE:          Thorlabs.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs device adapters: BBD102 Controller
//
// COPYRIGHT:     Thorlabs, 2011
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
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Thorlabs.h"
#include "../../MMDevice/ModuleInterface.h"
#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>

const char* g_XYStageDeviceName = "XYStage";
const char* g_PiezoZStageDeviceName = "PiezoZStage";
const char* g_WheelDeviceName = "FilterWheel";

const char* g_SerialNumberProp = "SerialNumber";
const char* g_ModelNumberProp = "ModelNumber";
const char* g_SWVersionProp = "SoftwareVersion";
const char* g_StepSizeXProp = "StepSizeX";
const char* g_StepSizeYProp = "StepSizeY";
const char* g_MaxVelocityProp = "MaxVelocity";
const char* g_AccelProp = "Acceleration";
const char* g_MoveTimeoutProp = "MoveTimeoutMs";

///////////
// commands
///////////

// hardware info
const unsigned char hardwareInfoCmd[cmdLength] = {0x05, 0x00, 0x00, 0x00, 0x11, 0x01};
const unsigned char hardwareInfoSgn = 0x06;

// enable X axis
const unsigned char enableXCmd[cmdLength] = {0x10, 0x02, 0x01, 0x01, 0x21, 0x01};

// home X
const unsigned char homeXCmd[cmdLength] = {0x43, 0x04, 0x01, 0x00, 0x21, 0x01};
const unsigned char homeXSgn = 0x44;

// get position X
const unsigned char getPositionXCmd[cmdLength] = {0x11, 0x04, 0x01, 0x00, 0x21, 0x01};
const unsigned char getPositionXSgn = 0x12;

// set position X
const unsigned char setPositionXCmd[cmdLength] = {0x53, 0x04, 0x06, 0x00, 0xA1, 0x01};
const unsigned char setPositionXSgn = 0x64;

// set relative position X
const unsigned char setRelPositionXCmd[cmdLength] = {0x45, 0x04, 0x06, 0x00, 0xA1, 0x01};
const unsigned char setRelPositionXSgn = 0x64;

// stop X (immediate)
const unsigned char stopXCmd[cmdLength] = {0x65, 0x04, 0x01, 0x01, 0x21, 0x01};
const unsigned char stopXSgn[2] = {0x04, 0x66};

// get status X
const unsigned char getStatusXCmd[cmdLength] = {0x90, 0x04, 0x01, 0x00, 0x21, 0x01};
const unsigned char getStatusXSgn = 0x91;

// get velocity profile X
const unsigned char getVelocityProfileXCmd[cmdLength] = {0x14, 0x04, 0x01, 0x00, 0x21, 0x01};
const unsigned char getVelocityProfileXSgn = 0x15;

// set velocity profile X
const unsigned char setVelocityProfileXCmd[cmdLength] = {0x13, 0x04, 0x0E, 0x00, 0xA1, 0x01};

using namespace std;
///////////////////////////////////////////////////////////////////////////////
// Utility
///////////////////////////////////////////////////////////////////////////////

/**
 * Clears receive buffer of any content.
 * To be used before sending commands to make sure that we are not catching
 * residual error messages or previous unhandled responses.
 */
int ClearPort(MM::Device& device, MM::Core& core, std::string port)
{
   // Clear contents of serial port 
   const int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   while ((int) read == bufSize)
   {
      int ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

/**
 * Generate Y axis command from the equivalent X axis command
 */
unsigned const char* GenerateYCommand(const unsigned char* xCmd)
{
	static unsigned char buf[cmdLength];
	memcpy(buf, xCmd, cmdLength);
	buf[cmdLength-2] += 1;
	return buf;
}

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_XYStageDeviceName, "Thorlabs BD102 XY Stage");
   AddAvailableDeviceName(g_PiezoZStageDeviceName, "Thorlabs piezo Z Stage");
   AddAvailableDeviceName(g_WheelDeviceName, "Integrated filter wheel");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_XYStageDeviceName) == 0)
   {
      XYStage* xyStage = new XYStage();
      return xyStage;
   }
   if (strcmp(deviceName, g_PiezoZStageDeviceName) == 0)
   {
      PiezoZStage* stage = new PiezoZStage();
      return stage;
   }
   if (strcmp(deviceName, g_WheelDeviceName) == 0)
   {
      IntegratedFilterWheel* wheel = new IntegratedFilterWheel();
      return wheel;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// XYStage class
///////////////////////////////////////////////////////////////////////////////

XYStage::XYStage() :
   CXYStageBase<XYStage>(),
   initialized_(false),
   home_(false),
   port_("Undefined"), 
   answerTimeoutMs_(1000.0),
   moveTimeoutMs_(10000.0),
   cmdThread_(0)
{
   // set default error messages
   InitializeDefaultErrorMessages();

   // set device specific error messages
   SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "Serial port can't be changed at run-time."
                                           " Use configuration utility or modify configuration file manually.");
   SetErrorText(ERR_UNRECOGNIZED_ANSWER, "Invalid response from the device");
   SetErrorText(ERR_UNSPECIFIED_ERROR, "Unspecified error occured.");
   SetErrorText(ERR_HOME_REQUIRED, "Stage must be homed before sending MOVE commands.\n"
      "To home the stage use one of the following options:\n"
      "   Open Tools | XY List... dialog and press 'Calibrate' button\n"
      "       or\n"
      "   Open Scipt panel and execute this line: mmc.home(mmc.getXyStageDevice());");
   SetErrorText(ERR_INVALID_PACKET_LENGTH, "Invalid packet length.");
   SetErrorText(ERR_RESPONSE_TIMEOUT, "Device timed-out: no response received withing expected time interval.");
   SetErrorText(ERR_BUSY, "Device busy.");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Thorlabs BBD102 XY stage adapter", MM::String, true);

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   cmdThread_ = new CommandThread(this);
}

XYStage::~XYStage()
{
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// XY Stage API
// required device interface implementation
///////////////////////////////////////////////////////////////////////////////
void XYStage::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_XYStageDeviceName);
}

int XYStage::Initialize()
{
   // create hardware info properties
   int ret = FillHardwareInfo(); // get information from the controller
   if (ret != DEVICE_OK)
      return ret;

   CreateProperty(g_SerialNumberProp, CDeviceUtils::ConvertToString((long)info_.dwSerialNum), MM::String, true);
   CreateProperty(g_ModelNumberProp, info_.szModelNum, MM::String, true);
   CreateProperty(g_SWVersionProp, CDeviceUtils::ConvertToString((long)info_.dwSoftwareVersion), MM::String, true);

   // check if we are already homed
   DCMOTSTATUS stat;
   ret = GetStatus(stat, X);
   if (ret != DEVICE_OK)
      return ret;

   if (stat.dwStatusBits & 0x00000400)
      home_ = true;

   // check if axes need enabling
   if (!(stat.dwStatusBits & 0x80000000))
   {
      // enable X
      ret = SetCommand(enableXCmd, cmdLength);
      if (ret != DEVICE_OK)
         return ret;

      // enable Y
      ret = SetCommand(GenerateYCommand(enableXCmd), cmdLength);
      if (ret != DEVICE_OK)
         return ret;
   }

   ret = GetStatus(stat, X);
   if (ret != DEVICE_OK)
      return ret;

   ostringstream os;
   os << "Status X axis (hex): " << hex << stat.dwStatusBits;
   LogMessage(os.str(), true);

   // Step size
   CreateProperty(g_StepSizeXProp, CDeviceUtils::ConvertToString(stepSizeUm), MM::Float, true);
   CreateProperty(g_StepSizeYProp, CDeviceUtils::ConvertToString(stepSizeUm), MM::Float, true);

   // Max Speed
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnMaxVelocity);
   CreateProperty(g_MaxVelocityProp, "100.0", MM::Float, false, pAct);
   //SetPropertyLimits(g_MaxVelocityProp, 0.0, 31999.0);

   // Acceleration
   pAct = new CPropertyAction (this, &XYStage::OnAcceleration);
   CreateProperty(g_AccelProp, "100.0", MM::Float, false, pAct);
   //SetPropertyLimits("Acceleration", 0.0, 150);

   // Move timeout
   pAct = new CPropertyAction (this, &XYStage::OnMoveTimeout);
   CreateProperty(g_MoveTimeoutProp, "10000.0", MM::Float, false, pAct);
   //SetPropertyLimits("Acceleration", 0.0, 150);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int XYStage::Shutdown()
{

   if (cmdThread_ && cmdThread_->IsMoving())
   {
      cmdThread_->Stop();
      cmdThread_->wait();
   }

   delete cmdThread_;
   cmdThread_ = 0;

   if (initialized_)
      initialized_ = false;

   return DEVICE_OK;
}

bool XYStage::Busy()
{
   return cmdThread_->IsMoving();
}
 
int XYStage::SetPositionSteps(long x, long y)
{
   if (!home_)
      return ERR_HOME_REQUIRED; 

   if (Busy())
      return ERR_BUSY;

   cmdThread_->StartMove(x, y);
   CDeviceUtils::SleepMs(10); // to make sure that there is enough time for thread to get started

   return DEVICE_OK;   
}
 
int XYStage::SetRelativePositionSteps(long x, long y)
{
   if (!home_)
      return ERR_HOME_REQUIRED; 

   if (Busy())
      return ERR_BUSY;

   cmdThread_->StartMoveRel(x, y);

   return DEVICE_OK;
}

int XYStage::GetPositionSteps(long& x, long& y)
{
   // if not homed just return default
   if (!home_)
   {
      x = 0L;
      y = 0L;
      return DEVICE_OK;
   }

   // send command to X axis
   ClearPort(*this, *GetCoreCallback(), port_);
   int ret = SetCommand(getPositionXCmd, cmdLength);
   if (ret != DEVICE_OK)
      return ret;

   unsigned char answer[cmdLength];
   ret = GetCommand(answer, cmdLength, answerTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0] != getPositionXSgn)
   {
      ostringstream os;
      os << "GetPositionX invalid response: " << hex << answer;
      LogMessage(os.str().c_str(), true);
      return ERR_UNRECOGNIZED_ANSWER;
   }

   // get data packed and parse it
   unsigned short pl(0);
   memcpy(&pl, answer+2, sizeof(short));
   
   const int packetLength = 6;
   if (packetLength != pl)
      return ERR_INVALID_PACKET_LENGTH;

   unsigned char packet[packetLength];
   ret = GetCommand(packet, packetLength, answerTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret;

   long pos = 0;
   memcpy(&pos, packet+2, 4);
   x = pos;

   CDeviceUtils::SleepMs(10);

   // and then to Y
   ret = SetCommand(GenerateYCommand(getPositionXCmd), cmdLength);
   if (ret != DEVICE_OK)
      return ret;

   ret = GetCommand(answer, cmdLength, answerTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0] != getPositionXSgn)
   {
      ostringstream os;
      os << "GetPositionY invalid response: " << hex << answer;
      LogMessage(os.str().c_str(), true);
      return ERR_UNRECOGNIZED_ANSWER;
   }

   // get data packed and parse it
   pl = 0;
   memcpy(&pl, answer+2, sizeof(short));
   
   if (packetLength != pl)
      return ERR_INVALID_PACKET_LENGTH;

   ret = GetCommand(packet, packetLength, answerTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret;

   memcpy(&pos, packet+2, 4);
   y = pos;

   ostringstream os;
   os << "GetPositionSteps(), X=" << x << ", Y=" << y;
   LogMessage(os.str().c_str(), true);

   return DEVICE_OK;
}

/**
 * Performs homing for both axes
 * (required after initialization)
 */
int XYStage::Home()
{
   // send command to X axis
   ClearPort(*this, *GetCoreCallback(), port_);
   int ret = SetCommand(homeXCmd, cmdLength);
   if (ret != DEVICE_OK)
      return ret;

   // send command to Y axis
   ret = SetCommand(GenerateYCommand(homeXCmd), cmdLength);
   if (ret != DEVICE_OK)
      return ret;

   // get the first of the two expected answers (either X or Y)
   unsigned char answer[cmdLength];
   memset(answer, 0, cmdLength);
   ret = GetCommand(answer, cmdLength, moveTimeoutMs_); // 10 sec timeout
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0] != homeXSgn)
      return ERR_UNRECOGNIZED_ANSWER;

   // get second answer
   ret = GetCommand(answer, cmdLength, moveTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0] != homeXSgn)
      return ERR_UNRECOGNIZED_ANSWER;

   home_ = true; // successfully homed

   // check status
   DCMOTSTATUS stat;
   ret = GetStatus(stat, X);
   if (ret != DEVICE_OK)
      return ret;
   
   ostringstream os;
   os << "Status X axis (hex): " << hex << stat.dwStatusBits;
   LogMessage(os.str(), true);

   return DEVICE_OK;
}

/**
 * Stops XY stage immediately. Blocks until done.
 */
int XYStage::Stop()
{
   // send command to X axis
   ClearPort(*this, *GetCoreCallback(), port_);
   int ret = SetCommand(stopXCmd, cmdLength);
   if (ret != DEVICE_OK)
      return ret;

   //...and to Y axis
   ret = SetCommand(GenerateYCommand(stopXCmd), cmdLength);
   if (ret != DEVICE_OK)
      return ret;

   // get two answers
   for (int i=0; i<2; i++)
   {
      unsigned char answer[cmdLength];
      ret = GetCommand(answer, cmdLength, moveTimeoutMs_);
      if (ret != DEVICE_OK)
         return ret;

      if (memcmp(stopXSgn, answer, sizeof(stopXSgn) != 0))
         return ERR_UNRECOGNIZED_ANSWER;

      // get data packed and parse it
      unsigned short pl(0);
      memcpy(&pl, answer+2, sizeof(short));

      const int packetLength = 14;
      if (pl != packetLength)
         return ERR_INVALID_PACKET_LENGTH;

      unsigned char packet[packetLength];
      ret = GetCommand(packet, packetLength, answerTimeoutMs_);
      if (ret != DEVICE_OK)
         return ret;

      DCMOTSTATUS stat;

      ret = ParseStatus(packet, packetLength, stat);
      if (ret != DEVICE_OK)
         return ret;
      // TODO: what do we do with status ???
   }

   return DEVICE_OK;
}

/**
 * This is supposed to set the origin (0,0) at whatever is the current position.
 * Our stage does not support setting the origin (it is fixed). The base class
 * (XYStageBase) provides the default implementation SetAdapterOriginUm(double x, double y)
 * but we are not going to use since it would affect absolute coordinates during "Calibrate"
 * command in micro-manager.
 */
int XYStage::SetOrigin()
{
   // commnted oout since we do not really want to support setting the origin
   // int ret = SetAdapterOriginUm(0.0, 0.0);
   return DEVICE_OK; 
}
 
int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax)
{
   xMin = 0.0;
   yMin = 0.0;
   xMax = xAxisMaxSteps * stepSizeUm;
   yMax = yAxisMaxSteps * stepSizeUm;

   return DEVICE_OK;
}

int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax)
{
   xMin = 0L;
   yMin = 0L;
   xMax = xAxisMaxSteps;
   yMax = yAxisMaxSteps;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
 * Gets/Sets serial port name
 * Works only before the device is initialized
 */
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

/**
 * Gets and sets the maximum speed with which the stage travels
 */
int XYStage::OnMaxVelocity(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) 
   {
      // get parameters from the x axis (we assume y is the same)
      MOTVELPARAMS params;
      int ret = GetVelocityProfile(params, X);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set((unsigned long)params.lMaxVel / velocityScale);
   } 
   else if (eAct == MM::AfterSet) 
   {
      // first get current profile
      // (from x axis only - y should be the same)
      MOTVELPARAMS params;
      int ret = GetVelocityProfile(params, X);
      if (ret != DEVICE_OK)
         return ret;
     
      // set desired velocity to both axes
      double maxVel;
      pProp->Get(maxVel);
      params.lMaxVel = (unsigned long)(maxVel * velocityScale); 

      // apply X profile
      ret = SetVelocityProfile(params, X);
      if (ret != DEVICE_OK)
         return ret;

      // apply the same for Y
      ret = SetVelocityProfile(params, Y);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

/**
 * Gets and sets the Acceleration of the stage travels
 */
int XYStage::OnAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) 
   {
      // get profile from x axis (we assume y is the same)
      MOTVELPARAMS params;
      int ret = GetVelocityProfile(params, X);
      if (ret != DEVICE_OK)
         return ret;

      pProp->Set((unsigned long)params.lAccn / accelScale);
   } 
   else if (eAct == MM::AfterSet) 
   {
      // first get current profile
      // (from x stage only because we assume y stage is the same)
      MOTVELPARAMS params;
      int ret = GetVelocityProfile(params, X);
      if (ret != DEVICE_OK)
         return ret;
     
      // set desired acceleration to both axes
      double accel;
      pProp->Get(accel);
      params.lAccn = (unsigned long)(accel * accelScale); 

      // apply X profile
      ret = SetVelocityProfile(params, X);
      if (ret != DEVICE_OK)
         return ret;

      // apply the same for Y
      ret = SetVelocityProfile(params, Y);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

/**
 * Gets and sets the Acceleration of the stage travels
 */
int XYStage::OnMoveTimeout(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) 
   {
      pProp->Set(moveTimeoutMs_);
   } 
   else if (eAct == MM::AfterSet) 
   {
      pProp->Get(moveTimeoutMs_);
   }

   return DEVICE_OK;
}
///////////////////////////////////////////////////////////////////////////////
// private methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sends a binary seqence of bytes to the com port
 */
int XYStage::SetCommand(const unsigned char* command, unsigned length)
{
   int ret = WriteToComPort(port_.c_str(), command, length);
   if (ret != DEVICE_OK)
      return ret;
   return DEVICE_OK;
}

/**
 * Retrieves specified number of bytes (length) from the Rx buffer.
 * We block until we collect (length) bytes from the port.
 * As soon as bytes are retrieved the method returns with DEVICE_OK code.
 * If the specified number of bytes is not retrieved from the port within
 * (timeoutMs) interval, we return with error.
 */
int XYStage::GetCommand(unsigned char* response, unsigned length, double timeoutMs)
{
   MM::MMTime startTime = GetCurrentMMTime();
   unsigned long totalBytesRead = 0;
   while ((totalBytesRead < length))
   {
      if ((GetCurrentMMTime() - startTime).getMsec() > timeoutMs)
         return ERR_RESPONSE_TIMEOUT;

      unsigned long bytesRead(0);
      int ret = ReadFromComPort(port_.c_str(), response + totalBytesRead, length-totalBytesRead, bytesRead);
      if (ret != DEVICE_OK)
         return ret;
      totalBytesRead += bytesRead;
   }
   return DEVICE_OK;
}

/**
 * Sets max velocity and acceleration parameters
 */
int XYStage::SetVelocityProfile(const MOTVELPARAMS& params, Axis a)
{
   ClearPort(*this, *GetCoreCallback(), port_);
   int ret = SetCommand(a==X ? setVelocityProfileXCmd : GenerateYCommand(setVelocityProfileXCmd), cmdLength);
   if (ret != DEVICE_OK)
      return ret;
   
   unsigned char sendPacket[14];
   memcpy(sendPacket, &params.wChannel, 2);
   memcpy(sendPacket+2, &params.lMinVel, 4);
   memcpy(sendPacket+6, &params.lAccn, 4);
   memcpy(sendPacket+10, &params.lMaxVel, 4);

   ret = SetCommand(sendPacket, 14);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/**
 * Obtains max velocity and acceleration parameters
 */
int XYStage::GetVelocityProfile(MOTVELPARAMS& params, Axis a)
{
   ClearPort(*this, *GetCoreCallback(), port_);
   int ret = SetCommand(a == X? getVelocityProfileXCmd : GenerateYCommand(getVelocityProfileXCmd), cmdLength);
   if (ret != DEVICE_OK)
      return ret;

   unsigned char answer[cmdLength];
   ret = GetCommand(answer, cmdLength, answerTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret; 

   if (answer[0] != getVelocityProfileXSgn)
      return ERR_UNRECOGNIZED_ANSWER;

   // get data packet and parse it
   unsigned short packetLength(0);
   memcpy(&packetLength, answer+2, sizeof(short));

   const unsigned short expectedLength = 14;
   if (packetLength != expectedLength)
      return ERR_INVALID_PACKET_LENGTH;

   unsigned char packet[expectedLength];
   ret = GetCommand(packet, expectedLength, answerTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret;

   ret  = ParseVelocityProfile(packet, expectedLength, params);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/**
 * Queries the device, parses hardware info and fills appropriate data structure
 */
int XYStage::FillHardwareInfo()
{
   // clear communications buffer
   ClearPort(*this, *GetCoreCallback(), port_);

   // send command
   int ret = SetCommand(hardwareInfoCmd, cmdLength);
   if (ret != DEVICE_OK)
      return ret;

   // get answer
   unsigned char answer[cmdLength];
   ret = GetCommand(answer, cmdLength, answerTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0] != hardwareInfoSgn)
      return ERR_UNRECOGNIZED_ANSWER;

   // get packet
   const int packetLength = 84;
   unsigned char packet[packetLength];
   ret = GetCommand(packet, packetLength, answerTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret;

   // fill hardware info struct
   unsigned char* bufPtr = packet;
   info_.dwSerialNum = (unsigned long) *bufPtr;
   bufPtr += sizeof(unsigned long);
   memcpy(info_.szModelNum, bufPtr, 8);
   bufPtr += 8;
   info_.wHWType = (unsigned short) *bufPtr;
   bufPtr += sizeof(unsigned short);
   info_.dwSoftwareVersion = (unsigned long) *bufPtr;
   bufPtr += sizeof(unsigned long);
   memcpy(info_.szNotes, bufPtr, 64);
   bufPtr += 64;
   info_.wNumChannels = (unsigned short) *bufPtr;

   return DEVICE_OK;
}
/**
 * Extract status information from the Rx buffer.
 */
int XYStage::ParseStatus(const unsigned char* buf, int bufLen, DCMOTSTATUS& stat)
{
   if (bufLen != 14)
      return ERR_INVALID_PACKET_LENGTH;

   int bufPtr = 0; 
   memcpy(&stat.wChannel, buf, sizeof(unsigned short));
   bufPtr += sizeof(unsigned short);

   memcpy(&stat.lPosition, buf + bufPtr, sizeof(long));
   bufPtr += sizeof(long);

   memcpy(&stat.wVelocity, buf + bufPtr, sizeof(unsigned short));
   bufPtr += sizeof(unsigned short);

   memcpy(&stat.wReserved, buf + bufPtr, sizeof(unsigned short));
   bufPtr += sizeof(unsigned short);

   memcpy(&stat.dwStatusBits, buf + bufPtr, sizeof(unsigned long));

   return DEVICE_OK;
}

/**
 * Extract velocity profile information from the Rx buffer.
 */
int XYStage::ParseVelocityProfile(const unsigned char* buf, int bufLen, MOTVELPARAMS& params)
{
   if (bufLen != 14)
      return ERR_INVALID_PACKET_LENGTH;

   int bufPtr = 0; 
   memcpy(&params.wChannel, buf, sizeof(unsigned short));
   bufPtr += sizeof(unsigned short);

   memcpy(&params.lMinVel, buf + bufPtr, sizeof(long));
   bufPtr += sizeof(long);

   memcpy(&params.lAccn, buf + bufPtr, sizeof(long));
   bufPtr += sizeof(long);

   memcpy(&params.lMaxVel, buf + bufPtr, sizeof(long));

   return DEVICE_OK;
}

/**
 * Obtain status information for the given axis
 */
int XYStage::GetStatus(DCMOTSTATUS& stat, Axis a)
{
   // X axis
   ClearPort(*this, *GetCoreCallback(), port_);
   int ret = SetCommand(a == X ? getStatusXCmd : GenerateYCommand(getStatusXCmd), cmdLength);
   if (ret != DEVICE_OK)
      return ret;

   unsigned char answer[cmdLength];
   ret = GetCommand(answer, cmdLength, answerTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret; 

   if (answer[0] != getStatusXSgn)
      return ERR_UNRECOGNIZED_ANSWER;

   // get data packed and parse it
   unsigned short packetLength(0);
   memcpy(&packetLength, answer+2, sizeof(short));

   const unsigned short expectedLength = 14;
   if (packetLength != expectedLength)
      return ERR_INVALID_PACKET_LENGTH;

   unsigned char packet[expectedLength];
   ret = GetCommand(packet, expectedLength, answerTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret;

   ret  = ParseStatus(packet, expectedLength, stat);
   if (ret != DEVICE_OK)
      return ret;
   
   return DEVICE_OK;
}

/**
 * Sends move command to both axes and waits for responses, blocking the calling thread.
 * If expected answers do not come within timeout interval, returns with error.
 */
int XYStage::MoveBlocking(long x, long y, bool relative)
{
   if (!home_)
      return ERR_HOME_REQUIRED; 

   // send command to X axis
   ClearPort(*this, *GetCoreCallback(), port_);
   int ret = SetCommand(relative ? setRelPositionXCmd : setPositionXCmd, cmdLength);
   if (ret != DEVICE_OK)
      return ret;
   
   unsigned char sendPacket[6];
   const unsigned short channel = 1;
   memcpy(sendPacket, &channel, 2);
   memcpy(sendPacket + 2, &x, 4);

   ret = SetCommand(sendPacket, 6);
   if (ret != DEVICE_OK)
      return ret;

   //...and then to y axis
   ret = SetCommand(GenerateYCommand(relative ? setRelPositionXCmd : setPositionXCmd), cmdLength);
   if (ret != DEVICE_OK)
      return ret;

   memcpy(sendPacket, &channel, 2);
   memcpy(sendPacket + 2, &y, 4);

   ret = SetCommand(sendPacket, 6);
   if (ret != DEVICE_OK)
      return ret;

   // get two expected answers
   for (int i=0; i<2; i++)
   {
      unsigned char answer[cmdLength];
      ret = GetCommand(answer, cmdLength, moveTimeoutMs_);
      if (ret != DEVICE_OK)
         return ret; 

      // wait for answer
      if (answer[0] != (relative ? setRelPositionXSgn : setPositionXSgn))
         return ERR_UNRECOGNIZED_ANSWER;

      // get data packed and parse it
      unsigned short packetLength(0);
      memcpy(&packetLength, answer+2, sizeof(short));

      const unsigned short expectedLength = 14;
      if (packetLength != expectedLength)
         return ERR_INVALID_PACKET_LENGTH;

      unsigned char packet[expectedLength];
      ret = GetCommand(packet, expectedLength, answerTimeoutMs_);
      if (ret != DEVICE_OK)
         return ret;

      DCMOTSTATUS stat;
      ret  = ParseStatus(packet, expectedLength, stat);
      if (ret != DEVICE_OK)
         return ret;

   }

   return DEVICE_OK;   
}

///////////////////////////////////////////////////////////////////////////////
// CommandThread class
///////////////////////////////////////////////////////////////////////////////
/**
 * Service procedure for the async command thread
 */
int CommandThread::svc(void)
{
   if (cmd_ == MOVE)
   {
      moving_ = true;
      errCode_ = stage_->MoveBlocking(x_, y_);
      moving_ = false;
      ostringstream os;
      os << "Move finished with error code: " << errCode_;
      stage_->LogMessage(os.str().c_str(), true);
   }
   else if (cmd_ == MOVEREL)
   {
      moving_ = true;
      errCode_ = stage_->MoveBlocking(x_, y_, true); // relative move
      moving_ = false;
      ostringstream os;
      os << "Move finished with error code: " << errCode_;
      stage_->LogMessage(os.str().c_str(), true);
   }
   return 0;
}
