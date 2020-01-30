///////////////////////////////////////////////////////////////////////////////
//PROJECT:       ASIdiSPIM plugin
//SUBSYSTEM:     utils
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 1, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006-2015
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
package org.micromanager.asidispim.utils;

import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.JFrame;
import org.micromanager.Studio;
import org.micromanager.UserProfile;


/**
 * Ported from Micro-Manager source code
 * Base class for frame windows.
 * Saves and restores window size and position. 
 */
public class SPIMFrame extends JFrame {
   private final Studio gui_;
   private static final long serialVersionUID = 1L;
   private final String prefPrefix_;
   private static final String WINDOW_X = "frame_x";
   private static final String WINDOW_Y = "frame_y";
   private static final String WINDOW_WIDTH = "frame_width";
   private static final String WINDOW_HEIGHT = "frame_height";
   
   public SPIMFrame(Studio gui) {
      super();
      gui_ = gui;
      prefPrefix_ = "";
   }

   public SPIMFrame(Studio gui, String prefPrefix) {
      super();
      gui_ = gui;
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
      UserProfile profile = gui_.profile();
      int prefX = profile.getSettings(this.getClass()).getInteger(
              prefPrefix_ + WINDOW_X, 0);
      int prefY = profile.getSettings(this.getClass()).getInteger(
              prefPrefix_ + WINDOW_Y, 0);
      if (getGraphicsConfigurationContaining(prefX, prefY) == null) {
         // only reach this code if the pref coordinates are off screen
         profile.getSettings(this.getClass()).putInteger(
                 prefPrefix_ + WINDOW_X, x);
         profile.getSettings(this.getClass()).putInteger(
                 prefPrefix_ + WINDOW_Y, y);
      }
   }

   public void loadPosition(int x, int y, int width, int height) {
      ensureSafeWindowPosition(x, y);
      UserProfile profile = gui_.profile();
      setBounds(profile.getSettings(this.getClass()).getInteger(
                        prefPrefix_ + WINDOW_X, x),
                profile.getSettings(this.getClass()).getInteger(
                        prefPrefix_ + WINDOW_Y, y),
                profile.getSettings(this.getClass()).getInteger(
                        prefPrefix_ + WINDOW_WIDTH, width),
                profile.getSettings(this.getClass()).getInteger(
                        prefPrefix_ + WINDOW_HEIGHT, height));
      offsetIfNecessary();
   }

   public void loadPosition(int x, int y) {
      ensureSafeWindowPosition(x, y);
      UserProfile profile = gui_.profile();
      setBounds(profile.getSettings(this.getClass()).getInteger(
                        prefPrefix_ + WINDOW_X, x),
                profile.getSettings(this.getClass()).getInteger(
                        prefPrefix_ + WINDOW_Y, y),
                getWidth(),
                getHeight());
      offsetIfNecessary();
   }

   /**
    * Scan the program for other MMFrames that have the same type as this
    * MMFrame, and make certain we don't precisely overlap any of them.
    */
   private void offsetIfNecessary() {
      Point newLoc = getLocation();
      boolean foundOverlap;
      do {
         foundOverlap = false;
         for (Frame frame : Frame.getFrames()) {
            if (frame != this && frame.getClass() == getClass() &&
                  frame.getLocation().equals(newLoc)) {
               foundOverlap = true;
               newLoc.x += 22;
               newLoc.y += 22;
            }
         }
         if (!foundOverlap) {
            break;
         }
      } while (foundOverlap);

      setLocation(newLoc);
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
      addComponentListener(new ComponentAdapter() {
         @Override
         public void componentMoved(ComponentEvent e) {
            savePosition();
         }
      });
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
      addComponentListener(new ComponentAdapter() {
         @Override
         public void componentMoved(ComponentEvent e) {
            savePosition();
         }
      });
   }
   

   public void savePosition() {
      Rectangle r = getBounds();
      
      // save window position
      UserProfile profile = gui_.profile();
      profile.getSettings(this.getClass()).putInteger(
              prefPrefix_ + WINDOW_X, r.x);
      profile.getSettings(this.getClass()).putInteger(
              prefPrefix_ + WINDOW_Y, r.y);
      profile.getSettings(this.getClass()).putInteger(
              prefPrefix_ + WINDOW_WIDTH, r.width);
      profile.getSettings(this.getClass()).putInteger(
              prefPrefix_ + WINDOW_HEIGHT, r.height);
   }
   
         
   @Override
   public void dispose() {
      savePosition();
      super.dispose();
   }
   
   
   public static GraphicsConfiguration getGraphicsConfigurationContaining(
         int x, int y) {
      for (GraphicsConfiguration config : getConfigs()) {
         Rectangle bounds = config.getBounds();
         if (bounds.contains(x, y)) {
            return config;
         }
      }
      return null;
   }
   
   /**
    * Convenience method to iterate over all graphics configurations.
    */
   private static ArrayList<GraphicsConfiguration> getConfigs() {
      ArrayList<GraphicsConfiguration> result = new ArrayList<GraphicsConfiguration>();
      GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] devices = env.getScreenDevices();
      for (GraphicsDevice device : devices) {
         GraphicsConfiguration[] configs = device.getConfigurations();
         result.addAll(Arrays.asList(configs));
      }
      return result;
   }
   
}
