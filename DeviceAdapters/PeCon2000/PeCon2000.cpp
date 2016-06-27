///////////////////////////////////////////////////////////////////////////////
// FILE:         PeCon2000.cpp
// PROJECT:      Micro-Manager
// SUBSYSTEM:    DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:  PeCon 2000 Adapter
//				
// AUTHOR:       Christian Sachs <c.sachs@fz-juelich.de>
//
// COPYRIGHT:    Forschungszentrum Jülich
// LICENSE:      BSD (2-clause/FreeBSD license)
// THANKS:       Dr. Oliver Merk (of PeCon GmbH) for Support and Testing


#include "PeCon2000.h"

#include <iostream>
#include <functional>

#include "../../MMDevice/ModuleInterface.h"

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

const char* g_PeCon2000DeviceName = "PeCon2000";
const char* g_PeCon2000HubDeviceName = "PeCon2000-Hub";

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_PeCon2000DeviceName, MM::GenericDevice, g_PeCon2000DeviceName);
   RegisterDevice(g_PeCon2000HubDeviceName, MM::HubDevice, g_PeCon2000HubDeviceName);
}

MODULE_API MM::Device* CreateDevice(const char* deviceNameChar)
{
   if(deviceNameChar == 0)
     return 0;

   string deviceName = string(deviceNameChar);

   // careful: Pecon2000 is the beginning of Pecon2000-Hub, so the order is important!

   if(deviceName == g_PeCon2000HubDeviceName)
   {
      return new CPeCon2000HubDevice();
   }
   else if(deviceName.compare(0, strlen(g_PeCon2000DeviceName), g_PeCon2000DeviceName) == 0)
   {
      return new CPeCon2000Device(deviceName);
   }
   
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Hub Device
///////////////////////////////////////////////////////////////////////////////


void CPeCon2000HubDevice::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, g_PeCon2000HubDeviceName);
}

bool CPeCon2000HubDevice::Busy()
{
   return false;
}

//

CPeCon2000HubDevice::CPeCon2000HubDevice()
{

}

CPeCon2000HubDevice::~CPeCon2000HubDevice()
{
   // nop
}


int CPeCon2000HubDevice::Initialize()
{
   if(PdlEnumReset() != PdlErrorSuccess)
   {
      return DEVICE_ERR;
   } 
   else 
   {
      return DEVICE_OK;
   }
}

int CPeCon2000HubDevice::Shutdown()
{
   return DEVICE_OK;
}

int CPeCon2000HubDevice::DetectInstalledDevices(void)
{
   PdlDeviceInfo info;

   if(PdlEnumReset() != PdlErrorSuccess)
   {
      return DEVICE_ERR;
   }
   
   while (PdlEnumNext(&info) == PdlErrorSuccess)
   {
       LogMessage(string("Found PeCon Series 2000 Device - Name: ") + string(info.name) + string(" Serial: ") + string(info.serial), false);

       MM::Device *pDev = new CPeCon2000Device(string(g_PeCon2000DeviceName) + string(":") + string(info.serial));

       if(pDev)
       {
         AddInstalledDevice(pDev);
       }
   }

   if(PdlEnumReset() != PdlErrorSuccess)
   {
      return DEVICE_ERR;
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Particular Device
///////////////////////////////////////////////////////////////////////////////


void CPeCon2000Device::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, name.c_str());
}

bool CPeCon2000Device::Busy()
{
   return false;
}


