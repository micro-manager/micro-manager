///////////////////////////////////////////////////////////////////////////////
// FILE:       ZeissHub.cpp
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Zeiss CAN29 bus controller, see Zeiss CAN29 bus documentation
//                
// AUTHOR: Nico Stuurman, 6/18/2007 - 
//
// COPYRIGHT:     University of California, San Francisco, 2007
// LICENSE:       Please note: This code could only be developed thanks to information 
//                provided by Zeiss under a non-disclosure agreement.  Subsequently, 
//                this code has been reviewed by Zeiss and we were permitted to release 
//                this under the LGPL on 1/16/2008 (and again on 7/3/2008 after changes 
//                to the code).  If you modify this code using information you obtained 
//                under a NDA with Zeiss, you will need to ask Zeiss whether you can release 
//                your modifications.  
//
//                This version contains changes not yet approved by Zeiss.  This code
//                can therefore not be made publicly available and the license below does not apply
//                
//                This library is free software; you can redistribute it and/or
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


#include "ZeissAxioZoom.h"


//////////////////////////////////////////
// Interface to the Zeis microscope
//

// Define static members of class ZeissHub
// TODO: change these arrays into dynamically allocated structures
std::string ZeissHub::reflectorList_[10];
std::string ZeissHub::objectiveList_[7];
std::string ZeissHub::tubeLensList_[5];
std::string ZeissHub::sidePortList_[3];
std::string ZeissHub::condenserList_[8];

ZeissDeviceInfo ZeissHub::deviceInfo_[MAXNUMBERDEVICES];
ZeissUByte ZeissHub::commandGroup_[MAXNUMBERDEVICES];


ZeissHub::ZeissHub() :
   portInitialized_ (false),
   targetDevice_ (AXIOOBSERVER),
   monitoringThread_(0),
   timeOutTime_(250000),
   scopeInitialized_ (false)
{
   // initialize deviceinfo
   for (int i=0; i< MAXNUMBERDEVICES; i++) {
      deviceInfo_[i].present = false;
   }
   // Set vector g_commandGroup
   // default all devices to Changer
   for (int i=0; i< MAXNUMBERDEVICES; i++) {
      commandGroup_[i] = 0xA1;
   }
   // Set System devices
   commandGroup_[0x15]=0xA0; 
   commandGroup_[0x18]=0xA0; 
   commandGroup_[0x19]=0xA0;
   // Set Servos
   commandGroup_[0x08]=0xA2; 
   commandGroup_[0x09]=0xA2; 
   commandGroup_[0x23]=0xA2; 
   commandGroup_[0x28]=0xA2; 
   commandGroup_[0x29]=0xA2; 
   commandGroup_[0x2D]=0xA2;
   // Set Axis
   commandGroup_[0x0F]=0xA3; 
   commandGroup_[0x25]=0xA3; 
   commandGroup_[0x26]=0xA3; 
   commandGroup_[0x27]=0xA3;
}

ZeissHub::~ZeissHub()
{
   if (monitoringThread_ != 0)
      delete(monitoringThread_);
}

/**
 * Clears the serial receive buffer.
 */
