package org.micromanager.orthogonalviewer;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.GifWriter;
import ij.plugin.filter.AVI_Writer;
import ij.process.ColorProcessor;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.display.internal.gearmenu.FfmpegLocator;

/**
 * Export dialog for the Orthogonal Viewer.
 *
 * <p>Exports a composite tiled image (XY/YZ/XZ panels with overlays) for each
 * step along a selected axis (Z, T, or P). Supports the same output formats as
 * the standard MM Export As Displayed dialog: PNG, JPEG, GIF, AVI, ImageJ
 * stack window, System Clipboard, and Movie (ffmpeg).</p>
 */
public class OrthogonalExportDlg extends JDialog {

   private static final String FORMAT_PNG = "PNG";
   private static final String FORMAT_JPEG = "JPEG";
   private static final String FORMAT_GIF = "GIF";
   private static final String FORMAT_AVI = "AVI";
   private static final String FORMAT_IMAGEJ = "ImageJ stack window";
   private static final String FORMAT_CLIPBOARD = "System Clipboard";
   private static final String FORMAT_MOVIE = "Movie (ffmpeg)";

   private static final String AXIS_Z = "Z";
   private static final String AXIS_T = "T";
   private static final String AXIS_P = "P";

   private final OrthogonalViewerFrame viewer_;
   private final Studio studio_;

   private JComboBox<String> formatSelector_;
   private JComboBox<String> axisSelector_;
   private JSpinner fromSpinner_;
   private JSpinner toSpinner_;
   private SpinnerNumberModel fromModel_;
   private SpinnerNumberModel toModel_;
   private JLabel dirLabel_;
   private JTextField dirField_;
   private JButton browseBtn_;
   private JLabel prefixLabel_;
   private JTextField prefixField_;
   private JLabel nameLabel_;
   private JTextField nameField_;
   private JLabel qualityLabel_;
   private JSpinner qualitySpinner_;
   private JPanel qualityPanel_;

