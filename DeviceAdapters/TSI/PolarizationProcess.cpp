///////////////////////////////////////////////////////////////////////////////
// FILE:          PolarizationProcess.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs Scientific Imaging camera adapter
//                SDK 3, Polarization processing functions
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
#include "tl_polarization_processor.h"
#include "tl_polarization_processor_error.h"

// dynamically loaded functions from Thorlabs dlls

HMODULE g_polarizationModuleHandle(0);
TL_POLARIZATION_PROCESSOR_MODULE_INITIALIZE tl_polarization_processing_module_initialize(0);
TL_POLARIZATION_PROCESSOR_CREATE_POLARIZATION_PROCESSOR tl_polarization_create_processor(0);
TL_POLARIZATION_PROCESSOR_DESTROY_POLARIZATION_PROCESSOR tl_polarization_destroy_processor(0);
TL_POLARIZATION_PROCESSOR_MODULE_TERMINATE tl_polarization_processing_module_terminate(0);
TL_POLARIZATION_PROCESSOR_TRANSFORM tl_polarization_processor_transform(0);
TL_POLARIZATION_PROCESSOR_SET_CUSTOM_CALIBRATION_COEFFICIENTS tl_polarization_processor_set_custom_calibration_coefficients(0);
TL_POLARIZATION_PROCESSOR_GET_CUSTOM_CALIBRATION_COEFFICIENTS tl_polarization_processor_get_custom_calibration_coefficients(0);

/////////////////////////////////////////////////////////////////////////////////////////
// Local utility functions for polarization processing
/////////////////////////////////////////////////////////////////////////////////////////
/**
 * Initializes color processing pipeline. Should be called only once each time application starts.
 * Requires shutdown routine on application exit.
 * @return - error code
 */
int Tsi3Cam::InitializePolarizationProcessor()
{
	if (!globalPolarizationInitialized)
	{
		// Load the demosaic module.
		g_polarizationModuleHandle = ::LoadLibrary("thorlabs_tsi_polarization_processor.dll");
		if (!g_polarizationModuleHandle)
		{
			LogMessage("Failed to open the polarization library!");
			return ERR_INTERNAL_ERROR;
		}
	
		// Map handles to the polarization module exported functions.
		tl_polarization_processing_module_initialize = reinterpret_cast <TL_POLARIZATION_PROCESSOR_MODULE_INITIALIZE> (::GetProcAddress(g_polarizationModuleHandle, "tl_polarization_processor_module_initialize"));
		if (!tl_polarization_processing_module_initialize)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_polarizationModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
		
		tl_polarization_processing_module_terminate = reinterpret_cast <TL_POLARIZATION_PROCESSOR_MODULE_TERMINATE> (::GetProcAddress(g_polarizationModuleHandle, "tl_polarization_processor_module_terminate"));
		if (!tl_polarization_processing_module_terminate)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_polarizationModuleHandle);
			return ERR_INTERNAL_ERROR;
		}

		tl_polarization_create_processor = reinterpret_cast <TL_POLARIZATION_PROCESSOR_CREATE_POLARIZATION_PROCESSOR> (::GetProcAddress(g_polarizationModuleHandle, "tl_polarization_processor_create_polarization_processor"));
		if (!tl_polarization_create_processor)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_polarizationModuleHandle);
			return ERR_INTERNAL_ERROR;
		}

	
		tl_polarization_destroy_processor = reinterpret_cast <TL_POLARIZATION_PROCESSOR_DESTROY_POLARIZATION_PROCESSOR> (::GetProcAddress(g_polarizationModuleHandle, "tl_polarization_processor_destroy_polarization_processor"));
		if (!tl_polarization_destroy_processor)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_polarizationModuleHandle);
			return ERR_INTERNAL_ERROR;
		}
	
		tl_polarization_processor_transform = reinterpret_cast <TL_POLARIZATION_PROCESSOR_TRANSFORM> (::GetProcAddress(g_polarizationModuleHandle, "tl_polarization_processor_transform"));
		if (!tl_polarization_processor_transform)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_polarizationModuleHandle);
			return ERR_INTERNAL_ERROR;
		}

		tl_polarization_processor_set_custom_calibration_coefficients = reinterpret_cast <TL_POLARIZATION_PROCESSOR_SET_CUSTOM_CALIBRATION_COEFFICIENTS> (::GetProcAddress(g_polarizationModuleHandle, "tl_polarization_processor_set_custom_calibration_coefficients"));
		if (!tl_polarization_processor_set_custom_calibration_coefficients)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_polarizationModuleHandle);
			return ERR_INTERNAL_ERROR;
		}

		tl_polarization_processor_get_custom_calibration_coefficients = reinterpret_cast <TL_POLARIZATION_PROCESSOR_GET_CUSTOM_CALIBRATION_COEFFICIENTS> (::GetProcAddress(g_polarizationModuleHandle, "tl_polarization_processor_get_custom_calibration_coefficients"));
		if (!tl_polarization_processor_get_custom_calibration_coefficients)
		{
			LogMessage(dllLoadErr);
			::FreeLibrary(g_polarizationModuleHandle);
			return ERR_INTERNAL_ERROR;
		}


		if (tl_polarization_processing_module_initialize() != TL_POLARIZATION_PROCESSOR_ERROR_NONE)
		{
			LogMessage("Failed to initialize polarization module");
			::FreeLibrary(g_polarizationModuleHandle);
			return ERR_INTERNAL_ERROR;
		}

		globalPolarizationInitialized = true;
	}

	// call initialize functions

	tl_polarization_create_processor(&polarizationProcessor);
	if (!polarizationProcessor)
	{
		LogMessage("Failed to create polarization processor!"); 
		return ERR_INTERNAL_ERROR;
	}

	return DEVICE_OK;
}

/**
 * Tears down color processor and releases associated handles
 */
int Tsi3Cam::ShutdownPolarizationProcessor()
{
	// destroy color processor
	if (polarizationProcessor)
	{
		tl_polarization_destroy_processor(polarizationProcessor);
		polarizationProcessor = 0;
	}

	// Terminate the color processing module
	if (globalPolarizationInitialized)
	{
		if (g_polarizationModuleHandle)
		{
			tl_polarization_processing_module_terminate();
			::FreeLibrary(g_polarizationModuleHandle);
			g_polarizationModuleHandle = 0;
		}

		globalPolarizationInitialized = false;
	}

	return DEVICE_OK;
}

/**
 * Process monochrome image obtained from polarized mask sensor
 * @param monoBuf - sensor image, assumed to be 2 bytes per pixel with bit-depth defined with bitDepth parameter
 * @param outBuf - output monochrome image
 * @param bitDepth - bit depth of the input image, valid values 8-16
 * @return - error code
 */
int Tsi3Cam::PolarizationIntensity(unsigned short* monoBuf, unsigned char* colorBuf, int mono_image_width, int mono_image_height)
{
	// process
	intensityBuffer.resize(mono_image_width * mono_image_height);

	if (tl_polarization_processor_transform(	polarizationProcessor,
															TL_POLARIZATION_PROCESSOR_POLAR_PHASE_0_DEGREES,
															monoBuf,
															0,
															0,
															mono_image_width,
															mono_image_height,
															fullFrame.bitDepth,
															65535,
															nullptr,
														   reinterpret_cast<unsigned short*>(colorBuf),
															nullptr,
															nullptr,
															nullptr,
															nullptr) != TL_POLARIZATION_PROCESSOR_ERROR_NONE)
	{
		LogMessage("Failed to process polarization image!"); 
		return ERR_INTERNAL_ERROR;
	}
	
	return DEVICE_OK;
}
