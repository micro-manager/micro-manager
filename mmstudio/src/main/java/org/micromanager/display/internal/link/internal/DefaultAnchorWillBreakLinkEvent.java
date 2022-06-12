/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.internal.link.internal;

import org.micromanager.display.internal.link.AnchorWillBreakLinkEvent;
import org.micromanager.display.internal.link.LinkAnchor;

/**
 * @author mark
 */
public class DefaultAnchorWillBreakLinkEvent<T>
      extends AbstractAnchorEvent<T>
      implements AnchorWillBreakLinkEvent<T> {
   public DefaultAnchorWillBreakLinkEvent(LinkAnchor<T> anchor) {
      super(anchor);
   }
}