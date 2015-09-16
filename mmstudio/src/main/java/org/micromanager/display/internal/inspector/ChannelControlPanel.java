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
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.NewSummaryMetadataEvent;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.NewDisplaySettingsEvent;

import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.internal.graph.GraphData;
import org.micromanager.internal.graph.HistogramPanel;
import org.micromanager.internal.graph.HistogramPanel.CursorListener;

import org.micromanager.display.internal.ChannelHistogramModel;
import org.micromanager.display.internal.events.LUTUpdateEvent;
import org.micromanager.display.internal.link.ContrastEvent;
import org.micromanager.display.internal.link.DisplayGroupManager;
import org.micromanager.display.internal.link.LinkButton;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.internal.DefaultDisplayWindow;
import org.micromanager.display.internal.DisplayDestroyedEvent;

import org.micromanager.internal.utils.ReportingUtils;

/**
 * Handles controls for a single histogram.
 */
public class ChannelControlPanel extends JPanel implements CursorListener {

   public static final Dimension CONTROLS_SIZE = new Dimension(80, 80);
  
   private ChannelHistogramModel model_;
   private final int channelIndex_;
   private HistogramPanel histogram_;
   private final HistogramsPanel parent_;
   private final Datastore store_;
   private DefaultDisplayWindow display_;

   private JButton autoButton_;
   private JButton zoomInButton_;
   private JButton zoomOutButton_;
   private JToggleButton isEnabledButton_;
   private JLabel nameLabel_;
   private JLabel colorPickerLabel_;
   private JButton fullButton_;
   private JLabel minMaxLabel_;
   private JComboBox histRangeComboBox_;
   private LinkButton linkButton_;

   private final AtomicBoolean haveInitialized_;

   public ChannelControlPanel(int channelIndex, HistogramsPanel parent,
         Datastore store, DefaultDisplayWindow display) {
      haveInitialized_ = new AtomicBoolean(false);
      channelIndex_ = channelIndex;
      parent_ = parent;
      store_ = store;
      display_ = display;
      model_ = display_.getHistogramModel(channelIndex_);

      // Must be registered for events before we start modifying images, since
      // that relies on LUTUpdateEvent.
      store.registerForEvents(this);
      display.registerForEvents(this);
      List<Image> images = store_.getImagesMatching(
            (new DefaultCoords.Builder()).channel(channelIndex_).build());
      if (images != null && images.size() > 0) {
         // Found an image for our channel
         initialize();
      }
   }

   private void initialize() {
      initComponents();
      reloadDisplaySettings();
      // Default to "camera depth" mode.
      histRangeComboBox_.setSelectedIndex(0);

      haveInitialized_.set(true);
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

      fullButton_.setFont(fullButton_.getFont().deriveFont((float) 9));
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

      autoButton_.setFont(autoButton_.getFont().deriveFont((float) 9));
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

      colorPickerLabel_.setBackground(model_.getColor());
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
               parent_.updateOtherDisplayCombos(histRangeComboBox_.getSelectedIndex());
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
      nameLabel_ = new JLabel(model_.getName());
      firstColumn.add(nameLabel_, "alignx center");
      firstColumn.add(isEnabledButton_, "split 3, flowx");
      firstColumn.add(colorPickerLabel_, "aligny center");
      linkButton_ = new LinkButton(
            DisplayGroupManager.getContrastLinker(channelIndex_, display_),
            display_);
      linkButton_.setMinimumSize(new Dimension(linkButton_.getWidth(),
               smallButtonSize.height));
      firstColumn.add(linkButton_, "aligny center");
      firstColumn.add(fullButton_, "alignx center, width 70!");
      firstColumn.add(autoButton_, "alignx center, width 70!");

      add(firstColumn, "growx 0");

      JPanel secondColumn = new JPanel(new MigLayout("insets 0, flowy, fill"));

      histogram_ = makeHistogramPanel();
      histogram_.setMinimumSize(new Dimension(100, 100));
      histogram_.setToolTipText("Adjust the brightness and contrast by dragging triangles at top and bottom. Change the gamma by dragging the curve. (These controls only change display, and do not edit the image data.)");

      secondColumn.add(histogram_, "grow, gapright 0");

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

      secondColumn.add(scalePanel);
      add(secondColumn, "growx");

      validate();
   }

