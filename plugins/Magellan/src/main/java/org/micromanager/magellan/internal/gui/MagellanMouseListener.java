/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.gui;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import javax.swing.SwingUtilities;
import org.micromanager.acqj.api.AcqEngMetadata;
import org.micromanager.magellan.internal.magellanacq.MagellanDataManager;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.surfacesandregions.MultiPosGrid;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;
import org.micromanager.ndviewer.api.CanvasMouseListenerInterface;

/**
 *
 * @author henrypinkard
 */
public class MagellanMouseListener implements CanvasMouseListenerInterface {

   private static final int DELETE_SURF_POINT_PIXEL_TOLERANCE = 10;
   private static final int MOUSE_WHEEL_ZOOM_INTERVAL_MS = 100;

   private static final double ZOOM_FACTOR_MOUSE = 1.4;

   //all these are volatile because they are accessed by overlayer
   private volatile Point mouseDragStartPointLeft_, mouseDragStartPointRight_, currentMouseLocation_;
   private volatile long lastMouseWheelZoomTime_ = 0;
   private volatile boolean mouseDragging_ = false;

   private MagellanDataManager manager_;
   private MagellanViewer viewer_;

   private volatile Point exploreStartTile_, exploreEndTile_;
   private boolean exploreMode_, surfaceMode_;

   public MagellanMouseListener(MagellanDataManager manager, MagellanViewer viewer) {
      manager_ = manager;
      viewer_ = viewer;
   }

   public void setSurfaceGridMode(boolean b) {
      surfaceMode_ = b;
      exploreMode_ = (!b) && manager_.isExploreAcquisition();
   }

   @Override
   public void mouseWheelMoved(MouseWheelEvent mwe) {
      long currentTime = System.currentTimeMillis();
      if (currentTime - lastMouseWheelZoomTime_ > MOUSE_WHEEL_ZOOM_INTERVAL_MS) {
         lastMouseWheelZoomTime_ = currentTime;
         if (mwe.getWheelRotation() < 0) {
            viewer_.zoom(1 / ZOOM_FACTOR_MOUSE, currentMouseLocation_); // zoom in?
         } else if (mwe.getWheelRotation() > 0) {
            viewer_.zoom(ZOOM_FACTOR_MOUSE, currentMouseLocation_); //zoom out
         }
      }
   }

   @Override
   public void mouseMoved(MouseEvent e) {
      currentMouseLocation_ = e.getPoint();
      if (exploreMode_) {
         viewer_.redrawOverlay();
      }
   }

   @Override
   public void mousePressed(MouseEvent e) {
      //to make zoom respond properly when switching between windows
      viewer_.getCanvasJPanel().requestFocusInWindow();
      if (SwingUtilities.isRightMouseButton(e)) {
         //clear potential explore region
         exploreEndTile_ = null;
         exploreStartTile_ = null;
         mouseDragStartPointRight_ = e.getPoint();
      } else if (SwingUtilities.isLeftMouseButton(e)) {
         mouseDragStartPointLeft_ = e.getPoint();
      }
      viewer_.redrawOverlay();
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
      if (exploreMode_) {
         viewer_.redrawOverlay();
      }
   }

   @Override
   public void mouseExited(MouseEvent e) {
      currentMouseLocation_ = null;
      if (exploreMode_) {
         viewer_.redrawOverlay();
      }
   }

