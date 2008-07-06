///////////////////////////////////////////////////////////////////////////////
// FILE:       ZeissCAN29.cpp
// PROJECT:    MicroManage
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
//                this under the LGPL on 1/16/2008 (and again on 7/3/2208 after changes 
//                to the code).  If you modify this code using information you obtained 
//                under a NDA with Zeiss, you will need to ask Zeiss whether you can release 
//                your modifications.  
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



#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#else
#include <netinet/in.h>
#endif

#include "ZeissCAN29.h"
#include "../../MMDevice/ModuleInterface.h"
#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>

// TODO: linux entry code
// Note that this only works with gcc (which we should be testing for)
#ifdef __GNUC__
void __attribute__ ((constructor)) my_init(void)
{
}
void __attribute__ ((destructor)) my_fini(void)
{
}
#endif

// windows dll entry code
#ifdef WIN32
   BOOL APIENTRY DllMain( HANDLE /*hModule*/,                                
                          DWORD  ul_reason_for_call,                         
                          LPVOID /*lpReserved*/                              
                   )                                                         
   {                                                                         
      switch (ul_reason_for_call)                                            
      {                                                                      
      case DLL_PROCESS_ATTACH:                                               
      break;                                                                 
      case DLL_THREAD_ATTACH:    
      break;                                                                 
      case DLL_THREAD_DETACH:
      break;
      case DLL_PROCESS_DETACH:
      break;
      }
       return TRUE;
   }
#endif

using namespace std;
MM_THREAD_GUARD mutex;

static ZeissDeviceInfo g_deviceInfo[MAXNUMBERDEVICES];
std::string ZeissHub::reflectorList_[10];
std::string ZeissHub::objectiveList_[7];
std::string ZeissHub::tubeLensList_[5];
std::string ZeissHub::sidePortList_[3];
std::string ZeissHub::condenserList_[7];
//static std::vector<ZeissUByte > g_commandGroup; // relates device to commandgroup, initialized in constructor
ZeissUByte g_commandGroup[MAXNUMBERDEVICES];

ZeissHub g_hub;

///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

// Zeiss Devices
const char* g_ZeissDeviceName = "ZeissScope";
const char* g_ZeissReflector = "ZeissReflectorTurret";
const char* g_ZeissNosePiece = "ZeissObjectiveTurret";
const char* g_ZeissFieldDiaphragm = "ZeissFieldDiaphragm";
const char* g_ZeissApertureDiaphragm = "ZeissApertureDiaphragm";
const char* g_ZeissFocusAxis = "ZeissFocusAxis";
const char* g_ZeissTubeLens = "ZeissTubeLens";
const char* g_ZeissTubeLensShutter = "ZeissTubeLensShutter";
const char* g_ZeissSidePort = "ZeissSidePort";
const char* g_ZeissReflectedLightShutter = "ZeissReflectedLightShutter";
const char* g_ZeissTransmittedLightShutter = "ZeissTransmittedLightShutter";
const char* g_ZeissHalogenLightSwitch = "ZeissHalogenLightSwitch";
const char* g_ZeissRLFLAttenuator = "ZeissRL-FLAttenuator";
const char* g_ZeissCondenserContrast = "ZeissCondenserContrast";
const char* g_ZeissCondenserAperture = "ZeissCondenserAperture";
const char* g_ZeissXYStage = "ZeissXYStage";
const char* g_ZeissHBOLamp = "ZeissHBOLamp";
const char* g_ZeissHalogenLamp = "ZeissHalogenLamp";
const char* g_ZeissLSMPort = "ZeissLSMPort";
const char* g_ZeissBasePort = "ZeissBasePort";
const char* g_ZeissUniblitz = "ZeissUniblitz";
const char* g_ZeissFilterWheel = "ZeissFilterWheel";

// List of Device numbers (from Zeiss documentation)
ZeissUByte g_ReflectorChanger = 0x01;
ZeissUByte g_NosePieceChanger = 0x02;
ZeissUByte g_FieldDiaphragmServo = 0x08; // Reflected Light
ZeissUByte g_ApertureDiaphragmServo = 0x09; // Reflected Light
ZeissUByte g_FocusAxis = 0x0F;
ZeissUByte g_TubeLensChanger = 0x12;
ZeissUByte g_TubeShutter = 0x13;
ZeissUByte g_SidePortChanger = 0x14;
ZeissUByte g_ReflectedLightShutter = 0x1D;
ZeissUByte g_TransmittedLightShutter = 0x1E; // this is not the halogen lamp!
ZeissUByte g_HalogenLampSwitch = 0x1F; // Read only
ZeissUByte g_RLFLAttenuatorChanger = 0x21; // reflected light
ZeissUByte g_CondenserContrastChanger = 0x22;
ZeissUByte g_CondenserApertureServo = 0x23;
ZeissUByte g_StageXAxis = 0x26;
ZeissUByte g_StageYAxis = 0x27;
ZeissUByte g_HBOLampServo = 0x28;
ZeissUByte g_HalogenLampServo = 0x29; 
ZeissUByte g_LSMPortChanger = 0x2B;  // RearPort, reflected light
ZeissUByte g_BasePortChanger = 0x40;
ZeissUByte g_UniblitzShutter = 0x41;
ZeissUByte g_FilterWheelChanger = 0x42;

///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_ZeissDeviceName,"Zeiss AxioObserver controlled through serial interface");
   AddAvailableDeviceName(g_ZeissReflector,"Reflector Turret (dichroics)"); 
   AddAvailableDeviceName(g_ZeissNosePiece,"Objective Turret");
   AddAvailableDeviceName(g_ZeissFieldDiaphragm,"Field Diaphragm (fluorescence)");
   AddAvailableDeviceName(g_ZeissApertureDiaphragm,"Aperture Diaphragm (fluorescence)");
   AddAvailableDeviceName(g_ZeissFocusAxis,"Z-drive");
   AddAvailableDeviceName(g_ZeissXYStage,"XYStage");
   AddAvailableDeviceName(g_ZeissTubeLens,"Tube Lens (optovar)");
   AddAvailableDeviceName(g_ZeissTubeLensShutter,"Tube Lens Shutter");
   AddAvailableDeviceName(g_ZeissSidePort,"Side Port");
   AddAvailableDeviceName(g_ZeissReflectedLightShutter,"Reflected Light Shutter"); 
   AddAvailableDeviceName(g_ZeissTransmittedLightShutter,"Transmitted Light Shutter"); 
   AddAvailableDeviceName(g_ZeissRLFLAttenuator,"Reflected (fluorescence) light attenuator");
   AddAvailableDeviceName(g_ZeissCondenserContrast,"Condenser Contrast");
   AddAvailableDeviceName(g_ZeissCondenserAperture,"Condenser Aperture");
   AddAvailableDeviceName(g_ZeissHBOLamp,"HBO Lamp");
   AddAvailableDeviceName(g_ZeissHalogenLamp,"Halogen Lamp"); 
   AddAvailableDeviceName(g_ZeissLSMPort,"LSM Port (rearPort)"); 
   AddAvailableDeviceName(g_ZeissBasePort,"Base Port switcher"); 
   AddAvailableDeviceName(g_ZeissUniblitz,"Uniblitz Shutter"); 
   AddAvailableDeviceName(g_ZeissFilterWheel,"Filter Wheel"); 
}
using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
   if (deviceName == 0)                                                      
       return 0;

   if (strcmp(deviceName, g_ZeissDeviceName) == 0)
        return new ZeissScope();
   else if (strcmp(deviceName, g_ZeissReflector) == 0)
        return new ReflectorTurret(g_ReflectorChanger, g_ZeissReflector, "Reflector Turret");
   else if (strcmp(deviceName, g_ZeissNosePiece) == 0)
        return new ObjectiveTurret(g_NosePieceChanger, g_ZeissNosePiece, "Objective Turret");
   else if (strcmp(deviceName, g_ZeissFieldDiaphragm) == 0)
        return new Servo(g_FieldDiaphragmServo, g_ZeissFieldDiaphragm, "Field Diaphragm");
   else if (strcmp(deviceName, g_ZeissApertureDiaphragm) == 0)
        return new Servo(g_ApertureDiaphragmServo, g_ZeissApertureDiaphragm, "Aperture Diaphragm");
   else if (strcmp(deviceName, g_ZeissFocusAxis) == 0)
        return new Axis(g_FocusAxis, g_ZeissFocusAxis, "Z-drive");
   else if (strcmp(deviceName, g_ZeissXYStage) == 0)
	   return new XYStage();
   else if (strcmp(deviceName, g_ZeissTubeLens) == 0)
        return new TubeLensTurret(g_TubeLensChanger, g_ZeissTubeLens, "Tube Lens (optoavar)");
   else if (strcmp(deviceName, g_ZeissTubeLensShutter) == 0)
        return new Shutter(g_TubeShutter, g_ZeissTubeLensShutter, "Tube Lens Shutter");
   else if (strcmp(deviceName, g_ZeissSidePort) == 0)
        return new SidePortTurret(g_SidePortChanger, g_ZeissSidePort, "Side Port");
   else if (strcmp(deviceName, g_ZeissReflectedLightShutter) == 0)
        return new  Shutter(g_ReflectedLightShutter, g_ZeissReflectedLightShutter, "Zeiss Reflected Light Shutter");
   else if (strcmp(deviceName, g_ZeissTransmittedLightShutter) == 0)
        return new  Shutter(g_TransmittedLightShutter, g_ZeissTransmittedLightShutter, "Zeiss Transmitted Light Shutter");
   else if (strcmp(deviceName, g_ZeissRLFLAttenuator) == 0)
        return new Turret(g_RLFLAttenuatorChanger, g_ZeissRLFLAttenuator, "Attenuator (reflected light)");
   else if (strcmp(deviceName, g_ZeissCondenserContrast) == 0)
        return new CondenserTurret(g_CondenserContrastChanger, g_ZeissCondenserContrast, "Condenser Contrast");
   else if (strcmp(deviceName, g_ZeissCondenserAperture) == 0)
        return new Servo(g_CondenserApertureServo, g_ZeissCondenserAperture, "Condenser Aperture");
   else if (strcmp(deviceName, g_ZeissHBOLamp) == 0)
        return new Servo(g_HBOLampServo, g_ZeissHBOLamp, "HBO Lamp intensity");
   else if (strcmp(deviceName, g_ZeissHalogenLamp) == 0)
        return new Servo(g_HalogenLampServo, g_ZeissHalogenLamp, "Halogen Lamp intensity");
   else if (strcmp(deviceName, g_ZeissLSMPort) == 0)
        return new Turret(g_LSMPortChanger, g_ZeissLSMPort, "LSM Port (rear port)");
   else if (strcmp(deviceName, g_ZeissBasePort) == 0)
        return new Turret(g_BasePortChanger, g_ZeissBasePort, "Base Port");
   else if (strcmp(deviceName, g_ZeissUniblitz) == 0)
        return new Shutter(g_UniblitzShutter, g_ZeissUniblitz, "Uniblitz Shutter");
   else if (strcmp(deviceName, g_ZeissFilterWheel) == 0)
        return new Turret(g_FilterWheelChanger, g_ZeissFilterWheel, "Filter Wheel");

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}

