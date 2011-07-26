#include "AndorSDK3.h"
#include "EnumProperty.h"
#include "atcore++.h"
#include <vector>

using namespace andor;

TEnumProperty::TEnumProperty(const std::string MM_name,
                             IEnum* enum_feature,
                             CAndorSDK3Camera* camera,
                             MySequenceThread* thd,
                             SnapShotControl* snapShotController,
                             bool readOnly) :
MM_name_(MM_name), enum_feature_(enum_feature), camera_(camera), thd_(thd),
snapShotController_(snapShotController)
{
   CPropertyAction *pAct = new CPropertyAction (this, &TEnumProperty::OnEnum);
   camera_->CreateProperty(MM_name_.c_str(), "", MM::String, readOnly, pAct);

   try {
      enum_feature_->Attach(this);
   }
   catch (NotImplementedException e) {
      // Callback not implemented for this feature
   }
}

TEnumProperty::~TEnumProperty()
{
}

void TEnumProperty::Update(ISubject* /*Subject*/)
{
   // This property has been changed in SDK3. The new value will be set by a
   // call to TEnumProperty::OnEnum, in here reset the list of allowed values
   std::vector<std::string> allowed_values;
   camera_->ClearAllowedValues(MM_name_.c_str());

   for (int i=0; i<enum_feature_->Count(); i++)
   {
      if (enum_feature_->IsIndexAvailable(i)) {
         std::wstring value_ws = enum_feature_->GetStringByIndex(i);
         std::string value_s(value_ws.begin(), value_ws.end());
         allowed_values.push_back(value_s.c_str());
      }
   }
   camera_->SetAllowedValues(MM_name_.c_str(), allowed_values);
}

// Action handler for OnEnum
int TEnumProperty::OnEnum(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      std::wstring temp_ws = enum_feature_->GetStringByIndex(enum_feature_->GetIndex());
      const wchar_t* wp = temp_ws.c_str();
      int length = temp_ws.length();
      char* gain_string = new char[length+1];
      gain_string[length] = '\0';
      wcstombs(gain_string, wp, length);
      pProp->Set(gain_string);
      delete [] gain_string;
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
      
      if (enum_feature_->IsWritable()) {
         std::string temp_s;
         pProp->Get(temp_s);
         std::wstring temp_ws(temp_s.length(), L'');
         std::copy(temp_s.begin(), temp_s.end(), temp_ws.begin());
         enum_feature_->Set(temp_ws);
      }

      if(was_poised) {
         snapShotController_->poiseForSnapShot();
      }
   }

   return DEVICE_OK;
}