#include "BooleanProperty.h"
#include "CallBackManager.h"

using namespace andor;
using namespace std;

TBooleanProperty::TBooleanProperty(const string & MM_name, IBool * boolean_feature,
                                       ICallBackManager * callback, bool readOnly)
: MM_name_(MM_name),
  boolean_feature_(boolean_feature),
  callback_(callback)
{
  initialise(readOnly);
}

void TBooleanProperty::initialise(bool readOnly)
{
   if (boolean_feature_->IsImplemented())
   {
      MM::ActionFunctor * pAct = CreatePropertyAction();
      callback_->CPCCreateProperty(MM_name_.c_str(), g_StatusON, MM::String, readOnly, pAct);

      vector<string> values;
      values.push_back(g_StatusOFF);
      values.push_back(g_StatusON);
      callback_->CPCSetAllowedValues(MM_name_.c_str(), values);
      try 
      {
         boolean_feature_->Attach(this);
      }
      catch (exception & e)
      {
         // SDK3 Callback not implemented for this feature
         callback_->CPCLog(e.what());
      }
   }
}

MM::ActionFunctor* TBooleanProperty::CreatePropertyAction()
{
  return new CPropertyAction(this, &TBooleanProperty::OnBoolean);
}

TBooleanProperty::~TBooleanProperty()
{
   if (boolean_feature_->IsImplemented())
   {
      try 
      {
         boolean_feature_->Detach(this);
      }
      catch (exception & e)
      {
         // SDK3 Callback not implemented for this feature
         callback_->CPCLog(e.what());
      }
   }
   //Clean up memory, created as passed in
   callback_->GetCameraDevice()->Release(boolean_feature_);
}

void TBooleanProperty::Update(ISubject * /*Subject*/)
{
}

void TBooleanProperty::setFeature(const string & value)
{
   try
   {
      if (boolean_feature_->IsWritable())
      {
         if (value.compare(g_StatusON) == 0)
         {
            boolean_feature_->Set(true);
         }
         else
         {
            boolean_feature_->Set(false);
         }
      }
   }
   catch (exception & e)
   {
      callback_->CPCLog(e.what());
   }
}

// Action handler for OnBoolean
int TBooleanProperty::OnBoolean(MM::PropertyBase * pProp, MM::ActionType eAct)
{

   if (eAct == MM::BeforeGet)
   {
      pProp->Set(boolean_feature_->Get() ? g_StatusON : g_StatusOFF);
   }
   else if (eAct == MM::AfterSet)
   {
      string temp_s;
      pProp->Get(temp_s);
      string currentValue(boolean_feature_->Get() ? g_StatusON : g_StatusOFF);
      if (0 != temp_s.compare(currentValue))
      {
         //Need check poised for Snap as camera running...
         if (callback_->IsSSCPoised())
         {
            callback_->SSCLeavePoised();
            setFeature(temp_s);
            callback_->SSCEnterPoised();
         }
         else if (callback_->IsLiveModeActive()) //Live
         {
            callback_->PauseLiveAcquisition();
            setFeature(temp_s);
            callback_->CPCRestartLiveAcquisition();
         }
         else
         {
            callback_->CPCLog("Error - cannot set boolean feature during MDA");
         }

      }
   }

   return DEVICE_OK;
}