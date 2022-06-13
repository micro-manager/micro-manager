package org.micromanager.display.internal.link.internal;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;

/**
 * A collection of LinkEndpointPools for all linkage gropus.
 *
 * @author Mark A. Tsuchida
 * @author based on original link framework by Chris Weisiger
 */
final class LinkEndpointManager {
   private final Map<Object, LinkEndpointPool> pools_ =
         new HashMap<Object, LinkEndpointPool>();

   static LinkEndpointManager create() {
      return new LinkEndpointManager();
   }

   private LinkEndpointManager() {
   }

   synchronized void registerEndpoint(LinkEndpoint endpoint) {
      Preconditions.checkNotNull(endpoint);
      Object linkageGroup = endpoint.getLinkageGroup();
      if (!pools_.containsKey(linkageGroup)) {
         pools_.put(linkageGroup, LinkEndpointPool.create());
      }
      pools_.get(linkageGroup).registerEndpoint(endpoint);
   }

   synchronized void unregisterEndpoint(LinkEndpoint endpoint) {
      Preconditions.checkNotNull(endpoint);
      Object linkageGroup = endpoint.getLinkageGroup();
      pools_.get(linkageGroup).unregisterEndpoint(endpoint);
      if (pools_.get(linkageGroup).isEmpty()) {
         pools_.remove(linkageGroup);
      }
   }
}