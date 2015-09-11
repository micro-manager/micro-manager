///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.display.internal;

import com.bulenkov.iconloader.IconLoader;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.LUT;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.micromanager.data.Coords;

import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.DefaultDisplaySettings;

import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This module handles setting the display mode (color/grayscale/composite) and
 * LUTs for displays. It basically acts as a backing for a) initializing
 * display LUTs, and b) implementing the logic behind the ColorModeControl
 * in the inspector.
 */
public class LUTMaster {

   /**
    * Provides an ImageIcon and the byte data used to generate that icon.
    */
   public static class IconWithStats {
      private static final int ICON_WIDTH = 64;
      private static final int ICON_HEIGHT = 10;

      public String text_;
      public byte[] red_;
      public byte[] green_;
      public byte[] blue_;
      public Icon icon_;
            
      /**
       * Derive the icon from the byte arrays we are provided.
       */
      public IconWithStats(String text, byte[] red, byte[] green, byte[] blue) {
         text_ = text;
         // Clone the arrays because they get re-used in the process of
         // creating all IconWithStats instances.
         red_ = red.clone();
         green_ = green.clone();
         blue_ = blue.clone();
         icon_ = makeLUTIcon(red, green, blue);
      }  

      /**
       * Take in a pre-set icon.
       */
      public IconWithStats(String text, Icon icon, byte[] red, byte[] green,
            byte[] blue) {
         text_ = text;
         icon_ = icon;
         // Null shows up here for the color and composite options.
         if (red != null) {
            red_ = red.clone();
            green_ = green.clone();
            blue_ = blue.clone();
         }
      }

      /**
       * Generate a gradient icon for one of the LUTs we support.
       */
      public static ImageIcon makeLUTIcon(byte[] red, byte[] green,
            byte[] blue) {
         int[] pixels = new int[ICON_WIDTH * ICON_HEIGHT];
         double ratio = (double) 256 / (double) ICON_WIDTH;
         for (int y = 0; y < ICON_HEIGHT; y++) {
            for (int x = 0; x < ICON_WIDTH; x++) {
               int index = (int) (ratio * x);
               int ri = 0xff & red[index];
               int rg = 0xff & green[index];
               int rb = 0xff & blue[index];
               pixels[y * ICON_WIDTH + x] = ((0xff << 24) | (ri << 16)
                       | (rg << 8) | (rb) );
            }
         }
         BufferedImage image = new BufferedImage(ICON_WIDTH, ICON_HEIGHT,
               BufferedImage.TYPE_INT_ARGB);
         image.setRGB(0, 0, ICON_WIDTH, ICON_HEIGHT, pixels, 0, ICON_WIDTH);
         return new ImageIcon(image) {
            @Override
            public boolean equals(Object alt) {
               return alt == this;
            }
         };
      }
   }

   public static IconWithStats GRAY;
   // Highlights the dimmest and brightest pixels; otherwise gray.
   public static IconWithStats HIGHLIGHT_LIMITS;
   public static IconWithStats FIRE;
   public static IconWithStats REDHOT;
   public static IconWithStats SPECTRUM;
   private static Icon EMPTY = IconLoader.getIcon(
         "/org/micromanager/icons/empty.png");

