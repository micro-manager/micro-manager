///////////////////////////////////////////////////////////////////////////////
//FILE:          ProjectionControlForm.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Projector plugin
//-----------------------------------------------------------------------------
//AUTHOR:        Arthur Edelstein
//COPYRIGHT:     University of California, San Francisco, 2010-2014
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.projector;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.EllipseRoi;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultFormatter;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.DeviceType;
import mmcorej.TaggedImage;
import org.micromanager.api.ScriptInterface;
import org.micromanager.imageDisplay.VirtualAcquisitionDisplay;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MMListenerAdapter;
import org.micromanager.utils.MathFunctions;
import org.micromanager.utils.ReportingUtils;


public class ProjectorControlForm extends javax.swing.JFrame implements OnStateListener {
   private final ProjectionDevice dev;
   private final MouseListener pointAndShootMouseListener;
   private final Set<OnStateListener> listeners_ = new HashSet<OnStateListener>();
   private final AtomicBoolean pointAndShooteModeOn_ = new AtomicBoolean(false);
   private final CMMCore core_;
   private final ScriptInterface app_;
   private int numROIs_;
   private boolean isSLM_;
   private Roi[] individualRois_ = {};
   private int reps_ = 1;
   private Map<Polygon, AffineTransform> mapping_ = null;
   private String mappingNode_ = null;
   private String targetingChannel_;
   AtomicBoolean stopRequested_ = new AtomicBoolean(false);
   AtomicBoolean isRunning_ = new AtomicBoolean(false);
   private MosaicSequencingFrame mosaicSequencingFrame_;
   private String targetingShutter_;

   // ### ProjectorController constructor.


   // ### Simple point manipulation utility methods
   
   // Add a point to an existing polygon.
   private static void addVertex(Polygon poly, Point p) {
      poly.addPoint(p.x, p.y);
   }

   // Convert a Point with double values for x,y to a point
   // with x and y rounded to the nearest integer.
   private static Point toIntPoint(Point2D.Double pt) {
      return new Point((int) (0.5 + pt.x), (int) (0.5 + pt.y));
   }

   // Convert a Point with integer values to a Point with x and y doubles.
   private static Point2D.Double toDoublePoint(Point pt) {
      return new Point2D.Double(pt.x, pt.y);
   }
   
     
   // ### Methods for generating a calibration mapping.
   
   // Find the peak in an ImageProcessor. The image is first blurred
   // to avoid finding a peak in noise.
   private Point findPeak(ImageProcessor proc) {
      ImageProcessor blurImage = proc.duplicate();
      blurImage.setRoi((Roi) null);
      GaussianBlur blur = new GaussianBlur();
      blur.blurGaussian(blurImage, 10, 10, 0.01);
      //showProcessor("findPeak",proc);
      Point x = ImageUtils.findMaxPixel(blurImage);
      x.translate(1, 1);
      return x;
   }
   
   // Display a spot using the projection device, and return its current
   // location on the camera.
   private Point measureSpot(Point projectionPoint) {
      if (stopRequested_.get()) {
         return null;
      }

      try {
         dev.turnOff();
         Thread.sleep(300);
         core_.snapImage();
         TaggedImage image = core_.getTaggedImage();
         ImageProcessor proc1 = ImageUtils.makeMonochromeProcessor(image);
         long originalExposure = dev.getExposure();
         dev.setExposure(500000);
         displaySpot(projectionPoint.x, projectionPoint.y);
         Thread.sleep(300);
         dev.setExposure(500000);
         core_.snapImage();
         TaggedImage taggedImage2 = core_.getTaggedImage();
         ImageProcessor proc2 = ImageUtils.makeMonochromeProcessor(taggedImage2);
         app_.displayImage(taggedImage2);

         ImageProcessor diffImage = ImageUtils.subtractImageProcessors(proc2.convertToFloatProcessor(), proc1.convertToFloatProcessor());
         Point peak = findPeak(diffImage);
         Point maxPt = peak;
         IJ.getImage().setRoi(new PointRoi(maxPt.x, maxPt.y));
         core_.sleep(500);
         return maxPt;
      } catch (Exception e) {
         ReportingUtils.showError(e);
         return null;
      }
   }

   // Illuminate a spot at ptSLM, measure its location on the camera, and
   // add the resulting point pair to the spotMap.
   private void mapSpot(Map<Point2D.Double, Point2D.Double> spotMap,
         Point ptSLM) {
      Point2D.Double ptSLMDouble = new Point2D.Double(ptSLM.x, ptSLM.y);
      Point ptCam = measureSpot(ptSLM);
      Point2D.Double ptCamDouble = new Point2D.Double(ptCam.x, ptCam.y);
      spotMap.put(ptCamDouble, ptSLMDouble);
   }

   // Illuminate a spot at ptSLM, measure its location on the camera, and
   // add the resulting point pair to the spotMap.
   private void mapSpot(Map<Point2D.Double, Point2D.Double> spotMap,
         Point2D.Double ptSLM) {
      if (!stopRequested_.get()) {
         mapSpot(spotMap, new Point((int) ptSLM.x, (int) ptSLM.y));
      }
   }

   
   private AffineTransform getFirstApproxTransform() {
      double x = dev.getWidth() / 2;
      double y = dev.getHeight() / 2;

      int s = 50;
      Map<Point2D.Double, Point2D.Double> spotMap
            = new HashMap<Point2D.Double, Point2D.Double>();

      mapSpot(spotMap, new Point2D.Double(x, y));
      mapSpot(spotMap, new Point2D.Double(x, y + s));
      mapSpot(spotMap, new Point2D.Double(x + s, y));
      mapSpot(spotMap, new Point2D.Double(x, y - s));
      mapSpot(spotMap, new Point2D.Double(x - s, y));
      if (stopRequested_.get()) {
         return null;
      }
      return MathFunctions.generateAffineTransformFromPointPairs(spotMap);
   }

