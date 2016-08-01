///////////////////////////////////////////////////////////////////////////////
// FILE:          GenericSLM.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   GenericSLM device adapter
// COPYRIGHT:     University of California, San Francisco, 2009
//
// AUTHOR:        Arthur Edelstein, arthuredelstein@gmail.com, 3/17/2009
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
   #include <windows.h>
   #define snprintf _snprintf
#endif


#include "../../MMDevice/MMDevice.h"
#include "GenericSLM.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include <sstream>
#include <iostream>
#include <windows.h>
#include <tchar.h>


// GenericSLM
const char* g_GenericSLMName = "GenericSLM";
const char* g_Keyword_Intensity = "Intensity";
const char* g_Graphics_Port = "GraphicsPort";
const char* g_Keyword_Inversion = "Inversion";
const char* g_Keyword_MonochromeColor = "MonochromeColor";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_GenericSLMName, MM::SLMDevice, "Spatial Light Modulator controlled through computer graphics output");
}


MODULE_API MM::Device* CreateDevice(const char* deviceName)
{

   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_GenericSLMName) == 0)
   {
      // create GenericSLM
      GenericSLM* pGenericSLM = new GenericSLM(g_GenericSLMName);
      return pGenericSLM;
   }



   return 0;
}


MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// GenericSLM implementation
// ~~~~~~~~~~~~~~~~~~~~

GenericSLM::GenericSLM(const char* name) :
   initialized_(false),
   name_(name),
   busy_(false),
   error_(0),
   graphicsPortDescription_(""),
   allOff_(false),
   allOn_(false),
   invert_(false),
   colorInvert_(false),
   inversionNum_(0),
   monochromeColorNum_(0),
   inversionStr_("Off"),
   monochromeColorStr_("White"),
   chosenDisplayIndex_(-1),
   ddObject_(NULL)
{
   assert(strlen(name) < (unsigned int) MM::MaxStrLength);

   InitializeDefaultErrorMessages();
   SetErrorText(ERR_DIRECT_DRAW, "Error invoking Direct Draw function");

   displays_ = getMonitorInfo();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "SLM controlled by computer display adapter output", MM::String, true);

   // Graphics Port
   GenerateGraphicsPortProperty();

   // Mode Properties
   GenerateModeProperties();
}


void GenericSLM::GenerateModeProperties()
{
   CPropertyAction* pAct;

   // Inversion Mode property
   pAct = new CPropertyAction (this, &GenericSLM::OnInversion);
   CreateProperty(g_Keyword_Inversion, "Off", MM::String, false, pAct, false);
   AddAllowedValue(g_Keyword_Inversion, "Off", 0);
   AddAllowedValue(g_Keyword_Inversion, "On", 1);

   // Monochrome Mode property
   pAct = new CPropertyAction (this, &GenericSLM::OnMonochromeColor);
   CreateProperty(g_Keyword_MonochromeColor, "White", MM::String, false, pAct, false);
   AddAllowedValue(g_Keyword_MonochromeColor, "White", 0);
   AddAllowedValue(g_Keyword_MonochromeColor, "Red", 1);
   AddAllowedValue(g_Keyword_MonochromeColor, "Green", 2);
   AddAllowedValue(g_Keyword_MonochromeColor, "Blue", 3);
}


void GenericSLM::GenerateGraphicsPortProperty()
{

   // Graphics port
   CPropertyAction* pAct = new CPropertyAction (this, &GenericSLM::OnGraphicsPort);
   CreateProperty(g_Graphics_Port, "Undefined", MM::String, false, pAct, true);

   for(unsigned int i=0;i<displays_.size();++i) {
      if (! displays_[i].isPrimary) {
         stringstream displayDescription;
         displayDescription << displays_[i].cardName;
         AddAllowedValue(g_Graphics_Port,displayDescription.str().c_str(),i);
      } else {
         primaryDisplayIndex_ = i;
      }
   }
}


GenericSLM::~GenericSLM()
{
   if (initialized_)
      Shutdown();
}


bool GenericSLM::Busy()
{
   return false;
}


void GenericSLM::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}


