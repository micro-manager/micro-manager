/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.internal.link.internal;

import java.util.Collection;
import java.util.List;
import org.micromanager.EventPublisher;
import org.micromanager.display.internal.link.LinkAnchor;

/**
 * @author mark
 */
public interface LinkEndpoint extends EventPublisher {
   <T> LinkAnchor<T> getAnchor();

   // Forwarded to anchor
   Object getLinkageGroup();

   Object getCurrentValue();

   boolean receivePropagatedValue(Object value);

   // For anchor to call
   void propagateValue(Object value);

   // For anchor users to call via anchor
   boolean hasLinkablePeers();

   List<LinkEndpoint> getLinkablePeers();

   void linkToPeer(LinkEndpoint peer);

   void unlink();

   boolean isLinked();

   Collection<LinkEndpoint> getLinkedPeers();

   boolean isOutOfSync();

   // Interface for LinkEndpointPool
   void joinPool(LinkEndpointPool pool);

   void leavePool(LinkEndpointPool pool);

   // Interface for LinkEndpointManager
   void setLinkageFactory(LinkageFactory factory);

   // Interface for Linkage
   void linkageDidAdmitEndpoint(Linkage linkage);

   void linkageWillExpelEndpoint(Linkage linkage);

   void linkageDidTransferEndpoint(Linkage oldLinkage, Linkage newLinkage);

   boolean linkageRequiresResync();

   // Interface for LinkageFactory
   Linkage getLinkage();
}