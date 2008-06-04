///////////////////////////////////////////////////////////////////////////////
// FILE:          Pecon.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Pecon Incubation System Remote Control
//
// AUTHOR:        Peter Topor, topor@ilc.sk, 05/17/2008
// LICENSE:       This file is distributed under the BSD license.

#ifdef WIN32
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "Pecon.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

const char* g_TempControl = "TempControl";
const char* g_TempChannel1 = "1";
const char* g_TempChannel2 = "2";

//device properties
const char* g_Heating1 = "Channel1_Heating";
const char* g_Heating2 = "Channel2_Heating";
const char* g_NominalTemp1 = "Channel1_NominalTemperature";
const char* g_NominalTemp2 = "Channel2_NominalTemperature";
const char* g_RealTemp1 = "Channel1_RealTemperature";
const char* g_RealTemp2 = "Channel2_RealTemperature";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	AddAvailableDeviceName(g_TempControl, "Temperature controller 37-2 digital");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_TempControl) == 0)
	{
		TempControl* s = new TempControl();
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
// TempControl

TempControl::TempControl() :
port_("Undefined"),
state_(0),
initialized_(false),
activeChannel_(g_TempChannel1),
version_("Undefined")
{
	InitializeDefaultErrorMessages();

	CreateProperty(MM::g_Keyword_Name, g_TempControl, MM::String, true); 

	CreateProperty(MM::g_Keyword_Description, "Pecon Incubation Tempcontrol 37-2 adapter", MM::String, true);

	CPropertyAction* pAct = new CPropertyAction (this, &TempControl::OnPort);      
	CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);         
}                                                                            

TempControl::~TempControl()                                                            
{                                                                            
	Shutdown();                                                               
} 

void TempControl::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_TempControl);
}  


int TempControl::GetSerialAnswer (const char* portName, std::string& ans, unsigned int count)
{
	const unsigned long MAX_BUFLEN = 2000; 
	char buf[MAX_BUFLEN];
	unsigned char* cc = new unsigned char[MAX_BUFLEN];
	int ret = 0;
    unsigned long read = 0;
    MM::MMTime startTime = GetCurrentMMTime();
    // Time out of 5 sec.  Make this a property?
    MM::MMTime timeOut(5000000);
	unsigned long offset = 0;
	while((offset < count) && ( (GetCurrentMMTime() - startTime) < timeOut))
	{
		ret = ReadFromComPort(portName, cc, MAX_BUFLEN,read);
		for(unsigned int i=0; i<read; i++)      
		{
			buf[offset+i] = cc[i];
		}
		offset += read;
	}
	// get port response to buf

	if (ret != DEVICE_OK)
		return ret;
	ans = buf;

	return DEVICE_OK;
}

int TempControl::Initialize()
{
	if (initialized_)
		return DEVICE_OK;


	//First contact with device
	command_ = "A003"; //is device ready?
	std::string answer;
	int ret = SendSerialCommand(port_.c_str(), command_.c_str(), "");
	if (ret != DEVICE_OK)
		return ret;

	ret = GetSerialAnswer(port_.c_str(), answer,3); //awaiting 3 response characters
	if (ret != DEVICE_OK)
		return ret;


	//ret = GetSerialAnswer(port_.c_str(),"3", answer);
	bool ok = 0;
	if((answer.c_str()[0]==48) && (answer.c_str()[1]==48) && (answer.c_str()[2]==51)) ok=1; //003 means O.K.
	else 
		return DEVICE_SERIAL_INVALID_RESPONSE;

	// Heating in channels	
	// -----
	CPropertyAction* pAct = new CPropertyAction (this, &TempControl::OnHeating1C);
	ret = CreateProperty(g_Heating1, "0", MM::Integer, false, pAct);
	if (ret != DEVICE_OK)
		return ret;                                                            
	pAct = new CPropertyAction (this, &TempControl::OnHeating2C);
	ret = CreateProperty(g_Heating2, "0", MM::Integer, false, pAct);
	if (ret != DEVICE_OK)
		return ret;                                                            

	AddAllowedValue(g_Heating1, "0");                                
	AddAllowedValue(g_Heating1, "1");                                
	AddAllowedValue(g_Heating2, "0");                                
	AddAllowedValue(g_Heating2, "1");                                

	// Real temperature in channels
	// -----
	pAct = new CPropertyAction (this, &TempControl::OnGetTemp1C);
	ret = CreateProperty(g_RealTemp1, "0.0", MM::Float, true, pAct);
	if (ret != DEVICE_OK)
		return ret;                                                            

	pAct = new CPropertyAction (this, &TempControl::OnGetTemp2C);
	ret = CreateProperty(g_RealTemp2, "0.0", MM::Float, true, pAct);
	if (ret != DEVICE_OK)
		return ret;                                                            

	// Nominal temperature in channels
	pAct = new CPropertyAction (this, &TempControl::OnSetTemp1C);
	ret = CreateProperty(g_NominalTemp1, "37.0", MM::Float, false, pAct);
	if (ret != DEVICE_OK)
		return ret;                                                            

	pAct = new CPropertyAction (this, &TempControl::OnSetTemp2C);
	ret = CreateProperty(g_NominalTemp2, "37.0", MM::Float, false, pAct);
	if (ret != DEVICE_OK)
		return ret;                                                            

	initialized_ = true;                                                      
	return DEVICE_OK;                                                         
}

