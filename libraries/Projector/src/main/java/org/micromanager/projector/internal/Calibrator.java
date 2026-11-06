package org.micromanager.projector.internal;

import ij.IJ;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.filter.GaussianBlur;
import ij.process.ImageProcessor;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.LogManager;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.internal.utils.MathFunctions;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;
import org.micromanager.projector.Mapping;
import org.micromanager.projector.ProjectionDevice;
import org.micromanager.projector.ProjectorActions;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Calibration of phototargetting devices (Galvos and SLMs).
 *
 *
 * @author nico
 */
public class Calibrator {

   private static final double EDGE_MARGIN_FRACTION = 0.10;
   private static final double MIN_SPOT_INTENSITY = 100.0;
   // Half-size (pixels) of the centroid window; must contain the spot core plus some margin
   private static final int CENTROID_RADIUS = 15;
   static final int MIN_CALIBRATION_POINTS = 2;
   static final int MAX_CALIBRATION_POINTS = 8;
   static final int DEFAULT_CALIBRATION_POINTS = 8;
   // fewer calibration cells than this are subdivided to get enough check points
   static final int MIN_CHECK_POINTS = 10;
   static final double DEFAULT_CALIBRATION_SMOOTHING = 0.0;
   static final double MAX_CALIBRATION_SMOOTHING = 3.0;
   static final double[] CALIBRATION_SMOOTHING_CHOICES = {0.0, 0.5, 1.0, 1.5, 2.0};

   private final Studio app_;
   private final CMMCore core_;
   private final ProjectionDevice dev_;
   private final MutablePropertyMapView settings_;
   private final AtomicBoolean stopRequested_;
   private final AtomicBoolean isRunning_;
   private final ExecutorService executor_;
   // spot detection method, read once per run so that a run never mixes methods
   private volatile boolean useCentroid_;
   // raw measurements of the last calibration, saved with the mapping
   private CalibrationGrid lastGrid_;

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
    * Number of calibration spots per side of the grid, clamped to the supported range.
    */
   static int getCalibrationPoints(MutablePropertyMapView settings) {
      int n = settings.getInteger(Terms.CALIBRATION_POINTS, DEFAULT_CALIBRATION_POINTS);
      return Math.max(MIN_CALIBRATION_POINTS, Math.min(MAX_CALIBRATION_POINTS, n));
   }

