///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package org.micromanager.plugins.magellan.gui;

import org.micromanager.plugins.magellan.acq.MagellanEngine;
import org.micromanager.plugins.magellan.acq.ExploreAcqSettings;
import org.micromanager.plugins.magellan.acq.FixedAreaAcquisitionSettings;
import org.micromanager.plugins.magellan.misc.LoadedAcquisitionData;
import org.micromanager.plugins.magellan.acq.MultipleAcquisitionManager;
import org.micromanager.plugins.magellan.acq.MultipleAcquisitionTableModel;
import org.micromanager.plugins.magellan.autofocus.AutofocusChannelComboModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.micromanager.plugins.magellan.mmcloneclasses.utils.PropertyValueCellEditor;
import org.micromanager.plugins.magellan.mmcloneclasses.utils.PropertyValueCellRenderer;
import mmcorej.StrVector;
import org.micromanager.plugins.magellan.propsandcovariants.DeviceControlTableModel;
import org.micromanager.plugins.magellan.propsandcovariants.CovariantPairValuesTableModel;
import org.micromanager.plugins.magellan.propsandcovariants.CovariantPairing;
import org.micromanager.plugins.magellan.propsandcovariants.CovariantPairingsManager;
import org.micromanager.plugins.magellan.propsandcovariants.CovariantPairingsTableModel;
import org.micromanager.plugins.magellan.propsandcovariants.CovariantValueCellEditor;
import org.micromanager.plugins.magellan.propsandcovariants.CovariantValueCellRenderer;
import org.micromanager.plugins.magellan.surfacesandregions.RegionManager;
import org.micromanager.plugins.magellan.surfacesandregions.SurfaceManager;
import org.micromanager.plugins.magellan.surfacesandregions.SurfaceRegionComboBoxModel;
import org.micromanager.plugins.magellan.surfacesandregions.XYFootprint;
import org.micromanager.plugins.magellan.misc.ExactlyOneRowSelectionModel;
import org.micromanager.plugins.magellan.channels.SimpleChannelTableModel;
import org.micromanager.plugins.magellan.coordinates.AffineGUI;
import org.micromanager.plugins.magellan.bidc.JavaLayerImageConstructor;
import org.micromanager.plugins.magellan.bidc.FrameIntegrationMethod;
import org.micromanager.plugins.magellan.channels.ChannelComboBoxModel;
import org.micromanager.plugins.magellan.channels.ChannelSetting;
import org.micromanager.plugins.magellan.channels.ColorEditor;
import org.micromanager.plugins.magellan.channels.ColorRenderer;
import java.awt.FileDialog;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultCellEditor;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.micromanager.plugins.magellan.acq.AcqDurationEstimator;
import org.micromanager.plugins.magellan.acq.MultiResMultipageTiffStorage;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.GlobalSettings;
import org.micromanager.plugins.magellan.misc.JavaUtils;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.propsandcovariants.LaserPredNet;


/**
 *
 * @author Henry
 */
public class GUI extends javax.swing.JFrame {

   private static final String PREF_SIZE_WIDTH = "Magellan gui size width";
   private static final String PREF_SIZE_HEIGHT = "Magellan gui size height";
   private static final String PREF_SPLIT_PANE = "split pane location";
    private static final Color DARK_GREEN = new Color(0, 128, 0);
    private MagellanEngine eng_;
    private AcqDurationEstimator acqDurationEstimator_;
    private Preferences prefs_;
    private RegionManager regionManager_ = new RegionManager();
    private SurfaceManager surfaceManager_ = new SurfaceManager();
    private CovariantPairingsManager covariantPairManager_;
    private MultipleAcquisitionManager multiAcqManager_;
    private GlobalSettings settings_;
    private boolean storeAcqSettings_ = true;
    private int multiAcqSelectedIndex_ = 0;
    private LinkedList<JSpinner> offsetSpinners_ = new LinkedList<JSpinner>();
    private static GUI singleton_;

   public GUI(Preferences prefs, String version) {
      singleton_ = this;
      prefs_ = prefs;
      settings_ = new GlobalSettings(prefs_, this);
      new JavaLayerImageConstructor();
      this.setTitle("Micro-Magellan " + version);
      acqDurationEstimator_ = new AcqDurationEstimator();
      eng_ = new MagellanEngine(Magellan.getCore(), acqDurationEstimator_);
      multiAcqManager_ = new MultipleAcquisitionManager(this, eng_);
      covariantPairManager_ = new CovariantPairingsManager(this, multiAcqManager_);
      initComponents();
      moreInitialization();
      this.setVisible(true);
      updatePropertiesTable();
      addTextFieldListeners();
      addGlobalSettingsListeners();
      storeCurrentAcqSettings();
      if (GlobalSettings.getInstance().firstMagellanOpening()) {
         new StartupHelpWindow();
      }      
   }
   
   public static GUI getInstance() {
      return singleton_;
   }
   
   public void acquisitionRunning(boolean running) {
      //disable or enabe the controls that cannot be changed during acquisition
      zStepSpinner_.setEnabled(!running);
      zStepLabel_.setEnabled(!running);
      savingNameLabel_.setEnabled(!running);
      savingNameTextField_.setEnabled(!running);
      acqTileOverlapLabel_.setEnabled(!running);
      acqOverlapPercentSpinner_.setEnabled(!running);
      tileOverlapPercentLabel_.setEnabled(!running);
      this.repaint();
   }
   
