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


// On/off control bits. On is low, off is high.
// Note that the Aura may have different colors installed, but the bits to
// control the available channels are (presumably) the same.
enum ControlBitPosition {
   BIT_RED = 0,
   BIT_GREEN, // Turning on turns off other channels
   BIT_CYAN,
   BIT_VIOLET,
   BIT_YG_FILTER,
   BIT_BLUE, // Different meaning in Aura
   BIT_AURA_DAC = BIT_BLUE, // Low activates intensity control from computer
   BIT_TEAL, // Not used for Aura (keep high)
   BIT_UNUSED, // Keep low for Aura and Spectra
};

inline unsigned char SetBitOn(unsigned char mask, ControlBitPosition bit)
{
   // Set the bit to low without changing other bits
   return mask & ~(1 << bit);
}

inline unsigned char SetBitOff(unsigned char mask, ControlBitPosition bit)
{
   // Set the bit to high without changing other bits
   return mask | (1 << bit);
}

inline unsigned char SetBit(unsigned char mask, ControlBitPosition bit, bool state)
{
   return state ? SetBitOn(mask, bit) : SetBitOff(mask, bit);
}

inline bool GetBitOn(unsigned char mask, ControlBitPosition bit)
{
   bool level = (mask & (1 << bit)) != 0;
   bool logic = !level;
   return logic;
}

// Return a mask where the shuttered bits are all high.
inline unsigned char AllOffMask(LEType leType)
{
   switch (leType)
   {
      case Aura_Type:
         return (1 << BIT_RED) | (1 << BIT_GREEN) | (1 << BIT_CYAN) | (1 << BIT_VIOLET);
      case Sola_Type:
         return 0xff;
      case Spectra_Type:
      case SpectraX_Type:
         // All except YG filter bit and unused high bit
         return 0xff & ~((1 << BIT_YG_FILTER) | (1 << BIT_UNUSED));
      default:
         // Unimplemented device; make no changes
         return 0x00;
   }
}

inline unsigned char SetAllOn(unsigned char mask, LEType leType)
{
   // Set the shuttered bits low.
   return mask & ~AllOffMask(leType);
}

inline unsigned char SetAllOff(unsigned char mask, LEType leType)
{
   // Set the shuttered bits high.
   return mask | AllOffMask(leType);
}

inline unsigned char SetAll(unsigned char mask, LEType leType, bool state)
{
   return state ? SetAllOn(mask, leType) : SetAllOff(mask, leType);
}

// Mask to set on initialization (turn everything off)
inline unsigned char InitialEnableMask(LEType leType)
{
   switch (leType)
   {
      case Aura_Type:
         // All off; turn on DAC.
         return (1 << BIT_RED) | (1 << BIT_GREEN) | (1 << BIT_CYAN) | (1 << BIT_VIOLET) |
            (1 << BIT_YG_FILTER) | (1 << BIT_TEAL);
      case Sola_Type:
         // Off.
         return 0xff;
      case Spectra_Type:
      case SpectraX_Type:
         // All off.
         return 0xff & ~(1 << BIT_UNUSED);
      default:
         // Unimplemented device; should not reach here.
         return 0x7f;
   }
}


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
   enableMask_(InitialEnableMask(lightEngine_)),
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

   int ret = InitLE();
   if (ret != DEVICE_OK)
      return ret;

   // set property list
   // -----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &Spectra::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;                                                            
                                                                             
   AddAllowedValue(MM::g_Keyword_State, "0");                                
   AddAllowedValue(MM::g_Keyword_State, "1");                                
                                              
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


int Spectra::SendColorEnableMask(unsigned char mask)
{
   unsigned char command[] = { 0x4f, mask, 0x50 };
   return WriteToComPort(port_.c_str(), command, sizeof(command));
}


int Spectra::SetOpen(bool open)
{
   if (open == open_)
      return DEVICE_OK;

   unsigned char newShutteredEnableMask;
   if (open)
      newShutteredEnableMask = enableMask_;
   else
      newShutteredEnableMask = SetAllOff(enableMask_, lightEngine_);

   int ret = SendColorEnableMask(newShutteredEnableMask);
   if (ret != DEVICE_OK)
      return ret;

   open_ = open;

   return DEVICE_OK;
} 

