package gui;


import acq.CustomAcqEngine;
import acq.FixedAreaAcquisitionSettings;
import acq.MultipleAcquisitionManager;
import tables.PropertyControlTableModel;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.text.DecimalFormat;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import mmcorej.CMMCore;
import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.PropertyValueCellEditor;
import org.micromanager.utils.PropertyValueCellRenderer;
import org.micromanager.utils.ReportingUtils;
import surfacesandregions.RegionManager;
import surfacesandregions.SurfaceInterpolator;
import surfacesandregions.SurfaceManager;
import surfacesandregions.SurfaceRegionComboBoxModel;
import surfacesandregions.XYFootprint;
import tables.ExactlyOneRowSelectionModel;
import tables.MultipleAcquisitionTableModel;
import tables.SimpleChannelTableModel;


/**
 *
 * @author Henry
 */
public class GUI extends javax.swing.JFrame {
   
   private static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");
   private static final int SLIDER_TICKS = 100000;
   
   private ScriptInterface mmAPI_;
   private CMMCore core_;
   private CustomAcqEngine eng_;
   private Preferences prefs_;
   private String zName_, xyName_;
   private double zMin_, zMax_;
   private RegionManager regionManager_ = new RegionManager();
   private SurfaceManager surfaceManager_ = new SurfaceManager();
   private MultipleAcquisitionManager multiAcqManager_ = new MultipleAcquisitionManager(this);
   private SettingsDialog settings_;
   private boolean storeAcqSettings_ = true;
   private int multiAcqSelectedIndex_ = 0;

   public GUI(Preferences prefs, ScriptInterface mmapi, String version) {
      prefs_ = prefs;   
      settings_ = new SettingsDialog(prefs_, this);
      mmAPI_ = mmapi;
      core_ = mmapi.getMMCore();
      this.setTitle("5D Navigator " + version);
      
      //Demo mode 
      try {
         String s = ((MMStudio) mmAPI_).getSysConfigFile();
         if (s.endsWith("NavDemo.cfg")) {
            SettingsDialog.setDemoMode(true);
         }
      } catch (Exception e) {}
      
      
      initComponents();
      moreInitialization();
      refresh();
      this.setVisible(true);
      eng_ = new CustomAcqEngine(mmAPI_.getMMCore());
      updatePropertiesTable();
      addTextFieldListeners();
      storeCurrentAcqSettings();
   }
   
   public XYFootprint getFootprintObject(int index) {
      //regions first then surfaces
      if (index < regionManager_.getNumberOfRegions()) {
         return regionManager_.getRegion(index);
      } else {
         return surfaceManager_.getSurface(index - regionManager_.getNumberOfRegions());
      }
   }
   
   public static SurfaceRegionComboBoxModel createSurfaceAndRegionComboBoxModel (boolean surfaces, boolean regions) {
      SurfaceRegionComboBoxModel model = new SurfaceRegionComboBoxModel(surfaces ? SurfaceManager.getInstance() : null,
              regions ? RegionManager.getInstance() : null);
      if (surfaces) {
         SurfaceManager.getInstance().addToModelList(model);
      }
      if (regions) {
         RegionManager.getInstance().addToModelList(model);
      }
      return model;
   }
   
   public void updatePropertiesTable() {
      //needs to be off EDT to update width properly
      new Thread(new Runnable() {
         @Override
         public void run() {
            ((PropertyControlTableModel) (propertyControlTable_.getModel())).updateStoredProps();
            ((AbstractTableModel) propertyControlTable_.getModel()).fireTableDataChanged();

            //autofit columns
            propertyControlTable_.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            TableColumn col1 = propertyControlTable_.getColumnModel().getColumn(0);
            int preferredWidth = col1.getMinWidth();
            for (int row = 0; row < propertyControlTable_.getRowCount(); row++) {
               TableCellRenderer cellRenderer = propertyControlTable_.getCellRenderer(row, 0);
               Component c = propertyControlTable_.prepareRenderer(cellRenderer, row, 0);
               int width = c.getPreferredSize().width + propertyControlTable_.getIntercellSpacing().width;
               preferredWidth = Math.max(preferredWidth, width);
            }
            col1.setPreferredWidth(preferredWidth);
            TableColumn col2 = propertyControlTable_.getColumnModel().getColumn(1);
            propertyControlTable_.getHeight();
            col2.setPreferredWidth(propertyControlTable_.getParent().getParent().getWidth() - preferredWidth
                    - (customPropsScrollPane_.getVerticalScrollBar().isVisible() ? customPropsScrollPane_.getVerticalScrollBar().getWidth() : 0));
         }
      }).start();
   }

