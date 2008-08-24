package com.imaging100x.hcs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpringLayout;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import java.awt.geom.Point2D;

import org.micromanager.api.ScriptInterface;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.metadata.WellAcquisitionData;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;
import org.micromanager.navigation.StagePosition;
import org.micromanager.utils.MMDialog;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.TextUtils;

public class PlateEditor extends MMDialog implements ParentPlateGUI {
   private JTextField plateNameField_;
   private JTextField rootDirField_;
   private JTextField spacingField_;
   private JTextField columnsField_;
   private JTextField rowsField_;
   private JComboBox plateIDCombo_;
   private static final long serialVersionUID = 1L;
   private SpringLayout springLayout;
   private SBSPlate plate_;
   private PlatePanel platePanel_;
   private ScriptInterface app_;
   private ScanThread scanThread_ = null;
   private Point2D.Double stagePos_;
   private Point2D.Double cursorPos_;
   private String stageWell_;
   private String stageSite_;
   private String cursorWell_;
   private String cursorSite_;
   
   private final String PLATE_FORMAT_ID = "plate_format_id";
   private final String SITE_SPACING = "site_spacing_x";
   private final String SITE_ROWS = "site_rows";
   private final String SITE_COLS = "site_cols";
   private final String LOCK_ASPECT = "lock_aspect";
   private final String POINTER_MOVE = "Move";
   private final String POINTER_SELECT = "Select";
   private final String ROOT_DIR = "root";
   private final String PLATE_DIR = "plate";
   
   private JToggleButton moveToggleButton_;
   private JCheckBox lockAspectCheckBox_;
   private JLabel statusLabel_;

