///////////////////////////////////////////////////////////////////////////////
// FILE:          MotorStage.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs device adapters: BBD Controller
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

#include "Thorlabs.h"
#include "MotorStage.h"
#include <sstream>

using namespace std;

const char* g_StepSizeXProp = "StepSizeX";
const char* g_StepSizeYProp = "StepSizeY";
const char* g_MaxVelocityProp = "MaxVelocity";
const char* g_AccelProp = "Acceleration";
const char* g_MoveTimeoutProp = "MoveTimeoutMs";

///////////
// commands
///////////

// hardware info
const ThorlabsCommand hardwareInfoCmd = {0x0005, 0x00, 0x00, DEVICE_CONTROLLER, false, DEVICE_HOSTPC};
const unsigned char hardwareInfoSgn = 0x06;

// enable axis
const ThorlabsCommand enableCmd = {0x0210, 0x01, 0x01, DEVICE_CHANNEL0, false, DEVICE_HOSTPC};

// move to home position
const ThorlabsCommand homeCmd = {0x0443, 0x01, 0x00, DEVICE_CHANNEL0, false, DEVICE_HOSTPC};
const unsigned char homeSgn = 0x44;

// get position
const ThorlabsCommand getPositionCmd = {0x0411, 0x01, 0x00, DEVICE_CHANNEL0, false, DEVICE_HOSTPC};
const unsigned char getPositionSgn = 0x12;

// set position
const ThorlabsCommand setPositionCmd = {0x0453, 0x06, 0x00, DEVICE_CHANNEL0, true, DEVICE_HOSTPC};
const unsigned char setPositionSgn = 0x64;

// ensure device alive
const ThorlabsCommand serverAliveCmd = {0x0492, 0x00, 0x00, DEVICE_CHANNEL0, false, DEVICE_HOSTPC};

// set relative position
const ThorlabsCommand setRelPositionCmd = {0x0445, 0x06, 0x00, DEVICE_CHANNEL0, true, DEVICE_HOSTPC};
const unsigned char setRelPositionSgn = 0x64;

// stop (immediate)
const ThorlabsCommand stopCmd = {0x0465, 0x01, 0x01, DEVICE_CHANNEL0, false, DEVICE_HOSTPC};
const unsigned char stopSgn[2] = {0x66, 0x04};

// get status
const ThorlabsCommand getStatusCmd = {0x0490, 0x01, 0x00, DEVICE_CHANNEL0, false, DEVICE_HOSTPC};
const unsigned char getStatusSgn = 0x91;

// get velocity profile
const ThorlabsCommand getVelocityProfileCmd = {0x0414, 0x01, 0x00, DEVICE_CHANNEL0, false, DEVICE_HOSTPC};
const unsigned char getVelocityProfileSgn = 0x15;

// set velocity profile
const ThorlabsCommand setVelocityProfileCmd = {0x0413, 0x0E, 0x00, DEVICE_CHANNEL0, true, DEVICE_HOSTPC};

MotorStage::MotorStage(MM::Device *parent, MM::Core *core, std::string port, int axis, double aTimeoutMs, double mTimeoutMs) :
   port_(port), 
   axis_(axis), 
   moveTimeoutMs_(mTimeoutMs),
   type_(MOTORSTAGE_UNDEFINED),
   parent_(parent),
   core_(core),
   pollingPositionStep_(false),
   blockPolling_(false)
{
}


/**
 * Communication "clear buffer" utility function
 */
int MotorStage::ClearPort(void)
{
   return ::ClearPort(*parent_, *core_, port_);
}

/**
 * Send a Thorlabs serial command
 */
int MotorStage::SendCommand(const ThorlabsCommand cmd)
{
   ThorlabsCommand local_cmd = (ThorlabsCommand) cmd;

   if (local_cmd.destination == DEVICE_CHANNEL0)
      local_cmd.destination += (char)axis_;
   return SetCommand((unsigned char *)&local_cmd, sizeof(local_cmd));
}

/**
 * Sends a binary seqence of bytes to the com port
 */
int MotorStage::SetCommand(const unsigned char* command, unsigned length)
{
   int ret = core_->WriteToSerial(parent_, port_.c_str(), command, length);
#if 0
{
unsigned char *c = (unsigned char *)command;
ostringstream msg;
unsigned int i;
msg << "CMD(" << length << "):";
for (i=0;i<length;i++)
	msg << " 0x" << hex << (int)c[i];
msg << "\n";
core_->LogMessage(parent_, msg.str().c_str());
}
#endif
   if (ret != DEVICE_OK)
      return ret;
   return DEVICE_OK;
}

