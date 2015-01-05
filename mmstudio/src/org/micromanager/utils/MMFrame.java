///////////////////////////////////////////////////////////////////////////////
//FILE:          MMFrame.java
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

import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;

import javax.swing.JFrame;

import org.micromanager.MMStudio;

/**
 * Base class for Micro-Manager frame windows.
 * Saves and restores window size and position. 
 */
public class MMFrame extends JFrame {
   private static final long serialVersionUID = 1L;
   private Preferences prefs_;
   private static final String WINDOW_X = "frame_x";
   private static final String WINDOW_Y = "frame_y";
   private static final String WINDOW_WIDTH = "frame_width";
   private static final String WINDOW_HEIGHT = "frame_height";
   
   public MMFrame() {
      super();
      finishConstructor();
   }
   
      private void finishConstructor() {
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      MMStudio mfr = MMStudio.getInstance();
      if (mfr != null) {
         mfr.addMMBackgroundListener(this);
    	   setBackground(mfr.getBackgroundColor());
      }
   }

   /**
    * Checks whether WINDOW_X and WINDOW_Y coordinates are on the screen(s).
    * If not then it sets the prefs to the values specified.
    * Accounts for screen size changes between invocations or if screen
    * is removed (e.g. had 2 monitors and go to 1).
    * TODO: this code is duplicated between here and MMDialog.
    * @param x new WINDOW_X position if current value isn't valid
    * @param y new WINDOW_Y position if current value isn't valid
    */
   private void ensureSafeWindowPosition(int x, int y) {
      int prefX = prefs_.getInt(WINDOW_X, 0);
      int prefY = prefs_.getInt(WINDOW_Y, 0);
      if (GUIUtils.getGraphicsConfigurationContaining(prefX, prefY) == null) {
         // only reach this code if the pref coordinates are off screen
         prefs_.putInt(WINDOW_X, x);
         prefs_.putInt(WINDOW_Y, y);
      }
   }

   public void loadPosition(int x, int y, int width, int height) {
      if (prefs_ == null)
         return;

      ensureSafeWindowPosition(x, y);
      setBounds(prefs_.getInt(WINDOW_X, x),
                prefs_.getInt(WINDOW_Y, y),
                prefs_.getInt(WINDOW_WIDTH, width),
                prefs_.getInt(WINDOW_HEIGHT, height));      
   }

   public void loadPosition(int x, int y) {
      if (prefs_ == null)
         return;
      
      ensureSafeWindowPosition(x, y);
      setBounds(prefs_.getInt(WINDOW_X, x),
                prefs_.getInt(WINDOW_Y, y),
                getWidth(),
                getHeight());      
   }
   
   
    /**
    * Load window position and size from preferences
    * Makes sure that the window can be displayed
    * Attaches a listener to the window that will save the position when the
    * window closing event is received
    * @param x - X position of this dialog
    * @param y - y position of this dialog
    * @param width - width of this dialog
    * @param height - height of this dialog
    */
   protected void loadAndRestorePosition(int x, int y, int width, int height) {
      loadPosition(x, y, width, height);
      this.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            savePosition();
         }
      }
      );
   }
   
    /**
    * Load window position and size from preferences
    * Makes sure that the window can be displayed
    * Attaches a listener to the window that will save the position when the
    * window closing event is received
    * @param x - X position of this dialog
    * @param y - y position of this dialog
    */
   protected void loadAndRestorePosition(int x, int y) {
      loadPosition(x, y);
      this.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            savePosition();
         }
      }
      );
   }
   

   public void savePosition() {
      if (prefs_ == null)
         return;
      
      Rectangle r = getBounds();
      
      // save window position
      prefs_.putInt(WINDOW_X, r.x);
      prefs_.putInt(WINDOW_Y, r.y);
      prefs_.putInt(WINDOW_WIDTH, r.width);
      prefs_.putInt(WINDOW_HEIGHT, r.height);                  
   }
   
         
   @Override
   public void dispose() {
      savePosition();
      super.dispose();
   }
   
   
   public Preferences getPrefsNode() {
      return prefs_;
   }
   
   public void setPrefsNode(Preferences prefs) {
      prefs_ = prefs;
   }
  
}
