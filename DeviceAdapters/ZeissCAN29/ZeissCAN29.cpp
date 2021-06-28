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
//                this under the LGPL on 1/16/2008 (permission re-granted on 7/3/2008, 7/1/2009//                after changes to the code).
//                If you modify this code using information you obtained 
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





#include "ZeissCAN29.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>


ZeissHub g_hub;

///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

// Zeiss Devices
const char* g_ZeissDeviceName = "ZeissScope";
const char* g_ZeissReflector = "ZeissReflectorTurret";
const char* g_ZeissNosePiece = "ZeissObjectiveTurret";
const char* g_ZeissNeutralDensityWheel1RL = "ZeissNDWheel1RL";
const char* g_ZeissNeutralDensityWheel2RL = "ZeissNDWheel2RL";
const char* g_ZeissFieldDiaphragm = "ZeissFieldDiaphragm";
const char* g_ZeissApertureDiaphragm = "ZeissApertureDiaphragm";
const char* g_ZeissNeutralDensityWheel1TL = "ZeissNDWheel1TL";
const char* g_ZeissNeutralDensityWheel2TL = "ZeissNDWheel2TL";
const char* g_ZeissFocusAxis = "ZeissFocusAxis";
const char* g_ZeissTubeLens = "ZeissTubeLens";
const char* g_ZeissTubeLensShutter = "ZeissTubeLensShutter";
const char* g_ZeissSidePort = "ZeissSidePort";
const char* g_ZeissExcitationSwitcher = "ZeissExcitationSwitcher";
const char* g_ZeissExternalLampMirror = "ZeissExternalLampMirror";
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
const char* g_ZeissDefiniteFocus = "ZeissDefiniteFocus";
const char* g_ZeissDFOffset = "ZeissDefiniteFocusOffset";
const char* g_ZeissColibri = "ZeissColibri";
const char* g_Zeiss2TVTubePrism = "Zeiss2TVTubePrism";
const char* g_Zeiss2TVTubeSlider = "Zeiss2TVTubeSlider";
const char* g_Zeiss2TVTubeShutter = "Zeiss2TVTubeShutter";
const char* g_ZeissHXPShutter = "ZeissHXPShutter";


// List of Device numbers (from Zeiss documentation)
ZeissUByte g_ReflectorChanger = 0x01;
ZeissUByte g_NosePieceChanger = 0x02;
ZeissUByte g_NeutralDensityWheel1RL = 0x05; // Called Filter wheel 1 in docs
ZeissUByte g_NeutralDensityWheel2RL = 0x06; // Called Filter wheel 2 in docs
ZeissUByte g_FieldDiaphragmServo = 0x08; // Reflected Light
ZeissUByte g_ApertureDiaphragmServo = 0x09; // Reflected Light
ZeissUByte g_NeutralDensityWheel1TL = 0x0B; // Called Filter wheel 1 in docs
ZeissUByte g_NeutralDensityWheel2TL = 0x0C; // Called Filter wheel 2 in docs
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
ZeissUByte g_ExcitationChanger = 0x2B;
ZeissUByte g_LSMPortChanger = 0x2B;  // RearPort, reflected light
ZeissUByte g_2TVTubePrism = 0x2E;  
ZeissUByte g_2TVTubeSlider = 0x2F;  
ZeissUByte g_2TVTubeShutter = 0x30;  
ZeissUByte g_ExternalLampMirror = 0x32;  
ZeissUByte g_HXPShutter = 0x36;
ZeissUByte g_BasePortChanger = 0x40;
ZeissUByte g_UniblitzShutter = 0x41;
ZeissUByte g_FilterWheelChanger = 0x42;

// convenience strings
const char* g_COperationMode = "Operation Mode";
const char* g_CExternalShutter = "Shutter";
const char* g_focusMethod = "Focus Method";
const char* g_focusThisPosition = "Measure";
const char* g_focusLastPosition = "Last Position";
const char* g_focusApplyPosition = "Apply";

