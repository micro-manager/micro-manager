package org.micromanager.orthogonalviewer;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.GifWriter;
import ij.plugin.filter.AVI_Writer;
import ij.process.ColorProcessor;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.ApplicationSkin;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.display.ImageExporter;
import org.micromanager.display.internal.gearmenu.AxisPanelParent;
import org.micromanager.display.internal.gearmenu.ExportMovieDlg;
import org.micromanager.display.internal.gearmenu.FfmpegLocator;
import org.micromanager.internal.utils.FileDialogs;

/**
 * Export dialog for the Orthogonal Viewer.
 *
 * <p>Mirrors the layout and preferences of the standard MM Export As Displayed dialog.
 * Exports a composite tiled image (XY/YZ/XZ panels with overlays) for each step
 * along a selected axis (Z, T, or P). The channel axis is not offered because the
 * orthogonal viewer handles channels internally in its composite/grayscale modes.
 * Reuses {@link ExportMovieDlg.AxisPanel} for axis/range selection and shares the
 * same profile preferences keys so the
 * user's last-used format, prefix, and quality are remembered.</p>
 */
public class OrthogonalExportDlg extends JDialog implements AxisPanelParent {

   // Profile keys — deliberately the same class as ExportMovieDlg so preferences are shared
   private static final String DEFAULT_EXPORT_FORMAT =
         "default format to use for exporting image sequences";
   private static final String DEFAULT_FILENAME_PREFIX =
         "default prefix to use for files when exporting image sequences";
   private static final String JPEG_QUALITY = "JPEG quality";
   private static final String DEFAULT_USE_LABEL = "Use Label";
   private static final String EXPORT_LOCATION = "Export Location";

   private static final String FORMAT_PNG = "PNG";
   private static final String FORMAT_JPEG = "JPEG";
   private static final String FORMAT_AVI = "AVI";
   private static final String FORMAT_GIF = "GIF";
   private static final String FORMAT_IMAGEJ = "ImageJ stack window";
   private static final String FORMAT_SYSTEM_CLIPBOARD = "System Clipboard";
   private static final String FORMAT_MOVIE = "Movie (ffmpeg)";
   private static final String[] OUTPUT_FORMATS = {
         FORMAT_PNG, FORMAT_JPEG, FORMAT_AVI, FORMAT_GIF,
         FORMAT_IMAGEJ, FORMAT_SYSTEM_CLIPBOARD, FORMAT_MOVIE};

   private final OrthogonalViewerFrame viewer_;
   private final Studio studio_;
   private final ApplicationSkin skin_;
   private final ArrayList<ExportMovieDlg.AxisPanel> axisPanels_;
   private final JPanel contentsPanel_;

   private JComboBox<String> outputFormatSelector_;
   private JLabel prefixLabel_;
   private JTextField prefixText_;
   private JPanel jpegPanel_;
   private JSpinner jpegQualitySpinner_;

