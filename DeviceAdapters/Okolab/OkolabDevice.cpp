#include "OkolabDevice.h"

#include <ModuleInterface.h>
#include <cmath>


std::vector<std::string> OkolabDevice::_ports;
bool OkolabDevice::_initialized = false;
const char* g_MyDeviceName = "OkoLab";
const char* g_DevicePort = "Device Port";
const char* g_AutoLabel = "Auto";
const char* g_RefreshInterval = "Time between refresh [ms]";
const char* DB_PATH = "";
const char* DAL_VERSION = "2.0.1";

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
	RegisterDevice(g_MyDeviceName, MM::GenericDevice, "Okolab Device Adapter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	// decide which device class to create based on the deviceName parameter
	if (strcmp(deviceName, g_MyDeviceName) == 0)
	{
		// create device
		OkolabDevice *dev = new OkolabDevice();
		if (dev->isValid())
		{
			return dev;
		}
		else
		{
			// There were problems during creation.
			delete dev;
			return 0;
		}
	}

	// ...supplied name not recognized
	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	if (pDevice)
		delete pDevice;
}


// OkolabDevice Implementation
OkolabDevice::OkolabDevice(void)
{
	_busy = false;
	_module = "";
	_deviceHandle = (uint32_t) -1; 
	_timeBetweenUpdates = 1000;
	_okolabThread = NULL;
	int nRet = CreateStringProperty(g_DevicePort, g_AutoLabel, false, NULL, true);
	if (nRet != DEVICE_OK)
	{
		LogMessage("Error creating Device Port", false);
	}
	nRet = CreateIntegerProperty(g_RefreshInterval, _timeBetweenUpdates, false, NULL, true);
	if (nRet != DEVICE_OK)
	{
		LogMessage("Error Time between updates", false);
	}
	nRet = SetPropertyLimits(g_RefreshInterval, 100, 10000);
	if (nRet != DEVICE_OK)
	{
		LogMessage("Error setting Time between updates limits.", false);
	}
	_isValid = initializeVersionAndDetectPorts() == DEVICE_OK;
}


OkolabDevice::~OkolabDevice(void)
{
	if (_okolabThread != NULL)
	{
		if (!_okolabThread->IsStopped())
		{
			_okolabThread->Stop();
			_okolabThread->wait();
		}
		delete 	_okolabThread;
	}
	oko_DevicesCloseAll();
	oko_res_type ret = oko_LibShutDown();
	if (ret != OKO_OK)
	{
		std::ostringstream os;
		os << "Error when shutting down okolib. Err Code " << (int)ret;
		LogMessage(os.str().c_str(), false);
	}
}


bool OkolabDevice::isValid() const
{
	return _isValid;
}

/**
* Intializes the hardware.
* Required by the MM::Device API.
* Typically we access and initialize hardware at this point.
* Device properties are typically created here as well, except
* the ones we need to use for defining initialization parameters.
* Such pre-initialization properties are created in the constructor.
* (This device does not have any pre-initialization properties)
*/
int OkolabDevice::Initialize()
{
	LogMessage("int OkolabDevice::Initialize()", true);

	if (_initialized)
		return DEVICE_OK;

	initializeOkolabLibrary();
	// set property list
	// -----------------
	int nRet = CreateStringProperty(MM::g_Keyword_Name, g_MyDeviceName, true);
	if (nRet != DEVICE_OK)
	{
		LogMessage("Error creating Name", false);
	}
	char preInitPort[MM::MaxStrLength] = "";
	GetProperty(g_DevicePort, preInitPort);
	_workingPort = preInitPort;
	nRet = connectToPort(_workingPort);
	if (nRet == DEVICE_OK)
	{
		nRet = GetProperty(g_RefreshInterval, _timeBetweenUpdates);

		createOkolabProperties();

		createLoggerAndPlaybackFileProperties();
		 _okolabThread = new OkolabThread(this);

		_okolabThread->Start(_timeBetweenUpdates);
		_initialized = true;
		return DEVICE_OK;
	}
	else
	{
		return nRet;
	}
}

