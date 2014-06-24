package org.micromanager.positionlist;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
      Vector<String> options = new Vector<String>();
      StrVector xyStages = core.getLoadedDevicesOfType(DeviceType.XYStageDevice);
      for (int i = 0; i < xyStages.size(); ++i) {
         options.add(xyStages.get(i));
      }
      StrVector stages = core.getLoadedDevicesOfType(DeviceType.StageDevice);
      for (int i = 0; i < stages.size(); ++i) {
         options.add(stages.get(i));
      }

      for (int i = 0; i < options.size(); ++i) {
         final String deviceName = options.get(i);
         JMenuItem item = new JMenuItem(String.format("Merge with %s current position", deviceName));
         item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
               parent.mergePositionsWithDevice(deviceName);
            }
         });
         add(item);
      }
   }
}
