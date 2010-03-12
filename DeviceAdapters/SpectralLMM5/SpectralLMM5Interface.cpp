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

#ifdef WIN32
#include <winsock.h>
#else
#include <netinet/in.h>
#endif

SpectralLMM5Interface::SpectralLMM5Interface(std::string port, MM::PortType portType) :
   laserLinesDetected_ (false),
   nrLines_ (5),
   readWriteSame_(false)
{
   port_ = port;
   portType_ = portType;
   initialized_ = true;
}

SpectralLMM5Interface::~SpectralLMM5Interface(){};

/*
 * The Spectral LMM5 has a silly difference between USB and serial communication:
 * Commands can be sent straight to USB.  Commands to the serial port need to converted in some kind of weird ASCI:  The command "0x1A0xFF0x000x12<CR>" becomes "1AFF0012<CR>".  Presumably, the same weird conversion takes place on the way back.  We handle this translation in this function
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
      unsigned char c[2];
      c[0]=0x01;
      c[1]=0x02;
      ret = core.WriteToSerial(&device, port_.c_str(), c, 2);
      //ret = core.WriteToSerial(&device, port_.c_str(), buf, bufLen);
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
      std::ostringstream os;
      os << "LMM5 answered: " << strAnswer << " Port status: " << ret;
      core.LogMessage(&device, os.str().c_str(), true);
      // 'translate' back into numbers:
      std::string tmp = strAnswer;
      for (unsigned int i=0; i < tmp.length()/2; i++) {
         char * end;
         long j = strtol(tmp.substr(i*2,2).c_str(), &end, 16);
         answer[i] = (unsigned char) j;
         // printf("c:%x i:%u j:%ld\n", answer[i], i, j);
         read++;
      }
   } else // TODO: check that we have a USB port
   {
      // The USB port will attempt to read up to answerLen characters
      ret = core.ReadFromSerial(&device, port_.c_str(), answer, answerLen, read);
      if (ret != DEVICE_OK)
         return ret;
      std::ostringstream os;
      os << "LMM5 answered: ";
      for (unsigned int i=0; i < read; i++)
         os << std::hex << answer[i];
      os << std::endl;
      core.LogMessage(&device, os.str().c_str(), true);
   }
   return DEVICE_OK;
}

int SpectralLMM5Interface::DetectLaserLines(MM::Device& device, MM::Core& core) {
   if (laserLinesDetected_)
      return DEVICE_OK;
   const unsigned long bufLen = 1;
   unsigned char buf[bufLen];
   buf[0]=0x08;
   const unsigned long answerLen = 11;
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
   printf("NrLines: %d\n", nrLines_);
   for (int i=0; i<nrLines_; i++) 
   {
      laserLines_[i].lineNr = i;
      laserLines_[i].waveLength = ntohs(*(lineP+i)) / 10;
      if (*(lineP+i) == 0) {
         laserLines_[i].present = false;
      } else {
         laserLines_[i].present = true;
         std::ostringstream os;
         os << laserLines_[i].waveLength << "nm-" << i + 1;
         laserLines_[i].name = os.str();
      }
      // printf ("Line: %d %f %d %s\n", i, laserLines_[i].waveLength, laserLines_[i].present, laserLines_[i].name.c_str());
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

int SpectralLMM5Interface::GetTransmission(MM::Device& device, MM::Core& core, long laserLine, double& transmission) {
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
   std::ostringstream os;
   os << "Transmission for line" << laserLine << " is: " << transmission;
   printf("%s tr: %d\n", os.str().c_str(), tr);

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

int SpectralLMM5Interface::SetExposureConfig(MM::Device& device, MM::Core& core, std::string config) {
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
   printf ("Set Exposure confign \n");
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
   if (readWriteSame_)
      buf[0]=0x021;
   else
      buf[0]=0x027;
   const unsigned long answerLen = 70;
   unsigned char answer[answerLen];
   unsigned long read;
   printf ("Detecting Exposure Config: \n");
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

   const unsigned long answerLen = 10;//1; Why does 1 cause a runtime exception? 'Stack around the variable "answer" was corrupted.'
   unsigned char answer[answerLen];
   printf ("Set Trigger Out \n");
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
   if (readWriteSame_)
      buf[0]=0x023;
   else
      buf[0]=0x026;
   const unsigned long answerLen = 10;//6;
   unsigned char answer[answerLen];
   unsigned long read;
   printf ("Detecting Trigger out Config: \n");
   int ret = ExecuteCommand(device, core, buf, bufLen, answer, answerLen, read);
   if (ret != DEVICE_OK)
      return ret;
 
   // See if the controller acknowledged our command
   if (answer[0] != 0x23)
      return ERR_UNEXPECTED_ANSWER;

   memcpy(config, answer + 1, 4);

   return DEVICE_OK;
}


