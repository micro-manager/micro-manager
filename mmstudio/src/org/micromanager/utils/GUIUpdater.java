///////////////////////////////////////////////////////////////////////////////
//FILE:          GUIUpdater.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein and Henry Pinkard, 2011
//
// COPYRIGHT:    University of California, San Francisco, 2011
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

package org.micromanager.utils;

import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 *
 * @author arthur and henry
 * DO NOT USE.  This code is only here so that the DataBrowser plugin can still 
 * compile.  Remove after this dependency in the DataBrowser has been removed.
 * 
 * @Deprecated
 */
public class GUIUpdater {

   final AtomicReference<Runnable> latestTask = new AtomicReference<Runnable>();

   /*
    * Post a task for running on the EDT thread. If multiple
    * tasks pile up, only the most recent will run.
    */
   public void post(Runnable task) {
      if (latestTask.getAndSet(task) == null) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               latestTask.getAndSet(null).run();
            }
         });
      }
   }
}
