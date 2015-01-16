package main;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import gui.GUI;
import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import org.micromanager.internal.MMStudio;
import org.micromanager.MMPlugin;
import org.micromanager.ScriptInterface;

/**
 *
 * @author Henry
 */
public class Navigator implements MMPlugin{

   private static final String VERSION = "Beta";
           
   public static final String menuName = "Navigator";
   public static final String tooltipDescription = "Navigator plugin";

   private static Preferences prefs_;
   private static ScriptInterface mmAPI_;
   private static GUI gui_;
   
   public Navigator() {
      if (gui_ == null) {
         prefs_ = Preferences.userNodeForPackage(Navigator.class);
         gui_ = new GUI(prefs_, MMStudio.getInstance(), VERSION);
      }
   }
   
   @Override
   public void dispose() {
   }

   @Override
   public void setApp(ScriptInterface si) {
   }

   @Override
   public void show() {      
      gui_.setVisible(true);
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
