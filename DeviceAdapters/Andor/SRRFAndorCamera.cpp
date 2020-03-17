#include "SRRFAndorCamera.h"

#ifdef WIN32
#include "atmcd32d.h"
#else
#include "atmcdLXd.h"
#endif

#include "Andor.h"


SRRFAndorCamera::SRRFAndorCamera(AndorCamera* camera)
   : camera_(camera)
{
   if (!camera_) 
   {
      throw std::invalid_argument("invalid Andor camera pointer");
   }
}

int SRRFAndorCamera::AddProperty(const char* name, const char* value, MM::PropertyType eType, bool readOnly, MM::ActionFunctor* pAct)
{
   return camera_->AddProperty(name, value, eType, readOnly, pAct);
}

int SRRFAndorCamera::CreateProperty(const char* name, const char* value, MM::PropertyType eType, bool readOnly, MM::ActionFunctor* pAct, bool isPreInitProperty)
{
   return camera_->CreateProperty(name, value, eType, readOnly, pAct, isPreInitProperty);
}

int SRRFAndorCamera::GetProperty(const char* name, char* value) const
{
   return camera_->GetProperty(name, value);
}

int SRRFAndorCamera::GetProperty(const char* name, long& val)
{
   return camera_->GetProperty(name, val);
}

int SRRFAndorCamera::GetProperty(const char* name, double& val)
{
   return camera_->GetProperty(name, val);
}

int SRRFAndorCamera::SetProperty(const char* name, const char* value)
{
   return camera_->SetProperty(name, value);
}

int SRRFAndorCamera::SetPropertyLimits(const char* name, double low, double high)
{
   return camera_->SetPropertyLimits(name, low, high);
}

int SRRFAndorCamera::SetAllowedValues(const char* name, std::vector<std::string>& values)
{
   return camera_->SetAllowedValues(name, values);
}

void SRRFAndorCamera::Log(std::string message)
{
   camera_->Log(message);
}

int SRRFAndorCamera::GetMyCameraID() const
{
   return camera_->GetMyCameraID();
}

bool SRRFAndorCamera::IsCapturing()
{
   return camera_->IsCapturing();
}

void SRRFAndorCamera::ResizeSRRFImage(long radiality)
{
   camera_->ResizeSRRFImage(radiality);
}
