#pragma warning(push)
#pragma warning(disable : 4482)
#pragma warning(disable : 4251) // Note: need to have a C++ interface, i.e., compiler versions need to match!

#include "SpinnakerCamera.h"
#include "../../MMDevice/ModuleInterface.h"
#include <vector>
#include <string>


std::wstring StringToWString(const std::string& str)
{
	auto wss = std::wstringstream();
	wss << str.c_str();
	return wss.str();
}

std::wstring StringToWString(const GENICAM::gcstring& str)
{
	auto wss = std::wstringstream();
	wss << str.c_str();
	return wss.str();
}

std::wstring StringToWString(GENAPI::IString& str)
{
	auto wss = std::wstringstream();
	wss << str.ToString().c_str();
	return wss.str();
}

struct CamNameAndSN
{
	GENICAM::gcstring name;
	GENICAM::gcstring serialNumber;
};


enum NodeAvailableMask {
	NAM_NONE = 0,
	NAM_READ = 1,
	NAM_WRITE = 2
};

template<NodeAvailableMask NAM, class NodeType>
bool isNodeAvailable(const NodeType& node)
{
	bool available = GENAPI::IsAvailable(node);
	if (NAM & NAM_READ)
		available &= GENAPI::IsReadable(node);
	else if (NAM & NAM_WRITE)
		available &= GENAPI::IsWritable(node);

	return available;
}

std::vector<CamNameAndSN> GetSpinnakeerCameraNamesAndSNs()
{
	auto out = std::vector<CamNameAndSN>();
	auto system = SPKR::System::GetInstance();
	auto camList = system->GetCameras();

	for (unsigned int i = 0; i < camList.GetSize(); i++)
	{
		CamNameAndSN camInfo;

		GENAPI::INodeMap &nm =
			camList.GetByIndex(i)
			->GetTLDeviceNodeMap();


		GENAPI::CValuePtr nameNode = nm.GetNode("DeviceModelName");

		if (isNodeAvailable<NAM_READ>(nameNode))
		{
			camInfo.name = nameNode->ToString();
		}

		GENAPI::CValuePtr serialNumberNode = nm.GetNode("DeviceSerialNumber");

		if (isNodeAvailable<NAM_READ>(serialNumberNode))
		{
			camInfo.serialNumber = serialNumberNode->ToString();
		}

		out.push_back(camInfo);
	}

	camList.Clear();
	system = NULL;
	return out;
}


