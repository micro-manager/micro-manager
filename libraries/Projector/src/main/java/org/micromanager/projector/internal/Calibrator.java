
package org.micromanager.projector.internal;

import ij.IJ;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.filter.GaussianBlur;
import ij.process.ImageProcessor;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.projector.Mapping;
import org.micromanager.projector.ProjectionDevice;
import org.micromanager.propertymap.MutablePropertyMapView;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.imageanalysis.ImageUtils;
import org.micromanager.internal.utils.MathFunctions;

/**
 *
 * @author nico
 */
public class Calibrator {
      private final Studio app_;
      private final CMMCore core_;
      private final ProjectionDevice dev_;
      private final MutablePropertyMapView settings_;
      private final AtomicBoolean stopRequested_;
      private final AtomicBoolean isRunning_;
      private final ExecutorService executor_;
   
   public Calibrator(Studio app, ProjectionDevice dev, 
           MutablePropertyMapView settings) {
      app_ = app;
      core_ = app_.getCMMCore();
      dev_ = dev;
      settings_ = settings;
      executor_ = Executors.newSingleThreadExecutor();
      stopRequested_ = new AtomicBoolean(false);
      isRunning_ = new AtomicBoolean(false);
   }
   
   public void requestStop() {
      stopRequested_.set(true);
   }
   
    /**
    * Illuminate a spot at position x,y.
    */
   private void displaySpot(double x, double y) {
      if (x >= dev_.getXMinimum() && x < (dev_.getXRange() + dev_.getXMinimum())
            && y >= dev_.getYMinimum() && y < (dev_.getYRange() + dev_.getYMinimum())) {
         dev_.displaySpot(x, y);
      }
   }
   
   /**
    * Illuminate a spot at the center of the Galvo/SLM range, for
    * the exposure time.
    */
   void displayCenterSpot() {
      double x = dev_.getXRange() / 2 + dev_.getXMinimum();
      double y = dev_.getYRange() / 2 + dev_.getYMinimum();
      dev_.displaySpot(x, y);
   }
   
     // Find the brightest spot in an ImageProcessor. The image is first blurred
   // and then the pixel with maximum intensity is returned.
   private static Point findPeak(ImageProcessor proc) {
      ImageProcessor blurImage = proc.duplicate();
      blurImage.setRoi((Roi) null);
      GaussianBlur blur = new GaussianBlur();
      blur.blurGaussian(blurImage, 10, 10, 0.01);
      //showProcessor("findPeak",proc);
      Point x = ImageUtils.findMaxPixel(blurImage);
      x.translate(1, 1);
      return x;
   }
   
