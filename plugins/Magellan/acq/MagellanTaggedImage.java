/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import json.JSONObject;

/**
 *
 * @author Henry
 */
public class MagellanTaggedImage {
   
   public final JSONObject tags;
   public final Object pix;
   
   public MagellanTaggedImage(Object pix, JSONObject tags) {
      this.pix = pix;
      this.tags = tags;
   }
   
}