MODULE_API void InitializeModuleData()
{
	try
	{
		std::vector<CamNameAndSN> camInfos =
			GetSpinnakeerCameraNamesAndSNs();

		for (int i = 0; i < camInfos.size(); i++)
			RegisterDevice(camInfos[i].name.c_str(),
				MM::CameraDevice,
				"Point Grey Spinnaker Camera");
	}
	catch (SPKR::Exception &ex)
	{
		std::wstringstream wss = std::wstringstream();
		wss << ex.what();
	}
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	return new SpinnakerCamera(GENICAM::gcstring(deviceName));
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

SpinnakerCamera::SpinnakerCamera(GENICAM::gcstring name)
	: CCameraBase<SpinnakerCamera>(),
	m_system(NULL),
	m_cam(NULL),
	m_imageBuff(NULL),
	m_aqThread(NULL)
{
	InitializeDefaultErrorMessages();

	CreateProperty("Serial Number", "", MM::String, false, 0, true);
	try
	{
		std::vector<CamNameAndSN> camInfos =
			GetSpinnakeerCameraNamesAndSNs();

		for (int i = 0; i < camInfos.size(); i++)
			if (camInfos[i].name == name)
				AddAllowedValue("Serial Number", camInfos[i].serialNumber.c_str());
	}
	catch (SPKR::Exception &ex)
	{
		LogMessage(ex.what());
	}

	m_aqThread = new SpinnakerAcquisitionThread(this);
}


SpinnakerCamera::~SpinnakerCamera()
{
	StopSequenceAcquisition();
	delete m_aqThread;
	if (m_imageBuff)
		delete[] m_imageBuff;
}

int SpinnakerCamera::Initialize()
{
	char SN[MM::MaxStrLength];
	GetProperty("Serial Number", SN);
	m_SN = GENICAM::gcstring(SN);

	if (m_SN == "")
	{
		SetErrorText(SPKR_ERROR, "No Serial Number Provided! Cannot Identify Camera!");
		return SPKR_ERROR;
	}

	m_system = SPKR::System::GetInstance();
	if (m_system == NULL)
	{
		SetErrorText(SPKR_ERROR, "Spinnaker System Object Pointer is Null!");
		return SPKR_ERROR;
	}

	auto camList = m_system->GetCameras();

	if (camList.GetSize() == 0)
	{
		SetErrorText(SPKR_ERROR, "No Cameras Attached!");
		return SPKR_ERROR;
	}

	for (unsigned int i = 0; i < camList.GetSize(); i++)
	{
		auto &nm = camList.GetByIndex(i)
			->GetTLDeviceNodeMap();


		GENAPI::CValuePtr SNNode = nm.GetNode("DeviceSerialNumber");

		if (isNodeAvailable<NAM_READ>(SNNode))
		{
			if (SNNode->ToString() == m_SN)
			{
				m_cam = camList.GetByIndex(i);

				GENAPI::CValuePtr modelNode = nm.GetNode("DeviceModelName");

				m_cam->Init();
				break;
			}
		}
	}
	camList.Clear();

	if (m_cam == NULL)
	{
		this->Shutdown();
		SetErrorText(SPKR_ERROR, "Could not find camera with serial number: " + m_SN);
		return SPKR_ERROR;
	}



	SPKR::TriggerModeEnums originalTriggerMode;
	try
	{
		originalTriggerMode = m_cam->TriggerMode.GetValue();
		m_cam->ExposureAuto.SetValue(SPKR::ExposureAuto_Off);
		m_cam->ExposureMode.SetValue(SPKR::ExposureMode_Timed);
		m_cam->AcquisitionMode.SetValue(SPKR::AcquisitionMode_SingleFrame);
		m_cam->TriggerMode.SetValue(SPKR::TriggerMode_Off);
	}
	catch (SPKR::Exception &ex)
	{
		SetErrorText(SPKR_ERROR, ex.what());
		return SPKR_ERROR;
	}

	CPropertyAction* pAct;
	GENAPI::INodeMap &nm = m_cam->GetNodeMap();

	try
	{
		GENAPI::CEnumerationPtr AFRA_enumeration = nm.GetNode("AcquisitionFrameRateAuto");
		if (isNodeAvailable<NAM_READ>(AFRA_enumeration))
		{
			pAct = new CPropertyAction(this, &SpinnakerCamera::OnFrameRateAuto);
			GENAPI::StringList_t symbolics;
			AFRA_enumeration->GetSymbolics(symbolics);

			CreateProperty("Frame Rate Auto", symbolics[0].c_str(), MM::String, false, pAct);
			for (int i = 0; i < symbolics.size(); i++)
				AddAllowedValue("Frame Rate Auto", symbolics[i].c_str());
		}
	}
	catch (SPKR::Exception &ex)
	{
		LogMessage(ex.what());
	}

	try
	{
		GENAPI::CBooleanPtr AFRCE = nm.GetNode("AcquisitionFrameRateEnabled");
		if (isNodeAvailable<NAM_READ>(AFRCE))
		{
			LogMessage("Creating frame rate enabled...");
			pAct = new CPropertyAction(this, &SpinnakerCamera::OnFrameRateEnabled);
			CreateProperty("Frame Rate Control Enabled", "1", MM::Integer, false, pAct);
			AddAllowedValue("Frame Rate Control Enabled", "1");
			AddAllowedValue("Frame Rate Control Enabled", "0");
			//CreatePropertyFromBool("Frame Rate Control Enabled", m_cam->AcquisitionFrameRateEnable, &SpinnakerCamera::OnFrameRateEnabled);

		}
		else
		{
			LogMessage("Failed to create frame rate enabled...");
		}
	}
	catch (SPKR::Exception &ex)
	{
		LogMessage(ex.what());
	}


	try
	{
		GENAPI::CEnumerationPtr VM = nm.GetNode("VideoMode");
		GENAPI::CIntegerPtr BH = nm.GetNode("BinningHorizontal");
		GENAPI::CIntegerPtr BV = nm.GetNode("BinningVertical");
		if (isNodeAvailable<NAM_WRITE>(VM))
		{
			LogMessage("Using VideoMode for Binning");
			GENAPI::StringList_t videoModes;
			GENICAM::gcstring currentVideoMode = VM->GetCurrentEntry()->GetSymbolic();
			VM->GetSymbolics(videoModes);

			pAct = new CPropertyAction(this, &SpinnakerCamera::OnBinningEnum);
			CreateProperty(MM::g_Keyword_Binning, VM->GetCurrentEntry()->GetSymbolic(), MM::String, false, pAct);
			for (int i = 0; i < videoModes.size(); i++)
				AddAllowedValue(MM::g_Keyword_Binning, videoModes[i].c_str());


			for (int i = 0; i < videoModes.size(); i++)
			{
				VM->FromString(videoModes[i]);
				GENAPI::CEnumerationPtr BC = nm.GetNode("BinningControl");

				if (isNodeAvailable<NAM_WRITE>(BC))
					continue;

				GENAPI::StringList_t binningModes;
				BC->GetSymbolics(binningModes);

				pAct = new CPropertyAction(this, &SpinnakerCamera::OnBinningModeEnum);
				CreateProperty("Binning Mode", BC->GetCurrentEntry()->GetSymbolic().c_str(), MM::String, false, pAct);
				for (int j = 0; j < binningModes.size(); j++)
					AddAllowedValue("Binning Mode", binningModes[j].c_str());

				VM->FromString(currentVideoMode);
				break;
			}
		}
		else if (isNodeAvailable<NAM_WRITE>(BH) && isNodeAvailable<NAM_WRITE>(BV))
		{
			LogMessage("Using BinningHorizontal and BinningVertical for Binning");

			BH->SetValue(1); BV->SetValue(1);

			pAct = new CPropertyAction(this, &SpinnakerCamera::OnBinningInt);
			CreateProperty(MM::g_Keyword_Binning, "1", MM::String, false, pAct);

#ifdef max 
#define SPKR_MAX
#undef max
#endif
#ifdef min 
#define SPKR_MIN
#undef min
#endif
			auto max = std::min(BH->GetMax(), BV->GetMax());
			auto min = std::max(BH->GetMin(), BV->GetMin());

			for (int64_t i = min; i <= max; i++)
			{
				std::stringstream ss;
				ss << i;
				AddAllowedValue(MM::g_Keyword_Binning, ss.str().c_str());
			}

#ifdef SPKR_MAX
#define max(a,b)            (((a) > (b)) ? (a) : (b))
#undef SPKR_MAX
#endif

#ifdef SPKR_MIN
#define min(a,b)            (((a) < (b)) ? (a) : (b))
#undef SPKR_MIN
#endif

		}
		else
		{
			LogMessage("Unknown Binning Control");
		}
	}
	catch (SPKR::Exception &ex)
	{
		LogMessage(ex.what());
	}

	CreatePropertyFromEnum("Pixel Format", m_cam->PixelFormat, &SpinnakerCamera::OnPixelFormat);
	CreatePropertyFromEnum("Test Pattern", m_cam->TestPattern, &SpinnakerCamera::OnTestPattern);
	//CreatePropertyFromBool("Frame Rate Control Enabled", m_cam->AcquisitionFrameRateEnable, &SpinnakerCamera::OnFrameRateEnabled);
	CreatePropertyFromFloat("Frame Rate", m_cam->AcquisitionFrameRate, &SpinnakerCamera::OnFrameRate);
	CreatePropertyFromFloat("Gain", m_cam->Gain, &SpinnakerCamera::OnGain);
	CreatePropertyFromEnum("Gain Auto", m_cam->GainAuto, &SpinnakerCamera::OnGainAuto);
	CreatePropertyFromEnum("Exposure Auto", m_cam->ExposureAuto, &SpinnakerCamera::OnExposureAuto);
	CreatePropertyFromBool("Gamma Enabled", m_cam->GammaEnable, &SpinnakerCamera::OnGammaEnabled);
	CreatePropertyFromFloat("Gamma", m_cam->Gamma, &SpinnakerCamera::OnGamma);
	CreatePropertyFromFloat("Black Level", m_cam->BlackLevel, &SpinnakerCamera::OnBlackLevel);
	CreatePropertyFromEnum("Black Level Auto", m_cam->BlackLevelAuto, &SpinnakerCamera::OnBlackLevelAuto);

	try
	{
		m_cam->TriggerMode.SetValue(SPKR::TriggerMode_On);
	}
	catch (SPKR::Exception &ex)
	{
		SetErrorText(SPKR_ERROR, ex.what());
		return SPKR_ERROR;
	}


	CreatePropertyFromEnum("Trigger Selector", m_cam->TriggerSelector, &SpinnakerCamera::OnTriggerSelector);
	CreatePropertyFromEnum("Trigger Mode", m_cam->TriggerMode, &SpinnakerCamera::OnTriggerMode);
	CreatePropertyFromEnum("Trigger Source", m_cam->TriggerSource, &SpinnakerCamera::OnTriggerSource);
	CreatePropertyFromEnum("Trigger Activation", m_cam->TriggerActivation, &SpinnakerCamera::OnTriggerActivation);
	CreatePropertyFromEnum("Trigger Overlap", m_cam->TriggerOverlap, &SpinnakerCamera::OnTriggerOverlap);
	CreatePropertyFromFloat("Trigger Delay", m_cam->TriggerDelay, &SpinnakerCamera::OnTriggerDelay);
	CreatePropertyFromEnum("Exposure Mode", m_cam->ExposureMode, &SpinnakerCamera::OnExposureMode);
	CreatePropertyFromEnum("User Output Selector", m_cam->UserOutputSelector, &SpinnakerCamera::OnUserOutputSelector);
	CreatePropertyFromBool("User Output Value", m_cam->UserOutputValue, &SpinnakerCamera::OnUserOutputValue);

	CreatePropertyFromEnum("Line Selector", m_cam->LineSelector, &SpinnakerCamera::OnLineSelector);
	CreatePropertyFromEnum("Line Mode", m_cam->LineMode, &SpinnakerCamera::OnLineMode);
	CreatePropertyFromBool("Line Inverter", m_cam->LineInverter, &SpinnakerCamera::OnLineInverter);
	CreatePropertyFromEnum("Line Source", m_cam->LineSource, &SpinnakerCamera::OnLineSource);

	/*try
	{
		GENAPI::CEnumerationPtr LS = nm.GetNode("LineSelector");
		if (isNodeAvailable<NAM_WRITE>(LS))
		{
			LS->GetSymbolics(m_gpioLines);
			for (int i = 0; i < m_gpioLines.size(); i++)
			{
				LS->FromString(m_gpioLines[i]);
				
				CreatePropertyFromLineEnum("LineMode", i, &SpinnakerCamera::OnLineMode);

				GENAPI::CEnumerationPtr LM = nm.GetNode("LineMode");
				GENICAM::gcstring lineMode = ""; 
				try
				{
					lineMode = LM->GetCurrentEntry()->GetSymbolic();
					LM->FromString("Output");
				}
				catch (...) {}

				CreatePropertyFromLineEnum("LineSource", i, &SpinnakerCamera::OnLineSource);
				CreatePropertyFromLineBool("LineInverter", i, &SpinnakerCamera::OnLineInverter);

				try
				{
					LM->FromString(lineMode);
				}
				catch (...) {}
			}
		}
	}
	catch (SPKR::Exception &ex)
	{
		LogMessage(ex.what());
	}*/

	try
	{
		m_cam->TriggerMode.SetValue(originalTriggerMode);
	}
	catch (SPKR::Exception &ex)
	{
		SetErrorText(SPKR_ERROR, ex.what());
		return SPKR_ERROR;
	}

	this->ClearROI();

	return DEVICE_OK;
}

int SpinnakerCamera::Shutdown()
{
	try
	{
		if (m_imageBuff)
			delete[] m_imageBuff;
		m_cam = NULL;
		m_system->ReleaseInstance();
	}
	catch (SPKR::Exception ex)
	{
		SetErrorText(SPKR_ERROR, "Failed to clean up resources!");
		return SPKR_ERROR;
	}

	return DEVICE_OK;
}

void SpinnakerCamera::GetName(char * name) const
{
	CDeviceUtils::CopyLimitedString(name, m_SN.c_str());
}

int SpinnakerCamera::SnapImage()
{
	MMThreadGuard g(m_pixelLock);
	try
	{
		m_cam->BeginAcquisition();

		if (m_cam->TriggerMode.GetValue() == SPKR::TriggerMode_On &&
			m_cam->TriggerSource.GetValue() == SPKR::TriggerSource_Software)
		{
			m_cam->TriggerSoftware.Execute();
		}

		m_imagePtr = m_cam->GetNextImage((int)this->GetExposure() + 1000);
	}
	catch (SPKR::Exception &ex)
	{
		SetErrorText(SPKR_ERROR, ex.what());
		return SPKR_ERROR;
	}

	return DEVICE_OK;
}


void SpinnakerCamera::CreatePropertyFromFloat(const std::string& name, GENAPI::IFloat & camProp, int(SpinnakerCamera::* fpt)(MM::PropertyBase *pProp, MM::ActionType eAct))
{
	auto accessMode = camProp.GetAccessMode();
	if (accessMode == GENAPI::EAccessMode::RO || accessMode == GENAPI::EAccessMode::RW || accessMode == GENAPI::EAccessMode::NA)
	{
		try
		{
			auto pAct = new CPropertyAction(this, fpt);
			bool readOnly = accessMode == GENAPI::EAccessMode::RO || accessMode == GENAPI::EAccessMode::NA;
			if (accessMode != GENAPI::EAccessMode::NA)
				CreateProperty(name.c_str(), camProp.ToString().c_str(), MM::Float, readOnly, pAct);
			else
				CreateProperty(name.c_str(), "0", MM::Float, readOnly, pAct);
		}
		catch (SPKR::Exception &ex)
		{
			LogMessage(ex.what());
		}
	}
	else
	{
		LogMessage(name + " property not created: Property not accessable\nAccess Mode: " + EAccessName(accessMode));
	}
}

void SpinnakerCamera::CreatePropertyFromBool(const std::string& name, GENAPI::IBoolean & camProp, int(SpinnakerCamera::* fpt)(MM::PropertyBase *pProp, MM::ActionType eAct))
{
	auto accessMode = camProp.GetAccessMode();
	
	if (accessMode == GENAPI::EAccessMode::RO || accessMode == GENAPI::EAccessMode::RW || accessMode == GENAPI::EAccessMode::NA)
	{
		try
		{
			bool readOnly = accessMode == GENAPI::EAccessMode::RO || accessMode == GENAPI::EAccessMode::NA;
			auto pAct = new CPropertyAction(this, fpt);

			if (accessMode != GENAPI::EAccessMode::NA)
				CreateProperty(name.c_str(), camProp.ToString().c_str(), MM::Integer, readOnly, pAct);
			else
				CreateProperty(name.c_str(), "0", MM::Integer, readOnly, pAct);

			AddAllowedValue(name.c_str(), "0");
			AddAllowedValue(name.c_str(), "1");
		}
		catch (SPKR::Exception &ex)
		{
			LogMessage(ex.what());
		}
	}
	else
	{
		LogMessage(name + " property not created: Property not accessable\nAccess Mode:" + EAccessName(accessMode));
	}
}

void SpinnakerCamera::CreatePropertyFromLineEnum(const std::string& nodeName, int lineNumber, int(SpinnakerCamera::* fpt)(MM::PropertyBase *pProp, MM::ActionType eAct, long data))
{
	GENAPI::CEnumerationPtr ePtr = m_cam->GetNodeMap().GetNode(nodeName.c_str());
	if (isNodeAvailable<NAM_READ>(ePtr))
	{
		GENAPI::StringList_t symbolics;
		ePtr->GetSymbolics(symbolics);
		auto pActEx = new CPropertyActionEx(this, fpt, lineNumber);

		GENICAM::gcstring name = m_gpioLines[lineNumber] + nodeName.c_str();
		CreateProperty(name.c_str(), ePtr->GetCurrentEntry()->ToString().c_str(),
			MM::String, symbolics.size() == 1, pActEx);

		for (int j = 0; j < symbolics.size(); j++)
			AddAllowedValue(name.c_str(), symbolics[j]);
	}
}

void SpinnakerCamera::CreatePropertyFromLineBool(const std::string& nodeName, int lineNumber, int(SpinnakerCamera::* fpt)(MM::PropertyBase *pProp, MM::ActionType eAct, long data))
{
	GENAPI::CBooleanPtr bPtr = m_cam->GetNodeMap().GetNode(nodeName.c_str());
	if (isNodeAvailable<NAM_READ>(bPtr))
	{
		GENICAM::gcstring name = m_gpioLines[lineNumber] + nodeName.c_str();
		auto ss = std::stringstream();
		ss << (int) bPtr->GetValue();

		auto pActEx = new CPropertyActionEx(this, fpt, lineNumber);
		CreateProperty(name.c_str(), ss.str().c_str(), MM::Integer, false, pActEx);
		
		AddAllowedValue(name.c_str(), "0");
		AddAllowedValue(name.c_str(), "1");
	}
}

int SpinnakerCamera::OnFloatPropertyChanged(GENAPI::IFloat & camProp, MM::PropertyBase * pProp, MM::ActionType eAct)
{
	if (camProp.GetAccessMode() == GENAPI::EAccessMode::NA)
		return DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		try
		{
			auto mmProp = dynamic_cast<MM::Property*>(pProp);
			if (mmProp != nullptr) mmProp->SetReadOnly(camProp.GetAccessMode() != GENAPI::EAccessMode::RW);
			pProp->Set(camProp.GetValue());
		}
		catch (SPKR::Exception &ex)
		{
			SetErrorText(SPKR_ERROR, ("Could not read " + pProp->GetName() + "! " + std::string(ex.what())).c_str());
			return SPKR_ERROR;
		}
	}
	else if (eAct == MM::AfterSet)
	{
		try
		{
			double val;
			pProp->Get(val);

			camProp.SetValue(val);
		}
		catch (SPKR::Exception &ex)
		{
			SetErrorText(SPKR_ERROR, ("Could not write " + pProp->GetName() + "! " + std::string(ex.what())).c_str());
			return SPKR_ERROR;
		}
	}
	return DEVICE_OK;
}

