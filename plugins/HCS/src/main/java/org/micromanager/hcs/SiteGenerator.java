package org.micromanager.hcs;

import com.bulenkov.iconloader.IconLoader;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;
import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
import org.micromanager.Studio;
// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.TextUtils;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * This is the primary user interface for the plugin.
 *
 * @author nenad
 */
public class SiteGenerator extends JFrame implements ParentPlateGUI {

   private static final String EQUAL_SPACING = "Equal XY";
   private static final String DIFFERENT_SPACING = "Different XY";
   private static final String VIEW_SPACING = "Field of View";

   private static final String SNAKE_ORDER = "Snake";
   private static final String TYPEWRITER_ORDER = "Typewriter";
   private static final String CASCADE_ORDER = "Cascade";
   
   private static final String ZPLANESTAGE = "Z-Plane stage: ";

   private final JTextField spacingFieldX_;
   private final JTextField spacingFieldY_;
   private final JTextField overlapField_;
   private final JTextField columnsField_;
   private final JTextField rowsField_;
   private final JComboBox<String> plateIDCombo_;
   private boolean shouldIgnoreFormatEvent_ = false;
   private static final long serialVersionUID = 1L;
   private final SBSPlate plate_;
   private final PlatePanel platePanel_;
   private final Studio studio_;
   private final Point2D.Double xyStagePos_;
   private Point2D.Double offset_;
   private Boolean isCalibratedXY_;
   private double zStagePos_;
   private final Point2D.Double cursorPos_;
   private String stageWell_;
   private String cursorWell_;
   PositionList threePtList_;
   AFPlane focusPlane_;
   private final JLabel threePlaneDrive_;
   private static final  String PLATE_FORMAT_ID = "plate_format_id";
   private static final String SITE_SPACING_X  = "site_spacing"; //keep string for bac
   private static final String SITE_SPACING_Y  = "site_spacing_y";
   private static final String SITE_OVERLAP    = "site_overlap"; //in um
   private static final String SITE_ROWS       = "site_rows";
   private static final String SITE_COLS       = "site_cols";
   private static final String SITE_OFFSET     = "site_offset"; // in um
   private static final String SPACING_MODE    = "spacing_mode";
   private static final String LIST_OVERWRITE  = "list_overwrite";

   private final JLabel statusLabel_;
   private final JCheckBox chckbxThreePt_;
   private final JComboBox<String> spacingMode_;
   private final JComboBox<String> visitOrderInWell_;
   private final JComboBox<String> visitOrderBetweenWells_;

   private double xSpacing_ = 0.0;
   private double ySpacing_ = 0.0;
   
   private final JToggleButton moveStage_;
   private final JToggleButton selectWells_;
   private final JRadioButton overWriteMMList_;
   private final JRadioButton appendToMMList_;