   private void mouseReleasedActions(MouseEvent e) {
      if (exploreMode_ && SwingUtilities.isLeftMouseButton(e)) {
         Point p2 = e.getPoint();
         if (exploreStartTile_ != null) {
            //create events to acquire one or more tiles
            manager_.acquireTiles(
                    exploreStartTile_.y, exploreStartTile_.x, exploreEndTile_.y, exploreEndTile_.x);
            exploreStartTile_ = null;
            exploreEndTile_ = null;
         } else {
            //find top left row and column and number of columns spanned by drage event
            exploreStartTile_ = viewer_.getTileIndicesFromDisplayedPixel(
                    mouseDragStartPointLeft_.x, mouseDragStartPointLeft_.y);
            exploreEndTile_ = viewer_.getTileIndicesFromDisplayedPixel(p2.x, p2.y);
         }
         viewer_.redrawOverlay();
      } else if (surfaceMode_ && manager_.getCurrentEditableSurfaceOrGrid() != null
              && manager_.getCurrentEditableSurfaceOrGrid() instanceof SurfaceInterpolator
              && manager_.isCurrentlyEditableSurfaceGridVisible()) {
         SurfaceInterpolator currentSurface = (SurfaceInterpolator) manager_.getCurrentEditableSurfaceOrGrid();
         if (SwingUtilities.isRightMouseButton(e) && !mouseDragging_) {
            double z = manager_.getZCoordinateOfDisplayedSlice();
            if (e.isShiftDown()) {
               //delete all points at slice
               currentSurface.deletePointsWithinZRange(Math.min(z - manager_.getZStep() / 2, z + manager_.getZStep() / 2),
                       Math.max(z - manager_.getZStep() / 2, z + manager_.getZStep() / 2));
            } else {
               //delete point if one is nearby
               Point2D.Double stagePos = manager_.stageCoordsFromPixelCoords(e.getPoint().x, e.getPoint().y);
               //calculate tolerance
               Point2D.Double toleranceStagePos = manager_.stageCoordsFromPixelCoords(e.getPoint().x
                       + DELETE_SURF_POINT_PIXEL_TOLERANCE, e.getPoint().y + DELETE_SURF_POINT_PIXEL_TOLERANCE);
               double stageDistanceTolerance = Math.sqrt((toleranceStagePos.x - stagePos.x) * (toleranceStagePos.x - stagePos.x)
                       + (toleranceStagePos.y - stagePos.y) * (toleranceStagePos.y - stagePos.y));
               currentSurface.deleteClosestPoint(stagePos.x, stagePos.y, stageDistanceTolerance,
                       Math.min(z - manager_.getZStep() / 2, z + manager_.getZStep() / 2),
                       Math.max(z - manager_.getZStep() / 2, z + manager_.getZStep() / 2));
            }
         } else if (SwingUtilities.isLeftMouseButton(e)) {
            //convert to real coordinates in 3D space
            //Click point --> full res pixel point --> stage coordinate
            Point2D.Double stagePos = manager_.stageCoordsFromPixelCoords(e.getPoint().x, e.getPoint().y);
            double z = manager_.getZCoordinateOfDisplayedSlice();
            if (currentSurface == null) {
               Log.log("Can't add point--No surface selected", true);
            } else {
               currentSurface.addPoint(stagePos.x, stagePos.y, z);
            }
         }
      }
//      if (mouseDragging_ && SwingUtilities.isRightMouseButton(e)) {
//         //drag event finished, make sure pixels updated
//         display_.recomputeDisplayedImage();
//      }
      mouseDragging_ = false;
      viewer_.redrawOverlay();
   }

   @Override
   public void mouseDragged(MouseEvent e) {
      currentMouseLocation_ = e.getPoint();
      mouseDraggedActions(e);
   }

   private void mouseDraggedActions(MouseEvent e) {
      Point currentPoint = e.getPoint();
      mouseDragging_ = true;
      if (SwingUtilities.isRightMouseButton(e)) {
         //pan
         viewer_.pan(mouseDragStartPointRight_.x - currentPoint.x, mouseDragStartPointRight_.y - currentPoint.y);
         mouseDragStartPointRight_ = currentPoint;
      } else if (SwingUtilities.isLeftMouseButton(e)) {
         //only move grid
         if (surfaceMode_ && manager_.getCurrentEditableSurfaceOrGrid() != null
                 && manager_.getCurrentEditableSurfaceOrGrid() instanceof MultiPosGrid
                 && manager_.isCurrentlyEditableSurfaceGridVisible()) {
            MultiPosGrid currentGrid = (MultiPosGrid) manager_.getCurrentEditableSurfaceOrGrid();
            int dx = (currentPoint.x - mouseDragStartPointLeft_.x);
            int dy = (currentPoint.y - mouseDragStartPointLeft_.y);
            //convert pixel dx dy to stage dx dy
            Point2D.Double p0 = manager_.stageCoordsFromPixelCoords(0, 0);
            Point2D.Double p1 = manager_.stageCoordsFromPixelCoords(dx, dy);
            currentGrid.translate(p1.x - p0.x, p1.y - p0.y);
            mouseDragStartPointLeft_ = currentPoint;
         }
      }
      viewer_.redrawOverlay();
   }

   @Override
   public void mouseClicked(MouseEvent e) {
   }

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

}
