///////////////////////////////////////////////////////////////////////////////
// FILE:          BiostepEagleIce.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
// ----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for biostep EagleIce
// COPYRIGHT:     biostep, 2013
// AUTHOR:        Jens Gläser
// License:       LGPL
//
//

#include "BiostepEagleIce.h"
#include <string>
#include "MMDeviceConstants.h"
#include <sstream>

using namespace std;
/////////////////////////////////////////////////////////////////////////////
// Global strings
const char* g_CameraDeviceName = "biostep EagleIce";

//////////////////////////////////////////////////////////////////////////////
// Error codes
#define ERR_NO_CAMERA      200001

const byte BitsPerPixel = 16;
MM::MMTime sequenceStartTime_;
bool stopOnOverFlow_;
bool liveView_;

//////////////////////////////////////////////////////////////////////////////
// Module Interface
MODULE_API void InitializeModuleData()
{
	AddAvailableDeviceName(g_CameraDeviceName, "biostep EagleIce");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	// decide which device class to create based on the deviceName parameter
	if (strcmp(deviceName, g_CameraDeviceName) == 0)
	{
		// create camera
		return new EagleIce();
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

EagleIce* g_Self;

EagleIce::EagleIce()
{
	SetErrorText(ERR_NO_CAMERA, "No biostep EagleIce Camera found");
	thd_ = new biThread(this);
}

EagleIce::~EagleIce()
{
	//Shutdown();
}

void EagleIce::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
}

int EagleIce::Shutdown()
{
	if(_camfound == true)
		g_device->Close();
	return 0;
}

void _stdcall CallbackFunction(char* serial, DWORD ID)
{
	/*EagleIce* mmEI = (EagleIce*)g_Self;*/
	

	g_Self->g_device->Send_PictureData(g_Self->imgx);
	
	for(int i = 0; i<g_Self->lastResolution.X*g_Self->lastResolution.Y;i++)
	{
		g_Self->buffer[i*2+1] = (char)(g_Self->imgx[i] >> 8);
		g_Self->buffer[i*2] = (char)(g_Self->imgx[i]);
	}

	g_Self->m_img.SetPixels(g_Self->buffer);
	g_Self->InsertImage();
}

int EagleIce::Initialize()
{
	EI_Status _status = EI_SDK_ErrorList__No_Error;

	_status = getFirstCam();

	if(_status != EI_SDK_ErrorList__No_Error)
		return _status;
	char* serial=g_device->GetSerial();
	int nRet = CreateProperty("Serial", serial, MM::String, true);

	char* fw = new char[256];
	_status=g_device->Get_Firmware_Version(fw);
	nRet = CreateProperty("Firmware", fw, MM::String, true);

	char* id = new char[256];
	_status=g_device->Get_Camera_ID(id);
	nRet = CreateProperty("CameraID", id, MM::String, true);

	byte cooling = 0;
	_status=g_device->Get_CoolingVersion(&cooling);
	char* cool = "true";
	if(cooling==0)
		cool = "false";
	nRet = CreateProperty("Cooling", cool, MM::String, true);

	byte shutter = 0;
	_status=g_device->Get_Shutter(&shutter);
	char* shut = "true";
	if(shutter==0)
		shut = "false";
	nRet = CreateProperty("has Shutter", shut, MM::String, true);

	nRet = CreateProperty(MM::g_Keyword_Exposure, "10", MM::Integer, false);
	EI_Exposure exp;
	g_device->Get_ExposureSteps(&exp);
	SetPropertyLimits(MM::g_Keyword_Exposure, exp.min, exp.max);

	nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::String, false);
	SetAllowedBinning();

	g_Self = this;

	g_device->SetCallBackPointer(&CallbackFunction);

	m_img.Resize(2048,2048,2);
	EI_Dimension* dim = new EI_Dimension;
	g_device->Get_Dimension(dim);
	lastResolution.Y = dim->ABY;
	lastResolution.X = dim->ABX;

	EI_SpeedupAndBinning* speed = new EI_SpeedupAndBinning;
	g_device->Get_Speedup(speed);
	nRet = CreateProperty("SpeedUp","1",MM::Integer,false);
	SetPropertyLimits("SpeedUp",1,speed->X);

	if(cooling >0)
	{
		CPropertyAction *pAct = new CPropertyAction (this, &EagleIce::OnTemp);
	nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "0",MM::Float,false,pAct);
	EI_TempRange* tr = new EI_TempRange;
	nRet=g_device->Get_TempRange(tr);
	if(nRet == DEVICE_OK)
	SetPropertyLimits(MM::g_Keyword_CCDTemperature, tr->min, tr->max);

	nRet = CreateProperty("curTemp","0.0",MM::Float,false);
	
	pAct = new CPropertyAction (this, &EagleIce::OnCoolState);
	CreateProperty("Cooling On/Off","On",MM::String,false,pAct);
	std::vector<std::string> vec_Cool;
	vec_Cool.push_back("On");
	vec_Cool.push_back("Off");
	SetAllowedValues("Cooling On/Off",vec_Cool);
	}

	temp = new double;
	thd_->Start();

	return 0;
}

