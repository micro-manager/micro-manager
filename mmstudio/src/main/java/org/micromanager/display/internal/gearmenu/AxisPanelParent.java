package org.micromanager.display.internal.gearmenu;

import java.util.ArrayList;

/**
 * Callback interface that an export dialog must implement so
 * {@link ExportMovieDlg.AxisPanel} can call back into it for axis management
 * and layout.
 */
public interface AxisPanelParent {
   ArrayList<String> getNonZeroAxes();

   ExportMovieDlg.AxisPanel createAxisPanel();

   void changeAxis(String oldAxis, String newAxis);

   void deleteFollowing(ExportMovieDlg.AxisPanel last);

   int getNumSpareAxes();

   void pack();
}