   private void moreInitialization() {
      //exactly one acquisition selected at all times
      multipleAcqTable_.setSelectionModel(new ExactlyOneRowSelectionModel());
      multipleAcqTable_.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) {
               return;
               //action occures second time this method is called, after the table gains focus
            }
            multiAcqSelectedIndex_ = multipleAcqTable_.getSelectedRow();
            //don't autostore outdated settings while controls are being populated
            storeAcqSettings_ = false;
            populateAcqControls(multiAcqManager_.getAcquisition(multiAcqSelectedIndex_));
            storeAcqSettings_ = true;
         }
      });
      
      zName_ = core_.getFocusDevice();
      zSlider_.setMaximum(SLIDER_TICKS);
      try {
         zMax_ = (int) core_.getPropertyUpperLimit(zName_, "Position");
         zMin_ = (int) core_.getPropertyLowerLimit(zName_, "Position");
         if (SettingsDialog.getDemoMode()) {
            zMin_ = 0;
            zMax_ = 400;
         }
      } catch (Exception ex) {
         ReportingUtils.showError("couldn't get focus limits from core");
      }
      xyName_ = core_.getXYStageDevice();
      double zPosition = 0;
      try {
         zPosition = core_.getPosition(zName_);
         setZSliderPosition(zPosition);
      } catch (Exception e) {
         ReportingUtils.showError("Couldn't get focus position from core");
         return;
      }
      zSliderAdjusted(null);    
      
      populateAcqControls(multiAcqManager_.getAcquisition(0));     
      enableAcquisitionComponentsAsNeeded();  
   }
  
   private void enableAcquisitionComponentsAsNeeded() {
      //Enable or disable time point stuff
      boolean enableTime = timePointsCheckBox_.isSelected();
      for (Component c : timePointsPanel_.getComponents()) {
         c.setEnabled(enableTime);
      }
      //disable all Z stuff then renable as apporpriate
      zStepLabel.setEnabled(false);
      zStepSpinner_.setEnabled(false);
      for (Component c : simpleZPanel_.getComponents()) {
         c.setEnabled(false);
      }
      for (Component c : fixedDistanceZPanel_.getComponents()) {
         c.setEnabled(false);
      }
      for (Component c : volumeBetweenZPanel_.getComponents()) {
         c.setEnabled(false);
      }
      for (Component c : panel2D_.getComponents()) {
         c.setEnabled(false);
      }
      if (checkBox2D_.isSelected()) {
         for (Component c : panel2D_.getComponents()) {
            c.setEnabled(true);
         }
      } else if (checkBox3D_.isSelected()) {
         zStepLabel.setEnabled(true);
         zStepSpinner_.setEnabled(true);
         simpleZStackRadioButton_.setEnabled(true);
         fixedDistanceFromSurfaceRadioButton_.setEnabled(true);
         volumeBetweenSurfacesRadioButton_.setEnabled(true);

         boolean simpleZ = simpleZStackRadioButton_.isSelected();
         for (Component c : simpleZPanel_.getComponents()) {
            if (!(c instanceof JRadioButton)) {
               c.setEnabled(simpleZ);
            }
         }
         boolean fixedDist = fixedDistanceFromSurfaceRadioButton_.isSelected();
         for (Component c : fixedDistanceZPanel_.getComponents()) {
            if (!(c instanceof JRadioButton)) {
               c.setEnabled(fixedDist);
            }
         }
         boolean volumeBetween = volumeBetweenSurfacesRadioButton_.isSelected();
         for (Component c : volumeBetweenZPanel_.getComponents()) {
            if (!(c instanceof JRadioButton)) {
               c.setEnabled(volumeBetween);
            }
         }
      }
   }
      
   private void storeCurrentAcqSettings() {
      if(!storeAcqSettings_) {
         return;
      }
      FixedAreaAcquisitionSettings settings = multiAcqManager_.getAcquisition(multiAcqSelectedIndex_);
      //saving
      settings.dir_ = savingDirTextField_.getText();
      settings.name_ = savingNameTextField_.getText();
      //time
      settings.timeEnabled_ = timePointsCheckBox_.isSelected();
      settings.numTimePoints_ = (Integer) numTimePointsSpinner_.getValue();
      settings.timePointInterval_ = (Double) timeIntervalSpinner_.getValue();
      settings.timeIntervalUnit_ = timeIntevalUnitCombo_.getSelectedIndex();
      //space
      if (checkBox2D_.isSelected()) {
         settings.spaceMode_ = FixedAreaAcquisitionSettings.REGION_2D;
         settings.footprint_ = getFootprintObject(footprint2DComboBox_.getSelectedIndex());
      } else if (checkBox3D_.isSelected()) {
         settings.zStep_ = (Double) zStepSpinner_.getValue();
         if (simpleZStackRadioButton_.isSelected()) {
            settings.spaceMode_ = FixedAreaAcquisitionSettings.SIMPLE_Z_STACK;      
            settings.footprint_ = getFootprintObject(simpleZStackFootprintCombo_.getSelectedIndex());
            settings.zStart_ = (Double) zStartSpinner_.getValue();
            settings.zEnd_ = (Double) zEndSpinner_.getValue();
         } else if (volumeBetweenSurfacesRadioButton_.isSelected()) {            
            settings.spaceMode_ = FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK;
            settings.topSurface_ = (SurfaceInterpolator) surfaceManager_.getSurface(topSurfaceCombo_.getSelectedIndex());
            settings.bottomSurface_ = (SurfaceInterpolator) surfaceManager_.getSurface(bottomSurfaceCombo_.getSelectedIndex());
         } else if (fixedDistanceFromSurfaceRadioButton_.isSelected()) {            
            settings.spaceMode_ = FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK;
            settings.distanceBelowSurface_ = ((Number)distanceBelowSurfaceSpinner_.getValue()).doubleValue();
            settings.distanceAboveSurface_ = ((Number)distanceAboveSurfaceSpinner_.getValue()).doubleValue();
            settings.fixedSurface_ = (SurfaceInterpolator) surfaceManager_.getSurface(fixedDistanceSurfaceComboBox_.getSelectedIndex());         
         }
      } else {
         settings.spaceMode_ = FixedAreaAcquisitionSettings.NO_SPACE;
      }
      //channels
      
      settings.storePreferedValues();
      multipleAcqTable_.repaint();
      
   }
   
   private void populateAcqControls(FixedAreaAcquisitionSettings settings) {
      savingDirTextField_.setText(settings.dir_);
      savingNameTextField_.setText(settings.name_);
      //time
      timePointsCheckBox_.setSelected(settings.timeEnabled_);
      numTimePointsSpinner_.setValue(settings.numTimePoints_);
      timeIntervalSpinner_.setValue(settings.timePointInterval_);
      timeIntevalUnitCombo_.setSelectedIndex(settings.timeIntervalUnit_);
      //space           
      checkBox2D_.setSelected(settings.spaceMode_ == FixedAreaAcquisitionSettings.REGION_2D);
      checkBox3D_.setSelected(settings.spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK ||
              settings.spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK ||
              settings.spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK );
      simpleZStackRadioButton_.setSelected(settings.spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK);
      volumeBetweenSurfacesRadioButton_.setSelected(settings.spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK);
      fixedDistanceFromSurfaceRadioButton_.setSelected(settings.spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK);
      zStepSpinner_.setValue(settings.zStep_);
      zStartSpinner_.setValue(settings.zStart_);
      zEndSpinner_.setValue(settings.zEnd_);
      distanceBelowSurfaceSpinner_.setValue(settings.distanceBelowSurface_);
      distanceAboveSurfaceSpinner_.setValue(settings.distanceAboveSurface_);
      //channels
      
      enableAcquisitionComponentsAsNeeded();
   } 
   
   private void refresh() {
      //Refresh z stage, xy stage, etc and transmit them to eng?
   }

   private void addTextFieldListeners() {
      DocumentListener storeSettingsListener =
              new DocumentListener() {

                 @Override
                 public void insertUpdate(DocumentEvent e) {
                    storeCurrentAcqSettings();
                 }

                 @Override
                 public void removeUpdate(DocumentEvent e) {
                    storeCurrentAcqSettings();
                 }

                 @Override
                 public void changedUpdate(DocumentEvent e) {
                    storeCurrentAcqSettings();
                 }
              };
      savingDirTextField_.getDocument().addDocumentListener(storeSettingsListener);      
      savingNameTextField_.getDocument().addDocumentListener(storeSettingsListener);
   }

      
   //store values when user types text, becuase
   private void addTextEditListener(JSpinner spinner) {
      JSpinner.NumberEditor editor = (JSpinner.NumberEditor) spinner.getEditor();
      editor.getTextField().addFocusListener(new FocusAdapter() {
         @Override
         public void focusLost(FocusEvent e) {
            System.out.println("Text field focus lost, selectedRow: " + multipleAcqTable_.getSelectedRow());
            storeCurrentAcqSettings();
         }
      });
   }

   /**
    * This method is called from within the constructor to initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is always
    * regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        zStackModeButtonGroup_ = new javax.swing.ButtonGroup();
        jPanel2 = new javax.swing.JPanel();
        zLabel_ = new javax.swing.JLabel();
        zTextField_ = new javax.swing.JTextField();
        zSlider_ = new javax.swing.JSlider();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        customPropsScrollPane_ = new javax.swing.JScrollPane();
        propertyControlTable_ = new javax.swing.JTable();
        multipleAcquisitionsPanel = new javax.swing.JPanel();
        multipleAcqScrollPane_ = new javax.swing.JScrollPane();
        multipleAcqTable_ = new javax.swing.JTable();
        jCheckBox1 = new javax.swing.JCheckBox();
        addAcqButton_ = new javax.swing.JButton();
        removeAcqButton_ = new javax.swing.JButton();
        moveAcqDownButton_ = new javax.swing.JButton();
        moveAcqUpButton_ = new javax.swing.JButton();
        gridsPanel_ = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        gridTable_ = new javax.swing.JTable();
        deleteSelectedRegionButton_ = new javax.swing.JButton();
        deleteAllRegionsButton_ = new javax.swing.JButton();
        surfacesPanel_ = new javax.swing.JPanel();
        deleteSelectedSurfaceButton_ = new javax.swing.JButton();
        deleteAllSurfacesButton_ = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        surfacesTable_ = new javax.swing.JTable();
        jLabel1 = new javax.swing.JLabel();
        acqTabbedPane_ = new javax.swing.JTabbedPane();
        savingTab_ = new javax.swing.JPanel();
        savingDirLabel_ = new javax.swing.JLabel();
        browseButton_ = new javax.swing.JButton();
        savingDirTextField_ = new javax.swing.JTextField();
        savingNameLabel_ = new javax.swing.JLabel();
        savingNameTextField_ = new javax.swing.JTextField();
        timePointsTab_ = new javax.swing.JPanel();
        timePointsPanel_ = new javax.swing.JPanel();
        timeIntevalUnitCombo_ = new javax.swing.JComboBox();
        timeIntervalLabel_ = new javax.swing.JLabel();
        numTimePointsLabel_ = new javax.swing.JLabel();
        numTimePointsSpinner_ = new javax.swing.JSpinner();
        timeIntervalSpinner_ = new javax.swing.JSpinner();
        timePointsCheckBox_ = new javax.swing.JCheckBox();
        spaceTab_ = new javax.swing.JPanel();
        simpleZPanel_ = new javax.swing.JPanel();
        simpleZStackRadioButton_ = new javax.swing.JRadioButton();
        zStartLabel = new javax.swing.JLabel();
        zEndLabel = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        simpleZStackFootprintCombo_ = new javax.swing.JComboBox();
        zStartSpinner_ = new javax.swing.JSpinner();
        zEndSpinner_ = new javax.swing.JSpinner();
        volumeBetweenZPanel_ = new javax.swing.JPanel();
        volumeBetweenSurfacesRadioButton_ = new javax.swing.JRadioButton();
        topSurfaceLabel_ = new javax.swing.JLabel();
        bottomSurfaceLabel_ = new javax.swing.JLabel();
        topSurfaceCombo_ = new javax.swing.JComboBox();
        bottomSurfaceCombo_ = new javax.swing.JComboBox();
        jLabel5 = new javax.swing.JLabel();
        footprint3DCombo_ = new javax.swing.JComboBox();
        fixedDistanceZPanel_ = new javax.swing.JPanel();
        fixedDistanceFromSurfaceRadioButton_ = new javax.swing.JRadioButton();
        fixedSurfaceLabel_ = new javax.swing.JLabel();
        fixedDistanceSurfaceComboBox_ = new javax.swing.JComboBox();
        distanceBelowSurfaceLabel_ = new javax.swing.JLabel();
        distanceBelowSurfaceSpinner_ = new javax.swing.JSpinner();
        distanceAboveSurfaceLabel_ = new javax.swing.JLabel();
        distanceAboveSurfaceSpinner_ = new javax.swing.JSpinner();
        zStepLabel = new javax.swing.JLabel();
        panel2D_ = new javax.swing.JPanel();
        footprin2DLabel_ = new javax.swing.JLabel();
        footprint2DComboBox_ = new javax.swing.JComboBox();
        checkBox3D_ = new javax.swing.JCheckBox();
        checkBox2D_ = new javax.swing.JCheckBox();
        zStepSpinner_ = new javax.swing.JSpinner();
        ChannelsTab_ = new javax.swing.JPanel();
        channelsPanel_ = new javax.swing.JPanel();
        channelsCheckBox_ = new javax.swing.JCheckBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        newChannelButton_ = new javax.swing.JButton();
        removeChannelButton_ = new javax.swing.JButton();
        jComboBox2 = new javax.swing.JComboBox();
        jLabel3 = new javax.swing.JLabel();
        autofocusTab_l = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        autofocusFiducialCombo_ = new javax.swing.JComboBox();
        jPanel7 = new javax.swing.JPanel();
        runAcqButton_ = new javax.swing.JButton();
        newExploreWindowButton_ = new javax.swing.JButton();
        configPropsButton_ = new javax.swing.JButton();
        SettingsButton = new javax.swing.JButton();

        zLabel_.setText("Z: ");

        zTextField_.setText("jTextField1");
        zTextField_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zTextField_ActionPerformed(evt);
            }
        });

        zSlider_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                zSliderAdjusted(evt);
            }
        });

        customPropsScrollPane_.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        PropertyControlTableModel model = new PropertyControlTableModel(prefs_);
        propertyControlTable_.setAutoCreateColumnsFromModel(false);
        propertyControlTable_.setModel(model);
        propertyControlTable_.addColumn(new TableColumn(0, 200, new DefaultTableCellRenderer(), null));
        propertyControlTable_.addColumn(new TableColumn(1, 200, new PropertyValueCellRenderer(false), new PropertyValueCellEditor(false)));
        propertyControlTable_.setTableHeader(null);
        propertyControlTable_.setCellSelectionEnabled(false);
        propertyControlTable_.setModel(model);
        customPropsScrollPane_.setViewportView(propertyControlTable_);

        jTabbedPane1.addTab("Device control", customPropsScrollPane_);

        multipleAcqTable_.setModel(new MultipleAcquisitionTableModel(multiAcqManager_));
        multipleAcqTable_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        multipleAcqTable_.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                multipleAcqTable_FocusGained(evt);
            }
        });
        multipleAcqScrollPane_.setViewportView(multipleAcqTable_);

        jCheckBox1.setText("Enable");
        jCheckBox1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBox1ActionPerformed(evt);
            }
        });

        addAcqButton_.setText("Add");
        addAcqButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addAcqButton_ActionPerformed(evt);
            }
        });

        removeAcqButton_.setText("Remove selected");
        removeAcqButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeAcqButton_ActionPerformed(evt);
            }
        });

        moveAcqDownButton_.setText("↓");
        moveAcqDownButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveAcqDownButton_ActionPerformed(evt);
            }
        });

        moveAcqUpButton_.setText("↑");
        moveAcqUpButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveAcqUpButton_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout multipleAcquisitionsPanelLayout = new javax.swing.GroupLayout(multipleAcquisitionsPanel);
        multipleAcquisitionsPanel.setLayout(multipleAcquisitionsPanelLayout);
        multipleAcquisitionsPanelLayout.setHorizontalGroup(
            multipleAcquisitionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(multipleAcqScrollPane_, javax.swing.GroupLayout.DEFAULT_SIZE, 660, Short.MAX_VALUE)
            .addGroup(multipleAcquisitionsPanelLayout.createSequentialGroup()
                .addComponent(jCheckBox1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(addAcqButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(removeAcqButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(moveAcqUpButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(moveAcqDownButton_)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        multipleAcquisitionsPanelLayout.setVerticalGroup(
            multipleAcquisitionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, multipleAcquisitionsPanelLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(multipleAcqScrollPane_, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(multipleAcquisitionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jCheckBox1)
                    .addComponent(addAcqButton_)
                    .addComponent(removeAcqButton_)
                    .addComponent(moveAcqDownButton_)
                    .addComponent(moveAcqUpButton_))
                .addGap(48, 48, 48))
        );

        jTabbedPane1.addTab("Setup multiple acquisitions", multipleAcquisitionsPanel);

        gridTable_.setModel(regionManager_.createGridTableModel());
        gridTable_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane2.setViewportView(gridTable_);

        deleteSelectedRegionButton_.setText("Delete selected");
        deleteSelectedRegionButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteSelectedRegionButton_ActionPerformed(evt);
            }
        });

        deleteAllRegionsButton_.setText("Delete all");
        deleteAllRegionsButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteAllRegionsButton_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout gridsPanel_Layout = new javax.swing.GroupLayout(gridsPanel_);
        gridsPanel_.setLayout(gridsPanel_Layout);
        gridsPanel_Layout.setHorizontalGroup(
            gridsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 660, Short.MAX_VALUE)
            .addGroup(gridsPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(deleteSelectedRegionButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(deleteAllRegionsButton_)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        gridsPanel_Layout.setVerticalGroup(
            gridsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(gridsPanel_Layout.createSequentialGroup()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 104, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(gridsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(deleteSelectedRegionButton_)
                    .addComponent(deleteAllRegionsButton_))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Grids", gridsPanel_);

        deleteSelectedSurfaceButton_.setText("Delete selected");
        deleteSelectedSurfaceButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteSelectedSurfaceButton_ActionPerformed(evt);
            }
        });

        deleteAllSurfacesButton_.setText("Delete all");
        deleteAllSurfacesButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteAllSurfacesButton_ActionPerformed(evt);
            }
        });

        surfacesTable_.setModel(surfaceManager_.createSurfaceTableModel());
        surfacesTable_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane3.setViewportView(surfacesTable_);

        javax.swing.GroupLayout surfacesPanel_Layout = new javax.swing.GroupLayout(surfacesPanel_);
        surfacesPanel_.setLayout(surfacesPanel_Layout);
        surfacesPanel_Layout.setHorizontalGroup(
            surfacesPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(surfacesPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(deleteSelectedSurfaceButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(deleteAllSurfacesButton_)
                .addGap(0, 0, Short.MAX_VALUE))
            .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 660, Short.MAX_VALUE)
        );
        surfacesPanel_Layout.setVerticalGroup(
            surfacesPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(surfacesPanel_Layout.createSequentialGroup()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 104, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(surfacesPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(deleteAllSurfacesButton_)
                    .addComponent(deleteSelectedSurfaceButton_))
                .addContainerGap())
        );

        jTabbedPane1.addTab("Surfaces", surfacesPanel_);

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel1.setText("Acquisition Settings");

        savingDirLabel_.setText("Saving directory: ");

        browseButton_.setText("Browse");
        browseButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButton_ActionPerformed(evt);
            }
        });

        savingDirTextField_.setText("jTextField1");

        savingNameLabel_.setText("Saving name: ");

        savingNameTextField_.setText("jTextField2");

        javax.swing.GroupLayout savingTab_Layout = new javax.swing.GroupLayout(savingTab_);
        savingTab_.setLayout(savingTab_Layout);
        savingTab_Layout.setHorizontalGroup(
            savingTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(savingTab_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(savingTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(savingNameTextField_)
                    .addGroup(savingTab_Layout.createSequentialGroup()
                        .addGroup(savingTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(savingTab_Layout.createSequentialGroup()
                                .addComponent(savingDirLabel_)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(browseButton_))
                            .addComponent(savingNameLabel_))
                        .addGap(0, 620, Short.MAX_VALUE))
                    .addComponent(savingDirTextField_))
                .addContainerGap())
        );
        savingTab_Layout.setVerticalGroup(
            savingTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(savingTab_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(savingTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(savingDirLabel_)
                    .addComponent(browseButton_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(savingDirTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(savingNameLabel_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(savingNameTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(167, Short.MAX_VALUE))
        );

        acqTabbedPane_.addTab("Saving", savingTab_);

        timePointsPanel_.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        timeIntevalUnitCombo_.setModel(new DefaultComboBoxModel(new String[]{"ms", "s", "min"}));
        timeIntevalUnitCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeIntevalUnitCombo_ActionPerformed(evt);
            }
        });

        timeIntervalLabel_.setText("Interval");

        numTimePointsLabel_.setText("Number");

        numTimePointsSpinner_.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1), Integer.valueOf(1), null, Integer.valueOf(1)));
        numTimePointsSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                numTimePointsSpinner_StateChanged(evt);
            }
        });

        timeIntervalSpinner_.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(0.0d), Double.valueOf(0.0d), null, Double.valueOf(1.0d)));
        timeIntervalSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                timeIntervalSpinner_StateChanged(evt);
            }
        });

        javax.swing.GroupLayout timePointsPanel_Layout = new javax.swing.GroupLayout(timePointsPanel_);
        timePointsPanel_.setLayout(timePointsPanel_Layout);
        timePointsPanel_Layout.setHorizontalGroup(
            timePointsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(timePointsPanel_Layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addGroup(timePointsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(timeIntervalLabel_, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(numTimePointsLabel_, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(timePointsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(numTimePointsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(timePointsPanel_Layout.createSequentialGroup()
                        .addComponent(timeIntervalSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(timeIntevalUnitCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(33, Short.MAX_VALUE))
        );
        timePointsPanel_Layout.setVerticalGroup(
            timePointsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(timePointsPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(timePointsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(timePointsPanel_Layout.createSequentialGroup()
                        .addGroup(timePointsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(numTimePointsLabel_)
                            .addComponent(numTimePointsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(timePointsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(timeIntervalLabel_)
                            .addComponent(timeIntervalSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 13, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, timePointsPanel_Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(timeIntevalUnitCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap())))
        );

        addTextEditListener(numTimePointsSpinner_);
        addTextEditListener(timeIntervalSpinner_);

        timePointsCheckBox_.setText("Time points");
        timePointsCheckBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timePointsCheckBox_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout timePointsTab_Layout = new javax.swing.GroupLayout(timePointsTab_);
        timePointsTab_.setLayout(timePointsTab_Layout);
        timePointsTab_Layout.setHorizontalGroup(
            timePointsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(timePointsTab_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(timePointsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(timePointsCheckBox_)
                    .addComponent(timePointsPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(578, Short.MAX_VALUE))
        );
        timePointsTab_Layout.setVerticalGroup(
            timePointsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(timePointsTab_Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(timePointsCheckBox_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timePointsPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(167, Short.MAX_VALUE))
        );

        for (Component c : timePointsPanel_.getComponents()) {
            c.setEnabled(false);
        }

        acqTabbedPane_.addTab("Time", timePointsTab_);

        simpleZPanel_.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        zStackModeButtonGroup_.add(simpleZStackRadioButton_);
        simpleZStackRadioButton_.setText("Simple Z stack");
        simpleZStackRadioButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                simpleZStackRadioButton_ActionPerformed(evt);
            }
        });

        zStartLabel.setText("Z-start (µm)");

        zEndLabel.setText("Z-end (µm)");

        jLabel2.setText("Surface/Grid XY footprint:");

        simpleZStackFootprintCombo_.setModel(createSurfaceAndRegionComboBoxModel(true,true));
        simpleZStackFootprintCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                simpleZStackFootprintCombo_ActionPerformed(evt);
            }
        });

        zStartSpinner_.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(0.0d), null, null, Double.valueOf(1.0d)));
        zStartSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                zStartSpinner_StateChanged(evt);
            }
        });

        zEndSpinner_.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(0.0d), null, null, Double.valueOf(1.0d)));
        zEndSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                zEndSpinner_StateChanged(evt);
            }
        });

        javax.swing.GroupLayout simpleZPanel_Layout = new javax.swing.GroupLayout(simpleZPanel_);
        simpleZPanel_.setLayout(simpleZPanel_Layout);
        simpleZPanel_Layout.setHorizontalGroup(
            simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(simpleZPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(simpleZPanel_Layout.createSequentialGroup()
                        .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(simpleZStackRadioButton_)
                            .addComponent(jLabel2)
                            .addComponent(simpleZStackFootprintCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 139, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 8, Short.MAX_VALUE))
                    .addGroup(simpleZPanel_Layout.createSequentialGroup()
                        .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(zStartLabel)
                            .addComponent(zEndLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(zEndSpinner_)
                            .addComponent(zStartSpinner_))))
                .addContainerGap())
        );
        simpleZPanel_Layout.setVerticalGroup(
            simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(simpleZPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(simpleZStackRadioButton_)
                .addGap(5, 5, 5)
                .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(zStartLabel)
                    .addComponent(zStartSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(zEndLabel)
                    .addComponent(zEndSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(simpleZStackFootprintCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        addTextEditListener(zStartSpinner_);
        addTextEditListener(zEndSpinner_);

        volumeBetweenZPanel_.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        zStackModeButtonGroup_.add(volumeBetweenSurfacesRadioButton_);
        volumeBetweenSurfacesRadioButton_.setText("Volume between two surfaces");
        volumeBetweenSurfacesRadioButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                volumeBetweenSurfacesRadioButton_ActionPerformed(evt);
            }
        });

        topSurfaceLabel_.setText("Top surface:");

        bottomSurfaceLabel_.setText("Bottom surface: ");

        topSurfaceCombo_.setModel(createSurfaceAndRegionComboBoxModel(true,false));
        topSurfaceCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                topSurfaceCombo_ActionPerformed(evt);
            }
        });

        bottomSurfaceCombo_.setModel(createSurfaceAndRegionComboBoxModel(true,false));
        bottomSurfaceCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bottomSurfaceCombo_ActionPerformed(evt);
            }
        });

        jLabel5.setText("XY foorprint from:");

        footprint3DCombo_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Top surface", "Bottom surface" }));
        footprint3DCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                footprint3DCombo_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout volumeBetweenZPanel_Layout = new javax.swing.GroupLayout(volumeBetweenZPanel_);
        volumeBetweenZPanel_.setLayout(volumeBetweenZPanel_Layout);
        volumeBetweenZPanel_Layout.setHorizontalGroup(
            volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(volumeBetweenZPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(volumeBetweenZPanel_Layout.createSequentialGroup()
                        .addComponent(volumeBetweenSurfacesRadioButton_)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(volumeBetweenZPanel_Layout.createSequentialGroup()
                        .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(topSurfaceLabel_)
                            .addComponent(bottomSurfaceLabel_))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(topSurfaceCombo_, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(bottomSurfaceCombo_, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(volumeBetweenZPanel_Layout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(footprint3DCombo_, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        volumeBetweenZPanel_Layout.setVerticalGroup(
            volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(volumeBetweenZPanel_Layout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addComponent(volumeBetweenSurfacesRadioButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(topSurfaceLabel_)
                    .addComponent(topSurfaceCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bottomSurfaceLabel_)
                    .addComponent(bottomSurfaceCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(footprint3DCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        fixedDistanceZPanel_.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        zStackModeButtonGroup_.add(fixedDistanceFromSurfaceRadioButton_);
        fixedDistanceFromSurfaceRadioButton_.setLabel("Within distance from surface");
        fixedDistanceFromSurfaceRadioButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixedDistanceFromSurfaceRadioButton_ActionPerformed(evt);
            }
        });

        fixedSurfaceLabel_.setText("Surface: ");

        fixedDistanceSurfaceComboBox_.setModel(createSurfaceAndRegionComboBoxModel(true,false));
        fixedDistanceSurfaceComboBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixedDistanceSurfaceComboBox_ActionPerformed(evt);
            }
        });

        distanceBelowSurfaceLabel_.setText("Distance below (µm):");

        distanceBelowSurfaceSpinner_.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(0.0d), Double.valueOf(0.0d), null, Double.valueOf(0.001d)));
        distanceBelowSurfaceSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                distanceBelowSurfaceSpinner_StateChanged(evt);
            }
        });

        distanceAboveSurfaceLabel_.setText("Distance above (µm):");

        distanceAboveSurfaceSpinner_.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(0.0d), Double.valueOf(0.0d), null, Double.valueOf(0.001d)));
        distanceAboveSurfaceSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                distanceAboveSurfaceSpinner_StateChanged(evt);
            }
        });

        javax.swing.GroupLayout fixedDistanceZPanel_Layout = new javax.swing.GroupLayout(fixedDistanceZPanel_);
        fixedDistanceZPanel_.setLayout(fixedDistanceZPanel_Layout);
        fixedDistanceZPanel_Layout.setHorizontalGroup(
            fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                        .addComponent(fixedSurfaceLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(fixedDistanceSurfaceComboBox_, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                            .addComponent(distanceAboveSurfaceLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, 111, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(distanceAboveSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                            .addComponent(distanceBelowSurfaceLabel_)
                            .addGap(14, 14, 14)
                            .addComponent(distanceBelowSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(fixedDistanceFromSurfaceRadioButton_))
                .addContainerGap(19, Short.MAX_VALUE))
        );
        fixedDistanceZPanel_Layout.setVerticalGroup(
            fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(fixedDistanceFromSurfaceRadioButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(fixedSurfaceLabel_)
                    .addComponent(fixedDistanceSurfaceComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(distanceBelowSurfaceLabel_)
                    .addComponent(distanceBelowSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(distanceAboveSurfaceLabel_)
                    .addComponent(distanceAboveSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        addTextEditListener(distanceBelowSurfaceSpinner_);
        addTextEditListener(distanceAboveSurfaceSpinner_);

        zStepLabel.setText("Z-step (µm):");

        panel2D_.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        footprin2DLabel_.setText("Surface/Grid footprint:");

        footprint2DComboBox_.setModel(createSurfaceAndRegionComboBoxModel(true,true));
        footprint2DComboBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                footprint2DComboBox_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout panel2D_Layout = new javax.swing.GroupLayout(panel2D_);
        panel2D_.setLayout(panel2D_Layout);
        panel2D_Layout.setHorizontalGroup(
            panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel2D_Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(footprin2DLabel_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(footprint2DComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 139, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        panel2D_Layout.setVerticalGroup(
            panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel2D_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(footprin2DLabel_)
                    .addComponent(footprint2DComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(27, Short.MAX_VALUE))
        );

        checkBox3D_.setText("3D");
        checkBox3D_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBox3D_ActionPerformed(evt);
            }
        });

        checkBox2D_.setText("2D");
        checkBox2D_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBox2D_ActionPerformed(evt);
            }
        });

        zStepSpinner_.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(1.0d), null, null, Double.valueOf(1.0d)));
        zStepSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                zStepSpinner_StateChanged(evt);
            }
        });

        javax.swing.GroupLayout spaceTab_Layout = new javax.swing.GroupLayout(spaceTab_);
        spaceTab_.setLayout(spaceTab_Layout);
        spaceTab_Layout.setHorizontalGroup(
            spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(spaceTab_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(spaceTab_Layout.createSequentialGroup()
                        .addComponent(checkBox3D_)
                        .addGap(40, 40, 40)
                        .addComponent(zStepLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(zStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(spaceTab_Layout.createSequentialGroup()
                        .addComponent(simpleZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(10, 10, 10)
                        .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(spaceTab_Layout.createSequentialGroup()
                                .addComponent(volumeBetweenZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(8, 8, 8)
                                .addComponent(fixedDistanceZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(spaceTab_Layout.createSequentialGroup()
                                .addComponent(checkBox2D_)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(panel2D_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addContainerGap(164, Short.MAX_VALUE))
        );
        spaceTab_Layout.setVerticalGroup(
            spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(spaceTab_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(checkBox3D_)
                        .addComponent(zStepLabel, javax.swing.GroupLayout.Alignment.TRAILING))
                    .addComponent(zStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(spaceTab_Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(simpleZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, 143, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap())
                    .addGroup(spaceTab_Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(fixedDistanceZPanel_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(volumeBetweenZPanel_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(spaceTab_Layout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(panel2D_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGap(39, 39, 39))
                            .addGroup(spaceTab_Layout.createSequentialGroup()
                                .addGap(17, 17, 17)
                                .addComponent(checkBox2D_)
                                .addContainerGap())))))
        );

        for (Component c : simpleZPanel_.getComponents()) {
            c.setEnabled(false);
        }
        for (Component c : volumeBetweenZPanel_.getComponents()) {
            c.setEnabled(false);
        }
        addTextEditListener(zStepSpinner_);

        acqTabbedPane_.addTab("Space", spaceTab_);

        channelsPanel_.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        channelsCheckBox_.setText("Channels");
        channelsCheckBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                channelsCheckBox_ActionPerformed(evt);
            }
        });

        jTable1.setModel(new SimpleChannelTableModel());
        jScrollPane1.setViewportView(jTable1);

        newChannelButton_.setText("New");
        newChannelButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newChannelButton_ActionPerformed(evt);
            }
        });

        removeChannelButton_.setText("Remove");
        removeChannelButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeChannelButton_ActionPerformed(evt);
            }
        });

        jComboBox2.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        jComboBox2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox2ActionPerformed(evt);
            }
        });

        jLabel3.setText("Channel group:");

        javax.swing.GroupLayout channelsPanel_Layout = new javax.swing.GroupLayout(channelsPanel_);
        channelsPanel_.setLayout(channelsPanel_Layout);
        channelsPanel_Layout.setHorizontalGroup(
            channelsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(channelsPanel_Layout.createSequentialGroup()
                .addGroup(channelsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(channelsPanel_Layout.createSequentialGroup()
                        .addComponent(channelsCheckBox_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(newChannelButton_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(removeChannelButton_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 299, Short.MAX_VALUE)
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, 107, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 788, Short.MAX_VALUE))
                .addContainerGap())
        );
        channelsPanel_Layout.setVerticalGroup(
            channelsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(channelsPanel_Layout.createSequentialGroup()
                .addGroup(channelsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(channelsCheckBox_)
                    .addComponent(newChannelButton_)
                    .addComponent(removeChannelButton_)
                    .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 98, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout ChannelsTab_Layout = new javax.swing.GroupLayout(ChannelsTab_);
        ChannelsTab_.setLayout(ChannelsTab_Layout);
        ChannelsTab_Layout.setHorizontalGroup(
            ChannelsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(channelsPanel_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        ChannelsTab_Layout.setVerticalGroup(
            ChannelsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ChannelsTab_Layout.createSequentialGroup()
                .addComponent(channelsPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 146, Short.MAX_VALUE))
        );

        acqTabbedPane_.addTab("Channels", ChannelsTab_);

        jLabel7.setText("Fiducial channel index:");

        autofocusFiducialCombo_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        autofocusFiducialCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autofocusFiducialCombo_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout autofocusTab_lLayout = new javax.swing.GroupLayout(autofocusTab_l);
        autofocusTab_l.setLayout(autofocusTab_lLayout);
        autofocusTab_lLayout.setHorizontalGroup(
            autofocusTab_lLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(autofocusTab_lLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(autofocusFiducialCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(624, Short.MAX_VALUE))
        );
        autofocusTab_lLayout.setVerticalGroup(
            autofocusTab_lLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(autofocusTab_lLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(autofocusTab_lLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(autofocusFiducialCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(247, Short.MAX_VALUE))
        );

        acqTabbedPane_.addTab("Autofocus", autofocusTab_l);

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 802, Short.MAX_VALUE)
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 278, Short.MAX_VALUE)
        );

        acqTabbedPane_.addTab("Focus varying properties", jPanel7);

        runAcqButton_.setText("Run acquisition");
        runAcqButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runAcqButton_ActionPerformed(evt);
            }
        });

        newExploreWindowButton_.setText("New explore acquisition");
        newExploreWindowButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newExploreWindowButton_ActionPerformed(evt);
            }
        });

        configPropsButton_.setText("Configure device control properties");
        configPropsButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                configPropsButton_ActionPerformed(evt);
            }
        });

        SettingsButton.setText("Settings");
        SettingsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SettingsButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(newExploreWindowButton_))
                            .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(jPanel2Layout.createSequentialGroup()
                                    .addComponent(zLabel_)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(zTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, 68, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(zSlider_, javax.swing.GroupLayout.PREFERRED_SIZE, 562, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addComponent(jTabbedPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(30, 30, 30)
                        .addComponent(runAcqButton_)))
                .addGap(0, 0, Short.MAX_VALUE))
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(configPropsButton_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(SettingsButton)
                        .addGap(38, 38, 38))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(acqTabbedPane_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(18, Short.MAX_VALUE))))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(zLabel_)
                        .addComponent(zTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(zSlider_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 164, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(newExploreWindowButton_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(acqTabbedPane_, javax.swing.GroupLayout.PREFERRED_SIZE, 306, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(runAcqButton_)
                .addGap(41, 41, 41)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(configPropsButton_)
                    .addComponent(SettingsButton))
                .addGap(0, 162, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

   private void zTextField_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zTextField_ActionPerformed
      double val = Double.parseDouble(zTextField_.getText());
      val = Math.max(Math.min(val,zMax_),zMin_);
      setZSliderPosition(val);
      zSliderAdjusted(null); 
   }//GEN-LAST:event_zTextField_ActionPerformed
 
   private void setZSliderPosition(double pos) {
      int ticks = (int) (((pos - zMin_) / (zMax_ - zMin_)) * SLIDER_TICKS);
      zSlider_.setValue(ticks);
   }
   
   private void zSliderAdjusted(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zSliderAdjusted
     int ticks = zSlider_.getValue();
      double zVal = zMin_ + (zMax_ - zMin_) * (ticks / (double) SLIDER_TICKS);
      zTextField_.setText(TWO_DECIMAL_FORMAT.format(zVal));
      try {
         core_.setPosition(zName_,zVal);
      } catch (Exception ex) {
         ReportingUtils.showError("Couldn't set z position");
      }
   }//GEN-LAST:event_zSliderAdjusted

   private void newChannelButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newChannelButton_ActionPerformed
            storeCurrentAcqSettings();

   }//GEN-LAST:event_newChannelButton_ActionPerformed

   private void volumeBetweenSurfacesRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_volumeBetweenSurfacesRadioButton_ActionPerformed
      enableAcquisitionComponentsAsNeeded();
      storeCurrentAcqSettings();
   }//GEN-LAST:event_volumeBetweenSurfacesRadioButton_ActionPerformed

   private void timePointsCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timePointsCheckBox_ActionPerformed
      for (Component c : timePointsPanel_.getComponents()) {
         c.setEnabled(timePointsCheckBox_.isSelected());
      }
      storeCurrentAcqSettings();
   }//GEN-LAST:event_timePointsCheckBox_ActionPerformed

   private void SettingsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SettingsButtonActionPerformed

         settings_.setVisible(true);      
   }//GEN-LAST:event_SettingsButtonActionPerformed

   private void configPropsButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_configPropsButton_ActionPerformed
      new PickPropertiesGUI(prefs_, this);
   }//GEN-LAST:event_configPropsButton_ActionPerformed

   private void deleteSelectedRegionButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteSelectedRegionButton_ActionPerformed
      if (gridTable_.getSelectedRow() != -1) {
         regionManager_.delete(gridTable_.getSelectedRow());
      }
   }//GEN-LAST:event_deleteSelectedRegionButton_ActionPerformed

   private void deleteAllRegionsButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteAllRegionsButton_ActionPerformed
      regionManager_.deleteAll();
   }//GEN-LAST:event_deleteAllRegionsButton_ActionPerformed

   private void deleteAllSurfacesButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteAllSurfacesButton_ActionPerformed
      surfaceManager_.deleteAll();
   }//GEN-LAST:event_deleteAllSurfacesButton_ActionPerformed

   private void deleteSelectedSurfaceButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteSelectedSurfaceButton_ActionPerformed
     if (surfacesTable_.getSelectedRow() != -1) {
         surfaceManager_.delete(surfacesTable_.getSelectedRow());
      }
   }//GEN-LAST:event_deleteSelectedSurfaceButton_ActionPerformed

   private void runAcqButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runAcqButton_ActionPerformed
      //run acquisition
      eng_.runFixedAreaAcquisition(multiAcqManager_.getAcquisition(multipleAcqTable_.getSelectedRow()));
   }//GEN-LAST:event_runAcqButton_ActionPerformed

   private void browseButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButton_ActionPerformed
      String root = "";
      if (savingDirTextField_.getText() != null && !savingDirTextField_.getText().equals("")) {
         root = savingDirTextField_.getText();
      }
      JFileChooser chooser = new JFileChooser(root);
      chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      int option = chooser.showSaveDialog(this);
      if (option != JFileChooser.APPROVE_OPTION) {
         return;
      }
      File f = chooser.getSelectedFile();
      if (!f.isDirectory()) {
         f = f.getParentFile();
      }
      savingDirTextField_.setText(f.getAbsolutePath());
   }//GEN-LAST:event_browseButton_ActionPerformed

   private void fixedDistanceSurfaceComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixedDistanceSurfaceComboBox_ActionPerformed
           storeCurrentAcqSettings();
   }//GEN-LAST:event_fixedDistanceSurfaceComboBox_ActionPerformed

   private void fixedDistanceFromSurfaceRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixedDistanceFromSurfaceRadioButton_ActionPerformed
     enableAcquisitionComponentsAsNeeded();      
     storeCurrentAcqSettings();
   }//GEN-LAST:event_fixedDistanceFromSurfaceRadioButton_ActionPerformed

   private void simpleZStackRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_simpleZStackRadioButton_ActionPerformed
     enableAcquisitionComponentsAsNeeded();      
     storeCurrentAcqSettings();
   }//GEN-LAST:event_simpleZStackRadioButton_ActionPerformed

   private void checkBox3D_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBox3D_ActionPerformed
      if (checkBox3D_.isSelected()) {
         checkBox2D_.setSelected(false);
      }
      enableAcquisitionComponentsAsNeeded();
      storeCurrentAcqSettings();
   }//GEN-LAST:event_checkBox3D_ActionPerformed

   private void checkBox2D_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBox2D_ActionPerformed
      if (checkBox2D_.isSelected()) {
         checkBox3D_.setSelected(false);
      }
      enableAcquisitionComponentsAsNeeded();
            storeCurrentAcqSettings();
   }//GEN-LAST:event_checkBox2D_ActionPerformed

   private void jCheckBox1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBox1ActionPerformed
      // TODO add your handling code here:
   }//GEN-LAST:event_jCheckBox1ActionPerformed

   private void numTimePointsSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_numTimePointsSpinner_StateChanged
      storeCurrentAcqSettings();
   }//GEN-LAST:event_numTimePointsSpinner_StateChanged

   private void timeIntevalUnitCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeIntevalUnitCombo_ActionPerformed
      storeCurrentAcqSettings();
   }//GEN-LAST:event_timeIntevalUnitCombo_ActionPerformed

   private void simpleZStackFootprintCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_simpleZStackFootprintCombo_ActionPerformed
            storeCurrentAcqSettings();
   }//GEN-LAST:event_simpleZStackFootprintCombo_ActionPerformed

   private void topSurfaceCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_topSurfaceCombo_ActionPerformed
            storeCurrentAcqSettings();
   }//GEN-LAST:event_topSurfaceCombo_ActionPerformed

   private void bottomSurfaceCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bottomSurfaceCombo_ActionPerformed
            storeCurrentAcqSettings();

   }//GEN-LAST:event_bottomSurfaceCombo_ActionPerformed

   private void footprint3DCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_footprint3DCombo_ActionPerformed
           storeCurrentAcqSettings();

   }//GEN-LAST:event_footprint3DCombo_ActionPerformed

   private void distanceBelowSurfaceSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_distanceBelowSurfaceSpinner_StateChanged
            storeCurrentAcqSettings();
   }//GEN-LAST:event_distanceBelowSurfaceSpinner_StateChanged

   private void distanceAboveSurfaceSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_distanceAboveSurfaceSpinner_StateChanged
            storeCurrentAcqSettings();
   }//GEN-LAST:event_distanceAboveSurfaceSpinner_StateChanged

   private void footprint2DComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_footprint2DComboBox_ActionPerformed
           storeCurrentAcqSettings();
   }//GEN-LAST:event_footprint2DComboBox_ActionPerformed

   private void channelsCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_channelsCheckBox_ActionPerformed
            storeCurrentAcqSettings();
   }//GEN-LAST:event_channelsCheckBox_ActionPerformed

   private void removeChannelButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeChannelButton_ActionPerformed
           storeCurrentAcqSettings();

   }//GEN-LAST:event_removeChannelButton_ActionPerformed

   private void jComboBox2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox2ActionPerformed
            storeCurrentAcqSettings();
   }//GEN-LAST:event_jComboBox2ActionPerformed

   private void autofocusFiducialCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autofocusFiducialCombo_ActionPerformed
           storeCurrentAcqSettings();
   }//GEN-LAST:event_autofocusFiducialCombo_ActionPerformed

   private void moveAcqUpButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveAcqUpButton_ActionPerformed
      if (multiAcqManager_.moveUp(multipleAcqTable_.getSelectedRow())) {
         multipleAcqTable_.getSelectionModel().setSelectionInterval(multiAcqSelectedIndex_ - 1, multiAcqSelectedIndex_ - 1);
      }
      multipleAcqTable_.repaint();
   }//GEN-LAST:event_moveAcqUpButton_ActionPerformed

   private void removeAcqButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeAcqButton_ActionPerformed
      multiAcqManager_.remove(multipleAcqTable_.getSelectedRow());
      ((MultipleAcquisitionTableModel) multipleAcqTable_.getModel()).fireTableStructureChanged();
      multipleAcqTable_.repaint();
   }//GEN-LAST:event_removeAcqButton_ActionPerformed

   private void addAcqButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addAcqButton_ActionPerformed
      multiAcqManager_.addNew();
      ((MultipleAcquisitionTableModel) multipleAcqTable_.getModel()).fireTableStructureChanged();
      multipleAcqTable_.repaint();
   }//GEN-LAST:event_addAcqButton_ActionPerformed

   private void moveAcqDownButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveAcqDownButton_ActionPerformed
      if (multiAcqManager_.moveDown(multipleAcqTable_.getSelectedRow())) {
         multipleAcqTable_.getSelectionModel().setSelectionInterval(multiAcqSelectedIndex_ + 1, multiAcqSelectedIndex_ + 1);
      }
      multipleAcqTable_.repaint();
   }//GEN-LAST:event_moveAcqDownButton_ActionPerformed

   private void timeIntervalSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_timeIntervalSpinner_StateChanged
           storeCurrentAcqSettings();
   }//GEN-LAST:event_timeIntervalSpinner_StateChanged

   private void zStepSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zStepSpinner_StateChanged
           storeCurrentAcqSettings();
   }//GEN-LAST:event_zStepSpinner_StateChanged

   private void zStartSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zStartSpinner_StateChanged
           storeCurrentAcqSettings();
   }//GEN-LAST:event_zStartSpinner_StateChanged

   private void zEndSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zEndSpinner_StateChanged
           storeCurrentAcqSettings();
   }//GEN-LAST:event_zEndSpinner_StateChanged

   private void newExploreWindowButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newExploreWindowButton_ActionPerformed
      new ExploreInitDialog(eng_, savingDirTextField_.getText(), savingNameTextField_.getText() );
   }//GEN-LAST:event_newExploreWindowButton_ActionPerformed

   private void multipleAcqTable_FocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_multipleAcqTable_FocusGained
      System.out.println("Table focus gained, selected row: " + multipleAcqTable_.getSelectedRow());
   }//GEN-LAST:event_multipleAcqTable_FocusGained

 

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ChannelsTab_;
    private javax.swing.JButton SettingsButton;
    private javax.swing.JTabbedPane acqTabbedPane_;
    private javax.swing.JButton addAcqButton_;
    private javax.swing.JComboBox autofocusFiducialCombo_;
    private javax.swing.JPanel autofocusTab_l;
    private javax.swing.JComboBox bottomSurfaceCombo_;
    private javax.swing.JLabel bottomSurfaceLabel_;
    private javax.swing.JButton browseButton_;
    private javax.swing.JCheckBox channelsCheckBox_;
    private javax.swing.JPanel channelsPanel_;
    private javax.swing.JCheckBox checkBox2D_;
    private javax.swing.JCheckBox checkBox3D_;
    private javax.swing.JButton configPropsButton_;
    private javax.swing.JScrollPane customPropsScrollPane_;
    private javax.swing.JButton deleteAllRegionsButton_;
    private javax.swing.JButton deleteAllSurfacesButton_;
    private javax.swing.JButton deleteSelectedRegionButton_;
    private javax.swing.JButton deleteSelectedSurfaceButton_;
    private javax.swing.JLabel distanceAboveSurfaceLabel_;
    private javax.swing.JSpinner distanceAboveSurfaceSpinner_;
    private javax.swing.JLabel distanceBelowSurfaceLabel_;
    private javax.swing.JSpinner distanceBelowSurfaceSpinner_;
    private javax.swing.JRadioButton fixedDistanceFromSurfaceRadioButton_;
    private javax.swing.JComboBox fixedDistanceSurfaceComboBox_;
    private javax.swing.JPanel fixedDistanceZPanel_;
    private javax.swing.JLabel fixedSurfaceLabel_;
    private javax.swing.JLabel footprin2DLabel_;
    private javax.swing.JComboBox footprint2DComboBox_;
    private javax.swing.JComboBox footprint3DCombo_;
    private javax.swing.JTable gridTable_;
    private javax.swing.JPanel gridsPanel_;
    private javax.swing.JCheckBox jCheckBox1;
    private javax.swing.JComboBox jComboBox2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JButton moveAcqDownButton_;
    private javax.swing.JButton moveAcqUpButton_;
    private javax.swing.JScrollPane multipleAcqScrollPane_;
    private javax.swing.JTable multipleAcqTable_;
    private javax.swing.JPanel multipleAcquisitionsPanel;
    private javax.swing.JButton newChannelButton_;
    private javax.swing.JButton newExploreWindowButton_;
    private javax.swing.JLabel numTimePointsLabel_;
    private javax.swing.JSpinner numTimePointsSpinner_;
    private javax.swing.JPanel panel2D_;
    private javax.swing.JTable propertyControlTable_;
    private javax.swing.JButton removeAcqButton_;
    private javax.swing.JButton removeChannelButton_;
    private javax.swing.JButton runAcqButton_;
    private javax.swing.JLabel savingDirLabel_;
    private javax.swing.JTextField savingDirTextField_;
    private javax.swing.JLabel savingNameLabel_;
    private javax.swing.JTextField savingNameTextField_;
    private javax.swing.JPanel savingTab_;
    private javax.swing.JPanel simpleZPanel_;
    private javax.swing.JComboBox simpleZStackFootprintCombo_;
    private javax.swing.JRadioButton simpleZStackRadioButton_;
    private javax.swing.JPanel spaceTab_;
    private javax.swing.JPanel surfacesPanel_;
    private javax.swing.JTable surfacesTable_;
    private javax.swing.JLabel timeIntervalLabel_;
    private javax.swing.JSpinner timeIntervalSpinner_;
    private javax.swing.JComboBox timeIntevalUnitCombo_;
    private javax.swing.JCheckBox timePointsCheckBox_;
    private javax.swing.JPanel timePointsPanel_;
    private javax.swing.JPanel timePointsTab_;
    private javax.swing.JComboBox topSurfaceCombo_;
    private javax.swing.JLabel topSurfaceLabel_;
    private javax.swing.JRadioButton volumeBetweenSurfacesRadioButton_;
    private javax.swing.JPanel volumeBetweenZPanel_;
    private javax.swing.JLabel zEndLabel;
    private javax.swing.JSpinner zEndSpinner_;
    private javax.swing.JLabel zLabel_;
    private javax.swing.JSlider zSlider_;
    private javax.swing.ButtonGroup zStackModeButtonGroup_;
    private javax.swing.JLabel zStartLabel;
    private javax.swing.JSpinner zStartSpinner_;
    private javax.swing.JLabel zStepLabel;
    private javax.swing.JSpinner zStepSpinner_;
    private javax.swing.JTextField zTextField_;
    // End of variables declaration//GEN-END:variables


}
