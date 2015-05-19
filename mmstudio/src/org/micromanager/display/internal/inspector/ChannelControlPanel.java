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

import ij.CompositeImage;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.LUT;

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

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.NewImageEvent;
import org.micromanager.data.NewSummaryMetadataEvent;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.NewImagePlusEvent;

import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.internal.graph.GraphData;
import org.micromanager.internal.graph.HistogramPanel;
import org.micromanager.internal.graph.HistogramPanel.CursorListener;
import org.micromanager.internal.MMStudio;

import org.micromanager.display.internal.events.DefaultRequestToDrawEvent;
import org.micromanager.display.internal.events.LUTUpdateEvent;
import org.micromanager.display.internal.link.ContrastEvent;
import org.micromanager.display.internal.link.ContrastLinker;
import org.micromanager.display.internal.link.DisplayGroupManager;
import org.micromanager.display.internal.link.LinkButton;
import org.micromanager.display.internal.ChannelSettings;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.internal.DisplayDestroyedEvent;
import org.micromanager.display.internal.MMCompositeImage;
import org.micromanager.display.internal.MMVirtualStack;

import org.micromanager.internal.utils.HistogramUtils;
import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Handles controls for a single histogram.
 */
public class ChannelControlPanel extends JPanel implements CursorListener {

   public static final Dimension CONTROLS_SIZE = new Dimension(80, 80);
   
   private static final int NUM_BINS = 256;
   private final int channelIndex_;
   private HistogramPanel histogram_;
   private final HistogramsPanel parent_;
   private final Datastore store_;
   private DisplayWindow display_;
   private final MMVirtualStack stack_;
   private ImagePlus plus_;
   private CompositeImage composite_;

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

   private double binSize_;
   private String histMaxLabel_;
   private int histMax_;
   private int contrastMin_ = -1;
   private int contrastMax_ = -1;
   private double gamma_ = 1;
   private int minAfterRejectingOutliers_;
   private int maxAfterRejectingOutliers_;
   private int pixelMin_ = 0;
   private int pixelMax_ = 255;
   private int pixelMean_ = 0;
   private int maxIntensity_;
   private int bitDepth_;
   private Color color_;
   private String name_;
   private final AtomicBoolean haveInitialized_;

   public ChannelControlPanel(int channelIndex, HistogramsPanel parent,
         Datastore store, DisplayWindow display, MMVirtualStack stack,
         ImagePlus plus) {
      haveInitialized_ = new AtomicBoolean(false);
      channelIndex_ = channelIndex;
      parent_ = parent;
      store_ = store;
      display_ = display;
      stack_ = stack;
      setImagePlus(plus);

      // Default to a generic name based on our channel index.
      name_ = String.format("channel %d", channelIndex_);
      String[] allNames = store_.getSummaryMetadata().getChannelNames();
      if (allNames != null && allNames.length > channelIndex_) {
         name_ = allNames[channelIndex_];
      }

      // Must be registered for events before we start modifying images, since
      // that relies on LUTUpdateEvent.
      store.registerForEvents(this);
      display.registerForEvents(this);
      // This won't be available until there's at least one image in the 
      // Datastore for our channel.
      bitDepth_ = -1;
      List<Image> images = store_.getImagesMatching(
            new DefaultCoords.Builder().channel(channelIndex_).build()
      );
      if (images != null && images.size() > 0) {
         // Found an image for our channel
         bitDepth_ = images.get(0).getMetadata().getBitDepth();
         initialize();
      }
   }

   private void setImagePlus(ImagePlus plus) {
      plus_ = plus;

      // We may be for a single-channel system or a multi-channel one; the two
      // require different backing objects (ImagePlus vs. CompositeImage).
      // Hence why we have both the plus_ and composite_ objects, and use the
      // appropriate one depending on context.
      if (plus_ instanceof CompositeImage) {
         composite_ = (CompositeImage) plus_;
      }
   }

