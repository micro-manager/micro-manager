///////////////////////////////////////////////////////////////////////////////
//FILE:          AcqControlDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Dec 1, 2005
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
// CVS:          $Id$

package org.micromanager;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.AbstractCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.micromanager.utils.AcquisitionEngine;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.ColorEditor;
import org.micromanager.utils.ColorRenderer;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.PositionMode;
import org.micromanager.utils.SliceMode;

/**
 * Time-lapse, channel and z-stack acquistion setup dialog.
 * This dialog specifies alll parameters for the Image5D acquisition. 
 *
 */
public class AcqControlDlg extends JDialog {

   private JComboBox sliceModeCombo_;
   private JComboBox posModeCombo_;
   public static final String NEW_ACQFILE_NAME = "MMAcquistion.xml";
   public static final String ACQ_SETTINGS_NODE = "AcquistionSettings";

   private JComboBox channelGroupCombo_;
   private JTextArea commentTextArea_;
   private JComboBox zValCombo_;
   private JTextField nameField_;
   private JTextField rootField_;
   private JTextArea summaryTextArea_;
   private JComboBox timeUnitCombo_;
   private JComboBox comboCameraConfig_;
   private JTextField interval_;
   private JTextField zStep_;
   private JTextField zTop_;
   private JTextField zBottom_;
   private AcquisitionEngine acqEng_;
   private JScrollPane tablePane_;
   private JTable table_;
   private JSpinner numFrames_;
   private JCheckBox overrideCheckBox_;
   private ChannelTableModel model_;
   private Preferences prefs_;
   private Preferences acqPrefs_;
   private MMOptions opts_;
   private File acqFile_;
   private String acqDir_;
   private int zVals_=0;
   private JButton setBottomButton_;
   private JButton setTopButton_;
   private JCheckBox saveFilesCheckBox_;
   private JCheckBox useSliceSettingsCheckBox_;

   // presistent properties (app settings)
   private static final String ACQ_CONTROL_X = "x";
   private static final String ACQ_CONTROL_Y = "y";
   private static final String ACQ_FILE_DIR = "dir";

   private static final String ACQ_INTERVAL = "acqInterval";
   private static final String ACQ_TIME_UNIT = "acqTimeInit";
   private static final String ACQ_ZBOTTOM = "acqZbottom";
   private static final String ACQ_ZTOP = "acqZtop";
   private static final String ACQ_ZSTEP = "acqZstep";
   private static final String ACQ_ENABLE_SLICE_SETTINGS = "enableSliceSettings";
   private static final String ACQ_ENABLE_MULTI_POSITION = "enableMultiPosition";
   private static final String ACQ_SLICE_MODE = "sliceMode";
   private static final String ACQ_POSITION_MODE = "positionMode";
   private static final String ACQ_NUMFRAMES = "acqNumframes";
   private static final String ACQ_CHANNEL_GROUP = "acqChannelGroup";
   private static final String ACQ_NUM_CHANNELS = "acqNumchannels";
   private static final String CHANNEL_NAME_PREFIX = "acqChannelName";
   private static final String CHANNEL_EXPOSURE_PREFIX = "acqChannelExp";
   private static final String CHANNEL_ZOFFSET_PREFIX = "acqChannelZOffset";
   private static final String CHANNEL_CONTRAST8_MIN_PREFIX = "acqChannel8ContrastMin";
   private static final String CHANNEL_CONTRAST8_MAX_PREFIX = "acqChannel8ContrastMax";
   private static final String CHANNEL_CONTRAST16_MIN_PREFIX = "acqChannel16ContrastMin";
   private static final String CHANNEL_CONTRAST16_MAX_PREFIX = "acqChannel16ContrastMax";
   private static final String CHANNEL_COLOR_R_PREFIX = "acqChannelColorR";
   private static final String CHANNEL_COLOR_G_PREFIX = "acqChannelColorG";
   private static final String CHANNEL_COLOR_B_PREFIX = "acqChannelColorB";
   private static final String ACQ_OVERRIDE = "acqOverride";
   private static final String ACQ_OVERRIDE_CAMERA_CONFIG = "acqOverrideCameraConfig";
   private static final String ACQ_Z_VALUES = "acqZValues";
   private static final String ACQ_DIR_NAME = "acqDirName";
   private static final String ACQ_ROOT_NAME = "acqRootName";
   private static final String ACQ_SAVE_FILES = "acqSaveFiles";
   private JCheckBox multiPosCheckBox_;


   /**
    * File filter class for Open/Save file choosers 
    */
   private class AcqFileFilter extends FileFilter {
      final private String EXT_BSH;
      final private String DESCRIPTION;

      public AcqFileFilter() {
         super();
         EXT_BSH = new String("xml");
         DESCRIPTION = new String("XML files (*.xml)");
      }

