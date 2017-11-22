#include "ReadModeControl.h"
#ifdef WIN32
#include <windows.h>
#include "atmcd32d.h"
#else
#include "atmcdLXd.h"
#endif
#include "Andor.h"

using namespace std;

const char* g_ReadMode      = "ReadMode";
const char* g_ReadModeFVB   = "FVB";
const char* g_ReadModeImage = "Image";

ReadModeControl::ReadModeControl(AndorCamera * cam) :
camera_(cam),
currentMode_(IMAGE)
{
	InitialiseMaps();
	CreateProperty();
}

void ReadModeControl::CreateProperty() 
{
	DriverGuard dg(camera_);
	AndorCapabilities caps;
	caps.ulSize = sizeof(AndorCapabilities);
	GetCapabilities(&caps);

	if (caps.ulReadModes & AC_READMODE_FVB)
	{
		CPropertyAction* pAct = new CPropertyAction(this, &ReadModeControl::OnReadMode);
		camera_->AddProperty(g_ReadMode, g_ReadModeFVB, MM::String, false, pAct);

		vector<string> CCValues;
		CCValues.push_back(g_ReadModeFVB);
		CCValues.push_back(g_ReadModeImage);
		camera_->SetAllowedValues(g_ReadMode, CCValues);
	}
	
}

int ReadModeControl::OnReadMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		string readModeStr;
		pProp->Get(readModeStr);
		MODE newMode = GetMode(readModeStr.c_str());
		if (newMode == currentMode_)
			return DEVICE_OK;

		camera_->PrepareToApplySetting();
		{
			DriverGuard dg(camera_);
			unsigned int ret = SetReadMode(static_cast<long>(newMode));
			if (ret != DRV_SUCCESS)
				return DEVICE_CAN_NOT_SET_PROPERTY;
		}
		

		currentMode_ = newMode;
      
      camera_->SetProperty(MM::g_Keyword_Binning, "1");
      camera_->PopulateBinningDropdown();
      camera_->SetProperty("Trigger", "Internal");
      camera_->PopulateTriggerDropdown();

		if (currentMode_ == IMAGE)
		{
			camera_->PopulateROIDropdown();
		}
      else if(currentMode_ == FVB)
      {
         camera_->PopulateROIDropdownFVB();
      }
		camera_->ClearROI();
      camera_->ResumeAfterApplySetting();
	}
	else if (eAct == MM::BeforeGet)
	{
		pProp->Set(modes_[currentMode_]);
	}
	return DEVICE_OK;
}

void ReadModeControl::InitialiseMaps()
{
   modes_.clear();
   modes_[FVB]    = g_ReadModeFVB;
   modes_[IMAGE]  = g_ReadModeImage;
}

ReadModeControl::MODE ReadModeControl::GetMode(const char * mode)
{
   MODEMAP::const_iterator it;
   std::string strMode = mode;
   for (it = modes_.begin(); it != modes_.end(); ++it)
      if (0 == strMode.compare(it->second))
         return it->first;

   return FVB;
}

int ReadModeControl::getCurrentMode() 
{
	return static_cast<int>(currentMode_);
}
