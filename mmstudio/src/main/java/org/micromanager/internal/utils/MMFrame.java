///////////////////////////////////////////////////////////////////////////////
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
package org.micromanager.internal.utils;

import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;


/**
 * Base class for Micro-Manager frame windows.
 * Saves and restores window size and position. 
 */
public class MMFrame extends JFrame {
   private static final long serialVersionUID = 1L;
   private final String prefPrefix_;
   private static final String WINDOW_X = "frame_x";
   private static final String WINDOW_Y = "frame_y";
   private static final String WINDOW_WIDTH = "frame_width";
   private static final String WINDOW_HEIGHT = "frame_height";
   
   public MMFrame() {
      super();
      prefPrefix_ = "";
   }

   public MMFrame(String prefPrefix) {
      super();
      prefPrefix_ = prefPrefix;
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
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      int prefX = profile.getInt(this.getClass(), prefPrefix_ + WINDOW_X, 0);
      int prefY = profile.getInt(this.getClass(), prefPrefix_ + WINDOW_Y, 0);
      if (GUIUtils.getGraphicsConfigurationContaining(prefX, prefY) == null) {
         // only reach this code if the pref coordinates are off screen
         profile.setInt(this.getClass(), prefPrefix_ + WINDOW_X, x);
         profile.setInt(this.getClass(), prefPrefix_ + WINDOW_Y, y);
      }
   }

   public void loadPosition(int x, int y, int width, int height) {
      ensureSafeWindowPosition(x, y);
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      setBounds(profile.getInt(this.getClass(), prefPrefix_ + WINDOW_X, x),
                profile.getInt(this.getClass(), prefPrefix_ + WINDOW_Y, y),
                profile.getInt(this.getClass(), prefPrefix_ + WINDOW_WIDTH, width),
                profile.getInt(this.getClass(), prefPrefix_ + WINDOW_HEIGHT, height));
   }

   public void loadPosition(int x, int y) {
      ensureSafeWindowPosition(x, y);
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      setBounds(profile.getInt(this.getClass(), prefPrefix_ + WINDOW_X, x),
                profile.getInt(this.getClass(), prefPrefix_ + WINDOW_Y, y),
                getWidth(),
                getHeight());
   }
   
   
    /**
    * Load window position and size from profile if possible.
    * If not possible then sets them from arguments
    * Attaches a listener to the window that will save the position when the
    * window closing event is received
    * @param x - X position of this dialog if preference value invalid
    * @param y - y position of this dialog if preference value invalid
    * @param width - width of this dialog if preference value invalid
    * @param height - height of this dialog if preference value invalid
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
    * Load window position and size from profile if possible.
    * If not possible then sets it from arguments
    * Attaches a listener to the window that will save the position when the
    * window closing event is received
    * @param x - X position of this dialog if preference value invalid
    * @param y - y position of this dialog if preference value invalid
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
      Rectangle r = getBounds();
      
      // save window position
      DefaultUserProfile profile = DefaultUserProfile.getInstance();
      profile.setInt(this.getClass(), prefPrefix_ + WINDOW_X, r.x);
      profile.setInt(this.getClass(), prefPrefix_ + WINDOW_Y, r.y);
      profile.setInt(this.getClass(), prefPrefix_ + WINDOW_WIDTH, r.width);
      profile.setInt(this.getClass(), prefPrefix_ + WINDOW_HEIGHT, r.height);
   }
   
         
   @Override
   public void dispose() {
      savePosition();
      super.dispose();
   }
}
