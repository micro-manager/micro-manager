//---------------------------------------------------------------------------
// Header File to be included in external code for importing of DLL functions

//---------------------------------------------------------------------------
#ifndef SRRF_STREAMH
#define SRRF_STREAMH
//---------------------------------------------------------------------------

typedef long AT_SRRF_H;
typedef unsigned short int AT_SRRF_U16;
typedef unsigned long long AT_SRRF_U64;

#if defined(WIN32) || defined(_WIN32) || defined(__WIN32__) || defined(_WIN64)
#include <windows.h>
#if defined(__GPU_SRRF_EXP__)
#define AT_SRRF_EXP_MOD __declspec(dllexport)
#else
#define AT_SRRF_EXP_MOD __declspec(dllimport)
#endif
#define AT_SRRF_EXP_CONV WINAPI
#else
#define AT_SRRF_EXP_MOD
#define AT_SRRF_EXP_CONV
#endif

//---------------------------------------------------------------------------
/**
* Error Codes
*/
//---------------------------------------------------------------------------

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

//--------------------//
//---              ---//
//--- Enumerations ---//
//---              ---//
//--------------------//

//---------------------------------------------------------------------------
/**
* Boolean Values
*/
//---------------------------------------------------------------------------
typedef enum {
	AT_SRRF_FALSE = 0,
	AT_SRRF_TRUE = 1
} AT_SRRF_BOOL;

//---------------------------------------------------------------------------
/**
* Input Data Type
*/
//---------------------------------------------------------------------------
typedef enum {
	AT_SRRF_INPUT_FLOAT  = 0,
	AT_SRRF_INPUT_UINT16 = 1,
} AT_SRRF_InputDataType;

//---------------------------------------------------------------------------
/**
* Output Data Type
*/
//---------------------------------------------------------------------------
typedef enum {
	AT_SRRF_OUTPUT_FLOAT  = 0,
	AT_SRRF_OUTPUT_UINT16 = 1,
} AT_SRRF_OutputDataType;

//---------------------------------------------------------------------------
/**
 * Interpolation Type
 */
//---------------------------------------------------------------------------
typedef enum {
	AT_SRRF_CATMULL_ROM  = 0,
	AT_SRRF_FAST_BSPLINE = 1,
} AT_SRRF_InterpolationType;

//---------------------------------------------------------------------------
/**
* Temporal Analysis Type
*/
//---------------------------------------------------------------------------
typedef enum {
	AT_SRRF_MEAN = 0,
	AT_SRRF_MIP  = 1,
} AT_SRRF_TemporalAnalysisType;

//---------------------------------------------------------------------------
/**
* Constants
*/
//---------------------------------------------------------------------------
const int AT_SRRF_ERROR_STRING_SIZE          = 256;
const int AT_SRRF_VERSION_NUMBER_STRING_SIZE = 256;

const int AT_SRRF_DEFAULT_RADIALITY_MAGNIFICATION = 4;
const float AT_SRRF_DEFAULT_RING_RADIUS           = 2.0f;
const int AT_SRRF_DEFAULT_INTERPOLATION_TYPE      = AT_SRRF_FAST_BSPLINE;
const int AT_SRRF_DEFAULT_TEMPORAL_ANALYSIS_TYPE  = AT_SRRF_MEAN;

