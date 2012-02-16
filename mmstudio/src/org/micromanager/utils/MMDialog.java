///////////////////////////////////////////////////////////////////////////////
//FILE:          MMDialog.java
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

import java.awt.Frame;
import java.awt.Rectangle;
import java.util.prefs.Preferences;

import javax.swing.JDialog;
import org.micromanager.MMStudioMainFrame;

/**
 * Base class for the Micro-Manager dialogs.
 * Saves and restores window size and position.
 */
public class MMDialog extends JDialog {
   private static final long serialVersionUID = -3144618980027203294L;
   private Preferences prefs_;
   private static final String WINDOW_X = "mmdlg_y";
   private static final String WINDOW_Y = "mmdlg_x";
   private static final String WINDOW_WIDTH = "mmdlg_width";
   private static final String WINDOW_HEIGHT = "mmdlg_height";
   
   public MMDialog() {
      super();
      prefs_ = Preferences.userNodeForPackage(this.getClass());      
      setBackground(MMStudioMainFrame.getInstance().getBackgroundColor());
      
   }
   public MMDialog(Frame owner) {
      super(owner);
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      setBackground(MMStudioMainFrame.getInstance().getBackgroundColor());
   }
   
   protected void loadPosition(int x, int y, int width, int height) {
      setBounds(prefs_.getInt(WINDOW_X, x),
                prefs_.getInt(WINDOW_Y, y),
                prefs_.getInt(WINDOW_WIDTH, width),
                prefs_.getInt(WINDOW_HEIGHT, height));      
   }
   
   protected void loadPosition(int x, int y) {
      setLocation(prefs_.getInt(WINDOW_X, x),
                prefs_.getInt(WINDOW_Y, y));
   }

   protected void savePosition() {
      Rectangle r = getBounds();
      
      // save window position
      prefs_.putInt(WINDOW_X, r.x);
      prefs_.putInt(WINDOW_Y, r.y);
      prefs_.putInt(WINDOW_WIDTH, r.width);
      prefs_.putInt(WINDOW_HEIGHT, r.height);                  
   }
   
   public Preferences getPrefsNode() {
      return prefs_;
   }
   
   public void setPrefsNode(Preferences p) {
      prefs_ = p;
   }
   
   protected void setTopPosition() {
   }

}