void OkolabDevice::LogOkolabError(oko_res_type err, std::string title)
{
	std::string errMessage = title;
	char msg_err[501];
	oko_LibGetLastError(msg_err, 500);
	std::stringstream s;
	s << (int)err;
	std::string converted(s.str());
	errMessage += " Okolab err code: " + converted;
	if (msg_err[0] != 0)
	{
		errMessage += " Error: " + std::string(msg_err);
		LogMessage(errMessage, false);
	}
}

int OkolabDevice::initializeVersionAndDetectPorts()
{
	LogMessage("int OkolabDevice::initializeOkolabLibrary()", true);
	oko_res_type ret = OKO_OK;
	ret = oko_LibInit(DB_PATH);
	LogMessage("int OkolabDevice:2:initializeOkolabLibrary()", true);

	if (ret != OKO_OK)
	{
		LogOkolabError(ret, "Library cannot be initialized. ");
		return DEVICE_ERR;
	}
	int nRet = initializeAvailablePorts();
	if (nRet != DEVICE_OK)
	{
		LogMessage("Error initializing available ports", false);
	}

	nRet = initializeVersionProperties();

	ret = oko_LibShutDown();
	if (ret != OKO_OK)
	{
		LogOkolabError(ret, "Shutdown library");
		return translateError(ret);
	}
	return nRet;
}

void OkolabDevice::createLoggerAndPlaybackFileProperties()
{
	LogMessage("void OkolabDevice::createLoggerAndPlaybackFileProperties()", true);
	CPropertyAction * pAct = new CPropertyAction(this, &OkolabDevice::OnLogToFileChanged);
	int nRet = CreateStringProperty("Log Data to file", "", false, pAct);
	if (nRet != DEVICE_OK || nRet == DEVICE_DUPLICATE_PROPERTY)
	{
		LogMessage("Error creating Log Data to file property", false);
	}

	pAct = new CPropertyAction(this, &OkolabDevice::OnPlaybackFileChanged);
	nRet = CreateStringProperty("Playback data file", "", false, pAct);
	if (nRet != DEVICE_OK || nRet == DEVICE_DUPLICATE_PROPERTY)
	{
		LogMessage("Error creating Playback data file property", false);
	}
}


int OkolabDevice::initializeOkolabLibrary()
{
	LogMessage("int OkolabDevice::initializeOkolabLibrary()", true);
	oko_res_type ret = OKO_OK;
	ret = oko_LibInit(DB_PATH);
	if (ret != OKO_OK)
	{
		LogOkolabError(ret, "Library cannot be initialized. ");
		return translateError(ret);
	}
	return DEVICE_OK;
}

/**
* Shuts down (unloads) the device.
* Required by the MM::Device API.
* Ideally this method will completely unload the device and release all resources.
* Shutdown() may be called multiple times in a row.
* After Shutdown() we should be allowed to call Initialize() again to load the device
* without causing problems.
*/
int OkolabDevice::Shutdown()
{
	LogMessage("int OkolabDevice::Shutdown()", true);
	_initialized = false;

	if (_okolabThread != NULL)
	{
		if (!_okolabThread->IsStopped())
		{
			_okolabThread->Stop();
			_okolabThread->wait();
		}
	}
	oko_res_type ret = oko_DevicesCloseAll();
	ret = oko_LibShutDown();
	return DEVICE_OK;
}


/**
* Obtains device name.
* Required by the MM::Device API.
*/
void OkolabDevice::GetName(char *name) const
{
	LogMessage("void OkolabDevice::GetName(char *name) const", true);
	// Return the name used to referr to this device adapte
	CDeviceUtils::CopyLimitedString(name, g_MyDeviceName);
}	

