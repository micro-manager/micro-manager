package imagedisplay;

/*
 * Master stitched window to display real time stitched images, allow navigating of XY more easily
 */
import acq.Acquisition;
import acq.CustomAcqEngine;
import acq.ExploreAcquisition;
import acq.MultiResMultipageTiffStorage;
import coordinates.PositionManager;
import coordinates.StageCoordinates;
import surfacesandregions.SurfaceInterpolator;
import ij.CompositeImage;
import ij.IJ;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.TreeMap;
import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.vecmath.Point3d;
import mmcorej.TaggedImage;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.api.ImageCache;
import org.micromanager.imagedisplay.MMCompositeImage;
import org.micromanager.imagedisplay.NewImageEvent;


import org.micromanager.utils.*;
import surfacesandregions.MultiPosRegion;
import surfacesandregions.RegionManager;
import surfacesandregions.SurfaceManager;
import surfacesandregions.SurfaceOrRegionManager;

public class DisplayPlus extends VirtualAcquisitionDisplay  implements ListDataListener {

   private static final int EXPLORE_PIXEL_BUFFER = 96;
   
   private static final int[] ICE_RED =  {0,0,0,0,0,0,19,29,50,48,79,112,134,158,186,201,217,229,242,250,250,250,250,251,250,250,250,250,251,251,243,230};
   private static final int[] ICE_GREEN =  {156,165,176,184,190,196,193,184,171,162,146,125,107,93,81,87,92,97,95,93,93,90,85,69,64,54,47,35,19,0,4,0};
   private static final int[] ICE_BLUE =  {140,147,158,166,170,176,209,220,234,225,236,246,250,251,250,250,245,230,230,222,202,180,163,142,123,114,106,94,84,64,26,27};
   
   private static final int NONE = 0, EXPLORE = 1, GOTO = 2, NEWGRID = 3, NEWSURFACE = 4;
   
   private static final Color TRANSPARENT_BLUE = new Color(0, 0, 255, 100);   
   private static final Color TRANSPARENT_GREEN = new Color(0, 255, 0, 100);
   private static final Color TRANSPARENT_MAGENTA = new Color(255, 0, 255, 100);
   private ImageCanvas canvas_;
   private DisplayPlusControls controls_;
   private Acquisition acq_;
   private Point mouseDragStartPointLeft_, mouseDragStartPointRight_;
   private ArrayList<Point> selectedPositions_ = new ArrayList<Point>();
   private ZoomableVirtualStack zoomableStack_;
   private final boolean exploreAcq_;
   private PositionManager posManager_;
   private int tileWidth_, tileHeight_;
   private boolean cursorOverImage_;
   private int mode_ = NONE;
   private boolean mouseDragging_ = false;
   private RegionManager regionManager_;
   private SurfaceManager surfaceManager_;
   private boolean showSurface_ = true, showConvexHull_ = true, showStagePositions_ = true;
   
   

