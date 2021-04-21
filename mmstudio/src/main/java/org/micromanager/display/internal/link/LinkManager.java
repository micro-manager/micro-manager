/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.link;

/** @author mark */
public interface LinkManager {
  void registerAnchor(LinkAnchor<?> anchor);

  void unregisterAnchor(LinkAnchor<?> anchor);
}
