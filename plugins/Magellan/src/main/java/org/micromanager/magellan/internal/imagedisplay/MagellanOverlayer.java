///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
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
//
package org.micromanager.magellan.internal.imagedisplay;

import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import javax.swing.JPanel;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.micromanager.magellan.internal.acq.ExploreAcquisition;
import org.micromanager.magellan.internal.coordinates.XYStagePosition;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.misc.LongPoint;
import org.micromanager.magellan.internal.surfacesandregions.MultiPosGrid;
import org.micromanager.magellan.internal.surfacesandregions.Point3d;
import org.micromanager.magellan.internal.surfacesandregions.SingleResolutionInterpolation;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;
import org.micromanager.magellan.internal.surfacesandregions.XYFootprint;

/**
 * Class that encapsulates calculation of overlays for DisplayPlus
 */
public class MagellanOverlayer {

   private final static int INTERP_POINT_DIAMETER = 14;
   private final static int INITIAL_NUM_INTERPOLATION_DIVISIONS = 10;

   private final static Color ACTIVE_OBJECT_COLOR = Color.cyan;
   private final static Color BACKGROUND_OBJECT_COLOR = Color.orange;
   private static final Color LIGHT_BLUE = new Color(200, 200, 255);
   private static final Color DARK_BLUE = new Color(100, 100, 255);

   private static final int[] VIRIDIS_RED = {68, 68, 68, 69, 69, 69, 70, 70, 70, 70, 71, 71, 71, 71, 71, 71, 71, 72, 72, 72, 72, 72, 72, 72, 72, 72, 71, 71, 71, 71, 71, 71, 71, 70, 70, 70, 70, 69, 69, 69, 69, 68, 68, 67, 67, 67, 66, 66, 66, 65, 65, 64, 64, 63, 63, 62, 62, 61, 61, 61, 60, 60, 59, 59, 58, 58, 57, 57, 56, 56, 55, 55, 54, 54, 53, 53, 52, 52, 51, 51, 50, 50, 49, 49, 49, 48, 48, 47, 47, 46, 46, 46, 45, 45, 44, 44, 44, 43, 43, 42, 42, 42, 41, 41, 40, 40, 40, 39, 39, 39, 38, 38, 38, 37, 37, 36, 36, 36, 35, 35, 35, 34, 34, 34, 33, 33, 33, 32, 32, 32, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 31, 31, 31, 32, 32, 33, 33, 34, 35, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 46, 47, 48, 50, 51, 53, 54, 56, 57, 59, 61, 62, 64, 66, 68, 69, 71, 73, 75, 77, 79, 81, 83, 85, 87, 89, 91, 94, 96, 98, 100, 103, 105, 107, 109, 112, 114, 116, 119, 121, 124, 126, 129, 131, 134, 136, 139, 141, 144, 146, 149, 151, 154, 157, 159, 162, 165, 167, 170, 173, 175, 178, 181, 183, 186, 189, 191, 194, 197, 199, 202, 205, 207, 210, 212, 215, 218, 220, 223, 225, 228, 231, 233, 236, 238, 241, 243, 246, 248, 250, 253};
   private static final int[] VIRIDIS_GREEN = {1, 2, 3, 5, 6, 8, 9, 11, 12, 14, 15, 17, 18, 20, 21, 22, 24, 25, 26, 28, 29, 30, 32, 33, 34, 35, 37, 38, 39, 40, 42, 43, 44, 45, 47, 48, 49, 50, 52, 53, 54, 55, 57, 58, 59, 60, 61, 62, 64, 65, 66, 67, 68, 69, 71, 72, 73, 74, 75, 76, 77, 78, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 177, 178, 179, 180, 181, 182, 183, 184, 185, 185, 186, 187, 188, 189, 190, 190, 191, 192, 193, 194, 194, 195, 196, 197, 198, 198, 199, 200, 201, 201, 202, 203, 204, 204, 205, 206, 206, 207, 208, 208, 209, 210, 210, 211, 211, 212, 213, 213, 214, 214, 215, 215, 216, 216, 217, 217, 218, 218, 219, 219, 220, 220, 221, 221, 221, 222, 222, 223, 223, 223, 224, 224, 224, 225, 225, 225, 226, 226, 226, 227, 227, 227, 228, 228, 228, 229, 229, 229, 230, 230, 230, 231};
   private static final int[] VIRIDIS_BLUE = {84, 85, 87, 88, 90, 91, 92, 94, 95, 97, 98, 99, 101, 102, 103, 105, 106, 107, 108, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 124, 125, 126, 127, 127, 128, 129, 129, 130, 131, 131, 132, 132, 133, 133, 134, 134, 135, 135, 135, 136, 136, 137, 137, 137, 137, 138, 138, 138, 138, 139, 139, 139, 139, 139, 140, 140, 140, 140, 140, 140, 140, 141, 141, 141, 141, 141, 141, 141, 141, 141, 141, 141, 141, 141, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 141, 141, 141, 141, 141, 141, 141, 141, 141, 141, 141, 140, 140, 140, 140, 140, 140, 139, 139, 139, 139, 138, 138, 138, 138, 137, 137, 137, 136, 136, 136, 135, 135, 134, 134, 133, 133, 133, 132, 132, 131, 130, 130, 129, 129, 128, 127, 127, 126, 125, 125, 124, 123, 122, 122, 121, 120, 119, 118, 118, 117, 116, 115, 114, 113, 112, 111, 110, 109, 108, 107, 105, 104, 103, 102, 101, 100, 98, 97, 96, 95, 93, 92, 91, 89, 88, 86, 85, 84, 82, 81, 79, 78, 76, 75, 73, 71, 70, 68, 67, 65, 63, 62, 60, 58, 56, 55, 53, 51, 50, 48, 46, 44, 43, 41, 39, 38, 36, 34, 33, 31, 30, 29, 28, 27, 26, 25, 24, 24, 24, 24, 24, 25, 25, 26, 27, 28, 30, 31, 33, 34, 36};
   private static final Color TRANSPARENT_BLUE = new Color(0, 0, 255, 100);
   private static final Color TRANSPARENT_GREEN = new Color(0, 255, 0, 100);
   private static final Color TRANSPARENT_MAGENTA = new Color(255, 0, 255, 100);

