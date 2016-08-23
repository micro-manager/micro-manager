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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import javax.swing.JOptionPane;
import org.micromanager.PropertyMap;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultPropertyMap;
import org.micromanager.display.ControlsFactory;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayManager;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.HistogramData;
import org.micromanager.display.ImageExporter;
import org.micromanager.display.NewHistogramsEvent;
import org.micromanager.display.OverlayPanel;
import org.micromanager.display.OverlayPanelFactory;
import org.micromanager.display.OverlayPlugin;
import org.micromanager.display.RequestToCloseEvent;
import org.micromanager.display.internal.events.DisplayActivatedEvent;
import org.micromanager.display.internal.events.GlobalDisplayDestroyedEvent;
import org.micromanager.display.internal.events.ViewerAddedEvent;
import org.micromanager.display.internal.events.ViewerRemovedEvent;
import org.micromanager.display.internal.inspector.InspectorFrame;
import org.micromanager.display.internal.link.DisplayGroupManager;
import org.micromanager.events.DatastoreClosingEvent;
import org.micromanager.events.DisplayAboutToShowEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;


public final class DefaultDisplayManager implements DisplayManager {
   private static final String[] CLOSE_OPTIONS = new String[] {
         "Cancel", "Prompt for each", "Close without save prompt"};
   private static DefaultDisplayManager staticInstance_;

   private final MMStudio studio_;

   // Map from "managed" datastores to attached displays. Synchronized by
   // monitor on 'this'.
   private final HashMap<Datastore, ArrayList<DisplayWindow>> storeToDisplays_;

   private LinkedHashMap<String, OverlayPanelFactory> titleToOverlay_;
   private final Stack<DisplayWindow> displayFocusHistory_;
   private final HashSet<DataViewer> externalViewers_;

   public DefaultDisplayManager(MMStudio studio) {
      studio_ = studio;
      storeToDisplays_ = new HashMap<Datastore, ArrayList<DisplayWindow>>();
      displayFocusHistory_ = new Stack<DisplayWindow>();
      externalViewers_ = new HashSet<DataViewer>();
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
      catch (DatastoreRewriteException e) {
         // This should also never happen.
         ReportingUtils.showError(e, "Somehow managed to create a Datastore that already had an image in it.");
      }
      createDisplay(result);
      return result;
   }

   @Override
   public synchronized List<Datastore> getManagedDatastores() {
      return new ArrayList<Datastore>(storeToDisplays_.keySet());
   }

   @Override
   public synchronized void manage(Datastore store) {
      // Iterate over all display windows, find those associated with this
      // datastore, and manually associate them now.
      ArrayList<DisplayWindow> displays = new ArrayList<DisplayWindow>();
      storeToDisplays_.put(store, displays);
      for (DisplayWindow display : getAllImageWindows()) {
         if (display.getDatastore() == store) {
            displays.add(display);
            display.registerForEvents(this);
         }
      }
   }

   @Override
   public synchronized boolean getIsManaged(Datastore store) {
      return storeToDisplays_.containsKey(store);
   }

   /**
    * When a Datastore is closed, we need to remove all references to it so
    * it can be garbage-collected.
    * @param event
    */
   @Subscribe
   public void onDatastoreClosed(DatastoreClosingEvent event) {
      ArrayList<DisplayWindow> displays = null;
      Datastore store = event.getDatastore();
      synchronized (this) {
         if (storeToDisplays_.containsKey(store)) {
            displays = storeToDisplays_.get(store);
            storeToDisplays_.remove(store);
         }
      }
      if (displays != null) {
         for (DisplayWindow display : displays) {
            display.forceClosed();
         }
      }
   }

