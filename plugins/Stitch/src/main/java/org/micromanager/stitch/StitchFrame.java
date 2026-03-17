package org.micromanager.stitch;

import com.google.common.eventbus.Subscribe;
import java.awt.Dialog;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.exporttiles.TileAligner;
import org.micromanager.exporttiles.TileBlender;
import org.micromanager.imageprocessing.ImageTransformUtils;
import org.micromanager.internal.utils.FileDialogs;

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

   private final Studio studio_;
   private final DisplayWindow displayWindow_;
   private final DataProvider dataProvider_;

   private JComboBox<String> alignChannelCombo_;
   private JComboBox<Integer> alignZCombo_;
   private JCheckBox blendCheck_;
   private JCheckBox alignCheck_;
   private JCheckBox correctOrientationCheck_;
   private JComboBox<String> saveFormatCombo_;
   private JTextField outputPathField_;
   private JButton browseButton_;
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

      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      setLayout(new MigLayout("fillx, insets 8", "[right][grow,fill]", "[]4[]"));

      buildUI();

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
      int numZ = dataProvider_.getNextIndex(Coords.Z_SLICE);

      // Alignment channel
      add(new JLabel("Align channel:"));
      alignChannelCombo_ = new JComboBox<>();
      for (String name : channelNames) {
         alignChannelCombo_.addItem(name);
      }
      alignChannelCombo_.setEnabled(channelNames.size() > 1);
      add(alignChannelCombo_, "wrap");

      // Alignment z-slice
      add(new JLabel("Align z-slice:"));
      alignZCombo_ = new JComboBox<>();
      for (int z = 0; z < Math.max(numZ, 1); z++) {
         alignZCombo_.addItem(z);
      }
      alignZCombo_.setEnabled(numZ > 1);
      add(alignZCombo_, "wrap");

      // Blend
      add(new JLabel(""));
      blendCheck_ = new JCheckBox("Blend tiles (feathered overlap)");
      blendCheck_.setSelected(false);
      add(blendCheck_, "wrap");

      // Align
      add(new JLabel(""));
      alignCheck_ = new JCheckBox("Align tiles (phase correlation)");
      alignCheck_.setSelected(false);
      add(alignCheck_, "wrap");

      // Correct camera orientation
      add(new JLabel(""));
      correctOrientationCheck_ = new JCheckBox("Correct camera orientation (affine)");
      correctOrientationCheck_.setSelected(false);
      add(correctOrientationCheck_, "wrap");

      // Save format
      add(new JLabel("Save as:"));
      saveFormatCombo_ = new JComboBox<>(new String[]{SAVE_RAM, SAVE_STACK});
      saveFormatCombo_.addActionListener((ActionEvent e) -> updatePathControls());
      add(saveFormatCombo_, "wrap");

      // Output path (enabled only for Image Stack File)
      add(new JLabel("Output folder:"));
      outputPathField_ = new JTextField(30);
      outputPathField_.setEnabled(false);
      add(outputPathField_, "growx");

      browseButton_ = new JButton("...");
      browseButton_.setEnabled(false);
      browseButton_.addActionListener((ActionEvent e) -> chooseSavePath());
      add(browseButton_, "wrap");

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
      outputPathField_.setEnabled(needsPath);
      browseButton_.setEnabled(needsPath);
   }

   private void chooseSavePath() {
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Select output folder");
      chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      File suggested = new File(FileDialogs.getSuggestedFile(FileDialogs.MM_DATA_SET));
      if (suggested.getParentFile() != null) {
         chooser.setCurrentDirectory(suggested.getParentFile());
      }
      if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
         File file = chooser.getSelectedFile();
         outputPathField_.setText(file.getAbsolutePath());
         FileDialogs.storePath(FileDialogs.MM_DATA_SET, file);
      }
   }

   // -------------------------------------------------------------------------
   // Export logic
   // -------------------------------------------------------------------------

   private void onExport() {
      boolean saveToStack = SAVE_STACK.equals(saveFormatCombo_.getSelectedItem());
      if (saveToStack && outputPathField_.getText().trim().isEmpty()) {
         studio_.logs().showError("Please select an output folder.", this);
         return;
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

      String selectedChannel = alignChannelCombo_.getItemCount() > 0
            ? (String) alignChannelCombo_.getSelectedItem()
            : null;
      int alignZ = alignZCombo_.getItemCount() > 0
            ? (Integer) alignZCombo_.getSelectedItem()
            : 0;
      boolean blend = blendCheck_.isSelected();
      boolean align = alignCheck_.isSelected();
      boolean correctOrientation = correctOrientationCheck_.isSelected();
      String outputPath = outputPathField_.getText().trim();

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
      List<String> allChannelNames = getChannelNames(summary);
      List<String> channelNamesForExport = allChannelNames.isEmpty()
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

      new Thread(() -> {
         try {
            buildDatastore(adapter, baseAxes, finalAlignAxes, chNames, canvasW, canvasH,
                  doBlend, doAlign, finalCorrection, toStack, destPath, datasetName, exportAlignZ,
                  bar, statusLabel, progressDialog);
         } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> {
               progressDialog.dispose();
               JOptionPane.showMessageDialog(null,
                     "Export failed: " + ex.getMessage(),
                     "Export Error", JOptionPane.ERROR_MESSAGE);
            });
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
                               boolean toStack, String destPath,
                               String datasetName, int alignZ,
                               JProgressBar bar, JLabel statusLabel,
                               JDialog progressDialog) throws Exception {

      // Step 1: create the output Datastore
      final Datastore ds;
      if (toStack) {
         new File(destPath).mkdirs();
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
         // Step 2: set SummaryMetadata
         SummaryMetadata srcSummary = dataProvider_.getSummaryMetadata();
         SummaryMetadata.Builder smBuilder = studio_.data().summaryMetadataBuilder()
               .imageWidth(outCanvasW)
               .imageHeight(outCanvasH);
         if (srcSummary != null) {
            // Copy channel names
            List<String> chanNames = getChannelNames(srcSummary);
            if (!chanNames.isEmpty()) {
               smBuilder = smBuilder.channelNames(chanNames);
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

         // Step 4 & 5: compute aligned origins (if needed) and composite image
         SwingUtilities.invokeLater(() -> statusLabel.setText("Computing…"));

         Map<Point, Point2D.Float> origins = null;
         if (doAlign) {
            SwingUtilities.invokeLater(() -> statusLabel.setText("Aligning…"));
            origins = new TileAligner(adapter, alignAxes, chNames,
                  adapter.getSummaryMetadata())
                  .computeAlignedOrigins(0,
                        pct -> SwingUtilities.invokeLater(() -> {
                           bar.setValue(pct / 2);
                           statusLabel.setText("Aligning… " + pct + "%");
                        }));
            SwingUtilities.invokeLater(() -> bar.setValue(50));
         }

         final Map<Point, Point2D.Float> finalOrigins = origins;
         int numCh = chNames.size();

         if (doBlend || doAlign) {
            // Blend path: feathered blending per channel into 16-bit grayscale canvases.
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

            java.util.function.UnaryOperator<short[]> tileTransform = null;
            if (correction != null && (needsPerTileMirror || perTileRotation != 0)
                  && tileW > 0 && tileH > 0) {
               tileTransform = (pix) -> {
                  Object[] r = ImageTransformUtils.transformPixels(
                        pix, tileW, tileH, needsPerTileMirror, perTileRotation);
                  return (short[]) r[0];
               };
            }

            TileBlender blender = new TileBlender(adapter, new mmcorej.org.json.JSONObject(),
                  baseAxes, chNames, adapter.getSummaryMetadata());
            for (int c = 0; c < numCh; c++) {
               final int chIdx = c;
               final String chName = chNames.get(c);
               SwingUtilities.invokeLater(() -> statusLabel.setText(
                     "Compositing " + chName + "…"));
               final java.util.function.UnaryOperator<short[]> finalTileTransform = tileTransform;
               short[] pixels = blender.composite16(0, 0, canvasW, canvasH, 0,
                     chName,
                     finalOrigins,
                     finalTileTransform,
                     pct -> SwingUtilities.invokeLater(() -> {
                        int overall = doAlign
                              ? 50 + (chIdx * 100 + pct) / (numCh * 2)
                              : (chIdx * 100 + pct) / numCh;
                        bar.setValue(overall);
                     }));

               // Apply 90°/270° post-canvas rotation if needed
               int chCanvasW = canvasW;
               int chCanvasH = canvasH;
               if (postCanvasRotation != 0) {
                  Object[] transformed = ImageTransformUtils.transformPixels(
                        pixels, canvasW, canvasH, false, postCanvasRotation);
                  pixels = (short[]) transformed[0];
                  chCanvasW = (Integer) transformed[1];
                  chCanvasH = (Integer) transformed[2];
               }

               Coords coords = studio_.data().coordsBuilder()
                     .channel(c).z(alignZ).build();
               Metadata meta = studio_.data().metadataBuilder().build();
               Image mmImg = studio_.data().createImage(pixels, chCanvasW, chCanvasH, 2, 1,
                     coords, meta);
               ds.putImage(mmImg);
            }

         } else {
            // Simple stitch path: copy tiles per channel onto a canvas
            for (int c = 0; c < numCh; c++) {
               final String chName = chNames.get(c);
               SwingUtilities.invokeLater(() -> statusLabel.setText(
                     "Stitching " + chName + "…"));
               Object[] result = stitchTiles(adapter, baseAxes, chName, canvasW, canvasH,
                     correction, bar, statusLabel);
               Object canvas = result[0];
               int bytesPerPixel = (Integer) result[1];
               int stitchedW = (Integer) result[2];
               int stitchedH = (Integer) result[3];

               Coords coords = studio_.data().coordsBuilder()
                     .channel(c).z(alignZ).build();
               Metadata meta = studio_.data().metadataBuilder().build();
               Image mmImg = studio_.data().createImage(canvas, stitchedW, stitchedH,
                     bytesPerPixel, 1, coords, meta);
               ds.putImage(mmImg);
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
               studio_.displays().createDisplay(ds);
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
                                       JProgressBar bar, JLabel statusLabel) {
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
         int row = (Integer) rowObj;
         int col = (Integer) colObj;

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

         // Copy tile pixels into canvas, accounting for overlap between tiles
         int destX = col * stepX;
         int destY = row * stepY;
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
         SwingUtilities.invokeLater(() -> {
            bar.setValue(pct);
            statusLabel.setText("Stitching… " + pct + "%");
         });
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
