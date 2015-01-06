package org.micromanager.imagedisplay.link;

import org.micromanager.api.display.DisplayWindow;

/**
 * This interface is for setting up links across DisplayWindows for specific
 * attributes of the DisplaySettings. Implementations of this interface will
 * be specific to certain types of the DisplaySettings.
 */
public interface SettingsLinker {
   /**
    * Return true iff the given DisplaySettingsEvent, sourced from the
    * specified DisplayWindow, represents a change that we need to apply
    * to our own DisplayWindow.
    */
   public boolean getShouldApplyChanges(DisplayWindow sourceWindow,
         DisplaySettingsEvent changeEvent);

   /**
    * Apply the change indicated by the provided DisplaySettingsEvent to
    * our own DisplayWindow.
    */
   public void applyChange(DisplaySettingsEvent changeEvent);
}
