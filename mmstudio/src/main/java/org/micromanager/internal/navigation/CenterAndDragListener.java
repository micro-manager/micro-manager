/**
 * 
 */
package org.micromanager.internal.navigation;

import ij.gui.ImageCanvas;
import ij.gui.Toolbar;
import java.awt.Event;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import org.micromanager.Studio;
import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.events.XYStagePositionChangedEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * @author OD
 *
 */
public final class CenterAndDragListener implements MouseListener,
        MouseMotionListener, WindowListener {

   private final CMMCore core_;
   private final Studio studio_;
   private ImageCanvas canvas_;
   private boolean mirrorX_;
   private boolean mirrorY_;
   private boolean transposeXY_;
   private boolean correction_;
   private int lastX_, lastY_;

   public CenterAndDragListener(CMMCore core, Studio studio,
         DisplayController display) {
      studio_ = studio;
      core_ = core;
      // TODO
      
      /*
      canvas_ = display.getCanvas();
      if (!Arrays.asList(canvas_.getMouseListeners()).contains(this)) {
         canvas_.addMouseListener(this);
      }
      if (!Arrays.asList(canvas_.getMouseMotionListeners()).contains(this)) {
         canvas_.addMouseMotionListener(this);
      }
      if (!Arrays.asList(display.getAsWindow().getWindowListeners()).contains(this)) {
         display.getAsWindow().addWindowListener(this);
      }

      getOrientation();
      */
   }

   public void stop() {
      if (canvas_ != null) {
         canvas_.removeMouseListener(this);
         canvas_.removeMouseMotionListener(this);
      }
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
      try {
         String tmp = core_.getProperty(camera, "TransposeCorrection");
         correction_ = !(tmp.equals("0"));
         tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX());
         mirrorX_ = !(tmp.equals("0"));
         tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY());
         mirrorY_ = !(tmp.equals("0"));
         tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY());
         transposeXY_ = !(tmp.equals("0"));
      } catch(Exception exc) {
         ReportingUtils.showError(exc);
      }
   }

   @Override
   public void mouseClicked(MouseEvent e) {
      if (Toolbar.getInstance() != null) {
         if (Toolbar.getToolId() == Toolbar.HAND) {
            // right click and single click: ignore
            int nc=   e.getClickCount();
            if ((e.getModifiers() & Event.META_MASK) != 0 || nc < 2) 
                return;

            // Get needed info from core
            getOrientation();
            String xyStage = core_.getXYStageDevice();
            if (xyStage == null)
               return;
            double pixSizeUm = core_.getPixelSizeUm();
            if (! (pixSizeUm > 0.0)) {
               JOptionPane.showMessageDialog(null, "Please provide pixel size calibration data before using this function");
               return;
            }

            int width = (int) core_.getImageWidth();
            int height = (int) core_.getImageHeight();

            // Get coordinates of event
            int x = e.getX();
            int y = e.getY();
            int cX = canvas_.offScreenX(x);
            int cY = canvas_.offScreenY(y);

            // calculate needed relative movement
            double tmpXUm = ((0.5 * width) - cX) * pixSizeUm;
            double tmpYUm = ((0.5 * height) - cY) * pixSizeUm;

            double mXUm = tmpXUm;
            double mYUm = tmpYUm;
            // if camera does not correct image orientation, we'll correct for it here:
            if (!correction_) {
               // Order: swapxy, then mirror axis
               if (transposeXY_) {mXUm = tmpYUm; mYUm = tmpXUm;}
               if (mirrorX_) {mXUm = -mXUm;}
               if (mirrorY_) {mYUm = -mYUm;}
            }

            moveStage (xyStage, mXUm, mYUm);
            
         }
      }
   } 


   // Mouse listener implementation
   @Override
   public void mousePressed(MouseEvent e) {
      // Get the starting coordinate for the dragging
      int x = e.getX();
      int y = e.getY();
      lastX_ = canvas_.offScreenX(x);
      lastY_ = canvas_.offScreenY(y);
   }

   @Override
   public void mouseDragged(MouseEvent e) {
      if ((e.getModifiers() & Event.META_MASK) != 0) // right click: ignore
         return;

      // only respond when the Hand tool is selected in the IJ Toolbat
      if (Toolbar.getInstance() != null) {
      if (Toolbar.getToolId() != Toolbar.HAND)
            return;
      }

      // Get needed info from core
      // (is it really needed to run this every time?)
      getOrientation();
      String xyStage = core_.getXYStageDevice();
      if (xyStage == null || xyStage.equals(""))
         return;
      try {
         if (core_.deviceBusy(xyStage))
            return;
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
         return;
      }

      double pixSizeUm = core_.getPixelSizeUm();
      if (! (pixSizeUm > 0.0)) {
         JOptionPane.showMessageDialog(null, "Please provide pixel size calibration data before using this function");
         return;
      }

      // Get coordinates of event
      int x = e.getX();
      int y = e.getY();
      int cX = canvas_.offScreenX(x);
      int cY = canvas_.offScreenY(y);

      // calculate needed relative movement
      double tmpXUm = cX - lastX_;
      double tmpYUm = cY - lastY_;

      tmpXUm *= pixSizeUm;
      tmpYUm *= pixSizeUm;
      double mXUm = tmpXUm;
      double mYUm = tmpYUm;
      // if camera does not correct image orientation, we'll correct for it here:
      if (!correction_) {
         // Order: swapxy, then mirror axis
         if (transposeXY_) {mXUm = tmpYUm; mYUm = tmpXUm;}
         if (mirrorX_) {mXUm = -mXUm;}
         if (mirrorY_) {mYUm = -mYUm;}
      }
      
      lastX_ = cX;
      lastY_ = cY;

      moveStage (xyStage, mXUm, mYUm);     
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

   @Override
   public void mouseReleased(MouseEvent e) {}
   @Override
   public void mouseExited(MouseEvent e) {}
   @Override
   public void mouseEntered(MouseEvent e) {}
   @Override
   public void mouseMoved(MouseEvent e) {}

   @Override
   public void windowOpened(WindowEvent we) {
   }

      @Override
   public void windowClosing(WindowEvent we) {
   }

      @Override
   public void windowClosed(WindowEvent we) {
      stop();
   }

      @Override
   public void windowIconified(WindowEvent we) {
   }

      @Override
   public void windowDeiconified(WindowEvent we) {
   }

      @Override
   public void windowActivated(WindowEvent we) {
   }

      @Override
   public void windowDeactivated(WindowEvent we) {
   }
}
