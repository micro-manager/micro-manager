package org.micromanager.explorer;

import java.awt.Toolkit;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;
import org.micromanager.tileddataviewer.TiledDataViewerInspectorPanelController;

/**
 * Simple dialog for the Explorer plugin.
 * Lets the user configure a temp path and tile overlap, then start/open/stop an explore session.
 */
public class ExplorerFrame extends JFrame {

   private static final int DEFAULT_WIN_X = 100;
   private static final int DEFAULT_WIN_Y = 100;
   private static final String DIALOG_TITLE = "Explorer";

   static final String EXPLORE_TMP_PATH = "ExploreTmpPath";
   static final String EXPLORE_OVERLAP_PERCENT = "ExploreOverlapPercent";
   static final String VESSEL_TYPE = "VesselType";

   private final Studio studio_;
   private final MutablePropertyMapView settings_;
   private final ExplorerManager explorerManager_;

   private boolean sessionActive_ = false;
   // True only for a live (Started) session; false for an opened (read-only) dataset.
   private boolean liveSession_ = false;

   private JButton stopButton_;
   private JComboBox<VesselType> vesselCombo_;

   // Create-Positions controls (live session only).
   private JButton createPositionsButton_;
   private JComboBox<String> positionToolCombo_;
   private JCheckBox withinVesselCheck_;
   private JButton generatePositionsButton_;
   private JButton clearRoiButton_;
   private JLabel positionStatusLabel_;
   private boolean positionDrawActive_ = false;

   // Simple-vessel anchor (coverslips): 5 corner/center buttons.
   private JPanel simpleAnchorPanel_;
   private final List<JButton> simpleAnchorButtons_ = new ArrayList<>();

   // Multi-well anchor panel: HCS calibration status + optional well selector.
   private JPanel wellAnchorPanel_;
   private JLabel hcsStatusLabel_;
   private JButton refreshHcsButton_;
   private JComboBox<String> wellRowCombo_;
   private JSpinner wellColSpinner_;
   private JButton setWellAnchorButton_;

   public org.micromanager.PropertyMap getSettings() {
      return settings_.toPropertyMap();
   }

   public ExplorerFrame(Studio studio) {
      studio_ = studio;
      settings_ = studio_.profile().getSettings(this.getClass());
      explorerManager_ = new ExplorerManager(studio, this);

      initComponents();

      super.setLocation(DEFAULT_WIN_X, DEFAULT_WIN_Y);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
   }

