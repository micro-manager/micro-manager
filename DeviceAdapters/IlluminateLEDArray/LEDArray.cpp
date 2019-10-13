//////////////////////////////////////////////////////////////////////////////
// FILE:          LedArray.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for illuminate LED controller firmware
//                Needs accompanying firmware to be installed on the LED Array:
//                https://github.com/zfphil/illuminate
//
// COPYRIGHT:     Regents of the University of California
// LICENSE:       LGPL
//
// AUTHOR:        Henry Pinkard, hbp@berkeley.edu, 12/13/2016
// AUTHOR:        Zack Phillips, zkphil@berkeley.edu, 3/1/2019
//
//////////////////////////////////////////////////////////////////////////////

#include "LEDArray.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <cstdio>
#include <cstring>
#include <string>
#include "rapidjson/document.h"
#include "rapidjson/writer.h"
#include "rapidjson/stringbuffer.h"
#include <algorithm>
#include <math.h>

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#define snprintf _snprintf 
#endif


int LedArray::Initialize()
{
	if (initialized_)
		return DEVICE_OK;

	// Name
	int ret = CreateProperty(MM::g_Keyword_Name, g_Keyword_DeviceName, MM::String, true);
	if (DEVICE_OK != ret)
		return ret;

	// Description
	ret = CreateProperty(MM::g_Keyword_Description, "LED Array", MM::String, true);
	assert(DEVICE_OK == ret);

	// Most Recent Serial Response
	ret = CreateProperty(g_Keyword_Response, "", MM::String, false);
	assert(DEVICE_OK == ret);

	// Shutter
	CPropertyAction* pActshutter = new CPropertyAction(this, &LedArray::OnShutterOpen);
	ret = CreateProperty(g_Keyword_Shutter, "0", MM::Integer, false, pActshutter);
	assert(DEVICE_OK == ret);
	AddAllowedValue(g_Keyword_Shutter, "0");
	AddAllowedValue(g_Keyword_Shutter, "1");

	// Reset
	CPropertyAction* pActreset = new CPropertyAction(this, &LedArray::OnReset);
	ret = CreateProperty(g_Keyword_Reset, "0", MM::String, false, pActreset);
	assert(DEVICE_OK == ret);
	AddAllowedValue(g_Keyword_Reset, "0");
	AddAllowedValue(g_Keyword_Reset, "1");

	// Manual Command Interface
	CPropertyAction* pCommand = new CPropertyAction(this, &LedArray::OnCommand);
	ret = CreateProperty(g_Keyword_Command, "", MM::String, false, pCommand);
	assert(DEVICE_OK == ret);

	// Illumination Pattern:
	CPropertyAction* pActpat = new CPropertyAction(this, &LedArray::OnPattern);
	CreateProperty(g_Keyword_Pattern, g_Pattern_None, MM::String, false, pActpat);
	AddAllowedValue(g_Keyword_Pattern, g_Pattern_None);
	AddAllowedValue(g_Keyword_Pattern, g_Pattern_Brightfield);
	AddAllowedValue(g_Keyword_Pattern, g_Pattern_Darkfield);
	AddAllowedValue(g_Keyword_Pattern, g_Pattern_Dpc);
	AddAllowedValue(g_Keyword_Pattern, g_Pattern_ColorDpc);
	AddAllowedValue(g_Keyword_Pattern, g_Pattern_ColorDarkfield);
	AddAllowedValue(g_Keyword_Pattern, g_Pattern_ManualLedIndices);
	AddAllowedValue(g_Keyword_Pattern, g_Pattern_Annulus);
	AddAllowedValue(g_Keyword_Pattern, g_Pattern_HalfAnnulus);
	AddAllowedValue(g_Keyword_Pattern, g_Pattern_CenterLed);
	AddAllowedValue(g_Keyword_Pattern, g_Pattern_Clear);

	// Illumination Pattern Orientation
	CPropertyAction* pActpatOrientation = new CPropertyAction(this, &LedArray::OnPatternOrientation);
	ret = CreateProperty(g_Keyword_PatternOrientation, g_Orientation_Top, MM::String, false, pActpatOrientation);
	assert(DEVICE_OK == ret);
	AddAllowedValue(g_Keyword_PatternOrientation, g_Orientation_Top);
	AddAllowedValue(g_Keyword_PatternOrientation, g_Orientation_Bottom);
	AddAllowedValue(g_Keyword_PatternOrientation, g_Orientation_Left);
	AddAllowedValue(g_Keyword_PatternOrientation, g_Orientation_Right);

	// Annulus Width
	CPropertyAction* pActAnnuluswidth = new CPropertyAction(this, &LedArray::OnAnnulusWidth);
	ret = CreateProperty(g_Keyword_AnnulusWidth, "0.2", MM::Float, false, pActAnnuluswidth);
	assert(DEVICE_OK == ret);

	// LED indices illumination:
	CPropertyAction* pActled = new CPropertyAction(this, &LedArray::OnSetManualLedList);
	ret = CreateProperty(g_Keyword_LedList, "0.1.2.3.4", MM::String, false, pActled);
	assert(DEVICE_OK == ret);

	// Device MAC address
	//CreateProperty(g_Keyword_MacAddress, "None", MM::String, false);
	//assert(DEVICE_OK == ret);

	// Device name
	//CreateProperty(g_Keyword_LedArrayType, "None", MM::String, false);
	//assert(DEVICE_OK == ret);

	// Parse trigger input count
	ret = CreateProperty(g_Keyword_TriggerInputCount, "-1", MM::Integer, true);
	assert(DEVICE_OK == ret);

	// Parse trigger output count
	ret = CreateProperty(g_Keyword_TriggerOutputCount, "-1", MM::Integer, true);
	assert(DEVICE_OK == ret);

	//// Parse part number
	//ret = CreateProperty(g_Keyword_PartNumber, "-1", MM::Integer, false);
	//assert(DEVICE_OK == ret);

	//// Parse serial number
	//ret = CreateProperty(g_Keyword_SerialNumber, "-1", MM::Integer, false);
	//assert(DEVICE_OK == ret);

	// Parse bit depth
	ret = CreateProperty(g_Keyword_NativeBitDepth, "-1", MM::Integer, true);
	assert(DEVICE_OK == ret);

	//// Parse color channel count
	//ret = CreateProperty(g_Keyword_ColorChannelCount, "-1", MM::Integer, false);
	//assert(DEVICE_OK == ret);

	//// Parse interface version
	//ret = CreateProperty(g_Keyword_InterfaceVersion, "-1.0", MM::Float, false);
	//assert(DEVICE_OK == ret);

	// Brightness slider
	CPropertyAction* pActbr = new CPropertyAction(this, &LedArray::OnBrightness);
	ret = CreateProperty(g_Keyword_Brightness, std::to_string((long long)brightness).c_str(), MM::Float, false, pActbr);
	assert(DEVICE_OK == ret);
	SetPropertyLimits(g_Keyword_Brightness, 0, 255);

	// Check that we have a controller:
	PurgeComPort(port_.c_str());

	// Set the device to machine-readable mode
	SetMachineMode(true);

	// Get Device Parameters
	GetDeviceParameters();

	// Red Color Slider
	CPropertyAction* pActr = new CPropertyAction(this, &LedArray::OnColorBalanceRed);
	ret = CreateProperty(g_Keyword_ColorBalanceRed, std::to_string((long long)color_r).c_str(), MM::Float, false, pActr);
	SetPropertyLimits(g_Keyword_ColorBalanceRed, 0, 255);

	// Green Color Slider
	CPropertyAction* pActg = new CPropertyAction(this, &LedArray::OnColorBalanceGreen);
	ret = CreateProperty(g_Keyword_ColorBalanceGreen, std::to_string((long long)color_g).c_str(), MM::Float, false, pActg);
	SetPropertyLimits(g_Keyword_ColorBalanceGreen, 0, 255);

	// Blue Color Slider
	CPropertyAction* pActb = new CPropertyAction(this, &LedArray::OnColorBalanceBlue);
	ret = CreateProperty(g_Keyword_ColorBalanceBlue, std::to_string((long long)color_b).c_str(), MM::Float, false, pActb);
	SetPropertyLimits(g_Keyword_ColorBalanceBlue, 0, 255);

	// Disable color sliders if LED array is not color
	if (!array_is_color)
	{
		// Set red color slider to read-only
		MM::Property* pChildProperty = (MM::Property *)pActr;
		pChildProperty->SetReadOnly(true);

		// Set red color slider to read-only
		pChildProperty = (MM::Property *)pActg;
		pChildProperty->SetReadOnly(true);

		// Set red color slider to read-only
		pChildProperty = (MM::Property *)pActb;
		pChildProperty->SetReadOnly(true);
	}

	// Objective NA Property
	CPropertyAction* pActap = new CPropertyAction(this, &LedArray::OnObjectiveAperture);
	ret = CreateProperty(g_Keyword_ObjectiveNumericalAperture, std::to_string((long double)objective_numerical_aperture).c_str(), MM::Float, false, pActap);

	// Pattern NA Property
	CPropertyAction* pActap23 = new CPropertyAction(this, &LedArray::OnPatternAperture);
	ret = CreateProperty(g_Keyword_AnnulusNumericalAperture, std::to_string((long double)annulus_numerical_aperture).c_str(), MM::Float, false, pActap23);

	// LED Array Distance
	CPropertyAction* pActap2 = new CPropertyAction(this, &LedArray::OnDistance);
	ret = CreateProperty(g_Keyword_SetArrayDistanceMM, std::to_string((long double)array_distance_z).c_str(), MM::Float, false, pActap2);

	// LED Count
	ret = CreateProperty(g_Keyword_LedCount, std::to_string((long long)led_count).c_str(), MM::Integer, true);

	// LED Positions
	//CPropertyAction* pActap2 = new CPropertyAction(this, &LedArray::OnDistance);
	//ret = CreateProperty(g_Keyword_LedPositions, "", MM::String, false);	

	// Sync Current Parameters
	SyncState();

	// Generate LED position array

	// Read LED Positions
	//ReadLedPositions();

	// Reset the LED array
	Reset();

	ret = UpdateStatus();
	if (ret != DEVICE_OK)
		return ret;
	initialized_ = true;

	return DEVICE_OK;
}

