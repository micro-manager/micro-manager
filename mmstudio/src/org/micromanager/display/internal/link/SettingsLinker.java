///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.display.internal.link;

import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.DisplayDestroyedEvent;


/**
 * This class is for setting up links across DisplayWindows for specific
 * attributes of the DisplaySettings. Extensions of this class will be specific
 * to certain types of the DisplaySettings.
 */
public abstract class SettingsLinker {
   // This object tracks the different sets of "sibling" relationships, i.e.
   // all of the extant SettingsLinkers that have the same IDs.
   private static HashMap<Integer, HashSet<SettingsLinker>> idToSiblings_ = new HashMap<Integer, HashSet<SettingsLinker>>();
   // This object tracks the different groupings of synchronized
   // SettingsLinkers. Similar to the above, except that there can be multiple
   // groups for any given ID. Every SettingsLinker in a group is linked to all
   // other SettingsLinkers in the group.
   private static HashMap<Integer, ArrayList<HashSet<SettingsLinker>>> idToSynchroGroups_ = new HashMap<Integer, ArrayList<HashSet<SettingsLinker>>>();

   // This is used to sort SettingsLinkers by getSortedSiblings().
   private static final Comparator<SettingsLinker> LINKER_SORTER =
         new Comparator<SettingsLinker>() {
            @Override
            public int compare(SettingsLinker s1, SettingsLinker s2) {
               return s1.getDisplay().getName().compareTo(s2.getDisplay().getName());
            }
         };

   protected DisplayWindow parent_;
   private List<Class<?>> relevantEvents_;
   private HashSet<LinkButton> buttons_;
   private boolean isActive_;

   public SettingsLinker(DisplayWindow parent,
         List<Class<?>> relevantEventClasses) {
      parent_ = parent;
      parent_.registerForEvents(this);
      isActive_ = false;
      relevantEvents_ = relevantEventClasses;
      buttons_ = new HashSet<LinkButton>();
   }

   /**
    * Add us to the sibling set for our ID. This method must be called once
    * your constructor is finished; it can't be run from the root
    * SettingsLinker constructor as getID() presumably depends on values
    * that have not been set at that point.
    * TODO: detect when this method hasn't been called and throw an error when
    * someone tries to use this linker.
    */
   protected void addToSiblings() {
      if (!idToSiblings_.containsKey(getID())) {
         // First SettingsLinker of this type.
         HashSet<SettingsLinker> siblings = new HashSet<SettingsLinker>();
         idToSiblings_.put(getID(), siblings);
      }
      HashSet<SettingsLinker> siblings = idToSiblings_.get(getID());
      siblings.add(this);
      if (siblings.size() > 1) {
         // Have at least two linkers for this property, so they can be
         // visible.
         for (SettingsLinker linker : siblings) {
            linker.setVisible(true);
         }
      }
   }

   /**
    * Return the group of SettingsLinkers that this linker is synchronized
    * with, or null if it is not synchronized with any.
    */
   private HashSet<SettingsLinker> getSynchroGroup(SettingsLinker linker) {
      if (!idToSynchroGroups_.containsKey(linker.getID())) {
         return null;
      }
      for (HashSet<SettingsLinker> group : idToSynchroGroups_.get(linker.getID())) {
         if (group.contains(linker)) {
            return group;
         }
      }
      return null;
   }

   /**
    * NOTE: this must be called before the button is interacted with!
    * Preferably in the LinkButton constructor.
    */
   public void addButton(LinkButton button) {
      buttons_.add(button);
      button.setVisible(idToSiblings_.get(getID()).size() > 1);
   }

   /**
    * This should be called when the LinkButton is removed.
    */
   public void removeButton(LinkButton button) {
      buttons_.remove(button);
   }

   /**
    * Return a list of all SettingsLinkers we can link with. Results are
    * sorted by display name.
    */
   public List<SettingsLinker> getSortedSiblings() {
      ArrayList<SettingsLinker> result = new ArrayList<SettingsLinker>();
      for (SettingsLinker sibling : idToSiblings_.get(getID())) {
         if (sibling != this) {
            result.add(sibling);
         }
      }
      Collections.sort(result, LINKER_SORTER);
      return result;
   }

