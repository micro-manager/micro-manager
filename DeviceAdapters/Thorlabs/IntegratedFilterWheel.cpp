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

/*
To get position  for example

MGMSG_MOT_REQ_POSCOUNTER (0x0411)

Tx 11,04,01,00,50,01

First 6 bytes are 6 byte header

First 2 are msg ident 11 04  (0x0411)

Next 2 are channel ID (01 00) 1

Next 2 are src dest bytes  (50 dest, 01 src)              

 

Response for example will be

MGMSG_MOT_GET_POSCOUNTER  (0x0412)

Tx 12,04,06,00,81,50,01,00,00,0A,00,00

First 6 bytes are 6 byte header

First 2 are msg ident 12 04  (0x0412)

Next 2 are number of  bytes in appended structure (06 00) 6

Next 2 are sr dest bytes (81 50) (note dest 81 is Ored with 0x80 to indicate appended packet

 

Next 6 are appended packet

First 2 are channel ident ( 01 00) 1 

Next 4 are current position in absolute stepper micro-steps (00 0A 00 00) 0xA00 (angle of 0.52 degrees at output for example)

 

and relate to the output angle of the filter wheel by the gear ratio of stepper gear to

filter wheel gear which is 4.333….. -1. So for 1 turn of the motor which represents

409600 stepper micro-steps hence relates to 360 degrees at the motor. So 1microstep is

360/409600= approx. 0.0008789 degrees.

To move the actual filter wheel by 45 degrees say (an 8 position filter wheel), then 45*4.333..=195 degrees

This is equal to approx. 221867 micro-steps to go from 1 filter position to the next.

*/

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

const unsigned char getParamsCmd[] =  {      0x15, // cmd low byte
                                             0x00, // cmd high byte
                                             0x20, // num bytes low
                                             0x00, // num bytes hi
                                             0x50, // 
                                             0x01
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
      char buf[MM::MaxStrLength];
      // TODO: format command
	   
	   SendSerialCommand(port_.c_str(),buf,"\r");
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