   /**
    * At shutdown, we give the user the opportunity to save data, and to cancel
    * shutdown if they don't want to decide yet.
    */
   @Subscribe
   public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
      // If shutdown is already cancelled, don't do anything.
      if (!event.getIsCancelled() && !closeAllDisplayWindows(true)) {
         event.cancelShutdown();
      }
   }

   @Override
   public DisplaySettings getStandardDisplaySettings() {
      return DefaultDisplaySettings.getStandardSettings(
            DefaultDisplayWindow.DEFAULT_SETTINGS_KEY);
   }

   @Override
   public DisplaySettings.DisplaySettingsBuilder getDisplaySettingsBuilder() {
      return new DefaultDisplaySettings.Builder();
   }

   @Override
   public DisplaySettings.ContrastSettings getContrastSettings(
         Integer contrastMin, Integer contrastMax, Double gamma,
         Boolean isVisible) {
      return new DefaultDisplaySettings.DefaultContrastSettings(
            contrastMin, contrastMax, gamma, isVisible);
   }

   @Override
   public DisplaySettings.ContrastSettings getContrastSettings(
         Integer[] contrastMins, Integer[] contrastMaxes, Double[] gammas,
         Boolean isVisible) {
      return new DefaultDisplaySettings.DefaultContrastSettings(
            contrastMins, contrastMaxes, gammas, isVisible);
   }

   @Override
   public HistogramData calculateHistogram(Image image, int component,
         int binPower, int bitDepth, double extremaPercentage,
         boolean shouldCalcStdDev) {
      
      Boolean shouldScaleWithROI = getStandardDisplaySettings().getShouldScaleWithROI();
      if (shouldScaleWithROI == null) {
         shouldScaleWithROI = true;
      }
      
      return ContrastCalculator.calculateHistogram(image, null, component,
            binPower, bitDepth, extremaPercentage, shouldCalcStdDev, shouldScaleWithROI);
   }

   @Override
   public HistogramData calculateHistogramWithSettings(Image image,
         int component, DisplaySettings settings) {
      return ContrastCalculator.calculateHistogramWithSettings(image, null,
            component, settings);
   }

   @Override
   public void updateHistogramDisplays(List<Image> images, DataViewer viewer) {
      for (Image image : images) {
         ArrayList<HistogramData> datas = new ArrayList<HistogramData>();
         for (int i = 0; i < image.getNumComponents(); ++i) {
            HistogramData data = calculateHistogramWithSettings(image, i,
                  viewer.getDisplaySettings());
            datas.add(data);
         }
         viewer.postEvent(
               new NewHistogramsEvent(image.getCoords().getChannel(), datas));
      }
   }

   @Override
   public PropertyMap.PropertyMapBuilder getPropertyMapBuilder() {
      return new DefaultPropertyMap.Builder();
   }

   @Override
   public DisplayWindow createDisplay(Datastore store) {
      return DefaultDisplayWindow.createDisplay(studio_, store, null);
   }

   @Override
   public DisplayWindow createDisplay(Datastore store,
         ControlsFactory factory) {
      return DefaultDisplayWindow.createDisplay(studio_, store, factory);
   }

   @Override
   public void createInspector(DataViewer display) {
      InspectorFrame.createInspector(display);
   }

   @Override
   public boolean createFirstInspector() {
      return InspectorFrame.createFirstInspector();
   }

   @Override
   public void raisedToTop(DataViewer viewer) {
      if (viewer instanceof DisplayWindow) {
         // Update our knowledge of the Z-ordering of the DisplayWindows.
         DisplayWindow display = (DisplayWindow) viewer;
         if (displayFocusHistory_.contains(display)) {
            displayFocusHistory_.remove(display);
            displayFocusHistory_.push(display);
         }
      }
      DefaultEventManager.getInstance().post(
            new DisplayActivatedEvent(viewer));
   }

   @Override
   public void addViewer(DataViewer viewer) {
      externalViewers_.add(viewer);
      DisplayGroupManager.getInstance().addDisplay(viewer);
      DefaultEventManager.getInstance().post(new ViewerAddedEvent(viewer));
      createFirstInspector();
   }

   @Override
   public void removeViewer(DataViewer viewer) {
      if (!externalViewers_.contains(viewer)) {
         throw new IllegalArgumentException("Viewer " + viewer + " is not currently tracked.");
      }
      externalViewers_.remove(viewer);
      DisplayGroupManager.getInstance().removeDisplay(viewer);
      DefaultEventManager.getInstance().post(new ViewerRemovedEvent(viewer));
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
      // HACK: our savefiles can't currently save contrast settings for
      // multi-component images properly, so we must autostretch each display
      // in those cases.
      if (store.getAnyImage().getNumComponents() > 1) {
         for (DisplayWindow display : result) {
            display.autostretch();
         }
      }
      return result;
   }

   @Override
   public synchronized List<DisplayWindow> getDisplays(Datastore store) {
      return new ArrayList<DisplayWindow>(storeToDisplays_.get(store));
   }

   @Override
   public DisplayWindow getCurrentWindow() {
      if (displayFocusHistory_.isEmpty()) {
         return null;
      }
      return displayFocusHistory_.peek();
   }

   @Override
   public List<DisplayWindow> getAllImageWindows() {
      return new ArrayList<DisplayWindow>(displayFocusHistory_);
   }

   @Override
   public List<DataViewer> getAllDataViewers() {
      ArrayList<DataViewer> result = new ArrayList<DataViewer>(
            getAllImageWindows());
      result.addAll(externalViewers_);
      return result;
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
         titleToOverlay_.put(plugin.getName(), plugin.createFactory());
      }
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
      List<DisplayWindow> displays;
      synchronized (this) {
         if (!storeToDisplays_.containsKey(store)) {
            // This should never happen.
            ReportingUtils.logError("Somehow got notified of a request to close for a display that isn't associated with a datastore that we are managing.");
            return;
         }
         displays = getDisplays(store);
         if (!displays.contains(display)) {
            // This should also never happen.
            ReportingUtils.logError("Got notified of a request to close for a display that we didn't know was associated with datastore " + store);
         }
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
      if (result == 2 || result < 0) {
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
   public ImageExporter createExporter() {
      return new DefaultImageExporter();
   }

   @Override
   public void promptToCloseWindows() {
      if (getAllImageWindows().size() == 0) {
         // No open image windows.
         return;
      }
      int result = JOptionPane.showOptionDialog(null,
            "Close all open image windows?", "Micro-Manager",
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
            CLOSE_OPTIONS, CLOSE_OPTIONS[0]);
      if (result <= 0) { // cancel
         return;
      }
      if (result == 2 && JOptionPane.showConfirmDialog(null,
               "Are you sure you want to close all image windows without prompting to save?",
               "Micro-Manager", JOptionPane.YES_NO_OPTION) == 1) {
         // Close without prompting, but user backed out.
         return;
      }
      studio_.displays().closeAllDisplayWindows(result == 1);
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
      synchronized (this) {
         storeToDisplays_.get(store).remove(display);
      }
      display.forceClosed();
   }

   /**
    * Newly-created DisplayWindows should be associated with their Datastores if
    * the Datastore is being managed.
    */
   @Subscribe
   public void onDisplayAboutToShowEvent(DisplayAboutToShowEvent event) {
      DisplayWindow display = event.getDisplay();
      Datastore store = display.getDatastore();
      synchronized (this) {
         if (getIsManaged(store)) {
            storeToDisplays_.get(store).add(display);
            display.registerForEvents(this);
         }
      }
      displayFocusHistory_.push(display);
      DefaultEventManager.getInstance().post(new ViewerAddedEvent(display));
   }

   /**
    * Ensure that we don't think the display still exists. Translate the
    * display-destroyed event into a viewer-removed event.
    */
   @Subscribe
   public void onGlobalDisplayDestroyed(GlobalDisplayDestroyedEvent event) {
      try {
         DisplayWindow display = event.getDisplay();
         Datastore store = display.getDatastore();
         boolean shouldCloseDatastore = false;
         synchronized (this) {
            if (getIsManaged(store)) {
               List<DisplayWindow> displays = storeToDisplays_.get(store);
               if (displays.contains(display)) {
                  displays.remove(display);
               }
               if (displays.isEmpty()) {
                  // No more references to this display exist. Clean it up.
                  storeToDisplays_.remove(store);
                  shouldCloseDatastore = true;
               }
            }
         }
         if (shouldCloseDatastore) {
            store.close();
         }
         if (displayFocusHistory_.contains(display)) {
            displayFocusHistory_.remove(display);
            if (displayFocusHistory_.size() > 0) {
               // The next display in the stack must be on top now.
               raisedToTop(displayFocusHistory_.peek());
            }
         }
         else {
            // This should never happen.
            ReportingUtils.logError("DisplayManager informed of destruction of display it didn't know existed.");
         }
         DefaultEventManager.getInstance().post(
               new ViewerRemovedEvent(event.getDisplay()));
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error handling destroyed display");
      }
   }

   public static DefaultDisplayManager getInstance() {
      return staticInstance_;
   }
}
