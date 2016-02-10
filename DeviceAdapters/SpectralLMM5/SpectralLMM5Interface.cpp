///////////////////////////////////////////////////////////////////////////////
// FILE:          SpectralInterface.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Interface for the Spectral LMM5
//
// COPYRIGHT:     University of California, San Francisco, 2009
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
// AUTHOR:        Nico Stuurman (nico@cmp.ucf.edu), 2/7/2008
//
#include "SpectralLMM5Interface.h"
#include "SpectralLMM5.h"

#include <sstream>
#include <iomanip>

#ifdef WIN32
#include <winsock.h>
#else
#include <netinet/in.h>
#endif

SpectralLMM5Interface::SpectralLMM5Interface(std::string port, MM::PortType portType) :
   laserLinesDetected_ (false),
   firmwareDetected_(false),
   nrLines_ (5)
{
   port_ = port;
   portType_ = portType;
   initialized_ = true;
}

SpectralLMM5Interface::~SpectralLMM5Interface(){};

/*
 * The LMM5 USB HID commands are a sequence of binary bytes with no terminator.
 * The serial commands are the same bytes formatted as an ASCII hex string, two
 * characters per byte, and terminated with a CR; e.g.:
 *   USB:    "\x1a\xff\x00\x12" (4 bytes)
 *   RS-232: "1AFF0012\r" (9 bytes)
 * The opposite transformation takes place for the reply.
 *
 * This function abstracts these differences. Note that the exact answerLen is
 * important for USB HID: reading excess bytes can result in garbage being
 * appended to the reply (at least on Windows).
 */
