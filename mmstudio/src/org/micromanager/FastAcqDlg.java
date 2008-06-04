///////////////////////////////////////////////////////////////////////////////
//FILE:          FastAcqDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, May 15, 2007
//
// COPYRIGHT:    University of California, San Francisco, 2007
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
// CVS:          $Id: AcqControlDlg.java 189 2007-05-24 20:27:43Z nenad $

package org.micromanager;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Timer;
import java.util.prefs.Preferences;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;

import mmcorej.CMMCore;
import mmcorej.MMCoreJ;

import org.micromanager.fastacq.DiskStreamingThread;
import org.micromanager.fastacq.DisplayTimerTask;
import org.micromanager.fastacq.GUIStatus;
import org.micromanager.fastacq.StatusTimerTask;
import org.micromanager.api.DeviceControlGUI;
import org.micromanager.utils.GUIColors;
import org.micromanager.utils.MMLogger;
import org.micromanager.utils.MemoryUtils;
import org.micromanager.utils.TextUtils;

import com.swtdesigner.SwingResourceManager;

/**
 * Dialog box to set up fast sequence acquisition mode.
 *
 */
public class FastAcqDlg extends JDialog implements GUIStatus {
   private static final long serialVersionUID = 1L;
   private ButtonGroup buttonGroup = new ButtonGroup();
   private JLabel diskStatusLabel_;
   private JTextField namePrefix_;
   private JTextField dirRoot_;
   private JTextField seqLength_;
   private CMMCore core_;
   private DiskStreamingThread streamThread_;
   private JLabel bufStatusLabel_;
   private Preferences prefs_;
   private Timer dispTimer_;
   private Timer statusTimer_;
   private String cameraName_;
   private DeviceControlGUI parentGUI_;
   private StatusTimerTask statusTimerTask_;
   private JRadioButton saveToDiskRadioButton_;
   private JRadioButton createStackRadioButton_;
   private JCheckBox displayCheckBox_;
   private GUIColors guiColors_;
   
