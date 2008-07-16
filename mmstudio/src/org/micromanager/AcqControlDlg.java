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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

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
import javax.swing.JFormattedTextField;
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

import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.Autofocus;
import org.micromanager.api.DeviceControlGUI;

import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.metadata.WellAcquisitionData;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.ColorEditor;
import org.micromanager.utils.ColorRenderer;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.GUIColors;
import org.micromanager.utils.MMException;
import org.micromanager.utils.PositionMode;
import org.micromanager.utils.SliceMode;
import com.swtdesigner.SwingResourceManager;

/**
 * Time-lapse, channel and z-stack acquisition setup dialog.
 * This dialog specifies all parameters for the Image5D acquisition. 
 *
 */
public class AcqControlDlg extends JDialog implements PropertyChangeListener {
   private static final long serialVersionUID = 1L;
   private JButton listButton;
   private JButton afButton;
   private JCheckBox afCheckBox_;
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
   private JFormattedTextField interval_;
   private NumberFormat numberFormat_;
   private JFormattedTextField zStep_;
   private JFormattedTextField zTop_;
   private JFormattedTextField zBottom_;
   private AcquisitionEngine acqEng_;
   private JScrollPane tablePane_;
   private JTable table_;
   private JSpinner numFrames_;
   private ChannelTableModel model_;
   private Preferences prefs_;
   private Preferences acqPrefs_;
   private File acqFile_;
   private String acqDir_;
   private int zVals_=0;
   private JButton setBottomButton_;
   private JButton setTopButton_;
   private JCheckBox saveFilesCheckBox_;
   private JCheckBox useSliceSettingsCheckBox_;
   
   private DeviceControlGUI gui_;
   private GUIColors guiColors_;

   // persistent properties (app settings)
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
   private static final String CHANNEL_SKIP_PREFIX = "acqSkip";
   private static final String ACQ_Z_VALUES = "acqZValues";
   private static final String ACQ_DIR_NAME = "acqDirName";
   private static final String ACQ_ROOT_NAME = "acqRootName";
   private static final String ACQ_SAVE_FILES = "acqSaveFiles";
   private static final String ACQ_SINGLE_FRAME = "singleFrame";
   private static final String ACQ_AF_ENABLE = "autofocus_enabled";
   private JCheckBox multiPosCheckBox_;
   private JCheckBox singleFrameCheckBox_;


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
      private static final long serialVersionUID = 3290621191844925827L;
      private ArrayList<ChannelSpec> channels_;
      private AcquisitionEngine acqEng_;

