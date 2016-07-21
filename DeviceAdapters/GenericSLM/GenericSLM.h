///////////////////////////////////////////////////////////////////////////////
// FILE:          GenericSLM.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PrecisExcite controller adapter
// COPYRIGHT:     University of California, San Francisco, 2009
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
//
// AUTHOR:        Arthur Edelstein, arthuredelstein@gmail.com, 3/17/2009
//
//

#ifndef _GENERICSLM_H_
#define _GENERICSLM_H_

#include "ddraw.h"
#include "WinStuff.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
//#include <iostream>
#include <vector>
using namespace std;

#include "DisplayAdapters.h"

vector<MonitorDevice> displays_;
RECT viewBounds;

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_DIRECT_DRAW 12000

class GenericSLMWindowsGUIThread : public MMDeviceThreadBase
{
   public:
      GenericSLMWindowsGUIThread(HWND hwnd) : 
         stop_(false), hwnd_(hwnd) {}
      ~GenericSLMWindowsGUIThread() {}
      int svc (void);

      void Stop() {stop_ = true;}
      void Start() {stop_ = false; activate();}

      int RestrictCursor();



   private:
      bool stop_;
      HWND hwnd_;
};

class GenericSLM : public CSLMBase<GenericSLM>
{
public:
   GenericSLM(const char* name);
   ~GenericSLM();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* pszName) const;
   bool Busy();
   
   int SetImage(unsigned char* pixels);
   int SetImage(unsigned int* pixels);
   int SetPixelsTo(unsigned char red, unsigned char green, unsigned char blue);
   int SetPixelsTo(unsigned char intensity);
   int DisplayImage();
   int SetExposure(double interval_ms);
   double GetExposure();

   unsigned int GetWidth();
   unsigned int GetHeight();
   unsigned int GetNumberOfComponents();
   unsigned int GetBytesPerPixel();

   // action interface
   // ----------------
   int OnGraphicsPort(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnInversion(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMonochromeColor(MM::PropertyBase* pProp, MM::ActionType eAct);

   int IsSLMSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

private:
   string graphicsPortDescription_;
   long chosenDisplayIndex_;
   long primaryDisplayIndex_;

   string name_;
   HWND wnd_;

   HDC dc_;

   int error_;

   bool initialized_;

   bool busy_;
   bool allOn_;
   bool allOff_;
   bool invert_;
   bool colorInvert_;

   long inversionNum_; // 0 or 1
   long monochromeColorNum_; // 0,1,2,3,4

   string inversionStr_;
   string monochromeColorStr_;

   GenericSLMWindowsGUIThread * thd_;

   void GenerateModeProperties();
   void GenerateGraphicsPortProperty();

   void CopyIntPixelsToBitmap(unsigned int* pixels);

   bool AttachDisplayDevice(MonitorDevice * dev);
   bool DetachDisplayDevice(MonitorDevice * dev);

   void DeployWindow();
   void RemoveWindow();

   void InitializeDrawContext();
   void DestroyDrawContext();

   void DrawImage();
   BITMAPINFO * createBitmapInfo();
   void ConvertOneByteToFour(unsigned char* pixelsIn, unsigned int * pixelsOut);

   void StripString(string& StringToModify);
   int HandleErrors();

   void MoveWindowToViewingMonitor(HWND wnd);
   bool FixWindows(HWND slmWnd);
   vector<HWND> GetWindowList();

   HDC CreateDeviceContext(string deviceName);
   static BOOL CALLBACK AddWindowToList(HWND wnd, long param);

   int FillDC(HDC hdc, COLORREF color);

   HDC memdc_;
   HBITMAP hbmp_;
   HBITMAP hbmpold_;
   unsigned int * bmpPixels_;
   HDC windc_;
   long bmWidthBytes_;

   int BlitBitmap();

   int WaitForScreenRefresh();
   IDirectDraw * ddObject_;

   WinClass * winClass_;

   POINT GetCoordsOfLeftmostMonitor();

   vector<HWND> windowList_;
   MMThreadLock windowListLock_;





   GenericSLM& operator=(GenericSLM& /*rhs*/) {assert(false); return *this;}
};

// Static function



typedef HRESULT(WINAPI * DIRECTDRAWCREATE) (GUID *, LPDIRECTDRAW *, IUnknown *);


#endif // _GENERICSLM_H_