/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.internal.link.internal;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.micromanager.display.internal.link.LinkAnchor;
import org.micromanager.internal.utils.EventBusExceptionLogger;

/**
 * @author Mark A. Tsuchida
 * @author based on original link framework by Chris Weisiger
 */
final class DefaultLinkEndpoint implements LinkEndpoint {
   private final LinkAnchor<?> anchor_;
   private LinkEndpointPool pool_;
   private LinkageFactory linkageFactory_;

   // Note: we do _not_ keep the set of linkable peers (imagine what that would
   // do if the user opens 100 similar displays). Instead, we retrieve the full
   // list as needed (e.g. just before displaying the link popup menu).
   // However, we do remember the number of available peers (excluding this).
   private int numLinkablePeers_ = -1;

   private Linkage linkage_;

   // True if the last time we received a value propagation we were not able
   // to match it, _and_ no local change has happened since.
   private boolean outOfSync_;

   // Events provided to user code (e.g. GUI)
   private final EventBus bus_ =
         new EventBus(EventBusExceptionLogger.getInstance());


   static LinkEndpoint create(LinkAnchor anchor) {
      return new DefaultLinkEndpoint(anchor);
   }

   private DefaultLinkEndpoint(LinkAnchor anchor) {
      anchor_ = anchor;
   }

   @Override
   public LinkAnchor<?> getAnchor() {
      return anchor_;
   }

   @Override
   public Object getLinkageGroup() {
      return anchor_.getLinkageGroup();
   }

   @Override
   public Object getCurrentValue() {
      return anchor_.getCurrentValue();
   }

   @Override
   public boolean receivePropagatedValue(Object value) {
      return ((LinkAnchor<Object>) anchor_).receivePropagatedValue(value);
   }

   @Override
   public synchronized void propagateValue(Object value) {
      if (linkage_ == null) {
         return;
      }
      linkage_.propagateValue(this, value);
      if (outOfSync_) {
         outOfSync_ = false;
         bus_.post(new DefaultAnchorDidBecomeInSyncEvent(anchor_));
      }
   }

   @Override
   public synchronized boolean hasLinkablePeers() {
      return numLinkablePeers_ > 0;
   }

   @Override
   public synchronized List<LinkEndpoint> getLinkablePeers() {
      Preconditions.checkState(pool_ != null);
      return new ArrayList<LinkEndpoint>(pool_.getPeers(this));
   }

   @Override
   public synchronized void linkToPeer(LinkEndpoint peer) {
      Preconditions.checkArgument(peer instanceof LinkEndpoint);
      Preconditions.checkArgument(
            peer.getLinkageGroup().equals(getLinkageGroup()));
      Preconditions.checkState(linkageFactory_ != null);
      linkageFactory_.linkToPeer((LinkEndpoint) peer, this);
   }

   @Override
   public synchronized void unlink() {
      if (isLinked()) {
         linkage_.removeEndpoint(this);
         linkage_ = null;
      }
   }

   @Override
   public synchronized boolean isLinked() {
      return linkage_ != null;
   }

   @Override
   public Collection<LinkEndpoint> getLinkedPeers() {
      if (linkage_ == null) {
         return Collections.emptySet();
      }
      return linkage_.getEndpoints();
   }

   @Override
   public synchronized boolean isOutOfSync() {
      return outOfSync_;
   }

   @Override
   public void registerForEvents(Object recipient) {
      bus_.register(recipient);
   }

   @Override
   public void unregisterForEvents(Object recipient) {
      bus_.unregister(recipient);
   }


   //
   // Implementation: Interaction with LinkageEndpointPool
   //

   @Override
   public synchronized void joinPool(LinkEndpointPool pool) {
      Preconditions.checkNotNull(pool);
      Preconditions.checkState(pool_ == null);

      pool_ = pool;
      numLinkablePeers_ = pool.getPeers(this).size() - 1;
   }

   @Override
   public synchronized void leavePool(LinkEndpointPool pool) {
      Preconditions.checkNotNull(pool);
      Preconditions.checkState(pool == pool_);

      numLinkablePeers_ = -1;
      pool_ = null;
   }

   @Subscribe
   public synchronized void onEvent(LinkablePeerDidBecomeAvailableEvent e) {
      Preconditions.checkState(pool_ != null);
      if (e.getPeer() == this) {
         return;
      }
      if (numLinkablePeers_++ < 1) {
         bus_.post(new DefaultAnchorDidBecomeLinkableEvent(anchor_));
      }
   }

   @Subscribe
   public synchronized void onEvent(LinkablePeerWillBecomeUnavailableEvent e) {
      Preconditions.checkState(pool_ != null);
      if (e.getPeer() == this) {
         return;
      }
      if (--numLinkablePeers_ < 1) {
         bus_.post(new DefaultAnchorWillBecomeUnlinkableEvent(anchor_));
      }
   }


   //
   // Implementation: Interaction with LinkEndpointManager
   //

   @Override
   public synchronized void setLinkageFactory(LinkageFactory factory) {
      linkageFactory_ = factory;
   }


   //
   // Implementation: Interaction with Linkage
   //

   @Override
   public synchronized void linkageDidAdmitEndpoint(Linkage linkage) {
      linkage_ = linkage;
      bus_.post(new DefaultAnchorDidMakeLinkEvent(anchor_));
   }

   @Override
   public synchronized void linkageWillExpelEndpoint(Linkage linkage) {
      Preconditions.checkState(linkage_ == linkage);
      bus_.post(new DefaultAnchorWillBreakLinkEvent(anchor_));
      linkage_ = null;
   }

   @Override
   public synchronized void linkageDidTransferEndpoint(Linkage oldLinkage,
                                                       Linkage newLinkage) {
      Preconditions.checkState(linkage_ == oldLinkage);
      linkage_ = newLinkage;
   }

   @Override
   public synchronized boolean linkageRequiresResync() {
      if (outOfSync_) {
         return false;
      }
      propagateValue(getCurrentValue());
      return true;
   }

   @Subscribe
   public synchronized void onPropagated(LinkageValuePropagatedEvent e) {
      if (e.getSource() == this) {
         return;
      }
      boolean inSync = receivePropagatedValue(e.getValue());
      if (outOfSync_ && inSync) {
         outOfSync_ = false;
         bus_.post(new DefaultAnchorDidBecomeInSyncEvent(anchor_));
      } else if (!outOfSync_ && !inSync) {
         outOfSync_ = true;
         bus_.post(new DefaultAnchorDidBecomeOutOfSyncEvent(anchor_));
      }
   }


   //
   // Implementation: Interaction with LinkageFactory
   //

   @Override
   public synchronized Linkage getLinkage() {
      return linkage_;
   }
}
