///////////////////////////////////////////////////////////////////////////////
// FILE:          Spectra.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Lumencore Light Engine driver
//                Spectra
//
// AUTHOR:        Louis Ashford (adapted from sample code provided for MicroManager)
// COPYRIGHT:     Ashford Solutions LLC
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
   #include <iostream>

#endif

#include "Spectra.h"
#include <string>
#include <math.h>

#include "../../MMDevice/ModuleInterface.h"

#include <sstream>

const char* g_LumencorController = "Lumencor";
const char* g_Spectra =		"Spectra";
const char* g_Channel_1 =	"1";
const char* g_Channel_2 =	"2";
const char* g_Channel_3 =	"3";

char EnableMask = 0x7f;
enum LEType {Aura_Type,Sola_Type,Spectra_Type,SpectraX_Type};
LEType LightEngine = Spectra_Type; // Light Engine Type
using namespace std;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_Spectra, "Lumencor Spectra Light Engine");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_Spectra) == 0)
   {
      Spectra* s = new Spectra();
      return s;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

// General utility function:
int ClearPort(MM::Device& device, MM::Core& core, std::string port)
{
   // Clear contents of serial port 
   const int bufSize = 255;
   unsigned char clear[bufSize];                      
   unsigned long read = bufSize;
   int ret;                                                                   
   while (read == (unsigned) bufSize) 
   {                                                                     
      ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read);
      if (ret != DEVICE_OK)                               
         return ret;                                               
   }
   return DEVICE_OK;                                                           
} 
 
/****************************************************************************
* DBGprintf
*
* This debugging function prints out a string to the debug output.
* An optional set of substitutional parameters can be specified,
* and the final output will be the processed result of these combined
* with the format string, just like printf.  A newline is always
* output after every call to this function.
*
* Arguments:
*   LPTSTR fmt - Format string (printf style).
*   ...        - Variable number of arguments.
* Returns:
*    VOID
\****************************************************************************/

#ifdef Win32
void DbgPrintf(LPTSTR fmt,...    )
{
    va_list marker;
    char szBuf[256];
 
    va_start(marker, fmt);
    //wvsprintf(szBuf, fmt, marker);
    //vswprintf(szBuf, fmt, marker);
	vsprintf(szBuf, fmt, marker);
	va_end(marker);
 
    //OutputDebugString(szBuf);
    //OutputDebugString(TEXT("\r\n"));
}
#endif
///////////////////////////////////////////////////////////////////////////////
// Lumencor

Spectra::Spectra() :
   port_("Undefined"),
   state_(0),
   initialized_(false),
   activeChannel_(g_Channel_1),
   version_("Undefined")
{
   InitializeDefaultErrorMessages();
                                                                             
   // create pre-initialization properties                                   
   // ------------------------------------
   //
                                                                          
   // Name                                                                   
   CreateProperty(MM::g_Keyword_Name, g_Spectra, MM::String, true);
   //
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Lumencor Spectra Light Engine", MM::String, true);
                                                                             
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &Spectra::OnPort);      
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);       
}                                                                            
                                                                             
Spectra::~Spectra()                                                            
{                                                                            
   Shutdown();                                                               
} 

void Spectra::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_Spectra);
}  

