///////////////////////////////////////////////////////////////////////////////
// FILE:          FakeCamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   A camera implementation that is backed by the file system
//                Can access stage positions to choose image to display
//
// AUTHOR:        Lukas Lang
//
// COPYRIGHT:     2017 Lukas Lang
// LICENSE:       Licensed under the Apache License, Version 2.0 (the "License");
//                you may not use this file except in compliance with the License.
//                You may obtain a copy of the License at
//                
//                http://www.apache.org/licenses/LICENSE-2.0
//                
//                Unless required by applicable law or agreed to in writing, software
//                distributed under the License is distributed on an "AS IS" BASIS,
//                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//                See the License for the specific language governing permissions and
//                limitations under the License.

#include "FakeCamera.h"

const char* cameraName = "FakeCamera";

const char* label_CV_8U = "8bit";
const char* label_CV_16U = "16bit";
const char* label_CV_8UC4 = "32bitRGB";
const char* label_CV_16UC4 = "64bitRGB";

FakeCamera::FakeCamera() :
  initialized_(false),
  path_(""),
  roiX_(0),
  roiY_(0),
  capturing_(false),
  byteCount_(1),
  type_(CV_8UC1),
  emptyImg(1, 1, type_),
  color_(false),
  exposure_(10)
{
  resetCurImg();

  CreateProperty("Path Mask", "", MM::String, false, new CPropertyAction(this, &FakeCamera::OnPath));

  CreateProperty(MM::g_Keyword_Name, cameraName, MM::String, true);

  // Description
  CreateProperty(MM::g_Keyword_Description, "Loads images from disk according to position of focusing stage", MM::String, true);

  // CameraName
  CreateProperty(MM::g_Keyword_CameraName, "Fake camera adapter", MM::String, true);

  // CameraID
  CreateProperty(MM::g_Keyword_CameraID, "V1.0", MM::String, true);

  // binning
  CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false);

  std::vector<std::string> binningValues;
  binningValues.push_back("1");

  SetAllowedValues(MM::g_Keyword_Binning, binningValues);

  CreateStringProperty(MM::g_Keyword_PixelType, label_CV_8U, false, new CPropertyAction(this, &FakeCamera::OnPixelType));

  std::vector<std::string> pixelTypeValues;
  pixelTypeValues.push_back(label_CV_8U);
  pixelTypeValues.push_back(label_CV_16U);
  pixelTypeValues.push_back(label_CV_8UC4);
  pixelTypeValues.push_back(label_CV_16UC4);

  SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
  
  SetErrorText(ERR_INVALID_DEVICE_NAME, "Specified stage name is invalid");
  SetErrorText(OUT_OF_RANGE, "Parameters out of range");

  InitializeDefaultErrorMessages();
}

FakeCamera::~FakeCamera()
{
}

int FakeCamera::Initialize()
{
  if (initialized_)
     return DEVICE_OK;

  initSize_ = false;

  initialized_ = true;

  return DEVICE_OK;
}

int FakeCamera::Shutdown()
{
  initialized_ = false;

  return DEVICE_OK;
}

void FakeCamera::GetName(char * name) const
{
  CDeviceUtils::CopyLimitedString(name, cameraName);
}

long FakeCamera::GetImageBufferSize() const
{
  initSize();

  return roiWidth_ * roiHeight_ * GetImageBytesPerPixel();
}

unsigned FakeCamera::GetBitDepth() const
{
  initSize();

  return 8 * byteCount_;
}

int FakeCamera::GetBinning() const
{
  return 1;
}

int FakeCamera::SetBinning(int)
{
  return DEVICE_OK;
}

void FakeCamera::SetExposure(double exposure)
{
  exposure_ = exposure;
}

double FakeCamera::GetExposure() const
{
  return exposure_;
}

int FakeCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
  initSize();

  if (x + xSize > width_ || y + ySize > height_)
  {
     return OUT_OF_RANGE;
  }

  roiX_ = x;
  roiY_ = y;
  roiWidth_ = xSize;
  roiHeight_ = ySize;

  updateROI();

  return DEVICE_OK;
}

int FakeCamera::GetROI(unsigned & x, unsigned & y, unsigned & xSize, unsigned & ySize)
{
  initSize();

  x = roiX_;
  y = roiY_;
  xSize = roiWidth_;
  ySize = roiHeight_;

  return DEVICE_OK;
}

int FakeCamera::ClearROI()
{
  initSize();

  SetROI(0, 0, width_, height_);

  return DEVICE_OK;
}

int FakeCamera::IsExposureSequenceable(bool & isSequenceable) const
{
  isSequenceable = false;

  return DEVICE_OK;
}

