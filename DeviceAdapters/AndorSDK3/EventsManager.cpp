#define _CRT_SECURE_NO_DEPRECATE
#include "EventsManager.h"
#include "EventsManagerHelper.h"
#include <algorithm>

using namespace andor;
using namespace std;


static const AT_WC * const g_Buffer_Overflow_Event = L"BufferOverflowEvent";
static const AT_WC * const g_Exposure_End_Event = L"ExposureEndEvent";


//Constructor/Destructor
CEventsManager::CEventsManager(IDevice* cameraDevice)
:  camDevice_(cameraDevice)
{
}

CEventsManager::~CEventsManager(void)
{
   CleanupHelper cleanup(this);
   for_each(v_events.begin(), v_events.end(), cleanup);
   
   TEvIter iter = v_events.begin();
   for (; iter != v_events.end(); ++iter)
   {
      camDevice_->Release((*iter)->GetActualEvent());
      delete *iter;
   }
   v_events.clear();
}

//Private
void CEventsManager::eventsEnable(const AT_WC *const _event, bool _enable)
{
   IEnum * evSelect = camDevice_->GetEnum(L"EventSelector");
   bool b = evSelect->IsImplemented();
   if (b)
   {
      evSelect->Set(_event);
      IBool * evEnable = camDevice_->GetBool(L"EventEnable");
      if (evEnable->IsImplemented() )
      {
         evEnable->Set(_enable);
      }
      camDevice_->Release(evEnable);
   }
   camDevice_->Release(evSelect);
}

CEventsManager::TSDK3EVENTS CEventsManager::getEventTypeFromSubject(ISubject * Subject)
{
   TSDK3EVENTS RetEv = EV_BUFFER_OVERFLOW_EVENT;
   
   IInteger * subjectType = dynamic_cast<IInteger *>(Subject);
   if (subjectType)
   {
      TEvIter iter = v_events.begin();
      for (int i=0; iter != v_events.end(); ++iter, ++i)
      {
         if (subjectType == (*iter)->GetActualEvent() )
         {
            RetEv = static_cast<TSDK3EVENTS>(i);
            break;
         }
      }
   }
   return RetEv;
}

//Public
void CEventsManager::Update(ISubject* Subject)
{
   TSDK3EVENTS ev = getEventTypeFromSubject(Subject);

   if (!v_events[ev]->GetRegistered())
   {
      v_events[ev]->SetRegistered();
      eventsEnable(v_events[ev]->GetFeatureName(), true);
   }
   else
   {
      v_events[ev]->SetFired();
      v_events[ev]->Set();
   }
}

bool CEventsManager::Initialise(char * _errorMsg)
{
   bool b_retCode = false;

   v_events.push_back(new TEventContainer(g_Buffer_Overflow_Event, camDevice_->GetInteger(g_Buffer_Overflow_Event)));
   v_events.push_back(new TEventContainer(g_Exposure_End_Event, camDevice_->GetInteger(g_Exposure_End_Event)));
   
   try
   {
      if (false == v_events[EV_BUFFER_OVERFLOW_EVENT]->GetActualEvent()->IsImplemented())
      {
         strcpy(_errorMsg, "[CEventsManager::Initialise] Events are Not Implemented");
      }
      else
      {
         SetupHelper setupEvent(this);
         for_each(v_events.begin(), v_events.end(), setupEvent);
         b_retCode = true;
      }
   }
   catch (exception & e)
   {
      string s("[CEventsManager::Initialise] Caught Exception with message: ");
      s += e.what();
      strcpy(_errorMsg, s.c_str() );
      b_retCode = false;
   }

   return b_retCode;
}

void CEventsManager::ResetEvent(CEventsManager::TSDK3EVENTS _event)
{
   v_events[_event]->ResetFired();
   v_events[_event]->Reset();
}

bool CEventsManager::IsEventRegistered(CEventsManager::TSDK3EVENTS _event)
{
   return v_events[_event]->GetRegistered();
}

bool CEventsManager::HasEventFired(CEventsManager::TSDK3EVENTS _event)
{
   return v_events[_event]->GetFired();
}

bool CEventsManager::WaitForEvent(CEventsManager::TSDK3EVENTS _event, int _timeout_ms)
{
   return v_events[_event]->Wait(_timeout_ms);
}

