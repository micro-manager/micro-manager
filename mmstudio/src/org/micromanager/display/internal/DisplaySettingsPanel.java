package org.micromanager.display.internal;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.swtdesigner.SwingResourceManager;

import ij.CompositeImage;
import ij.ImagePlus;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreLockedException;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.NewImagePlusEvent;

import org.micromanager.display.internal.events.DefaultRequestToDrawEvent;
import org.micromanager.display.internal.link.ContrastEvent;

import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class provides controls for the general display settings, including
 * some settings that control how the histograms behave. Note this is
 * distinct from the DisplaySettings metadata in the Datastore for the display;
 * some of that is addressed here, and some in the histograms.
 */
public class DisplaySettingsPanel extends JPanel {
   private static String[] COLOR_DESCRIPTORS = new String[] {
      "RGBCMYW", "CMYRGBW", "Custom"};
   private static Color[][] DEFAULT_COLORS = new Color[][] {
      {Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA,
         Color.YELLOW, Color.WHITE},
      {Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.RED, Color.GREEN,
         Color.BLUE, Color.WHITE}
   };

   private Datastore store_;
   private ImagePlus ijImage_;
   private DisplayWindow display_;
   private EventBus displayBus_;
   private JComboBox displayMode_;
   private JComboBox colorPresets_;
   // Index of colorPresets_ last time its position was set.
   private int prevPresetIndex_ = -1;
   private JCheckBox shouldAutostretch_;

