package imagedisplay;

import acq.Acquisition;
import coordinates.XYStagePosition;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import mmcloneclasses.graph.ContrastPanel;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.micromanager.utils.ReportingUtils;
import surfacesandregions.SingleResolutionInterpolation;
import surfacesandregions.MultiPosGrid;
import surfacesandregions.Point3d;
import surfacesandregions.SurfaceInterpolator;

/**
 * Class that encapsulates calculation of overlays for DisplayPlus
 */
public class DisplayOverlayer {

   private final static int INTERP_POINT_DIAMETER = 4;
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
   private boolean showSurface_ = true, showConvexHull_ = true, showStagePositions_ = true;
   private ZoomableVirtualStack zoomableStack_;
   private ImageCanvas canvas_;
   private int tileWidth_, tileHeight_;
   private ExecutorService executor_;
   private AtomicBoolean surfaceNeedsUpdate_ = new AtomicBoolean(false);
   private volatile Thread updatingThread_;

   public DisplayOverlayer(DisplayPlus display, Acquisition acq, int tileWidth, int tileHeight, ZoomableVirtualStack stack) {
      display_ = display;
      tileWidth_ = tileWidth;
      tileHeight_ = tileHeight;
      acq_ = acq;
      canvas_ = display.getImagePlus().getCanvas();
      zoomableStack_ = stack;
      createSingleThreadExecutor();
   }
   
   public void setStack(ZoomableVirtualStack stack) {
      zoomableStack_ = stack;
   }

   public void shutdown() {
      executor_.shutdownNow();
      executor_ = null;
   }

