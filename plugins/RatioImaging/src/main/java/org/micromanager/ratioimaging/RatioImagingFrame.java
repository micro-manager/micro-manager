///////////////////////////////////////////////////////////////////////////////
//FILE:          RatioImagingFrame.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2018
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

package org.micromanager.ratioimaging;


import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.text.ParseException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.display.internal.event.DataViewerAddedEvent;
import org.micromanager.events.ChannelGroupChangedEvent;
// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.pluginutilities.PluginUtilities;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Micro-Manager plugin for ratio imaging.
 *
 * @author nico
 */
public class RatioImagingFrame extends JFrame implements ProcessorConfigurator {
   private static final int DEFAULT_WIN_X = 100;
   private static final int DEFAULT_WIN_Y = 100;
   static final String CHANNEL1 = "Channel1";
   static final String CHANNEL2 = "Channel2";
   static final String BACKGROUND1 = "Background1";
   static final String BACKGROUND2 = "Background2";
   static final String BACKGROUND1CONSTANT = "Background1Constant";
   static final String BACKGROUND2CONSTANT = "Background2Constant";
   static final String FACTOR = "Factor";
   private static final String[] IMAGESUFFIXES = {"tif", "tiff", "jpg", "png"};

   private final Studio studio_;
   private final CMMCore core_;
   private final JComboBox<String> ch1Combo_;
   private final JComboBox<String> ch2Combo_;
   private final MutablePropertyMapView settings_;
   private final JTextField background1TextField_;
   private final JTextField background2TextField_;
   private final JButton background1Button_;
   private final JButton background2Button_;
   private final PluginUtilities pluginUtilities_;

   /**
    * Constructs the UI of the plugin.
    *
    * @param configuratorSettings Map with settings.
    * @param studio Our beloved Studio object.
    */
   public RatioImagingFrame(PropertyMap configuratorSettings, Studio studio) {
      studio_ = studio;
      core_ = studio_.getCMMCore();
      settings_ = studio_.profile().getSettings(this.getClass());
      copySettings(settings_, configuratorSettings);
      pluginUtilities_ = new PluginUtilities(studio_);

      super.setTitle("Ratio Imaging");
      super.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

      ch1Combo_ = pluginUtilities_.createChannelCombo(settings_, CHANNEL1);
      ch2Combo_ = pluginUtilities_.createChannelCombo(settings_, CHANNEL2);
      
      super.setLayout(new MigLayout("flowx"));
      
      final JLabel darkImageLabel = new JLabel("background");
      
      background1TextField_ = createBackgroundTextField(settings_, BACKGROUND1);
      background2TextField_ = createBackgroundTextField(settings_, BACKGROUND2);

      background1Button_ =  createBackgroundButton(background1TextField_, settings_, 
              BACKGROUND1);
      background2Button_ = createBackgroundButton(background2TextField_, settings_, 
              BACKGROUND2);
      
      final JTextField bc1TextField = new JTextField(5);
      bc1TextField.setText(settings_.getString(BACKGROUND1CONSTANT, "0"));
      bc1TextField.getDocument().addDocumentListener(
              new TextFieldUpdater(bc1TextField, BACKGROUND1CONSTANT, settings_));
      settings_.putString(BACKGROUND1CONSTANT, bc1TextField.getText());
      
      final JTextField bc2TextField = new JTextField(5);
      bc2TextField.setText(settings_.getString(BACKGROUND2CONSTANT, "0"));
      bc2TextField.getDocument().addDocumentListener(
              new TextFieldUpdater(bc2TextField, BACKGROUND2CONSTANT, settings_));
      settings_.putString(BACKGROUND2CONSTANT, bc2TextField.getText());
      
      final int maxValue = 2 << core_.getImageBitDepth();
      final JTextField factorTextField = new JTextField(5);
      factorTextField.setText(settings_.getString(FACTOR, 
              NumberUtils.intToDisplayString((maxValue))));
      factorTextField.getDocument().addDocumentListener(
              new TextFieldUpdater(factorTextField, FACTOR, settings_));
      settings_.putString(FACTOR, factorTextField.getText());

      super.add(darkImageLabel, "skip 2, center");
      super.add(new JLabel("constant"), "skip 1, center, gap 20:push, wrap");
      
      super.add(new JLabel("Ch. 1"));
      super.add(ch1Combo_);
      super.add(background1TextField_,  "gap 20:push");
      super.add(background1Button_);
      super.add(bc1TextField, "gap 20:push, wrap");
      
      super.add(new JLabel("Ch. 2"));
      super.add(ch2Combo_);
      super.add(background2TextField_, "gap 20:push");
      super.add(background2Button_);
      super.add(bc2TextField, "gap 20:push, wrap");
      
      super.add(new JLabel("(Ch1 - (background + constant)) / (Ch2 - (background + constant) *"), 
              "gapy 20:push, span 5, split 2");
      super.add(factorTextField, "wrap");
      
      super.pack();

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
              getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(DEFAULT_WIN_X, DEFAULT_WIN_Y);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
      studio_.displays().registerForEvents(this);
      
   }

