
package org.micromanager.display.inspector.internal.panels.intensity;

import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JList;
import net.imglib2.display.ColorTable8;
import org.micromanager.internal.utils.ColorMaps;

/**
 * Generates the items in the colorModeComboBox in the top of the
 * Histograms and Intensity scaling panel
 *
 * @author mark
 */
final class ColorModeCell extends DefaultListCellRenderer {
   private static final int ICON_WIDTH = 64;
   private static final int ICON_HEIGHT = 12;

   static enum Item {
      COMPOSITE("Composite"),
      COLOR("Color"),
      GRAYSCALE("Grayscale"),
      HILIGHT_SAT("Highlight Saturated"),
      FIRE_LUT("Fire", ColorMaps.fireColorMap()),
      RED_HOT_LUT("Red-Hot", ColorMaps.redHotColorMap()),
      RGB("RGB");

      private final String text_;
      private final Icon icon_;

      private Item(String text) {
         text_ = text;
         icon_ = null;
      }

      private Item(String text, ColorTable8 lut) {
         text_ = text;
         icon_ = makeLUTIcon(lut);
      }

      @Override
      public String toString() {
         return text_;
      }

      Icon getIcon() {
         return icon_;
      }
   }

   private final List<Color> channelColors_ = new ArrayList<Color>();

   static ColorModeCell create() {
      return new ColorModeCell();
   }

   private ColorModeCell() {
      channelColors_.add(Color.WHITE);
   }

   void setChannelColors(List<Color> colors) {
      channelColors_.clear();
      if (colors == null || colors.isEmpty()) {
         channelColors_.add(Color.WHITE);
      }
      else {
         channelColors_.addAll(colors);
      }
   }

   @Override
   public Component getListCellRendererComponent(JList list, Object value,
         int index, boolean isSelected, boolean cellHasFocus) {
      Component superComponent = super.getListCellRendererComponent(list,
            String.valueOf(value), index, isSelected, cellHasFocus);
      if (value == null) {
         return superComponent;
      }

      Item item = (Item) value;
      switch (item) {
         case COMPOSITE:
            setIcon(getColorIcon(3));
            break;
         case COLOR:
            setIcon(getColorIcon(1));
            break;
         case GRAYSCALE:
            setIcon(GRAYSCALE_ICON);
            break;
         case HILIGHT_SAT:
            setIcon(GRAYSCALE_HILO_ICON);
            break;
         default:
            setIcon(item.getIcon());
      }

      return this;
   }

   private static Icon makeLUTIcon(ColorTable8 lut) {
      if (lut == null) {
         return null;
      }
      int[] pixels = new int[ICON_WIDTH * ICON_HEIGHT];
      for (int y = 0; y < ICON_HEIGHT; ++y) {
         for (int x = 0; x < ICON_WIDTH; ++x) {
            pixels[x + y * ICON_WIDTH] =
                  0xff << 24 |
                  lut.getResampled(0, ICON_WIDTH, x) << 16 |
                  lut.getResampled(1, ICON_WIDTH, x) << 8 |
                  lut.getResampled(2, ICON_WIDTH, x);
         }
      }

      BufferedImage image = new BufferedImage(ICON_WIDTH, ICON_HEIGHT,
            BufferedImage.TYPE_INT_ARGB);
      image.setRGB(0, 0, ICON_WIDTH, ICON_HEIGHT, pixels, 0, ICON_WIDTH);
      return new ImageIcon(image);
   }

   private static final Icon GRAYSCALE_ICON;
   private static final Icon GRAYSCALE_HILO_ICON;
   static {
      int[] pixels = new int[ICON_WIDTH * ICON_HEIGHT];
      for (int y = 0; y < ICON_HEIGHT; ++y) {
         for (int x = 0; x < ICON_WIDTH; ++x) {
            int gray = (int) Math.round((255.0 * x / (ICON_WIDTH - 1)));
            pixels[x + y * ICON_WIDTH] = 0xff << 24 | (0x00010101 * gray);
         }
      }
      BufferedImage image = new BufferedImage(ICON_WIDTH, ICON_HEIGHT,
            BufferedImage.TYPE_INT_ARGB);
      image.setRGB(0, 0, ICON_WIDTH, ICON_HEIGHT, pixels, 0, ICON_WIDTH);
      GRAYSCALE_ICON = new ImageIcon(image);

      for (int y = 0; y < ICON_HEIGHT; ++y) {
         for (int x = 0; x < 2; ++x) {
            pixels[x + y * ICON_WIDTH] = 0xff0000ff;
         }
         for (int x = ICON_WIDTH - 2; x < ICON_WIDTH; ++x) {
            pixels[x + y * ICON_WIDTH] = 0xffff0000;
         }
      }
      image = new BufferedImage(ICON_WIDTH, ICON_HEIGHT,
            BufferedImage.TYPE_INT_ARGB);
      image.setRGB(0, 0, ICON_WIDTH, ICON_HEIGHT, pixels, 0, ICON_WIDTH);
      GRAYSCALE_HILO_ICON = new ImageIcon(image);
   }

   private Icon getColorIcon(int maxColors) {
      // Show gradient of the first 1 to 3 colors.
      List<Color> sixColors = new ArrayList<Color>(6);
      switch (Math.min(maxColors, channelColors_.size())) {
         case 1:
            sixColors.addAll(Collections.nCopies(6, channelColors_.get(0)));
            break;
         case 2:
            sixColors.addAll(Collections.nCopies(3, channelColors_.get(0)));
            sixColors.addAll(Collections.nCopies(3, channelColors_.get(1)));
            break;
         default:
            sixColors.addAll(Collections.nCopies(2, channelColors_.get(0)));
            sixColors.addAll(Collections.nCopies(2, channelColors_.get(1)));
            sixColors.addAll(Collections.nCopies(2, channelColors_.get(2)));
            break;
      }

      int[] pixels = new int[ICON_WIDTH * ICON_HEIGHT];
      for (int y = 0; y < ICON_HEIGHT; ++y) {
         for (int x = 0; x < ICON_WIDTH; ++x) {
            double fraction = (double) x / (ICON_WIDTH - 1);
            Color color = sixColors.get(6 * y / ICON_HEIGHT);
            double r = fraction * color.getRed();
            double g = fraction * color.getGreen();
            double b = fraction * color.getBlue();
            pixels[x + y * ICON_WIDTH] =
                  0xff000000 |
                  0x00010000 * (int) Math.round(r) |
                  0x00000100 * (int) Math.round(g) |
                  0x00000001 * (int) Math.round(b);
         }
      }
      BufferedImage image = new BufferedImage(ICON_WIDTH, ICON_HEIGHT,
            BufferedImage.TYPE_INT_ARGB);
      image.setRGB(0, 0, ICON_WIDTH, ICON_HEIGHT, pixels, 0, ICON_WIDTH);
      return new ImageIcon(image);
   }
}