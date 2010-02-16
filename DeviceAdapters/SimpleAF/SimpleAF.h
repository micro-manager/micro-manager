///////////////////////////////////////////////////////////////////////////////
// FILE:          SimpleAF.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Collection of auto-focusing algorithms and supporting devices
//                
// AUTHOR:        Prashanth Ravindran, prashanth@100ximaging.com, February, 2009
//                Nenad Amodaj, nenad@amodaj.com, February 2010
//
// COPYRIGHT:     100X Imaging Inc, 2010, http://www.100ximaging.com
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#pragma once;

# include "../../MMDevice/MMDevice.h"
# include "../../MMDevice/DeviceBase.h"
# include "../../MMDevice/ImgBuffer.h"
# include <string>
# include <ctime>
#include <queue>
#include "SimpleAFImageUtils.h"

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_AF_IMAGE_ERROR          10010
#define ERR_AF_DEBUG_BUFFER_ERROR   10011

#define ERR_IP_NO_AF_DEVICE   10100

// Metadata keywords

namespace MM
{
	const char* const g_Keyword_Metadata_Focus_Exposure		     = "Autofocus Exposure";
	const char* const g_Keyword_Metadata_Focus_FullSearchSpan     = "Autofocus Full Search Span";
	const char* const g_Keyword_Metadata_Focus_IncSearchSpan	     = "Autofocus Incremental Search Span";
	const char* const g_Keyword_Metadata_Focus_FullSearchStepSize = "Autofocus Full Search Stepsize";
	const char* const g_Keyword_Metadata_Focus_IncrementalStepSize= "Autofocus Incremental Search Stepsize";
	const char* const g_Keyword_Metadata_Focus_CornerThreshold    = "Autofocus Corner Threshold";
	const char* const g_Keyword_Metadata_Focus_Minimum_Acceptable_score = "Autofocus Minimum acceptable score for image sharpness";
}
//////////////////////////////////////////////////////////////////////////////
// DemoAutoFocus class
// Simulation of the auto-focusing module
//////////////////////////////////////////////////////////////////////////////

class SimpleAF : public CAutoFocusBase<SimpleAF>
{
public:
   SimpleAF() ;
   ~SimpleAF() {}
      
   // MMDevice API
   bool Busy() {return busy_;}
   void GetName(char* pszName) const;

   int Initialize();
   int Shutdown(){initialized_ = false; return DEVICE_OK;}

   // AutoFocus API
   int SetContinuousFocusing(bool state) {return state ? DEVICE_UNSUPPORTED_COMMAND : DEVICE_OK;}
   int GetContinuousFocusing(bool& state) {state = false; return DEVICE_OK;}
   bool IsContinuousFocusLocked() {return false;}
   int FullFocus();
   int IncrementalFocus();
   int SetOffset(double offset);
   int GetOffset(double & offset);
	
   // Helper functions
   int GetLastFocusScore(double& score);
   int GetCurrentFocusScore(double& score); 

   // properties
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepsizeCoarse(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStepSizeFine(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnChannelForAutofocus(MM::PropertyBase * pProp, MM::ActionType eAct);
   int OnSearchSpanCoarse(MM::PropertyBase * pProp, MM::ActionType eAct);
   int OnSearchSpanFine(MM::PropertyBase * pProp, MM::ActionType eAct);
   int OnThreshold(MM::PropertyBase * pProp, MM::ActionType eAct);
   int OnCropFactor(MM::PropertyBase * pProp, MM::ActionType eAct);
  /* int OnMaxNumberofStepsCoarse(MM::PropertyBase * pProp, MM::ActionType eAct);
   int OnMaxNumberofStepsFine(MM::PropertyBase * pProp, MM::ActionType eAct);*/
   int OnNumImagesAcquired(MM::PropertyBase * pProp, MM::ActionType eAct);



private:
   enum					FocusMode{FULLFOCUS, INCREMENTALFOCUS};
   bool					busy_;
   bool					initialized_;
   double				param_stepsize_fine_;
   double				param_stepsize_coarse_;
   double				score_;
   double				bestscore_;
   double				param_coarse_search_span_;
   double				param_fine_search_span_;
   double				param_afexposure_;
   double				param_decision_threshold_;
   std::string			param_channel_;
   double				param_crop_ratio_;
   
   double				activespan_;
   double				activestep_;
   std::clock_t     	start_;
   std::clock_t     	stop_;
   std::clock_t     	timestamp_;
   bool             	timemeasurement_;
   ExposureManager  	exposure_;
   ShutterManager   	shutter_;
   ImageSharpnessScorer scorer_;
   ReportingManager		reporter_;

   int				Focus(FocusMode);
   void				StartClock();
   long				GetElapsedTime();
   int				InitScope();
   int				RestoreScope();
   int				GetImageForAnalysis(ImgBuffer & ,bool stretch = true);
   double			GetScore(ImgBuffer & );
   int				totalimagessnapped_;

   std::vector<std::string> groups_;

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
   
   // ImageProcessor API
   // ------------------
   int Process(unsigned char* buffer, unsigned width, unsigned height, unsigned byteDepth);

   // action interface
   // ----------------
   int OnAFDevice(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   static const int QUEUE_SIZE = 5;
   bool initialized_;
   bool afNeeded_;
   ImageSharpnessScorer scorer_;
   std::queue<double> scoreQueue_;

   int DoAF();
};
