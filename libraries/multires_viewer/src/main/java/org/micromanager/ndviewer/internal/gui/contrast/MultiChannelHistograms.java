///////////////////////////////////////////////////////////////////////////////
//FILE:          MultiChannelHistograms.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, 2012
//
// COPYRIGHT:    University of California, San Francisco, 2012
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
//
package org.micromanager.ndviewer.internal.gui.contrast;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.HashMap;
import javax.swing.JPanel;
import org.micromanager.ndviewer.main.NDViewer;
import org.micromanager.ndviewer.main.NDViewer;


final class MultiChannelHistograms extends JPanel {

   private HashMap<String, ChannelControlPanel> ccpList_;
   private NDViewer display_;
   private boolean updatingCombos_ = false;
   private ContrastPanel contrastPanel_;
   private DisplaySettings dispSettings_;

   public MultiChannelHistograms(NDViewer disp, ContrastPanel contrastPanel) {
      super();
      display_ = disp;
//      display_.registerForEvents(this);

      this.setLayout(new GridLayout(1, 1));
      contrastPanel_ = contrastPanel;
      ccpList_ = new HashMap<String, ChannelControlPanel>();
//      setupChannelControls();
   }
      
   public void displaySettingsChanged() {
      for (ChannelControlPanel c : ccpList_.values()) {
         c.updateActiveCheckbox(dispSettings_.isActive(c.getChannelName()));
      }
   }
   
   public void onDisplayClose() {
      display_ = null;

      ccpList_ = null;
      contrastPanel_ = null;
      dispSettings_ = null;
   }

   public void addContrastControls(String channelName) {
      // bring back RGB if you want...
//      boolean rgb;
//      try {
//         rgb = display_.isRGB();
//      } catch (Exception ex) {
//         Log.log(ex);
//         rgb = false;
//      }
//      if (rgb) {
//         nChannels *= 3;
//      }

      dispSettings_ = display_.getDisplaySettingsObject();
      //refresh display settings

      Color color;
      try {
         color = dispSettings_.getColor(channelName);
      } catch (Exception ex) {
         ex.printStackTrace();
         color = Color.white;
      }
      int bitDepth = 16;
      try {
         bitDepth = dispSettings_.getBitDepth(channelName);
      } catch (Exception ex) {
         ex.printStackTrace();
         bitDepth = 16;
      }
      
      //create new channel control panels as needed
      ChannelControlPanel ccp = new ChannelControlPanel(display_, contrastPanel_, channelName, color, bitDepth);
      ccpList_.put(channelName, ccp);
      this.add(ccpList_.get(channelName));

      ((GridLayout) this.getLayout()).setRows(ccpList_.keySet().size());

      Dimension dim = new Dimension(ChannelControlPanel.MINIMUM_SIZE.width,
              ccpList_.keySet().size() * ChannelControlPanel.MINIMUM_SIZE.height);
      this.setMinimumSize(dim);
      this.setSize(dim);
      this.setPreferredSize(dim);
      //Dunno if this is even needed
      contrastPanel_.revalidate();
   }

   public void autoscaleAllChannels() {
      if (ccpList_ != null && ccpList_.size() > 0) {
         for (ChannelControlPanel c : ccpList_.values()) {
            c.autoButtonAction();
         }
      }
   }

   public void rejectOutliersChangeAction() {
      if (ccpList_ != null && ccpList_.size() > 0) {
         for (ChannelControlPanel c : ccpList_.values()) {
            c.autoButtonAction();
         }
      }
   }

   public int getNumberOfChannels() {
      return ccpList_.size();
   }

   void updateHistogramData(HashMap<String, int[]> hists, HashMap<String, Integer> mins, HashMap<String, Integer> maxs) {
      for (String i : hists.keySet()) {
         ccpList_.get(i).updateHistogram(hists.get(i), mins.get(i), maxs.get(i));             
      }
   }
}
