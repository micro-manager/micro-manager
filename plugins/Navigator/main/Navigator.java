package main;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import gui.GUI;
import java.util.prefs.Preferences;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author Henry
 */
public class Navigator implements MMPlugin{

   private static final String VERSION = "Beta";
   
   public static final String PREF_SAVING_DIR = "Saving Directory";
   public static final String PREF_SAVING_NAME = "Saving name";
           
   public static final String menuName = "Navigator";
   public static final String tooltipDescription = "Navigator plugin";

   private Preferences prefs_;
   private ScriptInterface mmAPI_;
   
   
   public Navigator() {
      prefs_ = Preferences.userNodeForPackage(Navigator.class);
   }
   
   @Override
   public void dispose() {
   }

   @Override
   public void setApp(ScriptInterface si) {
      mmAPI_ = si;
   }

   @Override
   public void show() {
      new GUI(prefs_, mmAPI_,VERSION);
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
