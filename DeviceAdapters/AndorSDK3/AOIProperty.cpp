#include "AOIProperty.h"
#include "CallBackManager.h"
#include "atcore++.h"

using namespace andor;
using namespace std;

static const unsigned int NUMBER_PREDEFINED_AOIS = 6;
static const unsigned int NUMBER_R2_PREDEFINED_AOIS = 9;

static const char * const AOI_R2LIST[NUMBER_R2_PREDEFINED_AOIS] =
{
   "Full Image",
   "2544x2160",
   "2064x2048",
   "1920x1080",
   "1776x1760",
   "1392x1040",
   " 528x512",
   " 240x256",
   " 144x128"
};

static const char * const AOI_LIST[NUMBER_PREDEFINED_AOIS] =
{
   "Full Image",
   "2048x2048",
   "1920x1080",
   "1392x1040",
   " 512x512",
   " 128x128"
};


TAOIProperty::TAOIProperty(const std::string & MM_name, ICallBackManager * callback, bool readOnly)
: callback_(callback),
  pbProp_(NULL),
  aoi_stride_(NULL),
  fullAoiControl_(false)
{
   // Create the atcore++ objects needed to control the AIO
   aoi_height_ = callback->GetCameraDevice()->GetInteger(L"AOIHeight");
   aoi_width_ = callback->GetCameraDevice()->GetInteger(L"AOIWidth");
   aoi_top_ = callback->GetCameraDevice()->GetInteger(L"AOITop");
   aoi_left_ = callback->GetCameraDevice()->GetInteger(L"AOILeft");

   // Create the Micro-Manager property
   CPropertyAction * pAct = new CPropertyAction (this, &TAOIProperty::OnAOI);
   callback->CPCCreateProperty(MM_name.c_str(), AOI_LIST[0], MM::String, readOnly, pAct);

   IBool * full_aoi_control(NULL);
   try
   {
      full_aoi_control = callback->GetCameraDevice()->GetBool(L"FullAOIControl");
      if (full_aoi_control->IsImplemented())
      {
         fullAoiControl_ = full_aoi_control->Get();
         //if fullAOIControl is implemented, then Stride will be also
         aoi_stride_ = callback->GetCameraDevice()->GetInteger(L"AOIStride");
      }
      callback->GetCameraDevice()->Release(full_aoi_control);

   }
   catch (NotImplementedException & e)
   {
      callback->GetCameraDevice()->Release(full_aoi_control);
      callback->CPCLog(e.what());
   }

   // Set the list of valid AIOs
   populateWidthMaps(fullAoiControl_);
   populateLeftTopVectors();

   if (fullAoiControl_)
   {
      for (unsigned int ui = 0; ui < NUMBER_PREDEFINED_AOIS; ++ui)
      {
         callback->CPCAddAllowedValueWithData(MM_name.c_str(), AOI_LIST[ui], ui);
      }
   }
   else
   {
      for (unsigned int ui = 0; ui < NUMBER_R2_PREDEFINED_AOIS; ++ui)
      {
         callback->CPCAddAllowedValueWithData(MM_name.c_str(), AOI_R2LIST[ui], ui);
      }
   }
}

TAOIProperty::~TAOIProperty()
{
   callback_->GetCameraDevice()->Release(aoi_height_);
   callback_->GetCameraDevice()->Release(aoi_width_);
   callback_->GetCameraDevice()->Release(aoi_left_);
   callback_->GetCameraDevice()->Release(aoi_top_);
   callback_->GetCameraDevice()->Release(aoi_stride_);
}

