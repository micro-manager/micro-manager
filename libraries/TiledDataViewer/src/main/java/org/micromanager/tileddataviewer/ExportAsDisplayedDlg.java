///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// COPYRIGHT:    Regents of the University of California, 2026
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

package org.micromanager.tileddataviewer;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.GifWriter;
import ij.plugin.filter.AVI_Writer;
import ij.process.ColorProcessor;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;

/**
 * Saves what the TiledDataViewer is currently showing, as a single image or as a
 * movie stepping along one axis.
 *
 * <p>Every frame is a capture of the visible canvas, so zoom, contrast and
 * overlays are baked in. This is deliberately different from the Inspector's
 * Export button, which re-composites a region from storage at full resolution.
 */
public final class ExportAsDisplayedDlg extends JDialog {

   private static final String FMT_PNG = "PNG";
   private static final String FMT_JPEG = "JPEG";
   private static final String FMT_AVI = "AVI (movie)";
   private static final String FMT_GIF = "GIF (animation)";
   private static final String FMT_MP4 = "MP4 via ffmpeg (movie)";
   private static final String[] FORMATS = {
      FMT_PNG, FMT_JPEG, FMT_AVI, FMT_GIF, FMT_MP4,
   };

   private static final String NO_AXIS = "(single image)";
   /** Give a frame this long to appear before accepting it as unchanged. */
   private static final long FRAME_TIMEOUT_MS = 5000;

   private final Studio studio_;
   private final TiledDataViewerDataViewerAPI viewer_;
   private final JComboBox<String> formatCombo_;
   private final JComboBox<String> axisCombo_;
   private final JSpinner fpsSpinner_;
   private final JSpinner firstFrameSpinner_;
   private final JSpinner lastFrameSpinner_;
   private final SpinnerNumberModel firstFrameModel_;
   private final SpinnerNumberModel lastFrameModel_;
   private final JProgressBar progress_;
   private final JButton saveButton_;
   private final JButton copyButton_;

   /**
    * Builds the export dialog for the given viewer.
    *
    * @param studio the Studio, used for logging and locating ffmpeg
    * @param viewer viewer whose canvas will be captured
    */
   public ExportAsDisplayedDlg(Studio studio, TiledDataViewerDataViewerAPI viewer) {
      super(ownerOf(viewer), "Export As Displayed", ModalityType.MODELESS);
      studio_ = studio;
      viewer_ = viewer;

      JPanel panel = new JPanel(new MigLayout("fillx", "[][grow]", ""));
      panel.add(new JLabel("<html>Saves the image exactly as shown:<br>"
            + "current zoom and viewport, contrast and overlays included.</html>"),
            "span 2, wrap unrelated");

      panel.add(new JLabel("Format:"));
      formatCombo_ = new JComboBox<>(FORMATS);
      formatCombo_.addActionListener(e -> updateEnabledState());
      panel.add(formatCombo_, "growx, wrap");

      panel.add(new JLabel("Step along:"));
      axisCombo_ = new JComboBox<>(loopableAxes());
      axisCombo_.addActionListener(e -> updateFrameRange());
      panel.add(axisCombo_, "growx, wrap");

      panel.add(new JLabel("Frames:"));
      firstFrameModel_ = new SpinnerNumberModel(0, 0, 0, 1);
      firstFrameSpinner_ = new JSpinner(firstFrameModel_);
      lastFrameModel_ = new SpinnerNumberModel(0, 0, 0, 1);
      lastFrameSpinner_ = new JSpinner(lastFrameModel_);
      clampToRange(firstFrameSpinner_, firstFrameModel_);
      clampToRange(lastFrameSpinner_, lastFrameModel_);
      // Keep first <= last however the user drags them.
      firstFrameModel_.addChangeListener(e -> {
         if (intValue(firstFrameModel_) > intValue(lastFrameModel_)) {
            lastFrameModel_.setValue(intValue(firstFrameModel_));
         }
      });
      lastFrameModel_.addChangeListener(e -> {
         if (intValue(lastFrameModel_) < intValue(firstFrameModel_)) {
            firstFrameModel_.setValue(intValue(lastFrameModel_));
         }
      });
      JPanel rangePanel = new JPanel(new MigLayout("insets 0", "[grow][][grow]", ""));
      rangePanel.add(firstFrameSpinner_, "growx");
      rangePanel.add(new JLabel(" to "));
      rangePanel.add(lastFrameSpinner_, "growx");
      panel.add(rangePanel, "growx, wrap");

      panel.add(new JLabel("Frame rate:"));
      fpsSpinner_ = new JSpinner(new SpinnerNumberModel(10.0, 0.1, 120.0, 1.0));
      panel.add(fpsSpinner_, "growx, wrap unrelated");

      progress_ = new JProgressBar(0, 100);
      progress_.setStringPainted(true);
      progress_.setVisible(false);
      panel.add(progress_, "span 2, growx, wrap");

      copyButton_ = new JButton("Copy to Clipboard");
      copyButton_.addActionListener(e -> copyToClipboard());
      panel.add(copyButton_, "growx");

      saveButton_ = new JButton("Save...");
      saveButton_.addActionListener(e -> onSaveClicked());
      panel.add(saveButton_, "growx, wrap");

      add(panel);
      updateEnabledState();
      pack();
      setLocationRelativeTo(getOwner());
   }

