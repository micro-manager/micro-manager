#include "SRRFAndorSDK3Camera.h"
#include "atcore++.h"
#include "AndorSDK3.h"


SRRFAndorSDK3Camera::SRRFAndorSDK3Camera(CAndorSDK3Camera* camera)
   : camera_(camera)
{
   if (!camera_) 
   {
      throw std::invalid_argument("invalid Andor SDK3 camera pointer");
   }
}

int SRRFAndorSDK3Camera::AddProperty(const char* name, const char* value, MM::PropertyType eType, bool readOnly, MM::ActionFunctor* pAct)
{
   return camera_->AddProperty(name, value, eType, readOnly, pAct);
}

int SRRFAndorSDK3Camera::CreateProperty(const char* name, const char* value, MM::PropertyType eType, bool readOnly, MM::ActionFunctor* pAct, bool isPreInitProperty)
{
   return camera_->CreateProperty(name, value, eType, readOnly, pAct, isPreInitProperty);
}

int SRRFAndorSDK3Camera::GetProperty(const char* name, char* value) const
{
   return camera_->GetProperty(name, value);
}

int SRRFAndorSDK3Camera::GetProperty(const char* name, long& val)
{
   return camera_->GetProperty(name, val);
}

int SRRFAndorSDK3Camera::GetProperty(const char* name, double& val)
{
   return camera_->GetProperty(name, val);
}

int SRRFAndorSDK3Camera::SetProperty(const char* name, const char* value)
{
   return camera_->SetProperty(name, value);
}

int SRRFAndorSDK3Camera::SetPropertyLimits(const char* name, double low, double high)
{
   return camera_->SetPropertyLimits(name, low, high);
}

int SRRFAndorSDK3Camera::SetAllowedValues(const char* name, std::vector<std::string>& values)
{
   return camera_->SetAllowedValues(name, values);
}

void SRRFAndorSDK3Camera::Log(std::string message)
{
   camera_->LogMessage(message);
}

int SRRFAndorSDK3Camera::GetMyCameraID() const
{
   return camera_->GetCameraDevice()->GetHandle() + SDK3_SRRF_CAMERA_HANDLE;
}

bool SRRFAndorSDK3Camera::IsCapturing()
{
   return camera_->IsCapturing();
}

void SRRFAndorSDK3Camera::ResizeSRRFImage(long radiality)
{
   camera_->ResizeSRRFImage(radiality);
}