/*MM::DeviceDetectionStatus OkolabDevice::DetectDevice()
{
	LogMessage("MM::DeviceDetectionStatus OkolabDevice::DetectDevice()", true);
	MM::DeviceDetectionStatus result = MM::Misconfigured;
	try
	{
		if (0 < _ports.size())
		{
			result=MM::CanNotCommunicate;
			if(connectToPort(_workingPort))
			{
				return MM::CanCommunicate;
			}
		}
	}
	catch(...)
	{
		LogMessage("DetectDevice Exception!",false);
	}
	return result;
}*/

int OkolabDevice::connectToPort(const std::string &port)
{
	LogMessage("bool OkolabDevice::connectToPort(const std::string &port)", true);
	LogMessage(port, false);
	int nRet = DEVICE_OK;
	if (port == g_AutoLabel)
	{
		nRet = openAutoPort();
	}
	return DEVICE_NOT_CONNECTED == nRet ? nRet : openDeviceIfNecessary();
}

void OkolabDevice::DetectPorts()
{
	LogMessage("void OkolabDevice::DetectPorts()", true);
	// Available Ports
	_ports.clear();

	_ports.push_back(g_AutoLabel);

	unsigned int np;
	oko_res_type ret = ret = oko_LibGetNumberOfPorts(&np);
	if (OKO_OK != ret)
	{
		LogOkolabError(ret, "Error getting number of Ports");
	}

	char *names[100];
	for (unsigned h = 0; h < 100; h++)
		names[h] = (char*) malloc(100 * sizeof(char));

	ret = oko_LibGetPortNames(names);
	if (OKO_OK != ret)
	{
		LogOkolabError(ret, "Error getting number of Ports");
	}

	for (unsigned k = 0; k < np; k++)
	{
		std::string portName(names[k]);
		/*uint32_t dev = -1;
		ret = oko_DeviceOpen(names[k], &dev);
		if (ret == OKO_OK)
		{*/
		_ports.push_back(portName);
		/*}
		ret = oko_DeviceClose(dev);*/
	}
	for (unsigned h = 0; h < 100; h++)
		free(names[h]);
}

int OkolabDevice::openAutoPort()
{
	LogMessage("void OkolabDevice::openAutoPort()", true);
	if (_deviceHandle != -1)
	{
		oko_DeviceClose(_deviceHandle);
	}
	// This function is not implemented on okolib???
	_workingPort = "";

	for (std::size_t i = 0; i < _ports.size(); ++i)
	{
		if (_ports[i] != g_AutoLabel)
		{
			_workingPort = _ports[i];
			oko_res_type res = oko_DeviceOpen(_ports[i].c_str(), &_deviceHandle);
			if (res == OKO_OK)
			{
				_workingPort = _ports[i];
				SetProperty(g_DevicePort, _workingPort.c_str());
				break;
			}
			else
			{
				LogOkolabError(res, "Open Auto Port");
			}
		}
	}
	return _workingPort == "" ? DEVICE_NOT_CONNECTED : DEVICE_OK;
}

int OkolabDevice::initializeAvailablePorts()
{
	LogMessage("int OkolabDevice::initializeAvailablePorts()", true);
	DetectPorts();
	int nRet = SetAllowedValues(g_DevicePort, _ports);
	return nRet;
}

int OkolabDevice::initializeVersionProperties()
{
	LogMessage("int OkolabDevice::initializeVersionProperties()", true);
	int nRet = CreateStringProperty("Device adapter version", DAL_VERSION, true, NULL);
	if (nRet != DEVICE_OK && nRet != DEVICE_DUPLICATE_PROPERTY)
	{
		LogMessage("Error creating Device adapter Version", false);
	}

	char okolibVersion[100] = "";
	oko_LibGetVersion(okolibVersion);
	nRet = CreateStringProperty("Okolib version", okolibVersion, true, NULL);
	if (nRet != DEVICE_OK && nRet != DEVICE_DUPLICATE_PROPERTY)
	{
		LogMessage("Error creating Okolib Version", false);
	}

	nRet = CreateStringProperty("Device Adapter Version", DAL_VERSION, false, NULL, true);
	if (nRet != DEVICE_OK && nRet != DEVICE_DUPLICATE_PROPERTY)
	{
		LogMessage("Error creating Device adapter Version", false);
	}

	std::vector<std::string> allowedValues;
	allowedValues.push_back(DAL_VERSION);
	SetAllowedValues("Device Adapter Version", allowedValues);

	nRet = CreateStringProperty("Okolib Version", okolibVersion, false, NULL, true);
	if (nRet != DEVICE_OK && nRet != DEVICE_DUPLICATE_PROPERTY)
	{
		LogMessage("Error creating Device adapter Version", false);
	}

	return nRet;
}

