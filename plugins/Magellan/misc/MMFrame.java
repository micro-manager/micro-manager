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
// CVS:          $Id: MMFrame.java 14898 2015-01-08 18:52:48Z cweisiger $
//
package misc;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;
import javax.swing.JFrame;


/**
 * Base class for Micro-Manager frame windows.
 * Saves and restores window size and position. 
 */
public class MMFrame extends JFrame {
   private static final long serialVersionUID = 1L;
   private Preferences prefs_;
   private final String prefPrefix_;
   private static final String WINDOW_X = "frame_x";
   private static final String WINDOW_Y = "frame_y";
   private static final String WINDOW_WIDTH = "frame_width";
   private static final String WINDOW_HEIGHT = "frame_height";
   
   public MMFrame() {
      super();
      finishConstructor();
      prefPrefix_ = "";
   }

   public MMFrame(String prefPrefix) {
      super();
      finishConstructor();
      prefPrefix_ = prefPrefix;
   }
   
      private void finishConstructor() {
      prefs_ = Preferences.userNodeForPackage(this.getClass());
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
      int prefX = prefs_.getInt(prefPrefix_ + WINDOW_X, 0);
      int prefY = prefs_.getInt(prefPrefix_ + WINDOW_Y, 0);
      if (getGraphicsConfigurationContaining(prefX, prefY) == null) {
         // only reach this code if the pref coordinates are off screen
         prefs_.putInt(prefPrefix_ + WINDOW_X, x);
         prefs_.putInt(prefPrefix_ + WINDOW_Y, y);
      }
   }

   public void loadPosition(int x, int y, int width, int height) {
      if (prefs_ == null)
         return;

      ensureSafeWindowPosition(x, y);
      setBounds(prefs_.getInt(prefPrefix_ + WINDOW_X, x),
                prefs_.getInt(prefPrefix_ + WINDOW_Y, y),
                prefs_.getInt(prefPrefix_ + WINDOW_WIDTH, width),
                prefs_.getInt(prefPrefix_ + WINDOW_HEIGHT, height));
   }

   public void loadPosition(int x, int y) {
      if (prefs_ == null)
         return;
      
      ensureSafeWindowPosition(x, y);
      setBounds(prefs_.getInt(prefPrefix_ + WINDOW_X, x),
                prefs_.getInt(prefPrefix_ + WINDOW_Y, y),
                getWidth(),
                getHeight());
   }
   
   
    /**
    * Load window position and size from preferences if possible.
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
    * Load window position and size from preferences if possible.
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
      if (prefs_ == null)
         return;
      
      Rectangle r = getBounds();
      
      // save window position
      prefs_.putInt(prefPrefix_ + WINDOW_X, r.x);
      prefs_.putInt(prefPrefix_ + WINDOW_Y, r.y);
      prefs_.putInt(prefPrefix_ + WINDOW_WIDTH, r.width);
      prefs_.putInt(prefPrefix_ + WINDOW_HEIGHT, r.height);
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

   public static GraphicsConfiguration getGraphicsConfigurationContaining(
           int x, int y) {
      GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] devices = env.getScreenDevices();
      for (GraphicsDevice device : devices) {
         GraphicsConfiguration[] configs = device.getConfigurations();
         for (GraphicsConfiguration config : configs) {
            Rectangle bounds = config.getBounds();
            if (bounds.contains(x, y)) {
               return config;
            }
         }
      }
      return null;
   }
   
}
