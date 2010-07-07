/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import mmcorej.Metadata;

/**
 *
 * @author arthur
 */
public class MMImageBuffer {

   public Object img;
   public Metadata md;
   String filename;

   public MMImageBuffer(String filename, Object img, Metadata md) {
      this.img = img;
      this.md = md;
      this.filename = filename;
   }

   public MMImageBuffer(Object img, Metadata md) {
      this.img = img;
      this.md = md;
      this.filename = "";
   }

}
