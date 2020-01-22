///////////////////////////////////////////////////////////////////////////////
// FILE:          FirstLightImagingCameras.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Device adapter for C-RED2 & C-RED3 cameras for USB, Matrox, 
//				  Sapera and Edt grabbers.
//                
// AUTHOR:        JTU, 13/11/2019
//
// COPYRIGHT:     First Light Imaging Ltd, (2011-2019)
// LICENSE:       License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES. 
//

#include "FirstLightImagingCameras.h"

#ifdef __unix__
#include "unistd.h"
#endif

#include <cstdlib>
#include <iostream>

#include "FliSdk_utils.h"
#include "ModuleInterface.h"

//---------------------------------------------------------------
int roundUp(int numToRound, int multiple)
{
	if (multiple == 0)
		return numToRound;

	int remainder = numToRound % multiple;
	if (remainder == 0)
		return numToRound;

	if (remainder < multiple / 2)
		return numToRound - remainder;
	else
		return numToRound + multiple - remainder;
}

//---------------------------------------------------------------
MODULE_API void InitializeModuleData()
{
	RegisterDevice("FliSdk", MM::CameraDevice, "First Light Imaging camera");
}

//---------------------------------------------------------------
MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (!deviceName)
		return 0;

	return new FirstLightImagingCameras(deviceName);

	return 0;
}

//---------------------------------------------------------------
MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

//---------------------------------------------------------------
FirstLightImagingCameras::FirstLightImagingCameras(std::string cameraName) :
	_initialized(false),
	_cameraName(cameraName),
	_cameraModel(undefined),
	_nbCameras(0),
	_credTwo(false),
	_credThree(false),
	_isCapturing(false)
{
	FliSdk_init();
	uint8_t nbGrabbers = 0;
	FliSdk_detectGrabbers(&nbGrabbers);

	if(nbGrabbers > 0)
	{
		_listOfCameras = FliSdk_detectCameras(&_nbCameras);

		if(_nbCameras > 0)
		{
			FliSdk_setCamera(_listOfCameras[0]);
			FliSdk_setBufferSize(500);
			FliSdk_update();
			_cameraModel = FliSdk_getCameraModel();

			if(_cameraModel == Cred2)
			{
				_credTwo = true;
				_credThree = false;
			}
			else if(_cameraModel == Cred3)
			{
				_credTwo = false;
				_credThree = true;
			}

			_refreshThread = new FliThreadImp(this);
			_refreshThread->activate();
		}
	}

	createProperties();
}

//---------------------------------------------------------------
FirstLightImagingCameras::~FirstLightImagingCameras()
{
	_refreshThread->exit();
	_refreshThread->wait();
	delete _refreshThread;
	FliSdk_exit();
}

//---------------------------------------------------------------
void FirstLightImagingCameras::createProperties()
{
	CPropertyAction* pAct = nullptr;

	std::vector<std::string> boolvalues;
	boolvalues.push_back("0");
	boolvalues.push_back("1");

	CreateStringProperty("Camera Status", "", true, nullptr);

	if (_credTwo || _credThree)
	{
		pAct = new CPropertyAction(this, &FirstLightImagingCameras::onMaxExposure);
		CreateFloatProperty("MaximumExposureMs", _maxExposure, true, pAct, false);

		pAct = new CPropertyAction(this, &FirstLightImagingCameras::onMaxFps);
		CreateFloatProperty("MaximumFps", _maxFps, true, pAct, false);

		pAct = new CPropertyAction(this, &FirstLightImagingCameras::onSetMaxExposure);
		CreateIntegerProperty("Set max exposure", 0, false, pAct);
		SetAllowedValues("Set max exposure", boolvalues);

		pAct = new CPropertyAction(this, &FirstLightImagingCameras::onSetMaxFps);
		CreateIntegerProperty("Set max fps", 0, false, pAct);
		SetAllowedValues("Set max fps", boolvalues);

		pAct = new CPropertyAction(this, &FirstLightImagingCameras::onBuildBias);
		CreateIntegerProperty("Build bias", 0, false, pAct);
		SetAllowedValues("Build bias", boolvalues);

		pAct = new CPropertyAction(this, &FirstLightImagingCameras::onApplyBias);
		CreateIntegerProperty("Apply bias", 0, false, pAct);
		SetAllowedValues("Apply bias", boolvalues);

		pAct = new CPropertyAction(this, &FirstLightImagingCameras::onShutdown);
		CreateIntegerProperty("Shutdown", 0, false, pAct);
		SetAllowedValues("Shutdown", boolvalues);

		pAct = new CPropertyAction(this, &FirstLightImagingCameras::onFps);
		CreateFloatProperty("FPS", _fps, false, pAct, false);

		pAct = new CPropertyAction(this, &FirstLightImagingCameras::onCameraChange);
		CreateStringProperty("Cameras", _listOfCameras[0], false, pAct);
		std::vector<std::string> values;
		for(int i = 0; i < _nbCameras; ++i)
			values.push_back(std::string(_listOfCameras[i]));
		SetAllowedValues("Cameras", values);
	}

	pAct = new CPropertyAction(this, &FirstLightImagingCameras::onSendCommand);
	CreateStringProperty("Send command", "", false, pAct);

	if (_credTwo)
	{
		pAct = new CPropertyAction(this, &FirstLightImagingCameras::onApplySensorTemp);
		CreateFloatProperty("Set sensor temp", _sensorTemp, false, pAct);

		CreateFloatProperty("Sensor Temp", 0.0, true, nullptr, false);
	}
	else
		CreateFloatProperty("Set sensor temp", 0, true, nullptr);

	pAct = new CPropertyAction(this, &FirstLightImagingCameras::onDetectCameras);
	CreateIntegerProperty("Detect cameras", 0, false, pAct);
	SetAllowedValues("Detect cameras", boolvalues);

	pAct = new CPropertyAction(this, &FirstLightImagingCameras::onBinning);
	CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
}