void OkolabDevice::createOkolabProperties()
{
	LogMessage("void OkolabDevice::createOkolabProperties()", true);
	if (0 < _workingPort.length())
	{
		oko_res_type ret = OKO_ERR_FAIL;

		uint32_t num = 0;
		ret = oko_PropertiesGetNumber(_deviceHandle, &num);
		if (ret == OKO_OK)
		{
			for (uint32_t i = 0; i < num; ++i)
			{
				AddOkolabProperty(i);
			}
		}
		else
		{
			LogOkolabError(ret, "Refresh Properties");
		}
	}
}

void OkolabDevice::AddOkolabProperty(uint32_t propertyIndex)
{
	LogMessage("void OkolabDevice::AddOkolabProperty(uint32_t propertyIndex)", true);
	char name[50] = "";
	oko_res_type ret = oko_PropertyGetName(_deviceHandle, propertyIndex, name);
	if (ret != OKO_OK)
	{
		LogOkolabError(ret, "Add Property");
		return;
	}

	// If the property already exists, do nothing
	if (HasProperty(name))
	{
		return;
	}

	oko_prop_type type = OKO_PROP_UNDEF;

	ret = oko_PropertyGetType(_deviceHandle, name, &type);
	if (ret != OKO_OK)
	{
		LogOkolabError(ret, "Add Property");
		return;
	}

	bool isReadOnly = false;
	ret = oko_PropertyGetReadOnly(_deviceHandle, name, &isReadOnly);
	if (ret != OKO_OK)
	{
		LogOkolabError(ret, "Add Property");
		return;
	}

	int nRet = DEVICE_OK;

	CPropertyActionEx * pAct = new CPropertyActionEx(this, &OkolabDevice::OnOkolabPropertyChanged, propertyIndex);
	ret = oko_PropertyUpdate(_deviceHandle, name);
	if (ret != OKO_OK)
	{
		LogOkolabError(ret, "Add Property");
	}
	std::string errString("");
	switch(type)
	{
	case OKO_PROP_STRING:
		{
			char value[1024] = "";
			ret = oko_PropertyReadString(_deviceHandle, name, value);
			if (ret != OKO_OK)
			{
				LogOkolabError(ret, "Add Property");
				errString = createPropertyError(ret);
			}
			nRet = CreateStringProperty(name, value, isReadOnly, isReadOnly ? NULL : pAct);
			break;
		}
	case OKO_PROP_INT:
		{
			int32_t value = 0;
			ret = oko_PropertyReadInt(_deviceHandle, name, &value);
			if (ret != OKO_OK)
			{
				LogOkolabError(ret, "Add Property");
				errString = createPropertyError(ret);
			}
			nRet = CreateIntegerProperty(name, value, isReadOnly, isReadOnly ? NULL : pAct);
			break;
		}
	case OKO_PROP_DOUBLE:
		{
			double value = 0.;
			ret = oko_PropertyReadDouble(_deviceHandle, name, &value);
			if (ret != OKO_OK)
			{
				LogOkolabError(ret, "Add Property");
				errString = createPropertyError(ret);
			}
			nRet = CreateFloatProperty(name, value, isReadOnly, isReadOnly ? NULL : pAct);
			break;
		}
	case OKO_PROP_ENUM:
		{
			char value[1024] = "";
			ret = oko_PropertyReadString(_deviceHandle, name, value);
			if (ret != OKO_OK)
			{
				LogOkolabError(ret, "Add Property");
				errString = createPropertyError(ret);
			}
			nRet = CreateStringProperty(name, value, isReadOnly, isReadOnly ? NULL : pAct);
			unsigned int numEnums = 0;
			ret = oko_PropertyGetEnumNumber(_deviceHandle, name, &numEnums);
			if (ret != OKO_OK)
			{
				LogOkolabError(ret, "Add Property");
				errString = createPropertyError(ret);
			}
			std::vector<std::string> enumValues;
			for (unsigned int i = 0; i < numEnums; ++i)
			{
				char enumValue[1024] = "";
				ret = oko_PropertyGetEnumName(_deviceHandle, name, i, enumValue);
				if (ret != OKO_OK)
				{
					LogOkolabError(ret, "Add Property");
					errString = createPropertyError(ret);
				}
				enumValues.push_back(std::string(enumValue));
			}
			SetAllowedValues(name, enumValues);
			break;
		}
	case OKO_PROP_UNDEF:
	default:
		nRet = DEVICE_INVALID_PROPERTY_TYPE;
		break;
	}

	if (errString.length() > 0)
	{
		SetProperty(name, errString.c_str());
	}

	bool hasLimits = false;
	ret = oko_PropertyHasLimits(_deviceHandle, name, &hasLimits);
	if (ret != OKO_OK)
	{
		LogOkolabError(ret, "Add Property");
		return;
	}
	if (hasLimits)
	{
		double low = 0.;
		double high = 0.;
		ret = oko_PropertyGetLimits(_deviceHandle, name, &low, &high);
		if (ret != OKO_OK)
		{
			LogOkolabError(ret, "Add Property");
			return;
		}
		SetPropertyLimits(name, low, high);
	}
}


