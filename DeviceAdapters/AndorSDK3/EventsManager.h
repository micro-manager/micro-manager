#pragma once
#include "atcore++.h"
#include <vector>

class TEventContainer;

class CEventsManager : public andor::IObserver
{
public:
   typedef enum 
   {
      EV_BUFFER_OVERFLOW_EVENT,
      EV_EXPOSURE_END_EVENT,
      EV_TOTAL_SIZE  //Do not remove; insert new members above this.
   } TSDK3EVENTS;

public:
   CEventsManager(andor::IDevice* cameraDevice);
   ~CEventsManager(void);

   void Update(andor::ISubject* Subject);
   bool Initialise(char * _errorMsg);

   void ResetEvent(CEventsManager::TSDK3EVENTS _event);
   bool IsEventRegistered(CEventsManager::TSDK3EVENTS _event);
   bool HasEventFired(CEventsManager::TSDK3EVENTS _event);
   bool WaitForEvent(CEventsManager::TSDK3EVENTS _event, int _timeout_ms);

private:
   TSDK3EVENTS getEventTypeFromSubject(andor::ISubject * subjectType);
   void eventsEnable(const AT_WC *const _event, bool _enable);

private:
   andor::IDevice * camDevice_;
   
   std::vector<TEventContainer *> v_events;
   typedef std::vector<TEventContainer *>::iterator TEvIter;
};

