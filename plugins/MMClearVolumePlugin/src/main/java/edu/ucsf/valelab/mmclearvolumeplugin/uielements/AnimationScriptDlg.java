package edu.ucsf.valelab.mmclearvolumeplugin.uielements;

import edu.ucsf.valelab.mmclearvolumeplugin.CVViewer;
import edu.ucsf.valelab.mmclearvolumeplugin.animation.AnimationPlayer;
import edu.ucsf.valelab.mmclearvolumeplugin.animation.AnimationPlayer.ExportTarget;
import edu.ucsf.valelab.mmclearvolumeplugin.animation.AnimationScript;
import edu.ucsf.valelab.mmclearvolumeplugin.animation.AnimationScript.ParseResult;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.display.internal.gearmenu.FfmpegLocator;

/**
 * Dialog that provides a text editor for 3D animation scripts, and controls
 * to run them with export to an ImageJ stack or an ffmpeg MP4.
 *
 * <p>The animation language is the one described in:
 * Wan et al. (2019) Nature Methods.
 */
public final class AnimationScriptDlg extends JDialog {

   private static final String KEY_SCRIPT = "cv animation script";
   private static final String KEY_FPS = "cv animation fps";
   private static final String KEY_TOTAL_FRAMES = "cv animation total frames";
   private static final String KEY_EXPORT_TARGET = "cv animation export target";
   private static final String KEY_LAST_DIR = "cv animation last directory";
   private static final String KEY_RESTORE_STATE = "cv animation restore state";

   private static final String TARGET_PREVIEW = "Preview";
   private static final String TARGET_IMAGEJ = "ImageJ stack";
   private static final String TARGET_FFMPEG = "Movie (ffmpeg)";

   private final Studio studio_;
   private volatile CVViewer viewer_;

   private final javax.swing.JTextArea scriptArea_;
   private final JSpinner fpsSpinner_;
   private final JSpinner totalFramesSpinner_;
   private final JComboBox<String> exportTargetCombo_;
   private final JLabel outputLabel_;
   private final JTextField outputField_;
   private final JButton browseButton_;
   private final JCheckBox restoreStateCheck_;
   private final JButton runButton_;
   private final JButton stopButton_;
   private final JTextField statusLabel_;

   private volatile AnimationPlayer currentPlayer_ = null;

   /** The script text as last saved to / loaded from a file or the profile. */
   private String savedScript_;