int LedArray::SendCommand(const char * command, bool get_response)
{
	// Purge COM port
	PurgeComPort(port_.c_str());

	// Convert command to std::string
	std::string _command(command);

	// Send command to device
	_command += "\n";
	WriteToComPort(port_.c_str(), &((unsigned char *)_command.c_str())[0], (unsigned int)_command.length());

	// Impose a small delay to prevent overloading buffer
	Sleep(SERIAL_DELAY_MS);

	// Get/check response if desired
	if (get_response)
		return GetResponse();
	else
		return DEVICE_OK;


}

int LedArray::GetResponse()
{
	// Get answer
	GetSerialAnswer(port_.c_str(), "-==-", _serial_answer);

	// Set property
	SetProperty(g_Keyword_Response, _serial_answer.c_str());

	// Search for error
	std::string error_flag("ERROR");
	if (_serial_answer.find(error_flag) != std::string::npos)
		return DEVICE_ERR;
	else
		return DEVICE_OK;
}

int LedArray::SyncState()
{

	// Get current NA
	SendCommand("na", true);
	std::string na_str("NA.");
	objective_numerical_aperture = (float)atoi(_serial_answer.substr(_serial_answer.find(na_str) + na_str.length(), _serial_answer.length() - na_str.length()).c_str()) / 100.0;

	// Get current array distance
	SendCommand("sad", true);
	std::string sad_str("DZ.");
	array_distance_z = (float)atoi(_serial_answer.substr(_serial_answer.find(sad_str) + sad_str.length(), _serial_answer.length() - sad_str.length()).c_str());

	// Get Current Color
	if (array_is_color)
	{
		// Get color intensities
		SendCommand("sc", true);

		// Vector of string to save tokens 
		std::vector <std::string> color_values;

		// stringstream class check1 
		std::stringstream check1(_serial_answer);
		std::string intermediate;

		// Tokenizing w.r.t. space ' ' 
		while (getline(check1, intermediate, '.'))
			color_values.push_back(intermediate);

		if (color_values.size() > 0)
		{
			// Remove first value (the SC)
			color_values.erase(color_values.begin());

			if (color_channel_count == 3)
			{
				// Assign current color values
				color_r = strtoul((color_values.at(0).c_str()), NULL, false);
				color_g = strtoul((color_values.at(1).c_str()), NULL, false);
				color_b = strtoul((color_values.at(2).c_str()), NULL, false);

				// Red:
				SetProperty(g_Keyword_ColorBalanceRed, std::to_string((long long)color_r).c_str());

				// Green:
				SetProperty(g_Keyword_ColorBalanceGreen, std::to_string((long long)color_g).c_str());

				// Blue:
				SetProperty(g_Keyword_ColorBalanceBlue, std::to_string((long long)color_b).c_str());
			}
		}
	}

	// Get current brightness
	SendCommand("sb", true);
	std::string brightness_str("SB.");
	brightness = (long)atoi(_serial_answer.substr(_serial_answer.find(brightness_str) + brightness_str.length(), _serial_answer.length() - brightness_str.length()).c_str());

	// Set brightness:
	SetProperty(g_Keyword_Brightness, std::to_string((long long)brightness).c_str());

	// Set Numerical Aperture:
	SetProperty(g_Keyword_ObjectiveNumericalAperture, std::to_string((long double)objective_numerical_aperture).c_str());
	SetProperty(g_Keyword_AnnulusNumericalAperture, std::to_string((long double)annulus_numerical_aperture).c_str());

	//Set Array Dist:
	SetProperty(g_Keyword_SetArrayDistanceMM, std::to_string((long double)array_distance_z).c_str());

	return DEVICE_OK;
}

