package org.micromanager.magellan.internal.explore.gui;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.micromanager.acqj.main.XYTiledAcquisition;
import org.micromanager.acqj.util.xytiling.CameraTilingStageTranslator;
import org.micromanager.magellan.internal.explore.ExploreAcquisition;
import org.micromanager.ndviewer.api.CanvasMouseListenerInterface;
import org.micromanager.ndviewer.main.NDViewer;

/**
 *
 * @author henrypinkard
 */
public class ExploreMouseListener implements CanvasMouseListenerInterface, ExploreMouseListenerAPI {

   protected static final int MOUSE_WHEEL_ZOOM_INTERVAL_MS = 100;

   protected static final double ZOOM_FACTOR_MOUSE = 1.4;

   //all these are volatile because they are accessed by overlayer
   protected volatile Point mouseDragStartPointLeft_;
   protected volatile Point mouseDragStartPointRight_;
   protected volatile Point currentMouseLocation_;
   protected volatile long lastMouseWheelZoomTime_ = 0;

   protected XYTiledAcquisition acq_;
   protected NDViewer viewer_;

   protected volatile Point exploreStartTile_;
   protected volatile Point exploreEndTile_;
   protected Consumer<String> logger_;

   private CameraTilingStageTranslator pixelStageTranslator_;

   /**
    * Mouse Listener class for paning zooming, and clicking to explore tiles.
    *
    * @param acquisition
    * @param viewer
    * @param logger
    */
   public ExploreMouseListener(XYTiledAcquisition acquisition, NDViewer viewer,
                               Consumer<String> logger) {
      acq_ = acquisition;
      viewer_ = viewer;
      logger_ = logger;
      pixelStageTranslator_ = acq_.getPixelStageTranslator();
   }

   @Override
   public void mouseWheelMoved(MouseWheelEvent mwe) {
      long currentTime = System.currentTimeMillis();
      if (currentTime - lastMouseWheelZoomTime_ > MOUSE_WHEEL_ZOOM_INTERVAL_MS) {
         lastMouseWheelZoomTime_ = currentTime;
         mouseWheelMovedActions(mwe);
      }
   }


   @Override
   public void mouseMoved(MouseEvent e) {
      currentMouseLocation_ = e.getPoint();
      mouseMovedActions(e);
   }


   @Override
   public void mousePressed(MouseEvent e) {
      //to make zoom respond properly when switching between windows
      viewer_.getCanvasJPanel().requestFocusInWindow();
      if (SwingUtilities.isRightMouseButton(e)) {
         mouseDragStartPointRight_ = e.getPoint();
      } else if (SwingUtilities.isLeftMouseButton(e)) {
         mouseDragStartPointLeft_ = e.getPoint();
      }
      mousePressedActions(e);
   }

   @Override
   public void mouseReleased(MouseEvent e) {
      mouseReleasedActions(e);
      mouseDragStartPointLeft_ = null;
      mouseDragStartPointRight_ = null;
      viewer_.redrawOverlay();
   }

   @Override
   public void mouseEntered(MouseEvent e) {
      mouseEnteredActions(e);
   }

   @Override
   public void mouseExited(MouseEvent e) {
      currentMouseLocation_ = null;
      mouseExitedActions(e);
   }

   @Override
   public void mouseDragged(MouseEvent e) {
      currentMouseLocation_ = e.getPoint();
      mouseDraggedActions(e);
   }


   @Override
   public void mouseClicked(MouseEvent e) {
   }

   /////////////////////////////  Getters used by the overlayer /////////////////////////////
   public Point getExploreStartTile() {
      return exploreStartTile_;
   }

   public Point getExploreEndTile() {
      return exploreEndTile_;
   }

   public Point getMouseDragStartPointLeft() {
      return mouseDragStartPointLeft_;
   }

   public Point getCurrentMouseLocation() {
      return currentMouseLocation_;
   }

   //// Submethods for actions below

   protected void acquireTiles() {
      //create events to acquire one or more tiles
      ((ExploreAcquisition) acq_).acquireTiles(
              exploreStartTile_.y, exploreStartTile_.x, exploreEndTile_.y, exploreEndTile_.x);
      exploreStartTile_ = null;
      exploreEndTile_ = null;
   }

   protected void recordTilesForConfirmation(Point p2) {
      //find top left row and column and number of columns spanned by drage event
      exploreStartTile_ = pixelStageTranslator_.getTileIndicesFromDisplayedPixel(
              viewer_.getMagnification(),
              mouseDragStartPointLeft_.x, mouseDragStartPointLeft_.y,
              viewer_.getViewOffset().x, viewer_.getViewOffset().y);
      exploreEndTile_ = pixelStageTranslator_.getTileIndicesFromDisplayedPixel(
              viewer_.getMagnification(),
              p2.x, p2.y,
              viewer_.getViewOffset().x, viewer_.getViewOffset().y);
   }

   ///////////////////////////  Actions in addition to storing states /////////////////////////////
   // These can be overriden by subclasses to add functionality

   protected void mouseReleasedActions(MouseEvent e) {
      if (SwingUtilities.isLeftMouseButton(e)) {
         Point p2 = e.getPoint();
         if (exploreStartTile_ != null) {
            acquireTiles();
         } else {
            recordTilesForConfirmation(p2);
         }
         viewer_.redrawOverlay();
      }
      viewer_.redrawOverlay();
   }

   protected void mousePressedActions(MouseEvent e) {
      if (SwingUtilities.isRightMouseButton(e)) {
         //clear potential explore region
         exploreEndTile_ = null;
         exploreStartTile_ = null;
      }
      viewer_.redrawOverlay();
   }

   protected void mouseExitedActions(MouseEvent e) {
      viewer_.redrawOverlay();

   }

   protected void mouseEnteredActions(MouseEvent e) {
      viewer_.redrawOverlay();
   }

   protected void mouseDraggedActions(MouseEvent e) {
      Point currentPoint = e.getPoint();
      if (SwingUtilities.isRightMouseButton(e)) {
         //pan
         viewer_.pan(mouseDragStartPointRight_.x - currentPoint.x,
                 mouseDragStartPointRight_.y - currentPoint.y);
         mouseDragStartPointRight_ = currentPoint;
      }
      viewer_.redrawOverlay();
   }


   protected void mouseWheelMovedActions(MouseWheelEvent mwe) {
      if (mwe.getWheelRotation() < 0) {
         viewer_.zoom(1 / ZOOM_FACTOR_MOUSE, currentMouseLocation_); // zoom in?
      } else if (mwe.getWheelRotation() > 0) {
         viewer_.zoom(ZOOM_FACTOR_MOUSE, currentMouseLocation_); //zoom out
      }
      viewer_.redrawOverlay();
   }

   protected void mouseMovedActions(MouseEvent e) {
      viewer_.redrawOverlay();

   }

}
