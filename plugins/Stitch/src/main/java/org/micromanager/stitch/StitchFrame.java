package org.micromanager.stitch;

import com.google.common.eventbus.Subscribe;
import java.awt.Dialog;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import mmcorej.TaggedImage;
import net.miginfocom.swing.MigLayout;
import org.micromanager.MultiStagePosition;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.exporttiles.TileAligner;
import org.micromanager.exporttiles.TileBlender;
import org.micromanager.imageprocessing.ImageTransformUtils;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Dialog for the ExportMMTiles plugin.
 *
 * <p>Lets the user choose:
 * <ul>
 *   <li>Which channel to use for alignment (if multiple channels exist)</li>
 *   <li>Which z-slice to use for alignment (if multiple z-slices exist)</li>
 *   <li>Whether to blend and/or align tiles</li>
 *   <li>Output: RAM datastore (loaded into memory) or Image Stack File saved to disk</li>
 * </ul>
 *
 * <p>The stitched result is placed directly into an MM Datastore. For RAM output
 * the datastore is in-memory. For Image Stack File output it is written to disk.</p>
 */
public class StitchFrame extends JDialog {

   private static final String SAVE_RAM = "RAM (temporary)";
   private static final String SAVE_STACK = "Image Stack File";

   // Profile keys
   private static final String PREF_BLEND = "blend";
   private static final String PREF_ALIGN = "align";
   private static final String PREF_CORRECT_ORIENTATION = "correctOrientation";
   private static final String PREF_SAVE_FORMAT = "saveFormat";
   private static final String PREF_OUTPUT_DIR = "outputDir";
   private static final String PREF_NAME_PREFIX = "namePrefix";
   private static final String PREF_MAX_DISPLACEMENT = "maxDisplacement";

   private final Studio studio_;
   private final DisplayWindow displayWindow_;
   private final DataProvider dataProvider_;
   private final MutablePropertyMapView settings_;

   private JLabel alignChannelLabel_;
   private JComboBox<String> alignChannelCombo_;
   private JLabel alignZLabel_;
   private JComboBox<Integer> alignZCombo_;
   private JLabel maxDisplacementLabel_;
   private JCheckBox correctOrientationCheck_;
   private JCheckBox alignCheck_;
   private JCheckBox blendCheck_;
   private JComboBox<String> saveFormatCombo_;
   private JTextField outputDirField_;
   private JButton browseButton_;
   private JTextField namePrefixField_;
   private JTextField maxDisplacementField_;
   private boolean registeredForEvents_ = false;

   /**
    * Construct and show the export dialog.
    *
    * @param studio  the MM Studio instance
    * @param display the display whose data to export
    */
   public StitchFrame(Studio studio, DisplayWindow display) {
      super(display.getWindow(), "Export Tiled Dataset", Dialog.ModalityType.MODELESS);
      studio_ = studio;
      displayWindow_ = display;
      dataProvider_ = display.getDataProvider();
      settings_ = studio_.profile().getSettings(StitchFrame.class);

      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      setLayout(new MigLayout("fillx, insets 8", "[right][grow,fill]", "[]4[]"));

      buildUI();
      updatePathControls();
      updateAlignControls();

      pack();
      Window owner = display.getWindow();
      setLocation(owner.getX() + owner.getWidth() / 2 - getWidth() / 2,
            owner.getY() + owner.getHeight() / 2 - getHeight() / 2);

      studio_.displays().registerForEvents(this);
      registeredForEvents_ = true;
      setVisible(true);
   }

   // -------------------------------------------------------------------------
   // UI construction
   // -------------------------------------------------------------------------