//////////////////////////////////////////
// Interface to the Zeis microscope
//
ZeissHub::ZeissHub() :
   portInitialized_ (false),
   monitoringThread_(0),
   timeOutTime_(250000),
   scopeInitialized_ (false)
{
   // initialize deviceinfo
   for (int i=0; i< MAXNUMBERDEVICES; i++) {
      g_deviceInfo[i].present = false;
   }
   // Set vector g_commandGroup
   // default all devices to Changer
   for (int i=0; i< MAXNUMBERDEVICES; i++) {
      g_commandGroup[i] = 0xA1;
   }
   // Set System devices
   g_commandGroup[0x15]=0xA0; g_commandGroup[0x18]=0xA0; g_commandGroup[0x19]=0xA0;
   // Set Servos
   g_commandGroup[0x08]=0xA2; g_commandGroup[0x09]=0xA2; g_commandGroup[0x28]=0xA2; g_commandGroup[0x29]=0xA2, g_commandGroup[0x2D]=0xA2;
   // Set Axis
   g_commandGroup[0x0F]=0xA3; g_commandGroup[0x25]=0xA3; g_commandGroup[0x26]=0xA3; g_commandGroup[0x27]=0xA3;

   MM_THREAD_INITIALIZE_GUARD(&mutex);
}

ZeissHub::~ZeissHub()
{
   printf("in Hub destructor\n");
   MM_THREAD_DELETE_GUARD(&mutex);
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
   
   ostringstream os;
   os << "Initializing Hub";
   core.LogMessage (&device, os.str().c_str(), false);
   os.str("");
   // empty the Rx serial buffer before sending commands
   ClearRcvBuf();
   ClearPort(device, core);

   int ret = GetVersion(device, core);
   if (ret != DEVICE_OK) {
      // Sometimes we time out on the first try....  Try twice more...
      ret = GetVersion(device, core);
      if (ret != DEVICE_OK) {
         ret = GetVersion(device, core);
         if (ret != DEVICE_OK)
            return ret;
      }
   }

   availableDevices_.clear();
   ret = FindDevices(device, core);
   if (ret != DEVICE_OK)
      return ret;

   for (ZeissUByte i=0; i< availableDevices_.size(); i++) {
      // report on devices found
      os << "Found device: " << hex << (unsigned int) availableDevices_[i] << ", group: " << hex << (unsigned int) g_commandGroup[availableDevices_[i]];
      core.LogMessage (&device, os.str().c_str(), false);
      os.str("");

      // reset  the 'vectors' in ZeissDeviceInfo so that they do not grow out of bounds between invocations of the adapter:
      g_deviceInfo[availableDevices_[i]].deviceScalings.clear();
      g_deviceInfo[availableDevices_[i]].nativeScale.clear();
      g_deviceInfo[availableDevices_[i]].scaledScale.clear();
   }

   // Get the status for all devices found in this scope
   for (ZeissUByte i=0; i < availableDevices_.size(); i++) {
      ZeissUByte devId = availableDevices_[i];
      ZeissLong position, maxPosition;
      GetPosition(device, core, g_commandGroup[devId], (ZeissUByte)devId, position);
      g_deviceInfo[devId].currentPos = position;
      GetMaxPosition(device, core, g_commandGroup[devId], devId, maxPosition);
      g_deviceInfo[devId].maxPos = maxPosition;
      if ((g_commandGroup[devId] == (ZeissUByte) 0xA2) || (g_commandGroup[devId] == (ZeissUByte) 0xA3)) { // only Axis and Servos have scaling information
         GetDeviceScalings(device, core, g_commandGroup[devId], devId, g_deviceInfo[devId]);
         for (unsigned int j=0; j<g_deviceInfo[devId].deviceScalings.size(); j++) {
            if (g_deviceInfo[devId].deviceScalings[j] != "native") {
               GetScalingTable(device, core, g_commandGroup[devId], devId, g_deviceInfo[devId],g_deviceInfo[devId].deviceScalings[j]);
            }
         }
         if (g_commandGroup[devId] == 0xA3)
            GetMeasuringOrigin(device, core, g_commandGroup[devId], devId, g_deviceInfo[devId]);
      }
      g_deviceInfo[devId].busy = false;
      g_deviceInfo[devId].lastRequestTime == core.GetCurrentMMTime();
      g_deviceInfo[devId].lastUpdateTime == core.GetCurrentMMTime();

      os << "Device " << std::hex << (unsigned int) devId << " has ";
      os << std::dec << maxPosition << " positions and is now at position "<< position;
      core.LogMessage(&device, os.str().c_str(), false);
      os.str("");
      g_deviceInfo[devId].print(device, core);
   }
 
   // get labels for objectives and reflectors
   GetReflectorLabels(device, core);
   os << "Reflectors: ";
   for (int i=0; i< g_deviceInfo[0x01].maxPos && i < 10;i++) {
      os << "\n" << reflectorList_[i].c_str();
   }
   core.LogMessage(&device, os.str().c_str(), false);

   GetObjectiveLabels(device, core);
   os.str("");
   os <<"Objectives: ";
   for (int i=0; i< g_deviceInfo[0x02].maxPos && i < 8;i++) {
      os << "\n" << objectiveList_[i].c_str();
   }
   core.LogMessage(&device, os.str().c_str(), false);

   GetTubeLensLabels(device, core);
   os.str("");
   os <<"TubeLens: ";
   for (int i=0; i< g_deviceInfo[g_TubeLensChanger].maxPos && i < 5;i++) {
      os << "\n" << tubeLensList_[i].c_str();
   }
   core.LogMessage(&device, os.str().c_str(), false);

   GetSidePortLabels(device, core);
   os.str("");
   os <<"SidePort: ";
   for (int i=0; i< g_deviceInfo[0x14].maxPos && i < 3;i++) {
      os << "\n" << sidePortList_[i].c_str();
   }
   core.LogMessage(&device, os.str().c_str(), false);

   GetCondenserLabels(device, core);
   os.str("");
   os <<"Condenser: ";
   for (int i=0; i< g_deviceInfo[0x22].maxPos && i < 8;i++) {
      os << "\n" << condenserList_[i].c_str();
   }
   core.LogMessage(&device, os.str().c_str(), false);

   monitoringThread_ = new ZeissMonitoringThread(device, core);
   monitoringThread_->Start();
   scopeInitialized_ = true;
   return DEVICE_OK;
}