   public void setDisplayComboIndex(int index) {
      histRangeComboBox_.setSelectedIndex(index);
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
      int power = 0;
      if (index == 0) {
         // Currently on "camera bit depth" mode; adjust from there.
         power = Math.max(0, model_.getBitDepth() + modifier);
      }
      else {
         // Indices correspond to powers + 3
         power = index + 3 + modifier;
      }
      // TODO: hardcoded minimum/maximum power here.
      power = Math.max(4, Math.min(16, power));
      model_.updateHistMax(power);
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

      updateHistogram();
      calcAndDisplayHistAndStats(true);
   }

   private void updateHistogram() {
      if (histogram_ == null) {
         // Don't actually have a histogram yet. This can happen in weird
         // multi-channel situations.
         return;
      }
      histogram_.setCursorText(model_.getContrastMin() + "",
            model_.getContrastMax() + "");
      histogram_.setCursors(model_.getContrastMin() / model_.getBinSize(),
            (model_.getContrastMax() + 1) / model_.getBinSize(),
            model_.getContrastGamma());
      histogram_.repaint();
   }

   private void fullButtonAction() {
      model_.disableAutostretch();
      DisplaySettings settings = display_.getDisplaySettings();
      if (settings.getShouldSyncChannels() != null &&
            settings.getShouldSyncChannels()) {
         parent_.fullScaleChannels();
      } else {
         model_.setFullScale();
      }
   }

   public ChannelHistogramModel getModel() {
      return model_;
   }

   public void autoButtonAction() {
      model_.autostretch();
   }

   /**
    * Pop up a dialog to let the user set a new color for our channel.
    */
   private void colorPickerLabelMouseClicked() {
      // Pick an appropriate string for the dialog prompt.
      String name = "selected";
      String[] channelNames = store_.getSummaryMetadata().getChannelNames();
      if (channelNames != null && channelNames.length > channelIndex_) {
         name = channelNames[channelIndex_];
      }

      // Pick the default color to start with.
      DisplaySettings settings = display_.getDisplaySettings();
      Color defaultColor = model_.getColor();
      Color[] channelColors = settings.getChannelColors();
      if (channelColors != null && channelColors.length > channelIndex_) {
         defaultColor = channelColors[channelIndex_];
      }

      Color newColor = JColorChooser.showDialog(this, "Choose a color for the "
              + name + " channel", defaultColor);
      if (newColor != null) {
         // Update the display settings.
         Color[] newColors = channelColors;
         if (newColors == null) {
            // Create a new empty array, which will be filled in below.
            newColors = new Color[] {};
         }
         if (newColors.length <= channelIndex_) {
            // Expand the array and fill the new entries with white.
            // TODO: use differentiated colors instead of white everywhere.
            newColors = new Color[channelIndex_ + 1];
            for (int i = 0; i < newColors.length; ++i) {
               if (channelColors == null || i >= channelColors.length) {
                  newColors[i] = Color.WHITE;
               }
               else {
                  newColors[i] = channelColors[i];
               }
            }
         }
         newColors[channelIndex_] = newColor;
         DisplaySettings newSettings = settings.copy().channelColors(newColors).build();
         display_.setDisplaySettings(newSettings);
      }
      reloadDisplaySettings();
   }

   private void postContrastEvent(DisplaySettings newSettings) {
      String[] channelNames = display_.getDatastore().getSummaryMetadata().getChannelNames();
      String name = null;
      if (channelNames != null && channelNames.length > channelIndex_) {
         name = channelNames[channelIndex_];
      }
      display_.postEvent(new ContrastEvent(channelIndex_, name, newSettings));
   }

   private void isEnabledAction() {
      // These icons are adapted from the public-domain icon at
      // https://openclipart.org/detail/182888/eye-icon
      isEnabledButton_.setIcon(isEnabledButton_.isSelected() ?
            new ImageIcon(getClass().getResource(
                  "/org/micromanager/icons/eye.png")) :
            new ImageIcon(getClass().getResource(
                  "/org/micromanager/icons/eye-out.png")));
      model_.setChannelEnabled(isEnabledButton_.isSelected());
   }

