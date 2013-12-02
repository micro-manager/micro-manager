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

#include "Spectra.h"

#include "../../MMDevice/ModuleInterface.h"

#include <cstdlib> // atol()

using namespace std;


const char* g_LumencorController = "Lumencor";
const char* g_Aura = "Aura";
const char* g_Sola = "Sola";
const char* g_Spectra = "Spectra";
const char* g_SpectraX = "SpectraX";



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
 

///////////////////////////////////////////////////////////////////////////////
// Lumencor

Spectra::Spectra() :
   port_("Undefined"),
   open_(false),
   lightEngine_(Spectra_Type),
   enableMask_(0x7f),
   initialized_(false),
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

   // Light engine type
   pAct = new CPropertyAction(this,&Spectra::OnSetLE_Type);
   std::string LEType = "SetLE_Type";
   CreateProperty(LEType.c_str(), g_Spectra, MM::String, false, pAct, true);
   vector<string> LETypeStr;
   LETypeStr.push_back(g_Aura);
   LETypeStr.push_back(g_Sola);
   LETypeStr.push_back(g_Spectra);
   LETypeStr.push_back(g_SpectraX);
   SetAllowedValues(LEType.c_str(), LETypeStr);
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
                                              
   // get the version number
   pAct = new CPropertyAction(this,&Spectra::OnVersion);

   ret = InitLE();
   if (ret != DEVICE_OK)
      return ret;

   ret = GetVersion();
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            

   CreateProperty("Version", version_.c_str(), MM::String,true,pAct); 

   // switch all channels off on startup instead of querying which one is open
   SetProperty(MM::g_Keyword_State, "0");

   // All light engines appear to have White
   pAct = new CPropertyAction(this,&Spectra::OnWhiteEnable);
   CreateProperty("White_Enable",   "0", MM::Integer, false, pAct, false);
   SetPropertyLimits("White_Enable",   0, 1);

   pAct = new CPropertyAction(this,&Spectra::OnWhiteValue);
   CreateProperty("White_Level",   "100", MM::Integer, false, pAct, false);
   SetPropertyLimits("White_Level",   0, 100);

   if (lightEngine_ != Sola_Type) {
      //
      // Declare action functions for color Value changes
      //
      pAct = new CPropertyAction(this,&Spectra::OnRedValue);
      CreateProperty("Red_Level",    "100", MM::Integer, false, pAct, false);

      pAct = new CPropertyAction(this,&Spectra::OnGreenValue);
      CreateProperty("Green_Level",  "100", MM::Integer, false, pAct, false);

      pAct = new CPropertyAction(this,&Spectra::OnCyanValue);
      CreateProperty("Cyan_Level",   "100", MM::Integer, false, pAct, false);

      pAct = new CPropertyAction(this,&Spectra::OnVioletValue);
      CreateProperty("Violet_Level", "100", MM::Integer, false, pAct, false);

      if (lightEngine_ != Aura_Type) {
         pAct = new CPropertyAction(this,&Spectra::OnBlueValue);
         CreateProperty("Blue_Level",   "100", MM::Integer, false, pAct, false);

         pAct = new CPropertyAction(this,&Spectra::OnTealValue);
         CreateProperty("Teal_Level",   "100", MM::Integer, false, pAct, false);
      }

      //
      // Declare action functions for color Enable changes
      //
      pAct = new CPropertyAction(this,&Spectra::OnRedEnable);
      CreateProperty("Red_Enable",    "0", MM::Integer, false, pAct, false);

      pAct = new CPropertyAction(this,&Spectra::OnGreenEnable);
      CreateProperty("Green_Enable",  "0", MM::Integer, false, pAct, false);

      pAct = new CPropertyAction(this,&Spectra::OnCyanEnable);
      CreateProperty("Cyan_Enable",   "0", MM::Integer, false, pAct, false);

      pAct = new CPropertyAction(this,&Spectra::OnVioletEnable);
      CreateProperty("Violet_Enable", "0", MM::Integer, false, pAct, false);

      if (lightEngine_ != Aura_Type) {
         pAct = new CPropertyAction(this,&Spectra::OnBlueEnable);
         CreateProperty("Blue_Enable",   "0", MM::Integer, false, pAct, false);

         pAct = new CPropertyAction(this,&Spectra::OnTealEnable);
         CreateProperty("Teal_Enable",   "0", MM::Integer, false, pAct, false);
      }


      // Yellow Green Filter
      pAct = new CPropertyAction(this,&Spectra::OnYGFilterEnable);
      CreateProperty("YG_Filter", "0", MM::Integer, false, pAct, false);
      SetPropertyLimits("YG_Filter", 0, 1);

      // Color Value Limits
      SetPropertyLimits("Red_Level",    0, 100);
      SetPropertyLimits("Green_Level",  0, 100);
      SetPropertyLimits("Cyan_Level",   0, 100);
      SetPropertyLimits("Violet_Level", 0, 100);
      if (lightEngine_ != Aura_Type) {
         SetPropertyLimits("Blue_Level",   0, 100);
         SetPropertyLimits("Teal_Level",   0, 100);
      }

      SetPropertyLimits("Red_Enable",    0, 1);
      SetPropertyLimits("Green_Enable",  0, 1);
      SetPropertyLimits("Cyan_Enable",   0, 1);
      SetPropertyLimits("Violet_Enable", 0, 1);
      if (lightEngine_ != Aura_Type) {
         SetPropertyLimits("Blue_Enable",   0, 1);
         SetPropertyLimits("Teal_Enable",   0, 1);
      }
   }

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
   open = (pos != 0);
   return DEVICE_OK;                                                         
} 