int Spectra::GetOpen(bool& open)
{
   open = open_;
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
		ColorName = WHITE;
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
	if (ColorName != WHITE)
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
// Assumes current state matches enableMask_; turns on or off (based on
// newState) the color given by colorName. Afterwards, sets enableMask_ to
// match the new state. If the shutter is open, or the switch is for the YG
// filter, send the new state to the device.
// *****************************************************************************
int Spectra::SetColorEnabled(ColorNameT colorName, bool newState)
{
   unsigned char previousEnableMask = enableMask_;

   unsigned char previousShutteredEnableMask;
   if (open_)
      previousShutteredEnableMask = previousEnableMask;
   else
      previousShutteredEnableMask = SetAllOff(previousEnableMask, lightEngine_);

   // The enableMask_ we will switch to (initialize to no change).
   unsigned char newEnableMask = previousEnableMask;

   if (colorName == WHITE)
   {
      newEnableMask = SetAll(previousEnableMask, lightEngine_, newState);
   }
   else if (lightEngine_ != Sola_Type)
   {
      switch (colorName)
      {
         case RED:
            newEnableMask = SetBit(previousEnableMask, BIT_RED, newState);
            break;
         case GREEN:
            newEnableMask = SetBit(previousEnableMask, BIT_GREEN, newState);
            break;
         case CYAN:
            newEnableMask = SetBit(previousEnableMask, BIT_CYAN, newState);
            break;
         case VIOLET:
            newEnableMask = SetBit(previousEnableMask, BIT_VIOLET, newState);
            break;
         case YGFILTER:
            newEnableMask = SetBit(previousEnableMask, BIT_YG_FILTER, newState);
            break;
         case BLUE:
            if (lightEngine_ != Aura_Type)
               newEnableMask = SetBit(previousEnableMask, BIT_BLUE, newState);
            break;
         case TEAL:
            if (lightEngine_ != Aura_Type)
               newEnableMask = SetBit(previousEnableMask, BIT_TEAL, newState);
            break;
      }
   }

   enableMask_ = newEnableMask;

   if (!open_ && colorName != YGFILTER)
      // No change in device necessary.
      return DEVICE_OK;

   // The actual (shuttered) device state we will switch to.
   unsigned char newShutteredEnableMask = newEnableMask;

   return SendColorEnableMask(newShutteredEnableMask);
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


// This function Inits the GPIO on the 
// and must be done any time the LE is powered off then on again
int Spectra::InitLE()
{
   // Initialization sequence:
   unsigned char GPIO0to3[] = { 0x57, 0x02, 0xff, 0x50 };
   unsigned char GPIO5to7[] = { 0x57, 0x03, 0xab, 0x50 };

   // Initialize our channel mask: all channels off, except for the DAC
   // computer control in the case of the Aura.
   enableMask_ = InitialEnableMask(lightEngine_);

   // Sync state to device:
   unsigned char DACCtl[] = { 0x4f, enableMask_, 0x50 };

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
   if (eAct == MM::BeforeGet)
   {
      bool open;
      int ret = GetOpen(open);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(open ? 1L : 0L);
      return DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      return SetOpen(pos != 0);
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
// Color Value Change Handlers
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


// *****************************************************************************
// Color Enable Change Handlers
// *****************************************************************************

int Spectra::OnRedEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long State;
      pProp->Get(State);
      SetColorEnabled(RED, (State != 0));
   }
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(GetBitOn(enableMask_, BIT_RED) ? 1L : 0L);
   }
   return DEVICE_OK;
}

int Spectra::OnGreenEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long State;
      pProp->Get(State);
      SetColorEnabled(GREEN, (State != 0));
   }
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(GetBitOn(enableMask_, BIT_GREEN) ? 1L : 0L);
   }
   return DEVICE_OK;
}


int Spectra::OnCyanEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long State;
      pProp->Get(State);
      SetColorEnabled(CYAN, (State != 0));
   }
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(GetBitOn(enableMask_, BIT_CYAN) ? 1L : 0L);
   }
   return DEVICE_OK;
}


int Spectra::OnVioletEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long State;
      pProp->Get(State);
      SetColorEnabled(VIOLET, (State != 0));
   }
   if (eAct == MM::BeforeGet) 
   {
      pProp->Set(GetBitOn(enableMask_, BIT_VIOLET) ? 1L : 0L);
   }
   return DEVICE_OK;
}

int Spectra::OnTealEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long State;
      pProp->Get(State);
      SetColorEnabled(TEAL, (State != 0));
   }
   if (eAct == MM::BeforeGet) 
   {
      pProp->Set(GetBitOn(enableMask_, BIT_TEAL) ? 1L : 0L);
   }
   return DEVICE_OK;
}

int Spectra::OnBlueEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long State;
      pProp->Get(State);
      SetColorEnabled(BLUE, (State != 0));
   }
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(GetBitOn(enableMask_, BIT_BLUE) ? 1L : 0L);
   }
   return DEVICE_OK;
}

int Spectra::OnWhiteEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long State;
      pProp->Get(State);
      SetColorEnabled(WHITE, (State != 0));
   }
   if (eAct == MM::BeforeGet)
   {
      unsigned char whiteMask = SetAllOn(enableMask_, lightEngine_);
      pProp->Set((enableMask_ == whiteMask) ? 1L : 0L);
   }
   return DEVICE_OK;
}


int Spectra::OnYGFilterEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long State;
      pProp->Get(State);
      SetColorEnabled(YGFILTER, (State != 0));
   }
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(GetBitOn(enableMask_, BIT_YG_FILTER) ? 1L : 0L);
   }
   return DEVICE_OK;
}