EI_Status EagleIce::getFirstCam()
{
	EI_Status _status = EI_SDK_ErrorList__No_Error;
	DWORD Count = 0;
	_camfound = false;

	_status = Get_NumberOfCams(&Count);
	if(_status != EI_SDK_ErrorList__No_Error )
		return _status;
	if(Count == 0)
		return ERR_NO_CAMERA;

	EI_CamList* camlist = new EI_CamList[Count];
	_status = Get_CamList(camlist, &Count);
	if(_status != EI_SDK_ErrorList__No_Error)
		return _status;
	g_device = new EI_Device(camlist[0].SerialNumber);
	_camfound = true;
	return 0;
}

int EagleIce::SnapImage()
{
	EI_Status _status = EI_SDK_ErrorList__No_Error;
	g_device->Set_IntegrationTime((UINT32)GetExposure());
	EI_SpeedupAndBinning bin;
	bin.X =(int)GetBinning();
	bin.Y =(int)GetBinning();
	g_device->Set_Binning(bin);
	
	long dat = 0;
	GetProperty("SpeedUp",dat);
	if(dat > 1)
	{	
	EI_SpeedupAndBinning spd;
	spd.X=(byte)dat;
	spd.Y=(byte)dat;
	g_device->Set_Speedup(spd);
	}


	_status = g_device->Start_Integration(&lastResolution);
	GetCoreCallback()->OnPropertiesChanged(this);
	_capturing = true;
	this->Busy();
	while(_capturing == true)
	{
		Sleep(1);
	}

	return _status;
}

int EagleIce::InsertImage()
{
	char label[MM::MaxStrLength];
	this->GetLabel(label);
	Metadata md;
	md.put("Camera", label);
	int ret = GetCoreCallback()->InsertImage(this, GetImageBuffer(), GetImageWidth(),
		GetImageHeight(), GetImageBytesPerPixel(),
		md.Serialize().c_str());
	g_Self->_capturing = false;
	if(liveView_ == 1)
		g_device->Start_Integration(&lastResolution);
	if (ret == DEVICE_BUFFER_OVERFLOW)
	{
		// do not stop on overflow - just reset the buffer
		GetCoreCallback()->ClearImageBuffer(this);
		return GetCoreCallback()->InsertImage(this, GetImageBuffer(), GetImageWidth(),
			GetImageHeight(), GetImageBytesPerPixel(),
			md.Serialize().c_str());
	} else
		return ret;


}

unsigned EagleIce::GetBitDepth() const
{return BitsPerPixel;}

unsigned EagleIce::GetImageWidth()const
{return lastResolution.X;}

unsigned EagleIce::GetImageHeight()const
{return lastResolution.Y;}

double EagleIce::GetExposure() const
{
	char buf[MM::MaxStrLength]; 
	int ret = GetProperty(MM::g_Keyword_Exposure, buf);
	if (ret != DEVICE_OK)
		return 0.0;
	return atof(buf);
}; 