int OkolabDevice::OnOkolabPropertyChanged(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
	LogMessage("int OkolabDevice::OnOkolabPropertyChanged(MM::PropertyBase* pProp, MM::ActionType eAct, long index)", true);
	char name[50] = "";
	oko_res_type ret;
	ret = oko_PropertyGetName(_deviceHandle, (uint32_t) index, name);
	if (ret != OKO_OK)
	{
		LogOkolabError(ret, "On Okolab Property Changed");
	}


	if (eAct == MM::BeforeGet)
	{
		ret = oko_PropertyUpdate(_deviceHandle, name);
		if (ret != OKO_OK)
		{
			LogOkolabError(ret, "Property Changed");
		}
		switch(pProp->GetType())
		{
		case MM::String:
			{
				char value[1024] = "";
				ret = oko_PropertyReadString(_deviceHandle, name, value);
				if (ret != OKO_OK)
				{
					LogOkolabError(ret, "Property Changed");
					std::string errString = createPropertyError(ret);
					pProp->Set(errString.c_str());
				}
				else
				{
					pProp->Set(value);
				}
				break;
			}
		case MM::Float:
			{
				double value = 0.;
				ret = oko_PropertyReadDouble(_deviceHandle, name, &value);
				if (ret != OKO_OK)
				{
					LogOkolabError(ret, "Property Changed");
					std::string errString = createPropertyError(ret);
					pProp->Set(errString.c_str());
				}
				else
				{
					pProp->Set(value);
				}
				break;
			}
		case MM::Integer:
			{
				int32_t value = 0;
				ret = oko_PropertyReadInt(_deviceHandle, name, &value);
				if (ret != OKO_OK)
				{
					LogOkolabError(ret, "Property Changed");
					std::string errString = createPropertyError(ret);
					pProp->Set(errString.c_str());
				}
				else
				{
					pProp->Set((long) value);
				}
				break;
			}
		case MM::Undef:
		default:
			return DEVICE_INVALID_PROPERTY;
			break;
		}
	}
	else if (eAct == MM::AfterSet)
	{
		if (!propertyLocked(name))
		{
			propertyLock(name);
			switch(pProp->GetType())
			{
			case MM::String:
				{
					std::string value = "";
					pProp->Get(value);
					ret = oko_PropertyWriteString(_deviceHandle, name, value.c_str(), false);
					if (ret != OKO_OK)
					{
						LogOkolabError(ret, "Property Changed");
					}
					break;
				}
			case MM::Float:
				{
					double value = 0.;
					pProp->Get(value);
					ret = oko_PropertyWriteDouble(_deviceHandle, name, value, false);
					if (ret != OKO_OK)
					{
						LogOkolabError(ret, "Property Changed");
					}
					break;
				}
			case MM::Integer:
				{
					long value = 0;
					pProp->Get(value);
					ret = oko_PropertyWriteInt(_deviceHandle, name, (int32_t) value, false);
					if (ret != OKO_OK)
					{
						LogOkolabError(ret, "Property Changed");
					}
					break;
				}
			case MM::Undef:
			default:
				return DEVICE_INVALID_PROPERTY;
				break;
			}
			propertyUnlock(name);
		}
	}
	return DEVICE_OK;
}