CPeCon2000Device::CPeCon2000Device(string name) : ready(false), name(name), retries(5), retryErrorLogged(false), errorLogged(false)
{
   ADD_MAP(DeviceStatusMapper, PdlDeviceStatusOk, "OK: No device error");
   ADD_MAP(DeviceStatusMapper, PdlDeviceStatusBlOk, "Bootloader active: No error");
   ADD_MAP(DeviceStatusMapper, PdlDeviceStatusBlRamTestError, "Bootloader active: RAM test error");
   ADD_MAP(DeviceStatusMapper, PdlDeviceStatusBlFlashChecksumError, "Bootloader active: Flash checksum error - Reload Firmware");
   ADD_MAP(DeviceStatusMapper, PdlDeviceStatusBlAppCrashed, "Bootloader active: Application has crashed");
   ADD_MAP(DeviceStatusMapper, PdlDeviceStatusBlEEPRomChecksumError, "Bootloader active: EEPROM checksum error");
   ADD_MAP(DeviceStatusMapper, PdlDeviceStatusBlKeypadError, "Bootloader active: Keypad error: key pressed");

   ADD_MAP(ChannelStatusMapper, PdlChannelStatusOk, "OK. No channel error");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusNoSensor, "NO SENSOR. No valid sensor signal detected");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusOvertemp, "OVERTEMP. Actual Temperature > Max Setpoint + 5°C");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusOvercurrent, "OVERCURRENT. Actual Current > 4A");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusSumOvercurrent, "SUM OVERCURRENT. Current CH1 + CH2 > 4.2A");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusIdDetectError, "ID DETECT ERROR. ID number mismatch after 5x ID request over serial interface (3 identical ID's could not be found in the received 5 ID's) Auto-Detect will be repeated after a wait time, until 3 identical ID's are received.");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusMainsVoltErr, "MAINS VOLT ERR. Mains voltage on connected component is not in allowed range");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusFanRotateErr, "FAN ROTATE ERR. Fan(s) on connected component do not rotate");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusOverheat, "OVERHEAT. Overheat protection circuit on connected component has released");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusMBTempTooHi, "MB TEMP TOO HI. Mainboard temperature on connected component is too high");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusChangeFilter, "CHANGE FILTER. Filters on/in connected component needs replacement");

   ADD_MAP(ChannelStatusMapper, PdlChannelStatusHeatingSensor, "HEATING SENSOR. CO2: The sensor temperature is not +/- 5°C of the calibration temperature. If 60 min. after device start/reset the sensor status bit is still active, the channel status changes to \"FALSE SENS TEMP\", because the sensor heater normally heats up the sensor within 20-30 min. (CO2-Sensor 2000: Status Bit 6 is set) O2: The sensor heating voltage is slowly increased and has not reached yet final value.");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusWaitForSensor, "WAIT FOR SENSOR. CO2: The sensor is not ready yet to give a value (CO2-Sensor 2000: Status Bit 0 is set)");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusAutoCompensate, "AUTO COMPENSATE. CO2: The lamp intensity of the CO2-Sensor 2000 is automatically adjusted to give a specified CO2-concentration (e.g. 100,00% with pure CO2) (CO2-Sensor 2000: Status Bit 1 is set)");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusFalseSensTemp, "FALSE SENS TEMP. CO2: The sensor temperature is not +/- 6°C of the calibration temperature after it was once inside the +/- 5°C tolerance band. Or the sensor status bit was still set 60min. after device start/reset. (CO2-Sensor 2000: Status Bit 6 is set)");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusLampOverLimit, "LAMP OVER LIMIT. CO2: The lamp PWM limit of the connected sensor has been exceeded (<=450 / >=950). (CO2-Sensor 2000: Status Bit 2 is set)");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusNearADLimit, "NEAR AD LIMIT. CO2: One of the internal data points for CO2 measurement is near (or above) the limit of the AD converter. CO2-concentratrion of this time cycle is therefore invalid. (CO2-Sensor 2000: Status Bit 3 is set)");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusFalseCalibVal, "FALSE CALIB VAL. CO2: The calibration values of the connected CO2-sensor 2000 head are invalid  (10%/100% value=0) -> Factory recalibration with 0/10/100% (CO2-Sensor 2000: Status Bit 5 is set)");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusNegativeValue, "NEGATIVE VALUE. CO2: The internal raw value is negative because of inverted detector signal. (CO2-Sensor 2000: Status Bit 4 is set)");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusHeatingVErr, "HEATING V ERR. O2: The heating voltage feedback control algorithm has reached the upper/lower DA voltage output limit, while not have reached the set heating voltage read back by AD-measure­ment.");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusReferenceGas, "REFERENCE GAS. CO2: The sensor chamber is flooded with CO2 reference gas. O2: The sensor chamber is flooded with N2 reference gas.");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusAmbientAir, "AMBIENT AIR. CO2: The sensor chamber is flooded with ambient air. O2: The sensor chamber is flooded with ambient air.");
   ADD_MAP(ChannelStatusMapper, PdlChannelStatusAdjOtherSens, "ADJ OTHER SENS. CO2: The O2 sensor is automatically adjusted. O2: The CO2 sensor is automatically aging compensated (Auto Compensate). This error is set to each sensor, that is within the same volume as the other sensor, whose value is currently corrected/calibrated.");

   ADD_MAP(ErrorMapper, PdlErrorSuccess, "Operation succeeded");
   ADD_MAP(ErrorMapper, PdlErrorFailed, "Function call failed");
   ADD_MAP(ErrorMapper, PdlErrorIllegalHandle, "Illegal device handle");
   ADD_MAP(ErrorMapper, PdlErrorIllegalParameter, "Illegal Parameter");
   ADD_MAP(ErrorMapper, PdlErrorPointerMustNotBeNull, "Pointer must not be null");
   ADD_MAP(ErrorMapper, PdlErrorTooManyDevices, "Too many devices");
   ADD_MAP(ErrorMapper, PdlErrorSendFailed, "Sending data failed");
   ADD_MAP(ErrorMapper, PdlErrorRecvFailed, "Receiving data failed");
   ADD_MAP(ErrorMapper, PdlErrorNotSupported, "Operation not supported");
   ADD_MAP(ErrorMapper, PdlErrorFailure, "Operation failure detected");
   ADD_MAP(ErrorMapper, PdlErrorBufferTooSmall, "Buffer size too small");

   if(name.size() == 0)
   {
      name = g_PeCon2000DeviceName;
   }
   else
   {
      vector<string> tokens;
      CDeviceUtils::Tokenize(name, tokens, ":");

      if(tokens.size() == 2)
      {
         specificSerial = tokens[1];
         name = tokens[0];
      }
   }
   
   CreateProperty("SpecificSerial", specificSerial.c_str(), MM::String, false, new DeviceAction(this, &CPeCon2000Device::OnSpecificSerial), true);
   
   CreateProperty("RetriesOnError", CDeviceUtils::ConvertToString(retries), MM::Integer, false, new DeviceAction(this, &CPeCon2000Device::OnRetriesOnError), true);

   CreateProperty("Ready", "", MM::Integer, true, new DeviceAction(this, &CPeCon2000Device::OnReady), false);

}

