/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.internal.link;

import org.micromanager.display.internal.link.internal.LinkAnchorEvent;


/**
 * Event signalling that an Anchor (viewer element linking axes between different viewers)
 * will break a link.
 *
 * @author mark
 */
public interface AnchorWillBreakLinkEvent<T> extends LinkAnchorEvent<T> {
}