   @Override
   public PropertyMap getSettings() {
      return settings_.toPropertyMap();
   }

   @Override
   public void showGUI() {
      setVisible(true);
   }

   @Override
   public void cleanup() {
      studio_.events().unregisterForEvents(this);
      dispose();
   }

   
   private void copySettings(MutablePropertyMapView settings, PropertyMap configuratorSettings) {
      settings.putString(CHANNEL1, configuratorSettings.getString(CHANNEL1, ""));
      settings.putString(CHANNEL2, configuratorSettings.getString(CHANNEL2, ""));
      settings.putString(BACKGROUND1, configuratorSettings.getString(BACKGROUND1, ""));
      settings.putString(BACKGROUND2, configuratorSettings.getString(BACKGROUND2, ""));
      settings.putString(BACKGROUND1CONSTANT, 
              configuratorSettings.getString(BACKGROUND1CONSTANT, ""));
      settings.putString(BACKGROUND2CONSTANT, 
              configuratorSettings.getString(BACKGROUND2CONSTANT, ""));
      settings.putString(FACTOR, configuratorSettings.getString(FACTOR, ""));
   }


   private JTextField createBackgroundTextField(MutablePropertyMapView settings,
           String prefKey) {
      
      final JTextField backgroundTextField = new JTextField(20);
      backgroundTextField.setText(settings.getString(prefKey, ""));
      backgroundTextField.setHorizontalAlignment(JTextField.RIGHT);
      backgroundTextField.addActionListener(
            evt -> processBackgroundImage(backgroundTextField.getText(), settings, prefKey));
      backgroundTextField.addFocusListener(new FocusListener() {
         @Override
         public void focusGained(FocusEvent fe) {
            // great, so what?
         }

         @Override
         public void focusLost(FocusEvent fe) {
            processBackgroundImage(backgroundTextField.getText(), settings, prefKey);
         }
      });
      backgroundTextField.setText(processBackgroundImage(
              backgroundTextField.getText(), settings, prefKey));
      
      return backgroundTextField;
   }
   
   
   private String processBackgroundImage(String bFile, 
           MutablePropertyMapView settings, String prefKey) {
      settings.putString(prefKey, bFile);
      return bFile;
   }
   
   private JButton createBackgroundButton(
           final JTextField backgroundField, MutablePropertyMapView settings, 
           String prefKey) {
      final JButton button = new JButton();
      Font arialSmallFont = new Font("Arial", Font.PLAIN, 12);
      Dimension buttonSize = new Dimension(40, 21);
      button.setPreferredSize(buttonSize);
      button.setMinimumSize(buttonSize);
      button.setFont(arialSmallFont);
      button.setMargin(new Insets(0, 0, 0, 0));
      
      button.setText("...");
      button.addActionListener(evt -> {
         File f = FileDialogs.openFile(null, "Background",
                 new FileDialogs.FileType("MMAcq", "Dark image",
                         backgroundField.getText(), true, IMAGESUFFIXES));
         if (f != null) {
            processBackgroundImage(f.getAbsolutePath(), settings, prefKey);
            backgroundField.setText(f.getAbsolutePath());
         }
      });
      return button;
      
   }

   /**
    * Updates the UI when the ChannelGroup changes.
    *
    * @param event Event signalling the Channel group changed
    */
   @Subscribe
   public void onChannelGroupChanged(ChannelGroupChangedEvent event) {
      pluginUtilities_.populateWithChannels(ch1Combo_);
      pluginUtilities_.populateWithChannels(ch2Combo_);
      pack();
   }

   /**
    * Called when a new display opens.  Use this event to update the cannel
    * list in the UI.
    *
    * @param dae Event signalling a new display was added.
    */
   @Subscribe
   public void onDisplayAdded(DataViewerAddedEvent dae) {
      pluginUtilities_.populateWithChannels(ch1Combo_);
      pluginUtilities_.populateWithChannels(ch2Combo_);
      pack();
   }


   private class TextFieldUpdater implements DocumentListener {

      private final JTextField field_;
      private final String key_;
      private final MutablePropertyMapView settings_;

      public TextFieldUpdater(JTextField field, String key, MutablePropertyMapView settings) {
         field_ = field;
         key_ = key;
         settings_ = settings;
      }

      @Override
      public void insertUpdate(DocumentEvent e) {
         processEvent(e);
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
         processEvent(e);
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
         processEvent(e);
      }

      private void processEvent(DocumentEvent e) {
         try {
            int factor = NumberUtils.displayStringToInt(field_.getText());
            settings_.putString(key_, NumberUtils.intToDisplayString(factor));
         } catch (ParseException p) {
            // ignore
         }
      }
   }

}