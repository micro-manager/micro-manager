///////////////////////////////////////////////////////////////////////////////
//FILE:          MultiChannelSubPanel.java
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
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import org.micromanager.acquisition.ComponentTitledBorder;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.ChannelConfigEditor;
import org.micromanager.asidispim.Data.ChannelTableModel;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MultichannelModes;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.MyDialogUtils;
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
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import mmcorej.CMMCore;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;

import org.micromanager.asidispim.Data.ChannelSpec;


/**
 * Creates panel for channel selection
 * 
 * @author Jon
 */
@SuppressWarnings("serial")
public class MultiChannelSubPanel extends ListeningJPanel {
   private final CMMCore core_;
   private final Devices devices_;
   private final Properties props_;
   private final Prefs prefs_;
   private final JCheckBox useChannelsCB_;
   private final ChannelTableModel channelTableModel_;
   private final JComboBox channelGroup_;
   private ChannelSpec[] usedChannels_ = new ChannelSpec[0];
   private int nextChannelIndex_ = 0;
   private final List<ListeningJPanel> panels_;
   
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
   public MultiChannelSubPanel(ScriptInterface gui, Devices devices, 
           Properties props, Prefs prefs) {
      super (MyStrings.PanelNames.CHANNELS_SUBPANEL.toString(),
            new MigLayout(
                  "",
                  "[right]10[left]",
                  "[]8[]"));
      core_ = gui.getMMCore();
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
      panels_ = new ArrayList<ListeningJPanel>();
      
      // added listener where we should re-calculate the displayed durations
      ChangeListener recalculateTimingDisplayCL = new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            updateDurationLabels();
         }
      };
      
      final JComboBox channelMode;
      final JTable channelTable;
      final JScrollPane channelTablePane;

      
      PanelUtils pu = new PanelUtils(prefs_, props_, devices_);
      
      useChannelsCB_ = pu.makeCheckBox("Channels",
            Properties.Keys.PREFS_USE_MULTICHANNEL, panelName_, false);
      useChannelsCB_.setToolTipText("Contact ASI for details; advanced features require PLogic card");
      useChannelsCB_.setFocusPainted(false);
      useChannelsCB_.addChangeListener(recalculateTimingDisplayCL);
      ComponentTitledBorder componentBorder = 
            new ComponentTitledBorder(useChannelsCB_, this, 
                  BorderFactory.createLineBorder(ASIdiSPIM.borderColor)); 
      this.setBorder(componentBorder);
      
      this.add(new JLabel("Channel group:"));
      String groups[] = getAvailableGroups();
      channelGroup_  = pu.makeDropDownBox(groups, Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_MULTICHANNEL_GROUP, "");
            
      updateGroupsCombo(); 
            
      channelTableModel_ = new ChannelTableModel(prefs_, panelName_,
              (String) channelGroup_.getSelectedItem(), this);
      channelTable = new JTable(channelTableModel_);

      channelGroup_.addItemListener(new ItemListener() {
         @Override
         public void itemStateChanged(ItemEvent e) {
            // clear configs when changing the channel group
            if (e.getStateChange() == ItemEvent.SELECTED) {
               channelTableModel_.setChannelGroup((String) 
                       channelGroup_.getSelectedItem());
               channelTableModel_.fireTableDataChanged();
            }
         }
      });
      this.add(channelGroup_, "wrap");
      
      // put table and buttons in its own miglayout
      final JPanel tablePanel = new JPanel();
      tablePanel.setLayout(new MigLayout (
                  "flowy, ins 0",
                  "",
                  "") );
      channelTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      TableColumn column_useChannel = channelTable.getColumnModel().getColumn(
              ChannelTableModel.columnIndex_useChannel);
      TableColumn column_config = channelTable.getColumnModel().getColumn(
              ChannelTableModel.columnIndex_config);
      column_useChannel.setPreferredWidth(40);
      column_config.setPreferredWidth(155);
      column_useChannel.setCellRenderer(new UseChannelTableCellRenderer());
      column_config.setCellRenderer(new DisplayDisabledTableCellRenderer());
      column_config.setCellEditor(new ChannelConfigEditor(channelGroup_, core_));
      
      channelTablePane = new JScrollPane(channelTable);
      channelTablePane.setPreferredSize(new Dimension(200,75));
      channelTablePane.setViewportView(channelTable);
      tablePanel.add(channelTablePane, "wrap");
      
      Dimension buttonSize = new Dimension(30, 20);
      JButton plusButton = new JButton("+");
      plusButton.setMargin(new Insets(0,0,0,0));
      plusButton.setPreferredSize(buttonSize);
      plusButton.setMaximumSize(buttonSize);
      plusButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            channelTableModel_.addChannel((String) channelGroup_.getSelectedItem());
            channelTableModel_.fireTableDataChanged();
         }
      }
      );
      tablePanel.add(plusButton, "aligny top, split 2");
      
      JButton minusButton = new JButton("-");
      minusButton.setMargin(new Insets(0,0,0,0));
      minusButton.setPreferredSize(buttonSize);
      minusButton.setMaximumSize(buttonSize);
      minusButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            int selectedRows[] = channelTable.getSelectedRows();
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
      
      this.add(tablePanel, "span 2, align left, wrap");
      
      this.add(new JLabel("Change channel:"));
      MultichannelModes channelModes = new MultichannelModes(devices_, props_,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_MULTICHANNEL_MODE,
            MultichannelModes.Keys.VOLUME);
      channelMode = channelModes.getComboBox();
      this.add(channelMode, "wrap");
      
      // enable/disable panel elements depending on checkbox state
      final Component[] channelPaneComponents = this.getComponents();
      useChannelsCB_.addActionListener(new ActionListener(){ 
         @Override
         public void actionPerformed(ActionEvent e){ 
            boolean enabled = useChannelsCB_.isSelected();
            for (Component comp : channelPaneComponents) {
               comp.setEnabled(enabled);
            }
            for (Component comp : tablePanel.getComponents()) {
               comp.setEnabled(enabled);
            }
            channelGroup_.setEnabled(enabled);
            channelTable.setEnabled(enabled);
            channelMode.setEnabled(enabled);
         } 
      });
      // initialize GUI for muli-channel enabled checkbox
      useChannelsCB_.doClick();
      useChannelsCB_.doClick();
      
   }// constructor
   
   /**
    * gets the state of the enable/disable checkbox
    * @return state of the enable/disable checkbox
    */
   public boolean isPanelEnabled() {
      return useChannelsCB_.isSelected();
   }

   /**
    * Sets up the combo box for channel group.
    */
   // TODO add listener to update whenever group is added or other triggering event
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
    * ("config").  Different from the MDA method of getting valid groups.
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
   
   public String getChannelGroup() {
      return channelGroup_.getSelectedItem().toString();
   }
   
   /**
    * @return array of channels that are currently set be "used".
    * Ordered same as in the GUI.
    */
   public ChannelSpec[] getUsedChannels() {
      return channelTableModel_.getUsedChannels();
   }
   
   /**
    * call before starting to cycle through channels using selectNextChannel()
    */
   public void initializeChannelCycle() {
      usedChannels_  = channelTableModel_.getUsedChannels();
      nextChannelIndex_  = 0;
   }
   
   /**
    * Takes care of actually selecting next channel in table.
    * Called by acquisition code.  Blocks until devices ready.
    */
   public void selectNextChannel() {
      ChannelSpec channel = usedChannels_[nextChannelIndex_];
      try {
         core_.setConfig(channelGroup_.getSelectedItem().toString(), channel.config_);
         core_.waitForConfig(channelGroup_.getSelectedItem().toString(), channel.config_);
      } catch (Exception e) {
         MyDialogUtils.showError(e, "Couldn't select preset " + channel.config_ +
               "of channel group " + channelGroup_.getSelectedItem().toString());
      }
      nextChannelIndex_++;
      if (nextChannelIndex_ == usedChannels_.length) {
         nextChannelIndex_ = 0;
      }
   }
   
   /**
    * Gets the current configuration/preset from the selected channel group, even if that
    * preset isn't represented in the channel table.  Thus we can go back to the original
    * preset after changing it via selectNextChannel.
    * @return
    */
   public String getCurrentConfig() {
      try {
         return core_.getCurrentConfigFromCache(channelGroup_.getSelectedItem().toString());
      } catch (Exception e) {
         ReportingUtils.logError("Failed to get current configuration");
      }
      return null;
   }
   
   public void setConfig(String config) {
      try {
         core_.setConfig(channelGroup_.getSelectedItem().toString(), config);
      } catch (Exception e) {
         ReportingUtils.logError(e, "Failed to set config.");
      }
   }

   
   @Override
   public void saveSettings() {
   }
   
   /**
    * Gets called when this tab gets focus.
    */
   @Override
   public void gotSelected() {
   }

   public void addDurationLabelListener(ListeningJPanel panel) {
      panels_.add(panel);      
   }
   
   public void updateDurationLabels() {
      for (ListeningJPanel panel : panels_) {
         panel.refreshDisplay();
      }
   }

}
