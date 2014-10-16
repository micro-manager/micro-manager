package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.CompositeImage;
import ij.ImagePlus;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.data.NewImageEvent;

import org.micromanager.internalinterfaces.Histograms;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.ReportingUtils;

public final class HistogramsPanel extends JPanel implements Histograms {
   private ArrayList<ChannelControlPanel> channelPanels_;
   private Datastore store_;
   private MMVirtualStack stack_;
   private ImagePlus ijImage_;
   private EventBus displayBus_;
   private Timer histogramUpdateTimer_;
   private long lastUpdateTime_ = 0;
   private boolean updatingCombos_ = false;

   public HistogramsPanel(Datastore store, MMVirtualStack stack,
         ImagePlus ijImage, EventBus displayBus) {
      super();
      store_ = store;
      store_.registerForEvents(this, 100);
      stack_ = stack;
      ijImage_ = ijImage;
      displayBus_ = displayBus;
      setupChannelControls();
   }

   /**
    * Remove our existing UI, if any, and create it anew.
    */
   public synchronized void setupChannelControls() {
      removeAll();
      invalidate();
      if (channelPanels_ != null) {
         for (ChannelControlPanel panel : channelPanels_) {
            panel.cleanup();
         }
      }

      // TODO: ignoring the possibility of RGB images for now.
      final int nChannels = store_.getMaxIndex("channel") + 1;
      if (nChannels == 0) {
         ReportingUtils.logError("Have zero channels to work with.");
         return;
      }

      setLayout(new MigLayout("flowy"));
      channelPanels_ = new ArrayList<ChannelControlPanel>();
      for (int i = 0; i < nChannels; ++i) {
         ChannelControlPanel panel = new ChannelControlPanel(i, this, store_,
               stack_, ijImage_, displayBus_);
         add(panel, "growy");
         channelPanels_.add(panel);
      }

      validate();
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
      ReportingUtils.logError("Applying " + min + " " + max + " " + gamma + " to all channels");
      if (channelPanels_ == null) {
         ReportingUtils.logError("Oops no channel panels");
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
      ChannelControlPanel panel = channelPanels_.get(channel);
      return new ContrastSettings(panel.getContrastMin(),
              panel.getContrastMax(), panel.getContrastGamma());
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
         ReportingUtils.logError("Oops, no channel panels");
         return;
      }
      for (ChannelControlPanel panel : channelPanels_) {
         panel.applyChannelLUTToImage();
      }
      displayBus_.post(new DefaultRequestToDrawEvent());
   }

   public boolean amInCompositeMode() {
      return ((ijImage_ instanceof CompositeImage) &&
            ((CompositeImage) ijImage_).getMode() != CompositeImage.COMPOSITE);
   }

   public boolean amMultiChannel() {
      return (ijImage_ instanceof CompositeImage);
   }

   @Override
   public void setChannelContrast(int channelIndex, int min, int max, double gamma) {
      if (channelIndex >= channelPanels_.size()) {
         return;
      }
      channelPanels_.get(channelIndex).setContrast(min, max, gamma);
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

   private double getHistogramUpdateRate() {
      DisplaySettings settings = store_.getDisplaySettings();
      if (settings == null || settings.getHistogramUpdateRate() == null) {
         // Assume we update every time.
         return 0;
      }
      return settings.getHistogramUpdateRate();
   }

   @Override
   public void calcAndDisplayHistAndStats() {
      if (channelPanels_ == null) {
         return;
      }
      double updateRate = getHistogramUpdateRate();
      if (updateRate < 0) {
         // Update rate is set to "never".
         return;
      }
      else if (updateRate == 0) {
         // Update every time an image comes through.
         updateHistograms();
      }
      else {
         // Set up a timer to update the histograms at the right time.
         if (histogramUpdateTimer_ == null) {
            histogramUpdateTimer_ = new Timer();
         }
         TimerTask task = new TimerTask() {
            @Override
            public void run() {
               updateHistograms();
            }
         };
         histogramUpdateTimer_.schedule(task, (long) (updateRate * 1000));
      }
   }

   // Ensure it's been the right amount of time since the last update, then
   // redraw histograms.
   private void updateHistograms() {
      long curTime = System.currentTimeMillis();
      if (curTime - lastUpdateTime_ > getHistogramUpdateRate() * 1000) {
         // It's time to do an update.
         for (ChannelControlPanel panel : channelPanels_) {
            panel.calcAndDisplayHistAndStats(true);
         }
         lastUpdateTime_ = curTime;
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

   public void cleanup() {
      for (ChannelControlPanel panel : channelPanels_) {
         panel.cleanup();
      }
   }

   public void setImagePlus(ImagePlus ijImage) {
      ijImage_ = ijImage;
      setupChannelControls();
   }

   @Subscribe
   public void onNewImage(NewImageEvent event) {
      if (event.getImage().getCoords().getPositionAt("channel") >= channelPanels_.size()) {
         // Need to add a new channel histogram.
         setupChannelControls();
      }
   }
}
