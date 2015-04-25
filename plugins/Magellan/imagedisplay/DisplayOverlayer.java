package imagedisplay;

import acq.Acquisition;
import acq.ExploreAcquisition;
import acq.FixedAreaAcquisition;
import coordinates.XYStagePosition;
import ij.IJ;
import ij.gui.ImageCanvas;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import javax.swing.SwingUtilities;
import misc.LongPoint;
import mmcloneclasses.graph.ContrastPanel;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.micromanager.utils.ReportingUtils;
import surfacesandregions.SingleResolutionInterpolation;
import surfacesandregions.MultiPosRegion;
import surfacesandregions.Point3d;
import surfacesandregions.SurfaceInterpolator;

/**
 * Class that encapsulates calculation of overlays for DisplayPlus
 */
public class DisplayOverlayer {

   private final static int INTERP_POINT_DIAMETER = 4;
   private final static int INITIAL_NUM_INTERPOLATION_DIVISIONS = 10;
   private final static Color INTERP_POINT_COLOR = Color.orange;
   private final static Color CONVEX_HULL_COLOR = Color.GREEN;
   private static final Color NEW_GRID_COLOR = Color.red;
   private static final int[] ICE_RED = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 4, 7, 9, 11, 14, 16, 19, 20, 21, 22, 24, 25, 26, 27, 29, 31, 34, 36, 39, 42, 44, 47, 50, 49, 49, 49, 49, 48, 48, 48, 48, 51, 55, 59, 63, 67, 71, 75, 79, 83, 87, 91, 95, 99, 103, 107, 112, 114, 117, 120, 123, 125, 128, 131, 134, 137, 140, 143, 146, 149, 152, 155, 158, 161, 165, 168, 172, 175, 179, 182, 186, 187, 189, 191, 193, 195, 197, 199, 201, 203, 205, 207, 209, 211, 213, 215, 217, 218, 220, 221, 223, 224, 226, 227, 229, 230, 232, 233, 235, 237, 238, 240, 242, 243, 244, 245, 246, 247, 248, 249, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 251, 251, 251, 251, 251, 251, 251, 251, 251, 250, 249, 248, 247, 246, 245, 244, 243, 241, 239, 238, 236, 234, 233, 231, 230, 230, 230, 230, 230, 230, 230, 230};
   private static final int[] ICE_GREEN = {156, 157, 158, 159, 160, 161, 162, 163, 165, 166, 167, 169, 170, 171, 173, 174, 176, 177, 178, 179, 180, 181, 182, 183, 184, 184, 185, 186, 187, 187, 188, 189, 190, 190, 191, 192, 193, 193, 194, 195, 196, 195, 195, 194, 194, 194, 193, 193, 193, 191, 190, 189, 188, 187, 186, 185, 184, 182, 180, 179, 177, 175, 174, 172, 171, 169, 168, 167, 166, 165, 164, 163, 162, 160, 158, 156, 154, 152, 150, 148, 146, 143, 140, 138, 135, 132, 130, 127, 125, 122, 120, 118, 116, 113, 111, 109, 107, 105, 103, 101, 100, 98, 96, 94, 93, 91, 90, 88, 87, 85, 84, 82, 81, 81, 82, 83, 84, 84, 85, 86, 87, 87, 88, 88, 89, 90, 90, 91, 92, 92, 93, 93, 94, 95, 95, 96, 97, 96, 96, 96, 96, 95, 95, 95, 95, 94, 94, 94, 94, 93, 93, 93, 93, 93, 93, 93, 93, 93, 93, 93, 93, 92, 92, 91, 91, 91, 90, 90, 90, 89, 88, 88, 87, 86, 86, 85, 85, 83, 81, 79, 77, 75, 73, 71, 69, 68, 67, 67, 66, 65, 65, 64, 64, 62, 61, 60, 59, 57, 56, 55, 54, 53, 52, 51, 50, 49, 48, 47, 47, 45, 44, 42, 41, 39, 38, 36, 35, 33, 31, 29, 27, 25, 23, 21, 19, 16, 14, 11, 9, 7, 4, 2, 0, 0, 1, 1, 2, 2, 3, 3, 4, 3, 3, 2, 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0};
   private static final int[] ICE_BLUE = {140, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 151, 152, 153, 155, 156, 158, 159, 160, 161, 162, 163, 164, 165, 166, 166, 167, 167, 168, 168, 169, 169, 170, 170, 171, 172, 173, 173, 174, 175, 176, 180, 184, 188, 192, 196, 200, 204, 209, 210, 211, 213, 214, 215, 217, 218, 220, 221, 223, 225, 227, 228, 230, 232, 234, 232, 231, 230, 229, 228, 227, 226, 225, 226, 227, 229, 230, 231, 233, 234, 236, 237, 238, 239, 241, 242, 243, 244, 246, 246, 247, 247, 248, 248, 249, 249, 250, 250, 250, 250, 250, 250, 250, 250, 251, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 250, 249, 248, 248, 247, 246, 246, 245, 245, 243, 241, 239, 237, 235, 233, 231, 230, 230, 230, 230, 230, 230, 230, 230, 230, 229, 228, 227, 226, 225, 224, 223, 222, 219, 217, 214, 212, 209, 207, 204, 202, 199, 196, 193, 191, 188, 185, 182, 180, 177, 175, 173, 171, 169, 167, 165, 163, 160, 157, 155, 152, 149, 147, 144, 142, 139, 137, 134, 132, 130, 127, 125, 123, 121, 120, 119, 118, 117, 116, 115, 114, 113, 112, 111, 110, 109, 108, 107, 106, 104, 103, 101, 100, 98, 97, 95, 94, 92, 91, 90, 89, 87, 86, 85, 84, 81, 79, 76, 74, 71, 69, 66, 64, 59, 54, 49, 45, 40, 35, 30, 26, 26, 26, 26, 26, 26, 26, 26, 27, 27, 27, 27, 27, 27, 27, 27};
   private static final Color TRANSPARENT_BLUE = new Color(0, 0, 255, 100);
   private static final Color TRANSPARENT_GREEN = new Color(0, 255, 0, 100);
   private static final Color TRANSPARENT_MAGENTA = new Color(255, 0, 255, 100);
   private DisplayPlus display_;
   private Acquisition acq_;
   private volatile boolean showSurface_ = true, showConvexHull_ = true, showStagePositions_ = true;
   private ZoomableVirtualStack zoomableStack_;
   private ImageCanvas canvas_;
   private final int tileWidth_, tileHeight_;

   private ExecutorService taskExecutor_, overlayMakerExecutor_;
   private Future currentTask_;
   
  
   public DisplayOverlayer(DisplayPlus display, Acquisition acq, int tileWidth, int tileHeight, ZoomableVirtualStack stack) {
      display_ = display;
      tileWidth_ = tileWidth;
      tileHeight_ = tileHeight;
      acq_ = acq;
      canvas_ = display.getImagePlus().getCanvas();
      zoomableStack_ = stack;
      createExecutors();
      
   }
   
   private void createExecutors() {
      taskExecutor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {
         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, acq_.getName() + " overalyer task thread");
         }
      });
      overlayMakerExecutor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {
         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, acq_.getName() + " overaly maker thread");
         }
      });
   }

   public void setSurfaceDisplayParams(boolean convexHull, boolean stagePos, boolean surf) {
      showConvexHull_ = convexHull;
      showStagePositions_ = stagePos;
      showSurface_ = surf;
   }
   
   public void setStack(ZoomableVirtualStack stack) {
      zoomableStack_ = stack;
   }

   public void shutdown() {
      taskExecutor_.shutdownNow();
      overlayMakerExecutor_.shutdownNow();
   }

   //always try to cancel the previous task, assuming it is being replaced with a more current one
   public synchronized void redrawOverlay() {
      if (currentTask_ != null && !currentTask_.isDone()) {
         //cancel current surface calculation--this call does not block until complete
         currentTask_.cancel(true);
      }
      currentTask_ = taskExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            createAndRenderOverlay();
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
   private void createAndRenderOverlay() {
      //submit tasks for rendering the overlay at multiple levels of detail, drawing each as it becomes available
      Future<Overlay> baseOverlayCreation = overlayMakerExecutor_.submit(createBaseOverlay());
      try {
         //block until overlay creation finished
         final Overlay baseOverlay = baseOverlayCreation.get();
         //now that drawing finished, update canvas with most current overlay
         //Computing overlays is going to be the limiting step in this process,
         //because the setOverlay method just sets a reference and calls repaint    
         SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
               canvas_.setOverlay(baseOverlay);
            }
         });
         if (display_.getMode() == DisplayPlus.NEWSURFACE) {
            //now finished base overlay, move on to more detailed surface renderings 
            //first draw convex hull
            if (showConvexHull_) {
               final Overlay overlay = createBackgroundOverlay();
               addInterpPoints(display_.getCurrentSurface(), overlay);
               addConvexHull(overlay);
               SwingUtilities.invokeLater(new Runnable() {

                  @Override
                  public void run() {
                     canvas_.setOverlay(overlay);
                  }
               });
            }
            //then draw convex hull + stage positions
            if (showStagePositions_) {
               final Overlay overlay = createBackgroundOverlay();
               addInterpPoints(display_.getCurrentSurface(), overlay);
               if (showConvexHull_) {
                  addConvexHull(overlay);
               }
               addStagePositions(overlay);
               SwingUtilities.invokeLater(new Runnable() {

                  @Override
                  public void run() {
                     canvas_.setOverlay(overlay);
                  }
               });
            }

            //finally, draw surface at increasing high resolutions, blocking if interpolation hasn't progressed far enough to
            //supply the desired resolution yet
            if (showSurface_ && display_.getCurrentSurface() != null) {
               renderSurfaceOverlay();
            }
         }
      } catch (InterruptedException ex) {
         //interrupted because overlay is out of date
         return;
      } catch (ExecutionException ex) {
         //sholdn't ever happen because createBaseOverlay throw exceptions
         IJ.log("Exception when creating base overlay");
         ex.printStackTrace();
      }
   }

   /**
    * Draw an initial version of the overlay that can be calculated quickly
    * subsequent calls will draw more detailed surface overlay renderings
    * @return 
    */
   private Callable<Overlay> createBaseOverlay() {
      return new Callable<Overlay>() {
         @Override
         public Overlay call() throws InterruptedException {
            //determine appropriate overlay
            int mode = display_.getMode();
            if (mode == DisplayPlus.EXPLORE) {
               Overlay overlay = createBackgroundOverlay();
               if (display_.getExploreEndTile() != null) {
                  //draw explore tiles waiting to be confirmed with a click
                  highlightTilesOnOverlay(overlay, Math.min(display_.getExploreEndTile().y, display_.getExploreStartTile().y),
                          Math.max(display_.getExploreEndTile().y, display_.getExploreStartTile().y),
                          Math.min(display_.getExploreEndTile().x, display_.getExploreStartTile().x),
                          Math.max(display_.getExploreEndTile().x, display_.getExploreStartTile().x), TRANSPARENT_MAGENTA);
               } else if (display_.getMouseDragStartPointLeft() != null) {
                  //highlight multiple tiles when mouse dragging      
                  Point p2Tiles = zoomableStack_.getTileIndicesFromDisplayedPixel(display_.getCurrentMouseLocation().x, display_.getCurrentMouseLocation().y),
                          p1Tiles = zoomableStack_.getTileIndicesFromDisplayedPixel(display_.getMouseDragStartPointLeft().x, display_.getMouseDragStartPointLeft().y);
                  highlightTilesOnOverlay(overlay, Math.min(p1Tiles.y, p2Tiles.y), Math.max(p1Tiles.y, p2Tiles.y),
                          Math.min(p1Tiles.x, p2Tiles.x), Math.max(p1Tiles.x, p2Tiles.x), TRANSPARENT_BLUE);
               } else if (display_.getCurrentMouseLocation() != null) {
                  //draw single highlighted tile under mouse
                  Point coords = zoomableStack_.getTileIndicesFromDisplayedPixel(display_.getCurrentMouseLocation().x, display_.getCurrentMouseLocation().y);
                  highlightTilesOnOverlay(overlay, coords.y, coords.y, coords.x, coords.x, TRANSPARENT_BLUE); //highligth single tile
               }
               try {
               //always draw tiles waiting to be acquired
                  LinkedBlockingQueue<ExploreAcquisition.ExploreTileWaitingToAcquire> tiles = 
                          ((ExploreAcquisition) acq_).getTilesWaitingToAcquireAtSlice(display_.getVisibleSliceIndex() +
                                  ((ExploreAcquisition) acq_).getLowestExploredSliceIndex());
                  if (tiles != null) {
                     for (ExploreAcquisition.ExploreTileWaitingToAcquire t : tiles) {
                        highlightTilesOnOverlay(overlay, t.row, t.row, t.col, t.col, TRANSPARENT_GREEN);
                     }
                  }
               } catch (Exception e) {
                  e.printStackTrace();
               }
              
               return overlay;
            } else if (mode == DisplayPlus.NEWGRID) {
               return newGridOverlay();
            } else if (mode == DisplayPlus.NONE) {
               return createBackgroundOverlay();
            } else if (mode == DisplayPlus.NEWSURFACE) {
               //Do only fast version of surface overlay rendering, which don't require 
               //any progress in the interpolation
               Overlay overlay = createBackgroundOverlay();
               addInterpPoints(display_.getCurrentSurface(), overlay);
               return overlay;
            } else {
               ReportingUtils.showError("Unkonwn display mode");
               throw new RuntimeException();
            }
         }
      };
   }
   
   /**
    * creates background overlay that other modes should build upon
    * Includes scale bar and zoom indicator
    */
   private Overlay createBackgroundOverlay() {
      Overlay overlay = new Overlay();
      ContrastPanel cp = ((DisplayWindow) display_.getHyperImage().getWindow()).getContrastPanel();
      boolean showScaleBar = cp.showScaleBar();
      Color color = cp.getScaleBarColor();

      if (showScaleBar) {
         MMScaleBar sizeBar = new MMScaleBar(display_.getHyperImage(), color); 
         sizeBar.setPosition(cp.getScaleBarPosition());
         sizeBar.addToOverlay(overlay);
      }
      
      if (display_.getAcquisition() instanceof FixedAreaAcquisition) {
         drawZoomIndicator(overlay);
      }
      return overlay;
   }

   private void addInterpPoints(SurfaceInterpolator newSurface, Overlay overlay) {
      if (newSurface == null) {
         return;
      }
      for (Point3d point : newSurface.getPoints()) {
         LongPoint displayLocation = display_.imageCoordsFromStageCoords(point.x, point.y);
         int slice = zoomableStack_.getSliceIndexFromZCoordinate(point.z);
         if (slice != display_.getVisibleSliceIndex()) {
            continue;
         }

         Roi circle = new OvalRoi(displayLocation.x_ - INTERP_POINT_DIAMETER / 2, displayLocation.y_ - INTERP_POINT_DIAMETER / 2, INTERP_POINT_DIAMETER, INTERP_POINT_DIAMETER);
         circle.setStrokeColor(INTERP_POINT_COLOR);
         overlay.add(circle);
      }
   }

   private void addConvexHull(Overlay overlay) throws InterruptedException {
      //draw convex hull
      Vector2D[] hullPoints = display_.getCurrentSurface().getConvexHullPoints();

      LongPoint lastPoint = null, firstPoint = null;
      for (Vector2D v : hullPoints) {
         if (Thread.interrupted()) {
            throw new InterruptedException();
         }
         //convert to image coords
         LongPoint p = display_.imageCoordsFromStageCoords(v.getX(), v.getY());
         if (lastPoint != null) {
            Line l = new Line(p.x_, p.y_, lastPoint.x_, lastPoint.y_);
            l.setStrokeColor(CONVEX_HULL_COLOR);
            overlay.add(l);
         } else {
            firstPoint = p;
         }
         lastPoint = p;
      }
      //draw last connection         
      Line l = new Line(firstPoint.x_, firstPoint.y_, lastPoint.x_, lastPoint.y_);
      l.setStrokeColor(CONVEX_HULL_COLOR);
      overlay.add(l);
   }

   private void addStagePositions(Overlay overlay) throws InterruptedException {
      double zPosition = zoomableStack_.getZCoordinateOfDisplayedSlice(display_.getVisibleSliceIndex(), display_.getVisibleFrameIndex());
      //this will block until interpolation detailed enough to show stage positions
      ArrayList<XYStagePosition> positionsAtSlice = display_.getCurrentSurface().getXYPositonsAtSlice(zPosition);
      for (XYStagePosition pos : positionsAtSlice) {
         if (Thread.interrupted()) {
            throw new InterruptedException();
         }
         Point2D.Double[] corners = pos.getDisplayedTileCorners();
         LongPoint corner1 = display_.imageCoordsFromStageCoords(corners[0].x, corners[0].y);
         LongPoint corner2 = display_.imageCoordsFromStageCoords(corners[1].x, corners[1].y);
         LongPoint corner3 = display_.imageCoordsFromStageCoords(corners[2].x, corners[2].y);
         LongPoint corner4 = display_.imageCoordsFromStageCoords(corners[3].x, corners[3].y);
         //add lines connecting 4 corners
         Line l1 = new Line(corner1.x_, corner1.y_, corner2.x_, corner2.y_);
         Line l2 = new Line(corner2.x_, corner2.y_, corner3.x_, corner3.y_);
         Line l3 = new Line(corner3.x_, corner3.y_, corner4.x_, corner4.y_);
         Line l4 = new Line(corner4.x_, corner4.y_, corner1.x_, corner1.y_);
         l1.setStrokeColor(Color.red);
         l2.setStrokeColor(Color.red);
         l3.setStrokeColor(Color.red);
         l4.setStrokeColor(Color.red);
         overlay.add(l1);
         overlay.add(l2);
         overlay.add(l3);
         overlay.add(l4);
      }
   }

   private void renderSurfaceOverlay() throws InterruptedException {
      //start out with 10 interpolation points across the whole image 
      int pixPerInterpPoint = Math.max(display_.getImagePlus().getWidth(), display_.getImagePlus().getHeight()) / INITIAL_NUM_INTERPOLATION_DIVISIONS;
      //keep redrawing until surface full interpolated  
      final Overlay startingOverlay = createBackgroundOverlay();
      addInterpPoints(display_.getCurrentSurface(), startingOverlay);
      if (showConvexHull_) {
         addConvexHull(startingOverlay);
      }

      while (true) {
         final Overlay surfOverlay = new Overlay();
         //add all objects from starting overlay rather than recalculating them each time
         for (int i = 0; i < startingOverlay.size(); i++) {
            if (Thread.interrupted()) {
               throw new InterruptedException();
            }
            surfOverlay.add(startingOverlay.get(i));
         }
         SingleResolutionInterpolation interp = display_.getCurrentSurface().waitForCurentInterpolation();
         //wait until surface is interpolated at sufficent resolution to draw
         while (pixPerInterpPoint < interp.getPixelsPerInterpPoint()) {
            if (Thread.interrupted()) {
               throw new InterruptedException();
            }
            display_.getCurrentSurface().waitForHigherResolutionInterpolation();
            interp = display_.getCurrentSurface().waitForCurentInterpolation();
         }
         if (showStagePositions_) {
            //these could concieveably change as function of interpolation detail
            addStagePositions(surfOverlay);
         }
         //add surface interpolation
         addSurfaceInterpolation(surfOverlay, interp, pixPerInterpPoint);
         if (Thread.interrupted()) {
            throw new InterruptedException();
         }
         SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
               canvas_.setOverlay(surfOverlay);
            }
         });
      
         if (pixPerInterpPoint <= SurfaceInterpolator.MIN_PIXELS_PER_INTERP_POINT ) {
            //finished  
            return;
         }
         pixPerInterpPoint /= 2;
      }

   }

   //draw the surface itself by interpolating a grid over viewable area
   private void addSurfaceInterpolation(Overlay overlay, SingleResolutionInterpolation interp, int pixPerInterpPoint) throws InterruptedException {
      int width = display_.getImagePlus().getWidth();
      int height = display_.getImagePlus().getHeight();
      ZoomableVirtualStack zStack = (ZoomableVirtualStack) display_.virtualStack_;
      double sliceZ = zStack.getZCoordinateOfDisplayedSlice(display_.getVisibleSliceIndex(), display_.getVisibleFrameIndex() );
      double zStep = acq_.getZStep();

      //Make numTestPoints a factor of image size for clean display of surface
      int numTestPointsX = width / pixPerInterpPoint;
      int numTestPointsY = height / pixPerInterpPoint;
      double roiWidth = width / (double) numTestPointsX;
      double roiHeight = height / (double) numTestPointsY;

      for (int x = 0; x < numTestPointsX; x++) {
         for (int y = 0; y < numTestPointsY; y++) {
            if (Thread.interrupted()) {
               throw new InterruptedException();
            }
            Point2D.Double stageCoord = display_.stageCoordFromImageCoords((int) ((x + 0.5) * roiWidth), (int) ((y + 0.5) * roiHeight));
            Double interpZ = interp.getInterpolatedValue(stageCoord.x, stageCoord.y, false);
            if (interpZ == null) {
               continue;
            }
            if (Math.abs(sliceZ - interpZ) < zStep / 2) {
               double roiX = roiWidth * x;
               double roiY = roiHeight * y;
               //calculate distance from last ROI for uninterrupted coverage of image
               int displayWidth = (int) (roiWidth * (x + 1)) - (int) (roiX);
               int displayHeight = (int) (roiHeight * (y + 1)) - (int) (roiY);
               Roi rect = new Roi(roiX, roiY, displayWidth, displayHeight); //make ROI
               //ICE LUT
               double colorScale = ((sliceZ - interpZ) / (zStep / 2) + 1) / 2; //between 0 and 1
               rect.setFillColor(new Color(ICE_RED[(int) (colorScale * ICE_RED.length)],
                       ICE_GREEN[(int) (colorScale * ICE_RED.length)], ICE_BLUE[(int) (colorScale * ICE_RED.length)], 100));
               overlay.add(rect);
            }
         }
      }
   }

   private Overlay newGridOverlay() {
      Overlay overlay = createBackgroundOverlay();

      double dsTileWidth = tileWidth_ / (double) zoomableStack_.getDownsampleFactor();
      double dsTileHeight = tileHeight_ / (double) zoomableStack_.getDownsampleFactor();
      MultiPosRegion newGrid = display_.getCurrentRegion();
      if (newGrid == null) {
         return overlay;
      }
      int roiWidth = (int) ((newGrid.numCols() * dsTileWidth) );
      int roiHeight = (int) ((newGrid.numRows() * dsTileHeight) );
      LongPoint displayCenter = display_.imageCoordsFromStageCoords(newGrid.center().x, newGrid.center().y);
      Roi rectangle = new Roi(displayCenter.x_ - roiWidth / 2, displayCenter.y_ - roiHeight / 2, roiWidth, roiHeight);
      rectangle.setStrokeWidth(5f);
      rectangle.setStrokeColor(NEW_GRID_COLOR);

      Point displayTopLeft = new Point((int)(displayCenter.x_ - roiWidth / 2), (int)(displayCenter.y_ - roiHeight / 2));
      //draw boundries of tiles
      for (int row = 1; row < newGrid.numRows(); row++) {
         int yPos = (int) (displayTopLeft.y + row * dsTileHeight );
         Line l = new Line(displayTopLeft.x, yPos, displayTopLeft.x + roiWidth, yPos);
         l.setStrokeColor(NEW_GRID_COLOR);
         overlay.add(l);
      }
      for (int col = 1; col < newGrid.numCols(); col++) {
         int xPos = (int) (displayTopLeft.x + col * dsTileWidth );
         Line l = new Line(xPos, displayTopLeft.y, xPos, displayTopLeft.y + roiHeight);
         l.setStrokeColor(NEW_GRID_COLOR);
         overlay.add(l);
      }

      overlay.add(rectangle);
      return overlay;
   }

   private void highlightTilesOnOverlay(Overlay base, int row1, int row2, int col1, int col2, Color color) {
      LongPoint topLeft = zoomableStack_.getDisplayedPixel(row1, col1);
      int width = (int) Math.floor(tileWidth_ / (double) zoomableStack_.getDownsampleFactor() * (col2 - col1 + 1));
      int height = (int) Math.floor(tileHeight_ / (double) zoomableStack_.getDownsampleFactor() * (row2 - row1 + 1));
      Roi rect = new Roi(topLeft.x_, topLeft.y_, width, height);
      rect.setFillColor(color);
      base.add(rect);
   }

   private void drawZoomIndicator(Overlay overlay) {
      LongPoint zoomPos = zoomableStack_.getZoomLocation(); 
      int outerWidth = 100;
      int fullResHeight = display_.getAcquisition().getPositionManager().getNumRows() * display_.getAcquisition().getStorage().getTileHeight();
      int fullResWidth = display_.getAcquisition().getPositionManager().getNumCols() * display_.getAcquisition().getStorage().getTileWidth();
      int dsFactor = display_.getZoomableStack().getDownsampleFactor();
      Dimension displayImageSize = display_.getZoomableStack().getDisplayImageSize();
//      System.out.println(fullResHeight + "\t" + fullResWidth + "\t" + dsFactor + "\t" + displayImageSize.width + "\t" + displayImageSize.height);      
      int outerHeight = (int) ((double) fullResHeight / (double) fullResWidth * outerWidth);
      //draw outer rectangle representing full image
      Roi outerRect = new Roi(10, 10, outerWidth, outerHeight);
      outerRect.setStrokeColor(new Color(255, 0, 255)); //magenta
      int innerX = (int) Math.round(( (double) outerWidth / (double) fullResWidth ) * zoomPos.x_ *  dsFactor);
      int innerY = (int) Math.round(( (double) outerHeight / (double) fullResHeight ) * zoomPos.y_ * dsFactor);
      //outer width * percentage of width of full images that is shown
      int innerWidth = (int) (outerWidth * ((double) displayImageSize.width / (fullResWidth / dsFactor)));
      int innerHeight = (int) (outerHeight * ((double) displayImageSize.height / (fullResHeight / dsFactor)));
      Roi innerRect = new Roi(10+innerX,10+innerY,innerWidth,innerHeight );
      innerRect.setStrokeColor(new Color(255, 0, 255)); 
      if (outerWidth != innerWidth || outerHeight != innerHeight) { //dont draw if fully zoomed out
         overlay.add(outerRect);
         overlay.add(innerRect);
      }
      canvas_.setOverlay(overlay);
   }
}
