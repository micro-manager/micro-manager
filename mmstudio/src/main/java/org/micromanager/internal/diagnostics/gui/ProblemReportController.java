// AUTHOR:       Mark Tsuchida
// COPYRIGHT:    University of California, San Francisco, 2014
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.diagnostics.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.micromanager.internal.diagnostics.ProblemReport;
import org.micromanager.internal.diagnostics.ProblemReportFormatter;
import org.micromanager.internal.utils.WindowPositioning;


/**
 * Controller for "Report Problem" GUI.
 * The only public interface is start().
 * All methods must be called on the EDT.
 */
public final class ProblemReportController {
   /*
    * Static fields and methods
    */

   private static ProblemReportController instance_;

   private static int checkForInterruptedReport(boolean userInitiated) {
      ProblemReport report = loadLeftoverReport();
      if (report == null) {
         return JOptionPane.NO_OPTION;
      }

      String[] options;
      if (userInitiated) {
         options = new String[] {"Reopen", "Discard", "Cancel"};
      } else {
         options = new String[] {"Reopen", "Discard", "Not Now"};
      }
      int answer = JOptionPane.showOptionDialog(null,
            "A Problem Report was in progress when Micro-Manager "
                  + "exited. Would you like to reopen the interrupted report?",
            "Continue Problem Report",
            JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
            null, options, options[0]);

      if (answer == JOptionPane.YES_OPTION) {
         return JOptionPane.YES_OPTION;
      } else if (answer == JOptionPane.NO_OPTION) {
         report.deleteStorage();
         return JOptionPane.NO_OPTION;
      } else {
         return JOptionPane.CANCEL_OPTION;
      }
   }

   /**
    * Prompt user to reopen an interrupted Problem Report if one exists.
    * Does nothing if the Problem Report window is currently being displayed.
    */
   public static void startIfInterruptedOnExit() {
      if (instance_ != null && instance_.frame_ != null) {
         return; // Don't mess if frame is being shown
      }

      int answer = checkForInterruptedReport(false);
      if (answer == JOptionPane.YES_OPTION) {
         if (instance_ == null) {
            instance_ = new ProblemReportController();
         }
         instance_.showFrame(true, null);
      }
   }

   /**
    * Activates the Problem Report window. If one is already open, brings it to
    * the front; if not, creates the window.
    */
   public static void start(final mmcorej.CMMCore core) {
      if (instance_ == null) {
         instance_ = new ProblemReportController();
      }
      instance_.showFrame(false, core);
   }

   /*
    * Instance fields and methods
    */

   private mmcorej.CMMCore core_;

   private ProblemReportFrame frame_ = null;
   private boolean hasContent_ = false;
   private javax.swing.JTextArea descriptionTextArea_;

   private boolean useCrashRobustLogging_ = true;

   // The problem report model may be accessed from a background thread
   private volatile ProblemReport report_;

   // Constructed solely by static methods
   private ProblemReportController() {
   }

   void showFrame(boolean forceReopen, mmcorej.CMMCore core) {
      core_ = core;

      if (frame_ == null) {
         int reopenAnswer = forceReopen ? JOptionPane.YES_OPTION :
               checkForInterruptedReport(true);
         if (reopenAnswer == JOptionPane.CANCEL_OPTION) {
            return;
         }
         frame_ = new ProblemReportFrame(this);
         if (reopenAnswer == JOptionPane.YES_OPTION) {
            report_ = loadLeftoverReport();
            descriptionTextArea_.setText(report_.getDescription());
            frame_.setControlPanel(new SendReportControlPanel(this, false));
            markReportHasContent();
         }
      }

      frame_.setVisible(true);

      int state = frame_.getExtendedState();
      state &= ~javax.swing.JFrame.ICONIFIED;
      frame_.setExtendedState(state);

      frame_.toFront();
      frame_.requestFocus();
   }

   private static File getReportDirectory() {
      String homePath = System.getProperty("user.home");
      if (homePath != null && !homePath.isEmpty()) {
         File homeDir = new File(homePath);
         if (homeDir.isDirectory()) {
            return new File(homeDir, "MMProblemReport");
         }
      }
      return null;
   }

   private static ProblemReport loadLeftoverReport() {
      File reportDir = getReportDirectory();
      ProblemReport report = ProblemReport.loadFromPersistence(reportDir);
      if (report.isUsefulReport()) {
         return report;
      }
      report.deleteStorage();
      return null;
   }

   /*
    * Accessors
    */

   private void markReportHasContent() {
      hasContent_ = true;
   }

   void markDescriptionModified() {
      copyDescriptionToReport();
      markReportHasContent();
   }

   void setDescriptionTextArea(javax.swing.JTextArea textArea) {
      descriptionTextArea_ = textArea;
   }

   void setUseCrashRobustLogging(boolean flag) {
      useCrashRobustLogging_ = flag;
   }

   boolean getUseCrashRobustLogging() {
      return useCrashRobustLogging_;
   }

   void cancelRequested() {
      if (hasContent_) {
         int result = JOptionPane.showConfirmDialog(frame_,
               "Discard all unsaved contents of this report?",
               "Close Problem Report",
               JOptionPane.YES_NO_OPTION);
         if (result != JOptionPane.YES_OPTION) {
            return;
         }
      }

      if (report_ != null) {
         report_.cancelLogCapture();
         report_.deleteStorage();
         report_ = null;
      }

      frame_.close();
      frame_ = null;
   }

