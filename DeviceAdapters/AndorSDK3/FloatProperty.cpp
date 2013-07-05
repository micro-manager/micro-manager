#include "FloatProperty.h"
#include "CallBackManager.h"
#include <cmath>
#include <iomanip>

using namespace andor;
using namespace std;

TFloatProperty::TFloatProperty(const string & MM_name, IFloat * float_feature, ICallBackManager* callback,
                                bool readOnly, bool needsCallBack)
: MM_name_(MM_name),
  float_feature_(float_feature),
  callback_(callback),
  callbackRegistered_(needsCallBack)
{
   CPropertyAction * pAct = new CPropertyAction (this, &TFloatProperty::OnFloat);
   callback->CPCCreateProperty(MM_name.c_str(), "", MM::Float, readOnly, pAct);

   try 
   {
      if (needsCallBack)
      {
         float_feature_->Attach(this);
      }
   }
   catch (exception & e)
   {
      // SDK3 Callback not implemented for this feature
      callback->CPCLog(e.what());
   }
}

TFloatProperty::~TFloatProperty()
{
   if (callbackRegistered_)
   {
      try 
      {
         float_feature_->Detach(this);
      }
      catch (exception & e)
      {
         // SDK3 Callback not implemented for this feature
         callback_->CPCLog(e.what());
      }
   }
   //Clean up memory, created as passed in
   callback_->GetCameraDevice()->Release(float_feature_);
}

void TFloatProperty::Update(ISubject * Subject)
{
   //if NOT Poised,... (Snapshot sets this first, then changes trigger silently
   // so once updates get applied, and repoise, snapshot sets true, so no erroneous updates get applied
   if ( !callback_->IsSSCPoised() )
   {
      IFloat * featureSubject = dynamic_cast<IFloat *>(Subject);
      TAndorFloatCache * cache = dynamic_cast<TAndorFloatCache *>(float_feature_);
      if (cache && featureSubject)
      {
         cache->SetCache(featureSubject->Get());
      }
   }
}

void TFloatProperty::setFeatureWithinLimits(double new_value)
{
   try
   {
      if (new_value < float_feature_->Min())
      {
         new_value = float_feature_->Min();
      }
      else if (new_value > float_feature_->Max())
      {
         new_value = float_feature_->Max();
      }
      float_feature_->Set(new_value);
   }
   catch (exception & e)
   {
      callback_->CPCLog(e.what());
   }
}


inline bool almostEqual(double val1, double val2, int precisionFactor)
{
   const double base = 10.0;
   double precisionError = 1.0 / pow(base, precisionFactor);
   // Check if val1 and val2 are within precision decimal places
   return ( val1 > (val2 - precisionError) && val1 < (val2 + precisionError)) ? true : false;
}

int TFloatProperty::OnFloat(MM::PropertyBase * pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      TAndorFloatCache * cache = dynamic_cast<TAndorFloatCache *>(float_feature_);
      if (cache)
      {
         pProp->Set(cache->Get(callback_->IsSSCPoised()));
      }
      else
      {
         pProp->Set(float_feature_->Get());
      }

   }
   else if (eAct == MM::AfterSet)
   {
      double new_value = 0.0, current_value = float_feature_->Get();
      pProp->Get(new_value);
      TAndorFloatCache * cache = dynamic_cast<TAndorFloatCache *>(float_feature_);
      if (cache)
      {
         current_value = cache->Get(callback_->IsSSCPoised());
      }
      if (!almostEqual(new_value, current_value, DEC_PLACES_ERROR))
      {
         //Need check first, as camera running,min exp is e.g. 10ms (Long)
         if (callback_->IsSSCPoised())
         {
            callback_->SSCLeavePoised();
            setFeatureWithinLimits(new_value);
            callback_->SSCEnterPoised();
         }
         else if (float_feature_->IsWritable()) //FastExpSw
         {
            setFeatureWithinLimits(new_value);
         }
         else
         {
            callback_->CPCLog("[TFloatProperty::OnFloat] after set, !poised !writable");
         }
      }
   }

   return DEVICE_OK;
}



TFloatStringProperty::TFloatStringProperty(const string & MM_name, IFloat * float_feature, 
                                           ICallBackManager * callback, bool readOnly, bool needsCallBack)
: MM_name_(MM_name),
  float_feature_(float_feature),
  callback_(callback),
  callbackRegistered_(needsCallBack)
{
   displayStrValue_ = "";
   CPropertyAction * pAct = new CPropertyAction (this, &TFloatStringProperty::OnFStrChangeRefresh);
   callback->CPCCreateProperty(MM_name.c_str(), displayStrValue_.c_str(), MM::String, readOnly, pAct);

   try 
   {
      if (needsCallBack)
      {
         float_feature_->Attach(this);
      }
   }
   catch (exception & e)
   {
      // Callback not implemented for this feature
      callback->CPCLog(e.what());
   }
}

TFloatStringProperty::~TFloatStringProperty()
{
   if (callbackRegistered_)
   {
      try 
      {
         float_feature_->Detach(this);
      }
      catch (exception & e)
      {
         // Callback not implemented for this feature
         callback_->CPCLog(e.what());
      }
   }
   //Clean up memory, created as passed in
   callback_->GetCameraDevice()->Release(float_feature_);
}

void TFloatStringProperty::Update(ISubject * /*Subject*/)
{
   if (callbackRegistered_)
   {
      if ( !callback_->IsSSCPoised() )
      {
         IFloat * maxIntTransRate = callback_->GetCameraDevice()->GetFloat(L"MaxInterfaceTransferRate");
         double d_rate = maxIntTransRate->Get();
         callback_->GetCameraDevice()->Release(maxIntTransRate);

         stringstream ss;
         ss.setf(ios::fixed, ios::floatfield);
         ss << "Min: " << setprecision(5) << float_feature_->Min() << "  Max: " << float_feature_->Max();
         ss << "  Max Sustain: " << d_rate;
         displayStrValue_ = ss.str();
      }
   }
}

int TFloatStringProperty::OnFStrChangeRefresh(MM::PropertyBase * pPropBase, MM::ActionType eAct)
{
   if (MM::BeforeGet == eAct)
   {
      MM::Property * pProperty = dynamic_cast<MM::Property *>(pPropBase);
      pProperty->SetReadOnly(false);
      pProperty->Set(displayStrValue_.c_str() );
      pProperty->SetReadOnly(true);

   }
   else if (MM::AfterSet == eAct)
   {
      // Code execution should never get in here.
   }
   return DEVICE_OK;
}