///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ZeissDeviceName, MM::GenericDevice, "Zeiss AxioObserver controlled through serial interface");
   RegisterDevice(g_ZeissReflector, MM::StateDevice, "Reflector Turret (dichroics)");
   RegisterDevice(g_ZeissNosePiece, MM::StateDevice, "Objective Turret");
   RegisterDevice(g_ZeissNeutralDensityWheel1RL, MM::StateDevice, "ND Filter Wheel 1 Fluorescence Light Path");
   RegisterDevice(g_ZeissNeutralDensityWheel2RL, MM::StateDevice, "ND Filter Wheel 2 Fluorescence Light Path");
   RegisterDevice(g_ZeissFieldDiaphragm, MM::GenericDevice, "Field Diaphragm (fluorescence)");
   RegisterDevice(g_ZeissApertureDiaphragm, MM::GenericDevice, "Aperture Diaphragm (fluorescence)");
   RegisterDevice(g_ZeissNeutralDensityWheel1TL, MM::StateDevice, "ND Filter Wheel 1 Transmitted Light Path");
   RegisterDevice(g_ZeissNeutralDensityWheel2TL, MM::StateDevice, "ND Filter Wheel 2 Transmitted Light Path");
   RegisterDevice(g_ZeissFocusAxis, MM::StageDevice, "Z-drive");
   RegisterDevice(g_ZeissXYStage, MM::XYStageDevice, "XYStage");
   RegisterDevice(g_ZeissTubeLens, MM::StateDevice, "Tube Lens (optovar)");
   RegisterDevice(g_ZeissTubeLensShutter, MM::ShutterDevice, "Tube Lens Shutter");
   RegisterDevice(g_ZeissSidePort, MM::StateDevice, "Side Port");
   RegisterDevice(g_ZeissExcitationSwitcher, MM::StateDevice, "Excitation Switcher");
   RegisterDevice(g_ZeissReflectedLightShutter, MM::ShutterDevice, "Reflected Light Shutter");
   RegisterDevice(g_ZeissTransmittedLightShutter, MM::ShutterDevice, "Transmitted Light Shutter");
   RegisterDevice(g_ZeissRLFLAttenuator, MM::StateDevice, "Reflected (fluorescence) light attenuator");
   RegisterDevice(g_ZeissCondenserContrast, MM::StateDevice, "Condenser Contrast");
   RegisterDevice(g_ZeissCondenserAperture, MM::GenericDevice, "Condenser Aperture");
   RegisterDevice(g_ZeissHBOLamp, MM::GenericDevice, "HBO Lamp");
   RegisterDevice(g_ZeissHalogenLamp, MM::GenericDevice, "Halogen Lamp");
   RegisterDevice(g_ZeissLSMPort, MM::StateDevice, "LSM Port (rearPort)");
   RegisterDevice(g_ZeissBasePort, MM::StateDevice, "Base Port switcher");
   RegisterDevice(g_ZeissExternalLampMirror, MM::StateDevice, "External Lamp Mirror");
   RegisterDevice(g_ZeissUniblitz, MM::ShutterDevice, "Uniblitz Shutter");
   RegisterDevice(g_ZeissFilterWheel, MM::StateDevice, "Filter Wheel");
   RegisterDevice(g_ZeissDefiniteFocus, MM::AutoFocusDevice, "Definite Focus");
   RegisterDevice(g_ZeissDFOffset, MM::StageDevice, "Definite Focus Offset-drive");
   RegisterDevice(g_ZeissColibri, MM::ShutterDevice, "Colibri");
   RegisterDevice(g_Zeiss2TVTubePrism, MM::StateDevice, "g_Zeiss2TVTubePrism");
   RegisterDevice(g_Zeiss2TVTubeSlider, MM::StateDevice, "g_Zeiss2TVTubeSlider");
   RegisterDevice(g_Zeiss2TVTubeShutter, MM::StateDevice, "g_Zeiss2TVTubeShutter");
   RegisterDevice(g_ZeissHXPShutter, MM::ShutterDevice, "g_ZeissHXPShutter");
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
   else if (strcmp(deviceName, g_ZeissNeutralDensityWheel1RL) == 0)
        return new Turret(g_NeutralDensityWheel1RL, g_ZeissNeutralDensityWheel1RL, "ND Filter 1 Refl. Light");
   else if (strcmp(deviceName, g_ZeissNeutralDensityWheel2RL) == 0)
        return new Turret(g_NeutralDensityWheel2RL, g_ZeissNeutralDensityWheel2RL, "ND Filter 2 Refl. Light");
   else if (strcmp(deviceName, g_ZeissFieldDiaphragm) == 0)
        return new Servo(g_FieldDiaphragmServo, g_ZeissFieldDiaphragm, "Field Diaphragm");
   else if (strcmp(deviceName, g_ZeissApertureDiaphragm) == 0)
        return new Servo(g_ApertureDiaphragmServo, g_ZeissApertureDiaphragm, "Aperture Diaphragm");
   else if (strcmp(deviceName, g_ZeissNeutralDensityWheel1TL) == 0)
        return new Turret(g_NeutralDensityWheel1TL, g_ZeissNeutralDensityWheel1TL, "ND Filter 1 Trans. Light");
   else if (strcmp(deviceName, g_ZeissNeutralDensityWheel2TL) == 0)
        return new Turret(g_NeutralDensityWheel2TL, g_ZeissNeutralDensityWheel2TL, "ND Filter 2 Trans. Light");
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
   else if (strcmp(deviceName, g_ZeissExcitationSwitcher) == 0)
        return new Turret(g_ExcitationChanger, g_ZeissExcitationSwitcher, "Excitation Switcher");
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
   else if (strcmp(deviceName, g_ZeissExternalLampMirror) == 0)
        return new Turret(g_ExternalLampMirror, g_ZeissExternalLampMirror, "External Lamp Mirror");
   else if (strcmp(deviceName, g_ZeissUniblitz) == 0)
        return new Shutter(g_UniblitzShutter, g_ZeissUniblitz, "Uniblitz Shutter");
   else if (strcmp(deviceName, g_ZeissFilterWheel) == 0)
        return new Turret(g_FilterWheelChanger, g_ZeissFilterWheel, "Filter Wheel");
   else if (strcmp(deviceName, g_ZeissDefiniteFocus) == 0)
        return new DefiniteFocus();
   else if (strcmp(deviceName, g_ZeissDFOffset) == 0)
        return new DFOffsetStage();
   else if (strcmp(deviceName, g_ZeissColibri) == 0)
        return new Colibri();
   else if (strcmp(deviceName, g_Zeiss2TVTubePrism) == 0)
        return new Turret(g_2TVTubePrism, g_Zeiss2TVTubePrism, "2-TV Tube Prism");
   else if (strcmp(deviceName, g_Zeiss2TVTubeSlider) == 0)
        return new Turret(g_2TVTubeSlider, g_Zeiss2TVTubeSlider, "2-TV Tube Slider");
   else if (strcmp(deviceName, g_Zeiss2TVTubeShutter) == 0)
        return new Turret(g_2TVTubeShutter, g_Zeiss2TVTubeShutter, "2-TV Tube Shutter");
   else if (strcmp(deviceName, g_ZeissHXPShutter) == 0)
        return new Shutter(g_HXPShutter, g_ZeissHXPShutter, "HXP Shutter");

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}




/*
 * Base class for all Zeiss Devices (Changers, Servos and Axis)
 */
ZeissDevice::ZeissDevice() 
{
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
int ZeissDevice::SetPosition(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId, int position)
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
   if (g_hub.debug_) {
     ostringstream os;
     os << "Setting device "<< hex << (unsigned int) devId << " to position " << dec << position << " and Busy flag to true";
     core.LogMessage(&device, os.str().c_str(), false);
   }
   ret = g_hub.ExecuteCommand(device, core,  command, commandLength);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.SetModelBusy(devId, true);

   return DEVICE_OK;
}
/*
 * Send status request to Servo or Changer. 
 */
int ZeissDevice::GetStatus(MM::Device& device, MM::Core& core, ZeissUByte commandGroup, ZeissUByte devId)
{
   int ret;
   const int commandLength = 6;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x03;
   // Write command, do not expect answer:
   command[1] = 0x18;
   command[2] = commandGroup; 
   // ProcessID
   command[3] = 0x11;
   // SubID
   command[4] = 0x07;
   // Device ID
   command[5] = devId;
   if (g_hub.debug_) {
      ostringstream os;
      os << "Requesting status for device: "<< hex << (unsigned int) devId;
      core.LogMessage(&device, os.str().c_str(), true);
   }
   ret = g_hub.ExecuteCommand(device, core,  command, commandLength);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}
/*
 * Requests Lock from Microscope 
 */
int ZeissDevice::SetLock(MM::Device& device, MM::Core& core, ZeissUByte devId, bool on)
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
   hiMask = ntohl(hiMask);
   loMask = ntohl(loMask);
   memcpy(command+6, &hiMask, ZeissLongSize); 
   memcpy(command+10, &loMask, ZeissLongSize); 

   ostringstream os;
   if (on)
      os << "Requesting locking for device "<< hex << (unsigned int) devId ;
   else
      os << "Requesting unlocking for device "<< hex << (unsigned int) devId ;
   core.LogMessage(&device, os.str().c_str(), false);
   ret = g_hub.ExecuteCommand(device, core,  command, commandLength);
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
   return ZeissDevice::SetPosition(device, core, g_hub.GetCommandGroup(devId), devId, position);
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
   return ZeissDevice::SetPosition(device, core, g_hub.GetCommandGroup(devId), devId, position);
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
int ZeissAxis::SetPosition(MM::Device& device, MM::Core& core, ZeissUByte devId, long position, ZeissByte moveMode)
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
   ret = g_hub.ExecuteCommand(device, core,  command, commandLength);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.SetModelBusy(devId, true);

   return DEVICE_OK;
}

