/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.internal.link.internal;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.micromanager.internal.utils.EventBusExceptionLogger;

/**
 * @author Mark A. Tsuchida
 * @author based on original link framework by Chris Weisiger
 */
public final class Linkage {
   private final ExecutorService executor_;

   // Accessed only from the executor's thread and always kept in sync with
   // subscription to asyncBus_
   private final Set<LinkEndpoint> endpoints_ = new HashSet<LinkEndpoint>();
   private final EventBus asyncBus_;

   static Linkage create(ExecutorService executor) {
      return new Linkage(executor);
   }

   // executor must be a global single-threaded executor
   private Linkage(ExecutorService executor) {
      executor_ = executor;
      asyncBus_ = new AsyncEventBus(executor,
            EventBusExceptionLogger.getInstance());
   }

   void addEndpoint(final LinkEndpoint endpoint) {
      executor_.submit(new Runnable() {
         @Override
         public void run() {
            if (endpoints_.contains(endpoint)) {
               return;
            }
            endpoints_.add(endpoint);
            asyncBus_.register(endpoint);
            endpoint.linkageDidAdmitEndpoint(Linkage.this);

            // Get one of the existing endpoints to send a resync so that the
            // newly added one gets synced
            for (LinkEndpoint e : endpoints_) {
               if (e == endpoint) {
                  continue;
               }
               if (e.linkageRequiresResync()) {
                  break;
               }
            }
         }
      });
   }

   void removeEndpoint(final LinkEndpoint endpoint) {
      executor_.submit(new Runnable() {
         @Override
         public void run() {
            if (!endpoints_.contains(endpoint)) {
               return;
            }
            endpoint.linkageWillExpelEndpoint(Linkage.this);
            asyncBus_.unregister(endpoint);
            endpoints_.remove(endpoint);

            if (endpoints_.size() == 1) {
               for (LinkEndpoint lastEndpoint : endpoints_) {
                  removeEndpoint(lastEndpoint);
               }
            }
         }
      });
   }

   void merge(final Linkage absorbee) {
      Preconditions.checkArgument(absorbee != this);
      executor_.submit(new Runnable() {
         @Override
         public void run() {
            endpoints_.addAll(absorbee.endpoints_);
            for (LinkEndpoint endpoint : absorbee.endpoints_) {
               absorbee.asyncBus_.unregister(endpoint);
               asyncBus_.register(endpoint);
               endpoint.linkageDidTransferEndpoint(absorbee, Linkage.this);
            }

            // Get one of the absorbed endpoints to do a resync, so that our
            // preexisting endpoints can sync to them.
            for (LinkEndpoint endpoint : absorbee.endpoints_) {
               if (endpoint.linkageRequiresResync()) {
                  break;
               }
            }

            absorbee.endpoints_.clear();
         }
      });
   }

   Collection<LinkEndpoint> getEndpoints() {
      Future<Collection<LinkEndpoint>> f =
            executor_.submit(new Callable<Collection<LinkEndpoint>>() {
               @Override
               public Collection<LinkEndpoint> call() {
                  return new HashSet<LinkEndpoint>(endpoints_);
               }
            });
      try {
         return f.get();
      } catch (InterruptedException unexpected) {
         throw new RuntimeException(unexpected);
      } catch (ExecutionException e) {
         throw new RuntimeException(e);
      }
   }

   // TODO change should be typed
   void propagateValue(LinkEndpoint source, Object change) {
      // We do not need to notify mergePeer_ because endpoints are kept in sync
      asyncBus_.post(LinkageValuePropagatedEvent.create(source, change));
   }
}