int LedArray::SetMachineMode(bool mode)
{
	// Check that we have a controller:
	PurgeComPort(port_.c_str());

	if (mode)
	{
		// Send command to device
		unsigned char myString[] = "machine\n";
		WriteToComPort(port_.c_str(), &myString[0], 8);
	}
	else {
		// Send command to device
		unsigned char myString[] = "human\n";
		WriteToComPort(port_.c_str(), &myString[0], 7);
	}

	std::string answer;
	GetSerialAnswer(port_.c_str(), "-==-", answer);

	// Set property
	SetProperty(g_Keyword_Response, answer.c_str());

	return DEVICE_OK;
}

int LedArray::GetDeviceParameters()
{
	// Send command to device
	SendCommand("pp", true);

	// Set property
	SetProperty(g_Keyword_Response, _serial_answer.c_str());

	// Set Properties based on JSON output
	rapidjson::Document d;
	d.Parse(_serial_answer.c_str());

	// Parse json if it is valid
	if (d.IsObject())
	{

		//// Get properties from json and set as read-only properties
		//if (d.HasMember("mac_address"))
		//	SetProperty(g_Keyword_MacAddress, d["mac_address"].GetString());

		//// Parse device name
		//if (d.HasMember("device_name"))
		//	SetProperty(g_Keyword_LedArrayType, d["device_name"].GetString());

		// Parse LED count
		if (d.HasMember("led_count"))
		{
			led_count = (long)d["led_count"].GetUint64();
			SetProperty(g_Keyword_LedCount, std::to_string((long long)led_count).c_str());
		}

		// Parse trigger input count
		if (d.HasMember("trigger_input_count"))
		{
			trigger_input_count = (int)d["trigger_input_count"].GetUint64();
			SetProperty(g_Keyword_TriggerInputCount, std::to_string((long long)trigger_input_count).c_str());
		}

		// Parse trigger output count
		if (d.HasMember("trigger_output_count"))
		{
			trigger_output_count = (int)d["trigger_output_count"].GetUint64();
			SetProperty(g_Keyword_TriggerOutputCount, std::to_string((long long)trigger_output_count).c_str());
		}

		//// Parse part number
		//if (d.HasMember("part_number"))
		//{
		//	part_number = (int)d["part_number"].GetUint64();
		//	SetProperty(g_Keyword_PartNumber, std::to_string((long long)part_number).c_str());
		//}

		//// Parse serial number
		//if (d.HasMember("serial_number"))
		//{
		//	serial_number = (int)d["serial_number"].GetUint64();
		//	SetProperty(g_Keyword_SerialNumber, std::to_string((long long)serial_number).c_str());
		//}

		// Parse bit depth
		if (d.HasMember("bit_depth"))
		{
			bit_depth = (int)d["bit_depth"].GetUint64();
			SetProperty(g_Keyword_NativeBitDepth, std::to_string((long long)bit_depth).c_str());
		}

		// Parse color channel count
		if (d.HasMember("color_channel_count"))
		{
			color_channel_count = (int)d["color_channel_count"].GetUint64();

			// Set color flag
			array_is_color = color_channel_count > 1;

			// Update property
			//SetProperty(g_Keyword_ColorChannelCount, std::to_string((long long)color_channel_count).c_str());
		}

		//// Parse interface version
		//if (d.HasMember("interface_version"))
		//{
		//	interface_version = d["interface_version"].GetFloat();
		//	SetProperty(g_Keyword_InterfaceVersion, std::to_string((long double)interface_version).c_str());
		//}

		// Parse color channels
		if (d.HasMember("color_channel_center_wavelengths"))
		{
			// Parse red channel
			if (d["color_channel_center_wavelengths"].HasMember("r"))
				CreateProperty(g_Keyword_WavelengthRed, std::to_string((long double)d["color_channel_center_wavelengths"]["r"].GetDouble()).c_str(), MM::String, true);

			// Parse green channel
			if (d["color_channel_center_wavelengths"].HasMember("g"))
				CreateProperty(g_Keyword_WavelengthGreen, std::to_string((long double)d["color_channel_center_wavelengths"]["g"].GetDouble()).c_str(), MM::String, true);

			// Parse blue channel
			if (d["color_channel_center_wavelengths"].HasMember("b"))
				CreateProperty(g_Keyword_WavelengthBlue, std::to_string((long double)d["color_channel_center_wavelengths"]["b"].GetDouble()).c_str(), MM::String, true);
		}

		// Return
		return DEVICE_OK;
	}
	else
		return DEVICE_ERR;

}

