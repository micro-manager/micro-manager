package org.micromanager.hcs;

import com.bulenkov.iconloader.IconLoader;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import org.micromanager.MenuPlugin;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.StagePosition;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.TextUtils;

import com.swtdesigner.SwingResourceManager;

import javax.swing.border.LineBorder;

import java.awt.Color;
import java.awt.Component;
import java.awt.geom.AffineTransform;

import net.miginfocom.swing.MigLayout;

import mmcorej.CMMCore;

import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;

import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * This is the primary user interface for the plugin.
 * @author nenad
 */
public class SiteGenerator extends MMFrame implements ParentPlateGUI {

   private static final String EQUAL_SPACING = "Equal XY";
   private static final String DIFFERENT_SPACING = "Different XY";
   private static final String VIEW_SPACING = "Field of View";

   private static final String SNAKE_ORDER = "Snake";
   private static final String TYPEWRITER_ORDER = "Typewriter";

   private CMMCore core_;
   private JTextField spacingFieldX_;
   private JTextField spacingFieldY_;
   private JTextField overlapField_;
   private JTextField columnsField_;
   private JTextField rowsField_;
   private JComboBox plateIDCombo_;
   private static final long serialVersionUID = 1L;
   private SBSPlate plate_;
   private PlatePanel platePanel_;
   private Studio app_;
   private final Point2D.Double xyStagePos_;
   private double zStagePos_;
   private final Point2D.Double cursorPos_;
   private String stageWell_;
   private String cursorWell_;
   PositionList threePtList_;
   AFPlane focusPlane_;
   private final String PLATE_FORMAT_ID = "plate_format_id";
   private final String SITE_SPACING_X  = "site_spacing"; //keep string for bac
   private final String SITE_SPACING_Y  = "site_spacing_y";
   private final String SITE_OVERLAP    = "site_overlap"; //in µm
   private final String SITE_ROWS       = "site_rows";
   private final String SITE_COLS       = "site_cols";
   private final String LOCK_ASPECT = "lock_aspect";
   private final String POINTER_MOVE = "Move";
   private final String POINTER_SELECT = "Select";
   private final String ROOT_DIR = "root";
   private final String PLATE_DIR = "plate";
   public static final String menuName = "HCS Site Generator";
   public static final String tooltipDescription =
           "Generate position list for multi-well plates";
   private final JLabel statusLabel_;
   private final JCheckBox chckbxThreePt_;
   private final ButtonGroup toolButtonGroup = new ButtonGroup();
   private final JComboBox spacingMode_;
   private final JComboBox visitOrder_;

   private double xSpacing_ = 0.0;
   private double ySpacing_ = 0.0;


   private void updateXySpacing() {
      String mode = (String) spacingMode_.getSelectedItem();
      if (mode.equals(VIEW_SPACING)) {
       core_ = app_.getCMMCore();
       long width  = core_.getImageWidth();
       long height = core_.getImageHeight();
       double cameraXFieldOfView = core_.getPixelSizeUm() * width;
       double cameraYFieldOfView = core_.getPixelSizeUm() * height;
       double overlap = Double.parseDouble(overlapField_.getText().replace(',', '.'));
       xSpacing_ = cameraXFieldOfView - overlap;
       ySpacing_ = cameraYFieldOfView - overlap;
     }
     else {
       xSpacing_ = Double.parseDouble(spacingFieldX_.getText().replace(',','.'));
       if (mode.equals(EQUAL_SPACING)) {
          ySpacing_ = xSpacing_;
         }
       else {
          ySpacing_ = Double.parseDouble(spacingFieldY_.getText().replace(',', '.'));
       }
     }
   }

   public AffineTransform getCurrentAffineTransform() {
      AffineTransform transform = null;
      try {
         transform = app_.compat().getCameraTransform(core_.getCameraDevice());
      } catch (Exception ex) {
         app_.logs().logError(ex);
      }


      if (transform == null) {
         int result = JOptionPane.showConfirmDialog(null,
                 "The current magnification setting needs to be calibrated.\n" +
                 "Would you like to run automatic pixel calibration?",
                 "Pixel calibration required.",
                 JOptionPane.YES_NO_OPTION);
         if (result == JOptionPane.YES_OPTION) {
            try {
               MenuPlugin pc = app_.plugins().getMenuPlugins().get("org.micromanager.pixelcalibrator.PixelCalibratorPlugin");
               pc.onPluginSelected();
            } catch (Exception ex) {
               app_.logs().showError(ex, "Unable to load Pixel Calibrator Plugin.");
            }
         }
      }

      return transform;
   }



