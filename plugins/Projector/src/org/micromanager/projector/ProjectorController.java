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
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.TaggedImage;
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
   private Map mapping_ = null;
   private String mappingNode_ = null;
   private String targetingChannel_;
       
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

      loadMapping();
      pointAndShootMouseListener = setupPointAndShootMouseListener();
   }

   public boolean isSLM() {
       return (dev instanceof SLM);
   }
   
   
   public Point transform(Map<Polygon, AffineTransform> mapping, Point pt) {
       Set<Polygon> set = mapping.keySet();
       for (Polygon poly:set) {
           if (poly.contains(pt)) {
               return toIntPoint((Point2D.Double) mapping.get(poly).transform(toDoublePoint(pt), null));
           } 
       }
       return null;
   }
   
   public void calibrate() {
      final boolean liveModeRunning = gui.isLiveModeOn();
      gui.enableLiveMode(false);
      Thread th = new Thread("Projector calibration thread") {
         public void run() {
            Roi originalROI = IJ.getImage().getRoi();
            gui.snapSingleImage();
            HashMap<Polygon, AffineTransform> mapping = (HashMap<Polygon, AffineTransform>) getMapping();
            //LocalWeightedMean lwm = multipleAffineTransforms(mapping_);
            //AffineTransform affineTransform = MathFunctions.generateAffineTransformFromPointPairs(mapping_);
            dev.turnOff();
            try {
               Thread.sleep(500);
            } catch (InterruptedException ex) {
               ReportingUtils.logError(ex);
            }
            saveMapping((HashMap<Polygon, AffineTransform>) mapping);
            //saveAffineTransform(affineTransform);
            gui.enableLiveMode(liveModeRunning);
            JOptionPane.showMessageDialog(IJ.getImage().getWindow(), "Calibration finished.");
            IJ.getImage().setRoi(originalROI);
         }
      };
      th.start();
   }

   private HashMap<Polygon, AffineTransform> loadMapping() {
       String nodeStr = getCalibrationNode().toString();
       if (mappingNode_ == null || !nodeStr.contentEquals(mappingNode_)) {
           mappingNode_ = nodeStr;
           mapping_ = (HashMap<Polygon, AffineTransform>) JavaUtils.getObjectFromPrefs(getCalibrationNode(), dev.getName(), new HashMap<Polygon, AffineTransform>());
       }
       return (HashMap<Polygon, AffineTransform>) mapping_;
   }
   
   private void saveMapping(HashMap<Polygon, AffineTransform> mapping) {
       JavaUtils.putObjectInPrefs(getCalibrationNode(), dev.getName(), mapping);
       mapping_ = mapping;
       mappingNode_ = getCalibrationNode().toString();
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
      
   public Point measureSpot(Point dmdPt) {
      try {
         mmc.snapImage();
         ImageProcessor proc1 = ImageUtils.makeProcessor(mmc.getTaggedImage());

         displaySpot(dmdPt.x, dmdPt.y, 500000);
         Thread.sleep(300);

         mmc.snapImage();
         TaggedImage taggedImage2 = mmc.getTaggedImage();
         ImageProcessor proc2 = ImageUtils.makeProcessor(taggedImage2);
         gui.displayImage(taggedImage2);

         Point maxPt = findPeak(ImageUtils.subtractImageProcessors(proc2, proc1));
         IJ.getImage().setRoi(new PointRoi(maxPt.x, maxPt.y));
         mmc.sleep(500);
         return maxPt;
      } catch (Exception e) {
         ReportingUtils.showError(e);
         return null;
      }
   }

   private Point findPeak(ImageProcessor proc) {
      ImageProcessor blurImage = ((ImageProcessor) proc.duplicate());
      blurImage.setRoi((Roi) null);
      GaussianBlur blur = new GaussianBlur();
      blur.blurGaussian(blurImage, 10, 10, 0.01);
      //gui.displayImage(blurImage.getPixels());
      Point x = ImageUtils.findMaxPixel(blurImage);
      x.translate(1, 1);
      return x;
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

   
   public static Point2D.Double clipPoint(Point2D.Double pt, Rectangle2D.Double rect) {
      return new Point2D.Double(
              MathFunctions.clip(pt.x, rect.x, rect.x + rect.width),
              MathFunctions.clip(pt.y, rect.y, rect.y + rect.height));
   }
   
   public static Point2D.Double transformAndClip(double x, double y, AffineTransform transform, Rectangle2D.Double clipRect) {
      return clipPoint((Point2D.Double) transform.transform(new Point2D.Double(x,y), null), clipRect);
   }
   
   public static void addVertex(Polygon poly, Point p) {
       poly.addPoint(p.x, p.y);
   }
   
   public static Point toIntPoint(Point2D.Double pt) {
       return new Point((int) pt.x, (int) pt.y);
   }
   
   public static Point2D.Double toDoublePoint(Point pt) {
       return new Point2D.Double(pt.x, pt.y);
   }
   
   public Map getMapping() {
      int w = (int) dev.getWidth()-1;
      int h = (int) dev.getHeight()-1;
      int n = 8;
      Point2D.Double dmdPoint[][] = new Point2D.Double[1+n][1+n];
      Point2D.Double resultPoint[][] = new Point2D.Double[1+n][1+n];
      for (int i = 0; i <= n; ++i) {
        for (int j = 0; j <= n; ++j) {
           dmdPoint[i][j] = new Point2D.Double((int) (i*w/n),(int) (j*h/n));
           resultPoint[i][j] = toDoublePoint(measureSpot(toIntPoint(dmdPoint[i][j])));
        }
      }
      
      Map bigMap = new HashMap();
      for (int i=0; i<=n-1; ++i) {
          for (int j=0; j<=n-1; ++j) {
              Polygon poly = new Polygon();
              addVertex(poly, toIntPoint(resultPoint[i][j]));
              addVertex(poly, toIntPoint(resultPoint[i][j+1]));
              addVertex(poly, toIntPoint(resultPoint[i+1][j+1]));
              addVertex(poly, toIntPoint(resultPoint[i+1][j]));
              
              Map map = new HashMap();
              map.put(resultPoint[i][j], dmdPoint[i][j]);
              map.put(resultPoint[i][j+1], dmdPoint[i][j+1]);
              map.put(resultPoint[i+1][j], dmdPoint[i+1][j]);
              map.put(resultPoint[i+1][j+1], dmdPoint[i+1][j+1]);
              
              AffineTransform transform = MathFunctions.generateAffineTransformFromPointPairs(map);
              bigMap.put(poly, transform);
          } 
      }
      return bigMap;
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
      //AffineTransform transform = loadAffineTransform();
      if (mapping_ != null) {
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
   
   private Polygon[] transformROIs(Roi[] rois, Map<Polygon, AffineTransform> mapping) {
      ArrayList<Polygon> transformedROIs = new ArrayList<Polygon>();
      for (Roi roi : rois) {
         if ((roi.getType() == Roi.POINT)
                 || (roi.getType() == Roi.POLYGON)
                 || (roi.getType() == Roi.RECTANGLE)
                 || (roi.getType() == Roi.OVAL)) {

            Polygon poly = roi.getPolygon();
            Polygon newPoly = new Polygon();
            try {
               Point2D galvoPoint;
               for (int i = 0; i < poly.npoints; ++i) {
                  Point imagePoint = new Point(poly.xpoints[i], poly.ypoints[i]);
                  galvoPoint = transform(mapping, imagePoint);
                  newPoly.addPoint((int) galvoPoint.getX(), (int) galvoPoint.getY());
               }
               transformedROIs.add(newPoly);
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
               break;
            }

         } else {
            ReportingUtils.showError("Can't use this type of ROI.");
            break;
         }
      }
      return (Polygon[]) transformedROIs.toArray();
   }

   private void sendRoiData() {
      if (individualRois_.length > 0) {
         if (mapping_ != null) {
            Polygon[] galvoROIs = transformROIs(individualRois_,mapping_);
            dev.setRois(galvoROIs);
            dev.setPolygonRepetitions(reps_);
            dev.setSpotInterval(interval_us_);
         }
      }
   }
   
   public void setRoiRepetitions(int reps) {
      reps_ = reps;
      sendRoiData();
   }

   public void displaySpot(double x, double y, double intervalUs) {
      if (x>=0 && x<dev.getWidth() && y>=0 && y<dev.getHeight()) {
         dev.displaySpot(x, y, intervalUs);
      }
   }
   
    public MouseListener setupPointAndShootMouseListener() {
        final ProjectorController thisController = this;
        return new MouseAdapter() {

            public void mouseClicked(MouseEvent e) {
                if (e.isControlDown()) {
                    String originalConfig = null;
                    String channelGroup = mmc.getChannelGroup();
                    boolean acqEngineShouldRestart = false;
                    AcquisitionEngine eng = gui.getAcquisitionEngine();
                    try {
                        if (targetingChannel_.length() > 0) {
                            originalConfig = mmc.getCurrentConfig(channelGroup);
                            if (!originalConfig.contentEquals(targetingChannel_)) {
                                acqEngineShouldRestart = eng.isAcquisitionRunning() && !eng.isPaused();
                                gui.getAcquisitionEngine().setPause(true);
                                mmc.setConfig(channelGroup, targetingChannel_);
                            }
                        }
                    } catch (Exception ex) {
                        ReportingUtils.logError(ex);
                    }

                    Point p = e.getPoint();
                    ImageCanvas canvas = (ImageCanvas) e.getSource();
                    Point pOffscreen = new Point(canvas.offScreenX(p.x), canvas.offScreenY(p.y));
                    Point devP = transform((Map<Polygon, AffineTransform>) loadMapping(), new Point(pOffscreen.x, pOffscreen.y));
                    if (devP != null) {
                        displaySpot(devP.x, devP.y, thisController.getPointAndShootInterval());
                    }
                    if (originalConfig != null) {
                        try {
                            mmc.setConfig(channelGroup, originalConfig);
                            if (acqEngineShouldRestart) {
                                eng.setPause(false);
                            }
                        } catch (Exception ex) {
                            ReportingUtils.logError(ex);
                        }
                    }
                }
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

    void setTargetingChannel(Object selectedItem) {
        targetingChannel_ = (String) selectedItem;
    }
   

}
