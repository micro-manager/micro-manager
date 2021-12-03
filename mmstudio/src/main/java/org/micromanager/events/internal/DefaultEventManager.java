package org.micromanager.events.internal;

import com.google.common.eventbus.EventBus;
import org.micromanager.LogManager;
import org.micromanager.events.EventManager;
import org.micromanager.internal.utils.EventBusExceptionLogger;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This is a singleton wrapper around the Guava library's EventBus. It exposes
 * a system-wide EventBus for certain general-purpose events.
 */
public final class DefaultEventManager implements EventManager {

   private final EventBus bus_;
   private final LogManager logger_;
   
   /**
    * This DefaultEventManager is basically a pass-through to the Google (formerly Guava) Eventbus.
    */
   public DefaultEventManager() {
      bus_ = new EventBus(EventBusExceptionLogger.getInstance());
      logger_ = ReportingUtils.getWrapper();
   }

   @Override
   public void registerForEvents(Object obj) {
      try {
         bus_.register(obj);
      } catch (IllegalArgumentException iae) {
         logger_.logError(iae, "Error while registering");
      }
   }

   @Override
   public void unregisterForEvents(Object obj) {
      try {
         bus_.unregister(obj);
      } catch (IllegalArgumentException iae) {
         logger_.logError(iae, "Error while unregistering");
      }
   }

   @Override
   public void post(Object obj) {
      try {
         bus_.post(obj);
      } catch (IllegalArgumentException iae) {
         logger_.logError(iae, "Error while posting");
      }
   }
}
