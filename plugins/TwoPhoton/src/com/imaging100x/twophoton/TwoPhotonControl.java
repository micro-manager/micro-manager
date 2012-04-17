///////////////////////////////////////////////////////////////////////////////
//FILE:           TwoPhotonControl.java
//PROJECT:        Micro-Manager-2P
//SUBSYSTEM:      Two-photon microscope control plugin
//-----------------------------------------------------------------------------
//
// COPYRIGHT:     Nenad Amodaj 2011, 100X Imaging Inc 2009
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//                
// AUTHOR:        Nenad Amodaj

package com.imaging100x.twophoton;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Label;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumn;

import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;
import org.micromanager.navigation.StagePosition;
import org.micromanager.utils.MMFrame;
import org.micromanager.utils.MMScriptException;

public class TwoPhotonControl extends MMFrame implements MMPlugin, KeyListener, ContainerListener {
   private static final long serialVersionUID = 1L;

   public static String LASER_EOM_1 = "EOM1";
   public static String LASER_EOM_2 = "EOM2";
   public static String SHUTTER = "EOM12Shutter";
   public static String VOLTS = "Volts";
   public static String Z_STAGE = "Z";

   public static final String menuName = "100X | 2Photon...";
   public static final String tooltipDescription = "2Photon control panel";

   // preference keys and other string constants
   static private final String PANEL_X = "panel_x";
   static private final String PANEL_Y = "panel_y";
   static private final String AUTO_REFRESH = "auto_refresh";
   static private final String VERSION_INFO = "2.00";
   static private final String COPYRIGHT_NOTICE = "Copyright by 100X, 2011";
   static private final String DESCRIPTION = "Two Photon control module";
   static private final String INFO = "Not available";
   private Preferences prefs_;
   private CMMCore core_;
   private ScriptInterface app_;
   private JTable pmtTable_;
   private PMTDataModel pmtData_;
   private JTable depthTable_;
   private DepthDataModel depthData_;

   private Timer statusTimer_;
   private boolean initialized_;
   private boolean lockZ_;

   private JLabel pifocPlaceholder_;
   private JLabel laser1Placeholder_;
   private JLabel laser2Placeholder_;
   private SliderPanel laserSlider2_;
   private SliderPanel laserSlider1_;

   private SliderPanel pifocSlider_;
   private JButton btnMark_1;
   private JButton btnRemove;
   private JButton btnMark;
   private JCheckBox chckbxAutoRefresh_;

   protected boolean autoRefresh_ = false;
   private JCheckBox chckbxLockZ_;
   private JComboBox pixelSizeCombo_;
   
   private File depthFile_;

   private String posListDir_;
   private static final String DEFAULT_DEPTH_FNAME = "default_depth_list.dlf";

   private JLabel labelListName_;

private ActionListener pixelSizeListener_;

private JComboBox listCombo_;

private DepthSetting depthSettingCache[];



   private class StatusTimerTask extends TimerTask {

      public StatusTimerTask() {
      }

      public void run() {
         updateLasers();
         if (!autoRefresh_)
            cancel();
      }
   }
   
   private class SharedListSelectionHandler implements ListSelectionListener {
      public void valueChanged(ListSelectionEvent e) { 
          ListSelectionModel lsm = (ListSelectionModel)e.getSource();
          if (! lsm.isSelectionEmpty()) {
              // Find out which indexes are selected.
              int minIndex = lsm.getMinSelectionIndex();
              int maxIndex = lsm.getMaxSelectionIndex();
              for (int i = minIndex; i <= maxIndex; i++) {
                  if (lsm.isSelectedIndex(i)) {
                      DepthSetting ds = depthData_.getDepthSetting(i);
                      applyDepthSetting(ds, true);
                  }
              }
          }
      }
  }
   
   /**
    * File filter class for Open/Save file choosers 
    */
   private class PosFileFilter extends FileFilter {
      final private String EXT_POS;
      final private String DESCRIPTION;

      public PosFileFilter() {
         super();
         EXT_POS = new String("dlf");
         DESCRIPTION = new String("Z-depth lists (*.dlf)");
      }

      public boolean accept(File f){
         if (f.isDirectory())
            return true;

         if (EXT_POS.equals(getExtension(f)))
            return true;
         return false;
      }

      public String getDescription(){
         return DESCRIPTION;
      }