   static {
      byte[] red = new byte[256];
      byte[] green = new byte[256];
      byte[] blue = new byte[256];

      // Gray
      for (int i = 0; i < 256; ++i) {
         red[i] = (byte) i;
         green[i] = (byte) i;
         blue[i] = (byte) i;
      }
      GRAY = new IconWithStats("Grayscale", red, green, blue);

      // Glow. In the icon, we make the low/high bits wider so they're more
      // visible.
      byte[] iconRed = red.clone();
      byte[] iconGreen = green.clone();
      byte[] iconBlue = blue.clone();
      for (int i = 0; i < 6; ++i) {
         iconRed[i] = (byte) 0;
         iconGreen[i] = (byte) 0;
         iconBlue[i] = (byte) 255;
         iconRed[255 - i] = (byte) 255;
         iconGreen[255 - i] = (byte) 0;
         iconBlue[255 - i] = (byte) 0;
      }
      red[0] = (byte) 0;
      green[0] = (byte) 0;
      blue[0] = (byte) 255;
      red[255] = (byte) 255;
      green[255] = (byte) 0;
      blue[255] = (byte) 0;
      HIGHLIGHT_LIMITS = new IconWithStats("Highlight limits",
            IconWithStats.makeLUTIcon(iconRed, iconGreen, iconBlue),
            red, green, blue);

      // Fire. Interpolated from 32 control points, copied from ImageJ.
      int[] r = {0,0,1,25,49,73,98,122,146,162,173,184,195,207,217,229,240,252,255,255,255,255,255,255,255,255,255,255,255,255,255,255};
      int[] g = {0,0,0,0,0,0,0,0,0,0,0,0,0,14,35,57,79,101,117,133,147,161,175,190,205,219,234,248,255,255,255,255};
      int[] b = {0,61,96,130,165,192,220,227,210,181,151,122,93,64,35,5,0,0,0,0,0,0,0,0,0,0,0,35,98,160,223,255};
      FIRE = new IconWithStats("Fire", interpolate(r), interpolate(g),
            interpolate(b));

      // Redhot; developed by Nico Stuurman based on ImageJ. Similarly
      // interpolated based on 32 control points.
      r = new int[] {0,1,27,52,78,103,130,155,181,207,233,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255};
      g = new int[] {0,1, 0, 0, 0,  0,  0,  0,  0,  0,  0,  3, 29, 55, 81,106,133,158,184,209,236,255,255,255,255,255,255,255,255,255,255,255};
      b = new int[] {0,1, 0, 0, 0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  6, 32, 58, 84,110,135,161,187,160,213,255};
      REDHOT = new IconWithStats("Red-Hot", interpolate(r), interpolate(g),
            interpolate(b));

      // Spectrum.
      for (int i = 0; i < 256; ++i) {
         Color color = Color.getHSBColor(i / 255f, 1f, 1f);
         red[i] = (byte) color.getRed();
         green[i] = (byte) color.getGreen();
         blue[i] = (byte) color.getBlue();
      }
      SPECTRUM = new IconWithStats("Spectrum", red, green, blue);
   }

   /**
    * Given an array of ints, generate a length-256 array of bytes
    * interpolating between the ints. ints are used just as a convenience to
    * avoid excessive casting to byte, as numeric literals are ints in Java.
    * NOTE: it is assumed that the length of the input array evenly divides
    * 256.
    */
   private static byte[] interpolate(int[] vals) {
      byte[] result = new byte[256];
      // Ratio of final control points to initial control points.
      double ratio = 256 / vals.length;
      for (int i = 0; i < 256; ++i) {
         // First control point
         int a = (int) (i / ratio);
         // Second control point
         int b = a + 1;
         // Don't go off the end of the control points.
         if (b >= vals.length) {
            b = a;
         }
         // Location of this value between a and b
         double delta = (i % ratio) / ratio;
         result[i] = (byte) (vals[a] + (vals[b] - vals[a]) * delta);
      }
      return result;
   }

   // List of available color modes we allow.
   // NOTE: the order of items in this list must match the order of ColorMode
   // enums!
   public static final ArrayList<IconWithStats> ICONS = new ArrayList<IconWithStats>();
   static {
      ICONS.add(new IconWithStats("Color", EMPTY, null, null, null));
      ICONS.add(new IconWithStats("Composite", EMPTY, null, null, null));
      ICONS.add(GRAY);
      ICONS.add(HIGHLIGHT_LIMITS);
      ICONS.add(FIRE);
      ICONS.add(REDHOT);
      ICONS.add(SPECTRUM);
   }

   /**
    * Ensure that the display's color mode is correctly-set, and apply the
    * default LUTs to it.
    */
   public static void initializeDisplay(DisplayWindow display) {
      // Use saved settings, or default to composite.
      DisplaySettings settings = display.getDisplaySettings();
      if (settings.getChannelColorMode() != null) {
         setModeByIndex(display, settings.getChannelColorMode().getIndex());
      }
      else {
         setModeByIndex(display,
               DisplaySettings.ColorMode.COMPOSITE.getIndex());
      }
      updateDisplayLUTs(display);
   }

