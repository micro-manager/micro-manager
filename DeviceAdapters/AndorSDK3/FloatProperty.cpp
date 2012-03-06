#include "FloatProperty.h"
#include "AndorSDK3.h"

using namespace andor;
using namespace std;

TFloatProperty::TFloatProperty(const string & MM_name, IFloat * float_feature, CAndorSDK3Camera * camera,
                               MySequenceThread * thd, SnapShotControl * snapShotController, bool readOnly,
                               bool limited)
: MM_name_(MM_name),
  float_feature_(float_feature),
  camera_(camera),
  thd_(thd),
  snapShotController_(snapShotController),
  limited_(limited)
{
   CPropertyAction * pAct = new CPropertyAction (this, &TFloatProperty::OnFloat);
   camera_->CreateProperty(MM_name.c_str(), "", MM::Float, readOnly, pAct);

   try 
   {
      if (limited)
      {
         float_feature_->Attach(this);
      }
   }
   catch (exception & e)
   {
      // Callback not implemented for this feature
      camera_->LogMessage(e.what());
   }
}

TFloatProperty::~TFloatProperty()
{
   if (limited_)
   {
      try 
      {
         float_feature_->Detach(this);
      }
      catch (exception & e)
      {
         // Callback not implemented for this feature
         camera_->LogMessage(e.what());
      }
   }
   //Clean up memory, created as passed in
   camera_->GetCameraDevice()->Release(float_feature_);
}

void TFloatProperty::Update(ISubject * /*Subject*/)
{
   // This property has been changed in SDK3. The new value will be set by a
   // call to TFloatProperty::OnFloat, in here reset the limits
   if (limited_)
   {
      camera_->SetPropertyLimits(MM_name_.c_str(), float_feature_->Min(), float_feature_->Max());
   }
}

inline bool almostEqual(double val1, double val2, double precisionFactor)
{
   const double base = 10.0;
   double precisionError = 1.0 / pow(base, precisionFactor);
   // Check if val1 and val2 are within precision decimal places
   return ( val1 > (val2 - precisionError) && val1 < (val2 + precisionError)) ? true : false;
}

int TFloatProperty::OnFloat(MM::PropertyBase * pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(float_feature_->Get());
   }
   else if (eAct == MM::AfterSet)
   {
      double new_value;
      pProp->Get(new_value);
      if (!almostEqual(new_value, float_feature_->Get(), 6))
      {
         if (snapShotController_->isPoised())
         {
            if (!thd_->IsStopped())
            {
               camera_->StopSequenceAcquisition();
            }

            bool was_poised = false;
            if (snapShotController_->isPoised())
            {
               snapShotController_->leavePoisedMode();
               was_poised = true;
            }

            if (new_value < float_feature_->Min())
            {
               new_value = float_feature_->Min();
            }
            else if (new_value > float_feature_->Max())
            {
               new_value = float_feature_->Max();
            }
            float_feature_->Set(new_value);

            if (was_poised)
            {
               snapShotController_->poiseForSnapShot();
            }
         }
         else if (float_feature_->IsWritable())
         {
            if (new_value < float_feature_->Min())
            {
               new_value = float_feature_->Min();
            }
            else if (new_value > float_feature_->Max())
            {
               new_value = float_feature_->Max();
            }
            float_feature_->Set(new_value);
         }
      }
   }

   return DEVICE_OK;
}