int SpectralLMM5Interface::ExecuteCommand(MM::Device& device, MM::Core& core, unsigned char* buf, unsigned long bufLen, unsigned char* answer, unsigned long answerLen, unsigned long& read) 
{
   int ret;
   if (portType_ == MM::SerialPort) 
   {
      std::string serialCommand;
      char tmp[3];
      tmp[2] = 0;
      for (unsigned long i=0; i<bufLen; i++) {
         sprintf(tmp, "%.2x", buf[i]);
         serialCommand += tmp;
      }
      ret = core.SetSerialCommand(&device, port_.c_str(), serialCommand.c_str(), "\r");
   } else  // check for USB port
   {
      ret = core.WriteToSerial(&device, port_.c_str(), buf, bufLen);
   }

   if (ret != DEVICE_OK)  
      return ret;
  
   if (portType_ == MM::SerialPort) 
   {
      char strAnswer[128];
      read = 0;
      ret = core.GetSerialAnswer(&device, port_.c_str(), 128, strAnswer, "\r");
      if (ret != DEVICE_OK)
         return ret;
      
      // 'translate' back into numbers:
      std::string tmp = strAnswer;
      for (unsigned int i=0; i < tmp.length()/2; i++) {
         char * end;
         long j = strtol(tmp.substr(i*2,2).c_str(), &end, 16);
         answer[i] = (unsigned char) j;
         read++;
      }
   } else if (portType_ == MM::HIDPort) 
   {
      // The USB port will attempt to read up to answerLen characters
      ret = core.ReadFromSerial(&device, port_.c_str(), answer, answerLen, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

int SpectralLMM5Interface::DetectLaserLines(MM::Device& device, MM::Core& core) 
{
   if (laserLinesDetected_)
      return DEVICE_OK;

   // see if we have old or new firmware
   bool newFirmware = true;
   if (!firmwareDetected_) 
   {
      std::string version;
      GetFirmwareVersion(device, core, version);
   }
   if (majorFWV_ != 1 || minorFWV_ < 30) {  // the newer firmware emulates emulats the old firmware, but we do not want to confuse things or crash when we ask if the <100 lines have PWM (note that older majorversions are 2, 162, and 178)
      newFirmware = false;
   }

   const unsigned long bufLen = 1;
   unsigned char buf[bufLen];
   buf[0]=0x08;
   const unsigned long answerLen = 17;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   if (read < 8) {
      return ERR_UNEXPECTED_ANSWER;
   }

   uint16_t* lineP = (uint16_t*) (answer + 1);
   nrLines_ = (read-1)/2;
   char outputPort = 'A';
   for (int i=0; i<nrLines_; i++) 
   {
      laserLines_[i].lineNr = i;
      laserLines_[i].waveLength = ntohs(*(lineP+i)) / 10;
      if (*(lineP+i) == 0) {
         laserLines_[i].present = false;
      } else {
         if (newFirmware && laserLines_[i].waveLength <= 100) 
         {
            laserLines_[i].present = false;
            laserLines_[i].name = "Do not use";
         }
         else {
            laserLines_[i].present = true;
            std::ostringstream os;
            if (laserLines_[i].waveLength > 100)
            {
               os << laserLines_[i].waveLength << "nm-" << i + 1;
            } else
            {
               outputPort++;
               os << "output-port " << outputPort;
            }
            laserLines_[i].name = os.str();
         }
      }
   }

   laserLinesDetected_ = true;
   return DEVICE_OK;
}


int SpectralLMM5Interface::SetTransmission(MM::Device& device, MM::Core& core, long laserLine, double transmission) 
{
   const unsigned long bufLen = 4;
   unsigned char buf[bufLen];
   buf[0]=0x04;
   buf[1]=(unsigned char) laserLine;
   int16_t tr = (int16_t) (10*transmission);
   tr = htons(tr);
   memcpy(buf+2, &tr, 2);
   const unsigned long answerLen = 1;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   // See if the controller acknowledged our command
   if (answer[0] != 0x04)
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int SpectralLMM5Interface::GetTransmission(MM::Device& device, MM::Core& core, long laserLine, double& transmission) 
{
   const unsigned long bufLen = 2;
   unsigned char buf[bufLen];
   buf[0]=0x05;
   buf[1]= (unsigned char) laserLine;
   const unsigned long answerLen = 3;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   if (read < 3) {
      return ERR_UNEXPECTED_ANSWER;
   }

   // See if the controller acknowledged our command
   if (answer[0] != 0x05) {
      return ERR_UNEXPECTED_ANSWER;
   }

   int16_t tr = 0;
   memcpy(&tr, answer + 1, 2);
   tr = ntohs(tr);
   transmission = tr/10;

   return DEVICE_OK;
}

int SpectralLMM5Interface::SetShutterState(MM::Device& device, MM::Core& core, int state)
{
   const unsigned long bufLen = 2;
   unsigned char buf[bufLen];
   buf[0]=0x01;
   buf[1]=(unsigned char) state;
   const unsigned long answerLen = 1;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   // See if the controller acknowledged our command
   if (answer[0] != 0x01)
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int SpectralLMM5Interface::GetShutterState(MM::Device& device, MM::Core& core, int& state)
{
   const unsigned long bufLen = 1;
   unsigned char buf[bufLen];
   buf[0]=0x02;
   const unsigned long answerLen = 4;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   if (read < 2) {
      return ERR_UNEXPECTED_ANSWER;
   }

   // See if the controller acknowledged our command
   if (answer[0] != 0x02)
      return ERR_UNEXPECTED_ANSWER;

   state = (int) answer[1];

   return DEVICE_OK;
}

int SpectralLMM5Interface::SetExposureConfig(MM::Device& device, MM::Core& core, std::string config) 
{
   unsigned char* buf;
   int length = (int) config.size()/2;
   unsigned long bufLen = length +1;
   unsigned long read = 0;
   buf = (unsigned char*) malloc(bufLen);
   buf[0]=0x21;
   for (int i=0; i<length; i++) {
      char * end;
      long j = strtol(config.substr(i*2,2).c_str(), &end, 16);
      buf[i+1] = (unsigned char) j;
   }

   const unsigned long answerLen = 1;
   unsigned char answer[answerLen];
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   // See if the controller acknowledged our command
   if (answer[0] != 0x21)
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int SpectralLMM5Interface::GetExposureConfig(MM::Device& device, MM::Core& core, std::string& config)
{
   const unsigned long bufLen = 1;
   unsigned char buf[bufLen];
   buf[0]=0x027;
   const unsigned long answerLen = 70;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;
 
   // See if the controller acknowledged our command
   if (answer[0] != 0x27)
      return ERR_UNEXPECTED_ANSWER;

   config = "";
   for (unsigned int i=1; i < read; i++)
      config += buf[i];

   return DEVICE_OK;
}


int SpectralLMM5Interface::SetTriggerOutConfig(MM::Device& device, MM::Core& core, unsigned char * config) 
{
   const unsigned long bufLen = 5;
   unsigned char buf[bufLen];
   unsigned long read = 0;
   buf[0]=0x23;
   memcpy(buf + 1, config, 4);

   const unsigned long answerLen = 10;
   unsigned char answer[answerLen];
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   // See if the controller acknowledged our command
   if (answer[0] != 0x23)
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}

int SpectralLMM5Interface::GetTriggerOutConfig(MM::Device& device, MM::Core& core, unsigned char * config)
{
   const unsigned long bufLen = 1;
   unsigned char buf[bufLen];
   buf[0] = 0x26;
   const unsigned long answerLen = 10;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;
 
   // See if the controller acknowledged our command
   if (answer[0] != 0x26)
      return ERR_UNEXPECTED_ANSWER;

   memcpy(config, answer + 1, 4);

   return DEVICE_OK;
}


int SpectralLMM5Interface::GetFirmwareVersion(MM::Device& device, MM::Core& core, std::string& version)
{
   version.clear();

   const unsigned long bufLen = 1;
   unsigned char buf[bufLen];
   buf[0] = 0x14;
   const unsigned long answerLen = 3;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   if (answer[0] != 0x14 || read < answerLen)
      return ERR_UNEXPECTED_ANSWER;

   // The firmware version is a two-byte word.
   std::ostringstream oss;
   oss << static_cast<unsigned int>(answer[1]) << "." << static_cast<unsigned int>(answer[2]);
   version = oss.str();
   majorFWV_ = answer[1];
   minorFWV_ = answer[2];

   return DEVICE_OK;
}

int SpectralLMM5Interface::GetFLICRAvailable(MM::Device& device, MM::Core& core, bool& available) 
{
   available = false;
   if (!firmwareDetected_) 
   {
      std::string version;
      GetFirmwareVersion(device, core, version);
   }
   if (majorFWV_ != 1 || minorFWV_ < 30) {  // FLICR only available in firmware version 1.30 and higher (note that older majorversions are 2, 162, and 178)
      return DEVICE_OK; 
   }
   const unsigned long bufLen = 3;
   unsigned char buf[bufLen];
   buf[0] = 0x52;
   buf[1] = 0x10;
   buf[2] = 0x02;
   const unsigned long answerLen = 4;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   if ( read < answerLen)
      return ERR_UNEXPECTED_ANSWER;

   available = false;
   if (answer[3] == 0x01) {
      available = true;
   } 

   return DEVICE_OK;
}


int SpectralLMM5Interface::GetFLICRAvailableByLine(MM::Device& device, MM::Core& core, long laserLine, bool& available) 
{
   available = false;
   if (!firmwareDetected_) 
   {
      std::string version;
      GetFirmwareVersion(device, core, version);
   }
   if (majorFWV_ != 1 || minorFWV_ < 30) {  // FLICR only available in firmware version 1.30 and higher (note that older majorversions are 2, 162, and 178)
      return DEVICE_OK; 
   }
   const unsigned long bufLen = 4;
   unsigned char buf[bufLen];
   buf[0] = 0x52;
   buf[1] = 0x10;
   buf[2] = 0x03;
   buf[3] = (unsigned char) laserLine;
   const unsigned long answerLen = 4;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   if ( read < answerLen)
      return ERR_UNEXPECTED_ANSWER;

   available = false;
   if (answer[0] != 0X52 || answer[1] != 0x10 || answer[2] != 0x03)
      return ERR_UNEXPECTED_ANSWER;
   if (answer[3] == 0x01) {
      available = true;
   } 

   return DEVICE_OK;
}


int SpectralLMM5Interface::GetMaxFLICRValue(MM::Device& device, MM::Core& core, long laserLine, uint16_t & maxValue)
{
   maxValue = 10000;
   if (majorFWV_ != 1 || minorFWV_ < 30) {  // FLICR only available in firmware version 1.30 and higher (note that older majorversions are 2, 162, and 178)
      return DEVICE_OK; 
   }
   // TODO: check firmware version first
   const unsigned long bufLen = 4;
   unsigned char buf[bufLen];
   buf[0] = 0x52;
   buf[1] = 0x10;
   buf[2] = 0x04;
   buf[3] = (unsigned char) laserLine;
   const unsigned long answerLen = 5;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   if ( read < answerLen)
      return ERR_UNEXPECTED_ANSWER;

   if (answer[0] != 0X52 || answer[1] != 0x10 || answer[2] != 0x04)
      return ERR_UNEXPECTED_ANSWER;
   uint16_t highByte = answer[3];
   highByte = highByte << 8;
   uint16_t lowByte = answer[4];
   maxValue = highByte + lowByte;

   return DEVICE_OK;
}


int SpectralLMM5Interface::SetFLICRValue(MM::Device& device, MM::Core& core, long laserLine, uint16_t value) 
{
   const unsigned long bufLen = 7;
   unsigned char buf[bufLen];
   buf[0] = 0x53;
   buf[1] = 0x90;
   buf[2] = 0x10;
   buf[3] = 0x01;
   buf[4] = (unsigned char) laserLine;
   buf[5] = (value >> (8)) & 0xff;
   buf[6] = value & 0xff;
   const unsigned long answerLen = 1;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   if ( read < answerLen)
      return ERR_UNEXPECTED_ANSWER;

   if (answer[0] != 0X53)
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}
 
int SpectralLMM5Interface::GetFLICRValue(MM::Device& device, MM::Core& core, long laserLine, uint16_t& value)
{
   const unsigned long bufLen = 4;
   unsigned char buf[bufLen];
   buf[0] = 0x52;
   buf[1] = 0x10;
   buf[2] = 0x01;
   buf[3] = (unsigned char) laserLine;
   const unsigned long answerLen = 5;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   if ( read < answerLen)
      return ERR_UNEXPECTED_ANSWER;

   if (answer[0] != 0X52 || answer[1] != 0x10 || answer[2] != 0x01)
      return ERR_UNEXPECTED_ANSWER;

   uint16_t highByte = answer[3];
   highByte = highByte << 8;
   uint16_t lowByte = answer[4];
   value = highByte + lowByte;

   return DEVICE_OK;
}

int SpectralLMM5Interface::GetNumberOfOutputs(MM::Device& device, MM::Core& core, uint16_t& nrOutputs)
{
   // if the firmware does not support this command, return 1
   if (!firmwareDetected_) 
   {
      std::string version;
      GetFirmwareVersion(device, core, version);
   }
   if (majorFWV_ != 1 || minorFWV_ < 30) { 
         nrOutputs = 1;
      return DEVICE_OK; 
   }

   const unsigned long bufLen = 3;
   unsigned char buf[bufLen];
   buf[0] = 0x52;
   buf[1] = 0x20;
   buf[2] = 0x03;
   const unsigned long answerLen = 4;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   if ( read < answerLen)
      return ERR_UNEXPECTED_ANSWER;

   if (answer[0] != 0X52 || answer[1] != 0x20 || answer[2] != 0x03)
      return ERR_UNEXPECTED_ANSWER;

   nrOutputs = answer[3];

   return DEVICE_OK;
}

int SpectralLMM5Interface::GetOutput(MM::Device& device, MM::Core& core, uint16_t& output)
{
   const unsigned long bufLen = 2;
   unsigned char buf[bufLen];
   buf[0] = 0x55;
   buf[1] = 0x03;
   const unsigned long answerLen = 3;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   if ( read < answerLen)
      return ERR_UNEXPECTED_ANSWER;

   if (answer[0] != buf[0] || answer[1] != buf[1])
      return ERR_UNEXPECTED_ANSWER;

   output = answer[2];

   return DEVICE_OK;
}

int SpectralLMM5Interface::SetOutput(MM::Device& device, MM::Core& core, uint16_t output)
{
   const unsigned long bufLen = 4;
   unsigned char buf[bufLen];
   buf[0] = 0x54;
   buf[1] = 0xA7;
   buf[2] = 0x03;
   buf[3] = (unsigned char) output;
   const unsigned long answerLen = 1;
   unsigned char answer[answerLen];
   unsigned long read;
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;

   if ( read < answerLen)
      return ERR_UNEXPECTED_ANSWER;

   if (answer[0] != 0X54)
      return ERR_UNEXPECTED_ANSWER;

   return DEVICE_OK;
}
