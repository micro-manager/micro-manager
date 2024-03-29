/*
 * Copyright (c) 2015, University of California, San Francisco
 * Derived from Micro-Manager MMFrame.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package edu.ucsf.valelab.gaussianfit.utils;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import org.micromanager.Studio;
import org.micromanager.UserProfile;


/**
 * Base class for frame windows. Saves and restores window size and position. Shamelessly copied
 * from Micro-Manager MMFrame Included here so that the plugin does not become dependent on
 * Micro-Manager
 */
public class GUFrame extends JFrame {

   private static final long serialVersionUID = 1L;
   private final String prefPrefix_;
   private final Class<?> caller_;
   private final Studio studio_;
   private static final String WINDOW_X = "frame_x";
   private static final String WINDOW_Y = "frame_y";
   private static final String WINDOW_WIDTH = "frame_width";
   private static final String WINDOW_HEIGHT = "frame_height";

   public GUFrame(Studio studio, Class<?> caller) {
      super();
      caller_ = caller;
      studio_ = studio;
      prefPrefix_ = "";
   }

   public GUFrame(Studio studio, Class<?> caller, String prefPrefix) {
      super();
      caller_ = caller;
      studio_ = studio;
      prefPrefix_ = prefPrefix;
   }

   /**
    * Checks whether WINDOW_X and WINDOW_Y coordinates are on the screen(s). If not then it sets the
    * prefs to the values specified. Accounts for screen size changes between invocations or if
    * screen is removed (e.g. had 2 monitors and go to 1). TODO: this code is duplicated between
    * here and MMDialog.
    *
    * @param x new WINDOW_X position if current value isn't valid
    * @param y new WINDOW_Y position if current value isn't valid
    */
   private void ensureSafeWindowPosition(int x, int y) {
      UserProfile up = studio_.profile();
      int prefX = up.getInt(caller_, prefPrefix_ + WINDOW_X, 0);
      int prefY = up.getInt(caller_, prefPrefix_ + WINDOW_Y, 0);
      if (getGraphicsConfigurationContaining(prefX, prefY) == null) {
         // only reach this code if the pref coordinates are off screen
         up.setInt(caller_, prefPrefix_ + WINDOW_X, x);
         up.setInt(caller_, prefPrefix_ + WINDOW_Y, y);
      }
   }

   public void loadPosition(int x, int y, int width, int height) {
      UserProfile up = studio_.profile();
      if (up == null) {
         return;
      }

      ensureSafeWindowPosition(x, y);
      setBounds(up.getInt(caller_, prefPrefix_ + WINDOW_X, x),
            up.getInt(caller_, prefPrefix_ + WINDOW_Y, y),
            up.getInt(caller_, prefPrefix_ + WINDOW_WIDTH, width),
            up.getInt(caller_, prefPrefix_ + WINDOW_HEIGHT, height));
   }

   public void loadPosition(int x, int y) {
      UserProfile up = studio_.profile();
      if (up == null) {
         return;
      }

      ensureSafeWindowPosition(x, y);
      setBounds(up.getInt(caller_, prefPrefix_ + WINDOW_X, x),
            up.getInt(caller_, prefPrefix_ + WINDOW_Y, y),
            getWidth(),
            getHeight());
   }


   /**
    * Load window position and size from preferences if possible. If not possible then sets them
    * from arguments Attaches a listener to the window that will save the position when the window
    * closing event is received
    *
    * @param x      - X position of this dialog if preference value invalid
    * @param y      - y position of this dialog if preference value invalid
    * @param width  - width of this dialog if preference value invalid
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
    * Load window position and size from preferences if possible. If not possible then sets it from
    * arguments Attaches a listener to the window that will save the position when the window
    * closing event is received
    *
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
      });
   }


   public void savePosition() {
      UserProfile up = studio_.profile();
      if (up == null) {
         return;
      }

      Rectangle r = getBounds();

      // save window position
      up.setInt(caller_, prefPrefix_ + WINDOW_X, r.x);
      up.setInt(caller_, prefPrefix_ + WINDOW_Y, r.y);
      up.setInt(caller_, prefPrefix_ + WINDOW_WIDTH, r.width);
      up.setInt(caller_, prefPrefix_ + WINDOW_HEIGHT, r.height);
   }


   @Override
   public void dispose() {
      savePosition();
      super.dispose();
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

