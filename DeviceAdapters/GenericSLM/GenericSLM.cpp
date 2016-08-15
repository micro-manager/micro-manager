// DESCRIPTION:   GenericSLM device adapter
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

#include "GenericSLM.h"

#include "Monitors.h"
#include "OffscreenBuffer.h"
#include "SLMWindowThread.h"
#include "SleepBlocker.h"

#include "ModuleInterface.h"
#include "DeviceUtils.h"

#include <Windows.h>

#include <algorithm>


const char* g_GenericSLMName = "GenericSLM";
const char* g_PropName_GraphicsPort = "GraphicsPort";
const char* g_PropName_Inversion = "Inversion";
const char* g_PropName_MonoColor = "MonochromeColor";


MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_GenericSLMName, MM::SLMDevice,
         "Spatial light modulator controlled through computer graphics output");
}


MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_GenericSLMName) == 0)
   {
      GenericSLM* pGenericSLM = new GenericSLM(g_GenericSLMName);
      return pGenericSLM;
   }

   return 0;
}


MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


GenericSLM::GenericSLM(const char* name) :
   name_(name),
   width_(0),
   height_(0),
   windowThread_(0),
   sleepBlocker_(0),
   shouldBlitInverted_(false),
   invert_(false),
   inversionStr_("Off"),
   monoColor_(SLM_COLOR_WHITE),
   monoColorStr_("White")
{
   SetErrorText(DEVICE_ERR, "An error occurred");
   // TODO We can do better than that

   availableMonitors_ = GetMonitorNames(true, false);

   // Create pre-init properties

   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   CreateProperty(MM::g_Keyword_Description, "SLM controlled by computer display adapter output", MM::String, true);

   CreateStringProperty(g_PropName_GraphicsPort, "Test128x128", false, 0, true);
   AddAllowedValue(g_PropName_GraphicsPort, "Test128x128", 0); // Prevent empty list
   for (unsigned i = 0; i < availableMonitors_.size(); ++i)
   {
      AddAllowedValue(g_PropName_GraphicsPort,
            availableMonitors_[i].c_str(), i + 1);
   }

   CreateStringProperty(g_PropName_Inversion, inversionStr_.c_str(), false,
         new CPropertyAction(this, &GenericSLM::OnInversion), false);
   AddAllowedValue(g_PropName_Inversion, "Off", 0);
   AddAllowedValue(g_PropName_Inversion, "On", 1);

   CreateProperty(g_PropName_MonoColor, monoColorStr_.c_str(), MM::String, false,
         new CPropertyAction(this, &GenericSLM::OnMonochromeColor), false);
   AddAllowedValue(g_PropName_MonoColor, "White", SLM_COLOR_WHITE);
   AddAllowedValue(g_PropName_MonoColor, "Red", SLM_COLOR_RED);
   AddAllowedValue(g_PropName_MonoColor, "Green", SLM_COLOR_GREEN);
   AddAllowedValue(g_PropName_MonoColor, "Blue", SLM_COLOR_BLUE);
   AddAllowedValue(g_PropName_MonoColor, "Cyan", SLM_COLOR_CYAN);
   AddAllowedValue(g_PropName_MonoColor, "Magenta", SLM_COLOR_MAGENTA);
   AddAllowedValue(g_PropName_MonoColor, "Yellow", SLM_COLOR_YELLOW);
}


GenericSLM::~GenericSLM()
{
   Shutdown();
}


