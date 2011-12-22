/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.awt.Panel;
import org.json.JSONObject;

/**
 *
 * @author Henry
 */
public abstract class DisplayControls extends Panel {
   
   abstract public void imagesOnDiskUpdate(boolean onDisk);
   
   abstract public void acquiringImagesUpdate(boolean acquiring);
   
   abstract public void setStatusLabel(String text);
   
   abstract public void newImageUpdate(JSONObject tags);
   
   
}
