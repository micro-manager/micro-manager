#ifndef _SRRFANDORCAMERA_H_
#define _SRRFANDORCAMERA_H_

#include "SRRFCamera.h"

class AndorCamera;

class SRRFAndorCamera : public ISRRFCamera
{
public:
   SRRFAndorCamera(AndorCamera* camera);
   ~SRRFAndorCamera(){};
   
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
   AndorCamera* camera_;
};

#endif