package org.micromanager.internal;


import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import org.micromanager.Studio;

/**
 * Provides the optional background Frame.
 * Bit crummy in behavior, but can look pretty good at times.
 */
public class BackgroundFrame extends JFrame {
   private static final String MICRO_MANAGER_TITLE = "Micro-Manager";

   /**
    * Construct the background frame.
    *
    * @param studio Stay connected to studio, used here to exit the application when this window
    *               is closed.
    */
   public BackgroundFrame(Studio studio) {

      // This removes the title bar, but also hides the Windows taskbar, which I do not want
      //setUndecorated(false); // Remove window decorations
      setExtendedState(JFrame.MAXIMIZED_BOTH);
      setFocusableWindowState(false);
      toBack();
      setIconImage(Toolkit.getDefaultToolkit().getImage(
              getClass().getResource("/org/micromanager/icons/microscope.gif")));
      setTitle(String.format("%s %s", MICRO_MANAGER_TITLE,
              MMVersion.VERSION_STRING));
      setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowActivated(WindowEvent event) {
            setMenuBar(null);
            setJMenuBar(getJMenuBar());
            toBack();
         }

         @Override
         public void windowDeiconified(WindowEvent event) {
            toBack();
         }

         @Override
         public void windowIconified(WindowEvent event) {
            toBack();
         }

         @Override
         public void windowClosing(WindowEvent event) {
            ((MMStudio) studio).closeSequence(false);
         }
      });

      setEnabled(true);
   }

}