package org.micromanager.exporttiles;

import java.awt.Window;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import mmcorej.org.json.JSONObject;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;

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
           MultiresNDTiffAPI storage,
           JSONObject displaySettings,
           HashMap<String, Object> baseAxes,
           List<String> channelNames,
           int roiX, int roiY, int roiW, int roiH) {

      ExportDialog dialog = new ExportDialog(owner, storage.getNumResLevels(), roiW, roiH);
      ExportDialog.ExportOptions opts = dialog.showAndGet();
      if (opts == null) {
         return;
      }

      List<String> channels = channelNames.isEmpty()
              ? Collections.singletonList(null) : channelNames;

      new Thread(() -> {
         try {
            new ExportImageExporter(storage, displaySettings)
                    .export(baseAxes, channels, roiX, roiY, roiW, roiH,
                            opts.resolutionLevel, opts.format, opts.filePath, opts.blend);
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(null,
                            "Export complete:\n" + opts.filePath));
         } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(null,
                            "Export failed: " + ex.getMessage(),
                            "Export Error", JOptionPane.ERROR_MESSAGE));
         }
      }, "ExportTiles-Export").start();
   }
}
