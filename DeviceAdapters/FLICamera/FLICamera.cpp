///////////////////////////////////////////////////////////////////////////////
// FILE:          FLICamera.cpp
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

#ifdef WIN32

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#define snprintf _snprintf 

extern "C" {
	long __stdcall FLILibAttach(void);
	long __stdcall FLILibDetach(void);
}

#endif

#include "FLICamera.h"
#include "../../MMDevice/ModuleInterface.h"
#include <string>
#include <sstream>
#include <cmath>

using namespace std;

const char* g_CameraDeviceName = "FLICamera";
const char* g_CameraDescription = "FLI Camera Device Adapter";

const char* g_Keyword_Shutter = "Shutter";
const char* g_Shutter_Open = "Open";
const char* g_Shutter_Normal = "Normal";

const char* g_PixelType_16bit = "16bit";

const char* g_Keyword_CameraSerial = "CameraSerial";

#define DOFLIAPIERR(F,A) {long status; \
	if ((status = (F)) != 0) \
	{ char err[1024]; memset(err, '\0', 1024); \
		snprintf(err, 1023, "%s failed in %s, line %d, status %d\n", #F, __FILE__, __LINE__, status); \
		LogMessage(err, true); \
		ret = A; \
		Disconnect(); \
		return ret; } }

#define DOFLIAPI(F,A) {long status; bool a = A; \
	if ((status = (F)) != 0) \
	{ char err[1024]; memset(err, '\0', 1024); \
		snprintf(err, 1023, "%s failed in %s, line %d, status %d\n", #F, __FILE__, __LINE__, status); \
		LogMessage(err, true); \
		ret = DEVICE_ERR; \
		if (a != false) break; } }

// TODO: linux entry code

#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                      DWORD  ul_reason_for_call, 
                      LPVOID /*lpReserved*/
                      )
{
	switch(ul_reason_for_call)
	{
		case DLL_PROCESS_ATTACH:
			FLILibAttach();
		break;

		case DLL_THREAD_ATTACH:
		break;

		case DLL_THREAD_DETACH:
		break;

		case DLL_PROCESS_DETACH:
			FLILibDetach();
		break;

		default:
		break;
	}
	return TRUE;
}
#endif

MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_CameraDeviceName, MM::CameraDevice, "FLI Camera Device");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if(deviceName == NULL)
		return 0;

	if(strcmp(deviceName, g_CameraDeviceName) == 0)
	{
		return new CFLICamera();
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

CFLICamera::CFLICamera() :
	CCameraBase<CFLICamera> (),
	initialized_(false),
	pDemoResourceLock_(0),
	dev_(FLI_INVALID_DEVICE),
	image_offset_x_(0),
	image_offset_y_(0),
	image_width_(0),
	image_height_(0),
	offset_x_(0),
	offset_y_(0),
	bin_x_(0),
	bin_y_(0),
	width_(0),
	height_(0),
	exposure_(1000),
	downloaded_(0),
	shutter_(FLI_SHUTTER_CLOSE)
{
	InitializeDefaultErrorMessages();
	pDemoResourceLock_ = new MMThreadLock();
}

CFLICamera::~CFLICamera()
{
	delete pDemoResourceLock_;
}

void CFLICamera::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
}

