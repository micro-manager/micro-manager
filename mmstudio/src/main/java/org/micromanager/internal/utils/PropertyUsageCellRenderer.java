package org.micromanager.internal.utils;

import java.awt.Component;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.micromanager.Studio;

/** @author arthur */
public final class PropertyUsageCellRenderer implements TableCellRenderer {

  PropertyItem item_;
  JCheckBox cb_ = new JCheckBox();
  private final Studio studio_;

  public PropertyUsageCellRenderer(Studio studio) {
    super();
    studio_ = studio;
  }

  @Override
  public Component getTableCellRendererComponent(
      JTable table, Object value, boolean isSelected, boolean hasFocus, int rowIndex, int column) {
    PropertyTableData data = (PropertyTableData) table.getModel();
    item_ = data.getPropertyItem(rowIndex);

    cb_.setSelected(item_.confInclude);
    cb_.setBackground(studio_.app().skin().getBackgroundColor());
    if (item_.readOnly) {
      cb_.setEnabled(false);
    } else {
      cb_.setEnabled(true);
    }
    return (Component) cb_;
  }

  // The following methods override the defaults for performance reasons
  public void validate() {}

  public void revalidate() {}

  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}

  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
}
