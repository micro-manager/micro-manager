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

import java.awt.GraphicsConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.micromanager.data.Datastore;
import org.micromanager.display.DisplayWindow;

import org.micromanager.display.internal.DefaultDisplayWindow;
import org.micromanager.display.internal.DisplayDestroyedEvent;
import org.micromanager.display.internal.events.FullScreenEvent;
import org.micromanager.events.internal.DefaultEventManager;

import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class is responsible for tracking groupings of DisplayWindows and
 * handling synchronization of linked DisplaySettings across them. It also
 * handles any other logic that requires DisplayWindows to work together
 * (e.g. toggling fullscreen mode).
 */
public class DisplayGroupManager {
   /**
    * This class listens to events from a specific DisplayWindow, and forwards
    * them to the DisplayGroupManager, so it can know which window originated
    * which event.
    */
   private class WindowListener {
      private DisplayWindow display_;
      private DisplayGroupManager master_;
      public WindowListener(DisplayWindow display, DisplayGroupManager master) {
         display_ = display;
         master_ = master;
         display_.registerForEvents(this);
      }

      @Subscribe
      public void onDisplaySettingsChanged(DisplaySettingsEvent event) {
         master_.onDisplaySettingsChanged(display_, event);
      }

      @Subscribe
      public void onFullScreen(FullScreenEvent event) {
         master_.onFullScreen(display_, event);
      }

      @Subscribe
      public void onDisplayDestroyed(DisplayDestroyedEvent event) {
         display_.unregisterForEvents(this);
         master_.onDisplayDestroyed(display_, event);
      }

      @Subscribe
      public void onLinkerRemoved(LinkerRemovedEvent event) {
         master_.onLinkerRemoved(display_, event);
      }

      public DisplayWindow getDisplay() {
         return display_;
      }
   }

   private static DisplayGroupManager staticInstance_;
   static {
      staticInstance_ = new DisplayGroupManager();
   }

   public static DisplayGroupManager getInstance() {
      return staticInstance_;
   }

   /**
    * This class tracks linkers for a single DisplayWindow.
    */
   private class LinkersGroup {
      private HashSet<SettingsLinker> genericLinkers_;
      // As a special case, we maintain the ContrastLinkers for each
      // DisplayWindow. These are special because they aren't stored in the
      // DisplayWindow itself, but in the InspectorFrame; consequently, there
      // can be multiple LinkButtons all working with the same ContrastLinker.
      // This means they need to be stored centrally somewhere, and that place
      // is here.
      private HashMap<Integer, ContrastLinker> channelToContrastLinker_;
      private DisplayWindow display_;

      public LinkersGroup(DisplayWindow display) {
         genericLinkers_ = new HashSet<SettingsLinker>();
         channelToContrastLinker_ = new HashMap<Integer, ContrastLinker>();
         display_ = display;
      }

      public HashSet<SettingsLinker> getLinkers() {
         return genericLinkers_;
      }

      /**
       * Retrieve the specified ContrastLinker. Create it if it doesn't already
       * exist.
       */
      public ContrastLinker getContrastLinker(int channel) {
         if (!channelToContrastLinker_.containsKey(channel)) {
            ContrastLinker linker = new ContrastLinker(channel, display_);
            channelToContrastLinker_.put(channel, linker);
         }
         return channelToContrastLinker_.get(channel);
      }
   }

   // Keeps track of which displays have which SettingsLinkers, so we can
   // propagate changes to linkers, and clean up when displays go away. This
   // also serves as a convenient container of all displays.
   private HashMap<DisplayWindow, LinkersGroup> displayToLinkers_;
   // Tracks which screen (a.k.a. monitor) a given window is on.
   private HashMap<GraphicsConfiguration, DisplayWindow> screenToDisplay_;
   private HashMap<Datastore, ArrayList<DisplayWindow>> storeToDisplays_;
   private ArrayList<WindowListener> listeners_;

   public DisplayGroupManager() {
      displayToLinkers_ = new HashMap<DisplayWindow, LinkersGroup>();
      screenToDisplay_ = new HashMap<GraphicsConfiguration, DisplayWindow>();
      storeToDisplays_ = new HashMap<Datastore, ArrayList<DisplayWindow>>();
      listeners_ = new ArrayList<WindowListener>();
      DefaultEventManager.getInstance().registerForEvents(this);
   }

   /**
    * A new display has arrived; register for its events, and add it to our
    * list of displays for its datastore.
    */
   public void addDisplay(DisplayWindow display) {
      listeners_.add(new WindowListener(display, this));
      displayToLinkers_.put(display, new LinkersGroup(display));
      Datastore store = display.getDatastore();
      if (!storeToDisplays_.containsKey(store)) {
         storeToDisplays_.put(store, new ArrayList<DisplayWindow>());
      }
      ArrayList<DisplayWindow> displays = storeToDisplays_.get(store);
      displays.add(display);
      if (displays.size() > 1) {
         // Multiple displays; numbers in titles need to be shown.
         for (DisplayWindow alt : displays) {
            ((DefaultDisplayWindow) alt).resetTitle();
         }
      }
   }