int SpinnakerCamera::OnBoolPropertyChanged(GENAPI::IBoolean & camProp, MM::PropertyBase * pProp, MM::ActionType eAct)
{
	if (camProp.GetAccessMode() == GENAPI::EAccessMode::NA)
		return DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		try
		{

			auto mmProp = dynamic_cast<MM::Property*>(pProp);
			if (mmProp != nullptr) mmProp->SetReadOnly(camProp.GetAccessMode() != GENAPI::EAccessMode::RW);

			pProp->Set((long)camProp.GetValue());
		}
		catch (SPKR::Exception &ex)
		{
			SetErrorText(SPKR_ERROR, ("Could not read " + pProp->GetName() + "! " + std::string(ex.what())).c_str());
			return SPKR_ERROR;
		}
	}
	else if (eAct == MM::AfterSet)
	{
		try
		{
			long val;
			pProp->Get(val);

			camProp.SetValue( val != 0);
		}
		catch (SPKR::Exception &ex)
		{
			SetErrorText(SPKR_ERROR, ("Could not write " + pProp->GetName() + "! " + std::string(ex.what())).c_str());
			return SPKR_ERROR;
		}
	}
	return DEVICE_OK;
}


int SpinnakerCamera::OnLineEnumPropertyChanged(std::string name, MM::PropertyBase * pProp, MM::ActionType eAct, long lineNum)
{
	GENAPI::CEnumerationPtr LS;
	GENAPI::CEnumerationPtr ePtr;
	try
	{
		LS = m_cam->GetNodeMap().GetNode("LineSelector");
		ePtr = m_cam->GetNodeMap().GetNode(name.c_str());
		
		if (isNodeAvailable<NAM_WRITE>(LS))
			LS->FromString(m_gpioLines[lineNum]);
	} 
	catch (SPKR::Exception &ex)
	{
		SetErrorText(SPKR_ERROR, ex.what());
		return SPKR_ERROR;
	}

	if (eAct == MM::BeforeGet)
	{
		try
		{
			if (isNodeAvailable<NAM_WRITE>(ePtr))
			{
				pProp->Set(ePtr->GetCurrentEntry()->GetSymbolic().c_str());
			}
			else
			{
				//SetErrorText(SPKR_ERROR, (name + " is not readable!").c_str());
				//return SPKR_ERROR;
				pProp->Set("");
			}
		}
		catch (SPKR::Exception &ex)
		{
			SetErrorText(SPKR_ERROR, ex.what());
			return SPKR_ERROR;
		}
	}
	else if(eAct == MM::AfterSet)
	{
		try
		{
			std::string val;
			pProp->Get(val);

			if (isNodeAvailable<NAM_WRITE>(ePtr))
			{
				ePtr->FromString(val.c_str());
			}
			else
			{
				SetErrorText(SPKR_ERROR, (name + " is not writable!").c_str());
				return SPKR_ERROR;
			}
		}
		catch (SPKR::Exception &ex)
		{
			SetErrorText(SPKR_ERROR, ex.what());
			return SPKR_ERROR;
		}
	}
	
	return DEVICE_OK;
}

