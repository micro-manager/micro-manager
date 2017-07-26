///////////////////////////////////////////////////////////////////////////////
// FILE:          PIGCSControllerDLL.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PI GCS DLL Controller Driver
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/28/2006
//                Steffen Rau, s.rau@pi.ws, 10/03/2008
// COPYRIGHT:     University of California, San Francisco, 2006
//                Physik Instrumente (PI) GmbH & Co. KG, 2008
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
// CVS:           $Id: PIGCSControllerDLL.cpp,v 1.17, 2014-03-31 12:51:24Z, Steffen Rau$
//

#ifndef __APPLE__

#ifndef WIN32
    #include <dlfcn.h>
#endif

#include "Controller.h"
#include "PI_GCS_2.h"
#include "PIGCSControllerDLL.h"

#include <locale>
#include <algorithm>

const char* PIGCSControllerDLLDevice::DeviceName_ = "PI_GCSController_DLL";
const char* PIGCSControllerDLLDevice::PropName_ = "DLL Name";
const char* PIGCSControllerDLLDevice::PropInterfaceType_ = "Interface Type";
const char* PIGCSControllerDLLDevice::PropInterfaceParameter_ = "Interface Parameter";

struct ToUpper
{
	ToUpper (std::locale const& l) : loc(l) {;}
	char operator() (char c) const { return std::toupper(c,loc); }
private:
	std::locale const loc;
    ToUpper& operator=(const ToUpper&);
    ToUpper ();
};


PIGCSControllerDLLDevice::PIGCSControllerDLLDevice()
    : ctrl_(NULL)
    , dllName_("GCS_DLL.dll")
    , interfaceType_("")
    , interfaceParameter_("")
    , initialized_(false)
    , bShowInterfaceProperties_(true)
{
   InitializeDefaultErrorMessages();
   
   SetErrorText(ERR_DLL_PI_DLL_NOT_FOUND, g_msg_DLL_NOT_FOUND);
   SetErrorText(ERR_DLL_PI_INVALID_INTERFACE_NAME, g_msg_INVALID_INTERFACE_NAME);
   SetErrorText(ERR_DLL_PI_INVALID_INTERFACE_PARAMETER, g_msg_INVALID_INTERFACE_PARAMETER);
}


PIGCSControllerDLLDevice::~PIGCSControllerDLLDevice()
{
	Shutdown();
    ctrl_ = NULL;
}

void PIGCSControllerDLLDevice::SetDLL(std::string dll_name)
{
   dllName_ = dll_name;
}

void PIGCSControllerDLLDevice::SetInterface(std::string type, std::string parameter)
{
   interfaceType_ = type;
   interfaceParameter_ = parameter;
}

void PIGCSControllerDLLDevice::ShowInterfaceProperties(bool bShow)
{
   bShowInterfaceProperties_ = bShow;
}

void PIGCSControllerDLLDevice::CreateProperties()
{
   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, DeviceName_, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "Physik Instrumente (PI) GCS DLL Adapter", MM::String, true);

   CPropertyAction* pAct;

   // DLL name
   pAct = new CPropertyAction (this, &PIGCSControllerDLLDevice::OnDLLName);
   CreateProperty(PIGCSControllerDLLDevice::PropName_, dllName_.c_str(), MM::String, false, pAct, true);

   CreateInterfaceProperties();

}

void PIGCSControllerDLLDevice::CreateInterfaceProperties(void)
{
   CPropertyAction* pAct;
   std::string interfaceParameterLabel = "";

   // Interface type
   if (bShowInterfaceProperties_)
   {
      pAct = new CPropertyAction (this, &PIGCSControllerDLLDevice::OnInterfaceType);
      CreateProperty(PIGCSControllerDLLDevice::PropInterfaceType_, interfaceType_.c_str(), MM::String, false, pAct, true);

      interfaceParameterLabel = PIGCSControllerDLLDevice::PropInterfaceParameter_;
   }
   else
   {
      if (strcmp(interfaceType_.c_str(), "PCI") == 0)
      {
         interfaceParameterLabel = "PCI Board";
      }
      else if (strcmp(interfaceType_.c_str(), "RS-232") == 0)
      {
         interfaceParameterLabel = "ComPort ; Baudrate";
      }
   }
   
   // Interface parameter
   if (interfaceParameterLabel.empty()) return;

   pAct = new CPropertyAction (this, &PIGCSControllerDLLDevice::OnInterfaceParameter);
   CreateProperty(interfaceParameterLabel.c_str(), interfaceParameter_.c_str(), MM::String, false, pAct, true);
}