      private String getExtension(File f) {
         String ext = null;
         String s = f.getName();
         int i = s.lastIndexOf('.');

         if (i > 0 &&  i < s.length() - 1) {
            ext = s.substring(i+1).toLowerCase();
         }
         return ext;
      }
   }


   /**
    * Constructor
    */
   public TwoPhotonControl() {
      super();
      addKeyAndContainerListenerRecursively(this);
      
      setLocation(-3, -31);
      initialized_ = false;
      lockZ_ = false;

      // load preferences
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      prefs_ = root.node(root.absolutePath() + "/settings");
      setPrefsNode(prefs_);

      addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent arg0) {
            if (canExit()) {
               saveSettings();
               dispose();
            } else {
               displayMessageDialog("Can't exit now.");
               return;
            }
         }

         public void windowOpened(final WindowEvent arg0) {
            initialize();
         }

         public void windowClosed(WindowEvent arg0) {
            if (statusTimer_ != null)
               statusTimer_.cancel();
            // saveSettings();
         }
      });

      depthSettingCache = new DepthSetting[2];
      depthSettingCache[0] = null;
      depthSettingCache[1] = null;
      
      getContentPane().setLayout(null);
      setResizable(false);

      setTitle("Two Photon Control v " + VERSION_INFO);
      setSize(626, 538);
      loadPosition(100, 100);

      final JButton exitButton = new JButton();
      exitButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            if (canExit()) {
               dispose();
               saveSettings();
            } else {
               displayMessageDialog("Can't exit now");
               return;
            }
         }
      });
      exitButton.setText("Close");
      exitButton.setBounds(521, 11, 89, 23);
      getContentPane().add(exitButton);
      {
         JLabel lblExcitation = new JLabel("Excitation (EOM Voltage)");
         lblExcitation.setFont(new Font("Tahoma", Font.BOLD, 11));
         lblExcitation.setBounds(10, 48, 251, 14);
         getContentPane().add(lblExcitation);
      }
      {
         JLabel lblLaser = new JLabel("Laser-1");
         lblLaser.setBounds(10, 73, 46, 14);
         getContentPane().add(lblLaser);
      }
      {
         JSeparator separator = new JSeparator();
         separator.setBounds(10, 153, 389, 2);
         getContentPane().add(separator);
      }
      {
         JLabel lblPmtGain = new JLabel("PMT gain [V]");
         lblPmtGain.setFont(new Font("Tahoma", Font.BOLD, 11));
         lblPmtGain.setBounds(10, 166, 121, 14);
         getContentPane().add(lblPmtGain);
      }
      {
         Label label = new Label("Z [um]");
         label.setBounds(10, 362, 54, 23);
         getContentPane().add(label);
      }
      {
         pmtTable_ = new JTable();
         pmtTable_.setAutoCreateColumnsFromModel(false);

         pmtData_ = new PMTDataModel();
         pmtTable_.setModel(pmtData_);

         int firstColWidth = 80;
         pmtTable_.addColumn(new TableColumn(0, firstColWidth, null, null));
         pmtTable_.addColumn(new TableColumn(1, 309, new SliderCellRenderer(), new SliderCellEditor(this)));

      }
      {
         pifocPlaceholder_ = new JLabel("");
         pifocPlaceholder_.setBounds(66, 366, 333, 18);
         getContentPane().add(pifocPlaceholder_);
      }

      {
         laser1Placeholder_ = new JLabel("");
         laser1Placeholder_.setBounds(66, 69, 333, 18);
         getContentPane().add(laser1Placeholder_);
      }
      {
         laser2Placeholder_ = new JLabel("");
         laser2Placeholder_.setBounds(66, 90, 333, 18);
         getContentPane().add(laser2Placeholder_);
      }
      {
         JLabel lblLaser_1 = new JLabel("Laser-2");
         lblLaser_1.setBounds(10, 92, 46, 14);
         getContentPane().add(lblLaser_1);
      }

      pifocSlider_ = new SliderPanel();
      pifocSlider_.setBounds(pifocPlaceholder_.getBounds());
      getContentPane().add(pifocSlider_);
      getContentPane().remove(pifocPlaceholder_);
      pifocSlider_.addEditActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            onPifocAction();
         }
      });
      pifocSlider_.addSliderMouseListener(new MouseAdapter() {

         public void mouseReleased(MouseEvent e) {
             onPifocAction();
         }
         
         public void mousePressed(MouseEvent e) {
         }
         
      });
      
      laserSlider1_ = new SliderPanel();
      laserSlider1_.setBounds(laser1Placeholder_.getBounds());
      getContentPane().add(laserSlider1_);
      getContentPane().remove(laser1Placeholder_);
      laserSlider1_.setLimits(0, 100);

      laserSlider1_.addEditActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            onEOMSliderAction(1);
         }
      });
      laserSlider1_.addSliderMouseListener(new MouseAdapter() {

         public void mouseReleased(MouseEvent e) {
            onEOMSliderAction(1);
         }
      });
      

      laserSlider2_ = new SliderPanel();
      laserSlider2_.setBounds(laser2Placeholder_.getBounds());
      laserSlider2_.setLimits(0, 100);
      getContentPane().add(laserSlider2_);
      getContentPane().remove(laser2Placeholder_);

      {
         JButton btnRefresh = new JButton("Refresh");
         btnRefresh.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               updateLasers();
            }
         });
         btnRefresh.setBounds(521, 450, 89, 23);
         getContentPane().add(btnRefresh);
      }
      {
         JLabel lblCopyrightxImaging = new JLabel(
               "Copyright 100X Imaging Inc, 2010");
         lblCopyrightxImaging.setBounds(438, 482, 172, 14);
         getContentPane().add(lblCopyrightxImaging);
      }
      {
         depthTable_ = new JTable();

         depthTable_.setAutoCreateColumnsFromModel(false);

         depthData_ = new DepthDataModel();
         depthTable_.setModel(depthData_);

         int firstColWidth = 100;
         depthTable_.addColumn(new TableColumn(0, firstColWidth, null, null));
         depthTable_.addColumn(new TableColumn(1, 100, null, null));

         ListSelectionModel lsm = depthTable_.getSelectionModel();
         lsm.addListSelectionListener(new SharedListSelectionHandler());
         depthTable_.setSelectionModel(lsm);
         
      }
      {
         btnMark_1 = new JButton("Mark");
         btnMark_1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               onDepthMark();
            }
         });
         btnMark_1.setBounds(424, 184, 89, 23);
         getContentPane().add(btnMark_1);
      }
      {
         btnRemove = new JButton("Remove");
         btnRemove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               onDepthRemove();
            }
         });
         btnRemove.setBounds(424, 210, 89, 23);
         getContentPane().add(btnRemove);
      }
      {
         btnMark = new JButton("Remove all");
         btnMark.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               onDepthRemoveAll();
            }
         });
         btnMark.setBounds(424, 234, 89, 23);
         getContentPane().add(btnMark);
      }
      {
         JLabel lblDepthControl = new JLabel("Depth (Z) control");
         lblDepthControl.setFont(new Font("Tahoma", Font.BOLD, 11));
         lblDepthControl.setBounds(424, 145, 166, 14);
         getContentPane().add(lblDepthControl);
      }
      {
         chckbxAutoRefresh_ = new JCheckBox("Auto refresh");
         chckbxAutoRefresh_.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
               boolean sel = chckbxAutoRefresh_.isSelected();
               if (!autoRefresh_ && sel && initialized_) {
                  startTimer();
               }
               autoRefresh_ = sel;
            }
         });
         chckbxAutoRefresh_.setBounds(420, 450, 101, 23);
         getContentPane().add(chckbxAutoRefresh_);
      }
      laserSlider2_.addEditActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            onEOMSliderAction(2);
         }
      });
      laserSlider2_.addSliderMouseListener(new MouseAdapter() {

         public void mouseReleased(MouseEvent e) {
            onEOMSliderAction(2);
         }
      });

      
      {
         JScrollPane scrollPane = new JScrollPane();
         scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
         scrollPane.setBounds(420, 267, 190, 171);
         getContentPane().add(scrollPane);
         scrollPane.setViewportView(depthTable_);
      }
      {
         JScrollPane sp = new JScrollPane();
         sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
         sp.setBounds(10, 191, 389, 154);
         sp.setViewportView(pmtTable_);
         getContentPane().add(sp);
      }

      {
         JButton btnNewList = new JButton("Save");
         btnNewList.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               onSaveDepthList();
            }
         });
         btnNewList.setBounds(522, 210, 89, 23);
         getContentPane().add(btnNewList);
      }
      {
         JButton btnSaveAs = new JButton("Save As...");
         btnSaveAs.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               onSaveDepthListAs();
            }
         });
         btnSaveAs.setBounds(522, 235, 89, 23);
         getContentPane().add(btnSaveAs);
      }
      {
         labelListName_ = new JLabel("");
         labelListName_.setBorder(new LineBorder(new Color(0, 0, 0)));
         labelListName_.setBounds(424, 165, 186, 14);
         getContentPane().add(labelListName_);
      }
      {
         JButton btnLoad = new JButton("Load...");
         btnLoad.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               onLoadDepthList();
            }
         });
         btnLoad.setBounds(522, 184, 89, 23);
         getContentPane().add(btnLoad);
      }
      {
         chckbxLockZ_ = new JCheckBox("Lock stage to Z-list");
         chckbxLockZ_.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               onLockZ();
            }
         });
         chckbxLockZ_.setBounds(66, 391, 134, 23);
         chckbxLockZ_.setSelected(lockZ_);
         getContentPane().add(chckbxLockZ_);
      }
      {
         pixelSizeCombo_ = new JComboBox();
         pixelSizeListener_ = new ActionListener() {
             public void actionPerformed(ActionEvent e) {
                 onPixelSize();
              }
           };
         pixelSizeCombo_.addActionListener(pixelSizeListener_);
         pixelSizeCombo_.setBounds(66, 453, 184, 20);
         getContentPane().add(pixelSizeCombo_);
      }
      {
         JLabel lblPixelSize = new JLabel("Pixel size");
         lblPixelSize.setBounds(10, 456, 59, 14);
         getContentPane().add(lblPixelSize);
      }

      {
         JButton btnGeneratex = new JButton("Generate 3X3 grid");
         btnGeneratex.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               onGenerateGrid(3);
            }
         });
         btnGeneratex.setBounds(424, 70, 186, 23);
         getContentPane().add(btnGeneratex);
      }
      {
         JButton btnGeneratexGrid = new JButton("Generate 5X5 grid");
         btnGeneratexGrid.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               onGenerateGrid(5);
            }
         });
         btnGeneratexGrid.setBounds(424, 99, 186, 23);
         getContentPane().add(btnGeneratexGrid);
      }
      {
        	listCombo_ = new JComboBox();
        	listCombo_.addItem("Depth List 0");
        	listCombo_.addItem("Depth List 1");
         listCombo_.setSelectedIndex(0);
        	listCombo_.addActionListener(new ActionListener() {
        		public void actionPerformed(ActionEvent arg0) {
        			onDepthListIndex();
        		}
        	});
        	listCombo_.setBounds(217, 395, 182, 20);
        	getContentPane().add(listCombo_);
        }
      
      // load previous settings from the registry
      loadSettings();

      // update dependent gui elements
      chckbxAutoRefresh_.setSelected(autoRefresh_);
   }

   protected void onDepthListIndex() {
      int listIdx = listCombo_.getSelectedIndex();
      
      // do list caching

         double v1 = 0.0;
         double v2 = 0.0;
         try {
            v1 = Double.parseDouble(core_.getProperty(LASER_EOM_1, "Volts"));
            v2 = Double.parseDouble(core_.getProperty(LASER_EOM_2, "Volts"));
         } catch (NumberFormatException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
         PMTSetting[] pmts = pmtData_.getPMTSettings();
         
         // cache values
         int cacheIdx = listIdx == 0 ? 1 : 0;
         depthSettingCache[cacheIdx] = new DepthSetting();
         depthSettingCache[cacheIdx].eomVolts1_ = v1;
         depthSettingCache[cacheIdx].eomVolts2_ = v2;
         depthSettingCache[cacheIdx].pmts = pmts;
         
         // apply cache
         if (depthSettingCache[listIdx] != null && lockZ_ == false) {
            applyDepthSetting(depthSettingCache[listIdx], false);
         }
      
      
      try {
         setCurrentListIndexOnDevices(listIdx);
         disableDepthControl();
         depthData_.setCurrentListIndex(listIdx);
         if (lockZ_ && depthData_.getAllDepthSettings().length > 0) {
            enableDepthControl();
            onPifocAction();
         } else {
            chckbxLockZ_.setSelected(false);
            lockZ_ = false;
            disableDepthControl();
         }
      } catch (TwoPhotonException e) {
         handleError(e.getMessage());
         int idx = depthData_.getCurrentListIndex();
         listCombo_.setSelectedIndex(idx);
      }
   }
   
   private void applyDepthListToDevices() {
      int listIdx = depthData_.getCurrentListIndex();
      setCurrentListIndexOnDevices(0);
      enableDepthControl();
      setCurrentListIndexOnDevices(1);
      enableDepthControl();
      if (!lockZ_)
         disableDepthControl();
      setCurrentListIndexOnDevices(listIdx);
   }

   private void setCurrentListIndexOnDevices(int listIdx) {
      try {
         core_.setProperty(LASER_EOM_1, "ListIndex", Integer.toString(listIdx));
         core_.setProperty(LASER_EOM_2, "ListIndex", Integer.toString(listIdx));
         pmtData_.setCurrentDepthList(listIdx);
         core_.setProperty(Z_STAGE, "DepthList", Integer.toString(listIdx));
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

   protected void onSaveDepthListAs() {
      JFileChooser fc = new JFileChooser();
      boolean saveFile = true;

      do {
         if (depthFile_ == null)
            depthFile_ = new File(DEFAULT_DEPTH_FNAME);

         fc.setSelectedFile(depthFile_);
         int retVal = fc.showSaveDialog(this);
         if (retVal == JFileChooser.APPROVE_OPTION) {
            depthFile_ = fc.getSelectedFile();

            // check if file already exists
            if( depthFile_.exists() ) { 
               int sel = JOptionPane.showConfirmDialog( this,
                     "Overwrite " + depthFile_.getName(),
                     "File Save",
                     JOptionPane.YES_NO_OPTION);

               if(sel == JOptionPane.YES_OPTION)
                  saveFile = true;
               else
                  saveFile = false;
            }
         }
      } while (saveFile == false);

      try {
         depthData_.save(depthFile_.getAbsolutePath());
         posListDir_ = depthFile_.getParent();
         labelListName_.setText(depthFile_.getName());
      } catch (Exception e) {
         handleError(e.getMessage());
      }
   }

   protected void onSaveDepthList() {
      if (depthFile_ == null)
         depthFile_ = new File(DEFAULT_DEPTH_FNAME);
      
      try {
         depthData_.save(depthFile_.getAbsolutePath());
         posListDir_ = depthFile_.getParent();
         labelListName_.setText(depthFile_.getName());
      } catch (Exception e) {
         handleError(e.getMessage());
      }
      
   }

   protected void onLoadDepthList() {
      JFileChooser fc = new JFileChooser();
      fc.addChoosableFileFilter(new PosFileFilter());

      if (posListDir_ != null)
         fc.setCurrentDirectory(new File(posListDir_));
      int retVal = fc.showOpenDialog(this);
      if (retVal == JFileChooser.APPROVE_OPTION) {
         depthFile_ = fc.getSelectedFile();
         try {
            depthData_.load(depthFile_.getAbsolutePath());
            posListDir_ = depthFile_.getParent();
            labelListName_.setText(depthFile_.getName());
         } catch (Exception e) {
            handleError(e.getMessage());
         }
      }
      applyDepthListToDevices();
   }

   protected void onGenerateGrid(int size) {
      double pixSize = core_.getPixelSizeUm();
      if (pixSize == 0.0)
         pixSize = 1.0;
      
      long height = core_.getImageHeight();
      long width = core_.getImageWidth();
      String xyStage = core_.getXYStageDevice();
      
      if (xyStage.isEmpty()) {
         handleError("No XY stage available.");
         return;
      }
      
      String camera = core_.getCameraDevice();
      if (camera.isEmpty()){
         handleError("No camera available.");
         return;
      }
      
      boolean swapXY = false;
      try {
         String swapProp = core_.getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY());
         if (swapProp.equals("1"))
            swapXY = true;
      } catch (Exception e) {
         handleError(e.getMessage());
         return;
      }

      
      double x[] = new double[1];
      double y[] = new double[1];
      try {
         if (swapXY)
            core_.getXYPosition(xyStage, y, x);
         else
            core_.getXYPosition(xyStage, x, y);
      } catch (Exception e) {
         handleError(e.getMessage());
         return;
      }

      PositionList pl = new PositionList();

      for (int i=-size/2; i<=size/2; i++) {
         double yPos = y[0] + i*height*pixSize;
         for (int j=-size/2; j<=size/2; j++) {
            double xPos = x[0] + j*width*pixSize;
            MultiStagePosition mpl = new MultiStagePosition();
            StagePosition sp = new StagePosition();
            sp.numAxes = 2;
            sp.stageName = xyStage;
            if (swapXY) {
               sp.y = xPos;
               sp.x = yPos;
            } else {
               sp.x = xPos;
               sp.y = yPos;
            }
            mpl.add(sp);
            int row = swapXY ? size/2 + j : size/2 + i;
            int col = swapXY ? size/2 + i : size/2 + j;
            String lab = new String("Grid_" + row + "_" + col);
            mpl.setLabel(lab);
            mpl.setGridCoordinates(row, col);
            pl.addPosition(mpl);
         }
      }
      
      try {
         app_.setPositionList(pl);
      } catch (MMScriptException e) {
         handleError(e.getMessage());
      }
      
   }

   protected void onPixelSize() {
      String pixSizeName = (String)pixelSizeCombo_.getSelectedItem();
      if (pixSizeName == null)
         return;
      
      try {
         if (!pixSizeName.isEmpty())
            core_.setPixelSizeConfig(pixSizeName);
      } catch (Exception e) {
         handleError(e.getMessage());
      }
   }

   public void applyDepthSetting(DepthSetting ds, boolean setZ) {
      try {
         if (setZ)
            core_.setPosition(core_.getFocusDevice(), ds.z);
         
         core_.setProperty(LASER_EOM_1, "Volts", Double.toString(ds.eomVolts1_));
         core_.setProperty(LASER_EOM_2, "Volts", Double.toString(ds.eomVolts2_));
         for (int i=0; i<ds.pmts.length; i++)
            core_.setProperty(ds.pmts[i].name, "Volts", Double.toString(ds.pmts[i].volts));
         updateLasers();
      } catch (Exception e) {
         handleError(e.getMessage());
         return;
      }     
   }

   protected void onDepthRemoveAll() {
      chckbxLockZ_.setSelected(false);
      lockZ_ = false;
      disableDepthControl();
      depthData_.clear();
   }

   protected void onDepthRemove() {
      chckbxLockZ_.setSelected(false);
      lockZ_ = false;
      disableDepthControl();
      int idx = depthTable_.getSelectedRow();
      depthData_.deleteDepthSetting(idx);
   }

   private void onDepthMark() {
      chckbxLockZ_.setSelected(false);
      lockZ_ = false;
      disableDepthControl();
      try {
         double z = core_.getPosition(core_.getFocusDevice());
         double v1 = Double
               .parseDouble(core_.getProperty(LASER_EOM_1, "Volts"));
         double v2 = Double
               .parseDouble(core_.getProperty(LASER_EOM_2, "Volts"));
         PMTSetting[] pmts = pmtData_.getPMTSettings();
         DepthSetting ds = new DepthSetting();
         ds.z = z;
         ds.deltaZ = 0.0; // temp value
         ds.eomVolts1_ = v1;
         ds.eomVolts2_ = v2;
         ds.pmts = pmts;
         depthData_.setDepthSetting(ds);
      } catch (Exception e) {
         handleError(e.getMessage());
         return;
      }
   }

   protected void onPifocAction() {
      String zprop = pifocSlider_.getText();
      try {
         double z = Double.parseDouble(zprop);
         core_.setPosition(core_.getFocusDevice(), z);
         if (lockZ_) {
            DepthSetting ds = depthData_.getInterpolatedDepthSetting(z);
            applyDepthSetting(ds, false);
         }
         removeDepthListSelection();
      } catch (NumberFormatException e) {
         handleError(e.getMessage());
         return;
      } catch (Exception e) {
         handleError(e.getMessage());
         return;
      }
   }

//   private void setEOMShutterState(boolean open) {
//      if (open) {
//         tglbtnEOMShutter_.setSelected(true);
//         tglbtnEOMShutter_.setText("Shutter:Open");
//      } else {
//         tglbtnEOMShutter_.setSelected(false);
//         tglbtnEOMShutter_.setText("Shutter:Closed");
//      }
//   }

   protected void onEOMSliderAction(int eom) {
      try {
         if (eom == 1) {
            String eom1 = laserSlider1_.getText();
            core_.setProperty(LASER_EOM_1, "Volts", eom1);
         } else {
            String eom2 = laserSlider2_.getText();
            core_.setProperty(LASER_EOM_2, "Volts", eom2);
         }
         
         removeDepthListSelection();
         
      } catch (Exception e) {
         e.printStackTrace();
         handleError(e.getMessage());
      }
   }
   
   public void removeDepthListSelection() {
      depthTable_.clearSelection();
   
}

   /**
    * Save settings to registry
    */
   protected void saveSettings() {
      Rectangle r = getBounds();
      prefs_.putInt(PANEL_X, r.x);
      prefs_.putInt(PANEL_Y, r.y);
      prefs_.putBoolean(AUTO_REFRESH, autoRefresh_);
      savePosition();
   }

   /**
    * Load settings from the registry
    */
   protected void loadSettings() {
      loadPosition(100, 100);
      autoRefresh_ = prefs_.getBoolean(AUTO_REFRESH, autoRefresh_);
   }

   /**
    * Provides the interface to the parent application
    */
   public void setApp(ScriptInterface app) {
      app_ = app;
      initialize();
   }

   /**
    * Initializes the module.
    */
   private void initialize() {
      core_ = app_.getMMCore();
      pmtData_.setCore(core_);
      
      try {
         double minV = core_.getPropertyLowerLimit(LASER_EOM_1, "Volts");
         double maxV = core_.getPropertyUpperLimit(LASER_EOM_1, "Volts");
         laserSlider1_.setLimits(minV, maxV);
      } catch (Exception e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }
      try {
         double minV = core_.getPropertyLowerLimit(LASER_EOM_2, "Volts");
         double maxV = core_.getPropertyUpperLimit(LASER_EOM_2, "Volts");
         laserSlider2_.setLimits(minV, maxV);
      } catch (Exception e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }

      String focusDev = core_.getFocusDevice();
      if (focusDev.isEmpty()) {
         pifocSlider_.setEnabled(false);
      } else {
         try {
            double min = core_.getPropertyLowerLimit(focusDev, "Position");
            double max = core_.getPropertyUpperLimit(focusDev, "Position");
            if (max - min > 0.0)
               pifocSlider_.setLimits(min, max);
            else
               pifocSlider_.setLimits(0.0, 200.0);
         } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
      }
      
      // pixel size
      StrVector pixSizes = core_.getAvailablePixelSizeConfigs();
      pixelSizeCombo_.removeAllItems();
      pixelSizeCombo_.removeActionListener(pixelSizeListener_);
      for (int i=0; i<pixSizes.size(); i++)
         pixelSizeCombo_.addItem(pixSizes.get(i));
      
      try {
         String pixSize = core_.getCurrentPixelSizeConfig();
         pixelSizeCombo_.setSelectedItem(pixSize);
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      
      pixelSizeCombo_.addActionListener(pixelSizeListener_);
      initialized_ = true;

      // start update timer
      if (autoRefresh_) {
         startTimer();
      }
   }

   private void startTimer() {
      if (statusTimer_ != null)
         statusTimer_.cancel();

      statusTimer_ = new Timer();

      StatusTimerTask task = new StatusTimerTask();
      statusTimer_.schedule(task, 0, 5000);
   }

   public void updateLasers() {
      System.out.println("updating gui");
      try {

         // laser 1
         laserSlider1_.setText(core_.getProperty(LASER_EOM_1, "Volts"));

         // laser 2
         laserSlider2_.setText(core_.getProperty(LASER_EOM_2, "Volts"));

         PMTDataModel pmtdata = (PMTDataModel) pmtTable_.getModel();
         pmtdata.refresh();

         // dual shutter
//         try {
//            String s = core_.getProperty(SHUTTER, "State");
//            if (s.compareTo("1") == 0)
//               setEOMShutterState(true);
//            else
//               setEOMShutterState(false);
//
//         } catch (Exception e1) {
//            e1.printStackTrace();
//            tglbtnEOMShutter_.setEnabled(false);
//         }

         // Z stage
         String focusDev = core_.getFocusDevice();
         if (!focusDev.isEmpty()) {
            double pos = core_.getPosition(focusDev);
            pifocSlider_.setText(Double.toString(pos));
         }
         
         String pixSize = core_.getCurrentPixelSizeConfig();
         pixelSizeCombo_.setSelectedItem(pixSize);

      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

   /**
    * Displays a message in a dialog box
    */
   private void displayMessageDialog(String message) {
      JOptionPane.showMessageDialog(this, message);
   }

   /**
    * Displays error message in a dialog box.
    */
   private void handleError(String message) {
      JOptionPane.showMessageDialog(this, message);
   }

   private boolean canExit() {
      return true;
   }

   // //////////////////////////////////////////////////////////////////////////
   // required api methods

   public String getCopyright() {
      return COPYRIGHT_NOTICE;
   }

   public String getDescription() {
      return DESCRIPTION;
   }

   public String getInfo() {
      return INFO;
   }

   public String getVersion() {
      return VERSION_INFO;
   }

   public void configurationChanged() {
      // TODO Auto-generated method stub

   }
  
   // This function is called whenever a Component or a Container is added to
   // another Container belonging to this EscapeDialog
   public void componentAdded(ContainerEvent e) {
      addKeyAndContainerListenerRecursively(e.getChild());
   }

   // // This function is called whenever a Component or a Container is removed
   // // from another Container belonging to this EscapeDialog
   // public void componentRemoved(ContainerEvent e)
   // {
   // removeKeyAndContainerListenerRecursively(e.getChild());
   // }

   private void addKeyAndContainerListenerRecursively(Component c) {
      // Add KeyListener to the Component passed as an argument
      c.addKeyListener(this);
      // Check if the Component is a Container
      if (c instanceof Container) {
         // Component c is a Container. The following cast is safe.
         Container cont = (Container) c;
         // Add ContainerListener to the Container.
         cont.addContainerListener(this);
         // Get the Container's array of children Components.
         Component[] children = cont.getComponents();
         // For every child repeat the above operation.
         for (int i = 0; i < children.length; i++) {
            addKeyAndContainerListenerRecursively(children[i]);
         }
      }
   }

   public void keyTyped(KeyEvent e) {
   }

   public void keyPressed(KeyEvent e) {
      if (e.isShiftDown()) {
         if (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT)
            onPifocAction();
      }
   }

   public void keyReleased(KeyEvent e) {
      // TODO Auto-generated method stub

   }

   public void componentRemoved(ContainerEvent e) {
      //System.out.println("component removed");
      
   }

   private void onLockZ() {
      lockZ_ = chckbxLockZ_.isSelected();
      if (lockZ_) {
         applyDepthListToDevices();
         enableDepthControl();
      } else {
         disableDepthControl();
      }
   }

   private void disableDepthControl() {
      try {
         // effectively disable and clear depth interpolation
//         core_.setProperty(LASER_EOM_1, "DepthIndex", "-1");
//         core_.setProperty(LASER_EOM_1, "DepthListSize", "0");
//         core_.setProperty(LASER_EOM_2, "DepthIndex", "-1");
//         core_.setProperty(LASER_EOM_2, "DepthListSize", "0");
         core_.setProperty("Z", "DepthControl", "No");
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

   private void enableDepthControl() {
      DepthSetting[] ds = depthData_.getAllDepthSettings();
      if (ds.length == 0) {
         disableDepthControl();
         return;
      }
      try {
         core_.setProperty(LASER_EOM_1, "DepthIndex", "-1");
         core_.setProperty(LASER_EOM_2, "DepthIndex", "-1");
         core_.setProperty(LASER_EOM_1, "DepthListSize", Integer.toString(ds.length));
         core_.setProperty(LASER_EOM_2, "DepthListSize", Integer.toString(ds.length));
         for (int j=0; j<ds[0].pmts.length; j++) {
            core_.setProperty(ds[0].pmts[j].name, "DepthIndex", "-1");
            core_.setProperty(ds[0].pmts[j].name, "DepthListSize", Integer.toString(ds.length));
         }
            
         for (int i=0; i<ds.length; i++) {
            core_.setProperty(LASER_EOM_1, "DepthIndex", Integer.toString(i));
            core_.setProperty(LASER_EOM_1, "Z", Double.toString(ds[i].z));
            core_.setProperty(LASER_EOM_1, "VD", Double.toString(ds[i].eomVolts1_));
            core_.setProperty(LASER_EOM_2, "DepthIndex", Integer.toString(i));
            core_.setProperty(LASER_EOM_2, "Z", Double.toString(ds[i].z));
            core_.setProperty(LASER_EOM_2, "VD", Double.toString(ds[i].eomVolts2_));
            for (int j=0; j<ds[i].pmts.length; j++) {
               core_.setProperty(ds[i].pmts[j].name, "DepthIndex", Integer.toString(i));
               core_.setProperty(ds[i].pmts[j].name, "Z", Double.toString(ds[i].z));
               core_.setProperty(ds[i].pmts[j].name, "VD", Double.toString(ds[i].pmts[j].volts));
            }
         }
         
         core_.setProperty(Z_STAGE, "DepthControl", "Yes");
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }
}
