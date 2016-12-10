// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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

package org.micromanager;

/**
 * Any object that bears an internal event bus to which other objects can
 * subscribe to receive events.
 *
 * @author Mark A. Tsuchida
 */
public interface EventPublisher {
   /**
    * Register an object to receive events on the viewer event bus.
    * <p>
    * Objects registered by this method will receive events through their
    * methods bearing a {@code com.google.common.eventbus.Subscribe}
    * annotation. See Guava Event Bus documentation for how this works.
    *
    * @param recipient the object to register
    *
    * @see unregisterForEvents
    */
   void registerForEvents(Object recipient);

   /**
    * Unregister an object from the viewer event bus.
    *
    * @param recipient the object to unregister
    *
    * @see registerForEvents
    */
   void unregisterForEvents(Object recipient);
}