   private void initComponents() {
      super.setTitle(DIALOG_TITLE);
      URL iconUrl = getClass().getResource("/org/micromanager/icons/microscope.gif");
      if (iconUrl != null) {
         setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
      }
      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setLayout(new MigLayout("fillx", "[grow, fill][]"));

      add(new JLabel("Tmp Path:"), "split 3");
      JTextField tmpPathField = new JTextField(25);
      tmpPathField.setToolTipText(
            "Directory for temporary explore data (uses system temp dir if empty)");
      tmpPathField.setText(settings_.getString(EXPLORE_TMP_PATH,
            System.getProperty("java.io.tmpdir")));
      tmpPathField.getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void insertUpdate(DocumentEvent e) {
            settings_.putString(EXPLORE_TMP_PATH, tmpPathField.getText());
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            settings_.putString(EXPLORE_TMP_PATH, tmpPathField.getText());
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            settings_.putString(EXPLORE_TMP_PATH, tmpPathField.getText());
         }
      });
      add(tmpPathField, "growx");

      JButton browseButton = new JButton("...");
      browseButton.setToolTipText("Browse for a temporary storage directory");
      browseButton.addActionListener(e -> {
         JFileChooser chooser = new JFileChooser();
         chooser.setDialogTitle("Select temporary storage directory");
         chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
         String current = tmpPathField.getText().trim();
         if (!current.isEmpty()) {
            chooser.setCurrentDirectory(new File(current));
         }
         if (chooser.showOpenDialog(ExplorerFrame.this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            tmpPathField.setText(path);
            settings_.putString(EXPLORE_TMP_PATH, path);
         }
      });
      add(browseButton, "wrap");

      add(new JLabel("Overlap (%):"), "split 2");
      SpinnerNumberModel overlapModel = new SpinnerNumberModel(10, 0, 50, 5);
      JSpinner overlapSpinner = new JSpinner(overlapModel);
      overlapSpinner.setToolTipText(
            "Percentage overlap between adjacent tiles (0-50%).");
      overlapSpinner.setValue(settings_.getInteger(EXPLORE_OVERLAP_PERCENT, 10));
      overlapSpinner.addChangeListener(e ->
            settings_.putInteger(EXPLORE_OVERLAP_PERCENT, (Integer) overlapSpinner.getValue()));
      add(overlapSpinner, "wrap");

      // Vessel type selector
      add(new JLabel("Vessel:"), "split 2");
      vesselCombo_ = new JComboBox<>(VesselType.builtIn().toArray(new VesselType[0]));
      String savedVessel = settings_.getString(VESSEL_TYPE, VesselType.NONE.getName());
      VesselType.builtIn().stream()
            .filter(v -> v.getName().equals(savedVessel))
            .findFirst()
            .ifPresent(vesselCombo_::setSelectedItem);
      vesselCombo_.setToolTipText("Select the type of vessel on the stage");
      vesselCombo_.addActionListener(e -> {
         VesselType selected = (VesselType) vesselCombo_.getSelectedItem();
         if (selected != null) {
            settings_.putString(VESSEL_TYPE, selected.getName());
            explorerManager_.setVesselType(selected);
         }
         updateAnchorPanels();
         updatePositionToolsEnabled();
      });
      add(vesselCombo_, "wrap");

      // ── Panel A: simple-vessel corner/center anchor (coverslips) ──────────────
      simpleAnchorPanel_ = new JPanel(new MigLayout("insets 0"));
      simpleAnchorPanel_.add(new JLabel("Anchor:"));
      for (VesselType.AnchorType at : VesselType.AnchorType.values()) {
         String label = anchorLabel(at);
         JButton btn = new JButton(label);
         btn.setToolTipText("Set current stage position as the " + label + " of the vessel");
         btn.setEnabled(false);
         btn.addActionListener(e -> explorerManager_.setVesselAnchor(at));
         simpleAnchorButtons_.add(btn);
         simpleAnchorPanel_.add(btn);
      }
      simpleAnchorPanel_.setVisible(false);
      add(simpleAnchorPanel_, "growx, wrap");

      // ── Panel B: multi-well anchor (HCS calibration + well selector) ──────────
      wellAnchorPanel_ = new JPanel(new MigLayout("insets 0, fillx"));
      wellAnchorPanel_.add(new JLabel("HCS cal:"));
      hcsStatusLabel_ = new JLabel("Not found");
      wellAnchorPanel_.add(hcsStatusLabel_);
      refreshHcsButton_ = new JButton("Refresh");
      refreshHcsButton_.setToolTipText(
            "Re-read HCS plugin calibration from the profile and apply it");
      refreshHcsButton_.setEnabled(false);
      refreshHcsButton_.addActionListener(e -> explorerManager_.refreshHcsCalibration());
      wellAnchorPanel_.add(refreshHcsButton_, "wrap");

      wellAnchorPanel_.add(new JLabel("Anchor well:"));
      wellRowCombo_ = new JComboBox<>();
      wellRowCombo_.setToolTipText("Row of the well to use as anchor");
      wellAnchorPanel_.add(wellRowCombo_);
      wellColSpinner_ = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
      wellColSpinner_.setToolTipText("Column of the well to use as anchor");
      wellAnchorPanel_.add(wellColSpinner_);
      setWellAnchorButton_ = new JButton("Set Anchor");
      setWellAnchorButton_.setToolTipText(
            "Set current stage position as the center of the selected well");
      setWellAnchorButton_.setEnabled(false);
      setWellAnchorButton_.addActionListener(e -> {
         int row = wellRowCombo_.getSelectedIndex();
         int col = (Integer) wellColSpinner_.getValue() - 1;
         explorerManager_.setVesselWellAnchor(row, col);
      });
      wellAnchorPanel_.add(setWellAnchorButton_, "wrap");
      wellAnchorPanel_.setVisible(false);
      add(wellAnchorPanel_, "growx, wrap");

      // Create Positions panel (live session only).
      final JPanel positionPanel = new JPanel(new MigLayout("insets 0, fillx"));
      createPositionsButton_ = new JButton("Create Positions");
      createPositionsButton_.setToolTipText(
            "Draw an ROI on the image, then add a tiled position list covering it.");
      createPositionsButton_.setEnabled(false);
      createPositionsButton_.addActionListener(e -> togglePositionDraw());
      positionPanel.add(createPositionsButton_);

      positionToolCombo_ = new JComboBox<>(
            new String[] {"Rectangle", "Oval", "Polygon", "Freehand"});
      positionToolCombo_.setToolTipText("Shape to draw for the position region");
      positionToolCombo_.setEnabled(false);
      positionToolCombo_.addActionListener(e -> {
         if (positionDrawActive_) {
            explorerManager_.setPositionDrawTool(selectedPositionTool());
         }
      });
      positionPanel.add(positionToolCombo_);

      withinVesselCheck_ = new JCheckBox("Only within vessel");
      withinVesselCheck_.setToolTipText(
            "Generate positions only where they fall within the vessel boundary");
      withinVesselCheck_.setEnabled(false);
      positionPanel.add(withinVesselCheck_);

      generatePositionsButton_ = new JButton("Add to Position List");
      generatePositionsButton_.setToolTipText(
            "Generate positions covering the drawn ROI using the current pixel size");
      generatePositionsButton_.setEnabled(false);
      generatePositionsButton_.addActionListener(e ->
            explorerManager_.createPositionsFromRoi(withinVesselCheck_.isSelected()));
      positionPanel.add(generatePositionsButton_);

      clearRoiButton_ = new JButton("Clear");
      clearRoiButton_.setToolTipText("Clear the drawn ROI");
      clearRoiButton_.setEnabled(false);
      clearRoiButton_.addActionListener(e -> {
         explorerManager_.clearPositionRoi();
         setGenerateEnabled(false);
         setPositionStatus("Draw an ROI.");
      });
      positionPanel.add(clearRoiButton_, "wrap");

      positionStatusLabel_ = new JLabel(" ");
      positionPanel.add(positionStatusLabel_, "span, growx, wrap");
      add(positionPanel, "growx, wrap");

      JButton openButton = new JButton("Open Existing");
      openButton.setToolTipText("Open a previously saved Explorer dataset.");
      openButton.addActionListener(e -> openExplore());
      add(openButton, "split 4");

      JButton startButton = new JButton("Start");
      startButton.setToolTipText(
            "Start explore mode. Right-click to select tiles, left-click to acquire.");
      startButton.addActionListener(e -> explorerManager_.startExplore());
      add(startButton);

      stopButton_ = new JButton("Interrupt");
      stopButton_.setToolTipText("Interrupt tile acquisition after the current tile finishes.");
      stopButton_.setEnabled(false);
      stopButton_.addActionListener(e -> explorerManager_.interruptAcquisition());
      add(stopButton_);

      JButton helpButton = new JButton("Help");
      helpButton.addActionListener(e -> JOptionPane.showMessageDialog(
            this,
            TiledDataViewerInspectorPanelController.EXPLORE_HELP_TEXT
                  + "\n\n"
                  + "Images pass through the active Data Processing Pipeline.\n"
                  + "Configure the pipeline in MM's Data Processing Pipeline window.",
            "Explorer Help", JOptionPane.PLAIN_MESSAGE));
      add(helpButton, "wrap");

      pack();
   }

   private void openExplore() {
      File result = FileDialogs.openDir(ExplorerFrame.this,
              "Select Explorer Dataset",
              FileDialogs.MM_DATA_SET);
      if (result != null) {
         File parent = result.getParentFile();
         if (parent != null) {
            FileDialogs.storePath(FileDialogs.MM_DATA_SET, parent);
         }
         explorerManager_.openExplore(result.getAbsolutePath());
      }
   }

   /**
    * Enables or disables the Stop button.
    * Called from ExplorerManager; switches to EDT.
    */
   public void setAcquisitionInProgress(boolean inProgress) {
      SwingUtilities.invokeLater(() -> {
         if (stopButton_ != null) {
            stopButton_.setEnabled(inProgress);
         }
      });
   }

   /**
    * Notifies the frame that an explore session has started or stopped.
    * Enables or disables anchor controls accordingly. {@code live} is true only for a
    * Started session (not an opened, read-only dataset); Create-Positions tools are gated on it.
    * Called from ExplorerManager; switches to EDT.
    */
   public void setExploringActive(boolean active, boolean live) {
      SwingUtilities.invokeLater(() -> {
         sessionActive_ = active;
         liveSession_ = active && live;
         if (!liveSession_ && positionDrawActive_) {
            exitPositionDraw();
         }
         updateAnchorPanels();
         updatePositionToolsEnabled();
      });
   }

   /**
    * Updates the HCS calibration status label and re-evaluates well-selector enable state.
    * Called from ExplorerManager; switches to EDT.
    */
   public void setHcsCalibrationStatus(boolean found) {
      SwingUtilities.invokeLater(() -> {
         hcsStatusLabel_.setText(found ? "Found ✓" : "Not found");
         updateAnchorPanels();
      });
   }

   /** Returns the vessel currently selected in the combo box. */
   public VesselType getSelectedVessel() {
      VesselType v = (VesselType) vesselCombo_.getSelectedItem();
      return v != null ? v : VesselType.NONE;
   }

   private void updateAnchorPanels() {
      VesselType v = (VesselType) vesselCombo_.getSelectedItem();
      if (v == null) {
         v = VesselType.NONE;
      }
      boolean isMultiWell = !v.isNone() && v.isMultiWell();
      boolean isSimple    = !v.isNone() && !v.isMultiWell();

      simpleAnchorPanel_.setVisible(isSimple);
      wellAnchorPanel_.setVisible(isMultiWell);

      simpleAnchorButtons_.forEach(b -> b.setEnabled(sessionActive_ && isSimple));

      refreshHcsButton_.setEnabled(sessionActive_ && isMultiWell);

      boolean hcsFound = hcsStatusLabel_.getText().startsWith("Found");
      boolean wellAnchorEnabled = sessionActive_ && isMultiWell && !hcsFound;
      wellRowCombo_.setEnabled(wellAnchorEnabled);
      wellColSpinner_.setEnabled(wellAnchorEnabled);
      setWellAnchorButton_.setEnabled(wellAnchorEnabled);

      if (isMultiWell) {
         populateWellSelector(v);
      }

      pack();
   }

   private void populateWellSelector(VesselType v) {
      int currentRow = wellRowCombo_.getSelectedIndex();
      wellRowCombo_.removeAllItems();
      for (int r = 0; r < v.getWellRows(); r++) {
         wellRowCombo_.addItem(VesselType.getRowLabel(r));
      }
      if (currentRow >= 0 && currentRow < v.getWellRows()) {
         wellRowCombo_.setSelectedIndex(currentRow);
      }
      int currentCol = (Integer) wellColSpinner_.getValue();
      SpinnerNumberModel model = new SpinnerNumberModel(
            Math.min(currentCol, v.getWellCols()), 1, v.getWellCols(), 1);
      wellColSpinner_.setModel(model);
   }

   private ExplorerDataSource.PositionTool selectedPositionTool() {
      Object sel = positionToolCombo_.getSelectedItem();
      String name = sel != null ? sel.toString() : "Rectangle";
      switch (name) {
         case "Oval":
            return ExplorerDataSource.PositionTool.OVAL;
         case "Polygon":
            return ExplorerDataSource.PositionTool.POLYGON;
         case "Freehand":
            return ExplorerDataSource.PositionTool.FREEHAND;
         case "Rectangle":
         default:
            return ExplorerDataSource.PositionTool.RECTANGLE;
      }
   }

   private void togglePositionDraw() {
      if (positionDrawActive_) {
         exitPositionDraw();
      } else {
         positionDrawActive_ = true;
         createPositionsButton_.setText("Stop Drawing");
         explorerManager_.setPositionDrawTool(selectedPositionTool());
         setPositionStatus("Draw an ROI on the image.");
         updatePositionToolsEnabled();
      }
   }

   private void exitPositionDraw() {
      positionDrawActive_ = false;
      if (createPositionsButton_ != null) {
         createPositionsButton_.setText("Create Positions");
      }
      explorerManager_.setPositionDrawTool(ExplorerDataSource.PositionTool.NONE);
      updatePositionToolsEnabled();
   }

   /** Enables/disables the Generate button. Called from ExplorerManager; switches to EDT. */
   public void setGenerateEnabled(boolean enabled) {
      SwingUtilities.invokeLater(() -> {
         if (generatePositionsButton_ != null) {
            generatePositionsButton_.setEnabled(enabled && liveSession_ && positionDrawActive_);
         }
      });
   }

   /** Sets the Create-Positions status text. Called from ExplorerManager; switches to EDT. */
   public void setPositionStatus(String text) {
      SwingUtilities.invokeLater(() ->
            positionStatusLabel_.setText(text == null || text.isEmpty() ? " " : text));
   }

   private void updatePositionToolsEnabled() {
      if (createPositionsButton_ == null) {
         return;
      }
      createPositionsButton_.setEnabled(liveSession_);
      boolean drawing = liveSession_ && positionDrawActive_;
      positionToolCombo_.setEnabled(drawing);
      clearRoiButton_.setEnabled(drawing);
      boolean vesselSelected = !getSelectedVessel().isNone();
      withinVesselCheck_.setEnabled(drawing && vesselSelected);
      if (!drawing) {
         generatePositionsButton_.setEnabled(false);
      }
   }

   private static String anchorLabel(VesselType.AnchorType at) {
      switch (at) {
         case TOP_LEFT:     return "Top-Left";
         case TOP_RIGHT:    return "Top-Right";
         case BOTTOM_LEFT:  return "Bot-Left";
         case BOTTOM_RIGHT: return "Bot-Right";
         case CENTER:       return "Center";
         default:           return at.name();
      }
   }
}
