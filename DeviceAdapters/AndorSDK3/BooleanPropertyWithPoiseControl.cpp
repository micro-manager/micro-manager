#include "BooleanPropertyWithPoiseControl.h"
#include "CallBackManager.h"

using namespace andor;
using namespace std;

TBooleanPropertyWithPoiseControl::TBooleanPropertyWithPoiseControl(const std::string & MM_name, andor::IBool* boolean_feature,
                       ICallBackManager* callback, bool readOnly) 
{ 
	MM_name_ = MM_name;
	boolean_feature_ = boolean_feature;
	callback_ = callback;
	initialise(readOnly);  
}

MM::ActionFunctor* TBooleanPropertyWithPoiseControl::CreatePropertyAction()
{
	return new CBooleanPropertyWithPoiseControlAction(this, &TBooleanPropertyWithPoiseControl::OnBoolean);
}

int TBooleanPropertyWithPoiseControl::OnBoolean(MM::PropertyBase* pProp, MM::ActionType eAct)
{
  if (eAct == MM::BeforeGet)
   {
	 if(callback_->IsSSCPoised())
	 {
		 callback_->SSCLeavePoised();
		 pProp->Set(boolean_feature_->Get() ? g_StatusON : g_StatusOFF);
		 callback_->SSCEnterPoised();
	 }
	 else if(callback_->IsLiveModeActive())
	 {
		 callback_->PauseLiveAcquisition();
		 pProp->Set(boolean_feature_->Get() ? g_StatusON : g_StatusOFF);
		 callback_->CPCRestartLiveAcquisition();
	 }
   }
   else if (eAct == MM::AfterSet)
   {
		//Need check poised for Snap as camera running...
        if (callback_->IsSSCPoised())
        {
          callback_->SSCLeavePoised();

          setValue(pProp);

          callback_->SSCEnterPoised();
        }
        else if (callback_->IsLiveModeActive()) //Live
        {
          callback_->PauseLiveAcquisition();

          setValue(pProp);

          callback_->CPCRestartLiveAcquisition();
        }
        else
        {
          callback_->CPCLog("Error - cannot set boolean feature during MDA");
        }
   }

   return DEVICE_OK;
}

void TBooleanPropertyWithPoiseControl::setValue(MM::PropertyBase* pProp)
{
	string inputValue;
    pProp->Get(inputValue);//need to leave poised to get the value from the expected trigger mode (the one shown in property browser)
    string sdkValue(boolean_feature_->Get() ? g_StatusON : g_StatusOFF);
    if (0 != inputValue.compare(sdkValue))// don't set if it hasn't changed
    {
		setFeature(inputValue);
    }
}