   /**
    * Cause any changes to the LUT parameters for the display to be applied
    * to it.
    */
   public static void updateDisplayLUTs(final DisplayWindow display) {
      if (!SwingUtilities.isEventDispatchThread()) {
         // Can't muck about with the display outside of the EDT. Re-call
         // in the EDT instead.
         Runnable runnable = new Runnable() {
            @Override
            public void run() {
               updateDisplayLUTs(display);
            }
         };
         SwingUtilities.invokeLater(runnable);
         return;
      }
      for (int i = 0; i < display.getDatastore().getAxisLength(Coords.CHANNEL); ++i) {
         updateDisplayLUTForChannel(display, i);
      }
   }

   /**
    * Apply LUTs to the ImagePlus/CompositeImage for the given display, based
    * on the parameters currently in the DisplaySettings.
    */
   private static void updateDisplayLUTForChannel(DisplayWindow display,
         int channelIndex) {
      DisplaySettings settings = display.getDisplaySettings();
      DisplaySettings.ColorMode mode = settings.getChannelColorMode();
      boolean hasCustomLUT = (mode != null &&
            mode.getIndex() > DisplaySettings.ColorMode.COMPOSITE.getIndex());
      Color color = settings.getSafeChannelColor(channelIndex, Color.WHITE);
      if (mode == DisplaySettings.ColorMode.GRAYSCALE) {
         color = Color.WHITE;
      }
      LUT lut = ImageUtils.makeLUT(color,
            settings.getSafeChannelGamma(channelIndex, 1.0));
      ImagePlus plus = display.getImagePlus();
      CompositeImage composite = null;
      if (plus instanceof CompositeImage) {
         composite = (CompositeImage) plus;
      }
      ImageProcessor processor = plus.getProcessor();
      if (hasCustomLUT) {
         // Get the current LUT from ImageJ instead.
         // TODO: Ignore gamma settings for custom LUTs.
         if (composite == null) {
            lut = processor.getLut();
         }
         else {
            // ImageJ is 1-indexed...
            try {
               lut = composite.getChannelLut(channelIndex + 1);
            }
            catch (IllegalArgumentException e) {
               // This can happen sometimes before the display gets
               // set up properly, as the CompositeImage doesn't
               // have the required number of LUTs for
               // getChannelLut() to succeed. Just ignore it.
               // TODO: fix whatever race condition causes this to happen.
               return;
            }
         }
      }
      lut.min = settings.getSafeChannelContrastMin(channelIndex,
            (int) processor.getMin());
      lut.max = settings.getSafeChannelContrastMax(channelIndex,
            (int) processor.getMax());
      processor.setMinAndMax(lut.min, lut.max);
      if (composite == null) {
         // Single-channel.
         if (!hasCustomLUT) {
            processor.setColorModel(lut);
         }
      }
      else {
         if (!hasCustomLUT) {
            try {
               composite.setChannelLut(lut, channelIndex + 1);
            }
            catch (IllegalArgumentException e) {
               // See above catch of the same exception.
               return;
            }
         }

         if (composite.getMode() != CompositeImage.COMPOSITE) {
            // ImageJ workaround: do this so the appropriate color
            // model gets applied in color or grayscale mode.
            // Otherwise we can end up with erroneously grayscale
            // images.
            try {
               JavaUtils.setRestrictedFieldValue(composite,
                     CompositeImage.class, "currentChannel", -1);
            } catch (NoSuchFieldException ex) {
               ReportingUtils.logError(ex);
            }
            composite.updateImage();
         }
      } // End multi-channel case.
   }

