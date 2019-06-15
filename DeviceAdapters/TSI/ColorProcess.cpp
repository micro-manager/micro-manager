///////////////////////////////////////////////////////////////////////////////
// FILE:          ColorProcess.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs Scientific Imaging camera adapter
//                SDK 3, Color processing functions
//                
// AUTHOR:        Nenad Amodaj, 2019
// COPYRIGHT:     Thorlabs
//
// DISCLAIMER:    This file is provided WITHOUT ANY WARRANTY;
//                without even the implied warranty of MERCHANTABILITY or
//                FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
//#include <fcntl.h>
//#include <io.h>
#pragma warning(disable : 4996) // disable warning for deprecated CRT functions on Windows 
#endif

#include "TSI3Cam.h"
#include "tl_color_demosaic.h"
#include "tl_color_processing.h"
#include "tl_color_error.h"
#include "tl_color_enum.h"

const char* dllLoadErr = "Error loading color processing functions from the dll";

// dynamically loaded functions from Thorlabs dlls
HMODULE g_demosaicModuleHandle(0);
TL_DEMOSAIC_MODULE_INITIALIZE tl_demosaic_module_initialize(0);
TL_DEMOSAIC_TRANSFORM_16_TO_48 tl_demosaic_transform_16_to_48(0);
TL_DEMOSAIC_MODULE_TERMINATE tl_demosaic_module_terminate(0);

HMODULE g_colorModuleHandle(0);
TL_COLOR_PROCESSING_MODULE_INITIALIZE tl_color_processing_module_initialize(0);
TL_COLOR_CREATE_COLOR_PROCESSOR tl_color_create_color_processor(0);
TL_COLOR_GET_BLUE_INPUT_LUT tl_color_get_blue_input_LUT(0);
TL_COLOR_GET_GREEN_INPUT_LUT tl_color_get_green_input_LUT(0);
TL_COLOR_GET_RED_INPUT_LUT tl_color_get_red_input_LUT(0);
TL_COLOR_ENABLE_INPUT_LUTS tl_color_enable_input_LUTs(0);
TL_COLOR_APPEND_MATRIX tl_color_append_matrix(0);
TL_COLOR_CLEAR_MATRIX tl_color_clear_matrix(0);
TL_COLOR_GET_BLUE_OUTPUT_LUT tl_color_get_blue_output_LUT(0);
TL_COLOR_GET_GREEN_OUTPUT_LUT tl_color_get_green_output_LUT(0);
TL_COLOR_GET_RED_OUTPUT_LUT tl_color_get_red_output_LUT(0);
TL_COLOR_ENABLE_OUTPUT_LUTS tl_color_enable_output_LUTs(0);
TL_COLOR_TRANSFORM_48_TO_48 tl_color_transform_48_to_48(0);
TL_COLOR_TRANSFORM_48_TO_24 tl_color_transform_48_to_24(0);
TL_COLOR_TRANSFORM_48_TO_32 tl_color_transform_48_to_32(0);
TL_COLOR_TRANSFORM_48_TO_64 tl_color_transform_48_to_64(0);
TL_COLOR_DESTROY_COLOR_PROCESSOR tl_color_destroy_color_processor(0);
TL_COLOR_PROCESSING_MODULE_TERMINATE tl_color_processing_module_terminate(0);

/////////////////////////////////////////////////////////////////////////////////////////
// Local utility functions for color processing
/////////////////////////////////////////////////////////////////////////////////////////
double sRGBCompand(double colorPixelIntensity)
{
	const double expFactor = 1 / 2.4;
	return ((colorPixelIntensity <= 0.0031308) ? colorPixelIntensity * 12.92 : ((1.055 * pow(colorPixelIntensity, expFactor)) - 0.055));
}

void sRGB_companding_LUT(int bit_depth, int* lut)
{
	int max_pixel_value = (1 << bit_depth) - 1;
	int LUT_size = max_pixel_value + 1;
	const double dMaxValue = static_cast <double> (max_pixel_value);
	for (int i = 0; i < LUT_size; ++i)
		lut[i] = static_cast <unsigned short> (sRGBCompand(static_cast <double> (i) / dMaxValue) * dMaxValue);
}

/**
 * Process monochrome image obtained from Bayer mask sensor with demosaic and color transformation
 * @param monoBuf - sensor image, assumed to be 2 bytes per pixel with bit-depth defined with bitDepth parameter
 * @param colorBuf - output color image 32-bit RGBA format
 * @param bitDepth - bit depth of the input image, valid values 8-16
 * @return - error code
 */
