///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Events API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Mark A. Tsuchida, 2016
//
// COPYRIGHT:    (c)2016 Open Imaging, Inc.
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

/**
 * Autofocus plugins should initialize properties upon receiving this event.
 *
 * The Core is available and the hardware configuration is loaded when this
 * event is received. Note that the autofocus plugin may receive this event
 * multiple times, if the user loads a new hardware configuration.
 * @deprecated use
 */
@Deprecated
public class AutofocusPluginShouldInitializeEvent {
}