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
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DirectColorModel;
import java.util.ArrayList;
import java.util.HashSet;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This module handles setting the display mode (color/grayscale/composite) and
 * LUTs for displays. It is responsible for ensuring that image pixel
 * intensities map to the correct color/brightness in the display.
 */
public final class LUTMaster {

   /**
    * Provides an ImageIcon and the byte data used to generate that icon. These
    * represent the various false-color LUTs that we allow.
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

      @Override
      public String toString() {
         return String.format("<IconWithStats %s>", text_);
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
   // enums in the DisplaySettings!
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
      // Ensure that each channel has a valid initial LUT.
      // HACK: even if the datastore lacks a channel axis, there's still 1
      // processor that needs to be set.
      for (int i = 0; i < Math.max(1, display.getDatastore().getAxisLength(Coords.CHANNEL)); ++i) {
         updateDisplayLUTForChannel(display, i, false);
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
      // Determine which image(s) to update.
      // HACK: always do at least one update (even if there's no channel axis).
      // That's what the Math.max() call accomplishes.
      HashSet<Integer> displayedChannels = new HashSet<Integer>();
      for (Image image : display.getDisplayedImages()) {
         displayedChannels.add(Math.max(0, image.getCoords().getChannel()));
      }
      for (Integer i : displayedChannels) {
         updateDisplayLUTForChannel(display, i, true);
      }
   }

   /**
    * Apply LUTs to the ImagePlus/CompositeImage for the given display, based
    * on the parameters currently in the DisplaySettings.
    */
   private static void updateDisplayLUTForChannel(DisplayWindow display,
         int channelIndex, boolean shouldUpdateImage) {
      DisplaySettings settings = display.getDisplaySettings();
      DisplaySettings.ColorMode mode = settings.getChannelColorMode();
      boolean hasCustomLUT = (mode != null &&
            mode.getIndex() > DisplaySettings.ColorMode.GRAYSCALE.getIndex());
      SummaryMetadata summary = display.getDatastore().getSummaryMetadata();

      // Determine the channel color to use. If the display settings don't
      // have a channel color, then we should use either the remembered
      // color for this channel name, or a colorblind-friendly color, and then
      // save the new color to the display settings.
      Color defaultColor = Color.WHITE;
      if (channelIndex < ColorSets.COLORBLIND_COLORS.length) {
         defaultColor = ColorSets.COLORBLIND_COLORS[channelIndex];
      }
      Color color = RememberedChannelSettings.getColorWithSettings(
            summary.getSafeChannelName(channelIndex),
            summary.getChannelGroup(), settings, channelIndex, defaultColor);
      if (!color.equals(settings.getSafeChannelColor(channelIndex, null))) {
         settings = settings.copy().safeUpdateChannelColor(color,
               channelIndex).build();
         display.setDisplaySettings(settings);
      }
      // Coerce white for grayscale mode, but don't save it to the display
      // settings so we don't forget the color when we switch to other modes.
      if (mode == DisplaySettings.ColorMode.GRAYSCALE) {
         color = Color.WHITE;
      }

      // Get the ImageProcessor.
      ImagePlus plus = display.getImagePlus();
      CompositeImage composite = null;
      ImageProcessor processor = plus.getProcessor();

      if (plus instanceof CompositeImage) {
         composite = (CompositeImage) plus;
         if (composite.getMode() == CompositeImage.COMPOSITE) {
            // Get the processor for the specific channel we want to modify.
            // We don't need to do this in color/grayscale mode because the
            // processor is already the correct one.
            processor = composite.getProcessor(channelIndex + 1);
         }
      }

      if (processor == null) {
         // Not ready to apply LUTs yet.
         return;
      }

      // Get the parameters to use to adjust contrast for this channel.
      DisplaySettings.ContrastSettings defaultSettings =
         new DefaultDisplaySettings.DefaultContrastSettings(
            (int) processor.getMin(), (int) processor.getMax(), 1.0, true);
      DisplaySettings.ContrastSettings contrastSettings =
         settings.getSafeContrastSettings(channelIndex, defaultSettings);

      if (processor instanceof ColorProcessor) {
         // RGB images require special handling.
         setRGBLUT((ColorProcessor) processor, contrastSettings);
         return;
      }

      double gamma = contrastSettings.getSafeContrastGamma(0, 1.0);
      if (gamma < 0) {
         gamma = 1.0;
      }
      LUT lut = ImageUtils.makeLUT(color, gamma);
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
      if (lut == null) {
         return;
      }
      // Note that we are guaranteed to have values for the first component
      // here because of the call to getSafeContrastSettings() earlier.
      lut.min = contrastSettings.getSafeContrastMin(0, 0);
      lut.max = contrastSettings.getSafeContrastMax(0, 0);
      // HACK: don't allow min to equal max as this can cause images to render
      // as wholly black.
      if (lut.min == lut.max) {
         if (lut.min > 0) {
            lut.min--;
         }
         else {
            lut.max++;
         }
      }
      if (composite == null) {
         // Single-channel.
         processor.setColorModel(lut);
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
         }
         if (shouldUpdateImage) {
            composite.updateImage();
         }
      } // End multi-channel case.
      processor.setMinAndMax(lut.min, lut.max);
   }

   /**
    * Apply an RGB LUT to the provided processor.
    */
   private static void setRGBLUT(ColorProcessor processor,
         DisplaySettings.ContrastSettings contrastSettings) {
      // Unfathomably, ColorProcessor's setMinAndMax() method actually
      // modifies the pixel data, so we need to "reset" it to its "snapshot"
      // copy of the pixel data before applying our contrast factors. Of
      // course we also need to ensure that the snapshot exists.
      if (processor.getSnapshotPixels() == null) {
         processor.snapshot();
      }
      processor.reset();
      processor.setColorModel(
            new DirectColorModel(32, 0xff0000, 0xff00, 0xff));
      // ImageJ only does RGB, i.e. 3-component, images.
      for (int i = 0; i < 3; ++i) {
         // Note that using ColorProcessor.setMinAndMax() doesn't work; only
         // the component that we adjust last has the correct contrast
         // settings, while the other two components have default contrast.
         // Unfathomably, ImageJ's "which component to adjust" parameter is
         // actually a bitmask, so 1 = first component, 2 = second component,
         // and 4 = third component.
         int[] lut = calculateLinearLUT(contrastSettings.getSafeContrastMin(i,
                  (int) processor.getMin()),
               contrastSettings.getSafeContrastMax(i,
                  (int) processor.getMax()));
         // ImageJ's component numbers are BGR, so swap our index here.
         processor.applyTable(lut, 1 << (2 - i));
      }
   }

   /**
    * Generate a linear ramp from the specified min to the specified max, with
    * 256 entries.
    */
   private static int[] calculateLinearLUT(int min, int max) {
      int[] result = new int[256];
      for (int i = 0; i < result.length; ++i) {
         result[i] = (int) Math.max(0, Math.min(255,
                  256.0 * (i - min) / (max - min)));
      }
      return result;
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
            index == DisplaySettings.ColorMode.COMPOSITE.getIndex() ||
            display.getImagePlus().getProcessor() instanceof ColorProcessor);
      DisplaySettings.ColorMode mode = DisplaySettings.ColorMode.fromInt(index);
      if (isColored) {
         setColoredMode(display, mode);
      }
      else {
         IconWithStats icon = ICONS.get(index);
         setLUTMode(display, icon, index);
      }

      // Update the display settings, only if something changed.
      DisplaySettings origSettings = display.getDisplaySettings();
      if (origSettings.getChannelColorMode() != DisplaySettings.ColorMode.fromInt(index)) {
         DisplaySettings.DisplaySettingsBuilder builder = origSettings.copy();
         builder.channelColorMode(DisplaySettings.ColorMode.fromInt(index));
         DisplaySettings settings = builder.build();
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
      DisplaySettings settings = display.getDisplaySettings();
      ImagePlus plus = display.getImagePlus();
      if (plus instanceof CompositeImage) {
         CompositeImage composite = (CompositeImage) plus;
         if (mode == DisplaySettings.ColorMode.COMPOSITE) {
            // TODO: we may show this error multiple times per display, due
            // to the invokeLater logic above.
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
            // Ensure channels have appropriate visibility.
            for (int i = 0; i < display.getDatastore().getAxisLength(Coords.CHANNEL); ++i) {
               composite.getActiveChannels()[i] = settings.getSafeIsVisible(i, true);
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
         final IconWithStats icon, final int index) {
      if (!SwingUtilities.isEventDispatchThread()) {
         // Can't muck about with the display outside of the EDT. Re-call
         // in the EDT instead.
         Runnable runnable = new Runnable() {
            @Override
            public void run() {
               setLUTMode(display, icon, index);
            }
         };
         SwingUtilities.invokeLater(runnable);
         return;
      }
      ImagePlus plus = display.getImagePlus();
      // Set channel 0 always; do other channels if we're composite.
      LUT lut = createLUT(icon, display, 0);
      plus.getProcessor().setColorModel(lut);
      if (plus instanceof CompositeImage) {
         // Composite LUTs are done by shifting to grayscale and setting the
         // LUT for all channels to be the same; only one will be drawn at a
         // time.
         CompositeImage composite = (CompositeImage) plus;
         composite.setMode(CompositeImage.GRAYSCALE);
         for (int i = 0; i < display.getDatastore().getAxisLength(Coords.CHANNEL); ++i) {
            lut = createLUT(icon, display, i);
            // ImageJ is 1-indexed...
            composite.setChannelLut(lut, i + 1);
         }
      }
      plus.updateAndDraw();

      DisplaySettings settings = display.getDisplaySettings();
      DisplaySettings.ColorMode curMode = DisplaySettings.ColorMode.fromInt(index);
      if (settings.getChannelColorMode() != curMode) {
         // Changed display settings; update them.
         settings = settings.copy().channelColorMode(curMode).build();
         display.setDisplaySettings(settings);
      }
   }

   /**
    * Create a new LUT based on the colors of the given IconWithStats and the
    * gamma of the specified channel of the display.
    */
   private static LUT createLUT(IconWithStats icon, DisplayWindow display,
         int channelIndex) {
      // For now, always use the gamma of component 0.
      double gamma = display.getDisplaySettings().getSafeContrastGamma(
            channelIndex, 0, 1.0);
      if (Math.abs(gamma - 1.0) < .01) {
         // No gamma correction.
         return new LUT(8, icon.red_.length, icon.red_,
                  icon.green_, icon.blue_);
      }
      int len = icon.red_.length;
      byte[] red = new byte[len];
      byte[] blue = new byte[len];
      byte[] green = new byte[len];
      for (int i = 0; i < len; ++i) {
         int index = (int)
            (Math.pow((double) i / (len - 1), gamma) * (len - 1));
         red[i] = icon.red_[index];
         blue[i] = icon.blue_[index];
         green[i] = icon.green_[index];
      }
      return new LUT(8, len, red, green, blue);
   }
}