   /**
    * Start synchronizing with another SettingsLinker; set up or merge
    * synchro groups, and push our state across to the other linker.
    */
   public void synchronize(SettingsLinker linker) {
      if (linker == this || linker.getID() != getID()) {
         // Not a valid linkage.
         return;
      }

      // Ensure that their link state matches our own.
      setIsActive(true);
      linker.setIsActive(true);
      if (!idToSynchroGroups_.containsKey(getID())) {
         // Make a new list of synchro groups.
         idToSynchroGroups_.put(getID(), new ArrayList<HashSet<SettingsLinker>>());
      }
      // Figure out if we need to create a new group, add to an existing group,
      // or merge two groups.
      HashSet<SettingsLinker> ourGroup = getSynchroGroup(this);
      if (ourGroup != null && ourGroup.contains(linker)) {
         // Already linked to them.
         return;
      }
      HashSet<SettingsLinker> altGroup = getSynchroGroup(linker);
      if (ourGroup == null && altGroup == null) {
         // Create a new group containing us both.
         HashSet<SettingsLinker> newGroup = new HashSet<SettingsLinker>();
         newGroup.add(this);
         newGroup.add(linker);
         idToSynchroGroups_.get(getID()).add(newGroup);
      }
      else if (ourGroup != null && altGroup == null) {
         // Add them to our group.
         ourGroup.add(linker);
      }
      else if (ourGroup == null && altGroup != null) {
         // Add us to their group.
         altGroup.add(this);
      }
      else {
         // Merge two groups.
         ourGroup.addAll(altGroup);
         idToSynchroGroups_.get(getID()).remove(altGroup);
      }

      pushState(linker.getDisplay());
   }

   /**
    * Eject a SettingsLinker from our synchronization group. This automatically
    * deactivates them.
    */
   public void desynchronize(SettingsLinker linker) {
      HashSet<SettingsLinker> group = getSynchroGroup(linker);
      if (group != null) {
         group.remove(linker);
         if (group.size() <= 1) {
            // Time to disband this group.
            for (SettingsLinker alt : group) {
               alt.setIsActive(false);
            }
            idToSynchroGroups_.get(getID()).remove(group);
         }
      }
      linker.setIsActive(false);
   }

   /**
    * Remove all references to this linker.
    */
   public void destroy() {
      HashSet<SettingsLinker> siblings = idToSiblings_.get(getID());
      if (siblings == null) {
         // We're the only linker of this type.
         return;
      }
      siblings.remove(this);
      if (siblings.size() <= 1) {
         // Time to hide our sibling's link button.
         for (SettingsLinker linker : siblings) {
            linker.setVisible(false);
         }
      }
      HashSet<SettingsLinker> group = getSynchroGroup(this);
      if (group == null) {
         // All done.
         return;
      }
      group.remove(this);
      if (group.size() <= 1) {
         // Time to remove this group.
         idToSynchroGroups_.get(getID()).remove(group);
         for (SettingsLinker linker : group) {
            linker.setIsActive(false);
         }
      }
   }

   /**
    * Toggle the state of our button, indicating whether or not we're currently
    * pushing state changes to the SettingsLinkers we're synchronized with.
    */
   public void setIsActive(boolean isActive) {
      if (isActive == isActive_) {
         return;
      }
      isActive_ = isActive;
      for (LinkButton button : buttons_) {
         button.setActive(isActive);
      }
   }

   /**
    * Set visibility of our button.
    */
   public void setVisible(boolean isVisible) {
      for (LinkButton button : buttons_) {
         button.setVisible(isVisible);
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
    * Returns true if we are currently synchronized with the given linker.
    */
   public boolean getIsSynchronized(SettingsLinker sibling) {
      HashSet<SettingsLinker> group = getSynchroGroup(this);
      return group != null && group.contains(sibling);
   }

   /**
    * Push the provided event to the linkers we are connected to -- only if
    * the event is one of the event classes we care about.
    */
   public void pushEvent(DisplayWindow source, DisplaySettingsEvent event) {
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

      HashSet<SettingsLinker> group = getSynchroGroup(this);
      if (group == null) {
         // Nobody to push to.
         return;
      }
      for (SettingsLinker linker : group) {
         if (linker.getDisplay() == source) {
            // Don't push the event to the originator.
            continue;
         }
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

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      parent_.unregisterForEvents(this);
      // Note that the SettingsGroupManager will call our destroy() method.
   }

   /**
    * Return our DisplayWindow, which may be needed e.g. to provide access to
    * the Datastore for another SettingsLinker.
    */
   public DisplayWindow getDisplay() {
      return parent_;
   }

   @Override
   public String toString() {
      return String.format("<%s with ID %s>", getClass().getName(), getID());
   }

   /**
    * Provide a short textual description of the propert[y|ies] this linker
    * controls. This is used in the link button menu instructions.
    */
   public abstract String getProperty();

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