/**
 * Retrieves specified number of bytes (length) from the Rx buffer.
 * We block until we collect (length) bytes from the port.
 * As soon as bytes are retrieved the method returns with DEVICE_OK code.
 * If the specified number of bytes is not retrieved from the port within
 * (answerTimeoutMs_) interval, we return with error.
 */
int MotorStage::GetCommandAnswer(unsigned char *response, int length, double timeout, bool yieldToPolling)
{
   MM::MMTime startTime = core_->GetCurrentMMTime();
   long totalBytesRead = 0;

   if (timeout == -1)
      timeout = moveTimeoutMs_; /* use default timeout */

   while ((totalBytesRead < length))
   {
      if ((core_->GetCurrentMMTime() - startTime).getMsec() > timeout)
         return ERR_RESPONSE_TIMEOUT;

	  if (yieldToPolling && blockPolling_)
		  return DEVICE_OK;  // This happens if end of move message processed by GetPositionSteps()

	  if (!pollingPositionStep_ || !yieldToPolling)
	  {
		unsigned long bytesRead(0);
		int ret = core_->ReadFromSerial(parent_, port_.c_str(), response + totalBytesRead, length-totalBytesRead, bytesRead);
		if (ret != DEVICE_OK)
			return ret;
		totalBytesRead += bytesRead;
	  }
   }
#if 0
{
unsigned char *c = (unsigned char *)response;
ostringstream msg;
int i;
msg << "RPL(" << totalBytesRead << "): ";
for (i=0;i<totalBytesRead;i++)
	msg << " 0x" << hex << (int)c[i];
msg << "\n";
core_->LogMessage(parent_, msg.str().c_str());
}
#endif
   return DEVICE_OK;
}

/**
 * Queries the device, parses hardware info and fills appropriate data structure
 */
int MotorStage::Initialize(HWINFO *info)
{
   // clear communications buffer
   ClearPort();

   // send command
   int ret = SendCommand(hardwareInfoCmd);
   if (ret != DEVICE_OK)
      return ret;

   // get answer
   unsigned char answer[cmdLength];
   ret = GetCommandAnswer(answer, cmdLength);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0] != hardwareInfoSgn)
      return ERR_UNRECOGNIZED_ANSWER;

   // get packet
   const int packetLength = 84;
   unsigned char packet[packetLength];
   ret = GetCommandAnswer(packet, packetLength);
   if (ret != DEVICE_OK)
      return ret;

   // fill hardware info struct
   unsigned char* bufPtr = packet;
   info_.dwSerialNum = (unsigned int) *bufPtr;
   bufPtr += sizeof(unsigned int);
   memcpy(info_.szModelNum, bufPtr, 8);
   bufPtr += 8;
   info_.wHWType = (unsigned short) *bufPtr;
   bufPtr += sizeof(unsigned short);
   info_.dwSoftwareVersion = (unsigned int) *bufPtr;
   bufPtr += sizeof(unsigned int);
   memcpy(info_.szNotes, bufPtr, 64);
   bufPtr += 64;
   info_.wNumChannels = (unsigned short) *bufPtr;
   if (info)
      *info = info_;

   // check for supported models
   if (strcmp(info_.szModelNum, "BBD102") == 0)
      type_ = MOTORSTAGE_SERVO;
   else if (strcmp(info_.szModelNum, "BBD101") == 0)
      type_ = MOTORSTAGE_SERVO;
   else if (strcmp(info_.szModelNum, "BBD103") == 0)
      type_ = MOTORSTAGE_SERVO;
   else if (strcmp(info_.szModelNum, "BBD201") == 0)
      type_ = MOTORSTAGE_SERVO;
   else if (strcmp(info_.szModelNum, "BBD202") == 0)
      type_ = MOTORSTAGE_SERVO;
   else if (strcmp(info_.szModelNum, "BBD203") == 0)
      type_ = MOTORSTAGE_SERVO;
   else if (strcmp(info_.szModelNum, "TST001") == 0)
      type_ = MOTORSTAGE_STEPPER;
   else if (strcmp(info_.szModelNum, "OST001") == 0)
      type_ = MOTORSTAGE_STEPPER;
   else if (strcmp(info_.szModelNum, "TDC001") == 0)
      type_ = MOTORSTAGE_SERVO;
   else if (strcmp(info_.szModelNum, "ODC001") == 0)
      type_ = MOTORSTAGE_SERVO;
   else
      return ERR_UNRECOGNIZED_DEVICE;

   blockPolling_ = true;

   return DEVICE_OK;
}

/**
 * Extract status information from the Rx buffer.
 */
