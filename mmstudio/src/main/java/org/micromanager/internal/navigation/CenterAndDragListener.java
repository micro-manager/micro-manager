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

package org.micromanager.internal.navigation;

import com.google.common.eventbus.Subscribe;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.concurrent.ExecutorService;
import javax.swing.JOptionPane;
import org.micromanager.Studio;
import mmcorej.MMCoreJ;
import org.micromanager.display.internal.event.DisplayMouseEvent;
import org.micromanager.events.XYStagePositionChangedEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.internal.utils.AffineUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * @author OD, nico
 *
 */
public final class CenterAndDragListener {

   private final Studio studio_;
   private final ExecutorService executorService_;
   private final StageMover stageMover_;
   private boolean mirrorX_;
   private boolean mirrorY_;
   private boolean transposeXY_;
   private boolean correction_;
   private AffineTransform affineTransform_;
   
   private int lastX_, lastY_;

   public CenterAndDragListener(final Studio studio, 
         final ExecutorService executorService) {
      studio_ = studio;
      executorService_ = executorService;
      stageMover_ = new StageMover();

      getOrientation();
   }
   
   private class StageMover implements Runnable {

      private String xyStage_;
      private double xRel_;                                   
      private double yRel_;

      /**
       * Always call this function first before executing the Runnable
       * Otherwise, the previous movement will be repeated...
       * @param xyStage - Stage to move
       * @param xRel - relative movement in X in microns
       * @param yRel - relative movement in Y in microns
       */
      public void setPosition(String xyStage, double xRel, double yRel) {
         xyStage_ = xyStage;
         xRel_ = xRel;
         yRel_ = yRel;
      }

      @Override
      public void run() {

         // Move the stage
         try {
            studio_.core().setRelativeXYPosition(xyStage_, xRel_, yRel_);
            double[] xs = new double[1];
            double[] ys = new double[1];
            studio_.core().getXYPosition(xyStage_, xs, ys);
            studio_.events().post(
                    new XYStagePositionChangedEvent(xyStage_, xs[0], ys[0]));
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
         }
      }
   }


