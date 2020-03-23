#ifndef _SRRFANDORSDK3CAMERA_H_
#define _SRRFANDORSDK3CAMERA_H_

#include "../Andor/SRRFCamera.h"

class CAndorSDK3Camera;

class SRRFAndorSDK3Camera : public ISRRFCamera
{
public:
   SRRFAndorSDK3Camera(CAndorSDK3Camera* camera);
   ~SRRFAndorSDK3Camera(){};
   
   //Inherited from ISRRFCamera
   int AddProperty(const char* name, const char* value, MM::PropertyType eType, bool readOnly, MM::ActionFunctor* pAct);
   int CreateProperty(const char* name, const char* value, MM::PropertyType eType, bool readOnly, MM::ActionFunctor* pAct=0, bool isPreInitProperty=false);
   int GetProperty(const char* name, char* value) const;
   int GetProperty(const char* name, long& val);
   int GetProperty(const char* name, double& val);
   int SetProperty(const char* name, const char* value);
   int SetPropertyLimits(const char* name, double low, double high);
   int SetAllowedValues(const char* name, std::vector<std::string>& values);

   void Log(std::string message);
   int GetMyCameraID() const;
   bool IsCapturing();
   void ResizeSRRFImage(long radiality);

private:
   CAndorSDK3Camera* camera_;
   static const int SDK3_SRRF_CAMERA_HANDLE = 30000;
};

#endif
