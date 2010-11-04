/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition.engine;

import org.micromanager.api.EngineTask;

/**
 *
 * @author arthur
 */
public class StopTask implements EngineTask {
   public void requestStop() {}
   public void requestPause() {}
   public void requestResume() {}
   public void run(Engine eng) {}
}
