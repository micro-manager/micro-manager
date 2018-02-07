///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, 2018
//
// COPYRIGHT:    Regents of the University of California, 2018
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

package org.micromanager.internal.pixelcalibrator;

import ij.process.ImageProcessor;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Map;
import mmcorej.CMMCore;
import org.micromanager.Studio;
import org.micromanager.display.DisplayWindow;

/**
 *
 * @author NicoLocal
 */
public class ManualCalibrationThread extends Thread {
   private final Studio studio_;
   private final CMMCore core_;
   private final PixelCalibratorDialog dialog_;
   private final RectangleOverlay overlay_;
   
   private Map<Point2D.Double, Point2D.Double> pointPairs_;

   private AffineTransform result_ = null;
   private int progress_ = 0;
   private DisplayWindow liveWin_;
   private ImageProcessor referenceImage_;
   
   private Point2D.Double xy0_;

   private double x;
   private double y;
   private int w;
   private int h;
   private int side_small;

   private class CalibrationFailedException extends Exception {

      private static final long serialVersionUID = 4749723616733251885L;

      public CalibrationFailedException(String msg) {
         super(msg);
         if (xy0_ != null) {
            try {
               core_.setXYPosition( xy0_.x, xy0_.y);
               studio_.live().snap(true);
            } catch (Exception ex) {
               // annoying but at this point better to not bother the user 
               // with failure after failure
            }
         }
         if (overlay_ != null) {
            overlay_.setVisible(false);
         }
         if (liveWin_ != null) {
            liveWin_.setCustomTitle("Preview");
            if (overlay_ != null) {
               liveWin_.removeOverlay(overlay_);
            }
         }

      }
   }

   ManualCalibrationThread(Studio studio, PixelCalibratorDialog dialog) {
      studio_ = studio;
      core_ = studio_.getCMMCore();
      dialog_ = dialog;
      overlay_ = new RectangleOverlay();
      
      //studio_.live().getDisplay().addListener(listener, MIN_PRIORITY);
   }


   AffineTransform getResult() {
      return result_;
   }

   synchronized int getProgress() {
      return progress_;
   }

   private synchronized void incrementProgress() {
      progress_++;
      dialog_.update();
   }

   synchronized void setProgress(int value) {
      progress_ = value;
   }
}
