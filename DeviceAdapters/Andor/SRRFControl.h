#ifndef _SRRF_H_
#define _SRRF_H_

#include "SRRF_Stream.h"
#include "../../MMDevice/Property.h"
#include <fstream>

class AndorCamera;
class ImgBuffer;

// SRRF Function typedefs
typedef int(*AT_SRRF_InitialiseProcessorDimensionsFn)(AT_SRRF_H, AT_SRRF_U16, AT_SRRF_U16, AT_SRRF_U16, AT_SRRF_InputDataType, AT_SRRF_OutputDataType);
typedef int(*AT_SRRF_SetRadialityMagnificationFn)(AT_SRRF_H, AT_SRRF_U16);
typedef int(*AT_SRRF_SetRingRadiusFn)(AT_SRRF_H, float);
typedef int(*AT_SRRF_SetTemporalAnalysisTypeFn)(AT_SRRF_H, AT_SRRF_TemporalAnalysisType);
typedef int(*AT_SRRF_SetInterpolationTypeFn)(AT_SRRF_H, AT_SRRF_InterpolationType);
typedef int(*AT_SRRF_RunOnSingleFrameFromCpuFn)(AT_SRRF_H, void *, AT_SRRF_U64, AT_SRRF_U16);
typedef int(*AT_SRRF_GetResultFn)(AT_SRRF_H, void *, AT_SRRF_U64);
typedef int(*AT_SRRF_CheckCudaCompatibilityFn)();
typedef int(*AT_SRRF_ValidateLicenseFn)(AT_SRRF_H);
typedef void(*AT_SRRF_FinaliseAllFn)();
typedef char*(*AT_SRRF_GetLastErrorStringFn)();
typedef char*(*AT_SRRF_GetLibraryVersionFn)();

#ifdef __linux__

const                        // this is a const object...
class {
public:
  template<class T>          // convertible to any type
    operator T*() const      // of null non-member
    { return 0; }            // pointer...
  template<class C, class T> // or any type of null
    operator T C::*() const  // member pointer...
    { return 0; }
private:
  void operator&() const;    // whose address can't be taken
} nullptr = {}; 

#endif

class SRRFControl
{
public:
   enum LIBRARYSTATUS {
      NOT_INITIALISED,
      LIBRARY_FILE_NOT_FOUND,
      FUNCTION_MISSING_IN_LIBRARY,
      NOT_CUDA_COMPATIBLE,
      LICENSE_INVALID,
      READY
   };

   SRRFControl(AndorCamera* camera);
   ~SRRFControl();

   int ApplySRRFParameters(ImgBuffer* cameraImageBuffer, bool liveMode);

   LIBRARYSTATUS GetLibraryStatus() const { return libraryStatus_; }

   char* GetLastErrorString() const;
   bool GetSRRFEnabled() const { return SRRFEnabled_; }
   AT_SRRF_U16 GetFrameBurst() const { return frameBurst_; }
   AT_SRRF_U16 GetRadiality();
   bool ProcessSingleFrameOnCPU(void * imagePtr, AT_SRRF_U64 imageSize);
   void GetSRRFResult(void * outputBuffer, AT_SRRF_U64 bufferSize);

private:
#ifdef __linux__
   typedef void * DLLHANDLE;
#else
   typedef HINSTANCE DLLHANDLE;
#endif

   typedef MM::Action<SRRFControl> CPropertyAction;

   typedef enum
   {
      All_data,
      Avg_data,
      None
   } TSaveOriginalDataOption;

   // Device property browser strings
   static const char* String_SRRF_Library_Name_;
   static const char* String_SRRF_Library_Version_;
   static const char* String_NumberFramesPerTimePoint_;
   static const char* String_RadialityMagnification_;
   static const char* String_RingRadius_;
   static const char* String_TemporalAnalysisType_;
   static const char* String_TemporalAnalysisType_MIP_;
   static const char* String_TemporalAnalysisType_Mean_;
   static const char* String_InterpolationType_;
   static const char* String_InterpolationType_CatmullRom_;
   static const char* String_InterpolationType_FastBspline_;
   static const char* String_EnableSRRF_;
   static const char* String_SRRFDisabled_;
   static const char* String_SRRFEnabled_;
   static const char* String_OriginalDataPath_;
   static const char* String_OriginalDataSaveOption_;
   static const char* String_OriginalDataNone_;
   static const char* String_OriginalDataAll_;
   static const char* String_OriginalDataAveraged_;

