///////////////////////////////////////////////////////////////////////////////
//FILE:          MultiDPanel.java
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

import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.ColorConfigEditor;
import org.micromanager.asidispim.Data.ColorTableModel;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MulticolorModes;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.utils.ReportingUtils;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import mmcorej.CMMCore;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;


/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public class MultiDPanel extends ListeningJPanel {
   private final CMMCore core_;
   private final Devices devices_;
   private final Properties props_;
   private final Prefs prefs_;
   
   private final JPanel colorPanel_;
   private final JComboBox colorGroup_;
   private final JComboBox colorMode_;
   private final ColorTableModel colorTableModel_;
   private final JTable colorTable_;
   private final JScrollPane colorTablePane_;
   
   
   /**
    * MultiD panel constructor.
    */
   public MultiDPanel(ScriptInterface gui, Devices devices, Properties props, Prefs prefs) {
      super (MyStrings.PanelNames.MULTID.toString(),
            new MigLayout(
              "", 
              "[right]",
              "[]6[]"));
      core_ = gui.getMMCore();
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
      
      
      PanelUtils pu = new PanelUtils(prefs_, props_, devices_);
      
      // start channel sub-panel
      colorPanel_ = new JPanel(new MigLayout(
              "",
              "[right]4[left]",
              "[]8[]"));
      
      colorPanel_.setBorder(PanelUtils.makeTitledBorder("Channels"));
      
      colorPanel_.add(new JLabel("Color group:"));
//      colorGroup_ = new JComboBox();
      String groups[] = getAvailableGroups();
      colorGroup_  = pu.makeDropDownBox(groups, Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_MULTICOLOR_GROUP, "");
            
      updateGroupsCombo();
      colorGroup_.addItemListener(new ItemListener() {
         @Override
         public void itemStateChanged(ItemEvent e) {
            // clear configs when changing the color group
            if (e.getStateChange() == ItemEvent.SELECTED) {
               for (int i=0; i<colorTable_.getRowCount(); i++) {
                  colorTable_.setValueAt("", i, ColorTableModel.columnIndex_config);
               }
            }
         }
      });
      colorPanel_.add(colorGroup_, "wrap");
      
      
      colorTableModel_ = new ColorTableModel(prefs_, panelName_);
      colorTable_ = new JTable(colorTableModel_);
      colorTable_.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
      colorTable_.getColumnModel().getColumn(ColorTableModel.columnIndex_useChannel).setPreferredWidth(50);
      colorTable_.getColumnModel().getColumn(ColorTableModel.columnIndex_config).setPreferredWidth(150);
      colorTable_.getColumnModel().getColumn(ColorTableModel.columnIndex_pLogicNum).setPreferredWidth(50);
      DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
      centerRenderer.setHorizontalAlignment(JLabel.CENTER);
      colorTable_.getColumnModel().getColumn(ColorTableModel.columnIndex_pLogicNum).setCellRenderer(centerRenderer);
      colorTable_.getColumnModel().getColumn(ColorTableModel.columnIndex_config).setCellEditor(new ColorConfigEditor(colorGroup_, core_));
      
      colorTablePane_ = new JScrollPane(colorTable_);
      colorTablePane_.setPreferredSize(new Dimension(300,100));
      colorTablePane_.setViewportView(colorTable_);
      colorPanel_.add(colorTablePane_, "span 2, wrap");
      
      colorPanel_.add(new JLabel("Change color:"));
      MulticolorModes colorModes = new MulticolorModes(devices_, props_,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_MULTICOLOR_MODE,
            MulticolorModes.Keys.VOLUME);
      colorMode_ = colorModes.getComboBox(); 
      colorPanel_.add(colorMode_, "wrap");
      
      // end channel sub-panel
      
      this.add(colorPanel_);
      
   }// constructor

      
   private final void updateGroupsCombo() {
      Object selection = colorGroup_.getSelectedItem();
      String groups[] = getAvailableGroups();
      if (groups.length != 0) {
         colorGroup_.setModel(new DefaultComboBoxModel(groups));
      }
      colorGroup_.setSelectedItem(selection);
   }


   /**
    * gets all valid groups from Core-ChannelGroup that have more than 1 preset ("config")
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