int MotorStage::ParseStatus(const unsigned char* buf, int bufLen, DCMOTSTATUS& stat)
{
   if (bufLen != 14)
      return ERR_INVALID_PACKET_LENGTH;

   int bufPtr = 0; 
   memcpy(&stat.wChannel, buf, sizeof(unsigned short));
   bufPtr += sizeof(unsigned short);

   memcpy(&stat.lPosition, buf + bufPtr, sizeof(long));
   currentPos_ = stat.lPosition;
   bufPtr += sizeof(long);

   memcpy(&stat.wVelocity, buf + bufPtr, sizeof(unsigned short));
   bufPtr += sizeof(unsigned short);

   memcpy(&stat.wReserved, buf + bufPtr, sizeof(unsigned short));
   bufPtr += sizeof(unsigned short);

   memcpy(&stat.dwStatusBits, buf + bufPtr, sizeof(unsigned long));

   return DEVICE_OK;
}

/**
 * Obtain status information for the given axis
 */
int MotorStage::GetStatus(DCMOTSTATUS& stat)
{
   int ret;

   // FIXME: The stepper motors do not appear to support the GetStatus command, look for other ways to find this information
   if (type_ != MOTORSTAGE_SERVO)
   {
      ostringstream os;
      os << "GetStatus command does not appear to be supported for this stage type!";
      core_->LogMessage(parent_, os.str().c_str(), true);
      stat.dwStatusBits = 0x0; // Require that a "Home" operation be performed
      return DEVICE_OK;
   }

   ClearPort();
   ret = SendCommand(getStatusCmd);
   if (ret != DEVICE_OK)
      return ret;

   unsigned char answer[cmdLength];
   ret = GetCommandAnswer(answer, cmdLength);
   if (ret != DEVICE_OK)
      return ret; 

   if (answer[0] != getStatusSgn)
      return ERR_UNRECOGNIZED_ANSWER;

   // get data packed and parse it
   unsigned short packetLength(0);
   memcpy(&packetLength, answer+2, sizeof(short));

   const unsigned short expectedLength = 14;
   if (packetLength != expectedLength)
      return ERR_INVALID_PACKET_LENGTH;

   unsigned char packet[expectedLength];
   ret = GetCommandAnswer(packet, expectedLength);
   if (ret != DEVICE_OK)
      return ret;

   ret  = ParseStatus(packet, expectedLength, stat);
   if (ret != DEVICE_OK)
      return ret;
   
   return DEVICE_OK;
}

/**
 * Sends move command to axis and waits for response, blocking the calling thread.
 * If the expected answer does not come within timeout interval, returns with error.
 */