/**
 * Here we set the shutter to open or close
 */
int Spectra::SetShutterPosition(bool open)                              
{   
   SendColorEnableCmd(SHUTTER, open, &enableMask_);
   open_ = open;
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
	if(lightEngine_ == Sola_Type)
	{
		ColorName = ALL;
	}
	switch(ColorName)
	{
		case  RED:
			DACSetupArray[3] = 0x08;
			DACSetupArray[1] = 0x18;
			break;
		case  GREEN:
			DACSetupArray[3] = 0x04;
			DACSetupArray[1] = 0x18;
			break;
		case  VIOLET:
			DACSetupArray[3] = 0x01;
			DACSetupArray[1] = 0x18;
			break;
		case  CYAN:
			DACSetupArray[3] = 0x02;
			DACSetupArray[1] = 0x18;
			break;
		case  BLUE:
			DACSetupArray[3] = 0x01;
			DACSetupArray[1] = 0x1A;
			break;
		case  TEAL:
			DACSetupArray[3] = 0x02;
			DACSetupArray[1] = 0x1A;
			break;
		case ALL:
		case WHITE:
			DACSetupArray[4] = (unsigned char) ((ColorValue >> 4) & 0x0F) | 0xF0;
			DACSetupArray[5] = (unsigned char) (ColorValue << 4) & 0xF0;
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
		DACSetupArray[4] = (unsigned char) ((ColorValue >> 4) & 0x0F) | 0xF0;
		DACSetupArray[5] = (unsigned char) (ColorValue << 4) & 0xF0;
		WriteToComPort(port_.c_str(),DACSetupArray, 7); // Write Event Data to device
	}
	// block/wait no acknowledge so just give it time                      
    // CDeviceUtils::SleepMs(200);
	return DEVICE_OK;  // debug only 
}



