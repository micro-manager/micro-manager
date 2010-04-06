/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.projector;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.Color;
import org.micromanager.utils.ReportingUtils;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
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
   private ScriptInterface gui;
   private int slmWidth;
   private int slmHeight;
   private double diameter;
   private AffineTransform trans;

   public ProjectorController(ScriptInterface app) {
      gui = app;
      mmc = app.getMMCore();
      slm = mmc.getSLMDevice();
      if (slm.length() > 0) {
         slmWidth = (int) mmc.getSLMWidth(slm);
         slmHeight = (int) mmc.getSLMHeight(slm);
         diameter = 5;
      }

   }

   public void calibrate() {
      AffineTransform firstApprox = getFirstApproxTransform();
      trans = getFinalTransform(firstApprox);
   }

   public void addSpot(ImageProcessor proc, int x, int y, double dia) {
      proc.fillOval((int) (x-dia/2), (int) (y-dia/2), (int) dia, (int) dia);
   }
// then use:
//imgp.updateImage();
//imgp.getCanvas().repaint();


   public void displaySpot(int x, int y) {
      ImageProcessor proc = new ByteProcessor(slmWidth, slmHeight);
      proc.setColor(Color.black);
      proc.fill();
      proc.setColor(Color.white);
      addSpot(proc,x,y, diameter);
      ImagePlus img = new ImagePlus("",proc);
      //img.show();
      try {
         mmc.setSLMImage(slm, (byte []) proc.getPixels());
         mmc.displaySLMImage(slm);
      } catch (Throwable e) {
         ReportingUtils.showError("SLM not connecting properly.");
      }
   }

   public Point measureSpot(Point dmdPt) {
      displaySpot(dmdPt.x,dmdPt.y);
      //mmc.sleep(200);
      gui.snapSingleImage();
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
      int x = slmWidth/2;
      int y = slmHeight/2;

      int s = 50;
      Map spotMap = new HashMap();

      mapSpot(spotMap, new Point(x,y));
      mapSpot(spotMap, new Point(x,y+s));
      mapSpot(spotMap, new Point(x+s,y));
      mapSpot(spotMap, new Point(x,y-s));
      mapSpot(spotMap, new Point(x-s,y));

      return MathFunctions.generateAffineTransformFromPointPairs(spotMap);
   }

   public AffineTransform getFinalTransform(AffineTransform firstApprox) {
      Map spotMap2 = new HashMap();
      int imgWidth = (int) mmc.getImageWidth();
      int imgHeight = (int) mmc.getImageHeight();

      int s = 30;
      Point2D.Double dmdPoint;

      dmdPoint = (Point2D.Double) firstApprox.transform(new Point2D.Double((double) s,(double) s), null);
      mapSpot(spotMap2, dmdPoint);
      dmdPoint = (Point2D.Double)  firstApprox.transform(new Point2D.Double((double) imgWidth-s,(double) s),null);
      mapSpot(spotMap2, dmdPoint);
      dmdPoint = (Point2D.Double)  firstApprox.transform(new Point2D.Double((double) imgWidth-s,(double) imgHeight-s),null);
      mapSpot(spotMap2, dmdPoint);
      dmdPoint = (Point2D.Double)  firstApprox.transform(new Point2D.Double((double) s,(double) imgHeight-s),null);
      mapSpot(spotMap2, dmdPoint);

      return MathFunctions.generateAffineTransformFromPointPairs(spotMap2);
   }
   

   public void setRoi() {
      AffineTransformOp cmo = new AffineTransformOp(trans, AffineTransformOp.TYPE_BILINEAR);
      ImagePlus imgpCamera = null;

      if (gui.getImageWin() != null) {
         imgpCamera = gui.getImageWin().getImagePlus();
      } else {
         return;
      }

      int imgWidth = (int) mmc.getImageWidth();
      int imgHeight = (int) mmc.getImageHeight();
      

      if (imgpCamera != null) {
         ImageProcessor procCamera = imgpCamera.getProcessor();
         ImageCanvas cvsCamera = imgpCamera.getCanvas();
         Roi roiCamera = imgpCamera.getRoi();

         if (roiCamera != null) {
            ByteProcessor procMask = new ByteProcessor(imgWidth, imgHeight);
            procMask.setColor(Color.black);
            procMask.fill();
            procMask.setColor(Color.white);
            procMask.fill(roiCamera);
            //imgpMask = new ImagePlus("", procMask);
            //imgpMask.show();

            BufferedImage imgMask = procMask.getBufferedImage();
            BufferedImage imgSLM = new BufferedImage(slmWidth,slmHeight,BufferedImage.TYPE_BYTE_GRAY);
            cmo.filter(imgMask,imgSLM);
            ByteProcessor procSLM = new ByteProcessor(imgSLM);
            try {
               mmc.setSLMImage(slm, (byte[]) procSLM.getPixels());
            } catch (Exception ex) {
               ReportingUtils.showError(ex);          
            }
            //gui.snapSingleImage();
         } else {
            ReportingUtils.showMessage("Please draw an ROI for bleaching.");
         }
      } else {
         ReportingUtils.showMessage("Please snap an image first.");
      }
   }

   public void turnOff() {
      try {
         mmc.setSLMPixelsTo(slm, (byte) 0);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   void turnOn() {
      try {
         mmc.displaySLMImage(slm);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }
}
