///////////////////////////////////////////////////////////////////////////////
// FILE:          TeensySLM.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Arduino adapter for sending serial commands as a property value.  Needs accompanying firmware
// COPYRIGHT:     University of California, Berkeley, 2016
// LICENSE:       LGPL
// 
// AUTHOR:        Henry Pinkard, hbp@berkeley.edu, 12/13/2016         
//
//

#include "TeensySLM.h"
#include "TeensyShutter.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <cstdio>

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif


const char* g_DeviceNameTeensySLM = "Teensy-SLM";
const char* g_DeviceNameTeensySLMVirtualShutter = "Teensy-SLM Virtual Shutter";

	const char * g_Keyword_Width = "Width";
	const char * g_Keyword_Height = "Height";
	const char * g_Keyword_Pattern = "SLM_Pattern";
	const char * g_Keyword_ManualVoltage = "ManualVoltage";
	const char * g_Keyword_GlobalRotation = "Pattern Rotation";
	const char * g_Keyword_GlobalTilt = "Pattern Tilt";

	 const unsigned char CTeensySLM::GLOBAL_HEADER[] = {148,169};
	 const unsigned char CTeensySLM::SHUTTER_HEADER[] = {19,119};
    const unsigned char CTeensySLM::PATTERN_HEADER[] = {27,44};
	const unsigned char CTeensySLM::COMMAND_SUCCESS[] = {176};

	const int MAX_VOLTAGE = 255;
	const int MIN_VOLTAGE = 0;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceNameTeensySLM, MM::SLMDevice, "Teensy Spatial Light Modulator");
   RegisterDevice(g_DeviceNameTeensySLMVirtualShutter, MM::ShutterDevice, "Virtual Shutter using Teensy Spatial Light Modulator");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameTeensySLM) == 0)
   {
      return new CTeensySLM;
   }
    if (strcmp(deviceName, g_DeviceNameTeensySLMVirtualShutter) == 0)
   {
      return new TeensyShutter;
   }
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


///////////////////////////////////////////////////////////////////////////////
// CTeensySLM implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

CTeensySLM::CTeensySLM() : initialized_(false), name_(g_DeviceNameTeensySLM), pixels_(0), width_(16), height_(16),
	shutterOpen_(false), manualVoltage_(0), rotation_(0), tilt_(0)
{
   portAvailable_ = false;

   InitializeDefaultErrorMessages();
   SetErrorText(ERR_COMMAND_SUCCESS_MISSING, "Teensy didn't return command success code");
 
   //initialization property: port name
    CPropertyAction* pAct = new CPropertyAction(this, &CTeensySLM::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
   //Height
   CPropertyAction* pAct4 = new CPropertyAction(this, &CTeensySLM::OnHeight);
   CreateProperty(g_Keyword_Height, "1", MM::Integer, false, pAct4,true);
   //Width
   CPropertyAction* pAct5 = new CPropertyAction(this, &CTeensySLM::OnWidth);
   CreateProperty(g_Keyword_Width, "1", MM::Integer, false, pAct5,true);
}

CTeensySLM::~CTeensySLM()
{
	delete[] pixels_;
   Shutdown();
}

bool CTeensySLM::Busy() 
{
	return false;
}

void CTeensySLM::GetName(char* name) const 
{
CDeviceUtils::CopyLimitedString(name, g_DeviceNameTeensySLM);
}

int CTeensySLM::Initialize()
{
   if (initialized_)
     return DEVICE_OK;
   pixels_ = new unsigned char[width_*height_];
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameTeensySLM, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Teensy Spatial Light Modulator", MM::String, true);
   assert(DEVICE_OK == ret);

   //Shutter
   CPropertyAction* pAct3 = new CPropertyAction(this, &CTeensySLM::OnShutterOpen);
   CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct3);

   AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   //manual voltageControl
   CPropertyAction* pAct4 = new CPropertyAction (this, &CTeensySLM::OnManualVoltage);
   CreateProperty(g_Keyword_ManualVoltage, "0.0", MM::Integer, false, pAct4);
   SetPropertyLimits(g_Keyword_ManualVoltage, MIN_VOLTAGE, MAX_VOLTAGE);

   //Tilt
   CPropertyAction* pAct5 = new CPropertyAction (this, &CTeensySLM::OnRotation);
   CreateProperty(g_Keyword_GlobalRotation, "0.0", MM::Float, false, pAct5);
   SetPropertyLimits(g_Keyword_GlobalRotation, 0, 360);

   CPropertyAction* pAct6 = new CPropertyAction (this, &CTeensySLM::OnTilt);
   CreateProperty(g_Keyword_GlobalTilt, "0.0", MM::Float, false, pAct6);
   SetPropertyLimits(g_Keyword_GlobalTilt, -89, 89);


   //store pattern
   CPropertyAction* pAct7 = new CPropertyAction(this, &CTeensySLM::OnPattern);
   CreateProperty(g_Keyword_Pattern, "", MM::String, true, pAct7);

   // Check that we have a controller:
   PurgeComPort(port_.c_str());


   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
   initialized_ = true;

   return DEVICE_OK;
}

