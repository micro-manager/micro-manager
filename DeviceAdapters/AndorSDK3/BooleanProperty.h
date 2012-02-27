#ifndef _BOOLEANPROPERTY_H_
#define _BOOLEANPROPERTY_H_

#include "MMDeviceConstants.h"
#include "Property.h"

class MySequenceThread;
class CAndorSDK3Camera;
class SnapShotControl;
namespace andor {
   class IBool;
   class ISubject;
};

class TBooleanProperty
{
public:
   TBooleanProperty(const std::string & MM_name, andor::IBool* bool_feature,
                    CAndorSDK3Camera* camera, MySequenceThread* thd,
                    SnapShotControl* snapShotController, bool readOnly);
   ~TBooleanProperty();

   //Update not currently implemented as no attach within SDK3 required at present
   void Update(andor::ISubject* Subject);
   int OnBoolean(MM::PropertyBase* pProp, MM::ActionType eAct);
   typedef MM::Action<TBooleanProperty> CPropertyAction;

private:
   andor::IBool* boolean_feature_;
   CAndorSDK3Camera* camera_;
   std::string MM_name_;
   MySequenceThread * thd_;
   SnapShotControl* snapShotController_;
};

#endif // include only once
