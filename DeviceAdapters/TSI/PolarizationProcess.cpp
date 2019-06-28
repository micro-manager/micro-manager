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
		// Load the polarization module.
		std::string polarPath = sdkPath + "thorlabs_tsi_polarization_processor.dll";
		g_polarizationModuleHandle = ::LoadLibrary(polarPath.c_str());
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
 * @param imgType - polarization image type
 * @return - error code
 */
int Tsi3Cam::TransformPolarizationImage(unsigned short* monoBuf, unsigned char* outBuf, int mono_image_width, int mono_image_height, PolarImageType imgType)
{
	if (imgType == Raw)
	{
		memcpy(outBuf, monoBuf, mono_image_width * mono_image_height * sizeof(unsigned short));
	}
	else if (imgType == Quad)
	{
		SeparateQuadViewAngles(polarPhase, monoBuf, reinterpret_cast<unsigned short*>(outBuf), mono_image_width, mono_image_height);
	}
	else
	{
		if (tl_polarization_processor_transform(	polarizationProcessor,
																polarPhase,
																monoBuf,
																0,
																0,
																mono_image_width,
																mono_image_height,
																fullFrame.bitDepth,
																65535, //4095,
																nullptr,
															   imgType == Intensity ? reinterpret_cast<unsigned short*>(outBuf) : nullptr,
																nullptr,
																nullptr,
																imgType == Azimuth ? reinterpret_cast<unsigned short*>(outBuf) : nullptr,
																imgType == DoLP ? reinterpret_cast<unsigned short*>(outBuf) : nullptr) != TL_POLARIZATION_PROCESSOR_ERROR_NONE)
		{
			LogMessage("Failed to process polarization image!"); 
			return ERR_INTERNAL_ERROR;
		}
	}
	
	return DEVICE_OK;
}

void Tsi3Cam::SeparateQuadViewAngles(int polarPhase, unsigned short* sourceImage, unsigned short* destImage, int sourceWidth, int sourceHeight)
{
    int halfRows = sourceHeight / 2;
    const int halfCols = sourceWidth / 2;

    int topRightRowStart;
    int topLeftRowStart;
    int bottomLeftRowStart;
    int bottomRightRowStart;
    int topRightColStart;
    int topLeftColStart;
    int bottomLeftColStart;
    int bottomRightColStart;

    switch (polarPhase)
    {
    case 0:
    {
        topRightRowStart = 0;
        topLeftRowStart = 0;
        bottomLeftRowStart = 1;
        bottomRightRowStart = 1;
        topRightColStart = 1;
        topLeftColStart = 0;
        bottomLeftColStart = 0;
        bottomRightColStart = 1;
    }
    break;
    case 1:
    {
        topRightRowStart = 1;
        topLeftRowStart = 1;
        bottomLeftRowStart = 0;
        bottomRightRowStart = 0;
        topRightColStart = 1;
        topLeftColStart = 0;
        bottomLeftColStart = 0;
        bottomRightColStart = 1;
    }
    break;
    case 2:
    {
        topRightRowStart = 1;
        topLeftRowStart = 1;
        bottomLeftRowStart = 0;
        bottomRightRowStart = 0;
        topRightColStart = 0;
        topLeftColStart = 1;
        bottomLeftColStart = 1;
        bottomRightColStart = 0;
    }
    break;
    case 3:
    {
        topRightRowStart = 0;
        topLeftRowStart = 0;
        bottomLeftRowStart = 1;
        bottomRightRowStart = 1;
        topRightColStart = 0;
        topLeftColStart = 1;
        bottomLeftColStart = 1;
        bottomRightColStart = 0;
    }
    break;
    default:
    {
        assert(!"Attempting to use unknown polar phase");
		  return;
    }
    }


    int topLeftRow = 0;
    int topLeftCol = 0;

    int topRightRow = 0;
    int topRightCol = halfCols;

    int bottomLeftRow = halfRows;
    int bottomLeftCol = 0;

    int bottomRightRow = halfRows;
    int bottomRightCol = halfCols;

    for (int row = topRightRowStart; row < sourceHeight; row += 2)
    {
        for (int col = topRightColStart; col < sourceWidth; col += 2)
        {
            int index = sourceWidth * row + col;

            if (topRightCol == sourceWidth)
            {
                topRightCol = halfCols;
                topRightRow++;
            }

            int quadOneIndex = sourceWidth * topRightRow + topRightCol;
            topRightCol++;

            destImage[quadOneIndex] = sourceImage[index];
        }
    }

    for (int row = topLeftRowStart; row < sourceHeight; row += 2)
    {
        for (int col = topLeftColStart; col < sourceWidth; col += 2)
        {
            int index = sourceWidth * row + col;

            if (topLeftCol == halfCols)
            {
                topLeftCol = 0;
                topLeftRow++;
            }

            int quadTwoIndex = sourceWidth * topLeftRow + topLeftCol;
            topLeftCol++;

            destImage[quadTwoIndex] = sourceImage[index];
        }
    }

    for (int row = bottomLeftRowStart; row < sourceHeight; row += 2)
    {
        for (int col = bottomLeftColStart; col < sourceWidth; col += 2)
        {
            int index = sourceWidth * row + col;

            if (bottomLeftCol == halfCols)
            {
                bottomLeftCol = 0;
                bottomLeftRow++;
            }

            int quadThreeIndex = sourceWidth * bottomLeftRow + bottomLeftCol;
            bottomLeftCol++;

            destImage[quadThreeIndex] = sourceImage[index];
        }
    }

    for (int row = bottomRightRowStart; row < sourceHeight; row += 2)
    {
        for (int col = bottomRightColStart; col < sourceWidth; col += 2)
        {
            int index = sourceWidth * row + col;

            if (bottomRightCol == sourceWidth)
            {
                bottomRightCol = halfCols;
                bottomRightRow++;
            }

            int quadFourIndex = sourceWidth * bottomRightRow + bottomRightCol;
            bottomRightCol++;

            destImage[quadFourIndex] = sourceImage[index];
        }
    }
}
