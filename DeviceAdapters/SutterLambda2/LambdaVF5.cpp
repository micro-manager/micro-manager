///////////////////////////////////////////////////////////////////////////////
// FILE:          LambdaVF5.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Sutter Lambda controller adapter
// COPYRIGHT:     Northwestern University, 2018
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
// AUTHOR:        Nick Anthony, Oct. 2018
//
// CVS:           $Id$
//

#include "SutterHub.h"
#include "SutterWheel.h"

LambdaVF5::LambdaVF5(const char* name):
	WheelBase(name, 0, true, "Lambda VF-5 Tunable Filter (Channel A)"),
	wv_(500),
	uSteps_(1),
	tiltSpeed_(3),
	ttlInEnabled_(false),
	ttlOutEnabled_(false)
{};

int LambdaVF5::Initialize(){
	int ret = WheelBase::Initialize();
	if (ret != DEVICE_OK) { return ret;}
	
	configureTTL( true, false, true, 1); //We get some freezes if these aren't explicitly disabled. If ttl in was left on and then the pin goes high but no sequence was loaded then it's going to totally freeze.
	configureTTL( true, false, false, 1);

	CPropertyAction* pAct = new CPropertyAction(this, &LambdaVF5::onWavelength);
	ret = CreateProperty("Wavelength", "500", MM::Integer, false, pAct);
	SetPropertyLimits("Wavelength", 338, 900);
	if (ret != DEVICE_OK) { return ret; }
	
	pAct = new CPropertyAction(this, &LambdaVF5::onWheelTilt);
	ret = CreateProperty("Wheel Tilt (uSteps)", "100", MM::Integer, false, pAct);
	SetPropertyLimits("Wheel Tilt (uSteps)", 0, 267);
	if (ret != DEVICE_OK) { return ret; }
	
	pAct = new CPropertyAction(this, &LambdaVF5::onTiltSpeed);
	ret = CreateProperty("Tilt Speed", "3", MM::Integer, false, pAct);
	SetPropertyLimits("Tilt Speed", 0, 3);
	if (ret != DEVICE_OK) { return ret; }

	pAct = new CPropertyAction(this, &LambdaVF5::onTTLOut);
	ret = CreateProperty("TTL Out", "Disabled", MM::String, false, pAct);
	AddAllowedValue("TTL Out","Disabled");
	AddAllowedValue("TTL Out","Enabled");
	if (ret != DEVICE_OK) { return ret; }

	pAct = new CPropertyAction(this, &LambdaVF5::onTTLOutPolarity);
	ret = CreateProperty("TTL Out Polarity", "Rising Edge", MM::String, false, pAct);
	AddAllowedValue("TTL Out Polarity","Rising Edge");
	AddAllowedValue("TTL Out Polarity","Falling Edge");
	if (ret != DEVICE_OK) { return ret; }

	pAct = new CPropertyAction(this, &LambdaVF5::onTTLIn);
	ret = CreateProperty("TTL In", "Disabled", MM::String, false, pAct);
	AddAllowedValue("TTL In","Disabled");
	AddAllowedValue("TTL In","Enabled");
	if (ret != DEVICE_OK) { return ret; }

	pAct = new CPropertyAction(this, &LambdaVF5::onTTLInPolarity);
	ret = CreateProperty("TTL In Polarity", "Rising Edge", MM::String, false, pAct);
	AddAllowedValue("TTL In Polarity","Rising Edge");
	AddAllowedValue("TTL In Polarity","Falling Edge");
	if (ret != DEVICE_OK) { return ret; }

	pAct = new CPropertyAction(this, &LambdaVF5::onSequenceType);
	ret = CreateProperty("Wavelength Sequence Type", "Arbitrary", MM::String, false, pAct);
	AddAllowedValue("Wavelength Sequence Type","Arbitrary");
	AddAllowedValue("Wavelength Sequence Type","Evenly Spaced");
	if (ret != DEVICE_OK) { return ret; }

	return DEVICE_OK;
}