int Spectra::Initialize()
{
   if (initialized_)
      return DEVICE_OK;
      
   // set property list
   // -----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Spectra::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;                                                            
                                                                             
   AddAllowedValue(MM::g_Keyword_State, "0");                                
   AddAllowedValue(MM::g_Keyword_State, "1");                                
                                              
   // The Channel we will act on
   // ----
   pAct = new CPropertyAction (this, &Spectra::OnChannel);
   ret=CreateProperty("Channel", g_Channel_1, MM::String, false, pAct);  

   vector<string> commands;                                                  
   commands.push_back(g_Channel_1);                                           
   commands.push_back(g_Channel_2);                                            
   commands.push_back(g_Channel_3);                                         
   ret = SetAllowedValues("Channel", commands);        
   if (ret != DEVICE_OK)                                                    
      return ret;

   // get the version number
   pAct = new CPropertyAction(this,&Spectra::OnVersion);

   // If GetVersion fails we are not talking to the Lumencor
   ret = GetVersion();
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            

   ret = CreateProperty("Version", version_.c_str(), MM::String,true,pAct); 

   // switch all channels off on startup instead of querying which one is open
   SetProperty(MM::g_Keyword_State, "0");

   pAct = new CPropertyAction(this,&Spectra::OnSetLE_Type);
   CreateProperty("SetLE_Type",    "Spectra", MM::String, false, pAct, false);
      vector<string> LETypeStr;
		LETypeStr.push_back("Aura");
		LETypeStr.push_back("Sola");
		LETypeStr.push_back("Spectra");
		LETypeStr.push_back("SpectraX");
		ret = SetAllowedValues("SetLE_Type", LETypeStr);
		assert(ret == DEVICE_OK);

   pAct = new CPropertyAction(this,&Spectra::OnInitLE);
   CreateProperty("Init_LE",    "0", MM::Integer, false, pAct, false);
   //
   // Declare action function for color Value changes
   //
   pAct = new CPropertyAction(this,&Spectra::OnRedValue);
   CreateProperty("Red_Level",    "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&Spectra::OnGreenValue);
   CreateProperty("Green_Level",  "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&Spectra::OnCyanValue);
   CreateProperty("Cyan_Level",   "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&Spectra::OnVioletValue);
   CreateProperty("Violet_Level", "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&Spectra::OnTealValue);
   CreateProperty("Teal_Level",   "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&Spectra::OnBlueValue);
   CreateProperty("Blue_Level",   "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&Spectra::OnWhiteValue);
   CreateProperty("White_Level",   "0", MM::Integer, false, pAct, false);
   //
   // Declare action function for color Enable changes
   //
   pAct = new CPropertyAction(this,&Spectra::OnRedEnable);
   CreateProperty("Red_Enable",    "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&Spectra::OnGreenEnable);
   CreateProperty("Green_Enable",  "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&Spectra::OnCyanEnable);
   CreateProperty("Cyan_Enable",   "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&Spectra::OnVioletEnable);
   CreateProperty("Violet_Enable", "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&Spectra::OnTealEnable);
   CreateProperty("Teal_Enable",   "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&Spectra::OnBlueEnable);
   CreateProperty("Blue_Enable",   "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&Spectra::OnWhiteEnable);
   CreateProperty("White_Enable",   "0", MM::Integer, false, pAct, false);

   // Yellow Green Filter
   pAct = new CPropertyAction(this,&Spectra::OnYGFilterEnable);
   CreateProperty("YG_Filter", "0", MM::Integer, false, pAct, false);
   SetPropertyLimits("YG_Filter", 0, 1);

   // Color Value Limits
   SetPropertyLimits("Red_Level",    0, 100);
   SetPropertyLimits("Green_Level",  0, 100);
   SetPropertyLimits("Cyan_Level",   0, 100);
   SetPropertyLimits("Violet_Level", 0, 100);
   SetPropertyLimits("Teal_Level",   0, 100);
   SetPropertyLimits("Blue_Level",   0, 100);
   SetPropertyLimits("White_Level",   0, 100);
	 
   //
   SetPropertyLimits("Init_LE",       0, 1);
   //
   SetPropertyLimits("Red_Enable",    0, 1);
   SetPropertyLimits("Green_Enable",  0, 1);
   SetPropertyLimits("Cyan_Enable",   0, 1);
   SetPropertyLimits("Violet_Enable", 0, 1);
   SetPropertyLimits("Teal_Enable",   0, 1);
   SetPropertyLimits("Blue_Enable",   0, 1);
   SetPropertyLimits("White_Enable",   0, 1);
   //

   ret = UpdateStatus();

   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
                                                                             
   initialized_ = true;                                                      
   return DEVICE_OK;                                                         
}  

int Spectra::SetOpen(bool open)
{  
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
} 

int Spectra::GetOpen(bool& open)
{     
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
   long pos = atol(buf);                                                     
   pos == 1 ? open = true : open = false;                                    
   return DEVICE_OK;                                                         
} 

/**
 * Here we set the shutter to open or close
 */
int Spectra::SetShutterPosition(bool state)                              
{   
	enum statevalue {open = 1, closed = 0};
	if(state == open)
		SendColorEnableCmd(SHUTTER, true, &EnableMask);  // If on then Set
	else
		SendColorEnableCmd(SHUTTER, false, &EnableMask);  // close
    return DEVICE_OK;
}

// *****************************************************************************
// Sends color level command to Lumencor LightEngine
// *****************************************************************************
int Spectra::SendColorLevelCmd(ColorNameT ColorName,int ColorLevel)
{
	unsigned int ColorValue;
	unsigned char DACSetupArray[]= "\x53\x18\x03\x00\x00\x00\x50\x00";
	std::string Cmd;

	ColorValue = 255-(unsigned int)(2.55 * ColorLevel); // map color to range
	ColorValue &= 0xFF;  // Mask to one byte
	ColorValue = (ColorLevel == 100) ? 0 : ColorValue;
	ColorValue = (ColorLevel == 0) ? 0xFF : ColorValue;  // coherce to correct values at limits
	if(LightEngine == Sola_Type)
	{
		ColorName = ALL;
	}
	switch(ColorName)
	{
		case  RED:
			DACSetupArray[3] = 0x08;
			break;
		case  GREEN:
			DACSetupArray[3] = 0x04;
			break;
		case  VIOLET:
			DACSetupArray[3] = 0x01;
			break;
		case  CYAN:
			DACSetupArray[3] = 0x02;
			break;
		case  BLUE:
			DACSetupArray[3] = 0x01;
			break;
		case  TEAL:
			DACSetupArray[3] = 0x02;
			break;
		case ALL:
		case WHITE:
			DACSetupArray[4] = (char) ((ColorValue >> 4) & 0x0F) | 0xF0;
			DACSetupArray[5] = (char) (ColorValue << 4) & 0xF0;
			DACSetupArray[3] = 0x0F; // setup for RGCV 
			DACSetupArray[1] = 0x18;
			WriteToComPort(port_.c_str(),DACSetupArray, 7); // Write Event Data to device

			DACSetupArray[3] = 0x03; // BT
			DACSetupArray[1] = 0x1A;
			WriteToComPort(port_.c_str(),DACSetupArray, 7); // Write Event Data to device
			break;
		default:
			break;		
	}
	if(ColorName != ALL && ColorName != WHITE)
	{
		DACSetupArray[4] = (char) ((ColorValue >> 4) & 0x0F) | 0xF0;
		DACSetupArray[5] = (char) (ColorValue << 4) & 0xF0;
		WriteToComPort(port_.c_str(),DACSetupArray, 7); // Write Event Data to device
	}
	// block/wait no acknowledge so just give it time                      
    // CDeviceUtils::SleepMs(200);
	return DEVICE_OK;  // debug only 
}



// *****************************************************************************
// Sends color Enable/Disable command to Lumencor LightEngine
// *****************************************************************************
int Spectra::SendColorEnableCmd(ColorNameT ColorName,bool State, char* EnableMask)
{
	enum StateValue {OFF=0, ON=1};
	unsigned char DACSetupArray[]= "\x4F\x00\x50\x00";
	if(LightEngine == Sola_Type)
	{
		 ColorName = ALL;
	}
	switch (ColorName)
	{	
		case  RED:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x7E;
			else
				DACSetupArray[1] = *EnableMask | 0x01;
			break;
		case  GREEN:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x7D;
			else
				DACSetupArray[1] = *EnableMask | 0x02;
			break;
		case  VIOLET:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x77;
			else
				DACSetupArray[1] = *EnableMask | 0x08;
			break;
		case  CYAN:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x7B;
			else
				DACSetupArray[1] = *EnableMask | 0x04;
			break;
		case  BLUE:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x5F;
			else
				DACSetupArray[1] = *EnableMask | 0x20;
			break;
		case  TEAL:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x3F;
			else
				DACSetupArray[1] = *EnableMask | 0x40;
			break;
		case  YGFILTER:
			if(State==ON)
				DACSetupArray[1] = *EnableMask & 0x6F;
			else
				DACSetupArray[1] = *EnableMask | 0x10;
			break;
		case ALL:
		case WHITE:
			if(State==ON)
			{
				DACSetupArray[1] = ((*EnableMask & 0x40) == 0x40) ? 0x40 : 0x00;
			}
			else
			    DACSetupArray[1] = ((*EnableMask & 0x40) == 0x40) ? 0x7F : 0xCF; // dont toggle YG filter if not needed
			break;
		case SHUTTER:
			if(State== ON)
			{
				DACSetupArray[1] = *EnableMask;  // set enabled channels on
			}
			else
			{
				DACSetupArray[1] = 0x7F; // all off
			}
		default:
			break;		
	}
	if (LightEngine == Aura_Type)
	{
		// See Aura TTL IF Doc: Front Panel Control/DAC for more detail
		DACSetupArray[1] = DACSetupArray[1] | 0x20; // Mask for Aura to be sure DACs are Enabled 
	}

	if(ColorName != SHUTTER) // shutter is a unique case were we dont want to change our mask
	{
		*EnableMask = DACSetupArray[1]; // Sets the Mask to current state
	}

	WriteToComPort(port_.c_str(),DACSetupArray, 3); // Write Event Data to device

   // block/wait no acknowledge so just give it time                     
   // CDeviceUtils::SleepMs(200);
	return DEVICE_OK;  // debug only 
}

// Lumencor Initialization Info
// Initialization Command String for RS-232 Intensity and RS-232 OR TTL Enables:
// The first two commands MUST be issued after every power cycle
// to properly configure controls for further commands.
// 57 02 FF 50- Set GPIO0-3 as open drain output
// 57 03 AB 50- Set GPI05-7 push-pull out, GPIO4 open drain out
// 
// There is no Version command for the Lumencor products 
//
// 53 18 03 08 FF F0 50- Sets RED DAC to 0xFF
//
// Enables
// 4F 7E 50- Enables Red, Disables Green,Cyan,Blue,UV,Teal.
// 4F 7D 50- Enables Green, Disables Red,Cyan,Blue,UV,Teal.
// 4F 7B 50- Enables Cyan, Disables Red,Green,Blue,UV,Teal.
// 4F 5F 50- Enables Blue, Disables Red,Green,Cyan,UV,Teal.
// 4F 77 50- Enables UV, Disables Red,Green,Cyan,Blue,Teal.
// 4F 3F 50- Enables Teal, Disables Red,Green,Cyan,Blue,UV.
// 4F 7F 50- Disables All.
// 4F 5B 50- Enables Cyan and Blue, Disables all others.
// 4F 3E 50- Enables Red and Teal, Disables all others.
//

int Spectra::GetVersion()
{
     int ret;
	 ret = InitLE();
     version_ = "1234"; // this is a dummy value for now.. return real version when hardware supports it
     return DEVICE_OK;  // debug only 
}

// This function Inits the GPIO on the 
// and must be done any time the LE is powered off then on again
int Spectra::InitLE()
{
   int ret;
   char DACCtl[] = "\x4f\x7F\x50";  // Disable All Colors
   if(LightEngine == Aura_Type)
   {
		DACCtl[1] = 0x5f;  // set all off and Dac Control if an Aura
   }
   char GPIO0to3[] = "\x57\x02\xff\x50";
   char GPIO5to7[] = "\x57\x03\xAB\x50"; // Write to the uart register

   std::string cmd =  GPIO5to7;
   std::string cmd1 = GPIO0to3;	
   std::string cmd2 = DACCtl;
	// Send GPIO Commands
   ret = SendSerialCommand(port_.c_str(), cmd.c_str(), ""); // send command to write register
   ret = SendSerialCommand(port_.c_str(), cmd1.c_str(), "");  // Set GIPO 0-3
   ret = SendSerialCommand(port_.c_str(), cmd2.c_str(), "");  // setup for DAC control for Aura Only
   return DEVICE_OK;  // We dont get a response for these commands so just set to normal return 
}

int Spectra::Shutdown()                                                
{                                                                            
   if (initialized_)                                                         
   {                                                                         
      initialized_ = false;                                                  
   }                                                                         
   return DEVICE_OK;                                                         
}                                                                            

// Never busy because all commands block
bool Spectra::Busy()
{
   return false;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
/*
 * Sets the Serial Port to be used.
 * Should be called before initialization
 */
int Spectra::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
                                                                             
      pProp->Get(port_);                                                     
   }                                                                         
   return DEVICE_OK;     
}

int Spectra::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {                                                                         
      // instead of relying on stored state we could actually query the device
      pProp->Set((long)state_);                                                          
   }                                                                         
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);

      return SetShutterPosition(pos == 0 ? false : true);
   }
   return DEVICE_OK;
}

int Spectra::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(activeChannel_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      // if there is a channel change and the shutter was open, re-open in the new position
      std::string tmpChannel;
      pProp->Get(tmpChannel);
      if (tmpChannel != activeChannel_) {
         activeChannel_ = tmpChannel;
         if (state_ == 1)
            SetShutterPosition(true);
      }
      // It might be a good idea to close the shutter at this point...
   }
   return DEVICE_OK;
}

 
int Spectra::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet && version_ == "Undefined")
   {
      int ret = GetVersion();
      if (ret != DEVICE_OK) 
         return ret;
      pProp->Set(version_.c_str());
   }
   return DEVICE_OK;
}

int Spectra::OnSetLE_Type(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string TypeStr;

   pProp->Get(TypeStr);
   if (eAct == MM::AfterSet)
   {
	  if(TypeStr == "Aura")
	  {
		  LightEngine = Aura_Type;
	  }

	  if(TypeStr == "Sola")
	  {
		  LightEngine = Sola_Type;
	  }

	  if(TypeStr == "Spectra")
	  {
		  LightEngine = Spectra_Type;
	  }

	  if(TypeStr == "SpectraX")
	  {
		  LightEngine = SpectraX_Type;
	  }
   }
   return DEVICE_OK;
}

int Spectra::OnInitLE(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long State;
   pProp->Get(State); 
                                                                      
   if (eAct == MM::AfterSet && State == 1)
   {  
	  State = 0; 
      pProp->Set(State); // reset button
      InitLE();
   }
   return DEVICE_OK;
}
// *****************************************************************************
//                  Color Value Change Handlers
// *****************************************************************************

int Spectra::OnRedValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long ColorLevel;
   if (eAct == MM::AfterSet)
   {		
			pProp->Get(ColorLevel);
			SendColorLevelCmd(RED, ColorLevel);
   }
   return DEVICE_OK;
}

int Spectra::OnGreenValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long ColorLevel;
   if (eAct == MM::AfterSet)
   {
		pProp->Get(ColorLevel);
		SendColorLevelCmd(GREEN,ColorLevel);
   }
   return DEVICE_OK;
}

int Spectra::OnCyanValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long ColorLevel;
   if (eAct == MM::AfterSet)
   {
		pProp->Get(ColorLevel);
		SendColorLevelCmd(CYAN,ColorLevel);
   }
   return DEVICE_OK;
}

int Spectra::OnVioletValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long ColorLevel;
   if (eAct == MM::AfterSet)
   {
		pProp->Get(ColorLevel);
		SendColorLevelCmd(VIOLET,ColorLevel);
   }

   return DEVICE_OK;
}

int Spectra::OnTealValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long ColorLevel;
   if (eAct == MM::AfterSet)
   {
		pProp->Get(ColorLevel);
		SendColorLevelCmd(TEAL,ColorLevel);
   }
   return DEVICE_OK;
}

