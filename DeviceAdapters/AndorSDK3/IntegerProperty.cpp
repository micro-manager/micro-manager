#include "IntegerProperty.h"
#include "AndorSDK3.h"
#include "SnapShotControl.h"

using namespace andor;
using namespace std;

TIntegerProperty::TIntegerProperty(const string & MM_name, IInteger * integer_feature, CAndorSDK3Camera * camera,
                                   MySequenceThread * thd, SnapShotControl * snapShotController, bool readOnly,
                                   bool limited)
: MM_name_(MM_name),
  integer_feature_(integer_feature),
  camera_(camera),
  thd_(thd),
  snapShotController_(snapShotController),
  limited_(limited)
{
   CPropertyAction * pAct = new CPropertyAction (this, &TIntegerProperty::OnInteger);
   camera_->CreateProperty(MM_name.c_str(), "", MM::Integer, readOnly, pAct);

   try
   {
      if (limited)
      {
         integer_feature_->Attach(this);
      }
   }
   catch (exception & e)
   {
      // Callback not implemented for this feature
      camera_->LogMessage(e.what());
   }
}

TIntegerProperty::~TIntegerProperty()
{
   if (limited_)
   {
      try 
      {
         integer_feature_->Detach(this);
      }
      catch (exception & e)
      {
         // Callback not implemented for this feature
         camera_->LogMessage(e.what());
      }
   }
   //Clean up memory, created as passed in
   camera_->GetCameraDevice()->Release(integer_feature_);
}

void TIntegerProperty::Update(ISubject * /*Subject*/)
{
   // This property has been changed in SDK3. The new value will be set by a
   // call to TIntegerProperty::OnInteger, in here reset the limits
   if (limited_)
   {
      camera_->SetPropertyLimits(MM_name_.c_str(), (long)integer_feature_->Min(), (long)integer_feature_->Max());
   }
}

int TIntegerProperty::OnInteger(MM::PropertyBase * pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)integer_feature_->Get());
   }
   else if (eAct == MM::AfterSet)
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

      long new_value;
      pProp->Get(new_value);
      if (new_value < integer_feature_->Min())
      {
         new_value = (long)integer_feature_->Min();
      }
      else if (new_value > integer_feature_->Max())
      {
         new_value = (long)integer_feature_->Max();
      }

      if (integer_feature_->IsWritable())
      {
         integer_feature_->Set(new_value);
      }

      if (was_poised)
      {
         snapShotController_->poiseForSnapShot();
      }
   }

   return DEVICE_OK;
}