   /**
    * Create the frame.
    *
    * @param studio Micro-Manager api
    */
   public SiteGenerator(Studio studio) {
      super();
      studio_ = studio;
      
      super.setMinimumSize(new Dimension(815, 600));
      super.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(final WindowEvent e) {
            saveSettings();
         }
      });

      JPanel contentsPanel = new JPanel(
            new MigLayout("fill, flowx, insets 0, gap 0"));
      super.add(contentsPanel);
      plate_ = new SBSPlate();

      xyStagePos_ = new Point2D.Double(0.0, 0.0);
      cursorPos_ = new Point2D.Double(0.0, 0.0);

      stageWell_ = "undef";
      cursorWell_ = "undef";

      threePtList_ = null;
      focusPlane_ = null;

      super.setTitle("HCS Site Generator " + HCSPlugin.VERSION_INFO);

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
              getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setBounds(100, 100, 1000, 640);
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);

      platePanel_ = new PlatePanel(plate_, null, this, studio_);
      contentsPanel.add(platePanel_, "grow, push");

      JPanel sidebar = new JPanel(new MigLayout("flowy, gap 0, insets 0"));
      contentsPanel.add(sidebar, "growprio 0, shrinkprio 200, gap 0, gapright 20, wrap");

      // Mutually-exclusive toggle buttons for what the mouse does.
      selectWells_ = new JRadioButton("Select");

      selectWells_.setToolTipText("Click and drag to select wells.");
      selectWells_.addActionListener((ActionEvent arg0) -> {
         if (selectWells_.isSelected()) {
            platePanel_.setTool(PlatePanel.Tool.SELECT);
         }
      });

      moveStage_ = new JRadioButton("Move");
      moveStage_.setToolTipText("Click to move the stage");
      moveStage_.addActionListener((ActionEvent e) -> {
         if (moveStage_.isSelected()) {
            platePanel_.setTool(PlatePanel.Tool.MOVE);
         }
      });
      final ButtonGroup toolButtonGroup = new ButtonGroup();
      toolButtonGroup.add(selectWells_);
      toolButtonGroup.add(moveStage_);
      selectWells_.setSelected(true);

      // set default tool
      platePanel_.setTool(PlatePanel.Tool.SELECT);
      sidebar.add(selectWells_, "split 2, alignx center, flowx");
      sidebar.add(moveStage_);

      final JLabel plateFormatLabel = new JLabel();
      plateFormatLabel.setAlignmentY(Component.TOP_ALIGNMENT);
      plateFormatLabel.setText("<html><b>Plate Format:</b></html>");
      sidebar.add(plateFormatLabel, "gaptop 14");

      plateIDCombo_ = new JComboBox<>();
      sidebar.add(plateIDCombo_);
      plateIDCombo_.addItem(SBSPlate.SBS_6_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_12_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_24_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_48_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_96_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_384_WELL);
      plateIDCombo_.addItem(SBSPlate.SLIDE_HOLDER);
      plateIDCombo_.addItem(SBSPlate.LOAD_CUSTOM);

      JButton customButton = new JButton("Create Custom");
      sidebar.add(customButton, "growx");
      customButton.addActionListener((ActionEvent e) -> {
         CustomSettingsFrame csf = new CustomSettingsFrame(studio_, SiteGenerator.this);
         csf.setVisible(true);
      });

      plateIDCombo_.addActionListener((final ActionEvent e) -> {
         if (shouldIgnoreFormatEvent_) {
            // Ignore this event, as it occurred due to software setting
            // the display, rather than due to the user selecting an option.
            return;
         }
         plate_.initialize((String) plateIDCombo_.getSelectedItem());
         updateXySpacing();
         PositionList sites = generateSitesInWell();
         try {
            platePanel_.refreshImagingSites(sites);
         } catch (HCSException e1) {
            if (studio_ != null) {
               studio_.logs().logError(e1);
            }
         }
         platePanel_.repaint();
      });

      final FocusListener regeneratePlateOnLossOfFocus = new FocusListener() {
         @Override
         public void focusGained(FocusEvent e) {
         }

         @Override
         public void focusLost(FocusEvent e) {
            regenerate();
         }
      };

      sidebar.add(new JLabel("<html><b>Imaging Sites:</b></html"), "gaptop 14");
      JPanel gridPanel = new JPanel(new MigLayout("flowx, gap 0, insets 0",
               "0[]0[]0", "0[]0[]0"));
      gridPanel.add(new JLabel("Rows"));
      gridPanel.add(new JLabel("Columns"), "wrap");

      rowsField_ = new JTextField(3);
      rowsField_.setText("1");
      rowsField_.setHorizontalAlignment(SwingConstants.RIGHT);
      gridPanel.add(rowsField_, "gapright 15");
      rowsField_.addFocusListener(regeneratePlateOnLossOfFocus);

      //sidebar.add(new JLabel("Columns"));
      columnsField_ = new JTextField(3);
      columnsField_.setText("1");
      columnsField_.setHorizontalAlignment(SwingConstants.RIGHT);
      gridPanel.add(columnsField_);
      columnsField_.addFocusListener(regeneratePlateOnLossOfFocus);

      sidebar.add(gridPanel);

      final JLabel spacingLabel = new JLabel();
      spacingLabel.setText("Spacing [\u00b5m]"); // U+00B5 MICRO SIGN
      sidebar.add(spacingLabel);

      spacingFieldX_ = new JTextField(3);
      spacingFieldX_.setText("1000");
      spacingFieldX_.setHorizontalAlignment(SwingConstants.RIGHT);
      spacingFieldX_.addFocusListener(regeneratePlateOnLossOfFocus);
      sidebar.add(spacingFieldX_, "split 2, flowx, wmin 50, hidemode 2, gapright 15");

      spacingFieldY_ = new JTextField();
      spacingFieldY_.setText("1000");
      spacingFieldY_.setHorizontalAlignment(SwingConstants.RIGHT);
      spacingFieldY_.addFocusListener(regeneratePlateOnLossOfFocus);
      // Take zero space when invisible.
      sidebar.add(spacingFieldY_, "wmin 50, hidemode 2");
      spacingFieldY_.setVisible(false);

      //same size and position like X_
      overlapField_ = new JTextField();
      overlapField_.setText("0");
      overlapField_.setHorizontalAlignment(SwingConstants.RIGHT);
      overlapField_.addFocusListener(regeneratePlateOnLossOfFocus);
      // Take zero space when invisible.
      sidebar.add(overlapField_, "wmin 50, hidemode 2");
      overlapField_.setVisible(false);

      sidebar.add(new JLabel("Spacing Rule:"));

      spacingMode_ = new JComboBox<>(new String[] {
         EQUAL_SPACING, DIFFERENT_SPACING, VIEW_SPACING});
      sidebar.add(spacingMode_, "growx");
      spacingMode_.setSelectedIndex(0);
      spacingMode_.addActionListener((ActionEvent e) -> {
         String mode = (String) spacingMode_.getSelectedItem();
         switch (mode) {
            case EQUAL_SPACING:
               spacingLabel.setText("Spacing [\u00b5m]"); // U+00B5 MICRO SIGN
               spacingFieldX_.setVisible(true);
               spacingFieldY_.setVisible(false);
               overlapField_.setVisible(false);
               break;
            case DIFFERENT_SPACING:
               spacingLabel.setText("Spacing X,Y [\u00b5m]"); // U+00B5 MICRO SIGN
               spacingFieldX_.setVisible(true);
               spacingFieldY_.setVisible(true);
               overlapField_.setVisible(false);
               break;
            case VIEW_SPACING:
               spacingLabel.setText("Overlap [\u00b5m]"); // U+00B5 MICRO SIGN
               overlapField_.setVisible(true);
               spacingFieldX_.setVisible(false);
               spacingFieldY_.setVisible(false);
               break;
            default:
               studio_.logs().showError("Unrecognized spacing mode " + mode);
               break;
         }
         regenerate();
      });

      sidebar.add(new JLabel("<html><b>Site visit order:</b></html>"), "gaptop 14");
      visitOrderInWell_ = new JComboBox<>(
            new String[] {SNAKE_ORDER, TYPEWRITER_ORDER, CASCADE_ORDER});
      visitOrderInWell_.addActionListener((ActionEvent e) -> regenerate());
      sidebar.add(new JLabel("In well:"), "split 2");
      sidebar.add(visitOrderInWell_, "growx");

      visitOrderBetweenWells_ = new JComboBox<>(
            new String[] {SNAKE_ORDER, TYPEWRITER_ORDER, CASCADE_ORDER});
      visitOrderBetweenWells_.addActionListener((ActionEvent e) -> regenerate());
      sidebar.add(new JLabel("Between wells:"), "split 2");
      sidebar.add(visitOrderBetweenWells_, "growx");

      final JButton refreshButton = new JButton("Refresh",
            IconLoader.getIcon("/org/micromanager/icons/arrow_refresh.png"));
      refreshButton.addActionListener((final ActionEvent e) -> regenerate());
      sidebar.add(refreshButton, "growx, gaptop 14");

      final JButton calibrateXyButton = new JButton("Calibrate XY...",
            IconLoader.getIcon("/org/micromanager/icons/cog.png"));
      calibrateXyButton.addActionListener((final ActionEvent e) -> calibrateXY());
      sidebar.add(calibrateXyButton, "growx");

      overWriteMMList_ = new JRadioButton("Overwrite");
      appendToMMList_ = new JRadioButton("Append");
      final ButtonGroup mmListButtonGroup = new ButtonGroup();
      mmListButtonGroup.add(overWriteMMList_);
      mmListButtonGroup.add(appendToMMList_);
      overWriteMMList_.setSelected(true);

      final JButton setPositionListButton = new JButton("Build MM List",
            IconLoader.getIcon("/org/micromanager/icons/table.png"));
      setPositionListButton.addActionListener((final ActionEvent e) -> {
         if (!isCalibratedXY_) {
            studio_.logs().showMessage("Calibrate XY first");
            return;
         }
         setPositionList((String) visitOrderBetweenWells_.getSelectedItem(),
                 overWriteMMList_.isSelected());
      });
      sidebar.add(setPositionListButton, "growx");

      sidebar.add(overWriteMMList_, "split 2, alignx center, flowx");
      sidebar.add(appendToMMList_);

      chckbxThreePt_ = new JCheckBox("Use 3-Point Z-Plane");
      sidebar.add(chckbxThreePt_, "gaptop 14");

      JButton btnMarkPt = new JButton("Mark Point",
            IconLoader.getIcon("/org/micromanager/icons/plus.png"));
      btnMarkPt.addActionListener((ActionEvent arg0) -> markOnePoint());
      sidebar.add(btnMarkPt, "growx");

      JButton btnSetThreePt = new JButton("Set 3-Point List",
            IconLoader.getIcon("/org/micromanager/icons/asterisk_orange.png"));
      btnSetThreePt.addActionListener((ActionEvent e) -> setThreePoint());
      sidebar.add(btnSetThreePt, "growx");
      
      threePlaneDrive_ = new JLabel(ZPLANESTAGE);
      sidebar.add(threePlaneDrive_, "growx");
      
      chckbxThreePt_.addActionListener((ActionEvent e) -> {
         platePanel_.repaint();
         btnMarkPt.setEnabled(chckbxThreePt_.isSelected());
         btnSetThreePt.setEnabled(chckbxThreePt_.isSelected());
         threePlaneDrive_.setEnabled(chckbxThreePt_.isSelected());
      });

      JButton btnAbout = new JButton("About...");
      btnAbout.addActionListener((ActionEvent e) -> {
         HCSAbout dlgAbout = new HCSAbout(SiteGenerator.this);
         dlgAbout.setVisible(true);
      });
      sidebar.add(btnAbout, "growx, gaptop 14");

      statusLabel_ = new JLabel();
      statusLabel_.setBorder(new LineBorder(new Color(0, 0, 0)));
      contentsPanel.add(statusLabel_, "dock south, gap 0");

      loadSettings();

      btnMarkPt.setEnabled(chckbxThreePt_.isSelected());
      btnSetThreePt.setEnabled(chckbxThreePt_.isSelected());

      updateXySpacing();

      PositionList sites = generateSitesInWell();
      try {
         platePanel_.refreshImagingSites(sites);
      } catch (HCSException e1) {
         if (studio_ != null) {
            studio_.logs().logError(e1);
         }
      }
   }
   
   @Override
   public void dispose() {
      saveSettings();
   }

   protected void saveSettings() {
      final MutablePropertyMapView settings = studio_.profile().getSettings(SiteGenerator.class);
      settings.putString(PLATE_FORMAT_ID, (String) plateIDCombo_.getSelectedItem());
      settings.putString(SITE_SPACING_X, spacingFieldX_.getText().replace(',', '.'));
      settings.putString(SITE_SPACING_Y, spacingFieldY_.getText().replace(',', '.'));
      settings.putString(SITE_OVERLAP, overlapField_.getText());
      settings.putString(SITE_ROWS, rowsField_.getText());
      settings.putString(SITE_COLS, columnsField_.getText());
      settings.putString(SPACING_MODE, (String) spacingMode_.getSelectedItem());
      Double[] offset;
      if (isCalibratedXY_) {
         offset = new Double[] {offset_.getX(), offset_.getY()};
      } else {
         offset = new Double[] {Double.NaN, Double.NaN};
      }
      settings.putDoubleList(SITE_OFFSET, Arrays.asList(offset));
      settings.putBoolean(LIST_OVERWRITE, overWriteMMList_.isSelected());
   }

   protected final void loadSettings() {
      final MutablePropertyMapView settings = studio_.profile().getSettings(SiteGenerator.class);
      plateIDCombo_.setSelectedItem(settings.getString(PLATE_FORMAT_ID, SBSPlate.SBS_96_WELL));
      rowsField_.setText(settings.getString(SITE_ROWS, "1"));
      columnsField_.setText(settings.getString(SITE_COLS, "1"));
      spacingFieldX_.setText(settings.getString(SITE_SPACING_X, "200"));
      spacingFieldY_.setText(settings.getString(SITE_SPACING_Y, "200"));
      overlapField_.setText(settings.getString(SITE_OVERLAP, "10"));
      spacingMode_.setSelectedItem(settings.getString(SPACING_MODE, EQUAL_SPACING));
      Double[] offset = settings.getDoubleList(SITE_OFFSET,
                      Arrays.asList(Double.NaN, Double.NaN))
              .toArray(new Double[0]);
      // If the offset appears to be valid numbers then a calibration was previously run.
      isCalibratedXY_ = Double.isFinite(offset[0]) && Double.isFinite(offset[1]);
      offset_ = new Point2D.Double(offset[0], offset[1]);
      moveStage_.setEnabled(isCalibratedXY_);
      overWriteMMList_.setSelected(settings.getBoolean(LIST_OVERWRITE, true));
      appendToMMList_.setSelected(!settings.getBoolean(LIST_OVERWRITE, true));
   }

   private void setPositionList(String betweenWellOrder, boolean replaceList) {
      List<WellPositionList> wpl = platePanel_.getSelectedWellPositions();
      if (!betweenWellOrder.equals(SNAKE_ORDER)) {
         if (betweenWellOrder.equals(TYPEWRITER_ORDER)) {
            Collections.sort(wpl, (l1, l2) -> {
               if (l1.getRow() == l2.getRow()) {
                  return l1.getColumn() - l2.getColumn();
               } else {
                  return l1.getRow() - l2.getRow();
               }
            });
         } else if (betweenWellOrder.equals(CASCADE_ORDER)) {
            Collections.sort(wpl, (l1, l2) -> {
               if (l1.getColumn() == l2.getColumn()) {
                  return l1.getRow() - l2.getRow();
               } else {
                  return l1.getColumn() - l2.getColumn();
               }
            });
         }
      }

      PositionList platePl;
      if (replaceList) {
         platePl = new PositionList();
      } else {
         platePl = studio_.positions().getPositionList();
      }
      for (WellPositionList wpl1 : wpl) {
         PositionList pl = PositionList.newInstance(wpl1.getSitePositions());
         for (int j = 0; j < pl.getNumberOfPositions(); j++) {
            MultiStagePosition msp = pl.getPosition(j);
            // make label unique
            msp.setLabel(wpl1.getLabel() + "-" + msp.getLabel());
            if (studio_ != null) {
               msp.setDefaultXYStage(studio_.getCMMCore().getXYStageDevice());
            }
            // set the proper XYstage name
            for (int k = 0; k < msp.size(); k++) {
               StagePosition sp = msp.get(k);
               if (sp.is2DStagePosition()) {
                  sp.set2DPosition(msp.getDefaultXYStage(), sp.get2DPositionX() + offset_.getX(), 
                          sp.get2DPositionY() + offset_.getY());
               }
            }
            // add Z position if 3-point focus is enabled
            if (useThreePtAF()) {
               if (focusPlane_ == null) {
                  displayError("3-point AF is selected but 3 points are not defined.");
                  return;
               }
               if (!focusPlane_.isValid()) {
                  displayError("3-point AF is selected, but 3 point list is invalid");
                  return;
               }
               msp.setDefaultZStage(focusPlane_.getZStage());
               // add z position from the 3-point plane estimate
               StagePosition sp = StagePosition.create1D(focusPlane_.getZStage(), 
                       focusPlane_.getZPos(msp.getX(), msp.getY()));
               msp.add(sp);
            }
            platePl.addPosition(pl.getPosition(j));
         }
      }

      try {
         if (studio_ != null) {
            if (platePl.getNumberOfPositions() == 0) {
               studio_.logs().showMessage("No sites selected");
            }
            studio_.positions().setPositionList(platePl);
            studio_.app().showPositionList();
         }
      } catch (Exception e) {
         displayError(e.getMessage());
      }

   }

   private void updateXySpacing() {
      String mode = (String) spacingMode_.getSelectedItem();
      if (mode.equals(VIEW_SPACING)) {
         CMMCore core = studio_.getCMMCore();
         long width = core.getImageWidth();
         long height = core.getImageHeight();
         double cameraXFieldOfView = core.getPixelSizeUm() * width;
         double cameraYFieldOfView = core.getPixelSizeUm() * height;
         double overlap;
         try {
            overlap = NumberUtils.displayStringToDouble(overlapField_.getText());
         } catch (java.text.ParseException nfe) {
            overlap = 0.0;
            overlapField_.setText(NumberUtils.doubleToDisplayString(0.0));
            studio_.logs().logError("NumberFormat error in updateXYSpacing in HCS generator");
         }
         xSpacing_ = cameraXFieldOfView - overlap;
         ySpacing_ = cameraYFieldOfView - overlap;
      } else {
         try {
            xSpacing_ = NumberUtils.displayStringToDouble(spacingFieldX_.getText());
         } catch (java.text.ParseException nfe) {
            xSpacing_ = 0.0;
            spacingFieldX_.setText(NumberUtils.doubleToDisplayString(0.0));
            studio_.logs().logError("NumberFormat error in updateXYSpacing in HCS generator");
         }
         if (mode.equals(EQUAL_SPACING)) {
            ySpacing_ = xSpacing_;
         } else {
            try {
               ySpacing_ = NumberUtils.displayStringToDouble(spacingFieldY_.getText());
            } catch (java.text.ParseException nfe) {
               ySpacing_ = 0.0;
               spacingFieldY_.setText(NumberUtils.doubleToDisplayString(0.0));
               studio_.logs().logError("NumberFormat error in updateXYSpacing in HCS generator");
            }
         }
      }
   }


   /**
    * Marks current position as one point in the 3-pt set.
    * Ensures that one XY stage and one Z stage are selected in the PositionList
    * Also ensures that the same stages as in the previous points are selected
    */
   private void markOnePoint() {
      studio_.app().showPositionList();
      int nrPositions = studio_.positions().getPositionList().getNumberOfPositions();
      if (nrPositions > 3) {
         studio_.logs().showMessage(
               "PositionList contains > 3 points.  Try to clear the Stage Position List");
         return;
      }
      if (nrPositions == 3) {
         studio_.logs().showMessage(
               "PositionList already contains 3 positions.  Delete at least one");
         return;
      }
      studio_.positions().markCurrentPosition();
      // check that exactly one z-stage and one xy-stage is checked
      MultiStagePosition msp = studio_.positions().getPositionList().getPosition(nrPositions);
      if (msp == null) {
         studio_.logs().logError("Failed to mark current position in PositionList");
         return;
      }
      if (msp.size() != 2) {
         studio_.positions().getPositionList().removePosition(nrPositions);
         studio_.logs().showMessage(
               "Make sure that only one XY stage and one Z stage is checked", this);
         return;
      }
      boolean stagesOK = false;
      if (msp.get(0).is1DStagePosition() && msp.get(1).is2DStagePosition()
            || msp.get(0).is2DStagePosition() && msp.get(1).is1DStagePosition()) {
         stagesOK = true;
      }
      if (!stagesOK) {
         studio_.positions().getPositionList().removePosition(nrPositions);
         studio_.logs().showMessage(
               "Make sure that only one XY stage and one Z stage is checked", this);
         return;
      }
      // also ensure that the same stages are checked
      nrPositions = studio_.positions().getPositionList().getNumberOfPositions();
      if (nrPositions > 1) {        
         String stage0 = msp.get(0).getStageDeviceLabel();
         String stage1 = msp.get(1).getStageDeviceLabel();
         for (int i = 0; i < nrPositions; i++) {
            MultiStagePosition mspTest = studio_.positions().getPositionList().getPosition(i);
            if (!mspTest.get(0).getStageDeviceLabel().equals(stage0)
                  || !mspTest.get(1).getStageDeviceLabel().equals(stage1)) {
               studio_.positions().getPositionList().removePosition(nrPositions - 1);
               studio_.logs().showMessage(
                       "Make sure that the same stages are checked for all 3 positions", this);
            }
         }
      }
   }

   private void setThreePoint() {
      try {
         PositionList plist = studio_.positions().getPositionList();
         if (plist.getNumberOfPositions() != 3) {
            studio_.logs().showMessage(
                    "We need exactly three positions to fit AF plane. "
                  + "Please create XY list with exactly 3 positions.");
            return;
         }

         threePtList_ = PositionList.newInstance(plist);
         focusPlane_ = new AFPlane(threePtList_.getPositions());
         if (focusPlane_.isValid()) {
            threePlaneDrive_.setText(ZPLANESTAGE + focusPlane_.getZStage());
         }
         chckbxThreePt_.setSelected(true);
         platePanel_.repaint();

      } catch (Exception e) {
         displayError(e.getMessage());
      }
   }


   private PositionList generateSitesInWell() {
      int rows = Integer.parseInt(rowsField_.getText());
      int cols = Integer.parseInt(columnsField_.getText());
      PositionList sites = new PositionList();
      if (visitOrderInWell_.getSelectedItem().equals(CASCADE_ORDER)) {
         for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
               MultiStagePosition msp = stagePositionInWell(rows, cols, row, col);
               if (msp != null) {
                  sites.addPosition(msp);
               }
            }
         }
      } else {
         for (int row = 0; row < rows; row++) {
            // In "snake" mode we go in one X direction on odd rows and the other
            // X direction on even rows; in "typewriter" mode we always use the
            // same X direction.
            int start;
            int end;
            int stepDir;
            if (visitOrderInWell_.getSelectedItem().equals(TYPEWRITER_ORDER)) {
               start = 0;
               end = cols;
               stepDir = 1;
            } else {
               boolean isEven = row % 2 == 0;
               start = isEven ? 0 : cols - 1;
               end = isEven ? cols : -1;
               stepDir = isEven ? 1 : -1;
            }
            for (int col = start; col != end; col += stepDir) {
               MultiStagePosition msp = stagePositionInWell(rows, cols, row, col);
               if (msp != null) {
                  sites.addPosition(msp);
               }
            }
         }
      }

      return sites;
   }

   /**
    * Generates a MSP for the given row and column within a well.  MultiStagePosition is
    * centered at the center of the well (i.e. 0,0 is at the center of the well).
    * Will check if the location falls outside of the well and return null if that is the case.
    *
    * @param rows Total number of rows for this PositionList
    * @param cols Total number of columns for this PositionList
    * @param row Specific row number for which to generate an MSP
    * @param col Specific column number for which to generate an MSP
    * @return MultiStagePosition for just the default XY stage relative to center of the well.
    */
   private MultiStagePosition stagePositionInWell(int rows, int cols, int row, int col) {
      double x;
      double y;
      if (cols > 1) {
         x = -cols * xSpacing_ / 2.0 + xSpacing_ * col + xSpacing_ / 2.0;
      } else {
         x = 0.0;
      }

      if (rows > 1) {
         y = -rows * ySpacing_ / 2.0 + ySpacing_ * row + ySpacing_ / 2.0;
      } else {
         y = 0.0;
      }

      // check if this location is outside the actual well
      if (plate_.isPointWithinWell(x, y)) {
         MultiStagePosition mps = new MultiStagePosition();
         StagePosition sp = StagePosition.create2D("", x, y);
         mps.add(sp);
         return mps;
      }
      return null;
   }

   @Override
   public void updatePointerXYPosition(double x, double y, String wellLabel, 
           String siteLabel) {
      cursorPos_.x = x;
      cursorPos_.y = y;
      cursorWell_ = wellLabel;

      displayStatus();
   }

   private void displayStatus() {
      if (statusLabel_ == null) {
         return;
      }
      Point2D.Double cursorOffsetPos = applyOffset(cursorPos_);
      String statusTxt = "Cursor: X=" + TextUtils.FMT2.format(cursorOffsetPos.x)
            + "\u00b5m, Y=" // Micro sign
            + TextUtils.FMT2.format(cursorOffsetPos.y)
            + "\u00b5m, "  //Micro sign
            + cursorWell_
            + ((useThreePtAF() && focusPlane_ != null) ? ", Z->"
            + TextUtils.FMT2.format(focusPlane_.getZPos(cursorOffsetPos.x, cursorOffsetPos.y))
            + "\u00b5m" : "")  //Micro sign
            + " -- Stage: X="
            + TextUtils.FMT2.format(xyStagePos_.x)
            + "\u00b5m, Y=" //Micro sign
            + TextUtils.FMT2.format(xyStagePos_.y)
            + "\u00b5m, Z=" // Micro sign
            + TextUtils.FMT2.format(zStagePos_)
            + "\u00b5m, " + stageWell_; // Micro sign
      if (SwingUtilities.isEventDispatchThread()) {
         statusLabel_.setText(statusTxt);
      } else  {
         SwingUtilities.invokeLater(() -> statusLabel_.setText(statusTxt));
      }
   }

   @Override
   public void updateStagePositions(double x, double y, double z, String wellLabel, 
           String siteLabel) {
      xyStagePos_.x = x;
      xyStagePos_.y = y;
      zStagePos_ = z;
      stageWell_ = wellLabel;

      displayStatus();
   }

   @Override
   public String getXYStageName() {
      if (studio_ != null) {
         return studio_.getCMMCore().getXYStageDevice();
      } else {
         return SBSPlate.DEFAULT_XYSTAGE_NAME;
      }
   }

   /**
    * Returns the Z stage in use by the HCS plugin.
    * If a 3 point calibration has been carried out, it will return the
    * Z Stage of the 3 point calibration.  Otherwise, the default Core focusdevice.
    *
    * @return Name of the Z stage in use by the HCS plugin.
    */
   @Override
   public String getZStageName() {
      if (useThreePtAF() && focusPlane_ != null) {
         return focusPlane_.getZStage();
      }
      return studio_.core().getFocusDevice();
   }

   @Override
   public void displayError(String txt) {
      if (studio_ != null) {
         studio_.logs().showError(txt, this);
      }
   }

   protected void calibrateXY() {
      if (studio_ == null) {
         return;
      }
      new CalibrationFrame(studio_, plate_, this);
   }

   /**
    * Finish Calibration.
    *
    * @param offset Offset found during Calibration
    */
   public void finishCalibration(Point2D.Double offset) {
      offset_ = offset;
      isCalibratedXY_ = true;
      regenerate();
      moveStage_.setEnabled(true);
   }
   
   private void regenerate() {
      updateXySpacing();
      PositionList sites = generateSitesInWell();
      List<WellPositionList> selectedWells = null;
      if (plate_.getID().equals(plateIDCombo_.getSelectedItem())) {
         selectedWells = platePanel_.getSelectedWellPositions();
      }
      plate_.initialize((String) plateIDCombo_.getSelectedItem());
      try {
         platePanel_.refreshImagingSites(sites);
      } catch (HCSException e) {
         displayError(e.getMessage());
      }

      if (selectedWells != null) {
         platePanel_.setSelectedWells(selectedWells);
      }
      platePanel_.repaint();
   }

   public void configurationChanged() {
      // TODO:
   }

   @Override
   public boolean useThreePtAF() {
      return chckbxThreePt_.isSelected();
   }

   @Override
   public PositionList getThreePointList() {
      return threePtList_;
   }

   @Override
   public Double getThreePointZPos(double x, double y) {
      if (focusPlane_ == null) {
         return null;
      }

      return focusPlane_.getZPos(x, y);
   }

   /**
    * Load custom plate layout from file.
    *
    * @param target File containing the custom plate layout
    */
   public void loadCustom(File target) {
      plate_.initialize(target.getAbsolutePath());
      PositionList sites = generateSitesInWell();
      try {
         platePanel_.refreshImagingSites(sites);
      } catch (HCSException e1) {
         if (studio_ != null) {
            studio_.logs().logError(e1);
         }
      }
      shouldIgnoreFormatEvent_ = true;
      plateIDCombo_.setSelectedItem(SBSPlate.LOAD_CUSTOM);
      shouldIgnoreFormatEvent_ = false;
      platePanel_.repaint();
   }
   
   @Override
   public Point2D.Double getOffset() {
      if (offset_ == null) {
         return new Point2D.Double(0, 0);
      } else {
         return offset_;
      }
   }

   @Override
   public boolean isCalibratedXY() {
      return isCalibratedXY_;
   }

   @Override
   public Point2D.Double applyOffset(Point2D.Double pt) {
      Point2D.Double offset = getOffset();
      pt.setLocation(pt.getX() + offset.getX(), pt.getY() + offset.getY());
      return pt;
   }
}