int TempControl::SetTemp(int channel, double temp)
{
	if(!WakeUp()) return DEVICE_SERIAL_INVALID_RESPONSE;
	//double to string conversion
	std::string command_ = "N000";
	if(channel==2) command_[0] = 'O';
	char s[10];
	sprintf(s,"%f",temp);

	int pos =0;
	int count = 0;
	while(count<3)
	{
		if((s[pos]!='.')&&(s[pos]!=','))
		{
			command_[count+1] = s[pos];
			count++;
		}
		pos++;
	}

	std::string answer;
	int ret = TempControl::SendSerialCommand(port_.c_str(), command_.c_str(), "");
	if (ret != DEVICE_OK)
		return ret;
	ret = TempControl::GetSerialAnswer(port_.c_str(), answer,3); //awaiting 3 response characters
	if (ret != DEVICE_OK)
		return ret;
	bool ok = 0;
	if((s[0]==answer.c_str()[0])&&(s[1]==answer.c_str()[1])&&(s[3]==answer.c_str()[2])) ok = 1;

	if(ok)
		return DEVICE_OK;                                                         
	else
		return DEVICE_SERIAL_INVALID_RESPONSE;
}


int TempControl::GetTemp(int channel, double& temp)
{   
	if(!WakeUp()) return DEVICE_SERIAL_INVALID_RESPONSE;
	if(channel==1)
	{
		command_ = "R100"; //what is the current temp in Channel1?
		std::string answer;
		int ret = SendSerialCommand(port_.c_str(), command_.c_str(), "");
		if (ret != DEVICE_OK)
			return ret;
		ret = GetSerialAnswer(port_.c_str(), answer,3); //awaiting 3 response characters
		if (ret != DEVICE_OK)
			return ret;

		if(answer.c_str()[0]==45)
		{
			temp = (-1)*((answer.c_str()[1]-48)+0.1*(answer.c_str()[2]-48));
		}
		else
		{
			temp = (answer.c_str()[0]-48)*10 + (answer.c_str()[1]-48) + 0.1*(answer.c_str()[2]-48);
		}

		return DEVICE_OK;                                                         
	}
	else
	{
		command_ = "R200"; //what is the current temp in Channel1?
		std::string answer;
		int ret = SendSerialCommand(port_.c_str(), command_.c_str(), "");
		if (ret != DEVICE_OK)
			return ret;
		ret = GetSerialAnswer(port_.c_str(), answer,3); //awaiting 3 response characters
		if (ret != DEVICE_OK)
			return ret;

		if(answer.c_str()[0]==45)
		{
			temp = (-1)*((answer.c_str()[1]-48)+0.1*(answer.c_str()[2]-48));
		}
		else
		{
			temp = (answer.c_str()[0]-48)*10 + (answer.c_str()[1]-48) + 0.1*(answer.c_str()[2]-48);
		}
		return DEVICE_OK;                                                         
	}
} 



int TempControl::GetHeating(int channel, int& status)
{     
	if(!WakeUp()) return DEVICE_SERIAL_INVALID_RESPONSE;
	char * command_ = "H000"; //what is the current heating status?
	std::string answer;
	int ret = SendSerialCommand(port_.c_str(), command_, "");
	if (ret != DEVICE_OK)
		return ret;
	ret = GetSerialAnswer(port_.c_str(), answer,3); //awaiting 3 response characters
	if (ret != DEVICE_OK)
		return ret;

	if(channel==1)
	{
		status = answer.c_str()[2];
	}
	else
	{
		status = answer.c_str()[1];
	}
	return DEVICE_OK;                                                         
} 



