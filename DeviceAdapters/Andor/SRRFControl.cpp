#include "SRRFControl.h"
#include <boost/filesystem/operations.hpp>
#include "boost/date_time/posix_time/posix_time.hpp"

#ifdef WIN32
#include <windows.h>
#pragma warning(push)
#pragma warning(disable : 4091)
#include <shlobj.h>
#pragma warning(pop)
#include "atmcd32d.h"
#else
#include "atmcdLXd.h"
#endif

#include "Andor.h"

using namespace boost::filesystem;
using namespace boost::posix_time;
using namespace std;

const char* SRRFControl::String_SRRF_Library_Name_ = "SRRF_Stream.dll";
const char* SRRFControl::String_SRRF_Library_Version_ = "SRRF | Version Information";
const char* SRRFControl::String_NumberFramesPerTimePoint_ = "SRRF | Number of Frames per Time point";
const char* SRRFControl::String_RadialityMagnification_ = "SRRF | Radiality Magnification";
const char* SRRFControl::String_RingRadius_ = "SRRF | Ring Radius";
const char* SRRFControl::String_TemporalAnalysisType_ = "SRRF | Radiality Temporal Analysis";
const char* SRRFControl::String_TemporalAnalysisType_Mean_ = "Mean";
const char* SRRFControl::String_TemporalAnalysisType_MIP_ = "MIP";
const char* SRRFControl::String_InterpolationType_ = "SRRF | Interpolation Type";
const char* SRRFControl::String_InterpolationType_CatmullRom_ = "Catmull-Rom";
const char* SRRFControl::String_InterpolationType_FastBspline_ = "Fast B-spline";
const char* SRRFControl::String_EnableSRRF_ = "SRRF | Enable";
const char* SRRFControl::String_SRRFDisabled_ = "Disabled";
const char* SRRFControl::String_SRRFEnabled_ = "Enabled";
const char* SRRFControl::String_OriginalDataSaveOption_ = "SRRF | Save Original Data | Option";
const char* SRRFControl::String_OriginalDataNone_ = "None";
const char* SRRFControl::String_OriginalDataAll_ = "All";
const char* SRRFControl::String_OriginalDataAveraged_ = "Averaged";
const char* SRRFControl::String_OriginalDataPath_ = "SRRF | Save Original Data | Path";

// Not required so far.
//const char* SRRFControl::String_RawDataFormatType_ = "SRRF | Save Original Data | Type";
//const char* SRRFControl::String_RawDataTypeRaw_ = "Raw (*.raw)";
//const char* SRRFControl::String_RawDataTypeTif_ = "Tiff (*.tif)";

static const int MIN_RADIALITY_MAGNIFICATION = 1;
static const int MAX_RADIALITY_MAGNIFICATION = 10;
static const float MIN_RING_RADIUS = 0.1000f;
static const float MAX_RING_RADIUS = 3.0f;
static const int DEFAULT_FRAME_BURST = 100;