int LambdaVF5::onWavelength(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		std::vector<unsigned char> cmd;
		std::vector<unsigned char> response;
		cmd.push_back(0xDB);
		int ret = hub_->SetCommand(cmd, cmd, response, 9); // The expected response is `1xx2xx3xx` where the numbers indicate the channel number and the x's are bytes indicating the wavelength for that channel. Sometimes the x's can be a carriage return which would cause the function to think that the response is terminated. For this reason we much tell the function that there is a minimum response length of 9 bytes.
		if (ret != DEVICE_OK) { return ret;}
		if (response.at(0) != 0x01) { //The first byte indicates which channel the response is for. if it isn't 0x01 (channel A) we have a problem.
			return DEVICE_ERR;
		}
		long wv = ((response.at(2) << 8) | (response.at(1)));
		wv = wv & 0x3fff; //The first two bits indicate the current tilt speed of the vf5.
		pProp->Set(wv);
		wv_ = wv;
		return DEVICE_OK;
	}
	else if (eAct == MM::AfterSet) {
		long wv;
		pProp->Get(wv);
		if ((wv > 900) || (wv < 338)) {
			return DEVICE_ERR;
		}
		std::vector<unsigned char> cmd;
		std::vector<unsigned char> response;
		cmd.push_back(0xDA);
		cmd.push_back(0x01);
		cmd.push_back((unsigned char) (wv));
		cmd.push_back(((unsigned char)(wv>>8) | (tiltSpeed_ << 6)));
		wv_ = wv;
		int ret = hub_->SetCommand(cmd);
		initializeDelayTimer();
		return ret;
		
	}
	else if (eAct == MM::IsSequenceable) {
		int max;
		if (sequenceEvenlySpaced_) { max = 3;} //in this case the "sequence" is actually the start, stop, and step of wavelengths
		else { max = 100;}
		pProp->SetSequenceable(max);
		return DEVICE_OK;
	}
	else if (eAct == MM::StartSequence) {
		int ret = resetSequenceIndex(1); //Start from the beginning of the sequence
		if (ret != DEVICE_OK) { return ret; }
		ret = SetProperty("TTL Out", "Enabled");
		if (ret != DEVICE_OK) { return ret; }
		return SetProperty("TTL In", "Enabled");
	
	}
	else if (eAct == MM::StopSequence) {
		int ret = SetProperty("TTL Out", "Disabled");
		if (ret != DEVICE_OK) { return ret; }
		return SetProperty("TTL In", "Disabled");
	}
	else if (eAct == MM::AfterLoadSequence) {
		std::vector<unsigned char> cmd;
		cmd.push_back(0xFA);
		if (sequenceEvenlySpaced_) {
			cmd.push_back(0xF1);
			cmd.push_back(1);
			std::vector<std::string> seq = pProp->GetSequence();
			for (int i=0; i<3; i++) {
				int wv = atoi(seq.at(0).c_str());
				cmd.push_back((unsigned char) (wv));
				cmd.push_back((unsigned char) (wv>>8));
			}
		} else {
			//Any misteps here will cause weirdness and require a hardware restart.
			//Commands with less than 100 wavelengths must be followed by a 16bit 0 to terminate the sequence (0x00 0x00)
			//If a command has 100 wavelengths then it must not have any termination character following it.
			cmd.push_back(0xF2);
			cmd.push_back(1);
			std::vector<std::string> seq = pProp->GetSequence();
			for (int i=0; i<seq.size(); i++){
				int wv = atoi(seq.at(i).c_str());
				cmd.push_back((unsigned char) (wv));
				cmd.push_back((unsigned char) (wv>>8));
			}
			if (seq.size() < 100) {
				cmd.push_back(0);	//Terminate the sequence loading command.
				cmd.push_back(0);
			}
		}
		return hub_->SetCommand(cmd);
	}
   return MM_CODE_ERR;
}

int LambdaVF5::onTiltSpeed(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		pProp->Set(tiltSpeed_);
	}
	else if (eAct == MM::AfterSet) {
		pProp->Get(tiltSpeed_);
	}
	return DEVICE_OK;
}

int LambdaVF5::onWheelTilt(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		pProp->Set(uSteps_);
	}
	else if (eAct == MM::AfterSet)
	{
		long uSteps;
		pProp->Get(uSteps);
		if ((uSteps > 267) || (uSteps < 0)) {
			return DEVICE_ERR;
		}
		std::vector<unsigned char> cmd;
		cmd.push_back(0xDE);
		cmd.push_back(0x01);
		cmd.push_back((unsigned char) (uSteps));
		cmd.push_back((unsigned char) (uSteps>>8));
		int ret = hub_->SetCommand(cmd);
		if (ret != DEVICE_OK) { return ret;}
		initializeDelayTimer();
		uSteps_ = uSteps;
	}
	return DEVICE_OK;
}

int LambdaVF5::configureTTL( bool risingEdge, bool enabled, bool output, unsigned char channel){
	if ((channel > 3) || (channel < 1)) {
		LogMessage("Lambda VF5: Invalid TTL Channel specified", false);
		return DEVICE_ERR;
	}
	std::vector<unsigned char> cmd;
	cmd.push_back(0xFA);
	unsigned char action = 0xA0;
	if (output) { //We're configuring the TTL Out
		action |= (output << 4); // we now have 0xB0
		if (enabled) {
			if (risingEdge) { action |= 0x01; }
			else { action |= 0x02; }
		}
	} else { //We're configuring the TTL In
		if (enabled) {
			if (risingEdge) { action |= 0x03; }
			else { action |= 0x04; }
		}
	}
	cmd.push_back(action);
	cmd.push_back(channel);
	return hub_->SetCommand(cmd);
}

