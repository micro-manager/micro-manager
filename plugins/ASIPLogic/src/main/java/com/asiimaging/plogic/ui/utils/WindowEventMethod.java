/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.utils;

import java.util.EventObject;

@FunctionalInterface
public interface WindowEventMethod {
   void run(EventObject event);
}
