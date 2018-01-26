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
//
//

#include "LEDArray.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <cstdio>
#include <cstring>
#include <string>


#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>
   #define snprintf _snprintf 
#endif


	const char* g_DeviceNameLEDArray = "LED-Array";
	const char* g_DeviceNameLEDArrayVirtualShutter = "LED-Array-Vitrual-Shutter";

	const char * g_Keyword_Width = "Width";
	const char * g_Keyword_Height = "Height";
	const char * g_Keyword_Red = "Intensity: Red";  //Global intensity with a maximum of 65535
	const char * g_Keyword_Green = "Intensity: Green";  //Global intensity with a maximum of 65535
	const char * g_Keyword_Blue = "Intensity: Blue";  //Global intensity with a maximum of 65535
	const char * g_Keyword_SingleLED = "Single LED"; // Lighting single LED
	const char * g_Keyword_MultipleLEDs = "Multiple LEDs"; // Lighting multiple LEDs
	const char * g_Keyword_NumericalAp = "Numerical Aperture"; // Setting the numerical aperture
	const char * g_Keyword_ArrayDistMM = "Distance from aray to objective (mm)"; 
	const char * g_Keyword_Pattern = "Illumination pattern";
	const char * g_Keyword_type = "Pattern type"; //Pattern type: top, bottom, left, right
	const char * g_Keyword_minna = "Minimum NA"; 
	const char * g_Keyword_maxna = "Maximum NA";
	const char * g_Keyword_LEDlist = "Manual LED Indices";
	const char * g_Keyword_Reset = "Reset";
	const char * g_Keyword_Shutter = "ShutterOpen";

	const char * g_Keyword_LEDArrayName = "LEDArrayDeviceName";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceNameLEDArray, MM::SLMDevice, "LED Array");
   RegisterDevice(g_DeviceNameLEDArrayVirtualShutter, MM::ShutterDevice, "LED Array Virtual shutter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNameLEDArray) == 0)
   {
      return new CLEDArray;
   } else if (strcmp(deviceName, g_DeviceNameLEDArrayVirtualShutter) == 0) {
	  return new CLEDArrayVirtualShutter;
   }
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}
///////////////////////////////////////////////////////////////////////////////
//CLEDArrayVirtualShutter Implementation
///////////////////////////////////////////////////////////////////////////////
// VShutter control implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
 * Constructor.
 */
CLEDArrayVirtualShutter::CLEDArrayVirtualShutter() : initialized_(false) {
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

}

CLEDArrayVirtualShutter::~CLEDArrayVirtualShutter()
{
   Shutdown();
}

/**
 * Obtains device name.
 */
void CLEDArrayVirtualShutter::GetName(char* name) const {
   CDeviceUtils::CopyLimitedString(name, g_DeviceNameLEDArrayVirtualShutter);
}

/**
 * Intializes the hardware.
 */