int OkolabDevice::OnLogToFileChanged(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	oko_res_type res = OKO_OK;
	if (eAct == MM::BeforeGet)
	{
		char filename[100];
		res = oko_PropertyLoggingGetFileName(_deviceHandle, filename);
	}
	else if (eAct == MM::AfterSet)
	{
		std::string value = "";
		pProp->Get(value);
		if (value != "")
		{
			res = oko_StartPropertyLogging(_deviceHandle, value.c_str(), 1000);
		}
		else
		{
			res = oko_StopPropertyLogging(_deviceHandle);
		}
	}
	return res == OKO_OK ? DEVICE_OK : DEVICE_ERR;
}

int OkolabDevice::OnPlaybackFileChanged(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	oko_res_type res = OKO_OK;
	if (eAct == MM::BeforeGet)
	{
		char filename[100];
		res = oko_PlaybakGetFileName(_deviceHandle, filename);
	}
	else if (eAct == MM::AfterSet)
	{
		std::string value = "";
		pProp->Get(value);
		if (value != "")
		{
			res = oko_StartPlayback(_deviceHandle, value.c_str());
		}
		else
		{
			res = oko_StopPlayback(_deviceHandle);
		}
	}
	return res == OKO_OK ? DEVICE_OK : DEVICE_ERR;
}

