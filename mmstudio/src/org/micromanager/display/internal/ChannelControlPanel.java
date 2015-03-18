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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.swtdesigner.SwingResourceManager;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.LUT;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
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

import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.internal.graph.GraphData;
import org.micromanager.internal.graph.HistogramPanel;
import org.micromanager.internal.graph.HistogramPanel.CursorListener;
import org.micromanager.internal.MMStudio;

import org.micromanager.display.internal.events.DefaultRequestToDrawEvent;
import org.micromanager.display.internal.events.LUTUpdateEvent;
import org.micromanager.display.internal.link.ContrastEvent;
import org.micromanager.display.internal.link.ContrastLinker;
import org.micromanager.display.internal.link.LinkButton;

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
   private HistogramsPanel parent_;
   private Datastore store_;
   private DisplayWindow display_;
   private MMVirtualStack stack_;
   private ImagePlus plus_;
   private CompositeImage composite_;
   private EventBus displayBus_;

   private JButton autoButton_;
   private JButton zoomInButton_;
   private JButton zoomOutButton_;
   private JCheckBox channelNameCheckbox_;
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
   private AtomicBoolean haveInitialized_;

   public ChannelControlPanel(int channelIndex, HistogramsPanel parent,
         Datastore store, DisplayWindow display, MMVirtualStack stack,
         ImagePlus plus, EventBus displayBus) {
      haveInitialized_ = new AtomicBoolean(false);
      channelIndex_ = channelIndex;
      parent_ = parent;
      store_ = store;
      display_ = display;
      stack_ = stack;
      plus_ = plus;

      // We may be for a single-channel system or a multi-channel one; the two
      // require different backing objects (ImagePlus vs. CompositeImage).
      // Hence why we have both the plus_ and composite_ objects, and use the
      // appropriate one depending on context.
      if (plus_ instanceof CompositeImage) {
         composite_ = (CompositeImage) plus_;
      }
      displayBus_ = displayBus;

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
      fullButton_ = new javax.swing.JButton();
      autoButton_ = new javax.swing.JButton();
      colorPickerLabel_ = new javax.swing.JLabel();
      channelNameCheckbox_ = new javax.swing.JCheckBox();
      minMaxLabel_ = new javax.swing.JLabel();

      setOpaque(false);

      fullButton_.setFont(fullButton_.getFont().deriveFont((float) 9));
      fullButton_.setName("Full channel histogram width");
      fullButton_.setText("Full");
      fullButton_.setToolTipText("Stretch the display gamma curve over the full pixel range");
      fullButton_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            fullButtonAction();
         }
      });

      autoButton_.setFont(autoButton_.getFont().deriveFont((float) 9));
      autoButton_.setName("Auto channel histogram width");
      autoButton_.setText("Auto");
      autoButton_.setToolTipText("Align the display gamma curve with minimum and maximum measured intensity values");
      autoButton_.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
      autoButton_.setIconTextGap(0);
      autoButton_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            autoButtonAction();
         }
      });

      colorPickerLabel_.setBackground(color_);
      colorPickerLabel_.setToolTipText("Change the color for displaying this channel");
      colorPickerLabel_.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
      colorPickerLabel_.setOpaque(true);
      colorPickerLabel_.addMouseListener(new java.awt.event.MouseAdapter() {
         @Override
         public void mouseClicked(java.awt.event.MouseEvent evt) {
            colorPickerLabelMouseClicked();
         }
      });

      channelNameCheckbox_.setText(name_);
      channelNameCheckbox_.setToolTipText("Show/hide this channel in the multi-dimensional viewer");
      channelNameCheckbox_.addActionListener(new java.awt.event.ActionListener() {

         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            channelNameCheckboxAction();
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

      zoomInButton_ = new JButton();
      zoomInButton_.setIcon(SwingResourceManager.getIcon(MMStudio.class,
            "/org/micromanager/internal/icons/zoom_in.png"));
      zoomInButton_.setMinimumSize(new Dimension(20, 20));
      zoomInButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            zoomInAction();
         }
      });
      
      zoomOutButton_ = new JButton();
      zoomOutButton_.setIcon(SwingResourceManager.getIcon(MMStudio.class,
            "/org/micromanager/internal/icons/zoom_out.png"));   
      zoomOutButton_.setMinimumSize(new Dimension(20, 20));
      zoomOutButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            zoomOutAction();
         }
      });

      // No insets on the top/bottom/right, only on the left.
      setLayout(new MigLayout("fill, flowy, insets 0",
               "[]0[]0[]", "[]0[]0[]"));

      JPanel firstRow = new JPanel(new MigLayout("insets 0"));

      firstRow.add(channelNameCheckbox_);

      firstRow.add(colorPickerLabel_);

      fullButton_.setPreferredSize(new Dimension(35, 20));
      linkButton_ = new LinkButton(
            new ContrastLinker(channelIndex_, display_), display_);
      firstRow.add(linkButton_);
      firstRow.add(fullButton_);

      autoButton_.setPreferredSize(new Dimension(35, 20));
      firstRow.add(autoButton_, "wrap");

      add(firstRow);

      histogram_ = makeHistogramPanel();
      histogram_.setMinimumSize(new Dimension(100, 60));
      histogram_.setToolTipText("Adjust the brightness and contrast by dragging triangles at top and bottom. Change the gamma by dragging the curve. (These controls only change display, and do not edit the image data.)");

      add(histogram_, "grow");

      JPanel secondRow = new JPanel(new MigLayout("insets 0"));
      colorPickerLabel_.setMinimumSize(new Dimension(18, 18));

      secondRow.add(zoomInButton_);
      secondRow.add(zoomOutButton_);
      secondRow.add(histRangeComboBox_);
      secondRow.add(minMaxLabel_);

      add(secondRow);

      setPreferredSize(getMinimumSize());
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
      int bits = histRangeComboBox_.getSelectedIndex() + 3;
      if (bits == 3) {
         histMax_ = maxIntensity_;
      } else {
         histMax_ = (int) (Math.pow(2, bits) - 1);
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

   private void colorPickerLabelMouseClicked() {
      String[] channelNames = store_.getSummaryMetadata().getChannelNames();
      String name = "selected";
      if (channelNames != null && channelNames.length > channelIndex_) {
         name = channelNames[channelIndex_];
      }
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
            // Create a new array and fill it in with white.
            // TODO: use differentiated colors instead of white everywhere.
            newColors = new Color[channelIndex_ + 1];
            for (int i = 0; i < newColors.length; ++i) {
               newColors[i] = Color.WHITE;
            }
         }
         else if (newColors.length <= channelIndex_) {
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

   private void channelNameCheckboxAction() {
      if (composite_ == null) {
         // Not multi-channel; ignore.
         return;
      }
      boolean[] active = composite_.getActiveChannels();
      if (composite_.getMode() != CompositeImage.COMPOSITE) {
         if (active[channelIndex_]) {
            channelNameCheckbox_.setSelected(true);
            return;
         } else {
            // Change which channel the stack is pointing at.
            stack_.setCoords(stack_.getCurrentImageCoords().copy().channel(channelIndex_).build());
            composite_.updateAndDraw();
         }
      }

      composite_.getActiveChannels()[channelIndex_] = channelNameCheckbox_.isSelected();
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
    * TODO: for now only loading the color from ChannelSettings; need to extend
    * this to the other values as well.
    */
   public void reloadDisplaySettings() {
      DisplaySettings settings = display_.getDisplaySettings();
      Integer[] mins = settings.getChannelContrastMins();
      if (mins != null && mins.length > channelIndex_ &&
            mins[channelIndex_] != null) {
         contrastMin_ = mins[channelIndex_];
      }
      Integer[] maxes = settings.getChannelContrastMaxes();
      if (maxes != null && maxes.length > channelIndex_ &&
            maxes[channelIndex_] != null) {
         contrastMax_ = maxes[channelIndex_];
      }
      Double[] gammas = settings.getChannelGammas();
      if (gammas != null && gammas.length > channelIndex_ &&
            gammas[channelIndex_] != null) {
         gamma_ = gammas[channelIndex_];
      }

      if (store_.getAxisLength(Coords.CHANNEL) <= 1) {
         // Default to white.
         color_ = Color.WHITE;
      }
      else {
         // Use the ChannelSettings value, or override with DisplaySettings
         // if available.
         ChannelSettings channelSettings = ChannelSettings.loadSettings(
               name_, store_.getSummaryMetadata().getChannelGroup(),
               Color.WHITE, contrastMin_, contrastMax_, true);
         color_ = channelSettings.getColor();
         Color[] colors = settings.getChannelColors();
         if (colors != null && colors.length > channelIndex_ &&
               colors[channelIndex_] != null) {
            color_ = colors[channelIndex_];
         }
      }

      colorPickerLabel_.setBackground(color_);
      histogram_.setTraceStyle(true, color_);

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
      ChannelSettings settings = new ChannelSettings(name_,
            store_.getSummaryMetadata().getChannelGroup(),
            color_, contrastMin_, contrastMax_, autoButton_.isSelected());
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
      if (event.getMin() != null) {
         contrastMin_ = event.getMin();
      }
      if (event.getMax() != null) {
         contrastMax_ = event.getMax();
      }
      if (event.getGamma() != null) {
         gamma_ = event.getGamma();
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
                  // ImageJ workaround: do this so the appropriate color model
                  // gets applied in color or grayscale mode. Otherwise we
                  // can end up with erroneously grayscale images.
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
            channelNameCheckbox_.setSelected(active);
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
      Double trimPercentage = settings.getTrimPercentage();
      if (trimPercentage == null) {
         trimPercentage = 0.0;
      }
      HistogramUtils hu = new HistogramUtils(rawHistogram, totalPoints, 
            0.01 * trimPercentage);
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
    * Update our parent's DisplaySettings, and post a ContrastEvent.
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
      Object[] ourParams = new Object[] {color_,
         new Integer(contrastMin_), new Integer(contrastMax_),
         new Double(gamma_)};
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
         displayBus_.post(new DefaultRequestToDrawEvent());
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
      String[] names = event.getSummaryMetadata().getChannelNames();
      if (names != null && names.length > channelIndex_) {
         name_ = names[channelIndex_];
         channelNameCheckbox_.setText(name_);
      }
   }

   /**
    * A new image has arrived; if it's for our channel and we haven't set bit
    * depth yet, then do so now. And if we're the first channel, our color is
    * white (i.e. the default color), and an image for another channel arrives,
    * then we need to re-load our color.
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
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

   public void cleanup() {
      store_.unregisterForEvents(this);
      display_.unregisterForEvents(this);
      linkButton_.cleanup();
   }
}