   // Not required so far.
   //static const char* String_RawDataFormatType_;
   //static const char* String_RawDataTypeRaw_;
   //static const char* String_RawDataTypeTif_;
  
   // Library functions
   AT_SRRF_InitialiseProcessorDimensionsFn AT_SRRF_InitialiseProcessorDimensions_;
   AT_SRRF_SetRadialityMagnificationFn AT_SRRF_SetRadialityMagnification_;
   AT_SRRF_SetRingRadiusFn AT_SRRF_SetRingRadius_;
   AT_SRRF_SetTemporalAnalysisTypeFn AT_SRRF_SetTemporalAnalysisType_;
   AT_SRRF_SetInterpolationTypeFn AT_SRRF_SetInterpolationType_;
   AT_SRRF_RunOnSingleFrameFromCpuFn AT_SRRF_RunOnSingleFrameFromCpu_;
   AT_SRRF_GetResultFn AT_SRRF_GetResult_;
   AT_SRRF_CheckCudaCompatibilityFn AT_SRRF_CheckCudaCompatibility_;
   AT_SRRF_ValidateLicenseFn AT_SRRF_ValidateLicense_;
   AT_SRRF_FinaliseAllFn AT_SRRF_FinaliseAll_;
   AT_SRRF_GetLastErrorStringFn AT_SRRF_GetLastErrorString_;
   AT_SRRF_GetLibraryVersionFn AT_SRRF_GetLibraryVersion_;
   AT_SRRF_GetResultFn AT_SRRF_GetMeanInputResult_;

   int OnSRRFEnabledChange(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSRRFRadialityChange(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRawDataSaveOptionChange(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnRawDataPathChange(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnSaveDataTypeChange(MM::PropertyBase* pProp, MM::ActionType eAct);
   void SetPropertyReadOnly(MM::PropertyBase* pProp, bool readOnly);

   AndorCamera* camera_;
   DLLHANDLE SRRFLibrary_;
   LIBRARYSTATUS libraryStatus_;
   char* szMessage_;
   bool SRRFEnabled_;
   AT_SRRF_U16 SRRFRadialityCache_;
   AT_SRRF_U16 frameBurst_;
   AT_SRRF_U16 frameIndex_;
   std::string strFilename_;
   std::ofstream originalDataFileStream_;
   TSaveOriginalDataOption saveOriginalDataValue_;

   template <typename T>
   bool InitialiseDLLFunction(T& pFunction, const char* szFunctionName);
   bool LoadFunctions();
   bool CanSRRFBeActivated();
   void AddSRRFPropertiesToDevice();
   int  ApplySRRFProperties(AT_SRRF_U16 width, AT_SRRF_U16 height);
   void GenerateOriginalDataImageFileNameUsingDimensions(AT_SRRF_U16 width, AT_SRRF_U16 height);
   void CacheFrameBurst();
   void InitialiseOriginalDataStreamIfEnabled();
   bool WriteOriginalDataToStream(void * imagePtr, AT_SRRF_U64 imageSize);
   bool WriteAverageDataInputIfRequested(AT_SRRF_U64 imageSize);
   void CloseOriginalDataStream();
   std::string GetFullPathNameForOriginalData();
   std::string GenerateUniqueFolderWithDateTimestampMicroseconds();
   std::string ReplaceAllCharsInString(std::string src, char ch, std::string s_newChar);
   std::string GetDefaultDirectoryPath();
};


template <typename T>
bool SRRFControl::InitialiseDLLFunction(T& pFunction, const char* szFunctionName)
{
#ifdef __linux__
   return false;
#else
   bool bRet = true;
   pFunction = (T)GetProcAddress(SRRFLibrary_, szFunctionName);
   if (pFunction == nullptr) {
      libraryStatus_ = FUNCTION_MISSING_IN_LIBRARY;
      bRet = false;
   }
   return bRet;
#endif
}
#endif