int Tsi3Cam::ColorProcess16to32(unsigned short* monoBuf, unsigned char* colorBuf, int mono_image_width, int mono_image_height)
{
	// process
	// resize temp buffer (3x larger than the monochrome buffer) to hold the demosaic (only) data.
	demosaicBuffer.resize(mono_image_width * mono_image_height * 3);

	// Demosaic the monochrome image data.
	if (tl_demosaic_transform_16_to_48(mono_image_width
												, mono_image_height
												, 0
												, 0
												, cfaPhase
												, TL_COLOR_FORMAT_BGR_PLANAR
												, TL_COLOR_FILTER_TYPE_BAYER
												, fullFrame.bitDepth
												, monoBuf
												, &demosaicBuffer[0]) != TL_COLOR_NO_ERROR)
	{
		LogMessage("Failed to demosaic the monochrome image!"); 
		return ERR_INTERNAL_ERROR;
	}
	
	// Color process the demosaic color frame.
	if (tl_color_transform_48_to_32(colorProcessor
											, &demosaicBuffer[0] // input buffer
											, TL_COLOR_FORMAT_BGR_PLANAR
											, 0
											, (1 << fullFrame.bitDepth) - 1
											, 0
											, (1 << fullFrame.bitDepth) - 1
											, 0
											, (1 << fullFrame.bitDepth) - 1
											, 8 - fullFrame.bitDepth
											, 8 - fullFrame.bitDepth
											, 8 - fullFrame.bitDepth
											, colorBuf
											, TL_COLOR_FORMAT_BGR_PIXEL
											, mono_image_width * mono_image_height) != TL_COLOR_NO_ERROR)
	{
		LogMessage("Color transform failed!"); 
		return ERR_INTERNAL_ERROR;
	}
	
	return DEVICE_OK;
}

/**
 * Process monochrome image obtained from Bayer mask sensor with demosaic and color transformation
 * @param monoBuf - sensor image, assumed to be 2 bytes per pixel with bit-depth defined with bitDepth parameter
 * @param colorBuf - output color image 64-bit RGBA format
 * @param bitDepth - bit depth of the input image, valid values 8-16
 * @return - error code
 */
int Tsi3Cam::ColorProcess16to64(unsigned short* monoBuf, unsigned char* colorBuf, int mono_image_width, int mono_image_height)
{
	// process
	// resize temp buffer (3x larger than the monochrome buffer) to hold the demosaic (only) data.
	demosaicBuffer.resize(mono_image_width * mono_image_height * 3);

	// Demosaic the monochrome image data.
	if (tl_demosaic_transform_16_to_48(mono_image_width
												, mono_image_height
												, 0
												, 0
												, cfaPhase
												, TL_COLOR_FORMAT_BGR_PLANAR
												, TL_COLOR_FILTER_TYPE_BAYER
												, fullFrame.bitDepth
												, monoBuf
												, &demosaicBuffer[0]) != TL_COLOR_NO_ERROR)
	{
		LogMessage("Failed to demosaic the monochrome image!"); 
		return ERR_INTERNAL_ERROR;
	}
	
	// Color process the demosaic color frame.
	if (tl_color_transform_48_to_64(colorProcessor
											, &demosaicBuffer[0] // input buffer
											, TL_COLOR_FORMAT_BGR_PLANAR
											, 0
											, (1 << fullFrame.bitDepth) - 1
											, 0
											, (1 << fullFrame.bitDepth) - 1
											, 0
											, (1 << fullFrame.bitDepth) - 1
											, 0
											, 0
											, 0
											, reinterpret_cast<unsigned short*>(colorBuf)
											, TL_COLOR_FORMAT_BGR_PIXEL
											, mono_image_width * mono_image_height) != TL_COLOR_NO_ERROR)
	{
		LogMessage("Color transform failed!"); 
		return ERR_INTERNAL_ERROR;
	}
	
	return DEVICE_OK;
}

/**
 * Process monochrome image obtained from Bayer mask sensor with demosaic and color transformation
 * @param monoBuf - sensor image, assumed to be 2 bytes per pixel with bit-depth defined with bitDepth parameter
 * @param colorBuf - output color image 48-bit RGB format
 * @param bitDepth - bit depth of the input image, valid values 8-16
 * @return - error code
 */