      public boolean accept(File f){
         if (f.isDirectory())
            return true;

         if (EXT_BSH.equals(getExtension(f)))
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
    * Data representation class for the channels list
    */
   public class ChannelTableModel extends AbstractTableModel {

      private ArrayList<ChannelSpec> channels_;
      private AcquisitionEngine acqEng_;

      public final String[] COLUMN_NAMES = new String[] {
            "Configuration",
            "Exposure",
            "Z-offset",
            "Color"
      };

      public ChannelTableModel(AcquisitionEngine eng) {
         acqEng_ = eng;
      }

      public int getRowCount() {
         if (channels_ == null)
            return 0;
         else
            return channels_.size();
      }
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }
      public Object getValueAt(int rowIndex, int columnIndex) {
         if (channels_ != null && rowIndex < channels_.size()) {
            if (columnIndex == 0)
               return (channels_.get(rowIndex)).config_;
            else if (columnIndex == 1)
               return new Double((channels_.get(rowIndex)).exposure_);
            else if (columnIndex == 2)
               return new Double((channels_.get(rowIndex)).zOffset_);
            else if (columnIndex == 3)
               return (channels_.get(rowIndex)).color_;
         }
         return null;
      }

      public void setValueAt(Object value, int row, int col) {
         if (row >= channels_.size())
            return;

         ChannelSpec channel = channels_.get(row);

         if (col == 0)
            channel.config_ = value.toString();
         else if (col == 1)
            channel.exposure_ = ((Double)value).doubleValue();
         else if (col == 2)
            channel.zOffset_ = ((Double)value).doubleValue();
         else if (col == 3)
            channel.color_ = (Color)value;

         acqEng_.setChannel(row, channel);
         repaint();
      }

      public boolean isCellEditable(int nRow, int nCol) {
         return true;
      }


      public void setChannels(ArrayList<ChannelSpec> ch) {
         channels_ = ch;
      }

      public ArrayList<ChannelSpec> getChannels() {
         return channels_;
      }

      public void addNewChannel() {
         ChannelSpec channel = new ChannelSpec();
         if (acqEng_.getChannelConfigs().length > 0) {
            channel.config_ = acqEng_.getChannelConfigs()[0];
            channels_.add(channel);
         }
      }

      public void removeChannel(int chIndex) {
         if (chIndex >= 0 && chIndex < channels_.size())
            channels_.remove(chIndex);
      }

      public int rowUp(int rowIdx) {
         if (rowIdx >= 0 && rowIdx < channels_.size() - 1) {
            ChannelSpec channel = channels_.get(rowIdx);
            channels_.add(rowIdx+2, channel);
            channels_.remove(rowIdx);
            return rowIdx+1;
         }
         return rowIdx;
      }

      public int rowDown(int rowIdx) {
         if (rowIdx >= 1 && rowIdx < channels_.size()) {
            ChannelSpec channel = channels_.get(rowIdx);
            channels_.add(rowIdx-1, channel);
            channels_.remove(rowIdx+1);
            return rowIdx-1;
         }
         return rowIdx;
      }

      public String[] getAvailableChannels() {
         return acqEng_.getChannelConfigs();
      }

      /**
       * Remove all channels from the list which are not compatible with
       * the current acquistion settings
       */
      public void cleanUpConfigurationList() {
         for (Iterator<ChannelSpec> it = channels_.iterator(); it.hasNext(); ) {
            if (!acqEng_.isConfigAvailable(((ChannelSpec)it.next()).config_))
               it.remove();
         }
         fireTableStructureChanged();
      }
   }

   /**
    * Cell editing using either JTextField or JComboBox depending on whether the
    * property enforces a set of allowed values.
    */
   public class ChannelCellEditor extends AbstractCellEditor implements TableCellEditor {
      JTextField text_ = new JTextField();
      JComboBox combo_ = new JComboBox();
      JLabel colorLabel_ = new JLabel();
      int editCol_ = -1;

      // This method is called when a cell value is edited by the user.
      public Component getTableCellEditorComponent(JTable table, Object value,
            boolean isSelected, int rowIndex, int colIndex) {

         if (isSelected) {
            // cell (and perhaps other cells) are selected
         }

         ChannelTableModel model = (ChannelTableModel)table.getModel();
         ArrayList<ChannelSpec> channels = model.getChannels();
         ChannelSpec channel = channels.get(rowIndex);
         // Configure the component with the specified value

         editCol_ = colIndex;
         if (colIndex==1 || colIndex==2)
         {

            text_.setText(((Double)value).toString());
            return text_;

         } else if(colIndex == 0) {

            combo_.removeAllItems();

            // remove old listeners
            ActionListener[] l = combo_.getActionListeners();
            for (int i=0; i<l.length; i++)
               combo_.removeActionListener(l[i]);
            combo_.removeAllItems();

            String configs[] = model.getAvailableChannels();
            for (int i=0; i<configs.length; i++){
               combo_.addItem(configs[i]);
            }
            combo_.setSelectedItem(channel.config_);

            // end editing on selection change
            combo_.addActionListener(new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  fireEditingStopped();
               }
            });

            // Return the configured component
            return combo_;
         } else {
            // TODO: this is never called ????
            Color selColor = JColorChooser.showDialog(null, "Channel color", (Color)value);
            colorLabel_.setOpaque(true);
            colorLabel_.setBackground(selColor);
            channel.color_ = selColor;
            return colorLabel_;
         }
      }

