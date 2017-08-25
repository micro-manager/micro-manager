
#ifndef SRRF_STREAMH
#define SRRF_STREAMH

#ifdef __linux__

#define AT_SRRF_EXP_MOD
#define AT_SRRF_EXP_CONV

#else

#if defined(__GPU_SRRF_EXP__)
#define AT_SRRF_EXP_MOD __declspec(dllexport)
#else
#define AT_SRRF_EXP_MOD __declspec(dllimport)
#endif

#include <windows.h>
#define AT_SRRF_EXP_CONV WINAPI

#endif


#define AT_SRRF_SUCCESS                              0
#define AT_SRRF_ERR_FAIL                             1
#define AT_SRRF_ERR_INITIALISATION                   2
#define AT_SRRF_ERR_NOT_INITIALISED                  3
#define AT_SRRF_ERR_INVALID_INPUT_PARAMETER          4
#define AT_SRRF_ERR_INVALID_PARAMETER_UPDATE         5
#define AT_SRRF_ERR_INVALID_BUFFER_SIZE              6
#define AT_SRRF_ERR_CUDA_INITIALISATION              7
#define AT_SRRF_ERR_CUDA_TEXTURE_SETUP               8
#define AT_SRRF_ERR_CUDA_MEM_ALLOCATION              9
#define AT_SRRF_ERR_CUDA_MEM_COPY                    10
#define AT_SRRF_ERR_CUDA_FUNCTION_CALL               11
#define AT_SRRF_ERR_NO_CUDA_CARD                     12
#define AT_SRRF_ERR_NO_COMPATIBLE_CUDA_DRIVER        13
#define AT_SRRF_ERR_COMPUTE_CAPABILITY_OF_CUDA_CARD  14
#define AT_SRRF_ERR_INVALID_LICENSE                  15

typedef long AT_SRRF_H;
typedef unsigned short int AT_SRRF_U16;
typedef unsigned long long AT_SRRF_U64;

typedef enum {
    AT_SRRF_INPUT_FLOAT  = 0,
    AT_SRRF_INPUT_UINT16 = 1,
} AT_SRRF_InputDataType;

typedef enum {
    AT_SRRF_OUTPUT_FLOAT  = 0,
    AT_SRRF_OUTPUT_UINT16 = 1,
} AT_SRRF_OutputDataType;

typedef enum {
    AT_SRRF_CATMULL_ROM  = 0,
    AT_SRRF_FAST_BSPLINE = 1,
} AT_SRRF_InterpolationType;

typedef enum {
    AT_SRRF_MEAN = 0,
    AT_SRRF_MIP  = 1,
} AT_SRRF_TemporalAnalysisType;

const int AT_SRRF_ERROR_STRING_SIZE          = 256;
const int AT_SRRF_VERSION_NUMBER_STRING_SIZE = 256;

const int AT_SRRF_DEFAULT_RADIALITY_MAGNIFICATION = 4;
const float AT_SRRF_DEFAULT_RING_RADIUS           = 0.5f;
const int AT_SRRF_DEFAULT_INTERPOLATION_TYPE      = AT_SRRF_CATMULL_ROM;
const int AT_SRRF_DEFAULT_TEMPORAL_ANALYSIS_TYPE  = AT_SRRF_MEAN;

#ifdef __cplusplus
extern "C" {
#endif

AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_InitialiseProcessorDimensions(
                                                              AT_SRRF_H handle,
                                                              AT_SRRF_U16 width, 
                                                              AT_SRRF_U16 height, 
                                                              AT_SRRF_U16 framesPerTimePoint, 
                                                              AT_SRRF_InputDataType inputDataType, 
                                                              AT_SRRF_OutputDataType outputDataType);

AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_SetRadialityMagnification(AT_SRRF_H handle, AT_SRRF_U16 radialityMagnification);

AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_SetRingRadius(AT_SRRF_H handle, float ringRadius);

AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_SetTemporalAnalysisType(AT_SRRF_H handle, AT_SRRF_TemporalAnalysisType temporalAnalysisType);

AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_SetInterpolationType(AT_SRRF_H handle, AT_SRRF_InterpolationType interpolationType);

AT_SRRF_EXP_MOD void AT_SRRF_EXP_CONV AT_SRRF_FinaliseAll();

AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_RunOnSingleFrameFromCpu(
                                                              AT_SRRF_H handle, 
                                                              void * inputFrameCpuBufferPtr, 
                                                              AT_SRRF_U64 inputBufferSize, 
                                                              AT_SRRF_U16 frameIndex);

AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_GetResult(AT_SRRF_H handle, void * outputCpuBufferPtr, AT_SRRF_U64 outputBufferSize);

AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_GetMeanInput(AT_SRRF_H handle, void * outputCpuBufferPtr, AT_SRRF_U64 outputBufferSize);

AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_ValidateLicense(AT_SRRF_H handle);

AT_SRRF_EXP_MOD char* AT_SRRF_EXP_CONV AT_SRRF_GetLastErrorString();

AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_CheckCudaCompatibility();

AT_SRRF_EXP_MOD char* AT_SRRF_EXP_CONV AT_SRRF_GetLibraryVersion();

#ifdef __cplusplus
}
#endif

#endif
