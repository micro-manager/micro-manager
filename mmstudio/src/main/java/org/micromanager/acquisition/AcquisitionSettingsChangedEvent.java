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

   /**
    * Indicates whether these settings belong to the application's primary
    * acquisition engine, i.e. the one driving the MDA window, as opposed to a
    * secondary engine such as a Test Acquisition.
    *
    * <p>Secondary engines derive their settings from the MDA window and then
    * modify them for their own purposes (a Test Acquisition, for instance,
    * switches off saving, time-lapse, and the position list).  Those modified
    * settings are not the user's MDA settings, so subscribers that mirror the
    * state of the MDA window should ignore events for which this returns false.
    * Subscribers that simply want to observe whatever acquisition is being set
    * up can ignore this method.
    *
    * <p>Defaults to true, which is the behavior of this event before this
    * method was introduced.
    *
    * @return true if these settings came from the primary acquisition engine.
    */
   default boolean isPrimaryEngine() {
      return true;
   }

}
