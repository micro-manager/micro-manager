package org.micromanager.magellan.internal.gui;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

import org.micromanager.explore.ExploreAcquisition;
import org.micromanager.explore.gui.ExploreMouseListener;
import org.micromanager.magellan.internal.magellanacq.MagellanUIViewerStorageAdapater;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.surfacesandregions.MultiPosGrid;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;
import org.micromanager.ndviewer.main.NDViewer;

/**
 *
 * @author henrypinkard
 */
public class MagellanMouseListener extends ExploreMouseListener {

   private static final int DELETE_SURF_POINT_PIXEL_TOLERANCE = 10;
   private static final int MOUSE_WHEEL_ZOOM_INTERVAL_MS = 100;

   private static final double ZOOM_FACTOR_MOUSE = 1.4;

   //all these are volatile because they are accessed by overlayer
   private volatile Point mouseDragStartPointLeft_;
   private volatile Point mouseDragStartPointRight_;
   private volatile Point currentMouseLocation_;
   private volatile long lastMouseWheelZoomTime_ = 0;
   private volatile boolean mouseDragging_ = false;

   private MagellanUIViewerStorageAdapater manager_;
   private NDViewer viewer_;

   private volatile Point exploreStartTile_;
   private volatile Point exploreEndTile_;
   private boolean exploreMode_;
   private boolean surfaceMode_;
   private SurfaceGridPanel surfaceGridPanel_;
   private ExploreAcquisition exploreAcquisition_;


   /**
    * MouseListener that adds on moving and modifying of grids and surfaces
    */
   public MagellanMouseListener(MagellanUIViewerStorageAdapater manager,
                                NDViewer viewer, ExploreAcquisition eAcq,
                                SurfaceGridPanel surfaceGridPanel
                                ) {
      super(manager, eAcq, viewer, new Consumer<String>() {
         @Override
         public void accept(String s) {
            Log.log(s);
         }
      });
      manager_ = manager;
      viewer_ = viewer;
      surfaceGridPanel_ = surfaceGridPanel;
      exploreAcquisition_ = eAcq;
   }

   public void setSurfaceGridMode(boolean b) {
      surfaceMode_ = b;
      if( manager_.isExploreAcquisition()) {
         setExploreActive(!b);
      }
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


   protected void mouseReleasedActions(MouseEvent e) {

      if (surfaceMode_ && surfaceGridPanel_.getCurrentSurfaceOrGrid() != null
              && surfaceGridPanel_.getCurrentSurfaceOrGrid() instanceof SurfaceInterpolator
              && surfaceGridPanel_.isCurrentlyEditableSurfaceGridVisible()) {
         SurfaceInterpolator currentSurface = (SurfaceInterpolator)
                 surfaceGridPanel_.getCurrentSurfaceOrGrid();
         if (SwingUtilities.isRightMouseButton(e) && !mouseDragging_) {
            double z = manager_.getZCoordinateOfDisplayedSlice();
            if (e.isShiftDown()) {
               //delete all points at slice
               currentSurface.deletePointsWithinZRange(Math.min(z - manager_.getZStep() / 2,
                           z + manager_.getZStep() / 2),
                       Math.max(z - manager_.getZStep() / 2, z + manager_.getZStep() / 2));
            } else {
               //delete point if one is nearby
               Point2D.Double stagePos = manager_.stageCoordsFromPixelCoords(e.getPoint().x,
                     e.getPoint().y);
               //calculate tolerance
               Point2D.Double toleranceStagePos = manager_.stageCoordsFromPixelCoords(
                     e.getPoint().x
                       + DELETE_SURF_POINT_PIXEL_TOLERANCE, e.getPoint().y
                           + DELETE_SURF_POINT_PIXEL_TOLERANCE);
               double stageDistanceTolerance = Math.sqrt((toleranceStagePos.x - stagePos.x)
                     * (toleranceStagePos.x - stagePos.x)
                       + (toleranceStagePos.y - stagePos.y) * (toleranceStagePos.y - stagePos.y));
               currentSurface.deleteClosestPoint(stagePos.x, stagePos.y, stageDistanceTolerance,
                       Math.min(z - manager_.getZStep() / 2, z + manager_.getZStep() / 2),
                       Math.max(z - manager_.getZStep() / 2, z + manager_.getZStep() / 2));
            }
         } else if (SwingUtilities.isLeftMouseButton(e)) {
            //convert to real coordinates in 3D space
            //Click point --> full res pixel point --> stage coordinate
            Point2D.Double stagePos = manager_.stageCoordsFromPixelCoords(e.getPoint().x,
                  e.getPoint().y);
            double z = manager_.getZCoordinateOfDisplayedSlice();
            if (currentSurface == null) {
               Log.log("Can't add point--No surface selected", true);
            } else {
               currentSurface.addPoint(stagePos.x, stagePos.y, z);
            }
         }
      }

      if (exploreMode_) {
         super.mouseReleasedActions(e);
      }

      mouseDragging_ = false;
      viewer_.redrawOverlay();
   }


   private void mouseDraggedActions(MouseEvent e) {
      Point currentPoint = e.getPoint();
      mouseDragging_ = true;
      if (SwingUtilities.isRightMouseButton(e)) {
         //pan
         viewer_.pan(mouseDragStartPointRight_.x - currentPoint.x,
               mouseDragStartPointRight_.y - currentPoint.y);
         mouseDragStartPointRight_ = currentPoint;
      } else if (SwingUtilities.isLeftMouseButton(e)) {
         //only move grid
         if (surfaceMode_ && surfaceGridPanel_.getCurrentSurfaceOrGrid() != null
                 && surfaceGridPanel_.getCurrentSurfaceOrGrid() instanceof MultiPosGrid
                 && surfaceGridPanel_.isCurrentlyEditableSurfaceGridVisible()) {
            MultiPosGrid currentGrid = (MultiPosGrid) surfaceGridPanel_.getCurrentSurfaceOrGrid();
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

}