int PIGCSControllerDLLDevice::Initialize()
{
	if (initialized_)
		return DEVICE_OK;

	char szLabel[MM::MaxStrLength];
	GetLabel(szLabel);
	ctrl_ = new PIGCSControllerDLL(szLabel, this, GetCoreCallback()); 

   int ret = ctrl_->LoadDLL(dllName_);
   if (ret != DEVICE_OK)
   {
      LogMessage(std::string("Cannot load dll ") + dllName_);
      Shutdown();
	  return ret;
   }

   ret = ctrl_->ConnectInterface(interfaceType_, interfaceParameter_);
   if (ret != DEVICE_OK)
   {
	   LogMessage("Cannot connect");
	   Shutdown();
	   return ret;
   }
   initialized_ = true;

   int nrJoysticks = ctrl_->FindNrJoysticks();
	if (nrJoysticks > 0)
	{
		CPropertyAction* pAct = new CPropertyAction (this, &PIGCSControllerDLLDevice::OnJoystick1);
		CreateProperty("Joystick 1", "0" , MM::Integer, false, pAct);
	}
	if (nrJoysticks > 1)
	{
		CPropertyAction* pAct = new CPropertyAction (this, &PIGCSControllerDLLDevice::OnJoystick2);
		CreateProperty("Joystick 2", "0" , MM::Integer, false, pAct);
	}
	if (nrJoysticks > 2)
	{
		CPropertyAction* pAct = new CPropertyAction (this, &PIGCSControllerDLLDevice::OnJoystick3);
		CreateProperty("Joystick 3", "0" , MM::Integer, false, pAct);
	}
	if (nrJoysticks > 3)
	{
		CPropertyAction* pAct = new CPropertyAction (this, &PIGCSControllerDLLDevice::OnJoystick4);
		CreateProperty("Joystick 4", "0" , MM::Integer, false, pAct);
	}


   return ret;
}

int PIGCSControllerDLLDevice::Shutdown()
{
	if (!initialized_)
		return DEVICE_OK;
	char szLabel[MM::MaxStrLength];
	GetLabel(szLabel);
	PIController::DeleteByLabel(szLabel);
	initialized_ = false;

	return DEVICE_OK;
}

bool PIGCSControllerDLLDevice::Busy()
{
   return false;
}

void PIGCSControllerDLLDevice::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, DeviceName_);
}

int PIGCSControllerDLLDevice::OnDLLName(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(dllName_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(dllName_);
   }

   return DEVICE_OK;
}

int PIGCSControllerDLLDevice::OnInterfaceType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(interfaceType_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(interfaceType_);
   }

   return DEVICE_OK;
}

int PIGCSControllerDLLDevice::OnInterfaceParameter(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(interfaceParameter_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(interfaceParameter_);
   }

   return DEVICE_OK;
}

int PIGCSControllerDLLDevice::OnJoystick1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == ctrl_)
    {
        return DEVICE_ERR;
    }
	return ctrl_->OnJoystick(pProp, eAct, 1);
}

int PIGCSControllerDLLDevice::OnJoystick2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == ctrl_)
    {
        return DEVICE_ERR;
    }
	return ctrl_->OnJoystick(pProp, eAct, 2);
}

int PIGCSControllerDLLDevice::OnJoystick3(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == ctrl_)
    {
        return DEVICE_ERR;
    }
	return ctrl_->OnJoystick(pProp, eAct, 3);
}

int PIGCSControllerDLLDevice::OnJoystick4(MM::PropertyBase* pProp, MM::ActionType eAct)
{
    if (NULL == ctrl_)
    {
        return DEVICE_ERR;
    }
	return ctrl_->OnJoystick(pProp, eAct, 4);
}