   // preference keys
   static final String NAME_PREFIX = "name_prefix";
   static final String DIR_ROOT = "dir_root";
   static final String INTERVAL_MS = "interval_ms";
   static final String SEQ_LENGTH = "sequence_length";
   static final String PANEL_X = "panel_x";
   static final String PANEL_Y = "panel_y";
   static private final String DISPLAY = "display";
   static private final String SAVE_TO_DISK = "save_to_disk";
   private JLabel actualIntervalLabel_;
   
   
   /**
    * Create the dialog
    */
   public FastAcqDlg(CMMCore core, DeviceControlGUI pg) {
      super();
      
      parentGUI_ = pg;
      guiColors_ = new GUIColors();
      
      addWindowListener(new WindowAdapter() {
         public void windowClosed(WindowEvent arg0) {
            saveSettings();
            if (statusTimer_ != null)
               statusTimer_.cancel();
            if (dispTimer_ != null)
               dispTimer_.cancel();
         }
      });
      
      setTitle("Burst acquisition");
      setBackground(guiColors_.background.get(parentGUI_.getBackgroundStyle()));
      setResizable(false);
      getContentPane().setLayout(null);
      //
      core_ = core;
      getContentPane().setLayout(null);
      setTitle("MM Fast Acquisition Plugin");
      setResizable(false);
      
      // load preferences
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      prefs_ = root.node(root.absolutePath() + "/settings");
      
      //setBounds(100, 100, 458, 227);
      setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      int x = prefs_.getInt(PANEL_X, 100);
      int y = prefs_.getInt(PANEL_Y, 100);
      setBounds(x, y, 457, 298);
      
      final JLabel sequenceLengthLabel = new JLabel();
      sequenceLengthLabel.setText("Sequence length");
      sequenceLengthLabel.setBounds(10, 10, 101, 14);
      getContentPane().add(sequenceLengthLabel);

      final JLabel intervalmsLabel = new JLabel();
      intervalmsLabel.setText("Interval [ms]");
      intervalmsLabel.setBounds(10, 163, 101, 14);
      getContentPane().add(intervalmsLabel);

      final JLabel rootDirectoryLabel = new JLabel();
      rootDirectoryLabel.setText("Directory root");
      rootDirectoryLabel.setBounds(10, 51, 101, 14);
      getContentPane().add(rootDirectoryLabel);

      final JLabel namePrefixLabel = new JLabel();
      namePrefixLabel.setText("Name prefix");
      namePrefixLabel.setBounds(10, 76, 101, 14);
      getContentPane().add(namePrefixLabel);

      seqLength_ = new JTextField();
      seqLength_.setBounds(115, 5, 79, 20);
      getContentPane().add(seqLength_);

      dirRoot_ = new JTextField();
      dirRoot_.setBounds(115, 46, 281, 20);
      getContentPane().add(dirRoot_);

      namePrefix_ = new JTextField();
      namePrefix_.setBounds(115, 73, 281, 20);
      getContentPane().add(namePrefix_);

      final JButton startButton = new JButton();
      startButton.setIcon(SwingResourceManager.getIcon(FastAcqDlg.class, "icons/resultset_next.png"));
      startButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            start();
         }
      });
      startButton.setText("Start Acq.");
      startButton.setBounds(10, 232, 113, 25);
      getContentPane().add(startButton);

      final JButton stopButton = new JButton();
      stopButton.setIcon(SwingResourceManager.getIcon(FastAcqDlg.class, "icons/delete.png"));
      stopButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            stop();
         }
      });
      stopButton.setText(" Stop Acq.");
      stopButton.setBounds(127, 232, 113, 25);
      getContentPane().add(stopButton);

      final JButton exitButton = new JButton();
      exitButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            if (canExit()) {
               dispose();
               saveSettings();
             } else {
               displayMessageDialog("Saving to disk not finished.\n" +
                     "Please wait until all acquired images are procesessed.");
               return;
            }
         }
      });
      exitButton.setText("Close");
      exitButton.setBounds(353, 6, 93, 23);
      getContentPane().add(exitButton);

      final JButton button = new JButton();
      button.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            browse();
         }
      });
      button.setText("...");
      button.setBounds(402, 47, 44, 23);
      getContentPane().add(button);

      final JLabel bsLabel = new JLabel();
      bsLabel.setText("Buffer status");
      bsLabel.setBounds(10, 123, 93, 14);
      getContentPane().add(bsLabel);

      bufStatusLabel_ = new JLabel();
      bufStatusLabel_.setBorder(new LineBorder(Color.black, 1, false));
      bufStatusLabel_.setBounds(115, 123, 327, 14);
      getContentPane().add(bufStatusLabel_);
      //
      
      final JLabel dsLabel = new JLabel();
      dsLabel.setText("Disk status");
      dsLabel.setBounds(10, 143, 93, 14);
      getContentPane().add(dsLabel);

      diskStatusLabel_ = new JLabel();
      diskStatusLabel_.setBorder(new LineBorder(Color.black, 1, false));
      diskStatusLabel_.setBounds(115, 143, 327, 14);
      getContentPane().add(diskStatusLabel_);

      displayCheckBox_ = new JCheckBox();
      displayCheckBox_.setText("Display while acquiring");
      displayCheckBox_.setBounds(10, 203, 198, 25);
      getContentPane().add(displayCheckBox_);

      JButton stopSavingButton = new JButton();
      stopSavingButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            stopSaving();
         }
      });
      stopSavingButton.setIcon(SwingResourceManager.getIcon(FastAcqDlg.class, "icons/cancel.png"));
      stopSavingButton.setText("Stop saving");
      stopSavingButton.setBounds(311, 232, 121, 25);
      getContentPane().add(stopSavingButton);

      final JLabel outputLabel = new JLabel();
      outputLabel.setText("Output To;");
      outputLabel.setBounds(311, 163, 131, 20);
      getContentPane().add(outputLabel);

      saveToDiskRadioButton_ = new JRadioButton();
      buttonGroup.add(saveToDiskRadioButton_);
      saveToDiskRadioButton_.setText("Disk");
      saveToDiskRadioButton_.setBounds(311, 183, 131, 20);
      getContentPane().add(saveToDiskRadioButton_);

      createStackRadioButton_ = new JRadioButton();
      buttonGroup.add(createStackRadioButton_);
      createStackRadioButton_.setText("Screen");
      createStackRadioButton_.setBounds(311, 203, 131, 20);
      getContentPane().add(createStackRadioButton_);
      
      statusTimerTask_ = new StatusTimerTask(core_, this);
      statusTimer_ = new Timer();
      statusTimer_.schedule(statusTimerTask_, 0, 300);

      actualIntervalLabel_ = new JLabel();
      actualIntervalLabel_.setBounds(117, 163, 180, 14);
      getContentPane().add(actualIntervalLabel_);
      
      
      loadSettings();
   }
   
   /**
    * Stop disk streaming thread.
    */
   protected void stopSaving() {
      if (streamThread_ != null)
         streamThread_.stopSaving();
   }
   
   /**
    * Choose the root directory to save files.
    */
   protected void browse() {
      JFileChooser fc = new JFileChooser();
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      fc.setCurrentDirectory(new File(dirRoot_.getText()));
      int retVal = fc.showOpenDialog(this);
      if (retVal == JFileChooser.APPROVE_OPTION) {
         dirRoot_.setText(fc.getSelectedFile().getAbsolutePath());
      }
   }

   /**
    * Start acquisition sequence.
    */
   public void start() {
      cameraName_ = core_.getCameraDevice();
      int width = (int)core_.getImageWidth();
      int height = (int)core_.getImageHeight();
      int depth = (int)core_.getBytesPerPixel();
      
      final int numFrames = Integer.parseInt(seqLength_.getText());
      if (cameraName_.length() == 0) {
         displayMessageDialog("Camera device not defined or configured.");
         return;
      }
      if (isBusy()) {
         displayMessageDialog("Acquisition still running, please wait until done.");
         return;
      }
                  
      // check if there is enough memory for the stack
      // check if we have enough memory to acquire the entire sequence
      long freeBytes = MemoryUtils.freeMemory();
      long requiredBytes = ((long)numFrames + 10) * (width * height * depth);
      MMLogger.getLogger().info("Remaining memory " + freeBytes + " bytes. Required: " + requiredBytes);
      if (createStackRadioButton_.isSelected() && freeBytes <  requiredBytes) {
         handleError("Remaining memory " + TextUtils.FMT2.format(freeBytes/1048576.0) +
               " MB. Required for screen display: " + TextUtils.FMT2.format(requiredBytes/1048576.0) + " MB\n" +
               "Try saving images to disk instead of screen.");
         return;
      }
 
      try {
         double exposureMs = core_.getExposure();
         final double acqIntervalMs = exposureMs; // set interval to be equal to exposure
         final double displayIntervalMs = Math.max(acqIntervalMs, 50.0); // display interval is minimum 50ms
         
         // start acquiring
         // ---------------
         core_.startSequenceAcquisition(numFrames, acqIntervalMs);
         MMLogger.getLogger().info("core_.startSequenceAcquisition() called.");            
         
         // try to get actual interval
         // --------------------------
         if (core_.hasProperty(cameraName_, MMCoreJ.getG_Keyword_ActualInterval_ms())) {
            actualIntervalLabel_.setText(core_.getProperty(cameraName_, MMCoreJ.getG_Keyword_ActualInterval_ms()) + " ms");
         } else {
            actualIntervalLabel_.setText("not available");
         }
         
         // start writing to disk
         // ---------------------
         //if (createStackRadioButton_.isSelected())
         //   displayCheckBox_.setSelected(false);
         
         streamThread_ = new DiskStreamingThread(core_, this, dirRoot_.getText(), namePrefix_.getText(), createStackRadioButton_.isSelected(), acqIntervalMs);
         streamThread_.start();
         
         // start displaying
         // ----------------
         
         if (displayCheckBox_.isSelected()) {
            DisplayTimerTask dispTimerTask = new DisplayTimerTask(core_, parentGUI_);
            dispTimer_ = new Timer();
            dispTimer_.schedule(dispTimerTask, (long)displayIntervalMs, (long)displayIntervalMs);
         }
         
      } catch (Exception e) {
         handleError(e.getMessage());
         if (dispTimer_ != null)
            dispTimer_.cancel();
         if (streamThread_ != null)
            streamThread_.interrupt();
      }            
   }
   
   private void handleError(String message) {
      JOptionPane.showMessageDialog(this, message);     
   }
   
   /**
    * Stop acquiring.
    */
   private void stop() {
      try {
         
         if (dispTimer_ != null)
            dispTimer_.cancel();
         
         // stop acquiring
         core_.stopSequenceAcquisition();
                  
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }
   
   public synchronized void displayMessage(String msg)
   {
      bufStatusLabel_.setText(msg);
   }
   public void displayStreamingMessage(String msg) {
      diskStatusLabel_.setText(msg);
   }
   protected void displayMessageDialog(String message) {
      JOptionPane.showMessageDialog(this, message);     
   }

   /**
    * Read settings from the registry.
    */
   private void loadSettings() {
      namePrefix_.setText(prefs_.get(NAME_PREFIX, "experiment"));
      dirRoot_.setText(prefs_.get(DIR_ROOT, "C:/mm_sequence_data"));
      seqLength_.setText(prefs_.get(SEQ_LENGTH, "50"));
      displayCheckBox_.setSelected(prefs_.getBoolean(DISPLAY, true));
      saveToDiskRadioButton_.setSelected(prefs_.getBoolean(SAVE_TO_DISK, true));
      createStackRadioButton_.setSelected(!saveToDiskRadioButton_.isSelected());
   }
   
   /**
    * Save settings to the registry.
    *
    */
   private void saveSettings() {
      prefs_.put(NAME_PREFIX, namePrefix_.getText());
      prefs_.put(DIR_ROOT, dirRoot_.getText());
      prefs_.put(SEQ_LENGTH, seqLength_.getText());
      Rectangle r = getBounds();
      prefs_.putInt(PANEL_X, r.x);
      prefs_.putInt(PANEL_Y, r.y);
      prefs_.putBoolean(DISPLAY, displayCheckBox_.isSelected());
      prefs_.putBoolean(SAVE_TO_DISK, saveToDiskRadioButton_.isSelected());
    }

   /**
    * Test if we can exit, i.e. if the disk thread is still running.
    */
   private boolean canExit() {
      // make sure writing thread finishes
      if (streamThread_ != null && streamThread_.isAlive())
         return false;
      return true;
   }
   
   /**
    * Tests if the acquistion is still in progress.
    */
   public boolean isBusy() {
      // make sure writing thread finishes
      if (streamThread_ != null && streamThread_.isAlive()) {
         return true;
      }
      
      try {
         if (core_.deviceBusy(cameraName_)) {
            return true;
         }
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      
      return false;   
   }

   /**
    * This method is called by the monitoring thread when the acquisition is finished.
    */
   public void acquisitionFinished() {
      if (dispTimer_ != null)
         dispTimer_.cancel();
   }
   
   /**
    * This method is called from the Options dialog, to set the background style
    */
   public void setBackgroundStyle(String style) {
      setBackground(guiColors_.background.get(style));
      repaint();
   } 
}
