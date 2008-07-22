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
   os << "Initializing Leica Microscope";
   core.LogMessage (&device, os.str().c_str(), false);
   os.str("");
   // empty the Rx serial buffer before sending commands
   ClearRcvBuf();
   ClearPort(device, core);

   std::string version;
   // Get info about stand, firmware and available devices and store in the model
   int ret = GetStandInfo(device, core);
   if (ret != DEVICE_OK) 
      return ret;

   if (scopeModel_->IsDeviceAvailable(g_Dark_Flap_Tl)) {
      scopeModel_->TLShutter_.SetMaxPosition(1);
      scopeModel_->TLShutter_.SetMinPosition(0);
   }

   // TODO: get info about all devices that we are interested in (make sure they are available)

   // Start all events at this point
   std::ostringstream command;

   // Start event reporting for method changes
   command << g_Master << "003" << " 1 0 0";
   ret = core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;
   command.str("");

   // Start event reporting for TL Shutter:
   if (scopeModel_->IsDeviceAvailable(g_Dark_Flap_Tl)) {
      command << g_Dark_Flap_Tl << "003" << " 1";
      ret = core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;
      command.str("");
   }


   // Start monitoring of all messages coming from the microscope
   monitoringThread_ = new LeicaMonitoringThread(device, core, port_, scopeModel_);
   monitoringThread_->Start();


   // Get current positions of all devices.  Let MonitoringThread digest the incoming info
   command << g_Master << "028";
   ret = core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
   if (ret != DEVICE_OK)
      return ret;
   command.str("");

   if (scopeModel_->IsDeviceAvailable(g_Dark_Flap_Tl)) {
      command << g_Dark_Flap_Tl << "023";
      ret = core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
      if (ret != DEVICE_OK)
         return ret;
      command.str("");
   }


   initialized_ = true;
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
   std::string version;
   st >> answer;
   if (answer.compare(os2.str()) != 0)
      return ERR_SCOPE_NOT_ACTIVE;
   
   st >> version;
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


/**
 * Sends command to the microscope to set requested method
 * Does not listen for answers (should be causght in the monitoringthread
 */
int LeicaScopeInterface::SetMethod(MM::Device& device, MM::Core& core, int position)
{
   std::ostringstream os;
   os << g_Master << "029" << " " << position << " " << 1;
   return core.SetSerialCommand(&device, port_.c_str(), os.str().c_str(), "\r");
}

int LeicaScopeInterface::ClearPort(MM::Device& device, MM::Core& core)
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


/*
 * Thread that continuously monitors messages from the Leica scope and inserts them into a model of the microscope
 */
LeicaMonitoringThread::LeicaMonitoringThread(MM::Device& device, MM::Core& core, std::string port, LeicaDMIModel* scopeModel) :
   port_(port),
   device_ (device),
   core_ (core),
   stop_ (true),
   intervalUs_(5000), // check every 5 ms for new messages, 
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

   char rcvBuf[LeicaScopeInterface::RCV_BUF_LENGTH];
   memset(rcvBuf, 0, LeicaScopeInterface::RCV_BUF_LENGTH);

   while (!stop_) 
   {
      do { 
         rcvBuf[0] = 0;
         int ret = core_.GetSerialAnswer(&(device_), port_.c_str(), LeicaScopeInterface::RCV_BUF_LENGTH, rcvBuf, "\r"); 
         if (ret != DEVICE_OK && ret != ret != DEVICE_SERIAL_TIMEOUT) {
            std::ostringstream oss;
            oss << "Monitoring Thread: ERROR while reading from serial port, error code: " << ret;
            core_.LogMessage(&(device_), oss.str().c_str(), false);
         } else if (strlen(rcvBuf) >= 5) {
            // Analyze incoming messages.  Tokenize and take action based on first toke
            std::stringstream os(rcvBuf);
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
                      // Set Method command, set stand to busy
                      case (29) : 
                         scopeModel_->method_.SetBusy(true);
                         break;
                      case (28):
                         int pos;
                         os >> pos;
                         scopeModel_->method_.SetPosition(pos);
                         scopeModel_->method_.SetBusy(false);
                         break;
                   }
                   break;
                case (g_Dark_Flap_Tl) :
                   switch (commandId) {
                      case (22) :
                         scopeModel_->TLShutter_.SetBusy(true);
                         break;
                      case (23) :
                         int pos;
                         os >> pos;
                         scopeModel_->TLShutter_.SetPosition(pos);
                         scopeModel_->TLShutter_.SetBusy(false);
                         break;
                   }
                   break;
            }
         }
      } while ((strlen(rcvBuf) > 0) && (!stop_)); 

       CDeviceUtils::SleepMs(intervalUs_/1000);
   }
   printf("Monitoring thread finished\n");
   return 0;
}

void LeicaMonitoringThread::Start()
{
   stop_ = false;
   activate();
}