   /**
    * Set the color mode of the given display by indexing into the ColorMode
    * aspect of the DisplaySettings using the given index.
    */
   public static void setModeByIndex(final DisplayWindow display,
         final int index) {
      if (!SwingUtilities.isEventDispatchThread()) {
         // Can't muck about with the display outside of the EDT. Re-call
         // in the EDT instead.
         Runnable runnable = new Runnable() {
            @Override
            public void run() {
               setModeByIndex(display, index);
            }
         };
         SwingUtilities.invokeLater(runnable);
         return;
      }
      if (index < 0 || index >= ICONS.size()) {
         ReportingUtils.logError("Invalid color mode index selection " + index);
         return;
      }
      boolean isColored = (
            index == DisplaySettings.ColorMode.COLOR.getIndex() ||
            index == DisplaySettings.ColorMode.COMPOSITE.getIndex());
      DisplaySettings.ColorMode mode = DisplaySettings.ColorMode.fromInt(index);
      if (isColored) {
         setColoredMode(display, mode);
      }
      else {
         IconWithStats icon = ICONS.get(index);
         setLUTMode(display, new LUT(8, icon.red_.length, icon.red_,
                  icon.green_, icon.blue_), index);
      }

      // Update the display settings, only if something changed.
      DisplaySettings origSettings = display.getDisplaySettings();
      DisplaySettings.DisplaySettingsBuilder builder = origSettings.copy();
      builder.channelColorMode(DisplaySettings.ColorMode.fromInt(index));
      DisplaySettings settings = builder.build();
      if (settings.getChannelColorMode() != origSettings.getChannelColorMode()) {
         DefaultDisplaySettings.setStandardSettings(settings);
         display.setDisplaySettings(settings);
      }
   }

   /**
    * Set the color or composite mode. This updates the ImageJ CompositeImage
    * as needed.
    */
   public static void setColoredMode(final DisplayWindow display,
         final DisplaySettings.ColorMode mode) {
      if (!SwingUtilities.isEventDispatchThread()) {
         // Can't muck about with the display outside of the EDT. Re-call
         // in the EDT instead.
         Runnable runnable = new Runnable() {
            @Override
            public void run() {
               setColoredMode(display, mode);
            }
         };
         SwingUtilities.invokeLater(runnable);
         return;
      }
      DisplaySettings origSettings = display.getDisplaySettings();
      ImagePlus plus = display.getImagePlus();
      if (plus instanceof CompositeImage) {
         CompositeImage composite = (CompositeImage) plus;
         if (mode == DisplaySettings.ColorMode.COMPOSITE) {
            if (display.getDatastore().getAxisLength(Coords.CHANNEL) > 7) {
               JOptionPane.showMessageDialog(null,
                  "Images with more than 7 channels cannot be displayed in Composite mode.");
               // Send them back to Color mode.
               setColoredMode(display, DisplaySettings.ColorMode.COLOR);
               return;
            }
            else {
               composite.setMode(CompositeImage.COMPOSITE);
            }
         }
         else if (mode == DisplaySettings.ColorMode.COLOR) {
            composite.setMode(CompositeImage.COLOR);
         }
         else if (mode == DisplaySettings.ColorMode.GRAYSCALE) {
            composite.setMode(CompositeImage.GRAYSCALE);
         }
         else {
            ReportingUtils.showError("Unsupported color mode " + mode);
         }
      }
      plus.updateAndDraw();
   }

   /**
    * Set a LUT-based mode.
    */
   public static void setLUTMode(final DisplayWindow display,
         final LUT lut, final int index) {
      if (!SwingUtilities.isEventDispatchThread()) {
         // Can't muck about with the display outside of the EDT. Re-call
         // in the EDT instead.
         Runnable runnable = new Runnable() {
            @Override
            public void run() {
               setLUTMode(display, lut, index);
            }
         };
         SwingUtilities.invokeLater(runnable);
         return;
      }
      ImagePlus plus = display.getImagePlus();
      plus.getProcessor().setColorModel(lut);
      if (plus instanceof CompositeImage) {
         // Composite LUTs are done by shifting to grayscale and setting the
         // LUT for all channels to be the same; only one will be drawn at a
         // time.
         // Though we can't actually use the *same* LUT instance because then
         // the scaling is shared across channels.
         CompositeImage composite = (CompositeImage) plus;
         composite.setMode(CompositeImage.GRAYSCALE);
         for (int i = 0; i < display.getDatastore().getAxisLength(
                  Coords.CHANNEL); ++i) {
            // ImageJ is 1-indexed...
            composite.setChannelLut((LUT) lut.clone(), i + 1);
         }
      }
      plus.updateAndDraw();

      DisplaySettings settings = display.getDisplaySettings().copy()
         .channelColorMode(DisplaySettings.ColorMode.fromInt(index)).build();
      display.setDisplaySettings(settings);
   }
}
