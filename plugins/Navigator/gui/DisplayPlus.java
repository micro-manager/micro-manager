package gui;

/*
 * Master stitched window to display real time stitched images, allow navigating of XY more easily
 */
import acq.CustomAcqEngine;
import acq.MultiResMultipageTiffStorage;
import acq.PositionManager;
import ij.CompositeImage;
import ij.IJ;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.*;
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
   
   private static final Color TRANSPARENT_BLUE = new Color(0, 0, 255, 100);
   private static final Color TRANSPARENT_MAGENTA = new Color(255, 0, 255, 100);
   private ImageCanvas canvas_;
   private DisplayPlusControls controls_;
   private CustomAcqEngine eng_;
   private JSpinner gridXSpinner_, gridYSpinner_;
   private Point clickStart_;
   private Point gridStart_, mouseDragStartPoint_;
   private boolean suspendUpdates_ = false;
   private ArrayList<Point> selectedPositions_ = new ArrayList<Point>();
   private boolean gotoMode_ = false, newGridMode_ = false;
   private ZoomableVirtualStack zoomableStack_;
   private final boolean exploreMode_;
   private PositionManager posManager_;
   private int tileWidth_, tileHeight_;
   private boolean spacebarDown_ = false;
   private boolean cursorOverImage_;

   public DisplayPlus(final ImageCache stitchedCache, CustomAcqEngine eng, JSONObject summaryMD, 
           MultiResMultipageTiffStorage multiResStorage, boolean exploreMode) {
      super(stitchedCache, null, "test", true);
      tileWidth_ = multiResStorage.getTileWidth();
      tileHeight_ = multiResStorage.getTileHeight();
      posManager_ = multiResStorage.getPositionManager();
      exploreMode_ = exploreMode;
      
      //Set parameters for tile dimensions, num rows and columns, overlap, and image dimensions
      eng_ = eng;

      
      controls_ = new DisplayPlusControls(this, this.getEventBus(), exploreMode, eng);

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
                 this, multiResStorage, exploreMode_ ? EXPLORE_PIXEL_BUFFER : 0);
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
   }
