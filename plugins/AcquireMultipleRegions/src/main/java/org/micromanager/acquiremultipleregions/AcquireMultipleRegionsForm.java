

package org.micromanager.acquiremultipleregions;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractListModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
import org.micromanager.Studio;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Datastore;
import org.micromanager.internal.positionlist.utils.TileCreator;
import org.micromanager.internal.positionlist.utils.ZGenerator;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * @author kthorn
 */
public class AcquireMultipleRegionsForm extends javax.swing.JFrame {
   private final Studio gui_;
   private final mmcorej.CMMCore mmc_;
   private final TileCreator tileCreator_;
   private final RegionListModel rlm_;
   private Region currentRegion_;
   private static final String MSG_PREFIX = "AcquireMultipleRegions: ";
   private final JTable axisTable_;
   private final AxisTableModel axisModel_;
   private final AxisList axisList_;
   public int userParameter1;

   /**
    * Creates new form AcquireMultipleRegionsForm
    *
    * @param gui
    */
   public AcquireMultipleRegionsForm(Studio gui) {
      rlm_ = new RegionListModel();
      gui_ = gui;
      mmc_ = gui_.core();
      tileCreator_ = new TileCreator(mmc_);
      initComponents();
      currentRegion_ =
            new Region(new PositionList(), directoryText.getText(), filenameText.getText());

      //From PositionListDlg
      axisTable_ = new JTable();
      axisTable_.setFont(new Font("Arial", Font.PLAIN, 10));
      axisList_ = new AxisList();
      axisModel_ = new AxisTableModel();
      axisTable_.setModel(axisModel_);
      axisPane.setViewportView(axisTable_);

      //populate zDropdown
      zTypeDropdown.setModel(new DefaultComboBoxModel(ZGenerator.Type.values()));

      userParameter1 = 1;
   }

   //From PositionListDlg
   private class AxisData {
      private boolean use_;
      private final String axisName_;

      public AxisData(boolean use, String axisName) {
         use_ = use;
         axisName_ = axisName;
      }

      public boolean getUse() {
         return use_;
      }

      public String getAxisName() {
         return axisName_;
      }

      public void setUse(boolean use) {
         use_ = use;
      }
   }

   //From PositionListDlg
   public class AxisList {
      private ArrayList<AxisData> axisList_;

      public AxisList() {
         this.axisList_ = new ArrayList<AxisData>();
         // Initialize the axisList.
         try {
            // add 1D stages
            StrVector stages = mmc_.getLoadedDevicesOfType(DeviceType.StageDevice);
            for (int i = 0; i < stages.size(); i++) {
               axisList_.add(new AxisData(true, stages.get(i)));
            }
         } catch (Exception e) {
            handleError(e);
         }
      }

      private AxisData get(int i) {
         if (i >= 0 && i < axisList_.size()) {
            return axisList_.get(i);
         }
         return null;
      }

      private int getNumberOfPositions() {
         return axisList_.size();
      }

      public boolean use(String axisName) {
         for (int i = 0; i < axisList_.size(); i++) {
            if (axisName.equals(get(i).getAxisName())) {
               return get(i).getUse();
            }
         }
         // not in the list??  It might be time to refresh the list.  
         return true;
      }
   }

   //From PositionListDlg
   private class AxisTableModel extends AbstractTableModel {
      private boolean isEditable_ = true;
      public final String[] columnNames_ = new String[] {
            "Use",
            "Axis"
      };

      @Override
      public int getRowCount() {
         return axisList_.getNumberOfPositions();
      }

      @Override
      public int getColumnCount() {
         return columnNames_.length;
      }

      @Override
      public String getColumnName(int columnIndex) {
         return columnNames_[columnIndex];
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
         AxisData aD = axisList_.get(rowIndex);
         if (aD != null) {
            if (columnIndex == 0) {
               return aD.getUse();
            } else if (columnIndex == 1) {
               return aD.getAxisName();
            }
         }
         return null;
      }

      @Override
      public Class<?> getColumnClass(int c) {
         return getValueAt(0, c).getClass();
      }

      public void setEditable(boolean state) {
         isEditable_ = state;
         if (state) {
            for (int i = 0; i < getRowCount(); i++) {

            }
         }
      }

      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
         if (columnIndex == 0) {
            return isEditable_;
         }
         return false;
      }

