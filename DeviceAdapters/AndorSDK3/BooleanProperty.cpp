#include "BooleanProperty.h"
#include "AndorSDK3.h"
#include "SnapShotControl.h"

using namespace andor;
using namespace std;

static const char * const g_StatusON = "On";
static const char * const g_StatusOFF = "Off";


TBooleanProperty::TBooleanProperty(const string & MM_name, IBool * boolean_feature, CAndorSDK3Camera * camera,
                                   MySequenceThread * thd, SnapShotControl * snapShotController, bool readOnly)
: MM_name_(MM_name),
  boolean_feature_(boolean_feature),
  camera_(camera),
  thd_(thd),
  snapShotController_(snapShotController)
{
   CPropertyAction * pAct = new CPropertyAction (this, &TBooleanProperty::OnBoolean);
   camera_->CreateProperty(MM_name_.c_str(), g_StatusON, MM::String, readOnly, pAct);

   camera_->ClearAllowedValues(MM_name_.c_str());
   camera_->AddAllowedValue(MM_name_.c_str(), g_StatusON);
   camera_->AddAllowedValue(MM_name_.c_str(), g_StatusOFF);
   camera_->ApplyProperty(MM_name_.c_str());
}

TBooleanProperty::~TBooleanProperty()
{
   //Clean up memory, created as passed in
   camera_->GetCameraDevice()->Release(boolean_feature_);
}

// Action handler for OnBoolean
int TBooleanProperty::OnBoolean(MM::PropertyBase * pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(boolean_feature_->Get() ? g_StatusON : g_StatusOFF);
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

      string temp_s;
      pProp->Get(temp_s);
      if (boolean_feature_->IsWritable())
      {
         if (temp_s.compare(g_StatusON) == 0)
         {
            boolean_feature_->Set(true);
         }
         else
         {
            boolean_feature_->Set(false);
         }
      }

      if (was_poised)
      {
         snapShotController_->poiseForSnapShot();
      }
   }

   return DEVICE_OK;
}
