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

// This file is written in call stack order: methods declared later in the
// file call methods earlier in the file, with the exception of generated
// code (found at the end of file).

// This source file is formatted to be processed
// with [docco](http://jashkenas.github.io/docco/),
// which generates nice HTML documentation side-by-side with the
// source code.

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.EllipseRoi;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.OvalRoi;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.json.JSONException;
import org.micromanager.data.Datastore;
import org.micromanager.ScriptInterface;
// TODO should not depend on this module.
import org.micromanager.display.internal.MMVirtualStack;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.MMListenerAdapter;
import org.micromanager.internal.utils.MathFunctions;
import org.micromanager.internal.utils.ReportingUtils;

// The main window for the Projector plugin. Contains logic for calibration,
// and control for SLMs and Galvos.
public class ProjectorControlForm extends javax.swing.JFrame implements OnStateListener {
   private static ProjectorControlForm formSingleton_;
   private final ProjectionDevice dev_;
   private final MouseListener pointAndShootMouseListener;
   private final AtomicBoolean pointAndShooteModeOn_ = new AtomicBoolean(false);
   private final CMMCore core_;
   private final ScriptInterface app_;
   private final boolean isSLM_;
   private int numROIs_;
   private Roi[] individualRois_ = {};
   private Map<Polygon, AffineTransform> mapping_ = null;
   private String mappingNode_ = null;
   private String targetingChannel_;
   AtomicBoolean stopRequested_ = new AtomicBoolean(false);
   AtomicBoolean isRunning_ = new AtomicBoolean(false);
   private MosaicSequencingFrame mosaicSequencingFrame_;
   private String targetingShutter_;

   // ## Simple utility methods for points
   
   // Add a point to an existing polygon.
   private static void addVertex(Polygon polygon, Point p) {
      polygon.addPoint(p.x, p.y);
   }
   
   // Return the vertices of the given polygon as a series of points.
   private static Point[] getVertices(Polygon polygon) {
      Point vertices[] = new Point[polygon.npoints];
      for (int i = 0; i < polygon.npoints; ++i) {
         vertices[i] = new Point(polygon.xpoints[i], polygon.ypoints[i]);
      }   
      return vertices;
   }
   
