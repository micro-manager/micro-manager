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

#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"

#include <string>
//#include <iostream>
#include <vector>
#include <math.h>
#include <queue>
#include <algorithm>


#define ERR_IP_NO_AF_DEVICE   10100

// data for AF performance report table
class SAFData;

// computational utility functions

double GetScore(short* img, int w0, int h0, double cropFactor);

class SimpleAutofocus : public CAutoFocusBase<SimpleAutofocus>
{
public:
   SimpleAutofocus(const char *pName);
   virtual ~SimpleAutofocus();
   bool Busy();
   void GetName(char* name) const;
   int Initialize();
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
   int BrentSearch();

   // action interface
   // ---------------

   // these two properties are used only if non-0
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);

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
   int OnSearchAlgorithm(MM::PropertyBase* pProp, MM::ActionType eAct);


private:

   std::string name_;

   // parameters for the Pakpoom Subsoontorn & Hernan Garcia brute force search
   double offset_; // TODO - need to know what this is.
   double coarseStepSize_;
   long coarseSteps_; // +/- #of snapshot
   double fineStepSize_;
   long fineSteps_;
   double threshold_;

   bool continuousFocusing_;
   bool locked_;
   SimpleAutofocus& operator=(SimpleAutofocus& /*rhs*/) {
      assert(false);
      return *this;
   };

   // N.B  (note well) this utility MODIFIES the argument, make a copy yourself if you want the original data preserved
   template <class U> U FindMedian(std::vector<U>& values ) {
      std::sort(values.begin(), values.end());
      return values[(values.size())>>1];
   };

   double SharpnessAtZ(const double zvalue);
   double DoubleFunctionOfDouble(const double zvalue);

   MM::Core* pCore_;
   double cropFactor_;
   bool busy_;

   MMThreadLock busyLock_;
   double latestSharpness_;

   long enableAutoShuttering_;
   unsigned long sizeOfTempShortBuffer_;

   float* pSmoothedIm_;
   unsigned long sizeOfSmoothedIm_;

   unsigned short* pShort_;
   // a flag to trigger recalculation
   long recalculate_;
   double mean_;
   double standardDeviationOverMean_;
   SAFData* pPoints_;
   std::string selectedChannelConfig_;
   std::vector<std::string> possibleChannels_;
   void RefreshChannelsToSelect(void);
   std::string searchAlgorithm_;
   int acquisitionSequenceNumber_;

   double exposureForAutofocusAcquisition_;
   long binningForAutofocusAcquisition_; // over-ride the camera setting if this is non-0


   //std::multiset<float> mins_;
   //std::mulitset<float> maxs_;
   //const int nExtrema_ = 5;

   float min1_;
   float min2_;
   float max1_;
   float max2_;

   // this defines member functions that operate on evaluator DoubleFunctionOfDouble
#include "../../Util/Brent.h"


};

//////////////////////////////////////////////////////////////////////////////
// FocusMonitor class
// Focus monitoring module
//////////////////////////////////////////////////////////////////////////////
class FocusMonitor : public CImageProcessorBase<FocusMonitor>
{
public:
   FocusMonitor();
   ~FocusMonitor();

   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();

   void GetName(char* name) const;
   bool Busy();

   int AcqBeforeFrame();
   int AcqAfterFrame();

   // ImageProcessor API
   // ------------------
   int Process(unsigned char* buffer, unsigned width, unsigned height, unsigned byteDepth);

   // action interface
   // ----------------
   int OnCorrect(MM::PropertyBase* pProp, MM::ActionType eAct);

   friend class AFThread;

private:
   static const int QUEUE_SIZE = 5;
   bool initialized_;
   //ImageSharpnessScorer scorer_;
   std::queue<double> scoreQueue_;

   class AFThread : public MMDeviceThreadBase
   {
   public:
      AFThread(FocusMonitor* fm) : 
         delaySec_(2.0), 
         fm_(fm), 
         running_(false) 
      {}
      ~AFThread() {}

      int svc (void)
      {
         running_ = true;
         CDeviceUtils::SleepMs((long)(delaySec_*1000));
         int ret = fm_->DoAF();
         if (ret != DEVICE_OK)
         {
            std::ostringstream txt;
            txt << "Focus monitor AF failed with code " << ret;
            fm_->GetCoreCallback()->LogMessage(fm_, txt.str().c_str(), false);
         }
         running_ = false;
         return 0;
      }

      bool isRunning() {
         return running_;
      }
      void setDelaySec(double sec) {
         delaySec_ = sec;
      }

      double getDelaySec() {
         return delaySec_;
      }

   private:
      double delaySec_;
      FocusMonitor* fm_;
      bool running_;
   };

   AFThread* delayThd_;
   int DoAF();
};

//////////////////////////////////////////////////////////////////////////////
// ThreePointAF class
// Image based auto-focus. Sequential search.
//////////////////////////////////////////////////////////////////////////////
class ThreePointAF : public CAutoFocusBase<ThreePointAF>
{
public:
   ThreePointAF() ;
   ~ThreePointAF() {}

   // MMDevice API
   bool Busy() {
      return busy_;
   }
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown();

   // AutoFocus API
   int SetContinuousFocusing(bool state) {
      return state ? DEVICE_UNSUPPORTED_COMMAND : DEVICE_OK;
   }
   int GetContinuousFocusing(bool& state) {
      state = false;
      return DEVICE_OK;
   }
   bool IsContinuousFocusLocked() {
      return false;
   }
   int FullFocus();
   int IncrementalFocus() {
      return FullFocus();
   }
   int SetOffset(double offset);
   int GetOffset(double & offset);

   // Helper functions
   int GetLastFocusScore(double& score);
   int GetCurrentFocusScore(double& score);

   // properties
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepsize(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnFocusChannel(MM::PropertyBase * pProp, MM::ActionType eAct);
   int OnThreshold(MM::PropertyBase * pProp, MM::ActionType eAct);
   int OnCropFactor(MM::PropertyBase * pProp, MM::ActionType eAct);

private:
   bool					busy_;
   bool					initialized_;
   double				score_;
   double				cropFactor_;
   double				stepSize_;
   double            exposure_;
};

#endif // _SIMPLEAUTOFOCUS_H_
