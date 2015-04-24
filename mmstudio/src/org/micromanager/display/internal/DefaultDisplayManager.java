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
   private final LinkedHashMap<String, OverlayPanelFactory> titleToOverlay_;

   public DefaultDisplayManager(MMStudio studio) {
      studio_ = studio;
      storeToDisplays_ = new HashMap<Datastore, ArrayList<DisplayWindow>>();
      titleToOverlay_ = new LinkedHashMap<String, OverlayPanelFactory>();
      // HACK: start out with some hardcoded overlay options for now.
      registerOverlay(new ScaleBarOverlayFactory());
      registerOverlay(new TimestampOverlayFactory());
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
      else {
         // Just create a blank new display.
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
      return DefaultDisplayWindow.getAllImageWindows();
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

   @Override
   public void registerOverlay(OverlayPanelFactory factory) {
      String title = factory.getTitle();
      if (titleToOverlay_.containsKey(title)) {
         throw new RuntimeException("Overlay title " + title + " is already in use");
      }
      titleToOverlay_.put(title, factory);
      DefaultEventManager.getInstance().post(new NewOverlayEvent(factory));
   }

   public OverlayPanel createOverlayPanel(String title) {
      OverlayPanelFactory factory = titleToOverlay_.get(title);
      OverlayPanel panel = factory.createOverlayPanel();
      panel.setManager(this);
      return panel;
   }

   public String[] getOverlayTitles() {
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
      String[] options = {"Save as Separate Files", "Save as Single File",
         "Discard", "Cancel"};
      int result = JOptionPane.showOptionDialog(display.getAsWindow(),
            "Do you want to save this data set before closing?",
            "MicroManager", JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
      if (result == 3) {
         // User cancelled.
         return false;
      }
      Datastore.SaveMode mode = Datastore.SaveMode.MULTIPAGE_TIFF;
      if (result == 0) {
         mode = Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES;
      }
      if (result != 2) { // I.e. not the "discard" option
         if (!store.save(mode, display.getAsWindow())) {
            // Don't close the window, as saving failed.
            return false;
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
   }

   public static DefaultDisplayManager getInstance() {
      return staticInstance_;
   }
}