   /**
    * Build and show the export dialog.
    *
    * @param viewer the orthogonal viewer to export from
    * @param studio the Studio instance (for profile prefs and skin)
    */
   public OrthogonalExportDlg(OrthogonalViewerFrame viewer, Studio studio) {
      super();
      java.net.URL iconUrl = getClass().getResource("/org/micromanager/icons/microscope.gif");
      if (iconUrl != null) {
         setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
      }
      viewer_ = viewer;
      studio_ = studio;
      skin_ = studio.app().getApplicationSkin();
      axisPanels_ = new ArrayList<ExportMovieDlg.AxisPanel>();

      // Centre over the viewer window (matches original behaviour)
      Window vw = viewerWindow();
      final int centerX = (vw != null) ? vw.getX() + vw.getWidth() / 2 : 400;
      final int centerY = (vw != null) ? vw.getY() + vw.getHeight() / 2 : 300;

      setTitle("Export Image Series: " + new File(viewer.getName()).getName());

      contentsPanel_ = new JPanel(new MigLayout("flowy"));

      contentsPanel_.add(new JLabel(
            "<html><body>Export a series of images from your dataset. The images will be "
                  + "exactly as currently<br>drawn on your display, including histogram "
                  + "scaling, overlays, etc. Each frame is a tiled<br>composite of the XY, "
                  + "YZ, and XZ views. Note that this does not preserve the raw data.</body>"
                  + "</html>"), "align center");

      jpegQualitySpinner_ = new JSpinner();
      jpegQualitySpinner_.setModel(new SpinnerNumberModel(getJpegQuality(), 1, 100, 1));
      final JCheckBox useLabel = new JCheckBox("Use label in filename");

      // Format selector row — identical layout to original ("split 4, flowx")
      contentsPanel_.add(new JLabel("Output format: "), "split 4, flowx");
      outputFormatSelector_ = new JComboBox<>(OUTPUT_FORMATS);

      ExportMovieDlg.AxisPanel ap = null;
      if (!getNonZeroAxes().isEmpty()) {
         ap = createAxisPanel();
      }
      final ExportMovieDlg.AxisPanel axisPanel = ap;

      outputFormatSelector_.addActionListener(e -> {
         String selection = (String) outputFormatSelector_.getSelectedItem();
         // Quality panel: show for JPEG/AVI/Movie
         jpegPanel_.removeAll();
         if (selection.equals(FORMAT_JPEG) || selection.equals(FORMAT_AVI)
               || selection.equals(FORMAT_MOVIE)) {
            String qualLabel = selection.equals(FORMAT_MOVIE)
                  ? "Quality(%): " : "JPEG quality(%): ";
            jpegPanel_.add(new JLabel(qualLabel));
            jpegPanel_.add(jpegQualitySpinner_);
         }
         // Prefix/name row: show for PNG, JPEG, ImageJ
         boolean usePrefix = selection.equals(FORMAT_PNG) || selection.equals(FORMAT_JPEG)
               || selection.equals(FORMAT_IMAGEJ);
         prefixLabel_.setText(selection.equals(FORMAT_IMAGEJ)
               ? "ImageJ Name: " : "Filename prefix: ");
         prefixLabel_.setEnabled(usePrefix);
         prefixLabel_.setVisible(usePrefix);
         prefixText_.setEnabled(usePrefix);
         prefixText_.setVisible(usePrefix);
         useLabel.setVisible(usePrefix);
         if (axisPanel != null) {
            axisPanel.setVisible(!selection.equals(FORMAT_SYSTEM_CLIPBOARD));
         }
         pack();
      });
      contentsPanel_.add(outputFormatSelector_);

      prefixLabel_ = new JLabel("Filename prefix: ");
      contentsPanel_.add(prefixLabel_);

      prefixText_ = new JTextField(getDefaultPrefix(), 20);
      contentsPanel_.add(prefixText_, "grow 0");

      jpegPanel_ = new JPanel(new MigLayout("flowx, gap 0", "0[]0[]0", "0[]0[]0"));
      contentsPanel_.add(jpegPanel_);

      if (getNonZeroAxes().isEmpty()) {
         contentsPanel_.add(
               new JLabel("There is only one image available to export."),
               "align center");
      } else {
         contentsPanel_.add(axisPanel);
      }

      useLabel.setSelected(studio_.profile().getSettings(ExportMovieDlg.class)
            .getBoolean(DEFAULT_USE_LABEL, true));
      ChangeListener useLabelChange = e -> studio_.profile()
            .getSettings(ExportMovieDlg.class)
            .putBoolean(DEFAULT_USE_LABEL, useLabel.isSelected());
      useLabel.addChangeListener(useLabelChange);

      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(e -> dispose());
      JButton exportButton = new JButton("Export");
      exportButton.addActionListener(e -> {
         export(useLabel.isSelected());
      });
      contentsPanel_.add(useLabel, "split 3, flowx, align right");
      contentsPanel_.add(cancelButton);
      contentsPanel_.add(exportButton);

      getContentPane().add(contentsPanel_);
      outputFormatSelector_.setSelectedItem(getDefaultExportFormat());
      pack();
      setLocation(centerX - getWidth() / 2, centerY - getHeight() / 2);
   }

   // ---- Axis panel support (mirrors ExportMovieDlg) ----

