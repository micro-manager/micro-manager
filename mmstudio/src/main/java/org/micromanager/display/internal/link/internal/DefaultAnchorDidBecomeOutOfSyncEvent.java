/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.link.internal;

import org.micromanager.display.internal.link.AnchorDidBecomeOutOfSyncEvent;
import org.micromanager.display.internal.link.LinkAnchor;

/** @author mark */
public class DefaultAnchorDidBecomeOutOfSyncEvent<T> extends AbstractAnchorEvent<T>
    implements AnchorDidBecomeOutOfSyncEvent<T> {
  public DefaultAnchorDidBecomeOutOfSyncEvent(LinkAnchor<T> anchor) {
    super(anchor);
  }
}
