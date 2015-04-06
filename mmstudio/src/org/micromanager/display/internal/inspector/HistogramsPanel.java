///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.display.internal.inspector;

import com.google.common.eventbus.Subscribe;

import ij.CompositeImage;
import ij.ImagePlus;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.NewImageEvent;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.NewImagePlusEvent;

import org.micromanager.display.Inspector;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.internal.events.DefaultRequestToDrawEvent;
import org.micromanager.display.internal.events.LUTUpdateEvent;
import org.micromanager.display.internal.DefaultDisplayWindow;
import org.micromanager.display.internal.DisplayDestroyedEvent;
import org.micromanager.display.internal.MMVirtualStack;
import org.micromanager.display.PixelsSetEvent;

import org.micromanager.internal.interfaces.Histograms;
import org.micromanager.internal.utils.ContrastSettings;
import org.micromanager.internal.utils.ReportingUtils;

// HACK TODO: all methods that interact with channelPanels_ are synchronized
// to prevent concurrent modification exceptions. In fact, I don't think we
// really need this class in the first place, or at least we don't need it to
// be so tightly-bound to the ChannelControlPanels it contains.
public final class HistogramsPanel extends InspectorPanel implements Histograms {
   private Inspector inspector_;

   private ArrayList<ChannelControlPanel> channelPanels_;
   private Datastore store_;
   private DisplayWindow display_;
   private MMVirtualStack stack_;
   private ImagePlus ijImage_;
   private Timer histogramUpdateTimer_;
   private long lastUpdateTime_ = 0;
   private boolean updatingCombos_ = false;

   public HistogramsPanel() {
      super();
      setMinimumSize(new java.awt.Dimension(280, 0));
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
      final int nChannels = store_.getAxisLength(Coords.CHANNEL);
      if (nChannels == 0) {
         ReportingUtils.logError("Have zero channels to work with.");
         return;
      }

      setLayout(new MigLayout("flowy, fillx"));
      channelPanels_ = new ArrayList<ChannelControlPanel>();
      for (int i = 0; i < nChannels; ++i) {
         ChannelControlPanel panel = new ChannelControlPanel(i, this, store_,
               display_, stack_, ijImage_);
         add(panel, "grow");
         channelPanels_.add(panel);
      }

      validate();
      inspector_.relayout();
   }
   
   public synchronized void fullScaleChannels() {
      if (channelPanels_ == null) {
         return;
      }
      for (ChannelControlPanel panel : channelPanels_) {
         panel.setFullScale();
      }
      display_.postEvent(new LUTUpdateEvent(null, null, null));
      display_.postEvent(new DefaultRequestToDrawEvent());
   }

   @Override
   public synchronized ContrastSettings getChannelContrastSettings(int channel) {
      if (channelPanels_ == null || channelPanels_.size() - 1 > channel) {
         return null;
      }
      ChannelControlPanel panel = channelPanels_.get(channel);
      return new ContrastSettings(panel.getContrastMin(),
              panel.getContrastMax(), panel.getContrastGamma());
   }

   public synchronized void updateOtherDisplayCombos(int selectedIndex) {
      if (updatingCombos_) {
         return;
      }
      updatingCombos_ = true;
      for (int i = 0; i < channelPanels_.size(); i++) {
         channelPanels_.get(i).setDisplayComboIndex(selectedIndex);
      }
      updatingCombos_ = false;
   }

   public synchronized void setChannelDisplayModeFromFirst() {
      if (channelPanels_ == null || channelPanels_.size() <= 1) {
         return;
      }
      int displayIndex = channelPanels_.get(0).getDisplayComboIndex();
      //automatically syncs other channels
      channelPanels_.get(0).setDisplayComboIndex(displayIndex);
   }

