///////////////////////////////////////////////////////////////////////////////
// FILE:          Piezosystem_30DV50.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Piezosystem Jena device adapter.
//					30DV50 is a 1 channel device which can control diffrent actuators.
//					The actuator has a small memory with the values for the amplifier.
//					The controller has USB and RS232-interface.
//                
// AUTHOR:        Chris Belter, cbelter@piezojena.com 15/07/2013, ZStage and Shutter by Chris Belter
//                
//
// COPYRIGHT:     Piezosystem Jena, Germany, 2013
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES. 
//

//#include "stdafx.h"

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Piezosystem_30DV50.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string.h>
#include <math.h>
#include <algorithm>
#include <sstream>
#include <cstdio>


//Port-Control
const char* g_Mesg_Send_term ="\r";  //CR 
const char* g_Mesg_Receive_term ="\r\n";  //CR LF

// Controller
const char* g_Controller		= "PSJController";
const char* g_dDrive				= "PSJ_dDrive";
const char* g_30DV50				= "PSJ_30DV50";
const char* g_PSJ_Version		= "Version";
// single axis stage
const char* g_StageDeviceName = "PSJStage";
const char* g_PSJ_Axis_Id		= "PSJSingleAxisName";

const char* g_Shutter			= "PSJShutter";
const char* g_ShutterState		= "Shutter State";
const char* g_Open				= "open";
const char* g_Close				= "close";

const char* g_Version			= "Version (zero volt position)";
const char* g_Version1			= "edges open";
const char* g_Version2			= "edges closed";

//Controller properties
const char* cmdsmon				= "*SetA";			
const char* cmdsmoff			= "*SetA,1";
const char* g_bright			= "display bright";
//Stage properties
const char* g_Status			= "Status";
const char* g_Ktemp				= "Ktemp";
const char* g_Loop				= "Loop";

const char* g_Loop_open			= "open loop";
const char* g_Loop_close		= "close loop";

const char* g_Rohm				= "Rohm";
const char* g_Rgver				= "Rgver";
const char* g_Actuator			= "Actuator Name";


const char* g_Fenable			= "actuator soft start";
const char* g_Fenable_Off		= "soft start disable";
const char* g_Fenable_On		= "soft start enable";

const char* g_Sr					= "Slew rate";
const char* g_Modon				= "Modulation Input";
const char* g_Modon_On			= "on";
const char* g_Modon_Off			= "off";
const char* g_Monsrc				= "Monitor output";
const char* g_Monsrc_0			= "position in closed loop";
const char* g_Monsrc_1			= "command value";
const char* g_Monsrc_2			= "controller output voltage";
const char* g_Monsrc_3			= "closed loop deviation incl. sign";
const char* g_Monsrc_4			= "absolute closed loop deviation";
const char* g_Monsrc_5			= "actuator voltage";
const char* g_Monsrc_6			= "position in open loop";

const char* g_Limit_V_Min		= "Voltage min";
const char* g_Limit_V_Max		= "Voltage max";
const char* g_Limit_Um_Min		= "um min";
const char* g_Limit_Um_Max		= "um max";
const char* g_Voltage			= "Voltage";
const char* g_Position			= "Position";
const char* g_PID_P				= "PID kp";
const char* g_PID_I				= "PID ki";
const char* g_PID_D				= "PID kd";

const char* g_Notch				= "notch filter";
const char* g_Notch_On			= "on";
const char* g_Notch_Off			= "off";
const char* g_Notch_Freq		= "notch filter freqency";
const char* g_Notch_Band		= "notch bandwidth";

const char* g_Lowpass			= "low pass filter";
const char* g_Lowpass_On		= "on";
const char* g_Lowpass_Off		= "off";
const char* g_Lowpass_Freq		= "low pass filter freqency";

const char* g_Generator				= "generator";
const char* g_Generator_Off		= "off";
const char* g_Generator_Sine		= "sine";
const char* g_Generator_Tri		= "triangle";
const char* g_Generator_Rect		= "rectangle";
const char* g_Generator_Noise		= "noise";
const char* g_Generator_Sweep		= "sweep";
const char* g_Generator_Sine_Amp	= "sine amplitude";
const char* g_Generator_Sine_Offset	= "sine offset";
const char* g_Generator_Sine_Freq	= "sine freqency";
const char* g_Generator_Tri_Amp		= "triangle amplitude";
const char* g_Generator_Tri_Offset	= "triangle offset";
const char* g_Generator_Tri_Freq		= "triangle freqency";
const char* g_Generator_Tri_Sym		= "triangle symetry";
const char* g_Generator_Rect_Amp		= "rectangle amplitude";
const char* g_Generator_Rect_Offset	= "rectangle offset";
const char* g_Generator_Rect_Freq	= "rectangle freqency";
const char* g_Generator_Rect_Sym		= "rectangle symetry";
const char* g_Generator_Noise_Amp	= "noise amplitude";
const char* g_Generator_Noise_Offset= "noise offset";
const char* g_Generator_Sweep_Amp	= "sweep amplitude";
const char* g_Generator_Sweep_Offset= "sweep offset";
const char* g_Generator_Sweep_Time	= "sweep time";

const char* g_Scan_Type				= "scan type";
const char* g_Scan_Type_Off		= "scan function off";
const char* g_Scan_Type_Sine		= "sine scan";
const char* g_Scan_Type_Tri		= "triangle scan";
const char* g_Scan_Start			= "scan: start scan";
const char* g_Scan_Off				= "off";
const char* g_Scan_Starting		= "start scan";

const char* g_Trigger_Start		= "trigger start";
const char* g_Trigger_End			= "trigger end";
const char* g_Trigger_Interval	= "trigger intervals";
const char* g_Trigger_Time			= "trigger duration";
const char* g_Trigger_Generator = "trigger generation edge";
const char* g_Trigger_Off			= "trigger off";
const char* g_Trigger_Rising		= "trigger at rising edge";
const char* g_Trigger_Falling		= "trigger at falling edge";
const char* g_Trigger_Both			= "trigger at both edges";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{	 
	//RegisterDevice(g_30DV50, MM::HubDevice, "Piezosystem 30DV50 Controller");
	RegisterDevice(g_StageDeviceName, MM::StageDevice, "Single Axis Stage");
	RegisterDevice(g_Shutter, MM::ShutterDevice, "PSJ Shutter");
}             

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
	if (deviceName == 0)
      return 0;
	if (strcmp(deviceName, g_StageDeviceName) == 0){	  return new Stage();	}