/**
 * Reads in version info
 * Stores version info in variable version_
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

   int ret = ExecuteCommand(device, core,  command, commandLength);
   if (ret != DEVICE_OK)
      return ret;

   // Version string starts in 6th character, length is in first char
   long unsigned int responseLength = RCV_BUF_LENGTH;
   unsigned char response[RCV_BUF_LENGTH];
   unsigned long signatureLength = 4;
   unsigned char signature[] = { 0x08, command[2], command[3], command[4] };
   ret = GetAnswer(device, core, response, responseLength, signature, 1, signatureLength);
   if (ret != DEVICE_OK)
      return ret;
   response[responseLength] = 0;
   string answer((char *)response);
   version_ = "Application version: " + answer.substr(6, atoi(answer.substr(0,1).c_str()));
  
   if (ret != DEVICE_OK)
      return ret;

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
         vector<char> tmp(response[0]-2);
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
   vector<unsigned char> command(commandLength);
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
            g_deviceInfo[response[5]].present = true;
         }
         g_deviceInfo[response[5]].status = response[6];
      }
   } while (response[1] == 0x05); // last response starts with 0x09
  
   return DEVICE_OK;
}

/*
 * Reads reflector labels from the microscope and stores them in a static array
 */
int ZeissHub::GetReflectorLabels(MM::Device& device, MM::Core& core)
{
   unsigned char data[g_hub.RCV_BUF_LENGTH];
   unsigned char  dataLength;
   ZeissUByte dataType;
   std::string label;
   if (!g_deviceInfo[0x01].present)
      return 0; // no reflectors, lets not make a fuss
   for (ZeissUShort i=0; i< g_deviceInfo[0x01].maxPos && i < 10; i++) {
      // Short name 1
      memset(data, 0, g_hub.RCV_BUF_LENGTH);
      GetPermanentParameter(device, core, (ZeissUShort) 0x1500 + i + 1, 0x15, dataType, data, dataLength);
      std::ostringstream os;
      os << (i+1) << "-";
      label = os.str() + std::string(reinterpret_cast<char*> (data));
      size_t pos = label.find(16);
      if (pos != std::string::npos)
         label.replace(pos, 1, " ");
      // Short name 2
      memset(data, 0, g_hub.RCV_BUF_LENGTH);
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
   unsigned char data[g_hub.RCV_BUF_LENGTH];
   unsigned char  dataLength;
   ZeissUByte dataType;
   std::string label;
   if (!g_deviceInfo[0x02].present)
      return DEVICE_OK; // no objectives, lets not make a fuss
   for (ZeissLong i=0; (i<= g_deviceInfo[0x02].maxPos) && (i < 7); i++) {
      memset(data, 0, g_hub.RCV_BUF_LENGTH);
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
   unsigned char data[g_hub.RCV_BUF_LENGTH];
   unsigned char  dataLength;
   ZeissUByte dataType;
   std::string label;
   if (!g_deviceInfo[0x12].present)
      return DEVICE_OK; // no TubeLens, lets not make a fuss
   for (ZeissLong i=0; (i<= g_deviceInfo[0x12].maxPos) && (i < 5); i++) {
      memset(data, 0, g_hub.RCV_BUF_LENGTH);
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
   unsigned char data[g_hub.RCV_BUF_LENGTH];
   unsigned char  dataLength;
   ZeissUByte dataType;
   std::string label;
   if (!g_deviceInfo[0x14].present)
      return DEVICE_OK; // no SidePort, lets not make a fuss
   for (ZeissLong i=0; (i<= g_deviceInfo[0x14].maxPos) && (i < 3); i++) {
      memset(data, 0, g_hub.RCV_BUF_LENGTH);
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
   unsigned char data[g_hub.RCV_BUF_LENGTH];
   unsigned char  dataLength;
   ZeissUByte dataType;
   std::string label;
   if (!g_deviceInfo[0x22].present)
      return DEVICE_OK; // no Condenser, lets not make a fuss
   for (ZeissLong i=0; (i<= g_deviceInfo[0x22].maxPos) && (i < 7); i++) {
      memset(data, 0, g_hub.RCV_BUF_LENGTH);
      dataLength =0;
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

   long unsigned int responseLength = g_hub.RCV_BUF_LENGTH;
   unsigned char response[g_hub.RCV_BUF_LENGTH];
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

   data[dataLength] = 0;
   return DEVICE_OK;
}

/**
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
 * The first (10 02 19 11) and last part of the command (10 03) are added here
 */
int ZeissHub::ExecuteCommand(MM::Device& device, MM::Core& core, const unsigned char* command, int commandLength, unsigned char targetDevice) 
{
   // Prepare command according to CAN29 Protocol
   vector<unsigned char> preparedCommand(commandLength + 20); // make provision for doubling tens
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
   }
   preparedCommand[commandLength+4+tenCounter]=0x10;
   preparedCommand[commandLength+5+tenCounter]=0x03;

   // int preparedCommandLength = commandLength + 6 + tenCounter;
   // send command
   //int ret = core.WriteToSerial(&device, port_.c_str(), &(preparedCommand[0]), (unsigned long) preparedCommand.size());
   int ret = core.WriteToSerial(&device, port_.c_str(), &(preparedCommand[0]), (unsigned long) commandLength + tenCounter + 6);
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
                                                                             
   // core.LogMessage(&device, preparedCommand, true);

   return DEVICE_OK;                                                         
}

/**
 * Receive answers from the microscope and stores them in rcvBuf_
 * Strip message start, target address and source address
 */
int ZeissHub::GetAnswer(MM::Device& device, MM::Core& core, unsigned char* answer, unsigned long &answerLength) 
{     
   int ret(DEVICE_OK);
   long unsigned int dataLength = 1;
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
      //usleep(500);
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
           }
           else {
              tenFound = false;
           }
         }
         else if (rcvBuf_[0] == 0x10)
            tenFound = true;
         dataReadLength += 1;
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
      ostringstream os;
      os << "Time out in answer.  Read so far: ";
      for (int i =0; i < dataReadLength; i++) 
         os << hex << (unsigned int) answer[i] << " ";
      core.LogMessage(&device, os.str().c_str(), false);
      return ERR_ANSWER_TIMEOUT;
   }


   if (terminatorFound)
      core.LogMessage(&device, "Found terminator in Scope answer", true);
   else if (timeOut)
      core.LogMessage(&device, "Timeout in Scope answer", true);

   ostringstream os;
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
   // We are looking for a signature in the answer that runs from byte 0 
   while (!signatureFound(answer, signature, signatureStart, signatureLength) && !timeOut) {
      ret = GetAnswer(device, core, answer, answerLength);
      if (ret != DEVICE_OK)
         return ret;
      // MM::MMTime dif = core.GetCurrentMMTime() - startTime;
      // printf ("Waited for %f usec, timeout: %f usec\n", dif.getUsec(), timeOutTime_.getUsec());
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
   MM_THREAD_GUARD_LOCK(&mutex);
   g_deviceInfo[devId].currentPos = position;
   MM_THREAD_GUARD_UNLOCK(&mutex);
   return DEVICE_OK;
}

/*
 * Sets Upper Hardware Stop in scope model 
 */
int ZeissHub::SetUpperHardwareStop(ZeissUByte devId, ZeissLong position) {
   MM_THREAD_GUARD_LOCK(&mutex);
   g_deviceInfo[devId].upperHardwareStop = position;
   MM_THREAD_GUARD_UNLOCK(&mutex);
   return DEVICE_OK;
}

/*
 * Sets Lower Hardware Stop in scope model 
 */
int ZeissHub::SetLowerHardwareStop(ZeissUByte devId, ZeissLong position) {
   MM_THREAD_GUARD_LOCK(&mutex);
   g_deviceInfo[devId].lowerHardwareStop = position;
   MM_THREAD_GUARD_UNLOCK(&mutex);
   return DEVICE_OK;
}

/*
 * Sets status in scope model.  
 */
int ZeissHub::SetModelStatus(ZeissUByte devId, ZeissULong status) {
   MM_THREAD_GUARD_LOCK(&mutex);
   g_deviceInfo[devId].status = status;
   MM_THREAD_GUARD_UNLOCK(&mutex);
   return DEVICE_OK;
}


/*
 * Sets busy flag in scope model.  
 */
int ZeissHub::SetModelBusy(ZeissUByte devId, bool busy) {
   MM_THREAD_GUARD_LOCK(&mutex);
   g_deviceInfo[devId].busy = busy;
   MM_THREAD_GUARD_UNLOCK(&mutex);
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
   MM_THREAD_GUARD_LOCK(&mutex);
   position = g_deviceInfo[devId].currentPos;
   MM_THREAD_GUARD_UNLOCK(&mutex);
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
   MM_THREAD_GUARD_LOCK(&mutex);
   maxPosition = g_deviceInfo[devId].maxPos;
   MM_THREAD_GUARD_UNLOCK(&mutex);
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
   MM_THREAD_GUARD_LOCK(&mutex);
   status = g_deviceInfo[devId].status;
   MM_THREAD_GUARD_UNLOCK(&mutex);
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
   MM_THREAD_GUARD_LOCK(&mutex);
   present = g_deviceInfo[devId].present;
   MM_THREAD_GUARD_UNLOCK(&mutex);
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
   MM_THREAD_GUARD_LOCK(&mutex);
   busy = g_deviceInfo[devId].busy;
   MM_THREAD_GUARD_UNLOCK(&mutex);
   return DEVICE_OK;
}

/*
 * Utility class for ZeissMonitoringThread
 */
ZeissMessageParser::ZeissMessageParser(unsigned char* inputStream, long inputStreamLength) :
   index_(0)
{
   inputStream_ = inputStream;
   inputStreamLength_ = inputStreamLength;
}

int ZeissMessageParser::GetNextMessage(unsigned char* nextMessage, int& nextMessageLength) {
   bool startFound = false;
   bool endFound = false;
   bool tenFound = false;
   nextMessageLength = 0;
   long remainder = index_;
   while ( (endFound == false) && (index_ < inputStreamLength_) && (nextMessageLength < messageMaxLength_) ) {
      if (tenFound && inputStream_[index_] == 0x02) {
         startFound = true;
         tenFound = false;
      }
      else if (tenFound && inputStream_[index_] == 0x03) {
         endFound = true;
         tenFound = false;
      }
      else if (tenFound && inputStream_[index_] == 0x10) {
         nextMessage[nextMessageLength] = inputStream_[index_];
         nextMessageLength++;
         tenFound = false;
      }
      else if (inputStream_[index_] == 0x10)
         tenFound = true;
      else if (startFound) {
         nextMessage[nextMessageLength] = inputStream_[index_];
         nextMessageLength++;
      }
      index_++;
   }
   if (endFound)
      return 0;
   else {
      // no more complete message found, return the whole stretch we were considering:
      for (long i= remainder; i < inputStreamLength_; i++)
         nextMessage[i-remainder] = inputStream_[i];
      nextMessageLength = inputStreamLength_ - remainder;
      return -1;
   }
}

/*
 * Thread that continuously monitors messages from the Zeiss scope and inserts them into a model of the microscope
 */
ZeissMonitoringThread::ZeissMonitoringThread(MM::Device& device, MM::Core& core) :
   device_ (device),
   core_ (core),
   stop_ (true),
   intervalUs_(5000) // check every 5 ms for new messages, 
{
}

ZeissMonitoringThread::~ZeissMonitoringThread()
{
   printf("Destructing monitoringThread\n");
}

void ZeissMonitoringThread::interpretMessage(unsigned char* message)
{
   //if (!(message[5] == 0)) // only message with Proc Id 0 are meant for us
   //  In reality I see here 0, 6, and 7???
   //   return;
   if (message[3] == 0x07) { // events/unsolicited message
      if (message[6] == 0x01) // leaving settled position
         g_hub.SetModelBusy(message[5], true);
      else if (message[6] == 0x02) { // actual moving position 
         ZeissLong position;
         if (message[4] == 0xA3) {
            memcpy(&position, message + 8, 4);
            position = ntohl(position);
         } else {
            memcpy(&position, message + 8, 2);
            position = ntohs((unsigned short) position);
         }
         g_hub.SetModelPosition(message[7], position);
         g_hub.SetModelBusy(message[7], true);
      }
      else if (message[6] == 0x03) { // target position settled
         ZeissLong position;
         if (message[4] == 0xA3) {
            memcpy(&position, message + 8, 4);
            position = ntohl(position);
         } else {
            memcpy(&position, message + 8, 2);
            position = ntohs((unsigned short) position);
         }
         g_hub.SetModelPosition(message[7], position);
         g_hub.SetModelBusy(message[7], false);
      }
      else if (message[6] == 0x04) { // status changed
         ZeissULong status;
         memcpy(&status, message + 8, 4);
         status = ntohl(status);
         g_hub.SetModelStatus(message[7], status);
         g_hub.SetModelBusy(message[7], !(status & 32)); // 'is settled' bit 
      }
   } else if (message[3] == 0x08) { // Some direct answers that we want to interpret
      if (message[6] == 0x20) { // Axis: Upper hardware stop reached
         g_hub.SetModelBusy(message[5], false);
         ZeissLong position;
         memcpy(&position, message + 8, 4);
         position = ntohl(position);
         g_hub.SetUpperHardwareStop(message[5], position);
         // TODO: How to unlock the stage from here?
      } else if (message[6] == 0x21) { // Axis:: Lower hardware stop reached
         g_hub.SetModelBusy(message[5], false);
         ZeissLong position;
         memcpy(&position, message + 8, 4);
         position = ntohl(position);
         g_hub.SetLowerHardwareStop(message[5], position);
         // TODO: How to unlock the stage from here?
       }
   }
}

MM_THREAD_FUNC_DECL ZeissMonitoringThread::svc(void *arg) {
   ZeissMonitoringThread* thd = (ZeissMonitoringThread*) arg;

   printf ("Starting MonitoringThread\n");

   unsigned long dataLength;
   unsigned long charsRead = 0;
   unsigned long charsRemaining = 0;
   unsigned char rcvBuf[ZeissHub::RCV_BUF_LENGTH];
   memset(rcvBuf, 0, ZeissHub::RCV_BUF_LENGTH);

   while (!thd->stop_) 
   {
      do { 
         dataLength = ZeissHub::RCV_BUF_LENGTH - charsRemaining;
         // Do the scope monitoring stuff here
         int ret = thd->core_.ReadFromSerial(&(thd->device_), g_hub.port_.c_str(), rcvBuf + charsRemaining, dataLength, charsRead); 
         if (ret != DEVICE_OK) {
            ostringstream oss;
            oss << "Monitoring Thread: ERROR while reading from serial port, error code: " << ret;
            thd->core_.LogMessage(&(thd->device_), oss.str().c_str(), false);
         } else if (charsRead > 0) {
            ZeissMessageParser* parser = new ZeissMessageParser(rcvBuf, charsRead + charsRemaining);
            do {
               unsigned char message[ZeissMessageParser::messageMaxLength_];
               int messageLength;
               ret = parser->GetNextMessage(message, messageLength);
               if (ret == 0) {
                  // Report 
                  ostringstream os;
                  os << "Monitoring Thread incoming message: ";
                  for (int i=0; i< messageLength; i++)
                     os << hex << (unsigned int)message[i] << " ";
                  thd->core_.LogMessage(&(thd->device_), os.str().c_str(), true);
                  // and do the real stuff
                  thd->interpretMessage(message);
                }
               else {
                  // no more messages, copy remaining (if any) back to beginning of buffer
                  memset(rcvBuf, 0, ZeissHub::RCV_BUF_LENGTH);
                  for (int i = 0; i < messageLength; i++)
                     rcvBuf[i] = message[i];
                  charsRemaining = messageLength;
               }
            } while (ret == 0);
         }
      } while ((charsRead != 0) && (!thd->stop_)); 

       CDeviceUtils::SleepMs(thd->intervalUs_/1000);
   }
   printf("Monitoring thread finished\n");
   return 0;
}

void ZeissMonitoringThread::Start()
{

   stop_ = false;
   //pthread_create(&thread_, NULL, svc, this);
   MM_THREAD_CREATE(&thread_, svc, this);
   //activate();
   //activate(THR_NEW_LWP | THR_JOINABLE, 1, 1, ACE_THR_PRI_OTHER_MAX);
   //activate(THR_NEW_LWP | THR_JOINABLE, 1, 1, THREAD_PRIORITY_TIME_CRITICAL);
}


/*
 * Base class for all Zeiss Devices (Changers, Servos and Axis)
 */
ZeissDevice::ZeissDevice() 
{
   // targetProcessId_ = 0x12;
}

ZeissDevice::~ZeissDevice()
{
}

int ZeissDevice::GetPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& position)
{
   return g_hub.GetModelPosition(device, core, devId, position);
}

/*
 * Send command to microscope to set position of Servo and Changer. 
 * Do not use this for Axis (override in Axis device)
 */
int ZeissDevice::SetPosition(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, int position, unsigned char targetDevice)
{
   int ret;
   const int commandLength = 8;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x05;
   // Write command, do not expect answer:
   command[1] = 0x1B;
   command[2] = commandGroup; 
   // ProcessID
   command[3] = 0x11;
   // SubID
   command[4] = 0x01;
   // Device ID
   command[5] = devId;
   // position is a short (2-byte) in big endian format...
   ZeissShort tmp = htons((ZeissShort) position);
   memcpy(command+6, &tmp, ZeissShortSize); 
   ostringstream os;
   os << "Setting device "<< hex << (unsigned int) devId << " to position " << dec << position;
   core.LogMessage(&device, os.str().c_str(), false);
   ret = g_hub.ExecuteCommand(device, core,  command, commandLength, targetDevice);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.SetModelBusy(devId, true);

   return DEVICE_OK;
}

/*
 * Requests Lock from Microscope 
 */
int ZeissDevice::SetLock(MM::Device& device, MM::Core& core, ZeissUByte devId, bool on, unsigned char targetDevice)
{
   int ret;
   const int commandLength = 14;
   unsigned char command[commandLength];

   command[0] = 11;
   command[1] = 0x19;
   command[2] = 0xA0; 
   // ProcessID
   command[3] = 0x11;
   // SubID
   if (on)
      command[4] = 0x42;
   else
      command[4] = 0x43;
   // Device ID, here page:
   if (devId < 64)
      command[5] = 0;
   else
      command[5] = 1;
   if (devId > 63)
      devId -= 64;
   ZeissULong hiMask = 0;
   ZeissULong loMask = 0;
   if (devId < 33)
      loMask |= 1 << devId;
   else
      hiMask |= 1 << (devId - 32);
   memcpy(command+6, &hiMask, ZeissLongSize); 
   memcpy(command+10, &loMask, ZeissLongSize); 

   ostringstream os;
   if (on)
      os << "Requesting locking for device "<< hex << (unsigned int) devId ;
   else
      os << "Requesting unlocking for device "<< hex << (unsigned int) devId ;
   core.LogMessage(&device, os.str().c_str(), false);
   ret = g_hub.ExecuteCommand(device, core,  command, commandLength, targetDevice);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}
/*
int ZeissDevice::GetTargetPosition(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, int& position)
{
   targetProcessId_ +=1;
   if (targetProcessId_ >= 254)
      targetProcessId_ = 0x12;
   int ret;
   unsigned char command[8];
   // Size of data bloc
   command[0] = 0x03;
   // Read command, immediate answer
   command[1] = 0x18;
   // 'Changer command??'
   command[2] = commandGroup;
   // ProcessID
   command[3] = targetProcessId_;
   // SubID (01 = target position)
   command[4] = (int) 0x04;
   // Device ID
   command[5] = (ZeissUByte) devId;

   ret = g_hub.ExecuteCommand(device, core, command, 6);
   if (ret != DEVICE_OK)
      return ret;

   long unsigned int responseLength = g_hub.RCV_BUF_LENGTH;
   unsigned char response[responseLength];
   unsigned long signatureLength = 5;
   unsigned char signature[] = {0x04,  0x08, commandGroup, targetProcessId_, command[4]};
   ret = g_hub.GetAnswer(device, core, response, responseLength, signature, signatureLength);
   if (ret != DEVICE_OK)
      return ret;
printf ("response in Get Target Position: \n");
   for (int i=0; i < responseLength; i++)
      printf ("%#.2X ", (unsigned int) response[i]);
   printf("\n");

   ZeissShort tmp;
   memcpy (&tmp, response + 5, ZeissShortSize);
   position = (int) ntohs(tmp);
   printf("Target Position: %d\n", position);
   
   return DEVICE_OK;
}
*/

int ZeissDevice::GetMaxPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, int& position)
{
   return g_hub.GetModelMaxPosition(device, core, devId, position);
}

