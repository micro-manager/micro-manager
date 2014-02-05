/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.projector;

import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
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
   private double spotDiameter_;
   private boolean imageOn_ = false;   
   HashSet<OnStateListener> onStateListeners_ = new HashSet<OnStateListener>();

   public SLM(CMMCore mmc, double spotDiameter) {
      mmc_ = mmc;
      slm_ = mmc_.getSLMDevice();
      spotDiameter_ = spotDiameter;
      slmWidth_ = (int) mmc.getSLMWidth(slm_);
      slmHeight_ = (int) mmc.getSLMHeight(slm_);
   }

   public String getName() {
       return slm_;
   }
   
   private void displaySpot(int x, int y) {
      ImageProcessor proc = new ByteProcessor(slmWidth_, slmHeight_);
      proc.setColor(Color.black);
      proc.fill();
      proc.setColor(Color.white);
      addSpot(proc, x, y, spotDiameter_);
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

   public double getWidth() {
      return this.slmWidth_;
   }

   public double getHeight() {
      return this.slmHeight_;
   }


   public void turnOff() {
      try {
         mmc_.setSLMPixelsTo(slm_, (byte) 0);
         imageOn_ = false;
         for (OnStateListener listener:onStateListeners_) {
            listener.turnedOff();
         }
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
         for (OnStateListener listener:onStateListeners_) {
            listener.turnedOn();
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   // Convert an array of polygonal ROIs to a single pixel image.
   public static byte[] roisToPixels(int width, int height, Polygon[] roiPolygons) {
      ByteProcessor processor = new ByteProcessor(width, height);
      processor.setColor(Color.black);
      processor.fill();
      processor.setColor(Color.white);
      for (Polygon roiPolygon : roiPolygons) {
         Roi roi = new PolygonRoi(roiPolygon, Roi.POLYGON);
         processor.fill(roi);
      }
      return (byte[]) processor.getPixels();
   }
   
   public void loadRois(Polygon[] roiPolygons) {
      try {
         mmc_.setSLMImage(slm_, roisToPixels(slmWidth_, slmHeight_, roiPolygons));
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }
   
   public void displaySpot(double x, double y, double interval_us) {
      setDwellTime((long) interval_us);
      displaySpot(x, y);
   }
   
   public void addOnStateListener(OnStateListener listener) {
      onStateListeners_.add(listener);
   }

   public void removeOnStateListener(OnStateListener listener) {
      onStateListeners_.remove(listener);
   }

   public void setPolygonRepetitions(int reps) {
      // Ignore!
   }

   public void runPolygons() {
      try {
         mmc_.displaySLMImage(slm_);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public String getChannel() {
       return "Default";
   }
   
   public void waitForDevice() {
        try {
            mmc_.waitForDevice(slm_);
        } catch (Exception ex) {
            ReportingUtils.logError(ex);
        }
   }

   @Override
   public void setDwellTime(long interval_us) {
      try {
         mmc_.setSLMExposure(slm_, interval_us / 1000.);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

    @Override
    public void activateAllPixels() {
        try {
           mmc_.setSLMPixelsTo(slm_, (short) 255);
           if (imageOn_ == true) {
              mmc_.displaySLMImage(slm_);
           }
        } catch (Exception ex) {
           ReportingUtils.showError(ex);
        }
     }
    
}
