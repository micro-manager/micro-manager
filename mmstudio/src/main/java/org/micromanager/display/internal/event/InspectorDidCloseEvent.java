/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.event;

import org.micromanager.display.inspector.internal.InspectorController;

/** @author mark */
public class InspectorDidCloseEvent {
  private final InspectorController inspector_;

  public static InspectorDidCloseEvent create(InspectorController inspector) {
    return new InspectorDidCloseEvent(inspector);
  }

  private InspectorDidCloseEvent(InspectorController inspector) {
    inspector_ = inspector;
  }

  public InspectorController getInspector() {
    return inspector_;
  }
}
