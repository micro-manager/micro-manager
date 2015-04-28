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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

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
import org.micromanager.display.internal.DefaultDisplayManager;
import org.micromanager.display.internal.DefaultDisplayWindow;
import org.micromanager.display.internal.DisplayDestroyedEvent;
import org.micromanager.display.internal.link.DisplayGroupManager;
import org.micromanager.display.internal.MMVirtualStack;
import org.micromanager.display.PixelsSetEvent;

import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.events.DisplayAboutToShowEvent;

import org.micromanager.internal.utils.ContrastSettings;
import org.micromanager.internal.utils.ReportingUtils;

// This class tracks all histograms for all displays in a given inspector
// window.
// TODO: ideally the histogram *data* should be associated with the
// DisplayWindow, and we would only handle the histogram *controls*. This
// doesn't save us that much effort in terms of bookkeeping but it would give
// us a lot more flexibility in terms of how we control contrast settings.
// HACK TODO: all methods that interact with channelPanels_ are synchronized
// to prevent concurrent modification exceptions. In fact, I don't think we
// really need this class to be so tightly-bound to the ChannelControlPanels it
// contains. Everything should be doable by event-passing between the various
// histograms without using this as a go-between.
public final class HistogramsPanel extends InspectorPanel {
   private Inspector inspector_;

   // Maps displays to the histograms for those displays. We need one histogram
   // for every channel in every open display window, so that they can link
   // between each other correctly.
   // TODO: this is potentially inefficient in the use case where there are
   // multiple Inspector windows open, as each will have a complete set of
   // histograms. Make this a static singleton, maybe?
   private HashMap<DisplayWindow, ArrayList<ChannelControlPanel>> displayToPanels_;
   // The current active (displayed) set of histograms.
   private ArrayList<ChannelControlPanel> channelPanels_;
   private Datastore store_;
   private DefaultDisplayWindow display_;
   private MMVirtualStack stack_;
   private ImagePlus ijImage_;
   private Timer histogramUpdateTimer_;
   private long lastUpdateTime_ = 0;
   private boolean updatingCombos_ = false;

   public HistogramsPanel() {
      super();
      setMinimumSize(new java.awt.Dimension(280, 0));
      displayToPanels_ = new HashMap<DisplayWindow, ArrayList<ChannelControlPanel>>();
      // Populate displayToPanels now.
      for (DisplayWindow display : DefaultDisplayManager.getInstance().getAllImageWindows()) {
         setupDisplay(display);
      }
      DefaultEventManager.getInstance().registerForEvents(this);
   }

   // Create a list of histograms for the display, and register to it and its
   // datastore for events.
   private void setupDisplay(DisplayWindow display) {
      displayToPanels_.put(display, new ArrayList<ChannelControlPanel>());
      // Check the display to see how many histograms it needs at the start.
      for (int i = 0; i < display.getDatastore().getAxisLength(Coords.CHANNEL); ++i) {
         addPanel((DefaultDisplayWindow) display, i);
      }
      display.registerForEvents(this);
      display.getDatastore().registerForEvents(this);
   }

   /**
    * Remove our existing UI, if any, and create it anew.
    */
   public synchronized void setupChannelControls() {
      removeAll();
      invalidate();

      // TODO: ignoring the possibility of RGB images for now.
      final int nChannels = store_.getAxisLength(Coords.CHANNEL);
      if (nChannels == 0) {
         ReportingUtils.logError("Have zero channels to work with.");
         return;
      }

      setLayout(new MigLayout("flowy, fillx, insets 0"));
      channelPanels_ = displayToPanels_.get(display_);
      for (ChannelControlPanel panel : channelPanels_) {
         add(panel, "grow, gap 0");
      }

      validate();
      // TODO: for some reason if we don't manually repaint at this stage,
      // the link button(s) won't redraw (which can make it look like they have
      // the wrong icons). Everything *else* redraws fine, but the link buttons
      // don't.
      repaint();
      inspector_.relayout();
   }

