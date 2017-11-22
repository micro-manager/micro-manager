// DESCRIPTION:   iPadSLM device adapter
// COPYRIGHT:     2009-2016 Regents of the University of California
//                2016 Open Imaging, Inc.
//
// AUTHOR:        Arthur Edelstein, arthuredelstein@gmail.com, 3/17/2009
//                Mark Tsuchida (refactor/rewrite), 2016
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

#include "iPadSLM.h"

#include "Monitors.h"
#include "OffscreenBuffer.h"
#include "SLMWindowThread.h"
#include "SleepBlocker.h"

#include "ModuleInterface.h"
#include "DeviceUtils.h"

#include <Windows.h>

#include <boost/lexical_cast.hpp>

#include <algorithm>
#include <cmath>
#include <vector>


const char* g_iPadSLMName = "iPadSLM";
const char* g_PropName_GraphicsPort = "GraphicsPort";
const char* g_PropName_TestModeWidth = "TestModeWidth";
const char* g_PropName_TestModeHeight = "TestModeHeight";
const char* g_PropName_Inversion = "Inversion";
const char* g_PropName_MonoColor = "MonochromeColor";
const char* g_PropName_DisplayWidthSize = "Display_Width";
const char* g_PropName_DisplayHeightSize = "Display_Height";
const char* g_Propname_CenterPointWidth = "Center_Width";
const char* g_Propname_CenterPointHeight = "Center_Height";
const char* g_Propname_IlluminationPattern = "IlluminationPattern";
const char* g_Propname_NumericalAperture = "NA";
const char* g_Propname_MinNA = "Minimum NA";
const char* g_Propname_MaxNA = "Maximum NA";
const char* g_Propname_Intensity = "Intensity";
const char* g_Propname_Resolution_H= "Resolution_Height";
const char* g_Propname_Resolution_W= "Resolution_Width";
const char* g_Propname_Type = "DPC_type";
const char* g_Propname_Distance = "Distance_mm";

enum {
   ERR_INVALID_TESTMODE_SIZE = 20000,
   ERR_CANNOT_DETACH,
   ERR_CANNOT_ATTACH,
   ERR_OFFSCREEN_BUFFER_UNAVAILABLE,
};


MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_iPadSLMName, MM::SLMDevice,
         "Spatial light modulator controlled through computer graphics output");
}


MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_iPadSLMName) == 0)
   {
      iPadSLM* piPadSLM = new iPadSLM(g_iPadSLMName);
      return piPadSLM;
   }

   return 0;
}


MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


iPadSLM::iPadSLM(const char* name) :
   name_(name),
   width_(0),
   height_(0),
   windowThread_(0),
   sleepBlocker_(0),
   shouldBlitInverted_(false),
   invert_(false),
   inversionStr_("Off"),
   monoColor_(SLM_COLOR_WHITE),
   monoColorStr_("White"),
   pattern_("Off"),
   distance_(50), 
   pixelsizeh_(0.155), pixelsizew_(0.155), centerx_(0), centery_(0), 
   DispWidth_(0), DispHeight_(0), minna_(0.25), maxna_(0.45), numa_(0.5), intensity_(255) //distances are in mm
{
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_INVALID_TESTMODE_SIZE,
         "Invalid test mode window size");
   SetErrorText(ERR_CANNOT_DETACH,
         "Failed to detach monitor from desktop");
   SetErrorText(ERR_CANNOT_ATTACH,
         "Failed to attach monitor to desktop");
   SetErrorText(ERR_OFFSCREEN_BUFFER_UNAVAILABLE,
         "Cannot set image (device uninitialized?)");

   availableMonitors_ = GetMonitorNames(true, false);

   // Create pre-init properties

   CreateStringProperty(g_PropName_GraphicsPort, "TestMode", false, 0, true);
   AddAllowedValue(g_PropName_GraphicsPort, "TestMode", 0);
   // Map available monitors 0 thru N to property data 1 thru N + 1
   for (unsigned i = 0; i < availableMonitors_.size(); ++i)
   {
      AddAllowedValue(g_PropName_GraphicsPort,
            availableMonitors_[i].c_str(), i + 1);
   }

   CreateIntegerProperty(g_PropName_TestModeWidth, 128, false, 0, true);
   CreateIntegerProperty(g_PropName_TestModeHeight, 128, false, 0, true);

}


