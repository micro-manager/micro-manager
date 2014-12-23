package imagedisplay;

/*
 * Master stitched window to display real time stitched images, allow navigating
 * of XY more easily
 */
import acq.Acquisition;
import acq.ExploreAcquisition;
import acq.FixedAreaAcquisition;
import acq.MultiResMultipageTiffStorage;
import com.google.common.eventbus.Subscribe;
import coordinates.PositionManager;
import gui.SettingsDialog;
import ij.CompositeImage;
import ij.IJ;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Toolbar;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import mmcloneclasses.acquisition.MMImageCache;
import mmcloneclasses.imagedisplay.DisplayWindow;
import mmcloneclasses.imagedisplay.MMCompositeImage;
import mmcloneclasses.imagedisplay.VirtualAcquisitionDisplay;
import org.json.JSONObject;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;
import surfacesandregions.MultiPosGrid;
import surfacesandregions.SurfaceInterpolator;

public class DisplayPlus extends VirtualAcquisitionDisplay implements ListDataListener {

   private static final int DELETE_SURF_POINT_PIXEL_TOLERANCE = 10;
   public static final int NONE = 0, EXPLORE = 1, GOTO = 2, NEWGRID = 3, NEWSURFACE = 4;
   private static ArrayList<DisplayPlus> activeDisplays_ = new ArrayList<DisplayPlus>();
   private ImageCanvas canvas_;
   private Acquisition acq_;
   private Point mouseDragStartPointLeft_, mouseDragStartPointRight_, currentMouseLocation_;
   private ArrayList<Point> selectedPositions_ = new ArrayList<Point>();
   private ZoomableVirtualStack zoomableStack_;
   private final boolean exploreAcq_;
   private PositionManager posManager_;
   private boolean cursorOverImage_;
   private int mode_ = NONE;
   private boolean mouseDragging_ = false;
   private DisplayOverlayer overlayer_;
   private SurfaceInterpolator currentSurface_;
   private MultiPosGrid currentRegion_;
   private ThreadPoolExecutor redrawPixelsExecutor_;

