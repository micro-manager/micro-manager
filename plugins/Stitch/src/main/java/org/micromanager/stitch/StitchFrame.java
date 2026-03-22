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
      final String outputPath = saveToStack
            ? outputDir + File.separator + namePrefix
            : "";

      // Determine the save path to reload from (null = RAM datastore, use directly).
      // loadData() is slow (filesystem I/O + metadata parsing) so it runs in the export
      // thread below rather than here on the EDT.
      final String reloadSavePath;
      if (dataProvider_ instanceof Datastore) {
         String sp = ((Datastore) dataProvider_).getSavePath();
         reloadSavePath = (sp != null && !sp.isEmpty()) ? sp : null;
      } else {
         reloadSavePath = null;
      }

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

      final boolean doBlend = blend;
      final boolean doAlign = align;
      final boolean doCorrectOrientation = correctOrientation;
      final boolean toStack = saveToStack;
      final String destPath = outputPath;
      final int exportAlignZ = alignZ;
      final int finalMaxDisplacement = maxDisplacementPx;

      new Thread(() -> {
         // For file-based datastores, re-open from disk to get proper read-mode file channels.
         //
         // Background: MultipageTiffWriter creates its readers with a write-mode constructor
         // that does not set the file_ field. Instead the writer hands the reader an already-open
         // FileChannel via setFileChannel(). After freeze(), StorageMultipageTiff.getImage()
         // starts calling pause() on readers when it switches between them, closing their
         // FileChannel. A paused write-mode reader cannot reopen its channel (file_ is null),
         // so any subsequent readImage() call causes a NullPointerException.
         //
         // The display avoids this because it calls getImagesIgnoringAxes(), which bypasses
         // getImage() and never triggers pause(). The Stitch plugin iterates all coords via
         // getImage(), cycling through multiple readers and hitting the NPE.
         //
         // The fix: re-open the dataset from disk. loadData() creates fresh read-mode readers
         // that have file_ set and can safely reopen their channel after a pause().
         // RAM datastores use a different storage backend and are not affected.
         // loadData() is I/O-bound so it runs here in the background thread.
         Datastore reloadedDatastore = null;
         DataProvider sourceProvider = dataProvider_;
         if (reloadSavePath != null) {
            try {
               reloadedDatastore = studio_.data().loadData(reloadSavePath, true);
               sourceProvider = reloadedDatastore;
            } catch (IOException ex) {
               studio_.logs().logError(ex, "Stitch: cannot re-open dataset from disk");
               final String msg = ex.getMessage();
               SwingUtilities.invokeLater(() -> {
                  progressDialog.dispose();
                  JOptionPane.showMessageDialog(null,
                        "Cannot re-open dataset from disk: " + msg,
                        "Export Error", JOptionPane.ERROR_MESSAGE);
               });
               return;
            }
         }

         // Wrap the DataProvider with row/col grid knowledge.
         StitchDataProviderAdapter adapter;
         try {
            adapter = new StitchDataProviderAdapter(sourceProvider);
         } catch (IllegalArgumentException ex) {
            closeQuietly(reloadedDatastore);
            final String msg = ex.getMessage();
            SwingUtilities.invokeLater(() -> {
               progressDialog.dispose();
               JOptionPane.showMessageDialog(null,
                     "Cannot determine tile grid positions: " + msg,
                     "Export Error", JOptionPane.ERROR_MESSAGE);
            });
            return;
         }

         final Datastore finalReloadedDatastore = reloadedDatastore;
         try {
            // Probe affine transform for orientation correction.
            int[] correction = null;
            if (doCorrectOrientation) {
               AffineTransform affine = probeAffineTransform(sourceProvider);
               correction = ImageTransformUtils.correctionFromAffine(affine);
               if (correction == null) {
                  SwingUtilities.invokeLater(() ->
                        studio_.logs().showMessage(
                              "No pixel size affine transform found in image metadata. "
                              + "Orientation correction will be skipped.", null));
               }
            }

            SummaryMetadata summary = sourceProvider.getSummaryMetadata();
            final List<String> allChannelNames = getChannelNames(summary);
            // When no channel names are set in SummaryMetadata, use integer indices as
            // String channel identifiers so that TileBlender/TileAligner can match them
            // against the Integer channel values stored in the axes map.
            final List<String> chNames;
            if (allChannelNames.isEmpty()) {
               int numCh = sourceProvider.getNextIndex(Coords.CHANNEL);
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

            buildDatastore(adapter, sourceProvider, baseAxes, alignAxes, chNames,
                  canvasW, canvasH, doBlend, doAlign, finalCorrection, finalMaxDisplacement,
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
            closeQuietly(finalReloadedDatastore);
         }
      }, "ExportMMTiles-Export").start();
   }

   private void closeQuietly(Datastore ds) {
      if (ds != null) {
         try {
            ds.close();
         } catch (IOException ioe) {
            studio_.logs().logError(ioe, "IOException closing reloaded datastore");
         }
      }
   }

   /**
    * Build an MM Datastore from the stitched/blended tile data and display it.
    *
    * <p>All pixel assembly happens here — no temp files, no loadData().</p>
    */
   private void buildDatastore(StitchDataProviderAdapter adapter,
                               DataProvider sourceProvider,
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
         SummaryMetadata srcSummary = sourceProvider.getSummaryMetadata();
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
               probeTemplateMetadata(sourceProvider, alignZ);

         // Step 5 & 6: composite/stitch — iterates all time points, z slices, channels.
         SwingUtilities.invokeLater(() -> statusLabel.setText("Computing…"));

         // raw count from caller; effective count set after probe
         final int numCh = chNames.size();
         int numZ = sourceProvider.getNextIndex(Coords.Z_SLICE);
         if (numZ < 1) {
            numZ = 1;
         }
         int numT = sourceProvider.getNextIndex(Coords.TIME_POINT);
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
            // Origins are shared across all z and channels within this time point.
            Map<Point, Point2D.Float> origins = null;
            if (doAlign) {
               final int tIdx = t;
               SwingUtilities.invokeLater(() -> statusLabel.setText(
                     "Aligning t=" + (tIdx + 1) + "…"));
               HashMap<String, Object> tAlignAxes = new HashMap<>(alignAxes);
               if (numT > 1) {
                  tAlignAxes.put(Coords.TIME_POINT, t);
               }
               TileAligner aligner = new TileAligner(adapter, tAlignAxes, effectiveChNames,
                     adapter.getSummaryMetadata());
               if (doMirror || rotationDeg != 0) {
                  aligner.setTileTransform(doMirror, rotationDeg);
               }
               origins = aligner.computeAlignedOrigins(0, maxDisplacementPx, pct -> {});
            }
            final Map<Point, Point2D.Float> tOrigins = origins;

            for (int z = 0; z < numZ; z++) {
               // baseAxes for this t/z
               HashMap<String, Object> tzAxes = new HashMap<>(baseAxes);
               tzAxes.put("z", z);
               if (numT > 1) {
                  tzAxes.put(Coords.TIME_POINT, t);
               }

               if (doBlend) {
                  TileBlender blender = new TileBlender(adapter,
                        new mmcorej.org.json.JSONObject(),
                        tzAxes, effectiveChNames, correctedBlendSummaryMD);

                  for (int c = 0; c < effectiveNumCh; c++) {
                     final int tIdx = t;
                     final int zIdx = z;
                     final String chName = effectiveChNames.get(c);
                     SwingUtilities.invokeLater(() -> statusLabel.setText(
                           "Blending t=" + (tIdx + 1) + " z=" + (zIdx + 1) + " " + chName + "…"));
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
                        // Composite at the raw (pre-correction) canvas size so that the
                        // tile geometry in TileBlender is consistent with the uncorrected
                        // tile layout.  Orientation correction is then applied to the
                        // assembled canvas; transformPixels returns the corrected dims.
                        java.awt.image.BufferedImage bimg = blender.composite(
                              0, 0, canvasW, canvasH, 0, tOrigins, blendProgress);
                        int[] argb = new int[canvasW * canvasH];
                        bimg.getRGB(0, 0, canvasW, canvasH, argb, 0, canvasW);
                        byte[] pixels = argbToBgra(argb);
                        if (correction != null && (doMirror || rotationDeg != 0)) {
                           Object[] transformed = ImageTransformUtils.transformPixels(
                                 pixels, canvasW, canvasH, 4, doMirror, rotationDeg);
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
                     ds.putImage(mmImg);
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
                                 + " " + chName + "…"));
                     final int imagesBefore = imagesWritten;
                     Object[] result = stitchTiles(studio_,
                              adapter,
                              tzAxes,
                              chName,
                              canvasW,
                              canvasH,
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
                     ds.putImage(mmImg);
                     imagesWritten++;
                  }
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
    * @param channelName the channel to stitch, or null for RGB (no channel axis)
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
      // Corrected tile dims: swap w/h for 90/270 rotations
      boolean swapTileDims = rotationDeg == 90 || rotationDeg == 270;
      // Corrected canvas dims (determined once tile dims are known)
      int corrCanvasW = swapTileDims ? canvasH : canvasW;
      int corrCanvasH = swapTileDims ? canvasW : canvasH;

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
            if (!channelValuesMatch(channelName, axisChannel)) {
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

         studio.logs().logDebugMessage("Stitch: copied tile row=" + row + " col=" + col
               + " destX=" + destX + " destY=" + destY
               + " copyW=" + copyW + " copyH=" + copyH);
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

   /**
    * Returns true when a channel name from the caller matches a channel value from an axes map.
    *
    * <p>Handles the case where unnamed channels are stored as {@code Integer} indices
    * but the caller passes the index as a {@code String} (e.g. {@code "0"}).</p>
    */
   private static boolean channelValuesMatch(String callerName, Object storedValue) {
      if (storedValue == null) {
         return false;
      }
      if (callerName.equals(storedValue)) {
         return true;
      }
      if (storedValue instanceof Integer) {
         try {
            return Integer.parseInt(callerName) == (Integer) storedValue;
         } catch (NumberFormatException e) {
            return false;
         }
      }
      return false;
   }
}
