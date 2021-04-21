/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.link.internal;

/** @author mark */
class LinkablePeerDidBecomeAvailableEvent {
  private final LinkEndpoint endpoint_;

  static LinkablePeerDidBecomeAvailableEvent create(LinkEndpoint endpoint) {
    return new LinkablePeerDidBecomeAvailableEvent(endpoint);
  }

  private LinkablePeerDidBecomeAvailableEvent(LinkEndpoint endpoint) {
    endpoint_ = endpoint;
  }

  LinkEndpoint getPeer() {
    return endpoint_;
  }
}