/*
 * Send command to microscope to move relative to current position of Axis
 */
int ZeissAxis::SetRelativePosition(MM::Device& device, MM::Core& core, ZeissUByte devId, long increment, ZeissByte moveMode)
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

   ret = g_hub.ExecuteCommand(device, core,  command, commandLength);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.SetModelBusy(devId, true);

   return DEVICE_OK;
}

/*
 * Moves the Stage to the specified (upper or lower) hardware stop
 */
int ZeissAxis::FindHardwareStop(MM::Device& device, MM::Core& core, ZeissUByte devId, HardwareStops stop)
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

   ret = g_hub.ExecuteCommand(device, core,  command, commandLength);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.SetModelBusy(devId, true);
   // TODO: have monitoring thread look for completion message and unlock the stage!!!

   return DEVICE_OK;
} 

/*
 * Stops movement for this Axis immediately
 */
int ZeissAxis::StopMove(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissByte moveMode)
{
   int ret;
   const int commandLength = 7;
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

   ret = g_hub.ExecuteCommand(device, core,  command, commandLength);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

/*
 * Sets Trajectory Velocity 
 */
int ZeissAxis::SetTrajectoryVelocity(MM::Device& device, MM::Core& core, ZeissUByte devId, long velocity)
{
   int ret;
   const int commandLength = 10;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x07;
   // Write command, do not expect answer:
   command[1] = 0x1B;
   command[2] = 0xA3; 
   // ProcessID
   command[3] = 0x11;
   // SubID
   command[4] = 0x2B;
   // Device ID
   command[5] = devId;
   // position is a ZeissLong (4-byte) in big endian format...
   ZeissLong tmp = htonl((ZeissLong) velocity);
   memcpy(command+6, &tmp, ZeissLongSize); 
   ret = g_hub.ExecuteCommand(device, core,  command, commandLength);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.SetModelBusy(devId, true);

   return DEVICE_OK;
}

int ZeissAxis::HasTrajectoryVelocity(MM::Device& device, MM::Core& core, ZeissUByte devId, bool& hasTV)
{
   return g_hub.HasModelTrajectoryVelocity(device, core, devId, hasTV);
}

/*
 * Sets Trajectory Acceleration
 */
int ZeissAxis::SetTrajectoryAcceleration(MM::Device& device, MM::Core& core, ZeissUByte devId, long acceleration)
{
   int ret;
   const int commandLength = 10;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x07;
   // Write command, do not expect answer:
   command[1] = 0x1B;
   command[2] = 0xA3; 
   // ProcessID
   command[3] = 0x11;
   // SubID
   command[4] = 0x2C;
   // Device ID
   command[5] = devId;
   // position is a ZeissLong (4-byte) in big endian format...
   ZeissLong tmp = htonl((ZeissLong) acceleration);
   memcpy(command+6, &tmp, ZeissLongSize); 
   ret = g_hub.ExecuteCommand(device, core,  command, commandLength);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.SetModelBusy(devId, true);

   return DEVICE_OK;
}

int ZeissAxis::GetTrajectoryVelocity(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& velocity)
{
   return g_hub.GetModelTrajectoryVelocity(device, core, devId, velocity);
}

int ZeissAxis::GetTrajectoryAcceleration(MM::Device& device, MM::Core& core, ZeissUByte devId, ZeissLong& velocity)
{
   return g_hub.GetModelTrajectoryAcceleration(device, core, devId, velocity);
}

///////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////
// ZeissScope
//
ZeissScope::ZeissScope() :
   initialized_(false),
   port_("Undefined")
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_ANSWER_TIMEOUT, "The Zeiss microscope does not answer.  Is it switched on and connected to this computer?");
   SetErrorText(ERR_PORT_NOT_OPEN, "The communication port to the microscope can not be opened");

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
   
   // Definite Focus version property
   pAct = new CPropertyAction(this, &ZeissScope::OnVersionChange);
   CreateProperty("DefiniteFocusVersion", "1", MM::Integer, false, pAct, true);
   SetPropertyLimits("DefiniteFocusVersion", 1, 2);
}

ZeissScope::~ZeissScope() 
{
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
         // return ERR_PORT_CHANGE_FORBIDDEN;
      } else {
         // take this port.  TODO: should we check if this is a valid port?
         pProp->Get(g_hub.port_);
         // set flags indicating we have a port
         g_hub.portInitialized_ = true;
         initialized_ = true;
      }
   }

   return DEVICE_OK;
}

