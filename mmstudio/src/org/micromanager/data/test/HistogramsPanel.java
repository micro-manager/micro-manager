package org.micromanager.data.test;

import com.google.common.eventbus.EventBus;

import ij.CompositeImage;
import ij.ImagePlus;

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
   private ArrayList<ChannelControlPanel> channelPanels_;
   private Datastore store_;
   private ImagePlus plus_;
   private EventBus bus_;
   private boolean updatingCombos_ = false;

   public HistogramsPanel(Datastore store, ImagePlus plus,
         EventBus bus) {
      super();
      store_ = store;
      plus_ = plus;
      bus_ = bus;
      setupChannelControls();
   }

   public synchronized void setupChannelControls() {
      this.removeAll();
      this.invalidate();

      // TODO: ignoring the possibility of RGB images for now.
      final int nChannels = store_.getMaxIndex("channel") + 1;
      if (nChannels == 0) {
         ReportingUtils.logError("Have zero channels to work with.");
         return;
      }

      GridLayout layout = new GridLayout(nChannels, 1);
      this.setLayout(layout);
      Dimension dim = new Dimension(ChannelControlPanel.MINIMUM_SIZE.width,
              nChannels * ChannelControlPanel.MINIMUM_SIZE.height);
      this.setMinimumSize(dim);
      this.setSize(dim);
      channelPanels_ = new ArrayList<ChannelControlPanel>();
      for (int i = 0; i < nChannels; ++i) {
         ChannelControlPanel panel = new ChannelControlPanel(i, this, store_,
               plus_, bus_);
         this.add(panel);
         channelPanels_.add(panel);
      }

      this.validate();
   }
   
   public void updateChannelNamesAndColors() {
      if (channelPanels_ == null) {
         return;
      }
      for (ChannelControlPanel panel : channelPanels_) {
         panel.updateChannelNameAndColorFromCache();
      }
   }

   public void fullScaleChannels() {
      if (channelPanels_ == null) {
         return;
      }
      for (ChannelControlPanel panel : channelPanels_) {
         panel.setFullScale();
      }
      applyLUTToImage();
   }

   public void applyContrastToAllChannels(int min, int max, double gamma) {
      if (channelPanels_ == null) {
         return;
      }
      for (ChannelControlPanel panel : channelPanels_) {
         panel.setContrast(min, max, gamma);
      }
      applyLUTToImage();
   }

   @Override
   public ContrastSettings getChannelContrastSettings(int channel) {
      if (channelPanels_ == null || channelPanels_.size() - 1 > channel) {
         return null;
      }
      return new ContrastSettings(channelPanels_.get(channel).getContrastMin(),
              channelPanels_.get(channel).getContrastMax(), channelPanels_.get(channel).getContrastGamma());
   }

   public void updateOtherDisplayCombos(int selectedIndex) {
      if (updatingCombos_) {
         return;
      }
      updatingCombos_ = true;
      for (int i = 0; i < channelPanels_.size(); i++) {
         channelPanels_.get(i).setDisplayComboIndex(selectedIndex);
      }
      updatingCombos_ = false;
   }

   public void setChannelDisplayModeFromFirst() {
      if (channelPanels_ == null || channelPanels_.size() <= 1) {
         return;
      }
      int displayIndex = channelPanels_.get(0).getDisplayComboIndex();
      //automatically syncs other channels
      channelPanels_.get(0).setDisplayComboIndex(displayIndex);
   }

   public void setChannelContrastFromFirst() {
      if (channelPanels_ == null || channelPanels_.size() <= 1) {
         return;
      }
      int min = channelPanels_.get(0).getContrastMin();
      int max = channelPanels_.get(0).getContrastMax();
      double gamma = channelPanels_.get(0).getContrastGamma();
      for (int i = 1; i < channelPanels_.size(); i++) {
         channelPanels_.get(i).setContrast(min, max, gamma);
      }
      applyLUTToImage();
   }

   @Override
   public void setChannelHistogramDisplayMax(int channelIndex, int histMax) {
      if (channelPanels_ == null || channelPanels_.size() <= channelIndex) {
         return;
      }
      int index = (int) (histMax == -1 ? 0 : Math.ceil(Math.log(histMax) / Math.log(2)) - 3);
      channelPanels_.get(channelIndex).setDisplayComboIndex(index);
   }

   @Override
   public void applyLUTToImage() {
      if (channelPanels_ == null) {
         return;
      }
      for (ChannelControlPanel panel : channelPanels_) {
         panel.applyChannelLUTToImage();
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
         for (ChannelControlPanel panel : channelPanels_) {
            // TODO: used to check if we're the active display, as the boolean
            // here.
            panel.calcAndDisplayHistAndStats(true);
            if (store_.getDisplaySettings().getShouldAutostretch()) {
               panel.autostretch();
            }
            panel.applyChannelLUTToImage();
         }
      }
   }
   
   public boolean amInCompositeMode() {
      return ((plus_ instanceof CompositeImage) &&
            ((CompositeImage) plus_).getMode() != CompositeImage.COMPOSITE);
   }

   public boolean amMultiChannel() {
      return (plus_ instanceof CompositeImage);
   }

   private void updateActiveChannels() {
      if (!amMultiChannel()) {
         return;
      }
      CompositeImage composite = (CompositeImage) plus_;
      int currentChannel = composite.getChannel() - 1;
      boolean[] active = composite.getActiveChannels();
      if (amInCompositeMode()) {
         for (int i = 0; i < active.length; i++) {
            active[i] = (currentChannel == i);
         }
      }
   }

   @Override
   public void setChannelContrast(int channelIndex, int min, int max, double gamma) {
      if (channelIndex >= channelPanels_.size()) {
         return;
      }
      channelPanels_.get(channelIndex).setContrast(min, max, gamma);
   }
   
   public void setDisplayMode(int mode) {
      // TODO: skipping for now.
//      display_.setDisplayMode(mode);
   }

   @Override
   public void autoscaleAllChannels() {
      if (channelPanels_ != null && channelPanels_.size() > 0) {
         for (ChannelControlPanel panel : channelPanels_) {
            panel.autoButtonAction();
         }
      }
   }

   @Override
   public void rejectOutliersChangeAction() {
      if (channelPanels_ != null && channelPanels_.size() > 0) {
         for (ChannelControlPanel panel : channelPanels_) {
            panel.calcAndDisplayHistAndStats(true);
            panel.autoButtonAction();
         }
      }
   }

   @Override
   public void calcAndDisplayHistAndStats(boolean drawHist) {
      if (channelPanels_ != null) {
         for (ChannelControlPanel panel : channelPanels_) {
            panel.calcAndDisplayHistAndStats(drawHist);
         }
      }
   }

   @Override
   public void autostretch() {
      if (channelPanels_ != null) {
         for (ChannelControlPanel panel : channelPanels_) {
            panel.autostretch();
         }
      }
   }

   @Override
   public int getNumberOfChannels() {
      return channelPanels_.size();
   }
}
