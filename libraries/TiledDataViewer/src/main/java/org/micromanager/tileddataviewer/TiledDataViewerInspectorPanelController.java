package org.micromanager.tileddataviewer;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Window;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import mmcorej.org.json.JSONObject;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.micromanager.display.inspector.AbstractInspectorPanelController;
import org.micromanager.exporttiles.ExportTiles;

public final class TiledDataViewerInspectorPanelController
      extends AbstractInspectorPanelController {

   private final Studio studio_;
   private final JPanel panel_;
   private final JLabel statusLabel_;
   private final JButton exportButton_;
   private TiledDataViewerDataViewerAPI viewer_;
   private static boolean expanded_ = true;

   // Last confirmed ROI in full-resolution pixels [x, y, w, h]; null when none.
   private int[] lastRoi_ = null;

   public TiledDataViewerInspectorPanelController(Studio studio) {
      studio_ = studio;
      statusLabel_ = new JLabel(" ");
      exportButton_ = new JButton("Export...");
      panel_ = buildPanel();
   }

   private JPanel buildPanel() {
      final JPanel p = new JPanel(new MigLayout("insets 4", "[]4[]4[]", "[]2[]"));
      JButton center = new JButton("Center");
      JButton noZoom = new JButton("No Zoom");
      center.addActionListener(e -> onCenter());
      noZoom.addActionListener(e -> onNoZoom());
      exportButton_.addActionListener(e -> onExportClicked());
      p.add(center);
      p.add(noZoom, "wrap");
      p.add(exportButton_);
      p.add(statusLabel_, "wrap");
      return p;
   }

   /** Returns the center of the dataset in full-res pixel coordinates, or null if unknown. */
   private Point2D.Double getDataCenter() {
      TiledDataViewerDataProviderAPI dp = (TiledDataViewerDataProviderAPI) viewer_.getDataProvider();
      int[] b = dp.getStorage().getImageBounds();
      if (b == null) {
         return null;
      }
      return new Point2D.Double((b[0] + b[2]) / 2.0, (b[1] + b[3]) / 2.0);
   }

   /** Pan so the dataset center is in the middle of the canvas; keep current zoom. */
   private void onCenter() {
      if (viewer_ == null) {
         return;
      }
      TiledDataViewerAPI v = viewer_.getNDViewer();
      Point2D.Double dataCenter = getDataCenter();
      if (dataCenter == null) {
         return;
      }
      Point2D.Double source = v.getFullResSourceDataSize();
      v.setViewOffset(dataCenter.x - source.x / 2.0, dataCenter.y - source.y / 2.0);
      v.update();
   }

   /** Set zoom to 1:1 (one full-res pixel per screen pixel), centered on the dataset. */
   private void onNoZoom() {
      if (viewer_ == null) {
         return;
      }
      TiledDataViewerAPI v = viewer_.getNDViewer();
      Point2D.Double displaySize = v.getDisplayImageSize();
      Point2D.Double dataCenter = getDataCenter();
      double centerX = dataCenter != null ? dataCenter.x
               : v.getViewOffset().x + v.getFullResSourceDataSize().x / 2.0;
      double centerY = dataCenter != null ? dataCenter.y
               : v.getViewOffset().y + v.getFullResSourceDataSize().y / 2.0;
      v.setViewOffset(centerX - displaySize.x / 2.0, centerY - displaySize.y / 2.0);
      v.setFullResSourceDataSize(displaySize.x, displaySize.y);
      v.update();
   }

   private void onExportClicked() {
      if (viewer_ == null) {
         return;
      }
      if (lastRoi_ != null) {
         showExportDialog(lastRoi_);
      } else {
         startExportMode();
      }
   }

   private void startExportMode() {
      TiledDataViewerAPI v = viewer_.getNDViewer();
      v.getCanvasJPanel().setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      setStatus("Draw a selection on the image");

      ExportSelectionOverlay exportOverlay = new ExportSelectionOverlay(v);
      viewer_.setOverlayerPlugin(exportOverlay);

      ExportMouseListener[] el = new ExportMouseListener[1];
      el[0] = new ExportMouseListener(v,
              () -> {
                 v.getCanvasJPanel().setCursor(Cursor.getDefaultCursor());
                 if (lastRoi_ != null) {
                    // Freeze the drawn rectangle in the overlay plugin so it survives
                    // repaints without needing mouse movement. The exportListener stays
                    // installed — its next mousePressed will call onDismiss.
                    exportOverlay.freezeRoi(el[0].mouseDragStartPoint_,
                            el[0].currentMouseLocation_);
                    el[0].setOnDismiss(() -> {
                       lastRoi_ = null;
                       viewer_.setOverlayerPlugin(null);
                       v.resetCanvasMouseListener();
                       v.update();
                       setStatus(null);
                    });
                    setStatus("Click to dismiss selection");
                 } else {
                    viewer_.setOverlayerPlugin(null);
                    v.resetCanvasMouseListener();
                    setStatus(null);
                 }
              },
              this::onRoiSelected);
      exportOverlay.setExportMouseListener(el[0]);
      v.setCustomCanvasMouseListener(el[0]);
   }

   private void onRoiSelected(Point dragStart, Point dragEnd) {
      TiledDataViewerAPI v = viewer_.getNDViewer();
      Point2D.Double viewOffset = v.getViewOffset();
      double mag = v.getMagnification();
      int x1 = (int) (viewOffset.x + Math.min(dragStart.x, dragEnd.x) / mag);
      int y1 = (int) (viewOffset.y + Math.min(dragStart.y, dragEnd.y) / mag);
      int x2 = (int) (viewOffset.x + Math.max(dragStart.x, dragEnd.x) / mag);
      int y2 = (int) (viewOffset.y + Math.max(dragStart.y, dragEnd.y) / mag);
      int roiW = Math.max(1, x2 - x1);
      int roiH = Math.max(1, y2 - y1);
      lastRoi_ = new int[]{x1, y1, roiW, roiH};
      showExportDialog(lastRoi_);
   }

   private void showExportDialog(int[] roi) {
      TiledDataViewerAPI v = viewer_.getNDViewer();
      TiledDataViewerDataProviderAPI dp = (TiledDataViewerDataProviderAPI) viewer_.getDataProvider();
      List<String> chNames = dp.getSummaryMetadata().getChannelNameList();
      if (chNames == null || chNames.isEmpty()) {
         chNames = Collections.singletonList(null);
      }
      Window owner = SwingUtilities.getWindowAncestor(panel_);
      ExportTiles.showDialogAndExport(owner, dp.getStorage(),
            buildDisplaySettingsJSON(), new HashMap<String, Object>(), chNames,
            roi[0], roi[1], roi[2], roi[3]);
   }

   /**
    * Build the display settings JSON that ExportTiles expects.
    * Format: { channelName: { "Min": x, "Max": y, "Color": rgb, ... } }.
    * getDisplaySettingsJSON() is always current because setRenderSettings() syncs it.
    */
   private JSONObject buildDisplaySettingsJSON() {
      return viewer_.getNDViewer().getDisplaySettingsJSON();
   }

   private void setStatus(String text) {
      statusLabel_.setText(text == null ? " " : text);
   }

   @Override
   public void attachDataViewer(DataViewer viewer) {
      viewer_ = (TiledDataViewerDataViewerAPI) viewer;
      lastRoi_ = null;
   }

   @Override
   public void detachDataViewer() {
      viewer_ = null;
      lastRoi_ = null;
      setStatus(null);
   }

   @Override
   public String getTitle() {
      return "NDViewer2 Controls";
   }

   @Override
   public JPanel getPanel() {
      return panel_;
   }

   @Override
   public boolean isVerticallyResizableByUser() {
      return false;
   }

   @Override
   public boolean initiallyExpand() {
      return expanded_;
   }

   @Override
   public void setExpanded(boolean status) {
      expanded_ = status;
   }
}