PIGCSControllerDLL::PIGCSControllerDLL(const std::string& label, PIGCSControllerDLLDevice* proxy, MM::Core* logsink)
    : PIController(label)
	, ConnectRS232_(NULL)
	, Connect_(NULL)
	, IsConnected_(NULL)
	, CloseConnection_(NULL)
	, EnumerateUSB_(NULL)
	, ConnectUSB_(NULL)
	, GetError_(NULL)
	, qIDN_(NULL)
	, qVER_(NULL)
	, INI_(NULL)
	, CST_(NULL)
	, qCST_(NULL)
	, qFRF_(NULL)
	, FRF_(NULL)
	, FPL_(NULL)
	, FNL_(NULL)
	, IsReferencing_(NULL)
	, IsControllerReady_(NULL)
	, IsMoving_(NULL)
	, REF_(NULL)
	, MPL_(NULL)
	, MNL_(NULL)
	, qPOS_(NULL)
	, MOV_(NULL)
	, STP_(NULL)
	, SVO_(NULL)
	, qSVO_(NULL)
	, JON_(NULL)
	, qJON_(NULL)
	, VEL_(NULL)
	, qVEL_(NULL)
    , qTPC_(NULL)
    , ID_(-1)
    , needResetStages_(false)
    , module_(NULL)
{
	PIController::logsink_ = logsink;
	PIController::logdevice_= proxy;
}

PIGCSControllerDLL::~PIGCSControllerDLL()
{
	CloseAndUnload();
}

   

int PIGCSControllerDLL::LoadDLL(const std::string& dllName)
{
    if (ci_find(dllName, "C843_GCS_DLL") != std::string::npos)
    {
        dllPrefix_ = "C843_";
        gcs2_ = false;
    }
    else if (ci_find(dllName, "PI_Mercury_GCS") != std::string::npos)
    {
        dllPrefix_ = "Mercury_";
        gcs2_ = false;
    }
    else if (ci_find(dllName, "E7XX_GCS_DLL") != std::string::npos)
    {
        dllPrefix_ = "E7XX_";
        gcs2_ = false;
        umToDefaultUnit_ = 1.0;
        needResetStages_ = true;
        onlyIDSTAGEvalid_ = true;
    }
    else if (ci_find(dllName, "E816_DLL") != std::string::npos)
    {
        dllPrefix_ = "E816_";
        umToDefaultUnit_ = 1.0;
    }
    else if (ci_find(dllName, "PI_HydraPollux_GCS2_DLL") != std::string::npos)
    {
        dllPrefix_ = "Hydra_";
    }
    else if (ci_find(dllName, "C865_GCS_DLL") != std::string::npos)
    {
        dllPrefix_ = "C865_";
        gcs2_ = false;
    }
    else if (ci_find(dllName, "C866_GCS_DLL") != std::string::npos)
    {
        dllPrefix_ = "C866_";
        gcs2_ = false;
    }
    else if (ci_find(dllName, "PI_GCS2_DLL") != std::string::npos)
    {
        dllPrefix_ = "PI_";
        gcs2_ = true;
    }

#ifdef WIN32
    module_ = LoadLibrary(dllName.c_str());
#else
    module_ = dlopen(dllName.c_str(), RTLD_LAZY);
#endif
    if (module_ == NULL)
    {
        printf("load module failed\n");
        return ERR_DLL_PI_DLL_NOT_FOUND;
    }
    ConnectRS232_ = (FP_ConnectRS232)LoadDLLFunc("ConnectRS232");
    Connect_ = (FP_Connect)LoadDLLFunc("Connect");
    IsConnected_ = (FP_IsConnected)LoadDLLFunc("IsConnected");
    CloseConnection_ = (FP_CloseConnection)LoadDLLFunc("CloseConnection");
    EnumerateUSB_ = (FP_EnumerateUSB)LoadDLLFunc("EnumerateUSB");
    ConnectUSB_ = (FP_ConnectUSB)LoadDLLFunc("ConnectUSB");
    GetError_ = (FP_GetError)LoadDLLFunc("GetError");
    qIDN_ = (FP_qIDN)LoadDLLFunc("qIDN");
    qVER_ = (FP_qVER)LoadDLLFunc("qVER");
    INI_ = (FP_INI)LoadDLLFunc("INI");
    CST_ = (FP_CST)LoadDLLFunc("CST");
    qCST_ = (FP_qCST)LoadDLLFunc("qCST");
    qFRF_ = (FP_qFRF)LoadDLLFunc("qFRF");
    FRF_ = (FP_FRF)LoadDLLFunc("FRF");
    FPL_ = (FP_FPL)LoadDLLFunc("FPL");
    FNL_ = (FP_FNL)LoadDLLFunc("FNL");
    IsReferencing_ = (FP_IsReferencing)LoadDLLFunc("IsReferencing");
    IsControllerReady_ = (FP_IsControllerReady)LoadDLLFunc("IsControllerReady");
    IsMoving_ = (FP_IsMoving)LoadDLLFunc("IsMoving");
    REF_ = (FP_REF)LoadDLLFunc("REF");
    MPL_ = (FP_MPL)LoadDLLFunc("MPL");
    MNL_ = (FP_MNL)LoadDLLFunc("MNL");
    qPOS_ = (FP_qPOS)LoadDLLFunc("qPOS");
    MOV_ = (FP_MOV)LoadDLLFunc("MOV");
    STP_ = (FP_STP)LoadDLLFunc("STP");
    SVO_ = (FP_SVO)LoadDLLFunc("SVO");
    qSVO_ = (FP_qSVO)LoadDLLFunc("qSVO");
    JON_ = (FP_JON)LoadDLLFunc("JON");
    qJON_ = (FP_qJON)LoadDLLFunc("qJON");
    qVEL_ = (FP_qVEL)LoadDLLFunc("qVEL");
    VEL_ = (FP_VEL)LoadDLLFunc("VEL");
    qTPC_ = (FP_qTPC)LoadDLLFunc("qTPC");

    return DEVICE_OK;
}

