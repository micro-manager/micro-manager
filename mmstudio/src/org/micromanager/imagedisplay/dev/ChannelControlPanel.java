package org.micromanager.imagedisplay.dev;

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
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import mmcorej.CMMCore;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DatastoreLockedException;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.NewDisplaySettingsEvent;
import org.micromanager.api.data.NewImageEvent;

import org.micromanager.data.DefaultCoords;
import org.micromanager.dialogs.AcqControlDlg;
import org.micromanager.graph.GraphData;
import org.micromanager.graph.HistogramPanel;
import org.micromanager.graph.HistogramPanel.CursorListener;
import org.micromanager.MMStudio;

import org.micromanager.imagedisplay.MMCompositeImage;

import org.micromanager.utils.HistogramUtils;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

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
   private MMVirtualStack stack_;
   private DisplaySettings settings_;
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

   public ChannelControlPanel(int channelIndex, HistogramsPanel parent,
         Datastore store, MMVirtualStack stack,
         ImagePlus plus, EventBus displayBus) {
      channelIndex_ = channelIndex;
      parent_ = parent;
      store_ = store;
      stack_ = stack;
      settings_ = store.getDisplaySettings();
      plus_ = plus;
      // We may be for a single-channel system or a multi-channel one; the two
      // require different backing objects (ImagePlus vs. CompositeImage).
      // Hence why we have both the plus_ and composite_ objects, and use the
      // appropriate one depending on context.
      if (plus_ instanceof CompositeImage) {
         composite_ = (CompositeImage) plus_;
      }
      displayBus_ = displayBus;
      // Default to white; select a better color if available.
      color_ = getColorFromSettings();

      name_ = String.format("channel %d", channelIndex_);
      String[] allNames = settings_.getChannelNames();
      if (allNames != null && allNames.length > channelIndex_) {
         name_ = allNames[channelIndex_];
      }

      // This won't be available until there's at least one image in the 
      // Datastore for our channel.
      bitDepth_ = -1;
      List<Image> images = store_.getImagesMatching(
            new DefaultCoords.Builder().position("channel", channelIndex_).build()
      );
      if (images != null && images.size() > 0) {
         // Found an image for our channel
         bitDepth_ = images.get(0).getMetadata().getBitDepth();
         initialize();
      }
      store.registerForEvents(this, 100);
   }

   private void initialize() {
      maxIntensity_ = (int) Math.pow(2, bitDepth_) - 1;
      histMax_ = maxIntensity_ + 1;
      binSize_ = histMax_ / NUM_BINS;
      histMaxLabel_ = "" + histMax_;
      initComponents();
      loadDisplaySettings();
      updateHistogram();
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
            if (settings_.getShouldSyncChannels() != null && 
                  settings_.getShouldSyncChannels()) {
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
            "/org/micromanager/icons/zoom_in.png"));
      zoomInButton_.setMinimumSize(new Dimension(20, 20));
      zoomInButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            zoomInAction();
         }
      });
      
      zoomOutButton_ = new JButton();
      zoomOutButton_.setIcon(SwingResourceManager.getIcon(MMStudio.class,
            "/org/micromanager/icons/zoom_out.png"));   
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
      storeDisplaySettings();
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
      if (settings_.getShouldSyncChannels() != null &&
            settings_.getShouldSyncChannels()) {
         parent_.fullScaleChannels();
      } else {
         setFullScale();
         parent_.applyLUTToImage();
         repaint();
      }
   }

   public void autoButtonAction() {
      autostretch();
      applyLUT(true);
   }

   private void colorPickerLabelMouseClicked() {
      //Can only edit color in this way if there is an active window
      //so it is ok to get image cache in this way
      String name = "selected";
      String[] channelNames = settings_.getChannelNames();
      if (channelNames != null && channelNames.length > channelIndex_) {
         name = channelNames[channelIndex_];
      }
      Color[] channelColors = settings_.getChannelColors();
      Color newColor = JColorChooser.showDialog(this, "Choose a color for the "
              + name + " channel", channelColors[channelIndex_]);
      if (newColor != null) {
         // Update the display settings.
         channelColors[channelIndex_] = newColor;
         try {
            store_.setDisplaySettings(
                  settings_.copy().channelColors(channelColors).build());
         }
         catch (DatastoreLockedException e) {
            ReportingUtils.logError("Can't set new colors because the datastore is locked.");
         }
      }
      updateChannelNameAndColorFromCache();

      //if multicamera, save color
      if (newColor != null) {
         saveColorPreference(newColor.getRGB());
      }
   }

   /*
    * save color to preferences, but only if this is multicamera.  Since
    * we cannot check for this directly from metadata,this performs a series of
    * checks to error on the side of not saving the preference
    */
   private void saveColorPreference(int color) {
      CMMCore core = MMStudio.getInstance().getCore();
      if (core == null) {
         return;
      }
      int numMultiCamChannels = (int) core.getNumberOfCameraChannels();
      int numDataChannels;
      String[] dataNames;
      String[] cameraNames = new String[numMultiCamChannels];
      try {
         numDataChannels = store_.getAxisLength("channel");
         dataNames = settings_.getChannelNames();
         for (int i = 0; i < numMultiCamChannels; i++) {
            cameraNames[i] = core.getCameraChannelName(i);
         }

         if (numMultiCamChannels > 1 && numDataChannels == numMultiCamChannels
                 && cameraNames.length == dataNames.length) {
            for (int i = 0; i < cameraNames.length; i++) {
               if (!cameraNames[i].equals(dataNames[i])) {
                  return;
               }
            }
            Preferences root = Preferences.userNodeForPackage(AcqControlDlg.class);
            Preferences colorPrefs = root.node(root.absolutePath() + "/" + AcqControlDlg.COLOR_SETTINGS_NODE);
            colorPrefs.putInt("Color_Camera_" + cameraNames[channelIndex_], color);
         }
      } catch (Exception ex) {
         
      }
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
            stack_.setCoords(stack_.getCurrentImageCoords().copy().position("channel", channelIndex_).build());
            composite_.updateAndDraw();
         }
      }

      composite_.getActiveChannels()[channelIndex_] = channelNameCheckbox_.isSelected();
      composite_.updateAndDraw();
   }

   public void setFullScale() {
      contrastMin_ = 0;
      contrastMax_ = histMax_;
   }

   public void autostretch() {
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
   }

   private void loadDisplaySettings() {
      Integer[] contrastMaxes = settings_.getChannelContrastMaxes();
      if (contrastMaxes != null && contrastMaxes.length > channelIndex_) {
         contrastMax_ =  contrastMaxes[channelIndex_];
      }
      if (contrastMax_ < 0 || contrastMax_ > maxIntensity_) {
         contrastMax_ = maxIntensity_;
      }
      Integer[] contrastMins = settings_.getChannelContrastMins();
      if (contrastMins != null && contrastMins.length > channelIndex_) {
         contrastMin_ = contrastMins[channelIndex_];
      }
      Double[] gammas = settings_.getChannelGammas();
      if (gammas != null && gammas.length > channelIndex_) {
         gamma_ = gammas[channelIndex_];
      }
      int histMax = contrastMax_;
      int index = (int) (Math.ceil(Math.log(histMax)/Math.log(2)) - 3);
      histRangeComboBox_.setSelectedIndex(index);
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

   public void updateChannelNameAndColorFromCache() {
      color_ = getColorFromSettings();
      colorPickerLabel_.setBackground(color_);
      histogram_.setTraceStyle(true, color_);
      String[] allNames = settings_.getChannelNames();
      if (allNames != null && allNames.length > channelIndex_) {
         String name = allNames[channelIndex_];
         if (name.length() > 11) {
            name = name.substring(0, 9) + "...";
         }
         channelNameCheckbox_.setText(name);
      }
      calcAndDisplayHistAndStats(true);
      parent_.applyLUTToImage();
      repaint();
   }

   private Color getColorFromSettings() {
      Color result = Color.WHITE;
      Color[] allColors = settings_.getChannelColors();
      // Coerce white when there's only one channel (i.e. ignore the chosen
      // color).
      if (store_.getAxisLength("channel") > 1 &&
            allColors != null && allColors.length > channelIndex_) {
         result = allColors[channelIndex_];
      }
      return result;
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

   public void applyChannelLUTToImage() {
      // Need to put this on EDT to avoid index out of bounds because of
      // setting currentChannel to -1
      Runnable run = new Runnable() {
         @Override
         public void run() {
            if (store_.getIsLocked()) {
               // TODO: why do we not do this when the store is locked?
               ReportingUtils.logError("Store locked; no LUT for us");
               return;
            }

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

               // ImageJ workaround: do this so the appropriate color model and
               // min/max get applied in color or grayscale mode
               try {
                  JavaUtils.setRestrictedFieldValue(composite_, 
                        CompositeImage.class, "currentChannel", -1);
               } catch (NoSuchFieldException ex) {
                  ReportingUtils.logError(ex);
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
            storeDisplaySettings();
            updateHistogram();
         }
      };
      if (SwingUtilities.isEventDispatchThread()) {
         run.run();
      } else {
         SwingUtilities.invokeLater(run);
      }
   }

   // TODO: implement.
   private void storeDisplaySettings() {
//      int histMax = histRangeComboBox_.getSelectedIndex() == 0 ? -1 : histMax_;
//      display_.storeChannelHistogramSettings(channelIndex_, contrastMin_, contrastMax_,
//              gamma_, histMax,((MMCompositeImage) composite_).getMode());
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
         ReportingUtils.logError("Can't draw histogram: no image to work with");
         return;
      }
      if (histogram_ == null) {
         ReportingUtils.logError("Can't draw histogram: no histogram created yet");
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
         ReportingUtils.logError("Can't draw histogram: no processor for " + channelIndex_);
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
         ReportingUtils.logError("Can't draw histogram: no pixels");
         return;  //Blank pixels 
      }

      // Autostretch, if necessary.
      if (settings_.getShouldAutostretch()) {
         autostretch();
         applyLUT(false);
      }

      // Determine what percentage of the histogram range to autotrim.
      maxAfterRejectingOutliers_ = rawHistogram.length;
      int totalPoints = imgHeight * imgWidth;
      Double trimPercentage = settings_.getTrimPercentage();
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
         if (settings_.getShouldUseLogScale() != null && 
               settings_.getShouldUseLogScale()) {
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
      }
   }

   /**
    * We provide the boolean mostly so that we don't get into cyclic draw
    * events when our drawing code calls this method.
    */
   private void applyLUT(boolean shouldRedisplay) {
      // This looks silly, but the function can return null.
      if (settings_.getShouldSyncChannels() == true) {
         parent_.applyContrastToAllChannels(contrastMin_, contrastMax_, gamma_);
      } else {
         parent_.applyLUTToImage();
         repaint();
      }
      if (shouldRedisplay) {
         displayBus_.post(new DefaultRequestToDrawEvent());
      }
   }

   private void disableAutostretch() {
      try {
         store_.setDisplaySettings(store_.getDisplaySettings().copy().shouldAutostretch(false).build());
      }
      catch (DatastoreLockedException e) {
         // Just ignore it.
      }
   }

   /**
    * Display settings have changed; update our color.
    */
   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      settings_ = event.getDisplaySettings();
      updateChannelNameAndColorFromCache();
   }

   /**
    * A new image has arrived; if it's for our channel and we haven't set bit
    * depth yet, then do so now.
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      if (bitDepth_ == -1 && 
            event.getCoords().getPositionAt("channel") == channelIndex_) {
         bitDepth_ = event.getImage().getMetadata().getBitDepth();
         initialize();
      }
   }

   public void cleanup() {
      store_.unregisterForEvents(this);
   }
}
