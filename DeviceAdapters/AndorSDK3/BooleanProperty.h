#ifndef _BOOLEANPROPERTY_H_
#define _BOOLEANPROPERTY_H_

#include "atcore++.h"

using namespace andor;

class MySequenceThread;
class CAndorSDK3Camera;

class TBooleanProperty
{
public:
   TBooleanProperty(const std::string MM_name, IBool* bool_feature,
                    CAndorSDK3Camera* camera, MySequenceThread* thd,
                    SnapShotControl* snapShotController, bool readOnly);
   ~TBooleanProperty();

   void Update(ISubject* Subject);
   int OnBoolean(MM::PropertyBase* pProp, MM::ActionType eAct);
   typedef MM::Action<TBooleanProperty> CPropertyAction;

private:
   IBool* boolean_feature_;
   CAndorSDK3Camera* camera_;
   std::string MM_name_;
   MySequenceThread * thd_;
   SnapShotControl* snapShotController_;
};

#endif // _INTEGERPROPERTY_H_