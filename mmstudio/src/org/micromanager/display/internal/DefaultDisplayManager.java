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
import java.util.List;

import javax.swing.JOptionPane;

import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.display.ControlsFactory;
import org.micromanager.display.DisplayManager;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.RequestToCloseEvent;
import org.micromanager.events.DatastoreClosingEvent;
import org.micromanager.events.NewDisplayEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.data.internal.DefaultPropertyMap;
import org.micromanager.PropertyMap;

public class DefaultDisplayManager implements DisplayManager {
   private MMStudio studio_;
   private HashMap<Datastore, ArrayList<DisplayWindow>> storeToDisplays_;

   public DefaultDisplayManager(MMStudio studio) {
      studio_ = studio;
      storeToDisplays_ = new HashMap<Datastore, ArrayList<DisplayWindow>>();
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
   public List<Datastore> getTrackedDatastores() {
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
         }
      }
      storeToDisplays_.put(store, displays);
   }

   @Override
   public boolean getIsTracked(Datastore store) {
      return storeToDisplays_.containsKey(store);
   }

   /**
    * When a Datastore is closed, we need to remove all references to it so
    * it can be garbage-collected.
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
   public List<DisplayWindow> loadDisplaySettings(Datastore store, String path) {
      List<DisplaySettings> allSettings = DefaultDisplaySettings.load(path);
      ArrayList<DisplayWindow> result = new ArrayList<DisplayWindow>();
      for (DisplaySettings settings : allSettings) {
         result.add(new DefaultDisplayWindow(store, null, settings, null));
      }
      return result;
   }

   @Override
   public PropertyMap.PropertyMapBuilder getPropertyMapBuilder() {
      return new DefaultPropertyMap.Builder();
   }

   @Override
   public DisplayWindow createDisplay(Datastore store) {
      return new DefaultDisplayWindow(store, null);
   }

   @Override
   public DisplayWindow createDisplay(Datastore store,
         ControlsFactory factory) {
      return new DefaultDisplayWindow(store, factory);
   }

   @Override
   public void associateDisplay(DisplayWindow display, Datastore store)
         throws IllegalArgumentException {
      if (!storeToDisplays_.containsKey(store)) {
         throw new IllegalArgumentException("Asked to associate a display with datastore " + store + " that is not managed.");
      }
      storeToDisplays_.get(store).add(display);
      display.registerForEvents(this);
   }

   @Override
   public void removeDisplay(DisplayWindow display, Datastore store) {
      if (storeToDisplays_.containsKey(store) &&
            storeToDisplays_.get(store).contains(display)) {
         storeToDisplays_.get(store).remove(display);
      }
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
      if (store.getIsSaved()) {
         // No problem with saving.
         removeDisplay(display);
         return;
      }

      // Prompt the user to save their data.
      String[] options = {"Save as Separate Files", "Save as Single File",
         "Discard", "Cancel"};
      int result = JOptionPane.showOptionDialog(display.getAsWindow(),
            "Do you want to save this data set before closing?",
            "MicroManager", JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
      if (result == 3) {
         // User cancelled.
         return;
      }
      Datastore.SaveMode mode = Datastore.SaveMode.MULTIPAGE_TIFF;
      if (result == 0) {
         mode = Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES;
      }
      if (result != 2) { // I.e. not the "discard" option
         if (!store.save(mode, display.getAsWindow())) {
            // Don't close the window, as saving failed.
            return;
         }
      }
      removeDisplay(display);
      store.freeze();
      // This will invoke our onDatastoreClosed() method.
      store.close();
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
      DisplayWindow display = event.getDisplayWindow();
      if (getIsTracked(display.getDatastore())) {
         associateDisplay(display, display.getDatastore());
      }
   }
}