//
//   public void readGridInfoFromPositionList(JSONArray posList) {
//      try {
//         //get grid parameters
//         for (int i = 0; i < posList.length(); i++) {
//            long colInd = posList.getJSONObject(i).getLong("GridColumnIndex");
//            long rowInd = posList.getJSONObject(i).getLong("GridRowIndex");
//            if (colInd >= numCols_) {
//               numCols_ = (int) (colInd + 1);
//            }
//            if (rowInd >= numRows_) {
//               numRows_ = (int) (rowInd + 1);
//            }
//
//         }
//      } catch (Exception e) {
//         ReportingUtils.showError("Couldnt get grid info");
//      }
//      fullResHeight_ = numRows_ * tileHeight_ - (numRows_ - 1) * yOverlap_;
//      fullResWidth_ = numCols_ * tileWidth_ - (numCols_ - 1) * xOverlap_;
//   }

    
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
   
   private void clearOverlay() {
      canvas_.setOverlay(null);
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

   private void setupKeyListeners() {
      //remove ImageJ key listeners
      ImageWindow window = this.getImagePlus().getWindow();
      window.removeKeyListener(this.getImagePlus().getWindow().getKeyListeners()[0]);
      canvas_.removeKeyListener(canvas_.getKeyListeners()[0]);     
      
      KeyListener kl = new KeyListener() {
         @Override
         public void keyTyped(KeyEvent ke) {
             if (ke.getKeyChar() == '=') {
                clearOverlay();
               zoomableStack_.zoom(cursorOverImage_ ? canvas_.getCursorLoc() : null, -1);
               redrawPixels();
            } else if (ke.getKeyChar() == '-') {
               clearOverlay();
               zoomableStack_.zoom(cursorOverImage_ ? canvas_.getCursorLoc() : null, 1);
               redrawPixels();
            }            
         }

         @Override
         public void keyPressed(KeyEvent ke) {
            if (ke.getKeyCode() == KeyEvent.VK_SPACE) {
               clearOverlay();
               spacebarDown_ = true;
            }
         }

         @Override
         public void keyReleased(KeyEvent ke) {
            if (ke.getKeyCode() == KeyEvent.VK_SPACE) {
               spacebarDown_ = false;
            }
         }
      };
      
      //add keylistener to window and all subscomponenets so it will fire whenever
      //focus in anywhere in the window
      addRecursively(window, kl);  
   }
   
   private void addRecursively(Component c, KeyListener kl) {
      c.addKeyListener(kl);
      if (c instanceof Container) {
         for (Component subC : ((Container) c).getComponents() ) {
            addRecursively(subC, kl);
         }
      }      
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
            Point currentPoint = e.getPoint();
            if (newGridMode_) {
               int dx = (int) ((currentPoint.x - clickStart_.x) / canvas_.getMagnification());
               int dy = (int) ((currentPoint.y - clickStart_.y) / canvas_.getMagnification());
               DisplayPlus.this.getImagePlus().getOverlay().get(0).setLocation(gridStart_.x + dx, gridStart_.y + dy);
               if (!CanvasPaintPending.isMyPaintPending(canvas_, this)) {
                  canvas_.setPaintPending(true);
                  canvas_.paint(canvas_.getGraphics());
               }
            } else if  ( spacebarDown_) {
               zoomableStack_.translateView(mouseDragStartPoint_.x - currentPoint.x,mouseDragStartPoint_.y - currentPoint.y);
               redrawPixels();
               mouseDragStartPoint_ = currentPoint;
               drawZoomIndicatorOverlay();               
            } else if (exploreMode_) {
               //find top left row and column and number of columns spanned by drage event          
               Point p2Tiles = zoomableStack_.getTileIndicesFromDisplayedPixel(e.getPoint().x, e.getPoint().y),
                       p1Tiles = zoomableStack_.getTileIndicesFromDisplayedPixel(mouseDragStartPoint_.x, mouseDragStartPoint_.y);
               highlightTiles(Math.min(p1Tiles.y, p2Tiles.y), Math.max(p1Tiles.y, p2Tiles.y),
                       Math.min(p1Tiles.x, p2Tiles.x), Math.max(p1Tiles.x, p2Tiles.x), TRANSPARENT_MAGENTA);
            }
         }

         @Override
         public void mouseMoved(MouseEvent e) {
            if (exploreMode_ && !newGridMode_ && !spacebarDown_) {
               Point coords = zoomableStack_.getTileIndicesFromDisplayedPixel(e.getPoint().x, e.getPoint().y);
               int row = coords.y, col = coords.x;
               //highlight tile(s)
               highlightTiles(row, row, col, col, TRANSPARENT_MAGENTA);
            }
         }
      });

      canvas_.addMouseListener(new MouseListener() {

         @Override
         public void mouseClicked(MouseEvent e) {
            if (gotoMode_) {
//               //translate point into stage coordinates and move there
//               Point p = e.getPoint();
//               double xPixelDisp = (p.x / canvas_.getMagnification())
//                       + canvas_.getSrcRect().x - vad_.getImagePlus().getWidth() / 2;
//               double yPixelDisp = (p.y / canvas_.getMagnification())
//                       + canvas_.getSrcRect().y - vad_.getImagePlus().getHeight() / 2;
//
//               Point2D stagePos = stagePositionFromPixelPosition(xPixelDisp, yPixelDisp);
//               try {
//                  MMStudioMainFrame.getInstance().setXYStagePosition(stagePos.getX(), stagePos.getY());
//               } catch (MMScriptException ex) {
//                  ReportingUtils.showError("Couldn't move xy stage");
//               }
//               controls_.clearSelectedButtons();
            } 
         }

         @Override
         public void mousePressed(MouseEvent e) {
            if (newGridMode_) {
               clickStart_ = e.getPoint();
               Roi rect = DisplayPlus.this.getImagePlus().getOverlay().get(0);
               Rectangle2D bounds = rect.getFloatBounds();
               gridStart_ = new Point((int) bounds.getX(), (int) bounds.getY());
            } else if ( exploreMode_) {
               //used for both multiple tile selection and dragging to zoom
               mouseDragStartPoint_ = e.getPoint();
            } 
         }

         @Override
         public void mouseReleased(MouseEvent e) {
            if (exploreMode_ && !newGridMode_  && !spacebarDown_) {

               Point p2 = e.getPoint();
               //find top left row and column and number of columns spanned by drage event
               Point tile1 = zoomableStack_.getTileIndicesFromDisplayedPixel(mouseDragStartPoint_.x, mouseDragStartPoint_.y);
               Point tile2 = zoomableStack_.getTileIndicesFromDisplayedPixel(p2.x, p2.y);
               //create events to acquire one or more tiles
               eng_.acquireTiles(tile1.y, tile1.x, tile2.y, tile2.x);

               //TODO: immediately redraw image to reflect updates positions in position manager
               
               //redraw overlay to potentially reflect new grid   
//               Point coords = storage_.tileIndicesFromLoResPixel(e.getPoint().x, e.getPoint().y);
//               int row = coords.x, col = coords.y;
//               //highlight tile(s)
//               highlightTiles(row, row, col, col, TRANSPARENT_MAGENTA);
            }
         }

         @Override
         public void mouseEntered(MouseEvent e) {
            cursorOverImage_ = true;
         }

         @Override
         public void mouseExited(MouseEvent e) {
            cursorOverImage_ = false;
            if (!gotoMode_) {
               return;
            }
         }
      });
   }

   private void createGrid() {
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

   private void makeGridOverlay(int centerX, int centerY, int rows, int cols, int xOverlap, int yOverlap) {
//      Overlay overlay = this.getImagePlus().getOverlay();
//      if (overlay == null || overlay.size() == 0) {
//         overlay = new Overlay();
//      } else {
//         overlay.clear();
//      }
//
//      double dsTileWidth = storage_.getTileWidth() / (double) storage_.getDSFactor();
//      double dsTileHeight = storage_.getTileHeight() / (double) storage_.getDSFactor();
//      int roiWidth = (int) ((cols * dsTileWidth) - ((cols - 1) * xOverlap) / storage_.getDSFactor());
//      int roiHeight = (int) ((rows * dsTileHeight) - ((rows - 1) * yOverlap) / storage_.getDSFactor());
//
//      Roi rectangle = new Roi(centerX - roiWidth / 2, centerY - roiHeight / 2, roiWidth, roiHeight);
//      rectangle.setStrokeWidth(10f);
//      overlay.add(rectangle);
//      this.getImagePlus().setOverlay(overlay);
   }

   public void resizeGrid(int rows, int cols, int xOverlap, int yOverlap) {
      //resize exisiting grid but keep centered on same area
      Overlay overlay = this.getImagePlus().getOverlay();
      if (overlay == null || overlay.get(0) == null) {
         return;
      }
      Rectangle2D oldBounds = overlay.get(0).getFloatBounds();
      int centerX = (int) oldBounds.getCenterX();
      int centerY = (int) oldBounds.getCenterY();
      makeGridOverlay(centerX, centerY, rows, cols, xOverlap, yOverlap);
   }
   
   public void newGrid(boolean selected, int rows, int cols, int xOverlap, int yOverlap) {
      if (selected) {
         newGridMode_ = true;
         makeGridOverlay(this.getImagePlus().getWidth() / 2, this.getImagePlus().getHeight() / 2,
                 rows, cols, xOverlap, yOverlap);
      } else {
         newGridMode_ = false;
         this.getImagePlus().setOverlay(null);
         canvas_.repaint();
      }
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

      if (!suspendUpdates_) {
         //make sure scrollbars render properly even if event doesn't come through
         int channel = 0, frame = 0, slice = 0, position = 0;
         try {
            frame = MDUtils.getFrameIndex(taggedImage.tags);
            slice = MDUtils.getSliceIndex(taggedImage.tags);
            channel = MDUtils.getChannelIndex(taggedImage.tags);
            position = MDUtils.getPositionIndex(taggedImage.tags);
            // Construct a mapping of axis to position so we can post an 
            // event informing others of the new image.
            HashMap<String, Integer> axisToPosition = new HashMap<String, Integer>();
            axisToPosition.put("channel", channel);
            axisToPosition.put("position", position);
            axisToPosition.put("time", frame);
            axisToPosition.put("z", slice);
            this.getEventBus().post(new NewImageEvent(axisToPosition));
         } catch (JSONException ex) {
            ReportingUtils.logError(ex);
         }


         super.imageReceived(taggedImage);
      } else {
//         try {
//            //tSelector will be null on first frame
//            if (tSelector_ != null) {
//               int frame = MDUtils.getFrameIndex(taggedImage.tags);
//               if (tSelector_.getMaximum() <= (1 + frame)) {
//                  ((IMMImagePlus) this.getHyperImage()).setNFramesUnverified(frame + 1);
//                  tSelector_.setMaximum(frame + 2);
//                  tSelector_.invalidate();
//                  tSelector_.validate();
//               }
//            }
//         } catch (Exception ex) {
//            ReportingUtils.showError("Couldn't suspend updates");
//         }
      }

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
}