int ZeissScope::OnVersionChange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (eAct == MM::BeforeGet) {
       ZeissUShort version;
       g_hub.definiteFocusModel_.GetVersion(version);
       pProp->Set((long)version);
    } else if (eAct == MM::AfterSet) {
       long int version;
       pProp->Get(version);
       g_hub.definiteFocusModel_.SetVersion((ZeissUShort) version);
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
   if (g_hub.monitoringThread_ != 0) {
      g_hub.monitoringThread_->Stop();
      g_hub.monitoringThread_->wait();
      delete g_hub.monitoringThread_;
      g_hub.monitoringThread_ = 0;
   }
   g_hub.scopeInitialized_ = false;
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
   SetErrorText(ERR_SHUTTER_POS_UNKNOWN, "Shutter reported that it was neither opened or closed, so I am confused");

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
   else {
      std::ostringstream os;
      os << "Shutter was in unexpected position: " << position;
      LogMessage(os.str(), false);
      return ERR_SHUTTER_POS_UNKNOWN;
   }

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
      {
         // Only change position if it is different from requested
         int currentPos;
         int ret = ZeissChanger::GetPosition(*this, *GetCoreCallback(), devId_, currentPos);
         if (ret != DEVICE_OK)
            return ret;
         if (pos != currentPos)
            return ZeissChanger::SetPosition(*this, *GetCoreCallback(), devId_, pos);
         return DEVICE_OK;
      }
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
   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for this Servo to work");
   SetErrorText(ERR_INVALID_TURRET_POSITION, "The requested position is not available on this servo");
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
   unit_ = ZeissHub::deviceInfo_[devId_].deviceScalings[ZeissHub::deviceInfo_[devId_].deviceScalings.size()-1];

   ret = CreateProperty(unit_.c_str(), "1", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   minPosScaled_ = ZeissHub::deviceInfo_[devId_].scaledScale[unit_][0];
   maxPosScaled_ = ZeissHub::deviceInfo_[devId_].scaledScale[unit_][0];
   minPosNative_ = ZeissHub::deviceInfo_[devId_].nativeScale[unit_][0];
   maxPosNative_ = ZeissHub::deviceInfo_[devId_].nativeScale[unit_][0];
   for (size_t i=0; i < ZeissHub::deviceInfo_[devId_].scaledScale[unit_].size(); i++) {
      if (minPosScaled_ > ZeissHub::deviceInfo_[devId_].scaledScale[unit_][i])
      {
         minPosScaled_ = ZeissHub::deviceInfo_[devId_].scaledScale[unit_][i];
         minPosNative_ = ZeissHub::deviceInfo_[devId_].nativeScale[unit_][i];
      }
      if (maxPosScaled_ < ZeissHub::deviceInfo_[devId_].scaledScale[unit_][i])
      {
         maxPosScaled_ = ZeissHub::deviceInfo_[devId_].scaledScale[unit_][i];
         maxPosNative_ = ZeissHub::deviceInfo_[devId_].nativeScale[unit_][i];
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
      double posScaled = ((double)(pos-minPosNative_)/(maxPosNative_-minPosNative_) * (maxPosScaled_ - minPosScaled_)) + minPosScaled_; 
      pProp->Set(posScaled);
   }
   else if (eAct == MM::AfterSet)
   {
      double posScaled;
      pProp->Get(posScaled);
      int posNative = (int) ( (posScaled-minPosScaled_)/(maxPosScaled_-minPosScaled_) * (maxPosNative_ - minPosNative_)) + minPosNative_;
      return ZeissServo::SetPosition(*this, *GetCoreCallback(), devId_, posNative);
   }
   return DEVICE_OK;
}

/*************************************************************
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
   default_ ("Default"),
   fast_ ("Fast"),
   smooth_ ("Smooth"),
   busyCounter_(0)
{
   devId_ = devId;
   name_ = name;
   description_ = description;
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for this Zeiss Axis to work");
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
   AddAllowedValue("Velocity-Acceleration", default_.c_str());
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
         case 0: pProp->Set(default_.c_str()); break;
         case 4: pProp->Set(smooth_.c_str()); break;
         case 8: pProp->Set(fast_.c_str()); break;
         default: pProp->Set(fast_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == default_)
         velocity_ = 0;
      else if (result == smooth_)
         velocity_ = 4;
      else if (result == fast_)
         velocity_ = 8;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

/************************************************************
 * ZeissXYStage: Micro-Manager implementation of X and Y Stage
 */
XYStage::XYStage (): 
   CXYStageBase<XYStage>(),
   stepSize_um_(0.001),
   initialized_ (false),
   moveMode_ (0),
   velocity_ (0),
   direct_ ("Direct move to target"),
   uni_ ("Unidirectional backlash compensation"),
   biSup_ ("Bidirectional Precision suppress small upwards"),
   biAlways_ ("Bidirectional Precision Always"),
   default_ ("Default"),
   fast_ ("Fast"),
   smooth_ ("Smooth")
{
   name_ = g_ZeissXYStage;
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss XYStage to work");
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

   return xBusy || yBusy;
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
   AddAllowedValue("Velocity-Acceleration", default_.c_str());
   AddAllowedValue("Velocity-Acceleration", fast_.c_str());
   AddAllowedValue("Velocity-Acceleration", smooth_.c_str());
   
   // Trajectory Velocity and Acceleration:
   bool hasTV = false;;
   ret = HasTrajectoryVelocity(*this, *GetCoreCallback(), g_StageYAxis, hasTV);
   if (ret != DEVICE_OK)
      return ret;
   if (hasTV) 
   {
      pAct = new CPropertyAction(this, &XYStage::OnTrajectoryVelocity);
      ret = CreateProperty("Velocity (micron/s)", "0", MM::Float, false, pAct);
      if (ret != DEVICE_OK)
         return ret;
      pAct = new CPropertyAction(this, &XYStage::OnTrajectoryAcceleration);
      ret = CreateProperty("Acceleration (micron/s^2)", "0", MM::Float, false, pAct);
      if (ret != DEVICE_OK)
         return ret;
   }

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

int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax) 
{
   long xMi, xMa, yMi, yMa;
   GetStepLimits(xMi, xMa, yMi, yMa);
   xMin = xMi * stepSize_um_;
   yMin = yMi * stepSize_um_;
   xMax = xMa * stepSize_um_;
   yMax = yMa * stepSize_um_;

   return DEVICE_OK;
}

int XYStage::GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax) 
{
   //xMin = ZeissHub::deviceInfo_[g_StageXAxis].lowerHardwareStop;
   ZeissLong xMi, xMa, yMi, yMa;
   g_hub.GetLowerHardwareStop(g_StageXAxis, xMi);
   xMin = xMi;
   g_hub.GetLowerHardwareStop(g_StageYAxis, yMi);
   yMin = yMi;
   g_hub.GetUpperHardwareStop(g_StageXAxis, xMa);
   xMax = xMa;
   g_hub.GetUpperHardwareStop(g_StageYAxis, yMa);
   yMax = yMa;
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
   return SetAdapterOriginUm(0.0, 0.0);
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
         case 0: pProp->Set(default_.c_str()); break;
         case 4: pProp->Set(smooth_.c_str()); break;
         case 8: pProp->Set(fast_.c_str()); break;
         default: pProp->Set(default_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == default_)
         velocity_ = 0;
      else if (result == smooth_)
         velocity_ = 4;
      else if (result == fast_)
         velocity_ = 8;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

int XYStage::OnTrajectoryVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      // we are lazy and only check the x axis
      long velocity;
      int ret = ZeissAxis::GetTrajectoryVelocity(*this, *GetCoreCallback(), g_StageXAxis, (ZeissLong&) velocity);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set( (float) (velocity/1000.0) );
   } else if (eAct == MM::AfterSet) {
      double tmp;
      pProp->Get(tmp);
      long velocity = (long) (tmp * 1000.0);
      int ret = ZeissAxis::SetTrajectoryVelocity(*this, *GetCoreCallback(), g_StageXAxis, (ZeissLong) velocity);
      if (ret != DEVICE_OK)
         return ret;
      ret = ZeissAxis::SetTrajectoryVelocity(*this, *GetCoreCallback(), g_StageYAxis, (ZeissLong) velocity);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

int XYStage::OnTrajectoryAcceleration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      // we are lazy and only check the x axis
      long accel;
      int ret = ZeissAxis::GetTrajectoryAcceleration(*this, *GetCoreCallback(), g_StageXAxis, (ZeissLong&) accel);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set( (float) (accel / 1000.0) );
   } else if (eAct == MM::AfterSet) {
      double tmp;
      pProp->Get(tmp);
      long accel = (long) (tmp * 1000.0);
      int ret = ZeissAxis::SetTrajectoryAcceleration(*this, *GetCoreCallback(), g_StageXAxis, (ZeissLong) accel);
      if (ret != DEVICE_OK)
         return ret;
      ret = ZeissAxis::SetTrajectoryAcceleration(*this, *GetCoreCallback(), g_StageYAxis, (ZeissLong) accel);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

/***********************************
 * Definite Focus
 */
DefiniteFocus::DefiniteFocus() :
   offsets_(),
   focusMethod_(g_focusThisPosition),
   initialized_ (false)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_MODULE_NOT_FOUND, "Definite Focus not found.  Is it installed on this Zeiss microscope?");
   SetErrorText(ERR_UNKNOWN_OFFSET, "Definite Focus Offset requested that had not been previously defined");
   SetErrorText(ERR_DEFINITE_FOCUS_NOT_LOCKED, "Definite Focus not locked");
   SetErrorText(ERR_DEFINITE_FOCUS_TIMEOUT, "Definite Focus timed out.  Increase the value of Core-Timeout if the definite focus is still searching");
}


