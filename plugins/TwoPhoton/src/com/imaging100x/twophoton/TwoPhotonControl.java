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
// AUTHOR:        Nenad Amodaj, Henry Pinkard

package com.imaging100x.twophoton;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.prefs.Preferences;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumn;

import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.ComponentTitledBorder;
import org.micromanager.acquisition.VirtualAcquisitionDisplay;
import org.micromanager.api.AcquisitionEngine;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;
import org.micromanager.navigation.StagePosition;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ImageFocusListener;
import org.micromanager.utils.MMFrame;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

public class TwoPhotonControl extends MMFrame implements MMPlugin, KeyListener, 
         ImageFocusListener {
   private static final long serialVersionUID = 1L;

   public static String LASER_EOM_1 = "EOM1";
   public static String LASER_EOM_2 = "EOM2";
   public static String SHUTTER = "EOM12Shutter";
   public static String VOLTS = "Volts";
   public static String DISABLE = "Disable";
   public static String Z_STAGE = "Z";
   public static String RESOLUTION = "Objective Res";

   public static final String menuName = "100X | 2Photon...";
   public static final String tooltipDescription = "2Photon control panel";

   
   // preference keys and other string constants
   static private final String PANEL_X = "panel_x";
   static private final String PANEL_Y = "panel_y";
   static private final String INVERT_X = "Invert_x";
   static private final String INVERT_Y = "Invert_y";
   static private final String SWAP_X_AND_Y = "SwapXandY";
   static private final String VERSION_INFO = "3.0";
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

   private SliderPanel laserSlider2_;
   private SliderPanel laserSlider1_;
   private JSpinner gridOverlapYSpinner_, gridOverlapXSpinner_;
   private JSpinner gridSizeXSpinner_, gridSizeYSpinner_;
   
   private SliderPanel pifocSlider_;
   private JButton markButton;
   private JButton removeButton;
   private JButton removeAllButton;
   private JCheckBox activateDepthList_;
   private JComboBox pixelSizeCombo_;
   
   private File depthFile_;

   private String posListDir_;
   private static final String DEFAULT_DEPTH_FNAME = "default_depth_list.dlf";


private ActionListener pixelSizeListener_;

private JComboBox listCombo_;

private DepthSetting depthSettingCache[];

private AcquisitionStitcher stitcher_ = new AcquisitionStitcher();
private JButton stitchButton_;
private JComboBox windowsToStitchCombo_;
private ArrayList<VirtualAcquisitionDisplay> availableVADs_;
private JCheckBox invertXCheckBox_, invertYCheckBox_, swapXandYCheckBox_;
private JCheckBox drawGrid_, drawPosNames_;
   
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
      
      setLocation(-3, -31);
      initialized_ = false;

      // load preferences
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      prefs_ = root.node(root.absolutePath() + "/settings");
      setPrefsNode(prefs_);

      addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent arg0) {
               saveSettings();
               dispose();
  
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

      
      getContentPane().setLayout(null);
      setResizable(false);

      setTitle("Two Photon Control v " + VERSION_INFO);
      setSize(660, 580);
      loadPosition(100, 100);
      
      createExcitationPanel();
      createZPanel();
      createPMTPanel();
      createGridPanel();
      createStitchPanel();
      createDepthPanel();

      pixelSizeCombo_ = new JComboBox();
      pixelSizeListener_ = new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            onPixelSize();
         }
      };
      pixelSizeCombo_.addActionListener(pixelSizeListener_);
      pixelSizeCombo_.setBounds(460, 380 , 184, 20);
      getContentPane().add(pixelSizeCombo_);
      JLabel lblPixelSize = new JLabel("Pixel size");
      lblPixelSize.setBounds(410, 383, 59, 14);
      getContentPane().add(lblPixelSize);
      
      
      //Add credit
      JLabel lblCopyrightxImaging = new JLabel("Copyright 100X Imaging Inc, 2010");
      lblCopyrightxImaging.setBounds(438, 510, 172, 14);
      getContentPane().add(lblCopyrightxImaging);

      // load previous settings from the registry
      loadSettings();

      GUIUtils.registerImageFocusListener(this);
      initializeDepthListRunnable();
   }

   private void createDepthPanel() {
      JPanel panel = createPanel("Vary excitation with Z position", 5, 320, 400, 545);
      panel.setLayout(new BorderLayout());

      depthTable_ = new JTable();
      depthTable_.setAutoCreateColumnsFromModel(false);
      depthData_ = new DepthDataModel();
      depthTable_.setModel(depthData_);
      int firstColWidth = 100;
      depthTable_.addColumn(new TableColumn(0, firstColWidth, null, null));
      depthTable_.addColumn(new TableColumn(1, 100, null, null));
      JScrollPane scrollPane = new JScrollPane();
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setViewportView(depthTable_);
      panel.add(scrollPane, BorderLayout.CENTER);
            
      
      JPanel buttonPanel = new JPanel();
      buttonPanel.setLayout(new BoxLayout(buttonPanel,BoxLayout.Y_AXIS));
      JPanel row1 = new JPanel(new FlowLayout());
      markButton = new JButton("Mark");
      markButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            onDepthMark();
         }
      });
      removeButton = new JButton("Remove");
      removeButton.addActionListener(new ActionListener() {

         public void actionPerformed(ActionEvent e) {
            onDepthRemove();
         }
      });
      removeAllButton = new JButton("Remove all");
      removeAllButton.addActionListener(new ActionListener() {

         public void actionPerformed(ActionEvent e) {
            onDepthRemoveAll();
         }
      });      
      JButton saveAsButton = new JButton("Save as");
      saveAsButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            onSaveDepthListAs();
         }
      });
      JButton loadButton = new JButton("Load");
      loadButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            onLoadDepthList();
         }
      });
           row1.add(markButton);
      row1.add(removeButton);
      row1.add(removeAllButton);
      row1.add(saveAsButton);
      row1.add(loadButton);  
      buttonPanel.add(row1);
      
      JPanel row2 = new JPanel(new FlowLayout());
      activateDepthList_ = new JCheckBox("Activate");
      row2.add(activateDepthList_); 
      activateDepthList_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (activateDepthList_.isSelected()) {
               applyDepthSetting();
            }
         }
      });
      buttonPanel.add(row2);
      panel.add(buttonPanel,BorderLayout.PAGE_END);
   }
   
   private void createStitchPanel() {
      JPanel panel = createPanel("Stitch last time point", 405, 235, 650, 365);
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      
      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));      
      row1.add(new JLabel("Layout:"));
      invertXCheckBox_ = new JCheckBox("Flip X");
      invertXCheckBox_.setSelected(prefs_.getBoolean(INVERT_X, false));
      invertYCheckBox_ = new JCheckBox("Flip Y");
      invertYCheckBox_.setSelected(prefs_.getBoolean(INVERT_Y, false));
      swapXandYCheckBox_ = new JCheckBox("Transpose");
      swapXandYCheckBox_.setSelected(prefs_.getBoolean(SWAP_X_AND_Y, false));
      row1.add(invertXCheckBox_);
      row1.add(invertYCheckBox_);
      row1.add(swapXandYCheckBox_);
      panel.add(row1);

      JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
      drawPosNames_ = new JCheckBox("Show position names");
      drawGrid_ = new JCheckBox("Draw grid");
      row2.add(drawPosNames_);
      row2.add(drawGrid_);
      panel.add(row2);
      
      stitchButton_ = new JButton("Stitch: ");
      stitchButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            VirtualAcquisitionDisplay vadToStitch = null;
            int index = windowsToStitchCombo_.getSelectedIndex();
            if (index < availableVADs_.size()) {
               vadToStitch = availableVADs_.get(index);
            }
            stitcher_.setStitchParameters(invertXCheckBox_.isSelected(), invertYCheckBox_.isSelected(),
                    swapXandYCheckBox_.isSelected(), drawPosNames_.isSelected(), drawGrid_.isSelected(),
                    vadToStitch);
            new Thread(new Runnable() {

               @Override
               public void run() {
                  stitcher_.createStitchedFromCurrentFrame();
               }
            }).start();
         }
      });
      windowsToStitchCombo_ = new JComboBox();
      windowsToStitchCombo_.setPreferredSize(new Dimension(155,23));
      JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
      row3.add(stitchButton_);
      row3.add(windowsToStitchCombo_);
      panel.add(row3);
   }

   private void createGridPanel() {
      JPanel panel = createPanel("Create multi-position grid", 405, 140, 650, 235);
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

      JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
      row1.add(new JLabel("Pixel overlap X "));
      gridOverlapXSpinner_ = new JSpinner();
      gridOverlapXSpinner_.setModel(new SpinnerNumberModel(0, -1000, 1000, 1));
      gridOverlapXSpinner_.setPreferredSize(new Dimension(40, 22));
      row1.add(gridOverlapXSpinner_);
      row1.add(new JLabel("  Y  "));
      gridOverlapYSpinner_ = new JSpinner();
      gridOverlapYSpinner_.setModel(new SpinnerNumberModel(0, -1000, 1000, 1));
      gridOverlapYSpinner_.setPreferredSize(new Dimension(40, 22));
      row1.add(gridOverlapYSpinner_);
      panel.add(row1);
      
      
      JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
      gridSizeXSpinner_ = new JSpinner();
      gridSizeXSpinner_.setModel(new SpinnerNumberModel(3, 1, 1000, 1));
      gridSizeXSpinner_.setPreferredSize(new Dimension(40, 22));
      row2.add(gridSizeXSpinner_);
      row2.add(new JLabel("by"));
      gridSizeYSpinner_ = new JSpinner();
      gridSizeYSpinner_.setModel(new SpinnerNumberModel(3, 1, 1000, 1));
      gridSizeYSpinner_.setPreferredSize(new Dimension(40, 22));
      row2.add(gridSizeYSpinner_);
      row2.add(new JLabel("grid   "));
      JButton generateGridButton = new JButton("Generate");
      generateGridButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            onGenerateGrid();
         }
      });
      row2.add(generateGridButton);
      panel.add(row2);
   }

   private void createPMTPanel() {    
      JPanel panel = createPanel("PMT gain (V)", 5, 140, 400, 320);
      panel.setLayout(new BorderLayout());

      pmtTable_ = new JTable();
      pmtTable_.setAutoCreateColumnsFromModel(false);

      pmtData_ = new PMTDataModel();
      pmtTable_.setModel(pmtData_);

      int firstColWidth = 80;
      pmtTable_.addColumn(new TableColumn(0, firstColWidth, null, null));
      pmtTable_.addColumn(new TableColumn(1, 309, new SliderCellRenderer(), new SliderCellEditor(this)));

      JScrollPane sp = new JScrollPane();
      sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      sp.setViewportView(pmtTable_);
      panel.add(sp, BorderLayout.CENTER);

   }

   private void createZPanel() {
      JPanel panel = createPanel("Z Position (" + (char) 956  + "m)", 5, 84, 650, 134);
      panel.setLayout(new BorderLayout());
      pifocSlider_ = new SliderPanel();
      pifocSlider_.addEditActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            onPifocAction();
         }
      });
      pifocSlider_.addSliderMouseListener(new MouseAdapter() {

         public void mouseReleased(MouseEvent e) {
            onPifocAction();
         }
         public void mousePressed(MouseEvent e) {}
      });
      pifocSlider_.setBorder(BorderFactory.createEmptyBorder(1,4,4,4));
      panel.add(pifocSlider_, BorderLayout.CENTER );
   }

   private void createExcitationPanel() {
      JPanel panel = createPanel("Excitation (EOM Voltage)", 5, 5, 650, 86);
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
      
      JPanel row1 = new JPanel(new BorderLayout());
      row1.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
      row1.add(new JLabel("Laser-1  "), BorderLayout.LINE_START);
      laserSlider1_ = new SliderPanel();
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
      row1.add(laserSlider1_, BorderLayout.CENTER);

      
      JPanel row2 = new JPanel(new BorderLayout());
      row2.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
      row2.add(new JLabel("Laser-2  "), BorderLayout.LINE_START);
      laserSlider2_ = new SliderPanel();
      laserSlider2_.setLimits(0, 100);
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
      row2.add(laserSlider2_, BorderLayout.CENTER);
      
      panel.add(row1);
      panel.add(row2); 
   }
   
   private JPanel createPanel(String text, int left, int top, int right, int bottom) {
      LabelPanel thePanel = new LabelPanel(text);
     
      thePanel.setTitleFont(new Font("Dialog", Font.BOLD, 12));
      thePanel.setBounds(left, top, right - left, bottom - top);
//      dayBorder_ = BorderFactory.createEtchedBorder();
//      nightBorder_ = BorderFactory.createEtchedBorder(Color.gray, Color.darkGray);

      //updatePanelBorder(thePanel);
      thePanel.setLayout(null);
      getContentPane().add(thePanel);
      return thePanel;
   }
    
   @Override
   public void focusReceived(ImageWindow iw) {
      //change only if number of windows changes?
      int[] ids = WindowManager.getIDList();
      availableVADs_ = new ArrayList<VirtualAcquisitionDisplay>();
      for (int id : ids) {
         ImagePlus ip = WindowManager.getImage(id);
         VirtualAcquisitionDisplay vad = VirtualAcquisitionDisplay.getDisplay(ip);
         if (vad != null && vad.getNumPositions() > 1) {
            availableVADs_.add(vad);
         }
      }
      
      String[] names = new String[availableVADs_.size()];
      for (int i = 0; i < names.length; i++) {
         names[i] = availableVADs_.get(i).getImagePlus().getTitle();
      }
      windowsToStitchCombo_.setModel(new DefaultComboBoxModel(names));
      windowsToStitchCombo_.setSelectedIndex(names.length - 1);
   }
   
   private void initializeDepthListRunnable() {
      AcquisitionEngine acqEng = MMStudioMainFrame.getInstance().getAcquisitionEngine();
      acqEng.attachRunnable(-1, -1, -1, -1, new Runnable() {
         @Override
         public void run() {
            //apply depth list settings  
            if (activateDepthList_.isSelected()) {
               applyDepthSetting();
            }
            //refresh GUI on each image so it reflects depth list changes
            refreshGUI();          
         }
      });
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
//         labelListName_.setText(depthFile_.getName());
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
//         labelListName_.setText(depthFile_.getName());
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
         } catch (Exception e) {
            handleError(e.getMessage());
         }
      }
      activateDepthList_.setSelected(true);
   }

   protected void onGenerateGrid() {
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

      ArrayList<MultiStagePosition> positions = new ArrayList<MultiStagePosition>();

      int pixelOverlapX = (Integer) gridOverlapXSpinner_.getValue();
      int pixelOverlapY = (Integer) gridOverlapYSpinner_.getValue();
      int xSize = (Integer) gridSizeXSpinner_.getValue();
      int ySize = (Integer) gridSizeYSpinner_.getValue();
      
      
      for (int gridX = 0; gridX < xSize; gridX++) {
         double xPos = x[0] + (gridX - (xSize - 1) / 2.0) * (width - pixelOverlapX) * pixSize;
         for (int gridY = 0; gridY < ySize; gridY++) {
            double yPos = y[0] + (gridY - (ySize - 1) / 2.0) * (height - pixelOverlapY) * pixSize;
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
            int row = swapXY ? gridY : gridX;
            int col = swapXY ? gridX : gridY;
            String lab = new String("Grid_" + row + "_" + col);
            mpl.setLabel(lab);
            mpl.setGridCoordinates(row, col);
            positions.add(mpl);
         }
      }
   
      try {
         PositionList list = app_.getPositionList();
         list.clearAllPositions();
         for (MultiStagePosition p : positions) {
            list.addPosition(p);
         }
         list.notifyChangeListeners();
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

   public void applyDepthSetting() {
      try {
         DepthSetting ds = depthData_.getInterpolatedDepthSetting(core_.getPosition(core_.getFocusDevice()));
         core_.setProperty(LASER_EOM_1, "Volts", Double.toString(ds.eomVolts1_));
         core_.setProperty(LASER_EOM_2, "Volts", Double.toString(ds.eomVolts2_));
         for (int i = 0; i < ds.pmts.length; i++) {
            core_.setProperty(ds.pmts[i].name, "Volts", Double.toString(ds.pmts[i].volts));
         }
         refreshGUI();
      } catch (Exception e) {
         handleError(e.getMessage());
         return;
      }
   }

   protected void onDepthRemoveAll() {
      activateDepthList_.setSelected(false);
      depthData_.clear();
   }

   protected void onDepthRemove() {
      activateDepthList_.setSelected(false);
      int idx = depthTable_.getSelectedRow();
      depthData_.deleteDepthSetting(idx);
   }

   private void onDepthMark() {
      activateDepthList_.setSelected(false);
      try {
         double z = core_.getPosition(core_.getFocusDevice());
         double v1 = Double.parseDouble(core_.getProperty(LASER_EOM_1, "Volts"));
         double v2 = Double.parseDouble(core_.getProperty(LASER_EOM_2, "Volts"));
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
         if (activateDepthList_.isSelected()) {
            applyDepthSetting();
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

   protected void saveSettings() {
      Rectangle r = getBounds();
      prefs_.putInt(PANEL_X, r.x);
      prefs_.putInt(PANEL_Y, r.y);
      prefs_.putBoolean(INVERT_X, invertXCheckBox_.isSelected());
      prefs_.putBoolean(INVERT_Y, invertYCheckBox_.isSelected());
      prefs_.putBoolean(SWAP_X_AND_Y, swapXandYCheckBox_.isSelected());
      savePosition();
   }

   protected void loadSettings() {
      loadPosition(100, 100);
   }

   public void setApp(ScriptInterface app) {
      app_ = app;
      initialize();
   }

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

   }

   private void refreshGUI() {
      try {
         core_.logMessage("Refreshing Two Photon plugin from hardware", true);
         //with devices on a separate thread during acquisition
         
         pifocSlider_.setText(core_.getProperty(Z_STAGE, "Position"));
         laserSlider1_.setText(core_.getProperty(LASER_EOM_1, "Volts"));
         laserSlider2_.setText(core_.getProperty(LASER_EOM_2, "Volts"));
         pixelSizeCombo_.setSelectedItem(core_.getProperty(RESOLUTION, "Label"));

         PMTDataModel pmtdata = (PMTDataModel) pmtTable_.getModel();
         pmtdata.refresh();

      } catch (Exception ex) {
         ReportingUtils.logError(ex.getMessage());
      }
   }

   /**
    * Displays error message in a dialog box.
    */
   private void handleError(String message) {
      JOptionPane.showMessageDialog(this, message);
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
   }
   
   public class LabelPanel extends JPanel {

      public ComponentTitledBorder compTitledBorder;
      public boolean borderSet_ = false;
      public Component titleComponent;

      public LabelPanel(String title) {
         super();
         titleComponent = new JLabel(title);
         JLabel label = (JLabel) titleComponent;
         label.setOpaque(true);
         label.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
         compTitledBorder = new ComponentTitledBorder(label, this, BorderFactory.createEtchedBorder());
         this.setBorder(compTitledBorder);
         borderSet_ = true;
      }
      
      @Override
      public void setBorder(Border border) {
         if (compTitledBorder != null && borderSet_) {
            compTitledBorder.setBorder(border);
         } else {
            super.setBorder(border);
         }
      }

      @Override
      public Border getBorder() {
         return compTitledBorder;
      }

      public void setTitleFont(Font font) {
         titleComponent.setFont(font);
      }
   }
   
}