CPeCon2000Device::~CPeCon2000Device()
{
   // nop
}


int CPeCon2000Device::Initialize()
{
   PdlDeviceInfo info;
   deviceIdentifierString = "Not connected";

   ready = false;

   if(PdlEnumReset() != PdlErrorSuccess)
   {
      return DEVICE_ERR;
   }

   while(PdlEnumNext(&info) == PdlErrorSuccess)
   {
      LogMessage(string("Found PeCon Series 2000 Device - ") + deviceInfoToString(&info), false);
   }

   if(PdlEnumReset() != PdlErrorSuccess)
   {
      return DEVICE_ERR;
   }

   int foundOne = false;

   while(PdlEnumNext(&info) == PdlErrorSuccess)
   {

      if(specificSerial.size() == 0)
	   {
         LogMessage("Connecting to first PeCon Series 2000 device - " + deviceInfoToString(&info));
         foundOne = true;
         break;
      }
	   else if(string(info.serial) == specificSerial)
	   {
         LogMessage("Connecting to PeCon Series 2000 device matching specific serial - " + deviceInfoToString(&info));
         foundOne = true;
         break;
      }
	   else
	   {
         LogMessage("Found PeCon Series 2000 device, but skipping it due to serial mismatch - " + deviceInfoToString(&info));
         continue;
      }
   }

   if(foundOne && PdlOpen(&info, &handle) == PdlErrorSuccess) 
   {

      deviceIdentifierString = deviceInfoToString(&info);

      CreateProperty("Name",string(info.name).c_str(), MM::String, true, NULL, false);
      CreateProperty("Serial", string(info.serial).c_str(), MM::String, true, NULL, false);

      char component_name[255] = {0};
      unsigned char status = 0, controlmode = 0;
      DWORD version = 0;

      PdlGetVersion(&version);

      CreateProperty("LibraryVersion", formatVersion(version).c_str(), MM::String, true, NULL, false);

      PdlGetDeviceStatus(handle, &status);


      CreateProperty("Status-Numeric", CDeviceUtils::ConvertToString((int)status), MM::Integer, true, new DeviceActionEx(this, &CPeCon2000Device::OnDeviceStatus, MTYPE_NUMBER), false);

      CreateProperty("Status-Identifier", DeviceStatusMapper.ToIdentifier[status], MM::String, true, new DeviceActionEx(this, &CPeCon2000Device::OnDeviceStatus, MTYPE_IDENTIFIER), false);

      CreateProperty("Status-Message", DeviceStatusMapper.ToMessage[status], MM::String, true, new DeviceActionEx(this, &CPeCon2000Device::OnDeviceStatus, MTYPE_MESSAGE), false);


      PdlGetControlMode(handle, &controlmode);
      
      CreateProperty("KeypadBlocked", CDeviceUtils::ConvertToString(controlmode != PdlControlModeKeypadAndUsb), MM::Integer, false, new DeviceAction(this, &CPeCon2000Device::OnControlMode), false);
      AddAllowedValue("KeypadBlocked", "0");
      AddAllowedValue("KeypadBlocked", "1");


      for(unsigned char component_num = 1; PdlGetComponentName(handle, component_num, (char *)&component_name, sizeof(component_name)) == PdlErrorSuccess; component_num++)
      {
         unsigned char loopcontrol = 0, status = 0;
         short actual = 0, setpoint = 0, low = 0, high = 0; 

         PdlGetActualValue(handle, component_num, &actual);
         PdlGetSetpointValue(handle, component_num, &setpoint);
         PdlGetSetpointValueRange(handle, component_num, &low, &high);
         PdlGetLoopControl(handle, component_num, &loopcontrol);

         PdlGetChannelStatus(handle, component_num, &status);

         string prefix = string("Ch") + CDeviceUtils::ConvertToString((int)component_num) + string("-");

         CreateProperty((prefix + "Name").c_str(), string(component_name).c_str(), MM::String, true, NULL, false);

         CreateProperty((prefix + "ActualValue").c_str(), CDeviceUtils::ConvertToString((double)actual * precision), MM::Float, true, new DeviceActionEx(this, &CPeCon2000Device::OnActualValue, (long)component_num), false);
         SetProperty((prefix + "ActualValue").c_str(), CDeviceUtils::ConvertToString((double)actual * precision));

         CreateProperty((prefix + "Setpoint").c_str(), CDeviceUtils::ConvertToString((double)setpoint * precision), MM::Float, false, new DeviceActionEx(this, &CPeCon2000Device::OnSetValue, (long)component_num), false);
         SetPropertyLimits((prefix + "Setpoint").c_str(), (double)low * precision, (double)high * precision);
               
         CreateProperty((prefix + "LoopControl").c_str(), CDeviceUtils::ConvertToString((int)loopcontrol), MM::Integer, false, new DeviceActionEx(this, &CPeCon2000Device::OnLoopControl, (long)component_num), false);
         AddAllowedValue((prefix + "LoopControl").c_str(), "0");
         AddAllowedValue((prefix + "LoopControl").c_str(), "1");

         CreateProperty((prefix + "Status-Numeric").c_str(), CDeviceUtils::ConvertToString((int)status), MM::Integer, true, new DeviceActionEx(this, &CPeCon2000Device::OnChannelStatus, MTYPE_NUMBER | (long)component_num), false);
         CreateProperty((prefix + "Status-Identifier").c_str(), ChannelStatusMapper.ToIdentifier[status], MM::String, true, new DeviceActionEx(this, &CPeCon2000Device::OnChannelStatus, MTYPE_IDENTIFIER | (long)component_num), false);
         CreateProperty((prefix + "Status-Message").c_str(), ChannelStatusMapper.ToMessage[status], MM::String, true, new DeviceActionEx(this, &CPeCon2000Device::OnChannelStatus, MTYPE_MESSAGE | (long)component_num), false);
      }


      ready = true;
      retryErrorLogged = false;
      errorLogged = false;

      return DEVICE_OK;
   }
   return DEVICE_NOT_CONNECTED; // apparently, no device was found
   //return DEVICE_ERR;
}

