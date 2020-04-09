///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 20, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// CVS:          $Id$
//

package org.micromanager.internal.dialogs;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.profile.internal.LegacyMM1Preferences;
import org.micromanager.propertymap.MutablePropertyMapView;

// TODO: the change to the Profile system (away from Preferences) has caused
// registration state to be lost.
public final class RegistrationDlg extends JDialog {
   private static final long serialVersionUID = 1L;
   public static final String HAVE_REGISTERED = "this user has registered with Micro-Manager";
   public static final String SHOULD_NEVER_REGISTER = "this user never wants to be prompted to register";
   public static final String REGISTRATION_ATTEMPTS = "number of times we've shown the registration dialog to this user";
   public static final String REGISTRATION_NAME = "name under which this user has registered";
   public static final String REGISTRATION_INST = "institution at which this user works";

   /**
    * Display the registration dialog, if the user has not registered and not
    * opted out of registering. 
    * @param studio Uses studio.app().skin() and studio.profile(), so make sure 
    * those are initialized before calling this function
    */
   public static void showIfNecessary(Studio studio) {
      if (getHaveRegistered(studio) || getShouldNeverRegister(studio)) {
         return;
      }
      new RegistrationDlg(studio).setVisible(true);
   }
   
    /**
    * Display the registration dialog, 
    * For debugging purposes only
    * @param studio Uses studio.app().skin() and studio.profile(), so make sure 
    * those are initialized before calling this function
    */
   public static void showRegistration(Studio studio) {
      
      new RegistrationDlg(studio).setVisible(true);
   }
   

   private JTextArea welcomeTextArea_;
   private JTextField email_;
   private JTextField inst_;
   private JTextField name_;
   private final Studio studio_;

   /**
    * Dialog to collect registration data from the user.
    * @param studio Uses studio.app().skin() and studio.profile(), so make sure 
    * those are initialized before instantiating this class
    */
   public RegistrationDlg(Studio studio) {
      super();

      studio_ = studio;
      incrementRegistrationAttempts();

      super.setModal(true);
      super.setUndecorated(true);
      super.setTitle("Micro-Manager Registration");
      super.setLayout(new MigLayout());

      JPanel contents = new JPanel(new MigLayout("flowx"));
      welcomeTextArea_ = new JTextArea();
      welcomeTextArea_.setColumns(40);
      welcomeTextArea_.setMargin(new Insets(10, 10, 10, 10));
      welcomeTextArea_.setLineWrap(true);
      welcomeTextArea_.setBackground(new Color(
               studio_.app().skin().getDisabledBackgroundColor().getRGB()));
      welcomeTextArea_.setFocusable(false);
      welcomeTextArea_.setEditable(false);
      welcomeTextArea_.setFont(new Font("Arial", Font.PLAIN, 12));
      welcomeTextArea_.setForeground(studio_.app().skin().getDisabledTextColor());
      welcomeTextArea_.setWrapStyleWord(true);
      welcomeTextArea_.setText("Welcome to Micro-Manager.\n\n" +
            "Please take a minute to let us know that you are using this " +
            "software. The information you enter will only be used to " +
            "estimate how many people use Micro-Manager. Accurate tracking " +
            "of the number of users is very important for our funding " +
            "efforts and essential for the long-term survival of " +
            "Micro-Manager.\n\n" +
            "Your information will never be made public or given away to " +
            "third parties, however, we might very occasionally inform you " +
            "of Micro-Manager developments.");
      contents.add(welcomeTextArea_, "alignx center, span, wrap");

      final JLabel nameLabel = new JLabel("Name");
      contents.add(nameLabel, "split 2, span, width 75");
      name_ = new JTextField(25);
      contents.add(name_, "wrap");

      final JLabel institutionLabel = new JLabel("Institution");
      contents.add(institutionLabel, "split 2, span, width 75");
      inst_ = new JTextField(25);
      contents.add(inst_, "wrap");

      final JLabel emailLabel = new JLabel("Email");
      contents.add(emailLabel, "split 2, span, width 75");
      email_ = new JTextField(25);
      contents.add(email_, "wrap");

      final JButton okButton = new JButton();
      okButton.setFont(new Font("", Font.BOLD, 12));
      okButton.addActionListener((ActionEvent arg0) -> {
         if (name_.getText().length() == 0 ||
                 email_.getText().length() == 0) {
            JOptionPane.showMessageDialog(RegistrationDlg.this, "Name and email fields can't be empty.");
         } else {
            try {
               URL url;
               InputStream is;
               BufferedReader br;
               // save registration information to profile
               MutablePropertyMapView settings = studio_.profile().getSettings(RegistrationDlg.class);
               settings.putString(REGISTRATION_NAME, name_.getText());
               settings.putString(REGISTRATION_INST, inst_.getText());
               // replace special characters to properly format the command string
               String name1 = name_.getText().replaceAll("[ \t]", "%20");
               name1 = name1.replaceAll("[&]", "%20and%20");
               String inst = inst_.getText().replaceAll("[ \t]", "%20");
               inst = inst.replaceAll("[&]", "%20and%20");
               String email = email_.getText().replaceAll("[ \t]", "%20");
               email = email.replaceAll("[&]", "%20and%20");
               String regText = "http://valelab.ucsf.edu/micro-manager-registration.php?Name=" + name1 + "&Institute=" + inst + "&email=" + email;
               url = new URL(regText);
               is = url.openStream();
               br = new BufferedReader(new InputStreamReader(is));
               String response = br.readLine();
               if (response.compareTo("SUCCESS") != 0) {
                  JOptionPane.showMessageDialog(RegistrationDlg.this, "Registration did not succeed. You will be prompted again next time.");
                  dispose();
                  return;
               }
            }catch (java.net.UnknownHostException e) {
               ReportingUtils.showError(e, "Registration did not succeed. You are probably not connected to the Internet.\n" +
                       "You will be prompted again next time you start.");
            }catch (MalformedURLException e) {
               ReportingUtils.showError(e);
            }catch (IOException e) {
               ReportingUtils.showError(e);
            }catch (SecurityException e) {
               ReportingUtils.showError(e,
                       "\nThe program failed to save registration status.\n" +
                               "Most likely you are not logged in with administrator privileges.\n" +
                               "Please try registering again using the administrator's account.");
               
            }catch (HeadlessException e) {
               ReportingUtils.logError(e);
            } finally {
               dispose();
            }
            // save to profile
            setHaveRegistered(studio_, true);
         }
      });
      okButton.setText("OK");
      contents.add(okButton, "split 3, span, alignx center");

      final JButton skipButton = new JButton();
      skipButton.addActionListener((ActionEvent arg0) -> {
         JOptionPane.showMessageDialog(RegistrationDlg.this, "You choose to postpone registration.\n" +
                 "This prompt will appear again next time you start the application.");
         setShouldNeverRegister(studio_, false);
         dispose();
      });
      skipButton.setText("Later");
      contents.add(skipButton);

      // Don't show "never" button the first time
      if (getNumRegistrationAttempts(studio_) > 1) {
         final JButton neverButton = new JButton();
         neverButton.addActionListener((ActionEvent arg0) -> {
            JOptionPane.showMessageDialog(RegistrationDlg.this, "You have chosen never to register. \n" +
                    "If you change your mind in the future, please\nchoose the \"Register\" option in the Help menu.");
            setShouldNeverRegister(studio_, true);
            dispose();
         });
         neverButton.setText("Never");
         contents.add(neverButton);
      }

      super.getContentPane().add(contents);
      super.pack();
      Dimension winSize = super.getSize();
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      super.setLocation(screenSize.width/2 - (winSize.width/2), screenSize.height/2 - (winSize.height/2));
   }

