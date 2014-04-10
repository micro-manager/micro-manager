#include "SpuriousNoiseFilterControl.h"
#include "atmcd32d.h"
#include "Andor.h"

using namespace std;

const char* g_SpuriousNoiseFilter                  = "SpuriousNoiseFilter";
const char* g_SpuriousNoiseFilterNone              = "None";
const char* g_SpuriousNoiseFilterMedian            = "Median";
const char* g_SpuriousNoiseFilterLevel             = "Level Above";
const char* g_SpuriousNoiseFilterIQRange           = "Interquartile Range";
const char* g_SpuriousNoiseFilterThreshold         = "SpuriousNoiseFilterThreshold";
const char* g_SpuriousNoiseFilterDescription       = "SpuriousNoiseFilterDescription";

const char* g_SpuriousNoiseFilterDescriptionNone   = "Spurious Noise Filter is Off";
const char* g_SpuriousNoiseFilterDescription2to10  = "Recommended Range 2-10";
const char* g_SpuriousNoiseFilterDescription20to50 = "Recommended Range 20-50";

SpuriousNoiseFilterControl::SpuriousNoiseFilterControl(AndorCamera * cam) :
spuriousNoiseFilterThreshold_(0.0),
camera_(cam),
currentMode_(NONE)
{
   InitialiseMaps();
   CreateProperties();
}

inline bool almostEqual(double val1, double val2, int precisionFactor)
{
   const double base = 10.0;
   double precisionError = 1.0 / pow(base, precisionFactor);
   // Check if val1 and val2 are within precision decimal places
   return ( val1 > (val2 - precisionError) && val1 < (val2 + precisionError)) ? true : false;
}

void SpuriousNoiseFilterControl::InitialiseMaps()
{
   modes_.clear();
   modes_[NONE]    = g_SpuriousNoiseFilterNone;
   modes_[MEDIAN]  = g_SpuriousNoiseFilterMedian;
   modes_[LEVEL]   = g_SpuriousNoiseFilterLevel;
   modes_[IQRANGE] = g_SpuriousNoiseFilterIQRange;

   modeDescriptions_.clear();
   modeDescriptions_[NONE]    = g_SpuriousNoiseFilterDescriptionNone;
   modeDescriptions_[MEDIAN]  = g_SpuriousNoiseFilterDescription2to10;
   modeDescriptions_[LEVEL]   = g_SpuriousNoiseFilterDescription20to50;
   modeDescriptions_[IQRANGE] = g_SpuriousNoiseFilterDescription2to10;
}

SpuriousNoiseFilterControl::MODE SpuriousNoiseFilterControl::GetMode(const char * mode)
{
   MODEMAP::const_iterator it;
   std::string strMode = mode;
   for (it = modes_.begin(); it != modes_.end(); ++it)
      if (0 == strMode.compare(it->second))
         return it->first;

   return NONE;
}

void SpuriousNoiseFilterControl::CreateProperties()
{
   DriverGuard dg(camera_);
   AndorCapabilities caps;
   caps.ulSize=sizeof(caps);
   GetCapabilities(&caps);

   if(caps.ulFeatures & AC_FEATURES_REALTIMESPURIOUSNOISEFILTER)
   {
      CPropertyAction* pAct = new CPropertyAction(this, &SpuriousNoiseFilterControl::OnSpuriousNoiseFilter);
      camera_->AddProperty(g_SpuriousNoiseFilter, g_SpuriousNoiseFilterNone, MM::String, false, pAct);
      
      vector<string> CCValues;
      CCValues.push_back(g_SpuriousNoiseFilterNone);
      CCValues.push_back(g_SpuriousNoiseFilterMedian);
      CCValues.push_back(g_SpuriousNoiseFilterLevel);
      if(AC_CAMERATYPE_IXONULTRA == caps.ulCameraType)
         CCValues.push_back(g_SpuriousNoiseFilterIQRange);
      
      camera_->SetAllowedValues(g_SpuriousNoiseFilter, CCValues);

      pAct = new CPropertyAction(this, &SpuriousNoiseFilterControl::OnSpuriousNoiseFilterThreshold);
      camera_->AddProperty(g_SpuriousNoiseFilterThreshold, "0.0", MM::Float, false, pAct);

      pAct = new CPropertyAction(this, &SpuriousNoiseFilterControl::OnSpuriousNoiseFilterDescription);
      camera_->AddProperty(g_SpuriousNoiseFilterDescription, "", MM::String, true, pAct);
   }
}


int SpuriousNoiseFilterControl::OnSpuriousNoiseFilter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   { 
      string spuriousNoiseFilterStr;
      pProp->Get(spuriousNoiseFilterStr);
      MODE newMode = GetMode(spuriousNoiseFilterStr.c_str());
      if(newMode == currentMode_)
        return DEVICE_OK;

      camera_->PrepareToApplySetting();
      {
         DriverGuard dg(camera_);
         unsigned int ret = Filter_SetMode(static_cast<long>(newMode));
         if (ret != DRV_SUCCESS)
           return DEVICE_CAN_NOT_SET_PROPERTY;
      }
      camera_->ResumeAfterApplySetting();

      currentMode_ = newMode;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(modes_[currentMode_]);
   }
   return DEVICE_OK;
}

int SpuriousNoiseFilterControl::OnSpuriousNoiseFilterThreshold(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      double spuriousNoiseFilterThreshold = 0;
      pProp->Get(spuriousNoiseFilterThreshold);
         
      if(almostEqual(spuriousNoiseFilterThreshold,spuriousNoiseFilterThreshold_,4))
        return DEVICE_OK;

      camera_->PrepareToApplySetting();
      {
         DriverGuard dg(camera_);
         unsigned int ret  = Filter_SetThreshold(static_cast<float>(spuriousNoiseFilterThreshold));
         if (ret != DRV_SUCCESS)
           return DEVICE_CAN_NOT_SET_PROPERTY;
      }
      camera_->ResumeAfterApplySetting();
  }
  else if (eAct == MM::BeforeGet)
  {
      pProp->Set(spuriousNoiseFilterThreshold_);
  }
  return DEVICE_OK;
}  
  
int SpuriousNoiseFilterControl::OnSpuriousNoiseFilterDescription(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(modeDescriptions_[currentMode_]);
   }
   return DEVICE_OK;
}