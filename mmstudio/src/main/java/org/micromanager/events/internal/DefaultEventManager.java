package org.micromanager.events.internal;

import com.google.common.eventbus.EventBus;
import org.micromanager.events.EventManager;
import org.micromanager.internal.utils.EventBusExceptionLogger;

// This is a singleton wrapper around the Guava library's EventBus. It exposes
// a system-wide EventBus for certain general-purpose events.
public final class DefaultEventManager implements EventManager {
   // Singleton.
   private static final DefaultEventManager STATIC_INSTANCE;
   static {
      STATIC_INSTANCE = new DefaultEventManager();
   }

   private final EventBus bus_;
   public DefaultEventManager() {
      bus_ = new EventBus(EventBusExceptionLogger.getInstance());
   }

   @Override
   public void registerForEvents(Object obj) {
      bus_.register(obj);
   }

   @Override
   public void unregisterForEvents(Object obj) {
      bus_.unregister(obj);
   }

   @Override
   public void post(Object obj) {
      bus_.post(obj);
   }

   public static DefaultEventManager getInstance() {
      return STATIC_INSTANCE;
   }
}