/**
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void EagleIce::SetExposure(double exp)
{
	SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
	GetCoreCallback()->OnExposureChanged(this, exp);;
}

/**
* Returns the current binning factor.
* Required by the MM::Camera API.
*/
int EagleIce::GetBinning() const
{
	char buf[MM::MaxStrLength]; 
	int ret = GetProperty(MM::g_Keyword_Binning, buf);
	if (ret != DEVICE_OK)
		return 1;
	return atoi(buf);
}; 

/**
* Sets binning factor.
* Required by the MM::Camera API.
*/
int EagleIce::SetBinning(int binF)
{
	return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binF));
}

string IntToStr(int number)
{
	stringstream ss;
	ss << number;
	return ss.str();
}

int EagleIce::SetAllowedBinning() 
{
	EI_SpeedupAndBinning binning;
	g_device->Get_Binning(&binning);

	vector<string> binValues;

	for (int i = 1;i<=binning.X;i++)
	{	
		
		binValues.push_back(IntToStr(i));
	}
	LogMessage("Setting Allowed Binning settings", true);
	return SetAllowedValues(MM::g_Keyword_Binning, binValues);
}

int EagleIce::StartSequenceAcquisition(double interval) {
	return StartSequenceAcquisition(LONG_MAX, interval, false);    
}

int EagleIce::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
	if (IsCapturing())
		return DEVICE_CAMERA_BUSY_ACQUIRING;

	int ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK)
		return ret;
	sequenceStartTime_ = GetCurrentMMTime();
	liveView_ = 1;
	this->SnapImage();
	//thd_->Start(numImages,interval_ms);
	//stopOnOverflow_ = stopOnOverflow;
	return DEVICE_OK;
}

int EagleIce::StopSequenceAcquisition()
{
	liveView_ =0;
	return 0;
}

bool EagleIce::IsCapturing()
{
	return liveView_;
}

int EagleIce::ThreadRun()
{
	int ret;
	ret=g_device->Get_CcdTemp(temp);
	stringstream ss;
	ss << *temp;
	ret=GetCoreCallback()->OnPropertyChanged(this,"curTemp",ss.str().c_str());
	return ret;
}

void biThread::Stop()
{
	MMThreadGuard(this->stopLock_);
	stop_=true;
}

void biThread::Start()
{
	MMThreadGuard(this->stopLock_);
	stop_ = false;
	activate();
}

int biThread::svc(void) throw()
{
	int ret=DEVICE_ERR;
	try 
	{
		do
		{  
			Sleep(150);
			ret=camera_->ThreadRun();
		} while (DEVICE_OK == ret && !stop_);
		if (stop_)
			camera_->LogMessage("SeqAcquisition interrupted by the user\n");
	}catch(...){
		camera_->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
	}
	stop_=true;
	/*camera_->OnThreadExiting();*/
	return ret;
}

biThread::biThread(EagleIce* pCam)
	:stop_(true)
	,camera_(pCam)
{

};

biThread::~biThread()
{
};

int EagleIce::OnCoolState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string val;
	pProp->Get(val);
	if (val == "On")
	{
		return g_device->Set_Cooling(true);
	}
	else
	{
		return g_device->Set_Cooling(false);
	}
	
	
}

int EagleIce::OnTemp(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string val;
	pProp->Get(val);
	double set_temp = atof(val.c_str());
	return g_device->Set_CCDTemp(set_temp);	
}	

int EagleIce::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
	EI_ROI* roi = new EI_ROI;
	roi->start.x = x;
	roi->start.y = y;
	roi->width = xSize;
	roi->height = ySize;

	return g_device->Set_ROI(*roi);
}

int EagleIce::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
	return 0;
}

int EagleIce::ClearROI()
{
	EI_Dimension* dim = new EI_Dimension;
	g_device->Get_Dimension(dim); 

	EI_ROI* roi = new EI_ROI;
	roi->start.x = dim->SRX;
	roi->start.y = dim->SRY;
	roi->width = dim->ABX;
	roi->height = dim->ABY;

	return g_device->Set_ROI(*roi);
}
