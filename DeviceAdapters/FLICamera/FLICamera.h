///////////////////////////////////////////////////////////////////////////////
// FILE:          FLICamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   FLI Camera interface for MicroManager
//                
// AUTHOR:        Jim Moronski, jim@flicamera.com, 12/2010
//
// COPYRIGHT:     Finger Lakes Instrumentation, LLC, 2010
//								University of California, San Francisco, 2006
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

#ifndef _FLICAMERA_H_
#define _FLICAMERA_H_

#include "libfli.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
#include <map>

class CFLICamera : public CCameraBase<CFLICamera>
{
	public:
		CFLICamera();
		~CFLICamera();

		// MMDevice API
		// ------------
		int Initialize();
		int Shutdown();
		void GetName(char* name) const;  
		bool Busy();

		// MMCamera API
		// ------------
		int SnapImage();
		const unsigned char* GetImageBuffer();
		unsigned GetImageWidth() const;
		unsigned GetImageHeight() const;
		unsigned GetImageBytesPerPixel() const;
		unsigned GetBitDepth() const;
		long GetImageBufferSize() const;
		double GetExposure() const;
		void SetExposure(double exp);
		int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
		int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize); 
		int ClearROI();
		int PrepareSequenceAcqusition();
		double GetNominalPixelSizeUm() const;
		double GetPixelSizeUm() const;
		int GetBinning() const;
		int SetBinning(int binSize);
		int IsExposureSequenceable(bool& seq) const {seq = false; return DEVICE_OK;}
		int GetComponentName(unsigned channel, char* name);
		unsigned GetNumberOfChannels() const;
		unsigned GetNumberOfComponents() const;

    // action interface
		int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnCCDTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnCCDTemperatureSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
		int OnShutterSetting(MM::PropertyBase* pProp, MM::ActionType eAct);

	private:
		int ResizeImageBuffer();

		MMThreadLock* pDemoResourceLock_;
		bool initialized_;

		flidev_t dev_;
		ImgBuffer img_;
		ImgBuffer dlimg_;

		long image_width_;
		long image_height_;
		long image_offset_x_;
		long image_offset_y_;

		long offset_x_;
		long offset_y_;
		long width_;
		long height_;
		long bin_x_;
		long bin_y_;

		long offset_x_last_;
		long offset_y_last_;
		long width_last_;
		long height_last_;
		long bin_x_last_;
		long bin_y_last_;

		long exposure_;
		long shutter_;
		long downloaded_;

		double pixel_x_;
		double pixel_y_;

public:
	void Disconnect(void);
};


#endif