int TempControl::SetHeating(int channel, int status)
{  
	if(!WakeUp()) return DEVICE_SERIAL_INVALID_RESPONSE;
	if(channel == 1)
	{
		if(status == 1)
		{
			command_ = "H101";
		}
		else
		{
			command_ = "H100";
		}
	}
	else
	{
		if(status == 1)
		{
			command_ = "H201";
		}
		else
		{
			command_ = "H200";
		}
	}
	std::string answer;
	int ret = SendSerialCommand(port_.c_str(), command_.c_str(), "");
	if (ret != DEVICE_OK)
		return ret;
	ret = GetSerialAnswer(port_.c_str(), answer,3); //awaiting 3 response characters
	if (ret != DEVICE_OK)
		return ret;

	//TODO: check return value;
	return DEVICE_OK;                                                         
} 


int TempControl::GetVersion()
{
	std::string command = "S000"; //should return: 0xy -> where x=1 <=> Tempcontrol37-2; y = version
	int ret = SendSerialCommand(port_.c_str(), command.c_str(), "");
	if (ret != DEVICE_OK)
		return ret;

	// block/wait for acknowledge                     
	std::string answer;
	ret = GetSerialAnswer(port_.c_str(), answer,3);
	if (ret != DEVICE_OK)
		return ret;

	if (answer[0]== '0') 
	{
		version_ = answer[1]+answer[2];
		return DEVICE_OK;
	}
	else 
	{
		//TODO : device specific error
		//return ERR_TIRFSHUTTER_OFFSET + errNo;
	}
	return DEVICE_SERIAL_INVALID_RESPONSE;
}





int TempControl::Shutdown()                                                
{                                                                            
	if (initialized_)                                                         
	{                                                                         
		initialized_ = false;                                                  
	}                                                                         
	return DEVICE_OK;                                                         
}                                                                            

bool TempControl::WakeUp()
{
	command_ = "A003"; //is device ready?
	std::string answer;
	int ret = SendSerialCommand(port_.c_str(), command_.c_str(), "");
	if (ret != DEVICE_OK)
		return false;
	ret = GetSerialAnswer(port_.c_str(), answer,3); //awaiting 3 response characters
	if (ret != DEVICE_OK)
		return false;

	if((answer.c_str()[0]=='0')&&(answer.c_str()[1]=='0')&&(answer.c_str()[2]=='3'))
		return true; //true = ready
	else 
		return false; //false = cannot communicate with device
}

bool TempControl::Busy()
{
	return false;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
int TempControl::OnHeating1C(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = 0;
	if (eAct == MM::BeforeGet)
	{
		int retvalue;
		ret = GetHeating(1,retvalue);
		pProp->Set((long)retvalue-48); //Oyx, x=1 channel, y=2 channel
		return ret;
	}
	else if (eAct == MM::AfterSet)
	{
		std::string val;
		pProp->Get(val);
		if(val=="0")
			ret = SetHeating(1,0);
		else
			ret = SetHeating(1,1);
		return ret;		
	}
	return DEVICE_OK;
}


int TempControl::OnHeating2C(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	int ret = 0;
	if (eAct == MM::BeforeGet)
	{
		int retvalue;
		ret = GetHeating(2,retvalue);
		pProp->Set((long)retvalue-48); //Oyx, x=1 channel, y=2 channel
		return ret;
	}
	else if (eAct == MM::AfterSet)
	{
		std::string val;
		pProp->Get(val);
		if(val=="0")
			ret = SetHeating(2,0);
		else
			ret = SetHeating(2,1);
		return ret;
	}
	return DEVICE_OK;
}

/*
* Sets the Serial Port to be used.
* Should be called before initialization
*/
int TempControl::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
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


int TempControl::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
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



int TempControl::OnGetTemp1C(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		double temperature = 0;
		GetTemp(1,temperature);
		pProp->Set(temperature);
	}
	return DEVICE_OK;     
}
int TempControl::OnGetTemp2C(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		double temperature = 0;
		GetTemp(2,temperature);
		pProp->Set(temperature);
	}
	return DEVICE_OK;     
}
//---
int TempControl::OnSetTemp1C(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		//	pProp->Set(37.0);
	}
	else
		if (eAct == MM::AfterSet)
		{
			double temp;
			pProp->Get(temp);		
			SetTemp(1,temp);

		}
		return DEVICE_OK;     
}
int TempControl::OnSetTemp2C(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::BeforeGet)
	{
		//	pProp->Set(37.0);
	}
	else
		if (eAct == MM::AfterSet)
		{
			double temp;
			pProp->Get(temp);
			SetTemp(2,temp);
		}
		return DEVICE_OK;     
}

