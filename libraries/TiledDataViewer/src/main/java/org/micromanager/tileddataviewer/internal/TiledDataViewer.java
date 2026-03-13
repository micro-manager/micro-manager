// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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

package org.micromanager.tileddataviewer.internal;

import java.awt.Image;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.tileddataviewer.TiledDataViewerAPI;
import org.micromanager.tileddataviewer.TiledDataViewerAcqInterface;
import org.micromanager.tileddataviewer.TiledDataViewerCanvasMouseListenerInterface;
import org.micromanager.tileddataviewer.TiledDataViewerDataSource;
import org.micromanager.tileddataviewer.TiledDataViewerOverlayerPlugin;
import org.micromanager.tileddataviewer.internal.gui.AxisScroller;
import org.micromanager.tileddataviewer.internal.gui.ChannelRenderSettings;
import org.micromanager.tileddataviewer.internal.gui.CoalescentExecutor;
import org.micromanager.tileddataviewer.internal.gui.CoalescentRunnable;
import org.micromanager.tileddataviewer.internal.gui.ContrastUpdateCallback;
import org.micromanager.tileddataviewer.internal.gui.DataViewCoords;
import org.micromanager.tileddataviewer.internal.gui.DisplayCoalescentEDTRunnablePool;
import org.micromanager.tileddataviewer.internal.gui.DisplayModel;
import org.micromanager.tileddataviewer.internal.gui.GlobalRenderSettings;
import org.micromanager.tileddataviewer.internal.gui.GuiManager;
import org.micromanager.tileddataviewer.internal.gui.ViewerCanvas;
import org.micromanager.tileddataviewer.internal.gui.contrast.DisplaySettings;
import org.micromanager.tileddataviewer.overlay.Overlay;

public class TiledDataViewer implements TiledDataViewerAPI {

   public static String NO_CHANNEL = "NO_CHANNEL_PRESENT";
   public static String CHANNEL_AXIS = "channel";
   private final GuiManager guiManager_;

   private DisplayCoalescentEDTRunnablePool edtRunnablePool_ =
         DisplayCoalescentEDTRunnablePool.create();

   private CoalescentExecutor displayCalculationExecutor_ =
         new CoalescentExecutor("Display calculation executor");
   private CoalescentExecutor overlayCalculationExecutor_ =
         new CoalescentExecutor("Overlay calculation executor");



   private volatile TiledDataViewerAcqInterface acq_;
   private JSONObject summaryMetadata_;
   private volatile boolean closed_ = false;

   private Function<JSONObject, Long> readTimeFunction_ = null;
   private Function<JSONObject, Double> readZFunction_ = null;

   private double pixelSizeUm_;
   private volatile JSONObject currentMetadata_;
   private LinkedList<Consumer<HashMap<String, Object>>> setImageHooks_ = new LinkedList<>();

   private TiledDataViewerOverlayerPlugin overlayerPlugin_;
   private String preferencesKey_ = "";
   private TiledDataViewerDataSource dataSource_;
   private DisplayModel displayModel_;

   public TiledDataViewer(TiledDataViewerDataSource cache,
                          TiledDataViewerAcqInterface acq,
                          JSONObject summaryMD,
                          double pixelSize,
                          boolean rgb) {
      this(cache, acq, summaryMD, pixelSize, rgb, null);
   }

   public TiledDataViewer(TiledDataViewerDataSource dataSource,
                          TiledDataViewerAcqInterface acq,
                          JSONObject summaryMD,
                          double pixelSize,
                          boolean rgb,
                          String preferencesKey) {
      dataSource_ = dataSource;
      pixelSizeUm_ = pixelSize; //TODO: Could be replaced later with per image pixel size
      summaryMetadata_ = summaryMD;
      acq_ = acq;
      preferencesKey_ = preferencesKey;
      if (preferencesKey_ == null || preferencesKey_.equals("")) {
         preferencesKey_ = "Default";
      }
      displayModel_ = new DisplayModel(this, dataSource_, getPreferences(), rgb);
      guiManager_ = new GuiManager(this, acq_ != null);
   }

   public void setReadTimeMetadataFunction(Function<JSONObject, Long> fn) {
      readTimeFunction_ = fn;
   }

   public void setReadZMetadataFunction(Function<JSONObject, Double> fn) {
      readZFunction_ = fn;
   }

   public JSONObject getDisplaySettingsJSON() {
      return displayModel_.getDisplaySettingsJSON();
   }

