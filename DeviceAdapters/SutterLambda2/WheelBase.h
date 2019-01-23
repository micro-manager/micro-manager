///////////////////////////////////////////////////////////////////////////////
// FILE:          WheelBase.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Sutter Lambda controller adapter
// COPYRIGHT:     University of California, San Francisco, 2006
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 10/26/2005
//                Nico Stuurman, Oct. 2010
//				  Nick Anthony, Oct. 2018
//
// CVS:           $Id$
//

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "SutterHub.h" 

#define ERR_UNKNOWN_POSITION         10002
#define ERR_INVALID_SPEED            10003
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_SET_POSITION_FAILED      10005

template <class U>
class WheelBase : public CStateDeviceBase<U>
{
public:
   WheelBase(const char* name, unsigned id, bool evenPositionsOnly, const char* description);
   ~WheelBase();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   unsigned long GetNumberOfPositions()const {return evenPositionsOnly_? numPos_/2 : numPos_;}

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBusy(MM::PropertyBase* pProp, MM::ActionType eAct);


protected:
	unsigned speed_;
	SutterHub* hub_;
	void initializeDelayTimer();
private:
   bool SetWheelPosition(unsigned pos);
   bool initialized_;
   unsigned numPos_;
   bool evenPositionsOnly_;
   const char* description_;
   const unsigned id_;
   std::string name_;
   unsigned curPos_;
   bool open_;  
   MM::MMTime delay_;
   MM::MMTime changedTime_;
   WheelBase& operator=(WheelBase& /*rhs*/) {assert(false); return *this;}
};

template<class U>
WheelBase<U>::WheelBase(const char* name, unsigned id, bool evenPositionsOnly, const char* description) :
	initialized_(false),
	numPos_(10),
	id_(id),
	name_(name),
	curPos_(0),
	speed_(3),
	evenPositionsOnly_(evenPositionsOnly),
	description_(description)
{
	assert(id == 0 || id == 1 || id == 2);
	assert(strlen(name) < (unsigned int)MM::MaxStrLength);

	this->InitializeDefaultErrorMessages();

	// add custom MTB messages
	this->SetErrorText(ERR_UNKNOWN_POSITION, "Position out of range");
	this->SetErrorText(ERR_PORT_CHANGE_FORBIDDEN, "You can't change the port after device has been initialized.");
	this->SetErrorText(ERR_INVALID_SPEED, "Invalid speed setting. You must enter a number between 0 to 7.");
	this->SetErrorText(ERR_SET_POSITION_FAILED, "Set position failed.");

	// create pre-initialization properties
	// ------------------------------------

	// Name
	this->CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

	// Description
	this->CreateProperty(MM::g_Keyword_Description, description_, MM::String, true);

	this->EnableDelay();
	this->UpdateStatus();
}

