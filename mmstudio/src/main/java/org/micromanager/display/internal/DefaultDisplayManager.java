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

import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeActiveEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeVisibleEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeInvisibleEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;
import javax.swing.JOptionPane;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayManager;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.ImageExporter;
import org.micromanager.display.inspector.internal.InspectorCollection;
import org.micromanager.display.inspector.internal.InspectorController;
import org.micromanager.events.DatastoreClosingEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.EventBusExceptionLogger;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.display.DisplayWindowControlsFactory;
//import org.micromanager.display.RequestToCloseEvent;
import org.micromanager.display.internal.link.LinkManager;
import org.micromanager.display.internal.link.internal.DefaultLinkManager;


// TODO Methods must implement correct threading semantics!
public final class DefaultDisplayManager implements DisplayManager {
   private static final String[] CLOSE_OPTIONS = new String[] {
         "Cancel", "Prompt for each", "Close without save prompt"};
   private static DefaultDisplayManager staticInstance_;

   private final MMStudio studio_;

   // Map from "managed" datastores to attached displays. Synchronized by
   // monitor on 'this'.
   private final HashMap<Datastore, ArrayList<DisplayWindow>> storeToDisplays_;

   private final DataViewerCollection viewers_ = DataViewerCollection.create();

   private final WeakHashMap<DataViewer, Boolean> haveAutoCreatedInspector_ =
         new WeakHashMap<DataViewer, Boolean>();

   private final InspectorCollection inspectors_ = InspectorCollection.create();

   private final LinkManager linkManager_ = DefaultLinkManager.create();

   private final EventBus eventBus_ = new EventBus(EventBusExceptionLogger.getInstance());

   public DefaultDisplayManager(MMStudio studio) {
      studio_ = studio;
      storeToDisplays_ = new HashMap<Datastore, ArrayList<DisplayWindow>>();
      studio_.events().registerForEvents(this);
      staticInstance_ = this;

      viewers_.registerForEvents(this);
   }

