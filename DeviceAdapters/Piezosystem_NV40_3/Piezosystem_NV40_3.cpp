///////////////////////////////////////////////////////////////////////////////
// FILE:          Piezosystem_NV40_3.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Piezosystem Jena device adapter.
//					NV40/3 is a 3 channel device which can control diffrent actuators.
//					The actuator has a small memory with the values for the amplifier.
//					There are two version of controller NV40/3 and NV40/1 .
//					The controller has USB(VCP) and RS232-interface.
//					ATTENTION: Extern use channel 1-3, intern use channel 0-2
//                
// AUTHOR:        Chris Belter, cbelter@piezojena.com 4/09/2013, XYStage and ZStage by Chris Belter
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

#include "Piezosystem_NV40_3.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
#include <math.h>

//Port-Control
const char* g_Mesg_Send_term ="\r";  //CR 
const char* g_Mesg_Receive_term ="\r";  //CR
//Controller
const char* g_NV40_3 = "PSJ_NV40/3";
// single axis stage
const char* g_StageDeviceName  = "PSJ_Stage";
const char* g_StageDeviceName1 = "PSJ_Stage1";
const char* g_StageDeviceName2 = "PSJ_Stage2";
const char* g_StageDeviceName3 = "PSJ_Stage3";


//const char* g_Channel			= "Channel";
const char* g_Port1				= "PIEZO 1";
const char* g_Port2				= "PIEZO 2";
const char* g_Port3				= "PIEZO 3";

// XYStage
const char* g_XYStageDeviceName = "PSJ_XYStage";
// Tritor
const char* g_Tritor				= "PSJ_Tritor";
// Shutter
const char* g_Shutter1			= "PSJ_Shutter1";
const char* g_Shutter2			= "PSJ_Shutter2";
const char* g_Shutter3			= "PSJ_Shutter3";

const char* g_ShutterState		= "Shutter State";
const char* g_Open				= "open";
const char* g_Close				= "close";

const char* g_Version			= "Version (zero volt position)";
const char* g_Version1			= "edges open";
const char* g_Version2			= "edges closed";
//Properties

const char* g_Channel		= "Channel";
const char* g_ChannelX		= "Channel X";
const char* g_ChannelY		= "Channel Y";
const char* g_ChannelZ		= "Channel Z";
const char* g_Channel_		= "Channel_";
const char* g_ChannelX_		= "Channel_x";
const char* g_ChannelY_		= "Channel_y";
const char* g_ChannelZ_		= "Channel_z";
const char* g_Axis				= "Axisname";

const char* g_Loop				= "Loop";
const char* g_Loop_open			= "open loop";
const char* g_Loop_close		= "close loop";
const char* g_LoopX				= "Loop x";
const char* g_LoopY				= "Loop y";
const char* g_LoopZ				= "Loop z";

const char* g_Remote				= "Remote control";
const char* g_RemoteX			= "Remote control x";
const char* g_RemoteY			= "Remote control y";
const char* g_Remote_on			= "on";
const char* g_Remote_off		= "off";
const char* g_Status				= "Status";
const char* g_StatusX			= "Status x";
const char* g_StatusY			= "Status y";
const char* g_StatusZ			= "Status z";

const char* g_Limit_V_Min		= "Limit Voltage min [V]";
const char* g_Limit_V_MinX		= "Limit Voltage min x [V]";
const char* g_Limit_V_MinY		= "Limit Voltage min y [V]";
const char* g_Limit_V_MinZ		= "Limit Voltage min z [V]";
const char* g_Limit_V_Max		= "Limit Voltage max [V]";
const char* g_Limit_V_MaxX		= "Limit Voltage max x [V]";
const char* g_Limit_V_MaxY		= "Limit Voltage max y [V]";
const char* g_Limit_V_MaxZ		= "Limit Voltage max z [V]";
const char* g_Limit_Um_Min		= "Limit um min [microns]";
const char* g_Limit_Um_MinX	= "Limit um min x [microns]";
const char* g_Limit_Um_MinY	= "Limit um min y [microns]";
const char* g_Limit_Um_MinZ	= "Limit um min z [microns]";
const char* g_Limit_Um_Max		= "Limit um max [microns]";
const char* g_Limit_Um_MaxX	= "Limit um max x [microns]";
const char* g_Limit_Um_MaxY	= "Limit um max y [microns]";
const char* g_Limit_Um_MaxZ	= "Limit um max z [microns]";
const char* g_Voltage			= "Voltage [V]";
const char* g_VoltageX			= "Voltage x [V]";
const char* g_VoltageY			= "Voltage y [V]";
const char* g_VoltageZ			= "Voltage z [V]";
const char* g_Position			= "Position [microns]";
const char* g_PositionX			= "Position x [microns]";
const char* g_PositionY			= "Position y [microns]";
const char* g_PositionZ			= "Position z [microns]";

const char* g_Monitor			= "monitor output";
const char* g_MonitorX			= "monitor output x";
const char* g_MonitorY			= "monitor output y";
const char* g_Monitor0			= "0 actuator voltage";		//setpoint value
const char* g_Monitor1			= "1 actuator position";	//actual value
const char* g_Monitor2			= "2 operation dependent";	//open loop= setpoint value; close loop= actual value

const char* g_Encoder_Mode		= "encoder: mode";
const char* g_Encoder_Mode0	= "EM0: normal with acceleration";
const char* g_Encoder_Mode1	= "EM1: adjustable interval";
const char* g_Encoder_Mode2	= "EM2: adjustable interval with acceleration";

const char* g_Encoder_Time		= "encoder: sample interval [ x * 0.02s ]" ; //0...255 * 0.02sec
const char* g_Encoder_Limit	= "encoder: maximum step";							//1...65535
const char* g_Encoder_Exponent = "encoder: exponent for calculation of acceleration";
const char* g_Encoder_Open		= "encoder: interval for open loop [V]";		//0.001...150.0 Voltage
const char* g_Encoder_Close	= "encoder: interval for close loop [um]";	//0.001...100.0 miro meter
const char* g_Version_Ver		= "version: ver";
const char* g_Version_Date		= "version: date";
const char* g_Version_Serno	= "version: serno";
const char* g_Actor_Serno		= "Actuator serno";

