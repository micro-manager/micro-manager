package org.micromanager.events.internal;

import org.micromanager.events.StartupCompleteEvent;

/**
 * This event signifies that the system finished starting up.
 *
 * <p>This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.</p>
 */
public class DefaultStartupCompleteEvent implements StartupCompleteEvent {
}
