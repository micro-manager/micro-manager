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

package org.micromanager.display.internal;

import com.google.common.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;

import org.micromanager.display.ControlsFactory;
import org.micromanager.display.DisplayManager;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.RequestToCloseEvent;
import org.micromanager.display.RequestToDrawEvent;
import org.micromanager.display.OverlayPanel;
import org.micromanager.display.OverlayPanelFactory;
import org.micromanager.display.OverlayPlugin;
import org.micromanager.display.internal.events.DefaultRequestToDrawEvent;
import org.micromanager.display.internal.events.NewOverlayEvent;

import org.micromanager.events.DatastoreClosingEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.events.NewDisplayEvent;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

import org.micromanager.data.internal.DefaultPropertyMap;

import org.micromanager.PropertyMap;


public final class DefaultDisplayManager implements DisplayManager {
   private static DefaultDisplayManager staticInstance_;

   private final MMStudio studio_;
   private final HashMap<Datastore, ArrayList<DisplayWindow>> storeToDisplays_;
   private LinkedHashMap<String, OverlayPanelFactory> titleToOverlay_;
   private final ArrayList<DisplayWindow> allDisplays_;

   public DefaultDisplayManager(MMStudio studio) {
      studio_ = studio;
      storeToDisplays_ = new HashMap<Datastore, ArrayList<DisplayWindow>>();
      allDisplays_ = new ArrayList<DisplayWindow>();
      studio_.events().registerForEvents(this);
      staticInstance_ = this;
   }

   @Override
   public Datastore show(Image image) {
      Datastore result = studio_.data().createRAMDatastore();
      try {
         result.putImage(image);
      }
      catch (DatastoreFrozenException e) {
         // This should never happen.
         ReportingUtils.showError(e, "Somehow managed to create an immediately-frozen RAM datastore.");
      }
      createDisplay(result);
      return result;
   }

   @Override
   public List<Datastore> getManagedDatastores() {
      return new ArrayList<Datastore>(storeToDisplays_.keySet());
   }

   @Override
   public void manage(Datastore store) {
      // Iterate over all display windows, find those associated with this
      // datastore, and manually associate them now.
      ArrayList<DisplayWindow> displays = new ArrayList<DisplayWindow>();
      for (DisplayWindow display : getAllImageWindows()) {
         if (display.getDatastore() == store) {
            displays.add(display);
            display.registerForEvents(this);
         }
      }
      storeToDisplays_.put(store, displays);
   }

   @Override
   public boolean getIsManaged(Datastore store) {
      return storeToDisplays_.containsKey(store);
   }

   /**
    * When a Datastore is closed, we need to remove all references to it so
    * it can be garbage-collected.
    * @param event
    */
   @Subscribe
   public void onDatastoreClosed(DatastoreClosingEvent event) {
      Datastore store = event.getDatastore();
      if (storeToDisplays_.containsKey(store)) {
         ArrayList<DisplayWindow> displays = storeToDisplays_.get(store);
         for (DisplayWindow display : displays) {
            display.forceClosed();
         }
         storeToDisplays_.remove(store);
      }
   }

   @Override
   public DisplaySettings getStandardDisplaySettings() {
      return DefaultDisplaySettings.getStandardSettings();
   }

   @Override
   public DisplaySettings.DisplaySettingsBuilder getDisplaySettingsBuilder() {
      return new DefaultDisplaySettings.Builder();
   }

   @Override
   public PropertyMap.PropertyMapBuilder getPropertyMapBuilder() {
      return new DefaultPropertyMap.Builder();
   }

   @Override
   public DisplayWindow createDisplay(Datastore store) {
      return DefaultDisplayWindow.createDisplay(store, null);
   }

   @Override
   public DisplayWindow createDisplay(Datastore store,
         ControlsFactory factory) {
      return DefaultDisplayWindow.createDisplay(store, factory);
   }

   @Override
   public RequestToDrawEvent createRequestToDrawEvent(Coords coords) {
      return new DefaultRequestToDrawEvent(coords);
   }

   @Override
   public List<DisplayWindow> loadDisplays(Datastore store) {
      String path = store.getSavePath();
      ArrayList<DisplayWindow> result = new ArrayList<DisplayWindow>();
      if (path != null) {
         List<DisplaySettings> allSettings = DefaultDisplaySettings.load(path);
         for (DisplaySettings settings : allSettings) {
            DisplayWindow tmp = createDisplay(store);
            tmp.setDisplaySettings(settings);
            result.add(tmp);
         }
      }
      if (result.size() == 0) {
         // No path, or no display settings at the path.  Just create a blank
         // new display.
         result.add(createDisplay(store));
      }
      return result;
   }

   @Override
   public List<DisplayWindow> getDisplays(Datastore store) {
      return storeToDisplays_.get(store);
   }

   @Override
   public DisplayWindow getCurrentWindow() {
      return DefaultDisplayWindow.getCurrentWindow();
   }

   @Override
   public List<DisplayWindow> getAllImageWindows() {
      return new ArrayList<DisplayWindow>(allDisplays_);
   }

