
///////////////////////////////////////////////////////////////////////////////
// FILE:          TwainDevice.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   implementation classes for Twain Camera
//                
// COPYRIGHT:     University of California, San Francisco, 2009
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
#pragma once
#include <vector>
#include <string>
#include <map>

typedef unsigned short uint16_t;

union rectangle_t
{
	long long values_;
	uint16_t lefttoprightbottom_[4];
};

// forward declaration allows use of container object's facilities, but keeps the include depdencies simple
class TwainCamera;

// the implementation class for the Twain Library
class TwImpl;

class TwainDevice
{
private:
	// disallow default ctor
	TwainDevice(void);
public:
	// the only public ctor
	TwainDevice(TwainCamera* pcamera__);

	// make this virtual if the class is to be extended.
	~TwainDevice(void);

	char* GetImage(int& imh__, int& imw__, char& bpp__);
	void EnableCamera(const bool enable);
	std::vector<std::string> AvailableSources(void);
	void SelectAndOpenSource(std::string);
	void SetROIRectangle( const uint16_t& left__, const uint16_t& top__, const uint16_t& right__, const uint16_t& bottom__, uint16_t* pactualwidth__ = NULL, uint16_t* pactualheight__ = NULL, unsigned char* pactualdepth__ = NULL);
	void GetROIRectangle(uint16_t& left__, uint16_t& top__, uint16_t& right__, uint16_t& bottom__);
	void GetWholeCaptureRectangle(uint16_t& left__, uint16_t& top__, uint16_t& right__, uint16_t& bottom__);
	void GetActualImageSize(uint16_t& imheight__, uint16_t& imwidth__, char& bytesppixel__);
	void GetActualImageSize(uint16_t& left__, uint16_t& top__, uint16_t& right__, uint16_t& bottom__, uint16_t& imheight__, uint16_t& imwidth__, char& bytesppixel__);

	void ClearROI(uint16_t* pactualwidth__ = NULL, uint16_t* pactualheight__ = NULL, unsigned char* pactualdepth__ = NULL);

	std::string TwainDevice::CurrentSource();
   void LaunchVendorSettings(void);
	void StopTwain(void);
	void CurrentlySelectedSource(const std::string sourceName);
	std::string CurrentlySelectedSource(void);


private:

	TwImpl* pTwImpl_;
	std::map<long long, std::pair<uint16_t,uint16_t> > roiImageSizes_;
	char currentpixeldepth_;


	// TwainDevice   is an adapter from the implemenation to DeviceBase instantiation
	
	// the wrapper class
	TwainCamera* pcamera_;

	// for performance measurement:
	double imageTransferStartTime_; 
	double previousImageStartTime_;


};