SRRFControl::SRRFControl(AndorCamera * camera)
   : camera_(camera),
      SRRFLibrary_(nullptr),
      libraryStatus_(NOT_INITIALISED),
      szMessage_(new char[AT_SRRF_ERROR_STRING_SIZE]),
      SRRFEnabled_(false),
      SRRFRadialityCache_(AT_SRRF_DEFAULT_RADIALITY_MAGNIFICATION),
      frameBurst_(1),
      frameIndex_(0),
      strFilename_(""),
      saveOriginalDataValue_(None),
      AT_SRRF_InitialiseProcessorDimensions_(nullptr),
      AT_SRRF_SetRadialityMagnification_(nullptr),
      AT_SRRF_SetRingRadius_(nullptr),
      AT_SRRF_SetTemporalAnalysisType_(nullptr),
      AT_SRRF_SetInterpolationType_(nullptr),
      AT_SRRF_RunOnSingleFrameFromCpu_(nullptr),
      AT_SRRF_GetResult_(nullptr),
      AT_SRRF_CheckCudaCompatibility_(nullptr),
      AT_SRRF_ValidateLicense_(nullptr),
      AT_SRRF_FinaliseAll_(nullptr),
      AT_SRRF_GetLastErrorString_(nullptr),
      AT_SRRF_GetLibraryVersion_(nullptr),
      AT_SRRF_GetMeanInputResult_(nullptr)
{
#ifndef __linux__
   SRRFLibrary_ = LoadLibrary(String_SRRF_Library_Name_);
#endif

   if (SRRFLibrary_ == nullptr) {
      libraryStatus_ = LIBRARY_FILE_NOT_FOUND;
   }
   else {
      bool bContinue = LoadFunctions();
      
      if (bContinue) {
         bContinue = CanSRRFBeActivated();
      }

      if (bContinue) {
         AddSRRFPropertiesToDevice();
      }

      if (libraryStatus_ == READY)
         camera_->AddProperty("SRRF Status", "Ready", MM::String, true, nullptr);
      else {
         camera_->AddProperty("SRRF Status", GetLastErrorString(), MM::String, true, nullptr);
      }
   }
}

SRRFControl::~SRRFControl()
{
   if (AT_SRRF_FinaliseAll_ != nullptr)
   {
      AT_SRRF_FinaliseAll_();
   }

   delete[] szMessage_;
}

char* SRRFControl::GetLastErrorString() const
{
   if (libraryStatus_ == READY) {
      return AT_SRRF_GetLastErrorString_();
   }

   switch (libraryStatus_) {
   case NOT_INITIALISED:
      strcpy(szMessage_, "SRRF class not initialised. ");
      break;
   case LIBRARY_FILE_NOT_FOUND:
      strcpy(szMessage_, "SRRF library file not found. ");
      break;
   case FUNCTION_MISSING_IN_LIBRARY:
      strcpy(szMessage_, "SRRF library file format invalid. At least one function couldn't be found. ");
      break;
   case NOT_CUDA_COMPATIBLE:
      strcpy(szMessage_, "Computer not CUDA compatible. ");
      strcat(szMessage_, AT_SRRF_GetLastErrorString_());
      break;
   case LICENSE_INVALID:
      strcpy(szMessage_, "SRRF license invalid. ");
      strcat(szMessage_, AT_SRRF_GetLastErrorString_());
      break;
   default:
      strcpy(szMessage_, "SRRF error undefined. ");
      strcat(szMessage_, AT_SRRF_GetLastErrorString_());
      break;
   }

   return szMessage_;
}

int SRRFControl::ApplySRRFParameters(ImgBuffer* cameraImageBuffer, bool liveMode)
{
   CacheFrameBurst();
   int returnCode = ApplySRRFProperties((AT_SRRF_U16)cameraImageBuffer->Width(), (AT_SRRF_U16)cameraImageBuffer->Height());
   if (returnCode != AT_SRRF_SUCCESS)
   {
      return returnCode;
   }

   if (liveMode)
   {
      // no saving streams.
      strFilename_.clear();
   }
   else
   {
      GenerateOriginalDataImageFileNameUsingDimensions((AT_SRRF_U16)cameraImageBuffer->Width(), (AT_SRRF_U16)cameraImageBuffer->Height());
   }

   return returnCode;
}

bool SRRFControl::ProcessSingleFrameOnCPU(void * imagePtr, AT_SRRF_U64 imageSize)
{
   InitialiseOriginalDataStreamIfEnabled();

   if (AT_SRRF_RunOnSingleFrameFromCpu_ != nullptr) {
      int returnCode = AT_SRRF_RunOnSingleFrameFromCpu_(camera_->GetMyCameraID(), imagePtr, imageSize, frameIndex_++);
      if (returnCode != AT_SRRF_SUCCESS) {
         camera_->Log("[ProcessSingleFrameOnCPU] Error: " + string(AT_SRRF_GetLastErrorString_()));
      }
   }

   WriteOriginalDataToStream(imagePtr, imageSize);

   if (frameBurst_ == frameIndex_)
   {
      frameIndex_ = 0;
      CloseOriginalDataStream();

      WriteAverageDataInputIfRequested(imageSize);
      return true;
   }

   return false;
}