int LedArray::ReadLedPositions()
{

	// Initialize array
	led_positions_cartesian = new double *[led_count];
	for (uint16_t i = 0; i < led_count; i++)
	{
		led_positions_cartesian[i] = new double[3];
		led_positions_cartesian[i][0] = 0.0;
		led_positions_cartesian[i][1] = 0.0;
		led_positions_cartesian[i][2] = 0.0;
	}

	uint16_t led_positions_read = 0;
	uint16_t led_batch_size = 10;
	uint16_t led_batch_count = (uint16_t)ceil((double)led_count / (double)led_batch_size);
	for (uint16_t batch_index = 0; batch_index < led_batch_count; batch_index++)
	{
		// Send command
		char buffer[20];
		sprintf(buffer, "pledpos.%d.%d", batch_index * led_batch_size, (batch_index + 1) * led_batch_size);
		SendCommand(buffer, true);

		// Parse response as json
		rapidjson::Document d;
		d.Parse(_serial_answer.c_str());

		if (d.IsObject())
		{
			// Parse LED indicies
			for (uint16_t led_index = batch_index * led_batch_size; led_index < min((batch_index + 1) * led_batch_size, led_count); led_index++)
			{
				led_positions_cartesian[led_index][0] = d["led_position_list_cartesian"][std::to_string((long long)led_index).c_str()][0].GetFloat();
				led_positions_cartesian[led_index][1] = d["led_position_list_cartesian"][std::to_string((long long)led_index).c_str()][1].GetFloat();
				led_positions_cartesian[led_index][2] = d["led_position_list_cartesian"][std::to_string((long long)led_index).c_str()][2].GetFloat();
				led_positions_read += 1;
			}
		}
	}

	// Set Property to json positions
	std::string json_str;
	json_str += std::string("{\"led_position_list_cartesian\": {");
	for (uint16_t i = 0; i < led_count; i++)
	{
		// Set tag to LED number
		json_str += std::string("\"");
		json_str += std::to_string((long long)i);
		json_str += std::string("\": {");

		// Add x
		json_str += std::string("\"x\":");
		json_str += std::to_string((long double)led_positions_cartesian[i][0]);
		json_str += std::string(",");

		// Add y
		json_str += std::string("\"y\":");
		json_str += std::to_string((long double)led_positions_cartesian[i][0]);
		json_str += std::string(",");

		// Add z
		json_str += std::string("\"z\":");
		json_str += std::to_string((long double)led_positions_cartesian[i][0]);
		json_str += std::string("}");

		if (i < (led_count - 1))
			json_str += std::string(",");
	}

	// Get json
	json_str += std::string("}");

	// Set json string to property
	SetProperty(g_Keyword_LedPositions, json_str.c_str());

	return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_Keyword_DeviceName, MM::SLMDevice, "LED Array");
	RegisterDevice(g_Keyword_DeviceNameVirtualShutter, MM::ShutterDevice, "Virtual shutter device that only turns on LEDs when it is open");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_Keyword_DeviceName) == 0)
	{
		return new LedArray;
	} else if (strcmp(deviceName, g_Keyword_DeviceNameVirtualShutter) == 0) {
	return new LedArrayVirtualShutter;
	}

	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// LedArray implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

