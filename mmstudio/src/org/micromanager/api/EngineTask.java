/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.api;

import org.micromanager.acquisition.engine.Engine;

/**
 *
 * @author arthur
 */
public interface EngineTask {
   public void run(Engine eng);
   public void requestStop();
   public void requestPause();
   public void requestResume();
}
