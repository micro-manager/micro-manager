package org.micromanager.events;

import org.micromanager.MMEvent;

/**
 * Event that may fire when a Shutter opened or closed
 * (at the discretion of the device adapter).
 */
public interface ShutterOpenChangedEvent extends MMEvent {

   /**
    * Name of Shutter that opened or closed.
    *
    * @return Name of Shutter that opened or closed
    */
   String getDeviceName();

   /**
    * Indicates whether the Shutter was opened or closed.
    *
    * @return Flag indicating whether the Shutter is now open or closed.
    */
   boolean isShutterOpened();
}
