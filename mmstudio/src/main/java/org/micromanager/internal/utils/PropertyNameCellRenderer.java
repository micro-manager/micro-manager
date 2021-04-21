///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein, 2009
//
// COPYRIGHT:    University of California, San Francisco, 2009
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
//

package org.micromanager.internal.utils;

import org.micromanager.Studio;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/** @author arthur */
public final class PropertyNameCellRenderer implements TableCellRenderer {

  PropertyItem item_;
  private final Studio studio_;

  public PropertyNameCellRenderer(Studio studio) {
    super();
    studio_ = studio;
  }

  @Override
  public Component getTableCellRendererComponent(
      JTable table, Object value, boolean isSelected, boolean hasFocus, int rowIndex, int column) {
    MMPropertyTableModel data = (MMPropertyTableModel) table.getModel();
    item_ = data.getPropertyItem(rowIndex);
    JLabel lab = new JLabel();
    lab.setOpaque(true);
    lab.setHorizontalAlignment(JLabel.LEFT);
    lab.setText((String) value);

    if (item_.readOnly) {
      lab.setBackground(studio_.app().skin().getDisabledBackgroundColor());
      lab.setForeground(studio_.app().skin().getDisabledTextColor());
    } else {
      lab.setBackground(studio_.app().skin().getBackgroundColor());
      lab.setForeground(studio_.app().skin().getEnabledTextColor());
    }
    return lab;
  }

  // The following methods override the defaults for performance reasons
  public void validate() {}

  public void revalidate() {}

  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}

  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
}