int SpinnakerCamera::OnLineBoolPropertyChanged(std::string name, MM::PropertyBase * pProp, MM::ActionType eAct, long lineNum)
{
	GENAPI::CEnumerationPtr LS;
	GENAPI::CBooleanPtr bPtr;
	try
	{
		LS = m_cam->GetNodeMap().GetNode("LineSelector");
		bPtr = m_cam->GetNodeMap().GetNode(name.c_str());
		
		if (isNodeAvailable<NAM_WRITE>(LS))
			LS->FromString(m_gpioLines[lineNum]);
	} 
	catch (SPKR::Exception &ex)
	{
		SetErrorText(SPKR_ERROR, ex.what());
		return SPKR_ERROR;
	}

	if (eAct == MM::BeforeGet)
	{
		try
		{
			if (isNodeAvailable<NAM_WRITE>(bPtr))
			{
				pProp->Set((long)bPtr->GetValue());
			}
			else
			{
				SetErrorText(SPKR_ERROR, (name + " is not readable!").c_str());
				return SPKR_ERROR;
			}
		}
		catch (SPKR::Exception &ex)
		{
			SetErrorText(SPKR_ERROR, ex.what());
			return SPKR_ERROR;
		}
	}
	else if(eAct == MM::AfterSet)
	{
		try
		{
			long val;
			pProp->Get(val);

			if (isNodeAvailable<NAM_WRITE>(bPtr))
			{
				bPtr->SetValue(val != 0);
			}
			else
			{
				SetErrorText(SPKR_ERROR, (name + " is not writable!").c_str());
				return SPKR_ERROR;
			}
		}
		catch (SPKR::Exception &ex)
		{
			SetErrorText(SPKR_ERROR, ex.what());
			return SPKR_ERROR;
		}
	}
	
	return DEVICE_OK;
}