//---------------------------------------------------------------
void FirstLightImagingCameras::imageReceived(const uint8_t* image)
{
	unsigned int w = GetImageWidth();
	unsigned int h = GetImageHeight();
	unsigned int b = GetImageBytesPerPixel();

	Metadata md;
	char label[MM::MaxStrLength];
	GetLabel(label);
	md.put("Camera", label);
	md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString((long)w));
	md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString((long)h));

	MM::Core* core = GetCoreCallback();

	int ret = core->InsertImage(this, image, w, h, b, 1, md.Serialize().c_str(), false);
	if (ret == DEVICE_BUFFER_OVERFLOW)
	{
		// do not stop on overflow - just reset the buffer
		core->ClearImageBuffer(this);
		core->InsertImage(this, image, w, h, b, 1, md.Serialize().c_str(), false);
	}

	_numImages--;
	if (!_numImages)
		StopSequenceAcquisition();
}

//---------------------------------------------------------------
int FirstLightImagingCameras::Initialize()
{
	if (_initialized)
		return DEVICE_OK;

	_initialized = true;

	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::Shutdown()
{
	_initialized = false;
	return DEVICE_OK;
}

//---------------------------------------------------------------
void FirstLightImagingCameras::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, _cameraName.c_str());
}

//---------------------------------------------------------------
long FirstLightImagingCameras::GetImageBufferSize() const
{
	uint16_t width, height;
	FliSdk_getCurrentImageDimension(&width, &height);
	return width * height * 2;
}

