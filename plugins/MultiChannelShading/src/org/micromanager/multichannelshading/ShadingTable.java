///////////////////////////////////////////////////////////////////////////////
//FILE:          ShadingTable.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     MultiChannelShading plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Kurt Thorn, Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2014
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

package org.micromanager.multichannelshading;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import net.miginfocom.swing.MigLayout;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author nico
 */

public class ShadingTable extends JTable {

   private final ScriptInterface gui_;

   private static final String buttonCellLayoutConstraints =
         "insets 0, align center, center";

   private class LoadFileButtonCellRenderer implements TableCellRenderer {
      private final JPanel panel_ = new JPanel();
      private final JButton button_;

      public LoadFileButtonCellRenderer(MultiChannelShadingMigForm form) {         
         button_ = form.mcsButton(form.getButtonDimension(), 
                 form.getButtonFont());
         button_.setText("...");
         panel_.setLayout(new MigLayout(buttonCellLayoutConstraints));
         panel_.add(button_,"gapx push");
      }

      @Override
      public Component getTableCellRendererComponent(JTable table,
            Object dataProcessor, boolean isSelected, boolean hasFocus,
            int row, int column) {
         if (isSelected) {
            panel_.setBackground(table.getSelectionBackground());
         }
         else {
            panel_.setBackground(table.getBackground());
         } 
         return panel_;
      }
   }

   private class LoadFileButtonCellEditor extends AbstractCellEditor
         implements TableCellEditor, ActionListener {

      private int row_;
      private final MultiChannelShadingMigForm form_;
      private final JPanel panel_ = new JPanel();
      private final JButton button_ ;

      @SuppressWarnings("LeakingThisInConstructor")
      public LoadFileButtonCellEditor(MultiChannelShadingMigForm form) {     
         form_ = form;
         button_ = form_.mcsButton(form_.getButtonDimension(), 
                 form_.getButtonFont());
         button_.setText("...");
         row_ = -1;
         panel_.setLayout(new MigLayout(buttonCellLayoutConstraints));
         panel_.add(button_, "gapx push");
         button_.addActionListener(this);
      }

      @Override
      public void actionPerformed(ActionEvent e) {
         form_.flatFieldButtonActionPerformed(row_);
         fireEditingStopped();
      }

      @Override
      public Object getCellEditorValue() {
         return null;
      }

      @Override
      public Component getTableCellEditorComponent(JTable table,
            Object someObject, boolean isSelected, int row, int column) {
         row_ = row;
         panel_.setBackground(table.getSelectionBackground());
         return panel_;
      }
   }
   
   private class PresetCellEditor extends AbstractCellEditor 
   implements TableCellEditor, ActionListener {
      private final JPanel panel_ = new JPanel();
      private final JComboBox comboBox_ = new JComboBox();
      private final ShadingTableModel model_;
      private int row_;
      private String selectedPreset_;
      
      @SuppressWarnings("LeakingThisInConstructor")
      public PresetCellEditor(ScriptInterface gui, ShadingTableModel model) {
         model_ = model;
         row_ = -1;
         panel_.setLayout(new MigLayout("fill, insets 0, align center, center"));
         panel_.add(comboBox_);
         comboBox_.addActionListener(this);
      }
      
      @Override
      public Object getCellEditorValue() {
         return selectedPreset_;
      }

      @Override
      public Component getTableCellEditorComponent(JTable table, Object value, 
              boolean isSelected, int row, int column) {
         row_ = row;
         String[] presets = gui_.getMMCore().getAvailableConfigs(
                 model_.getChannelGroup()).toArray();
         // remove presets that are already in use
         String[] usedPresets = model_.getUsedPresets(row);
         String[] comboPresets = new String[presets.length - usedPresets.length];
         int index = 0;
         for (String preset : presets) {
            boolean found = false;
            for (String usedPreset : usedPresets) {
               if (preset.equals(usedPreset) ) {
                  found = true;
               }
            }
            if (!found) {
               comboPresets[index] = preset;
               index++;
            }
         }
         comboBox_.setModel(new javax.swing.DefaultComboBoxModel(comboPresets));       
         String preset = (String) model_.getValueAt(row, column);
         comboBox_.setSelectedItem(preset);
         return panel_;
      }

      @Override
      public void actionPerformed(ActionEvent e) {
         selectedPreset_ = (String) comboBox_.getSelectedItem();
         if (selectedPreset_ != null) {
            model_.setValueAt(selectedPreset_, row_, 0);
            fireEditingStopped();
         }
      }
   }

   private PresetCellEditor presetCellEditor_;
   private LoadFileButtonCellEditor loadFileButtonCellEditor_;
   
   ShadingTable(ScriptInterface gui, ShadingTableModel model, 
           MultiChannelShadingMigForm form) {
      super(model);
      gui_ = gui;

      setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

      //Editor for column 0 (preset combobox)
      presetCellEditor_ = new PresetCellEditor(gui, model);
      getColumnModel().getColumn(0).setCellEditor(presetCellEditor_);
                  
      // Renderer and Editor for column 2 (button)
      LoadFileButtonCellRenderer loadFileButtonRenderer = 
              new LoadFileButtonCellRenderer(form);
      getColumnModel().getColumn(2).setCellRenderer(loadFileButtonRenderer);

      loadFileButtonCellEditor_ = 
              new LoadFileButtonCellEditor(form);
      getColumnModel().getColumn(2).setCellEditor(loadFileButtonCellEditor_);
      
      this.setRowHeight((int) (this.getRowHeight() * 1.5));

   }
   
   public void stopCellEditing() {
      presetCellEditor_.stopCellEditing();
      loadFileButtonCellEditor_.stopCellEditing();
   }

}