LedArray::LedArray() : initialized_(false), name_(g_Keyword_DeviceName), pixels_(0), width_(1), height_(581),
shutterOpen_(true), color_r(63), color_g(63), color_b(63), objective_numerical_aperture(0.7), 
annulus_numerical_aperture(0.7), annulus_width(0.2), _pattern_orientation(g_Orientation_Top),
array_distance_z(50), led_count(0)
{
	portAvailable_ = false;

	// Initialize default error messages
	InitializeDefaultErrorMessages();

	////pre initialization property: port name
	CPropertyAction* pAct = new CPropertyAction(this, &LedArray::OnPort);
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);


}

LedArray::~LedArray()
{
	delete[] pixels_;
	Shutdown();
}

bool LedArray::Busy()
{
	return false;
}

void LedArray::GetName(char* name) const
{
	CDeviceUtils::CopyLimitedString(name, g_Keyword_DeviceName);
}

int LedArray::Shutdown()
{
	if (initialized_)
	{
		initialized_ = false;
	}
	return DEVICE_OK;
}

//SLM functions
int LedArray::SetImage(unsigned char * pixels) {
	memcpy(pixels_, pixels, width_*height_);
	return DEVICE_OK;
}

/**
* Command the SLM to display the loaded image.
*/
int LedArray::DisplayImage() {
	std::string command = std::string("l.");
	std::string index_str;
	for (int i = 0; i < height_; i++) {   // It was assumed that pixels_ are unsigned char. However, for the current LED Array, it would make sense if pixels_ was a bool to begin with.
		if (pixels_[i] != 0)
		{
			// Append this command
			command += std::to_string(static_cast <long long> (i + 1));   // Given the assumption mentioned above, it is further assumed that ' ' means that the corresponding pixel is off.

																		  // Append deimeter
			if (i < (height_ - 1))
				command += std::string(".");

		}
	}

	// Send command
	SendCommand(command.c_str(), true);

	return DEVICE_OK;
}