   /**
    * Creates and immediately shows the dialog.
    *
    * @param studio Micro-Manager Studio instance
    * @param viewer the ClearVolume viewer to animate
    */
   public AnimationScriptDlg(Studio studio, CVViewer viewer) {
      // null owner so the window gets its own Windows taskbar entry.
      super((java.awt.Frame) null, "3D Animation Script — " + viewer.getName(), false);
      studio_ = studio;
      viewer_ = viewer;

      java.net.URL iconUrl = getClass()
            .getResource("/org/micromanager/icons/microscope.gif");
      if (iconUrl != null) {
         setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
      }

      // "fill" makes the panel track the dialog size; "flowy" stacks rows top-down.
      final JPanel panel = new JPanel(new MigLayout("fill, flowy, insets 8"));

      // Script editor — label + Save/Load/Default buttons on one row.
      JButton saveScriptButton = new JButton("Save…");
      saveScriptButton.setToolTipText("Save script to a file");
      saveScriptButton.addActionListener((ActionEvent e) -> saveScriptToFile());
      JButton loadScriptButton = new JButton("Load…");
      loadScriptButton.setToolTipText("Load script from a file");
      loadScriptButton.addActionListener((ActionEvent e) -> loadScriptFromFile());
      JButton defaultScriptButton = new JButton("Default");
      defaultScriptButton.setToolTipText("Replace the script with the built-in example");
      defaultScriptButton.addActionListener((ActionEvent e) -> loadDefaultScript());
      panel.add(new JLabel("Animation script:"), "split 4, flowx");
      panel.add(saveScriptButton, "");
      panel.add(loadScriptButton, "");
      panel.add(defaultScriptButton, "");

      scriptArea_ = new javax.swing.JTextArea(20, 60);
      scriptArea_.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
      scriptArea_.setTabSize(4);
      savedScript_ = loadScript();
      scriptArea_.setText(savedScript_);
      JScrollPane scrollPane = new JScrollPane(scriptArea_);
      panel.add(scrollPane, "grow, push");

      // FPS and total frames on one row.
      panel.add(new JLabel("Frames per second:"), "split 4, flowx");
      fpsSpinner_ = new JSpinner(new SpinnerNumberModel(loadFps(), 1, 120, 1));
      panel.add(fpsSpinner_, "");
      panel.add(new JLabel("  Total frames:"), "");
      totalFramesSpinner_ = new JSpinner(
            new SpinnerNumberModel(loadTotalFrames(), 1, 100000, 1));
      panel.add(totalFramesSpinner_, "");

      // Export target selector.
      panel.add(new JLabel("Export to:"), "split 2, flowx");
      exportTargetCombo_ = new JComboBox<>(
            new String[]{TARGET_PREVIEW, TARGET_IMAGEJ, TARGET_FFMPEG});
      exportTargetCombo_.setSelectedItem(loadExportTarget());
      panel.add(exportTargetCombo_, "");

      // Restore-state checkbox.
      restoreStateCheck_ = new JCheckBox(
            "Restore viewer state after animation", loadRestoreState());
      panel.add(restoreStateCheck_, "");

      // Output file row (visible only for ffmpeg).
      outputLabel_ = new JLabel("Output file (.mp4):");
      outputField_ = new JTextField(40);
      browseButton_ = new JButton("Browse...");
      browseButton_.addActionListener((ActionEvent e) -> browseForOutput());
      panel.add(outputLabel_, "split 3, flowx");
      panel.add(outputField_, "growx");
      panel.add(browseButton_, "");

      // Buttons.
      runButton_ = new JButton("Run");
      stopButton_ = new JButton("Stop");
      stopButton_.setEnabled(false);
      final JButton closeButton = new JButton("Close");

      JButton helpButton = new JButton("Help");
      helpButton.addActionListener((ActionEvent e) -> openHelp());

      runButton_.addActionListener((ActionEvent e) -> startAnimation());
      stopButton_.addActionListener((ActionEvent e) -> stopAnimation());
      closeButton.addActionListener((ActionEvent e) -> closeDialog());

      // Intercept the window-close button (X) and app-exit so we can prompt
      // the user to save unsaved changes.
      setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            closeDialog();
         }
      });

      panel.add(runButton_, "split 4, flowx, align right");
      panel.add(stopButton_, "");
      panel.add(closeButton, "");
      panel.add(helpButton, "");

      // Status line — use a non-editable JTextField so the text can be
      // selected and copied (e.g. to paste a parse error into a bug report).
      statusLabel_ = new JTextField(" ");
      statusLabel_.setEditable(false);
      statusLabel_.setBorder(javax.swing.BorderFactory.createEmptyBorder());
      statusLabel_.setBackground(UIManager.getColor("Panel.background"));
      panel.add(statusLabel_, "growx");

      // Show/hide output row based on export target.
      exportTargetCombo_.addActionListener((ActionEvent e) -> updateOutputRowVisibility());
      updateOutputRowVisibility();

      getContentPane().add(panel, java.awt.BorderLayout.CENTER);
      pack();
      setLocationRelativeTo(null);
      setVisible(true);
   }

   // -----------------------------------------------------------------------
   // Animation control
   // -----------------------------------------------------------------------

   /**
    * Updates the viewer that this dialog animates. Called when the Inspector
    * switches to a different ClearVolume viewer so the already-open dialog
    * follows the frontmost viewer automatically. Has no effect while an
    * animation is currently running.
    */
   public void setViewer(CVViewer viewer) {
      if (currentPlayer_ == null) {
         viewer_ = viewer;
         setTitle("3D Animation Script — " + viewer.getName());
      }
   }

   private void startAnimation() {
      String scriptText = scriptArea_.getText();
      ParseResult parsed;
      try {
         parsed = AnimationScript.parse(scriptText);
      } catch (IllegalArgumentException ex) {
         statusLabel_.setText("Parse error: " + ex.getMessage());
         return;
      }

      if (parsed.instructions.isEmpty()) {
         statusLabel_.setText("Script contains no instructions.");
         return;
      }

      int fps = (Integer) fpsSpinner_.getValue();
      int totalFrames = (Integer) totalFramesSpinner_.getValue();
      String targetStr = (String) exportTargetCombo_.getSelectedItem();
      ExportTarget target = TARGET_FFMPEG.equals(targetStr) ? ExportTarget.FFMPEG
            : TARGET_PREVIEW.equals(targetStr) ? ExportTarget.PREVIEW
            : ExportTarget.IMAGEJ;

      String ffmpegPath = null;
      String outputPath = null;

      if (target == ExportTarget.FFMPEG) {
         ffmpegPath = FfmpegLocator.findOrLocate(studio_, this);
         if (ffmpegPath == null) {
            statusLabel_.setText("ffmpeg not found — export cancelled.");
            return;
         }
         outputPath = outputField_.getText().trim();
         if (outputPath.isEmpty()) {
            statusLabel_.setText("Please specify an output .mp4 file.");
            return;
         }
         if (!outputPath.endsWith(".mp4")) {
            outputPath = outputPath + ".mp4";
         }
         if (new File(outputPath).exists()) {
            statusLabel_.setText("Output file already exists: " + outputPath);
            return;
         }
      }

      saveSettings();

      final AnimationPlayer player = new AnimationPlayer(
            viewer_, parsed.instructions, parsed.scriptFunctions,
            totalFrames, fps, target,
            ffmpegPath, outputPath,
            restoreStateCheck_.isSelected(),
            studio_.getLogManager());
      currentPlayer_ = player;

      runButton_.setEnabled(false);
      stopButton_.setEnabled(true);
      statusLabel_.setText(target == ExportTarget.PREVIEW ? "Previewing…" : "Running…");

      final String finalOutputPath = outputPath;
      Thread animThread = new Thread(() -> {
         try {
            player.play();
            SwingUtilities.invokeLater(() -> {
               statusLabel_.setText(target == ExportTarget.FFMPEG
                     ? "Done — saved to: " + finalOutputPath
                     : target == ExportTarget.PREVIEW
                     ? "Preview finished."
                     : "Done — ImageJ stack opened.");
            });
         } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            SwingUtilities.invokeLater(() -> statusLabel_.setText("Interrupted."));
         } catch (Exception ex) {
            final String msg = ex.getMessage();
            SwingUtilities.invokeLater(() -> {
               statusLabel_.setText("Error: " + msg);
               studio_.getLogManager().showError(ex,
                     "Animation export failed", AnimationScriptDlg.this);
            });
         } finally {
            SwingUtilities.invokeLater(() -> {
               runButton_.setEnabled(true);
               stopButton_.setEnabled(false);
               currentPlayer_ = null;
            });
         }
      }, "CV-AnimationPlayer");
      animThread.setDaemon(true);
      animThread.start();
   }

   private void openHelp() {
      try {
         Desktop.getDesktop().browse(new URI("https://micro-manager.org/3DScript"));
      } catch (Exception ex) {
         statusLabel_.setText("Could not open browser: " + ex.getMessage());
      }
   }

   private void stopAnimation() {
      AnimationPlayer p = currentPlayer_;
      if (p != null) {
         p.stop();
      }
      statusLabel_.setText("Stopping…");
   }

   private void closeDialog() {
      if (confirmDiscardChanges()) {
         saveSettings();
         dispose();
      }
   }

   // -----------------------------------------------------------------------
   // UI helpers
   // -----------------------------------------------------------------------

   /** Returns true if the script has been edited since the last save/load. */
   private boolean isScriptModified() {
      return !scriptArea_.getText().equals(savedScript_);
   }

   /**
    * If the script has unsaved changes, asks the user whether to save first.
    * Returns true if it is safe to discard/replace the current script
    * (user chose Save and it succeeded, or chose Discard), false if the
    * user cancelled.
    */
   private boolean confirmDiscardChanges() {
      if (!isScriptModified()) {
         return true;
      }
      int choice = JOptionPane.showOptionDialog(
            this,
            "The script has unsaved changes. Save before continuing?",
            "Unsaved Changes",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            new String[]{"Save", "Discard", "Cancel"},
            "Save");
      if (choice == 0) {        // Save
         saveScriptToFile();
         // If still modified the save was cancelled — treat as Cancel.
         return !isScriptModified();
      }
      return choice == 1;       // Discard; Cancel (2 or closed) → false
   }

   private void updateOutputRowVisibility() {
      boolean isFfmpeg = TARGET_FFMPEG.equals(exportTargetCombo_.getSelectedItem());
      outputLabel_.setVisible(isFfmpeg);
      outputField_.setVisible(isFfmpeg);
      browseButton_.setVisible(isFfmpeg);
      pack();
   }


   private void browseForOutput() {
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Save movie as");
      chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      chooser.setSelectedFile(new File("animation.mp4"));
      if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
         String path = chooser.getSelectedFile().getAbsolutePath();
         if (!path.endsWith(".mp4")) {
            path += ".mp4";
         }
         outputField_.setText(path);
      }
   }

   // -----------------------------------------------------------------------
   // Script file I/O
   // -----------------------------------------------------------------------

   private JFileChooser makeScriptChooser() {
      JFileChooser chooser = new JFileChooser();
      chooser.setFileFilter(
            new FileNameExtensionFilter("ClearVolume animation script (*.cvs)", "cvs"));
      chooser.setAcceptAllFileFilterUsed(true);
      String lastDir = studio_.profile().getSettings(AnimationScriptDlg.class)
            .getString(KEY_LAST_DIR, null);
      if (lastDir != null) {
         chooser.setCurrentDirectory(new File(lastDir));
      }
      return chooser;
   }

   private void loadDefaultScript() {
      if (!confirmDiscardChanges()) {
         return;
      }
      savedScript_ = getDefaultScript();
      scriptArea_.setText(savedScript_);
      scriptArea_.setCaretPosition(0);
      statusLabel_.setText("Default script loaded.");
   }

   private void saveScriptToFile() {
      JFileChooser chooser = makeScriptChooser();
      chooser.setDialogTitle("Save animation script");
      chooser.setSelectedFile(new File("animation.cvs"));
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
         return;
      }
      File file = chooser.getSelectedFile();
      if (!file.getName().contains(".")) {
         file = new File(file.getAbsolutePath() + ".cvs");
      }
      studio_.profile().getSettings(AnimationScriptDlg.class)
            .putString(KEY_LAST_DIR, file.getParent());
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
         String text = scriptArea_.getText();
         writer.write(text);
         savedScript_ = text;
         statusLabel_.setText("Saved to: " + file.getName());
      } catch (IOException ex) {
         statusLabel_.setText("Save failed: " + ex.getMessage());
         studio_.getLogManager().logError(ex, "Failed to save animation script");
      }
   }

   private void loadScriptFromFile() {
      if (!confirmDiscardChanges()) {
         return;
      }
      JFileChooser chooser = makeScriptChooser();
      chooser.setDialogTitle("Load animation script");
      if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
         return;
      }
      File file = chooser.getSelectedFile();
      studio_.profile().getSettings(AnimationScriptDlg.class)
            .putString(KEY_LAST_DIR, file.getParent());
      StringBuilder sb = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
         String line;
         while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
         }
         savedScript_ = sb.toString();
         scriptArea_.setText(savedScript_);
         scriptArea_.setCaretPosition(0);
         statusLabel_.setText("Loaded: " + file.getName());
      } catch (IOException ex) {
         statusLabel_.setText("Load failed: " + ex.getMessage());
         studio_.getLogManager().logError(ex, "Failed to load animation script");
      }
   }

   // -----------------------------------------------------------------------
   // Profile persistence
   // -----------------------------------------------------------------------

   private void saveSettings() {
      studio_.profile().getSettings(AnimationScriptDlg.class)
            .putString(KEY_SCRIPT, scriptArea_.getText());
      studio_.profile().getSettings(AnimationScriptDlg.class)
            .putInteger(KEY_FPS, (Integer) fpsSpinner_.getValue());
      studio_.profile().getSettings(AnimationScriptDlg.class)
            .putInteger(KEY_TOTAL_FRAMES, (Integer) totalFramesSpinner_.getValue());
      studio_.profile().getSettings(AnimationScriptDlg.class)
            .putString(KEY_EXPORT_TARGET, (String) exportTargetCombo_.getSelectedItem());
      studio_.profile().getSettings(AnimationScriptDlg.class)
            .putBoolean(KEY_RESTORE_STATE, restoreStateCheck_.isSelected());
   }

   private String loadScript() {
      return studio_.profile().getSettings(AnimationScriptDlg.class)
            .getString(KEY_SCRIPT, getDefaultScript());
   }

   private int loadFps() {
      return studio_.profile().getSettings(AnimationScriptDlg.class)
            .getInteger(KEY_FPS, 24);
   }

   private int loadTotalFrames() {
      return studio_.profile().getSettings(AnimationScriptDlg.class)
            .getInteger(KEY_TOTAL_FRAMES, 120);
   }

   private String loadExportTarget() {
      return studio_.profile().getSettings(AnimationScriptDlg.class)
            .getString(KEY_EXPORT_TARGET, TARGET_IMAGEJ);
   }

   private boolean loadRestoreState() {
      return studio_.profile().getSettings(AnimationScriptDlg.class)
            .getBoolean(KEY_RESTORE_STATE, true);
   }

   private static String getDefaultScript() {
      return "# 3D Animation Script  (Wan et al. 2019 animation language)\n"
            + "# Lines starting with # are comments.\n"
            + "# A 'script' block at the end can define JavaScript functions\n"
            + "# to use as dynamic parameters (called with the frame number).\n"
            + "#\n"
            + "From frame 1 to frame 120:\n"
            + "- rotate by 360 degrees horizontally ease-in-out\n"
            + "- zoom by a factor of zoomFn\n"
            + "script\n"
            + "function zoomFn(t) {\n"
            + "    // Smooth arc: 0.1x at t=1, peaks at 3.0x near t=60, returns to 0.1x at t=120.\n"
            + "    var pos  = 0.1 + 2.9 * sin(PI * (t - 1) / 119);\n"
            + "    var prev = 0.1 + 2.9 * sin(PI * (t - 2) / 119);\n"
            + "    return pos - prev;\n"
            + "}\n";
   }
}