int CTeensySLM::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

//SLM functions
   int CTeensySLM::SetImage(unsigned char * pixels) {
	   memcpy(pixels_ , pixels, width_*height_);
	  return DEVICE_OK;
   }

      /**
       * Command the SLM to display the loaded image.
       */
   int CTeensySLM::DisplayImage() {
		return WriteImage(0);
   }

   int CTeensySLM::WriteImage(bool applyImmediately) {
	   //copy pattern to metadata
	   std::stringstream ss;
	   for (int i = 0; i < width_*height_; i++) {
		   ss << std::to_string((unsigned long long)(pixels_[i])) << "-";
		   if (i == width_ * height_ - 1) {
			  break; //don't include trailing dash
		   }
	   }
	   patternString_ = ss.str();
	   GetCoreCallback()->OnPropertyChanged(this,g_Keyword_Pattern,patternString_.c_str());  
		  //send pattern to Teensy
		  PurgeComPort(port_.c_str());
		  //write header that teensy firmware expects
        unsigned char* allData = new unsigned char[5 + width_*height_];
		allData[0] = GLOBAL_HEADER[0];
		allData[1] = GLOBAL_HEADER[1];
		allData[2] = PATTERN_HEADER[0];
		allData[3] = PATTERN_HEADER[1];
		allData[4] = applyImmediately ? 1 : 0;
		//copy in pattern
		memcpy(allData+5,pixels_,width_*height_);

		int ret =  WriteToComPort(port_.c_str(), allData, 5 +width_*height_);
		delete[] allData;
		if (ret != DEVICE_OK){
			return DEVICE_ERR;
		}
		return readCommandSuccess();
	  }

      /**
       * Command the SLM to display one 8-bit intensity.
       */
	  int CTeensySLM::SetPixelsTo(unsigned char intensity) {
		  lastModVal_ = intensity;
		  if (tilt_ == 0) {
			  memset(pixels_,intensity,width_*height_);
		  } else {
			 ApplyModulationGradient();
		  }
		  return DEVICE_OK;
	  }

	  void CTeensySLM::ApplyModulationGradient() {
		  //apply a gradient to the image
		  //caluclate axis of gradient
		  double angle = rotation_ / 180.0 * 3.1415;
		  double gradNormX = cos(angle);
		  double gradNormY = sin(angle);
		  //apply rotation and tilt to pattern
		  for (int x = 0; x < width_; x++) {
			  for (int y = 0; y < height_; y++) {
				  double xZeroed = x + 0.5 - width_ / 2;
				  double yZeroed = y + 0.5 - width_ / 2;
				  //convert to distance along gradient axis
				  double projDistance = gradNormX * xZeroed + gradNormY*yZeroed;
				  double gain = (projDistance / (double) width_ / 2) *tan( 3.14 * tilt_ / 180.0) + 1;
				  pixels_[x + y*width_] = (unsigned char) min(255,max(0,gain*lastModVal_));
			  }
		  }
	  }


	  int CTeensySLM::EnableShutter(bool open) {
		PurgeComPort(port_.c_str());
		  unsigned char allData[5];
		allData[0] = GLOBAL_HEADER[0];
		allData[1] = GLOBAL_HEADER[1];
		allData[2] = SHUTTER_HEADER[0];
		allData[3] = SHUTTER_HEADER[1];
		allData[4] = open ? 1 : 0; 
		int ret =  WriteToComPort(port_.c_str(), allData, 5);
		if (ret != DEVICE_OK){
			PurgeComPort(port_.c_str());
			return DEVICE_ERR;
		}
		return readCommandSuccess();
	  }

	  int CTeensySLM::readCommandSuccess() {
		  MM::MMTime startTime = GetCurrentMMTime();
		  unsigned long bytesRead = 0;
		  unsigned char answer[1];
		  int ret;
		  while ((bytesRead < 1) && ( (GetCurrentMMTime() - startTime).getMsec() < 1000)) {
			  ret = ReadFromComPort(port_.c_str(),answer,1,bytesRead);
			  if (ret != DEVICE_OK)
				  return ret;
		  }
		  if (answer[0] != COMMAND_SUCCESS[0]){
			  PurgeComPort(port_.c_str());
			  return ERR_COMMAND_SUCCESS_MISSING;
		  }
		  return DEVICE_OK;
	  }


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CTeensySLM::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(port_);
      portAvailable_ = true;
   }
   return DEVICE_OK;
}

