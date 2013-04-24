#ifndef _CallBackManagerH
#define _CallBackManagerH

#include "Property.h"

class MySequenceThread;
class CAndorSDK3Camera;
class SnapShotControl;
namespace andor {
   class IDevice;
};

class ICallBackManager
{
public:
   virtual ~ICallBackManager() {}
   virtual bool IsSSCPoised() = 0;
   virtual bool SSCEnterPoised() = 0;
   virtual bool SSCLeavePoised() = 0;
   virtual int  CPCCreateProperty(const char* name, const char* value, MM::PropertyType eType, 
                                    bool readOnly, MM::ActionFunctor* pAct=0, bool initStatus=false) = 0;
   virtual int  CPCLog(const char * msg) = 0;
   virtual andor::IDevice * GetCameraDevice() = 0;

};

class CCallBackManager : public ICallBackManager
{
public:
   CCallBackManager(CAndorSDK3Camera * _parentClass, MySequenceThread * _seqAcqThread, SnapShotControl * _snapControl);
   ~CCallBackManager();

   virtual bool IsSSCPoised();
   virtual bool SSCEnterPoised();
   virtual bool SSCLeavePoised();
   virtual int  CPCCreateProperty(const char* name, const char* value, MM::PropertyType eType, 
                                    bool readOnly, MM::ActionFunctor* pAct=0, bool initStatus=false);
   virtual int  CPCLog(const char * msg);
   virtual andor::IDevice * GetCameraDevice();


private:
   CAndorSDK3Camera * parentClass_;
   MySequenceThread * thd_;
   SnapShotControl  * ssControl_;

};

#endif	//include only once