   // Generate a calibration mapping for the current device settings.
   // A rectangular lattice of points is illuminated one-by-one on the
   // projection device, and locations in camera pixels of corresponding
   // spots on the camera image are recorded. For each rectangular
   // cell in the grid, we take the four point mappings (camera to projector)
   // and generate a local AffineTransform using linear least squares.
   // Cells with suspect measured corner positions are discarded.
   // A mapping of cell polygon to AffineTransform is generated. 
   private Map<Polygon, AffineTransform> getMapping(AffineTransform firstApprox) {
      if (firstApprox == null) {
         return null;
      }
      int devWidth = (int) dev.getWidth() - 1;
      int devHeight = (int) dev.getHeight() - 1;
      Point2D.Double camCorner1 = (Point2D.Double) firstApprox.transform(new Point2D.Double(0, 0), null);
      Point2D.Double camCorner2 = (Point2D.Double) firstApprox.transform(new Point2D.Double((int) core_.getImageWidth(), (int) core_.getImageHeight()), null);
      int camLeft = Math.min((int) camCorner1.x, (int) camCorner2.x);
      int camRight = Math.max((int) camCorner1.x, (int) camCorner2.x);
      int camTop = Math.min((int) camCorner1.y, (int) camCorner2.y);
      int camBottom = Math.max((int) camCorner1.y, (int) camCorner2.y);
      int left = Math.max(camLeft, 0);
      int right = Math.min(camRight, devWidth);
      int top = Math.max(camTop, 0);
      int bottom = Math.min(camBottom, devHeight);
      int width = right - left;
      int height = bottom - top;

      int n = 7;
      Point2D.Double dmdPoint[][] = new Point2D.Double[1 + n][1 + n];
      Point2D.Double resultPoint[][] = new Point2D.Double[1 + n][1 + n];
      for (int i = 0; i <= n; ++i) {
         for (int j = 0; j <= n; ++j) {
            int xoffset = (int) ((i + 0.5) * width / (n + 1.0));
            int yoffset = (int) ((j + 0.5) * height / (n + 1.0));
            dmdPoint[i][j] = new Point2D.Double(left + xoffset, top + yoffset);
            Point spot = measureSpot(toIntPoint(dmdPoint[i][j]));
            if (spot != null) {
               resultPoint[i][j] = toDoublePoint(spot);
            }
         }
      }

      if (stopRequested_.get()) {
         return null;
      }

      Map<Polygon, AffineTransform> bigMap
            = new HashMap<Polygon, AffineTransform>();
      for (int i = 0; i <= n - 1; ++i) {
         for (int j = 0; j <= n - 1; ++j) {
            Polygon poly = new Polygon();
            addVertex(poly, toIntPoint(resultPoint[i][j]));
            addVertex(poly, toIntPoint(resultPoint[i][j + 1]));
            addVertex(poly, toIntPoint(resultPoint[i + 1][j + 1]));
            addVertex(poly, toIntPoint(resultPoint[i + 1][j]));

            Map<Point2D.Double, Point2D.Double> map
                  = new HashMap<Point2D.Double, Point2D.Double>();
            map.put(resultPoint[i][j], dmdPoint[i][j]);
            map.put(resultPoint[i][j + 1], dmdPoint[i][j + 1]);
            map.put(resultPoint[i + 1][j], dmdPoint[i + 1][j]);
            map.put(resultPoint[i + 1][j + 1], dmdPoint[i + 1][j + 1]);
            double srcDX = Math.abs((resultPoint[i+1][j].x - resultPoint[i][j].x))/4; 
            double srcDY = Math.abs((resultPoint[i][j+1].y - resultPoint[i][j].y))/4;
            double srcTol = Math.max(srcDX, srcDY);

            try {
               AffineTransform transform = MathFunctions.generateAffineTransformFromPointPairs(map, srcTol, Double.MAX_VALUE);
               bigMap.put(poly, transform);
            } catch (Exception e) {
               ReportingUtils.logError("Bad cell in mapping.");
            }
         }
      }
      return bigMap;
   }
   
   
   // ### Loading and saving calibration mappings to java preferences.
   
   // Returns the java Preferences node where we store the Calibration mapping.
   // Each channel/camera combination is assigned a different node.
   private Preferences getCalibrationNode() {
      return Preferences.userNodeForPackage(ProjectorPlugin.class)
            .node("calibration")
            .node(dev.getChannel())
            .node(core_.getCameraDevice());
   }
   
   // Load the mapping for the current calibration node. The mapping
   // maps each polygon cell to an AffineTransform.
   private Map<Polygon, AffineTransform> loadMapping() {
      String nodeStr = getCalibrationNode().toString();
      if (mappingNode_ == null || !nodeStr.contentEquals(mappingNode_)) {
         mappingNode_ = nodeStr;
         mapping_ = (Map<Polygon, AffineTransform>) JavaUtils.getObjectFromPrefs(
                 getCalibrationNode(), 
                 dev.getName(), 
                 new HashMap<Polygon, AffineTransform>());
      }
      return  mapping_;
   }

   // Save the mapping for the current calibration node. The mapping
   // maps each polygon cell to an AffineTransform.
   private void saveMapping(HashMap<Polygon, AffineTransform> mapping) {
      JavaUtils.putObjectInPrefs(getCalibrationNode(), dev.getName(), mapping);
      mapping_ = mapping;
      mappingNode_ = getCalibrationNode().toString();
   }

   // Runs the full calibration, and saves it to Java Preferences.
   public void calibrate() {
      final boolean liveModeRunning = app_.isLiveModeOn();
      app_.enableLiveMode(false);
      if (!isRunning_.get()) {
         this.stopRequested_.set(false);
         Thread th = new Thread("Projector calibration thread") {
            @Override
            public void run() {
               try {
                  isRunning_.set(true);
                  Roi originalROI = IJ.getImage().getRoi();
                  app_.snapSingleImage();

                  AffineTransform firstApproxAffine = getFirstApproxTransform();

                  HashMap<Polygon, AffineTransform> mapping = (HashMap<Polygon, AffineTransform>) getMapping(firstApproxAffine);
                  //LocalWeightedMean lwm = multipleAffineTransforms(mapping_);
                  //AffineTransform affineTransform = MathFunctions.generateAffineTransformFromPointPairs(mapping_);
                  dev.turnOff();
                  try {
                     Thread.sleep(500);
                  } catch (InterruptedException ex) {
                     ReportingUtils.logError(ex);
                  }
                  if (!stopRequested_.get()) {
                     saveMapping(mapping);
                  }
                  //saveAffineTransform(affineTransform);
                  app_.enableLiveMode(liveModeRunning);
                  JOptionPane.showMessageDialog(IJ.getImage().getWindow(), "Calibration "
                        + (!stopRequested_.get() ? "finished." : "canceled."));
                  IJ.getImage().setRoi(originalROI);

                  for (OnStateListener listener : listeners_) {
                     listener.calibrationDone();
                  }
               } catch (Exception e) {
                  ReportingUtils.showError(e);
               } finally {
                  isRunning_.set(false);
                  stopRequested_.set(false);
               }
            }
         };
         th.start();
      }
   }
   
   // ### Transforming points according to a calibration mapping.
   
   // Roughly gets the center of a Polygon, given that it is more or less
   // a rectangle.
   private static Point2D.Double getApproximateCenter(Polygon polygon) {
      int n = polygon.npoints;
      double xsum = 0;
      double ysum = 0;
      for (int i = 0; i < n; ++i) {
         xsum += polygon.xpoints[i];
         ysum += polygon.ypoints[i];
      }
      return new Point2D.Double(xsum/n, ysum/n);
   }
   
