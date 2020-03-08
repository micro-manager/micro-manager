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
package org.micromanager.multiresviewer;

import org.micromanager.ndviewer.internal.gui.DisplayCoalescentEDTRunnablePool;
import org.micromanager.ndviewer.internal.gui.CoalescentRunnable;
import org.micromanager.ndviewer.internal.gui.CoalescentExecutor;
import org.micromanager.ndviewer.internal.gui.DataViewCoords;
import org.micromanager.ndviewer.internal.gui.AxisScroller;
import org.micromanager.ndviewer.api.DataSource;
import org.micromanager.ndviewer.api.AcquisitionPlugin;
import java.awt.Color;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.ndviewer.api.ViewerInterface;
import org.micromanager.ndviewer.internal.gui.ViewerCanvas;
import org.micromanager.ndviewer.internal.gui.DisplayWindow;
import org.micromanager.ndviewer.internal.gui.ImageMaker;
import org.micromanager.ndviewer.overlay.Overlay;

public class NDViewer implements ViewerInterface {

   protected DataSource dataSource_;
   private DisplaySettings displaySettings_;

   private DisplayCoalescentEDTRunnablePool edtRunnablePool_ = DisplayCoalescentEDTRunnablePool.create();

//   private EventBus eventBus_ = new EventBus(EventBusExceptionLogger.getInstance());
   private CoalescentExecutor displayCalculationExecutor_ = new CoalescentExecutor("Display calculation executor");
   private CoalescentExecutor overlayCalculationExecutor_ = new CoalescentExecutor("Overlay calculation executor");

   private DisplayWindow displayWindow_;
   private ImageMaker imageMaker_;
   private BaseOverlayer overlayer_;
   private Timer animationTimer_;
   private double animationFPS_ = 7;

   protected DataViewCoords viewCoords_;
   private AcquisitionPlugin acq_;
   private JSONObject summaryMetadata_;

   private Function<JSONObject, Long> readTimeFunction_ = null;
   private Function<JSONObject, Double> readZFunction_ = null;
 
   private double pixelSizeUm_;

   private CopyOnWriteArrayList<String> channelNames_ = new CopyOnWriteArrayList<String>();

   public NDViewer(DataSource cache, AcquisitionPlugin acq, JSONObject summaryMD,
           double pixelSize) {
      pixelSizeUm_ = pixelSize; //TODO: Could be replaced later with per image pixel size
      summaryMetadata_ = summaryMD;
      dataSource_ = cache;
      acq_ = acq;
      displaySettings_ = new DisplaySettings();
      int[] bounds = cache.getImageBounds();
      viewCoords_ = new DataViewCoords(cache, null, 0, 0,
              isImageXYBounded() ? 700 : bounds[2] - bounds[0],
              isImageXYBounded() ? 700 : bounds[3] - bounds[1], dataSource_.getImageBounds());
      displayWindow_ = new DisplayWindow(this);
      overlayer_ = new BaseOverlayer(this);
      imageMaker_ = new ImageMaker(this, cache);
   }

   public void setReadTimeMetadataFunciton(Function<JSONObject, Long> fn) {
      readTimeFunction_ = fn;
   }

   public void setReadZMetadataFunciton(Function<JSONObject, Double> fn) {
      readZFunction_ = fn;
   }

   public void setChannelColor(String channel, Color c) {
      getPreferences().putInt("Preferred_color_" + channel, c.getRGB());
      //Maybe should also propagate this down to contrast controls if called
      //after channels have been initialized
   }

   long readTimeMetadata(JSONObject tags) {
      if (readTimeFunction_ == null) {
         return 0;
      }
      return readTimeFunction_.apply(tags);
   }

   double readZMetadata(JSONObject tags) {
      if (readZFunction_ == null) {
         return 0;
      }
      return readZFunction_.apply(tags);
   }

   protected boolean isImageXYBounded() {
      return dataSource_.getImageBounds() != null;
   }

   public JSONObject getDisplaySettingsJSON() {
      return displaySettings_.toJSON();
   }

   public static Preferences getPreferences() {
      return Preferences.systemNodeForPackage(NDViewer.class);
   }