   @Override
   public boolean closeDisplaysFor(Datastore store) {
      for (DisplayWindow display : getAllImageWindows()) {
         if (display.getDatastore() == store) {
            if (!display.requestToClose()) {
               // Fail out immediately; don't try to close other displays.
               return false;
            }
         }
      }
      return true;
   }

   public OverlayPanel createOverlayPanel(String title) {
      OverlayPanelFactory factory = titleToOverlay_.get(title);
      OverlayPanel panel = factory.createOverlayPanel();
      panel.setManager(this);
      return panel;
   }

   /**
    * Load all plugins of the OverlayPlugin type.
    */
   private void loadOverlayPlugins() {
      HashMap<String, OverlayPlugin> plugins = studio_.plugins().getOverlayPlugins();
      for (String key : plugins.keySet()) {
         OverlayPlugin plugin = plugins.get(key);
         titleToOverlay_.put(key, plugin.createFactory());
      }
      // HACK: hardcode in the scalebar and timestamp overlays for now.
      titleToOverlay_.put("Scale Bar", new ScaleBarOverlayFactory());
      titleToOverlay_.put("Timestamp", new TimestampOverlayFactory());
   }

   public String[] getOverlayTitles() {
      if (titleToOverlay_ == null) {
         // Time to load overlays now.
         titleToOverlay_ = new LinkedHashMap<String, OverlayPanelFactory>();
         loadOverlayPlugins();
      }
      ArrayList<String> result = new ArrayList<String>();
      for (Map.Entry<String, OverlayPanelFactory> entry : titleToOverlay_.entrySet()) {
         result.add(entry.getKey());
      }
      return result.toArray(new String[result.size()]);
   }

   /**
    * Check if this is the last display for a Datastore that we are managing,
    * and verify closing without saving (if appropriate).
    */
   @Subscribe
   public void onRequestToClose(RequestToCloseEvent event) {
      DisplayWindow display = event.getDisplay();
      Datastore store = display.getDatastore();
      if (!storeToDisplays_.containsKey(store)) {
         // This should never happen.
         ReportingUtils.logError("Somehow got notified of a request to close for a display that isn't associated with a datastore that we are managing.");
         return;
      }
      ArrayList<DisplayWindow> displays = storeToDisplays_.get(store);
      if (!displays.contains(display)) {
         // This should also never happen.
         ReportingUtils.logError("Got notified of a request to close for a display that we didn't know was associated with datastore " + store);
      }

      if (displays.size() > 1) {
         // Not last display, so it's fine to remove it.
         removeDisplay(display);
         return;
      }
      // Last display; check for saving now.
      if (store.getSavePath() != null) {
         // No problem with saving.
         removeDisplay(display);
         return;
      }
      // Prompt the user to save their data.
      if (promptToSave(store, display)) {
         removeDisplay(display);
         store.freeze();
         // This will invoke our onDatastoreClosed() method.
         store.close();
      }
   }

   @Override
   public boolean promptToSave(Datastore store, DisplayWindow display) {
      String[] options = {"Save", "Discard", "Cancel"};
      int result = JOptionPane.showOptionDialog(display.getAsWindow(),
            "Do you want to save this data set before closing?",
            "MicroManager", JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
      if (result == 2) {
         // User cancelled.
         return false;
      }
      if (result == 0) { // I.e. not the "discard" option
         if (!store.save(display.getAsWindow())) {
            // Don't close the window, as saving failed.
            return false;
         }
      }
      return true;
   }

   @Override
   public boolean closeAllDisplayWindows(boolean shouldPromptToSave) {
      for (DisplayWindow display : getAllImageWindows()) {
         if (shouldPromptToSave && !display.requestToClose()) {
            // User cancelled closing.
            return false;
         }
         else if (!shouldPromptToSave) {
            // Force display closed.
            display.forceClosed();
         }
      }
      return true;
   }

   private void removeDisplay(DisplayWindow display) {
      Datastore store = display.getDatastore();
      ArrayList<DisplayWindow> displays = storeToDisplays_.get(store);
      displays.remove(display);
      display.forceClosed();
   }

   /**
    * Newly-created DisplayWindows should be associated with their Datastores
    * if the Datastore is being managed.
    */
   @Subscribe
   public void onNewDisplayEvent(NewDisplayEvent event) {
      DisplayWindow display = event.getDisplay();
      Datastore store = display.getDatastore();
      if (getIsManaged(store)) {
         storeToDisplays_.get(store).add(display);
         display.registerForEvents(this);
      }
      allDisplays_.add(display);
   }

   /**
    * Ensure that we don't think the display still exists.
    */
   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      DisplayWindow display = event.getDisplay();
      Datastore store = display.getDatastore();
      if (getIsManaged(store)) {
         storeToDisplays_.get(store).remove(display);
      }
      stopTrackingDisplay(display);
   }

   /**
    * A display is going away, so we should stop listing it in allDisplays.
    */
   public static void stopTrackingDisplay(DisplayWindow display) {
      if (staticInstance_.allDisplays_.contains(display)) {
         staticInstance_.allDisplays_.remove(display);
      }
      else {
         // This should never happen.
         ReportingUtils.logError("DisplayManager informed of destruction of display it didn't know existed.");
      }
   }

   public static DefaultDisplayManager getInstance() {
      return staticInstance_;
   }
}
