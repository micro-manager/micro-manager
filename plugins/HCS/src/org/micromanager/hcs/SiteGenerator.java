package org.micromanager.hcs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.StagePosition;
import org.micromanager.utils.MMFrame;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.TextUtils;

import com.swtdesigner.SwingResourceManager;

import javax.swing.border.LineBorder;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;

import java.awt.Dimension;

/**
 * @author nenad
 *
 */
public class SiteGenerator extends MMFrame implements ParentPlateGUI, MMPlugin {

   private JTextField spacingField_;
   private JTextField columnsField_;
   private JTextField rowsField_;
   private JComboBox plateIDCombo_;
   private static final long serialVersionUID = 1L;
   private final SpringLayout springLayout;
   private SBSPlate plate_;
   private PlatePanel platePanel_;
   private ScriptInterface app_;
   private final Point2D.Double xyStagePos_;
   private double zStagePos_;
   private final Point2D.Double cursorPos_;
   private String stageWell_;
   private String stageSite_;
   private String cursorWell_;
   private String cursorSite_;
   PositionList threePtList_;
   AFPlane focusPlane_;
   private final String PLATE_FORMAT_ID = "plate_format_id";
   private final String SITE_SPACING = "site_spacing_x";
   private final String SITE_ROWS = "site_rows";
   private final String SITE_COLS = "site_cols";
   private final String LOCK_ASPECT = "lock_aspect";
   private final String POINTER_MOVE = "Move";
   private final String POINTER_SELECT = "Select";
   private final String ROOT_DIR = "root";
   private final String PLATE_DIR = "plate";
   private final String INCREMENTAL_AF = "incremental_af";
   public static final String menuName = "HCS Site Generator";
   public static final String tooltipDescription =
           "Generate position list for multi-well plates";
   //private JCheckBox lockAspectCheckBox_;
   private final JLabel statusLabel_;
   static private final String VERSION_INFO = "1.4.1";
   static private final String COPYRIGHT_NOTICE = "Copyright by UCSF, 2013";
   static private final String DESCRIPTION = "Generate imaging site positions for micro-well plates and slides";
   static private final String INFO = "Not available";
   private final JCheckBox chckbxThreePt_;
   private final ButtonGroup toolButtonGroup = new ButtonGroup();
   private JRadioButton rdbtnSelectWells_;
   private JRadioButton rdbtnMoveStage_;

   /*
   public static void main(String args[]) {
      try {
         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
         SiteGenerator dlg = new SiteGenerator();
         dlg.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
         dlg.setVisible(true);
      } catch (Exception e) {
         app_.logError(e);
      }
   }
   */