   private HistogramPanel makeHistogramPanel() {
      HistogramPanel hp = new HistogramPanel() {
         @Override
         public void paint(Graphics g) {
            super.paint(g);
            //For drawing max label
            g.setColor(Color.black);
            g.setFont(new Font("Lucida Grande", 0, 10));
            g.drawString(model_.getHistMaxLabel(),
                  this.getSize().width - 8 * model_.getHistMaxLabel().length(),
                  this.getSize().height);
         }
      };

      hp.setMargins(12, 12);
      hp.setTraceStyle(true, model_.getColor());
      hp.setToolTipText("Click and drag curve to adjust gamma");
      hp.addCursorListener(this);
      return hp;
   }

   /**
    * Pull new color and contrast settings from our DisplayWindow's
    * DisplaySettings, or from the ChannelSettings for our channel name if our
    * DisplaySettings are deficient.
    * The priorities for values here are:
    * 1) If single-channel, then we use a white color
    * 2) Use the values in the display settings
    * 3) If those values aren't available, use the values in the
    *    ChannelSettings for our channel name.
    * 4) If *those* values aren't available, use hardcoded defaults.
    */
   public void reloadDisplaySettings() {
      model_.reloadDisplaySettings();

      if (histRangeComboBox_.getSelectedIndex() != model_.getHistRangeIndex()) {
         histRangeComboBox_.setSelectedIndex(model_.getHistRangeIndex());
      }

      Color color = model_.getColor();
      colorPickerLabel_.setBackground(color);
      histogram_.setTraceStyle(true, color);

      DisplaySettings.ColorMode mode = display_.getDisplaySettings().getChannelColorMode();
      // Eye buttons are only enabled when in composite mode.
      if (mode != null) {
         isEnabledButton_.setEnabled(
               mode == DisplaySettings.ColorMode.COMPOSITE);
      }

      updateHistogram();
      calcAndDisplayHistAndStats(true);
   }

   public void setContrast(int min, int max, double gamma) {
      model_.setContrast(min, max, gamma);
   }

   @Subscribe
   public void onLUTUpdate(LUTUpdateEvent event) {
      try {
         updateHistogram();
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error updating LUT");
      }
   }

   @Override
   public void contrastMaxInput(int max) {
      model_.setContrastMax(max);
   }
   
   @Override
   public void contrastMinInput(int min) {    
      model_.setContrastMin(min);
   }

   @Override
   public void onLeftCursor(double pos) {
      model_.setContrastMin((int) (Math.max(0, pos) * model_.getBinSize()));
   }

   @Override
   public void onRightCursor(double pos) {
      model_.setContrastMax(
            (int) (Math.min(ChannelHistogramModel.NUM_BINS - 1, pos) * model_.getBinSize()));
   }

   @Override
   public void onGammaCurve(double gamma) {
      model_.setContrastGamma(gamma);
   }

   /**
    * Display settings have changed; update our color.
    * @param event
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
    * @param event
    */
   @Subscribe
   public void onNewSummaryMetadata(NewSummaryMetadataEvent event) {
      if (!haveInitialized_.get()) {
         // See TODO note in onNewDisplaySettings.
         return;
      }
      String[] names = event.getSummaryMetadata().getChannelNames();
      if (names != null && names.length > channelIndex_) {
         nameLabel_.setText(names[channelIndex_]);
      }
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      try {
         cleanup();
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error when cleaning up histogram");
      }
   }

   public void cleanup() {
      if (display_ != null) {
         try {
            display_.unregisterForEvents(this);
         }
         catch (IllegalArgumentException e) {
            // We were already unregistered because cleanup() was called
            // from HistogramsPanel after it was called from
            // onDisplayDestroyed; ignore it.
         }
      }
      linkButton_.cleanup();
   }

   public void calcAndDisplayHistAndStats(boolean shouldDrawHistogram) {
      int[] histogram = model_.calcHistogramStats();
      if (histogram == null || !model_.getChannelEnabled() ||
            !shouldDrawHistogram) {
         histogram_.setVisible(false);
         return;
      }
      histogram_.setVisible(true);
      //Draw histogram and stats
      GraphData histogramData = new GraphData();
      histogramData.setData(histogram);
      histogram_.setData(histogramData);
      histogram_.setAutoScale();
      histogram_.repaint();
   }
}
