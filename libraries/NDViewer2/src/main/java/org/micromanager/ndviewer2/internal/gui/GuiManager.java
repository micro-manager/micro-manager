package org.micromanager.ndviewer2.internal.gui;

import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Timer;
import org.micromanager.ndviewer2.NDViewer2CanvasMouseListenerInterface;
import org.micromanager.ndviewer2.NDViewer2OverlayerPlugin;
import org.micromanager.ndviewer2.internal.NDViewer2;
import org.micromanager.ndviewer2.overlay.Overlay;



public class GuiManager {

   private DisplayWindow displayWindow_;

   private ImageMaker imageMaker_;
   private BaseOverlayer overlayer_;
   private Timer animationTimer_;
   private double animationFPS_ = 7;

   private NDViewer2 display_;

   public GuiManager(NDViewer2 ndViewer2, boolean acquisition) {
      displayWindow_ = new DisplayWindow(ndViewer2, !acquisition);

      overlayer_ = new BaseOverlayer(ndViewer2);
      imageMaker_ = new ImageMaker(ndViewer2, ndViewer2.getDataSource());
      display_ = ndViewer2;

   }

   public void onScrollersAdded() {
      displayWindow_.onScrollersAdded();
   }

   public void onCanvasResize(int w, int h) {
      if (displayWindow_ == null) {
         return; // during startup
      }
      displayWindow_.onCanvasResized(w, h);

   }

   public void setWindowTitle(String s) {
      if (displayWindow_ != null) {
         displayWindow_.setTitle(s);
      }
   }


   public boolean isScrollerAxisLocked(String axis) {
      return displayWindow_.isScrollerAxisLocked(axis);
   }

   public void onAnimationToggle(AxisScroller scoller, boolean animate) {
      if (animationTimer_ != null) {
         animationTimer_.stop();
      }
      if (animate) {
         animationTimer_ = new Timer((int) (1000 / animationFPS_), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               int min = scoller.getMinimum();
               int max = scoller.getMaximum();
               int range = max - min + 1;
               if (range <= 1) {
                  return;
               }
               int newPos = (scoller.getPosition() - min + 1) % range + min;
               display_.setAxisPosition(scoller.getAxis(), newPos);
            }
         });
         animationTimer_.start();
      }
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

   public void setAnimateFPS(double doubleValue) {
      animationFPS_ = doubleValue;
      if (animationTimer_ != null) {
         ActionListener action = animationTimer_.getActionListeners()[0];
         animationTimer_.stop();
         animationTimer_ = new Timer((int) (1000 / animationFPS_), action);
         animationTimer_.start();
      }
   }

   public void displayOverlay(Overlay overlay) {
      displayWindow_.displayOverlay(overlay);
   }

   public void showScaleBar(boolean selected) {
      overlayer_.setShowScaleBar(selected);
   }

   public boolean isCompositeMode() {
      return display_.getDisplayModel().isCompositeMode();
   }

   public void shutdown() {
      displayWindow_.onDisplayClose();

      imageMaker_.close();
      imageMaker_ = null;

      overlayer_.shutdown();
      overlayer_ = null;

      if (animationTimer_ != null) {
         animationTimer_.stop();
      }
      animationTimer_ = null;
      displayWindow_ = null;
   }

   public void displayNewImage(Image img, HashMap<String, int[]> hists, DataViewCoords view,
                               NDViewer2OverlayerPlugin overlayerPlugin) {
      if (displayWindow_ == null || overlayer_ == null) {
         return; // shutdown() already ran; discard stale repaint
      }
      displayWindow_.displayImage(img, hists, view);
      overlayer_.createOverlay(view, overlayerPlugin);
      displayWindow_.repaintCanvas();
   }

   public void setRenderSettings(Map<String, ChannelRenderSettings> channelSettings,
                                  GlobalRenderSettings globalSettings,
                                  ContrastUpdateCallback callback) {
      if (imageMaker_ != null) {
         imageMaker_.setRenderSettings(channelSettings, globalSettings, callback);
      }
   }

   public Image makeOrGetImage(DataViewCoords view) {
      return imageMaker_.makeOrGetImage(view);
   }

   public mmcorej.org.json.JSONObject getLatestTags() {
      return imageMaker_.getLatestTags();
   }

   public HashMap<String, int[]> getHistograms() {
      return imageMaker_.getHistograms();
   }

   public void expandDisplayedRangeToInclude(java.util.List<HashMap<String,
            Object>> newIamgeEvents, java.util.List<String> activeChannels) {
      if (displayWindow_ != null) {
         displayWindow_.expandDisplayedRangeToInclude(newIamgeEvents, activeChannels);
      }
   }

   public void setCustomCanvasMouseListener(NDViewer2CanvasMouseListenerInterface m) {
      displayWindow_.setCustomCanvasMouseListener(m);
   }

   public void setShowZPosition(boolean selected) {
      overlayer_.setShowZPosition(selected);
   }

   public void setShowTimeLabel(boolean selected) {
      overlayer_.setShowTimeLabel(selected);
   }

   public void updateActiveChannelCheckboxes() {
      // No-op: side controls panel has been removed.
   }

   public void addContrastControlsIfNeeded(String channelName) {
      // No-op: side controls panel has been removed.
   }

   public void readHistogramControlsStateFromGUI() {
      // No-op: side controls panel has been removed.
   }

   public void updateGUIFromDisplaySettings() {
      // No-op: side controls panel has been removed.
   }
}
