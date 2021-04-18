package org.micromanager.events.internal;

import org.micromanager.events.PropertiesChangedEvent;

/**
 * This class signals when any property of the microscope has changed.
 * Note that there is a discrepancy with the definition of OnPropertiesChanged
 * in MMCore, which is specific only for a given device (or device adapter).
 * Since the Core callbacks into this event, there is a mismatch here that needs
 * to be resolved.
 *
 * This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public class DefaultPropertiesChangedEvent implements PropertiesChangedEvent {
}
