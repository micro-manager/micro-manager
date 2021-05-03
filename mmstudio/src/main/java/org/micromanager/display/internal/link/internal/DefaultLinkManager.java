/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.link.internal;

import org.micromanager.display.internal.link.AbstractLinkAnchor;
import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;
import org.micromanager.display.internal.link.LinkAnchor;
import org.micromanager.display.internal.link.LinkManager;


public class DefaultLinkManager implements LinkManager {
   private final LinkEndpointManager endpointManager_ =
         LinkEndpointManager.create();
   private final LinkageFactory linkageFactory_ =
         LinkageFactory.create();
   private final Map<LinkAnchor<?>, LinkEndpoint> endpoints_ = new HashMap<>();

   public static LinkManager create() {
      return new DefaultLinkManager();
   }

   private DefaultLinkManager() {
   }

   @Override
   public void registerAnchor(LinkAnchor<?> anchor) {
      Preconditions.checkArgument(anchor instanceof AbstractLinkAnchor,
            "anchor must be a subclass of AbstractLinkAnchor");
      Preconditions.checkArgument(!endpoints_.containsKey(anchor));

      LinkEndpoint endpoint = DefaultLinkEndpoint.create(anchor);
      ((AbstractLinkAnchor) anchor).setInternalEndpoint(endpoint);
      endpoints_.put(anchor, endpoint);
      endpoint.setLinkageFactory(linkageFactory_);
      endpointManager_.registerEndpoint(endpoint);
   }

   @Override
   public void unregisterAnchor(LinkAnchor<?> anchor) {
      Preconditions.checkArgument(anchor instanceof AbstractLinkAnchor,
            "anchor must be a subclass of AbstractLinkAnchor");
      Preconditions.checkArgument(endpoints_.containsKey(anchor));
      endpointManager_.unregisterEndpoint(endpoints_.get(anchor));
      endpoints_.remove(anchor);
   }

   @Override
   public void unregisterAllAnchors() {
      for (LinkAnchor<?> anchor :  endpoints_.keySet()) {
         endpointManager_.unregisterEndpoint(endpoints_.get(anchor));
      }
      endpoints_.clear();
   }
}