   public OrthogonalExportDlg(OrthogonalViewerFrame viewer, Studio studio) {
      super(viewer.getAsWindow(), "Export Images as Displayed",
            java.awt.Dialog.ModalityType.APPLICATION_MODAL);
      viewer_ = viewer;
      studio_ = studio;

      JPanel content = new JPanel(new MigLayout(
            "insets 10, gap 6 6", "[right][grow, fill][]"));

      // --- Format selector ---
      content.add(new JLabel("Output format:"), "");
      formatSelector_ = new JComboBox<>(new String[]{
            FORMAT_PNG, FORMAT_JPEG, FORMAT_GIF, FORMAT_AVI,
            FORMAT_IMAGEJ, FORMAT_CLIPBOARD, FORMAT_MOVIE});
      content.add(formatSelector_, "spanx 2, wrap");

      // --- Axis selector ---
      content.add(new JLabel("Iterate over:"), "");
      List<String> availableAxes = buildAvailableAxes();
      axisSelector_ = new JComboBox<>(availableAxes.toArray(new String[0]));
      content.add(axisSelector_, "");

      fromSpinner_ = new JSpinner();
      toSpinner_ = new JSpinner();
      // Initialise models based on the first axis
      updateAxisRange((String) axisSelector_.getSelectedItem());
      content.add(new JLabel(""), "wrap");

      // From/To row
      content.add(new JLabel("From:"), "");
      content.add(fromSpinner_, "split 3");
      content.add(new JLabel("To:"));
      content.add(toSpinner_, "wrap");

      // --- Output directory (PNG/JPEG/AVI/GIF/Movie) ---
      dirLabel_ = new JLabel("Output directory:");
      content.add(dirLabel_, "");
      dirField_ = new JTextField(25);
      content.add(dirField_, "growx");
      browseBtn_ = new JButton("Browse…");
      browseBtn_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            browseForDir();
         }
      });
      content.add(browseBtn_, "wrap");

      // --- Filename prefix (PNG/JPEG/ImageJ) ---
      prefixLabel_ = new JLabel("Filename prefix:");
      content.add(prefixLabel_, "");
      prefixField_ = new JTextField("ortho", 15);
      content.add(prefixField_, "spanx 2, wrap");

      // --- ImageJ window name ---
      nameLabel_ = new JLabel("ImageJ name:");
      content.add(nameLabel_, "");
      nameField_ = new JTextField("Orthogonal Views", 15);
      content.add(nameField_, "spanx 2, wrap");

      // --- JPEG/AVI/Movie quality ---
      qualityLabel_ = new JLabel("JPEG quality (%):");
      content.add(qualityLabel_, "");
      qualityPanel_ = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
      qualitySpinner_ = new JSpinner(new SpinnerNumberModel(90, 1, 100, 1));
      qualityPanel_.add(qualitySpinner_);
      content.add(qualityPanel_, "spanx 2, wrap");

      // --- Buttons ---
      JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
      buttons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
      JButton exportBtn = new JButton("Export");
      exportBtn.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            startExport();
         }
      });
      JButton cancelBtn = new JButton("Cancel");
      cancelBtn.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            dispose();
         }
      });
      buttons.add(exportBtn);
      buttons.add(cancelBtn);

      getContentPane().add(content, BorderLayout.CENTER);
      getContentPane().add(buttons, BorderLayout.SOUTH);
      getRootPane().setDefaultButton(exportBtn);

      // Wire format selector — show/hide controls based on selection
      formatSelector_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            updateControlVisibility();
         }
      });
      axisSelector_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            updateAxisRange((String) axisSelector_.getSelectedItem());
         }
      });

      updateControlVisibility();
      pack();
      setResizable(false);
      setLocationRelativeTo(viewer.getAsWindow());
   }

   private List<String> buildAvailableAxes() {
      List<String> axes = new ArrayList<String>();
      if (viewer_.hasZ() && viewer_.getNumZSlices() > 1) {
         axes.add(AXIS_Z);
      }
      if (viewer_.getNumTimePoints() > 1) {
         axes.add(AXIS_T);
      }
      if (viewer_.getNumPositions() > 1) {
         axes.add(AXIS_P);
      }
      if (axes.isEmpty()) {
         axes.add(AXIS_Z); // always show at least one option
      }
      return axes;
   }

   private void updateAxisRange(String axis) {
      int max = axisLength(axis);
      fromModel_ = new SpinnerNumberModel(1, 1, max, 1);
      toModel_ = new SpinnerNumberModel(max, 1, max, 1);
      fromSpinner_.setModel(fromModel_);
      toSpinner_.setModel(toModel_);
      fromModel_.addChangeListener(e -> {
         int from = (Integer) fromSpinner_.getValue();
         if ((Integer) toSpinner_.getValue() < from) {
            toSpinner_.setValue(from);
         }
      });
      toModel_.addChangeListener(e -> {
         int to = (Integer) toSpinner_.getValue();
         if ((Integer) fromSpinner_.getValue() > to) {
            fromSpinner_.setValue(to);
         }
      });
   }

   private int axisLength(String axis) {
      if (AXIS_Z.equals(axis)) {
         return Math.max(1, viewer_.getNumZSlices());
      } else if (AXIS_T.equals(axis)) {
         return Math.max(1, viewer_.getNumTimePoints());
      } else {
         return Math.max(1, viewer_.getNumPositions());
      }
   }

   private void updateControlVisibility() {
      String fmt = (String) formatSelector_.getSelectedItem();
      boolean needsDir = FORMAT_PNG.equals(fmt) || FORMAT_JPEG.equals(fmt)
            || FORMAT_GIF.equals(fmt) || FORMAT_AVI.equals(fmt) || FORMAT_MOVIE.equals(fmt);
      final boolean needsPrefix = FORMAT_PNG.equals(fmt) || FORMAT_JPEG.equals(fmt);
      final boolean needsName = FORMAT_IMAGEJ.equals(fmt);
      final boolean needsQuality = FORMAT_JPEG.equals(fmt) || FORMAT_AVI.equals(fmt)
            || FORMAT_MOVIE.equals(fmt);

      dirLabel_.setVisible(needsDir);
      dirField_.setVisible(needsDir);
      browseBtn_.setVisible(needsDir);
      prefixLabel_.setVisible(needsPrefix);
      prefixField_.setVisible(needsPrefix);
      nameLabel_.setVisible(needsName);
      nameField_.setVisible(needsName);
      qualityLabel_.setVisible(needsQuality);
      qualityPanel_.setVisible(needsQuality);

      // Clipboard: hide axis range too — export only current state
      boolean showAxis = !FORMAT_CLIPBOARD.equals(fmt);
      axisSelector_.setVisible(showAxis);
      fromSpinner_.setVisible(showAxis);
      toSpinner_.setVisible(showAxis);

      pack();
   }

   private void browseForDir() {
      JFileChooser chooser = new JFileChooser();
      chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      chooser.setDialogTitle("Select output directory");
      String cur = dirField_.getText().trim();
      if (!cur.isEmpty()) {
         chooser.setCurrentDirectory(new File(cur));
      }
      if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
         dirField_.setText(chooser.getSelectedFile().getAbsolutePath());
      }
   }

   private void startExport() {
      String fmt = (String) formatSelector_.getSelectedItem();

      // Validate directory for formats that need one
      String dir = dirField_.getText().trim();
      if (needsDirectory(fmt)) {
         if (dir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an output directory.",
                  "No Directory", JOptionPane.WARNING_MESSAGE);
            return;
         }
         File dirFile = new File(dir);
         if (!dirFile.exists() && !dirFile.mkdirs()) {
            JOptionPane.showMessageDialog(this,
                  "Could not create directory:\n" + dir,
                  "Error", JOptionPane.ERROR_MESSAGE);
            return;
         }
      }

      // Locate ffmpeg if needed
      final String ffmpegPath;
      if (FORMAT_MOVIE.equals(fmt)) {
         ffmpegPath = FfmpegLocator.findOrLocate(studio_, this);
         if (ffmpegPath == null) {
            return;
         }
      } else {
         ffmpegPath = null;
      }

      // Gather export parameters
      final String finalFmt = fmt;
      final String finalDir = dir;
      final String prefix = prefixField_.getText().trim();
      final String ijName = nameField_.getText().trim();
      final int quality = (Integer) qualitySpinner_.getValue();
      final String axis = (String) axisSelector_.getSelectedItem();
      final int fromIdx = FORMAT_CLIPBOARD.equals(fmt)
            ? getCurrentAxisValue(axis) : (Integer) fromSpinner_.getValue() - 1;
      final int toIdx = FORMAT_CLIPBOARD.equals(fmt)
            ? getCurrentAxisValue(axis) : (Integer) toSpinner_.getValue() - 1;

      dispose();

      // Run export in background thread
      Thread exportThread = new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               doExport(finalFmt, finalDir, prefix, ijName, quality,
                     axis, fromIdx, toIdx, ffmpegPath);
            } catch (Exception ex) {
               final String msg = ex.getMessage();
               SwingUtilities.invokeLater(new Runnable() {
                  @Override
                  public void run() {
                     JOptionPane.showMessageDialog(viewer_.getAsWindow(),
                           "Export failed:\n" + msg, "Export Error",
                           JOptionPane.ERROR_MESSAGE);
                  }
               });
            }
         }
      }, "OrthogonalExport");
      exportThread.setDaemon(true);
      exportThread.start();
   }

   private boolean needsDirectory(String fmt) {
      return FORMAT_PNG.equals(fmt) || FORMAT_JPEG.equals(fmt) || FORMAT_GIF.equals(fmt)
            || FORMAT_AVI.equals(fmt) || FORMAT_MOVIE.equals(fmt);
   }

   private int getCurrentAxisValue(String axis) {
      // Returns the current crosshair/scroll position for the axis (0-based)
      if (AXIS_Z.equals(axis)) {
         return viewer_.getCrosshairZ();
      } else if (AXIS_T.equals(axis)) {
         return viewer_.getCurrentTime();
      } else {
         return viewer_.getCurrentPosition();
      }
   }

   private void doExport(String fmt, String dir, String prefix, String ijName,
                         int quality, String axis,
                         int fromIdx, int toIdx, String ffmpegPath) throws IOException {
      int fixedZ = viewer_.getCrosshairZ();
      int fixedT = viewer_.getCurrentTime();
      int fixedP = viewer_.getCurrentPosition();

      // Collect frames
      List<BufferedImage> frames = new ArrayList<BufferedImage>();
      int numFrames = toIdx - fromIdx + 1;
      for (int i = fromIdx; i <= toIdx; i++) {
         int z = AXIS_Z.equals(axis) ? i : fixedZ;
         int t = AXIS_T.equals(axis) ? i : fixedT;
         int p = AXIS_P.equals(axis) ? i : fixedP;
         BufferedImage frame = viewer_.renderCompositeForExport(z, t, p);
         if (frame != null) {
            frames.add(frame);
         }
      }

      if (frames.isEmpty()) {
         showInfo("No images could be rendered for export.");
         return;
      }

      if (FORMAT_CLIPBOARD.equals(fmt)) {
         final BufferedImage img = frames.get(0);
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               Toolkit.getDefaultToolkit().getSystemClipboard()
                     .setContents(new TransferableImage(img), null);
            }
         });
         showInfo("Image copied to clipboard.");
         return;
      }

      if (FORMAT_IMAGEJ.equals(fmt)) {
         final ImageStack stack = new ImageStack(
               frames.get(0).getWidth(), frames.get(0).getHeight());
         for (BufferedImage f : frames) {
            stack.addSlice(new ColorProcessor(f));
         }
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               new ImagePlus(ijName.isEmpty() ? "Orthogonal Views" : ijName, stack).show();
            }
         });
         return;
      }

      if (FORMAT_PNG.equals(fmt)) {
         for (int i = 0; i < frames.size(); i++) {
            String label = numFrames > 1 ? String.format("_%06d", i + 1) : "";
            File f = new File(dir, prefix + label + ".png");
            ImageIO.write(frames.get(i), "png", f);
         }
         showInfo(frames.size() + " PNG file(s) saved to:\n" + dir);
         return;
      }

      if (FORMAT_JPEG.equals(fmt)) {
         float q = quality / 100f;
         for (int i = 0; i < frames.size(); i++) {
            String label = numFrames > 1 ? String.format("_%06d", i + 1) : "";
            File f = new File(dir, prefix + label + ".jpg");
            writeJpeg(frames.get(i), f, q);
         }
         showInfo(frames.size() + " JPEG file(s) saved to:\n" + dir);
         return;
      }

      if (FORMAT_GIF.equals(fmt) || FORMAT_AVI.equals(fmt)) {
         ImageStack stack = new ImageStack(
               frames.get(0).getWidth(), frames.get(0).getHeight());
         for (BufferedImage f : frames) {
            stack.addSlice(new ColorProcessor(f));
         }
         String ext = FORMAT_GIF.equals(fmt) ? ".gif" : ".avi";
         File outFile = new File(dir, prefix + ext);
         ImagePlus imp = new ImagePlus(prefix, stack);
         if (FORMAT_AVI.equals(fmt)) {
            new AVI_Writer().writeImage(imp, outFile.getAbsolutePath(),
                  AVI_Writer.JPEG_COMPRESSION, quality);
         } else {
            GifWriter.save(imp, outFile.getAbsolutePath());
         }
         showInfo("Saved to:\n" + outFile.getAbsolutePath());
         return;
      }

      if (FORMAT_MOVIE.equals(fmt)) {
         // Write frames as temp PNGs then invoke ffmpeg
         String tmpDirPath = System.getProperty("java.io.tmpdir")
               + File.separator + "mm_ortho_export_" + System.currentTimeMillis();
         File tmpDir = new File(tmpDirPath);
         if (!tmpDir.mkdirs()) {
            throw new IOException("Could not create temp directory: " + tmpDirPath);
         }
         try {
            for (int i = 0; i < frames.size(); i++) {
               File f = new File(tmpDir, String.format("frame_%06d.png", i + 1));
               ImageIO.write(frames.get(i), "png", f);
            }
            File outFile = new File(dir, prefix + ".mp4");
            runFfmpeg(ffmpegPath, tmpDir, outFile, quality);
            showInfo("Movie saved to:\n" + outFile.getAbsolutePath());
         } finally {
            deleteTempDir(tmpDir);
         }
      }
   }

   private void writeJpeg(BufferedImage img, File file, float quality) throws IOException {
      ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(quality);
      ImageOutputStream stream = ImageIO.createImageOutputStream(file);
      writer.setOutput(stream);
      writer.write(null,
            new javax.imageio.IIOImage(img, null, null), param);
      stream.close();
      writer.dispose();
   }

   private void runFfmpeg(String ffmpegPath, File tmpDir, File outFile, int quality)
         throws IOException {
      int crf = (int) Math.round(51.0 * (1.0 - (quality - 1) / 99.0));
      String framePattern = new File(tmpDir, "frame_%06d.png").getAbsolutePath();

      List<String> cmd = new ArrayList<String>();
      cmd.add(ffmpegPath);
      cmd.add("-framerate");
      cmd.add("10");
      cmd.add("-i");
      cmd.add(framePattern);
      cmd.add("-vf");
      cmd.add("scale=trunc(iw/2)*2:trunc(ih/2)*2");
      cmd.add("-c:v");
      cmd.add("libx264");
      cmd.add("-crf");
      cmd.add(String.valueOf(crf));
      cmd.add("-preset");
      cmd.add("medium");
      cmd.add("-pix_fmt");
      cmd.add("yuv420p");
      cmd.add(outFile.getAbsolutePath());

      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      Process proc = pb.start();
      StringBuilder out = new StringBuilder();
      try {
         BufferedReader br = new BufferedReader(
               new InputStreamReader(proc.getInputStream()));
         String line;
         while ((line = br.readLine()) != null) {
            out.append(line).append("\n");
         }
      } catch (IOException ex) {
         // ignore read errors
      }
      int code;
      try {
         code = proc.waitFor();
      } catch (InterruptedException ex) {
         proc.destroy();
         Thread.currentThread().interrupt();
         throw new IOException("Interrupted waiting for ffmpeg", ex);
      }
      if (code != 0) {
         throw new IOException("ffmpeg exited with code " + code + "\n" + out);
      }
   }

   private void deleteTempDir(File tmpDir) {
      File[] files = tmpDir.listFiles();
      if (files != null) {
         for (File f : files) {
            f.delete();
         }
      }
      tmpDir.delete();
   }

   private void showInfo(final String msg) {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            JOptionPane.showMessageDialog(viewer_.getAsWindow(), msg,
                  "Export Complete", JOptionPane.INFORMATION_MESSAGE);
         }
      });
   }

   private static class TransferableImage implements Transferable {
      private final java.awt.Image img_;

      TransferableImage(java.awt.Image img) {
         img_ = img;
      }

      @Override
      public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException {
         if (flavor.equals(DataFlavor.imageFlavor)) {
            return img_;
         }
         throw new UnsupportedFlavorException(flavor);
      }

      @Override
      public DataFlavor[] getTransferDataFlavors() {
         return new DataFlavor[]{DataFlavor.imageFlavor};
      }

      @Override
      public boolean isDataFlavorSupported(DataFlavor flavor) {
         return flavor.equals(DataFlavor.imageFlavor);
      }
   }
}