   public DisplayPlus(final ImageCache stitchedCache, Acquisition acq, JSONObject summaryMD, 
           MultiResMultipageTiffStorage multiResStorage, RegionManager rManager, SurfaceManager sManager) {
      super(stitchedCache, null, "test", true);
      tileWidth_ = multiResStorage.getTileWidth();
      tileHeight_ = multiResStorage.getTileHeight();
      posManager_ = multiResStorage.getPositionManager();
      exploreAcq_ = acq instanceof ExploreAcquisition;
      regionManager_ = rManager;
      surfaceManager_ = sManager;
      
      surfaceManager_.addListDataListener(this);
      
      //Set parameters for tile dimensions, num rows and columns, overlap, and image dimensions
      acq_ = acq;
      
      controls_ = new DisplayPlusControls(this, this.getEventBus(), acq, regionManager_, surfaceManager_);

      //Add in custom controls
      try {
         JavaUtils.setRestrictedFieldValue(this, VirtualAcquisitionDisplay.class, "controls_", controls_);
      } catch (NoSuchFieldException ex) {
         ReportingUtils.showError("Couldn't create display controls");
      }

      //add in customized zoomable acquisition virtual stack
      try {
         //looks like nSlicess only really matters during display startup
         int nSlices = MDUtils.getNumChannels(summaryMD) * MDUtils.getNumFrames(summaryMD) * MDUtils.getNumSlices(summaryMD);
         int width = MDUtils.getWidth(summaryMD);
         int height = MDUtils.getHeight(summaryMD);
         //25 pixel buffer for exploring
         if (exploreAcq_) {
            width += 2*EXPLORE_PIXEL_BUFFER;
            height += 2*EXPLORE_PIXEL_BUFFER;
            mode_ = EXPLORE;
         }
         zoomableStack_ = new ZoomableVirtualStack(MDUtils.getIJType(summaryMD), width, height, stitchedCache, nSlices, 
                 this, multiResStorage, exploreAcq_ ? EXPLORE_PIXEL_BUFFER : 0, acq_);
         this.show(zoomableStack_);
      } catch (Exception e) {
         ReportingUtils.showError("Problem with initialization due to missing summary metadata tags");
         return;
      }

      canvas_ = this.getImagePlus().getCanvas();
      
      //Zoom to 100%
      canvas_.unzoom();

      setupKeyListeners();
      setupMouseListeners();

      IJ.setTool(Toolbar.SPARE6);

      stitchedCache.addImageCacheListener(this);
      canvas_.requestFocus();
   }
   
