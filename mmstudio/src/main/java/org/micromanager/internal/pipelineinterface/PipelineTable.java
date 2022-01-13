///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//AUTHOR:        Mark Tsuchida, Chris Weisiger
//COPYRIGHT:     University of California, San Francisco, 2006-2015
//               100X Imaging Inc, www.100ximaging.com, 2008
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.pipelineinterface;

import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.AbstractCellEditor;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import net.miginfocom.swing.MigLayout;

public final class PipelineTable extends JTable {

   private static final String buttonCellLayoutConstraints =
         "insets 0, align center center";

   private static JButton makeConfigureButton() {
      JButton result = new JButton("Configure...");
      result.setMargin(new Insets(0, 0, 0, 0));
      result.setFont(new Font("Arial", Font.PLAIN, 10));
      return result;
   }

   private class ConfigureButtonCellRenderer implements TableCellRenderer {
      private final JPanel panel_ = new JPanel();
      private final JButton button_ = makeConfigureButton();

      public ConfigureButtonCellRenderer() {
         panel_.setLayout(new MigLayout(buttonCellLayoutConstraints));
         panel_.add(button_);
      }

      @Override
      public Component getTableCellRendererComponent(JTable table,
            Object dataProcessor, boolean isSelected, boolean hasFocus,
            int row, int column) {
         if (isSelected) {
            panel_.setBackground(table.getSelectionBackground());
         } else {
            panel_.setBackground(table.getBackground());
         }
         return panel_;
      }
   }

   private class ConfigureButtonCellEditor extends AbstractCellEditor
         implements TableCellEditor, ActionListener {

      private final JPanel panel_ = new JPanel();
      private final JButton button_ = makeConfigureButton();
      private ConfiguratorWrapper configurator_;

      public ConfigureButtonCellEditor() {
         panel_.setLayout(new MigLayout(buttonCellLayoutConstraints));
         panel_.add(button_);
         button_.addActionListener(this);
      }

      @Override
      public void actionPerformed(ActionEvent e) {
         configurator_.getConfigurator().showGUI();
         // Since the config GUI is modeless, we're immediately "done" editing.
         fireEditingStopped();
      }

      @Override
      public Object getCellEditorValue() {
         return configurator_;
      }

      @Override
      public Component getTableCellEditorComponent(JTable table,
            Object cellValue, boolean isSelected, int row, int column) {

         ConfiguratorWrapper configurator = (ConfiguratorWrapper) cellValue;
         configurator_ = configurator;

         if (isSelected) {
            panel_.setBackground(table.getSelectionBackground());
         } else {
            panel_.setBackground(table.getBackground());
         }
         return panel_;
      }
   }

   PipelineTable() {
      super(new PipelineTableModel());

      setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      setDefaultRenderer(ConfiguratorWrapper.class,
            new ConfigureButtonCellRenderer());
      setDefaultEditor(ConfiguratorWrapper.class,
            new ConfigureButtonCellEditor());

      // Shrink the checkbox columns down to size.
      for (int columnID : new int[] {PipelineTableModel.ENABLED_COLUMN,
         PipelineTableModel.ENABLED_LIVE_COLUMN}) {
         TableColumn column = getColumnModel().getColumn(columnID);
         column.setMinWidth(column.getPreferredWidth());
         column.setMaxWidth(column.getPreferredWidth());
      }
   }

   ConfiguratorWrapper getSelectedConfigurator() {
      int i = getSelectedRow();
      if (i >= 0) {
         Object cellValue = getModel().getValueAt(i,
               PipelineTableModel.CONFIGURE_COLUMN);
         return (ConfiguratorWrapper) cellValue;
      }
      return null;
   }
}
