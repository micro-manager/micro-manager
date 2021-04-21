/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.inspector;

import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.micromanager.display.DataViewer;

/** @author Mark A. Tsuchida */
public interface InspectorPanelController {
  void addInspectorPanelListener(InspectorPanelListener listener);

  void removeInspectorPanelListener(InspectorPanelListener listener);

  String getTitle();

  JPanel getPanel();

  JPopupMenu getGearMenu();

  void attachDataViewer(DataViewer viewer);

  void detachDataViewer();

  boolean isVerticallyResizableByUser();

  boolean initiallyExpand();

  public void setExpanded(boolean status);
}
