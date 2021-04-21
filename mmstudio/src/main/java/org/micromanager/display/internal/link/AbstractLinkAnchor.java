/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.link;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.micromanager.display.internal.link.internal.LinkEndpoint;

public abstract class AbstractLinkAnchor<T> implements LinkAnchor<T> {
  private LinkEndpoint endpoint_;

  /** Used by the framework; user code should not call. */
  public void setInternalEndpoint(LinkEndpoint endpoint) {
    endpoint_ = endpoint;
  }

  /**
   * Notify the linked peers that the linked value has changed.
   *
   * @param value
   */
  protected final synchronized void propagateValue(T value) {
    endpoint_.propagateValue(value);
  }

  @Override
  public boolean hasLinkablePeers() {
    return endpoint_.hasLinkablePeers();
  }

  @Override
  public List<LinkAnchor<T>> getLinkablePeers() {
    List<LinkEndpoint> peers = endpoint_.getLinkablePeers();
    List<LinkAnchor<T>> ret = new ArrayList<LinkAnchor<T>>();
    for (LinkEndpoint endpoint : peers) {
      ret.add((LinkAnchor<T>) endpoint.getAnchor());
    }
    return ret;
  }

  @Override
  public void linkToPeer(LinkAnchor<T> peer) {
    Preconditions.checkArgument(peer instanceof AbstractLinkAnchor);
    endpoint_.linkToPeer(((AbstractLinkAnchor) peer).endpoint_);
  }

  @Override
  public void unlink() {
    endpoint_.unlink();
  }

  @Override
  public boolean isLinked() {
    return endpoint_.isLinked();
  }

  @Override
  public Collection<LinkAnchor<T>> getLinkedPeers() {
    Set<LinkAnchor<T>> ret = new HashSet<LinkAnchor<T>>();
    for (LinkEndpoint peer : endpoint_.getLinkedPeers()) {
      if (peer != endpoint_) {
        ret.add((LinkAnchor<T>) peer.getAnchor());
      }
    }
    return ret;
  }

  @Override
  public boolean isOutOfSync() {
    return endpoint_.isOutOfSync();
  }
}
