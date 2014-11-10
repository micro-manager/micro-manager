package org.micromanager.pipelineinterface;

import java.awt.Component;
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
import mmcorej.TaggedImage;
import net.miginfocom.swing.MigLayout;
import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.ScriptInterface;

public class PipelineTable extends JTable {

   private final ScriptInterface gui_;

   private static final String buttonCellLayoutConstraints =
         "fill, insets 0, align center center";

   private class ConfigureButtonCellRenderer implements TableCellRenderer {
      private final JPanel panel_ = new JPanel();
      private final JButton button_ = new JButton("Configure...");

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
         }
         else {
            panel_.setBackground(table.getBackground());
         }
         return panel_;
      }
   }

   private class ConfigureButtonCellEditor extends AbstractCellEditor
         implements TableCellEditor, ActionListener {

      private final JPanel panel_ = new JPanel();
      private final JButton button_ = new JButton("Configure...");
      private DataProcessor<TaggedImage> processor_;

      public ConfigureButtonCellEditor() {
         panel_.setLayout(new MigLayout(buttonCellLayoutConstraints));
         panel_.add(button_);
         button_.addActionListener(this);
      }

      @Override
      public void actionPerformed(ActionEvent e) {
         processor_.makeConfigurationGUI();
         // Since the config GUI is modeless, we're immediately "done" editing.
         fireEditingStopped();
      }

      @Override
      public Object getCellEditorValue() {
         return processor_;
      }

      @Override
      public Component getTableCellEditorComponent(JTable table,
            Object cellValue, boolean isSelected, int row, int column) {

         @SuppressWarnings("unchecked")
         DataProcessor<TaggedImage> dataProcessor = (DataProcessor) cellValue;
         processor_ = dataProcessor;

         if (isSelected) {
            panel_.setBackground(table.getSelectionBackground());
         }
         else {
            panel_.setBackground(table.getBackground());
         }
         return panel_;
      }
   }

   PipelineTable(ScriptInterface gui, AcquisitionEngine engine) {
      super(new PipelineTableModel(engine));
      gui_ = gui;

      setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

      setDefaultRenderer(DataProcessor.class,
            new ConfigureButtonCellRenderer());
      setDefaultEditor(DataProcessor.class, new ConfigureButtonCellEditor());

      TableColumn enabledColumn = getColumnModel().
            getColumn(PipelineTableModel.ENABLED_COLUMN);
      enabledColumn.setMinWidth(enabledColumn.getPreferredWidth());
      enabledColumn.setMaxWidth(enabledColumn.getPreferredWidth());
   }

   DataProcessor<TaggedImage> getSelectedProcessor() {
      int i = getSelectedRow();
      if (i >= 0) {
         Object cellValue = getModel().getValueAt(i,
               PipelineTableModel.CONFIGURE_COLUMN);
         @SuppressWarnings("unchecked")
         DataProcessor<TaggedImage> processor = (DataProcessor) cellValue;
         return processor;
      }
      return null;
   }
}