   private void createSingleThreadExecutor() {
      executor_ = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {

         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Overlay update thread ");
         }
      }) {

         @Override
         protected void beforeExecute(Thread t, Runnable r) {
            super.beforeExecute(t, r);
            updatingThread_ = t;
         }

         @Override
         protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);
            updatingThread_ = null;
         }
      };
   }

   /**
    * creates base overlay that other modes should build upon
    * Includes scale bar and zoom indicator
    */
   private Overlay createBaseOverlay() {
      Overlay overlay = new Overlay();
      ContrastPanel cp = ((DisplayWindow) display_.getHyperImage().getWindow()).getContrastPanel();
      boolean showScaleBar = cp.showScaleBar();
      Color color = cp.getScaleBarColor();

      if (showScaleBar) {
         MMScaleBar sizeBar = new MMScaleBar(display_.getHyperImage(), color); 
         sizeBar.setPosition(cp.getScaleBarPosition());
         sizeBar.addToOverlay(overlay);
      }
      return overlay;
   }
   
   private void setOverlay(final Overlay overlay) throws InterruptedException {
      try {
         if (Thread.interrupted()) {
            throw new InterruptedException();
         }
         SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
               canvas_.setOverlay(overlay);
            }
         });
      } catch (InterruptedException ex) {
         throw new InterruptedException();
      } catch (InvocationTargetException ex) {
         ReportingUtils.logError(ex.toString());
      }
   }

   /**
    *
    * @param interrupt true when something about the image has changed, so
    * existing overlaying should be canceled
    */
   public synchronized void renderOverlay(final boolean surfaceUpdate) {
      if (surfaceUpdate) {
         //cancel surface update in progress
         if (updatingThread_ != null) {
            updatingThread_.interrupt();
         }
         //set flag so that next runnable will know to update
         surfaceNeedsUpdate_.set(true);
      }

      executor_.execute(new Runnable() {

         @Override
         public void run() {
            try {
               calcAndDrawOverlay();
            } catch (InterruptedException ex) {
               return; //Overlay interrupted because of change in interpolator or display
            }
         }
      });
   }

   private void calcAndDrawOverlay() throws InterruptedException {
      //determine appropriate overlay
      int mode = display_.getMode();
      if (mode == DisplayPlus.EXPLORE) {
         //highlight tiles as appropriate
         if (display_.getCurrentMouseLocation() == null) {
            setOverlay(createBaseOverlay());
         } else if (display_.getMouseDragStartPointLeft() != null) {
            //highlight multiple tiles       
            Point p2Tiles = zoomableStack_.getTileIndicesFromDisplayedPixel(display_.getCurrentMouseLocation().x, display_.getCurrentMouseLocation().y),
                    p1Tiles = zoomableStack_.getTileIndicesFromDisplayedPixel(display_.getMouseDragStartPointLeft().x, display_.getMouseDragStartPointLeft().y);

            setOverlay(highlightTileOverlay(Math.min(p1Tiles.y, p2Tiles.y), Math.max(p1Tiles.y, p2Tiles.y),
                    Math.min(p1Tiles.x, p2Tiles.x), Math.max(p1Tiles.x, p2Tiles.x), TRANSPARENT_GREEN));
         } else if (display_.cursorOverImage()) {
            Point coords = zoomableStack_.getTileIndicesFromDisplayedPixel(display_.getCurrentMouseLocation().x, display_.getCurrentMouseLocation().y);
            setOverlay(highlightTileOverlay(coords.y, coords.y, coords.x, coords.x, TRANSPARENT_GREEN)); //highligh single tile
         } else {
            setOverlay(createBaseOverlay());
         }
      } else if (mode == DisplayPlus.NEWGRID) {
         setOverlay(newGridOverlay());
      } else if (mode == DisplayPlus.NONE) {
         //draw nothing (or maybe zoom indicator?) 
         setOverlay(createBaseOverlay());
      } else if (mode == DisplayPlus.NEWSURFACE && surfaceNeedsUpdate_.get()) {
         //if a preceeding call has updated the surface, don't do it again
         createSurfaceOverlay();
      }
   }

   public void setSurfaceDisplayParams(boolean convexHull, boolean stagePos, boolean surf) {
      showConvexHull_ = convexHull;
      showStagePositions_ = stagePos;
      showSurface_ = surf;
      //TODO redraw?
   }

   private void drawInterpPoints(SurfaceInterpolator newSurface, Overlay overlay) {
      //draw all points on EDT, since there is little calculation involved in this
      for (Point3d point : newSurface.getPoints()) {
         Point displayLocation = display_.imageCoordsFromStageCoords(point.x, point.y);
         int slice = zoomableStack_.getDisplaySliceIndexFromZCoordinate(point.z, display_.getHyperImage().getFrame());
         if (slice != display_.getHyperImage().getSlice()) {
            continue;
         }

         Roi circle = new OvalRoi(displayLocation.x - INTERP_POINT_DIAMETER / 2, displayLocation.y - INTERP_POINT_DIAMETER / 2, INTERP_POINT_DIAMETER, INTERP_POINT_DIAMETER);
         circle.setStrokeColor(INTERP_POINT_COLOR);
         overlay.add(circle);
      }
   }

   private void drawConvexHull(Overlay overlay) throws InterruptedException {
      if (showConvexHull_) {
         //draw convex hull
         Vector2D[] hullPoints = display_.getCurrentSurface().getConvexHullPoints();
         while (hullPoints == null) {
            Thread.sleep(5);
            hullPoints = display_.getCurrentSurface().getConvexHullPoints();
         }
         Point lastPoint = null, firstPoint = null;
         for (Vector2D v : hullPoints) {
            //convert to image coords
            Point p = display_.imageCoordsFromStageCoords(v.getX(), v.getY());
            if (lastPoint != null) {
               Line l = new Line(p.x, p.y, lastPoint.x, lastPoint.y);
               l.setStrokeColor(CONVEX_HULL_COLOR);
               overlay.add(l);
            } else {
               firstPoint = p;
            }
            lastPoint = p;
         }
         //draw last connection         
         Line l = new Line(firstPoint.x, firstPoint.y, lastPoint.x, lastPoint.y);
         l.setStrokeColor(CONVEX_HULL_COLOR);
         overlay.add(l);
      }
   }

   private Overlay createPointsConvexHullOverlay() throws InterruptedException {
      Overlay overlay = createBaseOverlay();
      SurfaceInterpolator newSurface = display_.getCurrentSurface();
      if (newSurface != null) {
         drawInterpPoints(newSurface, overlay);
         if (display_.getCurrentSurface().getPoints().length > 2) {
            drawConvexHull(overlay);
         }
      }
      return overlay;
   }

   private void createSurfaceOverlay() throws InterruptedException {
      SurfaceInterpolator surf = display_.getCurrentSurface();
      if (surf == null) {
         setOverlay(null);
         return;
      }
      if (surf.getPoints().length < 3) {
         setOverlay(createPointsConvexHullOverlay()); //no surfcae to draw yet
      } else {

         final Overlay baseOverlay = createPointsConvexHullOverlay();
         //initial drawing of points and maybe convex hull for GUI responsiveness
         SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
               canvas_.setOverlay(baseOverlay);
            }
         });

         SingleResolutionInterpolation interp = surf.getCurrentInterpolation();
         while (interp == null) {
            Thread.sleep(10);
            interp = surf.getCurrentInterpolation();
         }
         //start out with 10 interpolation points across the whole image 
         int pixPerInterpPoint = Math.max(display_.getImagePlus().getWidth(), display_.getImagePlus().getHeight()) / 10;
         //keep redrawing until surface full interpolated  
         while (true) {
            Overlay overlay = createBaseOverlay();
            //add all object from base overlay so don't need to redraw them at each successive resolution
            for (int i = 0; i < baseOverlay.size(); i++) {
               overlay.add(baseOverlay.get(i));
            }
            //wait until sufficient resolution is available for drawing
            while (pixPerInterpPoint < interp.getPixelsPerInterpPoint()) {
               Thread.sleep(10);
               interp = surf.getCurrentInterpolation();
            }
            drawStagePositions(overlay, interp);  //draw outline of stage positions specific to slice     
            calculateAndAddSurfaceToOverlay(overlay, interp, pixPerInterpPoint); //draw surface overlay
            setOverlay(overlay);

            if (pixPerInterpPoint == SurfaceInterpolator.MIN_PIXELS_PER_INTERP_POINT && !Thread.interrupted()) {
               //finished  
               surfaceNeedsUpdate_.set(false);
               return;
            }
            pixPerInterpPoint /= 2;
         }
      }
   }

   //draw the surface itself by interpolating a grid over viewable area
   private void calculateAndAddSurfaceToOverlay(Overlay overlay, SingleResolutionInterpolation interp, int pixPerInterpPoint) throws InterruptedException {
      if (!showSurface_ || display_.getCurrentSurface().getPoints().length <= 2) {
         return;
      }

      int width = display_.getImagePlus().getWidth();
      int height = display_.getImagePlus().getHeight();
      ZoomableVirtualStack zStack = (ZoomableVirtualStack) display_.virtualStack_;
      double sliceZ = zStack.getZCoordinateOfDisplayedSlice(display_.getHyperImage().getSlice(), display_.getHyperImage().getFrame());
      double zStep = acq_.getZStep();

      //Make numTestPoints a factor of image size for clean display of surface
      int numTestPointsX = width / pixPerInterpPoint;
      int numTestPointsY = height / pixPerInterpPoint;
      double roiWidth = width / (double) numTestPointsX;
      double roiHeight = height / (double) numTestPointsY;

      for (int x = 0; x < numTestPointsX; x++) {
         for (int y = 0; y < numTestPointsY; y++) {
            Point2D.Double stageCoord = display_.stageCoordFromImageCoords((int) ((x + 0.5) * roiWidth), (int) ((y + 0.5) * roiHeight));
            Float interpZ = interp.getInterpolatedValue(stageCoord.x, stageCoord.y);
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
            if (Thread.interrupted()) {
               throw new InterruptedException();
            }
         }
      }
   }

   private void drawStagePositions(Overlay overlay, SingleResolutionInterpolation interp) throws InterruptedException {
      if (showStagePositions_) {
         //wait until XY positions areound footprint are calculated
         while (display_.getCurrentSurface().getXYPositions() == null) {
            Thread.sleep(5);
         }
         double zPosition = zoomableStack_.getZCoordinateOfDisplayedSlice(display_.getHyperImage().getSlice(), display_.getHyperImage().getFrame());
         ArrayList<XYStagePosition> positionsAtSlice = display_.getCurrentSurface().getXYPositonsAtSlice(zPosition, interp);
         if (positionsAtSlice == null) {
            return; //interpolation isn't detailed enough yet
         }
         for (XYStagePosition pos : positionsAtSlice) {
            Point2D.Double[] corners = pos.getCorners();
            Point corner1 = display_.imageCoordsFromStageCoords(corners[0].x, corners[0].y);
            Point corner2 = display_.imageCoordsFromStageCoords(corners[1].x, corners[1].y);
            Point corner3 = display_.imageCoordsFromStageCoords(corners[2].x, corners[2].y);
            Point corner4 = display_.imageCoordsFromStageCoords(corners[3].x, corners[3].y);
            //debugging:

//            System.out.println("Position corners (pixel): " + pos.getName());
//            System.out.println(corner1.x + ", " + corner1.y);
//            System.out.println(corner2.x + ", " + corner2.y);
//            System.out.println(corner3.x + ", " + corner3.y);
//            System.out.println(corner4.x + ", " + corner4.y);
//            
//            System.out.println("Position corners (stage): " + pos.getName());
//            System.out.println(corners[0].x + ", " + corners[0].y);
//            System.out.println(corners[1].x + ", " + corners[1].y);
//            System.out.println(corners[2].x + ", " + corners[2].y);
//            System.out.println(corners[3].x + ", " + corners[3].y);


            //add lines connecting 4 corners
            Line l1 = new Line(corner1.x, corner1.y, corner2.x, corner2.y);
            Line l2 = new Line(corner2.x, corner2.y, corner3.x, corner3.y);
            Line l3 = new Line(corner3.x, corner3.y, corner4.x, corner4.y);
            Line l4 = new Line(corner4.x, corner4.y, corner1.x, corner1.y);
            l1.setStrokeColor(Color.red);
            l2.setStrokeColor(Color.red);
            l3.setStrokeColor(Color.red);
            l4.setStrokeColor(Color.red);
            overlay.add(l1);
            overlay.add(l2);
            overlay.add(l3);
            overlay.add(l4);
            if (Thread.interrupted()) {
               throw new InterruptedException();
            }
         }
         System.out.println("\n\n");

      }
   }

   private Overlay newGridOverlay() {
      Overlay overlay = createBaseOverlay();

      double dsTileWidth = tileWidth_ / (double) zoomableStack_.getDownsampleFactor();
      double dsTileHeight = tileHeight_ / (double) zoomableStack_.getDownsampleFactor();
      MultiPosGrid newGrid = display_.getCurrentRegion();
      if (newGrid == null) {
         display_.getImagePlus().setOverlay(null);
         return null;
      }
      int roiWidth = (int) ((newGrid.numCols() * dsTileWidth) - ((newGrid.numCols() - 1) * newGrid.overlapX()) / zoomableStack_.getDownsampleFactor());
      int roiHeight = (int) ((newGrid.numRows() * dsTileHeight) - ((newGrid.numRows() - 1) * newGrid.overlapY()) / zoomableStack_.getDownsampleFactor());

      Point displayCenter = display_.imageCoordsFromStageCoords(newGrid.center().x, newGrid.center().y);

      Roi rectangle = new Roi(displayCenter.x - roiWidth / 2, displayCenter.y - roiHeight / 2, roiWidth, roiHeight);
      rectangle.setStrokeWidth(5f);
      rectangle.setStrokeColor(NEW_GRID_COLOR);

      Point displayTopLeft = new Point(displayCenter.x - roiWidth / 2, displayCenter.y - roiHeight / 2);
      //draw boundries of tiles
      for (int row = 1; row < newGrid.numRows(); row++) {
         int yPos = (int) (displayTopLeft.y + row * dsTileWidth + (newGrid.overlapY() / 2) / zoomableStack_.getDownsampleFactor());
         Line l = new Line(displayTopLeft.x, yPos, displayTopLeft.x + roiWidth, yPos);
         l.setStrokeColor(NEW_GRID_COLOR);
         overlay.add(l);
      }
      for (int col = 1; col < newGrid.numCols(); col++) {
         int xPos = (int) (displayTopLeft.x + col * dsTileHeight + (newGrid.overlapX() / 2) / zoomableStack_.getDownsampleFactor());
         Line l = new Line(xPos, displayTopLeft.y, xPos, displayTopLeft.y + roiHeight);
         l.setStrokeColor(NEW_GRID_COLOR);
         overlay.add(l);
      }

      overlay.add(rectangle);
      return overlay;
   }

   private Overlay highlightTileOverlay(int row1, int row2, int col1, int col2, Color color) {
      Point topLeft = zoomableStack_.getDisplayedPixel(row1, col1);
      int width = (int) Math.round(tileWidth_ / (double) zoomableStack_.getDownsampleFactor() * (col2 - col1 + 1));
      int height = (int) Math.round(tileHeight_ / (double) zoomableStack_.getDownsampleFactor() * (row2 - row1 + 1));
      Roi rect = new Roi(topLeft.x, topLeft.y, width, height);
      rect.setFillColor(color);
      Overlay overlay = createBaseOverlay();
      overlay.add(rect);
      return overlay;
   }

   private void drawZoomIndicatorOverlay() {
//      //draw zoom indicator
//      Overlay overlay = createBaseOverlay();
//      Point zoomPos = zoomableStack_.getZoomPosition();      
//      int outerWidth = 100;
//      int outerHeight = (int) ((double) storage_.getFullResHeight() / (double) storage_.getFullResWidth() * outerWidth);
//      //draw outer rectangle representing full image
//      Roi outerRect = new Roi(10, 10, outerWidth, outerHeight);
//      outerRect.setStrokeColor(new Color(255, 0, 255)); //magenta
//      overlay.add(outerRect);
//      int innerX = (int) Math.round(( (double) outerWidth / (double) storage_.getFullResWidth() ) * zoomPos.x);
//      int innerY = (int) Math.round(( (double) outerHeight / (double) storage_.getFullResHeight() ) * zoomPos.y);
//      int innerWidth = (int) Math.round(((double) outerWidth / (double) storage_.getFullResWidth() ) * 
//              (storage_.getFullResWidth() / storage_.getDSFactor()));
//      int innerHeight = (int) Math.round(((double) outerHeight / (double) storage_.getFullResHeight() ) * 
//              (storage_.getFullResHeight() / storage_.getDSFactor()));
//      Roi innerRect = new Roi(10+innerX,10+innerY,innerWidth,innerHeight );
//      innerRect.setStrokeColor(new Color(255, 0, 255)); 
//      overlay.add(innerRect);
//      canvas_.setOverlay(overlay);
   }
}