   public static void updateEstiamtedDurationLabel(final String text) {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            singleton_.estDurationLabel_.setText(text);
         }
      });
   }

   private void fitSplitPaneToWindowSize() {
      splitPane_.setDividerLocation(splitPane_.getMaximumDividerLocation());
   }

   public void acquisitionSettingsChanged() {
      //refresh GUI and store its state in current acq settings
      refreshAcqTabTitleText();
      storeCurrentAcqSettings();
   }

   public FixedAreaAcquisitionSettings getActiveAcquisitionSettings() {
      return multiAcqManager_.getAcquisitionSettings(multiAcqSelectedIndex_);
   }

   public XYFootprint getFootprintObject(int index) {
      //regions first then surfaces
      if (index < regionManager_.getNumberOfRegions()) {
         return regionManager_.getRegion(index);
      } else {
         return surfaceManager_.getSurface(index - regionManager_.getNumberOfRegions());
      }
   }

   public static SurfaceRegionComboBoxModel createSurfaceAndRegionComboBoxModel(boolean surfaces, boolean regions) {
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
                ((DeviceControlTableModel) (deviceControlTable_.getModel())).updateStoredProps();
                ((AbstractTableModel) deviceControlTable_.getModel()).fireTableDataChanged();

                //autofit columns
                deviceControlTable_.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                TableColumn col1 = deviceControlTable_.getColumnModel().getColumn(0);
                int preferredWidth = col1.getMinWidth();
                for (int row = 0; row < deviceControlTable_.getRowCount(); row++) {
                    TableCellRenderer cellRenderer = deviceControlTable_.getCellRenderer(row, 0);
                    Component c = deviceControlTable_.prepareRenderer(cellRenderer, row, 0);
                    int width = c.getPreferredSize().width + deviceControlTable_.getIntercellSpacing().width;
                    preferredWidth = Math.max(preferredWidth, width);
                }
                col1.setPreferredWidth(preferredWidth);
                TableColumn col2 = deviceControlTable_.getColumnModel().getColumn(1);
                deviceControlTable_.getHeight();
                col2.setPreferredWidth(deviceControlTable_.getParent().getParent().getWidth() - preferredWidth
                        - (deviceControlScrollPane_.getVerticalScrollBar().isVisible() ? deviceControlScrollPane_.getVerticalScrollBar().getWidth() : 0));
            }
        }).start();
    }

    private void moreInitialization() {
       //add link to user guide label
       userGuideLink_.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            new Thread(new Runnable() {
               @Override
               public void run() {
                  try {            
                     ij.plugin.BrowserLauncher.openURL("https://micro-manager.org/wiki/MicroMagellan");
                  } catch (IOException ex) {
                     Log.log("couldn't open User guide link");
                  }
               }
            }).start();
         }
      });
       //add link to citation
       citeLink_.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            new Thread(new Runnable() {
               @Override
               public void run() {
                  try {            
                     ij.plugin.BrowserLauncher.openURL("http://www.nature.com/nmeth/journal/v13/n10/full/nmeth.3991.html");
                  } catch (IOException ex) {
                     Log.log("couldn't open citation link");
                  }
               }
            }).start();
         }
      });
       
       covariantPairingsTable_.setSelectionModel(new ExactlyOneRowSelectionModel());
        covariantPairingsTable_.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                    //action occurs second time this method is called, after the table gains focus
                }
                //populate covariant values table
                covariantPairValuesTable_.editingStopped(null);
                int index = covariantPairingsTable_.getSelectedRow();
                if (covariantPairingsTable_.getRowCount() == 0) {
                    index = -1;
                }
                CovariantPairing activePair = (CovariantPairing) covariantPairingsTable_.getModel().getValueAt(index, 1);

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
                    multipleAcqTable_.getSelectionModel().setSelectionInterval(multiAcqSelectedIndex_ - 1, multiAcqSelectedIndex_ - 1);
                }
                populateAcqControls(multiAcqManager_.getAcquisitionSettings(multiAcqSelectedIndex_));
            }
        });
        //Table column widths
        multipleAcqTable_.getColumnModel().getColumn(0).setMaxWidth(60); //order column
        covariantPairingsTable_.getColumnModel().getColumn(0).setMaxWidth(60); //Acitve checkbox column
       channelsTable_.getColumnModel().getColumn(0).setMaxWidth(60); //Acitve checkbox column

       //set color renderer for channel table
       for (int col = 1; col < channelsTable_.getColumnModel().getColumnCount(); col++) {
          if (col == 3) {
             ColorRenderer cr = new ColorRenderer(true);
             ColorEditor ce = new ColorEditor((AbstractTableModel) channelsTable_.getModel(), col);
             channelsTable_.getColumnModel().getColumn(col).setCellRenderer(cr);
             channelsTable_.getColumnModel().getColumn(col).setCellEditor(ce);
          } else {
             DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
             renderer.setHorizontalAlignment(SwingConstants.LEFT); // left justify
             channelsTable_.getColumnModel().getColumn(col).setCellRenderer(renderer);
          }
          if (col == 2) {
              //left justified editor
              JTextField tf = new JTextField();
              tf.setHorizontalAlignment(SwingConstants.LEFT);
              DefaultCellEditor ed = new DefaultCellEditor(tf);
              channelsTable_.getColumnModel().getColumn(col).setCellEditor(ed);
          }
       }
      
        //load global settings     
        globalSavingDirTextField_.setText(settings_.getStoredSavingDirectory());
        //load explore settings
        exploreSavingNameTextField_.setText(ExploreAcqSettings.getNameFromPrefs());
        exploreZStepSpinner_.setValue(ExploreAcqSettings.getZStepFromPrefs());
        exploreTileOverlapSpinner_.setValue(ExploreAcqSettings.getExploreTileOverlapFromPrefs());

        populateAcqControls(multiAcqManager_.getAcquisitionSettings(0));
        enableAcquisitionComponentsAsNeeded();

        int width = settings_.getIntInPrefs(PREF_SIZE_WIDTH, Integer.MIN_VALUE);
        int height = settings_.getIntInPrefs(PREF_SIZE_HEIGHT, Integer.MIN_VALUE);
        if (height != Integer.MIN_VALUE && width != Integer.MIN_VALUE ) {
           this.setSize(width, height);
        } 
        
        int splitPane = settings_.getIntInPrefs(PREF_SPLIT_PANE,Integer.MIN_VALUE);
        if (splitPane != Integer.MIN_VALUE) {
           splitPane_.setDividerLocation(splitPane);
        }

        //save resizing
        this.addComponentListener(new ComponentAdapter() {
           @Override
           public void componentResized(ComponentEvent e) {
              settings_.storeIntInPrefs(PREF_SIZE_WIDTH, GUI.this.getWidth());
              settings_.storeIntInPrefs(PREF_SIZE_HEIGHT, GUI.this.getHeight());
              fitSplitPaneToWindowSize();
          }
       });
       //save splitpane position
       splitPane_.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener() {

           @Override
           public void propertyChange(PropertyChangeEvent evt) {
              settings_.storeIntInPrefs(PREF_SPLIT_PANE, splitPane_.getDividerLocation());
           }
       });
       fitSplitPaneToWindowSize();
        
        
       if (GlobalSettings.getInstance().isBIDCTwoPhoton()) {
          //store offsetSpinners in list for fast access
          offsetSpinners_.add(ch0OffsetSpinner_);
          offsetSpinners_.add(ch1OffsetSpinner_);
          offsetSpinners_.add(ch2OffsetSpinner_);
          offsetSpinners_.add(ch3OffsetSpinner_);
          offsetSpinners_.add(ch4OffsetSpinner_);
          offsetSpinners_.add(ch5OffsetSpinner_);
          //synchronize cpp layer offsets with these
          settings_.channelOffsetChanged();
       } else {
          //disable BIDC 2P features
          acqTabbedPane_.remove(imageFilteringTab_);
          splitPaneBottomPanel_.remove(exploreRankFilterButton_);
          splitPaneBottomPanel_.remove(exploreRankSpinner_);
          splitPaneBottomPanel_.remove(exploreFrameAverageButton_);
       }
    }

    public static double getExploreRankSetting() {
        return ((Number) singleton_.exploreRankSpinner_.getValue()).doubleValue();
    }
    
    public Integer getChannelOffset(int i) {
        if (i < offsetSpinners_.size()) {
            return ((Number) offsetSpinners_.get(i).getValue()).intValue();
        }
        return null;
    }
    
    public void selectNewCovariantPair() {
        //set bottom row selected because it was just added
        covariantPairingsTable_.setRowSelectionInterval(covariantPairingsTable_.getRowCount() - 1, covariantPairingsTable_.getRowCount() - 1);
    }
    
    public void refreshAcquisitionSettings() {
        //so that acquisition names can be changed form multi acquisitiion table
        populateAcqControls(multiAcqManager_.getAcquisitionSettings(multiAcqSelectedIndex_));
    }
    
    private void refreshAcqTabTitleText() {
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
       l3.setFont(acqTabbedPane_.getComponent(2).getFont().deriveFont(checkBox3D_.isSelected() || checkBox2D_.isSelected() ? Font.BOLD : Font.PLAIN));
       acqTabbedPane_.setTabComponentAt(2, l3);
       JLabel l4 = new JLabel("Channels");
       l4.setForeground(((SimpleChannelTableModel) channelsTable_.getModel()).anyChannelsActive() ? DARK_GREEN : Color.black);
       l4.setFont(acqTabbedPane_.getComponent(3).getFont().deriveFont(((SimpleChannelTableModel) channelsTable_.getModel()).anyChannelsActive()
               ? Font.BOLD : Font.PLAIN));
       acqTabbedPane_.setTabComponentAt(3, l4);
       JLabel l5 = new JLabel("Covaried Settings");
       l5.setForeground(((CovariantPairingsTableModel) covariantPairingsTable_.getModel()).isAnyPairingActive() ? DARK_GREEN : Color.black);
       l5.setFont(acqTabbedPane_.getComponent(4).getFont().deriveFont(((CovariantPairingsTableModel) covariantPairingsTable_.getModel()).isAnyPairingActive() ? Font.BOLD : Font.PLAIN));
        acqTabbedPane_.setTabComponentAt(4, l5);
        JLabel l6 = new JLabel("Drift Compensation");
        l6.setForeground(useAutofocusCheckBox_.isSelected() ? DARK_GREEN : Color.black);
        l6.setFont(acqTabbedPane_.getComponent(5).getFont().deriveFont((useAutofocusCheckBox_.isSelected() ? Font.BOLD : Font.PLAIN)));
        acqTabbedPane_.setTabComponentAt(5, l6);

        acqTabbedPane_.invalidate();
        acqTabbedPane_.validate();
    }

    private void enableAcquisitionComponentsAsNeeded() {
        //Set Tab titles
        refreshAcqTabTitleText();
        //Enable or disable time point stuff
        for (Component c : timePointsPanel_.getComponents()) {
            c.setEnabled(timePointsCheckBox_.isSelected());
        }
        //disable all Z stuff then renable as apporpriate
        zStepLabel_.setEnabled(false);
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
            boolean collectionPlane = collectionPlaneCheckBox_.isSelected();
            collectionPlaneLabel_.setEnabled(collectionPlane);
            collectionPlaneCombo_.setEnabled(collectionPlane);
        } else if (checkBox3D_.isSelected()) {
            zStepLabel_.setEnabled(true);
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
        //autofocus stuff
        for (Component c : autofocusComponentsPanel_.getComponents()) {
            c.setEnabled(useAutofocusCheckBox_.isSelected());
        }
        autofocusInitialPositionSpinner_.setEnabled(autofocusInitialPositionCheckBox_.isSelected());
    }

    private void storeCurrentAcqSettings() {
        if (!storeAcqSettings_) {
            return;
        }
        FixedAreaAcquisitionSettings settings = multiAcqManager_.getAcquisitionSettings(multiAcqSelectedIndex_);
        //saving
        settings.dir_ = globalSavingDirTextField_.getText();
        settings.name_ = savingNameTextField_.getText();
        //time
        settings.timeEnabled_ = timePointsCheckBox_.isSelected();
        if (settings.timeEnabled_) {
            settings.numTimePoints_ = (Integer) numTimePointsSpinner_.getValue();
            settings.timePointInterval_ = (Double) timeIntervalSpinner_.getValue();
            settings.timeIntervalUnit_ = timeIntevalUnitCombo_.getSelectedIndex();
        }
        //space
        settings.tileOverlap_ = (Double) acqOverlapPercentSpinner_.getValue();
        if (checkBox2D_.isSelected()) {
            settings.spaceMode_ = FixedAreaAcquisitionSettings.REGION_2D;
            settings.footprint_ = getFootprintObject(footprint2DComboBox_.getSelectedIndex());
            if (collectionPlaneCheckBox_.isSelected()) {
               settings.collectionPlane_ = surfaceManager_.getSurface(collectionPlaneCombo_.getSelectedIndex());
            } else {
                settings.collectionPlane_ = null;
            }
        } else if (checkBox3D_.isSelected()) {
            settings.zStep_ = (Double) zStepSpinner_.getValue();
            settings.channelsAtEverySlice_ = acqOrderCombo_.getSelectedIndex() == 0;
            if (simpleZStackRadioButton_.isSelected()) {
                settings.spaceMode_ = FixedAreaAcquisitionSettings.SIMPLE_Z_STACK;
                settings.footprint_ = getFootprintObject(simpleZStackFootprintCombo_.getSelectedIndex());
                settings.zStart_ = (Double) zStartSpinner_.getValue();
                settings.zEnd_ = (Double) zEndSpinner_.getValue();
            } else if (volumeBetweenSurfacesRadioButton_.isSelected()) {
                settings.spaceMode_ = FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK;
                settings.topSurface_ = surfaceManager_.getSurface(topSurfaceCombo_.getSelectedIndex());
                settings.bottomSurface_ = surfaceManager_.getSurface(bottomSurfaceCombo_.getSelectedIndex());
                settings.distanceAboveTopSurface_ = (Double) umAboveTopSurfaceSpinner_.getValue();
                settings.distanceBelowBottomSurface_ = (Double) umBelowBottomSurfaceSpinner_.getValue();
                settings.useTopOrBottomFootprint_ = volumeBetweenFootprintCombo_.getSelectedItem().equals("Top surface")
                        ? FixedAreaAcquisitionSettings.FOOTPRINT_FROM_TOP : FixedAreaAcquisitionSettings.FOOTPRINT_FROM_BOTTOM;
            } else if (fixedDistanceFromSurfaceRadioButton_.isSelected()) {
                settings.spaceMode_ = FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK;
                settings.distanceBelowFixedSurface_ = ((Number) distanceBelowFixedSurfaceSpinner_.getValue()).doubleValue();
                settings.distanceAboveFixedSurface_ = ((Number) distanceAboveFixedSurfaceSpinner_.getValue()).doubleValue();
                settings.fixedSurface_ = surfaceManager_.getSurface(fixedDistanceSurfaceComboBox_.getSelectedIndex());
                settings.footprint_ = getFootprintObject(withinDistanceFromFootprintCombo_.getSelectedIndex());  
            }
        } else {
            settings.spaceMode_ = FixedAreaAcquisitionSettings.NO_SPACE;
        }
        //channels
        settings.channelGroup_ = (String) ChannelGroupCombo_.getSelectedItem();
        settings.channels_ = ((SimpleChannelTableModel)channelsTable_.getModel()).getChannels();
              
        //autofocus
        settings.autofocusEnabled_ = useAutofocusCheckBox_.isSelected();
        if (settings.autofocusEnabled_) {
            settings.autofocusChannelName_ = autofocusChannelCombo_.getSelectedItem().toString();
            settings.autofocusMaxDisplacemnet_um_ = (Double) autofocusMaxDisplacementSpinner_.getValue();
            settings.autoFocusZDevice_ = autofocusZDeviceComboBox_.getSelectedItem().toString();
            settings.setInitialAutofocusPosition_ = autofocusInitialPositionCheckBox_.isSelected();
            settings.initialAutofocusPosition_ = (Double) autofocusInitialPositionSpinner_.getValue();
        }

        //2photon
        settings.imageFilterType_ = frameAverageRadioButton_.isSelected() ? FrameIntegrationMethod.FRAME_AVERAGE :
                (rankFilterRadioButton_.isSelected() ? FrameIntegrationMethod.RANK_FILTER : FrameIntegrationMethod.FRAME_SUMMATION );
        settings.rank_ = ((Number) rankSpinner_.getValue()).doubleValue();

        settings.storePreferedValues();
        multipleAcqTable_.repaint();

       if (multiAcqManager_.isRunning()) {
          //signal acquisition settings change for dynamic updating of acquisiitons
          multiAcqManager_.signalAcqSettingsChange();
       } else {
          //estimate time needed for acquisition
          acqDurationEstimator_.calcAcqDuration(getActiveAcquisitionSettings());
       }
    }

    private void populateAcqControls(FixedAreaAcquisitionSettings settings) {
        //don't autostore outdated settings while controls are being populated
        storeAcqSettings_ = false;
        savingNameTextField_.setText(settings.name_);
        //time
        timePointsCheckBox_.setSelected(settings.timeEnabled_);
        numTimePointsSpinner_.setValue(settings.numTimePoints_);
        timeIntervalSpinner_.setValue(settings.timePointInterval_);
        timeIntevalUnitCombo_.setSelectedIndex(settings.timeIntervalUnit_);
        //space           
        acqOrderCombo_.setSelectedIndex(settings.channelsAtEverySlice_ ? 0 : 1);
        checkBox2D_.setSelected(settings.spaceMode_ == FixedAreaAcquisitionSettings.REGION_2D);
        checkBox3D_.setSelected(settings.spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK
                || settings.spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK
                || settings.spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK);
        simpleZStackRadioButton_.setSelected(settings.spaceMode_ == FixedAreaAcquisitionSettings.SIMPLE_Z_STACK);
        volumeBetweenSurfacesRadioButton_.setSelected(settings.spaceMode_ == FixedAreaAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK);
        fixedDistanceFromSurfaceRadioButton_.setSelected(settings.spaceMode_ == FixedAreaAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK);
        zStepSpinner_.setValue(settings.zStep_);
        zStartSpinner_.setValue(settings.zStart_);
        zEndSpinner_.setValue(settings.zEnd_);
        distanceBelowFixedSurfaceSpinner_.setValue(settings.distanceBelowFixedSurface_);
        distanceAboveFixedSurfaceSpinner_.setValue(settings.distanceAboveFixedSurface_);
        acqOverlapPercentSpinner_.setValue(settings.tileOverlap_);
        umAboveTopSurfaceSpinner_.setValue(settings.distanceAboveTopSurface_);
        umBelowBottomSurfaceSpinner_.setValue(settings.distanceBelowBottomSurface_);
        //select surfaces/regions
        simpleZStackFootprintCombo_.setSelectedItem(settings.footprint_);
        topSurfaceCombo_.setSelectedItem(settings.topSurface_);
        bottomSurfaceCombo_.setSelectedItem(settings.bottomSurface_);
        volumeBetweenFootprintCombo_.setSelectedIndex(settings.useTopOrBottomFootprint_);
        fixedDistanceSurfaceComboBox_.setSelectedItem(settings.fixedSurface_);
        footprint2DComboBox_.setSelectedItem(settings.footprint_);
        withinDistanceFromFootprintCombo_.setSelectedItem(settings.footprint_);

        
        //channels
        ChannelGroupCombo_.setSelectedItem(settings.channelGroup_);
        ((SimpleChannelTableModel) channelsTable_.getModel()).setChannelGroup(settings.channelGroup_);
        ((SimpleChannelTableModel) channelsTable_.getModel()).setChannels(settings.channels_);
        
        //autofocus
        useAutofocusCheckBox_.setSelected(settings.autofocusEnabled_);
        autofocusChannelCombo_.setSelectedItem(settings.autofocusChannelName_);
        autofocusMaxDisplacementSpinner_.setValue(settings.autofocusMaxDisplacemnet_um_);
        autofocusZDeviceComboBox_.setSelectedItem(settings.autoFocusZDevice_);
        autofocusInitialPositionCheckBox_.setSelected(settings.setInitialAutofocusPosition_);
        autofocusInitialPositionSpinner_.setValue(settings.initialAutofocusPosition_);

        //2photon specific stuff
        frameAverageRadioButton_.setSelected(settings.imageFilterType_ == FrameIntegrationMethod.FRAME_AVERAGE);
        rankFilterRadioButton_.setSelected(settings.imageFilterType_ == FrameIntegrationMethod.RANK_FILTER);
        frameSummationButton_.setSelected(settings.imageFilterType_ == FrameIntegrationMethod.FRAME_SUMMATION);
        rankSpinner_.setValue(settings.rank_);

        enableAcquisitionComponentsAsNeeded();

        repaint();
        storeAcqSettings_ = true;
    }

    private void addGlobalSettingsListeners() {
        globalSavingDirTextField_.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                settings_.storeSavingDirectory(globalSavingDirTextField_.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                settings_.storeSavingDirectory(globalSavingDirTextField_.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                settings_.storeSavingDirectory(globalSavingDirTextField_.getText());
            }
        });
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
     * Channel offsets must be within 9 of eachother
     */
    public void validateChannelOffsets() {
        int minOffset = 200, maxOffset = -200;
        for (JSpinner s : offsetSpinners_) {
            minOffset = Math.min(((Number)s.getValue()).intValue(), minOffset);
            maxOffset = Math.min(((Number)s.getValue()).intValue(), maxOffset);
        }
        if (Math.abs(minOffset - maxOffset) > 9) {
            for (JSpinner s : offsetSpinners_) {
                s.setValue(Math.min(((Number) s.getValue()).intValue(), minOffset + 9));
            }
        }
        
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
        filterMethodButtonGroup_ = new javax.swing.ButtonGroup();
        exploreFilterMethodButtonGroup_ = new javax.swing.ButtonGroup();
        jLabel11 = new javax.swing.JLabel();
        splitPane_ = new javax.swing.JSplitPane();
        splitPaneTopPanel_ = splitPaneTopPanel_ = new javax.swing.JTabbedPane() {

            @Override
            public void setSize(Dimension size) {
                super.setSize(size);
                System.out.println();
            }

        };
        controlPanelName_ = new javax.swing.JPanel();
        deviceControlScrollPane_ = new javax.swing.JScrollPane();
        deviceControlTable_ = new javax.swing.JTable();
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
        saveButton_ = new javax.swing.JButton();
        loadButton_ = new javax.swing.JButton();
        surfacesPanel_ = new javax.swing.JPanel();
        deleteSelectedSurfaceButton_ = new javax.swing.JButton();
        deleteAllSurfacesButton_ = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        surfacesTable_ = new javax.swing.JTable();
        saveSurfacesButton_ = new javax.swing.JButton();
        loadSurfacesButton_ = new javax.swing.JButton();
        splitPaneBottomPanel_ = new javax.swing.JPanel();
        exploreSavingDirLabel_ = new javax.swing.JLabel();
        globalSavingDirTextField_ = new javax.swing.JTextField();
        exploreSampleLabel_ = new javax.swing.JLabel();
        exploreRankSpinner_ = new javax.swing.JSpinner();
        exploreRankFilterButton_ = new javax.swing.JRadioButton();
        exploreFrameAverageButton_ = new javax.swing.JRadioButton();
        explorePercentLabel_ = new javax.swing.JLabel();
        exploreTileOverlapSpinner_ = new javax.swing.JSpinner();
        exploreOverlapLabel_ = new javax.swing.JLabel();
        exploreChannelGroupCombo_ = new javax.swing.JComboBox();
        channelGroupLabel_ = new javax.swing.JLabel();
        exploreZStepSpinner_ = new javax.swing.JSpinner();
        exploreZStepLabel_ = new javax.swing.JLabel();
        exploreBrowseButton_ = new javax.swing.JButton();
        exploreSavingNameLabel_ = new javax.swing.JLabel();
        exploreSavingNameTextField_ = new javax.swing.JTextField();
        newExploreWindowButton_ = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        acqTabbedPane_ = new javax.swing.JTabbedPane();
        savingTab_ = new javax.swing.JPanel();
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
        umAboveTopSurfaceSpinner_ = new javax.swing.JSpinner();
        umAboveVolBetweenLabel_ = new javax.swing.JLabel();
        umBelowBottomSurfaceSpinner_ = new javax.swing.JSpinner();
        umBelowVolBetweenLabel_ = new javax.swing.JLabel();
        fixedDistanceZPanel_ = new javax.swing.JPanel();
        fixedDistanceFromSurfaceRadioButton_ = new javax.swing.JRadioButton();
        distanceBelowSurfaceLabel_ = new javax.swing.JLabel();
        distanceBelowFixedSurfaceSpinner_ = new javax.swing.JSpinner();
        distanceAboveSurfaceLabel_ = new javax.swing.JLabel();
        distanceAboveFixedSurfaceSpinner_ = new javax.swing.JSpinner();
        umAboveLabel_ = new javax.swing.JLabel();
        umBelowLabel_ = new javax.swing.JLabel();
        fixedSurfaceLabel_ = new javax.swing.JLabel();
        fixedDistanceSurfaceComboBox_ = new javax.swing.JComboBox();
        jLabel12 = new javax.swing.JLabel();
        withinDistanceFromFootprintCombo_ = new javax.swing.JComboBox();
        zStepLabel_ = new javax.swing.JLabel();
        panel2D_ = new javax.swing.JPanel();
        footprin2DLabel_ = new javax.swing.JLabel();
        footprint2DComboBox_ = new javax.swing.JComboBox();
        collectionPlaneCombo_ = new javax.swing.JComboBox();
        collectionPlaneCheckBox_ = new javax.swing.JCheckBox();
        collectionPlaneLabel_ = new javax.swing.JLabel();
        checkBox3D_ = new javax.swing.JCheckBox();
        checkBox2D_ = new javax.swing.JCheckBox();
        zStepSpinner_ = new javax.swing.JSpinner();
        acqTileOverlapLabel_ = new javax.swing.JLabel();
        acqOverlapPercentSpinner_ = new javax.swing.JSpinner();
        tileOverlapPercentLabel_ = new javax.swing.JLabel();
        acqOrderCombo_ = new javax.swing.JComboBox();
        acqOrderLabel_ = new javax.swing.JLabel();
        ChannelsTab_ = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        channelsTable_ = new javax.swing.JTable();
        jLabel3 = new javax.swing.JLabel();
        ChannelGroupCombo_ = new javax.swing.JComboBox();
        covariedSettingsTab_ = new javax.swing.JPanel();
        propertyPairValuesScrollpane_ = new javax.swing.JScrollPane();
        covariantPairValuesTable_ = covariantPairValuesTable_ = new javax.swing.JTable() {
            @Override
            public void editingStopped(ChangeEvent e) {
                //allows selections to persist even though fireTableData changed called
                //after every edit to resort rows
                int row = covariantPairValuesTable_.getSelectedRow();
                super.editingStopped(e);
                if (row != -1) {
                    covariantPairValuesTable_.setRowSelectionInterval(row, row);
                }
            }
        }
        ;
        newParingButton_ = new javax.swing.JButton();
        removePairingButton = new javax.swing.JButton();
        propertyPairingsScrollpane_ = new javax.swing.JScrollPane();
        covariantPairingsTable_ = new javax.swing.JTable();
        savePairingsButton_ = new javax.swing.JButton();
        loadPairingsButton_ = new javax.swing.JButton();
        addCovariedPairingValueButton_ = new javax.swing.JButton();
        deleteCovariedPairingValueButton_ = new javax.swing.JButton();
        jLabel6 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        autofocusTab_l = new javax.swing.JPanel();
        useAutofocusCheckBox_ = new javax.swing.JCheckBox();
        autofocusComponentsPanel_ = new javax.swing.JPanel();
        autofocusZLabel_ = new javax.swing.JLabel();
        autofocusZDeviceComboBox_ = new javax.swing.JComboBox();
        autofocusMaxDisplacementLabel_ = new javax.swing.JLabel();
        autofocusMaxDisplacementSpinner_ = new javax.swing.JSpinner();
        jLabel7 = new javax.swing.JLabel();
        autofocusChannelCombo_ = new javax.swing.JComboBox();
        autofocusInitialPositionSpinner_ = new javax.swing.JSpinner();
        autofocusInitialPositionCheckBox_ = new javax.swing.JCheckBox();
        imageFilteringTab_ = new javax.swing.JPanel();
        frameAverageRadioButton_ = new javax.swing.JRadioButton();
        rankFilterRadioButton_ = new javax.swing.JRadioButton();
        rankSpinner_ = new javax.swing.JSpinner();
        jLabel9 = new javax.swing.JLabel();
        ch0OffsetSpinner_ = new javax.swing.JSpinner();
        jLabel10 = new javax.swing.JLabel();
        offsetsLabel_ = new javax.swing.JLabel();
        ch0OffsetLabel_ = new javax.swing.JLabel();
        ch1OffsetLabel_ = new javax.swing.JLabel();
        ch1OffsetSpinner_ = new javax.swing.JSpinner();
        ch2OffsetLabel_ = new javax.swing.JLabel();
        ch2OffsetSpinner_ = new javax.swing.JSpinner();
        ch3OffsetLabel_ = new javax.swing.JLabel();
        ch3OffsetSpinner_ = new javax.swing.JSpinner();
        ch4OffsetLabel_ = new javax.swing.JLabel();
        ch4OffsetSpinner_ = new javax.swing.JSpinner();
        ch5OffsetLabel_ = new javax.swing.JLabel();
        ch5OffsetSpinner_ = new javax.swing.JSpinner();
        frameSummationButton_ = new javax.swing.JRadioButton();
        runAcqButton_ = new javax.swing.JButton();
        configPropsButton_ = new javax.swing.JButton();
        jButton1 = new javax.swing.JButton();
        createdByHenryLabel_ = new javax.swing.JLabel();
        openDatasetButton_ = new javax.swing.JButton();
        helpButton_ = new javax.swing.JButton();
        userGuideLink_ = new javax.swing.JLabel();
        estDurationLabel_ = new javax.swing.JLabel();
        citeLink_ = new javax.swing.JLabel();

        jLabel11.setText("jLabel11");

        getContentPane().setLayout(new javax.swing.BoxLayout(getContentPane(), javax.swing.BoxLayout.LINE_AXIS));

        splitPane_.setBorder(null);
        splitPane_.setDividerLocation(200);
        splitPane_.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        splitPaneTopPanel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N

        deviceControlScrollPane_.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        deviceControlScrollPane_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N

        DeviceControlTableModel model = new DeviceControlTableModel(prefs_);
        deviceControlTable_.setAutoCreateColumnsFromModel(false);
        deviceControlTable_.setModel(model);
        deviceControlTable_.addColumn(new TableColumn(0, 200, new DefaultTableCellRenderer(), null));
        deviceControlTable_.addColumn(new TableColumn(1, 200, new PropertyValueCellRenderer(false), new PropertyValueCellEditor()));
        deviceControlTable_.setTableHeader(null);
        deviceControlTable_.setCellSelectionEnabled(false);
        deviceControlTable_.setModel(model);
        deviceControlScrollPane_.setViewportView(deviceControlTable_);

        javax.swing.GroupLayout controlPanelName_Layout = new javax.swing.GroupLayout(controlPanelName_);
        controlPanelName_.setLayout(controlPanelName_Layout);
        controlPanelName_Layout.setHorizontalGroup(
            controlPanelName_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(deviceControlScrollPane_, javax.swing.GroupLayout.DEFAULT_SIZE, 962, Short.MAX_VALUE)
        );
        controlPanelName_Layout.setVerticalGroup(
            controlPanelName_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(deviceControlScrollPane_, javax.swing.GroupLayout.DEFAULT_SIZE, 151, Short.MAX_VALUE)
        );

        splitPaneTopPanel_.addTab("Device status/control", controlPanelName_);

        multipleAcqScrollPane_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N

        multipleAcqTable_.setModel(new MultipleAcquisitionTableModel(multiAcqManager_,this));
        multipleAcqTable_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        multipleAcqScrollPane_.setViewportView(multipleAcqTable_);

        addAcqButton_.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        addAcqButton_.setText("+");
        addAcqButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addAcqButton_ActionPerformed(evt);
            }
        });

        removeAcqButton_.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        removeAcqButton_.setText("-");
        removeAcqButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeAcqButton_ActionPerformed(evt);
            }
        });

        moveAcqUpButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        moveAcqUpButton_.setText("Move up");
        moveAcqUpButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveAcqUpButton_ActionPerformed(evt);
            }
        });

        moveAcqDownButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        moveAcqDownButton_.setText("Move down");
        moveAcqDownButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveAcqDownButton_ActionPerformed(evt);
            }
        });

        runMultipleAcquisitionsButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        runMultipleAcquisitionsButton_.setText("Run all");
        runMultipleAcquisitionsButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runMultipleAcquisitionsButton_ActionPerformed(evt);
            }
        });

        intereaveButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        intereaveButton_.setText("In parallel");
        intereaveButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                intereaveButton_ActionPerformed(evt);
            }
        });

        deinterleaveButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        deinterleaveButton_.setText("In series");
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
                .addComponent(moveAcqUpButton_)
                .addGap(3, 3, 3)
                .addComponent(moveAcqDownButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 106, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(intereaveButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deinterleaveButton_)
                .addGap(67, 67, 67)
                .addComponent(runMultipleAcquisitionsButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
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
                .addComponent(multipleAcqScrollPane_, javax.swing.GroupLayout.DEFAULT_SIZE, 104, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        splitPaneTopPanel_.addTab("Setup multiple acquisitions", multipleAcquisitionsPanel);

        gridTable_.setModel(regionManager_.createGridTableModel());
        gridTable_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane2.setViewportView(gridTable_);

        deleteSelectedRegionButton_.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        deleteSelectedRegionButton_.setText("-");
        deleteSelectedRegionButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteSelectedRegionButton_ActionPerformed(evt);
            }
        });

        deleteAllRegionsButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        deleteAllRegionsButton_.setText("Delete all");
        deleteAllRegionsButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteAllRegionsButton_ActionPerformed(evt);
            }
        });

        saveButton_.setText("Save");
        saveButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveButton_ActionPerformed(evt);
            }
        });

        loadButton_.setText("Load");
        loadButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadButton_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout gridsPanel_Layout = new javax.swing.GroupLayout(gridsPanel_);
        gridsPanel_.setLayout(gridsPanel_Layout);
        gridsPanel_Layout.setHorizontalGroup(
            gridsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 962, Short.MAX_VALUE)
            .addGroup(gridsPanel_Layout.createSequentialGroup()
                .addGap(78, 78, 78)
                .addComponent(saveButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(loadButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 254, Short.MAX_VALUE)
                .addComponent(deleteSelectedRegionButton_)
                .addGap(41, 41, 41)
                .addComponent(deleteAllRegionsButton_)
                .addGap(347, 347, 347))
        );
        gridsPanel_Layout.setVerticalGroup(
            gridsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(gridsPanel_Layout.createSequentialGroup()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 115, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(gridsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(deleteSelectedRegionButton_)
                    .addComponent(deleteAllRegionsButton_)
                    .addComponent(saveButton_)
                    .addComponent(loadButton_)))
        );

        splitPaneTopPanel_.addTab("Grids", gridsPanel_);

        deleteSelectedSurfaceButton_.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        deleteSelectedSurfaceButton_.setText("-");
        deleteSelectedSurfaceButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteSelectedSurfaceButton_ActionPerformed(evt);
            }
        });

        deleteAllSurfacesButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        deleteAllSurfacesButton_.setText("Delete all");
        deleteAllSurfacesButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteAllSurfacesButton_ActionPerformed(evt);
            }
        });

        jScrollPane3.setFocusable(false);
        jScrollPane3.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N

        surfacesTable_.setModel(surfaceManager_.createSurfaceTableModel());
        surfacesTable_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane3.setViewportView(surfacesTable_);

        saveSurfacesButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        saveSurfacesButton_.setText("Save");
        saveSurfacesButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveSurfacesButton_ActionPerformed(evt);
            }
        });

        loadSurfacesButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        loadSurfacesButton_.setText("Load");
        loadSurfacesButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadSurfacesButton_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout surfacesPanel_Layout = new javax.swing.GroupLayout(surfacesPanel_);
        surfacesPanel_.setLayout(surfacesPanel_Layout);
        surfacesPanel_Layout.setHorizontalGroup(
            surfacesPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(surfacesPanel_Layout.createSequentialGroup()
                .addGap(88, 88, 88)
                .addComponent(saveSurfacesButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(loadSurfacesButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(deleteSelectedSurfaceButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(deleteAllSurfacesButton_)
                .addGap(362, 362, 362))
            .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 962, Short.MAX_VALUE)
        );
        surfacesPanel_Layout.setVerticalGroup(
            surfacesPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(surfacesPanel_Layout.createSequentialGroup()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 116, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(surfacesPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(deleteAllSurfacesButton_)
                    .addComponent(deleteSelectedSurfaceButton_)
                    .addComponent(saveSurfacesButton_)
                    .addComponent(loadSurfacesButton_)))
        );

        splitPaneTopPanel_.addTab("Surfaces", surfacesPanel_);

        splitPane_.setTopComponent(splitPaneTopPanel_);

        exploreSavingDirLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        exploreSavingDirLabel_.setText("Saving directory: ");

        globalSavingDirTextField_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        globalSavingDirTextField_.setText("jTextField1");

        exploreSampleLabel_.setFont(new java.awt.Font("Tahoma", 1, 16)); // NOI18N
        exploreSampleLabel_.setText("Explore sample");

        exploreRankSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        exploreRankSpinner_.setModel(new javax.swing.SpinnerNumberModel(0.95d, 0.0d, 1.0d, 0.01d));

        exploreFilterMethodButtonGroup_.add(exploreRankFilterButton_);
        exploreRankFilterButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        exploreRankFilterButton_.setText("Rank filter");
        exploreRankFilterButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exploreRankFilterButton_ActionPerformed(evt);
            }
        });

        exploreFilterMethodButtonGroup_.add(exploreFrameAverageButton_);
        exploreFrameAverageButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        exploreFrameAverageButton_.setSelected(true);
        exploreFrameAverageButton_.setText("Frame average");

        explorePercentLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        explorePercentLabel_.setText("%");

        exploreTileOverlapSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        exploreTileOverlapSpinner_.setModel(new javax.swing.SpinnerNumberModel(0.0d, 0.0d, 99.0d, 1.0d));

        exploreOverlapLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        exploreOverlapLabel_.setText("Tile overlap:");

        exploreChannelGroupCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        exploreChannelGroupCombo_.setModel(new ChannelComboBoxModel());

        channelGroupLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        channelGroupLabel_.setText("Channel Group: ");

        exploreZStepSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        exploreZStepSpinner_.setModel(new javax.swing.SpinnerNumberModel(1.0d, null, null, 1.0d));
        exploreZStepSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                exploreZStepSpinner_StateChanged(evt);
            }
        });

        exploreZStepLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        exploreZStepLabel_.setText("<html>Z-step (&mu;m):</html>");

        exploreBrowseButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        exploreBrowseButton_.setText("Browse");
        exploreBrowseButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exploreBrowseButton_ActionPerformed(evt);
            }
        });

        exploreSavingNameLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        exploreSavingNameLabel_.setText("Saving name: ");

        exploreSavingNameTextField_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        exploreSavingNameTextField_.setText("jTextField2");
        exploreSavingNameTextField_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exploreSavingNameTextField_ActionPerformed(evt);
            }
        });

        newExploreWindowButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        newExploreWindowButton_.setText("Explore!");
        newExploreWindowButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newExploreWindowButton_ActionPerformed(evt);
            }
        });

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 16)); // NOI18N
        jLabel1.setText("Acquisition Settings");

        acqTabbedPane_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N

        savingNameLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        savingNameLabel_.setText("Saving name: ");

        savingNameTextField_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        savingNameTextField_.setText("jTextField2");

        javax.swing.GroupLayout savingTab_Layout = new javax.swing.GroupLayout(savingTab_);
        savingTab_.setLayout(savingTab_Layout);
        savingTab_Layout.setHorizontalGroup(
            savingTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(savingTab_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(savingTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(savingNameLabel_)
                    .addGroup(savingTab_Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(savingNameTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, 686, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(260, Short.MAX_VALUE))
        );
        savingTab_Layout.setVerticalGroup(
            savingTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(savingTab_Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(savingNameLabel_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(savingNameTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(298, Short.MAX_VALUE))
        );

        acqTabbedPane_.addTab("Saving", savingTab_);

        timePointsPanel_.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        timeIntevalUnitCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        timeIntevalUnitCombo_.setModel(new DefaultComboBoxModel(new String[]{"ms", "s", "min"}));
        timeIntevalUnitCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                timeIntevalUnitCombo_ActionPerformed(evt);
            }
        });

        timeIntervalLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        timeIntervalLabel_.setText("Interval");

        numTimePointsLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        numTimePointsLabel_.setText("Number");

        numTimePointsSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        numTimePointsSpinner_.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
        numTimePointsSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                numTimePointsSpinner_StateChanged(evt);
            }
        });

        timeIntervalSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        timeIntervalSpinner_.setModel(new javax.swing.SpinnerNumberModel(0.0d, 0.0d, null, 1.0d));
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

        timePointsCheckBox_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
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
                .addContainerGap(723, Short.MAX_VALUE))
        );
        timePointsTab_Layout.setVerticalGroup(
            timePointsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(timePointsTab_Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(timePointsCheckBox_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(timePointsPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(231, Short.MAX_VALUE))
        );

        for (Component c : timePointsPanel_.getComponents()) {
            c.setEnabled(false);
        }

        acqTabbedPane_.addTab("Time", timePointsTab_);

        simpleZPanel_.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        zStackModeButtonGroup_.add(simpleZStackRadioButton_);
        simpleZStackRadioButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        simpleZStackRadioButton_.setText("Simple Z stack");
        simpleZStackRadioButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                simpleZStackRadioButton_ActionPerformed(evt);
            }
        });

        zStartLabel.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        zStartLabel.setText("<html>Z-start (&mu;m)</html>");

        zEndLabel.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        zEndLabel.setText("<html>Z-end (&mu;m)</html>");

        jLabel2.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel2.setText("Surface/Grid XY footprint:");

        simpleZStackFootprintCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        simpleZStackFootprintCombo_.setModel(createSurfaceAndRegionComboBoxModel(true,true));
        simpleZStackFootprintCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                simpleZStackFootprintCombo_ActionPerformed(evt);
            }
        });

        zStartSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        zStartSpinner_.setModel(new javax.swing.SpinnerNumberModel(0.0d, null, null, 1.0d));
        zStartSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                zStartSpinner_StateChanged(evt);
            }
        });

        zEndSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        zEndSpinner_.setModel(new javax.swing.SpinnerNumberModel(0.0d, null, null, 1.0d));
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
                    .addComponent(simpleZStackFootprintCombo_, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(simpleZPanel_Layout.createSequentialGroup()
                        .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(simpleZStackRadioButton_)
                            .addComponent(jLabel2))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(simpleZPanel_Layout.createSequentialGroup()
                        .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(zStartLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(zEndLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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
                    .addComponent(zStartLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(zStartSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(zEndLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
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
        volumeBetweenSurfacesRadioButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        volumeBetweenSurfacesRadioButton_.setText("Volume between two surfaces");
        volumeBetweenSurfacesRadioButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                volumeBetweenSurfacesRadioButton_ActionPerformed(evt);
            }
        });

        topSurfaceLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        topSurfaceLabel_.setText("Z-start");

        bottomSurfaceLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        bottomSurfaceLabel_.setText("Z-end");

        topSurfaceCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        topSurfaceCombo_.setModel(createSurfaceAndRegionComboBoxModel(true,false));
        topSurfaceCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                topSurfaceCombo_ActionPerformed(evt);
            }
        });

        bottomSurfaceCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        bottomSurfaceCombo_.setModel(createSurfaceAndRegionComboBoxModel(true,false));
        bottomSurfaceCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bottomSurfaceCombo_ActionPerformed(evt);
            }
        });

        jLabel5.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel5.setText("XY footprint:");

        volumeBetweenFootprintCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        volumeBetweenFootprintCombo_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Top surface", "Bottom surface" }));
        volumeBetweenFootprintCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                volumeBetweenFootprintCombo_ActionPerformed(evt);
            }
        });

        umAboveTopSurfaceSpinner_.setModel(new javax.swing.SpinnerNumberModel(0.0d, 0.0d, null, 1.0d));
        umAboveTopSurfaceSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                umAboveTopSurfaceSpinner_StateChanged(evt);
            }
        });

        umAboveVolBetweenLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        umAboveVolBetweenLabel_.setText("<html>&mu;m above</html>");

        umBelowBottomSurfaceSpinner_.setModel(new javax.swing.SpinnerNumberModel(0.0d, 0.0d, null, 1.0d));
        umBelowBottomSurfaceSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                umBelowBottomSurfaceSpinner_StateChanged(evt);
            }
        });

        umBelowVolBetweenLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        umBelowVolBetweenLabel_.setText("<html>&mu;m below</html>");

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
                        .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(umAboveTopSurfaceSpinner_, javax.swing.GroupLayout.DEFAULT_SIZE, 53, Short.MAX_VALUE)
                            .addComponent(umBelowBottomSurfaceSpinner_))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(umAboveVolBetweenLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(umBelowVolBetweenLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(topSurfaceCombo_, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(bottomSurfaceCombo_, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(volumeBetweenZPanel_Layout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(volumeBetweenFootprintCombo_, 0, 158, Short.MAX_VALUE)))
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
                    .addComponent(topSurfaceCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(umAboveTopSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(umAboveVolBetweenLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bottomSurfaceLabel_)
                    .addComponent(bottomSurfaceCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(umBelowBottomSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(umBelowVolBetweenLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(volumeBetweenFootprintCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(22, Short.MAX_VALUE))
        );

        fixedDistanceZPanel_.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        zStackModeButtonGroup_.add(fixedDistanceFromSurfaceRadioButton_);
        fixedDistanceFromSurfaceRadioButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        fixedDistanceFromSurfaceRadioButton_.setLabel("Within distance from surface");
        fixedDistanceFromSurfaceRadioButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixedDistanceFromSurfaceRadioButton_ActionPerformed(evt);
            }
        });

        distanceBelowSurfaceLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        distanceBelowSurfaceLabel_.setText("Z-end");

        distanceBelowFixedSurfaceSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        distanceBelowFixedSurfaceSpinner_.setModel(new javax.swing.SpinnerNumberModel(0.0d, 0.0d, null, 0.001d));
        distanceBelowFixedSurfaceSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                distanceBelowFixedSurfaceSpinner_StateChanged(evt);
            }
        });

        distanceAboveSurfaceLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        distanceAboveSurfaceLabel_.setText("Z-start");

        distanceAboveFixedSurfaceSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        distanceAboveFixedSurfaceSpinner_.setModel(new javax.swing.SpinnerNumberModel(0.0d, 0.0d, null, 0.001d));
        distanceAboveFixedSurfaceSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                distanceAboveFixedSurfaceSpinner_StateChanged(evt);
            }
        });

        umAboveLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        umAboveLabel_.setText("<html>&mu;m above</html>");

        umBelowLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        umBelowLabel_.setText("<html>&mu;m below</html>");

        fixedSurfaceLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        fixedSurfaceLabel_.setText("Surface: ");

        fixedDistanceSurfaceComboBox_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        fixedDistanceSurfaceComboBox_.setModel(createSurfaceAndRegionComboBoxModel(true,false));
        fixedDistanceSurfaceComboBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fixedDistanceSurfaceComboBox_ActionPerformed(evt);
            }
        });

        jLabel12.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel12.setText("XY footprint:");

        withinDistanceFromFootprintCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        withinDistanceFromFootprintCombo_.setModel(createSurfaceAndRegionComboBoxModel(true,true));
        withinDistanceFromFootprintCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                withinDistanceFromFootprintCombo_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout fixedDistanceZPanel_Layout = new javax.swing.GroupLayout(fixedDistanceZPanel_);
        fixedDistanceZPanel_.setLayout(fixedDistanceZPanel_Layout);
        fixedDistanceZPanel_Layout.setHorizontalGroup(
            fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                        .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                                .addComponent(distanceAboveSurfaceLabel_)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(distanceAboveFixedSurfaceSpinner_)
                                .addGap(2, 2, 2))
                            .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                                .addGap(3, 3, 3)
                                .addComponent(distanceBelowSurfaceLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(distanceBelowFixedSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 81, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(umBelowLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(umAboveLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(21, 21, 21))
                    .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                        .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(fixedDistanceFromSurfaceRadioButton_)
                            .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                                .addComponent(fixedSurfaceLabel_)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(fixedDistanceSurfaceComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(7, 7, 7))
                    .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                        .addComponent(jLabel12)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(withinDistanceFromFootprintCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 126, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))))
        );
        fixedDistanceZPanel_Layout.setVerticalGroup(
            fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(fixedDistanceFromSurfaceRadioButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(distanceAboveSurfaceLabel_)
                    .addComponent(distanceAboveFixedSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(umAboveLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(3, 3, 3)
                .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(distanceBelowSurfaceLabel_)
                    .addComponent(distanceBelowFixedSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(umBelowLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(fixedDistanceSurfaceComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(fixedSurfaceLabel_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel12)
                    .addComponent(withinDistanceFromFootprintCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        addTextEditListener(distanceBelowFixedSurfaceSpinner_);
        addTextEditListener(distanceAboveFixedSurfaceSpinner_);

        zStepLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        zStepLabel_.setText("<html>Z-step (&mu;m):</html>");

        panel2D_.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        footprin2DLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        footprin2DLabel_.setText("Surface/Grid footprint:");

        footprint2DComboBox_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        footprint2DComboBox_.setModel(createSurfaceAndRegionComboBoxModel(true,true));
        footprint2DComboBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                footprint2DComboBox_ActionPerformed(evt);
            }
        });

        collectionPlaneCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        collectionPlaneCombo_.setModel(createSurfaceAndRegionComboBoxModel(true,false));
        collectionPlaneCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                collectionPlaneCombo_ActionPerformed(evt);
            }
        });

        collectionPlaneCheckBox_.setText("Use focus surface");
        collectionPlaneCheckBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                collectionPlaneCheckBox_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout panel2D_Layout = new javax.swing.GroupLayout(panel2D_);
        panel2D_.setLayout(panel2D_Layout);
        panel2D_Layout.setHorizontalGroup(
            panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel2D_Layout.createSequentialGroup()
                .addComponent(collectionPlaneCheckBox_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(collectionPlaneLabel_)
                .addGap(18, 18, 18)
                .addComponent(collectionPlaneCombo_, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
            .addGroup(panel2D_Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(footprin2DLabel_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(footprint2DComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 241, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(92, Short.MAX_VALUE))
        );
        panel2D_Layout.setVerticalGroup(
            panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel2D_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(footprin2DLabel_)
                    .addComponent(footprint2DComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(collectionPlaneCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(collectionPlaneLabel_)
                        .addComponent(collectionPlaneCheckBox_)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        checkBox3D_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        checkBox3D_.setText("3D");
        checkBox3D_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBox3D_ActionPerformed(evt);
            }
        });

        checkBox2D_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        checkBox2D_.setText("2D");
        checkBox2D_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBox2D_ActionPerformed(evt);
            }
        });

        zStepSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        zStepSpinner_.setModel(new javax.swing.SpinnerNumberModel(1.0d, null, null, 1.0d));
        zStepSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                zStepSpinner_StateChanged(evt);
            }
        });

        acqTileOverlapLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        acqTileOverlapLabel_.setText("Tile overlap:");

        acqOverlapPercentSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        acqOverlapPercentSpinner_.setModel(new javax.swing.SpinnerNumberModel(5.0d, 0.0d, 99.0d, 1.0d));
        acqOverlapPercentSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                acqOverlapPercentSpinner_StateChanged(evt);
            }
        });

        tileOverlapPercentLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        tileOverlapPercentLabel_.setText("%");

        acqOrderCombo_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Channels at each Z slice", "Z stacks for each channel" }));
        acqOrderCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                acqOrderCombo_ActionPerformed(evt);
            }
        });

        acqOrderLabel_.setText("Order:");

        javax.swing.GroupLayout spaceTab_Layout = new javax.swing.GroupLayout(spaceTab_);
        spaceTab_.setLayout(spaceTab_Layout);
        spaceTab_Layout.setHorizontalGroup(
            spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(spaceTab_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(spaceTab_Layout.createSequentialGroup()
                        .addComponent(checkBox3D_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(zStepLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(zStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(acqTileOverlapLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(acqOverlapPercentSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tileOverlapPercentLabel_)
                        .addGap(18, 18, 18)
                        .addComponent(acqOrderLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(acqOrderCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 207, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(spaceTab_Layout.createSequentialGroup()
                        .addComponent(simpleZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(volumeBetweenZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(fixedDistanceZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(spaceTab_Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(checkBox2D_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(panel2D_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(255, Short.MAX_VALUE))
        );
        spaceTab_Layout.setVerticalGroup(
            spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(spaceTab_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(checkBox3D_)
                    .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(zStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(zStepLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(acqTileOverlapLabel_)
                        .addComponent(acqOverlapPercentSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(tileOverlapPercentLabel_)
                        .addComponent(acqOrderCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(acqOrderLabel_)))
                .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(spaceTab_Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(simpleZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(spaceTab_Layout.createSequentialGroup()
                        .addGap(11, 11, 11)
                        .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(fixedDistanceZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(volumeBetweenZPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(spaceTab_Layout.createSequentialGroup()
                        .addGap(5, 5, 5)
                        .addComponent(checkBox2D_))
                    .addComponent(panel2D_, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(66, Short.MAX_VALUE))
        );

        for (Component c : simpleZPanel_.getComponents()) {
            c.setEnabled(false);
        }
        for (Component c : volumeBetweenZPanel_.getComponents()) {
            c.setEnabled(false);
        }
        addTextEditListener(zStepSpinner_);

        acqTabbedPane_.addTab("Space", spaceTab_);

        jScrollPane1.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N

        channelsTable_.setModel(new SimpleChannelTableModel());
        jScrollPane1.setViewportView(channelsTable_);

        jLabel3.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel3.setText("Channel group:");

        ChannelGroupCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        ChannelGroupCombo_.setModel(new ChannelComboBoxModel());
        ChannelGroupCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ChannelGroupCombo_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout ChannelsTab_Layout = new javax.swing.GroupLayout(ChannelsTab_);
        ChannelsTab_.setLayout(ChannelsTab_Layout);
        ChannelsTab_Layout.setHorizontalGroup(
            ChannelsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 962, Short.MAX_VALUE)
            .addGroup(ChannelsTab_Layout.createSequentialGroup()
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ChannelGroupCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 171, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        ChannelsTab_Layout.setVerticalGroup(
            ChannelsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, ChannelsTab_Layout.createSequentialGroup()
                .addGroup(ChannelsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(ChannelGroupCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 326, Short.MAX_VALUE))
        );

        acqTabbedPane_.addTab("Channels", ChannelsTab_);

        CovariantPairValuesTableModel cpvtModel = new CovariantPairValuesTableModel();
        covariantPairValuesTable_.setAutoCreateColumnsFromModel(false);
        covariantPairValuesTable_.addColumn(new TableColumn(0, 100, new CovariantValueCellRenderer(), new CovariantValueCellEditor()));
        covariantPairValuesTable_.addColumn(new TableColumn(1, 100, new CovariantValueCellRenderer(), new CovariantValueCellEditor()));
        covariantPairValuesTable_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        covariantPairValuesTable_.setModel(cpvtModel);
        covariantPairValuesTable_.setCellSelectionEnabled(true);
        covariantPairValuesTable_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        covariantPairValuesTable_.setSelectionModel(new DefaultListSelectionModel () {
            @Override
            public void clearSelection() {
                super.clearSelection();
            }
        });
        propertyPairValuesScrollpane_.setViewportView(covariantPairValuesTable_);

        newParingButton_.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        newParingButton_.setText("+");
        newParingButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newParingButton_ActionPerformed(evt);
            }
        });

        removePairingButton.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        removePairingButton.setText("-");
        removePairingButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removePairingButtonActionPerformed(evt);
            }
        });

        covariantPairingsTable_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        covariantPairingsTable_.setModel(new org.micromanager.plugins.magellan.propsandcovariants.CovariantPairingsTableModel());
        propertyPairingsScrollpane_.setViewportView(covariantPairingsTable_);

        savePairingsButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        savePairingsButton_.setText("Save");
        savePairingsButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                savePairingsButton_ActionPerformed(evt);
            }
        });

        loadPairingsButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        loadPairingsButton_.setText("Load");
        loadPairingsButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadPairingsButton_ActionPerformed(evt);
            }
        });

        addCovariedPairingValueButton_.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        addCovariedPairingValueButton_.setText("+");
        addCovariedPairingValueButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addCovariedPairingValueButton_ActionPerformed(evt);
            }
        });

        deleteCovariedPairingValueButton_.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        deleteCovariedPairingValueButton_.setText("-");
        deleteCovariedPairingValueButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteCovariedPairingValueButton_ActionPerformed(evt);
            }
        });

        jLabel6.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel6.setText("Covariant pairings");

        jLabel8.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel8.setText("Interpolation points");

        javax.swing.GroupLayout covariedSettingsTab_Layout = new javax.swing.GroupLayout(covariedSettingsTab_);
        covariedSettingsTab_.setLayout(covariedSettingsTab_Layout);
        covariedSettingsTab_Layout.setHorizontalGroup(
            covariedSettingsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, covariedSettingsTab_Layout.createSequentialGroup()
                .addGroup(covariedSettingsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(covariedSettingsTab_Layout.createSequentialGroup()
                        .addGap(15, 15, 15)
                        .addComponent(jLabel6)
                        .addGap(244, 413, Short.MAX_VALUE))
                    .addComponent(propertyPairingsScrollpane_, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(8, 8, 8)
                .addGroup(covariedSettingsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(propertyPairValuesScrollpane_, javax.swing.GroupLayout.PREFERRED_SIZE, 401, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8))
                .addGap(16, 16, 16))
            .addGroup(covariedSettingsTab_Layout.createSequentialGroup()
                .addGap(38, 38, 38)
                .addComponent(newParingButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(removePairingButton)
                .addGap(245, 245, 245)
                .addComponent(savePairingsButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(loadPairingsButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(addCovariedPairingValueButton_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deleteCovariedPairingValueButton_)
                .addContainerGap())
        );
        covariedSettingsTab_Layout.setVerticalGroup(
            covariedSettingsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(covariedSettingsTab_Layout.createSequentialGroup()
                .addGroup(covariedSettingsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(jLabel8))
                .addGap(8, 8, 8)
                .addGroup(covariedSettingsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(propertyPairValuesScrollpane_, javax.swing.GroupLayout.DEFAULT_SIZE, 288, Short.MAX_VALUE)
                    .addComponent(propertyPairingsScrollpane_, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
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

        useAutofocusCheckBox_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        useAutofocusCheckBox_.setText("Activate cross-correlation based drift compensation");
        useAutofocusCheckBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useAutofocusCheckBox_ActionPerformed(evt);
            }
        });

        autofocusZLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        autofocusZLabel_.setText("Drift compensation Z device: ");

        StrVector zVec = Magellan.getCore().getLoadedDevicesOfType(mmcorej.DeviceType.StageDevice);
        String[] zNames = new String[(int)zVec.size()];
        for (int i = 0; i < zNames.length; i++) {
            zNames[i] = zVec.get(i);
        }
        ComboBoxModel afzModel = new DefaultComboBoxModel(zNames);
        autofocusZDeviceComboBox_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        autofocusZDeviceComboBox_.setModel(afzModel);
        autofocusZDeviceComboBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autofocusZDeviceComboBox_ActionPerformed(evt);
            }
        });

        autofocusMaxDisplacementLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        autofocusMaxDisplacementLabel_.setText("<html>Maxmimum displacement (&mu;m): </html>");

        autofocusMaxDisplacementSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        autofocusMaxDisplacementSpinner_.setModel(new javax.swing.SpinnerNumberModel(1.0d, 0.0d, null, 1.0d));
        autofocusMaxDisplacementSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                autofocusMaxDisplacementSpinner_StateChanged(evt);
            }
        });

        jLabel7.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel7.setText("Use channel:");

        AutofocusChannelComboModel afucModel = new AutofocusChannelComboModel((SimpleChannelTableModel) channelsTable_.getModel());
        autofocusChannelCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        autofocusChannelCombo_.setModel(afucModel);
        autofocusChannelCombo_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autofocusChannelCombo_ActionPerformed(evt);
            }
        });

        autofocusInitialPositionSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        autofocusInitialPositionSpinner_.setModel(new javax.swing.SpinnerNumberModel(1.0d, null, null, 1.0d));
        autofocusInitialPositionSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                autofocusInitialPositionSpinner_StateChanged(evt);
            }
        });

        autofocusInitialPositionCheckBox_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        autofocusInitialPositionCheckBox_.setText("Set initial position");
        autofocusInitialPositionCheckBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autofocusInitialPositionCheckBox_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout autofocusComponentsPanel_Layout = new javax.swing.GroupLayout(autofocusComponentsPanel_);
        autofocusComponentsPanel_.setLayout(autofocusComponentsPanel_Layout);
        autofocusComponentsPanel_Layout.setHorizontalGroup(
            autofocusComponentsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(autofocusComponentsPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(autofocusComponentsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(autofocusComponentsPanel_Layout.createSequentialGroup()
                        .addComponent(jLabel7)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(autofocusChannelCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 115, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(autofocusComponentsPanel_Layout.createSequentialGroup()
                        .addComponent(autofocusMaxDisplacementLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(autofocusMaxDisplacementSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(autofocusComponentsPanel_Layout.createSequentialGroup()
                        .addComponent(autofocusZLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(autofocusZDeviceComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(autofocusComponentsPanel_Layout.createSequentialGroup()
                        .addComponent(autofocusInitialPositionCheckBox_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(autofocusInitialPositionSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 91, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        autofocusComponentsPanel_Layout.setVerticalGroup(
            autofocusComponentsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, autofocusComponentsPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(autofocusComponentsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(autofocusChannelCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(autofocusComponentsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(autofocusMaxDisplacementSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(autofocusMaxDisplacementLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(autofocusComponentsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(autofocusZLabel_)
                    .addComponent(autofocusZDeviceComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(autofocusComponentsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(autofocusInitialPositionSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(autofocusInitialPositionCheckBox_))
                .addContainerGap(27, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout autofocusTab_lLayout = new javax.swing.GroupLayout(autofocusTab_l);
        autofocusTab_l.setLayout(autofocusTab_lLayout);
        autofocusTab_lLayout.setHorizontalGroup(
            autofocusTab_lLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(autofocusTab_lLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(autofocusTab_lLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(useAutofocusCheckBox_)
                    .addComponent(autofocusComponentsPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(615, Short.MAX_VALUE))
        );
        autofocusTab_lLayout.setVerticalGroup(
            autofocusTab_lLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(autofocusTab_lLayout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addComponent(useAutofocusCheckBox_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(autofocusComponentsPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(141, Short.MAX_VALUE))
        );

        acqTabbedPane_.addTab("Drift Compensation", autofocusTab_l);

        filterMethodButtonGroup_.add(frameAverageRadioButton_);
        frameAverageRadioButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        frameAverageRadioButton_.setSelected(true);
        frameAverageRadioButton_.setText("Frame average");
        frameAverageRadioButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                frameAverageRadioButton_ActionPerformed(evt);
            }
        });

        filterMethodButtonGroup_.add(rankFilterRadioButton_);
        rankFilterRadioButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        rankFilterRadioButton_.setText("Rank filter");
        rankFilterRadioButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rankFilterRadioButton_ActionPerformed(evt);
            }
        });

        rankSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        rankSpinner_.setModel(new javax.swing.SpinnerNumberModel(0.95d, 0.0d, 1.0d, 0.01d));
        rankSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                rankSpinner_StateChanged(evt);
            }
        });

        jLabel9.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jLabel9.setText("Rank:");

        ch0OffsetSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        ch0OffsetSpinner_.setModel(new javax.swing.SpinnerNumberModel());
        ch0OffsetSpinner_.setValue(GlobalSettings.getInstance().getChannelOffset(0));
        ch0OffsetSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                ch0OffsetSpinner_StateChanged(evt);
            }
        });

        jLabel10.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel10.setText("Image construction method");

        offsetsLabel_.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        offsetsLabel_.setText("Offsets");

        ch0OffsetLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        ch0OffsetLabel_.setText("Ch0");

        ch1OffsetLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        ch1OffsetLabel_.setText("Ch1");

        ch1OffsetSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        ch1OffsetSpinner_.setModel(new javax.swing.SpinnerNumberModel());
        ch1OffsetSpinner_.setValue(GlobalSettings.getInstance().getChannelOffset(1));
        ch1OffsetSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                ch1OffsetSpinner_StateChanged(evt);
            }
        });

        ch2OffsetLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        ch2OffsetLabel_.setText("Ch2");

        ch2OffsetSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        ch2OffsetSpinner_.setModel(new javax.swing.SpinnerNumberModel());
        ch2OffsetSpinner_.setValue(GlobalSettings.getInstance().getChannelOffset(2));
        ch2OffsetSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                ch2OffsetSpinner_StateChanged(evt);
            }
        });

        ch3OffsetLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        ch3OffsetLabel_.setText("Ch3");

        ch3OffsetSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        ch3OffsetSpinner_.setModel(new javax.swing.SpinnerNumberModel());
        ch3OffsetSpinner_.setValue(GlobalSettings.getInstance().getChannelOffset(3));
        ch3OffsetSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                ch3OffsetSpinner_StateChanged(evt);
            }
        });

        ch4OffsetLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        ch4OffsetLabel_.setText("Ch4");

        ch4OffsetSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        ch4OffsetSpinner_.setModel(new javax.swing.SpinnerNumberModel());
        ch4OffsetSpinner_.setValue(GlobalSettings.getInstance().getChannelOffset(4));
        ch4OffsetSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                ch4OffsetSpinner_StateChanged(evt);
            }
        });

        ch5OffsetLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        ch5OffsetLabel_.setText("Ch5");

        ch5OffsetSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        ch5OffsetSpinner_.setModel(new javax.swing.SpinnerNumberModel());
        ch5OffsetSpinner_.setValue(GlobalSettings.getInstance().getChannelOffset(5));
        ch5OffsetSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                ch5OffsetSpinner_StateChanged(evt);
            }
        });

        filterMethodButtonGroup_.add(frameSummationButton_);
        frameSummationButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        frameSummationButton_.setText("Frame summation");
        frameSummationButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                frameSummationButton_ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout imageFilteringTab_Layout = new javax.swing.GroupLayout(imageFilteringTab_);
        imageFilteringTab_.setLayout(imageFilteringTab_Layout);
        imageFilteringTab_Layout.setHorizontalGroup(
            imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(imageFilteringTab_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(imageFilteringTab_Layout.createSequentialGroup()
                        .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(frameAverageRadioButton_)
                            .addComponent(frameSummationButton_))
                        .addGap(0, 817, Short.MAX_VALUE))
                    .addGroup(imageFilteringTab_Layout.createSequentialGroup()
                        .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel10)
                            .addComponent(rankFilterRadioButton_)
                            .addGroup(imageFilteringTab_Layout.createSequentialGroup()
                                .addGap(17, 17, 17)
                                .addComponent(jLabel9)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(rankSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(imageFilteringTab_Layout.createSequentialGroup()
                                .addGap(71, 71, 71)
                                .addComponent(offsetsLabel_)
                                .addGap(0, 683, Short.MAX_VALUE))
                            .addGroup(imageFilteringTab_Layout.createSequentialGroup()
                                .addGap(55, 55, 55)
                                .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(ch0OffsetLabel_)
                                    .addComponent(ch1OffsetLabel_)
                                    .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(ch5OffsetLabel_)
                                        .addComponent(ch2OffsetLabel_, javax.swing.GroupLayout.Alignment.TRAILING))
                                    .addComponent(ch4OffsetLabel_)
                                    .addComponent(ch3OffsetLabel_))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(ch1OffsetSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(ch2OffsetSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(ch3OffsetSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(ch5OffsetSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(ch4OffsetSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(ch0OffsetSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(187, 658, Short.MAX_VALUE))))))
        );
        imageFilteringTab_Layout.setVerticalGroup(
            imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(imageFilteringTab_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel10)
                    .addComponent(offsetsLabel_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(frameAverageRadioButton_)
                    .addComponent(ch0OffsetSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ch0OffsetLabel_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rankFilterRadioButton_)
                    .addComponent(ch1OffsetSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ch1OffsetLabel_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rankSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel9)
                    .addComponent(ch2OffsetSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ch2OffsetLabel_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ch3OffsetSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ch3OffsetLabel_)
                    .addComponent(frameSummationButton_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ch4OffsetSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ch4OffsetLabel_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(imageFilteringTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ch5OffsetSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ch5OffsetLabel_))
                .addContainerGap(145, Short.MAX_VALUE))
        );

        acqTabbedPane_.addTab("2Photon settings", imageFilteringTab_);

        runAcqButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        runAcqButton_.setText("Run acquisition");
        runAcqButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runAcqButton_ActionPerformed(evt);
            }
        });

        configPropsButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        configPropsButton_.setText("Configure device control");
        configPropsButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                configPropsButton_ActionPerformed(evt);
            }
        });

        jButton1.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        jButton1.setText("Calibrate");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        createdByHenryLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        createdByHenryLabel_.setText("Created by Henry Pinkard at the University of California San Francisco 2014-2015");

        openDatasetButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        openDatasetButton_.setText("Open dataset");
        openDatasetButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openDatasetButton_ActionPerformed(evt);
            }
        });

        helpButton_.setBackground(new java.awt.Color(200, 255, 200));
        helpButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        helpButton_.setText("Setup");
        helpButton_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                helpButton_ActionPerformed(evt);
            }
        });

        userGuideLink_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        userGuideLink_.setText("<html><a href=\\\"https://micro-manager.org/wiki/MicroMagellan\\\">Micro-Magellan User Guide</a></html>");

        estDurationLabel_.setText("Estimted Duration: ");

        citeLink_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        citeLink_.setText("<html><a href=\\\"http://www.nature.com/nmeth/journal/v13/n10/full/nmeth.3991.html\\\">Cite Micro-Magellan</a></html>");

        javax.swing.GroupLayout splitPaneBottomPanel_Layout = new javax.swing.GroupLayout(splitPaneBottomPanel_);
        splitPaneBottomPanel_.setLayout(splitPaneBottomPanel_Layout);
        splitPaneBottomPanel_Layout.setHorizontalGroup(
            splitPaneBottomPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(acqTabbedPane_)
            .addGroup(splitPaneBottomPanel_Layout.createSequentialGroup()
                .addGroup(splitPaneBottomPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(splitPaneBottomPanel_Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(splitPaneBottomPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(splitPaneBottomPanel_Layout.createSequentialGroup()
                                .addComponent(exploreZStepLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(exploreZStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(channelGroupLabel_)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(exploreChannelGroupCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 92, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(14, 14, 14)
                                .addComponent(exploreOverlapLabel_)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(exploreTileOverlapSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(explorePercentLabel_)
                                .addGap(12, 12, 12)
                                .addComponent(exploreFrameAverageButton_)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(exploreRankFilterButton_)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(exploreRankSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(splitPaneBottomPanel_Layout.createSequentialGroup()
                                .addComponent(exploreSavingNameLabel_)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(exploreSavingNameTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, 663, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(splitPaneBottomPanel_Layout.createSequentialGroup()
                                .addGap(326, 326, 326)
                                .addComponent(exploreSampleLabel_))
                            .addGroup(splitPaneBottomPanel_Layout.createSequentialGroup()
                                .addComponent(exploreSavingDirLabel_)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(globalSavingDirTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, 507, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(exploreBrowseButton_)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(openDatasetButton_))
                            .addComponent(newExploreWindowButton_)))
                    .addGroup(splitPaneBottomPanel_Layout.createSequentialGroup()
                        .addGap(337, 337, 337)
                        .addComponent(jLabel1))
                    .addGroup(splitPaneBottomPanel_Layout.createSequentialGroup()
                        .addGap(121, 121, 121)
                        .addComponent(configPropsButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 182, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(helpButton_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(citeLink_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(splitPaneBottomPanel_Layout.createSequentialGroup()
                        .addGap(250, 250, 250)
                        .addComponent(runAcqButton_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(estDurationLabel_))
                    .addGroup(splitPaneBottomPanel_Layout.createSequentialGroup()
                        .addGap(30, 30, 30)
                        .addComponent(createdByHenryLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(userGuideLink_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        splitPaneBottomPanel_Layout.setVerticalGroup(
            splitPaneBottomPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, splitPaneBottomPanel_Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(splitPaneBottomPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exploreSavingDirLabel_)
                    .addComponent(globalSavingDirTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exploreBrowseButton_)
                    .addComponent(openDatasetButton_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(exploreSampleLabel_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(splitPaneBottomPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exploreZStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exploreZStepLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(channelGroupLabel_)
                    .addComponent(exploreChannelGroupCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exploreOverlapLabel_)
                    .addComponent(exploreTileOverlapSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(explorePercentLabel_)
                    .addComponent(exploreFrameAverageButton_)
                    .addComponent(exploreRankFilterButton_)
                    .addComponent(exploreRankSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(splitPaneBottomPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exploreSavingNameTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exploreSavingNameLabel_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(newExploreWindowButton_)
                .addGap(1, 1, 1)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(acqTabbedPane_)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(splitPaneBottomPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(estDurationLabel_)
                    .addComponent(runAcqButton_))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(splitPaneBottomPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(configPropsButton_)
                    .addComponent(jButton1)
                    .addComponent(helpButton_)
                    .addComponent(citeLink_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(splitPaneBottomPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(createdByHenryLabel_)
                    .addComponent(userGuideLink_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(9, 9, 9))
        );

        addTextEditListener(zStepSpinner_);

        splitPane_.setRightComponent(splitPaneBottomPanel_);

        getContentPane().add(splitPane_);

        pack();
    }// </editor-fold>//GEN-END:initComponents

   private void configPropsButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_configPropsButton_ActionPerformed
       new PickPropertiesGUI(prefs_, this);
   }//GEN-LAST:event_configPropsButton_ActionPerformed

   private void runAcqButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runAcqButton_ActionPerformed
       //run acquisition
       new Thread(new Runnable() {
           @Override
           public void run() {
               eng_.runFixedAreaAcquisition(multiAcqManager_.getAcquisitionSettings(multipleAcqTable_.getSelectedRow()));
           }
       }).start();
   }//GEN-LAST:event_runAcqButton_ActionPerformed

   private void newExploreWindowButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newExploreWindowButton_ActionPerformed
       ExploreAcqSettings settings = new ExploreAcqSettings(
               ((Number) exploreZStepSpinner_.getValue()).doubleValue(), (Double) exploreTileOverlapSpinner_.getValue(),
               globalSavingDirTextField_.getText(), exploreSavingNameTextField_.getText(), exploreFrameAverageButton_.isSelected()
               ? FrameIntegrationMethod.FRAME_AVERAGE : FrameIntegrationMethod.RANK_FILTER, ((Number) exploreRankSpinner_.getValue()).doubleValue(),
               (String) exploreChannelGroupCombo_.getSelectedItem());
       eng_.runExploreAcquisition(settings);
   }//GEN-LAST:event_newExploreWindowButton_ActionPerformed

   private void autofocusChannelCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autofocusChannelCombo_ActionPerformed
       acquisitionSettingsChanged();
   }//GEN-LAST:event_autofocusChannelCombo_ActionPerformed

   private void ChannelGroupCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ChannelGroupCombo_ActionPerformed
      ((SimpleChannelTableModel) channelsTable_.getModel()).setChannelGroup((String) ChannelGroupCombo_.getSelectedItem());
      ((SimpleChannelTableModel) channelsTable_.getModel()).refreshChannels();
      ((SimpleChannelTableModel) channelsTable_.getModel()).fireTableDataChanged();
      acquisitionSettingsChanged();
   }//GEN-LAST:event_ChannelGroupCombo_ActionPerformed

   private void zStepSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zStepSpinner_StateChanged
       acquisitionSettingsChanged();
   }//GEN-LAST:event_zStepSpinner_StateChanged

   private void checkBox2D_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBox2D_ActionPerformed
       if (checkBox2D_.isSelected()) {
           checkBox3D_.setSelected(false);
       }
       enableAcquisitionComponentsAsNeeded();
       acquisitionSettingsChanged();
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
       acquisitionSettingsChanged();
   }//GEN-LAST:event_checkBox3D_ActionPerformed

   private void footprint2DComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_footprint2DComboBox_ActionPerformed
       acquisitionSettingsChanged();
   }//GEN-LAST:event_footprint2DComboBox_ActionPerformed

   private void distanceAboveFixedSurfaceSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_distanceAboveFixedSurfaceSpinner_StateChanged
       acquisitionSettingsChanged();
   }//GEN-LAST:event_distanceAboveFixedSurfaceSpinner_StateChanged

   private void distanceBelowFixedSurfaceSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_distanceBelowFixedSurfaceSpinner_StateChanged
       acquisitionSettingsChanged();
   }//GEN-LAST:event_distanceBelowFixedSurfaceSpinner_StateChanged

   private void fixedDistanceSurfaceComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixedDistanceSurfaceComboBox_ActionPerformed
       acquisitionSettingsChanged();
   }//GEN-LAST:event_fixedDistanceSurfaceComboBox_ActionPerformed

   private void fixedDistanceFromSurfaceRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixedDistanceFromSurfaceRadioButton_ActionPerformed
       enableAcquisitionComponentsAsNeeded();
       acquisitionSettingsChanged();
   }//GEN-LAST:event_fixedDistanceFromSurfaceRadioButton_ActionPerformed

   private void volumeBetweenFootprintCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_volumeBetweenFootprintCombo_ActionPerformed
       acquisitionSettingsChanged();
   }//GEN-LAST:event_volumeBetweenFootprintCombo_ActionPerformed

   private void bottomSurfaceCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bottomSurfaceCombo_ActionPerformed
       acquisitionSettingsChanged();
   }//GEN-LAST:event_bottomSurfaceCombo_ActionPerformed

   private void topSurfaceCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_topSurfaceCombo_ActionPerformed
       acquisitionSettingsChanged();
   }//GEN-LAST:event_topSurfaceCombo_ActionPerformed

   private void volumeBetweenSurfacesRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_volumeBetweenSurfacesRadioButton_ActionPerformed
       enableAcquisitionComponentsAsNeeded();
       acquisitionSettingsChanged();
   }//GEN-LAST:event_volumeBetweenSurfacesRadioButton_ActionPerformed

   private void zEndSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zEndSpinner_StateChanged
       acquisitionSettingsChanged();
   }//GEN-LAST:event_zEndSpinner_StateChanged

   private void zStartSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zStartSpinner_StateChanged
       acquisitionSettingsChanged();
   }//GEN-LAST:event_zStartSpinner_StateChanged

   private void simpleZStackFootprintCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_simpleZStackFootprintCombo_ActionPerformed
       acquisitionSettingsChanged();
   }//GEN-LAST:event_simpleZStackFootprintCombo_ActionPerformed

   private void simpleZStackRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_simpleZStackRadioButton_ActionPerformed
       enableAcquisitionComponentsAsNeeded();
       acquisitionSettingsChanged();
   }//GEN-LAST:event_simpleZStackRadioButton_ActionPerformed

   private void timePointsCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timePointsCheckBox_ActionPerformed
       for (Component c : timePointsPanel_.getComponents()) {
           c.setEnabled(timePointsCheckBox_.isSelected());
       }
       acquisitionSettingsChanged();
   }//GEN-LAST:event_timePointsCheckBox_ActionPerformed

   private void timeIntervalSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_timeIntervalSpinner_StateChanged
       acquisitionSettingsChanged();
   }//GEN-LAST:event_timeIntervalSpinner_StateChanged

   private void numTimePointsSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_numTimePointsSpinner_StateChanged
       acquisitionSettingsChanged();
   }//GEN-LAST:event_numTimePointsSpinner_StateChanged

   private void timeIntevalUnitCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_timeIntevalUnitCombo_ActionPerformed
       acquisitionSettingsChanged();
   }//GEN-LAST:event_timeIntevalUnitCombo_ActionPerformed

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
   }//GEN-LAST:event_exploreZStepSpinner_StateChanged

   private void exploreBrowseButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exploreBrowseButton_ActionPerformed
       String root = "";
       if (globalSavingDirTextField_.getText() != null && !globalSavingDirTextField_.getText().equals("")) {
           root = globalSavingDirTextField_.getText();
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
       globalSavingDirTextField_.setText(f.getAbsolutePath());
   }//GEN-LAST:event_exploreBrowseButton_ActionPerformed

   private void exploreSavingNameTextField_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exploreSavingNameTextField_ActionPerformed
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
       new Thread(new Runnable() {
           @Override
           public void run() {
               covariantPairManager_.loadPairingsFile(GUI.this);
           }
       } ).start();     
   }//GEN-LAST:event_loadPairingsButton_ActionPerformed

   private void removePairingButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removePairingButtonActionPerformed
       int newSelectionIndex = covariantPairingsTable_.getSelectedRow();
       if (newSelectionIndex == covariantPairingsTable_.getRowCount() - 1) {
           newSelectionIndex--;
       }
       covariantPairManager_.deletePair((CovariantPairing) ((CovariantPairingsTableModel) covariantPairingsTable_.getModel()).getValueAt(covariantPairingsTable_.getSelectedRow(), 1));
       covariantPairingsTable_.getSelectionModel().setSelectionInterval(newSelectionIndex, newSelectionIndex);
//       covariantPairingsTable_.valueChanged(new ListSelectionEvent(this, covariantPairingsTable_.getSelectedRow(),
//               covariantPairingsTable_.getSelectedRow(), true));
   }//GEN-LAST:event_removePairingButtonActionPerformed

   private void deleteCovariedPairingValueButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteCovariedPairingValueButton_ActionPerformed
       int selectedRow = covariantPairValuesTable_.getSelectedRow();
       if (selectedRow != -1) {
           //finish editing so editor doesn't refer to a deleted row index
           covariantPairValuesTable_.editingStopped(null);
           covariantPairManager_.deleteValuePair(covariantPairingsTable_.getSelectedRow(), selectedRow);
           ((CovariantPairValuesTableModel) covariantPairValuesTable_.getModel()).fireTableDataChanged();
           //re add selection for quick serial deleting
           if (covariantPairValuesTable_.getRowCount() > 0) {
               if (selectedRow == covariantPairValuesTable_.getRowCount()) {
                   covariantPairValuesTable_.setRowSelectionInterval(selectedRow - 1, selectedRow - 1);
               } else {
                   covariantPairValuesTable_.setRowSelectionInterval(selectedRow, selectedRow);
               }
           } 
       }
   }//GEN-LAST:event_deleteCovariedPairingValueButton_ActionPerformed

   private void useAutofocusCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useAutofocusCheckBox_ActionPerformed
       enableAcquisitionComponentsAsNeeded();
       acquisitionSettingsChanged();
   }//GEN-LAST:event_useAutofocusCheckBox_ActionPerformed

   private void acqOverlapPercentSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_acqOverlapPercentSpinner_StateChanged
       acquisitionSettingsChanged();
       //update any grids/surface shown
       for (int i = 0; i < regionManager_.getNumberOfRegions(); i++) {
           regionManager_.drawRegionOverlay(regionManager_.getRegion(i));
       }

       for (int i = 0; i < surfaceManager_.getNumberOfSurfaces(); i++) {
           surfaceManager_.drawSurfaceOverlay(surfaceManager_.getSurface(i));
       }
   }//GEN-LAST:event_acqOverlapPercentSpinner_StateChanged

   private void umAboveTopSurfaceSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_umAboveTopSurfaceSpinner_StateChanged
       acquisitionSettingsChanged();
   }//GEN-LAST:event_umAboveTopSurfaceSpinner_StateChanged

   private void umBelowBottomSurfaceSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_umBelowBottomSurfaceSpinner_StateChanged
       acquisitionSettingsChanged();
   }//GEN-LAST:event_umBelowBottomSurfaceSpinner_StateChanged

    private void autofocusZDeviceComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autofocusZDeviceComboBox_ActionPerformed
        acquisitionSettingsChanged();
    }//GEN-LAST:event_autofocusZDeviceComboBox_ActionPerformed

    private void autofocusMaxDisplacementSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_autofocusMaxDisplacementSpinner_StateChanged
        acquisitionSettingsChanged();
    }//GEN-LAST:event_autofocusMaxDisplacementSpinner_StateChanged

   private void autofocusInitialPositionSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_autofocusInitialPositionSpinner_StateChanged
       acquisitionSettingsChanged();
   }//GEN-LAST:event_autofocusInitialPositionSpinner_StateChanged

   private void autofocusInitialPositionCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autofocusInitialPositionCheckBox_ActionPerformed
       enableAcquisitionComponentsAsNeeded();
       acquisitionSettingsChanged();
   }//GEN-LAST:event_autofocusInitialPositionCheckBox_ActionPerformed

   private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
       new AffineGUI();
   }//GEN-LAST:event_jButton1ActionPerformed

   private void rankFilterRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rankFilterRadioButton_ActionPerformed
       acquisitionSettingsChanged();
   }//GEN-LAST:event_rankFilterRadioButton_ActionPerformed

   private void exploreRankFilterButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exploreRankFilterButton_ActionPerformed
   }//GEN-LAST:event_exploreRankFilterButton_ActionPerformed

   private void ch0OffsetSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_ch0OffsetSpinner_StateChanged
       validateChannelOffsets();
       settings_.channelOffsetChanged();
   }//GEN-LAST:event_ch0OffsetSpinner_StateChanged

   private void ch1OffsetSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_ch1OffsetSpinner_StateChanged
       validateChannelOffsets();
       settings_.channelOffsetChanged();
   }//GEN-LAST:event_ch1OffsetSpinner_StateChanged

   private void ch2OffsetSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_ch2OffsetSpinner_StateChanged
       validateChannelOffsets();
       settings_.channelOffsetChanged();
   }//GEN-LAST:event_ch2OffsetSpinner_StateChanged

   private void ch3OffsetSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_ch3OffsetSpinner_StateChanged
       validateChannelOffsets();
       settings_.channelOffsetChanged();
   }//GEN-LAST:event_ch3OffsetSpinner_StateChanged

   private void ch4OffsetSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_ch4OffsetSpinner_StateChanged
       validateChannelOffsets();
       settings_.channelOffsetChanged();
   }//GEN-LAST:event_ch4OffsetSpinner_StateChanged

   private void ch5OffsetSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_ch5OffsetSpinner_StateChanged
       validateChannelOffsets();
       settings_.channelOffsetChanged();
   }//GEN-LAST:event_ch5OffsetSpinner_StateChanged

    private void frameAverageRadioButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_frameAverageRadioButton_ActionPerformed
       acquisitionSettingsChanged();
    }//GEN-LAST:event_frameAverageRadioButton_ActionPerformed

    private void rankSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_rankSpinner_StateChanged
        acquisitionSettingsChanged();
    }//GEN-LAST:event_rankSpinner_StateChanged

    private void frameSummationButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_frameSummationButton_ActionPerformed
       acquisitionSettingsChanged();
    }//GEN-LAST:event_frameSummationButton_ActionPerformed

   private void openDatasetButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openDatasetButton_ActionPerformed
       File selectedFile = null;
      if (JavaUtils.isMac()) {
         System.setProperty("apple.awt.fileDialogForDirectories", "true");
         FileDialog fd = new FileDialog(this, "Select Magellan dataset to load", FileDialog.LOAD);

         fd.setVisible(true);
         if (fd.getFile() != null) {
            selectedFile = new File(fd.getDirectory() + File.separator + fd.getFile());
            selectedFile = new File(selectedFile.getAbsolutePath());
         }
         fd.dispose();
         System.setProperty("apple.awt.fileDialogForDirectories", "false");
      } else {
         JFileChooser fc = new JFileChooser(globalSavingDirTextField_.getText());
         fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
         fc.setDialogTitle("Select Magellan dataset to load");
         int returnVal = fc.showOpenDialog(this);
         if (returnVal == JFileChooser.APPROVE_OPTION) {
            selectedFile = fc.getSelectedFile();
         }
      }
      if (selectedFile == null) {
         return; //canceled
      }
      final File finalFile = selectedFile;
      new Thread(new Runnable() {
         @Override
         public void run() {
            new LoadedAcquisitionData(finalFile.toString());
         }
      }).start();
      
   }//GEN-LAST:event_openDatasetButton_ActionPerformed

   private void helpButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_helpButton_ActionPerformed
      new StartupHelpWindow();
   }//GEN-LAST:event_helpButton_ActionPerformed

   private void saveSurfacesButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveSurfacesButton_ActionPerformed
      surfaceManager_.saveSurfaces(this);
   }//GEN-LAST:event_saveSurfacesButton_ActionPerformed

   private void loadSurfacesButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadSurfacesButton_ActionPerformed
      surfaceManager_.loadSurfaces(this);
   }//GEN-LAST:event_loadSurfacesButton_ActionPerformed

   private void loadButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadButton_ActionPerformed
      regionManager_.loadRegions(this);
   }//GEN-LAST:event_loadButton_ActionPerformed

   private void saveButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButton_ActionPerformed
      regionManager_.saveRegions(this);
   }//GEN-LAST:event_saveButton_ActionPerformed

   private void withinDistanceFromFootprintCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_withinDistanceFromFootprintCombo_ActionPerformed
      acquisitionSettingsChanged();
   }//GEN-LAST:event_withinDistanceFromFootprintCombo_ActionPerformed

   private void acqOrderCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_acqOrderCombo_ActionPerformed
      acquisitionSettingsChanged();
   }//GEN-LAST:event_acqOrderCombo_ActionPerformed

   private void collectionPlaneCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_collectionPlaneCombo_ActionPerformed
      acquisitionSettingsChanged();
   }//GEN-LAST:event_collectionPlaneCombo_ActionPerformed

   private void collectionPlaneCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_collectionPlaneCheckBox_ActionPerformed
       enableAcquisitionComponentsAsNeeded();
       acquisitionSettingsChanged();
   }//GEN-LAST:event_collectionPlaneCheckBox_ActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox ChannelGroupCombo_;
    private javax.swing.JPanel ChannelsTab_;
    private javax.swing.JComboBox acqOrderCombo_;
    private javax.swing.JLabel acqOrderLabel_;
    private javax.swing.JSpinner acqOverlapPercentSpinner_;
    private javax.swing.JTabbedPane acqTabbedPane_;
    private javax.swing.JLabel acqTileOverlapLabel_;
    private javax.swing.JButton addAcqButton_;
    private javax.swing.JButton addCovariedPairingValueButton_;
    private javax.swing.JComboBox autofocusChannelCombo_;
    private javax.swing.JPanel autofocusComponentsPanel_;
    private javax.swing.JCheckBox autofocusInitialPositionCheckBox_;
    private javax.swing.JSpinner autofocusInitialPositionSpinner_;
    private javax.swing.JLabel autofocusMaxDisplacementLabel_;
    private javax.swing.JSpinner autofocusMaxDisplacementSpinner_;
    private javax.swing.JPanel autofocusTab_l;
    private javax.swing.JComboBox autofocusZDeviceComboBox_;
    private javax.swing.JLabel autofocusZLabel_;
    private javax.swing.JComboBox bottomSurfaceCombo_;
    private javax.swing.JLabel bottomSurfaceLabel_;
    private javax.swing.JLabel ch0OffsetLabel_;
    private javax.swing.JSpinner ch0OffsetSpinner_;
    private javax.swing.JLabel ch1OffsetLabel_;
    private javax.swing.JSpinner ch1OffsetSpinner_;
    private javax.swing.JLabel ch2OffsetLabel_;
    private javax.swing.JSpinner ch2OffsetSpinner_;
    private javax.swing.JLabel ch3OffsetLabel_;
    private javax.swing.JSpinner ch3OffsetSpinner_;
    private javax.swing.JLabel ch4OffsetLabel_;
    private javax.swing.JSpinner ch4OffsetSpinner_;
    private javax.swing.JLabel ch5OffsetLabel_;
    private javax.swing.JSpinner ch5OffsetSpinner_;
    private javax.swing.JLabel channelGroupLabel_;
    private javax.swing.JTable channelsTable_;
    private javax.swing.JCheckBox checkBox2D_;
    private javax.swing.JCheckBox checkBox3D_;
    private javax.swing.JLabel citeLink_;
    private javax.swing.JCheckBox collectionPlaneCheckBox_;
    private javax.swing.JComboBox collectionPlaneCombo_;
    private javax.swing.JLabel collectionPlaneLabel_;
    private javax.swing.JButton configPropsButton_;
    private javax.swing.JPanel controlPanelName_;
    private javax.swing.JTable covariantPairValuesTable_;
    private javax.swing.JTable covariantPairingsTable_;
    private javax.swing.JPanel covariedSettingsTab_;
    private javax.swing.JLabel createdByHenryLabel_;
    private javax.swing.JButton deinterleaveButton_;
    private javax.swing.JButton deleteAllRegionsButton_;
    private javax.swing.JButton deleteAllSurfacesButton_;
    private javax.swing.JButton deleteCovariedPairingValueButton_;
    private javax.swing.JButton deleteSelectedRegionButton_;
    private javax.swing.JButton deleteSelectedSurfaceButton_;
    private javax.swing.JScrollPane deviceControlScrollPane_;
    private javax.swing.JTable deviceControlTable_;
    private javax.swing.JSpinner distanceAboveFixedSurfaceSpinner_;
    private javax.swing.JLabel distanceAboveSurfaceLabel_;
    private javax.swing.JSpinner distanceBelowFixedSurfaceSpinner_;
    private javax.swing.JLabel distanceBelowSurfaceLabel_;
    private javax.swing.JLabel estDurationLabel_;
    private javax.swing.JButton exploreBrowseButton_;
    private javax.swing.JComboBox exploreChannelGroupCombo_;
    private javax.swing.ButtonGroup exploreFilterMethodButtonGroup_;
    private javax.swing.JRadioButton exploreFrameAverageButton_;
    private javax.swing.JLabel exploreOverlapLabel_;
    private javax.swing.JLabel explorePercentLabel_;
    private javax.swing.JRadioButton exploreRankFilterButton_;
    private javax.swing.JSpinner exploreRankSpinner_;
    private javax.swing.JLabel exploreSampleLabel_;
    private javax.swing.JLabel exploreSavingDirLabel_;
    private javax.swing.JLabel exploreSavingNameLabel_;
    private javax.swing.JTextField exploreSavingNameTextField_;
    private javax.swing.JSpinner exploreTileOverlapSpinner_;
    private javax.swing.JLabel exploreZStepLabel_;
    private javax.swing.JSpinner exploreZStepSpinner_;
    private javax.swing.ButtonGroup filterMethodButtonGroup_;
    private javax.swing.JRadioButton fixedDistanceFromSurfaceRadioButton_;
    private javax.swing.JComboBox fixedDistanceSurfaceComboBox_;
    private javax.swing.JPanel fixedDistanceZPanel_;
    private javax.swing.JLabel fixedSurfaceLabel_;
    private javax.swing.JLabel footprin2DLabel_;
    private javax.swing.JComboBox footprint2DComboBox_;
    private javax.swing.JRadioButton frameAverageRadioButton_;
    private javax.swing.JRadioButton frameSummationButton_;
    private javax.swing.JTextField globalSavingDirTextField_;
    private javax.swing.JTable gridTable_;
    private javax.swing.JPanel gridsPanel_;
    private javax.swing.JButton helpButton_;
    private javax.swing.JPanel imageFilteringTab_;
    private javax.swing.JButton intereaveButton_;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JButton loadButton_;
    private javax.swing.JButton loadPairingsButton_;
    private javax.swing.JButton loadSurfacesButton_;
    private javax.swing.JButton moveAcqDownButton_;
    private javax.swing.JButton moveAcqUpButton_;
    private javax.swing.JScrollPane multipleAcqScrollPane_;
    private javax.swing.JTable multipleAcqTable_;
    private javax.swing.JPanel multipleAcquisitionsPanel;
    private javax.swing.JButton newExploreWindowButton_;
    private javax.swing.JButton newParingButton_;
    private javax.swing.JLabel numTimePointsLabel_;
    private javax.swing.JSpinner numTimePointsSpinner_;
    private javax.swing.JLabel offsetsLabel_;
    private javax.swing.JButton openDatasetButton_;
    private javax.swing.JPanel panel2D_;
    private javax.swing.JScrollPane propertyPairValuesScrollpane_;
    private javax.swing.JScrollPane propertyPairingsScrollpane_;
    private javax.swing.JRadioButton rankFilterRadioButton_;
    private javax.swing.JSpinner rankSpinner_;
    private javax.swing.JButton removeAcqButton_;
    private javax.swing.JButton removePairingButton;
    private javax.swing.JButton runAcqButton_;
    private javax.swing.JButton runMultipleAcquisitionsButton_;
    private javax.swing.JButton saveButton_;
    private javax.swing.JButton savePairingsButton_;
    private javax.swing.JButton saveSurfacesButton_;
    private javax.swing.JLabel savingNameLabel_;
    private javax.swing.JTextField savingNameTextField_;
    private javax.swing.JPanel savingTab_;
    private javax.swing.JPanel simpleZPanel_;
    private javax.swing.JComboBox simpleZStackFootprintCombo_;
    private javax.swing.JRadioButton simpleZStackRadioButton_;
    private javax.swing.JPanel spaceTab_;
    private javax.swing.JPanel splitPaneBottomPanel_;
    private javax.swing.JTabbedPane splitPaneTopPanel_;
    private javax.swing.JSplitPane splitPane_;
    private javax.swing.JPanel surfacesPanel_;
    private javax.swing.JTable surfacesTable_;
    private javax.swing.JLabel tileOverlapPercentLabel_;
    private javax.swing.JLabel timeIntervalLabel_;
    private javax.swing.JSpinner timeIntervalSpinner_;
    private javax.swing.JComboBox timeIntevalUnitCombo_;
    private javax.swing.JCheckBox timePointsCheckBox_;
    private javax.swing.JPanel timePointsPanel_;
    private javax.swing.JPanel timePointsTab_;
    private javax.swing.JComboBox topSurfaceCombo_;
    private javax.swing.JLabel topSurfaceLabel_;
    private javax.swing.JLabel umAboveLabel_;
    private javax.swing.JSpinner umAboveTopSurfaceSpinner_;
    private javax.swing.JLabel umAboveVolBetweenLabel_;
    private javax.swing.JSpinner umBelowBottomSurfaceSpinner_;
    private javax.swing.JLabel umBelowLabel_;
    private javax.swing.JLabel umBelowVolBetweenLabel_;
    private javax.swing.JCheckBox useAutofocusCheckBox_;
    private javax.swing.JLabel userGuideLink_;
    private javax.swing.JComboBox volumeBetweenFootprintCombo_;
    private javax.swing.JRadioButton volumeBetweenSurfacesRadioButton_;
    private javax.swing.JPanel volumeBetweenZPanel_;
    private javax.swing.JComboBox withinDistanceFromFootprintCombo_;
    private javax.swing.JLabel zEndLabel;
    private javax.swing.JSpinner zEndSpinner_;
    private javax.swing.ButtonGroup zStackModeButtonGroup_;
    private javax.swing.JLabel zStartLabel;
    private javax.swing.JSpinner zStartSpinner_;
    private javax.swing.JLabel zStepLabel_;
    private javax.swing.JSpinner zStepSpinner_;
    // End of variables declaration//GEN-END:variables

}