//Private
void TAOIProperty::populateWidthMaps(bool fullAoiControl)
{
   if (fullAoiControl)
   {
      aoiWidthIndexMap_[2560] = 0;
      aoiWidthHeightMap_[2560] = 2160;
      aoiWidthIndexMap_[2048] = 1;
      aoiWidthHeightMap_[2048] = 2048;
      aoiWidthIndexMap_[1920] = 2;
      aoiWidthHeightMap_[1920] = 1080;
      aoiWidthIndexMap_[1392] = 3;
      aoiWidthHeightMap_[1392] = 1040;
      aoiWidthIndexMap_[512] = 4;
      aoiWidthHeightMap_[512] = 512;
      aoiWidthIndexMap_[128] = 5;
      aoiWidthHeightMap_[128] = 128;
   }
   else
   {
      aoiWidthIndexMap_[2592] = 0;
      aoiWidthHeightMap_[2592] = 2160;
      aoiWidthIndexMap_[2544] = 1;
      aoiWidthHeightMap_[2544] = 2160;
      aoiWidthIndexMap_[2064] = 2;
      aoiWidthHeightMap_[2064] = 2048;
      aoiWidthIndexMap_[1920] = 3;
      aoiWidthHeightMap_[1920] = 1080;
      aoiWidthIndexMap_[1776] = 4;
      aoiWidthHeightMap_[1776] = 1760;
      aoiWidthIndexMap_[1392] = 5;
      aoiWidthHeightMap_[1392] = 1040;
      aoiWidthIndexMap_[528] = 6;
      aoiWidthHeightMap_[528] = 512;
      aoiWidthIndexMap_[240] = 7;
      aoiWidthHeightMap_[240] = 256;
      aoiWidthIndexMap_[144] = 8;
      aoiWidthHeightMap_[144] = 128;
   }
}

void TAOIProperty::populateLeftTopVectors()
{
   leftX_.clear();
   leftX_.resize(aoiWidthIndexMap_.size(), 0);
   topY_.clear();
   topY_.resize(aoiWidthIndexMap_.size(), 0);
   IInteger * sensorWidth = callback_->GetCameraDevice()->GetInteger(L"SensorWidth");
   IInteger * sensorHeight = callback_->GetCameraDevice()->GetInteger(L"SensorHeight");
   AT_64 i64_sensorWidth = sensorWidth->Get();
   AT_64 i64_sensorHeight = sensorHeight->Get();
   callback_->GetCameraDevice()->Release(sensorHeight);
   callback_->GetCameraDevice()->Release(sensorWidth);

   TMapAOIIndexType::iterator iter = aoiWidthIndexMap_.begin();
   TMapAOIIndexType::iterator iterEnd = aoiWidthIndexMap_.end();
   for (; iter != iterEnd; ++iter)
   {
      AT_64 i64_TargetLeft = (i64_sensorWidth - iter->first) / 2 + 1;
      AT_64 i64_TargetTop = (i64_sensorHeight - aoiWidthHeightMap_[iter->first]) / 2 + 1;
      try
      {
         aoi_width_->Set(iter->first);
         aoi_left_->Set(i64_TargetLeft);
         aoi_height_->Set(aoiWidthHeightMap_[iter->first]);
         aoi_top_->Set(i64_TargetTop);
         leftX_[iter->second] = i64_TargetLeft;
         topY_[iter->second] = i64_TargetTop;
      }
      catch (OutOfRangeException & e)
      {
         callback_->CPCLog(e.what());
         findBestR2AOICoords(iter, i64_sensorWidth, i64_sensorHeight);
      }
   }
}

void TAOIProperty::findBestR2AOICoords(TMapAOIIndexType::iterator iter, AT_64 i64_sensorWidth, AT_64 i64_sensorHeight)
{
   try
   {
      aoi_width_->Set(iter->first);
   }
   catch (exception & e)
   {
      callback_->CPCLog(e.what());
   }

   AT_64 i64_TargetLeft = (i64_sensorWidth - iter->first) / 2 + 1;
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
      aoi_height_->Set(aoiWidthHeightMap_[iter->first]);
   }
   catch (exception & e)
   {
      callback_->CPCLog(e.what());
   }

   AT_64 i64_TargetTop = (i64_sensorHeight - aoiWidthHeightMap_[iter->first]) / 2 + 1;
   i64_TargetTop -= (i64_TargetTop - 1) % 8;
   try
   {
      aoi_top_->Set(i64_TargetTop);
   }
   catch (exception & e)
   {
      callback_->CPCLog(e.what());
   }
   leftX_.push_back(i64_TargetLeft);
   topY_.push_back(i64_TargetTop);
}

