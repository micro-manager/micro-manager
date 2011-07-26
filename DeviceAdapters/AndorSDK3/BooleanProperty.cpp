#include "AndorSDK3.h"
#include "BooleanProperty.h"
#include "atcore++.h"
#include <vector>

using namespace andor;

TBooleanProperty::TBooleanProperty(const std::string MM_name,
                                   IBool* boolean_feature,
                                   CAndorSDK3Camera* camera,
                                   MySequenceThread* thd,
                                   SnapShotControl* snapShotController,
                                   bool readOnly) :
MM_name_(MM_name), boolean_feature_(boolean_feature), camera_(camera), thd_(thd),
snapShotController_(snapShotController)
{
   CPropertyAction *pAct = new CPropertyAction (this, &TBooleanProperty::OnBoolean);
   camera_->CreateProperty(MM_name_.c_str(), "", MM::String, readOnly, pAct);

   
   std::vector<std::string> allowed_values;
   camera_->ClearAllowedValues(MM_name_.c_str());
   allowed_values.push_back("On");
   allowed_values.push_back("Off");
   camera_->SetAllowedValues(MM_name_.c_str(), allowed_values);
}

TBooleanProperty::~TBooleanProperty()
{
}

// Action handler for OnEnum
int TBooleanProperty::OnBoolean(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(boolean_feature_->Get()?"On":"Off");
   }
   else if (eAct == MM::AfterSet) {
      if (!thd_->IsStopped()) {
         camera_->StopSequenceAcquisition();
      }

      bool was_poised = false;
      if (snapShotController_->isPoised()) {
         snapShotController_->leavePoisedMode();
         was_poised = true;
      }
      
      std::string temp_s;
      pProp->Get(temp_s);
      if (boolean_feature_->IsWritable()) {
         if(temp_s.compare("On") == 0) {
            boolean_feature_->Set(true);
         }
         else {
            boolean_feature_->Set(false);
         }
      }

      if(was_poised) {
         snapShotController_->poiseForSnapShot();
      }
   }

   return DEVICE_OK;
}