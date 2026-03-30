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
import org.micromanager.data.Image;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.event.DataViewerMousePixelInfoChangedEvent;
import org.micromanager.display.internal.imagestats.ImageStats;
import org.micromanager.display.internal.imagestats.IntegerComponentStats;
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
   private double[] whiteRatios_ = null; // non-null when white mode is active
   private long whiteMainMax_ = 0; // main max shown by white handle; = max(R,G,B)
   // For RGB images we manage autostretch here (not in DisplayUIController) so we
   // can honour the selected component and white-mode ratios.
   // DisplaySettings.isAutostretchEnabled() is kept false to prevent the display
   // engine from interfering; this flag tracks the user's actual intent.
   private boolean rgbAutostretchEnabled_ = false;
   private boolean suppressAutostretchDetection_ = false;

   private final JToggleButton[] componentButtons_ = new JToggleButton[4];

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

         maxMinMaxWidth_ = valueFontMetrics_.stringWidth("99999") + 2;
         maxAvgStdWidth_ = valueFontMetrics_.stringWidth("9.99e+99") + 2;

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
         @Override public int getIconWidth()  { return 14; }
         @Override public int getIconHeight() { return 14; }
      };
   }

   private static final Icon WHITE_ICON_ACTIVE   = makeFilledSquareIcon(Color.WHITE, Color.DARK_GRAY, true);
   private static final Icon WHITE_ICON_INACTIVE = makeFilledSquareIcon(new Color(180, 180, 180), Color.GRAY, false);


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
      IntegerComponentStats selectedStats = stats_.getComponentStats(statsComponent);
      long min = selectedStats.getMinIntensity();
      intensityStatsPanel_.setMin(min >= 0 ? Long.toString(min) : null);
      long max = selectedStats.getMaxIntensity();
      intensityStatsPanel_.setMax(max >= 0 ? Long.toString(max) : null);
      long mean = selectedStats.getMeanIntensity();
      intensityStatsPanel_.setMean(mean >= 0 ? Long.toString(mean) : null);
      double stdev = selectedStats.getStandardDeviation();
      intensityStatsPanel_.setStdev(Double.isNaN(stdev) ? null :
            String.format("%1.2e", stdev));

      cameraBits_ = stats_.getComponentStats(0).getBitDepth();

      final DisplaySettings displaySettings = viewer_.getDisplaySettings();
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

      boolean whiteMode = getSelectedComponent() == COMPONENT_WHITE;
      for (int c = 0; c < numComponents; c++) {
         IntegerComponentStats cStats = stats_.getComponentStats(c);
         long[] data = cStats.getInRangeHistogram();
         if (data != null) {
            int lengthToUse = Math.min(data.length, (1 << rangeBits));
            int rangeMax = lengthToUse - 1;
            histogram_.setComponentGraph(c, data, lengthToUse, rangeMax);
            histogram_.setROIIndicator(cStats.isROIStats());
         }
         updateScalingIndicators(displaySettings, cStats, c);
      }
      if (whiteMode) {
         updateWhiteScalingIndicator(displaySettings, stats_.getComponentStats(0));
      }
   }

   @MustCallOnEDT
   // Updates the histogram's scaling indicator (dotted lines + handle) for a single
   // R/G/B component (indices 0–2). Always reflects the component's own
   // ComponentDisplaySettings — independent of white mode.
   private void updateScalingIndicators(DisplaySettings settings,
                                        IntegerComponentStats componentStats, int component) {
      long min;
      long max;
      if (settings.isAutostretchEnabled()) {
         double q = settings.getAutoscaleIgnoredQuantile();
         long[] minMax = new long[2];
         componentStats.getAutoscaleMinMaxForQuantile(q, minMax);
         min = minMax[0];
         max = minMax[1];
      } else {
         ComponentDisplaySettings componentSettings =
               settings.getChannelSettings(channelIndex_)
                     .getComponentSettings(component);
         max = Math.min(componentStats.getHistogramRangeMax(),
               componentSettings.getScalingMaximum());
         min = Math.max(0, Math.min(max - 1,
               componentSettings.getScalingMinimum()));
      }
      histogram_.setComponentScaling(component, min, max);
   }

   // Updates the white handle (HistogramView slot COMPONENT_WHITE) to show
   // whiteMainMax_ as the draggable position. Uses component 0's rangeMax and
   // min for positioning since white mode operates in the same intensity space.
   private void updateWhiteScalingIndicator(DisplaySettings settings,
                                            IntegerComponentStats comp0Stats) {
      long rangeMax = comp0Stats.getHistogramRangeMax();
      long max = whiteMainMax_ > 0
            ? Math.min(whiteMainMax_, rangeMax)
            : Math.min(settings.getChannelSettings(channelIndex_)
                  .getComponentSettings(0).getScalingMaximum(), rangeMax);
      ComponentDisplaySettings comp0Settings =
            settings.getChannelSettings(channelIndex_).getComponentSettings(0);
      long min = Math.max(0, comp0Settings.getScalingMinimum());
      // Feed component 3 the same graph data as component 0 so that rangeMax_ is
      // set in HistogramView (required for handle position calculation).
      long[] graph = comp0Stats.getInRangeHistogram();
      if (graph != null && rangeMax > 0) {
         histogram_.setComponentGraph(COMPONENT_WHITE, graph, graph.length, rangeMax);
         histogram_.setComponentScaling(COMPONENT_WHITE, min, max);
      }
   }

   @MustCallOnEDT
   private void updateHistoRangeButtonStates() {
      int index = histoRangeComboBox_.getSelectedIndex();
      histoRangeDownButton_.setEnabled(index > 0);
      histoRangeUpButton_.setEnabled(index < histoRangeComboBoxModel_.getSize() - 2);
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

      if (isRGB && !wasRGB) {
         // Auto-select White when entering RGB mode for the first time.
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
         captureWhiteRatios();
         // Use HistogramView slot 3 for the white handle so that Red (slot 0)
         // keeps its own independent dotted line and handle.
         histogram_.setComponentColor(0, Color.RED, Color.RED);
         histogram_.setComponentColor(COMPONENT_WHITE, Color.WHITE, Color.WHITE);
         histogram_.setSelectedComponent(COMPONENT_WHITE);
      } else {
         whiteRatios_ = null;
         whiteMainMax_ = 0;
         // Deactivate the white slot so its dotted line disappears.
         histogram_.clearComponentGraph(COMPONENT_WHITE);
         histogram_.setComponentColor(0, Color.RED, Color.RED);
         histogram_.setSelectedComponent(component);
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
      // Use the effective (displayed) max for each component: clamped to histogram range.
      // This matches what updateScalingIndicators uses and avoids Long.MAX_VALUE defaults
      // polluting the ratio when a channel hasn't been manually adjusted yet.
      DisplaySettings settings = viewer_.getDisplaySettings();
      ChannelDisplaySettings ch = settings.getChannelSettings(channelIndex_);
      long[] effectiveMax = new long[3];
      for (int c = 0; c < 3; c++) {
         long settingsMax = ch.getComponentSettings(c).getScalingMaximum();
         if (stats_ != null) {
            long rangeMax = stats_.getComponentStats(c).getHistogramRangeMax();
            effectiveMax[c] = Math.min(settingsMax, rangeMax);
         } else {
            effectiveMax[c] = settingsMax == Long.MAX_VALUE ? 255 : settingsMax;
         }
      }
      long mainMax = Math.max(effectiveMax[0], Math.max(effectiveMax[1], effectiveMax[2]));
      whiteMainMax_ = mainMax;
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

   // Returns {mainMax, commonMin} across all components at the given quantile.
   // mainMax = brightest autoscale max, so the white handle lands at the image's
   // true peak value rather than an inflated implied value.
   private long[] computeWhiteAutoscaleRange(double q) {
      long mainMax = 0;
      long commonMin = Long.MAX_VALUE;
      int nComponents = stats_.getNumberOfComponents();
      for (int c = 0; c < nComponents; c++) {
         long[] minMax = new long[2];
         stats_.getComponentStats(c).getAutoscaleMinMaxForQuantile(q, minMax);
         if (minMax[1] > mainMax) { mainMax = minMax[1]; }
         if (minMax[0] < commonMin) { commonMin = minMax[0]; }
      }
      return new long[] { mainMax, commonMin == Long.MAX_VALUE ? 0 : commonMin };
   }

   @MustCallOnEDT
   private void applyRGBAutostretch() {
      if (stats_ == null) {
         return;
      }
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      double q = viewer_.getDisplaySettings().getAutoscaleIgnoredQuantile();
      int selectedComponent = getSelectedComponent();

      if (selectedComponent == COMPONENT_WHITE) {
         if (whiteRatios_ == null) {
            captureWhiteRatios();
         }
         long[] range = computeWhiteAutoscaleRange(q);
         whiteMainMax_ = range[0];
         final long sharedMin = range[1];
         int nComponents = stats_.getNumberOfComponents();
         do {
            oldDisplaySettings = viewer_.getDisplaySettings();
            ChannelDisplaySettings channelSettings =
                  oldDisplaySettings.getChannelSettings(channelIndex_);
            ChannelDisplaySettings.Builder builder = channelSettings.copyBuilder();
            for (int c = 0; c < nComponents; c++) {
               long scaledMax = Math.max(Math.round(whiteRatios_[c] * whiteMainMax_), sharedMin + 1);
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
         long[] minMax = new long[2];
         stats_.getComponentStats(selectedComponent).getAutoscaleMinMaxForQuantile(q, minMax);
         final long newMin = minMax[0];
         final long newMax = minMax[1];
         do {
            oldDisplaySettings = viewer_.getDisplaySettings();
            ChannelDisplaySettings channelSettings =
                  oldDisplaySettings.getChannelSettings(channelIndex_);
            newDisplaySettings = oldDisplaySettings
                  .copyBuilderWithComponentSettings(channelIndex_, selectedComponent,
                        channelSettings.getComponentSettings(selectedComponent).copyBuilder()
                              .scalingRange(newMin, newMax).build())
                  .autostretch(false)
                  .build();
         } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
      }
   }


   private void handleFullscale() {
      int nComponents = stats_.getNumberOfComponents();
      long fullMax = 1 << stats_.getComponentStats(0).getBitDepth();
      if (fullMax < 0) {
         fullMax = Long.MAX_VALUE;
      }
      int sel = getSelectedComponent();
      if (sel == COMPONENT_WHITE && whiteRatios_ == null) {
         captureWhiteRatios();
      }
      if (sel == COMPONENT_WHITE) {
         whiteMainMax_ = fullMax;
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
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         ChannelDisplaySettings.Builder builder = channelSettings.copyBuilder();
         int nComponents = stats_.getNumberOfComponents();
         if (getSelectedComponent() == COMPONENT_WHITE) {
            if (whiteRatios_ == null) {
               captureWhiteRatios();
            }
            long[] range = computeWhiteAutoscaleRange(q);
            whiteMainMax_ = range[0];
            long commonMin = range[1];
            for (int i = 0; i < nComponents; ++i) {
               long scaledMax = Math.max(Math.round(whiteRatios_[i] * whiteMainMax_), commonMin + 1);
               builder.component(i,
                     channelSettings.getComponentSettings(i).copyBuilder()
                           .scalingRange(commonMin, scaledMax).build());
            }
         } else {
            int sel = getSelectedComponent();
            IntegerComponentStats stats = stats_.getComponentStats(sel);
            long[] minMax = new long[2];
            stats.getAutoscaleMinMaxForQuantile(q, minMax);
            builder.component(sel,
                  channelSettings.getComponentSettings(sel).copyBuilder()
                        .scalingRange(minMax[0], minMax[1]).build());
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
         applyAbsoluteMinToAllComponents(newMin);
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
         if (componentSettings.getScalingMinimum() == newMin) {
            return;
         }
         componentSettings = componentSettings
               .copyBuilder()
               .scalingMinimum(newMin)
               .build();
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
         if (componentSettings.getScalingMaximum() == newMax) {
            return;
         }
         componentSettings = componentSettings.copyBuilder()
               .scalingMaximum(newMax)
               .build();
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
   private void applyAbsoluteMinToAllComponents(long newMin) {
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         ChannelDisplaySettings.Builder builder = channelSettings.copyBuilder();
         for (int c = 0; c < 3; c++) {
            long currentMax = channelSettings.getComponentSettings(c).getScalingMaximum();
            long clampedMin = Math.max(0, Math.min(newMin, currentMax - 1));
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
      channelNameLabel_.setText(channelSettings.getName());

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
            IntegerComponentStats componentStats = stats_.getComponentStats(c);
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
      histogram_.clearComponentHighlights();
      if (!e.isInfoAvailable()) {
         return;
      }

      // Channel-less case
      if (channelIndex_ == 0 && e.getNumberOfCoords() == 1) {
         Coords coords = e.getAllCoords().get(0);
         if (!coords.hasAxis(Coords.CHANNEL)) {
            long[] values = e.getComponentValuesForCoords(coords);
            for (int component = 0; component < values.length; ++component) {
               histogram_.setComponentHighlight(component, values[component]);
            }
            return;
         }
      }

      for (Coords coords : e.getAllCoords()) {
         if (coords.getChannel() == channelIndex_) {
            long[] values = e.getComponentValuesForCoords(coords);
            for (int component = 0; component < values.length; ++component) {
               histogram_.setComponentHighlight(component, values[component]);
            }
            return;
         }
      }
   }
}