   private static Window ownerOf(TiledDataViewerDataViewerAPI viewer) {
      if (viewer == null || viewer.getTiledDataViewer() == null) {
         return null;
      }
      JPanel canvas = viewer.getTiledDataViewer().getCanvasJPanel();
      return canvas == null ? null : SwingUtilities.getWindowAncestor(canvas);
   }

   /** Axes with more than one position, which are the ones worth stepping along. */
   private String[] loopableAxes() {
      List<String> axes = new ArrayList<>();
      axes.add(NO_AXIS);
      try {
         DataProvider dp = viewer_.getDataProvider();
         for (String axis : dp.getAxes()) {
            if (dp.getNextIndex(axis) > 1) {
               axes.add(axis);
            }
         }
      } catch (RuntimeException e) {
         // Leave just the single-image option.
      }
      return axes.toArray(new String[0]);
   }

   private boolean isMovieFormat() {
      String fmt = (String) formatCombo_.getSelectedItem();
      return FMT_AVI.equals(fmt) || FMT_GIF.equals(fmt) || FMT_MP4.equals(fmt);
   }

   private void updateEnabledState() {
      boolean movie = isMovieFormat();
      // Stepping along an axis only applies to a movie: a still image is always
      // a capture of the position currently displayed.
      axisCombo_.setEnabled(movie);
      fpsSpinner_.setEnabled(movie);
      firstFrameSpinner_.setEnabled(movie);
      lastFrameSpinner_.setEnabled(movie);
      copyButton_.setEnabled(!movie);
      if (movie && NO_AXIS.equals(axisCombo_.getSelectedItem())
            && axisCombo_.getItemCount() > 1) {
         axisCombo_.setSelectedIndex(1);
      }
      updateFrameRange();
   }

   /**
    * Re-bounds the first/last frame spinners to the selected axis, keeping any
    * range the user already chose where it still fits.
    */
   private void updateFrameRange() {
      String axis = (String) axisCombo_.getSelectedItem();
      int count = 0;
      if (axis != null && !NO_AXIS.equals(axis)) {
         try {
            count = viewer_.getDataProvider().getNextIndex(axis);
         } catch (RuntimeException e) {
            count = 0;
         }
      }
      if (count < 1) {
         firstFrameModel_.setMaximum(0);
         lastFrameModel_.setMaximum(0);
         firstFrameModel_.setValue(0);
         lastFrameModel_.setValue(0);
         return;
      }
      int max = count - 1;
      firstFrameModel_.setMaximum(max);
      lastFrameModel_.setMaximum(max);
      int first = Math.min(intValue(firstFrameModel_), max);
      int last = intValue(lastFrameModel_);
      // A newly selected axis starts as the full range rather than a stale one.
      if (last <= 0 || last > max) {
         last = max;
      }
      firstFrameModel_.setValue(first);
      lastFrameModel_.setValue(Math.max(first, last));
   }

   private static int intValue(SpinnerNumberModel model) {
      return ((Number) model.getValue()).intValue();
   }

