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
void refreshThread(std::atomic<bool>& running, FirstLightImagingCameras* ctx)
{
	while (running)
	{
		ctx->refreshValues();
#ifdef WIN32
		Sleep(1000);
#else
		usleep(1000 * 1000);
#endif
	}
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
	_credTwo(nullptr),
	_credThree(nullptr)
{
	_fli.detectGrabbers();
	_listOfCameras = _fli.detectCameras();
	_fli.setBufferSize(400);

	if (_listOfCameras.size() > 0)
	{
		_fli.setCamera(_listOfCameras[0]);
		_fli.update();

		_credTwo = _fli.credTwo();
		_credThree = _fli.credThree();
	}

	createProperties();

	_threadRunning = true;
	_refreshThread = std::thread(refreshThread, ref(_threadRunning), this);
}

//---------------------------------------------------------------
FirstLightImagingCameras::~FirstLightImagingCameras()
{
	_threadRunning = false;
	_refreshThread.join();
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
		CreateStringProperty("Cameras", _listOfCameras[0].c_str(), false, pAct);
		SetAllowedValues("Cameras", _listOfCameras);
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
	CreateIntegerProperty(MM::g_Keyword_Binning, 1, true, pAct);
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

	int ret = core->InsertImage(this, image, w, h, b, 1, md.Serialize().c_str());
	if (ret == DEVICE_BUFFER_OVERFLOW)
	{
		// do not stop on overflow - just reset the buffer
		core->ClearImageBuffer(this);
		// don't process this same image again...
		core->InsertImage(this, image, w, h, b, 1, md.Serialize().c_str(), false);
	}

	_numImages--;
	if (!_numImages)
		StopSequenceAcquisition();
}

//---------------------------------------------------------------
double FirstLightImagingCameras::fpsTrigger()
{
	return _fpsTrigger;
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
	_fli.getCurrentImageDimension(width, height);
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
	return DEVICE_OK;
}

//---------------------------------------------------------------
void FirstLightImagingCameras::SetExposure(double exp_ms)
{
	if (_credTwo)
		_credTwo->setTint(exp_ms / 1000.0);
	else if (_credThree)
		_credThree->setTint(exp_ms / 1000.0);
}

//---------------------------------------------------------------
double FirstLightImagingCameras::GetExposure() const
{
	double tint;

	if (_credTwo)
		_credTwo->getTint(tint);
	else if (_credThree)
		_credThree->getTint(tint);

	return tint*1000;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
	CroppingData cropping;
	cropping.col1 = roundUp(x, 32);
	cropping.col2 = roundUp(x + xSize, 32)-1;
	cropping.row1 = roundUp(y, 4);
	cropping.row2 = roundUp(y + ySize,4)-1;
	FliSdkError fliError = _fli.setCroppingState(true, cropping);
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
		CroppingData cropping;
		bool enabled;
		FliSdkError fliError = _fli.getCroppingState(enabled, cropping);
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
	CroppingData cropping;
	FliSdkError fliError = _fli.setCroppingState(false, cropping);
	_croppingEnabled = false;
	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::IsExposureSequenceable(bool& isSequenceable) const
{
	return 0;
}

//---------------------------------------------------------------
const unsigned char* FirstLightImagingCameras::GetImageBuffer()
{
	return _fli.getRawImage();
}

//---------------------------------------------------------------
unsigned FirstLightImagingCameras::GetImageWidth() const
{
	uint16_t width, height;
	_fli.getCurrentImageDimension(width, height);
	return width;
}

//---------------------------------------------------------------
unsigned FirstLightImagingCameras::GetImageHeight() const
{
	uint16_t width, height;
	_fli.getCurrentImageDimension(width, height);
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
	if (_fli.isStarted())
		_fli.stop();
	_fli.enableGrabN(10);
	_fli.start();
#ifdef WIN32
		Sleep(50);
#else
		usleep(50 * 1000);
#endif
	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
	if (_fli.isStarted())
		_fli.stop();
	_fli.disableGrabN();
	_fli.start();
	_fpsTrigger = interval_ms == 0 ? 0 : 1.0 / interval_ms;
	_fli.addRawImageReceivedObserver(this);
	_numImages = numImages;
	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::StopSequenceAcquisition()
{
	_fli.stop();
	_fli.removeRawImageReceivedObserver(this);

	return DEVICE_OK;
}

//---------------------------------------------------------------
void FirstLightImagingCameras::OnThreadExiting() throw()
{
}

//---------------------------------------------------------------
void FirstLightImagingCameras::refreshValues()
{
	if (_credTwo)
	{
		double val;
		_credTwo->getTempSnake(val);
		OnPropertyChanged("Sensor Temp", std::to_string(val).c_str());
		double consigne;
		_credTwo->getTempSnakeSetpoint(consigne);
		OnPropertyChanged("Set sensor temp", std::to_string(consigne).c_str());
		std::string status;
		std::string diag;
		_fli.camera()->getStatusDetailed(status, diag);
		status.append("-");
		status.append(diag);
		OnPropertyChanged("Camera Status", status.c_str());
	}
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onMaxExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		double tintMin;
		if (_credTwo)
			_credTwo->getTintRange(tintMin, _maxExposure);
		else if (_credThree)
			_credThree->getTintRange(tintMin, _maxExposure);

		pProp->Set(_maxExposure *1000);
	}
	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onMaxFps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		_fli.camera()->getFpsMax(_maxFps);
		pProp->Set(_maxFps);
	}
	return DEVICE_OK;
}

//---------------------------------------------------------------
int FirstLightImagingCameras::onFps(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		double fps;
		_fli.camera()->getFps(_fps);
		pProp->Set(_fps);
	}
	else if (eAct == MM::AfterSet)
	{
		pProp->Get(_fps);
		_fli.camera()->setFps(_fps);
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

		if (_listOfCameras.size() > 0)
		{
			_fli.setCamera(camera);
			_fli.update();

			_credTwo = _fli.credTwo();
			_credThree = _fli.credThree();
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

		_threadRunning = false;
		_refreshThread.join();

		if (detect == "1")
		{
			_fli.detectGrabbers();
			_listOfCameras = _fli.detectCameras();

			CPropertyAction* pAct = nullptr;

			if (_listOfCameras.size() > 0)
			{
				_fli.setCamera(_listOfCameras[0]);
				_fli.update();

				_credTwo = _fli.credTwo();
				_credThree = _fli.credThree();

				createProperties();
			}

			_threadRunning = true;
			_refreshThread = std::thread(refreshThread, ref(_threadRunning), this);
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
	static std::string response;
	if (eAct == MM::AfterSet)
	{
		std::string command;
		pProp->Get(command);
		command.append("\n");
		response = "";
		_fli.camera()->sendCommand(command, response);
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(response.c_str());
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
			_credTwo->setTint(_maxExposure);
		else if (_credThree)
			_credThree->setTint(_maxExposure);
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
		_fli.camera()->setFps(_maxFps);
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
		_fli.camera()->buildBias();
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
		double enabled;
		pProp->Get(enabled);
		_fli.camera()->enableBias(enabled);
		break;
	}
	case MM::BeforeGet:
	{
		ret = DEVICE_OK;
		bool enabled = false;
		_fli.camera()->getBiasState(enabled);
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
		_credTwo->setSensorTemp(val);
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
		_fli.camera()->shutDown();
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