template<class U>
WheelBase<U>::~WheelBase()
{
	Shutdown();
}
template<class U>
void WheelBase<U>::GetName(char* name) const
{
	assert(name_.length() < CDeviceUtils::GetMaxStringLength());
	CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


template<class U>
bool WheelBase<U>::Busy() {
	if (delay_.getMsec() > 0.0) {
		MM::MMTime interval = this->GetCurrentMMTime() - changedTime_;
		if (interval.getMsec() < delay_.getMsec()) {
			return true;
		} else {
			return false;
		}
	} else {
		return hub_->Busy();
	}
}

template<class U>
int WheelBase<U>::Initialize() {
	hub_ = dynamic_cast<SutterHub*>(this->GetParentHub());
	// set property list
	// -----------------

	// Gate Closed Position
	int ret = this->CreateProperty(MM::g_Keyword_Closed_Position, "", MM::String, false);

	// State
	// -----
   MM::Action<U>* pAct = new MM::Action<U>(dynamic_cast<U *const>(this), &U::OnState);
	ret = this->CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
	if (ret != DEVICE_OK) {return ret;}
	this->SetPropertyLimits(MM::g_Keyword_State, 0, evenPositionsOnly_? numPos_/2-1:numPos_-1);

	// Speed
	// -----
	pAct = new MM::Action<U>(dynamic_cast<U *const>(this), &WheelBase::OnSpeed);
	ret = this->CreateProperty("State Change Speed", "3", MM::Integer, false, pAct);
	if (ret != DEVICE_OK) {return ret;}
	for (int i = 0; i < 8; i++) {
		std::ostringstream os;
		os << i;
		this->AddAllowedValue("State Change Speed", os.str().c_str());
	}

	// Label
	// -----
	pAct = new MM::Action<U>(dynamic_cast<U *const>(this), &CStateDeviceBase<U>::OnLabel);
	ret = this->CreateProperty(MM::g_Keyword_Label, "Undefined", MM::String, false, pAct);
	if (ret != DEVICE_OK) {return ret;}

	// Delay
	// -----
	pAct = new MM::Action<U>(dynamic_cast<U *const>(this), &WheelBase::OnDelay);
	ret = this->CreateProperty(MM::g_Keyword_Delay, "0.0", MM::Float, false, pAct);
	if (ret != DEVICE_OK) {return ret;}
	this->SetPropertyLimits(MM::g_Keyword_Delay, 0.0, 500.0);



	// Busy
	// -----
	// NOTE: in the lack of better status checking scheme,
	// this is a kludge to allow resetting the busy flag.  
	pAct = new MM::Action<U>(dynamic_cast<U *const>(this), &WheelBase::OnBusy);
	ret = this->CreateProperty("Busy", "0", MM::Integer, false, pAct);
	if (ret != DEVICE_OK) {return ret;}

	// create default positions and labels
	const int bufSize = 1024;
	char buf[bufSize];
	int _ = evenPositionsOnly_ ? 2 : 1;
	int position=0;
	for (unsigned i = 0; i < numPos_; i+=_)
	{
		snprintf(buf, bufSize, "Filter-%d", i);
		ret = this->SetPositionLabel(position, buf);
		if (ret != DEVICE_OK){return ret;}
		// Also give values for Closed-Position state while we are at it
		snprintf(buf, bufSize, "%d", i);
		this->AddAllowedValue(MM::g_Keyword_Closed_Position, buf);
		position++;
	}

	this->GetGateOpen(open_);

	ret = this->UpdateStatus();
	if (ret != DEVICE_OK)
		return ret;

	initialized_ = true;
	return DEVICE_OK;
}

template<class U>
int WheelBase<U>::Shutdown()
{
	initialized_ = false;
	return DEVICE_OK;
}

/**
* Sends a command to Lambda through the serial port.
* The command format is one byte encoded as follows:
* bits 0-3 : position
* bits 4-6 : speed
* bit  7   : wheel id
*/

template<class U>
bool WheelBase<U>::SetWheelPosition(unsigned pos)
{
	// build the respective command for the wheel
	unsigned char upos = (unsigned char)pos;

	std::vector<unsigned char> command;

	// the Lambda 10-2, at least, SOMETIMES echos only the pos, not the command and speed - really confusing!!!
	std::vector<unsigned char> alternateEcho;

	switch (id_)
	{
	case 0:
		command.push_back((unsigned char)(speed_ * 16 + pos));
		alternateEcho.push_back(upos);
		break;
	case 1:
		command.push_back((unsigned char)(128 + speed_ * 16 + pos));
		alternateEcho.push_back(upos);
		break;
	case 2:
		alternateEcho.push_back(upos);    // TODO!!!  G.O.K. what they really echo...
		command.push_back((unsigned char)252); // used for filter C
		command.push_back((unsigned char)(speed_ * 16 + pos));
		break;
	default:
		break;
	}

	int ret2 = hub_->SetCommand(command, alternateEcho);
	initializeDelayTimer();
	return (DEVICE_OK == ret2) ? true : false;

}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
template<class U>
int WheelBase<U>::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)curPos_);
	}
	else if (eAct == MM::AfterSet)
	{
		bool gateOpen;
		this->GetGateOpen(gateOpen);
		long pos;
		pProp->Get(pos);

		if (pos >= (long)numPos_ || pos < 0)
		{
			pProp->Set((long)curPos_); // revert
			return ERR_UNKNOWN_POSITION;
		}

		// For efficiency, the previous state and gateOpen position is cached
		if (gateOpen) {
			// check if we are already in that state
			if (((unsigned)pos == curPos_) && open_)
				return DEVICE_OK;

			// try to apply the value
			if (!SetWheelPosition(pos))
			{
				pProp->Set((long)curPos_); // revert
				return ERR_SET_POSITION_FAILED;
			}
		}
		else {
			if (!open_) {
				curPos_ = pos;
				return DEVICE_OK;
			}

			char closedPos[MM::MaxStrLength];
			this->GetProperty(MM::g_Keyword_Closed_Position, closedPos);
			int gateClosedPosition = atoi(closedPos);

			if (!SetWheelPosition(gateClosedPosition))
			{
				pProp->Set((long)curPos_); // revert
				return ERR_SET_POSITION_FAILED;
			}
		}
		curPos_ = pos;
		open_ = gateOpen;
	}

	return DEVICE_OK;
}

template<class U>
int WheelBase<U>::OnSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set((long)speed_);
	}
	else if (eAct == MM::AfterSet)
	{
		long speed;
		pProp->Get(speed);
		if (speed < 0 || speed > 7)
		{
			pProp->Set((long)speed_); // revert
			return ERR_INVALID_SPEED;
		}
		speed_ = speed;
	}

	return DEVICE_OK;
}

template<class U>
int WheelBase<U>::OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		pProp->Set(this->GetDelayMs());
	}
	else if (eAct == MM::AfterSet)
	{
		double delay;
		pProp->Get(delay);
		this->SetDelayMs(delay);
		delay_ = delay * 1000;
	}
	return DEVICE_OK;
}

template<class U>
int WheelBase<U>::OnBusy(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
			pProp->Set((long)hub_->Busy());
	}
	return DEVICE_OK;
}

template<class U>
void WheelBase<U>::initializeDelayTimer() {
	changedTime_ = this->GetCurrentMMTime();
}
