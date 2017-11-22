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

#pragma once

#include "SLMColor.h"
#include "RefreshWaiter.h"

#include "DeviceBase.h"
#include "DeviceUtils.h"
#include <vector>

class SLMWindowThread;
class SleepBlocker;


// Note: Only one SLM is currently supported. The effect of adding 2 or more is
// undefined.
class iPadSLM : public CSLMBase<iPadSLM>
{
public:
   iPadSLM(const char* name);
   virtual ~iPadSLM();

   // Device API
   virtual int Initialize();
   virtual int Shutdown();

   virtual void GetName(char* pszName) const;
   virtual bool Busy();

   // SLM API
   virtual unsigned int GetWidth();
   virtual unsigned int GetHeight();
   virtual unsigned int GetNumberOfComponents();
   virtual unsigned int GetBytesPerPixel();

   virtual int SetExposure(double interval_ms);
   virtual double GetExposure();

   virtual int SetImage(unsigned char* pixels);
   virtual int SetImage(unsigned int* pixels);
   virtual int SetPixelsTo(unsigned char intensity);
   virtual int SetPixelsTo(unsigned char red, unsigned char green, unsigned char blue);
   virtual int DisplayImage();

   virtual int IsSLMSequenceable(bool& isSequenceable) const
   { isSequenceable = false; return DEVICE_OK; }

private: // Action handlers
   int OnInversion(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMonochromeColor(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnWidth(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnHeight(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnWCenter(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnHCenter(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnPattern(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int Aperture(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnDist(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnMinNA(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnMaxNA(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnInt(MM::PropertyBase* pPropt, MM::ActionType eAct);
   int OnType(MM::PropertyBase* pPropt, MM::ActionType eAct);

private: // Private data
   const std::string name_;

   // Used in constructor, pre-init properties, and Initiazlie()
   std::vector<std::string> availableMonitors_;

   std::string monitorName_; // Empty string if test mode
   unsigned width_, height_;

   SLMWindowThread* windowThread_;
   SleepBlocker* sleepBlocker_;
   RefreshWaiter refreshWaiter_;

   bool shouldBlitInverted_;

   bool invert_;
   std::string inversionStr_;

   SLMColor monoColor_;
   std::string monoColorStr_;
   std::string pattern_;
   std::string type_;

   double distance_, numa_, radius_, maxrad_, minrad_, minna_, maxna_, DispWidth_, DispHeight_, intensity_;
   long double pixelsizeh_, pixelsizew_;
   long centerx_, centery_;
   unsigned char *indices_; //a dynamic two dimensional array pointer to the LED indices 
   double Rad(double Dist, double na);
   double minRad(double Dist, double minna);
   double maxRad(double Dist, double maxna);
   int BF();
   int DF();
   int Annul();
   int Off();
   int DPC(std::string type);

private:
   iPadSLM& operator=(const iPadSLM&);
};