int LambdaVF5::onTTLOut(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		std::string setting;
		if (ttlOutEnabled_) {
			setting = "Enabled";
		} else {
			setting = "Disabled";
		}
		pProp->Set(setting.c_str());
		return DEVICE_OK;
	}
	else if (eAct == MM::AfterSet) {
		std::string setting;
		pProp->Get(setting);
		if (setting.compare("Disabled")==0) {
			ttlOutEnabled_ = false;
		}
		else if (setting.compare("Enabled")==0) {
			ttlOutEnabled_ = true;
		}
		else { //The setting wasn't valid for some reason
			return DEVICE_ERR;
		}
		return configureTTL(ttlOutRisingEdge_, ttlOutEnabled_, true, 1);
	}
   return MM_CODE_ERR;
}

int LambdaVF5::onTTLIn(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		std::string setting;
		if (ttlInEnabled_) {
			setting = "Enabled";
		} else {
			setting = "Disabled";
		}
		pProp->Set(setting.c_str());
		return DEVICE_OK;
	}
	else if (eAct == MM::AfterSet) {
		std::string setting;
		pProp->Get(setting);
		if (setting=="Disabled") {
			ttlInEnabled_ = false;
		}
		else if (setting=="Enabled") {
			ttlInEnabled_ = true;
		}
		else { //The setting wasn't valid for some reason
			return DEVICE_ERR;
		}
		return configureTTL(ttlInRisingEdge_, ttlInEnabled_, false, 1);
	}
   return MM_CODE_ERR;
}

int LambdaVF5::onTTLInPolarity(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		std::string setting;
		if (ttlInRisingEdge_) {
			setting = "Rising Edge";
		} else {
			setting = "Falling Edge";
		}
		pProp->Set(setting.c_str());
		return DEVICE_OK;
	}
	else if (eAct == MM::AfterSet) {
		std::string setting;
		pProp->Get(setting);
		if (setting=="Rising Edge") {
			ttlInRisingEdge_ = true;
		}
		else if (setting=="Falling Edge") {
			ttlInRisingEdge_ = false;
		}
		else { //The setting wasn't valid for some reason
			return DEVICE_ERR;
		}
		return configureTTL(ttlInRisingEdge_, ttlInEnabled_, false, 1);
	}
   return MM_CODE_ERR;
}

int LambdaVF5::onTTLOutPolarity(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		std::string setting;
		if (ttlOutRisingEdge_) {
			setting = "Rising Edge";
		} else {
			setting = "Falling Edge";
		}
		pProp->Set(setting.c_str());
		return DEVICE_OK;
	}
	else if (eAct == MM::AfterSet) {
		std::string setting;
		pProp->Get(setting);
		if (setting=="Rising Edge") {
			ttlOutRisingEdge_ = true;
		}
		else if (setting=="Falling Edge") {
			ttlOutRisingEdge_ = false;
		}
		else { //The setting wasn't valid for some reason
			return DEVICE_ERR;
		}
		return configureTTL(ttlOutRisingEdge_, ttlOutEnabled_, true, 1);
	}
   return MM_CODE_ERR;
}


int LambdaVF5::onSequenceType(MM::PropertyBase* pProp, MM::ActionType eAct) {
	if (eAct == MM::BeforeGet) {
		std::string setting;
		if (sequenceEvenlySpaced_) {
			setting = "Evenly Spaced";
		} else {
			setting = "Arbitrary";
		}
		pProp->Set(setting.c_str());
		return DEVICE_OK;
	}
	else if (eAct == MM::AfterSet) {
		std::string setting;
		pProp->Get(setting);
		if (setting=="Arbitrary") {
			sequenceEvenlySpaced_ = false;
		}
		else if (setting=="Evenly Spaced") {
			sequenceEvenlySpaced_ = true;
		}
		else { //The setting wasn't valid for some reason
			return DEVICE_ERR;
		}
		return DEVICE_OK;
	}
   return MM_CODE_ERR;
}

int LambdaVF5::resetSequenceIndex(unsigned char channel) {
	//This function resets the current index in the TTL sequence that the Lambda 10 is at. This is the only reliable way to go back to the beginning of a sequence.
	std::vector<unsigned char> cmd;
	cmd.push_back(0xFA);
	cmd.push_back(0xC0);
	cmd.push_back(channel);
	return hub_->SetCommand(cmd);
}