      // This method is called when editing is completed.
      // It must return the new value to be stored in the cell.
      public Object getCellEditorValue() {
         if (editCol_ == 0)
            return combo_.getSelectedItem();
         else if (editCol_ == 1 || editCol_ == 2)
            return new Double(text_.getText());
         else if (editCol_ == 3) {
            Color c = colorLabel_.getBackground();
            return c;
         }
         else
         {
            String err = new String("Internal error: unknown column");
            return err;
         }
      }
   }

   /**
    * Renederer class for the channel table.
    */
   public class ChannelCellRenderer extends JLabel implements TableCellRenderer {
      // This method is called each time a cell in a column
      // using this renderer needs to be rendered.
      public ChannelCellRenderer() {
         super();
      }
      public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int rowIndex, int colIndex) {

         ChannelTableModel model = (ChannelTableModel)table.getModel();
         ArrayList<ChannelSpec> channels = model.getChannels();
         ChannelSpec channel = channels.get(rowIndex);

         if (isSelected) {
            // cell (and perhaps other cells) are selected
         }

         if (hasFocus) {
            // this cell is the anchor and the table has the focus
         }

         setOpaque(false);
         if (colIndex == 0)
            setText(channel.config_);
         else if (colIndex == 1)
            setText(Double.toString(channel.exposure_));
         else if (colIndex == 2)
            setText(Double.toString(channel.zOffset_));
         else if (colIndex == 3) {
            setText("");
            setBackground(channel.color_);
            setOpaque(true);
         }

         // Since the renderer is a component, return itself
         return this;
      }

      // The following methods override the defaults for performance reasons
      public void validate() {}
      public void revalidate() {}
      protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
      public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
   }


   /**
    * Acquisition control dialog box.
    * Specification of all parameters required for the acquisition.
    * @param acqEng - acquistion engine
    * @param prefs - application preferences node
    */
   public AcqControlDlg(AcquisitionEngine acqEng, Preferences prefs, MMOptions opts) {
      super();

      prefs_ = prefs;
      opts_ = opts;
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      acqPrefs_ = root.node(root.absolutePath() + "/" + ACQ_SETTINGS_NODE);
      addWindowListener(new WindowAdapter() {
         public void windowClosing(final WindowEvent e) {
            applySettings();
            saveSettings();
            saveAcqSettings();
         }
      });
      acqEng_ = acqEng;

      getContentPane().setLayout(null);
      //getContentPane().setFocusTraversalPolicyProvider(true);
      setResizable(false);
      setTitle("Multi-dimensional Acquisition");

      final JLabel framesLabel = new JLabel();
      framesLabel.setBounds(6, 1, 121, 19);
      framesLabel.setFont(new Font("Arial", Font.BOLD, 11));
      framesLabel.setText("Frames (time)");
      getContentPane().add(framesLabel);

      final JLabel channelsLabel = new JLabel();
      channelsLabel.setFont(new Font("Arial", Font.BOLD, 11));
      channelsLabel.setBounds(10, 252, 104, 24);
      channelsLabel.setText("Channel group:");
      getContentPane().add(channelsLabel);

      final JLabel slicesLabel = new JLabel();
      slicesLabel.setFont(new Font("Arial", Font.BOLD, 11));
      slicesLabel.setBounds(7, 85, 54, 14);
      slicesLabel.setText("Slices (Z)");
      getContentPane().add(slicesLabel);

      final JLabel numberLabel = new JLabel();
      numberLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      numberLabel.setBounds(7, 27, 54, 14);
      numberLabel.setText("Number");
      getContentPane().add(numberLabel);

      SpinnerModel sModel = new SpinnerNumberModel (
            new Integer(1),
            new Integer(1),
            null,
            new Integer(1)
      );

      numFrames_ = new JSpinner(sModel);
      numFrames_.setFont(new Font("Arial", Font.PLAIN, 10));

      numFrames_.setBounds(81, 23, 67, 21);
      numFrames_.setValue(new Integer(acqEng_.getNumFrames()));
      getContentPane().add(numFrames_);
      numFrames_.addChangeListener(new ChangeListener() {
         public void stateChanged(ChangeEvent e) {
            // update summary
            applySettings();
            summaryTextArea_.setText(acqEng_.getVerboseSummary());
         }
      });

      final JLabel zbottomLabel = new JLabel();
      zbottomLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      zbottomLabel.setText("Z-start [um]");
      zbottomLabel.setBounds(7, 107, 69, 14);
      getContentPane().add(zbottomLabel);

      final JLabel ztopLabel = new JLabel();
      ztopLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      ztopLabel.setText("Z-end [um]");
      ztopLabel.setBounds(7, 131, 52, 14);
      getContentPane().add(ztopLabel);

      final JLabel zstepLabel = new JLabel();
      zstepLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      zstepLabel.setText("Z-step [um]");
      zstepLabel.setBounds(7, 156, 70, 14);
      getContentPane().add(zstepLabel);


      final JButton addButton = new JButton();
      addButton.setFont(new Font("Arial", Font.PLAIN, 10));
      addButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            //AddChannelDlg dlg = new AddChannelDlg();
            //dlg.setData(acqEng_.getConfigurations(), 10.0, 0.0);
            //dlg.setVisible(true);
            //if (dlg.isOK()) {
            model_.addNewChannel();
            model_.fireTableStructureChanged();
            // update summary
            summaryTextArea_.setText(acqEng_.getVerboseSummary());
            //}
         }
      });
      addButton.setText("New");
      addButton.setBounds(379, 279, 93, 22);
      getContentPane().add(addButton);

      final JButton removeButton = new JButton();
      removeButton.setFont(new Font("Arial", Font.PLAIN, 10));
      removeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            ChannelTableModel model = (ChannelTableModel) table_.getModel();
            model.removeChannel(table_.getSelectedRow());
            model.fireTableStructureChanged();
            // update summary
            summaryTextArea_.setText(acqEng_.getVerboseSummary());
         }
      });
      removeButton.setText("Remove");
      removeButton.setBounds(379, 302, 93, 22);
      getContentPane().add(removeButton);

      final JButton upButton = new JButton();
      upButton.setFont(new Font("Arial", Font.PLAIN, 10));
      upButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            int newSel = model_.rowUp(table_.getSelectedRow());
            model_.fireTableStructureChanged();
            table_.setRowSelectionInterval(newSel, newSel);
         }
      });
      upButton.setText("Up");
      upButton.setBounds(379, 325, 93, 22);
      getContentPane().add(upButton);

      final JButton downButton = new JButton();
      downButton.setFont(new Font("Arial", Font.PLAIN, 10));
      downButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            int sel = table_.getSelectedRow();
            int newSel = model_.rowDown(sel);
            model_.fireTableStructureChanged();
            table_.setRowSelectionInterval(newSel, newSel);
         }
      });
      downButton.setText("Dn");
      downButton.setBounds(379, 349, 93, 22);
      getContentPane().add(downButton);

      zBottom_ = new JTextField();
      zBottom_.setFont(new Font("Arial", Font.PLAIN, 10));
      zBottom_.setBounds(82, 105, 67, 21);
      getContentPane().add(zBottom_);

      zTop_ = new JTextField();
      zTop_.setFont(new Font("Arial", Font.PLAIN, 10));
      zTop_.setBounds(82, 129, 67, 21);
      getContentPane().add(zTop_);

      zStep_ = new JTextField();
      zStep_.setFont(new Font("Arial", Font.PLAIN, 10));
      zStep_.setBounds(82, 153, 67, 21);
      getContentPane().add(zStep_);

      final JLabel intervalLabel = new JLabel();
      intervalLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      intervalLabel.setText("Interval");
      intervalLabel.setBounds(7, 49, 43, 14);
      getContentPane().add(intervalLabel);

      interval_ = new JTextField();
      interval_.setFont(new Font("Arial", Font.PLAIN, 10));
      interval_.setBounds(81, 47, 52, 21);
      getContentPane().add(interval_);

      final JButton closeButton = new JButton();
      closeButton.setFont(new Font("Arial", Font.PLAIN, 10));
      closeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            applySettings();
            saveSettings();
            saveAcqSettings();
            AcqControlDlg.this.dispose();
         }
      });
      closeButton.setText("Close");
      closeButton.setBounds(379, 9, 93, 22);
      getContentPane().add(closeButton);

      final JButton acquireButton = new JButton();
      acquireButton.setFont(new Font("Arial", Font.BOLD, 11));
      acquireButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            AbstractCellEditor ae = (AbstractCellEditor)table_.getCellEditor();
            if (ae != null)
               ae.stopCellEditing();
            runAcquisition();
         }
      });
      acquireButton.setText("Acquire!");
      acquireButton.setBounds(379, 33, 93, 22);
      getContentPane().add(acquireButton);

      final JButton loadButton = new JButton();
      loadButton.setFont(new Font("Arial", Font.PLAIN, 10));
      loadButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            loadAcqSettingsFromFile();
         }
      });
      loadButton.setText("Load...");
      loadButton.setBounds(379, 57, 93, 22);
      getContentPane().add(loadButton);

      final JButton saveAsButton = new JButton();
      saveAsButton.setFont(new Font("Arial", Font.PLAIN, 10));
      saveAsButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            saveAsAcqSettingsToFile();
         }
      });
      saveAsButton.setText("Save As...");
      saveAsButton.setBounds(379, 81, 93, 22);
      getContentPane().add(saveAsButton);

      final JSeparator separator = new JSeparator();
      separator.setFont(new Font("Arial", Font.PLAIN, 10));
      separator.setBounds(6, 241, 464, 5);
      getContentPane().add(separator);

      final JLabel cameraSettingsLabel = new JLabel();
      cameraSettingsLabel.setFont(new Font("Arial", Font.BOLD, 11));
      cameraSettingsLabel.setText("Camera");
      cameraSettingsLabel.setBounds(221, 4, 104, 17);
      getContentPane().add(cameraSettingsLabel);

      overrideCheckBox_ = new JCheckBox();
      overrideCheckBox_.setFont(new Font("Arial", Font.PLAIN, 10));
      overrideCheckBox_.addChangeListener(new ChangeListener() {
         public void stateChanged(ChangeEvent e) {
            // enable/disable dependent controls
            if (overrideCheckBox_.isSelected()) {
               comboCameraConfig_.setEnabled(true);               
            } else {
               comboCameraConfig_.setEnabled(false);               
            }
         }
      });
      overrideCheckBox_.setText("Override current settings");
      overrideCheckBox_.setBounds(215, 15, 157, 20);
      getContentPane().add(overrideCheckBox_);

      comboCameraConfig_ = new JComboBox();
      comboCameraConfig_.setFont(new Font("Arial", Font.PLAIN, 10));
      comboCameraConfig_.setBounds(220, 35, 152, 21);
      getContentPane().add(comboCameraConfig_);

      // camera config combo
      String configs[];
      try {
         configs = acqEng_.getCameraConfigs();
         for (int i=0; i<configs.length; i++)
            comboCameraConfig_.addItem(configs[i]);
      } catch (Exception e) {
         handleException(e);
         return;
      }

      tablePane_ = new JScrollPane();
      tablePane_.setFont(new Font("Arial", Font.PLAIN, 10));
      tablePane_.setBounds(9, 282, 364, 112);
      getContentPane().add(tablePane_);

      timeUnitCombo_ = new JComboBox();
      timeUnitCombo_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
         }
      });
      timeUnitCombo_.setModel(new DefaultComboBoxModel(new String[] {"ms", "s", "min"}));
      timeUnitCombo_.setFont(new Font("Arial", Font.PLAIN, 10));
      timeUnitCombo_.setBounds(136, 47, 47, 21);
      getContentPane().add(timeUnitCombo_);

      // update GUI contents
      // -------------------

      // load window settings
      int x = 100;
      int y = 100;
      setBounds(x, y, 485, 599);
      if (prefs_ != null) {
         x = prefs_.getInt(ACQ_CONTROL_X, x);
         y = prefs_.getInt(ACQ_CONTROL_Y, y);
         setLocation(x, y);

         // load override settings
         comboCameraConfig_.setSelectedItem(prefs_.get(ACQ_OVERRIDE_CAMERA_CONFIG, ""));

         overrideCheckBox_.setSelected(prefs_.getBoolean(ACQ_OVERRIDE, false));
         // enable/disable dependent controls
         if (overrideCheckBox_.isSelected()) {
            comboCameraConfig_.setEnabled(true);               
         } else {
            comboCameraConfig_.setEnabled(false);               
         }
      }

      JSeparator separator_1 = new JSeparator();
      separator_1.setOrientation(SwingConstants.VERTICAL);
      separator_1.setBounds(208, 15, 5, 224);
      getContentPane().add(separator_1);

      setBottomButton_ = new JButton();
      setBottomButton_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            setBottomPosition();
         }
      });
      setBottomButton_.setMargin(new Insets(2, 5, 2, 5));
      setBottomButton_.setFont(new Font("", Font.PLAIN, 10));
      setBottomButton_.setText("Set");
      setBottomButton_.setBounds(151, 104, 47, 24);
      getContentPane().add(setBottomButton_);

      setTopButton_ = new JButton();
      setTopButton_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            setTopPosition();
         }
      });
      setTopButton_.setMargin(new Insets(2, 5, 2, 5));
      setTopButton_.setFont(new Font("Dialog", Font.PLAIN, 10));
      setTopButton_.setText("Set");
      setTopButton_.setBounds(151, 129, 47, 24);
      getContentPane().add(setTopButton_);

      summaryTextArea_ = new JTextArea();
      summaryTextArea_.setFont(new Font("Arial", Font.PLAIN, 10));
      summaryTextArea_.setEditable(false);
      summaryTextArea_.setBorder(new LineBorder(Color.black, 1, false));
      summaryTextArea_.setBounds(221, 153, 250, 85);
      getContentPane().add(summaryTextArea_);

      rootField_ = new JTextField();
      rootField_.setFont(new Font("Arial", Font.PLAIN, 10));
      rootField_.setBounds(88, 440, 334, 22);
      getContentPane().add(rootField_);

      nameField_ = new JTextField();
      nameField_.setFont(new Font("Arial", Font.PLAIN, 10));
      nameField_.setBounds(88, 468, 334, 22);
      getContentPane().add(nameField_);

      final JButton browseRootButton = new JButton();
      browseRootButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            setRootDirectory();
         }
      });
      browseRootButton.setMargin(new Insets(2, 5, 2, 5));
      browseRootButton.setFont(new Font("Dialog", Font.PLAIN, 10));
      browseRootButton.setText("...");
      browseRootButton.setBounds(425, 438, 47, 24);
      getContentPane().add(browseRootButton);

      saveFilesCheckBox_ = new JCheckBox();
      saveFilesCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
         }
      });
      saveFilesCheckBox_.setFont(new Font("Arial", Font.PLAIN, 10));
      saveFilesCheckBox_.setText("Save files to acquisition directory");
      saveFilesCheckBox_.setBounds(6, 413, 399, 21);
      getContentPane().add(saveFilesCheckBox_);

      final JLabel directoryPrefixLabel = new JLabel();
      directoryPrefixLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      directoryPrefixLabel.setText("Name prefix");
      directoryPrefixLabel.setBounds(11, 467, 76, 22);
      getContentPane().add(directoryPrefixLabel);

      final JLabel directoryPrefixLabel_1 = new JLabel();
      directoryPrefixLabel_1.setFont(new Font("Arial", Font.PLAIN, 10));
      directoryPrefixLabel_1.setText("Directory root");
      directoryPrefixLabel_1.setBounds(9, 439, 72, 22);
      getContentPane().add(directoryPrefixLabel_1);

      final JLabel summaryLabel = new JLabel();
      summaryLabel.setFont(new Font("Arial", Font.BOLD, 11));
      summaryLabel.setText("Summary");
      summaryLabel.setBounds(220, 130, 120, 21);
      getContentPane().add(summaryLabel);

      zValCombo_ = new JComboBox();
      zValCombo_.setFont(new Font("Arial", Font.PLAIN, 10));
      zValCombo_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            zValCalcChanged();
         }
      });
      zValCombo_.setModel(new DefaultComboBoxModel(new String[] {"relative Z", "absolute Z"}));
      zValCombo_.setBounds(83, 177, 110, 22);
      getContentPane().add(zValCombo_);

      commentTextArea_ = new JTextArea();
      commentTextArea_.setFont(new Font("", Font.PLAIN, 10));
      commentTextArea_.setToolTipText("Comment for the current acquistion");
      commentTextArea_.setWrapStyleWord(true);
      commentTextArea_.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
      commentTextArea_.setBounds(88, 495, 334, 62);
      getContentPane().add(commentTextArea_);

      JLabel directoryPrefixLabel_2 = new JLabel();
      directoryPrefixLabel_2.setFont(new Font("Arial", Font.PLAIN, 10));
      directoryPrefixLabel_2.setText("Comment");
      directoryPrefixLabel_2.setBounds(11, 495, 76, 22);
      getContentPane().add(directoryPrefixLabel_2);

      useSliceSettingsCheckBox_ = new JCheckBox();
      useSliceSettingsCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            // enable disable all related contrtols
            if (useSliceSettingsCheckBox_.isSelected()) {
               enableZSliceControls(true);
            } else {
               enableZSliceControls(false);
            }
            applySettings();
         }
      });
      useSliceSettingsCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      useSliceSettingsCheckBox_.setText("Use slice settings");
      useSliceSettingsCheckBox_.setBounds(80, 81, 122, 23);
      getContentPane().add(useSliceSettingsCheckBox_);

      JSeparator separator_2 = new JSeparator();
      separator_2.setFont(new Font("Arial", Font.PLAIN, 10));
      separator_2.setBounds(5, 405, 468, 5);
      getContentPane().add(separator_2);

      JSeparator separator_3 = new JSeparator();
      separator_3.setFont(new Font("Arial", Font.PLAIN, 10));
      separator_3.setBounds(5, 77, 204, 5);
      getContentPane().add(separator_3);

      String groups[] = acqEng_.getAvailableGroups();
      channelGroupCombo_ = new JComboBox(groups);
      channelGroupCombo_.setSelectedItem(acqEng_.getChannelGroup());

      channelGroupCombo_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            String newGroup = (String)channelGroupCombo_.getSelectedItem();
            acqEng_.setChannelGroup(newGroup);
            model_.cleanUpConfigurationList();
         }
      });
      channelGroupCombo_.setBounds(120, 253, 179, 22);
      getContentPane().add(channelGroupCombo_);
      
      final JLabel summaryLabel_1 = new JLabel();
      summaryLabel_1.setFont(new Font("Arial", Font.BOLD, 11));
      summaryLabel_1.setText("Multi-position list");
      summaryLabel_1.setBounds(220, 65, 120, 21);
      getContentPane().add(summaryLabel_1);

      multiPosCheckBox_ = new JCheckBox();
      multiPosCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
         }
      });
      multiPosCheckBox_.setText("Use current");
      multiPosCheckBox_.setBounds(220, 80, 101, 23);
      getContentPane().add(multiPosCheckBox_);

      posModeCombo_ = new JComboBox();
      posModeCombo_.setBounds(221, 104, 151, 20);
      getContentPane().add(posModeCombo_);
      posModeCombo_.addItem(new PositionMode(PositionMode.MULTI_FIELD));
      posModeCombo_.addItem(new PositionMode(PositionMode.TIME_LAPSE));

      sliceModeCombo_ = new JComboBox();
      sliceModeCombo_.setBounds(6, 209, 188, 20);
      getContentPane().add(sliceModeCombo_);
      sliceModeCombo_.addItem(new SliceMode(SliceMode.CHANNELS_FIRST));
      sliceModeCombo_.addItem(new SliceMode(SliceMode.SLICES_FIRST));
      
      // load acquistion settings
      loadAcqSettings();

      // update summary
      updateGUIContents();
   }

   public void updateGroupsCombo() {
      String groups[] = acqEng_.getAvailableGroups();
      channelGroupCombo_.setModel(new DefaultComboBoxModel(groups));
      channelGroupCombo_.setSelectedItem(acqEng_.getChannelGroup());
   }

   public void loadAcqSettings() {
      // load acquition engine preferences
      acqEng_.clear();
      int numFrames = acqPrefs_.getInt(ACQ_NUMFRAMES, 1);
      double interval = acqPrefs_.getDouble(ACQ_INTERVAL, 0.0);
      int unit = acqPrefs_.getInt(ACQ_TIME_UNIT, 0);
      timeUnitCombo_.setSelectedIndex(unit);
      acqEng_.setFrames(numFrames, interval);
      double bottom = acqPrefs_.getDouble(ACQ_ZBOTTOM, 0.0);
      double top = acqPrefs_.getDouble(ACQ_ZTOP, 0.0);
      double step = acqPrefs_.getDouble(ACQ_ZSTEP, 1.0);
      if (Math.abs(step) < Math.abs(acqEng_.getMinZStepUm()))
         step = acqEng_.getMinZStepUm();
      zVals_ = acqPrefs_.getInt(ACQ_Z_VALUES, 0);
      acqEng_.setSlices(bottom, top, step, zVals_ == 0 ? false : true);
      acqEng_.enableZSliceSetting(acqPrefs_.getBoolean(ACQ_ENABLE_SLICE_SETTINGS, acqEng_.isZSliceSettingEnabled()));
      acqEng_.enableMultiPosition(acqPrefs_.getBoolean(ACQ_ENABLE_MULTI_POSITION, acqEng_.isMultiPositionEnabled()));
      saveFilesCheckBox_.setSelected(acqPrefs_.getBoolean(ACQ_SAVE_FILES, false));
      nameField_.setText(acqPrefs_.get(ACQ_DIR_NAME, "Untitled"));
      rootField_.setText(acqPrefs_.get(ACQ_ROOT_NAME, "C:/AcquisitionData"));
      
      acqEng_.setSliceMode(acqPrefs_.getInt(ACQ_SLICE_MODE, acqEng_.getSliceMode()));
      acqEng_.setPositionMode(acqPrefs_.getInt(ACQ_POSITION_MODE, acqEng_.getPositionMode()));

      acqEng_.setChannelGroup(acqPrefs_.get(ACQ_CHANNEL_GROUP, ChannelSpec.DEFAULT_CHANNEL_GROUP));
      int numChannels = acqPrefs_.getInt(ACQ_NUM_CHANNELS, 0);

      ChannelSpec defaultChannel = new ChannelSpec();
      for (int i=0; i<numChannels; i++){
         String name = acqPrefs_.get(CHANNEL_NAME_PREFIX+i, "Undefined");
         double exp = acqPrefs_.getDouble(CHANNEL_EXPOSURE_PREFIX+i, 0.0);
         double zOffset = acqPrefs_.getDouble(CHANNEL_ZOFFSET_PREFIX+i, 0.0);
         ContrastSettings s8 = new ContrastSettings();
         s8.min = acqPrefs_.getDouble(CHANNEL_CONTRAST8_MIN_PREFIX + i, defaultChannel.contrast8_.min);
         s8.max = acqPrefs_.getDouble(CHANNEL_CONTRAST8_MAX_PREFIX + i, defaultChannel.contrast8_.max);
         ContrastSettings s16 = new ContrastSettings();
         s16.min = acqPrefs_.getDouble(CHANNEL_CONTRAST16_MIN_PREFIX + i, defaultChannel.contrast16_.min);
         s16.max = acqPrefs_.getDouble(CHANNEL_CONTRAST16_MAX_PREFIX + i, defaultChannel.contrast16_.max);
         int r = acqPrefs_.getInt(CHANNEL_COLOR_R_PREFIX + i, defaultChannel.color_.getRed());
         int g = acqPrefs_.getInt(CHANNEL_COLOR_G_PREFIX + i, defaultChannel.color_.getGreen());
         int b = acqPrefs_.getInt(CHANNEL_COLOR_B_PREFIX + i, defaultChannel.color_.getBlue());
         Color c = new Color(r, g, b);
         acqEng_.addChannel(name, exp, zOffset, s8, s16, c);
      }
   }

   public void saveAcqSettings() {
      try {
         acqPrefs_.clear();
      } catch (BackingStoreException e) {
         // TODO: not sure what to do here
      }

      acqPrefs_.putDouble(ACQ_INTERVAL, acqEng_.getFrameIntervalMs());
      acqPrefs_.putInt(ACQ_TIME_UNIT, timeUnitCombo_.getSelectedIndex());
      acqPrefs_.putDouble(ACQ_ZBOTTOM, acqEng_.getSliceZBottomUm());
      acqPrefs_.putDouble(ACQ_ZTOP, acqEng_.getZTopUm());
      acqPrefs_.putDouble(ACQ_ZSTEP, acqEng_.getSliceZStepUm());
      acqPrefs_.putBoolean(ACQ_ENABLE_SLICE_SETTINGS, acqEng_.isZSliceSettingEnabled());
      acqPrefs_.putBoolean(ACQ_ENABLE_MULTI_POSITION, acqEng_.isMultiPositionEnabled());
      acqPrefs_.putInt(ACQ_NUMFRAMES, acqEng_.getNumFrames());
      acqPrefs_.putInt(ACQ_Z_VALUES, zVals_);
      acqPrefs_.putBoolean(ACQ_SAVE_FILES, saveFilesCheckBox_.isSelected());
      acqPrefs_.put(ACQ_DIR_NAME, nameField_.getText());
      acqPrefs_.put(ACQ_ROOT_NAME, rootField_.getText());
      
      acqPrefs_.putInt(ACQ_SLICE_MODE, acqEng_.getSliceMode());
      acqPrefs_.putInt(ACQ_POSITION_MODE, acqEng_.getPositionMode());

      acqPrefs_.put(ACQ_CHANNEL_GROUP, acqEng_.getChannelGroup());
      ArrayList<ChannelSpec> channels = acqEng_.getChannels();
      acqPrefs_.putInt(ACQ_NUM_CHANNELS, channels.size());
      for (int i=0; i<channels.size(); i++) {
         ChannelSpec channel = channels.get(i);
         acqPrefs_.put(CHANNEL_NAME_PREFIX + i, channel.config_);
         acqPrefs_.putDouble(CHANNEL_EXPOSURE_PREFIX + i, channel.exposure_);
         acqPrefs_.putDouble(CHANNEL_ZOFFSET_PREFIX + i, channel.zOffset_);
         acqPrefs_.putDouble(CHANNEL_CONTRAST8_MIN_PREFIX + i, channel.contrast8_.min);
         acqPrefs_.putDouble(CHANNEL_CONTRAST8_MAX_PREFIX + i, channel.contrast8_.max);
         acqPrefs_.putDouble(CHANNEL_CONTRAST16_MIN_PREFIX + i, channel.contrast16_.min);
         acqPrefs_.putDouble(CHANNEL_CONTRAST16_MAX_PREFIX + i, channel.contrast16_.max);
         acqPrefs_.putInt(CHANNEL_COLOR_R_PREFIX + i, channel.color_.getRed());
         acqPrefs_.putInt(CHANNEL_COLOR_G_PREFIX + i, channel.color_.getGreen());
         acqPrefs_.putInt(CHANNEL_COLOR_B_PREFIX + i, channel.color_.getBlue());
      }    
   }
   
   protected void enableZSliceControls(boolean state) {
      zBottom_.setEnabled(state);
      zTop_.setEnabled(state);
      zStep_.setEnabled(state);
      zValCombo_.setEnabled(state);
   }

   protected void setRootDirectory() {
      JFileChooser fc = new JFileChooser();
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      fc.setCurrentDirectory(new File(acqEng_.getRootName()));
      int retVal = fc.showOpenDialog(this);
      if (retVal == JFileChooser.APPROVE_OPTION) {
         rootField_.setText(fc.getSelectedFile().getAbsolutePath());
         acqEng_.setRootName(fc.getSelectedFile().getAbsolutePath());
      }
   }

   protected void setTopPosition() {
      double z = acqEng_.getCurrentZPos();
      zTop_.setText(Double.toString(z));
      applySettings();
      // update summary
      summaryTextArea_.setText(acqEng_.getVerboseSummary());     
   }


   protected void setBottomPosition() {
      double z = acqEng_.getCurrentZPos();
      zBottom_.setText(Double.toString(z));
      applySettings();
      // update summary
      summaryTextArea_.setText(acqEng_.getVerboseSummary());     
   }

   protected void loadAcqSettingsFromFile() {
      JFileChooser fc = new JFileChooser();
      fc.addChoosableFileFilter(new AcqFileFilter());
      acqDir_ = prefs_.get(ACQ_FILE_DIR, null);

      if (acqDir_ != null)
         fc.setCurrentDirectory(new File(acqDir_));
      int retVal = fc.showOpenDialog(this);
      if (retVal == JFileChooser.APPROVE_OPTION) {
         acqFile_ = fc.getSelectedFile();
         try {
            FileInputStream in = new FileInputStream(acqFile_);
            acqPrefs_.clear();
            Preferences.importPreferences(in);
            loadAcqSettings();
            updateGUIContents();
            in.close();
            acqDir_ = acqFile_.getParent();
            prefs_.put(ACQ_FILE_DIR, acqDir_);
         } catch (Exception e) {
            handleException(e);
            return;
         }
      }
   }

   protected boolean saveAsAcqSettingsToFile() {
      applySettings();
      saveAcqSettings();
      JFileChooser fc = new JFileChooser();
      boolean saveFile = true;
      if (acqPrefs_ == null)
         return false; //nothing to save

      do {
         if (acqFile_ == null)
            acqFile_ = new File(NEW_ACQFILE_NAME);

         fc.setSelectedFile(acqFile_);
         int retVal = fc.showSaveDialog(this);
         if (retVal == JFileChooser.APPROVE_OPTION) {
            acqFile_ = fc.getSelectedFile();

            // check if file already exists
            if( acqFile_.exists() ) { 
               int sel = JOptionPane.showConfirmDialog( this,
                     "Overwrite " + acqFile_.getName(),
                     "File Save",
                     JOptionPane.YES_NO_OPTION);

               if(sel == JOptionPane.YES_OPTION)
                  saveFile = true;
               else
                  saveFile = false;
            }
         } else {
            return false; 
         }
      } while (saveFile == false);

      FileOutputStream os;
      try {
         os = new FileOutputStream(acqFile_);
         acqPrefs_.exportNode(os);
      } catch (FileNotFoundException e) {
         handleException(e);
         return false;
      } catch (IOException e) {
         handleException(e);
         return false;
      } catch (BackingStoreException e) {
         handleException(e);
         return false;
      }
      return true;
   }

   protected void runAcquisition() {
      if (acqEng_.isAcquisitionRunning()) {
         JOptionPane.showMessageDialog(this, "Unable to start the new acquisition task: previous acquisition still in progress.");
         return;
      }
      // NS: To avoid further suffering like Adam's:
      if (acqEng_.getChannels().size()==0) {
         JOptionPane.showMessageDialog(this, "No Channels defined.  Please add one or more Channels first by pressing the New button");
         return;
      }

      try {
         applySettings();
         applyOverride();
         acqEng_.acquireMT();
      } catch(Exception e) {
         handleException(e);
         return;
      }
   }

   private void applyOverride() throws Exception{

      // if the "override" is checked
      String config = (String)comboCameraConfig_.getSelectedItem();
      if (overrideCheckBox_.isSelected() && config != null) {                  
         // override current camera settings
         acqEng_.setCameraConfig(config);
         acqEng_.setUpdateLiveWindow(false);
      } else {
         acqEng_.setCameraConfig("");
         acqEng_.setUpdateLiveWindow(true);
      }
   }
   private void updateGUIContents() {

      table_ = new JTable();
      table_.setFont(new Font("Dialog", Font.PLAIN, 10));
      table_.setAutoCreateColumnsFromModel(false);

      model_ = new ChannelTableModel(acqEng_);
      // TODO: remove setChannels()
      model_.setChannels(acqEng_.getChannels());
      table_.setModel(model_);

      ChannelCellEditor cellEditor = new ChannelCellEditor();
      ChannelCellRenderer cellRenderer = new ChannelCellRenderer();

      for (int k=0; k < model_.getColumnCount()-1; k++) {
         TableColumn column = new TableColumn(k, 200, cellRenderer, cellEditor);
         table_.addColumn(column);
      }
      ColorRenderer cr = new ColorRenderer(true);
      ColorEditor ce = new ColorEditor();
      TableColumn column = new TableColumn(model_.getColumnCount()-1, 200, cr, ce);
      table_.addColumn(column);

      tablePane_.setViewportView(table_);
      double intervalMs = acqEng_.getFrameIntervalMs();
      interval_.setText(Double.toString(convertMsToTime(intervalMs, timeUnitCombo_.getSelectedIndex())));
      zBottom_.setText(Double.toString(acqEng_.getSliceZBottomUm()));
      zTop_.setText(Double.toString(acqEng_.getZTopUm()));
      zStep_.setText(Double.toString(acqEng_.getSliceZStepUm()));
      useSliceSettingsCheckBox_.setSelected(acqEng_.isZSliceSettingEnabled());
      multiPosCheckBox_.setSelected(acqEng_.isMultiPositionEnabled());
      enableZSliceControls(acqEng_.isZSliceSettingEnabled());
      model_.fireTableStructureChanged();
      channelGroupCombo_.setSelectedItem(acqEng_.getChannelGroup());
      sliceModeCombo_.setSelectedIndex(acqEng_.getSliceMode());
      posModeCombo_.setSelectedIndex(acqEng_.getPositionMode());
      
      numFrames_.setValue(new Integer(acqEng_.getNumFrames()));
      zValCombo_.setSelectedIndex(zVals_);

      // update summary
      summaryTextArea_.setText(acqEng_.getVerboseSummary());      
   }

   private void applySettings() {
      AbstractCellEditor ae = (AbstractCellEditor)table_.getCellEditor();
      if (ae != null)
         ae.stopCellEditing();

      double zStep = Double.parseDouble(zStep_.getText());
      if (Math.abs(zStep) < acqEng_.getMinZStepUm()) {
         zStep = acqEng_.getMinZStepUm();
         zStep_.setText(Double.toString(zStep));
      }
      acqEng_.setSlices(Double.parseDouble(zBottom_.getText()), Double.parseDouble(zTop_.getText()), zStep, zVals_ == 0 ? false : true);
      acqEng_.enableZSliceSetting(useSliceSettingsCheckBox_.isSelected());
      acqEng_.enableMultiPosition(multiPosCheckBox_.isSelected());
      acqEng_.setSliceMode(((SliceMode)sliceModeCombo_.getSelectedItem()).getID());
      acqEng_.setPositionMode(((PositionMode)posModeCombo_.getSelectedItem()).getID());
      acqEng_.setChannels(((ChannelTableModel)table_.getModel()).getChannels());
      acqEng_.setFrames(((Integer)numFrames_.getValue()).intValue(),
            convertTimeToMs(Double.parseDouble(interval_.getText()), timeUnitCombo_.getSelectedIndex()));

      acqEng_.setSaveFiles(saveFilesCheckBox_.isSelected());
      acqEng_.setDirName(nameField_.getText());
      acqEng_.setRootName(rootField_.getText());

      // update summary
      summaryTextArea_.setText(acqEng_.getVerboseSummary()); 
      acqEng_.setComment(commentTextArea_.getText());
   }

   private void handleException (Exception e) {
      String errText = "Exeption occured: " + e.getMessage();
      JOptionPane.showMessageDialog(this, errText);     
   }

   private void handleError (String txt) {
      JOptionPane.showMessageDialog(this, txt);     
   }

   /**
    * Save settings to application properties.
    *
    */
   private void saveSettings() {
      Rectangle r = getBounds();

      if (prefs_ != null) {
         // save window position
         prefs_.putInt(ACQ_CONTROL_X, r.x);
         prefs_.putInt(ACQ_CONTROL_Y, r.y);

         // save override settings
         prefs_.putBoolean(ACQ_OVERRIDE, overrideCheckBox_.isSelected());
         String cameraCfg = (String)comboCameraConfig_.getSelectedItem();
         if (cameraCfg != null)
            prefs_.put(ACQ_OVERRIDE_CAMERA_CONFIG, cameraCfg);
      }
   }

   private double convertTimeToMs(double interval, int units) {
      if (units == 1)
         return interval * 1000; // sec
      else if (units == 2)
         return interval * 60.0 * 1000.0; // min
      else if (units == 0)
         return interval; // ms

      handleError("Unknown units supplied for acquisition interval!");
      return interval;
   }

   private double convertMsToTime(double intervalMs, int units) {
      if (units == 1)
         return intervalMs / 1000; // sec
      else if (units == 2)
         return intervalMs / (60.0 * 1000.0); // min
      else if (units == 0)
         return intervalMs; // ms

      handleError("Unknown units supplied for acquisition interval!");
      return intervalMs;
   }

   private void zValCalcChanged() {

      if (zValCombo_.getSelectedIndex() == 0) {
         setTopButton_.setEnabled(false);
         setBottomButton_.setEnabled(false);         
      } else {
         setTopButton_.setEnabled(true);
         setBottomButton_.setEnabled(true);         
      }

      if (zVals_ == zValCombo_.getSelectedIndex())
         return;

      zVals_ = zValCombo_.getSelectedIndex();
      double zBottomUm = Double.parseDouble(zBottom_.getText());
      double zTopUm = Double.parseDouble(zTop_.getText());
      DecimalFormat fmt = new DecimalFormat("#0.00");

      if (zVals_ == 0) {
         setTopButton_.setEnabled(false);
         setBottomButton_.setEnabled(false);
         // convert from absolute to relative
         double newTop = (zTopUm - zBottomUm) / 2.0;
         double newBottom = -newTop / 2.0;
         zBottom_.setText(fmt.format(newBottom));
         zTop_.setText(fmt.format(newTop));
      } else {
         setTopButton_.setEnabled(true);
         setBottomButton_.setEnabled(true);
         // convert from relative to absolute
         double curZ = acqEng_.getCurrentZPos();
         double newTop = curZ + zTopUm;
         double newBottom = curZ + zBottomUm;
         zBottom_.setText(fmt.format(newBottom));
         zTop_.setText(fmt.format(newTop));
      }
   }

} 
