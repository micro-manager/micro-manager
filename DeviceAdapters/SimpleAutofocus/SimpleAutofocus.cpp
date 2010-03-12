///////////////////////////////////////////////////////////////////////////////
// FILE:          SimpleAutofocus.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   SimpleAutofocus controller adapter
// COPYRIGHT:     University of California, San Francisco, 2009
//
// AUTHOR:        Karl Hoover, UCSF
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
//
// CVS:           
//



#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif


#include "SimpleAutofocus.h"
#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>

#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"
#include "boost/lexical_cast.hpp"
#include "boost/tuple/tuple.hpp"
#include <set>

// property names:
// Controller
const char* g_ControllerName = "SimpleAutofocus";

const bool messageDebug = false;

class SAFPoint
{
public:
   SAFPoint( int seqNo, float z, float meanValue, float stdOverMeanScore, double hiPassScore):thePoint_( seqNo, z, meanValue,  stdOverMeanScore, hiPassScore )
   {
   }

   SAFPoint(  boost::tuple<int,float,float,float,double> apoint) : thePoint_(apoint){}

   bool operator<(const SAFPoint& that) const
   {
      // sort by Z position
      return (boost::tuples::get<1>(thePoint_) < boost::tuples::get<1>(that.thePoint_));
   }

    const std::string DataRow()
    {
       std::ostringstream data;
       data << std::setw(3) << boost::lexical_cast<std::string, int>( boost::tuples::get<0>(thePoint_) )<< "\t"
          << std::setprecision(5) << boost::tuples::get<1>(thePoint_) << "\t" // Z
          << std::setprecision(5) <<  boost::tuples::get<2>(thePoint_) << "\t" // mean
          << std::setprecision(6) << std::setiosflags(std::ios::scientific) << boost::tuples::get<3>(thePoint_) <<  std::resetiosflags(std::ios::scientific) << "\t"  // std / mean
          << std::setprecision(6) <<  std::setiosflags(std::ios::scientific) << boost::tuples::get<4>(thePoint_) << std::resetiosflags(std::ios::scientific) ;
       return data.str();
    }

 private:
   boost::tuple<int,float,float,float,double> thePoint_;

};

class SAFData
{
   std::set< SAFPoint > points_;

public:
   void InsertPoint( int seqNo, float z, float meanValue,float stdOverMeanScore,  double hiPassScore)
   {
      // SAFPoint value(int seqNo, float z, float meanValue,  float stdOverMeanScore, double hiPassScore);
      // VS 2008 thinks above line is a function decl !!!
      boost::tuple<int,float,float,float,double> vals(seqNo, z, meanValue, stdOverMeanScore,  hiPassScore );
      SAFPoint value(vals);
      points_.insert(value);
   }

   // default dtor is ok