   /**
    * Create the frame
    */
   /**
    *
    */
   public SiteGenerator(Studio app) {
      super();
      app_ = app;
      setMinimumSize(new Dimension(815, 600));
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(final WindowEvent e) {
            saveSettings();
         }
      });

      JPanel contentsPanel = new JPanel(
            new MigLayout("fill, flowx, insets 0, gap 0"));
      add(contentsPanel);
      plate_ = new SBSPlate();

      xyStagePos_ = new Point2D.Double(0.0, 0.0);
      cursorPos_ = new Point2D.Double(0.0, 0.0);

      stageWell_ = "undef";
      cursorWell_ = "undef";

      threePtList_ = null;
      focusPlane_ = null;

      setTitle("HCS Site Generator " + HCSPlugin.VERSION_INFO);
      loadAndRestorePosition(100, 100, 1000, 640);

      platePanel_ = new PlatePanel(plate_, null, this, app);
      contentsPanel.add(platePanel_, "grow, push");

      JPanel sidebar = new JPanel(new MigLayout("flowy, gap 0, insets 0"));
      contentsPanel.add(sidebar, "growprio 0, shrinkprio 200, gap 0, wrap");

      // Mutually-exclusive toggle buttons for what the mouse does.
      final JToggleButton selectWells = new JToggleButton("Select",
            IconLoader.getIcon("/org/micromanager/icons/mouse_cursor_on.png"));
      final JToggleButton moveStage = new JToggleButton("Move",
            IconLoader.getIcon("/org/micromanager/icons/move_hand.png"));

      selectWells.setToolTipText("Click and drag to select wells.");
      selectWells.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            if (selectWells.isSelected()) {
               platePanel_.setTool(PlatePanel.Tool.SELECT);
               selectWells.setIcon(IconLoader.getIcon("/org/micromanager/icons/mouse_cursor_on.png"));
               moveStage.setIcon(IconLoader.getIcon("/org/micromanager/icons/move_hand.png"));
            }
            else {
               selectWells.setIcon(IconLoader.getIcon("/org/micromanager/icons/mouse_cursor.png"));
               moveStage.setIcon(IconLoader.getIcon("/org/micromanager/icons/move_hand_on.png"));
            }
         }
      });
      toolButtonGroup.add(selectWells);
      // set default tool
      platePanel_.setTool(PlatePanel.Tool.SELECT);
      sidebar.add(selectWells, "split 2, alignx center, flowx");

      moveStage.setToolTipText("Click to move the stage");
      moveStage.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (moveStage.isSelected()) {
               platePanel_.setTool(PlatePanel.Tool.MOVE);
               moveStage.setIcon(IconLoader.getIcon("/org/micromanager/icons/move_hand_on.png"));
               selectWells.setIcon(IconLoader.getIcon("/org/micromanager/icons/mouse_cursor.png"));
            }
            else {
               moveStage.setIcon(IconLoader.getIcon("/org/micromanager/icons/move_hand.png"));
               selectWells.setIcon(IconLoader.getIcon("/org/micromanager/icons/mouse_cursor_on.png"));
            }
         }
      });
      toolButtonGroup.add(moveStage);
      selectWells.setSelected(true);
      sidebar.add(moveStage);

      final JLabel plateFormatLabel = new JLabel();
      plateFormatLabel.setAlignmentY(Component.TOP_ALIGNMENT);
      plateFormatLabel.setText("Plate Format:");
      sidebar.add(plateFormatLabel);

      plateIDCombo_ = new JComboBox();
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
      customButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            new CustomSettingsFrame(SiteGenerator.this);
         }
      });

      plateIDCombo_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            plate_.initialize((String) plateIDCombo_.getSelectedItem());
            updateXySpacing();
            PositionList sites = generateSites();
            try {
               platePanel_.refreshImagingSites(sites);
            } catch (HCSException e1) {
               if (app_ != null) {
                  app_.logs().logError(e1);
               }
            }
            platePanel_.repaint();
         }
      });


      FocusListener regeneratePlateOnLossOfFocus = new FocusListener() {
         @Override
         public void focusGained(FocusEvent e) {
         }

         @Override
         public void focusLost(FocusEvent e) {
            regenerate();
         }
      };

      sidebar.add(new JLabel("Imaging Sites"));
      JPanel gridPanel = new JPanel(new MigLayout("flowx, gap 0, insets 0",
               "0[]0[]0", "0[]0[]0"));
      gridPanel.add(new JLabel("Rows"));
      gridPanel.add(new JLabel("Columns"), "wrap");

      rowsField_ = new JTextField(3);
      rowsField_.setText("1");
      gridPanel.add(rowsField_);
      rowsField_.addFocusListener(regeneratePlateOnLossOfFocus);

      sidebar.add(new JLabel("Columns"));
      columnsField_ = new JTextField(3);
      columnsField_.setText("1");
      gridPanel.add(columnsField_);
      columnsField_.addFocusListener(regeneratePlateOnLossOfFocus);
      sidebar.add(gridPanel);

      final JLabel spacingLabel = new JLabel();
      spacingLabel.setText("Spacing [\u00b5m]");
      sidebar.add(spacingLabel);

      spacingFieldX_ = new JTextField(3);
      spacingFieldX_.setText("1000");
      sidebar.add(spacingFieldX_, "split 2, flowx, hidemode 2");

      spacingFieldY_ = new JTextField();
      spacingFieldY_.setText("1000");
      // Take zero space when invisible.
      sidebar.add(spacingFieldY_, "hidemode 2");
      spacingFieldY_.setVisible(false);

      //same size and position like X_
      overlapField_ = new JTextField();
      overlapField_.setText("0");
      // Take zero space when invisible.
      sidebar.add(overlapField_, "hidemode 2");
      overlapField_.setVisible(false);

      sidebar.add(new JLabel("Spacing Rule:"));

      spacingMode_ = new JComboBox(new String[] {
         EQUAL_SPACING, DIFFERENT_SPACING, VIEW_SPACING});
      sidebar.add(spacingMode_, "growx");
      spacingMode_.setSelectedIndex(0);
      spacingMode_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            String mode = (String) spacingMode_.getSelectedItem();
            if (mode.equals(EQUAL_SPACING)) {
               spacingLabel.setText("Spacing [\u00b5m]");
               spacingFieldX_.setVisible(true);
               spacingFieldY_.setVisible(false);
               overlapField_.setVisible(false);
            }
            else if (mode.equals(DIFFERENT_SPACING)) {
               spacingLabel.setText("Spacing X,Y [\u00b5m]");
               spacingFieldX_.setVisible(true);
               spacingFieldY_.setVisible(true);
               overlapField_.setVisible(false);
            }
            else if (mode.equals(VIEW_SPACING)) {
               spacingLabel.setText("Overlap [\u00b5m]");
               overlapField_.setVisible(true);
               spacingFieldX_.setVisible(false);
               spacingFieldY_.setVisible(false);
            }
            else {
               app_.logs().showError("Unrecognized spacing mode " + mode);
            }
            regenerate();
         }
      });

      sidebar.add(new JLabel("Site visit order:"));
      visitOrder_ = new JComboBox(new String[] {SNAKE_ORDER, TYPEWRITER_ORDER});
      visitOrder_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            regenerate();
         }
      });
      sidebar.add(visitOrder_, "growx");

      final JButton refreshButton = new JButton("Refresh",
            IconLoader.getIcon("/org/micromanager/icons/arrow_refresh.png"));
      refreshButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            regenerate();
         }
      });
      sidebar.add(refreshButton, "growx, gaptop 10");

      final JButton calibrateXyButton = new JButton("Calibrate XY...",
            IconLoader.getIcon("/org/micromanager/icons/cog.png"));
      calibrateXyButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            calibrateXY();
         }
      });
      sidebar.add(calibrateXyButton, "growx");


      final JButton setPositionListButton = new JButton("Build MM List",
            IconLoader.getIcon("/org/micromanager/icons/table.png"));
      setPositionListButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            setPositionList();
         }
      });
      sidebar.add(setPositionListButton, "growx");

      chckbxThreePt_ = new JCheckBox("Use 3-Point AF");
      chckbxThreePt_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            platePanel_.repaint();
         }
      });
      sidebar.add(chckbxThreePt_, "gaptop 10");

      JButton btnMarkPt = new JButton("Mark Point",
            IconLoader.getIcon("/org/micromanager/icons/plus.png"));
      btnMarkPt.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            markOnePoint();
         }
      });
      sidebar.add(btnMarkPt, "growx");

      JButton btnSetThreePt = new JButton("Set 3-Point List",
            IconLoader.getIcon("/org/micromanager/icons/asterisk_orange.png"));
      btnSetThreePt.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            setThreePoint();
         }
      });
      sidebar.add(btnSetThreePt, "growx");

      JButton btnAbout = new JButton("About...");
      btnAbout.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            HCSAbout dlgAbout = new HCSAbout(SiteGenerator.this);
            dlgAbout.setVisible(true);
         }
      });
      sidebar.add(btnAbout, "growx");

      statusLabel_ = new JLabel();
      statusLabel_.setBorder(new LineBorder(new Color(0, 0, 0)));
      contentsPanel.add(statusLabel_, "dock south, gap 0");

      loadSettings();
      updateXySpacing();

      PositionList sites = generateSites();
      try {
         platePanel_.refreshImagingSites(sites);
      } catch (HCSException e1) {
         if (app_ != null) {
            app_.logs().logError(e1);
         }
      }
   }

   protected void saveSettings() {
      app_.profile().setString(SiteGenerator.class, PLATE_FORMAT_ID,
            (String) plateIDCombo_.getSelectedItem());
      app_.profile().setString(SiteGenerator.class, SITE_SPACING_X,
            spacingFieldX_.getText().replace(',', '.'));
      app_.profile().setString(SiteGenerator.class, SITE_SPACING_Y,
            spacingFieldY_.getText().replace(',', '.'));
      app_.profile().setString(SiteGenerator.class, SITE_OVERLAP,
            overlapField_.getText());
      app_.profile().setString(SiteGenerator.class, SITE_ROWS,
            rowsField_.getText());
      app_.profile().setString(SiteGenerator.class, SITE_COLS,
            columnsField_.getText());
   }

   protected final void loadSettings() {
      plateIDCombo_.setSelectedItem(app_.getUserProfile().getString(
               SiteGenerator.class, PLATE_FORMAT_ID, SBSPlate.SBS_96_WELL));
      rowsField_.setText(app_.profile().getString(SiteGenerator.class,
               SITE_ROWS, "1"));
      columnsField_.setText(app_.profile().getString(SiteGenerator.class,
               SITE_COLS, "1"));
      spacingFieldX_.setText(app_.profile().getString(SiteGenerator.class,
               SITE_SPACING_X, "200"));
      spacingFieldY_.setText(app_.profile().getString(SiteGenerator.class,
               SITE_SPACING_Y, "200"));
      overlapField_.setText(app_.profile().getString(SiteGenerator.class,
               SITE_OVERLAP, "10"));
   }

   private void setPositionList() {
      WellPositionList[] wpl = platePanel_.getSelectedWellPositions();
      PositionList platePl = new PositionList();
      for (WellPositionList wpl1 : wpl) {
         PositionList pl = PositionList.newInstance(wpl1.getSitePositions());
         for (int j = 0; j < pl.getNumberOfPositions(); j++) {
            MultiStagePosition mpl = pl.getPosition(j);
            // make label unique
            mpl.setLabel(wpl1.getLabel() + "-" + mpl.getLabel());
            if (app_ != null) {
               mpl.setDefaultXYStage(app_.getCMMCore().getXYStageDevice());
               mpl.setDefaultZStage(app_.getCMMCore().getFocusDevice());
            }
            // set the proper XYstage name
            for (int k = 0; k < mpl.size(); k++) {
               StagePosition sp = mpl.get(k);
               if (sp.numAxes == 2) {
                  sp.stageName = mpl.getDefaultXYStage();
               }
            }
            // add Z position if 3-point focus is enabled
            if (useThreePtAF()) {
               if (focusPlane_ == null) {
                  displayError("3-point AF is seleced but 3 points are not defined.");
                  return;
               }
               // add z position from the 3-point plane estimate
               StagePosition sp = new StagePosition();
               sp.numAxes = 1;
               sp.x = focusPlane_.getZPos(mpl.getX(), mpl.getY());
               sp.stageName = mpl.getDefaultZStage();
               mpl.add(sp);
            }
            platePl.addPosition(pl.getPosition(j));
         }
      }

      try {
         if (app_ != null) {
            app_.positions().setPositionList(platePl);
         }
      } catch (MMScriptException e) {
         displayError(e.getMessage());
      }

   }

   /**
    * Mark current position as one point in the 3-pt set
    */
   private void markOnePoint() {
      app_.positions().markCurrentPosition();
   }

   private void setThreePoint() {
      try {
         PositionList plist = app_.positions().getPositionList();
         if (plist.getNumberOfPositions() != 3) {
            displayError("We need exactly three positions to fit AF plane. Please create XY list with 3 positions.");
            return;
         }

         threePtList_ = PositionList.newInstance(plist);
         focusPlane_ = new AFPlane(threePtList_.getPositions());
         chckbxThreePt_.setSelected(true);
         platePanel_.repaint();

      } catch (MMScriptException e) {
         displayError(e.getMessage());
      }
   }


   private PositionList generateSites() {
      int rows = Integer.parseInt(rowsField_.getText());
      int cols = Integer.parseInt(columnsField_.getText());
      boolean isTypewriterMode =
            ((String) visitOrder_.getSelectedItem()).equals(TYPEWRITER_ORDER);
      PositionList sites = new PositionList();
      for (int i = 0; i < rows; i++) {
         // In "snake" mode we go in one X direction on odd rows and the other
         // X direction on even rows; in "typewriter" mode we always use the
         // same X direction.
         int start, end, stepDir;
         if (isTypewriterMode) {
            start = 0;
            end = cols;
            stepDir = 1;
         }
         else {
            boolean isEven = i % 2 == 0;
            start = isEven ? 0 : cols - 1;
            end = isEven ? cols : - 1;
            stepDir = isEven ? 1 : -1;
         }
         for (int j = start; j != end; j += stepDir) {
            double x;
            double y;
            if (cols > 1) {
               x = -cols * xSpacing_ / 2.0 + xSpacing_ * j + xSpacing_ / 2.0;
            } else {
               x = 0.0;
            }

            if (rows > 1) {
               y = -rows * ySpacing_ / 2.0 + ySpacing_ * i + ySpacing_ / 2.0;
            } else {
               y = 0.0;
            }

            MultiStagePosition mps = new MultiStagePosition();
            StagePosition sp = new StagePosition();
            sp.numAxes = 2;
            sp.x = x;
            sp.y = y;

            mps.add(sp);
            sites.addPosition(mps);
         }
      }

      return sites;
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

      String statusTxt = "Cursor: X=" + TextUtils.FMT2.format(cursorPos_.x) + "um, Y=" + TextUtils.FMT2.format(cursorPos_.y) + "um, " + cursorWell_
              + ((useThreePtAF() && focusPlane_ != null) ? ", Z->" + TextUtils.FMT2.format(focusPlane_.getZPos(cursorPos_.x, cursorPos_.y)) + "um" : "")
              + " -- Stage: X=" + TextUtils.FMT2.format(xyStagePos_.x) + "um, Y=" + TextUtils.FMT2.format(xyStagePos_.y) + "um, Z=" + TextUtils.FMT2.format(zStagePos_) + "um, "
              + stageWell_;
      statusLabel_.setText(statusTxt);
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
      if (app_ != null) {
         return app_.getCMMCore().getXYStageDevice();
      } else {
         return SBSPlate.DEFAULT_XYSTAGE_NAME;
      }
   }

   @Override
   public void displayError(String txt) {
      if (app_ !=null) {
         app_.logs().showError(txt, this);
      }
   }

   protected void calibrateXY() {
      if (app_ == null) {
         return;
      }
      int ret = JOptionPane.showConfirmDialog(this, "Manually position the XY stage over the center of the well A01 and press OK",
              "XYStage origin setup", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
      if (ret == JOptionPane.OK_OPTION) {
         try {

            app_.getCMMCore().setAdapterOriginXY(
                  plate_.getFirstWellX(), plate_.getFirstWellY());
            regenerate();
            Point2D.Double pt = app_.getCMMCore().getXYStagePosition();
            JOptionPane.showMessageDialog(this, "XY Stage set at position: " + pt.x + "," + pt.y);
         } catch (Exception e) {
            displayError(e.getMessage());
         }
      }
   }

   private void regenerate() {
      WellPositionList[] selectedWells = platePanel_.getSelectedWellPositions();
      updateXySpacing();
      PositionList sites = generateSites();
      plate_.initialize((String) plateIDCombo_.getSelectedItem());
      try {
         platePanel_.refreshImagingSites(sites);
      } catch (HCSException e) {
         displayError(e.getMessage());
      }

      platePanel_.setSelectedWells(selectedWells);
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

   public void loadCustom(File target) {
      try {
         plate_.load(target.getAbsolutePath());
      } catch (HCSException e) {
         app_.logs().logError(e);
      }
      PositionList sites = generateSites();
      try {
         platePanel_.refreshImagingSites(sites);
      } catch (HCSException e1) {
         if (app_ != null) {
            app_.logs().logError(e1);
         }
      }
      platePanel_.repaint();
   }
}