   // Gets the vectorial mean of an array of Points.
   private static Point2D.Double meanPosition2D(Point[] points) {
      double xsum = 0;
      double ysum = 0;
      int n = points.length;
      for (int i = 0; i < n; ++i) {
         xsum += points[i].x;
         ysum += points[i].y;
      }
      return new Point2D.Double(xsum/n, ysum/n);
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

   // ## Methods for handling targeting channel and shutter
   
   // Read the available channels from Micro-Manager Channel Group
   // and populate the targeting channel drop-down menu.
   final void populateChannelComboBox(String initialChannel) {
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

   // Read the available shutters from Micro-Manager and
   // list them in the targeting shutter drop-down menu.
   final void populateShutterComboBox(String initialShutter) {
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
   
   // Sets the targeting channel. channelName should be
   // a channel from the current ChannelGroup.
   void setTargetingChannel(String channelName) {
      targetingChannel_ = channelName;
       if (channelName != null) {
          Preferences.userNodeForPackage(this.getClass()).put("channel", channelName);
       }
   }
   
   // Sets the targeting shutter. Should be the name of a loaded Shutter device.
   void setTargetingShutter(String shutterName) {
      targetingShutter_ = shutterName;
      if (shutterName != null) {
         Preferences.userNodeForPackage(this.getClass()).put("shutter", shutterName);
      }
   }
   
   // Set the Channel Group to the targeting channel, if it exists.
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
   // Returns Channel Group to its original settings, if needed.
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
   
   // Open the targeting shutter, if it has been specified.
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

   // Close a targeting shutter if it exists and if
   // it was originally closed.
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
   
   // ## Simple methods for device control.
     
   // Set the exposure time for the phototargeting device.
   public void setExposure(double intervalUs) {
      long previousExposure = dev_.getExposure();
      long newExposure = (long) intervalUs;
      if (previousExposure != newExposure) {
         dev_.setExposure(newExposure);
      }
   }
   
   // Turn the projection device on or off.
   private void setOnState(boolean onState) {
      if (onState) {
         dev_.turnOn();
      } else {
         dev_.turnOff();
      }
   }
   
   // Illuminate a spot at position x,y.
   private void displaySpot(double x, double y) {
      if (x >= 0 && x < dev_.getWidth() && y >= 0 && y < dev_.getHeight()) {
         dev_.displaySpot(x, y);
      }
   }
   
   // Illuminate a spot at the center of the Galvo/SLM range, for
   // the exposure time.
   void displayCenterSpot() {
      double x = dev_.getWidth() / 2;
      double y = dev_.getHeight() / 2;
      dev_.displaySpot(x, y);
   }
   
   // ## Generating, loading and saving calibration mappings
   
   // Returns the java Preferences node where we store the Calibration mapping.
   // Each channel/camera combination is assigned a different node.
   private Preferences getCalibrationNode() {
      return Preferences.userNodeForPackage(ProjectorPlugin.class)
            .node("calibration")
            .node(dev_.getChannel())
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
                 dev_.getName(), 
                 new HashMap<Polygon, AffineTransform>());
      }
      return  mapping_;
   }

   // Save the mapping for the current calibration node. The mapping
   // maps each polygon cell to an AffineTransform.
   private void saveMapping(HashMap<Polygon, AffineTransform> mapping) {
      JavaUtils.putObjectInPrefs(getCalibrationNode(), dev_.getName(), mapping);
      mapping_ = mapping;
      mappingNode_ = getCalibrationNode().toString();
   }
   
   // ## Methods for generating a calibration mapping.
   
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
   
   // Display a spot using the projection device, and return its current
   // location on the camera.
   private Point measureSpot(Point projectionPoint) {
      if (stopRequested_.get()) {
         return null;
      }
      try {
         dev_.turnOff();
         Thread.sleep(300);
         core_.snapImage();
         TaggedImage image = core_.getTaggedImage();
         ImageProcessor proc1 = ImageUtils.makeMonochromeProcessor(image);
         long originalExposure = dev_.getExposure();
         dev_.setExposure(500000);
         displaySpot(projectionPoint.x, projectionPoint.y);
         core_.snapImage();
         Thread.sleep(500);
         dev_.setExposure(originalExposure);
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

   // Illuminates and images five control points, and returns
   // an affine transform mapping from image coordinates to
   // phototargeter coordinates.
   private AffineTransform generateLinearMapping() {
      double x = dev_.getWidth() / 2;
      double y = dev_.getHeight() / 2;

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
      try {
         return MathFunctions.generateAffineTransformFromPointPairs(spotMap, s, Double.MAX_VALUE);
      } catch (Exception e) {
         throw new RuntimeException("Spots aren't detected as expected. Is DMD in focus and roughly centered in camera's field of view?");
      }
   }

   // Generate a nonlinear calibration mapping for the current device settings.
   // A rectangular lattice of points is illuminated one-by-one on the
   // projection device, and locations in camera pixels of corresponding
   // spots on the camera image are recorded. For each rectangular
   // cell in the grid, we take the four point mappings (camera to projector)
   // and generate a local AffineTransform using linear least squares.
   // Cells with suspect measured corner positions are discarded.
   // A mapping of cell polygon to AffineTransform is generated. 
   private Map<Polygon, AffineTransform> generateNonlinearMapping(AffineTransform firstApprox) {
      if (firstApprox == null) {
         return null;
      }
      int devWidth = (int) dev_.getWidth() - 1;
      int devHeight = (int) dev_.getHeight() - 1;
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

   // Runs the full calibration. First
   // generates a linear mapping (a first approximation) and then runs
   // a second, non-linear, mapping using the first mapping as a guide. Saves
   // the mapping to Java Preferences.
   public void runCalibration() {
      final boolean liveModeRunning = app_.isLiveModeOn();
      app_.enableLiveMode(false);
      if (!isRunning_.get()) {
         stopRequested_.set(false);
         Thread th = new Thread("Projector calibration thread") {
            @Override
            public void run() {
               try {
                  isRunning_.set(true);
                  Roi originalROI = IJ.getImage().getRoi();
                  app_.snapSingleImage();

                  AffineTransform firstApproxAffine = generateLinearMapping();

                  HashMap<Polygon, AffineTransform> mapping = (HashMap<Polygon, AffineTransform>) generateNonlinearMapping(firstApproxAffine);
                  //LocalWeightedMean lwm = multipleAffineTransforms(mapping_);
                  //AffineTransform affineTransform = MathFunctions.generateAffineTransformFromPointPairs(mapping_);
                  dev_.turnOff();
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
                  calibrateButton_.setText("Calibrate");
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
   
   // Returns true if the calibration is currently running.
   public boolean isCalibrating() {
      return isRunning_.get();
   }
   
   // Requests an interruption to calibration while it is running.
   public void stopCalibration() {
      stopRequested_.set(true);
   }
   
   // ## Transforming points according to a nonlinear calibration mapping.
     
   // Transform a point, pt, given the mapping, which is a Map of polygon cells
   // to AffineTransforms.
   private static Point transformPoint(Map<Polygon, AffineTransform> mapping, Point pt) {
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
         double distance = meanPosition2D(getVertices(poly)).distance(pt.x, pt.y);
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
   private static boolean isImageMirrored(ImagePlus imgp) {
      if (!(imgp.getStack() instanceof MMVirtualStack)) {
         return false;
      }
      try {
         Datastore store = ((MMVirtualStack) imgp.getStack()).getDatastore();
         String mirrorString = store.getSummaryMetadata().getUserData().getString("ImageFlipper-Mirror");
         return mirrorString.contentEquals("On");
      } catch (JSONException e) {
         return false;
      }
   }

   // Flips a point if it has been mirrored.
   private static Point mirrorIfNecessary(Point pOffscreen, ImagePlus imgp) {
      if (isImageMirrored(imgp)) {
         return new Point(imgp.getWidth() - pOffscreen.x, pOffscreen.y);
      } else {
         return pOffscreen;
      }
   }
   
   // Transform and mirror (if necessary) a point on an image to 
   // a point on phototargeter coordinates.
   private static Point transformAndMirrorPoint(Map<Polygon, AffineTransform> mapping, ImagePlus imgp, Point pt) {
      Point pOffscreen = mirrorIfNecessary(pt, imgp);
      return transformPoint(mapping, pOffscreen);
   }

   // ## Point and shoot
   
   // Creates a MouseListener instance for future use with Point and Shoot
   // mode. When the MouseListener is attached to an ImageJ window, any
   // clicks will result in a spot being illuminated.
   private MouseListener createPointAndShootMouseListenerInstance() {
      return new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            if (e.isControlDown()) {
               Point p = e.getPoint();
               ImageCanvas canvas = (ImageCanvas) e.getSource();
               Point pOffscreen = new Point(canvas.offScreenX(p.x), canvas.offScreenY(p.y));
               Point devP = transformAndMirrorPoint(loadMapping(), canvas.getImage(), 
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

   // Turn on/off point and shoot mode.
   public void enablePointAndShootMode(boolean on) {
      if (on && (mapping_ == null)) {
         throw new RuntimeException("Please calibrate the phototargeting device first, using the Setup tab.");
      }
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
   
   // ## Manipulating ROIs
   
   // Convert an OvalRoi to an EllipseRoi.
   private static Roi asEllipseRoi(OvalRoi roi) {
      Rectangle bounds = roi.getBounds();
      double aspectRatio = bounds.width / (double) bounds.height;
      if (aspectRatio < 1) {
         return new EllipseRoi(bounds.x + bounds.width / 2,
               bounds.y,
               bounds.x + bounds.width / 2,
               bounds.y + bounds.height,
               aspectRatio);
      } else {
         return new EllipseRoi(bounds.x,
               bounds.y + bounds.height / 2,
               bounds.x + bounds.width,
               bounds.y + bounds.height / 2,
               1 / aspectRatio);
      }
   }
   
   // Converts an ROI to a Polygon.
   private static Polygon asPolygon(Roi roi) {
      if ((roi.getType() == Roi.POINT)
               || (roi.getType() == Roi.FREEROI)
               || (roi.getType() == Roi.POLYGON)
            || (roi.getType() == Roi.RECTANGLE)) {
         return roi.getPolygon();
      } else {
         throw new RuntimeException("Can't use this type of ROI.");
      }
   }
   
   // We can't handle Ellipse Rois and compounds PointRois directly.
   private static Roi[] homogenizeROIs(Roi[] rois) {
      List<Roi> roiList = new ArrayList<Roi>();
      for (Roi roi : rois) {
         if (roi.getType() == Roi.POINT) {
            Polygon poly = ((PointRoi) roi).getPolygon();
            for (int i = 0; i < poly.npoints; ++i) {
               roiList.add(new PointRoi(
                     poly.xpoints[i],
                     poly.ypoints[i]));
            }
         } else if (roi.getType() == Roi.OVAL) {
            roiList.add(asEllipseRoi((OvalRoi) roi));
         } else {
            roiList.add(roi);
         }
      }
      return roiList.toArray(rois);
   }
   
   // Coverts an array of ImageJ Rois to an array of Polygons.
   // Handles EllipseRois and compound Point ROIs.
   public static Polygon[] roisAsPolygons(Roi[] rois) {
      Roi[] cleanROIs = homogenizeROIs(rois);
      List<Polygon> roiPolygons = new ArrayList<Polygon>();
      for (Roi roi : cleanROIs) {
         roiPolygons.add(asPolygon(roi));
      }
      return roiPolygons.toArray(new Polygon[0]);
   }
   
   // Gets the label of an ROI with the given index n. Borrowed from ImageJ.
   private static String getROILabel(ImagePlus imp, Roi roi, int n) {
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
     
   // Save a list of ROIs to a given path.
   private static void saveROIs(File path, Roi[] rois) {
      try {
         ImagePlus imgp = IJ.getImage();
         ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path));
         DataOutputStream out = new DataOutputStream(new BufferedOutputStream(zos));
         RoiEncoder re = new RoiEncoder(out);
         for (Roi roi : rois) {
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
   
   // Returns the current ROIs for a given ImageWindow. If selectedOnly
   // is true, then returns only those ROIs selected in the ROI Manager.
   // If no ROIs are selected, then all ROIs are returned.
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
   
   // Transform the Roi polygons with the given nonlinear mapping.
   private static List<Polygon> transformRoiPolygons(final ImagePlus imgp, Polygon[] roiPolygons, Map<Polygon, AffineTransform> mapping) {
      ArrayList<Polygon> transformedROIs = new ArrayList<Polygon>();
      for (Polygon roiPolygon : roiPolygons) {
         Polygon targeterPolygon = new Polygon();
         try {
            Point2D targeterPoint;
            for (int i = 0; i < roiPolygon.npoints; ++i) {
               Point imagePoint = new Point(roiPolygon.xpoints[i], roiPolygon.ypoints[i]);
               targeterPoint = transformAndMirrorPoint(mapping, imgp, imagePoint);
               if (targeterPoint == null) {
                  throw new Exception();
               }
               targeterPolygon.addPoint((int) (0.5 + targeterPoint.getX()), (int) (0.5 + targeterPoint.getY()));
            }
            transformedROIs.add(targeterPolygon);
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
            break;
         }
      }
      return transformedROIs;
   }
       
   // ## Saving, sending, and running ROIs.
     
   // Returns ROIs, transformed by the current mapping.
   public List<Polygon> transformROIs(ImagePlus contextImagePlus, Roi[] rois) {
      return transformRoiPolygons(contextImagePlus, roisAsPolygons(rois), mapping_);
   }
   
   // Save ROIs in the acquisition path, if it exists.
   private void recordPolygons() {
      if (app_.isAcquisitionRunning()) {
         // TODO: The MM2.0 refactor broke this code by removing the below
         // method.
//         String location = app_.getAcquisitionPath();
//         if (location != null) {
//            try {
//               File f = new File(location, "ProjectorROIs.zip");
//               if (!f.exists()) {
//                  saveROIs(f, individualRois_);
//               }
//            } catch (Exception ex) {
//               ReportingUtils.logError(ex);
//            }
//         }
      }
   }
   
   // Upload current Window's ROIs, transformed, to the phototargeting device.
   public void sendCurrentImageWindowRois() {
      if (mapping_ == null) {
         throw new RuntimeException("Please calibrate the phototargeting device first, using the Setup tab.");
      }
      ImageWindow window = WindowManager.getCurrentWindow();
      if (window == null) {
         throw new RuntimeException("No image window with ROIs is open.");
      }
      ImagePlus imgp = window.getImagePlus();
      Roi[] rois = getRois(window, true);
      if (rois.length == 0) {
         throw new RuntimeException("Please first draw the desired phototargeting ROIs.");
      }
      List<Polygon> transformedRois = transformROIs(imgp, rois);
      dev_.loadRois(transformedRois);
      individualRois_ = rois;
   }
   
   // Illuminate the polygons ROIs that have been previously uploaded to
   // phototargeter.
   void runRois() {
      Configuration originalConfig = prepareChannel();
      boolean originalShutterState = prepareShutter();
      dev_.runPolygons();
      returnShutter(originalShutterState);
      returnChannel(originalConfig);
      recordPolygons();
   }

   // ## Attach/detach MDA
       
   // Attaches phototargeting ROIs to a multi-dimensional acquisition, so that
   // they will run on a particular firstFrame and, if repeat is true,
   // thereafter again every frameRepeatInterval frames.
   public void attachRoisToMDA(int firstFrame, boolean repeat, int frameRepeatInveral, Runnable runPolygons) {
      app_.clearRunnables();
      if (repeat) {
         for (int i = firstFrame; i < app_.getAcquisitionSettings().numFrames * 10; i += frameRepeatInveral) {
            app_.attachRunnable(i, -1, 0, 0, runPolygons);
         }
      } else {
         app_.attachRunnable(firstFrame, -1, 0, 0, runPolygons);
      }
   }

   // Remove the attached ROIs from the multi-dimensional acquisition.
   public void removeFromMDA() {
      app_.clearRunnables();
   }
  
   // ## GUI
   
   // Forces a JSpinner to fire a change event reflecting the new value
   // whenver user types a valid entry.
   private static void commitSpinnerOnValidEdit(final JSpinner spinner) {
      ((DefaultFormatter) ((JSpinner.DefaultEditor) spinner.getEditor())
            .getTextField().getFormatter()).setCommitsOnValidEdit(true);
   }
   
   // Return the value of a spinner displaying an integer.
   private static int getSpinnerIntegerValue(JSpinner spinner) {
      return Integer.parseInt(spinner.getValue().toString());
   }

   // Return the value of a spinner displaying an integer.
   private static double getSpinnerDoubleValue(JSpinner spinner) {
      return Double.parseDouble(spinner.getValue().toString());
   }
   
   // Sets the Point and Shoot "On and Off" buttons to a given state.
   public void updatePointAndShoot(boolean turnedOn) {
      pointAndShootOnButton.setSelected(turnedOn);
      pointAndShootOffButton.setSelected(!turnedOn);
      enablePointAndShootMode(turnedOn);
   }
   
   // Generates a runnable that runs the selected ROIs.
   private Runnable phototargetROIsRunnable(final String runnableName) {
      return new Runnable() {
         @Override
         public void run() {
            runRois();
         }
         @Override
         public String toString() {
            return runnableName;
         }
      };
   }
   
   // Converts a Runnable to one that runs asynchronously.
   public static Runnable makeRunnableAsync(final Runnable runnable) { 
      return new Runnable() {
         public void run() {
            new Thread() {
               public void run() {
                  runnable.run();            
               }
            }.start();
         }
      };
   }  
   
   // Sleep until the designated clock time.
   private static void sleepUntil(long clockTimeMillis) {
      long delta = clockTimeMillis - System.currentTimeMillis();
      if (delta > 0) {
         try {
            Thread.sleep(delta);
         } catch (InterruptedException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }
   
   // Runs runnable starting at firstTimeMs after this function is called, and
   // then, if repeat is true, every intervalTimeMs thereafter until
   // shouldContinue.call() returns false.
   private Runnable runAtIntervals(final long firstTimeMs, final boolean repeat,
      final long intervalTimeMs, final Runnable runnable, final Callable<Boolean> shouldContinue) {
      return new Runnable() {
         public void run() {
            try {
               final long startTime = System.currentTimeMillis() + firstTimeMs;
               int reps = 0;
               while (shouldContinue.call()) {
                  sleepUntil(startTime + reps * intervalTimeMs);
                  runnable.run();
                  ++reps;
                  if (!repeat) {
                     break;
                  }
               }
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
         }
      };
   }
   
   // Update the GUI's roi settings so they reflect the user's current choices.
   public final void updateROISettings() {
      boolean roisSubmitted = false;
      int numROIs = individualRois_.length;
      if (numROIs == 0) {
         roiStatusLabel.setText("No ROIs submitted");
         roisSubmitted = false;
      } else if (numROIs == 1) {
         roiStatusLabel.setText("One ROI submitted");
         roisSubmitted = true;
      } else { // numROIs > 1
         roiStatusLabel.setText("" + numROIs + " ROIs submitted");
         roisSubmitted = true;
      }

      roiLoopLabel.setEnabled(roisSubmitted);
      roiLoopSpinner.setEnabled(!isSLM_ && roisSubmitted);
      roiLoopTimesLabel.setEnabled(!isSLM_ && roisSubmitted);
      runROIsNowButton.setEnabled(roisSubmitted);
      useInMDAcheckBox.setEnabled(roisSubmitted);

      boolean useInMDA = roisSubmitted && useInMDAcheckBox.isSelected();
      attachToMdaTabbedPane.setEnabled(useInMDA);
      startFrameLabel.setEnabled(useInMDA);
      startFrameSpinner.setEnabled(useInMDA);
      repeatCheckBox.setEnabled(useInMDA);
      startTimeLabel.setEnabled(useInMDA);
      startTimeUnitLabel.setEnabled(useInMDA);
      startTimeSpinner.setEnabled(useInMDA);
      repeatCheckBoxTime.setEnabled(useInMDA);
      
      boolean repeatInMDA = useInMDA && repeatCheckBox.isSelected();
      repeatEveryFrameSpinner.setEnabled(repeatInMDA);
      repeatEveryFrameUnitLabel.setEnabled(repeatInMDA);
      
      boolean repeatInMDATime = useInMDA && repeatCheckBoxTime.isSelected();
      repeatEveryIntervalSpinner.setEnabled(repeatInMDATime);
      repeatEveryIntervalUnitLabel.setEnabled(repeatInMDATime);
      
      boolean synchronous = attachToMdaTabbedPane.getSelectedComponent() == syncRoiPanel;

      if (useInMDAcheckBox.isSelected()) {
         removeFromMDA();
         if (synchronous) {
            attachRoisToMDA(getSpinnerIntegerValue(startFrameSpinner) - 1,
                  repeatCheckBox.isSelected(),
                  getSpinnerIntegerValue(repeatEveryFrameSpinner),
                  phototargetROIsRunnable("Synchronous phototargeting of ROIs"));
         } else {
            final Callable<Boolean> mdaRunning = new Callable<Boolean>() {
               public Boolean call() throws Exception {
                  return app_.isAcquisitionRunning();
               }
            };
            attachRoisToMDA(1, false, 0,
                  makeRunnableAsync(
                        runAtIntervals((long) (1000. * getSpinnerDoubleValue(startTimeSpinner)),
                        repeatCheckBoxTime.isSelected(),
                        (long) (1000 * getSpinnerDoubleValue(repeatEveryIntervalSpinner)),
                        phototargetROIsRunnable("Asynchronous phototargeting of ROIs"),
                        mdaRunning)));
         }
      } else {
         removeFromMDA();
      }
      dev_.setPolygonRepetitions(repeatCheckBox.isSelected()
         ? getSpinnerIntegerValue(roiLoopSpinner) : 0);
   }
    
   // Set the exposure to whatever value is currently in the Exposure field.
   private void updateExposure() {
       setExposure(1000 * Double.parseDouble(pointAndShootIntervalSpinner.getValue().toString()));
   }
   
   // Method called if the phototargeting device has turned on or off.
   public void stateChanged(final boolean onState) {
      SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            onButton.setSelected(onState);
            offButton.setSelected(!onState);
         }
      });
   }
   
   // Show the Mosaic Sequencing window (a JFrame). Should only be called
   // if we already know the Mosaic is attached.
   void showMosaicSequencingWindow() {
      if (mosaicSequencingFrame_ == null) {
         mosaicSequencingFrame_ = new MosaicSequencingFrame(app_, core_, this, (SLM) dev_);
      }
      mosaicSequencingFrame_.setVisible(true);
   }
   
   // Constructor. Creates the main window for the Projector plugin.
   private ProjectorControlForm(CMMCore core, ScriptInterface app) {
      initComponents();
      app_ = app;
      core_ = app.getMMCore();
      String slm = core_.getSLMDevice();
      String galvo = core_.getGalvoDevice();

      if (slm.length() > 0) {
         dev_ = new SLM(core_, 20);
      } else if (galvo.length() > 0) {
         dev_ = new Galvo(core_);
      } else {
         dev_ = null;
      }
     
      loadMapping();
      pointAndShootMouseListener = createPointAndShootMouseListenerInstance();

      Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
         @Override
         public void eventDispatched(AWTEvent e) {
            enablePointAndShootMode(pointAndShooteModeOn_.get());
         }
      }, AWTEvent.WINDOW_EVENT_MASK);
      
      isSLM_ = dev_ instanceof SLM;
      // Only an SLM (not a galvo) has pixels.
      allPixelsButton.setVisible(isSLM_);
      // No point in looping ROIs on an SLM.
      roiLoopSpinner.setVisible(!isSLM_);
      roiLoopLabel.setVisible(!isSLM_);
      roiLoopTimesLabel.setVisible(!isSLM_);
      pointAndShootOffButton.setSelected(true);
      populateChannelComboBox(Preferences.userNodeForPackage(this.getClass()).get("channel", ""));
      populateShutterComboBox(Preferences.userNodeForPackage(this.getClass()).get("shutter", ""));
      this.addWindowFocusListener(new WindowAdapter() {
         @Override
         public void windowGainedFocus(WindowEvent e) {
            populateChannelComboBox(null);
            populateShutterComboBox(null);
         }
      });
      
      commitSpinnerOnValidEdit(pointAndShootIntervalSpinner);
      commitSpinnerOnValidEdit(startFrameSpinner);
      commitSpinnerOnValidEdit(repeatEveryFrameSpinner);
      commitSpinnerOnValidEdit(roiLoopSpinner);
      pointAndShootIntervalSpinner.setValue(dev_.getExposure() / 1000);
      sequencingButton.setVisible(MosaicSequencingFrame.getMosaicDevices(core).size() > 0);
     
      app_.addMMListener(new MMListenerAdapter() {
         @Override
         public void slmExposureChanged(String deviceName, double exposure) {
            if (deviceName.equals(dev_.getName())) {
               pointAndShootIntervalSpinner.setValue(exposure);
            }
         }
      });

      updateROISettings();
   }
   
   // Show the form, which is a singleton.
   public static ProjectorControlForm showSingleton(CMMCore core, ScriptInterface app) {
      if (formSingleton_ == null) {
         formSingleton_ = new ProjectorControlForm(core, app);
         // Place window where it was last.
         GUIUtils.recallPosition(formSingleton_);
      }
      formSingleton_.setVisible(true);
      return formSingleton_;
   }
   
   // ## Generated code
   // Warning: the computer-generated code below this line should not be edited
   // by hand. Instead, use the Netbeans Form Editor to make changes.
   
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
      pointAndShootModeLabel = new javax.swing.JLabel();
      pointAndShootOnButton = new javax.swing.JToggleButton();
      pointAndShootOffButton = new javax.swing.JToggleButton();
      phototargetInstructionsLabel = new javax.swing.JLabel();
      setupTab = new javax.swing.JPanel();
      calibrateButton_ = new javax.swing.JButton();
      allPixelsButton = new javax.swing.JButton();
      centerButton = new javax.swing.JButton();
      channelComboBox = new javax.swing.JComboBox();
      phototargetingChannelDropdownLabel = new javax.swing.JLabel();
      shutterComboBox = new javax.swing.JComboBox();
      phototargetingShutterDropdownLabel = new javax.swing.JLabel();
      roisTab = new javax.swing.JPanel();
      roiLoopLabel = new javax.swing.JLabel();
      roiLoopTimesLabel = new javax.swing.JLabel();
      setRoiButton = new javax.swing.JButton();
      runROIsNowButton = new javax.swing.JButton();
      roiLoopSpinner = new javax.swing.JSpinner();
      jSeparator1 = new javax.swing.JSeparator();
      useInMDAcheckBox = new javax.swing.JCheckBox();
      roiStatusLabel = new javax.swing.JLabel();
      roiManagerButton = new javax.swing.JButton();
      jSeparator3 = new javax.swing.JSeparator();
      sequencingButton = new javax.swing.JButton();
      attachToMdaTabbedPane = new javax.swing.JTabbedPane();
      asyncRoiPanel = new javax.swing.JPanel();
      startTimeLabel = new javax.swing.JLabel();
      startTimeSpinner = new javax.swing.JSpinner();
      repeatCheckBoxTime = new javax.swing.JCheckBox();
      repeatEveryIntervalSpinner = new javax.swing.JSpinner();
      repeatEveryIntervalUnitLabel = new javax.swing.JLabel();
      startTimeUnitLabel = new javax.swing.JLabel();
      syncRoiPanel = new javax.swing.JPanel();
      startFrameLabel = new javax.swing.JLabel();
      repeatCheckBox = new javax.swing.JCheckBox();
      startFrameSpinner = new javax.swing.JSpinner();
      repeatEveryFrameSpinner = new javax.swing.JSpinner();
      repeatEveryFrameUnitLabel = new javax.swing.JLabel();
      offButton = new javax.swing.JButton();
      ExposureTimeLabel = new javax.swing.JLabel();
      pointAndShootIntervalSpinner = new javax.swing.JSpinner();
      jLabel2 = new javax.swing.JLabel();

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

      pointAndShootModeLabel.setText("Point and shoot mode:");

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

      phototargetInstructionsLabel.setText("(To phototarget, Control + click on the image.)");

      javax.swing.GroupLayout pointAndShootTabLayout = new javax.swing.GroupLayout(pointAndShootTab);
      pointAndShootTab.setLayout(pointAndShootTabLayout);
      pointAndShootTabLayout.setHorizontalGroup(
         pointAndShootTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(pointAndShootTabLayout.createSequentialGroup()
            .addGap(25, 25, 25)
            .addGroup(pointAndShootTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(pointAndShootTabLayout.createSequentialGroup()
                  .addComponent(pointAndShootModeLabel)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(pointAndShootOnButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(pointAndShootOffButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addComponent(phototargetInstructionsLabel))
            .addContainerGap(109, Short.MAX_VALUE))
      );
      pointAndShootTabLayout.setVerticalGroup(
         pointAndShootTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(pointAndShootTabLayout.createSequentialGroup()
            .addGap(43, 43, 43)
            .addGroup(pointAndShootTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(pointAndShootModeLabel)
               .addComponent(pointAndShootOnButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(pointAndShootOffButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGap(18, 18, 18)
            .addComponent(phototargetInstructionsLabel)
            .addContainerGap(211, Short.MAX_VALUE))
      );

      mainTabbedPane.addTab("Point and Shoot", pointAndShootTab);

      calibrateButton_.setText("Calibrate!");
      calibrateButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            calibrateButton_ActionPerformed(evt);
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

      phototargetingChannelDropdownLabel.setText("Phototargeting channel:");

      shutterComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
      shutterComboBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            shutterComboBoxActionPerformed(evt);
         }
      });

      phototargetingShutterDropdownLabel.setText("Phototargeting shutter:");

      javax.swing.GroupLayout setupTabLayout = new javax.swing.GroupLayout(setupTab);
      setupTab.setLayout(setupTabLayout);
      setupTabLayout.setHorizontalGroup(
         setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(setupTabLayout.createSequentialGroup()
            .addGap(39, 39, 39)
            .addGroup(setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(setupTabLayout.createSequentialGroup()
                  .addComponent(centerButton)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(allPixelsButton))
               .addGroup(setupTabLayout.createSequentialGroup()
                  .addGroup(setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(phototargetingChannelDropdownLabel)
                     .addComponent(phototargetingShutterDropdownLabel))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addGroup(setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(shutterComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 126, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(channelComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 126, javax.swing.GroupLayout.PREFERRED_SIZE)))
               .addComponent(calibrateButton_))
            .addContainerGap(127, Short.MAX_VALUE))
      );
      setupTabLayout.setVerticalGroup(
         setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(setupTabLayout.createSequentialGroup()
            .addGap(27, 27, 27)
            .addGroup(setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(centerButton)
               .addComponent(allPixelsButton))
            .addGap(18, 18, 18)
            .addComponent(calibrateButton_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 89, Short.MAX_VALUE)
            .addGroup(setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(phototargetingChannelDropdownLabel)
               .addComponent(channelComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(shutterComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(phototargetingShutterDropdownLabel))
            .addGap(83, 83, 83))
      );

      mainTabbedPane.addTab("Setup", setupTab);

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

      useInMDAcheckBox.setText("Run ROIs in Multi-Dimensional Acquisition");
      useInMDAcheckBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            useInMDAcheckBoxActionPerformed(evt);
         }
      });

      roiStatusLabel.setText("No ROIs submitted yet");

      roiManagerButton.setText("ROI Manager >>");
      roiManagerButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            roiManagerButtonActionPerformed(evt);
         }
      });

      jSeparator3.setOrientation(javax.swing.SwingConstants.VERTICAL);

      sequencingButton.setText("Sequencing...");
      sequencingButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            sequencingButtonActionPerformed(evt);
         }
      });

      startTimeLabel.setText("Start Time");

      startFrameSpinner.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      startTimeSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            startTimeSpinnerStateChanged(evt);
         }
      });

      repeatCheckBoxTime.setText("Repeat every");
      repeatCheckBoxTime.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            repeatCheckBoxTimeActionPerformed(evt);
         }
      });

      repeatEveryFrameSpinner.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      repeatEveryIntervalSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            repeatEveryIntervalSpinnerStateChanged(evt);
         }
      });

      repeatEveryIntervalUnitLabel.setText("seconds");

      startTimeUnitLabel.setText("seconds");

      javax.swing.GroupLayout asyncRoiPanelLayout = new javax.swing.GroupLayout(asyncRoiPanel);
      asyncRoiPanel.setLayout(asyncRoiPanelLayout);
      asyncRoiPanelLayout.setHorizontalGroup(
         asyncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(asyncRoiPanelLayout.createSequentialGroup()
            .addContainerGap()
            .addGroup(asyncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
               .addGroup(asyncRoiPanelLayout.createSequentialGroup()
                  .addComponent(startTimeLabel)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(startTimeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 57, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(startTimeUnitLabel))
               .addGroup(asyncRoiPanelLayout.createSequentialGroup()
                  .addComponent(repeatCheckBoxTime)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(repeatEveryIntervalSpinner)))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(repeatEveryIntervalUnitLabel)
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      asyncRoiPanelLayout.setVerticalGroup(
         asyncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(asyncRoiPanelLayout.createSequentialGroup()
            .addContainerGap()
            .addGroup(asyncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(startTimeLabel)
               .addComponent(startTimeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(startTimeUnitLabel))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(asyncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(repeatCheckBoxTime)
               .addComponent(repeatEveryIntervalSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(repeatEveryIntervalUnitLabel))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      attachToMdaTabbedPane.addTab("During imaging", asyncRoiPanel);

      startFrameLabel.setText("Start Frame");

      repeatCheckBox.setText("Repeat every");
      repeatCheckBox.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            repeatCheckBoxActionPerformed(evt);
         }
      });

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

      repeatEveryFrameUnitLabel.setText("frames");

      javax.swing.GroupLayout syncRoiPanelLayout = new javax.swing.GroupLayout(syncRoiPanel);
      syncRoiPanel.setLayout(syncRoiPanelLayout);
      syncRoiPanelLayout.setHorizontalGroup(
         syncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(syncRoiPanelLayout.createSequentialGroup()
            .addContainerGap()
            .addGroup(syncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(syncRoiPanelLayout.createSequentialGroup()
                  .addComponent(startFrameLabel)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(startFrameSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(syncRoiPanelLayout.createSequentialGroup()
                  .addComponent(repeatCheckBox)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(repeatEveryFrameSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(repeatEveryFrameUnitLabel)))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      syncRoiPanelLayout.setVerticalGroup(
         syncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(syncRoiPanelLayout.createSequentialGroup()
            .addContainerGap()
            .addGroup(syncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(startFrameLabel)
               .addComponent(startFrameSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(syncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(repeatCheckBox)
               .addComponent(repeatEveryFrameSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(repeatEveryFrameUnitLabel))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      attachToMdaTabbedPane.addTab("Between images", syncRoiPanel);

      javax.swing.GroupLayout roisTabLayout = new javax.swing.GroupLayout(roisTab);
      roisTab.setLayout(roisTabLayout);
      roisTabLayout.setHorizontalGroup(
         roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(roisTabLayout.createSequentialGroup()
            .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
               .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 389, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addGroup(javax.swing.GroupLayout.Alignment.LEADING, roisTabLayout.createSequentialGroup()
                  .addGap(20, 20, 20)
                  .addComponent(runROIsNowButton)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(jSeparator3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(15, 15, 15)
                  .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(useInMDAcheckBox)
                     .addComponent(attachToMdaTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 247, javax.swing.GroupLayout.PREFERRED_SIZE))))
            .addGap(0, 13, Short.MAX_VALUE))
         .addGroup(roisTabLayout.createSequentialGroup()
            .addGap(25, 25, 25)
            .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(roisTabLayout.createSequentialGroup()
                  .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(roisTabLayout.createSequentialGroup()
                        .addComponent(roiStatusLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(sequencingButton))
                     .addGroup(roisTabLayout.createSequentialGroup()
                        .addComponent(setRoiButton, javax.swing.GroupLayout.PREFERRED_SIZE, 108, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(roiManagerButton)))
                  .addGap(93, 93, 93))
               .addGroup(roisTabLayout.createSequentialGroup()
                  .addComponent(roiLoopLabel)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(roiLoopSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(roiLoopTimesLabel)
                  .addGap(115, 115, 115))))
      );
      roisTabLayout.setVerticalGroup(
         roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(roisTabLayout.createSequentialGroup()
            .addGap(21, 21, 21)
            .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(setRoiButton)
               .addComponent(roiManagerButton))
            .addGap(18, 18, 18)
            .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(roiStatusLabel)
               .addComponent(sequencingButton))
            .addGap(18, 18, 18)
            .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(roiLoopLabel)
               .addComponent(roiLoopTimesLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(roiLoopSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
               .addGroup(roisTabLayout.createSequentialGroup()
                  .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(runROIsNowButton)
                     .addComponent(useInMDAcheckBox))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(attachToMdaTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 113, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addComponent(jSeparator3, javax.swing.GroupLayout.PREFERRED_SIZE, 148, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      mainTabbedPane.addTab("ROIs", roisTab);

      offButton.setText("Off");
      offButton.setSelected(true);
      offButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            offButtonActionPerformed(evt);
         }
      });

      ExposureTimeLabel.setText("Exposure time:");

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

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(layout.createSequentialGroup()
                  .addComponent(mainTabbedPane)
                  .addContainerGap())
               .addGroup(layout.createSequentialGroup()
                  .addComponent(ExposureTimeLabel)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(pointAndShootIntervalSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(18, 18, 18)
                  .addComponent(jLabel2)
                  .addGap(65, 65, 65)
                  .addComponent(onButton)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(offButton)
                  .addGap(0, 0, Short.MAX_VALUE))))
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(onButton)
               .addComponent(offButton)
               .addComponent(pointAndShootIntervalSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(ExposureTimeLabel)
               .addComponent(jLabel2))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(mainTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 337, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      pack();
   }// </editor-fold>//GEN-END:initComponents

    private void calibrateButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_calibrateButton_ActionPerformed
       try {
          boolean running = isCalibrating();
          if (running) {
             stopCalibration();
             calibrateButton_.setText("Calibrate");
          } else {
             runCalibration();
             calibrateButton_.setText("Stop calibration");
          }
       } catch (Exception e) {
          ReportingUtils.showError(e);
       }
    }//GEN-LAST:event_calibrateButton_ActionPerformed

    private void onButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_onButtonActionPerformed
       setOnState(true);
       pointAndShootOffButtonActionPerformed(null);
    }//GEN-LAST:event_onButtonActionPerformed

    private void offButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_offButtonActionPerformed
       setOnState(false);
    }//GEN-LAST:event_offButtonActionPerformed

    private void allPixelsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allPixelsButtonActionPerformed
       dev_.activateAllPixels();
    }//GEN-LAST:event_allPixelsButtonActionPerformed

   private void mainTabbedPaneStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_mainTabbedPaneStateChanged
      updatePointAndShoot(false);
   }//GEN-LAST:event_mainTabbedPaneStateChanged

   private void roiManagerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_roiManagerButtonActionPerformed
      ProjectorPlugin.showRoiManager();
   }//GEN-LAST:event_roiManagerButtonActionPerformed

   private void useInMDAcheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useInMDAcheckBoxActionPerformed
      updateROISettings();
   }//GEN-LAST:event_useInMDAcheckBoxActionPerformed

   private void repeatCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_repeatCheckBoxActionPerformed
      updateROISettings();
   }//GEN-LAST:event_repeatCheckBoxActionPerformed

   private void roiLoopSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_roiLoopSpinnerStateChanged
      updateROISettings();
   }//GEN-LAST:event_roiLoopSpinnerStateChanged

   private void runROIsNowButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runROIsNowButtonActionPerformed
      runRois();
   }//GEN-LAST:event_runROIsNowButtonActionPerformed

   private void setRoiButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setRoiButtonActionPerformed
      try {
         sendCurrentImageWindowRois();
         updateROISettings();
      } catch (RuntimeException e) {
         ReportingUtils.showError(e);
      }
   }//GEN-LAST:event_setRoiButtonActionPerformed

   private void centerButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_centerButtonActionPerformed
      offButtonActionPerformed(null);
      displayCenterSpot();
   }//GEN-LAST:event_centerButtonActionPerformed

   private void pointAndShootOffButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pointAndShootOffButtonActionPerformed
      updatePointAndShoot(false);
   }//GEN-LAST:event_pointAndShootOffButtonActionPerformed

   private void pointAndShootOnButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pointAndShootOnButtonActionPerformed
      offButtonActionPerformed(null);
      try {
         updatePointAndShoot(true);
      } catch (RuntimeException e) {
         ReportingUtils.showError(e);
      }
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
    }//GEN-LAST:event_channelComboBoxActionPerformed

   private void sequencingButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sequencingButtonActionPerformed
      showMosaicSequencingWindow();
   }//GEN-LAST:event_sequencingButtonActionPerformed

   private void shutterComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_shutterComboBoxActionPerformed
      final String shutter = (String) shutterComboBox.getSelectedItem();
      setTargetingShutter(shutter);
   }//GEN-LAST:event_shutterComboBoxActionPerformed