void* PIGCSControllerDLL::LoadDLLFunc( const char* funcName )
{
#ifdef WIN32
	return GetProcAddress(module_, (dllPrefix_ + funcName).c_str());
#else
        return(dlsym(module_, (dllPrefix_ + funcName).c_str()));
#endif
}

void PIGCSControllerDLL::CloseAndUnload()
{
	if (module_ == NULL)
		return;

	if (ID_>=0 && CloseConnection_ != NULL)
		CloseConnection_(ID_);

	ID_ = -1;

	ConnectRS232_ = NULL;
	Connect_ = NULL;
	IsConnected_ = NULL;
	CloseConnection_ = NULL;
	EnumerateUSB_ = NULL;
	ConnectUSB_ = NULL;
	GetError_ = NULL;
	qIDN_ = NULL;
	qVER_ = NULL;
	INI_ = NULL;
	CST_ = NULL;
	qCST_ = NULL;
	qFRF_ = NULL;
	FRF_ = NULL;
	FPL_ = NULL;
	FNL_ = NULL;
	//IsReferenceOK_ = NULL;
	IsReferencing_ = NULL;
	IsControllerReady_ = NULL;
	IsMoving_ = NULL;
	REF_ = NULL;
	MPL_ = NULL;
	MNL_ = NULL;
	qPOS_ = NULL;
	MOV_ = NULL;
	STP_ = NULL;
	SVO_ = NULL;
	qSVO_ = NULL;
	JON_ = NULL;
	qJON_ = NULL;
	VEL_ = NULL;
	qVEL_ = NULL;
   qTPC_ = NULL;

#ifdef WIN32
	FreeLibrary(module_);
	module_ = NULL;
#else
#endif
}

int PIGCSControllerDLL::ConnectInterface(const std::string &interfaceType, const std::string &interfaceParameter)
{
#ifdef WIN32
	if (module_ == NULL)
		return DEVICE_NOT_CONNECTED;
#else
#endif

	int ret = ERR_DLL_PI_INVALID_INTERFACE_NAME;
	if (interfaceType == "PCI")
		ret = ConnectPCI(interfaceParameter);
	if (interfaceType == "RS-232")
		ret = ConnectRS232(interfaceParameter);
	if (interfaceType == "USB")
		ret = ConnectUSB(interfaceParameter);

	if (ret != DEVICE_OK)
		return ret;

	if (HasqCST() && needResetStages_ && !gcs2_)
	{
		char szStages[1024];
		qCST_(ID_, "", szStages, 1023);
		std::vector<std::string> lines = tokenize(szStages);

		std::string axisNames, stagetypes;
		std::vector<std::string>::iterator line;
		for(line = lines.begin(); line != lines.end(); ++line)
		{
			stagetypes += "NOSTAGE \n";
			axisNames += (*line)[0];
		}
		CST_(ID_, axisNames.c_str(), stagetypes.c_str());
		qCST_(ID_, "", szStages, 1023);

	}

	return ret;
}