int GenericSLM::Initialize()
{
   if (!initialized_)
   {
      if (chosenDisplayIndex_ == -1)
         return DEVICE_NOT_CONNECTED;

      LogMessage("GenericSLM::Initialize()");

      stringstream msg;
      displays_[chosenDisplayIndex_].isSLM = true;

      msg << "Graphics Port: " << displays_[chosenDisplayIndex_].deviceName;
      this->LogMessage(msg.str().c_str());

      if (! displays_[chosenDisplayIndex_].isDisabled) {
         DetachDisplayDevice(&displays_[chosenDisplayIndex_]);
      }

      POINT pos = getNextDisplayPosition(displays_);

      displays_[chosenDisplayIndex_].x = pos.x;
      displays_[chosenDisplayIndex_].y = pos.y;

      AttachDisplayDevice(&displays_[chosenDisplayIndex_]);

      viewBounds = getViewingMonitorsBounds(displays_);

      DeployWindow();

      Sleep(500); // Wait for the adapter hardware to get fully attached.

      FixWindows(wnd_);
      SetWindowPos(wnd_, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);

      thd_ = new GenericSLMWindowsGUIThread(wnd_);
      thd_->Start();

      InitializeDrawContext();

      initialized_=true;
   }
   return HandleErrors();
}


int GenericSLM::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
      LogMessage("GenericSLM::Shutdown()");
      DestroyDrawContext();

      thd_->Stop();
      thd_->wait();

      RemoveWindow();
      DetachDisplayDevice(&displays_[chosenDisplayIndex_]);

      Sleep(500);
   }
   return HandleErrors();
}


void GenericSLM::InitializeDrawContext()
{
   // Create a device context (display image buffer) for window.
   windc_ = GetDC(wnd_);

   // Create a device context (drawing image buffer) in memory.
   memdc_ = CreateCompatibleDC(windc_);

   // Create a device-independent bitmap (DIB) to be associated with the memory device context.
   // Retrieve the pointer to the bitmap pixel data (bmpPixels_).
   hbmp_ = CreateDIBSection(memdc_, createBitmapInfo(), DIB_RGB_COLORS, (void **) &bmpPixels_, NULL, NULL);

   // Save the old 1x1 pixel bitmap from the memory device context and apply the new bitmap, hbmp_.
   hbmpold_ = (HBITMAP) SelectObject(memdc_, hbmp_);

   // Get the BITMAP associated with the HBITMAP hbmp_; call it bmp.
   BITMAP bmp;
   GetObject(hbmp_, sizeof(BITMAP), &bmp);

   // Get the byte-padded width of the bitmap.
   bmWidthBytes_ = bmp.bmWidthBytes;

   // Fill with black (off) pixels.
   FillDC(memdc_, RGB(0,0,0));

   // Spit out the results.
   stringstream msg;
   msg << "bmp: " << bmp.bmWidth << " x " << bmp.bmHeight << ", bits = " << bmp.bmBits << ", bitsPixel = " << bmp.bmBitsPixel
      << ", Contexts = " << bmp.bmPlanes << ", type = " << bmp.bmType << ", widthBytes = " << bmp.bmWidthBytes;
   LogMessage(msg.str(), false);
}


BITMAPINFO * GenericSLM::createBitmapInfo()
{
   BITMAPINFO *pbi;

   // Create a BITMAPINFOHEADER for creating a device-independent bitmap (DIB).
   BITMAPINFOHEADER bih;

   bih.biSize = sizeof(bih);
   bih.biWidth = displays_[chosenDisplayIndex_].width;
   bih.biHeight = displays_[chosenDisplayIndex_].height;
   bih.biPlanes = 1;         // Always 1
   bih.biCompression = BI_RGB; // Uncompressed
   bih.biSizeImage = 0;        // Okay for BI_RGB format.
   bih.biXPelsPerMeter = 0;    // Not used
   bih.biYPelsPerMeter = 0;    // Not used
   bih.biClrUsed = 0;          // Not used
   bih.biClrImportant = 0;     // Not used
   bih.biBitCount = 32;

   BITMAPINFO bi;
   pbi = &bi;
   pbi->bmiHeader = bih;
   return pbi;
}


int GenericSLM::BlitBitmap()
{
   int ret = WaitForScreenRefresh();
   if (ret != DEVICE_OK)
      return ret;
   BitBlt(windc_, 0, 0, GetWidth(), GetHeight(), memdc_, 0, 0, colorInvert_ ? NOTSRCCOPY : SRCCOPY);
   return DEVICE_OK;
}