   void Clear()
   {
      points_.clear();
   }
   const std::string Table()
   {
      std::ostringstream data;
      data << "Acq#\t Z\t Mean\t  Std / Mean\t Hi Pass Score";
      std::set< SAFPoint >::iterator ii;
      for( ii = points_.begin(); ii!=points_.end(); ++ii)
      {
         data << "\n";
         data << ii->DataRow();
      }
      return data.str();
   }
};



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_ControllerName, "SimpleAutofocus Finder");
   
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_ControllerName) == 0)
   {
      // create Controller
      SimpleAutofocus* pSimpleAutofocus = new SimpleAutofocus(g_ControllerName);
      return pSimpleAutofocus;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// Controller implementation
// ~~~~~~~~~~~~~~~~~~~~

SimpleAutofocus::SimpleAutofocus(const char* name) : name_(name), pCore_(NULL), cropFactor_(0.2), busy_(false),
   coarseStepSize_(1.), coarseSteps_ (5), fineStepSize_ (0.3), fineSteps_ ( 5), threshold_( 0.1), enableAutoShuttering_(1), 
   sizeOfTempShortBuffer_(0), pShort_(NULL),latestSharpness_(0.), recalculate_(0), mean_(0.), standardDeviationOverMean_(0.), 
   pPoints_(NULL), pSmoothedIm_(NULL), sizeOfSmoothedIm_(0), offset_(0.)
{

}

int SimpleAutofocus::Shutdown()
{
return DEVICE_OK;
}


SimpleAutofocus::~SimpleAutofocus()
{
   if(NULL!=pPoints_)
      delete pPoints_;

   if( NULL!=pShort_)
      free(pShort_);

   if(NULL!=pSmoothedIm_)
      free(pSmoothedIm_);

   Shutdown();
}

bool SimpleAutofocus::Busy()
{
     return busy_;
}

void SimpleAutofocus::GetName(char* name) const
{
   assert(name_.length() < CDeviceUtils::GetMaxStringLength());
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}






// channels are not available during initialization....
void SimpleAutofocus::RefreshChannelsToSelect(void)
{
   std::vector<std::string> channelConfigs;
   std::vector<std::string> cf2;

   char value[MM::MaxStrLength];
   std::string coreChannelGroup;
   
   if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
      coreChannelGroup = std::string(value);

   if(  0 < coreChannelGroup.length())
   {
      LogMessage(" Core channel group is : " + coreChannelGroup, true);
      // this list of 'configs' is called 'presets' in the main UI
      if ( DEVICE_OK != pCore_->GetChannelConfigs(channelConfigs))
            LogMessage(" error retrieving channel configs! " , false);
      cf2 = channelConfigs;
   }
   cf2.push_back("");

   std::ostringstream os;
   os<<" channels in " << coreChannelGroup << ": ";
   std::vector<std::string>::iterator jj;
   for(jj = channelConfigs.begin(); jj != channelConfigs.end(); ++jj)
   {
      if( channelConfigs.begin() != jj)
         os << ", ";
      os << *jj;
   }
   LogMessage(os.str(), true);
   possibleChannels_ = cf2;
}

//const std::vector<std::string> SimpleAutofocus::RefreshChannelsToSelect(void)
//{
//   std::vector<std::string> channelConfigs;
//
//   char value[MM::MaxStrLength];
//   std::string coreChannelGroup;
//   
//   if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
//   {
//      coreChannelGroup = std::string(value);
//   }
//   LogMessage(" Core channel group is : " + coreChannelGroup, true);
//
//
//   // this list of 'configs' is called 'presets' in the main UI
//   if ( DEVICE_OK != pCore_->GetChannelConfigs(channelConfigs))
//   {
//         LogMessage(" error retrieving channel configs! " , false);
//   }
//   std::vector<std::string> cf2 = channelConfigs;
//
//   cf2.push_back("");
//
//   assert( cf2.size() == channelConfigs.size()+1);
//
//   return cf2;
//   
//
//}


int SimpleAutofocus::Initialize()
{

   if(NULL == pPoints_)
   {
      pPoints_ = new SAFData();
   }
   LogMessage("SimpleAutofocus::Initialize()");
   pCore_ = GetCoreCallback();


   // Set Exposure
   CPropertyAction *pAct = new CPropertyAction (this, &SimpleAutofocus::OnExposure);
   CreateProperty(MM::g_Keyword_Exposure, "10", MM::Integer, false, pAct); 

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnCoarseStepNumber);
   CreateProperty("CoarseSteps from center","5",MM::Integer, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnStepsizeCoarse);
   CreateProperty("CoarseStepSize","1.0",MM::Float, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnFineStepNumber);
   CreateProperty("FineSteps from center","5",MM::Integer, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnStepSizeFine);
   CreateProperty("FineStepSize","0.3",MM::Float, false, pAct);

   // Set the sharpness threshold
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnThreshold);
   CreateProperty("Threshold","0.1",MM::Float, false, pAct);

   // Set the cropping factor to speed up computation
   pAct = new CPropertyAction(this, &SimpleAutofocus::OnCropFactor);
   CreateProperty("ROI CropFactor","0.2",MM::Float, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnSharpnessScore);
   CreateProperty("SharpnessScore","0.0",MM::Float, true, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnMean);
   CreateProperty("Mean","0",MM::Float, true, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnEnableAutoShutter);
   CreateProperty("EnableAutoshutter","0",MM::Integer, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnRecalculate);
   CreateProperty("Re-acquire&Re-calculate","0",MM::Integer, false, pAct);


   pAct = new CPropertyAction(this, &SimpleAutofocus::OnStandardDeviationOverMean);
   CreateProperty("StandardDeviation/Mean","0",MM::Float, false, pAct);

   pAct = new CPropertyAction(this, &SimpleAutofocus::OnChannel);
   CreateProperty("Channel","",MM::String, false, pAct);
   AddAllowedValue("Channel","");
   AddAllowedValue("Channel","...");
   selectedChannelConfig_ = "";
   UpdateStatus();

   return DEVICE_OK;
}


