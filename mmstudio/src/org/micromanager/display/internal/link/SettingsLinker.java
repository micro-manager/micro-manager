package org.micromanager.display.internal.link;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.micromanager.display.DisplayWindow;

/**
 * This stub class is for setting up links across DisplayWindows for specific
 * attributes of the DisplaySettings. Implementations of this class will
 * be specific to certain types of the DisplaySettings.
 */
public abstract class SettingsLinker {
   private HashSet<SettingsLinker> linkedLinkers_;
   private List<Class<?>> relevantEventClasses_;

   public SettingsLinker(List<Class<?>> relevantEventClasses) {
      linkedLinkers_ = new HashSet<SettingsLinker>();
      relevantEventClasses_ = relevantEventClasses;
   }

   /**
    * Return a list of DisplaySettingsEvent classes that this particular
    * linker cares about. There must be at least one element in the list.
    */
   public List<Class<?>> getRelevantEventClasses() {
      return relevantEventClasses_;
   }

   /**
    * Return true iff the given DisplaySettingsEvent represents a change that
    * we need to apply to our own DisplayWindow.
    */
   public abstract boolean getShouldApplyChanges(DisplaySettingsEvent changeEvent);

   /**
    * Apply the change indicated by the provided DisplaySettingsEvent to
    * our own DisplayWindow.
    */
   public abstract void applyChange(DisplaySettingsEvent changeEvent);

   /**
    * Generate a semi-unique ID for this linker; it should indicate the
    * specific property, sub-property, or group of properties that this linker
    * handles synchronization for.
    */
   public abstract int getID();
}