int GenericSLM::WaitForScreenRefresh()
{
   if (ddObject_ == NULL)
   {
      HINSTANCE hLibDDraw = LoadLibrary(TEXT("ddraw.dll"));
      DIRECTDRAWCREATE ddcreate = (DIRECTDRAWCREATE) GetProcAddress(hLibDDraw, "DirectDrawCreate");

      if (ddcreate) {
         HRESULT hr = ddcreate(NULL, &ddObject_, NULL);
         if (hr != DD_OK) {
            this->LogMessage("DirectDraw call in WaitForScreenRefresh function failed");
            ddObject_ = NULL;
            return ERR_DIRECT_DRAW;
         }
      }
   }
   ddObject_->WaitForVerticalBlank(DDWAITVB_BLOCKBEGIN, NULL);
   return DEVICE_OK;
}


void GenericSLM::DestroyDrawContext()
{
   // Windows API cleanup of device contexts and bitmaps.

   // Set the memory device context back to its original 1x1 bitmap.
   SelectObject(memdc_, hbmpold_);

   // Delete the memory device context.
   DeleteDC(memdc_);

   // Delete the bitmap.
   DeleteObject(hbmp_);
}


int GenericSLM::SetImage(unsigned char* pixels)
{
   if (initialized_)
   {
      unsigned int * newPix = (unsigned int *) malloc(displays_[chosenDisplayIndex_].height * displays_[chosenDisplayIndex_].width * 4);
      ConvertOneByteToFour(pixels,newPix);
      colorInvert_ = false;
      CopyIntPixelsToBitmap(newPix);
      free(newPix);
      return DEVICE_OK;
   }
   else
   {
      SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR, "SLM not initialized.");
      return DEVICE_LOCALLY_DEFINED_ERROR;
   }
}


void GenericSLM::ConvertOneByteToFour(unsigned char* pixelsIn, unsigned int * pixelsOut)
{
   long length = displays_[chosenDisplayIndex_].height * displays_[chosenDisplayIndex_].width;
   unsigned char * pixelsOutByte = (unsigned char *) pixelsOut;
   for (long i=0;i<length;++i)
   {
      unsigned char pixel = *pixelsIn;
      if (invert_)
         pixel = ~pixel;

      // B
      if (monochromeColorNum_ == 0 || monochromeColorNum_ == 3)
         *pixelsOutByte = pixel;
      else
         *pixelsOutByte = 0;
      ++pixelsOutByte;

      // G
      if (monochromeColorNum_ == 0 || monochromeColorNum_ == 2)
         *pixelsOutByte = pixel;
      else
         *pixelsOutByte = 0;
      ++pixelsOutByte;

      // R
      if (monochromeColorNum_ == 0 || monochromeColorNum_ == 1)
         *pixelsOutByte = pixel;
      else
         *pixelsOutByte = 0;
      ++pixelsOutByte;

      // A (unused byte)
      *pixelsOutByte = 0;
      ++pixelsOutByte;

      // Move to next input byte.
      ++pixelsIn;
   }
}


int GenericSLM::SetImage(unsigned int* pixels)
{
   if (initialized_)
   {
      colorInvert_ = invert_;
      CopyIntPixelsToBitmap(pixels);
      return DEVICE_OK;
   }
   else
   {
      SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR, "SLM not initialized.");
      return DEVICE_LOCALLY_DEFINED_ERROR;
   }
}


void GenericSLM::CopyIntPixelsToBitmap(unsigned int* pixels)
{
   unsigned char * bmpLoc = (unsigned char *) bmpPixels_;
   unsigned char * inputOrigin = (unsigned char *) pixels;
   unsigned char * inputLoc;

   int rows = displays_[chosenDisplayIndex_].height;
   int cols = displays_[chosenDisplayIndex_].width;
   long inputWidthBytes = cols * 4;
   long bmpWidthBytes = bmWidthBytes_;

   for (int row = rows - 1; row >= 0; --row)
   {
      inputLoc = inputOrigin + row * inputWidthBytes;
      memcpy(bmpLoc, inputLoc, inputWidthBytes);
      bmpLoc += bmpWidthBytes;
   }
}


int GenericSLM::DisplayImage()
{
   if (initialized_)
   {
      inversionStr_ = invert_ ? "On" : "Off" ;

      return BlitBitmap();
   }
   else
   {
      SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR, "SLM not active.");
      return DEVICE_LOCALLY_DEFINED_ERROR;
   }
}


int GenericSLM::SetExposure(double /*interval_ms*/)
{
   // ignore for now.
   return DEVICE_OK;
}


