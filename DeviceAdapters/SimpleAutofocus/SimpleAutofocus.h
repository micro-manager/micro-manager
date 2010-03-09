///////////////////////////////////////////////////////////////////////////////
// FILE:          SimpleAutofocus.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   SimpleAutofocus controller adapter
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
// AUTHOR:        Karl Hoover, UCSF
//
//

#ifndef _SIMPLEAUTOFOCUS_H_
#define _SIMPLEAUTOFOCUS_H_

# include "../../MMDevice/MMDevice.h"
# include "../../MMDevice/DeviceBase.h"
# include "../../MMDevice/ImgBuffer.h"

#include <string>
//#include <iostream>
#include <vector>


   double SIZE_FIRST = 2;//
   int NUM_FIRST = 1; // +/- #of snapshot
    double SIZE_SECOND = 0.2;
    int NUM_SECOND = 5;
   double THRES = 0.02;
   double CROP_SIZE = 0.2; 


class SimpleAutofocus : public CAutoFocusBase<SimpleAutofocus>
{
public:
   SimpleAutofocus(const char *pName);
   virtual ~SimpleAutofocus();
   bool SimpleAutofocus::Busy();
   void SimpleAutofocus::GetName(char* name) const;
   int SimpleAutofocus::Initialize();
   int Shutdown();

   // AutoFocus API
   virtual int SetContinuousFocusing(bool state)
   { 
      continuousFocusing_ = state; 
      return DEVICE_OK;
   };
   
   virtual int GetContinuousFocusing(bool& state) 
   {
      state = continuousFocusing_; 
      return DEVICE_OK;
   };
   virtual bool IsContinuousFocusLocked();
   virtual int FullFocus();
   virtual int IncrementalFocus();
   virtual int GetLastFocusScore(double& score);
   virtual int GetCurrentFocusScore(double& score);
   virtual int AutoSetParameters();
   virtual int GetOffset(double &offset);
   virtual int SetOffset(double offset);

   void Exposure(const int value);
   int Exposure(void);

   void Z(const double value__);
   double Z(void);
   
   
   // action interface
   // ---------------
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct); 
   int OnSearchSpanCoarse(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSearchSpanFine(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepsizeCoarse(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeFine(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannelForAutofocus(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnThreshold(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCropFactor(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSharpnessScore(MM::PropertyBase* pProp, MM::ActionType eAct);


private:
   bool continuousFocusing_;
   bool locked_;
   std::string name_;
   SimpleAutofocus& operator=(SimpleAutofocus& /*rhs*/) {assert(false); return *this;};

   short findMedian(short* arr__, const int leng__ );
   double SharpnessAtCurrentSettings();
   MM::Core* pCore_;
   double cropFactor_;
   bool busy_;


};


#endif // _SIMPLEAUTOFOCUS_H_