int PIGCSControllerDLL::ConnectPCI(const std::string &interfaceParameter)
{
	if (Connect_ == NULL)
		return DEVICE_NOT_SUPPORTED;

	long board;
	if (!GetValue(interfaceParameter, board))
		return ERR_DLL_PI_INVALID_INTERFACE_PARAMETER;
	ID_ = Connect_((int)board);
	if (ID_<0)
		return DEVICE_NOT_CONNECTED;
	return DEVICE_OK;
}

int PIGCSControllerDLL::ConnectRS232(const std::string &interfaceParameter)
{
	if (ConnectRS232_ == NULL)
		return DEVICE_NOT_SUPPORTED;

	size_t pos = interfaceParameter.find(';');
	if (pos == std::string::npos)
		return ERR_DLL_PI_INVALID_INTERFACE_PARAMETER;
	std::string sport = interfaceParameter.substr(0,pos);
	std::string sbaud = interfaceParameter.substr(pos+1);

	long port, baud;
	if (!GetValue(sport, port))
		return DEVICE_INVALID_PROPERTY_VALUE;
	if (!GetValue(sbaud, baud))
		return DEVICE_INVALID_PROPERTY_VALUE;

	ID_ = ConnectRS232_((int)port, (int)baud);
	if (ID_<0)
		return DEVICE_NOT_CONNECTED;

	return DEVICE_OK;
}



int PIGCSControllerDLL::ConnectUSB(const std::string&  interfaceParameter)
{
	if (ConnectUSB_ == NULL || EnumerateUSB_ == NULL)
		return DEVICE_NOT_SUPPORTED;

	char szDevices[128*80+1];
	int nrDevices = EnumerateUSB_(szDevices, 128*80, NULL);
	if (nrDevices<0)
		return TranslateError(nrDevices);
	if (nrDevices==0)
		return DEVICE_NOT_CONNECTED;

	std::string deviceName;
	if (interfaceParameter.empty())
	{
		if (nrDevices != 1)
			return ERR_DLL_PI_INVALID_INTERFACE_PARAMETER;
		deviceName = szDevices;
	}
	else
	{
		deviceName = FindDeviceNameInUSBList(szDevices, interfaceParameter);
	}
	if (deviceName.empty())
		return DEVICE_NOT_CONNECTED;

	ID_ = ConnectUSB_(deviceName.c_str());
	if (ID_<0)
		return DEVICE_NOT_CONNECTED;

	return DEVICE_OK;
}

std::string PIGCSControllerDLL::FindDeviceNameInUSBList(const char* szDevices, std::string interfaceParameter) const
{
	std::string sDevices(szDevices);
	static ToUpper up(std::locale::classic());
	std::transform(interfaceParameter.begin(), interfaceParameter.end(), interfaceParameter.begin(), up);

	std::vector<std::string> lines = tokenize(sDevices);
	std::vector<std::string>::iterator line;
	for(line = lines.begin(); line != lines.end(); ++line)
	{
		std::string LINE (*line);
		std::transform(LINE.begin(), LINE.end(), LINE.begin(), up);

		if ( LINE.find(interfaceParameter) != std::string::npos )
			return (*line);
	}
	return "";
}

bool PIGCSControllerDLL::qIDN(std::string& sIDN)
{
	if (qIDN_ == NULL)
		return false;

	char szIDN[1024];
	if (qIDN_(ID_, szIDN, 1023) == FALSE)
		return false;
	sIDN = szIDN;
	return true;
}

bool PIGCSControllerDLL::INI(const std::string& axis)
{
	if (INI_ == NULL)
		return false;
	if (INI_(ID_, axis.c_str()) == TRUE)
	{
		return true;
	}
	bool hasINI = false;
	if (CheckError(hasINI))
	{
		if (!hasINI)
		{
			INI_ = NULL;
		}
		return true;
	}
	return false;

}

bool PIGCSControllerDLL::CST(const std::string& axis, const std::string& stagetype)
{
	if (CST_ == NULL)
		return false;
	return (CST_(ID_, axis.c_str(), stagetype.c_str()) == TRUE);
}

bool PIGCSControllerDLL::SVO(const std::string& axis, BOOL svo)
{
	if (SVO_ == NULL)
		return false;
	return (SVO_(ID_, axis.c_str(), &svo) == TRUE);
};

