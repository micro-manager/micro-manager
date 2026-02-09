///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nick Anthony (nickmanthony at hotmail.com)
//
// COPYRIGHT:    
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
//

package org.micromanager.internal;

import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.ToolTipManager;
import mmcorej.MMCoreJ;
import org.micromanager.PositionList;
import org.micromanager.events.internal.DefaultGUIRefreshEvent;
import org.micromanager.events.internal.DefaultPixelSizeChangedEvent;
import org.micromanager.internal.dialogs.AcqControlDlg;
import org.micromanager.internal.dialogs.CalibrationListDlg;
import org.micromanager.internal.dialogs.StageControlFrame;
import org.micromanager.internal.menus.MMMenuBar;
import org.micromanager.internal.pipelineinterface.PipelineFrame;
import org.micromanager.internal.positionlist.MMPositionListDlg;
import org.micromanager.internal.script.ScriptPanel;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * This class handles much of the User Interface.
 */
public class MMUIManager {
   private PropertyEditor propertyBrowser_;
   private CalibrationListDlg calibrationListDlg_;
   private AcqControlDlg acqControlWin_;
   private ScriptPanel scriptPanel_;
   private PipelineFrame pipelineFrame_;
   private MMMenuBar mmMenuBar_;
   private MainFrame frame_;
   private final MMStudio studio_;
   private MMPositionListDlg posListDlg_;
   private static final int TOOLTIP_DISPLAY_DURATION_MILLISECONDS = 15000;
   private static final int TOOLTIP_DISPLAY_INITIAL_DELAY_MILLISECONDS = 2000;

   /**
    * Constructs TooltipManager, and stores the studio object for later use.
    *
    * @param studio MMStudio instance.
    */
   public MMUIManager(MMStudio studio) {
      studio_ = studio;

      //Initialize Tooltips
      ToolTipManager ttManager = ToolTipManager.sharedInstance();
      ttManager.setDismissDelay(TOOLTIP_DISPLAY_DURATION_MILLISECONDS);
      ttManager.setInitialDelay(TOOLTIP_DISPLAY_INITIAL_DELAY_MILLISECONDS);
   }

   /**
    * Creates and displays the Property Browser.
    */
   public void createPropertyEditor() {
      if (propertyBrowser_ != null) {
         propertyBrowser_.dispose();
      }

      propertyBrowser_ = new PropertyEditor(studio_);
      studio_.events().registerForEvents(propertyBrowser_);
      propertyBrowser_.setVisible(true);
      propertyBrowser_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
   }

   /**
    * Creates the Pixel Size Calibration Dialog.
    */
   public void createCalibrationListDlg() {
      if (calibrationListDlg_ != null) {
         calibrationListDlg_.dispose();
      }

      calibrationListDlg_ = new CalibrationListDlg(studio_.core());
      calibrationListDlg_.setVisible(true);
      calibrationListDlg_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      calibrationListDlg_.setParentGUI(studio_);
   }

   /**
    * Provides access to the CalibrationListDlg.
    * TODO: Remove?
    *
    * @return singleton instance of the Calivration Dialog.
    */
   public CalibrationListDlg getCalibrationListDlg() {
      if (calibrationListDlg_ == null) {
         createCalibrationListDlg();
      }
      return calibrationListDlg_;
   }

   public void createScriptPanel() {
      scriptPanel_ = new ScriptPanel(studio_);
   }

   public void createStageControlFrame()  {
      StageControlFrame.createStageControl(studio_);
   }

   public ScriptPanel getScriptPanel() {
      return scriptPanel_;
   }

   /**
    * Creates the PipelineFrame that lets users set up on the fly proecssing
    * pipelines.
    */
   public void createPipelineFrame() {
      if (pipelineFrame_ == null) { //Create the pipelineframe if it hasn't already been done.
         pipelineFrame_ = new PipelineFrame(studio_);
      }
   }

   public PipelineFrame getPipelineFrame() {
      return pipelineFrame_;
   }

   /**
    * Returns the singleton instance of the Multi-Dimensional Acquisition Dialog.
    *
    * @return Singleton instance of the MDA dialog.
    */
   public AcqControlDlg getAcquisitionWindow() {
      if (acqControlWin_ == null) {
         acqControlWin_ = new AcqControlDlg(studio_);
      }
      return acqControlWin_;
   }

   /**
    * Shows the MDA dialog.  Creates it if needed.
    */
   public void openAcqControlDialog() {
      try {
         if (acqControlWin_ == null) {
            acqControlWin_ = new AcqControlDlg(studio_);
         }
         if (acqControlWin_.isActive()) {
            acqControlWin_.setEndPosition();
         }

         acqControlWin_.setVisible(true);

         acqControlWin_.repaint();

      } catch (Exception exc) {
         ReportingUtils.showError(exc,
               "\nAcquisition window failed to open due to invalid or corrupted settings.\n"
                     + "Try resetting registry settings to factory defaults (Menu Tools|Options).");
      }
   }

   /**
    * Updates the ChannelGroup and Preset UI.
    */
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

   /**
    * Closes and cleans up all UI elements.  Should be called when quitting the application.
    */
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

   /**
    * Close the Main Frame.
    */
   public void close() {
      if (frame_ != null) {
         frame_.dispose();
         frame_ = null;
      }
   }

