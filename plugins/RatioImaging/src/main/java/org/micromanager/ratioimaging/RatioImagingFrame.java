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


/**

 * Created on Aug 28, 2011, 9:41:57 PM
 */
package org.micromanager.ratioimaging;


import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import mmcorej.CMMCore;
import mmcorej.StrVector;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.events.internal.ChannelGroupEvent;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Micro-Manager plugin 
 *
 * @author nico
 */
public class RatioImagingFrame extends MMFrame implements ProcessorConfigurator {
   private static final int DEFAULT_WIN_X = 100;
   private static final int DEFAULT_WIN_Y = 100;
   private static final String BACKGROUND1 = "Background1";
   private static final String BACKGROUND2 = "Background2";
   private final String[] IMAGESUFFIXES = {"tif", "tiff", "jpg", "png"};

   private final Studio studio_;
   private final CMMCore core_;
   private final JComboBox ch1Combo_;
   private final JComboBox ch2Combo_;
   private final MutablePropertyMapView settings_;
   private final JTextField background1TextField_;
   private final JTextField background2TextField_;
   private final JButton background1Button_;
   private final JButton background2Button_;

   public RatioImagingFrame(PropertyMap configuratorSettings, Studio studio) {
      studio_ = studio;
      core_ = studio_.getCMMCore();
      settings_ = studio_.profile().getSettings(this.getClass());

      super.setTitle("Ratio Imaging");
      super.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

      ch1Combo_ = new JComboBox();
      populateWithChannels(ch1Combo_);
      ch2Combo_ = new JComboBox();
      populateWithChannels(ch2Combo_);
      
      super.setLayout(new MigLayout("flowx"));
      
      JLabel darkImageLabel = new JLabel("background");
      
      background1TextField_ = new JTextField(20);
      createBackgroundTextField(background1TextField_, settings_, BACKGROUND1);
      background2TextField_ = new JTextField(20);
      createBackgroundTextField(background2TextField_, settings_, BACKGROUND2);

      background1Button_ = new JButton();
      backgroundButton(background1Button_, background1TextField_, settings_, 
              BACKGROUND1);
      background2Button_ = new JButton();
      backgroundButton(background2Button_, background2TextField_, settings_, 
              BACKGROUND2);

      super.add (darkImageLabel, "skip 2, center");
      super.add(new JLabel("background-constant"), "skip 1, center, wrap");
      
      super.add(new JLabel("Ch. 1"));
      super.add(ch1Combo_);
      super.add(background1TextField_);
      super.add(background1Button_, "wrap");
      
      super.add(new JLabel("Ch. 2"));
      super.add(ch2Combo_);
      super.add(background2TextField_);
      super.add(background2Button_, "wrap");
      
      super.add(new JLabel("(Ch1 - background) / (Ch2 - background) *"), "span 5, split 2");
      super.add(new JTextField(5), "wrap");
      
      super.pack();

      super.loadAndRestorePosition(DEFAULT_WIN_X, DEFAULT_WIN_Y);
      
      studio_.events().registerForEvents(this);
   }

   @Override
   public PropertyMap getSettings() {
      PropertyMap.Builder builder =  PropertyMaps.builder();
      
      return builder.build();
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

   
   private void populateWithChannels(JComboBox cBox) {
      cBox.removeAllItems();
      String channelGroup = core_.getChannelGroup();
      StrVector channels = core_.getAvailableConfigs(channelGroup);
      for (int i = 0; i < channels.size(); i++) {
        cBox.addItem(channels.get(i));
      }
   }
   
   final JTextField darkFieldTextField = new JTextField(50);
   
   private void createBackgroundTextField(final JTextField backgroundTextField, 
           MutablePropertyMapView settings, String prefKey) {
      //backgroundTextField.setMinimumSize(new Dimension());
      backgroundTextField.setText(settings.getString(prefKey, ""));
      backgroundTextField.setHorizontalAlignment(JTextField.RIGHT);
      backgroundTextField.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            
            processBackgroundImage(backgroundTextField.getText(), settings, prefKey);
         }
      });
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
   }
   
   private String processBackgroundImage(String bFile, 
           MutablePropertyMapView settings, String prefKey) {
      settings.putString(prefKey, bFile);
      return bFile;
   }
   
   private void backgroundButton(final JButton button, 
           final JTextField backgroundField, MutablePropertyMapView settings, 
           String prefKey) {
      Font arialSmallFont = new Font("Arial", Font.PLAIN, 12);
      Dimension buttonSize = new Dimension(70, 21);
      button.setPreferredSize(buttonSize);
      button.setMinimumSize(buttonSize);
      button.setFont(arialSmallFont);
      button.setMargin(new Insets(0, 0, 0, 0));
      
      button.setText("...");
      button.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            File f = FileDialogs.openFile(null, "Background",
                    new FileDialogs.FileType("MMAcq", "Dark image",
                            backgroundField.getText(), true, IMAGESUFFIXES));
            if (f != null) {
               processBackgroundImage(f.getAbsolutePath(), settings, prefKey);
               backgroundField.setText(f.getAbsolutePath());
            }
         }
      });
      
      
   }
   
   @Subscribe
   public void onChannelGroup(ChannelGroupEvent event) {
      populateWithChannels(ch1Combo_);
      populateWithChannels(ch2Combo_);
      pack();
   }

 
}
