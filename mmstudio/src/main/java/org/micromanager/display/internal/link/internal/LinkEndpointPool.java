/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.link.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import org.micromanager.internal.utils.EventBusExceptionLogger;

import java.util.HashSet;
import java.util.Set;

/**
 * A collection of mutually compatible link endpoints.
 *
 * @author Mark A. Tsuchida
 * @author based on original link framework by Chris Weisiger
 */
public final class LinkEndpointPool {
  private final Set<LinkEndpoint> endpoints_ = new HashSet<LinkEndpoint>();

  private final EventBus bus_ = new EventBus(EventBusExceptionLogger.getInstance());

  public static LinkEndpointPool create() {
    return new LinkEndpointPool();
  }

  private LinkEndpointPool() {}

  /**
   * Make an endpoint available for linking.
   *
   * @param endpoint the endpoint
   */
  synchronized void registerEndpoint(LinkEndpoint endpoint) {
    endpoints_.add(endpoint);
    bus_.register(endpoint);
    endpoint.joinPool(this);
    bus_.post(LinkablePeerDidBecomeAvailableEvent.create(endpoint));
  }

  /**
   * Remove an endpoint.
   *
   * @param endpoint the endpoint
   */
  synchronized void unregisterEndpoint(LinkEndpoint endpoint) {
    bus_.post(LinkablePeerWillBecomeUnavailableEvent.create(endpoint));
    endpoint.leavePool(this);
    bus_.unregister(endpoint);
    endpoints_.remove(endpoint);
  }

  synchronized boolean isEmpty() {
    return endpoints_.isEmpty();
  }

  /**
   * Return all peer endpoints in this pool, including the caller.
   *
   * @param caller the calling endpoint
   * @return an immutable set containing the peers
   */
  synchronized Set<LinkEndpoint> getPeers(LinkEndpoint caller) {
    Preconditions.checkArgument(endpoints_.contains(caller));
    return ImmutableSet.<LinkEndpoint>builder().addAll(endpoints_).build();
  }
}
