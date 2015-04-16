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

import com.google.common.eventbus.Subscribe;

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

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Datastore;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.NewImagePlusEvent;

import org.micromanager.display.internal.events.DefaultRequestToDrawEvent;

import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class provides controls for the general display settings, including
 * some settings that control how the histograms behave. Note this is
 * distinct from the DisplaySettings metadata in the Datastore for the display;
 * some of that is addressed here, and some in the histograms.
 */
public class DisplaySettingsPanel extends InspectorPanel {
   private static final String[] COLOR_DESCRIPTORS = new String[] {
      "RGBCMYW", "CMYRGBW", "Custom"};
   private static final Color[][] DEFAULT_COLORS = new Color[][] {
      {Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA,
         Color.YELLOW, Color.WHITE},
      {Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.RED, Color.GREEN,
         Color.BLUE, Color.WHITE}
   };

   private static final String DISPLAY_MODE = "color mixing mode";
   private static final String SHOULD_AUTOSTRETCH = "whether or not to autostretch";
   private static final String UPDATE_RATE = "rate at which to update histogram";
   private static final String EXTREMA_PERCENT = "percentage of image data ignored when stretching";

   private static final int GRAYSCALE = 0;
   private static final int COLOR = 1;
   private static final int COMPOSITE = 2;

   private Datastore store_;
   private ImagePlus ijImage_;
   private DisplayWindow display_;
   private JComboBox displayMode_;
   private final JComboBox colorPresets_;
   // Index of colorPresets_ last time its position was set.
   private int prevPresetIndex_ = -1;
   private final JCheckBox shouldAutostretch_;
   private final JComboBox histogramUpdateRate_;
   private final JSpinner extremaPercentage_;

