///////////////////////////////////////////////////////////////////////////////
// FILE:          CIA.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Lumencore Light Engine driver
//                CIA
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

#define DEBUG

//#include <commdlg.h>   // for GetSaveFileName
//#include <shlobj.h>    // for SHGetFolderPath

#include "CIA.h"
#include <string>
#include <math.h>
#include "..\..\MMDevice\ModuleInterface.h"
#include "..\..\MMDevice\DeviceUtils.h"
#include <sstream>
#include <fstream>
#include "time.h"


const char* g_LumencorController = "Lumencor";
const char* g_CIA =		"CIA";
const char* g_Channel_1 =	"1";
const char* g_Channel_2 =	"2";
const char* g_Channel_3 =	"3";

enum LEType {Aura,Sola,Spectra,SpectraX};
enum PolarityType {Low=0, High=1};
// Color order in CIAColorLevels Array [Violet, Blue, Cyan, Teal, Green, Yellow, Red]
enum CIA_CL_Type { CL_Violet, CL_Blue, CL_Cyan, CL_Teal, CL_Green, CL_Yellow, CL_Red};
enum CIAState_Type { Unintialized, Ready, Running, Stopped};
char EnableMask = 0x7f;
int MaxEvents = 16;
unsigned char EventData[17];
unsigned char EventByte;
unsigned char CIAColorLevels[] = {0,0,0,0,0,0,0};
int EventCount = 0;
int EventIndex = 0;
bool ShutterState = false; // false= closed true = open
CIAState_Type CIARunState = Unintialized; // if true indicates that we have loaded a script and are running 
LEType LightEngine = Spectra; // Light Engine Type
PolarityType Input1 = High;
PolarityType Input2 = High;
std::string CIAFilePath("");
std::string CIABasePath("");
bool SerialEnable = true;

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_CIA, "Lumencor Camera Interface Adapter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_CIA) == 0)
   {
      CIA* s = new CIA();
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
 
void DbgPrintf(LPTSTR fmt,...    )
{
    va_list marker;
    char szBuf[MM::MaxStrLength];
 
    va_start(marker, fmt);
	vsprintf(szBuf, fmt, marker);
	va_end(marker);
    
    OutputDebugString(szBuf);
    OutputDebugString(TEXT("\r\n")); // output blank line after message to make it easier to find in log
}

///////////////////////////////////////////////////////////////////////////////
// Lumencor

CIA::CIA() :
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
   CreateProperty(MM::g_Keyword_Name, g_CIA, MM::String, true);
   //
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Lumencor Camera Interface Adapter", MM::String, true);
                                                                             
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &CIA::OnPort);      
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);       
}                                                                            
                                                                             
CIA::~CIA()                                                            
{                                                                            
   Shutdown();                                                               
} 

void CIA::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_CIA);
}  

