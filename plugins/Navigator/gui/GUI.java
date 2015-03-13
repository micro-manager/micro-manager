package gui;


import acq.CustomAcqEngine;
import acq.ExploreAcqSettings;
import acq.FixedAreaAcquisitionSettings;
import acq.MultipleAcquisitionManager;
import acq.MultipleAcquisitionTableModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.text.DecimalFormat;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import mmcloneclasses.utils.PropertyValueCellEditor;
import mmcloneclasses.utils.PropertyValueCellRenderer;
import mmcorej.CMMCore;
import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import propsandcovariants.DeviceControlTableModel;
import propsandcovariants.CovariantPairValuesTableModel;
import propsandcovariants.CovariantPairing;
import propsandcovariants.CovariantPairingsManager;
import propsandcovariants.CovariantPairingsTableModel;
import propsandcovariants.CovariantValueCellEditor;
import propsandcovariants.CovariantValueCellRenderer;
import surfacesandregions.RegionManager;
import surfacesandregions.SurfaceInterpolator;
import surfacesandregions.SurfaceManager;
import surfacesandregions.SurfaceRegionComboBoxModel;
import surfacesandregions.XYFootprint;
import utility.ExactlyOneRowSelectionModel;
import utility.SimpleChannelTableModel;


/**
 *
 * @author Henry
 */
public class GUI extends javax.swing.JFrame {
   
   private static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");
   private static final Color DARK_GREEN = new Color(0,128,0);
   