void SpinnakerCamera::Unpack12Bit( size_t width, size_t height, bool flip)
{
	uint16_t *unpacked = new uint16_t[width*height];
	uint8_t *packed = m_imageBuff;

	int u_idx;
	int p_idx;
	for (u_idx = 0, p_idx = 0; u_idx < width*height; u_idx++)
	{
		if (u_idx % 2 == 0)
		{
			auto pt = (Unpack12Struct*)(packed + p_idx);
			if (!flip)
				unpacked[u_idx] = ((((unsigned short)pt->_1 & 0x0F) << 8) | ((unsigned short)pt->_2));
			else
				unpacked[u_idx] = ((((unsigned short)pt->_1 & 0x0F) | ((unsigned short)pt->_2) << 4));
		}
		else
		{
			auto pt = (Unpack12Struct*)(packed + p_idx);
			unpacked[u_idx] = ((((unsigned short)pt->_0) << 4) | (((unsigned short)pt->_1) >> 4));
			p_idx += 3;
		}
	}

	delete[] packed;
	m_imageBuff = (unsigned char*)unpacked;
}

const unsigned char * SpinnakerCamera::GetImageBuffer()
{
	MMThreadGuard g(m_pixelLock);
	try
	{
		if (!m_imagePtr->IsIncomplete())
		{
			if (m_imageBuff)
			{
				delete[] m_imageBuff;
				m_imageBuff = NULL;
			}

			m_imageBuff = new unsigned char[m_imagePtr->GetWidth()*m_imagePtr->GetWidth()*this->GetImageBytesPerPixel()];

			if (m_imageBuff)
			{
				std::memcpy(m_imageBuff, m_imagePtr->GetData(), m_imagePtr->GetBufferSize());

				if (m_imagePtr->GetPixelFormat() == SPKR::PixelFormat_Mono12p)
					Unpack12Bit(m_imagePtr->GetWidth(), m_imagePtr->GetHeight(), false);
				else if (m_imagePtr->GetPixelFormat() == SPKR::PixelFormat_Mono12Packed)
					Unpack12Bit( m_imagePtr->GetWidth(), m_imagePtr->GetHeight(), true);
			}
			else
			{
				LogMessage("Failed to alocate memory for image buffer!");
				return NULL;
			}
		}
		else
		{
			LogMessage(SPKR::Image::GetImageStatusDescription(m_imagePtr->GetImageStatus()));
		}

		if (m_imagePtr != NULL)
			m_imagePtr->Release();
	}
	catch (SPKR::Exception &ex)
	{
		LogMessage(ex.what());
		if (m_imageBuff)
			delete[] m_imageBuff;
		return NULL;
	}

	try
	{
		m_cam->EndAcquisition();
	}
	catch (SPKR::Exception &ex)
	{
		LogMessage(ex.what());
	}
	return m_imageBuff;
}

unsigned SpinnakerCamera::GetImageWidth() const
{
	return (unsigned) m_cam->Width.GetValue();
}

unsigned SpinnakerCamera::GetImageHeight() const
{
	return (unsigned) m_cam->Height.GetValue();
}

unsigned SpinnakerCamera::GetImageBytesPerPixel() const
{
	switch (m_cam->PixelSize.GetValue())
	{
	case SPKR::PixelSize_Bpp1:
	case SPKR::PixelSize_Bpp2:
	case SPKR::PixelSize_Bpp4:
	case SPKR::PixelSize_Bpp8:
		return 1;
	case SPKR::PixelSize_Bpp10:
	case SPKR::PixelSize_Bpp12:
	case SPKR::PixelSize_Bpp14:
	case SPKR::PixelSize_Bpp16:
		return 2;
	case SPKR::PixelSize_Bpp20:
	case SPKR::PixelSize_Bpp24:
		return 3;
	case SPKR::PixelSize_Bpp30:
	case SPKR::PixelSize_Bpp32:
		return 4;
	case SPKR::PixelSize_Bpp48:
		return 6;
	case SPKR::PixelSize_Bpp64:
		return 8;
	case SPKR::PixelSize_Bpp96:
		return 12;
	}
   return 0;
}

unsigned SpinnakerCamera::GetBitDepth() const
{
	switch (m_cam->PixelSize.GetValue())
	{
	case SPKR::PixelSize_Bpp1:
		return 1;
	case SPKR::PixelSize_Bpp2:
		return 2;
	case SPKR::PixelSize_Bpp4:
		return 4;
	case SPKR::PixelSize_Bpp8:
		return 8;
	case SPKR::PixelSize_Bpp10:
		return 10;
	case SPKR::PixelSize_Bpp12:
		return 12;
	case SPKR::PixelSize_Bpp14:
		return 14;
	case SPKR::PixelSize_Bpp16:
		return 16;
	case SPKR::PixelSize_Bpp20:
		return 20;
	case SPKR::PixelSize_Bpp24:
		return 24;
	case SPKR::PixelSize_Bpp30:
		return 30;
	case SPKR::PixelSize_Bpp32:
		return 32;
	case SPKR::PixelSize_Bpp48:
		return 48;
	case SPKR::PixelSize_Bpp64:
		return 64;
	case SPKR::PixelSize_Bpp96:
		return 96;
	}
   return 0;
}

long SpinnakerCamera::GetImageBufferSize() const
{
	return (long) (m_cam->Width.GetValue() * m_cam->Height.GetValue() * this->GetImageBytesPerPixel());
}

double SpinnakerCamera::GetExposure() const
{
	return m_cam->ExposureTime.GetValue() / 1000.0;
}

void SpinnakerCamera::SetExposure(double exp)
{
	try
	{
		m_cam->ExposureTime.SetValue(exp * 1000.0);
		GetCoreCallback()->OnExposureChanged(this, exp);;
	}
	catch (SPKR::Exception &ex)
	{
		LogMessage(ex.what());
	}
}

int SpinnakerCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
	try
	{
		//Force offsets to be multiples of 2
		x -= (unsigned) ( (m_cam->OffsetX.GetInc() - ((x - m_cam->OffsetX.GetMin()) % m_cam->OffsetX.GetInc())));
		y -= (unsigned) ( (m_cam->OffsetY.GetInc() - ((y - m_cam->OffsetY.GetMin()) % m_cam->OffsetY.GetInc())));

		// Force width and height to be multiple of 8
		xSize += (unsigned) ((m_cam->Width.GetInc() - ((xSize - m_cam->Width.GetMin()) % m_cam->Width.GetInc())));
		ySize += (unsigned) ((m_cam->Height.GetInc() - ((ySize - m_cam->Height.GetMin()) % m_cam->Height.GetInc())));

		xSize = (unsigned) (min(xSize, m_cam->Width.GetMax()));
		ySize = (unsigned) (min(ySize, m_cam->Height.GetMax()));

		m_cam->Width.SetValue(xSize);
		m_cam->Height.SetValue(ySize);
		m_cam->OffsetX.SetValue(x); 
		m_cam->OffsetY.SetValue(y);
	}
	catch (SPKR::Exception &ex)
	{
		this->ClearROI();
		SetErrorText(SPKR_ERROR, ("Could not set ROI! " + std::string(ex.what())).c_str());
		return SPKR_ERROR;
	}
	return DEVICE_OK;
}

int SpinnakerCamera::GetROI(unsigned & x, unsigned & y, unsigned & xSize, unsigned & ySize)
{
	x = (unsigned) m_cam->OffsetX.GetValue();
	y = (unsigned) m_cam->OffsetY.GetValue();
	xSize = (unsigned) m_cam->Width.GetValue();
	ySize = (unsigned) m_cam->Height.GetValue();
	return DEVICE_OK;
}

int SpinnakerCamera::ClearROI()
{
	try
	{
		m_cam->OffsetX.SetValue(0);
		m_cam->OffsetY.SetValue(0);
		m_cam->Width.SetValue(m_cam->Width.GetMax());
		m_cam->Height.SetValue(m_cam->Height.GetMax());
	}
	catch (SPKR::Exception &ex)
	{
		SetErrorText(SPKR_ERROR, ("Could not Clear ROI! " + std::string(ex.what())).c_str());
		return SPKR_ERROR;
	}
	return DEVICE_OK;
}

int SpinnakerCamera::GetBinning() const
{
	char buf[MM::MaxStrLength];
	int ret = GetProperty(MM::g_Keyword_Binning, buf);
	if (ret != DEVICE_OK)
		return 1;
	return DEVICE_OK;
}

int SpinnakerCamera::SetBinning(int /*binSize*/) //I don't think I actually use this function...
{
	/*try
	{
		m_cam->BinningHorizontal.SetValue(binSize);
	}
	catch (SPKR::Exception &ex)
	{
		LogMessage(ex.what());
		return DEVICE_ERR; //TODO
	}*/
	return SetProperty(MM::g_Keyword_Binning, "No Binning");
}

int SpinnakerCamera::OnPixelFormat(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->PixelFormat, pProp, eAct);
}

int SpinnakerCamera::OnTestPattern(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->TestPattern, pProp, eAct);
}

int SpinnakerCamera::OnFrameRateEnabled(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	//return OnBoolPropertyChanged(m_cam->AcquisitionFrameRateEnable, pProp, eAct);
	GENAPI::CBooleanPtr AFRCE = m_cam->GetNodeMap().GetNode("AcquisitionFrameRateEnabled");

	if (eAct == MM::BeforeGet)
	{
		if (isNodeAvailable<NAM_READ>(AFRCE))
		{
			pProp->Set(AFRCE->GetValue() ? "1" : "0");
		}
		else
		{
			SetErrorText(SPKR_ERROR, "Could not read acquisition frame rate control enabled");
			return SPKR_ERROR;
		}
	}
	else if (eAct == MM::AfterSet)
	{
		if (isNodeAvailable<NAM_WRITE>(AFRCE))
		{
			long value;
			pProp->Get(value);

			try
			{
				AFRCE->SetValue(value != 0);
			}
			catch (SPKR::Exception &ex)
			{
				SetErrorText(SPKR_ERROR, ("Could not set acquisition frame rate control enabled! " + std::string(ex.what())).c_str());
				return SPKR_ERROR;
			}
		}
		else
		{
			SetErrorText(SPKR_ERROR, "Could not set frame rate control enabled");
			return SPKR_ERROR;
		}
	}

	return DEVICE_OK;
}

int SpinnakerCamera::OnFrameRateAuto(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	GENAPI::CEnumerationPtr AFRA = m_cam->GetNodeMap().GetNode("AcquisitionFrameRateAuto");

	if (eAct == MM::BeforeGet)
	{
		if (isNodeAvailable<NAM_READ>(AFRA))
		{
			pProp->Set(AFRA->GetCurrentEntry()->GetSymbolic().c_str());
		}
		else
		{
			SetErrorText(SPKR_ERROR, "Could not read auto frame rate");
			return SPKR_ERROR;
		}
	}
	else if (eAct == MM::AfterSet)
	{
		if (isNodeAvailable<NAM_WRITE>(AFRA))
		{
			std::string value;
			pProp->Get(value);

			try
			{
				AFRA->FromString(value.c_str());
			}
			catch (SPKR::Exception &ex)
			{
				SetErrorText(SPKR_ERROR, ("Could not set auto frame rate! " + std::string(ex.what())).c_str());
				return SPKR_ERROR;
			}
		}
		else
		{
			SetErrorText(SPKR_ERROR, "Could not set auto frame rate");
			return SPKR_ERROR;
		}
	}

	return DEVICE_OK;
}

int SpinnakerCamera::OnExposureAuto(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->ExposureAuto, pProp, eAct);
}

int SpinnakerCamera::OnFrameRate(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnFloatPropertyChanged(m_cam->AcquisitionFrameRate, pProp, eAct);
}

int SpinnakerCamera::OnBinningEnum(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	GENAPI::CEnumerationPtr VM = m_cam->GetNodeMap().GetNode("VideoMode");
	if (eAct == MM::BeforeGet)
	{
		if (isNodeAvailable<NAM_READ>(VM))
		{
			pProp->Set(VM->GetCurrentEntry()->GetSymbolic());
		}
		else
		{
			SetErrorText(SPKR_ERROR, "Could not read video mode!");
			return SPKR_ERROR;
		}
	}
	else if (eAct == MM::AfterSet)
	{
		if (isNodeAvailable<NAM_WRITE>(VM))
		{
			std::string val;
			pProp->Get(val);

			try
			{
				VM->FromString(GENICAM::gcstring(val.c_str()));
			}
			catch (SPKR::Exception &ex)
			{
				SetErrorText(SPKR_ERROR, ex.what());
				return SPKR_ERROR;
			}
		}
		else
		{
			SetErrorText(SPKR_ERROR, "Could not write video mode");
			return SPKR_ERROR;
		}
	}
	return DEVICE_OK;
}

