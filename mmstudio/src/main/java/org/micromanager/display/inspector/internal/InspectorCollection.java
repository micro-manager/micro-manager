/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.inspector.internal;

import com.google.common.eventbus.Subscribe;
import org.micromanager.display.internal.event.InspectorDidCloseEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** @author mark */
public class InspectorCollection {
  private final Set<InspectorController> inspectors_ = new HashSet<InspectorController>();

  public static InspectorCollection create() {
    return new InspectorCollection();
  }

  private InspectorCollection() {}

  public void addInspector(InspectorController inspector) {
    inspectors_.add(inspector);
    inspector.registerForEvents(this);
  }

  private void removeInspector(InspectorController inspector) {
    inspector.unregisterForEvents(this);
    inspectors_.remove(inspector);
  }

  public boolean hasInspector(InspectorController inspector) {
    return inspectors_.contains(inspector);
  }

  public List<InspectorController> getAllInspectors() {
    return new ArrayList<InspectorController>(inspectors_);
  }

  public boolean hasInspectorForFrontmostDataViewer() {
    for (InspectorController inspector : inspectors_) {
      if (inspector.isAttachedToFrontmostDataViewer()) {
        return true;
      }
    }
    return false;
  }

  @Subscribe
  public void onEvent(InspectorDidCloseEvent e) {
    removeInspector(e.getInspector());
  }
}
