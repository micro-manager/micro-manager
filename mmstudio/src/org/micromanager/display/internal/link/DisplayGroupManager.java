package org.micromanager.display.internal.link;

import com.google.common.eventbus.Subscribe;

import java.awt.GraphicsConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.micromanager.display.DisplayManager;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.NewDisplayEvent;

import org.micromanager.display.internal.DisplayDestroyedEvent;
import org.micromanager.display.internal.events.FullScreenEvent;

import org.micromanager.internal.MMStudio;

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
      public void onLinkButton(LinkButtonEvent event) {
         master_.onLinkButton(display_, event);
      }

      @Subscribe
      public void onFullScreen(FullScreenEvent event) {
         master_.onFullScreen(display_, event);
      }

      @Subscribe
      public void onDisplayDestroyed(DisplayDestroyedEvent event) {
         master_.onDisplayDestroyed(display_, event);
      }

      public DisplayWindow getDisplay() {
         return display_;
      }
   }

   private MMStudio studio_;
   private HashMap<DisplayWindow, HashSet<SettingsLinker>> displayToLinkers_;
   private HashMap<SettingsLinker, Boolean> linkerToIsLinked_;
   private HashMap<GraphicsConfiguration, DisplayWindow> screenToDisplay_;
   private ArrayList<WindowListener> listeners_;

   public DisplayGroupManager(MMStudio studio) {
      displayToLinkers_ = new HashMap<DisplayWindow, HashSet<SettingsLinker>>();
      linkerToIsLinked_ = new HashMap<SettingsLinker, Boolean>();
      screenToDisplay_ = new HashMap<GraphicsConfiguration, DisplayWindow>();
      listeners_ = new ArrayList<WindowListener>();
      studio_ = studio;
      // So we can be notified of newly-created displays.
      studio_.registerForEvents(this);
   }

   /**
    * A new display has arrived; register for its events.
    */
   @Subscribe
   public void onNewDisplay(NewDisplayEvent event) {
      DisplayWindow display = event.getDisplayWindow();
      listeners_.add(new WindowListener(display, this));
      displayToLinkers_.put(display, new HashSet<SettingsLinker>());
   }

   /**
    * A display was destroyed; stop tracking it.
    */
   public void onDisplayDestroyed(DisplayWindow source,
         DisplayDestroyedEvent event) {
      for (SettingsLinker linker : displayToLinkers_.get(source)) {
         linkerToIsLinked_.remove(linker);
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
   }

   /**
    * A new LinkButton has been created; we need to start tracking its linker.
    */
   public void onNewLinkButton(DisplayWindow source,
         LinkButtonCreatedEvent event) {
      try {
         displayToLinkers_.get(source).add(event.getLinker());
         linkerToIsLinked_.put(event.getLinker(), false);
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Couldn't handle new link button for display " + source.hashCode() + " linker " + event.getLinker().getID());
      }
   }

   /**
    * A LinkButton has been clicked. Ensure other DisplayWindows get their
    * corresponding LinkButtons updated, and update our linker tracking.
    */
   public void onLinkButton(DisplayWindow source, LinkButtonEvent event) {
      try {
         // Find other displays for this datastore and update their
         // corresponding link buttons.
         SettingsLinker linker = event.getLinker();
         boolean isLinked = event.getIsLinked();
         RemoteLinkEvent notifyEvent = new RemoteLinkEvent(linker, isLinked);
         // Not just this linker, but all others with the same ID and datastore
         // need to be updated.
         linkerToIsLinked_.put(linker, isLinked);
         for (DisplayWindow display : displayToLinkers_.keySet()) {
            if (display == source ||
                  display.getDatastore() != source.getDatastore()) {
               // No need to notify this one.
               continue;
            }
            display.postEvent(notifyEvent);

            for (SettingsLinker alt : displayToLinkers_.get(display)) {
               if (alt.getID() == linker.getID() && display != source) {
                  linkerToIsLinked_.put(alt, isLinked);
               }
            }
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Couldn't redistribute LinkButtonEvent");
      }
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
      if (screenToDisplay_.containsKey(sourceConfig)) {
         if (isFullScreen) {
            // Enabling fullscreen mode for a monitor that already has a
            // fullscreen DisplayWindow; remove that one first.
            screenToDisplay_.get(sourceConfig).forceClosed();
         }
         else if (source == screenToDisplay_.get(sourceConfig)) {
            // Disabling fullscreen mode; stop tracking this display.
            screenToDisplay_.remove(sourceConfig);
         }
         else {
            ReportingUtils.logError("Disabling fullscreen mode for a display we didn't realize was in fullscreen mode; this should never happen!");
         }
      }

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
    * One DisplayWindow has changed settings; check if others care.
    */
   public void onDisplaySettingsChanged(DisplayWindow source,
         DisplaySettingsEvent event) {
      try {
         for (DisplayWindow display : displayToLinkers_.keySet()) {
            if (display == source) {
               continue;
            }
            for (SettingsLinker linker : displayToLinkers_.get(display)) {
               if (!linkerToIsLinked_.get(linker)) {
                  // This linker isn't active.
                  continue;
               }
               List<Class<?>> classes = linker.getRelevantEventClasses();
               for (Class<?> eventClass : classes) {
                  if (eventClass.isInstance(event) &&
                        linker.getShouldApplyChanges(event)) {
                     linker.applyChange(event);
                     break;
                  }
               }
            }
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error dispatching settings change event");
      }
   }
}