   private ScriptInterface mmAPI_;
   private CMMCore core_;
   private CustomAcqEngine eng_;
   private Preferences prefs_;
   private RegionManager regionManager_ = new RegionManager();
   private SurfaceManager surfaceManager_ = new SurfaceManager();
   private CovariantPairingsManager covariantPairManager_;
   private MultipleAcquisitionManager multiAcqManager_;
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
      } catch (Exception e) {
      }
      eng_ = new CustomAcqEngine(mmAPI_.getMMCore());
      multiAcqManager_ = new MultipleAcquisitionManager(this, eng_);
      covariantPairManager_ = new CovariantPairingsManager(this, multiAcqManager_);
      initComponents();
      moreInitialization();
      this.setVisible(true);
      updatePropertiesTable();
      addTextFieldListeners();
      storeCurrentAcqSettings();
   }
   
   public FixedAreaAcquisitionSettings getActiveAcquisitionSettings() {
      return multiAcqManager_.getAcquisition(multiAcqSelectedIndex_);
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
            ((DeviceControlTableModel) (propertyControlTable_.getModel())).updateStoredProps();
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
      covariantPairingsTable_.setSelectionModel(new ExactlyOneRowSelectionModel());
      covariantPairingsTable_.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) {
               return;
               //action occurs second time this method is called, after the table gains focus
            }
            //populate property pairings list
            CovariantPairing activePair;
            int index = covariantPairingsTable_.getSelectedRow(); 
            if (index == -1) {
               activePair = (CovariantPairing) covariantPairingsTable_.getModel().getValueAt(0, 1) ;
            } else {
               activePair = (CovariantPairing) covariantPairingsTable_.getModel().getValueAt(index, 1);
            }
            ((CovariantPairValuesTableModel) covariantPairValuesTable_.getModel()).setPair(activePair);
            //have to do it manually for this one owing to soemthing custom I've done with columns
            ((CovariantPairValuesTableModel) covariantPairValuesTable_.getModel()).updateColumnNames(covariantPairValuesTable_.getColumnModel());
            covariantPairValuesTable_.getTableHeader().repaint();
         }   
      });
      //initial update to prevent column headers from showiing up as "A" and "B"
      ((CovariantPairValuesTableModel) covariantPairValuesTable_.getModel()).updateColumnNames(covariantPairValuesTable_.getColumnModel());
      covariantPairValuesTable_.getTableHeader().repaint();

      //exactly one acquisition selected at all times
      multipleAcqTable_.setSelectionModel(new ExactlyOneRowSelectionModel());
      multipleAcqTable_.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) {
               return;
               //action occurs second time this method is called, after the table gains focus
            }
            multiAcqSelectedIndex_ = multipleAcqTable_.getSelectedRow();
            //if last acq in list is removed, update the selected index
            if (multiAcqSelectedIndex_ == multipleAcqTable_.getModel().getRowCount()) {
               multipleAcqTable_.getSelectionModel().setSelectionInterval(multiAcqSelectedIndex_-1, multiAcqSelectedIndex_-1);
            }
            populateAcqControls(multiAcqManager_.getAcquisition(multiAcqSelectedIndex_));
         }
      });
      //Table column widths
      multipleAcqTable_.getColumnModel().getColumn(0).setMaxWidth(40); //order column
      covariantPairingsTable_.getColumnModel().getColumn(0).setMaxWidth(40); //Acitve checkbox column
      
      //load explore settings
      exploreSavingDirTextField_.setText(ExploreAcqSettings.getDirFromPrefs());
      exploreSavingNameTextField_.setText(ExploreAcqSettings.getNameFromPrefs());
      exploreZStepSpinner_.setValue(ExploreAcqSettings.getZStepFromPrefs());
      
      populateAcqControls(multiAcqManager_.getAcquisition(0));     
      enableAcquisitionComponentsAsNeeded();  
   }
    
   public void refreshAcquisitionSettings() {
      //so that acquisition names can be changed form multi acquisitiion table
      populateAcqControls(multiAcqManager_.getAcquisition(multiAcqSelectedIndex_));
   }

   private void setTabTitleText() {
      JLabel l1 = new JLabel("Saving");
      l1.setForeground(DARK_GREEN);
      l1.setFont(acqTabbedPane_.getComponent(0).getFont().deriveFont(Font.BOLD));
      acqTabbedPane_.setTabComponentAt(0, l1);
      JLabel l2 = new JLabel("Time");
      l2.setForeground(timePointsCheckBox_.isSelected() ? DARK_GREEN : Color.black);
      l2.setFont(acqTabbedPane_.getComponent(1).getFont().deriveFont(timePointsCheckBox_.isSelected() ? Font.BOLD : Font.PLAIN));
      acqTabbedPane_.setTabComponentAt(1, l2);
      JLabel l3 = new JLabel("Space");
      l3.setForeground(checkBox3D_.isSelected() || checkBox2D_.isSelected() ? DARK_GREEN : Color.black);
      l3.setFont(acqTabbedPane_.getComponent(1).getFont().deriveFont(checkBox3D_.isSelected() || checkBox2D_.isSelected() ? Font.BOLD : Font.PLAIN));
      acqTabbedPane_.setTabComponentAt(2, l3);
      acqTabbedPane_.invalidate();
      acqTabbedPane_.validate();
   }

   private void enableAcquisitionComponentsAsNeeded() {
      //Set Tab titles
      setTabTitleText();
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
            settings.useTopOrBottomFootprint_ = volumeBetweenFootprintCombo_.getSelectedItem().equals("Top surface") ? 
                    FixedAreaAcquisitionSettings.FOOTPRINT_FROM_TOP : FixedAreaAcquisitionSettings.FOOTPRINT_FROM_BOTTOM;
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
      //don't autostore outdated settings while controls are being populated
      storeAcqSettings_ = false;
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
      //select surfaces/regions
      simpleZStackFootprintCombo_.setSelectedItem(settings.footprint_);   
      topSurfaceCombo_.setSelectedItem(settings.topSurface_);
      bottomSurfaceCombo_.setSelectedItem(settings.bottomSurface_);      
      volumeBetweenFootprintCombo_.setSelectedIndex(settings.useTopOrBottomFootprint_);
      fixedDistanceSurfaceComboBox_.setSelectedItem(settings.fixedSurface_);
      footprint2DComboBox_.setSelectedItem(settings.footprint_);
      //channels
      enableAcquisitionComponentsAsNeeded();
      
      repaint();
      storeAcqSettings_ = true;
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
            storeCurrentAcqSettings();
         }
      });
   }
   
   public void enableMultiAcquisitionControls(boolean enable) {
      addAcqButton_.setEnabled(enable);
      removeAcqButton_.setEnabled(enable);
      moveAcqDownButton_.setEnabled(enable);
      moveAcqUpButton_.setEnabled(enable);
      intereaveButton_.setEnabled(enable);
      deinterleaveButton_.setEnabled(enable);
      runMultipleAcquisitionsButton_.setText(enable ? "Run all acquisitions" : "Abort");
      repaint();
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
        SettingsButton = new javax.swing.JButton();
        runAcqButton_ = new javax.swing.JButton();
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
        volumeBetweenFootprintCombo_ = new javax.swing.JComboBox();
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
        jLabel4 = new javax.swing.JLabel();
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
        covariedSettingsTab_ = new javax.swing.JPanel();
        propertyPairValuesScrollpane_ = new javax.swing.JScrollPane();
        covariantPairValuesTable_ = new javax.swing.JTable();
        newParingButton_ = new javax.swing.JButton();
        removePairingButton = new javax.swing.JButton();
        propertyPairingsScrollpane_ = new javax.swing.JScrollPane();
        covariantPairingsTable_ = new javax.swing.JTable();
        savePairingsButton_ = new javax.swing.JButton();
        loadPairingsButton_ = new javax.swing.JButton();
        addCovariedPairingValueButton_ = new javax.swing.JButton();
        deleteCovariedPairingValueButton_ = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        newExploreWindowButton_ = new javax.swing.JButton();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        controlPanelName_ = new javax.swing.JPanel();
        configPropsButton_ = new javax.swing.JButton();
        customPropsScrollPane_ = new javax.swing.JScrollPane();
        propertyControlTable_ = new javax.swing.JTable();
        multipleAcquisitionsPanel = new javax.swing.JPanel();
        multipleAcqScrollPane_ = new javax.swing.JScrollPane();
        multipleAcqTable_ = new javax.swing.JTable();
        jPanel1 = new javax.swing.JPanel();
        addAcqButton_ = new javax.swing.JButton();
        removeAcqButton_ = new javax.swing.JButton();
        moveAcqUpButton_ = new javax.swing.JButton();
        moveAcqDownButton_ = new javax.swing.JButton();
        runMultipleAcquisitionsButton_ = new javax.swing.JButton();
        intereaveButton_ = new javax.swing.JButton();
        deinterleaveButton_ = new javax.swing.JButton();
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
        exploreSampleLabel_ = new javax.swing.JLabel();
        exploreZStepLabel_ = new javax.swing.JLabel();
        exploreZStepSpinner_ = new javax.swing.JSpinner();
        exploreSavingDirLabel_ = new javax.swing.JLabel();
        exploreBrowseButton_ = new javax.swing.JButton();
        exploreSavingDirTextField_ = new javax.swing.JTextField();
        exploreSavingNameLabel_ = new javax.swing.JLabel();
        exploreSavingNameTextField_ = new javax.swing.JTextField();
        channelGroupLabel_ = new javax.swing.JLabel();
        exploreChannelGroupCombo_ = new javax.swing.JComboBox();

        SettingsButton.setText("Settings");
        SettingsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SettingsButtonActionPerformed(evt);
            }
        });

        runAcqButton_.setText("Run acquisition");
        runAcqButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runAcqButton_ActionPerformed(evt);
            }
        });

        savingDirLabel_.setText("Saving directory: ");

        browseButton_.setText("Browse");
        browseButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButton_ActionPerformed(evt);
            }
        });

        savingDirTextField_.setText("jTextField1");
        savingDirTextField_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                savingDirTextField_ActionPerformed(evt);
            }
        });

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
                        .addGap(0, 612, Short.MAX_VALUE))
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
                        .addComponent(timeIntevalUnitCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        timePointsPanel_Layout.setVerticalGroup(
            timePointsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(timePointsPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(timePointsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(numTimePointsLabel_)
                    .addComponent(numTimePointsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(timePointsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(timeIntervalLabel_)
                    .addComponent(timeIntervalSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(timeIntevalUnitCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 12, Short.MAX_VALUE))
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
                .addContainerGap(566, Short.MAX_VALUE))
        );
        timePointsTab_Layout.setVerticalGroup(
            timePointsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(timePointsTab_Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(timePointsCheckBox_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timePointsPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(160, Short.MAX_VALUE))
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

        volumeBetweenFootprintCombo_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Top surface", "Bottom surface" }));
        volumeBetweenFootprintCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                volumeBetweenFootprintCombo_ActionPerformed(evt);
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
                        .addComponent(volumeBetweenFootprintCombo_, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
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
                    .addComponent(volumeBetweenFootprintCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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

        jLabel4.setText("TODO: add tile overlaps");

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
                        .addComponent(zStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(37, 37, 37)
                        .addComponent(jLabel4))
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
                .addContainerGap(156, Short.MAX_VALUE))
        );
        spaceTab_Layout.setVerticalGroup(
            spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(spaceTab_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(checkBox3D_)
                    .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(zStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(zStepLabel)
                        .addComponent(jLabel4)))
                .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
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
                                .addContainerGap())))
                    .addGroup(spaceTab_Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(simpleZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))))
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
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 283, Short.MAX_VALUE)
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, 107, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 780, Short.MAX_VALUE))
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
                .addContainerGap(616, Short.MAX_VALUE))
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

        CovariantPairValuesTableModel cpvtModel = new CovariantPairValuesTableModel();
        covariantPairValuesTable_.setAutoCreateColumnsFromModel(false);
        covariantPairValuesTable_.addColumn(new TableColumn(0, 100, new CovariantValueCellRenderer(), new CovariantValueCellEditor()));
        covariantPairValuesTable_.addColumn(new TableColumn(1, 100, new CovariantValueCellRenderer(), new CovariantValueCellEditor()));
        covariantPairValuesTable_.setCellSelectionEnabled(false);

        covariantPairValuesTable_.setModel(cpvtModel);
        propertyPairValuesScrollpane_.setViewportView(covariantPairValuesTable_);

        newParingButton_.setText("New pairing");
        newParingButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newParingButton_ActionPerformed(evt);
            }
        });

        removePairingButton.setText("Remove pairing");
        removePairingButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removePairingButtonActionPerformed(evt);
            }
        });

        covariantPairingsTable_.setModel(new propsandcovariants.CovariantPairingsTableModel());
        propertyPairingsScrollpane_.setViewportView(covariantPairingsTable_);

        savePairingsButton_.setText("Save all");
        savePairingsButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                savePairingsButton_ActionPerformed(evt);
            }
        });

        loadPairingsButton_.setText("Load");
        loadPairingsButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadPairingsButton_ActionPerformed(evt);
            }
        });

        addCovariedPairingValueButton_.setText("Add row");
        addCovariedPairingValueButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addCovariedPairingValueButton_ActionPerformed(evt);
            }
        });

        deleteCovariedPairingValueButton_.setText("Delete");

        javax.swing.GroupLayout covariedSettingsTab_Layout = new javax.swing.GroupLayout(covariedSettingsTab_);
        covariedSettingsTab_.setLayout(covariedSettingsTab_Layout);
        covariedSettingsTab_Layout.setHorizontalGroup(
            covariedSettingsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, covariedSettingsTab_Layout.createSequentialGroup()
                .addGroup(covariedSettingsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(covariedSettingsTab_Layout.createSequentialGroup()
                        .addComponent(propertyPairingsScrollpane_, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED))
                    .addGroup(covariedSettingsTab_Layout.createSequentialGroup()
                        .addComponent(newParingButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 89, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(removePairingButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(savePairingsButton_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(loadPairingsButton_)
                        .addGap(31, 31, 31)))
                .addGroup(covariedSettingsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(covariedSettingsTab_Layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(addCovariedPairingValueButton_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(deleteCovariedPairingValueButton_)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(propertyPairValuesScrollpane_, javax.swing.GroupLayout.PREFERRED_SIZE, 425, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );
        covariedSettingsTab_Layout.setVerticalGroup(
            covariedSettingsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(covariedSettingsTab_Layout.createSequentialGroup()
                .addGroup(covariedSettingsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(propertyPairingsScrollpane_, javax.swing.GroupLayout.DEFAULT_SIZE, 233, Short.MAX_VALUE)
                    .addComponent(propertyPairValuesScrollpane_, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(covariedSettingsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(newParingButton_)
                    .addComponent(removePairingButton)
                    .addComponent(savePairingsButton_)
                    .addComponent(loadPairingsButton_)
                    .addComponent(addCovariedPairingValueButton_)
                    .addComponent(deleteCovariedPairingValueButton_))
                .addContainerGap())
        );

        acqTabbedPane_.addTab("Covaried settings", covariedSettingsTab_);

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel1.setText("Acquisition Settings");

        newExploreWindowButton_.setText("Explore!");
        newExploreWindowButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newExploreWindowButton_ActionPerformed(evt);
            }
        });

        configPropsButton_.setText("Configure");
        configPropsButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                configPropsButton_ActionPerformed(evt);
            }
        });

        customPropsScrollPane_.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        DeviceControlTableModel model = new DeviceControlTableModel(prefs_);
        propertyControlTable_.setAutoCreateColumnsFromModel(false);
        propertyControlTable_.setModel(model);
        propertyControlTable_.addColumn(new TableColumn(0, 200, new DefaultTableCellRenderer(), null));
        propertyControlTable_.addColumn(new TableColumn(1, 200, new PropertyValueCellRenderer(false), new PropertyValueCellEditor()));
        propertyControlTable_.setTableHeader(null);
        propertyControlTable_.setCellSelectionEnabled(false);
        propertyControlTable_.setModel(model);
        customPropsScrollPane_.setViewportView(propertyControlTable_);

        javax.swing.GroupLayout controlPanelName_Layout = new javax.swing.GroupLayout(controlPanelName_);
        controlPanelName_.setLayout(controlPanelName_Layout);
        controlPanelName_Layout.setHorizontalGroup(
            controlPanelName_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(controlPanelName_Layout.createSequentialGroup()
                .addComponent(customPropsScrollPane_, javax.swing.GroupLayout.PREFERRED_SIZE, 796, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
            .addGroup(controlPanelName_Layout.createSequentialGroup()
                .addGap(346, 346, 346)
                .addComponent(configPropsButton_)
                .addContainerGap())
        );
        controlPanelName_Layout.setVerticalGroup(
            controlPanelName_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(controlPanelName_Layout.createSequentialGroup()
                .addComponent(customPropsScrollPane_, javax.swing.GroupLayout.PREFERRED_SIZE, 195, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(configPropsButton_)
                .addContainerGap(16, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Device status/control", controlPanelName_);

        multipleAcqTable_.setModel(new MultipleAcquisitionTableModel(multiAcqManager_,this));
        multipleAcqTable_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        multipleAcqScrollPane_.setViewportView(multipleAcqTable_);

        addAcqButton_.setText("Add");
        addAcqButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addAcqButton_ActionPerformed(evt);
            }
        });

        removeAcqButton_.setText("Remove");
        removeAcqButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeAcqButton_ActionPerformed(evt);
            }
        });

        moveAcqUpButton_.setText("Move↑");
        moveAcqUpButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveAcqUpButton_ActionPerformed(evt);
            }
        });

        moveAcqDownButton_.setText("Move↓");
        moveAcqDownButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveAcqDownButton_ActionPerformed(evt);
            }
        });

        runMultipleAcquisitionsButton_.setText("Run all");
        runMultipleAcquisitionsButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runMultipleAcquisitionsButton_ActionPerformed(evt);
            }
        });

        intereaveButton_.setText("Interleave");
        intereaveButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                intereaveButton_ActionPerformed(evt);
            }
        });

        deinterleaveButton_.setText("Deinterleave");
        deinterleaveButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deinterleaveButton_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(addAcqButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(removeAcqButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(moveAcqUpButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(moveAcqDownButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(intereaveButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deinterleaveButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(runMultipleAcquisitionsButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(58, 58, 58))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addAcqButton_)
                    .addComponent(removeAcqButton_)
                    .addComponent(moveAcqUpButton_)
                    .addComponent(moveAcqDownButton_)
                    .addComponent(runMultipleAcquisitionsButton_)
                    .addComponent(intereaveButton_)
                    .addComponent(deinterleaveButton_)))
        );

        javax.swing.GroupLayout multipleAcquisitionsPanelLayout = new javax.swing.GroupLayout(multipleAcquisitionsPanel);
        multipleAcquisitionsPanel.setLayout(multipleAcquisitionsPanelLayout);
        multipleAcquisitionsPanelLayout.setHorizontalGroup(
            multipleAcquisitionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(multipleAcqScrollPane_)
            .addGroup(multipleAcquisitionsPanelLayout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        multipleAcquisitionsPanelLayout.setVerticalGroup(
            multipleAcquisitionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, multipleAcquisitionsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(multipleAcqScrollPane_, javax.swing.GroupLayout.DEFAULT_SIZE, 189, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 796, Short.MAX_VALUE)
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
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 211, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(gridsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(deleteSelectedRegionButton_)
                    .addComponent(deleteAllRegionsButton_)))
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
            .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 796, Short.MAX_VALUE)
        );
        surfacesPanel_Layout.setVerticalGroup(
            surfacesPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(surfacesPanel_Layout.createSequentialGroup()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 195, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(surfacesPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(deleteAllSurfacesButton_)
                    .addComponent(deleteSelectedSurfaceButton_))
                .addContainerGap())
        );

        jTabbedPane1.addTab("Surfaces", surfacesPanel_);

        exploreSampleLabel_.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        exploreSampleLabel_.setText("Explore sample");

        exploreZStepLabel_.setText("Z-step (µm):");

        exploreZStepSpinner_.setModel(new javax.swing.SpinnerNumberModel(Double.valueOf(1.0d), null, null, Double.valueOf(1.0d)));
        exploreZStepSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                exploreZStepSpinner_StateChanged(evt);
            }
        });

        exploreSavingDirLabel_.setText("Saving directory: ");

        exploreBrowseButton_.setText("Browse");
        exploreBrowseButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exploreBrowseButton_ActionPerformed(evt);
            }
        });

        exploreSavingDirTextField_.setText("jTextField1");

        exploreSavingNameLabel_.setText("Saving name: ");

        exploreSavingNameTextField_.setText("jTextField2");
        exploreSavingNameTextField_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exploreSavingNameTextField_ActionPerformed(evt);
            }
        });

        channelGroupLabel_.setText("Channel Group: ");

        exploreChannelGroupCombo_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(320, 320, 320)
                .addComponent(runAcqButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(SettingsButton)
                .addGap(25, 25, 25))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel1))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(2, 2, 2)
                        .addComponent(jTabbedPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 801, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(acqTabbedPane_, javax.swing.GroupLayout.PREFERRED_SIZE, 799, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(exploreSampleLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(exploreZStepLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exploreZStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(28, 28, 28)
                        .addComponent(channelGroupLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(exploreChannelGroupCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(exploreSavingDirLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exploreSavingDirTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, 618, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exploreBrowseButton_))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(exploreSavingNameLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exploreSavingNameTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, 699, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(329, 329, 329)
                        .addComponent(newExploreWindowButton_)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exploreSampleLabel_)
                    .addComponent(exploreZStepLabel_)
                    .addComponent(exploreZStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(channelGroupLabel_)
                    .addComponent(exploreChannelGroupCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exploreSavingDirLabel_)
                    .addComponent(exploreSavingDirTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exploreBrowseButton_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exploreSavingNameLabel_)
                    .addComponent(exploreSavingNameTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(newExploreWindowButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(acqTabbedPane_, javax.swing.GroupLayout.PREFERRED_SIZE, 306, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(runAcqButton_)
                        .addContainerGap())
                    .addComponent(SettingsButton, javax.swing.GroupLayout.Alignment.TRAILING)))
        );

        addTextEditListener(zStepSpinner_);

        pack();
    }// </editor-fold>//GEN-END:initComponents
 
   private void SettingsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SettingsButtonActionPerformed

         settings_.setVisible(true);      
   }//GEN-LAST:event_SettingsButtonActionPerformed

   private void configPropsButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_configPropsButton_ActionPerformed
      new PickPropertiesGUI(prefs_, this);
   }//GEN-LAST:event_configPropsButton_ActionPerformed

   private void runAcqButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runAcqButton_ActionPerformed
      //run acquisition
      new Thread(new Runnable() {
         @Override
         public void run() {
            eng_.runFixedAreaAcquisition(multiAcqManager_.getAcquisition(multipleAcqTable_.getSelectedRow()));
         }
      }).start();
   }//GEN-LAST:event_runAcqButton_ActionPerformed

   private void newExploreWindowButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newExploreWindowButton_ActionPerformed
      ExploreAcqSettings settings = new ExploreAcqSettings(
              ((Number) exploreZStepSpinner_.getValue()).doubleValue(), exploreSavingDirTextField_.getText(), exploreSavingNameTextField_.getText());
      eng_.newExploreAcquisition(settings);
   }//GEN-LAST:event_newExploreWindowButton_ActionPerformed

   private void autofocusFiducialCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autofocusFiducialCombo_ActionPerformed
      storeCurrentAcqSettings();
   }//GEN-LAST:event_autofocusFiducialCombo_ActionPerformed

   private void jComboBox2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox2ActionPerformed
      storeCurrentAcqSettings();
   }//GEN-LAST:event_jComboBox2ActionPerformed

   private void removeChannelButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeChannelButton_ActionPerformed
      storeCurrentAcqSettings();
   }//GEN-LAST:event_removeChannelButton_ActionPerformed

