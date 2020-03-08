///*
// * To change this license header, choose License Headers in Project Properties.
// * To change this template file, choose Tools | Templates
// * and open the template in the editor.
// */
//package org.micromanager.magellan.internal.magellanacq;
//
//import java.awt.Point;
//import java.awt.event.MouseEvent;
//import java.awt.geom.Point2D;
//import org.micromanager.ndviewer.internal.gui.CanvasMouseListener;
//import javax.swing.SwingUtilities;
//import org.micromanager.magellan.internal.misc.Log;
//
///**
// *
// * @author henrypinkard
// */
//public class MagellanMouseListener {
//
//   private volatile Point exploreStartTile_, exploreEndTile_;
//
//   public MagellanMouseListener( MagellanViewer display) {
////      super(display);
//   }
//
//   @Override
//   public void mousePressed(MouseEvent e) {
////      //to make zoom respond properly when switching between windows
////      display_.getCanvas().getCanvas().requestFocusInWindow();
////      if (SwingUtilities.isRightMouseButton(e)) {
////         //clear potential explore region
////         exploreEndTile_ = null;
////         exploreStartTile_ = null;
////         mouseDragStartPointRight_ = e.getPoint();
////      } else if (SwingUtilities.isLeftMouseButton(e)) {
////         mouseDragStartPointLeft_ = e.getPoint();
////      }
////      display_.redrawOverlay();
//   }
//
//   @Override
//   public void mouseReleased(MouseEvent e) {
//      mouseReleasedActions(e);
//      mouseDragStartPointLeft_ = null;
//      mouseDragStartPointRight_ = null;
//      display_.redrawOverlay();
//   }
//
//   @Override
//   public void mouseEntered(MouseEvent e) {
////      if (cache_.isExploreMode()) {
////         display_.redrawOverlay();
////      }
//   }
//
//   @Override
//   public void mouseExited(MouseEvent e) {
////      currentMouseLocation_ = null;
////      if (cache_.isExploreMode()) {
////         display_.redrawOverlay();
////      }
//   }
//
//   private void mouseReleasedActions(MouseEvent e) {
////      if (cache_.isExploreMode() && SwingUtilities.isLeftMouseButton(e)) {
////         Point p2 = e.getPoint();
////         if (exploreStartTile_ != null) {
////            //create events to acquire one or more tiles
////            display_.acquireTiles(exploreStartTile_.y, exploreStartTile_.x, exploreEndTile_.y, exploreEndTile_.x);
////            exploreStartTile_ = null;
////            exploreEndTile_ = null;
////         } else {
////            //find top left row and column and number of columns spanned by drage event
////            exploreStartTile_ = display_.getTileIndicesFromDisplayedPixel(mouseDragStartPointLeft_.x, mouseDragStartPointLeft_.y);
////            exploreEndTile_ = display_.getTileIndicesFromDisplayedPixel(p2.x, p2.y);
////         }
////         display_.redrawOverlay();
////      } else if (display_.getOverlayMode() == SURFACE_AND_GRID && display_.getCurrentEditableSurfaceOrGrid() != null
////              && display_.getCurrentEditableSurfaceOrGrid() instanceof SurfaceInterpolator
////              && display_.isCurrentlyEditableSurfaceGridVisible()) {
////         SurfaceInterpolator currentSurface = (SurfaceInterpolator) display_.getCurrentEditableSurfaceOrGrid();
////         if (SwingUtilities.isRightMouseButton(e) && !mouseDragging_) {
////            double z = display_.getZCoordinateOfDisplayedSlice();
////            if (e.isShiftDown()) {
////               //delete all points at slice
////               currentSurface.deletePointsWithinZRange(Math.min(z - display_.getZStep() / 2, z + display_.getZStep() / 2),
////                       Math.max(z - display_.getZStep() / 2, z + display_.getZStep() / 2));
////            } else {
////               //delete point if one is nearby
////               Point2D.Double stagePos = display_.stageCoordFromImageCoords(e.getPoint().x, e.getPoint().y);
////               //calculate tolerance
////               Point2D.Double toleranceStagePos = display_.stageCoordFromImageCoords(e.getPoint().x + DELETE_SURF_POINT_PIXEL_TOLERANCE, e.getPoint().y + DELETE_SURF_POINT_PIXEL_TOLERANCE);
////               double stageDistanceTolerance = Math.sqrt((toleranceStagePos.x - stagePos.x) * (toleranceStagePos.x - stagePos.x)
////                       + (toleranceStagePos.y - stagePos.y) * (toleranceStagePos.y - stagePos.y));
////               currentSurface.deleteClosestPoint(stagePos.x, stagePos.y, stageDistanceTolerance,
////                       Math.min(z - display_.getZStep() / 2, z + display_.getZStep() / 2),
////                       Math.max(z - display_.getZStep() / 2, z + display_.getZStep() / 2));
////            }
////         } else if (SwingUtilities.isLeftMouseButton(e)) {
////            //convert to real coordinates in 3D space
////            //Click point --> full res pixel point --> stage coordinate
////            Point2D.Double stagePos = display_.stageCoordFromImageCoords(e.getPoint().x, e.getPoint().y);
////            double z = display_.getZCoordinateOfDisplayedSlice();
////            if (currentSurface == null) {
////               Log.log("Can't add point--No surface selected", true);
////            } else {
////               currentSurface.addPoint(stagePos.x, stagePos.y, z);
////            }
////         }
////      }
//////      if (mouseDragging_ && SwingUtilities.isRightMouseButton(e)) {
//////         //drag event finished, make sure pixels updated
//////         display_.recomputeDisplayedImage();
//////      }
////      mouseDragging_ = false;
////      display_.redrawOverlay();
//   }
//
//   private void mouseDraggedActions(MouseEvent e) {
////      Point currentPoint = e.getPoint();
////      mouseDragging_ = true;
////      if (SwingUtilities.isRightMouseButton(e)) {
////         //pan
////         display_.pan(mouseDragStartPointRight_.x - currentPoint.x, mouseDragStartPointRight_.y - currentPoint.y);
////         mouseDragStartPointRight_ = currentPoint;
////      } else if (SwingUtilities.isLeftMouseButton(e)) {
////         //only move grid
////         if (display_.getOverlayMode() == SURFACE_AND_GRID && display_.getCurrentEditableSurfaceOrGrid() != null
////                 && display_.getCurrentEditableSurfaceOrGrid() instanceof MultiPosGrid
////                 && display_.isCurrentlyEditableSurfaceGridVisible()) {
////            MultiPosGrid currentGrid = (MultiPosGrid) display_.getCurrentEditableSurfaceOrGrid();
////            int dx = (currentPoint.x - mouseDragStartPointLeft_.x);
////            int dy = (currentPoint.y - mouseDragStartPointLeft_.y);
////            //convert pixel dx dy to stage dx dy
////            Point2D.Double p0 = display_.stageCoordFromImageCoords(0, 0);
////            Point2D.Double p1 = display_.stageCoordFromImageCoords(dx, dy);
////            currentGrid.translate(p1.x - p0.x, p1.y - p0.y);
////            mouseDragStartPointLeft_ = currentPoint;
////         }
////      }
////      display_.redrawOverlay();
//   }
//
//   public Point getExploreStartTile() {
//      return exploreStartTile_;
//   }
//
//   public Point getExploreEndTile() {
//      return exploreEndTile_;
//   }
//
//   Point getMouseDragStartPointLeft() {
//      return mouseDragStartPointLeft_;
//   }
//
//   Point getCurrentMouseLocation() {
//      return currentMouseLocation_;
//   }
//
//}
