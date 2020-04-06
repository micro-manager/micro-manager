#ifndef _SRRFCAMERA_H_
#define _SRRFCAMERA_H_

#include "../../MMDevice/Property.h"

class ISRRFCamera 
{
public:
   virtual ~ISRRFCamera(){};

   virtual int AddProperty(const char* name, const char* value, MM::PropertyType eType, bool readOnly, MM::ActionFunctor* pAct) = 0;
   virtual int CreateProperty(const char* name, const char* value, MM::PropertyType eType, bool readOnly, MM::ActionFunctor* pAct=0, bool isPreInitProperty=false) = 0;
   virtual int GetProperty(const char* name, char* value) const = 0;
   virtual int GetProperty(const char* name, long& val) = 0;
   virtual int GetProperty(const char* name, double& val) = 0;
   virtual int SetProperty(const char* name, const char* value) = 0;
   virtual int SetPropertyLimits(const char* name, double low, double high) = 0;
   virtual int SetAllowedValues(const char* name, std::vector<std::string>& values) = 0;

   virtual void Log(std::string message) = 0;
   virtual int GetMyCameraID() const = 0;
   virtual bool IsCapturing() = 0;
   virtual void ResizeSRRFImage(long radiality) = 0;
};

#endif
