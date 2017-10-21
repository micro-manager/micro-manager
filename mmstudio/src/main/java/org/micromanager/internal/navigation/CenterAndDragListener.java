/**
 * 
 */
package org.micromanager.internal.navigation;

import com.google.common.eventbus.Subscribe;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import org.micromanager.display.internal.event.DisplayMouseEvent;
import org.micromanager.events.XYStagePositionChangedEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * @author OD
 *
 */
public final class CenterAndDragListener {

   private final CMMCore core_;
   private boolean mirrorX_;
   private boolean mirrorY_;
   private boolean transposeXY_;
   private boolean correction_;
   private int lastX_, lastY_;

   public CenterAndDragListener(CMMCore core) {
      core_ = core;

      getOrientation();
   }

   /*
    * Ensures that the stage moves in the expected direction
    */
   public void getOrientation() {
      String camera = core_.getCameraDevice();
      if (camera == null) {
         JOptionPane.showMessageDialog(null, "This function does not work without a camera");
         return;
      }
      // we could possibly cache the current camera and only execute the code
      // below if the camera changed.  However, that will make it difficult 
      // to experiment with these settings, and it probably is not very expensive
      // to check every time
      try {
         String tmp = core_.getProperty(camera, "TransposeCorrection");
         correction_ = !(tmp.equals("0"));
         tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX());
         mirrorX_ = !(tmp.equals("0"));
         tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY());
         mirrorY_ = !(tmp.equals("0"));
         tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY());
         transposeXY_ = !(tmp.equals("0"));
      } catch (Exception exc) {
         ReportingUtils.showError(exc);
      }
   }

   /**
    * Handles mouse events and does the actual stage movement
    * TODO: factor out duplicated code
    * @param dme DisplauMouseEvent containing information about the mouse movement
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
               String xyStage = core_.getXYStageDevice();
               if (xyStage == null) {
                  return;
               }
               double pixSizeUm = core_.getPixelSizeUm();
               if (!(pixSizeUm > 0.0)) {
                  JOptionPane.showMessageDialog(null, 
                          "Please provide pixel size calibration data before using this function");
                  return;
               }

               int width = (int) core_.getImageWidth();
               int height = (int) core_.getImageHeight();

               // Calculate the center point of the event
               final Point center = new Point(
                       location.x + location.width / 2,
                       location.y + location.height / 2);

               // calculate needed relative movement
               double tmpXUm = ((0.5 * width) - center.x) * pixSizeUm;
               double tmpYUm = ((0.5 * height) - center.y) * pixSizeUm;

               double mXUm = tmpXUm;
               double mYUm = tmpYUm;
               // if camera does not correct image orientation, we'll correct for it here:
               if (!correction_) {
                  // Order: swapxy, then mirror axis
                  if (transposeXY_) {
                     mXUm = tmpYUm;
                     mYUm = tmpXUm;
                  }
                  if (mirrorX_) {
                     mXUm = -mXUm;
                  }
                  if (mirrorY_) {
                     mYUm = -mYUm;
                  }
               }

               moveStage(xyStage, mXUm, mYUm);

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
            String xyStage = core_.getXYStageDevice();
            if (xyStage == null || xyStage.equals("")) {
               return;
            }
            try {
               if (core_.deviceBusy(xyStage)) {
                  return;
               }
            } catch (Exception ex) {
               ReportingUtils.showError(ex);
               return;
            }

            double pixSizeUm = core_.getPixelSizeUm();
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

            tmpXUm *= pixSizeUm;
            tmpYUm *= pixSizeUm;
            double mXUm = tmpXUm;
            double mYUm = tmpYUm;
            // if camera does not correct image orientation, we'll correct for it here:
            if (!correction_) {
               // Order: swapxy, then mirror axis
               if (transposeXY_) {
                  mXUm = tmpYUm;
                  mYUm = tmpXUm;
               }
               if (mirrorX_) {
                  mXUm = -mXUm;
               }
               if (mirrorY_) {
                  mYUm = -mYUm;
               }
            }

            lastX_ = center2.x;
            lastY_ = center2.y;

            moveStage(xyStage, mXUm, mYUm);
            break;
      }
   }
    

   private void moveStage(String xyStage, double xRel, double yRel) {
      // Move the stage
      try {
         // TODO: make sure to not run on EDT?
         core_.setRelativeXYPosition(xyStage, xRel, yRel);
         double[] xs = new double[1];
         double[] ys = new double[1];
         core_.getXYPosition(xyStage, xs, ys);
         // studio_.updateXYStagePosition();
         DefaultEventManager.getInstance().post(
                 new XYStagePositionChangedEvent(xyStage, xs[0], ys[0]));
         // alternative, less correct but possibly faster:
         //      if (studio_ instanceof MMStudio) {
         //  ((MMStudio) studio_).updateXYPosRelative(mXUm, mYUm);
         // }
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

}