int SpinnakerCamera::OnBinningInt(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	GENAPI::CIntegerPtr BH = m_cam->GetNodeMap().GetNode("BinningHorizontal");
	GENAPI::CIntegerPtr BV = m_cam->GetNodeMap().GetNode("BinningVertical");
	if (eAct == MM::BeforeGet)
	{
		if (GENAPI::IsAvailable(BH) && GENAPI::IsReadable(BH) && GENAPI::IsAvailable(BV) && GENAPI::IsReadable(BV))
		{
			std::stringstream ss;
			ss << BH->GetValue();
			pProp->Set(ss.str().c_str());
		}
		else
		{
			SetErrorText(SPKR_ERROR, "Could not read horizontal binning");
			return SPKR_ERROR;
		}
	}
	else if (eAct == MM::AfterSet)
	{
		std::string val;
		pProp->Get(val);

		try
		{
			BH->SetValue(std::stoi(val));
			BV->SetValue(std::stoi(val));
			m_cam->Width.SetValue(m_cam->WidthMax.GetValue());
			m_cam->Height.SetValue(m_cam->HeightMax.GetValue());
		}
		catch (SPKR::Exception &ex)
		{
			SetErrorText(SPKR_ERROR, ex.what());
			return SPKR_ERROR;
		}
	}

	return DEVICE_OK;
}

int SpinnakerCamera::OnBinningModeEnum(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	GENAPI::CEnumerationPtr BC = m_cam->GetNodeMap().GetNode("BinningControl");
	if (eAct == MM::BeforeGet)
	{
		if (isNodeAvailable<NAM_READ>(BC))
		{
			pProp->Set(BC->GetCurrentEntry()->GetSymbolic());
		}
		else
		{
			//SetErrorText(SPKR_ERROR, "Could not read binning mode");
			//return SPKR_ERROR;
			pProp->Set("");
		}
	}
	else if (eAct == MM::AfterSet)
	{
		std::string val;
		pProp->Get(val);
		if (isNodeAvailable<NAM_WRITE>(BC))
		{
			BC->FromString(val.c_str());
		}
		else
		{
			SetErrorText(SPKR_ERROR, "Could not write binning mode!");
			return SPKR_ERROR;
		}
	}
	return DEVICE_OK;
}

int SpinnakerCamera::OnGain(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnFloatPropertyChanged(m_cam->Gain, pProp, eAct);
}

int SpinnakerCamera::OnGainAuto(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->GainAuto, pProp, eAct);
}

int SpinnakerCamera::OnGamma(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnFloatPropertyChanged(m_cam->Gamma, pProp, eAct);
}

int SpinnakerCamera::OnGammaEnabled(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnBoolPropertyChanged(m_cam->GammaEnable, pProp, eAct);
}

int SpinnakerCamera::OnBlackLevel(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnFloatPropertyChanged(m_cam->BlackLevel, pProp, eAct);
}

int SpinnakerCamera::OnBlackLevelAuto(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->BlackLevelAuto, pProp, eAct);
}

int SpinnakerCamera::OnTriggerSelector(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->TriggerSelector, pProp, eAct);
}

int SpinnakerCamera::OnTriggerMode(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->TriggerMode, pProp, eAct);
}

int SpinnakerCamera::OnTriggerSource(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->TriggerSource, pProp, eAct);
}

int SpinnakerCamera::OnTriggerActivation(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->TriggerActivation, pProp, eAct);
}

int SpinnakerCamera::OnTriggerOverlap(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->TriggerOverlap, pProp, eAct);
}

int SpinnakerCamera::OnTriggerDelay(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnFloatPropertyChanged(m_cam->TriggerDelay, pProp, eAct);
}

int SpinnakerCamera::OnExposureMode(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->ExposureMode, pProp, eAct);
}

/*int SpinnakerCamera::OnLineMode(MM::PropertyBase * pProp, MM::ActionType eAct, long lineNum)
{
	return OnLineEnumPropertyChanged("LineMode", pProp, eAct, lineNum);
}

int SpinnakerCamera::OnLineInverter(MM::PropertyBase * pProp, MM::ActionType eAct, long lineNum)
{
	return OnLineBoolPropertyChanged("LineInverter", pProp, eAct, lineNum);
}

int SpinnakerCamera::OnLineSource(MM::PropertyBase * pProp, MM::ActionType eAct, long lineNum)
{
	return OnLineEnumPropertyChanged("LineSource", pProp, eAct, lineNum);
}*/

int SpinnakerCamera::OnUserOutputSelector(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->UserOutputSelector, pProp, eAct);
}

int SpinnakerCamera::OnUserOutputValue(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnBoolPropertyChanged(m_cam->UserOutputValue, pProp, eAct);
}

int SpinnakerCamera::OnLineSelector(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->LineSelector, pProp, eAct);
}

int SpinnakerCamera::OnLineMode(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->LineMode, pProp, eAct);
}

int SpinnakerCamera::OnLineInverter(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnBoolPropertyChanged(m_cam->LineInverter, pProp, eAct);
}

int SpinnakerCamera::OnLineSource(MM::PropertyBase * pProp, MM::ActionType eAct)
{
	return OnEnumPropertyChanged(m_cam->LineSource, pProp, eAct);
}


int SpinnakerCamera::PrepareSequenceAcqusition()
{
	return DEVICE_OK;
}

int SpinnakerCamera::StartSequenceAcquisition(double interval)
{
	if (!m_aqThread->IsStopped())
		return DEVICE_CAMERA_BUSY_ACQUIRING;

	m_stopOnOverflow = false;
		m_aqThread->Start(-1, interval);
	return DEVICE_OK;
}

int SpinnakerCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
	if (!m_aqThread->IsStopped())
		return DEVICE_CAMERA_BUSY_ACQUIRING;

	int ret = GetCoreCallback()->PrepareForAcq(this);
	if (ret != DEVICE_OK)
		return ret;

	m_stopOnOverflow = stopOnOverflow;
	m_aqTriggerMode = m_cam->TriggerMode.GetValue();
	m_aqTriggerSource = m_cam->TriggerSource.GetValue();
	m_aqThread->Start(numImages, interval_ms);
	return DEVICE_OK;
}

int SpinnakerCamera::StopSequenceAcquisition()
{
	if (!m_aqThread->IsStopped())
	{
		m_aqThread->Stop();
		m_aqThread->wait();
	}
	return DEVICE_OK;
}

bool SpinnakerCamera::IsCapturing()
{
	return !m_aqThread->IsStopped();
}