   public synchronized void setChannelContrastFromFirst() {
      if (channelPanels_ == null || channelPanels_.size() <= 1) {
         return;
      }
      int min = channelPanels_.get(0).getContrastMin();
      int max = channelPanels_.get(0).getContrastMax();
      double gamma = channelPanels_.get(0).getContrastGamma();
      display_.postEvent(new LUTUpdateEvent(min, max, gamma));
      display_.postEvent(new DefaultRequestToDrawEvent());
   }

   @Override
   public synchronized void setChannelHistogramDisplayMax(int channelIndex, int histMax) {
      if (channelPanels_ == null || channelPanels_.size() <= channelIndex) {
         return;
      }
      int index = (int) (histMax == -1 ? 0 : Math.ceil(Math.log(histMax) / Math.log(2)) - 3);
      channelPanels_.get(channelIndex).setDisplayComboIndex(index);
   }

   public boolean amInCompositeMode() {
      return ((ijImage_ instanceof CompositeImage) &&
            ((CompositeImage) ijImage_).getMode() != CompositeImage.COMPOSITE);
   }

   public boolean amMultiChannel() {
      return (ijImage_ instanceof CompositeImage);
   }

   @Override
   public synchronized void setChannelContrast(int channelIndex, int min, int max, double gamma) {
      if (channelIndex >= channelPanels_.size()) {
         return;
      }
      channelPanels_.get(channelIndex).setContrast(min, max, gamma);
   }
   
   @Override
   public synchronized void autoscaleAllChannels() {
      if (channelPanels_ != null && channelPanels_.size() > 0) {
         for (ChannelControlPanel panel : channelPanels_) {
            panel.autoButtonAction();
         }
      }
   }

   @Override
   public synchronized void rejectOutliersChangeAction() {
      if (channelPanels_ != null && channelPanels_.size() > 0) {
         for (ChannelControlPanel panel : channelPanels_) {
            panel.calcAndDisplayHistAndStats(true);
            panel.autoButtonAction();
         }
      }
   }

   private double getHistogramUpdateRate() {
      DisplaySettings settings = display_.getDisplaySettings();
      if (settings == null || settings.getHistogramUpdateRate() == null) {
         // Assume we update every time.
         return 0;
      }
      return settings.getHistogramUpdateRate();
   }

   @Override
   public synchronized void calcAndDisplayHistAndStats() {
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
   private synchronized void updateHistograms() {
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
   public synchronized void autostretch() {
      if (channelPanels_ != null) {
         for (ChannelControlPanel panel : channelPanels_) {
            panel.autostretch();
         }
      }
   }

   @Override
   public synchronized int getNumberOfChannels() {
      return channelPanels_.size();
   }

   public void setImagePlus(ImagePlus ijImage) {
      ijImage_ = ijImage;
      setupChannelControls();
   }

   @Subscribe
   public synchronized void onNewImage(NewImageEvent event) {
      if (event.getImage().getCoords().getIndex("channel") >= channelPanels_.size()) {
         // Need to add a new channel histogram.
         setupChannelControls();
      }
   }

   @Subscribe
   public void onPixelsSet(PixelsSetEvent event) {
      calcAndDisplayHistAndStats();
   }

   @Subscribe
   public void onNewImagePlus(NewImagePlusEvent event) {
      try {
         setImagePlus(event.getImagePlus());
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to set new ImagePlus");
      }
   }

   @Subscribe
   public synchronized void onDisplayDestroyed(DisplayDestroyedEvent event) {
      try {
         for (ChannelControlPanel panel : channelPanels_) {
            panel.cleanup();
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error during histograms cleanup");
      }
   }

   @Override
   public void setDisplay(DisplayWindow display) {
      if (display_ != null) {
         display_.unregisterForEvents(this);
      }
      display_ = display;
      display_.registerForEvents(this);
      store_ = display_.getDatastore();
      store_.registerForEvents(this);
      stack_ = ((DefaultDisplayWindow) display_).getStack();
      ijImage_ = display_.getImagePlus();
      setupChannelControls();
   }

   @Override
   public void setInspector(Inspector inspector) {
      inspector_ = inspector;
   }
}
