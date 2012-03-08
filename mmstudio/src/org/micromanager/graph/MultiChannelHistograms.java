package org.micromanager.graph;


import ij.ImagePlus;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import javax.swing.JPanel;
import org.micromanager.acquisition.MetadataPanel;
import org.micromanager.api.Histograms;
import org.micromanager.api.ImageCache;
import org.micromanager.graph.ChannelControlPanel;
import org.micromanager.graph.ContrastPanel;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

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


public class MultiChannelHistograms extends JPanel implements Histograms {
   
      private ArrayList<ChannelControlPanel> ccpList_;
      private ContrastPanel contrastPanel_;
      private MetadataPanel mdPanel_;
      private boolean updatingCombos_ = false;

      public MultiChannelHistograms(MetadataPanel md, ContrastPanel cp) {
         super();
         mdPanel_ = md;
         contrastPanel_ = cp;
         
         
      }
      
      
      public synchronized void setupChannelControls(ImageCache cache) {
         this.removeAll();
         this.invalidate();
      
      
         final int nChannels;
         boolean rgb;
         try {
            rgb = MDUtils.isRGB(cache.getSummaryMetadata());
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
            rgb = false;
         }
         if (rgb) {
            nChannels = 3;
         } else {
            nChannels = cache.getNumChannels();
         }

         GridLayout layout = new GridLayout(nChannels, 1);
         this.setLayout(layout);
         this.setMinimumSize(new Dimension(ChannelControlPanel.MINIMUM_SIZE.width,
                 nChannels * ChannelControlPanel.MINIMUM_SIZE.height));
         ccpList_ = new ArrayList<ChannelControlPanel>();
         for (int i = 0; i < nChannels; ++i) {
            ChannelControlPanel ccp = new ChannelControlPanel(i, contrastPanel_, this, mdPanel_, cache,
                    cache.getChannelColor(i), cache.getBitDepth(), contrastPanel_.getIgnorePercent(),
                    contrastPanel_.getLogHist());
            this.add(ccp);
            ccpList_.add(ccp);
         }
         
         this.validate();
   }

       public void fullScaleChannels() {
      if (ccpList_ == null) {
         return;
      }
      for (ChannelControlPanel c : ccpList_) {
         c.setFullScale();
      }
      mdPanel_.drawWithoutUpdate();
   }

   public void applyContrastToAllChannels(int min, int max, double gamma) {
      if (ccpList_ == null) {
         return;
      }
      for (ChannelControlPanel c : ccpList_) {
         c.setContrast(min, max, gamma);
      }
      mdPanel_.drawWithoutUpdate();
   }
   
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
      mdPanel_.drawWithoutUpdate();
   }
      
   @Override
   public void setChannelHistogramDisplayMax(int channelIndex, int histMax) {
      if (ccpList_ == null || ccpList_.size() <= channelIndex) {
         return;
      }
      int index = (int) (histMax == -1 ? 0 : Math.ceil(Math.log(histMax) / Math.log(2)) - 3);
      ccpList_.get(channelIndex).setDisplayComboIndex(index);
   }

   public void applyLUTToImage(ImagePlus img, ImageCache cache) {
      if (ccpList_ == null) {
         return;
      }
      for (ChannelControlPanel c : ccpList_) {
         c.applyChannelLUTToImage(img, cache);
      }
   }

   public void displayChanged(ImagePlus img, ImageCache cache) {
      setupChannelControls(cache);
       for (ChannelControlPanel c : ccpList_) {
         c.calcAndDisplayHistAndStats(img, true);
         c.loadDisplaySettings(cache);
         if (contrastPanel_.getAutostretch()) {
            c.autostretch();
         }
      }
      mdPanel_.drawWithoutUpdate(img);
   }

   @Override
   public void imageChanged(ImagePlus img, ImageCache cache, boolean drawHist, boolean slowHistUpdate) {
      if (ccpList_ == null) {
         return;
      }
    
      if (slowHistUpdate) {
         for (ChannelControlPanel c : ccpList_) {
            boolean histAndStatsCalculated = c.calcAndDisplayHistAndStats(img, drawHist);
            if (histAndStatsCalculated) {
               if (contrastPanel_.getAutostretch()) {
                  c.autostretch();
               }
               c.applyChannelLUTToImage(img, cache);
            }
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
   
   public void setLogScale() {
      if (ccpList_ != null && ccpList_.size() > 0) {
         for (ChannelControlPanel c : ccpList_) {
            c.setLogScale();
         }
      }
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
            c.setFractionToReject(contrastPanel_.getIgnorePercent());
            c.calcAndDisplayHistAndStats(mdPanel_.getCurrentImage(), true);
            c.autoButtonAction();
         }
      }
   }
   
      @Override
   public void calcAndDisplayHistAndStats(ImagePlus img, boolean drawHist) {
      if (ccpList_ != null) {
         for (ChannelControlPanel c : ccpList_) {
            c.calcAndDisplayHistAndStats(img, drawHist);
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

}