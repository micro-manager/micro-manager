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
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;

/**
 * Responsible for the table with channel presets and flatfield files.
 *
 * @author nico
 */
public class ShadingTable extends JTable {
   private final Studio studio_;

   private static final String BUTTONCELLLAYOUTCONSTRAINTS =
         "insets 0, align center, center";

   private class LoadFileButtonCellRenderer implements TableCellRenderer {
      private final JPanel panel_ = new JPanel();
      private final JButton button_;

      public LoadFileButtonCellRenderer(MultiChannelShadingMigForm form) {
         button_ = form.mcsButton(form.getButtonDimension(),
               form.getButtonFont());
         button_.setText("...");
         panel_.setLayout(new MigLayout(BUTTONCELLLAYOUTCONSTRAINTS));
         panel_.add(button_, "gapx push");
      }

      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object dataProcessor, boolean isSelected,
                                                     boolean hasFocus,
                                                     int row, int column) {
         if (isSelected) {
            panel_.setBackground(table.getSelectionBackground());
         } else {
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

      @SuppressWarnings("LeakingThisInConstructor")
      public LoadFileButtonCellEditor(MultiChannelShadingMigForm form) {
         form_ = form;
         JButton button = form_.mcsButton(form_.getButtonDimension(),
               form_.getButtonFont());
         button.setText("...");
         row_ = -1;
         panel_.setLayout(new MigLayout(BUTTONCELLLAYOUTCONSTRAINTS));
         panel_.add(button, "gapx push");
         button.addActionListener(this);
      }

      @Override
      public void actionPerformed(ActionEvent e) {
         form_.flatFieldButtonActionPerformed(row_);
         fireEditingStopped();
         selectionModel.clearSelection();
      }

      @Override
      public Object getCellEditorValue() {
         return null;
      }

      @Override
      public Component getTableCellEditorComponent(JTable table,
                                                   Object someObject, boolean isSelected, int row,
                                                   int column) {
         row_ = row;
         panel_.setBackground(table.getSelectionBackground());
         return panel_;
      }
   }

   private class PresetCellRenderer implements TableCellRenderer {
      private final JComboBox<String> comboBox_ = new JComboBox<>();

      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                      Object value, boolean isSelected,
                                                      boolean hasFocus,
                                                      int row, int column) {
         comboBox_.setModel(new DefaultComboBoxModel<>(
               new String[] {value != null ? value.toString() : ""}));
         comboBox_.setSelectedIndex(0);
         if (isSelected) {
            comboBox_.setBackground(table.getSelectionBackground());
         } else {
            comboBox_.setBackground(table.getBackground());
         }
         return comboBox_;
      }
   }

   private class PresetCellEditor extends AbstractCellEditor
         implements TableCellEditor, ActionListener {
      private final JComboBox<String> comboBox_ = new JComboBox<>();
      private final ShadingTableModel model_;
      private int row_;
      private String selectedPreset_;

      @SuppressWarnings("LeakingThisInConstructor")
      public PresetCellEditor(Studio gui, ShadingTableModel model) {
         model_ = model;
         row_ = -1;
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
         String[] presets = {"Default"};
         if (!model_.getChannelGroup().isEmpty()) {
            presets = studio_.getCMMCore().getAvailableConfigs(
                    model_.getChannelGroup()).toArray();
         }
         // remove presets that are already in use
         String[] usedPresets = model_.getUsedPresets(row);
         String[] comboPresets = new String[presets.length - usedPresets.length];
         int index = 0;
         for (String preset : presets) {
            boolean found = false;
            for (String usedPreset : usedPresets) {
               if (preset.equals(usedPreset)) {
                  found = true;
               }
            }
            if (!found) {
               comboPresets[index] = preset;
               index++;
            }
         }
         comboBox_.setModel(new DefaultComboBoxModel<String>(comboPresets));
         String preset = (String) model_.getValueAt(row, column);
         comboBox_.setSelectedItem(preset);
         return comboBox_;
      }

      @Override
      public void actionPerformed(ActionEvent e) {
         selectedPreset_ = (String) comboBox_.getSelectedItem();
         if (selectedPreset_ != null) {
            model_.setValueAt(selectedPreset_, row_, 0);
            selectionModel.clearSelection();
            fireEditingStopped();
         }
      }
   }

   private final PresetCellEditor presetCellEditor_;
   private final LoadFileButtonCellEditor loadFileButtonCellEditor_;

   ShadingTable(Studio gui, ShadingTableModel model, MultiChannelShadingMigForm form) {
      super(model);
      studio_ = gui;

      super.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      super.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);

      // Renderer and Editor for column 0 (preset combobox)
      presetCellEditor_ = new PresetCellEditor(gui, model);
      TableColumn presetColumn = super.getColumnModel().getColumn(0);
      presetColumn.setCellRenderer(new PresetCellRenderer());
      presetColumn.setCellEditor(presetCellEditor_);
      presetColumn.setPreferredWidth(120);
      presetColumn.setMaxWidth(150);

      // Column 1 (file path) gets remaining space by default

      // Renderer and Editor for column 2 (button)
      TableColumn buttonColumn = super.getColumnModel().getColumn(2);
      LoadFileButtonCellRenderer loadFileButtonRenderer =
            new LoadFileButtonCellRenderer(form);
      buttonColumn.setCellRenderer(loadFileButtonRenderer);
      loadFileButtonCellEditor_ =
            new LoadFileButtonCellEditor(form);
      buttonColumn.setCellEditor(loadFileButtonCellEditor_);
      buttonColumn.setPreferredWidth(40);
      buttonColumn.setMaxWidth(40);

      super.setRowHeight((int) (super.getRowHeight() * 1.5));

   }

   public void stopCellEditing() {
      presetCellEditor_.stopCellEditing();
      loadFileButtonCellEditor_.stopCellEditing();
   }

}