void GenericSLM::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int GenericSLM::Initialize()
{
   Shutdown();

   long index;
   int err = GetCurrentPropertyData(g_PropName_GraphicsPort, index);
   if (err != DEVICE_OK)
      return err;
   if (index > 0) // Unless test mode
      monitorName_ = availableMonitors_[index - 1];

   if (!monitorName_.empty() && !DetachMonitorFromDesktop(monitorName_))
   {
      monitorName_ = "";
      return DEVICE_ERR; // TODO "Cannot detach monitor from desktop"
   }

   std::vector<std::string> otherAttachedMonitors(GetMonitorNames(false, true));

   LONG posX, posY;
   GetRightmostMonitorTopRight(otherAttachedMonitors, posX, posY);

   if (!monitorName_.empty() &&
         !AttachMonitorToDesktop(monitorName_, posX, posY))
   {
      monitorName_ = "";
      return DEVICE_ERR; // TODO "Cannot attach monitor to desktop"
   }

   LONG x, y, w, h;
   if (!monitorName_.empty())
   {
      GetMonitorRect(monitorName_, x, y, w, h);
   }
   else // Test mode
   {
      x = y = 0;
      w = h = 128;
   }

   windowThread_ = new SLMWindowThread("MM_SLM", x, y, w, h);
   windowThread_->Show();

   RECT mouseClipRect;
   if (GetBoundingRect(otherAttachedMonitors, mouseClipRect))
      sleepBlocker_ = new SleepBlocker(mouseClipRect);
   else
      sleepBlocker_ = new SleepBlocker();
   sleepBlocker_->Start();

   width_ = w;
   height_ = h;

   return DEVICE_OK;
}


int GenericSLM::Shutdown()
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


bool GenericSLM::Busy()
{
   // TODO We _could_ make the wait for vertical sync asynchronous
   // (Make sure first that Projector knows to wait for non-busy)
   return false;
}


unsigned int GenericSLM::GetWidth()
{
   return width_;
}


unsigned int GenericSLM::GetHeight()
{
   return height_;
}


unsigned int GenericSLM::GetNumberOfComponents()
{
   return 3;
}


unsigned int GenericSLM::GetBytesPerPixel()
{
   return 4;
}


int GenericSLM::SetExposure(double)
{
   // ignore for now.
   return DEVICE_OK;
}


double GenericSLM::GetExposure()
{
   return 0;
}


int GenericSLM::SetImage(unsigned char* pixels)
{
   OffscreenBuffer* offscreen = windowThread_->GetOffscreenBuffer();
   if (!offscreen)
      return DEVICE_ERR;

   offscreen->DrawImage(pixels, monoColor_, invert_);
   return DEVICE_OK;
}


int GenericSLM::SetImage(unsigned int* pixels)
{
   OffscreenBuffer* offscreen = windowThread_->GetOffscreenBuffer();
   if (!offscreen)
      return DEVICE_ERR;

   offscreen->DrawImage(pixels);
   shouldBlitInverted_ = invert_;
   return DEVICE_OK;
}


int GenericSLM::SetPixelsTo(unsigned char intensity)
{
   OffscreenBuffer* offscreen = windowThread_->GetOffscreenBuffer();
   if (!offscreen)
      return DEVICE_ERR;

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


int GenericSLM::SetPixelsTo(unsigned char red, unsigned char green, unsigned char blue)
{
   OffscreenBuffer* offscreen = windowThread_->GetOffscreenBuffer();
   if (!offscreen)
      return DEVICE_ERR;

   unsigned char xorMask = invert_ ? 0xff : 0x00;

   COLORREF color(RGB(red ^ xorMask, green ^ xorMask, blue ^ xorMask));

   offscreen->FillWithColor(color);
   shouldBlitInverted_ = false;
   return DisplayImage();
}


int GenericSLM::DisplayImage()
{
   OffscreenBuffer* offscreen = windowThread_->GetOffscreenBuffer();
   if (!offscreen)
      return DEVICE_ERR;

   HDC onscreenDC = windowThread_->GetDC();
   DWORD op = shouldBlitInverted_ ? NOTSRCCOPY : SRCCOPY;

   refreshWaiter_.WaitForVerticalBlank();
   offscreen->BlitTo(onscreenDC, op);
   return DEVICE_OK;
}


int GenericSLM::OnInversion(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int GenericSLM::OnMonochromeColor(MM::PropertyBase* pProp, MM::ActionType eAct)
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