iPadSLM::~iPadSLM()
{
   Shutdown();
}


void iPadSLM::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int iPadSLM::Initialize()
{
   Shutdown();

   //
   // Create post-init properties
   //

   int err = CreateStringProperty(MM::g_Keyword_Name, name_.c_str(), true);
   if (err != DEVICE_OK)
      return err;
   err = CreateStringProperty(MM::g_Keyword_Description,
         "SLM controlled by computer display adapter output", true);
   if (err != DEVICE_OK)
      return err;

   /*err = CreateStringProperty(g_PropName_Inversion, inversionStr_.c_str(), false,
         new CPropertyAction(this, &iPadSLM::OnInversion));
   if (err != DEVICE_OK)
      return err;
   AddAllowedValue(g_PropName_Inversion, "Off", 0);
   AddAllowedValue(g_PropName_Inversion, "On", 1);*/

   

   // Adjust the center point of the Display:
   // X- Width:
   CPropertyAction* pActwc = new CPropertyAction(this, &iPadSLM::OnWCenter);
   CreateProperty(g_Propname_CenterPointWidth, "" , MM::Integer, false, pActwc);
   // Y- Height:
   CPropertyAction* pActhc = new CPropertyAction(this, &iPadSLM::OnHCenter);
   CreateProperty(g_Propname_CenterPointHeight, "" , MM::Integer, false, pActhc);
    //******

   //Illumination Patterns:
   CPropertyAction* pActpat = new CPropertyAction(this, &iPadSLM::OnPattern);
   CreateProperty(g_Propname_IlluminationPattern, " " , MM::String, false, pActpat);
   AddAllowedValue(g_Propname_IlluminationPattern, "BrightField");
   AddAllowedValue(g_Propname_IlluminationPattern, "DarkField");
   AddAllowedValue(g_Propname_IlluminationPattern, "Annulus");
   AddAllowedValue(g_Propname_IlluminationPattern, "DPC");
   AddAllowedValue(g_Propname_IlluminationPattern, "Off");

   //Numerical Aperture:
   CPropertyAction* pActna = new CPropertyAction(this, &iPadSLM::Aperture);
   CreateProperty(g_Propname_NumericalAperture, "" , MM::Float, false, pActna);

   //Minimum and Maximum Apertures:
   CPropertyAction* pActmin = new CPropertyAction(this, &iPadSLM::OnMinNA);
   CreateProperty(g_Propname_MinNA, "" , MM::Float, false, pActmin);

   CPropertyAction* pActmax = new CPropertyAction(this, &iPadSLM::OnMaxNA);
   CreateProperty(g_Propname_MaxNA, "" , MM::Float, false, pActmax);

   //Intensity:
   CPropertyAction* pActint = new CPropertyAction(this, &iPadSLM::OnInt);
   CreateProperty(g_Propname_Intensity, "100", MM::Float, false, pActint);
   SetPropertyLimits(g_Propname_Intensity, 0.0, 255);

    //Height in inches:
   CPropertyAction* pActh = new CPropertyAction(this, &iPadSLM::OnHeight);
   CreateProperty(g_PropName_DisplayHeightSize, "15" , MM::Float, false, pActh);
   //Width in inches:
   CPropertyAction* pActw = new CPropertyAction(this, &iPadSLM::OnWidth);
   CreateProperty(g_PropName_DisplayWidthSize, "25", MM::Float, false, pActw);

   err = CreateStringProperty(g_PropName_MonoColor, monoColorStr_.c_str(), false,
         new CPropertyAction(this, &iPadSLM::OnMonochromeColor));
   if (err != DEVICE_OK)
      return err;
   AddAllowedValue(g_PropName_MonoColor, "White", SLM_COLOR_WHITE);
   AddAllowedValue(g_PropName_MonoColor, "Red", SLM_COLOR_RED);
   AddAllowedValue(g_PropName_MonoColor, "Green", SLM_COLOR_GREEN);
   AddAllowedValue(g_PropName_MonoColor, "Blue", SLM_COLOR_BLUE);
   AddAllowedValue(g_PropName_MonoColor, "Cyan", SLM_COLOR_CYAN);
   AddAllowedValue(g_PropName_MonoColor, "Magenta", SLM_COLOR_MAGENTA);
   AddAllowedValue(g_PropName_MonoColor, "Yellow", SLM_COLOR_YELLOW);

   //DPC type:
   CPropertyAction* pActype = new CPropertyAction(this, &iPadSLM::OnType);
   CreateProperty(g_Propname_Type, "" , MM::String, false, pActype);
   AddAllowedValue(g_Propname_Type, "Top");
   AddAllowedValue(g_Propname_Type, "Bottom");
   AddAllowedValue(g_Propname_Type, "Left");
   AddAllowedValue(g_Propname_Type, "Right");


   //Distance from the sample:

   CPropertyAction* pActdist = new CPropertyAction(this, &iPadSLM::OnDist);
   CreateProperty(g_Propname_Distance, "" , MM::Float, false, pActdist);
   //
   // Set up the monitor and window
   //

   long graphicsPortIndex;
   err = GetCurrentPropertyData(g_PropName_GraphicsPort, graphicsPortIndex);
   if (err != DEVICE_OK)
      return err;

   LONG x, y, w, h;
   std::vector<std::string> desktopMonitors;
   if (graphicsPortIndex == 0) // Test mode
   {
      err = GetProperty(g_PropName_TestModeWidth, w);
      if (err != DEVICE_OK)
         return err;
      err = GetProperty(g_PropName_TestModeHeight, h);
      if (err != DEVICE_OK)
         return err;

      if (w < 1 || h < 1)
         return ERR_INVALID_TESTMODE_SIZE;

      // The top-left of the primary desktop monior is (0, 0), so this is a
      // safe position for the window
      x = y = 100;

      desktopMonitors = GetMonitorNames(false, true);
   }
   else // Real monitor
   {
      // Map property data 1 thru N + 1 to available monitors 0 thru N
      monitorName_ = availableMonitors_[graphicsPortIndex - 1];

      if (!DetachMonitorFromDesktop(monitorName_))
      {
         monitorName_ = "";
         return ERR_CANNOT_DETACH;
      }

      desktopMonitors = GetMonitorNames(false, true);

      LONG posX, posY;
      GetRightmostMonitorTopRight(desktopMonitors, posX, posY);

      if (!AttachMonitorToDesktop(monitorName_, posX, posY))
      {
         monitorName_ = "";
         return ERR_CANNOT_ATTACH;
      }

      GetMonitorRect(monitorName_, x, y, w, h);
   }

   std::string windowTitle = "MM_SLM " +
      boost::lexical_cast<std::string>(w) + "x" +
      boost::lexical_cast<std::string>(h) + " [" +
      (monitorName_.empty() ? "Test Mode" : monitorName_) +
      "]";

   windowThread_ = new SLMWindowThread(monitorName_.empty(),
         windowTitle, x, y, w, h);
   windowThread_->Show();

   RECT mouseClipRect;
   if (GetBoundingRect(desktopMonitors, mouseClipRect))
      sleepBlocker_ = new SleepBlocker(mouseClipRect);
   else
      sleepBlocker_ = new SleepBlocker();
   sleepBlocker_->Start();

   width_ = w;
   height_ = h;

   //Initializing the dimension of the Indices array:
   indices_ = new unsigned char[height_*width_]; //Setting number of rows
   
   //Initializing the center point:
   centerx_ = width_/2 - 1;
   centery_ = height_/2 - 1;
	
   //Initializing Radii:
   radius_ = Rad(distance_, numa_);
   minrad_ = minRad(distance_, minna_);
   maxrad_ = maxRad(distance_, maxna_);

   //Show Resolution:
   CreateIntegerProperty(g_Propname_Resolution_H, GetHeight(), true);
   CreateIntegerProperty(g_Propname_Resolution_W, GetWidth(), true);

   return DEVICE_OK;
}