int Tsi3Cam::ColorProcess16to48WB(unsigned short* monoBuf, unsigned short* colorBuf, int mono_image_width, int mono_image_height)
{
	// process
	// resize temp buffer (3x larger than the monochrome buffer) to hold the demosaic (only) data.
	demosaicBuffer.resize(mono_image_width * mono_image_height * 3);

	// Demosaic the monochrome image data.
	if (tl_demosaic_transform_16_to_48(mono_image_width
												, mono_image_height
												, 0
												, 0
												, cfaPhase
												, TL_COLOR_FORMAT_BGR_PLANAR
												, TL_COLOR_FILTER_TYPE_BAYER
												, fullFrame.bitDepth
												, monoBuf
												, &demosaicBuffer[0]) != TL_COLOR_NO_ERROR)
	{
		LogMessage("Failed to demosaic the monochrome image!"); 
		return ERR_INTERNAL_ERROR;
	}
	
	// Color process the demosaic color frame.
	if (tl_color_transform_48_to_48(colorProcessor
											, &demosaicBuffer[0] // input buffer
											, TL_COLOR_FORMAT_BGR_PLANAR
											, 0
											, (1 << fullFrame.bitDepth) - 1
											, 0
											, (1 << fullFrame.bitDepth) - 1
											, 0
											, (1 << fullFrame.bitDepth) - 1
											, 0
											, 0
											, 0
											, colorBuf
											, TL_COLOR_FORMAT_BGR_PIXEL
											, mono_image_width * mono_image_height) != TL_COLOR_NO_ERROR)
	{
		LogMessage("Color transform failed!"); 
		return ERR_INTERNAL_ERROR;
	}
	
	return DEVICE_OK;
}


/**
 * Initializes color processing pipeline. Should be called only once each time application starts.
 * Requires shutdown routine on application exit.
 * @return - error code
 */