   private volatile boolean showSurface_ = true, showConvexHull_ = true, showXYFootprint_ = true, showScalebar_ = false;
   private final int tileWidth_, tileHeight_;
   private ExecutorService taskExecutor_;
   private Future currentTask_;
   private MagellanDisplayController display_;
   private MagellanCoalescentEDTRunnablePool pool_;

   public MagellanOverlayer(MagellanDisplayController display, MagellanCoalescentEDTRunnablePool pool) {
      pool_ = pool;
      display_ = display;
      tileWidth_ = display.getTileWidth();
      tileHeight_ = display.getTileHeight();
      taskExecutor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {

         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Overalyer task thread");
         }
      });

   }
   
   public void setShowScaleBar(boolean show) {
      showScalebar_ = show;
   }

   public void setSurfaceDisplayParams(boolean surf, boolean footprint) {
      showXYFootprint_ = footprint;
      showSurface_ = surf;
   }

   public void shutdown() {
      taskExecutor_.shutdownNow();
   }

   //always try to cancel the previous task, assuming it is being replaced with a more current one
   public synchronized void redrawOverlay(MagellanDataViewCoords viewCoords) {
      if (currentTask_ != null && !currentTask_.isDone()) {
         //cancel current surface calculation--this call does not block until complete
         currentTask_.cancel(true);
      }
      currentTask_ = taskExecutor_.submit(new Runnable() {

         @Override
         public void run() {
            try {
               createAndRenderOverlay(viewCoords);
            } catch (InterruptedException ex) {
               return; //cancelld
            }
         }
      });
   }

   /**
    * Calculate the surface on a different thread, and block until it returns an
    * overlay, then add the rendering of that overlay back onto EDT.
    *
    * needs to support interrupts when new rendering instructions come from GUI
    *
    * @return
    */
   private void createAndRenderOverlay(MagellanDataViewCoords viewCoords) throws InterruptedException {

      //start out with 10 interpolation points across the whole image 
      int displayPixPerInterpPoint = (int) (Math.max(viewCoords.getDisplayImageSize().x,
              viewCoords.getDisplayImageSize().y) / INITIAL_NUM_INTERPOLATION_DIVISIONS);
      //keep redrawing until surface full interpolated  
      final Overlay startingOverlay = createEasyPartsOfOverlay(viewCoords);

      int maxMinPixPerInterpPoint = Integer.MAX_VALUE;
      while (true) {
         final Overlay surfOverlay = new Overlay();
         //add all objects from starting overlay rather than recalculating them each time
         for (int i = 0; i < startingOverlay.size(); i++) {
            if (Thread.interrupted()) {
               throw new InterruptedException();
            }
            surfOverlay.add(startingOverlay.get(i));
         }

         for (XYFootprint xy : getSurfacesAndGridsInDrawOrder()) {
            if (xy instanceof MultiPosGrid || ((SurfaceInterpolator) xy).getPoints().length < 3) {
               continue;
            }
            SurfaceInterpolator surface = ((SurfaceInterpolator) xy);

            SingleResolutionInterpolation interp = surface.waitForCurentInterpolation();
            //wait until surface is interpolated at sufficent resolution to draw
            while (displayPixPerInterpPoint * viewCoords.getDownsampleFactor() < interp.getPixelsPerInterpPoint()) {
               if (Thread.interrupted()) {
                  throw new InterruptedException();
               }
               surface.waitForHigherResolutionInterpolation();
               interp = surface.waitForCurentInterpolation();
            }
            //add surface interpolation
            addSurfaceInterpolation(surfOverlay, interp, displayPixPerInterpPoint, viewCoords);
            if (Thread.interrupted()) {
               throw new InterruptedException();
            }
            display_.setOverlay(surfOverlay);

            maxMinPixPerInterpPoint = Math.min(maxMinPixPerInterpPoint, surface.getMinPixelsPerInterpPoint());
         }
         displayPixPerInterpPoint /= 2;
         if (displayPixPerInterpPoint == 1) {
            return; //All rendered at full res
         }
         if (displayPixPerInterpPoint * viewCoords.getDownsampleFactor() <= maxMinPixPerInterpPoint) {
            //finished  
            return;
         }
      }
   }

   /**
    * Draw an initial version of the overlay that can be calculated quickly
    * subsequent calls will draw more detailed surface overlay renderings
    * Includes convex hull and interp points
    *
    * @return
    */
   public Overlay createEasyPartsOfOverlay(MagellanDataViewCoords viewCoords) {
      try {
         //determine appropriate overlay
         int mode = viewCoords.getOverlayMode();
         Overlay overlay = createBackgroundOverlay(viewCoords);
         if (showScalebar_) {
            addScaleBar(overlay, viewCoords);
         }
         if (mode == MagellanDataViewCoords.EXPLORE) {
            addExploreToOverlay(overlay);
            return overlay;
         } else if (mode == MagellanDataViewCoords.NONE) {
            return overlay;
         } else if (mode == MagellanDataViewCoords.SURFACE_AND_GRID) {
            //Add in the easier to render parts of all surfaces and grids
            ArrayList<XYFootprint> sAndg = getSurfacesAndGridsInDrawOrder();
            if (sAndg.isEmpty()) {
               String[] text = {"Grid and surface mode:",
                  "",
                  "Use the \"New Grid\" or \"New Surface\" buttons to the right to get started"};
               addTextBox(text, overlay);
            }
            //if any surfaces are visible show the interp scale bar
            boolean showSurfaceInterpScale = false;
            for (XYFootprint xy : sAndg) {
               if (xy instanceof SurfaceInterpolator && ((SurfaceInterpolator) xy).getPoints().length >= 3) {
                  showSurfaceInterpScale = true;
               }
            }
            if (showSurfaceInterpScale && showSurface_) {
               drawSurfaceInterpScaleBar(overlay, viewCoords);
            }
            for (XYFootprint xy : sAndg) {
               if (xy instanceof MultiPosGrid) {
                  addGridToOverlay(overlay, (MultiPosGrid) xy, viewCoords);
               } else {
                  //Do only fast version of surface overlay rendering, which don't require 
                  //any progress in the interpolation
                  addInterpPoints((SurfaceInterpolator) xy, overlay, viewCoords);
                  if (((SurfaceInterpolator) xy).getPoints().length >= 3) {
                     if (showConvexHull_) {
                        addConvexHull((SurfaceInterpolator) xy, overlay, viewCoords);
                     }
                     if (showXYFootprint_) {
                        addStagePositions((SurfaceInterpolator) xy, overlay, viewCoords);
                     }
                  } else if (((SurfaceInterpolator) xy).getPoints().length == 0) {
                     //Add surface instructions
                     String[] text = {"Surface creation (for non-rectangular/cuboidal acquisitions):",
                        "",
                        "Left click to add interpolation points",
                        "Right click to remove points",
                        "Shift + right click to remove all points on current Z slice"};
                     addTextBox(text, overlay);
                  }

               }
            }
            return overlay;
         } else {
            Log.log("Unkonwn display mode", true);
            throw new RuntimeException();
         }
      } catch (NullPointerException npe) {
         Log.log("Null pointer exception while creating overlay. written to stack ", false);
         npe.printStackTrace();
         return null;
      }
   }

   private void addScaleBar(Overlay overlay, MagellanDataViewCoords viewCoords) {
      int fontSize = 18;
      int scaleBarWidth = 100;
      Font font = new Font("Arial", Font.BOLD, fontSize);
      float textHeight = 0;
      float textWidth = 0;
      
      double pixelSize = display_.getPixelSize();
      pixelSize /= viewCoords.getDisplayToFullScaleFactor();
      double barSize = pixelSize * scaleBarWidth;
      String text = ((int) Math.round(barSize)) + " \u00B5" + "m";
      
      JPanel canvas = display_.getCanvas().getCanvas();
      textHeight = Math.max(textHeight, canvas.getGraphics().getFontMetrics(font
      ).getLineMetrics(text, canvas.getGraphics()).getHeight());
      textWidth = Math.max(textWidth, canvas.getGraphics().getFontMetrics().stringWidth(text));
   

     TextRoi troi = new TextRoi(canvas.getBounds().width - 100,
             viewCoords.getDisplayImageSize().y - 70, text, font);
      troi.setStrokeColor(Color.white);
      overlay.add(troi);

      Roi outline = new Roi(canvas.getBounds().width - 125,
             viewCoords.getDisplayImageSize().y - 70 + textHeight + 8, scaleBarWidth, 15);
      outline.setFillColor(Color.white);
      overlay.add(outline);
   }

   

   private void addExploreToOverlay(Overlay overlay) {
      Point currentMouseLocation = display_.getCurrentMouseLocation();
      if (display_.getExploreEndTile() != null) {
         //draw explore tiles waiting to be confirmed with a click
         highlightTilesOnOverlay(overlay, Math.min(display_.getExploreEndTile().y, display_.getExploreStartTile().y),
                 Math.max(display_.getExploreEndTile().y, display_.getExploreStartTile().y),
                 Math.min(display_.getExploreEndTile().x, display_.getExploreStartTile().x),
                 Math.max(display_.getExploreEndTile().x, display_.getExploreStartTile().x), TRANSPARENT_MAGENTA);
         addTextBox(new String[]{"Left click again to confirm acquire", "Right click to cancel"}, overlay);
      } else if (display_.getMouseDragStartPointLeft() != null) {
         //highlight multiple tiles when mouse dragging    
         Point dragStart = display_.getMouseDragStartPointLeft();
         Point p2Tiles = display_.getTileIndicesFromDisplayedPixel(currentMouseLocation.x, currentMouseLocation.y),
                 p1Tiles = display_.getTileIndicesFromDisplayedPixel(dragStart.x, dragStart.y);
         highlightTilesOnOverlay(overlay, Math.min(p1Tiles.y, p2Tiles.y), Math.max(p1Tiles.y, p2Tiles.y),
                 Math.min(p1Tiles.x, p2Tiles.x), Math.max(p1Tiles.x, p2Tiles.x), TRANSPARENT_BLUE);
      } else if (currentMouseLocation != null) {
         //draw single highlighted tile under mouse
         Point coords = display_.getTileIndicesFromDisplayedPixel(currentMouseLocation.x, currentMouseLocation.y);
         highlightTilesOnOverlay(overlay, coords.y, coords.y, coords.x, coords.x, TRANSPARENT_BLUE); //highligth single tile
      } else if (!display_.anythingAcquired()) {

         String[] text = {"Explore mode controls:", "", "Left click or left click and drag to select tiles",
            "Left click again to confirm", "Right click and drag to pan", "+/- keys or mouse wheel to zoom in/out"};
         addTextBox(text, overlay);
      }
//      always draw tiles waiting to be acquired 
      LinkedBlockingQueue<ExploreAcquisition.ExploreTileWaitingToAcquire> tiles
              = display_.getTilesWaitingToAcquireAtVisibleSlice();
      if (tiles != null) {
         for (ExploreAcquisition.ExploreTileWaitingToAcquire t : tiles) {
            highlightTilesOnOverlay(overlay, t.row, t.row, t.col, t.col, TRANSPARENT_GREEN);
         }
      }
   }

   private void addTextBox(String[] text, Overlay overlay) {
      int fontSize = 12;
      Font font = new Font("Arial", Font.BOLD, fontSize);
      float lineHeight = 0;
      float textWidth = 0;
      JPanel canvas = display_.getCanvas().getCanvas();
      for (String line : text) {
         lineHeight = Math.max(lineHeight, canvas.getGraphics().getFontMetrics(font).getLineMetrics(line, canvas.getGraphics()).getHeight());
         textWidth = Math.max(textWidth, canvas.getGraphics().getFontMetrics().stringWidth(line));
      }
      float textHeight = lineHeight * text.length;
      //10 pixel border 
      int border = 10;
      int roiWidth = (int) (textWidth + 2 * border);
      int roiHeight = (int) (textHeight + 2 * border);
      Roi rectangle = new Roi(canvas.getBounds().width / 2 - roiWidth / 2,
              canvas.getBounds().height / 2 - roiHeight / 2, roiWidth, roiHeight);
      rectangle.setStrokeWidth(3f);
      rectangle.setFillColor(LIGHT_BLUE);
      overlay.add(rectangle);

      for (int i = 0; i < text.length; i++) {
         TextRoi troi = new TextRoi(canvas.getBounds().width / 2 - roiWidth / 2 + border,
                 canvas.getBounds().height / 2 - roiHeight / 2 + border + lineHeight * i, text[i], font);
         troi.setStrokeColor(Color.black);
         overlay.add(troi);
      }
   }

   /**
    * creates background overlay that other modes should build upon Includes
    * scale bar and zoom indicator
    */
   private Overlay createBackgroundOverlay(MagellanDataViewCoords viewCoords) {
      Overlay overlay = new Overlay();
      if (!display_.isActiveExploreAcquisiton()) {
         drawZoomIndicator(overlay, viewCoords);
      }
      return overlay;
   }

   private void addInterpPoints(SurfaceInterpolator newSurface, Overlay overlay, MagellanDataViewCoords viewCoords) {
      if (newSurface == null) {
         return;
      }
      for (Point3d point : newSurface.getPoints()) {
         LongPoint displayLocation = display_.imageCoordsFromStageCoords(point.x, point.y, viewCoords);
         int displaySlice;
         displaySlice = display_.getSliceIndexFromZCoordinate(point.z);

         if (displaySlice != viewCoords.getAxisPosition("z")) {
            continue;
         }
         Roi circle = new OvalRoi(displayLocation.x_ - INTERP_POINT_DIAMETER / 2, displayLocation.y_ - INTERP_POINT_DIAMETER / 2, INTERP_POINT_DIAMETER, INTERP_POINT_DIAMETER);
         circle.setFillColor(getSurfaceGridLineColor(newSurface));
         circle.setStrokeColor(getSurfaceGridLineColor(newSurface));
         overlay.add(circle);
      }
   }

   private Color getSurfaceGridLineColor(XYFootprint xy) {
      if (xy == display_.getCurrentEditableSurfaceOrGrid()) {
         return ACTIVE_OBJECT_COLOR;
      }
      return BACKGROUND_OBJECT_COLOR;
   }

   private void addConvexHull(SurfaceInterpolator surface, Overlay overlay, MagellanDataViewCoords viewCoords) {
      //draw convex hull
      Vector2D[] hullPoints = surface.getConvexHullPoints();

      LongPoint lastPoint = null, firstPoint = null;
      for (Vector2D v : hullPoints) {
//         if (Thread.interrupted()) {
//            throw new InterruptedException();
//         }
         //convert to image coords
         LongPoint p = display_.imageCoordsFromStageCoords(v.getX(), v.getY(), viewCoords);
         if (lastPoint != null) {
            Line l = new Line(p.x_, p.y_, lastPoint.x_, lastPoint.y_);
            l.setStrokeColor(getSurfaceGridLineColor(surface));
            l.setStrokeWidth(5f);
            overlay.add(l);
         } else {
            firstPoint = p;
         }
         lastPoint = p;
      }
      //draw last connection         
      Line l = new Line(firstPoint.x_, firstPoint.y_, lastPoint.x_, lastPoint.y_);
      l.setStrokeColor(getSurfaceGridLineColor(surface));
      l.setStrokeWidth(5f);
      overlay.add(l);
   }

   private void addStagePositions(SurfaceInterpolator surface, Overlay overlay, MagellanDataViewCoords viewCoords) {
      List<XYStagePosition> positionsXY = surface.getXYPositionsNoUpdate();
      if (positionsXY == null) {
         if (surface.getPoints() != null && surface.getPoints().length >= 3) {
            display_.redrawOverlay();
         }
         return;
      }
      for (XYStagePosition pos : positionsXY) {
//         if (Thread.interrupted()) {
//            throw new InterruptedException();
//         }
         Point2D.Double[] corners = pos.getDisplayedTileCorners();
         LongPoint corner1 = display_.imageCoordsFromStageCoords(corners[0].x, corners[0].y, viewCoords);
         LongPoint corner2 = display_.imageCoordsFromStageCoords(corners[1].x, corners[1].y, viewCoords);
         LongPoint corner3 = display_.imageCoordsFromStageCoords(corners[2].x, corners[2].y, viewCoords);
         LongPoint corner4 = display_.imageCoordsFromStageCoords(corners[3].x, corners[3].y, viewCoords);
         //add lines connecting 4 corners
         Line l1 = new Line(corner1.x_, corner1.y_, corner2.x_, corner2.y_);
         Line l2 = new Line(corner2.x_, corner2.y_, corner3.x_, corner3.y_);
         Line l3 = new Line(corner3.x_, corner3.y_, corner4.x_, corner4.y_);
         Line l4 = new Line(corner4.x_, corner4.y_, corner1.x_, corner1.y_);
         l1.setStrokeColor(getSurfaceGridLineColor(surface));
         l2.setStrokeColor(getSurfaceGridLineColor(surface));
         l3.setStrokeColor(getSurfaceGridLineColor(surface));
         l4.setStrokeColor(getSurfaceGridLineColor(surface));
         overlay.add(l1);
         overlay.add(l2);
         overlay.add(l3);
         overlay.add(l4);
      }
   }

   //draw the surface itself by interpolating a grid over viewable area
   private void addSurfaceInterpolation(Overlay overlay, SingleResolutionInterpolation interp,
           int displayPixPerInterpTile, MagellanDataViewCoords viewCoords) throws InterruptedException {
      int width = (int) viewCoords.getDisplayImageSize().x;
      int height = (int) viewCoords.getDisplayImageSize().y;
      double sliceZ = display_.getZCoordinateOfDisplayedSlice();
      double zStep = display_.getZStep();

      //Make numTestPoints a factor of image size for clean display of surface
      int numTestPointsX = width / displayPixPerInterpTile;
      int numTestPointsY = height / displayPixPerInterpTile;
      double roiWidth = width / (double) numTestPointsX;
      double roiHeight = height / (double) numTestPointsY;

      for (int x = 0; x < numTestPointsX; x++) {
         for (int y = 0; y < numTestPointsY; y++) {
            if (Thread.interrupted()) {
               throw new InterruptedException();
            }
            Point2D.Double stageCoord = display_.stageCoordFromImageCoords((int) ((x + 0.5) * roiWidth), (int) ((y + 0.5) * roiHeight));
            if (!interp.isInterpDefined(stageCoord.x, stageCoord.y)) {
               continue;
            }
            float interpZ = interp.getInterpolatedValue(stageCoord.x, stageCoord.y);
            if (Math.abs(sliceZ - interpZ) < zStep / 2) {
               double roiX = roiWidth * x;
               double roiY = roiHeight * y;
               //calculate distance from last ROI for uninterrupted coverage of image
               int displayWidth = (int) (roiWidth * (x + 1)) - (int) (roiX);
               int displayHeight = (int) (roiHeight * (y + 1)) - (int) (roiY);
               Roi rect = new Roi(roiX, roiY, displayWidth, displayHeight); //make ROI
               int[] lutRed = VIRIDIS_RED;
               int[] lutBlue = VIRIDIS_BLUE;
               int[] lutGreen = VIRIDIS_GREEN;
               double colorScale = ((sliceZ - interpZ) / (zStep / 2) + 1) / 2; //between 0 and 1
               rect.setFillColor(new Color(lutRed[(int) (colorScale * lutRed.length)],
                       lutGreen[(int) (colorScale * lutGreen.length)], lutBlue[(int) (colorScale * lutBlue.length)], 175));
               overlay.add(rect);
            }
         }
      }
   }

   private Overlay addGridToOverlay(Overlay overlay, MultiPosGrid grid, MagellanDataViewCoords viewCoords) {
      double dsTileWidth, dsTileHeight;
      dsTileWidth = tileWidth_ * viewCoords.getDisplayToFullScaleFactor();
      dsTileHeight = tileHeight_ * viewCoords.getDisplayToFullScaleFactor();
      int roiWidth = (int) ((grid.numCols() * dsTileWidth));
      int roiHeight = (int) ((grid.numRows() * dsTileHeight));
      LongPoint displayCenter = display_.imageCoordsFromStageCoords(grid.center().x, grid.center().y, viewCoords);
      Roi rectangle = new Roi(displayCenter.x_ - roiWidth / 2, displayCenter.y_ - roiHeight / 2, roiWidth, roiHeight);
      rectangle.setStrokeWidth(5f);
      rectangle.setStrokeColor(getSurfaceGridLineColor(grid));

      Point displayTopLeft = new Point((int) (displayCenter.x_ - roiWidth / 2), (int) (displayCenter.y_ - roiHeight / 2));
      //draw boundries of tiles
      for (int row = 1; row < grid.numRows(); row++) {
         int yPos = (int) (displayTopLeft.y + row * dsTileHeight);
         Line l = new Line(displayTopLeft.x, yPos, displayTopLeft.x + roiWidth, yPos);
         l.setStrokeColor(getSurfaceGridLineColor(grid));
         overlay.add(l);
      }
      for (int col = 1; col < grid.numCols(); col++) {
         int xPos = (int) (displayTopLeft.x + col * dsTileWidth);
         Line l = new Line(xPos, displayTopLeft.y, xPos, displayTopLeft.y + roiHeight);
         l.setStrokeColor(getSurfaceGridLineColor(grid));
         overlay.add(l);
      }

      overlay.add(rectangle);
      return overlay;
   }

   private void highlightTilesOnOverlay(Overlay base, long row1, long row2, long col1, long col2, Color color) {
      LongPoint topLeft = display_.getDisplayedPixel(row1, col1);
      int width = (int) Math.round(tileWidth_ * (col2 - col1 + 1) * display_.getScale());
      int height = (int) Math.round(tileHeight_ * (row2 - row1 + 1) * display_.getScale());
      Roi rect = new Roi(topLeft.x_, topLeft.y_, width, height);
      rect.setFillColor(color);
      base.add(rect);
   }

   private void drawSurfaceInterpScaleBar(Overlay overlay, MagellanDataViewCoords viewCoords) {
      double sliceZ = viewCoords.getAxisPosition("z");
      double zStep = display_.getZStep();
      String label1 = String.format("%.1f", sliceZ - zStep / 2) + " μm";
      String label2 = String.format("%.1f", sliceZ) + " μm";
      String label3 = String.format("%.1f", sliceZ + zStep / 2) + " μm";

      int fontSize = 12;
      Font font = new Font("Arial", Font.BOLD, fontSize);
      Graphics g = display_.getCanvas().getCanvas().getGraphics();
      float textHeight = g.getFontMetrics(font).getLineMetrics(label1, g).getHeight();
      float textWidth = Math.max(Math.max(g.getFontMetrics().stringWidth(label1),
              g.getFontMetrics().stringWidth(label2)), g.getFontMetrics().stringWidth(label3));

      double scalePixelWidth = 10;
      double scalePixelHeight = 100;
      double borderSize = 2 + textHeight / 2;
      double scalePosXBuffer = 50;
      double offsetY = 10;

      //10 pixel border outside of scale
      Roi backgroundRect = new Roi(viewCoords.getDisplayImageSize().x - scalePosXBuffer - textWidth - borderSize, offsetY,
              scalePixelWidth + 2 * borderSize + textWidth, scalePixelHeight + 2 * borderSize);
      backgroundRect.setFillColor(new Color(230, 230, 230)); //magenta      
      overlay.add(backgroundRect);

      for (double y = 0; y < scalePixelHeight; y++) {
         Roi line = new Roi(viewCoords.getDisplayImageSize().x - scalePosXBuffer, offsetY + borderSize + y, scalePixelWidth, 1);
         double colorScale = y / scalePixelHeight;
         line.setFillColor(new Color(VIRIDIS_RED[(int) (colorScale * VIRIDIS_RED.length)],
                 VIRIDIS_GREEN[(int) (colorScale * VIRIDIS_GREEN.length)], VIRIDIS_BLUE[(int) (colorScale * VIRIDIS_BLUE.length)]));
         overlay.add(line);
      }

      //outline rectange
      Roi outline = new Roi(viewCoords.getDisplayImageSize().x - scalePosXBuffer, offsetY + borderSize, scalePixelWidth, scalePixelHeight);
      outline.setStrokeColor(Color.black);
      overlay.add(outline);
      //add three labels
      Roi labelTop = new TextRoi(viewCoords.getDisplayImageSize().x - scalePosXBuffer - textWidth, offsetY + borderSize - textHeight / 2, label1, font);
      labelTop.setStrokeColor(Color.black);
      overlay.add(labelTop);

      Roi labelMid = new TextRoi(viewCoords.getDisplayImageSize().x - scalePosXBuffer - textWidth, offsetY + borderSize + scalePixelHeight / 2 - textHeight / 2, label2, font);
      labelMid.setStrokeColor(Color.black);
      overlay.add(labelMid);

      Roi labelBot = new TextRoi(viewCoords.getDisplayImageSize().x - scalePosXBuffer - textWidth, offsetY + borderSize + scalePixelHeight - textHeight / 2, label3, font);
      labelBot.setStrokeColor(Color.black);
      overlay.add(labelBot);
   }

   private void drawZoomIndicator(Overlay overlay, MagellanDataViewCoords viewCoords) {
      int outerWidth = 100;
      long fullResHeight = viewCoords.yMax_ - viewCoords.yMin_;
      long fullResWidth = viewCoords.xMax_ - viewCoords.xMin_;

      Point2D.Double sourceDataSize = viewCoords.getSourceDataSize();
      int outerHeight = (int) ((double) fullResHeight / (double) fullResWidth * outerWidth);
      //draw outer rectangle representing full image
      Roi outerRect = new Roi(10, 10, outerWidth, outerHeight);
      outerRect.setStrokeColor(new Color(255, 0, 255)); //magenta
      int innerX = (int) Math.round(((double) outerWidth / (double) fullResWidth) * (viewCoords.getViewOffset().x - viewCoords.xMin_));
      int innerY = (int) Math.round(((double) outerHeight / (double) fullResHeight) * (viewCoords.getViewOffset().y - viewCoords.yMin_));
      //outer width * percentage of width of full images that is shown
      int innerWidth = (int) (outerWidth * ((double) sourceDataSize.x / fullResWidth));
      int innerHeight = (int) (outerHeight * ((double) sourceDataSize.y / fullResHeight));
      Roi innerRect = new Roi(10 + innerX, 10 + innerY, innerWidth, innerHeight);
      innerRect.setStrokeColor(new Color(255, 0, 255));
      if (outerWidth != innerWidth || outerHeight != innerHeight) { //dont draw if fully zoomed out
         overlay.add(outerRect);
         overlay.add(innerRect);
      }
   }

   //put the active one last in the list so that it gets drawn on top
   private ArrayList<XYFootprint> getSurfacesAndGridsInDrawOrder() {
      ArrayList<XYFootprint> list = display_.getSurfacesAndGridsForDisplay();
      if (list.contains(display_.getCurrentEditableSurfaceOrGrid())) {
         list.remove(display_.getCurrentEditableSurfaceOrGrid());
         list.add((XYFootprint) display_.getCurrentEditableSurfaceOrGrid());
      }
      return list;
   }

}
