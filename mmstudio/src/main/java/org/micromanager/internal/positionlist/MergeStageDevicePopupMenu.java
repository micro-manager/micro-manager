package org.micromanager.internal.positionlist;

import java.util.Vector;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;

/**
 * This class allows the user to select a StageDevice or XYStageDevice from
 * a popup menu.
 */
class MergeStageDevicePopupMenu extends JPopupMenu {
   public MergeStageDevicePopupMenu(final PositionListDlg parent, CMMCore core) {
      Vector<String> options = new Vector<>();
      StrVector xyStages = core.getLoadedDevicesOfType(DeviceType.XYStageDevice);
      for (int i = 0; i < xyStages.size(); ++i) {
         options.add(xyStages.get(i));
      }
      StrVector stages = core.getLoadedDevicesOfType(DeviceType.StageDevice);
      for (int i = 0; i < stages.size(); ++i) {
         options.add(stages.get(i));
      }

      for (final String deviceName : options) {
         JMenuItem item =
               new JMenuItem(String.format("Merge with %s current position", deviceName));
         if (!parent.useDrive(deviceName)) {
            item.setEnabled(false);
            item.setText(item.getText() + " (inactive)");
         }
         item.addActionListener(event -> parent.mergePositionsWithDevice(deviceName));
         add(item);
      }
   }
}
