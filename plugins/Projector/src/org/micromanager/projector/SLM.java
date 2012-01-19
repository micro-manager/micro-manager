/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.projector;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import java.awt.Color;
import mmcorej.CMMCore;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class SLM implements ProjectionDevice {
   String slm_;
   CMMCore mmc_;
   int slmWidth_;
   int slmHeight_;
   private double diameter_;
   private boolean imageOn_ = false;   

   public SLM(CMMCore mmc, double diameter) {
      mmc_ = mmc;
      slm_ = mmc_.getSLMDevice();
      slmWidth_ = (int) mmc.getSLMWidth(slm_);
      slmHeight_ = (int) mmc.getSLMHeight(slm_);
   }

   private void displaySpot(int x, int y) {
      ImageProcessor proc = new ByteProcessor(slmWidth_, slmHeight_);
      proc.setColor(Color.black);
      proc.fill();
      proc.setColor(Color.white);
      addSpot(proc,x,y, diameter_);
      ImagePlus img = new ImagePlus("",proc);
      //img.show();
      try {
         mmc_.setSLMImage(slm_, (byte []) proc.getPixels());
         mmc_.displaySLMImage(slm_);
      } catch (Throwable e) {
         ReportingUtils.showError("SLM not connecting properly.");
      }
   }

   private void addSpot(ImageProcessor proc, int x, int y, double dia) {
      proc.fillOval((int) (x-dia/2), (int) (y-dia/2), (int) dia, (int) dia);
   }

   public void displaySpot(double x, double y) {
      displaySpot((int) x, (int) y);
   }

   public int getWidth() {
      return this.slmWidth_;
   }

   public int getHeight() {
      return this.slmHeight_;
   }


   public void turnOff() {
      try {
         mmc_.setSLMPixelsTo(slm_, (byte) 0);
         imageOn_ = false;
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public void turnOn() {
      try {
         if (imageOn_ == false) {
            mmc_.displaySLMImage(slm_);
            imageOn_ = true;
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

/*
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
            BufferedImage imgSLM = new BufferedImage(
                    dev.getWidth(),
                    dev.getHeight(),
                    BufferedImage.TYPE_BYTE_GRAY);
            cmo.filter(imgMask,imgSLM);
            ByteProcessor procSLM = new ByteProcessor(imgSLM);
            try {
               mmc.setSLMImage(slm, (byte[]) procSLM.getPixels());
               if (imageOn_)
                  mmc.displaySLMImage(slm);
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
*/
}
