/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal;

import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.JFrame;
import mmcorej.MMCoreJ;
import org.micromanager.events.GUIRefreshEvent;
import org.micromanager.internal.dialogs.AcqControlDlg;
import org.micromanager.internal.dialogs.CalibrationListDlg;
import org.micromanager.internal.menus.MMMenuBar;
import org.micromanager.internal.pipelineinterface.PipelineFrame;
import org.micromanager.internal.script.ScriptPanel;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author nicke
 */
public class MMUIManager {
   private PropertyEditor propertyBrowser_;
   private CalibrationListDlg calibrationListDlg_;
   private AcqControlDlg acqControlWin_;
   private ScriptPanel scriptPanel_;
   private PipelineFrame pipelineFrame_;    
   private MMMenuBar mmMenuBar_;  // Our menubar
   private MainFrame frame_;// Our primary window.
   private final MMStudio studio_;

   public MMUIManager(MMStudio studio) {
      studio_ = studio;
   }

   public void createPropertyEditor() {
      if (propertyBrowser_ != null) {
         propertyBrowser_.dispose();
      }

      propertyBrowser_ = new PropertyEditor(studio_);
      studio_.events().registerForEvents(propertyBrowser_);
      propertyBrowser_.setVisible(true);
      propertyBrowser_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
   }

   public void createCalibrationListDlg() {
      if (calibrationListDlg_ != null) {
         calibrationListDlg_.dispose();
      }

      calibrationListDlg_ = new CalibrationListDlg(studio_.core());
      calibrationListDlg_.setVisible(true);
      calibrationListDlg_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      calibrationListDlg_.setParentGUI(studio_);
   }

   public CalibrationListDlg getCalibrationListDlg() {
      if (calibrationListDlg_ == null) {
         createCalibrationListDlg();
      }
      return calibrationListDlg_;
   }

   public void createScriptPanel() {
      scriptPanel_ = new ScriptPanel(studio_);
   }

   public ScriptPanel getScriptPanel() {
      return scriptPanel_;
   }

   public void createPipelineFrame() {
      if (pipelineFrame_ == null) { //Create the pipelineframe if it hasn't already been done.
         pipelineFrame_ = new PipelineFrame(studio_);
      }
   }

   public PipelineFrame getPipelineFrame() {
      return pipelineFrame_;
   }

   public AcqControlDlg getAcquisitionWindow() {
      if (acqControlWin_ == null) {
         acqControlWin_ = new AcqControlDlg(studio_.getAcquisitionEngine(), studio_);
      }
      return acqControlWin_;
   }

   public void openAcqControlDialog() {
      try {
         if (acqControlWin_ == null) {
            acqControlWin_ = new AcqControlDlg(studio_.getAcquisitionEngine(), studio_);
         }
         if (acqControlWin_.isActive()) {
            acqControlWin_.setTopPosition();
         }

         acqControlWin_.setVisible(true);

         acqControlWin_.repaint();

      } catch (Exception exc) {
         ReportingUtils.showError(exc,
               "\nAcquisition window failed to open due to invalid or corrupted settings.\n"
               + "Try resetting registry settings to factory defaults (Menu Tools|Options).");
      }
   }

   public void updateChannelCombos() {
      if (acqControlWin_ != null) {
         acqControlWin_.updateChannelAndGroupCombo();
      }
   }

   public void showPipelineFrame() {
      pipelineFrame_.setVisible(true);
   }

   public void showScriptPanel() {
      scriptPanel_.setVisible(true);
   }

   public void cleanupOnClose() {
      if (scriptPanel_ != null) {
         scriptPanel_.closePanel();
         scriptPanel_ = null;
      }

      if (pipelineFrame_ != null) {
         pipelineFrame_.dispose();
      }

      if (propertyBrowser_ != null) {
         propertyBrowser_.getToolkit().getSystemEventQueue().postEvent(
                 new WindowEvent(propertyBrowser_, WindowEvent.WINDOW_CLOSING));
         propertyBrowser_.dispose();
      }

      if (acqControlWin_ != null) {
         acqControlWin_.close();
      }
   }

   public void close() {
      if (frame_ != null) {
         frame_.dispose();
         frame_ = null;
      }
   }

   public void createMainWindow() {
      mmMenuBar_ = MMMenuBar.createMenuBar(studio_);
      frame_ = new MainFrame(studio_, studio_.core());
      frame_.toFront();
      frame_.setVisible(true);
      ReportingUtils.SetContainingFrame(frame_);
      frame_.initializeConfigPad();
   }

   public MainFrame frame() {
      //TODO check if null and raise error.
      return frame_;
   }

   public MMMenuBar menubar() {
      return mmMenuBar_;
   }

   public void promptToSaveConfigPresets() {
      File f = FileDialogs.save(frame_,
            "Save the configuration file", FileDialogs.MM_CONFIG_FILE);
      if (f != null) {
         try {
            studio_.app().saveConfigPresets(f.getAbsolutePath(), true);
         }
         catch (IOException e) {
            // This should be impossible as we set shouldOverwrite to true.
            studio_.logs().logError(e, "Error saving config presets");
         }
      }
   }

   public void updateGUI(boolean updateConfigPadStructure) {
      updateGUI(updateConfigPadStructure, false);
   }

   public void updateGUI(boolean updateConfigPadStructure, boolean fromCache) {
      ReportingUtils.logMessage("Updating GUI; config pad = " +
            updateConfigPadStructure + "; from cache = " + fromCache);
      try {
         studio_.cache().refreshValues();
         studio_.getAutofocusManager().refresh();

         if (!fromCache) { // The rest of this function uses the cached property values. If `fromCache` is false, start by updating all properties in the cache.
            studio_.core().updateSystemStateCache();
         }

         // camera settings
         if (studio_.cache().getCameraLabel().length() > 0) {
            double exp = studio_.core().getExposure();
            frame_.setDisplayedExposureTime(exp);
            configureBinningCombo();
            String binSize = studio_.core().getPropertyFromCache(studio_.cache().getCameraLabel(), MMCoreJ.getG_Keyword_Binning());
            frame_.setBinSize(binSize);
         }

         frame_.updateAutofocusButtons(studio_.getAutofocusManager().getAutofocusMethod() != null);

         ConfigGroupPad pad = frame_.getConfigPad();
         // state devices
         if (updateConfigPadStructure && (pad != null)) {
            pad.refreshStructure(true);
         }

         // update Channel menus in Multi-dimensional acquisition dialog
         updateChannelCombos();

         // update list of pixel sizes in pixel size configuration window
         if (calibrationListDlg_ != null) {
            calibrationListDlg_.refreshCalibrations();
         }
         if (propertyBrowser_ != null) {
            propertyBrowser_.refresh(true);
         }

         ReportingUtils.logMessage("Finished updating GUI");
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
      frame_.setConfigText(studio_.getSysConfigFile());
      studio_.events().post(new GUIRefreshEvent());
   }
   
   public void configureBinningCombo() throws Exception {
      if (studio_.cache().getCameraLabel().length() > 0) {
         frame_.configureBinningComboForCamera(studio_.cache().getCameraLabel());
      }
   }
} 