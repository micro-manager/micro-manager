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

package org.micromanager.internal.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * An annotation to mark methods that must be called on the EDT.
 *
 * <p>Does not have any functionality or enforcement (yet).
 *
 * @author Mark Tsuchida
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface MustCallOnEDT {}