   public DisplaySettingsPanel(Datastore store, ImagePlus ijImage,
         DisplayWindow display, EventBus displayBus) {
      super(new MigLayout());

      store_ = store;
      store_.registerForEvents(this);
      ijImage_ = ijImage;
      display_ = display;
      displayBus_ = displayBus;
      displayBus_.register(this);

      DisplaySettings settings = display_.getDisplaySettings();

      // We have several controls that consist of a label and a combobox;
      // we want the label to be left-aligned, then a variable gap, then
      // the right-aligned combobox.
      add(new JLabel("Display mode: "), "split 2, align left, growx");
      displayMode_ = new JComboBox(
            new String[] {"Color", "Grayscale", "Composite"});
      displayMode_.setToolTipText("<html>Set the display mode for the image:<ul><li>Color: single channel, in color<li>Grayscale: single-channel grayscale<li>Composite: multi-channel color overlay</ul></html>");
      displayMode_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setDisplayMode(displayMode_);
         }
      });
      if (settings.getChannelDisplayModeIndex() != null) {
         displayMode_.setSelectedIndex(settings.getChannelDisplayModeIndex());
      }
      displayMode_.setEnabled(ijImage_ instanceof CompositeImage);
      add(displayMode_, "align right, wrap");

      add(new JLabel("Channel colors: "), "split 2, align left, growx");
      colorPresets_ = new JComboBox(COLOR_DESCRIPTORS);
      colorPresets_.setToolTipText("Select a preset color combination for multichannel setups");
      setColorPresetIndex(settings);
      colorPresets_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setColorPreset();
         }
      });
      add(colorPresets_, "align right, wrap");

      add(new JLabel("Histograms update "), "split 2, align left, growx");
      final JComboBox histogramUpdateRate = new JComboBox(
            new String[] {"Never", "Every image", "Once per second"});
      histogramUpdateRate.setToolTipText("Select how frequently to update histograms. Reduced histogram update rate may help reduce CPU load.");
      histogramUpdateRate.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setHistogramUpdateRate(histogramUpdateRate);
         }
      });
      if (settings.getHistogramUpdateRate() != null) {
         double updateRate = settings.getHistogramUpdateRate();
         if (updateRate < 0) {
            histogramUpdateRate.setSelectedIndex(0);
         }
         else if (updateRate == 0) {
            histogramUpdateRate.setSelectedIndex(1);
         }
         else {
            // TODO: this ignores the possibility that the actual update rate
            // will be a value other than once per second.
            histogramUpdateRate.setSelectedIndex(2);
         }
      }
      else {
         // Default to "update every image"
         histogramUpdateRate.setSelectedIndex(1);
      }
      add(histogramUpdateRate, "align right, wrap");
      
      shouldAutostretch_ = new JCheckBox("Autostretch histograms");
      shouldAutostretch_.setToolTipText("Automatically rescale the histograms every time a new image is displayed.");
      shouldAutostretch_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setShouldAutostretch();
         }
      });
      if (settings.getShouldAutostretch() != null) {
         shouldAutostretch_.setSelected(settings.getShouldAutostretch());
      }
      add(shouldAutostretch_, "growx, align right, wrap");

      add(new JLabel("Truncate histograms: "), "split 2, align left, growx");
      final JSpinner trimPercentage = new JSpinner();
      trimPercentage.setToolTipText("When autostretching histograms, the min and max will be moved inwards by the specified percentage (e.g. if this is set to 10, then the scaling will be from the 10th percentile to the 90th).");
      trimPercentage.setModel(new SpinnerNumberModel(0.0, 0.0, 100.0, 1.0));
      trimPercentage.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent event) {
            setTrimPercentage(trimPercentage);
         }
      });
      trimPercentage.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent event) {
            setTrimPercentage(trimPercentage);
         }
      });
      if (settings.getTrimPercentage() != null) {
         trimPercentage.setValue(settings.getTrimPercentage());
      }
      add(trimPercentage, "align right, wrap");

      JButton zoomInButton = new JButton();
      zoomInButton.setIcon(SwingResourceManager.getIcon(
               DisplaySettingsPanel.class,
               "/org/micromanager/internal/icons/zoom_in.png"));
      zoomInButton.setToolTipText("Zoom in");
      zoomInButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            adjustZoom(2.0);
         }
      });
      add(zoomInButton, "split 2");
      JButton zoomOutButton = new JButton();
      zoomOutButton.setIcon(SwingResourceManager.getIcon(
               DisplaySettingsPanel.class,
               "/org/micromanager/internal/icons/zoom_out.png"));
      zoomOutButton.setToolTipText("Zoom out");
      zoomOutButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            adjustZoom(0.5);
         }
      });
      add(zoomOutButton, "wrap, gapright push");

      JButton saveButton = new JButton("Set as default");
      saveButton.setToolTipText("Save the current display settings as default for all new image windows.");
      saveButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            saveAsDefaults();
         }
      });
      add(saveButton, "split 2, flowx, growx, align left");

      JButton dupeButton = new JButton("Duplicate");
      dupeButton.setToolTipText("Create an additional display window for this dataset.");
      dupeButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // TODO: ideally we would copy the custom controls from the
            // existing DefaultDisplayWindow, but that's not available here.
            new DefaultDisplayWindow(store_, null);
         }
      });
      add(dupeButton, "align right, wrap");
   }

   /**
    * The user has interacted with the display mode combo box.
    */
   private void setDisplayMode(JComboBox displayMode) {
      if (!(ijImage_ instanceof CompositeImage)) {
         // Non-composite images are always in grayscale mode.
         displayMode.setSelectedIndex(1);
         return;
      }
      
      CompositeImage composite = (CompositeImage) ijImage_;
      String selection = (String) displayMode.getSelectedItem();
      DisplaySettings.DisplaySettingsBuilder builder = display_.getDisplaySettings().copy();
      if (selection.equals("Composite")) {
         if (store_.getAxisLength("channel") > 7) {
            JOptionPane.showMessageDialog(null,
               "Images with more than 7 channels cannot be displayed in Composite mode.");
            // Send them back to Color mode.
            displayMode.setSelectedIndex(0);
            builder.channelDisplayModeIndex(0);
         }
         else {
            composite.setMode(CompositeImage.COMPOSITE);
            builder.channelDisplayModeIndex(2);
         }
      }
      else if (selection.equals("Color")) {
         composite.setMode(CompositeImage.COLOR);
         builder.channelDisplayModeIndex(0);
      }
      else {
         // Assume grayscale mode.
         composite.setMode(CompositeImage.GRAYSCALE);
         builder.channelDisplayModeIndex(1);
      }
      composite.updateAndDraw();
      display_.setDisplaySettings(builder.build());
      displayBus_.post(new DefaultRequestToDrawEvent());
   }

   /**
    * The user wants to select one of our color presets.
    */
   private void setColorPreset() {
      int i = colorPresets_.getSelectedIndex();
      if (i == 2 || i == prevPresetIndex_) {
         // Ignore the "custom" color scheme, and ignore no-ops.
         return;
      }
      DisplaySettings settings = display_.getDisplaySettings();
      settings = settings.copy().channelColors(DEFAULT_COLORS[i]).build();
      display_.setDisplaySettings(settings);
      prevPresetIndex_ = i;

      // This will end up triggering onNewDisplaySettings, so watch out for
      // potential infinite loops!
      displayBus_.post(new DefaultRequestToDrawEvent());
   }

   /**
    * We care when the channel colors change, and when autostretch is turned
    * on/off.
    */
   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      DisplaySettings settings = event.getDisplaySettings();
      try {
         setColorPresetIndex(settings);
         if (shouldAutostretch_ != null) {
            shouldAutostretch_.setSelected(settings.getShouldAutostretch());
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to handle new display settings");
      }
   }

   /**
    * Examine the current display settings and set the appropriate channel
    * colors selector.
    */
   private void setColorPresetIndex(DisplaySettings settings) {
      if (colorPresets_ == null) {
         // Not done setting up our UI yet.
         return;
      }
      if (settings.getChannelColors() != null) {
         int index = -1;
         for (int i = 0; i < DEFAULT_COLORS.length; ++i) {
            if (Arrays.deepEquals(DEFAULT_COLORS[i], settings.getChannelColors())) {
               index = i;
               break;
            }
         }
         if (index != -1) {
            colorPresets_.setSelectedIndex(index);
         }
         else {
            // Assume user has a custom color scheme.
            colorPresets_.setSelectedIndex(2);
         }
      }
   }

   /** 
    * The user is setting a new update rate for the histograms.
    */
   private void setHistogramUpdateRate(JComboBox histogramUpdateRate) {
      String selection = (String) histogramUpdateRate.getSelectedItem();
      double rate = 0; // i.e. update as often as possible.
      if (selection.equals("Never")) {
         rate = -1;
      }
      else if (selection.equals("Every image")) {
         rate = 0;
      }
      else if (selection.equals("Once per second")) {
         rate = 1;
      }
      DisplaySettings settings = display_.getDisplaySettings();
      settings = settings.copy().histogramUpdateRate(rate).build();
      display_.setDisplaySettings(settings);
   }

   /**
    * The user is toggling autostretch on/off.
    */
   private void setShouldAutostretch() {
      DisplaySettings settings = display_.getDisplaySettings();
      settings = settings.copy().shouldAutostretch(shouldAutostretch_.isSelected()).build();
      display_.setDisplaySettings(settings);
      displayBus_.post(new DefaultRequestToDrawEvent());
      // Autostretch changes apply to all channels.
      displayBus_.post(new ContrastEvent(-1, settings));
   }

   /**
    * The user set a new trim percentage.
    */
   private void setTrimPercentage(JSpinner trimPercentage) {
      DisplaySettings settings = display_.getDisplaySettings();
      double percentage = (Double) trimPercentage.getValue();
      settings = settings.copy().trimPercentage(percentage).build();
      display_.setDisplaySettings(settings);
      displayBus_.post(new DefaultRequestToDrawEvent());
   }

   /**
    * Adjust the magnification of the display by the given factor.
    */
   private void adjustZoom(double factor) {
      display_.setMagnification(display_.getMagnification() * factor);
   }

   /**
    * Save the current display settings as default settings.
    */
   private void saveAsDefaults() {
      DefaultDisplaySettings.setStandardSettings(display_.getDisplaySettings());
   }

   /**
    * This is mostly relevant for our handling of the display mode (grayscale,
    * composite, etc.).
    */
   @Subscribe
   public void onNewImagePlus(NewImagePlusEvent event) {
      try {
         ijImage_ = event.getImagePlus();
         if (ijImage_ instanceof CompositeImage) {
            // Enable the display mode dropdown, and change its value if
            // appropriate.
            displayMode_.setEnabled(true);
            if (((CompositeImage) ijImage_).getMode() == CompositeImage.COMPOSITE) {
               displayMode_.setSelectedIndex(2);
            }
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to set new ImagePlus");
      }
   }
}