int ZeissDevice::GetStatus(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissULong& status)
{
   return g_hub.GetModelStatus(device, core, devId, status);
}

int ZeissDevice::GetBusy(MM::Device& device, MM::Core& core,  ZeissUByte devId, bool& busy)
{
   return g_hub.GetModelBusy(device, core, devId, busy);
}

int ZeissDevice::GetPresent(MM::Device& device, MM::Core& core, ZeissUByte devId, bool& present)
{
   return g_hub.GetModelPresent(device, core, devId, present);
}


/////////////////////////////////////////////////////////////
// Utility class to make it easier for 'turret-based' devices
// 
ZeissChanger::ZeissChanger() 
{
}

ZeissChanger::~ZeissChanger()
{
}

int ZeissChanger::SetPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, int position)
{
   return ZeissDevice::SetPosition(device, core, g_commandGroup[devId], devId, position);
}

/////////////////////////////////////////////////////////////
// Utility class to make it easier for 'Servo-based' devices
// 
ZeissServo::ZeissServo() 
{
}

ZeissServo::~ZeissServo()
{
}

int ZeissServo::SetPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, int position)
{
   return ZeissDevice::SetPosition(device, core, g_commandGroup[devId], devId, position);
}

/////////////////////////////////////////////////////////////
// Class for all Axis devices
// 
ZeissAxis::ZeissAxis() 
{
}