int LedArray::SetBrightness(long brightness)
{
	// Initialize Command
	std::string command("sb.");

	// Append Red
	command += std::to_string((long long)brightness);

	// Send Command
	SendCommand(command.c_str(), true);

	return DEVICE_OK;
}

int LedArray::UpdateColor(long redint, long greenint, long blueint)
{
	// Initialize Command
	std::string command("sc.");

	// Append Red
	command += std::to_string((long long)redint);
	command += std::string(".");

	// Append Green
	command += std::to_string((long long)greenint);
	command += std::string(".");

	// Append Blue
	command += std::to_string((long long)blueint);

	// Send Command
	SendCommand(command.c_str(), true);

	return DEVICE_OK;
}

/**
* Command the SLM to display one 8-bit intensity.
*/
int LedArray::SetPixelsTo(unsigned char intensity)
{
	lastModVal_ = intensity;
	return DEVICE_OK;
}

int LedArray::Reset()
{
	// Send reset command
	SendCommand("reset", true);

	// Return
	return DEVICE_OK;
}

int LedArray::DrawLedList(const char * led_list_char)
{
	// Generate command
	std::string command("l.");

	// Replace commas with periods
	std::string led_list_string(led_list_char);
	std::replace(led_list_string.begin(), led_list_string.end(), ',', '.');

	// Append LED list
	command += led_list_string;

	// Send Command
	SendCommand(command.c_str(), true);

	// Return
	return DEVICE_OK;
}

int LedArray::SetArrayDistance(double distMM)
{
	// Set Numerical Aperture
	std::string command("sad.");
	command += std::to_string((long long)(distMM));

	// Send Command
	SendCommand(command.c_str(), true);

	// Return
	return DEVICE_OK;
}


int LedArray::SetNumericalAperture(double numerical_aperture)
{
	// Set Numerical Aperture
	std::string command("na.");
	command += std::to_string((long long)(numerical_aperture * 100.0));


	// Send Command
	SendCommand(command.c_str(), true);

	//update displayed
	UpdatePattern();

	return DEVICE_OK;
}

int LedArray::DrawDpc(std::string type)
{

	// Initialize Command
	std::string command("dpc.");

	// Append orientation
	if (type == g_Orientation_Top)
		command += std::string("t");
	else if (type == g_Orientation_Bottom)
		command += std::string("b");
	else if (type == g_Orientation_Left)
		command += std::string("l");
	else if (type == g_Orientation_Right)
		command += std::string("r");


	// Send Command
	SendCommand(command.c_str(), true);

	// Return
	return DEVICE_OK;
}

int LedArray::DrawAnnulus(double min_na, double max_na)
{

	// Initialize Command
	std::string command("an.");

	// Append Min NA
	command += std::to_string((long long)(min_na * 100.0));
	command += std::string(".");

	// Append Max NA
	command += std::to_string((long long)(max_na * 100.0));

	// Send Command
	SendCommand(command.c_str(), true);

	// Return
	return DEVICE_OK;
}

int LedArray::DrawHalfAnnulus(std::string type, double min_na, double max_na)
{

	// Initialize Command
	std::string command("ha.");

	// Append orientation
	if (type == g_Orientation_Top)
		command += std::string("t");
	else if (type == g_Orientation_Bottom)
		command += std::string("b");
	else if (type == g_Orientation_Left)
		command += std::string("l");
	else if (type == g_Orientation_Right)
		command += std::string("r");

	command += std::string(".");

	// Append Min NA
	command += std::to_string((long long)(min_na * 100.0));
	command += std::string(".");

	// Append Max NA
	command += std::to_string((long long)(max_na * 100.0));

	// Send Command
	SendCommand(command.c_str(), true);

	// Return
	return DEVICE_OK;
}

