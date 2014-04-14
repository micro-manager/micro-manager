/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author Henry
 */
public class Navigator implements MMPlugin{

   @Override
   public void dispose() {
   }

   @Override
   public void setApp(ScriptInterface si) {
   }

   @Override
   public void show() {
      new GUI();
   }

   @Override
   public String getDescription() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public String getInfo() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public String getVersion() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public String getCopyright() {
      throw new UnsupportedOperationException("Not supported yet.");
   }
   
}