ZeissAxis::~ZeissAxis()
{
}

/*
 * Send command to microscope to set position of Axis. 
 */
int ZeissAxis::SetPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, long position, ZeissByte moveMode, unsigned char targetDevice)
{
   int ret;
   const int commandLength = 11;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x08;
   // Write command, do not expect answer:
   command[1] = 0x1B;
   command[2] = 0xA3; 
   // ProcessID
   command[3] = 0x11;
   // SubID
   command[4] = 0x01;
   // Device ID
   command[5] = devId;
   // movemode
   command[6] = moveMode;
   // position is a ZeissLong (4-byte) in big endian format...
   ZeissLong tmp = htonl((ZeissLong) position);
   memcpy(command+7, &tmp, ZeissLongSize); 
   ret = g_hub.ExecuteCommand(device, core,  command, commandLength, targetDevice);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.SetModelBusy(devId, true);

   return DEVICE_OK;
}

/*
 * Send command to microscope to move relative to current position of Axis
 */
int ZeissAxis::SetRelativePosition(MM::Device& device, MM::Core& core, ZeissUByte devId, long increment, ZeissByte moveMode, unsigned char targetDevice)
{
   int ret;
   const int commandLength = 11;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x08;
   // Write command, do not expect answer:
   command[1] = 0x1B;
   command[2] = 0xA3; 
   // ProcessID
   command[3] = 0x11;
   // SubID
   command[4] = 0x02;
   // Device ID
   command[5] = devId;
   // movemode
   command[6] = moveMode;
   // position is a ZeissLong (4-byte) in big endian format...
   ZeissLong tmp = htonl((ZeissLong) increment);
   memcpy(command+7, &tmp, ZeissLongSize); 

   ret = g_hub.ExecuteCommand(device, core,  command, commandLength, targetDevice);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.SetModelBusy(devId, true);

   return DEVICE_OK;
}

/*
 * Moves the Stage to the specified (upper or lower) hardware stop
 */
int ZeissAxis::FindHardwareStop(MM::Device& device, MM::Core& core, ZeissUByte devId, HardwareStops stop, unsigned char targetDevice)
{
   // Lock the stage
   int ret = SetLock(device, core, devId, true);
   if (ret != DEVICE_OK)
      return ret;
  
   const int commandLength = 6;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x03;
   // Write command, do not expect answer:
   command[1] = 0x1B;
   command[2] = 0xA3; 
   // ProcessID
   command[3] = 0x11;
   // SubID
   if (stop == UPPER)
      command[4] = 0x20;
   else
      command[4] = 0x21;
   // Device ID
   command[5] = devId;

   ret = g_hub.ExecuteCommand(device, core,  command, commandLength, targetDevice);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.SetModelBusy(devId, true);
   // TODO: have monitoring thread look for completion message and unlock the stage!!!

   return DEVICE_OK;
} 

