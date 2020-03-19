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
package org.micromanager.magellan.internal.gui;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.FileDialog;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.magellan.internal.acq.AcqDurationEstimator;
import org.micromanager.magellan.internal.acq.Acquisition;
import org.micromanager.magellan.internal.acq.AcquisitionSettingsBase;
import org.micromanager.magellan.internal.acq.ExploreAcqSettings;
import org.micromanager.magellan.internal.acq.MagellanGUIAcquisitionSettings;
import org.micromanager.magellan.internal.acq.MagellanEngine;
import org.micromanager.magellan.internal.acq.MagellanAcquisitionsManager;
import org.micromanager.magellan.internal.acq.ExploreAcquisition;
import org.micromanager.magellan.internal.channels.ColorEditor;
import org.micromanager.magellan.internal.channels.ColorRenderer;
import org.micromanager.magellan.internal.coordinates.MagellanAffineUtils;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.GlobalSettings;
import org.micromanager.magellan.internal.misc.JavaUtils;
import org.micromanager.magellan.internal.misc.LoadedAcquisitionData;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridManager;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 *
 * @author Henry
 */
public class GUI extends javax.swing.JFrame {

   private static final String PREF_SIZE_WIDTH = "Magellan gui size width";
   private static final String PREF_SIZE_HEIGHT = "Magellan gui size height";
   private static final Color DARK_GREEN = new Color(0, 128, 0);
   private static final Color LIGHT_GREEN = new Color(0, 200, 0);
   private static final Color DEFAULT_RADIO_BUTTON_TEXT_COLOR = new JRadioButton().getForeground();

   private AcqDurationEstimator acqDurationEstimator_;
   private MutablePropertyMapView prefs_;
   private SurfaceGridManager manager_ = new SurfaceGridManager();
   private MagellanAcquisitionsManager multiAcqManager_;
   private GlobalSettings settings_;
   private boolean storeAcqSettings_ = true;
   private int multiAcqSelectedIndex_ = 0;
   private LinkedList<JSpinner> offsetSpinners_ = new LinkedList<JSpinner>();
   private static GUI singleton_;
   private ExploreAcquisition exploreAcq_;
   private volatile boolean acquisitionRunning_ = false;
   private volatile boolean ignoreUpdate_ = false;

   public GUI(String version) {
      singleton_ = this;
      storeAcqSettings_ = false; // dont store during intialization
      prefs_ = Magellan.getStudio().profile().getSettings(GUI.class);
      settings_ = new GlobalSettings();
      this.setTitle("Micro-Magellan " + version);
      acqDurationEstimator_ = new AcqDurationEstimator();
      new MagellanEngine(Magellan.getCore(), acqDurationEstimator_);
      multiAcqManager_ = new MagellanAcquisitionsManager(this);
      initComponents();
      moreInitialization();
      this.setVisible(true);
      addGlobalSettingsListeners();
      storeAcqSettings_ = true;
      storeCurrentAcqSettings();
   }

   public static GUI getInstance() {
      return singleton_;
   }

   public void acquisitionRunning(boolean running) {
      //disable or enabe the controls that cannot be changed during acquisition
      zStepSpinner_.setEnabled(!running);
      zStepLabel_.setEnabled(!running);
      acqTileOverlapLabel_.setEnabled(!running);
      acqOverlapPercentSpinner_.setEnabled(!running);
      tileOverlapPercentLabel_.setEnabled(!running);
      this.repaint();
   }

   public static void updateEstiamtedSizeLabel(final String text) {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            singleton_.estSizeLabel_.setText(text);
         }
      });
   }

   public static void updateEstiamtedDurationLabel(final String text) {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            singleton_.estDurationLabel_.setText(text);
         }
      });
   }

   private void updateAvailableDiskSpaceLabel() {
      double mb = (new File(settings_.getStoredSavingDirectory()).getUsableSpace()) / 1024.0 / 1024.0;
      if (mb < 1024) {
         freeDiskSpaceLabel_.setText("Free disk space: " + ((int) mb) + " MB");
      } else {
         double gb = mb / 1024.0;
         freeDiskSpaceLabel_.setText("Free disk space: " + String.format("%.1f", gb) + " GB");
      }
   }

   public void acquisitionSettingsChanged() {
      updateAvailableDiskSpaceLabel();
      //refresh GUI and store its state in current acq settings
      refreshBoldedText();
      ((MultipleAcquisitionTableModel) multipleAcqTable_.getModel()).fireTableDataChanged();
      multipleAcqTable_.repaint();
      //Tell the channels table something has changed
      ((SimpleChannelTableModel) channelsTable_.getModel()).fireTableDataChanged();
      channelsTable_.repaint();
      storeCurrentAcqSettings();
   }

   public MagellanGUIAcquisitionSettings getActiveAcquisitionSettings() {
      return multiAcqManager_.getAcquisitionSettings(multiAcqSelectedIndex_);
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

      //add link to g report
      bugReportLink_.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            new Thread(new Runnable() {
               @Override
               public void run() {
                  try {
                     ij.plugin.BrowserLauncher.openURL("https://github.com/henrypinkard/micro-manager/issues");
                  } catch (IOException ex) {
                     Log.log("couldn't open citation link");
                  }
               }
            }).start();
         }
      });

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
            refreshAcqControlsFromSettings();
         }
      });
      //Table column widths