// *****************************************************************************
// Sends color Enable/Disable command to Lumencor LightEngine
//
// Assumes current state matches *enableMask; turns on or off (based on State)
// the color given by ColorName. Afterwards, sets *enableMask to match the new
// state. If the shutter is closed, do nothing. XXX BUG: Even if shutter is
// open, do nothing if close is requested (State == false). (Bug is currently
// inconsequential because this function is always called with ColorName ==
// SHUTTER (see below), except in the case of the SOLA, where ColorName = ALL
// is forced.)
//
// If ColorName equals SHUTTER, apply *enableMask to the device (if State ==
// true) or switch off all lines (if State == false), but do not modify
// *enableMask even in the latter case. XXX BUG: When shutter is closed and
// opening is requested (State == true), the command for closing is sent
// instead.
//
// A SOLA user has reported that closing the shutter works, suggesting that
// sending a 0xCF or 0x7F (not sure if both of these work; at least one or the
// other appears to work) can turn off the SOLA (not surprising).
// *****************************************************************************
int Spectra::SendColorEnableCmd(ColorNameT ColorName, bool State, unsigned char* enableMask)
{
	enum StateValue {OFF=0, ON=1};
	unsigned char DACSetupArray[]= "\x4F\x00\x50\x00";
	bool open; 
	int ret = GetOpen(open);
	if (ret == DEVICE_OK) // only set enable if shuttern 
	{
		if(lightEngine_ == Aura_Type)
		{
			if(ColorName == BLUE || ColorName == CYAN)
			{	
				return DEVICE_OK;  // we exit here as the Aura does not support these colors
			}
		}
		if(lightEngine_ == Sola_Type)
		{
         // XXX BUG: If ColorName is SHUTTER, we don't want the (intended)
         // behavior of ALL!
			 ColorName = ALL;
		}
		switch (ColorName)
		{	
			case  RED:
				if(State==ON && open)
					DACSetupArray[1] = *enableMask & 0x7E;
				else
					DACSetupArray[1] = *enableMask | 0x01;
				break;
			case  GREEN:
				if(State==ON && open)
					DACSetupArray[1] = *enableMask & 0x7D;
				else
					DACSetupArray[1] = *enableMask | 0x02;
				break;
			case  VIOLET:
				if(State==ON && open)
					DACSetupArray[1] = *enableMask & 0x77;
				else
					DACSetupArray[1] = *enableMask | 0x08;
				break;
			case  CYAN:
				if(State==ON && open)
					DACSetupArray[1] = *enableMask & 0x7B;
				else
					DACSetupArray[1] = *enableMask | 0x04;
				break;
			case  BLUE:
				if(State==ON && open)
					DACSetupArray[1] = *enableMask & 0x5F;
				else
					DACSetupArray[1] = *enableMask | 0x20;
				break;
			case  TEAL:
				if(State==ON && open)
					DACSetupArray[1] = *enableMask & 0x3F;
				else
					DACSetupArray[1] = *enableMask | 0x40;
				break;
			case  YGFILTER:
				if(State==ON && open)
					DACSetupArray[1] = *enableMask & 0x6F;
				else
					DACSetupArray[1] = *enableMask | 0x10;
				break;
			case ALL:
			case WHITE:
            // XXX BUG: Why is this checking for the TEAL bit? It is switching
            // only if TEAL is off...
				if(State==ON && open)
					DACSetupArray[1] = ((*enableMask & 0x40) == 0x40) ? 0x40 : 0x00;
				else
					DACSetupArray[1] = ((*enableMask & 0x40) == 0x40) ? 0x7F : 0xCF; // dont toggle YG filter if not needed
				break;
			case SHUTTER:
				if(State== ON && open)
				{
					DACSetupArray[1] = *enableMask;  // set enabled channels on
					// DACSetupArray[1] = 0x00 | (*enableMask & 0x10);  // Enable All Channels and YGFIlter if was on
				}
				else
				{
					if((*enableMask & 0x10) == 0x10){  //if YGFilter is disabled
						DACSetupArray[1] = 0x7F; // all off
					}
					else {
						DACSetupArray[1] = 0x6F; // all off except YGFilter so we dont toggle it
					}
				}
			default:
				break;		
		}
		if (lightEngine_ == Aura_Type)
		{
			// See Aura TTL IF Doc: Front Panel Control/DAC for more detail
			// DACSetupArray[1] = DACSetupArray[1] | 0x20; // Mask for Aura to be sure DACs are Enabled
			// Byte 1 bit 5 Selects either DAC or Pot control on the Aura so we want to set this
			// to a zero for DAC control.
			// Examples:
			// 4f 70 50  sets Pot intensity control and all channels ON.
			// 4F 50 50  sets DAC intensity control and all channels ON.
			DACSetupArray[1] = DACSetupArray[1] & 0x5F; // Mask for Aura to be sure DACs are Enabled 
		}

		if(ColorName != SHUTTER) // shutter is a unique case were we dont want to change our mask
		{
			*enableMask = DACSetupArray[1]; // Sets the Mask to current state
		}
      if ( (ColorName == SHUTTER) || open_)
      {
		   WriteToComPort(port_.c_str(),DACSetupArray, 3); // Write Event Data to device
      }

		// block/wait no acknowledge so just give it time                     
		// CDeviceUtils::SleepMs(200);
	}
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
    version_ = "092712"; // this is the software build date for now.. return real version when hardware supports it
    return DEVICE_OK;  // debug only 
}

