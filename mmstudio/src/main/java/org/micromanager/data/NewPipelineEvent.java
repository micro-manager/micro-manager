///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
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

package org.micromanager.data;

import org.micromanager.MMEvent;

/**
 * This class signifies that the configuration of the application pipeline
 * has changed, giving entities that use that pipeline an opportunity to make
 * a new copy of it (by invoking DataManager.copyApplicationPipeline()).
 *
 * The default implementation of this event is posted on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public interface NewPipelineEvent extends MMEvent {}
