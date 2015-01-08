///////////////////////////////////////////////////////////////////////////////
//FILE:          PropertyEditor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 20, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006, 2014
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
//

package org.micromanager;

/**
 * @author Nenad Amodaj
 * PropertyEditor provides UI for manipulating sets of device properties
 */

import java.awt.Font;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SpringLayout;
import javax.swing.border.BevelBorder;
import javax.swing.table.TableColumn;

import com.swtdesigner.SwingResourceManager;

import mmcorej.CMMCore;
import mmcorej.StrVector;

import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMListenerAdapter;
import org.micromanager.utils.MMFrame;
import org.micromanager.utils.PropertyValueCellEditor;
import org.micromanager.utils.PropertyValueCellRenderer;
import org.micromanager.utils.PropertyItem;
import org.micromanager.utils.PropertyTableData;
import org.micromanager.utils.ShowFlags;

import org.micromanager.utils.PropertyNameCellRenderer;
import org.micromanager.utils.ReportingUtils;

/**
 * JFrame based component for generic manipulation of device properties.
 * Represents the entire system state as a list of triplets:
 * device - property - value
 *
 * aka the "Device/Property Browser"
 */
public class PropertyEditor extends MMFrame {
   private final SpringLayout springLayout;
   private static final long serialVersionUID = 1507097881635431043L;
   
   private JTable table_;
   private PropertyEditorTableData data_;
   private ShowFlags flags_;
   
   private static final String PREF_SHOW_READONLY = "show_readonly";
   private JCheckBox showCamerasCheckBox_;
   private JCheckBox showShuttersCheckBox_;
   private JCheckBox showStagesCheckBox_;
   private JCheckBox showStateDevicesCheckBox_;
   private JCheckBox showOtherCheckBox_;
   private JCheckBox showReadonlyCheckBox_;
   private final JScrollPane scrollPane_;
   private ScriptInterface gui_;
   
   public class myMMListener extends MMListenerAdapter {

      @Override
      public void propertiesChangedAlert() {
         // avoid re-executing a refresh because of callbacks while we are updating
         if (!data_.updating()) {
            refresh();
         }
      }

      @Override
      public void propertyChangedAlert(String device, String property, String value) {
         data_.update(device, property, value);
      }
   }

   private final myMMListener myMMListener_ = new myMMListener();

   public void setGui(ScriptInterface gui) {
      gui_ = gui;
      gui_.addMMListener(myMMListener_);
   }



