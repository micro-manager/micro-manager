package org.micromanager.events.internal;

import org.micromanager.events.GUIRefreshEvent;

/**
 * This event posts whenever the GUI refreshes its state from the Core
 * (e.g. when the user clicks the "Refresh" button or when code calls the
 * refreshGUI() method in CompatibilityInterface).
 *
 * This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public class DefaultGUIRefreshEvent implements GUIRefreshEvent {
}