// API

bool SimpleAutofocus::IsContinuousFocusLocked(){ 
   return locked_;} ;
int SimpleAutofocus::FullFocus(){ 
   return this->BruteForceSearch();
   };
int SimpleAutofocus::IncrementalFocus(){ 
   return -1;};
int SimpleAutofocus::GetLastFocusScore(double& score){
   score = latestSharpness_;
   return 0;};
int SimpleAutofocus::GetCurrentFocusScore(double& score){ 
   score = latestSharpness_ = SharpnessAtCurrentSettings();
   return 0;};
int SimpleAutofocus::AutoSetParameters(){ 
   return 0;};
int SimpleAutofocus::GetOffset(double &offset){ 
   offset = offset_;
   return 0;};
int SimpleAutofocus::SetOffset(double offset){ 
   offset_ = offset;
   return 0;};


/////////////////////////////////////////////
// Property Generators
/////////////////////////////////////////////




///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

// action interface
// ---------------
int SimpleAutofocus::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   try
   {
      if (eAct == MM::BeforeGet)
      {
         // retrieve value from the camera via the core
         double v;
         pCore_->GetExposure(v);
         pProp->Set(v);
      }
      else if (eAct == MM::AfterSet)
      {
         // set the value to the camera via the core
         double val;
         pProp->Get(val);
         pCore_->SetExposure(val);
      }
   }
   catch(CMMError& e)
   {
      return e.getCode();

   }
   catch(...)
   {
      return DEVICE_ERR;
   }
   return DEVICE_OK;
}


 
int SimpleAutofocus::OnCoarseStepNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{       
   if (eAct == MM::BeforeGet)      
   {
      pProp->Set(coarseSteps_);      
   }
   else if (eAct == MM::AfterSet)
   { 
      pProp->Get(coarseSteps_);      
   }   
   return DEVICE_OK;
};

int SimpleAutofocus::OnFineStepNumber(MM::PropertyBase* pProp, MM::ActionType eAct){       if (eAct == MM::BeforeGet)      {         pProp->Set(fineSteps_);      }      else if (eAct == MM::AfterSet)      {         pProp->Get(fineSteps_);      }   return DEVICE_OK;};;
int SimpleAutofocus::OnStepsizeCoarse(MM::PropertyBase* pProp, MM::ActionType eAct){       if (eAct == MM::BeforeGet)      {         pProp->Set(coarseStepSize_);      }      else if (eAct == MM::AfterSet)      {         pProp->Get(coarseStepSize_);      }   return DEVICE_OK;};;
int SimpleAutofocus::OnStepSizeFine(MM::PropertyBase* pProp, MM::ActionType eAct){       if (eAct == MM::BeforeGet)      {         pProp->Set(fineStepSize_);      }      else if (eAct == MM::AfterSet)      {         pProp->Get(fineStepSize_);      }   return DEVICE_OK;};;
int SimpleAutofocus::OnThreshold(MM::PropertyBase* pProp, MM::ActionType eAct){       if (eAct == MM::BeforeGet)      {         pProp->Set(threshold_);      }      else if (eAct == MM::AfterSet)      {         pProp->Get(threshold_);      }   return DEVICE_OK;};;
int SimpleAutofocus::OnEnableAutoShutter(MM::PropertyBase* pProp, MM::ActionType eAct){       if (eAct == MM::BeforeGet)      {         pProp->Set(enableAutoShuttering_);      }      else if (eAct == MM::AfterSet)      {         pProp->Get(enableAutoShuttering_);      }   return DEVICE_OK;};;

