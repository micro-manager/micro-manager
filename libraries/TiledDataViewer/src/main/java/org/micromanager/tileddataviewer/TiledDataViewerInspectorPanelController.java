package org.micromanager.tileddataviewer;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Window;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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

   /**
    * Shared help text describing the Explorer / TiledDataViewer mouse and button controls.
    * Used by this panel's Help button and by the Explorer/Deskew plugin frames so the
    * documentation stays in one place.
    */
   public static final String EXPLORE_HELP_TEXT =
         "Navigation:\n"
               + "  Left-drag: pan view\n"
               + "  Scroll wheel: zoom in/out\n"
               + "\n"
               + "Tile selection (live explore):\n"
               + "  Right-click: select tile\n"
               + "  Right-drag: expand selection\n"
               + "  Left-click: acquire (or queue) selected tiles\n"
               + "  Interrupt: cancel queued tiles; the current tile finishes first\n"
               + "  Ctrl+left-click: move stage to position\n"
               + "\n"
               + "View controls:\n"
               + "  Center: pan to center of dataset (keep zoom)\n"
               + "  No Zoom: zoom to 1:1 and center on dataset\n"
               + "\n"
               + "Export:\n"
               + "  Click Export, drag to draw ROI, then confirm export\n"
               + "  Click anywhere to dismiss the ROI";

   private final Studio studio_;
   private final JPanel panel_;
   private final JLabel statusLabel_;
   private final JButton exportButton_;
   private final JButton interruptButton_;
   private final JButton helpButton_;
   private TiledDataViewerDataViewerAPI viewer_;
   private static boolean expanded_ = true;

   // Live-explore acquisition controls for the attached viewer, or null if the data
   // source does not support interrupting (e.g. a read-only opened dataset).
   private TiledDataViewerExploreControls exploreControls_;
   // Listener registered on exploreControls_ while attached; updates the Interrupt button.
   private TiledDataViewerExploreControls.AcquisitionStateListener acqStateListener_;

   // Last confirmed export ROI in full-resolution pixels [x, y, w, h]; null when none.
   private int[] lastRoi_ = null;

   public TiledDataViewerInspectorPanelController(Studio studio) {
      studio_ = studio;
      statusLabel_ = new JLabel(" ");
      exportButton_ = new JButton("Export...");
      interruptButton_ = new JButton("Interrupt");
      helpButton_ = new JButton("Help");
      panel_ = buildPanel();
   }

   private JPanel buildPanel() {
      final JPanel p = new JPanel(new MigLayout("insets 4", "[]4[]4[]", "[]2[]"));
      JButton center = new JButton("Center");
      JButton noZoom = new JButton("No Zoom");
      center.addActionListener(e -> onCenter());
      noZoom.addActionListener(e -> onNoZoom());
      exportButton_.addActionListener(e -> onExportClicked());
      interruptButton_.setToolTipText(
            "Stop tile acquisition after the current tile finishes.");
      interruptButton_.setEnabled(false);
      interruptButton_.addActionListener(e -> {
         if (exploreControls_ != null) {
            exploreControls_.interruptAcquisition();
         }
      });
      helpButton_.addActionListener(e -> showHelp());
      p.add(center);
      p.add(noZoom, "wrap");
      p.add(exportButton_, "wrap");
      p.add(interruptButton_);
      p.add(helpButton_, "wrap");
      p.add(statusLabel_, "span 2, wrap");
      return p;
   }

   private void showHelp() {
      JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(panel_),
            EXPLORE_HELP_TEXT, "Explorer Help", JOptionPane.PLAIN_MESSAGE);
   }

   /** Returns the center of the dataset in full-res pixel coordinates, or null if unknown. */
   private Point2D.Double getDataCenter() {
      TiledDataViewerDataProviderAPI dp =
               (TiledDataViewerDataProviderAPI) viewer_.getDataProvider();
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
      TiledDataViewerAPI v = viewer_.getTiledDataViewer();
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
      TiledDataViewerAPI v = viewer_.getTiledDataViewer();
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

   // ---- Export ----

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
      TiledDataViewerAPI v = viewer_.getTiledDataViewer();
      v.getCanvasJPanel().setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      setStatus("Draw a selection on the image");

      TiledDataViewerOverlayerPlugin previousOverlay = viewer_.getOverlayerPlugin();
      ExportSelectionOverlay exportOverlay = new ExportSelectionOverlay(v);
      viewer_.setOverlayerPlugin(exportOverlay);

      ExportMouseListener[] el = new ExportMouseListener[1];
      el[0] = new ExportMouseListener(v,
              () -> {
                 v.getCanvasJPanel().setCursor(Cursor.getDefaultCursor());
                 if (lastRoi_ != null) {
                    exportOverlay.freezeRoi(el[0].mouseDragStartPoint_,
                            el[0].currentMouseLocation_);
                    el[0].setOnDismiss(() -> {
                       lastRoi_ = null;
                       viewer_.setOverlayerPlugin(previousOverlay);
                       v.resetCanvasMouseListener();
                       v.update();
                       setStatus(null);
                    });
                    setStatus("Click to dismiss selection");
                 } else {
                    viewer_.setOverlayerPlugin(previousOverlay);
                    v.resetCanvasMouseListener();
                    v.update();
                    setStatus(null);
                 }
              },
              this::onRoiSelected);
      exportOverlay.setExportMouseListener(el[0]);
      v.setCustomCanvasMouseListener(el[0]);
   }

   private void onRoiSelected(Point dragStart, Point dragEnd) {
      TiledDataViewerAPI v = viewer_.getTiledDataViewer();
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
      TiledDataViewerDataProviderAPI dp =
               (TiledDataViewerDataProviderAPI) viewer_.getDataProvider();
      List<String> chNames = viewer_.getExportChannelNames();
      Window owner = SwingUtilities.getWindowAncestor(panel_);
      ExportTiles.showDialogAndExport(owner, dp.getStorage(),
            buildDisplaySettingsJSON(), new HashMap<String, Object>(), chNames,
            roi[0], roi[1], roi[2], roi[3]);
   }

   private JSONObject buildDisplaySettingsJSON() {
      return viewer_.buildExportDisplaySettingsJSON();
   }

   private void setStatus(String text) {
      statusLabel_.setText(text == null ? " " : text);
   }

   @Override
   public void attachDataViewer(DataViewer viewer) {
      // The Inspector reuses one controller instance and may re-attach without an
      // intervening detach when the active viewer changes; detach first so we never
      // leak a listener on the previous viewer's explore controls.
      if (viewer_ != null) {
         detachDataViewer();
      }
      viewer_ = (TiledDataViewerDataViewerAPI) viewer;
      lastRoi_ = null;

      exploreControls_ = viewer_.getExploreControls();
      final boolean inProgress =
            exploreControls_ != null && exploreControls_.isAcquisitionInProgress();
      // Interrupt is only meaningful for a live explore session; for a read-only
      // viewer it stays present but disabled (matching the other panel buttons).
      // Touch Swing on the EDT in case attach is ever called off-EDT.
      SwingUtilities.invokeLater(() -> interruptButton_.setEnabled(inProgress));
      if (exploreControls_ != null) {
         acqStateListener_ = changed ->
               SwingUtilities.invokeLater(() -> interruptButton_.setEnabled(changed));
         exploreControls_.addAcquisitionStateListener(acqStateListener_);
      }
   }

   @Override
   public void detachDataViewer() {
      if (exploreControls_ != null && acqStateListener_ != null) {
         exploreControls_.removeAcquisitionStateListener(acqStateListener_);
      }
      exploreControls_ = null;
      acqStateListener_ = null;
      SwingUtilities.invokeLater(() -> interruptButton_.setEnabled(false));
      viewer_ = null;
      lastRoi_ = null;
      setStatus(null);
   }

   @Override
   public String getTitle() {
      return "Tiled Data Viewer (Explorer) Controls";
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
