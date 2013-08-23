#include "CallBackManager.h"
#include "SnapShotControl.h"
#include "AndorSDK3.h"

using namespace std;

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

bool CCallBackManager::IsLiveModeActive()
{
   bool b_retCode = false;
   if (thd_)
   {
      b_retCode = !thd_->IsStopped() && LONG_MAX == thd_->GetLength();
   }
   return b_retCode;
}

int CCallBackManager::CPCCreateProperty(const char* name, const char* value, MM::PropertyType eType, 
                                          bool readOnly, MM::ActionFunctor* pAct, bool initStatus)
{
   return parentClass_->CreateProperty(name, value, eType, readOnly, pAct, initStatus);
}

int CCallBackManager::CPCSetAllowedValues(const char* pszName, vector<string>& values)
{
   return parentClass_->SetAllowedValues(pszName, values);
}

int CCallBackManager::CPCAddAllowedValueWithData(const char* pszName, const string & value, long data)
{
   return parentClass_->AddAllowedValue(pszName, value.c_str(), data);
}

int CCallBackManager::CPCLog(const char * msg)
{
   return parentClass_->LogMessage(msg);
}

void CCallBackManager::PauseLiveAcquisition()
{
   thd_->Suspend();
   ssControl_->resetCameraAcquiring();
}

void CCallBackManager::CPCRestartLiveAcquisition()
{
   parentClass_->RestartLiveAcquisition();
   thd_->Resume();
}

void CCallBackManager::CPCStopLiveAcquisition()
{
   parentClass_->StopSequenceAcquisition();
   parentClass_->GetCoreCallback()->ClearImageBuffer(parentClass_);
}

void CCallBackManager::CPCStartLiveAcquisition()
{
   parentClass_->StartSequenceAcquisition(0.f);
}

void CCallBackManager::CPCResizeImageBuffer()
{
   parentClass_->ResizeImageBuffer();
}

int CCallBackManager::CPCGetBinningFactor()
{
   return parentClass_->GetBinning();
}

andor::IDevice * CCallBackManager::GetCameraDevice()
{
   return parentClass_->GetCameraDevice();
}