   /**
    * Display a spot using the projection device, and return its current
    * location on the camera.  Does not do sub-pixel localization, but could
    * (just would change its return type, most other code would be OK with this)
   */
   private Point measureSpotOnCamera(Point2D.Double projectionPoint) {
      if (stopRequested_.get()) {
         return null;
      }
      try {
         dev_.turnOff();
         // This sleep was likely inserted by Arthur to make the code work with 
         // the Andor Mosaic.  It is not needed for the GenericSLM, and 
         // also not for several other devices. We could have user-input for
         // this and two other sleeps in this function, however, that will make
         // things quite confusing for the user.  For now, have a single delay 
         // field and use it in multiple locations where a sleep is warranted
         // for one reason or another.
         
         int delayMs = Integer.parseInt(settings_.getString(Terms.DELAY, "10"));
         Thread.sleep(delayMs);
         core_.snapImage();
         TaggedImage image = core_.getTaggedImage();
         ImageProcessor proc1 = ImageUtils.makeMonochromeProcessor(image);
         // JonD: should use the exposure that the user has set to avoid hardcoding a value;
         // if the user wants a different exposure time for calibration than for use it's easy to specify
         // => commenting out next two lines
         // long originalExposure = dev_.getExposure();
         // dev_.setExposure(500000);
         displaySpot(projectionPoint.x, projectionPoint.y);
         // NS: Timing between displaySpot and snapImage is critical
         // we have no idea how fast the device will respond
         // if we add "dev_.waitForDevice(), then the RAPP UGA-40 will already have ended
         // its exposure before returning control
         // For now, wait for a user specified delay
         Thread.sleep(delayMs);
         core_.snapImage();
         // JonD: added line below to short-circuit exposure time
         dev_.turnOff();
         // NS: just make sure to wait until the spot is no longer displayed
         // JonD: time to wait is simply the exposure time
         // JonD: no longer needed now that we turn the device off after taking our image
         // Thread.sleep((int) (dev_.getExposure()/1000) - delayMs);
         // JonD: see earlier comment => commenting out next line
         // dev_.setExposure(originalExposure);
         TaggedImage taggedImage2 = core_.getTaggedImage();
         ImageProcessor proc2 = ImageUtils.makeMonochromeProcessor(taggedImage2);
         app_.live().displayImage(app_.data().convertTaggedImage(taggedImage2));
         ImageProcessor diffImage = ImageUtils.subtractImageProcessors(proc2.convertToFloatProcessor(), proc1.convertToFloatProcessor());
         Point maxPt = findPeak(diffImage);
         IJ.getImage().setRoi(new PointRoi(maxPt.x, maxPt.y));
         // NS: what is this second sleep good for????
         Thread.sleep(delayMs);
         return maxPt;
      } catch (Exception e) {
         app_.logs().showError(e);
         return null;
      }
   }

   
   /**
    * Illuminate a spot at ptSLM, measure its location on the camera, and
    * add the resulting point pair to the spotMap.
    */
   private void measureAndAddToSpotMap(Map<Point2D.Double, Point2D.Double> spotMap,
         Point2D.Double ptSLM) {
      Point ptCam = measureSpotOnCamera(ptSLM);
      Point2D.Double ptCamDouble = new Point2D.Double(ptCam.x, ptCam.y);
      spotMap.put(ptCamDouble, ptSLM);
   }

   /**
    * Illuminates and images five control points near the center,
    * and return an affine transform mapping from image coordinates
    * to phototargeter coordinates.
    */
   private AffineTransform generateLinearMapping() {
      double centerX = dev_.getXRange() / 2 + dev_.getXMinimum();
      double centerY = dev_.getYRange() / 2 + dev_.getYMinimum();
      double spacing = Math.min(dev_.getXRange(), dev_.getYRange() ) / 30;  // user 3% of galvo/SLM range
      Map<Point2D.Double, Point2D.Double> spotMap = new HashMap<>();

      measureAndAddToSpotMap(spotMap, new Point2D.Double(centerX, centerY));
      measureAndAddToSpotMap(spotMap, new Point2D.Double(centerX, centerY + spacing));
      measureAndAddToSpotMap(spotMap, new Point2D.Double(centerX + spacing, centerY));
      measureAndAddToSpotMap(spotMap, new Point2D.Double(centerX, centerY - spacing));
      measureAndAddToSpotMap(spotMap, new Point2D.Double(centerX - spacing, centerY));
      if (stopRequested_.get()) {
         return null;
      }
      try {
         // require that the RMS value between the mapped points and the measured points be less than 5% of image size
         final long imageSize = Math.min(core_.getImageWidth(), core_.getImageHeight()); 
         return MathFunctions.generateAffineTransformFromPointPairs(spotMap, imageSize*0.05, Double.MAX_VALUE);
      } catch (Exception e) {
         throw new RuntimeException("Spots aren't detected as expected. " + 
                 "Is the Projector in focus and roughly centered in camera's field of view?");
      }
   }
   
