package org.micromanager.hcs;

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
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

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
import mmcorej.CMMCore;

import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;

import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * @author nenad
 *
 */
public class SiteGenerator extends MMFrame implements ParentPlateGUI {

   private CMMCore core_;
   private JTextField spacingFieldX_;
   private JTextField spacingFieldY_;
   private JTextField overlapField_;
   private JTextField columnsField_;
   private JTextField rowsField_;
   private JComboBox plateIDCombo_;
   private static final long serialVersionUID = 1L;
   private final SpringLayout springLayout;
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
   private JRadioButton rdbtnSelectWells_;
   private JRadioButton rdbtnMoveStage_;
   private final ButtonGroup spacingButtonGroup = new ButtonGroup();
   private JRadioButton rdbtnEqualXYSpacing_;
   private JRadioButton rdbtnDifferentXYSpacing_;
   private JRadioButton rdbtnFieldOfViewSpacing_;

   private double xSpacing = 0.0;
   private double ySpacing = 0.0;


   private void updateXySpacing() {
     if (rdbtnFieldOfViewSpacing_.isSelected()) {
       core_ = app_.getCMMCore();
       long width  = core_.getImageWidth();
       long height = core_.getImageHeight();
       double cameraXFieldOfView = core_.getPixelSizeUm() * width;
       double cameraYFieldOfView = core_.getPixelSizeUm() * height;
       double overlap = Double.parseDouble(overlapField_.getText().replace(',', '.'));
       xSpacing = cameraXFieldOfView - overlap;
       ySpacing = cameraYFieldOfView - overlap;
     }
     else {
       xSpacing = Double.parseDouble(spacingFieldX_.getText().replace(',','.'));
       if (rdbtnEqualXYSpacing_.isSelected()) ySpacing = xSpacing;
       else ySpacing = Double.parseDouble(spacingFieldY_.getText().replace(',', '.'));
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

      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
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
      springLayout.putConstraint(SpringLayout.NORTH, platePanel_, 5, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, platePanel_, -136, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, platePanel_, 5, SpringLayout.WEST, getContentPane());

      getContentPane().add(platePanel_);

      plateIDCombo_ = new JComboBox();
      plateIDCombo_.setAlignmentX(Component.LEFT_ALIGNMENT);
      springLayout.putConstraint(SpringLayout.WEST, plateIDCombo_, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, plateIDCombo_, -4, SpringLayout.EAST, getContentPane());
      getContentPane().add(plateIDCombo_);
      plateIDCombo_.addItem(SBSPlate.SBS_6_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_12_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_24_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_48_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_96_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_384_WELL);
      plateIDCombo_.addItem(SBSPlate.SLIDE_HOLDER);
      plateIDCombo_.addItem(SBSPlate.LOAD_CUSTOM);

      JButton customButton = new JButton("Create Custom");
      springLayout.putConstraint(SpringLayout.NORTH, customButton, 105, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, customButton, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, customButton, -4, SpringLayout.EAST, getContentPane());
      getContentPane().add(customButton);
      customButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            new CustomSettingsFrame(SiteGenerator.this);
         }
      });

      //comboBox.addItem(SBSPlate.CUSTOM);
      plateIDCombo_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            plate_.initialize((String) plateIDCombo_.getSelectedItem());
            updateXySpacing();
            PositionList sites = generateSites(
               Integer.parseInt(rowsField_.getText()),
               Integer.parseInt(columnsField_.getText()),
               xSpacing, ySpacing);
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

      final JLabel plateFormatLabel = new JLabel();
      springLayout.putConstraint(SpringLayout.NORTH, plateIDCombo_, 6, SpringLayout.SOUTH, plateFormatLabel);
      springLayout.putConstraint(SpringLayout.NORTH, plateFormatLabel, 60, SpringLayout.NORTH, getContentPane());
      plateFormatLabel.setAlignmentY(Component.TOP_ALIGNMENT);
      springLayout.putConstraint(SpringLayout.SOUTH, plateFormatLabel, 75, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, plateFormatLabel, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, plateFormatLabel, -9, SpringLayout.EAST, getContentPane());
      plateFormatLabel.setText("Plate format");
      getContentPane().add(plateFormatLabel);

      rowsField_ = new JTextField();
      rowsField_.setText("1");
      getContentPane().add(rowsField_);
      springLayout.putConstraint(SpringLayout.EAST, rowsField_, -85, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, rowsField_, -125, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, rowsField_, 195, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, rowsField_, 175, SpringLayout.NORTH, getContentPane());
      rowsField_.addFocusListener(regeneratePlateOnLossOfFocus);
      
      final JLabel imagingSitesLabel = new JLabel();
      springLayout.putConstraint(SpringLayout.SOUTH, plateIDCombo_, -31, SpringLayout.NORTH, imagingSitesLabel);
      springLayout.putConstraint(SpringLayout.NORTH, imagingSitesLabel, 135, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, imagingSitesLabel, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, imagingSitesLabel, -24, SpringLayout.EAST, getContentPane());
      imagingSitesLabel.setText("Imaging Sites");
      getContentPane().add(imagingSitesLabel);
      
      columnsField_ = new JTextField();
      columnsField_.setText("1");
      getContentPane().add(columnsField_);
      springLayout.putConstraint(SpringLayout.SOUTH, columnsField_, 195, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, columnsField_, 175, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, columnsField_, -40, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, columnsField_, -80, SpringLayout.EAST, getContentPane());
      columnsField_.addFocusListener(regeneratePlateOnLossOfFocus);
     
      spacingFieldX_ = new JTextField();
      spacingFieldX_.setText("1000");
      getContentPane().add(spacingFieldX_);
      springLayout.putConstraint(SpringLayout.EAST, spacingFieldX_, -85, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, spacingFieldX_, -125, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, spacingFieldX_, 240, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, spacingFieldX_, 220, SpringLayout.NORTH, getContentPane());

      spacingFieldY_ = new JTextField();
      spacingFieldY_.setText("1000");
      getContentPane().add(spacingFieldY_);
      springLayout.putConstraint(SpringLayout.SOUTH, spacingFieldY_, 240, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, spacingFieldY_, 220, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, spacingFieldY_, -40, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, spacingFieldY_, -80, SpringLayout.EAST, getContentPane());
      spacingFieldY_.setVisible(false);

      //same size and position like X_
      overlapField_ = new JTextField();
      overlapField_.setText("0");
      getContentPane().add(overlapField_);
      springLayout.putConstraint(SpringLayout.EAST, overlapField_, -65, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, overlapField_, -105, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, overlapField_, 240, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, overlapField_, 220, SpringLayout.NORTH, getContentPane());
      overlapField_.setVisible(false);

      
      final JLabel rowsColumnsLabel = new JLabel();
      springLayout.putConstraint(SpringLayout.NORTH, rowsColumnsLabel, 153, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, rowsColumnsLabel, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, rowsColumnsLabel, -30, SpringLayout.EAST, getContentPane());
      rowsColumnsLabel.setText("Rows, Columns");
      getContentPane().add(rowsColumnsLabel);

      final JLabel spacingLabel = new JLabel();
      spacingLabel.setText("Spacing [um]");
      getContentPane().add(spacingLabel);
      springLayout.putConstraint(SpringLayout.EAST, spacingLabel, -40, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, spacingLabel, -125, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, spacingLabel, 216, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, spacingLabel, 200, SpringLayout.NORTH, getContentPane());

      final JButton refreshButton = new JButton();
      springLayout.putConstraint(SpringLayout.NORTH, refreshButton, 320, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, refreshButton, 345, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, refreshButton,  6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, refreshButton, -4, SpringLayout.EAST, getContentPane());
      refreshButton.setIcon(SwingResourceManager.getIcon(SiteGenerator.class, "/org/micromanager/icons/arrow_refresh.png"));
      refreshButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            regenerate();
         }
      });
      refreshButton.setText("Refresh");
      getContentPane().add(refreshButton);

      final JButton calibrateXyButton = new JButton();
      springLayout.putConstraint(SpringLayout.WEST, calibrateXyButton, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.SOUTH, calibrateXyButton, 405, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, calibrateXyButton, -4, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, calibrateXyButton, 380, SpringLayout.NORTH, getContentPane());
      calibrateXyButton.setIcon(SwingResourceManager.getIcon(SiteGenerator.class, "/org/micromanager/icons/cog.png"));
      calibrateXyButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            calibrateXY();
         }
      });
      calibrateXyButton.setText("Calibrate XY...");
      getContentPane().add(calibrateXyButton);


      final JButton setPositionListButton = new JButton();
      springLayout.putConstraint(SpringLayout.WEST, setPositionListButton, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.NORTH, setPositionListButton, 350, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, setPositionListButton, 375, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, setPositionListButton, -4, SpringLayout.EAST, getContentPane());
      setPositionListButton.setIcon(SwingResourceManager.getIcon(SiteGenerator.class, "/org/micromanager/icons/table.png"));
      setPositionListButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            setPositionList();
         }
      });
      setPositionListButton.setText("Build MM List");
      getContentPane().add(setPositionListButton);

      statusLabel_ = new JLabel();
      springLayout.putConstraint(SpringLayout.SOUTH, platePanel_, -6, SpringLayout.NORTH, statusLabel_);
      springLayout.putConstraint(SpringLayout.SOUTH, statusLabel_, -5, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, statusLabel_, -25, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, statusLabel_, 5, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, statusLabel_, -4, SpringLayout.EAST, getContentPane());
      statusLabel_.setBorder(new LineBorder(new Color(0, 0, 0)));
      getContentPane().add(statusLabel_);

      chckbxThreePt_ = new JCheckBox("Use 3-Point AF");
      springLayout.putConstraint(SpringLayout.NORTH, chckbxThreePt_, 419, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, chckbxThreePt_, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, chckbxThreePt_, -4, SpringLayout.EAST, getContentPane());
      chckbxThreePt_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            platePanel_.repaint();
         }
      });
      getContentPane().add(chckbxThreePt_);

      JButton btnMarkPt = new JButton("Mark Point");
      springLayout.putConstraint(SpringLayout.SOUTH, chckbxThreePt_, -6, SpringLayout.NORTH, btnMarkPt);
      springLayout.putConstraint(SpringLayout.NORTH, btnMarkPt, 440, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, btnMarkPt, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.SOUTH, btnMarkPt, 465, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, btnMarkPt, -4, SpringLayout.EAST, getContentPane());
      btnMarkPt.setIcon(SwingResourceManager.getIcon(SiteGenerator.class, "/org/micromanager/icons/plus.png"));
      btnMarkPt.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            markOnePoint();
         }
      });
      getContentPane().add(btnMarkPt);

      JButton btnSetThreePt = new JButton("Set 3-Point List");
      springLayout.putConstraint(SpringLayout.NORTH, btnSetThreePt, 6, SpringLayout.SOUTH, btnMarkPt);
      springLayout.putConstraint(SpringLayout.WEST, btnSetThreePt, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.SOUTH, btnSetThreePt, 31, SpringLayout.SOUTH, btnMarkPt);
      //springLayout.putConstraint(SpringLayout.SOUTH, btnSetThreePt, -67, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, btnSetThreePt, -4, SpringLayout.EAST, getContentPane());
      btnSetThreePt.setIcon(SwingResourceManager.getIcon(SiteGenerator.class, "/org/micromanager/icons/asterisk_orange.png"));
      btnSetThreePt.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            setThreePoint();
         }
      });
      getContentPane().add(btnSetThreePt);

      rdbtnSelectWells_ = new JRadioButton("Select Wells");
      rdbtnSelectWells_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            if (rdbtnSelectWells_.isSelected()) {
               platePanel_.setTool(PlatePanel.Tool.SELECT);
            }
         }
      });
      toolButtonGroup.add(rdbtnSelectWells_);
      rdbtnSelectWells_.setSelected(true);
      // set default tool
      platePanel_.setTool(PlatePanel.Tool.SELECT);
      springLayout.putConstraint(SpringLayout.NORTH, rdbtnSelectWells_, 10, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, rdbtnSelectWells_, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, rdbtnSelectWells_, 0, SpringLayout.EAST, plateIDCombo_);
      getContentPane().add(rdbtnSelectWells_);

      rdbtnMoveStage_ = new JRadioButton("Move Stage");
      rdbtnMoveStage_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (rdbtnMoveStage_.isSelected()) {
               platePanel_.setTool(PlatePanel.Tool.MOVE);
            }
         }
      });
      toolButtonGroup.add(rdbtnMoveStage_);
      rdbtnSelectWells_.setSelected(false);
      springLayout.putConstraint(SpringLayout.NORTH, rdbtnMoveStage_, 2, SpringLayout.SOUTH, rdbtnSelectWells_);
      springLayout.putConstraint(SpringLayout.WEST, rdbtnMoveStage_, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, rdbtnMoveStage_, 0, SpringLayout.EAST, plateIDCombo_);
      getContentPane().add(rdbtnMoveStage_);

      rdbtnEqualXYSpacing_ = new JRadioButton("Equal XY Spacing");
      rdbtnEqualXYSpacing_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            if (rdbtnEqualXYSpacing_.isSelected()) {
                spacingLabel.setText("Spacing [um]");
                spacingFieldX_.setVisible(true);
                spacingFieldY_.setVisible(false);
                overlapField_.setVisible(false);
            }
         }
      });
      
      rdbtnDifferentXYSpacing_ = new JRadioButton("Different XY Spacing");
      rdbtnDifferentXYSpacing_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            if (rdbtnDifferentXYSpacing_.isSelected()) {
                spacingLabel.setText("Spacing X,Y [um]");
                spacingFieldX_.setVisible(true);
                spacingFieldY_.setVisible(true);
                overlapField_.setVisible(false);
            }
         }
      });
      
      rdbtnFieldOfViewSpacing_ = new JRadioButton("Field of View Spacing");
      rdbtnFieldOfViewSpacing_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            if (rdbtnFieldOfViewSpacing_.isSelected()) {
                spacingLabel.setText("Overlap [um]");
                overlapField_.setVisible(true);
                spacingFieldX_.setVisible(false);
                spacingFieldY_.setVisible(false);
            }
         }
      });
      
      spacingButtonGroup.add(rdbtnEqualXYSpacing_);
      spacingButtonGroup.add(rdbtnDifferentXYSpacing_);
      spacingButtonGroup.add(rdbtnFieldOfViewSpacing_);
      
      rdbtnEqualXYSpacing_.setSelected(true);
      rdbtnDifferentXYSpacing_.setSelected(false);      
      rdbtnFieldOfViewSpacing_.setSelected(false);
      
      springLayout.putConstraint(SpringLayout.NORTH, rdbtnEqualXYSpacing_, 250, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, rdbtnEqualXYSpacing_, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, rdbtnEqualXYSpacing_, 0, SpringLayout.EAST, plateIDCombo_);
      getContentPane().add(rdbtnEqualXYSpacing_);
      
      springLayout.putConstraint(SpringLayout.NORTH, rdbtnDifferentXYSpacing_, 0, SpringLayout.SOUTH, rdbtnEqualXYSpacing_);
      springLayout.putConstraint(SpringLayout.WEST, rdbtnDifferentXYSpacing_, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, rdbtnDifferentXYSpacing_, 0, SpringLayout.EAST, plateIDCombo_);
      getContentPane().add(rdbtnDifferentXYSpacing_);
      
      springLayout.putConstraint(SpringLayout.NORTH, rdbtnFieldOfViewSpacing_, 0, SpringLayout.SOUTH, rdbtnDifferentXYSpacing_);
      springLayout.putConstraint(SpringLayout.WEST, rdbtnFieldOfViewSpacing_, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, rdbtnFieldOfViewSpacing_, 0, SpringLayout.EAST, plateIDCombo_);
      getContentPane().add(rdbtnFieldOfViewSpacing_);
      
      JButton btnAbout = new JButton("About...");
      btnAbout.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            HCSAbout dlgAbout = new HCSAbout(SiteGenerator.this);
            dlgAbout.setVisible(true);
         }
      });
      springLayout.putConstraint(SpringLayout.SOUTH, btnAbout, 0, SpringLayout.SOUTH, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, btnAbout, 0, SpringLayout.EAST, plateIDCombo_);
      getContentPane().add(btnAbout);

      loadSettings();
      updateXySpacing();

      PositionList sites = generateSites(Integer.parseInt(rowsField_.getText()), Integer.parseInt(columnsField_.getText()),
            xSpacing, ySpacing);
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
            app_.compat().setPositionList(platePl);
         }
      } catch (MMScriptException e) {
         displayError(e.getMessage());
      }

   }

   /**
    * Mark current position as one point in the 3-pt set
    */
   private void markOnePoint() {
      app_.compat().markCurrentPosition();
   }

   private void setThreePoint() {
      try {
         PositionList plist = app_.compat().getPositionList();
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

   private PositionList generateSites(int rows, int cols,
         double spacingX, double spacingY) {
      PositionList sites = new PositionList();
      System.out.println("# Rows : " + rows + ", # Cols : " + cols +
            ", spacingX = " + spacingX + ", spacingY = " + spacingY);
      for (int i = 0; i < rows; i++) {
         // create snake-like pattern inside the well:
         boolean isEven = i % 2 == 0;
         int start = isEven ? 0 : cols - 1;
         int end = isEven ? cols : - 1;
         int j = start;
         // instead of using a for loop, cycle backwards on odd rows
         while ( (isEven && j < end) || (!isEven && j > end)  ) {
            double x;
            double y;
            if (cols > 1) {
               x = -cols * spacingX / 2.0 + spacingX * j + spacingX / 2.0;
            } else {
               x = 0.0;
            }

            if (rows > 1) {
               y = -rows * spacingY / 2.0 + spacingY * i + spacingY / 2.0;
            } else {
               y = 0.0;
            }

            MultiStagePosition mps = new MultiStagePosition();
            StagePosition sp = new StagePosition();
            sp.numAxes = 2;
            sp.x = x;
            sp.y = y;
            System.out.println("(" + i + "," + j + ") = " + x + "," + y);

            mps.add(sp);
            sites.addPosition(mps);
            if (isEven) {
               j++;
            } else {
               j--;
            }

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
      PositionList sites = generateSites(Integer.parseInt(rowsField_.getText()), Integer.parseInt(columnsField_.getText()),
              xSpacing, ySpacing);
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
      PositionList sites = generateSites(Integer.parseInt(rowsField_.getText()), Integer.parseInt(columnsField_.getText()),
              xSpacing, ySpacing);
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