int iPadSLM::Shutdown()
{
   width_ = height_ = 0;

   if (sleepBlocker_)
   {
      sleepBlocker_->Stop();
      delete sleepBlocker_;
      sleepBlocker_ = 0;
   }

   if (windowThread_)
   {
      delete windowThread_;
      windowThread_ = 0;
   }

   if (!monitorName_.empty())
   {
      DetachMonitorFromDesktop(monitorName_);
      monitorName_ = "";
   }

   return DEVICE_OK;
}


bool iPadSLM::Busy()
{
   // TODO We _could_ make the wait for vertical sync asynchronous
   // (Make sure first that Projector knows to wait for non-busy)
   return false;
}


unsigned int iPadSLM::GetWidth()
{
   return width_;
}


unsigned int iPadSLM::GetHeight()
{
   return height_;
}


unsigned int iPadSLM::GetNumberOfComponents()
{
   return 3;
}


unsigned int iPadSLM::GetBytesPerPixel()
{
   return 4;
}


int iPadSLM::SetExposure(double)
{
   // ignore for now.
   return DEVICE_OK;
}


double iPadSLM::GetExposure()
{
   return 0;
}


int iPadSLM::SetImage(unsigned char* pixels)
{
   OffscreenBuffer* offscreen = windowThread_->GetOffscreenBuffer();
   if (!offscreen)
      return ERR_OFFSCREEN_BUFFER_UNAVAILABLE;

   offscreen->DrawImage(pixels, monoColor_, invert_);
   return DEVICE_OK;
}