void ZeissHub::ClearRcvBuf()
{
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

/*
 * Reads version number, available devices, device properties and some labels from the microscope and then starts a thread that keeps on reading data from the scope.  Data are stored in the array deviceInfo from which they can be retrieved by the device adapters
 */
int ZeissHub::Initialize(MM::Device& device, MM::Core& core)
{
   if (!portInitialized_)
      return ERR_PORT_NOT_OPEN;
   
   std::ostringstream os;
   os << "Initializing Hub";
   core.LogMessage (&device, os.str().c_str(), false);
   os.str("");
   // empty the Rx serial buffer before sending commands
   ClearRcvBuf();
   ClearPort(device, core);

   int ret = DEVICE_OK;

   // Get version inquires whether we are dealing with an Imager, Observer or AxioZoom
   // It also does some voodoo to deal with serial ports that do not respond immediately
   ret = GetVersion(device, core);
   if (ret != DEVICE_OK) {
      return ret;
   }

   // Query the system and collect all nodes present in the hardware
   ret = GetCANNodes(device, core);
   if (ret != DEVICE_OK) {
      return ret;
   }

   // Check if we are dealing with AxioZoom
   availableDevices_.clear();
   if (targetDevice_ != EMS3)
   {
      // fail if we did not detect axiozoom
      return ERR_GET_AXIOZOOM_ONLY;
   }

   // get Motor Focus status info
   std::vector<ZeissByte>::iterator it = find(canNodes_.begin(), canNodes_.end(), MOTOR_FOCUS);
   if (it != canNodes_.end()) {
      GetMotorFocusInfo(device, core);
      std::string info;
      GetBiosInfo(device, core, MOTOR_FOCUS, 5, info);
      os << "Bios version of Motor Focus is: " << info;
      core.LogMessage(&device, os.str().c_str(), false);
      os.str("");
   }

   // get stage X status info
   it = find(canNodes_.begin(), canNodes_.end(), STAGEX);
   if (it != canNodes_.end()) {
      GetStageInfo(device, core, STAGEX);
      std::string info;
      GetBiosInfo(device, core, STAGEX, 5, info);
      os << "Bios version of Stage X is: " << info;
      core.LogMessage(&device, os.str().c_str(), false);
      os.str("");
   }

   // get stage Y status info
   it = find(canNodes_.begin(), canNodes_.end(), STAGEY);
   if (it != canNodes_.end()) {
      GetStageInfo(device, core, STAGEY);
      std::string info;
      GetBiosInfo(device, core, STAGEY, 5, info);
      os << "Bios version of Stage Y is: " << info;
      core.LogMessage(&device, os.str().c_str(), false);
      os.str("");
   }

   // get fluo tube info
   it = find(canNodes_.begin(), canNodes_.end(), FLUO_TUBE);
   if (it != canNodes_.end()) {
      GetFluoTubeInfo(device, core);
      std::string info;
      GetBiosInfo(device, core, FLUO_TUBE, 5, info);
      os << "Bios version of Fluo Tube is: " << info;
      core.LogMessage(&device, os.str().c_str(), false);
      os.str("");
   }

   // get optics unit info
   it = find(canNodes_.begin(), canNodes_.end(), OPTICS);
   if (it != canNodes_.end()) {
      GetOpticsInfo(device, core);
      std::string info;
      GetBiosInfo(device, core, OPTICS, 5, info);
      os << "Bios version of Optics Unit is: " << info;
      core.LogMessage(&device, os.str().c_str(), false);
      os.str("");
   }

   // get DL450 info
   it = find(canNodes_.begin(), canNodes_.end(), DL450);
   if (it != canNodes_.end()) {
      GetDL450Info(device, core);
      std::string info;
      GetBiosInfo(device, core, DL450, 5, info);
      os << "Bios version of DL450 is: " << info;
      core.LogMessage(&device, os.str().c_str(), false);
      os.str("");
   }

   monitoringThread_ = new ZeissMonitoringThread(device, core, *this, &deviceInfo_[0], debug_);
   monitoringThread_->Start();

   // initialize MOTOR_FOCUS node
   it = find(canNodes_.begin(), canNodes_.end(), MOTOR_FOCUS);
   if (it != canNodes_.end()) {
      InitMotorFocusEventMonitoring(device, core, true);
      CDeviceUtils::SleepMs(50);
   }

   // initialize stage X node
   it = find(canNodes_.begin(), canNodes_.end(), STAGEX);
   if (it != canNodes_.end()) {
      InitStageEventMonitoring(device, core, true, STAGEX);
      CDeviceUtils::SleepMs(50);
   }

   // initialize stage Y node
   it = find(canNodes_.begin(), canNodes_.end(), STAGEY);
   if (it != canNodes_.end()) {
      InitStageEventMonitoring(device, core, true, STAGEY);
      CDeviceUtils::SleepMs(50);
   }

   // initialize OPTICS node
   it = find(canNodes_.begin(), canNodes_.end(), OPTICS);
   if (it != canNodes_.end()) {
      InitOpticsEventMonitoring(device, core, true);
      CDeviceUtils::SleepMs(50);
   }

   // initialize FLUO_TUBE node
   it = find(canNodes_.begin(), canNodes_.end(), FLUO_TUBE);
   if (it != canNodes_.end()) {
      InitFluoTubeEventMonitoring(device, core);
      CDeviceUtils::SleepMs(50);
   }

   // initialize DL450 node
   it = find(canNodes_.begin(), canNodes_.end(), DL450);
   if (it != canNodes_.end()) {
      InitDL450EventMonitoring(device, core);
      CDeviceUtils::SleepMs(50);
   }

   scopeInitialized_ = true;

   return DEVICE_OK;
}

// broadcast message to discover which nodes are present
int ZeissHub::GetCANAdrress(MM::Device& device, MM::Core& core)
{
   const int commandLength = 5;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x02;
   // Read command, immediate answer
   command[1] = 0x18;
   // 'Find CAN Address'
   command[2] = 0x01;
   // ProcessID
   command[3] = 0x11;
   // SubID 
   command[4] = 0x01;

   int tries = 0;
   bool success = false;
   int ret = DEVICE_OK;
   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned char targetAddress = 0xFF; // all nodes

   core.LogMessage(&device, "Attempting Get CAN Address command...", false);

   while  ((tries < 3) && !success) {
      ret = ExecuteCommand(device, core,  command, commandLength, targetAddress);
      if (ret != DEVICE_OK)
		   return ret;

      ret = GetAnswer(device, core, response, responseLength);
      if (ret == DEVICE_OK) {
	      success = true;
			core.LogMessage(&device, "Microscope responded!", false);
      }
		tries++;
   }

   if (!success) {
      core.LogMessage(&device, "Microscope type detection failed!", false);
   }

   return ret;
}

/**
 * Reads in version info
 * Stores version info in variable version_
 * Also determined whether this is an AxioImager or AxioObserver
 */
int ZeissHub::GetVersion(MM::Device& device, MM::Core& core)
{
   const int commandLength = 5;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x02;
   // Read command, immediate answer
   command[1] = 0x18;
   // 'Get Applications Version'
   command[2] = 0x02;
   // ProcessID
   command[3] = 0x11;
   // SubID 
   command[4] = 0x07;

   core.LogMessage(&device, "Attempting GetVersion() command...", false);

   // The CAN address for the AxioImager A1/D1 is 0x1B, for the Z1/D1 0x19,
   // for the AxioZoom 0x28
   // However, node MAIN BIOS (0x19) is present in the AxioZoom as well as in the Observer,
   // so we have to test for EMS3 unit first which is present only on AxioZoom

   // Try up to three times, alternating between different models
   int ret = DEVICE_OK;
   unsigned char targetAddress[] = {EMS3, AXIOOBSERVER, AXIOIMAGER};
   int tries = 0;
   bool success = false;
   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 4;
   // Version string starts in 6th character, length is in first char
   unsigned char signature[] = { 0x08, command[2], command[3], command[4] };
   while  ((tries < 3) && !success) {
      for (int i = 0; (i < sizeof(targetAddress)) && !success; i++) {
         ret = ExecuteCommand(device, core,  command, commandLength, targetAddress[i]);
         if (ret != DEVICE_OK)
            return ret;

         ret = GetAnswer(device, core, response, responseLength, signature, 1, signatureLength);
         if (ret == DEVICE_OK) {
            success = true;
            targetDevice_ = targetAddress[i];
         }
      }
      tries++;
   }
   if (!success) {
      core.LogMessage(&device, "Microscope type detection failed!", false);
      return ret;
   }

   std::ostringstream os;
   os << "Microscope type Detected: ";
   if (targetDevice_ == AXIOIMAGER)
      os << "AxioImager";
   else if (targetDevice_ == AXIOOBSERVER)
      os << "AxioObserver";
   else if (targetDevice_ == EMS3)
      os << "AxioZoom";
   else
      os << "???";
   core.LogMessage(&device, os.str().c_str(), false);

   response[responseLength] = 0;
   std::string answer((char *)response);
   version_ = "Application version: " + answer.substr(6, atoi(answer.substr(0,1).c_str()));
   core.LogMessage(&device, version_.c_str(), false);
  
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/*
 * Initializes a device inside the microscope
 * This is only needed on the AxioImagers (not clear if this is actually needed)
 * Only to be called from ZeissHub::Initialize for an AxioImager
 */
int ZeissHub::InitDev(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId)
{
   int ret;
   unsigned char command[8];
   // Size of data block
   command[0] = 0x03;
   // Write command, Do not expect answer
   command[1] = 0x1B;
   command[2] = commandGroup;
   // ProcessID
   command[3] = 0x11;
   // SubID (FD = Init Device)
   command[4] = 0xFD;
   // Device ID
   command[5] = (ZeissUByte) devId;

   ret = ExecuteCommand(device, core, command, 6);
   if (ret != DEVICE_OK)
      return ret;

   std::ostringstream os;
   os << "Send Init Dev command to microscope for device: " << devId;
   core.LogMessage(&device, os.str().c_str(), false);

   return DEVICE_OK;
}

/*
 * Initializes AxioZoom device
 * Only to be called from ZeissHub::Initialize for an AxioImager
 */
int ZeissHub::InitDevX(MM::Device& device, MM::Core& core, ZeissUByte devId)
{
   int ret;
   const int cmdLen = 5;
   unsigned char command[cmdLen];
   // Size of data block
   command[0] = 0x02;
   // Write command, Do not expect answer
   command[1] = 0x1B;
   // ProcessID
   command[3] = 0x11;
   // SubID (FD = Init Device)
   command[4] = 0x00;
   // Device ID
   command[5] = devId;

   ret = ExecuteCommand(device, core, command, cmdLen);
   if (ret != DEVICE_OK)
      return ret;

   std::ostringstream os;
   os << "Send Init Dev command to microscope for device: " << devId;
   core.LogMessage(&device, os.str().c_str(), false);

   return DEVICE_OK;
}

/*
 * Gets information strings from the BIOS of a CAN device
 */
int ZeissHub::GetBiosInfo(MM::Device& device, MM::Core& core, ZeissUByte canNode, ZeissUByte infoType, std::string& info)
{
   int ret;
   unsigned char command[5];
   // Size of data block
   command[0] = 0x02;
   command[1] = 0x18;
   command[2] = 0x02;
   // ProcessID
   command[3] = 0x11;
   // SubID
   command[4] = infoType;

   ret = ExecuteCommand(device, core, command, 5, canNode);
   if (ret != DEVICE_OK)
      return ret;

   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 4;
   unsigned char signature[] = {0x08, command[2], command[3], command[4]};
   ret = GetAnswer(device, core, response, responseLength, signature, 1, signatureLength);
   if (ret != DEVICE_OK)
      return ret;

   // [0] = number of data bytes
   info = "";
   for (ZeissUByte i=5; i < response[0] + 3; i++)
      info += response[i];

   std::ostringstream os;
   os << "Info of type " << std::hex << (int) infoType << " Read from canNode " << std::hex << (int) canNode << " was: " << info;
   core.LogMessage(&device, os.str().c_str(), true);

   return DEVICE_OK;
}

/*
 * Requests State of FOCUS MOTOR
 */
int ZeissHub::GetMotorFocusInfo(MM::Device& device, MM::Core& core)
{
   const int cmdLength = 5;
   unsigned char command[cmdLength];
 
   command[0] = 0x02; // size of data block = 2 bytes
   command[1] = 0x18; // command class - immediate answer
   command[2] = 0xB0; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x01; // data byte 2 = subID

   int ret = ExecuteCommand(device, core, command, cmdLength, MOTOR_FOCUS);
   if (ret != DEVICE_OK)
      return ret;

   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 4;
   unsigned char signature[] = {0x08, command[2], command[3], command[4]};
   memset(response, 0, RCV_BUF_LENGTH);
   ret = GetAnswer(device, core, response, responseLength, signature, 1, signatureLength);
   if (ret != DEVICE_OK)
      return ret;

   // [0] = number of data bytes, should be 3
   // [1] = command class (should be 0x08)
   // [2] = command number (0xB0)
   // [3] = Process ID (same as in query), data 1
   // [4] = SubID (0x01 = status), data 2
   // [5] = status, data 3

   motorFocusModel_.SetState(response[5]);
   std::ostringstream os;
   bool init(false), busy(false);
   motorFocusModel_.GetInitialized(init);
   motorFocusModel_.GetBusy(busy);
   os << "Focus Motor Initialized: " << init << ", Busy: " << busy;
   core.LogMessage(&device, os.str().c_str(), false);

   return DEVICE_OK;
}


/*
 * Requests State of stage
 */
int ZeissHub::GetStageInfo(MM::Device& device, MM::Core& core, unsigned char id)
{
   const int cmdLength = 5;
   unsigned char command[cmdLength];
 
   command[0] = 0x02; // size of data block = 2 bytes
   command[1] = 0x18; // command class - immediate answer
   command[2] = 0xB0; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x01; // data byte 2 = subID

   int ret = ExecuteCommand(device, core, command, cmdLength, id);
   if (ret != DEVICE_OK)
      return ret;

   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 4;
   unsigned char signature[] = {0x08, command[2], command[3], command[4]};
   memset(response, 0, RCV_BUF_LENGTH);
   ret = GetAnswer(device, core, response, responseLength, signature, 1, signatureLength);
   if (ret != DEVICE_OK)
      return ret;

   // [0] = number of data bytes, should be 3
   // [1] = command class (should be 0x08)
   // [2] = command number (0xB0)
   // [3] = Process ID (same as in query), data 1
   // [4] = SubID (0x01 = status), data 2
   // [5] = status, data 3

   if (id == STAGEX)
   {
      stageModelX_.SetState(response[5]);
      bool init(false), busy(false);
      stageModelX_.GetInitialized(init);
      stageModelX_.GetBusy(busy);

      std::ostringstream os;
      os << "Stage X (" << id << ") Initialized: " << init << ", Busy: " << busy;
      core.LogMessage(&device, os.str().c_str(), false);
   }
   else
   {
      stageModelY_.SetState(response[5]);
      bool init(false), busy(false);
      stageModelY_.GetInitialized(init);
      stageModelY_.GetBusy(busy);

      std::ostringstream os;
      os << "Stage Y (" << id << ") Initialized: " << init << ", Busy: " << busy;
      core.LogMessage(&device, os.str().c_str(), false);
   }

   return DEVICE_OK;
}

/*
 * Requests State of OPTICS unit
 */
int ZeissHub::GetOpticsInfo(MM::Device& device, MM::Core& core)
{
   const int cmdLength = 5;
   unsigned char command[cmdLength];
 
   command[0] = 0x02; // size of data block = 2 bytes
   command[1] = 0x18; // command class - immediate answer
   command[2] = 0xA0; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x01; // data byte 2 = subID

   int ret = ExecuteCommand(device, core, command, cmdLength, OPTICS);
   if (ret != DEVICE_OK)
      return ret;

   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 4;
   unsigned char signature[] = {0x08, command[2], command[3], command[4]};
   memset(response, 0, RCV_BUF_LENGTH);
   ret = GetAnswer(device, core, response, responseLength, signature, 1, signatureLength);
   if (ret != DEVICE_OK)
      return ret;

   // [0] = number of data bytes, should be 3
   // [1] = command class (should be 0x08)
   // [2] = command number (0xB0)
   // [3] = Process ID (same as in query), data 1
   // [4] = SubID (0x01 = status), data 2
   // [5] = status, data 3

   opticsUnitModel_.SetState(response[5]);
   std::ostringstream os;
   bool init(false), busy(false);
   opticsUnitModel_.GetInitialized(init);
   opticsUnitModel_.GetBusy(busy);
   os << "Optics Unit Initialized: " << init << ", Busy: " << busy;
   core.LogMessage(&device, os.str().c_str(), false);

   return DEVICE_OK;
}

/*
 * Requests State of FLUO_TUBE unit
 */
int ZeissHub::GetFluoTubeInfo(MM::Device& device, MM::Core& core)
{
   const int cmdLength = 6;
   unsigned char command[cmdLength];
 
   command[0] = 0x03; // size of data block = 3 bytes
   command[1] = 0x18; // command class - immediate answer
   command[2] = 0x50; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x01; // data byte 2 = subID
   command[5] = 0x02; // device id

   int ret = ExecuteCommand(device, core, command, cmdLength, FLUO_TUBE);
   if (ret != DEVICE_OK)
      return ret;

   // [0] = number of data bytes, should be 4
   // [1] = command class (should be 0x08)
   // [2] = command number (0x50)
   // [3] = Process ID (same as in query), data 1
   // [4] = SubID (0x01 = status), data 2
   // [5] = status, data 3
   // [6] = status. data 4

   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 4;
   unsigned char signature[] = {0x08, command[2], command[3], command[4]};
   memset(response, 0, RCV_BUF_LENGTH);
   ret = GetAnswer(device, core, response, responseLength, signature, 1, signatureLength);
   if (ret != DEVICE_OK)
      return ret;

   ZeissUShort status = 0;
   memcpy(&status, response + 5, 2);
   status = ntohs(status);

   fluoTubeModel_.SetState(status);
   std::ostringstream os;
   bool init(false), busy(false);
   fluoTubeModel_.GetInitialized(init);
   fluoTubeModel_.GetBusy(busy);
   os << "FluoTube Initialized: " << init << ", Busy: " << busy;
   core.LogMessage(&device, os.str().c_str(), false);

   return DEVICE_OK;
}

/*
 * Requests State of DL450 unit
 */
int ZeissHub::GetDL450Info(MM::Device& device, MM::Core& core)
{
   const int cmdLength = 5;
   unsigned char command[cmdLength];
 
   command[0] = 0x02; // size of data block = 2 bytes
   command[1] = 0x18; // command class - immediate answer
   command[2] = 0xA0; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x01; // data byte 2 = subID
   //command[5] = 0x07; // device id

   int ret = ExecuteCommand(device, core, command, cmdLength, DL450);
   if (ret != DEVICE_OK)
      return ret;

   // [0] = number of data bytes, should be 4
   // [1] = command class (should be 0x08)
   // [2] = command number (0x50)
   // [3] = Process ID (same as in query), data 1
   // [4] = SubID (0x01 = status), data 2
   // [5] = status, data 3
   // [6] = status. data 4

   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 4;
   unsigned char signature[] = {0x08, command[2], command[3], command[4]};
   memset(response, 0, RCV_BUF_LENGTH);
   ret = GetAnswer(device, core, response, responseLength, signature, 1, signatureLength);
   if (ret != DEVICE_OK)
      return ret;

   //ZeissUShort status = 0;
   //memcpy(&status, response + 5, 2);
   //status = ntohs(status);

   dl450Model_.SetState(response[5]);
   std::ostringstream os;
   bool init(false), busy(false);
   dl450Model_.GetInitialized(init);
   dl450Model_.GetBusy(busy);
   os << "DL450 Initialized: " << init << ", Busy: " << busy;
   core.LogMessage(&device, os.str().c_str(), false);

   return DEVICE_OK;
}

/*
 * Starts event monitoring for the Motor Focus
 */
int ZeissHub::InitMotorFocusEventMonitoring(MM::Device& device, MM::Core& core, bool start)
{
   int ret;
   const int cmdLength = 9;
   unsigned char command[cmdLength];

   command[0] = 0x06; // data bytes
   command[1] = 0x1B; // class
   command[2] = 0xB0; // command
   command[3] = 0x11; // process id
   command[4] = 0x10; // sub ID
   command[5] = 0x00; // state
   command[6] = 0x00; // interval
   command[7] = 0x00; // address
   command[8] = 0x00; // procID

   if (start)
      command[5] = 0x02 | 0x20; // monitor position changes
   else
      command[5] = 0x00;
   command[6] = 2;
   command[7] = 0x11;
   command[8] = 0x11;

   ret = ExecuteCommand(device, core, command, cmdLength, MOTOR_FOCUS);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/*
 * Starts event monitoring for the Optics Unit
 */
int ZeissHub::InitOpticsEventMonitoring(MM::Device& device, MM::Core& core, bool start)
{
   int ret;
   const int cmdLength = 9;
   unsigned char command[cmdLength];

   command[0] = 0x06; // data bytes
   command[1] = 0x1B; // class
   command[2] = 0xA0; // command
   command[3] = 0x11; // process id
   command[4] = 0x10; // sub ID
   command[5] = 0x00; // state
   command[6] = 0x00; // interval
   command[7] = 0x00; // address
   command[8] = 0x00; // procID

   if (start)
      command[5] = 0x02 | 0x20 | 0x04; // monitor zomm and aperture
   else
      command[5] = 0x00;
   command[6] = 2;
   command[7] = 0x11;
   command[8] = 0x11;

   ret = ExecuteCommand(device, core, command, cmdLength, OPTICS);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/*
 * Starts event monitoring for the Fluo Tube
 */
int ZeissHub::InitFluoTubeEventMonitoring(MM::Device& device, MM::Core& core)
{
   int ret;
   const int cmdLength = 11;
   unsigned char command[cmdLength];

   command[0] = 0x06; // data bytes
   command[1] = 0x1B; // class
   command[2] = 0x50; // command
   command[3] = 0x11; // process id
   command[4] = 0x1F; // sub ID
   command[5] = 0x02; // deviceID
   command[6] = 0x00; // mode
   command[7] = 0x00; // interval
   command[8] = 0x00; // interval
   command[9] = 0x00; // address
   command[10] = 0x00; // pid

   command[6] = 0x12; // change and status monitoring
   command[9] = 0x11;
   command[10] = 0x11;

   ZeissUShort interval = 100;
   ZeissShort tmp = htons(interval);
   memcpy(command+7, &tmp, ZeissShortSize); 

   ret = ExecuteCommand(device, core, command, cmdLength, FLUO_TUBE);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/*
 * Starts event monitoring for DL450
 */
int ZeissHub::InitDL450EventMonitoring(MM::Device& device, MM::Core& core)
{
   int ret;
   const int cmdLength = 11;
   unsigned char command[cmdLength];

   command[0] = 0x06; // data bytes
   command[1] = 0x1B; // class
   command[2] = 0x51; // command
   command[3] = 0x11; // process id
   command[4] = 0x1F; // sub ID
   command[5] = 0x07; // deviceID
   command[6] = 0x00; // mode
   command[7] = 0x00; // interval
   command[8] = 0x00; // interval
   command[9] = 0x00; // address
   command[10] = 0x00; // pid

   command[6] = 0x03; // change and status monitoring
   command[9] = 0x11;
   command[10] = 0x11;

   ZeissUShort interval = 100;
   ZeissShort tmp = htons(interval);
   memcpy(command+7, &tmp, ZeissShortSize); 

   ret = ExecuteCommand(device, core, command, cmdLength, DL450);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}
/*
 * Starts event monitoring for theStage
 */
int ZeissHub::InitStageEventMonitoring(MM::Device& device, MM::Core& core, bool start, unsigned char stageNodeId)
{
   int ret;
   const int cmdLength = 11;
   unsigned char command[cmdLength];

   command[0] = 0x08; // data bytes
   command[1] = 0x1B; // class
   command[2] = 0x5F; // command
   command[3] = 0x11; // process id
   command[4] = 0x1F; // sub ID
   command[5] = 0x00; // DEV
   command[6] = 0x00; // mode
   command[7] = 0x00; // interval
   command[8] = 0x00; // interval
   command[9] = 0x00; // address
   command[10] = 0x11; // pid

   ZeissUShort interval = 0;
   ZeissShort tmp = htons(interval);
   memcpy(command+7, &tmp, ZeissShortSize); 

   if (start)
      command[6] = 0x13; // position + status monitoring
   else
      command[6] = 0x00;
   command[7] = 10;
   command[8] = 0x11;
   command[9] = 0x11;

   ret = ExecuteCommand(device, core, command, cmdLength, stageNodeId);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/*
 * Broadcast a find CAN address command to query available CAN devices
 */
int ZeissHub::GetCANNodes(MM::Device& device, MM::Core& core)
{
   int ret;
   unsigned char command[5];
   // Size of data block
   command[0] = 0x02;
   // Write command, 
   command[1] = 0x18;
   // command ID
   command[2] = 0x01;

   command[3] = 0x10;
   // SubID (status request)
   command[4] = 0x01;

   core.LogMessage(&device, "Attempting GetCANNodes() command...", false);
   canNodes_.clear();

   ret =  ExecuteCommand(device, core, command, 5, 0xFF);
   if (ret != DEVICE_OK)
      return ret;

   // read incoming serial port traffic up to 250 ms.
   // Parse for available CAN nodes
   MM::MMTime duration(250000);
   MM::MMTime startTime = core.GetCurrentMMTime();
   // 255 CAN addresses, answers are 14 bytes max
   static const unsigned long maxSize = 3570;
   unsigned char buf[maxSize];
   unsigned long totalRead = 0;
   unsigned long read = 0;
   while ((core.GetCurrentMMTime() - startTime) < duration) {
      ret = core.ReadFromSerial(&device, port_.c_str(), buf + totalRead, maxSize - totalRead, read);
      totalRead += read;
      CDeviceUtils::SleepMs(10);
   }

   ZeissMessageParser parser(buf, (long) totalRead);
   unsigned char message[ZeissMessageParser::messageMaxLength_];
   int messageLength;
   do {
      ret = parser.GetNextMessage(message, messageLength);

      if (ret == 0) 
      {
         // print byte array to hex stream
         std::ostringstream os;
         os << "Parsed response: ";
         for (int i=0; i<messageLength; i++)
            os << std::hex << std::setw(2) << std::setfill('0') << static_cast<int>(message[i]) << " ";
         core.LogMessage(&device, os.str().c_str(), true);

         // interpret node
         if (message[0] == 0x11 && message[1] == message[7] && message[4] == 0x01 && message[6] == 1) {
            std::ostringstream os;
            os << "Discovered node: " << std::hex << std::setw(2) << std::setfill('0') << static_cast<int>(message[7]);
            core.LogMessage(&device, os.str().c_str(), true);
            canNodes_.push_back(message[7]);
         }
      }
   } while (ret == 0);
   return DEVICE_OK;
}


/*
 * Queries the microscope directly for the position of the given device
 * Should only be called from the Initialize function
 * position will always be cast to a long, even though it is a short for commandGroups 0xA2 and 0xA1
 * Only to be called from ZeissHub::Initialize
 */
int ZeissHub::GetPosition(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissLong& position)
{
   int ret;
   unsigned char command[8];
   // Size of data block
   command[0] = 0x03;
   // Read command, immediate answer
   command[1] = 0x18;
   command[2] = commandGroup;
   // ProcessID
   command[3] = 0x11;
   // SubID (01 = absolute position)
   command[4] = 0x01;
   // Device ID
   command[5] = (ZeissUByte) devId;

   ret = ExecuteCommand(device, core, command, 6);
   if (ret != DEVICE_OK)
      return ret;

   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 5;
   unsigned char signature[] = { 0x08, commandGroup, command[3], command[4], devId };
   ret = GetAnswer(device, core, response, responseLength, signature, 1, signatureLength);
   if (ret != DEVICE_OK)
      return ret;

   if (commandGroup == 0xA3) {
      ZeissLong tmp = 0;
      memcpy (&tmp, response + 6, ZeissLongSize);
      position = (long) ntohl(tmp);
   }
   else {
      ZeissShort tmp = 0;
      memcpy (&tmp, response + 6, ZeissShortSize);
      position = (long) ntohs(tmp);
   }
   
   return DEVICE_OK;
}

/*
 * Queries the microscope for the total number of positions for this device
 * Only to be called from ZeissHub::Initialize
 */
int ZeissHub::GetMaxPosition(MM::Device& device, MM::Core& core, ZeissUByte groupName, ZeissUByte devId, ZeissLong& position)
{
   int ret;
   const int commandLength = 6;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x03;
   // Read command, immediate answer
   command[1] = 0x18;
   // 'Changer command??'
   command[2] = groupName;
   // ProcessID (first data byte)
   command[3] = 0x11;
   // SubID (05 = total positions)
   command[4] = 0x05;
   // Device ID
   command[5] = devId;

   ret = ExecuteCommand(device, core, command, commandLength);
   if (ret != DEVICE_OK)
      return ret;

   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 5;
   unsigned char signature[] = {0x08, groupName, command[3], command[4], devId};
   ret = GetAnswer(device, core, response, responseLength, signature, 1, signatureLength);
   if (ret != DEVICE_OK)
      return ret;

   // Answer is in bytes 6 and 7 of the already stripped answer:
   // [0] = number of data bytes
   // [1] = command class (should be 0x08)
   // [2] = command number (0xA1)
   // [3] = Process ID (same as in query)
   // [4] = SubID (01 = absolute position
   // [5] = DeviceID (should be same as what we asked for)
   // [6] = high byte of position
   // [7] = low byte of position
 
   if (groupName == 0xA3) {
      ZeissLong tmp;
      memcpy (&tmp, response + 6, ZeissLongSize);
      position = (long) ntohl(tmp);
   }
   else {
      ZeissShort tmp;
      memcpy (&tmp, response + 6, ZeissShortSize);
      position = (long) ntohs(tmp);
   }

   return DEVICE_OK;
}

/*
 * Queries the microscope for the device scaling strings
 * Only to be called from ZeissHub::Initialize
 */
int ZeissHub::GetDeviceScalings(MM::Device& device, MM::Core& core, ZeissUByte groupName, ZeissUByte devId, ZeissDeviceInfo& deviceInfo)
{
   const int commandLength = 6;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x03;
   // Read command, immediate answer
   command[1] = 0x15;
   // 'Find Devices'
   command[2] = groupName;
   // ProcessID
   command[3] = 0x24;
   // SubID 
   command[4] = 0x08;
   // Dev-ID (all devices)
   command[5] = devId;

   int ret = ExecuteCommand(device, core,  command, commandLength);
   if (ret != DEVICE_OK)
      return ret;

   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 4;
   unsigned char signature[] = {command[2], command[3], command[4], command[5] };
   do {
      memset(response, 0, RCV_BUF_LENGTH);
      ret = GetAnswer(device, core, response, responseLength, signature, 2, signatureLength);
      if (ret != DEVICE_OK)
         return ret;
      if ( (response[0]>3) && (response[0]==responseLength-5)) {
         std::vector<char> tmp(response[0]-2);
         memcpy(&(tmp[0]), response + 6, response[0]-3);
         tmp[response[0] - 3] = 0;
         deviceInfo.deviceScalings.push_back(std::string((char*) &(tmp[0])));
      }
   } while (response[1] == 0x05); // last response of the list is of class 0x09
  
   return DEVICE_OK;
}

/*
 * Queries the microscope for the Scaling table.
 * Each device scaling (unit) has associated with it a list of at least two sets of datapoints that relate the numeric positions (steps) to real world coordinates
 * Data should be related by interpolation
 * Only to be called from ZeissHub::Initialize
 */
int ZeissHub::GetScalingTable(MM::Device& device, MM::Core& core, ZeissUByte groupName, ZeissUByte devId, ZeissDeviceInfo& deviceInfo, std::string unit)
{
   unsigned int commandLength = 6 + (unsigned int) unit.length();
   std::vector<unsigned char> command(commandLength);
   // Size of data block
   command[0] = 0x03 + (unsigned char) unit.length();
   // Read command, immediate answer
   command[1] = 0x15;
   // 'Find Devices'
   command[2] = groupName;
   // ProcessID
   command[3] = 0x25;
   // SubID 
   command[4] = 0x09;
   // Dev-ID (all devices)
   command[5] = devId;
   for (unsigned int i=0; i<unit.length(); i++)
      command[6+i] = (unsigned char) unit[i];

   int ret = ExecuteCommand(device, core,  &(command[0]), (int) command.size());
   if (ret != DEVICE_OK)
      return ret;

   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 4;
   unsigned char signature[] = {command[2], command[3], command[4], command[5] };
   do {
      memset(response, 0, RCV_BUF_LENGTH);
      ret = GetAnswer(device, core, response, responseLength, signature, 2, signatureLength);
      if (ret != DEVICE_OK)
         return ret;

      if (groupName == 0xA3) {
         ZeissLong tmp;
         memcpy (&tmp, response + 6, ZeissLongSize);
         deviceInfo.nativeScale[unit].push_back(ntohl(tmp));
         ZeissLong tmpl;
         ZeissFloat tmpf;
         memcpy(&tmpl, response + 6 + ZeissShortSize, ZeissLongSize);
         tmpl = ntohl(tmpl);
         memcpy(&tmpf, &tmpl, ZeissFloatSize);
         deviceInfo.scaledScale[unit].push_back(tmpf);
      }
      else { // groupName == 0xA2
         ZeissShort tmp;
         memcpy (&tmp, response + 6, ZeissShortSize);
         deviceInfo.nativeScale[unit].push_back(ntohs(tmp));
         ZeissLong tmpl;
         ZeissFloat tmpf;
         memcpy(&tmpl, response + 6 + ZeissShortSize, ZeissLongSize);
         tmpl = ntohl(tmpl);
         memcpy(&tmpf, &tmpl, ZeissFloatSize);
         deviceInfo.scaledScale[unit].push_back(tmpf);
      }
   } while (response[1] == 0x05); // last response starts with 0x09
  
   return DEVICE_OK;
}

/*
 * Queries the microscope for the Axis's measuring origin
 * Only works for Axis
 * Only to be called from ZeissHub::Initialize
 */
int ZeissHub::GetMeasuringOrigin(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, ZeissDeviceInfo& deviceInfo)
{
   if (commandGroup!=0xA3)
      return DEVICE_OK;
   const int commandLength = 6;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x03;
   // Read command, immediate answer
   command[1] = 0x18;
   // only works on Axis
   command[2] = commandGroup;
   // ProcessID (first data byte)
   command[3] = 0x11;
   // SubID (24 = Measuring origin
   command[4] = 0x24;
   // Device ID
   command[5] = devId;

   int ret = ExecuteCommand(device, core, command, commandLength);
   if (ret != DEVICE_OK)
      return ret;

   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 5;
   unsigned char signature[] = {0x08, command[2], command[3], command[4], devId};
   ret = GetAnswer(device, core, response, responseLength, signature, 1, signatureLength);
   if (ret != DEVICE_OK)
      return ret;

   ZeissLong tmp;
   memcpy (&tmp, response + 6, ZeissLongSize);
   tmp = (long) ntohl(tmp);
   deviceInfo.measuringOrigin = tmp;

   return DEVICE_OK;
}

/**
 * Queries the microscope for available motorized devices
 * Stores devices IDs in vector availableDevice_
 * Does not return 0x11 (PC) 0x15 (BIOS), 0x18 (TFT display), 0x19 (Main program) 
 */
int ZeissHub::FindDevices(MM::Device& device, MM::Core& core)
{
   const int commandLength = 6;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x03;
   // Read command, immediate answer
   command[1] = 0x15;
   // 'Find Devices'
   command[2] = 0xA0;
   // ProcessID
   command[3] = 0x23;
   // SubID 
   command[4] = 0xFE;
   // Dev-ID (all devices)
   command[5] = 0x00;

   core.LogMessage(&device, "Attempting FindDevices() command...", false);
   int ret = ExecuteCommand(device, core,  command, commandLength);
   if (ret != DEVICE_OK)
      return ret;

   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 3;
   unsigned char signature[] = { 0xA0,  command[3], command[4] };
   do {
      memset(response, 0, responseLength);
      ret = GetAnswer(device, core, response, responseLength, signature, 2, signatureLength);
      if (ret != DEVICE_OK)
         return ret;
      // We are only interested in motorized devices and do not want any of the system devices:
      if (response[5] <= MAXNUMBERDEVICES) {
         if (response[5] != 0x11 && response[5] != 0x15 && response[5] != 0x18 && response[5] != 0x19 && (response[6] & 3)) {
            availableDevices_.push_back(response[5]);
            deviceInfo_[response[5]].present = true;
         }
         deviceInfo_[response[5]].status = response[6];
      }
   } while (response[1] == 0x05); // last response starts with 0x09
  
   return DEVICE_OK;
}

/*
 * Reads reflector labels from the microscope and stores them in a static array
 */
int ZeissHub::GetReflectorLabels(MM::Device& device, MM::Core& core)
{
   unsigned char data[ZeissHub::RCV_BUF_LENGTH];
   unsigned char  dataLength;
   ZeissUByte dataType;
   std::string label;
   if (!deviceInfo_[0x01].present)
      return 0; // no reflectors, lets not make a fuss
   for (ZeissUShort i=0; i< deviceInfo_[0x01].maxPos && i < 10; i++) {
      // Short name 1
      memset(data, 0, ZeissHub::RCV_BUF_LENGTH);
      GetPermanentParameter(device, core, (ZeissUShort) 0x1500 + i + 1, 0x15, dataType, data, dataLength);
      std::ostringstream os;
      os << (i+1) << "-";
      label = os.str() + std::string(reinterpret_cast<char*> (data));
      size_t pos = label.find(16);
      if (pos != std::string::npos)
         label.replace(pos, 1, " ");
      // Short name 2
      memset(data, 0, ZeissHub::RCV_BUF_LENGTH);
      GetPermanentParameter(device, core, (ZeissUShort) 0x1500 + i + 1, 0x16, dataType, data, dataLength);
      label += " ";
      label += reinterpret_cast<char*> (data);
      pos = label.find(16);
      if (pos != std::string::npos)
         label.replace(pos, 1, " ");
      reflectorList_[i] = label;
   }
   return 0;
} 

/*
 * Reads objective labels from the microscope and stores them in a static array
 */
int ZeissHub::GetObjectiveLabels(MM::Device& device, MM::Core& core)
{
   unsigned char data[RCV_BUF_LENGTH];
   unsigned char  dataLength;
   ZeissUByte dataType;
   std::string label;
   if (!deviceInfo_[0x02].present)
      return DEVICE_OK; // no objectives, lets not make a fuss
   for (ZeissLong i=0; (i<= deviceInfo_[0x02].maxPos) && (i < 7); i++) {
      memset(data, 0, RCV_BUF_LENGTH);
      dataLength =0;
      GetPermanentParameter(device, core, (ZeissUShort) 0x1410, (ZeissByte) (0x00 + ((i+1)*0x10)), dataType, data, dataLength);
      std::ostringstream os;
      os << (i+1) << "-";
      label = os.str() + std::string(reinterpret_cast<char*> (data));
      objectiveList_[i] = label;
   }
   return 0;
}

/*
 * Reads TubeLens Magnification from the microscope and stores them in a static array
 */
int ZeissHub::GetTubeLensLabels(MM::Device& device, MM::Core& core)
{
   unsigned char data[RCV_BUF_LENGTH];
   unsigned char  dataLength;
   ZeissUByte dataType;
   std::string label;
   if (!deviceInfo_[0x12].present)
      return DEVICE_OK; // no TubeLens, lets not make a fuss
   for (ZeissLong i=0; (i<= deviceInfo_[0x12].maxPos) && (i < 5); i++) {
      memset(data, 0, RCV_BUF_LENGTH);
      dataLength =0;
      GetPermanentParameter(device, core, (ZeissUShort) 0x1430, (ZeissByte) (0x10 + ((i)*0x10)), dataType, data, dataLength);
      std::ostringstream os;
      os << (i+1) << "-";
      label = os.str() + std::string(reinterpret_cast<char*> (data));
      tubeLensList_[i] = label;
   }
   return 0;
}

/*
 * Reads SidePort Labels  from the microscope and stores them in a static array
 */
int ZeissHub::GetSidePortLabels(MM::Device& device, MM::Core& core)
{
   unsigned char data[RCV_BUF_LENGTH];
   unsigned char  dataLength;
   ZeissUByte dataType;
   std::string label;
   if (!deviceInfo_[0x14].present)
      return DEVICE_OK; // no SidePort, lets not make a fuss
   for (ZeissLong i=0; (i<= deviceInfo_[0x14].maxPos) && (i < 3); i++) {
      memset(data, 0, RCV_BUF_LENGTH);
      dataLength =0;
      GetPermanentParameter(device, core, (ZeissUShort) 0x1450, (ZeissByte) (0x41 + ((i)*0x10)), dataType, data, dataLength);
      std::ostringstream os;
      os << (i+1) << "-";
      label = os.str() + std::string(reinterpret_cast<char*> (data));
      std::string::size_type loc = label.find("Aquila.SideportElement_",0);
      if (loc != std::string::npos)
            label = label.substr(0,2) + label.substr(25);
      sidePortList_[i] = label;
   }
   return 0;
}


/*
 * Reads Condenser Labels  from the microscope and stores them in a static array
 */
int ZeissHub::GetCondenserLabels(MM::Device& device, MM::Core& core)
{
   unsigned char data[RCV_BUF_LENGTH];
   unsigned char  dataLength;
   ZeissUByte dataType;
   std::string label;
   if (!deviceInfo_[0x22].present) 
   {
      return DEVICE_OK; // no Condenser, lets not make a fuss
   }
   for (ZeissLong i=0; (i<= deviceInfo_[0x22].maxPos) && (i < 8); i++) {
      memset(data, 0, RCV_BUF_LENGTH);
      dataLength = 0;
      GetPermanentParameter(device, core, (ZeissUShort) 0x1470, (ZeissByte) (0x15 + ((i)*8)), dataType, data, dataLength);
      std::ostringstream os;
      os << (i+1) << "-";
      label = os.str() + std::string(reinterpret_cast<char*> (data));
      condenserList_[i] = label;
   }
   return 0;
}

int ZeissHub::GetPermanentParameter(MM::Device& device, MM::Core& core, ZeissUShort descriptor, ZeissByte entry, ZeissUByte& dataType, unsigned char* data, unsigned char& dataLength)
{
   int ret;
   const int commandLength = 8;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x05;
   // Read command, immediate answer
   command[1] = 0x18;
   // Command Number
   command[2] = 0x15;
   // ProcessID (first data byte)
   command[3] = 0x11;
   // SubID (04 = ReadParameter(permanent))
   command[4] = 0x04;
   descriptor = ntohs(descriptor);
   memcpy((void *) &(command[5]), &descriptor, ZeissShortSize);
   // Byte entry
   command[7] = entry;

   ret = ExecuteCommand(device, core, command, commandLength);
   if (ret != DEVICE_OK)
      return ret;

   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 7;
   unsigned char signature[] = {0x08,  0x15, 0x11, 0x04, command[5], command[6], command[7]};
   ret = GetAnswer(device, core, response, responseLength, signature, 1, signatureLength);
   if (ret != DEVICE_OK)
      return ret;

   // if we have the right answer, data is at position 9 and has a length we can calculate from the first byte in the answer
   if (response[0] > 5) {
      dataType = response[8];
      memcpy(data, response+9, response[0]-5);
      dataLength = response[0] - 5;
   } else
      dataLength = 0;

   data[dataLength-1] = 0;
   return DEVICE_OK;
}


/*
 * Queries the microscope for the device scaling strings
 * Access function for version information
 */
int ZeissHub::GetVersion(MM::Device& device, MM::Core& core, std::string& ver)
{
   if (!scopeInitialized_) {
      int ret = Initialize(device, core);
      if (ret != DEVICE_OK)
         return ret;
   }
   ver = version_;
   return DEVICE_OK;
}

/**
 * Sends command to serial port
 * The first (10 02 targetDevice 11) and last part of the command (10 03) are added here
 * @targetDevice = CAN address.  This defaults to the microscope's hub CAN address (either AXIOOBSERVER or AXIOIMAGER.  Definite Focus and Colibri have their own CAN address
 */
int ZeissHub::ExecuteCommand(MM::Device& device, MM::Core& core, const unsigned char* command, int commandLength, unsigned char targetDevice) 
{
   // needs a lock because the monitoringThread will also use this function
   MMThreadGuard(this->executeLock_);

   if (targetDevice <= 0)
      targetDevice = targetDevice_;

   // Prepare command according to CAN29 Protocol
   std::vector<unsigned char> preparedCommand(commandLength + 20); // make provision for doubling tens
   preparedCommand[0] = 0x10;
   preparedCommand[1] = 0x02;
   preparedCommand[2] = targetDevice;
   preparedCommand[3] = 0x11;
   // copy command into preparedCommand, but double 0x10
   int tenCounter = 0;
   for (int i=0; i< commandLength; i++) {
      preparedCommand[i+4+tenCounter] = command[i];
      if (command[i]==0x10) {
         tenCounter++;
         preparedCommand[i+4+tenCounter] = command[i];
      }
      if (command[i]==0x0D) {
         preparedCommand[i+4+tenCounter] = 0x10;
         tenCounter++;
         preparedCommand[i+4+tenCounter] = command[i];
      }
      
   }
   preparedCommand[commandLength+4+tenCounter]=0x10;
   preparedCommand[commandLength+5+tenCounter]=0x03;

   // send command
   int ret = core.WriteToSerial(&device, port_.c_str(), &(preparedCommand[0]), (unsigned long) commandLength + tenCounter + 6);
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            

   return DEVICE_OK;                                                         
}

/**
 * Receive answers from the microscope and stores them in rcvBuf_
 * Strip message start, target address and source address
 */
int ZeissHub::GetAnswer(MM::Device& device, MM::Core& core, unsigned char* answer, unsigned long &answerLength) 
{     
   int ret(DEVICE_OK);
   const unsigned long dataLength = 1;
   bool terminatorFound = false;
   bool timeOut = false;
   MM::MMTime startTime = core.GetCurrentMMTime();
   bool tenFound = false;
   unsigned long charsRead;
   unsigned char dataRead[RCV_BUF_LENGTH];
   memset(dataRead, 0, RCV_BUF_LENGTH);
   long dataReadLength = 0;

   while (!terminatorFound && !timeOut && (dataReadLength < RCV_BUF_LENGTH)) {
      ret = core.ReadFromSerial(&device, port_.c_str(), rcvBuf_, dataLength, charsRead); 
      if (charsRead > 0) {
        if (ret != DEVICE_OK) 
           timeOut = true;
        memcpy((void *)(dataRead + dataReadLength), rcvBuf_, 1); 
        if (tenFound) {
           if (rcvBuf_[0] == 3)
              terminatorFound = true;
           // There is some weird stuff going on here.  This works most of the time
           else if (rcvBuf_[0] == 0x10) {
              tenFound = false;
              dataReadLength -= 1;
           } else if (rcvBuf_[0] == 0x0D) {
              tenFound = false;
              // make current position of 0x0D available
              dataReadLength -= 1;
              // overwrite the 0x10 preceeding the original 0x0D with 0X0D
              dataRead[dataReadLength] = 0x0D;
           }
           else {
              tenFound = false;
           }
         }
         else if (rcvBuf_[0] == 0x10)
            tenFound = true;
         dataReadLength += dataLength;
      }
      if ((core.GetCurrentMMTime() - startTime) > timeOutTime_)
         timeOut = true;
   }
   
   // strip message start, target address and source address
   if (dataReadLength >= 4)
   {
      memcpy ((void *) answer,  dataRead + 4, dataReadLength -4); 
      answerLength = dataReadLength - 4;
   }
   else { 
      std::ostringstream os;
      os << "Time out in answer.  Read so far: ";
      for (int i =0; i < dataReadLength; i++) 
         os << std::hex << (unsigned int) answer[i] << " ";
      core.LogMessage(&device, os.str().c_str(), false);
      return ERR_ANSWER_TIMEOUT;
   }


   if (terminatorFound)
      core.LogMessage(&device, "Found terminator in Scope answer", true);
   else if (timeOut)
      core.LogMessage(&device, "Timeout in Scope answer", true);

   std::ostringstream os;
   os << "Answer(hex): ";
   for (unsigned long i=0; i< answerLength; i++)
      os << std::hex << (unsigned int) answer[i] << " ";
   core.LogMessage(&device, os.str().c_str(), true);

   return DEVICE_OK;                                                         
}
         
/**
 * Same as GetAnswer, but check that this answer matches the given signature
 * Signature starts at the first returned byte, check for signatureLength bytes
 * Timeout if the correct answer is not found in time
*/ int ZeissHub::GetAnswer(MM::Device& device, MM::Core& core, unsigned char* answer, unsigned long &answerLength, unsigned char* signature, unsigned long signatureStart, unsigned long signatureLength) {
   bool timeOut = false;
   MM::MMTime startTime = core.GetCurrentMMTime();
   int ret = DEVICE_OK;
   // We are looking for a signature in the answer that runs from byte signatureStart
   while (!signatureFound(answer, signature, signatureStart, signatureLength) && !timeOut) {
      ret = GetAnswer(device, core, answer, answerLength);
      if (ret != DEVICE_OK)
         return ret;
      if ((core.GetCurrentMMTime() - startTime) > timeOutTime_) {
         timeOut = true;
         ret = ERR_ANSWER_TIMEOUT;
      }
   }
   return ret;
}

int ZeissHub::ClearPort(MM::Device& device, MM::Core& core)
{
   // Clear contents of serial port 
   const unsigned int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while (read == bufSize)
   {
      ret = core.ReadFromSerial(&device, port_.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
} 

bool ZeissHub::signatureFound(unsigned char* answer, unsigned char* signature, unsigned long signatureStart, unsigned long signatureLength) {
   unsigned long i = signatureStart;
   while ( (answer[i] == signature[i-signatureStart]) && i< (signatureLength + signatureStart))
      i++;
   if (i >= (signatureLength + signatureStart)) {
      return true;
   }
   return false;
}

///////////// Access functions for device status ///////////////
/*
 * Sets position in scope model.  
 */
int ZeissHub::SetModelPosition(ZeissUByte devId, ZeissLong position) {
   MMThreadGuard guard(mutex);
   deviceInfo_[devId].currentPos = position;
   return DEVICE_OK;
}

/*
 * Sets Upper Hardware Stop in scope model 
 */
int ZeissHub::SetUpperHardwareStop(ZeissUByte devId, ZeissLong position) {
   MMThreadGuard guard(mutex);
   deviceInfo_[devId].upperHardwareStop = position;
   return DEVICE_OK;
}

/*
 * Sets Lower Hardware Stop in scope model 
 */
int ZeissHub::SetLowerHardwareStop(ZeissUByte devId, ZeissLong position) {
   MMThreadGuard guard(mutex);
   deviceInfo_[devId].lowerHardwareStop = position;
   return DEVICE_OK;
}

/*
 * Gets Upper Hardware Stop in scope model 
 */
int ZeissHub::GetUpperHardwareStop(ZeissUByte devId, ZeissLong& position) {
   MMThreadGuard guard(mutex);
   position = deviceInfo_[devId].upperHardwareStop;
   return DEVICE_OK;
}

/*
 * Gets Lower Hardware Stop in scope model 
 */
int ZeissHub::GetLowerHardwareStop(ZeissUByte devId, ZeissLong& position) {
   MMThreadGuard guard(mutex);
   position = deviceInfo_[devId].lowerHardwareStop;
   return DEVICE_OK;
}

/*
 * Sets status in scope model.  
 */
int ZeissHub::SetModelStatus(ZeissUByte devId, ZeissULong status) {
   MMThreadGuard guard(mutex);
   deviceInfo_[devId].status = status;
   return DEVICE_OK;
}


/*
 * Sets busy flag in scope model.  
 */
int ZeissHub::SetModelBusy(ZeissUByte devId, bool busy) {
   MMThreadGuard guard(mutex);
   deviceInfo_[devId].busy = busy;
   return DEVICE_OK;
}

/*
 * Starts initialize or returns cached position
 */
int ZeissHub::GetModelPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& position) {
   if (! scopeInitialized_) {
      int ret = Initialize(device, core);
      if (ret != DEVICE_OK)
         return ret;
   }
	MMThreadGuard guard(mutex);
   position = deviceInfo_[devId].currentPos;
   // enable for debugging
   //std::ostringstream os;
   //os << "GetModel Position is reporting position " << position << " for device with ID: " << std::hex << (unsigned int) devId;
   //core.LogMessage (&device, os.str().c_str(), false);
   return DEVICE_OK;
}


/*
 * Starts initialize or returns number of positions
 */
int ZeissHub::GetModelMaxPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& maxPosition) {
   if (! scopeInitialized_) {
      int ret = Initialize(device, core);
      if (ret != DEVICE_OK)
         return ret;
   }
   MMThreadGuard guard(mutex);
   maxPosition = deviceInfo_[devId].maxPos;
   return DEVICE_OK;

}

/*
 * Returns status of device or starts initialize if not initialized yet
 */
int ZeissHub::GetModelStatus(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissULong& status) {
   if (! scopeInitialized_) {
      int ret = Initialize(device, core);
      if (ret != DEVICE_OK)
         return ret;
   }
   MMThreadGuard guard(mutex);
   status = deviceInfo_[devId].status;
   return DEVICE_OK;
}

/*
 * Returns presence of device or starts initialize if not initialized yet
 */
int ZeissHub::GetModelPresent(MM::Device& device, MM::Core& core, ZeissUByte devId, bool &present) {
   if (! scopeInitialized_) {
      int ret = Initialize(device, core);
      if (ret != DEVICE_OK)
         return ret;
   }
  MMThreadGuard guard(mutex);
   present = deviceInfo_[devId].present;
   return DEVICE_OK;
}

/*
 * Returns busy flag of device or starts initialize if not initialized yet
 */
int ZeissHub::GetModelBusy(MM::Device& device, MM::Core& core, ZeissUByte devId, bool &busy) {
   if (! scopeInitialized_) {
      int ret = Initialize(device, core);
      if (ret != DEVICE_OK)
         return ret;
   }
   MMThreadGuard guard(mutex);
   busy = deviceInfo_[devId].busy;
   return DEVICE_OK;
}
