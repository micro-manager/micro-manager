///////////////////////////////////////////////////////////////////////////////
//FILE:          StaticInfo.java
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

package org.micromanager;

import com.google.common.eventbus.Subscribe;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.json.JSONException;

import org.micromanager.api.events.PixelSizeChangedEvent;
import org.micromanager.api.events.StagePositionChangedEvent;
import org.micromanager.api.events.XYStagePositionChangedEvent;

import org.micromanager.events.EventManager;

import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.TextUtils;

/**
 * Simple class used to cache information that doesn't change very often.
 */
class StaticInfo {
   static public long width_;
   static public long height_;
   static public long bytesPerPixel_;
   static public long imageBitDepth_;
   static public double pixSizeUm_;
   static public double zPos_;
   static public double x_;
   static public double y_;

   static public String cameraLabel_ = "";
   static public String shutterLabel_ = "";
   static public String xyStageLabel_ = "";
   static public String zStageLabel_ = "";

   static private CMMCore core_;
   static private MainFrame frame_;

   @SuppressWarnings("LeakingThisInConstructor")
   public StaticInfo(CMMCore core, MainFrame frame) {
      core_ = core;
      frame_ = frame;
      EventManager.register(this);
   }

   public void updateXYPos(double x, double y) {
      x_ = x;
      y_ = y;
      updateInfoDisplay();
   }
   public void updateXYPosRelative(double x, double y) {
      x_ += x;
      y_ += y;
      updateInfoDisplay();
   }
   public void getNewXYStagePosition() {
      double x[] = new double[1];
      double y[] = new double[1];
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
   public void onStagePositionChanged(StagePositionChangedEvent event) {
      updateZPos(event.getPos());
   }

   @Subscribe
   public void onXYStagePositionChanged(XYStagePositionChangedEvent event) {
      updateXYPos(event.getXPos(), event.getYPos());
   }

   public void refreshValues() {
      try {
         cameraLabel_ = core_.getCameraDevice();
         shutterLabel_ = core_.getShutterDevice();
         zStageLabel_ = core_.getFocusDevice();
         xyStageLabel_ = core_.getXYStageDevice();
         double zPos = 0.0;
         double x[] = new double[1];
         double y[] = new double[1];

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
         zPos_ = zPos;
         x_ = x[0];
         y_ = y[0];
      }
      catch (Exception e) {
         ReportingUtils.showError(e);
      }
      updateInfoDisplay();
   }

   public void updateInfoDisplay() {
      String text = "Image info (from camera): " + width_ + " X " + height_ + " X "
            + bytesPerPixel_ + ", Intensity range: " + imageBitDepth_ + " bits";
      text += ", " + TextUtils.FMT0.format(pixSizeUm_ * 1000) + "nm/pix";
      if (zStageLabel_.length() > 0) {
         text += ", Z=" + TextUtils.FMT2.format(zPos_) + "um";
      }
      if (xyStageLabel_.length() > 0) {
         text += ", XY=(" + TextUtils.FMT2.format(x_) + "," + TextUtils.FMT2.format(y_) + ")um";
      }
      frame_.updateInfoDisplay(text);
   }

   public void addStagePositionToTags(TaggedImage ti) throws JSONException {
      if (xyStageLabel_.length() > 0) {
         MDUtils.setXPositionUm(ti.tags, x_);
         MDUtils.setYPositionUm(ti.tags, y_);
      }
      if (zStageLabel_.length() > 0) {
         MDUtils.setZPositionUm(ti.tags, zPos_);
      }
   }
}
