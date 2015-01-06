package org.micromanager.imagedisplay.link;

import java.util.List;

import org.micromanager.api.display.DisplayWindow;

/**
 * This interface is for setting up links across DisplayWindows for specific
 * attributes of the DisplaySettings. Implementations of this interface will
 * be specific to certain types of the DisplaySettings.
 */
public interface SettingsLinker {
   /**
    * Return a list of DisplaySettingsEvent classes that this particular
    * linker cares about. There must be at least one element in the list.
    */
   public List<Class<?>> getRelevantEventClasses();
   /**
    * Return true iff the given DisplaySettingsEvent represents a change that
    * we need to apply to our own DisplayWindow.
    */
   public boolean getShouldApplyChanges(DisplaySettingsEvent changeEvent);

   /**
    * Apply the change indicated by the provided DisplaySettingsEvent to
    * our own DisplayWindow.
    */
   public void applyChange(DisplaySettingsEvent changeEvent);

   /**
    * Generate a semi-unique ID for this linker; it should indicate the
    * specific property, sub-property, or group of properties that this linker
    * handles synchronization for.
    */
   public int getID();
}