   /**
    * A display was destroyed; stop tracking it, and remove siblings from
    * linkers.
    */
   public void onDisplayDestroyed(DisplayWindow source,
         DisplayDestroyedEvent event) {
      try {
         for (SettingsLinker linker : displayToLinkers_.get(source).getLinkers()) {
            linker.destroy();
         }
         displayToLinkers_.remove(source);
         for (WindowListener listener : listeners_) {
            if (listener.getDisplay() == source) {
               listeners_.remove(listener);
               break;
            }
         }

         // Check if we were tracking this display in screenToDisplay_.
         for (GraphicsConfiguration config : screenToDisplay_.keySet()) {
            if (screenToDisplay_.get(config) == source) {
               screenToDisplay_.remove(config);
               break;
            }
         }

         // Remove from our datastore-based tracking, and update other
         // displays' titles if necessary.
         Datastore store = source.getDatastore();
         if (storeToDisplays_.containsKey(store)) {
            ArrayList<DisplayWindow> displays = storeToDisplays_.get(store);
            displays.remove(source);
            if (displays.size() == 1) {
               // Back down to one display; hide display numbers.
               ((DefaultDisplayWindow) displays.get(0)).resetTitle();
            }
            else if (displays.size() == 0) {
               // Stop tracking.
               storeToDisplays_.remove(store);
            }
         }
         else {
            ReportingUtils.logError("Display was destroyed, but somehow we don't know about its datastore.");
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error when cleaning up after destroyed display");
      }
   }

   /**
    * A new LinkButton has been created; we need to start tracking its linker.
    */
   public void addNewLinker(SettingsLinker newLinker, DisplayWindow source) {
      displayToLinkers_.get(source).getLinkers().add(newLinker);
   }

   /**
    * A SettingsLinker is being removed.
    */
   public void onLinkerRemoved(DisplayWindow source,
         LinkerRemovedEvent event) {
      SettingsLinker linker = event.getLinker();
      // We might not have the display around any more if this was called
      // as a side-effect of the display being removed.
      if (displayToLinkers_.containsKey(source)) {
         displayToLinkers_.get(source).getLinkers().remove(linker);
      }
      linker.destroy();
   }

   /**
    * Retrieve a ContrastLinker.
    */
   public static ContrastLinker getContrastLinker(int channel, DisplayWindow display) {
      return staticInstance_.displayToLinkers_.get(display).getContrastLinker(channel);
   }

   /**
    * One display has gone fullscreen, or fullscreen mode has ended; show/hide
    * the other displays as appropriate.
    * TODO: we directly hide the DisplayWindows instead of letting them do it
    * themselves because the latter would require exposing show/hide logic in
    * the API, which seemed unnecessary, but is there a better way?
    */
   public void onFullScreen(DisplayWindow source, FullScreenEvent event) {
      boolean isFullScreen = event.getIsFullScreen();
      GraphicsConfiguration sourceConfig = event.getConfig();

      // Show all displays that aren't on the same screen as a fullscreen
      // display.
      HashSet<GraphicsConfiguration> bannedConfigs = new HashSet<GraphicsConfiguration>();
      if (isFullScreen) {
         bannedConfigs.add(sourceConfig);
         screenToDisplay_.put(sourceConfig, source);
      }
      for (GraphicsConfiguration altConfig : screenToDisplay_.keySet()) {
         // This conditional ensures we don't ban showing displays that are
         // on the same screen as a display that is leaving fullscreen mode.
         if (isFullScreen || altConfig != event.getConfig()) {
            // There's a fullscreen display on this monitor, so we can't show
            // other displays on it.
            bannedConfigs.add(altConfig);
         }
      }
      // These displays are already fullscreened; thus we shouldn't change
      // their visibility.
      HashSet<DisplayWindow> displaysToSkip = new HashSet<DisplayWindow>(screenToDisplay_.values());
      for (DisplayWindow display : displayToLinkers_.keySet()) {
         if (!displaysToSkip.contains(display)) {
            display.getAsWindow().setVisible(
                  !bannedConfigs.contains(display.getScreenConfig()));
         }
      }
   }

   /**
    * A DisplayWindow's DisplaySettings have changed; push those changes out
    * to linked displays via the SettingsLinkers.
    */
   public void onDisplaySettingsChanged(DisplayWindow source,
         DisplaySettingsEvent event) {
      if (!displayToLinkers_.containsKey(source)) {
         // Unlikely, but could happen when a display is cleared during Live
         // mode.
         return;
      }
      try {
         for (SettingsLinker linker : displayToLinkers_.get(source).getLinkers()) {
            linker.pushEvent(source, event);
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error dispatching settings change event");
      }
   }

   /**
    * Return all displays that we know about for the specified Datastore, or
    * an empty list if we aren't tracking it.
    */
   public static List<DisplayWindow> getDisplaysForDatastore(Datastore store) {
      if (staticInstance_.storeToDisplays_.containsKey(store)) {
         return staticInstance_.storeToDisplays_.get(store);
      }
      return new ArrayList<DisplayWindow>();
   }
}