    /**
    * Generate a nonlinear calibration mapping for the current device settings.
    * A rectangular lattice of points is illuminated one-by-one on the
    * projection device, and locations in camera pixels of corresponding
    * spots on the camera image are recorded. For each rectangular
    * cell in the grid, we take the four point mappings (camera to projector)
    * and generate a local AffineTransform using linear least squares.
    * Cells with suspect measured corner positions are discarded.
    * A mapping of cell polygon to AffineTransform is generated. 
    */
   private Mapping generateNonlinearMapping() {
      
      // get the affine transform near the center spot
      final AffineTransform firstApproxAffine = generateLinearMapping();
      
      // then use this single transform to estimate what SLM coordinates 
      // correspond to the image's corner positions 
      final Point2D.Double camCorner1 = 
              (Point2D.Double) firstApproxAffine.transform(
                      new Point2D.Double(0, 0), null);
      final Point2D.Double camCorner2 = 
              (Point2D.Double) firstApproxAffine.transform(
                      new Point2D.Double((int) core_.getImageWidth(), (int) core_.getImageHeight()), null);
      final Point2D.Double camCorner3 = 
              (Point2D.Double) firstApproxAffine.transform(
                      new Point2D.Double(0, (int) core_.getImageHeight()), null);
      final Point2D.Double camCorner4 = 
              (Point2D.Double) firstApproxAffine.transform(
                      new Point2D.Double((int) core_.getImageWidth(), 0), null);

      // figure out camera's bounds in SLM coordinates
      // min/max because we don't know the relative orientation of the camera and SLM
      // do some extra checking in case camera/SLM aren't at exactly 90 degrees from each other, 
      // but still better that they are at 0, 90, 180, or 270 degrees from each other
      // TODO can create grid along camera location instead of SLM's if camera is the limiting factor; this will make arbitrary rotation possible
      final double camLeft = Math.min(Math.min(Math.min(camCorner1.x, camCorner2.x), camCorner3.x), camCorner4.x);
      final double camRight = Math.max(Math.max(Math.max(camCorner1.x, camCorner2.x), camCorner3.x), camCorner4.x);
      final double camTop = Math.min(Math.min(Math.min(camCorner1.y, camCorner2.y), camCorner3.y), camCorner4.y);
      final double camBottom = Math.max(Math.max(Math.max(camCorner1.y, camCorner2.y), camCorner3.y), camCorner4.y);
      
      // these are the SLM's bounds
      final double slmLeft = dev_.getXMinimum();
      final double slmRight = dev_.getXRange() + dev_.getXMinimum();
      final double slmTop = dev_.getYMinimum();
      final double slmBottom = dev_.getYRange() + dev_.getYMinimum();
      
      // figure out the "overlap region" where both the camera and SLM
      // can "see", expressed in SLM coordinates
      final double left = Math.max(camLeft, slmLeft);
      final double right = Math.min(camRight, slmRight);
      final double top = Math.max(camTop, slmTop);
      final double bottom = Math.min(camBottom, slmBottom);
      final double width = right - left;
      final double height = bottom - top;

      // compute a grid of SLM points inside the "overlap region"
      // nGrid is how many polygons in both X and Y
      // require (nGrid + 1)^2 spot measurements to get nGrid^2 squares
      // TODO allow user to change nGrid
      final int nGrid = 7;
      Point2D.Double slmPoint[][] = new Point2D.Double[1 + nGrid][1 + nGrid];
      Point2D.Double camPoint[][] = new Point2D.Double[1 + nGrid][1 + nGrid];

      // tabulate the camera spot at each of SLM grid points
      for (int i = 0; i <= nGrid; ++i) {
         for (int j = 0; j <= nGrid; ++j) {
            double xoffset = ((i + 0.5) * width / (nGrid + 1.0));
            double yoffset = ((j + 0.5) * height / (nGrid + 1.0));
            slmPoint[i][j] = new Point2D.Double(left + xoffset, top + yoffset);
            Point spot = measureSpotOnCamera(slmPoint[i][j]);
            if (spot != null) {
                camPoint[i][j] = new Point2D.Double(spot.x, spot.y);
            }
         }
      }

      if (stopRequested_.get()) {
         return null;
      }

      // now make a grid of (square) polygons (in camera's coordinate system)
      // and generate an affine transform for each of these square regions
      Map<Polygon, AffineTransform> bigMap = new HashMap<>();
      for (int i = 0; i <= nGrid - 1; ++i) {
         for (int j = 0; j <= nGrid - 1; ++j) {
            Polygon poly = new Polygon();
            Utils.addVertex(poly, Utils.toIntPoint(camPoint[i][j]));
            Utils.addVertex(poly, Utils.toIntPoint(camPoint[i][j + 1]));
            Utils.addVertex(poly, Utils.toIntPoint(camPoint[i + 1][j + 1]));
            Utils.addVertex(poly, Utils.toIntPoint(camPoint[i + 1][j]));

            Map<Point2D.Double, Point2D.Double> map = new HashMap<>();
            map.put(camPoint[i][j], slmPoint[i][j]);
            map.put(camPoint[i][j + 1], slmPoint[i][j + 1]);
            map.put(camPoint[i + 1][j], slmPoint[i + 1][j]);
            map.put(camPoint[i + 1][j + 1], slmPoint[i + 1][j + 1]);
            double srcDX = Math.abs((camPoint[i+1][j].x - camPoint[i][j].x))/4; 
            double srcDY = Math.abs((camPoint[i][j+1].y - camPoint[i][j].y))/4;
            double srcTol = Math.max(srcDX, srcDY);

            try {
               AffineTransform transform = MathFunctions.generateAffineTransformFromPointPairs(map, srcTol, Double.MAX_VALUE);
               bigMap.put(poly, transform);
            } catch (Exception e) {
               app_.logs().logError("Bad cell in mapping.");
            }
         }
      }
      try {
         Mapping.Builder mb = new Mapping.Builder();
         // amazing that there is no API call for binning!
         String binningAsString = core_.getProperty(core_.getCameraDevice(), "Binning");
         // Hamamatsu reports 1x1.  I wish there was an api call for binning
         int binning = Integer.parseInt(binningAsString.substring(0, 1));
         mb.setMap(bigMap).setApproximateTransform(firstApproxAffine).setROI(core_.getROI()).setBinning(binning);
         return mb.build();
      } catch (Exception ex) {
         return null;
      }
   }

