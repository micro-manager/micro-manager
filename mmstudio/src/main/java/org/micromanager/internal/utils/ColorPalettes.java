/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.utils;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author mark
 */
public final class ColorPalettes {
   private ColorPalettes() { }

   public static Color getFromDefaultPalette(int i) {
      return getFromColorblindFriendlyPalette(i);
   }

   public static Color getFromColorblindFriendlyPalette(int i) {
      // If you've used up the colors, you should probably be using something
      // other than color to distinguish data.... So don't attempt modulo i.
      if (i >= COLORBLIND_FRIENDLY_COLORS.length) {
         return Color.WHITE;
      }
      return COLORBLIND_FRIENDLY_COLORS[i];
   }

   public static Color getFromPrimaryColorPalette(int i) {
      if (i >= PRIMARY_COLORS.length) {
         return Color.WHITE;
      }
      return PRIMARY_COLORS[i];
   }

   public static List<Color> getDefaultPalette() {
      return getColorblindFriendlyPalette();
   }

   public static List<Color> getColorblindFriendlyPalette() {
      return Collections.unmodifiableList(
            Arrays.asList(COLORBLIND_FRIENDLY_COLORS));
   }

   public static List<Color> getPrimaryColorPalette() {
      return Collections.unmodifiableList(Arrays.asList(PRIMARY_COLORS));
   }

   // Colors optimized for colorblind individuals, from
   // Bang Wong, 2011. Nature Methods 8, 441. Points of view: Color blindness.
   // http://dx.doi.org/10.1038/nmeth.1618
   // Selection of the first three colors based on recommendations from
   // Ankur Jain at the Vale lab.
   private static final Color[] COLORBLIND_FRIENDLY_COLORS = new Color[] {
      new Color(  0, 114, 178), // Blue
      new Color(213,  94,   0), // Vermillion
      new Color(  0, 158, 115), // Bluish Green
      new Color(230, 159,   0), // Orange
      new Color( 86, 180, 233), // Sky Blue
      new Color(240, 228,  66), // Yellow
      new Color(204, 121, 167), // Reddish Purple
   };

   private static final Color[] PRIMARY_COLORS = new Color[] {
         Color.RED, Color.GREEN, Color.BLUE,
         Color.CYAN, Color.MAGENTA, Color.YELLOW,
   };
}