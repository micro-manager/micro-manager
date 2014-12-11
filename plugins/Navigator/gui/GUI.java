package gui;


import acq.CustomAcqEngine;
import acq.FixedAreaAcquisitionSettings;
import tables.PropertyControlTableModel;
import java.awt.Component;
import java.io.File;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import mmcorej.CMMCore;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.PropertyValueCellEditor;
import org.micromanager.utils.PropertyValueCellRenderer;
import org.micromanager.utils.ReportingUtils;
import surfacesandregions.RegionManager;
import surfacesandregions.SurfaceInterpolator;
import surfacesandregions.SurfaceManager;
import tables.SimpleChannelTableModel;


/**
 *
 * @author Henry
 */
public class GUI extends javax.swing.JFrame {

   public static final String PREF_SAVING_DIR = "Saving Directory";
   public static final String PREF_SAVING_NAME = "Saving name";
   
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
   private SettingsDialog settings_;
           
   public GUI(Preferences prefs, ScriptInterface mmapi, String version) {
      prefs_ = prefs;
      mmAPI_ = mmapi;
      core_ = mmapi.getMMCore();
      this.setTitle("5D Navigator " + version);
      try {
         String s = InetAddress.getLocalHost().getHostName();
         if (s.equals("BIDC-Sebastian")) {
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
            
      //intial values for GUI components
      savingNameTextField_.setText(prefs_.get(PREF_SAVING_NAME, "Untitled"));
      savingDirTextField_.setText(prefs_.get(PREF_SAVING_DIR, ""));
      timeIntervalTextField_.setText("0");
      distanceFromTopSurfaceField_.setText("0");
      simpleZStackRadioButton_.setSelected(true);
      zStepField_.setValue(1);
      zStartField_.setText(zPosition+ "");
      zEndField_.setText(zPosition+"");
      
      updateZStackComponents();
   }
     
   
   private void updateZStackComponents() {
      boolean zEnabled = zStackCheckBox_.isSelected();

      zStepLabel.setEnabled(zEnabled);
      zStepField_.setEnabled(zEnabled);

      simpleZStackRadioButton_.setEnabled(zEnabled);
      fixedDistanceFromSurfaceRadioButton_.setEnabled(zEnabled);
      volumeBetweenSurfacesRadioButton_.setEnabled(zEnabled);

      boolean simpleZ = simpleZStackRadioButton_.isSelected() && zEnabled;
      for (Component c : simpleZPanel_.getComponents()) {
         if (!(c instanceof JRadioButton)) {
            c.setEnabled(simpleZ);
         }
      }

      boolean fixedDist = fixedDistanceFromSurfaceRadioButton_.isSelected()&& zEnabled;
      for (Component c : fixedDistanceZPanel_.getComponents()) {
         if (!(c instanceof JRadioButton)) {
            c.setEnabled(fixedDist);
         }
      }

      boolean volumeBetween = volumeBetweenSurfacesRadioButton_.isSelected()&& zEnabled;
      for (Component c : volumeBetweenZPanel_.getComponents()) {
         if (!(c instanceof JRadioButton)) {
            c.setEnabled(volumeBetween);
         }
      }
   }
   
   private void refresh() {
      //Refresh z stage, xy stage, etc and transmit them to eng?
   }

   private void addTextFieldListeners() {
      savingDirTextField_.getDocument().addDocumentListener(new DocumentListener() {

         @Override
         public void insertUpdate(DocumentEvent e) {
            prefs_.put(PREF_SAVING_DIR, savingDirTextField_.getText());
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            prefs_.put(PREF_SAVING_DIR, savingDirTextField_.getText());
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            prefs_.put(PREF_SAVING_DIR, savingDirTextField_.getText());
         }
      });
      savingNameTextField_.getDocument().addDocumentListener(new DocumentListener() {

         @Override
         public void insertUpdate(DocumentEvent e) {
            prefs_.put(PREF_SAVING_NAME, savingNameTextField_.getText());
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            prefs_.put(PREF_SAVING_NAME, savingNameTextField_.getText());
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            prefs_.put(PREF_SAVING_NAME, savingNameTextField_.getText());
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
        zTextField_ = new javax.swing.JTextField();
        zLabel_ = new javax.swing.JLabel();
        zSlider_ = new javax.swing.JSlider();
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
        timeIntervalTextField_ = new javax.swing.JFormattedTextField(DecimalFormat.getInstance());
        timePointsCheckBox_ = new javax.swing.JCheckBox();
        zStackTab_ = new javax.swing.JPanel();
        simpleZPanel_ = new javax.swing.JPanel();
        simpleZStackRadioButton_ = new javax.swing.JRadioButton();
        zStartLabel = new javax.swing.JLabel();
        zEndLabel = new javax.swing.JLabel();
        zStartField_ = new javax.swing.JFormattedTextField(DecimalFormat.getInstance());
        zEndField_ = new javax.swing.JFormattedTextField(DecimalFormat.getInstance());
        volumeBetweenZPanel_ = new javax.swing.JPanel();
        volumeBetweenSurfacesRadioButton_ = new javax.swing.JRadioButton();
        topSurfaceLabel_ = new javax.swing.JLabel();
        bottomSurfaceLabel_ = new javax.swing.JLabel();
        topSurfaceCombo_ = new javax.swing.JComboBox();
        bottomSurfaceCombo_ = new javax.swing.JComboBox();
        zStackCheckBox_ = new javax.swing.JCheckBox();
        fixedDistanceZPanel_ = new javax.swing.JPanel();
        fixedDistanceFromSurfaceRadioButton_ = new javax.swing.JRadioButton();
        fixedSurfaceLabel_ = new javax.swing.JLabel();
        fixedDistanceSurfaceComboBox_ = new javax.swing.JComboBox();
        distanceToFixedSurfaceLabel_ = new javax.swing.JLabel();
        distanceFromTopSurfaceField_ = new javax.swing.JFormattedTextField(NumberFormat.getInstance());
        zStepLabel = new javax.swing.JLabel();
        zStepField_ = new javax.swing.JFormattedTextField(NumberFormat.getInstance());
        autofocusTab_l = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        jComboBox4 = new javax.swing.JComboBox();
        ChannelsTab_ = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        channelsCheckBox_ = new javax.swing.JCheckBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        newChannelButton_ = new javax.swing.JButton();
        removeChannelButton_ = new javax.swing.JButton();
        jComboBox2 = new javax.swing.JComboBox();
        jLabel3 = new javax.swing.JLabel();
        jPanel7 = new javax.swing.JPanel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        customPropsScrollPane_ = new javax.swing.JScrollPane();
        propertyControlTable_ = new javax.swing.JTable();
        multipleAcquisitionsPanel = new javax.swing.JPanel();
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
        SettingsButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        configPropsButton_ = new javax.swing.JButton();
        runAcqButton_ = new javax.swing.JButton();
        newExploreWindowButton_ = new javax.swing.JButton();

        zTextField_.setText("jTextField1");
        zTextField_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zTextField_ActionPerformed(evt);
            }
        });

        zLabel_.setText("Z: ");

        zSlider_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                zSliderAdjusted(evt);
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
                        .addGap(0, 468, Short.MAX_VALUE))
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
                .addContainerGap(61, Short.MAX_VALUE))
        );