int CPeCon2000Device::Shutdown()
{
   ready = false;

   PdlClose(handle);
   PdlEnumFree();

   return DEVICE_OK;
}


bool CPeCon2000Device::checkDevice()
{
   unsigned char status;

   return checkError(PdlGetDeviceStatus(handle, &status));
}


struct is_older_than {
   MM::MMTime now;
   double delta;

   inline is_older_than(MM::MMTime now, double delta) : now(now), delta(delta) {};

   inline bool operator()(const MM::MMTime& then) {
      return (now - then) > delta;
   }
};


bool CPeCon2000Device::checkError(PdlError error)
{
   if(error == PdlErrorSuccess)
   {
      return true;
   }

   if(!errorLogged)
   {
      LogMessage(
         string("PeCon Series 2000 Error occurred: ") +
         string(ErrorMapper.ToIdentifier[error]) +
         string("(") + string(ErrorMapper.ToMessage[error]) + string(").") +
         string(" Will try to reconnect the device! ") + 
         deviceIdentifierString, false);
      errorLogged = true;
   }

   Shutdown();

   double retryTimeout = 60.0;

   MM::MMTime now = GetCurrentMMTime();

   lastErrorOccurrences.push_back(now);

   is_older_than is_older_than_a_minute(now, retryTimeout);
   lastErrorOccurrences.remove_if(is_older_than_a_minute);

   if(lastErrorOccurrences.size() > retries)
   {
      if(!retryErrorLogged)
      {
         LogMessage(
            string("PeCon Series 2000 Retry limit (per ") + CDeviceUtils::ConvertToString(retryTimeout) + string("s) reached! Device is down now. ") + 
            deviceIdentifierString, false);
         retryErrorLogged = true;
      }
   }
   else
   {
      Initialize();
   }

   return false;
}

