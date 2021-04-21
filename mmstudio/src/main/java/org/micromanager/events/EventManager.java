///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006-2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.events;

/**
 * This interface provides access to the application-wide EventBus, on which the events in the
 * org.micromanager.events package are posted.
 */
public interface EventManager {
  /**
   * Register the specified object to receive events. Any public void methods in the object that a)
   * are marked with a @Subscribe annotation, and b) take a single event from the
   * org.micromanager.events package as their only argument will be invoked with the appropriate
   * event object when those events occur.
   *
   * @param obj The object that should be subscribed to receive event notifications.
   */
  public void registerForEvents(Object obj);

  /**
   * Un-register the provided object from the EventBus, so that it will no longer receive event
   * notifications. You should make certain to do this when objects are disposed of (e.g. if they
   * are attached to a window that is being closed). Failure to do so can result in errors when the
   * event is published to an invalid object.
   *
   * @param obj The object that should no longer receive event notifications.
   */
  public void unregisterForEvents(Object obj);

  /**
   * Post an event on the EventBus, so that subscribers for that event can be notified.
   *
   * @param event The event object to be posted.
   */
  public void post(Object event);
}
