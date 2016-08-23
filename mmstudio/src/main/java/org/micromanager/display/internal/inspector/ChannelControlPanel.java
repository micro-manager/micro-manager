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

package org.micromanager.display.internal.inspector;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import net.miginfocom.swing.MigLayout;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.NewSummaryMetadataEvent;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.HistogramData;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.NewHistogramsEvent;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.internal.events.HistogramRecalcEvent;
import org.micromanager.display.internal.events.HistogramRequestEvent;
import org.micromanager.display.internal.events.MouseExitedEvent;
import org.micromanager.display.internal.events.MouseMovedEvent;
import org.micromanager.display.internal.link.ContrastEvent;
import org.micromanager.display.internal.link.ContrastLinker;
import org.micromanager.display.internal.link.LinkButton;
import org.micromanager.internal.graph.GraphData;
import org.micromanager.internal.graph.HistogramCanvas;
import org.micromanager.internal.graph.HistogramCanvas.CursorListener;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Handles controls for a single histogram.
 */
public final class ChannelControlPanel extends JPanel implements CursorListener {

   public static final Dimension CONTROLS_SIZE = new Dimension(80, 80);

   // Names of RGB components
   private static final String[] COMPONENT_NAMES = new String[] {
      "red", "green", "blue"};
   // Icons to go with the components. These are completely hand-done (not
   // based on any external work).
   private static final Icon[] COMPONENT_ICONS_ACTIVE = new Icon[] {
      IconLoader.getIcon("/org/micromanager/icons/rgb_red.png"),
      IconLoader.getIcon("/org/micromanager/icons/rgb_green.png"),
      IconLoader.getIcon("/org/micromanager/icons/rgb_blue.png")
   };
   private static final Icon[] COMPONENT_ICONS_INACTIVE = new Icon[] {
      IconLoader.getIcon("/org/micromanager/icons/rgb_red_blank.png"),
      IconLoader.getIcon("/org/micromanager/icons/rgb_green_blank.png"),
      IconLoader.getIcon("/org/micromanager/icons/rgb_blue_blank.png")
   };

   // Event to tell other panels to go to "full" contrast.
   private static class FullScaleEvent {}

   // Event to tell other panels to set their histogram X scale to the given
   // index.
   private static class HistogramRangeEvent {
      public int index_;
      public HistogramRangeEvent(int index) {
         index_ = index;
      }
   }

   private HistogramsPanel parent_;
   private HistogramData[] lastHistograms_;
   private final int channelIndex_;
   private boolean hasChannelAxis_;
   private int curComponent_;
   private HistogramCanvas histogram_;
   private final ContrastLinker linker_;
   private final Datastore store_;
   private final DataViewer display_;

   private JButton autoButton_;
   private JButton zoomInButton_;
   private JButton zoomOutButton_;
   private JToggleButton isEnabledButton_;
   private JLabel nameLabel_;
   private JLabel colorPickerLabel_;
   private final JToggleButton[] componentPickerButtons_;
   private JButton fullButton_;
   private JLabel minMaxLabel_;
   private JComboBox histRangeComboBox_;
   private LinkButton linkButton_;

   private int lastX_ = -1;
   private int lastY_ = -1;

   private final AtomicBoolean haveInitialized_;

   public ChannelControlPanel(HistogramsPanel parent, int channelIndex,
         Datastore store, ContrastLinker linker, DataViewer display) {
      haveInitialized_ = new AtomicBoolean(false);
      parent_ = parent;
      channelIndex_ = channelIndex;
      curComponent_ = 0;
      // Start with 1 component; extend array as needed later.
      lastHistograms_ = new HistogramData[1];
      lastHistograms_[0] = null;
      store_ = store;
      linker_ = linker;
      display_ = display;
      // TODO: hardcoded to 3 elements for now.
      componentPickerButtons_ = new JToggleButton[3];

      // HACK: check for a lack of channel axis in the datastore; this requires
      // different behaviors at various points.
      hasChannelAxis_ = store_.getAxisLength(Coords.CHANNEL) > 0;

      store.registerForEvents(this);
      display.registerForEvents(this);
      // We can't create our GUI until we have histogram data to use, so
      // request it. If it's available it'll ping our NewHistogramsEvent
      // handler.
      display.postEvent(new HistogramRequestEvent(
               hasChannelAxis_ ? channelIndex_ : -1));
   }