int CPeCon2000Device::OnSpecificSerial(MM::PropertyBase *pProp, MM::ActionType eAct)
{
   
   if(eAct == MM::BeforeGet)
   {
      pProp->Set(specificSerial.c_str());
   }
   else if(eAct == MM::AfterSet)
   {
      pProp->Get(specificSerial);
   }

   return DEVICE_OK;
}

int CPeCon2000Device::OnRetriesOnError(MM::PropertyBase *pProp, MM::ActionType eAct)
{
   if(eAct == MM::BeforeGet)
   {
      pProp->Set(retries);
   }
   else if(eAct == MM::AfterSet)
   {
      pProp->Get(retries);
   }

   return DEVICE_OK;
}

int CPeCon2000Device::OnReady(MM::PropertyBase *pProp, MM::ActionType eAct)
{
   if(eAct == MM::BeforeGet)
   {
      pProp->Set((long)ready);
   }

   return DEVICE_OK;
}


int CPeCon2000Device::OnControlMode(MM::PropertyBase *pProp, MM::ActionType eAct)
{
   unsigned char controlmode;

   if(eAct == MM::BeforeGet)
   {
      if(!checkDevice() || !checkError(PdlGetControlMode(handle, &controlmode)))
      {
         return DEVICE_ERR;
      }

      pProp->Set((long)(controlmode == PdlControlModeKeypadAndUsb ? 0 : 1));
      
   }
   else if(eAct == MM::AfterSet)
   {
      long shouldbe;
      pProp->Get(shouldbe);

      controlmode = (unsigned char)(shouldbe == 1 ? PdlControlModeUsbExclusive : PdlControlModeKeypadAndUsb);

      if(!checkDevice() || !checkError(PdlSetControlMode(handle, &controlmode)))
      {
         return DEVICE_ERR;
      }
   }

   return DEVICE_OK;
}

