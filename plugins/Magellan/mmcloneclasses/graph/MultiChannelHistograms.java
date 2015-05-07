package mmcloneclasses.graph;

import ij.CompositeImage;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import javax.swing.JPanel;
import acq.MMImageCache;
import imagedisplay.DisplayPlus;
import imagedisplay.VirtualAcquisitionDisplay;
import misc.Log;
import org.micromanager.MMStudio;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.MDUtils;

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
public final class MultiChannelHistograms extends JPanel implements Histograms {

   private static final int SLOW_HIST_UPDATE_INTERVAL_MS = 1000;
   private long lastUpdateTime_;
   private ArrayList<ChannelControlPanel> ccpList_;
   private VirtualAcquisitionDisplay display_;
   private MMImageCache cache_;
   private CompositeImage img_;
   private boolean updatingCombos_ = false;
   private HistogramControlsState hcs_;
   private ContrastPanel contrastPanel_;

   public MultiChannelHistograms(VirtualAcquisitionDisplay disp, ContrastPanel contrastPanel) {
      super();
      display_ = disp;
      img_ = (CompositeImage) disp.getImagePlus();
      cache_ = disp.getImageCache();
      hcs_ = contrastPanel.getHistogramControlsState();

      setupChannelControls(cache_, contrastPanel);
   }

   @Override
   public synchronized void setupChannelControls(MMImageCache cache, ContrastPanel cp) {
      this.removeAll();
      this.invalidate();

      contrastPanel_ = cp;

      final int nChannels;
      boolean rgb;
      try {
         rgb = MDUtils.isRGB(display_.getSummaryMetadata());
      } catch (Exception ex) {
         Log.log(ex);
         rgb = false;
      }
      if (rgb) {
         nChannels = 3;
      } else {
         nChannels = display_.getNumChannels();
      }

      GridLayout layout = new GridLayout(nChannels, 1);
      this.setLayout(layout);
      Dimension dim = new Dimension(ChannelControlPanel.MINIMUM_SIZE.width,
              nChannels * ChannelControlPanel.MINIMUM_SIZE.height);
      this.setMinimumSize(dim);
      this.setSize(dim);
      ccpList_ = new ArrayList<ChannelControlPanel>();
      for (int i = 0; i < nChannels; ++i) {
         ChannelControlPanel ccp = new ChannelControlPanel(i, this, display_, cp);
         this.add(ccp);
         ccpList_.add(ccp);
      }

      this.validate();
   }
   
   public void updateChannelNamesAndColors() {
      if (ccpList_ == null) {
         return;
      }
      for (ChannelControlPanel c : ccpList_) {
         c.updateChannelNameAndColorFromCache();
      }
   }

   public void fullScaleChannels() {
      if (ccpList_ == null) {
         return;
      }
      for (ChannelControlPanel c : ccpList_) {
         c.setFullScale();
      }
      applyLUTToImage();
      display_.drawWithoutUpdate();
   }

   public void applyContrastToAllChannels(int min, int max, double gamma) {
      if (ccpList_ == null) {
         return;
      }
      for (ChannelControlPanel c : ccpList_) {
         c.setContrast(min, max, gamma);
      }
      applyLUTToImage();
      display_.drawWithoutUpdate();
   }

   @Override
   public ContrastSettings getChannelContrastSettings(int channel) {
      if (ccpList_ == null || ccpList_.size() - 1 > channel) {
         return null;
      }
      return new ContrastSettings(ccpList_.get(channel).getContrastMin(),
              ccpList_.get(channel).getContrastMax(), ccpList_.get(channel).getContrastGamma());
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
      display_.drawWithoutUpdate();
   }

   @Override
   public void setChannelHistogramDisplayMax(int channelIndex, int histMax) {
      if (ccpList_ == null || ccpList_.size() <= channelIndex) {
         return;
      }
      int index = (int) (histMax == -1 ? 0 : Math.ceil(Math.log(histMax) / Math.log(2)) - 3);
      ccpList_.get(channelIndex).setDisplayComboIndex(index);
   }

   @Override
   public void applyLUTToImage() {
      if (ccpList_ == null) {
         return;
      }
      for (ChannelControlPanel c : ccpList_) {
         c.applyChannelLUTToImage();
      }
   }

   @Override
   public void imageChanged() {
     boolean update = true;
        if ( ((DisplayPlus) display_).getAcquisition() != null && !((DisplayPlus) display_).getAcquisition().isFinished()  &&
                !((DisplayPlus) display_).getAcquisition().isPaused()) {
            if (hcs_.slowHist) {
                long time = System.currentTimeMillis();
                if (time - lastUpdateTime_ < SLOW_HIST_UPDATE_INTERVAL_MS) {
                    update = false;
                } else {
                    lastUpdateTime_ = time;
                }
            }
        }
 
      updateActiveChannels();
      
      if (update) {
         for (ChannelControlPanel c : ccpList_) {
            c.calcAndDisplayHistAndStats(true);
            
            if (hcs_.autostretch) {
               c.autostretch();
            }
            c.applyChannelLUTToImage();
         }
      }
   }
   
   private void updateActiveChannels() {
      int currentChannel = img_.getChannel() - 1;
      boolean[] active = img_.getActiveChannels();
      if (img_.getMode() != CompositeImage.COMPOSITE) {
         for (int i = 0; i < active.length; i++) {
            active[i] = (currentChannel == i);
         }
      }
   }

   @Override
   public void setChannelContrast(int channelIndex, int min, int max, double gamma) {
      if (channelIndex >= ccpList_.size()) {
         return;
      }
      ccpList_.get(channelIndex).setContrast(min, max, gamma);
   }
   
   public void setDisplayMode(int mode) {
      contrastPanel_.setDisplayMode(mode);
   }

   @Override
   public void autoscaleAllChannels() {
      if (ccpList_ != null && ccpList_.size() > 0) {
         for (ChannelControlPanel c : ccpList_) {
            c.autoButtonAction();
         }
      }
   }

   @Override
   public void rejectOutliersChangeAction() {
      if (ccpList_ != null && ccpList_.size() > 0) {
         for (ChannelControlPanel c : ccpList_) {
            c.calcAndDisplayHistAndStats(true);
            c.autoButtonAction();
         }
      }
   }

   @Override
   public void calcAndDisplayHistAndStats(boolean drawHist) {
      if (ccpList_ != null) {
         for (ChannelControlPanel c : ccpList_) {
            c.calcAndDisplayHistAndStats(drawHist);
         }
      }
   }

   @Override
   public void autostretch() {
      if (ccpList_ != null) {
         for (ChannelControlPanel c : ccpList_) {
            c.autostretch();
         }
      }
   }

   @Override
   public int getNumberOfChannels() {
      return ccpList_.size();
   }
}
