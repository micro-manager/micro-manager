package org.micromanager.api.display;

/**
 * This event signifies that a DisplayWindow wants to close. If this is
 * acceptable to whatever is controlling the DisplayWindow, then that entity
 * should call DisplayWindow.forceClosed().
 */
public interface RequestToCloseEvent {
   public DisplayWindow getWindow();
}
