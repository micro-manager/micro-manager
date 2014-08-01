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

import MMCustomization.AcquisitionWrapperEngineAdapter;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.io.Opener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumn;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.ComponentTitledBorder;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.StagePosition;
import org.micromanager.utils.*;



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

   public static final String menuName = "100X | 2Photon";
   public static final String tooltipDescription = "2Photon control panel";

   
   // preference keys and other string constants
   static private final String PANEL_X = "panel_x";
   static private final String PANEL_Y = "panel_y";
   static private final String VERSION_INFO = "3.1";
   static private final String COPYRIGHT_NOTICE = "Copyright by 100X, 2011";
   static private final String DESCRIPTION = "Two Photon control module";
   static private final String INFO = "Not available";
   private static Preferences prefs_;
   private CMMCore core_;
   private ScriptInterface app_;
   private JTable pmtTable_;
   private PMTDataModel pmtData_;
   private JTable depthTable_;
   private DepthDataModel depthData_;
   private static TwoPhotonControl twoPC_;
   
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
   private static JProgressBar hdfProgressBar_, filterProgressBar_;
   private JPanel imarisPanel_, stitchPanel_, filteringPanel_;
   
   private File depthFile_;

   private String posListDir_;
   private static final String DEFAULT_DEPTH_FNAME = "default_depth_list.dlf";
   private AcquisitionWrapperEngineAdapter acqEngAdapter_;


private ActionListener pixelSizeListener_;

