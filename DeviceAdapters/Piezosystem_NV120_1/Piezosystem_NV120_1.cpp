///////////////////////////////////////////////////////////////////////////////
// FILE:          Piezosystem_NV120_1.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Piezosystem Jena device adapter.
//						NV120/1CL is a 1 channel device which can control diffrent actuators.
//						The actuator has a small memory with the values for the amplifier.//					
//						The controller has USB(VCP) and RS232-interface.
//					
//                
// AUTHOR:        Chris Belter, cbelter@piezojena.com 4/09/2013, ZStage and Shutter by Chris Belter
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

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Piezosystem_NV120_1.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
#include <math.h>

//Port-Control
const char* g_Mesg_Send_term ="\r";  //CR 
const char* g_Mesg_Receive_term ="\r";  //CR 
//Controller
const char* g_NV120_1 = "PSJ_NV120/1CL";
// single axis stage
const char* g_StageDeviceName = "PSJ_Stage";

const char* g_Actor				= "Actuator";
const char* g_Axis				= "Axis";

const char* g_Loop				= "Loop";
const char* g_Loop_open			= "open loop";
const char* g_Loop_close		= "close loop";

const char* g_Remote				= "Remote control";
const char* g_Remote_on			= "on";
const char* g_Remote_off		= "off";
const char* g_Status				= "Status";

const char* g_Limit_V_Min		= "Limit Voltage min [V]";
const char* g_Limit_V_Max		= "Limit Voltage max [V]";
const char* g_Limit_Um_Min		= "Limit um min [microns]";
const char* g_Limit_Um_Max		= "Limit um max [microns]";
const char* g_Voltage			= "Voltage [V]";
const char* g_Position			= "Position [microns]";

const char* g_Monitor			= "monitor output";
const char* g_Monitor0			= "0 actuator voltage";
const char* g_Monitor1			= "1 actuator position";
const char* g_Monitor2			= "2 operation dependent";

const char* g_Encoder_Mode		= "encoder: mode";
const char* g_Encoder_Mode0	= "EM0: normal with acceleration";
const char* g_Encoder_Mode1	= "EM1: adjustable interval";
const char* g_Encoder_Mode2	= "EM2: adjustable interval with acceleration";

const char* g_Encoder_Time		= "encoder: sample interval [ x * 0.02s ]" ; //0...255 * 0.02sec
const char* g_Encoder_Limit	= "encoder: maximum step";  //1...65535
const char* g_Encoder_Exponent = "encoder: exponent for calculation of acceleration";
const char* g_Encoder_Open		= "encoder: interval for open loop [V]";		//0.001...150.0 Voltage
const char* g_Encoder_Close	= "encoder: interval for close loop [um]";	//0.001...100.0 miro meter
const char* g_Version_Ver		= "version: ver";
const char* g_Version_Date		= "version: date";
const char* g_Version_Serno	= "version: serno";
const char* g_Actor_Serno		= "Actuator serno";

const char* g_Fenable			= "actuator soft start";
const char* g_Fenable_Off		= "soft start disable";
const char* g_Fenable_On		= "soft start enable";
const char* g_Light				= "display brightness";		//0...255

// Shutter
const char* g_Shutter			= "PSJ_Shutter";

const char* g_ShutterState		= "Shutter State";
const char* g_Open				= "open";
const char* g_Close				= "close";

