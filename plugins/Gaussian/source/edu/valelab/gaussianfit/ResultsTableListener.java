package edu.valelab.gaussianfit;


import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.text.TextPanel;
import ij.text.TextWindow;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *
 * @author nico
 */
/**
 * KeyListener and MouseListenerclass for ResultsTable
 * When user selected a line in the ResulsTable and presses a key,
 * the corresponding image will move to the correct slice and draw the ROI
 * that was used to calculate the Gaussian fit
 * Works only in conjunction with appropriate column names
 * Up and down keys also work as expected
 */
public class ResultsTableListener implements KeyListener, MouseListener{
   ImagePlus siPlus_;
   ResultsTable res_;
   TextWindow win_;
   TextPanel tp_;
   int hBS_;
   public ResultsTableListener(ImagePlus siPlus, ResultsTable res, TextWindow win, int halfBoxSize) {
      siPlus_ = siPlus;
      res_ = res;
      win_ = win;
      tp_ = win.getTextPanel();
      hBS_ = halfBoxSize;
   }
   public void keyPressed(KeyEvent e) {
      int key = e.getKeyCode();
      int row = tp_.getSelectionStart();
      if (key == KeyEvent.VK_J) {
         if (row > 0) {
            row--;
            tp_.setSelection(row, row);
         }
      } else if (key == KeyEvent.VK_K) {
         if  (row < tp_.getLineCount() - 1) {
            row++;
            tp_.setSelection(row, row);
         }
      }
      update();
   }
   public void keyReleased(KeyEvent e) {}
   public void keyTyped(KeyEvent e) {}

   public void mouseReleased(MouseEvent e) {
      update();
   }
   public void mousePressed(MouseEvent e) {}
   public void mouseClicked(MouseEvent e) {}
   public void mouseEntered(MouseEvent e) {};
   public void mouseExited(MouseEvent e) {};

   private void update() {
      if (siPlus_ == null) {
         return;
      }
      int row = tp_.getSelectionStart();
      if (row >= 0 && row < tp_.getLineCount()) {
         if (siPlus_.getWindow() != null) {
            if (siPlus_ != IJ.getImage()) {
               siPlus_.getWindow().toFront();
               win_.toFront();
            }
         } else {
            siPlus_ = null;
            return;
         }
         try {
            Boolean isMMWindow = false;
            Class<?> mmWin = Class.forName("org.micromanager.api.MMWindow");
            Constructor[] aCTors = mmWin.getDeclaredConstructors();
            aCTors[0].setAccessible(true);
            Object mw = aCTors[0].newInstance(siPlus_);
            Method[] allMethods = mmWin.getDeclaredMethods();

            // assemble all methods we need
            Method mIsMMWindow = null;
            Method mSetPosition = null;
            for (Method m : allMethods) {
               String mname = m.getName();
               if (mname.startsWith("isMMWindow")
                       && m.getGenericReturnType() == boolean.class) {
                  mIsMMWindow = m;
                  mIsMMWindow.setAccessible(true);
               }
               if (mname.startsWith("setPosition")) {
                  mSetPosition = m;
                  mSetPosition.setAccessible(true);
               }

            }

            if (mIsMMWindow != null && (Boolean) mIsMMWindow.invoke(mw)) {
               isMMWindow = true;
            }

            if (isMMWindow) { // MMImageWindow
               int position = (int) res_.getValue(Terms.POSITION, row);
               if (mSetPosition != null) {
                  mSetPosition.invoke(mw, position);
               }
            }
         } catch (ClassNotFoundException ex) {
         } catch (IllegalAccessException ex) {
            Logger.getLogger(ResultsTableListener.class.getName()).log(Level.SEVERE, null, ex);
         } catch (IllegalArgumentException ex) {
            Logger.getLogger(ResultsTableListener.class.getName()).log(Level.SEVERE, null, ex);
         } catch (InvocationTargetException ex) {
            Logger.getLogger(ResultsTableListener.class.getName()).log(Level.SEVERE, null, ex);
         } catch (InstantiationException ex) {
            Logger.getLogger(ResultsTableListener.class.getName()).log(Level.SEVERE, null, ex);
         } 

         int frame = (int) res_.getValue(Terms.FRAME, row);
         int slice = (int) res_.getValue(Terms.SLICE, row);
         int channel = (int) res_.getValue(Terms.CHANNEL, row);
         int x = (int) res_.getValue(Terms.XPIX, row);
         int y = (int) res_.getValue(Terms.YPIX, row);
         if (siPlus_.isHyperStack()) {
            siPlus_.setPosition(channel, slice, frame);
         } else {
            siPlus_.setPosition(Math.max(frame, slice));
         }
         siPlus_.setRoi(new Roi(x - hBS_, y - hBS_, 2 * hBS_, 2 * hBS_));
      }
   }
}
