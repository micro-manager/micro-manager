#pragma once
#include "atcore++.h"

class CEventsManager : public andor::IObserver
{
public:
   typedef enum 
   {
      EV_BUFFER_OVERFLOW_EVENT
   } TSDK3EVENTS;

public:
   CEventsManager(andor::IDevice* cameraDevice);
   ~CEventsManager(void);

   void Update(andor::ISubject* Subject);
   bool Initialise(char * _errorMsg);

   void ResetEvent(CEventsManager::TSDK3EVENTS _event);
   bool IsEventRegistered(CEventsManager::TSDK3EVENTS _event);
   bool HasEventFired(CEventsManager::TSDK3EVENTS _event);

private:
   void EventsEnable(const AT_WC *const _event, bool _enable);

private:
   andor::IDevice * camDevice_;
   andor::IInteger * bufferOverflowEvent_;
   bool bufferOverFlowEventRegistered_;
   bool bufferOverFlowEventFired_;
};