   public DisplayPlus(final MMImageCache stitchedCache, Acquisition acq, JSONObject summaryMD,
           MultiResMultipageTiffStorage multiResStorage ) {
      super(stitchedCache, null, "test", true);
      posManager_ = multiResStorage.getPositionManager();
      exploreAcq_ = acq instanceof ExploreAcquisition;

      //create redraw pixels executor
      redrawPixelsExecutor_ = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {

         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Pixel update thread ");
         }
      });

      //Set parameters for tile dimensions, num rows and columns, overlap, and image dimensions
      acq_ = acq;

      this.getEventBus().register(this);

      //Add in custom controls
      subImageControls_ = new SubImageControls(this, this.getEventBus(), acq);

      //add in customized zoomable acquisition virtual stack
      try {
         //looks like nSlicess only really matters during display startup
         int nSlices = MDUtils.getNumChannels(summaryMD) * MDUtils.getNumFrames(summaryMD) * MDUtils.getNumSlices(summaryMD);

         //craete stack with appropriate image size
         int height, width;
         if (SettingsDialog.getAutoImageSize()) {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            height = screenSize.height - 250;
            width = height;
         } else {
            width = SettingsDialog.getImageWidth();
            height = SettingsDialog.getImageHeight();
         }
         //use width and height for explore window as they are. scale to correct aspect ratio for fixed area acquisitions        
         int displayWidth, displayHeight;
         int maxZoomIndex = -1;
         if (exploreAcq_) {
            displayHeight = height;
            displayWidth = width;
            mode_ = EXPLORE;
         } else {
            int fullWidth = ((FixedAreaAcquisition) acq).getNumColumns() * multiResStorage.getTileWidth();
            int fullHeight = ((FixedAreaAcquisition) acq).getNumRows() * multiResStorage.getTileHeight();
            double imageRatio = (double) fullWidth / (double) fullHeight;
            double displayRatio = (double) width / (double) height;
            //goal is to make image fit snuggly into display window when fully zoomed out
            if (imageRatio > displayRatio) {
               //wide image
               displayWidth = fullWidth;
               while (displayWidth > width) {
                  displayWidth /= 2;
               }
               displayHeight = (int) (displayWidth / imageRatio);
            } else {
               displayHeight = fullHeight;
               while (displayHeight > height) {
                  displayHeight /= 2;
               }
               displayWidth = (int) (displayHeight * imageRatio);
            }
            maxZoomIndex = (int) Math.round(Math.log(fullWidth / displayWidth) / Math.log(2));
         }
         zoomableStack_ = new ZoomableVirtualStack(MDUtils.getIJType(summaryMD), displayWidth, displayHeight,
                 stitchedCache, nSlices, this, multiResStorage, acq_, maxZoomIndex);

         this.show(zoomableStack_);
      } catch (Exception e) {
         e.printStackTrace();
         ReportingUtils.showError("Problem with initialization due to missing summary metadata tags");
         return;
      }
      overlayer_ = new DisplayOverlayer(this, acq, multiResStorage.getTileWidth(), multiResStorage.getTileHeight());

      canvas_ = this.getImagePlus().getCanvas();

      //Zoom to 100%
      canvas_.unzoom();

      setupKeyListeners();
      setupMouseListeners();
      IJ.setTool(Toolbar.SPARE6);
      stitchedCache.addImageCacheListener(this);
      canvas_.requestFocus();
      activeDisplays_.add(this);
   }

   @Subscribe
   public void onWindowClose(DisplayWindow.RequestToCloseEvent event) {
      //abort acquisition if needed then call super method
      if (!acq_.isFinished()) {
         int result = JOptionPane.showConfirmDialog(null, "Finish acquisition?", "Finish Current Acquisition", JOptionPane.OK_CANCEL_OPTION);
         if (result == JOptionPane.OK_OPTION) {
            acq_.abort();
         } else {
            return;
         }
      }
      overlayer_.shutdown();
      activeDisplays_.remove(this);

      super.onWindowClose(event);
   }

   public void setSurfaceDisplaySettings(boolean convexHull, boolean stagePos, boolean surf) {
      overlayer_.setSurfaceDisplayParams(convexHull, stagePos, surf);
      overlayer_.renderOverlay(true);
   }

   public int getMode() {
      return mode_;
   }

   public static void redrawRegionOverlay(MultiPosGrid region) {
      for (DisplayPlus display : activeDisplays_) {
         if (display.getCurrentRegion() == region) {
            display.drawOverlay(true);
         }
      }
   }

   public static void redrawSurfaceOverlay(SurfaceInterpolator surface) {
      for (DisplayPlus display : activeDisplays_) {
         if (display.getCurrentSurface() == surface) {
            display.drawOverlay(true);
         }
      }
   }

   public void setCurrentSurface(SurfaceInterpolator surf) {
      currentSurface_ = surf;
      drawOverlay(true);
   }

   public void setCurrentRegion(MultiPosGrid region) {
      currentRegion_ = region;
      drawOverlay(false);
   }

   public SurfaceInterpolator getCurrentSurface() {
      return currentSurface_;
   }

   public MultiPosGrid getCurrentRegion() {
      return currentRegion_;
   }

   public void drawOverlay(boolean interruptSurfcaeRendering) {
      overlayer_.renderOverlay(interruptSurfcaeRendering);
   }

   private void mouseReleasedActions(MouseEvent e) {
      if (exploreAcq_ && mode_ == EXPLORE && SwingUtilities.isLeftMouseButton(e)) {
         Point p2 = e.getPoint();
         //find top left row and column and number of columns spanned by drage event
         Point tile1 = zoomableStack_.getTileIndicesFromDisplayedPixel(mouseDragStartPointLeft_.x, mouseDragStartPointLeft_.y);
         Point tile2 = zoomableStack_.getTileIndicesFromDisplayedPixel(p2.x, p2.y);
         //create events to acquire one or more tiles
         ((ExploreAcquisition) acq_).acquireTiles(tile1.y, tile1.x, tile2.y, tile2.x);
      } else if (mode_ == NEWSURFACE) {
         if (SwingUtilities.isRightMouseButton(e) && !mouseDragging_) {
            //delete point if one is nearby
            Point2D.Double stagePos = stageCoordFromImageCoords(e.getPoint().x, e.getPoint().y);
            //calculate tolerance
            Point2D.Double toleranceStagePos = stageCoordFromImageCoords(e.getPoint().x + DELETE_SURF_POINT_PIXEL_TOLERANCE, e.getPoint().y + DELETE_SURF_POINT_PIXEL_TOLERANCE);
            double stageDistanceTolerance = Math.sqrt((toleranceStagePos.x - stagePos.x) * (toleranceStagePos.x - stagePos.x)
                    + (toleranceStagePos.y - stagePos.y) * (toleranceStagePos.y - stagePos.y));
            currentSurface_.deleteClosestPoint(stagePos.x, stagePos.y, stageDistanceTolerance);
         } else if (SwingUtilities.isLeftMouseButton(e)) {
            //convert to real coordinates in 3D space
            //Click point --> full res pixel point --> stage coordinate
            Point2D.Double stagePos = stageCoordFromImageCoords(e.getPoint().x, e.getPoint().y);
            double z = zoomableStack_.getZCoordinateOfDisplayedSlice(this.getHyperImage().getSlice(), this.getHyperImage().getFrame());
            currentSurface_.addPoint(stagePos.x, stagePos.y, z);
         }
      }
      if (mouseDragging_ && SwingUtilities.isRightMouseButton(e)) {
         //drag event finished, make sure pixels updated
         redrawPixels(true);
      }
      mouseDragging_ = false;
   }

   private void mouseDraggedActions(MouseEvent e) {
      Point currentPoint = e.getPoint();
      mouseDragging_ = true;
      if (SwingUtilities.isRightMouseButton(e)) {
         //pan
         zoomableStack_.translateView(mouseDragStartPointRight_.x - currentPoint.x, mouseDragStartPointRight_.y - currentPoint.y);
         redrawPixels(false);
         mouseDragStartPointRight_ = currentPoint;
      } else if (SwingUtilities.isLeftMouseButton(e)) {
         //only move grid
         if (mode_ == NEWGRID) {
            int dx = (currentPoint.x - mouseDragStartPointLeft_.x);
            int dy = (currentPoint.y - mouseDragStartPointLeft_.y);
            //convert pixel dx dy to stage dx dy
            Point2D.Double p0 = stageCoordFromImageCoords(0, 0);
            Point2D.Double p1 = stageCoordFromImageCoords(dx, dy);
            currentRegion_.translate(p1.x - p0.x, p1.y - p0.y);
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
      zoomableStack_.zoom(cursorOverImage_ ? canvas_.getCursorLoc() : null, in ? -1 : 1);
      redrawPixels(true);
   }

   public void setMode(int mode) {
      mode_ = mode;
      drawOverlay(true);
   }

   private void redrawPixels(final boolean forcePaint) {
      redrawPixelsExecutor_.execute(new Runnable() {

         @Override
         public void run() {
            //Set pixels, do this every time
            if (!DisplayPlus.this.getHyperImage().isComposite()) {
               int index = DisplayPlus.this.getHyperImage().getCurrentSlice();
               Object pixels = zoomableStack_.getPixels(index);
               DisplayPlus.this.getHyperImage().getProcessor().setPixels(pixels);
            } else {
               CompositeImage ci = (CompositeImage) DisplayPlus.this.getHyperImage();
               if (ci.getMode() == CompositeImage.COMPOSITE) {
                  for (int i = 0; i < ((MMCompositeImage) ci).getNChannelsUnverified(); i++) {
                     //Dont need to set pixels if processor is null because it will get them from stack automatically  
                     Object pixels = zoomableStack_.getPixels(ci.getCurrentSlice() - ci.getChannel() + i + 1);
                     if (ci.getProcessor(i + 1) != null && pixels != null) {
                        ci.getProcessor(i + 1).setPixels(pixels);
                     }
                  }
               }
               Object pixels = zoomableStack_.getPixels(DisplayPlus.this.getHyperImage().getCurrentSlice());
               if (pixels != null) {
                  ci.getProcessor().setPixels(pixels);
               }
            }
            //always draw overlay when pixels need to be updated, because this call will interrupt itself if need be     
            drawOverlay(true);

            //send back to EDT
            SwingUtilities.invokeLater(new Runnable() {

               @Override
               public void run() {
                  DisplayPlus.this.updateAndDraw(forcePaint);
               }
            });

         }
      });
   }

   public boolean cursorOverImage() {
      return cursorOverImage_;
   }

   public Point getCurrentMouseLocation() {
      return currentMouseLocation_;
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
            currentMouseLocation_ = e.getPoint();
            mouseDraggedActions(e);
            drawOverlay(false);
         }

         @Override
         public void mouseMoved(MouseEvent e) {
            currentMouseLocation_ = e.getPoint();
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
         }

         @Override
         public void mouseReleased(MouseEvent e) {
            mouseReleasedActions(e);
            mouseDragStartPointLeft_ = null;
            mouseDragStartPointRight_ = null;
         }

         @Override
         public void mouseEntered(MouseEvent e) {
            cursorOverImage_ = true;
            drawOverlay(false);
         }

         @Override
         public void mouseExited(MouseEvent e) {
            currentMouseLocation_ = null;
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

   public boolean acquisitionFinished() {
      return acq_.isFinished();
   }

   @Override
   public long getNextWakeTime() {
      return ((FixedAreaAcquisition) acq_).getNextWakeTime_ms();
   }

   @Override
   public void intervalAdded(ListDataEvent e) {
      if (mode_ == NEWSURFACE) {
         drawOverlay(true);
      } else if (mode_ == NEWGRID) {
         drawOverlay(false);
      }
   }

   @Override
   public void intervalRemoved(ListDataEvent e) {
      if (mode_ == NEWSURFACE) {
         drawOverlay(true);
      } else if (mode_ == NEWGRID) {
         drawOverlay(false);
      }
   }

   @Override
   public void contentsChanged(ListDataEvent e) {
      if (mode_ == NEWSURFACE) {
         drawOverlay(true);
      } else if (mode_ == NEWGRID) {
         drawOverlay(false);
      }
   }
}