DefiniteFocus::~DefiniteFocus()
{
}


bool DefiniteFocus::Busy()
{
   bool busy = false;
   g_hub.definiteFocusModel_.GetBusy(busy);
   return busy;
}


void DefiniteFocus::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, "ZeissDefiniteFocus");
}


int DefiniteFocus::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   ZeissUShort status;
   ZeissHub::definiteFocusModel_.GetStatus(status);
   if ((status & 1) != 1)
      return ERR_MODULE_NOT_FOUND;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, "Definite Focus", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Zeiss Definite Focus adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;
   
   CPropertyAction* pAct = new CPropertyAction(this, &DefiniteFocus::OnDFWorkingPosition);
   ret = CreateProperty("DF Working Position", "0.0", MM::Float, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction(this, &DefiniteFocus::OnPeriod);
   ret = CreateProperty("Period", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   // TODO: set the real maximum
   SetPropertyLimits("Period", 0, 100000);
   
   pAct = new CPropertyAction(this, &DefiniteFocus::OnFocusMethod);
   ret = CreateProperty(g_focusMethod, focusMethod_.c_str(), MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_focusMethod, g_focusThisPosition);
   AddAllowedValue(g_focusMethod, g_focusLastPosition);
   AddAllowedValue(g_focusMethod, g_focusApplyPosition);

   return DEVICE_OK;
}


int DefiniteFocus::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

/**
 * Writes data describing position to be stabilized to DF
 * Engages DF and lets go (i.e., as if "once" was pressed on the remote pad)
 */
int DefiniteFocus::StabilizeThisPosition(ZeissUByte dataLength, ZeissUByte* data)
{
   // Get Definite Focus version (1 or 2)
   ZeissUShort version;
   g_hub.definiteFocusModel_.GetVersion(version);
   // Offset for Definite Focus 1 should be 0, for Definite Focus 2 should be 1
   const char dataOffset = version == 1 ? 0 : 1;

   // - Stabilize This Position (0x02)
   const int commandLength = 7 + dataLength - dataOffset;
   unsigned char command[7 + UCHAR_MAX];
   // Size of data block
   command[0] = 0x04 + dataLength - dataOffset;
   // Command, request completion message
   command[1] = 0x19;
   // 'Definite Focus command number'
   command[2] = 0xB3;
   // ProcessID
   command[3] = 0x11;
   // SubID: Stabilize This Focus Position
   command[4] = 0x02;
   // DevID (0x0 for Definite Focus)
   command[5] = 0x00;
   command[6] = dataLength - dataOffset;
   for (ZeissUByte i = 0; i < dataLength - dataOffset; i++)
      command[7+i] = data[i];

   g_hub.definiteFocusModel_.SetBusy(true);

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command, commandLength, DEFINITEFOCUS); 
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK; 
}

/**
 * Overloaded form that uses our own DFOffset struct as input
 */
int DefiniteFocus::StabilizeThisPosition(DFOffset position)
{
   return StabilizeThisPosition(position.length_, position.data_);
}

/**
 * Requests data describing the currently focussed position
 */
int DefiniteFocus::GetStabilizedPosition()
{
   // - Stabilize This Position (0x02)
   const int commandLength = 6;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x03;
   // Request
   command[1] = 0x18;
   // 'Definite Focus command number'
   command[2] = 0xB3;
   // ProcessID
   command[3] = 0x11;
   // SubID: Stabilize This Focus Position
   command[4] = 0x02;
   // DevID (0x0 for Definite Focus)
   command[5] = 0x00;

   g_hub.definiteFocusModel_.SetBusy(true);

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command, commandLength, DEFINITEFOCUS); 
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK; 
}

int DefiniteFocus::StabilizeLastPosition()
{
   // - Stabilize Position last stabilized (0x03)
   const int commandLength = 6;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x03;
   // Command, request completion message
   command[1] = 0x19;
   // 'Definite Focus command number'
   command[2] = 0xB3;
   // ProcessID
   command[3] = 0x11;
   // SubID: Do Stabilize Position
   command[4] = 0x03;
   // DevID (0x0 for Definite Focus)
   command[5] = 0x00;

   g_hub.definiteFocusModel_.SetBusy(true);

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command, commandLength, DEFINITEFOCUS); 
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK; 
}

int DefiniteFocus::IlluminationFeatures()
{
   const int commandLength = 6;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x03;
   // Get request
   command[1] = 0x18;
   // 'Definite Focus command number'
   command[2] = 0xB3;
   // ProcessID
   command[3] = 0x11;
   // SubID: Illumination Features
   command[4] = 0x13;
   // DevID (0x0 for Definite Focus)
   command[5] = 0x00;

   g_hub.definiteFocusModel_.SetBusy(true);

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command, commandLength, DEFINITEFOCUS); 
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK; 
}

/*
 * Stabilizes the last stablized position (or the current position if not stabilized before)
 * This function also keep the DF active (subID 2 and 3 are OneShot)
 * Activity interval is set with the period parameter (0 for as fast as possible)
 */
int DefiniteFocus::FocusControlOnOff(bool state)
{
   // Only send commands to the device when a state change is needed
   if ( (state && IsContinuousFocusLocked()) || (!state && !IsContinuousFocusLocked()))
      return DEVICE_OK;

   // - Focus control on/off (0x01)
   const int commandLength = 7;
   unsigned char command[commandLength];
   // Size of data block
   command[0] = 0x04;
   // Command, ask for direct reply
   command[1] = 0x19;
   // 'Definite Focus command number'
   command[2] = 0xB3;
   // ProcessID
   command[3] = 0x11;
   // SubID: Focus control on/off
   command[4] = 0x01;
   // DevID (0x0 for Definite Focus)
   command[5] = 0x00;
   command[6] = 0x00;
   if (state)
      command[6] = 0x01;

   g_hub.definiteFocusModel_.SetBusy(true);

   return g_hub.ExecuteCommand(*this, *GetCoreCallback(),  command, commandLength, DEFINITEFOCUS); 
}


/*
 * Lock onto the current position
 */
int DefiniteFocus::SetContinuousFocusing(bool state)
{
   return FocusControlOnOff(state);
}

/*
 * Waits until device is Locked or timeout expires
 */
