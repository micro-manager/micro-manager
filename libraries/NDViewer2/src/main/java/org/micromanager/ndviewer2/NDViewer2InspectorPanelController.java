package org.micromanager.ndviewer2;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
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
import org.micromanager.ndviewer2.overlay.Overlay;

public final class NDViewer2InspectorPanelController
      extends AbstractInspectorPanelController {

   private final Studio studio_;
   private final JPanel panel_;
   private final JLabel statusLabel_;
   private final JButton exportButton_;
   private NDViewer2DataViewerAPI viewer_;
   private static boolean expanded_ = true;

   // Last confirmed ROI in full-resolution pixels [x, y, w, h]; null when none.
   private int[] lastRoi_ = null;

   public NDViewer2InspectorPanelController(Studio studio) {
      studio_ = studio;
      statusLabel_ = new JLabel(" ");
      exportButton_ = new JButton("Export...");
      panel_ = buildPanel();
   }

   private JPanel buildPanel() {
      JPanel p = new JPanel(new MigLayout("insets 4", "[]4[]4[]", "[]2[]"));
      JButton oneToOne = new JButton("1:1");
      JButton fit = new JButton("Fit");
      oneToOne.addActionListener(e -> onOneToOne());
      fit.addActionListener(e -> onFit());
      exportButton_.addActionListener(e -> onExportClicked());
      p.add(oneToOne);
      p.add(fit);
      p.add(exportButton_, "wrap");
      p.add(statusLabel_, "span 3");
      return p;
   }

   private void onOneToOne() {
      if (viewer_ == null) {
         return;
      }
      NDViewer2API v = viewer_.getNDViewer();
      Point2D.Double displaySize = v.getDisplayImageSize();
      v.setFullResSourceDataSize(displaySize.x, displaySize.y);
      v.update();
   }

   private void onFit() {
      if (viewer_ == null) {
         return;
      }
      NDViewer2API v = viewer_.getNDViewer();
      NDViewer2DataProviderAPI dp = (NDViewer2DataProviderAPI) viewer_.getDataProvider();
      int[] b = dp.getStorage().getImageBounds();
      if (b == null) {
         return;
      }
      v.setViewOffset(b[0], b[1]);
      v.setFullResSourceDataSizeAspectCorrected(b[2] - b[0], b[3] - b[1]);
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
      NDViewer2API v = viewer_.getNDViewer();
      v.getCanvasJPanel().setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      setStatus("Draw a selection on the image");

      ExportSelectionOverlay exportOverlay = new ExportSelectionOverlay(v);
      viewer_.setOverlayerPlugin(exportOverlay);

      ExportMouseListener exportListener = new ExportMouseListener(v,
              () -> {
                 // onRelease: restore normal state
                 exportOverlay.setExportMouseListener(null);
                 viewer_.setOverlayerPlugin(null);
                 v.getCanvasJPanel().setCursor(Cursor.getDefaultCursor());
                 if (lastRoi_ != null) {
                    // ROI accepted: install dismiss listener
                    v.setCustomCanvasMouseListener(new NDViewer2CanvasMouseListenerInterface() {
                       private void dismiss() {
                          lastRoi_ = null;
                          v.setOverlay(new Overlay());
                          v.resetCanvasMouseListener();
                          setStatus(null);
                       }

                       @Override public void mousePressed(MouseEvent e) { dismiss(); }
                       @Override public void mouseReleased(MouseEvent e) {}
                       @Override public void mouseClicked(MouseEvent e) {}
                       @Override public void mouseDragged(MouseEvent e) {}
                       @Override public void mouseMoved(MouseEvent e) {}
                       @Override public void mouseEntered(MouseEvent e) {}
                       @Override public void mouseExited(MouseEvent e) {}
                       @Override public void mouseWheelMoved(MouseWheelEvent e) {}
                    });
                    setStatus("Click to dismiss selection");
                 } else {
                    v.setOverlay(new Overlay());
                    v.resetCanvasMouseListener();
                    setStatus(null);
                 }
              },
              this::onRoiSelected);
      exportOverlay.setExportMouseListener(exportListener);
      v.setCustomCanvasMouseListener(exportListener);
   }

   private void onRoiSelected(Point dragStart, Point dragEnd) {
      NDViewer2API v = viewer_.getNDViewer();
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
      NDViewer2API v = viewer_.getNDViewer();
      NDViewer2DataProviderAPI dp = (NDViewer2DataProviderAPI) viewer_.getDataProvider();
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
      viewer_ = (NDViewer2DataViewerAPI) viewer;
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