const unsigned char * FakeCamera::GetImageBuffer()
{
  return roi_.data;
}

unsigned FakeCamera::GetNumberOfComponents() const
{
  return color_ ? 4 : 1;
}

const unsigned int* FakeCamera::GetImageBufferAsRGB32()
{
  return color_ ? (const unsigned int*)roi_.data : 0;
}

unsigned FakeCamera::GetImageWidth() const
{
  initSize();

  return roiWidth_;
}

unsigned FakeCamera::GetImageHeight() const
{
  initSize();

  return roiHeight_;
}

unsigned FakeCamera::GetImageBytesPerPixel() const
{
  return color_ ? 4 * byteCount_ : byteCount_;
}

int FakeCamera::SnapImage()
{
ERRH_START

  MM::MMTime start = GetCoreCallback()->GetCurrentMMTime();
  initSize();

  getImg();

  MM::MMTime end = GetCoreCallback()->GetCurrentMMTime();

  double rem = exposure_ - (end - start).getMsec();

  if (rem > 0) 
     CDeviceUtils::SleepMs((long) rem);

ERRH_END
}

int FakeCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
  capturing_ = true;
  return CCameraBase::StartSequenceAcquisition(numImages, interval_ms, stopOnOverflow);
}

int FakeCamera::StopSequenceAcquisition()
{
  capturing_ = false;
  return CCameraBase::StopSequenceAcquisition();
}

void FakeCamera::OnThreadExiting() throw()
{
  capturing_ = false;
  CCameraBase::OnThreadExiting();
}

int FakeCamera::OnPath(MM::PropertyBase * pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
     pProp->Set(path_.c_str());
  }
  else if (eAct == MM::AfterSet)
  {
     std::string oldPath = path_;
     pProp->Get(path_);
     resetCurImg();

     if (initialized_)
     {
        ERRH_START
           try
           {
              getImg();
           }
           catch (error_code ex)
           {
              pProp->Set(oldPath.c_str());
              path_ = oldPath;
              throw ex;
           }
        ERRH_END
     }
  }

  return DEVICE_OK;
}

double scaleFac(int bef, int aft)
{
  return (double)(1 << (8 * aft)) / (1 << (8 * bef));
}

int FakeCamera::OnPixelType(MM::PropertyBase * pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
  {
     switch (type_)
     {
     case CV_8UC1:
        pProp->Set(label_CV_8U);
        break;
     case CV_16UC1:
        pProp->Set(label_CV_16U);
        break;
     case CV_8UC4:
        pProp->Set(label_CV_8UC4);
        break;
     case CV_16UC4:
        pProp->Set(label_CV_16UC4);
        break;

     }
  }
  else if (eAct == MM::AfterSet)
  {
     if (capturing_)
        return DEVICE_CAMERA_BUSY_ACQUIRING;

     std::string val;
     pProp->Get(val);

     if (val == label_CV_16U)
     {
        byteCount_ = 2;
        color_ = false;
        type_ = CV_16UC1;
     }
     else if (val == label_CV_8UC4)
     {
        byteCount_ = 1;
        color_ = true;
        type_ = CV_8UC4;
     }
     else if (val == label_CV_16UC4)
     {
        byteCount_ = 2;
        color_ = true;
        type_ = CV_16UC4;
     }
     else
     {
        byteCount_ = 1;
        color_ = false;
        type_ = CV_8UC1;
     }

     emptyImg = cv::Mat::zeros(1, 1, type_);
     // emptyImg = 0;

     resetCurImg();
  }

  return DEVICE_OK;
}