private AcquisitionStitcher stitcher_ = new AcquisitionStitcher();
private JButton stitchButton_;
private JComboBox windowsToStitchCombo_;
private ArrayList<VirtualAcquisitionDisplay> availableVADs_;
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
       twoPC_ = this;
       
      setLocation(-3, -31);

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
      createDepthPanel();

      pixelSizeCombo_ = new JComboBox();
      pixelSizeListener_ = new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            onPixelSize();
         }
      };
      pixelSizeCombo_.addActionListener(pixelSizeListener_);
      pixelSizeCombo_.setBounds(460, 390 , 184, 20);
      getContentPane().add(pixelSizeCombo_);
      JLabel lblPixelSize = new JLabel("Pixel size");
      lblPixelSize.setBounds(410, 394, 59, 14);
      getContentPane().add(lblPixelSize);
      
      if (prefs_.getBoolean(SettingsDialog.DYNAMIC_STITCH, false)) {
         addDynamicStitchControls();
      } else {
         createStitchPanel();
      }
 
      //add settings button
      JButton settings = new JButton("Settings");
      getContentPane().add(settings);
      settings.setBounds(485, 480, 80, 25);
      settings.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            new SettingsDialog(prefs_, TwoPhotonControl.this);
         }   
      });
      
      //Add credit
      JLabel lblCopyrightxImaging = new JLabel("Copyright 100X Imaging Inc, 2010");
      lblCopyrightxImaging.setBounds(438, 510, 172, 14);
      getContentPane().add(lblCopyrightxImaging);
      JLabel henry = new JLabel("Improved by Henry Pinkard, 2012-2014");
      henry.setBounds(438, 530, 205, 14);
      getContentPane().add(henry);
      
      
      // load previous settings from the registry
      loadSettings();

      GUIUtils.registerImageFocusListener(this);
      //attach depth list runnable
      MMStudio.getInstance().getAcquisitionEngine2010().attachRunnable(-1, -1, -1, -1,
              new Runnable() {
                 @Override
                 public void run() {        
                    applyDepthSetting(-1,0);                  
                 }
              });
   }

   private void addDynamicStitchControls() {
      createProgressPanels();
//      JButton exploreButton = new JButton("Explore");
//      getContentPane().add(exploreButton);
//      exploreButton.setBounds(410, 450, 130, 20);
//      exploreButton.addActionListener(new ActionListener() {
//
//         @Override
//         public void actionPerformed(ActionEvent e) {
//         }
//      });
   }

   public void acitvateDynamicStitching() {
      getContentPane().remove(stitchPanel_);
      addDynamicStitchControls();
      clearSpaceInIMSFileCache();
      try {
         acqEngAdapter_ = new AcquisitionWrapperEngineAdapter(this, prefs_);
      } catch (NoSuchFieldException ex) {
         ReportingUtils.showError("Couldn't substitute acquisition engine");
      }
      
      getContentPane().invalidate();
      getContentPane().validate();
      getContentPane().paint(getContentPane().getGraphics());
      
   }
   
   public static void updateFilterQueueSize(int n, int max) {
      filterProgressBar_.setMaximum(max);
      filterProgressBar_.setValue(n);
   }
   
   public static void updateHDFQueueSize(int n, int max) {
      hdfProgressBar_.setMaximum(max);
      hdfProgressBar_.setValue(n);
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
            applyDepthSetting(-1,0);
         }
      });
      buttonPanel.add(row2);
      panel.add(buttonPanel,BorderLayout.PAGE_END);
   }
   
   private void createProgressPanels() {
      filteringPanel_ = createPanel("Image processing queue", 405, 235, 650, 285);
      filteringPanel_.setLayout(new BorderLayout());
      filterProgressBar_ = new JProgressBar(0,1);
      filteringPanel_.add(filterProgressBar_, BorderLayout.CENTER);
      filteringPanel_.setVisible(true);
      
      imarisPanel_ = createPanel("Imaris file writing queue", 405, 290, 650, 340);
      imarisPanel_.setLayout(new BorderLayout());
      hdfProgressBar_ = new JProgressBar(0,1);
      imarisPanel_.add(hdfProgressBar_, BorderLayout.CENTER);
      imarisPanel_.setVisible(true);
   }
   
   private void createStitchPanel() {
      stitchPanel_ = createPanel("Stitch last time point", 405, 235, 650, 330);
      stitchPanel_.setLayout(new BoxLayout(stitchPanel_, BoxLayout.Y_AXIS));

      JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
      drawPosNames_ = new JCheckBox("Show position names");
      drawGrid_ = new JCheckBox("Draw grid");
      drawPosNames_.setSelected(true);
      drawGrid_.setSelected(true); 
      row2.add(drawPosNames_);
      row2.add(drawGrid_);
      stitchPanel_.add(row2);
      
      stitchButton_ = new JButton("Stitch: ");
      stitchButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {           
            VirtualAcquisitionDisplay vadToStitch = null;
            int index = windowsToStitchCombo_.getSelectedIndex();
            if (index < availableVADs_.size()) {
               vadToStitch = availableVADs_.get(index);
            }
            stitcher_.setStitchParameters(drawPosNames_.isSelected(), drawGrid_.isSelected(),
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
      stitchPanel_.add(row3);
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
            generateGrid();
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
      if (ids == null) {
         return;
      }
      for (int id : ids) {
         ImagePlus ip = WindowManager.getImage(id);
         VirtualAcquisitionDisplay vad = VirtualAcquisitionDisplay.getDisplay(ip);
         if (vad != null && vad.getNumPositions() > 1) {
            availableVADs_.add(vad);
         }
      }
      
      String[] names = new String[availableVADs_.size()];
      for (int i = 0; i < names.length; i++) {
         names[i] = availableVADs_.get(i).getImagePlus().getWindow().getTitle();
      }
      windowsToStitchCombo_.setModel(new DefaultComboBoxModel(names));
      windowsToStitchCombo_.setSelectedIndex(names.length - 1);
   }
   
   private void clearSpaceInIMSFileCache() {
      new Thread(new Runnable() {
         @Override
         public void run() {
            //delete oldest directories first until enough space is cleared
            String path = prefs_.get(SettingsDialog.STITCHED_DATA_DIRECTORY, "");
            if (path.equals("")) {
               return;
            }
            File topDir = new File(path);
            File[] subDirs = topDir.listFiles();

            Arrays.sort(subDirs, new Comparator<File>() {

               @Override
               public int compare(File f1, File f2) {
                  return new Long(f1.lastModified()).compareTo(f2.lastModified());
               }
            });
            long bytesPerGig = 1024 * 1024 * 1024;
            int index = 0;
            ProgressBar progressBar = new ProgressBar("Clearing space in stitched data cache", 0, subDirs.length);
            progressBar.setProgress(index);
            progressBar.setVisible(true);
            if (subDirs != null && subDirs.length > 0) {
               while (topDir.getFreeSpace() / bytesPerGig
                       < prefs_.getLong(SettingsDialog.FREE_GB__MIN_IN_STITCHED_DATA, 200)) {
                  for (File f : subDirs[index].listFiles()) {
                     f.delete();
                  }
                  subDirs[index].delete();
                  subDirs[index] = null;
                  index++;
                  progressBar.setProgress(index);
                  if (index == subDirs.length) {
                     //all directories deleted
                     break;
                  }
               }
            }
            progressBar.setVisible(false);
         }
      }).start();
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

   protected void generateGrid() {
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
           core_.getXYPosition(xyStage, x, y);
       } catch (Exception ex) {
           ReportingUtils.showError("Get current stage position");
       }


      int pixelOverlapX = (Integer) gridOverlapXSpinner_.getValue();
      int pixelOverlapY = (Integer) gridOverlapYSpinner_.getValue();
      int xSize = (Integer) gridSizeXSpinner_.getValue();
      int ySize = (Integer) gridSizeYSpinner_.getValue();

      
     Util.createGrid(x[0], y[0], xSize, ySize, pixelOverlapX, pixelOverlapY);
      
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
   
   public void applyDepthSetting(int positionIndex, double focusOffset) {
      if (activateDepthList_.isSelected()) {
         int offset =  Util.getDepthListOffset(positionIndex);                             
         try {
             //need to disable live mode for some reason or crashes
             boolean live = app_.isLiveModeOn();
             app_.enableLiveMode(false);
                     
             //It takes time for the z on gen3 to get to its proper position,
             //for now, loop here until it settles so correct depth setting is applied          
            double zPos1 = 1000000;
            double zPos2 = core_.getPosition(Z_STAGE); 
//            int count = 0;
            while (Math.abs(zPos1 - zPos2) > 1) {
                zPos1 = zPos2;
                zPos2 = core_.getPosition(core_.getFocusDevice()); 
//                count++;
            }
//            System.out.println(count + " cycles to settle z");
            //Z should now have settled
                    
            DepthSetting ds = depthData_.getInterpolatedDepthSetting(zPos2 + offset + focusOffset);
            core_.setProperty(LASER_EOM_1, "Volts", Double.toString(ds.eomVolts1_));
            core_.setProperty(LASER_EOM_2, "Volts", Double.toString(ds.eomVolts2_));
            for (int i = 0; i < ds.pmts.length; i++) {
               core_.setProperty(ds.pmts[i].name, "Volts", Double.toString(ds.pmts[i].volts));
            }
            refreshGUI();
            app_.enableLiveMode(live);
         } catch (Exception e) {
            handleError(e.getMessage());
            return;
         }
      }
   }
   
   public void refreshGUI() {
      try {
         core_.logMessage("Refreshing Two Photon plugin from hardware", true);
         //with devices on a separate thread during acquisition
         
         core_.updateSystemStateCache();
//         pifocSlider_.setText(core_.getProperty(Z_STAGE, "Position"));
         pifocSlider_.setText(core_.getPosition(Z_STAGE) +"");
         laserSlider1_.setText(core_.getProperty(LASER_EOM_1, "Volts"));
         laserSlider2_.setText(core_.getProperty(LASER_EOM_2, "Volts"));
         pixelSizeCombo_.setSelectedItem(core_.getProperty(RESOLUTION, "Label"));

         PMTDataModel pmtdata = (PMTDataModel) pmtTable_.getModel();
         pmtdata.refresh();

      } catch (Exception ex) {
         ReportingUtils.logError(ex.getMessage());
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
         applyDepthSetting(-1,0);
         removeDepthListSelection();
         MMStudio.getInstance().updateZPos(z);
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
