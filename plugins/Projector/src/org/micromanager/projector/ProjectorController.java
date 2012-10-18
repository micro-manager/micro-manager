/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.projector;

import ij.IJ;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MathFunctions;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class ProjectorController {

   private String slm;
   private CMMCore mmc;
   private final ScriptInterface gui;
   private boolean imageOn_ = false;
   final private ProjectionDevice dev;
   private MouseListener pointAndShootMouseListener;
   private double pointAndShootInterval;
   private Roi[] individualRois_ = {};
   private int reps_ = 1;
   private long interval_us_ = 500000;

   public ProjectorController(ScriptInterface app) {
      gui = app;
      mmc = app.getMMCore();
      String slm = mmc.getSLMDevice();
      String galvo = mmc.getGalvoDevice();
      
      if (slm.length() > 0) {
         dev = new SLM(mmc, 5);
      } else if (galvo.length() > 0) {
         dev = new Galvo(mmc);
      } else {
         dev = null;
      }
      
      pointAndShootMouseListener = setupPointAndShootMouseListener();
   }

   public boolean isSLM() {
       return (dev instanceof SLM);
   }
   
   public void calibrate() {
      final boolean liveModeRunning = gui.isLiveModeOn();
      gui.enableLiveMode(false);
      Thread th = new Thread("Projector calibration thread") {
         public void run() {
            Roi originalROI = IJ.getImage().getRoi();
            AffineTransform firstApprox = getFirstApproxTransform();
            AffineTransform affineTransform = getFinalTransform(firstApprox);
            dev.turnOff();
            try {
               Thread.sleep(500);
            } catch (InterruptedException ex) {
               ReportingUtils.logError(ex);
            }
            saveAffineTransform(affineTransform);
            gui.enableLiveMode(liveModeRunning);
            JOptionPane.showMessageDialog(IJ.getImage().getWindow(), "Calibration finished.");
            IJ.getImage().setRoi(originalROI);
         }
      };
      th.start();
   }

   private Preferences getCalibrationNode() {
       return Preferences.userNodeForPackage(ProjectorPlugin.class)
               .node("calibration")
               .node(dev.getChannel())
               .node(mmc.getCameraDevice());
   }
   
   public void saveAffineTransform(AffineTransform affineTransform) {
       JavaUtils.putObjectInPrefs(getCalibrationNode(), dev.getName(), affineTransform);
   }

      public AffineTransform loadAffineTransform() {
      AffineTransform transform = (AffineTransform) JavaUtils.getObjectFromPrefs(getCalibrationNode(), dev.getName(), null);
      if (transform == null) {
         ReportingUtils.showError("The galvo has not been calibrated for the current settings.");
         return null;
      } else {
         return transform;
      }
   }
// then use:
//imgp.updateImage();
//imgp.getCanvas().repaint();
   public Point measureSpot(Point dmdPt) {
      dev.displaySpot(dmdPt.x, dmdPt.y);
      dev.waitForDevice();
      gui.snapSingleImage();
      mmc.sleep(200);
      ImageProcessor proc = IJ.getImage().getProcessor();
      Point maxPt = findPeak(proc);
      IJ.getImage().setRoi(new PointRoi(maxPt.x, maxPt.y));
      return maxPt;
   }

   private Point findPeak(ImageProcessor proc) {
      ImageProcessor blurImage = ((ImageProcessor) proc.duplicate());
      blurImage.setRoi((Roi) null);
      GaussianBlur blur = new GaussianBlur();
      blur.blurGaussian(blurImage, 20, 20, 0.01);
      return ImageUtils.findMaxPixel(blurImage);
   }
   
   public void mapSpot(Map spotMap, Point ptSLM) {
      Point2D.Double ptSLMDouble = new Point2D.Double(ptSLM.x, ptSLM.y);
      Point ptCam = measureSpot(ptSLM);
      Point2D.Double ptCamDouble = new Point2D.Double(ptCam.x, ptCam.y);
      spotMap.put(ptCamDouble, ptSLMDouble);
   }

   public void mapSpot(Map spotMap, Point2D.Double ptSLM) {
      mapSpot(spotMap, new Point((int) ptSLM.x, (int) ptSLM.y));
   }

   public AffineTransform getFirstApproxTransform() {
      double x = dev.getWidth() / 2;
      double y = dev.getHeight() / 2;

      int s = 50;
      Map spotMap = new HashMap();

      mapSpot(spotMap, new Point2D.Double(x, y));
      mapSpot(spotMap, new Point2D.Double(x, y + s));
      mapSpot(spotMap, new Point2D.Double(x + s, y));
      mapSpot(spotMap, new Point2D.Double(x, y - s));
      mapSpot(spotMap, new Point2D.Double(x - s, y));

      return MathFunctions.generateAffineTransformFromPointPairs(spotMap);
   }

   public AffineTransform getFinalTransform(AffineTransform firstApprox) {
      Map spotMap2 = new HashMap();
      int imgWidth = (int) mmc.getImageWidth();
      int imgHeight = (int) mmc.getImageHeight();

      int s = 30;
      Point2D.Double dmdPoint;

      dmdPoint = (Point2D.Double) firstApprox.transform(new Point2D.Double((double) s, (double) s), null);
      mapSpot(spotMap2, dmdPoint);
      dmdPoint = (Point2D.Double) firstApprox.transform(new Point2D.Double((double) imgWidth - s, (double) s), null);
      mapSpot(spotMap2, dmdPoint);
      dmdPoint = (Point2D.Double) firstApprox.transform(new Point2D.Double((double) imgWidth - s, (double) imgHeight - s), null);
      mapSpot(spotMap2, dmdPoint);
      dmdPoint = (Point2D.Double) firstApprox.transform(new Point2D.Double((double) s, (double) imgHeight - s), null);
      mapSpot(spotMap2, dmdPoint);
      return MathFunctions.generateAffineTransformFromPointPairs(spotMap2);
   }

   public void turnOff() {
      dev.turnOff();
   }

   public void turnOn() {
      dev.turnOn();
   }

   void activateAllPixels() {
     if (dev instanceof SLM) {
        try {
           mmc.setSLMPixelsTo(slm, (short) 255);
           if (imageOn_ == true) {
              mmc.displaySLMImage(slm);
           }
        } catch (Exception ex) {
           ReportingUtils.showError(ex);
        }
     }
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
      return (Roi[]) roiList.toArray(rois);
   }
   
   public int setRois(int reps) {
      AffineTransform transform = loadAffineTransform();
      if (transform != null) {
         Roi[] rois = null;
         Roi[] roiMgrRois = {};
         Roi singleRoi = gui.getImageWin().getImagePlus().getRoi();
         final RoiManager mgr = RoiManager.getInstance();
         if (mgr != null) {
            roiMgrRois = mgr.getRoisAsArray();
         }
         if (roiMgrRois.length > 0) {
            rois = roiMgrRois;
         } else if (singleRoi != null) {
            rois = new Roi[] {singleRoi};
         } else {
            ReportingUtils.showError("Please first select ROI(s)");
         }
         individualRois_ = separateOutPointRois(rois);
         sendRoiData();
         return individualRois_.length;
      } else {
         return 0;
      }
   }

   private void sendRoiData() {
      if (individualRois_.length > 0) {
         AffineTransform transform = loadAffineTransform();
         if (transform != null) {
            dev.setRois(individualRois_, transform);
            dev.setPolygonRepetitions(reps_);
            dev.setSpotInterval(interval_us_);
         }
      }
   }
   
   public void setRoiRepetitions(int reps) {
      reps_ = reps;
      sendRoiData();
   }

   public MouseListener setupPointAndShootMouseListener() {
      final ProjectorController thisController = this;
      return new MouseAdapter() {
         public void mouseClicked(MouseEvent e) {
            Point p = e.getPoint();
            ImageCanvas canvas = (ImageCanvas) e.getSource();
            Point pOffscreen = new Point(canvas.offScreenX(p.x),canvas.offScreenY(p.y));
            Point2D.Double devP = (Point2D.Double) loadAffineTransform().transform(
                    new Point2D.Double(pOffscreen.x, pOffscreen.y), null);
            dev.displaySpot(devP.x, devP.y, thisController.getPointAndShootInterval());
         }
      };
   }
 
   public void activatePointAndShootMode(boolean on) {
      ImageCanvas canvas = null;
      ImageWindow window = WindowManager.getCurrentWindow();
      if (window != null) {
         canvas = window.getCanvas();
         for (MouseListener listener : canvas.getMouseListeners()) {
            if (listener == pointAndShootMouseListener) {
               canvas.removeMouseListener(listener);
            }
         }
      }

      if (on) {
         if (canvas != null) {
            canvas.addMouseListener(pointAndShootMouseListener);
         }
      }
   }

   public void attachToMDA(int frameOn, boolean repeat, int repeatInterval) {
      Runnable runPolygons = new Runnable() {
         public void run() {
            runPolygons();
         }
      };

      final AcquisitionEngine acq = gui.getAcquisitionEngine();
      acq.clearRunnables();
      if (repeat) {
         for (int i = frameOn; i < acq.getNumFrames(); i += repeatInterval) {
            acq.attachRunnable(i, -1, 0, 0, runPolygons);
         }
      } else {
         acq.attachRunnable(frameOn, -1, 0, 0, runPolygons);
      }
   }

   public void removeFromMDA() {
      gui.getAcquisitionEngine().clearRunnables();
   }

   public void setPointAndShootInterval(double intervalUs) {
      this.pointAndShootInterval = intervalUs;
   }

   public double getPointAndShootInterval() {
       return this.pointAndShootInterval;
   }
   
   void runPolygons() {
      dev.runPolygons();
   }

   void addOnStateListener(OnStateListener listener) {
      dev.addOnStateListener(listener);
   }

   void moveToCenter() {
      double x = dev.getWidth() / 2;
      double y = dev.getHeight() / 2;
      dev.displaySpot(x, y);
   }

   void setSpotInterval(long interval_us) {
     interval_us_ = interval_us;
     this.sendRoiData();
   }
}
