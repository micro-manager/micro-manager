/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.projector;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.TaggedImage;
import org.micromanager.acquisition.VirtualAcquisitionDisplay;
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
   AtomicBoolean stopRequested_ = new AtomicBoolean(false);
   AtomicBoolean isRunning_ = new AtomicBoolean(false);
   
       
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
   
   public Point transformAndFlip(Map<Polygon, AffineTransform> mapping, ImagePlus imgp, Point pt) {
       Point pOffscreen = mirrorIfNecessary(pt, imgp);
       return transform(mapping, pOffscreen);
   }
   
   public void calibrate() {
      final boolean liveModeRunning = gui.isLiveModeOn();
      gui.enableLiveMode(false);
      Thread th = new Thread("Projector calibration thread") {
         public void run() {
            isRunning_.set(true);
            Roi originalROI = IJ.getImage().getRoi();
            gui.snapSingleImage();
            
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
            saveMapping((HashMap<Polygon, AffineTransform>) mapping);
            }
            //saveAffineTransform(affineTransform);
            gui.enableLiveMode(liveModeRunning);
            JOptionPane.showMessageDialog(IJ.getImage().getWindow(), "Calibration " 
                    + (!stopRequested_.get() ? "finished." : "canceled."));
            IJ.getImage().setRoi(originalROI);
            
            isRunning_.set(false);
            stopRequested_.set(false);
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
      if ((boolean) stopRequested_.get()) {
          return null;
      }
      
      try {
         mmc.snapImage();
         ImageProcessor proc1 = ImageUtils.makeProcessor(mmc.getTaggedImage());

         displaySpot(dmdPt.x, dmdPt.y, 500000);
         Thread.sleep(300);

         mmc.snapImage();
         TaggedImage taggedImage2 = mmc.getTaggedImage();
         ImageProcessor proc2 = ImageUtils.makeProcessor(taggedImage2);
         gui.displayImage(taggedImage2);

         Point peak = findPeak(ImageUtils.subtractImageProcessors(proc2, proc1));
         Point maxPt = peak;
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
       if (!stopRequested_.get()) {
            mapSpot(spotMap, new Point((int) ptSLM.x, (int) ptSLM.y));
       }
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
      if ((boolean) stopRequested_.get()) {
          return null;
      }
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
   
   public Map getMapping(AffineTransform firstApprox) {
       if (firstApprox == null) {
           return null;
       }
      int devWidth = (int) dev.getWidth()-1;
      int devHeight = (int) dev.getHeight()-1;
     Point2D.Double camCorner1 = (Point2D.Double) firstApprox.transform(new Point2D.Double(0,0), null);
     Point2D.Double camCorner2 = (Point2D.Double) firstApprox.transform(new Point2D.Double((int )mmc.getImageWidth(), (int) mmc.getImageHeight()), null);
     int camLeft = Math.min((int) camCorner1.x, (int) camCorner2.x);
     int camRight = Math.max((int) camCorner1.x, (int) camCorner2.x);
     int camTop = Math.min((int) camCorner1.y, (int) camCorner2.y);
     int camBottom = Math.max((int) camCorner1.y, (int) camCorner2.y);
     int left = Math.max(camLeft, 0);
     int right = Math.min(camRight,devWidth);
     int top = Math.max(camTop, 0);
     int bottom = Math.min(camBottom,devHeight);
     int width = right-left;
     int height = bottom-top;
   
     
      int n = 7;
      Point2D.Double dmdPoint[][] = new Point2D.Double[1+n][1+n];
      Point2D.Double resultPoint[][] = new Point2D.Double[1+n][1+n];
      for (int i = 0; i <= n; ++i) {
        for (int j = 0; j <= n; ++j) {
           dmdPoint[i][j] = new Point2D.Double((int) left + i*width/n,(int) top + j*height/n);
                Point spot = measureSpot(toIntPoint(dmdPoint[i][j]));
           if (spot != null) {
                resultPoint[i][j] = toDoublePoint(spot);           
           }
        }
      }
      
      if (stopRequested_.get()) {
          return null;
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
   
    public int setRois(int reps, ImagePlus imgp) {
        //AffineTransform transform = loadAffineTransform();
        if (mapping_ != null) {
            Roi[] rois = null;
            Roi[] roiMgrRois = {};
            ImageWindow window = WindowManager.getCurrentWindow();
            if (window != null) {
                Roi singleRoi = window.getImagePlus().getRoi();
                final RoiManager mgr = RoiManager.getInstance();
                if (mgr != null) {
                    roiMgrRois = mgr.getRoisAsArray();
                }
                if (roiMgrRois.length > 0) {
                    rois = roiMgrRois;
                } else if (singleRoi != null) {
                    rois = new Roi[]{singleRoi};
                } else {
                    ReportingUtils.showError("Please first select ROI(s)");
                }
                individualRois_ = separateOutPointRois(rois);
                sendRoiData(imgp);
                return individualRois_.length;
            } else {
                ReportingUtils.showError("No image window with ROIs is open.");
                return 0;
            }

        } else {
            return 0;
        }
    }
   
   private Polygon[] transformROIs(ImagePlus imgp, Roi[] rois, Map<Polygon, AffineTransform> mapping) {
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
                  galvoPoint = transformAndFlip(mapping, imgp, imagePoint);
                  if (galvoPoint == null) throw new Exception();
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
      return (Polygon[]) transformedROIs.toArray(new Polygon[0]);
   }

   private void sendRoiData(ImagePlus imgp) {
      if (individualRois_.length > 0) {
         if (mapping_ != null) {
            Polygon[] galvoROIs = transformROIs(imgp, individualRois_,mapping_);
            dev.setRois(galvoROIs);
            dev.setPolygonRepetitions(reps_);
            dev.setSpotInterval(interval_us_);
         }
      }
   }
   
   public void setRoiRepetitions(int reps) {
      reps_ = reps;
      sendRoiData(IJ.getImage());
   }

   public void displaySpot(double x, double y, double intervalUs) {
      if (x>=0 && x<dev.getWidth() && y>=0 && y<dev.getHeight()) {
         dev.displaySpot(x, y, intervalUs);
      }
   }
   
   public String prepareChannel() {
                           String originalConfig = null;
                    String channelGroup = mmc.getChannelGroup();
                    AcquisitionEngine eng = gui.getAcquisitionEngine();
                    try {
                        if (targetingChannel_.length() > 0) {
                            originalConfig = mmc.getCurrentConfig(channelGroup);
                            if (!originalConfig.contentEquals(targetingChannel_)) {
                                gui.getAcquisitionEngine().setPause(true);
                                mmc.setConfig(channelGroup, targetingChannel_);
                            }
                        }
                    } catch (Exception ex) {
                        ReportingUtils.logError(ex);
                    }
                    return originalConfig;
 
   }
   
   public void returnChannel(String originalConfig) {
                           String channelGroup = mmc.getChannelGroup();
       if (originalConfig != null) {
                        try {
                            mmc.setConfig(channelGroup, originalConfig);
                            final AcquisitionEngine eng = gui.getAcquisitionEngine();
                            if (eng.isAcquisitionRunning() && eng.isPaused()) {
                                eng.setPause(false);
                            }
                        } catch (Exception ex) {
                            ReportingUtils.logError(ex);
                        }
                    }
   }
   
   public boolean isMirrored(ImagePlus imgp) {
       try {
       String mirrorString = VirtualAcquisitionDisplay.getDisplay(imgp)
               .getCurrentMetadata().getString("ImageFlipper-Mirror");
       return (mirrorString.contentEquals("On"));
       } catch (Exception e) {
           return false;
       }
   }
   
   public Point mirrorIfNecessary(Point pOffscreen, ImagePlus imgp) {
       if (isMirrored(imgp)) {
           return new Point(imgp.getWidth() - pOffscreen.x, pOffscreen.y);
       } else {
           return pOffscreen;
       }
   }
   
    public MouseListener setupPointAndShootMouseListener() {
        final ProjectorController thisController = this;
        return new MouseAdapter() {

            public void mouseClicked(MouseEvent e) {
                if (e.isControlDown()) {
                    String originalConfig = prepareChannel();
                    Point p = e.getPoint();
                    ImageCanvas canvas = (ImageCanvas) e.getSource();
                    Point pOffscreen = new Point(canvas.offScreenX(p.x), canvas.offScreenY(p.y));
                    Point devP = transformAndFlip((Map<Polygon, AffineTransform>) loadMapping(), canvas.getImage(), new Point(pOffscreen.x, pOffscreen.y));
                    if (devP != null) {
                        displaySpot(devP.x, devP.y, thisController.getPointAndShootInterval());
                    }
                    returnChannel(originalConfig);
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
            String originalConfig = prepareChannel();
            runPolygons();
            returnChannel(originalConfig);
         }
         
         public String toString() {
             return "Phototargeting of ROIs";
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
      recordPolygons();
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
        this.sendRoiData(IJ.getImage());
    }

    void setTargetingChannel(Object selectedItem) {
        targetingChannel_ = (String) selectedItem;
    }

    private void recordPolygons() {
        String location = gui.getAcquisitionEngine().getImageCache().getDiskLocation();
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
    
    private void saveROIs(File path) {
        try {
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(zos));
            RoiEncoder re = new RoiEncoder(out);
            int count = 0;
            for (Roi roi : individualRois_) {
                String label = roi.getName();
                if (label == null) {
                    label = String.valueOf(count);
                }
                if (!label.endsWith(".roi")) {
                    label += ".roi";
                }
                zos.putNextEntry(new ZipEntry(label));
                re.write(roi);
                out.flush();
                ++count;
            }
            out.close();
        } catch (Exception e) {
            ReportingUtils.logError(e);
        }
    }

    boolean isCalibrating() {
        return isRunning_.get();
    }

    void stopCalibration() {
        stopRequested_.set(true);
    }
   

}
