#include "AOIProperty.h"
#include "AndorSDK3.h"
#include "SnapShotControl.h"
#include "atcore++.h"
#include <vector>

using namespace andor;
using namespace std;

static const unsigned int NUMBER_PREDEFINED_AOIS = 9;

static const char * const AOI_LIST[NUMBER_PREDEFINED_AOIS] =
{
   "Full Image",
   "2544x2160",
   "2064x2048",
   "1776x1760",
   "1920x1080",
   "1392x1040",
   " 528x512",
   " 240x256",
   " 144x128"
};

static const char * const g_Custom = "Custom AOI";

TAOIProperty::TAOIProperty(const std::string MM_name, CAndorSDK3Camera * camera, IDevice * device_hndl,
                           MySequenceThread * thd, SnapShotControl * snapShotController, bool readOnly)
: MM_name_(MM_name),
  camera_(camera),
  thd_(thd),
  device_hndl_(device_hndl),
  snapShotController_(snapShotController),
  pbProp_(NULL),
  fullAoiControl_(false),
  aoi_stride_(NULL),
  leftOffset_(1),
  topOffset_(1)
{
   // Create the atcore++ objects needed to control the AIO
   aoi_height_ = device_hndl_->GetInteger(L"AOIHeight");
   aoi_width_ = device_hndl_->GetInteger(L"AOIWidth");
   aoi_top_ = device_hndl_->GetInteger(L"AOITop");
   aoi_left_ = device_hndl_->GetInteger(L"AOILeft");

   // Create the Micro-Manager property
   CPropertyAction * pAct = new CPropertyAction (this, &TAOIProperty::OnAOI);
   camera_->CreateProperty(MM_name_.c_str(), AOI_LIST[0], MM::String, readOnly, pAct);

   IBool * full_aoi_control(NULL);
   try
   {
      full_aoi_control = device_hndl_->GetBool(L"FullAOIControl");
      if (full_aoi_control->IsImplemented())
      {
         fullAoiControl_ = full_aoi_control->Get();
         //if fullAOIControl is implemented, then Stride will be also
         aoi_stride_ = device_hndl_->GetInteger(L"AOIStride");
      }
      device_hndl_->Release(full_aoi_control);

   }
   catch (NotImplementedException &)
   {
      device_hndl_->Release(full_aoi_control);
   }

   // Set the list of valid AIOs
   aoiWidthIndexMap_[2592] = 0;
   aoiWidthHeightMap_[2592] = 2160;
   aoiWidthIndexMap_[2544] = 1;
   aoiWidthHeightMap_[2544] = 2160;
   aoiWidthIndexMap_[2064] = 2;
   aoiWidthHeightMap_[2064] = 2048;
   aoiWidthIndexMap_[1776] = 3;
   aoiWidthHeightMap_[1776] = 1760;
   aoiWidthIndexMap_[1920] = 4;
   aoiWidthHeightMap_[1920] = 1080;
   aoiWidthIndexMap_[1392] = 5;
   aoiWidthHeightMap_[1392] = 1040;
   aoiWidthIndexMap_[528] = 6;
   aoiWidthHeightMap_[528] = 512;
   aoiWidthIndexMap_[240] = 7;
   aoiWidthHeightMap_[240] = 256;
   aoiWidthIndexMap_[144] = 8;
   aoiWidthHeightMap_[144] = 128;

   camera_->ClearAllowedValues(MM_name_.c_str());
   for (unsigned int ui = 0; ui < NUMBER_PREDEFINED_AOIS; ++ui)
   {
      camera_->AddAllowedValue(MM_name_.c_str(), AOI_LIST[ui], ui);
   }
   if (fullAoiControl_)
   {
      camera_->AddAllowedValue(MM_name_.c_str(), g_Custom, NUMBER_PREDEFINED_AOIS);
      aoiWidthIndexMap_.erase(aoiWidthIndexMap_.find(2592));
      aoiWidthIndexMap_[2560] = 0;
      aoiWidthHeightMap_.erase(aoiWidthHeightMap_.find(2592));
      aoiWidthHeightMap_[2560] = 2160;
   }

}

TAOIProperty::~TAOIProperty()
{
   device_hndl_->Release(aoi_height_);
   device_hndl_->Release(aoi_width_);
   device_hndl_->Release(aoi_left_);
   device_hndl_->Release(aoi_top_);
   device_hndl_->Release(aoi_stride_);
}

