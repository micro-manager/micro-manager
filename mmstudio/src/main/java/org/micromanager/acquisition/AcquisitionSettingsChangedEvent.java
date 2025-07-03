package org.micromanager.acquisition;

import org.micromanager.MMEvent;

/**
 * The default implementation of this event posts on the Studio event bus,
 * so subscribe to this event using {@link org.micromanager.events.EventManager}.
 */
public interface AcquisitionSettingsChangedEvent extends MMEvent {

   /**
    * Return the freshly changed SequenceSettings.  This event should
    * occur very soon after new SequenceSettings were sent to the AcquisitionEngine.
    *
    * @return Newly changed SequenceSettings
    */
   SequenceSettings getNewSettings();

}
