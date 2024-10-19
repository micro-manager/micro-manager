/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.model;

/**
 * Generate a script to program the PLC with the current settings.
 *
 * <p>Formats:
 *
 * <p>Beanshell - generate a Beanshell script for use in Micro-Manager.
 *
 * <p>pymmcore - generate a Python script that uses the pymmcore package.
 *
 * <p>pycromanager - generate a Python script that uses the pycromanager package.
 *
 * <p>serial - generate a Python script that sends serial commands using pyserial.
 */
public class ScriptGenerator {

   public static void createScript() {
      // TODO: impl this feature; this feature should also document the pre-init settings for the PLogic device
   }

}