   public void pan(int dx, int dy) {
      Point2D.Double offset = viewCoords_.getViewOffset();
      double newX = offset.x + (dx / viewCoords_.getDisplayScaleFactor()) * viewCoords_.getDownsampleFactor();
      double newY = offset.y + (dy / viewCoords_.getDisplayScaleFactor()) * viewCoords_.getDownsampleFactor();

      if (isImageXYBounded()) {
         viewCoords_.setViewOffset(
                 Math.max(viewCoords_.xMin_, Math.min(newX, viewCoords_.xMax_ - viewCoords_.getSourceDataSize().x)),
                 Math.max(viewCoords_.yMin_, Math.min(newY, viewCoords_.yMax_ - viewCoords_.getSourceDataSize().y)));
      } else {
         viewCoords_.setViewOffset(newX, newY);
      }
      update();
   }

   public void onScollersAdded() {
      displayWindow_.onScrollersAdded();
   }

   public void onScollPositionChanged(AxisScroller scroller, int value) {
      displayWindow_.onScollPositionChanged(scroller, value);
   }

   public void zoom(double factor, Point mouseLocation) {
      //get zoom center in full res pixel coords
      Point2D.Double viewOffset = viewCoords_.getViewOffset();
      Point2D.Double sourceDataSize = viewCoords_.getSourceDataSize();
      Point2D.Double zoomCenter;
      //compute centroid of the zoom in full res coordinates
      if (mouseLocation == null) {
         //if mouse not over image zoom to center
         zoomCenter = new Point2D.Double(viewOffset.x + sourceDataSize.y / 2, viewOffset.y + sourceDataSize.y / 2);
      } else {
         zoomCenter = new Point2D.Double(
                 (long) viewOffset.x + mouseLocation.x / viewCoords_.getDisplayScaleFactor() * viewCoords_.getDownsampleFactor(),
                 (long) viewOffset.y + mouseLocation.y / viewCoords_.getDisplayScaleFactor() * viewCoords_.getDownsampleFactor());
      }

      //Do zooming--update size of source data
      double newSourceDataWidth = sourceDataSize.x * factor;
      double newSourceDataHeight = sourceDataSize.y * factor;
      if (newSourceDataWidth < 5 || newSourceDataHeight < 5) {
         return; //constrain maximum zoom
      }
      if (isImageXYBounded()) {
         //don't let either of these go bigger than the actual data
         double overzoomXFactor = newSourceDataWidth / (viewCoords_.xMax_ - viewCoords_.xMin_);
         double overzoomYFactor = newSourceDataHeight / (viewCoords_.yMax_ - viewCoords_.yMin_);
         if (overzoomXFactor > 1 || overzoomYFactor > 1) {
            newSourceDataWidth = newSourceDataWidth / Math.max(overzoomXFactor, overzoomYFactor);
            newSourceDataHeight = newSourceDataHeight / Math.max(overzoomXFactor, overzoomYFactor);
         }
      }
      viewCoords_.setSourceDataSize(newSourceDataWidth, newSourceDataHeight);

      double xOffset = (zoomCenter.x - (zoomCenter.x - viewOffset.x) * newSourceDataWidth / sourceDataSize.x);
      double yOffset = (zoomCenter.y - (zoomCenter.y - viewOffset.y) * newSourceDataHeight / sourceDataSize.y);
      //make sure view doesn't go outside image bounds
      if (isImageXYBounded()) {
         viewCoords_.setViewOffset(
                 Math.max(viewCoords_.xMin_, Math.min(xOffset, viewCoords_.xMax_ - viewCoords_.getSourceDataSize().x)),
                 Math.max(viewCoords_.yMin_, Math.min(yOffset, viewCoords_.yMax_ - viewCoords_.getSourceDataSize().y)));
      } else {
         viewCoords_.setViewOffset(xOffset, yOffset);
      }

      update();
   }