std::string FakeCamera::buildPath() const throw (error_code)
{
  std::ostringstream path;

  path << std::fixed;

  int mode = 0;
  unsigned long int prec = 0;

  for (const char* it = path_.data(); it != path_.data() + path_.size(); ++it)
  {
     switch (mode)
     {
     case 0:
        if (*it == '?')
           mode = 1;
        else
           path << *it;
        break;
     case 1:
        if (*it == '{')
        {
           mode = 2;
           break;
        }
     case 3:
        if (*it == '?')
        {
           double pos;
           int ret = GetCoreCallback()->GetFocusPosition(pos);
           if (ret != 0)
              pos = 0;

           if (prec == 0)
              path << (int)pos;
           else
              path << std::setprecision(prec) << pos;

           prec = 0;
           mode = 0;
           break;
        }
        else if (*it == '[')
           mode = 4;
        else
           throw error_code(CONTROLLER_ERROR, "Invalid path specification. No stage name specified. (format: ?? for focus stage, ?[name] for any stage, and ?{prec}[name]/?{prec}? for precision other than 0)");
        break;
     case 2:
        char* end;
        prec = strtoul(it, &end, 10);
        it = end;


        if (*it == '}')
        {
           mode = 3;
        }
        else
        {
           throw error_code(CONTROLLER_ERROR, "Invalid precision specification. (format: ?? for focus stage, ?[name] for any stage, and ?{prec}[name]/?{prec}? for precision other than 0)");
        }
        break;
     case 4:
        std::ostringstream name;
        for (; *it != ']' && it != path_.data() + path_.size() - 1; name << *(it++));

        if (*it == ']')
        {
           MM::Stage* stage = (MM::Stage*)GetCoreCallback()->GetDevice(this, name.str().c_str());
           if (!stage)
              throw error_code(CONTROLLER_ERROR, "Invalid stage name '"+ name.str() + "'. (format: ?? for focus stage, ?[name] for any stage, and ?{prec}[name]/?{prec}? for precision other than 0)");

           double pos;
           int ret = stage->GetPositionUm(pos);
           if (ret != 0)
              pos = 0;

           if (prec == 0)
              path << (int)pos;
           else
              path << std::setprecision(prec) << pos;

           prec = 0;
           mode = 0;
        }
        else
           throw error_code(CONTROLLER_ERROR, "Invalid name specification. (format: ?? for focus stage, ?[name] for any stage, and ?{prec}[name]/?{prec}? for precision other than 0)");
     }
  }

  return path.str();
}

void FakeCamera::getImg() const throw (error_code)
{
  std::string path = buildPath();

  if (path == curPath_)
     return;

  cv::Mat img = path == lastFailedPath_ ? lastFailedImg_ : cv::imread(path, cv::IMREAD_ANYDEPTH | (color_ ? cv::IMREAD_COLOR : cv::IMREAD_GRAYSCALE));
     
  if (img.data == NULL)
  {
     if (curImg_.data != NULL)
     {
        LogMessage("Could not find image '" + path + "', reusing last valid image");
        curPath_ = path;
        return;
     }
     else
     {
        throw error_code(CONTROLLER_ERROR, "Could not find image '" + path + "'. Please specify a valid path mask (format: ?? for focus stage, ?[name] for any stage, and ?{prec}[name]/?{prec}? for precision other than 0)");
     }
  }

  img.convertTo(img, type_, scaleFac((int) img.elemSize() / img.channels(), byteCount_));

  bool dimChanged = (unsigned) img.cols != width_ || (unsigned) img.rows != height_;

  if (dimChanged)
  {
     if (capturing_)
     {
        lastFailedPath_ = path;
        lastFailedImg_ = img;
        throw error_code(DEVICE_CAMERA_BUSY_ACQUIRING);
     }
  }

  if (color_)
  {
     if (alphaChannel_.rows != img.rows || alphaChannel_.cols != img.cols || alphaChannel_.depth() != img.depth())
     {
        alphaChannel_ = cv::Mat(img.rows, img.cols, byteCount_ == 2 ? CV_16U : CV_8U);
        alphaChannel_ = 1 << (8 * byteCount_);
     }

     if (dimChanged)
        curImg_ = cv::Mat(img.rows, img.cols, type_);

     int fromTo[] = { 0,0 , 1,1 , 2,2 , 3,3 };
     cv::Mat from[] = { img, alphaChannel_ };

     cv::mixChannels(from, 2, &curImg_, 1, fromTo, 4);
  }
  else
     curImg_ = img;

  curPath_ = path;

  if (dimChanged)
  { 
     initSize_ = false;
     initSize(false);
  }

  updateROI();
}

void FakeCamera::updateROI() const
{
  roi_ = curImg_(cv::Range(roiY_, roiY_ + roiHeight_), cv::Range(roiX_, roiX_ + roiWidth_));

  if (!roi_.isContinuous())
     roi_ = roi_.clone();
}

void FakeCamera::initSize(bool loadImg) const
{
  if (initSize_)
     return;

  initSize_ = true;

  try
  {
     if (loadImg)
        getImg();

     roiWidth_ = width_ = curImg_.cols;
     roiHeight_ = height_ = curImg_.rows;
  }
  catch (error_code)
  {
     roiWidth_ = width_ = 1;
     roiHeight_ = height_ = 1;

     initSize_ = false;
  }
}

void FakeCamera::resetCurImg()
{
  initSize_ = false;
  curPath_ = "";
  curImg_ = cv::Mat(0, 0, type_, NULL, 0);
  roiWidth_ = width_ = 0;
  roiHeight_ = height_ = 0;  
}
