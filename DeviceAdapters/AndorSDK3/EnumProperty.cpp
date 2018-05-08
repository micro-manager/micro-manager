#include "EnumProperty.h"
#include "AndorSDK3.h"
#include "SnapShotControl.h"
#include <vector>


using namespace andor;
using namespace std;

TEnumProperty::TEnumProperty(const string & MM_name, IEnum * enum_feature, CAndorSDK3Camera * camera,
                             MySequenceThread * thd, SnapShotControl * snapShotController, bool readOnly,
                             bool needsCallBack)
: MM_name_(MM_name),
  enum_feature_(enum_feature),
  camera_(camera),
  thd_(thd),
  snapShotController_(snapShotController),
  callBackRegistered_(needsCallBack)
{
   if (enum_feature->IsImplemented())
   {
      CPropertyAction * pAct = new CPropertyAction (this, &TEnumProperty::OnEnum);
      camera_->CreateProperty(MM_name_.c_str(), "", MM::String, readOnly, pAct);

      try
      {
         if (needsCallBack)
         {
            enum_feature_->Attach(this);
         }
         else
         {
            Update(NULL);
         }
      }
      catch (exception & e)
      {
         // Callback not implemented for this feature
         camera_->LogMessage(e.what());
      }
   }
   else
   {
      callBackRegistered_ = false;
   }
}

TEnumProperty::~TEnumProperty()
{
   if (callBackRegistered_)
   {
      try
      {
         enum_feature_->Detach(this);
      }
      catch (exception & e)
      {
         // Callback not implemented for this feature
         camera_->LogMessage(e.what());
      }
   }
   //Clean up memory, created as passed in
   camera_->GetCameraDevice()->Release(enum_feature_);
}


inline wchar_t * convertToWString(const string & str, wchar_t * outBuf, unsigned int bufSize)
{
   wmemset(outBuf, L'\0', bufSize);
   mbstowcs(outBuf, str.c_str(), str.size());
   return outBuf;
}

inline char * convertFromWString(const wstring & wstr, char * outBuf, unsigned int bufSize)
{
   memset(outBuf, '\0', bufSize);
   wcstombs(outBuf, wstr.c_str(), wstr.size());
   return outBuf;
}

void TEnumProperty::Update(ISubject * /*Subject*/)
{
   // This property has been changed in SDK3. The new value will be set by a
   // call to TEnumProperty::OnEnum, in here reset the list of allowed values
   //  No clear required as this is always done by base impl, if call SetAllowedValues
   vector<string> allowed_values;
   char buf[MAX_CHARS_ENUM_VALUE_BUFFER];
   for (int i = 0; i < enum_feature_->Count(); i++)
   {
      if (enum_feature_->IsIndexImplemented(i))
      {
         if (enum_feature_->IsIndexAvailable(i))
         {
            wstring value_ws = enum_feature_->GetStringByIndex(i);
            allowed_values.push_back(convertFromWString(value_ws, buf, MAX_CHARS_ENUM_VALUE_BUFFER));
         }
         //else
         //{
         //   stringstream ss("Enum feature ");
         //   ss << MM_name_ << "; Index " << i << " Not Available";
         //   camera_->LogMessage(ss.str().c_str(), true);
         //}
      }
      //else
      //{
      //   stringstream ss("Enum feature ");
      //   ss << MM_name_ << "; Index " << i << " Not Implemented";
      //   camera_->LogMessage(ss.str().c_str(), true);
      //}
   }
   camera_->SetAllowedValues(MM_name_.c_str(), allowed_values);
}

// Action handler for OnEnum
int TEnumProperty::OnEnum(MM::PropertyBase * pProp, MM::ActionType eAct)
{
   if (!enum_feature_->IsImplemented())
   {
      return DEVICE_OK;
   }
   if (eAct == MM::BeforeGet)
   {
      char buf[MAX_CHARS_ENUM_VALUE_BUFFER];
      wstring temp_ws = enum_feature_->GetStringByIndex(enum_feature_->GetIndex());
      pProp->Set(convertFromWString(temp_ws, buf, MAX_CHARS_ENUM_VALUE_BUFFER));
   }
   else if (eAct == MM::AfterSet)
   {
      if (!thd_->IsStopped())
      {
         camera_->StopSequenceAcquisition();
      }

      bool was_poised = false;
      if (snapShotController_->isPoised())
      {
         snapShotController_->leavePoisedMode();
         was_poised = true;
      }

      if (enum_feature_->IsWritable())
      {
         wchar_t buf[MAX_CHARS_ENUM_VALUE_BUFFER];
         string temp_s;
		 pProp->Get(temp_s);

		 if (temp_s.find("(not available from sequential)") == std::string::npos)
		 {
			 enum_feature_->Set(convertToWString(temp_s, buf, MAX_CHARS_ENUM_VALUE_BUFFER));
		 }
      }
      camera_->UpdateProperty(MM_name_.c_str());

	  if(MM_name_.compare("LightScanPlus-SensorReadoutMode") == 0)
	  {
		  vector<string> allowed_values;
		  char buf[MAX_CHARS_ENUM_VALUE_BUFFER];
		  for (int i = 0; i < enum_feature_->Count(); i++)
		  {
			  if (enum_feature_->IsIndexImplemented(i))
			  {
				  if (enum_feature_->IsIndexAvailable(i))
				  {
					  wstring value_ws = enum_feature_->GetStringByIndex(i);
					  allowed_values.push_back(convertFromWString(value_ws, buf, MAX_CHARS_ENUM_VALUE_BUFFER));
				  }
				  else
				  {
					  wstring value_ws = enum_feature_->GetStringByIndex(i) + L" (not available from sequential)";
					  allowed_values.push_back(convertFromWString(value_ws, buf, MAX_CHARS_ENUM_VALUE_BUFFER));
				  }
			  }

		  }
		  camera_->SetAllowedValues(MM_name_.c_str(), allowed_values);
	  }

      if (0 == MM_name_.compare(MM::g_Keyword_Binning))
      {
         camera_->ResizeImageBuffer();
      }

      if (was_poised)
      {
         snapShotController_->poiseForSnapShot();
      }
   }

   return DEVICE_OK;
}
