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
import org.micromanager.events.NewDisplayEvent;

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
      public void onNewLinkButton(LinkButtonCreatedEvent event) {
         master_.onNewLinkButton(display_, event);
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
   // We need to ensure this exists before any DisplayWindows post any events.
   // If we just used a static initializer (i.e. not explicitly invoked
   // anywhere, but just run when the module is first referred to), then that
   // wouldn't happen until after the first display is created, which is too
   // late. Instead, this method is called by the DefaultDisplayWindow
   // constructor early on. I don't like having the explicit linkage, but it
   // seems to be necessary.
   public static void ensureInitialized() {
      if (staticInstance_ == null) {
         staticInstance_ = new DisplayGroupManager();
      }
   }

   private HashMap<DisplayWindow, HashSet<SettingsLinker>> displayToLinkers_;
   private HashMap<GraphicsConfiguration, DisplayWindow> screenToDisplay_;
   private HashMap<Datastore, ArrayList<DisplayWindow>> storeToDisplays_;
   private ArrayList<WindowListener> listeners_;

   public DisplayGroupManager() {
      displayToLinkers_ = new HashMap<DisplayWindow, HashSet<SettingsLinker>>();
      screenToDisplay_ = new HashMap<GraphicsConfiguration, DisplayWindow>();
      storeToDisplays_ = new HashMap<Datastore, ArrayList<DisplayWindow>>();
      listeners_ = new ArrayList<WindowListener>();
      DefaultEventManager.getInstance().registerForEvents(this);
   }

   /**
    * A new display has arrived; register for its events, and add it to our
    * list of displays for its datastore.
    */
   @Subscribe
   public void onNewDisplay(NewDisplayEvent event) {
      DisplayWindow display = event.getDisplay();
      listeners_.add(new WindowListener(display, this));
      displayToLinkers_.put(display, new HashSet<SettingsLinker>());
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
    * A display was destroyed; stop tracking it, and unlink linkers.
    */
   public void onDisplayDestroyed(DisplayWindow source,
         DisplayDestroyedEvent event) {
      for (SettingsLinker linker : displayToLinkers_.get(source)) {
         linker.unlinkAll();
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

      // Remove from our datastore-based tracking, and update other displays'
      // titles if necessary.
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

   /**
    * A new LinkButton has been created; we need to start tracking its linker,
    * and all related linkers need to be linked to it.
    */
   public void onNewLinkButton(DisplayWindow source,
         LinkButtonCreatedEvent event) {
      SettingsLinker newLinker = event.getLinker();
      displayToLinkers_.get(source).add(newLinker);
      for (DisplayWindow display : displayToLinkers_.keySet()) {
         for (SettingsLinker linker : displayToLinkers_.get(display)) {
            if (linker.getID() == newLinker.getID() &&
                  linker != newLinker) {
               // This also creates the reciprocal link.
               linker.link(newLinker);
            }
         }
      }
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
         displayToLinkers_.get(source).remove(linker);
      }
      linker.unlinkAll();
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
         // There's a fullscreen display on this monitor, so we can't show
         // other displays on it.
         bannedConfigs.add(altConfig);
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
         for (SettingsLinker linker : displayToLinkers_.get(source)) {
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