   private void buildUI() {
      SummaryMetadata summary = dataProvider_.getSummaryMetadata();
      final List<String> channelNames = getChannelNames(summary);
      final int numZ = dataProvider_.getNextIndex(Coords.Z_SLICE);

      // Correct camera orientation
      add(new JLabel(""));
      correctOrientationCheck_ = new JCheckBox("Correct camera orientation (affine)");
      correctOrientationCheck_.setSelected(settings_.getBoolean(PREF_CORRECT_ORIENTATION, false));
      add(correctOrientationCheck_, "wrap");

      // Align
      add(new JLabel(""));
      alignCheck_ = new JCheckBox("Align tiles (phase correlation)");
      alignCheck_.setSelected(settings_.getBoolean(PREF_ALIGN, false));
      alignCheck_.addActionListener((ActionEvent e) -> updateAlignControls());
      add(alignCheck_, "wrap");

      // Alignment channel (only active when align is checked)
      alignChannelLabel_ = new JLabel("Align channel:");
      add(alignChannelLabel_);
      alignChannelCombo_ = new JComboBox<>();
      for (String name : channelNames) {
         alignChannelCombo_.addItem(name);
      }
      add(alignChannelCombo_, "wrap");

      // Alignment z-slice (only active when align is checked)
      alignZLabel_ = new JLabel("Align z-slice:");
      add(alignZLabel_);
      alignZCombo_ = new JComboBox<>();
      for (int z = 0; z < Math.max(numZ, 1); z++) {
         alignZCombo_.addItem(z + 1);  // display 1-based
      }
      add(alignZCombo_, "wrap");

      // Max displacement cutoff (only active when align is checked)
      maxDisplacementLabel_ = new JLabel("Max displacement (px):");
      add(maxDisplacementLabel_);
      maxDisplacementField_ = new JTextField(6);
      maxDisplacementField_.setText(settings_.getString(PREF_MAX_DISPLACEMENT, ""));
      add(maxDisplacementField_, "growx, wrap");

      // Blend
      add(new JLabel(""));
      blendCheck_ = new JCheckBox("Blend tiles (feathered overlap)");
      blendCheck_.setSelected(settings_.getBoolean(PREF_BLEND, false));
      add(blendCheck_, "wrap");

      // Save format
      add(new JLabel("Save as:"));
      saveFormatCombo_ = new JComboBox<>(new String[]{SAVE_RAM, SAVE_STACK});
      saveFormatCombo_.setSelectedItem(settings_.getString(PREF_SAVE_FORMAT, SAVE_RAM));
      saveFormatCombo_.addActionListener((ActionEvent e) -> updatePathControls());
      add(saveFormatCombo_, "wrap");

      // Directory root (enabled only for Image Stack File)
      add(new JLabel("Directory root:"));
      outputDirField_ = new JTextField(30);
      outputDirField_.setText(settings_.getString(PREF_OUTPUT_DIR, ""));
      add(outputDirField_, "growx");

      browseButton_ = new JButton("...");
      browseButton_.addActionListener((ActionEvent e) -> chooseSaveDir());
      add(browseButton_, "wrap");

      // Name prefix (enabled only for Image Stack File)
      add(new JLabel("Name prefix:"));
      namePrefixField_ = new JTextField(30);
      namePrefixField_.setText(settings_.getString(PREF_NAME_PREFIX, "stitched"));
      add(namePrefixField_, "growx, wrap");

      // Buttons
      JButton exportButton = new JButton("Export");
      exportButton.addActionListener((ActionEvent e) -> onExport());
      JButton cancelButton = new JButton("Cancel");
      cancelButton.addActionListener((ActionEvent e) -> dispose());
      add(exportButton, "span 2, split 2, tag ok, wmin button");
      add(cancelButton, "tag cancel");
   }

   // -------------------------------------------------------------------------
   // Event handlers
   // -------------------------------------------------------------------------

   @Subscribe
   public void onDataViewerClosing(DataViewerWillCloseEvent event) {
      if (event.getDataViewer().equals(displayWindow_)) {
         dispose();
      }
   }

   @Override
   public void dispose() {
      if (registeredForEvents_) {
         registeredForEvents_ = false;
         studio_.displays().unregisterForEvents(this);
      }
      super.dispose();
   }

   // -------------------------------------------------------------------------
   // UI helpers
   // -------------------------------------------------------------------------

   private void updatePathControls() {
      boolean needsPath = SAVE_STACK.equals(saveFormatCombo_.getSelectedItem());
      outputDirField_.setEnabled(needsPath);
      browseButton_.setEnabled(needsPath);
      namePrefixField_.setEnabled(needsPath);
   }

   private void updateAlignControls() {
      boolean align = alignCheck_.isSelected();
      SummaryMetadata summary = dataProvider_.getSummaryMetadata();
      final int numChannels = getChannelNames(summary).size();
      final int numZ = dataProvider_.getNextIndex(Coords.Z_SLICE);
      alignChannelLabel_.setEnabled(align);
      alignChannelCombo_.setEnabled(align && numChannels > 1);
      alignZLabel_.setEnabled(align);
      alignZCombo_.setEnabled(align && numZ > 1);
      maxDisplacementLabel_.setEnabled(align);
      maxDisplacementField_.setEnabled(align);
   }

