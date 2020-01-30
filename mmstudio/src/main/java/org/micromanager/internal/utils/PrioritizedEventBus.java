package org.micromanager.internal.utils;

import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * The PrioritizedEventBus allows registrants to provide a priority value; when
 * dispatching events to registrants, those with lower priority numbers are
 * notified first.
 */
public final class PrioritizedEventBus {
   // Priority at which to register objects when no priority is specified.
   public static final int DEFAULT_PRIORITY = 100;
   /**
    * Each priority level corresponds to a different EventBus instance.
    */
   private HashMap<Integer, EventBus> prioritizedBuses_;

   public PrioritizedEventBus() {
      prioritizedBuses_ = new HashMap<Integer, EventBus>();
   }

   public void register(Object o) {
      register(o, DEFAULT_PRIORITY);
   }

   public void register(Object o, Integer priority) {
      EventBus subBus;
      if (!prioritizedBuses_.containsKey(priority)) {
         subBus = new EventBus(EventBusExceptionLogger.getInstance());
         prioritizedBuses_.put(priority, subBus);
      }
      else {
         subBus = prioritizedBuses_.get(priority);
      }
      subBus.register(o);
   }

   public void unregister(Object o) {
      for (Integer priority : prioritizedBuses_.keySet()) {
         EventBus subBus = prioritizedBuses_.get(priority);
         // TODO: I can't find any way to test if a given EventBus has a given
         // object subscribed to it, hence the try/catch logic here, which is
         // unpleasant. However, objects should not be frequently unregistering
         // so I don't think it's a huge issue.
         // Similarly I'm not too fussed that we may have empty
         // SubscriberRegistries lying around after unregister is called.
         try {
            subBus.unregister(o);
         }
         catch (IllegalArgumentException e) {
         }
      }
   }

   public void post(Object event) {
      ArrayList<Integer> priorities = new ArrayList<Integer>(prioritizedBuses_.keySet());
      Collections.sort(priorities);
      for (Integer priority : priorities) {
         EventBus subBus = prioritizedBuses_.get(priority);
         subBus.post(event);
      }
   }
}
