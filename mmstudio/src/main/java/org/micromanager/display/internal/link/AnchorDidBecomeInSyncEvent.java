/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.internal.link;

import org.micromanager.display.internal.link.internal.LinkAnchorEvent;


/**
 * Event that signal that an Anchor (used in the display to synchronize axes) become
 * synchronized.
 *
 * @author mark
 */
public interface AnchorDidBecomeInSyncEvent<T> extends LinkAnchorEvent<T> {
}