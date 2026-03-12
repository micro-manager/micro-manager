package org.micromanager.exporttiles;

import java.awt.Dialog;
import java.awt.Window;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import mmcorej.org.json.JSONObject;
import net.miginfocom.swing.MigLayout;
import org.micromanager.ndviewer2.NDViewer2StorageAPI;

/**
 * Public facade for the ExportTiles library.
 *
 * <p>Callers supply the storage, display settings, axes, channel names, and an
 * ROI in full-resolution pixels. This class shows the export dialog and, if
 * the user confirms, spawns a background thread to write the composited image.
 */
public class ExportTiles {

   /**
    * Shows the export dialog and, if the user clicks OK, spawns a background
    * thread to composite and write the selected region to disk.
    *
    * @param owner           Window ancestor (for dialog parenting).
    * @param storage         Multiresolution tiff storage to read tiles from.
    * @param displaySettings Per-channel color/contrast JSON (NDViewer format).
    * @param baseAxes        Non-channel axes at the time of export (e.g. Z position).
    * @param channelNames    Channel names; pass a list containing null for no-channel data.
    * @param roiX            Left edge of ROI in full-resolution pixels.
    * @param roiY            Top edge of ROI in full-resolution pixels.
    * @param roiW            Width of ROI in full-resolution pixels.
    * @param roiH            Height of ROI in full-resolution pixels.
    */
   public static void showDialogAndExport(
           Window owner,
           NDViewer2StorageAPI storage,
           JSONObject displaySettings,
           HashMap<String, Object> baseAxes,
           List<String> channelNames,
           int roiX, int roiY, int roiW, int roiH) {

      ExportDialog dialog = new ExportDialog(owner, storage.getNumResLevels(), roiW, roiH);
      ExportDialog.ExportOptions opts = dialog.showAndGet();
      if (opts == null) {
         return;
      }

      final List<String> channels = channelNames.isEmpty()
              ? Collections.singletonList(null) : channelNames;

      // Build a non-modal progress dialog
      JDialog progressDialog = new JDialog(owner, "Exporting…", Dialog.ModalityType.MODELESS);
      progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      JProgressBar bar = new JProgressBar(0, 100);
      bar.setStringPainted(true);
      JLabel label = new JLabel("Preparing…");
      progressDialog.getContentPane().setLayout(new MigLayout("insets 12, gap 8", "[grow]"));
      progressDialog.getContentPane().add(label, "wrap");
      progressDialog.getContentPane().add(bar, "growx, wrap");
      progressDialog.pack();
      progressDialog.setLocationRelativeTo(owner);
      progressDialog.setVisible(true);

      new Thread(() -> {
         try {
            new ExportImageExporter(storage, displaySettings)
                    .export(baseAxes, channels, roiX, roiY, roiW, roiH,
                            opts.resolutionLevel, opts.format, opts.filePath,
                            opts.blend, opts.align,
                            pct -> SwingUtilities.invokeLater(() -> {
                               bar.setValue(pct);
                               if (opts.align && pct < 50) {
                                  label.setText("Aligning tiles… " + (pct * 2) + "%");
                               } else {
                                  int blendPct = opts.align ? (pct - 50) * 2 : pct;
                                  label.setText("Compositing… " + blendPct + "%");
                               }
                            }));
            SwingUtilities.invokeLater(() -> {
               progressDialog.dispose();
               JOptionPane.showMessageDialog(owner,
                       "Export complete:\n" + opts.filePath);
            });
         } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> {
               progressDialog.dispose();
               JOptionPane.showMessageDialog(owner,
                       "Export failed: " + ex.getMessage(),
                       "Export Error", JOptionPane.ERROR_MESSAGE);
            });
         }
      }, "ExportTiles-Export").start();
   }
}