int Tsi3Cam::InitializeColorProcessor(bool wb/*=false*/)
{
	if (!globalColorInitialized)
	{
		// Load the demosaic module.
		g_demosaicModuleHandle = ::LoadLibrary("thorlabs_tsi_demosaic.dll");
		if (!g_demosaicModuleHandle)
		{
			LogMessage("Failed to open the demosaic library!");
			return ERR_INTERNAL_ERROR;
		}
	
		// Map handles to the demosaic module exported functions.
		tl_demosaic_module_initialize = reinterpret_cast <TL_DEMOSAIC_MODULE_INITIALIZE> (::GetProcAddress(g_demosaicModuleHandle, "tl_demosaic_module_initialize"));
		if (!tl_demosaic_module_initialize)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_demosaic_transform_16_to_48 = reinterpret_cast <TL_DEMOSAIC_TRANSFORM_16_TO_48> (::GetProcAddress(g_demosaicModuleHandle, "tl_demosaic_transform_16_to_48"));
		if (!tl_demosaic_transform_16_to_48)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_demosaic_module_terminate = reinterpret_cast <TL_DEMOSAIC_MODULE_TERMINATE> (::GetProcAddress(g_demosaicModuleHandle, "tl_demosaic_module_terminate"));
		if (!tl_demosaic_module_terminate)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		g_colorModuleHandle = ::LoadLibrary("thorlabs_tsi_color_processing.dll");
		if (!g_colorModuleHandle)
		{
			LogMessage("Failed to open the color processing library!");
			::FreeLibrary(g_demosaicModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_processing_module_initialize =reinterpret_cast <TL_COLOR_PROCESSING_MODULE_INITIALIZE> (::GetProcAddress(g_colorModuleHandle, "tl_color_processing_module_initialize"));
		if (!tl_color_processing_module_initialize)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_create_color_processor = reinterpret_cast <TL_COLOR_CREATE_COLOR_PROCESSOR> (::GetProcAddress(g_colorModuleHandle, "tl_color_create_color_processor"));
		if (!tl_color_create_color_processor)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_get_blue_input_LUT = reinterpret_cast <TL_COLOR_GET_BLUE_INPUT_LUT> (::GetProcAddress(g_colorModuleHandle, "tl_color_get_blue_input_LUT"));
		if (!tl_color_get_blue_input_LUT)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_get_green_input_LUT = reinterpret_cast <TL_COLOR_GET_GREEN_INPUT_LUT> (::GetProcAddress(g_colorModuleHandle, "tl_color_get_green_input_LUT"));
		if (!tl_color_get_green_input_LUT)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_get_red_input_LUT = reinterpret_cast <TL_COLOR_GET_RED_INPUT_LUT> (::GetProcAddress(g_colorModuleHandle, "tl_color_get_red_input_LUT"));
		if (!tl_color_get_red_input_LUT)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_enable_input_LUTs = reinterpret_cast <TL_COLOR_ENABLE_INPUT_LUTS> (::GetProcAddress(g_colorModuleHandle, "tl_color_enable_input_LUTs"));
		if (!tl_color_enable_input_LUTs)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_append_matrix = reinterpret_cast <TL_COLOR_APPEND_MATRIX> (::GetProcAddress(g_colorModuleHandle, "tl_color_append_matrix"));
		if (!tl_color_append_matrix)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_clear_matrix = reinterpret_cast <TL_COLOR_CLEAR_MATRIX> (::GetProcAddress(g_colorModuleHandle, "tl_color_clear_matrix"));
		if (!tl_color_clear_matrix)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_get_blue_output_LUT = reinterpret_cast <TL_COLOR_GET_BLUE_OUTPUT_LUT> (::GetProcAddress(g_colorModuleHandle, "tl_color_get_blue_output_LUT"));
		if (!tl_color_get_blue_output_LUT)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_get_green_output_LUT = reinterpret_cast <TL_COLOR_GET_GREEN_OUTPUT_LUT> (::GetProcAddress(g_colorModuleHandle, "tl_color_get_green_output_LUT"));
		if (!tl_color_get_green_output_LUT)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_get_red_output_LUT = reinterpret_cast <TL_COLOR_GET_RED_OUTPUT_LUT> (::GetProcAddress(g_colorModuleHandle, "tl_color_get_red_output_LUT"));
		if (!tl_color_get_red_output_LUT)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_enable_output_LUTs = reinterpret_cast <TL_COLOR_ENABLE_OUTPUT_LUTS> (::GetProcAddress(g_colorModuleHandle, "tl_color_enable_output_LUTs"));
		if (!tl_color_enable_output_LUTs)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_transform_48_to_48 = reinterpret_cast <TL_COLOR_TRANSFORM_48_TO_48> (::GetProcAddress(g_colorModuleHandle, "tl_color_transform_48_to_48"));
		if (!tl_color_transform_48_to_48)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_transform_48_to_24 = reinterpret_cast <TL_COLOR_TRANSFORM_48_TO_24> (::GetProcAddress(g_colorModuleHandle, "tl_color_transform_48_to_24"));
		if (!tl_color_transform_48_to_24)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_transform_48_to_32 = reinterpret_cast <TL_COLOR_TRANSFORM_48_TO_32> (::GetProcAddress(g_colorModuleHandle, "tl_color_transform_48_to_32"));
		if (!tl_color_transform_48_to_32)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}

		tl_color_transform_48_to_64 = reinterpret_cast <TL_COLOR_TRANSFORM_48_TO_64> (::GetProcAddress(g_colorModuleHandle, "tl_color_transform_48_to_64"));
      if (!tl_color_transform_48_to_64)
      {
			LogMessage(dllLoadErr);
         ::FreeLibrary(g_demosaicModuleHandle);
         ::FreeLibrary(g_colorModuleHandle);
         return ERR_INTERNAL_ERROR;
      }

		tl_color_destroy_color_processor = reinterpret_cast <TL_COLOR_DESTROY_COLOR_PROCESSOR> (::GetProcAddress(g_colorModuleHandle, "tl_color_destroy_color_processor"));
		if (!tl_color_destroy_color_processor)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_color_processing_module_terminate = reinterpret_cast <TL_COLOR_PROCESSING_MODULE_TERMINATE> (::GetProcAddress(g_colorModuleHandle, "tl_color_processing_module_terminate"));
		if (!tl_color_processing_module_terminate)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}

		if (tl_demosaic_module_initialize() != TL_COLOR_NO_ERROR)
		{
			LogMessage("Failed to initialize demosaic module");
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}

		if (tl_color_processing_module_initialize() != TL_COLOR_NO_ERROR)
		{
			LogMessage("Failed to initialize color processing module");
			::FreeLibrary(g_demosaicModuleHandle);
			::FreeLibrary(g_colorModuleHandle);
			return ERR_INTERNAL_ERROR;
		}

		globalColorInitialized = true;
	}

	// call initialize functions

	colorProcessor = tl_color_create_color_processor(fullFrame.bitDepth, fullFrame.bitDepth);
	if (!colorProcessor)
	{
		LogMessage("Failed to create color processor!"); 
		return ERR_INTERNAL_ERROR;
	}
	
	// configure sRGB output color space
	if (!wb)
	{
		float chromatic_adaptation_matrix[9] = { 1.0f, 0.0f, 0.0f, 0.0f, 1.37f, 0.0f, 0.0f, 0.0f, 2.79f };
		tl_camera_get_default_white_balance_matrix(camHandle, chromatic_adaptation_matrix);
		tl_color_append_matrix (colorProcessor, chromatic_adaptation_matrix);
	}

	float merged_camera_correction_sRGB_matrix[9] = { 1.25477f, -0.15359f, -0.10118f, -0.07011f, 1.13723f, -0.06713f, 0.0f, -0.26641f, 1.26641f };
	tl_camera_get_color_correction_matrix(camHandle, merged_camera_correction_sRGB_matrix);
	tl_color_append_matrix (colorProcessor, merged_camera_correction_sRGB_matrix);

	// Use the output LUTs to configure the sRGB nonlinear (companding) function.
	sRGB_companding_LUT(fullFrame.bitDepth, tl_color_get_blue_output_LUT(colorProcessor));
	sRGB_companding_LUT(fullFrame.bitDepth, tl_color_get_green_output_LUT(colorProcessor));
	sRGB_companding_LUT(fullFrame.bitDepth, tl_color_get_red_output_LUT(colorProcessor));

	// enable luts if not wb
	if (!wb)
		tl_color_enable_output_LUTs (colorProcessor, 1, 1, 1);

	return DEVICE_OK;
}

