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
import java.util.TimerTask;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.NewImageEvent;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.NewImagePlusEvent;

import org.micromanager.display.DisplayWindow;
import org.micromanager.display.Inspector;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.internal.events.DefaultRequestToDrawEvent;
import org.micromanager.display.internal.events.LUTUpdateEvent;
import org.micromanager.display.internal.DefaultDisplayManager;
import org.micromanager.display.internal.DefaultDisplaySettings;
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
   private HashMap<DisplayWindow, ArrayList<ChannelControlPanel>> displayToPanels_;
   // The current active (displayed) set of histograms.
   private ArrayList<ChannelControlPanel> channelPanels_;
   private JCheckBox shouldAutostretch_;
   private JLabel extremaLabel_;
   private JSpinner extrema_;
   private JLabel percentLabel_;

   private Object panelLock_ = new Object();
   private Datastore store_;
   private DisplayWindow viewer_;
   private Timer histogramUpdateTimer_;
   private boolean isFirstDrawEvent_ = true;
   private long lastUpdateTime_ = 0;
   private boolean updatingCombos_ = false;

   public HistogramsPanel() {
      super();
      setLayout(new MigLayout("flowy, fillx, insets 0"));
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
      synchronized(panelLock_) {
         // Check the display to see how many histograms it needs at the start.
         for (int i = 0; i < display.getDatastore().getAxisLength(Coords.CHANNEL); ++i) {
            addPanel(display, i);
         }
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

      addStandardControls();

      // TODO: ignoring the possibility of RGB images for now.
      final int nChannels = store_.getAxisLength(Coords.CHANNEL);
      if (nChannels == 0) {
         ReportingUtils.logError("Have zero channels to work with.");
         return;
      }

      channelPanels_ = displayToPanels_.get(viewer_);
      if (channelPanels_ == null) {
         // This should never happen (it means that this object didn't know
         // about the new display), but could potentially if there's a bug
         // in event unregistering.
         ReportingUtils.logError("Somehow defunct HistogramsPanel got notified of existence of " + viewer_);
         return;
      }
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
            DefaultDisplaySettings.setStandardSettings(newSettings);
            viewer_.setDisplaySettings(newSettings);
         }
      });
   }

   private void setExtremaPercentage(JSpinner extrema) {
      DisplaySettings settings = viewer_.getDisplaySettings();
      Double percentage = (Double) (extrema.getValue());
      settings = settings.copy().extremaPercentage(percentage).build();
      DefaultDisplaySettings.setStandardSettings(settings);
      viewer_.setDisplaySettings(settings);
   }

   private void addPanel(DisplayWindow display, int channelIndex) {
      ChannelControlPanel panel = new ChannelControlPanel(channelIndex,
            display.getDatastore(),
            DisplayGroupManager.getContrastLinker(channelIndex, display),
            display);
      displayToPanels_.get(display).add(panel);
      if (display == viewer_) {
         // Also add the panel to our contents, and tell our inspector frame
         // to relayout.
         add(panel, "grow, gap 0");
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
   public synchronized void onNewImage(NewImageEvent event) {
      try {
         // Make certain we have enough histograms for the relevant display(s).
         Datastore store = event.getDatastore();
         List<DisplayWindow> displays = new ArrayList<DisplayWindow>(DisplayGroupManager.getDisplaysForDatastore(store));
         for (DisplayWindow display : displays) {
            if (display.getDatastore() != store) {
               continue;
            }
            ArrayList<ChannelControlPanel> panels = displayToPanels_.get(display);
            synchronized(panelLock_) {
               Coords imageCoords = event.getImage().getCoords();
               while (imageCoords.getChannel() >= panels.size()) {
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
   public synchronized void onNewDisplaySettings(NewDisplaySettingsEvent event) {
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
      if (viewer_ == null) {
         // Nothing to be done yet.
         return result;
      }
      final DisplaySettings settings = viewer_.getDisplaySettings();

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
            viewer_.setDisplaySettings(newSettings);
         }
      });
      result.add(logDisplay);

      // The display mode menu opens a sub-menu with three options, one of
      // which starts selected depending on the current color settings of the
      // display.
      JMenu displayMode = new JMenu("Color presets");
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

      JMenu updateRate = new JMenu("Histogram update rate");

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
      updateRate.add(never);
      updateRate.add(oncePerSec);
      result.add(updateRate);

      return result;
   }

   @Override
   public InspectorPanel.DisplayRequirement getDisplayRequirement() {
      return InspectorPanel.DisplayRequirement.DISPLAY_WINDOW;
   }

   @Override
   public synchronized void setDisplayWindow(DisplayWindow viewer) {
      viewer_ = viewer;
      if (viewer_ == null) {
         removeAll();
         invalidate();
         return;
      }
      store_ = viewer_.getDatastore();
      setupChannelControls();
      revalidate();
      inspector_.relayout();
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