   public Preferences getPreferences() {
      return Preferences.userNodeForPackage(TiledDataViewer.class).node(preferencesKey_);
   }

   public void pan(int dx, int dy) {
      displayModel_.pan(dx, dy);
      update();
   }

   public void onScollersAdded() {
      guiManager_.onScrollersAdded();
   }

   public void zoom(double factor, Point mouseLocation) {
      displayModel_.zoom(factor, mouseLocation);

      update();
   }

   public void onCanvasResize(int w, int h) {
      if (guiManager_ == null) {
         return; //Initializing
      }
      guiManager_.onCanvasResize(w, h);
      displayModel_.onCanvasResize(w, h);
      update();
   }

   @Deprecated
   @Override
   public void initializeViewerToLoaded(List<String> channelNames,
                                        JSONObject displaySettings,
                                        HashMap<String, Object> axisMins,
                                        HashMap<String, Object> axisMaxs) {
      throw new UnsupportedOperationException("This method is deprecated");
   }

   public void initializeViewerToLoaded(JSONObject dispSettings) {

      displayModel_.setDisplaySettings(new DisplaySettings(dispSettings, getPreferences()));
      Set<HashMap<String, Object>> axesList = dataSource_.getImageKeys();
      //      //Hide row and column axes form the viewer
      //      if (axesNames.contains(MagellanMD.AXES_GRID_ROW)) {
      //         axesNames.remove(MagellanMD.AXES_GRID_ROW);
      //      }
      //      if (axesNames.contains(MagellanMD.AXES_GRID_COL)) {
      //         axesNames.remove(MagellanMD.AXES_GRID_COL);
      //      }

      for (HashMap<String, Object> axesPositions : axesList) {
         if (axesPositions.keySet().contains(TiledDataViewer.CHANNEL_AXIS)) {
            String channel = (String) axesPositions.get(TiledDataViewer.CHANNEL_AXIS);
            if (!displayModel_.getDisplayedChannels().contains(channel)) {
               getGUIManager().addContrastControlsIfNeeded(channel);
               // Make this channel option appear on scrollbars
               edtRunnablePool_.invokeLaterWithCoalescence(
                       new TiledDataViewer.ExpandDisplayRangeCoalescentRunnable(axesPositions));
            }
         }
         displayModel_.parseNewAxesToUpdateDisplayModel(axesPositions);
      }

      //TODO: wheres the override to ignore row/column axes?
      HashMap<String, Object> axisMins = new HashMap<String, Object>();
      HashMap<String, Object> axisMaxs = new HashMap<String, Object>();
      for (HashMap<String, Object> ax : axesList) {
         for (String axis : ax.keySet()) {
            if (!getDisplayModel().isIntegerAxis(axis)) {
               continue; // String axis, no min or max
            }
            if (!axisMins.containsKey(axis)) {
               axisMins.put(axis, ax.get(axis));
               axisMaxs.put(axis, ax.get(axis));
            }
            axisMins.put(axis, Math.min((Integer) ax.get(axis), (Integer) axisMins.get(axis)));
            axisMaxs.put(axis, Math.max((Integer) ax.get(axis), (Integer) axisMaxs.get(axis)));
         }

      }


      //maximum scrollbar extents
      edtRunnablePool_.invokeLaterWithCoalescence(
              new TiledDataViewer.ExpandDisplayRangeCoalescentRunnable(axisMaxs));
      edtRunnablePool_.invokeLaterWithCoalescence(
              new TiledDataViewer.ExpandDisplayRangeCoalescentRunnable(axisMins));
   }

   public void channelSetActiveByCheckbox(String channelName, boolean selected) {
      displayModel_.channelWasSetActiveByCheckbox(channelName, selected);
      update();
   }

   public void setWindowTitle(String s) {
      guiManager_.setWindowTitle(s);
   }

