///////////////////////////////////////////////////////////////////////////////
// FILE:          FakeCamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   A camera implementation that is backed by the file system
//                Can access stage positions to choose image to display
//
// AUTHOR:        Lukas Lang
//
// COPYRIGHT:     2017 Lukas Lang
// LICENSE:       Licensed under the Apache License, Version 2.0 (the "License");
//                you may not use this file except in compliance with the License.
//                You may obtain a copy of the License at
//                
//                http://www.apache.org/licenses/LICENSE-2.0
//                
//                Unless required by applicable law or agreed to in writing, software
//                distributed under the License is distributed on an "AS IS" BASIS,
//                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//                See the License for the specific language governing permissions and
//                limitations under the License.

#pragma once

#include <string>

#include "DeviceBase.h"

#include "opencv/highgui.h"

#define ERR_INVALID_DEVICE_NAME 10000
#define OUT_OF_RANGE 10001
#define CONTROLLER_ERROR 10002

#include "error_code.h"

extern const char* cameraName;
extern const char* label_CV_8U;
extern const char* label_CV_16U;
extern const char* label_CV_8UC4;
extern const char* label_CV_16UC4;


class parse_error : public std::exception {};

class FakeCamera : public CCameraBase<FakeCamera>
{
public:
	FakeCamera();
	~FakeCamera();

	// Inherited via CCameraBase
	int Initialize();
	int Shutdown();
	void GetName(char * name) const;
	long GetImageBufferSize() const;
	unsigned GetBitDepth() const;
	int GetBinning() const;
	int SetBinning(int binSize);
	void SetExposure(double exp_ms);
	double GetExposure() const;
	int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize);
	int GetROI(unsigned & x, unsigned & y, unsigned & xSize, unsigned & ySize);
	int ClearROI();
	int IsExposureSequenceable(bool & isSequenceable) const;
	const unsigned char * GetImageBuffer();
	unsigned GetImageWidth() const;
	unsigned GetImageHeight() const;
	unsigned GetImageBytesPerPixel() const;
	int SnapImage();
	int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
	int StopSequenceAcquisition();
	void OnThreadExiting() throw();

	unsigned GetNumberOfComponents() const;
	const unsigned int* GetImageBufferAsRGB32();

	int OnPath(MM::PropertyBase* pProp, MM::ActionType eAct);
	int ResolvePath(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnFrameCount(MM::PropertyBase* pProp, MM::ActionType eAct);

	std::string parseUntil(const char*& it, const char delim) const throw (parse_error);
	std::string parsePlaceholder(const char*& it) const;
	std::pair<int, int> parsePrecision(const char*& it) const throw (parse_error);
	static std::ostream& printNum(std::ostream& o, std::pair<int, int> precSpec, double num);
	static std::string iif(bool test, std::string spec);
	std::string parseMask(std::string mask) const throw(error_code);
	void getImg() const;
	void updateROI() const;

	void initSize(bool loadImg = true) const;

private:
	bool initialized_;

	std::string path_;
	int frameCount_;

	bool capturing_;
	mutable bool initSize_;
	mutable unsigned width_;
	mutable unsigned height_;
	unsigned byteCount_;
	unsigned type_;
	bool color_;

	mutable unsigned roiX_;
	mutable unsigned roiY_;
	mutable unsigned roiWidth_;
	mutable unsigned roiHeight_;

	cv::Mat emptyImg;

	mutable cv::Mat curImg_;
	mutable cv::Mat alphaChannel_;
	mutable cv::Mat lastFailedImg_;
	mutable cv::Mat roi_;
	mutable std::string curPath_;
	mutable std::string lastFailedPath_;

	void resetCurImg();

	double exposure_;
};