int CPeCon2000Device::OnDeviceStatus(MM::PropertyBase *pProp, MM::ActionType eAct, long data)
{

   if(eAct == MM::BeforeGet)
   {
      unsigned char status = 0;

      if(!checkDevice() || !checkError(PdlGetDeviceStatus(handle, &status)))
      {
         return DEVICE_ERR;
      }
      
      if(data & MTYPE_NUMBER)
      {
         pProp->Set((long)status);
      }
      else if(data & MTYPE_IDENTIFIER)
      {
         pProp->Set(DeviceStatusMapper.ToIdentifier[status]);
      }
      else if(data & MTYPE_MESSAGE)
      {
         pProp->Set(DeviceStatusMapper.ToMessage[status]);
      }
   }

   return DEVICE_OK;
}

int CPeCon2000Device::OnChannelStatus(MM::PropertyBase *pProp, MM::ActionType eAct, long data)
{
   unsigned char component = data & 0xff;

   if(eAct == MM::BeforeGet)
   {
      unsigned char status = 0;
      if(!checkDevice() || !checkError(PdlGetChannelStatus(handle, component, &status)))
      {
         return DEVICE_ERR;
      }
      
      if(data & MTYPE_NUMBER)
      {
         pProp->Set((long)status);
      }
      else if(data & MTYPE_IDENTIFIER)
      {
         pProp->Set(ChannelStatusMapper.ToIdentifier[status]);
      }
      else if(data & MTYPE_MESSAGE)
      {
         pProp->Set(ChannelStatusMapper.ToMessage[status]);
      }
   }

   return DEVICE_OK;
}


int CPeCon2000Device::OnActualValue(MM::PropertyBase *pProp, MM::ActionType eAct, long data)
{
   if(eAct == MM::BeforeGet)
   {
      short value = 0;
      if (!checkDevice() || !checkError(PdlGetActualValue(handle, (unsigned char)data, &value)))
      {
         return DEVICE_ERR;
      }

      pProp->Set((double)value * precision);
   }

   return DEVICE_OK;
}

int CPeCon2000Device::OnSetValue(MM::PropertyBase *pProp, MM::ActionType eAct, long data)
{

   short setpoint = 0, oldsetpoint = 0;

   if(eAct == MM::BeforeGet)
   { 
      if(!checkDevice() || !checkError(PdlGetSetpointValue(handle, (unsigned char)data, &setpoint)))
      {
         return DEVICE_ERR;
      }

      pProp->Set((double)setpoint * precision);
   }
   else if(eAct == MM::AfterSet)
   {
      double shouldbe, tmp;
      pProp->Get(shouldbe);

      setpoint = (short)(shouldbe / precision);

      tmp = shouldbe / precision;
      tmp -= (int)tmp;
      
      if(tmp > 0 && tmp > (precision / 10)) {
         if(!checkDevice() || !checkError(PdlGetSetpointValue(handle, (unsigned char)data, &oldsetpoint)))
         {
            return DEVICE_ERR;
         }

         if(((double)oldsetpoint * precision) < shouldbe)
         {
            setpoint++;
         }
         else
         {
            setpoint--;
         }
      }
      
      if(!checkDevice() || !checkError(PdlSetSetpointValue(handle, (unsigned char)data, &setpoint))) {
         return DEVICE_ERR;
      }
   }

   return DEVICE_OK;
}

int CPeCon2000Device::OnLoopControl(MM::PropertyBase *pProp, MM::ActionType eAct, long data)
{

   unsigned char loopcontrol;

   if(eAct == MM::BeforeGet)
   { 
      if(!checkDevice() || !checkError(PdlGetLoopControl(handle, (unsigned char)data, &loopcontrol)))
      {
         return DEVICE_ERR;
      }

      pProp->Set((long)loopcontrol);
   }
   else if(eAct == MM::AfterSet)
   {
      long iloopcontrol;
      pProp->Get(iloopcontrol);
      loopcontrol = (unsigned char)iloopcontrol;

      if(!checkDevice() || !checkError(PdlSetLoopControl(handle, (unsigned char)data, &loopcontrol)))
      {
         return DEVICE_ERR;
      }
   }

   return DEVICE_OK;
}
