package org.micromanager.magellan.imagedisplaynew;

import org.micromanager.magellan.imagedisplaynew.ChannelControlPanel;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import ij.CompositeImage;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import javax.swing.JPanel;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.imagedisplay.MagellanDisplay;
import org.micromanager.magellan.imagedisplay.NewImageEvent;
import org.micromanager.magellan.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.magellan.imagedisplaynew.events.UpdateImageEvent;
import org.micromanager.magellan.imagedisplaynew.MagellanDisplayController;
import org.micromanager.magellan.imagedisplaynew.events.DisplayClosingEvent;
import org.micromanager.magellan.misc.Log;
import org.micromanager.magellan.misc.MD;

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
 final class MultiChannelHistograms extends JPanel  {

   private static final int SLOW_HIST_UPDATE_INTERVAL_MS = 1000;
   private long lastUpdateTime_;
   private ArrayList<ChannelControlPanel> ccpList_;
   private MagellanDisplayController display_;
   private boolean updatingCombos_ = false;
   private HistogramControlsState hcs_;
   private ContrastPanel contrastPanel_;
   private JSONObject dispSettings_;
   private int numChannels_ = 0;

   public MultiChannelHistograms(MagellanDisplayController disp, ContrastPanel contrastPanel) {
      super();
      //TODO: initialize wiht disp settings
//      dispSettings_ = disSettings;
      display_ = disp;
      display_.registerForEvents(this);
      hcs_ = contrastPanel.getHistogramControlsState();

      this.setLayout(new GridLayout(1, 1));
      contrastPanel_ = contrastPanel;
      ccpList_ = new ArrayList<ChannelControlPanel>();
//      setupChannelControls();
   }
   
      @Subscribe
   public void onDisplayClose(DisplayClosingEvent e) {
      display_.unregisterForEvents(this);
      display_ = null;
      
      
    ccpList_ = null;
     hcs_ = null;
     contrastPanel_ = null;
     dispSettings_ = null;
   }
   
   public int getNumChannels() {
      return numChannels_;
   }

   @Subscribe
   public synchronized void onNewImageEvent(NewImageEvent event) {
      //TODO make sure this is on EDT
      int channelIndex = event.getPositionForAxis("c");
      if (channelIndex > ccpList_.size() - 1) {
         setupChannelControls(channelIndex + 1, event.channelName_);
      }
   }

   private synchronized void setupChannelControls(int nChannels, String channelName) {
      boolean rgb;
      try {
         rgb = display_.isRGB();
      } catch (Exception ex) {
         Log.log(ex);
         rgb = false;
      }
      if (rgb) {
         nChannels *= 3;
      }
      
      Color color;
      try {
         color = new Color(dispSettings_.getJSONObject(channelName).getInt("Color"));
      } catch (JSONException ex) {
         color = Color.white;
      }
      int bitDepth = 16;
      try {
         bitDepth = dispSettings_.getJSONObject(channelName).optInt("BitDepth", 16);
      } catch (JSONException ex) {
         
      }

      //create new channel control panels as needed
      for (int i = ccpList_.size(); i < nChannels; ++i) {
         ChannelControlPanel ccp = new ChannelControlPanel(i, this, display_, contrastPanel_, channelName, color, bitDepth);
         ccpList_.add(ccp);
         this.add(ccpList_.get(i));
      }
      ((GridLayout) this.getLayout()).setRows(nChannels);
      //add all to this panel
      numChannels_ = nChannels;

      for (ChannelControlPanel c : ccpList_) {
         c.revalidate();
      }

      
       Dimension dim = new Dimension(ChannelControlPanel.MINIMUM_SIZE.width,
              nChannels * ChannelControlPanel.MINIMUM_SIZE.height);
      this.setMinimumSize(dim);
      this.setSize(dim);
      this.setPreferredSize(dim);
      //Dunno if this is even needed
      contrastPanel_.revalidate();
   }

   public void fullScaleChannels() {
      if (ccpList_ == null) {
         return;
      }
      for (ChannelControlPanel c : ccpList_) {
         c.setFullScale();
      }
      applyLUTToImage();
      display_.postEvent(new UpdateImageEvent());
   }

   public void applyContrastToAllChannels(int min, int max, double gamma) {
      if (ccpList_ == null) {
         return;
      }
      for (ChannelControlPanel c : ccpList_) {
         c.setContrast(min, max, gamma);
      }
      applyLUTToImage();
      display_.postEvent(new UpdateImageEvent());
   }

   public void updateOtherDisplayCombos(int selectedIndex) {
      if (updatingCombos_) {
         return;
      }
      updatingCombos_ = true;
      for (int i = 0; i < ccpList_.size(); i++) {
         ccpList_.get(i).setDisplayComboIndex(selectedIndex);
      }
      updatingCombos_ = false;
   }

   public void setChannelDisplayModeFromFirst() {
      if (ccpList_ == null || ccpList_.size() <= 1) {
         return;
      }
      int displayIndex = ccpList_.get(0).getDisplayComboIndex();
      //automatically syncs other channels
      ccpList_.get(0).setDisplayComboIndex(displayIndex);
   }

   public void setChannelContrastFromFirst() {
      if (ccpList_ == null || ccpList_.size() <= 1) {
         return;
      }
      int min = ccpList_.get(0).getContrastMin();
      int max = ccpList_.get(0).getContrastMax();
      double gamma = ccpList_.get(0).getContrastGamma();
      for (int i = 1; i < ccpList_.size(); i++) {
         ccpList_.get(i).setContrast(min, max, gamma);
      }
      applyLUTToImage();
      display_.postEvent(new UpdateImageEvent());
   }

   public void setChannelHistogramDisplayMax(int channelIndex, int histMax) {
      if (ccpList_ == null || ccpList_.size() <= channelIndex) {
         return;
      }
      int index = (int) (histMax == -1 ? 0 : Math.ceil(Math.log(histMax) / Math.log(2)) - 3);
      ccpList_.get(channelIndex).setDisplayComboIndex(index);
   }

   public void applyLUTToImage() {
      if (ccpList_ == null) {
         return;
      }
      for (ChannelControlPanel c : ccpList_) {
         c.applyChannelLUTToImage();
      }
   }

   public void imageChanged() {
      for (ChannelControlPanel c : ccpList_) {
         c.calcAndDisplayHistAndStats(true);

         if (hcs_.autostretch) {
            c.autostretch();
         }
         c.applyChannelLUTToImage();
      }
   }


   public void setChannelContrast(int channelIndex, int min, int max, double gamma) {
      if (channelIndex >= ccpList_.size()) {
         return;
      }
      ccpList_.get(channelIndex).setContrast(min, max, gamma);
   }

   public void setDisplayMode(int mode) {
      contrastPanel_.setDisplayMode(mode);
   }

   public void autoscaleAllChannels() {
      if (ccpList_ != null && ccpList_.size() > 0) {
         for (ChannelControlPanel c : ccpList_) {
            c.autoButtonAction();
         }
      }
   }

   public void rejectOutliersChangeAction() {
      if (ccpList_ != null && ccpList_.size() > 0) {
         for (ChannelControlPanel c : ccpList_) {
            c.calcAndDisplayHistAndStats(true);
            c.autoButtonAction();
         }
      }
   }

   public void calcAndDisplayHistAndStats(boolean drawHist) {
      if (ccpList_ != null) {
         for (ChannelControlPanel c : ccpList_) {
            c.calcAndDisplayHistAndStats(drawHist);
         }
      }
   }

   public void autostretch() {
      if (ccpList_ != null) {
         for (ChannelControlPanel c : ccpList_) {
            c.autostretch();
         }
      }
   }

   public int getNumberOfChannels() {
      return ccpList_.size();
   }
}
