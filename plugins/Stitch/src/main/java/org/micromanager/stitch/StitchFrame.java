package org.micromanager.stitch;

import com.google.common.eventbus.Subscribe;
import java.awt.Dialog;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Array;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
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
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
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
import org.micromanager.exporttiles.ChannelUtils;
import org.micromanager.exporttiles.TileAligner;
import org.micromanager.exporttiles.TileBlender;
import org.micromanager.imageprocessing.ImageTransformUtils;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.propertymap.MutablePropertyMapView;
import org.micromanager.tileddataprovider.NDTiffProviderAdapter;
import org.micromanager.tileddataviewer.TiledDataViewerAPI;
import org.micromanager.tileddataviewer.TiledDataViewerDataProviderAPI;
import org.micromanager.tileddataviewer.TiledDataViewerDataSource;
import org.micromanager.tileddataviewer.TiledDataViewerDataViewerAPI;
import org.micromanager.tileddataviewer.TiledDataViewerFactory;

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

   // Set by the progress dialog's Stop button to request a cooperative cancel of the export.
   // The write loop checks it per output tile and stops at a safe point, still finalizing the
   // (partial) NDTiff so it remains valid. Reset at the start of each export.
   private final java.util.concurrent.atomic.AtomicBoolean exportCancelled_ =
         new java.util.concurrent.atomic.AtomicBoolean(false);

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
      correctOrientationCheck_.setToolTipText("<html>Rotate/mirror tiles and place them so the "
            + "stitched image matches the stage frame (lowest stage X,Y at top-left).<br>"
            + "The correction is derived entirely from the pixel-size <b>affine transform</b>, "
            + "which encodes the camera's orientation <i>and handedness</i> relative to the stage "
            + "(including any mirror from imaging through the sample, e.g. from below).<br>"
            + "If the result comes out <b>mirror-reversed</b>, the affine is missing a reflection "
            + "term &mdash; recalibrate the pixel-size affine for your optical path rather than "
            + "expecting this option to add a mirror.</html>");
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
      final String originalPrefix = namePrefix; // saved before uniquification for preferences
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
         if (uniquePath == null) {
            studio_.logs().showError("Could not find a unique output directory name.", this);
            return;
         }
         // Strip the parent dir back out — we only want the (possibly suffixed) leaf name.
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
      // Use the (already-uniquified) namePrefix as the dataset name so repeated
      // exports get distinct viewer titles (e.g. "stitched", "stitched_1", …).
      final String datasetName = namePrefix.isEmpty()
            ? dataProvider_.getName() + "_stitched" : namePrefix;

      // Persist dialog settings to profile.
      settings_.putBoolean(PREF_CORRECT_ORIENTATION, correctOrientation);
      settings_.putBoolean(PREF_ALIGN, align);
      settings_.putBoolean(PREF_BLEND, blend);
      settings_.putString(PREF_SAVE_FORMAT, (String) saveFormatCombo_.getSelectedItem());
      settings_.putString(PREF_OUTPUT_DIR, outputDir);
      settings_.putString(PREF_NAME_PREFIX, originalPrefix);
      settings_.putString(PREF_MAX_DISPLACEMENT, maxDisplacementField_.getText().trim());

      dispose();

      // Progress dialog
      // Parent the progress dialog to the source display window so it appears over it.
      final Window sourceWindow = displayWindow_ != null ? displayWindow_.getWindow() : null;
      JDialog progressDialog = new JDialog(sourceWindow, "Exporting...",
            Dialog.ModalityType.MODELESS);
      progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      JProgressBar bar = new JProgressBar(0, 100);
      bar.setStringPainted(true);
      // Start animated: the "Preparing" phase (adapter build, canvas sizing) has no
      // measurable progress, so an indeterminate bar shows the user it is alive until the
      // first determinate phase (Aligning/Writing) sets a 0->100 value. Hide the "0%" string
      // while indeterminate -- it is meaningless and confusing during this phase.
      bar.setStringPainted(false);
      bar.setIndeterminate(true);
      JLabel statusLabel = new JLabel("Preparing...");
      // Reset cancel state for this export and wire a Stop button.
      exportCancelled_.set(false);
      JButton stopButton = new JButton("Stop");
      stopButton.addActionListener(evt -> {
         exportCancelled_.set(true);
         stopButton.setEnabled(false);
         statusLabel.setText("Stopping...");
      });
      progressDialog.getContentPane().setLayout(
            new MigLayout("insets 12, gap 8", "[grow]"));
      progressDialog.getContentPane().add(statusLabel, "wrap");
      progressDialog.getContentPane().add(bar, "growx, wrap");
      progressDialog.getContentPane().add(stopButton, "align center");
      URL iconUrl = getClass().getResource("/org/micromanager/icons/microscope.gif");
      if (iconUrl != null) {
         progressDialog.setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
      }
      progressDialog.pack();
      // Position over the source window (falls back to screen-centre if it is null).
      progressDialog.setLocationRelativeTo(sourceWindow);
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
         // Resolve orientation BEFORE building the grid, so the same affine drives both
         // the per-tile pixel transform AND tile placement (they can never diverge).
         int[] correction = null;
         StitchDataProviderAdapter.OrientationModel orientationModel = null;
         if (doCorrectOrientation) {
            ResolvedOrientation resolved = resolveOrientation(dataProvider_);
            if (resolved == null) {
               SwingUtilities.invokeLater(() ->
                     studio_.logs().showMessage(
                           "No pixel size affine transform found in image metadata. "
                           + "Orientation correction will be skipped.", null));
            } else {
               correction = resolved.pixelOp;
               orientationModel = resolved.model;
               int rot = correction != null ? correction[0] : 0;
               boolean mir = correction != null && correction[1] != 0;
               // Breadcrumb: the correction is whatever the affine encodes. A mirrored
               // result means the affine lacks a reflection term (recalibrate it), not a
               // stitcher bug. The affine already captures handedness from the optical
               // path, including imaging through the sample (e.g. from below).
               studio_.logs().logMessage(String.format(
                     "Stitch: orientation correction from pixel-size affine "
                     + "(rotation=%d deg, mirror=%b). This trusts the affine's handedness; "
                     + "if the stitch looks mirror-reversed, recalibrate the pixel-size "
                     + "affine for your optical path.", rot, mir));
            }
         }

         // Wrap the DataProvider with row/col grid knowledge. When orientationModel is
         // non-null, placement is affine-driven; otherwise it is the legacy raw behavior.
         StitchDataProviderAdapter adapter;
         try {
            adapter = new StitchDataProviderAdapter(dataProvider_, orientationModel);
         } catch (IllegalArgumentException ex) {
            final String msg = ex.getMessage();
            SwingUtilities.invokeLater(() -> {
               progressDialog.dispose();
               JOptionPane.showMessageDialog(sourceWindow,
                     "Cannot determine tile grid positions: " + msg,
                     "Export Error", JOptionPane.ERROR_MESSAGE);
            });
            return;
         }

         try {

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
         } catch (OutOfMemoryError oom) {
            // OutOfMemoryError is an Error, not an Exception, so it would otherwise
            // escape the catch below and kill the export thread silently. Report it with
            // actionable guidance: the stitch holds the output canvas plus alignment data
            // in the heap, so a large grid needs a larger -Xmx (Edit > Options > Memory).
            // logError takes an Exception; OutOfMemoryError is an Error, so wrap it.
            studio_.logs().logError(new RuntimeException(oom),
                  "Stitch export ran out of heap memory");
            long maxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
            final String oomMsg =
                  "Out of memory while stitching.\n\n"
                  + "The JVM heap (currently about " + maxMb + " MB) was exhausted. "
                  + "Increase it via ImageJ: Edit > Options > Memory & Threads, set a "
                  + "larger value (e.g. 50000 MB), then restart Micro-Manager. Reducing "
                  + "the output region or disabling alignment also lowers memory use.";
            SwingUtilities.invokeLater(() -> {
               progressDialog.dispose();
               JOptionPane.showMessageDialog(sourceWindow, oomMsg,
                     "Export Error - Out of Memory", JOptionPane.ERROR_MESSAGE);
            });
         } catch (Exception ex) {
            studio_.logs().logError(ex, "Stitch export failed");
            String msg = ex.getMessage();
            if (msg == null) {
               msg = ex.getClass().getSimpleName();
            }
            final String displayMsg = msg;
            SwingUtilities.invokeLater(() -> {
               progressDialog.dispose();
               JOptionPane.showMessageDialog(sourceWindow,
                     "Export failed: " + displayMsg,
                     "Export Error", JOptionPane.ERROR_MESSAGE);
            });
         } finally {
            adapter.close();
         }
      }, "ExportMMTiles-Export").start();
   }

   /**
    * Set the progress bar to a phase-local 0-100 value on the EDT. Each export phase
    * (Aligning, Writing, ...) drives the bar across the full 0-100 range so the user
    * always sees motion within the current phase; the status label names the phase.
    */
   private static void setPhaseProgress(JProgressBar bar, int pct) {
      final int v = Math.max(0, Math.min(100, pct));
      SwingUtilities.invokeLater(() -> {
         if (bar.isIndeterminate()) {
            bar.setIndeterminate(false);
         }
         // Show the percentage now that we have a meaningful determinate value.
         bar.setStringPainted(true);
         bar.setValue(v);
      });
   }

   /**
    * Put the progress bar into indeterminate (animated) mode with a status label, for
    * phases that have no measurable sub-progress (e.g. NDTiff finalization) so the bar
    * keeps moving and the user knows the process is still alive.
    */
   private static void setIndeterminate(JProgressBar bar, JLabel statusLabel, String text) {
      SwingUtilities.invokeLater(() -> {
         statusLabel.setText(text);
         // Hide the percentage string -- it has no meaning during an indeterminate phase.
         bar.setStringPainted(false);
         bar.setIndeterminate(true);
      });
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
      NDTiffStorage ndtiffStorage = null;

      // Derive correction components (null correction = no-op)
      boolean doMirror = correction != null && correction[1] != 0;
      int rotationDeg = correction != null ? correction[0] : 0;
      // Compute output canvas from corrected tile geometry rather than naively swapping
      // the raw adapter canvas dims.  The adapter canvas is in stage-coordinate space
      // (X→width, Y→height); swapping it for 90/270° rotations is wrong when the grid
      // is not square.  Instead derive the output size from corrected tile step * grid extent.
      boolean swapCanvasDims = rotationDeg == 90 || rotationDeg == 270;
      JSONObject rawMD = adapter.getSummaryMetadata();
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

      // The in-memory (RAM / MM Datastore) path materialises the whole canvas as a single
      // pixel array per channel. A Java array is indexed by int, so a canvas with more than
      // Integer.MAX_VALUE pixels cannot be represented and would throw a cryptic
      // NegativeArraySizeException deep in TileBlender. Reject it up front with an
      // actionable message. The NDTiff path has no such limit (it writes uniform output
      // tiles), so steer the user there.
      if (!toNdtiff) {
         long canvasPixels = (long) outCanvasW * (long) outCanvasH;
         if (canvasPixels > Integer.MAX_VALUE) {
            final String msg = "The stitched canvas is " + outCanvasW + " x " + outCanvasH
                  + " (" + canvasPixels + " pixels), which exceeds the "
                  + Integer.MAX_VALUE + "-pixel limit of a single in-memory image. "
                  + "Save to NDTiff instead -- it stores the canvas as tiles and has no "
                  + "such size limit.";
            SwingUtilities.invokeLater(() -> {
               Window owner = progressDialog.getOwner();
               progressDialog.dispose();
               JOptionPane.showMessageDialog(owner, msg,
                     "Canvas too large for in-memory export", JOptionPane.ERROR_MESSAGE);
            });
            // The caller's finally-block closes the adapter; just return here.
            return;
         }
      }

      try {
         // Step 2: detect pixel type, determine effectiveChNames, run alignment (if enabled)
         // — all before writing SummaryMetadata so the canvas size is final.
         // No measurable sub-progress here, so animate the bar (indeterminate) instead of
         // sitting at a dead 0%. The Aligning/Writing phases set a determinate 0->100 after.
         setIndeterminate(bar, statusLabel, "Computing...");

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
                  + " len=" + Array.getLength(probe.pix)
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
            setPhaseProgress(bar, 0);  // determinate 0%, clears the "Computing" animation
            TileAligner t0Aligner = new TileAligner(adapter, alignAxes, effectiveChNames,
                  adapter.getSummaryMetadata());
            if (doMirror || rotationDeg != 0) {
               t0Aligner.setTileTransform(doMirror, rotationDeg);
            }
            // Alignment is its own phase: drive the bar 0->100 across the alignment work.
            t0Origins = t0Aligner.computeAlignedOrigins(0, maxDisplacementPx,
                  pct -> setPhaseProgress(bar, pct));
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
                  Map<Point, Point2D.Float> shifted = new HashMap<>();
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
               // Canvas can only grow from alignment — shrinking would clip real tiles.
               newCanvasW = Math.max(outCanvasW, newCanvasW);
               newCanvasH = Math.max(outCanvasH, newCanvasH);
               if (newCanvasW != outCanvasW || newCanvasH != outCanvasH) {
                  studio_.logs().logMessage("Stitch: canvas grown by alignment from "
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

         // For NDTiff output, create NDTiff storage with a uniform output tile grid.
         // The canvas is composited into outTileSize×outTileSize tiles with zero overlap.
         if (toNdtiff) {
            ndtiffStorage = buildNdtiffSummaryMetadata(
                  destPath, datasetName,
                  NDTIFF_OUTPUT_TILE_SIZE,
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
         JSONObject blendSummaryMD = adapter.getSummaryMetadata();
         final int tileW = blendSummaryMD != null ? blendSummaryMD.optInt("Width", 0) : 0;
         final int tileH = blendSummaryMD != null ? blendSummaryMD.optInt("Height", 0) : 0;

         // Build per-tile orientation transforms.  All rotation angles (0/90/180/270)
         // are applied per-tile for both the blend and simple paths.
         // For 90/270° rotations the corrected tile dimensions are swapped, so we
         // build a corrected summary metadata for TileBlender that reflects the new dims.
         final UnaryOperator<short[]> tileTransform16;
         final UnaryOperator<byte[]> tileTransform8;
         final JSONObject correctedBlendSummaryMD;
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
               JSONObject md = new JSONObject();
               md.put("Width", corrTileW);
               md.put("Height", corrTileH);
               md.put("GridPixelOverlapX", corrOverlapX);
               md.put("GridPixelOverlapY", corrOverlapY);
               correctedBlendSummaryMD = md;
            } catch (JSONException e) {
               throw new IllegalStateException("Failed to build corrected summary metadata", e);
            }
         } else {
            tileTransform16 = null;
            tileTransform8 = null;
            correctedBlendSummaryMD = blendSummaryMD;
         }

         // Writing is a fresh phase: restart the bar at a determinate 0 so it fills 0->100
         // across all (t, z, channel) output images regardless of how the prior phase left
         // it (also clears the "Computing" indeterminate animation when alignment is off).
         setPhaseProgress(bar, 0);
         int imagesWritten = 0;
         for (int t = 0; t < numT; t++) {
            if (exportCancelled_.get()) {
               break; // stop before starting a new time point
            }
            // Compute aligned origins once per time point at the selected z slice.
            // t=0 was already computed before SummaryMetadata was written; reuse it.
            Map<Point, Point2D.Float> origins;
            if (!doAlign) {
               origins = null;
            } else if (t == 0) {
               origins = t0Origins;  // already computed and canvas-adjusted above
            } else {
               final int tIdx = t;
               setIndeterminate(bar, statusLabel, "Aligning t=" + (tIdx + 1) + "...");
               HashMap<String, Object> tAlignAxes = new HashMap<>(alignAxes);
               tAlignAxes.put(Coords.TIME_POINT, t);
               TileAligner aligner = new TileAligner(adapter, tAlignAxes, effectiveChNames,
                     adapter.getSummaryMetadata());
               if (doMirror || rotationDeg != 0) {
                  aligner.setTileTransform(doMirror, rotationDeg);
               }
               origins = aligner.computeAlignedOrigins(0, maxDisplacementPx,
                     pct -> setPhaseProgress(bar, pct));
               if (origins != null) {
                  studio_.logs().logMessage("Stitch alignment t=" + (t + 1) + ": "
                        + aligner.getLastAlignmentStats());
                  // Apply the same coordinate shift used for t=0 so all origins are
                  // non-negative and fit within the already-sized canvas.
                  final float sx = alignOriginShiftX;
                  final float sy = alignOriginShiftY;
                  Map<Point, Point2D.Float> shifted = new HashMap<>();
                  boolean anyOutOfBounds = false;
                  for (Map.Entry<Point, Point2D.Float> e : origins.entrySet()) {
                     float ox = e.getValue().x + sx;
                     float oy = e.getValue().y + sy;
                     shifted.put(e.getKey(), new Point2D.Float(ox, oy));
                     // Origins are in pre-rotation tile space; compare against
                     // pre-rotation canvas extents (swapped when output is rotated 90/270°).
                     int preRotCanvasW = swapCanvasDims ? outCanvasH : outCanvasW;
                     int preRotCanvasH = swapCanvasDims ? outCanvasW : outCanvasH;
                     if (ox < 0 || oy < 0 || ox + rawTileW > preRotCanvasW
                           || oy + rawTileH > preRotCanvasH) {
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
                  // Nominal placement: log only tiles that actually exist (from the adapter
                  // axes set), not the full maxRow x maxCol product -- the latter can be huge
                  // and floods the log. Cap the number of lines as a final safety net.
                  sortedTiles = new ArrayList<>();
                  Set<Point> realTiles = new HashSet<>();
                  for (HashMap<String, Object> stored : adapter.getAxesSet()) {
                     Object rowObj = stored.get(NDTiffStorage.ROW_AXIS);
                     Object colObj = stored.get(NDTiffStorage.COL_AXIS);
                     if (rowObj instanceof Integer && colObj instanceof Integer) {
                        realTiles.add(new Point((Integer) colObj, (Integer) rowObj));
                     }
                  }
                  sortedTiles.addAll(realTiles);
                  sortedTiles.sort((a, b) -> a.y != b.y ? a.y - b.y : a.x - b.x);
                  final int maxLogLines = 500;
                  int logged = 0;
                  for (Point tile : sortedTiles) {
                     if (logged++ >= maxLogLines) {
                        studio_.logs().logMessage("Stitch: ... (" + (sortedTiles.size()
                              - maxLogLines) + " more nominal tiles not logged)");
                        break;
                     }
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
                  // NDTiff path: composite the full canvas into a uniform output tile grid.
                  // tOrigins=null → nominal tile positions; non-null → alignment-corrected.
                  // doBlend controls feathering at overlap seams (not tile placement).
                  // For grayscale, tileTransform16/8 applies the per-tile orientation
                  // correction; correctedBlendSummaryMD reflects swapped tile dims.
                  // For RGB, composite() handles the full canvas (no per-tile transform).
                  final int tIdx = t;
                  final int zIdx = z;
                  SwingUtilities.invokeLater(() -> statusLabel.setText(
                        "Writing t=" + (tIdx + 1) + " z=" + (zIdx + 1) + "..."));
                  // One writeCanvasTiledNdtiff call per (t, z); its pct (0-100) already covers
                  // all channels and output tiles for that slice. The write phase therefore
                  // spans numT*numZ such calls: global pct = (callIdx*100 + pct)/(numT*numZ).
                  // (Do NOT divide by totalImages here -- writeCanvasTiledNdtiff returns a
                  // count of output TILES, a different unit, which previously capped the bar
                  // at ~100/numChannels percent.)
                  final int ndtiffCallsBefore = tIdx * numZ + zIdx;
                  final int ndtiffTotalCalls = numT * numZ;
                  final IntConsumer ndtiffProgress = pct ->
                        setPhaseProgress(bar,
                              (ndtiffCallsBefore * 100 + pct) / Math.max(1, ndtiffTotalCalls));
                  imagesWritten += writeCanvasTiledNdtiff(adapter, ndtiffStorage, tzAxes,
                        effectiveChNames,
                        tOrigins, doBlend, is16bit, isRgb, z, t,
                        outCanvasW, outCanvasH, NDTIFF_OUTPUT_TILE_SIZE,
                        isRgb ? blendSummaryMD : correctedBlendSummaryMD,
                        tileTransform16, tileTransform8, numZ, numT, ndtiffProgress);
               } else if (doBlend) {
                  // RGB composite() has no per-tile transform — the whole assembled canvas is
                  // rotated afterward, so the blender must use the raw (uncorrected) tile geometry.
                  // Grayscale paths apply per-tile transforms and need the corrected geometry.
                  TileBlender blender = new TileBlender(adapter,
                        new JSONObject(),
                        tzAxes, effectiveChNames,
                        isRgb ? blendSummaryMD : correctedBlendSummaryMD);

                  for (int c = 0; c < effectiveNumCh; c++) {
                     final int tIdx = t;
                     final int zIdx = z;
                     final String chName = effectiveChNames.get(c);
                     SwingUtilities.invokeLater(() -> statusLabel.setText(
                           "Blending t=" + (tIdx + 1) + " z=" + (zIdx + 1) + " " + chName + "..."));
                     final int imagesBefore = imagesWritten;
                     final IntConsumer blendProgress =
                           pct -> setPhaseProgress(bar,
                                 (imagesBefore * 100 + pct) / Math.max(1, totalImages));

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
                        BufferedImage bimg = blender.composite(
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
                                 + " " + chName + "..."));
                     final int imagesBefore = imagesWritten;
                     Object[] result = stitchTiles(studio_,
                              adapter,
                              tzAxes,
                              chName,
                              outCanvasW,
                              outCanvasH,
                           correction, tOrigins, is16bit, isRgb,
                           pct -> setPhaseProgress(bar,
                                 (imagesBefore * 100 + pct) / Math.max(1, totalImages)));
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

         if (toNdtiff) {
            // The pyramid was built incrementally during writing (NDTIFF_MAX_RES_LEVEL set
            // after the first tile). This call backstops the already-built levels and is a
            // no-op when they exist; guard it so a pyramid hiccup can never prevent
            // finishedWriting(), which MUST always run -- an unfinalized NDTiff throws
            // NegativeArraySizeException when a viewer opens it.
            // Finalizing has no measurable sub-progress, so show an animated (indeterminate)
            // bar rather than freezing the user at a fixed value.
            setIndeterminate(bar, statusLabel, "Finalizing...");
            try {
               ndtiffStorage.increaseMaxResolutionLevel(
                     pyramidDepthForCanvas(outCanvasW, outCanvasH));
            } catch (Throwable pyramidError) {
               studio_.logs().logError(new RuntimeException(pyramidError),
                     "Stitch: pyramid (low-res) generation issue; full-resolution data is "
                     + "still valid. Zoomed-out display may be limited.");
            }
            // Persist the source channel colors/contrast into the NDTiff (written to
            // display_settings.txt by finishedWriting()) so that reopening the dataset
            // (e.g. in the Explorer plugin) restores the colors set here rather than
            // falling back to guessed palette colors.
            writeNdtiffDisplaySettings(ndtiffStorage, effectiveChNames, sourceDisplaySettings);
            ndtiffStorage.finishedWriting();
            setPhaseProgress(bar, 100);
            final boolean wasCancelled = exportCancelled_.get();
            final NDTiffStorage finalStorage = ndtiffStorage;
            final String ndtiffPath = destPath;
            final DisplaySettings dispSettings = sourceDisplaySettings;
            final boolean finalIsRgb = isRgb;
            final int finalCanvasW = outCanvasW;
            final int finalCanvasH = outCanvasH;
            SwingUtilities.invokeLater(() -> {
               // Capture the dialog's owner (the source display window) before disposing so
               // the cancelled message can be centred over it rather than the screen.
               Window owner = progressDialog.getOwner();
               progressDialog.dispose();
               if (wasCancelled) {
                  // Partial dataset was finalized and is valid, but incomplete -- don't open
                  // it as if it were a finished stitch; tell the user where it is.
                  JOptionPane.showMessageDialog(owner,
                        "Stitch cancelled. A partial dataset was saved to:\n" + ndtiffPath,
                        "Stitch Cancelled", JOptionPane.INFORMATION_MESSAGE);
                  return;
               }
               try {
                  openInTiledDataViewer(finalStorage, ndtiffPath, datasetName,
                        dispSettings, finalIsRgb, finalCanvasW, finalCanvasH);
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
    * Output tile size for NDTiff canvas export (pixels per side).
    * One tile uses outTileSize² × bytesPerPixel of memory for the pixel buffer.
    */
   private static final int NDTIFF_OUTPUT_TILE_SIZE = 2048;

   /**
    * Maximum number of parallel compositing worker threads, or 0 for "auto"
    * (availableProcessors - 1). Set to 1 to force fully single-threaded compositing if a
    * source storage ever proves unsafe for concurrent reads. NDTiff writes always remain
    * single-threaded regardless of this value.
    */
   private static final int STITCH_MAX_COMPOSITE_THREADS = 0;

   /**
    * Minimum pyramid depth for NDTiff output. The actual depth is chosen per canvas by
    * {@link #pyramidDepthForCanvas(int, int)} so the coarsest level fits in a window.
    */
   private static final int NDTIFF_MIN_RES_LEVEL = 4;

   /**
    * Largest dimension (px) the coarsest pyramid level should have. The pyramid depth is
    * chosen so the canvas downsampled by 2^depth fits within this, which keeps the fully
    * zoomed-out view small enough to drag smoothly. NDTiff caps levels at MAX_RESOLUTION_LEVEL.
    */
   private static final int NDTIFF_COARSEST_TARGET_PX = 2048;

   /**
    * Choose the NDTiff pyramid depth (number of downsampled levels) for a canvas so the
    * coarsest level's larger dimension is at most {@link #NDTIFF_COARSEST_TARGET_PX}.
    * Without enough levels, a very zoomed-out view must assemble a large image from many
    * low-res tiles on every drag, making panning sluggish. Bounded below by
    * {@link #NDTIFF_MIN_RES_LEVEL} and above by NDTiffStorage.MAX_RESOLUTION_LEVEL.
    *
    * <p>Required for multi-gigapixel canvases: without a pyramid the viewer clamps to res
    * level 0 and tries to allocate a full-canvas array, overflowing int.</p>
    */
   private static int pyramidDepthForCanvas(int canvasW, int canvasH) {
      int largest = Math.max(canvasW, canvasH);
      int depth = NDTIFF_MIN_RES_LEVEL;
      while (depth < NDTiffStorage.MAX_RESOLUTION_LEVEL
            && (largest >> depth) > NDTIFF_COARSEST_TARGET_PX) {
         depth++;
      }
      return depth;
   }

   /**
    * Composites the fully blended, alignment-corrected stitched canvas into a regular
    * uniform grid of output tiles and writes each to NDTiff storage.
    *
    * <p>Rather than writing source-grid tiles with per-tile position tags, this method
    * divides the output canvas ({@code canvasW × canvasH}) into a uniform grid of
    * {@code outTileSize × outTileSize} tiles and calls {@link TileBlender} to composite
    * each output tile from the source data. NDTiffStorage sees a clean uniform grid
    * (overlap=0) and its built-in pyramid generation works correctly at all zoom levels
    * without any per-tile position tags.</p>
    *
    * <p>Memory usage: one output tile at a time (~{@code outTileSize²×2} bytes for 16-bit),
    * regardless of total canvas size.</p>
    *
    * @return number of tile-channel images written
    */
   private int writeCanvasTiledNdtiff(
         StitchDataProviderAdapter adapter,
         NDTiffStorage ndtiffStorage,
         HashMap<String, Object> tzAxes,
         List<String> effectiveChNames,
         Map<Point, Point2D.Float> alignedOrigins,
         boolean doBlend,
         boolean is16bit, boolean isRgb,
         int z, int t,
         int canvasW, int canvasH,
         int outTileSize,
         JSONObject sourceMD,
         UnaryOperator<short[]> tileTransform16,
         UnaryOperator<byte[]> tileTransform8,
         int numZ, int numT,
         IntConsumer tileProgress)
         throws Exception {

      // TileBlender is used for canvas assembly regardless of feathering. It requires:
      //   - sourceMD: provides tile dimensions and overlap for computing step sizes.
      //   - tileOrigins: explicit per-tile canvas positions. When null, TileBlender derives
      //     nominal positions from col * (tileW - overlapX).
      //
      // doBlend controls weighted blending at overlap seams:
      //   true  → use real overlap from sourceMD; TileBlender feathers at seams.
      //   false → suppress feathering by zeroing overlap in sourceMD. But nominal positions
      //           must still use the real step (tileW - realOverlapX), so when alignedOrigins
      //           is null we pre-compute explicit nominal origins from the real step.
      final TileBlender compositor;
      final Map<Point, Point2D.Float> tileOrigins;
      if (!doBlend) {
         // Build a zero-overlap sourceMD so the compositor does not feather at seams.
         JSONObject noFeatherMD = sourceMD;
         if (sourceMD != null) {
            try {
               noFeatherMD = new JSONObject(sourceMD.toString());
               noFeatherMD.put("GridPixelOverlapX", 0);
               noFeatherMD.put("GridPixelOverlapY", 0);
            } catch (JSONException ignore) { /* keep original */ }
         }
         // With zero overlap in sourceMD, nominal step would be tileW instead of
         // tileW - realOverlap. Pre-compute explicit origins from the real step so positions
         // stay correct.
         if (alignedOrigins == null && sourceMD != null) {
            int srcTileW  = sourceMD.optInt("Width", 0);
            int srcTileH  = sourceMD.optInt("Height", 0);
            int realOverlapX = sourceMD.optInt("GridPixelOverlapX", 0);
            int realOverlapY = sourceMD.optInt("GridPixelOverlapY", 0);
            int stepX = Math.max(1, srcTileW - realOverlapX);
            int stepY = Math.max(1, srcTileH - realOverlapY);
            Map<Point, Point2D.Float> nominalOrigins = new HashMap<>();
            for (HashMap<String, Object> stored : adapter.getAxesSet()) {
               Object rowObj = stored.get(NDTiffStorage.ROW_AXIS);
               Object colObj = stored.get(NDTiffStorage.COL_AXIS);
               if (rowObj instanceof Integer && colObj instanceof Integer) {
                  int r = (Integer) rowObj;
                  int c = (Integer) colObj;
                  nominalOrigins.put(new Point(c, r), new Point2D.Float(c * stepX, r * stepY));
               }
            }
            tileOrigins = nominalOrigins.isEmpty() ? null : nominalOrigins;
         } else {
            tileOrigins = alignedOrigins;
         }
         compositor = new TileBlender(adapter, new JSONObject(),
               tzAxes, effectiveChNames, noFeatherMD);
      } else {
         compositor = new TileBlender(adapter, new JSONObject(),
               tzAxes, effectiveChNames, sourceMD);
         tileOrigins = alignedOrigins;
      }

      JSONObject storageMD = ndtiffStorage.getSummaryMetadata();
      double pixelSizeUm = storageMD != null ? storageMD.optDouble("PixelSize_um", 0) : 0;

      final int numCols = (int) Math.ceil((double) canvasW / outTileSize);
      final int numRows = (int) Math.ceil((double) canvasH / outTileSize);
      final int totalTilesThisCall = numRows * numCols * effectiveChNames.size();

      // Build the list of output-tile work items (one per canvas tile per channel).
      final List<int[]> workItems = new ArrayList<>(totalTilesThisCall);
      for (int canvasRow = 0; canvasRow < numRows; canvasRow++) {
         for (int canvasCol = 0; canvasCol < numCols; canvasCol++) {
            for (int ch = 0; ch < effectiveChNames.size(); ch++) {
               workItems.add(new int[]{canvasRow, canvasCol, ch});
            }
         }
      }

      // Parallelise the CPU-bound compositing across worker threads, but keep the NDTiff
      // writes (and the inline pyramid build, which is NOT thread-safe) on a single writer.
      // Disk I/O is not the bottleneck (spindle vs SSD measured identical) -- compositing is.
      // The concurrency lives in ParallelTileWriter (unit-tested separately); here we only
      // supply the producer (composite a tile) and consumer (write a tile).
      //
      // THREAD-SAFETY CAVEAT: producers call compositeOutputTile concurrently, which reads
      // from the shared source storage via TileBlender. That is only safe because
      // StitchDataProviderAdapter serializes source reads (MultipageTiffReader is not
      // concurrent-read-safe). If a new source path bypasses that adapter, revisit this.
      // STITCH_MAX_COMPOSITE_THREADS=1 forces fully serial compositing as an escape hatch.
      final int numWorkers = STITCH_MAX_COMPOSITE_THREADS > 0
            ? STITCH_MAX_COMPOSITE_THREADS
            : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
      final TileBlender finalCompositor = compositor;
      final Map<Point, Point2D.Float> finalTileOrigins = tileOrigins;
      final double finalPixelSizeUm = pixelSizeUm;
      // Tracks whether the pyramid depth has been set (once, on the first write).
      final boolean[] pyramidSet = {false};

      ParallelTileWriter.Producer<Object[]> producer = idx -> {
         int[] wi = workItems.get(idx);
         int canvasRow = wi[0];
         int canvasCol = wi[1];
         String chName = effectiveChNames.get(wi[2]);
         int roiX = canvasCol * outTileSize;
         int roiY = canvasRow * outTileSize;
         int roiW = Math.min(outTileSize, canvasW - roiX);
         int roiH = Math.min(outTileSize, canvasH - roiY);
         Object pixelData = compositeOutputTile(finalCompositor, finalTileOrigins,
               roiX, roiY, roiW, roiH, outTileSize, isRgb, is16bit,
               chName, tileTransform16, tileTransform8);
         HashMap<String, Object> axes = buildNdtiffAxes(canvasRow, canvasCol, z, t,
               isRgb ? null : chName, numZ, numT);
         JSONObject tags = buildNdtiffTags(
               outTileSize, outTileSize, isRgb, is16bit, finalPixelSizeUm, axes);
         return new Object[]{pixelData, tags, axes};
      };

      ParallelTileWriter.Consumer<Object[]> consumer = (item, consumedCount) -> {
         Object pixelData = item[0];
         JSONObject tags = (JSONObject) item[1];
         @SuppressWarnings("unchecked")
         HashMap<String, Object> axes = (HashMap<String, Object>) item[2];
         ndtiffStorage.putImageMultiRes(pixelData, tags, axes,
               isRgb, is16bit ? 16 : 8, outTileSize, outTileSize).get();
         // Set the pyramid depth once, right after the first tile is written, so every
         // subsequent putImageMultiRes builds the low-res levels inline. The inline pyramid
         // uses overwritePixels on shared low-res tiles and is NOT thread-safe, which is
         // exactly why the consumer (and this call) stays single-threaded.
         if (!pyramidSet[0]) {
            ndtiffStorage.increaseMaxResolutionLevel(pyramidDepthForCanvas(canvasW, canvasH));
            pyramidSet[0] = true;
         }
      };

      // Cooperative cancel: stop at a tile boundary. The caller still calls finishedWriting()
      // so the partial NDTiff is valid.
      return ParallelTileWriter.run(workItems.size(), numWorkers, producer, consumer,
            exportCancelled_::get,
            tileProgress == null ? null : pct -> tileProgress.accept(pct));
   }

   /**
    * Composite one output ROI from source tiles and pad it to a full
    * {@code outTileSize x outTileSize} tile (NDTiff needs a consistent row stride).
    *
    * <p>Pure and thread-safe: it only reads from the (stateless) {@link TileBlender} and the
    * read-only source storage, and returns a freshly-allocated pixel array. This is the
    * CPU-bound work parallelised across composite worker threads in
    * {@link #writeCanvasTiledNdtiff}.</p>
    *
    * @return {@code short[]} (16-bit gray), {@code byte[]} (8-bit gray), or {@code byte[]}
    *         (RGB32 BGRA), each of length {@code outTileSize * outTileSize [* 4 for RGB]}.
    */
   private Object compositeOutputTile(TileBlender compositor,
                                      Map<Point, Point2D.Float> tileOrigins,
                                      int roiX, int roiY, int roiW, int roiH, int outTileSize,
                                      boolean isRgb, boolean is16bit, String chName,
                                      UnaryOperator<short[]> tileTransform16,
                                      UnaryOperator<byte[]> tileTransform8) {
      if (isRgb) {
         BufferedImage bimg =
               compositor.composite(roiX, roiY, roiW, roiH, 0, tileOrigins, pct -> { });
         int[] argb = new int[roiW * roiH];
         bimg.getRGB(0, 0, roiW, roiH, argb, 0, roiW);
         byte[] bgra = argbToBgra(argb);
         if (roiW < outTileSize || roiH < outTileSize) {
            byte[] padded = new byte[outTileSize * outTileSize * 4];
            for (int row = 0; row < roiH; row++) {
               System.arraycopy(bgra, row * roiW * 4,
                     padded, row * outTileSize * 4, roiW * 4);
            }
            return padded;
         }
         return bgra;
      } else if (is16bit) {
         short[] composited = compositor.composite16(roiX, roiY, roiW, roiH, 0,
               chName, tileOrigins, tileTransform16, pct -> { });
         if (roiW < outTileSize || roiH < outTileSize) {
            short[] padded = new short[outTileSize * outTileSize];
            for (int row = 0; row < roiH; row++) {
               System.arraycopy(composited, row * roiW, padded, row * outTileSize, roiW);
            }
            return padded;
         }
         return composited;
      } else {
         byte[] composited = compositor.composite8(roiX, roiY, roiW, roiH, 0,
               chName, tileOrigins, tileTransform8, pct -> { });
         if (roiW < outTileSize || roiH < outTileSize) {
            byte[] padded = new byte[outTileSize * outTileSize];
            for (int row = 0; row < roiH; row++) {
               System.arraycopy(composited, row * roiW, padded, row * outTileSize, roiW);
            }
            return padded;
         }
         return composited;
      }
   }

   /**
    * Builds the axes HashMap for one NDTiff image.
    * NDTiffStorage requires axes to be embedded in the tags under "Axes" AND passed
    * separately as a HashMap — this helper builds the HashMap; use buildNdtiffTags
    * to embed it in the tags object.
    * Only z/time axes that actually have more than one value are included; omitting
    * them for single-plane datasets prevents spurious scrollbars in TiledDataViewer.
    */
   private static HashMap<String, Object> buildNdtiffAxes(
         int row, int col, int z, int t, String chName, int numZ, int numT)
         throws JSONException {
      HashMap<String, Object> axes = new HashMap<>();
      axes.put("row", row);
      axes.put("column", col);
      if (numZ > 1) {
         axes.put("z", z);
      }
      if (numT > 1) {
         axes.put("time", t);
      }
      if (chName != null) {
         axes.put("channel", chName);
      }
      return axes;
   }

   /**
    * Builds the per-image tags JSONObject for NDTiff, embedding axes under "Axes".
    */
   private static JSONObject buildNdtiffTags(
         int imgW, int imgH, boolean isRgb, boolean is16bit,
         double pixelSizeUm, HashMap<String, Object> axes)
         throws JSONException {
      JSONObject tags = new JSONObject();
      tags.put("Width", imgW);
      tags.put("Height", imgH);
      tags.put("BytesPerPixel", isRgb ? 4 : (is16bit ? 2 : 1));
      // BitDepth is required by ImageStatsProcessor — without it, ComponentStats.bitDepth(null)
      // throws NPE inside the stats compute queue and histograms show NO DATA.
      tags.put("BitDepth", isRgb ? 8 : (is16bit ? 16 : 8));
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
      // Embed axes under "Axes" key — required by NDTiffStorage.putImageMultiRes.
      JSONObject axesJson = new JSONObject();
      for (Map.Entry<String, Object> e : axes.entrySet()) {
         axesJson.put(e.getKey(), e.getValue());
      }
      tags.put("Axes", axesJson);
      return tags;
   }

   /**
    * Persist per-channel display settings (color and contrast) into the NDTiff storage in
    * the NDViewer/NDTiff format: a JSON object keyed by channel name, each value an object
    * with an integer {@code "Color"} (packed RGB) and {@code "Min"}/{@code "Max"} contrast.
    * NDTiffStorage.finishedWriting() writes this to {@code display_settings.txt}; the
    * Explorer plugin reads {@code Color} per channel name when reopening the dataset.
    *
    * <p>Best-effort: any failure is logged and ignored so it can never block finalization.</p>
    */
   private void writeNdtiffDisplaySettings(NDTiffStorage storage, List<String> channelNames,
                                           DisplaySettings sourceSettings) {
      if (storage == null || sourceSettings == null || channelNames == null) {
         return;
      }
      try {
         JSONObject ds = new JSONObject();
         for (int i = 0; i < channelNames.size(); i++) {
            String name = channelNames.get(i);
            if (name == null) {
               continue; // RGB / unnamed channel
            }
            JSONObject chJson = new JSONObject();
            try {
               java.awt.Color color = sourceSettings.getChannelColor(i);
               if (color != null) {
                  chJson.put("Color", color.getRGB() & 0xFFFFFF);
               }
            } catch (Exception e) {
               // no color for this channel; skip
            }
            try {
               org.micromanager.display.ComponentDisplaySettings comp =
                     sourceSettings.getChannelSettings(i).getComponentSettings(0);
               chJson.put("Min", comp.getScalingMinimum());
               chJson.put("Max", comp.getScalingMaximum());
            } catch (Exception e) {
               // no contrast for this channel; skip
            }
            if (chJson.length() > 0) {
               ds.put(name, chJson);
            }
         }
         if (ds.length() > 0) {
            storage.setDisplaySettings(ds);
         }
      } catch (Exception e) {
         studio_.logs().logError(e, "Stitch: could not write NDTiff display settings");
      }
   }

   /**
    * Creates an NDTiff storage for the stitched output and writes its summary metadata.
    *
    * <p>The output uses a uniform grid of {@code outTileSize × outTileSize} tiles with
    * zero overlap. TiledDataViewer's built-in pyramid generation works correctly at all
    * zoom levels for a uniform zero-overlap grid.</p>
    */
   private NDTiffStorage buildNdtiffSummaryMetadata(
         String path, String name,
         int outTileSize,
         boolean isRgb, boolean is16bit,
         List<String> chNames)
         throws JSONException {
      JSONObject json = new JSONObject();
      json.put("Width", outTileSize);
      json.put("Height", outTileSize);
      // Zero overlap — tiles are placed on a uniform grid. NDTiffStorage computes
      // tileWidth_ = outTileSize - 0 = outTileSize, so the grid step = outTileSize exactly.
      json.put("GridPixelOverlapX", 0);
      json.put("GridPixelOverlapY", 0);
      // Probe pixel size from the source dataset's first image metadata.
      double pixelSizeUm = probePixelSizeUm(dataProvider_);
      if (pixelSizeUm > 0) {
         json.put("PixelSize_um", pixelSizeUm);
      }
      json.put("BitDepth", is16bit ? 16 : 8);
      json.put("PixelType", isRgb ? "RGB32" : (is16bit ? "GRAY16" : "GRAY8"));
      if (!chNames.isEmpty() && !isRgb) {
         JSONArray arr = new JSONArray();
         for (String ch : chNames) {
            arr.put(ch);
         }
         json.put("ChNames", arr);
         json.put("Channels", chNames.size());
      }
      return new NDTiffStorage(
            path, name, json, 0, 0, true, null, 30, null, true);
   }

   /**
    * Opens a finished NDTiff storage in a TiledDataViewer window.
    * Must be called on the EDT.
    */
   private void openInTiledDataViewer(
         NDTiffStorage storage,
         String path, String name,
         DisplaySettings displaySettings,
         boolean isRgb,
         int canvasW, int canvasH) {

      final Set<HashMap<String, Object>> allAxes = storage.getAxesSet();
      JSONObject summaryJson = storage.getSummaryMetadata();

      // Build SummaryMetadata with channel names from the NDTiff summary so the provider
      // registers all channels before the viewer is created (avoids NODATA histograms).
      SummaryMetadata.Builder smb = studio_.data().summaryMetadataBuilder();
      if (summaryJson != null && summaryJson.has("ChNames")) {
         try {
            JSONArray chArr = summaryJson.getJSONArray("ChNames");
            String[] chNames = new String[chArr.length()];
            for (int i = 0; i < chArr.length(); i++) {
               chNames[i] = chArr.getString(i);
            }
            smb.channelNames(chNames);
         } catch (JSONException ignore) { /* use no-channel metadata */ }
      }
      SummaryMetadata summaryMetadata = smb.build();

      NDTiffProviderAdapter adapter =
            new NDTiffProviderAdapter(storage);
      TiledDataViewerDataProviderAPI provider =
            TiledDataViewerFactory.createDataProvider(
                  studio_.data(), adapter, name, summaryMetadata);

      // Data source delegates display requests directly to the NDTiff storage.
      TiledDataViewerDataSource dataSource =
            new StitchNdtiffDataSource(storage, isRgb, canvasW, canvasH);

      double pixelSizeUm = summaryJson != null ? summaryJson.optDouble("PixelSize_um", 0) : 0;

      TiledDataViewerDataViewerAPI viewer =
            TiledDataViewerFactory.createDataViewer(
                  studio_, dataSource, null, provider,
                  summaryJson != null ? summaryJson : new JSONObject(),
                  pixelSizeUm, isRgb);

      // Set the window title bar text.
      viewer.getTiledDataViewer().setWindowTitle(name);

      // Initialize the viewer for a loaded (already-written) dataset.
      // This reads getImageKeys() from the data source, registers all channels in the
      // display model (addChannel, scrollbars, contrast panels), and sets up scrollbar
      // extents — replacing the manual newImageArrived loop.
      // Must be called before setDisplaySettings so channels exist when pushRenderSettings
      // calls setActive.
      // Pass empty JSON so DisplaySettings uses its default constructor (preferences-based).
      // The NDTiff summary JSON does not have the "All channel settings" structure that
      // DisplaySettings expects, and passing it causes JSONExceptions for every setting read.
      viewer.getTiledDataViewer().initializeViewerToLoaded(new JSONObject());

      // Seed the initial view size to the full canvas. The data source reports null bounds
      // (to allow free zoom-out), so the viewer cannot derive the initial source-data size
      // from getBounds(); set it explicitly so the first view frames the whole stitch.
      if (canvasW > 0 && canvasH > 0) {
         viewer.getTiledDataViewer().setFullResSourceDataSize(canvasW, canvasH);
      }

      // Apply source display settings after channels have been registered.
      if (displaySettings != null) {
         viewer.setDisplaySettings(displaySettings);
      }

      // Seed histogram computation: pick one representative tile per channel, register
      // each with the provider (creates Inspector panels), then submit them all to the
      // viewer via newTileArrived to trigger ImageStatsRequest computation.
      List<Image> seedImages = new ArrayList<>();
      List<HashMap<String, Object>> seedAxesList = new ArrayList<>();
      Set<Object> seenChannels = new LinkedHashSet<>();
      for (HashMap<String, Object> axes : allAxes) {
         Object ch = axes.get("channel");
         if (!seenChannels.add(ch == null ? "" : ch)) {
            continue; // already seeded this channel
         }
         HashMap<String, Object> channelAxes = new HashMap<>();
         if (ch != null) {
            channelAxes.put("channel", ch);
         }
         try {
            Image img = provider.getDownsampledImageByAxes(axes);
            if (img != null) {
               provider.newImageArrived(img, channelAxes);
               seedImages.add(img);
               seedAxesList.add(channelAxes);
            }
         } catch (Exception e) {
            studio_.logs().logMessage(
                  "Stitch: exception fetching seed image: " + e.getMessage());
         }
      }
      if (!seedImages.isEmpty()) {
         viewer.newTileArrived(seedImages, seedAxesList);
      }

      // Save the view state (zoom/pan) to view_state.json when the viewer window closes, so
      // reopening (e.g. in Explorer) restores the zoom the user left it at. Use a WindowListener
      // rather than the MM DataViewerWillCloseEvent: closing the window goes through the
      // TiledDataViewer's internal GUI close() path, which tears the viewer down but does NOT
      // post DataViewerWillCloseEvent, so an event subscriber never fires.
      // Write to the storage's ACTUAL on-disk directory (the unique subdirectory NDTiff
      // created), not the parent `path` -- that subdir is where Explorer reads view_state.json.
      final String stateDir = storage.getDiskLocation() != null
            ? storage.getDiskLocation() : path;
      if (stateDir != null) {
         final TiledDataViewerAPI tdv = viewer.getTiledDataViewer();
         // Defer so the window exists and is realized before we look it up.
         SwingUtilities.invokeLater(() -> {
            try {
               java.awt.Component canvas = tdv.getCanvasJPanel();
               java.awt.Window window = canvas != null
                     ? SwingUtilities.getWindowAncestor(canvas) : null;
               if (window != null) {
                  window.addWindowListener(new java.awt.event.WindowAdapter() {
                     private boolean saved = false;

                     @Override
                     public void windowClosing(java.awt.event.WindowEvent e) {
                        save();
                     }

                     @Override
                     public void windowClosed(java.awt.event.WindowEvent e) {
                        save();
                     }

                     private void save() {
                        if (saved) {
                           return; // windowClosing and windowClosed can both fire; write once
                        }
                        saved = true;
                        writeViewState(tdv, stateDir);
                     }
                  });
               } else {
                  studio_.logs().logMessage(
                        "Stitch: could not attach view-state save listener (no window yet)");
               }
            } catch (Exception ex) {
               studio_.logs().logError(ex, "Stitch: failed to attach view-state save listener");
            }
         });
      }
   }

   /**
    * Write the current zoom/pan of the viewer to {@code view_state.json} in the dataset
    * directory, in the format the Explorer plugin reads on reopen
    * ({@code magnification}, {@code xView}, {@code yView}). Best-effort.
    */
   private void writeViewState(TiledDataViewerAPI viewer, String dir) {
      try {
         java.awt.geom.Point2D.Double offset = viewer.getViewOffset();
         java.awt.geom.Point2D.Double displaySize = viewer.getDisplayImageSize();
         java.awt.geom.Point2D.Double sourceSize = viewer.getFullResSourceDataSize();
         JSONObject json = new JSONObject();
         json.put("xView", offset.x);
         json.put("yView", offset.y);
         if (displaySize.x > 0 && sourceSize.x > 0) {
            json.put("magnification", displaySize.x / sourceSize.x);
         }
         java.io.File f = new java.io.File(dir, "view_state.json");
         java.nio.file.Files.write(f.toPath(),
               json.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      } catch (Exception e) {
         studio_.logs().logError(e, "Stitch: could not write view_state.json");
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
                                       IntConsumer progress) {
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
      Set<HashMap<String, Object>> allAxes = adapter.getAxesSet();
      int total = allAxes.size();

      int probedTileW = 0;
      int probedTileH = 0;
      JSONObject summaryMD = adapter.getSummaryMetadata();
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
            if (!ChannelUtils.channelValuesMatch(
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
               + " len=" + Array.getLength(tile.pix)
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
    * Convert an ARGB {@code int[]} (as returned by {@link BufferedImage#getRGB})
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
            // Fallback: derive pixel size from the affine transform diagonal.
            AffineTransform af = meta.getPixelSizeAffine();
            if (af != null) {
               double colX = Math.sqrt(af.getScaleX() * af.getScaleX()
                     + af.getShearY() * af.getShearY());
               double colY = Math.sqrt(af.getShearX() * af.getShearX()
                     + af.getScaleY() * af.getScaleY());
               double scale = (colX + colY) / 2.0;
               if (scale > 0) {
                  return scale;
               }
            }
         }
      } catch (Exception e) {
         // ignore
      }
      return 0.0;
   }

   /**
    * Bundles the resolved orientation: the per-tile pixel operator (threaded through
    * the pixel paths as the {@code correction} int[]) and the affine-driven placement
    * model for the adapter. Either field may be null when not applicable.
    */
   private static final class ResolvedOrientation {
      final int[] pixelOp;                                   // {rotation, mirror} or null
      final StitchDataProviderAdapter.OrientationModel model; // placement model or null

      ResolvedOrientation(int[] pixelOp,
                          StitchDataProviderAdapter.OrientationModel model) {
         this.pixelOp = pixelOp;
         this.model = model;
      }
   }

   /**
    * Resolves the orientation correction for the "Correct camera orientation" path.
    *
    * <p>Derives a single orientation operator {@code O} from the pixelSizeAffine, folds
    * in any Image Flipper transform already applied in-acquisition (so it is not applied
    * twice), and builds the stage-to-canvas placement matrix {@code M}. The same affine is
    * the sole authority for both pixel orientation and tile placement, so they cannot
    * diverge.</p>
    *
    * @return resolved operators, or null if no usable affine is present
    */
   private ResolvedOrientation resolveOrientation(DataProvider dataProvider) {
      AffineTransform affine = probeAffineTransform(dataProvider);
      int[] o = ImageTransformUtils.correctionFromAffine(affine);
      if (o == null) {
         return null;
      }

      // placementOp drives M; pixelOp is what we actually apply to the stored pixels.
      int[] placementOp = o;
      int[] pixelOp = o;

      // Fold in the Image Flipper: when the Flipper already transformed the pixels in
      // acquisition (flipperOp), the per-tile correction must be reduced so it is not
      // applied twice: pixelOp = O after flipperOp^-1. Placement still uses the full O,
      // because the stored stage coordinates are raw (pre-flipper).
      int[] flipperOp = probeImageFlipper(dataProvider);
      if (flipperOp != null) {
         int[] flipInv = ImageTransformUtils.invertCorrection(flipperOp[0], flipperOp[1]);
         pixelOp = ImageTransformUtils.composeCorrection(
               o[0], o[1], flipInv[0], flipInv[1]);
      }

      double[] m = ImageTransformUtils.stageToCanvasMatrix(
            affine, placementOp[0], placementOp[1] != 0);
      StitchDataProviderAdapter.OrientationModel model = null;
      if (m != null) {
         double[] ref = probeReferenceStageXY(dataProvider);
         model = new StitchDataProviderAdapter.OrientationModel(m, ref[0], ref[1]);
      }

      // A pixelOp that collapses to identity means "no per-tile pixel transform".
      int[] effectivePixelOp =
            (pixelOp[0] == 0 && pixelOp[1] == 0) ? null : pixelOp;
      return new ResolvedOrientation(effectivePixelOp, model);
   }

   /**
    * Reads the Image Flipper operator from the first image's user data, or null if the
    * Flipper was not in the pipeline (or its transform was the identity). The tag values
    * are written by {@code FlipperProcessor} (mirror, then rotate clockwise).
    */
   private static int[] probeImageFlipper(DataProvider dataProvider) {
      try {
         for (Coords coords : dataProvider.getUnorderedImageCoords()) {
            Image img = dataProvider.getImage(coords);
            if (img == null || img.getMetadata() == null) {
               continue;
            }
            org.micromanager.PropertyMap userData = img.getMetadata().getUserData();
            if (userData == null) {
               return null;
            }
            if (userData.containsInteger("ImageFlipper-Rotation")
                  && userData.containsString("ImageFlipper-Mirror")) {
               int rot = userData.getInteger("ImageFlipper-Rotation", 0);
               String mir = userData.getString("ImageFlipper-Mirror", "Off");
               return ImageTransformUtils.flipperFromUserData(rot, mir);
            }
            return null;
         }
      } catch (Exception e) {
         // Ignore - treat as no Flipper.
      }
      return null;
   }

   /** Returns a reference stage (X,Y) for placement deltas, or {0,0} if unavailable. */
   private static double[] probeReferenceStageXY(DataProvider dataProvider) {
      try {
         for (Coords coords : dataProvider.getUnorderedImageCoords()) {
            Image img = dataProvider.getImage(coords);
            if (img == null || img.getMetadata() == null) {
               continue;
            }
            Double x = img.getMetadata().getXPositionUm();
            Double y = img.getMetadata().getYPositionUm();
            if (x != null && y != null) {
               return new double[]{x, y};
            }
         }
      } catch (Exception e) {
         // Ignore - fall through to origin.
      }
      return new double[]{0.0, 0.0};
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
    * TiledDataViewerDataSource for the canvas-tiled NDTiff export path.
    * Tiles are laid out on a uniform zero-overlap grid; NDTiffStorage handles
    * all compositing and pyramid generation internally.
    */
   private static class StitchNdtiffDataSource
         implements TiledDataViewerDataSource {

      private final NDTiffStorage storage_;
      private final boolean rgb_;
      private volatile Set<HashMap<String, Object>> imageKeysCache_ = null;
      private final int canvasW_;
      private final int canvasH_;

      StitchNdtiffDataSource(
            NDTiffStorage storage, boolean rgb,
            int canvasW, int canvasH) {
         storage_ = storage;
         rgb_ = rgb;
         canvasW_ = canvasW;
         canvasH_ = canvasH;
      }

      @Override
      public boolean isFinished() {
         return storage_.isFinished();
      }

      @Override
      public int[] getBounds() {
         // Return null (unbounded) so the viewer does not clamp zoom-out to "the whole
         // canvas fits the window" -- this matches the Explorer plugin's free zoom-out.
         // The initial view size is seeded explicitly via setFullResSourceDataSize() in
         // openInTiledDataViewer(), since with null bounds the viewer can't derive it here.
         return null;
      }

      @Override
      public mmcorej.TaggedImage getImageForDisplay(
            HashMap<String, Object> axes,
            int resolutionindex,
            double xOffset, double yOffset,
            int imageWidth, int imageHeight) {
         // Fill any missing non-spatial axes from the first stored entry so that
         // single-plane datasets (no z/channel scrollbar) still work.
         // NDTiffStorage handles grid-based tile placement internally.
         HashMap<String, Object> fullAxes = new HashMap<>(axes);
         Set<HashMap<String, Object>> stored = storage_.getAxesSet();
         if (!stored.isEmpty()) {
            HashMap<String, Object> sample = stored.iterator().next();
            for (Map.Entry<String, Object> e : sample.entrySet()) {
               String key = e.getKey();
               if (!key.equals(NDTiffStorage.ROW_AXIS)
                     && !key.equals(NDTiffStorage.COL_AXIS)
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
         Set<HashMap<String, Object>> result = new HashSet<>();
         for (HashMap<String, Object> axes : storage_.getAxesSet()) {
            HashMap<String, Object> copy = new HashMap<>(axes);
            copy.remove(NDTiffStorage.ROW_AXIS);
            copy.remove(NDTiffStorage.COL_AXIS);
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
         try {
            storage_.close();
         } catch (Exception ignore) {
            // In-flight reads may fail during async teardown; file handles are released regardless.
         }
      }

      @Override
      public int getImageBitDepth(HashMap<String, Object> axesPositions) {
         JSONObject meta = storage_.getSummaryMetadata();
         return meta != null ? meta.optInt("BitDepth", 16) : 16;
      }
   }
}
