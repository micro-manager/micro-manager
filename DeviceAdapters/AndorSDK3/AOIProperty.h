#ifndef _AOIPROPERTY_H_
#define _AOIPROPERTY_H_

#include "atcore++.h"

using namespace andor;

class MySequenceThread;
class CAndorSDK3Camera;

class TAOIProperty
{
public:
   TAOIProperty(const std::string MM_name, CAndorSDK3Camera* camera,
                IDevice* device_hndl, MySequenceThread* thd,
                SnapShotControl* snapShotController, bool readOnly);
   ~TAOIProperty();

   void Update(ISubject* Subject);
   int OnAOI(MM::PropertyBase* pProp, MM::ActionType eAct);
   typedef MM::Action<TAOIProperty> CPropertyAction;

   int GetWidth();
   int GetHeight();
   void SetReadOnly(bool set_to);

private:
   IInteger* aoi_height_;
   IInteger* aoi_width_;
   IInteger* aoi_top_;
   IInteger* aoi_left_;
   CAndorSDK3Camera* camera_;
   IDevice* device_hndl_;
   std::string MM_name_;
   MySequenceThread * thd_;
   SnapShotControl* snapShotController_;
   MM::Property* pbProp_;
};

#endif // _AOIPROPERTY_H_