   private void chooseSaveDir() {
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Select directory root");
      chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      String current = outputDirField_.getText().trim();
      if (!current.isEmpty()) {
         File cur = new File(current);
         chooser.setCurrentDirectory(cur.isDirectory() ? cur : cur.getParentFile());
      } else {
         File suggested = new File(FileDialogs.getSuggestedFile(FileDialogs.MM_DATA_SET));
         if (suggested.getParentFile() != null) {
            chooser.setCurrentDirectory(suggested.getParentFile());
         }
      }
      if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
         File file = chooser.getSelectedFile();
         outputDirField_.setText(file.getAbsolutePath());
         FileDialogs.storePath(FileDialogs.MM_DATA_SET, file);
      }
   }

   // -------------------------------------------------------------------------
   // Export logic
   // -------------------------------------------------------------------------

   private void onExport() {
      boolean saveToStack = SAVE_STACK.equals(saveFormatCombo_.getSelectedItem());
      String outputDir = outputDirField_.getText().trim();
      String namePrefix = namePrefixField_.getText().trim();
      if (saveToStack && outputDir.isEmpty()) {
         studio_.logs().showError("Please select a directory root.", this);
         return;
      }
      if (saveToStack && namePrefix.isEmpty()) {
         studio_.logs().showError("Please enter a name prefix.", this);
         return;
      }
      if (saveToStack) {
         namePrefix = studio_.data().getUniqueSaveDirectory(
               new File(outputDir, namePrefix).getAbsolutePath());
         // Strip the parent dir back out — we only want the (possibly suffixed) leaf name
         namePrefix = new File(namePrefix).getName();
      }

      // Wrap the DataProvider with row/col grid knowledge
      StitchDataProviderAdapter tiledAdapter;
      try {
         tiledAdapter = new StitchDataProviderAdapter(dataProvider_);
      } catch (IllegalArgumentException ex) {
         studio_.logs().showError(
               "Cannot determine tile grid positions: " + ex.getMessage(), null);
         return;
      }

      final String selectedChannel = alignChannelCombo_.getItemCount() > 0
            ? (String) alignChannelCombo_.getSelectedItem()
            : null;
      final int alignZ = alignZCombo_.getItemCount() > 0
            ? (Integer) alignZCombo_.getSelectedItem() - 1  // convert 1-based display to 0-based
            : 0;
      final boolean blend = blendCheck_.isSelected();
      final boolean align = alignCheck_.isSelected();
      final boolean correctOrientation = correctOrientationCheck_.isSelected();
      int maxDisplacementPx = -1;  // -1 = no cutoff
      if (align) {
         String maxDispText = maxDisplacementField_.getText().trim();
         if (!maxDispText.isEmpty()) {
            try {
               maxDisplacementPx = Integer.parseInt(maxDispText);
               if (maxDisplacementPx < 0) {
                  maxDisplacementPx = -1;
               }
            } catch (NumberFormatException ex) {
               studio_.logs().showError(
                     "Max displacement must be a non-negative integer.", this);
               return;
            }
         }
      }
      // Combine dir + prefix into the full save path
      final String outputPath = saveToStack
            ? outputDir + File.separator + namePrefix
            : "";

      // Probe affine transform for orientation correction
      int[] correction = null;
      if (correctOrientation) {
         AffineTransform affine = probeAffineTransform(dataProvider_);
         correction = ImageTransformUtils.correctionFromAffine(affine);
         if (correction == null) {
            studio_.logs().showMessage(
                  "No pixel size affine transform found in image metadata. "
                  + "Orientation correction will be skipped.", this);
         }
      }

      SummaryMetadata summary = dataProvider_.getSummaryMetadata();
      final List<String> allChannelNames = getChannelNames(summary);
      final List<String> channelNamesForExport = allChannelNames.isEmpty()
            ? Collections.singletonList(null)
            : allChannelNames;

      // baseAxes pins the z used for alignment only; channel is handled per-channel
      HashMap<String, Object> baseAxes = new HashMap<>();
      baseAxes.put("z", alignZ);

      // alignAxes adds the selected channel for TileAligner (alignment uses one channel)
      HashMap<String, Object> alignAxes = new HashMap<>(baseAxes);
      if (selectedChannel != null) {
         alignAxes.put("channel", selectedChannel);
      }

      // Capture display settings before dispose() closes the window
      final DisplaySettings sourceDisplaySettings = displayWindow_.getDisplaySettings();

      // Persist dialog settings to profile
      settings_.putBoolean(PREF_CORRECT_ORIENTATION, correctOrientation);
      settings_.putBoolean(PREF_ALIGN, align);
      settings_.putBoolean(PREF_BLEND, blend);
      settings_.putString(PREF_SAVE_FORMAT, (String) saveFormatCombo_.getSelectedItem());
      settings_.putString(PREF_OUTPUT_DIR, outputDir);
      settings_.putString(PREF_NAME_PREFIX, namePrefix);
      settings_.putString(PREF_MAX_DISPLACEMENT, maxDisplacementField_.getText().trim());

      dispose();

      // Progress dialog
      JDialog progressDialog = new JDialog((Window) null, "Exporting…",
            Dialog.ModalityType.MODELESS);
      progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      JProgressBar bar = new JProgressBar(0, 100);
      bar.setStringPainted(true);
      JLabel statusLabel = new JLabel("Preparing…");
      progressDialog.getContentPane().setLayout(
            new MigLayout("insets 12, gap 8", "[grow]"));
      progressDialog.getContentPane().add(statusLabel, "wrap");
      progressDialog.getContentPane().add(bar, "growx, wrap");
      URL iconUrl = getClass().getResource("/org/micromanager/icons/microscope.gif");
      if (iconUrl != null) {
         progressDialog.setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
      }
      progressDialog.pack();
      progressDialog.setLocationRelativeTo(null);
      progressDialog.setVisible(true);

      final int canvasW = tiledAdapter.getCanvasWidth();
      final int canvasH = tiledAdapter.getCanvasHeight();
      final StitchDataProviderAdapter adapter = tiledAdapter;
      final List<String> chNames = channelNamesForExport;
      final boolean doBlend = blend;
      final boolean doAlign = align;
      final int[] finalCorrection = correction;
      final boolean toStack = saveToStack;
      final String destPath = outputPath;
      final String datasetName = dataProvider_.getName() + "_stitched";
      final int exportAlignZ = alignZ;

      final HashMap<String, Object> finalAlignAxes = alignAxes;
      final int finalMaxDisplacement = maxDisplacementPx;

      new Thread(() -> {
         try {
            buildDatastore(adapter, baseAxes, finalAlignAxes, chNames, canvasW, canvasH,
                  doBlend, doAlign, finalCorrection, finalMaxDisplacement,
                  toStack, destPath, datasetName, exportAlignZ,
                  sourceDisplaySettings, bar, statusLabel, progressDialog);
         } catch (Exception ex) {
            studio_.logs().logError(ex, "Stitch export failed");
            String msg = ex.getMessage();
            if (msg == null) {
               msg = ex.getClass().getSimpleName();
            }
            final String displayMsg = msg;
            SwingUtilities.invokeLater(() -> {
               progressDialog.dispose();
               JOptionPane.showMessageDialog(null,
                     "Export failed: " + displayMsg,
                     "Export Error", JOptionPane.ERROR_MESSAGE);
            });
         } finally {
            adapter.close();
         }
      }, "ExportMMTiles-Export").start();
   }

   /**
    * Build an MM Datastore from the stitched/blended tile data and display it.
    *
    * <p>All pixel assembly happens here — no temp files, no loadData().</p>
    */
   private void buildDatastore(StitchDataProviderAdapter adapter,
                               HashMap<String, Object> baseAxes,
                               HashMap<String, Object> alignAxes,
                               List<String> chNames,
                               int canvasW, int canvasH,
                               boolean doBlend, boolean doAlign,
                               int[] correction,
                               int maxDisplacementPx,
                               boolean toStack, String destPath,
                               String datasetName, int alignZ,
                               DisplaySettings sourceDisplaySettings,
                               JProgressBar bar, JLabel statusLabel,
                               JDialog progressDialog) throws Exception {

      // Step 1: create the output Datastore
      final Datastore ds;
      if (toStack) {
         ds = studio_.data().createMultipageTIFFDatastore(destPath, true, false);
      } else {
         ds = studio_.data().createRAMDatastore();
      }

      // Derive correction components (null correction = no-op)
      boolean doMirror = correction != null && correction[1] != 0;
      int rotationDeg = correction != null ? correction[0] : 0;
      // Canvas dims may change if rotation is 90/270
      int outCanvasW = (rotationDeg == 90 || rotationDeg == 270) ? canvasH : canvasW;
      int outCanvasH = (rotationDeg == 90 || rotationDeg == 270) ? canvasW : canvasH;

      try {
         // Step 2: set SummaryMetadata — copy from source, then fix up stitched-specific fields
         SummaryMetadata srcSummary = dataProvider_.getSummaryMetadata();
         SummaryMetadata.Builder smBuilder;
         if (srcSummary != null) {
            smBuilder = srcSummary.copyBuilder();
         } else {
            smBuilder = studio_.data().summaryMetadataBuilder();
         }
         smBuilder = smBuilder
               .imageWidth(outCanvasW)
               .imageHeight(outCanvasH)
               // Stitched output is a single position — clear the multi-position list
               .stagePositions(new MultiStagePosition[0]);

         // Fix intendedDimensions: keep channel/z/time from source, force position=1
         if (srcSummary != null) {
            Coords srcDims = srcSummary.getIntendedDimensions();
            if (srcDims != null) {
               Coords.Builder dimBuilder = srcDims.copyBuilder();
               // Remove position axis — stitched result has a single (merged) position
               dimBuilder.removeAxis(Coords.STAGE_POSITION);
               smBuilder = smBuilder.intendedDimensions(dimBuilder.build());
            }
         }

         final SummaryMetadata outputSummary = smBuilder.build();
         ds.setSummaryMetadata(outputSummary);

         // Step 3: wait for SummaryMetadata to be accepted (up to 10s)
         long deadline = System.currentTimeMillis() + 10000;
         while (ds.getSummaryMetadata() == null
               && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
         }

         // Step 4: build a template Metadata from the first tile (position 0, selected z)
         final Metadata.Builder templateMetaBuilder =
               probeTemplateMetadata(dataProvider_, alignZ);

         // Step 5 & 6: compute aligned origins (if needed) and composite image
         SwingUtilities.invokeLater(() -> statusLabel.setText("Computing…"));

         Map<Point, Point2D.Float> origins = null;
         if (doAlign) {
            SwingUtilities.invokeLater(() -> statusLabel.setText("Aligning…"));
            origins = new TileAligner(adapter, alignAxes, chNames,
                  adapter.getSummaryMetadata())
                  .computeAlignedOrigins(0, maxDisplacementPx,
                        pct -> SwingUtilities.invokeLater(() -> {
                           bar.setValue(pct / 2);
                           statusLabel.setText("Aligning… " + pct + "%");
                        }));
            SwingUtilities.invokeLater(() -> bar.setValue(50));
         }

         final Map<Point, Point2D.Float> finalOrigins = origins;
         int numCh = chNames.size();
         int numZ = dataProvider_.getNextIndex(Coords.Z_SLICE);
         if (numZ < 1) {
            numZ = 1;
         }
         final int totalImages = numZ * numCh;

         // Detect pixel depth from the first available tile.
         final boolean is16bit;
         {
            TaggedImage probe = adapter.getImage(adapter.getAxesSet().iterator().next(), 0);
            is16bit = probe == null || probe.pix instanceof short[];
         }

         if (doBlend) {
            // Blend path: feathered blending per channel.
            //
            // Orientation correction strategy:
            //   - Mirror and 180° rotation keep tile w×h unchanged, so they can be applied
            //     per-tile inside TileBlender via the tileTransform callback.
            //   - 90°/270° rotations swap tile w×h which breaks TileBlender's grid geometry,
            //     so those are applied to the fully-assembled canvas afterward.
            //
            // The per-tile transform handles: mirror (if requested) + 180° (if requested).
            // The post-canvas transform handles: 90°/270° rotation only.
            final boolean needsPerTileMirror = doMirror;
            final int perTileRotation = (rotationDeg == 180) ? 180 : 0;
            final int postCanvasRotation = (rotationDeg == 90 || rotationDeg == 270)
                  ? rotationDeg : 0;

            // Read tile dims from summary metadata (set by StitchDataProviderAdapter)
            mmcorej.org.json.JSONObject blendSummaryMD = adapter.getSummaryMetadata();
            final int tileW = blendSummaryMD != null ? blendSummaryMD.optInt("Width", 0) : 0;
            final int tileH = blendSummaryMD != null ? blendSummaryMD.optInt("Height", 0) : 0;

            // Build per-tile transform for 16-bit path (8-bit uses a separate operator below)
            UnaryOperator<short[]> tileTransform16 = null;
            UnaryOperator<byte[]> tileTransform8 = null;
            if (correction != null && (needsPerTileMirror || perTileRotation != 0)
                  && tileW > 0 && tileH > 0) {
               if (is16bit) {
                  tileTransform16 = (pix) -> {
                     Object[] r = ImageTransformUtils.transformPixels(
                           pix, tileW, tileH, needsPerTileMirror, perTileRotation);
                     return (short[]) r[0];
                  };
               } else {
                  tileTransform8 = (pix) -> {
                     Object[] r = ImageTransformUtils.transformPixels(
                           pix, tileW, tileH, needsPerTileMirror, perTileRotation);
                     return (byte[]) r[0];
                  };
               }
            }

            int imagesWritten = 0;
            for (int z = 0; z < numZ; z++) {
               HashMap<String, Object> zAxes = new HashMap<>(baseAxes);
               zAxes.put("z", z);

               TileBlender blender = new TileBlender(adapter, new mmcorej.org.json.JSONObject(),
                     zAxes, chNames, adapter.getSummaryMetadata());
               for (int c = 0; c < numCh; c++) {
                  final int zIdx = z;
                  final String chName = chNames.get(c);
                  SwingUtilities.invokeLater(() -> statusLabel.setText(
                        "Blending z=" + zIdx + " " + chName + "…"));
                  final int imagesBefore = imagesWritten;
                  final java.util.function.IntConsumer blendProgress =
                        pct -> SwingUtilities.invokeLater(() -> {
                           int base = doAlign ? 50 : 0;
                           int half = doAlign ? 2 : 1;
                           bar.setValue(base + (imagesBefore * 100 + pct) / (totalImages * half));
                        });

                  int chCanvasW = canvasW;
                  int chCanvasH = canvasH;
                  Object pixelData;
                  int bytesPerPixel;
                  if (is16bit) {
                     final UnaryOperator<short[]> finalTT16 = tileTransform16;
                     short[] pixels = blender.composite16(0, 0, canvasW, canvasH, 0,
                           chName, finalOrigins, finalTT16, blendProgress);
                     // Apply 90°/270° post-canvas rotation if needed
                     if (postCanvasRotation != 0) {
                        Object[] transformed = ImageTransformUtils.transformPixels(
                              pixels, canvasW, canvasH, false, postCanvasRotation);
                        pixels = (short[]) transformed[0];
                        chCanvasW = (Integer) transformed[1];
                        chCanvasH = (Integer) transformed[2];
                     }
                     pixelData = pixels;
                     bytesPerPixel = 2;
                  } else {
                     final UnaryOperator<byte[]> finalTT8 = tileTransform8;
                     byte[] pixels = blender.composite8(0, 0, canvasW, canvasH, 0,
                           chName, finalOrigins, finalTT8, blendProgress);
                     if (postCanvasRotation != 0) {
                        Object[] transformed = ImageTransformUtils.transformPixels(
                              pixels, canvasW, canvasH, false, postCanvasRotation);
                        pixels = (byte[]) transformed[0];
                        chCanvasW = (Integer) transformed[1];
                        chCanvasH = (Integer) transformed[2];
                     }
                     pixelData = pixels;
                     bytesPerPixel = 1;
                  }

                  Coords coords = studio_.data().coordsBuilder()
                        .channel(c).z(z).build();
                  Metadata meta = templateMetaBuilder.generateUUID().build();
                  Image mmImg = studio_.data().createImage(pixelData, chCanvasW, chCanvasH,
                        bytesPerPixel, 1, coords, meta);
                  ds.putImage(mmImg);
                  imagesWritten++;
               }
            }

         } else {
            // Simple stitch path: copy tiles per channel per z onto a canvas.
            // When doAlign is true, use the computed aligned origins for tile placement.
            int imagesWritten = 0;
            for (int z = 0; z < numZ; z++) {
               HashMap<String, Object> zAxes = new HashMap<>(baseAxes);
               zAxes.put("z", z);

               for (int c = 0; c < numCh; c++) {
                  final String chName = chNames.get(c);
                  final int zIdx = z;
                  SwingUtilities.invokeLater(() -> statusLabel.setText(
                        "Stitching z=" + zIdx + " " + chName + "…"));
                  final int imagesBefore = imagesWritten;
                  Object[] result = stitchTiles(adapter, zAxes, chName, canvasW, canvasH,
                        correction, finalOrigins,
                        pct -> {
                           int base = doAlign ? 50 : 0;
                           int half = doAlign ? 2 : 1;
                           int overall = base + (imagesBefore * 100 + pct) / (totalImages * half);
                           SwingUtilities.invokeLater(() -> bar.setValue(overall));
                        });
                  Object canvas = result[0];
                  int bytesPerPixel = (Integer) result[1];
                  int stitchedW = (Integer) result[2];
                  int stitchedH = (Integer) result[3];

                  Coords coords = studio_.data().coordsBuilder()
                        .channel(c).z(z).build();
                  Metadata meta = templateMetaBuilder.generateUUID().build();
                  Image mmImg = studio_.data().createImage(canvas, stitchedW, stitchedH,
                        bytesPerPixel, 1, coords, meta);
                  ds.putImage(mmImg);
                  imagesWritten++;
               }
            }
         }

         SwingUtilities.invokeLater(() -> bar.setValue(100));

         // Step 6: freeze
         ds.freeze();
         ds.setName(datasetName);

         // Step 7: display
         SwingUtilities.invokeLater(() -> {
            progressDialog.dispose();
            try {
               studio_.displays().manage(ds);
               studio_.displays().createDisplay(ds, null, sourceDisplaySettings);
            } catch (Exception e) {
               studio_.logs().logError(e, "Could not display exported dataset");
            }
         });

      } catch (Exception ex) {
         try {
            ds.close();
         } catch (Exception ignore) {
            // ignore close errors
         }
         throw ex;
      }
   }

   /**
    * Stitch tiles for a single channel into a canvas without blending.
    *
    * <p>Iterates all axes sets from the adapter and copies each tile's pixels
    * into the correct position on the canvas based on its row/column. If a
    * correction is provided, each tile is transformed before placement and the
    * canvas dimensions are adjusted accordingly.</p>
    *
    * @param channelName the channel to stitch, or null for no channel axis
    * @param correction  int[]{rotation, mirror} from
    *                    {@link ImageTransformUtils#correctionFromAffine}, or null
    * @return Object[]{pixels (short[] or byte[]), bytesPerPixel (Integer),
    *         canvasWidth (Integer), canvasHeight (Integer)}
    */
   private static Object[] stitchTiles(StitchDataProviderAdapter adapter,
                                       HashMap<String, Object> baseAxes,
                                       String channelName,
                                       int canvasW, int canvasH,
                                       int[] correction,
                                       Map<Point, Point2D.Float> tileOrigins,
                                       java.util.function.IntConsumer progress) {
      short[] canvas16 = null;
      byte[] canvas8 = null;

      boolean doMirror = correction != null && correction[1] != 0;
      int rotationDeg = correction != null ? correction[0] : 0;
      boolean needsTransform = correction != null && (doMirror || rotationDeg != 0);
      // Corrected tile dims: swap w/h for 90/270 rotations
      boolean swapTileDims = rotationDeg == 90 || rotationDeg == 270;
      // Corrected canvas dims (determined once tile dims are known)
      int corrCanvasW = swapTileDims ? canvasH : canvasW;
      int corrCanvasH = swapTileDims ? canvasW : canvasH;

      Object targetZ = baseAxes.get("z");

      int processed = 0;
      java.util.Set<HashMap<String, Object>> allAxes = adapter.getAxesSet();
      int total = allAxes.size();

      int probedTileW = 0;
      int probedTileH = 0;
      mmcorej.org.json.JSONObject summaryMD = adapter.getSummaryMetadata();
      int overlapX = summaryMD != null ? summaryMD.optInt("GridPixelOverlapX", 0) : 0;
      int overlapY = summaryMD != null ? summaryMD.optInt("GridPixelOverlapY", 0) : 0;

      for (HashMap<String, Object> axes : allAxes) {
         // Skip only if z axis is present AND differs from target
         Object axisZ = axes.get("z");
         if (axisZ != null && targetZ != null && !targetZ.equals(axisZ)) {
            processed++;
            continue;
         }
         // Filter to the requested channel name
         Object axisChannel = axes.get("channel");
         if (channelName != null) {
            if (!channelName.equals(axisChannel)) {
               processed++;
               continue;
            }
         } else if (axisChannel != null) {
            processed++;
            continue;
         }

         Object rowObj = axes.get("row");
         Object colObj = axes.get("column");
         if (!(rowObj instanceof Integer) || !(colObj instanceof Integer)) {
            processed++;
            continue;
         }
         final int row = (Integer) rowObj;
         final int col = (Integer) colObj;

         TaggedImage tile = adapter.getImage(axes, 0);
         if (tile == null || tile.pix == null) {
            processed++;
            continue;
         }

         // Determine pixel type and allocate canvas on first valid tile
         int nPix;
         boolean is16bit;
         if (tile.pix instanceof short[]) {
            nPix = ((short[]) tile.pix).length;
            is16bit = true;
         } else if (tile.pix instanceof byte[]) {
            nPix = ((byte[]) tile.pix).length;
            is16bit = false;
         } else {
            processed++;
            continue;
         }

         // Probe tile dimensions on first valid tile
         if (probedTileW == 0 && tile.tags != null) {
            probedTileW = tile.tags.optInt("Width", 0);
            if (probedTileW > 0 && nPix % probedTileW == 0) {
               probedTileH = nPix / probedTileW;
            }
         }
         int tw = probedTileW > 0 ? probedTileW : (int) Math.sqrt(nPix);
         int th = probedTileH > 0 ? probedTileH : tw;

         // Apply orientation correction to the tile pixels
         Object tilePix = tile.pix;
         int tilePaintW = tw;
         int tilePaintH = th;
         if (needsTransform) {
            Object[] transformed = ImageTransformUtils.transformPixels(
                  tilePix, tw, th, doMirror, rotationDeg);
            tilePix = transformed[0];
            tilePaintW = (Integer) transformed[1];
            tilePaintH = (Integer) transformed[2];
         }

         // Allocate canvas on first valid tile (using corrected dims)
         if (canvas16 == null && canvas8 == null) {
            if (is16bit) {
               canvas16 = new short[corrCanvasW * corrCanvasH];
            } else {
               canvas8 = new byte[corrCanvasW * corrCanvasH];
            }
         }

         // Corrected step accounts for overlap in corrected-tile space
         int stepX = swapTileDims ? (th - overlapY) : (tw - overlapX);
         int stepY = swapTileDims ? (tw - overlapX) : (th - overlapY);

         // Tile origin: use aligned origins when provided, otherwise nominal grid position
         int destX;
         int destY;
         if (tileOrigins != null) {
            Point2D.Float origin = tileOrigins.get(new Point(col, row));
            destX = origin != null ? Math.round(origin.x) : col * stepX;
            destY = origin != null ? Math.round(origin.y) : row * stepY;
         } else {
            destX = col * stepX;
            destY = row * stepY;
         }
         for (int ty = 0; ty < tilePaintH; ty++) {
            if (destY + ty >= corrCanvasH) {
               break;
            }
            int srcOff = ty * tilePaintW;
            int dstOff = (destY + ty) * corrCanvasW + destX;
            int copyW = Math.min(tilePaintW, corrCanvasW - destX);
            if (copyW <= 0) {
               continue;
            }
            if (is16bit && canvas16 != null) {
               System.arraycopy(tilePix, srcOff, canvas16, dstOff, copyW);
            } else if (!is16bit && canvas8 != null) {
               System.arraycopy(tilePix, srcOff, canvas8, dstOff, copyW);
            }
         }

         processed++;
         final int pct = total > 0 ? processed * 100 / total : 100;
         if (progress != null) {
            progress.accept(pct);
         }
      }

      if (canvas16 != null) {
         return new Object[]{canvas16, 2, corrCanvasW, corrCanvasH};
      } else if (canvas8 != null) {
         return new Object[]{canvas8, 1, corrCanvasW, corrCanvasH};
      } else {
         return new Object[]{new short[corrCanvasW * corrCanvasH], 2, corrCanvasW, corrCanvasH};
      }
   }

   // -------------------------------------------------------------------------
   // Utilities
   // -------------------------------------------------------------------------

   /**
    * Build a template Metadata from the first available image at the given z slice.
    *
    * <p>Copies instrument-level fields (camera, binning, bitDepth, exposureMs,
    * pixelSizeUm, pixelSizeAffine, pixelAspect, roi, scopeData, userData) from a
    * representative source image. Position-specific fields (xPositionUm, yPositionUm,
    * positionName, imageNumber, elapsedTimeMs, receivedTime, fileName) are left unset
    * because they are meaningless for a stitched output.</p>
    *
    * <p>Returns a blank builder if no source image is found.</p>
    */
   private Metadata.Builder probeTemplateMetadata(DataProvider dataProvider, int z) {
      try {
         for (Coords coords : dataProvider.getUnorderedImageCoords()) {
            // Match selected z if the dataset has a z axis; otherwise take the first image
            if (coords.hasAxis(Coords.Z_SLICE) && coords.getIndex(Coords.Z_SLICE) != z) {
               continue;
            }
            Image img = dataProvider.getImage(coords);
            if (img == null) {
               continue;
            }
            Metadata src = img.getMetadata();
            if (src == null) {
               break;
            }
            // Start from a copy, then clear position-specific fields
            return src.copyBuilderWithNewUUID()
                  .xPositionUm(null)
                  .yPositionUm(null)
                  .positionName(null)
                  .imageNumber(null)
                  .elapsedTimeMs(null)
                  .receivedTime(null)
                  .fileName(null);
         }
      } catch (Exception e) {
         // Fall through to blank builder
      }
      return studio_.data().metadataBuilder();
   }

   /**
    * Scan the DataProvider for the first image that carries a pixel size affine transform.
    *
    * @return the first non-null AffineTransform found, or null if none exists
    */
   private static AffineTransform probeAffineTransform(DataProvider dataProvider) {
      try {
         for (Coords coords : dataProvider.getUnorderedImageCoords()) {
            Image img = dataProvider.getImage(coords);
            if (img == null) {
               continue;
            }
            Metadata meta = img.getMetadata();
            if (meta == null) {
               continue;
            }
            AffineTransform affine = meta.getPixelSizeAffine();
            if (affine != null) {
               return affine;
            }
         }
      } catch (Exception e) {
         // Ignore — return null
      }
      return null;
   }

   private static List<String> getChannelNames(SummaryMetadata summary) {
      if (summary == null) {
         return new ArrayList<>();
      }
      List<String> names = summary.getChannelNameList();
      return names != null ? names : new ArrayList<>();
   }
}