int CLEDArrayVirtualShutter::Initialize()
{
   if (initialized_)
      return DEVICE_OK;


   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameLEDArrayVirtualShutter, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Virtual dual shutter for turning LED Array on and off", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // name of LED array to control
   ret = CreateProperty(g_Keyword_LEDArrayName, "", MM::String, false);
   assert(ret == DEVICE_OK);

   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int CLEDArrayVirtualShutter::SetOpen(bool open) {
   char arrayname[MM::MaxStrLength];
   GetProperty(g_Keyword_LEDArrayName, arrayname);

   if (strlen(arrayname) > 0)
   {
	  GetCoreCallback()->SetDeviceProperty(arrayname, g_Keyword_Shutter, open ? "1" : "0");
   }

   return DEVICE_OK;
}


int CLEDArrayVirtualShutter::GetOpen(bool& open) {
	char arrayname[MM::MaxStrLength];
    GetProperty(g_Keyword_LEDArrayName, arrayname);
	char isopen[MM::MaxStrLength];
	GetCoreCallback()->GetDeviceProperty(arrayname, g_Keyword_Shutter, isopen);
	open = strcmp(isopen, "0");

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// CLEDArray implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~

CLEDArray::CLEDArray() : initialized_(false), name_(g_DeviceNameLEDArray), pixels_(0), width_(1), height_(581),
	shutterOpen_(true), red_(600), green_(400), blue_(200), numa_(0.7), minna_(0.2), maxna_(0.6), type_("Top"), distMM_(50)
{
   portAvailable_ = false;

   InitializeDefaultErrorMessages();
 
   ////pre initialization property: port name
   CPropertyAction* pAct = new CPropertyAction(this, &CLEDArray::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
   //Height
   CPropertyAction* pAct4 = new CPropertyAction(this, &CLEDArray::OnHeight);
   CreateProperty(g_Keyword_Height, "1", MM::Integer, false, pAct4,true);
   //Width
   CPropertyAction* pAct5 = new CPropertyAction(this, &CLEDArray::OnWidth);
   CreateProperty(g_Keyword_Width, "1", MM::Integer, false, pAct5,true);
}

CLEDArray::~CLEDArray()
{
	delete[] pixels_;
   Shutdown();
}

bool CLEDArray::Busy() 
{
	return false;
}

void CLEDArray::GetName(char* name) const 
{
CDeviceUtils::CopyLimitedString(name, g_DeviceNameLEDArray);
}

int CLEDArray::Initialize()
{
   if (initialized_)
     return DEVICE_OK;
   pixels_ = new unsigned char[width_*height_];
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameLEDArray, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "LED Array", MM::String, true);
   assert(DEVICE_OK == ret);

   //shutter
   CPropertyAction* pActshutter = new CPropertyAction(this, &CLEDArray::OnShutterOpen);
   ret = CreateProperty(g_Keyword_Shutter,"0",MM::Integer,false, pActshutter );
   AddAllowedValue(g_Keyword_Shutter,"0");
   AddAllowedValue(g_Keyword_Shutter,"1");

   //reset
   CPropertyAction* pActreset = new CPropertyAction(this, &CLEDArray::OnReset);
   ret = CreateProperty(g_Keyword_Reset,"0",MM::String,false,pActreset );
   AddAllowedValue(g_Keyword_Reset,"0");
   AddAllowedValue(g_Keyword_Reset,"1");

   //Color Intensities:
	//Red:
   CPropertyAction* pActr = new CPropertyAction(this, &CLEDArray::OnRed);
   CreateProperty(g_Keyword_Red, "600", MM::Float, false, pActr);
   SetPropertyLimits(g_Keyword_Red, 0.0, 65535);
   
    //Green:
   CPropertyAction* pActg = new CPropertyAction(this, &CLEDArray::OnGreen);
   CreateProperty(g_Keyword_Green, "200", MM::Float, false, pActg);
   SetPropertyLimits(g_Keyword_Green, 0.0, 65535);
    //Blue:
   CPropertyAction* pActb = new CPropertyAction(this, &CLEDArray::OnBlue);
   CreateProperty(g_Keyword_Blue, "400", MM::Float, false, pActb);
   SetPropertyLimits(g_Keyword_Blue, 0.0, 65535);

   //Set Numerical Aperture:
   CPropertyAction* pActap = new CPropertyAction(this, &CLEDArray::OnAperture);
   CreateProperty(g_Keyword_NumericalAp, "0.7", MM::Float, false, pActap);

   //Set Array Dist:
   CPropertyAction* pActap2 = new CPropertyAction(this, &CLEDArray::OnDistance);
   CreateProperty(g_Keyword_ArrayDistMM, "50", MM::Float, false, pActap2);

   //Illumination Pattern:
   CPropertyAction* pActpat = new CPropertyAction(this, &CLEDArray::OnPattern);
   CreateProperty(g_Keyword_Pattern, "", MM::String, false, pActpat);
   AddAllowedValue(g_Keyword_Pattern, "Bright Field");
   AddAllowedValue(g_Keyword_Pattern, "Dark Field");
   AddAllowedValue(g_Keyword_Pattern, "DPC");
   AddAllowedValue(g_Keyword_Pattern, "Colored DPC");
   AddAllowedValue(g_Keyword_Pattern, "Manual LED Indices");
   AddAllowedValue(g_Keyword_Pattern, "Annulus");
   AddAllowedValue(g_Keyword_Pattern, "Half Annulus");
   AddAllowedValue(g_Keyword_Pattern, "Off");

   CPropertyAction* pActype = new CPropertyAction(this, &CLEDArray::OnType);
   CreateProperty(g_Keyword_type, "Top", MM::String, false, pActype);
   AddAllowedValue(g_Keyword_type, "Top");
   AddAllowedValue(g_Keyword_type, "Bottom");
   AddAllowedValue(g_Keyword_type, "Left");
   AddAllowedValue(g_Keyword_type, "Right");

   //Min and Max NA for annuli:
   CPropertyAction* pActmin = new CPropertyAction(this, &CLEDArray::OnMinNA);
   CreateProperty(g_Keyword_minna, "0.2", MM::Float, false, pActmin);

   CPropertyAction* pActmax = new CPropertyAction(this, &CLEDArray::OnMaxNA);
   CreateProperty(g_Keyword_maxna, "0.6", MM::Float, false, pActmax);

   //LED indices illumination:
   CPropertyAction* pActled = new CPropertyAction(this, &CLEDArray::OnLED);
   CreateProperty(g_Keyword_LEDlist, "", MM::String, false, pActled);

   // Check that we have a controller:
   PurgeComPort(port_.c_str());

	Reset();

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
   initialized_ = true;

   return DEVICE_OK;
}

int CLEDArray::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

//SLM functions
   int CLEDArray::SetImage(unsigned char * pixels) {
	   memcpy(pixels_ , pixels, width_*height_);
	  return DEVICE_OK;
   }

      /**
       * Command the SLM to display the loaded image.
       */
   int CLEDArray::DisplayImage() {
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

	   CLEDArray::MLED(indices);
	   return DEVICE_OK;
   }

   int CLEDArray::ColorUpdate(long redint, long greenint, long blueint)
   {
	    PurgeComPort(port_.c_str());

		int i, j, u, size;
	    unsigned char allData[100], Red[6], Green[6], Blue[6];
		std::string red = std::to_string(static_cast <long long> (redint));
		std::string green = std::to_string(static_cast <long long> (greenint));
		std::string blue = std::to_string(static_cast <long long> (blueint));

		std::copy(red.begin(),red.end(),Red);
		std::copy(green.begin(),green.end(),Green);
		std::copy(blue.begin(),blue.end(),Blue);
		allData[0] = 's';
		allData[1] = 'c';
		allData[2] = ',';

		for(i = 0; i < red.size();i++){
			allData[3 + i] = Red[i];
		}

		allData[3+i] = ',';

		for(j = 0; j < green.size();j++){
			allData[4 + i + j] = Green[j];
		}

		allData[4+i+j] = ',';

		for(u = 0; u < blue.size();u++){
			allData[5 + i + j + u] = Blue[u];
		}
		size = 5 + i + j + u;
		allData[size] = 10;
		size++;

		int ret =  WriteToComPort(port_.c_str(), allData, size); //Writing to port
		if (ret != DEVICE_OK){
			PurgeComPort(port_.c_str());
			return DEVICE_ERR;
		}
   }

      /**
       * Command the SLM to display one 8-bit intensity.
       */
int CLEDArray::SetPixelsTo(unsigned char intensity) {
	lastModVal_ = intensity;
	return DEVICE_OK;
}
int CLEDArray::Reset() {
	//reset LED arraya
	unsigned char allData[6];

	allData[0] = 'r';
	allData[1] = 'e';
	allData[2] = 's';
	allData[3] = 'e';
	allData[4] = 't';
	allData[5] = 10;

	int ret =  WriteToComPort(port_.c_str(), allData, 6); //Writing to port
	if (ret != DEVICE_OK){
		PurgeComPort(port_.c_str());
		return DEVICE_ERR;
	}
	return ret;
}

//Lighting a single LED:
int CLEDArray::SLED(std::string index){
	PurgeComPort(port_.c_str());
	int i, size; //size is the size of the sent command
	unsigned char allData[100], Indices[2];

	std::copy(index.begin(),index.end(),Indices);

	allData[0] = 'l';
	allData[1] = ',';

	for(i = 0; i < index.size();i++){
		allData[2 + i] = Indices[i];
	}
	size = 2 + i;
	allData[size] = 10;
	size++;

	int ret =  WriteToComPort(port_.c_str(), allData, size); //Writing to port
	if (ret != DEVICE_OK){
		PurgeComPort(port_.c_str());
		return DEVICE_ERR;
	}
} 
//Lighting multiple LEDs:

int CLEDArray::MLED(std::string indices){
	PurgeComPort(port_.c_str());
	int i, size;
	unsigned char allData[100], Indices[40];

	std::copy(indices.begin(),indices.end(),Indices);
	
	if (indices.size() == 1){
		allData[0] = 'l';
		allData[1] = ',';

		for(i = 0; i < indices.size();i++){
			allData[2 + i] = Indices[i];
		}
		size = 2 + i;
		allData[size] = 10;
		size++;
	}
	else{
		allData[0] = 'l';
		allData[1] = 'l';
		allData[2] = ',';

		for(i = 0; i < indices.size();i++){
			if(Indices[i] == ' '){
				allData[3 + i] = ',';
			}
			else{
				allData[3 + i] = Indices[i];
			}
		}
		size = 3 + i;
		allData[size] = 10;
		size++;
	}
	int ret =  WriteToComPort(port_.c_str(), allData, size); //Writing to port
	if (ret != DEVICE_OK){
		PurgeComPort(port_.c_str());
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}


int CLEDArray::ArrayDist(double distMM){
	PurgeComPort(port_.c_str());
	int i, size, DIST[8];
	int distMM100 = 100*distMM; 
	unsigned char allData[100];
	    
	std::string distString = std::to_string(static_cast <long long> (distMM100));
	std::copy(distString.begin(),distString.end(),DIST);
	allData[0] = 's';
	allData[1] = 'a';
	allData[2] = 'd';
	allData[3] = ',';

	for(i = 0; i < distString.size();i++){
		allData[4 + i] = DIST[i];
	}
	size = 4 + i;
	allData[size] = 10;
	size++;

	int ret =  WriteToComPort(port_.c_str(), allData, size); //Writing to port
	if (ret != DEVICE_OK){
		PurgeComPort(port_.c_str());
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int CLEDArray::NumA(double numa){
	PurgeComPort(port_.c_str());
	int i, size, na100 = 100*numa, NUMAP[8]; // na100 is 100*na which is the required format for the input command.
	unsigned char allData[100];
	    
	std::string numap = std::to_string(static_cast <long long> (na100));
	std::copy(numap.begin(),numap.end(),NUMAP);
	allData[0] = 'n';
	allData[1] = 'a';
	allData[2] = ',';

	for(i = 0; i < numap.size();i++){
		allData[3 + i] = NUMAP[i];
	}
	size = 3 + i;
	allData[size] = 10;
	size++;

	int ret =  WriteToComPort(port_.c_str(), allData, size); //Writing to port
	if (ret != DEVICE_OK){
		PurgeComPort(port_.c_str());
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int CLEDArray::DF(){
	PurgeComPort(port_.c_str());
	unsigned char allData[5];
	allData[0] = 'd';
	allData[1] = 'f';
	allData[2] = 10;

	int ret =  WriteToComPort(port_.c_str(), allData, 3); //Writing to port
	if (ret != DEVICE_OK){
		PurgeComPort(port_.c_str());
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int CLEDArray::BF(){
	PurgeComPort(port_.c_str());
	unsigned char allData[5];
	allData[0] = 'b';
	allData[1] = 'f';
	allData[2] = 10;

	int ret =  WriteToComPort(port_.c_str(), allData, 3);
	if (ret != DEVICE_OK){
		PurgeComPort(port_.c_str());
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int CLEDArray::DPC(std::string type){
	PurgeComPort(port_.c_str());
	unsigned char allData[10];
	allData[0] = 'd';
	allData[1] = 'p';
	allData[2] = 'c';
	allData[3] = ',';
	
	if(type == "Top"){
		allData[4] = 't';
	}
	else if(type == "Bottom"){
		allData[4] = 'b';
	}
	else if(type == "Left"){
		allData[4] = 'l';
	}
	else{
		allData[4] = 'r';
	}
	allData[5] = 10;

	int ret =  WriteToComPort(port_.c_str(), allData, 6);
	if (ret != DEVICE_OK){
		PurgeComPort(port_.c_str());
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int CLEDArray::CDPC(long redint, long greenint, long blueint){
	PurgeComPort(port_.c_str());
	unsigned char allData[40], Red[6], Green[6], Blue[6];
	int size, i, j, u;
	std::string red = std::to_string(static_cast <long long> (redint));
	std::string green = std::to_string(static_cast <long long> (greenint));
	std::string blue = std::to_string(static_cast <long long> (blueint));
	std::copy(red.begin(),red.end(),Red);
	std::copy(green.begin(),green.end(),Green);
	std::copy(blue.begin(),blue.end(),Blue);
	allData[0] = 'c';
	allData[1] = 'd';
	allData[2] = 'p';
	allData[3] = 'c';
	allData[4] = ',';
	
	for(i = 0; i < red.size();i++){
		allData[5 + i] = Red[i];
	}

	allData[5+i] = ',';

	for(j = 0; j < green.size();j++){
		allData[6 + i + j] = Green[j];
	}

	allData[6+i+j] = '.';

	for(u = 0; u < blue.size();u++){
		allData[7 + i + j + u] = Blue[u];
	}
	size = 7 + i + j + u;
	allData[size] = 10;
	size++;

	int ret =  WriteToComPort(port_.c_str(), allData, size);
	if (ret != DEVICE_OK){
		PurgeComPort(port_.c_str());
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int CLEDArray::Annul(double minna, double maxna){
	PurgeComPort(port_.c_str());
	int minint = 100*minna, maxint = 100*maxna;
	unsigned char allData[40], Minna[6], Maxna[6];
	int size, i, j;
	std::string min = std::to_string(static_cast <long long> (minint));
	std::string max = std::to_string(static_cast <long long> (maxint));
	std::copy(min.begin(),min.end(),Minna);
	std::copy(max.begin(),max.end(),Maxna);

	allData[0] = 'a';
	allData[1] = 'n';
	allData[2] = ',';

	for(i = 0; i < min.size();i++){
		allData[3 + i] = Minna[i];
	}

	allData[3+i] = ',';

	for(j = 0; j < max.size();j++){
		allData[4 + i + j] = Maxna[j];
	}

	size = 4 + i + j;
	allData[size] = 10;
	size++;

	int ret =  WriteToComPort(port_.c_str(), allData, size);
	if (ret != DEVICE_OK){
		PurgeComPort(port_.c_str());
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int CLEDArray::hAnnul(std::string type, double minna, double maxna){
	PurgeComPort(port_.c_str());
	int minint = 100*minna, maxint = 100*maxna;
	unsigned char allData[40], Minna[6], Maxna[6];
	int size, i, j;
	std::string min = std::to_string(static_cast <long long> (minint));
	std::string max = std::to_string(static_cast <long long> (maxint));
	std::copy(min.begin(),min.end(),Minna);
	std::copy(max.begin(),max.end(),Maxna);

	allData[0] = 'h';
	allData[1] = 'a';
	allData[2] = ',';

	if(type == "Top"){
		allData[3] = 't';
	}
	else if(type == "Bottom"){
		allData[3] = 'b';
	}
	else if(type == "Left"){
		allData[3] = 'l';
	}
	else{
		allData[3] = 'r';
	}
	allData[4] = ',';

	for(i = 0; i < min.size();i++){
		allData[5 + i] = Minna[i];
	}

	allData[5+i] = ',';

	for(j = 0; j < max.size();j++){
		allData[6 + i + j] = Maxna[j];
	}

	size = 6 + i + j;
	allData[size] = 10;
	size++;

	int ret =  WriteToComPort(port_.c_str(), allData, size);
	if (ret != DEVICE_OK){
		PurgeComPort(port_.c_str());
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int CLEDArray::Off(){
	PurgeComPort(port_.c_str());
	unsigned char allData[2];
	allData[0] = 'x';
	allData[1] = 10;
	int ret =  WriteToComPort(port_.c_str(), allData, 2);
	if (ret != DEVICE_OK){
		PurgeComPort(port_.c_str());
		return DEVICE_ERR;
	}
	return DEVICE_OK;
}

int CLEDArray::UpdatePattern(){
	//master function that gets called to send commands to LED array.
	//Waits on response from serial port so that call blocks until pattern
	//is corectly shown
	if (!shutterOpen_) {
		Off();
	} else if (pattern_ == "Bright Field"){
		  NumA(numa_);
		  ColorUpdate(red_,green_,blue_);
		  BF();
	  }
	  else if(pattern_ == "Dark Field"){
		  NumA(numa_);
		  ColorUpdate(red_,green_,blue_);
		  DF();
	  }
	  else if(pattern_ == "DPC"){
		  NumA(numa_);
		  ColorUpdate(red_,green_,blue_);
		  DPC(type_);
	  }
	  else if(pattern_ == "Colored DPC"){
		  NumA(numa_);
		  ColorUpdate(red_,green_,blue_);
		  CDPC(red_,green_,blue_);
	  }
	  else if(pattern_ == "Manual LED Indices"){
		  if (indices_.size() > 0){
			ColorUpdate(red_,green_,blue_);
			MLED(indices_);
		  }
	  }
	  else if(pattern_ == "Annulus"){
		  ColorUpdate(red_,green_,blue_);
		  Annul(minna_, maxna_);
	  }
	  else if(pattern_ == "Half Annulus"){
		  ColorUpdate(red_,green_,blue_);
		  hAnnul(type_,minna_,maxna_);
	  }
	  else{
		  Off();
	  }
	return 	ReadResponse();
}

int CLEDArray::ReadResponse(){
	  //try to read from serial port
	  unsigned char response[1];
	  unsigned long read = 0;
	  int error  = ReadFromComPort(port_.c_str(),response,1, read);
	  if (error==0) {
		  return DEVICE_OK;
	  }

	  return DEVICE_ERR;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int CLEDArray::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
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

int CLEDArray::OnReset(MM::PropertyBase* pProp, MM::ActionType pAct)
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

int CLEDArray::OnShutterOpen(MM::PropertyBase* pProp, MM::ActionType pAct)
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
int CLEDArray::OnPattern(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(pattern_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(pattern_);  
	  return UpdatePattern();
   }
   return DEVICE_OK;
}

int CLEDArray::OnMinNA(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
   {
      pProp->Set(minna_);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(minna_);
	  return UpdatePattern();
   }
   return DEVICE_OK;
}

int CLEDArray::OnLED(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet) {
      pProp->Set(indices_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(indices_);
   }
   return DEVICE_OK;
}

int CLEDArray::OnMaxNA(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if (pAct == MM::BeforeGet)
   {
      pProp->Set(maxna_);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(maxna_);
   }
   return DEVICE_OK;
}

int CLEDArray::OnRed(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(red_);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(red_);
	  ColorUpdate(red_,green_,blue_);
   }
   return DEVICE_OK;
}
int CLEDArray::OnBlue(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(blue_);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(blue_);
	  ColorUpdate(red_,green_,blue_);
   }
   return DEVICE_OK;
}

int CLEDArray::OnGreen(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(green_);
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(green_);
	  ColorUpdate(red_,green_,blue_);
   }
   return DEVICE_OK;
}

int CLEDArray::OnDistance(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if(pAct == MM::BeforeGet)
	{
		pProp->Set(distMM_);
	}
	else if(pAct == MM::AfterSet)
	{
		pProp->Get(distMM_);
		ArrayDist(distMM_);
	}
    return DEVICE_OK;
}

int CLEDArray::OnAperture(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if(pAct == MM::BeforeGet)
	{
		pProp->Set(numa_);
	}
	else if(pAct == MM::AfterSet)
	{
		pProp->Get(numa_);
		NumA(numa_);
	}
    return DEVICE_OK;
}

int CLEDArray::OnType(MM::PropertyBase* pProp, MM::ActionType pAct)
{
	if(pAct == MM::BeforeGet)
	{
		pProp->Set(type_.c_str());
	}
	else if(pAct == MM::AfterSet)
	{
		pProp->Get(type_);
	}
    return DEVICE_OK;
}

int CLEDArray::OnWidth(MM::PropertyBase* pProp, MM::ActionType pAct)
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

int CLEDArray::OnHeight(MM::PropertyBase* pProp, MM::ActionType pAct)
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