double GenericSLM::GetExposure()
{
   return 0;
}


unsigned int GenericSLM::GetWidth()
{
   return displays_[chosenDisplayIndex_].width;
}


unsigned int GenericSLM::GetHeight()
{
   return displays_[chosenDisplayIndex_].height;
}


unsigned int GenericSLM::GetNumberOfComponents()
{
   return 3;
}


unsigned int GenericSLM::GetBytesPerPixel()
{
   return 4;
}


int GenericSLM::SetPixelsTo(unsigned char red, unsigned char green, unsigned char blue)
{
   if (initialized_)
   {
      if (invert_) {
         red = ~red;
         green = ~green;
         blue = ~blue;
      }
      return FillDC(windc_, RGB(red, green, blue));
   }
   else
   {
      SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR, "SLM not active.");
      return DEVICE_LOCALLY_DEFINED_ERROR;
   }
}


int GenericSLM::SetPixelsTo(unsigned char intensity)
{
   if (initialized_)
   {
      COLORREF color( RGB(0,0,0) );
      if (invert_)
         intensity = ~intensity;

      switch (monochromeColorNum_)
      {
         case 0: //white
            color = RGB(intensity,intensity,intensity);
            break;

         case 1: //red
            color = RGB(intensity,0,0);
            break;

         case 2: //green
            color = RGB(0,intensity,0);
            break;

         case 3: //blue
            color = RGB(0,0,intensity);
            break;
      }

      return FillDC(windc_, color);
   }
   else
   {
      SetErrorText(DEVICE_LOCALLY_DEFINED_ERROR, "SLM not active.");
      return DEVICE_LOCALLY_DEFINED_ERROR;
   }
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int GenericSLM::OnGraphicsPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((const char *) graphicsPortDescription_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      long graphicsPortNum;
      pProp->Get(graphicsPortDescription_);
      GetCurrentPropertyData(g_Graphics_Port,graphicsPortNum);
      chosenDisplayIndex_ = graphicsPortNum;
      DetachDisplayDevice(&displays_[chosenDisplayIndex_]);
   }
   return HandleErrors();
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
      ((MM::Property *) pProp)->GetData(inversionStr_.c_str(), inversionNum_);
      invert_ = (inversionNum_ == 1);
   }

   return DEVICE_OK;
}