int OkolabDevice::updateOkolabProperties(MM::MMTime startTime)
{
	if (!_initialized)
		return DEVICE_OK;

	LogMessage("int OkolabDevice::updateOkolabProperties(MM::MMTime startTime)", true);
	if (0 < _workingPort.length())
	{
		oko_res_type ret = OKO_ERR_FAIL;

		uint32_t num = 0;
		ret = oko_PropertiesGetNumber(_deviceHandle, &num);
		if (ret != OKO_OK)
		{
			LogOkolabError(ret, "Property Changed");
			num = 0;
		}
		for (uint32_t i = 0; i < num; ++i)
		{
			if (_okolabThread->IsStopped() || _okolabThread->IsSuspended())
			{
				return DEVICE_OK;
			}
			char name[50] = "";
			ret = oko_PropertyGetName(_deviceHandle, i, name);
			if (ret != OKO_OK)
			{
				LogOkolabError(ret, "Property Changed");
				continue;
			}
			if (propertyLocked(name))
			{
				continue;
			}
			ret = oko_PropertyUpdate(_deviceHandle, name);
			if (ret != OKO_OK)
			{
				LogOkolabError(ret, "Property Changed");
			}

			char value[1024] = "";			
			MM::PropertyType propType;
			GetPropertyType(name, propType);
			switch (propType)
			{
			case MM::String:
				{
					ret = oko_PropertyReadString(_deviceHandle, name, value);
					if (ret != OKO_OK)
					{
						LogOkolabError(ret, "Property Changed");
						std::ostringstream oss;
						oss << "ERROR " << (int) ret;
						strcpy(value, oss.str().c_str());						
					}
				}
				break;
			case MM::Float:
				{
					double v = 0.0;
					ret = oko_PropertyReadDouble(_deviceHandle, name, &v);
					if (ret != OKO_OK)
					{
						LogOkolabError(ret, "Property Changed");
						std::ostringstream oss;
						oss << "ERROR " << (int) ret;
						strcpy(value, oss.str().c_str());
					}
					else
					{
						std::ostringstream oss;
						if (v != v) // This is a NaN check
						{

							oss << "ERROR NaN";
						}
						else
						{
							oss << v;
						}
						strcpy(value, oss.str().c_str());
					}
				}
				break;
			case MM::Integer:
				{
					int32_t v = 0;
					ret = oko_PropertyReadInt(_deviceHandle, name, &v);
					if (ret != OKO_OK)
					{
						LogOkolabError(ret, "Property Changed");
						std::ostringstream oss;
						oss << "ERROR " << (int) ret;
						strcpy(value, oss.str().c_str());
					}
					else
					{
						strcpy(value, CDeviceUtils::ConvertToString(v));
					}
				}
				break;
			case MM::Undef:
			default:
				break;
			}
			SetProperty(name, value);
			OnPropertyChanged(name, value);
		}
	}

	Sleep(_timeBetweenUpdates);
	LogMessage("updateProperties");
	return DEVICE_OK;
}

int OkolabDevice::openDeviceIfNecessary()
{
	LogMessage("void OkolabDevice::openDeviceIfNecessary()", true);
	oko_res_type ret;
	if (_deviceHandle != -1)
	{
		char port[100] = "";
		ret = oko_DeviceGetPortName(_deviceHandle, port);
		if (_workingPort != std::string(port))
		{
			ret = oko_DeviceClose(_deviceHandle);
			_deviceHandle = (uint32_t) -1;
			_busy = false;
		}
	}
	if (_deviceHandle == -1)
	{
		ret = oko_DeviceOpen(_workingPort.c_str(), &_deviceHandle);
		_busy = false;
		if (ret != OKO_OK)
		{
			LogOkolabError(ret, "Opening device");
		}

		return translateError(ret);
	}
	return DEVICE_OK;
}

int OkolabDevice::translateError(oko_res_type ret)
{
	int nRet = DEVICE_OK;
	switch (ret)
	{
	case OKO_OK: //!< Operation completed successfully.
		nRet = DEVICE_OK;
		break;
	case OKO_ERR_UNINIT: //!< Library not initialized yet.
		nRet = DEVICE_NATIVE_MODULE_FAILED;
		break;
	case OKO_ERR_ARG: //!< Invalid arguments were passed to the function.
		nRet = DEVICE_INVALID_INPUT_PARAM;
		break;
	case OKO_ERR_FAIL: //!< A system error occurred while executing the operation.
		nRet = DEVICE_ERR;
		break;
	case OKO_ERR_NOTSUPP: //!< The requested operation is not supported by this system or device.
		nRet = DEVICE_NOT_SUPPORTED;
		break;
	case OKO_ERR_CLOSED: //!< The specified device is not opened.
		nRet = DEVICE_NOT_CONNECTED;
		break;
	case OKO_ERR_UNCONN: //!< The specified device is not connected.
		nRet = DEVICE_NOT_CONNECTED;
		break;
	case OKO_ERR_PORT_BUSY: //!< Serial port busy
	case OKO_ERR_PORT_CFG: //!< Serial port configuration failed
	case OKO_ERR_PORT_SPEED: //!< Serial port speed settings failed
	case OKO_ERR_DB_OPEN: //!< Database error on open
		nRet = DEVICE_INTERNAL_INCONSISTENCY;
		break;
	case OKO_ERR_PROP_NOTFOUND:	 //!< Property not found
		nRet = DEVICE_ERR;
	case OKO_ERR_DEV_NOTFOUND: //!< Device not found
		nRet = DEVICE_NOT_CONNECTED;
		break;
	case OKO_ERR_PROTOCOL: //!< Protocol error
		nRet = DEVICE_ERR;
		break;
	case OKO_ERR_ENUM_NOTFOUND: //!< Enum of the Property not found
		nRet = DEVICE_ERR;
		break;
	case OKO_ERR_MODULE_NOTFOUND: //!< Module specified not found
		nRet = DEVICE_ERR;
		break;
	case OKO_ERR_UNDEF: //!< Undefined error
		nRet = DEVICE_ERR;
		break;
	default: 
		nRet = DEVICE_ERR;
		break;
	}
	return nRet;
}