AT_SRRF_U16 SRRFControl::GetRadiality()
{
   long longValue = 0;
   camera_->GetProperty(String_RadialityMagnification_, longValue);
   return (AT_SRRF_U16)longValue;
}

void SRRFControl::GetSRRFResult(void * outputBuffer, AT_SRRF_U64 bufferSize)
{
   if (AT_SRRF_GetResult_ != nullptr) {
      int returnCode = AT_SRRF_GetResult_(camera_->GetMyCameraID(), outputBuffer, bufferSize);
      if (returnCode != AT_SRRF_SUCCESS) {
         camera_->Log(AT_SRRF_GetLastErrorString_());
      }
   }
}


// Private

// Fired when SRRF is enabled / disabled
int SRRFControl::OnSRRFEnabledChange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      if (camera_->IsCapturing())
      {
         return DEVICE_CAMERA_BUSY_ACQUIRING;
      }

      string value;
      pProp->Get(value);
      if (strcmp(value.c_str(), String_SRRFEnabled_) == 0)
      {
         SRRFEnabled_ = true;
      }
      else
      {
         SRRFEnabled_ = false;
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(SRRFEnabled_ ? String_SRRFEnabled_ : String_SRRFDisabled_);
   }

   return DEVICE_OK;
}

// Fired when SRRF Radiality has been udpated / modified
int SRRFControl::OnSRRFRadialityChange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      if (SRRFEnabled_ && camera_->IsCapturing())
      {
         return DEVICE_CAMERA_BUSY_ACQUIRING;
      }

      long value = 1;
      if (pProp->Get(value))
      {
         camera_->ResizeSRRFImage(value);
         SRRFRadialityCache_ = (AT_SRRF_U16)value;
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)SRRFRadialityCache_);
   }

   return DEVICE_OK;
}

// Fired when SRRF Save Raw data has been udpated / modified
int SRRFControl::OnRawDataSaveOptionChange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string value;
      pProp->Get(value);
      if (strcmp(value.c_str(), String_OriginalDataAll_) == 0)
      {
         saveOriginalDataValue_ = All_data;
      }
      else if (strcmp(value.c_str(), String_OriginalDataAveraged_) == 0)
      {
         saveOriginalDataValue_ = Avg_data;
      }
      else
      {
         saveOriginalDataValue_ = None;
      }
   }

   return DEVICE_OK;
}

// Triggered when any other property has been udpated / modified - it checks 
//  if saving raw data and sets read only appropriately
int SRRFControl::OnRawDataPathChange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      //Removed read-only logic from here - ensures that correct preset values are loaded
   }
   else if (eAct == MM::AfterSet)
   {
      string pathValue;
      pProp->Get(pathValue);
      if (!exists(pathValue))
      {
         return DEVICE_INVALID_PROPERTY_VALUE;
      }
   }

   return DEVICE_OK;
}

int SRRFControl::OnSaveDataTypeChange(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      SetPropertyReadOnly(pProp, saveOriginalDataValue_ == None);
   }

   return DEVICE_OK;
}

void SRRFControl::SetPropertyReadOnly(MM::PropertyBase* pProp, bool readOnly)
{
   MM::Property* pChildProperty = (MM::Property*)pProp;
   pChildProperty->SetReadOnly(readOnly);
}