#ifdef __cplusplus
extern "C" {
#endif

//---------------------------//
//---                     ---//
//--- Interface Functions ---//
//---                     ---//
//---------------------------//

//---------------------------------------------------------------------------
/**
 * 
 * SRRF Processing Functions 
 *
 */
//---------------------------------------------------------------------------

//-----------------------------------------------------------------------------
/**
*
* Method : AT_SRRF_InitialiseProcessorDimensions
*
* @param handle                  valid Andor SDK camera handle - used to reference a specific instance of a SRRF processor
* @param width                   the width of the input frames to be processed                     
* @param height                  the height of the input frames to be processed      
* @param framesPerTimePoint      number of frames per time point / frame burst
* @param inputDataType           the type of the input data (see 'AT_SRRF_InputDataType' enum)
* @param outputDataType          the type of the output data (see 'AT_SRRF_OutputDataType' enum)
*
* @return success flag
*
* Initialise SRRF Processor with dimensions and type of the data to be input and output.
* This must be called before any of the other API functions that require an input handle.
*
* If the input handle has not previously been used a new instance of a SRRF processor shall be created with the specified parameters.
* If the input handle has previously been used the SRRF processor it refers to shall be updated with the specified parameters.
* Only 2 SRRF processors may be initialised at any time. 
* SRRF processors may be uninitialised (finalised) using either 'AT_SRRF_FinaliseProcessor' [to finalise a specific processor] 
* or 'AT_SRRF_FinaliseAll' [to finalise all processors].
*
*/
AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_InitialiseProcessorDimensions(
                                                            AT_SRRF_H handle,
	                                                          AT_SRRF_U16 width, 
	                                                          AT_SRRF_U16 height, 
	                                                          AT_SRRF_U16 framesPerTimePoint, 
	                                                          AT_SRRF_InputDataType inputDataType, 
	                                                          AT_SRRF_OutputDataType outputDataType);

//-----------------------------------------------------------------------------
/**
*
* Method : AT_SRRF_SetRadialityMagnification
*
* @param handle                  valid Andor SDK camera handle - used to reference a specific instance of a SRRF processor
* @param radialityMagnification  the value to set for the radiality magnification
*
* @return success flag
*
* Set the Radiality Magnification for the specified SRRF processor (Valid Values: 1 to 10).
*
*/
AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_SetRadialityMagnification(AT_SRRF_H handle, AT_SRRF_U16 radialityMagnification);

//-----------------------------------------------------------------------------
/**
*
* Method : AT_SRRF_SetRingRadius
*
* @param handle      valid Andor SDK camera handle - used to reference a specific instance of a SRRF processor
* @param ringRadius  the value to set for the ring radius
*
* @return success flag
*
* Set the Ring Radius for the specified SRRF processor (Valid Values: 0.1 to 3.0).
*
*/
AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_SetRingRadius(AT_SRRF_H handle, float ringRadius);

//-----------------------------------------------------------------------------
/**
*
* Method : AT_SRRF_SetTemporalAnalysisType
*
* @param handle                valid Andor SDK camera handle - used to reference a specific instance of a SRRF processor
* @param temporalAnalysisType  the value to set for the temporal analysis type
*
* @return success flag
*
* Set the Temporal Analysis Type for the specified SRRF processor (see 'AT_SRRF_TemporalAnalysisType' enum).
*
*/
AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_SetTemporalAnalysisType(AT_SRRF_H handle, AT_SRRF_TemporalAnalysisType temporalAnalysisType);

//-----------------------------------------------------------------------------
/**
*
* Method : AT_SRRF_SetInterpolationType
*
* @param handle             valid Andor SDK camera handle - used to reference a specific instance of a SRRF processor
* @param interpolationType  the value to set for the interpolation type
*
* @return success flag
*
* Set the Interpolation Type for the specified SRRF processor (see 'AT_SRRF_InterpolationType' enum).
*
*/
AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_SetInterpolationType(AT_SRRF_H handle, AT_SRRF_InterpolationType interpolationType);

//-----------------------------------------------------------------------------
/**
*
* Method : AT_SRRF_SetApplyFixedPatternNoiseCorrectionOption
*
* @param handle                            valid Andor SDK camera handle - used to reference a specific instance of a SRRF processor
* @param applyFixedPatternNoise            value to indicate whether or not fixed pattern noise correction should be applied
*
* @return success flag
*
*/
AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_SetApplyFixedPatternNoiseCorrectionOption(AT_SRRF_H handle, AT_SRRF_BOOL applyFixedPatternNoiseCorrection);

//-----------------------------------------------------------------------------
/**
*
* Method : AT_SRRF_SetFixedPatternNoiseCorrectionImage
*
* @param handle                                     valid Andor SDK camera handle - used to reference a specific instance of a SRRF processor
* @param fixedPatternNoiseCorrectionImageBufferPtr  pointer to buffer containing the averaged dark image to set for fixed pattern noise correction
* @param inputBufferSize                            the size of the input buffer pointed to by the second parameter (in bytes)
*
* @return success flag
*
*/
AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_SetFixedPatternNoiseCorrectionImage(AT_SRRF_H handle, float * fixedPatternNoiseCorrectionImageBufferPtr, AT_SRRF_U64 inputBufferSize);

//-----------------------------------------------------------------------------
/**
*
* Method : AT_SRRF_FinaliseProcessor
*
* @param handle   valid Andor SDK camera handle - used to reference a specific instance of a SRRF processor
*
* Clean up all resources being used for SRRF Processing for the specified SRRF processor.
* This should be called once ALL processing is complete for the specified SRRF processor.
*
*/
AT_SRRF_EXP_MOD void AT_SRRF_EXP_CONV AT_SRRF_FinaliseProcessor(AT_SRRF_H handle);

//-----------------------------------------------------------------------------
/**
*
* Method : AT_SRRF_FinaliseAll
*
* Clean up all resources being used for SRRF Processing for all SRRF processors, and delete all the SRRF processors.
* This should only be called once ALL processing is complete for all current SRRF processors.
*
*/
AT_SRRF_EXP_MOD void AT_SRRF_EXP_CONV AT_SRRF_FinaliseAll();

//-----------------------------------------------------------------------------
/**
*
* Method : AT_SRRF_RunOnSingleFrameFromCpu
*
* @param handle                  valid Andor SDK camera handle - used to reference a specific instance of a SRRF processor
* @param inputFrameCpuBufferPtr  pointer to buffer containing an input frame (pointing to start of input frame)
* @param inputBufferSize         the size of the input buffer pointed to by the second parameter (in bytes)
* @param frameIndex              the input frame's index within the frame burst. Note this is zero-indexed.
*
* @return success flag
*
* Updates current SRRF result using input data that should contain a single input frame, using the specified SRRF processor. 
* The input data should be stored in CPU memory.
* This function should be called for each frame within the full frame burst to complete the SRRF analysis.
*
*/
AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_RunOnSingleFrameFromCpu(
	AT_SRRF_H handle, 
	void * inputFrameCpuBufferPtr, 
	AT_SRRF_U64 inputBufferSize, 
	AT_SRRF_U16 frameIndex);

//-----------------------------------------------------------------------------
/**
*
* Method : AT_SRRF_RunOnSingleFrameFromGpu
*
* @param handle                  valid Andor SDK camera handle - used to reference a specific instance of a SRRF processor
* @param inputFrameGpuBufferPtr  pointer to buffer containing an input frame (pointing to start of input frame)
* @param inputBufferSize         the size of the input buffer pointed to by the second parameter (in bytes)
* @param frameIndex              the input frame's index within the frame burst. Note this is zero-indexed.
*
* @return success flag
*
* Updates current SRRF result using input data that should contain a single input frame, using the specified SRRF processor.  
* The input data should be stored in GPU memory.
* This function should be called for each frame within the full frame burst to complete the SRRF analysis.
*
*/
AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_RunOnSingleFrameFromGpu(
	AT_SRRF_H handle, 
	void * inputFrameGpuBufferPtr, 
	AT_SRRF_U64 inputBufferSize, 
	AT_SRRF_U16 frameIndex, 
	void * cudaStreamPtr);

//-----------------------------------------------------------------------------
/**
*
* Method : AT_SRRF_GetResult
*
* @param handle              valid Andor SDK camera handle - used to reference a specific instance of a SRRF processor
* @param outputCpuBufferPtr  pointer to buffer to be populated with current SRRF result
* @param outputBufferSize    the size of the output buffer pointed to by the second parameter (in bytes)
*
* @return success flag
*
* Copies current SRRF result for the specified SRRF processor to the output buffer. 
* This buffer should be stored in CPU memory.
* Note: The size of the output buffer shall be magnified in each dimension by the value of the Radiality Magnification, 
* i.e. if the original input is 512 x 512 and the radiality magnification is 4, then the output shall be 2048 x 2048.
*
*/
AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_GetResult(AT_SRRF_H handle, void * outputCpuBufferPtr, AT_SRRF_U64 outputBufferSize);

//-----------------------------------------------------------------------------
/**
*
* Method : AT_SRRF_GetMeanInput
*
* @param handle              valid Andor SDK camera handle - used to reference a specific instance of a SRRF processor
* @param outputCpuBufferPtr  pointer to buffer to be populated with current benchmark image
* @param outputBufferSize    the size of the output buffer pointed to by the second parameter (in bytes)
*
* @return success flag
*
* Copies the per-pixel calculated mean of all frames previously input from the current frame burst (associated with the specified SRRF processor)
* to the specified output buffer.
* This buffer should be stored in CPU memory.
* Note: The size of the output buffer should be the same as each input frame.
* i.e. if each original input frame is 512 x 512, then the output shall be 512 x 512.
*
*/
AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_GetMeanInput(AT_SRRF_H handle, void * outputCpuBufferPtr, AT_SRRF_U64 outputBufferSize);

/**
*  Method: AT_SRRF_ValidateLicense
*
* @param handle   valid Andor SDK camera handle
*
* @return error flag
*
* Check that a valid license for SRRF is present
*
*/
AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_ValidateLicense(AT_SRRF_H handle);

//---------------------------------------------------------------------------
/**
 * 
 * Utility Functions 
 *
 */
//---------------------------------------------------------------------------

/**
*
* Method : AT_SRRF_GetLastErrorString
*
* @return the last recorded error string
*
* Return the last recorded error string
*
*/
AT_SRRF_EXP_MOD char* AT_SRRF_EXP_CONV AT_SRRF_GetLastErrorString();

/**
*  Method: AT_SRRF_CheckCudaCompatibility
*
* @return error flag
*
* Check that a CUDA compatible GPU is attached and correct driver is installed for current Runtime Library Version
*
*/
AT_SRRF_EXP_MOD int AT_SRRF_EXP_CONV AT_SRRF_CheckCudaCompatibility();

/**
*  Method: AT_SRRF_GetLibraryVersion
*
* @return the current version number of the library
*
* Return the current version number of the library
*
*/
AT_SRRF_EXP_MOD char* AT_SRRF_EXP_CONV AT_SRRF_GetLibraryVersion();

#ifdef __cplusplus
}
#endif

#endif
