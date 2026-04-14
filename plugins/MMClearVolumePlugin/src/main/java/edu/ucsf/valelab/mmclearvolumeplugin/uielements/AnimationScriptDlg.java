package edu.ucsf.valelab.mmclearvolumeplugin.uielements;

import edu.ucsf.valelab.mmclearvolumeplugin.CVViewer;
import edu.ucsf.valelab.mmclearvolumeplugin.animation.AnimationInstruction;
import edu.ucsf.valelab.mmclearvolumeplugin.animation.AnimationPlayer;
import edu.ucsf.valelab.mmclearvolumeplugin.animation.AnimationPlayer.ExportTarget;
import edu.ucsf.valelab.mmclearvolumeplugin.animation.AnimationScript;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
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

   private static final String TARGET_IMAGEJ = "ImageJ stack";
   private static final String TARGET_FFMPEG = "Movie (ffmpeg)";

   private final Studio studio_;
   private final CVViewer viewer_;

   private final javax.swing.JTextArea scriptArea_;
   private final JSpinner fpsSpinner_;
   private final JSpinner totalFramesSpinner_;
   private final JComboBox<String> exportTargetCombo_;
   private final JLabel outputLabel_;
   private final JTextField outputField_;
   private final JButton browseButton_;
   private final JButton runButton_;
   private final JButton stopButton_;
   private final JLabel statusLabel_;

   private volatile AnimationPlayer currentPlayer_ = null;

   /**
    * Creates and immediately shows the dialog.
    *
    * @param studio Micro-Manager Studio instance
    * @param viewer the ClearVolume viewer to animate
    */
   public AnimationScriptDlg(Studio studio, CVViewer viewer) {
      super((java.awt.Frame) null, "3D Animation Script", false);
      studio_ = studio;
      viewer_ = viewer;

      java.net.URL iconUrl = getClass()
            .getResource("/org/micromanager/icons/microscope.gif");
      if (iconUrl != null) {
         setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
      }

      JPanel panel = new JPanel(new MigLayout("flowy, insets 8"));

      // Script editor.
      panel.add(new JLabel("Animation script:"), "");
      scriptArea_ = new javax.swing.JTextArea(20, 60);
      scriptArea_.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
      scriptArea_.setTabSize(4);
      scriptArea_.setText(loadScript());
      JScrollPane scrollPane = new JScrollPane(scriptArea_);
      scrollPane.setPreferredSize(new Dimension(600, 320));
      panel.add(scrollPane, "growx, pushx");

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
            new String[]{TARGET_IMAGEJ, TARGET_FFMPEG});
      exportTargetCombo_.setSelectedItem(loadExportTarget());
      panel.add(exportTargetCombo_, "");

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
      JButton closeButton = new JButton("Close");

      runButton_.addActionListener((ActionEvent e) -> startAnimation());
      stopButton_.addActionListener((ActionEvent e) -> stopAnimation());
      closeButton.addActionListener((ActionEvent e) -> {
         saveSettings();
         dispose();
      });

      panel.add(runButton_, "split 3, flowx, align right");
      panel.add(stopButton_, "");
      panel.add(closeButton, "");

      // Status line.
      statusLabel_ = new JLabel(" ");
      panel.add(statusLabel_, "growx");

      // Show/hide output row based on export target.
      exportTargetCombo_.addActionListener((ActionEvent e) -> updateOutputRowVisibility());
      updateOutputRowVisibility();

      getContentPane().add(panel);
      pack();
      setLocationRelativeTo(null);
      setVisible(true);
   }

   // -----------------------------------------------------------------------
   // Animation control
   // -----------------------------------------------------------------------

   private void startAnimation() {
      String scriptText = scriptArea_.getText();
      List<AnimationInstruction> instructions;
      try {
         instructions = AnimationScript.parse(scriptText);
      } catch (IllegalArgumentException ex) {
         statusLabel_.setText("Parse error: " + ex.getMessage());
         return;
      }

      if (instructions.isEmpty()) {
         statusLabel_.setText("Script contains no instructions.");
         return;
      }

      int fps = (Integer) fpsSpinner_.getValue();
      int totalFrames = (Integer) totalFramesSpinner_.getValue();
      String targetStr = (String) exportTargetCombo_.getSelectedItem();
      ExportTarget target = TARGET_FFMPEG.equals(targetStr)
            ? ExportTarget.FFMPEG : ExportTarget.IMAGEJ;

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
            viewer_, instructions, totalFrames, fps, target,
            ffmpegPath, outputPath, studio_.getLogManager());
      currentPlayer_ = player;

      runButton_.setEnabled(false);
      stopButton_.setEnabled(true);
      statusLabel_.setText("Running…");

      final String finalOutputPath = outputPath;
      Thread animThread = new Thread(() -> {
         try {
            player.play();
            SwingUtilities.invokeLater(() -> {
               statusLabel_.setText(target == ExportTarget.FFMPEG
                     ? "Done — saved to: " + finalOutputPath
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

   private void stopAnimation() {
      AnimationPlayer p = currentPlayer_;
      if (p != null) {
         p.stop();
      }
      statusLabel_.setText("Stopping…");
   }

   // -----------------------------------------------------------------------
   // UI helpers
   // -----------------------------------------------------------------------

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

   private static String getDefaultScript() {
      return "# 3D Animation Script\n"
            + "# Example: full rotation over 120 frames\n"
            + "#\n"
            + "From frame 0 to frame 119:\n"
            + "- rotate by 360 degrees horizontally ease-in-out\n";
   }
}
