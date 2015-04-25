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
import ij.CompositeImage;
import ij.IJ;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Toolbar;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.concurrent.ThreadFactory;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import acq.MMImageCache;
import ij.measure.Calibration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import misc.LongPoint;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.MMTags;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;
import surfacesandregions.MultiPosRegion;
import surfacesandregions.SurfaceInterpolator;

public class DisplayPlus extends VirtualAcquisitionDisplay implements ListDataListener {

   private static final int MOUSE_WHEEL_ZOOM_INTERVAL_MS = 100;
   private static final int DELETE_SURF_POINT_PIXEL_TOLERANCE = 10;
   public static final int NONE = 0, EXPLORE = 1, NEWGRID = 2, NEWSURFACE = 3;
   private static ArrayList<DisplayPlus> activeDisplays_ = new ArrayList<DisplayPlus>();
   private Acquisition acq_;
   //all these are volatile because they are accessed by overlayer
   private volatile Point mouseDragStartPointLeft_, mouseDragStartPointRight_, currentMouseLocation_;
   private volatile Point exploreStartTile_, exploreEndTile_;
   private long lastMouseWheelZoomTime_ = 0;
   private ZoomableVirtualStack zoomableStack_;
   private final boolean exploreAcq_;
   private PositionManager posManager_;
   private volatile int mode_ = NONE;
   private boolean mouseDragging_ = false;
   private DisplayOverlayer overlayer_;
   private volatile SurfaceInterpolator currentSurface_;
   private volatile MultiPosRegion currentRegion_;
   private ExecutorService redrawPixelsExecutor_;
   private Future previousPixelDrawTask_;
   private int fullResPixelWidth_ = -1, fullResPixelHeight_ = -1; //used for scaling in fixed area acqs
   private int tileWidth_, tileHeight_;

