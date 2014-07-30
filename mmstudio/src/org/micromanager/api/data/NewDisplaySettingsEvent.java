package org.micromanager.api.data;

/**
 * This class signifies that new display settings have been set for a 
 * Datastore.
 */
public interface NewDisplaySettingsEvent {
   public DisplaySettings getDisplaySettings();
}
