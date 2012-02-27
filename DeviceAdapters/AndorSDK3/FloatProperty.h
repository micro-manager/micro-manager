#ifndef _FLOATPROPERTY_H_
#define _FLOATPROPERTY_H_

#include "atcore++.h"
#include "MMDeviceConstants.h"
#include "Property.h"
#include "SnapShotControl.h"

class MySequenceThread;
class CAndorSDK3Camera;

class TFloatProperty : public andor::IObserver
{
public:
   TFloatProperty(const std::string & MM_name,
                  andor::IFloat* float_feature,
                  CAndorSDK3Camera* camera,
                  MySequenceThread* thd,
                  SnapShotControl* snapShotController,
                  bool readOnly, bool limited);
   ~TFloatProperty();

   void Update(andor::ISubject* Subject);
   int OnFloat(MM::PropertyBase* pProp, MM::ActionType eAct);
   typedef MM::Action<TFloatProperty> CPropertyAction;

private:
   andor::IDevice* device_hndl_;
   andor::IFloat* float_feature_;
   CAndorSDK3Camera* camera_;
   std::string MM_name_;
   MySequenceThread * thd_;
   SnapShotControl* snapShotController_;
   bool limited_;
};

class TAndorFloatFilter : public andor::IFloat
{
public:
   TAndorFloatFilter(andor::IFloat* _float):m_float(_float){}
   virtual ~TAndorFloatFilter() {};
   double Get() {return m_float->Get();}
   void Set(double Value){m_float->Set(Value);}
   double Max() {return m_float->Max();}
   double Min() {return m_float->Min();}
   bool IsImplemented(){return m_float->IsImplemented();}
   bool IsReadable(){return m_float->IsReadable();}
   bool IsWritable(){return m_float->IsWritable();}
   bool IsReadOnly(){return m_float->IsReadOnly();}
   void Attach(andor::IObserver* _observer){m_float->Attach(_observer);}
   void Detach(andor::IObserver* _observer){m_float->Detach(_observer);}

protected:
   andor::IFloat* m_float;
};

class TAndorFloatValueMapper : public TAndorFloatFilter
{
public:
   TAndorFloatValueMapper(andor::IFloat* _float, double _factor)
      :TAndorFloatFilter(_float), m_factor(_factor)
   {
   }
   ~TAndorFloatValueMapper() {}

   double Get() {return m_float->Get()*m_factor;}
   void Set(double Value) {m_float->Set(Value/m_factor);}
   double Max() {return m_float->Max()*m_factor;}
   double Min() {return m_float->Min()*m_factor;}

protected:
   double m_factor;
};

class TAndorFloatHolder : public TAndorFloatFilter
{
public:
   TAndorFloatHolder(SnapShotControl* snapShotController, andor::IFloat* _float)
      :m_snapShotController(snapShotController), TAndorFloatFilter(_float)
   {
      holding_float = m_float->Get();
   }

   double Get()
   {
      if (m_snapShotController->isInternal()) {
         return holding_float;
      }
      else {
         return m_float->Get();
      }
   }

   void Set(double Value)
   {
      holding_float = Value;
      m_float->Set(Value);
   }

private:
   SnapShotControl* m_snapShotController;
   double holding_float;
};

#endif // _FLOATPROPERTY_H_
