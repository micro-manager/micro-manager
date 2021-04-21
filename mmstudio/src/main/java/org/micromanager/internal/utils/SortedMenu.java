///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    Regents of the University of California 2015
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
//
package org.micromanager.internal.utils;

import java.util.HashSet;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/** Simple extension of JMenu whose menu items remained alphabetically ordered. */
public final class SortedMenu extends JMenu {
  private HashSet<JMenuItem> unsortedItems_;

  public SortedMenu(String title) {
    super(title);
    unsortedItems_ = new HashSet<JMenuItem>();
  }

  // Allow users to bypass the sorted nature
  public JMenuItem addUnsorted(JMenuItem item) {
    unsortedItems_.add(item);
    return super.add(item);
  }

  @Override
  public JMenuItem add(JMenuItem item) {
    // Find the insertion point.
    for (int i = 0; i < getItemCount(); ++i) {
      JMenuItem curItem = getItem(i);
      if (unsortedItems_.contains(curItem)) {
        // Skip this item because it's outside the sorted logic.
        continue;
      }
      if (curItem == null) {
        // Separator.
        continue;
      }
      if (item.getText().compareTo(curItem.getText()) < 0) {
        insert(item, i);
        return item;
      }
    }
    // Add it at the end instead.
    return super.add(item);
  }
}
