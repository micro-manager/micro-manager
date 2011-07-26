#include "AndorSDK3.h"
#include "AOIProperty.h"
#include "atcore++.h"
#include <vector>

using namespace andor;

const char* const g_FullImage = "Full Image";
const char* const g_2544x2160 = "2544x2160";
const char* const g_2064x2048 = "2064x2048";
const char* const g_1776x1760 = "1776x1760";
const char* const g_1920x1080 = "1920x1080";
const char* const g_1392x1040 = "1392x1040";
const char* const g_528x512   = " 528x512";
const char* const g_240x256   = " 240x256";
const char* const g_144x128   = " 144x128";

TAOIProperty::TAOIProperty(const std::string MM_name,
                           CAndorSDK3Camera* camera,
                           IDevice* device_hndl,
                           MySequenceThread* thd,
                           SnapShotControl* snapShotController,
                           bool readOnly) :
MM_name_(MM_name), camera_(camera), thd_(thd), device_hndl_(device_hndl),
snapShotController_(snapShotController)
{
   // Create the atcore++ objects needed to control the AIO
   aoi_height_ = device_hndl_->GetInteger(L"AOIHeight");
   aoi_width_ = device_hndl_->GetInteger(L"AOIWidth");
   aoi_top_ = device_hndl_->GetInteger(L"AOITop");
   aoi_left_ = device_hndl_->GetInteger(L"AOILeft");

   // Create the Micro-Manager property
   CPropertyAction *pAct = new CPropertyAction (this, &TAOIProperty::OnAOI);
   camera_->CreateProperty(MM_name_.c_str(), "", MM::String, readOnly, pAct);

   // Set the list of valid AIOs
   std::vector<std::string> allowed_values;
   camera_->ClearAllowedValues(MM_name_.c_str());
   allowed_values.push_back(g_FullImage);
   allowed_values.push_back(g_2544x2160);
   allowed_values.push_back(g_2064x2048);
   allowed_values.push_back(g_1776x1760);
   allowed_values.push_back(g_1920x1080);
   allowed_values.push_back(g_1392x1040);
   allowed_values.push_back(g_528x512);
   allowed_values.push_back(g_240x256);
   allowed_values.push_back(g_144x128);
   camera_->SetAllowedValues(MM_name_.c_str(), allowed_values);

   pbProp_ = NULL;
}

TAOIProperty::~TAOIProperty()
{
   device_hndl_->Release(aoi_height_);
   device_hndl_->Release(aoi_width_);
   device_hndl_->Release(aoi_left_);
   device_hndl_->Release(aoi_top_);
}

int TAOIProperty::OnAOI(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if(pbProp_ == NULL) {
      pbProp_ = static_cast<MM::Property*>(pProp);
   }

   if (eAct == MM::BeforeGet) {
      AT_64 aoi_width = aoi_width_->Get();

      switch (aoi_width) {
      case 2592:
         pProp->Set(g_FullImage);
         break;
      case 2544:
         pProp->Set(g_2544x2160);
         break;
      case 2064:
         pProp->Set(g_2064x2048);
         break;
      case 1776:
         pProp->Set(g_1776x1760);
         break;
      case 1920:
         pProp->Set(g_1920x1080);
         break;
      case 1392:
         pProp->Set(g_1392x1040);
         break;
      case 528:
         pProp->Set(g_528x512);
         break;
      case 240:
         pProp->Set(g_240x256);
         break;
      case 144:
         pProp->Set(g_144x128);
         break;
      }
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

      if (temp_s.compare(g_FullImage) == 0) {
         aoi_width_->Set(2592);
         aoi_height_->Set(2160);
      }
      else if (temp_s.compare(g_2544x2160) == 0) {
         aoi_width_->Set(2544);
         aoi_height_->Set(2160);
      }
      else if (temp_s.compare(g_2064x2048) == 0) {
         aoi_width_->Set(2064);
         aoi_height_->Set(2048);
      }
      else if (temp_s.compare(g_1776x1760) == 0) {
         aoi_width_->Set(1776);
         aoi_height_->Set(1760);
      }
      else if (temp_s.compare(g_1920x1080) == 0) {
         aoi_width_->Set(1920);
         aoi_height_->Set(1080);
      }
      else if (temp_s.compare(g_1392x1040) == 0) {
         aoi_width_->Set(1392);
         aoi_height_->Set(1040);
      }
      else if (temp_s.compare(g_528x512) == 0) {
         aoi_width_->Set(528);
         aoi_height_->Set(512);
      }
      else if (temp_s.compare(g_240x256) == 0) {
         aoi_width_->Set(240);
         aoi_height_->Set(256);
      }
      else if (temp_s.compare(g_144x128) == 0) {
         aoi_width_->Set(144);
         aoi_height_->Set(128);
      }

      IInteger* sensorHeight = device_hndl_->GetInteger(L"SensorHeight");
      AT_64 i64_sensorHeight = sensorHeight->Get();
      AT_64 i64_TargetTop = (i64_sensorHeight - aoi_height_->Get()) / 2 + 1;
      i64_TargetTop -= (i64_TargetTop-1) % 8;
      aoi_top_->Set(i64_TargetTop);

      IInteger* sensorWidth = device_hndl_->GetInteger(L"SensorWidth");
      AT_64 i64_sensorWidth= sensorWidth->Get();
      AT_64 i64_TargetLeft = (i64_sensorWidth - aoi_width_->Get()) / 2 + 1;
      int i_err = 0;
      do {
         try {
            aoi_left_->Set(i64_TargetLeft);
            i_err = 1;
         }
         catch(OutOfRangeException e) {
            i64_TargetLeft--;
         }
      }
      while (i_err != 1 && i64_TargetLeft>0);

      camera_->ResizeImageBuffer();

      if(was_poised) {
         snapShotController_->poiseForSnapShot();
      }
   }

   return DEVICE_OK;
}

int TAOIProperty::GetHeight()
{
   return (int)aoi_height_->Get();
}

int TAOIProperty::GetWidth()
{
   return (int)aoi_width_->Get();
}

void TAOIProperty::SetReadOnly(bool set_to)
{
   if (pbProp_ != NULL) {
      pbProp_->SetReadOnly(set_to);
   }
}