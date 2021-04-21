package org.micromanager.internal.utils;

import org.micromanager.Studio;
import org.micromanager.internal.ConfigGroupPad;

import javax.swing.*;
import java.awt.*;

/** @author arthur */
/** Rendering element for the property table. */
public final class StateGroupCellRenderer extends PropertyValueCellRenderer {

  private static final long serialVersionUID = 1L;
  // This method is called each time a cell in a column
  // using this renderer needs to be rendered.
  StateItem stateItem_;
  private final Studio studio_;

  public StateGroupCellRenderer(Studio studio) {
    super(studio);
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

    ConfigGroupPad.StateTableData data = (ConfigGroupPad.StateTableData) table.getModel();

    stateItem_ = data.getPropertyItem(rowIndex);

    Component comp;

    JLabel label = new JLabel();
    label.setOpaque(true);
    label.setFont(new Font("Arial", Font.BOLD, 11));
    label.setText((String) value);
    label.setToolTipText(stateItem_.descr);
    label.setHorizontalAlignment(JLabel.LEFT);
    comp = label;

    if (isSelected) {
      comp.setBackground(Color.LIGHT_GRAY);
      comp.setForeground(Color.BLACK);
    } else {
      // HACK: manually set the colors.
      comp.setBackground(studio_.app().skin().getBackgroundColor());
      comp.setForeground(studio_.app().skin().getEnabledTextColor());
    }

    return comp;
  }
  // The following methods override the defaults for performance reasons
  @Override
  public void validate() {}

  @Override
  public void revalidate() {}

  @Override
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}

  @Override
  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
}
