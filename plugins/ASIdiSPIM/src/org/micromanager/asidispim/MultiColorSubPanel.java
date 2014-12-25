///////////////////////////////////////////////////////////////////////////////
//FILE:          MultiColorSubPanel.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;

import org.micromanager.acquisition.ComponentTitledBorder;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.ColorConfigEditor;
import org.micromanager.asidispim.Data.ChannelTableModel;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MultichannelModes;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.utils.ReportingUtils;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import mmcorej.CMMCore;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;


/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public class MultiColorSubPanel extends ListeningJPanel {
   private final CMMCore core_;
   private final Devices devices_;
   private final Properties props_;
   private final Prefs prefs_;
   
   private final JCheckBox useChannelsCB_;
   private final JComboBox channelGroup_;
   private final JComboBox channelMode_;
   private final ChannelTableModel channelTableModel_;
   private final JTable channelTable_;
   private final JScrollPane channelTablePane_;
   
   /**
    * if table is disabled then cell will be set to disabled too
    */
   class DisplayDisabledTableCellRenderer extends DefaultTableCellRenderer {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value,
            boolean selected, boolean focused, int rowIndex, int columnIndex)
      {
         setEnabled(table.isEnabled());
         return super.getTableCellRendererComponent(table, value, selected, 
                 focused, rowIndex, columnIndex);
      }
   };
   
   class UseChannelTableCellRenderer extends JCheckBox implements TableCellRenderer {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int rowIndex, int columnIndex) {
         if (!(value instanceof Boolean)) {
            return null;
         }
         JCheckBox check = new JCheckBox("", (Boolean) value);
         check.setEnabled(table.isEnabled());
         check.setOpaque(true);
         if (isSelected) {
            check.setBackground(table.getSelectionBackground());
            check.setOpaque(true);
         } else {
            check.setOpaque(false);
            check.setBackground(table.getBackground());
         }
         check.setHorizontalAlignment(SwingConstants.CENTER);
         return check;
      }
   }
   
   /**
    * MultiD panel constructor.
    * @param gui - Micro-Manager api
    * @param devices
    * @param props
    * @param prefs
    */
   public MultiColorSubPanel(ScriptInterface gui, Devices devices, 
           Properties props, Prefs prefs) {
      super (MyStrings.PanelNames.MULTID.toString(),
            new MigLayout(
                  "",
                  "[right]4[left]",
                  "[]8[]"));
      core_ = gui.getMMCore();
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
      
      
      PanelUtils pu = new PanelUtils(prefs_, props_, devices_);
      
      useChannelsCB_ = pu.makeCheckBox("Use multiple channels",
            Properties.Keys.PLUGIN_USE_MULTICOLOR, panelName_, false);
      useChannelsCB_.setToolTipText("Contact ASI for details; advanced features require PLogic card");
      useChannelsCB_.setFocusPainted(false); 
      ComponentTitledBorder componentBorder = 
            new ComponentTitledBorder(useChannelsCB_, this, 
                  BorderFactory.createLineBorder(ASIdiSPIM.borderColor)); 
      this.setBorder(componentBorder);
      
      this.add(new JLabel("Channel group:"));
      String groups[] = getAvailableGroups();
      channelGroup_  = pu.makeDropDownBox(groups, Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_MULTICOLOR_GROUP, "");
            
      updateGroupsCombo();
      channelGroup_.addItemListener(new ItemListener() {
         @Override
         public void itemStateChanged(ItemEvent e) {
            // clear configs when changing the channel group
            if (e.getStateChange() == ItemEvent.SELECTED) {
               for (int i=0; i<channelTable_.getRowCount(); i++) {
                  channelTable_.setValueAt("", i, ChannelTableModel.columnIndex_config);
               }
            }
         }
      });
      this.add(channelGroup_, "wrap");
      
      // put table and buttons in its own miglayout
      JPanel tablePanel = new JPanel();
      tablePanel.setLayout(new MigLayout (
                  "flowy",
                  "",
                  "") );
      channelTableModel_ = new ChannelTableModel(prefs_, panelName_);
      channelTable_ = new JTable(channelTableModel_);
      channelTable_.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      TableColumn column_useChannel = channelTable_.getColumnModel().getColumn(ChannelTableModel.columnIndex_useChannel);
      TableColumn column_config = channelTable_.getColumnModel().getColumn(ChannelTableModel.columnIndex_config);
      column_useChannel.setPreferredWidth(40);
      column_config.setPreferredWidth(100);
      column_useChannel.setCellRenderer(new UseChannelTableCellRenderer());
      column_config.setCellRenderer(new DisplayDisabledTableCellRenderer());
      column_config.setCellEditor(new ColorConfigEditor(channelGroup_, core_));
      
      channelTablePane_ = new JScrollPane(channelTable_);
      channelTablePane_.setPreferredSize(new Dimension(220,100));
      channelTablePane_.setViewportView(channelTable_);
      tablePanel.add(channelTablePane_, "wrap");
      
      JButton plusButton = new JButton("+");
      plusButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            channelTableModel_.addChannel();
            channelTableModel_.fireTableDataChanged();
         }
      }
      );
      tablePanel.add(plusButton, "aligny top, split 2");
      
      JButton minusButton = new JButton("-");
      minusButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            int selectedRows[] = channelTable_.getSelectedRows();
            for (int row : selectedRows) {
               channelTableModel_.removeChannel(row);
            }
            if (selectedRows.length > 0) {
               channelTableModel_.fireTableDataChanged();
            }
         }
      }
      );
      tablePanel.add(minusButton, "wrap");
      
      this.add(tablePanel, "span 3, wrap");
      
      this.add(new JLabel("Change channel:"));
      MultichannelModes channelModes = new MultichannelModes(devices_, props_,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_MULTICOLOR_MODE,
            MultichannelModes.Keys.VOLUME);
      channelMode_ = channelModes.getComboBox();
      this.add(channelMode_, "wrap");
      
      // enable/disable panel elements depending on checkbox state
      useChannelsCB_.addActionListener(new ActionListener(){ 
         @Override
         public void actionPerformed(ActionEvent e){ 
            boolean enabled = useChannelsCB_.isSelected();
            channelGroup_.setEnabled(enabled);
            channelTable_.setEnabled(enabled);
            channelMode_.setEnabled(enabled);
         } 
      });
      // initialize GUI for muli-channel enabled checkbox
      useChannelsCB_.doClick();
      useChannelsCB_.doClick();
      
   }// constructor

      
   private void updateGroupsCombo() {
      Object selection = channelGroup_.getSelectedItem();
      String groups[] = getAvailableGroups();
      if (groups.length != 0) {
         channelGroup_.setModel(new DefaultComboBoxModel(groups));
      }
      channelGroup_.setSelectedItem(selection);
   }


   /**
    * gets all valid groups from Core-ChannelGroup that have more than 1 preset 
    * ("config")
    */
   private String[] getAvailableGroups() {
      StrVector groups;
      try {
         groups = core_.getAllowedPropertyValues("Core", "ChannelGroup");
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return new String[0];
      }
      ArrayList<String> strGroups = new ArrayList<String>();
      strGroups.add("");
      for (String group : groups) {
         if (core_.getAvailableConfigs(group).size() > 1) {
            strGroups.add(group);
         }
      }
      return strGroups.toArray(new String[0]);
   }
   
   @Override
   public void saveSettings() {
   }
   
   /**
    * Gets called when this tab gets focus.  Sets the physical UI in the Tiger
    * controller to what was selected in this pane
    */
   @Override
   public void gotSelected() {
   }

}