int SimpleAutofocus::OnRecalculate(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 
      if (eAct == MM::BeforeGet)
      {
         pProp->Set(recalculate_);
      }
      else if (eAct == MM::AfterSet)
      {
         pProp->Get(recalculate_);
         if( 0!= recalculate_)
         {
            latestSharpness_ = SharpnessAtCurrentSettings();
            recalculate_ = 0;
            pProp->Set(recalculate_);
         }
      }
   return DEVICE_OK;

};


int SimpleAutofocus::OnStandardDeviationOverMean(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(standardDeviationOverMean_);
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything for a read-only property
   }
   return DEVICE_OK;
};



int SimpleAutofocus::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 
   if (eAct == MM::BeforeGet)
   {
      
      RefreshChannelsToSelect();
      SetAllowedValues("Channel", possibleChannels_);

      std::vector<std::string>::iterator isThere = std::find(possibleChannels_.begin(),possibleChannels_.end(), selectedChannelConfig_);
      if( possibleChannels_.end() == isThere)
         selectedChannelConfig_ = "";

      //todo - triple check that this doesn't wipe out AF channel selections!!
      if( selectedChannelConfig_.length() < 1) // no channel is selected for AF
      {
         // get the channel selected for the mainframe
         char value[MM::MaxStrLength];
         std::string coreChannelGroup;
         if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
            coreChannelGroup = std::string(value);
         if( 0 < coreChannelGroup.length() )
         {
            pCore_->GetCurrentConfig(coreChannelGroup.c_str(),MM::MaxStrLength,value);
            selectedChannelConfig_ = std::string(value);
         }
      }
      pProp->Set(selectedChannelConfig_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(selectedChannelConfig_);
      if ( 0 < selectedChannelConfig_.length())
      {
         char value[MM::MaxStrLength];
         std::string coreChannelGroup;
         if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
            coreChannelGroup = std::string(value);
         int ret = pCore_->SetConfig(coreChannelGroup.c_str(),selectedChannelConfig_.c_str());
	      if(ret != DEVICE_OK)
		      return ret;
      }
   }
   return DEVICE_OK;
};


//int SimpleAutofocus::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
//{ 
//   if (eAct == MM::BeforeGet)
//   {
//      // user can always surreptitiously change the 'channel group' without our knowing it!
//      /*const ? */ std::vector<std::string> possible = this->RefreshChannelsToSelect();
//      ClearAllowedValues("Channel");
//      SetAllowedValues("Channel", possible);
//      std::vector<std::string>::iterator isThere = std::find(possible.begin(),possible.end(), selectedChannelConfig_);
//      if( possible.end() == isThere)
//         selectedChannelConfig_ = "";
//      pProp->Set(selectedChannelConfig_.c_str());
//   }
//   else if (eAct == MM::AfterSet)
//   {
//      char value[MM::MaxStrLength];
//
//      pProp->Get(selectedChannelConfig_);
//      if( 0 < selectedChannelConfig_.length())
//      {
//         // need to find the 'channel group' so we can set the channel...  
//         pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value);
//         pCore_->SetConfig(value, selectedChannelConfig_.c_str());
//      }
//   }
//   return DEVICE_OK;
//};





int SimpleAutofocus::OnCropFactor(MM::PropertyBase* pProp, MM::ActionType eAct)
{ 
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(cropFactor_);
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(cropFactor_);
   }
   return DEVICE_OK;

};

