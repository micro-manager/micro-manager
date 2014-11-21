///////////////////////////////////////////////////////////////////////////////
// FILE:          OVP_ECS2.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Optic Valley Photonics Environmental Conditioning System Gen2
//
// COPYRIGHT:     Applied Scientific Instrumentation, Eugene OR
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
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 06/2014
//


#ifdef WIN32
#define snprintf _snprintf 
#pragma warning(disable: 4355)
#endif

#include "OVP_ECS2.h"
#include <boost/crc.hpp>
#include <boost/integer.hpp>
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <iostream>
#include <sstream>
#include <vector>

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ECSDeviceName, MM::GenericDevice, g_ECSDeviceDescription);
}


MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   string deviceStr = deviceName;
   if (deviceName == 0)
      return 0;
   else if (strcmp(deviceName, g_ECSDeviceName) == 0)
      return new ECS;
   else
      return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

ECS::ECS() :
   initialized_(false),
   port_("Undefined")
{
   CPropertyAction* pAct;

   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NO_SERIAL_COMM, g_Msg_ERR_NO_SERIAL_COMM);
   SetErrorText(ERR_SERIAL_COMM_BAD_RESPONSE, g_Msg_ERR_SERIAL_COMM_BAD_RESPONSE);

   pAct = new CPropertyAction (this, &ECS::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

ECS::~ECS()
{
   Shutdown();
}

int ECS::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int ECS::Initialize()
{
   CPropertyAction* pAct;

   CreateProperty(MM::g_Keyword_Name, g_ECSDeviceName , MM::String, true);
   CreateProperty(MM::g_Keyword_Description, "Environment Control System", MM::String, true);

   // property to allow sending arbitrary serial commands and receiving response
   pAct = new CPropertyAction (this, &ECS::OnSerialCommand);
   CreateProperty(g_SerialCommandPropertyName, "", MM::String, false, pAct);
   // this is only changed programmatically, never by user
   CreateProperty(g_SerialResponsePropertyName, "", MM::String, false);

   // current temperature, read only
   pAct = new CPropertyAction (this, &ECS::OnTemperature);
   CreateProperty(g_TemperatureCurrent, "0", MM::Float, true, pAct);

   // temperature set point
   pAct = new CPropertyAction (this, &ECS::OnTempSetPoint);
   CreateProperty(g_TemperatureSetPoint, "0", MM::Float, false, pAct);

   // temperature controller output power, read only
   pAct = new CPropertyAction (this, &ECS::OnHeaterPower);
   CreateProperty(g_HeaterPower, "0", MM::Float, true, pAct);

   // current CO2 concentration, read only
   pAct = new CPropertyAction (this, &ECS::OnCO2Level);
   CreateProperty(g_CO2Level, "0", MM::Float, true, pAct);

   // CO2 set point
   pAct = new CPropertyAction (this, &ECS::OnCO2SetPoint);
   CreateProperty(g_CO2SetPoint, "0", MM::Float, false, pAct);

   // CO2 deadband for set point (by definition the setpoint is the upper threshold,
   //   the lower threshold (which is stored) is the upper threshold minus the deadband
   pAct = new CPropertyAction (this, &ECS::OnCO2Deadband);
   CreateProperty(g_CO2SetPointDeadband, "0", MM::Float, false, pAct);


   initialized_ = true;
   return DEVICE_OK;
}

void ECS::GetName(char* pszName) const
{
   CDeviceUtils::CopyLimitedString(pszName, g_ECSDeviceName);
}

bool ECS::Busy()
{
   return false;
}

int ECS::GetTempSP(double& sp)
{
   static const Message QUERY = HexString2Message("010300190001");
   static const Message REPLY = HexString2Message("010302");
   RETURN_ON_MM_ERROR( QueryCommandVerify(QUERY, REPLY, 5) );
   sp = (double)Message2UInt(GetMessageEnd(serialAnswer_, 2));
   sp /= 10;  // temp given as integer 250-400 for 25.0-40.0 degC
   if(sp < 0) sp = 0;
   return DEVICE_OK;
}


int ECS::SetTempSP(double sp)
{
   static const Message WRITE = HexString2Message("01060019");
   if(sp<0) sp = 0;
   sp *= 10;  // set point given as integer 250-400 for 25.0-40.0 degC
   sp += 0.5;    // turn subsequent type cast (which does floor) into round operation
   Message num = ULong2Message((unsigned long)sp, 2);
   Message msg = WRITE;
   msg.insert(msg.end(), num.begin(), num.end());
   RETURN_ON_MM_ERROR( QueryCommandVerify(msg, msg, 6) );
   return DEVICE_OK;
}

int ECS::GetTemp(double& temp)
{
   static const Message QUERY = HexString2Message("010300010001");
   static const Message REPLY = HexString2Message("010302");
   RETURN_ON_MM_ERROR( QueryCommandVerify(QUERY, REPLY, 5) );
   temp = (double)Message2UInt(GetMessageEnd(serialAnswer_, 2));
   temp /= 10;  // temp given as integer 250-400 for 25.0-40.0 degC
   return DEVICE_OK;
}

int ECS::GetCO2SP(double& sp)
{
   static const Message QUERY = HexString2Message("2046005204");
   static const Message REPLY = HexString2Message("204604");
   RETURN_ON_MM_ERROR( QueryCommandVerify(QUERY, REPLY, 7) );
   double deadband = (double)Message2UInt(GetMessageEnd(serialAnswer_, 2));
   double lowsp = (double)Message2UInt(GetSubMessage(serialAnswer_, 3, 2));
   sp = (lowsp + deadband) / 1000;   // concentrations in 10ppm in controller and % in micro-manager
   if(sp < 0) sp = 0;
   return DEVICE_OK;
}


int ECS::SetCO2SP(double sp)
{
   static const Message WRITE = HexString2Message("2043005202");
   static const Message WRITE_REPLY = HexString2Message("2043");
   double band;
   GetCO2Deadband(band);  // need to compute lower threshold using setpoint and deadband
   // user specifies setpoint = upper threshold but controller stores lower threshold = setpoint - deadband
   sp -= band;  // we store the lower threshold, which is setpoint - deadband
   if(sp < 0) sp = 0;
   sp *= 1000;  // concentrations in 10ppm in controller and % in micro-manager
   sp += 0.5;    // turn subsequent type cast (which does floor) into round operation
   Message num = ULong2Message((unsigned long)sp, 2);
   Message msg = WRITE;
   msg.insert(msg.end(), num.begin(), num.end());
   RETURN_ON_MM_ERROR( QueryCommandVerify(msg, WRITE_REPLY, 2) );
   return DEVICE_OK;
}

int ECS::GetCO2Deadband(double& band)
{
   static const Message QUERY = HexString2Message("2046005402");
   static const Message REPLY = HexString2Message("204602");
   RETURN_ON_MM_ERROR( QueryCommandVerify(QUERY, REPLY, 5) );
   band = (double)Message2UInt(GetMessageEnd(serialAnswer_, 2));
   band /= 1000;  // concentrations in 10ppm in controller and % in micro-manager
   return DEVICE_OK;
}

int ECS::SetCO2Deadband(double band)
{
   static const Message WRITE = HexString2Message("2043005204");
   static const Message WRITE_REPLY = HexString2Message("2043");
   double sp;
   GetCO2SP(sp);  // need to compute new lower threshold when changing deadband
   sp *= 1000;
   if(band<0) band = 0;
   band *= 1000;  // concentrations in 10ppm in controller and % in micro-manager
   sp -= band;    // this is lower threshold
   if(sp < 0) sp = 0;
   sp += 0.5;   // turn subsequent type cast (which does floor) into round operation
   Message msg = WRITE;
   Message num = ULong2Message((unsigned long)sp, 2);
   msg.insert(msg.end(), num.begin(), num.end());  // append the lower threshold to message
   num.clear();
   band += 0.5;   // turn subsequent type cast (which does floor) into round operation
   num = ULong2Message((unsigned long)band, 2);
   msg.insert(msg.end(), num.begin(), num.end());  // append the deadband to message
   RETURN_ON_MM_ERROR( QueryCommandVerify(msg, WRITE_REPLY, 2) );
   return DEVICE_OK;
}

int ECS::GetCO2(double& temp)
{
   static const Message QUERY = HexString2Message("2044000802");
   static const Message REPLY = HexString2Message("204402");
   RETURN_ON_MM_ERROR( QueryCommandVerify(QUERY, REPLY, 5) );
   temp = (double)Message2UInt(GetMessageEnd(serialAnswer_, 2));
   temp /= 1000;  // concentrations in 10ppm in controller and % in micro-manager
   return DEVICE_OK;
}


int ECS::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_) // don't let user change after initialization
      {
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
      else
         pProp->Get(port_);
   }
   return DEVICE_OK;
}