int Spectra::OnBlueValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long ColorLevel;
   if (eAct == MM::AfterSet)
   {
		pProp->Get(ColorLevel);
		SendColorLevelCmd(BLUE,ColorLevel);
   }
   return DEVICE_OK;
}

int Spectra::OnWhiteValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long ColorLevel;
   if (eAct == MM::AfterSet)
   {
		pProp->Get(ColorLevel);
		SendColorLevelCmd(WHITE,ColorLevel);
   }
   return DEVICE_OK;
}
//
// *****************************************************************************
//						Color Enable Change Handlers
// *****************************************************************************

int Spectra::OnRedEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    string State;
	if (eAct == MM::AfterSet)
   {
	    pProp->Get(State);
		if (State == "1")
		{
			SendColorEnableCmd(RED,true,&EnableMask);
		}
		else
		{
			SendColorEnableCmd(RED,false,&EnableMask);
		}
   }
   return DEVICE_OK;
}

int Spectra::OnGreenEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string State;
	if (eAct == MM::AfterSet)
    {
	    pProp->Get(State);
		if (State == "1")
		{
			SendColorEnableCmd(GREEN,true,&EnableMask);
		}
		else
		{
			SendColorEnableCmd(GREEN,false,&EnableMask);
		}
   }
   return DEVICE_OK;
}

