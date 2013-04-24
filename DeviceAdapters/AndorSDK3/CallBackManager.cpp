#include "CallBackManager.h"
#include "SnapShotControl.h"
#include "AndorSDK3.h"



CCallBackManager::CCallBackManager(CAndorSDK3Camera * _parentClass, MySequenceThread * _seqAcqThread, SnapShotControl * _snapControl)
:  parentClass_(_parentClass),
   thd_(_seqAcqThread),
   ssControl_(_snapControl)
{
}

CCallBackManager::~CCallBackManager()
{
}

bool CCallBackManager::IsSSCPoised()
{
   bool b_retCode = false;
   if (ssControl_)
   {
      b_retCode = ssControl_->isPoised();
   }
   return b_retCode;
}

bool CCallBackManager::SSCEnterPoised()
{
   bool b_retCode = false;
   if (ssControl_)
   {
      ssControl_->poiseForSnapShot();
      b_retCode = true;
   }
   return b_retCode;
}

bool CCallBackManager::SSCLeavePoised()
{
   bool b_retCode = false;
   if (ssControl_)
   {
      ssControl_->leavePoisedMode();
      b_retCode = true;
   }
   return b_retCode;
}

int CCallBackManager::CPCCreateProperty(const char* name, const char* value, MM::PropertyType eType, 
                                          bool readOnly, MM::ActionFunctor* pAct, bool initStatus)
{
   return parentClass_->CreateProperty(name, value, eType, readOnly, pAct, initStatus);
}

int CCallBackManager::CPCLog(const char * msg)
{
   return parentClass_->LogMessage(msg);
}

andor::IDevice * CCallBackManager::GetCameraDevice()
{
   return parentClass_->GetCameraDevice();
}