int DefiniteFocus::Wait() 
{
   char timeout[MM::MaxStrLength];
   MM::Core* core = GetCoreCallback();
   core->GetDeviceProperty("Core", "TimeoutMs", timeout);
   MM::MMTime dTimeout = MM::MMTime (atof(timeout) * 1000.0);
   MM::MMTime start = core->GetCurrentMMTime();
   while(!IsContinuousFocusLocked() && ((core->GetCurrentMMTime() - start) < dTimeout)) {
      CDeviceUtils::SleepMs(20);
   }
   if (!IsContinuousFocusLocked())
      return ERR_DEFINITE_FOCUS_NOT_LOCKED;

   return DEVICE_OK;
}

/*
 * Waits for stabilization data to return from the Definite Focus
 */
int DefiniteFocus::WaitForStabilizationData() 
{
   char timeout[MM::MaxStrLength];
   MM::Core* core = GetCoreCallback();
   core->GetDeviceProperty("Core", "TimeoutMs", timeout);
   MM::MMTime dTimeout = MM::MMTime (atof(timeout) * 1000.0);
   MM::MMTime start = core->GetCurrentMMTime();
   while(g_hub.definiteFocusModel_.GetWaitForStabilizationData() && ((core->GetCurrentMMTime() - start) < dTimeout)) {
      CDeviceUtils::SleepMs(20);
   }
   if (g_hub.definiteFocusModel_.GetWaitForStabilizationData())
      return ERR_DEFINITE_FOCUS_NO_DATA;

   return DEVICE_OK;
}

/*
 * Waits for device to become not-Busy
 */
int DefiniteFocus::WaitForBusy() 
{
   char timeout[MM::MaxStrLength];
   MM::Core* core = GetCoreCallback();
   core->GetDeviceProperty("Core", "TimeoutMs", timeout);
   MM::MMTime dTimeout = MM::MMTime (atof(timeout) * 1000.0);
   MM::MMTime start = core->GetCurrentMMTime();
   while(Busy() && ((core->GetCurrentMMTime() - start) < dTimeout)) {
      CDeviceUtils::SleepMs(20);
   }
   if (Busy())
      return ERR_DEFINITE_FOCUS_TIMEOUT;

   return DEVICE_OK;
}

/*
 * Request stabilized position from the DF
 */
int DefiniteFocus::GetOffset(double& offset)
{
   g_hub.definiteFocusModel_.SetWaitForStabilizationData(true);
   // work around for problem in firmware.  Should be called only for specific firmware versions
   int ret = IlluminationFeatures();
   if (ret != DEVICE_OK)
      return ret;
   ret = GetStabilizedPosition();
   if (ret != DEVICE_OK)
      return ret;

   ret = WaitForStabilizationData();
   if (ret != DEVICE_OK)
      return ret;

   DFOffset dfOffset = g_hub.definiteFocusModel_.GetData();
   // work around problem in firmware.  Should be called only for specific firmware version
   if (dfOffset.length_ == 8 || dfOffset.length_ == 12) {
      DFOffset* dfOffset_new = new DFOffset((ZeissUByte) (dfOffset.length_ + 3));
      memcpy(dfOffset_new->data_, dfOffset.data_, dfOffset.length_);
      ZeissShort shutterFactor;
      ZeissByte brightnessLED;
      g_hub.definiteFocusModel_.GetShutterFactor(shutterFactor);
      g_hub.definiteFocusModel_.GetBrightnessLED(brightnessLED);
      memcpy(dfOffset_new->data_ + dfOffset.length_, &shutterFactor, 2);
      memcpy(dfOffset_new->data_ + dfOffset.length_ + 2, &brightnessLED, 1);
      dfOffset = *dfOffset_new;
   }

   vector<DFOffset>::iterator it;
   it = find(offsets_.begin(), offsets_.end(), dfOffset);
   if (it == offsets_.end()) {
      offsets_.push_back(dfOffset);
      it = find(offsets_.begin(), offsets_.end(), dfOffset);
      std::ostringstream os;
      os << (int) (it -offsets_.begin());
      AddAllowedValue("Offset",os.str().c_str());
   }
   vector<DFOffset>::iterator::difference_type i = distance(offsets_.begin(), it);
   offset = (double) i;

   return DEVICE_OK;
}


/*
 * Copy measured offset to the current offest
 */
int DefiniteFocus::SetOffset(double offset)
{
   if (offset >= 0 && (unsigned int)offset < offsets_.size())
      currentOffset_ = offsets_[(int) offset];
   return DEVICE_OK;
}

int DefiniteFocus::GetContinuousFocusing(bool& state)
{
   ZeissUShort status;
   g_hub.definiteFocusModel_.GetStatus(status);
   state = false;
   if (status >= 16)
      state = true;
   return DEVICE_OK; 
}


bool DefiniteFocus::IsContinuousFocusLocked()
{
   ZeissUShort status;
   g_hub.definiteFocusModel_.GetStatus(status);
   if (status & 16)
      return true;
   return false;
}


/*
 * Focus once, either on the current position, or on the last stabilized position
 */
int DefiniteFocus::FullFocus()
{
   LogMessage("DOING FULL FOCUS NOW!!!");
   if (focusMethod_ == g_focusThisPosition)
      StabilizeThisPosition(0);
   else if (focusMethod_ == g_focusLastPosition)
      StabilizeLastPosition();
   else {
      if (currentOffset_.length_ != 0) {
         StabilizeThisPosition(currentOffset_);
         int ret = WaitForBusy();
         if (ret != DEVICE_OK)
            return ret;
         currentOffset_.length_ = 0;
      } 
   }
   return WaitForBusy();
}


/*
 * Focus once, either on the current position, or on the last stabilized position
 */
int DefiniteFocus::IncrementalFocus()
{
   return FullFocus();
}


/*
 * Period for continuous focus.  0- do as often as possible, otherwise in seconds
 * only has effect for continuous focus
 */
int DefiniteFocus::OnPeriod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      ZeissULong period;
      g_hub.definiteFocusModel_.GetPeriod(period);
      pProp->Set((long)period);
   } else if (eAct ==MM::AfterSet) {
      long int period;
      pProp->Get(period);
      g_hub.definiteFocusModel_.SetPeriod((ZeissULong) period);
   }

   return DEVICE_OK;
}


/*
int DefiniteFocus::OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      double offsetPosition;
      int ret = MeasureOffset(offsetPosition);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long) offsetPosition);
   } else if (eAct ==MM::AfterSet) {
      long index;
      pProp->Get(index);
      if (index < offsets_.size() && index >= 0) {
         currentOffset_ = offsets_[index];
      } else
         return ERR_UNKNOWN_OFFSET;
      if (currentOffset_.length_ != 0) {
         StabilizeThisPosition(currentOffset_);
         currentOffset_.length_ = 0;
      }
   }

   return DEVICE_OK;
}
*/
int DefiniteFocus::OnDFWorkingPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      double workingPosition;
      g_hub.definiteFocusModel_.GetWorkingPosition(workingPosition);
      pProp->Set(workingPosition);
   }
   return DEVICE_OK;
}

