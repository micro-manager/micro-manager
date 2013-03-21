#pragma once
#include "EventsContainer.h"

class CleanupHelper
{
public:
   CleanupHelper(CEventsManager * _mngr)
      : evMngr_(_mngr)
   {
   }

   void operator () (TEventContainer * _evContainer)
   {
      if (_evContainer->GetRegistered() )
      {
         try
         {
            _evContainer->GetActualEvent()->Detach(evMngr_);
         }
         catch (andor::UnrecognisedObserverException &)
         {
         }
      }
   }
private:
   CEventsManager * evMngr_;
};


class SetupHelper
{
public:
   SetupHelper(CEventsManager * _mngr)
      : evMngr_(_mngr)
   {
   }

   void operator () (TEventContainer * _evContainer)
   {
      try
      {
         _evContainer->GetActualEvent()->Attach(evMngr_);
      }
      catch (std::exception &)
      {
      }
   }
private:
   CEventsManager * evMngr_;
};