int ECS::OnSerialCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // do nothing
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      Message msg = HexString2Message(tmpstr);
      RETURN_ON_MM_ERROR ( QueryCommand(msg) );
      SetProperty(g_SerialResponsePropertyName, Message2HexString(serialAnswer_).c_str());
   }
   return DEVICE_OK;
}

int ECS::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet || eAct == MM::AfterSet)
   {
      // always read from controller
      double tmp;
      GetTemp(tmp);
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int ECS::OnTempSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double tmp;
   if (eAct == MM::BeforeGet)
   {
      GetTempSP(tmp);
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(tmp);
      RETURN_ON_MM_ERROR ( SetTempSP(tmp) );
   }
   return DEVICE_OK;
}

int ECS::OnHeaterPower(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   static const Message QUERY = HexString2Message("010300020001");
   static const Message REPLY = HexString2Message("010302");
   if (eAct == MM::BeforeGet || eAct == MM::AfterSet)
   {
      // always read from controller
      RETURN_ON_MM_ERROR( QueryCommandVerify(QUERY, REPLY, 5) );
      double tmp = (double)Message2UInt(GetMessageEnd(serialAnswer_, 2));
      tmp = tmp/10;  // power given as integer 0-1000 for 0.0%-100.0%
      if (tmp < 0 || !pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int ECS::OnCO2Level(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet || eAct == MM::AfterSet)
   {
      // always read from controller
      double tmp;
      GetCO2(tmp);
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int ECS::OnCO2SetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double tmp;
   if (eAct == MM::BeforeGet)
   {
      GetCO2SP(tmp);
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(tmp);
      RETURN_ON_MM_ERROR ( SetCO2SP(tmp) );
   }
   return DEVICE_OK;
}

int ECS::OnCO2Deadband(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double tmp;
   if (eAct == MM::BeforeGet)
   {
      GetCO2Deadband(tmp);
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(tmp);
      RETURN_ON_MM_ERROR ( SetCO2Deadband(tmp) );
   }
   return DEVICE_OK;
}





int ECS::ClearComPort(void)
{
   return PurgeComPort(port_.c_str());
}

Message ECS::GetCRCChecksum(Message msg)
// returns a 2-element vector (16 bits)
// relies on the header-only Boost CRC library (boost/crc.hpp)
// when I compile I see 3 warnings related to type conversions
{
   Message crc_vector;
   boost::uint16_t crc_uint;
   boost::crc_optimal<16, 0x8005, 0xFFFF, 0, true, true> crc_modbus;
   crc_modbus.process_bytes(&msg[0], msg.size());
   crc_uint = crc_modbus.checksum();
   crc_vector.push_back(crc_uint & 0xFF);
   crc_vector.push_back((crc_uint & 0xFF00) >> 8);
   return crc_vector;
}

Message ECS::HexString2Message(string str)
{
   // credit http://stackoverflow.com/questions/13490977/convert-hex-stdstring-to-unsigned-char
   Message result;
   unsigned int code;
   std::string::size_type offset = 0;
   std::stringstream ss;
   while(offset < str.length())
   {
      ss.clear();
      ss << std::hex << str.substr(offset, 2);
      ss >> code;
      result.push_back(static_cast<unsigned char>(code));
      offset += 2;
   }
   return result;
}

string ECS::Message2HexString(Message msg)
{
   // credit http://stackoverflow.com/questions/3381614/c-convert-string-to-hexadecimal-and-vice-versa
   static const char* const lut = "0123456789ABCDEF";
   string result;
   size_t len = msg.size();
   result.reserve(2*len);
   for(size_t i = 0; i < len; ++i)
   {
      unsigned char c = msg[i];
      result.push_back(lut[c >> 4]);
      result.push_back(lut[c & 0x0F]);
   }
   return result;
}

unsigned int ECS::Message2UInt(Message msg)
// assumes the message only has 2 bytes (2 unsigned chars)
{
   return (msg[0] << 8) | msg[1];
}

unsigned long ECS::Message2ULong(Message msg)
// assumes the message has 4 bytes
{
   return (msg[0] << 24) | (msg[1] << 16) | (msg[2] << 8) | (msg[3]);
}

Message ECS::ULong2Message(unsigned long myint, size_t len)
// only the lowest "len" bytes will be returned
{
   // we assume long has 4 bytes, if it has more we just take lower 4 bytes
   Message msg;
   msg.push_back((unsigned char)((myint >> 24) & 0xFF));
   msg.push_back((unsigned char)((myint >> 16) & 0xFF));
   msg.push_back((unsigned char)((myint >> 8) & 0xFF));
   msg.push_back((unsigned char)((myint >> 0) & 0xFF));
   return GetMessageEnd(msg, len);
}

Message ECS::GetSubMessage(Message msg, int start, size_t len)
// if start is negative then will start that far back from the end of the vector
{
   Message result;
   int actual_start;
   if(start < 0 && (msg.size() + start)>0)
      actual_start = (int)msg.size() + start;
   else
      actual_start = start;
   for(size_t i = actual_start; i < actual_start + len; ++i)
   {
      result.push_back(msg[i]);
   }
   return result;
}

Message ECS::GetMessageEnd(Message msg, size_t len)
// will grab the last "len" bytes of the message
{
   Message result;
   int actual_start = (int)msg.size() - (int)len;
   if(actual_start < 0) return result;
   for(size_t i = actual_start; i < actual_start + len; ++i)
   {
      result.push_back(msg[i]);
   }
   return result;
}

int ECS::QueryCommand(Message command)
{
   Message response(MAX_RESPONSE_LENGTH+1, 0x00);
   Message command_full;
   Message checksum;
   Message checksum_calc;
   unsigned long bytesRead;
   long attepmts = 0;
   bool success = false;

   // append checksum to requested command
   command_full = command;
   checksum = GetCRCChecksum(command);
   command_full.push_back(checksum[0]);
   command_full.push_back(checksum[1]);

   // write to com port and get reply, every time check to see if we got the right checksum
   while(!success && attepmts++ <= RECEIVE_RETRIES)
   {
      RETURN_ON_MM_ERROR ( ClearComPort() );
      RETURN_ON_MM_ERROR ( WriteToComPort(port_.c_str(), &command_full[0], (unsigned int)command_full.size()) );
      CDeviceUtils::SleepMs(RECEIVE_DELAY_MS);  // wait for response to become available to us; could get more fancy if faster speed needed
      RETURN_ON_MM_ERROR ( ReadFromComPort(port_.c_str(), &response[0], MAX_RESPONSE_LENGTH, bytesRead) );
      if(bytesRead == 0)  // try one more time if we didn't get anything at all
      {
         CDeviceUtils::SleepMs(RECEIVE_DELAY_MS/2);
         RETURN_ON_MM_ERROR ( ReadFromComPort(port_.c_str(), &response[0], MAX_RESPONSE_LENGTH, bytesRead) );
      }
      if(bytesRead < 2) continue;  // proceed to try again if we didn't get a response long enough to have the checksum
      response.resize(bytesRead);
      checksum = Message(response.end()-2, response.end());  // remember the response's checksum
      response.resize(bytesRead-2);  // remove the last two (checksum) bytes
      checksum_calc = GetCRCChecksum(response);
      success = (checksum == checksum_calc);  // if the checksums are equal then we successfully communicated
   }

   if(!success) return ERR_NO_SERIAL_COMM;

   // only save command/response if we successfully sent/received
   serialCommand_ = command;
   serialAnswer_ = response;
   return DEVICE_OK;
}

int ECS::QueryCommandVerify(Message command, Message responsePrefix, size_t expectedLength)
// expected length doesn't include checksum
{
   RETURN_ON_MM_ERROR ( QueryCommand(command) );
   if(serialAnswer_.size() != expectedLength)
      return ERR_SERIAL_COMM_BAD_RESPONSE;
   for(size_t i = 0; i < responsePrefix.size(); ++i)
   {
      if(serialAnswer_[i] != responsePrefix[i])
         return ERR_SERIAL_COMM_BAD_RESPONSE;
   }
   return DEVICE_OK;
}

MM::DeviceDetectionStatus ECS::DetectDevice()   // looks for hub, not child devices
{
   if (initialized_)
      return MM::CanCommunicate;

   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
   char answerTO[MM::MaxStrLength];

   try
   {
      std::string portLowerCase = port_;
      for( std::string::iterator its = portLowerCase.begin(); its != portLowerCase.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      if( 0< portLowerCase.length() &&  0 != portLowerCase.compare("undefined")  && 0 != portLowerCase.compare("unknown") )
      {
         result = MM::CanNotCommunicate;

         // record the default answer time out
         GetCoreCallback()->GetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);

         // device specific default communication parameters for OCP ECS controller
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Off");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "9600" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "500.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "DelayBetweenCharsMs", "0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();
         PurgeComPort(port_.c_str());
         double tmp;
         int ret = GetTemp(tmp);
         if( DEVICE_OK != ret )
         {
            LogMessageCode(ret,true);
         }
         else
         {
            // to succeed must reach here....
            result = MM::CanCommunicate;
         }
         pS->Shutdown();
         // restore the AnswerTimeout to the default
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);
      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }

   return result;
}
