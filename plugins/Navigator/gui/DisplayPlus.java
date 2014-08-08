package gui;

/*
 * Master stitched window to display real time stitched images, allow navigating of XY more easily
 */
import acq.Acquisition;
import acq.CustomAcqEngine;
import acq.ExploreAcquisition;
import acq.MultiResMultipageTiffStorage;
import acq.PositionManager;
import acq.SurfaceInterpolater;
import ij.CompositeImage;
import ij.IJ;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.*;
import javax.vecmath.Point3d;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.api.ImageCache;
import org.micromanager.imagedisplay.MMCompositeImage;
import org.micromanager.imagedisplay.NewImageEvent;


import org.micromanager.utils.*;

public class DisplayPlus extends VirtualAcquisitionDisplay  {

   private static final int EXPLORE_PIXEL_BUFFER = 96;
   
   private static final int NONE = 0, PAN = 1, GOTO = 2, NEWGRID = 3, NEWSURFACE = 4;
   
   private static final Color TRANSPARENT_BLUE = new Color(0, 0, 255, 100);
   private static final Color TRANSPARENT_MAGENTA = new Color(255, 0, 255, 100);
   private ImageCanvas canvas_;
   private DisplayPlusControls controls_;
   private Acquisition acq_;
   private Point mouseDragStartPoint_;
   private ArrayList<Point> selectedPositions_ = new ArrayList<Point>();
   private ZoomableVirtualStack zoomableStack_;
   private final boolean exploreMode_;
   private PositionManager posManager_;
   private int tileWidth_, tileHeight_;
   private boolean cursorOverImage_;
   private int mode_ = NONE;
   private NewGridParams newGrid_ = null;
   private SurfaceInterpolater newSurface_ = null;
   
   