/**
 * Tears down color processor and releases associated handles
 */
int Tsi3Cam::ShutdownColorProcessor()
{
	// destroy color processor
	if (colorProcessor)
	{
		tl_color_destroy_color_processor(colorProcessor);
		colorProcessor = 0;
	}

	// Terminate the color processing module
	if (globalColorInitialized)
	{
		if (g_colorModuleHandle)
		{
			tl_color_processing_module_terminate();
			::FreeLibrary(g_colorModuleHandle);
			g_colorModuleHandle = 0;
		}

		// terminate demoisaic module
		if (g_demosaicModuleHandle)
		{
			tl_demosaic_module_terminate();
			::FreeLibrary(g_demosaicModuleHandle);
			g_demosaicModuleHandle = 0;
		}

		globalColorInitialized = false;
	}

	return DEVICE_OK;
}

int Tsi3Cam::SetWhiteBalance()
{
   if (!color)
      return DEVICE_OK;

   InterlockedExchange(&whiteBalancePending, 1);
   whiteBalance = true;

   return DEVICE_OK;
}

int Tsi3Cam::ClearWhiteBalance()
{
	InterlockedExchange(&whiteBalancePending, 0);
   ShutdownColorProcessor();
	int ret = InitializeColorProcessor(false);
   whiteBalance = false;
	return ret;
}

/**
 * Applies precomputed gains for rgb WB correction to color pipeline
 */
int Tsi3Cam::ApplyWhiteBalance(double redScaler, double greenScaler, double blueScaler)
{
	// reset current color matrix
	tl_color_clear_matrix(colorProcessor);

	// apply wb scaling
	float grey_world_balance_matrix [9] = { (float)redScaler, 0.0f, 0.0f, 0.0f, (float)greenScaler, 0.0f, 0.0f, 0.0f, (float)blueScaler };
	tl_color_append_matrix (colorProcessor, grey_world_balance_matrix);

	float merged_camera_correction_sRGB_matrix[9] = { 1.25477f, -0.15359f, -0.10118f, -0.07011f, 1.13723f, -0.06713f, 0.0f, -0.26641f, 1.26641f };
	tl_camera_get_color_correction_matrix(camHandle, merged_camera_correction_sRGB_matrix);
	tl_color_append_matrix(colorProcessor, merged_camera_correction_sRGB_matrix);

	return DEVICE_OK;
}

/**
 * Enables output lookup tables
 */
void Tsi3Cam::EnableColorOutputLUTs()
{
    tl_color_enable_output_LUTs (colorProcessor, 1, 1, 1);
}