   public DisplayPlus(final MMImageCache stitchedCache, final Acquisition acq, JSONObject summaryMD,
           MultiResMultipageTiffStorage multiResStorage) {
      super(stitchedCache, acq.getName(), summaryMD);
      tileWidth_ = multiResStorage.getTileWidth();
      tileHeight_ = multiResStorage.getTileHeight();

      posManager_ = multiResStorage.getPositionManager();
      exploreAcq_ = acq instanceof ExploreAcquisition;

      //create redraw pixels executor
      redrawPixelsExecutor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {

         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, acq.getName() + ": Pixel update thread ");
         }
      });

      //Set parameters for tile dimensions, num rows and columns, overlap, and image dimensions
      acq_ = acq;

      this.getEventBus().register(this);


      //add in customized zoomable acquisition virtual stack
      try {
         //looks like nSlicess only really matters during display startup
         int nSlices = MDUtils.getNumChannels(summaryMD) * MDUtils.getNumFrames(summaryMD) * MDUtils.getNumSlices(summaryMD);


         if (exploreAcq_) {
            mode_ = EXPLORE;
         } else {
            fullResPixelWidth_ = ((FixedAreaAcquisition) acq_).getNumColumns() * multiResStorage.getTileWidth();
            fullResPixelHeight_ = ((FixedAreaAcquisition) acq_).getNumRows() * multiResStorage.getTileHeight();
         }

         //zoomable stack dimensions don't matter because they get changed on window startup
         //But making it at least 200 is good, because smaller than this causes a weird smaller canvas 
         //panel that screws up the autolayout
         zoomableStack_ = new ZoomableVirtualStack(MDUtils.getIJType(summaryMD), 200, 200,
                 stitchedCache, nSlices, this, multiResStorage, acq_);


      } catch (Exception e) {
         e.printStackTrace();
         ReportingUtils.showError("Problem with initialization due to missing summary metadata tags");
         return;
      }

      this.show(zoomableStack_);

      setupKeyListeners();
      setupMouseListeners();
      IJ.setTool(Toolbar.SPARE6);
      stitchedCache.setDisplay(this);
      canvas_.requestFocus();
      activeDisplays_.add(this);
   }

   //Thread safe calls for getting displayed indices
   public int getVisibleSliceIndex() {
      return subImageControls_ == null ? 0 : subImageControls_.getDisplayedSlice();
   }

   public int getVisibleFrameIndex() {
      return subImageControls_.getDisplayedFrame();
   }

   public int getVisibleChannelIndex() {
      return subImageControls_.getDisplayedChannel();
   }

   public ZoomableVirtualStack getZoomableStack() {
      return zoomableStack_;
   }

   protected void applyPixelSizeCalibration() {
      try {
         JSONObject summary = getSummaryMetadata();
         double pixSizeUm;
         if (summary != null && summary.has(MMTags.Summary.PIXSIZE)) {
            pixSizeUm = summary.getDouble(MMTags.Summary.PIXSIZE);
         } else {
            ReportingUtils.showError("pixel size tag missing from summary metadata");
            return;
         }
         //multiply by zoom factor
         pixSizeUm *= zoomableStack_.getDownsampleFactor();

         if (pixSizeUm > 0) {
            Calibration cal = new Calibration();
            if (pixSizeUm < 10) {
               cal.setUnit("um");
               cal.pixelWidth = pixSizeUm;
               cal.pixelHeight = pixSizeUm;
            } else if (pixSizeUm < 1000) {
               cal.setUnit("mm");
               cal.pixelWidth = pixSizeUm / 1000;
               cal.pixelHeight = pixSizeUm / 1000;
            } else {
               cal.setUnit("cm");
               cal.pixelWidth = pixSizeUm / 10000;
               cal.pixelHeight = pixSizeUm / 10000;
            }
            String intMs = "Interval_ms";
            if (summary.has(intMs)) {
               cal.frameInterval = summary.getDouble(intMs) / 1000.0;
            }
            String zStepUm = "z-step_um";
            if (summary.has(zStepUm)) {
               cal.pixelDepth = summary.getDouble(zStepUm);
            }
            this.getHyperImage().setCalibration(cal);
         }
      } catch (JSONException ex) {
         // no pixelsize defined.  Nothing to do
      }
   }

   protected void updateWindowTitleAndStatus() {
      String name = title_ + " ";
      name += acq_.isFinished() ? "(Finished)" : "(Running)";
      this.getHyperImage().getWindow().setTitle(name);
   }

   public void windowAndCanvasReady() {
      canvas_ = this.getImagePlus().getCanvas();
      overlayer_ = new DisplayOverlayer(this, acq_, tileWidth_, tileHeight_, zoomableStack_);
      //so that contrast panel can signal to redraw overlay
      ((DisplayWindow) this.getHyperImage().getWindow()).getContrastPanel().setOverlayer(overlayer_);
   }

   public int getFullResWidth() {
      return fullResPixelWidth_;
   }

   public int getFullResHeight() {
      return fullResPixelHeight_;
   }

   public Acquisition getAcquisition() {
      return acq_;
   }

   /**
    * Change the stack so that the resolution of the imageplus can change for
    * window resizing
    *
    * @param newStack
    */
   public synchronized void changeStack(ZoomableVirtualStack newStack) {
      zoomableStack_ = newStack;
      super.virtualStack_ = newStack;
      try {
         if (this.getHyperImage() instanceof MMCompositeImage) {
            JavaUtils.setRestrictedFieldValue(super.getHyperImage(), CompositeImage.class, "rgbPixels", null);
         }
         super.getHyperImage().setStack(newStack);
         //account for border in image drawing
         JavaUtils.setRestrictedFieldValue(canvas_, ImageCanvas.class, "imageWidth",
                 canvas_.getWidth() - 2 * DisplayWindow.CANVAS_PIXEL_BORDER);
         JavaUtils.setRestrictedFieldValue(canvas_, ImageCanvas.class, "imageHeight",
                 canvas_.getHeight() - 2 * DisplayWindow.CANVAS_PIXEL_BORDER);
      } catch (NoSuchFieldException ex) {
         ReportingUtils.showError("Couldn't change ImageCanvas width");
      } catch (NullPointerException exc) {
         exc.printStackTrace();
      }
      overlayer_.setStack(newStack);
   }

   @Subscribe
   public void onWindowClose(DisplayWindow.RequestToCloseEvent event) {
      //make sure user wants to close if it involves aborting acq
      if (!acq_.isFinished()) {
         int result = JOptionPane.showConfirmDialog(null, "Finish acquisition?", "Finish Current Acquisition", JOptionPane.OK_CANCEL_OPTION);
         if (result == JOptionPane.OK_OPTION) {
            try {
               acq_.abort();
            } catch (Exception e) {
               ReportingUtils.showError("Couldn't successfully abort acq");
               e.printStackTrace();
            }
         } else {
            return;
         }
      }
      overlayer_.shutdown();
      activeDisplays_.remove(this);
      redrawPixelsExecutor_.shutdownNow();

      super.onWindowClose(event);
   }

   public void setSurfaceDisplaySettings(boolean convexHull, boolean stagePos, boolean surf) {
      overlayer_.setSurfaceDisplayParams(convexHull, stagePos, surf);
      overlayer_.redrawOverlay();
   }

   public int getMode() {
      return mode_;
   }

   public static void redrawRegionOverlay(MultiPosRegion region) {
      for (DisplayPlus display : activeDisplays_) {
         if (display.getCurrentRegion() == region) {
            display.drawOverlay();
         }
      }
   }

   public static void redrawSurfaceOverlay(SurfaceInterpolator surface) {
      for (DisplayPlus display : activeDisplays_) {
         if (display.getCurrentSurface() == surface) {
            display.drawOverlay();
         }
      }
   }

   public void setCurrentSurface(SurfaceInterpolator surf) {
      currentSurface_ = surf;
      if (surf != null) {
         drawOverlay();
      }
   }

   public void setCurrentRegion(MultiPosRegion region) {
      currentRegion_ = region;
      if (currentRegion_ != null) {
         drawOverlay();
      }
   }

   public SurfaceInterpolator getCurrentSurface() {
      return currentSurface_;
   }

   public MultiPosRegion getCurrentRegion() {
      return currentRegion_;
   }

   public void drawOverlay() {
      overlayer_.redrawOverlay();
   }

   private void mouseReleasedActions(MouseEvent e) {
      if (exploreAcq_ && mode_ == EXPLORE && SwingUtilities.isLeftMouseButton(e)) {
         Point p2 = e.getPoint();
         if (exploreStartTile_ != null) {
            //create events to acquire one or more tiles
            ((ExploreAcquisition) acq_).acquireTiles(exploreStartTile_.y, exploreStartTile_.x, exploreEndTile_.y, exploreEndTile_.x);
            exploreStartTile_ = null;
            exploreEndTile_ = null;
         } else {
            //find top left row and column and number of columns spanned by drage event
            exploreStartTile_ = zoomableStack_.getTileIndicesFromDisplayedPixel(mouseDragStartPointLeft_.x, mouseDragStartPointLeft_.y);
            exploreEndTile_ = zoomableStack_.getTileIndicesFromDisplayedPixel(p2.x, p2.y);
         }
         overlayer_.redrawOverlay();
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
            double z = zoomableStack_.getZCoordinateOfDisplayedSlice(this.getVisibleSliceIndex(), this.getVisibleFrameIndex());
            if (currentSurface_ == null) {
               ReportingUtils.showError("Can't add point--No surface selected");
            } else {
               currentSurface_.addPoint(stagePos.x, stagePos.y, z);
            }
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
         zoomableStack_.pan(mouseDragStartPointRight_.x - currentPoint.x, mouseDragStartPointRight_.y - currentPoint.y);
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
         } else if (mode_ == EXPLORE) {
            overlayer_.redrawOverlay();
         }
      }
   }

   public LongPoint imageCoordsFromStageCoords(double x, double y) {
      //convert back to pixel coordinates
      Point xy = posManager_.getPixelCoordsFromStageCoords(x, y);
      //convert ful res pixel coordinates to coordinates of the viewed image
      return zoomableStack_.getDisplayImageCoordsFromFullImageCoords(xy);
   }

   public Point2D.Double stageCoordFromImageCoords(long x, long y) {
      LongPoint fullResPix = zoomableStack_.getAbsoluteFullResPixelCoordinate(x, y);
      return posManager_.getStageCoordsFromPixelCoords(fullResPix.x_, fullResPix.y_);
   }

   /**
    * zoom and redraw the given number of levels - is zoom in, + is zoom out
    *
    * @param numLevels
    */
   public void zoom(int numLevels) {
      zoomableStack_.zoom(currentMouseLocation_ != null ? canvas_.getCursorLoc() : null, numLevels);
      redrawPixels(true);
   }

   public void setMode(int mode) {
      //delete potential explore tiles on mode change
      exploreEndTile_ = null;
      exploreStartTile_ = null;
      mode_ = mode;
      drawOverlay();
   }
   
   public Point getCurrentMouseLocation() {
      return currentMouseLocation_;
   }
   
   public Point getExploreStartTile() {
      return exploreStartTile_;
   }
   
   public Point getExploreEndTile() {
      return exploreEndTile_;
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
      canvas_.addMouseWheelListener(new MouseWheelListener() {

         @Override
         public void mouseWheelMoved(MouseWheelEvent mwe) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastMouseWheelZoomTime_ > MOUSE_WHEEL_ZOOM_INTERVAL_MS) {
               lastMouseWheelZoomTime_ = currentTime;
               if (mwe.getWheelRotation() < 0) {
                  zoom(-1); //zoom in
               } else if (mwe.getWheelRotation() > 0) {
                  zoom(1); //zoom out
               }
            }
         }
      });

      canvas_.addMouseMotionListener(new MouseMotionListener() {

         @Override
         public void mouseDragged(MouseEvent e) {
            currentMouseLocation_ = e.getPoint();
            mouseDraggedActions(e);
         }

         @Override
         public void mouseMoved(MouseEvent e) {
            currentMouseLocation_ = e.getPoint();
            if (mode_ == EXPLORE) {
               drawOverlay();
            }
         }
      });

      canvas_.addMouseListener(new MouseListener() {

         @Override
         public void mouseClicked(MouseEvent e) {
         }

         @Override
         public void mousePressed(MouseEvent e) {
            //to make zoom respond properly when switching between windows
            canvas_.requestFocusInWindow();
            if (SwingUtilities.isRightMouseButton(e)) {
               //clear potential explore region
               exploreEndTile_ = null;
               exploreStartTile_ = null;
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
            if (mode_ == EXPLORE) {
               drawOverlay();
            }
         }

         @Override
         public void mouseExited(MouseEvent e) {
            currentMouseLocation_ = null;
            if (mode_ == EXPLORE) {
               drawOverlay();
            }
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
               zoom(-1); //zoom in
            } else if (ke.getKeyChar() == '-') {
               zoom(1); // zoom out
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

   public long getNextWakeTime() {
      return ((FixedAreaAcquisition) acq_).getNextWakeTime_ms();
   }

   @Override
   public void intervalAdded(ListDataEvent e) {
      if (mode_ == NEWSURFACE || mode_ == NEWGRID) {
         drawOverlay();
      }
   }

   @Override
   public void intervalRemoved(ListDataEvent e) {
      if (mode_ == NEWSURFACE || mode_ == NEWGRID) {
         drawOverlay();
      }
   }

   @Override
   public void contentsChanged(ListDataEvent e) {
      if (mode_ == NEWSURFACE || mode_ == NEWGRID) {
         drawOverlay();
      }
   }

   private synchronized void redrawPixels(final boolean forcePaint) {
      if (previousPixelDrawTask_ != null && !previousPixelDrawTask_.isDone()) {
         previousPixelDrawTask_.cancel(true);
      }

      previousPixelDrawTask_ = new RedrawPixelsRunnable(forcePaint);
      redrawPixelsExecutor_.submit((Runnable)previousPixelDrawTask_);
   }

   private class RedrawPixelsRunnable implements RunnableFuture {

      private volatile boolean cancel_ = false;
      private final boolean forcePaint_;
      private volatile boolean done_ = false;

      public RedrawPixelsRunnable(boolean forcePaint) {
         forcePaint_ = forcePaint;
      }

      @Override
      public void run() {
         //put all this stuff on EDT because stuff that interacts with CompositeImage
         //should occur a single threaded, orderly fashion
         SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
               //Make sure correct pixels are set...think this is redundant when showing acquired 
               //images, but needed for zooming and panning
               if (!DisplayPlus.this.getHyperImage().isComposite()) {
                  //monochrome images
                  Object pixels = zoomableStack_.getPixels(DisplayPlus.this.getHyperImage().getCurrentSlice());
                  DisplayPlus.this.getHyperImage().getProcessor().setPixels(pixels);
               } else {
                  CompositeImage ci = (CompositeImage) DisplayPlus.this.getHyperImage();
                  if (ci.getMode() == CompositeImage.COMPOSITE) {
                     //in case number of pixels has changed, update channel processors wih this call
                     ((MMCompositeImage) DisplayPlus.this.getHyperImage()).updateImage();
                     //now make sure each channel processor has pixels correctly
                     for (int i = 0; i < ((MMCompositeImage) ci).getNChannelsUnverified(); i++) {
                        //Dont need to set pixels if processor is null because it will get them from stack automatically  
                        Object pixels = zoomableStack_.getPixels(ci.getCurrentSlice() - ci.getChannel() + i + 1);
                        if (ci.getProcessor(i + 1) != null && pixels != null) {
                           ci.getProcessor(i + 1).setPixels(pixels);
                        }
                     }
                  }
                  if (cancel_) {
                     done_ = true;
                     return;
                  }
                  Object pixels = zoomableStack_.getPixels(DisplayPlus.this.getHyperImage().getCurrentSlice());
                  if (pixels != null) {
                     ci.getProcessor().setPixels(pixels);
                  }
               }
               if (cancel_) {
                  done_ = true;
                  return;
               }
               //always draw overlay when pixels need to be updated, because this call will interrupt itself if need be     
               drawOverlay();

               if (cancel_) {
                  done_ = true;
                  return;
               }
               DisplayPlus.this.updateAndDraw(forcePaint_);
               done_ = true;
            }
         });
      }

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
         cancel_ = true;
         return true;
      }

      @Override
      public boolean isCancelled() {
         throw new UnsupportedOperationException("Not supported yet.");
      }

      @Override
      public boolean isDone() {
         return done_;
      }

      @Override
      public Object get() throws InterruptedException, ExecutionException {
         throw new UnsupportedOperationException("Not supported yet.");
      }

      @Override
      public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
         throw new UnsupportedOperationException("Not supported yet.");
      }
   }
}