   /**
    * Returns the axes in the data provider that have more than one value.
    * Excludes the channel axis (the orthogonal viewer handles channels internally).
    */
   @Override
   public ArrayList<String> getNonZeroAxes() {
      ArrayList<String> result = new ArrayList<String>();
      for (String axis : viewer_.getDataProvider().getAxes()) {
         if (viewer_.getDataProvider().getNextIndex(axis) > 1
               && !axis.equals(Coords.CHANNEL)) {
            result.add(axis);
         }
      }
      return result;
   }

   /**
    * Create an AxisPanel for the next unused axis and register it.
    */
   @Override
   public ExportMovieDlg.AxisPanel createAxisPanel() {
      java.util.HashSet<String> axes = new java.util.HashSet<String>(getNonZeroAxes());
      for (ExportMovieDlg.AxisPanel panel : axisPanels_) {
         axes.remove(panel.getAxis());
      }
      if (axes.isEmpty()) {
         return null;
      }
      ExportMovieDlg.AxisPanel panel = new ExportMovieDlg.AxisPanel(viewer_, this);
      panel.setAxis(new ArrayList<String>(axes).get(0));
      axisPanels_.add(panel);
      return panel;
   }

   /** Called by AxisPanel when the user changes the selected axis. */
   @Override
   public void changeAxis(String oldAxis, String newAxis) {
      for (ExportMovieDlg.AxisPanel panel : axisPanels_) {
         if (panel.getAxis().equals(newAxis)) {
            panel.setAxis(oldAxis);
         }
      }
   }

   /** Called by AxisPanel when the user removes an inner loop. */
   @Override
   public void deleteFollowing(ExportMovieDlg.AxisPanel last) {
      boolean shouldRemove = false;
      java.util.HashSet<ExportMovieDlg.AxisPanel> defuncts =
            new java.util.HashSet<ExportMovieDlg.AxisPanel>();
      for (ExportMovieDlg.AxisPanel panel : axisPanels_) {
         if (shouldRemove) {
            defuncts.add(panel);
         }
         if (panel == last) {
            shouldRemove = true;
         }
      }
      for (ExportMovieDlg.AxisPanel panel : defuncts) {
         axisPanels_.remove(panel);
      }
      pack();
   }

   /** Returns the number of axes not yet assigned to any AxisPanel. */
   @Override
   public int getNumSpareAxes() {
      return getNonZeroAxes().size() - axisPanels_.size();
   }

   // ---- Profile preferences (same keys as ExportMovieDlg so they are shared) ----

   private String getDefaultExportFormat() {
      return studio_.profile().getSettings(ExportMovieDlg.class)
            .getString(DEFAULT_EXPORT_FORMAT, FORMAT_PNG);
   }

   private void setDefaultExportFormat(String format) {
      studio_.profile().getSettings(ExportMovieDlg.class)
            .putString(DEFAULT_EXPORT_FORMAT, format);
   }

   private String getDefaultPrefix() {
      return studio_.profile().getSettings(ExportMovieDlg.class)
            .getString(DEFAULT_FILENAME_PREFIX, "exported");
   }

   private void setDefaultPrefix(String prefix) {
      studio_.profile().getSettings(ExportMovieDlg.class)
            .putString(DEFAULT_FILENAME_PREFIX, prefix);
   }

   private int getJpegQuality() {
      return studio_.profile().getSettings(ExportMovieDlg.class)
            .getInteger(JPEG_QUALITY, 90);
   }

   private void setJpegQuality(int quality) {
      studio_.profile().getSettings(ExportMovieDlg.class)
            .putInteger(JPEG_QUALITY, quality);
   }

   // ---- Export action ----

