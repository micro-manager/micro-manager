/**
 * 
 */
package org.micromanager.internal.navigation;

import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Toolbar;
import ij.WindowManager;

import java.awt.Event;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Arrays;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * @author OD
 *
 */
public class CenterAndDragListener implements MouseListener, 
        MouseMotionListener, WindowListener {
      private CMMCore core_;
      private MMStudio studio_;
      private ImageCanvas canvas_;
      private static boolean isRunning_ = false;
      private boolean mirrorX_;
      private boolean mirrorY_;
      private boolean transposeXY_;
      private boolean correction_;
      private int lastX_, lastY_;

      public CenterAndDragListener(MMStudio gui) {
         studio_ = gui;
         core_ = gui.getMMCore();
      }

      public void start () {
         if (isRunning_)
            return;

         isRunning_ = true;

         // Get a handle to the Live window
         ImageWindow win = WindowManager.getCurrentWindow();
         if (win != null) {
            attach(win);
         }
      }

      public void stop() {
         if (canvas_ != null) {
            canvas_.removeMouseListener(this);
            canvas_.removeMouseMotionListener(this);
         }
         isRunning_ = false;
      }

      public boolean isRunning() {
         return isRunning_;
      }

      /*
       * Attached a MouseLisetener and MouseMotionListener to the Live Window
       */
      public void attach(ImageWindow win) {
         if (win == null)
            return;
         if (!isRunning_)
            return;
         canvas_ = win.getCanvas();
         if (!Arrays.asList(canvas_.getMouseListeners()).contains(this)) {
            canvas_.addMouseListener(this);
         }
         if (!Arrays.asList(canvas_.getMouseMotionListeners()).contains(this)) {
            canvas_.addMouseMotionListener(this);
         }
         if (!Arrays.asList(win.getWindowListeners()).contains(this)) {
            win.addWindowListener(this);
         }

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
         try {
            String tmp = core_.getProperty(camera, "TransposeCorrection");
            if (tmp.equals("0")) 
               correction_ = false;
            else 
               correction_ = true;
            tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX());
            if (tmp.equals("0")) 
               mirrorX_ = false;
            else 
               mirrorX_ = true;
            tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY());
            if (tmp.equals("0")) 
               mirrorY_ = false;
            else 
               mirrorY_ = true;
            tmp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY());
            if (tmp.equals("0")) 
               transposeXY_ = false;
            else 
               transposeXY_ = true;
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

               // Move the stage
               try {
                  core_.setRelativeXYPosition(xyStage, mXUm, mYUm);
               } catch (Exception ex) {
                  ReportingUtils.showError(ex);
                  return;
               }

               // refresh GUI x,y
               studio_.updateXYStagePosition();
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
         if (xyStage == null && !xyStage.equals(""))
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

         // Move the stage
         try {
            core_.setRelativeXYPosition(xyStage, mXUm, mYUm);
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
            return;
         }
         lastX_ = cX;
         lastY_ = cY;

         studio_.updateXYPosRelative(mXUm, mYUm);

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
