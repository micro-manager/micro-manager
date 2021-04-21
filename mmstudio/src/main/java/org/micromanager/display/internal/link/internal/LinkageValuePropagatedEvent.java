/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.link.internal;

/** @author mark */
class LinkageValuePropagatedEvent {
  private final LinkEndpoint source_;
  private final Object value_;

  static LinkageValuePropagatedEvent create(LinkEndpoint source, Object value) {
    return new LinkageValuePropagatedEvent(source, value);
  }

  private LinkageValuePropagatedEvent(LinkEndpoint source, Object value) {
    source_ = source;
    value_ = value;
  }

  LinkEndpoint getSource() {
    return source_;
  }

  Object getValue() {
    return value_;
  }
}
