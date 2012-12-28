#define _CRT_SECURE_NO_DEPRECATE
#include "EventsManager.h"
#include <list>
#include <algorithm>

using namespace andor;
using namespace std;


static const AT_WC * const EV_BUFFOVERFLOW = L"BufferOverflowEvent";

//Constructor/Destructor
CEventsManager::CEventsManager(IDevice* cameraDevice)
:  camDevice_(cameraDevice),
   bufferOverflowEvent_(NULL),
   bufferOverFlowEventRegistered_(false),
   bufferOverFlowEventFired_(false)
{
}

CEventsManager::~CEventsManager(void)
{
   if (bufferOverFlowEventRegistered_)
   {
      EventsEnable(EV_BUFFOVERFLOW, false);
      bufferOverflowEvent_->Detach(this);
   }
   camDevice_->Release(bufferOverflowEvent_);
}

//Private
void CEventsManager::EventsEnable(const AT_WC *const _event, bool _enable)
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

//Public
void CEventsManager::Update(ISubject* /*Subject*/)
{
   static bool b_enabled = false;

   if (!b_enabled)
   {
      b_enabled = true;
      bufferOverFlowEventFired_ = false;
      bufferOverFlowEventRegistered_ = true;
   }
   else
   {
      bufferOverFlowEventFired_ = true;
   }
}

bool CEventsManager::Initialise(char * _errorMsg)
{
   bool b_retCode = false;

   bufferOverflowEvent_ = camDevice_->GetInteger(EV_BUFFOVERFLOW);

   try
   {
      bool b = bufferOverflowEvent_->IsImplemented();
      if (b)
      {
         bufferOverflowEvent_->Attach(this);
         EventsEnable(EV_BUFFOVERFLOW, true);
         b_retCode = true;
      }
      else
      {
         strcpy(_errorMsg, "[CEventsManager::Initialise] Buffer Overflow Event is Not Implemented");
      }
   }
   catch (exception & e)
   {
      string s("[CEventsManager::Initialise] BufferOverflowEvent Caught Exception with message: ");
      s += e.what();
      strcpy(_errorMsg, s.c_str() );
      b_retCode = false;
   }

   return b_retCode;
}

void CEventsManager::ResetEvent(CEventsManager::TSDK3EVENTS _event)
{
   switch(_event)
   {
   case EV_BUFFER_OVERFLOW_EVENT:
      bufferOverFlowEventFired_ = false;
      break;
   default:
      break;

   }
}

bool CEventsManager::IsEventRegistered(CEventsManager::TSDK3EVENTS _event)
{
   bool b_retCode = false;

   switch(_event)
   {
   case EV_BUFFER_OVERFLOW_EVENT:
      b_retCode = bufferOverFlowEventRegistered_;
      break;
   default:
      b_retCode = false;
      break;

   }
   return b_retCode;
}

bool CEventsManager::HasEventFired(CEventsManager::TSDK3EVENTS _event)
{
   bool b_retCode = false;

   switch(_event)
   {
   case EV_BUFFER_OVERFLOW_EVENT:
      b_retCode = bufferOverFlowEventFired_;
      break;
   default:
      b_retCode = false;
      break;

   }
   return b_retCode;
}
