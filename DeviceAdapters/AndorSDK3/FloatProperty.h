#ifndef _FLOATPROPERTY_H_
#define _FLOATPROPERTY_H_

#include "atcore++.h"

using namespace andor;

class MySequenceThread;
class CAndorSDK3Camera;
class SnapShotController;

class TFloatProperty : public IObserver
{
public:
   TFloatProperty(const std::string MM_name,
                  IFloat* float_feature,
                  CAndorSDK3Camera* camera,
                  MySequenceThread* thd,
                  SnapShotControl* snapShotController,
                  bool readOnly, bool limited);
   ~TFloatProperty();

   void Update(ISubject* Subject);
   int OnFloat(MM::PropertyBase* pProp, MM::ActionType eAct);
   typedef MM::Action<TFloatProperty> CPropertyAction;

private:
   IDevice* device_hndl_;
   IFloat* float_feature_;
   CAndorSDK3Camera* camera_;
   std::string MM_name_;
   MySequenceThread * thd_;
   SnapShotControl* snapShotController_;
   bool limited_;
   bool almostEqual(double val1, double val2, double precision);
};

class TAndorFloatFilter : public IFloat
{
public:
   TAndorFloatFilter(IFloat* _float):m_float(_float){}
   virtual ~TAndorFloatFilter() {};
   double Get() {return m_float->Get();}
   void Set(double Value){m_float->Set(Value);}
   double Max() {return m_float->Max();}
   double Min() {return m_float->Min();}
   bool IsImplemented(){return m_float->IsImplemented();}
   bool IsReadable(){return m_float->IsReadable();}
   bool IsWritable(){return m_float->IsWritable();}
   bool IsReadOnly(){return m_float->IsReadOnly();}
   void Attach(IObserver* _observer){m_float->Attach(_observer);}
   void Detach(IObserver* _observer){m_float->Detach(_observer);}

protected:
   IFloat* m_float;
};

class TAndorFloatValueMapper : public TAndorFloatFilter
{
public:
   TAndorFloatValueMapper(IFloat* _float, double _factor)
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
   TAndorFloatHolder(SnapShotControl* snapShotController, IFloat* _float)
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
      m_float->Get();
   }

private:
   SnapShotControl* m_snapShotController;
   double holding_float;
};

#endif // _FLOATPROPERTY_H_