   /*
    * Ensures that the stage moves in the expected direction
    */
   public void getOrientation() {
      String camera = studio_.core().getCameraDevice();
      if (camera == null) {
         JOptionPane.showMessageDialog(null, "This function does not work without a camera");
         return;
      }
      // If there is an affine transform, use that, otherwise fallbakc to 
      // the old mechanism
      try {
         double pixelSize = studio_.core().getPixelSizeUm();
         try {
            affineTransform_ = AffineUtils.doubleToAffine(studio_.core().getPixelSizeAffine(true));
            if (Math.abs(pixelSize
                    - AffineUtils.deducePixelSize(affineTransform_)) > 0.1 * pixelSize) {
               // affine transform does not correspond to pixelSize, so do not 
               // trust it and fallback to old mechanism
               affineTransform_ = null;
            }
         } catch (Exception ex) {
            ReportingUtils.logError("Failed to find affine transform");
         }
      } catch (Exception exc) {
         ReportingUtils.showError(exc);
      }
      if (affineTransform_ == null) {
         // we could possibly cache the current camera and only execute the code
         // below if the camera changed.  However, that will make it difficult 
         // to experiment with these settings, and it probably is not very expensive
         // to check every time
         try {
            String tmp = studio_.core().getProperty(camera, "TransposeCorrection");
            correction_ = !(tmp.equals("0"));
            tmp = studio_.core().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX());
            mirrorX_ = !(tmp.equals("0"));
            tmp = studio_.core().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY());
            mirrorY_ = !(tmp.equals("0"));
            tmp = studio_.core().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY());
            transposeXY_ = !(tmp.equals("0"));
         } catch (Exception exc) {
            ReportingUtils.showError(exc);
         }
      }
   }

   /**
    * Handles mouse events and does the actual stage movement
    * TODO: factor out duplicated code
    * @param dme DisplayMouseEvent containing information about the mouse movement
    * TODO: this does not take into account multiple cameras in different displays
    * 
    */
   @Subscribe
   public void onDisplayMouseEvent(DisplayMouseEvent dme) {
      // only take action when the Hand tool is selected
      if (dme.getToolId() != ij.gui.Toolbar.HAND) {
         return;
      }
      switch (dme.getEvent().getID()) {
         case MouseEvent.MOUSE_CLICKED:
            if (dme.getEvent().getClickCount() >= 2) {
               // double clik, center the stage
               Rectangle location = dme.getLocation();

               // Get needed info from core
               getOrientation();
               String xyStage = studio_.core().getXYStageDevice();
               if (xyStage == null) {
                  return;
               }
               double pixSizeUm = studio_.core().getPixelSizeUm(true);
               if (!(pixSizeUm > 0.0)) {
                  JOptionPane.showMessageDialog(null, 
                          "Please provide pixel size calibration data before using this function");
                  return;
               }

               int width = (int) studio_.core().getImageWidth();
               int height = (int) studio_.core().getImageHeight();

               // Calculate the center point of the event
               final Point center = new Point(
                       location.x + location.width / 2,
                       location.y + location.height / 2);

               // calculate needed relative movement in pixels
               double tmpXUm = (0.5 * width) - center.x;
               double tmpYUm = (0.5 * height) - center.y;

               Point2D stagePos = toStageSpace(pixSizeUm, tmpXUm, tmpYUm);

               moveStage(xyStage, stagePos.getX(), stagePos.getY());

            }
            break;
         case MouseEvent.MOUSE_PRESSED:
            // record start position for a drag
            // Calculate the center point of the event
            final Point center = new Point(
                    dme.getLocation().x + dme.getLocation().width / 2,
                    dme.getLocation().y + dme.getLocation().height / 2);
            lastX_ = center.x;
            lastY_ = center.y;
            break;
         case MouseEvent.MOUSE_DRAGGED:
            // compare to start position and move stage
            // Get needed info from core
            // (is it really needed to run this every time?)
            getOrientation();
            String xyStage = studio_.core().getXYStageDevice();
            if (xyStage == null || xyStage.equals("")) {
               return;
            }
            try {
               if (studio_.core().deviceBusy(xyStage)) {
                  return;
               }
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
               return;
            }

            double pixSizeUm = studio_.core().getPixelSizeUm();
            if (!(pixSizeUm > 0.0)) {
               JOptionPane.showMessageDialog(null, "Please provide pixel size calibration data before using this function");
               return;
            }

            // Get coordinates of event
            final Point center2 = new Point(
                    dme.getLocation().x + dme.getLocation().width / 2,
                    dme.getLocation().y + dme.getLocation().height / 2);

            // calculate needed relative movement
            double tmpXUm = center2.x - lastX_;
            double tmpYUm = center2.y - lastY_;

            Point2D stagePos = toStageSpace(pixSizeUm, tmpXUm, tmpYUm);

            lastX_ = center2.x;
            lastY_ = center2.y;

            moveStage(xyStage, stagePos.getX(), stagePos.getY());
            break;
      }
   }

   private void moveStage(String xyStage, double xRel, double yRel) {
      stageMover_.setPosition(xyStage, xRel, yRel);
      executorService_.submit(stageMover_);
   }

   /**
    * Converts Camera-Pixel Space to Stage-micron space
    * Use the affine transform when available, otherwise uses pixelSize
    * and orientation booleans, determined using the getOrientation function.
    * 
    * @param pixSizeUm - pixelSize in micron
    * @param x - Desired x movement in pixels
    * @param y - Desired y movement in pixels
    * @return - Needed stage movement as a Point2D.Double
    */
   private Point2D toStageSpace(double pixSizeUm, double x, double y) {
      Point2D dest = new Point2D.Double();
      if (affineTransform_ != null) {
         Point2D source = new Point2D.Double(x, y);
         affineTransform_.transform(source, dest);
         // not sure why, but for the stage movement to be correct, we need 
         // to invert both axes"
         dest.setLocation(-dest.getX(), -dest.getY());
      } else {
         // if camera does not toStageSpace image orientation, we'll toStageSpace for it here:
         dest.setLocation(x * pixSizeUm, y * pixSizeUm);
         if (!correction_) {
            // Order: swapxy, then mirror axis
            if (transposeXY_) {
               dest.setLocation(y * pixSizeUm, x * pixSizeUm);
            }
            if (mirrorX_) {
               dest.setLocation(-dest.getX(), dest.getY());
            }
            if (mirrorY_) {
               dest.setLocation(dest.getX(), -dest.getY());
            }
         }
      }
      
      return dest;
   }



}