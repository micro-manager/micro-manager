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
   private final JToggleButton[] componentButtons_ = new JToggleButton[3];

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

      channelPanel_.setLayout(new MigLayout(
            new LC().fill().insets("0").gridGap("0", "0")));
      channelPanel_.setOpaque(true);
      channelPanel_.add(channelVisibleButton_, new CC().gapBefore("rel").split(2));
      channelPanel_.add(channelColorSwatch_, new CC().gapBefore("rel").width("32").wrap());
      channelPanel_.add(channelNameLabel_, new CC().gapBefore("rel").pushX().wrap("rel:rel:push"));
      channelPanel_.add(componentButtons_[0], new CC().gapBefore("push").gapAfter("0").split(3));
      channelPanel_.add(componentButtons_[1], new CC().gapAfter("0"));
      channelPanel_.add(componentButtons_[2], new CC().gapAfter("push").wrap("rel"));
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
      IntegerComponentStats selectedStats = stats_.getComponentStats(selectedComponent);
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
   }

   @MustCallOnEDT
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

      // Reapply display settings with correct component handling
      newDisplaySettings(viewer_.getDisplaySettings());
   }

   @MustCallOnEDT
   private void handleComponentSelection(int component) {
      for (int i = 0; i < 3; i++) {
         componentButtons_[i].setSelected(i == component);
      }
      histogram_.setSelectedComponent(component);
   }

   @MustCallOnEDT
   private int getSelectedComponent() {
      for (int i = 0; i < 3; i++) {
         if (componentButtons_[i].isSelected()) {
            return i;
         }
      }
      return 0; // Shouldn't reach
   }

   private void handleFullscale() {
      DisplaySettings oldDisplaySettings;
      DisplaySettings newDisplaySettings;
      do {
         oldDisplaySettings = viewer_.getDisplaySettings();
         ChannelDisplaySettings channelSettings =
               oldDisplaySettings.getChannelSettings(channelIndex_);
         ChannelDisplaySettings.Builder builder = channelSettings.copyBuilder();
         int nComponents = stats_.getNumberOfComponents();
         for (int i = 0; i < nComponents; ++i) {
            long max = 1 << stats_.getComponentStats(0).getBitDepth();
            builder.component(i,
                  channelSettings.getComponentSettings(i).copyBuilder()
                        .scalingRange(0L, max >= 0 ? max : Long.MAX_VALUE)
                        .build());
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
         for (int i = 0; i < nComponents; ++i) {
            IntegerComponentStats stats = stats_.getComponentStats(i);
            long[] minMax = new long[2];
            stats.getAutoscaleMinMaxForQuantile(q, minMax);
            long min = minMax[0];
            long max = minMax[1];
            builder.component(i,
                  channelSettings.getComponentSettings(i).copyBuilder()
                        .scalingRange(min, max).build());
         }
         newDisplaySettings = oldDisplaySettings
               .copyBuilderWithChannelSettings(channelIndex_, builder.build())
               .build();
      } while (!viewer_.compareAndSetDisplaySettings(oldDisplaySettings, newDisplaySettings));
   }

   @Override
   public void histogramScalingMinChanged(int component, long newMin) {
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
      ChannelDisplaySettings channelSettings =
            settings.getChannelSettings(channelIndex_);
      channelVisibleButton_.setSelected(channelSettings.isVisible());
      channelColorSwatch_.setColor(channelSettings.getColor());
      channelNameLabel_.setText(channelSettings.getName());

      histoRangeComboBoxModel_.setBits(channelSettings);

      int numComponents = stats_ == null ? 1 : stats_.getNumberOfComponents();
      if (numComponents <= 1) {
         histogram_.setGamma(channelSettings
                 .getComponentSettings(0)
                 .getScalingGamma());
      }

      for (int c = 0; c < numComponents; c++) {
         Color color = numComponents <= 1 ? channelSettings.getColor() : RGB_COLORS[c];
         Color highlight = numComponents <= 1 ? Color.YELLOW : color;
         histogram_.setComponentColor(c, color, highlight);

         if (stats_ != null) {
            IntegerComponentStats componentStats = stats_.getComponentStats(c);
            updateScalingIndicators(settings, componentStats, c);
         }
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