      @Override
      public void setValueAt(Object value, int rowIndex, int columnIndex) {
         if (columnIndex == 0) {
            axisList_.get(rowIndex).setUse((Boolean) value);
            // prefs_.putBoolean(axisList_.get(rowIndex).getAxisName(), (Boolean) value);
         }
         fireTableCellUpdated(rowIndex, columnIndex);
      }
   }

   private class RegionListModel extends AbstractListModel {
      public RegionList regions_;

      private RegionListModel() {
         this.regions_ = new RegionList();
      }

      @Override
      public int getSize() {
         return regions_.getNumberOfRegions();
      }

      @Override
      public Object getElementAt(int index) {
         Region r = regions_.getRegion(index);
         return r.name();
      }

      public void addRegion(Region r) {
         regions_.addRegion(r);
         fireIntervalAdded(this, regions_.getNumberOfRegions(), regions_.getNumberOfRegions());
      }

      public void deleteRegion(int index) {
         regions_.removeRegion(index);
         fireIntervalRemoved(this, index, index);
      }

      public Region getRegion(int index) {
         Region r = regions_.getRegion(index);
         return r;
      }

      public void clearRegions() {
         this.regions_ = new RegionList();
         fireIntervalAdded(this, 0, 0);
      }

      public void saveRegions(Path path) {
         this.regions_.saveRegions(path);
      }

      public void loadRegions(File f, File newDir) {
         clearRegions();
         int regionsLoaded = regions_.loadRegions(f, newDir);
         fireIntervalAdded(this, 0, regionsLoaded - 1);
      }
   }

   class AcqThread extends Thread {
      @Override
      public void run() {
         for (int i = 0; i < rlm_.getSize(); i++) {
            Region currRegion = rlm_.getRegion(i);
            ZGenerator.Type zGenType = (ZGenerator.Type) zTypeDropdown.getSelectedItem();
            String xyStage = mmc_.getXYStageDevice();
            StrVector zStages = new StrVector();
            for (int axNum = 0; axNum < axisList_.getNumberOfPositions(); axNum++) {
               AxisData ad = axisList_.get(axNum);
               if (ad.getUse()) {
                  zStages.add(ad.getAxisName());
               }
            }
            try {
               statusText.setText("Acquiring region " + String.valueOf(i));
               //turn on position list, turn off time lapse
               SequenceSettings.Builder currSettingsB =
                     gui_.acquisitions().getAcquisitionSettings().copyBuilder();
               currSettingsB.usePositionList(true);
               currSettingsB.numFrames(1);
               gui_.acquisitions().setAcquisitionSettings(currSettingsB.build());
               // TODO: ensure saving chooses the correct format. This logic
               // became lost in the MM2.0 refactoring.
               // gui_.compat().setImageSavingFormat(org.micromanager.acquisition.internal
               // .TaggedImageStorageMultipageTiff.class);
               // update positionlist with grid
               // gui_.positions().setPositionList(currRegion.tileGrid(getXFieldSize(),
               // getYFieldSize(), axisList_, zGenType_));

               double overlap = Double.parseDouble(overlapText.getText());
               double pixelSizeUm = mmc_.getPixelSizeUm();
               if (pixelSizeUm == 0.0) {
                  gui_.logs().showError("Pixel Size is 0. Must set pixel size.");
                  statusText.setText("Pixel Size is 0. Must set pixel size.");
                  return;
               }
               gui_.positions().setPositionList(
                     tileCreator_.createTiles(
                           overlap,
                           TileCreator.OverlapUnitEnum.PERCENT,
                           currRegion.positions.getPositions(),
                           pixelSizeUm,
                           "1",
                           xyStage,
                           zStages,
                           zGenType
                     )
               );

               gui_.app().refreshGUI();
               Datastore store =
                     gui_.acquisitions().runAcquisition(currRegion.filename, currRegion.directory);
               store.freeze();
               gui_.displays().closeDisplaysFor(store);
               store.close();
               gui_.positions().getPositionList()
                     .save(Paths.get(store.getSavePath()).resolve("AMRposlist.pos").toFile());
            } catch (Exception ex) {
               handleError(ex);
            }
         }
         statusText.setText("Acquisition finished");
      }
   }


   /*
     Convenience function for logging errors.
     */
   private void handleError(Exception e) {
      ReportingUtils.showError(e);
   }

   /*
     Convenience function for logging messages with prefix indicating message
     is from AcquireMultipleRegions.
     */
   private void logMessage(String message) {
      ReportingUtils.logMessage(MSG_PREFIX + message);
   }

   private Region makeUniqueRegionName(Region r) {
      //update region filename and directory so its name is unique in regions_
      String filenameprefix;
      int trailingnumber;
      //regular expression for finding trailing _number
      Pattern trailingdigit = Pattern.compile("(.+_)(\\d+)");

      while (!rlm_.regions_.isFileNameUnique(r.directory, r.filename)) {
         //append _1 to filename if it doesn't end with _number
         //otherwise increment trailing number until it is unique
         Matcher matcher = trailingdigit.matcher(r.filename);
         if (matcher.matches()) {
            //update trailing number
            filenameprefix = matcher.group(1);
            trailingnumber = Integer.parseInt(matcher.group(2));
            r.filename = filenameprefix.concat(String.valueOf(trailingnumber + 1));
         } else {
            //append trailing digit
            r.filename = r.filename.concat("_1");
         }
      }
      return r;
   }

   private double getXFieldSize() {
      double fieldOverlap = Double.parseDouble(overlapText.getText()) / 100;
      double xFieldSize = mmc_.getPixelSizeUm() * mmc_.getImageWidth() * (1 - fieldOverlap);
      return xFieldSize;
   }

   private double getYFieldSize() {
      double fieldOverlap = Double.parseDouble(overlapText.getText()) / 100;
      double yFieldSize = mmc_.getPixelSizeUm() * mmc_.getImageHeight() * (1 - fieldOverlap);
      return yFieldSize;
   }

   /**
    * This method is called from within the constructor to initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is always
    * regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      zAxisButtonGroup = new javax.swing.ButtonGroup();
      jTabbedPane2 = new javax.swing.JTabbedPane();
      mainPanel = new javax.swing.JPanel();
      directoryText = new javax.swing.JTextField();
      jLabel4 = new javax.swing.JLabel();
      directoryButton = new javax.swing.JButton();
      jLabel1 = new javax.swing.JLabel();
      jScrollPane1 = new javax.swing.JScrollPane();
      acquireList = new javax.swing.JList();
      jLabel2 = new javax.swing.JLabel();
      filenameText = new javax.swing.JTextField();
      addPointToRegion = new javax.swing.JButton();
      regionText = new javax.swing.JLabel();
      addPositionList = new javax.swing.JButton();
      deleteRegion = new javax.swing.JButton();
      deleteAllButton = new javax.swing.JButton();
      startAcquisition = new javax.swing.JButton();
      jPanel1 = new javax.swing.JPanel();
      jLabel3 = new javax.swing.JLabel();
      gotoTop = new javax.swing.JButton();
      gotoCenter = new javax.swing.JButton();
      gotoBottom = new javax.swing.JButton();
      gotoRight = new javax.swing.JButton();
      gotoLeft = new javax.swing.JButton();
      statusText = new javax.swing.JLabel();
      configPanel = new javax.swing.JPanel();
      overlapText = new javax.swing.JTextField();
      jLabel5 = new javax.swing.JLabel();
      axisPane = new javax.swing.JScrollPane();
      jLabel6 = new javax.swing.JLabel();
      mjLabel7 = new javax.swing.JLabel();
      zTypeDropdown = new javax.swing.JComboBox();
      loadRegionsButton = new javax.swing.JButton();

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

      directoryText.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            directoryTextActionPerformed(evt);
         }
      });

      jLabel4.setText("Directory:");
      jLabel4.setFocusable(false);

      directoryButton.setText("...");
      directoryButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            directoryButtonActionPerformed(evt);
         }
      });

      jLabel1.setText("Regions to Acquire");
      jLabel1.setFocusable(false);

      acquireList.setModel(rlm_);
      acquireList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
      jScrollPane1.setViewportView(acquireList);

      jLabel2.setText("Filename:");
      jLabel2.setFocusable(false);

      addPointToRegion.setText("Add Point to Current Region");
      addPointToRegion.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            addPointToRegionActionPerformed(evt);
         }
      });

      regionText.setText("Current Region: 0 images");

      addPositionList.setText("Save Region for Acquisition");
      addPositionList.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            addPositionListActionPerformed(evt);
         }
      });

      deleteRegion.setText("Delete Selected Region");
      deleteRegion.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            deleteRegionActionPerformed(evt);
         }
      });

      deleteAllButton.setText("Clear Regions");
      deleteAllButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            deleteAllButtonActionPerformed(evt);
         }
      });

      startAcquisition.setText("Acquire All Regions");
      startAcquisition.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            startAcquisitionActionPerformed(evt);
         }
      });

      jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

      jLabel3.setText("Go To Selected Region");

      gotoTop.setText("Top");
      gotoTop.setMaximumSize(new java.awt.Dimension(67, 23));
      gotoTop.setMinimumSize(new java.awt.Dimension(67, 23));
      gotoTop.setPreferredSize(new java.awt.Dimension(67, 23));
      gotoTop.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            gotoTopActionPerformed(evt);
         }
      });

      gotoCenter.setText("Center");
      gotoCenter.setMaximumSize(new java.awt.Dimension(67, 23));
      gotoCenter.setMinimumSize(new java.awt.Dimension(67, 23));
      gotoCenter.setPreferredSize(new java.awt.Dimension(67, 23));
      gotoCenter.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            gotoCenterActionPerformed(evt);
         }
      });

      gotoBottom.setText("Bottom");
      gotoBottom.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            gotoBottomActionPerformed(evt);
         }
      });

      gotoRight.setText("Right");
      gotoRight.setMaximumSize(new java.awt.Dimension(67, 23));
      gotoRight.setMinimumSize(new java.awt.Dimension(67, 23));
      gotoRight.setPreferredSize(new java.awt.Dimension(67, 23));
      gotoRight.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            gotoRightActionPerformed(evt);
         }
      });

      gotoLeft.setText("Left");
      gotoLeft.setToolTipText("");
      gotoLeft.setMaximumSize(new java.awt.Dimension(67, 23));
      gotoLeft.setMinimumSize(new java.awt.Dimension(67, 23));
      gotoLeft.setPreferredSize(new java.awt.Dimension(67, 23));
      gotoLeft.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            gotoLeftActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
      jPanel1.setLayout(jPanel1Layout);
      jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                  .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(gotoLeft, javax.swing.GroupLayout.PREFERRED_SIZE,
                              javax.swing.GroupLayout.DEFAULT_SIZE,
                              javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(
                                    javax.swing.GroupLayout.Alignment.CENTER)
                              .addGroup(jPanel1Layout.createSequentialGroup()
                                    .addComponent(gotoCenter,
                                          javax.swing.GroupLayout.PREFERRED_SIZE,
                                          javax.swing.GroupLayout.DEFAULT_SIZE,
                                          javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGap(3, 3, 3)
                                    .addComponent(gotoRight, javax.swing.GroupLayout.PREFERRED_SIZE,
                                          javax.swing.GroupLayout.DEFAULT_SIZE,
                                          javax.swing.GroupLayout.PREFERRED_SIZE))
                              .addComponent(gotoBottom, javax.swing.GroupLayout.Alignment.LEADING)
                              .addComponent(gotoTop, javax.swing.GroupLayout.Alignment.LEADING,
                                    javax.swing.GroupLayout.PREFERRED_SIZE,
                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                    javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                  .addGroup(javax.swing.GroupLayout.Alignment.TRAILING,
                        jPanel1Layout.createSequentialGroup()
                              .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE,
                                    Short.MAX_VALUE)
                              .addComponent(jLabel3)
                              .addGap(56, 56, 56))
      );
      jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                  .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(gotoTop, javax.swing.GroupLayout.PREFERRED_SIZE,
                              javax.swing.GroupLayout.DEFAULT_SIZE,
                              javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(
                                    javax.swing.GroupLayout.Alignment.BASELINE)
                              .addComponent(gotoCenter, javax.swing.GroupLayout.PREFERRED_SIZE,
                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                    javax.swing.GroupLayout.PREFERRED_SIZE)
                              .addComponent(gotoRight, javax.swing.GroupLayout.PREFERRED_SIZE,
                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                    javax.swing.GroupLayout.PREFERRED_SIZE)
                              .addComponent(gotoLeft, javax.swing.GroupLayout.PREFERRED_SIZE,
                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                    javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(gotoBottom)
                        .addGap(0, 12, Short.MAX_VALUE))
      );

      statusText.setText("Waiting for user to enter regions...");

      javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
      mainPanel.setLayout(mainPanelLayout);
      mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                  .addGroup(mainPanelLayout.createSequentialGroup()
                        .addGroup(mainPanelLayout.createParallelGroup(
                                    javax.swing.GroupLayout.Alignment.LEADING)
                              .addGroup(mainPanelLayout.createSequentialGroup()
                                    .addGap(10, 10, 10)
                                    .addGroup(mainPanelLayout.createParallelGroup(
                                                javax.swing.GroupLayout.Alignment.LEADING)
                                          .addComponent(jLabel4)
                                          .addComponent(filenameText,
                                                javax.swing.GroupLayout.PREFERRED_SIZE, 191,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                          .addComponent(jLabel2)))
                              .addGroup(mainPanelLayout.createSequentialGroup()
                                    .addContainerGap()
                                    .addComponent(directoryText,
                                          javax.swing.GroupLayout.PREFERRED_SIZE, 140,
                                          javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGap(18, 18, 18)
                                    .addComponent(directoryButton))
                              .addGroup(mainPanelLayout.createSequentialGroup()
                                    .addContainerGap()
                                    .addGroup(mainPanelLayout.createParallelGroup(
                                                javax.swing.GroupLayout.Alignment.LEADING)
                                          .addComponent(regionText,
                                                javax.swing.GroupLayout.DEFAULT_SIZE, 214,
                                                Short.MAX_VALUE)
                                          .addComponent(statusText)
                                          .addComponent(addPositionList)
                                          .addComponent(startAcquisition,
                                                javax.swing.GroupLayout.PREFERRED_SIZE, 189,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                          .addComponent(addPointToRegion)))
                              .addGroup(mainPanelLayout.createSequentialGroup()
                                    .addContainerGap()
                                    .addGroup(mainPanelLayout.createParallelGroup(
                                                javax.swing.GroupLayout.Alignment.TRAILING)
                                          .addComponent(deleteRegion,
                                                javax.swing.GroupLayout.PREFERRED_SIZE, 189,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                          .addComponent(deleteAllButton,
                                                javax.swing.GroupLayout.Alignment.LEADING,
                                                javax.swing.GroupLayout.PREFERRED_SIZE, 189,
                                                javax.swing.GroupLayout.PREFERRED_SIZE))))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(mainPanelLayout.createParallelGroup(
                                    javax.swing.GroupLayout.Alignment.LEADING)
                              .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0,
                                    Short.MAX_VALUE)
                              .addGroup(mainPanelLayout.createSequentialGroup()
                                    .addGroup(mainPanelLayout.createParallelGroup(
                                                javax.swing.GroupLayout.Alignment.LEADING)
                                          .addComponent(jLabel1)
                                          .addComponent(jPanel1,
                                                javax.swing.GroupLayout.PREFERRED_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGap(0, 10, Short.MAX_VALUE)))
                        .addContainerGap())
      );
      mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                  .addGroup(mainPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(mainPanelLayout.createParallelGroup(
                                    javax.swing.GroupLayout.Alignment.LEADING)
                              .addGroup(mainPanelLayout.createSequentialGroup()
                                    .addComponent(jLabel4)
                                    .addPreferredGap(
                                          javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addGroup(mainPanelLayout.createParallelGroup(
                                                javax.swing.GroupLayout.Alignment.BASELINE)
                                          .addComponent(directoryButton)
                                          .addComponent(directoryText,
                                                javax.swing.GroupLayout.PREFERRED_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addPreferredGap(
                                          javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(jLabel2)
                                    .addGap(1, 1, 1)
                                    .addComponent(filenameText,
                                          javax.swing.GroupLayout.PREFERRED_SIZE,
                                          javax.swing.GroupLayout.DEFAULT_SIZE,
                                          javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGap(44, 44, 44)
                                    .addComponent(addPointToRegion,
                                          javax.swing.GroupLayout.PREFERRED_SIZE, 41,
                                          javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(
                                          javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(regionText,
                                          javax.swing.GroupLayout.PREFERRED_SIZE, 27,
                                          javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(
                                          javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(addPositionList,
                                          javax.swing.GroupLayout.PREFERRED_SIZE, 41,
                                          javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(
                                          javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(deleteRegion,
                                          javax.swing.GroupLayout.PREFERRED_SIZE, 41,
                                          javax.swing.GroupLayout.PREFERRED_SIZE))
                              .addGroup(mainPanelLayout.createSequentialGroup()
                                    .addComponent(jLabel1)
                                    .addPreferredGap(
                                          javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(jScrollPane1,
                                          javax.swing.GroupLayout.PREFERRED_SIZE, 274,
                                          javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(mainPanelLayout.createParallelGroup(
                                    javax.swing.GroupLayout.Alignment.LEADING)
                              .addGroup(mainPanelLayout.createSequentialGroup()
                                    .addComponent(deleteAllButton,
                                          javax.swing.GroupLayout.PREFERRED_SIZE, 41,
                                          javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(
                                          javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(startAcquisition,
                                          javax.swing.GroupLayout.PREFERRED_SIZE, 41,
                                          javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(
                                          javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(statusText))
                              .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE,
                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                    javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      jTabbedPane2.addTab("Main", mainPanel);

      overlapText.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
      overlapText.setText("10");

      jLabel5.setText("% overlap between tiles");

      jLabel6.setText("Which axes should be set at each position?");

      mjLabel7.setText("How to handle movement along those axes?");

      zTypeDropdown.setModel(new javax.swing.DefaultComboBoxModel(
            new String[] {"Item 1", "Item 2", "Item 3", "Item 4"}));

      loadRegionsButton.setText("Load Regions From Folder");
      loadRegionsButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            loadRegionsButtonActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout configPanelLayout = new javax.swing.GroupLayout(configPanel);
      configPanel.setLayout(configPanelLayout);
      configPanelLayout.setHorizontalGroup(
            configPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                  .addGroup(configPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(configPanelLayout.createParallelGroup(
                                    javax.swing.GroupLayout.Alignment.LEADING)
                              .addComponent(axisPane, javax.swing.GroupLayout.PREFERRED_SIZE, 299,
                                    javax.swing.GroupLayout.PREFERRED_SIZE)
                              .addGroup(configPanelLayout.createSequentialGroup()
                                    .addComponent(overlapText,
                                          javax.swing.GroupLayout.PREFERRED_SIZE, 24,
                                          javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(
                                          javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(jLabel5))
                              .addComponent(jLabel6)
                              .addComponent(mjLabel7)
                              .addComponent(zTypeDropdown, javax.swing.GroupLayout.PREFERRED_SIZE,
                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                    javax.swing.GroupLayout.PREFERRED_SIZE)
                              .addComponent(loadRegionsButton,
                                    javax.swing.GroupLayout.PREFERRED_SIZE, 191,
                                    javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap(171, Short.MAX_VALUE))
      );
      configPanelLayout.setVerticalGroup(
            configPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                  .addGroup(configPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(configPanelLayout.createParallelGroup(
                                    javax.swing.GroupLayout.Alignment.BASELINE)
                              .addComponent(overlapText, javax.swing.GroupLayout.PREFERRED_SIZE,
                                    javax.swing.GroupLayout.DEFAULT_SIZE,
                                    javax.swing.GroupLayout.PREFERRED_SIZE)
                              .addComponent(jLabel5))
                        .addGap(7, 7, 7)
                        .addComponent(jLabel6)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(axisPane, javax.swing.GroupLayout.PREFERRED_SIZE, 100,
                              javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(mjLabel7)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(zTypeDropdown, javax.swing.GroupLayout.PREFERRED_SIZE,
                              javax.swing.GroupLayout.DEFAULT_SIZE,
                              javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(28, 28, 28)
                        .addComponent(loadRegionsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 47,
                              javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(172, Short.MAX_VALUE))
      );

      jTabbedPane2.addTab("Config", configPanel);

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                  .addComponent(jTabbedPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 487,
                        Short.MAX_VALUE)
      );
      layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                  .addComponent(jTabbedPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 494,
                        Short.MAX_VALUE)
      );

      pack();
   }

   private void loadRegionsButtonActionPerformed(
         java.awt.event.ActionEvent evt) {
      File f = FileDialogs.openDir(this, "Directory of .POS files",
            new FileDialogs.FileType("LoadDir", "Load Directory",
                  "D:\\Data", true, ""));
      File newDir = new File(directoryText.getText());
      rlm_.loadRegions(f, newDir);
   }

   private void gotoLeftActionPerformed(ActionEvent evt) {
      //Go to min X and center Y coordinate
      Region r;
      PositionList bBox;
      r = rlm_.getRegion(acquireList.getSelectedIndex());
      bBox = r.boundingBox();
      final MultiStagePosition center = r.center();
      final MultiStagePosition minPos = bBox.getPosition(0);
      try {
         gui_.core().setXYPosition(minPos.getX(), center.getY());
      } catch (Exception ex) {
         handleError(ex);
      }
   }

   private void gotoRightActionPerformed(ActionEvent evt) {
      //Go to max X and center Y coordinate
      Region r;
      PositionList bBox;
      r = rlm_.getRegion(acquireList.getSelectedIndex());
      bBox = r.boundingBox();
      final MultiStagePosition center = r.center();
      final MultiStagePosition maxPos = bBox.getPosition(1);
      try {
         gui_.core().setXYPosition(maxPos.getX(), center.getY());
      } catch (Exception ex) {
         handleError(ex);
      }
   }

   private void gotoBottomActionPerformed(java.awt.event.ActionEvent evt) {
      //Go to center X and min Y coordinate
      int index;
      Region r;
      PositionList bBox;
      index = acquireList.getSelectedIndex();
      r = rlm_.getRegion(index);
      bBox = r.boundingBox();
      final MultiStagePosition center = r.center();
      final MultiStagePosition minPos = bBox.getPosition(0);
      try {
         gui_.core().setXYPosition(center.getX(), minPos.getY());
      } catch (Exception ex) {
         handleError(ex);
      }
   }

   private void gotoCenterActionPerformed(
         java.awt.event.ActionEvent evt) {
      //Go to center X and Y coordinate
      Region r;
      MultiStagePosition center;
      r = rlm_.getRegion(acquireList.getSelectedIndex());
      center = r.center();
      try {
         gui_.core().setXYPosition(center.getX(), center.getY());
      } catch (Exception ex) {
         handleError(ex);
      }
   }

   private void gotoTopActionPerformed(java.awt.event.ActionEvent evt) {
      //Go to center X and max Y coordinate
      Region r;
      PositionList bBox;
      r = rlm_.getRegion(acquireList.getSelectedIndex());
      bBox = r.boundingBox();
      final MultiStagePosition center = r.center();
      final MultiStagePosition maxPos = bBox.getPosition(1);
      try {
         gui_.core().setXYPosition(center.getX(), maxPos.getY());
      } catch (Exception ex) {
         handleError(ex);
      }
   }

   private void startAcquisitionActionPerformed(
         java.awt.event.ActionEvent evt) {
      //Loop over saved regions, updating Acquisition position list and filename and acquiring
      //start separate thread for acquisition
      Path savePath;
      savePath = Paths.get(directoryText.getText()).resolve("AMRpositionLists");
      if (savePath.toFile().exists()) {
         try {
            Files.walk(savePath)
                  .sorted(Comparator.reverseOrder())
                  .map(Path::toFile)
                  .forEach(File::delete);
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
         }
      }
      try {
         Thread.sleep(50);
         savePath.toFile().mkdir();
         Thread.sleep(50);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
      rlm_.saveRegions(savePath);
      AcqThread a = new AcqThread();
      a.start();
   }

   private void deleteAllButtonActionPerformed(java.awt.event.ActionEvent evt) {
      rlm_.clearRegions();
   }

   private void deleteRegionActionPerformed(java.awt.event.ActionEvent evt) {
      rlm_.deleteRegion(acquireList.getSelectedIndex());
   }

   private void addPositionListActionPerformed(java.awt.event.ActionEvent evt) {
      currentRegion_.directory = directoryText.getText();
      currentRegion_.filename = filenameText.getText();
      //make sure region name is unique
      currentRegion_ = makeUniqueRegionName(currentRegion_);
      filenameText.setText(currentRegion_.filename); //in case filename has changed
      rlm_.addRegion(currentRegion_);
      currentRegion_ =
            new Region(new PositionList(), directoryText.getText(), filenameText.getText()); //clear
      regionText.setText("Current Region: 0 images");
      logMessage("Starting new Region");
   }

   private void addPointToRegionActionPerformed(java.awt.event.ActionEvent evt) {
      // Record coordinates of core XY stage and all single axis (Z) stages
      // Build grid in XY and fit a plane to each Z axis
      // could consider adding checkboxes for which axes to track

      MultiStagePosition msp = new MultiStagePosition();
      msp.setDefaultXYStage(mmc_.getXYStageDevice());
      msp.setDefaultZStage(mmc_.getFocusDevice());
      String message = "";

      // read 1-axis stages
      try {
         StrVector stages = mmc_.getLoadedDevicesOfType(DeviceType.StageDevice);
         for (int i = 0; i < stages.size(); i++) {
            StagePosition sp = new StagePosition();
            sp.stageName = stages.get(i);
            sp.numAxes = 1;
            sp.x = mmc_.getPosition(stages.get(i));
            msp.add(sp);
            message = message + sp.stageName + ": " + Double.toString(sp.x) + " ";
         }

         StagePosition sp = new StagePosition();
         sp.stageName = mmc_.getXYStageDevice();
         sp.numAxes = 2;
         sp.x = mmc_.getXPosition(mmc_.getXYStageDevice());
         sp.y = mmc_.getYPosition(mmc_.getXYStageDevice());
         msp.add(sp);
         message = "Added point X: " + Double.toString(sp.x) + " Y: "
               + Double.toString(sp.y) + " " + message;

         currentRegion_.positions.addPosition(msp);
         //update text
         int nX = currentRegion_.getNumXTiles(getXFieldSize());
         int nY = currentRegion_.getNumYTiles(getYFieldSize());
         String fieldSize = "Current Region: " + String.valueOf(nX * nY)
               + " images (" + String.valueOf(nX) + " x " + String.valueOf(nY) + ")";
         regionText.setText(fieldSize);
         //log position added and new grid coordinates
         logMessage(message);
         logMessage("Grid now " + Integer.toString(nX) + " x " + Integer.toString(nY));
      } catch (Exception e) {
         handleError(e);
      }
   }

   private void directoryButtonActionPerformed(java.awt.event.ActionEvent evt) {
      File f = FileDialogs.openDir(this, "Directory to save to",
            new FileDialogs.FileType("SaveDir", "Save Directory",
                  "D:\\Data", true, ""));
      directoryText.setText(f.getAbsolutePath());
   }

   private void directoryTextActionPerformed(ActionEvent evt) {
      // TODO add your handling code here:
   }

   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JList acquireList;
   private javax.swing.JButton addPositionList;
   private javax.swing.JButton deleteRegion;
   private javax.swing.JButton directoryButton;
   private javax.swing.JTextField directoryText;
   private javax.swing.JTextField filenameText;
   private javax.swing.JButton gotoBottom;
   private javax.swing.JButton gotoCenter;
   private javax.swing.JButton gotoLeft;
   private javax.swing.JButton gotoRight;
   private javax.swing.JButton gotoTop;
   private javax.swing.JButton startAcquisition;
   private javax.swing.JButton addPointToRegion;
   private javax.swing.JScrollPane axisPane;
   private javax.swing.JPanel configPanel;
   private javax.swing.JButton deleteAllButton;
   private javax.swing.JLabel jLabel1;
   private javax.swing.JLabel jLabel2;
   private javax.swing.JLabel jLabel3;
   private javax.swing.JLabel jLabel4;
   private javax.swing.JLabel jLabel5;
   private javax.swing.JLabel jLabel6;
   private javax.swing.JPanel jPanel1;
   private javax.swing.JScrollPane jScrollPane1;
   private javax.swing.JTabbedPane jTabbedPane2;
   private javax.swing.JButton loadRegionsButton;
   private javax.swing.JPanel mainPanel;
   private javax.swing.JLabel mjLabel7;
   private javax.swing.JTextField overlapText;
   private javax.swing.JLabel regionText;
   private javax.swing.JLabel statusText;
   private javax.swing.ButtonGroup zAxisButtonGroup;
   private javax.swing.JComboBox zTypeDropdown;
   // End of variables declaration//GEN-END:variables
}
