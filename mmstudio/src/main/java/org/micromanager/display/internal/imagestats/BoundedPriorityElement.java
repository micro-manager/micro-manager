// Copyright (C) 2016 Open Imaging, Inc.
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

package org.micromanager.display.internal.imagestats;

/**
 * An item with a small nonnegative integer priority attached.
 * @author Mark A. Tsuchida
 */
public interface BoundedPriorityElement {
   /**
    * Get the priority, which must be a small nonnegative integer.
    *
    * @return the priority of the element
    */
   int getPriority();
}