   /**
    * Create the frame
    */
   /**
    *
    */
   public SiteGenerator() {
      super();
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
      stageSite_ = "undef";
      cursorSite_ = "undef";

      threePtList_ = null;
      focusPlane_ = null;

      setTitle("HCS Site Generator " + VERSION_INFO);
      loadAndRestorePosition(100, 100, 1000, 640);

      platePanel_ = new PlatePanel(plate_, null, this);
      springLayout.putConstraint(SpringLayout.NORTH, platePanel_, 5, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, platePanel_, -136, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, platePanel_, 5, SpringLayout.WEST, getContentPane());
      try {
         platePanel_.setApp(app_);
      } catch (HCSException e1) {
         app_.logError(e1);
      }

      getContentPane().add(platePanel_);

      plateIDCombo_ = new JComboBox();
      plateIDCombo_.setAlignmentX(Component.LEFT_ALIGNMENT);
      springLayout.putConstraint(SpringLayout.WEST, plateIDCombo_, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, plateIDCombo_, -4, SpringLayout.EAST, getContentPane());
      getContentPane().add(plateIDCombo_);
      plateIDCombo_.addItem(SBSPlate.SBS_24_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_48_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_96_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_384_WELL);
      plateIDCombo_.addItem(SBSPlate.SLIDE_HOLDER);

      //comboBox.addItem(SBSPlate.CUSTOM);
      plateIDCombo_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            plate_.initialize((String) plateIDCombo_.getSelectedItem());
            PositionList sites = generateSites(Integer.parseInt(rowsField_.getText()), Integer.parseInt(columnsField_.getText()),
                    Double.parseDouble(spacingField_.getText()));
            try {
               platePanel_.refreshImagingSites(sites);
            } catch (HCSException e1) {
               app_.logError(e1);
            }
            platePanel_.repaint();
         }
      });

      final JLabel plateFormatLabel = new JLabel();
      springLayout.putConstraint(SpringLayout.NORTH, plateIDCombo_, 6, SpringLayout.SOUTH, plateFormatLabel);
      springLayout.putConstraint(SpringLayout.NORTH, plateFormatLabel, 80, SpringLayout.NORTH, getContentPane());
      plateFormatLabel.setAlignmentY(Component.TOP_ALIGNMENT);
      springLayout.putConstraint(SpringLayout.SOUTH, plateFormatLabel, 95, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, plateFormatLabel, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, plateFormatLabel, -9, SpringLayout.EAST, getContentPane());
      plateFormatLabel.setText("Plate format");
      getContentPane().add(plateFormatLabel);

      rowsField_ = new JTextField();
      rowsField_.setText("1");
      getContentPane().add(rowsField_);
      springLayout.putConstraint(SpringLayout.EAST, rowsField_, -65, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, rowsField_, -105, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, rowsField_, 215, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, rowsField_, 195, SpringLayout.NORTH, getContentPane());

      final JLabel imagingSitesLabel = new JLabel();
      springLayout.putConstraint(SpringLayout.SOUTH, plateIDCombo_, -31, SpringLayout.NORTH, imagingSitesLabel);
      springLayout.putConstraint(SpringLayout.NORTH, imagingSitesLabel, 155, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, imagingSitesLabel, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, imagingSitesLabel, -24, SpringLayout.EAST, getContentPane());
      imagingSitesLabel.setText("Imaging Sites");
      getContentPane().add(imagingSitesLabel);

      columnsField_ = new JTextField();
      columnsField_.setText("1");
      getContentPane().add(columnsField_);
      springLayout.putConstraint(SpringLayout.SOUTH, columnsField_, 215, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, columnsField_, 195, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, columnsField_, -20, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, columnsField_, -60, SpringLayout.EAST, getContentPane());

      spacingField_ = new JTextField();
      spacingField_.setText("1000");
      getContentPane().add(spacingField_);
      springLayout.putConstraint(SpringLayout.EAST, spacingField_, -65, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, spacingField_, -105, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, spacingField_, 260, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, spacingField_, 240, SpringLayout.NORTH, getContentPane());

      final JLabel rowsColumnsLabel = new JLabel();
      springLayout.putConstraint(SpringLayout.SOUTH, imagingSitesLabel, -4, SpringLayout.NORTH, rowsColumnsLabel);
      springLayout.putConstraint(SpringLayout.NORTH, rowsColumnsLabel, 173, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, rowsColumnsLabel, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.SOUTH, rowsColumnsLabel, -6, SpringLayout.NORTH, rowsField_);
      springLayout.putConstraint(SpringLayout.EAST, rowsColumnsLabel, -40, SpringLayout.EAST, getContentPane());
      rowsColumnsLabel.setText("Rows, Columns");
      getContentPane().add(rowsColumnsLabel);

      final JLabel spacingLabel = new JLabel();
      spacingLabel.setText("Spacing [um]");
      getContentPane().add(spacingLabel);
      springLayout.putConstraint(SpringLayout.EAST, spacingLabel, -20, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, spacingLabel, -105, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, spacingLabel, 236, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, spacingLabel, 220, SpringLayout.NORTH, getContentPane());

      final JButton refreshButton = new JButton();
      springLayout.putConstraint(SpringLayout.NORTH, refreshButton, 23, SpringLayout.SOUTH, spacingField_);
      springLayout.putConstraint(SpringLayout.WEST, refreshButton, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, refreshButton, -4, SpringLayout.EAST, getContentPane());
      refreshButton.setIcon(SwingResourceManager.getIcon(SiteGenerator.class, "/org/micromanager/icons/arrow_refresh.png"));
      refreshButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            regenerate();
         }
      });
      refreshButton.setText("Refresh");
      getContentPane().add(refreshButton);

      final JButton calibrateXyButton = new JButton();
      springLayout.putConstraint(SpringLayout.WEST, calibrateXyButton, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.SOUTH, calibrateXyButton, 365, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, calibrateXyButton, -4, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, calibrateXyButton, 340, SpringLayout.NORTH, getContentPane());
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
      springLayout.putConstraint(SpringLayout.SOUTH, refreshButton, -6, SpringLayout.NORTH, setPositionListButton);
      springLayout.putConstraint(SpringLayout.WEST, setPositionListButton, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.NORTH, setPositionListButton, 314, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, setPositionListButton, 337, SpringLayout.NORTH, getContentPane());
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