int SpinnakerCamera::MoveImageToCircularBuffer()
{
	if (!IsCapturing())
	{
		SetErrorText(SPKR_ERROR, "Camera is not capturing! Cannot retrieve image!");
		return SPKR_ERROR;
	}

	try
	{
		if (m_aqTriggerMode == SPKR::TriggerMode_On &&
			m_aqTriggerSource == SPKR::TriggerSource_Software)
		{
			m_cam->TriggerSoftware.Execute();
		}

		SPKR::ImagePtr ip =
			m_cam->GetNextImage((int)this->GetExposure() + 1000);

		if (!ip->IsIncomplete())
		{
			MM::MMTime timeStamp = this->GetCurrentMMTime();
			char label[MM::MaxStrLength];
			this->GetLabel(label);

			Metadata md;
			md.put("Camera", label);
			md.put(MM::g_Keyword_Metadata_StartTime, CDeviceUtils::ConvertToString(m_aqThread->GetStartTime().getMsec()));
			md.put(MM::g_Keyword_Elapsed_Time_ms, CDeviceUtils::ConvertToString((timeStamp - m_aqThread->GetStartTime()).getMsec()));
			md.put(MM::g_Keyword_Metadata_ROI_X, CDeviceUtils::ConvertToString((long)m_cam->Width.GetValue()));
			md.put(MM::g_Keyword_Metadata_ROI_Y, CDeviceUtils::ConvertToString((long)m_cam->Height.GetValue()));

			char buf[MM::MaxStrLength];
			GetProperty(MM::g_Keyword_Binning, buf);
			md.put(MM::g_Keyword_Binning, buf);

			MMThreadGuard g(m_pixelLock);

			if (m_imageBuff)
			{
				delete[] m_imageBuff;
				m_imageBuff = NULL;
			}

			m_imageBuff = new unsigned char[ip->GetWidth()*ip->GetWidth()*this->GetImageBytesPerPixel()];

			if (m_imageBuff)
			{
				std::memcpy(m_imageBuff, ip->GetData(), ip->GetBufferSize());
				if (ip->GetPixelFormat() == SPKR::PixelFormat_Mono12p)
					Unpack12Bit(ip->GetWidth(), ip->GetHeight(), false);
				else if (ip->GetPixelFormat() == SPKR::PixelFormat_Mono12Packed)
					Unpack12Bit(ip->GetWidth(), ip->GetHeight(), true);
			}
			else
			{
				SetErrorText(SPKR_ERROR, "Could not allocate sufficient memory for image");
				return SPKR_ERROR;
			}

			unsigned int w = GetImageWidth();
			unsigned int h = GetImageHeight();
			unsigned int b = GetImageBytesPerPixel();

			int ret = GetCoreCallback()->InsertImage(this, m_imageBuff, w, h, b, md.Serialize().c_str());
			if (!m_stopOnOverflow && ret == DEVICE_BUFFER_OVERFLOW)
			{
				// do not stop on overflow - just reset the buffer
				GetCoreCallback()->ClearImageBuffer(this);
				// don't process this same image again...
				return GetCoreCallback()->InsertImage(this, m_imageBuff, w, h, b, md.Serialize().c_str(), false);
			}
			else
			{
				if (ip != NULL)
					ip->Release();

				return ret;
			}
		}
		else
		{
			LogMessage(SPKR::Image::GetImageStatusDescription(ip->GetImageStatus()));
		}

		if (ip != NULL)
			ip->Release();
	}
	catch (SPKR::Exception &ex)
	{
		LogMessage(ex.what());
	}

	return DEVICE_OK;
}

SpinnakerAcquisitionThread::SpinnakerAcquisitionThread(SpinnakerCamera * pCam)
	: m_numImages(-1),
	m_intervalMs(0),
	m_imageCounter(0),
	m_stop(true),
	m_suspend(false),
	m_spkrCam(pCam),
	m_startTime(0),
	m_actualDuration(0),
	m_lastFrameTime(0)
{
}

SpinnakerAcquisitionThread::~SpinnakerAcquisitionThread()
{
}

void SpinnakerAcquisitionThread::Stop()
{
	MMThreadGuard g(this->m_stopLock);
	m_stop = true;
}

void SpinnakerAcquisitionThread::Start(long numImages, double intervalMs)
{
	MMThreadGuard g1(this->m_stopLock);
	MMThreadGuard g2(this->m_suspendLock);
	m_numImages = numImages;
	m_intervalMs = intervalMs;
	m_imageCounter = 0;
	m_stop = false;
	m_suspend = false;
	activate();
	m_actualDuration = 0;
	m_startTime = m_spkrCam->GetCurrentMMTime();
	m_lastFrameTime = 0;

	if (numImages == -1)
	{
		m_spkrCam->m_cam->AcquisitionMode.SetValue(SPKR::AcquisitionMode_Continuous);
	}
	else
	{
		m_spkrCam->m_cam->AcquisitionMode.SetValue(SPKR::AcquisitionMode_MultiFrame);
		m_spkrCam->m_cam->AcquisitionFrameCount.SetValue(numImages);
	}
	m_spkrCam->m_cam->BeginAcquisition();
}

bool SpinnakerAcquisitionThread::IsStopped()
{
	MMThreadGuard g(this->m_stopLock);
	return m_stop;
}

void SpinnakerAcquisitionThread::Suspend()
{
	MMThreadGuard g(this->m_suspendLock);
	m_suspend = true;
}

bool SpinnakerAcquisitionThread::IsSuspended()
{
	MMThreadGuard g(this->m_suspendLock);
	return m_suspend;
}

void SpinnakerAcquisitionThread::Resume()
{
	MMThreadGuard g(this->m_suspendLock);
	m_suspend = false;
}

int SpinnakerAcquisitionThread::svc(void) throw()
{
	int ret=DEVICE_ERR;

	try
	{
		do
		{  

			ret = m_spkrCam->MoveImageToCircularBuffer();

			/*if (m_spkrCam->m_cam->TriggerMode.GetValue() == SPKR::TriggerMode_On &&
				m_spkrCam->m_cam->TriggerSource.GetValue() == SPKR::TriggerSource_Software)
			{
				while (m_spkrCam->GetCurrentMMTime() - m_lastFrameTime < m_intervalMs);
			}*/
		} while (DEVICE_OK == ret && !IsStopped() && (m_imageCounter++ < m_numImages || m_numImages == -1));
		if (IsStopped())
			m_spkrCam->LogMessage("SeqAcquisition interrupted by the user\n");
	} 
	catch (SPKR::Exception &ex)
	{
		m_spkrCam->LogMessage(ex.what());
	}
	catch (...)
	{
		m_spkrCam->LogMessage("Unknown error in acquisition");
	}

	m_stop=true;
	m_actualDuration = m_spkrCam->GetCurrentMMTime() - m_startTime;
	m_spkrCam->m_cam->EndAcquisition();
	m_spkrCam->m_cam->AcquisitionMode.SetValue(SPKR::AcquisitionMode_SingleFrame);
	m_spkrCam->OnThreadExiting();
	return DEVICE_OK;
}


#pragma warning(pop)
