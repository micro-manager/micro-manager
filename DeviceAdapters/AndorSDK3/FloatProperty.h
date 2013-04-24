#ifndef _FLOATPROPERTY_H_
#define _FLOATPROPERTY_H_

#include "atcore++.h"
#include "MMDeviceConstants.h"
#include "Property.h"

class ICallBackManager;

class TFloatProperty : public andor::IObserver
{
public:
   TFloatProperty(const std::string & MM_name,
                  andor::IFloat* float_feature,
                  ICallBackManager* callback,
                  bool readOnly, bool needsCallBack);
   ~TFloatProperty();

protected:
   void Update(andor::ISubject* Subject);
   int OnFloat(MM::PropertyBase* pProp, MM::ActionType eAct);
   typedef MM::Action<TFloatProperty> CPropertyAction;

private:
   void setFeatureWithinLimits(double new_value);

private:
   andor::IFloat* float_feature_;
   ICallBackManager* callback_;
   std::string MM_name_;
   bool callbackRegistered_;
   static const int DEC_PLACES_ERROR = 4;
};

class TFloatStringProperty : public andor::IObserver
{
public:
   TFloatStringProperty(const std::string & MM_name,
                  andor::IFloat* float_feature,
                  ICallBackManager* callback,
                  bool readOnly, bool needsCallBack);
   ~TFloatStringProperty();

   void Update(andor::ISubject* Subject);
   int OnFStrChangeRefresh(MM::PropertyBase* pPropBase, MM::ActionType eAct);
protected:
   typedef MM::Action<TFloatStringProperty> CPropertyAction;

private:
   andor::IFloat* float_feature_;
   ICallBackManager* callback_;
   std::string MM_name_;
   std::string displayStrValue_;
   bool callbackRegistered_;
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

class TAndorFloatCache : public TAndorFloatFilter
{
public:
   TAndorFloatCache(andor::IFloat* _float)
      : TAndorFloatFilter(_float),
        cached_float(0.00)
   {
      cached_float = m_float->Get();
   }

   double Get(bool sscPoised)
   {
      if (sscPoised) 
      {
         return cached_float;
      }
      else 
      {
         return m_float->Get();
      }
   }

   void Set(double Value)
   {
      cached_float = Value;
      m_float->Set(Value);
   }

   void SetCache(double Value)
   {
      cached_float = Value;
   }

private:
   double cached_float;
};


#endif // _FLOATPROPERTY_H_
