package org.micromanager.ndviewer2;

/**
 * Interface for adding custom control JPanels into the tabbed pane on right side
 *
 * @author henrypinkard
 */
public interface ControlsPanelInterface {

   /**
    * The tab containing this panel was selected.
    */
   public void selected();

   /**
   * A different tab was selected.
   */
   public void deselected();

   /**
   * provide a String for the title of the panel in the tabbed pane.
   */
   public String getTitle();

   /**
    * Viewer closing, release any resources.
    */
   public void close();

}