int GenericSLM::OnMonochromeColor(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(monochromeColorStr_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(monochromeColorStr_);
      ((MM::Property *) pProp)->GetData(monochromeColorStr_.c_str(), monochromeColorNum_);
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Error handler
///////////////////////////////////////////////////////////////////////////////

int GenericSLM::HandleErrors()
{
   int lastError = error_;
   error_ = 0;
   return lastError;
}


///////////////////////////////////////////////////////////////////////////////
/// Windows API stuff
///////////////////////////////////////////////////////////////////////////////

int GenericSLM::FillDC(HDC hdc, COLORREF color)
{

   RECT rect;
   rect.left = 0;
   rect.top = 0;
   rect.right = GetWidth();
   rect.bottom = GetHeight();

   HBRUSH hbrush = CreateSolidBrush(color);
   FillRect(hdc, &rect, hbrush);
   return DEVICE_OK;
}


bool GenericSLM::AttachDisplayDevice(MonitorDevice * dev)
{
   DEVMODE defaultMode;

   ZeroMemory(&defaultMode, sizeof(DEVMODE));
   defaultMode.dmSize = sizeof(DEVMODE);
   defaultMode.dmPosition.x = dev->x;
   defaultMode.dmPosition.y = dev->y;
   defaultMode.dmFields = DM_POSITION;

   ChangeDisplaySettingsEx((LPSTR)dev->cardName.c_str(), &defaultMode, NULL, CDS_NORESET|CDS_UPDATEREGISTRY, NULL);
   ChangeDisplaySettings(NULL, 0);
   dev->isDisabled = false;
   Sleep(1000);

   updateMonitorRects(&displays_);
   return true;
}


bool GenericSLM::DetachDisplayDevice(MonitorDevice * dev)
{
   int result;
   DEVMODE    DevMode;
   ZeroMemory(&DevMode, sizeof(DevMode));
   DevMode.dmSize = sizeof(DevMode);
   DevMode.dmFields = DM_PELSWIDTH | DM_PELSHEIGHT | DM_BITSPERPEL | DM_POSITION
      | DM_DISPLAYFREQUENCY | DM_DISPLAYFLAGS ;
   result = ChangeDisplaySettingsEx(dev->cardName.c_str(), &DevMode, NULL, CDS_UPDATEREGISTRY, NULL);
   result = ChangeDisplaySettingsEx(dev->cardName.c_str(), &DevMode, NULL, CDS_UPDATEREGISTRY, NULL);
   dev->isDisabled = true;

   updateMonitorRects(&displays_);
   return true;
}


void GenericSLM::DeployWindow()
{
   updateMonitorRects(&displays_);
   const char * className = name_.append("_MMWindow").c_str();

   winClass_ = new WinClass(WindowProcedure, className, 0);
   winClass_->SetBackColor(RGB(0,0,0)); // Make sure the window is black at the beginning.
   winClass_->Register();

   WinMaker win ("MM_SLM", className, 0, displays_[chosenDisplayIndex_].x, displays_[chosenDisplayIndex_].y, displays_[chosenDisplayIndex_].width, displays_[chosenDisplayIndex_].height);
   win.Show(1);

   // Make this the topmost window.
   SetWindowPos(win.getHandle(), HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);

   wnd_ = win.getHandle();
}


void GenericSLM::RemoveWindow()
{
   DestroyWindow(wnd_);
   winClass_->Unregister();
}


// Windows GUI thread to prevent screen savers or hibernation, and prevent the cursor from getting lost.
int GenericSLMWindowsGUIThread::svc(void)
{
   long i=0;
   while(!stop_)
   {
      ++i;

      if ((i % 900) == 0) {  // Every 30 seconds or so ...
         // ... reset idle clocks to prevent screensaver or hibernation (would appear on SLM).
         SetThreadExecutionState(ES_DISPLAY_REQUIRED | ES_SYSTEM_REQUIRED);
      }

      if ((i % 30) == 0)
      {
         //FixWindows(hwnd_); // This seems to be too slow. Run in separate thread?
         //SetWindowPos(hwnd_, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE); // This seems to be too slow.
      }

      RestrictCursor();

      Sleep(30);
   }

   return 0;
}

int GenericSLMWindowsGUIThread::RestrictCursor()
{
   ClipCursor(&viewBounds);
   return 0;
}


// Static functions for controlling windows.
bool GenericSLM::FixWindows(HWND slmWnd)
{
   HWND wnd;
   vector<HWND> windows = GetWindowList();
   char caption[32];

   for (unsigned int i=0;i<windows.size();++i)
   {
      wnd = windows[i];

      if (IsWindowVisible(windows[i]))
      {
         GetWindowText(wnd, caption, 32);
         if (strncmp(caption, "MM_SLM", 6) !=0 && wnd != slmWnd)
         {
            MoveWindowToViewingMonitor(wnd);
         }
      }
   }
   return true;
}


void GenericSLM::MoveWindowToViewingMonitor(HWND wnd)
{
   RECT winRect;
   int width, height;
   GetWindowRect(wnd, &winRect);

   if ((winRect.left > viewBounds.right) || (winRect.top > viewBounds.bottom))
   {
      width = winRect.right - winRect.left;
      height = winRect.bottom - winRect.top;
      winRect.left = 10;
      winRect.top = 10;
      SetWindowPos(wnd, HWND_TOP, winRect.left, winRect.top, width, height, SWP_NOZORDER);
   }
}


vector<HWND> GenericSLM::GetWindowList()
{
   windowList_.clear();
   EnumWindows(reinterpret_cast<WNDENUMPROC>(&GenericSLM::AddWindowToList), (long) &windowList_);
   return windowList_;
}


// Only called from GetWindowList()
BOOL CALLBACK GenericSLM::AddWindowToList(HWND wnd, long windowListAddr)
{
   ((vector<HWND> *) windowListAddr)->push_back(wnd);
   EnumChildWindows(wnd,reinterpret_cast<WNDENUMPROC>(AddWindowToList),windowListAddr);
   return 1;
}


HDC GenericSLM::CreateDeviceContext(string deviceName)
{
   DEVMODE dm;
   ZeroMemory(&dm, sizeof(dm));
   dm.dmSize = sizeof(dm);
   EnumDisplaySettingsEx(deviceName.c_str(), ENUM_REGISTRY_SETTINGS, &dm, 0);
   return CreateDC(deviceName.c_str(), 0, 0, &dm);
}
