package org.micromanager.display.inspector.internal.panels.intensity;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.data.Coords;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.event.DataViewerMousePixelInfoChangedEvent;
import org.micromanager.display.internal.event.DisplayMouseEvent;
import org.micromanager.display.internal.imagestats.ComponentStats;
import org.micromanager.display.internal.imagestats.ImageStats;
import org.micromanager.internal.utils.MustCallOnEDT;

/**
 * Controls brightness / contrast / gamma in the display of a single channel.
 *
 * @author mark
 */
public final class ChannelIntensityController implements HistogramView.Listener {
   private final DataViewer viewer_;
   private final int channelIndex_;

   private ImageStats stats_;
   private Integer cameraBits_;

   private final JPanel channelPanel_ = new JPanel();
   private final JPanel histoPanel_ = new JPanel();

   private final ColorSwatch channelColorSwatch_ = new ColorSwatch();
   private final JLabel channelNameLabel_ = new JLabel();
   private final JToggleButton channelVisibleButton_ = new JToggleButton();
   private static final int COMPONENT_WHITE = 3;
   // Number of bins the accumulated float axis is divided into for display.
   private static final int FLOAT_AXIS_BINS = 256;
   private double[] whiteRatios_ = null; // non-null when white mode is active
   private long whiteMainMax_ = 0;  // main max shown by white handle; = max(R,G,B)
   private long whiteMainMin_ = -1; // min shown by black handle; = min(R,G,B); -1 = not set
   // For RGB images we manage autostretch here (not in DisplayUIController) so we
   // can honour the selected component and white-mode ratios.
   // DisplaySettings.isAutostretchEnabled() is kept false to prevent the display
   // engine from interfering; this flag tracks the user's actual intent.
   private boolean rgbAutostretchEnabled_ = false;
   private boolean suppressAutostretchDetection_ = false;
   // Non-null for float images: one mapper per component, rebuilt on each statsOrRangeChanged().
   private FloatCoordinateMapper[] floatMappers_ = null;
   // For float images: the pixel-value range of the histogram axis, accumulated over the
   // images seen so far. Each image only tells us its own min/max, so using that directly
   // would rescale the display on every frame. Widening a remembered range instead lets it
   // settle as the user browses. NaN until the first float image arrives.
   private double floatRangeMin_ = Double.NaN;
   private double floatRangeMax_ = Double.NaN;
   // Set once the user edits an axis end by hand: the axis then stays exactly where they
   // put it and no longer widens to take in new images.
   private boolean floatRangePinned_ = false;
   // Guards the one-time adoption of a range recorded in the display settings.
   private boolean floatRangeRestored_ = false;
   private boolean pickingWhiteBalancePoint_ = false;
   private long[] lastPickedValues_ = null; // pixel values tracked while in pick mode

   private final JToggleButton[] componentButtons_ = new JToggleButton[4];
   private final JButton whiteBalanceButton_ = new JButton("White Balance");

   private final HistogramView histogram_ = HistogramView.create();
   private final JButton histoRangeDownButton_ = new JButton();
   private final JButton histoRangeUpButton_ = new JButton();
   private final HistoRangeComboBoxModel histoRangeComboBoxModel_ =
         new HistoRangeComboBoxModel();
   private final JComboBox<String> histoRangeComboBox_ =
         new JComboBox<>(histoRangeComboBoxModel_);
   private final StatsPanel intensityStatsPanel_ = new StatsPanel();

   private static final class HistoRangeComboBoxModel extends DefaultComboBoxModel<String> {
      public HistoRangeComboBoxModel() {
         super(new String[] {
               "4-bit (0-15)", "5-bit (0-31)", "6-bit (0-63)",
               "7-bit (0-127)", "8-bit (0-255)", "9-bit (0-511)", "10-bit (0-1023)",
               "11-bit (0-2047)", "12-bit (0-4095)", "13-bit (0-8191)",
               "14-bit (0-16383)", "15-bit (0-32767)", "16-bit (0-65535)", "Camera Depth"
         });
      }

      public ChannelDisplaySettings getBits(ChannelDisplaySettings settings,
                                            Integer cameraBits) {
         int index = getIndexOf(getSelectedItem());
         if (index == 13) {
            // in order to prevent null pointer exception when no cameraBits info is available
            // I am not sure if this is the best default..
            if (cameraBits == null) {
               cameraBits = 16;
            }
            return settings.copyBuilder().useCameraHistoRange(true)
                  .histoRangeBits(cameraBits).build();
         }
         return settings.copyBuilder().useCameraHistoRange(false)
               .histoRangeBits(index + 4).build();
      }

      public void setBits(ChannelDisplaySettings settings) {
         String newSelection = null;
         if (settings.useCameraRange()) {
            newSelection = "Camera Depth";
         } else {
            int bits = settings.getHistoRangeBits();
            if (bits > 3 && (bits - 4) < getSize()) {
               newSelection = getElementAt(bits - 4);
            }
         }

         // Avoid updating when no change
         if (newSelection != null && !newSelection.equals(getSelectedItem())) {
            setSelectedItem(newSelection);
         }
      }
   }

   // Panel showing min/max/avg/std.
   // Use custom draw code instead of 8 separate labels, since JLabel.setText()
   // is horrendously slow on Mac OS X (seen on Yosemite, Java 6).
   private static final class StatsPanel extends JPanel {
      // Layout is:
      // MAX 99999  AVG    12345
      // MIN  1111  STD 1.23e+00

      private final Font valueFont_ = getFont().deriveFont(9.0f);
      private final Font keyFont_ = valueFont_.deriveFont(Font.BOLD);
      private final FontMetrics valueFontMetrics_ = getFontMetrics(valueFont_);
      private final int keyX1 = 0;
      private final int keyX2;
      private final int valueX1;
      private final int valueX2;
      private final int y1;
      private final int y2;
      private final int maxMinMaxWidth_;
      private final int maxAvgStdWidth_;

      private String min_;
      private String max_;
      private String mean_;
      private String stdev_;
      private int minWidth_;
      private int maxWidth_;
      private int meanWidth_;
      private int stdevWidth_;

      StatsPanel() {
         super.setOpaque(true);
         FontMetrics keyFontMetrics = super.getFontMetrics(keyFont_);

         // Wide enough for a signed float in scientific notation: float images show real
         // values such as "-2.238" or "-1.62e-01", which do not fit an integer-sized slot
         // and would otherwise be replaced by "..." in formatString().
         maxMinMaxWidth_ = valueFontMetrics_.stringWidth("-9.999e+99") + 2;
         maxAvgStdWidth_ = valueFontMetrics_.stringWidth("-9.999e+99") + 2;

         valueX1 = keyX1 + Math.max(keyFontMetrics.stringWidth("MAX"),
               keyFontMetrics.stringWidth("MIN"))
               + keyFontMetrics.stringWidth(" ")
               + maxMinMaxWidth_;
         keyX2 = valueX1 + keyFontMetrics.stringWidth("  ");
         valueX2 = keyX2 + Math.max(keyFontMetrics.stringWidth("AVG"),
               keyFontMetrics.stringWidth("STD"))
               + keyFontMetrics.stringWidth(" ")
               + maxAvgStdWidth_;

         y1 = keyFontMetrics.getMaxAscent();
         y2 = y1 + keyFontMetrics.getHeight();
         int height = y2 + keyFontMetrics.getMaxDescent();

         Dimension size = new Dimension(valueX2, height);
         super.setMinimumSize(size);
         super.setPreferredSize(size);
         super.setMaximumSize(size);

         min_ = max_ = mean_ = stdev_ = "-";
         minWidth_ = maxWidth_ = meanWidth_ = stdevWidth_ =
               valueFontMetrics_.stringWidth("-");
      }

      private String formatString(String given, int width) {
         if (given == null || given.isEmpty()) {
            return "-";
         }
         if (valueFontMetrics_.stringWidth(given) > width) {
            return "...";
         }
         return given;
      }

      void setMin(String minString) {
         min_ = formatString(minString, maxMinMaxWidth_);
         minWidth_ = valueFontMetrics_.stringWidth(min_);
         repaint();
      }

      void setMax(String maxString) {
         max_ = formatString(maxString, maxMinMaxWidth_);
         maxWidth_ = valueFontMetrics_.stringWidth(max_);
         repaint();
      }

      void setMean(String meanString) {
         mean_ = formatString(meanString, maxAvgStdWidth_);
         meanWidth_ = valueFontMetrics_.stringWidth(mean_);
         repaint();
      }

      void setStdev(String stdevString) {
         stdev_ = formatString(stdevString, maxAvgStdWidth_);
         stdevWidth_ = valueFontMetrics_.stringWidth(stdev_);
         repaint();
      }

      @Override
      public void paintComponent(Graphics g) {
         super.paintComponent(g);
         g = g.create();
         g.setFont(keyFont_);
         g.drawString("MAX", keyX1, y1);
         g.drawString("MIN", keyX1, y2);
         g.drawString("AVG", keyX2, y1);
         g.drawString("STD", keyX2, y2);
         g.setFont(valueFont_);
         g.drawString(max_, valueX1 - maxWidth_, y1);
         g.drawString(min_, valueX1 - minWidth_, y2);
         g.drawString(mean_, valueX2 - meanWidth_, y1);
         g.drawString(stdev_, valueX2 - stdevWidth_, y2);
      }
   }

   /**
    * Note: because we use a UIManager to set the Look and Feel,
    * the normal method to set the color of the button does not work.
    * As a workaround, set a border that fills the complete button and
    * color it.  Works on Windows...
    */
   private static final class ColorSwatch extends JButton {
      private Color color_ = Color.WHITE;

      ColorSwatch() {
         super.setPreferredSize(new Dimension(16, 16));
         super.setMinimumSize(new Dimension(16, 16));
         super.setOpaque(true); // Needed for background to be drawn
         super.setBorder(BorderFactory.createLineBorder(color_, 8));
         super.setBackground(color_);
      }

      void setColor(Color color) {
         color_ = color;
         super.setBorder(BorderFactory.createLineBorder(color_, 8));
         super.setBackground(color_);
      }

      Color getColor() {
         return color_;
      }
   }

   private static final Color[] RGB_COLORS = new Color[] {
         Color.RED, Color.GREEN, Color.BLUE
   };

   private static final Icon[] RGB_ICONS_ACTIVE = new Icon[] {
         IconLoader.getIcon("/org/micromanager/icons/rgb_red.png"),
         IconLoader.getIcon("/org/micromanager/icons/rgb_green.png"),
         IconLoader.getIcon("/org/micromanager/icons/rgb_blue.png")
   };
   private static final Icon[] RGB_ICONS_INACTIVE = new Icon[] {
         IconLoader.getIcon("/org/micromanager/icons/rgb_red_blank.png"),
         IconLoader.getIcon("/org/micromanager/icons/rgb_green_blank.png"),
         IconLoader.getIcon("/org/micromanager/icons/rgb_blue_blank.png")
   };

