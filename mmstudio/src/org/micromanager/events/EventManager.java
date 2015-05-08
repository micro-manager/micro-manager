package org.micromanager.events;

import com.google.common.eventbus.EventBus;

// This is a singleton wrapper around the Guava library's EventBus. It exposes
// a global EventBus for system-wide events.
public class EventManager {
   // Singleton.
   public static EventBus bus_;

   public EventManager() {
      bus_ = new EventBus();
   }

   public static void register(Object obj) {
      bus_.register(obj);
   }
   
   public static void unregister(Object obj) {
      bus_.unregister(obj);
   }

   public static void post(Object obj) {
      bus_.post(obj);
   }

   public static EventBus getBus() {
      return bus_;
   }
}