   public static void main(String args[]) {
     try {
         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
         PlateEditor dlg = new PlateEditor(null);
         dlg.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
         dlg.setVisible(true);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   /**
    * Plate scanning thread
    *
    */
   private class ScanThread extends Thread {
      public void run() {
         String plateRoot = rootDirField_.getText();
         String plateName = plateNameField_.getText();
         PlateAcquisitionData pad = new PlateAcquisitionData();

         try {
            pad.createNew(plateName, plateRoot, true);
            WellPositionList[] wpl = platePanel_.getWellPositions();
            for (int i=0; i<wpl.length; i++) {
               PositionList pl = wpl[i].getSitePositions();
               app_.setPositionList(pl);
               WellAcquisitionData wad = pad.createNewWell(wpl[i].getLabel());
               System.out.println("well: " + wpl[i].getLabel() + " - " + wpl[i].getRow() + "," + wpl[i].getColumn());
               platePanel_.activateWell(wpl[i].getRow(), wpl[i].getColumn(), true);
               platePanel_.refreshStagePosition();
               platePanel_.repaint();
               app_.runWellScan(wad);
               Thread.sleep(50);
            }
         } catch (MMScriptException e) {
            e.printStackTrace();
         } catch (MMAcqDataException e) {
            e.printStackTrace();
         } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         } finally {
            platePanel_.refreshStagePosition();
            platePanel_.repaint();            
         }
      }
   }

   /**
    * Create the frame
    */
   public PlateEditor(ScriptInterface app) {
      super();
      addWindowListener(new WindowAdapter() {
         public void windowClosing(final WindowEvent e) {
            savePosition();
            saveSettings();
         }
      });
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      setPrefsNode(root.node(root.absolutePath() + "/PlateEditor"));
      
      app_ = app;
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      plate_ = new SBSPlate();
      
      stagePos_ = new Point2D.Double(0.0, 0.0);
      cursorPos_ = new Point2D.Double(0.0, 0.0);
      
      stageWell_ = new String("undef");
      cursorWell_ = new String("undef");
      stageSite_ = new String("undef");
      cursorSite_ = new String("undef");

      setTitle("HCS plate editor");
      loadPosition(100, 100, 654, 448);

      platePanel_ = new PlatePanel(plate_, null, this);
      platePanel_.setApp(app_);
      
      getContentPane().add(platePanel_);
      springLayout.putConstraint(SpringLayout.SOUTH, platePanel_, -71, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, platePanel_, 5, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, platePanel_, -136, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, platePanel_, 5, SpringLayout.WEST, getContentPane());

      plateIDCombo_ = new JComboBox();
      getContentPane().add(plateIDCombo_);
      springLayout.putConstraint(SpringLayout.EAST, plateIDCombo_, -4, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, plateIDCombo_, -110, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, plateIDCombo_, 140, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, plateIDCombo_, 115, SpringLayout.NORTH, getContentPane());
      plateIDCombo_.addItem(SBSPlate.SBS_96_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_384_WELL);
      plateIDCombo_.addItem(SBSPlate.EVR_300_WELL);
      //comboBox.addItem(SBSPlate.CUSTOM);
      plateIDCombo_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            plate_.initialize((String)plateIDCombo_.getSelectedItem());
            PositionList sites = generateSites(Integer.parseInt(rowsField_.getText()), Integer.parseInt(columnsField_.getText()), 
                  Double.parseDouble(spacingField_.getText()));
            platePanel_.refreshImagingSites(sites);
            platePanel_.repaint();
         }
      });

      final JLabel plateFormatLabel = new JLabel();
      plateFormatLabel.setText("Plate format");
      getContentPane().add(plateFormatLabel);
      springLayout.putConstraint(SpringLayout.EAST, plateFormatLabel, -9, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, plateFormatLabel, -110, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, plateFormatLabel, 109, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, plateFormatLabel, 95, SpringLayout.NORTH, getContentPane());

      rowsField_ = new JTextField();
      rowsField_.setText("1");
      getContentPane().add(rowsField_);
      springLayout.putConstraint(SpringLayout.EAST, rowsField_, -65, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, rowsField_, -105, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, rowsField_, 215, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, rowsField_, 195, SpringLayout.NORTH, getContentPane());

      final JLabel imagingSitesLabel = new JLabel();
      imagingSitesLabel.setText("Imaging Sites");
      getContentPane().add(imagingSitesLabel);
      springLayout.putConstraint(SpringLayout.EAST, imagingSitesLabel, -4, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, imagingSitesLabel, -110, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, imagingSitesLabel, 169, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, imagingSitesLabel, 155, SpringLayout.NORTH, getContentPane());

      columnsField_ = new JTextField();
      columnsField_.setText("1");
      getContentPane().add(columnsField_);
      springLayout.putConstraint(SpringLayout.SOUTH, columnsField_, 215, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, columnsField_, 195, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, columnsField_, -20, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, columnsField_, -60, SpringLayout.EAST, getContentPane());

      spacingField_ = new JTextField();
      spacingField_.setText("200");
      getContentPane().add(spacingField_);
      springLayout.putConstraint(SpringLayout.EAST, spacingField_, -65, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, spacingField_, -105, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, spacingField_, 260, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, spacingField_, 240, SpringLayout.NORTH, getContentPane());

      final JLabel rowsColumnsLabel = new JLabel();
      rowsColumnsLabel.setText("Rows, Columns");
      getContentPane().add(rowsColumnsLabel);
      springLayout.putConstraint(SpringLayout.SOUTH, rowsColumnsLabel, 191, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, rowsColumnsLabel, 175, SpringLayout.NORTH, getContentPane());

      final JLabel spacingLabel = new JLabel();
      spacingLabel.setText("Spacing [um]");
      getContentPane().add(spacingLabel);
      springLayout.putConstraint(SpringLayout.EAST, spacingLabel, -20, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, spacingLabel, -105, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, spacingLabel, 236, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, spacingLabel, 220, SpringLayout.NORTH, getContentPane());

      final JButton refreshButton = new JButton();
      refreshButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            PositionList sites = generateSites(Integer.parseInt(rowsField_.getText()), Integer.parseInt(columnsField_.getText()), 
                  Double.parseDouble(spacingField_.getText()));
            plate_.initialize((String)plateIDCombo_.getSelectedItem());
            platePanel_.refreshImagingSites(sites);
            platePanel_.repaint();
         }
      });
      refreshButton.setText("Refresh");
      getContentPane().add(refreshButton);
      springLayout.putConstraint(SpringLayout.SOUTH, refreshButton, 290, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, refreshButton, 267, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, refreshButton, -4, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, refreshButton, -110, SpringLayout.EAST, getContentPane());

      final JButton setPositionListButton = new JButton();
      setPositionListButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            setPositionList();
         }
      });
      setPositionListButton.setText("Set MM List");
      getContentPane().add(setPositionListButton);
      springLayout.putConstraint(SpringLayout.EAST, setPositionListButton, -4, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, setPositionListButton, -110, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, setPositionListButton, 320, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, setPositionListButton, 295, SpringLayout.NORTH, getContentPane());

      final JButton scanButton = new JButton();
      scanButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            scan();
         }
      });
      scanButton.setText("Scan!");
      getContentPane().add(scanButton);
      springLayout.putConstraint(SpringLayout.SOUTH, scanButton, 360, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, scanButton, 337, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, scanButton, -4, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, scanButton, -110, SpringLayout.EAST, getContentPane());

      final JButton stopButton = new JButton();
      stopButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            stop();
         }
      });
      stopButton.setText("Stop");
      getContentPane().add(stopButton);
      springLayout.putConstraint(SpringLayout.EAST, stopButton, -4, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, stopButton, -110, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, stopButton, 391, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, stopButton, 365, SpringLayout.NORTH, getContentPane());
      
      moveToggleButton_ = new JToggleButton();
      moveToggleButton_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            if (moveToggleButton_.isSelected()) {
               moveToggleButton_.setText(POINTER_MOVE);
               platePanel_.setTool(PlatePanel.Tool.MOVE);
            } else {
               moveToggleButton_.setText(POINTER_SELECT);
               platePanel_.setTool(PlatePanel.Tool.SELECT);
            }
         }
      });
      
      // initialize in SELECT mode
      moveToggleButton_.setSelected(false);
      moveToggleButton_.setText(POINTER_SELECT);
      platePanel_.setTool(PlatePanel.Tool.SELECT);
      
      getContentPane().add(moveToggleButton_);
      springLayout.putConstraint(SpringLayout.EAST, rowsColumnsLabel, 95, SpringLayout.WEST, moveToggleButton_);
      springLayout.putConstraint(SpringLayout.WEST, rowsColumnsLabel, 5, SpringLayout.WEST, moveToggleButton_);
      springLayout.putConstraint(SpringLayout.EAST, moveToggleButton_, -19, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, moveToggleButton_, -115, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, moveToggleButton_, 45, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, moveToggleButton_, 20, SpringLayout.NORTH, getContentPane());
      
      statusLabel_ = new JLabel();
      getContentPane().add(statusLabel_);
      springLayout.putConstraint(SpringLayout.EAST, statusLabel_, -4, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, statusLabel_, 5, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, statusLabel_, -5, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, statusLabel_, -29, SpringLayout.SOUTH, getContentPane());

      lockAspectCheckBox_ = new JCheckBox();
      lockAspectCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            platePanel_.setLockAspect(lockAspectCheckBox_.isSelected());
            platePanel_.repaint();
         }
      });
      lockAspectCheckBox_.setText("Lock aspect");
      getContentPane().add(lockAspectCheckBox_);
      lockAspectCheckBox_.setSelected(true);
      springLayout.putConstraint(SpringLayout.SOUTH, lockAspectCheckBox_, 73, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, lockAspectCheckBox_, 50, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, lockAspectCheckBox_, -14, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, lockAspectCheckBox_, -115, SpringLayout.EAST, getContentPane());
      
      rootDirField_ = new JTextField();
      getContentPane().add(rootDirField_);
      springLayout.putConstraint(SpringLayout.EAST, rootDirField_, 0, SpringLayout.EAST, platePanel_);
      springLayout.putConstraint(SpringLayout.WEST, rootDirField_, 5, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, rootDirField_, -29, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, rootDirField_, -49, SpringLayout.SOUTH, getContentPane());

      plateNameField_ = new JTextField();
      getContentPane().add(plateNameField_);
      springLayout.putConstraint(SpringLayout.EAST, plateNameField_, -4, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, plateNameField_, -131, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, plateNameField_, -29, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, plateNameField_, -49, SpringLayout.SOUTH, getContentPane());
      
      final JLabel rootDirectoryLabel = new JLabel();
      rootDirectoryLabel.setText("Root directory");
      getContentPane().add(rootDirectoryLabel);
      springLayout.putConstraint(SpringLayout.EAST, rootDirectoryLabel, 110, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, rootDirectoryLabel, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, rootDirectoryLabel, -52, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, rootDirectoryLabel, -66, SpringLayout.SOUTH, getContentPane());

      final JLabel plateNameLabel = new JLabel();
      plateNameLabel.setText("Plate name");
      getContentPane().add(plateNameLabel);
      springLayout.putConstraint(SpringLayout.EAST, plateNameLabel, -4, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, plateNameLabel, -131, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, plateNameLabel, -52, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, plateNameLabel, -66, SpringLayout.SOUTH, getContentPane());
      //

      loadSettings();

      PositionList sites = generateSites(Integer.parseInt(rowsField_.getText()), Integer.parseInt(columnsField_.getText()), 
            Double.parseDouble(spacingField_.getText()));
      plate_.initialize((String)plateIDCombo_.getSelectedItem());
      platePanel_.refreshImagingSites(sites);

   }

   protected void saveSettings() {
      Preferences prefs = getPrefsNode();
      prefs.put(PLATE_FORMAT_ID, (String)plateIDCombo_.getSelectedItem());
      prefs.put(SITE_SPACING, spacingField_.getText());
      prefs.put(SITE_ROWS, rowsField_.getText());
      prefs.put(SITE_COLS, columnsField_.getText());
      prefs.putBoolean(LOCK_ASPECT, lockAspectCheckBox_.isSelected());
      prefs.put(PLATE_DIR, plateNameField_.getText());
      prefs.put(ROOT_DIR, rootDirField_.getText());
   }
   
   protected void loadSettings() {
      Preferences prefs = getPrefsNode();
      plateIDCombo_.setSelectedItem(prefs.get(PLATE_FORMAT_ID, SBSPlate.SBS_96_WELL));
      spacingField_.setText(prefs.get(SITE_SPACING, "200"));
      rowsField_.setText(prefs.get(SITE_ROWS, "1"));
      columnsField_.setText(prefs.get(SITE_COLS, "1"));
      lockAspectCheckBox_.setSelected(prefs.getBoolean(LOCK_ASPECT, true));
      platePanel_.setLockAspect(lockAspectCheckBox_.isSelected());
      plateNameField_.setText(prefs.get(PLATE_DIR, "plate"));
      rootDirField_.setText(prefs.get(ROOT_DIR, "ScreeningData"));
   }

   private void setPositionList() {
      WellPositionList[] wpl = platePanel_.getWellPositions();
      PositionList platePl = new PositionList();
      for (int i=0; i<wpl.length; i++) {
         PositionList pl = PositionList.newInstance(wpl[i].getSitePositions());
         for (int j=0; j<pl.getNumberOfPositions(); j++) {
            MultiStagePosition mpl = pl.getPosition(j);
            mpl.setLabel(wpl[i].getLabel() + "-" + mpl.getLabel());
            platePl.addPosition(pl.getPosition(j));
         }
      }

      try {
         if (app_ != null)
            app_.setPositionList(platePl);
      } catch (MMScriptException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

   private PositionList generateSites(int rows, int cols, double spacing) {
      PositionList sites = new PositionList();
      for (int i=0; i<rows; i++)
         for (int j=0; j<cols; j++) {
            double x;
            double y;
            if (cols > 1)
               x = - cols * spacing /2.0 + spacing*j;
            else
               x = 0.0;

            if (rows > 1)
               y = - rows * spacing/2.0 + spacing*i;
            else
               y = 0.0;

            MultiStagePosition mps = new MultiStagePosition();
            StagePosition sp = new StagePosition();
            sp.numAxes = 2;
            sp.x = x;
            sp.y = y;
            System.out.println("("+i+","+j+") = " + x + "," + y);

            mps.add(sp);
            sites.addPosition(mps);            
         }

      return sites;
   }


   protected void scan() {
      if (app_ == null)
         return;
      
      // ignore command if the scan is already running
      if (scanThread_ != null && scanThread_.isAlive())
         return;
      
      platePanel_.clearActive();
      scanThread_ = new ScanThread();
      scanThread_.start();
   }

   private void stop() {
      if (scanThread_ != null && scanThread_.isAlive())
         scanThread_.interrupt();
   }

   public void updatePointerXYPosition(double x, double y, String wellLabel, String siteLabel) {
      cursorPos_.x = x;
      cursorPos_.y = y;
      cursorWell_ = wellLabel;
      cursorSite_ = siteLabel;
      
      displayStatus();
   }

   private void displayStatus() {
      if (statusLabel_ == null)
         return;
      
      String statusTxt = "Cursor: X=" + TextUtils.FMT2.format(cursorPos_.x) + "um, Y=" + TextUtils.FMT2.format(cursorPos_.y) + "um, " + cursorWell_ +
                         " -- Stage: X=" + TextUtils.FMT2.format(stagePos_.x) + "um, Y=" + TextUtils.FMT2.format(stagePos_.y) + "um, " + stageWell_;
      statusLabel_.setText(statusTxt);
   }
   
   public void updateStageXYPosition(double x, double y, String wellLabel, String siteLabel) {
      stagePos_.x = x;
      stagePos_.y = y;
      stageWell_ = wellLabel;
      stageSite_ = siteLabel;
      
      displayStatus();
   }

   public String getXYStageName() {
      if (app_ != null)
         return app_.getXYStageName();
      else
         return SBSPlate.DEFAULT_XYSTAGE_NAME;
   }
}
