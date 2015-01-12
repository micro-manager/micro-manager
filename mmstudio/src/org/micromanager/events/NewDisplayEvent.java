package org.micromanager.events;

import org.micromanager.display.DisplayWindow;

/**
 * This event is posted whenever a new display window is created for *any*
 * Datastore. Register for this event using the MMStudio.registerForEvents()
 * method (i.e. not the equivalent Datastore or DisplayWindow methods).
 */
public interface NewDisplayEvent {
   public DisplayWindow getDisplayWindow();
}
