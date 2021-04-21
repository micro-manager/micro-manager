package org.micromanager.internal.utils;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** @author arthur */
public final class PropertyUsageCellEditor extends AbstractCellEditor implements TableCellEditor {
  JCheckBox check_ = new JCheckBox();
  PropertyItem item_;

  public PropertyUsageCellEditor() {
    super();
    check_.setSelected(false);
    check_.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            fireEditingStopped();
          }
        });
  }

  // This method is called when a cell value is edited by the user.
  @Override
  public Component getTableCellEditorComponent(
      JTable table, Object value, boolean isSelected, int rowIndex, int colIndex) {

    PropertyTableData data = (PropertyTableData) table.getModel();
    item_ = data.getPropertyItem(rowIndex);
    check_.setSelected(item_.confInclude);
    // Make sure cell to the right is(not) grayed out when checkbox changes.
    return check_;
  }

  @Override
  public Object getCellEditorValue() {
    return check_.isSelected();
  }
}
