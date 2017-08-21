///////////////////////////////////////////////////////////////////////////////
//FILE:          ChannelConfigRenderer.java
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
//
package org.micromanager.asidispim.Data;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

import mmcorej.CMMCore;
import mmcorej.StrVector;


/**
 * @author Jon
 */
@SuppressWarnings("serial")
public class ChannelConfigEditor extends AbstractCellEditor implements TableCellEditor {
   private final JComboBox channelGroup_;  // this is the combo box selecting 
            //the group, NOT the combo box selecting which preset of the group
   private final JComboBox channelPreset_;  // this is the combo box used by the table 
            // to select the appropriate preset
   private final CMMCore core_;
   
   public ChannelConfigEditor(JComboBox cb, CMMCore core) {
      channelGroup_ = cb;
      channelPreset_ = new JComboBox();
      channelPreset_.addActionListener(new ConfigActionListener());
      core_ = core;
  }
   
   class ConfigActionListener implements ActionListener {
      @Override
      public void actionPerformed(ActionEvent e) {
         fireEditingStopped();
      }
   }
   
   /** 
    * Called when cell value is edited by user.
    * @param rowIndex
    * @param colIndex
    */
   @Override
   public Component getTableCellEditorComponent(JTable table, Object value,
           boolean isSelected, int rowIndex, int colIndex) {

      // https://stackoverflow.com/a/3055930
      if (value == null) {
         return null;
      }

      channelPreset_.removeAllItems();
      StrVector configs = core_.getAvailableConfigs(channelGroup_.getSelectedItem().toString());
      for (String config : configs) {
         channelPreset_.addItem(config);
      }
      return channelPreset_;
   }
   
   /** 
    * Called when editing is completed.  Must return the new value to be stored in the cell.
    */
   @Override
   public Object getCellEditorValue() {
      return channelPreset_.getSelectedItem();
   }

}

