package org.micromanager.ndviewer2.internal.gui;

import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import javax.swing.Timer;
import org.micromanager.ndviewer2.api.CanvasMouseListenerInterface;
import org.micromanager.ndviewer2.api.OverlayerPlugin;
import org.micromanager.ndviewer2.main.NDViewer;
import org.micromanager.ndviewer2.overlay.Overlay;



public class GuiManager {

   private DisplayWindow displayWindow_;

   private ImageMaker imageMaker_;
   private BaseOverlayer overlayer_;
   private Timer animationTimer_;
   private double animationFPS_ = 7;

   private NDViewer display_;

   public GuiManager(NDViewer ndViewer, boolean acquisition) {
      displayWindow_ = new DisplayWindow(ndViewer, !acquisition);

      overlayer_ = new BaseOverlayer(ndViewer);
      imageMaker_ = new ImageMaker(ndViewer, ndViewer.getDataSource());
      display_ = ndViewer;

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
               int newPos = (scoller.getPosition() + 1)
                       % (scoller.getMaximum() - scoller.getMinimum());
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
                               OverlayerPlugin overlayerPlugin) {
      displayWindow_.displayImage(img, hists, view);
      overlayer_.createOverlay(view, overlayerPlugin);
      displayWindow_.repaintCanvas();
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

   public void setCustomCanvasMouseListener(CanvasMouseListenerInterface m) {
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