   // Transform a point, pt, given the mapping, which is a Map of polygon cells
   // to AffineTransforms.
   private static Point transform(Map<Polygon, AffineTransform> mapping, Point pt) {
      Set<Polygon> set = mapping.keySet();
      // First find out if the given point is inside a cell, and if so,
      // transform it with that cell's AffineTransform.
      for (Polygon poly : set) {
         if (poly.contains(pt)) {
            return toIntPoint((Point2D.Double) mapping.get(poly).transform(toDoublePoint(pt), null));
         }
      }
      // The point isn't inside any cell, so search for the closest cell
      // and use the AffineTransform from that.
      double minDistance = Double.MAX_VALUE;
      Polygon bestPoly = null;
      for (Polygon poly : set) {
         double distance = getApproximateCenter(poly).distance(pt.x, pt.y);
         if (minDistance > distance) {
            bestPoly = poly;
            minDistance = distance;
         }
      }
      if (bestPoly == null) {
         throw new RuntimeException("Unable to map point to device.");
      }
      return toIntPoint((Point2D.Double) mapping.get(bestPoly).transform(toDoublePoint(pt), null));
   }
   
   // Returns true if a particular image is mirrored.
   private static boolean isMirrored(ImagePlus imgp) {
      try {
         String mirrorString = VirtualAcquisitionDisplay.getDisplay(imgp)
               .getCurrentMetadata().getString("ImageFlipper-Mirror");
         return (mirrorString.contentEquals("On"));
      } catch (Exception e) {
         return false;
      }
   }

   // Flips a point if it has been mirrored.
   private static Point mirrorIfNecessary(Point pOffscreen, ImagePlus imgp) {
      if (isMirrored(imgp)) {
         return new Point(imgp.getWidth() - pOffscreen.x, pOffscreen.y);
      } else {
         return pOffscreen;
      }
   }
   
   
   private static Point transformAndFlip(Map<Polygon, AffineTransform> mapping, ImagePlus imgp, Point pt) {
      Point pOffscreen = mirrorIfNecessary(pt, imgp);
      return transform(mapping, pOffscreen);
   }
   
   // Converts an ROI to a Polygon.
   private static Polygon asPolygon(Roi roi) {
      if ((roi.getType() == Roi.POINT)
               || (roi.getType() == Roi.FREEROI)
               || (roi.getType() == Roi.POLYGON)
            || (roi.getType() == Roi.RECTANGLE)) {
         return roi.getPolygon();
      } else if (roi.getType() == Roi.OVAL) {
         Rectangle bounds = roi.getBounds();
         double aspectRatio = bounds.width / (double) bounds.height;
         if (aspectRatio < 1) {
            return new EllipseRoi(bounds.x + bounds.width / 2,
                  bounds.y,
                  bounds.x + bounds.width / 2,
                  bounds.y + bounds.height,
                  aspectRatio).getPolygon();
         } else {
            return new EllipseRoi(bounds.x,
                  bounds.y + bounds.height / 2,
                  bounds.x + bounds.width,
                  bounds.y + bounds.height / 2,
                  1 / aspectRatio).getPolygon();
         }
      } else {
         throw new RuntimeException("Can't use this type of ROI.");
      }
   }

   private static List<Polygon> transformROIs(ImagePlus imgp, Roi[] rois, Map<Polygon, AffineTransform> mapping) {
      ArrayList<Polygon> transformedROIs = new ArrayList<Polygon>();
      for (Roi roi : rois) {
         Polygon poly = asPolygon(roi);
         Polygon newPoly = new Polygon();
         try {
            Point2D galvoPoint;
            for (int i = 0; i < poly.npoints; ++i) {
               Point imagePoint = new Point(poly.xpoints[i], poly.ypoints[i]);
               galvoPoint = transformAndFlip(mapping, imgp, imagePoint);
               if (galvoPoint == null) {
                  throw new Exception();
               }
               newPoly.addPoint((int) galvoPoint.getX(), (int) galvoPoint.getY());
            }
            transformedROIs.add(newPoly);
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
            break;
         }
      }
      return transformedROIs;
   }

      /* Returns the currently selected ROIs for a given ImageWindow. */
   public static Roi[] getRois(ImageWindow window, boolean selectedOnly) {
      Roi[] rois = new Roi[]{};
      Roi[] roiMgrRois = {};
      Roi singleRoi = window.getImagePlus().getRoi();
      final RoiManager mgr = RoiManager.getInstance();
      if (mgr != null) {
         if (selectedOnly) {
            roiMgrRois = mgr.getSelectedRoisAsArray();
            if (roiMgrRois.length == 0) {
               roiMgrRois = mgr.getRoisAsArray();
            }
         } else {
            roiMgrRois = mgr.getRoisAsArray();
         }
      }
      if (roiMgrRois.length > 0) {
         rois = roiMgrRois;
      } else if (singleRoi != null) {
         rois = new Roi[]{singleRoi};
      }
      return rois;
   }
      
   public List<Polygon> getTransformedRois(ImagePlus contextImagePlus, Roi[] rois) {
      return transformROIs(contextImagePlus, rois, mapping_);
   }

   public static Roi[] separateOutPointRois(Roi[] rois) {
      List<Roi> roiList = new ArrayList<Roi>();
      for (Roi roi : rois) {
         if (roi.getType() == Roi.POINT) {
            Polygon poly = ((PointRoi) roi).getPolygon();
            for (int i = 0; i < poly.npoints; ++i) {
               roiList.add(new PointRoi(
                     poly.xpoints[i],
                     poly.ypoints[i]));
            }
         } else {
            roiList.add(roi);
         }
      }
      return roiList.toArray(rois);
   }

   public int setRois(int reps) {
      if (mapping_ != null) {
         ImageWindow window = WindowManager.getCurrentWindow();
         if (window == null) {
            ReportingUtils.showError("No image window with ROIs is open.");
            return 0;
         } else {
            ImagePlus imgp = window.getImagePlus();
            Roi[] rois = getRois(window, true);
            if (rois.length == 0) {
               ReportingUtils.showMessage("Please first draw the desired phototargeting ROIs.");
            }
            individualRois_ = separateOutPointRois(rois);
            sendRoiData(imgp);
            return individualRois_.length;
         }
      } else {
         return 0;
      }
   }

   private void sendRoiData(ImagePlus imgp) {
      if (individualRois_.length > 0) {
         if (mapping_ != null) {
            List<Polygon> rois = transformROIs(imgp, individualRois_, mapping_);
            dev.loadRois(rois);
            dev.setPolygonRepetitions(reps_);
         }
      }
   }

   public void setRoiRepetitions(int reps) {
      reps_ = reps;
      sendRoiData(IJ.getImage());
   }

   private void displaySpot(double x, double y) {
      if (x >= 0 && x < dev.getWidth() && y >= 0 && y < dev.getHeight()) {
         dev.displaySpot(x, y);
      }
   }