   private static Icon makeFilledSquareIcon(final Color fill, final Color border,
                                            final boolean withTriangle) {
      return new Icon() {
         @Override
         public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
            g.setColor(fill);
            g.fillRect(x, y, 14, 14);
            g.setColor(border);
            g.drawRect(x, y, 13, 13);
            if (withTriangle) {
               // Triangle in top-right corner, matching the R/G/B active icons.
               // Use dark gray to contrast against the white background.
               g.setColor(Color.DARK_GRAY);
               int[] tx = {x + 7, x + 13, x + 13};
               int[] ty = {y + 1, y + 1, y + 7};
               g.fillPolygon(tx, ty, 3);
            }
         }

         @Override public int getIconWidth()  {
            return 14;
         }

         @Override public int getIconHeight() {
            return 14;
         }

      };
   }

   private static final Icon WHITE_ICON_ACTIVE   = makeFilledSquareIcon(Color.WHITE,
            Color.DARK_GRAY, true);
   private static final Icon WHITE_ICON_INACTIVE = makeFilledSquareIcon(new Color(
            180, 180, 180), Color.GRAY, false);


   /**
    * Create an instance of the ChannelIntensityController.
    *
    * @param viewer Display that will use this instance
    * @param channelIndex Number of the channel (zero based) in this display
    * @return Instance of the controller.
    */
   public static ChannelIntensityController create(DataViewer viewer, int channelIndex) {
      ChannelIntensityController instance = new ChannelIntensityController(viewer, channelIndex);
      instance.histogram_.addListener(instance);
      instance.newDisplaySettings(viewer.getDisplaySettings());
      viewer.registerForEvents(instance);
      return instance;
   }

   private ChannelIntensityController(DataViewer viewer, int channelIndex) {
      viewer_ = viewer;
      channelIndex_ = channelIndex;

      for (int i = 0; i < 3; ++i) {
         componentButtons_[i] = new JToggleButton(RGB_ICONS_INACTIVE[i]);
         componentButtons_[i].setSelectedIcon(RGB_ICONS_ACTIVE[i]);
         componentButtons_[i].setBorder(BorderFactory.createEmptyBorder());
         componentButtons_[i].setBorderPainted(false);
         componentButtons_[i].setOpaque(true);
         componentButtons_[i].setVisible(false);
         componentButtons_[i].setSelected(i == 0);
         final int ii = i;
         componentButtons_[i].addActionListener((ActionEvent e) -> handleComponentSelection(ii));
      }
      componentButtons_[COMPONENT_WHITE] = new JToggleButton(WHITE_ICON_INACTIVE);
      componentButtons_[COMPONENT_WHITE].setSelectedIcon(WHITE_ICON_ACTIVE);
      componentButtons_[COMPONENT_WHITE].setBorder(BorderFactory.createEmptyBorder());
      componentButtons_[COMPONENT_WHITE].setBorderPainted(false);
      componentButtons_[COMPONENT_WHITE].setOpaque(true);
      componentButtons_[COMPONENT_WHITE].setVisible(false);
      componentButtons_[COMPONENT_WHITE].setSelected(false);
      componentButtons_[COMPONENT_WHITE].addActionListener(
            (ActionEvent e) -> handleComponentSelection(COMPONENT_WHITE));
      javax.swing.ButtonGroup rgbGroup = new javax.swing.ButtonGroup();
      for (JToggleButton btn : componentButtons_) {
         rgbGroup.add(btn);
      }

      channelPanel_.setLayout(new MigLayout(
            new LC().fill().insets("0").gridGap("0", "0")));
      channelPanel_.setOpaque(true);
      channelPanel_.add(channelVisibleButton_, new CC().gapBefore("rel").split(2));
      channelPanel_.add(channelColorSwatch_, new CC().gapBefore("rel").width("32").wrap());
      channelPanel_.add(channelNameLabel_, new CC().gapBefore("rel").pushX().wrap("rel:rel:push"));
      channelPanel_.add(componentButtons_[0], new CC().gapBefore("push").gapAfter("0").split(4));
      channelPanel_.add(componentButtons_[1], new CC().gapAfter("0"));
      channelPanel_.add(componentButtons_[2], new CC().gapAfter("0"));
      channelPanel_.add(componentButtons_[COMPONENT_WHITE], new CC().gapAfter("push").wrap("rel"));
      JButton fullscaleButton = new JButton("Fullscale");
      channelPanel_.add(fullscaleButton, new CC().pushX().wrap());
      JButton autostretchOnceButton = new JButton("Auto Once");
      channelPanel_.add(autostretchOnceButton, new CC().pushX().wrap("rel"));
      whiteBalanceButton_.setVisible(false);
      whiteBalanceButton_.addActionListener((ActionEvent e) -> showWhiteBalancePopup());
      channelPanel_.add(whiteBalanceButton_, new CC().pushX().wrap());

      histoPanel_.setLayout(new MigLayout(
            new LC().fill().insets("0").gridGap("0", "0")));
      histoPanel_.setOpaque(true);
      histoPanel_.add(histogram_, new CC().grow().push().wrap("rel"));

      histoPanel_.add(histoRangeDownButton_, new CC().split(5).gapBefore("12").gapAfter("0"));
      histoPanel_.add(histoRangeComboBox_, new CC().gapAfter("0").pad(0, -4, 0, 4));
      histoPanel_.add(histoRangeUpButton_, new CC().gapAfter("push"));

      histoPanel_.add(intensityStatsPanel_, new CC().gapAfter("push"));
      JToggleButton intensityLinkButton = new JToggleButton();
      histoPanel_.add(intensityLinkButton, new CC());

      Font labelFont = channelNameLabel_.getFont()
            .deriveFont(11.0f).deriveFont(Font.BOLD);
      channelNameLabel_.setFont(labelFont);
      channelNameLabel_.setText(viewer.getDataProvider().getSummaryMetadata()
            .getSafeChannelName(channelIndex));

      channelVisibleButton_.setMargin(new Insets(0, 0, 0, 0));
      channelVisibleButton_.setPreferredSize(new Dimension(23, 23));
      channelVisibleButton_.setMaximumSize(new Dimension(23, 23));
      channelVisibleButton_.setIcon(
            IconLoader.getIcon("/org/micromanager/icons/eye-out.png"));
      channelVisibleButton_.setSelectedIcon(
            IconLoader.getIcon("/org/micromanager/icons/eye.png"));
      channelVisibleButton_.addActionListener((ActionEvent e) -> handleVisible());
      channelColorSwatch_.addActionListener((ActionEvent e) ->
            handleColor(channelColorSwatch_.getColor()));

      Font buttonFont = fullscaleButton.getFont().deriveFont(9.0f);
      fullscaleButton.setMargin(new Insets(0, 0, 0, 0));
      fullscaleButton.setFont(buttonFont);
      fullscaleButton.setPreferredSize(new Dimension(72, 23));
      fullscaleButton.setMaximumSize(new Dimension(72, 23));
      fullscaleButton.addActionListener((ActionEvent e) -> handleFullscale());
      autostretchOnceButton.setMargin(new Insets(0, 0, 0, 0));
      autostretchOnceButton.setFont(buttonFont);
      autostretchOnceButton.setPreferredSize(new Dimension(72, 23));
      autostretchOnceButton.setMaximumSize(new Dimension(72, 23));
      autostretchOnceButton.addActionListener((ActionEvent e) -> handleAutoscale());

      histoRangeDownButton_.setMaximumSize(new Dimension(20, 20));
      histoRangeDownButton_.setIcon(IconLoader.getIcon(
            "/org/micromanager/icons/triangle_left.png"));
      histoRangeDownButton_.addActionListener((ActionEvent e) -> {
         int index = histoRangeComboBox_.getSelectedIndex();
         if (index > 0) {
            histoRangeComboBox_.setSelectedIndex(index - 1);
         }
      });
      histoRangeUpButton_.setMaximumSize(new Dimension(20, 20));
      histoRangeUpButton_.setIcon(IconLoader.getIcon(
            "/org/micromanager/icons/triangle_right.png"));
      histoRangeUpButton_.addActionListener((ActionEvent e) -> {
         int index = histoRangeComboBox_.getSelectedIndex();
         if (index < histoRangeComboBoxModel_.getSize() - 1) {
            histoRangeComboBox_.setSelectedIndex(index + 1);
         }
      });

      histoRangeComboBox_.setMaximumSize(new Dimension(128, 20));
      histoRangeComboBox_.setFocusable(false);
      histoRangeComboBox_.setFont(histoRangeComboBox_.getFont().deriveFont(10.0f));
      histoRangeComboBox_.setMaximumRowCount(16);
      histoRangeComboBox_.setSelectedItem("Camera Depth");
      histoRangeComboBox_.addActionListener((ActionEvent e) -> {
         statsOrRangeChanged();
         updateHistoRangeButtonStates();
      });

      // TODO This will actually be a popup button!
      intensityLinkButton.setMaximumSize(new Dimension(30, 20));
      intensityLinkButton.setMinimumSize(new Dimension(30, 20));
      intensityLinkButton.setIcon(IconLoader.getIcon(
            "/org/micromanager/icons/linkflat.png"));
      intensityLinkButton.setSelectedIcon(IconLoader.getIcon(
            "/org/micromanager/icons/linkflat_active.png"));

      //updateHistoRangeButtonStates();
      // Needed to pick up the current DisplaySettings, 
      newDisplaySettings(viewer.getDisplaySettings());

      updateHistoRangeButtonStates();
   }

   void detach() {
      viewer_.unregisterForEvents(this);
   }

   JPanel getChannelPanel() {
      return channelPanel_;
   }

   JPanel getHistogramPanel() {
      return histoPanel_;
   }

   /**
    * Formats a floating point intensity for the stats readout.
    *
    * <p>Switches to scientific notation for magnitudes that would otherwise be shown as
    * mostly zeros or as an unreadably long integer.
    *
    * @param v value to format
    * @return the formatted value, or null if it is not a number
    */
   static String formatStatValue(double v) {
      if (Double.isNaN(v)) {
         return null;
      }
      double m = Math.abs(v);
      if (m != 0.0 && (m < 1e-3 || m >= 1e5)) {
         return String.format("%.2e", v);
      }
      return String.format("%.4g", v);
   }

   @MustCallOnEDT
   private void statsOrRangeChanged() {
      if (stats_ == null) {
         histogram_.clearGraphs();
         histogram_.setOverlayText("NO DATA");
         return;
      }
      histogram_.setOverlayText(null);

      int selectedComponent = getSelectedComponent();
      // In white mode, show stats for component 0 (Red) as representative
      int statsComponent = (selectedComponent == COMPONENT_WHITE) ? 0 : selectedComponent;
      ComponentStats selectedStats = stats_.getComponentStats(statsComponent);

      final DisplaySettings displaySettings = viewer_.getDisplaySettings();
      boolean ignoreZeros = displaySettings.isAutoscaleIgnoringZeros();

      if (selectedStats.isFloat()) {
         // Float values are genuinely fractional and often negative, so show them as such
         // rather than rounding (which collapses a mean of -0.16 to 0) or hiding them.
         double min = ignoreZeros ? selectedStats.getFloatMinIntensityExcludingZeros()
                                  : selectedStats.getFloatMinIntensity();
         intensityStatsPanel_.setMin(formatStatValue(min));
         intensityStatsPanel_.setMax(formatStatValue(selectedStats.getFloatMaxIntensity()));
         double mean = ignoreZeros ? selectedStats.getFloatMeanIntensityExcludingZeros()
                                   : selectedStats.getFloatMeanIntensity();
         intensityStatsPanel_.setMean(formatStatValue(mean));
         double stdev = ignoreZeros ? selectedStats.getFloatStandardDeviationExcludingZeros()
                                    : selectedStats.getFloatStandardDeviation();
         intensityStatsPanel_.setStdev(Double.isNaN(stdev) ? null :
               String.format("%1.2e", stdev));
      } else {
         long min = ignoreZeros ? selectedStats.getMinIntensityExcludingZeros()
                                : selectedStats.getMinIntensity();
         intensityStatsPanel_.setMin(min >= 0 ? Long.toString(min) : null);
         long max = selectedStats.getMaxIntensity();
         intensityStatsPanel_.setMax(max >= 0 ? Long.toString(max) : null);
         long mean = ignoreZeros ? selectedStats.getMeanIntensityExcludingZeros()
                                 : selectedStats.getMeanIntensity();
         intensityStatsPanel_.setMean(mean >= 0 ? Long.toString(mean) : null);
         double stdev = ignoreZeros ? selectedStats.getStandardDeviationExcludingZeros()
                                    : selectedStats.getStandardDeviation();
         intensityStatsPanel_.setStdev(Double.isNaN(stdev) ? null :
               String.format("%1.2e", stdev));
      }

      cameraBits_ = stats_.getComponentStats(0).getBitDepth();
      updateHistoRangeControlsEnabled();
      ChannelDisplaySettings chanDispSettings = histoRangeComboBoxModel_.getBits(
              displaySettings.getChannelSettings(channelIndex_), cameraBits_);

      int rangeBits;
      if (cameraBits_ == null || !chanDispSettings.useCameraRange()) {
         rangeBits = chanDispSettings.getHistoRangeBits();
      } else {
         rangeBits = cameraBits_;
      }

      int numComponents = stats_.getNumberOfComponents();
      setRGBMode(numComponents > 1);

      restoreFloatRangeFromSettings(displaySettings);
      accumulateFloatRange();
      seedFloatScalingIfUnset(displaySettings);

      boolean whiteMode = getSelectedComponent() == COMPONENT_WHITE;
      for (int c = 0; c < numComponents; c++) {
         ComponentStats cStats = stats_.getComponentStats(c);
         long[] data = cStats.getInRangeHistogram();
         if (data != null) {
            // For float images: the histogram is built in actual pixel-value coordinates
            // with a data-driven range [floor(fMin), ceil(fMax)] so the X-axis shows
            // real pixel values. For integer images: use the bit-depth combo box selection,
            // capping at 30 bits to avoid Java int-shift overflow (1<<32 == 1).
            if (cStats.isFloat()) {
               // Use bin-index coordinate space: rangeMin=0, rangeMax=binCount (~256).
               // The X axis shows bin indices; labels are formatted as pixel values via
               // the FloatCoordinateMapper wired into HistogramView. The mapper spans the
               // accumulated range rather than this image's own range, so the axis stays
               // put as one steps through images.
               FloatCoordinateMapper mapper = new FloatCoordinateMapper(
                     floatRangeMin_, floatRangeMax_, FLOAT_AXIS_BINS);
               if (floatMappers_ == null || floatMappers_.length != numComponents) {
                  floatMappers_ = new FloatCoordinateMapper[numComponents];
               }
               floatMappers_[c] = mapper;
               long binCount = Math.max(1L, mapper.getBinCount());
               // The image's bins span its own min..max, which need not coincide with the
               // axis; resample them onto the axis so every bin lands at its true pixel
               // value and anything outside the axis is dropped rather than displaced.
               long[] axisData = resampleOntoAxis(cStats, mapper, (int) binCount);
               histogram_.setComponentGraph(c, axisData, axisData.length, 0L, binCount);
               histogram_.setComponentFloatMapper(c, mapper);
               histogram_.setComponentRangeMaxLabel(c, mapper.formatBinIndex(binCount));
               histogram_.setComponentRangeMinLabel(c, mapper.formatBinIndex(0L));
            } else {
               if (c == 0) {
                  floatMappers_ = null; // integer image: clear all mappers
               }
               histogram_.setComponentFloatMapper(c, null);
               histogram_.setComponentRangeMaxLabel(c, null);
               histogram_.setComponentRangeMinLabel(c, null);
               int clampedRangeBits = Math.min(rangeBits, 30);
               int lengthToUse = Math.min(data.length, (1 << clampedRangeBits) - 1);
               if (lengthToUse <= 0) {
                  lengthToUse = data.length;
               }
               histogram_.setComponentGraph(c, data, lengthToUse, lengthToUse);
            }
            histogram_.setROIIndicator(cStats.isROIStats());
         }
         updateScalingIndicators(displaySettings, cStats, c);
      }
      if (whiteMode) {
         updateWhiteScalingIndicator(displaySettings, stats_.getComponentStats(0));
      }
      // Only float images have a meaningful, user-choosable axis range; for integer data
      // the axis is determined by the bit depth.
      histogram_.setAxisRangeEditable(
            numComponents > 0 && stats_.getComponentStats(0).isFloat());
   }

   @MustCallOnEDT
   // Updates the histogram's scaling indicator (dotted lines + handle) for a single
   // R/G/B component (indices 0–2). Always reflects the component's own
   // ComponentDisplaySettings — independent of white mode.
   private void updateScalingIndicators(DisplaySettings settings,
                                        ComponentStats componentStats, int component) {
      long min;
      long max;
      if (settings.isAutostretchEnabled()) {
         double q = settings.getAutoscaleIgnoredQuantile();
         FloatCoordinateMapper mapper = getFloatMapper(component);
         if (mapper != null) {
            // Stay in pixel values and convert to bin positions only for drawing. Going
            // through the long autoscale would round the bounds to whole units first,
            // which for a range such as 2.7..22.7 pins the handles at 3 and 22 no matter
            // what the ignore fraction is.
            double fMin;
            double fMax;
            if (settings.isAutoscaleIgnoringZeros()) {
               fMin = 0.0;
               fMax = componentStats.getFloatAutoscaleMaxForQuantileIgnoringZeros(q);
            } else {
               double[] fMinMax = new double[2];
               componentStats.getFloatAutoscaleMinMaxForQuantile(q, fMinMax);
               fMin = fMinMax[0];
               fMax = fMinMax[1];
            }
            int binCount = mapper.getBinCount();
            long binMax = Math.max(1, Math.min(binCount,
                  mapper.pixelValueToBinIndex(fMax)));
            long binMin = Math.max(0, Math.min(binMax - 1,
                  mapper.pixelValueToBinIndex(fMin)));
            histogram_.setComponentScaling(component, binMin, binMax);
            return;
         }
         if (settings.isAutoscaleIgnoringZeros()) {
            min = 0L;
            max = componentStats.getAutoscaleMaxForQuantileIgnoringZeros(q);
         } else {
            long[] minMax = new long[2];
            componentStats.getAutoscaleMinMaxForQuantile(q, minMax);
            min = minMax[0];
            max = minMax[1];
         }
      } else {
         ComponentDisplaySettings componentSettings =
               settings.getChannelSettings(channelIndex_)
                     .getComponentSettings(component);
         if (componentStats.isFloat() && floatMappers_ != null
               && component < floatMappers_.length && floatMappers_[component] != null) {
            FloatCoordinateMapper mapper = floatMappers_[component];
            int binCount = mapper.getBinCount();
            long clampedMin;
            long clampedMax;
            if (componentSettings.hasFloatScaling()) {
               // Stored as pixel values; convert to bin positions for drawing only.
               clampedMax = mapper.pixelValueToBinIndex(
                     componentSettings.getFloatScalingMaximum());
               clampedMin = mapper.pixelValueToBinIndex(
                     componentSettings.getFloatScalingMinimum());
               clampedMax = Math.max(1, Math.min(binCount, clampedMax));
               clampedMin = Math.max(0, Math.min(clampedMax - 1, clampedMin));
            } else {
               long storedMax = componentSettings.getScalingMaximum();
               long storedMin = componentSettings.getScalingMinimum();
               clampedMax = (storedMax == Long.MAX_VALUE) ? binCount
                                 : Math.min(binCount, storedMax);
               clampedMin = Math.max(0, Math.min(clampedMax - 1, storedMin));
            }
            histogram_.setComponentScaling(component, clampedMin, clampedMax);
            return;
         }
         max = Math.min(componentStats.getHistogramRangeMax(),
               componentSettings.getScalingMaximum());
         long rangeMin = componentStats.getHistogramRangeMin();
         min = Math.max(rangeMin, Math.min(max - 1,
               componentSettings.getScalingMinimum()));
      }
      histogram_.setComponentScaling(component, min, max);
   }

   // Updates the white handle (HistogramView slot COMPONENT_WHITE) to show
   // whiteMainMax_ as the max handle and whiteMainMin_ as the min handle.
   private void updateWhiteScalingIndicator(DisplaySettings settings,
                                            ComponentStats comp0Stats) {
      long rangeMax = comp0Stats.getHistogramRangeMax();
      long max = whiteMainMax_ > 0
            ? Math.min(whiteMainMax_, rangeMax)
            : Math.min(settings.getChannelSettings(channelIndex_)
                  .getComponentSettings(0).getScalingMaximum(), rangeMax);
      long min = whiteMainMin_ >= 0
            ? whiteMainMin_
            : Math.max(0, settings.getChannelSettings(channelIndex_)
                  .getComponentSettings(0).getScalingMinimum());
      // Feed component 3 the same graph data as component 0 so that rangeMax_ is
      // set in HistogramView (required for handle position calculation).
      long[] graph = comp0Stats.getInRangeHistogram();
      if (graph != null && rangeMax > 0) {
         histogram_.setComponentGraph(COMPONENT_WHITE, graph, graph.length, rangeMax);
         histogram_.setComponentScaling(COMPONENT_WHITE, min, max);
      }
   }

   @MustCallOnEDT
   private void updateHistoRangeControlsEnabled() {
      boolean isFloat = stats_ != null
            && stats_.getNumberOfComponents() > 0
            && stats_.getComponentStats(0).isFloat();
      histoRangeComboBox_.setEnabled(!isFloat);
      histoRangeDownButton_.setEnabled(!isFloat && histoRangeComboBox_.getSelectedIndex() > 0);
      histoRangeUpButton_.setEnabled(!isFloat
            && histoRangeComboBox_.getSelectedIndex() < histoRangeComboBoxModel_.getSize() - 2);
   }

   @MustCallOnEDT
   private void updateHistoRangeButtonStates() {
      updateHistoRangeControlsEnabled();
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         newDisplaySettings = oldDisplaySettings
               .copyBuilderWithChannelSettings(channelIndex_,
                     histoRangeComboBoxModel_.getBits(channelSettings,
                           cameraBits_)).build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
   }

   boolean hasStats() {
      return stats_ != null;
   }

   @MustCallOnEDT
   void setStats(ImageStats stats) {
      stats_ = stats;
      if (rgbAutostretchEnabled_) {
         applyRGBAutostretch();
      }
      statsOrRangeChanged();
   }

   @MustCallOnEDT
   void setHistogramLogYAxis(boolean enable) {
      histogram_.setLogIntensity(enable);
   }

   @MustCallOnEDT
   void setHistogramOverlayText(String text) {
      histogram_.setOverlayText(text);
   }

   private void handleVisible() {
      boolean visible = channelVisibleButton_.isSelected();
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         newDisplaySettings = oldDisplaySettings
               .copyBuilderWithChannelSettings(channelIndex_,
                     channelSettings.copyBuilder().visible(visible).build())
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
   }

   private void handleColor(Color color) {
      Color newColor = JColorChooser.showDialog(
            histoPanel_.getTopLevelAncestor(), "Channel Color", color);

      if (newColor != null) {
         DisplaySettings oldDisplaySettings;
         DisplaySettings newDisplaySettings;
         do {
            oldDisplaySettings = viewer_.getDisplaySettings();
            ChannelDisplaySettings channelSettings
                  = oldDisplaySettings.getChannelSettings(channelIndex_);
            newDisplaySettings = oldDisplaySettings
                  .copyBuilderWithChannelSettings(channelIndex_,
                        channelSettings.copyBuilder().color(newColor).build()).build();
         } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
      }
   }

   @MustCallOnEDT
   private void setRGBMode(boolean isRGB) {
      boolean wasRGB = componentButtons_[0].isVisible();
      if (isRGB == wasRGB) {
         return;
      }

      for (int i = 0; i < componentButtons_.length; i++) {
         componentButtons_[i].setVisible(isRGB);
      }
      if (!isRGB) {
         whiteBalanceButton_.setVisible(false);
      }

      if (isRGB && !wasRGB) {
         // Auto-select White when entering RGB mode for the first time.
         // captureWhiteRatios() will derive ratios from the stored component maxima.
         handleComponentSelection(COMPONENT_WHITE);
      } else {
         // Reapply display settings with correct component handling
         newDisplaySettings(viewer_.getDisplaySettings());
      }
   }

   @MustCallOnEDT
   private void handleComponentSelection(int component) {
      for (int i = 0; i < componentButtons_.length; i++) {
         componentButtons_[i].setSelected(i == component);
      }
      if (component == COMPONENT_WHITE) {
         // applyCurrentWhiteRatios() calls captureWhiteRatios() internally when needed.
         // Calling it here ensures first-seen channels immediately persist sensible
         // values rather than raw Long.MAX_VALUE defaults.
         applyCurrentWhiteRatios();
         // Use HistogramView slot 3 for the white handle so that Red (slot 0)
         // keeps its own independent dotted line and handle.
         for (int c = 0; c < RGB_COLORS.length; c++) {
            histogram_.setComponentColor(c, RGB_COLORS[c], RGB_COLORS[c]);
         }
         histogram_.setComponentColor(COMPONENT_WHITE, Color.WHITE, Color.WHITE);
         histogram_.setSelectedComponent(COMPONENT_WHITE);
         whiteBalanceButton_.setVisible(true);
      } else {
         whiteRatios_ = null;
         whiteMainMax_ = 0;
         whiteMainMin_ = -1;
         pickingWhiteBalancePoint_ = false;
         lastPickedValues_ = null;
         // Deactivate the white slot so its dotted line disappears.
         histogram_.clearComponentGraph(COMPONENT_WHITE);
         histogram_.setComponentColor(0, Color.RED, Color.RED);
         histogram_.setSelectedComponent(component);
         whiteBalanceButton_.setVisible(false);
      }
      // Refresh scaling indicators immediately so the handle position reflects the
      // new mode (white main max vs Red's individual max) without waiting for the
      // next stats update.
      statsOrRangeChanged();
      // If autostretch is active (managed by us), re-apply for the new selection
      if (rgbAutostretchEnabled_) {
         applyRGBAutostretch();
      }
   }

   @MustCallOnEDT
   private int getSelectedComponent() {
      for (int i = 0; i < componentButtons_.length; i++) {
         if (componentButtons_[i].isSelected()) {
            return i;
         }
      }
      return 0; // component 0 selected by default
   }

   @MustCallOnEDT
   private void captureWhiteRatios() {
      if (whiteRatios_ != null) {
         return; // already set (e.g. restored from profile or set by white balance)
      }
      // Use the effective (displayed) max for each component: clamped to histogram range.
      // This matches what updateScalingIndicators uses and avoids Long.MAX_VALUE defaults
      // polluting the ratio when a channel hasn't been manually adjusted yet.
      DisplaySettings settings = viewer_.getDisplaySettings();
      ChannelDisplaySettings ch = settings.getChannelSettings(channelIndex_);
      long[] effectiveMax = new long[3];
      long mainMin = Long.MAX_VALUE;
      for (int c = 0; c < 3; c++) {
         long settingsMax = ch.getComponentSettings(c).getScalingMaximum();
         if (stats_ != null) {
            long rangeMax = stats_.getComponentStats(c).getHistogramRangeMax();
            effectiveMax[c] = Math.min(settingsMax, rangeMax);
         } else {
            effectiveMax[c] = settingsMax == Long.MAX_VALUE ? 255 : settingsMax;
         }
         mainMin = Math.min(mainMin, Math.max(0, ch.getComponentSettings(c).getScalingMinimum()));
      }
      long mainMax = Math.max(effectiveMax[0], Math.max(effectiveMax[1], effectiveMax[2]));
      whiteMainMax_ = mainMax;
      whiteMainMin_ = mainMin == Long.MAX_VALUE ? 0 : mainMin;
      if (mainMax > 0) {
         whiteRatios_ = new double[] {
               (double) effectiveMax[0] / mainMax,
               (double) effectiveMax[1] / mainMax,
               (double) effectiveMax[2] / mainMax
         };
      } else {
         whiteRatios_ = new double[] {1.0, 1.0, 1.0};
      }
   }

   boolean isRgbAutostretchEnabled() {
      return rgbAutostretchEnabled_;
   }

   void setRgbAutostretchEnabled(boolean enabled) {
      rgbAutostretchEnabled_ = enabled;
   }

   /**
    * Adopts a float histogram axis range recorded in the display settings.
    *
    * <p>Called before accumulating, so that a range restored from a saved dataset (or set
    * in an earlier session) takes precedence over one derived from the images.
    *
    * @param settings current display settings
    */
   @MustCallOnEDT
   private void restoreFloatRangeFromSettings(DisplaySettings settings) {
      if (floatRangeRestored_) {
         return;
      }
      ChannelDisplaySettings channelSettings = settings.getChannelSettings(channelIndex_);
      if (!channelSettings.hasFloatHistoRange()) {
         return;
      }
      floatRangeRestored_ = true;
      floatRangeMin_ = channelSettings.getFloatHistoRangeMinimum();
      floatRangeMax_ = channelSettings.getFloatHistoRangeMaximum();
      floatRangePinned_ = channelSettings.isFloatHistoRangePinned();
   }

   /**
    * Records the current float histogram axis in the display settings so that it survives
    * closing and reopening the dataset.
    *
    * @param pinned whether the axis was chosen by the user
    */
   @MustCallOnEDT
   private void storeFloatRangeInSettings(boolean pinned) {
      if (Double.isNaN(floatRangeMin_) || Double.isNaN(floatRangeMax_)
            || floatRangeMax_ <= floatRangeMin_) {
         return;
      }
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         if (channelSettings.hasFloatHistoRange()
               && channelSettings.getFloatHistoRangeMinimum() == floatRangeMin_
               && channelSettings.getFloatHistoRangeMaximum() == floatRangeMax_
               && channelSettings.isFloatHistoRangePinned() == pinned) {
            return;
         }
         newDisplaySettings = oldDisplaySettings
               .copyBuilderWithChannelSettings(channelIndex_,
                     channelSettings.copyBuilder()
                           .floatHistoRange(floatRangeMin_, floatRangeMax_)
                           .floatHistoRangePinned(pinned)
                           .build())
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
   }

   /**
    * Widens the remembered float axis range to include the current image.
    *
    * <p>Only ever widens: a range that tracked each image exactly would rescale the display
    * on every frame, which is what this exists to avoid. Browsing therefore makes the axis
    * settle rather than oscillate.
    */
   @MustCallOnEDT
   private void accumulateFloatRange() {
      if (stats_ == null || floatRangePinned_) {
         return;
      }
      for (int c = 0; c < stats_.getNumberOfComponents(); ++c) {
         ComponentStats cStats = stats_.getComponentStats(c);
         if (!cStats.isFloat()) {
            return; // Integer image: nothing to accumulate.
         }
         double frameMin = cStats.getHistogramRangeMinDouble();
         double frameMax = frameMin
               + cStats.getBinWidthDouble() * cStats.getHistogramBinCount();
         if (Double.isNaN(frameMin) || Double.isNaN(frameMax)) {
            continue;
         }
         if (Double.isNaN(floatRangeMin_) || frameMin < floatRangeMin_) {
            floatRangeMin_ = frameMin;
         }
         if (Double.isNaN(floatRangeMax_) || frameMax > floatRangeMax_) {
            floatRangeMax_ = frameMax;
         }
      }
      // A single-valued image gives a zero-width range; widen it so the axis is drawable.
      if (!Double.isNaN(floatRangeMin_) && floatRangeMax_ <= floatRangeMin_) {
         floatRangeMax_ = floatRangeMin_ + 1.0;
      }
   }

   /**
    * Gives a float channel an explicit scaling range the first time it is displayed.
    *
    * <p>Without this, the settings still hold the integer defaults (0 and Long.MAX_VALUE).
    * Those are bin indices in the legacy interpretation, so they get resolved against
    * whichever image happens to be on screen -- the display keeps rescaling itself, and the
    * numbers shown next to the handles do not match what the image is actually scaled to.
    * Seeding from this image's autoscale range makes the two agree from the start and gives
    * a sensible first view.
    *
    * <p>Only ever fills in a missing range; a range the user or a saved settings file
    * provided is left alone.
    *
    * @param settings current display settings
    */
   @MustCallOnEDT
   private void seedFloatScalingIfUnset(DisplaySettings settings) {
      if (stats_ == null || settings.isAutostretchEnabled()) {
         return;
      }
      int numComponents = stats_.getNumberOfComponents();
      if (numComponents < 1 || !stats_.getComponentStats(0).isFloat()) {
         return;
      }
      ChannelDisplaySettings channelSettings = settings.getChannelSettings(channelIndex_);
      boolean anyUnset = false;
      for (int c = 0; c < numComponents; ++c) {
         if (!channelSettings.getComponentSettings(c).hasFloatScaling()) {
            anyUnset = true;
            break;
         }
      }
      if (!anyUnset) {
         return;
      }

      double q = settings.getAutoscaleIgnoredQuantile();
      boolean ignoreZeros = settings.isAutoscaleIgnoringZeros();
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings chSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         ChannelDisplaySettings.Builder builder = chSettings.copyBuilder();
         boolean changed = false;
         for (int c = 0; c < numComponents; ++c) {
            if (chSettings.getComponentSettings(c).hasFloatScaling()) {
               continue;
            }
            ComponentStats cStats = stats_.getComponentStats(c);
            // Use the float autoscale, which keeps the quantiles as pixel values. The
            // long-valued one rounds them, so data spanning less than a unit would seed
            // a range of 0..1 or wider than the data itself.
            double seedMinValue;
            double seedMaxValue;
            if (ignoreZeros) {
               seedMinValue = 0.0;
               seedMaxValue = cStats.getFloatAutoscaleMaxForQuantileIgnoringZeros(q);
            } else {
               double[] minMax = new double[2];
               cStats.getFloatAutoscaleMinMaxForQuantile(q, minMax);
               seedMinValue = minMax[0];
               seedMaxValue = minMax[1];
            }
            if (seedMaxValue <= seedMinValue) {
               // Degenerate (e.g. constant image); fall back to the accumulated axis.
               seedMinValue = floatRangeMin_;
               seedMaxValue = floatRangeMax_;
            }
            if (Double.isNaN(seedMinValue) || Double.isNaN(seedMaxValue)
                  || seedMaxValue <= seedMinValue) {
               continue;
            }
            builder.component(c, chSettings.getComponentSettings(c).copyBuilder()
                  .floatScalingRange(seedMinValue, seedMaxValue).build());
            changed = true;
         }
         if (!changed) {
            return;
         }
         newDisplaySettings = oldDisplaySettings
               .copyBuilderWithChannelSettings(channelIndex_, builder.build())
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
   }

   /**
    * Rebins a float image's histogram from the image's own value range onto the axis.
    *
    * <p>The statistics are computed with bins spanning the image's own min..max, which is
    * generally not the range the axis shows. Each source bin is distributed over the axis
    * bins it overlaps, so that a count always appears at the pixel value it came from.
    * Counts falling outside the axis are dropped, which is what makes data beyond the
    * chosen range read as clipped rather than piling up at an end.
    *
    * <p>Each output bin is rounded independently, so the total count is not exactly
    * conserved. That is fine for a display histogram, which is only ever drawn.
    *
    * @param cStats statistics of the image
    * @param mapper mapper describing the axis
    * @param axisBins number of bins on the axis
    * @return counts per axis bin
    */
   private static long[] resampleOntoAxis(ComponentStats cStats,
                                          FloatCoordinateMapper mapper, int axisBins) {
      long[] src = cStats.getInRangeHistogram();
      long[] out = new long[Math.max(1, axisBins)];
      if (src == null || src.length == 0) {
         return out;
      }
      double srcBinWidth = cStats.getBinWidthDouble();
      double srcMin = cStats.getHistogramRangeMinDouble();
      double axisMin = mapper.getRangeMin();
      double axisBinWidth = mapper.getBinWidth();
      if (axisBinWidth <= 0.0) {
         return out;
      }
      if (srcBinWidth <= 0.0) {
         // Degenerate source (all pixels identical): put every count in one axis bin.
         int idx = (int) Math.floor((srcMin - axisMin) / axisBinWidth);
         if (idx >= 0 && idx < out.length) {
            long total = 0;
            for (int i = 0; i < src.length; ++i) {
               total += src[i];
            }
            out[idx] += total;
         }
         return out;
      }
      for (int i = 0; i < src.length; ++i) {
         if (src[i] == 0) {
            continue;
         }
         // Value range covered by this source bin, in axis-bin coordinates.
         double lo = ((srcMin + i * srcBinWidth) - axisMin) / axisBinWidth;
         double hi = lo + srcBinWidth / axisBinWidth;
         if (hi <= 0.0 || lo >= out.length) {
            continue; // Entirely outside the axis
         }
         int firstBin = (int) Math.floor(Math.max(0.0, lo));
         int lastBin = (int) Math.ceil(Math.min((double) out.length, hi)) - 1;
         if (lastBin < firstBin) {
            lastBin = firstBin;
         }
         double width = hi - lo;
         for (int b = firstBin; b <= lastBin && b < out.length; ++b) {
            if (b < 0) {
               continue;
            }
            // Fraction of this source bin that falls inside axis bin b.
            double overlap = Math.min(hi, b + 1.0) - Math.max(lo, (double) b);
            if (overlap <= 0.0) {
               continue;
            }
            out[b] += Math.round(src[i] * (overlap / width));
         }
      }
      return out;
   }

   /**
    * Returns the float mapper for a component, or null if this is not a float image.
    *
    * @param component component index
    * @return the mapper, or null
    */
   private FloatCoordinateMapper getFloatMapper(int component) {
      if (floatMappers_ == null || component < 0 || component >= floatMappers_.length) {
         return null;
      }
      return floatMappers_[component];
   }

   /**
    * Returns the float scaling minimum to preserve when only the maximum is being changed.
    *
    * @param settings current component settings
    * @param mapper mapper for converting a legacy bin index
    * @return pixel value
    */
   private double currentFloatMin(ComponentDisplaySettings settings,
                                  FloatCoordinateMapper mapper) {
      if (settings.hasFloatScaling()) {
         return settings.getFloatScalingMinimum();
      }
      return mapper.binIndexToPixelValue(settings.getScalingMinimum());
   }

   /**
    * Returns the float scaling maximum to preserve when only the minimum is being changed.
    *
    * @param settings current component settings
    * @param mapper mapper for converting a legacy bin index
    * @return pixel value
    */
   private double currentFloatMax(ComponentDisplaySettings settings,
                                  FloatCoordinateMapper mapper) {
      if (settings.hasFloatScaling()) {
         return settings.getFloatScalingMaximum();
      }
      long storedMax = settings.getScalingMaximum();
      long binIndex = (storedMax == Long.MAX_VALUE) ? mapper.getBinCount() : storedMax;
      return mapper.binIndexToPixelValue(binIndex);
   }

   private long toStoredScalingValue(int component, long pixelValue) {
      if (floatMappers_ != null && component < floatMappers_.length
            && floatMappers_[component] != null) {
         return floatMappers_[component].pixelValueToBinIndex((double) pixelValue);
      }
      return pixelValue;
   }

   @MustCallOnEDT
   private void turnOffAutostretchInSettings() {
      DisplaySettings oldSettings;
      DisplaySettings newSettings;
      do {
         oldSettings = viewer_.getDisplaySettings();
         if (!oldSettings.isAutostretchEnabled()) {
            return;
         }
         newSettings = oldSettings.copyBuilder().autostretch(false).build();
      } while (!viewer_.compareAndSetDisplaySettings(oldSettings, newSettings));
   }

   // Returns {mainMax, commonMin, cMax[0], cMax[1], cMax[2]} across all components
   // at the given quantile. mainMax = brightest autoscale max so the white handle
   // lands at the image's true peak value. Per-component maxes are used to recompute
   // white ratios from actual image content rather than stale captured values.
   private long[] computeWhiteAutoscaleRange(double q, boolean ignoreZeros) {
      long mainMax = 0;
      long commonMin = Long.MAX_VALUE;
      int nComponents = stats_.getNumberOfComponents();
      long[] cMaxes = new long[nComponents];
      for (int c = 0; c < nComponents; c++) {
         long cMax;
         long cMin;
         if (ignoreZeros) {
            cMax = stats_.getComponentStats(c).getAutoscaleMaxForQuantileIgnoringZeros(q);
            cMin = 0L;
         } else {
            long[] minMax = new long[2];
            stats_.getComponentStats(c).getAutoscaleMinMaxForQuantile(q, minMax);
            cMax = minMax[1];
            cMin = minMax[0];
         }
         cMaxes[c] = cMax;
         if (cMax > mainMax) {
            mainMax = cMax;
         }
         if (cMin < commonMin) {
            commonMin = cMin;
         }
      }
      long[] result = new long[2 + nComponents];
      result[0] = mainMax;
      result[1] = commonMin == Long.MAX_VALUE ? 0 : commonMin;
      System.arraycopy(cMaxes, 0, result, 2, nComponents);
      return result;
   }

   @MustCallOnEDT
   private void applyRGBAutostretch() {
      if (stats_ == null) {
         return;
      }
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      double q = viewer_.getDisplaySettings().getAutoscaleIgnoredQuantile();
      boolean ignoreZeros = viewer_.getDisplaySettings().isAutoscaleIgnoringZeros();
      int selectedComponent = getSelectedComponent();

      if (selectedComponent == COMPONENT_WHITE) {
         long[] range = computeWhiteAutoscaleRange(q, ignoreZeros);
         whiteMainMax_ = range[0];
         final long sharedMin = range[1];
         whiteMainMin_ = sharedMin;
         int nComponents = stats_.getNumberOfComponents();
         if (whiteRatios_ == null) {
            // No white balance set: derive ratios from image content.
            whiteRatios_ = new double[nComponents];
            for (int c = 0; c < nComponents; c++) {
               whiteRatios_[c] = whiteMainMax_ > 0 ? (double) range[2 + c] / whiteMainMax_ : 1.0;
            }
         }
         do {
            oldDisplaySettings = viewer_.getDisplaySettings();
            ChannelDisplaySettings channelSettings =
                  oldDisplaySettings.getChannelSettings(channelIndex_);
            ChannelDisplaySettings.Builder builder = channelSettings.copyBuilder();
            for (int c = 0; c < nComponents; c++) {
               long scaledMax = Math.max(Math.round(whiteRatios_[c] * whiteMainMax_),
                     sharedMin + 1);
               builder.component(c,
                     channelSettings.getComponentSettings(c).copyBuilder()
                           .scalingRange(sharedMin, scaledMax).build());
            }
            newDisplaySettings = oldDisplaySettings
                  .copyBuilderWithChannelSettings(channelIndex_, builder.build())
                  .autostretch(false)
                  .build();
         } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
      } else {
         // Single component: only autoscale the selected component, leave others unchanged.
         ComponentStats selStats = stats_.getComponentStats(selectedComponent);
         FloatCoordinateMapper selMapper = getFloatMapper(selectedComponent);
         double newFloatMin = 0.0;
         double newFloatMax = 0.0;
         long newMin = 0L;
         long newMax = 0L;
         if (selMapper != null) {
            // Keep pixel values: rounding them here would pin the range to whole units.
            if (ignoreZeros) {
               newFloatMax = selStats.getFloatAutoscaleMaxForQuantileIgnoringZeros(q);
            } else {
               double[] fMinMax = new double[2];
               selStats.getFloatAutoscaleMinMaxForQuantile(q, fMinMax);
               newFloatMin = fMinMax[0];
               newFloatMax = fMinMax[1];
            }
         } else {
            if (ignoreZeros) {
               newMin = 0L;
               newMax = selStats.getAutoscaleMaxForQuantileIgnoringZeros(q);
            } else {
               long[] minMax = new long[2];
               selStats.getAutoscaleMinMaxForQuantile(q, minMax);
               newMin = minMax[0];
               newMax = minMax[1];
            }
         }
         do {
            oldDisplaySettings = viewer_.getDisplaySettings();
            ChannelDisplaySettings channelSettings =
                  oldDisplaySettings.getChannelSettings(channelIndex_);
            ComponentDisplaySettings.Builder cb =
                  channelSettings.getComponentSettings(selectedComponent).copyBuilder();
            if (selMapper != null) {
               cb.floatScalingRange(newFloatMin, newFloatMax);
            } else {
               cb.scalingRange(newMin, newMax);
            }
            newDisplaySettings = oldDisplaySettings
                  .copyBuilderWithComponentSettings(channelIndex_, selectedComponent,
                        cb.build())
                  .autostretch(false)
                  .build();
         } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
      }
   }


   private void handleFullscale() {
      final int nComponents = stats_.getNumberOfComponents();
      int sel = getSelectedComponent();
      Integer bitDepth = stats_.getComponentStats(0).getBitDepth();
      long fullMax;
      int floatSel = (sel == COMPONENT_WHITE) ? 0 : sel;
      if (floatMappers_ != null && floatSel < floatMappers_.length
            && floatMappers_[floatSel] != null) {
         fullMax = floatMappers_[floatSel].getBinCount();
      } else if (bitDepth == null) {
         fullMax = stats_.getComponentStats(0).getHistogramRangeMax();
      } else {
         fullMax = bitDepth >= 63 ? Long.MAX_VALUE : (1L << bitDepth) - 1L;
      }
      if (sel == COMPONENT_WHITE && whiteRatios_ == null) {
         captureWhiteRatios();
      }
      if (sel == COMPONENT_WHITE) {
         whiteMainMax_ = fullMax;
         whiteMainMin_ = 0;
      }
      final long finalFullMax = fullMax;
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         ChannelDisplaySettings.Builder builder = channelSettings.copyBuilder();
         if (sel == COMPONENT_WHITE) {
            for (int i = 0; i < nComponents; ++i) {
               long scaledMax = Math.max(Math.round(whiteRatios_[i] * finalFullMax), 1L);
               builder.component(i,
                     channelSettings.getComponentSettings(i).copyBuilder()
                           .scalingRange(0L, scaledMax).build());
            }

         } else if (getFloatMapper(sel) != null) {
            // Float: full scale is the whole accumulated axis, as pixel values.
            FloatCoordinateMapper mapper = getFloatMapper(sel);
            builder.component(sel,
                  channelSettings.getComponentSettings(sel).copyBuilder()
                        .floatScalingRange(mapper.getRangeMin(),
                              mapper.binIndexToPixelValue(mapper.getBinCount()))
                        .build());
         } else {
            builder.component(sel,
                  channelSettings.getComponentSettings(sel).copyBuilder()
                        .scalingRange(0L, finalFullMax).build());
         }
         newDisplaySettings = oldDisplaySettings
               .copyBuilderWithChannelSettings(channelIndex_, builder.build())
               .autostretch(false)
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
   }

   void handleAutoscale() {
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         double q = oldDisplaySettings.getAutoscaleIgnoredQuantile();
         boolean ignoreZeros = oldDisplaySettings.isAutoscaleIgnoringZeros();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         ChannelDisplaySettings.Builder builder = channelSettings.copyBuilder();
         int nComponents = stats_.getNumberOfComponents();
         int sel = getSelectedComponent();
         if (sel == COMPONENT_WHITE) {
            long[] range = computeWhiteAutoscaleRange(q, ignoreZeros);
            whiteMainMax_ = range[0];
            long commonMin = range[1];
            whiteMainMin_ = commonMin;
            if (whiteRatios_ == null) {
               // No white balance set: derive ratios from image content.
               whiteRatios_ = new double[nComponents];
               for (int i = 0; i < nComponents; ++i) {
                  whiteRatios_[i] = whiteMainMax_ > 0 ? (double) range[2 + i] / whiteMainMax_ : 1.0;
               }
            }
            for (int i = 0; i < nComponents; ++i) {
               long scaledMax = Math.max(Math.round(whiteRatios_[i] * whiteMainMax_),
                     commonMin + 1);
               builder.component(i,
                     channelSettings.getComponentSettings(i).copyBuilder()
                           .scalingRange(commonMin, scaledMax).build());
            }

         } else {
            ComponentStats stats = stats_.getComponentStats(sel);
            if (getFloatMapper(sel) != null) {
               // Stay in pixel values throughout: the long-valued autoscale would round
               // the quantiles first, and the range is stored as pixel values so that it
               // is not reinterpreted against the next image's binning.
               double min;
               double max;
               if (ignoreZeros) {
                  min = 0.0;
                  max = stats.getFloatAutoscaleMaxForQuantileIgnoringZeros(q);
               } else {
                  double[] minMax = new double[2];
                  stats.getFloatAutoscaleMinMaxForQuantile(q, minMax);
                  min = minMax[0];
                  max = minMax[1];
               }
               builder.component(sel,
                     channelSettings.getComponentSettings(sel).copyBuilder()
                           .floatScalingRange(min, max).build());
            } else {
               long min;
               long max;
               if (ignoreZeros) {
                  min = 0L;
                  max = stats.getAutoscaleMaxForQuantileIgnoringZeros(q);
               } else {
                  long[] minMax = new long[2];
                  stats.getAutoscaleMinMaxForQuantile(q, minMax);
                  min = minMax[0];
                  max = minMax[1];
               }
               builder.component(sel,
                     channelSettings.getComponentSettings(sel).copyBuilder()
                           .scalingRange(min, max).build());
            }
         }
         newDisplaySettings = oldDisplaySettings
               .copyBuilderWithChannelSettings(channelIndex_, builder.build())
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
   }

   @Override
   public void histogramScalingMinChanged(int component, long newMin) {
      rgbAutostretchEnabled_ = false;
      if (component == COMPONENT_WHITE) {
         applyShiftedMinToAllComponents(newMin);
         return;
      }
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         ComponentDisplaySettings componentSettings =
               channelSettings.getComponentSettings(component);
         FloatCoordinateMapper mapper = getFloatMapper(component);
         if (mapper != null) {
            // The handle position is a bin index; store the pixel value it denotes so the
            // range keeps its meaning when the next image has a different distribution.
            double newMinValue = mapper.binIndexToPixelValue(newMin);
            if (componentSettings.hasFloatScaling()
                  && componentSettings.getFloatScalingMinimum() == newMinValue) {
               return;
            }
            componentSettings = componentSettings
                  .copyBuilder()
                  .floatScalingMinimum(newMinValue)
                  .floatScalingMaximum(currentFloatMax(componentSettings, mapper))
                  .build();
         } else {
            if (componentSettings.getScalingMinimum() == newMin) {
               return;
            }
            componentSettings = componentSettings
                  .copyBuilder()
                  .scalingMinimum(newMin)
                  .build();
         }
         newDisplaySettings = oldDisplaySettings
               .copyBuilderWithComponentSettings(channelIndex_, component, componentSettings)
               .autostretch(false)
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
   }

   @Override
   public void histogramScalingMaxChanged(int component, long newMax) {
      rgbAutostretchEnabled_ = false;
      if (component == COMPONENT_WHITE) {
         applyProportionalMaxScaling(newMax);
         return;
      }
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         ComponentDisplaySettings componentSettings =
               channelSettings.getComponentSettings(component);
         FloatCoordinateMapper mapper = getFloatMapper(component);
         if (mapper != null) {
            double newMaxValue = mapper.binIndexToPixelValue(newMax);
            if (componentSettings.hasFloatScaling()
                  && componentSettings.getFloatScalingMaximum() == newMaxValue) {
               return;
            }
            componentSettings = componentSettings.copyBuilder()
                  .floatScalingMinimum(currentFloatMin(componentSettings, mapper))
                  .floatScalingMaximum(newMaxValue)
                  .build();
         } else {
            if (componentSettings.getScalingMaximum() == newMax) {
               return;
            }
            componentSettings = componentSettings.copyBuilder()
                  .scalingMaximum(newMax)
                  .build();
         }
         newDisplaySettings = oldDisplaySettings
               .copyBuilderWithComponentSettings(channelIndex_, component, componentSettings)
               .autostretch(false)
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
   }

   @MustCallOnEDT
   private void applyProportionalMaxScaling(long newMasterMax) {
      if (whiteRatios_ == null) {
         captureWhiteRatios();
      }
      // newMasterMax is the value the white handle was dragged to — this IS the
      // main max directly (updateScalingIndicators now shows whiteMainMax_ at
      // the handle, not Red's raw max, so no division is needed).
      whiteMainMax_ = newMasterMax;
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         ChannelDisplaySettings.Builder builder = channelSettings.copyBuilder();
         for (int c = 0; c < 3; c++) {
            long scaledMax = Math.round(whiteRatios_[c] * newMasterMax);
            long currentMin = channelSettings.getComponentSettings(c).getScalingMinimum();
            scaledMax = Math.max(scaledMax, currentMin + 1);
            builder.component(c,
                  channelSettings.getComponentSettings(c).copyBuilder()
                        .scalingMaximum(scaledMax)
                        .build());
         }
         newDisplaySettings = oldDisplaySettings
               .copyBuilderWithChannelSettings(channelIndex_, builder.build())
               .autostretch(false)
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
   }

   @MustCallOnEDT
   private void applyShiftedMinToAllComponents(long newMin) {
      // Shift all component mins by the same delta so that per-component offsets
      // (e.g. blue min set independently before entering white mode) are preserved,
      // exactly as whiteRatios_ preserves per-component max proportions.
      long delta = whiteMainMin_ >= 0 ? newMin - whiteMainMin_ : 0;
      whiteMainMin_ = Math.max(0, newMin);
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         ChannelDisplaySettings.Builder builder = channelSettings.copyBuilder();
         for (int c = 0; c < 3; c++) {
            long currentMax = channelSettings.getComponentSettings(c).getScalingMaximum();
            long shiftedMin = channelSettings.getComponentSettings(c).getScalingMinimum() + delta;
            long clampedMin = Math.max(0, Math.min(shiftedMin, currentMax - 1));
            builder.component(c,
                  channelSettings.getComponentSettings(c).copyBuilder()
                        .scalingMinimum(clampedMin)
                        .build());
         }
         newDisplaySettings = oldDisplaySettings
               .copyBuilderWithChannelSettings(channelIndex_, builder.build())
               .autostretch(false)
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
   }

   @Override
   public void histogramAxisRangeChanged(int component, double newRangeMin,
                                         double newRangeMax) {
      if (newRangeMax <= newRangeMin) {
         return;
      }
      // The user has chosen the axis explicitly: stop widening it to fit new images.
      floatRangePinned_ = true;
      floatRangeRestored_ = true;
      floatRangeMin_ = newRangeMin;
      floatRangeMax_ = newRangeMax;
      // Record it so the choice survives closing and reopening the dataset.
      storeFloatRangeInSettings(true);
      // Keep the scaling range inside the new axis, otherwise the handles would sit off
      // the end and the image would be scaled to something the user cannot see or reach.
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         ComponentDisplaySettings componentSettings =
               channelSettings.getComponentSettings(component);
         if (!componentSettings.hasFloatScaling()) {
            break;
         }
         double clampedMin = Math.max(newRangeMin,
               Math.min(componentSettings.getFloatScalingMinimum(), newRangeMax));
         double clampedMax = Math.max(clampedMin,
               Math.min(componentSettings.getFloatScalingMaximum(), newRangeMax));
         if (clampedMax <= clampedMin) {
            clampedMin = newRangeMin;
            clampedMax = newRangeMax;
         }
         if (clampedMin == componentSettings.getFloatScalingMinimum()
               && clampedMax == componentSettings.getFloatScalingMaximum()) {
            break;
         }
         newDisplaySettings = oldDisplaySettings
               .copyBuilderWithComponentSettings(channelIndex_, component,
                     componentSettings.copyBuilder()
                           .floatScalingRange(clampedMin, clampedMax).build())
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
      statsOrRangeChanged();
   }

   @Override
   public void histogramFloatScalingChanged(int component, double newMin, double newMax) {
      if (newMax <= newMin || Double.isNaN(newMin) || Double.isNaN(newMax)) {
         return;
      }
      rgbAutostretchEnabled_ = false;
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ComponentDisplaySettings componentSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_)
                     .getComponentSettings(component);
         if (componentSettings.hasFloatScaling()
               && componentSettings.getFloatScalingMinimum() == newMin
               && componentSettings.getFloatScalingMaximum() == newMax) {
            return;
         }
         newDisplaySettings = oldDisplaySettings
               .copyBuilderWithComponentSettings(channelIndex_, component,
                     componentSettings.copyBuilder()
                           .floatScalingRange(newMin, newMax).build())
               .autostretch(false)
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
   }

   @Override
   public void histogramGammaChanged(double newGamma) {
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         ChannelDisplaySettings.Builder channelBuilder =
               channelSettings.copyBuilder();
         boolean changed = false;
         for (int c = 0; c < channelSettings.getNumberOfComponents(); ++c) {
            ComponentDisplaySettings componentSettings =
                  channelSettings.getComponentSettings(c);
            if (componentSettings.getScalingGamma() == newGamma) {
               continue;
            }
            changed = true;
            channelBuilder.component(c,
                  componentSettings
                        .copyBuilder()
                        .scalingGamma(newGamma)
                        .build());
         }
         if (!changed) {
            return;
         }
         newDisplaySettings = oldDisplaySettings
               .copyBuilder()
               .channel(channelIndex_, channelBuilder.build())
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
   }

   void newDisplaySettings(DisplaySettings settings) {
      // For RGB images, intercept autostretch so we can honour the selected
      // component and white-mode ratios. Keep DisplaySettings.isAutostretchEnabled()
      // false and drive updates ourselves from setStats().
      int numComponents = stats_ == null ? 1 : stats_.getNumberOfComponents();
      if (numComponents > 1 && !suppressAutostretchDetection_) {
         if (settings.isAutostretchEnabled() && !rgbAutostretchEnabled_) {
            // User just checked Autostretch — take ownership.
            rgbAutostretchEnabled_ = true;
            suppressAutostretchDetection_ = true;
            try {
               turnOffAutostretchInSettings();
            } finally {
               suppressAutostretchDetection_ = false;
            }
            applyRGBAutostretch();
            return;
         }
         // Note: disabling rgbAutostretchEnabled_ when the user unchecks the box
         // is handled externally via setRgbAutostretchEnabled(false), called from
         // IntensityInspectorPanelController.handleAutostretch(). We must NOT do it
         // here based on isAutostretchEnabled()==false, because we always keep that
         // flag false in DisplaySettings (to prevent the display engine from
         // interfering), so this condition would fire on every settings update and
         // immediately reset rgbAutostretchEnabled_.
      }

      ChannelDisplaySettings channelSettings =
            settings.getChannelSettings(channelIndex_);
      channelVisibleButton_.setSelected(channelSettings.isVisible());
      channelColorSwatch_.setColor(channelSettings.getColor());
      // The display-settings channel name can be empty (e.g. settings built without a
      // name, as when reopening a dataset and deriving settings from heuristics). Fall
      // back to the authoritative channel name from SummaryMetadata so the label is not
      // blanked out -- this is the same source used when the panel was first created.
      // This path is shared by all data viewers; the fallback only fires when the name is
      // empty, so viewers that already set a non-empty channel name are unaffected.
      String dsName = channelSettings.getName();
      if (dsName == null || dsName.isEmpty()) {
         dsName = viewer_.getDataProvider().getSummaryMetadata()
               .getSafeChannelName(channelIndex_);
      }
      channelNameLabel_.setText(dsName);

      histoRangeComboBoxModel_.setBits(channelSettings);

      if (numComponents <= 1) {
         histogram_.setGamma(channelSettings
               .getComponentSettings(0)
               .getScalingGamma());
      }

      boolean whiteMode = getSelectedComponent() == COMPONENT_WHITE;
      for (int c = 0; c < numComponents; c++) {
         Color color = numComponents <= 1 ? channelSettings.getColor() : RGB_COLORS[c];
         Color highlight = numComponents <= 1 ? Color.YELLOW : color;
         histogram_.setComponentColor(c, color, highlight);

         if (stats_ != null) {
            ComponentStats componentStats = stats_.getComponentStats(c);
            updateScalingIndicators(settings, componentStats, c);
         }
      }
      if (whiteMode && stats_ != null) {
         updateWhiteScalingIndicator(settings, stats_.getComponentStats(0));
      }
   }

   /**
    * Handles event indicating the Pixel info about the pizel the mouse
    * is pointing at has changed.
    *
    * @param e Event with information about the pixel and its value(s).
    */
   @Subscribe
   public void onEvent(DataViewerMousePixelInfoChangedEvent e) {
      if (pickingWhiteBalancePoint_) {
         // In pick mode, track the current pixel values but wait for a click to apply.
         if (e.isInfoAvailable()) {
            long[] values = getPixelValuesForChannel(e);
            if (values != null && values.length >= 3) {
               lastPickedValues_ = values;
            }
         }
         return;
      }

      histogram_.clearComponentHighlights();
      if (!e.isInfoAvailable()) {
         return;
      }

      // Read the untruncated values for float images: a pixel of 0.37 arrives as 0
      // through the long-valued accessor, so the highlight lands in the wrong bin.
      double[] floatValues = getPixelValuesDoubleForChannel(e);
      long[] values = getPixelValuesForChannel(e);
      if (values != null) {
         for (int component = 0; component < values.length; ++component) {
            long highlightValue = values[component];
            if (floatMappers_ != null && component < floatMappers_.length
                  && floatMappers_[component] != null) {
               double pixelValue = floatValues != null && component < floatValues.length
                     ? floatValues[component] : (double) highlightValue;
               highlightValue = floatMappers_[component].pixelValueToBinIndex(pixelValue);
            }
            histogram_.setComponentHighlight(component, highlightValue);
         }
      }
   }

   @Subscribe
   public void onEvent(DisplayMouseEvent e) {
      if (!pickingWhiteBalancePoint_) {
         return;
      }
      if (e.getEvent().getID() == java.awt.event.MouseEvent.MOUSE_PRESSED
               && javax.swing.SwingUtilities.isLeftMouseButton(e.getEvent())) {
         pickingWhiteBalancePoint_ = false;
         final long[] values = lastPickedValues_;
         lastPickedValues_ = null;
         if (values != null) {
            javax.swing.SwingUtilities.invokeLater(() -> applyPickedPointWhiteBalance(values));
         }
      }
   }

   private long[] getPixelValuesForChannel(DataViewerMousePixelInfoChangedEvent e) {
      Coords coords = getCoordsForChannel(e);
      return coords == null ? null : e.getComponentValuesForCoords(coords);
   }

   /**
    * Pixel values for this channel without float truncation.
    *
    * @param e the event to read
    * @return the values, or null when unavailable
    */
   private double[] getPixelValuesDoubleForChannel(
         DataViewerMousePixelInfoChangedEvent e) {
      Coords coords = getCoordsForChannel(e);
      return coords == null ? null : e.getComponentValuesDoubleForCoords(coords);
   }

   private Coords getCoordsForChannel(DataViewerMousePixelInfoChangedEvent e) {
      // Channel-less case
      if (channelIndex_ == 0 && e.getNumberOfCoords() == 1) {
         Coords coords = e.getAllCoords().get(0);
         if (!coords.hasAxis(Coords.CHANNEL)) {
            return coords;
         }
      }
      for (Coords coords : e.getAllCoords()) {
         if (coords.getChannel() == channelIndex_) {
            return coords;
         }
      }
      return null;
   }

   @MustCallOnEDT
   private void showWhiteBalancePopup() {
      javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
      menu.add(new javax.swing.AbstractAction("From picked point") {
         @Override
         public void actionPerformed(ActionEvent e) {
            startPickedPointWhiteBalance();
         }
      });
      menu.add(new javax.swing.AbstractAction("From average intensity") {
         @Override
         public void actionPerformed(ActionEvent e) {
            applyAverageIntensityWhiteBalance();
         }
      });
      menu.add(new javax.swing.AbstractAction("From color temperature...") {
         @Override
         public void actionPerformed(ActionEvent e) {
            showColorTemperatureDialog();
         }
      });
      menu.show(whiteBalanceButton_, 0, whiteBalanceButton_.getHeight());
   }

   @MustCallOnEDT
   private void showColorTemperatureDialog() {
      // Snapshot only the channel settings we will modify, so Cancel can revert
      // just this channel without clobbering unrelated concurrent changes.
      final ChannelDisplaySettings channelOnOpen =
            viewer_.getDisplaySettings().getChannelSettings(channelIndex_);
      final double[] ratiosOnOpen = whiteRatios_ == null ? null : whiteRatios_.clone();
      final long mainMaxOnOpen = whiteMainMax_;
      final long mainMinOnOpen = whiteMainMin_;

      java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(whiteBalanceButton_);
      javax.swing.JDialog dialog = new javax.swing.JDialog(owner, "Color Temperature",
            java.awt.Dialog.ModalityType.MODELESS);
      dialog.setLayout(new MigLayout(new LC().insets("8").fillX()));

      // Slider: 2000K–10000K
      final int MIN_K = 2000;
      final int MAX_K = 10000;
      final int DEFAULT_K = whiteRatios_ != null
            ? estimateColorTemperature(whiteRatios_, MIN_K, MAX_K) : 6500;
      final javax.swing.JSlider slider = new javax.swing.JSlider(MIN_K, MAX_K, DEFAULT_K);
      slider.setMajorTickSpacing(2000);
      slider.setMinorTickSpacing(500);
      slider.setPaintTicks(true);

      // Text field showing K value
      final javax.swing.JTextField kField = new javax.swing.JTextField(
            Integer.toString(DEFAULT_K), 6);

      // Color swatch showing the illuminant color
      final javax.swing.JPanel colorSwatch = new javax.swing.JPanel() {
         @Override
         protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
         }
      };
      colorSwatch.setPreferredSize(new java.awt.Dimension(48, 24));
      colorSwatch.setBorder(BorderFactory.createLineBorder(java.awt.Color.GRAY));
      colorSwatch.setOpaque(true);

      // Lay out: label + field + swatch on first row, slider spanning full width
      dialog.add(new javax.swing.JLabel("Temperature (K):"));
      dialog.add(kField, new CC().width("60!").gapAfter("rel"));
      dialog.add(colorSwatch, new CC().wrap());
      dialog.add(slider, new CC().spanX().growX().wrap("rel"));

      // OK / Cancel buttons
      javax.swing.JButton okButton = new javax.swing.JButton("OK");
      javax.swing.JButton cancelButton = new javax.swing.JButton("Cancel");
      dialog.add(okButton, new CC().spanX().split(2).tag("ok"));
      dialog.add(cancelButton, new CC().tag("cancel"));

      // Shared update logic: apply balance and update swatch
      final Runnable applyTemperature = new Runnable() {
         @Override
         public void run() {
            int k = slider.getValue();
            int[] rgb = colorTemperatureToRgb(k);
            colorSwatch.setBackground(new java.awt.Color(rgb[0], rgb[1], rgb[2]));
            colorSwatch.repaint();
            applyColorTemperatureWhiteBalance(k);
         }
      };

      slider.addChangeListener(e -> {
         kField.setText(Integer.toString(slider.getValue()));
         applyTemperature.run();
      });

      kField.addActionListener(e -> {
         try {
            int k = Integer.parseInt(kField.getText().trim());
            k = Math.max(MIN_K, Math.min(MAX_K, k));
            slider.setValue(k);  // triggers changeListener → applyTemperature
         } catch (NumberFormatException ex) {
            kField.setText(Integer.toString(slider.getValue()));
         }
      });

      okButton.addActionListener(e -> dialog.dispose());

      cancelButton.addActionListener(e -> {
         // Revert only this channel's scaling — leave all other display changes intact.
         whiteRatios_ = ratiosOnOpen;
         whiteMainMax_ = mainMaxOnOpen;
         whiteMainMin_ = mainMinOnOpen;
         DisplaySettings current;
         DisplaySettings reverted;
         do {
            current = viewer_.getDisplaySettings();
            reverted = current
                  .copyBuilderWithChannelSettings(channelIndex_, channelOnOpen)
                  .build();
         } while (!viewer_.compareAndSetDisplaySettings(current, reverted));
         statsOrRangeChanged();
         dialog.dispose();
      });

      // Seed the swatch without applying a balance change
      int[] initRgb = colorTemperatureToRgb(DEFAULT_K);
      colorSwatch.setBackground(new java.awt.Color(initRgb[0], initRgb[1], initRgb[2]));

      dialog.pack();
      dialog.setLocationRelativeTo(whiteBalanceButton_);
      dialog.setVisible(true);
   }

   /**
    * Estimates the color temperature in Kelvin that best matches the given white ratios,
    * by finding the K in [minK, maxK] whose correction ratios minimize squared error.
    */
   private static int estimateColorTemperature(double[] ratios, int minK, int maxK) {
      int bestK = 6500;
      double bestError = Double.MAX_VALUE;
      for (int k = minK; k <= maxK; k++) {
         int[] illuminant = colorTemperatureToRgb(k);
         double maxIlluminant = Math.max(illuminant[0], Math.max(illuminant[1], illuminant[2]));
         if (maxIlluminant <= 0) {
            continue;
         }
         double[] correctionRatios = new double[] {
               (double) illuminant[0] / maxIlluminant,
               (double) illuminant[1] / maxIlluminant,
               (double) illuminant[2] / maxIlluminant
         };
         double error = 0;
         for (int c = 0; c < 3; c++) {
            double diff = ratios[c] - correctionRatios[c];
            error += diff * diff;
         }
         if (error < bestError) {
            bestError = error;
            bestK = k;
         }
      }
      return bestK;
   }

   /**
    * Converts a color temperature in Kelvin to an approximate RGB illuminant color.
    * Output values are in the range 0-255.
    *
    * <p>Algorithm by Tanner Helland, based on blackbody data by Mitchell Charity.
    * Licensed under CC BY-SA 4.0.
    * Source: https://tannerhelland.com/2012/09/18/convert-temperature-rgb-algorithm-code.html
    *
    * <p>Suitable for display purposes; not intended for scientific use.
    */
   private static int[] colorTemperatureToRgb(int kelvin) {
      double t = Math.max(1000, Math.min(40000, kelvin)) / 100.0;

      int r;
      if (t <= 66) {
         r = 255;
      } else {
         r = (int) (329.698727446 * Math.pow(t - 60, -0.1332047592));
         r = Math.max(0, Math.min(255, r));
      }

      int g;
      if (t <= 66) {
         g = (int) (99.4708025861 * Math.log(t) - 161.1195681661);
      } else {
         g = (int) (288.1221695283 * Math.pow(t - 60, -0.0755148492));
      }
      g = Math.max(0, Math.min(255, g));

      int b;
      if (t >= 66) {
         b = 255;
      } else if (t <= 19) {
         b = 0;
      } else {
         b = (int) (138.5177312231 * Math.log(t - 10) - 305.0447927307);
         b = Math.max(0, Math.min(255, b));
      }

      return new int[] {r, g, b};
   }

   @MustCallOnEDT
   private void applyColorTemperatureWhiteBalance(int kelvin) {
      int[] illuminant = colorTemperatureToRgb(kelvin);
      // Ratios proportional to illuminant: dominant channel stays at 1.0,
      // weaker channels get smaller ratios (smaller scaledMax → appear brighter).
      double maxIlluminant = Math.max(illuminant[0], Math.max(illuminant[1], illuminant[2]));
      if (maxIlluminant <= 0) {
         return;
      }
      whiteRatios_ = new double[] {
            (double) illuminant[0] / maxIlluminant,
            (double) illuminant[1] / maxIlluminant,
            (double) illuminant[2] / maxIlluminant
      };
      rgbAutostretchEnabled_ = false;
      applyCurrentWhiteRatios();
   }

   @MustCallOnEDT
   private void startPickedPointWhiteBalance() {
      lastPickedValues_ = null;
      pickingWhiteBalancePoint_ = true;
   }

   @MustCallOnEDT
   private void applyPickedPointWhiteBalance(long[] pixelValues) {
      long maxVal = 0;
      for (long v : pixelValues) {
         if (v > maxVal) {
            maxVal = v;
         }
      }
      if (maxVal == 0) {
         return; // can't balance a black pixel
      }
      whiteRatios_ = new double[] {
            (double) pixelValues[0] / maxVal,
            (double) pixelValues[1] / maxVal,
            (double) pixelValues[2] / maxVal
      };
      rgbAutostretchEnabled_ = false;
      applyCurrentWhiteRatios();
   }

   @MustCallOnEDT
   private void applyAverageIntensityWhiteBalance() {
      if (stats_ == null || stats_.getNumberOfComponents() < 3) {
         return;
      }
      long[] means = new long[3];
      for (int c = 0; c < 3; c++) {
         means[c] = stats_.getComponentStats(c).getMeanIntensityExcludingZeros();
      }
      long maxMean = Math.max(means[0], Math.max(means[1], means[2]));
      if (maxMean == 0) {
         return;
      }
      whiteRatios_ = new double[] {
            (double) means[0] / maxMean,
            (double) means[1] / maxMean,
            (double) means[2] / maxMean
      };
      rgbAutostretchEnabled_ = false;
      applyCurrentWhiteRatios();
   }

   @MustCallOnEDT
   private void applyCurrentWhiteRatios() {
      if (whiteMainMax_ <= 0) {
         captureWhiteRatios();
      }
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         ChannelDisplaySettings.Builder builder = channelSettings.copyBuilder();
         for (int c = 0; c < 3; c++) {
            long scaledMax = Math.max(Math.round(whiteRatios_[c] * whiteMainMax_), 1L);
            long currentMin = channelSettings.getComponentSettings(c).getScalingMinimum();
            scaledMax = Math.max(scaledMax, currentMin + 1);
            builder.component(c, channelSettings.getComponentSettings(c)
                  .copyBuilder().scalingRange(currentMin, scaledMax).build());
         }
         newDisplaySettings = oldDisplaySettings.copyBuilder()
               .channel(channelIndex_, builder.build())
               .autostretch(false)
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
      statsOrRangeChanged();
   }
}