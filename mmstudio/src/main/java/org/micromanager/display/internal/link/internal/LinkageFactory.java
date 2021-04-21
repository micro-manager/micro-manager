/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.link.internal;

import org.micromanager.internal.utils.ThreadFactoryFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** @author mark */
public final class LinkageFactory {
  private final ExecutorService executor_ =
      Executors.newSingleThreadExecutor(
          ThreadFactoryFactory.createThreadFactory("Linkage Executor"));

  static LinkageFactory create() {
    return new LinkageFactory();
  }

  private LinkageFactory() {}

  private Linkage createLinkage() {
    return Linkage.create(executor_);
  }

  void linkToPeer(final LinkEndpoint target, final LinkEndpoint initiator) {
    executor_.submit(
        new Runnable() {
          @Override
          @SuppressWarnings("NestedSynchronizedStatement")
          public void run() {
            // We can access two endpoints at the same time without deadlock
            // because this is the only place where we do so and no other
            // thread is blocking this (executor) thread -- assuming that
            // linkToPeer() was not itself called from within a linkage event
            // handler (which is forbidden).
            synchronized (target) {
              synchronized (initiator) {
                Linkage targetLinkage = target.getLinkage();
                Linkage initiatorLinkage = initiator.getLinkage();
                if (targetLinkage == null) {
                  targetLinkage = createLinkage();
                  targetLinkage.addEndpoint(target);
                }
                if (initiatorLinkage == null) {
                  targetLinkage.addEndpoint(initiator);
                } else {
                  initiatorLinkage.merge(targetLinkage);
                }
              }
            }
          }
        });
  }
}