   public Configuration prepareChannel() {
      Configuration originalConfig = null;
      String channelGroup = core_.getChannelGroup();
      try {
         if (targetingChannel_.length() > 0) {
            originalConfig = core_.getConfigGroupState(channelGroup);
            if (!originalConfig.isConfigurationIncluded(core_.getConfigData(channelGroup, targetingChannel_))) {
               if (app_.isAcquisitionRunning()) {
                  app_.setPause(true);
               }
               core_.setConfig(channelGroup, targetingChannel_);
            }
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      return originalConfig;
   }
   
   // Should be called with the value returned by prepareChannel.
   public void returnChannel(Configuration originalConfig) {
      if (originalConfig != null) {
         try {
            core_.setSystemState(originalConfig);
            if (app_.isAcquisitionRunning() && app_.isPaused()) {
               app_.setPause(false);
            }
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
   }
   
   public boolean prepareShutter() {
      try {
         if (targetingShutter_ != null && targetingShutter_.length() > 0) {
            boolean originallyOpen = core_.getShutterOpen(targetingShutter_);
            if (!originallyOpen) {
               core_.setShutterOpen(targetingShutter_, true);
               core_.waitForDevice(targetingShutter_);
            }
            return originallyOpen;
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      return true; // by default, say it was already open
   }

   // Should be called with the value returned by prepareShutter.
   public void returnShutter(boolean originallyOpen) {
      try {
         if (targetingShutter_ != null &&
               (targetingShutter_.length() > 0) &&
               !originallyOpen) {
            core_.setShutterOpen(targetingShutter_, false);
            core_.waitForDevice(targetingShutter_);
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   public final MouseListener setupPointAndShootMouseListener() {
      return new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            if (e.isControlDown()) {
               Point p = e.getPoint();
               ImageCanvas canvas = (ImageCanvas) e.getSource();
               Point pOffscreen = new Point(canvas.offScreenX(p.x), canvas.offScreenY(p.y));
               Point devP = transformAndFlip(loadMapping(), canvas.getImage(), 
                       new Point(pOffscreen.x, pOffscreen.y));
               Configuration originalConfig = prepareChannel();
               boolean originalShutterState = prepareShutter();
               if (devP != null) {
                  displaySpot(devP.x, devP.y);
               }
               returnShutter(originalShutterState);
               returnChannel(originalConfig);
            }
         }
      };
   }

   public void enablePointAndShootMode(boolean on) {
      pointAndShooteModeOn_.set(on);
      ImageWindow window = WindowManager.getCurrentWindow();
      if (window != null) {
         ImageCanvas canvas = window.getCanvas();
         if (on) {
            if (canvas != null) {
               boolean found = false;
               for (MouseListener listener : canvas.getMouseListeners()) {
                  if (listener == pointAndShootMouseListener) {
                     found = true;
                  }
               }
               if (!found) {
                  canvas.addMouseListener(pointAndShootMouseListener);
               }
            }
         } else {
            for (MouseListener listener : canvas.getMouseListeners()) {
               if (listener == pointAndShootMouseListener) {
                  canvas.removeMouseListener(listener);
               }
            }
         }
      }
   }



   void runPolygons() {
      Configuration originalConfig = prepareChannel();
      boolean originalShutterState = prepareShutter();
      dev.runPolygons();
      returnShutter(originalShutterState);
      returnChannel(originalConfig);
      recordPolygons();
   }

   void addOnStateListener(OnStateListener listener) {
      dev.addOnStateListener(listener);
      listeners_.add(listener);
   }

   void moveToCenter() {
      double x = dev.getWidth() / 2;
      double y = dev.getHeight() / 2;
      dev.displaySpot(x, y);
   }

   void setTargetingChannel(Object selectedItem) {
      targetingChannel_ = (String) selectedItem;
   }
   
   void setTargetingShutter(Object selectedItem) {
      targetingShutter_ = (String) selectedItem;
   }

   private void saveROIs(File path) {
      try {
         ImagePlus imgp = IJ.getImage();
         ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path));
         DataOutputStream out = new DataOutputStream(new BufferedOutputStream(zos));
         RoiEncoder re = new RoiEncoder(out);
         for (Roi roi : individualRois_) {
            String label = getROILabel(imgp, roi, 0);
            if (!label.endsWith(".roi")) {
               label += ".roi";
            }
            zos.putNextEntry(new ZipEntry(label));
            re.write(roi);
            out.flush();
         }
         out.close();
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   
   private void recordPolygons() {
      if (app_.isAcquisitionRunning()) {
         String location = app_.getAcquisitionPath();
         if (location != null) {
            try {
               File f = new File(location, "ProjectorROIs.zip");
               if (!f.exists()) {
                  saveROIs(f);
               }
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
         }
      }
   }

   private String getROILabel(ImagePlus imp, Roi roi, int n) {
      Rectangle r = roi.getBounds();
      int xc = r.x + r.width / 2;
      int yc = r.y + r.height / 2;
      if (n >= 0) {
         xc = yc;
         yc = n;
      }
      if (xc < 0) {
         xc = 0;
      }
      if (yc < 0) {
         yc = 0;
      }
      int digits = 4;
      String xs = "" + xc;
      if (xs.length() > digits) {
         digits = xs.length();
      }
      String ys = "" + yc;
      if (ys.length() > digits) {
         digits = ys.length();
      }
      if (digits == 4 && imp != null && imp.getStackSize() >= 10000) {
         digits = 5;
      }
      xs = "000000" + xc;
      ys = "000000" + yc;
      String label = ys.substring(ys.length() - digits) + "-" + xs.substring(xs.length() - digits);
      if (imp != null && imp.getStackSize() > 1) {
         int slice = roi.getPosition();
         if (slice == 0) {
            slice = imp.getCurrentSlice();
         }
         String zs = "000000" + slice;
         label = zs.substring(zs.length() - digits) + "-" + label;
         roi.setPosition(slice);
      }
      return label;
   }


  // ### Public methods for interfacing with the UI.
   
   // Returns true if the device we are controlling is an SLM (not galvo-based).
   public  boolean isSLM() {
      return (dev instanceof SLM);
   }
   
   // Turn the projection device off.
   public void turnOff() {
      dev.turnOff();
   }

   // Turn the projection device on.
   public void turnOn() {
      dev.turnOn();
   }

   // Activate all pixels in an SLM.
   void activateAllPixels() {
      dev.activateAllPixels();
   }
   
   // Returns true if the calibration is currently running.
   public boolean isCalibrating() {
      return isRunning_.get();
   }

   // Requests an interruption to calibration while it is running.
   public void stopCalibration() {
      stopRequested_.set(true);
   }

   public void attachToMDA(int frameOn, boolean repeat, int repeatInterval) {
      Runnable runPolygons = new Runnable() {
         public void run() {
            runPolygons();
         }

         @Override
         public String toString() {
            return "Phototargeting of ROIs";
         }
      };

      app_.clearRunnables();
      if (repeat) {
         for (int i = frameOn; i < app_.getAcquisitionSettings().numFrames * 10; i += repeatInterval) {
            app_.attachRunnable(i, -1, 0, 0, runPolygons);
         }
      } else {
         app_.attachRunnable(frameOn, -1, 0, 0, runPolygons);
      }
   }

   public void removeFromMDA() {
      app_.clearRunnables();
   }

   public void setExposure(double intervalUs) {
      long previousExposure = dev.getExposure();
      long newExposure = (long) intervalUs;
      if (previousExposure != newExposure) {
         dev.setExposure((long) newExposure);
      }
   }

   public double getPointAndShootInterval() {
      return dev.getExposure();
   }

   void showMosaicSequencingFrame() {
      if (mosaicSequencingFrame_ == null) {
         mosaicSequencingFrame_ = new MosaicSequencingFrame(app_, core_, this, (SLM) dev);
      }
      mosaicSequencingFrame_.setVisible(true);
   }

   String getDeviceName() {
      return dev.getName();
   }

   /**
    * Creates new form ProjectorControlForm
    */
   public ProjectorControlForm(CMMCore core, ScriptInterface app) {
      initComponents();
      app_ = app;
      core_ = app.getMMCore();
      String slm = core_.getSLMDevice();
      String galvo = core_.getGalvoDevice();

      if (slm.length() > 0) {
         dev = new SLM(core_, 20);
      } else if (galvo.length() > 0) {
         dev = new Galvo(core_);
      } else {
         dev = null;
      }
     
      loadMapping();
      pointAndShootMouseListener = setupPointAndShootMouseListener();

      Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
         @Override
         public void eventDispatched(AWTEvent e) {
            ProjectorControlForm.this.enablePointAndShootMode(ProjectorControlForm.this.pointAndShooteModeOn_.get());
         }
      }, AWTEvent.WINDOW_EVENT_MASK);
      
      // Place window where it was last.
      GUIUtils.recallPosition(this);
      isSLM_ = isSLM();
      // Only an SLM (not a galvo) has pixels.
      allPixelsButton.setVisible(isSLM_);
      // No point in looping ROIs on an SLM.
      roiLoopSpinner.setVisible(!isSLM_);
      roiLoopLabel.setVisible(!isSLM_);
      roiLoopTimesLabel.setVisible(!isSLM_);
      pointAndShootOffButton.setSelected(true);
      updateROISettings();
      populateChannelComboBox(Preferences.userNodeForPackage(this.getClass()).get("channel", ""));
      populateShutterComboBox(Preferences.userNodeForPackage(this.getClass()).get("shutter", ""));
      this.addWindowFocusListener(new WindowAdapter() {
          public void windowGainedFocus(WindowEvent e) {
              populateChannelComboBox(null);
              populateShutterComboBox(null);
          }
      });
      
      commitSpinnerOnValidEdit(pointAndShootIntervalSpinner);
      commitSpinnerOnValidEdit(startFrameSpinner);
      commitSpinnerOnValidEdit(repeatEveryFrameSpinner);
      commitSpinnerOnValidEdit(roiLoopSpinner);
      
      sequencingButton.setVisible(MosaicSequencingFrame.getMosaicDevices(core).size() > 0);
     
      app_.addMMListener(new MMListenerAdapter() {
         @Override
         public void slmExposureChanged(String deviceName, double exposure) {
            if (deviceName.equals(getDeviceName())) {
               pointAndShootIntervalSpinner.setValue(exposure * 1000);
            }
         }
      });

   }

   /*
    * Makes a JSpinner fire a change event reflecting the new value
    * whenver user types a valid entry. Why the hell isn't that the default setting?
    */
   private static void commitSpinnerOnValidEdit(final JSpinner spinner) {
      ((DefaultFormatter) ((JSpinner.DefaultEditor) spinner.getEditor())
            .getTextField().getFormatter()).setCommitsOnValidEdit(true);
   }
   
   /**
    * This method is called from within the constructor to initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is always
    * regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      onButton = new javax.swing.JButton();
      mainTabbedPane = new javax.swing.JTabbedPane();
      pointAndShootTab = new javax.swing.JPanel();
      jLabel1 = new javax.swing.JLabel();
      pointAndShootOnButton = new javax.swing.JToggleButton();
      pointAndShootOffButton = new javax.swing.JToggleButton();
      jLabel3 = new javax.swing.JLabel();
      roisTab = new javax.swing.JPanel();
      roiLoopLabel = new javax.swing.JLabel();
      roiLoopTimesLabel = new javax.swing.JLabel();
      setRoiButton = new javax.swing.JButton();
      runROIsNowButton = new javax.swing.JButton();
      roiLoopSpinner = new javax.swing.JSpinner();
      repeatCheckBox = new javax.swing.JCheckBox();
      startFrameLabel = new javax.swing.JLabel();
      startFrameSpinner = new javax.swing.JSpinner();
      repeatEveryFrameSpinner = new javax.swing.JSpinner();
      framesLabel = new javax.swing.JLabel();
      jSeparator1 = new javax.swing.JSeparator();
      useInMDAcheckBox = new javax.swing.JCheckBox();
      roiStatusLabel = new javax.swing.JLabel();
      jButton1 = new javax.swing.JButton();
      jSeparator3 = new javax.swing.JSeparator();
      sequencingButton = new javax.swing.JButton();
      setupTab = new javax.swing.JPanel();
      calibrateButton = new javax.swing.JButton();
      allPixelsButton = new javax.swing.JButton();
      centerButton = new javax.swing.JButton();
      channelComboBox = new javax.swing.JComboBox();
      jLabel4 = new javax.swing.JLabel();
      shutterComboBox = new javax.swing.JComboBox();
      jLabel5 = new javax.swing.JLabel();
      offButton = new javax.swing.JButton();
      closeShutterLabel = new javax.swing.JLabel();
      pointAndShootIntervalSpinner = new javax.swing.JSpinner();
      jLabel2 = new javax.swing.JLabel();

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("Projector Controls");
      setResizable(false);

      onButton.setText("On");
      onButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            onButtonActionPerformed(evt);
         }
      });

      mainTabbedPane.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            mainTabbedPaneStateChanged(evt);
         }
      });

      jLabel1.setText("Point and shoot mode:");

      pointAndShootOnButton.setText("On");
      pointAndShootOnButton.setMaximumSize(new java.awt.Dimension(75, 23));
      pointAndShootOnButton.setMinimumSize(new java.awt.Dimension(75, 23));
      pointAndShootOnButton.setPreferredSize(new java.awt.Dimension(75, 23));
      pointAndShootOnButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            pointAndShootOnButtonActionPerformed(evt);
         }
      });

      pointAndShootOffButton.setText("Off");
      pointAndShootOffButton.setPreferredSize(new java.awt.Dimension(75, 23));
      pointAndShootOffButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            pointAndShootOffButtonActionPerformed(evt);
         }
      });

      jLabel3.setText("(To phototarget, Control + click on the image.)");

      org.jdesktop.layout.GroupLayout pointAndShootTabLayout = new org.jdesktop.layout.GroupLayout(pointAndShootTab);
      pointAndShootTab.setLayout(pointAndShootTabLayout);
      pointAndShootTabLayout.setHorizontalGroup(
         pointAndShootTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(pointAndShootTabLayout.createSequentialGroup()
            .add(25, 25, 25)
            .add(pointAndShootTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(pointAndShootTabLayout.createSequentialGroup()
                  .add(jLabel1)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                  .add(pointAndShootOnButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                  .add(pointAndShootOffButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
               .add(jLabel3))
            .addContainerGap(179, Short.MAX_VALUE))
      );
      pointAndShootTabLayout.setVerticalGroup(
         pointAndShootTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(pointAndShootTabLayout.createSequentialGroup()
            .add(43, 43, 43)
            .add(pointAndShootTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(jLabel1)
               .add(pointAndShootOnButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(pointAndShootOffButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .add(18, 18, 18)
            .add(jLabel3)
            .addContainerGap(140, Short.MAX_VALUE))
      );

      mainTabbedPane.addTab("Point and Shoot", pointAndShootTab);

      roiLoopLabel.setText("Loop:");

      roiLoopTimesLabel.setText("times");

      setRoiButton.setText("Set ROI(s)");
      setRoiButton.setToolTipText("Specify an ROI you wish to be phototargeted by using the ImageJ ROI tools (point, rectangle, oval, polygon). Then press Set ROI(s) to send the ROIs to the phototargeting device. To initiate phototargeting, press Go!");
      setRoiButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            setRoiButtonActionPerformed(evt);
         }
      });

      runROIsNowButton.setText("Run ROIs now!");
      runROIsNowButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            runROIsNowButtonActionPerformed(evt);
         }
      });

      roiLoopSpinner.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      roiLoopSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            roiLoopSpinnerStateChanged(evt);
         }
      });

      repeatCheckBox.setText("Repeat every");
      repeatCheckBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            repeatCheckBoxActionPerformed(evt);
         }
      });

      startFrameLabel.setText("Start Frame");

      startFrameSpinner.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      startFrameSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            startFrameSpinnerStateChanged(evt);
         }
      });

      repeatEveryFrameSpinner.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      repeatEveryFrameSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            repeatEveryFrameSpinnerStateChanged(evt);
         }
      });

      framesLabel.setText("frames");

      useInMDAcheckBox.setText("Run ROIs in Multi-Dimensional Acquisition");
      useInMDAcheckBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            useInMDAcheckBoxActionPerformed(evt);
         }
      });

      roiStatusLabel.setText("No ROIs submitted yet");

      jButton1.setText("ROI Manager >>");
      jButton1.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            jButton1ActionPerformed(evt);
         }
      });

      jSeparator3.setOrientation(javax.swing.SwingConstants.VERTICAL);

      sequencingButton.setText("Sequencing...");
      sequencingButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            sequencingButtonActionPerformed(evt);
         }
      });

      org.jdesktop.layout.GroupLayout roisTabLayout = new org.jdesktop.layout.GroupLayout(roisTab);
      roisTab.setLayout(roisTabLayout);
      roisTabLayout.setHorizontalGroup(
         roisTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(roisTabLayout.createSequentialGroup()
            .add(25, 25, 25)
            .add(roisTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(roisTabLayout.createSequentialGroup()
                  .add(roiStatusLabel)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .add(sequencingButton))
               .add(roisTabLayout.createSequentialGroup()
                  .add(setRoiButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 108, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .add(jButton1)))
            .add(24, 24, 24))
         .add(roisTabLayout.createSequentialGroup()
            .addContainerGap()
            .add(roisTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(roisTabLayout.createSequentialGroup()
                  .add(10, 10, 10)
                  .add(runROIsNowButton)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                  .add(jSeparator3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .add(15, 15, 15)
                  .add(roisTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                     .add(roisTabLayout.createSequentialGroup()
                        .add(29, 29, 29)
                        .add(roisTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                           .add(roisTabLayout.createSequentialGroup()
                              .add(startFrameLabel)
                              .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                              .add(startFrameSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 46, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                           .add(roisTabLayout.createSequentialGroup()
                              .add(repeatCheckBox)
                              .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                              .add(repeatEveryFrameSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 50, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                              .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                              .add(framesLabel))))
                     .add(useInMDAcheckBox))
                  .add(0, 0, Short.MAX_VALUE))
               .add(roisTabLayout.createSequentialGroup()
                  .add(jSeparator1)
                  .addContainerGap())
               .add(roisTabLayout.createSequentialGroup()
                  .add(15, 15, 15)
                  .add(roiLoopLabel)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                  .add(roiLoopSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 36, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                  .add(roiLoopTimesLabel)
                  .add(107, 349, Short.MAX_VALUE))))
      );
      roisTabLayout.setVerticalGroup(
         roisTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(roisTabLayout.createSequentialGroup()
            .add(21, 21, 21)
            .add(roisTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(setRoiButton)
               .add(jButton1))
            .add(18, 18, 18)
            .add(roisTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(roiStatusLabel)
               .add(sequencingButton))
            .add(18, 18, 18)
            .add(roisTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(roiLoopLabel)
               .add(roiLoopTimesLabel, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 14, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(roiLoopSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
            .add(jSeparator1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(roisTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(jSeparator3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 84, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(roisTabLayout.createSequentialGroup()
                  .add(6, 6, 6)
                  .add(roisTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                     .add(runROIsNowButton)
                     .add(roisTabLayout.createSequentialGroup()
                        .add(useInMDAcheckBox)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                        .add(roisTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                           .add(startFrameLabel)
                           .add(startFrameSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(roisTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                           .add(repeatCheckBox)
                           .add(repeatEveryFrameSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                           .add(framesLabel))))))
            .addContainerGap(12, Short.MAX_VALUE))
      );

      mainTabbedPane.addTab("ROIs", roisTab);

      calibrateButton.setText("Calibrate!");
      calibrateButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            calibrateButtonActionPerformed(evt);
         }
      });

      allPixelsButton.setText("All Pixels");
      allPixelsButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            allPixelsButtonActionPerformed(evt);
         }
      });

      centerButton.setText("Show center spot");
      centerButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            centerButtonActionPerformed(evt);
         }
      });

      channelComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
      channelComboBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            channelComboBoxActionPerformed(evt);
         }
      });

      jLabel4.setText("Phototargeting channel:");

      shutterComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
      shutterComboBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            shutterComboBoxActionPerformed(evt);
         }
      });

      jLabel5.setText("Phototargeting shutter:");

      org.jdesktop.layout.GroupLayout setupTabLayout = new org.jdesktop.layout.GroupLayout(setupTab);
      setupTab.setLayout(setupTabLayout);
      setupTabLayout.setHorizontalGroup(
         setupTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(setupTabLayout.createSequentialGroup()
            .add(39, 39, 39)
            .add(setupTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(setupTabLayout.createSequentialGroup()
                  .add(centerButton)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                  .add(allPixelsButton))
               .add(setupTabLayout.createSequentialGroup()
                  .add(setupTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                     .add(jLabel4)
                     .add(jLabel5))
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                  .add(setupTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                     .add(shutterComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 126, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                     .add(channelComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 126, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
               .add(calibrateButton))
            .addContainerGap(197, Short.MAX_VALUE))
      );
      setupTabLayout.setVerticalGroup(
         setupTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(setupTabLayout.createSequentialGroup()
            .add(27, 27, 27)
            .add(setupTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(centerButton)
               .add(allPixelsButton))
            .add(18, 18, 18)
            .add(calibrateButton)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 18, Short.MAX_VALUE)
            .add(setupTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(jLabel4)
               .add(channelComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(setupTabLayout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(shutterComboBox, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(jLabel5))
            .add(83, 83, 83))
      );

      mainTabbedPane.addTab("Setup", setupTab);

      offButton.setText("Off");
      offButton.setSelected(true);
      offButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            offButtonActionPerformed(evt);
         }
      });

      closeShutterLabel.setText("Exposure time:");

      pointAndShootIntervalSpinner.setModel(new SpinnerNumberModel(500, 1, 1000000000, 1));
      pointAndShootIntervalSpinner.setMaximumSize(new java.awt.Dimension(75, 20));
      pointAndShootIntervalSpinner.setMinimumSize(new java.awt.Dimension(75, 20));
      pointAndShootIntervalSpinner.setPreferredSize(new java.awt.Dimension(75, 20));
      pointAndShootIntervalSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            pointAndShootIntervalSpinnerStateChanged(evt);
         }
      });
      pointAndShootIntervalSpinner.addVetoableChangeListener(new java.beans.VetoableChangeListener() {
         public void vetoableChange(java.beans.PropertyChangeEvent evt)throws java.beans.PropertyVetoException {
            pointAndShootIntervalSpinnerVetoableChange(evt);
         }
      });

      jLabel2.setText("ms");

      org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(layout.createSequentialGroup()
            .addContainerGap()
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(mainTabbedPane)
               .add(layout.createSequentialGroup()
                  .add(closeShutterLabel)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                  .add(pointAndShootIntervalSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 75, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                  .add(18, 18, 18)
                  .add(jLabel2)
                  .add(65, 65, 65)
                  .add(onButton)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                  .add(offButton)
                  .add(0, 0, Short.MAX_VALUE)))
            .addContainerGap())
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(layout.createSequentialGroup()
            .addContainerGap()
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(onButton)
               .add(offButton)
               .add(pointAndShootIntervalSpinner, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(closeShutterLabel)
               .add(jLabel2))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
            .add(mainTabbedPane, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 266, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addContainerGap(20, Short.MAX_VALUE))
      );

      pack();
   }// </editor-fold>//GEN-END:initComponents

    private void calibrateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_calibrateButtonActionPerformed
       boolean running = isCalibrating();
       if (running) {
           stopCalibration();
           calibrateButton.setText("Calibrate");
       } else {
           calibrate();
           calibrateButton.setText("Stop calibration");
       }
    }//GEN-LAST:event_calibrateButtonActionPerformed

    private void onButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_onButtonActionPerformed
       turnOn();
       offButton.setSelected(false);
       onButton.setSelected(true);
       pointAndShootOffButtonActionPerformed(null);
    }//GEN-LAST:event_onButtonActionPerformed

    private void offButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_offButtonActionPerformed
       turnOff();
       offButton.setSelected(true);
       onButton.setSelected(false);
    }//GEN-LAST:event_offButtonActionPerformed

    private void allPixelsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allPixelsButtonActionPerformed
       activateAllPixels();
    }//GEN-LAST:event_allPixelsButtonActionPerformed

   private void mainTabbedPaneStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_mainTabbedPaneStateChanged
         pointAndShootOnButton.setSelected(false);
         pointAndShootOffButton.setSelected(true);
         updatePointAndShoot();
   }//GEN-LAST:event_mainTabbedPaneStateChanged

   private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
      ProjectorPlugin.showRoiManager();
   }//GEN-LAST:event_jButton1ActionPerformed

   private void useInMDAcheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useInMDAcheckBoxActionPerformed
      updateROISettings();
   }//GEN-LAST:event_useInMDAcheckBoxActionPerformed

   private void repeatCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_repeatCheckBoxActionPerformed
      setRoiRepetitions(repeatCheckBox.isSelected()
         ? getRoiRepetitionsSetting() : 0);
      updateROISettings();
   }//GEN-LAST:event_repeatCheckBoxActionPerformed

   private void roiLoopSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_roiLoopSpinnerStateChanged
      setRoiRepetitions(getRoiRepetitionsSetting());
   }//GEN-LAST:event_roiLoopSpinnerStateChanged

   private void runROIsNowButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runROIsNowButtonActionPerformed
      runPolygons();
   }//GEN-LAST:event_runROIsNowButtonActionPerformed

   private void setRoiButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setRoiButtonActionPerformed
      numROIs_ = setRois(getRoiRepetitionsSetting());
      this.updateROISettings();
   }//GEN-LAST:event_setRoiButtonActionPerformed

   private void centerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_centerButtonActionPerformed
      offButtonActionPerformed(null);
      moveToCenter();
   }//GEN-LAST:event_centerButtonActionPerformed

   private void pointAndShootOffButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pointAndShootOffButtonActionPerformed
      pointAndShootOnButton.setSelected(false);
      pointAndShootOffButton.setSelected(true);
      updatePointAndShoot();
   }//GEN-LAST:event_pointAndShootOffButtonActionPerformed

   private void pointAndShootOnButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pointAndShootOnButtonActionPerformed
      pointAndShootOnButton.setSelected(true);
      pointAndShootOffButton.setSelected(false);
      offButtonActionPerformed(null);
      updatePointAndShoot();
   }//GEN-LAST:event_pointAndShootOnButtonActionPerformed

   private void pointAndShootIntervalSpinnerVetoableChange(java.beans.PropertyChangeEvent evt)throws java.beans.PropertyVetoException {//GEN-FIRST:event_pointAndShootIntervalSpinnerVetoableChange
      updateExposure();
   }//GEN-LAST:event_pointAndShootIntervalSpinnerVetoableChange

   private void pointAndShootIntervalSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_pointAndShootIntervalSpinnerStateChanged
   updateExposure();
   }//GEN-LAST:event_pointAndShootIntervalSpinnerStateChanged

   private void repeatEveryFrameSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_repeatEveryFrameSpinnerStateChanged
      updateROISettings();
   }//GEN-LAST:event_repeatEveryFrameSpinnerStateChanged

   private void startFrameSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_startFrameSpinnerStateChanged
      updateROISettings();
   }//GEN-LAST:event_startFrameSpinnerStateChanged

    private void channelComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_channelComboBoxActionPerformed
       final String channel = (String) channelComboBox.getSelectedItem();
       setTargetingChannel(channel);
       if (channel != null) {
          Preferences.userNodeForPackage(this.getClass()).put("channel", channel);
       }
    }//GEN-LAST:event_channelComboBoxActionPerformed

   private void sequencingButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sequencingButtonActionPerformed
      showMosaicSequencingFrame();
   }//GEN-LAST:event_sequencingButtonActionPerformed

   private void shutterComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_shutterComboBoxActionPerformed
      final String shutter = (String) shutterComboBox.getSelectedItem();
      setTargetingShutter(shutter);
      if (shutter != null) {
         Preferences.userNodeForPackage(this.getClass()).put("shutter", shutter);
      }
   }//GEN-LAST:event_shutterComboBoxActionPerformed

   private int getRoiRepetitionsSetting() {
      return getSpinnerValue(roiLoopSpinner);
   }

   private int getSpinnerValue(JSpinner spinner) {
      return Integer.parseInt(spinner.getValue().toString());
   }

   public void updatePointAndShoot() {
      enablePointAndShootMode(pointAndShootOnButton.isSelected());
   }

   public void dispose() {
      super.dispose();
   }
  
   public void updateROISettings() {
      boolean roisSubmitted = false;
      if (numROIs_ == 0) {
         roiStatusLabel.setText("No ROIs submitted");
         roisSubmitted = false;
      } else if (numROIs_ == 1) {
         roiStatusLabel.setText("One ROI submitted");
         roisSubmitted = true;
      } else { // numROIs_ > 1
         roiStatusLabel.setText("" + numROIs_ + " ROIs submitted");
         roisSubmitted = true;
      }

      roiLoopLabel.setEnabled(roisSubmitted);
      roiLoopSpinner.setEnabled(!isSLM_ && roisSubmitted);
      roiLoopTimesLabel.setEnabled(!isSLM_ && roisSubmitted);
      runROIsNowButton.setEnabled(roisSubmitted);
      useInMDAcheckBox.setEnabled(roisSubmitted);

      boolean useInMDA = roisSubmitted && useInMDAcheckBox.isSelected();
      startFrameLabel.setEnabled(useInMDA);
      startFrameSpinner.setEnabled(useInMDA);
      repeatCheckBox.setEnabled(useInMDA);

      boolean repeatInMDA = useInMDA && repeatCheckBox.isSelected();
      repeatEveryFrameSpinner.setEnabled(repeatInMDA);
      framesLabel.setEnabled(repeatInMDA);
      
      if (useInMDAcheckBox.isSelected()) {
         removeFromMDA();
         attachToMDA(getSpinnerValue(this.startFrameSpinner) - 1,
            this.repeatCheckBox.isSelected(),
            getSpinnerValue(this.repeatEveryFrameSpinner));
      } else {
         removeFromMDA();
      }
   }
   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JButton allPixelsButton;
   private javax.swing.JButton calibrateButton;
   private javax.swing.JButton centerButton;
   private javax.swing.JComboBox channelComboBox;
   private javax.swing.JLabel closeShutterLabel;
   private javax.swing.JLabel framesLabel;
   private javax.swing.JButton jButton1;
   private javax.swing.JLabel jLabel1;
   private javax.swing.JLabel jLabel2;
   private javax.swing.JLabel jLabel3;
   private javax.swing.JLabel jLabel4;
   private javax.swing.JLabel jLabel5;
   private javax.swing.JSeparator jSeparator1;
   private javax.swing.JSeparator jSeparator3;
   private javax.swing.JTabbedPane mainTabbedPane;
   private javax.swing.JButton offButton;
   private javax.swing.JButton onButton;
   private javax.swing.JSpinner pointAndShootIntervalSpinner;
   private javax.swing.JToggleButton pointAndShootOffButton;
   private javax.swing.JToggleButton pointAndShootOnButton;
   private javax.swing.JPanel pointAndShootTab;
   private javax.swing.JCheckBox repeatCheckBox;
   private javax.swing.JSpinner repeatEveryFrameSpinner;
   private javax.swing.JLabel roiLoopLabel;
   private javax.swing.JSpinner roiLoopSpinner;
   private javax.swing.JLabel roiLoopTimesLabel;
   private javax.swing.JLabel roiStatusLabel;
   private javax.swing.JPanel roisTab;
   private javax.swing.JButton runROIsNowButton;
   private javax.swing.JButton sequencingButton;
   private javax.swing.JButton setRoiButton;
   private javax.swing.JPanel setupTab;
   private javax.swing.JComboBox shutterComboBox;
   private javax.swing.JLabel startFrameLabel;
   private javax.swing.JSpinner startFrameSpinner;
   private javax.swing.JCheckBox useInMDAcheckBox;
   // End of variables declaration//GEN-END:variables

   public void turnedOn() {
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            onButton.setSelected(true);
            offButton.setSelected(false);
         }
      });
   }

   public void turnedOff() {
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            onButton.setSelected(false);
            offButton.setSelected(true);
         }
      });
   }

   void populateChannelComboBox(String initialChannel) {
      if (initialChannel == null) {
         initialChannel = (String) channelComboBox.getSelectedItem();
      }
      channelComboBox.removeAllItems();
      channelComboBox.addItem("");
      for (String preset : core_.getAvailableConfigs(core_.getChannelGroup())) {
         channelComboBox.addItem(preset);
      }
      channelComboBox.setSelectedItem(initialChannel);
   }

   void populateShutterComboBox(String initialShutter) {
      if (initialShutter == null) {
         initialShutter = (String) shutterComboBox.getSelectedItem();
      }
      shutterComboBox.removeAllItems();
      shutterComboBox.addItem("");
      for (String shutter : core_.getLoadedDevicesOfType(DeviceType.ShutterDevice)) {
         shutterComboBox.addItem(shutter);
      }
      shutterComboBox.setSelectedItem(initialShutter);
   }

   @Override
   public void calibrationDone() {
      calibrateButton.setText("Calibrate");
   }

   private void updateExposure() {
       setExposure(1000 * Double.parseDouble(this.pointAndShootIntervalSpinner.getValue().toString()));
   }



}
