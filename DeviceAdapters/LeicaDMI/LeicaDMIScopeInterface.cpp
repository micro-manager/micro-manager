///////////////////////////////////////////////////////////////////////////////
// FILE:       LeicaDMIScopeInterace.cpp
// PROJECT:    MicroManage
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
//
// COPYRIGHT:     100xImaging, Inc. 2008
// LICENSE:        
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

#include "LeicaDMIScopeInterface.h"
#include "LeicaDMIModel.h"
#include "LeicaDMI.h"
#include "LeicaDMICodes.h"
#include "../../MMDevice/ModuleInterface.h"
#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>


//////////////////////////////////////////
// Interface to the Leica microscope
//
LeicaScopeInterface::LeicaScopeInterface() :
   portInitialized_ (false),
   monitoringThread_(0),
   timeOutTime_(250000),
   initialized_ (false)
{
}

LeicaScopeInterface::~LeicaScopeInterface()
{
   if (monitoringThread_ != 0) {
   printf ("Stopping Thread\n");
      monitoringThread_->Stop();
   printf ("Waiting Thread\n");
      monitoringThread_->wait();
   printf ("Deleting Thread\n");
      delete (monitoringThread_);
      monitoringThread_ = 0;
   }
   initialized_ = false;
}

/**
 * Clears the serial receive buffer.
 */
void LeicaScopeInterface::ClearRcvBuf()
{
   memset(rcvBuf_, 0, RCV_BUF_LENGTH);
}

/*
 * Reads version number, available devices, device properties and some labels from the microscope and then starts a thread that keeps on reading data from the scope.  Data are stored in the array deviceInfo from which they can be retrieved by the device adapters
 */
