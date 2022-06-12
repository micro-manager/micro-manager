///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       
//
// COPYRIGHT:    University of California, San Francisco, 2014
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.internal;

import com.google.common.eventbus.Subscribe;
import java.awt.geom.AffineTransform;
import mmcorej.CMMCore;
import org.micromanager.Studio;
import org.micromanager.events.PixelSizeAffineChangedEvent;
import org.micromanager.events.PixelSizeChangedEvent;
import org.micromanager.events.StagePositionChangedEvent;
import org.micromanager.events.XYStagePositionChangedEvent;
import org.micromanager.internal.utils.AffineUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.TextUtils;

/**
 * Simple class used to cache information that doesn't change very often.
 */
public class MMCache {
   private long width_;
   private long height_;
   private long bytesPerPixel_;
   private long imageBitDepth_;
   private double pixSizeUm_;
   private AffineTransform affineTransform_;
   private double zPos_;
   private double x_;
   private double y_;

   private String cameraLabel_ = "";
   private String shutterLabel_ = "";
   private String xyStageLabel_ = "";
   private String zStageLabel_ = "";

   static CMMCore core_;
   static MainFrame frame_;

   /**
    * Yet another cache.  This one seems mainly to be used by the info string
    * in the bottom of the main window.  TODO: evaluate.
    *
    * @param studio (singleton) Studion object.
    * @param frame  MainFrame.
    */
   @SuppressWarnings("LeakingThisInConstructor")
   public MMCache(Studio studio, MainFrame frame) {
      core_ = studio.core();
      frame_ = frame;
      studio.events().registerForEvents(this);
   }

   /**
    * Updates Cache with X,Y position of the Core-Stage.
    *
    * @param x X position of the Core-Stage.
    * @param y Y position of the Core-Stage.
    */
   public void updateXYPos(double x, double y) {
      x_ = x;
      y_ = y;
      updateInfoDisplay();
   }

   /**
    * Updates Cache with relative X,Y position of the Core-Stage.
    *
    * @param x relative change in X position of the Core-Stage.
    * @param y relative change in Y position of the Core-Stage.
    */
   public void updateXYPosRelative(double x, double y) {
      x_ += x;
      y_ += y;
      updateInfoDisplay();
   }

   /**
    * Updates XY stage position in the cache by reading values from hardware.
    */
   public void updateXYStagePosition() {
      double[] x = new double[1];
      double[] y = new double[1];
      try {
         if (xyStageLabel_.length() > 0) {
            core_.getXYPosition(xyStageLabel_, x, y);
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
      updateXYPos(x[0], y[0]);
   }

   public void updateZPos(double z) {
      zPos_ = z;
      updateInfoDisplay();
   }

   public void updateZPosRelative(double z) {
      zPos_ += z;
      updateInfoDisplay();
   }

   @Subscribe
   public void onPixelSizeChanged(PixelSizeChangedEvent event) {
      pixSizeUm_ = event.getNewPixelSizeUm();
      updateInfoDisplay();
   }

   @Subscribe
   public void onPixelSizeAffineChanged(PixelSizeAffineChangedEvent event) {
      affineTransform_ = event.getNewPixelSizeAffine();
      // we are not displaying the affine transform...
   }

   /**
    * Handles event signalling that a StagePosition changed.
    *
    * @param event Holds information about the stage and its new position.
    */
   @Subscribe
   public void onStagePositionChanged(StagePositionChangedEvent event) {
      if (event.getDeviceName().equals(zStageLabel_)) {
         updateZPos(event.getPos());
      }
   }

   /**
    * Handles event signalling that a XYStagePosition changed.
    *
    * @param event Holds information about the XY stage in its new position.
    */
   @Subscribe
   public void onXYStagePositionChanged(XYStagePositionChangedEvent event) {
      if (event.getDeviceName().equals(xyStageLabel_)) {
         updateXYPos(event.getXPos(), event.getYPos());
      }
   }

   /**
    * Updates the cache by reading values from the hardware.
    */
   public void refreshValues() {
      try {
         cameraLabel_ = core_.getCameraDevice();
         shutterLabel_ = core_.getShutterDevice();
         zStageLabel_ = core_.getFocusDevice();
         xyStageLabel_ = core_.getXYStageDevice();
         double zPos = 0.0;
         double[] x = new double[1];
         double[] y = new double[1];

         try {
            if (zStageLabel_.length() > 0) {
               zPos = core_.getPosition(zStageLabel_);
            }
            if (xyStageLabel_.length() > 0) {
               core_.getXYPosition(xyStageLabel_, x, y);
            }
         } catch (Exception e) {
            ReportingUtils.showError(e, "Failed to get stage position");
         }

         width_ = core_.getImageWidth();
         height_ = core_.getImageHeight();
         bytesPerPixel_ = core_.getBytesPerPixel();
         imageBitDepth_ = core_.getImageBitDepth();
         pixSizeUm_ = core_.getPixelSizeUm();
         affineTransform_ = AffineUtils.doubleToAffine(core_.getPixelSizeAffine());
         zPos_ = zPos;
         x_ = x[0];
         y_ = y[0];
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
      updateInfoDisplay();
   }

   /**
    * Updates the information line in the UI.
    */
   public void updateInfoDisplay() {
      String text = String.format(
            "Image info (from camera): %s X %s X %s bytes, Intensity range: %s bits, %s nm/px",
            width_, height_, bytesPerPixel_, imageBitDepth_,
            TextUtils.FMT0.format(pixSizeUm_ * 1000));
      if (zStageLabel_.length() > 0) {
         text += String.format(", Z=%s \u00b5m",
               TextUtils.removeNegativeZero(TextUtils.FMT2.format(zPos_)));
      }
      if (xyStageLabel_.length() > 0) {
         text += String.format(", XY=(%s,%s) \u00b5m",
               TextUtils.removeNegativeZero(TextUtils.FMT2.format(x_)),
               TextUtils.removeNegativeZero(TextUtils.FMT2.format(y_)));
      }
      frame_.updateInfoDisplay(text);
   }

   public double getStageX() {
      return x_;
   }

   public double getStageY() {
      return y_;
   }

   public double getStageZ() {
      return zPos_;
   }

   public int getImageBitDepth() {
      return (int) imageBitDepth_;
   }

   public double getPixelSizeUm() {
      return pixSizeUm_;
   }

   public AffineTransform getPixelSizeAffine() {
      return affineTransform_;
   }

   public String getCameraLabel() {
      return cameraLabel_;
   }

   public String getZStageLabel() {
      return zStageLabel_;
   }

   public String getXYStageLabel() {
      return xyStageLabel_;
   }

   public String getShutterLabel() {
      return shutterLabel_;
   }
}
