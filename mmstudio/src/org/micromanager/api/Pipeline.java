/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.api;

import org.micromanager.acquisition.engine.SequenceSettings;

/**
 *
 * @author arthur
 */
public interface Pipeline {
   public void run(SequenceSettings settings);
   public void pause();
   public void stop();
}
