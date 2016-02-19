///////////////////////////////////////////////////////////////////////////////
// FILE:          TestControl.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Okolab device test adapter.
//                
// AUTHOR:        Domenico Mastronardi @ Okolab
//                
// COPYRIGHT:     Okolab s.r.l.
//
// LICENSE:       This file is distributed under the BSD license.
//

#include "Okolab.h"
#include "TestControl.h"
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>

// Note: this module uses Microsoft's C++/CLI to call CLR API.
// This is only used to check if a process of a given name is running,
// and could be rewritten using Win32 API should we need to disable
// C++/CLI.
///// Removed
/////#if defined(WIN32) || defined(WIN64)
///// #using <System.dll>  // Process
/////#endif

const char* g_TestControl  = "Test Unit";

using namespace std;


///////////////////////////////////////////////////////////////////////////////
// TestControl implementation
//

TestControl::TestControl() :
 initialized_(false), port_("Undefined")
{
 InitializeDefaultErrorMessages();

 CreateProperty(MM::g_Keyword_Name, g_TestControl , MM::String, true);
 CreateProperty(MM::g_Keyword_Description, "Okolab Test Unit", MM::String, true);

 CPropertyAction* pAct = new CPropertyAction (this, &TestControl::OnPort);      
 CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct);
}



TestControl::~TestControl()
{
 if(initialized_) Shutdown();
}



void TestControl::GetName(char* name) const
{
 CDeviceUtils::CopyLimitedString(name, g_TestControl);
}


int TestControl::Initialize()
{
 if(initialized_) return DEVICE_OK;
 mthread_ = new TestRefreshThread(*this);

// OCSStart();
/*
 if(!OCSRunning()) 
  { 
   LogMessage("OKO Control Service not running!",false);
   return DEVICE_NOT_CONNECTED;
  }
*/
 CPropertyAction* pAct=new CPropertyAction(this, &TestControl::OnGetRand);
 int ret=CreateProperty("Random", "0", MM::Float, true, pAct);
 if(ret!=DEVICE_OK) return ret;                           

 CPropertyAction* pAct1 = new CPropertyAction (this, &TestControl::OnTestAction);
 ret=CreateProperty("TestAction", "Off", MM::String, false, pAct1);
 if(ret!=DEVICE_OK) return ret;                           
 AddAllowedValue("TestAction", "Off");   
 AddAllowedValue("TestAction", "On");       

 initialized_=true;
 this->RefreshThread_Start();

 return DEVICE_OK;
}


int TestControl::Shutdown()
{
 if(initialized_)
  {
   RefreshThread_Stop();
   delete(mthread_);
  }
 initialized_ = false;
 return DEVICE_OK;
}


bool TestControl::WakeUp()
{
 return true; 
}


bool TestControl::Busy()
{
 return false;
}


int TestControl::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct == MM::BeforeGet)
  {
   pProp->Set(port_.c_str());
  }
 else if(eAct == MM::AfterSet)
       {
		if(initialized_)
		 {
          // revert
          pProp->Set(port_.c_str());
          return ERR_PORT_CHANGE_FORBIDDEN;
		 }
        pProp->Get(port_);                                                     
       }                                                                         
 return DEVICE_OK;     
}


int TestControl::OnGetRand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
   double rndnum=0;
   if(GetRand(rndnum)==DEVICE_OK) pProp->Set(rndnum);
  }
 return DEVICE_OK;     
}


int TestControl::GetRand(double& rndnum)
{   
 if(!initialized_) return DEVICE_ERR;
 rndnum=rand()%100;
 return DEVICE_OK;                                                         
} 



int TestControl::OnTestAction(MM::PropertyBase* /*pProp*/, MM::ActionType eAct)
{
 if(eAct==MM::BeforeGet)
  {
  }
 else if(eAct == MM::AfterSet)
       {
		if(initialized_)
		 {
          TestAction("  ");
		 }
       }                                                                         
 return DEVICE_OK;
}



int TestControl::TestAction(char * /*straction*/)
{
 #if defined(WIN32) || defined(WIN64)

 char cmdline[500]; 

 DWORD size=1023;
 DWORD type=REG_SZ;
 TCHAR wvalue[255];
 HKEY hKey;

 #if defined(WIN32)
 if(RegOpenKeyEx(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion", 0, KEY_READ | KEY_WOW64_64KEY, &hKey)==ERROR_SUCCESS)
  {
   DWORD dwret;
   dwret=RegQueryValueEx(hKey, L"ProgramFilesDir", NULL, &type, (LPBYTE)wvalue, &size);
   RegCloseKey(hKey);
  }
 #endif

 #if defined(WIN64)
 if(RegOpenKeyEx(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion", 0, KEY_READ | KEY_WOW64_64KEY, &hKey)==ERROR_SUCCESS)
  {
   DWORD dwret;
   dwret=RegQueryValueEx(hKey, L"ProgramFilesDir (x86)", NULL, &type, (LPBYTE)wvalue, &size);
   RegCloseKey(hKey);
  }
 #endif

 char value[400]; 
 WideCharToMultiByte(CP_ACP,0,wvalue,255,value,400,NULL,NULL);
 snprintf(cmdline,500,"\"%s\\OKOlab\\OKO-Control Server\\OKO-Control Server.exe\"",value);
/*
FILE *fp;
fp=fopen("registry_log.txt","a");
fprintf(fp,"cmdline=%s\n",cmdline);
fclose(fp);
*/
// if(OCSRunning()) return;
 TCHAR path[500];
 MultiByteToWideChar(CP_ACP, MB_PRECOMPOSED, cmdline, -1, path, 500);

 SHELLEXECUTEINFO ShExecInfo;
 ShExecInfo.cbSize = sizeof(SHELLEXECUTEINFO);
 ShExecInfo.fMask = NULL;
 ShExecInfo.hwnd = NULL;
 ShExecInfo.lpVerb = NULL;
 ShExecInfo.lpFile = path;
 ShExecInfo.lpParameters = NULL;
 ShExecInfo.lpDirectory = NULL;
 ShExecInfo.nShow = SW_MAXIMIZE;
 ShExecInfo.hInstApp = NULL;
 ShellExecuteEx(&ShExecInfo);

 #endif

 return DEVICE_OK;                                                         
}




void TestControl::RefreshThread_Start()
{
 mthread_->Start();
}


void TestControl::RefreshThread_Stop()
{
 mthread_->Stop();
}


void TestControl::UpdateGui()
{
 this->OnPropertiesChanged();
}


void TestControl::UpdatePropertyGui(double new_val)
{
 char strVal[30];
 sprintf(strVal,"%f",new_val);
 this->OnPropertyChanged("Random",strVal);
}




TestRefreshThread::TestRefreshThread(TestControl &oDevice) :
   stop_(true), okoDevice_(oDevice)
{
};


TestRefreshThread::~TestRefreshThread()
{
 Stop();
 wait();
}


int TestRefreshThread::svc() 
{
 while(!stop_)
  {
   double v=0;

   (void)okoDevice_.GetRand(v);
   okoDevice_.UpdatePropertyGui(v);
/*
FILE *fp;
fp=fopen("log_test_thread.txt","a");
fprintf(fp,"thread running; val=%f\n",v);
fclose(fp);
*/
   CDeviceUtils::SleepMs(2000);
  }
 return DEVICE_OK;
}


void TestRefreshThread::Start()
{
 if(stop_) { stop_=false; activate(); }
}

