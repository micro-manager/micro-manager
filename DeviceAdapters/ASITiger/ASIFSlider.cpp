///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIFSlider.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI filter slider adapter
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
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 09/2013
//
// BASED ON:      ASIStage.cpp and others
//

#ifdef WIN32
#define snprintf _snprintf 
#pragma warning(disable: 4355)
#endif

#include "ASITiger.h"
#include "ASIFSlider.h"
#include "ASIHub.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <iostream>
#include <vector>
#include <string>

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// CFSlider
//
CFSlider::CFSlider(const char* name) :
   ASIDevice(this, name),
   numPositions_(0),  // will read actual number of positions
   curPosition_(0),   // will read actual position
   axisLetter_(g_EmptyAxisLetterStr)
{
   if (IsExtendedName(name))  // only set up these properties if we have the required information in the name
   {
      axisLetter_ = GetAxisLetterFromExtName(name);
      CreateProperty(g_AxisLetterPropertyName, axisLetter_.c_str(), MM::String, true);
   }
}

CFSlider::~CFSlider()
{
   Shutdown();
}

int CFSlider::Initialize()
{
   // call generic Initialize first, this gets hub
   RETURN_ON_MM_ERROR( ASIDevice::Initialize() );

   ostringstream command;

   // create MM description; this doesn't work during hardware configuration wizard but will work afterwards
   command.str("");
   command << g_FSliderDeviceDescription << " Axis=" << axisLetter_ << " HexAddr=" << addressString_;
   CreateProperty(MM::g_Keyword_Description, command.str().c_str(), MM::String, true);

   // serial query to find out how many positions we have
   command.str("");
   command << "SU " << axisLetter_ << "?";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   long ans = (long) hub_->ParseAnswerAfterEquals();
   numPositions_ = (unsigned int) ans;
   command.str("");
   command << numPositions_;
   CreateProperty("NumPositions", command.str().c_str(), MM::Integer, true);

   // add allowed values to the special state/position property for state devices
   CPropertyAction* pAct = new CPropertyAction (this, &CFSlider::OnState);
   CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   for (unsigned int i=0; i<numPositions_; i++)
   {
      command.str("");
      command << i;
      AddAllowedValue(MM::g_Keyword_State, command.str().c_str());
   }

   // add default labels for the states
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   for (unsigned int i=0; i<numPositions_; i++)
   {
      command.str("");
      command << "Position-" << i+1;
      SetPositionLabel(i, command.str().c_str());
   }

   // get current position and cache in curPosition_
   command.str("");
   command << "W " << axisLetter_;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   curPosition_ = (unsigned int) (hub_->ParseAnswerAfterPosition(2) - 1);

   initialized_ = true;
   return DEVICE_OK;
}

bool CFSlider::Busy()
{
   // because we're asking for just this device we can't use controller-wide status
   // instead use RS command and parse reply which is given as a decimal of the byte code
   // bit2 of each reply (3rd from LSB) is 1 if motor is on
   // => look at whether floor(result/4) is even (bit2=0) or odd (bit2=1)
   // TODO fix firmware so status command works with addressing
   ostringstream command; command.str("");
   command << "RS " << axisLetter_;
   ret_ = hub_->QueryCommandVerify(command.str(),":A");
   if (ret_ != DEVICE_OK)  // say we aren't busy if we can't communicate
      return false;
   int i = (int) (floor(hub_->ParseAnswerAfterPosition(2))/4);
   return (i%2 == 1);
}

int CFSlider::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)curPosition_);
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      command << "M " << axisLetter_ << "=" << pos+1;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      curPosition_ = pos;
   }
   return DEVICE_OK;
}

int CFSlider::OnLabel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      char buf[MM::MaxStrLength];
      RETURN_ON_MM_ERROR ( GetPosition(buf) );
      pProp->Set(buf);
   }
   else if (eAct == MM::AfterSet)
   {
      string buf;
      pProp->Get(buf);
      RETURN_ON_MM_ERROR ( SetPosition(buf.c_str()) );
   }
   return DEVICE_OK;
}