int LedArray::Clear()
{
	// Send Command
	SendCommand("x", true);

	// Return
	return DEVICE_OK;
}

int LedArray::UpdatePattern() {
	//master function that gets called to send commands to LED array.
	//Waits on response from serial port so that call blocks until pattern
	//is corectly shown
	if (!shutterOpen_) {
		Clear();
	}
	else if (_pattern == g_Pattern_Brightfield) {
		UpdateColor(color_r, color_g, color_b);
		SendCommand("bf", true);
	}
	else if (_pattern == g_Pattern_Darkfield) {
		UpdateColor(color_r, color_g, color_b);
		SendCommand("df", true);
	}
	else if (_pattern == g_Pattern_Dpc) {
		UpdateColor(color_r, color_g, color_b);
		DrawDpc(_pattern_orientation);
	}
	else if (strcmp(_pattern.c_str(), g_Pattern_ColorDpc) == 0)
	{
		UpdateColor(color_r, color_g, color_b);
		SendCommand("cdpc", true);
	}
	else if (strcmp(_pattern.c_str(), g_Pattern_ColorDarkfield) == 0)
	{
		UpdateColor(color_r, color_g, color_b);
		SendCommand("cdf", true);
	}
	else if (_pattern == g_Pattern_ManualLedIndices) {
		if (_led_indicies.size() > 0) {
			UpdateColor(color_r, color_g, color_b);
			DrawLedList(_led_indicies.c_str());
		}
	}
	else if (_pattern == g_Pattern_Annulus) {
		UpdateColor(color_r, color_g, color_b);
		DrawAnnulus(annulus_numerical_aperture, annulus_width + annulus_numerical_aperture);
	}
	else if (_pattern == g_Pattern_HalfAnnulus) {
		UpdateColor(color_r, color_g, color_b);
		DrawHalfAnnulus(_pattern_orientation, annulus_numerical_aperture, annulus_width + annulus_numerical_aperture);
	}
	else if (_pattern == g_Pattern_CenterLed) {
		UpdateColor(color_r, color_g, color_b);
		SendCommand("l.0", true);
	}
	else {
		Clear();
	}
	return 	ReadResponse();
}

int LedArray::ReadResponse() {
	//try to read from serial port
	unsigned char response[1];
	unsigned long read = 0;
	int error = ReadFromComPort(port_.c_str(), response, 1, read);
	if (error == 0) {
		return DEVICE_OK;
	}

	return DEVICE_ERR;
}

int LedArray::SetShutter(boolean open)
{
	//TODO maybe update property?
	shutterOpen_ = open;
	return UpdatePattern();
}

bool LedArray::GetShutter()
{	
	return (shutterOpen_ != 0);
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int LedArray::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(port_.c_str());
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(port_);
		portAvailable_ = true;
	}
	return DEVICE_OK;
}

int LedArray::OnReset(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set("0");
	}
	else if (pAct == MM::AfterSet)
	{
		Reset();
	}
	return DEVICE_OK;
}

int LedArray::OnShutterOpen(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(shutterOpen_);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(shutterOpen_);
		return UpdatePattern();
	}
	return DEVICE_OK;
}

//Pattern functions:
int LedArray::OnPattern(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(_pattern.c_str());
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(_pattern);
		return UpdatePattern();
	}
	return DEVICE_OK;
}


int LedArray::OnSetManualLedList(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet) {
		pProp->Set(_led_indicies.c_str());
		//SetProperty(g_Keyword_Pattern, g_Pattern_ManualLedIndices);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(_led_indicies);
		//SetProperty(g_Keyword_Pattern, g_Pattern_ManualLedIndices);
	}

	return DEVICE_OK;
}


int LedArray::OnCommand(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(_command.c_str());
	}
	else if (pAct == MM::AfterSet)
	{
		// Get command string
		pProp->Get(_command);

		// Append terminator
		_command += "\n";

		// Purge COM Port
		PurgeComPort(port_.c_str());

		// Send command
		WriteToComPort(port_.c_str(), (unsigned char *)_command.c_str(), (unsigned int)_command.length());

		// Get Answer
		std::string answer;
		GetSerialAnswer(port_.c_str(), "-==-", answer);

		// Set property
		SetProperty(g_Keyword_Response, answer.c_str());
		//SetProperty(g_Keyword_Response, std::to_string((long long)answer.length()).c_str());
	}

	// Return
	return DEVICE_OK;
}