const char* g_Fready				= "device soft start";
const char* g_Fenable			= "actuator soft start";
const char* g_FenableX			= "actuator soft start x";
const char* g_FenableY			= "actuator soft start y";
const char* g_Fenable_Off		= "soft start disable";
const char* g_Fenable_On		= "soft start enable";
const char* g_Light				= "display brightness";		//0...255

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{		
	RegisterDevice(g_NV40_3, MM::GenericDevice, "Piezosystem Jena NV40 Multi");
	RegisterDevice(g_StageDeviceName1, MM::StageDevice, "Single Axis Stage Ch1");
	RegisterDevice(g_StageDeviceName2, MM::StageDevice, "Single Axis Stage Ch2");
	RegisterDevice(g_StageDeviceName3, MM::StageDevice, "Single Axis Stage Ch3");
	RegisterDevice(g_XYStageDeviceName, MM::XYStageDevice, "Two Axis XY Stage");
	//RegisterDevice(g_Shutter1, MM::ShutterDevice, "PSJ Shutter Ch1");
	//RegisterDevice(g_Shutter2, MM::ShutterDevice, "PSJ Shutter Ch2");
	//RegisterDevice(g_Shutter3, MM::ShutterDevice, "PSJ Shutter Ch3");
	//RegisterDevice(g_Tritor, MM::GenericDevice, "PSJ Tritor");
}             

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{	
	if (deviceName == 0)      return 0;		
	if (strcmp(deviceName, g_NV40_3) == 0){
		hub = new Hub(g_NV40_3);
		MM::Device* pdev=	 hub; 
		return pdev;}	
	else if (strcmp(deviceName, g_StageDeviceName1) == 0){		
		return new Stage(0);	}
	else if (strcmp(deviceName, g_StageDeviceName2) == 0){		
		return new Stage(1);	}
	else if (strcmp(deviceName, g_StageDeviceName3) == 0){		
		return new Stage(2);	}	
	else if (strcmp(deviceName, g_XYStageDeviceName) == 0){		
		return new XYStage();	} 	 	 
	/*
	else if (strcmp(deviceName, g_Tritor) == 0){				
		return new Tritor(g_Tritor); } 		 
	*/
//	else if (strcmp(deviceName, g_Shutter) == 0 ){		
//		return new Shutter(0,g_Shutter);   }
//	else if (strcmp(deviceName, g_Shutter1) == 0 ){		
//		return new Shutter(0,g_Shutter);   }
//	else if (strcmp(deviceName, g_Shutter2) == 0 ){		
//		return new Shutter(1,g_Shutter);   }
//	else if (strcmp(deviceName, g_Shutter3) == 0 ){		
//		return new Shutter(2,g_Shutter);   }
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
Hub::Hub(const char* devicename) :
   name_(devicename),
   initialized_(false)
{
	LogMessage ("PSJ new Hub");
   InitializeDefaultErrorMessages();
	SetErrorText(ERR_SET_POSITION_FAILED, "Wrong Position. Look to the limits");
	SetErrorText(ERR_ONLY_OPEN_LOOP, "Close loop not possible, pherhaps you have the wrong actuator");
	SetErrorText(ERR_REMOTE, "Not possible. First switch remote control to on");

	// pre-initialization properties
   // Port:
   CPropertyAction* pAct = new CPropertyAction(this, &Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}
Hub::~Hub()
{
   Shutdown();
}
int Hub::Shutdown()
{
   if (initialized_)
      initialized_ = false;

   return DEVICE_OK;
}
void Hub::GetName(char* name) const
{
	if(!name_){	
		CDeviceUtils::CopyLimitedString(name, g_NV40_3);
	}else{
		CDeviceUtils::CopyLimitedString(name,name_);
	}
}
bool Hub::Busy()
{
   return false;
}

int Hub::Initialize()
{
	LogMessage ("PSJ Hub Init");
   clearPort(*this, *GetCoreCallback(), port_.c_str());

	GetDevice(device_);
   // Name   
   //int ret = CreateProperty(MM::g_Keyword_Name, g_NV40_3, MM::String, true);
	int ret = CreateProperty(MM::g_Keyword_Name, device_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

	ret = GetLimitsValues();
	if (DEVICE_OK != ret)
      return ret;
	//Not alwasy possible, only with the bigger display like NV120
	//CPropertyAction* pAct = new CPropertyAction (this, &Hub::OnLight);
   //CreateProperty(g_Light, "0", MM::Integer, false, pAct,false);
	//SetPropertyLimits(g_Light, 0, 255);

	CPropertyAction* pAct = new CPropertyAction (this, &Hub::OnEncmode);
   CreateProperty(g_Encoder_Mode, g_Encoder_Mode0, MM::String, false, pAct,false);
	AddAllowedValue(g_Encoder_Mode , g_Encoder_Mode0);
   AddAllowedValue(g_Encoder_Mode , g_Encoder_Mode1);
	AddAllowedValue(g_Encoder_Mode , g_Encoder_Mode2);

	pAct = new CPropertyAction (this, &Hub::OnEnctime);
   CreateProperty(g_Encoder_Time, "0", MM::Integer, false, pAct,false);
	SetPropertyLimits(g_Encoder_Time, 0, 255); 
	pAct = new CPropertyAction (this, &Hub::OnEnclim);
   CreateProperty(g_Encoder_Limit, "1000", MM::Integer, false, pAct,false);
	SetPropertyLimits(g_Encoder_Limit, 1, 65535);
	pAct = new CPropertyAction (this, &Hub::OnEncexp);
   CreateProperty(g_Encoder_Exponent, "3", MM::Integer, false, pAct,false);
	SetPropertyLimits(g_Encoder_Exponent, 1, 10);

	pAct = new CPropertyAction (this, &Hub::OnEncstol);
   CreateProperty(g_Encoder_Open, "1.0", MM::Float, false, pAct,false);
	SetPropertyLimits(g_Encoder_Open, 0.001, 100.0);
	pAct = new CPropertyAction (this, &Hub::OnEncstcl);
   CreateProperty(g_Encoder_Close, "1.0", MM::Float, false, pAct,false);
	SetPropertyLimits(g_Encoder_Close, 0.001, 100.0); 

	pAct = new CPropertyAction (this, &Hub::OnSoftstart);
   CreateProperty(g_Fready, g_Fenable_Off, MM::String, false, pAct);
   AddAllowedValue(g_Fready , g_Fenable_Off);
   AddAllowedValue(g_Fready, g_Fenable_On);

	ret= GetVersion();
	CreateProperty(g_Version_Ver, ver_.c_str(), MM::String, true);
	CreateProperty(g_Version_Serno, serno_.c_str(), MM::String, true); 

	ret = GetRemoteValues();

	ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

bool Hub::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus Hub::DetectDevice(void)
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
		   std::string v;
         int qvStatus = this->GetDevice(v);
         //LogMessage(std::string("version : ")+v, true);
			device_=v;
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
int Hub::GetDevice(std::string& device)
{
   int returnStatus = DEVICE_OK;
   
   PurgeComPort(port_.c_str());
   // Version of the controller:

   const char* cm = "";		//Get Devicename
   returnStatus = SendSerialCommand(port_.c_str(), cm, g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;

   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), ">\r", device);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
   if (device.length() < 2) {
      // if we get no answer, try other port
      LogMessage("There is no device. Try other Port",true);   
	  // no answer, 
      return ERR_NO_ANSWER;
   }
   return returnStatus;
}	 
int Hub::GetLight(int& l){
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

int Hub::GetVersion()
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
int Hub::GetStatus(int ch,int& stat){
	LogMessage("GetStatus");
	char s[20];
	sprintf(s,"stat,%d",ch);
	std::string result;
	int returnStatus = SendSerialCommand(port_.c_str(),s , g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
	char* dest[50];
	splitString((char*)result.c_str(),",",dest);
	std::string type=dest[0];
	if(type=="ERROR"){
		//TODO: ERROR Message (look at OnLoop)
		clearPort(*this, *GetCoreCallback(), port_.c_str());
		//repeat
		returnStatus = SendSerialCommand(port_.c_str(),s , g_Mesg_Send_term);
		if (returnStatus != DEVICE_OK) 
			return returnStatus;
		returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
		if (returnStatus != DEVICE_OK) 
			return returnStatus;
		splitString((char*)result.c_str(),",",dest);
		type=dest[0];
	}
	if(type=="STATUS"){
		stat=atoi(dest[2]);
		//stat_=stat;
		//remote_=((stat&STATUS_REMOTE)==STATUS_REMOTE)?true:false;
		//remoteCh_[nr_]=remote_;
		//loop_= ((stat&STATUS_LOOP)==STATUS_LOOP)?true:false;
		//monwpa_=(stat&STATUS_MONWPA2)>>5;		  //only set on start, no change	 		
	}else{
		stat=0;
	}
	//CDeviceUtils::SleepMs(500);
	return returnStatus;
}
int Hub::GetLoop(int ch,bool& loop){
	LogMessage("GetLoop");
	std::string result;
	char cmd[20];
	sprintf(cmd,"cloop,%d",ch);
	int returnStatus = SendSerialCommand(port_.c_str(),cmd , g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
	CDeviceUtils::SleepMs(200);
   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
	char* dest[50];
	int l;
	
	splitString((char*)result.c_str()," ,\n",dest);		
	std::string type=dest[0];
	if(type=="cloop"){
		l=atoi(dest[2]);
		loop=(l==1)?true:false;
	}else{		
		loop=false;
		clearPort(*this, *GetCoreCallback(), port_.c_str());
	}
	return returnStatus;
}
int Hub::GetLimits(double& min, double& max)
{	
	min = min_V_;
	max = max_V_;

	return DEVICE_OK;
}
int Hub::GetLimitsValues()
{
	int ret;
	//min_V_
	ret = GetCommandValue("dspvmin",min_V_);
	if (ret != DEVICE_OK) 
      return ret;	
	//ret = SendSerialCommand(port_.c_str(), "dspvmin", g_Mesg_Send_term);
   //if (ret != DEVICE_OK) 
   //   return ret;   
   //ret = GetSerialAnswer(port_.c_str(), "\r", result);  
   //if (ret != DEVICE_OK) 
   //   return ret;		
	//splitString((char*)result.c_str(),",",dest);
	//min_V_=atoi(dest[1]);
	
	//max_V_
	ret = GetCommandValue("dspvmax",max_V_);
	if (ret != DEVICE_OK) 
      return ret;	
	//ret = SendSerialCommand(port_.c_str(), "dspvmax", g_Mesg_Send_term);
   //if (ret != DEVICE_OK) 
   //   return ret;   
   //ret = GetSerialAnswer(port_.c_str(), "\r", result);  
   //if (ret != DEVICE_OK) 
   //   return ret;		
	//splitString((char*)result.c_str(),",",dest);
	//max_V_=atoi(dest[1]); 

	return DEVICE_OK;
}
int Hub::GetRemoteValues(){
	char s[20];
	int stat=0;
	int returnStatus=0;
	std::string result;
	std::string type;
	char* dest[50];
	for (int i=0;i<3;i++){
		sprintf(s,"stat,%d",i);	
		returnStatus = SendSerialCommand(port_.c_str(),s , g_Mesg_Send_term);
		if (returnStatus != DEVICE_OK) 
			return returnStatus;
		// Read out result
		returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
		if (returnStatus != DEVICE_OK) 
			 return returnStatus;		
		splitString((char*)result.c_str(),",",dest);
		type=dest[0];	
		if(type=="STATUS"){
			stat=atoi(dest[2]);			
			remoteCh_[i]=((stat&STATUS_REMOTE)==STATUS_REMOTE)?true:false;		
		}
	}
	return DEVICE_OK ;
}
int Hub::SendCommand(const char* cmd,std::string &result){
	int ret;	
	LogMessage (cmd,true);
	ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	CDeviceUtils::SleepMs(200);	
	ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}

int Hub::ErrorMessage(int error){
	if((error&ERROR_ACTUATOR)==ERROR_ACTUATOR)
		LogMessage ("actor is missing");	 //there is no actuator pluged
	if((error&ERROR_UDL)==ERROR_UDL)
		LogMessage ("loop underload");		//position to low
	if((error&ERROR_OVL)==ERROR_OVL)
		LogMessage ("loop overflow");			//position to high 
	if((error&ERROR_WRONG_ACTUATOR)==ERROR_WRONG_ACTUATOR)
		LogMessage ("wrong actuator");	//there is a different between values in pluged actuator an the values in the device
	if((error&ERROR_HEAT)==ERROR_HEAT)
		LogMessage ("temperture to high"); //need a cooling
	//TODO: Message to the user, more than 1 error is possible
	clearPort(*this, *GetCoreCallback(), port_.c_str());

	return error;
}
int Hub::GetCommandValue(const char* c,double& d){
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
	}else if(type=="ERROR"){
		d=-1.0;
		for(int i=1;i<4;i++){
		int error=atoi(dest[i]);
			if((error&ERROR_ACTUATOR)==ERROR_ACTUATOR)
				LogMessage ("actor is missing");
			if((error&ERROR_UDL)==ERROR_UDL)
				LogMessage ("loop underload");
			if((error&ERROR_OVL)==ERROR_OVL)
				LogMessage ("loop overflow");
			if((error&ERROR_WRONG_ACTUATOR)==ERROR_WRONG_ACTUATOR)
				LogMessage ("wrong actuator");	//there is a different between pluged actuator an the values in the device
			if((error&ERROR_HEAT)==ERROR_HEAT)
				LogMessage ("temperture to high");
			//TODO: Message to the user
		}
		clearPort(*this, *GetCoreCallback(), port_.c_str());
	} else{
		LogMessage ("Wrong Result",true);	
		LogMessage (dest[0],true);
		d=-1.0;
		//ret =PurgeComPort(port_.c_str());
		clearPort(*this, *GetCoreCallback(), port_.c_str());
		CDeviceUtils::SleepMs(10);
	}		
	return DEVICE_OK;
}
int Hub::SetCommandValue(const char* c,double fkt){
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


int Hub::GetCommandValue(const char* c,int& i){
	LogMessage ("Get command value i");

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
		i=atoi(dest[1]);
	}else if(type=="ERROR"){
		i=-1;
		for(int e=1;e<4;e++){
			int error=atoi(dest[e]);
			if((error&ERROR_ACTUATOR)==ERROR_ACTUATOR)
				LogMessage ("actor is missing");
			if((error&ERROR_UDL)==ERROR_UDL)
				LogMessage ("loop underload");
			if((error&ERROR_OVL)==ERROR_OVL)
				LogMessage ("loop overflow");
			if((error&ERROR_WRONG_ACTUATOR)==ERROR_WRONG_ACTUATOR)
				LogMessage ("wrong actuator");	//there is a different between pluged actuator an the values in the device
			if((error&ERROR_HEAT)==ERROR_HEAT)
				LogMessage ("temperture to high");
		//TODO: Message to the user
		}
		clearPort(*this, *GetCoreCallback(), port_.c_str());
	} else{
		LogMessage ("Wrong Result",true);	
		LogMessage (dest[0],true);
		i=-1;
		//ret =PurgeComPort(port_.c_str());
		clearPort(*this, *GetCoreCallback(), port_.c_str());
		CDeviceUtils::SleepMs(10);
	}		
	return DEVICE_OK;
}
int Hub::SetCommandValue(const char* c,int fkt){
	LogMessage ("Set command value d");
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
int Hub::GetCommandValue(const char* c,int ch,double& d){
	LogMessage ("Get command value d");

	char str[50]="";
	sprintf(str,"%s,%d",c,ch);	
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
		d=atof(dest[2]);
	}else if(type=="ERROR"){ 		
		int error=atoi(dest[ch+1]);
		ErrorMessage(error);
	} else{
		LogMessage ("Wrong Result",true);	
		LogMessage (dest[0],true);
		d=0.0; 		
		ret = clearPort(*this, *GetCoreCallback(), port_.c_str());
		//CDeviceUtils::SleepMs(10);
	}		
	return DEVICE_OK;
}
int Hub::SetCommandValue(const char* c,int ch,double fkt){
	LogMessage ("Set command value d");
	char str[50]="";
	sprintf(str,"%s,%d,%.3lf",c,ch,fkt);	
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
int Hub::GetCommandValue(const char* c,int ch,int& i){
	LogMessage ("Get command value d");

	char str[50]="";
	sprintf(str,"%s,%d",c,ch);	
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
	}else if(type=="ERROR"){
		int error=atoi(dest[ch+1]);
		ErrorMessage(error);
	} else{
		LogMessage ("Wrong Result",true);	
		LogMessage (dest[0],true);
		i=0; 		
		ret = clearPort(*this, *GetCoreCallback(), port_.c_str());
		//CDeviceUtils::SleepMs(10);
	}		
	return DEVICE_OK;
}
int Hub::SetCommandValue(const char* c,int ch,int fkt){
	LogMessage ("Set command value d");
	char str[50]="";
	sprintf(str,"%s,%d,%d",c,ch,fkt);	
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

//////////////// Action Handlers (Hub) /////////////////

int Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
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
int Hub::OnLight(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnLight");
	if (eAct == MM::BeforeGet){
		int i;		
		int ret=GetLight(i);		
		if (ret!=DEVICE_OK)
			return ret;
		bright_=i;
		pProp->Set((long)bright_);
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










int Hub::OnEncmode(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnEncmode");
	if (eAct == MM::BeforeGet){
		int m;		
		GetCommandValue("encmode",m);
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
		std::string m;
		pProp->Get(m);
		if(m==g_Encoder_Mode0)
			i=0;
		if(m==g_Encoder_Mode1)
			i=1;
		if(m==g_Encoder_Mode2)
			i=2;
			
		SetCommandValue("encmode",i);
		}		
	return DEVICE_OK;
}
int Hub::OnEnctime(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnEnctime");
	if (eAct == MM::BeforeGet){
		int m;		
		GetCommandValue("enctime",m);
		pProp->Set((long)m);
	}
	 else if (eAct == MM::AfterSet){	  
		int i;		
		pProp->Get((long&)i);		
		int ret = SetCommandValue("enctime",i);
		if (ret!=DEVICE_OK)
			return ret;		
		}	 
	
	return DEVICE_OK;
}
int Hub::OnEnclim(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnEnclim");
	if (eAct == MM::BeforeGet){
		int m;		
		GetCommandValue("enclim",m);
		pProp->Set((long)m);
	}
	 else if (eAct == MM::AfterSet){	  
		int i; 		
		pProp->Get((long&)i);		
		int ret = SetCommandValue("enclim",i);
		if (ret!=DEVICE_OK)
			return ret;		
		}	 
	//CDeviceUtils::SleepMs(500);
	return DEVICE_OK;
}
int Hub::OnEncexp(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnEncexp");
	if (eAct == MM::BeforeGet){
		int m;			
		GetCommandValue("encexp",m);
		pProp->Set((long)m);
	}
	 else if (eAct == MM::AfterSet){	  
		int i;		
		pProp->Get((long&)i);		
		int ret = SetCommandValue("encexp",i);
		if (ret!=DEVICE_OK)
			return ret;		
		}	
	return DEVICE_OK;
}
int Hub::OnEncstol(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnEncstol");
	if (eAct == MM::BeforeGet){
		double m;		
		GetCommandValue("encstol",m);
		pProp->Set(m);
	}
	 else if (eAct == MM::AfterSet){	  
		double d;
		pProp->Get(d);
		int ret = SetCommandValue("encstol",d);	
		if (ret!=DEVICE_OK)
			return ret;	
	}
	return DEVICE_OK;
}
int Hub::OnEncstcl(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnEncstcl");
	if (eAct == MM::BeforeGet){
		double m; 		
		GetCommandValue("encstcl",m);
		pProp->Set(m);
	}
	 else if (eAct == MM::AfterSet){	  
		double d;
		pProp->Get(d);
		int ret = SetCommandValue("encstcl",d);
		if (ret!=DEVICE_OK)
			return ret;	
		}	
	return DEVICE_OK;
}

int Hub::OnSoftstart(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){		
		int i=0;
		int ret = GetCommandValue("fready",i);
		if (ret != DEVICE_OK)  
			return ret;	
		fready_=(i==1)?true:false;		
		if (ret!=DEVICE_OK)
			return ret;
		if (fready_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		int i;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			fready_ = true;
			i=1;
		}else{
			fready_ = false;
			i=0;			
		}
		int ret = SetCommandValue("fready",i);
		if (ret!=DEVICE_OK)
				return ret;
	}
	//CDeviceUtils::SleepMs(300);
   return DEVICE_OK;
}

/**
 * Single axis stage.
 */
Stage::Stage(int nr):
	answerTimeoutMs_(500),
   initialized_(false),
	loop_(false),
	min_V_(-20.0),
	max_V_(130.0),
	min_um_(0.0),
	max_um_(100.0),
	nr_(nr),
	pos_(0.0),
	voltage_(0)
{
	InitializeDefaultErrorMessages();

	// custemer error message
	SetErrorText(ERR_SET_POSITION_FAILED, "Wrong Position. Look to the limits");
	SetErrorText(ERR_ONLY_OPEN_LOOP, "Close loop not possible, pherhaps you have the wrong actuator");
	SetErrorText(ERR_REMOTE, "Not possible. First switch remote control to on");
	
   // Description
   CreateProperty(MM::g_Keyword_Description, "Piezosystem stage driver adapter", MM::String, true);
	
	// Port:
	//CPropertyAction *pAct = new CPropertyAction(this, &Stage::OnPort);
   //CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);	
	
	// Channel:
	char p[20];
	sprintf(p,"PIEZO %d",nr_);
	CPropertyAction *pAct = new CPropertyAction(this, &Stage::OnChannel);
   CreateProperty(MM::g_Keyword_Channel, p, MM::String, false, pAct, true);
	AddAllowedValue(MM::g_Keyword_Channel, g_Port1);
   AddAllowedValue(MM::g_Keyword_Channel, g_Port2);
	AddAllowedValue(MM::g_Keyword_Channel, g_Port3); 
}

Stage::~Stage()
{
   Shutdown();
}


//////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void Stage::GetName(char* Name) const
{ 
	std::ostringstream name;
	name<<g_StageDeviceName<<(nr_+1);
	CDeviceUtils::CopyLimitedString(Name, name.str().c_str());

   CDeviceUtils::CopyLimitedString(Name, g_StageDeviceName);
}

int Stage::Initialize()
{
	 // Name
	std::string name=g_StageDeviceName;
	GetActorname(acname_);
   CreateProperty(MM::g_Keyword_Name, acname_.c_str() , MM::String, true);

	//Axis
	GetAxisname(axisname_);
	CreateProperty(g_Axis, axisname_.c_str() , MM::String, true);


	CPropertyAction* pAct = new CPropertyAction (this, &Stage::OnStat);
   CreateProperty(g_Status, "0", MM::Integer, true, pAct,false); 

	pAct = new CPropertyAction (this, &Stage::OnLoop);
   CreateProperty(g_Loop, g_Loop_open, MM::String, false, pAct,false);
   AddAllowedValue(g_Loop, g_Loop_open);
   AddAllowedValue(g_Loop, g_Loop_close);
	//SetProperty(g_Loop, g_Loop_open);


	pAct = new CPropertyAction (this, &Stage::OnRemote);
   CreateProperty(g_Remote, g_Remote_on, MM::String, false, pAct,false);
   AddAllowedValue(g_Remote, g_Remote_on);
   AddAllowedValue(g_Remote, g_Remote_off);
	//SetProperty(g_Remote, g_Remote_on);

	pAct = new CPropertyAction (this, &Stage::OnMon);
	CreateProperty(g_Monitor, g_Monitor1, MM::String, false, pAct,false);
	AddAllowedValue(g_Monitor, g_Monitor0);
   AddAllowedValue(g_Monitor, g_Monitor1);
	AddAllowedValue(g_Monitor, g_Monitor2);

	pAct = new CPropertyAction (this, &Stage::OnSoftstart);
   CreateProperty(g_Fenable, g_Fenable_Off, MM::String, false, pAct);
   AddAllowedValue(g_Fenable , g_Fenable_Off);
   AddAllowedValue(g_Fenable, g_Fenable_On);

	int ret = GetLimitValues();
	if (DEVICE_OK != ret)
      return ret;
	//CPropertyAction *pAct = new CPropertyAction (this, &Stage::OnMinV);		
	CreateProperty(g_Limit_V_Min, CDeviceUtils::ConvertToString(min_V_), MM::Float, true);
	//pAct = new CPropertyAction (this, &Stage::OnMaxV);	;	
	CreateProperty(g_Limit_V_Max, CDeviceUtils::ConvertToString(max_V_), MM::Float, true);
	//pAct = new CPropertyAction (this, &Stage::OnMinUm);		
	CreateProperty(g_Limit_Um_Min,CDeviceUtils::ConvertToString(min_um_), MM::Float, true);
	//pAct = new CPropertyAction (this, &Stage::OnMaxUm);	
	CreateProperty(g_Limit_Um_Max, CDeviceUtils::ConvertToString(max_um_), MM::Float, true);

	pAct = new CPropertyAction (this, &Stage::OnPosition);
	CreateProperty(g_Position, "0", MM::Float, false, pAct,false);

	pAct = new CPropertyAction (this, &Stage::OnVoltage);
	CreateProperty(g_Voltage, "0", MM::Float, true, pAct,false);


		
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
	int s;
	int ret;
	ret = GetStatus(s);
	if(!remote_)
		return ERR_REMOTE;
	if(loop_){ //close loop
		pos_=pos;
		voltage_=(max_V_-min_V_)*(pos-min_um_)/(max_um_-min_um_)+min_V_; //Translate Pos->Voltage
		ret=hub->SetCommandValue("set",nr_,pos_);
	}else{  //open loop
		pos_=pos;
		voltage_=(max_V_-min_V_)*(pos-min_um_)/(max_um_-min_um_)+min_V_; //Translate Pos->Voltage		
		ret=hub->SetCommandValue("set",nr_,voltage_);
	}
	if (ret != DEVICE_OK)
      return ret;	

	//CDeviceUtils::SleepMs(200);
	return DEVICE_OK;
}

int Stage::GetPositionUm(double& pos){
	LogMessage ("GetPositionUm");
	int s,ret;	
	double d;
	ret = GetStatus(s);
	ret = hub->GetCommandValue("rk",nr_,d);
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
// User API
int Stage::GetLoop(bool& loop){
	LogMessage("GetLoop");
	std::string result;
	char cmd[20];
	sprintf(cmd,"cloop,%d",nr_);
	int returnStatus = SendSerialCommand(port_.c_str(),cmd , g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
	CDeviceUtils::SleepMs(200);
   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
	char* dest[50];
	int l;
	
	splitString((char*)result.c_str()," ,\n",dest);		
	std::string type=dest[0];
	if(type=="cloop"){
		l=atoi(dest[2]);
		loop=(l==1)?true:false;
	}else{
		loop=loop_;
		clearPort(*this, *GetCoreCallback(), port_.c_str());
	}
	return returnStatus;
}
int Stage::GetDevice(std::string& version)
{
   int returnStatus = DEVICE_OK;
   
   PurgeComPort(port_.c_str());
   // Version of the controller:

   const char* cm = "";		//Get Devicename
   returnStatus = SendSerialCommand(port_.c_str(), cm, g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;

   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), ">\r", version);  
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
int Stage::SendCommand(const char* cmd,std::string &result){
	int ret;	
	LogMessage (cmd,true);
	ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	//CDeviceUtils::SleepMs(50);	 
	ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}	
int Stage::GetStatus(int& stat){
	LogMessage("GetStatus");
	char s[20];
	sprintf(s,"stat,%d",nr_);
	std::string result;
	int returnStatus = SendSerialCommand(port_.c_str(),s , g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
	char* dest[50];
	splitString((char*)result.c_str(),",",dest);
	std::string type=dest[0];
	if(type=="ERROR"){
		//TODO: ERROR Message (look at OnLoop)
		clearPort(*this, *GetCoreCallback(), port_.c_str());
		//repeat
		returnStatus = SendSerialCommand(port_.c_str(),s , g_Mesg_Send_term);
		if (returnStatus != DEVICE_OK) 
			return returnStatus;
		returnStatus = GetSerialAnswer(port_.c_str(), "\r", result);  
		if (returnStatus != DEVICE_OK) 
			return returnStatus;
		splitString((char*)result.c_str(),",",dest);
		type=dest[0];
	}
	if(type=="STATUS"){
		stat=atoi(dest[2]);
		stat_=stat;
		remote_=((stat&STATUS_REMOTE)==STATUS_REMOTE)?true:false;
		remoteCh_[nr_]=remote_;
		loop_= ((stat&STATUS_LOOP)==STATUS_LOOP)?true:false;
		//monwpa_=(stat&STATUS_MONWPA2)>>5;		  //only set on start, no change
		char value[20];
		sprintf(value,"Monwpa,%d",monwpa_);
		LogMessage(value);
	}else{
		stat=stat_;
	}
	//CDeviceUtils::SleepMs(500);
	return returnStatus;
}
int Stage::GetLimitValues()
{
	int ret;	  			
	//min_um_ 
	ret = hub->GetCommandValue("dspclmin",nr_,min_um_);
	if (ret != DEVICE_OK) 
      return ret;				
	//max_um_
	ret = hub->GetCommandValue("dspclmax",nr_,max_um_);
	if (ret != DEVICE_OK) 
      return ret;
	ret = hub->GetLimits(min_V_,max_V_);
	if (ret != DEVICE_OK) 
      return ret;

	return DEVICE_OK;
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

/*
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
*/
int Stage::OnChannel(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet){
		switch(nr_){
		case 0:
			pProp->Set(g_Port1);
			break;
		case 1:
			pProp->Set(g_Port2);
			break;
		case 2:
			pProp->Set(g_Port3);
			break;
		}
   } else if (pAct == MM::AfterSet){ 
		std::string p;
		pProp->Get(p);
		if(p==g_Port1){
			nr_=0 ;
		}
		if(p==g_Port2){
			nr_=1 ;
		}
      if(p==g_Port3){
			nr_=2 ;
		}
		
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
int Stage::OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnStat");
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
int Stage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct){	
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
		char cmd[20];
      pProp->Get(loop);
		if (loop == g_Loop_close){
         loop_ = true;
			sprintf(cmd,"cloop,%d,1",nr_); 			
			l=1; 
		}else{
         loop_ = false;
			sprintf(cmd,"cloop,%d,0",nr_);			
			l=0;
		}
		int ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
		if (ret!=DEVICE_OK)
				return ret;	 		
		CDeviceUtils::SleepMs(300);
		clearPort(*this, *GetCoreCallback(), port_.c_str());
		sprintf(cmd,"cloop,%d",nr_);
		ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
		ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
		if (ret != DEVICE_OK) 
			return ret;
		char* dest[20];
		splitString((char*)result.c_str(),",",dest);
		int r=atoi(dest[2]);
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
	int l=0;
	if (eAct == MM::BeforeGet)
    {	
		int s=0;
		GetStatus(s);
		if (remote_){
			pProp->Set(g_Remote_on);
			l=1;
		}
		else{
			pProp->Set(g_Remote_off);
			l=0;
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string remote;
	  int i;
	  //char cmd[20];
      pProp->Get(remote);
		if (remote == g_Remote_on){
         remote_ = true;
			//sprintf(cmd,"setk,%d,1",nr_);
			i=1;
		}else{
         remote_ = false;
			//sprintf(cmd,"setk,%d,0",nr_);
			i=0;
		}
		remoteCh_[nr_]=remote_;
		//int ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
		int ret = hub->SetCommandValue("setk",nr_,i);
		if (ret!=DEVICE_OK)
			return ret;		 	  
	}
	//CDeviceUtils::SleepMs(300);
   return DEVICE_OK;
}
int Stage::OnMon(MM::PropertyBase* pProp, MM::ActionType eAct){			
	LogMessage("OnMon");
	
	if (eAct == MM::BeforeGet)
    {
		 //int stat= 0;
		 int ret = 0;
		 
		//Sometimes no answer with remote on,than put on/off remote
		////Put remote off
		//for(int i=0;i<3;i++){
		//	if(remoteCh_[i])
		//		ret = hub->SetCommandValue("setk",i,0);
		//}		
		ret = hub->GetCommandValue("monwpa",nr_,monwpa_);	   	
		////Put remote on again
		//for(int i=0;i<3;i++){
		//	if(remoteCh_[i])
		//		ret = hub->SetCommandValue("setk",i,1);
		//}
		if (ret!=DEVICE_OK)
				return ret;

		switch (monwpa_){
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
			monwpa_=0;
		}else if(mon == g_Monitor1){         
			monwpa_=1;
		}else if(mon == g_Monitor2){         
			monwpa_=2;
		}
		//int ret = SendSerialCommand(port_.c_str(), "monwpa,0", g_Mesg_Send_term);
		int ret = hub->SetCommandValue("monwpa",nr_,monwpa_);
			if (ret!=DEVICE_OK)
				return ret;	
		
	}
	//CDeviceUtils::SleepMs(300);
   return DEVICE_OK;
}
int Stage::OnSoftstart(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){		
		int i=0;
		int ret = hub->GetCommandValue("fenable",nr_,i);
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
		int i;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			fenable_ = true;
			i=1;
		}else{
			fenable_ = false;
			i=0;			
		}
		int ret = hub->SetCommandValue("fenable",nr_,i);
		if (ret!=DEVICE_OK)
				return ret;
	}
	//CDeviceUtils::SleepMs(300);
   return DEVICE_OK;
}

XYStage::XYStage():

//XYStage::XYStage(int xaxis, int yaxis) :    
   initialized_(false),
	stepSizeUm_(0.001),
	x_min_um_(0.0),
   x_max_um_(100.0), 
	y_min_um_(0.0),
   y_max_um_(100.0),
   xChannel_(0), //(xaxis),
   yChannel_(1) //(yaxis),   
   {
	LogMessage ("Init XYStage()");
   InitializeDefaultErrorMessages(); 
	SetErrorText(ERR_SET_POSITION_FAILED, "Wrong Position. Look to the limits");
	SetErrorText(ERR_ONLY_OPEN_LOOP, "Close loop not possible, pherhaps you have the wrong actuator");
	SetErrorText(ERR_REMOTE, "Not possible. First switch remote control to on");

   // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "PSJ XY stage driver adapter", MM::String, true);
	
	CPropertyAction*  pAct = new CPropertyAction (this, &XYStage::OnChannelX);
   CreateProperty(g_ChannelX, "1", MM::Integer, false, pAct,true);
	AddAllowedValue(g_ChannelX, "1");
	AddAllowedValue(g_ChannelX, "2");
	AddAllowedValue(g_ChannelX, "3");

	pAct = new CPropertyAction (this, &XYStage::OnChannelY);
   CreateProperty(g_ChannelY, "2", MM::Integer, false, pAct,true);
	AddAllowedValue(g_ChannelY, "1");
	AddAllowedValue(g_ChannelY, "2");
	AddAllowedValue(g_ChannelY, "3");
}

XYStage::XYStage(int x, int y):     
   initialized_(false), 
	stepSizeUm_(0.001),
	x_min_um_(0.0),
   x_max_um_(100.0), 
	y_min_um_(0.0),
   y_max_um_(100.0),
   xChannel_(x), //(xaxis),
   yChannel_(y) //(yaxis),   
{
	LogMessage ("Init XYStage()");
   InitializeDefaultErrorMessages();   
	SetErrorText(ERR_SET_POSITION_FAILED, "Wrong Position. Look to the limits");
	SetErrorText(ERR_ONLY_OPEN_LOOP, "Close loop not possible, pherhaps you have the wrong actuator");
	SetErrorText(ERR_REMOTE, "Not possible. First switch remote control to on");
   // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "PSJ XY stage driver adapter", MM::String, true);
	
	CPropertyAction*  pAct = new CPropertyAction (this, &XYStage::OnChannelX);
   CreateProperty(g_ChannelX, CDeviceUtils::ConvertToString(xChannel_+1), MM::Integer, false, pAct,true);
	AddAllowedValue(g_ChannelX, "1");
	AddAllowedValue(g_ChannelX, "2");
	AddAllowedValue(g_ChannelX, "3");

	pAct = new CPropertyAction (this, &XYStage::OnChannelY);
   CreateProperty(g_ChannelY, CDeviceUtils::ConvertToString(yChannel_+1), MM::Integer, false, pAct,true);
	AddAllowedValue(g_ChannelY, "1");
	AddAllowedValue(g_ChannelY, "2");
	AddAllowedValue(g_ChannelY, "3");
}
XYStage::~XYStage()
{
   Shutdown();
}
///////////////////////////////////////////////////////////////////////////////
// XYStage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void XYStage::GetName(char* Name) const
{		
   CDeviceUtils::CopyLimitedString(Name, g_XYStageDeviceName);
}
int XYStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}
int XYStage::Initialize()
{
   LogMessage ("Initialize",true);
   char c[5];   
	//Channel only for Info
   sprintf(c,"%d",xChannel_+1);
   const char* ch=c;   
   CreateProperty(g_ChannelX_, ch, MM::Integer, true);	//read-only 
   sprintf(c,"%d",yChannel_+1);
   ch=c;   
   CreateProperty(g_ChannelY_, ch, MM::Integer, true);  //read-only

	GetLimitValues();   
   CreateProperty(g_Limit_V_MinX, CDeviceUtils::ConvertToString(min_V_), MM::Float, true);		
   CreateProperty(g_Limit_V_MaxX, CDeviceUtils::ConvertToString(max_V_), MM::Float, true);			
   CreateProperty(g_Limit_Um_MinX, CDeviceUtils::ConvertToString(x_min_um_), MM::Float, true);		
   CreateProperty(g_Limit_Um_MaxX, CDeviceUtils::ConvertToString(x_max_um_), MM::Float, true);
   CreateProperty(g_Limit_V_MinY, CDeviceUtils::ConvertToString(min_V_), MM::Float, true);		
   CreateProperty(g_Limit_V_MaxY, CDeviceUtils::ConvertToString(max_V_), MM::Float, true);			
   CreateProperty(g_Limit_Um_MinY, CDeviceUtils::ConvertToString(y_min_um_), MM::Float, true);		
   CreateProperty(g_Limit_Um_MaxY, CDeviceUtils::ConvertToString(y_max_um_), MM::Float, true);

	CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnStatX);
   CreateProperty(g_StatusX, "0", MM::Integer, true, pAct,false); 
	pAct = new CPropertyAction (this, &XYStage::OnStatY);
   CreateProperty(g_StatusY, "0", MM::Integer, true, pAct,false); 

	pAct = new CPropertyAction (this, &XYStage::OnLoopX);
   CreateProperty(g_LoopX, g_Loop_open, MM::String, false, pAct,false);
   AddAllowedValue(g_LoopX, g_Loop_open);
   AddAllowedValue(g_LoopX, g_Loop_close);
   pAct = new CPropertyAction (this, &XYStage::OnLoopY);
   CreateProperty(g_LoopY, g_Loop_open, MM::String, false, pAct,false);
   AddAllowedValue(g_LoopY, g_Loop_open);
   AddAllowedValue(g_LoopY, g_Loop_close);

	pAct = new CPropertyAction (this, &XYStage::OnRemoteX);
   CreateProperty(g_RemoteX, g_Remote_on, MM::String, false, pAct,false);
   AddAllowedValue(g_RemoteX, g_Remote_on);
   AddAllowedValue(g_RemoteX, g_Remote_off);
	pAct = new CPropertyAction (this, &XYStage::OnRemoteY);
   CreateProperty(g_RemoteY, g_Remote_on, MM::String, false, pAct,false);
   AddAllowedValue(g_RemoteY, g_Remote_on);
   AddAllowedValue(g_RemoteY, g_Remote_off);

	pAct = new CPropertyAction (this, &XYStage::OnMonX);
	CreateProperty(g_MonitorX, g_Monitor1, MM::String, false, pAct,false);
	AddAllowedValue(g_MonitorX, g_Monitor0);
   AddAllowedValue(g_MonitorX, g_Monitor1);
	AddAllowedValue(g_MonitorX, g_Monitor2);
	pAct = new CPropertyAction (this, &XYStage::OnMonY);
	CreateProperty(g_MonitorY, g_Monitor1, MM::String, false, pAct,false);
	AddAllowedValue(g_MonitorY, g_Monitor0);
   AddAllowedValue(g_MonitorY, g_Monitor1);
	AddAllowedValue(g_MonitorY, g_Monitor2);

	pAct = new CPropertyAction (this, &XYStage::OnPositionX);
	CreateProperty(g_PositionX, "0", MM::Float, false, pAct,false);
	pAct = new CPropertyAction (this, &XYStage::OnPositionY);
	CreateProperty(g_PositionY, "0", MM::Float, false, pAct,false);

	pAct = new CPropertyAction (this, &XYStage::OnSoftstartX);
   CreateProperty(g_FenableX, g_Fenable_Off, MM::String, false, pAct);
   AddAllowedValue(g_FenableX , g_Fenable_Off);
   AddAllowedValue(g_FenableX , g_Fenable_On);
	pAct = new CPropertyAction (this, &XYStage::OnSoftstartY);
   CreateProperty(g_FenableY, g_Fenable_Off, MM::String, false, pAct);
   AddAllowedValue(g_FenableY , g_Fenable_Off);
   AddAllowedValue(g_FenableY , g_Fenable_On);

   int ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
   
   initialized_ = true;
   return DEVICE_OK;
}
int XYStage::SetPositionUm(double x, double y){
	LogMessage ("SetPositionUm",true);
	int ret;
	long value;
	GetProperty(g_StatusX,value);
	GetProperty(g_StatusY,value);
	if(!(x_remote_|y_remote_))
		//SetProperty(g_RemoteX,g_Remote_on);
		//SetProperty(g_RemoteY,g_Remote_on);
		//or
		return ERR_REMOTE;
	//hub->GetLoop(xChannel_,x_loop_);
	if(x_loop_){
		xpos_=x;
		ret  = hub->SetCommandValue("set",xChannel_,x);
	}else{
		xpos_=x;
		xvoltage_=(max_V_ - min_V_)*(x-x_min_um_)/(x_max_um_-x_min_um_)+ min_V_;
		ret = hub->SetCommandValue("set",xChannel_,xvoltage_);
	}
	if (ret != DEVICE_OK)
      return ret;	
	//hub->GetLoop(yChannel_,y_loop_);
	if(y_loop_){
		ypos_=y;
		ret = hub->SetCommandValue("set",yChannel_,y);
	}else{
		ypos_=y;
		yvoltage_=( max_V_ - min_V_)*(y-y_min_um_)/(y_max_um_-y_min_um_)+ min_V_;
		ret = hub->SetCommandValue("set",yChannel_,yvoltage_);
	}	
	if (ret != DEVICE_OK)
      return ret;

	return DEVICE_OK;
}

int XYStage::GetPositionUm(double& x, double& y){	
	LogMessage ("GetPositionUm",true);
	hub->GetLoop(xChannel_,x_loop_);
	if(x_loop_){
		hub->GetCommandValue("rk",xChannel_,x);
		xpos_=x;
		xvoltage_=(max_V_ - min_V_)*(xpos_ - x_min_um_)/(x_max_um_-x_min_um_)+ min_V_;
	}else{
		hub->GetCommandValue("rk",xChannel_,xvoltage_);
		xpos_=(x_max_um_-x_min_um_)*(xvoltage_- min_V_)/(max_V_ - min_V_)+x_min_um_;
		x=xpos_;
	}
	hub->GetLoop(yChannel_,y_loop_);
	if(y_loop_){
		hub->GetCommandValue("rk",yChannel_,y);
		ypos_=y;
		yvoltage_=(max_V_ - min_V_)*(ypos_ - y_min_um_)/(y_max_um_-y_min_um_)+ min_V_;
	}else{
		hub->GetCommandValue("rk",yChannel_,yvoltage_);
		ypos_=(y_max_um_-y_min_um_)*(yvoltage_ - min_V_)/(max_V_ - min_V_)+y_min_um_;
		y=ypos_;
	}

	return DEVICE_OK;
}	

int XYStage::SetRelativePositionUm(double x, double y){
	LogMessage ("SetRelativePositionUm",true);
	double oldx,oldy;	
	GetPositionUm(oldx,oldy);	
	SetPositionUm(oldx+x,oldy+y);
	return DEVICE_OK;
}
int XYStage::SetPositionSteps(long x, long y)
{	
	LogMessage ("SetPositionSteps",true);
	xStep_ = x;
	yStep_ = y;
	double xum=xStep_* stepSizeUm_;		
	double yum=yStep_* stepSizeUm_;	
	SetPositionUm(xum,yum);
	return DEVICE_OK;
}
int XYStage::GetPositionSteps(long& x, long& y)
{	
	LogMessage ("GetPositionSteps",true);
	double xum;
	double yum;	
	GetPositionUm(xum,yum);	
	xStep_=(long)floor(xum/stepSizeUm_);		
	yStep_=(long)floor(yum/stepSizeUm_);
	x=xStep_;
	y=yStep_;
	return DEVICE_OK;
}
/**
 * Sets relative position in steps.
 */
int XYStage::SetRelativePositionSteps(long /*x*/, long /*y*/)
{
 return DEVICE_UNSUPPORTED_COMMAND;
}
int XYStage::SetOrigin()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}
int XYStage::Home()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}
int XYStage::Stop()
{	
	return DEVICE_UNSUPPORTED_COMMAND;
}
int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax){	
	xMin=x_min_um_;
	xMax=x_max_um_;
	yMin=y_min_um_;
	yMax=y_max_um_;
	return DEVICE_OK;
}
int XYStage::GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

// User API
int XYStage::GetLimitValues()
{
	int ret;	  			
	//min_um_ 
	ret = hub->GetCommandValue("dspclmin",xChannel_,x_min_um_);
	if (ret != DEVICE_OK) 
      return ret;				
	//max_um_
	ret = hub->GetCommandValue("dspclmax",xChannel_,x_max_um_);
	if (ret != DEVICE_OK) 
      return ret;
	//min_um_ 
	ret = hub->GetCommandValue("dspclmin",yChannel_,y_min_um_);
	if (ret != DEVICE_OK) 
      return ret;				
	//max_um_
	ret = hub->GetCommandValue("dspclmax",yChannel_,y_max_um_);
	if (ret != DEVICE_OK) 
      return ret;

	ret = hub->GetLimits(min_V_,max_V_);
	if (ret != DEVICE_OK) 
      return ret;

	return DEVICE_OK;
}
int XYStage::OnChannelX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	long c;
	if (eAct == MM::BeforeGet)
    {			
		c=xChannel_+1;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(c);
		xChannel_=(int)c-1; 		
	}
    return DEVICE_OK;
}
int XYStage::OnChannelY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet)
    {			
		c=yChannel_+1;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(c);
		yChannel_=(int)c-1;
		
	}
    return DEVICE_OK;
}
int XYStage::OnStatX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnStat");
	if (eAct == MM::BeforeGet){
		int stat;		
		int ret=hub->GetStatus(xChannel_,stat);			
		xstat_=stat;
		x_remote_=((stat&STATUS_REMOTE)==STATUS_REMOTE)?true:false;
		remoteCh_[xChannel_]=x_remote_;
		x_loop_= ((stat&STATUS_LOOP)==STATUS_LOOP)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)xstat_);
	}
	//CDeviceUtils::SleepMs(500);
	return DEVICE_OK;
}
int XYStage::OnStatY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage("OnStat");
	if (eAct == MM::BeforeGet){
		int stat;		
		int ret=hub->GetStatus(yChannel_,stat);			
		ystat_=stat;
		y_remote_=((stat&STATUS_REMOTE)==STATUS_REMOTE)?true:false;
		remoteCh_[yChannel_]=y_remote_;
		y_loop_= ((stat&STATUS_LOOP)==STATUS_LOOP)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)ystat_);
	}
	//CDeviceUtils::SleepMs(500);
	return DEVICE_OK;
}
int XYStage::OnLoopX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {	
		hub->GetLoop(xChannel_,x_loop_);
		if (x_loop_){
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
		char cmd[20];
      pProp->Get(loop);
		if (loop == g_Loop_close){
         x_loop_ = true;
			sprintf(cmd,"cloop,%d,1",xChannel_); 			
			l=1; 
		}else{
         x_loop_ = false;
			sprintf(cmd,"cloop,%d,0",xChannel_);			
			l=0;
		}
		int ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
		if (ret!=DEVICE_OK)
				return ret;	 		
		CDeviceUtils::SleepMs(300);
		clearPort(*this, *GetCoreCallback(), port_.c_str());
		sprintf(cmd,"cloop,%d",xChannel_);
		ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
		ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
		if (ret != DEVICE_OK) 
			return ret;
		char* dest[20];
		splitString((char*)result.c_str(),",",dest);
		int r=atoi(dest[2]);
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
int XYStage::OnLoopY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {	
		hub->GetLoop(yChannel_,y_loop_);
		if (y_loop_){
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
		char cmd[20];
      pProp->Get(loop);
		if (loop == g_Loop_close){
         y_loop_ = true;
			sprintf(cmd,"cloop,%d,1",yChannel_); 			
			l=1; 
		}else{
         y_loop_ = false;
			sprintf(cmd,"cloop,%d,0",yChannel_);			
			l=0;
		}
		int ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
		if (ret!=DEVICE_OK)
				return ret;	 		
		CDeviceUtils::SleepMs(300);
		clearPort(*this, *GetCoreCallback(), port_.c_str());
		sprintf(cmd,"cloop,%d",yChannel_);
		ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
		ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
		if (ret != DEVICE_OK) 
			return ret;
		char* dest[20];
		splitString((char*)result.c_str(),",",dest);
		int r=atoi(dest[2]);
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
int XYStage::OnRemoteX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {	
		int stat=0;
		int ret=hub->GetStatus(xChannel_,stat);
		if (ret!=DEVICE_OK)
			return ret;
		x_remote_=((stat&STATUS_REMOTE)==STATUS_REMOTE)?true:false;
		if (x_remote_){
			pProp->Set(g_Remote_on);
			l=1;
		}
		else{
			pProp->Set(g_Remote_off);
			l=0;
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string remote;
	  int i;
	  //char cmd[20];
      pProp->Get(remote);
		if (remote == g_Remote_on){
         x_remote_ = true;
			//sprintf(cmd,"setk,%d,1",nr_);
			i=1;
		}else{
         x_remote_ = false;
			//sprintf(cmd,"setk,%d,0",nr_);
			i=0;
		}
		remoteCh_[xChannel_]=x_remote_;
		//int ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
		int ret = hub->SetCommandValue("setk",xChannel_,i);
		if (ret!=DEVICE_OK)
			return ret;		 	  
	}
	//CDeviceUtils::SleepMs(300);
   return DEVICE_OK;
}
int XYStage::OnRemoteY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {	
		int stat=0;
		int ret=hub->GetStatus(yChannel_,stat);
		if (ret!=DEVICE_OK)
			return ret;		
		y_remote_=((stat&STATUS_REMOTE)==STATUS_REMOTE)?true:false;
		if (y_remote_){
			pProp->Set(g_Remote_on);
			l=1;
		}
		else{
			pProp->Set(g_Remote_off);
			l=0;
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string remote;
	  int i;
	  //char cmd[20];
      pProp->Get(remote);
		if (remote == g_Remote_on){
         y_remote_ = true;
			//sprintf(cmd,"setk,%d,1",nr_);
			i=1;
		}else{
         y_remote_ = false;
			//sprintf(cmd,"setk,%d,0",nr_);
			i=0;
		}
		remoteCh_[yChannel_]=y_remote_;
		//int ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
		int ret = hub->SetCommandValue("setk",yChannel_,i);
		if (ret!=DEVICE_OK)
			return ret;		 	  
	}
	//CDeviceUtils::SleepMs(300);
   return DEVICE_OK;
}
int XYStage::OnMonX(MM::PropertyBase* pProp, MM::ActionType eAct){			
	LogMessage("OnMon");
	
	if (eAct == MM::BeforeGet)
    {
		 //int stat= 0;
		 int ret = 0;
		 
		//Sometimes no answer with remote on,than put on/off remote
		////Put remote off
		//for(int i=0;i<3;i++){
		//	if(remoteCh_[i])
		//		ret = hub->SetCommandValue("setk",i,0);
		//}		
		ret = hub->GetCommandValue("monwpa",xChannel_,x_monwpa_);	   	
		////Put remote on again
		//for(int i=0;i<3;i++){
		//	if(remoteCh_[i])
		//		ret = hub->SetCommandValue("setk",i,1);
		//}
		if (ret!=DEVICE_OK)
				return ret;

		switch (x_monwpa_){
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
			x_monwpa_=0;
		}else if(mon == g_Monitor1){         
			x_monwpa_=1;
		}else if(mon == g_Monitor2){         
			x_monwpa_=2;
		}
		//int ret = SendSerialCommand(port_.c_str(), "monwpa,0", g_Mesg_Send_term);
		int ret = hub->SetCommandValue("monwpa",xChannel_,x_monwpa_);
			if (ret!=DEVICE_OK)
				return ret;	
		
	}
	//CDeviceUtils::SleepMs(300);
   return DEVICE_OK;
}
int XYStage::OnMonY(MM::PropertyBase* pProp, MM::ActionType eAct){			
	LogMessage("OnMon");
	
	if (eAct == MM::BeforeGet)
    {
		 //int stat= 0;
		 int ret = 0;
		 
		//Sometimes no answer with remote on,than put on/off remote
		////Put remote off
		//for(int i=0;i<3;i++){
		//	if(remoteCh_[i])
		//		ret = hub->SetCommandValue("setk",i,0);
		//}		
		ret = hub->GetCommandValue("monwpa",yChannel_,y_monwpa_);	   	
		////Put remote on again
		//for(int i=0;i<3;i++){
		//	if(remoteCh_[i])
		//		ret = hub->SetCommandValue("setk",i,1);
		//}
		if (ret!=DEVICE_OK)
				return ret;

		switch (y_monwpa_){
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
			y_monwpa_=0;
		}else if(mon == g_Monitor1){         
			y_monwpa_=1;
		}else if(mon == g_Monitor2){         
			y_monwpa_=2;
		}
		//int ret = SendSerialCommand(port_.c_str(), "monwpa,0", g_Mesg_Send_term);
		int ret = hub->SetCommandValue("monwpa",yChannel_,y_monwpa_);
			if (ret!=DEVICE_OK)
				return ret;	
		
	}
	//CDeviceUtils::SleepMs(300);
   return DEVICE_OK;
}
int XYStage::OnPositionX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){	
		int ret = GetPositionUm(xpos_,ypos_);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(xpos_);			
	}
    else if (eAct == MM::AfterSet){
		int ret=0;
		pProp->Get(xpos_);		
		ret = SetPositionUm(xpos_,ypos_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnPositionY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){	
		int ret = GetPositionUm(xpos_,ypos_);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(ypos_);			
	}
    else if (eAct == MM::AfterSet){
		int ret=0;
		pProp->Get(ypos_);		
		ret = SetPositionUm(xpos_,ypos_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnSoftstartX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){		
		int i=0;
		int ret = hub->GetCommandValue("fenable",xChannel_,i);
		if (ret != DEVICE_OK)  
			return ret;	
		x_fenable_=(i==1)?true:false;		
		if (ret!=DEVICE_OK)
			return ret;
		if (x_fenable_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		int i;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			x_fenable_ = true;
			i=1;
		}else{
			x_fenable_ = false;
			i=0;			
		}
		int ret = hub->SetCommandValue("fenable",xChannel_,i);
		if (ret!=DEVICE_OK)
				return ret;
	}
	//CDeviceUtils::SleepMs(300);
   return DEVICE_OK;
}
int XYStage::OnSoftstartY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){		
		int i=0;
		int ret = hub->GetCommandValue("fenable",yChannel_,i);
		if (ret != DEVICE_OK)  
			return ret;	
		y_fenable_=(i==1)?true:false;		
		if (ret!=DEVICE_OK)
			return ret;
		if (y_fenable_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		int i;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			y_fenable_ = true;
			i=1;
		}else{
			y_fenable_ = false;
			i=0;			
		}
		int ret = hub->SetCommandValue("fenable",yChannel_,i);
		if (ret!=DEVICE_OK)
				return ret;
	}
	//CDeviceUtils::SleepMs(300);
   return DEVICE_OK;
}
