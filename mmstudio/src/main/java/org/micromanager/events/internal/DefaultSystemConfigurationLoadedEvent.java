package org.micromanager.events.internal;

import org.micromanager.events.SystemConfigurationLoadedEvent;

/**
 * This interface signals when a configuration file is loaded.
 *
 * This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public class DefaultSystemConfigurationLoadedEvent  implements
        SystemConfigurationLoadedEvent {
}
