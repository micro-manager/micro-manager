///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
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

import java.util.ArrayList;
import java.util.HashSet;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;

/**
 * Simple extension of JPopupMenu whose menu items remained alphabetically
 * ordered. Mostly a copy of SortedMenu except that it extends a different
 * base class, which requires some subtle changes in how it works internally.
 */
public final class SortedPopupMenu extends JPopupMenu {
   private HashSet<JMenuItem> unsortedItems_ = new HashSet<JMenuItem>();
   private ArrayList<Integer> separatorIndices_ = new ArrayList<Integer>();

   /**
    * Insert an item whose position will be ignored when sorting items.
    */
   public JMenuItem addUnsorted(JMenuItem item) {
      unsortedItems_.add(item);
      return super.add(item);
   }

   @Override
   public void addSeparator() {
      // HACK: JPopupMenu does not list separators in its getSubElements()
      // array but does take them into account when calling insert(), which we
      // use in add(), below, so we need to apply offsets at times.
      super.addSeparator();
      separatorIndices_.add(getSubElements().length);
   }

   @Override
   public JMenuItem add(JMenuItem item) {
      MenuElement[] elements = getSubElements();
      // Find the insertion point.
      for (int i = 0; i < elements.length; ++i) {
         JMenuItem curItem = (JMenuItem) elements[i];
         if (unsortedItems_.contains(curItem)) {
            // Skip this item because it's outside the sorted logic.
            continue;
         }
         if (curItem == null) {
            // Separator.
            continue;
         }
         if (item.getText().compareTo(curItem.getText()) < 0) {
            // HACK: apply offset based on number of separators that precede
            // us (see addSeparator() method).
            int offset = 0;
            for (Integer index : separatorIndices_) {
               if (index <= i) {
                  offset++;
               }
            }
            insert(item, i + offset);
            return item;
         }
      }
      // Add it at the end instead.
      return super.add(item);
   }
}