int LeicaScopeInterface::Initialize(MM::Device& device, MM::Core& core)
{
   if (!portInitialized_)
      return ERR_PORT_NOT_OPEN;
   
   std::ostringstream os;
   std::ostringstream command;
   std::string version, answer;

   os << "Initializing Leica Microscope";
   core.LogMessage (&device, os.str().c_str(), false);
   os.str("");

   // empty the Rx serial buffer before sending commands
   ClearRcvBuf();
   ClearPort(device, core);

   // Get info about stand, firmware and available devices and store in the model
   int ret = GetStandInfo(device, core);
   if (ret != DEVICE_OK) 
      return ret;

   if (scopeModel_->IsDeviceAvailable(g_Lamp)) {
      scopeModel_->TLShutter_.SetMaxPosition(1);
      scopeModel_->TLShutter_.SetMinPosition(0);
      scopeModel_->ILShutter_.SetMaxPosition(1);
      scopeModel_->ILShutter_.SetMinPosition(0);
   }

   if (scopeModel_->IsDeviceAvailable(g_IL_Turret)) {
      ret = GetILTurretInfo(device, core);
      if (ret != DEVICE_OK)
         return ret;
   }

   if (scopeModel_->IsDeviceAvailable(g_Revolver)) {
      ret = GetRevolverInfo(device, core);
      if (ret != DEVICE_OK)
         return ret;
   }

   if (scopeModel_->IsDeviceAvailable(g_ZDrive)) {
      ret = GetZDriveInfo(device, core);
      if (ret != DEVICE_OK)
         return ret;
   }


   // Start all events at this point

   // Start event reporting for method changes
   command << g_Master << "003" << " 1 0 0";
   ret = GetAnswer(device, core, command.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;
   command.str("");

   // Start event reporting for TL Shutter:
   if (scopeModel_->IsDeviceAvailable(g_Lamp)) {
      command << g_Lamp << "003" << " 1 1 1 0 1 1";
      ret = GetAnswer(device, core, command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      command.str("");
   }

   // Start event reporting for IL Turret
   if (scopeModel_->IsDeviceAvailable(g_IL_Turret)) {
      command << g_IL_Turret << "003 1";
      ret = GetAnswer(device, core, command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      command.str("");
   }

   // Start event reporting for Objective Turret
   if (scopeModel_->IsDeviceAvailable(g_Revolver)) {
      command << g_Revolver << "003 1 0 1 0 0 0 0 0 1";
      ret = GetAnswer(device, core, command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      command.str("");
   }

   // Start event reporting for Z Drive
   if (scopeModel_->IsDeviceAvailable(g_ZDrive)) {
      command << g_ZDrive << "003 1 0 0 0 0 1 0 0 0";
      ret = GetAnswer(device, core, command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      command.str("");
   }


   // Start monitoring of all messages coming from the microscope
   monitoringThread_ = new LeicaMonitoringThread(device, core, port_, scopeModel_);
   monitoringThread_->Start();


   // Get current positions of all devices.  Let MonitoringThread digest the incoming info
   command << g_Master << "028";
   ret = core.SetSerialCommand(&device, port_.c_str(), command.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;
   command.str("");

   if (scopeModel_->IsDeviceAvailable(g_Lamp)) {
      command << g_Lamp << "033";
      ret = core.SetSerialCommand(&device, port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;
      command.str("");
   }

   if (scopeModel_->IsDeviceAvailable(g_IL_Turret)) {
      command << g_IL_Turret << "023";
      ret = core.SetSerialCommand(&device, port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;
      command.str("");
   }

   if (scopeModel_->IsDeviceAvailable(g_Revolver)) {
      command << g_IL_Turret << "023";
      ret = core.SetSerialCommand(&device, port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;
      command.str("");
   }

   if (scopeModel_->IsDeviceAvailable(g_ZDrive)) {
      command << g_IL_Turret << "023";
      ret = core.SetSerialCommand(&device, port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;
      command.str("");
      // Get current speed of the stage
      command << g_IL_Turret << "033";
      ret = core.SetSerialCommand(&device, port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;
      command.str("");
      // Get current acceleration
      command << g_IL_Turret << "031";
      ret = core.SetSerialCommand(&device, port_.c_str(), command.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;
      command.str("");
   }

   initialized_ = true;
   return DEVICE_OK;
}


/**
 * Utility function that asks a question to the microscope and gets the answer
 */
int LeicaScopeInterface::GetAnswer(MM::Device& device, MM::Core& core, const char* command, std::string& answer)
{
   int ret = core.SetSerialCommand(&device, port_.c_str(), command, "\r");
   if (ret != DEVICE_OK)
      return ret;
   char response[RCV_BUF_LENGTH] = "";
   ret = core.GetSerialAnswer(&device, port_.c_str(), RCV_BUF_LENGTH, response, "\r");
   if (ret != DEVICE_OK)
      return ret;

   answer = response;
   return DEVICE_OK;
}

/**
 * Reads model, version and available devices from the stand
 * Stores directly into the model
 */
int LeicaScopeInterface::GetStandInfo(MM::Device& device, MM::Core& core)
{
   // returns the stand designation and list of IDs of all addressabel IDs
   std::ostringstream os;
   os << g_Master << "001";
   int ret = core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   long unsigned int responseLength = RCV_BUF_LENGTH;
   char response[RCV_BUF_LENGTH] = "";
   ret = core.GetSerialAnswer(&device, port_.c_str(), responseLength, response, "\r");
   if (ret != DEVICE_OK)
      return ret;
   std::stringstream ss(response);
   std::string answer, stand;
   ss >> answer;
   if (answer.compare(os.str()) != 0)
      return ERR_SCOPE_NOT_ACTIVE;
   
   ss >> stand;
   scopeModel_->SetStandType(stand);
   int devId;
   while (ss >> devId) {
      scopeModel_->SetDeviceAvailable(devId);
   }
  
   if (ret != DEVICE_OK)
      return ret;

   // returns the stand's firmware version
   std::ostringstream os2;
   os2 << g_Master << "002";
   ret = core.SetSerialCommand(&device, port_.c_str(), os2.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   ret = core.GetSerialAnswer(&device, port_.c_str(), responseLength, response, "\r");
   if (ret != DEVICE_OK)
      return ret;
   std::stringstream st(response);
   std::string version(response);
   st >> answer;
   if (answer.compare(os2.str()) != 0)
      return ERR_SCOPE_NOT_ACTIVE;
   
   version = version.substr(6);
   scopeModel_->SetStandVersion(version);

   // Get a list with all methods available on this stand
   std::ostringstream os3;
   os3 << g_Master << "026";
   ret = core.SetSerialCommand(&device, port_.c_str(), os3.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;

   ret = core.GetSerialAnswer(&device, port_.c_str(), responseLength, response, "\r");
   if (ret != DEVICE_OK)
      return ret;
   std::stringstream sm(response);
   std::string methods;
   sm >> answer;
   if (answer.compare(os3.str()) != 0)
      return ERR_SCOPE_NOT_ACTIVE;
   sm >> methods;
   for (int i=0; i< 16; i++) {
      if (methods[i] == '1')
         scopeModel_->SetMethodAvailable(15 - i);
   }
   

   return DEVICE_OK;
}

int LeicaScopeInterface::GetILTurretInfo(MM::Device& device, MM::Core& core)
{
   std::ostringstream command;
   std::string answer, token;

   // Get minimum position
   command << g_IL_Turret << "031";
   int ret = GetAnswer(device, core, command.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;
   command.str("");

   std::stringstream ts(answer);
   int minPos;
   ts >> minPos;
   ts >> minPos;
   if ( 0 < minPos && minPos < 10)
   scopeModel_->ILTurret_.SetMinPosition(minPos);
   ts.clear();
   ts.seekg(0,std::ios::beg);
   command.str("");

   // Get maximum position
   command << g_IL_Turret << "032";
   ret = GetAnswer(device, core, command.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;
   command.str("");

   int maxPos;
   std::stringstream tt(answer);
   tt >> maxPos;
   tt >> maxPos;
   if ( 0 < maxPos && maxPos < 10)
      scopeModel_->ILTurret_.SetMaxPosition(maxPos);
   tt.str("");

   // Get name of cube and aperture protection type
   for (int i=minPos; i<=maxPos; i++) {
      command << g_IL_Turret << "027 " << i;
      ret = GetAnswer(device, core, command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      std::stringstream tu(answer);
      tu << answer;
      tu >> token;
      if (token == "78027") {
         int j;
         tu >> j;
         if (i==j) {
            tu >> token;
            std::stringstream name;
            name << j << "-" << token.substr(0,token.size()-1);
            scopeModel_->ILTurret_.cube_[i].name = token.substr(0,token.size()-1);

            if (token.substr(token.size()-1,1) == "1")
               scopeModel_->ILTurret_.cube_[i].apProtection = true;
            else
               scopeModel_->ILTurret_.cube_[i].apProtection = false;
         }
      }
      command.str("");
   }

   // Get methods allowed with each cube
   for (int i=minPos; i<=maxPos; i++) {
      command << g_IL_Turret << "030 " << i;
      ret = GetAnswer(device, core, command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      std::stringstream tw(answer);
      tw >> token;
      if (token == ("78030")) {
         int j;
         tw >> j;
         if (i==j) {
            tw >> token;
            for (int k=0; k< 16; k++) {
               if (token[k] == '1') {
                  scopeModel_->ILTurret_.cube_[i].cubeMethods_[15 - k] = true;
               }
            }
         }
      }
      command.str("");
   }

   return DEVICE_OK;
}

int LeicaScopeInterface::GetRevolverInfo(MM::Device& device, MM::Core& core)
{
   std::ostringstream command;
   std::string answer, token;

   // Get minimum position
   command << g_Revolver << "038";
   int ret = GetAnswer(device, core, command.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;
   command.str("");

   std::stringstream ts(answer);
   int minPos;
   ts >> minPos;
   ts >> minPos;
   if ( 0 < minPos && minPos < 10)
   scopeModel_->ObjectiveTurret_.SetMinPosition(minPos);
   ts.clear();
   ts.seekg(0,std::ios::beg);

   // Get maximum position
   command << g_Revolver << "039";
   ret = GetAnswer(device, core, command.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;
   command.str("");

   int maxPos;
   std::stringstream tt (answer);
   tt >> maxPos;
   tt >> maxPos;
   if ( 0 < maxPos && maxPos < 10)
      scopeModel_->ObjectiveTurret_.SetMaxPosition(maxPos);

   // Get Objective info - magnification
   for (int i=minPos; i<=maxPos; i++) {
      command << g_Revolver << "033 " << i << " 1";
      ret = GetAnswer(device, core, command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      std::stringstream tu(answer);
      tu >> token;
      if (token == "76033") {
         int j;
         tu >> j;
         if (i==j) {
            int par;
            tu >> par;
            if (par==1) {
               int mag = 0;
               tu >> mag;
               scopeModel_->ObjectiveTurret_.objective_[i].magnification_ = mag;
            }
         }
      }
      command.str("");
   }

   // Get Objective info - numerical aperture
   for (int i=minPos; i<=maxPos; i++) {
      command << g_Revolver << "033 " << i << " 2";
      ret = GetAnswer(device, core, command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      std::stringstream tv(answer);
      tv >> token;
      if (token == "76033") {
         int j;
         tv >> j;
         if (i==j) {
            int par;
            tv >> par;
            if (par==2) {
               double na = 0;
               tv >> na;
               scopeModel_->ObjectiveTurret_.objective_[i].NA_ = na;
            }
         }
      }
      command.str("");
   }

   // Get Objective info - article number
   for (int i=minPos; i<=maxPos; i++) {
      command << g_Revolver << "033 " << i << " 3";
      ret = GetAnswer(device, core, command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      std::stringstream tw(answer);
      tw >> token;
      if (token == "76033") {
         int j;
         tw >> j;
         if (i==j) {
            int par;
            tw >> par;
            if (par==3) {
               int articleNumber = 0;
               tw >> articleNumber;
               scopeModel_->ObjectiveTurret_.objective_[i].articleNumber_ = articleNumber;
            }
         }
      }
      command.str("");
   }

   // Get methods allowed with each objective
   for (int i=minPos; i<=maxPos; i++) {
      command << g_Revolver << "033 " << i << " 4";
      ret = GetAnswer(device, core, command.str().c_str(), answer);
      if (ret != DEVICE_OK)
         return ret;
      std::stringstream tv(answer);
      tv >> token;
      if (token == "76033") {
         int j;
         tv >> j;
         if (i==j) {
            tv >> token;
            tv >> token;
            for (int k=0; k< 16; k++) {
               if (token[k] == '1') {
                  scopeModel_->ObjectiveTurret_.objective_[i].methods_[15 - k] = true;
               }
            }
         }
      }
      command.str("");
   }

   return DEVICE_OK;
}

int LeicaScopeInterface::GetZDriveInfo(MM::Device& device, MM::Core& core)
{
   std::ostringstream command;
   std::string answer, token;

   // Get minimum position
   command << g_ZDrive << "028";
   int ret = GetAnswer(device, core, command.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;
   command.str("");

   std::stringstream ts(answer);
   int minPos;
   ts >> minPos;
   ts >> minPos;
   if ( 0 <= minPos)
   scopeModel_->ZDrive_.SetMinPosition(minPos);

   // Get Conversion factor
   command << g_ZDrive << "042";
   ret = GetAnswer(device, core, command.str().c_str(), answer);
   if (ret != DEVICE_OK)
      return ret;
   command.str("");

   std::stringstream tt(answer);
   double factor;
   tt >> factor;
   tt >> factor;
   if ( 0 <= factor)
   scopeModel_->ZDrive_.SetStepSize(factor);

   // Scope does not know about a maximum position
   scopeModel_->ZDrive_.SetMaxPosition(9999999);

   return DEVICE_OK;
}

/**
 * Sends command to the microscope to set requested method
 * Does not listen for answers (should be caught in the monitoringthread)
 */
int LeicaScopeInterface::SetMethod(MM::Device& device, MM::Core& core, int position)
{
   scopeModel_->method_.SetBusy(true);
   std::ostringstream os;
   os << g_Master << "029" << " " << position << " " << 1;
   return core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
}

/**
 * Sets state of transmited light shutter
 */
int LeicaScopeInterface::SetTLShutterPosition(MM::Device& device, MM::Core& core, int position)
{
   scopeModel_->TLShutter_.SetBusy(true);
   std::ostringstream os;
   os << g_Lamp << "032" << " 0" << " " << position;
   return core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
}

/**
 * Sets state of incident light shutter
 */
int LeicaScopeInterface::SetILShutterPosition(MM::Device& device, MM::Core& core, int position)
{
   scopeModel_->ILShutter_.SetBusy(true);
   std::ostringstream os;
   os << g_Lamp << "032" << " 1" << " " << position;
   return core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
}

/**
 * Sets state of reflector Turret
 */
int LeicaScopeInterface::SetILTurretPosition(MM::Device& device, MM::Core& core, int position)
{
   scopeModel_->ILTurret_.SetBusy(true);
   std::ostringstream os;
   os << g_IL_Turret << "022" << " " << position;
   return core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
}

/**
 * Sets state of objective Turret
 */
int LeicaScopeInterface::SetRevolverPosition(MM::Device& device, MM::Core& core, int position)
{
   scopeModel_->ObjectiveTurret_.SetBusy(true);
   std::ostringstream os;
   os << g_Revolver << "022" << " " << position;
   return core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
}

/**
 * Sets ZDrive position
 */
int LeicaScopeInterface::SetZDrivePosition(MM::Device& device, MM::Core& core, int position)
{
   scopeModel_->ZDrive_.SetBusy(true);
   std::ostringstream os;
   os << g_ZDrive << "022" << " " << position;
   return core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
}

/**
 * Sets ZDrive Speed
 */
int LeicaScopeInterface::SetZDriveSpeed(MM::Device& device, MM::Core& core, int speed)
{
   scopeModel_->ZDrive_.SetBusy(true);
   std::ostringstream os;
   os << g_ZDrive << "032" << " " << speed;
   return core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
}

/**
 * Sets ZDrive acceleration
 */
int LeicaScopeInterface::SetZDriveAcceleration(MM::Device& device, MM::Core& core, int acc)
{
   scopeModel_->ZDrive_.SetBusy(true);
   std::ostringstream os;
   os << g_ZDrive << "030" << " " << acc;
   return core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
}

/**
 * Clear contents of serial port 
 */
int LeicaScopeInterface::ClearPort(MM::Device& device, MM::Core& core)
{
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


/*
 * Thread that continuously monitors messages from the Leica scope and inserts them into a model of the microscope
 */
LeicaMonitoringThread::LeicaMonitoringThread(MM::Device& device, MM::Core& core, std::string port, LeicaDMIModel* scopeModel) :
   port_(port),
   device_ (device),
   core_ (core),
   stop_ (true),
   intervalUs_(10000), // check every 10 ms for new messages, 
   scopeModel_(scopeModel)
{
}

LeicaMonitoringThread::~LeicaMonitoringThread()
{
   printf("Destructing monitoringThread\n");
}

void LeicaMonitoringThread::interpretMessage(unsigned char* message)
{

}

//MM_THREAD_FUNC_DECL LeicaMonitoringThread::svc(void *arg) {
int LeicaMonitoringThread::svc() {
   //LeicaMonitoringThread* thd = (LeicaMonitoringThread*) arg;

   printf ("Starting MonitoringThread\n");

   unsigned long dataLength = 0;
   unsigned long charsRead = 0;
   unsigned long charsRemaining = 0;
   char rcvBuf[LeicaScopeInterface::RCV_BUF_LENGTH];
   char buffer[LeicaScopeInterface::RCV_BUF_LENGTH];
   char message[LeicaScopeInterface::RCV_BUF_LENGTH];
   memset(rcvBuf, 0, LeicaScopeInterface::RCV_BUF_LENGTH);
   memset(message, 0, LeicaScopeInterface::RCV_BUF_LENGTH);

   while (!stop_) 
   {
      do { 
         dataLength = LeicaScopeInterface::RCV_BUF_LENGTH - charsRemaining;
         // rcvBuf[0] = 0;
         // TODO: listen to incoming characters directly and break when it is "\r"
         // int ret = core_.GetSerialAnswer(&(device_), port_.c_str(), LeicaScopeInterface::RCV_BUF_LENGTH, rcvBuf, "\r"); 
         int ret = core_.ReadFromSerial(&device_, port_.c_str(), (unsigned char*) (rcvBuf + strlen(rcvBuf)), dataLength, charsRead);
         memset(message, 0, strlen(message));
         if (ret == DEVICE_OK && (strlen(rcvBuf) > 4) && !stop_) {
            const char* eoln = strstr(rcvBuf, "\r");
            if (eoln != 0) {
               strncpy (message, rcvBuf, eoln - rcvBuf);
               printf ("Message: %s\n", message);
               core_.LogMessage (&device_, message, true);
               memmove(rcvBuf, eoln + 1, LeicaScopeInterface::RCV_BUF_LENGTH - (eoln - rcvBuf) -1);
            }
         } else {
            CDeviceUtils::SleepMs(intervalUs_/1000);
         }
         if (strlen(message) >= 5 && !stop_) {
            // Analyze incoming messages.  Tokenize and take action based on first toke
            std::stringstream os(message);
            std::string command;
            os >> command;
            // If first char is '$', then this is an event.  Treat as all other incoming messages:
            if (command[0] == '$')
               command = command.substr(1, command.length() - 1);

            int deviceId = atoi(command.substr(0,2).c_str());
            int commandId = atoi(command.substr(2,3).c_str());
            switch (deviceId) {
               case (g_Master) :
                   switch (commandId) {
                      // Set Method command, signals completion of command sends
                      case (29) : 
                         scopeModel_->method_.SetBusy(false);
                         break;
                      // I am unsure if this already signals the end of the command
                      case (28):
                         int pos;
                         os >> pos;
                         scopeModel_->method_.SetPosition(pos);
                         scopeModel_->method_.SetBusy(false);
                         break;
                   }
                   break;
                case (g_Lamp) :
                   switch (commandId) {
                      case (32) :
                         scopeModel_->TLShutter_.SetBusy(false);
                         scopeModel_->ILShutter_.SetBusy(false);
                         break;
                      case (33) :
                         int posTL, posIL;
                         os >> posTL >> posIL;
                         scopeModel_->TLShutter_.SetPosition(posTL);
                         scopeModel_->ILShutter_.SetPosition(posIL);
                         scopeModel_->TLShutter_.SetBusy(false);
                         scopeModel_->ILShutter_.SetBusy(false);
                         break;
                   }
                   break;
                case (g_IL_Turret) :
                   switch (commandId) {
                      case(22) :
                         scopeModel_->ILTurret_.SetBusy(false);
                         break;
                      case (23) :
                         int pos;
                         os >> pos;
                         scopeModel_->ILTurret_.SetPosition(pos);
                         scopeModel_->ILTurret_.SetBusy(false);
                         break;
                      case (122) :  // No cube in this position, or not allowed with this method
                         // TODO: Set an error?
                         scopeModel_->ILTurret_.SetBusy(false);
                         break;
                      case (322) :  // dark flap was not automatically opened
                         // TODO: open the dark flap
                         scopeModel_->ILTurret_.SetBusy(false);
                         break;
                       default : // TODO: error handling
                         break;
                   }
                   break;
               case (g_Revolver) :
                   switch (commandId) {
                      case (4) : // Status
                         {
                            std::string status[4];
                            for (int i=0; i < 4; i++) 
                               os >> status[i];
                            if (status[1]=="1")
                               scopeModel_->ObjectiveTurret_.SetBusy(true);
                            else
                               scopeModel_->ObjectiveTurret_.SetBusy(false);
                         }
                         break;
                      case (23) : // Position
                         {
                            int pos;
                            os >> pos;
                            scopeModel_->ObjectiveTurret_.SetPosition(pos);
                            scopeModel_->ObjectiveTurret_.SetBusy(false);
                            break;
                         }
                      case (33) : // Use new parameter reporting to get the new position
                         {
                            int pos;
                            os >> pos;
                            scopeModel_->ObjectiveTurret_.SetPosition(pos);
                            scopeModel_->ObjectiveTurret_.SetBusy(false);
                         }
                         break;
                   }
                   break;
               case (g_ZDrive) :
                   switch (commandId) {
                      case (4) : // Status
                         {
                            std::string status[5];
                            for (int i=0; i < 5; i++) 
                               os >> status[i];
                            if (status[0]=="1")
                               scopeModel_->ObjectiveTurret_.SetBusy(true);
                            else
                               scopeModel_->ObjectiveTurret_.SetBusy(false);
                         }
                         break;
                      case (23) : // Position
                         {
                            int pos;
                            os >> pos;
                            scopeModel_->ZDrive_.SetPosition(pos);
                            scopeModel_->ZDrive_.SetBusy(false);
                            break;
                         }
                      case (31) : // acceleration
                         {
                            int acc;
                            os >> acc;
                            scopeModel_->ZDrive_.SetRamp(acc);
                            break;
                         }
                      case (33) : // speed
                         {
                            int speed;
                            os >> speed;
                            scopeModel_->ZDrive_.SetSpeed(speed);
                            break;
                         }
                   }

            }
         }
      } while ((strlen(rcvBuf) > 0) && (!stop_)); 

   }

   return DEVICE_OK;
}

void LeicaMonitoringThread::Start()
{
   stop_ = false;
   activate();
}