int iPadSLM::SetImage(unsigned int* pixels)
{
   OffscreenBuffer* offscreen = windowThread_->GetOffscreenBuffer();
   if (!offscreen)
      return ERR_OFFSCREEN_BUFFER_UNAVAILABLE;

   offscreen->DrawImage(pixels);
   shouldBlitInverted_ = invert_;
   return DEVICE_OK;
}


int iPadSLM::SetPixelsTo(unsigned char intensity)
{
   OffscreenBuffer* offscreen = windowThread_->GetOffscreenBuffer();
   if (!offscreen)
      return ERR_OFFSCREEN_BUFFER_UNAVAILABLE;

   intensity ^= (invert_ ? 0xff : 0x00);

   unsigned char redMask = (monoColor_ & SLM_COLOR_RED) ? 0xff : 0x00;
   unsigned char greenMask = (monoColor_ & SLM_COLOR_GREEN) ? 0xff : 0x00;
   unsigned char blueMask = (monoColor_ & SLM_COLOR_BLUE) ? 0xff : 0x00;

   COLORREF color(RGB(intensity & redMask,
            intensity & greenMask,
            intensity & blueMask));

   offscreen->FillWithColor(color);
   shouldBlitInverted_ = false;
   return DisplayImage();
}


int iPadSLM::SetPixelsTo(unsigned char red, unsigned char green, unsigned char blue)
{
   OffscreenBuffer* offscreen = windowThread_->GetOffscreenBuffer();
   if (!offscreen)
      return ERR_OFFSCREEN_BUFFER_UNAVAILABLE;

   unsigned char xorMask = invert_ ? 0xff : 0x00;

   COLORREF color(RGB(red ^ xorMask, green ^ xorMask, blue ^ xorMask));

   offscreen->FillWithColor(color);
   shouldBlitInverted_ = false;
   return DisplayImage();
}


int iPadSLM::DisplayImage()
{
   OffscreenBuffer* offscreen = windowThread_->GetOffscreenBuffer();
   if (!offscreen)
      return ERR_OFFSCREEN_BUFFER_UNAVAILABLE;

   HDC onscreenDC = windowThread_->GetDC();
   DWORD op = shouldBlitInverted_ ? NOTSRCCOPY : SRCCOPY;

   refreshWaiter_.WaitForVerticalBlank();
   offscreen->BlitTo(onscreenDC, op);
   return DEVICE_OK;
}

double iPadSLM::Rad(double Dist, double NA){
	return Dist*NA/sqrt(1-NA*NA);
}

double iPadSLM::minRad(double Dist, double minna){
	return Dist*minna/sqrt(1-minna*minna);
}

double iPadSLM::maxRad(double Dist, double maxna){
	return Dist*maxna/sqrt(1-maxna*maxna);
}

int iPadSLM::BF(){
	int w = GetWidth(), h = GetHeight();
	for (int i = 0; i < h; i++){
		for (int j = 0; j < w; j++){
			double y = i, x = j, cx = centerx_, cy = centery_;
			long double dx = cx - x - 1, dy = cy - y - 1;
			long double p = sqrt(pow(dx,2) + pow(dy,2));
			long double d = pixelsizeh_ * p;
			if(d <= radius_){
				indices_[i * w + j] = intensity_;
			}
			else{
				indices_[i * w + j] = 0;
			}
		}
	}
	return DEVICE_OK;
}

