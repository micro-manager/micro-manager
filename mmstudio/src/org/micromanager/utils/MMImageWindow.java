///////////////////////////////////////////////////////////////////////////////
//FILE:          MMImageWindow.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 1, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
// CVS:          $Id$
//
package org.micromanager.utils;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;

import javax.swing.AbstractButton;
import javax.swing.JButton;

import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.io.FileSaver;

/**
 * ImageJ compatible image window.
 * Derived from the original ImageJ class.
 */
public class MMImageWindow extends ImageWindow {
   private static final long serialVersionUID = 1L;
   Panel buttonPanel_;
   ContrastSettings contrastSettings8_;
   ContrastSettings contrastSettings16_;
   LUTDialog contrastDlg_;
   Preferences prefs_;
   private static final String WINDOW_X = "mmimg_y";
   private static final String WINDOW_Y = "mmimg_x";
   private static final String WINDOW_WIDTH = "mmimg_width";
   private static final String WINDOW_HEIGHT = "mmimg_height";
   
   public MMImageWindow(ImagePlus imp) {
      super(imp);
      
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      Point p = getLocation();
      loadPosition(p.x, p.y);
      
      buttonPanel_ = new Panel();
      
      AbstractButton saveButton = new JButton("Save");
      saveButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            new FileSaver(getImagePlus()).save();
         }
      });
      buttonPanel_.add(saveButton);
      
      AbstractButton saveAsButton = new JButton("Save As...");
      saveAsButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            new FileSaver(getImagePlus()).saveAsTiff();
         }
      });
      buttonPanel_.add(saveAsButton);
            
      add(buttonPanel_);
      pack();
      
      // add window listeners
      addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent e) {
            savePosition();
            if (contrastDlg_ != null)
               contrastDlg_.dispose();
         }
      });   
   }
   
   public void setContrastSettings(ContrastSettings s8, ContrastSettings s16) {
      contrastSettings8_ = s8;
      contrastSettings16_ = s16;
   }
   
   public ContrastSettings getCurrentContrastSettings() {
      if (getImagePlus().getBitDepth() == 8)
         return contrastSettings8_;
      else
         return contrastSettings16_;      
   }
   
   public void loadPosition(int x, int y) {
      setLocation(prefs_.getInt(WINDOW_X, x),
            prefs_.getInt(WINDOW_Y, y));      
   }
   
   public void savePosition() {
      Rectangle r = getBounds();
      
      // save window position
      prefs_.putInt(WINDOW_X, r.x);
      prefs_.putInt(WINDOW_Y, r.y);
      prefs_.putInt(WINDOW_WIDTH, r.width);
      prefs_.putInt(WINDOW_HEIGHT, r.height);                  
   }
   
}