int CFLICamera::Initialize()
{
  CPropertyAction *pAct = NULL;
	int ret = 0;
	long ul_x, ul_y, lr_x, lr_y;
	char buf[32];

	if (initialized_)
		return DEVICE_OK;

	DOFLIAPIERR(FLIOpen(&dev_, "flipro0", FLIDOMAIN_USB | FLIDEVICE_CAMERA), DEVICE_NOT_CONNECTED);
	DOFLIAPIERR(FLIControlShutter(dev_, shutter_), DEVICE_NOT_CONNECTED);
	DOFLIAPIERR(FLIGetPixelSize(dev_, &pixel_x_, &pixel_y_), DEVICE_NOT_CONNECTED);
	DOFLIAPIERR(FLIGetVisibleArea(dev_, &ul_x, &ul_y, &lr_x, &lr_y), DEVICE_NOT_CONNECTED);

	image_offset_x_ = ul_x;
	image_offset_y_ = ul_y;
	image_width_ = lr_x - ul_x;
	image_height_ = lr_y - ul_y;

	bin_x_ = 1;
	bin_y_ = 1;
	offset_x_ = 0;
	offset_y_ = 0;
	width_ = image_width_;
	height_ = image_height_;

	offset_x_last_ = offset_x_;
	offset_y_last_ = offset_y_;
	width_last_ = width_;
	height_last_ = height_;
	bin_x_last_ = bin_x_;
	bin_y_last_ = bin_y_;

	ret = CreateProperty(MM::g_Keyword_Name, g_CameraDeviceName, MM::String, true);
	assert(ret == DEVICE_OK);

	ret = CreateProperty(MM::g_Keyword_Description, g_CameraDescription, MM::String, true);
	assert(ret == DEVICE_OK);

	memset(buf, '\0', sizeof(buf));
	DOFLIAPIERR(FLIGetModel(dev_, buf, sizeof(buf) - 1), DEVICE_NOT_CONNECTED);
	ret = CreateProperty(MM::g_Keyword_CameraName, buf, MM::String, true);
	assert(ret == DEVICE_OK);

	memset(buf, '\0', sizeof(buf));
	DOFLIAPIERR(FLIGetSerialString(dev_, buf, sizeof(buf) - 1), DEVICE_NOT_CONNECTED);
	ret = CreateProperty(g_Keyword_CameraSerial, buf, MM::String, true);
	assert(ret == DEVICE_OK);
	ret = CreateProperty(MM::g_Keyword_CameraID, buf, MM::String, true);
	assert(ret == DEVICE_OK);

	pAct = new CPropertyAction (this, &CFLICamera::OnBinning);
	ret = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
	assert(ret == DEVICE_OK);

  vector<string> binValues;
	for(int i = 1; i <= 8; i += 1)
	{
	  char b[16];
		sprintf(b, "%d", i);
		binValues.push_back(b);
	}
  ret = SetAllowedValues(MM::g_Keyword_Binning, binValues);
  assert(ret == DEVICE_OK);

	ret = SetPropertyLimits(MM::g_Keyword_Binning, 1, 255);
	assert(ret == DEVICE_OK);

	pAct = new CPropertyAction (this, &CFLICamera::OnPixelType);
	ret = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_16bit, MM::String, false, pAct);
	assert(ret == DEVICE_OK);

	vector<string> pixelTypeValues;
	pixelTypeValues.push_back(g_PixelType_16bit); 

	ret = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
	if (ret != DEVICE_OK)
		return ret;

	pAct = new CPropertyAction (this, &CFLICamera::OnCCDTemperature);
	ret = CreateProperty(MM::g_Keyword_CCDTemperature, "50", MM::Float, true, pAct);
	assert(ret == DEVICE_OK);

	pAct = new CPropertyAction (this, &CFLICamera::OnCCDTemperatureSetpoint);
	ret = CreateProperty(MM::g_Keyword_CCDTemperatureSetPoint, "25.0", MM::Float, false, pAct);
	assert(ret == DEVICE_OK);

	ret = SetPropertyLimits(MM::g_Keyword_CCDTemperatureSetPoint, (-50.0), (40.0));
	assert(ret == DEVICE_OK);

  pAct = new CPropertyAction (this, &CFLICamera::OnExposure);
	ret = CreateProperty(MM::g_Keyword_Exposure, "100", MM::Float, false, pAct);
	assert(ret == DEVICE_OK);
	SetPropertyLimits(MM::g_Keyword_Exposure, 0, 10000000);

	pAct = new CPropertyAction (this, &CFLICamera::OnShutterSetting);
	ret = CreateProperty(g_Keyword_Shutter, g_Shutter_Normal, MM::String, false, pAct);
	assert(ret == DEVICE_OK);

	vector<string> shutterTypeValues;
	shutterTypeValues.push_back(g_Shutter_Normal); 
	shutterTypeValues.push_back(g_Shutter_Open); 

	ret = SetAllowedValues(g_Keyword_Shutter, shutterTypeValues);
	if (ret != DEVICE_OK)
		return ret;

	ret = ResizeImageBuffer();
	if (ret != DEVICE_OK)
		return ret;

	initialized_ = true;
	return DEVICE_OK;
}

int CFLICamera::Shutdown()
{
	Disconnect();

	initialized_ = false;
	return DEVICE_OK;
}

bool CFLICamera::Busy()
{
	return FALSE;
}