std::string OkolabDevice::createPropertyError(oko_res_type ret)
{
	std::ostringstream os;
	os << "ERROR " << (int)ret;
	return os.str();
}

/*int OkolabDevice::StartSequenceAcquisition(double interval)
{
	(void) interval;
	LogMessage("int OkolabDevice::StartSequenceAcquisition(double interval)", true);
	int ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK)
		return ret;

	return DEVICE_OK;
}*/

void OkolabDevice::propertyLock(const std::string &propertyName)
{
	_propertyUnderEditionLock.Lock();
	_propertiesUnderEdition.push_back(propertyName);
	_propertyUnderEditionLock.Unlock();
}

void OkolabDevice::propertyUnlock(const std::string &propertyName)
{
	_propertyUnderEditionLock.Lock();
	_propertiesUnderEdition.remove(propertyName);
	_propertyUnderEditionLock.Unlock();
}

bool OkolabDevice::propertyLocked(const std::string &propertyName)
{
	for (std::list<std::string>::iterator it = _propertiesUnderEdition.begin(); it != _propertiesUnderEdition.end(); ++it)
	{
		if (propertyName == (*it))
			return true;
	}
	return false;
}


/////
OkolabThread::OkolabThread(OkolabDevice* pDevice)
	: _intervalMs(default_intervalMS), 
	_stop(true), _suspend(false), _device(pDevice),
	_startTime(0), _actualDuration(0), _lastFrameTime(0)
{
}

OkolabThread::~OkolabThread()
{
}

void OkolabThread::Stop() {
	MMThreadGuard g(this->_stopLock);
	_stop = true;
}

void OkolabThread::Start(double intervalMs)
{
	MMThreadGuard g1(this->_stopLock);
	MMThreadGuard g2(this->_suspendLock);
	_intervalMs = intervalMs;
	_stop = false;
	_suspend = false;
	activate();
	_actualDuration = 0;
	_startTime = _device->GetCurrentMMTime();
	_lastFrameTime = 0;
	Sleep(500);
}

bool OkolabThread::IsStopped(){
	MMThreadGuard g(this->_stopLock);
	return _stop;
}

void OkolabThread::Suspend() {
	MMThreadGuard g(this->_suspendLock);
	_suspend = true;
}

bool OkolabThread::IsSuspended() {
	MMThreadGuard g(this->_suspendLock);
	return _suspend;
}

void OkolabThread::Resume() {
	MMThreadGuard g(this->_suspendLock);
	_suspend = false;
}

int OkolabThread::svc(void) throw()
{
	int ret=DEVICE_ERR;
	try 
	{
		do
		{  
			ret = _device->updateOkolabProperties(_startTime);
		}
		while (DEVICE_OK == ret && !IsStopped());
		if (IsStopped())
			_device->LogMessage("SeqAcquisition interrupted by the user\n");
	} catch(...)
	{
		_device->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
	}
	_stop = true;
	_actualDuration = _device->GetCurrentMMTime() - _startTime;
	//TODO: work on this!!!
	//_device->OnThreadExiting();
	return ret;
}