   /**
    * Signal to viewer that a new image is available.
    *
    * @param axesPositions Hashmap of axis labels to positions
    */
   public void newImageArrived(HashMap<String, Object> axesPositions) {
      try {

         displayModel_.updateDisplayBounds();

         // This will go on to update the GUI as needed
         displayModel_.parseNewAxesToUpdateDisplayModel(axesPositions);

         //expand the scrollbars with new images
         edtRunnablePool_.invokeLaterWithCoalescence(
                 new TiledDataViewer.ExpandDisplayRangeCoalescentRunnable(axesPositions));
         //move scrollbars to new position
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   @Override
   public void addSetImageHook(Consumer<HashMap<String, Object>> hook) {
      setImageHooks_.add(hook);
   }

   public void setAxisPosition(String axis, int position) {
      HashMap<String, Object> axes = new HashMap<>();
      axes.put(axis, position);
      setImageEvent(axes, true);
   }

   /**
    * Called when scrollbars move.
    */
   public void setImageEvent(HashMap<String, Object> axes, boolean fromHuman) {
      if (axes != null && guiManager_ != null) {
         for (String axis : axes.keySet()) {
            if (!guiManager_.isScrollerAxisLocked(axis) || fromHuman) {
               displayModel_.setAxisPosition(axis, axes.get(axis));
            }
         }
      }
      //Set channel
      displayModel_.scrollbarsMoved(axes);
      guiManager_.updateActiveChannelCheckboxes();

      //run hooks
      for (Consumer<HashMap<String, Object>> hook : setImageHooks_) {
         hook.accept(axes);
      }

      update();
   }

   public void updateActiveChannelCheckboxes() {
      guiManager_.updateActiveChannelCheckboxes();
   }

   public void onContrastUpdated() {
      update();
   }

   public void onAnimationToggle(AxisScroller scoller, boolean animate) {
      guiManager_.onAnimationToggle(scoller, animate);
   }

   public void update() {
      if (displayCalculationExecutor_ == null) {
         return; // Not yet initialized
      }
      displayCalculationExecutor_.invokeAsLateAsPossibleWithCoalescence(
               new DisplayImageComputationRunnable());
   }

   public ViewerCanvas getCanvas() {
      return guiManager_.getCanvas();
   }

   public void superlockAllScrollers() {
      guiManager_.superlockAllScrollers();
   }

   public void unlockAllScroller() {
      guiManager_.unlockAllScroller();
   }

   public void showFolder() {
      try {
         File location = new File(dataSource_.getDiskLocation());
         if (isWindows()) {
            Runtime.getRuntime().exec("Explorer /n,/select," + location.getAbsolutePath());
         } else if (isMac()) {
            if (!location.isDirectory()) {
               location = location.getParentFile();
            }
            Runtime.getRuntime().exec(new String[]{"open", location.getAbsolutePath()});
         }
      } catch (IOException ex) {
         throw new RuntimeException(ex);
      }
   }

   static boolean isWindows() {
      String os = System.getProperty("os.name").toLowerCase();
      return (os.contains("win"));
   }

   static boolean isMac() {
      String os = System.getProperty("os.name").toLowerCase();
      return (os.contains("mac"));
   }

   public void abortAcquisition() {
      if (acq_ != null && !acq_.isFinished()) {
         int result = JOptionPane.showConfirmDialog(null, "Finish acquisition?",
                 "Finish Current Acquisition", JOptionPane.OK_CANCEL_OPTION);
         if (result == JOptionPane.OK_OPTION) {
            acq_.abort();
         }
      }
   }

   public void setPausedAction(boolean paused) {
      acq_.setPaused(paused);
   }

   public boolean isAcquisitionPaused() {
      return acq_.isPaused();
   }

   public void setOverlay(Overlay overlay) {
      guiManager_.displayOverlay(overlay);
   }

   public void redrawOverlay() {
      //this will automatically trigger overlay redrawing in a coalescent fashion
      displayCalculationExecutor_.invokeAsLateAsPossibleWithCoalescence(
            new DisplayImageComputationRunnable());
   }

   public double getMagnification() {
      return displayModel_.getMagnification();
   }

   public double getPixelSize() {
      //TODO: replace with pixel size read from image in case different pixel sizes
      return pixelSizeUm_;
   }

   public void showScaleBar(boolean selected) {
      guiManager_.showScaleBar(selected);
   }

   public void setCompositeMode(boolean selected) {
      displayModel_.setCompositeMode(selected);
      update();
   }

   /**
    * Update render settings used by ImageMaker for the next render.
    * Called by NDViewer2DataViewer before triggering update().
    * Also syncs the internal DisplaySettings so getDisplaySettingsJSON() is always current.
    */
   public void setRenderSettings(java.util.Map<String, ChannelRenderSettings> channelSettings,
                                  GlobalRenderSettings globalSettings,
                                  ContrastUpdateCallback callback) {
      guiManager_.setRenderSettings(channelSettings, globalSettings, callback);

      // Keep internal DisplaySettings in sync so getDisplaySettingsJSON() is always current.
      DisplaySettings ds = displayModel_.getDisplaySettings();
      for (java.util.Map.Entry<String, ChannelRenderSettings> entry : channelSettings.entrySet()) {
         String name = entry.getKey();
         ChannelRenderSettings rs = entry.getValue();
         ds.setColor(name, rs.color);
         ds.setContrastMin(name, rs.contrastMin);
         ds.setContrastMax(name, rs.contrastMax);
         ds.setGamma(name, rs.gamma);
         ds.setActive(name, rs.active);
      }
      ds.setAutoscale(globalSettings.autostretch);
      ds.setCompositeMode(globalSettings.composite);
      ds.setLogHist(globalSettings.logHistogram);
      ds.setIgnoreOutliers(globalSettings.ignoreOutliers);
      ds.setIgnoreOutliersPercentage(globalSettings.percentToIgnore);
   }

   @Override
   public JPanel getCanvasJPanel() {
      return getCanvas().getCanvas();
   }

   @Override
   public Object getAxisPosition(String axis) {
      return displayModel_.getAxisPosition(axis);
   }

   @Override
   public Point2D.Double getViewOffset() {
      return displayModel_.getViewOffset();
   }

   @Override
   public Point2D.Double getFullResSourceDataSize() {
      return displayModel_.getFullResSourceDataSize();
   }

   @Override
   public void setViewOffset(double newX, double newY) {
      displayModel_.setViewOffset(newX, newY);
   }

   @Override
   public void setFullResSourceDataSize(double width, double height) {
      displayModel_.setFullResSourceDataSize(width, height);
   }

   @Override
   public void setFullResSourceDataSizeAspectCorrected(double width, double height) {
      displayModel_.setFullResSourceDataSizeAspectCorrected(width, height);
   }

   public void showTimeLabel(boolean selected) {
      guiManager_.setShowTimeLabel(selected);
   }

   public void showZPositionLabel(boolean selected) {
      guiManager_.setShowZPosition(selected);
   }

   public String getCurrentT() {
      if (readTimeFunction_ == null) {
         return "Time metadata reader undefined";
      } else {
         long elapsed = readTimeFunction_.apply(currentMetadata_);
         long hours = elapsed / 60 / 60 / 1000;
         long minutes = elapsed / 60 / 1000;
         long seconds = elapsed / 1000;

         minutes = minutes % 60;
         seconds = seconds % 60;
         double sFrac = (elapsed % 1000) / 1000.0;
         String h = ("0" + hours).substring(("0" + hours).length() - 2);
         String m = ("0" + (minutes)).substring(("0" + minutes).length() - 2);
         String s = ("0" + (seconds)).substring(("0" + seconds).length() - 2);
         String label = h + ":" + m + ":" + s + String.format("%.3f", sFrac).substring(1)
               + " (H:M:S)";

         return label;
      }
   }

   public String getCurrentZPosition() {
      if (readZFunction_ == null) {
         return "Z metadata reader undefined";
      } else {
         try {
            return readZFunction_.apply(currentMetadata_) + " \u00B5" + "m"; //micron
         } catch (Exception e) {
            return  "";
         }
      }
   }

   @Override
   public void setCustomCanvasMouseListener(TiledDataViewerCanvasMouseListenerInterface m) {
      guiManager_.setCustomCanvasMouseListener(m);
   }

   @Override
   public void resetCanvasMouseListener() {
      guiManager_.resetCanvasMouseListener();
   }

   @Override
   public Point2D.Double getDisplayImageSize() {
      return displayModel_.getDisplayImageSize();
   }

   @Override
   public void setOverlayerPlugin(TiledDataViewerOverlayerPlugin overlayer) {
      overlayerPlugin_ = overlayer;
   }

   public GuiManager getGUIManager() {
      return guiManager_;
   }

   public DisplayModel getDisplayModel() {
      return displayModel_;
   }

   public TiledDataViewerDataSource getDataSource() {
      return dataSource_;
   }

   @Override
   public int[] getBounds() {
      return dataSource_.getBounds();
   }

   public void readHistogramControlsStateFromGUI() {
      guiManager_.readHistogramControlsStateFromGUI();
   }

   /**
    * A coalescent runnable to avoid excessively frequent update of the data
    * coords range in the UI.
    */
   private class ExpandDisplayRangeCoalescentRunnable
           implements CoalescentRunnable {

      private final List<HashMap<String, Object>> newIamgeEvents = new ArrayList<>();
      private final List<String> activeChannels = new ArrayList<String>();

      ExpandDisplayRangeCoalescentRunnable(HashMap<String, Object> axisPosisitons) {
         newIamgeEvents.add(axisPosisitons);
         if (axisPosisitons.containsKey(TiledDataViewer.CHANNEL_AXIS)) {
            activeChannels.add((String) axisPosisitons.get(TiledDataViewer.CHANNEL_AXIS));
         }
      }

      @Override
      public Class<?> getCoalescenceClass() {
         return getClass();
      }

      @Override
      public CoalescentRunnable coalesceWith(CoalescentRunnable another) {
         newIamgeEvents.addAll(
                 ((ExpandDisplayRangeCoalescentRunnable) another).newIamgeEvents);
         activeChannels.addAll(((ExpandDisplayRangeCoalescentRunnable) another).activeChannels);
         return this;
      }

      @Override
      public void run() {
         guiManager_.expandDisplayedRangeToInclude(newIamgeEvents, activeChannels);
         setImageEvent(newIamgeEvents.get(newIamgeEvents.size() - 1), false);
         newIamgeEvents.clear();
      }
   }

   /**
    * Called when window is x-ed out by user.
    */
   public void requestToClose() {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               requestToClose();
            }
         });
      } else {
         //check to stop acquisiton?, return here if the attempt to close window unsuccesslful
         if (acq_ != null && !acq_.isFinished()) {
            int result = JOptionPane.showConfirmDialog(null, "Finish acquisition?",
                    "Finish Current Acquisition", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
               acq_.abort();
            } else {
               return;
            }
         }

         close();
      }
   }

   /**
    *
    */
   public void close() {
      new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               if (acq_ != null) {
                  //Finish acquisition on different thread to not slow EDT
                  acq_.abort(); //it may already be aborted but call this again to be sure
                  acq_.waitForCompletion();
                  dataSource_.close();
                  dataSource_ = null;
               }

            } catch (Exception e) {
               //not ,uch to do at this point
               e.printStackTrace();
            } finally {
               //Now all resources should be released, so evertthing can be shut down

               //make everything else close

               guiManager_.shutdown();

               displayCalculationExecutor_.shutdownNow();
               overlayCalculationExecutor_.shutdownNow();

               setImageHooks_ = null;
               dataSource_ = null;
               displayModel_ = null;
               edtRunnablePool_ = null;
               displayCalculationExecutor_ = null;
               overlayCalculationExecutor_ = null;
               acq_ = null;
               closed_ = true;
            }
         }
      }, "NDViewer closing thread").start();
   }

   public JSONObject getSummaryMD() {
      try {
         return new JSONObject(summaryMetadata_.toString());
      } catch (JSONException ex) {
         return null; //this shouldnt happen
      }
   }

   private class DisplayImageComputationRunnable implements CoalescentRunnable {

      DataViewCoords view_ = null;

      public DisplayImageComputationRunnable() {
         if (displayModel_ != null) {
            view_ = displayModel_.copyViewCoords();
         }
      }

      @Override
      public Class<?> getCoalescenceClass() {
         return this.getClass();
      }

      @Override
      public CoalescentRunnable coalesceWith(CoalescentRunnable later) {
         return later; //Always update with newest image 
      }

      @Override
      public void run() {
         if (view_ == null) {
            return;
         }
         if (guiManager_ == null) {
            return; // initialization
         }
         //This is where most of the calculation of creating a display image happens
         Image img = guiManager_.makeOrGetImage(view_);
         JSONObject tags = guiManager_.getLatestTags();
         currentMetadata_ = tags;

         HashMap<String, int[]> channelHistograms = guiManager_.getHistograms();
         edtRunnablePool_.invokeAsLateAsPossibleWithCoalescence(new CanvasRepaintRunnable(img,
                 channelHistograms, view_));
         //now send expensive overlay computation to overlay creation thread
      }
   }

   private class CanvasRepaintRunnable implements CoalescentRunnable {

      final Image img_;
      DataViewCoords view_;
      HashMap<String, int[]> hists_;

      public CanvasRepaintRunnable(Image img, HashMap<String, int[]> hists,
                                   DataViewCoords view) {
         img_ = img;
         view_ = view;
         hists_ = hists;
      }

      @Override
      public Class<?> getCoalescenceClass() {
         return this.getClass();
      }

      @Override
      public CoalescentRunnable coalesceWith(CoalescentRunnable later) {
         return later;
      }

      @Override
      public void run() {
         guiManager_.displayNewImage(img_, hists_, view_, overlayerPlugin_);
      }

   }

}