   /**
    * Runs the full calibration. First
    * generates a linear mapping (a first approximation) and then generates
    * a second piece-wise "non-linear" mapping of affine transforms. Saves
    * the mapping to Java Preferences.
    * @return true if successful, false if interrupted or otherwise fails
    */
   public Future<Boolean> runCalibration() {
      return executor_.submit(() -> {
         app_.live().setSuspended(true);
         if (!isRunning_.get()) {
            stopRequested_.set(false);

            try {
               isRunning_.set(true);
               if (app_.live().getDisplay() == null) {
                   app_.live().snap(true);
                  // wait for the display to appear 
                  // It would be better to get the DisplayDidShowImageEvent 
                  // from the displayController (which itself can be retrieved
                  // using the NewDisplayEvent)
                  Thread.sleep(1000);
               }
               Roi originalROI = IJ.getImage().getRoi();

               // do the heavy lifting of generating the local affine transform map
              Mapping mapping = generateNonlinearMapping();

               dev_.turnOff();
               try {
                  Thread.sleep(500);
               } catch (InterruptedException ex) {
                  app_.logs().logError(ex);
               }

               // save local affine transform map to preferences
               // TODO allow different mappings to be stored for different channels (e.g. objective magnification)
               if (!stopRequested_.get()) {
                  List<Image> snap = app_.live().snap(false);
                  snap.get(0).getHeight(); snap.get(0).getMetadata().getBinning();
                  MappingStorage.saveMapping(core_, dev_, settings_, mapping);
               }
               IJ.getImage().setRoi(originalROI);
            } catch (HeadlessException e) {
               app_.logs().showError(e);
            } catch (RuntimeException e) {
               app_.logs().showError(e);
            } catch (InterruptedException ex) {
               app_.logs().logError(ex);
            } finally {
               app_.live().setSuspended(false);
               isRunning_.set(false);
            }
         }

         return !stopRequested_.get();
      });
   }

   /**
    * Returns true if the calibration is currently running.
    *
    * @return true if calibration is running
    */
   public boolean isCalibrating() {
      return isRunning_.get();
   }
   
   
   
   
}