bool SRRFControl::LoadFunctions()
{
   bool bRet = true;
   bRet &= this->InitialiseDLLFunction<AT_SRRF_InitialiseProcessorDimensionsFn>(AT_SRRF_InitialiseProcessorDimensions_, "AT_SRRF_InitialiseProcessorDimensions");
   bRet &= this->InitialiseDLLFunction<AT_SRRF_SetRadialityMagnificationFn>(AT_SRRF_SetRadialityMagnification_, "AT_SRRF_SetRadialityMagnification");
   bRet &= this->InitialiseDLLFunction<AT_SRRF_SetRingRadiusFn>(AT_SRRF_SetRingRadius_, "AT_SRRF_SetRingRadius");
   bRet &= this->InitialiseDLLFunction<AT_SRRF_SetTemporalAnalysisTypeFn>(AT_SRRF_SetTemporalAnalysisType_, "AT_SRRF_SetTemporalAnalysisType");
   bRet &= this->InitialiseDLLFunction<AT_SRRF_SetInterpolationTypeFn>(AT_SRRF_SetInterpolationType_, "AT_SRRF_SetInterpolationType");
   bRet &= this->InitialiseDLLFunction<AT_SRRF_RunOnSingleFrameFromCpuFn>(AT_SRRF_RunOnSingleFrameFromCpu_, "AT_SRRF_RunOnSingleFrameFromCpu");
   bRet &= this->InitialiseDLLFunction<AT_SRRF_GetResultFn>(AT_SRRF_GetResult_, "AT_SRRF_GetResult");
   bRet &= this->InitialiseDLLFunction<AT_SRRF_CheckCudaCompatibilityFn>(AT_SRRF_CheckCudaCompatibility_, "AT_SRRF_CheckCudaCompatibility");
   bRet &= this->InitialiseDLLFunction<AT_SRRF_ValidateLicenseFn>(AT_SRRF_ValidateLicense_, "AT_SRRF_ValidateLicense");
   bRet &= this->InitialiseDLLFunction<AT_SRRF_FinaliseAllFn>(AT_SRRF_FinaliseAll_, "AT_SRRF_FinaliseAll");
   bRet &= this->InitialiseDLLFunction<AT_SRRF_GetLastErrorStringFn>(AT_SRRF_GetLastErrorString_, "AT_SRRF_GetLastErrorString");
   bRet &= this->InitialiseDLLFunction<AT_SRRF_GetLibraryVersionFn>(AT_SRRF_GetLibraryVersion_, "AT_SRRF_GetLibraryVersion");
   bRet &= this->InitialiseDLLFunction<AT_SRRF_GetResultFn>(AT_SRRF_GetMeanInputResult_, "AT_SRRF_GetMeanInput");

   return bRet;
}

bool SRRFControl::CanSRRFBeActivated()
{
   // Check license for SRRF Library
   int licenceValidRetCode = AT_SRRF_ValidateLicense_(camera_->GetMyCameraID());
   if (licenceValidRetCode != AT_SRRF_SUCCESS) {
      libraryStatus_ = LICENSE_INVALID;
      camera_->Log(AT_SRRF_GetLastErrorString_());
      return false;
   }

   // Check support of CUDA
  if (AT_SRRF_CheckCudaCompatibility_() != AT_SRRF_SUCCESS) {
    libraryStatus_ = NOT_CUDA_COMPATIBLE;
    return false;
  }
  
   return true;
}

