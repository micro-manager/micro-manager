
package org.micromanager.intelligentacquisition;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.text.TextPanel;
import ij.text.TextWindow;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import org.micromanager.api.MMWindow;


/**
 *
 * @author nico
 */
/**
 * KeyListener and MouseListenerclass for ResultsTable
 * When user selected a line in the ResulsTable and presses a key,
 * the corresponding image will move to the correct slice and draw a
 * symbol indicating where the object was found
 * 
 * Works only in conjunction with appropriate column names
 * Up and down keys also work as expected
 */
public class ResultsListener implements KeyListener, MouseListener{
  
   ImagePlus siPlus_;
   ResultsTable res_;
   TextWindow win_;
   TextPanel tp_;
   
   public ResultsListener(ImagePlus siPlus, ResultsTable res, TextWindow win) {
      siPlus_ = siPlus;
      res_ = res;
      win_ = win;
      tp_ = win.getTextPanel();
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

   /**
    * Move display to the position in which the object was found
    * and draw a symbol there
    */
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

         MMWindow mw = new MMWindow(siPlus_);
         if (mw.isMMWindow()) {
            try {
               int position = (int) res_.getValue(Terms.POSITION, row);
               mw.setPosition(position);
            } catch (Exception ex) {
            }
         }

         double x = (int) res_.getValue(Terms.X, row);
         double y = (int) res_.getValue(Terms.Y, row);

         GeneralPath path = new GeneralPath();
         drawCross(siPlus_, new Point2D.Double(x, y), path);
         siPlus_.setOverlay(path, Color.RED, new BasicStroke(1));
      }
   }

   /** 
    * Creates the symbols.  Symbol size will be a portion of the image size
    * (currently 5%)
    * 
    * @param imp - ImagePlus in which the symbol will be shown
    * @param p - Point around which the symbol will be centered
    * @param path - product of this function, i.e. use path to draw the symbol
    */
	void drawCross(ImagePlus imp, Point2D.Double p, GeneralPath path) {
      double es = 0.4;
      double x  = imp.getCalibration().getRawX(p.x);
      double y = imp.getCalibration().getRawY(p.y);
		int width=imp.getWidth() / 20;
		int height=imp.getHeight() / 20;
		path.moveTo(x, y - height);
		path.lineTo(x, y - es * height);
      path.moveTo(x, y + es * height);
      path.lineTo(x, y + height);
		path.moveTo(x - width, y);
      path.lineTo(x - es * width, y);
      path.moveTo(x + es * width, y);
		path.lineTo(x + width, y);	
	}
   
}
