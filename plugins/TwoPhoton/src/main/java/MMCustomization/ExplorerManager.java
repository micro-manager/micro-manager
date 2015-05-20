/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package MMCustomization;

/**
 *
 * @author Henry
 */
public class ExplorerManager {
   
   
   private int zStart_ = 0, zStop_ = 10, zStep_ = 1;
   
   
   
   public ExplorerManager() {
      launchInitialSettingsDialog();
      
   
   }
   
   private void launchInitialSettingsDialog() {  
      zStart_ = 0;
      zStop_ = 10;
      zStep_ = 1;     
   }
   
}