//		lockAspectCheckBox_ = new JCheckBox();
//		springLayout.putConstraint(SpringLayout.NORTH, plateFormatLabel, 6, SpringLayout.SOUTH, lockAspectCheckBox_);
//		springLayout.putConstraint(SpringLayout.SOUTH, plateFormatLabel, 20, SpringLayout.SOUTH, lockAspectCheckBox_);
//		lockAspectCheckBox_.addActionListener(new ActionListener() {
//			public void actionPerformed(final ActionEvent e) {
//				platePanel_.setLockAspect(lockAspectCheckBox_.isSelected());
//				platePanel_.repaint();
//			}
//		});
//		lockAspectCheckBox_.setText("Lock aspect");
//		getContentPane().add(lockAspectCheckBox_);
//		lockAspectCheckBox_.setSelected(true);
//		springLayout.putConstraint(SpringLayout.SOUTH, lockAspectCheckBox_, 73, SpringLayout.NORTH, getContentPane());
//		springLayout.putConstraint(SpringLayout.NORTH, lockAspectCheckBox_, 50, SpringLayout.NORTH, getContentPane());
//		springLayout.putConstraint(SpringLayout.EAST, lockAspectCheckBox_, -14, SpringLayout.EAST, getContentPane());
//		springLayout.putConstraint(SpringLayout.WEST, lockAspectCheckBox_, -115, SpringLayout.EAST, getContentPane());

//		rootDirField_ = new JTextField();
//		getContentPane().add(rootDirField_);
//		springLayout.putConstraint(SpringLayout.EAST, rootDirField_, -189, SpringLayout.EAST, getContentPane());
//		springLayout.putConstraint(SpringLayout.WEST, rootDirField_, 5, SpringLayout.WEST, getContentPane());
//		springLayout.putConstraint(SpringLayout.SOUTH, rootDirField_, -29, SpringLayout.SOUTH, getContentPane());
//		springLayout.putConstraint(SpringLayout.NORTH, rootDirField_, -49, SpringLayout.SOUTH, getContentPane());

//		plateNameField_ = new JTextField();
//		getContentPane().add(plateNameField_);
//		springLayout.putConstraint(SpringLayout.EAST, plateNameField_, -4, SpringLayout.EAST, getContentPane());
//		springLayout.putConstraint(SpringLayout.WEST, plateNameField_, -131, SpringLayout.EAST, getContentPane());
//		springLayout.putConstraint(SpringLayout.SOUTH, plateNameField_, -29, SpringLayout.SOUTH, getContentPane());
//		springLayout.putConstraint(SpringLayout.NORTH, plateNameField_, -49, SpringLayout.SOUTH, getContentPane());

//		final JLabel rootDirectoryLabel = new JLabel();
//		rootDirectoryLabel.setText("Root directory");
//		getContentPane().add(rootDirectoryLabel);
//		springLayout.putConstraint(SpringLayout.EAST, rootDirectoryLabel, 110, SpringLayout.WEST, getContentPane());
//		springLayout.putConstraint(SpringLayout.WEST, rootDirectoryLabel, 10, SpringLayout.WEST, getContentPane());
//		springLayout.putConstraint(SpringLayout.SOUTH, rootDirectoryLabel, -52, SpringLayout.SOUTH, getContentPane());
//		springLayout.putConstraint(SpringLayout.NORTH, rootDirectoryLabel, -66, SpringLayout.SOUTH, getContentPane());