const char* g_Version			= "Version (zero volt position)";
const char* g_Version1			= "edges open";
const char* g_Version2			= "edges closed";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{		
	//RegisterDevice(g_NV120_1, MM::HubDevice, "Piezosystem Jena NV120");
	RegisterDevice(g_StageDeviceName, MM::StageDevice, "Single Axis Stage Ch1");
	//RegisterDevice(g_Shutter, MM::ShutterDevice, "PSJ Shutter");
}             

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{	
	if (deviceName == 0)      return 0;		
	//if (strcmp(deviceName, g_dDrive) == 0){ 		
	//	return new Hub(g_NV120_1);}	
	//else 
	if (strcmp(deviceName, g_StageDeviceName) == 0){		
		return new Stage(2);	}	
	//else if (strcmp(deviceName, g_Shutter) == 0 ){		
	//	return new Shutter();   }	
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

void splitString(char* string, const char* delimiter, char** dest){
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
/**
 * Single axis stage.
 */
Stage::Stage(int nr):
   initialized_(false),
	loop_(false),
	voltage_(0),
	min_V_(-20.0),
	max_V_(130.0),
	min_um_(0.0),
	max_um_(100.0),
	pos_(0.0),
	nr_(nr)
{
	InitializeDefaultErrorMessages();
	
	// custemer error message
	SetErrorText(ERR_SET_POSITION_FAILED, "Wrong Position. Look to the limits");
	SetErrorText(ERR_ONLY_OPEN_LOOP, "Close loop not possible, pherhaps you have the wrong actuator");
	SetErrorText(ERR_REMOTE, "Not possible. First activate remote");

	 // Name
   CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Piezosystem stage driver adapter", MM::String, true); 	
	
	// Port:
	CPropertyAction *pAct = new CPropertyAction(this, &Stage::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);	

}

Stage::~Stage()
{
   Shutdown();
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
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "19200" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_DataBits, "8");
			GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
			GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Parity, "None");  
			GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Software" );  
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_AnswerTimeout, "500.0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();
		   //std::string dev;
         int qvStatus = this->GetDevice(dev_); 			
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
//////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void Stage::GetName(char* Name) const
{  
   CDeviceUtils::CopyLimitedString(Name, g_StageDeviceName);
}

int Stage::Initialize()
{ 
	CPropertyAction* pAct = new CPropertyAction (this, &Stage::OnRemote);
   CreateProperty(g_Remote, g_Remote_on, MM::String, false, pAct,false);
   AddAllowedValue(g_Remote, g_Remote_on);
   AddAllowedValue(g_Remote, g_Remote_off); 	

	GetDevice(dev_);	
	if(dev_=="NV120CLE"){  //NV120 is only open loop , NV120CLE with close loop
		pAct = new CPropertyAction (this, &Stage::OnLoop);
		CreateProperty(g_Loop, g_Loop_open, MM::String, false, pAct,false);
		AddAllowedValue(g_Loop, g_Loop_open);
		AddAllowedValue(g_Loop, g_Loop_close);	 	
	}
	GetLimitValues();
	//pAct = new CPropertyAction (this, &Stage::OnMinV);		
	CreateProperty(g_Limit_V_Min, CDeviceUtils::ConvertToString(min_V_), MM::Float, true);
	//pAct = new CPropertyAction (this, &Stage::OnMaxV);	;	
	CreateProperty(g_Limit_V_Max, CDeviceUtils::ConvertToString(max_V_), MM::Float, true);
	//pAct = new CPropertyAction (this, &Stage::OnMinUm);		
	CreateProperty(g_Limit_Um_Min,CDeviceUtils::ConvertToString(min_um_), MM::Float, true);
	//pAct = new CPropertyAction (this, &Stage::OnMaxUm);	
	CreateProperty(g_Limit_Um_Max, CDeviceUtils::ConvertToString(max_um_), MM::Float, true);

	GetActorname(acname_);
	pAct = new CPropertyAction (this, &Stage::OnActorname);
   CreateProperty(g_Actor, "0", MM::String, true, pAct,false);
	GetAxisname(axisname_);
	pAct = new CPropertyAction (this, &Stage::OnAxisname);
   CreateProperty(g_Axis, "0", MM::String, true, pAct,false); 

	pAct = new CPropertyAction (this, &Stage::OnStat);
   CreateProperty(g_Status, "0", MM::Integer, true, pAct,false); 
	pAct = new CPropertyAction (this, &Stage::OnPosition);
	CreateProperty( g_Position, "0", MM::Float, false, pAct,false);

	pAct = new CPropertyAction (this, &Stage::OnMon);
	CreateProperty(g_Monitor, g_Monitor1, MM::String, false, pAct,false);
	AddAllowedValue(g_Monitor, g_Monitor0);
   AddAllowedValue(g_Monitor, g_Monitor1);
	AddAllowedValue(g_Monitor, g_Monitor2);

	pAct = new CPropertyAction (this, &Stage::OnSoftstart);
   CreateProperty(g_Fenable, g_Fenable_Off, MM::String, false, pAct);
   AddAllowedValue(g_Fenable , g_Fenable_Off);
   AddAllowedValue(g_Fenable, g_Fenable_On);

	pAct = new CPropertyAction (this, &Stage::OnLight);
   CreateProperty(g_Light, "0", MM::Integer, false, pAct,false);
	SetPropertyLimits(g_Light, 0, 255);

   pAct = new CPropertyAction (this, &Stage::OnEncmode);
   CreateProperty(g_Encoder_Mode, g_Encoder_Mode0, MM::String, false, pAct,false);
	AddAllowedValue(g_Encoder_Mode , g_Encoder_Mode0);
   AddAllowedValue(g_Encoder_Mode , g_Encoder_Mode1);
	AddAllowedValue(g_Encoder_Mode , g_Encoder_Mode2);

	pAct = new CPropertyAction (this, &Stage::OnEnctime);
   CreateProperty(g_Encoder_Time, "0", MM::Integer, false, pAct,false);
	SetPropertyLimits(g_Encoder_Time, 0, 255); 
	pAct = new CPropertyAction (this, &Stage::OnEnclim);
   CreateProperty(g_Encoder_Limit, "1000", MM::Integer, false, pAct,false);
	SetPropertyLimits(g_Encoder_Limit, 1, 65535);
	pAct = new CPropertyAction (this, &Stage::OnEncexp);
   CreateProperty(g_Encoder_Exponent, "3", MM::Integer, false, pAct,false);
	SetPropertyLimits(g_Encoder_Exponent, 1, 10);

	pAct = new CPropertyAction (this, &Stage::OnEncstol);
   CreateProperty(g_Encoder_Open, "1.0", MM::Float, false, pAct,false);
	SetPropertyLimits(g_Encoder_Open, 0.001, 100.0);
	pAct = new CPropertyAction (this, &Stage::OnEncstcl);
   CreateProperty(g_Encoder_Close, "1.0", MM::Float, false, pAct,false);
	SetPropertyLimits(g_Encoder_Close, 0.001, 100.0);
		
   int ret = UpdateStatus();
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
		CDeviceUtils::SleepMs(500);
      return false;
}
// Stage API
int Stage::SetPositionUm(double pos){
	LogMessage ("SetPositionUm");
	int ret;	
	if(!remote_)			//remote is off
		return ERR_REMOTE;
	if((pos<=min_um_) | (pos>=max_um_))
		return ERR_SET_POSITION_FAILED;

	ret=GetLoop(loop_);
	if(loop_){				//Close loop
		pos_=pos;
		voltage_=(max_V_-min_V_)*(pos-min_um_)/(max_um_-min_um_)+min_V_; //Translate Pos->Voltage
		ret=SetCommandValue("set",pos_);
	}else{					//open loop
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
	ret=GetLoop(loop_);
	ret = GetCommandValue("rk",d);
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
int Stage::GetLimitValues()
{
	std::string result;
	char* dest[50];
	//min_um_
	int returnStatus = SendSerialCommand(port_.c_str(), "dspclmin,2", g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;   
   returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;		
	splitString((char*)result.c_str(),",",dest);
	min_um_=atof(dest[2]);
	//max_um_
	returnStatus = SendSerialCommand(port_.c_str(), "dspclmax,2", g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;   
   returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;		
	splitString((char*)result.c_str(),",",dest);
	max_um_=atof(dest[2]);
	//min_V_
	returnStatus = SendSerialCommand(port_.c_str(), "dspvmin", g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;   
   returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;		
	splitString((char*)result.c_str(),",",dest);
	min_V_=atoi(dest[1]);
	//max_V_
	returnStatus = SendSerialCommand(port_.c_str(), "dspvmax", g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;   
   returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;		
	splitString((char*)result.c_str(),",",dest);
	max_V_=atoi(dest[1]); 

	return DEVICE_OK;
}
int Stage::GetDevice(std::string& dev)
{
   int returnStatus = DEVICE_OK;
   
   PurgeComPort(port_.c_str());
   // Version of the controller:

   const char* cm = "";		//Get Version
   returnStatus = SendSerialCommand(port_.c_str(), cm, g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;

   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), ">\r", dev);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
   if (dev.length() < 2) {
      // if we get no answer, try other port
      LogMessage("There is no device. Try other Port",true);   
	  // no answer, 
      return ERR_NO_ANSWER;
   }
   return returnStatus;
}
int Stage::GetActorname(std::string& name)
{
	std::string result;
	char c[20];
	sprintf(c,"acdescr,%d",nr_);
	const char* cm = c;		
   int returnStatus = SendSerialCommand(port_.c_str(), cm, g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;

   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
	char* dest[50];
	splitString((char*)result.c_str(),",",dest);
	name=dest[2];
	return returnStatus;
}
int Stage::GetAxisname(std::string& name)
{
	std::string result;
	int i=0;
	char c[20];
	sprintf(c,"accoor,%d",nr_);
	const char* cm = c ;		
   int returnStatus = SendSerialCommand(port_.c_str(), cm, g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;

   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
	char* dest[50];
	splitString((char*)result.c_str(),",",dest);
	i=atoi(dest[2]);
	switch (i){
	case 0: 
		name="X";
		break;
	case 1:
		name="Y";
		break;
	case 2:
		name="Z";
		break;
	case 3:
		name="Theta";
		break;
	case 4:
		name="Phi";
		break;
	case 5:
		name=" ";
		break;
	default:
		name="X";
	}
	return returnStatus;
} 
int Stage::GetVersion()
{
	std::string result;
   char* dest[50];
	const char* cm = "ver";		
   int ret = SendSerialCommand(port_.c_str(), cm, g_Mesg_Send_term);
   if (ret != DEVICE_OK) 
      return ret;

   // ver
   ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
   if (ret != DEVICE_OK) 
      return ret;		
	splitString((char*)result.c_str(),",",dest);
	ver_=dest[1];
	// sdate
   ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
   if (ret != DEVICE_OK) 
      return ret;		
	splitString((char*)result.c_str(),",",dest);
	sdate_=dest[1];
	// serno
   ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
   if (ret != DEVICE_OK) 
      return ret;	
	splitString((char*)result.c_str(),",",dest);
	serno_=dest[1];

	return ret;
}
int Stage::GetStatus(int& stat){
	LogMessage("GetStatus");

	std::string result;
	int returnStatus = SendSerialCommand(port_.c_str(), "stat", g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
	char* dest[50];
	splitString((char*)result.c_str(),",",dest);
	stat=atoi(dest[1]);
	stat_=stat;

	//CDeviceUtils::SleepMs(500);
	return returnStatus;
}
int Stage::GetLoop(bool& loop){
	LogMessage("GetLoop");
	std::string result;
	int returnStatus = SendSerialCommand(port_.c_str(), "cloop", g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
	char* dest[50];
	int l;
	splitString((char*)result.c_str()," ,\n",dest);
	l=atoi(dest[1]);
	loop=(l==1)?true:false;
	return returnStatus;
}
int Stage::GetLight(int& l){
	LogMessage("GetLight");
	std::string result;
	int returnStatus = SendSerialCommand(port_.c_str(), "light", g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
	char* dest[50];	
	splitString((char*)result.c_str()," ,\n",dest);
	l=atoi(dest[1]);	
	return returnStatus;
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
	LogMessage ("Get command value i");

	char str[50]="";
	sprintf(str,"%s,%d",c,nr_);	
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
		i=atoi(dest[2]);
	}else{
		LogMessage ("Wrong Result",true);	
		LogMessage (dest[0],true);
		i=0;
		ret =PurgeComPort(port_.c_str());
		CDeviceUtils::SleepMs(10);
	}		
	return DEVICE_OK;
}

int Stage::SetCommandValue(const char* c,double fkt){
	LogMessage ("Set command value d");
	char str[50]="";
	sprintf(str,"%s,%.3lf",c,fkt);	
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

int Stage::OnMinV(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(min_V_);
	}
	return DEVICE_OK;
}
int Stage::OnMaxV(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(max_V_);
	}
	return DEVICE_OK;
}
int Stage::OnMinUm(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(min_um_);
	}
	return DEVICE_OK;
}
int Stage::OnMaxUm(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(max_um_);
	}
	return DEVICE_OK;
}
int Stage::OnActorname(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(acname_.c_str());
	}
	return DEVICE_OK;
}
int Stage::OnAxisname(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(axisname_.c_str());
	}
	return DEVICE_OK;
}

int Stage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct){	
	LogMessage("OnPosition");
	if (eAct == MM::BeforeGet){	
		int ret = GetPositionUm(pos_);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(pos_);			
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
	LogMessage("OnStat");
	if (eAct == MM::BeforeGet){
		int i;		
		int ret=GetStatus(i);			
		stat_=i;
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)stat_);
	}
	CDeviceUtils::SleepMs(500);
	return DEVICE_OK;
}
int Stage::OnLoop(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {	
		GetLoop(loop_);
		if (loop_){
			pProp->Set(g_Loop_close);
			l=1;
		}
		else{
			pProp->Set(g_Loop_open);
			l=0;
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
		std::string loop;
		std::string result;
      pProp->Get(loop);
		if (loop == g_Loop_close){
         loop_ = true;
			int ret = SendSerialCommand(port_.c_str(), "cloop,1", g_Mesg_Send_term);
			if (ret!=DEVICE_OK)
				return ret;
			l=1;

		}else{
         loop_ = false;
			int ret = SendSerialCommand(port_.c_str(), "cloop,0", g_Mesg_Send_term);
			if (ret!=DEVICE_OK)
				return ret;
			l=0;
		}
		clearPort(*this, *GetCoreCallback(), port_.c_str());
		CDeviceUtils::SleepMs(300);
		int ret = SendSerialCommand(port_.c_str(), "cloop", g_Mesg_Send_term);
		ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
		if (ret != DEVICE_OK) 
			return ret;
		char* dest[20];
		splitString((char*)result.c_str(),",",dest);
		int r=atoi(dest[1]);
		if(l!=r){
			//Only open loop possible, because:
			// - no measure system
			// - other actorname
			// - other serial number
			return ERR_ONLY_OPEN_LOOP;
		}
	}
    return DEVICE_OK;
}
int Stage::OnRemote(MM::PropertyBase* pProp, MM::ActionType eAct){	
	LogMessage("OnRemote");
	int l=0;
	if (eAct == MM::BeforeGet)
    {			
		int stat;
		int ret=GetStatus(stat);
		if (ret!=DEVICE_OK)
				return ret;
		if((stat&STATUS_REMOTE)==STATUS_REMOTE){
		//if (remote_){
			remote_=true;
			pProp->Set(g_Remote_on);
			l=1;
		}
		else{
			remote_=false;
			pProp->Set(g_Remote_off);
			l=0;
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string remote;
      pProp->Get(remote);
		if (remote == g_Remote_on){
         remote_ = true;
			int ret = SendSerialCommand(port_.c_str(), "setk,1", g_Mesg_Send_term);
			if (ret!=DEVICE_OK)
				return ret;

		}else{
         remote_ = false;
			int ret = SendSerialCommand(port_.c_str(), "setk,0", g_Mesg_Send_term);
			if (ret!=DEVICE_OK)
				return ret;
		}
		CDeviceUtils::SleepMs(500);
	}
    return DEVICE_OK;
}
int Stage::OnMon(MM::PropertyBase* pProp, MM::ActionType eAct){			
	LogMessage("OnMon");
	if (eAct == MM::BeforeGet)
    {			
		std::string result;
		int ret = SendSerialCommand(port_.c_str(), "monwpa", g_Mesg_Send_term);
		ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
		if (ret != DEVICE_OK) 
			return ret;
		char* dest[20];
		splitString((char*)result.c_str(),",",dest);
		int r=atoi(dest[1]);
		if (ret!=DEVICE_OK)
				return ret;
		switch (r){
		case 0:
			pProp->Set(g_Monitor0);	break;
		case 1:
			pProp->Set(g_Monitor1);	break;
		case 2:
			pProp->Set(g_Monitor2);	break;
		default:
			pProp->Set(g_Monitor1);	break;
		}
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string mon;
      pProp->Get(mon);
		if (mon == g_Monitor0){         
			int ret = SendSerialCommand(port_.c_str(), "monwpa,0", g_Mesg_Send_term);
			if (ret!=DEVICE_OK)
				return ret;

		}else if(mon == g_Monitor1){         
			int ret = SendSerialCommand(port_.c_str(), "monwpa,1", g_Mesg_Send_term);
			if (ret!=DEVICE_OK)
				return ret;
		}else if(mon == g_Monitor2){         
			int ret = SendSerialCommand(port_.c_str(), "monwpa,2", g_Mesg_Send_term);
			if (ret!=DEVICE_OK)
				return ret;
		}
		CDeviceUtils::SleepMs(500);
	}
    return DEVICE_OK;
}
int Stage::OnSoftstart(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){		
		int i=0;
		int ret = GetCommandValue("fenable",i);
		if (ret != DEVICE_OK)  
			return ret;	
		fenable_=(i==1)?true:false;		
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
			int ret = SendSerialCommand(port_.c_str(), "fenable,2,1", g_Mesg_Send_term);
			if (ret!=DEVICE_OK)
				return ret;
		}else{
			fenable_ = false;
			int ret = SendSerialCommand(port_.c_str(), "fenable,2,0", g_Mesg_Send_term);
			if (ret!=DEVICE_OK)
				return ret;
		}		
	}
    return DEVICE_OK;
}
int Stage::OnLight(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnLight");
	if (eAct == MM::BeforeGet){
		int i;		
		int ret=GetLight(i);		
		if (ret!=DEVICE_OK)
			return ret;
		light_=i;
		pProp->Set((long)light_);
	}
	 else if (eAct == MM::AfterSet){	  
		int i;
		char light[20];
		pProp->Get((long&)i);
		sprintf(light,"light,%d",i);
		int ret = SendSerialCommand(port_.c_str(), light, g_Mesg_Send_term);
		if (ret!=DEVICE_OK)
			return ret;		
		}	 
	//CDeviceUtils::SleepMs(500);
	return DEVICE_OK;
}

int Stage::OnEncmode(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnEncmode");
	if (eAct == MM::BeforeGet){
		int m;
		std::string result;
		char* dest[50];
		int ret = SendSerialCommand(port_.c_str(), "encmode", g_Mesg_Send_term);
		ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
		if (ret != DEVICE_OK) 
			return ret;	
		splitString((char*)result.c_str(),",",dest);
		m=atoi(dest[1]);	
		switch(m){
			case 0:
				pProp->Set(g_Encoder_Mode0);
				break;
			case 1:
				pProp->Set(g_Encoder_Mode1);
				break;
			case 2:
				pProp->Set(g_Encoder_Mode2);
				break;
			default:
			  pProp->Set(g_Encoder_Mode1);
		}
	}
	 else if (eAct == MM::AfterSet){	  
		int i=0;
		char mode[20];
		std::string m;
		pProp->Get(m);
		if(m==g_Encoder_Mode0)
			i=0;
		if(m==g_Encoder_Mode1)
			i=1;
		if(m==g_Encoder_Mode2)
			i=2;
		sprintf(mode,"encmode,%d",i);
		int ret = SendSerialCommand(port_.c_str(), mode, g_Mesg_Send_term);
		if (ret!=DEVICE_OK)
			return ret;		
		}	 
	//CDeviceUtils::SleepMs(500);
	return DEVICE_OK;
}
int Stage::OnEnctime(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnEnctime");
	if (eAct == MM::BeforeGet){
		int m;
		std::string result;
		char* dest[50];
		int ret = SendSerialCommand(port_.c_str(), "enctime", g_Mesg_Send_term);
		ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
		if (ret != DEVICE_OK) 
			return ret;	
		splitString((char*)result.c_str(),",",dest);
		m=atoi(dest[1]);		
		pProp->Set((long)m);
	}
	 else if (eAct == MM::AfterSet){	  
		int i;
		char mode[20];
		pProp->Get((long&)i);
		sprintf(mode,"enctime,%d",i);
		int ret = SendSerialCommand(port_.c_str(), mode, g_Mesg_Send_term);
		if (ret!=DEVICE_OK)
			return ret;		
		}	 
	//CDeviceUtils::SleepMs(500);
	return DEVICE_OK;
}
int Stage::OnEnclim(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnEnclim");
	if (eAct == MM::BeforeGet){
		int m;
		std::string result;
		char* dest[50];
		int ret = SendSerialCommand(port_.c_str(), "enclim", g_Mesg_Send_term);
		ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
		if (ret != DEVICE_OK) 
			return ret;	
		splitString((char*)result.c_str(),",",dest);
		m=atoi(dest[1]);		
		pProp->Set((long)m);
	}
	 else if (eAct == MM::AfterSet){	  
		int i;
		char mode[20];
		pProp->Get((long&)i);
		sprintf(mode,"enclim,%d",i);
		int ret = SendSerialCommand(port_.c_str(), mode, g_Mesg_Send_term);
		if (ret!=DEVICE_OK)
			return ret;		
		}	 
	//CDeviceUtils::SleepMs(500);
	return DEVICE_OK;
}
int Stage::OnEncexp(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnEncexp");
	if (eAct == MM::BeforeGet){
		int m;
		std::string result;
		char* dest[50];
		int ret = SendSerialCommand(port_.c_str(), "encexp", g_Mesg_Send_term);
		ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
		if (ret != DEVICE_OK) 
			return ret;	
		splitString((char*)result.c_str(),",",dest);
		m=atoi(dest[1]);		
		pProp->Set((long)m);
	}
	 else if (eAct == MM::AfterSet){	  
		int i;
		char mode[20];
		pProp->Get((long&)i);
		sprintf(mode,"encexp,%d",i);
		int ret = SendSerialCommand(port_.c_str(), mode, g_Mesg_Send_term);
		if (ret!=DEVICE_OK)
			return ret;		
		}	 
	//CDeviceUtils::SleepMs(500);
	return DEVICE_OK;
}
int Stage::OnEncstol(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnEncstol");
	if (eAct == MM::BeforeGet){
		double m;
		std::string result;
		char* dest[50];
		int ret = SendSerialCommand(port_.c_str(), "encstol", g_Mesg_Send_term);
		ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
		if (ret != DEVICE_OK) 
			return ret;	
		splitString((char*)result.c_str(),",",dest);
		m=atof(dest[1]);		
		pProp->Set(m);
	}
	 else if (eAct == MM::AfterSet){	  
		double d;
		char loop[20];
		pProp->Get(d);
		sprintf(loop,"encstol,%.3f",d);
		int ret = SendSerialCommand(port_.c_str(), loop, g_Mesg_Send_term);
		if (ret!=DEVICE_OK)
			return ret;		
		}	 
	//CDeviceUtils::SleepMs(500);
	return DEVICE_OK;
}
int Stage::OnEncstcl(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnEncstcl");
	if (eAct == MM::BeforeGet){
		double m;
		std::string result;
		char* dest[50];
		int ret = SendSerialCommand(port_.c_str(), "encstcl", g_Mesg_Send_term);
		ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
		if (ret != DEVICE_OK) 
			return ret;	
		splitString((char*)result.c_str(),",",dest);
		m=atof(dest[1]);		
		pProp->Set(m);
	}
	 else if (eAct == MM::AfterSet){	  
		double d;
		char loop[20];
		pProp->Get(d);
		sprintf(loop,"encstcl,%.3f",d);
		int ret = SendSerialCommand(port_.c_str(), loop, g_Mesg_Send_term);
		if (ret!=DEVICE_OK)
			return ret;		
		}	 
	//CDeviceUtils::SleepMs(500);
	return DEVICE_OK;
}
