///////////////////////////////////////////////////////////////////////////////
// FILE:          TeensySLM.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Arduino adapter for sending serial commands as a property value.  Needs accompanying firmware
// COPYRIGHT:     University of California, Berkeley, 2016
// LICENSE:       LGPL
// 
// AUTHOR:        Henry Pinkard, hbp@berkeley.edu, 12/13/2016   
// AUTHOR:        Zack Phillips, zkphil@berkeley.edu, 3/1/2019
//
//

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

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif

const char* g_Keyword_DeviceName = "LED-Array";
const char* g_Keyword_DeviceNameVirtualShutter = "LED-Array-Vitrual-Shutter";

const char * g_Keyword_Red = "IntensityRed";  //Global intensity with a maximum of 65535
const char * g_Keyword_Green = "IntensityGreen";  //Global intensity with a maximum of 65535
const char * g_Keyword_Blue = "IntensityBlue";  //Global intensity with a maximum of 65535
const char * g_Keyword_NumericalAp = "NumericalAperture"; // Setting the numerical aperture
const char * g_Keyword_SetArrayDistanceMM = "ArrayDistance"; 
const char * g_Keyword_Pattern = "IlluminationPattern";
const char * g_Keyword_PatternOrientation = "IlluminationPatternOrientation";
const char * g_Keyword_AnnulusWidth = "AnnulusWidth"; 
const char * g_Keyword_LedList = "ArbitraryLedList";
const char * g_Keyword_Reset = "Reset";
const char * g_Keyword_Shutter = "ShutterOpen";

// Device Parameters
const char * g_Keyword_WavelengthRed = "CenterWavelengthRedMicrons";
const char * g_Keyword_WavelengthGreen = "CenterWavelengthGreenMicrons";
const char * g_Keyword_WavelengthBlue = "CenterWavelengthBlueMicrons";
const char * g_Keyword_LedCount = "LedCount";
const char * g_Keyword_PartNumber = "PartNumber";
const char * g_Keyword_SerialNumber = "SerialNumber";
const char * g_Keyword_MacAddress = "MacAddress";
const char * g_Keyword_TriggerInputCount = "TriggerInputCount";
const char * g_Keyword_TriggerOutputCount = "TriggerOutputCount";
const char * g_Keyword_NativeBitDepth = "NativeBitDepth";
const char * g_Keyword_LedArrayType = "Type";
const char * g_Keyword_InterfaceVersion = "InterfaceVersion";
const char * g_Keyword_ColorChannelCount = "ColorChannelCount";
const char * g_Keyword_LedPositions = "LedPositionsCartesian";

// Low-Level Serial IO
const char * g_Keyword_Response = "SerialResponse";
const char * g_Keyword_Command = "SerialCommand";

