#ifndef _BOOLEANPROPERTY_H_
#define _BOOLEANPROPERTY_H_

#include "atcore++.h"
#include "MMDeviceConstants.h"
#include "Property.h"

class ICallBackManager;

static const char * const g_StatusON = "On";
static const char * const g_StatusOFF = "Off";

class TBooleanProperty : public andor::IObserver
{
public:
   TBooleanProperty(const std::string & MM_name, andor::IBool* bool_feature,
                       ICallBackManager* callback, bool readOnly);
   ~TBooleanProperty();

protected:
   //Update not currently implemented as no attach within SDK3 required at present
   void Update(andor::ISubject* Subject);
   int OnBoolean(MM::PropertyBase* pProp, MM::ActionType eAct);
   typedef MM::Action<TBooleanProperty> CPropertyAction;
   void setFeature(const std::string & value);
   void initialise(bool readOnly);
   virtual MM::ActionFunctor* CreatePropertyAction();

   TBooleanProperty(){};

   andor::IBool* boolean_feature_;
   ICallBackManager* callback_;
   std::string MM_name_;
};

#endif // include only once