//      multipleAcqTable_.getColumnModel().getColumn(0).setMaxWidth(40); //name column
      multipleAcqTable_.getColumnModel().getColumn(2).setMaxWidth(100); //status column

      channelsTable_.getColumnModel().getColumn(0).setMaxWidth(30); //Acitve checkbox column

      surfacesAndGridsTable_.getColumnModel().getColumn(0).setMaxWidth(120); //type column

      //set color renderer for channel table
      for (int col = 1; col < channelsTable_.getColumnModel().getColumnCount(); col++) {
         if (col == 4) {
            ColorRenderer cr = new ColorRenderer(true);
            ColorEditor ce = new ColorEditor((AbstractTableModel) channelsTable_.getModel(), col);
            channelsTable_.getColumnModel().getColumn(col).setCellRenderer(cr);
            channelsTable_.getColumnModel().getColumn(col).setCellEditor(ce);
         } else {
            DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
            renderer.setHorizontalAlignment(SwingConstants.LEFT); // left justify
            channelsTable_.getColumnModel().getColumn(col).setCellRenderer(renderer);
         }
//         if (col == 2) {
//            //left justified editor
//            JTextField tf = new JTextField();
//            tf.setHorizontalAlignment(SwingConstants.LEFT);
//            DefaultCellEditor ed = new DefaultCellEditor(tf);
//            channelsTable_.getColumnModel().getColumn(col).setCellEditor(ed);
//         }
      }

      //load global settings     
      globalSavingDirTextField_.setText(settings_.getStoredSavingDirectory());
      //load explore settings
      exploreSavingNameTextField_.setText(ExploreAcqSettings.getNameFromPrefs());
      exploreZStepSpinner_.setValue(ExploreAcqSettings.getZStepFromPrefs());
      exploreTileOverlapSpinner_.setValue(ExploreAcqSettings.getExploreTileOverlapFromPrefs());

      refreshAcqControlsFromSettings();
      enableAndChangeFonts();

      int width = settings_.getIntInPrefs(PREF_SIZE_WIDTH, Integer.MIN_VALUE);
      int height = settings_.getIntInPrefs(PREF_SIZE_HEIGHT, Integer.MIN_VALUE);
      if (height != Integer.MIN_VALUE && width != Integer.MIN_VALUE) {
         this.setSize(width, height);
      }

      //save resizing
      this.addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(ComponentEvent e) {
            settings_.storeIntInPrefs(PREF_SIZE_WIDTH, GUI.this.getWidth());
            settings_.storeIntInPrefs(PREF_SIZE_HEIGHT, GUI.this.getHeight());
         }
      });

      //set XY footprint combos to default
      xyFootprintComboBox_.setSelectedIndex(0);
   }

   private void colorAndBoldButton(JRadioButton b) {
      b.setFont(b.getFont().deriveFont(b.isSelected() ? Font.BOLD : Font.PLAIN));
      b.setForeground(b.isSelected() ? LIGHT_GREEN : DEFAULT_RADIO_BUTTON_TEXT_COLOR);
      b.invalidate();
      b.validate();
   }

   private void refreshBoldedText() {
      if (acqTabbedPane_.getTabCount() == 3) { //Make sure inititilization is done
         JLabel l3 = new JLabel("Space");
         l3.setForeground(true ? LIGHT_GREEN : Color.black);
         l3.setFont(acqTabbedPane_.getComponent(0).getFont().deriveFont(true ? Font.BOLD : Font.PLAIN));
         acqTabbedPane_.setTabComponentAt(0, l3);
         JLabel l4 = new JLabel("Channels");
         boolean useChannels = !multiAcqManager_.getAcquisitionSettings(multiAcqSelectedIndex_).channelGroup_.equals("");
         l4.setForeground(useChannels ? LIGHT_GREEN : Color.black);
         l4.setFont(acqTabbedPane_.getComponent(1).getFont().deriveFont(useChannels ? Font.BOLD : Font.PLAIN));
         acqTabbedPane_.setTabComponentAt(1, l4);
         JLabel l2 = new JLabel("Time");
         l2.setForeground(timePointsCheckBox_.isSelected() ? LIGHT_GREEN : Color.black);
         l2.setFont(acqTabbedPane_.getComponent(2).getFont().deriveFont(timePointsCheckBox_.isSelected() ? Font.BOLD : Font.PLAIN));
         acqTabbedPane_.setTabComponentAt(2, l2);
         acqTabbedPane_.revalidate();
      }

      if (exploreAcqTabbedPane_.getTabCount() == 2) {
         JLabel l = new JLabel("Explore");
         l.setForeground(exploreAcqTabbedPane_.getSelectedIndex() == 0 ? LIGHT_GREEN : Color.black);
         l.setFont(exploreAcqTabbedPane_.getComponent(0).getFont().deriveFont(
                 exploreAcqTabbedPane_.getSelectedIndex() == 0 ? Font.BOLD : Font.PLAIN));
         exploreAcqTabbedPane_.setTabComponentAt(0, l);

         JLabel l1 = new JLabel("Acquisition(s)");
         l1.setForeground(exploreAcqTabbedPane_.getSelectedIndex() == 1 ? LIGHT_GREEN : Color.black);
         l1.setFont(exploreAcqTabbedPane_.getComponent(1).getFont().deriveFont(
                 exploreAcqTabbedPane_.getSelectedIndex() == 1 ? Font.BOLD : Font.PLAIN));
         exploreAcqTabbedPane_.setTabComponentAt(1, l1);
         exploreAcqTabbedPane_.revalidate();
      }

      //set bold for all buttons selected
      colorAndBoldButton(button2D_);
      colorAndBoldButton(button3D_);
      colorAndBoldButton(volumeBetweenSurfacesButton_);
      colorAndBoldButton(withinDistanceFromSurfacesButton_);
      colorAndBoldButton(cuboidVolumeButton_);
      colorAndBoldButton(noCollectionPlaneButton_);
      colorAndBoldButton(useCollectionPlaneButton_);

      labelDiagram2dSimple_.setBorder(BorderFactory.createLineBorder(
              noCollectionPlaneButton_.isSelected() ? DARK_GREEN : Color.BLACK, 4, true));
      labelDiagram2DSurface_.setBorder(BorderFactory.createLineBorder(
              useCollectionPlaneButton_.isSelected() ? DARK_GREEN : Color.BLACK, 4, true));
   }

   private void enableAndChangeFonts() {
      //Set Tab titles
      refreshBoldedText();
      //Enable or disable time point stuff
      for (Component c : timePointsPanel_.getComponents()) {
         c.setEnabled(timePointsCheckBox_.isSelected());
      }
   }

   public void storeCurrentAcqSettings() {
      if (!storeAcqSettings_) {
         return;
      }
      MagellanGUIAcquisitionSettings settings = multiAcqManager_.getAcquisitionSettings(multiAcqSelectedIndex_);
      //saving
      settings.dir_ = globalSavingDirTextField_.getText();
      settings.name_ = multiAcqManager_.getAcquisitionSettingsName(multiAcqSelectedIndex_);
      //time
      settings.timeEnabled_ = timePointsCheckBox_.isSelected();
      if (settings.timeEnabled_) {
         settings.numTimePoints_ = (Integer) numTimePointsSpinner_.getValue();
         settings.timePointInterval_ = (Double) timeIntervalSpinner_.getValue();
         settings.timeIntervalUnit_ = timeIntevalUnitCombo_.getSelectedIndex();
      }
      //space  
      settings.tileOverlap_ = (Double) acqOverlapPercentSpinner_.getValue();
      settings.xyFootprint_ = manager_.getSurfaceOrGrid(xyFootprintComboBox_.getSelectedIndex());
      if (button2D_.isSelected()) { //2D pane
         if (useCollectionPlaneButton_.isSelected()) {
            settings.collectionPlane_ = manager_.getSurface(collectionPlaneCombo_.getSelectedIndex());
            settings.spaceMode_ = MagellanGUIAcquisitionSettings.REGION_2D_SURFACE_GUIDED;
         } else {
            settings.collectionPlane_ = null;
            settings.spaceMode_ = MagellanGUIAcquisitionSettings.REGION_2D;
         }
      } else if (button3D_.isSelected()) {
         settings.zStep_ = (Double) zStepSpinner_.getValue();
         settings.channelsAtEverySlice_ = acqOrderCombo_.getSelectedIndex() == 1;
         if (cuboidVolumeButton_.isSelected()) {
            settings.spaceMode_ = MagellanGUIAcquisitionSettings.CUBOID_Z_STACK;
            settings.zStart_ = (Double) zStartSpinner_.getValue();
            settings.zEnd_ = (Double) zEndSpinner_.getValue();
         } else if (volumeBetweenSurfacesButton_.isSelected()) {
            settings.spaceMode_ = MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK;
            settings.topSurface_ = manager_.getSurface(topSurfaceCombo_.getSelectedIndex());
            settings.bottomSurface_ = manager_.getSurface(bottomSurfaceCombo_.getSelectedIndex());
            settings.distanceAboveTopSurface_ = (Double) umAboveTopSurfaceSpinner_.getValue();
            settings.distanceBelowBottomSurface_ = (Double) umBelowBottomSurfaceSpinner_.getValue();
         } else if (withinDistanceFromSurfacesButton_.isSelected()) {
            settings.spaceMode_ = MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK;
            settings.distanceBelowFixedSurface_ = ((Number) distanceBelowFixedSurfaceSpinner_.getValue()).doubleValue();
            settings.distanceAboveFixedSurface_ = ((Number) distanceAboveFixedSurfaceSpinner_.getValue()).doubleValue();
            settings.fixedSurface_ = manager_.getSurface(fixedDistanceSurfaceComboBox_.getSelectedIndex());
         }
      } else {
         settings.spaceMode_ = MagellanGUIAcquisitionSettings.NO_SPACE; //This isnt a thing anymore...
      }

      //channels
      settings.channelGroup_ = (String) ChannelGroupCombo_.getSelectedItem();

      settings.storePreferedValues();
      multipleAcqTable_.repaint();

      acqDurationEstimator_.calcAcqDuration(getActiveAcquisitionSettings());

   }

   public void refreshAcqControlsFromSettings() {
      MagellanGUIAcquisitionSettings settings = multiAcqManager_.getAcquisitionSettings(multiAcqSelectedIndex_);
      //don't autostore outdated settings while controls are being populated
      storeAcqSettings_ = false;
      multiAcqManager_.setAcquisitionName(multiAcqSelectedIndex_, settings.name_);
      //time
      timePointsCheckBox_.setSelected(settings.timeEnabled_);
      numTimePointsSpinner_.setValue(settings.numTimePoints_);
      timeIntervalSpinner_.setValue(settings.timePointInterval_);
      timeIntevalUnitCombo_.setSelectedIndex(settings.timeIntervalUnit_);
      //space           
      acqOrderCombo_.setSelectedIndex(settings.channelsAtEverySlice_ ? 1 : 0);
      if (settings.spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D || settings.spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D_SURFACE_GUIDED) {
         boolean useSurface = settings.spaceMode_ == MagellanGUIAcquisitionSettings.REGION_2D_SURFACE_GUIDED;
         noCollectionPlaneButton_.setSelected(!useSurface);
         useCollectionPlaneButton_.setSelected(useSurface);
         button2D_.setSelected(true);
         button2D_ActionPerformed(null);
      } else {
         button3D_.setSelected(true);
         button3D_ActionPerformed(null);
      }
      if (settings.spaceMode_ == MagellanGUIAcquisitionSettings.CUBOID_Z_STACK) {
         cuboidVolumeButton_.setSelected(true);
         cuboidVolumeButton_ActionPerformed(null);
      } else if (settings.spaceMode_ == MagellanGUIAcquisitionSettings.VOLUME_BETWEEN_SURFACES_Z_STACK) {
         volumeBetweenSurfacesButton_.setSelected(true);
         volumeBetweenSurfacesButton_ActionPerformed(null);
      } else if (settings.spaceMode_ == MagellanGUIAcquisitionSettings.SURFACE_FIXED_DISTANCE_Z_STACK) {
         withinDistanceFromSurfacesButton_.setSelected(true);
         withinDistanceFromSurfacesButton_ActionPerformed(null);
      }
      zStepSpinner_.setValue(settings.zStep_);
      zStartSpinner_.setValue(settings.zStart_);
      zEndSpinner_.setValue(settings.zEnd_);
      distanceBelowFixedSurfaceSpinner_.setValue(settings.distanceBelowFixedSurface_);
      distanceAboveFixedSurfaceSpinner_.setValue(settings.distanceAboveFixedSurface_);
      acqOverlapPercentSpinner_.setValue(settings.tileOverlap_);
      umAboveTopSurfaceSpinner_.setValue(settings.distanceAboveTopSurface_);
      umBelowBottomSurfaceSpinner_.setValue(settings.distanceBelowBottomSurface_);
      //select surfaces/regions
      topSurfaceCombo_.setSelectedItem(settings.topSurface_);
      bottomSurfaceCombo_.setSelectedItem(settings.bottomSurface_);
      fixedDistanceSurfaceComboBox_.setSelectedItem(settings.fixedSurface_);
      xyFootprintComboBox_.setSelectedItem(settings.xyFootprint_);

      //channels
      ChannelGroupCombo_.setSelectedItem(settings.channelGroup_);
      //make sure the table has a reference to the current channels
      ((SimpleChannelTableModel) channelsTable_.getModel()).setChannels(settings.channels_);
      ((SimpleChannelTableModel) channelsTable_.getModel()).fireTableDataChanged();

      enableAndChangeFonts();

      repaint();
      storeAcqSettings_ = true;
   }

   private void addGlobalSettingsListeners() {
      globalSavingDirTextField_.getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void insertUpdate(DocumentEvent e) {
            settings_.storeSavingDirectory(globalSavingDirTextField_.getText());
            acquisitionSettingsChanged();
         }

         @Override
         public void removeUpdate(DocumentEvent e) {
            settings_.storeSavingDirectory(globalSavingDirTextField_.getText());
            acquisitionSettingsChanged();
         }

         @Override
         public void changedUpdate(DocumentEvent e) {
            settings_.storeSavingDirectory(globalSavingDirTextField_.getText());
            acquisitionSettingsChanged();
         }
      });
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
      runAcqButton_.setText(enable ? "Run acquisition(s)" : "Abort acquisiton(s)");
      repaint();
      acquisitionRunning_ = !enable;
   }
   
   public String getSavingDir() {
      if (globalSavingDirTextField_ == null) {
         return null;
      }
      return globalSavingDirTextField_.getText();
   }

   /**
    * Channel offsets must be within 9 of eachother
    */
   public void validateChannelOffsets() {
      int minOffset = 200, maxOffset = -200;
      for (JSpinner s : offsetSpinners_) {
         minOffset = Math.min(((Number) s.getValue()).intValue(), minOffset);
         maxOffset = Math.min(((Number) s.getValue()).intValue(), maxOffset);
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
      z2DButtonGroup_ = new javax.swing.ButtonGroup();
      acq2D3DButtonGroup_ = new javax.swing.ButtonGroup();
      root_panel_ = new javax.swing.JPanel();
      exploreAcqTabbedPane_ = new javax.swing.JTabbedPane();
      explorePanel = new javax.swing.JPanel();
      exploreZStepLabel_ = new javax.swing.JLabel();
      exploreZStepSpinner_ = new javax.swing.JSpinner();
      channelGroupLabel_ = new javax.swing.JLabel();
      exploreChannelGroupCombo_ = new javax.swing.JComboBox();
      exploreOverlapLabel_ = new javax.swing.JLabel();
      exploreTileOverlapSpinner_ = new javax.swing.JSpinner();
      explorePercentLabel_ = new javax.swing.JLabel();
      exploreSavingNameLabel_ = new javax.swing.JLabel();
      exploreSavingNameTextField_ = new javax.swing.JTextField();
      newExploreWindowButton_ = new javax.swing.JButton();
      surfaceAndGridsPanel_ = new javax.swing.JPanel();
      deleteAllRegionsButton_ = new javax.swing.JButton();
      deleteSelectedRegionButton_ = new javax.swing.JButton();
      jScrollPane2 = new javax.swing.JScrollPane();
      surfacesAndGridsTable_ = new javax.swing.JTable();
      jButton2 = new javax.swing.JButton();
      jButton3 = new javax.swing.JButton();
      surfacesAndGrdisLabel_ = new javax.swing.JLabel();
      acqPanel = new javax.swing.JPanel();
      acqTabbedPane_ = new javax.swing.JTabbedPane();
      spaceTab_ = new javax.swing.JPanel();
      button3D_ = new javax.swing.JRadioButton();
      button2D_ = new javax.swing.JRadioButton();
      controls2DOr3D_ = new javax.swing.JPanel();
      panel2dControlsSpecific_ = new javax.swing.JPanel();
      panel2D_ = new javax.swing.JPanel();
      collectionPlaneCombo_ = new javax.swing.JComboBox();
      collectionPlaneLabel_ = new javax.swing.JLabel();
      noCollectionPlaneButton_ = new javax.swing.JRadioButton();
      useCollectionPlaneButton_ = new javax.swing.JRadioButton();
      labelDiagram2dSimple_ = new javax.swing.JLabel();
      labelDiagram2DSurface_ = new javax.swing.JLabel();
      panel3DControlsSpecific_ = new javax.swing.JPanel();
      acq3DSubtypePanel_ = new javax.swing.JPanel();
      simpleZPanel_ = new javax.swing.JPanel();
      zStartLabel = new javax.swing.JLabel();
      zEndLabel = new javax.swing.JLabel();
      zStartSpinner_ = new javax.swing.JSpinner();
      zEndSpinner_ = new javax.swing.JSpinner();
      setCurrentZStartButton_ = new javax.swing.JButton();
      setCurrentZEndButton_ = new javax.swing.JButton();
      labelDiagram3dSimple_ = new javax.swing.JLabel();
      volumeBetweenZPanel_ = new javax.swing.JPanel();
      topSurfaceLabel_ = new javax.swing.JLabel();
      bottomSurfaceLabel_ = new javax.swing.JLabel();
      topSurfaceCombo_ = new javax.swing.JComboBox();
      bottomSurfaceCombo_ = new javax.swing.JComboBox();
      umAboveTopSurfaceSpinner_ = new javax.swing.JSpinner();
      umAboveVolBetweenLabel_ = new javax.swing.JLabel();
      umBelowBottomSurfaceSpinner_ = new javax.swing.JSpinner();
      umBelowVolBetweenLabel_ = new javax.swing.JLabel();
      labelDiagram3d2Surface_ = new javax.swing.JLabel();
      fixedDistanceZPanel_ = new javax.swing.JPanel();
      distanceBelowSurfaceLabel_ = new javax.swing.JLabel();
      distanceBelowFixedSurfaceSpinner_ = new javax.swing.JSpinner();
      distanceAboveSurfaceLabel_ = new javax.swing.JLabel();
      distanceAboveFixedSurfaceSpinner_ = new javax.swing.JSpinner();
      umAboveLabel_ = new javax.swing.JLabel();
      umBelowLabel_ = new javax.swing.JLabel();
      fixedSurfaceLabel_ = new javax.swing.JLabel();
      fixedDistanceSurfaceComboBox_ = new javax.swing.JComboBox();
      labelDiagram3dSurface_ = new javax.swing.JLabel();
      zStepLabel_ = new javax.swing.JLabel();
      zStepSpinner_ = new javax.swing.JSpinner();
      acqOrderLabel_ = new javax.swing.JLabel();
      acqOrderCombo_ = new javax.swing.JComboBox();
      withinDistanceFromSurfacesButton_ = new javax.swing.JRadioButton();
      volumeBetweenSurfacesButton_ = new javax.swing.JRadioButton();
      cuboidVolumeButton_ = new javax.swing.JRadioButton();
      tileOverlapPercentLabel_ = new javax.swing.JLabel();
      acqOverlapPercentSpinner_ = new javax.swing.JSpinner();
      acqTileOverlapLabel_ = new javax.swing.JLabel();
      footprin2DLabel_ = new javax.swing.JLabel();
      xyFootprintComboBox_ = new javax.swing.JComboBox();
      ChannelsTab_ = new javax.swing.JPanel();
      jScrollPane1 = new javax.swing.JScrollPane();
      channelsTable_ = new javax.swing.JTable();
      jLabel3 = new javax.swing.JLabel();
      ChannelGroupCombo_ = new javax.swing.JComboBox();
      jButton1 = new javax.swing.JButton();
      syncExposuresButton_ = new javax.swing.JButton();
      timePointsTab_ = new javax.swing.JPanel();
      timePointsPanel_ = new javax.swing.JPanel();
      timeIntevalUnitCombo_ = new javax.swing.JComboBox();
      timeIntervalLabel_ = new javax.swing.JLabel();
      numTimePointsLabel_ = new javax.swing.JLabel();
      numTimePointsSpinner_ = new javax.swing.JSpinner();
      timeIntervalSpinner_ = new javax.swing.JSpinner();
      timePointsCheckBox_ = new javax.swing.JCheckBox();
      runAcqPanel_ = new javax.swing.JPanel();
      runAcqButton_ = new javax.swing.JButton();
      estDurationLabel_ = new javax.swing.JLabel();
      estSizeLabel_ = new javax.swing.JLabel();
      jPanel2 = new javax.swing.JPanel();
      multipleAcqScrollPane_ = new javax.swing.JScrollPane();
      multipleAcqTable_ = new javax.swing.JTable();
      jPanel3 = new javax.swing.JPanel();
      moveAcqUpButton_ = new javax.swing.JButton();
      removeAcqButton_ = new javax.swing.JButton();
      moveAcqDownButton_ = new javax.swing.JButton();
      addAcqButton_ = new javax.swing.JButton();
      bottomPanel_ = new javax.swing.JPanel();
      userGuideLink_ = new javax.swing.JLabel();
      citeLink_ = new javax.swing.JLabel();
      bugReportLink_ = new javax.swing.JLabel();
      jPanel1 = new javax.swing.JPanel();
      freeDiskSpaceLabel_ = new javax.swing.JLabel();
      openDatasetButton_ = new javax.swing.JButton();
      exploreBrowseButton_ = new javax.swing.JButton();
      exploreSavingDirLabel_ = new javax.swing.JLabel();
      globalSavingDirTextField_ = new javax.swing.JTextField();

      setBounds(new java.awt.Rectangle(0, 23, 740, 654));
      setMinimumSize(new java.awt.Dimension(730, 650));
      getContentPane().setLayout(new javax.swing.BoxLayout(getContentPane(), javax.swing.BoxLayout.LINE_AXIS));

      exploreAcqTabbedPane_.setPreferredSize(new java.awt.Dimension(727, 525));
      exploreAcqTabbedPane_.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            exploreAcqTabbedPane_StateChanged(evt);
         }
      });

      exploreZStepLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      exploreZStepLabel_.setText("<html>Z-step (&mu;m):</html>");

      exploreZStepSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      exploreZStepSpinner_.setModel(new javax.swing.SpinnerNumberModel(1.0d, null, null, 1.0d));
      exploreZStepSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            exploreZStepSpinner_StateChanged(evt);
         }
      });

      channelGroupLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      channelGroupLabel_.setText("Channel Group (optional): ");

      exploreChannelGroupCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      exploreChannelGroupCombo_.setModel(new ChannelComboBoxModel());

      exploreOverlapLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      exploreOverlapLabel_.setText("XY tile overlap:");

      exploreTileOverlapSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      exploreTileOverlapSpinner_.setModel(new javax.swing.SpinnerNumberModel(0.0d, 0.0d, 99.0d, 1.0d));

      explorePercentLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      explorePercentLabel_.setText("%");

      exploreSavingNameLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      exploreSavingNameLabel_.setText("Saving name: ");

      exploreSavingNameTextField_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      exploreSavingNameTextField_.setText("jTextField2");
      exploreSavingNameTextField_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            exploreSavingNameTextField_ActionPerformed(evt);
         }
      });

      newExploreWindowButton_.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
      newExploreWindowButton_.setText("Explore!");
      newExploreWindowButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            newExploreWindowButton_ActionPerformed(evt);
         }
      });

      deleteAllRegionsButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      deleteAllRegionsButton_.setText("Delete all");
      deleteAllRegionsButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            deleteAllRegionsButton_ActionPerformed(evt);
         }
      });

      deleteSelectedRegionButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      deleteSelectedRegionButton_.setText("Delete selected");
      deleteSelectedRegionButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            deleteSelectedRegionButton_ActionPerformed(evt);
         }
      });

      surfacesAndGridsTable_.setModel(new SurfaceGridTableModel());
      surfacesAndGridsTable_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
      jScrollPane2.setViewportView(surfacesAndGridsTable_);

      jButton2.setText("Export selected to micro-manager");
      jButton2.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            jButton2ActionPerformed(evt);
         }
      });

      jButton3.setText("Export all");
      jButton3.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            jButton3ActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout surfaceAndGridsPanel_Layout = new javax.swing.GroupLayout(surfaceAndGridsPanel_);
      surfaceAndGridsPanel_.setLayout(surfaceAndGridsPanel_Layout);
      surfaceAndGridsPanel_Layout.setHorizontalGroup(
         surfaceAndGridsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(surfaceAndGridsPanel_Layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(deleteSelectedRegionButton_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(deleteAllRegionsButton_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jButton2)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jButton3)
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
         .addGroup(surfaceAndGridsPanel_Layout.createSequentialGroup()
            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 668, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGap(0, 32, Short.MAX_VALUE))
      );
      surfaceAndGridsPanel_Layout.setVerticalGroup(
         surfaceAndGridsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(surfaceAndGridsPanel_Layout.createSequentialGroup()
            .addGap(20, 20, 20)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 286, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(surfaceAndGridsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(deleteSelectedRegionButton_)
               .addComponent(deleteAllRegionsButton_)
               .addComponent(jButton2)
               .addComponent(jButton3))
            .addContainerGap())
      );

      surfacesAndGrdisLabel_.setFont(new java.awt.Font("Lucida Grande", 1, 18)); // NOI18N
      surfacesAndGrdisLabel_.setText("Surfaces and Grids");

      javax.swing.GroupLayout explorePanelLayout = new javax.swing.GroupLayout(explorePanel);
      explorePanel.setLayout(explorePanelLayout);
      explorePanelLayout.setHorizontalGroup(
         explorePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(explorePanelLayout.createSequentialGroup()
            .addContainerGap()
            .addGroup(explorePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addComponent(surfaceAndGridsPanel_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
               .addGroup(explorePanelLayout.createSequentialGroup()
                  .addComponent(exploreSavingNameLabel_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(exploreSavingNameTextField_))
               .addGroup(explorePanelLayout.createSequentialGroup()
                  .addGroup(explorePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(explorePanelLayout.createSequentialGroup()
                        .addComponent(exploreZStepLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exploreZStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exploreOverlapLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exploreTileOverlapSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(explorePercentLabel_)
                        .addGap(102, 102, 102)
                        .addComponent(newExploreWindowButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE))
                     .addGroup(explorePanelLayout.createSequentialGroup()
                        .addGap(8, 8, 8)
                        .addComponent(surfacesAndGrdisLabel_))
                     .addGroup(explorePanelLayout.createSequentialGroup()
                        .addComponent(channelGroupLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exploreChannelGroupCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 172, javax.swing.GroupLayout.PREFERRED_SIZE)))
                  .addGap(0, 0, Short.MAX_VALUE)))
            .addContainerGap())
      );
      explorePanelLayout.setVerticalGroup(
         explorePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(explorePanelLayout.createSequentialGroup()
            .addGroup(explorePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(explorePanelLayout.createSequentialGroup()
                  .addContainerGap()
                  .addGroup(explorePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                     .addComponent(exploreSavingNameTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(exploreSavingNameLabel_))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addGroup(explorePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                     .addComponent(exploreZStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(exploreZStepLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(exploreOverlapLabel_)
                     .addComponent(exploreTileOverlapSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(explorePercentLabel_))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addGroup(explorePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                     .addComponent(channelGroupLabel_)
                     .addComponent(exploreChannelGroupCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
               .addGroup(explorePanelLayout.createSequentialGroup()
                  .addGap(53, 53, 53)
                  .addComponent(newExploreWindowButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addGap(18, 18, 18)
            .addComponent(surfacesAndGrdisLabel_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(surfaceAndGridsPanel_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      addTextEditListener(zStepSpinner_);

      exploreAcqTabbedPane_.addTab("Explore", explorePanel);

      acqTabbedPane_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N

      acq2D3DButtonGroup_.add(button3D_);
      button3D_.setSelected(true);
      button3D_.setText("3D");
      button3D_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            button3D_ActionPerformed(evt);
         }
      });

      acq2D3DButtonGroup_.add(button2D_);
      button2D_.setText("2D");
      button2D_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            button2D_ActionPerformed(evt);
         }
      });

      controls2DOr3D_.setLayout(new java.awt.CardLayout());

      collectionPlaneCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      collectionPlaneCombo_.setModel(new SurfaceGridComboBoxModel(true, false));
      collectionPlaneCombo_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            collectionPlaneCombo_ActionPerformed(evt);
         }
      });

      z2DButtonGroup_.add(noCollectionPlaneButton_);
      noCollectionPlaneButton_.setText("Use current Z position");
      noCollectionPlaneButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            noCollectionPlaneButton_ActionPerformed(evt);
         }
      });

      z2DButtonGroup_.add(useCollectionPlaneButton_);
      useCollectionPlaneButton_.setText("Get Z position from surface");
      useCollectionPlaneButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            useCollectionPlaneButton_ActionPerformed(evt);
         }
      });

      labelDiagram2dSimple_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/magellan/2dsimple.png"))); // NOI18N
      labelDiagram2dSimple_.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 4));

      labelDiagram2DSurface_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/magellan/2dsurface.png"))); // NOI18N
      labelDiagram2DSurface_.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0), 4));

      javax.swing.GroupLayout panel2D_Layout = new javax.swing.GroupLayout(panel2D_);
      panel2D_.setLayout(panel2D_Layout);
      panel2D_Layout.setHorizontalGroup(
         panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(panel2D_Layout.createSequentialGroup()
            .addGroup(panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(panel2D_Layout.createSequentialGroup()
                  .addGroup(panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                     .addGroup(panel2D_Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(collectionPlaneCombo_, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                     .addComponent(useCollectionPlaneButton_))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(labelDiagram2DSurface_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(collectionPlaneLabel_))
               .addGroup(panel2D_Layout.createSequentialGroup()
                  .addComponent(noCollectionPlaneButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 189, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(labelDiagram2dSimple_)))
            .addContainerGap(58, Short.MAX_VALUE))
      );
      panel2D_Layout.setVerticalGroup(
         panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(panel2D_Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addComponent(noCollectionPlaneButton_)
               .addComponent(labelDiagram2dSimple_))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(panel2D_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(panel2D_Layout.createSequentialGroup()
                  .addComponent(useCollectionPlaneButton_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(collectionPlaneCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(28, 28, 28)
                  .addComponent(collectionPlaneLabel_))
               .addComponent(labelDiagram2DSurface_))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      javax.swing.GroupLayout panel2dControlsSpecific_Layout = new javax.swing.GroupLayout(panel2dControlsSpecific_);
      panel2dControlsSpecific_.setLayout(panel2dControlsSpecific_Layout);
      panel2dControlsSpecific_Layout.setHorizontalGroup(
         panel2dControlsSpecific_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(panel2dControlsSpecific_Layout.createSequentialGroup()
            .addComponent(panel2D_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGap(0, 17, Short.MAX_VALUE))
      );
      panel2dControlsSpecific_Layout.setVerticalGroup(
         panel2dControlsSpecific_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(panel2dControlsSpecific_Layout.createSequentialGroup()
            .addComponent(panel2D_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGap(0, 0, Short.MAX_VALUE))
      );

      controls2DOr3D_.add(panel2dControlsSpecific_, "2D");

      acq3DSubtypePanel_.setLayout(new java.awt.CardLayout());

      zStartLabel.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      zStartLabel.setText("<html>Z-start (&mu;m)</html>");

      zEndLabel.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      zEndLabel.setText("<html>Z-end (&mu;m)</html>");

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

      setCurrentZStartButton_.setText("Set current Z");
      setCurrentZStartButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            setCurrentZStartButton_ActionPerformed(evt);
         }
      });

      setCurrentZEndButton_.setText("Set current Z");
      setCurrentZEndButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            setCurrentZEndButton_ActionPerformed(evt);
         }
      });

      labelDiagram3dSimple_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/magellan/3dsimple.png"))); // NOI18N
      labelDiagram3dSimple_.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 128, 0), 4, true));

      javax.swing.GroupLayout simpleZPanel_Layout = new javax.swing.GroupLayout(simpleZPanel_);
      simpleZPanel_.setLayout(simpleZPanel_Layout);
      simpleZPanel_Layout.setHorizontalGroup(
         simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(simpleZPanel_Layout.createSequentialGroup()
            .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(simpleZPanel_Layout.createSequentialGroup()
                  .addContainerGap()
                  .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                     .addGroup(simpleZPanel_Layout.createSequentialGroup()
                        .addComponent(zStartLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(zStartSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE))
                     .addGroup(simpleZPanel_Layout.createSequentialGroup()
                        .addComponent(zEndLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(zEndSpinner_))))
               .addComponent(setCurrentZStartButton_)
               .addComponent(setCurrentZEndButton_))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(labelDiagram3dSimple_, javax.swing.GroupLayout.PREFERRED_SIZE, 295, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGap(0, 0, 0))
      );
      simpleZPanel_Layout.setVerticalGroup(
         simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(simpleZPanel_Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
               .addGroup(simpleZPanel_Layout.createSequentialGroup()
                  .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                     .addComponent(zStartLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(zStartSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                  .addGap(2, 2, 2)
                  .addComponent(setCurrentZStartButton_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addGroup(simpleZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                     .addComponent(zEndLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(zEndSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .addComponent(setCurrentZEndButton_))
               .addComponent(labelDiagram3dSimple_))
            .addContainerGap(25, Short.MAX_VALUE))
      );

      addTextEditListener(zStartSpinner_);
      addTextEditListener(zEndSpinner_);

      acq3DSubtypePanel_.add(simpleZPanel_, "cuboid");

      topSurfaceLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      topSurfaceLabel_.setText("Z-start");

      bottomSurfaceLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      bottomSurfaceLabel_.setText("Z-end");

      topSurfaceCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      topSurfaceCombo_.setModel(new SurfaceGridComboBoxModel(true, false));
      topSurfaceCombo_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            topSurfaceCombo_ActionPerformed(evt);
         }
      });

      bottomSurfaceCombo_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      bottomSurfaceCombo_.setModel(new SurfaceGridComboBoxModel(true, false));
      bottomSurfaceCombo_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            bottomSurfaceCombo_ActionPerformed(evt);
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

      labelDiagram3d2Surface_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/magellan/3d2surface.png"))); // NOI18N
      labelDiagram3d2Surface_.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 128, 0), 4, true));

      javax.swing.GroupLayout volumeBetweenZPanel_Layout = new javax.swing.GroupLayout(volumeBetweenZPanel_);
      volumeBetweenZPanel_.setLayout(volumeBetweenZPanel_Layout);
      volumeBetweenZPanel_Layout.setHorizontalGroup(
         volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(volumeBetweenZPanel_Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(volumeBetweenZPanel_Layout.createSequentialGroup()
                  .addComponent(topSurfaceLabel_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(umAboveTopSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(umAboveVolBetweenLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                  .addComponent(topSurfaceCombo_, javax.swing.GroupLayout.Alignment.LEADING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .addComponent(bottomSurfaceCombo_, javax.swing.GroupLayout.Alignment.LEADING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .addGroup(javax.swing.GroupLayout.Alignment.LEADING, volumeBetweenZPanel_Layout.createSequentialGroup()
                     .addComponent(bottomSurfaceLabel_)
                     .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                     .addComponent(umBelowBottomSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                     .addComponent(umBelowVolBetweenLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(labelDiagram3d2Surface_)
            .addGap(0, 0, 0))
      );
      volumeBetweenZPanel_Layout.setVerticalGroup(
         volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(volumeBetweenZPanel_Layout.createSequentialGroup()
            .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(volumeBetweenZPanel_Layout.createSequentialGroup()
                  .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                     .addComponent(topSurfaceLabel_)
                     .addComponent(umAboveTopSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(umAboveVolBetweenLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                  .addGap(3, 3, 3)
                  .addComponent(topSurfaceCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addGroup(volumeBetweenZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                     .addComponent(bottomSurfaceLabel_)
                     .addComponent(umBelowBottomSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(umBelowVolBetweenLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(bottomSurfaceCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(volumeBetweenZPanel_Layout.createSequentialGroup()
                  .addContainerGap()
                  .addComponent(labelDiagram3d2Surface_)))
            .addContainerGap(26, Short.MAX_VALUE))
      );

      acq3DSubtypePanel_.add(volumeBetweenZPanel_, "volumeBetween");

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
      fixedDistanceSurfaceComboBox_.setModel(new SurfaceGridComboBoxModel(true, false));
      fixedDistanceSurfaceComboBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            fixedDistanceSurfaceComboBox_ActionPerformed(evt);
         }
      });

      labelDiagram3dSurface_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/magellan/3dsurface.png"))); // NOI18N
      labelDiagram3dSurface_.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 128, 0), 4, true));

      javax.swing.GroupLayout fixedDistanceZPanel_Layout = new javax.swing.GroupLayout(fixedDistanceZPanel_);
      fixedDistanceZPanel_.setLayout(fixedDistanceZPanel_Layout);
      fixedDistanceZPanel_Layout.setHorizontalGroup(
         fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                  .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, fixedDistanceZPanel_Layout.createSequentialGroup()
                        .addComponent(distanceAboveSurfaceLabel_)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(distanceAboveFixedSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE))
                     .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
                        .addGap(3, 3, 3)
                        .addComponent(distanceBelowSurfaceLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(distanceBelowFixedSurfaceSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 81, javax.swing.GroupLayout.PREFERRED_SIZE)))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addGroup(fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(umAboveLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(umBelowLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
               .addComponent(fixedSurfaceLabel_)
               .addComponent(fixedDistanceSurfaceComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 203, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(labelDiagram3dSurface_, javax.swing.GroupLayout.PREFERRED_SIZE, 270, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGap(0, 0, 0))
      );
      fixedDistanceZPanel_Layout.setVerticalGroup(
         fixedDistanceZPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
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
            .addComponent(fixedSurfaceLabel_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(fixedDistanceSurfaceComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGap(0, 0, Short.MAX_VALUE))
         .addGroup(fixedDistanceZPanel_Layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(labelDiagram3dSurface_, javax.swing.GroupLayout.PREFERRED_SIZE, 125, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addContainerGap(34, Short.MAX_VALUE))
      );

      addTextEditListener(distanceBelowFixedSurfaceSpinner_);
      addTextEditListener(distanceAboveFixedSurfaceSpinner_);

      acq3DSubtypePanel_.add(fixedDistanceZPanel_, "fixedDistance");

      zStepLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      zStepLabel_.setText("<html>Z-step (&mu;m):</html>");

      zStepSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      zStepSpinner_.setModel(new javax.swing.SpinnerNumberModel(1.0d, null, null, 1.0d));
      zStepSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            zStepSpinner_StateChanged(evt);
         }
      });

      acqOrderLabel_.setText("Order:");

      acqOrderCombo_.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Channel, Z", "Z, Channel" }));
      acqOrderCombo_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            acqOrderCombo_ActionPerformed(evt);
         }
      });

      zStackModeButtonGroup_.add(withinDistanceFromSurfacesButton_);
      withinDistanceFromSurfacesButton_.setSelected(true);
      withinDistanceFromSurfacesButton_.setText("<html>Within distance<br>from surface");
      withinDistanceFromSurfacesButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            withinDistanceFromSurfacesButton_ActionPerformed(evt);
         }
      });

      zStackModeButtonGroup_.add(volumeBetweenSurfacesButton_);
      volumeBetweenSurfacesButton_.setText("Between surfaces");
      volumeBetweenSurfacesButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            volumeBetweenSurfacesButton_ActionPerformed(evt);
         }
      });

      zStackModeButtonGroup_.add(cuboidVolumeButton_);
      cuboidVolumeButton_.setText("Cuboid");
      cuboidVolumeButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            cuboidVolumeButton_ActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout panel3DControlsSpecific_Layout = new javax.swing.GroupLayout(panel3DControlsSpecific_);
      panel3DControlsSpecific_.setLayout(panel3DControlsSpecific_Layout);
      panel3DControlsSpecific_Layout.setHorizontalGroup(
         panel3DControlsSpecific_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(panel3DControlsSpecific_Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(panel3DControlsSpecific_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(panel3DControlsSpecific_Layout.createSequentialGroup()
                  .addComponent(zStepLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(zStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(panel3DControlsSpecific_Layout.createSequentialGroup()
                  .addComponent(acqOrderLabel_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .addComponent(acqOrderCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addComponent(cuboidVolumeButton_)
               .addComponent(volumeBetweenSurfacesButton_)
               .addComponent(withinDistanceFromSurfacesButton_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(acq3DSubtypePanel_, javax.swing.GroupLayout.DEFAULT_SIZE, 502, Short.MAX_VALUE)
            .addContainerGap())
      );
      panel3DControlsSpecific_Layout.setVerticalGroup(
         panel3DControlsSpecific_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(panel3DControlsSpecific_Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(panel3DControlsSpecific_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
               .addComponent(acq3DSubtypePanel_, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
               .addGroup(panel3DControlsSpecific_Layout.createSequentialGroup()
                  .addGroup(panel3DControlsSpecific_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                     .addComponent(zStepLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(zStepSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addGroup(panel3DControlsSpecific_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                     .addComponent(acqOrderLabel_)
                     .addComponent(acqOrderCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(cuboidVolumeButton_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(volumeBetweenSurfacesButton_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(withinDistanceFromSurfacesButton_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addContainerGap(9, Short.MAX_VALUE))
      );

      addTextEditListener(zStepSpinner_);

      controls2DOr3D_.add(panel3DControlsSpecific_, "3D");

      tileOverlapPercentLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      tileOverlapPercentLabel_.setText("%");

      acqOverlapPercentSpinner_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      acqOverlapPercentSpinner_.setModel(new javax.swing.SpinnerNumberModel(5.0d, 0.0d, 99.0d, 1.0d));
      acqOverlapPercentSpinner_.addChangeListener(new javax.swing.event.ChangeListener() {
         public void stateChanged(javax.swing.event.ChangeEvent evt) {
            acqOverlapPercentSpinner_StateChanged(evt);
         }
      });

      acqTileOverlapLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      acqTileOverlapLabel_.setText("XY tile overlap:");

      footprin2DLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      footprin2DLabel_.setText("XY stage positions from:");

      xyFootprintComboBox_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      xyFootprintComboBox_.setModel(new SurfaceGridComboBoxModel(false, false));
      xyFootprintComboBox_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            xyFootprintComboBox_ActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout spaceTab_Layout = new javax.swing.GroupLayout(spaceTab_);
      spaceTab_.setLayout(spaceTab_Layout);
      spaceTab_Layout.setHorizontalGroup(
         spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, spaceTab_Layout.createSequentialGroup()
            .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(spaceTab_Layout.createSequentialGroup()
                  .addComponent(button3D_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(button2D_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(footprin2DLabel_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(xyFootprintComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, 221, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(acqTileOverlapLabel_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(acqOverlapPercentSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(tileOverlapPercentLabel_))
               .addGroup(spaceTab_Layout.createSequentialGroup()
                  .addContainerGap()
                  .addComponent(controls2DOr3D_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      spaceTab_Layout.setVerticalGroup(
         spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(spaceTab_Layout.createSequentialGroup()
            .addGroup(spaceTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(button3D_)
               .addComponent(button2D_)
               .addComponent(acqTileOverlapLabel_)
               .addComponent(acqOverlapPercentSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(tileOverlapPercentLabel_)
               .addComponent(footprin2DLabel_)
               .addComponent(xyFootprintComboBox_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(controls2DOr3D_, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addContainerGap())
      );

      acqTabbedPane_.addTab("Space", spaceTab_);

      jScrollPane1.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N

      channelsTable_.setModel(new SimpleChannelTableModel(null, true)
      );
      channelsTable_.getTableHeader().addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            int col = channelsTable_.columnAtPoint(e.getPoint());
            if (col ==0) {
               //Select all
               ((SimpleChannelTableModel) channelsTable_.getModel()).selectAllChannels();
            } else if(col == 2) {
               //set all exposures to exposure of first
               ((SimpleChannelTableModel) channelsTable_.getModel()).synchronizeExposures();
            }
         }
      });
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

      jButton1.setText("Select all");
      jButton1.setToolTipText("Select or deselect all channels");
      jButton1.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            jButton1ActionPerformed(evt);
         }
      });

      syncExposuresButton_.setText("Sync exposures");
      syncExposuresButton_.setToolTipText("Make all exposures equal to the top channel exposures");
      syncExposuresButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            syncExposuresButton_ActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout ChannelsTab_Layout = new javax.swing.GroupLayout(ChannelsTab_);
      ChannelsTab_.setLayout(ChannelsTab_Layout);
      ChannelsTab_Layout.setHorizontalGroup(
         ChannelsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addComponent(jScrollPane1)
         .addGroup(ChannelsTab_Layout.createSequentialGroup()
            .addComponent(jLabel3)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(ChannelGroupCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 171, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGap(35, 35, 35)
            .addComponent(jButton1)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(syncExposuresButton_)
            .addGap(0, 165, Short.MAX_VALUE))
      );
      ChannelsTab_Layout.setVerticalGroup(
         ChannelsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, ChannelsTab_Layout.createSequentialGroup()
            .addGroup(ChannelsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(jLabel3)
               .addComponent(ChannelGroupCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(jButton1)
               .addComponent(syncExposuresButton_))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 192, Short.MAX_VALUE))
      );

      acqTabbedPane_.addTab("Channels", ChannelsTab_);

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
            .addContainerGap()
            .addGroup(timePointsPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
               .addGroup(timePointsPanel_Layout.createSequentialGroup()
                  .addComponent(timeIntervalLabel_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(timeIntervalSpinner_))
               .addGroup(timePointsPanel_Layout.createSequentialGroup()
                  .addComponent(numTimePointsLabel_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(numTimePointsSpinner_, javax.swing.GroupLayout.PREFERRED_SIZE, 87, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(timeIntevalUnitCombo_, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE)
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
            .addGap(0, 9, Short.MAX_VALUE))
      );

      addTextEditListener(numTimePointsSpinner_);
      addTextEditListener(timeIntervalSpinner_);

      timePointsCheckBox_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      timePointsCheckBox_.setText("Use time points");
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
               .addComponent(timePointsPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(timePointsCheckBox_))
            .addContainerGap(454, Short.MAX_VALUE))
      );
      timePointsTab_Layout.setVerticalGroup(
         timePointsTab_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(timePointsTab_Layout.createSequentialGroup()
            .addGap(6, 6, 6)
            .addComponent(timePointsCheckBox_)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(timePointsPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addContainerGap(114, Short.MAX_VALUE))
      );

      for (Component c : timePointsPanel_.getComponents()) {
         c.setEnabled(false);
      }

      acqTabbedPane_.addTab("Time", timePointsTab_);

      runAcqButton_.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
      runAcqButton_.setText("Run acquisition(s)");
      runAcqButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            runAcqButton_ActionPerformed(evt);
         }
      });

      estDurationLabel_.setText("Estimated duration: ");

      estSizeLabel_.setText("Estimated size: ");

      javax.swing.GroupLayout runAcqPanel_Layout = new javax.swing.GroupLayout(runAcqPanel_);
      runAcqPanel_.setLayout(runAcqPanel_Layout);
      runAcqPanel_Layout.setHorizontalGroup(
         runAcqPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(runAcqPanel_Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(runAcqPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addComponent(estDurationLabel_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
               .addComponent(estSizeLabel_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(runAcqButton_)
            .addGap(303, 303, 303))
      );
      runAcqPanel_Layout.setVerticalGroup(
         runAcqPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(runAcqPanel_Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(runAcqPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(runAcqPanel_Layout.createSequentialGroup()
                  .addComponent(estDurationLabel_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .addComponent(estSizeLabel_))
               .addComponent(runAcqButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addContainerGap())
      );

      multipleAcqScrollPane_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N

      multipleAcqTable_.setModel(new MultipleAcquisitionTableModel(multiAcqManager_,this));
      multipleAcqTable_.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
      multipleAcqScrollPane_.setViewportView(multipleAcqTable_);

      moveAcqUpButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      moveAcqUpButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/magellan/arrow_up.png"))); // NOI18N
      moveAcqUpButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            moveAcqUpButton_ActionPerformed(evt);
         }
      });

      removeAcqButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/minus.png"))); // NOI18N
      removeAcqButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            removeAcqButton_ActionPerformed(evt);
         }
      });

      moveAcqDownButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      moveAcqDownButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/magellan/arrow_down.png"))); // NOI18N
      moveAcqDownButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            moveAcqDownButton_ActionPerformed(evt);
         }
      });

      addAcqButton_.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/icons/plus.png"))); // NOI18N
      addAcqButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            addAcqButton_ActionPerformed(evt);
         }
      });

      javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
      jPanel3.setLayout(jPanel3Layout);
      jPanel3Layout.setHorizontalGroup(
         jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(jPanel3Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
               .addComponent(moveAcqUpButton_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
               .addComponent(addAcqButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
               .addComponent(moveAcqDownButton_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
               .addComponent(removeAcqButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addContainerGap())
      );
      jPanel3Layout.setVerticalGroup(
         jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(jPanel3Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
               .addComponent(removeAcqButton_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
               .addComponent(addAcqButton_, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
               .addComponent(moveAcqUpButton_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
               .addComponent(moveAcqDownButton_))
            .addContainerGap())
      );

      javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
      jPanel2.setLayout(jPanel2Layout);
      jPanel2Layout.setHorizontalGroup(
         jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
            .addComponent(multipleAcqScrollPane_, javax.swing.GroupLayout.DEFAULT_SIZE, 574, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
      );
      jPanel2Layout.setVerticalGroup(
         jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(jPanel2Layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(multipleAcqScrollPane_, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
         .addGroup(jPanel2Layout.createSequentialGroup()
            .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addGap(0, 62, Short.MAX_VALUE))
      );

      javax.swing.GroupLayout acqPanelLayout = new javax.swing.GroupLayout(acqPanel);
      acqPanel.setLayout(acqPanelLayout);
      acqPanelLayout.setHorizontalGroup(
         acqPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(acqPanelLayout.createSequentialGroup()
            .addContainerGap()
            .addGroup(acqPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
               .addComponent(runAcqPanel_, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
               .addComponent(acqTabbedPane_, javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(javax.swing.GroupLayout.Alignment.LEADING, acqPanelLayout.createSequentialGroup()
                  .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(0, 0, Short.MAX_VALUE)))
            .addContainerGap())
      );
      acqPanelLayout.setVerticalGroup(
         acqPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(acqPanelLayout.createSequentialGroup()
            .addContainerGap()
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGap(18, 18, 18)
            .addComponent(acqTabbedPane_, javax.swing.GroupLayout.PREFERRED_SIZE, 255, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(runAcqPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addContainerGap())
      );

      exploreAcqTabbedPane_.addTab("Acquisition(s)", acqPanel);

      userGuideLink_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      userGuideLink_.setForeground(new java.awt.Color(153, 204, 255));
      userGuideLink_.setText("Micro-Magellan User Guide");

      citeLink_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      citeLink_.setForeground(new java.awt.Color(153, 204, 255));
      citeLink_.setText("Cite Micro-Magellan");

      bugReportLink_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      bugReportLink_.setForeground(new java.awt.Color(153, 204, 255));
      bugReportLink_.setText("Report a bug");

      userGuideLink_.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      Font font = userGuideLink_.getFont();
      Map attributes = font.getAttributes();
      attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
      userGuideLink_.setFont(font.deriveFont(attributes));
      citeLink_.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      Font font3 = citeLink_.getFont();
      Map attributes3 = font3.getAttributes();
      attributes3.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
      citeLink_.setFont(font3.deriveFont(attributes3));
      bugReportLink_.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      Font font2 = bugReportLink_.getFont();
      Map attributes2 = font2.getAttributes();
      attributes2.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
      bugReportLink_.setFont(font2.deriveFont(attributes2));

      javax.swing.GroupLayout bottomPanel_Layout = new javax.swing.GroupLayout(bottomPanel_);
      bottomPanel_.setLayout(bottomPanel_Layout);
      bottomPanel_Layout.setHorizontalGroup(
         bottomPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(bottomPanel_Layout.createSequentialGroup()
            .addComponent(userGuideLink_)
            .addGap(82, 82, 82)
            .addComponent(citeLink_)
            .addGap(119, 119, 119)
            .addComponent(bugReportLink_)
            .addContainerGap(143, Short.MAX_VALUE))
      );
      bottomPanel_Layout.setVerticalGroup(
         bottomPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(bottomPanel_Layout.createSequentialGroup()
            .addContainerGap(14, Short.MAX_VALUE)
            .addGroup(bottomPanel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(citeLink_)
               .addComponent(userGuideLink_)
               .addComponent(bugReportLink_)))
      );

      freeDiskSpaceLabel_.setText("Available disk space: ");
      freeDiskSpaceLabel_.setToolTipText("");

      openDatasetButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      openDatasetButton_.setText("Open dataset");
      openDatasetButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            openDatasetButton_ActionPerformed(evt);
         }
      });

      exploreBrowseButton_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      exploreBrowseButton_.setText("Browse");
      exploreBrowseButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            exploreBrowseButton_ActionPerformed(evt);
         }
      });

      exploreSavingDirLabel_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      exploreSavingDirLabel_.setText("Saving directory: ");

      globalSavingDirTextField_.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
      globalSavingDirTextField_.setText("jTextField1");

      javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
      jPanel1.setLayout(jPanel1Layout);
      jPanel1Layout.setHorizontalGroup(
         jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(jPanel1Layout.createSequentialGroup()
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addComponent(exploreSavingDirLabel_)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(globalSavingDirTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, 578, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(jPanel1Layout.createSequentialGroup()
                  .addContainerGap()
                  .addComponent(openDatasetButton_)
                  .addGap(127, 127, 127)
                  .addComponent(freeDiskSpaceLabel_, javax.swing.GroupLayout.PREFERRED_SIZE, 197, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                  .addComponent(exploreBrowseButton_)))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      jPanel1Layout.setVerticalGroup(
         jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(jPanel1Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(exploreSavingDirLabel_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
               .addComponent(globalSavingDirTextField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(openDatasetButton_)
               .addComponent(exploreBrowseButton_)
               .addComponent(freeDiskSpaceLabel_))
            .addContainerGap())
      );

      javax.swing.GroupLayout root_panel_Layout = new javax.swing.GroupLayout(root_panel_);
      root_panel_.setLayout(root_panel_Layout);
      root_panel_Layout.setHorizontalGroup(
         root_panel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(root_panel_Layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(root_panel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addComponent(exploreAcqTabbedPane_, javax.swing.GroupLayout.PREFERRED_SIZE, 714, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
               .addComponent(bottomPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addContainerGap())
      );
      root_panel_Layout.setVerticalGroup(
         root_panel_Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, root_panel_Layout.createSequentialGroup()
            .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(exploreAcqTabbedPane_, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(bottomPanel_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addContainerGap())
      );

      getContentPane().add(root_panel_);

      pack();
   }// </editor-fold>//GEN-END:initComponents

   private void runAcqButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runAcqButton_ActionPerformed
      if (acquisitionRunning_) {
         multiAcqManager_.abort();
      } else {
         multiAcqManager_.runAllAcquisitions();
      }
   }//GEN-LAST:event_runAcqButton_ActionPerformed

   private void newExploreWindowButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newExploreWindowButton_ActionPerformed
      if (!MagellanAffineUtils.isAffineTransformDefined()) {
         ReportingUtils.showError("XY Stage and Camera are not calibrated to each other."
                 + " \nOpen \"Devices--Pixel size calibration\" and set up Affine transform");
         throw new RuntimeException();
      }
      ExploreAcqSettings settings = new ExploreAcqSettings(
              ((Number) exploreZStepSpinner_.getValue()).doubleValue(), (Double) exploreTileOverlapSpinner_.getValue(),
              globalSavingDirTextField_.getText(), exploreSavingNameTextField_.getText(), (String) exploreChannelGroupCombo_.getSelectedItem());
      //check for abort of existing explore acquisition
      //abort existing explore acq if needed
      if (exploreAcq_ != null && !exploreAcq_.isFinished()) {
         int result = JOptionPane.showConfirmDialog(null, "Finish exisiting explore acquisition?", "Finish Current Explore Acquisition", JOptionPane.OK_CANCEL_OPTION);
         if (result == JOptionPane.OK_OPTION) {
            exploreAcq_.abort();
         } else {
            return;
         }
      }
      exploreAcq_ = new ExploreAcquisition(settings);
      exploreAcq_.start();
   }//GEN-LAST:event_newExploreWindowButton_ActionPerformed

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
      acquisitionSettingsChanged();
   }//GEN-LAST:event_exploreBrowseButton_ActionPerformed

   private void exploreSavingNameTextField_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exploreSavingNameTextField_ActionPerformed
   }//GEN-LAST:event_exploreSavingNameTextField_ActionPerformed

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
            //TODO
            new LoadedAcquisitionData(finalFile.toString());
         }
      }).start();

   }//GEN-LAST:event_openDatasetButton_ActionPerformed

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

   private void ChannelGroupCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ChannelGroupCombo_ActionPerformed
      //update the current channels object displayed in the GUI
      multiAcqManager_.getAcquisitionSettings(multiAcqSelectedIndex_).channelGroup_ = (String) ChannelGroupCombo_.getSelectedItem();
      multiAcqManager_.getAcquisitionSettings(multiAcqSelectedIndex_).channels_.updateChannelGroup((String) ChannelGroupCombo_.getSelectedItem());
      acquisitionSettingsChanged();
   }//GEN-LAST:event_ChannelGroupCombo_ActionPerformed

   private void acqOrderCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_acqOrderCombo_ActionPerformed
      acquisitionSettingsChanged();
   }//GEN-LAST:event_acqOrderCombo_ActionPerformed

   private void acqOverlapPercentSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_acqOverlapPercentSpinner_StateChanged
      acquisitionSettingsChanged();
      //update any grids/surface shown
      manager_.updateAll();
   }//GEN-LAST:event_acqOverlapPercentSpinner_StateChanged

   private void zStepSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zStepSpinner_StateChanged
      acquisitionSettingsChanged();
   }//GEN-LAST:event_zStepSpinner_StateChanged

   private void collectionPlaneCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_collectionPlaneCombo_ActionPerformed
      acquisitionSettingsChanged();
   }//GEN-LAST:event_collectionPlaneCombo_ActionPerformed

   private void xyFootprintComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_xyFootprintComboBox_ActionPerformed
      acquisitionSettingsChanged();
   }//GEN-LAST:event_xyFootprintComboBox_ActionPerformed

   private void fixedDistanceSurfaceComboBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fixedDistanceSurfaceComboBox_ActionPerformed
      acquisitionSettingsChanged();
   }//GEN-LAST:event_fixedDistanceSurfaceComboBox_ActionPerformed

   private void distanceAboveFixedSurfaceSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_distanceAboveFixedSurfaceSpinner_StateChanged
      acquisitionSettingsChanged();
   }//GEN-LAST:event_distanceAboveFixedSurfaceSpinner_StateChanged

   private void distanceBelowFixedSurfaceSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_distanceBelowFixedSurfaceSpinner_StateChanged
      acquisitionSettingsChanged();
   }//GEN-LAST:event_distanceBelowFixedSurfaceSpinner_StateChanged

   private void umBelowBottomSurfaceSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_umBelowBottomSurfaceSpinner_StateChanged
      acquisitionSettingsChanged();
   }//GEN-LAST:event_umBelowBottomSurfaceSpinner_StateChanged

   private void umAboveTopSurfaceSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_umAboveTopSurfaceSpinner_StateChanged
      acquisitionSettingsChanged();
   }//GEN-LAST:event_umAboveTopSurfaceSpinner_StateChanged

   private void bottomSurfaceCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bottomSurfaceCombo_ActionPerformed
      acquisitionSettingsChanged();
   }//GEN-LAST:event_bottomSurfaceCombo_ActionPerformed

   private void topSurfaceCombo_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_topSurfaceCombo_ActionPerformed
      acquisitionSettingsChanged();
   }//GEN-LAST:event_topSurfaceCombo_ActionPerformed

   private void zEndSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zEndSpinner_StateChanged
      acquisitionSettingsChanged();
   }//GEN-LAST:event_zEndSpinner_StateChanged

   private void zStartSpinner_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_zStartSpinner_StateChanged
      acquisitionSettingsChanged();
   }//GEN-LAST:event_zStartSpinner_StateChanged

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

   private void deleteAllRegionsButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteAllRegionsButton_ActionPerformed
      manager_.deleteAll();
   }//GEN-LAST:event_deleteAllRegionsButton_ActionPerformed

   private void deleteSelectedRegionButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteSelectedRegionButton_ActionPerformed
      if (surfacesAndGridsTable_.getSelectedRow() != -1) {
         manager_.delete(surfacesAndGridsTable_.getSelectedRow());
      }
   }//GEN-LAST:event_deleteSelectedRegionButton_ActionPerformed

   private void setCurrentZStartButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setCurrentZStartButton_ActionPerformed
      MagellanGUIAcquisitionSettings settings = multiAcqManager_.getAcquisitionSettings(multiAcqSelectedIndex_);
      try {
         settings.zStart_ = Magellan.getCore().getPosition();
         zStartSpinner_.setValue(settings.zStart_);
      } catch (Exception ex) {
         Log.log(ex);
      }
      acquisitionSettingsChanged();
   }//GEN-LAST:event_setCurrentZStartButton_ActionPerformed

   private void setCurrentZEndButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setCurrentZEndButton_ActionPerformed
      MagellanGUIAcquisitionSettings settings = multiAcqManager_.getAcquisitionSettings(multiAcqSelectedIndex_);
      try {
         settings.zEnd_ = Magellan.getCore().getPosition();
         zEndSpinner_.setValue(settings.zEnd_);
      } catch (Exception ex) {
         Log.log(ex);
      }
      acquisitionSettingsChanged();
   }//GEN-LAST:event_setCurrentZEndButton_ActionPerformed

   private void noCollectionPlaneButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_noCollectionPlaneButton_ActionPerformed
      acquisitionSettingsChanged();
   }//GEN-LAST:event_noCollectionPlaneButton_ActionPerformed

   private void useCollectionPlaneButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useCollectionPlaneButton_ActionPerformed
      acquisitionSettingsChanged();
   }//GEN-LAST:event_useCollectionPlaneButton_ActionPerformed

   private void button3D_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_button3D_ActionPerformed
      CardLayout card1 = (CardLayout) controls2DOr3D_.getLayout();
      card1.show(controls2DOr3D_, "3D");
      acquisitionSettingsChanged();
   }//GEN-LAST:event_button3D_ActionPerformed

   private void button2D_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_button2D_ActionPerformed
      CardLayout card1 = (CardLayout) controls2DOr3D_.getLayout();
      card1.show(controls2DOr3D_, "2D");
      acquisitionSettingsChanged();
   }//GEN-LAST:event_button2D_ActionPerformed

   private void cuboidVolumeButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cuboidVolumeButton_ActionPerformed
      CardLayout card1 = (CardLayout) acq3DSubtypePanel_.getLayout();
      card1.show(acq3DSubtypePanel_, "cuboid");
      acquisitionSettingsChanged();
   }//GEN-LAST:event_cuboidVolumeButton_ActionPerformed

   private void volumeBetweenSurfacesButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_volumeBetweenSurfacesButton_ActionPerformed
      CardLayout card1 = (CardLayout) acq3DSubtypePanel_.getLayout();
      card1.show(acq3DSubtypePanel_, "volumeBetween");
      acquisitionSettingsChanged();
   }//GEN-LAST:event_volumeBetweenSurfacesButton_ActionPerformed

   private void withinDistanceFromSurfacesButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_withinDistanceFromSurfacesButton_ActionPerformed
      CardLayout card1 = (CardLayout) acq3DSubtypePanel_.getLayout();
      card1.show(acq3DSubtypePanel_, "fixedDistance");
      acquisitionSettingsChanged();
   }//GEN-LAST:event_withinDistanceFromSurfacesButton_ActionPerformed

   private void exploreAcqTabbedPane_StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_exploreAcqTabbedPane_StateChanged
      refreshBoldedText();
   }//GEN-LAST:event_exploreAcqTabbedPane_StateChanged

   private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
      multiAcqManager_.getAcquisition(multiAcqSelectedIndex_).getChannels().setUseOnAll(
              !multiAcqManager_.getAcquisition(multiAcqSelectedIndex_).getChannels().getChannelListSetting(0).use_);
      ((SimpleChannelTableModel) channelsTable_.getModel()).fireTableDataChanged();
   }//GEN-LAST:event_jButton1ActionPerformed

   private void syncExposuresButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_syncExposuresButton_ActionPerformed
      multiAcqManager_.getAcquisition(multiAcqSelectedIndex_).getChannels().synchronizeExposures();
      ((SimpleChannelTableModel) channelsTable_.getModel()).fireTableDataChanged();
   }//GEN-LAST:event_syncExposuresButton_ActionPerformed

   private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
      int index = surfacesAndGridsTable_.getSelectedRow();
      if (index != -1) {
         SurfaceGridManager.getInstance().getSurfaceOrGrid(index).exportToMicroManager();
      }
   }//GEN-LAST:event_jButton2ActionPerformed

   private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
      for (int index = 0; index < SurfaceGridManager.getInstance().getNumberOfSurfaces() + SurfaceGridManager.getInstance().getNumberOfGrids(); index++) {
         SurfaceGridManager.getInstance().getSurfaceOrGrid(index).exportToMicroManager();
      }
   }//GEN-LAST:event_jButton3ActionPerformed

   private void addAcqButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addAcqButton_ActionPerformed
        multiAcqManager_.addNew();
      acquisitionSettingsChanged();
   }//GEN-LAST:event_addAcqButton_ActionPerformed

   private void removeAcqButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeAcqButton_ActionPerformed
           multiAcqManager_.remove(multipleAcqTable_.getSelectedRow());
      if (multiAcqSelectedIndex_ == multiAcqManager_.getNumberOfAcquisitions()) {
         multiAcqSelectedIndex_--;
         multipleAcqTable_.getSelectionModel().setSelectionInterval(multiAcqSelectedIndex_, multiAcqSelectedIndex_);
      }
      acquisitionSettingsChanged();
   }//GEN-LAST:event_removeAcqButton_ActionPerformed

   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JComboBox ChannelGroupCombo_;
   private javax.swing.JPanel ChannelsTab_;
   private javax.swing.ButtonGroup acq2D3DButtonGroup_;
   private javax.swing.JPanel acq3DSubtypePanel_;
   private javax.swing.JComboBox acqOrderCombo_;
   private javax.swing.JLabel acqOrderLabel_;
   private javax.swing.JSpinner acqOverlapPercentSpinner_;
   private javax.swing.JPanel acqPanel;
   private javax.swing.JTabbedPane acqTabbedPane_;
   private javax.swing.JLabel acqTileOverlapLabel_;
   private javax.swing.JButton addAcqButton_;
   private javax.swing.JPanel bottomPanel_;
   private javax.swing.JComboBox bottomSurfaceCombo_;
   private javax.swing.JLabel bottomSurfaceLabel_;
   private javax.swing.JLabel bugReportLink_;
   private javax.swing.JRadioButton button2D_;
   private javax.swing.JRadioButton button3D_;
   private javax.swing.JLabel channelGroupLabel_;
   private javax.swing.JTable channelsTable_;
   private javax.swing.JLabel citeLink_;
   private javax.swing.JComboBox collectionPlaneCombo_;
   private javax.swing.JLabel collectionPlaneLabel_;
   private javax.swing.JPanel controls2DOr3D_;
   private javax.swing.JRadioButton cuboidVolumeButton_;
   private javax.swing.JButton deleteAllRegionsButton_;
   private javax.swing.JButton deleteSelectedRegionButton_;
   private javax.swing.JSpinner distanceAboveFixedSurfaceSpinner_;
   private javax.swing.JLabel distanceAboveSurfaceLabel_;
   private javax.swing.JSpinner distanceBelowFixedSurfaceSpinner_;
   private javax.swing.JLabel distanceBelowSurfaceLabel_;
   private javax.swing.JLabel estDurationLabel_;
   private javax.swing.JLabel estSizeLabel_;
   private javax.swing.JTabbedPane exploreAcqTabbedPane_;
   private javax.swing.JButton exploreBrowseButton_;
   private javax.swing.JComboBox exploreChannelGroupCombo_;
   private javax.swing.JLabel exploreOverlapLabel_;
   private javax.swing.JPanel explorePanel;
   private javax.swing.JLabel explorePercentLabel_;
   private javax.swing.JLabel exploreSavingDirLabel_;
   private javax.swing.JLabel exploreSavingNameLabel_;
   private javax.swing.JTextField exploreSavingNameTextField_;
   private javax.swing.JSpinner exploreTileOverlapSpinner_;
   private javax.swing.JLabel exploreZStepLabel_;
   private javax.swing.JSpinner exploreZStepSpinner_;
   private javax.swing.JComboBox fixedDistanceSurfaceComboBox_;
   private javax.swing.JPanel fixedDistanceZPanel_;
   private javax.swing.JLabel fixedSurfaceLabel_;
   private javax.swing.JLabel footprin2DLabel_;
   private javax.swing.JLabel freeDiskSpaceLabel_;
   private javax.swing.JTextField globalSavingDirTextField_;
   private javax.swing.JButton jButton1;
   private javax.swing.JButton jButton2;
   private javax.swing.JButton jButton3;
   private javax.swing.JLabel jLabel3;
   private javax.swing.JPanel jPanel1;
   private javax.swing.JPanel jPanel2;
   private javax.swing.JPanel jPanel3;
   private javax.swing.JScrollPane jScrollPane1;
   private javax.swing.JScrollPane jScrollPane2;
   private javax.swing.JLabel labelDiagram2DSurface_;
   private javax.swing.JLabel labelDiagram2dSimple_;
   private javax.swing.JLabel labelDiagram3d2Surface_;
   private javax.swing.JLabel labelDiagram3dSimple_;
   private javax.swing.JLabel labelDiagram3dSurface_;
   private javax.swing.JButton moveAcqDownButton_;
   private javax.swing.JButton moveAcqUpButton_;
   private javax.swing.JScrollPane multipleAcqScrollPane_;
   private javax.swing.JTable multipleAcqTable_;
   private javax.swing.JButton newExploreWindowButton_;
   private javax.swing.JRadioButton noCollectionPlaneButton_;
   private javax.swing.JLabel numTimePointsLabel_;
   private javax.swing.JSpinner numTimePointsSpinner_;
   private javax.swing.JButton openDatasetButton_;
   private javax.swing.JPanel panel2D_;
   private javax.swing.JPanel panel2dControlsSpecific_;
   private javax.swing.JPanel panel3DControlsSpecific_;
   private javax.swing.JButton removeAcqButton_;
   private javax.swing.JPanel root_panel_;
   private javax.swing.JButton runAcqButton_;
   private javax.swing.JPanel runAcqPanel_;
   private javax.swing.JButton setCurrentZEndButton_;
   private javax.swing.JButton setCurrentZStartButton_;
   private javax.swing.JPanel simpleZPanel_;
   private javax.swing.JPanel spaceTab_;
   private javax.swing.JPanel surfaceAndGridsPanel_;
   private javax.swing.JLabel surfacesAndGrdisLabel_;
   private javax.swing.JTable surfacesAndGridsTable_;
   private javax.swing.JButton syncExposuresButton_;
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
   private javax.swing.JRadioButton useCollectionPlaneButton_;
   private javax.swing.JLabel userGuideLink_;
   private javax.swing.JRadioButton volumeBetweenSurfacesButton_;
   private javax.swing.JPanel volumeBetweenZPanel_;
   private javax.swing.JRadioButton withinDistanceFromSurfacesButton_;
   private javax.swing.JComboBox xyFootprintComboBox_;
   private javax.swing.ButtonGroup z2DButtonGroup_;
   private javax.swing.JLabel zEndLabel;
   private javax.swing.JSpinner zEndSpinner_;
   private javax.swing.ButtonGroup zStackModeButtonGroup_;
   private javax.swing.JLabel zStartLabel;
   private javax.swing.JSpinner zStartSpinner_;
   private javax.swing.JLabel zStepLabel_;
   private javax.swing.JSpinner zStepSpinner_;
   // End of variables declaration//GEN-END:variables

}
