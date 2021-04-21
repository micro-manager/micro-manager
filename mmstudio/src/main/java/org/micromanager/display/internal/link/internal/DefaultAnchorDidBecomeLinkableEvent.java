/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.link.internal;

import org.micromanager.display.internal.link.AnchorDidBecomeLinkableEvent;
import org.micromanager.display.internal.link.LinkAnchor;

/** @author mark */
public class DefaultAnchorDidBecomeLinkableEvent<T> extends AbstractAnchorEvent<T>
    implements AnchorDidBecomeLinkableEvent<T> {
  public DefaultAnchorDidBecomeLinkableEvent(LinkAnchor<T> anchor) {
    super(anchor);
  }
}
