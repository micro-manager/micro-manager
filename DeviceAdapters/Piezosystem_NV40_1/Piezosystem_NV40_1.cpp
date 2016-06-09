///////////////////////////////////////////////////////////////////////////////
// FILE:          Piezosystem_NV40_1.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Piezosystem Jena device adapter.
//					NV40/1CL is a 1 channel device which can control diffrent actuators.
//					The actuator has a small memory with the values for the amplifier.
//					There are two version of controller NV40/3 and NV40/1 .
//					The controller has USB(VCP) and RS232-interface.
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

#include "Piezosystem_NV40_1.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
#include <math.h>

//Port-Control
const char* g_Mesg_Send_term ="\r";  //CR 
const char* g_Mesg_Receive_term ="\r\n";  //CR LF
//Controller
const char* g_NV40_1 = "PSJ_NV40/1CL";
// single axis stage
const char* g_StageDeviceName = "PSJ_Stage";

const char* g_Loop				= "Loop";
const char* g_Loop_open			= "open loop";
const char* g_Loop_close		= "close loop";

const char* g_Remote				= "Remote control";
const char* g_Remote_on			= "on";
const char* g_Remote_off		= "off";

const char* g_Limit_V_Min		= "Limit Voltage min [V]";
const char* g_Limit_V_Max		= "Limit Voltage max [V]";
const char* g_Limit_Um_Min		= "Limit um min [microns]";
const char* g_Limit_Um_Max		= "Limit um max [microns]";
const char* g_Voltage			= "Voltage [V]";
const char* g_Position			= "Position [microns]";

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
	//RegisterDevice(g_NV40_1, MM::HubDevice, "Piezosystem Jena NV40 Single");
	RegisterDevice(g_StageDeviceName, MM::StageDevice, "Single Axis Stage Ch1");
	//RegisterDevice(g_Shutter, MM::ShutterDevice, "PSJ Shutter");
}             

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{	
	if (deviceName == 0)      return 0;		
	//if (strcmp(deviceName, g_dDrive) == 0){ 		
	//	return new Hub(g_NV40_1);}	
	//else 
	if (strcmp(deviceName, g_StageDeviceName) == 0){		
		return new Stage(0);	}	
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

/**
 * Single axis stage.
 */
Stage::Stage(int /* nr */):
   initialized_(false),
	loop_(false),
	voltage_(0),
	min_V_(0.0),
	max_V_(100.0),
	min_um_(0.0),
	max_um_(100.0),
	pos_(0.0)
{
	InitializeDefaultErrorMessages();
	 // Name
   CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Piezosystem stage driver adapter", MM::String, true);
	
	CPropertyAction *pAct = new CPropertyAction (this, &Stage::OnMinV);		
	CreateProperty(g_Limit_V_Min, CDeviceUtils::ConvertToString(min_V_), MM::Float, false, pAct, true);
	pAct = new CPropertyAction (this, &Stage::OnMaxV);	;	
	CreateProperty(g_Limit_V_Max, CDeviceUtils::ConvertToString(max_V_), MM::Float, false, pAct, true);
	pAct = new CPropertyAction (this, &Stage::OnMinUm);		
	CreateProperty(g_Limit_Um_Min,CDeviceUtils::ConvertToString(min_um_), MM::Float, false, pAct, true);
	pAct = new CPropertyAction (this, &Stage::OnMaxUm);	
	CreateProperty(g_Limit_Um_Max, CDeviceUtils::ConvertToString(max_um_), MM::Float, false, pAct, true);
	
	// Port:
	pAct = new CPropertyAction(this, &Stage::OnPort);
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
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "9600" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_DataBits, "8");
			GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
			GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Parity, "None");  
			GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Off" );  
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_AnswerTimeout, "500.0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();
		   std::string v;
         int qvStatus = this->GetVersion(v);
         //LogMessage(std::string("version : ")+v, true);
			ver_=v;
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
	CPropertyAction* pAct = new CPropertyAction (this, &Stage::OnLoop);
   CreateProperty(g_Loop, g_Loop_open, MM::String, false, pAct,false);
   AddAllowedValue(g_Loop, g_Loop_open);
   AddAllowedValue(g_Loop, g_Loop_close);
	SetProperty(g_Loop, g_Loop_open);


	pAct = new CPropertyAction (this, &Stage::OnRemote);
   CreateProperty(g_Remote, g_Remote_on, MM::String, false, pAct,false);
   AddAllowedValue(g_Remote, g_Remote_on);
   AddAllowedValue(g_Remote, g_Remote_off);
	SetProperty(g_Remote, g_Remote_on);	


	pAct = new CPropertyAction (this, &Stage::OnPosition);
	CreateProperty(g_Position, "0", MM::Float, false, pAct,false);

		
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
      return false;
}
// Stage API
int Stage::SetPositionUm(double pos){
	LogMessage ("SetPositionUm");
	int ret;	
	if(loop_){ //Close loop
		pos_=pos;
		voltage_=(max_V_-min_V_)*(pos-min_um_)/(max_um_-min_um_)+min_V_; //Translate Pos->Voltage
		ret=SetCommandValue("wr",pos_);
	}else{  //open loop
		pos_=pos;
		voltage_=(max_V_-min_V_)*(pos-min_um_)/(max_um_-min_um_)+min_V_; //Translate Pos->Voltage		
		ret=SetCommandValue("wr",voltage_);
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
	ret = GetCommandValue("rd",d);
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
   returnStatus = GetSerialAnswer(port_.c_str(), ">", version);  
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
int Stage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){	
		int ret = GetPositionUm(pos_);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(pos_);
		//SetProperty(g_Voltage,CDeviceUtils::ConvertToString(chx_.voltage_));
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
      pProp->Get(loop);
		if (loop == g_Loop_close){
         loop_ = true;
			int ret = SendSerialCommand(port_.c_str(), "cl", g_Mesg_Send_term);
			if (ret!=DEVICE_OK)
				return ret;

		}else{
         loop_ = false;
			int ret = SendSerialCommand(port_.c_str(), "ol", g_Mesg_Send_term);
			if (ret!=DEVICE_OK)
				return ret;
		}
		CDeviceUtils::SleepMs(500);
	}
    return DEVICE_OK;
}
int Stage::OnRemote(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {			
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
      pProp->Get(remote);
		if (remote == g_Remote_on){
         remote_ = true;
			int ret = SendSerialCommand(port_.c_str(), "i1", g_Mesg_Send_term);
			if (ret!=DEVICE_OK)
				return ret;

		}else{
         remote_ = false;
			int ret = SendSerialCommand(port_.c_str(), "i0", g_Mesg_Send_term);
			if (ret!=DEVICE_OK)
				return ret;
		}
	  
	}
    return DEVICE_OK;
}