void SRRFControl::AddSRRFPropertiesToDevice()
{
   camera_->AddProperty(String_SRRF_Library_Version_, AT_SRRF_GetLibraryVersion_(), MM::String, true, nullptr);
   camera_->AddProperty(String_NumberFramesPerTimePoint_, CDeviceUtils::ConvertToString(DEFAULT_FRAME_BURST), MM::Integer, false, nullptr);
   CPropertyAction *pActRadiality = new CPropertyAction(this, &SRRFControl::OnSRRFRadialityChange);
   camera_->AddProperty(String_RadialityMagnification_, CDeviceUtils::ConvertToString(AT_SRRF_DEFAULT_RADIALITY_MAGNIFICATION), MM::Integer, false, pActRadiality);
   camera_->AddProperty(String_RingRadius_, CDeviceUtils::ConvertToString(AT_SRRF_DEFAULT_RING_RADIUS), MM::Float, false, nullptr);

   camera_->SetPropertyLimits(String_RadialityMagnification_, MIN_RADIALITY_MAGNIFICATION, MAX_RADIALITY_MAGNIFICATION);
   camera_->SetPropertyLimits(String_RingRadius_, MIN_RING_RADIUS, MAX_RING_RADIUS);

   camera_->AddProperty(String_TemporalAnalysisType_, String_TemporalAnalysisType_Mean_, MM::String, false, nullptr);
   vector<string> TemporalAnalysisTypeValues;
   TemporalAnalysisTypeValues.push_back(String_TemporalAnalysisType_Mean_);
   TemporalAnalysisTypeValues.push_back(String_TemporalAnalysisType_MIP_);
   camera_->SetAllowedValues(String_TemporalAnalysisType_, TemporalAnalysisTypeValues);

   camera_->AddProperty(String_InterpolationType_, String_InterpolationType_CatmullRom_, MM::String, false, nullptr);
   vector<string> InterpolationTypeValues;
   InterpolationTypeValues.push_back(String_InterpolationType_CatmullRom_);
   InterpolationTypeValues.push_back(String_InterpolationType_FastBspline_);
   camera_->SetAllowedValues(String_InterpolationType_, InterpolationTypeValues);
   CPropertyAction *pActEnabled = new CPropertyAction(this, &SRRFControl::OnSRRFEnabledChange);
   camera_->CreateProperty(String_EnableSRRF_, "", MM::String, false, pActEnabled);
   vector<string> SRRFEnumValues;
   SRRFEnumValues.push_back(String_SRRFDisabled_);
   SRRFEnumValues.push_back(String_SRRFEnabled_);
   camera_->SetAllowedValues(String_EnableSRRF_, SRRFEnumValues);
   camera_->SetProperty(String_EnableSRRF_, String_SRRFDisabled_);

   CPropertyAction *pActRawDataPath = new CPropertyAction(this, &SRRFControl::OnRawDataPathChange);
   camera_->AddProperty(String_OriginalDataPath_, GetDefaultDirectoryPath().c_str(), MM::String, false, pActRawDataPath);

   CPropertyAction *pActSaveRawData = new CPropertyAction(this, &SRRFControl::OnRawDataSaveOptionChange);
   camera_->CreateProperty(String_OriginalDataSaveOption_, "", MM::String, false, pActSaveRawData);
   SRRFEnumValues.clear();
   SRRFEnumValues.push_back(String_OriginalDataNone_);
   SRRFEnumValues.push_back(String_OriginalDataAll_);
   SRRFEnumValues.push_back(String_OriginalDataAveraged_);
   camera_->SetAllowedValues(String_OriginalDataSaveOption_, SRRFEnumValues);
   camera_->SetProperty(String_OriginalDataSaveOption_, String_OriginalDataNone_);

   libraryStatus_ = READY;
}

