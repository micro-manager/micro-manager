package org.micromanager.internal.utils;

import static java.lang.Math.exp;
import static java.lang.Math.log;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Shape;
import java.awt.color.ColorSpace;
import java.awt.geom.Ellipse2D;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import javax.swing.JLabel;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.ArrayUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * @author mark
 */
public final class ColorPalettes {
   private ColorPalettes() {
   }

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
         new Color(0, 114, 178), // Blue
         new Color(213, 94, 0), // Vermillion
         new Color(0, 158, 115), // Bluish Green
         new Color(230, 159, 0), // Orange
         new Color(86, 180, 233), // Sky Blue
         new Color(240, 228, 66), // Yellow
         new Color(204, 121, 167), // Reddish Purple
   };

   private static final Color[] PRIMARY_COLORS = new Color[] {
         Color.RED, Color.GREEN, Color.BLUE,
         Color.CYAN, Color.MAGENTA, Color.YELLOW,
   };

   //
   //
   //

   private static final Map<String, Float> WAVELENGTHS = new LinkedHashMap<>();

   static {
      // Don't expect these numbers to be scientifically accurate in any sense;
      // the goal here is to guess at reasonable-ish colors to assign as
      // defaults to user-named channels.
      // (Another approach would be to let the user select from a combo box
      // (also allowing free-form text) when naming channels.)
      //
      // Source: Wikipedia, ThermoFisher, Chroma, etc.
      // List is ordered so that longer strings match before their substrings
      WAVELENGTHS.put("cy7", 767f);
      WAVELENGTHS.put("cy5.5", 694f);
      WAVELENGTHS.put("cy5", 670f);
      WAVELENGTHS.put("cy3.5", 594f);
      WAVELENGTHS.put("cy3", 570f);
      WAVELENGTHS.put("cy2", 506f);
      WAVELENGTHS.put("txr", 615f);
      WAVELENGTHS.put("texr", 615f);
      WAVELENGTHS.put("texred", 615f);
      WAVELENGTHS.put("texas", 615f);
      WAVELENGTHS.put("tamra", 580f);
      WAVELENGTHS.put("tritc", 572f);
      WAVELENGTHS.put("rhodamine", 576f);
      WAVELENGTHS.put("fitc", 518f);
      WAVELENGTHS.put("fluorescein", 519f);
      WAVELENGTHS.put("yoyo", 509f);
      WAVELENGTHS.put("dapi", 461f);
      WAVELENGTHS.put("hoechst", 461f);
      WAVELENGTHS.put("33342", 461f);
      WAVELENGTHS.put("34580", 461f);
      WAVELENGTHS.put("coumarin", 400f);
      WAVELENGTHS.put("raspberry", 625f);
      WAVELENGTHS.put("plum", 649f);
      WAVELENGTHS.put("cherry", 610f);
      WAVELENGTHS.put("mrfp", 607f);
      WAVELENGTHS.put("strawberry", 596f);
      WAVELENGTHS.put("rfp", 582f);
      WAVELENGTHS.put("dsred", 584f);
      WAVELENGTHS.put("tomato", 581f);
      WAVELENGTHS.put("orange", 562f);
      WAVELENGTHS.put("yellow", 539f);
      WAVELENGTHS.put("citrine", 529f);
      WAVELENGTHS.put("venus", 528f);
      WAVELENGTHS.put("eyfp", 527f);
      WAVELENGTHS.put("yfp", 527f);
      WAVELENGTHS.put("wasabi", 509f);
      WAVELENGTHS.put("green", 505f);
      WAVELENGTHS.put("egfp", 507f);
      WAVELENGTHS.put("gfp", 508f);
      WAVELENGTHS.put("mtfp", 477f);
      WAVELENGTHS.put("cfp", 477f);
      WAVELENGTHS.put("turquoise", 474f);
      WAVELENGTHS.put("cerulean", 475f);
      WAVELENGTHS.put("bfp", 440f);
      WAVELENGTHS.put("red", 584f);
      WAVELENGTHS.put("fam", 516f);
      WAVELENGTHS.put("tr", 615f);
   }

   private static final Pattern NM_PATTERN = Pattern.compile(
         // 3-digit number
         "(^|\\D)(\\d\\d\\d)(\\D|$)"
   );
   private static final int NM_GROUP_INDEX = 2;

   public static Color guessColor(String channelName) {
      String name = channelName.toLowerCase();
      Float lambda = WAVELENGTHS.get(name);
      // full match
      if (lambda != null) {
         return colorForMonochromaticWavelenth(lambda);
      }
      // partial match
      for (String key : WAVELENGTHS.keySet()) {
         if (name.contains(key)) {
            return colorForMonochromaticWavelenth(WAVELENGTHS.get(key));
         }
      }
      // if there is a 3-digit number in name, take it as excitation/absorption
      // wavelength and give it a Stokes shift.
      final int STOKES_SHIFT = 40;
      Matcher matcher = NM_PATTERN.matcher(name);
      if (matcher.find()) {
         try {
            int nanometers = Integer.parseInt(matcher.group(NM_GROUP_INDEX)) + STOKES_SHIFT;
            if (380 <= nanometers && nanometers <= 800) {
               return colorForMonochromaticWavelenth(nanometers);
            }
         } catch (NumberFormatException e) {
         }
      }
      return Color.WHITE;
   }

   public static Color colorForMonochromaticWavelenth(double lambdaNm) {
      lambdaNm = Math.max(380.0, Math.min(680.0, lambdaNm));

      float x = (float) ApproxCIE1964.xcie(lambdaNm);
      float y = (float) ApproxCIE1964.ycie(lambdaNm);
      float z = (float) ApproxCIE1964.zcie(lambdaNm);

      // Normalization factor scaled by 0.45 to prevent sRGB from saturating.
      float n = 0.45f / (x + y + z);

      Color xyzColor = new Color(ColorSpace.getInstance(ColorSpace.CS_CIEXYZ),
            new float[] {n * x, n * y, n * z}, 1.0f);
      float[] rgb = xyzColor.getColorComponents(
            ColorSpace.getInstance(ColorSpace.CS_sRGB), new float[3]);
      // Scale rgb uniformly so highest sample is 255
      float nrgb = 1.0f / Collections.max(Arrays.asList(ArrayUtils.toObject(rgb)));
      rgb[0] *= nrgb;
      rgb[1] *= nrgb;
      rgb[2] *= nrgb;
      return new Color(rgb[0], rgb[1], rgb[2]);
   }

   /**
    * Approximation of CIE 1964 standard observer color matching functions.
    *
    * <p>Analytical approximations given in:<br>
    * Chris Wyman, Peter-Pike Sloan, and Peter Shirley, 2013.
    * Simple Analytic Approximations to the CIE XYZ Color Matching Functions.
    * <a href="http://jcgt.org/published/0002/02/01/">Journal of Computer
    * Graphics Techniques, 2(2):1-11</a>.
    *
    * <p>The 1964 (rather than 1931) approximations are used here since they are
    * better than the simple 1931 approximations but simpler than the piecewise
    * approximations for 1931.
    */
   private static final class ApproxCIE1964 {
      private static double xcie(double lambda) {
         double f1 = log((lambda + 570.1) / 1014.0);
         double f2 = log((1338.0 - lambda) / 743.5);
         return 0.398 * exp(-1250.0 * f1 * f1) + 1.132 * exp(-234.0 * f2 * f2);
      }

      private static double ycie(double lambda) {
         double f = (lambda - 556.1) / 46.14;
         return 1.011 * exp(-0.5 * f * f);
      }

      private static double zcie(double lambda) {
         double f = log((lambda - 265.8) / 180.4);
         return 2.060 * exp(-32.0 * f * f);
      }
   }

   public static void main(String[] args) {
      // Plot xyz and rgb
      XYSeries xdata = new XYSeries("x", false);
      XYSeries ydata = new XYSeries("y", false);
      XYSeries zdata = new XYSeries("z", false);
      XYSeries rdata = new XYSeries("r", false);
      XYSeries gdata = new XYSeries("g", false);
      XYSeries bdata = new XYSeries("b", false);
      XYSeries hdata = new XYSeries("h", false);
      XYSeries sdata = new XYSeries("s", false);
      XYSeries vdata = new XYSeries("v", false);
      for (int lambda = 380; lambda <= 680; ++lambda) {
         xdata.add(lambda, ApproxCIE1964.xcie(lambda));
         ydata.add(lambda, ApproxCIE1964.ycie(lambda));
         zdata.add(lambda, ApproxCIE1964.zcie(lambda));

         Color c = colorForMonochromaticWavelenth(lambda);
         float[] rgb = c.getRGBColorComponents(new float[3]);
         rdata.add(lambda, rgb[0]);
         gdata.add(lambda, rgb[1]);
         bdata.add(lambda, rgb[2]);

         float[] hsv = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(),
               new float[3]);
         hdata.add(lambda, hsv[0]);
         sdata.add(lambda, hsv[1]);
         vdata.add(lambda, hsv[2]);
      }
      XYSeriesCollection data = new XYSeriesCollection();
      data.addSeries(xdata);
      data.addSeries(ydata);
      data.addSeries(zdata);
      data.addSeries(rdata);
      data.addSeries(gdata);
      data.addSeries(bdata);
      data.addSeries(hdata);
      data.addSeries(sdata);
      data.addSeries(vdata);
      JFreeChart chart = ChartFactory.createScatterPlot("CIE 1964",
            "lambda (nm)", null, data, PlotOrientation.VERTICAL,
            true, true, false);
      XYPlot plot = (XYPlot) chart.getPlot();
      plot.setBackgroundPaint(Color.WHITE);
      plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
      XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
      renderer.setDefaultShapesVisible(true);
      renderer.setSeriesPaint(0, Color.BLACK);
      renderer.setSeriesFillPaint(0, Color.WHITE);
      renderer.setSeriesLinesVisible(0, true);
      Shape circle = new Ellipse2D.Float(-2.0f, -2.0f, 4.0f, 4.0f);
      renderer.setSeriesShape(0, circle, false);
      renderer.setUseFillPaint(true);

      ChartFrame frame = new ChartFrame("CIE 1964", chart);
      frame.getChartPanel().setMouseWheelEnabled(true);
      frame.setPreferredSize(new Dimension(768, 512));
      frame.setResizable(true);
      frame.pack();
      frame.setVisible(true);

      // Show some sample colors
      JFrame f = new JFrame();
      f.setLayout(new MigLayout());
      for (String name : new String[] {
            "DAPI", "FITC", "TRITC", "TexR",
            "BFP", "CFP", "GFP", "YFP", "RFP", "mCherry",
            "laser488", "647laser", "Alexa555", "12345"
      }) {
         JLabel label = new JLabel(name);
         label.setOpaque(true);
         label.setBackground(guessColor(name));
         f.add(label);
      }
      f.pack();
      f.setVisible(true);
   }
}