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

import org.micromanager.display.internal.gearmenu.DefaultImageExporter;
import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeActiveEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeVisibleEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeInvisibleEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;
import javax.swing.JOptionPane;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DataViewerListener;
import org.micromanager.display.DisplayManager;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.ImageExporter;
import org.micromanager.display.inspector.internal.InspectorCollection;
import org.micromanager.display.inspector.internal.InspectorController;
import org.micromanager.events.DatastoreClosingEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.utils.EventBusExceptionLogger;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.display.DisplayWindowControlsFactory;
import org.micromanager.display.internal.event.DataViewerAddedEvent;
import org.micromanager.display.internal.link.LinkManager;
import org.micromanager.display.internal.link.internal.DefaultLinkManager;


// TODO Methods must implement correct threading semantics!
public final class DefaultDisplayManager extends DataViewerListener implements DisplayManager {
   private static final String[] CLOSE_OPTIONS = new String[] {
         "Cancel", "Prompt for each", "Close without save prompt"};

   private final Studio studio_;

   // Map from "managed" dataproviders to attached displays. Synchronized by
   // monitor on 'this'.
   private final HashMap<DataProvider, ArrayList<DisplayWindow>> providerToDisplays_;

   // TODO: the DataViewerCollection generates events that are being reposted
   // by the DefaultDisplayManager.  Why this indirection?
   // The DataViewerCollection is made available to the InspectorController
   // but nothing else.  I would "couple"  the DataViewerCollection directly
   // rather than through an eventbus, and let the InspectorController
   // use the eventbus from the DefaultDisplayManager...
   private final DataViewerCollection viewers_ = DataViewerCollection.create();

   private final WeakHashMap<DataViewer, Boolean> haveAutoCreatedInspector_ =
         new WeakHashMap<>();

   private final InspectorCollection inspectors_ = InspectorCollection.create();

   private final LinkManager linkManager_ = DefaultLinkManager.create();

   private final EventBus eventBus_ = new EventBus(EventBusExceptionLogger.getInstance());

   public DefaultDisplayManager(Studio studio) {
      studio_ = studio;
      providerToDisplays_ = new HashMap<>();
      viewers_.registerForEvents(this);
   }
   
   @Override
   public Datastore show(Image image) {
      Datastore result = studio_.data().createRAMDatastore();
      createDisplay(result);
      try {
         result.putImage(image);
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Failed to display image");
      }
      return result;
   }

   @Override
   public synchronized List<DataProvider> getManagedDataProviders() {
      return new ArrayList<>(providerToDisplays_.keySet());
   }

   @Override
   public synchronized void manage(DataProvider store) {
      // Iterate over all display windows, find those associated with this
      // datastore, and manually associate them now.
      ArrayList<DisplayWindow> displays = new ArrayList<>();
      providerToDisplays_.put(store, displays);
      for (DisplayWindow display : getAllImageWindows()) {
         if (display.getDataProvider() == store) {
            displays.add(display);
            display.registerForEvents(this);
            display.addListener(this, 100);
         }
      }
   }