int CFLICamera::SnapImage()
{
	long ret = DEVICE_OK;

	/* Need to test a lot of conditions here to make sure buffers are in line */

	bool done = false;
	while (done == false)
	{
		int frametype = FLI_FRAME_TYPE_NORMAL;
		long ul_x, ul_y, lr_x, lr_y;

		DOFLIAPI(FLISetFrameType(dev_, frametype), true)

		ul_x = image_offset_x_ + offset_x_;
		ul_y = image_offset_y_ + offset_y_;
		lr_x = ul_x + (width_ / bin_x_);
		lr_y = ul_y + (height_ / bin_y_);

		DOFLIAPI(FLISetHBin(dev_, bin_x_), true)
		DOFLIAPI(FLISetVBin(dev_, bin_y_), true)
		DOFLIAPI(FLISetImageArea(dev_, ul_x, ul_y, lr_x, lr_y), true)
		DOFLIAPI(FLISetExposureTime(dev_, exposure_), true)
		DOFLIAPI(FLIExposeFrame(dev_), true)

		while (done == false)
		{
			char dbgtxt[512];
			long remaining_exposure = 0;
			long camera_status = 0;

			DOFLIAPI(FLIGetDeviceStatus(dev_, &camera_status), true);
			DOFLIAPI(FLIGetExposureStatus(dev_, &remaining_exposure), true);

			switch (camera_status & 0x03)
			{
				case 0x00:
					LogMessage("Camera Idle", true);
				break;

				case 0x01:
					LogMessage("Waiting for trigger", true);
				break;

				case 0x02:
				{
					double e, h, m, s;

					e = ((double) remaining_exposure) / 1000.0;
					h = floor((e / (60.0 * 60.0)));
					m = floor((e - (h * 60.0 * 60.0)) / 60.0);
					s = e - (h * 60.0 * 60.0) - (m * 60.0);

					snprintf(dbgtxt, 512, "Exposing (%02.0lf:%02.0lf:%05.2lf)", h, m, s);
					LogMessage(dbgtxt, true);
				}
				break;

				case 0x03:
					LogMessage("Reading CCD...", true);
				break;

				default:
				break;
			}

			if ( ((camera_status == FLI_CAMERA_STATUS_UNKNOWN) && (remaining_exposure == 0)) ||
					 ((camera_status != FLI_CAMERA_STATUS_UNKNOWN) && ((camera_status & 0x80000000) != 0))
					 )
			{
				done = true;
			}
			else
			{
				/* Not run wild! */
				Sleep((remaining_exposure > 200)?200:(remaining_exposure < 10)?10:remaining_exposure);
			}
		}

		done = true;
	}

	downloaded_ = 0;
	return ret;
}

const unsigned char* CFLICamera::GetImageBuffer()
{
	if (downloaded_ == 0)
	{
		int j;

		downloaded_ = 1;

		unsigned short* pBuf = (unsigned short*) const_cast<unsigned char*>(img_.GetPixels());

		for (j = 0; j < (signed) img_.Height(); j++)
		{
			long ret;
			long lIndex = img_.Width() * j;

			ret = FLIGrabRow(dev_, pBuf + lIndex, img_.Width());

			if (ret != 0)
			{
				LogMessage("Error downloading image from camera", false);
				break;
			}
		}

		offset_x_last_ = offset_x_;
		offset_y_last_ = offset_y_;
		width_last_ = width_;
		height_last_ = height_;
		bin_x_last_ = bin_x_;
		bin_y_last_ = bin_y_;

	}  

	return img_.GetPixels();
}

int CFLICamera::GetComponentName(unsigned channel, char* name)
{
  if (channel >= GetNumberOfComponents())
     return DEVICE_NONEXISTENT_CHANNEL;

	char buf[32];
	sprintf(buf, "Channel %d", channel);

  CDeviceUtils::CopyLimitedString(name, buf);
  return DEVICE_OK;
}

unsigned CFLICamera::GetNumberOfChannels() const 
{
  return 1;
}

unsigned CFLICamera::GetNumberOfComponents() const 
{
  return 1;
}

unsigned CFLICamera::GetImageWidth() const
{
	return img_.Width();
}

unsigned CFLICamera::GetImageHeight() const
{
	return img_.Height();
}

unsigned CFLICamera::GetImageBytesPerPixel() const
{
	return img_.Depth();
} 

unsigned CFLICamera::GetBitDepth() const
{
	return (16);
}

long CFLICamera::GetImageBufferSize() const
{
	return img_.Width() * img_.Height() * GetImageBytesPerPixel();
}

int CFLICamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
	if (xSize == 0 && ySize == 0)
	{
		offset_x_ = 0;
		offset_y_ = 0;
		width_ = image_width_;
		height_ = image_height_;
	}
	else
	{
		offset_x_ = (x & 0xfffe);
		offset_y_ = (y & 0xfffe);
		width_ = ((xSize * bin_x_) & 0xfffe);
		height_ = ((ySize * bin_y_) & 0xfffe);
	}

	ResizeImageBuffer();
	return DEVICE_OK;
}

int CFLICamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
	x = offset_x_/bin_x_;
	y = offset_y_/bin_y_;
	xSize = width_last_ / bin_x_;
	ySize =  height_last_ / bin_y_;

	return DEVICE_OK;
}

int CFLICamera::ClearROI()
{
	offset_x_ = 0;
	offset_y_ = 0;

	width_ = image_width_;
	height_ = image_height_;

	ResizeImageBuffer();

	return DEVICE_OK;
}

double CFLICamera::GetExposure() const
{
	char buf[MM::MaxStrLength];
	int ret = GetProperty(MM::g_Keyword_Exposure, buf);
	if (ret != DEVICE_OK)
		return 0.0;

	return atof(buf);
}

