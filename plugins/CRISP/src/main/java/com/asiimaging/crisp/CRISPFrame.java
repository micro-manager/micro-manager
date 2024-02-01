/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2024, Applied Scientific Instrumentation
 */

package com.asiimaging.crisp;

import com.asiimaging.crisp.data.Icons;
import com.asiimaging.crisp.panels.ButtonPanel;
import com.asiimaging.crisp.panels.PlotPanel;
import com.asiimaging.crisp.panels.SpinnerPanel;
import com.asiimaging.crisp.panels.StatusPanel;
import com.asiimaging.crisp.utils.BrowserUtils;
import com.asiimaging.devices.crisp.CRISP;
import com.asiimaging.devices.crisp.CRISPTimer;
import com.asiimaging.devices.crisp.ControllerType;
import com.asiimaging.devices.zstage.ZStage;
import com.asiimaging.ui.Button;
import com.asiimaging.ui.Panel;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.ToolTipManager;

import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.WindowPositioning;

/**
 * The main frame that opens when the plugin is selected in Micro-Manager.
 */
public class CRISPFrame extends JFrame {

   // DEBUG => flag to turn on debug mode when editing the ui
   // use "debug" in MigLayout to see layout constraints
   public static final boolean DEBUG = false;

   private final CMMCore core;
   private final Studio studio;

   private Panel leftPanel;
   private Panel rightPanel;

   private PlotPanel plotPanel;
   private ButtonPanel buttonPanel;
   private StatusPanel statusPanel;
   private SpinnerPanel spinnerPanel;

   private final ZStage zStage;

   private final CRISP crisp;
   private final CRISPTimer timer;
   private final UserSettings settings;