int DefiniteFocus::OnFocusMethod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(focusMethod_.c_str());
   } else if (eAct ==MM::AfterSet) {
      pProp->Get(focusMethod_);
   }

   return DEVICE_OK;
}


/**************************
 * Definite Focus Offset stage implementation
 */

DFOffsetStage::DFOffsetStage() :
   initialized_(false)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_NO_AUTOFOCUS_DEVICE_FOUND, "No AutoFocus Device loaded");
   SetErrorText(ERR_DEFINITE_FOCUS_TIMEOUT, "Definite Focus timed out.  Increase the value of Core-Timeout if the definite focus is still searching");

   // Name                                                                   
   CreateProperty(MM::g_Keyword_Name, g_ZeissDFOffset, MM::String, true); 
                                                                             
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Definite Focus offset treated as a ZStage", MM::String, true);

}  
 
DFOffsetStage::~DFOffsetStage()
{
}

void DFOffsetStage::GetName(char* Name) const                                       
{                                                                            
   CDeviceUtils::CopyLimitedString(Name, g_ZeissDFOffset);                
}                                                                            
                                                                             
int DFOffsetStage::Initialize() 
{
   if (initialized_)
      return DEVICE_OK;

   ZeissUShort status;
   ZeissHub::definiteFocusModel_.GetStatus(status);
   if ((status & 1) != 1)
      return ERR_MODULE_NOT_FOUND;

   // get list with available AutoFocus devices.   
   //TODO: this is a initialization parameter, which makes it harder for the end-user to set up!
   char deviceName[MM::MaxStrLength];
   int deviceIterator = 0;
   for(;;)
   {
      GetLoadedDeviceOfType(MM::AutoFocusDevice, deviceName, deviceIterator++);
      if( 0 < strlen(deviceName))
      {
         availableAutoFocusDevices_.push_back(std::string(deviceName));
      }
      else
         break;
   }

   CPropertyAction* pAct = new CPropertyAction (this, &DFOffsetStage::OnAutoFocusDevice);      
   std::string defaultAutoFocus = "Undefined";
   if (availableAutoFocusDevices_.size() >= 1) {
      defaultAutoFocus = availableAutoFocusDevices_[0];
      autoFocusDeviceName_ = availableAutoFocusDevices_[0];
   }
   CreateProperty("AutoFocus Device", defaultAutoFocus.c_str(), MM::String, false, pAct, false);         
   if (availableAutoFocusDevices_.size() >= 1)
      SetAllowedValues("AutoFocus Device", availableAutoFocusDevices_);
   else
      return ERR_NO_AUTOFOCUS_DEVICE_FOUND;

   // This is needed, otherwise DeviceAUtofocus_ is not always set resulting in crashes
   // This could lead to strange problems if multiple AutoFocus devices are loaded
   SetProperty("AutoFocus Device", defaultAutoFocus.c_str());

   int ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   std::ostringstream tmp;
   tmp << autoFocusDevice_;
   LogMessage(tmp.str().c_str());

   initialized_ = true;

   return DEVICE_OK;
}

int DFOffsetStage::Shutdown()
{
   if (initialized_)
      initialized_ = false;

   return DEVICE_OK;
}

bool DFOffsetStage::Busy()
{
   if (autoFocusDevice_ != 0)
      return autoFocusDevice_->Busy();

   // If we are here, there is a problem.  No way to report it.
   return false;
}

/*
 * Sets the position of the stage in um relative to the position of the origin
 */
int DFOffsetStage::SetPositionUm(double pos)
{
   if (autoFocusDevice_ == 0)
      return ERR_NO_AUTOFOCUS_DEVICE;

   return autoFocusDevice_->SetOffset(pos);
}

/*
 * Reports the current position of the stage in um relative to the origin
 */
int DFOffsetStage::GetPositionUm(double& pos)
{
   if (autoFocusDevice_ == 0)
      return ERR_NO_AUTOFOCUS_DEVICE;

   return  autoFocusDevice_->GetOffset(pos);;
}

/*
 * Sets a voltage (in mV) on the DA, relative to the minimum Stage position
 * The origin is NOT taken into account
 */
int DFOffsetStage::SetPositionSteps(long /*steps */)
{
   if (autoFocusDevice_ == 0)
      return ERR_NO_AUTOFOCUS_DEVICE;

   return  DEVICE_UNSUPPORTED_COMMAND;
}

int DFOffsetStage::GetPositionSteps(long& /*steps*/)
{
   if (autoFocusDevice_ == 0)
      return ERR_NO_AUTOFOCUS_DEVICE;

   return  DEVICE_UNSUPPORTED_COMMAND;
}

/*
 * Sets the origin (relative position 0) to the current absolute position
 */
int DFOffsetStage::SetOrigin()
{
   if (autoFocusDevice_ == 0)
      return ERR_NO_AUTOFOCUS_DEVICE;

   return  DEVICE_UNSUPPORTED_COMMAND;
}

int DFOffsetStage::GetLimits(double& /* min */, double& /* max */)
{
   if (autoFocusDevice_ == 0)
      return ERR_NO_AUTOFOCUS_DEVICE;

   return  DEVICE_UNSUPPORTED_COMMAND;
}


///////////////////////////////////////
// Action Interface
//////////////////////////////////////
int DFOffsetStage::OnAutoFocusDevice(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(autoFocusDeviceName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string autoFocusDeviceName;
      pProp->Get(autoFocusDeviceName);
      MM::AutoFocus* autoFocusDevice = (MM::AutoFocus*) GetDevice(autoFocusDeviceName.c_str());
      if (autoFocusDevice != 0) {
         autoFocusDevice_ = autoFocusDevice;
         autoFocusDeviceName_ = autoFocusDeviceName;
      } else
         return ERR_NO_AUTOFOCUS_DEVICE;
   }
   return DEVICE_OK;
}


/***********************************************
 * Adapter for the Zeiss LED illuminator Colibri
 */
Colibri::Colibri() :
   initialized_ (false),
   useExternalShutter_(false)
{
   SetErrorText(ERR_MODULE_NOT_FOUND, "No Colibri installed in this Zeiss microscope");
}

Colibri::~Colibri()
{
}