   /**
    * Makes a spinner clamp typed input to its range instead of rejecting it.
    *
    * <p>By default a JSpinner's formatter refuses an out-of-range value and
    * reverts to the previous one, so typing a frame number past the end of the
    * axis silently undoes the edit. Clamping is friendlier here: the intent of
    * typing a large number is plainly "the last frame".
    */
   private static void clampToRange(JSpinner spinner, SpinnerNumberModel model) {
      JComponent editor = spinner.getEditor();
      if (!(editor instanceof JSpinner.DefaultEditor)) {
         return;
      }
      JFormattedTextField field = ((JSpinner.DefaultEditor) editor).getTextField();
      DefaultFormatterFactory factory =
            (DefaultFormatterFactory) field.getFormatterFactory();
      JFormattedTextField.AbstractFormatter delegate = factory.getDefaultFormatter();
      if (!(delegate instanceof NumberFormatter)) {
         return;
      }
      final NumberFormatter base = (NumberFormatter) delegate;
      NumberFormatter clamping = new NumberFormatter(base.getFormat()) {
         @Override
         public Object stringToValue(String text) throws java.text.ParseException {
            Object value = super.stringToValue(text);
            if (!(value instanceof Number)) {
               return value;
            }
            long typed = ((Number) value).longValue();
            long min = ((Number) model.getMinimum()).longValue();
            long max = ((Number) model.getMaximum()).longValue();
            return (int) Math.max(min, Math.min(max, typed));
         }
      };
      // Deliberately no setMinimum/setMaximum on the formatter: those are what
      // reject the input before stringToValue() can clamp it.
      clamping.setValueClass(Integer.class);
      clamping.setAllowsInvalid(true);
      clamping.setCommitsOnValidEdit(false);
      field.setFormatterFactory(new DefaultFormatterFactory(clamping));
   }

   private BufferedImage captureOrWarn() {
      try {
         BufferedImage img = CanvasCapture.capture(viewer_.getTiledDataViewer());
         if (img == null) {
            JOptionPane.showMessageDialog(this,
                  "Nothing to export: the viewer has not displayed an image yet.",
                  "Export As Displayed", JOptionPane.WARNING_MESSAGE);
         }
         return img;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return null;
      }
   }

   private void copyToClipboard() {
      BufferedImage img = captureOrWarn();
      if (img == null) {
         return;
      }
      try {
         java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
               .setContents(new ImageTransferable(img), null);
      } catch (IllegalStateException e) {
         JOptionPane.showMessageDialog(this,
               "The clipboard is unavailable: " + e.getMessage(),
               "Export As Displayed", JOptionPane.ERROR_MESSAGE);
         return;
      }
      dispose();
   }

   private void onSaveClicked() {
      if (isMovieFormat()) {
         saveMovie();
      } else {
         saveStillImage();
      }
   }

   private void saveStillImage() {
      BufferedImage img = captureOrWarn();
      if (img == null) {
         return;
      }
      String format = (String) formatCombo_.getSelectedItem();
      String extension = FMT_JPEG.equals(format) ? "jpg" : "png";
      File file = chooseFile(extension, format + " image");
      if (file == null) {
         return;
      }
      try {
         writeStill(img, extension, file);
      } catch (IOException e) {
         reportError(e, "Could not save the image", file);
         return;
      }
      dispose();
   }

   private void writeStill(BufferedImage img, String extension, File file)
         throws IOException {
      BufferedImage toWrite = img;
      if ("jpg".equals(extension) && img.getType() != BufferedImage.TYPE_INT_RGB) {
         // JPEG has no alpha channel; write an opaque copy rather than fail.
         toWrite = new BufferedImage(img.getWidth(), img.getHeight(),
               BufferedImage.TYPE_INT_RGB);
         toWrite.getGraphics().drawImage(img, 0, 0, null);
      }
      if (!ImageIO.write(toWrite, extension, file)) {
         throw new IOException("No writer available for " + extension);
      }
   }

   private void saveMovie() {
      final String axis = (String) axisCombo_.getSelectedItem();
      if (axis == null || NO_AXIS.equals(axis)) {
         JOptionPane.showMessageDialog(this,
               "Choose an axis to step along for a movie.",
               "Export As Displayed", JOptionPane.WARNING_MESSAGE);
         return;
      }
      final String format = (String) formatCombo_.getSelectedItem();
      final String extension = FMT_AVI.equals(format) ? "avi"
            : FMT_GIF.equals(format) ? "gif" : "mp4";
      final File file = chooseFile(extension, format);
      if (file == null) {
         return;
      }

      final String ffmpegPath;
      if (FMT_MP4.equals(format)) {
         ffmpegPath = org.micromanager.display.internal.gearmenu.FfmpegLocator
               .findOrLocate(studio_, this);
         if (ffmpegPath == null) {
            return; // The locator has already told the user.
         }
      } else {
         ffmpegPath = null;
      }

      final int first = intValue(firstFrameModel_);
      final int last = intValue(lastFrameModel_);
      if (last < first) {
         return; // Cannot happen: the spinners keep first <= last.
      }

      setControlsEnabled(false);
      progress_.setVisible(true);
      progress_.setValue(0);
      pack();

      new Thread(() -> runMovieExport(axis, first, last, format, ffmpegPath, file),
            "TiledDataViewer-Export").start();
   }