   public void setSurfaceDisplaySettings(boolean convexHull, boolean stagePos, boolean surf) {
      showSurface_ = surf;
      showConvexHull_ = convexHull;
      showStagePositions_ = stagePos;
      drawOverlay(true);
   }

    
   private void drawZoomIndicatorOverlay() {
//      //draw zoom indicator
//      Overlay overlay = new Overlay();
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

   public void drawOverlay(boolean fullUpdate) {

      if (mode_ == EXPLORE) {
         //highlight tiles as appropriate
         if (mouseDragStartPointLeft_ != null) {
            //highlight multiple tiles       
            Point p2Tiles = zoomableStack_.getTileIndicesFromDisplayedPixel(canvas_.getCursorLoc().x, canvas_.getCursorLoc().y),
                    p1Tiles = zoomableStack_.getTileIndicesFromDisplayedPixel(mouseDragStartPointLeft_.x, mouseDragStartPointLeft_.y);
            highlightTiles(Math.min(p1Tiles.y, p2Tiles.y), Math.max(p1Tiles.y, p2Tiles.y),
                    Math.min(p1Tiles.x, p2Tiles.x), Math.max(p1Tiles.x, p2Tiles.x), TRANSPARENT_MAGENTA);
         } else if (cursorOverImage_) {
            Point coords = zoomableStack_.getTileIndicesFromDisplayedPixel(canvas_.getCursorLoc().x, canvas_.getCursorLoc().y);
            highlightTiles(coords.y, coords.y, coords.x, coords.x, TRANSPARENT_MAGENTA); //highligh single tile
         } else {
            canvas_.setOverlay(null);
         }
      } else if (mode_ == NEWGRID) {
         drawNewGridOverlay();
      } else if (mode_ == NONE ) { 
         //draw nothing (or maybe zoom indicator?) 
         canvas_.setOverlay(null);
      } else if (mode_ == GOTO) {
         //nothing
      } else if (mode_ == NEWSURFACE) {
         if (fullUpdate) {
            drawNewSurfaceOverlay();
         }
      }

   }
   
   private void mouseReleasedActions(MouseEvent e) {
      if (exploreAcq_ && mode_ == EXPLORE && SwingUtilities.isLeftMouseButton(e)) {
         Point p2 = e.getPoint();
         //find top left row and column and number of columns spanned by drage event
         Point tile1 = zoomableStack_.getTileIndicesFromDisplayedPixel(mouseDragStartPointLeft_.x, mouseDragStartPointLeft_.y);
         Point tile2 = zoomableStack_.getTileIndicesFromDisplayedPixel(p2.x, p2.y);
         //create events to acquire one or more tiles
         ((ExploreAcquisition) acq_).acquireTiles(tile1.y, tile1.x, tile2.y, tile2.x);
      } else if (mode_ == NEWSURFACE ) {
         if (SwingUtilities.isRightMouseButton(e) && !mouseDragging_) {
            //delete point if one is nearby
            Point fullResPixel = zoomableStack_.getAbsoluteFullResPixelCoordinate(e.getPoint().x, e.getPoint().y);
            Point2D.Double stagePos = posManager_.getStageCoordsFromPixelCoords(fullResPixel.x, fullResPixel.y);
            //calculate tolerance
            int pixelDistTolerance = 10;
            Point toleranceFRPixel = zoomableStack_.getAbsoluteFullResPixelCoordinate(
                    e.getPoint().x + pixelDistTolerance, e.getPoint().y + pixelDistTolerance);
            Point2D.Double toleranceStagePos = posManager_.getStageCoordsFromPixelCoords(toleranceFRPixel.x, toleranceFRPixel.y);
            double stageDistanceTolerance = Math.sqrt( (toleranceStagePos.x - stagePos.x)*(toleranceStagePos.x - stagePos.x) +
                    (toleranceStagePos.y - stagePos.y)*(toleranceStagePos.y - stagePos.y) );
            surfaceManager_.getCurrentSurface().deleteClosestPoint(stagePos.x, stagePos.y, stageDistanceTolerance);    
            surfaceManager_.updateListeners();
         } else if (SwingUtilities.isLeftMouseButton(e)) {
            //convert to real coordinates in 3D space
            //Click point --> full res pixel point --> stage coordinate
            Point fullResPixel = zoomableStack_.getAbsoluteFullResPixelCoordinate(e.getPoint().x, e.getPoint().y);
            Point2D.Double stagePos = posManager_.getStageCoordsFromPixelCoords(fullResPixel.x, fullResPixel.y);
            double z = zoomableStack_.getZCoordinateOfDisplayedSlice(this.getHyperImage().getSlice(), this.getHyperImage().getFrame());
            surfaceManager_.getCurrentSurface().addPoint(stagePos.x, stagePos.y, z);
            surfaceManager_.updateListeners();
         }
      }
      mouseDragging_ = false;
   }
   
   private void mouseDraggedActions(MouseEvent e) {
      Point currentPoint = e.getPoint();
      mouseDragging_ = true;
      if (SwingUtilities.isRightMouseButton(e)) {
         //pan
         zoomableStack_.translateView(mouseDragStartPointRight_.x - currentPoint.x, mouseDragStartPointRight_.y - currentPoint.y);
         redrawPixels();
         mouseDragStartPointRight_ = currentPoint;
         drawZoomIndicatorOverlay();
      } else if (SwingUtilities.isLeftMouseButton(e)) {
         //only move grid
         if (mode_ == NEWGRID) {  
            int dx = (currentPoint.x - mouseDragStartPointLeft_.x) * zoomableStack_.getDownsampleFactor();
            int dy = (currentPoint.y - mouseDragStartPointLeft_.y) * zoomableStack_.getDownsampleFactor();
            regionManager_.getCurrentRegion().translate(dx,dy);
            mouseDragStartPointLeft_ = currentPoint;
         }
      }
   }

   private void drawNewGridOverlay() {
      Overlay overlay = new Overlay();

      double dsTileWidth = tileWidth_ / (double) zoomableStack_.getDownsampleFactor();
      double dsTileHeight = tileHeight_ / (double) zoomableStack_.getDownsampleFactor();
      MultiPosRegion newGrid = regionManager_.getCurrentRegion();
      if (newGrid == null) {
         this.getImagePlus().setOverlay(null);
         return;
      }
      int roiWidth = (int) ((newGrid.numCols() * dsTileWidth) - ((newGrid.numCols() - 1) * newGrid.overlapX()) / zoomableStack_.getDownsampleFactor());
      int roiHeight = (int) ((newGrid.numRows() * dsTileHeight) - ((newGrid.numRows() - 1) * newGrid.overlapY()) / zoomableStack_.getDownsampleFactor());
      
      Point displayCenter = zoomableStack_.getDisplayImageCoordsFromFullImageCoords(new Point(newGrid.center()));
      
      Roi rectangle = new Roi(displayCenter.x - roiWidth / 2,displayCenter.y - roiHeight / 2, roiWidth, roiHeight);
      rectangle.setStrokeWidth(10f);
      overlay.add(rectangle);
      this.getImagePlus().setOverlay(overlay);
   }
   
   private Point2D.Double stageCoordFromImageCoord(int x, int y) {
      Point fullResPix = zoomableStack_.getAbsoluteFullResPixelCoordinate(x, y);
      return posManager_.getStageCoordsFromPixelCoords(fullResPix.x, fullResPix.y);
   }

   private void drawNewSurfaceOverlay() {
      Overlay overlay = new Overlay();
      SurfaceInterpolator newSurface = surfaceManager_.getCurrentSurface();
      if (newSurface == null) {
         this.getImagePlus().setOverlay(null);
         return;
      }

      //draw all points
      for (Point3d point : newSurface.getPoints()) {
         //convert back to pixel coordinates
         Point xy = posManager_.getPixelCoordsFromStageCoords(point.x, point.y);
         //convert ful res pixel coordinates to coordinates of the viewed image
         Point displayLocation = zoomableStack_.getDisplayImageCoordsFromFullImageCoords(xy);
         int slice = zoomableStack_.getDisplaySliceIndexFromZCoordinate(point.z, this.getHyperImage().getFrame());

         if (slice != this.getHyperImage().getSlice()) {
            continue;
         }

         int diameter = 4;
         Roi circle = new OvalRoi(displayLocation.x - diameter / 2, displayLocation.y - diameter / 2, diameter, diameter);
         overlay.add(circle);
      }

      if (showSurface_) {
         try {
            //draw the surface itself by interpolating a grid over viewable area
            int width = this.getImagePlus().getWidth();
            int height = this.getImagePlus().getHeight();
            //Make numTestPoints a factor of image size for clean display of surface
            int numTestPointsX = width / 10;
            int numTestPointsY = height / 10;
            double roiWidth = width / (double) numTestPointsX;
            double roiHeight = height / (double) numTestPointsY;

            double zStep = acq_.getZStep();
            double sliceZ = zoomableStack_.getZCoordinateOfDisplayedSlice(this.getHyperImage().getSlice(), this.getHyperImage().getFrame());
            for (int x = 0; x < numTestPointsX; x++) {
               for (int y = 0; y < numTestPointsY; y++) {
                  Point2D.Double stageCoord = stageCoordFromImageCoord((int) ((x+0.5) * roiWidth), (int) ((y+0.5) * roiHeight));
                  Float interpZ = surfaceManager_.getCurrentSurface().getInterpolatedValue(stageCoord.x, stageCoord.y);
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

         } catch (Exception e) {
            e.printStackTrace();
            ReportingUtils.logError("Couldn't interpolate surface");
         }
      }

      if (showConvexHull_) {
         //draw convex hull
         if (surfaceManager_.getCurrentSurface().getPoints().size() > 2) {
            Vector2D[] hullPoints = surfaceManager_.getCurrentSurface().getConvexHullPoints();
            Point lastPoint = null, firstPoint = null;
            for (Vector2D v : hullPoints) {
               //convert to image coords
               Point p = zoomableStack_.getDisplayImageCoordsFromFullImageCoords(posManager_.getPixelCoordsFromStageCoords(v.getX(), v.getY()));
               if (lastPoint != null) {
                  overlay.add(new Line(p.x, p.y, lastPoint.x, lastPoint.y));
               } else {
                  firstPoint = p;
               }
               lastPoint = p;
            }
            //draw last connection         
            overlay.add(new Line(firstPoint.x, firstPoint.y, lastPoint.x, lastPoint.y));
         }
      }

      
      if (showStagePositions_) {
         //draw grid of positions
         if (surfaceManager_.getCurrentSurface().getPoints().size() > 2) {
            double zPosition = zoomableStack_.getZCoordinateOfDisplayedSlice(this.getHyperImage().getSlice(), this.getHyperImage().getFrame());

            ArrayList<StageCoordinates> positionsAtSlice = surfaceManager_.getCurrentSurface().getXYPositonsAtSlice(zPosition);
            for (StageCoordinates pos : positionsAtSlice) {
               Point corner1 = zoomableStack_.getDisplayImageCoordsFromFullImageCoords(posManager_.getPixelCoordsFromStageCoords(pos.corners[0].x, pos.corners[0].y));
               Point corner2 = zoomableStack_.getDisplayImageCoordsFromFullImageCoords(posManager_.getPixelCoordsFromStageCoords(pos.corners[1].x, pos.corners[1].y));
               Point corner3 = zoomableStack_.getDisplayImageCoordsFromFullImageCoords(posManager_.getPixelCoordsFromStageCoords(pos.corners[2].x, pos.corners[2].y));
               Point corner4 = zoomableStack_.getDisplayImageCoordsFromFullImageCoords(posManager_.getPixelCoordsFromStageCoords(pos.corners[3].x, pos.corners[3].y));
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
            }
         }
      }

      this.getImagePlus().setOverlay(overlay);
   }

   private void highlightTiles(int row1, int row2, int col1, int col2, Color color) {
      Point topLeft = zoomableStack_.getDisplayedPixel(row1,col1);
      int width = (int) Math.round(tileWidth_ / (double) zoomableStack_.getDownsampleFactor() * (col2 - col1 + 1));
      int height = (int) Math.round(tileHeight_ / (double) zoomableStack_.getDownsampleFactor() * (row2 - row1 + 1));
      Roi rect = new Roi(topLeft.x, topLeft.y, width, height);
      rect.setFillColor(color);
      Overlay overlay = new Overlay();
      overlay.add(rect);
      canvas_.setOverlay(overlay);
   }

   public void zoom(boolean in) {
      zoomableStack_.zoom(cursorOverImage_ ? canvas_.getCursorLoc() : null, in ? -1 :1);
      redrawPixels();
      drawOverlay(true);
   }
   
   public void activateExploreMode(boolean activate) {
      mode_ = activate && exploreAcq_ ? EXPLORE : NONE;
   }
   
   public void activateNewGridMode(boolean activate) {
      mode_ = activate ? NEWGRID : NONE;
   }
   
   public void activateNewSurfaceMode(boolean activate) {
      mode_ = activate ? NEWSURFACE : NONE;
      drawOverlay(true);
   }

   @Override
   public void imageReceived(TaggedImage taggedImage) {
      try {
         //TODO: can scrollbars be maniiulated to avoid this?
         //duplicate so image storage doesnt see incorrect tags
         JSONObject newTags = new JSONObject(taggedImage.tags.toString());
         MDUtils.setPositionIndex(newTags, 0);
         taggedImage = new TaggedImage(taggedImage.pix, newTags);
      } catch (JSONException ex) {
         ReportingUtils.showError("Couldn't manipulate image tags for display");
      }
      super.imageReceived(taggedImage);

   }

   private void redrawPixels() {
      if (!this.getHyperImage().isComposite()) {
         int index = this.getHyperImage().getCurrentSlice();
         Object pixels = zoomableStack_.getPixels(index);
         this.getHyperImage().getProcessor().setPixels(pixels);
      } else {
         CompositeImage ci = (CompositeImage) this.getHyperImage();
         if (ci.getMode() == CompositeImage.COMPOSITE) {
            for (int i = 0; i < ((MMCompositeImage) ci).getNChannelsUnverified(); i++) {
               //Dont need to set pixels if processor is null because it will get them from stack automatically  
               Object pixels = zoomableStack_.getPixels(ci.getCurrentSlice() - ci.getChannel() + i + 1);
               if (ci.getProcessor(i + 1) != null && pixels != null) {
                  ci.getProcessor(i + 1).setPixels(pixels);
               }
            }
         }
         Object pixels = zoomableStack_.getPixels(this.getHyperImage().getCurrentSlice());
         if (pixels != null) {
            ci.getProcessor().setPixels(pixels);
         }
      }
      if (CanvasPaintPending.isMyPaintPending(canvas_, this)) {
         return;
      }
      CanvasPaintPending.setPaintPending(canvas_, this);
      this.updateAndDraw(true);
   }

   private void setupMouseListeners() {
      //remove channel switching scroll wheel listener
      this.getImagePlus().getWindow().removeMouseWheelListener(
              this.getImagePlus().getWindow().getMouseWheelListeners()[0]);
      //remove canvas mouse listener and virtualacquisitiondisplay as mouse listener
      canvas_.removeMouseListener(canvas_.getMouseListeners()[0]);
      canvas_.removeMouseListener(canvas_.getMouseListeners()[0]);

      canvas_.addMouseMotionListener(new MouseMotionListener() {

         @Override
         public void mouseDragged(MouseEvent e) {
            mouseDraggedActions(e);
            drawOverlay(true);
         }

         @Override
         public void mouseMoved(MouseEvent e) {
            drawOverlay(false);
         }
      });

      canvas_.addMouseListener(new MouseListener() {

         @Override
         public void mouseClicked(MouseEvent e) {
         }

         @Override
         public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
               mouseDragStartPointRight_ = e.getPoint();
            } else if (SwingUtilities.isLeftMouseButton(e)) {
               mouseDragStartPointLeft_ = e.getPoint();
            }         
            drawOverlay(false);
         }

         @Override
         public void mouseReleased(MouseEvent e) {
            mouseReleasedActions(e);
            mouseDragStartPointLeft_ = null;
            mouseDragStartPointRight_ = null;
            drawOverlay(true);
         }

         @Override
         public void mouseEntered(MouseEvent e) {
            cursorOverImage_ = true;
            drawOverlay(false);
         }

         @Override
         public void mouseExited(MouseEvent e) {
            cursorOverImage_ = false;
            drawOverlay(false);
         }
      });
   }

   private void setupKeyListeners() {
      //remove ImageJ key listeners
      ImageWindow window = this.getImagePlus().getWindow();
      window.removeKeyListener(this.getImagePlus().getWindow().getKeyListeners()[0]);
      canvas_.removeKeyListener(canvas_.getKeyListeners()[0]);

      KeyListener kl = new KeyListener() {

         @Override
         public void keyTyped(KeyEvent ke) {
            if (ke.getKeyChar() == '=') {
               zoom(true);
            } else if (ke.getKeyChar() == '-') {
               zoom(false);
            } else if (ke.getKeyChar() == ' ') {
               controls_.toggleExploreMode();
               drawOverlay(false);
            }
         }

         @Override
         public void keyPressed(KeyEvent ke) {
         }

         @Override
         public void keyReleased(KeyEvent ke) {         
         }
      };

      //add keylistener to window and all subscomponenets so it will fire whenever
      //focus in anywhere in the window
      addRecursively(window, kl);
   }

   private void addRecursively(Component c, KeyListener kl) {
      c.addKeyListener(kl);
      if (c instanceof Container) {
         for (Component subC : ((Container) c).getComponents()) {
            addRecursively(subC, kl);
         }
      }
   }

   @Override
   public void intervalAdded(ListDataEvent e) {
   }

   @Override
   public void intervalRemoved(ListDataEvent e) {
   }

   @Override
   public void contentsChanged(ListDataEvent e) {
      this.drawOverlay(true);
   }
   
}