int MotorStage::MoveBlocking(long pos, bool relative)
{
   unsigned char sendPacket[6];
   const unsigned short channel = 1;
   long gotoPos = pos < 0 ? 0 : pos;

   ClearPort();
   SendCommand(serverAliveCmd);
   int ret = SendCommand(relative ? setRelPositionCmd : setPositionCmd);
   if (ret != DEVICE_OK)
      return ret;

   memcpy(sendPacket, &channel, 2);
   memcpy(sendPacket + 2, &gotoPos, 4);

   ret = SetCommand(sendPacket, 6);
   if (ret != DEVICE_OK)
      return ret;

   // get answer to command
   {
      unsigned char answer[cmdLength];
      blockPolling_ = false;
      ret = GetCommandAnswer(answer, cmdLength, moveTimeoutMs_, true);
	  if (blockPolling_)
		 return ret;  // This happens if end of move message processed by GetPositionSteps()
	  blockPolling_ = true;
      if (ret != DEVICE_OK)
         return ret; 

      // wait for answer
      if (answer[0] != (relative ? setRelPositionSgn : setPositionSgn))
         return ERR_UNRECOGNIZED_ANSWER;

	  ret = ProcessEndOfMove(answer, cmdLength);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

int MotorStage::ProcessEndOfMove(const unsigned char* buf, int bufLen)
{
      // get data packed and parse it
      unsigned short packetLength(0);
      memcpy(&packetLength, buf+2, sizeof(short));

      const unsigned short expectedLength = 14;
      if (packetLength != expectedLength)
         return ERR_INVALID_PACKET_LENGTH;

      unsigned char packet[expectedLength];
      int ret = GetCommandAnswer(packet, expectedLength);
      if (ret != DEVICE_OK)
         return ret;

      DCMOTSTATUS stat;
      return ParseStatus(packet, expectedLength, stat);
}

/**
 * Stops XY stage immediately. Blocks until done.
 */
int MotorStage::Stop()
{
   int ret;

   ClearPort();
   ret = SendCommand(stopCmd);
   if (ret != DEVICE_OK)
      return ret;

   // get command answer
   {
      unsigned char answer[cmdLength];
      ret = GetCommandAnswer(answer, cmdLength, moveTimeoutMs_);
      if (ret != DEVICE_OK)
         return ret;

      if (memcmp(stopSgn, answer, sizeof(stopSgn)) != 0)
         return ERR_UNRECOGNIZED_ANSWER;

      // get data packed and parse it
      unsigned short pl(0);
      memcpy(&pl, answer+2, sizeof(short));

      const int packetLength = 14;
      if (pl != packetLength)
         return ERR_INVALID_PACKET_LENGTH;

      unsigned char packet[packetLength];
      ret = GetCommandAnswer(packet, packetLength);
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

int MotorStage::GetPositionSteps(long& p)
{
   int ret;
   bool receivedEndOfMove = false;

   if (blockPolling_ || pollingPositionStep_)
   {
	  p = currentPos_;
      return DEVICE_OK;
   }

   pollingPositionStep_ = true;
   ret = SendCommand(getPositionCmd);
   if (ret != DEVICE_OK)
      return ret;

   unsigned char answer[cmdLength];
   ret = GetCommandAnswer(answer, cmdLength);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0] == setRelPositionSgn || answer[0] == setPositionSgn)
   {
	  ret = ProcessEndOfMove(answer, cmdLength);
	  if (ret != DEVICE_OK)
		 return ret;

	  ret = GetCommandAnswer(answer, cmdLength);
      if (ret != DEVICE_OK)
         return ret;

	  receivedEndOfMove = true;
   }

   if (answer[0] != getPositionSgn)
   {
      ostringstream os;
      os << "GetPosition invalid response: " << hex << answer;
      core_->LogMessage(parent_, os.str().c_str(), true);
      pollingPositionStep_ = false;
      blockPolling_ = true;
      return ERR_UNRECOGNIZED_ANSWER;
   }

   // get data packed and parse it
   unsigned short pl(0);
   memcpy(&pl, answer+2, sizeof(short));
   
   const int packetLength = 6;
   if (packetLength != pl)
      return ERR_INVALID_PACKET_LENGTH;

   unsigned char packet[packetLength];
   ret = GetCommandAnswer(packet, packetLength);
   blockPolling_ = receivedEndOfMove;
   pollingPositionStep_ = false;
   if (ret != DEVICE_OK)
      return ret;

   memcpy(&currentPos_, packet+2, 4);
   p = currentPos_;
   return DEVICE_OK;
}

int MotorStage::Enable()
{
   return SendCommand(enableCmd);
}

int MotorStage::Home()
{
   int ret;

   ClearPort();
   ret = SendCommand(homeCmd);
   if (ret != DEVICE_OK)
      return ret;

   // get the expected answer
   unsigned char answer[cmdLength];
   memset(answer, 0, cmdLength);
   ret = GetCommandAnswer(answer, cmdLength, moveTimeoutMs_);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0] != homeSgn)
      return ERR_UNRECOGNIZED_ANSWER;

   return DEVICE_OK;
}

/**
 * Sets max velocity and acceleration parameters
 */
int MotorStage::SetVelocityProfile(const MOTVELPARAMS& params)
{
   int ret;

   ClearPort();
   ret = SendCommand(setVelocityProfileCmd);
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
 * Extract velocity profile information from the Rx buffer.
 */
int MotorStage::ParseVelocityProfile(const unsigned char* buf, int bufLen, MOTVELPARAMS& params)
{
   if (bufLen != 14)
      return ERR_INVALID_PACKET_LENGTH;

   int bufPtr = 0; 
   memcpy(&params.wChannel, buf, sizeof(params.wChannel));
   bufPtr += sizeof(params.wChannel);

   memcpy(&params.lMinVel, buf + bufPtr, sizeof(params.lMinVel));
   bufPtr += sizeof(params.lMinVel);

   memcpy(&params.lAccn, buf + bufPtr, sizeof(params.lAccn));
   bufPtr += sizeof(params.lAccn);

   memcpy(&params.lMaxVel, buf + bufPtr, sizeof(params.lMaxVel));

   return DEVICE_OK;
}

int MotorStage::GetVelocityProfile(MOTVELPARAMS& params)
{
   int ret;

   ClearPort();
   ret = SendCommand(getVelocityProfileCmd);
   if (ret != DEVICE_OK)
      return ret;

   unsigned char answer[cmdLength];
   ret = GetCommandAnswer(answer, cmdLength);
   if (ret != DEVICE_OK)
      return ret; 

   if (answer[0] != getVelocityProfileSgn)
      return ERR_UNRECOGNIZED_ANSWER;

   // get data packet and parse it
   unsigned short packetLength(0);
   memcpy(&packetLength, answer+2, sizeof(short));

   const unsigned short expectedLength = 14;
   if (packetLength != expectedLength)
      return ERR_INVALID_PACKET_LENGTH;

   unsigned char packet[expectedLength];
   ret = GetCommandAnswer(packet, expectedLength);
   if (ret != DEVICE_OK)
      return ret;

   ret  = ParseVelocityProfile(packet, expectedLength, params);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