int iPadSLM::DF(){
	int w = GetWidth(), h = GetHeight();
	for (int i = 0; i < h; i++){
		for (int j = 0; j < w; j++){
			double y = i, x = j, cx = centerx_, cy = centery_;
			long double d = pixelsizeh_ * sqrt(pow((cx-x)-1,2) + pow((cy - y)-1,2));
			if(d <= radius_){
				indices_[i * w + j] = 0;
			}
			else{
				indices_[i * w + j] = intensity_;
			}
		}
	}
	return DEVICE_OK;
}

int iPadSLM::Annul(){
	int w = GetWidth(), h = GetHeight();
	for (int i = 0; i < h; i++){
		for (int j = 0; j < w; j++){
			double y = i, x = j, cx = centerx_, cy = centery_;
			long double d = pixelsizeh_ * sqrt(pow((cx-x)-1,2) + pow((cy - y)-1,2));
			if(d <= maxrad_ && d >= minrad_){
				indices_[i * w + j] = intensity_;
			}
			else{
				indices_[i * w + j] = 0;
			}
		}
	}
	return DEVICE_OK;
}

int iPadSLM::Off(){
	int w = GetWidth(), h = GetHeight();
	for (int i = 0; i < h; i++){
		for (int j = 0; j < w; j++){
			indices_[i * w + j] = 0;
		}
	}
	return DEVICE_OK;
}

int iPadSLM::DPC(std::string type){
	int h = GetHeight(), w = GetWidth(), a = 0, b = 0;
	if(type == "Top"){
		h = centery_;
		for (int i = h; i < GetHeight(); i++){
			for (int j = b ; j < w; j++){
				indices_[i * w + j] = 0;
			}
		}
	}
	else if(type == "Bottom"){
		a = centery_;
		for (int i = 0; i < a; i++){
			for (int j = b ; j < w; j++){
				indices_[i * w + j] = 0;
			}
		}
	}
	else if(type == "Left"){
		w = centerx_;
		for (int i = 0; i < GetHeight(); i++){
			for (int j = w ; j < GetWidth(); j++){
				indices_[i * GetWidth() + j] = 0;
			}
		}
	}
	else if (type == "Right") {
		b = centerx_;
		for (int i = 0; i < h; i++){
			for (int j = 0 ; j < b; j++){
				indices_[i * w + j] = 0;
			}
		}
	}
	for (int i = a; i < h; i++){
		for (int j = b ; j < w; j++){
			double y = i, x = j, cx = centerx_, cy = centery_;
			long double d = pixelsizeh_ * sqrt(pow((cx-x)-1,2) + pow((cy - y)-1,2));
			if(d <= maxrad_ && d >= minrad_){
				indices_[i * GetWidth() + j] = intensity_;
			}
			else{
				indices_[i * GetWidth() + j] = 0;
			}
		}
	}
	return DEVICE_OK;
}
//Device Display Action Handlers:
int iPadSLM::OnInversion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(inversionStr_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(inversionStr_);
      long data;
      int ret = GetPropertyData(g_PropName_Inversion, inversionStr_.c_str(),
            data);
      if (ret != DEVICE_OK)
         return ret;
      invert_ = (data != 0);
   }

   return DEVICE_OK;
}


int iPadSLM::OnMonochromeColor(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(monoColorStr_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(monoColorStr_);
      long data;
      int ret = GetPropertyData(g_PropName_MonoColor,
            monoColorStr_.c_str(), data);
      if (ret != DEVICE_OK)
         return ret;
      monoColor_ = (SLMColor)data;
   }

   return DEVICE_OK;
}

int iPadSLM::OnHeight(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  double heightMM = height_*pixelsizeh_;
      pProp->Set(heightMM);
   } else if (eAct == MM::AfterSet) {
	  double heightMM;
      pProp->Get(heightMM);
	  pixelsizeh_ = heightMM/height_;
      return DEVICE_OK;
   }
   return DEVICE_OK;
}