int TAOIProperty::OnAOI(MM::PropertyBase * pProp, MM::ActionType eAct)
{
   if (pbProp_ == NULL)
   {
      pbProp_ = dynamic_cast<MM::Property *>(pProp);
   }

   if (eAct == MM::BeforeGet)
   {
      if (!customStr_.empty())
      {
         pProp->Set(customStr_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)
   {
      string newValue;
      pProp->Get(newValue);
      long data = 0;
      pbProp_->GetData(newValue.c_str(), data);
      //Need check poised for Snap as camera running...
      if (callback_->IsSSCPoised())
      {
         callback_->SSCLeavePoised();
         setFeature(data);
         callback_->SSCEnterPoised();
      }
      else
      {
         callback_->CPCLog("Error - cannot set new AOI feature during MDA");
      }
   }

   return DEVICE_OK;
}

void TAOIProperty::setFeature(long data)
{
   TMapAOIIndexType::iterator iter = aoiWidthIndexMap_.begin();
   TMapAOIIndexType::iterator iterEnd = aoiWidthIndexMap_.end();
   int binning = callback_->CPCGetBinningFactor();
   while (iter != iterEnd && iter->second != data)
   {
      ++iter;
   }

   if (iter != iterEnd)
   {
      try
      {
         aoi_width_->Set(iter->first / binning);
         aoi_left_->Set(leftX_[data]);
         aoi_height_->Set(aoiWidthHeightMap_[iter->first] / binning);
         aoi_top_->Set(topY_[data]);
      }
      catch (OutOfRangeException & e)
      {
         callback_->CPCLog(e.what());
      }
   }
   else
   {
      //using ROI from ImageJ - should auto setup buffer size etc
   }
   callback_->CPCResizeImageBuffer();
}

AT_64 TAOIProperty::GetHeight()
{
   return aoi_height_->Get();
}

AT_64 TAOIProperty::GetWidth()
{
   return aoi_width_->Get();
}

AT_64 TAOIProperty::GetLeftOffset()
{
   //Micro-Manager image dims are zero based
   return aoi_left_->Get();
}

AT_64 TAOIProperty::GetTopOffset()
{
   //Micro-Manager image dims are zero based
   return aoi_top_->Get();
}

unsigned TAOIProperty::GetBytesPerPixel()
{
   unsigned ret;
   IFloat * bytesPerPixel = callback_->GetCameraDevice()->GetFloat(L"BytesPerPixel");
   double d_temp = bytesPerPixel->Get();
   if (d_temp < 2)
   {
      ret = 2;
   }
   else
   {
      ret = static_cast<unsigned>(d_temp);
   }
   callback_->GetCameraDevice()->Release(bytesPerPixel);
   return ret;
}

AT_64 TAOIProperty::GetStride()
{
   AT_64 ret = 0;

   if (aoi_stride_)
   {
      ret = aoi_stride_->Get();
   }
   else
   {
      ret = static_cast<AT_64>(GetWidth() * GetBytesPerPixelF());
   }

   return ret;
}

double TAOIProperty::GetBytesPerPixelF()
{
   double ret;
   IFloat * bytesPerPixel = callback_->GetCameraDevice()->GetFloat(L"BytesPerPixel");
   ret = bytesPerPixel->Get();
   callback_->GetCameraDevice()->Release(bytesPerPixel);
   return ret;
}

void TAOIProperty::SetReadOnly(bool set_to)
{
   if (pbProp_ != NULL)
   {
      pbProp_->SetReadOnly(set_to);
   }
}

const char* TAOIProperty::SetCustomAOISize(unsigned left, unsigned top, unsigned width, unsigned height)
{
   callback_->SSCLeavePoised();

   try
   {
      aoi_width_->Set(width);

      if (left > aoi_left_->Max())
      {
         left = static_cast<unsigned>(aoi_left_->Max());
      }
      aoi_left_->Set(left);
      aoi_height_->Set(height);

      if (top > aoi_top_->Max())
      {
         top = static_cast<unsigned>(aoi_top_->Max());
      }
      aoi_top_->Set(top);
   }
   catch (exception & e)
   {
      callback_->CPCLog(e.what());
   }
   stringstream ss;
   ss << "Custom AOI (" << width << "x" << height << ")";
   customStr_ = ss.str();
   SetReadOnly(true);
   callback_->SSCEnterPoised();
   return customStr_.c_str();
}

const char* TAOIProperty::ResetToFullImage()
{
   customStr_.clear();
   SetReadOnly(false);
   if (pbProp_ != NULL)
   {
      pbProp_->Set(AOI_LIST[0]);
      MM::StringProperty * strProp = dynamic_cast<MM::StringProperty *>(pbProp_);
      if (strProp)
      {
         strProp->Apply();
      }
   }
   return AOI_LIST[0];
}