   private void initialize() {
      maxIntensity_ = (int) Math.pow(2, bitDepth_) - 1;
      histMax_ = maxIntensity_ + 1;
      binSize_ = histMax_ / NUM_BINS;
      histMaxLabel_ = "" + histMax_;
      initComponents();
      reloadDisplaySettings();
      // HACK: Default to "camera depth" mode; only do the below math if it'll
      // get us a value greater than 0.
      int index = 0;
      if (contrastMax_ > 8) {
         index = (int) (Math.ceil(Math.log(contrastMax_) / Math.log(2)) - 3);
      }
      histRangeComboBox_.setSelectedIndex(index);

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
               "/org/micromanager/internal/icons/eye.png")));
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

      colorPickerLabel_.setBackground(color_);
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
      minMaxLabel_.setText("Min/Max/Mean:<br>00/00/00");

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
               "/org/micromanager/internal/icons/triangle_left.png"));
      zoomInButton_.setMinimumSize(new Dimension(16, 16));
      zoomInButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            zoomInAction();
         }
      });
      
      zoomOutButton_ = new JButton(IconLoader.getIcon(
               "/org/micromanager/internal/icons/triangle_right.png"));
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
      nameLabel_ = new JLabel(name_);
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
   
   public int getDisplayComboIndex() {
      return histRangeComboBox_.getSelectedIndex();
   }
   
   /**
    * Do a logarithmic (powers of 2) zoom, which in turn updates our displayed
    * bit depth.
    */
   private void zoomInAction() {
      int selected = histRangeComboBox_.getSelectedIndex();
      if (selected == 0) {
         selected = bitDepth_ - 3;
      }
      if (selected != 1) {
         selected--;
      }
      histRangeComboBox_.setSelectedIndex(selected);
   }
   
   private void zoomOutAction() {
      int selected = histRangeComboBox_.getSelectedIndex();
      if (selected == 0) {
         selected = bitDepth_ - 3;
      }
      if (selected < histRangeComboBox_.getModel().getSize() - 1) {
         selected++;
      }
      histRangeComboBox_.setSelectedIndex(selected);  
   }
   
   public void displayComboAction() {
      int index = histRangeComboBox_.getSelectedIndex();
      // Update the display settings.
      DisplaySettings settings = display_.getDisplaySettings();
      Integer[] curIndices = settings.getBitDepthIndices();
      if (curIndices == null || curIndices.length <= channelIndex_) {
         // Expand the array to contain our value.
         Integer[] indices = new Integer[channelIndex_ + 1];
         for (int i = 0; i < indices.length; ++i) {
            indices[i] = (curIndices != null && curIndices.length > i) ? curIndices[i] : 0;
         }
         curIndices = indices;
      }
      curIndices[channelIndex_] = index;
      settings = settings.copy().bitDepthIndices(curIndices).build();
      display_.setDisplaySettings(settings);

      // Update the histogram display.
      histMax_ = (int) (Math.pow(2, index + 3) - 1);
      if (index == 0) {
         // User selected the "Camera depth" option.
         histMax_ = maxIntensity_;
      }
      binSize_ = ((double) (histMax_ + 1)) / ((double) NUM_BINS);
      histMaxLabel_ = histMax_ + "";
      updateHistogram();
      calcAndDisplayHistAndStats(true);
   }

   private void updateHistogram() {
      if (histogram_ == null) {
         // Don't actually have a histogram yet. This can happen in weird
         // multi-channel situations.
         return;
      }
      histogram_.setCursorText(contrastMin_ + "", contrastMax_ + "");
      histogram_.setCursors(contrastMin_ / binSize_, (contrastMax_+1) / binSize_, gamma_);
      histogram_.repaint();
   }

   private void fullButtonAction() {
      DisplaySettings settings = display_.getDisplaySettings();
      if (settings.getShouldSyncChannels() != null &&
            settings.getShouldSyncChannels()) {
         parent_.fullScaleChannels();
      } else {
         setFullScale();
         applyLUT(true);
      }
      disableAutostretch();
   }

   public void autoButtonAction() {
      autostretch();
      applyLUT(true);
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
      Color defaultColor = color_;
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
               if (i < channelColors.length) {
                  newColors[i] = channelColors[i];
               }
               else {
                  newColors[i] = Color.WHITE;
               }
            }
         }
         newColors[channelIndex_] = newColor;
         DisplaySettings newSettings = settings.copy().channelColors(newColors).build();
         // This will update our color_ field via onNewDisplaySettings().
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
      if (composite_ == null) {
         // Not multi-channel; ignore.
         return;
      }
      // These icons are adapted from the public-domain icon at
      // https://openclipart.org/detail/182888/eye-icon
      isEnabledButton_.setIcon(isEnabledButton_.isSelected() ?
            new ImageIcon(getClass().getResource(
                  "/org/micromanager/internal/icons/eye.png")) :
            new ImageIcon(getClass().getResource(
                  "/org/micromanager/internal/icons/eye-out.png")));
      boolean[] active = composite_.getActiveChannels();
      if (composite_.getMode() != CompositeImage.COMPOSITE) {
         if (active[channelIndex_]) {
            isEnabledButton_.setSelected(true);
            return;
         } else {
            // Change which channel the stack is pointing at.
            stack_.setCoords(stack_.getCurrentImageCoords().copy().channel(channelIndex_).build());
            composite_.updateAndDraw();
         }
      }

      composite_.getActiveChannels()[channelIndex_] = isEnabledButton_.isSelected();
      composite_.updateAndDraw();
   }

   public void setFullScale() {
      int origMin = contrastMin_;
      int origMax = contrastMax_;
      contrastMin_ = 0;
      contrastMax_ = histMax_;
      // Only send an event if we actually changed anything; otherwise we get
      // into an infinite loop.
      if (origMin != contrastMin_ || origMax != contrastMax_) {
         postNewSettings();
      }
   }

   public void autostretch() {
      int origMin = contrastMin_;
      int origMax = contrastMax_;

      contrastMin_ = pixelMin_;
      contrastMax_ = pixelMax_;
      if (pixelMin_ == pixelMax_) {
          if (pixelMax_ > 0) {
              contrastMin_--;
          } else {
              contrastMax_++;
          }
      }
      contrastMin_ = Math.max(0,
            Math.max(contrastMin_, minAfterRejectingOutliers_));
      contrastMax_ = Math.min(contrastMax_, maxAfterRejectingOutliers_);
      if (contrastMax_ <= contrastMin_) {
          if (contrastMax_ > 0) {
              contrastMin_ = contrastMax_ - 1;
          } else {
              contrastMax_ = contrastMin_ + 1;
          }
      }
      // Only send an event if we actually changed anything; otherwise we get
      // into an infinite loop.
      if (origMin != contrastMin_ || origMax != contrastMax_) {
         postNewSettings();
      }
   }

   private HistogramPanel makeHistogramPanel() {
      HistogramPanel hp = new HistogramPanel() {
         @Override
         public void paint(Graphics g) {
            super.paint(g);
            //For drawing max label
            g.setColor(Color.black);
            g.setFont(new Font("Lucida Grande", 0, 10));
            g.drawString(histMaxLabel_, this.getSize().width - 8 * histMaxLabel_.length(), this.getSize().height);
         }
      };

      hp.setMargins(12, 12);
      hp.setTraceStyle(true, color_);
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
      DisplaySettings settings = display_.getDisplaySettings();
      Integer[] bitDepthIndices = settings.getBitDepthIndices();
      if (bitDepthIndices != null && bitDepthIndices.length > channelIndex_ &&
            bitDepthIndices[channelIndex_] != histRangeComboBox_.getSelectedIndex()) {
         histRangeComboBox_.setSelectedIndex(bitDepthIndices[channelIndex_]);
      }
      // HACK: use a color based on the channel index: specifically, use the
      // colorblind-friendly color set.
      Color defaultColor = Color.WHITE;
      if (channelIndex_ < HistogramsPanel.COLORBLIND_COLORS.length) {
         defaultColor = HistogramsPanel.COLORBLIND_COLORS[channelIndex_];
      }
      ChannelSettings channelSettings = ChannelSettings.loadSettings(
            name_, store_.getSummaryMetadata().getChannelGroup(),
            defaultColor, contrastMin_, contrastMax_, true);

      contrastMin_ = channelSettings.getHistogramMin();
      Integer[] mins = settings.getChannelContrastMins();
      if (mins != null && mins.length > channelIndex_ &&
            mins[channelIndex_] != null) {
         contrastMin_ = mins[channelIndex_];
      }

      contrastMax_ = channelSettings.getHistogramMax();
      Integer[] maxes = settings.getChannelContrastMaxes();
      if (maxes != null && maxes.length > channelIndex_ &&
            maxes[channelIndex_] != null) {
         contrastMax_ = maxes[channelIndex_];
      }

      // TODO: gamma not stored in channel settings.
      Double[] gammas = settings.getChannelGammas();
      if (gammas != null && gammas.length > channelIndex_ &&
            gammas[channelIndex_] != null) {
         gamma_ = gammas[channelIndex_];
      }

      // TODO: no autoscale checkbox for individual channels, so can't apply
      // the autoscale property of ChannelSettings.

      if (store_.getAxisLength(Coords.CHANNEL) <= 1) {
         // Default to white.
         color_ = Color.WHITE;
      }
      else {
         // Use the ChannelSettings value (which incorporates the hardcoded
         // default when no color is set), or override with DisplaySettings
         // if available.
         color_ = channelSettings.getColor();
         Color[] colors = settings.getChannelColors();
         if (colors != null && colors.length > channelIndex_ &&
               colors[channelIndex_] != null) {
            color_ = colors[channelIndex_];
         }
      }

      colorPickerLabel_.setBackground(color_);
      histogram_.setTraceStyle(true, color_);

      if (contrastMin_ == -1 || contrastMax_ == -1) {
         // Invalid settings; we'll have to autoscale.
         autostretch();
      }

      // Eye buttons are only enabled when in composite mode.
      if (settings.getChannelDisplayModeIndex() != null) {
         isEnabledButton_.setEnabled(
               settings.getChannelDisplayModeIndex() == HistogramsPanel.COMPOSITE);
      }

      saveChannelSettings();
      updateHistogram();
      calcAndDisplayHistAndStats(true);
      applyLUT(true);
   }

   /**
    * Save our current settings into the profile, so they'll be used by default
    * by future displays for our channel name/group.
    */
   private void saveChannelSettings() {
      // HACK: because we override colors to white for singlechannel displays,
      // we need to try to "recover" the actual color from the profile or
      // display settings here; otherwise the first channel in every new
      // acquisition will always be white.
      Color color = color_;
      if (display_.getDatastore().getAxisLength(Coords.CHANNEL) == 1) {
         ChannelSettings channelSettings = ChannelSettings.loadSettings(
               name_, store_.getSummaryMetadata().getChannelGroup(),
               null, -1, -1, true);
         color = channelSettings.getColor();
         if (color == null) {
            Color[] colors = display_.getDisplaySettings().getChannelColors();
            if (colors != null && colors.length > channelIndex_) {
               color = colors[channelIndex_];
            }
         }
      }
      if (color == null) {
         color = Color.WHITE;
      }
      // TODO: no per-channel autoscale controls, so defaulting to true here.
      ChannelSettings settings = new ChannelSettings(name_,
            store_.getSummaryMetadata().getChannelGroup(),
            color, contrastMin_, contrastMax_, true);
      settings.saveToProfile();
   }

   public int getContrastMin() {
      return contrastMin_;
   }

   public int getContrastMax() {
      return contrastMax_;
   }

   public double getContrastGamma() {
      return gamma_;
   }

   public void setContrast(int min, int max, double gamma) {
      contrastMin_ = min;
      contrastMax_ = Math.min(maxIntensity_, max);
      gamma_ = gamma;
   }

   @Subscribe
   public void onLUTUpdate(LUTUpdateEvent event) {
      try {
         if (event.getMin() != null) {
            contrastMin_ = event.getMin();
         }
         if (event.getMax() != null) {
            contrastMax_ = event.getMax();
         }
         if (event.getGamma() != null) {
            gamma_ = event.getGamma();
         }
         if (color_ == null) {
            // Can't do anything about this yet.
            return;
         }
         // Need to put this on EDT to avoid index out of bounds because of
         // setting currentChannel to -1
         Runnable run = new Runnable() {
            @Override
            public void run() {
               LUT lut = ImageUtils.makeLUT(color_, gamma_);
               lut.min = contrastMin_;
               lut.max = contrastMax_;
               if (composite_ == null) {
                  // Single-channel case is straightforward.
                  plus_.getProcessor().setColorModel(lut);
                  plus_.getProcessor().setMinAndMax(lut.min, lut.max);
               }
               else {
                  // uses lut.min and lut.max to set min and max of processor
                  composite_.setChannelLut(lut, channelIndex_ + 1);

                  if (composite_.getMode() == CompositeImage.COLOR ||
                        composite_.getMode() == CompositeImage.GRAYSCALE) {
                     // ImageJ workaround: do this so the appropriate color
                     // model gets applied in color or grayscale mode.
                     // Otherwise we can end up with erroneously grayscale
                     // images.
                     try {
                        JavaUtils.setRestrictedFieldValue(composite_, 
                              CompositeImage.class, "currentChannel", -1);
                     } catch (NoSuchFieldException ex) {
                        ReportingUtils.logError(ex);
                     }
                  }

                  if (composite_.getChannel() == channelIndex_ + 1) {
                     LUT grayLut = ImageUtils.makeLUT(Color.white, gamma_);
                     ImageProcessor processor = composite_.getProcessor();
                     if (processor != null) {
                        processor.setColorModel(grayLut);
                        processor.setMinAndMax(contrastMin_, contrastMax_);
                     }
                     if (composite_.getMode() == CompositeImage.GRAYSCALE) {
                        composite_.updateImage();
                     }
                  }
               } // End multi-channel case.
               updateHistogram();
            }
         };
         if (SwingUtilities.isEventDispatchThread()) {
            run.run();
         } else {
            SwingUtilities.invokeLater(run);
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error updating LUT");
      }
   }

   public int getChannelIndex() {
      return channelIndex_;
   }

   /**
    * @param shouldDrawHistogram - set true if hist and stats calculated
    * successfully.
    */
   public void calcAndDisplayHistAndStats(boolean shouldDrawHistogram) {
      if (plus_ == null || plus_.getProcessor() == null) {
         // No image to work with.
         return;
      }
      if (histogram_ == null) {
         // No histogram to control.
         return;
      }
      ImageProcessor processor;
      if (composite_ == null) {
         // Single-channel mode.
         processor = plus_.getProcessor();
      }
      else {
         // Multi-channel mode.
         if (composite_.getMode() == CompositeImage.COMPOSITE) {
            processor = composite_.getProcessor(channelIndex_ + 1);
            if (processor != null) {
               processor.setRoi(composite_.getRoi());
            }
         } else {
            MMCompositeImage ci = (MMCompositeImage) composite_;
            int flatIndex = 1 + channelIndex_ + 
                  (composite_.getSlice() - 1) * ci.getNChannelsUnverified() +
                  (composite_.getFrame() - 1) * ci.getNSlicesUnverified() * ci.getNChannelsUnverified();
            processor = composite_.getStack().getProcessor(flatIndex);
         }
      }
      if (processor == null ) {
         // Tried to get an image that doesn't exist.
         return;
      }

      if (composite_ != null) {
         if (((MMCompositeImage) composite_).getNChannelsUnverified() <= 7) {
            boolean active = composite_.getActiveChannels()[channelIndex_];
            isEnabledButton_.setSelected(active);
            if (!active) {
               shouldDrawHistogram = false;
            }
         }
         if (((MMCompositeImage) composite_).getMode() != CompositeImage.COMPOSITE &&
               composite_.getChannel() - 1 != channelIndex_) {
            shouldDrawHistogram = false;
         }
      }

      int[] rawHistogram = processor.getHistogram();
      int imgWidth = plus_.getWidth();
      int imgHeight = plus_.getHeight();

      if (rawHistogram[0] == imgWidth * imgHeight) {
         // Image data is invalid/blank.
         return;
      }

      // Autostretch, if necessary.
      DisplaySettings settings = display_.getDisplaySettings();
      if (settings.getShouldAutostretch() != null &&
            settings.getShouldAutostretch()) {
         autostretch();
         applyLUT(false);
      }

      // Determine what percentage of the histogram range to autotrim.
      maxAfterRejectingOutliers_ = rawHistogram.length;
      int totalPoints = imgHeight * imgWidth;
      Double extremaPercentage = settings.getExtremaPercentage();
      if (extremaPercentage == null) {
         extremaPercentage = 0.0;
      }
      HistogramUtils hu = new HistogramUtils(rawHistogram, totalPoints, 
            0.01 * extremaPercentage);
      minAfterRejectingOutliers_ = hu.getMinAfterRejectingOutliers();
      maxAfterRejectingOutliers_ = hu.getMaxAfterRejectingOutliers();

      GraphData histogramData = new GraphData();

      pixelMin_ = -1;
      pixelMax_ = 0;
      pixelMean_ = (int) plus_.getStatistics(ImageStatistics.MEAN).mean;

      int numBins = (int) Math.min(rawHistogram.length / binSize_, NUM_BINS);
      int[] histogram = new int[NUM_BINS];
      int total = 0;
      for (int i = 0; i < numBins; i++) {
         histogram[i] = 0;
         for (int j = 0; j < binSize_; j++) {
            int rawHistIndex = (int) (i * binSize_ + j);
            int rawHistVal = rawHistogram[rawHistIndex];
            histogram[i] += rawHistVal;
            if (rawHistVal > 0) {
               pixelMax_ = rawHistIndex;
               if (pixelMin_ == -1) {
                  pixelMin_ = rawHistIndex;
               }
            }
         }
         total += histogram[i];
         if (settings.getShouldUseLogScale() != null &&
               settings.getShouldUseLogScale()) {
            histogram[i] = histogram[i] > 0 ? (int) (1000 * Math.log(histogram[i])) : 0;
         }
      }
      //Make sure max has correct value is hist display mode isnt auto
      if (histRangeComboBox_.getSelectedIndex() != -1) {
         pixelMin_ = rawHistogram.length-1;
         for (int i = rawHistogram.length-1; i > 0; i--) {
            if (rawHistogram[i] > 0 && i > pixelMax_ ) {
               pixelMax_ = i;
            }
            if (rawHistogram[i] > 0 && i < pixelMin_ ) {
               pixelMin_ = i;
            }
         }
      }

      // work around what is apparently a bug in ImageJ
      if (total == 0) {
         if (plus_.getProcessor().getMin() == 0) {
            histogram[0] = imgWidth * imgHeight;
         } else {
            histogram[numBins - 1] = imgWidth * imgHeight;
         }
      }
      
      if (shouldDrawHistogram) {
         histogram_.setVisible(true);
         //Draw histogram and stats
         histogramData.setData(histogram);
         histogram_.setData(histogramData);
         histogram_.setAutoScale();
         histogram_.repaint();

         minMaxLabel_.setText(String.format("<html>Min/Max/Mean:<br>%d/%d/%d</html>", pixelMin_, pixelMax_, pixelMean_));
      } else {
          histogram_.setVisible(false);
      }
   }
   
   @Override
   public void contrastMaxInput(int max) {
      disableAutostretch();
      contrastMax_ = max;
      if (contrastMax_ > maxIntensity_ ) {
         contrastMax_ = maxIntensity_;
      }
      if (contrastMax_ < 0) {
         contrastMax_ = 0;
      }
      if (contrastMin_ > contrastMax_) {
         contrastMin_ = contrastMax_;
      }
      applyLUT(true);
      postNewSettings();
   }
   
   @Override
   public void contrastMinInput(int min) {    
      disableAutostretch();
      contrastMin_ = min;
      if (contrastMin_ >= maxIntensity_)
          contrastMin_ = maxIntensity_ - 1;
      if(contrastMin_ < 0 ) {
         contrastMin_ = 0;
      }
      if (contrastMax_ < contrastMin_) {
         contrastMax_ = contrastMin_ + 1;
      }
      applyLUT(true);
      postNewSettings();
   }

   @Override
   public void onLeftCursor(double pos) {
      disableAutostretch();
      contrastMin_ = (int) (Math.max(0, pos) * binSize_);
      if (contrastMin_ >= maxIntensity_)
          contrastMin_ = maxIntensity_ - 1;
      if (contrastMax_ < contrastMin_) {
         contrastMax_ = contrastMin_ + 1;
      }
      applyLUT(true);
      postNewSettings();
   }

   @Override
   public void onRightCursor(double pos) {
      disableAutostretch();
      contrastMax_ = (int) (Math.min(NUM_BINS - 1, pos) * binSize_);
      if (contrastMax_ < 1) {
         contrastMax_ = 1;
      }
      if (contrastMin_ > contrastMax_) {
         contrastMin_ = contrastMax_;
      }
      applyLUT(true);
      postNewSettings();
   }

   @Override
   public void onGammaCurve(double gamma) {
      if (gamma != 0) {
         if (gamma > 0.9 & gamma < 1.1) {
            gamma_ = 1;
         } else {
            gamma_ = gamma;
         }
         applyLUT(true);
         postNewSettings();
      }
   }

   /**
    * Update our parent's DisplaySettings, post a ContrastEvent, and save
    * our new settings to the profile (via the ChannelSettings).
    */
   private void postNewSettings() {
      if (!haveInitialized_.get()) {
         // Don't send out anything until after we're done initializing, since
         // our settings are probably in an inconsistent state right now.
         return;
      }
      DisplaySettings settings = display_.getDisplaySettings();
      DisplaySettings.DisplaySettingsBuilder builder = settings.copy();

      Object[] channelSettings = DefaultDisplaySettings.getPerChannelArrays(settings);
      // TODO: ordering of these values is closely tied to the above function!
      Object[] ourParams = new Object[] {color_, contrastMin_, contrastMax_,
         gamma_};
      // For each of the above parameters, ensure that there's an array in
      // the display settings that's at least long enough to hold our channel,
      // and that our values are represented in that array.
      for (int i = 0; i < channelSettings.length; ++i) {
         Object[] oldVals = DefaultDisplaySettings.makePerChannelArray(i,
               (Object[]) channelSettings[i], channelIndex_ + 1);
         // HACK: for the specific case of a single-channel setup, our nominal
         // color is white -- but we should not force this into the
         // DisplaySettings as our color should change if a new channel is
         // added.
         if (!(oldVals instanceof Color[]) ||
               store_.getAxisLength("channel") > 1) {
            oldVals[channelIndex_] = ourParams[i];
         }
         DefaultDisplaySettings.updateChannelArray(i, oldVals, builder);
      }
      settings = builder.build();
      display_.setDisplaySettings(settings);
      saveChannelSettings();
      postContrastEvent(settings);
   }

   /**
    * We provide the boolean mostly so that we don't get into cyclic draw
    * events when our drawing code calls this method.
    */
   private void applyLUT(boolean shouldRedisplay) {
      DisplaySettings settings = display_.getDisplaySettings();
      if (settings.getShouldSyncChannels() != null &&
            settings.getShouldSyncChannels()) {
         display_.postEvent(
               new LUTUpdateEvent(contrastMin_, contrastMax_, gamma_));
      } else {
         display_.postEvent(new LUTUpdateEvent(null, null, null));
      }
      if (shouldRedisplay) {
         display_.postEvent(new DefaultRequestToDrawEvent());
      }
   }

   private void disableAutostretch() {
      DisplaySettings settings = display_.getDisplaySettings();
      if (settings.getShouldAutostretch() != null &&
            settings.getShouldAutostretch()) {
         display_.setDisplaySettings(display_.getDisplaySettings().copy().shouldAutostretch(false).build());
      }
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

   @Subscribe
   public void onNewImagePlus(NewImagePlusEvent event) {
      setImagePlus(event.getImagePlus());
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
         name_ = names[channelIndex_];
         nameLabel_.setText(name_);
      }
   }

   /**
    * A new image has arrived; if it's for our channel and we haven't set bit
    * depth yet, then do so now. And if we're the first channel, our color is
    * white (i.e. the default color), and an image for another channel arrives,
    * then we need to re-load our color.
    * @param event
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      try {
         int channel = event.getCoords().getChannel();
         if (bitDepth_ == -1 && channel == channelIndex_) {
            bitDepth_ = event.getImage().getMetadata().getBitDepth();
            initialize();
         }
         if (channelIndex_ == 0 && channel != channelIndex_ &&
               color_.equals(Color.WHITE)) {
            reloadDisplaySettings();
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error handling new image in histogram for channel " + channelIndex_);
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
      store_.unregisterForEvents(this);
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
}