   public PropertyEditor() {
      super();
      
      flags_ = new ShowFlags();
      flags_.load(getPrefsNode());
      
      setIconImage(SwingResourceManager.getImage(PropertyEditor.class, "icons/microscope.gif"));
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setSize(551, 514);
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            Preferences prefs = getPrefsNode();
            prefs.putBoolean(PREF_SHOW_READONLY, showReadonlyCheckBox_.isSelected());
            flags_.save(getPrefsNode());
         }
         @Override
         public void windowOpened(WindowEvent e) {
            // restore values from the previous session
            Preferences prefs = getPrefsNode();
            showReadonlyCheckBox_.setSelected(prefs.getBoolean(PREF_SHOW_READONLY, true));
            data_.update(false);
            data_.fireTableStructureChanged();
        }
      });
      setTitle("Device Property Browser");

      loadAndRestorePosition(100, 100, 400, 300);
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

      scrollPane_ = new JScrollPane();
      scrollPane_.setFont(new Font("Arial", Font.PLAIN, 10));
      scrollPane_.setBorder(new BevelBorder(BevelBorder.LOWERED));
      getContentPane().add(scrollPane_);
      springLayout.putConstraint(SpringLayout.EAST, scrollPane_, -5, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, scrollPane_, 5, SpringLayout.WEST, getContentPane());
      
      table_ = new JTable();
      table_.setAutoCreateColumnsFromModel(false);
      
      final JButton refreshButton = new JButton();
      refreshButton.setIcon(SwingResourceManager.getIcon(PropertyEditor.class, "/org/micromanager/icons/arrow_refresh.png"));
      refreshButton.setFont(new Font("Arial", Font.PLAIN, 10));
      getContentPane().add(refreshButton);
      springLayout.putConstraint(SpringLayout.EAST, refreshButton, 285, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, refreshButton, 185, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, refreshButton, 32, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, refreshButton, 9, SpringLayout.NORTH, getContentPane());
      refreshButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            refresh();
         }
      });
      refreshButton.setText("Refresh! ");

      showReadonlyCheckBox_ = new JCheckBox();
      showReadonlyCheckBox_.setFont(new Font("Arial", Font.PLAIN, 10));
      showReadonlyCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // show/hide read-only properties
            data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());
            data_.update(false);
            data_.fireTableStructureChanged();
          }
      });
      showReadonlyCheckBox_.setText("Show read-only properties");
      getContentPane().add(showReadonlyCheckBox_);
      springLayout.putConstraint(SpringLayout.EAST, showReadonlyCheckBox_, 358, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showReadonlyCheckBox_, 185, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, showReadonlyCheckBox_, 63, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, showReadonlyCheckBox_, 40, SpringLayout.NORTH, getContentPane());
      
      // restore values from the previous session
      Preferences prefs = getPrefsNode();
      showReadonlyCheckBox_.setSelected(prefs.getBoolean(PREF_SHOW_READONLY, true));

      showCamerasCheckBox_ = new JCheckBox();
      showCamerasCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      showCamerasCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            flags_.cameras_ = showCamerasCheckBox_.isSelected();
            data_.update(false);
         }
      });
      showCamerasCheckBox_.setText("Show cameras");
      getContentPane().add(showCamerasCheckBox_);
      springLayout.putConstraint(SpringLayout.SOUTH, showCamerasCheckBox_, 28, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showCamerasCheckBox_, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, showCamerasCheckBox_, 111, SpringLayout.WEST, getContentPane());

      showShuttersCheckBox_ = new JCheckBox();
      showShuttersCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      showShuttersCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            flags_.shutters_ = showShuttersCheckBox_.isSelected();
            data_.update(false);
         }
      });
      showShuttersCheckBox_.setText("Show shutters");
      getContentPane().add(showShuttersCheckBox_);
      springLayout.putConstraint(SpringLayout.EAST, showShuttersCheckBox_, 111, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showShuttersCheckBox_, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, showShuttersCheckBox_, 50, SpringLayout.NORTH, getContentPane());

      showStagesCheckBox_ = new JCheckBox();
      showStagesCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      showStagesCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            flags_.stages_ = showStagesCheckBox_.isSelected();
            data_.update(false);
         }
      });
      showStagesCheckBox_.setText("Show stages");
      getContentPane().add(showStagesCheckBox_);
      springLayout.putConstraint(SpringLayout.EAST, showStagesCheckBox_, 111, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showStagesCheckBox_, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, showStagesCheckBox_, 73, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, showStagesCheckBox_, 50, SpringLayout.NORTH, getContentPane());

      showStateDevicesCheckBox_ = new JCheckBox();
      showStateDevicesCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      showStateDevicesCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            flags_.state_ = showStateDevicesCheckBox_.isSelected();
            data_.update(false);
         }
      });
      showStateDevicesCheckBox_.setText("Show discrete changers");
      getContentPane().add(showStateDevicesCheckBox_);
      springLayout.putConstraint(SpringLayout.EAST, showStateDevicesCheckBox_, 200, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showStateDevicesCheckBox_, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, showStateDevicesCheckBox_, 95, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, showStateDevicesCheckBox_, 72, SpringLayout.NORTH, getContentPane());

      showOtherCheckBox_ = new JCheckBox();
      showOtherCheckBox_.setFont(new Font("", Font.PLAIN, 10));
      showOtherCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            flags_.other_ = showOtherCheckBox_.isSelected();
            data_.update(false);
         }
      });
      showOtherCheckBox_.setText("Show other devices");
      getContentPane().add(showOtherCheckBox_);
      springLayout.putConstraint(SpringLayout.EAST, showOtherCheckBox_, 155, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showOtherCheckBox_, 10, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, showOtherCheckBox_, 95, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, scrollPane_, -5, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, scrollPane_, 5, SpringLayout.SOUTH, showOtherCheckBox_);
   }
   


   protected void refresh() {
      data_.gui_ = gui_;
      data_.flags_ = flags_;
      data_.showUnused_ = true;
      data_.refresh(false);
   }

   public void updateStatus() {
      if (data_ != null)
         data_.update(false);
   }

    public void setCore(CMMCore core) {
        data_ = new PropertyEditorTableData(core, "", "", 1, 2, getContentPane());
        data_.gui_ = gui_;
        data_.flags_ = flags_;
        data_.showUnused_ = true;
        data_.setColumnNames("Property", "Value", "");

        table_ = new JTable();
        table_.setAutoCreateColumnsFromModel(false);
        table_.setModel(data_);
        scrollPane_.setViewportView(table_);

        table_.addColumn(new TableColumn(0, 200, new PropertyNameCellRenderer(), null));
        table_.addColumn(new TableColumn(1, 200, new PropertyValueCellRenderer(false), new PropertyValueCellEditor(false)));

        showCamerasCheckBox_.setSelected(flags_.cameras_);
        showStagesCheckBox_.setSelected(flags_.stages_);
        showShuttersCheckBox_.setSelected(flags_.shutters_);
        showStateDevicesCheckBox_.setSelected(flags_.state_);
        showOtherCheckBox_.setSelected(flags_.other_);

        data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());
    }
   
    public class PropertyEditorTableData extends PropertyTableData {
      public PropertyEditorTableData(CMMCore core, String groupName, String presetName,
         int PropertyValueColumn, int PropertyUsedColumn, Component parentComponent) {

         super(core, groupName, presetName, PropertyValueColumn, PropertyUsedColumn, false);
      }
   
      private static final long serialVersionUID = 1L;

      @Override
      public void setValueAt(Object value, int row, int col) {
         PropertyItem item = propListVisible_.get(row);
         gui_.logMessage("Setting value " + value + " at row " + row);
         if (col == PropertyValueColumn_) {
            setValueInCore(item,value);
         }
         core_.updateSystemStateCache();
         refresh(true);
         gui_.refreshGUIFromCache();
         fireTableCellUpdated(row, col);
      }

      public void update (String device, String propName, String newValue) {
         PropertyItem item = getItem(device, propName);
         if (item != null) {
            item.value = newValue;
            // Better to call fireTableCellUpdated(row, col)???
            fireTableDataChanged();
         }
      }
      
      @Override
      public void update(ShowFlags flags, String groupName, String presetName, boolean fromCache) {  
         try {
            StrVector devices = core_.getLoadedDevices();
            propList_.clear();

            boolean liveMode = gui_.isLiveModeOn();
            gui_.enableLiveMode(false);
            for (int i=0; i<devices.size(); i++) { 
               if (data_.showDevice(flags, devices.get(i))) {
                  StrVector properties = core_.getDevicePropertyNames(devices.get(i));
                  for (int j=0; j<properties.size(); j++){
                     PropertyItem item = new PropertyItem();
                     item.readFromCore(core_, devices.get(i), properties.get(j), fromCache);

                     if ((!item.readOnly || showReadOnly_) && !item.preInit) {
                        propList_.add(item);
                     }
                  }
               }
            }

            updateRowVisibility(flags); 


            gui_.enableLiveMode(liveMode);
         } catch (Exception e) {
            handleException(e);
         }
         this.fireTableStructureChanged();

      }
   }

 
   private void handleException (Exception e) {
      ReportingUtils.showError(e, this);
   }
   
}

