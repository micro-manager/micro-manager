///////////////////////////////////////////////////////////////////////////////
// FILE:			OnBoardHW.h
// PROJECT:			Micro-Manager
// SUBSYSTEM:		DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:		Describes the contents of the devices (lasers, modulators) 
//					operating inside a combiner
// COPYRIGHT:		Oxxius SA, 2013-2018
// LICENSE:			LGPL
// AUTHOR:			Tristan Martinez
//


//	Laser sources exists in different models...
//	SourceType[] depicts the can take the following values:
//
//
//	model[0]	model[1]
//	(major)		(minor)
//		1			0		-> standard LBX
//		2			0		-> standard LCX
//		2			1		-> LCX linked to an AOM number 1
//		2			2		-> LCX linked to an AOM number 2
//		2			5		-> LCX with power adjustment

#ifndef _OXXIUS_ONBOARDHW_H_
#define _OXXIUS_ONBOARDHW_H_

#define	MAX_NUMBER_OF_SLOTS	6
#define	MAX_CHARACTERS 256

#include <string>
#include <vector>

class OnBoardHW
{
public:
	OnBoardHW(unsigned int numberOfSlots);
	~OnBoardHW();

	bool IsEmpty();

	unsigned int GetType(unsigned int slot);
	void SetType(unsigned int slot, const char* newSourceType);

	void GetSerialNumber(unsigned int slot, char* serialN);
	void SetSerialNumber(unsigned int slot, const char* newSerialN);

	void SetAOMPos(unsigned int AOM1, unsigned int AOM2);

	void GetNominalPower(unsigned int slot, unsigned int nomPower);

private:
	unsigned int sourceType_[MAX_NUMBER_OF_SLOTS];
	unsigned int sourceWavelength_[MAX_NUMBER_OF_SLOTS];
	unsigned int sourceNominalPower_[MAX_NUMBER_OF_SLOTS];
	std::vector<std::string> sourceSerialNumber_;
//	std::array<std::string, MAX_NUMBER_OF_SLOTS> sourceSerialNumber_;
//	std::array<std::string, MAX_NUMBER_OF_SLOTS> sourceDescription_;

	unsigned int slotMax_;
	unsigned int wavelength_;
	unsigned int AOM1Position_;
	unsigned int AOM2Position_;
};

#endif // _OXXIUS_ONBOARDHW_H_