      public final String[] COLUMN_NAMES = new String[] {
            "Configuration",
            "Exposure",
            "Z-offset",
            "Skip Fr.",
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
               return channels_.get(rowIndex).config_;
            else if (columnIndex == 1)
               return new Double(channels_.get(rowIndex).exposure_);
            else if (columnIndex == 2)
               return new Double(channels_.get(rowIndex).zOffset_);
            else if (columnIndex == 3)
               return new Integer(channels_.get(rowIndex).skipFactorFrame_);
            else if (columnIndex == 4)
               return channels_.get(rowIndex).color_;
         }
         return null;
      }

      public void setValueAt(Object value, int row, int col) {
         if (row >= channels_.size() || value == null)
            return;

         ChannelSpec channel = channels_.get(row);

         if (col == 0)
            channel.config_ = value.toString();
         else if (col == 1)
            channel.exposure_ = ((Double)value).doubleValue();
         else if (col == 2)
            channel.zOffset_ = ((Double)value).doubleValue();
         else if (col == 3)
            channel.skipFactorFrame_ = ((Integer)value).intValue();
         else if (col == 4)
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
       * the current acquisition settings
       */
      public void cleanUpConfigurationList() {
         for (Iterator<ChannelSpec> it = channels_.iterator(); it.hasNext(); ) {
            if (!acqEng_.isConfigAvailable(it.next().config_))
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
      private static final long serialVersionUID = -8374637422965302637L;
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
            // exposure and z offset
            text_.setText(((Double)value).toString());
            return text_;

         } else if (colIndex == 3) {
            // skip
            text_.setText(((Integer)value).toString());
            return text_;
         } else if(colIndex == 0) {
            // channel
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
            // color
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
            return new Integer(text_.getText());
         } else if (editCol_ == 4) {
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
      private static final long serialVersionUID = -4328340719459382679L;
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
            setText(Integer.toString(channel.skipFactorFrame_));
         } else if (colIndex == 4) {
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
   public AcqControlDlg(AcquisitionEngine acqEng, Preferences prefs, DeviceControlGUI gui) {
      super();

      prefs_ = prefs;
      gui_ = gui;
      guiColors_ = new GUIColors();
      
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
      setBackground(guiColors_.background.get(gui_.getBackgroundStyle()));

      final JLabel framesLabel = new JLabel();
      framesLabel.setBounds(6, 1, 121, 19);
      framesLabel.setFont(new Font("Arial", Font.BOLD, 11));
      framesLabel.setText("Frames (time)");
      getContentPane().add(framesLabel);

      final JLabel channelsLabel = new JLabel();
      channelsLabel.setFont(new Font("Arial", Font.BOLD, 11));
      channelsLabel.setBounds(11, 267, 104, 24);
      channelsLabel.setText("Channel group:");
      getContentPane().add(channelsLabel);

      final JLabel slicesLabel = new JLabel();
      slicesLabel.setFont(new Font("Arial", Font.BOLD, 11));
      slicesLabel.setBounds(9, 136, 54, 14);
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

      numFrames_.setBounds(81, 21, 67, 24);
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
      zbottomLabel.setBounds(9, 158, 69, 14);
      getContentPane().add(zbottomLabel);

      final JLabel ztopLabel = new JLabel();
      ztopLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      ztopLabel.setText("Z-end [um]");
      ztopLabel.setBounds(9, 182, 52, 14);
      getContentPane().add(ztopLabel);

      final JLabel zstepLabel = new JLabel();
      zstepLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      zstepLabel.setText("Z-step [um]");
      zstepLabel.setBounds(9, 207, 70, 14);
      getContentPane().add(zstepLabel);


      final JButton addButton = new JButton();
      addButton.setFont(new Font("Arial", Font.PLAIN, 10));
      addButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            model_.addNewChannel();
            model_.fireTableStructureChanged();
            // update summary
            summaryTextArea_.setText(acqEng_.getVerboseSummary());
         }
      });
      addButton.setText("New");
      addButton.setBounds(405, 293, 93, 22);
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
      removeButton.setBounds(405, 316, 93, 22);
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
      upButton.setBounds(405, 339, 93, 22);
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
      downButton.setBounds(405, 363, 93, 22);
      getContentPane().add(downButton);

      zBottom_ = new JFormattedTextField();
      zBottom_.setFont(new Font("Arial", Font.PLAIN, 10));
      zBottom_.setBounds(84, 156, 67, 21);
      zBottom_.setValue(new Double(1.0));
      zBottom_.addPropertyChangeListener("value", this); 
      getContentPane().add(zBottom_);

      zTop_ = new JFormattedTextField();
      zTop_.setFont(new Font("Arial", Font.PLAIN, 10));
      zTop_.setBounds(84, 180, 67, 21);
      zTop_.setValue(new Double(1.0));
      zTop_.addPropertyChangeListener("value", this); 
      getContentPane().add(zTop_);

      zStep_ = new JFormattedTextField();
      zStep_.setFont(new Font("Arial", Font.PLAIN, 10));
      zStep_.setBounds(84, 204, 67, 21);
      zStep_.setValue(new Double(1.0));
      zStep_.addPropertyChangeListener("value", this); 
      getContentPane().add(zStep_);

      final JLabel intervalLabel = new JLabel();
      intervalLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      intervalLabel.setText("Interval");
      intervalLabel.setBounds(7, 49, 43, 14);
      getContentPane().add(intervalLabel);

      numberFormat_ = NumberFormat.getNumberInstance();
      interval_ = new JFormattedTextField(numberFormat_);
      interval_.setFont(new Font("Arial", Font.PLAIN, 10));
      interval_.setBounds(81, 47, 52, 21);
      interval_.setValue(new Double(1.0));
      interval_.addPropertyChangeListener("value", this); 
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
      closeButton.setBounds(405, 20, 93, 22);
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
      acquireButton.setBounds(405, 44, 93, 22);
      getContentPane().add(acquireButton);

      final JButton loadButton = new JButton();
      loadButton.setFont(new Font("Arial", Font.PLAIN, 10));
      loadButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            loadAcqSettingsFromFile();
         }
      });
      loadButton.setText("Load...");
      loadButton.setBounds(405, 105, 93, 22);
      getContentPane().add(loadButton);

      final JButton saveAsButton = new JButton();
      saveAsButton.setFont(new Font("Arial", Font.PLAIN, 10));
      saveAsButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            saveAsAcqSettingsToFile();
         }
      });
      saveAsButton.setText("Save As...");
      saveAsButton.setBounds(405, 129, 93, 22);
      getContentPane().add(saveAsButton);

      final JSeparator separator = new JSeparator();
      separator.setFont(new Font("Arial", Font.PLAIN, 10));
      separator.setBounds(6, 256, 492, 5);
      getContentPane().add(separator);

      tablePane_ = new JScrollPane();
      tablePane_.setFont(new Font("Arial", Font.PLAIN, 10));
      tablePane_.setBounds(10, 297, 389, 112);
      getContentPane().add(tablePane_);

      timeUnitCombo_ = new JComboBox();
      timeUnitCombo_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            interval_.setText(Double.toString(convertMsToTime(acqEng_.getFrameIntervalMs(), timeUnitCombo_.getSelectedIndex())));
         }
      });
      timeUnitCombo_.setModel(new DefaultComboBoxModel(new String[] {"ms", "s", "min"}));
      timeUnitCombo_.setFont(new Font("Arial", Font.PLAIN, 10));
      timeUnitCombo_.setBounds(136, 46, 57, 22);
      getContentPane().add(timeUnitCombo_);

      // update GUI contents
      // -------------------

      // load window settings
      int x = 100;
      int y = 100;
      setBounds(x, y, 514, 604);
      if (prefs_ != null) {
         x = prefs_.getInt(ACQ_CONTROL_X, x);
         y = prefs_.getInt(ACQ_CONTROL_Y, y);
         setLocation(x, y);

         // load override settings
         // enable/disable dependent controls
      }

      JSeparator separator_1 = new JSeparator();
      separator_1.setOrientation(SwingConstants.VERTICAL);
      separator_1.setBounds(223, 9, 5, 241);
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
      setBottomButton_.setBounds(170, 158, 47, 24);
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
      setTopButton_.setBounds(170, 182, 47, 24);
      getContentPane().add(setTopButton_);

      summaryTextArea_ = new JTextArea();
      summaryTextArea_.setFont(new Font("Arial", Font.PLAIN, 10));
      summaryTextArea_.setEditable(false);
      summaryTextArea_.setBorder(new LineBorder(Color.black, 1, false));
      summaryTextArea_.setBounds(248, 155, 250, 95);
      getContentPane().add(summaryTextArea_);

      rootField_ = new JTextField();
      rootField_.setFont(new Font("Arial", Font.PLAIN, 10));
      rootField_.setBounds(91, 450, 354, 22);
      getContentPane().add(rootField_);

      nameField_ = new JTextField();
      nameField_.setFont(new Font("Arial", Font.PLAIN, 10));
      nameField_.setBounds(91, 478, 354, 22);
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
      browseRootButton.setBounds(451, 448, 47, 24);
      getContentPane().add(browseRootButton);

      saveFilesCheckBox_ = new JCheckBox();
      saveFilesCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            if (saveFilesCheckBox_.isSelected())
               singleFrameCheckBox_.setEnabled(true);
            else
            {
               singleFrameCheckBox_.setSelected(false);
               singleFrameCheckBox_.setEnabled(false);
            }
         }
      });
      saveFilesCheckBox_.setFont(new Font("Arial", Font.PLAIN, 10));
      saveFilesCheckBox_.setText("Save files to acquisition directory");
      saveFilesCheckBox_.setBounds(9, 423, 207, 21);
      getContentPane().add(saveFilesCheckBox_);

      final JLabel directoryPrefixLabel = new JLabel();
      directoryPrefixLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      directoryPrefixLabel.setText("Name prefix");
      directoryPrefixLabel.setBounds(14, 477, 76, 22);
      getContentPane().add(directoryPrefixLabel);

      final JLabel directoryPrefixLabel_1 = new JLabel();
      directoryPrefixLabel_1.setFont(new Font("Arial", Font.PLAIN, 10));
      directoryPrefixLabel_1.setText("Directory root");
      directoryPrefixLabel_1.setBounds(12, 449, 72, 22);
      getContentPane().add(directoryPrefixLabel_1);

      final JLabel summaryLabel = new JLabel();
      summaryLabel.setFont(new Font("Arial", Font.BOLD, 11));
      summaryLabel.setText("Summary");
      summaryLabel.setBounds(248, 93, 120, 21);
      getContentPane().add(summaryLabel);

      zValCombo_ = new JComboBox();
      zValCombo_.setFont(new Font("Arial", Font.PLAIN, 10));
      zValCombo_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            zValCalcChanged();
         }
      });
      zValCombo_.setModel(new DefaultComboBoxModel(new String[] {"relative Z", "absolute Z"}));
      zValCombo_.setBounds(85, 228, 110, 22);
      getContentPane().add(zValCombo_);

      JScrollPane commentScrollPane = new JScrollPane();
      commentScrollPane.setBounds(91, 505, 354, 62);
      getContentPane().add(commentScrollPane);

      commentTextArea_ = new JTextArea();
      commentTextArea_.setFont(new Font("", Font.PLAIN, 10));
      commentTextArea_.setToolTipText("Comment for the current acquistion");
      commentTextArea_.setWrapStyleWord(true);
      commentTextArea_.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
      //commentTextArea_.setBounds(91, 505, 354, 62);
      //getContentPane().add(commentTextArea_);
      commentScrollPane.setViewportView(commentTextArea_);


      JLabel directoryPrefixLabel_2 = new JLabel();
      directoryPrefixLabel_2.setFont(new Font("Arial", Font.PLAIN, 10));
      directoryPrefixLabel_2.setText("Comment");
      directoryPrefixLabel_2.setBounds(14, 505, 76, 22);
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
      useSliceSettingsCheckBox_.setBounds(82, 132, 122, 23);
      getContentPane().add(useSliceSettingsCheckBox_);

      JSeparator separator_2 = new JSeparator();
      separator_2.setFont(new Font("Arial", Font.PLAIN, 10));
      separator_2.setBounds(5, 415, 493, 5);
      getContentPane().add(separator_2);

      JSeparator separator_3 = new JSeparator();
      separator_3.setFont(new Font("Arial", Font.PLAIN, 10));
      separator_3.setBounds(5, 130, 215, 5);
      getContentPane().add(separator_3);

      String groups[] = acqEng_.getAvailableGroups();
      channelGroupCombo_ = new JComboBox(groups);
      channelGroupCombo_.setFont(new Font("", Font.PLAIN, 10));
      channelGroupCombo_.setSelectedItem(acqEng_.getChannelGroup());

      channelGroupCombo_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            String newGroup = (String)channelGroupCombo_.getSelectedItem();
            acqEng_.setChannelGroup(newGroup);
            model_.cleanUpConfigurationList();
         }
      });
      channelGroupCombo_.setBounds(121, 268, 179, 22);
      getContentPane().add(channelGroupCombo_);

      multiPosCheckBox_ = new JCheckBox();
      multiPosCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      multiPosCheckBox_.setText("Use XY list");
      multiPosCheckBox_.setBounds(81, 77, 86, 19);
      multiPosCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            if (multiPosCheckBox_.isSelected())
               afCheckBox_.setEnabled(true);
            else
            {
               afCheckBox_.setSelected(false);
               afCheckBox_.setEnabled(false);
            }
         }
      });
      getContentPane().add(multiPosCheckBox_);

      posModeCombo_ = new JComboBox();
      posModeCombo_.setFont(new Font("", Font.PLAIN, 10));
      posModeCombo_.setBounds(248, 22, 151, 22);
      getContentPane().add(posModeCombo_);
      posModeCombo_.addItem(new PositionMode(PositionMode.MULTI_FIELD));
      posModeCombo_.addItem(new PositionMode(PositionMode.TIME_LAPSE));

      sliceModeCombo_ = new JComboBox();
      sliceModeCombo_.setFont(new Font("", Font.PLAIN, 10));
      sliceModeCombo_.setBounds(248, 48, 151, 22);
      getContentPane().add(sliceModeCombo_);
      sliceModeCombo_.addItem(new SliceMode(SliceMode.CHANNELS_FIRST));
      sliceModeCombo_.addItem(new SliceMode(SliceMode.SLICES_FIRST));
      
      final JSeparator separator_3_1 = new JSeparator();
      separator_3_1.setFont(new Font("Arial", Font.PLAIN, 10));
      separator_3_1.setBounds(6, 69, 215, 5);
      getContentPane().add(separator_3_1);

      final JLabel slicesLabel_1 = new JLabel();
      slicesLabel_1.setFont(new Font("Arial", Font.BOLD, 11));
      slicesLabel_1.setText("Position XY");
      slicesLabel_1.setBounds(10, 79, 67, 14);
      getContentPane().add(slicesLabel_1);

      final JLabel slicechannelOrderingLabel_1 = new JLabel();
      slicechannelOrderingLabel_1.setFont(new Font("", Font.BOLD, 12));
      slicechannelOrderingLabel_1.setText("Acquisition order");
      slicechannelOrderingLabel_1.setBounds(251, 3, 130, 14);
      getContentPane().add(slicechannelOrderingLabel_1);

      singleFrameCheckBox_ = new JCheckBox();
      singleFrameCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
         }
      });
      singleFrameCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      singleFrameCheckBox_.setText("Display only last frame");
      singleFrameCheckBox_.setEnabled(false);
      singleFrameCheckBox_.setBounds(242, 421, 183, 23);
      getContentPane().add(singleFrameCheckBox_);

      afCheckBox_ = new JCheckBox();
      afCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            applySettings();
         }
      });
      afCheckBox_.setFont(new Font("Dialog", Font.PLAIN, 10));
      afCheckBox_.setText("Autofocus");
      afCheckBox_.setBounds(82, 101, 86, 19);
      afCheckBox_.setEnabled(false);
      getContentPane().add(afCheckBox_);

      // load acquistion settings
      loadAcqSettings();

      // update summary
      updateGUIContents();
      
      // add update event listeners
      multiPosCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            applySettings();
         }
      });
      sliceModeCombo_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            applySettings();
         }
      });
      posModeCombo_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            applySettings();
         }
      });

      afButton = new JButton();
      afButton.setToolTipText("Set autofocus options");
      afButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            afOptions();
         }
      });
      afButton.setIcon(SwingResourceManager.getIcon(AcqControlDlg.class, "icons/wrench_orange.png"));
      afButton.setMargin(new Insets(2, 5, 2, 5));
      afButton.setFont(new Font("Dialog", Font.PLAIN, 10));
      afButton.setBounds(174, 102, 43, 24);
      getContentPane().add(afButton);

      listButton = new JButton();
      listButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            gui_.showXYPositionList();
         }
      });
      listButton.setToolTipText("Open XY list dialog");
      listButton.setIcon(SwingResourceManager.getIcon(AcqControlDlg.class, "icons/application_view_list.png"));
      listButton.setMargin(new Insets(2, 5, 2, 5));
      listButton.setFont(new Font("Dialog", Font.PLAIN, 10));
      listButton.setBounds(174, 76, 43, 24);
      getContentPane().add(listButton);

      final JButton stopButton = new JButton();
      stopButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            acqEng_.stop(true);
         }
      });
      stopButton.setText("Stop");
      stopButton.setBounds(405, 69, 93, 22);
      getContentPane().add(stopButton);
   }

   /** Called when a field's "value" property changes. Causes the Summary to be updated*/
   public void propertyChange(PropertyChangeEvent e) {
      // update summary
      applySettings();
      summaryTextArea_.setText(acqEng_.getVerboseSummary());
   }

   protected void afOptions() {
      Autofocus af = acqEng_.getAutofocus();
      if (af == null) {
         JOptionPane.showMessageDialog(this, "Autofocus plugin not installed.");
         return;
      }

      af.showOptionsDialog();
   }

   public boolean inArray(String member, String[] group) {
	   for (int i=0; i<group.length; i++)
		   if (member.equals(group[i]))
			   return true;
	   return false;
   }
   
   public void updateGroupsCombo() {
      String groups[] = acqEng_.getAvailableGroups();
      channelGroupCombo_.setModel(new DefaultComboBoxModel(groups));
      if (!inArray(acqEng_.getChannelGroup(), groups))
    	  acqEng_.setChannelGroup(groups[0]);
      channelGroupCombo_.setSelectedItem(acqEng_.getChannelGroup());
   }

   public void updateChannelAndGroupCombo() {
      updateGroupsCombo();
      model_.cleanUpConfigurationList();
   }
   
   public void loadAcqSettings() {
      // load acquisition engine preferences
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
      singleFrameCheckBox_.setSelected(acqPrefs_.getBoolean(ACQ_SINGLE_FRAME, false));
      if (saveFilesCheckBox_.isSelected())
         singleFrameCheckBox_.setEnabled(true);
      else
      {
         singleFrameCheckBox_.setEnabled(false);
         singleFrameCheckBox_.setSelected(false);
      }
      nameField_.setText(acqPrefs_.get(ACQ_DIR_NAME, "Untitled"));
      rootField_.setText(acqPrefs_.get(ACQ_ROOT_NAME, "C:/AcquisitionData"));
      
      acqEng_.setSliceMode(acqPrefs_.getInt(ACQ_SLICE_MODE, acqEng_.getSliceMode()));
      acqEng_.setPositionMode(acqPrefs_.getInt(ACQ_POSITION_MODE, acqEng_.getPositionMode()));
      acqEng_.enableAutoFocus(acqPrefs_.getBoolean(ACQ_AF_ENABLE, acqEng_.isAutoFocusEnabled()));
      if (acqEng_.isMultiPositionEnabled())
         afCheckBox_.setEnabled(true);
      else
      {
         afCheckBox_.setEnabled(false);
         afCheckBox_.setSelected(false);
         acqEng_.enableAutoFocus(false);
      }

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
         int skip = acqPrefs_.getInt(CHANNEL_SKIP_PREFIX + i, defaultChannel.skipFactorFrame_);
         Color c = new Color(r, g, b);
         acqEng_.addChannel(name, exp, zOffset, s8, s16, skip, c);
      }
   }

   public void saveAcqSettings() {
      try {
         acqPrefs_.clear();
      } catch (BackingStoreException e) {
         // TODO: not sure what to do here
      }

      acqPrefs_.putInt(ACQ_NUMFRAMES, acqEng_.getNumFrames());
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
      acqPrefs_.putBoolean(ACQ_SINGLE_FRAME, singleFrameCheckBox_.isSelected());
      acqPrefs_.put(ACQ_DIR_NAME, nameField_.getText());
      acqPrefs_.put(ACQ_ROOT_NAME, rootField_.getText());
      
      acqPrefs_.putInt(ACQ_SLICE_MODE, acqEng_.getSliceMode());
      acqPrefs_.putInt(ACQ_POSITION_MODE, acqEng_.getPositionMode());
      acqPrefs_.putBoolean(ACQ_AF_ENABLE, acqEng_.isAutoFocusEnabled());

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
         acqPrefs_.putInt(CHANNEL_SKIP_PREFIX + i, channel.skipFactorFrame_);
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
   
   public void loadAcqSettingsFromFile(String path) {
      acqFile_ = new File(path);
      try {
         FileInputStream in = new FileInputStream(acqFile_);
         acqPrefs_.clear();
         Preferences.importPreferences(in);
         loadAcqSettings();
         updateGUIContents();
         in.close();
         acqDir_ = acqFile_.getParent();
         if (acqDir_ != null)
            prefs_.put(ACQ_FILE_DIR, acqDir_);
      } catch (Exception e) {
         handleException(e);
         return;
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

   public void runAcquisition() {
      if (acqEng_.isAcquisitionRunning()) {
         JOptionPane.showMessageDialog(this, "Unable to start the new acquisition task: previous acquisition still in progress.");
         return;
      }

      try {
         applySettings();
         acqEng_.acquire();
      }  catch (MMAcqDataException e) {
         handleException(e);
         return;
      } catch(MMException e) {
         handleException(e);
         return;
      }
   }
   
   public void runAcquisition(String acqName, String acqRoot) {
      if (acqEng_.isAcquisitionRunning()) {
         JOptionPane.showMessageDialog(this, "Unable to start the new acquisition task: previous acquisition still in progress.");
         return;
      }

      try {
         applySettings();
         acqEng_.setDirName(acqName);
         acqEng_.setRootName(acqRoot);
         acqEng_.setSaveFiles(true);
         acqEng_.acquire();
      } catch(MMException e) {
         handleException(e);
         return;
      } catch (MMAcqDataException e) {
         handleException(e);
         return;
      }
   }
   
   public boolean runWellScan(WellAcquisitionData wad) {
      if (acqEng_.isAcquisitionRunning()) {
         JOptionPane.showMessageDialog(this, "Unable to start the new acquisition task: previous acquisition still in progress.");
         return false;
      }

      try {
         applySettings();
         acqEng_.setSaveFiles(true);
         acqEng_.acquireWellScan(wad);
      } catch(MMException e) {
         handleException(e);
         return false;
      } catch (MMAcqDataException e) {
         handleException(e);
         return false;
      }
      
      return true;
   }
   
   
   public boolean isAcquisitionRunning() {
      return acqEng_.isAcquisitionRunning();
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
      acqEng_.setSingleFrame(singleFrameCheckBox_.isSelected());
      acqEng_.setDirName(nameField_.getText());
      acqEng_.setRootName(rootField_.getText());

      // update summary
      summaryTextArea_.setText(acqEng_.getVerboseSummary()); 
      acqEng_.setComment(commentTextArea_.getText());
      
      if (multiPosCheckBox_.isSelected())
         posModeCombo_.setEnabled(true);
      else
         posModeCombo_.setEnabled(false);
      
      if (afCheckBox_.isSelected())
         acqEng_.enableAutoFocus(true);
      else
         acqEng_.enableAutoFocus(false);
      
      acqEng_.setParameterPreferences(acqPrefs_);
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

   /**
    * This method is called from the Options dialog, to set the background style
    */
   public void setBackgroundStyle(String style) {
      setBackground(guiColors_.background.get(style));
      repaint();
   }

} 