   private void startTimeSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_startTimeSpinnerStateChanged
      updateROISettings();
   }//GEN-LAST:event_startTimeSpinnerStateChanged

   private void repeatCheckBoxTimeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_repeatCheckBoxTimeActionPerformed
      updateROISettings();
   }//GEN-LAST:event_repeatCheckBoxTimeActionPerformed

   private void repeatEveryIntervalSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_repeatEveryIntervalSpinnerStateChanged
      updateROISettings();
   }//GEN-LAST:event_repeatEveryIntervalSpinnerStateChanged


   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JLabel ExposureTimeLabel;
   private javax.swing.JButton allPixelsButton;
   private javax.swing.JPanel asyncRoiPanel;
   private javax.swing.JTabbedPane attachToMdaTabbedPane;
   private javax.swing.JButton calibrateButton_;
   private javax.swing.JButton centerButton;
   private javax.swing.JComboBox channelComboBox;
   private javax.swing.JLabel jLabel2;
   private javax.swing.JSeparator jSeparator1;
   private javax.swing.JSeparator jSeparator3;
   private javax.swing.JTabbedPane mainTabbedPane;
   private javax.swing.JButton offButton;
   private javax.swing.JButton onButton;
   private javax.swing.JLabel phototargetInstructionsLabel;
   private javax.swing.JLabel phototargetingChannelDropdownLabel;
   private javax.swing.JLabel phototargetingShutterDropdownLabel;
   private javax.swing.JSpinner pointAndShootIntervalSpinner;
   private javax.swing.JLabel pointAndShootModeLabel;
   private javax.swing.JToggleButton pointAndShootOffButton;
   private javax.swing.JToggleButton pointAndShootOnButton;
   private javax.swing.JPanel pointAndShootTab;
   private javax.swing.JCheckBox repeatCheckBox;
   private javax.swing.JCheckBox repeatCheckBoxTime;
   private javax.swing.JSpinner repeatEveryFrameSpinner;
   private javax.swing.JLabel repeatEveryFrameUnitLabel;
   private javax.swing.JSpinner repeatEveryIntervalSpinner;
   private javax.swing.JLabel repeatEveryIntervalUnitLabel;
   private javax.swing.JLabel roiLoopLabel;
   private javax.swing.JSpinner roiLoopSpinner;
   private javax.swing.JLabel roiLoopTimesLabel;
   private javax.swing.JButton roiManagerButton;
   private javax.swing.JLabel roiStatusLabel;
   private javax.swing.JPanel roisTab;
   private javax.swing.JButton runROIsNowButton;
   private javax.swing.JButton sequencingButton;
   private javax.swing.JButton setRoiButton;
   private javax.swing.JPanel setupTab;
   private javax.swing.JComboBox shutterComboBox;
   private javax.swing.JLabel startFrameLabel;
   private javax.swing.JSpinner startFrameSpinner;
   private javax.swing.JLabel startTimeLabel;
   private javax.swing.JSpinner startTimeSpinner;
   private javax.swing.JLabel startTimeUnitLabel;
   private javax.swing.JPanel syncRoiPanel;
   private javax.swing.JCheckBox useInMDAcheckBox;
   // End of variables declaration//GEN-END:variables

}