int TAOIProperty::OnAOI(MM::PropertyBase * pProp, MM::ActionType eAct)
{
   if (pbProp_ == NULL)
   {
      pbProp_ = dynamic_cast<MM::Property *>(pProp);
   }

   int binning = camera_->GetBinning();
   if (eAct == MM::BeforeGet)
   {
      AT_64 aoi_width = aoi_width_->Get();
      aoi_width *= binning;

      if (aoiWidthIndexMap_.count(aoi_width) > 0)
      {
         int index = aoiWidthIndexMap_[aoi_width];
         pProp->Set(AOI_LIST[index]);
      }
      else
      {
         pProp->Set(g_Custom);
      }
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

      IInteger * sensorWidth = device_hndl_->GetInteger(L"SensorWidth");
      IInteger * sensorHeight = device_hndl_->GetInteger(L"SensorHeight");

      long data = 0;
      camera_->GetCurrentPropertyData(MM_name_.c_str(), data);

      TMapAOIIndexType::iterator iter = aoiWidthIndexMap_.begin();
      TMapAOIIndexType::iterator iterEnd = aoiWidthIndexMap_.end();
      while (iter != iterEnd && iter->second != data)
      {
         ++iter;
      }

      if (iter != iterEnd)
      {
         try
         {
            aoi_width_->Set(iter->first / binning);
         }
         catch (exception & e)
         {
            camera_->LogMessage(e.what());
         }

         AT_64 i64_sensorWidth = sensorWidth->Get();
         AT_64 i64_TargetLeft = (i64_sensorWidth - (aoi_width_->Get() * binning)) / 2 + 1;
         int i_err = 0;
         do
         {
            try
            {
               aoi_left_->Set(i64_TargetLeft);
               i_err = 1;
            }
            catch (OutOfRangeException &)
            {
               i64_TargetLeft--;
            }
         }
         while (i_err != 1 && i64_TargetLeft > 0);

         try
         {
            aoi_height_->Set((aoiWidthHeightMap_[iter->first]) / binning);
         }
         catch (exception & e)
         {
            camera_->LogMessage(e.what());
         }

         AT_64 i64_sensorHeight = sensorHeight->Get();
         AT_64 i64_TargetTop = (i64_sensorHeight - (aoi_height_->Get() * binning)) / 2 + 1;
         i64_TargetTop -= (i64_TargetTop - 1) % 8;
         try
         {
            aoi_top_->Set(i64_TargetTop);
         }
         catch (exception & e)
         {
            camera_->LogMessage(e.what());
         }
         leftOffset_ = aoi_left_->Get();
         topOffset_ = aoi_top_->Get();

      }
      else
      {
         //using ROI from ImageJ - should auto setup buffer size etc
      }

      device_hndl_->Release(sensorHeight);
      device_hndl_->Release(sensorWidth);

      camera_->ResizeImageBuffer();

      if (was_poised)
      {
         snapShotController_->poiseForSnapShot();
      }
   }

   return DEVICE_OK;
}

unsigned TAOIProperty::GetHeight()
{
   return static_cast<unsigned>(aoi_height_->Get());
}

unsigned TAOIProperty::GetWidth()
{
   return static_cast<unsigned>(aoi_width_->Get());
}

unsigned TAOIProperty::GetBytesPerPixel()
{
   unsigned ret;
   IFloat * bytesPerPixel = device_hndl_->GetFloat(L"BytesPerPixel");
   double d_temp = bytesPerPixel->Get();
   if (d_temp < 2)
   {
      ret = 2;
   }
   else
   {
      ret = static_cast<unsigned>(d_temp);
   }
   device_hndl_->Release(bytesPerPixel);
   return ret;
}

unsigned TAOIProperty::GetStride()
{
   unsigned ret = 0;

   if (aoi_stride_)
   {
      ret = static_cast<unsigned>(aoi_stride_->Get());
   }
   else
   {
      ret = static_cast<unsigned>(GetWidth() * GetBytesPerPixelF());
   }

   return ret;
}

double TAOIProperty::GetBytesPerPixelF()
{
   double ret;
   IFloat * bytesPerPixel = device_hndl_->GetFloat(L"BytesPerPixel");
   ret = bytesPerPixel->Get();
   device_hndl_->Release(bytesPerPixel);
   return ret;
}

void TAOIProperty::SetReadOnly(bool set_to)
{
   if (pbProp_ != NULL)
   {
      pbProp_->SetReadOnly(set_to);
   }
}

void TAOIProperty::SetCustomAOISize(unsigned left, unsigned top, unsigned width, unsigned height)
{
   snapShotController_->leavePoisedMode();

   try
   {
      aoi_width_->Set(width);

      left += static_cast<unsigned>(leftOffset_);

      if (left > aoi_left_->Max())
      {
         left = static_cast<unsigned>(aoi_left_->Max());
      }

      aoi_left_->Set(left);

      aoi_height_->Set(height);

      top += static_cast<unsigned>(topOffset_);

      if (top > aoi_top_->Max())
      {
         top = static_cast<unsigned>(aoi_top_->Max());
      }

      aoi_top_->Set(top);
   }
   catch (exception & e)
   {
      camera_->LogMessage(e.what());
   }
   camera_->ResizeImageBuffer();
   if (pbProp_ != NULL)
   {
      pbProp_->Set(g_Custom);
   }
   snapShotController_->poiseForSnapShot();

}

void TAOIProperty::ResetToFullImage()
{
   camera_->SetProperty(MM_name_.c_str(), AOI_LIST[0]);
   leftOffset_ = 1;
   topOffset_ = 1;
}