int SRRFControl::ApplySRRFProperties(AT_SRRF_U16 width, AT_SRRF_U16 height)
{
   char stringValue[MM::MaxStrLength];
   double doubleValue = 0.0;
   AT_SRRF_H handle = camera_->GetMyCameraID();

   // Init processor dimensions
   // Only 16-bit pixel depth used on this device adapter, hard coded last 2 params.
   int returnCode = AT_SRRF_InitialiseProcessorDimensions_(
      handle,
      width,
      height,
      frameBurst_,
      AT_SRRF_INPUT_UINT16,
      AT_SRRF_OUTPUT_UINT16);

   if (returnCode != AT_SRRF_SUCCESS) {
      stringstream ss;
      ss << "[ApplySRRFProperties] AT_SRRF_InitialiseProcessorDimensions_(handle[" << handle
         << "] width[" << width
         << "] height[" << height
         << "] framesPerTimePoint[" << frameBurst_
         << "] inputDataType[AT_SRRF_INPUT_UINT16] outputDataType[AT_SRRF_OUTPUT_UINT16])" << endl
         << "Error code[" << returnCode << "] Error string: " << AT_SRRF_GetLastErrorString_();
      camera_->Log(ss.str());
      return returnCode;
   }

   // Set Radiality Magnification
   if (AT_SRRF_SetRadialityMagnification_ != nullptr)
   {
      returnCode = AT_SRRF_SetRadialityMagnification_(handle, SRRFRadialityCache_);
      if (returnCode != AT_SRRF_SUCCESS) {
         stringstream ss;
         ss << "[ApplySRRFProperties] AT_SRRF_SetRadialityMagnification_(handle[" << handle
            << "] radialityMagnification[" << SRRFRadialityCache_ << "])" << endl
            << "Error code[" << returnCode << "] Error string: " << AT_SRRF_GetLastErrorString_();
         camera_->Log(ss.str());
         return returnCode;
      }
   }

   // Set Ring Radius
   camera_->GetProperty(String_RingRadius_, doubleValue);
   if (AT_SRRF_SetRingRadius_ != nullptr)
   {
      returnCode = AT_SRRF_SetRingRadius_(handle, (float)doubleValue);
      if (returnCode != AT_SRRF_SUCCESS) {
         stringstream ss;
         ss << "[ApplySRRFProperties] AT_SRRF_SetRingRadius_(handle[" << handle
            << "] ringRadius[" << doubleValue << "])" << endl
            << "Error code[" << returnCode << "] Error string: " << AT_SRRF_GetLastErrorString_();
         camera_->Log(ss.str());
         return returnCode;
      }
   }

   // Set Temporal Analysis Type
   AT_SRRF_TemporalAnalysisType analysisType = AT_SRRF_MEAN;
   camera_->GetProperty(String_TemporalAnalysisType_, stringValue);
   if (strcmp(stringValue, String_TemporalAnalysisType_MIP_) == 0)
   {
      analysisType = AT_SRRF_MIP;
   }
   if (AT_SRRF_SetTemporalAnalysisType_ != nullptr)
   {
      returnCode = AT_SRRF_SetTemporalAnalysisType_(handle, analysisType);
      if (returnCode != AT_SRRF_SUCCESS) {
         stringstream ss;
         ss << "[ApplySRRFProperties] AT_SRRF_SetTemporalAnalysisType_(handle[" << handle
            << "] temporalAnalysisType[" << stringValue << "])" << endl
            << "Error code[" << returnCode << "] Error string: " << AT_SRRF_GetLastErrorString_();
         camera_->Log(ss.str());
         return returnCode;
      }
   }

   // Set Interpolation Type
   AT_SRRF_InterpolationType interpolationType = AT_SRRF_CATMULL_ROM;
   camera_->GetProperty(String_InterpolationType_, stringValue);
   if (strcmp(stringValue, String_InterpolationType_FastBspline_) == 0)
   {
      interpolationType = AT_SRRF_FAST_BSPLINE;
   }
   if (AT_SRRF_SetInterpolationType_ != nullptr)
   {
      returnCode = AT_SRRF_SetInterpolationType_(handle, interpolationType);
      if (returnCode != AT_SRRF_SUCCESS) {
         stringstream ss;
         ss << "[ApplySRRFProperties] AT_SRRF_SetInterpolationType_(handle[" << handle
            << "] interpolationType[" << stringValue << "])" << endl
            << "Error code[" << returnCode << "] Error string: " << AT_SRRF_GetLastErrorString_();
         camera_->Log(ss.str());
         return returnCode;
      }
   }

   frameIndex_ = 0;
   return returnCode;
}

void SRRFControl::GenerateOriginalDataImageFileNameUsingDimensions(AT_SRRF_U16 width, AT_SRRF_U16 height)
{
   strFilename_.assign("\\Image_");
   stringstream ss;
   ss << width << "x" << height;
   if (saveOriginalDataValue_ == All_data)
   {
      ss << "x" << frameBurst_ << ".raw";
      strFilename_.append("AllOriginalData_");
   }
   else
   {
      ss << "x1.raw";
      strFilename_.append("AvgOriginalData_");
   }

   strFilename_.append(ss.str());
}