int Spectra::OnCyanEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string State;
	if (eAct == MM::AfterSet)
    {
	    pProp->Get(State);
		if (State == "1")
		{
			SendColorEnableCmd(CYAN,true,&EnableMask);
		}
		else
		{
			SendColorEnableCmd(CYAN,false,&EnableMask);
		}
	}
   return DEVICE_OK;
}

int Spectra::OnVioletEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string State;
	if (eAct == MM::AfterSet)
    {
	    pProp->Get(State);
		if (State == "1")
		{
			SendColorEnableCmd(VIOLET,true,&EnableMask);
		}
		else
		{
			SendColorEnableCmd(VIOLET,false,&EnableMask);
		}
	}
   return DEVICE_OK;
}

int Spectra::OnTealEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string State;
	if (eAct == MM::AfterSet)
    {
	    pProp->Get(State);
		if (State == "1")
		{
			SendColorEnableCmd(TEAL,true,&EnableMask);
		}
		else
		{
			SendColorEnableCmd(TEAL,false,&EnableMask);
		}
	}
   return DEVICE_OK;
}

int Spectra::OnBlueEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string State;
	if (eAct == MM::AfterSet)
    {
	    pProp->Get(State);
		if (State == "1")
		{
			SendColorEnableCmd(BLUE,true,&EnableMask);
		}
		else
		{
			SendColorEnableCmd(BLUE,false,&EnableMask);
		}
	}
   return DEVICE_OK;
}

int Spectra::OnWhiteEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string State;
	if (eAct == MM::AfterSet)
    {
	    pProp->Get(State);
		if (State == "1")
		{
			SendColorEnableCmd(WHITE,true,&EnableMask);
		}
		else
		{
			SendColorEnableCmd(WHITE,false,&EnableMask);
		}
	}
   return DEVICE_OK;
}


int Spectra::OnYGFilterEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string State;
	if (eAct == MM::AfterSet)
    {
	    pProp->Get(State);
		if (State == "1")
		{
			SendColorEnableCmd(YGFILTER,true,&EnableMask);
		}
		else
		{
			SendColorEnableCmd(YGFILTER,false,&EnableMask);
		}
	}
   return DEVICE_OK;
}