   public void onCanvasResize(int w, int h) {
      if (displayWindow_ == null) {
         return; // during startup
      }
      displayWindow_.onCanvasResized(w, h);

      Point2D.Double displaySizeOld = viewCoords_.getDisplayImageSize();
      //reshape the source image to match canvas aspect ratio
      //expand it, unless it would put it out of range
      double canvasAspect = w / (double) h;
      Point2D.Double source = viewCoords_.getSourceDataSize();
      double sourceAspect = source.x / source.y;
      double newSourceX;
      double newSourceY;
      if (isImageXYBounded()) {
         if (canvasAspect > sourceAspect) {
            newSourceX = canvasAspect / sourceAspect * source.x;
            newSourceY = source.y;
            //check that still within image bounds
         } else {
            newSourceX = source.x;
            newSourceY = source.y / (canvasAspect / sourceAspect);
         }

         double overzoomXFactor = newSourceX / (viewCoords_.xMax_ - viewCoords_.xMin_);
         double overzoomYFactor = newSourceY / (viewCoords_.yMax_ - viewCoords_.yMin_);
         if (overzoomXFactor > 1 || overzoomYFactor > 1) {
            newSourceX = newSourceX / Math.max(overzoomXFactor, overzoomYFactor);
            newSourceY = newSourceY / Math.max(overzoomXFactor, overzoomYFactor);
         }
      } else if (displaySizeOld.x != 0 && displaySizeOld.y != 0) {
         newSourceX = source.x * (w / (double) displaySizeOld.x);
         newSourceY = source.y * (h / (double) displaySizeOld.y);
      } else {
         newSourceX = source.x / sourceAspect * canvasAspect;
         newSourceY = source.y;
      }
      //move into visible area
      viewCoords_.setViewOffset(
              Math.max(viewCoords_.xMin_, Math.min(viewCoords_.xMax_
                      - newSourceX, viewCoords_.getViewOffset().x)),
              Math.max(viewCoords_.yMin_, Math.min(viewCoords_.yMax_
                      - newSourceY, viewCoords_.getViewOffset().y)));

      //set the size of the display iamge
      viewCoords_.setDisplayImageSize(w, h);
      //and the size of the source pixels from which it derives
      viewCoords_.setSourceDataSize(newSourceX, newSourceY);
      update();
   }

   void setLoadedDataScrollbarBounds(List<String> channelNames, int nFrames, int minSliceIndex, int maxSliceIndex) {

      for (int c = 0; c < channelNames.size(); c++) {
         displayWindow_.addContrastControls(channelNames.get(c));
      }
      //maximum scrollbar extensts
      //TODO: generalize to generic axes
      HashMap<String, Integer> axisPositions = new HashMap<String, Integer>();
      axisPositions.put("z", maxSliceIndex);
      axisPositions.put("t", nFrames - 1);
      axisPositions.put("c", channelNames.size() - 1);
      //TODO: generalize
      edtRunnablePool_.invokeLaterWithCoalescence(new NDViewer.ExpandDisplayRangeCoalescentRunnable(axisPositions,
            channelNames.get(channelNames.size()-1)));

      HashMap<String, Integer> axisPositionsMin = new HashMap<String, Integer>();
      axisPositions.put("z", minSliceIndex);
      //minimum scrollbar extensts
      edtRunnablePool_.invokeLaterWithCoalescence(
              new NDViewer.ExpandDisplayRangeCoalescentRunnable(axisPositionsMin,
                      channelNames.get(channelNames.size()-1) ) );
   }

   public void channelSetActive(String channelName, boolean selected) {
      if (!displaySettings_.isCompositeMode()) {
         if (selected) {
            viewCoords_.setActiveChannel(channelName);

            //only one channel can be active so inacivate others
            for (String channel : channelNames_) {
               displaySettings_.setActive(channel, channel.equals(viewCoords_.getActiveChannel()));
            }
         } else {
            //if channel turns off, nothing will show, so dont let this happen
         }
         //make sure other checkboxes update if they autochanged
         displayWindow_.displaySettingsChanged();
      } else {
         //composite mode
         displaySettings_.setActive(channelName, selected);
      }
      update();
   }

   public void setWindowTitle(String s) {
      displayWindow_.setTitle(s);
   }