   private void addPanel(DefaultDisplayWindow display, int channelIndex) {
      ChannelControlPanel panel = new ChannelControlPanel(channelIndex, this,
            display.getDatastore(), display, display.getStack(),
            display.getImagePlus());
      displayToPanels_.get(display).add(panel);
      if (display == display_) {
         // Also add the panel to our contents.
         add(panel, "grow, gap 0");
      }
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

   public boolean amInCompositeMode() {
      return ((ijImage_ instanceof CompositeImage) &&
            ((CompositeImage) ijImage_).getMode() != CompositeImage.COMPOSITE);
   }

   public boolean amMultiChannel() {
      return (ijImage_ instanceof CompositeImage);
   }

   private double getHistogramUpdateRate() {
      DisplaySettings settings = display_.getDisplaySettings();
      if (settings == null || settings.getHistogramUpdateRate() == null) {
         // Assume we update every time.
         return 0;
      }
      return settings.getHistogramUpdateRate();
   }

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

   @Subscribe
   public synchronized void onNewImage(NewImageEvent event) {
      try {
         // Make certain we have enough histograms for the relevant display(s).
         Datastore store = event.getDatastore();
         List<DisplayWindow> displays = DisplayGroupManager.getDisplaysForDatastore(store);
         for (DisplayWindow display : displays) {
            ArrayList<ChannelControlPanel> panels = displayToPanels_.get(display);
            if (display.getDatastore() == store) {
               while (event.getImage().getCoords().getIndex("channel") >= panels.size()) {
                  // Need to add a new channel histogram. Note that this will
                  // modify the "panels" object's length, incrementing the
                  // value returned by panels.size() here and ensuring the
                  // while loop continues.
                  addPanel((DefaultDisplayWindow) display, panels.size());
               }
            }
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error adjusting histograms to new image");
      }
   }

   @Subscribe
   public void onPixelsSet(PixelsSetEvent event) {
      if (event.getDisplay() == display_) {
         calcAndDisplayHistAndStats();
      }
   }

   // A new display has arrived, so we need to start tracking its histograms.
   @Subscribe
   public synchronized void onNewDisplay(DisplayAboutToShowEvent event) {
      try {
         setupDisplay(event.getDisplay());
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Unable to set up new display's histograms");
      }
   }

   @Subscribe
   public synchronized void onDisplayDestroyed(DisplayDestroyedEvent event) {
      DisplayWindow display = event.getDisplay();
      if (!displayToPanels_.containsKey(display)) {
         // This should never happen.
         ReportingUtils.logError("Got notified of a display being destroyed when we don't know about that display");
         return;
      }
      try {
         ArrayList<ChannelControlPanel> panels = displayToPanels_.get(display);
         for (ChannelControlPanel panel : panels) {
            panel.cleanup();
         }
         display.unregisterForEvents(this);
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error during histograms cleanup");
      }
      // If that was the last display for that datastore, then we should also
      // unregister to that datastore.
      displayToPanels_.remove(display);
      boolean shouldKeep = false;
      for (DisplayWindow alt : displayToPanels_.keySet()) {
         if (alt.getDatastore() == display.getDatastore()) {
            shouldKeep = true;
         }
      }
      if (!shouldKeep) {
         // Couldn't find any other displays using that datastore.
         display.getDatastore().unregisterForEvents(this);
      }
   }

   @Override
   public JPopupMenu getGearMenu() {
      JPopupMenu result = new JPopupMenu();
      if (display_ == null) {
         // Nothing to be done yet.
         return result;
      }
      final DisplaySettings settings = display_.getDisplaySettings();

      // Add option for turning log display on/off.
      JCheckBoxMenuItem logDisplay = new JCheckBoxMenuItem("Logarithmic Y axis");
      final Boolean shouldLog = settings.getShouldUseLogScale();
      if (shouldLog != null) {
         logDisplay.setState(shouldLog);
      }
      logDisplay.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // Invert current setting; null counts as false here.
            Boolean newVal = !(shouldLog == null || shouldLog);
            DisplaySettings newSettings = settings.copy().shouldUseLogScale(newVal).build();
            display_.setDisplaySettings(newSettings);
         }
      });
      result.add(logDisplay);

      return result;
   }

   @Override
   public synchronized void setDisplay(DisplayWindow display) {
      display_ = (DefaultDisplayWindow) display;
      if (display_ == null) {
         removeAll();
         invalidate();
         return;
      }
      store_ = display_.getDatastore();
      stack_ = display_.getStack();
      ijImage_ = display_.getImagePlus();
      setupChannelControls();
   }

   @Override
   public void setInspector(Inspector inspector) {
      inspector_ = inspector;
   }

   @Override
   public synchronized void cleanup() {
      DefaultEventManager.getInstance().unregisterForEvents(this);
      HashSet<Datastore> stores = new HashSet<Datastore>();
      for (DisplayWindow display : displayToPanels_.keySet()) {
         display.unregisterForEvents(this);
         stores.add(display.getDatastore());
      }
      for (Datastore store : stores) {
         store.unregisterForEvents(this);
      }
   }
}