   /**
    * Width (in grid spacings) of the neighbourhood used to smooth calibration spots; 0 = off.
    */
   static double getCalibrationSmoothing(MutablePropertyMapView settings) {
      double s = settings.getDouble(Terms.CALIBRATION_SMOOTHING, DEFAULT_CALIBRATION_SMOOTHING);
      return Math.max(0.0, Math.min(MAX_CALIBRATION_SMOOTHING, s));
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
    * Illuminate a spot at the center of the Galvo/SLM range, for the exposure time.
    */
   void displayCenterSpot() {
      double x = dev_.getXRange() / 2 + dev_.getXMinimum();
      double y = dev_.getYRange() / 2 + dev_.getYMinimum();
      dev_.displaySpot(x, y);
   }

   // Find the brightest spot in an ImageProcessor. The image is first blurred
   // and then the pixel with maximum intensity is returned (pixel centers at integers).
   static Point2D.Double findPeak(ImageProcessor proc) {
      ImageProcessor blurImage = proc.duplicate();
      blurImage.setRoi((Roi) null);
      GaussianBlur blur = new GaussianBlur();
      blur.blurGaussian(blurImage, 10, 10, 0.01);
      //showProcessor("findPeak",proc);
      Point x = ImageUtils.findMaxPixel(blurImage);
      return new Point2D.Double(x.x, x.y);
   }

   /**
    * Sub-pixel spot position: iterated intensity-weighted centroid in a square of half-size
    * CENTROID_RADIUS, after subtracting the median of that square's border pixels.
    * Seeded at the maximum of a lightly blurred copy. Returns null if there is no signal.
    */
   static Point2D.Double findCentroid(ImageProcessor proc) {
      ImageProcessor blurImage = proc.duplicate();
      blurImage.setRoi((Roi) null);
      new GaussianBlur().blurGaussian(blurImage, 2, 2, 0.01);
      Point seed = ImageUtils.findMaxPixel(blurImage);
      if (seed.x < 0) {
         return null;
      }
      final int w = proc.getWidth();
      final int h = proc.getHeight();
      double cx = seed.x;
      double cy = seed.y;
      for (int iter = 0; iter < 5; iter++) {
         int x0 = Math.max(0, (int) Math.round(cx) - CENTROID_RADIUS);
         int y0 = Math.max(0, (int) Math.round(cy) - CENTROID_RADIUS);
         int x1 = Math.min(w - 1, (int) Math.round(cx) + CENTROID_RADIUS);
         int y1 = Math.min(h - 1, (int) Math.round(cy) + CENTROID_RADIUS);
         float[] border = new float[2 * (x1 - x0 + 1) + 2 * Math.max(0, y1 - y0 - 1)];
         int n = 0;
         for (int x = x0; x <= x1; x++) {
            border[n++] = proc.getf(x, y0);
            border[n++] = proc.getf(x, y1);
         }
         for (int y = y0 + 1; y < y1; y++) {
            border[n++] = proc.getf(x0, y);
            border[n++] = proc.getf(x1, y);
         }
         Arrays.sort(border, 0, n);
         final double bg = border[n / 2];
         double sw = 0;
         double sx = 0;
         double sy = 0;
         for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
               double v = proc.getf(x, y) - bg;
               if (v > 0) {
                  sw += v;
                  sx += v * x;
                  sy += v * y;
               }
            }
         }
         if (sw <= 0) {
            return null;
         }
         cx = sx / sw;
         cy = sy / sw;
      }
      return new Point2D.Double(cx, cy);
   }

   /**
    * Display a spot using the projection device, and return its current location on the camera.
    * Uses the sub-pixel centroid when enabled in the settings, otherwise the blurred maximum.
    */
   private Point2D.Double measureSpotOnCamera(Point2D.Double projectionPoint) {
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
         final ImageProcessor proc1 = ImageUtils.makeMonochromeProcessor(image);
         // JonD: should use the exposure that the user has set to avoid hardcoding a value;
         // if the user wants a different exposure time for calibration than for use it's easy
         // to specify
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
         ImageProcessor diffImage = ImageUtils
               .subtractImageProcessors(proc2.convertToFloatProcessor(),
                     proc1.convertToFloatProcessor());
         if (diffImage.getMax() < MIN_SPOT_INTENSITY) {
            return null;
         }
         Point2D.Double maxPt = null;
         if (useCentroid_) {
            maxPt = findCentroid(diffImage);
         }
         if (maxPt == null) {
            maxPt = findPeak(diffImage);
         }
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
    * Illuminate a spot at ptSLM, measure its location on the camera, and add the resulting point
    * pair to the spotMap.
    */
   private void measureAndAddToSpotMap(Map<Point2D.Double, Point2D.Double> spotMap,
         Point2D.Double ptSLM) {
      Point2D.Double ptCam = measureSpotOnCamera(ptSLM);
      if (ptCam == null) {
         throw new RuntimeException("Spot at (" + (int) ptSLM.x + ", " + (int) ptSLM.y
               + ") was not detected on camera. "
               + "Is the projector beam visible and aimed at the camera?");
      }
      spotMap.put(ptCam, ptSLM);
   }

   /**
    * Illuminates and images five control points near the center, and return an affine transform
    * mapping from image coordinates to phototargeter coordinates.
    */
   private AffineTransform generateLinearMapping() {
      double centerX = dev_.getXRange() / 2 + dev_.getXMinimum();
      double centerY = dev_.getYRange() / 2 + dev_.getYMinimum();
      centerX = settings_.getDouble(Terms.XCENTER, centerX);
      centerY = settings_.getDouble(Terms.YCENTER, centerY);
      double spacing =
            Math.min(dev_.getXRange(), dev_.getYRange()) / 30;  // user 3% of galvo/SLM range
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
         // require that the RMS value between the mapped points and the measured points be
         // less than 5% of image size
         Rectangle roi = core_.getROI();
         final long imageSize = Math.min(roi.width, roi.height);
         return MathFunctions
               .generateAffineTransformFromPointPairs(spotMap, imageSize * 0.05, Double.MAX_VALUE);
      } catch (Exception e) {
         throw new RuntimeException("Spots aren't detected as expected. "
               + "Is the Projector in focus and roughly centered in camera's field of view?");
      }
   }

   /**
    * Generate a nonlinear calibration mapping for the current device settings. A rectangular
    * lattice of points is illuminated one-by-one on the projection device, and locations in camera
    * pixels of corresponding spots on the camera image are recorded. For each rectangular cell in
    * the grid, we take the four point mappings (camera to projector) and generate a local
    * AffineTransform using linear least squares. Cells with suspect measured corner positions are
    * discarded. A mapping of cell polygon to AffineTransform is generated.
    */
   private Mapping generateNonlinearMapping() {

      // get the affine transform near the center spot
      final AffineTransform firstApproxAffine = generateLinearMapping();

      Rectangle roi;
      try {
         roi = core_.getROI();
      } catch (Exception ex) {
         app_.logs().showError(ex, "Cannot get camera ROI for calibration.");
         return null;
      }

      // then use this single transform to estimate what SLM coordinates
      // correspond to the image's corner positions
      final Point2D.Double camCorner1 =
            (Point2D.Double) firstApproxAffine.transform(
                  new Point2D.Double(0, 0), null);
      final Point2D.Double camCorner2 =
            (Point2D.Double) firstApproxAffine.transform(
                  new Point2D.Double(roi.width, roi.height), null);
      final Point2D.Double camCorner3 =
            (Point2D.Double) firstApproxAffine.transform(
                  new Point2D.Double(0, roi.height), null);
      final Point2D.Double camCorner4 =
            (Point2D.Double) firstApproxAffine.transform(
                  new Point2D.Double(roi.width, 0), null);

      // figure out camera's bounds in SLM coordinates
      // min/max because we don't know the relative orientation of the camera and SLM
      // do some extra checking in case camera/SLM aren't at exactly 90 degrees from each other, 
      // but still better that they are at 0, 90, 180, or 270 degrees from each other
      // TODO can create grid along camera location instead of SLM's if camera is the limiting
      //  factor; this will make arbitrary rotation possible
      final double camLeft = Math
            .min(Math.min(Math.min(camCorner1.x, camCorner2.x), camCorner3.x), camCorner4.x);
      final double camRight = Math
            .max(Math.max(Math.max(camCorner1.x, camCorner2.x), camCorner3.x), camCorner4.x);
      final double camTop = Math
            .min(Math.min(Math.min(camCorner1.y, camCorner2.y), camCorner3.y), camCorner4.y);
      final double camBottom = Math
            .max(Math.max(Math.max(camCorner1.y, camCorner2.y), camCorner3.y), camCorner4.y);

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

      // inset the grid away from the (potentially inaccurate) extrapolated edges
      final double marginX = width * EDGE_MARGIN_FRACTION;
      final double marginY = height * EDGE_MARGIN_FRACTION;
      final double gridLeft   = left   + marginX;
      final double gridTop    = top    + marginY;
      final double gridWidth  = width  - 2 * marginX;
      final double gridHeight = height - 2 * marginY;

      // compute a grid of SLM points inside the "overlap region"
      // nGrid is how many polygons in both X and Y
      // require (nGrid + 1)^2 spot measurements to get nGrid^2 squares
      final int nGrid = getCalibrationPoints(settings_) - 1;
      Point2D.Double[][] slmPoint = new Point2D.Double[1 + nGrid][1 + nGrid];
      Point2D.Double[][] camPoint = new Point2D.Double[1 + nGrid][1 + nGrid];

      // tabulate the camera spot at each of SLM grid points
      for (int i = 0; i <= nGrid; ++i) {
         for (int j = 0; j <= nGrid; ++j) {
            double xoffset = ((i + 0.5) * gridWidth  / (nGrid + 1.0));
            double yoffset = ((j + 0.5) * gridHeight / (nGrid + 1.0));
            slmPoint[i][j] = new Point2D.Double(gridLeft + xoffset, gridTop + yoffset);
            camPoint[i][j] = measureSpotOnCamera(slmPoint[i][j]);
         }
      }

      if (stopRequested_.get()) {
         return null;
      }

      lastGrid_ = new CalibrationGrid(slmPoint, camPoint);
      Map<Polygon, AffineTransform> bigMap = buildCellMap(lastGrid_,
            getCalibrationSmoothing(settings_), app_.logs());
      try {
         Mapping.Builder mb = new Mapping.Builder();
         // amazing that there is no API call for binning!
         String binningAsString = core_.getProperty(core_.getCameraDevice(), "Binning");
         // Hamamatsu reports 1x1.  I wish there was an api call for binning
         int binning = Integer.parseInt(binningAsString.substring(0, 1));
         mb.setMap(bigMap).setApproximateTransform(firstApproxAffine).setROI(roi)
               .setBinning(binning);
         return mb.build();
      } catch (Exception ex) {
         return null;
      }
   }

   /**
    * Smooths the measured grid (sigma in grid spacings, 0 = off), then builds one polygon cell
    * with a local AffineTransform per grid square. Cells with suspect corners are discarded.
    */
   static Map<Polygon, AffineTransform> buildCellMap(CalibrationGrid grid, double sigma,
         LogManager logs) {
      final Point2D.Double[][] slmPoint = grid.slm;
      final int nGrid = grid.size() - 1;
      CalibrationSmoother.Result smoothed = CalibrationSmoother.smooth(grid.cam, sigma);
      for (CalibrationSmoother.FlaggedSpot f : smoothed.flagged) {
         logs.logMessage(String.format(Locale.US,
               "Projector calibration spot (%d,%d) at camera (%.1f, %.1f) deviates %.2f px "
               + "from its neighbours; ignored.", f.i, f.j, f.measured.x, f.measured.y,
               f.deviation()));
      }
      final Point2D.Double[][] camPoint = smoothed.points;

      // now make a grid of (square) polygons (in camera's coordinate system)
      // and generate an affine transform for each of these square regions
      Map<Polygon, AffineTransform> bigMap = new HashMap<>();
      for (int i = 0; i <= nGrid - 1; ++i) {
         for (int j = 0; j <= nGrid - 1; ++j) {
            if (camPoint[i][j] == null || camPoint[i][j + 1] == null
                  || camPoint[i + 1][j] == null || camPoint[i + 1][j + 1] == null) {
               continue;
            }
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
            double srcDX = Math.abs((camPoint[i + 1][j].x - camPoint[i][j].x)) / 4;
            double srcDY = Math.abs((camPoint[i][j + 1].y - camPoint[i][j].y)) / 4;
            double srcTol = Math.max(srcDX, srcDY);

            try {
               AffineTransform transform = MathFunctions
                     .generateAffineTransformFromPointPairs(map, srcTol, Double.MAX_VALUE);
               bigMap.put(poly, transform);
            } catch (Exception e) {
               logs.logError("Bad cell in mapping.");
            }
         }
      }
      return bigMap;
   }

   /**
    * Rebuilds a mapping from its stored raw measurements with a different smoothing, keeping
    * the camera ROI, binning and approximate transform of the existing mapping, and applying
    * the given offset correction.
    */
   static Mapping rebuildMapping(Mapping existing, CalibrationGrid grid, double sigma,
         Point2D.Double offset, LogManager logs) {
      Mapping rebuilt = new Mapping.Builder()
            .setMap(buildCellMap(grid, sigma, logs))
            .setApproximateTransform(existing.getApproximateTransform())
            .setROI(existing.getCameraROI())
            .setBinning(existing.getBinning())
            .build();
      return shiftMapping(rebuilt, offset);
   }

   /**
    * Returns a mapping that targets camera position p - delta wherever the input mapping
    * targets p, so that spots landing at p + delta (systematic error delta, in camera pixels
    * at calibration binning) end up at p. Cell polygons are unchanged.
    */
   static Mapping shiftMapping(Mapping mapping, Point2D.Double delta) {
      if (delta.x == 0.0 && delta.y == 0.0) {
         return mapping;
      }
      Map<Polygon, AffineTransform> shifted = new HashMap<>();
      for (Map.Entry<Polygon, AffineTransform> e : mapping.getMap().entrySet()) {
         AffineTransform t = new AffineTransform(e.getValue());
         t.translate(-delta.x, -delta.y);
         shifted.put(e.getKey(), t);
      }
      return new Mapping.Builder()
            .setMap(shifted)
            .setApproximateTransform(mapping.getApproximateTransform())
            .setROI(mapping.getCameraROI())
            .setBinning(mapping.getBinning())
            .build();
   }

   /**
    * Runs the full calibration. First generates a linear mapping (a first approximation) and then
    * generates a second piece-wise "non-linear" mapping of affine transforms. Saves the mapping to
    * Java Preferences.
    *
    * @return true if successful, false if interrupted or otherwise fails
    */
   public Future<Boolean> runCalibration() {
      return executor_.submit(() -> {
         app_.live().setSuspended(true);
         if (!isRunning_.get()) {
            stopRequested_.set(false);
            useCentroid_ = settings_.getBoolean(Terms.USE_CENTROID, false);

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
               final Roi originalROI = IJ.getImage().getRoi();

               // do the heavy lifting of generating the local affine transform map
               Mapping mapping = generateNonlinearMapping();

               dev_.turnOff();
               try {
                  Thread.sleep(500);
               } catch (InterruptedException ex) {
                  app_.logs().logError(ex);
               }

               // save local affine transform map to preferences
               // TODO allow different mappings to be stored for different channels
               //  (e.g. objective magnification)
               if (!stopRequested_.get()) {
                  List<Image> snap = app_.live().snap(false);
                  snap.get(0).getHeight();
                  snap.get(0).getMetadata().getBinning();
                  MappingStorage.saveMapping(core_, dev_, settings_, mapping, lastGrid_,
                        new Point2D.Double(0, 0));
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
    * Test points lie between the calibration spots, expressed in current camera coordinates
    * and sorted by row, then column. Each calibration cell contributes its center, or, when
    * that gives fewer than MIN_CHECK_POINTS points, a k x k grid of sub-cell centers.
    */
   private static List<Point2D.Double> checkTargets(Mapping mapping, Rectangle roi,
         int binning) {
      // inverse of the ROI/binning correction in ProjectorActions.transformPoint
      final double factor = (double) binning / (double) mapping.getBinning();
      final Rectangle mappingRoi = mapping.getCameraROI();
      final int nCells = mapping.getMap().size();
      int k = 1;
      while (nCells > 0 && nCells * k * k < MIN_CHECK_POINTS) {
         k++;
      }
      List<Point2D.Double> targets = new ArrayList<>();
      for (Polygon poly : mapping.getMap().keySet()) {
         Point[] v = Utils.getVertices(poly);
         for (int a = 0; a < k; a++) {
            for (int b = 0; b < k; b++) {
               Point2D.Double c = k == 1
                     ? Utils.meanPosition2D(v)
                     : bilinear(v, (a + 0.5) / k, (b + 0.5) / k);
               double x = c.x / factor - roi.x + mappingRoi.x;
               double y = c.y / factor - roi.y + mappingRoi.y;
               if (x >= 0 && x < roi.width && y >= 0 && y < roi.height) {
                  targets.add(new Point2D.Double(x, y));
               }
            }
         }
      }
      return sortByRows(targets, roi.height / 40.0);
   }

   /**
    * Sorts points by row, then by x. A new row starts where the gap in y to the previous point
    * (in y order) exceeds rowTolerance; assigning rows first keeps the ordering transitive.
    */
   static List<Point2D.Double> sortByRows(List<Point2D.Double> points, double rowTolerance) {
      List<Point2D.Double> byY = new ArrayList<>(points);
      byY.sort((a, b) -> Double.compare(a.y, b.y));
      List<Point2D.Double> sorted = new ArrayList<>(byY.size());
      int start = 0;
      for (int k = 1; k <= byY.size(); k++) {
         if (k == byY.size() || byY.get(k).y - byY.get(k - 1).y > rowTolerance) {
            List<Point2D.Double> row = byY.subList(start, k);
            row.sort((a, b) -> Double.compare(a.x, b.x));
            sorted.addAll(row);
            start = k;
         }
      }
      return sorted;
   }

   /**
    * Bilinear interpolation in a calibration quad with vertices ordered (i,j), (i,j+1),
    * (i+1,j+1), (i+1,j); u runs along i, w along j.
    */
   private static Point2D.Double bilinear(Point[] v, double u, double w) {
      double x = (1 - u) * (1 - w) * v[0].x + (1 - u) * w * v[1].x
            + u * w * v[2].x + u * (1 - w) * v[3].x;
      double y = (1 - u) * (1 - w) * v[0].y + (1 - u) * w * v[1].y
            + u * w * v[2].y + u * (1 - w) * v[3].y;
      return new Point2D.Double(x, y);
   }

   private CalibrationCheckResult checkCalibration(Mapping mapping) throws Exception {
      final Rectangle roi = core_.getROI();
      final int binning = Utils.getBinning(core_);
      CalibrationCheckResult result = new CalibrationCheckResult(core_.getPixelSizeUm(),
            useCentroid_, mapping.getMap().size(),
            MappingStorage.loadOffset(core_, dev_, settings_.toPropertyMap()), binning);
      for (Point2D.Double target : checkTargets(mapping, roi, binning)) {
         if (stopRequested_.get()) {
            return null;
         }
         // transformPoint modifies its input
         Point2D.Double device = ProjectorActions.transformPoint(mapping,
               new Point2D.Double(target.x, target.y), roi, binning);
         result.add(target, device, measureSpotOnCamera(device));
      }
      return result;
   }

   /**
    * Checks an existing calibration: projects spots, using the given mapping, at the camera
    * positions between the calibration points and measures where they land.
    *
    * @return Future with the result, or null if stopped or failed
    */
   public Future<CalibrationCheckResult> runCalibrationCheck(Mapping mapping) {
      return executor_.submit(() -> {
         if (isRunning_.getAndSet(true)) {
            return null;
         }
         useCentroid_ = settings_.getBoolean(Terms.USE_CENTROID, false);
         app_.live().setSuspended(true);
         try {
            if (app_.live().getDisplay() == null) {
               app_.live().snap(true);
               Thread.sleep(1000);
            }
            final Roi originalROI = IJ.getImage().getRoi();
            CalibrationCheckResult result = checkCalibration(mapping);
            dev_.turnOff();
            IJ.getImage().setRoi(originalROI);
            return result;
         } catch (Exception e) {
            app_.logs().showError(e, "Calibration check failed.");
            return null;
         } finally {
            app_.live().setSuspended(false);
            isRunning_.set(false);
         }
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