   /**
    * Creates the Main Frame.
    */
   public void createMainWindow() {
      mmMenuBar_ = MMMenuBar.createMenuBar(studio_);
      frame_ = new MainFrame(studio_, studio_.core());
      frame_.toFront();
      frame_.setVisible(true);
      ReportingUtils.setContainingFrame(frame_);
      frame_.initializeConfigPad();
   }

   public MainFrame frame() {
      //TODO check if null and raise error.
      return frame_;
   }

   public MMMenuBar menubar() {
      return mmMenuBar_;
   }

   /**
    * Asks the users to save the (changed) configuration file.
    */
   public void promptToSaveConfigPresets() {
      File f = FileDialogs.save(frame_,
            "Save the configuration file", FileDialogs.MM_CONFIG_FILE);
      if (f != null) {
         try {
            studio_.app().saveConfigPresets(f.getAbsolutePath(), true);
         } catch (IOException e) {
            // This should be impossible as we set shouldOverwrite to true.
            studio_.logs().logError(e, "Error saving config presets");
         }
      }
   }

   public void updateGUI(boolean updateConfigPadStructure) {
      updateGUI(updateConfigPadStructure, false);
   }

   /**
    * Updates the GUI with the hardware state.
    *
    * @param updateConfigPadStructure Whether to update the Config Pad.
    * @param fromCache                When true, use the cache in the core, otherwise, ask
    *                                 the hardware (which will update the cache).
    */
   public void updateGUI(boolean updateConfigPadStructure, boolean fromCache) {
      ReportingUtils.logMessage("Updating GUI; config pad = "
            + updateConfigPadStructure + "; from cache = " + fromCache);
      try {
         double pixSizeUmPre = studio_.cache().getPixelSizeUm();
         studio_.cache().refreshValues();
         studio_.getAutofocusManager().refresh();
         double pixSizeUmPost = studio_.cache().getPixelSizeUm();
         if (pixSizeUmPre != pixSizeUmPost) {
            // Firing this event is only needed for devices that do not notify the core
            // of changed properties.  For those devices, the core will already fire the
            // event.  Since we have no way of knowing, better call one extra time.
            studio_.events().post(new DefaultPixelSizeChangedEvent(pixSizeUmPost));
         }

         // The rest of this function uses the cached property values.
         // If `fromCache` is false, start by updating all properties in the cache.
         if (!fromCache) {
            studio_.core().updateSystemStateCache();
         }

         // camera settings
         if (studio_.cache().getCameraLabel().length() > 0) {
            double exp = studio_.core().getExposure();
            frame_.setDisplayedExposureTime(exp);
            configureBinningCombo();
            String binSize = studio_.core().getPropertyFromCache(
                  studio_.cache().getCameraLabel(), MMCoreJ.getG_Keyword_Binning());
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
      studio_.events().post(new DefaultGUIRefreshEvent());
   }

   /**
    * Sets up teh camera binning dropdown.
    *
    * @throws Exception may be thrown by the hardware.
    */
   public void configureBinningCombo() throws Exception {
      if (studio_.cache().getCameraLabel().length() > 0) {
         frame_.configureBinningComboForCamera(studio_.cache().getCameraLabel());
      }
   }

   // TODO: This method should be renamed!
   // resetGUIForNewHardwareConfig or something like that.
   // TODO: this method should be automatically invoked when
   // SystemConfigurationLoaded event occurs, and in no other way.
   // Better: each of these entities should listen for
   // SystemConfigurationLoaded itself and handle its own updates.

   /**
    * Todo: This function should be renamed.
    */
   public void initializeGUI() {
      try {
         if (studio_.cache() != null) {
            studio_.cache().refreshValues();
            if (studio_.getAcquisitionEngine() != null) {
               studio_.getAcquisitionEngine().setZStageDevice(studio_.cache().getZStageLabel());
            }
         }

         // Rebuild stage list in XY PositionList
         if (posListDlg_ != null) {
            posListDlg_.rebuildAxisList();
         }

         if (frame_ != null) {
            configureBinningCombo();
            frame_.updateAutofocusButtons(
                  studio_.getAutofocusManager().getAutofocusMethod() != null);
            // Since the load system configuration event already updated the cache,
            // we do not need to do it again.
            updateGUI(true, true);
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   /**
    * Adds an entry to the Position List with the current position of the hardware.
    */
   public void markCurrentPosition() {
      if (posListDlg_ == null) {
         showPositionList();
      }
      if (posListDlg_ != null) {
         posListDlg_.markPosition(false);
      }
   }

   /**
    * Shows the Position List Window.
    */
   public void showPositionList() {
      if (posListDlg_ == null) {
         posListDlg_ = new MMPositionListDlg(studio_, studio_.positions().getPositionList());
         posListDlg_.addListeners();
      }
      posListDlg_.setVisible(true);
   }

   /**
    * Hack: The API currently does not allow modifying positions in the current list
    * See https://github.com/micro-manager/micro-manager/issues/1090
    * This backdoor should be removed once a better solution has been implemented
    *
    * @return current PositionList.  Handle with care as dangerous things can happen
    * @deprecated Direct access to the PositionList should not be allowed, but
    *     currently is the only way to modify the StagePositions in the current PositionList
    */
   @Deprecated
   public PositionList getPositionList() {
      if (posListDlg_ == null) {
         showPositionList();
      }
      return posListDlg_.getPositionList();
   }
} 