void SRRFControl::CacheFrameBurst()
{
   long longValue = 0;
   camera_->GetProperty(String_NumberFramesPerTimePoint_, longValue);
   frameBurst_ = (AT_SRRF_U16)longValue;
}

void SRRFControl::InitialiseOriginalDataStreamIfEnabled()
{
   if (saveOriginalDataValue_ == All_data && strFilename_.length() > 0)
   {
      if (frameIndex_ == 0)
      {
         if (!originalDataFileStream_.is_open())
         {
            string fullPathName = GetFullPathNameForOriginalData();
            originalDataFileStream_.open(fullPathName.c_str(), ofstream::binary | ofstream::app);
         }
      }
   }
}

bool SRRFControl::WriteOriginalDataToStream(void * imagePtr, AT_SRRF_U64 imageSize)
{
   if (originalDataFileStream_.is_open())
   {
      originalDataFileStream_.write((const char*)imagePtr, imageSize);
      originalDataFileStream_.flush();
   }

   return true;
}

bool SRRFControl::WriteAverageDataInputIfRequested(AT_SRRF_U64 imageSize)
{
   if (saveOriginalDataValue_ == Avg_data && strFilename_.length() > 0)
   {
      string fullPathName = GetFullPathNameForOriginalData();
      originalDataFileStream_.open(fullPathName.c_str(), ofstream::binary);
      void * outputBuffer = new unsigned char[imageSize];
      int returnCode = AT_SRRF_GetMeanInputResult_(camera_->GetMyCameraID(), outputBuffer, imageSize);
      if (returnCode != AT_SRRF_SUCCESS) {
         stringstream ss;
         ss << "[WriteAverageDataInputIfRequested] AT_SRRF_GetMeanInputResult_(handle[" << camera_->GetMyCameraID()
            << "] outputBuffer[" << outputBuffer
            << "] imageSize[" << imageSize << "])" << endl
            << "Error code[" << returnCode << "] Error string: " << AT_SRRF_GetLastErrorString_();
         camera_->Log(ss.str());
      }

      WriteOriginalDataToStream(outputBuffer, imageSize);
      CloseOriginalDataStream();
      delete[] (unsigned char*)outputBuffer;
   }

   return true;
}

void SRRFControl::CloseOriginalDataStream()
{
   originalDataFileStream_.close();
}

string SRRFControl::GetFullPathNameForOriginalData()
{
   char pathValue[MM::MaxStrLength];
   camera_->GetProperty(String_OriginalDataPath_, pathValue);
   string s_uniqueFolderName = GenerateUniqueFolderWithDateTimestampMicroseconds();
   path fullPath(pathValue);
   fullPath /= s_uniqueFolderName;
   bool b_succeeded = create_directories(fullPath);
   if (b_succeeded)
   {
      fullPath += strFilename_;
   }

   return fullPath.string();
}

string SRRFControl::GenerateUniqueFolderWithDateTimestampMicroseconds()
{
   ptime time_now = microsec_clock::local_time();
   string s_dateTime_us = to_simple_string(time_now);

   s_dateTime_us = ReplaceAllCharsInString(s_dateTime_us, ' ', "_");
   s_dateTime_us = ReplaceAllCharsInString(s_dateTime_us, ':', ".");

   return s_dateTime_us;
}

string SRRFControl::ReplaceAllCharsInString(string src, char ch, string s_newChar)
{
   while (string::npos != src.find(ch)) {
      src.replace(src.find(ch), 1, s_newChar);
   }

   return src;
}

string SRRFControl::GetDefaultDirectoryPath() {
#ifdef __linux__
   //Not supported, return what would be /home for compiler.
   return string(getenv("HOME"));
#else
   char buffer[MAX_PATH];
   if (SUCCEEDED(SHGetFolderPath(NULL, CSIDL_PERSONAL, NULL, SHGFP_TYPE_CURRENT, buffer)))
   {
      return string(buffer);
   }

   return string(getenv("SystemDrive"));
#endif
}