   private int incrementRegistrationAttempts() {
      MutablePropertyMapView settings = studio_.profile().
              getSettings(RegistrationDlg.class);
      int attempts = getNumRegistrationAttempts(studio_) + 1;
      settings.putInteger(REGISTRATION_ATTEMPTS, attempts);
      return attempts;
   }

   public static boolean getHaveRegistered(Studio studio) {
      // HACK: if there's no entry, we also check the 1.4 Preferences.
      MutablePropertyMapView settings = studio.profile().getSettings(RegistrationDlg.class);
      if (settings.containsBoolean(HAVE_REGISTERED)) {
         return settings.getBoolean(HAVE_REGISTERED, false);
      }
      Preferences user = LegacyMM1Preferences.getUserRoot();
      Preferences system = LegacyMM1Preferences.getSystemRoot();
      if (user != null) {
         if (user.getBoolean("registered", false)) {
            setHaveRegistered(studio, true);
            return true;
         }
         else if (user.getBoolean("reg_never", false)) {
            setShouldNeverRegister(studio, true);
            return true;
         }
      }
      if (system != null) {
         if (system.getBoolean("registered", false)) {
            setHaveRegistered(studio, true);
            return true;
         }
         else if (system.getBoolean("reg_never", false)) {
            setShouldNeverRegister(studio,true);
            return true;
         }
      }
      // User hasn't registered or opted out of registering.
      return false;
   }

   public static void setHaveRegistered(Studio studio, boolean haveRegistered) {
      studio.profile().getSettings(RegistrationDlg.class).
              putBoolean(HAVE_REGISTERED, haveRegistered);
   }

   public static boolean getShouldNeverRegister(Studio studio) {
      return studio.profile().getSettings(RegistrationDlg.class).
              getBoolean(SHOULD_NEVER_REGISTER, false);
   }

   public static void setShouldNeverRegister(Studio studio, boolean haveRegistered) {
      studio.profile().getSettings(RegistrationDlg.class).
              putBoolean(SHOULD_NEVER_REGISTER, haveRegistered);
   }

   private static int getNumRegistrationAttempts(Studio studio) {
      return studio.profile().getSettings(RegistrationDlg.class).
              getInteger(REGISTRATION_ATTEMPTS, 0);
   }
}
