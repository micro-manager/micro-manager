///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, December 3, 2006
//               Chris Weisiger, 2015
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

package org.micromanager;

import java.io.File;

/**
 * Provides access to some utility methods for use in the Beanshell scripting
 * panel. Accessible via "mm.scripter()" or "mm.getScriptController()".
 */
public interface ScriptController {
   /**
    * This exception signifies that the Beanshell interpreter has a stop
    * request pending, which prevents most interactions with the system.
    */
   class ScriptStoppedException extends RuntimeException {
      /**
       * Exception thrown when the interpreted has a stop request pending.
       *
       * @param message Exception message
       */
      public ScriptStoppedException(String message) {
         super(message);
      }
   }

   /**
    * Execute the script located in the given file.
    *
    * @param file File containing the script to be run.
    */
   void runFile(File file);

   /**
    * Displays text in the scripting console output window.
    *
    * @param text Text to be displayed in the scripting console output window.
    * @throws ScriptStoppedException if the script panel has been requested to
    *         stop execution.
    */
   void message(String text) throws ScriptStoppedException;
   
   /**
    * Clears scripting console output window.
    *
    * @throws ScriptStoppedException if the script panel has been requested to
    *         stop execution.
    */
   void clearMessageWindow() throws ScriptStoppedException;

   /**
    * Clears all methods and variables defined in the script interpreter.
    *
    * @throws ScriptStoppedException if the script panel has been requested to
    *         stop execution.
    */
   void resetInterpreter() throws ScriptStoppedException;
}
