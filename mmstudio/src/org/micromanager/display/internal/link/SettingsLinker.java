package org.micromanager.display.internal.link;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;

import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class is for setting up links across DisplayWindows for specific
 * attributes of the DisplaySettings. Extensions of this class will be specific
 * to certain types of the DisplaySettings.
 */
public abstract class SettingsLinker {
   protected DisplayWindow parent_;
   private HashSet<SettingsLinker> linkedLinkers_;
   private List<Class<?>> relevantEvents_;
   private LinkButton button_;
   private boolean isActive_;

   public SettingsLinker(DisplayWindow parent,
         List<Class<?>> relevantEventClasses) {
      parent_ = parent;
      isActive_ = false;
      linkedLinkers_ = new HashSet<SettingsLinker>();
      relevantEvents_ = relevantEventClasses;
   }

   /**
    * NOTE: this must be called before the button is interacted with!
    * Preferably in the LinkButton constructor.
    */
   public void setButton(LinkButton button) {
      button_ = button;
      button_.setVisible(linkedLinkers_.size() > 0);
   }

   /**
    * Establish a connection with another SettingsLinker. The connection
    * is reciprocal. This method just calls our extra link() method below.
    */
   public void link(SettingsLinker linker) {
      link(linker, true);
   }

   /**
    * Establish a link with another SettingsLinker. If we're enabled, then we
    * also push our state across to the new linker. Either way, we also tell
    * them to link to us. The isSource boolean prevents redundant calls to
    * pushState().
    */
   public void link(SettingsLinker linker, boolean isSource) {
      // Don't link ourselves; just avoids some redundant pushing in
      // pushChanges().
      if (linker != this && !linkedLinkers_.contains(linker) &&
            linker.getID() == getID()) {
         // Ensure that their link state matches our own.
         linker.setIsActive(isActive_);
         if (isActive_ && isSource) {
            // Take this opportunity to push our state across to them.
            pushState(linker.getDisplay());
         }
         linkedLinkers_.add(linker);
         linker.link(this, false);
         // Now that we can link to someone, show our button.
         button_.setVisible(true);
      }
   }

   /**
    * Remove a connection with another SettingsLinker, and also remove the
    * reciprocal connection.
    */
   public void unlink(SettingsLinker linker) {
      if (linkedLinkers_.contains(linker)) {
         linkedLinkers_.remove(linker);
         linker.unlink(this);
         if (linkedLinkers_.size() == 0) {
            // No more links; hide our button and turn off our linkedness.
            button_.setVisible(false);
            button_.setSelected(false);
            setIsActive(false);
         }
      }
   }

   /**
    * Remove all links for this linker.
    */
   public void unlinkAll() {
      // Make a separate container since unlinking modifies linkedListers_.
      HashSet<SettingsLinker> linkers = new HashSet<SettingsLinker>(linkedLinkers_);
      for (SettingsLinker linker : linkers) {
         unlink(linker);
      }
   }

   /**
    * Turn on propagation of events for this linker, so that when our parent's
    * DisplaySettings change, we will push updates to our connected
    * SettingsLinkers.
    */
   public void setIsActive(boolean isActive) {
      if (isActive == isActive_) {
         return;
      }
      isActive_ = isActive;
      button_.setSelected(isActive);
      // Ensure that our linked linkers also get updated.
      for (SettingsLinker linker : linkedLinkers_) {
         if (linker.getIsActive() != isActive_) {
            linker.setIsActive(isActive_);
         }
      }
   }

   /**
    * Return whether or not we should apply changes, that originate from our
    * display, to other displays.
    */
   public boolean getIsActive() {
      return isActive_;
   }

   /**
    * Push the provided event to the linkers we are connected to -- only if
    * the event is one of the event classes we care about, and we are
    * currently linked. Note that the event is assumed to originate from our
    * parent, so we don't apply it to ourselves.
    */
   public void pushEvent(DisplayWindow source, DisplaySettingsEvent event) {
      if (!isActive_) {
         return;
      }
      boolean isRelevant = false;
      for (Class<?> eventClass : relevantEvents_) {
         if (eventClass.isInstance(event)) {
            isRelevant = true;
            break;
         }
      }
      if (!isRelevant) {
         return;
      }

      for (SettingsLinker linker : linkedLinkers_) {
         if (linker.getShouldApplyChanges(source, event)) {
            linker.applyChange(source, event);
         }
      }
   }

   /**
    * Apply the relevant parts of our DisplaySettings to the provided display.
    */
   public void pushState(DisplayWindow display) {
      DisplaySettings newSettings = copySettings(parent_,
            parent_.getDisplaySettings(), display.getDisplaySettings());
      if (newSettings != display.getDisplaySettings()) {
         // I.e. copySettings() actually made a change.
         display.setDisplaySettings(newSettings);
      }
   }

   /**
    * Return our DisplayWindow, which may be needed e.g. to provide access to
    * the Datastore for another SettingsLinker.
    */
   public DisplayWindow getDisplay() {
      return parent_;
   }

   /**
    * Copy from source to dest the portion of the DisplaySettings that we care
    * about. Should return the dest parameter unmodified if no change needs
    * to occur.
    */
   public abstract DisplaySettings copySettings(DisplayWindow sourceDisplay,
         DisplaySettings source, DisplaySettings dest);

   /**
    * Return true iff the given DisplaySettingsEvent represents a change that
    * we need to apply to our own DisplayWindow.
    */
   public abstract boolean getShouldApplyChanges(DisplayWindow source,
         DisplaySettingsEvent changeEvent);

   /**
    * Apply the change indicated by the provided DisplaySettingsEvent to
    * our own DisplayWindow.
    */
   public abstract void applyChange(DisplayWindow source,
         DisplaySettingsEvent changeEvent);

   /**
    * Generate a semi-unique ID for this linker; it should indicate the
    * specific property, sub-property, or group of properties that this linker
    * handles synchronization for.
    */
   public abstract int getID();
}