   void startLogCapture() {
      if (report_ != null) {
         report_.cancelLogCapture();
         report_.deleteStorage();
         report_ = null;
      }

      final LogCaptureControlPanel panel = new LogCaptureControlPanel(this);
      panel.setStatus("Collecting system information...");
      panel.setButtonsEnabled(false);
      frame_.setControlPanel(panel);

      File reportDir = getReportDirectory();
      report_ = ProblemReport.newPersistentReport(core_, reportDir);
      copyDescriptionToReport();

      report_.startCapturingLog(useCrashRobustLogging_);
      new SwingWorker<Object, Object>() {
         @Override
         public Object doInBackground() {
            try {
               report_.logSystemInfo(false);
            } catch (Exception e) {
               core_.logMessage("Logging system info failed: " + e.getMessage());
            }
            return null;
         }

         @Override
         public void done() {
            if (report_ == null) { // Canceled
               return;
            }

            if (frame_ != null) {
               panel.setStatus("Capturing log...");
               panel.setInstructions("<html><b>Please perform the steps that reproduce "
                     + "the problem.</b></html>");
               panel.setButtonsEnabled(true);
               frame_.setControlPanel(panel);
            }

            core_.logMessage("User has been prompted to reproduce problem");
         }
      }.execute();

      markReportHasContent();
   }

   void insertTimestampedRemark(String remark) {
      if (report_ == null) {
         return;
      }
      report_.logUserComment(remark);
   }

   void finishLogCapture() {
      if (report_ == null) { // Canceled
         return;
      }

      core_.logMessage("User has stopped log capture");

      if (frame_ != null) {
         final LogCaptureControlPanel panel = (LogCaptureControlPanel) frame_.getControlPanel();
         panel.setButtonsEnabled(false);
         panel.setStatus("Collecting system information...");
         panel.setInstructions("Please wait.");
         frame_.setControlPanel(panel);
      }

      new SwingWorker<Object, Object>() {
         @Override
         public Object doInBackground() {
            try {
               report_.logSystemInfo(true);
            } catch (Exception e) {
               core_.logMessage("Logging system info failed: " + e.getMessage());
            }
            report_.finishCapturingLog();
            return null;
         }

         @Override
         public void done() {
            if (report_ == null) { // Canceled
               return;
            }
            if (frame_ != null) {
               frame_.setControlPanel(new SendReportControlPanel(ProblemReportController.this));
            }
         }
      }.execute();
   }

   void finishWithoutReproducing() {
      if (report_ == null) { // Canceled
         return;
      }

      core_.logMessage("User has indicated that the problem cannot be reproduced");

      if (frame_ != null) {
         final LogCaptureControlPanel panel = (LogCaptureControlPanel) frame_.getControlPanel();
         panel.setButtonsEnabled(false);
         panel.setStatus("Saving log...");
         panel.setInstructions("Please wait.");
         frame_.setControlPanel(panel);
      }

      new SwingWorker<Object, Object>() {
         @Override
         public Object doInBackground() {
            report_.finishCapturingLog();
            return null;
         }

         @Override
         public void done() {
            if (report_ == null) { // Canceled
               return;
            }
            if (frame_ != null) {
               frame_.setControlPanel(new SendReportControlPanel(ProblemReportController.this));
            }
         }
      }.execute();
   }

   void displayReport() {
      if (report_ == null) {
         return; // Should not happen
      }

      ProblemReportFormatter formatter = new ProblemReportFormatter();
      String reportStr = formatter.format(report_);

      openReportWindow(reportStr);
   }

   private void copyDescriptionToReport() {
      if (report_ == null) {
         return;
      }

      report_.setDescription(descriptionTextArea_.getText());
   }

   private void openReportWindow(String report) {
      JTextArea textArea = new JTextArea(report);
      textArea.setEditable(false);
      textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
      textArea.setCaretPosition(0);

      JButton saveButton = new JButton("Save...");
      saveButton.addActionListener(e -> saveReport(report));

      JPanel buttonPanel = new JPanel();
      buttonPanel.add(saveButton);

      JDialog dialog = new JDialog(frame_, "Problem Report", false);
      URL iconUrl = getClass().getResource("/org/micromanager/icons/microscope.gif");
      if (iconUrl != null) {
         dialog.setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
      }
      dialog.getContentPane().add(new JScrollPane(textArea), BorderLayout.CENTER);
      dialog.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
      dialog.setSize(800, 600);
      dialog.setLocationRelativeTo(frame_);
      WindowPositioning.setUpBoundsMemory(dialog, ProblemReportController.class, "ReportWindow");
      dialog.setVisible(true);
   }

   private void saveReport(String report) {
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Save Problem Report");
      chooser.setSelectedFile(new File("MMProblemReport.txt"));
      chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
      URL iconUrl = getClass().getResource("/org/micromanager/icons/microscope.gif");
      if (iconUrl != null) {
         java.awt.Image icon = Toolkit.getDefaultToolkit().getImage(iconUrl);
         chooser.addAncestorListener(new javax.swing.event.AncestorListener() {
            @Override
            public void ancestorAdded(javax.swing.event.AncestorEvent e) {
               java.awt.Window w = javax.swing.SwingUtilities.windowForComponent(chooser);
               if (w != null) {
                  w.setIconImage(icon);
               }
            }

            @Override public void ancestorRemoved(javax.swing.event.AncestorEvent e) {}

            @Override public void ancestorMoved(javax.swing.event.AncestorEvent e) {}
         });
      }
      if (chooser.showSaveDialog(frame_) != JFileChooser.APPROVE_OPTION) {
         return;
      }
      File file = chooser.getSelectedFile();
      if (!file.getName().contains(".")) {
         file = new File(file.getPath() + ".txt");
      }
      try (FileWriter fw = new FileWriter(file)) {
         fw.write(report);
      } catch (IOException ex) {
         JOptionPane.showMessageDialog(frame_,
               "Failed to save report: " + ex.getMessage(),
               "Save Error", JOptionPane.ERROR_MESSAGE);
      }
   }
}