int LedArray::OnAnnulusWidth(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(annulus_width);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(annulus_width);
		return UpdatePattern();
	}
	return DEVICE_OK;
}

int LedArray::OnBrightness(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(brightness);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(brightness);
		SetBrightness(brightness);
	}
	return DEVICE_OK;
}

int LedArray::OnColorBalanceRed(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(color_r);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(color_r);
		UpdateColor(color_r, color_g, color_b);
	}
	return DEVICE_OK;
}

int LedArray::OnColorBalanceBlue(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(color_b);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(color_b);
		UpdateColor(color_r, color_g, color_b);
	}
	return DEVICE_OK;
}

int LedArray::OnColorBalanceGreen(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(color_g);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(color_g);
		UpdateColor(color_r, color_g, color_b);
	}
	return DEVICE_OK;
}

int LedArray::OnDistance(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(array_distance_z);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(array_distance_z);
		SetArrayDistance(array_distance_z);
		UpdatePattern();
	}
	return DEVICE_OK;
}

int LedArray::OnObjectiveAperture(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(objective_numerical_aperture);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(objective_numerical_aperture);
		SetNumericalAperture(objective_numerical_aperture);
		UpdatePattern();
	}
	return DEVICE_OK;
}

int LedArray::OnPatternAperture(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(annulus_numerical_aperture);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(annulus_numerical_aperture);
		UpdatePattern();
	}
	return DEVICE_OK;
}

int LedArray::OnPatternOrientation(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(_pattern_orientation.c_str());
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(_pattern_orientation);

		UpdatePattern();
	}
	return DEVICE_OK;
}

int LedArray::OnWidth(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(width_);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(width_);
		delete[] pixels_;
		pixels_ = new unsigned char[width_*height_];
	}
	return DEVICE_OK;
}

int LedArray::OnHeight(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(height_);
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(height_);
		delete[] pixels_;
		pixels_ = new unsigned char[width_*height_];
	}
	return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
//LedArrayVirtualShutter Implementation
///////////////////////////////////////////////////////////////////////////////
// VShutter control implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


LedArrayVirtualShutter::LedArrayVirtualShutter() : initialized_(false), ledArrayName_(g_Keyword_DeviceName) {
	//call the base class method to set-up default error codes/messages
	InitializeDefaultErrorMessages();

	//name of LED array to control
	CPropertyAction* pActg = new CPropertyAction(this, &LedArrayVirtualShutter::OnDeviceName);
	CreateProperty(g_Keyword_LEDArrayNameForShutter, "", MM::String, false, pActg, true);

}

int LedArrayVirtualShutter::Initialize()
{
if (initialized_)
return DEVICE_OK;

//Get ref to LED array
ledArray_ = (LedArray*) GetCoreCallback()->GetDevice(this, ledArrayName_.c_str());

 //set property list

 //Name
int ret = CreateProperty(MM::g_Keyword_Name, g_Keyword_DeviceNameVirtualShutter, MM::String, false);
if (DEVICE_OK != ret)
	return ret;

 //Description
ret = CreateProperty(MM::g_Keyword_Description, "Virtual dual shutter for turning LED Array on and off", MM::String, true);
if (DEVICE_OK != ret)
	return ret;

 //synchronize all properties
 --------------------------
ret = UpdateStatus();
if (ret != DEVICE_OK)
return ret;

initialized_ = true;
return DEVICE_OK;
}


LedArrayVirtualShutter::~LedArrayVirtualShutter()
{
Shutdown();
}

int LedArrayVirtualShutter::OnDeviceName(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
	{
		pProp->Set(ledArrayName_.c_str());
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(ledArrayName_);

	}
	return DEVICE_OK;
}


void LedArrayVirtualShutter::GetName(char* name) const {
CDeviceUtils::CopyLimitedString(name, g_Keyword_DeviceNameVirtualShutter);
}

int LedArrayVirtualShutter::SetOpen(bool open) {

//return GetCoreCallback()->SetDeviceProperty(ledArrayName_.c_str(), g_Keyword_Shutter, open ? "1" : "0");
	return ledArray_->SetShutter(open);
}


int LedArrayVirtualShutter::GetOpen(bool& open) {
	open = ledArray_->GetShutter();/*
	char isopen[1];
int ret = GetCoreCallback()->GetDeviceProperty(ledArrayName_.c_str(), g_Keyword_Shutter, isopen);
if (ret != DEVICE_OK)
	return ret;
open = strcmp(isopen, "0");*/
return DEVICE_OK;
}