        acqTabbedPane_.addTab("General", savingTab_);

        timePointsPanel_.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        timeIntevalUnitCombo_.setModel(new DefaultComboBoxModel(new String[]{"ms", "s", "min"}));

        timeIntervalLabel_.setText("Interval");

        numTimePointsLabel_.setText("Number");

        numTimePointsSpinner_.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1), Integer.valueOf(1), null, Integer.valueOf(1)));

        timeIntervalTextField_.setText("jFormattedTextField1");

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
                        .addComponent(timeIntervalTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, 68, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
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
                        .addGroup(timePointsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(timePointsPanel_Layout.createSequentialGroup()
                                .addComponent(timeIntervalLabel_)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, timePointsPanel_Layout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addComponent(timeIntervalTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap())))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, timePointsPanel_Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(timeIntevalUnitCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap())))
        );

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
                .addContainerGap(426, Short.MAX_VALUE))
        );
        timePointsTab_Layout.setVerticalGroup(
            timePointsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(timePointsTab_Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(timePointsCheckBox_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timePointsPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 67, Short.MAX_VALUE))
        );

        for (Component c : timePointsPanel_.getComponents()) {
            c.setEnabled(false);
        }

        acqTabbedPane_.addTab("Time Points", timePointsTab_);

        simpleZPanel_.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        zStackModeButtonGroup_.add(simpleZStackRadioButton_);
        simpleZStackRadioButton_.setText("Simple Z stack");
        simpleZStackRadioButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                simpleZStackRadioButton_ActionPerformed(evt);
            }
        });

        zStartLabel.setText("Z-start (µm)");

        zEndLabel.setText("Z-end (µm)");

        zStartField_.setText("jFormattedTextField1");

        zEndField_.setText("jFormattedTextField2");

        javax.swing.GroupLayout simpleZPanel_Layout = new javax.swing.GroupLayout(simpleZPanel_);
        simpleZPanel_.setLayout(simpleZPanel_Layout);
        simpleZPanel_Layout.setHorizontalGroup(
            simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(simpleZPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addGroup(simpleZPanel_Layout.createSequentialGroup()
                            .addComponent(zEndLabel)
                            .addGap(18, 18, 18)
                            .addComponent(zEndField_, javax.swing.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE))
                        .addGroup(simpleZPanel_Layout.createSequentialGroup()
                            .addComponent(zStartLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(zStartField_, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(simpleZStackRadioButton_))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        simpleZPanel_Layout.setVerticalGroup(
            simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(simpleZPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(simpleZStackRadioButton_)
                .addGap(8, 8, 8)
                .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(zStartLabel)
                    .addComponent(zStartField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(zEndLabel)
                    .addComponent(zEndField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        volumeBetweenZPanel_.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        zStackModeButtonGroup_.add(volumeBetweenSurfacesRadioButton_);
        volumeBetweenSurfacesRadioButton_.setText("Volume between two surfaces");
        volumeBetweenSurfacesRadioButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                volumeBetweenSurfacesRadioButton_ActionPerformed(evt);
            }
        });

        topSurfaceLabel_.setText("Top surface:");

        bottomSurfaceLabel_.setText("Bottom surface: ");

        topSurfaceCombo_.setModel(surfaceManager_.createSurfaceComboBoxModel());

        bottomSurfaceCombo_.setModel(surfaceManager_.createSurfaceComboBoxModel());

        javax.swing.GroupLayout volumeBetweenZPanel_Layout = new javax.swing.GroupLayout(volumeBetweenZPanel_);
        volumeBetweenZPanel_.setLayout(volumeBetweenZPanel_Layout);
        volumeBetweenZPanel_Layout.setHorizontalGroup(
            volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(volumeBetweenZPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(volumeBetweenSurfacesRadioButton_)
                    .addGroup(volumeBetweenZPanel_Layout.createSequentialGroup()
                        .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(topSurfaceLabel_)
                            .addComponent(bottomSurfaceLabel_))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(bottomSurfaceCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(topSurfaceCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(6, Short.MAX_VALUE))
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
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        zStackCheckBox_.setText("Z stack");
        zStackCheckBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zStackCheckBox_ActionPerformed(evt);
            }
        });

        fixedDistanceZPanel_.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        zStackModeButtonGroup_.add(fixedDistanceFromSurfaceRadioButton_);
        fixedDistanceFromSurfaceRadioButton_.setText("Fixed distance below surface");
        fixedDistanceFromSurfaceRadioButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixedDistanceFromSurfaceRadioButton_ActionPerformed(evt);
            }
        });

        fixedSurfaceLabel_.setText("Surface: ");

        fixedDistanceSurfaceComboBox_.setModel(surfaceManager_.createSurfaceComboBoxModel());
        fixedDistanceSurfaceComboBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixedDistanceSurfaceComboBox_ActionPerformed(evt);
            }
        });

        distanceToFixedSurfaceLabel_.setText("Distance (µm):");

        distanceFromTopSurfaceField_.setText("jFormattedTextField1");

        javax.swing.GroupLayout fixedDistanceZPanel_Layout = new javax.swing.GroupLayout(fixedDistanceZPanel_);
        fixedDistanceZPanel_.setLayout(fixedDistanceZPanel_Layout);
        fixedDistanceZPanel_Layout.setHorizontalGroup(
            fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(fixedSurfaceLabel_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(fixedDistanceSurfaceComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 91, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                    .addComponent(distanceToFixedSurfaceLabel_)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(distanceFromTopSurfaceField_, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(14, 14, 14))
                .addComponent(fixedDistanceFromSurfaceRadioButton_))
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
                    .addComponent(distanceToFixedSurfaceLabel_)
                    .addComponent(distanceFromTopSurfaceField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        zStepLabel.setText("Z-step (µm)");

        zStepField_.setText("jFormattedTextField3");

        javax.swing.GroupLayout zStackTab_Layout = new javax.swing.GroupLayout(zStackTab_);
        zStackTab_.setLayout(zStackTab_Layout);
        zStackTab_Layout.setHorizontalGroup(
            zStackTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(zStackTab_Layout.createSequentialGroup()
                .addGroup(zStackTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(zStackTab_Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(zStackTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(zStackCheckBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 107, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(zStackTab_Layout.createSequentialGroup()
                                .addComponent(simpleZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(fixedDistanceZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(10, 10, 10)
                                .addComponent(volumeBetweenZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(zStackTab_Layout.createSequentialGroup()
                        .addGap(162, 162, 162)
                        .addComponent(zStepLabel)
                        .addGap(18, 18, 18)
                        .addComponent(zStepField_, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(95, Short.MAX_VALUE))
        );
        zStackTab_Layout.setVerticalGroup(
            zStackTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(zStackTab_Layout.createSequentialGroup()
                .addComponent(zStackCheckBox_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(zStackTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(fixedDistanceZPanel_, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(simpleZPanel_, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(volumeBetweenZPanel_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(zStackTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(zStepField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(zStepLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        for (Component c : simpleZPanel_.getComponents()) {
            c.setEnabled(false);
        }
        for (Component c : volumeBetweenZPanel_.getComponents()) {
            c.setEnabled(false);
        }

        acqTabbedPane_.addTab("Z-Stack", zStackTab_);

        jLabel7.setText("Fiducial channel index:");

        jComboBox4.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        javax.swing.GroupLayout autofocusTab_lLayout = new javax.swing.GroupLayout(autofocusTab_l);
        autofocusTab_l.setLayout(autofocusTab_lLayout);
        autofocusTab_lLayout.setHorizontalGroup(
            autofocusTab_lLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(autofocusTab_lLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jComboBox4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(472, Short.MAX_VALUE))
        );
        autofocusTab_lLayout.setVerticalGroup(
            autofocusTab_lLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(autofocusTab_lLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(autofocusTab_lLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(jComboBox4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(141, Short.MAX_VALUE))
        );

        acqTabbedPane_.addTab("Cross correlation autofocus", autofocusTab_l);

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        channelsCheckBox_.setText("Channels");

        jTable1.setModel(new SimpleChannelTableModel());
        jScrollPane1.setViewportView(jTable1);

        newChannelButton_.setText("New");
        newChannelButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newChannelButton_ActionPerformed(evt);
            }
        });

        removeChannelButton_.setText("Remove");

        jComboBox2.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jLabel3.setText("Channel group:");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(channelsCheckBox_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(newChannelButton_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(removeChannelButton_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, 107, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 636, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
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
            .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        ChannelsTab_Layout.setVerticalGroup(
            ChannelsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(ChannelsTab_Layout.createSequentialGroup()
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 40, Short.MAX_VALUE))
        );

        acqTabbedPane_.addTab("Channels", ChannelsTab_);

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 650, Short.MAX_VALUE)
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 172, Short.MAX_VALUE)
        );

        acqTabbedPane_.addTab("Focus varying properties", jPanel7);

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

        javax.swing.GroupLayout multipleAcquisitionsPanelLayout = new javax.swing.GroupLayout(multipleAcquisitionsPanel);
        multipleAcquisitionsPanel.setLayout(multipleAcquisitionsPanelLayout);
        multipleAcquisitionsPanelLayout.setHorizontalGroup(
            multipleAcquisitionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 660, Short.MAX_VALUE)
        );
        multipleAcquisitionsPanelLayout.setVerticalGroup(
            multipleAcquisitionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 136, Short.MAX_VALUE)
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

        SettingsButton.setText("Settings");
        SettingsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SettingsButtonActionPerformed(evt);
            }
        });

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel1.setText("Acquisition Settings");

        configPropsButton_.setText("Configure device control properties");
        configPropsButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                configPropsButton_ActionPerformed(evt);
            }
        });

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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jTabbedPane1)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                            .addContainerGap()
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(acqTabbedPane_)
                                .addGroup(layout.createSequentialGroup()
                                    .addComponent(zLabel_)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel1)
                                        .addGroup(layout.createSequentialGroup()
                                            .addComponent(zTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, 68, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                            .addComponent(zSlider_, javax.swing.GroupLayout.PREFERRED_SIZE, 562, javax.swing.GroupLayout.PREFERRED_SIZE)))))))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(162, 162, 162)
                        .addComponent(runAcqButton_)
                        .addGap(106, 106, 106)
                        .addComponent(newExploreWindowButton_)))
                .addContainerGap())
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(configPropsButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(SettingsButton)
                .addGap(18, 18, 18))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(zLabel_)
                        .addComponent(zTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(zSlider_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 164, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(acqTabbedPane_, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(newExploreWindowButton_)
                        .addGap(143, 143, 143)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(configPropsButton_)
                            .addComponent(SettingsButton)))
                    .addComponent(runAcqButton_))
                .addGap(21, 21, 21))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

   private void newExploreWindowButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newExploreWindowButton_ActionPerformed
      new ExploreInitDialog(eng_, savingDirTextField_.getText(), savingNameTextField_.getText() );
   }//GEN-LAST:event_newExploreWindowButton_ActionPerformed

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
      // TODO add your handling code here:
   }//GEN-LAST:event_newChannelButton_ActionPerformed

   private void volumeBetweenSurfacesRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_volumeBetweenSurfacesRadioButton_ActionPerformed
       updateZStackComponents();
   }//GEN-LAST:event_volumeBetweenSurfacesRadioButton_ActionPerformed

   private void zStackCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zStackCheckBox_ActionPerformed
      updateZStackComponents();
   }//GEN-LAST:event_zStackCheckBox_ActionPerformed

   private void timePointsCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timePointsCheckBox_ActionPerformed
      for (Component c : timePointsPanel_.getComponents()) {
         c.setEnabled(timePointsCheckBox_.isSelected());
      }
   }//GEN-LAST:event_timePointsCheckBox_ActionPerformed

   private void SettingsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SettingsButtonActionPerformed
      if (settings_ == null) {
         settings_ = new SettingsDialog(prefs_, this);
      } else {
         settings_.setVisible(true);
      }
      
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
      // collect all acq settings
      FixedAreaAcquisitionSettings settings = new FixedAreaAcquisitionSettings(
              savingDirTextField_.getText(), savingNameTextField_.getText(),
              (Integer) numTimePointsSpinner_.getValue(), (Number) timeIntervalTextField_.getValue(), timeIntevalUnitCombo_.getSelectedIndex(),
              zStackCheckBox_.isSelected() ? simpleZStackRadioButton_.isSelected() ? FixedAreaAcquisitionSettings.SIMPLE_Z_STACK : 
              volumeBetweenSurfacesRadioButton_.isSelected() ? FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK :
              FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK : FixedAreaAcquisitionSettings.NO_Z_STACK,
              (Number) zStartField_.getValue(), (Number) zEndField_.getValue(), 
              (SurfaceInterpolator) surfaceManager_.getSurface(fixedDistanceSurfaceComboBox_.getSelectedIndex()), (Number) distanceFromTopSurfaceField_.getValue(),
              (SurfaceInterpolator) surfaceManager_.getSurface(topSurfaceCombo_.getSelectedIndex()), (SurfaceInterpolator) surfaceManager_.getSurface(bottomSurfaceCombo_.getSelectedIndex()),
              (Number) zStepField_.getValue());
                     
      //run acquisition
      eng_.runFixedAreaAcquisition(settings);
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
      // TODO add your handling code here:
   }//GEN-LAST:event_fixedDistanceSurfaceComboBox_ActionPerformed

   private void fixedDistanceFromSurfaceRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixedDistanceFromSurfaceRadioButton_ActionPerformed
     updateZStackComponents();
   }//GEN-LAST:event_fixedDistanceFromSurfaceRadioButton_ActionPerformed

   private void simpleZStackRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_simpleZStackRadioButton_ActionPerformed
     updateZStackComponents();
   }//GEN-LAST:event_simpleZStackRadioButton_ActionPerformed

 

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel ChannelsTab_;
    private javax.swing.JButton SettingsButton;
    private javax.swing.JTabbedPane acqTabbedPane_;
    private javax.swing.JPanel autofocusTab_l;
    private javax.swing.JComboBox bottomSurfaceCombo_;
    private javax.swing.JLabel bottomSurfaceLabel_;
    private javax.swing.JButton browseButton_;
    private javax.swing.JCheckBox channelsCheckBox_;
    private javax.swing.JButton configPropsButton_;
    private javax.swing.JScrollPane customPropsScrollPane_;
    private javax.swing.JButton deleteAllRegionsButton_;
    private javax.swing.JButton deleteAllSurfacesButton_;
    private javax.swing.JButton deleteSelectedRegionButton_;
    private javax.swing.JButton deleteSelectedSurfaceButton_;
    private javax.swing.JFormattedTextField distanceFromTopSurfaceField_;
    private javax.swing.JLabel distanceToFixedSurfaceLabel_;
    private javax.swing.JRadioButton fixedDistanceFromSurfaceRadioButton_;
    private javax.swing.JComboBox fixedDistanceSurfaceComboBox_;
    private javax.swing.JPanel fixedDistanceZPanel_;
    private javax.swing.JLabel fixedSurfaceLabel_;
    private javax.swing.JTable gridTable_;
    private javax.swing.JPanel gridsPanel_;
    private javax.swing.JComboBox jComboBox2;
    private javax.swing.JComboBox jComboBox4;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JPanel multipleAcquisitionsPanel;
    private javax.swing.JButton newChannelButton_;
    private javax.swing.JButton newExploreWindowButton_;
    private javax.swing.JLabel numTimePointsLabel_;
    private javax.swing.JSpinner numTimePointsSpinner_;
    private javax.swing.JTable propertyControlTable_;
    private javax.swing.JButton removeChannelButton_;
    private javax.swing.JButton runAcqButton_;
    private javax.swing.JLabel savingDirLabel_;
    private javax.swing.JTextField savingDirTextField_;
    private javax.swing.JLabel savingNameLabel_;
    private javax.swing.JTextField savingNameTextField_;
    private javax.swing.JPanel savingTab_;
    private javax.swing.JPanel simpleZPanel_;
    private javax.swing.JRadioButton simpleZStackRadioButton_;
    private javax.swing.JPanel surfacesPanel_;
    private javax.swing.JTable surfacesTable_;
    private javax.swing.JLabel timeIntervalLabel_;
    private javax.swing.JFormattedTextField timeIntervalTextField_;
    private javax.swing.JComboBox timeIntevalUnitCombo_;
    private javax.swing.JCheckBox timePointsCheckBox_;
    private javax.swing.JPanel timePointsPanel_;
    private javax.swing.JPanel timePointsTab_;
    private javax.swing.JComboBox topSurfaceCombo_;
    private javax.swing.JLabel topSurfaceLabel_;
    private javax.swing.JRadioButton volumeBetweenSurfacesRadioButton_;
    private javax.swing.JPanel volumeBetweenZPanel_;
    private javax.swing.JFormattedTextField zEndField_;
    private javax.swing.JLabel zEndLabel;
    private javax.swing.JLabel zLabel_;
    private javax.swing.JSlider zSlider_;
    private javax.swing.JCheckBox zStackCheckBox_;
    private javax.swing.ButtonGroup zStackModeButtonGroup_;
    private javax.swing.JPanel zStackTab_;
    private javax.swing.JFormattedTextField zStartField_;
    private javax.swing.JLabel zStartLabel;
    private javax.swing.JFormattedTextField zStepField_;
    private javax.swing.JLabel zStepLabel;
    private javax.swing.JTextField zTextField_;
    // End of variables declaration//GEN-END:variables


}