   @Override
   public Datastore show(Image image) {
      Datastore result = studio_.data().createRAMDatastore();
      try {
         result.putImage(image);
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Failed to display image");
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
      // TODO XXX This should be done by the individual data viewers
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
      return DefaultDisplaySettings.getStandardSettings(null);
   }

   @Override
   public DisplaySettings.DisplaySettingsBuilder getDisplaySettingsBuilder() {
      return new DefaultDisplaySettings.LegacyBuilder();
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

   /* TODO
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
   */

   @Override
   public PropertyMap.Builder getPropertyMapBuilder() {
      return PropertyMaps.builder();
   }

   @Override
   public DisplayWindow createDisplay(DataProvider provider) {
      DisplayWindow ret = new DisplayController.Builder(provider).
            linkManager(linkManager_).shouldShow(true).build();
      addViewer(ret);
      return ret;
   }

   @Override
   public DisplayWindow createDisplay(DataProvider provider,
         DisplayWindowControlsFactory factory)
   {
      DisplayWindow ret = new DisplayController.Builder(provider).
            linkManager(linkManager_).shouldShow(true).
            controlsFactory(factory).build();
      addViewer(ret);
      return ret;
   }

   @Override
   public void createInspectorForDataViewer(DataViewer viewer) {
      if (viewer == null || viewer.isClosed()) {
         return;
      }
      InspectorController inspector = InspectorController.create(viewers_);
      inspectors_.addInspector(inspector);
      inspector.attachToFixedDataViewer(viewer);
      inspector.setVisible(true);
   }

   private void createInspectorForFrontmostDataViewer() {
      if (!inspectors_.hasInspectorForFrontmostDataViewer()) {
         InspectorController inspector = InspectorController.create(viewers_);
         inspectors_.addInspector(inspector);
         inspector.attachToFrontmostDataViewer();
         inspector.setVisible(true);
      }
   }

   @Override
   @Deprecated
   public boolean createFirstInspector() {
      createInspectorForFrontmostDataViewer();
      return true;
   }

   @Override
   public void addViewer(DataViewer viewer) {
      viewers_.addDataViewer(viewer);
      if (viewer instanceof DisplayWindow) {
         // TODO DisplayGroupManager.getInstance().addDisplay(viewer);
      }

      Datastore store = viewer.getDatastore();
      synchronized (this) {
         if (getIsManaged(store) && viewer instanceof DisplayWindow) {
            DisplayWindow display = (DisplayWindow) viewer;
            storeToDisplays_.get(store).add(display);
         }
      }
   }

   @Override
   public void removeViewer(DataViewer viewer) {
      if (viewer instanceof DisplayWindow) {
         // TODO DisplayGroupManager.getInstance().removeDisplay(viewer);
      }
      viewers_.removeDataViewer(viewer);
   }

   @Override
   public List<DisplayWindow> loadDisplays(Datastore store) throws IOException {
      String path = store.getSavePath();
      ArrayList<DisplayWindow> result = new ArrayList<DisplayWindow>();
      if (path != null) {
         /*
          List<DisplaySettings> allSettings = DefaultDisplaySettings.load(path);
         for (DisplaySettings settings : allSettings) {
            DisplayWindow tmp = createDisplay(store);
            tmp.setDisplaySettings(settings);
            result.add(tmp);
         }
         */
      }
      if (result.isEmpty()) {
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
   public DataViewer getActiveDataViewer() {
      return viewers_.getActiveDataViewer();
   }

   @Override
   @Deprecated
   public DisplayWindow getCurrentWindow() {
      DataViewer viewer = viewers_.getActiveDataViewer();
      if (viewer instanceof DisplayWindow) {
         return (DisplayWindow) viewer;
      }
      return null;
   }

   // TODO Deprecate this and provide better-named methods that get (1) all
   // windows in added order or (2) visible windows in most-recently-activated
   // order. Note that DataViewers are excluded, since a DataViewer may not
   // have a window.
   @Override
   public List<DisplayWindow> getAllImageWindows() {
      List<DataViewer> viewers = viewers_.getAllDataViewers();
      List<DisplayWindow> ret = new ArrayList<DisplayWindow>();
      for (DataViewer viewer : viewers) {
         if (viewer instanceof DisplayWindow) {
            ret.add((DisplayWindow) viewer);
         }
      }
      return ret;
   }

   @Override
   public List<DataViewer> getAllDataViewers() {
      return viewers_.getAllDataViewers();
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

   /**
    * Check if this is the last display for a Datastore that we are managing,
    * and verify closing without saving (if appropriate).
    *
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
   */

   // TODO Why do we need both store and display?
   @Override
   public boolean promptToSave(Datastore store, DisplayWindow display) throws IOException {
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
      if (getAllImageWindows().isEmpty()) {
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

   @Subscribe
   public void onEvent(DataViewerDidBecomeVisibleEvent e) {
      // If the viewer has been shown for the first time, we ensure that an
      // inspector is open, set to display info on the frontmost viewer.
      Boolean haveCreatedInspector =
            haveAutoCreatedInspector_.get(e.getDataViewer());
      if (haveCreatedInspector == null || !haveCreatedInspector) {
         createInspectorForFrontmostDataViewer();
         haveAutoCreatedInspector_.put(e.getDataViewer(), true);
      }
      eventBus_.post(e);
   }

   @Subscribe
   public void onEvent(DataViewerDidBecomeInvisibleEvent e) {
      eventBus_.post(e);
   }

   @Subscribe
   public void onEvent(DataViewerDidBecomeActiveEvent e) {
      eventBus_.post(e);
   }

   @Subscribe
   public void onEvent(DataViewerWillCloseEvent e) {
      eventBus_.post(e);
   }

   @Deprecated
   public static DefaultDisplayManager getInstance() {
      return staticInstance_;
   }

   @Override
   public void registerForEvents(Object recipient) {
      eventBus_.register(recipient);
   }

   @Override
   public void unregisterForEvents(Object recipient) {
      eventBus_.unregister(recipient);
   }
}