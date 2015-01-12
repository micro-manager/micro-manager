package org.micromanager.display.internal;

import com.google.common.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.JOptionPane;

import org.micromanager.data.Datastore;
import org.micromanager.display.DisplayManager;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.RequestToCloseEvent;
import org.micromanager.events.DatastoreClosingEvent;
import org.micromanager.events.NewDisplayEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

public class DefaultDisplayManager implements DisplayManager {
   private MMStudio studio_;
   private HashMap<Datastore, ArrayList<DisplayWindow>> storeToDisplays_;

   public DefaultDisplayManager(MMStudio studio) {
      studio_ = studio;
      storeToDisplays_ = new HashMap<Datastore, ArrayList<DisplayWindow>>();
   }

   @Override
   public List<Datastore> getTrackedDatastores() {
      return new ArrayList<Datastore>(storeToDisplays_.keySet());
   }

   @Override
   public void track(Datastore store) {
      // Iterate over all display windows, find those associated with this
      // datastore, and manually associate them now.
      ArrayList<DisplayWindow> displays = new ArrayList<DisplayWindow>();
      for (DisplayWindow display : studio_.getAllImageWindows()) {
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
   public DisplayWindow createDisplay(Datastore store) {
      return new DefaultDisplayWindow(store, null);
   }

   @Override
   public void associateDisplay(DisplayWindow window, Datastore store)
         throws IllegalArgumentException {
      if (!storeToDisplays_.containsKey(store)) {
         throw new IllegalArgumentException("Asked to associate a display with datastore " + store + " that is not tracked.");
      }
      storeToDisplays_.get(store).add(window);
      window.registerForEvents(this);
   }

   @Override
   public void removeDisplay(DisplayWindow window, Datastore store) {
      if (storeToDisplays_.containsKey(store) &&
            storeToDisplays_.get(store).contains(window)) {
         storeToDisplays_.get(store).remove(window);
      }
   }

   @Override
   public List<DisplayWindow> getDisplays(Datastore store) {
      return storeToDisplays_.get(store);
   }

   /**
    * Check if this is the last display for a Datastore that we are tracking,
    * and verify closing without saving (if appropriate).
    */
   @Subscribe
   public void onRequestToClose(RequestToCloseEvent event) {
      DisplayWindow display = event.getDisplay();
      Datastore store = display.getDatastore();
      if (!storeToDisplays_.containsKey(store)) {
         // This should never happen.
         ReportingUtils.logError("Somehow got notified of a request to close for a display that isn't associated with a datastore that we are tracking.");
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
         mode = Datastore.SaveMode.SEPARATE_TIFFS;
      }
      if (result != 2) { // I.e. not the "discard" option
         if (!store.save(mode, display.getAsWindow())) {
            // Don't close the window, as saving failed.
            return;
         }
      }
      removeDisplay(display);
      store.lock();
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
    * if the Datastore is being tracked.
    */
   @Subscribe
   public void onNewDisplayEvent(NewDisplayEvent event) {
      DisplayWindow display = event.getDisplayWindow();
      if (getIsTracked(display.getDatastore())) {
         associateDisplay(display, display.getDatastore());
      }
   }
}
