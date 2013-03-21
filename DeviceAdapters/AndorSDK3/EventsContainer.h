#pragma once
#include <windows.h>

class TEventContainer
{
public:
   TEventContainer(const AT_WC * _name, andor::IInteger * _event)
   :  actualEvent_(_event),
      wcs_name_(_name),
      eventRegistered_(false),
      eventFired_(false),
      h_event_(CreateEvent(NULL, false, false, NULL))
   {
   }
   ~TEventContainer() {CloseHandle(h_event_); };
   andor::IInteger * GetActualEvent() { return actualEvent_; };
   const AT_WC * GetFeatureName() { return wcs_name_.c_str(); };
   void SetRegistered() { eventRegistered_ = true; };
   bool GetRegistered() { return eventRegistered_; };
   void SetFired() { eventFired_ = true; };
   void ResetFired() { eventFired_ = false; };
   bool GetFired() { return eventFired_; };
   bool Wait(int _iTimeout_ms)
   {
     DWORD retVal = WaitForSingleObject(h_event_, _iTimeout_ms);
     if(retVal!=WAIT_OBJECT_0)
     {
       return false;
     }
     return true;
   }
   void Set() { SetEvent(h_event_); }; 
   void Reset() { ResetEvent(h_event_); };
   
private:
   andor::IInteger * actualEvent_;
   std::wstring wcs_name_;
   bool eventRegistered_;
   bool eventFired_;
   HANDLE h_event_;
};