int Colibri::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   ZeissULong status;
   ZeissHub::colibriModel_.GetStatus(status);
   if ((status & 1) != 1)
      return ERR_MODULE_NOT_FOUND;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, "Colibri", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Zeiss Colibri adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // External Shutter
   CPropertyAction* pAct = new CPropertyAction(this, &Colibri::OnExternalShutter);
   ret = CreateProperty(g_CExternalShutter, "LED", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_CExternalShutter, "External");
   AddAllowedValue(g_CExternalShutter, "LED");
   
   // Operation Mode
   pAct = new CPropertyAction(this, &Colibri::OnOperationMode);
   ret = CreateProperty(g_COperationMode, "Normal", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue(g_COperationMode, "Normal", 0x01);
   AddAllowedValue(g_COperationMode, "Trigger Buffer", 0x02);
   AddAllowedValue(g_COperationMode, "External Source", 0x04);
   AddAllowedValue(g_COperationMode, "Pulsed", 0x11);
   AddAllowedValue(g_COperationMode, "Trigger Buffer (Pulsed)", 0x12);
   AddAllowedValue(g_COperationMode, "Gated", 0x21);
   AddAllowedValue(g_COperationMode, "Trigger Buffer (Gated)", 0x22);
   
   // Intensity, name and info for each individual LED
   CPropertyActionEx* pActEx;
   for (long i = 0; i < ColibriModel::NRLEDS; i++) {
      if (g_hub.colibriModel_.available_[i]) {
         pActEx = new CPropertyActionEx(this, &Colibri::OnIntensity, i);
         std::ostringstream os;
         os << "Intensity LED-" << g_hub.colibriModel_.info_[i].wavelengthNm_ << "nm";
         ret = CreateProperty(os.str().c_str(), "0", MM::Integer, false, pActEx);
         if (ret != DEVICE_OK)
               return ret;
         SetPropertyLimits(os.str().c_str(), 0, 100);
         
         pActEx = new CPropertyActionEx(this, &Colibri::OnName, i);
         std::ostringstream ns;
         ns << "Name LED-" << g_hub.colibriModel_.info_[i].wavelengthNm_ << "nm";
         ret = CreateProperty(ns.str().c_str(), "", MM::String, true, pActEx);
         if (ret != DEVICE_OK)
               return ret;

         pActEx = new CPropertyActionEx(this, &Colibri::OnInfo, i);
         std::ostringstream is;
         is << "Info LED-" << g_hub.colibriModel_.info_[i].wavelengthNm_ << "nm";
         ret = CreateProperty(is.str().c_str(), "", MM::String, true, pActEx);
         if (ret != DEVICE_OK)
               return ret;
      }
   }

   ZeissByte operationMode = g_hub.colibriModel_.GetMode();
   if (operationMode == 4)
      useExternalShutter_ = true;

   return DEVICE_OK;
}

int Colibri::Shutdown()
{
   return DEVICE_OK;
}

bool Colibri::Busy()
{
   if (g_hub.colibriModel_.GetBusy())
      return true;

   return false;
}

void Colibri::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, "ZeissColibri");
}


/**
 * Switches our virtual shutter between the LEDS and external shutter
 * Switches operation mode to accomplish this
 */
int Colibri::OnExternalShutter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct==MM::BeforeGet) {
      ZeissByte operationMode = g_hub.colibriModel_.GetMode();
      if (operationMode == 4)
         useExternalShutter_ = true;
      if (useExternalShutter_)
         pProp->Set("External");
      else
         pProp->Set("LED");
   } else if (eAct == MM::AfterSet) {
      std::string use;
      pProp->Get(use);
      bool open = g_hub.colibriModel_.GetOpen() || (g_hub.colibriModel_.GetExternalShutterState() == 2);
      if (open)
         SetOpen(false);
      if (use == "External") {
         if (g_hub.colibriModel_.GetMode() != 0x04)
            g_hub.ColibriOperationMode(*this, *GetCoreCallback(), 0x04);
         useExternalShutter_ = true;
      } else {
         if (g_hub.colibriModel_.GetMode() != 0x01)
            g_hub.ColibriOperationMode(*this, *GetCoreCallback(), 0x01);
         useExternalShutter_ = false;
      }
      CDeviceUtils::SleepMs(100);
      if (open)
         SetOpen(true);
   }
   return DEVICE_OK;
}


/**
 * Translate Intensity to percentages, just like the Colibri controller does
 */
int Colibri::OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   double calibrationValue = (double)g_hub.colibriModel_.GetCalibrationValue(index);

   if (eAct==MM::BeforeGet) {
      double intensity = (double) g_hub.colibriModel_.GetBrightness(index);
      pProp->Set(floor(intensity/calibrationValue * 100));
   } else if (eAct == MM::AfterSet) {
      long intensity;
      pProp->Get(intensity);
      if (intensity != (long) ((double)g_hub.colibriModel_.GetBrightness(index)/calibrationValue * 100)) {
         int ret = g_hub.ColibriBrightness(*this, *GetCoreCallback(),
                                       index, (ZeissShort) ceil ((double)intensity/100.0 * calibrationValue));
         if (ret != DEVICE_OK)
            return ret;
      }
   }

   return DEVICE_OK;
}


int Colibri::OnOperationMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      ZeissByte operationMode = g_hub.colibriModel_.GetMode();
      char msg[MM::MaxStrLength];
      long data;
      for (unsigned i=0; i<GetNumberOfPropertyValues(g_COperationMode); i++) {
         if (GetPropertyValueAt(g_COperationMode, i, msg)) {
            GetPropertyData(g_COperationMode, msg, data);
            if (data == operationMode) {
               pProp->Set(msg);
               return DEVICE_OK;
            }
         }
      }
   } else if (eAct == MM::AfterSet) {
      std::string propS;
      pProp->Get(propS);
      long data;
      GetPropertyData(g_COperationMode, propS.c_str(), data);
      if ( (ZeissByte) data != g_hub.colibriModel_.GetMode())
         return g_hub.ColibriOperationMode(*this, *GetCoreCallback(), (ZeissByte) data);
   }

   return DEVICE_OK;
}
      

int Colibri::OnName(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   if (eAct==MM::BeforeGet) {
      std::string name = g_hub.colibriModel_.GetName(index);
      pProp->Set(name.c_str());
   }
   return DEVICE_OK;
}

int Colibri::OnInfo(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   if (eAct==MM::BeforeGet) {
      std::string info = g_hub.colibriModel_.GetInfo(index);
      pProp->Set(info.c_str());
   }
   return DEVICE_OK;
}

/**
 * Switches LEDS or external shutter depending on flag useExternalShutter
 * Only switch on LEDs that have their intensity set higher than 0
 */
int Colibri::SetOpen(bool open)
{
   int ret = DEVICE_OK;
   if (useExternalShutter_) {
      // Note: should the operation mode be checked here?
      if (open != (g_hub.colibriModel_.GetExternalShutterState() == 2) ) {
         return g_hub.ColibriExternalShutter(*this, *GetCoreCallback(), open);
      }
   } else {
      // Note: should the operation mode be checked here?
      for (int i=0; i<g_hub.colibriModel_.NRLEDS; i++) {
         if (g_hub.colibriModel_.GetBrightness(i) != 0 && 
               g_hub.colibriModel_.available_[i]  &&
               ( (g_hub.colibriModel_.GetOnOff(i) == 2) != open) ) {
            ret = g_hub.ColibriOnOff(*this, *GetCoreCallback(), (ZeissByte) i, open);
            if (ret != DEVICE_OK)
               return ret;
         }
      }
   }

   return ret;
}

int Colibri::GetOpen(bool& open)
{
   open =  g_hub.colibriModel_.GetOpen();
   return DEVICE_OK;
}