// This function Inits the GPIO on the 
// and must be done any time the LE is powered off then on again
int Spectra::InitLE()
{
   // Initialization sequence:
   unsigned char GPIO0to3[] = { 0x57, 0x02, 0xff, 0x50 };
   unsigned char GPIO5to7[] = { 0x57, 0x03, 0xab, 0x50 };

   // Disable all colors:
   unsigned char DACCtl[] = { 0x4f, 0x7f, 0x50 };
   if(lightEngine_ == Aura_Type)
   {
      DACCtl[1] = 0x5f;  // set all off and Dac Control on if an Aura
   }

   int ret;
   ret = WriteToComPort(port_.c_str(), GPIO0to3, sizeof(GPIO0to3));
   if (ret != DEVICE_OK)
      return ret;
   ret = WriteToComPort(port_.c_str(), GPIO5to7, sizeof(GPIO5to7));
   if (ret != DEVICE_OK)
      return ret;
   ret = WriteToComPort(port_.c_str(), DACCtl, sizeof(DACCtl));
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
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
   enum statevalue {open = 1, closed = 0};
   if (eAct == MM::BeforeGet)
   {                                                                         
      // instead of relying on stored state we could actually query the device
      // pProp->Set((long)state_);
   }                                                                         
   else if (eAct == MM::AfterSet)
   {
      long pos;
	  pProp->Get(pos);
      int ret = SetShutterPosition(pos == open ? open : closed);
      if (ret != DEVICE_OK)
         return ret;
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
		  lightEngine_ = Aura_Type;
	  }

	  if(TypeStr == "Sola")
	  {
		  lightEngine_ = Sola_Type;
	  }

	  if(TypeStr == "Spectra")
	  {
		  lightEngine_ = Spectra_Type;
	  }

	  if(TypeStr == "SpectraX")
	  {
		  lightEngine_ = SpectraX_Type;
	  }
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
	if (eAct == MM::AfterSet)
	{
		long State;
		pProp->Get(State);
		if (State)
		{
			enableMask_ &= 0x7E;
		}
		else
		{
			enableMask_ = enableMask_ | 0x01;
		}
		SetShutterPosition(open_);
	}
	if (eAct == MM::BeforeGet)
	{
		if ( (enableMask_ & 0x01) > 0)
			pProp->Set(0L);
		else
			pProp->Set(1L);

	}
	return DEVICE_OK;
}

int Spectra::OnGreenEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		long State;
		pProp->Get(State);
		if (State)
		{
			enableMask_ &= 0x7D;
		}
		else
		{
			enableMask_ |= 0x02;;
		}
		SetShutterPosition(open_);
	}
	if (eAct == MM::BeforeGet)
	{
		if ( (enableMask_ & 0x02) > 0)
			pProp->Set(0L);
		else
			pProp->Set(1L);
	}
	return DEVICE_OK;
}


int Spectra::OnCyanEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		long State;
		pProp->Get(State);
		if (State)
		{
			enableMask_ &= 0x7B;
		}
		else
		{
			enableMask_ |= 0x04;
		}
		SetShutterPosition(open_);
	}
	if (eAct == MM::BeforeGet)
	{
		if ( (enableMask_ & 0x04) > 0)
			pProp->Set(0L);
		else
			pProp->Set(1L);
	}


	return DEVICE_OK;
}


int Spectra::OnVioletEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		long State;
		pProp->Get(State);
		if (State)
		{
			enableMask_ &= 0x77;
		}
		else
		{
			enableMask_ |= 0x08;
		}
		SetShutterPosition(open_);
	}
	if (eAct == MM::BeforeGet) 
	{
		if ( (enableMask_ & 0x08) > 0)
			pProp->Set(0L);
		else
			pProp->Set(1L);
	}

	return DEVICE_OK;
}

int Spectra::OnTealEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		long State;
		pProp->Get(State);
		if (State)
		{
			enableMask_ &= 0x3F;
		}
		else
		{
			enableMask_ |= 0x40;
		}
		SetShutterPosition(open_);
	}
	if (eAct == MM::BeforeGet) 
	{
		if ( (enableMask_ & 0x40) > 0)
			pProp->Set(0L);
		else
			pProp->Set(1L);
	}
	return DEVICE_OK;
}

int Spectra::OnBlueEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		long State;
		pProp->Get(State);
		if (State)
		{
			enableMask_ &= 0x5F;
		}
		else
		{
			enableMask_ |= 0x20;
		}
		SetShutterPosition(open_);
	}
	if (eAct == MM::BeforeGet)
	{	
		if ( (enableMask_ & 0x20) > 0)
			pProp->Set(0L);
		else
			pProp->Set(1L);
	}
	return DEVICE_OK;
}

int Spectra::OnWhiteEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		long State;
		pProp->Get(State);
		if (State)
		{
			enableMask_ &= 0x10;
		}
		else
		{
			enableMask_ |= 0x6f;
		}
		SetShutterPosition(open_);
	}
	if (eAct == MM::BeforeGet)
	{
		if (enableMask_ & 0x6f)
			pProp->Set(0L);
		else
			pProp->Set(1L);
	}
	return DEVICE_OK;
}


int Spectra::OnYGFilterEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		long State;
		pProp->Get(State);
		if (State)
		{
			enableMask_ &= 0x6F;
		}
		else
		{
			enableMask_ |= 0x10;
		}
		SetShutterPosition(open_);
	}
	if (eAct == MM::BeforeGet)
	{	
		if ( (enableMask_ & 0x10) > 0)
			pProp->Set(0L);
		else
			pProp->Set(1L);
	}

	return DEVICE_OK;
}