int LedArray::Initialize()
{
	if (initialized_)
		return DEVICE_OK;
	pixels_ = new unsigned char[width_*height_];

	// Name
	int ret = CreateProperty(MM::g_Keyword_Name, g_Keyword_DeviceName, MM::String, true);
	if (DEVICE_OK != ret)
		return ret;

	// Description
	ret = CreateProperty(MM::g_Keyword_Description, "LED Array", MM::String, true);
	assert(DEVICE_OK == ret);

	// Most Recent Serial Response
	ret = CreateProperty(g_Keyword_Response, "", MM::String, false);

	// Shutter
	CPropertyAction* pActshutter = new CPropertyAction(this, &LedArray::OnShutterOpen);
	ret = CreateProperty(g_Keyword_Shutter, "0", MM::Integer, false, pActshutter);
	AddAllowedValue(g_Keyword_Shutter, "0");
	AddAllowedValue(g_Keyword_Shutter, "1");

	// Reset
	CPropertyAction* pActreset = new CPropertyAction(this, &LedArray::OnReset);
	ret = CreateProperty(g_Keyword_Reset, "0", MM::String, false, pActreset);
	AddAllowedValue(g_Keyword_Reset, "0");
	AddAllowedValue(g_Keyword_Reset, "1");

	// Manual Command Interface
	CPropertyAction* pCommand = new CPropertyAction(this, &LedArray::OnCommand);
	ret = CreateProperty(g_Keyword_Command, "", MM::String, false, pCommand);

	// Illumination Pattern:
	CPropertyAction* pActpat = new CPropertyAction(this, &LedArray::OnPattern);
	CreateProperty(g_Keyword_Pattern, "None", MM::String, false, pActpat);
	AddAllowedValue(g_Keyword_Pattern, "None");
	AddAllowedValue(g_Keyword_Pattern, "Brightfield");
	AddAllowedValue(g_Keyword_Pattern, "Darkfield");
	AddAllowedValue(g_Keyword_Pattern, "DPC");
	AddAllowedValue(g_Keyword_Pattern, "Color DPC");
	AddAllowedValue(g_Keyword_Pattern, "Manual LED Indices");
	AddAllowedValue(g_Keyword_Pattern, "Annulus");
	AddAllowedValue(g_Keyword_Pattern, "Half Annulus");
	AddAllowedValue(g_Keyword_Pattern, "Center LED");
	AddAllowedValue(g_Keyword_Pattern, "Clear");

	// Illumination Pattern Orientation
	CPropertyAction* pActpatOrientation = new CPropertyAction(this, &LedArray::OnPatternOrientation);
	CreateProperty(g_Keyword_PatternOrientation, "Top", MM::String, false, pActpatOrientation);
	AddAllowedValue(g_Keyword_PatternOrientation, "Top");
	AddAllowedValue(g_Keyword_PatternOrientation, "Bottom");
	AddAllowedValue(g_Keyword_PatternOrientation, "Left");
	AddAllowedValue(g_Keyword_PatternOrientation, "Right");

	// Annulus Width
	CPropertyAction* pActAnnuluswidth = new CPropertyAction(this, &LedArray::OnAnnulusWidth);
	CreateProperty(g_Keyword_AnnulusWidth, "0.2", MM::Float, false, pActAnnuluswidth);

	// LED indices illumination:
	CPropertyAction* pActled = new CPropertyAction(this, &LedArray::OnLED);
	CreateProperty(g_Keyword_LedList, "0.1.2.3.4", MM::String, false, pActled);

	// Red Color Slider
	CPropertyAction* pActr = new CPropertyAction(this, &LedArray::OnRed);
	CreateProperty(g_Keyword_Red, std::to_string((long long)color_r).c_str(), MM::Float, false, pActr);
	SetPropertyLimits(g_Keyword_Red, 0, 255);

	// Green Color Slider
	CPropertyAction* pActg = new CPropertyAction(this, &LedArray::OnGreen);
	CreateProperty(g_Keyword_Green, std::to_string((long long)color_g).c_str(), MM::Float, false, pActg);
	SetPropertyLimits(g_Keyword_Green, 0, 255);

	// Blue Color Slider
	CPropertyAction* pActb = new CPropertyAction(this, &LedArray::OnBlue);
	CreateProperty(g_Keyword_Blue, std::to_string((long long)color_b).c_str(), MM::Float, false, pActb);
	SetPropertyLimits(g_Keyword_Blue, 0, 255);

	// NA Property
	CPropertyAction* pActap = new CPropertyAction(this, &LedArray::OnAperture);
	CreateProperty(g_Keyword_NumericalAp, std::to_string((long double)numerical_aperture).c_str(), MM::Float, false, pActap);

	// LED Array Distance
	CPropertyAction* pActap2 = new CPropertyAction(this, &LedArray::OnDistance);
	CreateProperty(g_Keyword_SetArrayDistanceMM, std::to_string((long double)array_distance_z).c_str(), MM::Float, false, pActap2);

	// LED Count
	CreateProperty(g_Keyword_LedCount, std::to_string((long long)led_count).c_str(), MM::Integer, true);

	// LED Positions
	//CPropertyAction* pActap2 = new CPropertyAction(this, &LedArray::OnDistance);
	CreateProperty(g_Keyword_LedPositions, "", MM::String, false);	

	// Check that we have a controller:
	PurgeComPort(port_.c_str());

	// Set the device to machine-readable mode
	SetMachineMode(true);

	// Get Device Parameters
	GetDeviceParameters();

	// Sync Current Parameters
	SyncCurrentParameters();

	// Get LED Positions
	SendCommand("pledpos.0.10", true);
	
	// Set Properties based on JSON output
	rapidjson::Document d;
	d.Parse(_serial_answer.c_str());
	assert(d.IsObject());    // Document is a JSON value represents the root of DOM. Root can be either an object or array.

	// Generate LED position array
	led_positions_cartesian = new double * [led_count];
	for (uint16_t i = 0; i < led_count; i++)
		led_positions_cartesian[i] = new double[3];

	//SetProperty(g_Keyword_LedPositions, std::to_string((long double) d["led_position_list_cartesian"]["0"].GetFloat()).c_str());

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
	WriteToComPort(port_.c_str(), &((unsigned char *)_command.c_str())[0], _command.length());

	// Get/check response if desired
	if (get_response)
		return GetResponse();
	else
		return DEVICE_OK;

	// Impose a small delay to prevent overloading buffer
	Sleep(SERIAL_DELAY_MS);
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

int LedArray::SyncCurrentParameters()
{

	// Get current NA
	SendCommand("na", true);
	std::string na_str("NA.");
	numerical_aperture = (float)atoi(_serial_answer.substr(_serial_answer.find(na_str) + na_str.length(), _serial_answer.length() - na_str.length()).c_str()) / 100.0;

	// Get current array distance
	SendCommand("sad", true);
	std::string sad_str("DZ.");
	array_distance_z = (float)atoi(_serial_answer.substr(_serial_answer.find(sad_str) + sad_str.length(), _serial_answer.length() - sad_str.length()).c_str());

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

	// Remove first value (the SC)
	color_values.erase(color_values.begin());

	// Assign current color values
	color_r = atof(color_values.at(0).c_str());
	color_g = atof(color_values.at(1).c_str());
	color_b = atof(color_values.at(2).c_str());

	// Red:
	SetProperty(g_Keyword_Red, std::to_string((long long)color_r).c_str());

	// Green:
	SetProperty(g_Keyword_Green, std::to_string((long long)color_g).c_str());

	// Blue:
	SetProperty(g_Keyword_Blue, std::to_string((long long)color_b).c_str());

	// Set Numerical Aperture:
	SetProperty(g_Keyword_NumericalAp, std::to_string((long double)numerical_aperture).c_str());

	//Set Array Dist:
	SetProperty(g_Keyword_SetArrayDistanceMM, std::to_string((long double)array_distance_z).c_str());
}

int LedArray::SetMachineMode(bool mode)
{
	// Check that we have a controller:
	PurgeComPort(port_.c_str());

	if (mode)
	{
		// Send command to device
		unsigned char myString[] = "machine\n";
		unsigned char* tmpBuffer = &myString[0];
		WriteToComPort(port_.c_str(), &myString[0], 8);
	}
	else {
		// Send command to device
		unsigned char myString[] = "human\n";
		unsigned char* tmpBuffer = &myString[0];
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
	assert(d.IsObject());    // Document is a JSON value represents the root of DOM. Root can be either an object or array.

	// Update Read-only property [TODO: Make work!]
	rapidjson::Value& s = d["device_name"];

	// Get properties from json and set as read-only properties
	if (d.HasMember("mac_address"))
		CreateProperty(g_Keyword_MacAddress, d["mac_address"].GetString(), MM::String, true);

	// Parse device name
	if (d.HasMember("device_name"))
		CreateProperty(g_Keyword_LedArrayType, d["device_name"].GetString(), MM::String, true);

	// Parse LED count
	if (d.HasMember("led_count"))
	{
		led_count = d["led_count"].GetUint64();
		SetProperty(g_Keyword_LedCount, std::to_string((long long)led_count).c_str());
	}

	// Parse trigger input count
	if (d.HasMember("trigger_input_count"))
		CreateProperty(g_Keyword_TriggerInputCount, std::to_string((long long)d["trigger_input_count"].GetUint64()).c_str(), MM::Integer, true);

	// Parse trigger output count
	if (d.HasMember("trigger_output_count"))
		CreateProperty(g_Keyword_TriggerOutputCount, std::to_string((long long)d["trigger_output_count"].GetUint64()).c_str(), MM::Integer, true);

	// Parse part number
	if (d.HasMember("part_number"))
		CreateProperty(g_Keyword_PartNumber, std::to_string((long long)d["part_number"].GetUint64()).c_str(), MM::Integer, true);

	// Parse serial number
	if (d.HasMember("serial_number"))
		CreateProperty(g_Keyword_SerialNumber, std::to_string((long long)d["serial_number"].GetUint64()).c_str(), MM::Integer, true);

	// Parse bit depth
	if (d.HasMember("bit_depth"))
		CreateProperty(g_Keyword_NativeBitDepth, std::to_string((long long)d["bit_depth"].GetUint64()).c_str(), MM::Integer, true);

	// Parse color channel count
	if (d.HasMember("color_channel_count"))
		CreateProperty(g_Keyword_ColorChannelCount, std::to_string((long long)d["color_channel_count"].GetUint64()).c_str(), MM::Integer, true);

	// Parse interface version
	if (d.HasMember("interface_version"))
		CreateProperty(g_Keyword_InterfaceVersion, std::to_string((long double)d["interface_version"].GetFloat()).c_str(), MM::Float, true);

	// Parse color channels
	if (d.HasMember("color_channel_center_wavelengths"))
	{
		// Parse red channel
		if (d["color_channel_center_wavelengths"].HasMember("r"))
			CreateProperty(g_Keyword_WavelengthRed, std::to_string((long double)d["color_channel_center_wavelengths"]["r"].GetDouble()).c_str(), MM::String, true);

		// Parse green channel
		if (d["color_channel_center_wavelengths"].HasMember("g"))
			CreateProperty(g_Keyword_WavelengthRed, std::to_string((long double)d["color_channel_center_wavelengths"]["g"].GetDouble()).c_str(), MM::String, true);

		// Parse blue channel
		if (d["color_channel_center_wavelengths"].HasMember("b"))
			CreateProperty(g_Keyword_WavelengthRed, std::to_string((long double)d["color_channel_center_wavelengths"]["b"].GetDouble()).c_str(), MM::String, true);
	}

	// Return
	return DEVICE_OK;
}


/**
 * Intializes the hardware.
 */
int LedArrayVirtualShutter::Initialize()
{
   if (initialized_)
      return DEVICE_OK;


   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_Keyword_DeviceNameVirtualShutter, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Virtual dual shutter for turning LED Array on and off", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // name of LED array to control
   ret = CreateProperty(g_Keyword_DeviceName, "", MM::String, false);
   assert(ret == DEVICE_OK);

   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_Keyword_DeviceName, MM::SLMDevice, "LED Array");
	RegisterDevice(g_Keyword_DeviceNameVirtualShutter, MM::ShutterDevice, "LED Array Virtual shutter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_Keyword_DeviceName) == 0)
	{
		return new LedArray;
	}
	else if (strcmp(deviceName, g_Keyword_DeviceNameVirtualShutter) == 0) {
		return new LedArrayVirtualShutter;
	}
	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}
///////////////////////////////////////////////////////////////////////////////
//LedArrayVirtualShutter Implementation
///////////////////////////////////////////////////////////////////////////////
// VShutter control implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
* Constructor.
*/
LedArrayVirtualShutter::LedArrayVirtualShutter() : initialized_(false) {
	// call the base class method to set-up default error codes/messages
	InitializeDefaultErrorMessages();

}

LedArrayVirtualShutter::~LedArrayVirtualShutter()
{
	Shutdown();
}

/**
* Obtains device name.
*/
void LedArrayVirtualShutter::GetName(char* name) const {
	CDeviceUtils::CopyLimitedString(name, g_Keyword_DeviceNameVirtualShutter);
}

int LedArrayVirtualShutter::SetOpen(bool open) {
   char arrayname[MM::MaxStrLength];
   GetProperty(g_Keyword_DeviceName, arrayname);

   if (strlen(arrayname) > 0)
   {
	  GetCoreCallback()->SetDeviceProperty(arrayname, g_Keyword_Shutter, open ? "1" : "0");
   }

   return DEVICE_OK;
}


int LedArrayVirtualShutter::GetOpen(bool& open) {
	char arrayname[MM::MaxStrLength];
    GetProperty(g_Keyword_DeviceName, arrayname);
	char isopen[MM::MaxStrLength];
	GetCoreCallback()->GetDeviceProperty(arrayname, g_Keyword_Shutter, isopen);
	open = strcmp(isopen, "0");

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// LedArray implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

LedArray::LedArray() : initialized_(false), name_(g_Keyword_DeviceName), pixels_(0), width_(1), height_(581),
	shutterOpen_(true), color_r(63), color_g(63), color_b(63), numerical_aperture(0.7),
	annulus_width(0.2), _pattern_orientation("Top"), array_distance_z(50), led_count(0)
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
	memcpy(pixels_ , pixels, width_*height_);
	return DEVICE_OK;
}

    /**
    * Command the SLM to display the loaded image.
    */
int LedArray::DisplayImage() {
	std::string indices;
	std::string index;
	int j = 0;
	for(int i = 0; i < height_; i++){   // It was assumed that pixels_ are unsigned char. However, for the current LED Array, it would make sense if pixels_ was a bool to begin with.
		if(pixels_[i] != 0){
			if (i < 10)
			{
				index = std::to_string(static_cast <long long> (i + 1));   // Given the assumption mentioned above, it is further assumed that ' ' means that the corresponding pixel is off.
				indices.insert(j, index);
				j++;
				indices.insert(j," ");
				j++;
			}
			else if( i < 100 && i > 10)
			{
				index = std::to_string(static_cast <long long> (i + 1));   // Given the assumption mentioned above, it is further assumed that ' ' means that the corresponding pixel is off.
				indices.insert(j, index);
				j = j + 2;
				indices.insert(j," ");
				j++;
			}
			else
			{
				index = std::to_string(static_cast <long long> (i + 1));   // Given the assumption mentioned above, it is further assumed that ' ' means that the corresponding pixel is off.
				indices.insert(j, index);
				j = j + 3;
				indices.insert(j," ");
				j++;
			}
		}
	}
	indices.erase(indices.end()-1);

	return DEVICE_OK;
}

int LedArray::UpdateColor(long redint, long greenint, long blueint)
{
	// Initialize Command
	std::string command("sc.");

	// Append Red
	command += std::to_string((long long) redint);
	command += std::string(".");

	// Append Green
	command += std::to_string((long long) greenint);
	command += std::string(".");

	// Append Blue
	command += std::to_string((long long) blueint);

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


int LedArray::SetNumericalAperture(double SetNumericalAperture)
{
	// Set Numerical Aperture
	std::string command("na.");
	command += std::to_string((long long)(numerical_aperture * 100.0));


	// Send Command
	SendCommand(command.c_str(), true);

	return DEVICE_OK;
}

int LedArray::DrawDpc(std::string type)
{

	// Initialize Command
	std::string command("dpc.");

	// Append orientation
	if (type == "Top")
		command += std::string("t");
	else if (type == "Bottom")
		command += std::string("b");
	else if (type == "Left")
		command += std::string("l");
	else
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
	if (type == "Top")
		command += std::string("t");
	else if (type == "Bottom")
		command += std::string("b");
	else if (type == "Left")
		command += std::string("l");
	else
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

int LedArray::UpdatePattern(){
	//master function that gets called to send commands to LED array.
	//Waits on response from serial port so that call blocks until pattern
	//is corectly shown
	if (!shutterOpen_) {
		Clear();
	} else if (_pattern == "Brightfield"){
		  SetNumericalAperture(numerical_aperture);
		  UpdateColor(color_r, color_g, color_b);
		  SendCommand("bf", true);
	  }
	  else if(_pattern == "Darkfield"){
		  SetNumericalAperture(numerical_aperture);
		  UpdateColor(color_r, color_g, color_b);
		  SendCommand("df", true);
	  }
	  else if(_pattern == "DPC"){
		  SetNumericalAperture(numerical_aperture);
		  UpdateColor(color_r, color_g, color_b);
		  DrawDpc(_pattern_orientation);
	  }
	  else if (strcmp(_pattern.c_str(), "Color DPC") == 0)
	  {
		  SetNumericalAperture(numerical_aperture);
		  UpdateColor(color_r, color_g, color_b);
		  SendCommand("cdpc", true);
	  }
	  else if(_pattern == "Manual LED Indices"){
		  if (_led_indicies.size() > 0){
			UpdateColor(color_r, color_g, color_b);
			DrawLedList(_led_indicies.c_str());
		  }
	  }
	  else if(_pattern == "Annulus"){
		  UpdateColor(color_r,color_g,color_b);
		  DrawAnnulus(numerical_aperture, annulus_width + numerical_aperture);
	  }
	  else if(_pattern == "Half Annulus"){
		  UpdateColor(color_r,color_g,color_b);
		  DrawHalfAnnulus(_pattern_orientation, numerical_aperture, annulus_width + numerical_aperture);
	  }
	  else if (_pattern == "Center LED") {
		  UpdateColor(color_r, color_g, color_b);
		  SendCommand("l.0", true);
	  }
	  else{
		  Clear();
	  }
	return 	ReadResponse();
	  return 0;
}

int LedArray::ReadResponse(){
	  //try to read from serial port
	  unsigned char response[1];
	  unsigned long read = 0;
	  int error  = ReadFromComPort(port_.c_str(), response, 1, read);
	  if (error==0) {
		  return DEVICE_OK;
	  }

	  return DEVICE_ERR;
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
      pProp->Set( shutterOpen_);
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


int LedArray::OnLED(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet) {
      pProp->Set(_led_indicies.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(_led_indicies);
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
		WriteToComPort(port_.c_str(), (unsigned char *)_command.c_str(), _command.length());

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

int LedArray::OnRed(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(color_r);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(color_r);
	  UpdateColor(color_r,color_g,color_b);
   }
   return DEVICE_OK;
}

int LedArray::OnBlue(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(color_b);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(color_b);
	  UpdateColor(color_r,color_g,color_b);
   }
   return DEVICE_OK;
}

int LedArray::OnGreen(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(color_g);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(color_g);
	  UpdateColor(color_r,color_g,color_b);
   }
   return DEVICE_OK;
}

int LedArray::OnDistance(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if(pAct == MM::BeforeGet)
	{
		pProp->Set(array_distance_z);
	}
	else if(pAct == MM::AfterSet)
	{
		pProp->Get(array_distance_z);
		SetArrayDistance(array_distance_z);
	}
    return DEVICE_OK;
}

int LedArray::OnAperture(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if(pAct == MM::BeforeGet)
	{
		pProp->Set(numerical_aperture);
	}
	else if(pAct == MM::AfterSet)
	{
		pProp->Get(numerical_aperture);
		SetNumericalAperture(numerical_aperture);
	}
    return DEVICE_OK;
}

int LedArray::OnPatternOrientation(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if(pAct == MM::BeforeGet)
	{
		pProp->Set(_pattern_orientation.c_str());
	}
	else if (pAct == MM::AfterSet)
	{
		pProp->Get(_pattern_orientation);

		// If we're currently displaying a DPC or half-annulus pattern, update the pattern
		if (_pattern == "DPC")
		{
			SetNumericalAperture(numerical_aperture);
			UpdateColor(color_r, color_g, color_b);
			DrawDpc(_pattern_orientation);
		}
		else if (_pattern == "Half Annulus")
		{
			UpdateColor(color_r, color_g, color_b);
			DrawHalfAnnulus(_pattern_orientation, numerical_aperture, annulus_width + numerical_aperture);
		}
	}
    return DEVICE_OK;
}

int LedArray::OnWidth(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set( width_);
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
	  pixels_  = new unsigned char[width_*height_];
   }
   return DEVICE_OK;
}

