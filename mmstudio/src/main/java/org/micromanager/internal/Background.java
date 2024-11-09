package org.micromanager.internal;


import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import org.micromanager.Studio;

/**
 * Provides optional background Frames, one per monitor.
 * Bit crummy in behavior, but can look pretty good at times.
 */
public class Background {
   private static final String MICRO_MANAGER_TITLE = "Micro-Manager";
   private JFrame[] frames_;

   /**
    * Construct the background frames, one per monitor.
    *
    * @param studio Stay connected to studio, used here to exit the application when this window
    *               is closed.
    */
   public Background(Studio studio) {
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] gd = ge.getScreenDevices();
      frames_ = new JFrame[gd.length];
      for (int i = 0; i < gd.length; i++) {
         frames_[i] = createFrame(studio, gd[i].getDefaultConfiguration().getBounds());
      }
   }

   private JFrame createFrame(Studio studio, Rectangle rect) {
      // This removes the title bar, but also hides the Windows taskbar, which I do not want
      //setUndecorated(false); // Remove window decorations
      JFrame frame = new JFrame(MICRO_MANAGER_TITLE);
      frame.setLocation(rect.x, rect.y + frame.getY());
      frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
      frame.setFocusableWindowState(false);
      frame.toBack();
      frame.setIconImage(Toolkit.getDefaultToolkit().getImage(
              getClass().getResource("/org/micromanager/icons/microscope.gif")));
      frame.setTitle(String.format("%s %s", MICRO_MANAGER_TITLE,
              MMVersion.VERSION_STRING));
      frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      frame.addWindowListener(new WindowAdapter() {
         @Override
         public void windowActivated(WindowEvent event) {
            for (JFrame f : frames_) {
               f.setMenuBar(null);
               f.setJMenuBar(frame.getJMenuBar());
               f.toBack();
            }
         }

         @Override
         public void windowDeiconified(WindowEvent event) {
            for (JFrame f : frames_) {
               f.setState(Frame.NORMAL);
               f.toBack();
            }
         }

         @Override
         public void windowIconified(WindowEvent event) {
            for (JFrame f : frames_) {
               f.setState(Frame.ICONIFIED);
            }
         }

         @Override
         public void windowClosing(WindowEvent event) {
            ((MMStudio) studio).closeSequence(false);
         }
      });

      frame.setEnabled(true);
      return frame;
   }

   /**
    * Sends all background frames to the back.
    */
   public void toBack() {
      for (JFrame frame : frames_) {
         frame.toBack();
      }
   }

   /**
    * Sets visibility of all background frames.
    *
    * @param visible whether these frames will become visible.
    */
   public void setVisible(boolean visible) {
      for (JFrame frame : frames_) {
         frame.setVisible(visible);
      }
   }

}