bool PIGCSControllerDLL::FRF(const std::string& axes)
{
    if(FRF_ == NULL)
        return false;
    return (FRF_(ID_, axes.c_str()) == TRUE);
}

bool PIGCSControllerDLL::REF(const std::string& axes)
{
    if(REF_ == NULL)
        return false;
    return (REF_(ID_, axes.c_str()) == TRUE);
}

bool PIGCSControllerDLL::MNL(const std::string& axes)
{
    if(MNL_ == NULL)
        return false;
    return (MNL_(ID_, axes.c_str()) == TRUE);
}

bool PIGCSControllerDLL::FNL(const std::string& axes)
{
    if(FNL_ == NULL)
        return false;
    return (FNL_(ID_, axes.c_str()) == TRUE);
}

bool PIGCSControllerDLL::FPL(const std::string& axes)
{
    if(FPL_ == NULL)
        return false;
    return (FPL_(ID_, axes.c_str()) == TRUE);
}

bool PIGCSControllerDLL::MPL(const std::string& axes)
{
    if(MPL_ == NULL)
        return false;
    return (MPL_(ID_, axes.c_str()) == TRUE);
}

int PIGCSControllerDLL::GetError()
{
    if (GetError_ == NULL)
        return PI_CNTR_NO_ERROR;
    return GetError_(ID_);
}

bool PIGCSControllerDLL::IsReferencing(const std::string& axes, BOOL* bReferencing)
{
    if(IsReferencing_ == NULL)
        return false;
    return (IsReferencing_(ID_, axes.c_str(), bReferencing) == TRUE);
}

bool PIGCSControllerDLL::IsControllerReady( BOOL* bReady)
{
    if(IsControllerReady_ == NULL)
        return false;
    return (IsControllerReady_(ID_, bReady) == TRUE);
}

bool PIGCSControllerDLL::IsMoving(const std::string& axes, BOOL* bIsMoving)
{
    if(IsMoving_ == NULL)
        return false;
    return (IsMoving_(ID_, axes.c_str(), bIsMoving) == TRUE);
}

bool PIGCSControllerDLL::MOV(const std::string& axis, const double* target)
{
    if(MOV_ == NULL)
        return false;
    return (MOV_(ID_, axis.c_str(), target) == TRUE);
}

bool PIGCSControllerDLL::MOV(const std::string& axis1, const std::string& axis2, const double* target)
{
    if(MOV_ == NULL)
        return false;
    return (MOV_(ID_, MakeAxesString(axis1, axis2).c_str(), target) == TRUE);
}

bool PIGCSControllerDLL::qPOS(const std::string& axis, double* position)
{
    if(qPOS_ == NULL)
        return false;
    return (qPOS_(ID_, axis.c_str(), position) == TRUE);
}

bool PIGCSControllerDLL::qPOS(const std::string& axis1, const std::string& axis2, double* position)
{
    if(qPOS_ == NULL)
        return false;
    return (qPOS_(ID_, MakeAxesString(axis1, axis2).c_str(), position) == TRUE);
}

bool PIGCSControllerDLL::STP()
{
    if(STP_ == NULL)
        return false;
    return (STP_(ID_) == TRUE);
}

bool PIGCSControllerDLL::JON(int joystick, int state)
{
    if (JON_ == NULL)
        return false;
    return (JON_(ID_, &joystick, &state, 1) == TRUE);
}

bool PIGCSControllerDLL::qJON(int joystick, int& state)
{
    if (qJON_ == NULL)
        return false;
    return (qJON_(ID_, &joystick, &state, 1) == TRUE);
}

bool PIGCSControllerDLL::VEL(const std::string& axis, const double* velocity)
{
    if(VEL_ == NULL)
        return false;
    return (VEL_(ID_, axis.c_str(), velocity) == TRUE);
}

bool PIGCSControllerDLL::qVEL(const std::string& axis, double* velocity)
{
    if(qVEL_ == NULL)
        return false;
    return (qVEL_(ID_, axis.c_str(), velocity) == TRUE);
}

bool PIGCSControllerDLL::qTPC(int& nrOutputChannels)
{
    if (qTPC_ == NULL)
        return false;
    return (qTPC_(ID_, &nrOutputChannels) == TRUE);
}

#endif
