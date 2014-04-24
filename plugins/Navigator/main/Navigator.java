package main;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import gui.GUI;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author Henry
 */
public class Navigator implements MMPlugin{

   private static final String VERSION = "1.0";
   
   public static final String menuName = "Navigator";
   public static final String tooltipDescription = "Navigator plugin";

   
   
   
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
      return "test description";
   }

   @Override
   public String getInfo() {
      return "test info";
   }

   @Override
   public String getVersion() {
      return VERSION;
   }

   @Override
   public String getCopyright() {
      return "Henry Pinkard UCSF 2014";
   }
   
}
