package org.micromanager.magellan.internal.gui;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acqj.main.XYTiledAcquisition;
import org.micromanager.acqj.util.xytiling.CameraTilingStageTranslator;
import org.micromanager.explore.ExploreAcquisition;
import org.micromanager.explore.gui.ExploreMouseListenerAPI;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.surfacesandregions.MultiPosGrid;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;
import org.micromanager.ndviewer.api.CanvasMouseListenerInterface;
import org.micromanager.ndviewer.main.NDViewer;

/**
 *
 * @author henrypinkard
 */
public class MagellanMouseListener  implements
      CanvasMouseListenerInterface, ExploreMouseListenerAPI {

   private static final int DELETE_SURF_POINT_PIXEL_TOLERANCE = 10;
   private boolean mouseDragging_ = false;
   private SurfaceGridPanel surfaceGridPanel_;

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
    * MouseListener that adds on moving and modifying of grids and surfaces
    */
   public MagellanMouseListener(NDViewer viewer, SurfaceGridPanel surfaceGridPanel,
                                XYTiledAcquisition acq) {
      surfaceGridPanel_ = surfaceGridPanel;
      acq_ = acq;
      viewer_ = viewer;
      logger_ =  new Consumer<String>() {
         @Override
         public void accept(String s) {
            Log.log(s);
         }
      };
      if (acq_ != null) {
         pixelStageTranslator_ = acq_.getPixelStageTranslator();
      }
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


   private double getZCoordinateOfDisplayedSlice(String name) {
      int index = (Integer) viewer_.getAxisPosition(name);
      return index * acq_.getZStep(Engine.getCore().getFocusDevice()) + acq_.getZOrigin(name);
   }

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

   protected void mouseReleasedActions(MouseEvent e) {
      if (acq_ instanceof ExploreAcquisition
            && !surfaceGridPanel_.isActive()
            && SwingUtilities.isLeftMouseButton(e)) {
         Point p2 = e.getPoint();
         if (exploreStartTile_ != null) {
            acquireTiles();
         } else {
            recordTilesForConfirmation(p2);
         }
         viewer_.redrawOverlay();
      } else if (surfaceGridPanel_.getCurrentSurfaceOrGrid() != null
              && surfaceGridPanel_.getCurrentSurfaceOrGrid()instanceof SurfaceInterpolator
              && surfaceGridPanel_.isCurrentlyEditableSurfaceGridVisible()) {
         SurfaceInterpolator currentSurface = (SurfaceInterpolator)
                 surfaceGridPanel_.getCurrentSurfaceOrGrid();
         if (SwingUtilities.isRightMouseButton(e) && !mouseDragging_) {
            double z = getZCoordinateOfDisplayedSlice(Engine.getCore().getFocusDevice());
            double zStep = acq_.getZStep(Engine.getCore().getFocusDevice());
            if (e.isShiftDown()) {
               //delete all points at slice
               currentSurface.deletePointsWithinZRange(Math.min(z - zStep / 2,
                               z + acq_.getZStep(Engine.getCore().getFocusDevice()) / 2),
                       Math.max(z - acq_.getZStep(Engine.getCore().getFocusDevice()) / 2,
                             z + zStep / 2));
            } else {
               //delete point if one is nearby
               Point2D.Double stagePos = acq_.getPixelStageTranslator().stageCoordsFromPixelCoords(
                       e.getPoint().x, e.getPoint().y, viewer_.getMagnification(),
                     viewer_.getViewOffset());
               //calculate tolerance
               Point2D.Double toleranceStagePos = acq_.getPixelStageTranslator()
                     .stageCoordsFromPixelCoords(
                       e.getPoint().x + DELETE_SURF_POINT_PIXEL_TOLERANCE,
                       e.getPoint().y + DELETE_SURF_POINT_PIXEL_TOLERANCE,
                       viewer_.getMagnification(), viewer_.getViewOffset());
               double stageDistanceTolerance = Math.sqrt((toleranceStagePos.x - stagePos.x)
                       * (toleranceStagePos.x - stagePos.x)
                       + (toleranceStagePos.y - stagePos.y) * (toleranceStagePos.y - stagePos.y));
               currentSurface.deleteClosestPoint(stagePos.x, stagePos.y, stageDistanceTolerance,
                       Math.min(z - zStep / 2, z + zStep / 2),
                       Math.max(z - zStep / 2, z + zStep / 2));
            }
         } else if (SwingUtilities.isLeftMouseButton(e)) {
            //convert to real coordinates in 3D space
            //Click point --> full res pixel point --> stage coordinate
            Point2D.Double stagePos = acq_.getPixelStageTranslator().stageCoordsFromPixelCoords(
                    e.getPoint().x, e.getPoint().y,
                    viewer_.getMagnification(), viewer_.getViewOffset());
            double z = getZCoordinateOfDisplayedSlice(Engine.getCore().getFocusDevice());
            if (currentSurface == null) {
               Log.log("Can't add point--No surface selected", true);
            } else {
               currentSurface.addPoint(stagePos.x, stagePos.y, z);
            }
         }
      }

      mouseDragging_ = false;
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
      mouseDragging_ = true;
      if (SwingUtilities.isRightMouseButton(e)) {
         //pan
         viewer_.pan(mouseDragStartPointRight_.x - currentPoint.x,
                 mouseDragStartPointRight_.y - currentPoint.y);
         mouseDragStartPointRight_ = currentPoint;
      } else if (SwingUtilities.isLeftMouseButton(e)) {
         //only move grid
         if (surfaceGridPanel_.isActive() && surfaceGridPanel_.getCurrentSurfaceOrGrid() != null
                 && surfaceGridPanel_.getCurrentSurfaceOrGrid() instanceof MultiPosGrid
                 && surfaceGridPanel_.isCurrentlyEditableSurfaceGridVisible()) {
            MultiPosGrid currentGrid = (MultiPosGrid) surfaceGridPanel_.getCurrentSurfaceOrGrid();
            int dx = (currentPoint.x - mouseDragStartPointLeft_.x);
            int dy = (currentPoint.y - mouseDragStartPointLeft_.y);
            //convert pixel dx dy to stage dx dy
            Point2D.Double p0 = acq_.getPixelStageTranslator().stageCoordsFromPixelCoords(
                    0, 0, viewer_.getMagnification(), viewer_.getViewOffset());
            Point2D.Double p1 = acq_.getPixelStageTranslator().stageCoordsFromPixelCoords(
                    dx, dy, viewer_.getMagnification(), viewer_.getViewOffset());
            currentGrid.translate(p1.x - p0.x, p1.y - p0.y);
            mouseDragStartPointLeft_ = currentPoint;
         }
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
      if (!surfaceGridPanel_.isActive()) {
         viewer_.redrawOverlay();
      }

   }

}
