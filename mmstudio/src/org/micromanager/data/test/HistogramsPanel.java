package org.micromanager.data.test;

import com.google.common.eventbus.EventBus;

import ij.CompositeImage;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import javax.swing.JPanel;

import org.micromanager.api.data.Datastore;
import org.micromanager.MMStudio;
import org.micromanager.internalinterfaces.Histograms;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

public final class HistogramsPanel extends JPanel implements Histograms {
   private static final int SLOW_HIST_UPDATE_INTERVAL_MS = 1000;
   private long lastUpdateTime_;
   private ArrayList<ChannelControlPanel> ccpList_;
   private Datastore store_;
   private CompositeImage composite_;
   private EventBus bus_;
   private boolean updatingCombos_ = false;

   public HistogramsPanel(Datastore store, CompositeImage composite,
         EventBus bus) {
      super();
      store_ = store;
      composite_ = composite;
      bus_ = bus;
      setupChannelControls();
   }

   public synchronized void setupChannelControls() {
      this.removeAll();
      this.invalidate();

      // TODO: ignoring the possibility of RGB images for now.
      final int nChannels = store_.getMaxExtent("channels");

      GridLayout layout = new GridLayout(nChannels, 1);
      this.setLayout(layout);
      Dimension dim = new Dimension(ChannelControlPanel.MINIMUM_SIZE.width,
              nChannels * ChannelControlPanel.MINIMUM_SIZE.height);
      this.setMinimumSize(dim);
      this.setSize(dim);
      ccpList_ = new ArrayList<ChannelControlPanel>();
      for (int i = 0; i < nChannels; ++i) {
         ChannelControlPanel ccp = new ChannelControlPanel(i, this, store_,
               composite_, bus_);
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
   }

   public void applyContrastToAllChannels(int min, int max, double gamma) {
      if (ccpList_ == null) {
         return;
      }
      for (ChannelControlPanel c : ccpList_) {
         c.setContrast(min, max, gamma);
      }
      applyLUTToImage();
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
      bus_.post(new DrawEvent());
   }

   @Override
   public void imageChanged() {
      boolean update = true;
      if (//display_.acquisitionIsRunning() ||
            (MMStudio.getInstance().isLiveModeOn())) {
         if (store_.getDisplaySettings().getIsSlowHistogramsOn()) {
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
            // TODO: used to check if we're the active display, as the boolean
            // here.
            c.calcAndDisplayHistAndStats(true);
            if (store_.getDisplaySettings().getShouldAutostretch()) {
               c.autostretch();
            }
            c.applyChannelLUTToImage();
         }
      }
   }
   
   private void updateActiveChannels() {
      int currentChannel = composite_.getChannel() - 1;
      boolean[] active = composite_.getActiveChannels();
      if (composite_.getMode() != CompositeImage.COMPOSITE) {
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
      // TODO: skipping for now.
//      display_.setDisplayMode(mode);
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