/*
 * Stops movement for this Axis immediately
 */
int ZeissAxis::StopMove(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissByte moveMode, unsigned char targetDevice)
{
   int ret;
   const int commandLength = 11;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x04;
   // Write command, do not expect answer:
   command[1] = 0x1B;
   command[2] = 0xA3; 
   // ProcessID
   command[3] = 0x11;
   // SubID
   command[4] = 0x00;
   // Device ID
   command[5] = devId;
   // movemode
   command[6] = moveMode;

   ret = g_hub.ExecuteCommand(device, core,  command, commandLength, targetDevice);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
// ZeissScope
//
ZeissScope::ZeissScope() :
   initialized_(false),
   port_("Undefined"),                                                       
   answerTimeoutMs_(250)
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_ANSWER_TIMEOUT, "The Zeiss microscope does not answer.  Is it switched on and connected to this computer?");

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_ZeissDeviceName, MM::String, true);
   
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Zeiss microscope CAN bus adapter", MM::String, true);
   
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &ZeissScope::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // AnswerTimeOut                                                                  
   pAct = new CPropertyAction (this, &ZeissScope::OnAnswerTimeOut);
   CreateProperty("AnswerTimeOut", "250", MM::Float, false, pAct, true);
}

ZeissScope::~ZeissScope() 
{
   printf ("In ZeissScope destructor\n");
   if (g_hub.monitoringThread_ != 0) {
      g_hub.monitoringThread_->Stop();
   printf ("Stopping monitoringThread\n");
      g_hub.monitoringThread_->wait();
   printf ("Thread stopped\n");
      delete g_hub.monitoringThread_;
      g_hub.monitoringThread_ = 0;
   }
   g_hub.scopeInitialized_ = false;
   Shutdown();
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int ZeissScope::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_hub.port_.c_str());
   } else if (eAct == MM::AfterSet) {
      if (initialized_) {
         // revert
         pProp->Set(g_hub.port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
      // take this port.  TODO: should we check if this is a valid port?
      pProp->Get(g_hub.port_);
      // set flags indicating we have a port
      g_hub.portInitialized_ = true;
      initialized_ = true;
   }

   return DEVICE_OK;
}

int ZeissScope::OnAnswerTimeOut(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_hub.GetTimeOutTime().getMsec());
   } else if (eAct == MM::AfterSet) {
      double tmp;
      pProp->Get(tmp);
      g_hub.SetTimeOutTime(MM::MMTime(tmp * 1000.0));
   }

   return DEVICE_OK;
}

int ZeissScope::Initialize() 
{
   // Version
   string version;
   int ret = g_hub.GetVersion(*this, *GetCoreCallback(), version);
   if (DEVICE_OK != ret)
      return ret;
   ret = CreateProperty("Microscope Version", version.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;
    
   ret = UpdateStatus();
   if (DEVICE_OK != ret)
      return ret;

   initialized_ = true;
   return 0;
}

int ZeissScope::Shutdown() 
{
   return 0;
}

void ZeissScope::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZeissDeviceName);
}

bool ZeissScope::Busy() 
{
   return false;
}


///////////////////////////////////////////////////////////////////////////////
// Zeiss Shutter, This implements a specialized ZeissChanger 
///////////////////////////////////////////////////////////////////////////////
Shutter::Shutter (ZeissUByte devId, std::string name, std::string description): 
   initialized_ (false),
   state_(0)
{
   devId_ = devId;
   name_ = name;
   description_ = description;
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Shutter to work");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This shutter is not installed in this Zeiss microscope");
}

Shutter::~Shutter ()
{
   Shutdown();
}

void Shutter::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Shutter::Initialize()
{
   if (!g_hub.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this shutter exists:
   bool present;
   int ret = GetPresent(*this, *GetCoreCallback(), devId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Check current state of shutter:
   ret = GetOpen(state_);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnState);
   if (state_)
      ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct); 
   else
      ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 

   if (ret != DEVICE_OK) 
      return ret; 

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   //Label

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 

   initialized_ = true;

   return DEVICE_OK;
}

