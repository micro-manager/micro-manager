///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIClocked.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI clocked device adapter (filter slider, turret)
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
#include "ASIClocked.h"
#include "ASIHub.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <iostream>
#include <vector>
#include <string>

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// CClocked
// this is a superclass for any clocked devices (except filterwheels which use a different command set)
//   including turrets and filter sliders
//
CClocked::CClocked(const char* name) :
   ASIPeripheralBase< ::CStateDeviceBase, CClocked >(name),
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

int CClocked::Initialize()
{
   // call generic Initialize first, this gets hub
   RETURN_ON_MM_ERROR( PeripheralInitialize() );

   ostringstream command;

   // serial query to find out how many positions we have
   command.str("");
   command << "SU " << axisLetter_ << "?";
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(numPositions_) );
   command.str("");
   command << numPositions_;
   CreateProperty(g_NumPositionsPropertyName, command.str().c_str(), MM::Integer, true);

   // add allowed values to the special state/position property for state devices
   CPropertyAction* pAct = new CPropertyAction (this, &CClocked::OnState);
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
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(curPosition_) );
   curPosition_--;  // make it 0-indexed

   // let calling class decide if initialized_ should be set
   return DEVICE_OK;
}

bool CClocked::Busy()
{
   ostringstream command; command.str("");
   if (firmwareVersion_ > 2.7) // can use more accurate RS <axis>?
   {
      command << "RS " << axisLetter_ << "?";
      if (hub_->QueryCommandVerify(command.str(),":A") != DEVICE_OK)  // say we aren't busy if we can't communicate
         return false;
      char c;
      if (hub_->GetAnswerCharAtPosition3(c) != DEVICE_OK)
         return false;
      return (c == 'B');
   }
   else  // use LSB of the status byte as approximate status, not quite equivalent
   {
      command << "RS " << axisLetter_;
      if (hub_->QueryCommandVerify(command.str(),":A") != DEVICE_OK)  // say we aren't busy if we can't communicate
         return false;
      unsigned int i;
      if (hub_->ParseAnswerAfterPosition2(i) != DEVICE_OK)  // say we aren't busy if we can't parse
         return false;
      return (i & (unsigned int)BIT0);  // mask everything but LSB
   }
}

int CClocked::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
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

int CClocked::OnLabel(MM::PropertyBase* pProp, MM::ActionType eAct)
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


///////////////////////////////////////////////////////////////////////////////
// CFSlider
// mostly just inherits from CClocked, except description
//
CFSlider::CFSlider(const char* name) :
      CClocked(name)
{

}

int CFSlider::Initialize()
{
   RETURN_ON_MM_ERROR( CClocked::Initialize() );

   ostringstream command;

   // create MM description; this doesn't work during hardware configuration wizard but will work afterwards
   command.str("");
   command << g_FSliderDeviceDescription << " Axis=" << axisLetter_ << " HexAddr=" << addressString_;
   CreateProperty(MM::g_Keyword_Description, command.str().c_str(), MM::String, true);

   initialized_ = true;
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// CTurret
// mostly just inherits from CClocked, except description
//
CTurret::CTurret(const char* name) :
      CClocked(name)
{

}

int CTurret::Initialize()
{
   RETURN_ON_MM_ERROR( CClocked::Initialize() );

   ostringstream command;

   // create MM description; this doesn't work during hardware configuration wizard but will work afterwards
   command.str("");
   command << g_TurretDeviceDescription << " Axis=" << axisLetter_ << " HexAddr=" << addressString_;
   CreateProperty(MM::g_Keyword_Description, command.str().c_str(), MM::String, true);

   initialized_ = true;
   return DEVICE_OK;
}





