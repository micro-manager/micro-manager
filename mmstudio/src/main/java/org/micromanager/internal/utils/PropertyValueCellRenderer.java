package org.micromanager.internal.utils;

import java.awt.Component;
import java.text.ParseException;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.micromanager.Studio;

public class PropertyValueCellRenderer implements TableCellRenderer {
  // This method is called each time a cell in a column
  // using this renderer needs to be rendered.

  PropertyItem item_;
  JLabel lab_ = new JLabel();
  private final Studio studio_;

  public PropertyValueCellRenderer(Studio studio) {
    super();
    studio_ = studio;
  }

  @Override
  public Component getTableCellRendererComponent(
      JTable table,
      Object value,
      boolean isSelected,
      boolean hasFocus,
      int rowIndex,
      int colIndex) {

    MMPropertyTableModel data = (MMPropertyTableModel) table.getModel();
    item_ = data.getPropertyItem(rowIndex);

    lab_.setOpaque(true);
    lab_.setHorizontalAlignment(JLabel.LEFT);

    Component comp;

    if (item_.hasRange) {
      SliderPanel slider = new SliderPanel();
      if (item_.isInteger()) {
        slider.setLimits((int) item_.lowerLimit, (int) item_.upperLimit);
      } else {
        slider.setLimits(item_.lowerLimit, item_.upperLimit);
      }
      try {
        if (value != null) {
          slider.setText((String) value);
        }
      } catch (ParseException ex) {
        ReportingUtils.logError(ex);
      }
      slider.setToolTipText(item_.value);
      comp = slider;
    } else {
      lab_.setText(item_.value);
      comp = lab_;
    }

    if (item_.readOnly) {
      comp.setBackground(studio_.app().skin().getDisabledBackgroundColor());
      comp.setForeground(studio_.app().skin().getDisabledTextColor());
    } else {
      comp.setBackground(studio_.app().skin().getBackgroundColor());
      comp.setForeground(studio_.app().skin().getEnabledTextColor());
    }

    if (!table.isCellEditable(rowIndex, colIndex)) {
      comp.setEnabled(false);
      // For legibility's sake, we always use the "enabled" color.
      comp.setForeground(studio_.app().skin().getEnabledTextColor());
    } else {
      comp.setEnabled(true);
    }

    return comp;
  }

  // The following methods override the defaults for performance reasons
  public void validate() {}

  public void revalidate() {}

  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}

  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
}