bool Shutter::Busy()
{
   bool busy;
   int ret = GetBusy(*this, *GetCoreCallback(), devId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

int Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int Shutter::SetOpen(bool open)
{
   int position;
   if (open)
      position = 2;
   else
      position = 1;

   int ret = SetPosition(*this, *GetCoreCallback(), devId_, position);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int Shutter::GetOpen(bool &open)
{

   int position;
   int ret = GetPosition(*this, *GetCoreCallback(), devId_, position);
   if (ret != DEVICE_OK)
      return ret;

   // Assume that closed == 1, open == 2 ?
   if (position == 1)
      open = false;
   else if (position == 2)
      open = true;
   else
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int Shutter::Fire(double)
{
   return DEVICE_UNSUPPORTED_COMMAND;  
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // return pos as we know it
      GetOpen(state_);
      if (state_)
         pProp->Set(1L);
      else
         pProp->Set(0L);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos==1)
      {
         return this->SetOpen(true);
      }
      else
      {
         return this->SetOpen(false);
      }
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// General Turret Object, implement all Changers. Inherit and override for
// more specialized requirements (like specific labels)
///////////////////////////////////////////////////////////////////////////////
Turret::Turret(ZeissUByte devId, std::string name, std::string description):
   numPos_(5),
   initialized_ (false),
   pos_(1)
{
   devId_ = devId;
   name_ = name;
   description_ = description;
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for this Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "The requested position is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This turret is not installed in this Zeiss microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);

   UpdateStatus();
}

Turret::~Turret()
{
   Shutdown();
}

void Turret::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Turret::Initialize()
{
   if (!g_hub.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this turret exists:
   bool present;
   int ret = GetPresent(*this, *GetCoreCallback(), devId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &Turret::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction(this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "Position-1", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // create default positions and labels
   int maxPos;
   ret = GetMaxPosition(*this, *GetCoreCallback(), devId_, maxPos);
   if (ret != DEVICE_OK)
      return ret;
   numPos_ = maxPos;

   const int bufSize = 32;
   char buf[bufSize];
   for (unsigned i=0; i < numPos_; i++)
   {
      #ifdef WIN32
      sprintf(buf, "Position-%d", i+1);
      #else
      snprintf(buf, bufSize, "Position-%d", i+1);
      #endif
      SetPositionLabel(i, buf);
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Turret::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool Turret::Busy()
{
   bool busy;
   int ret = GetBusy(*this, *GetCoreCallback(), devId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int Turret::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = ZeissChanger::GetPosition(*this, *GetCoreCallback(), devId_, pos);
      if (ret != DEVICE_OK)
         return ret;
      pos_ = pos -1;
      pProp->Set(pos_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pos_);
      int pos = pos_ + 1;
      if ((pos > 0) && (pos <= (int) numPos_))
         return ZeissChanger::SetPosition(*this, *GetCoreCallback(), devId_, pos);
      else
         return ERR_INVALID_TURRET_POSITION;
   }
   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// ReflectorTurret.  Inherits from Turret.  Only change is that it reads the 
// labels from the Zeiss microscope
///////////////////////////////////////////////////////////////////////////////
ReflectorTurret::ReflectorTurret(ZeissUByte devId, std::string name, std::string description) :
   Turret(devId, name, description)
{
}

ReflectorTurret::~ReflectorTurret()
{
   Shutdown();
}

int ReflectorTurret::Initialize()
{
   int ret = Turret::Initialize();
   if (ret != DEVICE_OK)
      return ret;

   for (unsigned i=0; i < numPos_; i++)
   {
      SetPositionLabel(i, g_hub.reflectorList_[i].c_str());
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// ObjectiveTurret.  Inherits from Turret.  Only change is that it reads the 
// labels from the Zeiss microscope
///////////////////////////////////////////////////////////////////////////////
ObjectiveTurret::ObjectiveTurret(ZeissUByte devId, std::string name, std::string description) :
   Turret(devId, name, description)
{
}

ObjectiveTurret::~ObjectiveTurret()
{
   Shutdown();
}

int ObjectiveTurret::Initialize()
{
   int ret = Turret::Initialize();
   if (ret != DEVICE_OK)
      return ret;

   for (unsigned i=0; i < numPos_; i++)
   {
      SetPositionLabel(i, g_hub.objectiveList_[i].c_str());
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   return DEVICE_OK;
}


// TubeLensTurret.  Inherits from Turret.  Only change is that it reads the 
// labels from the Zeiss microscope
///////////////////////////////////////////////////////////////////////////////
TubeLensTurret::TubeLensTurret(ZeissUByte devId, std::string name, std::string description) :
   Turret(devId, name, description)
{
}

TubeLensTurret::~TubeLensTurret()
{
   Shutdown();
}

int TubeLensTurret::Initialize()
{
   int ret = Turret::Initialize();
   if (ret != DEVICE_OK)
      return ret;

   for (unsigned i=0; i < numPos_; i++)
   {
      SetPositionLabel(i, g_hub.tubeLensList_[i].c_str());
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// SidePortTurret.  Inherits from Turret.  Only change is that it reads the 
// labels from the Zeiss microscope
///////////////////////////////////////////////////////////////////////////////
SidePortTurret::SidePortTurret(ZeissUByte devId, std::string name, std::string description) :
   Turret(devId, name, description)
{
}

SidePortTurret::~SidePortTurret()
{
   Shutdown();
}

int SidePortTurret::Initialize()
{
   int ret = Turret::Initialize();
   if (ret != DEVICE_OK)
      return ret;

   for (unsigned i=0; i < numPos_; i++)
   {
      SetPositionLabel(i, g_hub.sidePortList_[i].c_str());
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CondenserTurret.  Inherits from Turret.  Only change is that it reads the 
// labels from the Zeiss microscope
///////////////////////////////////////////////////////////////////////////////
CondenserTurret::CondenserTurret(ZeissUByte devId, std::string name, std::string description) :
   Turret(devId, name, description)
{
}

CondenserTurret::~CondenserTurret()
{
   Shutdown();
}

int CondenserTurret::Initialize()
{
   int ret = Turret::Initialize();
   if (ret != DEVICE_OK)
      return ret;

   for (unsigned i=0; i < numPos_; i++)
   {
      SetPositionLabel(i, g_hub.condenserList_[i].c_str());
   }

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// General Servo Object, implements all Servos
///////////////////////////////////////////////////////////////////////////////
Servo::Servo(ZeissUByte devId, std::string name, std::string description):
   initialized_ (false),
   numPos_(5)
{
   devId_ = devId;
   name_ = name;
   description_ = description;
   InitializeDefaultErrorMessages();

   // TODO provide error messages
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for this Turret to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "The requested position is not available on this turret");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This device is not installed in this Zeiss microscope");

   // Create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, description_.c_str(), MM::String, true);

   UpdateStatus();
}

Servo::~Servo()
{
   Shutdown();
}

void Servo::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Servo::Initialize()
{
   if (!g_hub.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this turret exists:
   bool present;
   int ret = GetPresent(*this, *GetCoreCallback(), devId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // Position
   // -----
   CPropertyAction* pAct = new CPropertyAction(this, &Servo::OnPosition);
   // if there are multiple units, which one will we take?  For simplicity, use the last one for now
   unit_ = g_deviceInfo[devId_].deviceScalings[g_deviceInfo[devId_].deviceScalings.size()-1];

   ret = CreateProperty(unit_.c_str(), "1", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   minPosScaled_ = g_deviceInfo[devId_].scaledScale[unit_][0];
   maxPosScaled_ = g_deviceInfo[devId_].scaledScale[unit_][0];
   minPosNative_ = g_deviceInfo[devId_].nativeScale[unit_][0];
   maxPosNative_ = g_deviceInfo[devId_].nativeScale[unit_][0];
   for (size_t i=0; i < g_deviceInfo[devId_].scaledScale[unit_].size(); i++) {
      if (minPosScaled_ > g_deviceInfo[devId_].scaledScale[unit_][i])
      {
         minPosScaled_ = g_deviceInfo[devId_].scaledScale[unit_][i];
         minPosNative_ = g_deviceInfo[devId_].nativeScale[unit_][i];
      }
      if (maxPosScaled_ < g_deviceInfo[devId_].scaledScale[unit_][i])
      {
         maxPosScaled_ = g_deviceInfo[devId_].scaledScale[unit_][i];
         maxPosNative_ = g_deviceInfo[devId_].nativeScale[unit_][i];
      }
   }

   SetPropertyLimits(unit_.c_str(), minPosScaled_, maxPosScaled_);

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Servo::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

bool Servo::Busy()
{
   bool busy;
   int ret = GetBusy(*this, *GetCoreCallback(), devId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return busy;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers                                                           
///////////////////////////////////////////////////////////////////////////////

int Servo::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct) {
   if (eAct == MM::BeforeGet)
   {
      int pos;
      int ret = ZeissServo::GetPosition(*this, *GetCoreCallback(), devId_, pos);
      if (ret != DEVICE_OK)
         return ret;
      // We have the native position here, translate to the 'scaled' position'
      // For simplicities sake we just do linear interpolation
      double posScaled = ((double)pos/(maxPosNative_-minPosNative_) * (maxPosScaled_ - minPosScaled_)) + minPosScaled_; 
      pProp->Set(posScaled);
   }
   else if (eAct == MM::AfterSet)
   {
      double posScaled;
      pProp->Get(posScaled);
      int posNative = (int) (posScaled/(maxPosScaled_-minPosScaled_) * (maxPosNative_ - minPosNative_)) + minPosNative_;
      return ZeissServo::SetPosition(*this, *GetCoreCallback(), devId_, posNative);
   }
   return DEVICE_OK;
}

/*
 * ZeissFocusStage: Micro-Manager implementation of focus drive
 */
Axis::Axis (ZeissUByte devId, std::string name, std::string description): 
   stepSize_um_(0.001),
   initialized_ (false),
   moveMode_ (0),
   velocity_ (0),
   direct_ ("Direct move to target"),
   uni_ ("Unidirectional backlash compensation"),
   biSup_ ("Bidirectional Precision suppress small upwards"),
   biAlways_ ("Bidirectional Precision Always"),
   fast_ ("Fast"),
   smooth_ ("Smooth"),
   busyCounter_(0)
{
   devId_ = devId;
   name_ = name;
   description_ = description;
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Shutter to work");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This Axis is not installed in this Zeiss microscope");
}

Axis::~Axis()
{
   Shutdown();
}

bool Axis::Busy()
{
   bool busy;
   int ret = GetBusy(*this, *GetCoreCallback(), devId_, busy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;
   if (busy) {
	   busyCounter_++;
	   if (busyCounter_ > 30) {
		   // TODO: send another status request, hack: set Busy to false now
		   busyCounter_ = 0;
		   g_hub.SetModelBusy(devId_, false);
	   }
   } else
	   busyCounter_ = 0;

   return busy;
}

void Axis::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZeissFocusAxis);
}


int Axis::Initialize()
{
   if (!g_hub.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this Axis exists:
   bool present;
   int ret = GetPresent(*this, *GetCoreCallback(), devId_, present);
   if (ret != DEVICE_OK)
      return ret;
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------
   // Position
   CPropertyAction* pAct = new CPropertyAction(this, &Axis::OnPosition);
   ret = CreateProperty(MM::g_Keyword_Position, "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // MoveMode
   pAct = new CPropertyAction(this, &Axis::OnMoveMode);
   ret = CreateProperty("Move Mode", direct_.c_str(), MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Move Mode", direct_.c_str()); 
   AddAllowedValue("Move Mode", uni_.c_str()); 
   AddAllowedValue("Move Mode", biSup_.c_str()); 
   AddAllowedValue("Move Mode", biAlways_.c_str()); 

   // velocity
   pAct = new CPropertyAction(this, &Axis::OnVelocity);
   ret = CreateProperty("Velocity-Acceleration", fast_.c_str(), MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Velocity-Acceleration", fast_.c_str());
   AddAllowedValue("Velocity-Acceleration", smooth_.c_str());
   

   // Update lower and upper limits.  These values are cached, so if they change during a session, the adapter will need to be re-initialized
/*
   ret = GetUpperLimit();
   if (ret != DEVICE_OK)
      return ret;
   ret = GetLowerLimit();
   if (ret != DEVICE_OK)
      return ret;
*/



   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int Axis::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

int Axis::SetPositionUm(double pos)
{
   long steps = (long)(pos / stepSize_um_);
   int ret = SetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int Axis::GetPositionUm(double& pos)
{
   long steps;                                                
   int ret = GetPositionSteps(steps);                         
   if (ret != DEVICE_OK)                                      
      return ret;                                             
   pos = steps * stepSize_um_;

   return DEVICE_OK;
}

int Axis::SetPositionSteps(long steps)
{
   return ZeissAxis::SetPosition(*this, *GetCoreCallback(), devId_, steps, (ZeissByte) (moveMode_ & velocity_));
}

int Axis::GetPositionSteps(long& steps)
{
   return ZeissDevice::GetPosition(*this, *GetCoreCallback(), devId_, (ZeissLong&) steps);
}

int Axis::SetOrigin()
{
   return DEVICE_OK;
}

int Axis::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      double pos;
      int ret = GetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   else if (eAct == MM::AfterSet)                             
   {  
      double pos;                                             
      pProp->Get(pos);                                        
      int ret = SetPositionUm(pos);                           
      if (ret != DEVICE_OK)                                   
         return ret;                                          
   }                                                          
                                                              
   return DEVICE_OK;                                          
}


int Axis::OnMoveMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      switch (moveMode_) {
         case 0: pProp->Set(direct_.c_str()); break;
         case 1: pProp->Set(uni_.c_str()); break;
         case 2: pProp->Set(biSup_.c_str()); break;
         case 3: pProp->Set(biAlways_.c_str()); break;
         default: pProp->Set(direct_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == direct_)
         moveMode_ = 0;
      else if (result == uni_)
         moveMode_ = 1;
      else if (result == biSup_)
         moveMode_ = 2;
      else if (result == biAlways_)
         moveMode_ = 3;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

int Axis::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      switch (velocity_) {
         case 0: pProp->Set(fast_.c_str()); break;
         case 4: pProp->Set(smooth_.c_str()); break;
         default: pProp->Set(fast_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == fast_)
         velocity_ = 0;
      else if (result == smooth_)
         velocity_ = 4;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

/*
 * ZeissXYStage: Micro-Manager implementation of X and Y Stage
 */
XYStage::XYStage (): 
   stepSize_um_(0.001),
   initialized_ (false),
   moveMode_ (0),
   velocity_ (0),
   direct_ ("Direct move to target"),
   uni_ ("Unidirectional backlash compensation"),
   biSup_ ("Bidirectional Precision suppress small upwards"),
   biAlways_ ("Bidirectional Precision Always"),
   fast_ ("Fast"),
   smooth_ ("Smooth")
{
   name_ = g_ZeissXYStage;
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss Shutter to work");
   SetErrorText(ERR_MODULE_NOT_FOUND, "No XYStage installed on this Zeiss microscope");
}

XYStage::~XYStage()
{
   Shutdown();
}

bool XYStage::Busy()
{
   bool xBusy = false;
   bool yBusy = false;
   int ret = GetBusy(*this, *GetCoreCallback(), g_StageXAxis, xBusy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;
   ret = GetBusy(*this, *GetCoreCallback(), g_StageYAxis, yBusy);
   if (ret != DEVICE_OK)  // This is bad and should not happen
      return false;

   return xBusy && yBusy;
}

void XYStage::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZeissXYStage);
}


int XYStage::Initialize()
{
   if (!g_hub.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this Axis exists:
   bool presentX, presentY;
   // TODO: check both stages
   int ret = GetPresent(*this, *GetCoreCallback(), g_StageYAxis, presentY);
   if (ret != DEVICE_OK)
      return ret;
   ret = GetPresent(*this, *GetCoreCallback(), g_StageXAxis, presentX);
   if (ret != DEVICE_OK)
      return ret;
   if (!(presentX && presentY))
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------
   // MoveMode
   CPropertyAction* pAct = new CPropertyAction(this, &XYStage::OnMoveMode);
   ret = CreateProperty("Move Mode", direct_.c_str(), MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Move Mode", direct_.c_str()); 
   AddAllowedValue("Move Mode", uni_.c_str()); 
   AddAllowedValue("Move Mode", biSup_.c_str()); 
   AddAllowedValue("Move Mode", biAlways_.c_str()); 

   // velocity
   pAct = new CPropertyAction(this, &XYStage::OnVelocity);
   ret = CreateProperty("Velocity-Acceleration", fast_.c_str(), MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Velocity-Acceleration", fast_.c_str());
   AddAllowedValue("Velocity-Acceleration", smooth_.c_str());
   

   // Update lower and upper limits.  These values are cached, so if they change during a session, the adapter will need to be re-initialized
/*
   ret = GetUpperLimit();
   if (ret != DEVICE_OK)
      return ret;
   ret = GetLowerLimit();
   if (ret != DEVICE_OK)
      return ret;
*/



   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int XYStage::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

int XYStage::GetLimits(double& xMin, double& xMax, double& yMin, double& yMax) 
{
   // TODO: rework to our own coordinate system
   xMin = 0;
   yMin = 0;
   xMax = g_deviceInfo[g_StageXAxis].maxPos;
   yMax = g_deviceInfo[g_StageYAxis].maxPos;
   return DEVICE_OK;
}

int XYStage::SetPositionUm(double x, double y)
{
   long xSteps = (long)(x / stepSize_um_);
   long ySteps = (long)(y / stepSize_um_);
   int ret = SetPositionSteps(xSteps, ySteps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::SetRelativePositionUm(double x, double y)
{
   long xSteps = (long)(x / stepSize_um_);
   long ySteps = (long)(y / stepSize_um_);
   int ret = SetRelativePositionSteps(xSteps, ySteps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::GetPositionUm(double& x, double& y)
{
   long xSteps, ySteps;
   int ret = GetPositionSteps(xSteps, ySteps);                         
   if (ret != DEVICE_OK)                                      
      return ret;                                             
   x = xSteps * stepSize_um_;
   y = ySteps * stepSize_um_;

   return DEVICE_OK;
}

int XYStage::SetPositionSteps(long xSteps, long ySteps)
{
   int ret = ZeissAxis::SetPosition(*this, *GetCoreCallback(), g_StageXAxis, xSteps, (ZeissByte) (moveMode_ & velocity_));
   if (ret != DEVICE_OK)
      return ret;
   ret = ZeissAxis::SetPosition(*this, *GetCoreCallback(), g_StageYAxis, ySteps, (ZeissByte) (moveMode_ & velocity_));
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::SetRelativePositionSteps(long xSteps, long ySteps)
{
   int ret = ZeissAxis::SetRelativePosition(*this, *GetCoreCallback(), g_StageXAxis, xSteps, (ZeissByte) (moveMode_ & velocity_));
   if (ret != DEVICE_OK)
      return ret;
   ret = ZeissAxis::SetRelativePosition(*this, *GetCoreCallback(), g_StageYAxis, ySteps, (ZeissByte) (moveMode_ & velocity_));
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::GetPositionSteps(long& xSteps, long& ySteps)
{
   int ret = ZeissDevice::GetPosition(*this, *GetCoreCallback(), g_StageXAxis, (ZeissLong&) xSteps);
   if (ret != DEVICE_OK)
      return ret;

   ret = ZeissDevice::GetPosition(*this, *GetCoreCallback(), g_StageYAxis, (ZeissLong&) ySteps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::Home()
{
   int ret = FindHardwareStop(*this, *GetCoreCallback(), g_StageXAxis, LOWER);
   if (ret != DEVICE_OK)
      return ret;
   ret = FindHardwareStop(*this, *GetCoreCallback(), g_StageYAxis, LOWER);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::Stop()
{
   int ret = ZeissAxis::StopMove(*this, *GetCoreCallback(), g_StageXAxis, (ZeissByte) (moveMode_ & velocity_));
   if (ret != DEVICE_OK)
      return ret;
   ret = ZeissAxis::StopMove(*this, *GetCoreCallback(), g_StageYAxis, (ZeissByte) (moveMode_ & velocity_));
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int XYStage::SetOrigin()
{
   return DEVICE_OK;
}

int XYStage::OnMoveMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      switch (moveMode_) {
         case 0: pProp->Set(direct_.c_str()); break;
         case 1: pProp->Set(uni_.c_str()); break;
         case 2: pProp->Set(biSup_.c_str()); break;
         case 3: pProp->Set(biAlways_.c_str()); break;
         default: pProp->Set(direct_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == direct_)
         moveMode_ = 0;
      else if (result == uni_)
         moveMode_ = 1;
      else if (result == biSup_)
         moveMode_ = 2;
      else if (result == biAlways_)
         moveMode_ = 3;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

int XYStage::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      switch (velocity_) {
         case 0: pProp->Set(fast_.c_str()); break;
         case 4: pProp->Set(smooth_.c_str()); break;
         default: pProp->Set(fast_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == fast_)
         velocity_ = 0;
      else if (result == smooth_)
         velocity_ = 4;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

