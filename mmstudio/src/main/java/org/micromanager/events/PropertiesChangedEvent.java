///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Events API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.events;

import org.micromanager.MMEvent;

/**
 * This class signals when any property of the microscope has changed.
 * Note that there is a discrepancy with the definition of OnPropertiesChanged
 * in MMCore, which is specific only for a given device (or device adapter).
 * Since the Core callbacks into this event, there is a mismatch here that needs
 * to be resolved.
 *
 * The default implementation of this event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public interface PropertiesChangedEvent extends MMEvent {
}
