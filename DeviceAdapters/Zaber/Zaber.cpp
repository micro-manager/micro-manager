///////////////////////////////////////////////////////////////////////////////
// FILE:          Zaber.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Zaber A-MCB2 Controller Driver
//                
// AUTHOR:        David Goosen
//                
// COPYRIGHT:     Zaber Technologies, 2013
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#ifdef WIN32
   #define snprintf _snprintf 
   #pragma warning(disable: 4355)
#endif

#include "Zaber.h"
#include "XYStage.h"
#include <ModuleInterface.h>
#include <sstream>
#include <string>

using namespace std;


//////////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
//////////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(XYStageName, MM::XYStageDevice, "Zaber XY Stage");
}                                                            

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
   MM::Device* dev = NULL;

   if ((deviceName != NULL) && (strcmp(deviceName, XYStageName) == 0))
   {
      dev = new XYStage();
   }
   return dev;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// ZaberBase (convenience parent class)
///////////////////////////////////////////////////////////////////////////////

// CONSTRUCTOR
ZaberBase::ZaberBase(MM::Device *device) :
   initialized_(false),
   peripheralID1_(0),
   peripheralID2_(0),
   port_("Undefined")
{
   device_ = static_cast<ZaberDeviceBase *>(device);
}

// DESTRUCTOR
ZaberBase::~ZaberBase()
{
}

// COMMUNICATION "clear buffer" utility function:
int ZaberBase::ClearPort(void)
{
   // Clear contents of serial port
   const int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while ((int) read == bufSize)
   {
      ret = device_->ReadFromComPort(port_.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;      
} 

// COMMUNICATION "send" utility function:
int ZaberBase::SendCommand(const char *command) const
{
   const char* g_TxTerm = "\r"; //Message footer. Required by Zaber ASCII protocol.
   int ret;

   std::string base_command = "";
   base_command += command;
   // send command
   ret = device_->SendSerialCommand(port_.c_str(), base_command.c_str(), g_TxTerm);

   return ret;
}

// COMMUNICATION "send & receive" utility function:
int ZaberBase::QueryCommand(const char *command, std::string &answer) const
{
   const char* g_RxTerm = "\r\n"; //Message footer. Required by Zaber ASCII protocol.

   // send command
   int ret;
   if((ret = SendCommand(command)) == DEVICE_OK)
   {
      // block/wait for acknowledge (or until we time out)
      ret = device_->GetSerialAnswer(port_.c_str(), g_RxTerm, answer);
   }
   return ret;
}
// Check Device Status
int ZaberBase::CheckDeviceStatus(void)
{
   int ret;
   if ((ret = ClearPort()) == DEVICE_OK)
   {
      //diasable alert messages
      string resp;
      if ((ret = QueryCommand("/set comm.alert 0",resp)) == DEVICE_OK)
      {
         if (resp.length() >= 1)
         {  
            // Get device ID
            ret = QueryCommand("/get deviceid",resp);
            if (ret == DEVICE_OK)
            {
               if (resp.length() >= 1)
               {
                  if (resp.find("30221") != string::npos)
                  {
                     // Get periperal IDs of device 1(y-axis) and device 2(x-axis)
                     string respID;
                     ret = QueryCommand("/get peripheralid",respID);
                     if (ret == DEVICE_OK)
                     {
                        if (resp.length() >= 1)
                        {
                           string IDString1=respID.substr(17,respID.find(" ", 17)-17);
                           string IDString2=respID.substr(respID.find(" ", 17)+1,string::npos);
                           stringstream(IDString1) >> peripheralID1_;
                           stringstream(IDString2) >> peripheralID2_;

                           initialized_ = true;
                        }
                        else
                        {
                           ret = DEVICE_NOT_CONNECTED;   //Invalid response
                        }
                     }
                  }
                  else
                  {
                     ret = DEVICE_NOT_CONNECTED;      //Not a A-MCB2 controller
                  }
               }
               else
               {
                  ret = DEVICE_NOT_CONNECTED;         //Invalid response
               }
            }
         }
         else
         {
            ret = DEVICE_NOT_CONNECTED;               //Invalid response
         }
      }
   }
   return ret;
}