void CFLICamera::SetExposure(double exp)
{
	SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

int CFLICamera::GetBinning() const
{
	char buf[MM::MaxStrLength];
	int ret = GetProperty(MM::g_Keyword_Binning, buf);

	if (ret != DEVICE_OK)
		return 1;

	return atoi(buf);
}

int CFLICamera::SetBinning(int binFactor)
{
	return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
}

int CFLICamera::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long ret = DEVICE_OK;

	if(eAct == MM::AfterSet)
	{
		string pixelType;
		pProp->Get(pixelType);

		if(pixelType.compare(g_PixelType_16bit) !=0)
		{
			LogMessage("Attempt to set unsupported pixel type", false);

			pProp->Set(g_PixelType_16bit);
			ret = DEVICE_ERR;
		}
	}
	else if(eAct == MM::BeforeGet)
	{
		pProp->Set(g_PixelType_16bit);
	}

	return ret;
}

int CFLICamera::OnShutterSetting(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	long ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		string shutterType;
		pProp->Get(shutterType);

		if (shutterType.compare(g_Shutter_Normal) == 0)
		{
			shutter_ = FLI_SHUTTER_CLOSE;
		}
		else if (shutterType.compare(g_Shutter_Open) == 0)
		{
			shutter_ = FLI_SHUTTER_OPEN;
		}
		else
		{
			LogMessage("Attempt to set shutter to unknown", false);
			pProp->Set(g_Shutter_Normal);
			ret = DEVICE_ERR;
		}

		if (ret == DEVICE_OK)
		{
			ret = FLIControlShutter(dev_, shutter_);
			if (ret != 0)
			{
				Disconnect();
				ret = DEVICE_NOT_CONNECTED;
			}
		}
	}
		
	if ( (eAct == MM::BeforeGet) ||
		((eAct == MM::AfterSet) && (ret == DEVICE_OK)) )
	{
		switch (shutter_)
		{
			case FLI_SHUTTER_CLOSE:
				pProp->Set(g_Shutter_Normal);
				break;

			case FLI_SHUTTER_OPEN:
				pProp->Set(g_Shutter_Open);
				break;

			default:
				pProp->Set(g_Shutter_Normal);
				break;
		}
	}

	return ret;
}

int CFLICamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	switch(eAct)
	{
		case MM::AfterSet:
		{
			long bin;
			pProp->Get(bin);

			if ((bin > 0) && (bin <= 255))
			{
				bin_x_ = bin;
				bin_y_ = bin;
			}
			else
			{
				LogMessage("Invalid BIN range set");

				bin_x_ = 1;
				bin_y_ = 1;

				pProp->Set(1L);
				ret = DEVICE_INVALID_PROPERTY_VALUE;
			}

			ResizeImageBuffer();
		}
		break;

		case MM::BeforeGet:
		{
			pProp->Set(bin_x_);
			ret = DEVICE_OK;
		}
		break;
	}

	return ret; 
}

int CFLICamera::OnCCDTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_ERR;

	if (eAct == MM::BeforeGet)
	{
		double t;

		ret = FLIReadTemperature(dev_, FLI_TEMPERATURE_CCD, &t);
		if (ret != 0)
		{
			Disconnect();
			ret = DEVICE_NOT_CONNECTED;
		}
		else
		{
			pProp->Set(t);
			ret = DEVICE_OK;
		}
	}
	else
	{

	}

	return ret;
}

int CFLICamera::OnCCDTemperatureSetpoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_ERR;

	if (eAct == MM::AfterSet)
	{
		double t;

		pProp->Get(t);
		ret = FLISetTemperature(dev_, t);
		if (ret != 0)
		{
			Disconnect();
			ret = DEVICE_NOT_CONNECTED;
		}
		else
		{
			ret = DEVICE_OK;
		}
	}
	else if (eAct == MM::BeforeGet)
	{
		ret = DEVICE_OK;
	}

	return ret; 
}

int CFLICamera::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_OK;

	if (eAct == MM::AfterSet)
	{
		double e;

		pProp->Get(e);

		exposure_ = (long) e;
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set((double) exposure_);
	}

	return ret; 
}

int CFLICamera::PrepareSequenceAcqusition()
{
	return DEVICE_OK;
}

double CFLICamera::GetNominalPixelSizeUm() const
{
	return pixel_x_;
}

double CFLICamera::GetPixelSizeUm() const
{
	return pixel_x_;
}

int CFLICamera::ResizeImageBuffer()
{
	int byteDepth = 2;

	assert(byteDepth != 2);

	img_.Resize(width_ / bin_x_, height_ / bin_y_, byteDepth);

	return DEVICE_OK;
}

void CFLICamera::Disconnect(void)
{
	if (dev_ != FLI_INVALID_DEVICE)
	{
		FLIClose(dev_);
		dev_ = FLI_INVALID_DEVICE;
	}

	return;
}
