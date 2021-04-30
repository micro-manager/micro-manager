/*
Copyright (c) 2012-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */

package edu.ucsf.valelab.gaussianfit;

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
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.display.DisplayWindow;

/**
 * @author nico
 */

/**
 * KeyListener and MouseListenerclass for ResultsTable When user selected a line in the ResulsTable
 * and presses a key, the corresponding image will move to the correct slice and draw the ROI that
 * was used to calculate the Gaussian fit Works only in conjunction with appropriate column names Up
 * and down keys also work as expected
 */
public class ResultsTableListener implements KeyListener, MouseListener {

   private final ImagePlus siPlus_;
   private final ResultsTable res_;
   private final TextWindow win_;
   private final TextPanel tp_;
   private final DisplayWindow dw_;
   private final int hBS_;
   private int key_;
   private int row_;

   public ResultsTableListener(DisplayWindow dw, ImagePlus siPlus, ResultsTable res, TextWindow win,
         int halfBoxSize) {
      dw_ = dw;
      siPlus_ = siPlus;
      res_ = res;
      win_ = win;
      tp_ = win.getTextPanel();
      hBS_ = halfBoxSize;
      /*
      t_ = new Timer(200, new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            row_ = tp_.getSelectionStart();
            if (key_ == KeyEvent.VK_J) {
               if (row_ > 0) {
                  row_--;
                  tp_.setSelection(row_, row_);
               }
            } else if (key_ == KeyEvent.VK_K) {
               if (row_ < tp_.getLineCount() - 1) {
                  row_++;
                  tp_.setSelection(row_, row_);
               }
            }
            update();
         }
      });
*/

   }


   @Override
   public void keyPressed(KeyEvent e) {
      key_ = e.getKeyCode();
      interpretKeyPress();
      //t_.start();
   }

   @Override
   public void keyReleased(KeyEvent e) {
      //t_.stop();
   }

   @Override
   public void keyTyped(KeyEvent e) {
      //t_.stop();
   }

   @Override
   public void mouseReleased(MouseEvent e) {
      update();
   }

   @Override
   public void mousePressed(MouseEvent e) {
   }

   @Override
   public void mouseClicked(MouseEvent e) {
   }

   @Override
   public void mouseEntered(MouseEvent e) {
   }

   ;

   @Override
   public void mouseExited(MouseEvent e) {
   }

   private void interpretKeyPress() {
      row_ = tp_.getSelectionStart();
      if (key_ == KeyEvent.VK_J) {
         if (row_ > 0) {
            row_--;
            tp_.setSelection(row_, row_);
         }
      } else if (key_ == KeyEvent.VK_K) {
         if (row_ < tp_.getLineCount() - 1) {
            row_++;
            tp_.setSelection(row_, row_);
         }
      }
      update();
   }

   private void update() {
      if (siPlus_ == null && dw_ == null) {
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
            return;
         }
         int frame = Integer.parseInt(res_.getStringValue(Terms.FRAME, row));
         int slice = Integer.parseInt(res_.getStringValue(Terms.SLICE, row));
         int channel = Integer.parseInt(res_.getStringValue(Terms.CHANNEL, row));
         int pos = Integer.parseInt(res_.getStringValue(Terms.POSITION, row));
         int x = Integer.parseInt(res_.getStringValue(Terms.XPIX, row));
         int y = Integer.parseInt(res_.getStringValue(Terms.YPIX, row));

         if (dw_ != null) {
            Coords.CoordsBuilder builder = Coordinates.builder();
            Coords coords = builder.channel(channel - 1).time(frame - 1).
                  z(slice - 1).stagePosition(pos - 1).build();
            dw_.setDisplayPosition(coords);
         } else if (siPlus_.isHyperStack()) {
            siPlus_.setPosition(channel, slice, frame);
         } else {
            siPlus_.setPosition(Math.max(frame, slice));
         }
         siPlus_.setRoi(new Roi(x - hBS_, y - hBS_, 2 * hBS_, 2 * hBS_));
      }
   }
}
