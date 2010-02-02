///////////////////////////////////////////////////////////////////////////////
// FILE:          SimpleAF.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Image based autofocus module
//                
// AUTHOR:        Prashanth Ravindran 
//                prashanth@100ximaging.com, February, 2009
//
// COPYRIGHT:     100X Imaging Inc, 2009, http://www.100ximaging.com 
//
// Redistribution and use in source and binary forms, with or without modification, are 
// permitted provided that the following conditions are met:
//
//     * Redistributions of source code must retain the above copyright 
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright 
//       notice, this list of conditions and the following disclaimer in 
//       the documentation and/or other materials provided with the 
//       distribution.
//     * Neither the name of 100X Imaging Inc nor the names of its 
//       contributors may be used to endorse or promote products 
//       derived from this software without specific prior written 
//       permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
// CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
// NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
// STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
// ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

#pragma once;

# include "../../MMDevice/MMDevice.h"
# include "../../MMDevice/DeviceBase.h"
# include "../../MMDevice/ImgBuffer.h"
# include <string>
# include <ctime>
#include "SimpleAFImageUtils.h"

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_AF_IMAGE_ERROR          10010
#define ERR_AF_DEBUG_BUFFER_ERROR   10011

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