////TODO:	  
	else if (strcmp(deviceName, g_Shutter) == 0 ) {      return new Shutter();   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

/*
 * Global Utility function for communication with the controller
 */
int clearPort(MM::Device& device, MM::Core& core, const char* port)
{
   // Clear contents of serial port
   const unsigned int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while (read == bufSize)
   {
      ret = core.ReadFromSerial(&device, port, clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

void splitString(char * string, const char* delimiter,char** dest){
  char * pch; 
  pch = strtok (string,delimiter); 
  int i=0;	
  while (pch != NULL)
  {
    //printf ("%s\n",pch);
	dest[i]=pch;
	i++;
    pch = strtok (NULL, delimiter);
  }  
}

/**
 * Single axis stage.
 */
Stage::Stage():
   initialized_(false),
   stepSizeUm_(0.1),   
   answerTimeoutMs_(2000),
   stat_(0),
   min_V_(-20.0),
   max_V_(130.0),
   min_um_(0.0),
   max_um_(80.0),
   pos_(0),
   rohm_(0),
   loop_(false)
   //port_("Undefined"),
{
	LogMessage ("new Stage");
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NO_CONTROLLER, "Please add the PSJController device first!");

   // create pre-initialization properties
   // ------------------------------------

   // Name
 //  CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "PSJ stage driver adapter", MM::String, true);
	
	// Port:
   CPropertyAction* pAct = new CPropertyAction(this, &Stage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

Stage::Stage(int channel) :
   initialized_(false),
   stepSizeUm_(0.1),  
   answerTimeoutMs_(2000),
	channel_(channel),
   stat_(0),
   min_V_(0.0),
   max_V_(100.0),
   min_um_(0.0),
   max_um_(50.0),
   pos_(0),
   rohm_(0),
   loop_(false)
   //port_("Undefined"),
{
	LogMessage ("new Stage");
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NO_CONTROLLER, "Please add the PSJController device first!");

   // create pre-initialization properties
   // ------------------------------------

   // Name
 //  CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "PSJ stage driver adapter", MM::String, true);

	// Port:
   CPropertyAction* pAct = new CPropertyAction(this, &Stage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

Stage::~Stage()
{
   Shutdown();
}

int Stage::GetVersion(std::string& version)
{
   int returnStatus = DEVICE_OK;
   
   PurgeComPort(port_.c_str());
   // Version of the controller:

   const char* cm = "";		//Get Version
   returnStatus = SendSerialCommand(port_.c_str(), cm, g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;

   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, version);  //"DSM V6.000\r"
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
   if (version.length() < 2) {
      // if we get no answer, try other port
      LogMessage("There is no device. Try other Port",true);   
	  // no answer, 
      return ERR_NO_ANSWER;
   }   
   return returnStatus;
}

bool Stage::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus Stage::DetectDevice(void)
{
   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
   try
   {
      std::string transformed = port_;
      for( std::string::iterator its = transformed.begin(); its != transformed.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      if( 0< transformed.length() &&  0 != transformed.compare("undefined")  && 0 != transformed.compare("unknown") )
      {
         // the port property seems correct, so give it a try
         result = MM::CanNotCommunicate;
         // device specific default communication parameters
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "115200" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_DataBits, "8");
			GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
			GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Parity, "None");  
			GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Software" );  
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_AnswerTimeout, "200.0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();
		   std::string v;
         int qvStatus = this->GetVersion(v);
         LogMessage(std::string("version : ")+v, true);
         if( DEVICE_OK != qvStatus )
         {
            LogMessageCode(qvStatus,true);
         }
         else
         {
            // to succeed must reach here....
            result = MM::CanCommunicate;
         }
         pS->Shutdown();         
      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }
   return result;
}
///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void Stage::GetName(char* Name) const
{  
   CDeviceUtils::CopyLimitedString(Name, g_StageDeviceName);
}

int Stage::Initialize()
{
	LogMessage ("PSJ Stage Init");
   // set property list
   // -----------------
   
   // Position
   // --------	
   CPropertyAction* pAct = new CPropertyAction (this, &Stage::OnStepSize);
   int ret = CreateProperty("StepSize", "1.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

	//LogMessage ("Property Status");
	pAct = new CPropertyAction (this, &Stage::OnStat);
   CreateProperty(g_Status, "0", MM::Integer, true, pAct);

	loop_=false;
   pAct = new CPropertyAction (this, &Stage::OnLoop);
   CreateProperty(g_Loop, "open loop", MM::String, false, pAct);
   AddAllowedValue(g_Loop, "open loop");
   AddAllowedValue(g_Loop, "close loop");

	ret = GetLimitsValues();  
	if (ret != DEVICE_OK)
      return ret;	
	

	//pAct = new CPropertyAction (this, &Stage::OnMinV);		
	CreateProperty(g_Limit_V_Min, CDeviceUtils::ConvertToString(min_V_), MM::Float, true);
	//pAct = new CPropertyAction (this, &Stage::OnMaxV);	;	
	CreateProperty(g_Limit_V_Max, CDeviceUtils::ConvertToString(max_V_), MM::Float, true);
	//pAct = new CPropertyAction (this, &Stage::OnMinUm);		
	CreateProperty(g_Limit_Um_Min,CDeviceUtils::ConvertToString(min_um_), MM::Float, true);
	//pAct = new CPropertyAction (this, &Stage::OnMaxUm);	
	CreateProperty(g_Limit_Um_Max, CDeviceUtils::ConvertToString(max_um_), MM::Float, true);
	
	pAct = new CPropertyAction (this, &Stage::OnVoltage);
	CreateProperty(g_Voltage, "0", MM::Float, true, pAct);
	SetPropertyLimits(g_Voltage, min_V_, max_V_);
	
	char n[20];
	ret = GetActuatorName(n);		
	if (ret != DEVICE_OK)
      return ret;	
	ac_name_=n;	
	CreateProperty(g_Actuator,ac_name_, MM::String, true);

	char s[20];
	GetRgver(rgver_);
	sprintf(s,"%i",rgver_);	
	CreateProperty(g_Rgver, s, MM::Integer, true);
	pAct = new CPropertyAction (this, &Stage::OnTime);
	CreateProperty(g_Rohm, "0", MM::Integer, true, pAct);
	pAct = new CPropertyAction (this, &Stage::OnTemp);
   CreateProperty(g_Ktemp, "0", MM::Float, true, pAct);

	pAct = new CPropertyAction (this, &Stage::OnSoftstart);
   CreateProperty(g_Fenable, g_Fenable_Off, MM::String, false, pAct);
   AddAllowedValue(g_Fenable , g_Fenable_Off);
   AddAllowedValue(g_Fenable, g_Fenable_On);

	pAct = new CPropertyAction (this, &Stage::OnSlewRate);
	CreateProperty(g_Sr, "10.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Sr, 0.0000002, 500.0);

	pAct = new CPropertyAction (this, &Stage::OnModulInput);
   CreateProperty(g_Modon, g_Modon_Off, MM::String, false, pAct);
   AddAllowedValue(g_Modon, g_Modon_Off);
   AddAllowedValue(g_Modon, g_Modon_On);

	pAct = new CPropertyAction (this, &Stage::OnMonitor);
   CreateProperty(g_Monsrc, g_Monsrc_0, MM::String, false, pAct);
   AddAllowedValue(g_Monsrc, g_Monsrc_0);
   AddAllowedValue(g_Monsrc, g_Monsrc_1);
	AddAllowedValue(g_Monsrc, g_Monsrc_2);
	AddAllowedValue(g_Monsrc, g_Monsrc_3);
	AddAllowedValue(g_Monsrc, g_Monsrc_4);
	AddAllowedValue(g_Monsrc, g_Monsrc_5);
	AddAllowedValue(g_Monsrc, g_Monsrc_6);
	
	pos_=0;
	pAct = new CPropertyAction (this, &Stage::OnPosition);
	CreateProperty(g_Position, "0", MM::Float, false, pAct);

	pAct = new CPropertyAction (this, &Stage::OnPidP);
	CreateProperty(g_PID_P, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_PID_P, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Stage::OnPidI);
	CreateProperty(g_PID_I, "0.0", MM::Float, false, pAct);
   SetPropertyLimits(g_PID_I, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Stage::OnPidD);
	CreateProperty(g_PID_D, "0.0", MM::Float, false, pAct);
   SetPropertyLimits(g_PID_D, 0.0, 999.0);

   //Notch Filter
   pAct = new CPropertyAction (this, &Stage::OnNotch);
    CreateProperty(g_Notch, g_Notch_Off, MM::String, false, pAct);
    AddAllowedValue(g_Notch, g_Notch_Off);
    AddAllowedValue(g_Notch, g_Notch_On);
   pAct = new CPropertyAction (this, &Stage::OnNotchFreq);
	CreateProperty(g_Notch_Freq, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_Notch_Freq, 0, 20000);
      pAct = new CPropertyAction (this, &Stage::OnNotchBand);
	CreateProperty(g_Notch_Band, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_Notch_Band, 0, 20000);
	//Low pass filter
    pAct = new CPropertyAction (this, &Stage::OnLowpass);
   CreateProperty(g_Lowpass, g_Lowpass_Off, MM::String, false, pAct);
    AddAllowedValue(g_Lowpass, g_Lowpass_Off);
    AddAllowedValue(g_Lowpass, g_Lowpass_On);
	pAct = new CPropertyAction (this, &Stage::OnLowpassFreq);
	CreateProperty(g_Lowpass_Freq, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_Lowpass_Freq, 0, 20000);

   //Internal function generator
   gfkt_=0;
	pAct = new CPropertyAction (this, &Stage::OnGenerate);
	CreateProperty(g_Generator, g_Generator_Off, MM::String, false, pAct);
	AddAllowedValue(g_Generator, g_Generator_Off);
	AddAllowedValue(g_Generator, g_Generator_Sine);
	AddAllowedValue(g_Generator, g_Generator_Tri);
	AddAllowedValue(g_Generator, g_Generator_Rect);
	AddAllowedValue(g_Generator, g_Generator_Noise);
	AddAllowedValue(g_Generator, g_Generator_Sweep);
	
	//Sine
	pAct = new CPropertyAction (this, &Stage::OnSinAmp);
	CreateProperty(g_Generator_Sine_Amp, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Stage::OnSinOff);
	CreateProperty(g_Generator_Sine_Offset, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_Offset, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Stage::OnSinFreq);
	CreateProperty(g_Generator_Sine_Freq, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_Freq, 0.00001, 9999.9);
	//triangle
	pAct = new CPropertyAction (this, &Stage::OnTriAmp);
	CreateProperty(g_Generator_Tri_Amp, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Stage::OnTriOff);
	CreateProperty(g_Generator_Tri_Offset, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Tri_Offset, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Stage::OnTriFreq);
	CreateProperty(g_Generator_Tri_Freq, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_Freq, 0.00001, 9999.9);
    pAct = new CPropertyAction (this, &Stage::OnTriSym);
	CreateProperty(g_Generator_Tri_Sym, "50.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_Sym, 0.0, 100.0);
	//rectangle
   pAct = new CPropertyAction (this, &Stage::OnRecAmp);
   CreateProperty(g_Generator_Rect_Amp, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Amp, 0.0, 100.0);
   pAct = new CPropertyAction (this, &Stage::OnRecOff);
	CreateProperty(g_Generator_Rect_Offset, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Offset, 0.0, 100.0);
   pAct = new CPropertyAction (this, &Stage::OnRecFreq);
	CreateProperty(g_Generator_Rect_Freq, "0.1", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Freq, 0.00001, 9999.9);
   pAct = new CPropertyAction (this, &Stage::OnRecSym);
	CreateProperty(g_Generator_Rect_Sym, "0.1", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Sym, 0.0, 100.0);
	//Noise
	pAct = new CPropertyAction (this, &Stage::OnNoiAmp);
	CreateProperty(g_Generator_Noise_Amp, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Noise_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Stage::OnNoiOff);
	CreateProperty(g_Generator_Noise_Offset, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Noise_Offset, 0.0, 100.0);
	//Sweep
    pAct = new CPropertyAction (this, &Stage::OnSweAmp);
	CreateProperty(g_Generator_Sweep_Amp, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sweep_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Stage::OnSweOff);
	CreateProperty(g_Generator_Sweep_Offset, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_Offset, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Stage::OnSweTime);
	CreateProperty(g_Generator_Sweep_Time, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_Time, 0.4, 800.0);
	
	//Scan
	pAct = new CPropertyAction (this, &Stage::OnScanType);
	CreateProperty(g_Scan_Type, g_Scan_Type_Off, MM::String, false, pAct);
	AddAllowedValue(g_Scan_Type, g_Scan_Type_Off);
	AddAllowedValue(g_Scan_Type, g_Scan_Type_Sine);
	AddAllowedValue(g_Scan_Type, g_Scan_Type_Tri);
	 pAct = new CPropertyAction (this, &Stage::OnScan);
	 CreateProperty(g_Scan_Start, g_Scan_Off, MM::String, false, pAct);
    AddAllowedValue(g_Scan_Start, g_Scan_Off);
    AddAllowedValue(g_Scan_Start, g_Scan_Starting);

	//trigger
    pAct = new CPropertyAction (this, &Stage::OnTriggerStart);
	CreateProperty(g_Trigger_Start, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Trigger_Start, max_um_*0.002, max_um_*0.998);
	pAct = new CPropertyAction (this, &Stage::OnTriggerEnd);
	CreateProperty(g_Trigger_End, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_End, max_um_*0.002, max_um_*0.998);
    pAct = new CPropertyAction (this, &Stage::OnTriggerInterval);
	CreateProperty(g_Trigger_Interval, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_Interval, min_um_, max_um_);
	pAct = new CPropertyAction (this, &Stage::OnTriggerTime);
	CreateProperty(g_Trigger_Time, "1", MM::Integer, false, pAct);
   SetPropertyLimits(g_Trigger_Time, 1, 255);
   pAct = new CPropertyAction (this, &Stage::OnTriggerType);
	CreateProperty(g_Trigger_Generator, g_Trigger_Off, MM::String, false, pAct);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Off);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Rising);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Falling);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Both);	
	
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int Stage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}


bool Stage::Busy()
{	
      return false;
}
// Stage API
int Stage::SetPositionUm(double pos){
	LogMessage ("SetPositionUm");
	int ret;
	
	if(loop_){ //Close loop
		pos_=pos;
		voltage_=(max_V_-min_V_)*(pos-min_um_)/(max_um_-min_um_)+min_V_; //Translate Pos->Voltage
		ret=SetCommandValue("set",pos_);
	}else{  //open loop
		pos_=pos;
		voltage_=(max_V_-min_V_)*(pos-min_um_)/(max_um_-min_um_)+min_V_; //Translate Pos->Voltage		
		ret=SetCommandValue("set",voltage_);
	}
	if (ret != DEVICE_OK)
      return ret;	

	CDeviceUtils::SleepMs(500);
	return DEVICE_OK;
}

int Stage::GetPositionUm(double& pos){
	LogMessage ("GetPositionUm");
	int ret;
	double d;
	ret = GetCommandValue("mess",d);
	if(loop_){
		pos=d;
		pos_=pos;
		voltage_=(max_V_-min_V_)*(pos_-min_um_)/(max_um_-min_um_)+min_V_;
	}else{
		voltage_=d;
		pos=(max_um_-min_um_)*(voltage_-min_V_)/(max_V_-min_V_)+min_um_;
		pos_=pos;
	}
	return DEVICE_OK;
}
int Stage::SetOrigin()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}
int Stage::GetLimits(double& min, double& max)
{	
	min=min_um_;
	max=max_um_;
	//return DEVICE_UNSUPPORTED_COMMAND;
	return DEVICE_OK;
}

int Stage::SendCommand(const char* cmd,std::string &result){
	int ret;	
	LogMessage (cmd,true);
	ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}

int Stage::SendServiceCommand(const char* cmd,std::string& result){	
	int ret = SendSerialCommand(port_.c_str(), cmdsmon, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;		
	ret = SendCommand(cmd, result);		
	ret = SendSerialCommand(port_.c_str(), cmdsmoff, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}
int Stage::GetCommandValue(const char* c,double& d){
	LogMessage ("Get command value d");

	char str[50]="";
	sprintf(str,"%s",c);	
	const char* cmd = str; 
	//LogMessage (cmd);
    int ret;
	std::string result;
	ret = SendCommand(cmd,result);	
	if (ret != DEVICE_OK)
		return ret;	
	if (result.length() < 1)
      return ERR_NO_ANSWER;
	//TODO: Error or no result
	LogMessage (result,true);
	char* dest[50];
	splitString((char*)result.c_str()," ,\n",dest);
	std::string type;
	type=dest[0];
	if(type==c){		
		d=atof(dest[1]);
	}else{
		LogMessage ("Wrong Result",true);	
		LogMessage (dest[0],true);
		d=0.0;
		ret =PurgeComPort(port_.c_str());
		CDeviceUtils::SleepMs(10);
	}		
	return DEVICE_OK;
}
int Stage::GetCommandValue(const char* c,int& i){
	LogMessage ("Get command value i",true);
	char cmd[50]="";
	sprintf(cmd,"%s",c);	
    int ret;
	std::string result;
	std::string type;
	ret = SendCommand(cmd,result);
	if (ret != DEVICE_OK)
		return ret;	
	if (result.length() < 1)
      return ERR_NO_ANSWER;
	LogMessage (result,true);

	char* dest[50];
	splitString((char*)result.c_str()," ,\n",dest);
	type=dest[0];
	if(type==c){
		i=atoi(dest[1]);
	}else{
		LogMessage ("Wrong Result",true);	
		LogMessage (dest[0],true);
		i=0;
		ret =PurgeComPort(port_.c_str());
		CDeviceUtils::SleepMs(10);
	}
	return ret;
}
int Stage::SetCommandValue(const char* c,double fkt){
	LogMessage ("Set command value d");
	char str[50]="";
	sprintf(str,"%s,%lf",c,fkt);	
	const char* cmd = str; 
	LogMessage (cmd);
    int ret;
    ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	//Normally no answer	
	return DEVICE_OK;
}

int Stage::SetCommandValue(const char* c,int fkt){
	LogMessage ("Set command value i");
	char str[50]="";
	sprintf(str,"%s,%d",c,fkt);	
	const char* cmd = str; 
	LogMessage (cmd);
    int ret;
    ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	//Normally no answer	
	return DEVICE_OK;
}
int Stage::GetLimitsValues(){
	LogMessage ("GetLimmitsValues");
	int ret;	
	LogMessage ("Set Service Mode");
	//Change to Service Mode
	ret = SendSerialCommand(port_.c_str(), cmdsmon, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	
	//Send Command
	//min_um_
	char s[20];
	std::string result;
	sprintf(s,"rdac,197");	
	const char* cmd = s; 
	ret = SendCommand(cmd, result);	
	LogMessage (cmd,true);
	LogMessage (result,true);
	char* dest[20];
	splitString((char*)result.c_str()," ,\n",dest);
	min_um_=atof(dest[2]);

	//max_um_	
	sprintf(s,"rdac,198");	
	cmd = s; 	
	ret = SendCommand(cmd, result);	
	LogMessage (cmd,true);
	LogMessage (result,true);
	splitString((char*)result.c_str()," ,\n",dest);
	max_um_=atof(dest[2]);

	//min_V_	
	sprintf(s,"rdac,199");	
	cmd = s; 
	ret = SendCommand(cmd, result);	
	LogMessage (cmd,true);
	LogMessage (result,true);
	splitString((char*)result.c_str()," ,\n",dest);
	min_V_=atof(dest[2]);

	//max_V_	
	sprintf(s,"rdac,200");	
	cmd = s;
	ret = SendCommand(cmd, result);	
	LogMessage (cmd,true);
	LogMessage (result,true);
	splitString((char*)result.c_str()," ,\n",dest);
	max_V_=atof(dest[2]);

	//Change back to User Mode
	LogMessage ("Set User Mode");
	ret = SendSerialCommand(port_.c_str(), cmdsmoff, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}

int Stage::GetStatus(int& stat){
	LogMessage ("GetStatus");
	
	const char* cmd = "stat"; 
	LogMessage (cmd);
    int ret;
	std::string str;
	std::string type;

	ret = SendCommand(cmd,str);    
	LogMessage (str);	
	char* dest[20];
	splitString((char*)str.c_str()," ,\n",dest);
	//If there is a Modul, look of the stat
	type=dest[0];
	std::size_t found;
	found=type.find("unit");
	if(found!=std::string::npos){
		LogMessage ("No Modul found");
		stat=0;
		return ERR_MODULE_NOT_FOUND;
	}
	found=type.find("stat");
	if(found!=std::string::npos){
		LogMessage ("Modul found");
		stat=atoi(dest[1]);
		stat_=stat;
		//if Bit4 is set, close loop
		loop_=((stat &CLOSE_LOOP)==CLOSE_LOOP)? true:false;
		//if Bit12 is set
		notchon_=((stat &NOTCH_FILTER_ON)==NOTCH_FILTER_ON)? true:false;
		//if Bit13 is set
		lpon_=((stat &LOW_PASS_FILTER_ON)==LOW_PASS_FILTER_ON)? true:false;
	}else{
		LogMessage ("ERROR ");
		LogMessage (dest[0]);
		stat=-1;
		//return ERR_MODULE_NOT_FOUND;
	}

	return DEVICE_OK;
}



int Stage::GetAxis(int& id){
	LogMessage ("GetAxis");
	std::string result;
	char s[20];
	sprintf(s,"rdac,5");	
	const char* cmd = s; 
	SendServiceCommand(cmd,result);
	LogMessage (result);
	char* dest[20];
	splitString((char*)result.c_str()," ,\n",dest);
	id = atoi(dest[2]);
	return DEVICE_OK;
}
int Stage::GetActuatorName(char* id){
	LogMessage ("GetActuatorName");
	std::string result;
	char s[20]="acdescr";	
	const char* cmd = s;
	SendServiceCommand(cmd,result);
	LogMessage(result);
	char* dest[30];
	splitString((char*)result.c_str()," ,\n",dest);	
	sprintf(id,"%s", dest[1]);	
	LogMessage(id);
	return DEVICE_OK;
}
int Stage::GetKtemp(double& ktemp){
	LogMessage ("GetKtemp");    
    int ret = GetCommandValue("ktemp",ktemp_);
	if (ret != DEVICE_OK)	  
      return ret;	
    ktemp=ktemp_;
   return DEVICE_OK;
}
int Stage::GetRohm(int& rohm){
	LogMessage ("GetRohm");    
    int ret = GetCommandValue("rohm",rohm_);
	if (ret != DEVICE_OK)	  
      return ret;	
    rohm=rohm_;
   return DEVICE_OK;
}
int Stage::GetRgver(int& rgver){
	LogMessage ("GetRgver");    
    int ret = GetCommandValue("rgver",rgver_);
	if (ret != DEVICE_OK)	  
      return ret;	
    rgver=rgver_;
   return DEVICE_OK;
}
int Stage::GetFenable(bool& b){	
    int ret;
	int i=0;
    ret = GetCommandValue("fenable",i);
	if (ret != DEVICE_OK)  
      return ret;	
	fenable_=(i==1)?true:false;
    b=fenable_;
   return DEVICE_OK;
}
int Stage::SetFenable(bool b){
	int l=(b)?1:0;
	int ret = SetCommandValue("fenable",l);
	fenable_=b;
	return ret;
}
int Stage::GetSr(double& d){    
   int ret = GetCommandValue("sr",sr_);
   d=sr_;
   return ret;
}
int Stage::SetSr(double d){	
	int ret = SetCommandValue("sr",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	sr_=d;
	return DEVICE_OK;
}
int Stage::GetModon(bool& b){	
    int ret;
	int i=0;
    ret = GetCommandValue("modon",i);
	if (ret != DEVICE_OK)  
      return ret;	
	modon_=(i==1)?true:false;
    b=modon_;
   return DEVICE_OK;
}
int Stage::SetModon(bool b){
	int l=(b)?1:0;
	int ret = SetCommandValue("modon",l);
	modon_=b;
	return ret;
}
int Stage::GetMonsrc(int& i){    
   int ret = GetCommandValue("monsrc",monsrc_);
   i=monsrc_;
   return ret;
}
int Stage::SetMonsrc(int i){	
	int ret = SetCommandValue("monsrc",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	monsrc_=i;
	return DEVICE_OK;
}
int Stage::GetLoop(bool& loop){
	LogMessage ("GetLoop");	
    int ret;
	int stat;    
	ret = GetCommandValue("stat",stat);
	if (ret != DEVICE_OK)
      return ret;
	loop_=((stat &CLOSE_LOOP)==CLOSE_LOOP)? true:false;
	loop=loop_;
	return DEVICE_OK;
}
int Stage::SetLoop(bool loop){
	LogMessage ("SetLoop");
	int i=(loop)?1:0;
	int ret = SetCommandValue("cl",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	loop_=loop;
	return DEVICE_OK;
}
int Stage::GetNotchon(bool& notch){
	LogMessage ("GetNotch");
	int n;
	int ret;
	ret = GetCommandValue("notchon",n);
	if (ret != DEVICE_OK)
      return ret;
	notch=(n==1)?true:false;	
	return DEVICE_OK;
}
int Stage::SetNotchon(bool notch){
	LogMessage ("SetNotch");
	int i=(notch)?1:0;
	int ret = SetCommandValue("notchon",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	notchon_=notch;
	return DEVICE_OK;
}
int Stage::GetNotchf(int& i){    
   int ret = GetCommandValue("notchf",notchf_);
   i=notchf_;
   return ret;
}
int Stage::SetNotchf(int i){	
	int ret = SetCommandValue("notchf",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	notchf_=i;
	//notch band = max. 2* notch freqency
	if(notchb_>=(2*notchf_)){
			notchb_=(2*notchf_);
			ret = SetNotchb(notchb_);
	}
	return DEVICE_OK;
}
int Stage::GetNotchb(int& i){    
   int ret = GetCommandValue("notchb",notchb_);
   i=notchb_;
   return ret;
}
int Stage::SetNotchb(int i){	
	int ret = SetCommandValue("notchb",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	notchb_=i;
	
	return DEVICE_OK;
}
int Stage::GetLpon(bool& lp){
	LogMessage ("GetLpon");
	int n;
	int ret;
	ret = GetCommandValue("lpon",n);
	if (ret != DEVICE_OK)
      return ret;
	lp=(n==1)?true:false;	
	return DEVICE_OK;
}
int Stage::SetLpon(bool lp){
	LogMessage ("SetLpon");
	int i=(lp)?1:0;
	int ret = SetCommandValue("lpon",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	lpon_=lp;
	return DEVICE_OK;
}
int Stage::GetLpf(int& i){    
   int ret = GetCommandValue("lpf",lpf_);
   i=lpf_;
   return ret;
}
int Stage::SetLpf(int i){	
	int ret = SetCommandValue("lpf",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	lpf_=i;	
	return DEVICE_OK;
}
int Stage::GetKp(double& d){    
   int ret = GetCommandValue("kp",kp_);
   d=kp_;
   return ret;
}
int Stage::SetKp(double d){	
	int ret = SetCommandValue("kp",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetKi(double& d){    
   int ret = GetCommandValue("ki",ki_);
   d=ki_;
   return ret;
}

int Stage::SetKi(double d){	
	int ret = SetCommandValue("ki",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetKd(double& d){    
   int ret = GetCommandValue("kd",kd_);
   d=kd_;
   return ret;
}
int Stage::SetKd(double d){	
	int ret = SetCommandValue("kd",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetGfkt(int& fkt){	
	int ret = GetCommandValue("gfkt",fkt);
	if (ret != DEVICE_OK)	  
      return ret;	
	gfkt_=fkt;
	return DEVICE_OK;
}
int Stage::SetGfkt(int fkt){
	LogMessage ("SetGfkt");
	if(-1<fkt && fkt< 6){
		int ret = SetCommandValue("gfkt",fkt);
		if (ret != DEVICE_OK){	  
			return ret;
		}
	}	
	return DEVICE_OK;
}

//sine
int Stage::GetGasin(double& d){
	int ret = GetCommandValue("gasin",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	gasin_=d;
	return DEVICE_OK;
}
int Stage::SetGasin(double d){
	LogMessage ("Set amp sine");
	int ret = SetCommandValue("gasin",d);	
	if (ret != DEVICE_OK){	  
      return ret;
	}
	gasin_=d;
	return DEVICE_OK;
}
int Stage::GetGosin(double& d){
	int ret = GetCommandValue("gosin",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	gosin_=d;
	return DEVICE_OK;
}
int Stage::SetGosin(double d){
	LogMessage ("Set Offset sine");	
	int ret = SetCommandValue("gosin",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	gosin_=d;
	return DEVICE_OK;
}
int Stage::GetGfsin(double& d){
	int ret = GetCommandValue("gfsin",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	gfsin_=d;
	return DEVICE_OK;
}
int Stage::SetGfsin(double d){
	LogMessage ("Set freqency sine");	
	int ret = SetCommandValue("gfsin",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	gfsin_=d;
	return DEVICE_OK;
}
//triangle
int Stage::GetGatri(double& d){
	int ret = GetCommandValue("gatri",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	gatri_=d;
	return DEVICE_OK;
}
int Stage::SetGatri(double d){
	LogMessage ("Set amp triangle");
	int ret = SetCommandValue("gatri",d);	
	if (ret != DEVICE_OK){	  
      return ret;
	}
	gatri_=d;
	return DEVICE_OK;
}
int Stage::GetGotri(double& d){
	int ret = GetCommandValue("gotri",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	gotri_=d;
	return DEVICE_OK;
}
int Stage::SetGotri(double d){
	LogMessage ("Set Offset triangle");	
	int ret = SetCommandValue("gotri",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	gotri_=d;
	return DEVICE_OK;
}
int Stage::GetGftri(double& d){
	int ret = GetCommandValue("gftri",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	gftri_=d;
	return DEVICE_OK;
}
int Stage::SetGftri(double d){
	LogMessage ("Set freqency triangle");	
	int ret = SetCommandValue("gftri",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	gftri_=d;
	return DEVICE_OK;
}
int Stage::GetGstri(double& d){
	int ret = GetCommandValue("gstri",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	gstri_=d;
	return DEVICE_OK;
}
int Stage::SetGstri(double d){
	LogMessage ("Set Symmetrie triangle");	
	int ret = SetCommandValue("gstri",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	gstri_=d;
	return DEVICE_OK;
}


//rectangle
int Stage::GetGarec(double& d){
	int ret = GetCommandValue("garec",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	garec_=d;
	return DEVICE_OK;
}
int Stage::SetGarec(double d){
	LogMessage ("Set amp rectangle");
	int ret = SetCommandValue("garec",d);	
	if (ret != DEVICE_OK){	  
      return ret;
	}
	garec_=d;
	return DEVICE_OK;
}
int Stage::GetGorec(double& d){
	int ret = GetCommandValue("gorec",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	gorec_=d;
	return DEVICE_OK;
}
int Stage::SetGorec(double d){
	LogMessage ("Set Offset rectangle");	
	int ret = SetCommandValue("gorec",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	gorec_=d;
	return DEVICE_OK;
}
int Stage::GetGfrec(double& d){
	int ret = GetCommandValue("gfrec",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	gfrec_=d;
	return DEVICE_OK;
}
int Stage::SetGfrec(double d){
	LogMessage ("Set freqency rectangle");	
	int ret = SetCommandValue("gfrec",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	gfrec_=d;
	return DEVICE_OK;
}
int Stage::GetGsrec(double& d){
	int ret = GetCommandValue("gsrec",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	gsrec_=d;
	return DEVICE_OK;
}
int Stage::SetGsrec(double d){
	LogMessage ("Set Symmetrie rectangle");	
	int ret = SetCommandValue("gsrec",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	gsrec_=d;
	return DEVICE_OK;
}


//noise
int Stage::GetGanoi(double& d){
	int ret = GetCommandValue("ganoi",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	ganoi_=d;
	return DEVICE_OK;
}
int Stage::SetGanoi(double d){
	LogMessage ("Set amp noise");
	int ret = SetCommandValue("ganoi",d);	
	if (ret != DEVICE_OK){	  
      return ret;
	}
	ganoi_=d;
	return DEVICE_OK;
}
int Stage::GetGonoi(double& d){
	int ret = GetCommandValue("gonoi",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	gonoi_=d;
	return DEVICE_OK;
}
int Stage::SetGonoi(double d){
	LogMessage ("Set Offset noise");	
	int ret = SetCommandValue("gonoi",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	gonoi_=d;
	return DEVICE_OK;
}
//sweep
int Stage::GetGaswe(double& d){
	int ret = GetCommandValue("gaswe",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	gaswe_=d;
	return DEVICE_OK;
}
int Stage::SetGaswe(double d){
	LogMessage ("Set amp sweep");
	int ret = SetCommandValue("gaswe",d);	
	if (ret != DEVICE_OK){	  
      return ret;
	}
	gaswe_=d;
	return DEVICE_OK;
}
int Stage::GetGoswe(double& d){
	int ret = GetCommandValue("goswe",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	goswe_=d;
	return DEVICE_OK;
}
int Stage::SetGoswe(double d){
	LogMessage ("Set Offset sweep");	
	int ret = SetCommandValue("goswe",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	goswe_=d;
	return DEVICE_OK;
}
int Stage::GetGtswe(double& d){
	int ret = GetCommandValue("gtswe",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	gtswe_=d;
	return DEVICE_OK;
}
int Stage::SetGtswe(double d){
	LogMessage ("Set time sweep");	
	int ret = SetCommandValue("gtswe",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	gtswe_=d;
	return DEVICE_OK;
}
//Scan
int Stage::GetScanType(int& i){    
   int ret = GetCommandValue("sct",sct_);
   i=sct_;
   return ret;
}
int Stage::SetScanType(int i){	
	int ret = SetCommandValue("sct",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	sct_=i;
	return DEVICE_OK;
}
int Stage::GetScan(bool& b){   
   int i;
   int ret = GetCommandValue("ss",i);
   ss_=(i==1)?true:false;
   b=ss_;
   return ret;
}
int Stage::SetScan(bool b){	
	if (b){
	   int i=(b)?1:0;
	   int ret = SetCommandValue("ss",i);	
	   if (ret != DEVICE_OK)	  
         return ret;	
	}
	return DEVICE_OK;
}
int Stage::GetTrgss(double& d){    
   int ret = GetCommandValue("trgss",trgss_);
   d=trgss_;
   return ret;
}
int Stage::SetTrgss(double d){	
	int ret = SetCommandValue("trgss",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetTrgse(double& d){    
   int ret = GetCommandValue("trgse",trgse_);
   d=trgse_;
   return ret;
}

int Stage::SetTrgse(double d){	
	int ret = SetCommandValue("trgse",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetTrgsi(double& d){    
   int ret = GetCommandValue("trgsi",trgsi_);
   d=trgsi_;
   return ret;
}

int Stage::SetTrgsi(double d){	
	int ret = SetCommandValue("trgsi",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetTrglen(int& i){    
   int ret = GetCommandValue("trglen",trglen_);
   i=trglen_;
   return ret;
}

int Stage::SetTrglen(int i){	
	int ret = SetCommandValue("trglen",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	trglen_=i;
	return DEVICE_OK;
}
int Stage::GetTrgedge(int& i){    
   int ret = GetCommandValue("trgedge",trgedge_);
   i=trgedge_;
   return ret;
}

int Stage::SetTrgedge(int i){	
	int ret = SetCommandValue("trgedge",i);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}



int Stage::GetSine(){
	LogMessage ("Get sine");	
	int ret=0;	
	ret = GetCommandValue("gasin",gasin_);
	if (ret != DEVICE_OK){
		return ret;
	}    
	ret = GetCommandValue("gosin",gosin_);
	if (ret != DEVICE_OK){
		return ret;
	}
	ret = GetCommandValue("gfsin",gfsin_);
	if (ret != DEVICE_OK){
		return ret;
	}
	return DEVICE_OK;
}

int Stage::GetDouble(const char * cmd,double& value ){	
    int ret;
	LogMessage ("Get Double");
	LogMessage (cmd);
    ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK){
		return ret;
	}
	std::string result;
	ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
	if (ret != DEVICE_OK){
		return ret;
	}
	LogMessage (result);
	char* dest[50];
	splitString((char*)result.c_str()," ,\n",dest);
	LogMessage (dest[2]);
	value=atof(dest[2]);
	
	return DEVICE_OK;
}

int Stage::SetPidDefault(){
	const char* cmd = "sstd"; 
	LogMessage (cmd);
    int ret;
    ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK){
		return ret;
	}
	std::string result;
	ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
	if (ret != DEVICE_OK){
		char msg[50]="PID Error ";
		sprintf(msg,"PID Error %d",ret);
		LogMessage (msg);
		return ret;
	}
	LogMessage (result);
	return DEVICE_OK;
}



///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
int Stage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{	
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <=0.0)
      {
         pProp->Set(stepSizeUm_);
         return ERR_INVALID_STEP_SIZE;
      }
      stepSizeUm_ = stepSize;
   }
   return DEVICE_OK;
}

int Stage::OnID(MM::PropertyBase* pProp, MM::ActionType eAct)
{	
   if (eAct == MM::BeforeGet)
   {      
      pProp->Set(id_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      string id;
      pProp->Get(id_);     
   }

   return DEVICE_OK;
}
int Stage::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
      pProp->Get(port_);
   }
   return DEVICE_OK;
}
int Stage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){	
		double d;
		int ret = GetPositionUm(d);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(d);
	}
    else if (eAct == MM::AfterSet){
		int ret=0;
		pProp->Get(pos_);		
		ret = SetPositionUm(pos_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}

int Stage::OnStat(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){
		int i;
		int ret=GetStatus(i);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)stat_);
	}
	return DEVICE_OK;
}
int Stage::OnTemp(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){
		int ret = GetKtemp(ktemp_);		
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set(ktemp_);
	}
	return DEVICE_OK;
}
int Stage::OnTime(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetRohm(rohm_);
		//int ret = GetCommandValue("rohm",rohm_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)rohm_);
	}
	return DEVICE_OK;
}
int Stage::OnSoftstart(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){		
		int ret=GetFenable(fenable_);		
		if (ret!=DEVICE_OK)
			return ret;
		if (fenable_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			fenable_ = true;
		}else{
			fenable_ = false;
		}
		int ret = SetFenable(fenable_);	  
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnSlewRate(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetSr(sr_);
		//ret = GetCommandValue("sr",sr_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)sr_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(sr_);		
		//int ret = SetCommandValue("sr",sr_);
		ret = SetSr(sr_);
		if (ret != DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnModulInput(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {		
		int ret = GetModon(modon_);
		//int ret = GetCommandValue("modon",l);
		//modon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (modon_){
			pProp->Set(g_Modon_On);			
		}
		else{
			pProp->Set(g_Modon_Off);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string modon;
      pProp->Get(modon);
      if (modon == g_Modon_On)
         modon_ = true;
      else
         modon_ = false;
	  int ret = SetModon(modon_);
	  //l=(modon_)?1:0;
	  //int ret = SetCommandValue("modon",l);	  
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnMonitor(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int m=0;
	if (eAct == MM::BeforeGet)
    {		
		int ret = GetMonsrc(monsrc_);
		//int ret = GetCommandValue("monsrc",m);
		if (ret!=DEVICE_OK)
			return ret;
		m=monsrc_;		
		switch (m){
		case 0:
			pProp->Set(g_Monsrc_0);	break;
		case 1:
			pProp->Set(g_Monsrc_1);	break;
		case 2:
			pProp->Set(g_Monsrc_2);	break;
		case 3:
			pProp->Set(g_Monsrc_3);	break;
		case 4:
			pProp->Set(g_Monsrc_4);	break;
		case 5:
			pProp->Set(g_Monsrc_5);	break;
		case 6:
			pProp->Set(g_Monsrc_6);	break;
		default:
			pProp->Set(g_Monsrc_0);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string mon;
		pProp->Get(mon);
		if (mon == g_Monsrc_0)
         monsrc_ = 0;
		else if (mon == g_Monsrc_1)
         monsrc_ = 1;
		else if (mon == g_Monsrc_2)
         monsrc_ = 2;
		else if (mon == g_Monsrc_3)
         monsrc_ = 3;
		else if (mon == g_Monsrc_4)
         monsrc_ = 4;
		else if (mon == g_Monsrc_5)
         monsrc_ = 5;	
		else if (mon == g_Monsrc_6)
         monsrc_ = 6;	
		int ret = SetMonsrc(monsrc_);
		//int ret = SetCommandValue("monsrc",monsrc_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){	
		pProp->Set(voltage_);
	}
	return DEVICE_OK;
}
int Stage::OnMinV(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(min_V_);
	} 
	else if (eAct == MM::AfterSet)
   {
		pProp->Get(min_V_);
	}
	return DEVICE_OK;
}

int Stage::OnMaxV(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(max_V_);
	}
	else if (eAct == MM::AfterSet)
   {
		pProp->Get(max_V_);
	}
	return DEVICE_OK;
}

int Stage::OnMinUm(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(min_um_);
	}
	else if (eAct == MM::AfterSet)
   {
		pProp->Get(min_um_);
	}
	return DEVICE_OK;
}
int Stage::OnMaxUm(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(max_um_);
	}
	else if (eAct == MM::AfterSet)
   {
		pProp->Get(max_um_);
	}
	return DEVICE_OK;
}
int Stage::OnLoop(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {		
		int ret=GetLoop(loop_);
		if (ret!=DEVICE_OK)
			return ret;
		if (loop_){
			pProp->Set("close loop");
			l=1;
		}
		else{
			pProp->Set("open loop");
			l=0;
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string loop;
      pProp->Get(loop);
      if (loop == "close loop")
         loop_ = true;
      else
         loop_ = false;
	  int ret = SetLoop(loop_);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}

int Stage::OnPidP(MM::PropertyBase* pProp, MM::ActionType eAct){		
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetKp(kp_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)kp_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(kp_);		
		ret = SetKp(kp_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnPidI(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetKi(ki_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)ki_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(ki_);		 
		ret = SetKi(ki_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}

int Stage::OnPidD(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{
		ret = GetKd(kd_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)kd_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(kd_);		
		ret = SetKd(kd_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnNotch(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet){
		int ret=GetNotchon(notchon_);
		if (ret!=DEVICE_OK)
			return ret;
		if (notchon_){
			pProp->Set(g_Notch_On);			
		}
		else{
			pProp->Set(g_Notch_Off);			
		}		
	}else if (eAct == MM::AfterSet){	  
	  std::string notch;
      pProp->Get(notch);
	  if (notch == g_Notch_On){
         notchon_ = true;
	  }else{
         notchon_ = false;
	  }
	  int ret = SetNotchon(notchon_);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}

int Stage::OnNotchFreq(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet){		
		int ret = GetNotchf(notchf_);	
		//int ret = GetCommandValue("notchf",notchf_);
		if (ret != DEVICE_OK)
			return ret;
		SetPropertyLimits(g_Notch_Band, 0, ((2*notchf_)<20000)?(2*notchf_):20000);
		c=notchf_;
		pProp->Set(c);
	}else if (eAct == MM::AfterSet){
		pProp->Get(c);
		notchf_=(int)c;
		int ret = SetNotchf(notchf_);
		//int ret = SetCommandValue("notchf",notchf_);
		if (ret!=DEVICE_OK)
			return ret;
		//set limit bandwidth to max 2*notch_frequency
		SetPropertyLimits(g_Notch_Band, 0, ((2*notchf_)<20000)?(2*notchf_):20000);		
		//CDeviceUtils::SleepMs(500);
	}
    return DEVICE_OK;
}
int Stage::OnNotchBand(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet){		
		//int ret = GetCommandValue("notchb",notchb_);
		int ret = GetNotchb(notchb_);
		if (ret != DEVICE_OK)
			return ret;
		c=notchb_;
		pProp->Set(c);
	}else if (eAct == MM::AfterSet){
		pProp->Get(c);
		notchb_=(int)c;
		//int ret = SetCommandValue("notchb",notchb_);
		int ret = SetNotchb(notchb_);
		if (ret!=DEVICE_OK)
			return ret;
		
	}
    return DEVICE_OK;
}
int Stage::OnLowpass(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){		
		//int ret = GetCommandValue("lpon",l);
		//lpon_=(l==1)?true:false;
		int ret = GetLpon(lpon_);		
		if (ret!=DEVICE_OK)
			return ret;
		if (lpon_){
			pProp->Set(g_Lowpass_On);			
		}else{
			pProp->Set(g_Lowpass_Off);			
		}		
	}else if (eAct == MM::AfterSet){
	  std::string lpon;
      pProp->Get(lpon);
	  if (lpon == g_Lowpass_On){
         lpon_ = true;
	  }else{
         lpon_ = false;
	  }
	  int ret = SetLpon(lpon_);
	  //	l=(lpon_)?1:0;
	  //int ret = SetCommandValue("lpon",l);	
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnLowpassFreq(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet){		
		//int ret = GetCommandValue("lpf",lpf_);
		int ret = GetLpf(lpf_);
		if (ret != DEVICE_OK)
			return ret;		
		c=lpf_;
		pProp->Set(c);
	}else if (eAct == MM::AfterSet){
		pProp->Get(c);
		lpf_=(int)c;
		//int ret = SetCommandValue("lpf",lpf_);
		int ret = SetLpf(lpf_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnGenerate(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){		
		//LogMessage ("BeforeGet ");
		int i;
		int ret=GetStatus(i);
		if (ret!=DEVICE_OK)
			return ret;
		gfkt_=(stat_&GENERATOR_OFF_MASK)>>9;		
		switch (gfkt_){
		case 0:
			pProp->Set(g_Generator_Off);		break;
		case 1:
			pProp->Set(g_Generator_Sine);		break;
		case 2:
			pProp->Set(g_Generator_Tri);		break;
		case 3:
			pProp->Set(g_Generator_Rect);	break;
		case 4:
			pProp->Set(g_Generator_Noise);	break;
		case 5:
			pProp->Set(g_Generator_Sweep);	break;
		default:
			pProp->Set(g_Generator_Off);
		}
	}else if (eAct == MM::AfterSet){
		LogMessage ("AfterSet ");
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Generator_Off)
         gfkt_ = 0;
		else if (gen == g_Generator_Sine)
         gfkt_ = 1;
		else if (gen == g_Generator_Tri)
         gfkt_ = 2;
		else if (gen == g_Generator_Rect)
         gfkt_ = 3;
		else if (gen == g_Generator_Noise)
         gfkt_ = 4;
		else if (gen == g_Generator_Sweep)
         gfkt_ = 5;		
		int ret = SetGfkt(gfkt_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnSinAmp(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{		
		//int ret = GetCommandValue("gasin",gasin_);
		int ret = GetGasin(gasin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gasin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gasin_);
		int ret = SetGasin(gasin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}

int Stage::OnSinOff(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{		
        ret = GetGosin(gosin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gosin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gosin_);
		ret = SetGosin(gosin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}

int Stage::OnSinFreq(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGfsin(gfsin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gfsin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gfsin_);
		int ret = SetGfsin(gfsin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnTriAmp(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetGatri(gatri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gatri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gatri_);
		//int ret = SetCommandValue("gatri",gatri_);
		int ret = SetGatri(gatri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}

int Stage::OnTriOff(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGotri(gotri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gotri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gotri_);
		//int ret = SetCommandValue("gotri",gotri_);
		int ret = SetGotri(gotri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnTriFreq(MM::PropertyBase* pProp, MM::ActionType eAct){
		
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGftri(gftri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gftri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gftri_);
		//int ret = SetCommandValue("gftri",gftri_);
		int ret = SetGftri(gftri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnTriSym(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGstri(gstri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gstri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gstri_);
		//int ret = SetCommandValue("gstri",gstri_);
		int ret = SetGstri(gstri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnRecAmp(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGarec(garec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(garec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(garec_);
		//int ret = SetCommandValue("garec",garec_);
		int ret = SetGarec(garec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnRecOff(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGorec(gorec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gorec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gorec_);
		//ret = SetCommandValue("gorec",gorec_);
		int ret = SetGorec(gorec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
   return DEVICE_OK;
}
int Stage::OnRecFreq(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGfrec(gfrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gfrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gfrec_);
		//int ret = SetCommandValue("gfrec",gfrec_);
		int ret = SetGfrec(gfrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
   return DEVICE_OK;
}
int Stage::OnRecSym(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGsrec(gsrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gsrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gsrec_);
		//int ret = SetCommandValue("gsrec",gsrec_);
		int ret = SetGsrec(gsrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
   return DEVICE_OK;
}
int Stage::OnNoiAmp(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetGanoi(ganoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(ganoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(ganoi_);
		int ret = SetGanoi(ganoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
   return DEVICE_OK;
}

int Stage::OnNoiOff(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGonoi(gonoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gonoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gonoi_);
		ret = SetGonoi(gonoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
   return DEVICE_OK;
}
int Stage::OnSweAmp(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGaswe(gaswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gaswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gaswe_);
		int ret = SetGaswe(gaswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
   return DEVICE_OK;
}
int Stage::OnSweOff(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGoswe(goswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(goswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(goswe_);
		int ret = SetGoswe(goswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
   return DEVICE_OK;
}

int Stage::OnSweTime(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGtswe(gtswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gtswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gtswe_);
		int ret = SetGtswe(gtswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
   return DEVICE_OK;
}
int Stage::OnScanType(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {			
		int ret=GetScanType(sct_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (sct_){
		case 0:
			pProp->Set(g_Scan_Type_Off);break;
		case 1:
			pProp->Set(g_Scan_Type_Sine);		break;
		case 2:
			pProp->Set(g_Scan_Type_Tri);	break;		
		default:
			pProp->Set(g_Scan_Type_Off);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Scan_Type_Off)
         sct_ = 0;
		else if (gen == g_Scan_Type_Sine)
         sct_ = 1;
		else if (gen == g_Scan_Type_Tri)
         sct_ = 2;		
		int ret = SetScanType(sct_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
   return DEVICE_OK;
}

int Stage::OnScan(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
   {			
		int ret=GetScan(ss_);
		if (ret!=DEVICE_OK)
			return ret;			
		if(ss_){
			pProp->Set(g_Scan_Starting);
		}else{
			pProp->Set(g_Scan_Off);	
		}
	}
   else if (eAct == MM::AfterSet)
   {		
		std::string s;
		pProp->Get(s);
		if (s == g_Scan_Off)
         ss_ = false;
		else if (s == g_Scan_Starting)
         ss_ = true;			
		int ret = SetScan(ss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
   return DEVICE_OK;
}
int Stage::OnTriggerStart(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){			
		int ret=GetTrgss(trgss_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(trgss_);		
	}else if (eAct == MM::AfterSet){		
		pProp->Get(trgss_);					
		int ret = SetTrgss(trgss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnTriggerEnd(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
   {			
		int ret=GetTrgse(trgse_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(trgse_);		
	}
   else if (eAct == MM::AfterSet)
   {		
		pProp->Get(trgse_);					
		int ret = SetTrgse(trgse_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
	return DEVICE_OK;
}
int Stage::OnTriggerInterval(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
   {			
		int ret=GetTrgsi(trgsi_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(trgsi_);		
	}
   else if (eAct == MM::AfterSet)
   {		
		pProp->Get(trgsi_);					
		int ret = SetTrgsi(trgsi_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnTriggerTime(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
   {			
		int ret=GetTrglen(trglen_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set((long)trglen_);		
	}
   else if (eAct == MM::AfterSet)
   {	
		double d;
		pProp->Get(d);
		trglen_=(int)d;
		int ret = SetTrglen(trglen_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnTriggerType(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
   {			
		int ret=GetTrgedge(trgedge_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (trgedge_){
		case 0:
			pProp->Set(g_Trigger_Off);		break;
		case 1:
			pProp->Set(g_Trigger_Rising);	break;
		case 2:
			pProp->Set(g_Trigger_Falling);	break;	
		case 3:
			pProp->Set(g_Trigger_Both);		break;
		default:
			pProp->Set(g_Trigger_Off);
		}	
	}
    else if (eAct == MM::AfterSet)
    {			
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Trigger_Off)
         trgedge_ = 0;
		else if (gen == g_Trigger_Rising)
         trgedge_ = 1;
		else if (gen == g_Trigger_Falling)
         trgedge_ = 2;	
		else if (gen == g_Trigger_Both)
         trgedge_ = 3;	
		int ret = SetTrgedge(trgedge_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Shutter 
// ~~~~~~~

Shutter::Shutter() : 
   name_(g_Shutter),     
   initialized_(false),
   answerTimeoutMs_(2000),
	stat_(0),
   min_V_(-20.0),
   max_V_(130.0),
   min_um_(0.0),
   max_um_(50.0),
   pos_(0),
   rohm_(0),
   loop_(false)
  
{
	InitializeDefaultErrorMessages();

	CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnVersion);
	CreateProperty(g_Version, g_Version1, MM::String, false, pAct,true);
	AddAllowedValue(g_Version, g_Version1);
	AddAllowedValue(g_Version, g_Version2);

	// Port:
   pAct = new CPropertyAction(this, &Shutter::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}
Shutter::~Shutter()
{
   //shuttersUsed[deviceNumber_ - 1][shutterNumber_ - 1] = false;
   Shutdown();
}

void Shutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Shutter::Initialize()
{
	pos_=0;
	//LogMessage ("Property Status");
	CPropertyAction* pAct = new CPropertyAction (this, &Shutter::OnStat);
   CreateProperty(g_Status, "0", MM::Integer, true, pAct);

	loop_=false;
   pAct = new CPropertyAction (this, &Shutter::OnLoop);
   CreateProperty(g_Loop, g_Loop_open, MM::String, false, pAct);
   AddAllowedValue(g_Loop, g_Loop_open);
   AddAllowedValue(g_Loop, g_Loop_close);

	int	ret = GetLimitsValues();  
	if (ret != DEVICE_OK)
      return ret;	

			
	CreateProperty(g_Limit_V_Min, CDeviceUtils::ConvertToString(min_V_), MM::Float, true);		
	CreateProperty(g_Limit_V_Max, CDeviceUtils::ConvertToString(max_V_), MM::Float, true);			
	CreateProperty(g_Limit_Um_Min,CDeviceUtils::ConvertToString(min_um_), MM::Float, true);		
	CreateProperty(g_Limit_Um_Max, CDeviceUtils::ConvertToString(max_um_), MM::Float, true);
	
	pAct = new CPropertyAction (this, &Shutter::OnVoltage);
	CreateProperty(g_Voltage, "0", MM::Float, true, pAct);
	SetPropertyLimits(g_Voltage, min_V_, max_V_);

	pAct = new CPropertyAction (this, &Shutter::OnState);
	ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
	AddAllowedValue(MM::g_Keyword_State, "0");
	AddAllowedValue(MM::g_Keyword_State, "1");
	if (ret != DEVICE_OK)
      return ret;
	
	pAct = new CPropertyAction (this, &Shutter::OnShutterState);
	CreateProperty(g_ShutterState, g_Open, MM::String, false, pAct);
	AddAllowedValue(g_ShutterState, g_Open);
	AddAllowedValue(g_ShutterState, g_Close);

	char n[20];
	ret = GetActuatorName(n);		
	if (ret != DEVICE_OK)
      return ret;	
	ac_name_=n;	
	CreateProperty(g_Actuator,ac_name_, MM::String, true);

	char s[20];
	GetRgver(rgver_);
	sprintf(s,"%i",rgver_);	
	CreateProperty(g_Rgver, s, MM::Integer, true);
	pAct = new CPropertyAction (this, &Shutter::OnTime);
	CreateProperty(g_Rohm, "0", MM::Integer, true, pAct);
	pAct = new CPropertyAction (this, &Shutter::OnTemp);
   CreateProperty(g_Ktemp, "0", MM::Float, true, pAct);

	pAct = new CPropertyAction (this, &Shutter::OnSoftstart);
   CreateProperty(g_Fenable, g_Fenable_Off, MM::String, false, pAct);
   AddAllowedValue(g_Fenable , g_Fenable_Off);
   AddAllowedValue(g_Fenable, g_Fenable_On);

	pAct = new CPropertyAction (this, &Shutter::OnSlewRate);
	CreateProperty(g_Sr, "10.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Sr, 0.0000002, 500.0);

	pAct = new CPropertyAction (this, &Shutter::OnModulInput);
   CreateProperty(g_Modon, g_Modon_Off, MM::String, false, pAct);
   AddAllowedValue(g_Modon, g_Modon_Off);
   AddAllowedValue(g_Modon, g_Modon_On);

	pAct = new CPropertyAction (this, &Shutter::OnMonitor);
   CreateProperty(g_Monsrc, g_Monsrc_0, MM::String, false, pAct);
   AddAllowedValue(g_Monsrc, g_Monsrc_0);
   AddAllowedValue(g_Monsrc, g_Monsrc_1);
	AddAllowedValue(g_Monsrc, g_Monsrc_2);
	AddAllowedValue(g_Monsrc, g_Monsrc_3);
	AddAllowedValue(g_Monsrc, g_Monsrc_4);
	AddAllowedValue(g_Monsrc, g_Monsrc_5);
	AddAllowedValue(g_Monsrc, g_Monsrc_6);
	
	pAct = new CPropertyAction (this, &Shutter::OnPidP);
	CreateProperty(g_PID_P, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_PID_P, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Shutter::OnPidI);
	CreateProperty(g_PID_I, "0.0", MM::Float, false, pAct);
   SetPropertyLimits(g_PID_I, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Shutter::OnPidD);
	CreateProperty(g_PID_D, "0.0", MM::Float, false, pAct);
   SetPropertyLimits(g_PID_D, 0.0, 999.0);

   //Notch Filter
   pAct = new CPropertyAction (this, &Shutter::OnNotch);
    CreateProperty(g_Notch, g_Notch_Off, MM::String, false, pAct);
    AddAllowedValue(g_Notch, g_Notch_Off);
    AddAllowedValue(g_Notch, g_Notch_On);
   pAct = new CPropertyAction (this, &Shutter::OnNotchFreq);
	CreateProperty(g_Notch_Freq, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_Notch_Freq, 0, 20000);
      pAct = new CPropertyAction (this, &Shutter::OnNotchBand);
	CreateProperty(g_Notch_Band, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_Notch_Band, 0, 20000);
	//Low pass filter
    pAct = new CPropertyAction (this, &Shutter::OnLowpass);
   CreateProperty(g_Lowpass, g_Lowpass_Off, MM::String, false, pAct);
    AddAllowedValue(g_Lowpass, g_Lowpass_Off);
    AddAllowedValue(g_Lowpass, g_Lowpass_On);
	pAct = new CPropertyAction (this, &Shutter::OnLowpassFreq);
	CreateProperty(g_Lowpass_Freq, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_Lowpass_Freq, 0, 20000);

   //Internal function generator
   gfkt_=0;
	pAct = new CPropertyAction (this, &Shutter::OnGenerate);
	CreateProperty(g_Generator, g_Generator_Off, MM::String, false, pAct);
	AddAllowedValue(g_Generator, g_Generator_Off);
	AddAllowedValue(g_Generator, g_Generator_Sine);
	AddAllowedValue(g_Generator, g_Generator_Tri);
	AddAllowedValue(g_Generator, g_Generator_Rect);
	AddAllowedValue(g_Generator, g_Generator_Noise);
	AddAllowedValue(g_Generator, g_Generator_Sweep);
	
	//Sine
	pAct = new CPropertyAction (this, &Shutter::OnSinAmp);
	CreateProperty(g_Generator_Sine_Amp, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Shutter::OnSinOff);
	CreateProperty(g_Generator_Sine_Offset, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_Offset, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Shutter::OnSinFreq);
	CreateProperty(g_Generator_Sine_Freq, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_Freq, 0.00001, 9999.9);
	//triangle
	pAct = new CPropertyAction (this, &Shutter::OnTriAmp);
	CreateProperty(g_Generator_Tri_Amp, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Shutter::OnTriOff);
	CreateProperty(g_Generator_Tri_Offset, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Tri_Offset, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Shutter::OnTriFreq);
	CreateProperty(g_Generator_Tri_Freq, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_Freq, 0.00001, 9999.9);
    pAct = new CPropertyAction (this, &Shutter::OnTriSym);
	CreateProperty(g_Generator_Tri_Sym, "50.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_Sym, 0.0, 100.0);
	//rectangle
   pAct = new CPropertyAction (this, &Shutter::OnRecAmp);
   CreateProperty(g_Generator_Rect_Amp, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Amp, 0.0, 100.0);
   pAct = new CPropertyAction (this, &Shutter::OnRecOff);
	CreateProperty(g_Generator_Rect_Offset, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Offset, 0.0, 100.0);
   pAct = new CPropertyAction (this, &Shutter::OnRecFreq);
	CreateProperty(g_Generator_Rect_Freq, "0.1", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Freq, 0.00001, 9999.9);
   pAct = new CPropertyAction (this, &Shutter::OnRecSym);
	CreateProperty(g_Generator_Rect_Sym, "0.1", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Sym, 0.0, 100.0);
	//Noise
	pAct = new CPropertyAction (this, &Shutter::OnNoiAmp);
	CreateProperty(g_Generator_Noise_Amp, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Noise_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Shutter::OnNoiOff);
	CreateProperty(g_Generator_Noise_Offset, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Noise_Offset, 0.0, 100.0);
	//Sweep
    pAct = new CPropertyAction (this, &Shutter::OnSweAmp);
	CreateProperty(g_Generator_Sweep_Amp, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sweep_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Shutter::OnSweOff);
	CreateProperty(g_Generator_Sweep_Offset, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_Offset, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Shutter::OnSweTime);
	CreateProperty(g_Generator_Sweep_Time, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_Time, 0.4, 800.0);
	
	//Scan
	pAct = new CPropertyAction (this, &Shutter::OnScanType);
	CreateProperty(g_Scan_Type, g_Scan_Type_Off, MM::String, false, pAct);
	AddAllowedValue(g_Scan_Type, g_Scan_Type_Off);
	AddAllowedValue(g_Scan_Type, g_Scan_Type_Sine);
	AddAllowedValue(g_Scan_Type, g_Scan_Type_Tri);
	 pAct = new CPropertyAction (this, &Shutter::OnScan);
	 CreateProperty(g_Scan_Start, g_Scan_Off, MM::String, false, pAct);
    AddAllowedValue(g_Scan_Start, g_Scan_Off);
    AddAllowedValue(g_Scan_Start, g_Scan_Starting);

	//trigger
    pAct = new CPropertyAction (this, &Shutter::OnTriggerStart);
	CreateProperty(g_Trigger_Start, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Trigger_Start, max_um_*0.002, max_um_*0.998);
	pAct = new CPropertyAction (this, &Shutter::OnTriggerEnd);
	CreateProperty(g_Trigger_End, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_End, max_um_*0.002, max_um_*0.998);
    pAct = new CPropertyAction (this, &Shutter::OnTriggerInterval);
	CreateProperty(g_Trigger_Interval, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_Interval, min_um_, max_um_);
	pAct = new CPropertyAction (this, &Shutter::OnTriggerTime);
	CreateProperty(g_Trigger_Time, "1", MM::Integer, false, pAct);
   SetPropertyLimits(g_Trigger_Time, 1, 255);
   pAct = new CPropertyAction (this, &Shutter::OnTriggerType);
	CreateProperty(g_Trigger_Generator, g_Trigger_Off, MM::String, false, pAct);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Off);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Rising);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Falling);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Both);	
	
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool Shutter::Busy()
{	
	//CDeviceUtils::SleepMs(50);	
	return false;
}
int Shutter::GetVersion(std::string& version)
{
   int returnStatus = DEVICE_OK;
   
   PurgeComPort(port_.c_str());
   // Version of the controller:

   const char* cm = "";		//Get Version
   returnStatus = SendSerialCommand(port_.c_str(), cm, g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;

   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, version);  //"DSM V6.000\r"
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
   if (version.length() < 2) {
      // if we get no answer, try other port
      LogMessage("There is no device. Try other Port",true);   
	  // no answer, 
      return ERR_NO_ANSWER;
   }   
   return returnStatus;
}
int Shutter::GetActuatorName(char* id){
	LogMessage ("GetActuatorName");
	std::string result;
	char s[20]="acdescr";	
	const char* cmd = s;	
	int ret = SendSerialCommand(port_.c_str(), cmdsmon, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;		
	ret = SendCommand(cmd, result);		
	ret = SendSerialCommand(port_.c_str(), cmdsmoff, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	LogMessage(result);
	char* dest[30];
	splitString((char*)result.c_str()," ,\n",dest);	
	sprintf(id,"%s", dest[1]);	
	LogMessage(id);
	return DEVICE_OK;
}

bool Shutter::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus Shutter::DetectDevice(void)
{
   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
   try
   {
      std::string transformed = port_;
      for( std::string::iterator its = transformed.begin(); its != transformed.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      if( 0< transformed.length() &&  0 != transformed.compare("undefined")  && 0 != transformed.compare("unknown") )
      {
         // the port property seems correct, so give it a try
         result = MM::CanNotCommunicate;
         // device specific default communication parameters
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "115200" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_DataBits, "8");
			GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
			GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Parity, "None");  
			GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Software" );  
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_AnswerTimeout, "200.0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();
		   std::string v;
         int qvStatus = this->GetVersion(v);
         LogMessage(std::string("version : ")+v, true);
         if( DEVICE_OK != qvStatus )
         {
            LogMessageCode(qvStatus,true);
         }
         else
         {
            // to succeed must reach here....
            result = MM::CanCommunicate;
         }
         pS->Shutdown();         
      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }
   return result;
}
int Shutter::SetOpen(bool open)
{   
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}

int Shutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos == 1 ? open = true : open = false;

   return DEVICE_OK;
}
//Use the internal funktion Generator
int Shutter::Fire(double deltaT)
{
	//Use the internal funktion Generator to open and close again
	if(!loop_)
		SetProperty(g_Loop,g_Loop_close);
	if(garec_ != 100.0)
		SetProperty(g_Generator_Rect_Amp,"100.0"); //%
	if(gorec_ != 0.0)
		SetProperty(g_Generator_Rect_Offset,"0.0");	//%
	if (close_){	//Version2
		if(deltaT>=0.001 && deltaT<=1){	//0.001...1 ms
			SetProperty(g_Generator_Rect_Freq,"1000.0");	//Hz
			SetProperty(g_Generator_Rect_Sym,CDeviceUtils::ConvertToString(100*deltaT));
			SetProperty(g_Generator,g_Generator_Rect); //Generate rectangle (Step)
			CDeviceUtils::SleepMs(1);	
		}

		if(deltaT>1 && deltaT<=1000){	//1...1000 ms
			SetProperty(g_Generator_Rect_Freq,"1.0");	//Hz
			SetProperty(g_Generator_Rect_Sym,CDeviceUtils::ConvertToString(0.1*deltaT));
			SetProperty(g_Generator,g_Generator_Rect); //Generate rectangle (Step)
			CDeviceUtils::SleepMs(1000-(long)floor(deltaT));	
		}
	}else{ //Version1
		if(deltaT>=0.001 && deltaT<=1){	//0.01...1 ms
			SetProperty(g_Generator_Rect_Freq,"1000.0");	//Hz
			SetProperty(g_Generator_Rect_Sym,CDeviceUtils::ConvertToString(100-(100*deltaT)));
			SetProperty(g_Generator,g_Generator_Rect); //Generate rectangle (Step)
			CDeviceUtils::SleepMs(1);	
		}

		if(deltaT>1 && deltaT<=1000){	//1...1000 ms
			SetProperty(g_Generator_Rect_Freq,"1.0");	//Hz
			SetProperty(g_Generator_Rect_Sym,CDeviceUtils::ConvertToString(100-(0.1*deltaT)));
			SetProperty(g_Generator,g_Generator_Rect); //Generate rectangle (Step)
			CDeviceUtils::SleepMs(1000-(long)floor(deltaT));	
		}
	}
	SetProperty(g_Generator,g_Generator_Off); //Generate off
   //return DEVICE_UNSUPPORTED_COMMAND;
	return DEVICE_OK;
}
int Shutter::SetPositionUm(double pos){	
	int ret;	
	if(loop_){ //Close loop
		pos_=pos;
		voltage_=(max_V_-min_V_)*(pos-min_um_)/(max_um_-min_um_)+min_V_; //Translate Pos->Voltage
		ret=SetCommandValue("set",pos_);
	}else{  //open loop
		pos_=pos;
		voltage_=(max_V_-min_V_)*(pos-min_um_)/(max_um_-min_um_)+min_V_; //Translate Pos->Voltage		
		ret=SetCommandValue("set",voltage_);
	}
	if (ret != DEVICE_OK)
      return ret;	
	//Wait for Update (500ms)
	CDeviceUtils::SleepMs(500);
	return DEVICE_OK;
}

int Shutter::GetPositionUm(double& pos){	
	int ret;
	double d;
	ret = GetCommandValue("mess",d);
	if(loop_){
		pos=d;
		pos_=pos;
		voltage_=(max_V_-min_V_)*(pos_-min_um_)/(max_um_-min_um_)+min_V_;
	}else{
		voltage_=d;
		pos=(max_um_-min_um_)*(voltage_-min_V_)/(max_V_-min_V_)+min_um_;
		pos_=pos;
	}
	return DEVICE_OK;
}
int Shutter::GetStatus(int& stat){	
	const char* cmd = "stat"; 	
   int ret;
	std::string str;
	std::string type;

	ret = SendCommand(cmd,str); 		
	char* dest[20];
	splitString((char*)str.c_str()," ,\n",dest);
	//If there is a Modul, look of the stat
	type=dest[0];
	std::size_t found;
	found=type.find("unit");
	if(found!=std::string::npos){
		LogMessage ("No Modul found",true);
		stat=0;
		return ERR_MODULE_NOT_FOUND;
	}
	found=type.find("stat");
	if(found!=std::string::npos){
		LogMessage ("Modul found",true);
		stat=atoi(dest[1]);
		stat_=stat;
		//if Bit4 is set, close loop
		loop_=((stat &CLOSE_LOOP)==CLOSE_LOOP)? true:false;
		//if Bit12 is set
		notchon_=((stat &NOTCH_FILTER_ON)==NOTCH_FILTER_ON)? true:false;
		//if Bit13 is set
		lpon_=((stat &LOW_PASS_FILTER_ON)==LOW_PASS_FILTER_ON)? true:false;
	}else{
		LogMessage ("ERROR ");
		LogMessage (dest[0]);
		stat=-1;
		//return ERR_MODULE_NOT_FOUND;
	}
	return DEVICE_OK;
}
int Shutter::SendCommand(const char* cmd,std::string &result){
	int ret;	
	LogMessage (cmd,true);
	ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}
int Shutter::GetCommandValue(const char* c,double& d){
	char str[50]="";
	sprintf(str,"%s",c);	
	const char* cmd = str; 	
    int ret;
	std::string result;
	ret = SendCommand(cmd,result);	
	if (ret != DEVICE_OK)
		return ret;	
	if (result.length() < 1)
      return ERR_NO_ANSWER;
	//TODO: Error or no result
	LogMessage (result,true);
	char* dest[50];
	splitString((char*)result.c_str()," ,\n",dest);
	std::string type;
	type=dest[0];
	if(type==c){		
		d=atof(dest[1]);
	}else{
		LogMessage ("Wrong Result",true);	
		LogMessage (dest[0],true);
		d=0.0;
		ret =PurgeComPort(port_.c_str());
		CDeviceUtils::SleepMs(10);
	}		
	return DEVICE_OK;
}
int Shutter::GetCommandValue(const char* c,int& i){	
	char cmd[50]="";
	sprintf(cmd,"%s",c);	
    int ret;
	std::string result;
	std::string type;
	ret = SendCommand(cmd,result);
	if (ret != DEVICE_OK)
		return ret;	
	if (result.length() < 1)
      return ERR_NO_ANSWER;
	char* dest[50];
	splitString((char*)result.c_str()," ,\n",dest);
	type=dest[0];
	if(type==c){
		i=atoi(dest[1]);
	}else{
		LogMessage ("Wrong Result",true);	
		LogMessage (dest[0],true);
		i=0;
		ret =PurgeComPort(port_.c_str());
		CDeviceUtils::SleepMs(10);
	}
	return ret;
}
int Shutter::SetCommandValue(const char* c,double fkt){
	char str[50]="";
	sprintf(str,"%s,%lf",c,fkt);	
	const char* cmd = str; 	
    int ret;
    ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	//Normally no answer	
	return DEVICE_OK;
}
int Shutter::SetCommandValue(const char* c,int fkt){	
	char str[50]="";
	sprintf(str,"%s,%d",c,fkt);	
	const char* cmd = str; 	
    int ret;
    ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	//Normally no answer	
	return DEVICE_OK;
}
int Shutter::GetLimitsValues(){	
	int ret = SendSerialCommand(port_.c_str(), cmdsmon, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	
	//Send Command
	//min_um_
	char s[20];
	std::string result;
	sprintf(s,"rdac,197");	
	const char* cmd = s; 
	ret = SendCommand(cmd, result);	
	char* dest[20];
	splitString((char*)result.c_str()," ,\n",dest);
	min_um_=atof(dest[2]);

	//max_um_	
	sprintf(s,"rdac,198");	
	cmd = s; 	
	ret = SendCommand(cmd, result);	
	splitString((char*)result.c_str()," ,\n",dest);
	max_um_=atof(dest[2]);

	//min_V_	
	sprintf(s,"rdac,199");	
	cmd = s; 
	ret = SendCommand(cmd, result);	
	splitString((char*)result.c_str()," ,\n",dest);
	min_V_=atof(dest[2]);

	//max_V_	
	sprintf(s,"rdac,200");	
	cmd = s;
	ret = SendCommand(cmd, result);	
	splitString((char*)result.c_str()," ,\n",dest);
	max_V_=atof(dest[2]);

	ret = SendSerialCommand(port_.c_str(), cmdsmoff, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}
int Shutter::GetRgver(int& rgver){	  
    int ret = GetCommandValue("rgver",rgver_);
	if (ret != DEVICE_OK)	  
      return ret;	
    rgver=rgver_;
   return DEVICE_OK;
}
int Shutter::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
      pProp->Get(port_);
   }
   return DEVICE_OK;
}
int Shutter::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {			
		if (close_){
			pProp->Set(g_Version2);			
		}else{
			pProp->Set(g_Version1);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string version;
      pProp->Get(version);
	  if (version == g_Version2){
         close_ = true;
	  }else{
         close_ = false;
	  }	  
	}
    return DEVICE_OK;
}
int Shutter::OnStat(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int s;
	if (eAct == MM::BeforeGet)
    {	
		GetStatus(s);	
		stat_=s;
		pProp->Set((long)s);		
	}   
    return DEVICE_OK;
}
int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){		
		if(shut_){
			pProp->Set((long)0);
		}else{
			pProp->Set((long)1);
		}
	}
    else if (eAct == MM::AfterSet){
		long l;
		pProp->Get(l);
		shut_=(l==1)?true:false;
		if(shut_){
			SetProperty(g_ShutterState, g_Close);
		}else{
			SetProperty(g_ShutterState, g_Open);
		}
	}
    return DEVICE_OK;
}
int Shutter::OnShutterState(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet){	
		bool open=true;		
		double pos=0;		
		int ret = GetPositionUm(pos);
		if (ret != DEVICE_OK)
			return ret;
		if(pos<(max_um_/2)){
			if(close_){
				open=false;
			}else{
				open=true;
			}
		}else{
			if(close_){
				open=true;
			}else{
				open=false;
			}
		}
		shut_=open;
		if (open){
			pProp->Set(g_Open);			
		}else{
			pProp->Set(g_Close);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string state;
		long b=0;
		long l=0;
		pProp->Get(state);
		if (state == g_Open){
			shut_ = false;
			b=0;
		}else{
			shut_ = true;
			b=1;
		}
		
		if((shut_&close_)|((!shut_)&(!close_))){ //close in version2 or open in version1
			//zero Volt 			
			SetPositionUm(min_um_);			
			GetProperty(MM::g_Keyword_State, l);
			if(l==b){
				LogMessage ("l==b");
				if(close_){
					SetProperty(MM::g_Keyword_State, "1");
				}else{
					SetProperty(MM::g_Keyword_State, "0");
				}
			}

		}else{			
			//high volt			
			SetPositionUm(max_um_);
			GetProperty(MM::g_Keyword_State, l);
			if(l==b){				
				if(close_){
					SetProperty(MM::g_Keyword_State, "0");
				}else{
					SetProperty(MM::g_Keyword_State, "1");
				}
			}		
		}
		//CDeviceUtils::SleepMs(50);	
	}
    return DEVICE_OK;
}
int Shutter::OnTemp(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){
		int ret = GetCommandValue("ktemp",ktemp_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set(ktemp_);
	}
	return DEVICE_OK;
}
int Shutter::OnTime(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("rohm",rohm_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)rohm_);
	}
	return DEVICE_OK;
}
int Shutter::OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){	
		pProp->Set(voltage_);
	}
	return DEVICE_OK;
}
int Shutter::OnLoop(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
    {	
		int stat;
		int ret=GetStatus(stat);
		if (ret!=DEVICE_OK)
			return ret;
		if (loop_){
			pProp->Set(g_Loop_close);			
		}else{
			pProp->Set(g_Loop_open);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string loop;
      pProp->Get(loop);
	  if (loop == g_Loop_close){
         loop_ = true;
	  }else{
         loop_ = false;
	  }
	  int i=(loop_)?1:0;
	  int ret = SetCommandValue("cl",i);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnSoftstart(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){	
		int b=0;		
		int ret = GetCommandValue("fenable",b);
		fenable_=(b==0)?false:true;
		if (ret!=DEVICE_OK)
			return ret;
		if (fenable_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		pProp->Get(softstart);
		int b;
		if (softstart == g_Fenable_On){
			fenable_ = true;
			b=1;
		}else{
			fenable_ = false;
			b=0;
		}		
		int ret = SetCommandValue("fenable",b);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnSlewRate(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetCommandValue("sr",sr_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)sr_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(sr_);		
		ret = SetCommandValue("sr",sr_);		
		if (ret != DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnModulInput(MM::PropertyBase* pProp, MM::ActionType eAct){
	int l=0;
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("modon",l);
		modon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (modon_){
			pProp->Set(g_Modon_On);			
		}
		else{
			pProp->Set(g_Modon_Off);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string modon;
      pProp->Get(modon);
	  if (modon == g_Modon_On){
         modon_ = true;
	  }else{
         modon_ = false;
	  }
	  l=(modon_)?1:0;
	  int ret = SetCommandValue("modon",l);	  
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnMonitor(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("monsrc",monsrc_);
		if (ret!=DEVICE_OK)
			return ret;				
		switch (monsrc_){
		case 0:
			pProp->Set(g_Monsrc_0);	break;
		case 1:
			pProp->Set(g_Monsrc_1);	break;
		case 2:
			pProp->Set(g_Monsrc_2);	break;
		case 3:
			pProp->Set(g_Monsrc_3);	break;
		case 4:
			pProp->Set(g_Monsrc_4);	break;
		case 5:
			pProp->Set(g_Monsrc_5);	break;
		case 6:
			pProp->Set(g_Monsrc_6);	break;
		default:
			pProp->Set(g_Monsrc_0);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string mon;
		pProp->Get(mon);
		if (mon == g_Monsrc_0)
         monsrc_ = 0;
		else if (mon == g_Monsrc_1)
         monsrc_ = 1;
		else if (mon == g_Monsrc_2)
         monsrc_ = 2;
		else if (mon == g_Monsrc_3)
         monsrc_ = 3;
		else if (mon == g_Monsrc_4)
         monsrc_ = 4;
		else if (mon == g_Monsrc_5)
         monsrc_ = 5;	
		else if (mon == g_Monsrc_6)
         monsrc_ = 6;	
		int ret = SetCommandValue("monsrc",monsrc_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnPidP(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetCommandValue("kp",kp_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)kp_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(kp_);		
		ret = SetCommandValue("kp",kp_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnPidI(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetCommandValue("ki",ki_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)ki_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(ki_);		 
		ret = SetCommandValue("ki",ki_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnPidD(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{
		ret = GetCommandValue("kd",kd_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)kd_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(kd_);		
		ret = SetCommandValue("kd",kd_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnNotch(MM::PropertyBase* pProp, MM::ActionType eAct){
	int b=0;
	if (eAct == MM::BeforeGet){
		int ret = GetCommandValue("notchon",b);
		notchon_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (notchon_){
			pProp->Set(g_Notch_On);			
		}else{
			pProp->Set(g_Notch_Off);			
		}		
	}else if (eAct == MM::AfterSet){	  
	  std::string notch;
      pProp->Get(notch);
	  if (notch == g_Notch_On){
         notchon_ = true;
	  }else{
         notchon_ = false;
	  }
	   b=(kd_)?1:0;
	  int ret = SetCommandValue("notchon",b);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnNotchFreq(MM::PropertyBase* pProp, MM::ActionType eAct){
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchf",notchf_);
		if (ret != DEVICE_OK)
			return ret;
		SetPropertyLimits(g_Notch_Band, 0, ((2*notchf_)<=20000)?(2*notchf_):20000);
		l=notchf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		notchf_=(int)l;
		int ret = SetCommandValue("notchf",notchf_);
		if (ret!=DEVICE_OK)
			return ret;
		//set limit bandwidth to max 2*notch_frequency
		SetPropertyLimits(g_Notch_Band, 0, ((2*notchf_)<=20000)?(2*notchf_):20000);
	}
    return DEVICE_OK;
}
int Shutter::OnNotchBand(MM::PropertyBase* pProp, MM::ActionType eAct){
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchb",notchb_);
		if (ret != DEVICE_OK)
			return ret;
		l=notchb_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		notchb_=(int)l;
		int ret = SetCommandValue("notchb",notchb_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnLowpass(MM::PropertyBase* pProp, MM::ActionType eAct){
	int l=0;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpon",l);
		lpon_=(l==1)?true:false;		
		if (ret!=DEVICE_OK)
			return ret;
		if (lpon_){
			pProp->Set(g_Lowpass_On);			
		}else{
			pProp->Set(g_Lowpass_Off);			
		}		
	}else if (eAct == MM::AfterSet){
	  std::string lpon;
      pProp->Get(lpon);
	  if (lpon == g_Lowpass_On){
         lpon_ = true;
	  }else{
         lpon_ = false;
	  }
	  l=(lpon_)?1:0;
	  int ret = SetCommandValue("lpon",l);	
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnLowpassFreq(MM::PropertyBase* pProp, MM::ActionType eAct){
	long c;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpf",lpf_);
		if (ret != DEVICE_OK)
			return ret;		
		c=lpf_;
		pProp->Set(c);
	}else if (eAct == MM::AfterSet){
		pProp->Get(c);
		lpf_=(int)c;
		int ret = SetCommandValue("lpf",lpf_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnGenerate(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){			
		int ret = GetCommandValue("gfkt",gfkt_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (gfkt_){
		case 0:
			pProp->Set(g_Generator_Off);		break;
		case 1:
			pProp->Set(g_Generator_Sine);		break;
		case 2:
			pProp->Set(g_Generator_Tri);		break;
		case 3:
			pProp->Set(g_Generator_Rect);	break;
		case 4:
			pProp->Set(g_Generator_Noise);	break;
		case 5:
			pProp->Set(g_Generator_Sweep);	break;
		default:
			pProp->Set(g_Generator_Off);
		}
	}else if (eAct == MM::AfterSet){		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Generator_Off)
         gfkt_ = 0;
		else if (gen == g_Generator_Sine)
         gfkt_ = 1;
		else if (gen == g_Generator_Tri)
         gfkt_ = 2;
		else if (gen == g_Generator_Rect)
         gfkt_ = 3;
		else if (gen == g_Generator_Noise)
         gfkt_ = 4;
		else if (gen == g_Generator_Sweep)
         gfkt_ = 5;		
		int ret = SetCommandValue("gfkt",gfkt_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnSinAmp(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gasin",gasin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gasin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gasin_);
		int ret = SetCommandValue("gasin",gasin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnSinOff(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{		
        ret = GetCommandValue("gosin",gosin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gosin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gosin_);
		ret = SetCommandValue("gosin",gosin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnSinFreq(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gfsin",gfsin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gfsin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gfsin_);
		int ret = SetCommandValue("gfsin",gfsin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnTriAmp(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gatri",gatri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gatri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gatri_);
		int ret = SetCommandValue("gatri",gatri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnTriOff(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gotri",gotri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gotri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gotri_);
		int ret = SetCommandValue("gotri",gotri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnTriFreq(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gftri",gftri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gftri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gftri_);
		int ret = SetCommandValue("gftri",gftri_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnTriSym(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gstri",gstri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gstri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gstri_);
		int ret = SetCommandValue("gstri",gstri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnRecAmp(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("garec",garec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(garec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(garec_);
		int ret = SetCommandValue("garec",garec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnRecOff(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gorec",gorec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gorec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gorec_);
		int ret = SetCommandValue("gorec",gorec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnRecFreq(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gfrec",gfrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gfrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gfrec_);
		int ret = SetCommandValue("gfrec",gfrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnRecSym(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gsrec",gsrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gsrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gsrec_);
		int ret = SetCommandValue("gsrec",gsrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnNoiAmp(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("ganoi",ganoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(ganoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(ganoi_);
		int ret = SetCommandValue("ganoi",ganoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnNoiOff(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gonoi",gonoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gonoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gonoi_);
		int ret = SetCommandValue("gonoi",gonoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnSweAmp(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gaswe",gaswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gaswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gaswe_);
		int ret = SetCommandValue("gaswe",gaswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnSweOff(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("goswe",goswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(goswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(goswe_);
		int ret = SetCommandValue("goswe",goswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnSweTime(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gtswe",gtswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(gtswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(gtswe_);
		int ret = SetCommandValue("gtswe",gtswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnScanType(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("sct",sct_);	
		if (ret!=DEVICE_OK)
			return ret;			
		switch (sct_){
		case 0:
			pProp->Set(g_Scan_Type_Off);break;
		case 1:
			pProp->Set(g_Scan_Type_Sine);		break;
		case 2:
			pProp->Set(g_Scan_Type_Tri);	break;		
		default:
			pProp->Set(g_Scan_Type_Off);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Scan_Type_Off)
         sct_ = 0;
		else if (gen == g_Scan_Type_Sine)
         sct_ = 1;
		else if (gen == g_Scan_Type_Tri)
         sct_ = 2;		
		int ret = SetCommandValue("sct",sct_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnScan(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {			
		int b=0;		
		int ret = GetCommandValue("ss",b);
		ss_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;			
		if(ss_){
			pProp->Set(g_Scan_Starting);
		}else{
			pProp->Set(g_Scan_Off);	
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string s;
		int b=0;
		pProp->Get(s);
		if (s == g_Scan_Off){
			ss_ = false;
			b=0;
		}else if (s == g_Scan_Starting){
			ss_ = true;
			b=1;
		}
		if(ss_){
			int ret = SetCommandValue("ss",b);
			if (ret!=DEVICE_OK)
				return ret;		
		}		
	}
    return DEVICE_OK;
}
int Shutter::OnTriggerStart(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){			
		int ret = GetCommandValue("trgss",trgss_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(trgss_);		
	}else if (eAct == MM::AfterSet){		
		pProp->Get(trgss_);					
		int ret = SetCommandValue("trgss",trgss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnTriggerEnd(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("trgse",trgse_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(trgse_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(trgse_);					
		int ret = SetCommandValue("trgse",trgse_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
	return DEVICE_OK;
}
int Shutter::OnTriggerInterval(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("trgsi",trgsi_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(trgsi_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(trgsi_);					
		int ret = SetCommandValue("trgsi",trgsi_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnTriggerTime(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("trglen",trglen_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set((long)trglen_);		
	}
    else if (eAct == MM::AfterSet)
    {	
		long l;
		pProp->Get(l);
		trglen_=(int)l;
		int ret = SetCommandValue("trglen",trglen_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnTriggerType(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("trgedge",trgedge_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (trgedge_){
		case 0:
			pProp->Set(g_Trigger_Off);		break;
		case 1:
			pProp->Set(g_Trigger_Rising);	break;
		case 2:
			pProp->Set(g_Trigger_Falling);	break;	
		case 3:
			pProp->Set(g_Trigger_Both);		break;
		default:
			pProp->Set(g_Trigger_Off);
		}	
	}
    else if (eAct == MM::AfterSet)
    {			
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Trigger_Off)
         trgedge_ = 0;
		else if (gen == g_Trigger_Rising)
         trgedge_ = 1;
		else if (gen == g_Trigger_Falling)
         trgedge_ = 2;	
		else if (gen == g_Trigger_Both)
         trgedge_ = 3;	
		int ret = SetCommandValue("trgedge",trgedge_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
