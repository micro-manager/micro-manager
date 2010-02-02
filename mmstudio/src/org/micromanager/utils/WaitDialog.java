///////////////////////////////////////////////////////////////////////////////
//FILE:          WaitDialog.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// CVS:          $Id$
//
package org.micromanager.utils;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;

/**
 * @author Jurij Henne,Maxim Bauer
 *
 * This is an implementation of a "Loading.."(or just "please wait") dialog. It pops up, when the GUI invokes some 
 * backend operations, like search or similar. 
 */
public class WaitDialog  extends JWindow  {
   private static final long serialVersionUID = 5356404305699524826L;
   // single instance of this class, used through out the scope of the application
	  private final static Cursor defaultCursor=Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
	  private final static Cursor waitCursor=Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR );

	  public WaitDialog(String message) {
         JPanel root = new JPanel();
         root.setBorder(BorderFactory.createLineBorder(new Color(0,0,0)));
         JLabel label = new JLabel(message);
         Dimension labelSize = label.getPreferredSize(); 
         root.setPreferredSize(new Dimension(labelSize.width+26,labelSize.height+16));
         root.add(label);
         this.setSize(root.getPreferredSize());                            
         this.getContentPane().add(root);
         Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
         this.setLocation(screenSize.width/2 - (labelSize.width/2),
                           screenSize.height/2 - (labelSize.height/2));
	  }

	  /**
	   * This static method uses pre-created dialog, positions it in the center
	   * and displays it to the user.
	   */
	  public void showDialog() {	
	      setCursor( waitCursor );  
	  	   setVisible(true);
	  	   paint(getGraphics());
                   update(getGraphics());
	  }

	  /**

	   * This static method closes the wait dialog.
	   */
	  public void closeDialog()   {
	     setCursor(defaultCursor );  
	  	  dispose();
	  }
	 
}
	