   private void export(boolean useLabel) {
      String mode = (String) outputFormatSelector_.getSelectedItem();

      // Locate ffmpeg before touching the file chooser
      String ffmpegPath = null;
      if (mode.equals(FORMAT_MOVIE)) {
         ffmpegPath = FfmpegLocator.findOrLocate(studio_, this);
         if (ffmpegPath == null) {
            return;
         }
      }

      // Determine suffix for file choosers
      String suffix = "png";
      if (mode.equals(FORMAT_JPEG)) {
         suffix = "jpg";
      } else if (mode.equals(FORMAT_AVI)) {
         suffix = "avi";
      } else if (mode.equals(FORMAT_GIF)) {
         suffix = "gif";
      } else if (mode.equals(FORMAT_MOVIE)) {
         suffix = "mp4";
      }
      final String[] suffixes = {suffix};

      // Default starting directory — same logic as original
      String base = System.getProperty("user.home");
      base = studio_.profile().getSettings(ExportMovieDlg.class)
            .getString(EXPORT_LOCATION, base);
      File basePath = new File(base);

      // Collect (z, t, p) tuples to export using AxisPanel.configureExporter().
      // We use int[] rather than Coords to avoid the DefaultCoords gotcha where
      // index(axis, 0) silently drops the axis, corrupting hasAxis() checks.
      final int baseZ = viewer_.getCrosshairZ();
      final int baseT = viewer_.getCurrentTime();
      final int baseP = viewer_.getCurrentPosition();
      final ArrayList<int[]> frames = new ArrayList<int[]>();
      if (mode.equals(FORMAT_SYSTEM_CLIPBOARD)) {
         frames.add(new int[]{baseZ, baseT, baseP});
      } else if (!axisPanels_.isEmpty()) {
         CoordCollector collector = new CoordCollector(viewer_.getDataProvider());
         axisPanels_.get(0).configureExporter(collector);
         collector.collectFrames(baseZ, baseT, baseP, frames);
      } else {
         frames.add(new int[]{baseZ, baseT, baseP});
      }

      // Show file/directory chooser and get output path info
      final String finalFfmpegPath = ffmpegPath;
      final String finalMode = mode;
      final String finalSuffix = suffix;
      final int quality = (Integer) jpegQualitySpinner_.getValue();
      final String prefix = prefixText_.getText();
      final String ijName = prefixText_.getText();

      String directory = null;
      String filePrefix = null;

      if (mode.equals(FORMAT_PNG) || mode.equals(FORMAT_JPEG)) {
         File outputDir = FileDialogs.promptForFile(this, "Export as", basePath,
               true, false, "", suffixes, false, skin_);
         if (outputDir == null) {
            return;
         }
         studio_.profile().getSettings(ExportMovieDlg.class)
               .putString(EXPORT_LOCATION, outputDir.getAbsolutePath());
         directory = outputDir.getAbsolutePath();
         filePrefix = prefix;
      } else if (mode.equals(FORMAT_AVI) || mode.equals(FORMAT_GIF)
            || mode.equals(FORMAT_MOVIE)) {
         String title = mode.equals(FORMAT_AVI) ? "Save AVI as"
               : mode.equals(FORMAT_GIF) ? "Save GIF as" : "Save movie as";
         File output = FileDialogs.promptForFile(this, title, basePath,
               false, false, "", suffixes, false, skin_);
         if (output == null) {
            return;
         }
         if (output.exists()) {
            studio_.logs().showMessage("File already exists", this);
            return;
         }
         studio_.profile().getSettings(ExportMovieDlg.class)
               .putString(EXPORT_LOCATION, output.getParent());
         directory = output.getParent();
         String name = output.getName();
         if (name.endsWith("." + finalSuffix)) {
            name = name.substring(0, name.length() - finalSuffix.length() - 1);
         }
         filePrefix = name;
      } else if (mode.equals(FORMAT_IMAGEJ)) {
         // ImageJ window name comes from the prefix field; no file chooser needed
         filePrefix = ijName;
      }
      // FORMAT_SYSTEM_CLIPBOARD: no file chooser needed

      // Save preferences
      setDefaultExportFormat(mode);
      setDefaultPrefix(prefix);
      setJpegQuality(quality);

      dispose();

      // Run the actual rendering + writing in a background thread
      final String finalDir = directory;
      final String finalPrefix = filePrefix;
      final ArrayList<int[]> finalFrames = frames;

      Thread exportThread = new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               doExport(finalMode, finalDir, finalPrefix, quality,
                     finalFrames, finalFfmpegPath, useLabel);
            } catch (IOException ex) {
               final String msg = ex.getMessage();
               SwingUtilities.invokeLater(new Runnable() {
                  @Override
                  public void run() {
                     studio_.logs().showError(ex,
                           "Export failed: " + msg, viewerWindow());
                  }
               });
            }
         }
      }, "OrthogonalExport");
      exportThread.setDaemon(true);
      exportThread.start();
   }

   // ---- Rendering and writing ----

   private void doExport(String mode, String directory, String prefix, int quality,
                         ArrayList<int[]> ztp, String ffmpegPath,
                         boolean useLabel) throws IOException {

      // Formats that need all frames up front (need full stack for IJ/GIF/AVI/clipboard).
      boolean needsAllFrames = mode.equals(FORMAT_SYSTEM_CLIPBOARD)
            || mode.equals(FORMAT_IMAGEJ)
            || mode.equals(FORMAT_GIF)
            || mode.equals(FORMAT_AVI);

      if (needsAllFrames) {
         doExportBuffered(mode, directory, prefix, quality, ztp, useLabel);
         return;
      }

      // Streaming path: render and write one frame at a time (PNG, JPEG, Movie).
      // This avoids accumulating all rendered frames in heap simultaneously.
      File tmpDir = null;
      if (mode.equals(FORMAT_MOVIE)) {
         String tmpPath = System.getProperty("java.io.tmpdir")
               + File.separator + "mm_ortho_" + System.currentTimeMillis();
         tmpDir = new File(tmpPath);
         if (!tmpDir.mkdirs()) {
            throw new IOException("Could not create temp directory: " + tmpPath);
         }
      }

      int written = 0;
      try {
         for (int i = 0; i < ztp.size(); i++) {
            int z = ztp.get(i)[0];
            int t = ztp.get(i)[1];
            int p = ztp.get(i)[2];
            BufferedImage frame = viewer_.renderCompositeForExport(z, t, p);
            if (frame == null) {
               continue;
            }
            written++;
            String label = ztp.size() > 1
                  ? (useLabel ? ztpLabel(z, t, p) : String.format("_%010d", written)) : "";
            if (mode.equals(FORMAT_PNG)) {
               ImageIO.write(frame, "png", new File(directory, prefix + label + ".png"));
            } else if (mode.equals(FORMAT_JPEG)) {
               writeJpeg(frame, new File(directory, prefix + label + ".jpg"), quality / 100f);
            } else if (mode.equals(FORMAT_MOVIE)) {
               ImageIO.write(frame, "png",
                     new File(tmpDir, String.format("frame_%06d.png", written)));
            }
         }
      } finally {
         if (mode.equals(FORMAT_MOVIE) && tmpDir != null) {
            if (written > 0) {
               File outFile = new File(directory, prefix + ".mp4");
               try {
                  runFfmpeg(ffmpegPath, tmpDir, outFile, quality);
                  showInfo("Movie saved to:\n" + outFile.getAbsolutePath());
               } finally {
                  deleteTempDir(tmpDir);
               }
            } else {
               deleteTempDir(tmpDir);
            }
         }
      }

      if (written == 0) {
         showInfo("No images could be rendered for export.");
         return;
      }
      if (mode.equals(FORMAT_PNG)) {
         showInfo(written + " PNG file(s) saved to:\n" + directory);
      } else if (mode.equals(FORMAT_JPEG)) {
         showInfo(written + " JPEG file(s) saved to:\n" + directory);
      }
   }

   private void doExportBuffered(String mode, String directory, String prefix, int quality,
                                 ArrayList<int[]> ztp, boolean useLabel) throws IOException {
      List<BufferedImage> frames = new ArrayList<BufferedImage>();
      List<String> labels = new ArrayList<String>();
      for (int[] coord : ztp) {
         int z = coord[0];
         int t = coord[1];
         int p = coord[2];
         BufferedImage frame = viewer_.renderCompositeForExport(z, t, p);
         if (frame != null) {
            frames.add(frame);
            labels.add(useLabel ? ztpLabel(z, t, p) : String.format("_%010d", frames.size()));
         }
      }

      if (frames.isEmpty()) {
         showInfo("No images could be rendered for export.");
         return;
      }

      if (mode.equals(FORMAT_SYSTEM_CLIPBOARD)) {
         final BufferedImage img = frames.get(0);
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               Toolkit.getDefaultToolkit().getSystemClipboard()
                     .setContents(new TransferableImage(img), null);
            }
         });
         return;
      }

      if (mode.equals(FORMAT_IMAGEJ)) {
         final ImageStack stack = buildStack(frames);
         final String name = (prefix != null && !prefix.isEmpty())
               ? prefix : "Orthogonal Views";
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               new ImagePlus(name, stack).show();
            }
         });
         return;
      }

      if (mode.equals(FORMAT_GIF)) {
         File outFile = new File(directory, prefix + ".gif");
         GifWriter.save(new ImagePlus(prefix, buildStack(frames)), outFile.getAbsolutePath());
         showInfo("Saved to:\n" + outFile.getAbsolutePath());
         return;
      }

      if (mode.equals(FORMAT_AVI)) {
         File outFile = new File(directory, prefix + ".avi");
         ImagePlus imp = new ImagePlus(prefix, buildStack(frames));
         new AVI_Writer().writeImage(imp, outFile.getAbsolutePath(),
               AVI_Writer.JPEG_COMPRESSION, quality);
         showInfo("Saved to:\n" + outFile.getAbsolutePath());
      }
   }

   private ImageStack buildStack(List<BufferedImage> frames) {
      ImageStack stack = new ImageStack(frames.get(0).getWidth(), frames.get(0).getHeight());
      for (BufferedImage f : frames) {
         stack.addSlice(new ColorProcessor(f));
      }
      return stack;
   }

   private String ztpLabel(int z, int t, int p) {
      StringBuilder sb = new StringBuilder();
      if (viewer_.getNumZSlices() > 1) {
         sb.append("_Z").append(String.format("%06d", z + 1));
      }
      if (viewer_.getNumTimePoints() > 1) {
         sb.append("_T").append(String.format("%06d", t + 1));
      }
      if (viewer_.getNumPositions() > 1) {
         sb.append("_P").append(String.format("%06d", p + 1));
      }
      return sb.length() > 0 ? sb.toString() : String.format("_%010d", 1);
   }

   private void writeJpeg(BufferedImage img, File file, float quality) throws IOException {
      ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
      try {
         ImageWriteParam param = writer.getDefaultWriteParam();
         param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
         param.setCompressionQuality(quality);
         ImageOutputStream stream = ImageIO.createImageOutputStream(file);
         try {
            writer.setOutput(stream);
            writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
         } finally {
            stream.close();
         }
      } finally {
         writer.dispose();
      }
   }

   private void runFfmpeg(String ffmpegPath, File tmpDir, File outFile, int quality)
         throws IOException {
      int crf = (int) Math.round(51.0 * (1.0 - (quality - 1) / 99.0));
      List<String> cmd = new ArrayList<String>();
      cmd.add(ffmpegPath);
      cmd.add("-framerate");
      cmd.add("10");
      cmd.add("-i");
      cmd.add(new File(tmpDir, "frame_%06d.png").getAbsolutePath());
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
      BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
      try {
         String line;
         while ((line = br.readLine()) != null) {
            out.append(line).append("\n");
         }
      } catch (IOException ex) {
         // ignore read errors from the stream
      } finally {
         try {
            br.close();
         } catch (IOException ex) {
            // ignore close error
         }
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
            studio_.logs().showMessage(msg, viewerWindow());
         }
      });
   }

   /** Returns the viewer's Window, or null if the viewer has already closed. */
   private Window viewerWindow() {
      try {
         return viewer_.getWindow();
      } catch (IllegalStateException ex) {
         return null;
      }
   }

   // ---- CoordCollector — minimal ImageExporter that records loop() calls ----

   /**
    * Minimal {@link ImageExporter} stub that records the axis loops configured by
    * {@link ExportMovieDlg.AxisPanel#configureExporter} and enumerates (z, t, p) tuples.
    *
    * <p>We avoid building {@link Coords} objects for the export sequence because
    * {@code DefaultCoords.Builder.index(axis, 0)} silently drops the axis, which would
    * break any downstream {@code hasAxis()} check when the first frame is at index 0.</p>
    */
   private static class CoordCollector implements ImageExporter {
      private final org.micromanager.data.DataProvider provider_;

      private static class Loop {
         final String axis;
         final int from;
         final int to;
         Loop next;

         Loop(String axis, int from, int to) {
            this.axis = axis;
            this.from = from;
            this.to = to;
         }
      }

      private Loop outerLoop_ = null;
      private Loop lastLoop_ = null;

      CoordCollector(org.micromanager.data.DataProvider provider) {
         provider_ = provider;
      }

      @Override
      public ImageExporter loop(String axis, int startIndex, int stopIndex) {
         Loop l = new Loop(axis, startIndex, stopIndex);
         if (outerLoop_ == null) {
            outerLoop_ = l;
            lastLoop_ = l;
         } else {
            lastLoop_.next = l;
            lastLoop_ = l;
         }
         return this;
      }

      /**
       * Enumerate all (z, t, p) tuples that the configured loops cover.
       *
       * @param baseZ fallback z when no Z loop is configured
       * @param baseT fallback t when no T loop is configured
       * @param baseP fallback p when no P loop is configured
       * @param result destination list; each entry is int[]{z, t, p}
       */
      public void collectFrames(int baseZ, int baseT, int baseP, ArrayList<int[]> result) {
         if (outerLoop_ == null) {
            result.add(new int[]{baseZ, baseT, baseP});
         } else {
            collectLoop(outerLoop_, baseZ, baseT, baseP, result);
         }
      }

      private void collectLoop(Loop loop, int z, int t, int p, ArrayList<int[]> result) {
         for (int i = loop.from; i <= loop.to; i++) {
            int nz = Coords.Z_SLICE.equals(loop.axis) ? i : z;
            int nt = Coords.TIME_POINT.equals(loop.axis) ? i : t;
            int np = Coords.STAGE_POSITION.equals(loop.axis) ? i : p;
            if (loop.next == null) {
               // Check whether the image exists. DefaultCoords drops index-0 axes so
               // we cannot use copyBuilder().index(axis, 0) for the check — use the
               // typed builder setters which handle 0 correctly.
               Coords check = buildCheckCoords(nz, nt, np);
               if (check == null || provider_.hasImage(check)) {
                  result.add(new int[]{nz, nt, np});
               }
            } else {
               collectLoop(loop.next, nz, nt, np, result);
            }
         }
      }

      private Coords buildCheckCoords(int z, int t, int p) {
         try {
            org.micromanager.data.Image any = provider_.getAnyImage();
            if (any == null) {
               return null;
            }
            Coords.Builder b = any.getCoords().copyBuilder();
            if (provider_.getAxes().contains(Coords.Z_SLICE)) {
               b = b.z(z);
            }
            if (provider_.getAxes().contains(Coords.TIME_POINT)) {
               b = b.timePoint(t);
            }
            if (provider_.getAxes().contains(Coords.STAGE_POSITION)) {
               b = b.stagePosition(p);
            }
            return b.build();
         } catch (java.io.IOException ex) {
            return null;
         }
      }

      // Unused ImageExporter methods — no-ops

      @Override
      public void setDisplay(org.micromanager.display.DisplayWindow d) {
      }

      @Override
      public void setOutputFormat(ImageExporter.OutputFormat f) {
      }

      @Override
      public void setOutputQuality(int q) {
      }

      @Override
      public void setFfmpegPath(String p) {
      }

      @Override
      public void setUseLabel(boolean u) {
      }

      @Override
      public void setSaveInfo(String dir, String prefix) {
      }

      @Override
      public void setImageJName(String name) {
      }

      @Override
      public void resetLoops() {
         outerLoop_ = null;
         lastLoop_ = null;
      }

      @Override
      public void export() {
      }

      @Override
      public void waitForExport() {
      }
   }

   private static class TransferableImage implements Transferable {
      private final java.awt.Image img_;

      TransferableImage(java.awt.Image img) {
         img_ = img;
      }

      @Override
      public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
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