   public DisplayPlus(final ImageCache stitchedCache, Acquisition acq, JSONObject summaryMD, 
           MultiResMultipageTiffStorage multiResStorage) {
      super(stitchedCache, null, "test", true);
      tileWidth_ = multiResStorage.getTileWidth();
      tileHeight_ = multiResStorage.getTileHeight();
      posManager_ = multiResStorage.getPositionManager();
      exploreMode_ = acq instanceof ExploreAcquisition;
      
      //Set parameters for tile dimensions, num rows and columns, overlap, and image dimensions
      acq_ = acq;
      
      controls_ = new DisplayPlusControls(this, this.getEventBus(), acq);

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
         if (exploreMode_) {
            width += 2*EXPLORE_PIXEL_BUFFER;
            height += 2*EXPLORE_PIXEL_BUFFER;
         }
         zoomableStack_ = new ZoomableVirtualStack(MDUtils.getIJType(summaryMD), width, height, stitchedCache, nSlices, 
                 this, multiResStorage, exploreMode_ ? EXPLORE_PIXEL_BUFFER : 0, acq_);
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

   public void drawOverlay() {

      if (mode_ == NONE && exploreMode_) {
         //highlight tiles as appropriate
         if (mouseDragStartPoint_ != null) {
            //highlight multiple tiles       
            Point p2Tiles = zoomableStack_.getTileIndicesFromDisplayedPixel(canvas_.getCursorLoc().x, canvas_.getCursorLoc().y),
                    p1Tiles = zoomableStack_.getTileIndicesFromDisplayedPixel(mouseDragStartPoint_.x, mouseDragStartPoint_.y);
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
      } else if (mode_ == PAN) { 
         //draw nothing (or maybe zoom indicator?) 
         canvas_.setOverlay(null);
      } else if (mode_ == GOTO) {
         //nothing
      } else if (mode_ == NEWSURFACE) {
         drawNewSurfaceOverlay();
      }

   }
   
   private void mouseReleasedActions(MouseEvent e) {
      if (exploreMode_ && mode_ == NONE) {

         Point p2 = e.getPoint();
         //find top left row and column and number of columns spanned by drage event
         Point tile1 = zoomableStack_.getTileIndicesFromDisplayedPixel(mouseDragStartPoint_.x, mouseDragStartPoint_.y);
         Point tile2 = zoomableStack_.getTileIndicesFromDisplayedPixel(p2.x, p2.y);
         //create events to acquire one or more tiles
         ((ExploreAcquisition)acq_).acquireTiles(tile1.y, tile1.x, tile2.y, tile2.x);
      } else if (mode_ == NEWSURFACE) {
         //convert to real coordinates in 3D space
         //Click point --> full res pixel point --> stage coordinate
         Point fullResPixel = zoomableStack_.getAbsoluteFullResPixelCoordinate(e.getPoint().x, e.getPoint().y);
         Point2D.Double stagePos = posManager_.getStageCoordsFromPixelCoords(fullResPixel.x, fullResPixel.y);                        
         double z = zoomableStack_.getZCoordinateOfDisplayedSlice(this.getHyperImage().getSlice(), this.getHyperImage().getFrame());
         newSurface_.addPoint(stagePos.x, stagePos.y, z);
      }
   }

   private void mouseDraggedActions(MouseEvent e) {
      Point currentPoint = e.getPoint();
      if (mode_ == NEWGRID) {
         int dx = (int) ((currentPoint.x - mouseDragStartPoint_.x) / canvas_.getMagnification());
         int dy = (int) ((currentPoint.y - mouseDragStartPoint_.y) / canvas_.getMagnification());
         newGrid_.centerX += dx;
         newGrid_.centerY += dy;
         mouseDragStartPoint_ = currentPoint;
      } else if (mode_ == PAN) {
         zoomableStack_.translateView(mouseDragStartPoint_.x - currentPoint.x, mouseDragStartPoint_.y - currentPoint.y);
         redrawPixels();
         mouseDragStartPoint_ = currentPoint;
         drawZoomIndicatorOverlay();
      } 
   }

   private void drawNewGridOverlay() {
      Overlay overlay = new Overlay();

      double dsTileWidth = tileWidth_ / (double) zoomableStack_.getDownsampleFactor();
      double dsTileHeight = tileHeight_ / (double) zoomableStack_.getDownsampleFactor();
      int roiWidth = (int) ((newGrid_.cols * dsTileWidth) - ((newGrid_.cols - 1) * newGrid_.overlapX) / zoomableStack_.getDownsampleFactor());
      int roiHeight = (int) ((newGrid_.rows * dsTileHeight) - ((newGrid_.rows - 1) * newGrid_.overlapY) / zoomableStack_.getDownsampleFactor());
      
      Roi rectangle = new Roi(newGrid_.centerX - roiWidth / 2, newGrid_.centerY - roiHeight / 2, roiWidth, roiHeight);
      rectangle.setStrokeWidth(10f);
      overlay.add(rectangle);
      this.getImagePlus().setOverlay(overlay);
   }
   
   private void drawNewSurfaceOverlay() {
      Overlay overlay = new Overlay();
      for (Point3d point : newSurface_.getPoints()) {
         //convert back to pixel coordinates
         Point xy = posManager_.getPixelCoordsFromStageCoords(point.x,point.y);
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
      drawOverlay();
   }
   
   public void activatePanMode(boolean activate) {
      mode_ = activate ? PAN : NONE;
   }
   
   public void activateNewGridMode(boolean activate) {
      mode_ = activate ? NEWGRID : NONE;
      newGrid_ = new NewGridParams(1, 1, 0, 0, this.getImagePlus().getWidth() / 2, this.getImagePlus().getHeight() / 2);
   }
   
   public void activateNewSurfaceMode(boolean activate) {
      mode_ = activate ? NEWSURFACE : NONE;
      newSurface_ = new SurfaceInterpolater();
   }

   public void gridSizeChanged(int numRows, int numCols, int xOverlap, int yOverlap) {      
      newGrid_.rows = numRows;
      newGrid_.cols = numCols;
      newGrid_.overlapX = xOverlap;
      newGrid_.overlapY = yOverlap;
      drawOverlay();
   }

   public void createGrid() {
//      try {
//         //get displacements of center of rectangle from center of stitched image
//         double rectCenterXDisp = this.getImagePlus().getOverlay().get(0).getFloatBounds().getCenterX()
//                 - this.getImagePlus().getWidth() / 2;
//         double rectCenterYDisp = this.getImagePlus().getOverlay().get(0).getFloatBounds().getCenterY()
//                 - this.getImagePlus().getHeight() / 2;
//
//         Point2D.Double stagePos = Util.stagePositionFromPixelPosition(rectCenterXDisp, rectCenterYDisp);
//
////         int xOverlap = SettingsDialog.getXOverlap(), yOverlap = SettingsDialog.getYOverlap();
//         int xOverlap = 0, yOverlap = 0;
//         Util.createGrid(stagePos.x, stagePos.y,
//                 (Integer) gridXSpinner_.getValue(), (Integer) gridYSpinner_.getValue(),
//                 xOverlap, yOverlap);
//
//      } catch (Exception e) {
//         ReportingUtils.showError("Couldnt create grid");
//      }
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
            drawOverlay();
         }

         @Override
         public void mouseMoved(MouseEvent e) {
            drawOverlay();
         }
      });

      canvas_.addMouseListener(new MouseListener() {

         @Override
         public void mouseClicked(MouseEvent e) {
         }

         @Override
         public void mousePressed(MouseEvent e) {
            mouseDragStartPoint_ = e.getPoint();
            drawOverlay();
         }

         @Override
         public void mouseReleased(MouseEvent e) {
            mouseReleasedActions(e);
            mouseDragStartPoint_ = null;
            drawOverlay();
         }

         @Override
         public void mouseEntered(MouseEvent e) {
            cursorOverImage_ = true;
            drawOverlay();
         }

         @Override
         public void mouseExited(MouseEvent e) {
            cursorOverImage_ = false;
            drawOverlay();
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
               controls_.togglePanMode();
               drawOverlay();
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
   
   class NewGridParams {
      
      double centerX, centerY;
      int overlapX, overlapY, rows, cols;
      
      public NewGridParams(int r, int c, int ox, int oy, double cx, double cy) {
         centerX = cx;
         centerY = cy;
         rows = r;
         cols = c;
         overlapX = ox;
         overlapY = oy;
      }
      
   }
}