   public DisplaySettingsPanel() {
      setLayout(new MigLayout());

      // We have several controls that consist of a label and a combobox;
      // we want the label to be left-aligned, then a variable gap, then
      // the right-aligned combobox.
      add(new JLabel("Display mode: "), "split 2, align left, growx");
      displayMode_ = new JComboBox(
            new String[] {"Grayscale", "Color", "Composite"});
      displayMode_.setToolTipText("<html>Set the display mode for the image:<ul><li>Color: single channel, in color<li>Grayscale: single-channel grayscale<li>Composite: multi-channel color overlay</ul></html>");
      displayMode_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setDisplayMode(displayMode_);
         }
      });
      add(displayMode_, "align right, wrap");

      add(new JLabel("Channel colors: "), "split 2, align left, growx");
      colorPresets_ = new JComboBox(COLOR_DESCRIPTORS);
      colorPresets_.setToolTipText("Select a preset color combination for multichannel setups");
      colorPresets_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setColorPreset();
         }
      });
      add(colorPresets_, "align right, wrap");

      JPanel histogramPanel = new JPanel(new MigLayout("flowy"));
      histogramPanel.setBorder(BorderFactory.createLoweredBevelBorder());

      histogramPanel.add(new JLabel("Histograms update "),
            "flowx, split 2, align left, growx");
      histogramUpdateRate_ = new JComboBox(
            new String[] {"Never", "Every Image", "Once per Second"});
      histogramUpdateRate_.setToolTipText("Select how frequently to update histograms. Reduced histogram update rate may help reduce CPU load.");
      histogramUpdateRate_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setHistogramUpdateRate();
         }
      });
      histogramPanel.add(histogramUpdateRate_, "align right");
      
      shouldAutostretch_ = new JCheckBox("Autostretch images");
      shouldAutostretch_.setToolTipText("Automatically rescale the histograms every time a new image is displayed.");
      shouldAutostretch_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setShouldAutostretch();
         }
      });
      histogramPanel.add(shouldAutostretch_, "growx, align right");

      histogramPanel.add(new JLabel("Ignore extrema %: "),
            "split 2, align left, growx, flowx");
      extremaPercentage_ = new JSpinner();
      extremaPercentage_.setToolTipText("Ignore the top and bottom percentage of the image when autostretching.");
      // Going to 50% would mean the entire thing is ignored.
      extremaPercentage_.setModel(
            new SpinnerNumberModel(0.0, 0.0, 49.999, 1.0));
      extremaPercentage_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent event) {
            setExtremaPercentage();
         }
      });
      extremaPercentage_.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent event) {
            setExtremaPercentage();
         }
      });
      histogramPanel.add(extremaPercentage_, "align right");

      add(histogramPanel);
   }

   @Override
   public synchronized void setDisplay(DisplayWindow display) {
      if (display_ != null) {
         display_.unregisterForEvents(this);
         display_.getDatastore().unregisterForEvents(this);
      }
      display_ = display;
      if (display_ == null) {
         return;
      }
      display_.registerForEvents(this);
      if (store_ != null) {
         store_.unregisterForEvents(this);
      }
      store_ = display_.getDatastore();
      store_.registerForEvents(this);
      ijImage_ = display_.getImagePlus();
      DisplaySettings settings = display_.getDisplaySettings();
      DefaultUserProfile profile = DefaultUserProfile.getInstance();

      if (settings.getChannelDisplayModeIndex() != null) {
         displayMode_.setSelectedIndex(settings.getChannelDisplayModeIndex());
      }
      else {
         // Default is composite mode.
         displayMode_.setSelectedIndex(profile.getInt(getClass(),
                  DISPLAY_MODE, COMPOSITE));
      }
      displayMode_.setEnabled(ijImage_ instanceof CompositeImage);

      setColorPresetIndex(settings);
      if (settings.getHistogramUpdateRate() != null) {
         double updateRate = settings.getHistogramUpdateRate();
         if (updateRate < 0) {
            histogramUpdateRate_.setSelectedIndex(0);
         }
         else if (updateRate == 0) {
            histogramUpdateRate_.setSelectedIndex(1);
         }
         else {
            // TODO: this ignores the possibility that the actual update rate
            // will be a value other than once per second.
            histogramUpdateRate_.setSelectedIndex(2);
         }
      }
      else {
         // Default is "update every image"
         histogramUpdateRate_.setSelectedIndex(1);
      }
      if (settings.getShouldAutostretch() != null) {
         shouldAutostretch_.setSelected(settings.getShouldAutostretch());
      }
      else {
         shouldAutostretch_.setSelected(profile.getBoolean(getClass(),
                  SHOULD_AUTOSTRETCH, true));
      }
      if (settings.getExtremaPercentage() != null) {
         extremaPercentage_.setValue(settings.getExtremaPercentage());
      }
      else {
         extremaPercentage_.setValue(profile.getDouble(
                     getClass(), EXTREMA_PERCENT, 0.0));
      }
   }

   @Override
   public synchronized void cleanup() {
      if (display_ != null) {
         display_.unregisterForEvents(this);
      }
      if (store_ != null) {
         store_.unregisterForEvents(this);
      }
   }

   /**
    * The user has interacted with the display mode combo box.
    */
   private void setDisplayMode(JComboBox displayMode) {
      if (!(ijImage_ instanceof CompositeImage)) {
         // Non-composite images are always in grayscale mode.
         displayMode.setSelectedIndex(GRAYSCALE);
         return;
      }

      CompositeImage composite = (CompositeImage) ijImage_;
      int selection = displayMode.getSelectedIndex();
      DisplaySettings.DisplaySettingsBuilder builder = display_.getDisplaySettings().copy();
      if (selection == COMPOSITE) {
         if (store_.getAxisLength("channel") > 7) {
            JOptionPane.showMessageDialog(null,
               "Images with more than 7 channels cannot be displayed in Composite mode.");
            // Send them back to Color mode.
            displayMode.setSelectedIndex(COLOR);
            builder.channelDisplayModeIndex(COLOR);
         }
         else {
            composite.setMode(CompositeImage.COMPOSITE);
            builder.channelDisplayModeIndex(COMPOSITE);
         }
      }
      else if (selection == COLOR) {
         composite.setMode(CompositeImage.COLOR);
         builder.channelDisplayModeIndex(COLOR);
      }
      else {
         // Assume grayscale mode.
         composite.setMode(CompositeImage.GRAYSCALE);
         builder.channelDisplayModeIndex(GRAYSCALE);
      }
      composite.updateAndDraw();
      DisplaySettings settings = builder.build();
      DefaultUserProfile.getInstance().setInt(getClass(), DISPLAY_MODE,
            settings.getChannelDisplayModeIndex());
      DefaultDisplaySettings.setStandardSettings(settings);
      display_.setDisplaySettings(settings);
      display_.postEvent(new DefaultRequestToDrawEvent());
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
      display_.postEvent(new DefaultRequestToDrawEvent());
   }

   /**
    * We care when the channel colors change, and when autostretch is turned
    * on/off.
    * 
    * @param event
    */
   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      DisplaySettings settings = event.getDisplaySettings();
      try {
         setColorPresetIndex(settings);
         if (shouldAutostretch_ != null &&
               settings.getShouldAutostretch() != null) {
            shouldAutostretch_.setSelected(settings.getShouldAutostretch());
         }
         if (settings.getExtremaPercentage() != null) {
            extremaPercentage_.setValue(settings.getExtremaPercentage());
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
   private void setHistogramUpdateRate() {
      String selection = (String) histogramUpdateRate_.getSelectedItem();
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
      DefaultUserProfile.getInstance().setDouble(getClass(), UPDATE_RATE,
            settings.getHistogramUpdateRate());
      DefaultDisplaySettings.setStandardSettings(settings);
      display_.setDisplaySettings(settings);
   }

   /**
    * The user is toggling autostretch on/off.
    */
   private void setShouldAutostretch() {
      DisplaySettings settings = display_.getDisplaySettings();
      settings = settings.copy().shouldAutostretch(shouldAutostretch_.isSelected()).build();
      DefaultUserProfile.getInstance().setBoolean(getClass(),
            SHOULD_AUTOSTRETCH, settings.getShouldAutostretch());
      DefaultDisplaySettings.setStandardSettings(settings);
      display_.setDisplaySettings(settings);
      display_.postEvent(new DefaultRequestToDrawEvent());
   }

   /**
    * The user set a new trim percentage.
    */
   private void setExtremaPercentage() {
      DisplaySettings settings = display_.getDisplaySettings();
      double percentage = (Double) extremaPercentage_.getValue();
      settings = settings.copy().extremaPercentage(percentage).build();
      DefaultUserProfile.getInstance().setDouble(getClass(),
            EXTREMA_PERCENT, settings.getExtremaPercentage());
      DefaultDisplaySettings.setStandardSettings(settings);
      display_.setDisplaySettings(settings);
      display_.postEvent(new DefaultRequestToDrawEvent());
   }

   /**
    * This is mostly relevant for our handling of the display mode (grayscale,
    * composite, etc.).
    * 
    * @param event
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
               displayMode_.setSelectedIndex(COMPOSITE);
            }
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to set new ImagePlus");
      }
   }
}
