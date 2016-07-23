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


import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.NewImageEvent;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.Inspector;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.NewDisplaySettingsEvent;

import org.micromanager.display.internal.events.ViewerAddedEvent;
import org.micromanager.display.internal.events.ViewerRemovedEvent;
import org.micromanager.display.internal.DefaultDisplayManager;
import org.micromanager.display.internal.link.DisplayGroupManager;
import org.micromanager.display.PixelsSetEvent;

import org.micromanager.events.internal.DefaultEventManager;

import org.micromanager.internal.utils.ReportingUtils;

// This class tracks all histograms for all displays in a given inspector
// window.
// HACK: I don't think we really need this class to be so tightly-bound to the
// ChannelControlPanels it contains. Everything should be doable by
// event-passing between the various histograms without using this as a
// go-between.
public final class HistogramsPanel extends InspectorPanel {
   public static final Color[] RGB_COLORS = new Color[] {
         Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA,
         Color.YELLOW, Color.WHITE};
   // Colors adapted from table at
   // http://www.nature.com/nmeth/journal/v8/n6/full/nmeth.1618.html
   // Selection of the first three colors based on recommendations from
   // Ankur Jain at the Vale lab.
   public static final Color[] COLORBLIND_COLORS = new Color[] {
         new Color(0, 114, 178), new Color(213, 94, 0),
         new Color(0, 158, 115), Color.RED, Color.CYAN, Color.YELLOW,
         Color.WHITE};

   private Inspector inspector_;

   // Maps displays to the histograms for those displays. We need one histogram
   // for every channel in every open display window, so that they can link
   // between each other correctly.
   // TODO: this is potentially inefficient in the use case where there are
   // multiple Inspector windows open, as each will have a complete set of
   // histograms. Make this a static singleton, maybe?
   private final HashMap<DataViewer, ArrayList<ChannelControlPanel>> displayToPanels_;
   private JCheckBox shouldAutostretch_;
   private JCheckBoxMenuItem shouldScaleWithROI;
   private JLabel extremaLabel_;
   private JSpinner extrema_;
   private JLabel percentLabel_;

   private final Object panelLock_ = new Object();
   private Datastore store_;
   private DataViewer viewer_;
   private boolean isFirstDrawEvent_ = true;

   public HistogramsPanel() {
      super();
      setLayout(new MigLayout("flowy, fill, insets 0"));
      setMinimumSize(new java.awt.Dimension(280, 0));
      displayToPanels_ = new HashMap<DataViewer, ArrayList<ChannelControlPanel>>();
      // Populate displayToPanels now.
      for (DataViewer display : DefaultDisplayManager.getInstance().getAllDataViewers()) {
         setupDisplay(display);
      }
      DefaultEventManager.getInstance().registerForEvents(this);
   }