   /**
    * Construct the main frame of the user interface.
    *
    * @param studio the {@link Studio} instance
    */
   public CRISPFrame(final Studio studio) {
      this.studio = Objects.requireNonNull(studio);
      this.core = studio.core();

      crisp = new CRISP(studio);
      zStage = new ZStage(studio);
      timer = new CRISPTimer(crisp);

      // save/load user settings
      settings = new UserSettings(studio, crisp, timer, this);

      // save/load window position
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), this.getClass().getSimpleName());

      // detect the device
      if (!crisp.detectDevice()) {
         createErrorInterface(); // report error to user
      } else {
         createUserInterface(); // some ui panels require both crisp and the timer
         init(); // called after ui is created because it updates the panels

         // window closing handler => only needed on device detection (error otherwise)
         createWindowClosingEventHandler();
      }
   }

   /**
    * Updates panels and starts the timer.
    */
   private void init() {
      Objects.requireNonNull(crisp);
      Objects.requireNonNull(timer);
      Objects.requireNonNull(settings);

      // find the Z stage
      zStage.findDevice();

      // query values from CRISP and update the ui
      spinnerPanel.setAxisLabelText(crisp.getAxisString());

      // the timer task updates the status panel
      timer.createTimerTask(statusPanel);

      // load settings before the polling check
      // but after we created the timer task we need
      settings.load();

      // update panels after we load the settings
      // spinner ActionListeners update the default CRISPSettings
      spinnerPanel.update();
      statusPanel.update();

      // start the timer if polling CheckBox enabled
      if (spinnerPanel.isPollingEnabled()) {
         timer.start();
      }

      // disable spinners if already focus locked
      if (crisp.isFocusLocked()) {
         spinnerPanel.setEnabledFocusLockSpinners(false);
         buttonPanel.setCalibrationButtonStates(false);
      }

      // disable update rate spinner if using old firmware
      if (crisp.getDeviceType() == ControllerType.TIGER) {
         if (crisp.getFirmwareVersion() < 3.38) {
            spinnerPanel.setEnabledUpdateRateSpinner(false);
         }
      } else {
         if (crisp.getFirmwareVersion() < 9.2
               || crisp.getFirmwareVersionLetter() < 'n') {
            spinnerPanel.setEnabledUpdateRateSpinner(false);
         }
      }

   }

   /**
    * This UI is created when the plugin encounters an error when trying to detect CRISP.
    */
   private void createErrorInterface() {
      // frame settings
      setTitle(CRISPPlugin.menuName);
      setResizable(false);

      // use MigLayout as the layout manager
      setLayout(new MigLayout(
            "insets 20 50 20 50",
            "[]0[]",
            "[]10[]"
      ));

      // draw the title in bold
      final JLabel lblTitle = new JLabel(CRISPPlugin.menuName + ": Error");
      lblTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));

      final JLabel lblError = new JLabel("This plugin requires an ASI CRISP Autofocus device.");
      lblError.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

      final JLabel lblHelp = new JLabel("<html>Add the CRISP device from the <b>ASIStage</b> or "
            + "<b>ASITiger</b><br> device adapter in the <u>Hardware Configuration Wizard</u>"
            + ".</html>");
      lblHelp.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

      final Button btnWebsite = new Button("Website", 120, 30);
      final Button btnManual = new Button("Manual", 120, 30);

      btnWebsite.registerListener(e -> BrowserUtils.openWebsite(studio,
            "https://www.asiimaging.com/products/focus-control-and-stabilization/crisp-autofocus-system"));
      btnManual.registerListener(e -> BrowserUtils.openWebsite(studio,
            "http://asiimaging.com/docs/crisp_mm_plugin"));

      add(lblTitle, "align center, wrap");
      add(lblError, "align center, wrap");
      add(lblHelp, "align center, wrap");
      add(btnWebsite, "split 2, align center");
      add(btnManual, "align center");

      pack(); // set the window size automatically
      setIconImage(Icons.MICROSCOPE.getImage());

      // clean up resources when the frame is closed
      setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
   }

   /**
    * Create the user interface for the plugin.
    */
   private void createUserInterface() {
      Objects.requireNonNull(crisp);
      Objects.requireNonNull(timer);

      // frame settings
      setTitle(CRISPPlugin.menuName);
      setResizable(false);

      // use MigLayout as the layout manager
      setLayout(new MigLayout(
            "insets 20 10 10 10",
            "",
            ""
      ));

      // draw the title in bold
      final JLabel lblTitle = new JLabel(CRISPPlugin.menuName);
      lblTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));

      Panel.setDefaultMigLayout(
            "",
            "[]10[]",
            "[]10[]"
      );

      // create panels and layouts for ui elements
      spinnerPanel = new SpinnerPanel(crisp, timer);
      buttonPanel = new ButtonPanel(crisp, timer, spinnerPanel);

      statusPanel = new StatusPanel(crisp, new MigLayout(
            "",
            "40[]10[]",
            "[]10[]"
      ));

      plotPanel = new PlotPanel(studio, this, new MigLayout(
            "",
            "[]20[]",
            ""
      ));

      // main layout panels
      leftPanel = Panel.createFromMigLayout(
            "",
            "[]10[]",
            "[]50[]"
      );
      rightPanel = Panel.createFromMigLayout(
            "",
            "",
            ""
      );

      // color the panels to make editing the ui easier
      if (DEBUG) {
         leftPanel.setBackground(Color.RED);
         rightPanel.setBackground(Color.BLUE);
         plotPanel.setBackground(Color.YELLOW);
      }

      // add sub panels to the main layout panels
      leftPanel.add(spinnerPanel, "wrap");
      leftPanel.add(statusPanel, "center");
      rightPanel.add(buttonPanel, "");

      // add ui components to the frame
      add(lblTitle, "span, center, wrap");
      add(leftPanel, "");
      add(rightPanel, "wrap");
      add(plotPanel, "span 2");

      // delay in milliseconds for tooltips to appear
      ToolTipManager.sharedInstance().setInitialDelay(500);

      pack(); // set the window size automatically
      setIconImage(Icons.MICROSCOPE.getImage());

      // clean up resources when the frame is closed
      setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
   }

   /**
    * This method is called when the main frame closes.
    * It stops the timer from polling and saves settings.
    */
   private void createWindowClosingEventHandler() {
      Objects.requireNonNull(timer);
      Objects.requireNonNull(settings);
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(final WindowEvent event) {
            timer.stop();
            settings.save();
         }
      });
   }

   public SpinnerPanel getSpinnerPanel() {
      return spinnerPanel;
   }

   public CRISPTimer getTimer() {
      return timer;
   }

   public CRISP getCRISP() {
      return crisp;
   }

   public ZStage getZStage() {
      return zStage;
   }
}
