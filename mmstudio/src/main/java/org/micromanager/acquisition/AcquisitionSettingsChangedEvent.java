package org.micromanager.acquisition;

import org.micromanager.MMEvent;

/**
 * <p>The default implementation of this event posts on the Studio event bus,
 * so subscribe to this event using {@link org.micromanager.events.EventManager}.</p>
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