int SimpleAutofocus::OnSharpnessScore(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(latestSharpness_);
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything for a read-only property
   }
   return DEVICE_OK;

}

int SimpleAutofocus::OnMean(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(mean_);
   }
   else if (eAct == MM::AfterSet)
   {
      // never do anything for a read-only property
   }
   return DEVICE_OK;
}





// computational utilities
short SimpleAutofocus::findMedian(short* arr, const int lengthMinusOne)
{ 
  short tmp;

   // n.b. this was ported from java, looks like a bubble sort....
  // todo use qsort
   for(int i=0; i<lengthMinusOne; ++i)
   {
      for(int j=0; j<lengthMinusOne-i; ++j)
      {
         if (arr[j+1]<arr[j])
         {
            tmp = arr[j];
            arr[j]=arr[j+1];
            arr[j+1]=tmp;
         }
      }
   }
   return arr[lengthMinusOne/2 +1];
}


double SimpleAutofocus::SharpnessAtCurrentSettings()
{
   busy_ = true;
   short windo[9];

   	int w0 = 0, h0 = 0, d0 = 0;
      double sharpness = 0;
      pCore_->GetImageDimensions(w0, h0, d0);
      
      int width =  (int)(cropFactor_*w0);
      int height = (int)(cropFactor_*h0);
      int ow = (int)(((1-cropFactor_)/2)*w0);
      int oh = (int)(((1-cropFactor_)/2)*h0);
      
      const unsigned long thisSize = sizeof(*pSmoothedIm_)*width*height;

      if( thisSize != sizeOfSmoothedIm_)
      {
         if(NULL!=pSmoothedIm_)
           free(pSmoothedIm_);
         // malloc is faster than new...
         pSmoothedIm_ = (float*)malloc(thisSize);
         if(NULL!=pSmoothedIm_)
         {
            sizeOfSmoothedIm_ = thisSize;
         }
         else // todo throw out of here...
            return sharpness;
      }


      // copy from MM image to the working buffer

	   ImgBuffer image(w0,h0,d0);
      //snap an image
      const unsigned char* pI = reinterpret_cast<const unsigned char*>(pCore_->GetImage());
      const unsigned short* pSInput = reinterpret_cast<const unsigned short*>(pI);

      int iindex;
      bool legalFormat = false;
      // to keep it simple always copy to a short array
      switch( d0)
      {
      case 1:
         legalFormat = true;
         if( sizeOfTempShortBuffer_ != sizeof(short)*w0*h0)
         {
            if( NULL != pShort_)
               free(pShort_);
            // malloc is faster than new...
            pShort_ = (short*)malloc( sizeof(short)*w0*h0);
            if( NULL!=pShort_)
            {
               sizeOfTempShortBuffer_ = sizeof(short)*w0*h0;
            }
         }

         for(iindex = 0; iindex < w0*h0; ++iindex)
         {
            pShort_[iindex] = pI[iindex];
         }
         break;

      case 2:
         legalFormat = true;
         if( sizeOfTempShortBuffer_ != sizeof(short)*w0*h0)
         {
            if( NULL != pShort_)
               free(pShort_);
            pShort_ = (short*)malloc( sizeof(short)*w0*h0);
            if( NULL!=pShort_)
            {
               sizeOfTempShortBuffer_ = sizeof(short)*w0*h0;
            }
         }
         for(iindex = 0; iindex < w0*h0; ++iindex)
         {
            pShort_[iindex] = pSInput[iindex];
         }
         break;
      default:
         break;
      }

      if(legalFormat)
      {
         // calculate the standard deviation & mean

         long nPts = 0;
         mean_ = 0;
         double M2 = 0;
         double delta;

         // one-pass algorithm for mean and std from Welford / Knuth

         for (int i=0; i<width; i++)
         {
            for (int j=0; j<height; j++)
            {
               ++nPts;
               long value = pShort_[ow+i+ width*(oh+j)];
               delta = value - mean_;
               mean_ = mean_ + delta/nPts;
               M2 = M2 + delta*(value - mean_); // # This expression uses the new value of mean_
            } 
         }

         //double variance_n = M2/nPts;
         double variance = M2/(nPts - 1);
         standardDeviationOverMean_ = 0.;
         double meanScaling = 1.;
         if( 0. != mean_)
         {
            standardDeviationOverMean_ = pow(variance,0.5)/mean_;
            meanScaling = 1./mean_;
         }


         LogMessage("N " + boost::lexical_cast<std::string,long>(nPts) + " mean " +  boost::lexical_cast<std::string,float>((float)mean_) + " nrmlzd std " +  boost::lexical_cast<std::string,float>((float)standardDeviationOverMean_) );

         // ToDO -- eliminate copy above.

         /*Apply 3x3 median filter to reduce shot noise*/
         for (int i=0; i<width; i++){
            for (int j=0; j<height; j++){

               windo[0] = pShort_[ow+i-1 + width*(oh+j-1)];
               windo[1] = pShort_[ow+i+ width*(oh+j-1)];
               windo[2] = pShort_[ow+i+1+ width*(oh+j-1)];
               windo[3] = pShort_[ow+i-1+ width*(oh+j)];
               windo[4] = pShort_[ow+i+ width*(oh+j)];
               windo[5] = pShort_[ow+i+1+ width*(oh+j)];
               windo[6] = pShort_[ow+i-1+ width*(oh+j+1)];
               windo[7] = pShort_[ow+i+ width*(oh+j+1)];
               windo[8] = pShort_[ow+i+1+ width*(oh+j+1)];

               pSmoothedIm_[i + j*width] = (float)((double)findMedian(windo,8)*meanScaling);
            } 
         }

         /*Edge detection using a 3x3 filter: [-2 -1 0; -1 0 1; 0 1 2]. Then sum all pixel values. Ideally, the sum is large if most edges are sharp*/

         for (int k=1; k<width-1; k++){
            for (int l=1; l<height-1; l++)
            {
               double convolvedValue = -2.0*pSmoothedIm_[k-1 + width*(l-1)] - pSmoothedIm_[k+ width*(l-1)]-pSmoothedIm_[k-1 + width*l]+pSmoothedIm_[k+1 + width*l]+pSmoothedIm_[k+ width*(l+1)]+2.0*pSmoothedIm_[k+1+ width*(l+1)];
               sharpness = sharpness + convolvedValue*convolvedValue;

            } 
         }

         //free(pShort);

      }
     // delete medPix;
      //delete windo;
      busy_ = false;
      latestSharpness_ = sharpness;
      return sharpness;
   }



