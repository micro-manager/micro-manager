package imagedisplay;

/*
 * Master stitched window to display real time stitched images, allow navigating of XY more easily
 */
import acq.Acquisition;
import acq.ExploreAcquisition;
import acq.MultiResMultipageTiffStorage;
import coordinates.PositionManager;
import ij.CompositeImage;
import ij.IJ;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.api.ImageCache;
import org.micromanager.imagedisplay.MMCompositeImage;


import org.micromanager.utils.*;
import surfacesandregions.MultiPosRegion;
import surfacesandregions.RegionManager;
import surfacesandregions.SurfaceManager;

public class DisplayPlus extends VirtualAcquisitionDisplay {

   private static final int EXPLORE_PIXEL_BUFFER = 96;
   
   public static final int NONE = 0, EXPLORE = 1, GOTO = 2, NEWGRID = 3, NEWSURFACE = 4;
   
   private ImageCanvas canvas_;
   private DisplayPlusControls controls_;
   private Acquisition acq_;
   private Point mouseDragStartPointLeft_, mouseDragStartPointRight_;
   private ArrayList<Point> selectedPositions_ = new ArrayList<Point>();
   private ZoomableVirtualStack zoomableStack_;
   private final boolean exploreAcq_;
   private PositionManager posManager_;
   private boolean cursorOverImage_;
   private int mode_ = NONE;
   private boolean mouseDragging_ = false;
   private RegionManager regionManager_;
   private SurfaceManager surfaceManager_;
   private DisplayOverlayer overlayer_;
   
   

   public DisplayPlus(final ImageCache stitchedCache, Acquisition acq, JSONObject summaryMD, 
           MultiResMultipageTiffStorage multiResStorage, RegionManager rManager, SurfaceManager sManager) {
      super(stitchedCache, null, "test", true);
      posManager_ = multiResStorage.getPositionManager();
      exploreAcq_ = acq instanceof ExploreAcquisition;
      regionManager_ = rManager;
      surfaceManager_ = sManager;
      
//      surfaceManager_.addListDataListener(this);
      
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
      overlayer_ = new DisplayOverlayer(this, sManager, rManager, acq, multiResStorage.getTileWidth(), multiResStorage.getTileHeight());

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
      overlayer_.setSurfaceDisplayParams(convexHull, stagePos, surf);
      overlayer_.renderOverlay(true);
   }

   public int getMode() {
      return mode_;
   }

   public void drawOverlay() {
      drawOverlay(true);
   }
   
   public void drawOverlay(boolean interrupt) {
      overlayer_.renderOverlay(interrupt);
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
            Point2D.Double stagePos = stageCoordFromImageCoords(e.getPoint().x, e.getPoint().y);
            //calculate tolerance
            int pixelDistTolerance = 10;
            Point2D.Double toleranceStagePos = stageCoordFromImageCoords(e.getPoint().x + pixelDistTolerance, e.getPoint().y + pixelDistTolerance);
            double stageDistanceTolerance = Math.sqrt( (toleranceStagePos.x - stagePos.x)*(toleranceStagePos.x - stagePos.x) +
                    (toleranceStagePos.y - stagePos.y)*(toleranceStagePos.y - stagePos.y) );
            surfaceManager_.getCurrentSurface().deleteClosestPoint(stagePos.x, stagePos.y, stageDistanceTolerance);    
            surfaceManager_.updateListeners();
         } else if (SwingUtilities.isLeftMouseButton(e)) {
            //convert to real coordinates in 3D space
            //Click point --> full res pixel point --> stage coordinate
            Point2D.Double stagePos = stageCoordFromImageCoords(e.getPoint().x, e.getPoint().y);
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
   
   public Point imageCoordsFromStageCoords(double x, double y) {
      //convert back to pixel coordinates
      Point xy = posManager_.getPixelCoordsFromStageCoords(x, y);
      //convert ful res pixel coordinates to coordinates of the viewed image
      return zoomableStack_.getDisplayImageCoordsFromFullImageCoords(xy);
   }

   public Point2D.Double stageCoordFromImageCoords(int x, int y) {
      Point fullResPix = zoomableStack_.getAbsoluteFullResPixelCoordinate(x, y);
      return posManager_.getStageCoordsFromPixelCoords(fullResPix.x, fullResPix.y);
   }

   public void zoom(boolean in) {
      zoomableStack_.zoom(cursorOverImage_ ? canvas_.getCursorLoc() : null, in ? -1 :1);
      redrawPixels();
      drawOverlay();
   }
   
   public void activateExploreMode(boolean activate) {
      mode_ = activate && exploreAcq_ ? EXPLORE : NONE;
   }
   
   public void activateNewGridMode(boolean activate) {
      mode_ = activate ? NEWGRID : NONE;
   }
   
   public void activateNewSurfaceMode(boolean activate) {
      mode_ = activate ? NEWSURFACE : NONE;
      drawOverlay();
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
   
   public boolean cursorOverImage() {
      return cursorOverImage_;
   }
   
   public Point getMouseDragStartPointLeft() {
      return mouseDragStartPointLeft_;
   }
   
   public Point getMouseDragStartPointRight() {
      return mouseDragStartPointRight_;
   }

   private void setupMouseListeners() {
      //remove channel switching scroll wheel listener
      this.getImagePlus().getWindow().removeMouseWheelListener(
              this.getImagePlus().getWindow().getMouseWheelListeners()[0]);
      //remove canvas mouse listener and virtualacquisitiondisplay as mouse listener
      canvas_.removeMouseListener(canvas_.getMouseListeners()[0]);
      canvas_.removeMouseListener(canvas_.getMouseListeners()[0]);

      canvas_.addMouseWheelListener(new MouseWheelListener() {
         @Override
         public void mouseWheelMoved(MouseWheelEvent mwe) { 
            if (mwe.getWheelRotation() < 0) {
               zoom(true);
            } else if (mwe.getWheelRotation() > 0) {
               zoom(false);
            }
         }
      });
      
      canvas_.addMouseMotionListener(new MouseMotionListener() {

         @Override
         public void mouseDragged(MouseEvent e) {
            mouseDraggedActions(e);
            drawOverlay();
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
            drawOverlay();
         }

         @Override
         public void mouseReleased(MouseEvent e) {
            mouseReleasedActions(e);
            mouseDragStartPointLeft_ = null;
            mouseDragStartPointRight_ = null;
            drawOverlay();
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

//   @Override
//   public void intervalAdded(ListDataEvent e) {
//   }
//
//   @Override
//   public void intervalRemoved(ListDataEvent e) {
//   }
//
//   @Override
//   public void contentsChanged(ListDataEvent e) {
//      this.drawOverlay();
//   }
}