   @Override
   public synchronized boolean getIsManaged(DataProvider provider) {
      return providerToDisplays_.containsKey(provider);
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
         if (providerToDisplays_.containsKey(store)) {
            displays = providerToDisplays_.get(store);
            providerToDisplays_.remove(store);
         }
      }
   }

   /**
    * At shutdown, we give the user the opportunity to save data, and to cancel
    * shutdown if they don't want to decide yet.
    * @param event
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
   @Deprecated
   public DisplaySettings.DisplaySettingsBuilder getDisplaySettingsBuilder() {
      return new DefaultDisplaySettings.LegacyBuilder();
   }
   
   @Override
   @Deprecated
   public DisplaySettings.Builder displaySettingsBuilder() {
      return DefaultDisplaySettings.builder();
   }
   
   @Override
   public ChannelDisplaySettings.Builder channelDisplaySettingsBuilder() {
      return DefaultChannelDisplaySettings.builder();
   }
   
   @Override
   public ComponentDisplaySettings.Builder componentDisplaySettingsBuilder() {
      return DefaultComponentDisplaySettings.builder();
   }

   @Override
   @Deprecated
   public DisplaySettings.ContrastSettings getContrastSettings(
         Integer contrastMin, Integer contrastMax, Double gamma,
         Boolean isVisible) {
      return new DefaultDisplaySettings.DefaultContrastSettings(
            contrastMin, contrastMax, gamma, isVisible);
   }

   @Override
   @Deprecated
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
   @Deprecated
   public PropertyMap.Builder getPropertyMapBuilder() {
      return PropertyMaps.builder();
   }

   @Override
   public DisplayWindow createDisplay(DataProvider provider) {
      DisplayWindow ret = new DisplayController.Builder(provider).
            linkManager(linkManager_).shouldShow(true).build(studio_);
      addViewer(ret);
      return ret;
   }

   @Override
   public DisplayWindow createDisplay(DataProvider provider,
         DisplayWindowControlsFactory factory)
   {
      DisplayWindow ret = new DisplayController.Builder(provider).
            linkManager(linkManager_).shouldShow(true).
            controlsFactory(factory).build(studio_);
      addViewer(ret);
      return ret;
   }

   @Override
   public void createInspectorForDataViewer(DataViewer viewer) {
      if (viewer == null || viewer.isClosed()) {
         return;
      }
      InspectorController inspector = InspectorController.create(studio_, viewers_);
      inspectors_.addInspector(inspector);
      inspector.attachToFixedDataViewer(viewer);
      inspector.setVisible(true);
   }

   private void createInspectorForFrontmostDataViewer() {
      if (!inspectors_.hasInspectorForFrontmostDataViewer()) {
         InspectorController inspector = InspectorController.create(studio_, viewers_);
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

      DataProvider store = viewer.getDataProvider();
      synchronized (this) {
         if (getIsManaged(store) && viewer instanceof DisplayWindow) {
            DisplayWindow display = (DisplayWindow) viewer;
            providerToDisplays_.get(store).add(display);
            display.addListener(this, 100);
            studio_.events().registerForEvents(viewer);
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

   /**
    * NS, 10/2017: Unlike documented in the interface, this only loads a single display
    * Currently, there is only a mechanism to store a single file with one set
    * of DisplaySettings to a datastore location. I can not think of an easy, quick,
    * reliable way to store the displaysettings for multiple viewers.  Moreover,
    * this seems a bit esoteric and currently not worth the effort to implement.
    * @param store Datastore to open displays for
    * @return List with opened DisplayWindows
    * @throws IOException 
    */
   @Override
   public List<DisplayWindow> loadDisplays(Datastore store) throws IOException {
      String path = store.getSavePath();
      ArrayList<DisplayWindow> result = new ArrayList<>();
      if (path != null) {
         // try to restore display settings
         File displaySettingsFile = new File(store.getSavePath() + File.separator + 
              PropertyKey.DISPLAY_SETTINGS_FILE_NAME.key());
         DisplaySettings displaySettings = DefaultDisplaySettings.
                 getSavedDisplaySettings(displaySettingsFile);
         if (displaySettings == null) {
            displaySettings = RememberedSettings.loadDefaultDisplaySettings(
                 studio_,
                 store.getSummaryMetadata());
         }
         // instead of using the createDisplay function, set the correct 
         // displaySettings right away
         DisplayWindow tmp = new DisplayController.Builder(store).
            linkManager(linkManager_).shouldShow(true).
                 initialDisplaySettings(displaySettings).build(studio_);
         addViewer(tmp);
         result.add(tmp);
      }
      if (result.isEmpty()) {
         // No path, or no display settings at the path.  Just create a blank
         // new display.
         result.add(createDisplay(store));
      }

      return result;
   }

   // TODO: deprecate, and/or remove?
   @Override
   public synchronized List<DisplayWindow> getDisplays(Datastore store) {
      return new ArrayList<>(providerToDisplays_.get(store));
   }
   
   public synchronized List<DisplayWindow> getDisplays(DataProvider provider) {
      return new ArrayList<>(providerToDisplays_.get(provider));
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
      List<DisplayWindow> ret = new ArrayList<>();
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
   public boolean closeDisplaysFor(DataProvider provider) {
      for (DisplayWindow display : getAllImageWindows()) {
         if (display.getDataProvider() == provider) {
            if (!display.requestToClose()) {
               // Fail out immediately; don't try to close other displays.
               return false;
            }
         }
      }
      return true;
   }

   
   /**
    * Asks user whether or not to save this data set.
    * Either saves the data (when so requested), or not.
    * Return value indicates whether or not the datastore can be closed
    * 
    * @param store   Datastore that can be saved
    * @param display Display over which to orient the prompt (can be null)
    * @return true if Datastore can be closed, false otherwise
    * @throws IOException 
    */
   @Override
   public boolean promptToSave(Datastore store, DisplayWindow display) throws IOException {
      String[] options = {"Save", "Discard", "Cancel"};
      int result = JOptionPane.showOptionDialog(display.getWindow(),
            "<html>Do you want to save <i>" + store.getName() + "</i> before closing?",
            "MicroManager", JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
      if (result == 2 || result < 0) {
         // User cancelled.
         return false;
      }
      if (result == 0) { // i.e. not the "discard" option
         if ( store.save(display.getWindow(), true ) == null) {
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
      boolean complicatedPromptNeeded = false;
      for (DisplayWindow displayWindow : getAllImageWindows()) {
         if (!this.canCloseViewerWithoutPrompting(displayWindow)) {
            complicatedPromptNeeded = true;
         }
      }
      if (complicatedPromptNeeded) {
         int result = JOptionPane.showOptionDialog(null,
                 "Close all open image windows?", "Micro-Manager",
                 JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                 CLOSE_OPTIONS, CLOSE_OPTIONS[0]);
         if (result <= 0) { // cancel
            return;
         }
         // this prompt feels like nagging, but may prevent disastrous data loss
         if (result == 2 && JOptionPane.showConfirmDialog(null,
                 "Are you sure you want to close all image windows without prompting to save?",
                 "Micro-Manager", JOptionPane.YES_NO_OPTION) == 1) {
            // Close without prompting, but user backed out.
            return;
         }
         studio_.displays().closeAllDisplayWindows(result == 1);
      } else {
         // simple prompt:
         if (JOptionPane.showConfirmDialog(null,
                 "Are you sure you want to close all image windows?",
                 "Micro-Manager", JOptionPane.YES_NO_OPTION) == 1) {
            // User backed out.
            return;
         }
         studio_.displays().closeAllDisplayWindows(false);
      }
   }

   @Override
   public boolean closeAllDisplayWindows(boolean shouldPromptToSave) {
      for (DisplayWindow display : getAllImageWindows()) {
         if (shouldPromptToSave && !display.requestToClose()) {
            // User cancelled closing.
            return false;
         }
         else if (!shouldPromptToSave) {
            // Forcefully close display.
            display.close();
         }
      }
      return true;
   }

   private void removeDisplay(DisplayWindow display) {
      DataProvider provider = display.getDataProvider();
      synchronized (this) {
         display.removeListener(this);
         providerToDisplays_.get(provider).remove(display);
      }
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
   public void onEvent(DataViewerAddedEvent e) {
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

   @Override
   public void registerForEvents(Object recipient) {
      eventBus_.register(recipient);
   }

   @Override
   public void unregisterForEvents(Object recipient) {
      eventBus_.unregister(recipient);
   }

   /**
    * Checks if this is the last display for a managed Datastore, and
    * if so, whether that datastore can be closed without prompting
    * Informational only, no actions are taken, so no side effects
    * (unlike canCLoseViewer)    *
    *
    * @return true if the viewer can be closed without prompting
    */
   public boolean canCloseViewerWithoutPrompting(DataViewer viewer) {
      DataProvider provider = viewer.getDataProvider();
      List<DisplayWindow> displays;
      synchronized (this) {
         if (!providerToDisplays_.containsKey(provider)) {
            ReportingUtils.logError(
                    "Received request to close a display that is not associated with a managed datastore.");
            return true;
         }
         displays = getDisplays(provider);

         if (viewer instanceof DisplayWindow) {
            DisplayWindow window = (DisplayWindow) viewer;
            if (!displays.contains(window)) {
               // This should also never happen.
               ReportingUtils.logError(
                       "Was notified of a request to close a display that we didn't know was associated with datastore " + provider);
            }
            if (displays.size() > 1) {
               // Not last display, so OK to close
               return true;
            }
            // Last display; check for saving now.
            if (provider instanceof Datastore) {
               Datastore store = (Datastore) provider;
               if (store.getSavePath() != null) {
                  // Data have been saved already
                  return true;
               }
               else {
                  // prompt needed
                  return false;
               }
            }
         }
         return false;
      }
   }

   /**
    * Check if this is the last display for a Datastore that we are managing,
    * and verify closing without saving (if appropriate).
    *
    * @return
    */
   @Override
   public boolean canCloseViewer(DataViewer viewer) {
      DataProvider provider = viewer.getDataProvider();
      List<DisplayWindow> displays;
      synchronized (this) {
         if (!providerToDisplays_.containsKey(provider)) {
            // This should never happen.
            ReportingUtils.logError("Received request to close a display that is not associated with a managed datastore.");
            return true;
         }
         displays = getDisplays(provider);

         if (viewer instanceof DisplayWindow) {
            DisplayWindow window = (DisplayWindow) viewer;
            if (!displays.contains(window)) {
               // This should also never happen.
               ReportingUtils.logError("Was notified of a request to close a display that we didn't know was associated with datastore " + provider);
            }

            if (displays.size() > 1) {
               // Not last display, so OK to remove 
               removeDisplay(window);
               return true;
            }
            // Last display; check for saving now.
            if (provider instanceof Datastore) {
               Datastore store = (Datastore) provider;
               if (store.getSavePath() != null) {
                  // Data have been saved already, but save our last display settings
                  removeDisplay(window);
                  // if we do not close the datastore, nobody will...
                  try {
                     store.close();
                  } catch (IOException ioe) {
                     ReportingUtils.logError(ioe, "Error while closing datastore");
                  }
                  return true;
               }
               // Prompt the user to save their data.
               try {
                  if (promptToSave(store, window)) {
                     removeDisplay(window);
                     store.freeze();
                     // This will invoke our onDatastoreClosed() method.
                     store.close();
                     return true;
                  }
               } catch (IOException ioe) {
                  ReportingUtils.logError(ioe, "Failed to save:");
               }
            }
            
         }
         return false;
      }
   }

}