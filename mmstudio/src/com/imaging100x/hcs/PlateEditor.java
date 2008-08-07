package com.imaging100x.hcs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpringLayout;
import javax.swing.UIManager;

import org.micromanager.api.ScriptInterface;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.metadata.WellAcquisitionData;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;
import org.micromanager.navigation.StagePosition;
import org.micromanager.utils.MMDialog;
import org.micromanager.utils.MMScriptException;

public class PlateEditor extends MMDialog {
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
   
   private final String PLATE_FORMAT_ID = "plate_format_id";
   private final String SITE_SPACING = "site_spacing_x";
   private final String SITE_ROWS = "site_rows";
   private final String SITE_COLS = "site_cols";
   private final String POINTER_MOVE = "Move";
   private final String POINTER_SELECT = "Select";
   private JToggleButton moveToggleButton_;

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
         String plateRoot = "c:/acquisitiondata/100XHCS";
         String plateName = "plate";
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

      setTitle("HCS plate editor");
      loadPosition(100, 100, 654, 448);

      platePanel_ = new PlatePanel(plate_, null);
      platePanel_.setApp(app_);
      getContentPane().add(platePanel_);
      springLayout.putConstraint(SpringLayout.EAST, platePanel_, -136, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, platePanel_, 5, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, platePanel_, -5, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, platePanel_, 5, SpringLayout.NORTH, getContentPane());

      plateIDCombo_ = new JComboBox();
      getContentPane().add(plateIDCombo_);
      springLayout.putConstraint(SpringLayout.EAST, plateIDCombo_, -10, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, plateIDCombo_, -116, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, plateIDCombo_, 100, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, plateIDCombo_, 75, SpringLayout.NORTH, getContentPane());
      plateIDCombo_.addItem(SBSPlate.SBS_96_WELL);
      plateIDCombo_.addItem(SBSPlate.SBS_384_WELL);
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
      springLayout.putConstraint(SpringLayout.EAST, plateFormatLabel, -15, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, plateFormatLabel, -116, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, plateFormatLabel, 69, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, plateFormatLabel, 55, SpringLayout.NORTH, getContentPane());

      rowsField_ = new JTextField();
      rowsField_.setText("1");
      getContentPane().add(rowsField_);
      springLayout.putConstraint(SpringLayout.EAST, rowsField_, -76, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, rowsField_, -116, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, rowsField_, 175, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, rowsField_, 155, SpringLayout.NORTH, getContentPane());

      final JLabel imagingSitesLabel = new JLabel();
      imagingSitesLabel.setText("Imaging Sites");
      getContentPane().add(imagingSitesLabel);
      springLayout.putConstraint(SpringLayout.EAST, imagingSitesLabel, 530, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, imagingSitesLabel, 0, SpringLayout.WEST, rowsField_);
      springLayout.putConstraint(SpringLayout.NORTH, imagingSitesLabel, 115, SpringLayout.NORTH, getContentPane());

      columnsField_ = new JTextField();
      columnsField_.setText("1");
      getContentPane().add(columnsField_);
      springLayout.putConstraint(SpringLayout.SOUTH, columnsField_, 175, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, columnsField_, 155, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, columnsField_, -31, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, columnsField_, -71, SpringLayout.EAST, getContentPane());

      spacingField_ = new JTextField();
      spacingField_.setText("200");
      getContentPane().add(spacingField_);
      springLayout.putConstraint(SpringLayout.EAST, spacingField_, -76, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, spacingField_, -116, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, spacingField_, 220, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, spacingField_, 200, SpringLayout.NORTH, getContentPane());

      final JLabel rowsColumnsLabel = new JLabel();
      rowsColumnsLabel.setText("Rows, Columns");
      getContentPane().add(rowsColumnsLabel);
      springLayout.putConstraint(SpringLayout.SOUTH, rowsColumnsLabel, 152, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, rowsColumnsLabel, 136, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, rowsColumnsLabel, -26, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, rowsColumnsLabel, -116, SpringLayout.EAST, getContentPane());

      final JLabel spacingLabel = new JLabel();
      spacingLabel.setText("Spacing [um]");
      getContentPane().add(spacingLabel);
      springLayout.putConstraint(SpringLayout.SOUTH, spacingLabel, 196, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, spacingLabel, 180, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, spacingLabel, -31, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, spacingLabel, -116, SpringLayout.EAST, getContentPane());

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
      springLayout.putConstraint(SpringLayout.SOUTH, refreshButton, 250, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, refreshButton, 106, SpringLayout.WEST, spacingField_);
      springLayout.putConstraint(SpringLayout.WEST, refreshButton, 0, SpringLayout.WEST, spacingField_);

      final JButton setPositionListButton = new JButton();
      setPositionListButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            setPositionList();
         }
      });
      setPositionListButton.setText("Set MM List");
      getContentPane().add(setPositionListButton);
      springLayout.putConstraint(SpringLayout.SOUTH, setPositionListButton, 280, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, setPositionListButton, 5, SpringLayout.SOUTH, refreshButton);
      springLayout.putConstraint(SpringLayout.EAST, setPositionListButton, 106, SpringLayout.WEST, refreshButton);
      springLayout.putConstraint(SpringLayout.WEST, setPositionListButton, 0, SpringLayout.WEST, refreshButton);

      final JButton scanButton = new JButton();
      scanButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            scan();
         }
      });
      scanButton.setText("Scan!");
      getContentPane().add(scanButton);
      springLayout.putConstraint(SpringLayout.SOUTH, scanButton, 360, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, scanButton, 0, SpringLayout.EAST, setPositionListButton);
      springLayout.putConstraint(SpringLayout.WEST, scanButton, -106, SpringLayout.EAST, setPositionListButton);

      final JButton stopButton = new JButton();
      stopButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            stop();
         }
      });
      stopButton.setText("Stop");
      getContentPane().add(stopButton);
      springLayout.putConstraint(SpringLayout.SOUTH, stopButton, 31, SpringLayout.SOUTH, scanButton);
      springLayout.putConstraint(SpringLayout.NORTH, stopButton, 5, SpringLayout.SOUTH, scanButton);
      springLayout.putConstraint(SpringLayout.EAST, stopButton, 106, SpringLayout.WEST, scanButton);
      springLayout.putConstraint(SpringLayout.WEST, stopButton, 0, SpringLayout.WEST, scanButton);
      
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
      springLayout.putConstraint(SpringLayout.EAST, moveToggleButton_, -19, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, moveToggleButton_, -115, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, moveToggleButton_, 45, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, moveToggleButton_, 20, SpringLayout.NORTH, getContentPane());
      //

      loadSettings();
   }

   protected void saveSettings() {
      Preferences prefs = getPrefsNode();
      prefs.put(PLATE_FORMAT_ID, (String)plateIDCombo_.getSelectedItem());
      prefs.put(SITE_SPACING, spacingField_.getText());
      prefs.put(SITE_ROWS, rowsField_.getText());
      prefs.put(SITE_COLS, columnsField_.getText());
   }
   
   protected void loadSettings() {
      Preferences prefs = getPrefsNode();
      plateIDCombo_.setSelectedItem(prefs.get(PLATE_FORMAT_ID, SBSPlate.SBS_96_WELL));
      spacingField_.setText(prefs.get(SITE_SPACING, "200"));
      rowsField_.setText(prefs.get(SITE_ROWS, "1"));
      columnsField_.setText(prefs.get(SITE_COLS, "1"));
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

}
