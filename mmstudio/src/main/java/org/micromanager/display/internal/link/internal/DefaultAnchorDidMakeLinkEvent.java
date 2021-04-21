/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.link.internal;

import org.micromanager.display.internal.link.AnchorDidMakeLinkEvent;
import org.micromanager.display.internal.link.LinkAnchor;

/** @author mark */
public class DefaultAnchorDidMakeLinkEvent<T> extends AbstractAnchorEvent<T>
    implements AnchorDidMakeLinkEvent<T> {
  public DefaultAnchorDidMakeLinkEvent(LinkAnchor<T> anchor) {
    super(anchor);
  }
}
