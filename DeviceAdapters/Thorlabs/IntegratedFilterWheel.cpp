///////////////////////////////////////////////////////////////////////////////
// FILE:          IntegratedFilterWheel.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs device adapters: Integrated Filter Wheel
//
// COPYRIGHT:     Thorlabs, 2011
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
// AUTHOR:        Nenad Amodaj, http://nenad.amodaj.com, 2011
//


#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Thorlabs.h"
#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>
using namespace std;


///////////
// commands
///////////
const unsigned char getPosCmd[] =  {         0x11, // cmd low byte
                                             0x04, // cmd high byte
                                             0x01, // channel id low
                                             0x00, // channel id hi
                                             0x50, // dest low
                                             0x01, // dest hi
                                          };             


const unsigned char getPosRsp[] = {          0x12, // cmd low byte
                                             0x04, // cmd high byte
                                             0x06, // num bytes low
                                             0x00, // num bytes hi
                                             0x81, // 
                                             0x50, // 
                                             0x01, // channel low
                                             0x00, // channel hi
                                             0x00, // position low byte
                                             0x00,  // position  
                                             0x00, // position
                                             0x00  // position high byte
                                          };             

const unsigned char setPosCmd[] =  {         0x53, // cmd low byte
                                             0x04, // cmd high byte
                                             0x06, // nun bytes low
                                             0x00, // num bytes hi
                                             0x81, // 
                                             0x50, //
                                             0x01, // ch low
                                             0x00, // ch hi
                                             0x00, // position low byte
                                             0x00,  // position  
                                             0x00, // position
                                             0x00  // position high byte
                                          };             

const unsigned char reqParamsCmd[] =  {      0x15, // cmd low byte
                                             0x00, // cmd high byte
                                             0x20, // num bytes low
                                             0x00, // num bytes hi
                                             0x50, // 
                                             0x01
                                          };             

const unsigned char getParamsResp[] = {      0x16, // cmd low
                                             0x00, //
                                             0x28, // 
                                             0x00, // 
                                             0x81, // 
                                             0x50, // cmd hi

                                             0x20, // num pos low
                                             0x00, // 
                                             0x00, //
                                             0x00, // num pos hi

                                             0x01, // ch ID low
                                             0x00, //   
                                             0x00, // 
                                             0x00,  // ch ID hi

                                             0x08, // num pos low
                                             0x00,
                                             0x00,
                                             0x00 // num pos hi
                                          };

using namespace std;

extern const char* g_WheelDeviceName;

IntegratedFilterWheel::IntegratedFilterWheel() : 
   numPos_(6), 
   busy_(false), 
   initialized_(false), 
   changedTime_(0.0),
   position_(0),
   port_("")
{
   InitializeDefaultErrorMessages();

   //Com port
   CPropertyAction* pAct = new CPropertyAction (this, &IntegratedFilterWheel::OnCOMPort);
   CreateProperty(MM::g_Keyword_Port, "", MM::String, false, pAct, true);

   EnableDelay();
}

IntegratedFilterWheel::~IntegratedFilterWheel()
{
   Shutdown();
}

void IntegratedFilterWheel::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_WheelDeviceName);
}


int IntegratedFilterWheel::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_WheelDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Integrated filter wheel", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Busy timer
   changedTime_ = GetCurrentMMTime();   

   // create default positions and labels
   char buf[MM::MaxStrLength];
   for (long i=0; i<numPos_; i++)
   {
      snprintf(buf, MM::MaxStrLength, "Position-%ld", i + 1);
      SetPositionLabel(i, buf);
   }

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &IntegratedFilterWheel::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Label
   // -----
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;


   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

bool IntegratedFilterWheel::Busy()
{
   MM::MMTime interval = GetCurrentMMTime() - changedTime_;
   MM::MMTime delay(GetDelayMs()*1000.0);
   if (interval < delay)
      return true;
   else
      return false;
}


int IntegratedFilterWheel::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int IntegratedFilterWheel::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(position_);
   }
   else if (eAct == MM::AfterSet)
   {
      // busy timer
      changedTime_ = GetCurrentMMTime();

      long pos;
      pProp->Get(pos);
      if (pos >= numPos_ || pos < 0)
      {
         pProp->Set(position_); // revert
         return ERR_INVALID_POSITION;
      }
      
      int ret = SetPosition(pos);
      position_ = pos;
   }

   return DEVICE_OK;
}

int IntegratedFilterWheel::OnCOMPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
         pProp->Set(port_.c_str());
 
      pProp->Get(port_);                                                     
   }                                                                         
                                                                             
   return DEVICE_OK;                                                         
}  
