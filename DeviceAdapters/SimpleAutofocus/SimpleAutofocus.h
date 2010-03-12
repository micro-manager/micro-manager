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


// data for AF performance report table
class SAFData;


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

   void Z(const double value);
   double Z(void);
   
   int BruteForceSearch();
   
   // action interface
   // ---------------
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct); 
   int OnCoarseStepNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFineStepNumber(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepsizeCoarse(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeFine(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnThreshold(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCropFactor(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSharpnessScore(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEnableAutoShutter(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnMean(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRecalculate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStandardDeviationOverMean(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   double offset_; // TODO - need to know what this is.

   // parameters for the Pakpoom Subsoontorn & Hernan Garcia brute force search

   double coarseStepSize_;
   long coarseSteps_; // +/- #of snapshot
   double fineStepSize_;
   long fineSteps_;
   double threshold_;

   bool continuousFocusing_;
   bool locked_;
   std::string name_;
   SimpleAutofocus& operator=(SimpleAutofocus& /*rhs*/) {assert(false); return *this;};

   short findMedian(short* arr, const int leng );
   double SharpnessAtCurrentSettings();
   MM::Core* pCore_;
   double cropFactor_;
   bool busy_;
   double latestSharpness_;

   long enableAutoShuttering_;
   unsigned long sizeOfTempShortBuffer_;

   float* pSmoothedIm_;
   unsigned long sizeOfSmoothedIm_;

   short* pShort_;
   // a flag to trigger recalculation
   long recalculate_;
   double mean_;  
   double standardDeviationOverMean_;
   SAFData* pPoints_;

   std::string selectedChannelConfig_;
   std::vector<std::string> possibleChannels_;
   void RefreshChannelsToSelect(void);
   




};


#endif // _SIMPLEAUTOFOCUS_H_