   private void initialize() {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               initialize();
            }
         });
         return;
      }
      initComponents();
      reloadDisplaySettings();
      // Default to modifying the first component.
      updateCurComponent(componentPickerButtons_[0]);
      // Default to "camera depth" mode.
      histRangeComboBox_.setSelectedIndex(0);

      haveInitialized_.set(true);
      parent_.relayout();
      updateHighlight();
      redraw();
   }

   private void initComponents() {
      setBorder(BorderFactory.createRaisedBevelBorder());
      fullButton_ = new javax.swing.JButton();
      autoButton_ = new javax.swing.JButton();
      colorPickerLabel_ = new javax.swing.JLabel();
      // This icon is adapted from one of the many on this page:
      // http://thenounproject.com/term/eye/421/
      // (this particular one is public domain)
      isEnabledButton_ = new javax.swing.JToggleButton(
            new ImageIcon(getClass().getResource(
               "/org/micromanager/icons/eye.png")));
      minMaxLabel_ = new javax.swing.JLabel();

      setOpaque(false);

      Insets zeroInsets = new Insets(0, 0, 0, 0);
      Dimension buttonSize = new Dimension(90, 25);

      fullButton_.setFont(new Font("Arial", Font.PLAIN, 9));
      fullButton_.setMargin(zeroInsets);
      fullButton_.setName("Full channel histogram width");
      fullButton_.setText("Full");
      fullButton_.setToolTipText("Set the min to 0 and the max to the current display range");
      fullButton_.setMaximumSize(buttonSize);
      fullButton_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            fullButtonAction();
         }
      });

      autoButton_.setFont(new Font("Arial", Font.PLAIN, 9));
      autoButton_.setMargin(zeroInsets);
      autoButton_.setName("Auto channel histogram width");
      autoButton_.setText("Auto once");
      autoButton_.setToolTipText("Set the min and max to the min and max in the current image");
      autoButton_.setMaximumSize(buttonSize);
      autoButton_.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
      autoButton_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            autoButtonAction();
         }
      });

      Dimension smallButtonSize = new Dimension(20, 20);

      isEnabledButton_.setMargin(zeroInsets);
      isEnabledButton_.setToolTipText("Show/hide this channel in the multi-dimensional viewer");
      isEnabledButton_.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            isEnabledAction();
         }
      });
      isEnabledButton_.setSize(smallButtonSize);
      isEnabledButton_.setSelected(true);

      colorPickerLabel_.setBackground(
            display_.getDisplaySettings().getSafeChannelColor(
               channelIndex_, Color.WHITE));
      colorPickerLabel_.setMinimumSize(smallButtonSize);
      colorPickerLabel_.setToolTipText("Change the color for displaying this channel");
      colorPickerLabel_.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
      colorPickerLabel_.setOpaque(true);
      colorPickerLabel_.addMouseListener(new java.awt.event.MouseAdapter() {
         @Override
         public void mouseClicked(java.awt.event.MouseEvent evt) {
            colorPickerLabelMouseClicked();
         }
      });

      for (int i = 0; i < COMPONENT_ICONS_INACTIVE.length; ++i) {
         final JToggleButton button = new JToggleButton(COMPONENT_ICONS_INACTIVE[i]);
         button.setToolTipText("Switch to controlling the " +
               COMPONENT_NAMES[i] + " component");
         componentPickerButtons_[i] = button;
         button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               updateCurComponent(button);
            }
         });
      }

      minMaxLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      minMaxLabel_.setText("<html>Min/Max/Mean:<br>00/00/00</html>");

      histRangeComboBox_ = new JComboBox();
      histRangeComboBox_.setFont(new Font("", Font.PLAIN, 10));
      histRangeComboBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            DisplaySettings settings = display_.getDisplaySettings();
            if (settings.getShouldSyncChannels() != null &&
                  settings.getShouldSyncChannels()) {
               display_.postEvent(new HistogramRangeEvent(
                     histRangeComboBox_.getSelectedIndex()));
            }
            displayComboAction();
         }
      });
      histRangeComboBox_.setModel(new DefaultComboBoxModel(new String[]{
            "Camera Depth", "4bit (0-15)", "5bit (0-31)", "6bit (0-63)",
            "7bit (0-127)", "8bit (0-255)", "9bit (0-511)", "10bit (0-1023)",
            "11bit (0-2047)", "12bit (0-4095)", "13bit (0-8191)",
            "14bit (0-16383)", "15bit (0-32767)", "16bit (0-65535)"}));

      zoomInButton_ = new JButton(IconLoader.getIcon(
               "/org/micromanager/icons/triangle_left.png"));
      zoomInButton_.setMinimumSize(new Dimension(16, 16));
      zoomInButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            zoomInAction();
         }
      });
      
      zoomOutButton_ = new JButton(IconLoader.getIcon(
               "/org/micromanager/icons/triangle_right.png"));
      zoomOutButton_.setMinimumSize(new Dimension(16, 16));
      zoomOutButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            zoomOutAction();
         }
      });

      // Allocate all extra space to the histogram, not the controls on the
      // left.
      setLayout(new MigLayout("fill, flowx, insets 0",
               "[grow 0][fill]"));

      // Minimize gapping between the full and auto buttons.
      JPanel firstColumn = new JPanel(
            new MigLayout("novisualpadding, insets 0, flowy",
               "[]", "[][][]0[]"));
      nameLabel_ = new JLabel(
            store_.getSummaryMetadata().getSafeChannelName(channelIndex_));
      firstColumn.add(nameLabel_, "alignx center");
      firstColumn.add(isEnabledButton_, "split 3, flowx");
      // Depending on the number of components, we show a color picker or a
      // component control selector.
      if (lastHistograms_.length == 1) {
         firstColumn.add(colorPickerLabel_, "aligny center");
      }
      else {
         boolean isFirst = true;
         JPanel subPanel = new JPanel(new MigLayout("flowx, insets 0, gap 0"));
         for (JToggleButton button : componentPickerButtons_) {
            subPanel.add(button, "aligny center, w 16!, h 16!, gap 0! 0!");
         }
         firstColumn.add(subPanel, "aligny center");
      }
      if (linker_ != null) {
         linkButton_ = new LinkButton(linker_, display_);
         linkButton_.setMinimumSize(new Dimension(linkButton_.getWidth(),
                  smallButtonSize.height));
         firstColumn.add(linkButton_, "aligny center, hidemode 2");
      }
      firstColumn.add(fullButton_, "alignx center, width 70!");
      firstColumn.add(autoButton_, "alignx center, width 70!");

      add(firstColumn, "growx 0");

      JPanel secondColumn = new JPanel(new MigLayout("insets 0, flowy, fill"));

      histogram_ = makeHistogramCanvas();
      updateHistogramColor(display_.getDisplaySettings().getSafeChannelColor(
               channelIndex_, Color.WHITE));
      histogram_.setMinimumSize(new Dimension(100, 100));
      histogram_.setToolTipText("Adjust the brightness and contrast by dragging triangles at top and bottom. Change the gamma by dragging the curve. (These controls only change display, and do not edit the image data.)");

      secondColumn.add(histogram_, "grow, gapright 0, pushy 100");

      // The two buttons should be right next to the dropdown they control.
      JPanel scalePanel = new JPanel(new MigLayout("fill, insets 0",
            "push[]0[]0[]push"));
      // Tweak padding to eliminate blank space.
      scalePanel.add(zoomInButton_,
            "gapright 0, width ::15, height 20!, pad -1 0 0 4, aligny center center");
      scalePanel.add(histRangeComboBox_, "gapleft 0, gapright 0, height 20!, aligny center center");
      scalePanel.add(zoomOutButton_,
            "gapleft 0, width ::15, height 20!, pad -1 -4 0 0, aligny center center");
      scalePanel.add(minMaxLabel_);

      secondColumn.add(scalePanel, "pushy 0");
      add(secondColumn, "grow");

      validate();
   }

   /**
    * Do a logarithmic (powers of 2) zoom, which in turn updates our displayed
    * bit depth.
    */
   private void zoomInAction() {
      updateZoom(-1);
   }

   private void zoomOutAction() {
      updateZoom(1);
   }

   private void updateZoom(int modifier) {
      int index = histRangeComboBox_.getSelectedIndex();
      int power;
      if (index == 0) {
         // Currently on "camera bit depth" mode; adjust from there.
         power = Math.max(0,
               lastHistograms_[curComponent_].getBitDepth() + modifier);
      }
      else {
         // Indices correspond to powers + 3
         power = index + 3 + modifier;
      }
      // TODO: hardcoded minimum/maximum power here.
      power = Math.max(4, Math.min(16, power));
      // Reflect the current power setting.
      histRangeComboBox_.setSelectedIndex(power - 3);
      updateHistogram();
   }

   public void displayComboAction() {
      int index = histRangeComboBox_.getSelectedIndex();
      // Update the display settings.
      DisplaySettings settings = display_.getDisplaySettings();
      boolean didMakeChanges = false;
      Integer[] curIndices = settings.getBitDepthIndices();
      if (curIndices == null || curIndices.length <= channelIndex_) {
         // Expand the array to contain our value.
         Integer[] indices = new Integer[channelIndex_ + 1];
         for (int i = 0; i < indices.length; ++i) {
            indices[i] = (curIndices != null && curIndices.length > i) ? curIndices[i] : 0;
         }
         curIndices = indices;
         didMakeChanges = true;
      }
      if (curIndices[channelIndex_] == null ||
            curIndices[channelIndex_] != index) {
         curIndices[channelIndex_] = index;
         didMakeChanges = true;
      }
      if (didMakeChanges) {
         settings = settings.copy().bitDepthIndices(curIndices).build();
         display_.setDisplaySettings(settings);
      }

      redraw();
   }

   private void fullButtonAction() {
      DisplaySettings settings = display_.getDisplaySettings();
      if (settings.getShouldSyncChannels() != null &&
            settings.getShouldSyncChannels()) {
         // This will make all histograms, including us, call the code in the
         // else block here (see onFullScale()).
         display_.postEvent(new FullScaleEvent());
      }
      else {
         setScale(0,
               (int) Math.pow(2, lastHistograms_[curComponent_].getBitDepth()));
      }
   }

   /**
    * Update contrast settings for the specified component into the provided
    * builder. Ignore parameters that are null (retain the old value).
    * Post the new settings to the display if the shouldPost boolean is set.
    */
   private DisplaySettings.DisplaySettingsBuilder updateContrastSettings(
         DisplaySettings.DisplaySettingsBuilder builder, int component,
         Integer minVal, Integer maxVal, Double gamma, boolean shouldPost,
         boolean shouldDisableAutostretch) {
      int numComponents = lastHistograms_.length;
      DisplaySettings settings = display_.getDisplaySettings();
      Integer[] mins = new Integer[numComponents];
      Integer[] maxes = new Integer[numComponents];
      Double[] gammas = new Double[numComponents];
      for (int i = 0; i < numComponents; ++i) {
         mins[i] = settings.getSafeContrastMin(channelIndex_, i,
               lastHistograms_[i].getMinVal());
         maxes[i] = settings.getSafeContrastMax(channelIndex_, i,
               lastHistograms_[i].getMaxVal());
         gammas[i] = settings.getSafeContrastGamma(channelIndex_, i, 1.0);
      }
      if (minVal != null) {
         mins[component] = minVal;
      }
      if (maxVal != null) {
         maxes[component] = maxVal;
      }
      if (gamma != null) {
         gammas[component] = gamma;
      }
      DisplaySettings.ContrastSettings contrast =
         new DefaultDisplaySettings.DefaultContrastSettings(
               mins, maxes, gammas, isEnabledButton_.isSelected());
      builder.safeUpdateContrastSettings(contrast, channelIndex_);
      if (shouldPost) {
         if (shouldDisableAutostretch) {
            builder.shouldAutostretch(false);
         }
         DisplaySettings newSettings = builder.build();
         postContrastEvent(newSettings);
         display_.setDisplaySettings(newSettings);
         display_.postEvent(new HistogramRecalcEvent(
                  hasChannelAxis_ ? channelIndex_ : -1));
      }
      return builder;
   }

   @Subscribe
   public void onFullScale(FullScaleEvent event) {
      setScale(0,
            (int) Math.pow(2, lastHistograms_[curComponent_].getBitDepth()));
   }

   /**
    * Set the contrast settings for our channel to the given min/max.
    */
   private void setScale(int min, int max) {
      DisplaySettings settings = display_.getDisplaySettings();
      DisplaySettings.DisplaySettingsBuilder builder = settings.copy();
      boolean didChange = false;
      if (settings.getShouldAutostretch() != null &&
            settings.getShouldAutostretch()) {
         builder.shouldAutostretch(false);
         didChange = true;
      }
      int curMin = settings.getSafeContrastMin(channelIndex_,
            curComponent_,
            lastHistograms_[curComponent_].getMinIgnoringOutliers());
      int curMax = settings.getSafeContrastMax(channelIndex_,
            curComponent_,
            lastHistograms_[curComponent_].getMaxIgnoringOutliers());
      if (curMin != min || curMax != max) {
         // New values are different from old ones.
         builder = updateContrastSettings(builder,
               curComponent_, min, max, null, false, true);
         didChange = true;
      }
      if (didChange) {
         DisplaySettings newSettings = builder.build();
         postContrastEvent(newSettings);
         display_.setDisplaySettings(newSettings);
      }
   }

   /**
    *
    * @param event
    */
   @Subscribe
   public void onHistogramRange(HistogramRangeEvent event) {
      if (histRangeComboBox_.getSelectedIndex() != event.index_) {
         histRangeComboBox_.setSelectedIndex(event.index_);
      }
   }

   public void autoButtonAction() {
      setScale(lastHistograms_[curComponent_].getMinIgnoringOutliers(),
            lastHistograms_[curComponent_].getMaxIgnoringOutliers());
   }

   /**
    * Pop up a dialog to let the user set a new color for our channel.
    */
   private void colorPickerLabelMouseClicked() {
      SummaryMetadata summary = store_.getSummaryMetadata();
      // Pick an appropriate string for the dialog prompt.
      String name = "selected";
      String[] channelNames = summary.getChannelNames();
      if (channelNames != null && channelNames.length > channelIndex_) {
         name = channelNames[channelIndex_];
      }

      DisplaySettings settings = display_.getDisplaySettings();
      // Pick the default color to start with.
      Color defaultColor = settings.getSafeChannelColor(channelIndex_,
            Color.WHITE);

      Color newColor = JColorChooser.showDialog(this, "Choose a color for the "
              + name + " channel", defaultColor);
      if (newColor != null) {
         // Update the display settings.
         settings = settings.copy().safeUpdateChannelColor(newColor,
               channelIndex_).build();
         display_.setDisplaySettings(settings);
      }
      reloadDisplaySettings();
   }

   /**
    * The specified button has been clicked; set the appropriate current
    * component for control.
    */
   private void updateCurComponent(JToggleButton button) {
      for (int i = 0; i < componentPickerButtons_.length; ++i) {
         JToggleButton altButton = componentPickerButtons_[i];
         if (altButton == button) {
            curComponent_ = i;
            altButton.setSelected(true);
            altButton.setIcon(COMPONENT_ICONS_ACTIVE[i]);
         }
         else {
            altButton.setSelected(false);
            altButton.setIcon(COMPONENT_ICONS_INACTIVE[i]);
         }
         button.repaint();
      }
      updateHistogram();
   }

   private void postContrastEvent(DisplaySettings newSettings) {
      String[] channelNames = store_.getSummaryMetadata().getChannelNames();
      String name = null;
      if (channelNames != null && channelNames.length > channelIndex_) {
         name = channelNames[channelIndex_];
      }
      display_.postEvent(new ContrastEvent(
               hasChannelAxis_ ? channelIndex_ : -1, name, newSettings));
   }

   private void isEnabledAction() {
      // These icons are adapted from the public-domain icon at
      // https://openclipart.org/detail/182888/eye-icon
      isEnabledButton_.setIcon(isEnabledButton_.isSelected() ?
            new ImageIcon(getClass().getResource(
                  "/org/micromanager/icons/eye.png")) :
            new ImageIcon(getClass().getResource(
                  "/org/micromanager/icons/eye-out.png")));
      DisplaySettings.DisplaySettingsBuilder builder =
         display_.getDisplaySettings().copy();
      updateContrastSettings(display_.getDisplaySettings().copy(),
            curComponent_, null, null, null, true, false);
   }

   private HistogramCanvas makeHistogramCanvas() {
      HistogramCanvas canvas = new HistogramCanvas() {
         @Override
         public void paint(Graphics g) {
            super.paint(g);
            //For drawing max label
            g.setColor(Color.black);
            g.setFont(new Font("Lucida Grande", 0, 10));
            int maxVal = (int) Math.pow(2, histRangeComboBox_.getSelectedIndex() + 3);
            if (histRangeComboBox_.getSelectedIndex() == 0) {
               // Use camera depth instead.
               maxVal = (int) Math.pow(2,
                     lastHistograms_[curComponent_].getBitDepth());
            }
            String maxLabel = Integer.toString(maxVal - 1);
            g.drawString(maxLabel, getSize().width - 8 * maxLabel.length(),
                  getSize().height);
         }
      };

      canvas.setMargins(12, 12);
      canvas.setToolTipText("Click and drag curve to adjust gamma");
      canvas.addCursorListener(this);
      return canvas;
   }

   /**
    * Update the histogram color -- only for single-component images, as
    * multi-component images use hardcoded colors.
    */
   private void updateHistogramColor(Color color) {
      if (lastHistograms_.length == 1) {
         // Just one component.
         DisplaySettings.ColorMode mode = display_.getDisplaySettings().getChannelColorMode();
         if (mode != null && mode == DisplaySettings.ColorMode.GRAYSCALE) {
            histogram_.setTraceStyle(true, 0, Color.WHITE);
         }
         else {
            histogram_.setTraceStyle(true, 0, color);
         }
      }
      else {
         // Multi-component images default to RGB.
         histogram_.setTraceStyle(false, 0, Color.RED);
         histogram_.setTraceStyle(false, 1, Color.GREEN);
         histogram_.setTraceStyle(false, 2, Color.BLUE);
      }
   }

   /**
    * Update our GUI to reflect changes in the display settings.
    */
   public void reloadDisplaySettings() {
      // Figure out which color to use.
      DisplaySettings settings = display_.getDisplaySettings();
      Color color = settings.getSafeChannelColor(channelIndex_, Color.WHITE);

      colorPickerLabel_.setBackground(color);
      updateHistogramColor(color);

      Integer bitDepthIndex = settings.getSafeBitDepthIndex(channelIndex_, 0);
      if (histRangeComboBox_.getSelectedIndex() != bitDepthIndex) {
         histRangeComboBox_.setSelectedIndex(bitDepthIndex);
      }

      DisplaySettings.ColorMode mode = settings.getChannelColorMode();
      // Eye buttons are only shown when in composite mode.
      if (mode != null) {
         isEnabledButton_.setVisible(
               mode == DisplaySettings.ColorMode.COMPOSITE);
      }
      Double updateRate = settings.getHistogramUpdateRate();
      if (updateRate != null && updateRate < 0) {
         histogram_.setOverlayText("Updates disabled");
      }
      else {
         histogram_.setOverlayText(null);
      }
      redraw();
   }

   @Override
   public void contrastMaxInput(int max) {
      setMax(max);
   }

   private void setMax(int max) {
      // Don't go below current min.
      int limit = display_.getDisplaySettings().getSafeContrastMin(
               channelIndex_, curComponent_,
               lastHistograms_[curComponent_].getMinIgnoringOutliers());
      max = Math.max(max, limit + 1);
      updateContrastSettings(display_.getDisplaySettings().copy(),
            curComponent_, null, max, null, true, true);
   }

   @Override
   public void contrastMinInput(int min) {
      setMin(min);
   }

   private void setMin(int min) {
      // Don't go above current max.
      int limit = display_.getDisplaySettings().getSafeContrastMax(
               channelIndex_, curComponent_,
               lastHistograms_[curComponent_].getMinIgnoringOutliers());
      min = Math.min(min, limit - 1);
      updateContrastSettings(display_.getDisplaySettings().copy(),
            curComponent_, min, null, null, true, true);
   }

   @Override
   public void onLeftCursor(double pos) {
      HistogramData hist = lastHistograms_[curComponent_];
      setMin((int) (Math.max(0, pos) * hist.getBinSize()));
   }

   @Override
   public void onRightCursor(double pos) {
      HistogramData hist = lastHistograms_[curComponent_];
      setMax((int) (Math.min(hist.getHistogram().length - 1, pos) * hist.getBinSize()));
   }

   @Override
   public void onGammaCurve(double gamma) {
      updateContrastSettings(display_.getDisplaySettings().copy(),
            curComponent_, null, null, gamma, true, true);
   }

   /**
    * Display settings have changed; update our GUI to match.
    */
   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      if (!haveInitialized_.get()) {
         // TODO: there's a race condition here if we've already set the
         // values that reloadDisplaySettings() modify -- if
         // they aren't yet available, though, then that method will fail.
         return;
      }
      try {
         reloadDisplaySettings();
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to update histogram display settings");
      }
   }

   /**
    * Summary metadata has changed; check for change in channel name.
    */
   @Subscribe
   public void onNewSummaryMetadata(NewSummaryMetadataEvent event) {
      if (!haveInitialized_.get()) {
         // See TODO note in onNewDisplaySettings.
         return;
      }
      nameLabel_.setText(
            event.getSummaryMetadata().getSafeChannelName(channelIndex_));
   }

   /**
    * Receive new histogram data.
    */
   @Subscribe
   public void onNewHistograms(final NewHistogramsEvent event) {
      // We don't know what thread we may be called in from, and as we modify
      // the GUI (especially if initialize() is called), we need to be on the
      // EDT.
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               onNewHistograms(event);
            }
         });
         return;
      }
      try {
         if (event.getChannel() != channelIndex_ && hasChannelAxis_) {
            // Wrong channel.
            return;
         }
         // Expand our number of components, if necessary.
         if (lastHistograms_.length < event.getNumComponents()) {
            lastHistograms_ = new HistogramData[event.getNumComponents()];
         }
         for (int i = 0; i < event.getNumComponents(); ++i) {
            lastHistograms_[i] = event.getHistogram(i);
         }

         if (!haveInitialized_.get()) {
            // Need to create our GUI now. GUI creation will also call
            // updateHighlight() and redraw().
            initialize();
            return;
         }
         updateHighlight();
         redraw();
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error receiving new histograms");
      }
   }

   /**
    * The mouse is moving over the canvas; highlight the bin in the histogram
    * corresponding to the pixel under the mouse.
    * @param event
    */
   @Subscribe
   public void onMouseMoved(MouseMovedEvent event) {
      lastX_ = event.getX();
      lastY_ = event.getY();
      updateHighlight();
   }

   /**
    * The mouse has left the canvas; stop highlighting it.
    */
   @Subscribe
   public void onMouseExited(MouseExitedEvent event) {
      lastX_ = -1;
      lastY_ = -1;
      updateHighlight();
   }

   private void updateHighlight() {
      if (haveInitialized_.get()) {
         if (lastX_ >= 0 && lastY_ >= 0) {
            // Highlight the intensity of the pixel the mouse is on in the
            // image.
            for (Image image : display_.getDisplayedImages()) {
               int channel = image.getCoords().getChannel();
               if (channel == channelIndex_ ||
                     (!hasChannelAxis_ && channel == -1)) {
                  long intensity = image.getComponentIntensityAt(
                        lastX_, lastY_, curComponent_);
                  HistogramData data = lastHistograms_[curComponent_];
                  histogram_.setHighlight(intensity / data.getBinSize());
               }
            }
         }
         else {
            // Disable highlights.
            histogram_.setHighlight(-1);
         }
      }
   };

   public void cleanup() {
      display_.unregisterForEvents(this);
      store_.unregisterForEvents(this);
      if (linkButton_ != null) {
         linkButton_.cleanup();
      }
   }

   private void updateHistogram() {
      if (histogram_ == null) {
         // Don't actually have a histogram yet. This can happen in weird
         // multi-channel situations. TODO: is that in fact still true?
         return;
      }
      DisplaySettings settings = display_.getDisplaySettings();
      DisplaySettings.ContrastSettings contrasts = settings.getSafeContrastSettings(
            channelIndex_, new DefaultDisplaySettings.DefaultContrastSettings(
               0, 0, 1.0, true));
      int binSize = lastHistograms_[curComponent_].getBinSize();
      for (int i = 0; i < Math.min(lastHistograms_.length, contrasts.getNumComponents()); ++i) {
         int minVal = contrasts.getSafeContrastMin(i,
               lastHistograms_[i].getMinVal());
         int maxVal = contrasts.getSafeContrastMax(i,
               lastHistograms_[i].getMaxVal());
         double gamma = contrasts.getSafeContrastGamma(i, 1.0);
         histogram_.setCursors(i, minVal / binSize, (maxVal + 1) / binSize,
               gamma);
         if (i == curComponent_) {
            histogram_.setCursorText(minVal + "", maxVal + "");
         }
      }
      histogram_.setCurComponent(curComponent_);
      histogram_.repaint();
      String label = String.format("Min/Max/Mean");
      String values = String.format("%d/%d/%d",
               lastHistograms_[curComponent_].getMinVal(),
               lastHistograms_[curComponent_].getMaxVal(),
               lastHistograms_[curComponent_].getMean());
      if (lastHistograms_[curComponent_].getStdDev() >= 0) {
         // Have a valid standard deviation.
         label += "/Std";
         values += String.format("/%.2f",
               lastHistograms_[curComponent_].getStdDev());
      }
      minMaxLabel_.setText(String.format(
               "<html>%s:<br>%s</html>", label, values));
   }

   public void redraw() {
      if (histogram_ == null) {
         // Can't do anything yet.
         return;
      }
      if (!isEnabledButton_.isSelected()) {
         histogram_.setVisible(false);
         return;
      }
      updateHistogram();
      histogram_.setVisible(true);
      // Draw histogram and stats. This includes respecifying the histogram's
      // visible data and X scale as those may have changed.
      for (int i = 0; i < lastHistograms_.length; ++i) {
         if (lastHistograms_[i].getNumSamples() > 0) {
            GraphData graphData = new GraphData();
            graphData.setData(lastHistograms_[i].getHistogram());
            histogram_.setData(i, graphData);
            Boolean isLog = display_.getDisplaySettings().getShouldUseLogScale();
            if (isLog != null) {
               histogram_.setLogScale(isLog);
            }
            // Set X scale of histogram. Default to camera depth.
            int scalePow = lastHistograms_[i].getBitDepth();
            if (histRangeComboBox_.getSelectedIndex() != 0) {
               // Add 3 to get from index to power-of-2.
               scalePow = histRangeComboBox_.getSelectedIndex() + 3;
            }
            histogram_.setXDataMax(Math.pow(2, scalePow));
         }
      }
      histogram_.repaint();
   }

   @Override
   public String toString() {
      return String.format("ChannelControlPanel for channel %d>",
            hasChannelAxis_ ? channelIndex_ : -1);
   }
}