void  SimpleAutofocus::Z(const double value)
{
   pCore_->SetFocusPosition(value);
}

double SimpleAutofocus::Z(void)
{
   double value;
   pCore_->GetFocusPosition(value);
   return value;
}


void SimpleAutofocus::Exposure(const int value)
{ 
   pCore_->SetExposure(value);
};

int SimpleAutofocus::Exposure(void){
   double value;
   pCore_->GetExposure(value);
   return (int)(0.5+value);
};


int SimpleAutofocus::BruteForceSearch()
{

   pPoints_->Clear();
   int acquisitionSequenceNumber = 0;
   double baseDist = 0.;
   double bestDist = 0.;
   double curSh = 0. ;
   double bestSh = 0.;
   MM::MMTime tPrev;
   MM::MMTime tcur;
   double curDist = Z();

   char value[MM::MaxStrLength];

   std::string coreChannelGroup;
   if( DEVICE_OK == pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreChannelGroup, value))
      coreChannelGroup = std::string(value);

   int ret = pCore_->SetConfig(coreChannelGroup.c_str(),selectedChannelConfig_.c_str());
	if(ret != DEVICE_OK)
		return ret;

   char shutterDeviceName[MM::MaxStrLength];
   pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, value);
   std::istringstream iss(value);
   int ivalue ;
   iss >> ivalue;
   bool previousAutoShutterSetting = static_cast<bool>(ivalue);
   bool currentAutoShutterSetting = previousAutoShutterSetting;

   // allow auto-shuttering or continuous illumination
   if((0==enableAutoShuttering_) && previousAutoShutterSetting)
   {
      pCore_->SetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, "0"); // disable auto-shutter
      currentAutoShutterSetting = false;
   }

   if( !currentAutoShutterSetting)
   {
      pCore_->GetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreShutter, shutterDeviceName);
      pCore_->SetDeviceProperty(shutterDeviceName, MM::g_Keyword_State, "1"); // open shutter
   }

   baseDist = curDist - coarseStepSize_ * coarseSteps_;

   // start of coarse search
   Z(baseDist);
   LogMessage("AF start coarse search range is  " + boost::lexical_cast<std::string,double>(baseDist) + " to " + boost::lexical_cast<std::string,double>(baseDist + coarseStepSize_*(2 * coarseSteps_)), messageDebug);
   for (int i = 0; i < 2 * coarseSteps_ + 1; ++i)
   {
      tPrev = GetCurrentMMTime();
      Z( baseDist + i * coarseStepSize_);
      curDist = Z();
      std::ostringstream progressMessage;
      progressMessage << "\nAF evaluation @ " + boost::lexical_cast<std::string,double>(curDist);
      curSh = SharpnessAtCurrentSettings();
      pPoints_->InsertPoint(acquisitionSequenceNumber++,(float)curDist,(float)mean_,(float)standardDeviationOverMean_,latestSharpness_);
      progressMessage <<  " AF metric is: " + boost::lexical_cast<std::string,double>(curSh);
      LogMessage( progressMessage.str(),  messageDebug);

      if (curSh > bestSh)
      {
         bestSh = curSh;
         bestDist = curDist;
      } else if (bestSh - curSh > threshold_ * bestSh)
      {
         break;
      }
      tcur = GetCurrentMMTime() - tPrev;
   }
   baseDist = bestDist - fineStepSize_ * fineSteps_;
   Z(baseDist);
   LogMessage("AF start fine search range is  " + boost::lexical_cast<std::string,double>(baseDist)+" to " + boost::lexical_cast<std::string,double>( baseDist+(2*fineSteps_)*fineStepSize_),  messageDebug);

   //Fine search
   for (int i = 0; i < 2 * fineSteps_ + 1; i++)
   {
      tPrev = GetCurrentMMTime();
      Z( baseDist + i * fineStepSize_);
      curDist = Z();
      std::ostringstream progressMessage;
      progressMessage << "\nAF evaluation @ " + boost::lexical_cast<std::string,double>(curDist);
      curSh = SharpnessAtCurrentSettings();
      pPoints_->InsertPoint(acquisitionSequenceNumber++,(float)curDist,(float)mean_,(float)standardDeviationOverMean_,latestSharpness_);
      progressMessage <<  " AF metric is: " + boost::lexical_cast<std::string,double>(curSh);
      LogMessage( progressMessage.str(),  messageDebug);

      if (curSh > bestSh)
      {
         bestSh = curSh;
         bestDist = curDist;
      } else if (bestSh - curSh > threshold_ * bestSh)
      {
         break;
      }
      tcur = GetCurrentMMTime() - tPrev;
   }
   LogMessage("AF best position is " + boost::lexical_cast<std::string,double>(bestDist),  messageDebug);
   LogMessage("AF Performance Table:\n" + pPoints_->Table(), messageDebug);
   if( !currentAutoShutterSetting)
   {
      pCore_->SetDeviceProperty(shutterDeviceName, MM::g_Keyword_State, "0"); // close
   }
   pCore_->SetDeviceProperty(MM::g_Keyword_CoreDevice, MM::g_Keyword_CoreAutoShutter, previousAutoShutterSetting?"1":"0"); // restore auto-shutter

  Z(bestDist);
  latestSharpness_ = bestSh;
  return DEVICE_OK;
}