int CTeensySLM::OnShutterOpen(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set( shutterOpen_);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(shutterOpen_);
	  int ret = EnableShutter(shutterOpen_);
	  return ret;
   }
   return DEVICE_OK;
}

int CTeensySLM::OnWidth(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set( width_);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(width_);
	  delete[] pixels_;
	  pixels_ = new unsigned char[width_*height_];
   }
   return DEVICE_OK;
}


int CTeensySLM::OnHeight(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(height_);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(height_);
	  delete[] pixels_;
	  pixels_  = new unsigned char[width_*height_];
   }
   return DEVICE_OK;
}


   int CTeensySLM::OnPattern(MM::PropertyBase* pProp, MM::ActionType pAct) 
   {
     if (pAct == MM::BeforeGet)
   {
	   pProp->Set(patternString_.c_str());
   }
   return DEVICE_OK;
   }

   int CTeensySLM::OnManualVoltage(MM::PropertyBase* pProp, MM::ActionType pAct) 
   {
	   if (pAct == MM::BeforeGet)
	   {
		   pProp->Set(manualVoltage_);
	   }
	   if (pAct == MM::AfterSet)
	   {
		   pProp->Get(manualVoltage_);
		   SetPixelsTo((unsigned char)manualVoltage_);
		   return WriteImage(true);
	   }
	   return DEVICE_OK;
   }

    int CTeensySLM::OnRotation(MM::PropertyBase* pProp, MM::ActionType pAct) 
   {
	   if (pAct == MM::BeforeGet)
	   {
		   pProp->Set(rotation_);
	   }
	   if (pAct == MM::AfterSet)
	   {
		   pProp->Get(rotation_);
		   ApplyModulationGradient();
		   DisplayImage();
	   }
	   return DEVICE_OK;
   }

	 int CTeensySLM::OnTilt(MM::PropertyBase* pProp, MM::ActionType pAct) 
   {
	   if (pAct == MM::BeforeGet)
	   {
		   pProp->Set(tilt_);
	   }
	   if (pAct == MM::AfterSet)
	   {
		   pProp->Get(tilt_);
		   ApplyModulationGradient();
		   DisplayImage();
	   }
	   return DEVICE_OK;
   }
