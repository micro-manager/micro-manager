package org.micromanager.data.internal;

import org.micromanager.data.NewPipelineEvent;


/**
 * This class signifies that the configuration of the application pipeline
 * has changed, giving entities that use that pipeline an opportunity to make
 * a new copy of it (by invoking DataManager.copyApplicationPipeline()).
 *
 * The default implementation of this event is posted on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 * This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public class DefaultNewPipelineEvent implements NewPipelineEvent {
}
