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
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Dialog for the Stitch plugin.
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
   private static final String SAVE_NDTIFF = "NDTiff (TiledDataViewer)";

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
      super(display.getWindow(), "Stitch Tiled Dataset", Dialog.ModalityType.MODELESS);
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
      List<String> channelNames = getChannelNames(summary);
      if (channelNames.isEmpty()) {
         int numCh = dataProvider_.getNextIndex(Coords.CHANNEL);
         if (numCh <= 0) {
            numCh = 1;
         }
         channelNames = new ArrayList<>();
         for (int i = 0; i < numCh; i++) {
            channelNames.add(String.valueOf(i));
         }
      }
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
      saveFormatCombo_ = new JComboBox<>(new String[]{SAVE_RAM, SAVE_STACK, SAVE_NDTIFF});
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
      boolean needsPath = SAVE_STACK.equals(saveFormatCombo_.getSelectedItem())
            || SAVE_NDTIFF.equals(saveFormatCombo_.getSelectedItem());
      outputDirField_.setEnabled(needsPath);
      browseButton_.setEnabled(needsPath);
      namePrefixField_.setEnabled(needsPath);
   }

   private void updateAlignControls() {
      boolean align = alignCheck_.isSelected();
      SummaryMetadata summary = dataProvider_.getSummaryMetadata();
      List<String> chNames = getChannelNames(summary);
      final int numChannels = chNames.isEmpty()
            ? Math.max(1, dataProvider_.getNextIndex(Coords.CHANNEL))
            : chNames.size();
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
      boolean saveToNdtiff = SAVE_NDTIFF.equals(saveFormatCombo_.getSelectedItem());
      String outputDir = outputDirField_.getText().trim();
      String namePrefix = namePrefixField_.getText().trim();
      if ((saveToStack || saveToNdtiff) && outputDir.isEmpty()) {
         studio_.logs().showError("Please select a directory root.", this);
         return;
      }
      if ((saveToStack || saveToNdtiff) && namePrefix.isEmpty()) {
         studio_.logs().showError("Please enter a name prefix.", this);
         return;
      }
      if (saveToStack || saveToNdtiff) {
         String targetPath = new File(outputDir, namePrefix).getAbsolutePath();
         String uniquePath = studio_.data().getUniqueSaveDirectory(targetPath);
         if (!uniquePath.equals(targetPath)) {
            // A collision was detected. Strip any trailing _N suffix that getUniqueSaveDirectory
            // may have found already appended to namePrefix (e.g. from a prior export), then
            // let getUniqueSaveDirectory pick a fresh suffix from the base name.
            String baseName = namePrefix.replaceAll("_\\d+$", "");
            uniquePath = studio_.data().getUniqueSaveDirectory(
                  new File(outputDir, baseName).getAbsolutePath());
         }
         // Strip the parent dir back out — we only want the (possibly suffixed) leaf name
         namePrefix = new File(uniquePath).getName();
      }

      // Refuse to stitch a live (still-acquiring) dataset — write-mode readers cannot be
      // used reliably for random-access reading.
      if (!dataProvider_.isFrozen()) {
         studio_.logs().showError(
               "The acquisition is still running. Please wait for it to finish before stitching.",
               this);
         return;
      }

      // Collect all UI state now, on the EDT, before dispose() and before the thread starts.
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
      final String outputPath = (saveToStack || saveToNdtiff)
            ? outputDir + File.separator + namePrefix
            : "";

      // Capture display state before dispose() closes the window.
      final DisplaySettings sourceDisplaySettings = displayWindow_.getDisplaySettings();
      final String datasetName = dataProvider_.getName() + "_stitched";

      // Persist dialog settings to profile.
      settings_.putBoolean(PREF_CORRECT_ORIENTATION, correctOrientation);
      settings_.putBoolean(PREF_ALIGN, align);
      settings_.putBoolean(PREF_BLEND, blend);
      settings_.putString(PREF_SAVE_FORMAT, (String) saveFormatCombo_.getSelectedItem());
      settings_.putString(PREF_OUTPUT_DIR, outputDir);
      settings_.putString(PREF_NAME_PREFIX, namePrefix);
      settings_.putString(PREF_MAX_DISPLACEMENT, maxDisplacementField_.getText().trim());

      dispose();

      // Progress dialog
      JDialog progressDialog = new JDialog((Window) null, "Exporting...",
            Dialog.ModalityType.MODELESS);
      progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      JProgressBar bar = new JProgressBar(0, 100);
      bar.setStringPainted(true);
      JLabel statusLabel = new JLabel("Preparing...");
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

      final boolean doBlend = blend;
      final boolean doAlign = align;
      final boolean doCorrectOrientation = correctOrientation;
      final boolean toStack = saveToStack;
      final boolean toNdtiff = saveToNdtiff;
      final String destPath = outputPath;
      final int exportAlignZ = alignZ;
      final int finalMaxDisplacement = maxDisplacementPx;

      new Thread(() -> {
         // Wrap the DataProvider with row/col grid knowledge.
         StitchDataProviderAdapter adapter;
         try {
            adapter = new StitchDataProviderAdapter(dataProvider_);
         } catch (IllegalArgumentException ex) {
            final String msg = ex.getMessage();
            SwingUtilities.invokeLater(() -> {
               progressDialog.dispose();
               JOptionPane.showMessageDialog(null,
                     "Cannot determine tile grid positions: " + msg,
                     "Export Error", JOptionPane.ERROR_MESSAGE);
            });
            return;
         }

         try {
            // Probe affine transform for orientation correction.
            int[] correction = null;
            if (doCorrectOrientation) {
               AffineTransform affine = probeAffineTransform(dataProvider_);
               correction = ImageTransformUtils.correctionFromAffine(affine);
               if (correction == null) {
                  SwingUtilities.invokeLater(() ->
                        studio_.logs().showMessage(
                              "No pixel size affine transform found in image metadata. "
                              + "Orientation correction will be skipped.", null));
               }
            }

            SummaryMetadata summary = dataProvider_.getSummaryMetadata();
            final List<String> allChannelNames = getChannelNames(summary);
            // When no channel names are set in SummaryMetadata, use integer indices as
            // String channel identifiers so that TileBlender/TileAligner can match them
            // against the Integer channel values stored in the axes map.
            final List<String> chNames;
            if (allChannelNames.isEmpty()) {
               int numCh = dataProvider_.getNextIndex(Coords.CHANNEL);
               if (numCh <= 0) {
                  numCh = 1;
               }
               List<String> indexed = new ArrayList<>();
               for (int i = 0; i < numCh; i++) {
                  indexed.add(String.valueOf(i));
               }
               chNames = indexed;
            } else {
               chNames = allChannelNames;
            }

            // baseAxes pins the z used for alignment only; channel is handled per-channel
            HashMap<String, Object> baseAxes = new HashMap<>();
            baseAxes.put("z", exportAlignZ);

            // alignAxes adds the selected channel for TileAligner (alignment uses one channel)
            HashMap<String, Object> alignAxes = new HashMap<>(baseAxes);
            if (selectedChannel != null) {
               alignAxes.put("channel", selectedChannel);
            }

            final int canvasW = adapter.getCanvasWidth();
            final int canvasH = adapter.getCanvasHeight();
            final int[] finalCorrection = correction;

            buildDatastore(adapter, baseAxes, alignAxes, chNames,
                  canvasW, canvasH, doBlend, doAlign, finalCorrection, finalMaxDisplacement,
                  toStack, toNdtiff, destPath, datasetName, exportAlignZ,
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
    * <p>All pixel assembly happens here — no temp files.</p>
    */
   private void buildDatastore(StitchDataProviderAdapter adapter,
                               HashMap<String, Object> baseAxes,
                               HashMap<String, Object> alignAxes,
                               List<String> chNames,
                               int canvasW, int canvasH,
                               boolean doBlend, boolean doAlign,
                               int[] correction,
                               int maxDisplacementPx,
                               boolean toStack, boolean toNdtiff, String destPath,
                               String datasetName, int alignZ,
                               DisplaySettings sourceDisplaySettings,
                               JProgressBar bar, JLabel statusLabel,
                               JDialog progressDialog) throws Exception {

      // Step 1: create the output Datastore (not used for NDTiff path).
      // ndtiffStorage is initialized later once canvas size and pixel type are known;
      // both are declared here so the catch block can close them on error.
      final Datastore ds;
      if (toStack) {
         ds = studio_.data().createMultipageTIFFDatastore(destPath, true, false);
      } else if (toNdtiff) {
         ds = null;
      } else {
         ds = studio_.data().createRAMDatastore();
      }
      org.micromanager.ndtiffstorage.NDTiffStorage ndtiffStorage = null;

      // Derive correction components (null correction = no-op)
      boolean doMirror = correction != null && correction[1] != 0;
      int rotationDeg = correction != null ? correction[0] : 0;
      // Compute output canvas from corrected tile geometry rather than naively swapping
      // the raw adapter canvas dims.  The adapter canvas is in stage-coordinate space
      // (X→width, Y→height); swapping it for 90/270° rotations is wrong when the grid
      // is not square.  Instead derive the output size from corrected tile step * grid extent.
      boolean swapCanvasDims = rotationDeg == 90 || rotationDeg == 270;
      mmcorej.org.json.JSONObject rawMD = adapter.getSummaryMetadata();
      int rawTileW = rawMD != null ? rawMD.optInt("Width", 0) : 0;
      int rawTileH = rawMD != null ? rawMD.optInt("Height", 0) : 0;
      int rawOverlapX = rawMD != null ? rawMD.optInt("GridPixelOverlapX", 0) : 0;
      int rawOverlapY = rawMD != null ? rawMD.optInt("GridPixelOverlapY", 0) : 0;
      int corrTileWDim = swapCanvasDims ? rawTileH : rawTileW;
      int corrTileHDim = swapCanvasDims ? rawTileW : rawTileH;
      int corrOverlapXDim = swapCanvasDims ? rawOverlapY : rawOverlapX;
      int corrOverlapYDim = swapCanvasDims ? rawOverlapX : rawOverlapY;
      int outCanvasW;
      int outCanvasH;
      if (corrTileWDim > 0 && corrTileHDim > 0 && (swapCanvasDims || doMirror)) {
         outCanvasW = (corrTileWDim - corrOverlapXDim) * adapter.getMaxCol() + corrTileWDim;
         outCanvasH = (corrTileHDim - corrOverlapYDim) * adapter.getMaxRow() + corrTileHDim;
      } else {
         outCanvasW = canvasW;
         outCanvasH = canvasH;
      }

      try {
         // Step 2: detect pixel type, determine effectiveChNames, run alignment (if enabled)
         // — all before writing SummaryMetadata so the canvas size is final.
         SwingUtilities.invokeLater(() -> statusLabel.setText("Computing..."));

         // raw count from caller; effective count set after probe
         final int numCh = chNames.size();
         int numZ = dataProvider_.getNextIndex(Coords.Z_SLICE);
         if (numZ < 1) {
            numZ = 1;
         }
         int numT = dataProvider_.getNextIndex(Coords.TIME_POINT);
         if (numT < 1) {
            numT = 1;
         }
         // totalImages placeholder — refined after pixel type detection below
         // totalImages is set after pixel type detection (effectiveNumCh may differ for RGB)

         // Detect pixel depth from the first tile that returns a non-null image.
         Set<HashMap<String, Object>> axesSet = adapter.getAxesSet();
         if (axesSet.isEmpty()) {
            throw new IllegalStateException("Dataset contains no images.");
         }
         TaggedImage probe = null;
         for (HashMap<String, Object> probeAxes : axesSet) {
            TaggedImage candidate = adapter.getImage(probeAxes, 0);
            if (candidate != null && candidate.pix != null) {
               probe = candidate;
               break;
            }
         }
         if (probe == null) {
            throw new IllegalStateException(
                  "Dataset pixel type is not supported "
                  + "(only 8-bit grayscale, 16-bit grayscale, and RGB32 are supported).");
         }
         // Determine pixel type.
         // MM RGB32 images are stored as byte[] with 4 bytes per pixel (BGRA order).
         // BytesPerPixel/NumComponents tags are set by MMDataProviderAdapter; fall back
         // to inferring from pixel array length vs. Width/Height when tags are absent.
         final boolean is16bit = probe.pix instanceof short[];
         final boolean isRgb;
         if (probe.pix instanceof byte[] && probe.tags != null) {
            int bpp = probe.tags.optInt("BytesPerPixel", 0);
            int nc  = probe.tags.optInt("NumComponents", 0);
            studio_.logs().logMessage("Stitch: probe pix=byte[] len=" + ((byte[]) probe.pix).length
                  + " tags=" + probe.tags.toString());
            if (bpp == 4 && nc == 3) {
               isRgb = true;
            } else if (bpp == 0) {
               // Tags absent: infer from pixel count vs. image dimensions
               int pw = probe.tags.optInt("Width", 0);
               int ph = probe.tags.optInt("Height", 0);
               isRgb = pw > 0 && ph > 0
                     && ((byte[]) probe.pix).length == pw * ph * 4;
            } else {
               isRgb = false;
            }
         } else {
            studio_.logs().logMessage("Stitch: probe pix type="
                  + probe.pix.getClass().getSimpleName()
                  + " len=" + java.lang.reflect.Array.getLength(probe.pix)
                  + " tags=" + (probe.tags != null ? probe.tags.toString() : "null"));
            isRgb = false;
         }
         studio_.logs().logMessage("Stitch: is16bit=" + is16bit + " isRgb=" + isRgb);

         // For RGB the whole assembled canvas is rotated (no per-tile transform), so the
         // output dims are the naive swap of raw canvas dims — exactly what transformPixels
         // returns — not the corrected-geometry formula used for grayscale.
         if (isRgb && swapCanvasDims) {
            outCanvasW = canvasH;
            outCanvasH = canvasW;
         }

         // RGB images have no channel axis — override chNames to a single null entry
         // so the channel loop runs once and no channel filtering is applied.
         final List<String> effectiveChNames;
         final int effectiveNumCh;
         if (isRgb) {
            effectiveChNames = Collections.singletonList(null);
            effectiveNumCh = 1;
         } else {
            effectiveChNames = chNames;
            effectiveNumCh = numCh;
         }
         final int totalImages = numT * numZ * effectiveNumCh;

         // If aligning, run t=0 alignment now so the canvas size is known before
         // SummaryMetadata is written (the datastore does not allow rewriting it).
         float alignOriginShiftX = 0;
         float alignOriginShiftY = 0;
         Map<Point, Point2D.Float> t0Origins = null;
         if (doAlign) {
            SwingUtilities.invokeLater(() -> statusLabel.setText("Aligning..."));
            TileAligner t0Aligner = new TileAligner(adapter, alignAxes, effectiveChNames,
                  adapter.getSummaryMetadata());
            if (doMirror || rotationDeg != 0) {
               t0Aligner.setTileTransform(doMirror, rotationDeg);
            }
            t0Origins = t0Aligner.computeAlignedOrigins(0, maxDisplacementPx, pct -> {});
            if (t0Origins == null) {
               String msg = "Alignment skipped: overlap is 0 px (overlapX="
                     + adapter.getOverlapX() + " overlapY=" + adapter.getOverlapY() + ")";
               studio_.logs().logMessage("Stitch: " + msg);
               SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
            } else {
               String stats = t0Aligner.getLastAlignmentStats();
               studio_.logs().logMessage("Stitch alignment t=0: " + stats);
            }
            // Adjust canvas size and origins for alignment shifts.
            // tOrigins are always in pre-rotation tile space, so use raw tile dims here.
            // For grayscale with rotation, outCanvasW/H will be swapped afterward by
            // the corrected-geometry formula; for RGB the whole canvas is rotated post-composite.
            if (t0Origins != null && rawTileW > 0 && rawTileH > 0) {
               float minX = Float.MAX_VALUE;
               float minY = Float.MAX_VALUE;
               float maxX = -Float.MAX_VALUE;
               float maxY = -Float.MAX_VALUE;
               for (Point2D.Float o : t0Origins.values()) {
                  minX = Math.min(minX, o.x);
                  minY = Math.min(minY, o.y);
                  maxX = Math.max(maxX, o.x + rawTileW);
                  maxY = Math.max(maxY, o.y + rawTileH);
               }
               final float shiftX = minX < 0 ? -minX : 0;
               final float shiftY = minY < 0 ? -minY : 0;
               if (shiftX != 0 || shiftY != 0) {
                  Map<Point, Point2D.Float> shifted = new java.util.HashMap<>();
                  for (Map.Entry<Point, Point2D.Float> e : t0Origins.entrySet()) {
                     shifted.put(e.getKey(),
                           new Point2D.Float(e.getValue().x + shiftX, e.getValue().y + shiftY));
                  }
                  t0Origins = shifted;
                  maxX += shiftX;
                  maxY += shiftY;
               }
               // Record shift so t>0 origins can be adjusted by the same amount.
               alignOriginShiftX = shiftX;
               alignOriginShiftY = shiftY;
               // For rotation, the output canvas dims swap; for RGB the whole canvas rotates
               // after composite so pre-rotation size drives the output dims.
               int preRotW = (int) Math.ceil(maxX);
               int preRotH = (int) Math.ceil(maxY);
               int newCanvasW = swapCanvasDims ? preRotH : preRotW;
               int newCanvasH = swapCanvasDims ? preRotW : preRotH;
               if (newCanvasW != outCanvasW || newCanvasH != outCanvasH) {
                  studio_.logs().logMessage("Stitch: canvas resized by alignment from "
                        + outCanvasW + "x" + outCanvasH
                        + " to " + newCanvasW + "x" + newCanvasH);
                  outCanvasW = newCanvasW;
                  outCanvasH = newCanvasH;
               }
            }
         }

         // Write SummaryMetadata now that outCanvasW/H is final (alignment may have grown it).
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
               .stagePositions(new MultiStagePosition[0]);
         if (srcSummary != null) {
            Coords srcDims = srcSummary.getIntendedDimensions();
            if (srcDims != null) {
               Coords.Builder dimBuilder = srcDims.copyBuilder();
               dimBuilder.removeAxis(Coords.STAGE_POSITION);
               smBuilder = smBuilder.intendedDimensions(dimBuilder.build());
            }
         }

         // For NDTiff output, create NDTiff storage and skip Datastore entirely.
         // Tiles are written individually (not assembled into one canvas), so
         // summary metadata uses the individual tile dimensions + overlap.
         int ndtiffTileW = swapCanvasDims ? rawTileH : rawTileW;
         int ndtiffTileH = swapCanvasDims ? rawTileW : rawTileH;
         int ndtiffOverlapX = swapCanvasDims ? rawOverlapY : rawOverlapX;
         int ndtiffOverlapY = swapCanvasDims ? rawOverlapX : rawOverlapY;
         if (toNdtiff) {
            ndtiffStorage = buildNdtiffSummaryMetadata(
                  destPath, datasetName,
                  ndtiffTileW, ndtiffTileH, ndtiffOverlapX, ndtiffOverlapY,
                  isRgb, is16bit, chNames);
         } else {
            ndtiffStorage = null;
            ds.setSummaryMetadata(smBuilder.build());
            // Wait for SummaryMetadata to be accepted (up to 10s).
            long deadline = System.currentTimeMillis() + 10000;
            while (ds.getSummaryMetadata() == null
                  && System.currentTimeMillis() < deadline) {
               Thread.sleep(100);
            }
         }

         // Build a template Metadata from the first tile (position 0, selected z).
         final Metadata.Builder templateMetaBuilder =
               probeTemplateMetadata(dataProvider_, alignZ);

         // Read tile dims for per-tile transform (blend path only)
         mmcorej.org.json.JSONObject blendSummaryMD = adapter.getSummaryMetadata();
         final int tileW = blendSummaryMD != null ? blendSummaryMD.optInt("Width", 0) : 0;
         final int tileH = blendSummaryMD != null ? blendSummaryMD.optInt("Height", 0) : 0;

         // Build per-tile orientation transforms.  All rotation angles (0/90/180/270)
         // are applied per-tile for both the blend and simple paths.
         // For 90/270° rotations the corrected tile dimensions are swapped, so we
         // build a corrected summary metadata for TileBlender that reflects the new dims.
         final UnaryOperator<short[]> tileTransform16;
         final UnaryOperator<byte[]> tileTransform8;
         final mmcorej.org.json.JSONObject correctedBlendSummaryMD;
         if (correction != null && (doMirror || rotationDeg != 0) && tileW > 0 && tileH > 0) {
            final boolean fm = doMirror;
            final int rot = rotationDeg;
            tileTransform16 = (pix) -> (short[]) ImageTransformUtils.transformPixels(
                  pix, tileW, tileH, fm, rot)[0];
            tileTransform8 = (pix) -> (byte[]) ImageTransformUtils.transformPixels(
                  pix, tileW, tileH, fm, rot)[0];
            // Corrected tile dims for the blender's geometry
            boolean swapDims = rotationDeg == 90 || rotationDeg == 270;
            int corrTileW = swapDims ? tileH : tileW;
            int corrTileH = swapDims ? tileW : tileH;
            int overlapX = blendSummaryMD != null
                  ? blendSummaryMD.optInt("GridPixelOverlapX", 0) : 0;
            int overlapY = blendSummaryMD != null
                  ? blendSummaryMD.optInt("GridPixelOverlapY", 0) : 0;
            int corrOverlapX = swapDims ? overlapY : overlapX;
            int corrOverlapY = swapDims ? overlapX : overlapY;
            try {
               mmcorej.org.json.JSONObject md = new mmcorej.org.json.JSONObject();
               md.put("Width", corrTileW);
               md.put("Height", corrTileH);
               md.put("GridPixelOverlapX", corrOverlapX);
               md.put("GridPixelOverlapY", corrOverlapY);
               correctedBlendSummaryMD = md;
            } catch (mmcorej.org.json.JSONException e) {
               throw new IllegalStateException("Failed to build corrected summary metadata", e);
            }
         } else {
            tileTransform16 = null;
            tileTransform8 = null;
            correctedBlendSummaryMD = blendSummaryMD;
         }

         int imagesWritten = 0;
         for (int t = 0; t < numT; t++) {
            // Compute aligned origins once per time point at the selected z slice.
            // t=0 was already computed before SummaryMetadata was written; reuse it.
            Map<Point, Point2D.Float> origins;
            if (!doAlign) {
               origins = null;
            } else if (t == 0) {
               origins = t0Origins;  // already computed and canvas-adjusted above
            } else {
               final int tIdx = t;
               SwingUtilities.invokeLater(() -> statusLabel.setText(
                     "Aligning t=" + (tIdx + 1) + "..."));
               HashMap<String, Object> tAlignAxes = new HashMap<>(alignAxes);
               tAlignAxes.put(Coords.TIME_POINT, t);
               TileAligner aligner = new TileAligner(adapter, tAlignAxes, effectiveChNames,
                     adapter.getSummaryMetadata());
               if (doMirror || rotationDeg != 0) {
                  aligner.setTileTransform(doMirror, rotationDeg);
               }
               origins = aligner.computeAlignedOrigins(0, maxDisplacementPx, pct -> {});
               if (origins != null) {
                  studio_.logs().logMessage("Stitch alignment t=" + (t + 1) + ": "
                        + aligner.getLastAlignmentStats());
                  // Apply the same coordinate shift used for t=0 so all origins are
                  // non-negative and fit within the already-sized canvas.
                  final float sx = alignOriginShiftX;
                  final float sy = alignOriginShiftY;
                  Map<Point, Point2D.Float> shifted = new java.util.HashMap<>();
                  boolean anyOutOfBounds = false;
                  for (Map.Entry<Point, Point2D.Float> e : origins.entrySet()) {
                     float ox = e.getValue().x + sx;
                     float oy = e.getValue().y + sy;
                     shifted.put(e.getKey(), new Point2D.Float(ox, oy));
                     if (ox < 0 || oy < 0 || ox + rawTileW > outCanvasW
                           || oy + rawTileH > outCanvasH) {
                        anyOutOfBounds = true;
                     }
                  }
                  origins = shifted;
                  if (anyOutOfBounds) {
                     studio_.logs().logMessage(
                           "Stitch: t=" + (t + 1) + " alignment extends beyond canvas bounds "
                           + outCanvasW + "x" + outCanvasH
                           + " — edge tiles will be clipped.");
                  }
               }
            }
            final Map<Point, Point2D.Float> tOrigins = origins;

            // Log tile placement for every tile (t=0 only to avoid log flood for time-lapse).
            if (t == 0) {
               int nomStepX = rawTileW > 0 ? rawTileW - rawOverlapX : 0;
               int nomStepY = rawTileH > 0 ? rawTileH - rawOverlapY : 0;
               // Collect actual tiles from the origins map (aligned) or from the adapter
               // grid (nominal). Only log tiles that actually exist.
               List<Point> sortedTiles;
               if (tOrigins != null) {
                  sortedTiles = new ArrayList<>(tOrigins.keySet());
                  sortedTiles.sort((a, b) -> a.y != b.y ? a.y - b.y : a.x - b.x);
                  for (Point tile : sortedTiles) {
                     Point2D.Float o = tOrigins.get(tile);
                     int nomX = tile.x * nomStepX;
                     int nomY = tile.y * nomStepY;
                     studio_.logs().logMessage(String.format(
                           "Stitch: tile (%d,%d) origin=(%.1f,%.1f)"
                                 + " nominal=(%d,%d) delta=(%.1f,%.1f)",
                           tile.x, tile.y, o.x, o.y,
                           nomX, nomY, o.x - nomX, o.y - nomY));
                  }
               } else {
                  sortedTiles = new ArrayList<>();
                  for (int row = 0; row <= adapter.getMaxRow(); row++) {
                     for (int col = 0; col <= adapter.getMaxCol(); col++) {
                        sortedTiles.add(new Point(col, row));
                     }
                  }
                  for (Point tile : sortedTiles) {
                     int nomX = tile.x * nomStepX;
                     int nomY = tile.y * nomStepY;
                     studio_.logs().logMessage(String.format(
                           "Stitch: tile (%d,%d) origin=(%d, %d) [nominal, no alignment]",
                           tile.x, tile.y, nomX, nomY));
                  }
               }
            }

            for (int z = 0; z < numZ; z++) {
               // baseAxes for this t/z
               HashMap<String, Object> tzAxes = new HashMap<>(baseAxes);
               tzAxes.put("z", z);
               if (numT > 1) {
                  tzAxes.put(Coords.TIME_POINT, t);
               }

               if (toNdtiff) {
                  // NDTiff path: write each source tile individually (cropped, aligned,
                  // optionally blended) so TiledDataViewer displays them natively tiled.
                  imagesWritten += writeTiledNdtiff(adapter, ndtiffStorage, tzAxes,
                        effectiveChNames,
                        tOrigins, correction, is16bit, isRgb, z, t,
                        ndtiffTileW, ndtiffTileH, ndtiffOverlapX, ndtiffOverlapY,
                        doBlend, isRgb ? blendSummaryMD : correctedBlendSummaryMD,
                        tileTransform16, tileTransform8);
               } else if (doBlend) {
                  // RGB composite() has no per-tile transform — the whole assembled canvas is
                  // rotated afterward, so the blender must use the raw (uncorrected) tile geometry.
                  // Grayscale paths apply per-tile transforms and need the corrected geometry.
                  TileBlender blender = new TileBlender(adapter,
                        new mmcorej.org.json.JSONObject(),
                        tzAxes, effectiveChNames,
                        isRgb ? blendSummaryMD : correctedBlendSummaryMD);

                  for (int c = 0; c < effectiveNumCh; c++) {
                     final int tIdx = t;
                     final int zIdx = z;
                     final String chName = effectiveChNames.get(c);
                     SwingUtilities.invokeLater(() -> statusLabel.setText(
                           "Blending t=" + (tIdx + 1) + " z=" + (zIdx + 1) + " " + chName + "..."));
                     final int imagesBefore = imagesWritten;
                     final java.util.function.IntConsumer blendProgress =
                           pct -> SwingUtilities.invokeLater(() -> {
                              int base = doAlign ? 50 : 0;
                              int half = doAlign ? 2 : 1;
                              bar.setValue(base
                                    + (imagesBefore * 100 + pct) / (totalImages * half));
                           });

                     Object pixelData;
                     int bytesPerPixel;
                     int numComponents;
                     int imgW = outCanvasW;
                     int imgH = outCanvasH;
                     if (isRgb) {
                        // For RGB, composite at the pre-rotation canvas size (the alignment-
                        // adjusted nominal canvas), then rotate the whole assembled canvas.
                        // For no-rotation case outCanvasW/H == pre-rotation size.
                        // For 90/270° rotation, outCanvasW/H was swapped from canvasW/H above,
                        // so the pre-rotation size is outCanvasH × outCanvasW.
                        int preRotW = swapCanvasDims ? outCanvasH : outCanvasW;
                        int preRotH = swapCanvasDims ? outCanvasW : outCanvasH;
                        java.awt.image.BufferedImage bimg = blender.composite(
                              0, 0, preRotW, preRotH, 0, tOrigins, blendProgress);
                        int[] argb = new int[preRotW * preRotH];
                        bimg.getRGB(0, 0, preRotW, preRotH, argb, 0, preRotW);
                        byte[] pixels = argbToBgra(argb);
                        if (correction != null && (doMirror || rotationDeg != 0)) {
                           Object[] transformed = ImageTransformUtils.transformPixels(
                                 pixels, preRotW, preRotH, 4, doMirror, rotationDeg);
                           pixels = (byte[]) transformed[0];
                           imgW   = (Integer) transformed[1];
                           imgH   = (Integer) transformed[2];
                        }
                        pixelData = pixels;
                        bytesPerPixel = 4;
                        numComponents = 3;
                     } else if (is16bit) {
                        short[] pixels = blender.composite16(0, 0, outCanvasW, outCanvasH, 0,
                              chName, tOrigins, tileTransform16, blendProgress);
                        pixelData = pixels;
                        bytesPerPixel = 2;
                        numComponents = 1;
                     } else {
                        byte[] pixels = blender.composite8(0, 0, outCanvasW, outCanvasH, 0,
                              chName, tOrigins, tileTransform8, blendProgress);
                        pixelData = pixels;
                        bytesPerPixel = 1;
                        numComponents = 1;
                     }

                     Coords coords = studio_.data().coordsBuilder()
                           .channel(c).z(z).time(t).build();
                     Metadata meta = templateMetaBuilder.generateUUID().build();
                     Image mmImg = studio_.data().createImage(pixelData, imgW, imgH,
                           bytesPerPixel, numComponents, coords, meta);
                     putStitchedImage(mmImg, ds);
                     imagesWritten++;
                  }

               } else {
                  // Simple stitch: copy tiles, using aligned origins when available.
                  for (int c = 0; c < effectiveNumCh; c++) {
                     final String chName = effectiveChNames.get(c);
                     final int tIdx = t;
                     final int zIdx = z;
                     SwingUtilities.invokeLater(() -> statusLabel.setText(
                           "Stitching t=" + (tIdx + 1) + " z=" + (zIdx + 1)
                                 + " " + chName + "..."));
                     final int imagesBefore = imagesWritten;
                     Object[] result = stitchTiles(studio_,
                              adapter,
                              tzAxes,
                              chName,
                              outCanvasW,
                              outCanvasH,
                           correction, tOrigins, is16bit, isRgb,
                           pct -> {
                              int base = doAlign ? 50 : 0;
                              int half = doAlign ? 2 : 1;
                              int overall = base
                                    + (imagesBefore * 100 + pct) / (totalImages * half);
                              SwingUtilities.invokeLater(() -> bar.setValue(overall));
                           });
                     Object canvas = result[0];
                     int bytesPerPixel = (Integer) result[1];
                     int numComponents = (Integer) result[2];
                     int stitchedW = (Integer) result[3];
                     int stitchedH = (Integer) result[4];

                     Coords coords = studio_.data().coordsBuilder()
                           .channel(c).z(z).time(t).build();
                     Metadata meta = templateMetaBuilder.generateUUID().build();
                     Image mmImg = studio_.data().createImage(canvas, stitchedW, stitchedH,
                           bytesPerPixel, numComponents, coords, meta);
                     putStitchedImage(mmImg, ds);
                     imagesWritten++;
                  }
               }
            }
         }

         SwingUtilities.invokeLater(() -> bar.setValue(100));

         if (toNdtiff) {
            // Pre-build downsampled pyramid levels then finalize.
            ndtiffStorage.increaseMaxResolutionLevel(4);
            ndtiffStorage.finishedWriting();
            final org.micromanager.ndtiffstorage.NDTiffStorage finalStorage = ndtiffStorage;
            final String ndtiffPath = destPath;
            final DisplaySettings dispSettings = sourceDisplaySettings;
            final boolean finalIsRgb = isRgb;
            SwingUtilities.invokeLater(() -> {
               progressDialog.dispose();
               try {
                  openInTiledDataViewer(finalStorage, ndtiffPath, datasetName,
                        dispSettings, finalIsRgb);
               } catch (Exception e) {
                  studio_.logs().logError(e, "Could not open NDTiff in TiledDataViewer");
               }
            });
         } else {
            // Step 6: freeze and display via standard MM display.
            ds.freeze();
            ds.setName(datasetName);
            SwingUtilities.invokeLater(() -> {
               progressDialog.dispose();
               try {
                  studio_.displays().manage(ds);
                  studio_.displays().createDisplay(ds, null, sourceDisplaySettings);
               } catch (Exception e) {
                  studio_.logs().logError(e, "Could not display exported dataset");
               }
            });
         }

      } catch (Exception ex) {
         if (ndtiffStorage != null) {
            try {
               ndtiffStorage.close();
            } catch (Exception ignore) {
               // ignore
            }
         }
         if (ds != null) {
            try {
               ds.close();
            } catch (Exception ignore) {
               // ignore close errors
            }
         }
         throw ex;
      }
   }

   // -------------------------------------------------------------------------
   // NDTiff output helpers
   // -------------------------------------------------------------------------

   /**
    * Writes each tile individually to NDTiff storage so TiledDataViewer can display
    * them natively as a tiled dataset with optional blending and alignment corrections.
    *
    * <p>For each (row, col) in the tile grid, this method composites (if blending) or
    * copies (if not blending) only the pixels for that tile's region from the source
    * data, applies orientation correction, and writes the result to NDTiff storage.</p>
    *
    * @return the number of tile-channel images actually written (skipped tiles not counted)
    */
   private int writeTiledNdtiff(
         StitchDataProviderAdapter adapter,
         org.micromanager.ndtiffstorage.NDTiffStorage ndtiffStorage,
         HashMap<String, Object> tzAxes,
         List<String> effectiveChNames,
         Map<Point, Point2D.Float> tOrigins,
         int[] correction,
         boolean is16bit, boolean isRgb,
         int z, int t,
         int tileW, int tileH,
         int overlapX, int overlapY,
         boolean doBlend,
         mmcorej.org.json.JSONObject blendMD,
         UnaryOperator<short[]> tileTransform16,
         UnaryOperator<byte[]> tileTransform8)
         throws Exception {

      int maxRow = adapter.getMaxRow();
      int maxCol = adapter.getMaxCol();
      boolean doMirror = correction != null && correction[1] != 0;
      int rotationDeg = correction != null ? correction[0] : 0;
      boolean swapTile = rotationDeg == 90 || rotationDeg == 270;
      int outTileW = swapTile ? tileH : tileW;
      int outTileH = swapTile ? tileW : tileH;

      TileBlender blender = doBlend
            ? new TileBlender(adapter, new mmcorej.org.json.JSONObject(),
                  tzAxes, effectiveChNames, blendMD)
            : null;

      // Read pixel size from the storage summary so the scale bar overlay can display it.
      mmcorej.org.json.JSONObject storageMD = ndtiffStorage.getSummaryMetadata();
      double pixelSizeUm = storageMD != null ? storageMD.optDouble("PixelSize_um", 0) : 0;

      int stepX = tileW - overlapX;
      int stepY = tileH - overlapY;
      int written = 0;

      for (int row = 0; row <= maxRow; row++) {
         for (int col = 0; col <= maxCol; col++) {
            // Origin of this tile in full-resolution source pixel space.
            int roiX;
            int roiY;
            if (tOrigins != null) {
               Point2D.Float o = tOrigins.get(new Point(col, row));
               if (o == null) {
                  continue;
               }
               roiX = Math.round(o.x);
               roiY = Math.round(o.y);
            } else {
               roiX = col * stepX;
               roiY = row * stepY;
            }

            for (int c = 0; c < effectiveChNames.size(); c++) {
               final String chName = effectiveChNames.get(c);
               Object pixelData;
               int imgW;
               int imgH;

               if (doBlend) {
                  if (isRgb) {
                     java.awt.image.BufferedImage bimg =
                           blender.composite(roiX, roiY, tileW, tileH, 0, tOrigins, pct -> {});
                     int[] argb = new int[tileW * tileH];
                     bimg.getRGB(0, 0, tileW, tileH, argb, 0, tileW);
                     byte[] pixels = argbToBgra(argb);
                     if (correction != null && (doMirror || rotationDeg != 0)) {
                        Object[] xf = ImageTransformUtils.transformPixels(
                              pixels, tileW, tileH, 4, doMirror, rotationDeg);
                        pixels = (byte[]) xf[0];
                        imgW = (Integer) xf[1];
                        imgH = (Integer) xf[2];
                     } else {
                        imgW = tileW;
                        imgH = tileH;
                     }
                     pixelData = pixels;
                  } else if (is16bit) {
                     short[] pixels = blender.composite16(roiX, roiY, tileW, tileH, 0,
                           chName, tOrigins, tileTransform16, pct -> {});
                     imgW = outTileW;
                     imgH = outTileH;
                     pixelData = pixels;
                  } else {
                     byte[] pixels = blender.composite8(roiX, roiY, tileW, tileH, 0,
                           chName, tOrigins, tileTransform8, pct -> {});
                     imgW = outTileW;
                     imgH = outTileH;
                     pixelData = pixels;
                  }
               } else {
                  // Non-blend: read the source tile at its aligned position.
                  // We use a single-tile composite to extract the tileW×tileH region
                  // at (roiX, roiY) from the source grid, respecting alignment just like
                  // the blend path. This avoids a separate unaligned code path.
                  HashMap<String, Object> tileAxes = new HashMap<>(tzAxes);
                  tileAxes.put("row", row);
                  tileAxes.put("column", col);
                  if (!isRgb && chName != null) {
                     tileAxes.put("channel", chName);
                  }
                  mmcorej.TaggedImage tagged = adapter.getImage(tileAxes, 0);
                  if (tagged == null || tagged.pix == null) {
                     continue;
                  }
                  Object rawPix = tagged.pix;
                  if (correction != null && (doMirror || rotationDeg != 0)) {
                     Object[] xf;
                     if (is16bit) {
                        xf = ImageTransformUtils.transformPixels(
                              (short[]) rawPix, tileW, tileH, doMirror, rotationDeg);
                     } else {
                        int bpp = isRgb ? 4 : 1;
                        xf = ImageTransformUtils.transformPixels(
                              (byte[]) rawPix, tileW, tileH, bpp, doMirror, rotationDeg);
                     }
                     rawPix = xf[0];
                     imgW = (Integer) xf[1];
                     imgH = (Integer) xf[2];
                  } else {
                     imgW = tileW;
                     imgH = tileH;
                  }
                  pixelData = rawPix;
               }

               // Build tags and write tile to NDTiff.
               HashMap<String, Object> axes = buildNdtiffAxes(row, col, z, t,
                     isRgb ? null : chName);
               mmcorej.org.json.JSONObject tags = buildNdtiffTags(
                     imgW, imgH, isRgb, is16bit, roiX, roiY, pixelSizeUm, axes);
               ndtiffStorage.putImageMultiRes(pixelData, tags, axes,
                     isRgb, is16bit ? 16 : 8, imgH, imgW).get();
               written++;
            }
         }
      }
      return written;
   }

   /**
    * Builds the axes HashMap for one NDTiff image.
    * NDTiffStorage requires axes to be embedded in the tags under "Axes" AND passed
    * separately as a HashMap — this helper builds the HashMap; use buildNdtiffTags
    * to embed it in the tags object.
    */
   private static HashMap<String, Object> buildNdtiffAxes(
         int row, int col, int z, int t, String chName)
         throws mmcorej.org.json.JSONException {
      HashMap<String, Object> axes = new HashMap<>();
      axes.put("row", row);
      axes.put("column", col);
      axes.put("z", z);
      axes.put("time", t);
      if (chName != null) {
         axes.put("channel", chName);
      }
      return axes;
   }

   /**
    * Builds the per-image tags JSONObject for NDTiff, embedding axes under "Axes".
    * Also stores the aligned pixel origin so TiledDataViewer can position tiles correctly.
    */
   private static mmcorej.org.json.JSONObject buildNdtiffTags(
         int imgW, int imgH, boolean isRgb, boolean is16bit,
         int roiX, int roiY, double pixelSizeUm, HashMap<String, Object> axes)
         throws mmcorej.org.json.JSONException {
      mmcorej.org.json.JSONObject tags = new mmcorej.org.json.JSONObject();
      tags.put("Width", imgW);
      tags.put("Height", imgH);
      tags.put("BytesPerPixel", isRgb ? 4 : (is16bit ? 2 : 1));
      // PixelType is required by DefaultImage (used by the overlay renderer to fetch
      // a representative image). Without it, getAnyImage() throws and overlays are skipped.
      tags.put("PixelType", isRgb ? "RGB32" : (is16bit ? "GRAY16" : "GRAY8"));
      if (isRgb) {
         tags.put("NumComponents", 3);
      }
      // Pixel size — required by the scale bar overlay renderer.
      if (pixelSizeUm > 0) {
         tags.put("PixelSizeUm", pixelSizeUm);
      }
      // Canvas-space pixel origin from alignment (or nominal grid if no alignment).
      tags.put("XPositionPix", roiX);
      tags.put("YPositionPix", roiY);
      // Embed axes under "Axes" key — required by NDTiffStorage.putImageMultiRes.
      mmcorej.org.json.JSONObject axesJson = new mmcorej.org.json.JSONObject();
      for (Map.Entry<String, Object> e : axes.entrySet()) {
         axesJson.put(e.getKey(), e.getValue());
      }
      tags.put("Axes", axesJson);
      return tags;
   }

   /**
    * Creates an NDTiff storage for the stitched output and writes its summary metadata.
    */
   private org.micromanager.ndtiffstorage.NDTiffStorage buildNdtiffSummaryMetadata(
         String path, String name,
         int tileW, int tileH,
         int overlapX, int overlapY,
         boolean isRgb, boolean is16bit,
         List<String> chNames)
         throws mmcorej.org.json.JSONException {
      mmcorej.org.json.JSONObject json = new mmcorej.org.json.JSONObject();
      json.put("Width", tileW);
      json.put("Height", tileH);
      json.put("GridPixelOverlapX", overlapX);
      json.put("GridPixelOverlapY", overlapY);
      // Probe pixel size from the source dataset's first image metadata.
      double pixelSizeUm = probePixelSizeUm(dataProvider_);
      if (pixelSizeUm > 0) {
         json.put("PixelSize_um", pixelSizeUm);
      }
      json.put("BitDepth", is16bit ? 16 : 8);
      json.put("PixelType", isRgb ? "RGB32" : (is16bit ? "GRAY16" : "GRAY8"));
      if (!chNames.isEmpty() && !isRgb) {
         mmcorej.org.json.JSONArray arr = new mmcorej.org.json.JSONArray();
         for (String ch : chNames) {
            arr.put(ch);
         }
         json.put("ChNames", arr);
         json.put("Channels", chNames.size());
      }
      return new org.micromanager.ndtiffstorage.NDTiffStorage(
            path, name, json, overlapX, overlapY, true, null, 30, null, isRgb);
   }

   /**
    * Writes one stitched image to the MM Datastore.
    * Only used by the RAM/TIFF export paths; the NDTiff path writes tiles directly
    * via writeTiledNdtiff and never calls this method.
    */
   private static void putStitchedImage(Image mmImg, Datastore ds)
         throws Exception {
      ds.putImage(mmImg);
   }

   /**
    * Opens a finished NDTiff storage in a TiledDataViewer window.
    * Must be called on the EDT.
    */
   private void openInTiledDataViewer(
         org.micromanager.ndtiffstorage.NDTiffStorage storage,
         String path, String name,
         DisplaySettings displaySettings,
         boolean isRgb) {

      Set<HashMap<String, Object>> allAxes = storage.getAxesSet();
      mmcorej.org.json.JSONObject summaryJson = storage.getSummaryMetadata();

      org.micromanager.tileddataprovider.NDTiffProviderAdapter adapter =
            new org.micromanager.tileddataprovider.NDTiffProviderAdapter(storage);
      org.micromanager.tileddataviewer.TiledDataViewerDataProviderAPI provider =
            org.micromanager.tileddataviewer.TiledDataViewerFactory.createDataProvider(
                  studio_.data(), adapter, name);

      // Data source delegates display requests directly to the NDTiff storage.
      org.micromanager.tileddataviewer.TiledDataViewerDataSource dataSource =
            new StitchNdtiffDataSource(storage, isRgb);

      double pixelSizeUm = summaryJson != null ? summaryJson.optDouble("PixelSize_um", 0) : 0;

      org.micromanager.tileddataviewer.TiledDataViewerDataViewerAPI viewer =
            org.micromanager.tileddataviewer.TiledDataViewerFactory.createDataViewer(
                  studio_, dataSource, null, provider,
                  summaryJson != null ? summaryJson : new mmcorej.org.json.JSONObject(),
                  pixelSizeUm, isRgb);

      // Set the window title bar text.
      viewer.getNDViewer().setWindowTitle(name);

      if (displaySettings != null) {
         viewer.setDisplaySettings(displaySettings);
      }

      // Notify the provider once per unique display-axes plane (strip row/col first so
      // we don't trigger redundant histogram computations for each tile position).
      Set<HashMap<String, Object>> notified = new java.util.HashSet<>();
      for (HashMap<String, Object> axes : allAxes) {
         HashMap<String, Object> displayAxes = new HashMap<>(axes);
         displayAxes.remove(org.micromanager.ndtiffstorage.NDTiffStorage.ROW_AXIS);
         displayAxes.remove(org.micromanager.ndtiffstorage.NDTiffStorage.COL_AXIS);
         if (notified.add(displayAxes)) {
            provider.newImageArrived(displayAxes);
         }
      }
   }

   /**
    * Stitch tiles for a single channel into a canvas without blending.
    *
    * <p>Iterates all axes sets from the adapter and copies each tile's pixels
    * into the correct position on the canvas based on its row/column.</p>
    *
    * @param channelName the channel to stitch, or null for RGB (no channel axis)
    * @param canvasW     output canvas width in pixels (caller must supply the corrected size)
    * @param canvasH     output canvas height in pixels (caller must supply the corrected size)
    * @param correction  int[]{rotation, mirror} from
    *                    {@link ImageTransformUtils#correctionFromAffine}, or null
    * @return Object[]{pixels, bytesPerPixel, numComponents, canvasWidth, canvasHeight}
    *         where pixels is {@code short[]} (16-bit gray), {@code byte[]} (8-bit gray),
    *         or {@code byte[]} (RGB32 BGRA, 4 bytes per pixel)
    */
   private static Object[] stitchTiles(Studio studio,
                                       StitchDataProviderAdapter adapter,
                                       HashMap<String, Object> baseAxes,
                                       String channelName,
                                       int canvasW, int canvasH,
                                       int[] correction,
                                       Map<Point, Point2D.Float> tileOrigins,
                                       boolean is16bit,
                                       boolean isRgb,
                                       java.util.function.IntConsumer progress) {
      short[] canvas16 = null;
      byte[] canvas8 = null;
      byte[] canvasRgb = null;  // BGRA, 4 bytes per pixel

      boolean doMirror = correction != null && correction[1] != 0;
      int rotationDeg = correction != null ? correction[0] : 0;
      boolean needsTransform = correction != null && (doMirror || rotationDeg != 0);
      // swapTileDims governs stepX/stepY axis swap for 90/270° rotations
      boolean swapTileDims = rotationDeg == 90 || rotationDeg == 270;
      // Canvas dims are already corrected by the caller
      int corrCanvasW = canvasW;
      int corrCanvasH = canvasH;

      Object targetZ = baseAxes.get("z");
      Object targetT = baseAxes.get(Coords.TIME_POINT);

      int processed = 0;
      java.util.Set<HashMap<String, Object>> allAxes = adapter.getAxesSet();
      int total = allAxes.size();

      int probedTileW = 0;
      int probedTileH = 0;
      mmcorej.org.json.JSONObject summaryMD = adapter.getSummaryMetadata();
      int overlapX = summaryMD != null ? summaryMD.optInt("GridPixelOverlapX", 0) : 0;
      int overlapY = summaryMD != null ? summaryMD.optInt("GridPixelOverlapY", 0) : 0;

      for (HashMap<String, Object> axes : allAxes) {
         // Skip if z or time axis is present AND differs from target
         Object axisZ = axes.get("z");
         if (axisZ != null && targetZ != null && !targetZ.equals(axisZ)) {
            processed++;
            continue;
         }
         Object axisT = axes.get(Coords.TIME_POINT);
         if (axisT != null && targetT != null && !targetT.equals(axisT)) {
            processed++;
            continue;
         }
         // Filter to the requested channel name.  Channel values can be String (named
         // channels) or Integer (unnamed channels stored as index), so use the same
         // matching logic as TileBlender/TileAligner.
         // For RGB (channelName == null), skip this filter entirely — RGB datasets can
         // still carry a channel axis (e.g. "Default") that must not cause tiles to be dropped.
         if (channelName != null) {
            Object axisChannel = axes.get("channel");
            if (!org.micromanager.exporttiles.ChannelUtils.channelValuesMatch(
                  channelName, axisChannel)) {
               processed++;
               continue;
            }
         }

         Object rowObj = axes.get("row");
         Object colObj = axes.get("column");
         if (!(rowObj instanceof Integer) || !(colObj instanceof Integer)) {
            studio.logs().logMessage("Stitch: skipping tile - missing row/col axes=" + axes);
            processed++;
            continue;
         }
         final int row = (Integer) rowObj;
         final int col = (Integer) colObj;

         TaggedImage tile = adapter.getImage(axes, 0);
         if (tile == null || tile.pix == null) {
            studio.logs().logMessage("Stitch: null tile at row=" + row + " col=" + col);
            processed++;
            continue;
         }

         // Determine pixel count; skip unsupported pixel types.
         // For RGB (byte[], 4 bytes per pixel) nPix = byte count / 4.
         int nPix;
         if (tile.pix instanceof short[]) {
            nPix = ((short[]) tile.pix).length;
         } else if (tile.pix instanceof byte[]) {
            int nBytes = ((byte[]) tile.pix).length;
            nPix = isRgb ? nBytes / 4 : nBytes;
         } else {
            studio.logs().logMessage("Stitch: skipping tile - unsupported pix type "
                  + tile.pix.getClass().getSimpleName());
            processed++;
            continue;
         }
         studio.logs().logDebugMessage("Stitch: tile row=" + row + " col=" + col
               + " pix=" + tile.pix.getClass().getSimpleName()
               + " len=" + java.lang.reflect.Array.getLength(tile.pix)
               + " nPix=" + nPix
               + " tags=" + (tile.tags != null ? tile.tags.toString() : "null"));

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
            int bpp = isRgb ? 4 : (is16bit ? 2 : 1);
            Object[] transformed = ImageTransformUtils.transformPixels(
                  tilePix, tw, th, bpp, doMirror, rotationDeg);
            tilePix = transformed[0];
            tilePaintW = (Integer) transformed[1];
            tilePaintH = (Integer) transformed[2];
         }

         // Allocate canvas on first valid tile (using corrected dims)
         if (canvas16 == null && canvas8 == null && canvasRgb == null) {
            studio.logs().logDebugMessage("Stitch: allocating canvas corrCanvasW=" + corrCanvasW
                  + " corrCanvasH=" + corrCanvasH + " isRgb=" + isRgb + " is16bit=" + is16bit
                  + " tilePaintW=" + tilePaintW + " tilePaintH=" + tilePaintH);
            if (isRgb) {
               canvasRgb = new byte[corrCanvasW * corrCanvasH * 4];
            } else if (is16bit) {
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
         // Clip copy region to canvas bounds, handling negative origins from alignment.
         int srcX0 = Math.max(0, -destX);
         int srcY0 = Math.max(0, -destY);
         int dstX0 = Math.max(0, destX);
         int dstY0 = Math.max(0, destY);
         int copyW = Math.min(tilePaintW - srcX0, corrCanvasW - dstX0);
         int copyH = Math.min(tilePaintH - srcY0, corrCanvasH - dstY0);
         if (copyW <= 0 || copyH <= 0) {
            processed++;
            continue;
         }
         for (int ty = 0; ty < copyH; ty++) {
            if (canvas16 != null) {
               int srcOff = (srcY0 + ty) * tilePaintW + srcX0;
               int dstOff = (dstY0 + ty) * corrCanvasW + dstX0;
               System.arraycopy(tilePix, srcOff, canvas16, dstOff, copyW);
            } else if (canvas8 != null) {
               int srcOff = (srcY0 + ty) * tilePaintW + srcX0;
               int dstOff = (dstY0 + ty) * corrCanvasW + dstX0;
               System.arraycopy(tilePix, srcOff, canvas8, dstOff, copyW);
            } else if (canvasRgb != null) {
               // RGB: offsets and lengths are in bytes (4 bytes per pixel)
               int srcOff = ((srcY0 + ty) * tilePaintW + srcX0) * 4;
               int dstOff = ((dstY0 + ty) * corrCanvasW + dstX0) * 4;
               System.arraycopy(tilePix, srcOff, canvasRgb, dstOff, copyW * 4);
            }
         }

         studio.logs().logDebugMessage("Stitch: tile row=" + row + " col=" + col
               + " destX=" + destX + " destY=" + destY);
         processed++;
         final int pct = total > 0 ? processed * 100 / total : 100;
         if (progress != null) {
            progress.accept(pct);
         }
      }

      if (canvas16 != null) {
         return new Object[]{canvas16, 2, 1, corrCanvasW, corrCanvasH};
      } else if (canvas8 != null) {
         return new Object[]{canvas8, 1, 1, corrCanvasW, corrCanvasH};
      } else if (canvasRgb != null) {
         return new Object[]{canvasRgb, 4, 3, corrCanvasW, corrCanvasH};
      } else {
         // No matching tiles found — return a blank canvas matching the expected pixel type
         if (isRgb) {
            return new Object[]{new byte[corrCanvasW * corrCanvasH * 4], 4, 3,
                  corrCanvasW, corrCanvasH};
         } else if (is16bit) {
            return new Object[]{new short[corrCanvasW * corrCanvasH], 2, 1,
                  corrCanvasW, corrCanvasH};
         } else {
            return new Object[]{new byte[corrCanvasW * corrCanvasH], 1, 1,
                  corrCanvasW, corrCanvasH};
         }
      }
   }

   // -------------------------------------------------------------------------
   // Utilities
   // -------------------------------------------------------------------------

   /**
    * Convert an ARGB {@code int[]} (as returned by {@link java.awt.image.BufferedImage#getRGB})
    * to a BGRA {@code byte[]} (as expected by Micro-Manager RGB32 images).
    */
   private static byte[] argbToBgra(int[] argb) {
      byte[] bgra = new byte[argb.length * 4];
      for (int i = 0; i < argb.length; i++) {
         int pixel = argb[i];
         bgra[i * 4]     = (byte) (pixel & 0xFF);          // B
         bgra[i * 4 + 1] = (byte) ((pixel >> 8) & 0xFF);   // G
         bgra[i * 4 + 2] = (byte) ((pixel >> 16) & 0xFF);  // R
         bgra[i * 4 + 3] = 0;                               // A (unused)
      }
      return bgra;
   }

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
               continue;
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
   private static double probePixelSizeUm(DataProvider dataProvider) {
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
            Double px = meta.getPixelSizeUm();
            if (px != null && px > 0) {
               return px;
            }
         }
      } catch (Exception e) {
         // ignore
      }
      return 0.0;
   }

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


   // -------------------------------------------------------------------------
   // TiledDataViewerDataSource implementation for the NDTiff export path
   // -------------------------------------------------------------------------

   /**
    * TiledDataViewerDataSource that delegates to a finished NDTiffStorage.
    * The stitched result is stored as individual tiles; NDTiffStorage handles
    * compositing them into display images internally.
    */
   private static class StitchNdtiffDataSource
         implements org.micromanager.tileddataviewer.TiledDataViewerDataSource {

      private final org.micromanager.ndtiffstorage.NDTiffStorage storage_;
      private final boolean rgb_;
      private volatile Set<HashMap<String, Object>> imageKeysCache_ = null;

      StitchNdtiffDataSource(
            org.micromanager.ndtiffstorage.NDTiffStorage storage, boolean rgb) {
         storage_ = storage;
         rgb_ = rgb;
      }

      @Override
      public boolean isFinished() {
         return storage_.isFinished();
      }

      @Override
      public int[] getBounds() {
         return storage_.getImageBounds();
      }

      @Override
      public mmcorej.TaggedImage getImageForDisplay(
            HashMap<String, Object> axes,
            int resolutionindex,
            double xOffset, double yOffset,
            int imageWidth, int imageHeight) {
         // The viewer passes display axes with row/col stripped (e.g. {z=0, time=0}
         // or even {} for single-plane data). NDTiffStorage.getDisplayImage needs
         // the non-spatial axes present to find the right image plane; it handles
         // row/col compositing internally. Merge incoming axes with the first stored
         // entry's non-spatial axes to supply any missing z/time/channel values.
         HashMap<String, Object> fullAxes = new HashMap<>(axes);
         Set<HashMap<String, Object>> stored = storage_.getAxesSet();
         if (!stored.isEmpty()) {
            HashMap<String, Object> sample = stored.iterator().next();
            for (Map.Entry<String, Object> e : sample.entrySet()) {
               String key = e.getKey();
               if (!key.equals(org.micromanager.ndtiffstorage.NDTiffStorage.ROW_AXIS)
                     && !key.equals(org.micromanager.ndtiffstorage.NDTiffStorage.COL_AXIS)
                     && !fullAxes.containsKey(key)) {
                  fullAxes.put(key, e.getValue());
               }
            }
         }
         return storage_.getDisplayImage(fullAxes, resolutionindex,
               (int) xOffset, (int) yOffset, imageWidth, imageHeight);
      }

      @Override
      public Set<HashMap<String, Object>> getImageKeys() {
         Set<HashMap<String, Object>> cached = imageKeysCache_;
         if (cached != null) {
            return cached;
         }
         // Strip row/col — the viewer treats each unique non-spatial axes combo
         // as a single logical plane; NDTiffStorage handles tiled compositing internally.
         Set<HashMap<String, Object>> result = new java.util.HashSet<>();
         for (HashMap<String, Object> axes : storage_.getAxesSet()) {
            HashMap<String, Object> copy = new HashMap<>(axes);
            copy.remove(org.micromanager.ndtiffstorage.NDTiffStorage.ROW_AXIS);
            copy.remove(org.micromanager.ndtiffstorage.NDTiffStorage.COL_AXIS);
            result.add(copy);
         }
         imageKeysCache_ = result;
         return result;
      }

      @Override
      public int getMaxResolutionIndex() {
         return storage_.getNumResLevels() - 1;
      }

      @Override
      public void increaseMaxResolutionLevel(int newMaxResolutionLevel) {
         storage_.increaseMaxResolutionLevel(newMaxResolutionLevel);
      }

      @Override
      public String getDiskLocation() {
         return storage_.getDiskLocation();
      }

      @Override
      public void close() {
         // Do not close storage here: TiledDataViewer may still issue read requests
         // after calling dataSource.close() (async close sequence). The storage will
         // be released when the JVM GC collects it, or closed explicitly elsewhere.
      }

      @Override
      public int getImageBitDepth(HashMap<String, Object> axesPositions) {
         mmcorej.org.json.JSONObject meta = storage_.getSummaryMetadata();
         return meta != null ? meta.optInt("BitDepth", 16) : 16;
      }
   }
}