//   private void setZSliderPosition(double pos) {
//      int ticks = (int) (((pos - zMin_) / (zMax_ - zMin_)) * SLIDER_TICKS);
//      zSlider_.setValue(ticks);
//   }
   
   private void newChannelButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newChannelButton_ActionPerformed
      storeCurrentAcqSettings();
   }//GEN-LAST:event_newChannelButton_ActionPerformed

   private void channelsCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_channelsCheckBox_ActionPerformed
      storeCurrentAcqSettings();
   }//GEN-LAST:event_channelsCheckBox_ActionPerformed

   private void zStepSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zStepSpinner_StateChanged
      storeCurrentAcqSettings();
   }//GEN-LAST:event_zStepSpinner_StateChanged

   private void checkBox2D_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBox2D_ActionPerformed
      if (checkBox2D_.isSelected()) {
         checkBox3D_.setSelected(false);
      }
      enableAcquisitionComponentsAsNeeded();
      storeCurrentAcqSettings();
      setTabTitleText();
   }//GEN-LAST:event_checkBox2D_ActionPerformed

   private void checkBox3D_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBox3D_ActionPerformed
      if (checkBox3D_.isSelected()) {
         checkBox2D_.setSelected(false);
      }
      if ((!simpleZStackRadioButton_.isSelected()) && (!volumeBetweenSurfacesRadioButton_.isSelected())
              && (!fixedDistanceFromSurfaceRadioButton_.isSelected())) {
         simpleZStackRadioButton_.setSelected(true);
      }
      enableAcquisitionComponentsAsNeeded();
      storeCurrentAcqSettings();
      setTabTitleText();
   }//GEN-LAST:event_checkBox3D_ActionPerformed

   private void footprint2DComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_footprint2DComboBox_ActionPerformed
      storeCurrentAcqSettings();
   }//GEN-LAST:event_footprint2DComboBox_ActionPerformed

   private void distanceAboveSurfaceSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_distanceAboveSurfaceSpinner_StateChanged
      storeCurrentAcqSettings();
   }//GEN-LAST:event_distanceAboveSurfaceSpinner_StateChanged

   private void distanceBelowSurfaceSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_distanceBelowSurfaceSpinner_StateChanged
      storeCurrentAcqSettings();
   }//GEN-LAST:event_distanceBelowSurfaceSpinner_StateChanged

   private void fixedDistanceSurfaceComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixedDistanceSurfaceComboBox_ActionPerformed
      storeCurrentAcqSettings();
   }//GEN-LAST:event_fixedDistanceSurfaceComboBox_ActionPerformed

   private void fixedDistanceFromSurfaceRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixedDistanceFromSurfaceRadioButton_ActionPerformed
      enableAcquisitionComponentsAsNeeded();
      storeCurrentAcqSettings();
   }//GEN-LAST:event_fixedDistanceFromSurfaceRadioButton_ActionPerformed

   private void volumeBetweenFootprintCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_volumeBetweenFootprintCombo_ActionPerformed
      storeCurrentAcqSettings();
   }//GEN-LAST:event_volumeBetweenFootprintCombo_ActionPerformed

   private void bottomSurfaceCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bottomSurfaceCombo_ActionPerformed
      storeCurrentAcqSettings();
   }//GEN-LAST:event_bottomSurfaceCombo_ActionPerformed

   private void topSurfaceCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_topSurfaceCombo_ActionPerformed
      storeCurrentAcqSettings();
   }//GEN-LAST:event_topSurfaceCombo_ActionPerformed

   private void volumeBetweenSurfacesRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_volumeBetweenSurfacesRadioButton_ActionPerformed
      enableAcquisitionComponentsAsNeeded();
      storeCurrentAcqSettings();
   }//GEN-LAST:event_volumeBetweenSurfacesRadioButton_ActionPerformed

   private void zEndSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zEndSpinner_StateChanged
      storeCurrentAcqSettings();
   }//GEN-LAST:event_zEndSpinner_StateChanged

   private void zStartSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zStartSpinner_StateChanged
      storeCurrentAcqSettings();
   }//GEN-LAST:event_zStartSpinner_StateChanged

   private void simpleZStackFootprintCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_simpleZStackFootprintCombo_ActionPerformed
      storeCurrentAcqSettings();
   }//GEN-LAST:event_simpleZStackFootprintCombo_ActionPerformed

   private void simpleZStackRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_simpleZStackRadioButton_ActionPerformed
      enableAcquisitionComponentsAsNeeded();
      storeCurrentAcqSettings();
   }//GEN-LAST:event_simpleZStackRadioButton_ActionPerformed

   private void timePointsCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timePointsCheckBox_ActionPerformed
      for (Component c : timePointsPanel_.getComponents()) {
         c.setEnabled(timePointsCheckBox_.isSelected());
      }
      storeCurrentAcqSettings();
      setTabTitleText();
   }//GEN-LAST:event_timePointsCheckBox_ActionPerformed

   private void timeIntervalSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_timeIntervalSpinner_StateChanged
      storeCurrentAcqSettings();
   }//GEN-LAST:event_timeIntervalSpinner_StateChanged

   private void numTimePointsSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_numTimePointsSpinner_StateChanged
      storeCurrentAcqSettings();
   }//GEN-LAST:event_numTimePointsSpinner_StateChanged

   private void timeIntevalUnitCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeIntevalUnitCombo_ActionPerformed
      storeCurrentAcqSettings();
   }//GEN-LAST:event_timeIntevalUnitCombo_ActionPerformed

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

   private void deleteAllSurfacesButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteAllSurfacesButton_ActionPerformed
      surfaceManager_.deleteAll();
   }//GEN-LAST:event_deleteAllSurfacesButton_ActionPerformed

   private void deleteSelectedSurfaceButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteSelectedSurfaceButton_ActionPerformed
      if (surfacesTable_.getSelectedRow() != -1) {
         surfaceManager_.delete(surfacesTable_.getSelectedRow());
      }
   }//GEN-LAST:event_deleteSelectedSurfaceButton_ActionPerformed

   private void deleteAllRegionsButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteAllRegionsButton_ActionPerformed
      regionManager_.deleteAll();
   }//GEN-LAST:event_deleteAllRegionsButton_ActionPerformed

   private void deleteSelectedRegionButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteSelectedRegionButton_ActionPerformed
      if (gridTable_.getSelectedRow() != -1) {
         regionManager_.delete(gridTable_.getSelectedRow());
      }
   }//GEN-LAST:event_deleteSelectedRegionButton_ActionPerformed

   private void deinterleaveButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deinterleaveButton_ActionPerformed
      multiAcqManager_.removeFromParallelGrouping(multiAcqSelectedIndex_);
      multipleAcqTable_.repaint();
   }//GEN-LAST:event_deinterleaveButton_ActionPerformed

   private void intereaveButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_intereaveButton_ActionPerformed
      multiAcqManager_.addToParallelGrouping(multiAcqSelectedIndex_);
      multipleAcqTable_.repaint();
   }//GEN-LAST:event_intereaveButton_ActionPerformed

   private void runMultipleAcquisitionsButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runMultipleAcquisitionsButton_ActionPerformed
      if (multiAcqManager_.isRunning()) {
         multiAcqManager_.abort();
      } else {
         multiAcqManager_.runAllAcquisitions();
      }
   }//GEN-LAST:event_runMultipleAcquisitionsButton_ActionPerformed

   private void moveAcqDownButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveAcqDownButton_ActionPerformed
      int move = multiAcqManager_.moveDown(multipleAcqTable_.getSelectedRow());
      multipleAcqTable_.getSelectionModel().setSelectionInterval(multiAcqSelectedIndex_ + move, multiAcqSelectedIndex_ + move);
      multipleAcqTable_.repaint();
   }//GEN-LAST:event_moveAcqDownButton_ActionPerformed

   private void moveAcqUpButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveAcqUpButton_ActionPerformed
      int move = multiAcqManager_.moveUp(multipleAcqTable_.getSelectedRow());
      multipleAcqTable_.getSelectionModel().setSelectionInterval(multiAcqSelectedIndex_ + move, multiAcqSelectedIndex_ + move);
      multipleAcqTable_.repaint();
   }//GEN-LAST:event_moveAcqUpButton_ActionPerformed

   private void removeAcqButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeAcqButton_ActionPerformed
      multiAcqManager_.remove(multipleAcqTable_.getSelectedRow());
      ((MultipleAcquisitionTableModel) multipleAcqTable_.getModel()).fireTableDataChanged();
      multipleAcqTable_.repaint();
   }//GEN-LAST:event_removeAcqButton_ActionPerformed

   private void addAcqButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addAcqButton_ActionPerformed
      multiAcqManager_.addNew();
      ((MultipleAcquisitionTableModel) multipleAcqTable_.getModel()).fireTableDataChanged();
      multipleAcqTable_.repaint();
   }//GEN-LAST:event_addAcqButton_ActionPerformed

   private void exploreZStepSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_exploreZStepSpinner_StateChanged
      // TODO add your handling code here:
   }//GEN-LAST:event_exploreZStepSpinner_StateChanged

   private void exploreBrowseButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exploreBrowseButton_ActionPerformed
       String root = "";
      if (exploreSavingDirTextField_.getText() != null && !exploreSavingDirTextField_.getText().equals("")) {
         root = exploreSavingDirTextField_.getText();
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
      exploreSavingDirTextField_.setText(f.getAbsolutePath());
   }//GEN-LAST:event_exploreBrowseButton_ActionPerformed

   private void savingDirTextField_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_savingDirTextField_ActionPerformed
      // TODO add your handling code here:
   }//GEN-LAST:event_savingDirTextField_ActionPerformed

   private void exploreSavingNameTextField_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exploreSavingNameTextField_ActionPerformed
      // TODO add your handling code here:
   }//GEN-LAST:event_exploreSavingNameTextField_ActionPerformed

   private void newParingButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newParingButton_ActionPerformed
      new PropertyPairCreationDialog(GUI.this, true);
   }//GEN-LAST:event_newParingButton_ActionPerformed

   private void addCovariedPairingValueButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addCovariedPairingValueButton_ActionPerformed
      ((CovariantPairValuesTableModel) covariantPairValuesTable_.getModel()).getPairing().addNewValuePairing();
      ((CovariantPairValuesTableModel) covariantPairValuesTable_.getModel()).fireTableDataChanged();
   }//GEN-LAST:event_addCovariedPairingValueButton_ActionPerformed

   private void savePairingsButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_savePairingsButton_ActionPerformed
      covariantPairManager_.saveAllPairings(this);
   }//GEN-LAST:event_savePairingsButton_ActionPerformed

   private void loadPairingsButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadPairingsButton_ActionPerformed
      covariantPairManager_.loadPairingsFile(this);
   }//GEN-LAST:event_loadPairingsButton_ActionPerformed

   private void removePairingButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removePairingButtonActionPerformed
      covariantPairManager_.deletePair( (CovariantPairing)
              ((CovariantPairingsTableModel)covariantPairingsTable_.getModel()).getValueAt(covariantPairingsTable_.getSelectedRow(),1));
   }//GEN-LAST:event_removePairingButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ChannelsTab_;
    private javax.swing.JButton SettingsButton;
    private javax.swing.JTabbedPane acqTabbedPane_;
    private javax.swing.JButton addAcqButton_;
    private javax.swing.JButton addCovariedPairingValueButton_;
    private javax.swing.JComboBox autofocusFiducialCombo_;
    private javax.swing.JPanel autofocusTab_l;
    private javax.swing.JComboBox bottomSurfaceCombo_;
    private javax.swing.JLabel bottomSurfaceLabel_;
    private javax.swing.JButton browseButton_;
    private javax.swing.JLabel channelGroupLabel_;
    private javax.swing.JCheckBox channelsCheckBox_;
    private javax.swing.JPanel channelsPanel_;
    private javax.swing.JCheckBox checkBox2D_;
    private javax.swing.JCheckBox checkBox3D_;
    private javax.swing.JButton configPropsButton_;
    private javax.swing.JPanel controlPanelName_;
    private javax.swing.JTable covariantPairValuesTable_;
    private javax.swing.JTable covariantPairingsTable_;
    private javax.swing.JPanel covariedSettingsTab_;
    private javax.swing.JScrollPane customPropsScrollPane_;
    private javax.swing.JButton deinterleaveButton_;
    private javax.swing.JButton deleteAllRegionsButton_;
    private javax.swing.JButton deleteAllSurfacesButton_;
    private javax.swing.JButton deleteCovariedPairingValueButton_;
    private javax.swing.JButton deleteSelectedRegionButton_;
    private javax.swing.JButton deleteSelectedSurfaceButton_;
    private javax.swing.JLabel distanceAboveSurfaceLabel_;
    private javax.swing.JSpinner distanceAboveSurfaceSpinner_;
    private javax.swing.JLabel distanceBelowSurfaceLabel_;
    private javax.swing.JSpinner distanceBelowSurfaceSpinner_;
    private javax.swing.JButton exploreBrowseButton_;
    private javax.swing.JComboBox exploreChannelGroupCombo_;
    private javax.swing.JLabel exploreSampleLabel_;
    private javax.swing.JLabel exploreSavingDirLabel_;
    private javax.swing.JTextField exploreSavingDirTextField_;
    private javax.swing.JLabel exploreSavingNameLabel_;
    private javax.swing.JTextField exploreSavingNameTextField_;
    private javax.swing.JLabel exploreZStepLabel_;
    private javax.swing.JSpinner exploreZStepSpinner_;
    private javax.swing.JRadioButton fixedDistanceFromSurfaceRadioButton_;
    private javax.swing.JComboBox fixedDistanceSurfaceComboBox_;
    private javax.swing.JPanel fixedDistanceZPanel_;
    private javax.swing.JLabel fixedSurfaceLabel_;
    private javax.swing.JLabel footprin2DLabel_;
    private javax.swing.JComboBox footprint2DComboBox_;
    private javax.swing.JTable gridTable_;
    private javax.swing.JPanel gridsPanel_;
    private javax.swing.JButton intereaveButton_;
    private javax.swing.JComboBox jComboBox2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JButton loadPairingsButton_;
    private javax.swing.JButton moveAcqDownButton_;
    private javax.swing.JButton moveAcqUpButton_;
    private javax.swing.JScrollPane multipleAcqScrollPane_;
    private javax.swing.JTable multipleAcqTable_;
    private javax.swing.JPanel multipleAcquisitionsPanel;
    private javax.swing.JButton newChannelButton_;
    private javax.swing.JButton newExploreWindowButton_;
    private javax.swing.JButton newParingButton_;
    private javax.swing.JLabel numTimePointsLabel_;
    private javax.swing.JSpinner numTimePointsSpinner_;
    private javax.swing.JPanel panel2D_;
    private javax.swing.JTable propertyControlTable_;
    private javax.swing.JScrollPane propertyPairValuesScrollpane_;
    private javax.swing.JScrollPane propertyPairingsScrollpane_;
    private javax.swing.JButton removeAcqButton_;
    private javax.swing.JButton removeChannelButton_;
    private javax.swing.JButton removePairingButton;
    private javax.swing.JButton runAcqButton_;
    private javax.swing.JButton runMultipleAcquisitionsButton_;
    private javax.swing.JButton savePairingsButton_;
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
    private javax.swing.JComboBox volumeBetweenFootprintCombo_;
    private javax.swing.JRadioButton volumeBetweenSurfacesRadioButton_;
    private javax.swing.JPanel volumeBetweenZPanel_;
    private javax.swing.JLabel zEndLabel;
    private javax.swing.JSpinner zEndSpinner_;
    private javax.swing.ButtonGroup zStackModeButtonGroup_;
    private javax.swing.JLabel zStartLabel;
    private javax.swing.JSpinner zStartSpinner_;
    private javax.swing.JLabel zStepLabel;
    private javax.swing.JSpinner zStepSpinner_;
    // End of variables declaration//GEN-END:variables


}