   /**
    * Steps the viewer along the axis, capturing each frame, then writes the movie.
    * Runs off the EDT; all UI updates are marshalled back.
    */
   private void runMovieExport(String axis, int firstFrame, int lastFrame,
                               String format, String ffmpegPath, File file) {
      Coords restorePosition = viewer_.getDisplayPosition();
      File tempDir = null;
      final int count = lastFrame - firstFrame + 1;
      try {
         ImageStack stack = null;
         BufferedImage previous = null;
         if (FMT_MP4.equals(format)) {
            tempDir = createTempDir();
         }
         for (int i = firstFrame; i <= lastFrame; i++) {
            Coords target = viewer_.getDisplayPosition().copyBuilder()
                  .index(axis, i).build();
            viewer_.setDisplayPosition(target, true);
            BufferedImage frame =
                  CanvasCapture.captureWhenChanged(viewer_.getTiledDataViewer(),
                        previous, FRAME_TIMEOUT_MS);
            if (frame == null) {
               throw new IOException("The viewer canvas has no size.");
            }
            previous = frame;
            // ffmpeg needs a gap-free sequence starting at 0, so number the temp
            // frames from the start of the exported range, not the axis origin.
            final int frameNumber = i - firstFrame;
            if (tempDir != null) {
               // Lossless intermediate so quality is governed only by the encoder.
               ImageIO.write(frame, "png",
                     new File(tempDir, String.format("frame_%06d.png", frameNumber)));
            } else {
               if (stack == null) {
                  stack = new ImageStack(frame.getWidth(), frame.getHeight());
               }
               stack.addSlice("" + i, new ColorProcessor(frame));
            }
            final int pct = (int) Math.round(100.0 * (frameNumber + 1) / count);
            SwingUtilities.invokeLater(() -> progress_.setValue(pct));
         }

         double fps = ((Number) fpsSpinner_.getValue()).doubleValue();
         if (tempDir != null) {
            runFfmpeg(ffmpegPath, tempDir, file, fps);
         } else {
            ImagePlus imp = new ImagePlus(file.getName(), stack);
            imp.getCalibration().fps = fps;
            if (FMT_AVI.equals(format)) {
               new AVI_Writer().writeImage(imp, file.getAbsolutePath(),
                     AVI_Writer.JPEG_COMPRESSION, 90);
            } else {
               GifWriter.save(imp, file.getAbsolutePath());
            }
         }
         SwingUtilities.invokeLater(this::dispose);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      } catch (Exception e) {
         SwingUtilities.invokeLater(() -> {
            reportError(e, "Could not save the movie", file);
            setControlsEnabled(true);
            progress_.setVisible(false);
         });
      } finally {
         deleteTempDir(tempDir);
         // Put the viewer back where the user left it.
         try {
            viewer_.setDisplayPosition(restorePosition, true);
         } catch (RuntimeException e) {
            // The viewer may be closing; nothing useful to do.
         }
      }
   }

   private static File createTempDir() throws IOException {
      File dir = File.createTempFile("mm-export-", "");
      if (!dir.delete() || !dir.mkdir()) {
         throw new IOException("Could not create a temporary directory for frames");
      }
      return dir;
   }

   private static void deleteTempDir(File dir) {
      if (dir == null) {
         return;
      }
      File[] files = dir.listFiles();
      if (files != null) {
         for (File f : files) {
            if (!f.delete()) {
               f.deleteOnExit();
            }
         }
      }
      if (!dir.delete()) {
         dir.deleteOnExit();
      }
   }