   /**
    * Signal to viewer that a new image is available
    *
    * @param axesPositions Hashmap of axis labels to positions
    * @param channelName
    * @param bitDepth
    */
   public void newImageArrived(HashMap<String, Integer> axesPositions,
           String channelName, int bitDepth) {
      if (viewCoords_.getActiveChannel() == null) {
         viewCoords_.setActiveChannel(channelName);
      }
      displayWindow_.onNewImage();

      //TODO: generalize
//      displayWindow_.updateExploreZControls(event.getPositionForAxis("z"));
      boolean newChannel = false;
      if (!channelNames_.contains(channelName)) {
         channelNames_.add(channelName);
         newChannel = true;
      }

      if (newChannel) {
         //Add contrast controls and display settings
         Color color;
         int colorInt = getPreferences().getInt("Preferred_color_" + channelName, -1);
         if (colorInt != -1) {
            color = new Color(colorInt);
         } else {
            color = Color.white;
         }
         displaySettings_.addChannel(channelName, bitDepth, color);
         displayWindow_.addContrastControls(channelName);
      }

      //expand the scrollbars with new images
      edtRunnablePool_.invokeLaterWithCoalescence(
              new NDViewer.ExpandDisplayRangeCoalescentRunnable(axesPositions,
               channelName));

      //move scrollbars to new position
//      postEvent(new SetImageEvent(axesPositions, false));
//      setImageEvent(axesPositions, false);
   }

   public void setChannel(String c) {
      setImageEvent(null, c, true);
   }

   public void setAxisPosition(String axis, int position) {
      HashMap<String, Integer> axes = new HashMap<String, Integer>();
      axes.put(axis, position);
      setImageEvent(axes, viewCoords_.getActiveChannel(), true);
   }

   /**
    * Called when scrollbars move
    */
   public void setImageEvent(HashMap<String, Integer> axes, String channel, boolean fromHuman) {
      if (axes != null) {
         for (String axis : axes.keySet()) {
            if (!displayWindow_.isScrollerAxisLocked(axis) || fromHuman) {
               viewCoords_.setAxisPosition(axis, axes.get(axis));
            }
         }
      }
      //Set channel
      viewCoords_.setActiveChannel(channel);
      //Update other channels if in single channel view mode
      if (!displaySettings_.isCompositeMode()) {
         //set all channels inactive except current one
         for (String c : channelNames_) {
            displaySettings_.setActive(c, c.equals(viewCoords_.getActiveChannel()));
            displayWindow_.displaySettingsChanged();
         }
      }

      //TODO: generalize
//      displayWindow_.updateExploreZControls(event.getPositionForAxis("z"));
      update();
   }

   public void onContrastUpdated() {
      update();
   }

