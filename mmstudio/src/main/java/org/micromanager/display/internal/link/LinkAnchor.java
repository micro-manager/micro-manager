/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.link;

import java.util.Collection;
import java.util.List;

/**
 * An object that serves as a port for bidirectional linking of arbitrary attributes to other
 * anchors.
 *
 * <p>To create a concrete anchor, you must extend {@link AbstractLinkAnchor}.
 *
 * @author Mark A. Tsuchida
 * @param <T> the type of the value to be linked
 */
public interface LinkAnchor<T> {
  /**
   * Return a tag object designating the linkage group of this anchor.
   *
   * <p>The linkage group determines the set of mutually compatible (linkable) anchors.
   *
   * <p>The tag object must be immutable and its {@code equals} and {@code hashCode} methods (if
   * overridden) must behave correctly.
   *
   * @return the linkage group tag object for this anchor
   */
  Object getLinkageGroup();

  /**
   * Return the current local value of the linked attribute.
   *
   * @return
   */
  T getCurrentValue();

  /**
   * Called when a linked peer has notified that the linked value has changed.
   *
   * <p>To prevent thread deadlock, this method must not (directly or indirectly) call {@link
   * #linkToPeer} or {@link #unlink} on this or any other anchor.
   *
   * @param value
   * @return true if the new value was applicable; false if it could not be matched (e.g. because it
   *     is our of range)
   */
  boolean receivePropagatedValue(T value);

  /**
   * Return whether any linkable peer anchors exist.
   *
   * @return true if at least one linkable peer is available
   * @see AnchorDidBecomeLinkableEvent
   * @see AnchorWillBecomeUnlinkableEvent
   */
  boolean hasLinkablePeers();

  /**
   * Get all linkable peer anchors.
   *
   * @return
   */
  List<LinkAnchor<T>> getLinkablePeers();

  /**
   * Form a bidirectional link with the given anchor, and any other anchors already linked to the
   * given one.
   *
   * @param peer the anchor to link to
   */
  void linkToPeer(LinkAnchor<T> peer);

  /** Unlink this anchor from any linked peers. */
  void unlink();

  /**
   * Return whether this anchor is linked to others.
   *
   * @return
   * @see AnchorDidMakeLinkEvent
   * @see AnchorWillBreakLinkEvent
   */
  boolean isLinked();

  /**
   * Return the currently linked peers.
   *
   * @return
   */
  Collection<LinkAnchor<T>> getLinkedPeers();

  /**
   * Returns whether this anchor is out of sync from its linked peers due to local conditions.
   *
   * @return true if this anchor is out of sync
   * @see AnchorDidBecomeInSyncEvent
   * @see AnchorDidBecomeOutOfSyncEvent
   */
  boolean isOutOfSync();
}
