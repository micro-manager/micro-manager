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
   virtual bool IsLiveModeActive() = 0;
   virtual int  CPCCreateProperty(const char* name, const char* value, MM::PropertyType eType, 
                                    bool readOnly, MM::ActionFunctor* pAct=0, bool initStatus=false) = 0;
   virtual int  CPCSetAllowedValues(const char* pszName, std::vector<std::string>& values) = 0;
   virtual int  CPCAddAllowedValueWithData(const char* pszName, const std::string & value, long data) = 0;
   virtual int  CPCLog(const char * msg) = 0;

   virtual void PauseLiveAcquisition() = 0;
   virtual void CPCRestartLiveAcquisition() = 0;
   virtual void CPCStopLiveAcquisition() = 0;
   virtual void CPCStartLiveAcquisition() = 0;
   virtual void CPCResizeImageBuffer() = 0;
   
   virtual int  CPCGetBinningFactor() = 0;
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
   virtual bool IsLiveModeActive();
   virtual int  CPCCreateProperty(const char* name, const char* value, MM::PropertyType eType, 
                                    bool readOnly, MM::ActionFunctor* pAct=0, bool initStatus=false);
   virtual int  CPCSetAllowedValues(const char* pszName, std::vector<std::string>& values);
   virtual int  CPCAddAllowedValueWithData(const char* pszName, const std::string & value, long data);
   virtual int  CPCLog(const char * msg);

   virtual void PauseLiveAcquisition();
   virtual void CPCRestartLiveAcquisition();
   virtual void CPCStopLiveAcquisition();
   virtual void CPCStartLiveAcquisition();
   virtual void CPCResizeImageBuffer();

   virtual int  CPCGetBinningFactor();
   virtual andor::IDevice * GetCameraDevice();


private:
   CAndorSDK3Camera * parentClass_;
   MySequenceThread * thd_;
   SnapShotControl  * ssControl_;

};

#endif	//include only once