   void onAnimationToggle(AxisScroller scoller, boolean animate) {
      if (animationTimer_ != null) {
         animationTimer_.stop();
      }
      if (animate) {
         animationTimer_ = new Timer((int) (1000 / animationFPS_), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               scoller.setPosition((scoller.getPosition() + 1)
                       % (scoller.getMaximum() - scoller.getMinimum()));
            }
         });
         animationTimer_.start();
      }
   }

   public void update() {
      displayCalculationExecutor_.invokeAsLateAsPossibleWithCoalescence(new DisplayImageComputationRunnable());
   }

   public ViewerCanvas getCanvas() {
      return displayWindow_.getCanvas();
   }

   public void superlockAllScrollers() {
      displayWindow_.superlockAllScrollers();
   }

   public void unlockAllScroller() {
      displayWindow_.unlockAllScrollers();
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

   public void setAnimateFPS(double doubleValue) {
      animationFPS_ = doubleValue;
      if (animationTimer_ != null) {
         ActionListener action = animationTimer_.getActionListeners()[0];
         animationTimer_.stop();
         animationTimer_ = new Timer((int) (1000 / animationFPS_), action);
         animationTimer_.start();
      }
   }

   public void abortAcquisition() {
      if (acq_ != null && !acq_.isComplete()) {
         int result = JOptionPane.showConfirmDialog(null, "Finish acquisition?",
                 "Finish Current Acquisition", JOptionPane.OK_CANCEL_OPTION);
         if (result == JOptionPane.OK_OPTION) {
            acq_.abort();
         } else {
            return;
         }
      }
   }

   public void togglePauseAcquisition() {
      acq_.togglePaused();
   }

   public boolean isAcquisitionPaused() {
      return acq_.isPaused();
   }

   void setOverlay(Overlay overlay) {
      displayWindow_.displayOverlay(overlay);
   }

   void setOverlayMode(int mode) {
      viewCoords_.setOverlayMode(mode);
   }

   //TODO: this needs changin/updating
   public int getOverlayMode() {
      return viewCoords_.getOverlayMode();
   }

   public void redrawOverlay() {
      //this will automatically trigger overlay redrawing in a coalescent fashion
      displayCalculationExecutor_.invokeAsLateAsPossibleWithCoalescence(new DisplayImageComputationRunnable());
   }

   double getScale() {
      return viewCoords_.getDisplayToFullScaleFactor();
   }

   double getPixelSize() {
      //TODO: replace with pixel size read from image in case different pixel sizes
      return pixelSizeUm_;
   }

   public void showScaleBar(boolean selected) {
      overlayer_.setShowScaleBar(selected);
   }

   public void setCompositeMode(boolean selected) {
      displaySettings_.setCompositeMode(selected);
      //select all channels if composite mode is being turned on
      if (selected) {
         for (String channel : channelNames_) {
            displaySettings_.setActive(channel, true);
            displayWindow_.displaySettingsChanged();
         }
      } else {
         for (String channel : channelNames_) {
            displaySettings_.setActive(channel, viewCoords_.getActiveChannel().equals(channel));
            displayWindow_.displaySettingsChanged();
         }
      }
      update();
   }

   public boolean isCompositMode() {
      return displaySettings_.isCompositeMode();
   }

   public DisplaySettings getDisplaySettingsObject() {
      return displaySettings_;
   }

   public Iterable<String> getChannels() {
      return channelNames_;
   }

   JPanel getCanvasJPanel() {
      return getCanvas().getCanvas();
   }

   @Override
   public int getAxisPosition(String axis) {
      return viewCoords_.getAxisPosition(axis);
   }

   @Override
   public Point2D.Double getViewOffset() {
      return viewCoords_.getViewOffset();
   }

   @Override
   public Point2D.Double getSizeOfVisibleImage() {
      return viewCoords_.getSourceDataSize();
   }

   @Override
   public double getDisplayToFullScaleFactor() {
      return viewCoords_.getDisplayToFullScaleFactor();
   }

   @Override
   public void setViewOffset(double newX, double newY) {
      viewCoords_.setViewOffset(newY, newY);
   }

   @Override
   public int[] getBounds() {
      return viewCoords_.getBounds();
   }

   public int getChannelIndex(String c) {
      return channelNames_.indexOf(c);
   }

   public String getChannelName(int position) {
      return channelNames_.get(position);
   }

   /**
    * A coalescent runnable to avoid excessively frequent update of the data
    * coords range in the UI
    */
   private class ExpandDisplayRangeCoalescentRunnable
           implements CoalescentRunnable {

      private final List<HashMap<String, Integer>> newIamgeEvents = new ArrayList<HashMap<String, Integer>>();
      private final List<String> activeChannels = new ArrayList<String> ();

      ExpandDisplayRangeCoalescentRunnable(HashMap<String, Integer> axisPosisitons, String channelIndex) {
         newIamgeEvents.add(axisPosisitons);
         activeChannels.add(channelIndex);
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
         if (displayWindow_ != null) {
            displayWindow_.expandDisplayedRangeToInclude(newIamgeEvents, activeChannels);
         }
         setImageEvent(newIamgeEvents.get(newIamgeEvents.size() - 1), 
                 activeChannels.get(activeChannels.size() - 1), false);
         newIamgeEvents.clear();
      }
   }

   /**
    * Called when window is x-ed out by user
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
         if (acq_ != null && !acq_.isComplete()) {
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
    * *
    * Acquisition should be closed before calling this
    */
   public void close() {
      //acquisition should be aborted or finished already when this is called

      //make everything else close
      displayWindow_.onDisplayClose();

      displayCalculationExecutor_.shutdownNow();
      overlayCalculationExecutor_.shutdownNow();

      imageMaker_.close();
      imageMaker_ = null;

      overlayer_.shutdown();
      overlayer_ = null;

      if (animationTimer_ != null) {
         animationTimer_.stop();
      }
      animationTimer_ = null;

      dataSource_.viewerClosing();
      dataSource_ = null;

      displayWindow_ = null;
      viewCoords_ = null;

      edtRunnablePool_ = null;
      displaySettings_ = null;
      displayCalculationExecutor_ = null;
      overlayCalculationExecutor_ = null;
      displaySettings_ = null;
      acq_ = null;
   }

   //TODO: deal with
//   @Subscribe
//   public void onExploreZLimitsChangedEvent(ExploreZLimitsChangedEvent event) {
//      ((ExploreAcquisition) acq_).setZLimits(event.top_, event.bottom_);
//   }
   public void onDataSourceClosing() {
      requestToClose();
   }

   /**
    * Post an event on the viewer event bus.
    *
    * Implementations should call this method to post required notification
    * events.
    * <p>
    * Some standard viewer events require that they be posted on the Swing/AWT
    * event dispatch thread. Make sure you are on the right thread when posting
    * such events.
    * <p>
    * Viewers are required to post the following events:
    * <ul>
    * <li>{@link DisplaySettingsChangedEvent} (posted by this abstract class)
    * <li>{@link DisplayPositionChangedEvent} (posted by this abstract class)
    * </ul>
    *
    * @param event the event to post
    */
//   final void postEvent(Object event) {
//      eventBus_.post(event);
//   }
   public JSONObject getSummaryMD() {
      try {
         return new JSONObject(summaryMetadata_.toString());
      } catch (JSONException ex) {
         return null; //this shouldnt happen
      }
   }

   private class DisplayImageComputationRunnable implements CoalescentRunnable {

      DataViewCoords view_;

      public DisplayImageComputationRunnable() {
         view_ = viewCoords_.copy();
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
         //This is where most of the calculation of creating a display image happens
         Image img = imageMaker_.makeOrGetImage(view_);
         JSONObject tags = imageMaker_.getLatestTags();
         Overlay cheapOverlay = null;
         //TODO: add to overlay rather than all external
         if (overlayer_ != null) {
            cheapOverlay = overlayer_.createDefaultOverlay(view_);
         }

         HashMap<String, int[]> channelHistograms = imageMaker_.getHistograms();
         HashMap<String, Integer> pixelMins = imageMaker_.getPixelMins();
         HashMap<String, Integer> pixelMaxs = imageMaker_.getPixelMaxs();
         edtRunnablePool_.invokeAsLateAsPossibleWithCoalescence(new CanvasRepaintRunnable(img,
                 channelHistograms, pixelMins, pixelMaxs, view_, cheapOverlay, tags));
         //now send expensive overlay computation to overlay creation thread
         overlayer_.redrawOverlay(view_);
      }
   }

   private class CanvasRepaintRunnable implements CoalescentRunnable {

      final Image img_;
      DataViewCoords view_;
      HashMap<String, int[]> hists_;
      HashMap<String, Integer> mins_;
      HashMap<String, Integer> maxs_;
      Overlay overlay_;
      JSONObject imageMD_;

      public CanvasRepaintRunnable(Image img, HashMap<String, int[]> hists, HashMap<String, Integer> pixelMins,
              HashMap<String, Integer> pixelMaxs, DataViewCoords view, Overlay cheapOverlay, JSONObject imageMD) {
         img_ = img;
         view_ = view;
         hists_ = hists;
         mins_ = pixelMins;
         maxs_ = pixelMaxs;
         overlay_ = cheapOverlay;
         imageMD_ = imageMD;
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
         displayWindow_.displayImage(img_, hists_, mins_, maxs_, view_);
         displayWindow_.setImageMetadata(imageMD_);
         if (overlay_ != null) {
            displayWindow_.displayOverlay(overlay_);
         }
         displayWindow_.repaintCanvas();
      }

   }

}
