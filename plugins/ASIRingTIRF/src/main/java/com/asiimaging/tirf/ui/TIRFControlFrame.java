/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.ui;

import com.asiimaging.tirf.TIRFControlPlugin;
import com.asiimaging.tirf.model.TIRFControlModel;
import com.asiimaging.tirf.model.data.Icons;
import com.asiimaging.tirf.model.devices.Scanner;
import com.asiimaging.tirf.ui.components.Button;
import com.asiimaging.tirf.ui.components.Label;
import com.asiimaging.tirf.ui.panels.ButtonPanel;
import com.asiimaging.tirf.ui.panels.TabPanel;
import com.asiimaging.tirf.ui.utils.BrowserUtils;
import com.asiimaging.tirf.ui.utils.DialogUtils;
import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.events.ExposureChangedEvent;
import org.micromanager.events.LiveModeEvent;
import org.micromanager.internal.utils.WindowPositioning;

public class TIRFControlFrame extends JFrame {

   public static final boolean DEBUG = false;

   private final Studio studio;

   private TabPanel tabPanel;
   private ButtonPanel buttonPanel;

   private TIRFControlModel model;

   public TIRFControlFrame(final Studio studio) {
      this.studio = studio;

      // save/load window position
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), this.getClass().getSimpleName());
   }

   /**
    * Creates the user interface for when an error occurs in the plugin.
    */
   public void createErrorInterface() {
      // frame settings
      setTitle(TIRFControlPlugin.menuName);
      setResizable(false);

      // use MigLayout as the layout manager
      setLayout(new MigLayout(
            "insets 20 50 20 50",
            "[]0[]",
            "[]10[]"
      ));

      // draw the title in bold
      final JLabel lblTitle = new JLabel(TIRFControlPlugin.menuName + ": Error");
      lblTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));

      final JLabel lblError =
            new JLabel("<html>This plugin requires an <b>ASITiger</b> controller.</html>");
      lblError.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

      final JLabel lblHelp = new JLabel("<html>An ASI scanner with the <b>FAST_CIRCLES</b><br>"
            + " firmware module is also required.</html>");
      lblHelp.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

      final Button btnWebsite = new Button("Website", 120, 30);
      final Button btnManual = new Button("Manual", 120, 30);

      final JLabel lblTigerFound = new JLabel("");
      final JLabel lblFastCirclesFound = new JLabel("");

      if (model.isTigerDevice()) {
         lblTigerFound.setText("Tiger Controller Found");
         lblTigerFound.setForeground(Color.GREEN);
      } else {
         lblTigerFound.setText("Tiger Controller Not Found");
         lblTigerFound.setForeground(Color.RED);
      }
      if (model.getScanner().hasFastCirclesModule()) {
         lblFastCirclesFound.setText("FAST_CIRCLES Found");
         lblFastCirclesFound.setForeground(Color.GREEN);
      } else {
         lblFastCirclesFound.setText("FAST_CIRCLES Not Found");
         lblFastCirclesFound.setForeground(Color.RED);
      }

      btnWebsite.registerListener(e -> {
         final boolean result = DialogUtils.showYesNoDialog(btnManual,
               "Open Browser", "Would you like to navigate to the product website?");
         if (result) {
            BrowserUtils.openWebsite(studio,
                  "https://www.asiimaging.com/products/light-sheet-microscopy/fiber-coupled-laser-scanner");
         }
      });

      btnManual.registerListener(e -> {
         final boolean result = DialogUtils.showYesNoDialog(btnManual,
               "Open Browser", "Would you like to navigate to the plugin manual?");
         if (result) {
            BrowserUtils.openWebsite(studio,
                  "https://www.asiimaging.com/docs/ringtirf");
         }
      });

      add(lblTitle, "align center, wrap");
      add(lblError, "align center, wrap");
      add(lblHelp, "align center, wrap");
      add(lblTigerFound, "align center, wrap");
      add(lblFastCirclesFound, "align center, wrap");
      add(btnWebsite, "split 2, align center");
      add(btnManual, "align center");

      pack(); // set the window size automatically
      setIconImage(Icons.MICROSCOPE.getImage());

      // clean up resources when the frame is closed
      setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
   }

   public void createUserInterface() {
      setTitle(TIRFControlPlugin.menuName);
      setResizable(false);

      // use MigLayout as the layout manager
      setLayout(new MigLayout(
            "insets 20 30 20 20",
            "[]20[]",
            "[]10[]"
      ));

      final Label lblTitle = new Label(TIRFControlPlugin.menuName);
      lblTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));

      // ui elements
      tabPanel = new TabPanel(model, this);
      buttonPanel = new ButtonPanel(model, this);

      // register for events
      studio.events().registerForEvents(this);

      // window closing method
      registerWindowEventHandlers();

      // add ui elements to the panel
      add(lblTitle, "wrap");
      add(tabPanel, "wrap");
      add(buttonPanel, "");

      pack(); // set the window size automatically
      setIconImage(Icons.MICROSCOPE.getImage());

      // clean up resources when the frame is closed
      setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

      // set up the PLogic card for fast circles
      // Note: setupPLogic() should only be run after the user settings have been loaded
      final boolean result = DialogUtils.showYesNoDialog(this, "PLogic Card Settings",
            "Would you like to send settings to the PLogic device to setup the plugin?");
      if (result) {
         model.setupPLogic();
      }
   }

   /**
    * This method is called when the frame closes.
    */
   private void registerWindowEventHandlers() {
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(final WindowEvent event) {
            model.getUserSettings().save(model);
            model.getScanner().setBeamEnabled(false);
            model.getScanner().setFastCirclesState(Scanner.Values.FastCirclesState.OFF);
            studio.logs().logDebugMessage("plugin closed");
         }
      });
   }

   /**
    * Enable or disable the live mode window.
    */
   public void toggleLiveMode() {
      // set to internal trigger mode
      if (model.getCamera().isSupported()) {
         if (model.getCamera().isTriggerModeExternal()) {
            model.getCamera().setTriggerModeInternal();
         }
      }
      // toggle live mode
      if (studio.live().isLiveModeOn()) {
         studio.live().setLiveModeOn(false);
         // close the live mode window if it exists
         if (studio.live().getDisplay() != null) {
            studio.live().getDisplay().close();
         }
      } else {
         studio.live().setLiveModeOn(true);
      }
   }

   public void setModel(final TIRFControlModel model) {
      this.model = model;
   }

   public TabPanel getTabPanel() {
      return tabPanel;
   }

   public ButtonPanel getButtonPanel() {
      return buttonPanel;
   }

   @Subscribe
   public void liveModeListener(LiveModeEvent event) {
      if (!studio.live().isLiveModeOn()) {
         buttonPanel.getLiveModeButton().setState(false);
      }
   }

   // Note: this does not work for all cameras
   @Subscribe
   public void onExposureChanged(ExposureChangedEvent event) {
      tabPanel.getScannerTab().updateFastCirclesHzLabel();
   }

}