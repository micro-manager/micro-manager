#ifndef _EVENT_H_
#define _EVENT_H_

#include <boost/thread/mutex.hpp>
#include <boost/thread/condition_variable.hpp>

/**
* Simple synchronization primitive.
* LW: MicroManager provides only one synchronization primitive: MMThreadLock
* but no events, semaphores etc (at least I haven't found any). It looks like
* boost is the way to go but boost also does not provide a real event so we
* wrote our own.
*/
class Event
{
   public:
      /**
      * Creates an auto reset, non-signalled event
      */
      Event();
      /**
      * Creates an event with specific configuration
      * @param manualReset Set this to true to make this a manual reset event
      * @param initialState True to set the event to initial signalled state
      */
      Event(bool manualReset, bool initialState);
      /**
      * Destroys the event.
      */
      ~Event();

      /**
      * Sets the event to signalled state
      */
      void Set();
      /**
      * Resets the event to non-signalled state
      */
      void Reset();
      /**
      * Waits for the event signal. If the event is already signalled
      * the function returns immediately. If the event is non-signalled
      * the function waits until the event becomes signalled.
      * In case of auto-reset event the event is reset automatically.
      * @return Always true
      */
      bool Wait();
      /**
      * Similar to the argument-less overload but allows to set an timeout.
      * @return False if the wait timeouted. True otherwise.
      */
      bool Wait(unsigned int timeoutMs);

   private:
      bool                      manualReset_;
      bool                      signalled_;
      boost::mutex              mutex_;
      boost::condition_variable condVar_;
};

#endif // _EVENT_H_