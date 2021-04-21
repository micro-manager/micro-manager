/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.inspector.internal;

import java.awt.Component;
import javax.swing.JList;
import javax.swing.JSeparator;
import javax.swing.ListCellRenderer;

/**
 * A {@code ListCellRenderer} allowing separators in a {@code JComboBox}.
 *
 * <p>Simply set the JComboBox's renderer to an instance of this class, then add a {@code
 * JSeparator} as an item in the JComboBox.
 *
 * <p>Note, however, that the separator is selectable (there is no easy way to "disable" items in a
 * JComboBox). In your action handler for the combo box, you will need to ignore such selections and
 * revert to the previous value.
 *
 * <p>TODO Move this to generic GUI utilities
 *
 * @author Mark A. Tsuchida
 */
class ComboBoxSeparatorRenderer implements ListCellRenderer {
  private final ListCellRenderer parent_;

  public static ComboBoxSeparatorRenderer create(ListCellRenderer parent) {
    return new ComboBoxSeparatorRenderer(parent);
  }

  private ComboBoxSeparatorRenderer(ListCellRenderer parent) {
    parent_ = parent;
  }

  @Override
  public Component getListCellRendererComponent(
      JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    if (value instanceof JSeparator) {
      return (JSeparator) value;
    }
    return parent_.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
  }
}