//---------------------------------------------------------------
unsigned FirstLightImagingCameras::GetBitDepth() const
{
	return 16;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::GetBinning() const
{
	return 1;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::SetBinning(int binSize)
{
	(void)binSize;
	return DEVICE_OK;
}

//---------------------------------------------------------------
void FirstLightImagingCameras::SetExposure(double exp_ms)
{
	if (_credTwo)
		Cred2_setTint(exp_ms / 1000.0);
	else if (_credThree)
		Cred3_setTint(exp_ms / 1000.0);
}

//---------------------------------------------------------------
double FirstLightImagingCameras::GetExposure() const
{
	double tint;

	if (_credTwo)
		Cred2_getTint(&tint);
	else if (_credThree)
		Cred3_getTint(&tint);

	return tint*1000;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
	CroppingData_C cropping;
	cropping.col1 = roundUp(x, 32);
	cropping.col2 = roundUp(x + xSize, 32)-1;
	cropping.row1 = roundUp(y, 4);
	cropping.row2 = roundUp(y + ySize,4)-1;
	FliSdk_setCroppingState(true, cropping);
	_croppingEnabled = true;
	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
	if (!_croppingEnabled)
	{
		x = 0;
		xSize = GetImageWidth();
		y = 0;
		ySize = GetImageHeight();
	}
	else
	{
		CroppingData_C cropping;
		bool enabled;
		FliSdk_getCroppingState(&enabled, &cropping);
		x = cropping.col1;
		xSize = cropping.col2 - x;
		y = cropping.row1;
		ySize = cropping.row2 - y;
	}
	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::ClearROI()
{
	CroppingData_C cropping;
	cropping.col1 = 0;
	cropping.col2 = 0;
	cropping.row1 = 0;
	cropping.row2 = 0;
	FliSdk_setCroppingState(false, cropping);
	_croppingEnabled = false;
	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::IsExposureSequenceable(bool& isSequenceable) const
{
	(void)isSequenceable;
	return 0;
}

//---------------------------------------------------------------
const unsigned char* FirstLightImagingCameras::GetImageBuffer()
{
	return FliSdk_getRawImage(-1);
}

//---------------------------------------------------------------
unsigned FirstLightImagingCameras::GetImageWidth() const
{
	uint16_t width, height;
	FliSdk_getCurrentImageDimension(&width, &height);
	return width;
}

//---------------------------------------------------------------
unsigned FirstLightImagingCameras::GetImageHeight() const
{
	uint16_t width, height;
	FliSdk_getCurrentImageDimension(&width, &height);
	return height;
}

//---------------------------------------------------------------
unsigned FirstLightImagingCameras::GetImageBytesPerPixel() const
{
	return 2;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::SnapImage()
{
	if (FliSdk_isStarted())
		FliSdk_stop();
	FliSdk_enableGrabN(10);
	FliSdk_start();
#ifdef WIN32
		Sleep(50);
#else
		usleep(50 * 1000);
#endif
	return DEVICE_OK;
}

//---------------------------------------------------------------
void onImageReceived(const uint8_t* image, void* ctx)
{
	FirstLightImagingCameras* context = static_cast<FirstLightImagingCameras*>(ctx);
	context->imageReceived(image);
}

//---------------------------------------------------------------
int FirstLightImagingCameras::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
	(void)stopOnOverflow;
	_callbackCtx = FliSdk_addCallbackNewImage(onImageReceived, (uint16_t)_fpsTrigger, this);
	if (FliSdk_isStarted())
		FliSdk_stop();
	FliSdk_disableGrabN();
	FliSdk_start();
	_fpsTrigger = interval_ms == 0 ? 0 : 1.0 / interval_ms;
	_numImages = numImages;
	_isCapturing = true;
	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::StopSequenceAcquisition()
{
	FliSdk_stop();
	FliSdk_removeCallbackNewImage(_callbackCtx);
	_isCapturing = false;
	return DEVICE_OK;
}

//---------------------------------------------------------------
void FirstLightImagingCameras::OnThreadExiting() throw()
{
}

//---------------------------------------------------------------
bool FirstLightImagingCameras::IsCapturing()
{
	return _isCapturing;
}

//---------------------------------------------------------------
void FirstLightImagingCameras::refreshValues()
{
	if (_credTwo)
	{
		double val;
		Cred2_getTempSnake(&val);
		OnPropertyChanged("Sensor Temp", std::to_string((long double)val).c_str());
		double consigne;
		Cred2_getTempSnakeSetPoint(&consigne);
		OnPropertyChanged("Set sensor temp", std::to_string((long double)consigne).c_str());
		char status[200];
		char diag[200];
		FliCamera_getStatusDetailed(status, diag);
		std::string s;
		s.append(status);
		s.append("-");
		s.append(diag);
		OnPropertyChanged("Camera Status", s.c_str());
	}
	else if (_credThree)
	{
		char status[200];
		char diag[200];
		FliCamera_getStatusDetailed(status, diag);
		std::string s;
		s.append(status);
		s.append("-");
		s.append(diag);
		OnPropertyChanged("Camera Status", s.c_str());
	}
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onMaxExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		double tintMin;
		if (_credTwo)
			Cred2_getTintRange(&tintMin, &_maxExposure);
		else if (_credThree)
			Cred3_getTintRange(&tintMin, &_maxExposure);

		pProp->Set(_maxExposure *1000);
	}
	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onMaxFps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		FliCamera_getFpsMax(&_maxFps);
		pProp->Set(_maxFps);
	}
	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onFps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		FliCamera_getFps(&_fps);
		pProp->Set(_fps);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(_fps);
		FliCamera_setFps(_fps);
	}
	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onCameraChange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		std::string camera;
		pProp->Get(camera);

		FliSdk_setCamera(camera.c_str());
		FliSdk_update();

		_cameraModel = FliSdk_getCameraModel();

		if(_cameraModel == Cred2)
		{
			_credTwo = true;
			_credThree = false;
		}
		else if(_cameraModel == Cred3)
		{
			_credTwo = false;
			_credThree = true;
		}
	}

	return 0;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onDetectCameras(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		std::string detect;
		pProp->Get(detect);

		_refreshThread->exit();
		_refreshThread->wait();
		delete _refreshThread;

		if (detect == "1")
		{
			_credTwo = false;
			_credThree = false;
			uint8_t nbGrabbers = 0;
			FliSdk_detectGrabbers(&nbGrabbers);

			if(nbGrabbers > 0)
			{
				_nbCameras = 0;
				_listOfCameras = FliSdk_detectCameras(&_nbCameras);

				if(_nbCameras > 0)
				{
					FliSdk_setCamera(_listOfCameras[0]);
					FliSdk_update();
					_cameraModel = FliSdk_getCameraModel();

					if(_cameraModel == Cred2)
					{
						_credTwo = true;
						_credThree = false;
					}
					else if(_cameraModel == Cred3)
					{
						_credTwo = false;
						_credThree = true;
					}

					_refreshThread = new FliThreadImp(this);
					_refreshThread->activate();
				}
			}

			createProperties();
		}
	}
	else if (eAct == MM::BeforeGet)
	{
		double enabled = 0;
		pProp->Set(enabled);
	}

	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onSendCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	static char response[200];
	if (eAct == MM::AfterSet)
	{
		std::string command;
		pProp->Get(command);
		command.append("\n");
		FliCamera_sendCommand(command.c_str(), response);
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(response);
	}

	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_ERR;
	switch (eAct)
	{
	case MM::AfterSet:
		ret = DEVICE_OK;
		break;
	case MM::BeforeGet:
	{
		ret = DEVICE_OK;
		double bin = 2;
		pProp->Set(bin);
		break;
	}
	default:
		break;
	}
	return ret;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onSetMaxExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_ERR;
	switch (eAct)
	{
	case MM::AfterSet:
		ret = DEVICE_OK;
		if (_credTwo)
			Cred2_setTint(_maxExposure);
		else if (_credThree)
			Cred3_setTint(_maxExposure);
		break;
	case MM::BeforeGet:
	{
		ret = DEVICE_OK;
		double enabled = 0;
		pProp->Set(enabled);
		break;
	}
	default:
		break;
	}
	return ret;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onSetMaxFps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_ERR;
	switch (eAct)
	{
	case MM::AfterSet:
		ret = DEVICE_OK;
		FliCamera_setFps(_maxFps);
		break;
	case MM::BeforeGet:
	{
		ret = DEVICE_OK;
		double enabled = 0;
		pProp->Set(enabled);
		break;
	}
	default:
		break;
	}
	return ret;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onBuildBias(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_ERR;
	switch (eAct)
	{
	case MM::AfterSet:
		ret = DEVICE_OK;
		FliCamera_buildBias();
		break;
	case MM::BeforeGet:
	{
		ret = DEVICE_OK;
		double enabled = 0;
		pProp->Set(enabled);
		break;
	}
	default:
		break;
	}
	return ret;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onApplyBias(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_ERR;
	switch (eAct)
	{
	case MM::AfterSet:
	{
		ret = DEVICE_OK;
		double val;
		pProp->Get(val);
		bool enabled = val == 0 ? false : true;
		FliCamera_enableBias(enabled);
		break;
	}
	case MM::BeforeGet:
	{
		ret = DEVICE_OK;
		bool enabled = false;
		FliCamera_getBiasState(&enabled);
		pProp->Set((double)enabled);
		break;
	}
	default:
		break;
	}
	return ret;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onApplySensorTemp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_ERR;
	switch (eAct)
	{
	case MM::AfterSet:
	{
		ret = DEVICE_OK;
		double val;
		pProp->Get(val);
		Cred2_setSensorTemp(val);
		break;
	}
	case MM::BeforeGet:
	{
		ret = DEVICE_OK;
		break;
	}
	default:
		break;
	}
	return ret;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onShutdown(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = DEVICE_ERR;
	switch (eAct)
	{
	case MM::AfterSet:
	{
		ret = DEVICE_OK;
		double val;
		pProp->Get(val);
		FliCamera_shutDown();
		break;
	}
	case MM::BeforeGet:
	{
		ret = DEVICE_OK;
		break;
	}
	default:
		break;
	}
	return ret;
}

//---------------------------------------------------------------
FliThreadImp::FliThreadImp(FirstLightImagingCameras* camera) : _camera(camera), _exit(false)
{

}

//---------------------------------------------------------------
FliThreadImp::~FliThreadImp()
{

}

//---------------------------------------------------------------
void FliThreadImp::exit()
{
	MMThreadGuard g(_lock);
	_exit = true;
}

//---------------------------------------------------------------
bool FliThreadImp::mustExit()
{
	MMThreadGuard g(_lock);
	return _exit;
}

//---------------------------------------------------------------
int FliThreadImp::svc()
{
	while(!mustExit())
	{
		_camera->refreshValues();
		CDeviceUtils::SleepMs(1000);
	}

	return 0;
}