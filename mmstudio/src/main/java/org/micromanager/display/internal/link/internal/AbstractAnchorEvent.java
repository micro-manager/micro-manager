/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.link.internal;

import org.micromanager.display.internal.link.LinkAnchor;

/** @author mark */
public class AbstractAnchorEvent<T> implements LinkAnchorEvent<T> {
  private final LinkAnchor<T> anchor_;

  protected AbstractAnchorEvent(LinkAnchor<T> anchor) {
    anchor_ = anchor;
  }

  @Override
  public LinkAnchor<T> getAnchor() {
    return anchor_;
  }
}
