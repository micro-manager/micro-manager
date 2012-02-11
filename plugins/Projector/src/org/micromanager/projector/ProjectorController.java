/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.projector;

import ij.IJ;
import ij.gui.Roi;
import java.awt.geom.AffineTransform;
import org.micromanager.utils.ReportingUtils;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MathFunctions;

/**
 *
 * @author arthur
 */
public class ProjectorController {

   private String slm;
   private CMMCore mmc;
   private final ScriptInterface gui;
   private AffineTransform affineTransform;
   private boolean imageOn_ = false;
   private ProjectionDevice dev;

   public ProjectorController(ScriptInterface app) {
      gui = app;
      mmc = app.getMMCore();
      String slm = mmc.getSLMDevice();
      String galvo = mmc.getGalvoDevice();

      if (slm.length() > 0) {
         dev = new SLM(mmc, 5);
      } else if (galvo.length() > 0) {
         dev = new Galvo(mmc);
      }

   }

   public void calibrate() {
      final boolean liveModeRunning = gui.isLiveModeOn();
      gui.enableLiveMode(false);
      Thread th = new Thread("Projector calibration thread") {

         public void run() {
            AffineTransform firstApprox = getFirstApproxTransform();
            affineTransform = getFinalTransform(firstApprox);
            gui.enableLiveMode(liveModeRunning);
         }
      };
      th.start(); 
   }

// then use:
//imgp.updateImage();
//imgp.getCanvas().repaint();
   public Point measureSpot(Point dmdPt) {
      dev.displaySpot(dmdPt.x, dmdPt.y);
      mmc.sleep(200);
     gui.snapSingleImage();

      mmc.sleep(200);
      ImageProcessor proc = IJ.getImage().getProcessor();
      Point maxPt = ImageUtils.findMaxPixel(proc);
      return maxPt;
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
      try {
         mmc.setSLMPixelsTo(slm, (short) 255);
         if (imageOn_ == true) {
            mmc.displaySLMImage(slm);
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public void setRoi() {
      dev.setRoi(IJ.getImage().getRoi(), affineTransform);
   }
}