   /**
    * Encodes the captured frames with ffmpeg. Mirrors the main viewer's
    * invocation, including the even-dimension filter libx264 requires.
    */
   private void runFfmpeg(String ffmpegPath, File tempDir, File outputFile, double fps)
         throws IOException {
      List<String> command = new ArrayList<>();
      command.add(ffmpegPath);
      command.add("-y");
      command.add("-framerate");
      command.add(String.valueOf(fps <= 0 ? 10.0 : fps));
      command.add("-i");
      command.add(new File(tempDir, "frame_%06d.png").getAbsolutePath());
      command.add("-vf");
      command.add("scale=trunc(iw/2)*2:trunc(ih/2)*2");
      command.add("-c:v");
      command.add("libx264");
      command.add("-crf");
      command.add("18");
      command.add("-preset");
      command.add("medium");
      command.add("-pix_fmt");
      command.add("yuv420p");
      command.add(outputFile.getAbsolutePath());

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(true);
      Process process;
      try {
         process = pb.start();
      } catch (IOException e) {
         throw new IOException("Failed to start ffmpeg at: " + ffmpegPath, e);
      }
      StringBuilder output = new StringBuilder();
      try (BufferedReader br = new BufferedReader(
            new InputStreamReader(process.getInputStream()))) {
         String line;
         while ((line = br.readLine()) != null) {
            output.append(line).append("\n");
         }
      }
      int exitCode;
      try {
         exitCode = process.waitFor();
      } catch (InterruptedException e) {
         process.destroy();
         Thread.currentThread().interrupt();
         throw new IOException("Interrupted while waiting for ffmpeg", e);
      }
      if (exitCode != 0) {
         throw new IOException("ffmpeg exited with code " + exitCode
               + ".\nffmpeg output:\n" + output);
      }
   }

   private void setControlsEnabled(boolean enabled) {
      saveButton_.setEnabled(enabled);
      formatCombo_.setEnabled(enabled);
      if (enabled) {
         // Restore the per-format enablement rather than enabling everything.
         updateEnabledState();
      } else {
         copyButton_.setEnabled(false);
         axisCombo_.setEnabled(false);
         fpsSpinner_.setEnabled(false);
         firstFrameSpinner_.setEnabled(false);
         lastFrameSpinner_.setEnabled(false);
      }
   }

   /**
    * Prompts for an output file, adding the extension and confirming overwrite.
    *
    * @return the chosen file, or null if the user cancelled
    */
   private File chooseFile(String extension, String description) {
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Save As Displayed");
      chooser.setFileFilter(new FileNameExtensionFilter(description, extension));
      chooser.setSelectedFile(new File(defaultFileName() + "." + extension));
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
         return null;
      }
      File file = chooser.getSelectedFile();
      if (!file.getName().toLowerCase().endsWith("." + extension)) {
         file = new File(file.getParentFile(), file.getName() + "." + extension);
      }
      if (file.exists()) {
         int choice = JOptionPane.showConfirmDialog(this,
               file.getName() + " already exists. Overwrite?",
               "Export As Displayed", JOptionPane.YES_NO_OPTION);
         if (choice != JOptionPane.YES_OPTION) {
            return null;
         }
      }
      return file;
   }

   private void reportError(Exception e, String message, File file) {
      if (studio_ != null) {
         studio_.logs().logError(e, "Export As Displayed: " + message
               + (file == null ? "" : " (" + file + ")"));
      }
      JOptionPane.showMessageDialog(this, message + ":\n" + e.getMessage(),
            "Export As Displayed", JOptionPane.ERROR_MESSAGE);
   }

   /** Builds a default file name from the dataset name and displayed position. */
   private String defaultFileName() {
      StringBuilder sb = new StringBuilder();
      String name = viewer_.getName();
      sb.append(name == null || name.isEmpty() ? "image" : name);
      if (!isMovieFormat()) {
         try {
            Coords pos = viewer_.getDisplayPosition();
            for (String axis : pos.getAxes()) {
               sb.append("_").append(axis).append(pos.getIndex(axis));
            }
         } catch (RuntimeException e) {
            // No position available; the dataset name alone is a fine default.
         }
      }
      return sb.toString().replaceAll("[\\\\/:*?\"<>|]", "_");
   }

   /** Wraps an image so it can be put on the system clipboard. */
   private static final class ImageTransferable implements Transferable {
      private final BufferedImage image_;

      ImageTransferable(BufferedImage image) {
         image_ = image;
      }

      @Override
      public DataFlavor[] getTransferDataFlavors() {
         return new DataFlavor[]{DataFlavor.imageFlavor};
      }

      @Override
      public boolean isDataFlavorSupported(DataFlavor flavor) {
         return DataFlavor.imageFlavor.equals(flavor);
      }

      @Override
      public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
         if (!isDataFlavorSupported(flavor)) {
            throw new UnsupportedFlavorException(flavor);
         }
         return image_;
      }
   }
}
