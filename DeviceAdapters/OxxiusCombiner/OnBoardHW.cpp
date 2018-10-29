///////////////////////////////////////////////////////////////////////////////
// FILE:			OnBoardHW.cpp
// PROJECT:			Micro-Manager
// SUBSYSTEM:		DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:		Describes the contents of the devices (lasers, modulators) 
//					operating inside a combiner
// COPYRIGHT:		Oxxius SA, 2013-2018
// LICENSE:			LGPL
// AUTHOR:			Tristan Martinez
//

#include "OnBoardHW.h"
#include <cstdlib>

using namespace std;

OnBoardHW::OnBoardHW(unsigned int numberOfSlots) {
	AOM1Position_ = 0;
	AOM2Position_ = 0;

	if (numberOfSlots <= MAX_NUMBER_OF_SLOTS) {
		slotMax_ = numberOfSlots;
	} else {
		slotMax_ = MAX_NUMBER_OF_SLOTS;
	}

	for(unsigned int i = 0; i<MAX_NUMBER_OF_SLOTS ; i++) {
		sourceType_[i] = 0;
		sourceWavelength_[i] = 0;
		sourceNominalPower_[i] = 0;
		sourceSerialNumber_.push_back("No serial");
	}
}


OnBoardHW::~OnBoardHW() {
}


bool OnBoardHW::IsEmpty() {
	unsigned short detect = 0;

	for(unsigned int i = 0; i<slotMax_ ; i++) {
		detect |= sourceType_[i];
	}
	return (detect == 0);
}


unsigned int OnBoardHW::GetType(unsigned int slot) {
	return sourceType_[slot-1];
}


void OnBoardHW::SetType(unsigned int slot, const char* newSourceType) {
	if( (slot > 0) & (slot <= MAX_NUMBER_OF_SLOTS) ) {
		std::string	modelStr = "",
					wavelengthStr = "",
					nominalPowerStr = "",
					newSourceDescription = string(newSourceType),
					token = "-";
		std::size_t found;

		found = newSourceDescription.find(token);
		if (found!=std::string::npos) {
			modelStr = newSourceDescription.substr(0, found);
		}

		if (modelStr.compare("LBX") == 0) {			// The source is a LBX
			sourceType_[slot-1] = 10;
		} else if (modelStr.compare("LCX") == 0) {	// The source is a LCX
			if( slot == AOM1Position_ ) {
				sourceType_[slot-1] = 21;
			} else if( slot == AOM2Position_ ) {
				sourceType_[slot-1] = 22;
			} else {
				sourceType_[slot-1] = 20;
			}
		} else {								// Should not happen: unkown type
			sourceType_[slot-1] = 99;
		}

		newSourceDescription.erase(0, found+1);

		found = newSourceDescription.find(token);
		if (found!=std::string::npos) {
			wavelengthStr = newSourceDescription.substr(0, found);
			sourceWavelength_[slot-1] = (unsigned int) atoi(wavelengthStr.c_str());
		}
		newSourceDescription.erase(0, found+1);

		nominalPowerStr.assign(newSourceDescription);
		sourceNominalPower_[slot-1] = (unsigned int) atoi(nominalPowerStr.c_str());
	}
}


void OnBoardHW::GetSerialNumber(unsigned int slot, char * serialN) {
	strcpy(serialN, sourceSerialNumber_[slot-1].c_str());
}


void OnBoardHW::SetSerialNumber(unsigned int slot, const char* newSerialN) {
	if(sourceType_[slot-1] != 0) {
		sourceSerialNumber_[slot-1].assign(newSerialN);
	}
}


void OnBoardHW::SetAOMPos(unsigned int AOM1, unsigned int AOM2) {
	if (AOM1>0)
		AOM1Position_ = AOM1 + 1;
	
	if (AOM2>0)
		AOM2Position_ = AOM2 + 1;
}


void OnBoardHW::GetNominalPower(unsigned int slot, unsigned int nomPower) {
	if(sourceType_[slot-1] != 0) {
		nomPower = sourceNominalPower_[slot-1];
	}
}