//		final JLabel plateNameLabel = new JLabel();
//		plateNameLabel.setText("Plate name");
//		getContentPane().add(plateNameLabel);
//		springLayout.putConstraint(SpringLayout.EAST, plateNameLabel, -4, SpringLayout.EAST, getContentPane());
//		springLayout.putConstraint(SpringLayout.WEST, plateNameLabel, -131, SpringLayout.EAST, getContentPane());
//		springLayout.putConstraint(SpringLayout.SOUTH, plateNameLabel, -52, SpringLayout.SOUTH, getContentPane());
//		springLayout.putConstraint(SpringLayout.NORTH, plateNameLabel, -66, SpringLayout.SOUTH, getContentPane());
//
//		final JButton button = new JButton();
//		button.addActionListener(new ActionListener() {
//			public void actionPerformed(final ActionEvent e) {
//				setRootDirectory();
//			}
//		});
//		button.setToolTipText("browse root directory");
//		button.setText("...");
//		getContentPane().add(button);
//		springLayout.putConstraint(SpringLayout.SOUTH, button, -27, SpringLayout.SOUTH, getContentPane());
//		springLayout.putConstraint(SpringLayout.NORTH, button, -50, SpringLayout.SOUTH, getContentPane());
//		springLayout.putConstraint(SpringLayout.EAST, button, -136, SpringLayout.EAST, getContentPane());
//		springLayout.putConstraint(SpringLayout.WEST, button, -184, SpringLayout.EAST, getContentPane());

      chckbxThreePt_ = new JCheckBox("Use 3-Point AF");
      springLayout.putConstraint(SpringLayout.NORTH, chckbxThreePt_, 419, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, chckbxThreePt_, 6, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, chckbxThreePt_, -4, SpringLayout.EAST, getContentPane());
      chckbxThreePt_.addActionListener(new ActionListener() {
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

      //

      loadSettings();

      PositionList sites = generateSites(Integer.parseInt(rowsField_.getText()), Integer.parseInt(columnsField_.getText()),
              Double.parseDouble(spacingField_.getText()));
      plate_.initialize((String) plateIDCombo_.getSelectedItem());
      try {
         platePanel_.refreshImagingSites(sites);
      } catch (HCSException e1) {
         app_.logError(e1);
      }

   }

   protected void saveSettings() {
      Preferences prefs = getPrefsNode();
      prefs.put(PLATE_FORMAT_ID, (String) plateIDCombo_.getSelectedItem());
      prefs.put(SITE_SPACING, spacingField_.getText());
      prefs.put(SITE_ROWS, rowsField_.getText());
      prefs.put(SITE_COLS, columnsField_.getText());
      //prefs.putBoolean(LOCK_ASPECT, lockAspectCheckBox_.isSelected());
      //prefs.put(PLATE_DIR, plateNameField_.getText());
      //prefs.put(ROOT_DIR, rootDirField_.getText());
   }

   protected final void loadSettings() {
      Preferences prefs = getPrefsNode();
      plateIDCombo_.setSelectedItem(prefs.get(PLATE_FORMAT_ID, SBSPlate.SBS_96_WELL));
      spacingField_.setText(prefs.get(SITE_SPACING, "200"));
      rowsField_.setText(prefs.get(SITE_ROWS, "1"));
      columnsField_.setText(prefs.get(SITE_COLS, "1"));
      //lockAspectCheckBox_.setSelected(prefs.getBoolean(LOCK_ASPECT, true));
      //platePanel_.setLockAspect(lockAspectCheckBox_.isSelected());
      //plateNameField_.setText(prefs.get(PLATE_DIR, "plate"));
      //rootDirField_.setText(prefs.get(ROOT_DIR, "C:/ScreeningData"));
      boolean incaf = prefs.getBoolean(INCREMENTAL_AF, false);
   }

   private void setPositionList() {
      WellPositionList[] wpl = platePanel_.getSelectedWellPositions();
      PositionList platePl = new PositionList();
      for (int i = 0; i < wpl.length; i++) {
         PositionList pl = PositionList.newInstance(wpl[i].getSitePositions());
         for (int j = 0; j < pl.getNumberOfPositions(); j++) {
            MultiStagePosition mpl = pl.getPosition(j);

            // make label unique
            mpl.setLabel(wpl[i].getLabel() + "-" + mpl.getLabel());
            if (app_ != null) {
               mpl.setDefaultXYStage(app_.getXYStageName());
               mpl.setDefaultZStage(app_.getMMCore().getFocusDevice());
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
            app_.setPositionList(platePl);
         }
      } catch (MMScriptException e) {
         displayError(e.getMessage());
      }

   }

   /**
    * Mark current position as one point in the 3-pt set
    */
   private void markOnePoint() {
      app_.markCurrentPosition();
   }

   private void setThreePoint() {
      try {
         PositionList plist = app_.getPositionList();
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

   private PositionList generateSites(int rows, int cols, double spacing) {
      PositionList sites = new PositionList();
      System.out.println("# Rows : " + rows + ", # Cols : " + cols + " ,spacing = " + spacing);
      for (int i = 0; i < rows; i++) {
         for (int j = 0; j < cols; j++) {
            double x;
            double y;
            if (cols > 1) {
               x = -cols * spacing / 2.0 + spacing * j + spacing / 2.0;
            } else {
               x = 0.0;
            }

            if (rows > 1) {
               y = -rows * spacing / 2.0 + spacing * i + spacing / 2.0;
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
         }
      }

      return sites;
   }

   @Override
   public void updatePointerXYPosition(double x, double y, String wellLabel, String siteLabel) {
      cursorPos_.x = x;
      cursorPos_.y = y;
      cursorWell_ = wellLabel;
      cursorSite_ = siteLabel;

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
   public void updateStagePositions(double x, double y, double z, String wellLabel, String siteLabel) {
      xyStagePos_.x = x;
      xyStagePos_.y = y;
      zStagePos_ = z;
      stageWell_ = wellLabel;
      stageSite_ = siteLabel;

      displayStatus();
   }

   @Override
   public String getXYStageName() {
      if (app_ != null) {
         return app_.getXYStageName();
      } else {
         return SBSPlate.DEFAULT_XYSTAGE_NAME;
      }
   }

   /*
    private void setRootDirectory() {
    JFileChooser fc = new JFileChooser();
    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    fc.setCurrentDirectory(new File(rootDirField_.getText()));
    int retVal = fc.showOpenDialog(this);
    if (retVal == JFileChooser.APPROVE_OPTION) {
    rootDirField_.setText(fc.getSelectedFile().getAbsolutePath());
    }
    }
    */
   @Override
   public void displayError(String txt) {
      app_.showError(txt, this);
   }

   protected void calibrateXY() {
      if (app_ == null) {
         return;
      }
      int ret = JOptionPane.showConfirmDialog(this, "Manually position the XY stage over the center of the well A01 and press OK",
              "XYStage origin setup", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
      if (ret == JOptionPane.OK_OPTION) {
         try {

            app_.setXYOrigin(plate_.getFirstWellX(), plate_.getFirstWellY());
            regenerate();
            Point2D.Double pt = app_.getXYStagePosition();
            JOptionPane.showMessageDialog(this, "XY Stage set at position: " + pt.x + "," + pt.y);
         } catch (MMScriptException e) {
            displayError(e.getMessage());
         }
      }
   }

   private void regenerate() {
      PositionList sites = generateSites(Integer.parseInt(rowsField_.getText()), Integer.parseInt(columnsField_.getText()),
              Double.parseDouble(spacingField_.getText()));
      plate_.initialize((String) plateIDCombo_.getSelectedItem());
      try {
         platePanel_.refreshImagingSites(sites);
      } catch (HCSException e) {
         displayError(e.getMessage());
      }
      platePanel_.repaint();
   }

   public void setApp(ScriptInterface app) {
      app_ = app;
      try {
         platePanel_.setApp(app);
      } catch (HCSException e) {
         // commented out to avod displaying this error at startup
         //displayError(e.getMessage());
      }
   }

   public void configurationChanged() {
      // TODO:
   }

   @Override
   public String getCopyright() {
      return COPYRIGHT_NOTICE;
   }

   @Override
   public String getDescription() {
      return DESCRIPTION;
   }

   @Override
   public String getInfo() {
      return INFO;
   }

   @Override
   public String getVersion() {
      return VERSION_INFO;
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
}
