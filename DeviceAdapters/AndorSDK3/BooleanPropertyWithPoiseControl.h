#ifndef _BOOLEANPROPERTYWITHPOISECONTROL_H_
#define _BOOLEANPROPERTYWITHPOISECONTROL_H_

#include "BooleanProperty.h"

class TBooleanPropertyWithPoiseControl : public TBooleanProperty
{
public:
  TBooleanPropertyWithPoiseControl(const std::string & MM_name, andor::IBool* bool_feature,
                       ICallBackManager* callback, bool readOnly);
  ~TBooleanPropertyWithPoiseControl(){};
   typedef MM::Action<TBooleanPropertyWithPoiseControl> CBooleanPropertyWithPoiseControlAction;

protected:
  MM::ActionFunctor* CreatePropertyAction();
  int OnBoolean(MM::PropertyBase* pProp, MM::ActionType eAct);
  void setValue(MM::PropertyBase* pProp);
};

#endif // include only once