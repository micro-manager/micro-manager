package org.micromanager.internal.utils;

import org.micromanager.Studio;
import org.micromanager.internal.ConfigGroupPad;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.text.ParseException;

/** @author arthur */
/** Rendering element for the property table. */
public final class StatePresetCellRenderer implements TableCellRenderer {

  private static final long serialVersionUID = 1L;
  // This method is called each time a cell in a column
  // using this renderer needs to be rendered.
  StateItem stateItem_;
  private final Studio studio_;

  public StatePresetCellRenderer(Studio studio) {
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

    ConfigGroupPad.StateTableData data = (ConfigGroupPad.StateTableData) table.getModel();
    stateItem_ = data.getPropertyItem(rowIndex);

    Component comp;

    // Configure the component with the specified value

    if (stateItem_.hasLimits) {
      SliderPanel slider = new SliderPanel();
      if (stateItem_.isInteger()) {
        slider.setLimits((int) stateItem_.lowerLimit, (int) stateItem_.upperLimit);
      } else {
        slider.setLimits(stateItem_.lowerLimit, stateItem_.upperLimit);
      }
      try {
        slider.setText((String) value);
      } catch (ParseException ex) {
        ReportingUtils.logError(ex);
      }
      slider.setToolTipText((String) value);
      comp = slider;

    } else {
      JLabel label = new JLabel();
      label.setOpaque(true);
      label.setFont(new Font("Arial", Font.PLAIN, 10));
      label.setText(stateItem_.config);
      label.setToolTipText(stateItem_.descr);
      label.setHorizontalAlignment(JLabel.LEFT);
      comp = label;
    }

    if (isSelected) {
      comp.setBackground(Color.LIGHT_GRAY);
      comp.setForeground(Color.BLACK);
    } else {
      // HACK: manually set day/night colors.
      comp.setBackground(studio_.app().skin().getBackgroundColor());
      comp.setForeground(studio_.app().skin().getEnabledTextColor());
    }

    return comp;
  }
  // The following methods override the defaults for performance reasons
  public void validate() {}

  public void revalidate() {}

  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}

  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
}