int iPadSLM::OnWidth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	  double widthMM = width_*pixelsizew_;
      pProp->Set(widthMM);
   } else if (eAct == MM::AfterSet) {
	  double widthMM;
      pProp->Get(widthMM);
	  pixelsizew_ = widthMM/width_;
      return DEVICE_OK;
   }
   return DEVICE_OK;
}

int iPadSLM::OnWCenter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(centerx_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(centerx_); // The width index (pixel column) of the center.
	  if(pattern_ == "BrightField"){
		  BF();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "DarkField"){
		  DF();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "Annulus"){
		  Annul();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "DPC"){
		  DPC(type_);
		  SetImage(indices_);
		  DisplayImage();
	  }
      return DEVICE_OK;
   }
   return DEVICE_OK;
}

int iPadSLM::OnHCenter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(centery_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(centery_); //The height index (pixel row) of the center.
	  if(pattern_ == "BrightField"){
		  BF();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "DarkField"){
		  DF();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "Annulus"){
		  Annul();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "DPC"){
		  DPC(type_);
		  SetImage(indices_);
		  DisplayImage();
	  }
      return DEVICE_OK;
   }
   return DEVICE_OK;
}

int iPadSLM::OnPattern(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(pattern_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(pattern_); //The height index (pixel row) of the center
	  if(pattern_ == "BrightField"){
		  BF();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "DarkField"){
		  DF();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "Annulus"){
		  Annul();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "DPC"){
		  DPC(type_);
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "Off"){
		  Off();
		  SetImage(indices_);
		  DisplayImage();
	  }
      return DEVICE_OK;
   }
   return DEVICE_OK;
}

int iPadSLM::Aperture(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if(pAct == MM::BeforeGet)
	{
		pProp->Set(numa_);
	}
	else if(pAct == MM::AfterSet)
	{
	  pProp->Get(numa_);
	  radius_ = Rad(distance_, numa_);
	  if(pattern_ == "BrightField"){
		  BF();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "DarkField"){
		  DF();
		  SetImage(indices_);
		  DisplayImage();
	  }
	}
    return DEVICE_OK;
}

int iPadSLM::OnDist(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if(pAct == MM::BeforeGet)
	{
		pProp->Set(distance_);
	}
	else if(pAct == MM::AfterSet)
	{
	  pProp->Get(distance_);
	  minrad_ = minRad(distance_, minna_);
	  maxrad_ = maxRad(distance_, maxna_);
	  radius_ = Rad(distance_, numa_);
	  if(pattern_ == "BrightField"){
		  BF();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "DarkField"){
		  DF();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "Annulus"){
		  Annul();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "DPC"){
		  DPC(type_);
		  SetImage(indices_);
		  DisplayImage();
	  }
	}
    return DEVICE_OK;
}

int iPadSLM::OnMinNA(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
   {
      pProp->Set(minna_);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(minna_);
	  minrad_ = minRad(distance_, minna_);
	  if(pattern_ == "Annulus"){
		  Annul();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "DPC"){
		  DPC(type_);
		  SetImage(indices_);
		  DisplayImage();
	  }
	  return DEVICE_OK;
   }
   return DEVICE_OK;
}

int iPadSLM::OnMaxNA(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
   {
      pProp->Set(maxna_);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(maxna_);
	  maxrad_ = maxRad(distance_, maxna_);
	  if(pattern_ == "Annulus"){
		  Annul();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "DPC"){
		  DPC(type_);
		  SetImage(indices_);
		  DisplayImage();
	  }
	  return DEVICE_OK;
   }
   return DEVICE_OK;
}

int iPadSLM::OnInt(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
   {
      pProp->Set(intensity_);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(intensity_);
	  if(pattern_ == "BrightField"){
		  BF();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "DarkField"){
		  DF();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "Annulus"){
		  Annul();
		  SetImage(indices_);
		  DisplayImage();
	  }
	  else if(pattern_ == "DPC"){
		  DPC(type_);
		  SetImage(indices_);
		  DisplayImage();
	  }
	  return DEVICE_OK;
   }
   return DEVICE_OK;
}

int iPadSLM::OnType(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
   {
      pProp->Set(type_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(type_);
	  if(pattern_ == "DPC"){
		  DPC(type_);
		  SetImage(indices_);
		  DisplayImage();
	  }
	  return DEVICE_OK;
   }
   return DEVICE_OK;
}