   // Create a list of histograms for the display, and register to it and its
   // datastore for events.
   private void setupDisplay(final DataViewer display) {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               setupDisplay(display);
            }
         });
         return;
      }
      displayToPanels_.put(display, new ArrayList<ChannelControlPanel>());
      // Need to register *before* we manually add panels, in case any images
      // arrive while we're doing our setup.
      display.registerForEvents(this);
      display.getDatastore().registerForEvents(this);
      if (display.getDatastore().getAnyImage() == null) {
         // No need to create any panels yet.
         return;
      }
      synchronized(panelLock_) {
         // Check the display to see how many histograms it needs at the start.
         // If there's no channel axis, then it gets 1 histogram.
         int numChannels = Math.max(1,
               display.getDatastore().getAxisLength(Coords.CHANNEL));
         for (int i = 0; i < numChannels; ++i) {
            addPanel(display, i);
         }
      }
   }

   /**
    * Remove our existing UI, if any, and create it anew.
    */
   public synchronized void setupChannelControls() {
      removeAll();
      invalidate();

      addStandardControls();

      if (store_.getAnyImage() == null) {
         return;
      }

      List<ChannelControlPanel> panels = displayToPanels_.get(viewer_);
      if (panels == null) {
         // This should never happen (it means that this object didn't know
         // about the new display), but could potentially if there's a bug
         // in event unregistering.
         ReportingUtils.logError("Somehow defunct HistogramsPanel got notified of existence of " + viewer_);
         return;
      }
      for (ChannelControlPanel panel : panels) {
         add(panel, "grow, gap 0, pushy 100");
      }

      validate();
      // TODO: for some reason if we don't manually repaint at this stage,
      // the link button(s) won't redraw (which can make it look like they have
      // the wrong icons). Everything *else* redraws fine, but the link buttons
      // don't.
      repaint();
   }

   /**
    * Set up the controls that are standard across all histograms: the display
    * mode, autostretch, and extrema% controls.
    */
   private synchronized void addStandardControls() {
      DisplaySettings settings = viewer_.getDisplaySettings();

      // HACK: we set these labels manually to null now, so that if the
      // display settings change while making controls (e.g. because we need
      // to set the initial color display mode), we can recognize that they're
      // invalid in the NewDisplaySettingsEvent handler.
      shouldAutostretch_ = null;
      extremaLabel_ = null;
      extrema_ = null;
      percentLabel_ = null;

      add(new JLabel("Color mode: "), "split 2, flowx, gapleft 15");
      ColorModeCombo colorModeCombo = new ColorModeCombo(viewer_);
      add(colorModeCombo, "align right");
      
      boolean shouldAutostretchSetting = true;
      if (viewer_.getDisplaySettings().getShouldAutostretch() != null) {
         shouldAutostretchSetting = viewer_.getDisplaySettings().getShouldAutostretch();
      }
      shouldAutostretch_ = new JCheckBox("Autostretch");
      shouldAutostretch_.setSelected(shouldAutostretchSetting);
      shouldAutostretch_.setToolTipText("Automatically rescale the histograms every time a new image is displayed.");
      add(shouldAutostretch_, "split 4, flowx, gapleft 10");
      extremaLabel_ = new JLabel("(Ignore");
      extremaLabel_.setEnabled(shouldAutostretchSetting);
      add(extremaLabel_);
      extrema_ = new JSpinner();
      extrema_.setToolTipText("Ignore the top and bottom percentage of the image when autostretching.");
      // Going to 50% would mean the entire image is ignored.
      extrema_.setModel(new SpinnerNumberModel(0.0, 0.0, 49.999, 0.1));
      if (settings.getExtremaPercentage() != null) {
         extrema_.setValue(settings.getExtremaPercentage());
      }
      extrema_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            setExtremaPercentage(extrema_);
         }
      });
      extrema_.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent e) {
            setExtremaPercentage(extrema_);
         }
      });
      extrema_.setEnabled(shouldAutostretchSetting);
      add(extrema_);
      percentLabel_ = new JLabel("%)");
      percentLabel_.setEnabled(shouldAutostretchSetting);
      add(percentLabel_);

      shouldAutostretch_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            boolean newVal = shouldAutostretch_.isSelected();
            DisplaySettings newSettings = viewer_.getDisplaySettings()
                  .copy().shouldAutostretch(newVal).build();
            viewer_.setDisplaySettings(newSettings);
         }
      });
      
   }

   private void setExtremaPercentage(JSpinner extrema) {
      DisplaySettings settings = viewer_.getDisplaySettings();
      Double percentage = (Double) (extrema.getValue());
      settings = settings.copy().extremaPercentage(percentage).build();
      viewer_.setDisplaySettings(settings);
   }

   private void addPanel(DataViewer display, int channelIndex) {
      if (!SwingUtilities.isEventDispatchThread()) {
         throw new RuntimeException("Don't create histograms outside the EDT!");
      }
      ChannelControlPanel panel = new ChannelControlPanel(this,
            channelIndex, display.getDatastore(),
            DisplayGroupManager.getContrastLinker(channelIndex, display),
            display);
      displayToPanels_.get(display).add(panel);
      if (display == viewer_) {
         add(panel, "grow, gap 0, pushy 100");
         revalidate();
         inspector_.relayout();
      }
   }

   private double getHistogramUpdateRate() {
      DisplaySettings settings = viewer_.getDisplaySettings();
      if (settings == null || settings.getHistogramUpdateRate() == null) {
         // Assume we update every time.
         return 0;
      }
      return settings.getHistogramUpdateRate();
   }

   @Subscribe
   public void onNewImage(final NewImageEvent event) {
      // This may add new histogram panels, so only run it on the EDT.
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               onNewImage(event);
            }
         });
         return;
      }
      try {
         // Make certain we have enough histograms for the relevant display(s).
         Datastore store = event.getDatastore();
         List<DataViewer> displays = new ArrayList<DataViewer>(DisplayGroupManager.getDisplaysForDatastore(store));
         for (DataViewer display : displays) {
            ArrayList<ChannelControlPanel> panels = displayToPanels_.get(display);
            synchronized(panelLock_) {
               // HACK: no-channel-axis datasets get 1 histogram.
               int channel = Math.max(0, event.getImage().getCoords().getChannel());
               while (channel >= panels.size()) {
                  // Need to add a new channel histogram. Note that this will
                  // modify the "panels" object's length, incrementing the
                  // value returned by panels.size() here and ensuring the
                  // while loop continues.
                  addPanel(display, panels.size());
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
      try {
         if (event.getDisplay() == viewer_) {
            // HACK: the inspector window can have the incorrect size (somewhat
            // too small) if sized prior to histograms having been drawn. So we
            // force a relayout the first time we receive this event.
            if (isFirstDrawEvent_) {
               inspector_.relayout();
               isFirstDrawEvent_ = false;
            }
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error updating histograms to match image");
      }
   }

   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      try {
         if (event.getDisplay() == viewer_) {
            // Check if autostretch is modified.
            DisplaySettings settings = event.getDisplaySettings();
            Boolean stretchSetting = settings.getShouldAutostretch();
            // This method may be called as a side-effect of us creating
            // our controls, so make certain the things we need to modify
            // actually exist at this point.
            if (stretchSetting != null && shouldAutostretch_ != null &&
                  extremaLabel_ != null && extrema_ != null &&
                  percentLabel_ != null) {
               shouldAutostretch_.setSelected(stretchSetting);
               extremaLabel_.setEnabled(stretchSetting);
               extrema_.setEnabled(stretchSetting);
               percentLabel_.setEnabled(stretchSetting);
            }
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error on new display settings");
      }
   }

   // An external display has been added, so we need to start tracking its
   // histograms.
   @Subscribe
   public void onViewerAdded(ViewerAddedEvent event) {
      try {
         setupDisplay(event.getViewer());
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Unable to set up new display's histograms");
      }
   }

   @Subscribe
   public void onViewerRemoved(ViewerRemovedEvent event) {
      removeDisplay(event.getViewer());
   }

   private void removeDisplay(DataViewer display) {
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
      for (DataViewer alt : displayToPanels_.keySet()) {
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
      if (viewer_ == null) {
         // Nothing to be done yet.
         return result;
      }
      final DisplaySettings settings = viewer_.getDisplaySettings();

      // Add option for turning log display on/off.
      JCheckBoxMenuItem logDisplay = new JCheckBoxMenuItem("Logarithmic Y axis");
      final Boolean shouldLog = settings.getShouldUseLogScale() != null ?
         settings.getShouldUseLogScale() : false;
      logDisplay.setState(shouldLog);
      logDisplay.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            DisplaySettings newSettings = settings.copy()
               .shouldUseLogScale(!shouldLog).build();
            viewer_.setDisplaySettings(newSettings);
         }
      });
      result.add(logDisplay);

      // Whether or not to include standard deviation in histogram
      // calculations.
      JCheckBoxMenuItem calcStdDev = new JCheckBoxMenuItem("Calculate Standard Deviation");
      calcStdDev.setToolTipText("Calculate standard deviations of image data. This may reduce your image refresh rate.");
      final Boolean shouldCalc = settings.getShouldCalculateStdDev() != null ?
         settings.getShouldCalculateStdDev() : false;
      calcStdDev.setState(shouldCalc);
      calcStdDev.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            DisplaySettings newSettings = settings.copy()
               .shouldCalculateStdDev(!shouldCalc).build();
            viewer_.setDisplaySettings(newSettings);
         }
      });
      result.add(calcStdDev);

      // The display mode menu opens a sub-menu with three options, one of
      // which starts selected depending on the current color settings of the
      // display.
      JMenu displayMode = new JMenu("Color Presets");
      JCheckBoxMenuItem rgb = new JCheckBoxMenuItem("RGBCMYW");
      rgb.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            DisplaySettings newSettings = settings.copy().channelColors(RGB_COLORS).build();
            viewer_.setDisplaySettings(newSettings);
         }
      });
      JCheckBoxMenuItem colorblind = new JCheckBoxMenuItem("Colorblind-friendly");
      colorblind.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            DisplaySettings newSettings = settings.copy().channelColors(COLORBLIND_COLORS).build();
            viewer_.setDisplaySettings(newSettings);
         }
      });
      JCheckBoxMenuItem custom = new JCheckBoxMenuItem("Custom");
      // This menu item doesn't actually do anything.
      custom.setEnabled(false);
      Color[] channelColors = settings.getChannelColors();
      if (channelColors != null) {
         boolean isRGB = true;
         boolean isColorblind = true;
         for (int i = 0; i < channelColors.length; ++i) {
            if (channelColors[i] == null) {
               isRGB = false;
               isColorblind = false;
               break;
            }
            if (!channelColors[i].equals(RGB_COLORS[i])) {
               isRGB = false;
            }
            if (!channelColors[i].equals(COLORBLIND_COLORS[i])) {
               isColorblind = false;
            }
         }
         if (isRGB) {
            rgb.setState(true);
         }
         else if (isColorblind) {
            colorblind.setState(true);
         }
         else {
            custom.setState(true);
         }
      }
      // else TODO: if there's no colors, our defaults (per
      // ChannelControlPanel) are the Colorblind set, so that option ought to
      // be checked, but should this module really know about that?
      displayMode.add(rgb);
      displayMode.add(colorblind);
      displayMode.add(custom);
      result.add(displayMode);

      JMenu updateRate = new JMenu("Histogram Update Rate");

      JCheckBoxMenuItem never = new JCheckBoxMenuItem("Never");
      never.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            DisplaySettings newSettings = settings.copy().histogramUpdateRate(-1.0).build();
            viewer_.setDisplaySettings(newSettings);
         }
      });
      JCheckBoxMenuItem always = new JCheckBoxMenuItem("Every Image");
      always.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            DisplaySettings newSettings = settings.copy().histogramUpdateRate(0.0).build();
            viewer_.setDisplaySettings(newSettings);
         }
      });
      JCheckBoxMenuItem oncePerSec = new JCheckBoxMenuItem("Once per Second");
      oncePerSec.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            DisplaySettings newSettings = settings.copy().histogramUpdateRate(1.0).build();
            viewer_.setDisplaySettings(newSettings);
         }
      });

      // Determine which of them should be checked, if any.
      Double curRate = settings.getHistogramUpdateRate();
      if (curRate == null || curRate == 0) {
         always.setState(true);
      }
      else if (curRate == -1) {
         never.setState(true);
      }
      else if (curRate == 1) {
         oncePerSec.setState(true);
      }
      // Else must be a custom value.

      updateRate.add(always);
      updateRate.add(oncePerSec);
      updateRate.add(never);
      result.add(updateRate);
      
      boolean shouldScaleWithROISetting = true;
      if (viewer_.getDisplaySettings().getShouldScaleWithROI() != null) {
         shouldScaleWithROISetting = viewer_.getDisplaySettings().getShouldScaleWithROI();
      }
      
      shouldScaleWithROI = new JCheckBoxMenuItem("Use ROI When Scaling");
      shouldScaleWithROI.setSelected(shouldScaleWithROISetting);
      shouldScaleWithROI.setToolTipText("Use the pixels inside the ROI to rescale the histograms.");
      result.add(shouldScaleWithROI);
      
      shouldScaleWithROI.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            boolean newVal = shouldScaleWithROI.isSelected();
            DisplaySettings newSettings = viewer_.getDisplaySettings()
                  .copy().shouldScaleWithROI(newVal).build();
            viewer_.setDisplaySettings(newSettings);
         }
      });

      return result;
   }

   /**
    * This is called by our ChannelControlPanels after their set up their
    * GUIs, which can only happen after they have histogram data to show.
    */
   public void relayout() {
      if (inspector_ != null) {
         inspector_.relayout();
      }
   }

   @Override
   public void setDataViewer(DataViewer viewer) {
      viewer_ = viewer;
      if (viewer_ == null) {
         removeAll();
         invalidate();
         return;
      }
      store_ = viewer_.getDatastore();
      setupChannelControls();
      validate();
      inspector_.relayout();
   }

   @Override
   public void setInspector(Inspector inspector) {
      inspector_ = inspector;
   }

   @Override
   public void cleanup() {
      DefaultEventManager.getInstance().unregisterForEvents(this);
      HashSet<Datastore> stores = new HashSet<Datastore>();
      for (DataViewer display : displayToPanels_.keySet()) {
         try {
            display.unregisterForEvents(this);
         }
         catch (IllegalArgumentException e) {
            // Must've already unregistered; ignore it.
         }
         stores.add(display.getDatastore());
      }
      for (Datastore store : stores) {
         try {
            store.unregisterForEvents(this);
         }
         catch (IllegalArgumentException e) {
            // Must've already unregistered; ignore it.
         }
      }
   }
}
