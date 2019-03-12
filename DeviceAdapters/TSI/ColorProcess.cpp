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

HMODULE demosaic_module_handle(0);
TL_DEMOSAIC_MODULE_INITIALIZE tl_demosaic_module_initialize(0);
TL_DEMOSAIC_TRANSFORM_16_TO_48 tl_demosaic_transform_16_to_48(0);
TL_DEMOSAIC_MODULE_TERMINATE tl_demosaic_module_terminate(0);
HMODULE cc_module_handle(0);
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
TL_COLOR_DESTROY_COLOR_PROCESSOR tl_color_destroy_color_processor(0);
TL_COLOR_PROCESSING_MODULE_TERMINATE tl_color_processing_module_terminate(0);
TL_COLOR_FILTER_ARRAY_PHASE g_cfaPhase(TL_COLOR_FILTER_ARRAY_PHASE_BAYER_BLUE);


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

int Tsi3Cam::ColorProcess16to32(unsigned short* monoBuf, unsigned char* colorBuf, int mono_image_width, int mono_image_height)
{
	// process
	// Allocate a temporary buffer (3x larger than the monochrome buffer) to hold the demosaic (only) data.
	unsigned short* demosaic_color_buffer = new unsigned short[mono_image_width * mono_image_height * 3];

	// Demosaic the monochrome image data.
	if (tl_demosaic_transform_16_to_48(mono_image_width
												, mono_image_height
												, 0
												, 0
												, g_cfaPhase
												, TL_COLOR_FORMAT_BGR_PLANAR
												, TL_COLOR_FILTER_TYPE_BAYER
												, 14
												, monoBuf
												, demosaic_color_buffer) != TL_COLOR_NO_ERROR)
	{
		LogMessage("Failed to demosaic the monochrome image!"); 
		delete[](demosaic_color_buffer);
		tl_demosaic_module_terminate();
		tl_color_processing_module_terminate();
		::FreeLibrary(cc_module_handle);
		::FreeLibrary(demosaic_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	// Create a color processor instance.
	void* color_processor_inst = tl_color_create_color_processor(14, 14); // 14-bit image data
	if (!color_processor_inst)
	{
		delete[](demosaic_color_buffer);
		tl_demosaic_module_terminate();
		tl_color_processing_module_terminate();
		::FreeLibrary(cc_module_handle);
		::FreeLibrary(demosaic_module_handle);
	}
	
	// configure sRGB output color space
	float chromatic_adaptation_matrix[9] = { 1.0f, 0.0f, 0.0f, 0.0f, 1.37f, 0.0f, 0.0f, 0.0f, 2.79f };
	float merged_camera_correction_sRGB_matrix[9] = { 1.25477f, -0.15359f, -0.10118f, -0.07011f, 1.13723f, -0.06713f, 0.0f, -0.26641f, 1.26641f };
	tl_color_append_matrix (color_processor_inst, chromatic_adaptation_matrix);
	tl_color_append_matrix (color_processor_inst, merged_camera_correction_sRGB_matrix);

	// Use the output LUTs to configure the sRGB nonlinear (companding) function.
	sRGB_companding_LUT(14, tl_color_get_blue_output_LUT(color_processor_inst));
	sRGB_companding_LUT(14, tl_color_get_green_output_LUT(color_processor_inst));
	sRGB_companding_LUT(14, tl_color_get_red_output_LUT(color_processor_inst));
	
	// Color process the demosaic color frame.

	if (tl_color_transform_48_to_32(color_processor_inst
											, demosaic_color_buffer // input buffer
											, TL_COLOR_FORMAT_BGR_PLANAR
											, 0
											, 1 << 14
											, 0
											, 1 << 14
											, 0
											, 1 << 14
											, 0
											, 0
											, 0
											, colorBuf
											, TL_COLOR_FORMAT_BGR_PIXEL
											, mono_image_width * mono_image_height) != TL_COLOR_NO_ERROR)
	{
		delete[](demosaic_color_buffer);
		tl_demosaic_module_terminate();
		tl_color_processing_module_terminate();
		::FreeLibrary(cc_module_handle);
		::FreeLibrary(demosaic_module_handle);
	}

	tl_color_destroy_color_processor(color_processor_inst);

	// Clean up.
	delete[](demosaic_color_buffer);
		
	return DEVICE_OK;
}

int Tsi3Cam::InitializeColorProcessor()
{
	// Load the demosaic module.
	demosaic_module_handle = ::LoadLibrary("thorlabs_tsi_demosaic.dll");
	if (!demosaic_module_handle)
	{
		LogMessage("Failed to open the demosaic library!");
		return ERR_INTERNAL_ERROR;
	}

	// Map handles to the demosaic module exported functions.
	tl_demosaic_module_initialize = reinterpret_cast <TL_DEMOSAIC_MODULE_INITIALIZE> (::GetProcAddress(demosaic_module_handle, "tl_demosaic_module_initialize"));
	if (!tl_demosaic_module_initialize)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_demosaic_transform_16_to_48 = reinterpret_cast <TL_DEMOSAIC_TRANSFORM_16_TO_48> (::GetProcAddress(demosaic_module_handle, "tl_demosaic_transform_16_to_48"));
	if (!tl_demosaic_transform_16_to_48)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_demosaic_module_terminate = reinterpret_cast <TL_DEMOSAIC_MODULE_TERMINATE> (::GetProcAddress(demosaic_module_handle, "tl_demosaic_module_terminate"));
	if (!tl_demosaic_module_terminate)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	cc_module_handle = ::LoadLibrary("thorlabs_tsi_color_processing.dll");
	if (!cc_module_handle)
	{
		LogMessage("Failed to open the color processing library!");
		::FreeLibrary(demosaic_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_processing_module_initialize =reinterpret_cast <TL_COLOR_PROCESSING_MODULE_INITIALIZE> (::GetProcAddress(cc_module_handle, "tl_color_processing_module_initialize"));
	if (!tl_color_processing_module_initialize)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_create_color_processor = reinterpret_cast <TL_COLOR_CREATE_COLOR_PROCESSOR> (::GetProcAddress(cc_module_handle, "tl_color_create_color_processor"));
	if (!tl_color_create_color_processor)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_get_blue_input_LUT = reinterpret_cast <TL_COLOR_GET_BLUE_INPUT_LUT> (::GetProcAddress(cc_module_handle, "tl_color_get_blue_input_LUT"));
	if (!tl_color_get_blue_input_LUT)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_get_green_input_LUT = reinterpret_cast <TL_COLOR_GET_GREEN_INPUT_LUT> (::GetProcAddress(cc_module_handle, "tl_color_get_green_input_LUT"));
	if (!tl_color_get_green_input_LUT)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_get_red_input_LUT = reinterpret_cast <TL_COLOR_GET_RED_INPUT_LUT> (::GetProcAddress(cc_module_handle, "tl_color_get_red_input_LUT"));
	if (!tl_color_get_red_input_LUT)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_enable_input_LUTs = reinterpret_cast <TL_COLOR_ENABLE_INPUT_LUTS> (::GetProcAddress(cc_module_handle, "tl_color_enable_input_LUTs"));
	if (!tl_color_enable_input_LUTs)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_append_matrix = reinterpret_cast <TL_COLOR_APPEND_MATRIX> (::GetProcAddress(cc_module_handle, "tl_color_append_matrix"));
	if (!tl_color_append_matrix)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_clear_matrix = reinterpret_cast <TL_COLOR_CLEAR_MATRIX> (::GetProcAddress(cc_module_handle, "tl_color_clear_matrix"));
	if (!tl_color_clear_matrix)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_get_blue_output_LUT = reinterpret_cast <TL_COLOR_GET_BLUE_OUTPUT_LUT> (::GetProcAddress(cc_module_handle, "tl_color_get_blue_output_LUT"));
	if (!tl_color_get_blue_output_LUT)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_get_green_output_LUT = reinterpret_cast <TL_COLOR_GET_GREEN_OUTPUT_LUT> (::GetProcAddress(cc_module_handle, "tl_color_get_green_output_LUT"));
	if (!tl_color_get_green_output_LUT)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_get_red_output_LUT = reinterpret_cast <TL_COLOR_GET_RED_OUTPUT_LUT> (::GetProcAddress(cc_module_handle, "tl_color_get_red_output_LUT"));
	if (!tl_color_get_red_output_LUT)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_enable_output_LUTs = reinterpret_cast <TL_COLOR_ENABLE_OUTPUT_LUTS> (::GetProcAddress(cc_module_handle, "tl_color_enable_output_LUTs"));
	if (!tl_color_enable_output_LUTs)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_transform_48_to_48 = reinterpret_cast <TL_COLOR_TRANSFORM_48_TO_48> (::GetProcAddress(cc_module_handle, "tl_color_transform_48_to_48"));
	if (!tl_color_transform_48_to_48)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_transform_48_to_24 = reinterpret_cast <TL_COLOR_TRANSFORM_48_TO_24> (::GetProcAddress(cc_module_handle, "tl_color_transform_48_to_24"));
	if (!tl_color_transform_48_to_24)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_transform_48_to_32 = reinterpret_cast <TL_COLOR_TRANSFORM_48_TO_32> (::GetProcAddress(cc_module_handle, "tl_color_transform_48_to_32"));
	if (!tl_color_transform_48_to_32)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_destroy_color_processor = reinterpret_cast <TL_COLOR_DESTROY_COLOR_PROCESSOR> (::GetProcAddress(cc_module_handle, "tl_color_destroy_color_processor"));
	if (!tl_color_destroy_color_processor)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	tl_color_processing_module_terminate = reinterpret_cast <TL_COLOR_PROCESSING_MODULE_TERMINATE> (::GetProcAddress(cc_module_handle, "tl_color_processing_module_terminate"));
	if (!tl_color_processing_module_terminate)
	{
		LogMessage(dllLoadErr);
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	// initialize
	tl_camera_get_color_filter_array_phase(camHandle, &g_cfaPhase);

	if (tl_demosaic_module_initialize() != TL_COLOR_NO_ERROR)
	{
		LogMessage("Failed to initialize demosaic module");
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	if (tl_color_processing_module_initialize() != TL_COLOR_NO_ERROR)
	{
		LogMessage("Failed to initialize color processing module");
		::FreeLibrary(demosaic_module_handle);
		::FreeLibrary(cc_module_handle);
		return ERR_INTERNAL_ERROR;
	}

	return DEVICE_OK;
}

int Tsi3Cam::ShutdownColorProcessor()
{
	// Terminate the color processing module
	tl_color_processing_module_terminate();

	// terminate demoisaic module
	tl_demosaic_module_terminate();

	::FreeLibrary(cc_module_handle);
	return DEVICE_OK;
}


