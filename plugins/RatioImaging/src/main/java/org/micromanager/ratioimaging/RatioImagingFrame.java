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
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JRadioButton;

import mmcorej.CMMCore;
import mmcorej.StrVector;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.events.internal.ChannelGroupEvent;
import org.micromanager.internal.utils.MMFrame;

/**
 * Micro-Manager plugin 
 *
 * @author nico
 */
public class RatioImagingFrame extends MMFrame implements ProcessorConfigurator {
   private static final int DEFAULT_WIN_X = 100;
   private static final int DEFAULT_WIN_Y = 100;
   private static final String ORIENTATION = "Orientation";
   private static final String NUM_SPLITS = "numSplits";
   private static final String[] SPLIT_OPTIONS = new String[] {"Two", "Three",
      "Four", "Five"};
   public static final String LR = "lr";
   public static final String TB = "tb";

   private final Studio studio_;
   private final CMMCore core_;
   private final JComboBox ch1Combo_;
   private final JComboBox ch2Combo_;
   private String orientation_;
   private int numSplits_;
   private JRadioButton lrRadio_;
   private JRadioButton tbRadio_;

   public RatioImagingFrame(PropertyMap settings, Studio studio) {
      studio_ = studio;
      core_ = studio_.getCMMCore();

      orientation_ = settings.getString("orientation",
            studio_.profile().getSettings(RatioImagingFrame.class).getString(ORIENTATION, LR));
      numSplits_ = settings.getInteger("splits",
            studio_.profile().getSettings(RatioImagingFrame.class).getInteger(NUM_SPLITS, 2));

      super.setTitle("Ratio Imaging");
      super.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

      ch1Combo_ = new JComboBox();
      populateWithChannels(ch1Combo_);
      ch2Combo_ = new JComboBox();
      populateWithChannels(ch2Combo_);
      
      super.setLayout(new MigLayout("flowx"));
      
      super.add(new JLabel("Ch. 1"));
      super.add(ch1Combo_, "wrap");
      super.add(new JLabel("Ch. 2"));
      super.add(ch2Combo_, "wrap");
      
      super.pack();

      super.loadAndRestorePosition(DEFAULT_WIN_X, DEFAULT_WIN_Y);
      
      studio_.events().registerForEvents(this);
   }

   @Override
   public PropertyMap getSettings() {
      PropertyMap.Builder builder =  PropertyMaps.builder();
      builder.putString("orientation", orientation_);
      builder.putInteger("splits", numSplits_);
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


   private void updateSettings(String orientation, int numSplits) {
      orientation_ = orientation;
      numSplits_ = numSplits;
      studio_.profile().getSettings(RatioImagingFrame.class).putString(
              ORIENTATION, orientation);
      studio_.profile().getSettings(RatioImagingFrame.class).putInteger(
              NUM_SPLITS, numSplits_);
      studio_.data().notifyPipelineChanged();
      repaint();
   }

  private void populateWithChannels(JComboBox cBox) {
     cBox.removeAllItems();
     String channelGroup = core_.getChannelGroup();
     StrVector channels = core_.getAvailableConfigs(channelGroup);
     for (int i = 0; i < channels.size(); i++) {
        cBox.addItem(channels.get(i));
     }
  }
   
   @Subscribe
   public void onChannelGroup(ChannelGroupEvent event) {
      populateWithChannels(ch1Combo_);
      populateWithChannels(ch2Combo_);
      pack();
   }

   /**
    * Recreate the contents and current selection of the chanGroupSelect_
    * combobox. We have to temporarily disable its action listener so it
    * doesn't try to change the current channel group while we do this.
    */
   private void refreshChannelGroup() {
      /*
      shouldChangeChannelGroup_ = false;
      chanGroupSelect_.removeAllItems();
      for (String group : studio_.getAcquisitionEngine().getAvailableGroups()) {
         chanGroupSelect_.addItem(group);
      }
      chanGroupSelect_.setSelectedItem(core_.getChannelGroup());
      shouldChangeChannelGroup_ = true;
*/
   }
}