int CIA::Initialize()
{
   if (initialized_)
      return DEVICE_OK;
      
   // set property list
   // -----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &CIA::OnState);
   int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;                                                            
                                                                             
   AddAllowedValue(MM::g_Keyword_State, "0");                                
   AddAllowedValue(MM::g_Keyword_State, "1");                                
                                              
   // The Channel we will act on
   // ----
   pAct = new CPropertyAction (this, &CIA::OnChannel);
   ret=CreateProperty("Channel", g_Channel_1, MM::String, false, pAct);  

   vector<string> commands;                                                  
   commands.push_back(g_Channel_1);                                           
   commands.push_back(g_Channel_2);                                            
   commands.push_back(g_Channel_3);                                         
   ret = SetAllowedValues("Channel", commands);        
   if (ret != DEVICE_OK)                                                    
      return ret;

   // get the version number
   pAct = new CPropertyAction(this,&CIA::OnVersion);

   // If GetVersion fails we are not talking to the Lumencor
   ret = GetVersion();
   if (ret != DEVICE_OK)                                                     
      return ret;                                                            

   ret = CreateProperty("Version", version_.c_str(), MM::String,true,pAct); 

   // switch all channels off on startup instead of querying which one is open
   SetProperty(MM::g_Keyword_State, "0");

   pAct = new CPropertyAction(this,&CIA::OnEventCount);
   CreateProperty("Event_Count",    "0", MM::Integer, false, pAct, false);
   //
   // Declare action function CIA Play Contrrol
   //
   pAct = new CPropertyAction(this,&CIA::OnCIAStart);
   CreateProperty("CIA_Start",    "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnClearSeq);
   CreateProperty("Clear_Seq",    "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnRunCommand);
   CreateProperty("Run_Seq",    "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnStop_CIA);
   CreateProperty("Stop_Seq",    "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnGetInfo);
   CreateProperty("Get_Info",    "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnDownLoadSeq);
   CreateProperty("DownLoad_Seq",    "CIA_DefaultScript.CIA", MM::String, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnStepSeq);
   CreateProperty("Step_Seq",    "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnRewindSeq);
   CreateProperty("Rewind_Seq",    "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnSetLE_Type);
   CreateProperty("SetLE_Type",    "Spectra", MM::String, false, pAct, false);
      vector<string> LETypeStr;
		LETypeStr.push_back("Spectra");
		LETypeStr.push_back("SpectraX");
		LETypeStr.push_back("Aura");
		LETypeStr.push_back("Sola");
		ret = SetAllowedValues("SetLE_Type", LETypeStr);
		assert(ret == DEVICE_OK);


   pAct = new CPropertyAction(this,&CIA::OnSet_Input1_Pol);
   CreateProperty("Input1_Polarity",    "High", MM::String, false, pAct, false);
   vector<string> Input1PolSel;
		Input1PolSel.push_back("Low");
		Input1PolSel.push_back("High");
		ret = SetAllowedValues("Input1_Polarity", Input1PolSel);
		assert(ret == DEVICE_OK);

   pAct = new CPropertyAction(this,&CIA::OnSet_Input2_Pol);
   CreateProperty("Input2_Polarity",    "High", MM::String, false, pAct, false);
   vector<string> Input2PolSel;
        Input2PolSel.push_back("Low");
		Input2PolSel.push_back("High");
		ret = SetAllowedValues("Input2_Polarity", Input2PolSel);
		assert(ret == DEVICE_OK);

   pAct = new CPropertyAction(this,&CIA::OnSetScriptCommand);
   CreateProperty("Set_Script",    "CIA_DefaultScript.cia", MM::String, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnSetBasePathCommand);
   CreateProperty("Set_BasePath",    "", MM::String, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnLoadScriptCommand);
   CreateProperty("Load_Script",    "CIA_DefaultScript.cia", MM::Integer, false, pAct, false);

   //pAct = new CPropertyAction(this,&CIA::OnSaveScriptCommand);
   //CreateProperty("Save_Script",    "CIA_DefaultScript.cia", MM::String, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnAddEventCommand);
   CreateProperty("Add_Event",    "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnRemoveEventCommand);
   CreateProperty("Remove_Event",    "0", MM::Integer, false, pAct, false);

   //
   // Declare action function for color Value changes
   //
   pAct = new CPropertyAction(this,&CIA::OnRedValue);
   CreateProperty("Red_Level",    "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnGreenValue);
   CreateProperty("Green_Level",  "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnCyanValue);
   CreateProperty("Cyan_Level",   "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnVioletValue);
   CreateProperty("Violet_Level", "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnTealValue);
   CreateProperty("Teal_Level",   "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnBlueValue);
   CreateProperty("Blue_Level",   "0", MM::Integer, false, pAct, false);
   //
   // Declare action function for color Enable changes
   //
   pAct = new CPropertyAction(this,&CIA::OnRedEnable);
   CreateProperty("Red_Enable",    "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnGreenEnable);
   CreateProperty("Green_Enable",  "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnCyanEnable);
   CreateProperty("Cyan_Enable",   "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnVioletEnable);
   CreateProperty("Violet_Enable", "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnTealEnable);
   CreateProperty("Teal_Enable",   "0", MM::Integer, false, pAct, false);

   pAct = new CPropertyAction(this,&CIA::OnBlueEnable);
   CreateProperty("Blue_Enable",   "0", MM::Integer, false, pAct, false);

   // Yellow Green Filter
   pAct = new CPropertyAction(this,&CIA::OnYGFilterEnable);
   CreateProperty("YG_Filter", "0", MM::Integer, false, pAct, false);
   SetPropertyLimits("YG_Filter", 0, 1);

   // Set Cotrol Limits
    SetPropertyLimits("Load_Script", 0, 1);
    SetPropertyLimits("Clear_Seq", 0, 1);
	SetPropertyLimits("Run_Seq", 0, 1);
	SetPropertyLimits("Stop_Seq", 0, 1);
	SetPropertyLimits("Get_Info", 0, 1);
	SetPropertyLimits("Step_Seq", 0, 1);
	SetPropertyLimits("Rewind_Seq", 0, 1);
	SetPropertyLimits("Add_Event", 0, 1);
	SetPropertyLimits("Remove_Event", 0, 1);
	SetPropertyLimits("Event_Count", 0, 15);
	SetPropertyLimits("CIA_Start", 0, 1);

   // Color Value Limits
   SetPropertyLimits("Red_Level",    0, 100);
   SetPropertyLimits("Green_Level",  0, 100);
   SetPropertyLimits("Cyan_Level",   0, 100);
   SetPropertyLimits("Violet_Level", 0, 100);
   SetPropertyLimits("Teal_Level",   0, 100);
   SetPropertyLimits("Blue_Level",   0, 100);
	 
   //
   SetPropertyLimits("Red_Enable",    0, 1);
   SetPropertyLimits("Green_Enable",  0, 1);
   SetPropertyLimits("Cyan_Enable",   0, 1);
   SetPropertyLimits("Violet_Enable", 0, 1);
   SetPropertyLimits("Teal_Enable",   0, 1);
   SetPropertyLimits("Blue_Enable",   0, 1);
   //

   ret = UpdateStatus();

   if (ret != DEVICE_OK)                                                     
      return ret;                                                            
                                                                             
   initialized_ = true;                                                      
   return DEVICE_OK;                                                         
}  
//**************************
//  Get Serial Response
//************************** 
int CIA::GetResponse(const char *command, std::string &answer)
{
   std::string CmdPrefix;
   int ret;

   CDeviceUtils::SleepMs(10); // wait a little for the response
   CmdPrefix = command[1]; // commands have syntax like '#C' where C is the command so we stript the commad part to compare
   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
   DbgPrintf("CommandResponse: Command->[%s] Response->[%s]",command, answer.c_str());
   if(ret == DEVICE_OK)
   {
	   if(answer.compare(CmdPrefix) == 0) // if correct response
	   {
		   ret = DEVICE_OK;
	   }
	   else
	   {
		   	DbgPrintf("CommandResponse not correct Expected[%s] Actual[%s]",CmdPrefix.c_str(), answer.c_str());
			ret = ERR_UNRECOGNIZED_ANSWER;
	   }
   }
   return ret;
}
//**************************
//  Sleep function
//************************** 
void sleep(unsigned int mseconds)
{
	clock_t goal = mseconds + clock();
	while (goal > clock());
}


int CIA::SetOpen(bool open)
{  
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   ShutterState = open; // set to state of open passed in
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
} 

int CIA::GetOpen(bool& open)
{     
   char buf[MM::MaxStrLength];
   long pos;
   int ret;
   ret = GetProperty(MM::g_Keyword_State, buf);
   pos = atol(buf);
   if (ret != DEVICE_OK)                                                     
      return ret;                                                                                                               
   pos == 1 ? open = true : open = false;
   ShutterState = open;

   open=false;
   return DEVICE_OK;                                                         
} 

/**
 * Here we set the shutter to open or close
 */
int CIA::SetShutterPosition(bool state)                              
{   
	ShutterState = state;
	// DbgPrintf("In SetShutterPosition ShutterState = %d",ShutterState);
	if (state)
	{
		// DbgPrintf("Shutter Set to Open");
		LogMessage("In SetShutterPosition: OPEN ");
	}
	else
	{
		// DbgPrintf("Shutter Set to Closed");
		LogMessage("In SetShutterPosition: CLOSED ");
	}
    return DEVICE_OK;
}

// *****************************************************************************
// Add Event to Event Array
// *****************************************************************************
int CIA::AddEvent()
{
	if( EventIndex < MaxEvents)
	{
		EventData[EventIndex++] = EventByte; // add event to event Array
		EventCount++;
		int ret = SetProperty("Event_Count", CDeviceUtils::ConvertToString(EventCount -1));
		return ret;
	}
	return DEVICE_OK; 
}
// *****************************************************************************
// Remove Event from Event Array
// *****************************************************************************
int CIA::RemoveEvent()
{
	if( EventIndex > 0)
	{
		EventCount--;
		EventIndex--;
		int ret = SetProperty("Event_Count", CDeviceUtils::ConvertToString(EventCount));
		return ret;
	}
	return DEVICE_OK; 
}
// *****************************************************************************
// Load Sequence File
// *****************************************************************************
int CIA::LoadSeq()
{
	char FileNamebuf[MM::MaxStrLength];
	std::string rsvbuf;
	rsvbuf.reserve(MM::MaxStrLength);
	std::stringstream buffer(rsvbuf);
	std::string filename, msg;
	int ret;
	ret = GetProperty("Set_Script", FileNamebuf);
	if(strcmp(FileNamebuf, "") != 0)
	{
		filename = FileNamebuf;
		SetScriptName(filename); // build and set the CIAFilePath variable
		std::ifstream infile(CIAFilePath.c_str());  // define file stream
		if(strcmp(CIAFilePath.c_str(), "") != 0)
		{
			buffer << infile.rdbuf(); // read the data from the file into our buffer
			if(infile)
			{   
				msg = "in LoadSeq;-- File Found-->[";
				msg += CIAFilePath.c_str();
				msg += "]";
				LogMessage(msg.c_str());

				DbgPrintf("LoadSeq: File Found --> [%s]",CIAFilePath.c_str());
				DbgPrintf("LoadSeq: Length of Data Loaded --> [%d]", sizeof(buffer));
				ParseSeqData(buffer.str().c_str());
				SetProperty("Event_Count", CDeviceUtils::ConvertToString(EventCount));
				DbgPrintf("LoadSeq: Event Count is [%s]",CDeviceUtils::ConvertToString(EventCount));
			}
			else
			{
				msg = "in LoadSeq -- File is Not Present[";
				msg += CIAFilePath.c_str();
				msg += "]";
				LogMessage(msg.c_str());
				DbgPrintf("Script File not Found [%s]",CIAFilePath.c_str());
			}
		}
		else
		{
			LogMessage("in LoadSeq -- No File Defined ");
		}
	}
	return DEVICE_OK; 
}
// *****************************************************************************
// Save Sequence File
// *****************************************************************************
//int CIA::SaveSeq()
//{
//	return DEVICE_OK; 
//}

// *****************************************************************************
// Write Sequence to Hardware
// *****************************************************************************
int CIA::WriteSeqToHW()
{
	string DataMsg;
	std::ostringstream command;
	std::string Cmd;
	std::string Response;
    unsigned char LevelArray[7];
	unsigned char PolArray[5];
	int ret;

		PurgeComPort(port_.c_str());
		command << "#H"; // Send Level Cmd Data
		SendSerialCommand(port_.c_str(), command.str().c_str(), "\n"); // send to device
		command.str("");
        LevelArray[0] = CIAColorLevels[CL_Violet]; 
		LevelArray[1] = CIAColorLevels[CL_Cyan];
		LevelArray[2] = CIAColorLevels[CL_Green];
		LevelArray[3] = CIAColorLevels[CL_Red];
		LevelArray[4] = CIAColorLevels[CL_Blue];
		LevelArray[5] = CIAColorLevels[CL_Teal];
		LevelArray[6] = CIAColorLevels[CL_Green]; // set Yellow to green level as there is really no Yellow Level only a yelloow filter
	    WriteToComPort(port_.c_str(),LevelArray, 7); // Write Level Array to device
 		SendSerialCommand(port_.c_str(), "", "\n"); // Send the CR only here
		ret = GetResponse("#H",Response);

		if(ret != DEVICE_OK)
		{
			DbgPrintf("Error getting #H-Header Command Response");
		}

		EventData[EventCount] = '\n';

		// Cmd = "#D" + '\n';  // Send Event Data Cmd
        command << "#D";
		SendSerialCommand(port_.c_str(), command.str().c_str(), "\n"); // send to device
		WriteToComPort(port_.c_str(),EventData, EventCount); // Write Event Data to device
		SendSerialCommand(port_.c_str(), "", "\n"); // send to device
		command.str("");
		ret = GetResponse("#D",Response);
		if(ret != DEVICE_OK)
		{
			DbgPrintf("Error getting #D-Data Command Response");
		}

		//Cmd = "#E" + (unsigned char) LightEngine + '\n';  // Send Light Engine Type
		command << "#E" << (unsigned char) LightEngine + 1; 
		SendSerialCommand(port_.c_str(), command.str().c_str(), "\n"); // send to device
		command.str("");
		ret = GetResponse("#E",Response);
		if(ret != DEVICE_OK)
		{
			DbgPrintf("Error getting #E-LightEngine Command Response");
		}
		PolArray[0] = (unsigned char) '#';
		PolArray[1] = (unsigned char) 'P';
		PolArray[2] = (unsigned char) Input1;
		PolArray[3] = (unsigned char) Input2;
		PolArray[4] = (unsigned char) '\n';
		WriteToComPort(port_.c_str(),PolArray, 5); // Write Event Data to device
		ret = GetResponse("#P",Response);
		if(ret != DEVICE_OK)
		{
			DbgPrintf("Error getting #P-Polarity Command Response");
		}

	return DEVICE_OK;
}
// *****************************************************************************
// Update Color Enables
// *****************************************************************************
int CIA::UpdateColorEnables(unsigned char DataByte)
{
	enum ColorBitValues {RedBit=0x01,GreenBit=0x02,CyanBit=0x04,VioletBit=0x04,BlueBit=0x20,TealBit=0x40};
	if (DataByte & RedBit)
		SetProperty("Red_Enable",CDeviceUtils::ConvertToString(true));
	else
		SetProperty("Red_Enable",CDeviceUtils::ConvertToString(false));

	if (DataByte & GreenBit)
		SetProperty("Green_Enable",CDeviceUtils::ConvertToString(true));
	else
		SetProperty("Green_Enable",CDeviceUtils::ConvertToString(false));

	if (DataByte & CyanBit)
		SetProperty("Cyan_Enable",CDeviceUtils::ConvertToString(true));
	else
		SetProperty("Cyan_Enable",CDeviceUtils::ConvertToString(false));

	if (DataByte & VioletBit)
		SetProperty("Violet_Enable",CDeviceUtils::ConvertToString(true));
	else
		SetProperty("Violet_Enable",CDeviceUtils::ConvertToString(false));

	if (DataByte & BlueBit)
		SetProperty("Blue_Enable",CDeviceUtils::ConvertToString(true));
	else
		SetProperty("Blue_Enable",CDeviceUtils::ConvertToString(false));

	if (DataByte & TealBit)
		SetProperty("Teal_Enable",CDeviceUtils::ConvertToString(true));
	else
		SetProperty("Teal_Enable",CDeviceUtils::ConvertToString(false));
	return DEVICE_OK;
}


// *****************************************************************************
// Sets local copy of color EventByte byte for building commands
// *****************************************************************************
int CIA::SetColorEnable(ColorNameT ColorName,bool State, char* EnableMask)
{
	enum StateValue {OFF=0, ON=1};

	switch (ColorName)
	{	
		case  RED:
			if(State==ON)
				EventByte = *EnableMask & 0x3E;
			else
				EventByte = *EnableMask | 0x01;
			break;

		case  TEAL:
			if(State==ON)
			    EventByte = *EnableMask & 0x3D;
			else
				EventByte = *EnableMask | 0x02;
			break;

		case  GREEN:
			if(State==ON)
				EventByte = *EnableMask & 0x3B;
			else
				EventByte = *EnableMask | 0x04;
			break;

		case  BLUE:
			if(State==ON)
			    EventByte = *EnableMask & 0x37;
			else
				EventByte = *EnableMask | 0x08;
			break;

		case  CYAN:
			if(State==ON)
			    EventByte = *EnableMask & 0x2F;
			else
				EventByte = *EnableMask | 0x10;
			break;

		case  VIOLET:
			if(State==ON)
			    EventByte = *EnableMask & 0x1F; 
			else
				EventByte = *EnableMask | 0x20;
			break;
		//case  YGFILTER:
		//	if(State==ON)
		//		EventByte = *EnableMask & 0x6F;
		//	else
		//		EventByte = *EnableMask | 0x10;
		//	break;
		case ALL:
			if(State==ON)
				EventByte = *EnableMask & 0x00;
			else
			    EventByte = 0x3F; // dont toggle YG filter if not needed
			break;
		default:
			break;		
	}
	*EnableMask = EventByte; // Sets the Mask to current state
	return DEVICE_OK;  // debug only 
}

// *****************************************************************************
// Sends color level command to CIA Hardware
// *****************************************************************************
int CIA::SendColorLevelCmd(ColorNameT ColorName,int ColorLevel)
{
	unsigned int ColorValue;
	unsigned char DACSetupArray[]= "\x53\x18\x03\x00\x00\x00\x50\x00";
	unsigned char CmdPrefix[]="#Z\x09S";
	unsigned char NewLine[] = "\n";
	std::string Cmd;
	std::string Response;
	int ret;

	ColorValue = 255-(unsigned int)(2.55 * ColorLevel); // map color to range
	ColorValue &= 0xFF;  // Mask to one byte
	ColorValue = (ColorLevel == 100) ? 0 : ColorValue;
	ColorValue = (ColorLevel == 0) ? 0xFF : ColorValue;  // coherce to correct values at limits
	if(LightEngine == Sola)
	{
		ColorName = ALL;
	}
	switch(ColorName)
	{
		case  RED:
			DACSetupArray[3] = 0x08;
			break;
		case  GREEN:
	    case  YELLOW:
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
			// not supported on CIA
			break;
		default:
			break;		
	}
	DACSetupArray[4] = (char) ((ColorValue >> 4) & 0x0F) | 0xF0;
	DACSetupArray[5] = (char) (ColorValue << 4) & 0xF0;

	DbgPrintf("#Z Color Level Immediate Command [%02x]", ColorValue);
    PurgeComPort(port_.c_str());
	WriteToComPort(port_.c_str(),CmdPrefix,4);      // write command prefix
	WriteToComPort(port_.c_str(),DACSetupArray, 7); // Write Event Data to device
	WriteToComPort(port_.c_str(),NewLine,1);           // write ending linefeed

	ret = GetResponse("#Z",Response);
	if(ret != DEVICE_OK)
	{
		DbgPrintf("Error getting #Z-Set Color Level Command Response");
	}
	// block/wait no acknowledge so just give it time                      
	// CDeviceUtils::SleepMs(200);
	return DEVICE_OK;  // debug only 
}



// *****************************************************************************
// Sends color Enable/Disable command to CIA Hardware
// *****************************************************************************
int CIA::SendColorEnableCmd(ColorNameT ColorName,bool State, char* EnableMask)
{
	enum StateValue {OFF=0, ON=1};
	unsigned char DACSetupArray[]= "\x4F\x00\x50\x00";
	unsigned char CmdPrefix[] = "#Z\x03P";
	unsigned char NewLine[] = "\n";
	unsigned char CommandData[] = "";
	std::string Response;
	int ret;

	if(LightEngine == Sola)
	{
		 ColorName = ALL;
	}
	switch (ColorName)
	{	
		case  RED:
			if(State==ON)
				//DACSetupArray[1] = *EnableMask & 0x7E;
				DACSetupArray[1] = *EnableMask & 0x3E;
			else
				DACSetupArray[1] = *EnableMask | 0x01;
			break;
		case  GREEN:
			if(State==ON)
				//DACSetupArray[1] = *EnableMask & 0x7D;
				DACSetupArray[1] = *EnableMask & 0x3B;
			else
				//DACSetupArray[1] = *EnableMask | 0x02;
				DACSetupArray[1] = *EnableMask | 0x04;
			break;
		case  VIOLET:
			if(State==ON)
				//DACSetupArray[1] = *EnableMask & 0x77;
				DACSetupArray[1] = *EnableMask & 0x3F;
			else
				//DACSetupArray[1] = *EnableMask | 0x08;
				DACSetupArray[1] = *EnableMask | 0x20;
			break;
		case  CYAN:
			if(State==ON)
				//DACSetupArray[1] = *EnableMask & 0x7B;
				DACSetupArray[1] = *EnableMask & 0x2f;
			else
				//DACSetupArray[1] = *EnableMask | 0x04;
				DACSetupArray[1] = *EnableMask | 0x10;
			break;
		case  BLUE:
			if(State==ON)
				//DACSetupArray[1] = *EnableMask & 0x5F;
				DACSetupArray[1] = *EnableMask & 0x37;
			else
				//DACSetupArray[1] = *EnableMask | 0x20;
				DACSetupArray[1] = *EnableMask | 0x8;
			break;
		case  TEAL:
			if(State==ON)
				//DACSetupArray[1] = *EnableMask & 0x3F;
				DACSetupArray[1] = *EnableMask & 0x3D;
			else
				//DACSetupArray[1] = *EnableMask | 0x40;
				DACSetupArray[1] = *EnableMask | 0x02;
			break;
		//case  YGFILTER:
		//	if(State==ON)
		//		DACSetupArray[1] = *EnableMask & 0x6F;
		//	else
		//		DACSetupArray[1] = *EnableMask | 0x10;
		//	break;
		case ALL:
		case WHITE:
			if(State==ON)
			{
				DACSetupArray[1] = ((*EnableMask & 0x40) == 0x40) ? 0x40 : 0x00;
			}
			else
				DACSetupArray[1] = ((*EnableMask & 0x40) == 0x40) ? 0x7F : 0xCF; // dont toggle YG filter if not needed
			break;
		default:
			break;		
	}
	if (LightEngine == Aura)
	{
		// See Aura TTL IF Doc: Front Panel Control/DAC for more detail
		DACSetupArray[1] = DACSetupArray[1] | 0x20; // Mask for Aura to be sure DACs are Enabled 
	}
    CommandData[0] = DACSetupArray[1];   //We only will send the one eanble byte

	DbgPrintf("#Z Color Enable Immediate Command(TTL)[%02x]", CommandData[0]);
	PurgeComPort(port_.c_str());
    WriteToComPort(port_.c_str(),CmdPrefix,4);       // write command prefix
	WriteToComPort(port_.c_str(),CommandData, 1);  // Write Event Data to device
	WriteToComPort(port_.c_str(),NewLine,1);           // write ending linefeed

	ret = GetResponse("#Z",Response);
	if(ret != DEVICE_OK)
	{
		DbgPrintf("Error getting #Z-Color Enable Command Response");
	}
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
// *****************************************************************************
// Get Version
// *****************************************************************************
int CIA::GetVersion()
{
   // We will verify that we are talking to the Lumencor product by writing to a
   // To be added when hardware supports Version Numbers
    return DEVICE_OK;  // debug only 
}


int CIA::Shutdown()                                                
{                                                                            
   if (initialized_)                                                         
   {                                                                         
      initialized_ = false;                                                  
   }                                                                         
   return DEVICE_OK;                                                         
}                                                                            

// Never busy because all commands block
bool CIA::Busy()
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
int CIA::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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
// *****************************************************************************
// On State
// *****************************************************************************
int CIA::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
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
// *****************************************************************************
// On Channel
// *****************************************************************************
int CIA::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
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

// *****************************************************************************
// On Version Command
// ***************************************************************************** 
int CIA::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
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

// *****************************************************************************
//                           CIA Commands
// *****************************************************************************
// Color order in CIAColorLevels Array [Violet, Blue, Cyan, Teal, Green, Yellow, Red]
// *****************************************************************************
// On Run Command
// *****************************************************************************
int CIA::OnRunCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::ostringstream command;
	std::string Response;
	double RState;
	int ret;
	pProp->Get(RState);
	if (eAct == MM::AfterSet && RState == 1)
    {

		command << "#S";  // Send Stop Command
		SendSerialCommand(port_.c_str(), command.str().c_str(), "\n"); // send to device
		ret = GetResponse("#S",Response);
		if(ret != DEVICE_OK)
		{
			DbgPrintf("Error getting #S-Stop Command Response");
		}
		CIARunState = Stopped;

		WriteSeqToHW();  // Send Sequence data to CIA
		CDeviceUtils::SleepMs(10); // may not be needed. allows time for hardware to init

        command.str(""); // Empty command buffer
		command << "#R"; // Send Run command
		SendSerialCommand(port_.c_str(), command.str().c_str(), "\n"); // send to device
		ret = GetResponse("#R",Response);
		if(ret != DEVICE_OK)
		{
			DbgPrintf("Error getting #R-Run Command Response");
		}
		CIARunState = Running;

		RState = 0;
		pProp->Set(RState); // Clear the button
	}
	return DEVICE_OK;
}
// *****************************************************************************
// On Stop Command
// *****************************************************************************
int CIA::OnStop_CIA(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::ostringstream command;
	double RState;
	int ret;
	std::string Response;

    pProp->Get(RState);
	if (eAct == MM::AfterSet && RState == 1)
    {
		command << "#S";  // Send Stop Command
		SendSerialCommand(port_.c_str(), command.str().c_str(), "\n"); // send to device
		CIARunState = Stopped;
		ret = GetResponse("#S",Response);
		if(ret != DEVICE_OK)
		{
			DbgPrintf("Error getting #S-Stop Command Response");
		}
		RState = 0;
		pProp->Set(RState); // Clear the button
	}
	return DEVICE_OK;
}

// *****************************************************************************
// On Event Count
// *****************************************************************************
int CIA::OnEventCount(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	double RState;
    pProp->Get(RState);
	if (eAct == MM::AfterSet && RState == 1)
    {
		RState = EventCount;
		pProp->Set(RState); // Clear the button
	}
	return DEVICE_OK;
}
// *****************************************************************************
// On Get Info
// *****************************************************************************
int CIA::OnGetInfo(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::ostringstream command;
	double RState;
	pProp->Get(RState);
	if (eAct == MM::AfterSet && RState == 1)
    {
		command << "#I";  // Send Stop Command"#I" 
		SendSerialCommand(port_.c_str(), command.str().c_str(), "\n"); // send to device
		RState = 0;
		pProp->Set(RState); // Clear the button
	}
	return DEVICE_OK;
}
// *****************************************************************************
// On Load Sequence
// *****************************************************************************
int CIA::OnDownLoadSeq(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	double RState;
	pProp->Get(RState);
	if (eAct == MM::AfterSet && RState == 1)
    {
		if (EventCount > 0)
			WriteSeqToHW();
		else
			LogMessage("InDownLoadSeq -- No events to Send");

		RState = 0;
		pProp->Set(RState); // Clear the button
	}
	return DEVICE_OK;
}
// *****************************************************************************
// On Step Sequence
// *****************************************************************************
int CIA::OnStepSeq(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::ostringstream command;
	double RState;
	int ret;
	std::string Response;
	pProp->Get(RState);
	if (eAct == MM::AfterSet && RState == 1)
    {
		if(CIARunState == Running)
		{
			command << "#S";  // Send Stop Command
			SendSerialCommand(port_.c_str(), command.str().c_str(), "\n"); // send to device
			ret = GetResponse("#S",Response);
			if(ret != DEVICE_OK)
			{
				DbgPrintf("Error getting #S-Stop Command Response");
			}
			CIARunState = Stopped;
		}
		command.str(""); // clear the string
		command << "#T";// Send Step Command
		SendSerialCommand(port_.c_str(), command.str().c_str(), "\n"); // send to device
		CIARunState = Ready;
		ret = GetResponse("#T",Response);
		if(ret != DEVICE_OK)
		{
			DbgPrintf("Error getting #T-Step Command Response");
		}
		RState = 0;
		pProp->Set(RState); // Clear the button
	}
	return DEVICE_OK;
}

// *****************************************************************************
// On Rewind Sequence
// *****************************************************************************
int CIA::OnRewindSeq(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::ostringstream command;
	double RState;
	int ret;
	std::string Response;
	pProp->Get(RState);
	if (eAct == MM::AfterSet && RState == 1)
    {
		command << "#@";  // Send Rewind Command
		SendSerialCommand(port_.c_str(), command.str().c_str(), "\n"); // send to device
		ret = GetResponse("#@",Response);
		if(ret != DEVICE_OK)
		{
			DbgPrintf("Error getting #@-Rewind Command Response");
		}
		RState = 0;
		pProp->Set(RState); // Reset the button
		CIARunState = Ready;
	}
	return DEVICE_OK;
}

// *****************************************************************************
// On CIA Start command
// *****************************************************************************
int CIA::OnCIAStart(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::ostringstream command;
	double RState;
	int ret;
	std::string Response;

	pProp->Get(RState);
	if (eAct == MM::AfterSet)
    {
		if(RState == 1) // Starting ?
		{
			if(CIARunState == Running)
			{
				//  running so do nothing
			}
			else
			{
				LoadSeq(); // loads script and downloads to HW
				WriteSeqToHW();
				command << "#R"; // Send Run command
				SendSerialCommand(port_.c_str(), command.str().c_str(), "\n"); // send to device
				ret = GetResponse("#R",Response);
				if(ret != DEVICE_OK)
				{
					DbgPrintf("Error getting #R-Run Command Response");
				}
				CIARunState = Running;
			}
		}
		else // not Starting so Stop
		{
				//command << "#S"; // Send Run command
				//SendSerialCommand(port_.c_str(), command.str().c_str(), "\n"); // send to device
				CIARunState = Stopped;
		}

	}
	
	return DEVICE_OK;
}

// *****************************************************************************
// On Clear Sequence
// *****************************************************************************
int CIA::OnClearSeq(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::ostringstream command;
	double RState;
	pProp->Get(RState);
	if (eAct == MM::AfterSet && RState == 1)
    {
		EventCount = 0; // Reset index and count
		EventIndex = 0;
		CIARunState = Unintialized;
		RState = 0;
		pProp->Set(RState); // reset button
	}
	return DEVICE_OK;
}
// *****************************************************************************
// On Set LE Type
// *****************************************************************************
int CIA::OnSetLE_Type(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::ostringstream command;
	std::string LightEng("");
	int ret;
	std::string Response;

	if (eAct == MM::AfterSet)
    {
	    pProp->Get(LightEng);
		if (LightEng == "Aura")
		{
			LightEngine = Aura;
			// Cmd = "#E1" + '\n';
			command << "#E1";
		}
		else
		{
			if (LightEng == "Sola")
			{
				LightEngine = Sola;
				command <<  "#E2";
			}
			else 
			{ 
				if (LightEng == "Spectra")
				{
					LightEngine = Spectra;
					command << "#E3";
				}
				else
				{
					if (LightEng == "SpectraX")
					{
						LightEngine = SpectraX;
						command << "#E4";
					}
				}
			}
		}
		SendSerialCommand(port_.c_str(), command.str().c_str(), "\n"); // send to device
		ret = GetResponse("#E",Response);
		if(ret != DEVICE_OK)
		{
			DbgPrintf("Error getting #E-LightEngine Command Response");
		}
	}

	return DEVICE_OK;
}
// *****************************************************************************
// On Set Input1 Polarity
// *****************************************************************************
int CIA::OnSet_Input1_Pol(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string Cmd;
	string Input1Polarity;
	unsigned char PolArray[5];
	int ret;
	std::string Response;

	if (eAct == MM::AfterSet)
    {
	    pProp->Get(Input1Polarity);

	    if (Input1Polarity == "High")
			 Input1 = High;
		else
			 Input1 = Low;
		//Cmd = "#P" + (unsigned char) Input1 + (unsigned char) Input2 + '\n';  // Send Polarity
		//SendSerialCommand(port_.c_str(), Cmd.c_str(), ""); // send to device
		PolArray[0] = (unsigned char) '#';
		PolArray[1] = (unsigned char) 'P';
		PolArray[2] = (unsigned char) Input1;
		PolArray[3] = (unsigned char) Input2;
		PolArray[4] = (unsigned char) '\n';
		WriteToComPort(port_.c_str(),PolArray, 5); // Write Event Data to device

		ret = GetResponse("#P",Response);
		if(ret != DEVICE_OK)
		{
			DbgPrintf("Error getting #P-Polarity Command Response");
		}
	}

	return DEVICE_OK;
}
// *****************************************************************************
// On Set Input2 Polarity
// *****************************************************************************
int CIA::OnSet_Input2_Pol(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	std::string Cmd;
	string Input2Polarity;
	unsigned char PolArray[5];
	std::string Response;
	int ret;

	if (eAct == MM::AfterSet)
    {
	    pProp->Get(Input2Polarity);

	    if (Input2Polarity == "High")
			 Input2 = High;
		else
			 Input2 = Low;

		//Cmd = "#P" + (unsigned char) Input1 + (unsigned char) Input2 + '\n';  // Send Polarity
		//SendSerialCommand(port_.c_str(), Cmd.c_str(), ""); // send to device

		PolArray[0] = (unsigned char) '#';
		PolArray[1] = (unsigned char) 'P';
		PolArray[2] = (unsigned char) Input1;
		PolArray[3] = (unsigned char) Input2;
		PolArray[4] = (unsigned char) '\n';
		WriteToComPort(port_.c_str(),PolArray, 5); // Write Event Data to device
		ret = GetResponse("#P",Response);
		if(ret != DEVICE_OK)
		{
			DbgPrintf("Error getting #P-Polarity Command Response");
		}
	}

	return DEVICE_OK;
}                              
// *****************************************************************************
//   Set Script Name
// *****************************************************************************
int CIA::SetScriptName(std::string ScriptName)
{
	string FullPath;
	TCHAR szAppData[MAX_PATH];
	size_t found;
	int hr;
	// Here we build the path to the directory "users\appdata\local\lumencor\cia\filename"
	// this path will not work on OSX systems so this adapter will need modification 
	// to run on non windows systems
	if(CIABasePath.size() == 0){ // if no base path specified
		hr = SHGetFolderPath(NULL, CSIDL_LOCAL_APPDATA, NULL, 0, szAppData);
		FullPath = szAppData;
		FullPath.append("\\Lumencor\\CIA");
	}
	else{
		FullPath = CIABasePath;
	}
	found = FullPath.find_last_not_of("\\");
	if (found!=string::npos)
		FullPath.erase(found+1);

	FullPath += '\\' + ScriptName;
    OutputDebugString(FullPath.c_str());
	CIAFilePath = FullPath;
	return DEVICE_OK;
}
// *****************************************************************************
// On Set Script
// *****************************************************************************
int CIA::OnSetScriptCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	string ScriptName;

	if (eAct == MM::AfterSet)
    {
	    pProp->Get(ScriptName);
		SetScriptName(ScriptName);
   }
	return DEVICE_OK;
}

// *****************************************************************************
// On Set Script
// *****************************************************************************
int CIA::OnSetBasePathCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	string ScriptBasePath;

	if (eAct == MM::AfterSet)
    {
	    pProp->Get(ScriptBasePath);
		CIABasePath = ScriptBasePath;
   }
	return DEVICE_OK;
}
// *****************************************************************************
// Parse Sequence Data
// *****************************************************************************
int CIA::ParseSeqData(string data)
{
	bool done;
	int index,ColorBit,EventIdx;
	unsigned int i;
	std::vector<string> tokens;
	std::string CompString;
	//char *buf = new char[data.size() + 1];
	//buf[data.size()]=0;
    //memcpy(buf,data.c_str(),data.size());

	DbgPrintf("Seq data=[%s]",data.c_str());
	DbgPrintf("Data Size=[%d]",data.size());

	std::string delims="\t\n";
	CDeviceUtils::Tokenize(data,tokens,delims);
	index = EventIdx = 0;
	for(i=0; i < 7;i++)// parse the Color Row
	{
		CIAColorLevels[i]= (unsigned char) (2.55 * (100 - atoi(tokens.at(i).c_str()))); // get color level
		index++;
	}
    done = false;
	for(EventIdx = 0; EventIdx < MaxEvents && !done; EventIdx++)
	{
		EventByte = 0x3F;  // we use only lower 6 bits
		for(ColorBit = 0; ColorBit < 7 ; ColorBit++)
		{ 
			CompString = tokens.at(i + (EventIdx * 7) + ColorBit).c_str();
			if(CompString.compare("ON") == 0)
			{
				switch(ColorBit)
				{
					case 0:		// Violet
						//EventByte &= 0xF7;
						EventByte &= 0x1F; 
						break;
					case 1:		// Blue
						//EventByte &= 0xDF;
						EventByte &= 0x37; 
						break;
					case 2:		// Cyan
						//EventByte &= 0xFB;  
						EventByte &= 0x2F; 
						break;
					case 3:		// Teal
						//EventByte &= 0xBF;
						EventByte &= 0x2D;
						break;
					case 4:		// Green
						//EventByte &= 0xFD; 
						EventByte &= 0x3B;
						break;
					case 5:		// Yellow
						//EventByte &= 0xED;  <--  Not used
						break;
					case 6:		// Red
						//EventByte &= 0xFE; 
						EventByte &= 0x3E;
						break;
					default:
						break;
				}
			}
			//DbgPrintf("EventByte=[%0x]",EventIdx, EventByte);
		}
		EventData[EventIdx] = EventByte; // save to data array
		EventIndex = EventIdx;
		EventCount = EventIdx + 1;
		DbgPrintf("EventData[%d]=[%x]",EventIdx, EventByte);
		done = (i + (EventIdx * 7) + ColorBit) < tokens.size() ? false : true;
	}
	return DEVICE_OK;
}

// *****************************************************************************
// On Load Script
// *****************************************************************************
int CIA::OnLoadScriptCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	double RState;
	if (eAct == MM::AfterSet)
    {
	    pProp->Get(RState);
		if(RState == 1)
		{
			LoadSeq(); // loads script and downloads to HW
			CIARunState = Ready;
		}
		RState = 0;
		pProp->Set(RState); // Clear the button
   }
	return DEVICE_OK;
}
// *****************************************************************************
// On Save Script
// *****************************************************************************
//int CIA::OnSaveScriptCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
//{
//	return DEVICE_OK;
//}
// *****************************************************************************
// On Add Event
// *****************************************************************************
int CIA::OnAddEventCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	double RState;
	pProp->Get(RState);
	if (eAct == MM::AfterSet && RState == 1)
   {		
		AddEvent();
		RState = 0;
		pProp->Set(RState); // Clear the button
   }
	return DEVICE_OK;
}
// *****************************************************************************
// On Remove Event
// *****************************************************************************
int CIA::OnRemoveEventCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	double RState;
	pProp->Get(RState);
	if (eAct == MM::AfterSet && RState == 1)
	{
		RemoveEvent();
		RState = 0;
		pProp->Set(RState); // Clear the button
	}
	return DEVICE_OK;
}

// *****************************************************************************
//                  Color Value Change Handlers
// *****************************************************************************
// Color order in CIAColorLevels Array [Violet = 0, Blue=1, Cyan=2, Teal=3, Green=4, Yellow=5, Red=6]
int CIA::OnRedValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long ColorLevel;
   if (eAct == MM::AfterSet)
   {		
			LogMessage("In OnRedValue ActiveColor Before ");
			pProp->Get(ColorLevel);
			CIAColorLevels[CL_Red] = (unsigned char) (ColorLevel == 100) ? 0 : (ColorLevel == 0) ? 0xff : ((100 - ColorLevel) * 2.55);
			//OutputDebugString("In OnRedValue");
			SendColorLevelCmd(RED,ColorLevel);
   }
   return DEVICE_OK;

}
// *****************************************************************************
// On Green Value
// *****************************************************************************
int CIA::OnGreenValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long ColorLevel;
   if (eAct == MM::AfterSet)
   {
		//LogMessage("In OnGreenValue ");
		pProp->Get(ColorLevel);
		CIAColorLevels[CL_Green] = (unsigned char) (ColorLevel == 100) ? 0 : (ColorLevel == 0) ? 0xff : ((100 - ColorLevel) * 2.55);
		//OutputDebugString("In OnGreenValue");
		SendColorLevelCmd(GREEN,ColorLevel);
   }
   return DEVICE_OK;
}
// *****************************************************************************
// On Cyan Value
// *****************************************************************************
int CIA::OnCyanValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long ColorLevel;
   if (eAct == MM::AfterSet)
   {
		//LogMessage("In OnCYANValue ");
		pProp->Get(ColorLevel);
		CIAColorLevels[CL_Cyan] = (unsigned char) (ColorLevel == 100) ? 0 : (ColorLevel == 0) ? 0xff : ((100 - ColorLevel) * 2.55);
		//OutputDebugString("In OnGreenValue");
		SendColorLevelCmd(CYAN,ColorLevel);
   }
   return DEVICE_OK;
}
// *****************************************************************************
// On Violet Value
// *****************************************************************************
int CIA::OnVioletValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long ColorLevel;
   if (eAct == MM::AfterSet)
   {
		//LogMessage("In OnVioletValue ");
		pProp->Get(ColorLevel);
		CIAColorLevels[CL_Violet] = (unsigned char) (ColorLevel == 100) ? 0 : (ColorLevel == 0) ? 0xff : ((100 - ColorLevel) * 2.55);
		//OutputDebugString("In OnVioletValue");
        SendColorLevelCmd(VIOLET,ColorLevel);

   }

   return DEVICE_OK;
}
// *****************************************************************************
// On Teal Value
// *****************************************************************************
int CIA::OnTealValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long ColorLevel;
   if (eAct == MM::AfterSet)
   {
		//LogMessage("In OnTealValue ");
		pProp->Get(ColorLevel);
		CIAColorLevels[CL_Teal] = (unsigned char) (ColorLevel == 100) ? 0 : (ColorLevel == 0) ? 0xff : ((100 - ColorLevel) * 2.55);
		//OutputDebugString("In OnTealValue");
		SendColorLevelCmd(TEAL,ColorLevel);
   }
   return DEVICE_OK;
}
// *****************************************************************************
// On Blue Value
// *****************************************************************************
int CIA::OnBlueValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long ColorLevel;
   if (eAct == MM::AfterSet)
   {
		//LogMessage("In OnBlueValue ");
		pProp->Get(ColorLevel);
		CIAColorLevels[CL_Blue] = (unsigned char) (ColorLevel == 100) ? 0 : (ColorLevel == 0) ? 0xff : ((100 - ColorLevel) * 2.55);
		//OutputDebugString("In OnBlueValue");
		SendColorLevelCmd(BLUE,ColorLevel);
   }
   return DEVICE_OK;
}
//
// *****************************************************************************
//						Color Enable Change Handlers
// *****************************************************************************
// *****************************************************************************
// On Red Enable
// *****************************************************************************
int CIA::OnRedEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    string State;
	if (eAct == MM::AfterSet)
   {
	    pProp->Get(State);
		if (State == "1")
		{
			SendColorEnableCmd(RED,true, &EnableMask);
		}
		else
		{
			SendColorEnableCmd(RED,false, &EnableMask);
		}
		//LogMessage("In OnRedEnable ");
		//OutputDebugString("In OnRedEnable");
   }
   return DEVICE_OK;
}
// *****************************************************************************
// On Green Enable
// ****************************************************************************
int CIA::OnGreenEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
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
//		LogMessage("In OnGreenEnable ");
//		OutputDebugString("In OnGreenEnable");
   }
   return DEVICE_OK;
}
// *****************************************************************************
// On Cyan Enable
// ****************************************************************************
int CIA::OnCyanEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
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
//		LogMessage("In OnCyanEnable ");
//		OutputDebugString("In OnCyanEnable");
	}
   return DEVICE_OK;
}
// *****************************************************************************
// On Violet Enable
// ****************************************************************************
int CIA::OnVioletEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
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
//		LogMessage("In OnVioletEnable ");
//		OutputDebugString("In OnVioletEnable");
	}
   return DEVICE_OK;
}
// *****************************************************************************
// On Teal Enable
// ****************************************************************************
int CIA::OnTealEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
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
//		LogMessage("In OnTealEnable ");
//		OutputDebugString("In OnTealEnable");
	}
   return DEVICE_OK;
}
// *****************************************************************************
// On Blue Enable
// ****************************************************************************
int CIA::OnBlueEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
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
//		LogMessage("In OnBlueEnable ");
//		OutputDebugString("In OnBlueEnable");
	}
   return DEVICE_OK;
}


// *****************************************************************************
// On YGFilter Enable
// ****************************************************************************
int CIA::OnYGFilterEnable(MM::PropertyBase* pProp, MM::ActionType eAct)
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
//		LogMessage("In OnYGFilterEnable ");
//		OutputDebugString("In OnYGFilterEnable");
	}
   return DEVICE_OK;
}

