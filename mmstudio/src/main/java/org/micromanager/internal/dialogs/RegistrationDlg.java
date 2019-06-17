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
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import org.micromanager.UserProfile;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.profile.internal.LegacyMM1Preferences;

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
    */
   public static void showIfNecessary() {
      if (getHaveRegistered() || getShouldNeverRegister()) {
         return;
      }
      new RegistrationDlg().setVisible(true);
   }

   private JTextArea welcomeTextArea_;
   private JTextField email_;
   private JTextField inst_;
   private JTextField name_;

   /**
    * Dialog to collect registration data from the user.
    */
   public RegistrationDlg() {
      super();

      incrementRegistrationAttempts();

      setModal(true);
      setUndecorated(true);
      setTitle("Micro-Manager Registration");
      setLayout(new MigLayout());

      JPanel contents = new JPanel(new MigLayout("flowx"));
      welcomeTextArea_ = new JTextArea();
      welcomeTextArea_.setColumns(40);
      welcomeTextArea_.setMargin(new Insets(10, 10, 10, 10));
      welcomeTextArea_.setLineWrap(true);
      // HACK: convert to Color, because the ColorUIResource that
      // DaytimeNighttime returns here gets overridden as soon as setVisible()
      // is called, for unknown reasons.
      welcomeTextArea_.setBackground(new Color(
            DaytimeNighttime.getInstance().getDisabledBackgroundColor().getRGB()));
      welcomeTextArea_.setFocusable(false);
      welcomeTextArea_.setEditable(false);
      welcomeTextArea_.setFont(new Font("Arial", Font.PLAIN, 12));
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
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            if (name_.getText().length() == 0 ||
               email_.getText().length() == 0) {
               JOptionPane.showMessageDialog(RegistrationDlg.this, "Name and email fields can't be empty.");
            }
            else {
               try {
                  URL url;
                  InputStream is;
                  BufferedReader br;

                  // save registration information to profile
                  UserProfile profile = MMStudio.getInstance().profile();
                  profile.setString(RegistrationDlg.class, REGISTRATION_NAME,
                     name_.getText());
                  profile.setString(RegistrationDlg.class, REGISTRATION_INST,
                     inst_.getText());

                  // replace special characters to properly format the command string
                  String name = name_.getText().replaceAll("[ \t]", "%20");
                  name = name.replaceAll("[&]", "%20and%20");
                  String inst = inst_.getText().replaceAll("[ \t]", "%20");
                  inst = inst.replaceAll("[&]", "%20and%20");
                  String email = email_.getText().replaceAll("[ \t]", "%20");
                  email = email.replaceAll("[&]", "%20and%20");

                  String regText = "http://valelab.ucsf.edu/micro-manager-registration.php?Name=" + name +
                  "&Institute=" + inst + "&email=" + email;

                  url = new URL(regText);
                  is = url.openStream();
                  br = new BufferedReader(new InputStreamReader(is));
                  String response = br.readLine();
                  if (response.compareTo("SUCCESS") != 0) {
                     JOptionPane.showMessageDialog(RegistrationDlg.this, "Registration did not succeed. You will be prompted again next time.");
                     dispose();
                     return;
                  }

               }
               catch (java.net.UnknownHostException e) {
                  ReportingUtils.showError(e, "Registration did not succeed. You are probably not connected to the Internet.\n" +
                                                "You will be prompted again next time you start.");
               }
               catch (MalformedURLException e) {
                  ReportingUtils.showError(e);
               }
               catch (IOException e) {
                  ReportingUtils.showError(e);
               }
               catch (SecurityException e) {
                  ReportingUtils.showError(e,
                        "\nThe program failed to save registration status.\n" +
                        "Most likely you are not logged in with administrator privileges.\n" +
                  "Please try registering again using the administrator's account.");

               }
               catch (Exception e) {
                  ReportingUtils.logError(e);
               }
               finally {
                  dispose();
               }
               // save to profile
               setHaveRegistered(true);
            }
         }
      });
      okButton.setText("OK");
      contents.add(okButton, "split 3, span, alignx center");

      final JButton skipButton = new JButton();
      skipButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            JOptionPane.showMessageDialog(RegistrationDlg.this, "You choose to postpone registration.\n" +
                  "This prompt will appear again next time you start the application.");
            setShouldNeverRegister(false);
            dispose();
         }
      });
      skipButton.setText("Later");
      contents.add(skipButton);

      // Don't show "never" button the first time
      if (getNumRegistrationAttempts() > 1) {
         final JButton neverButton = new JButton();
         neverButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
               JOptionPane.showMessageDialog(RegistrationDlg.this, "You have chosen never to register. \n" +
                     "If you change your mind in the future, please\nchoose the \"Register\" option in the Help menu.");
               setShouldNeverRegister(true);
               dispose();
            }
         });
         neverButton.setText("Never");
         contents.add(neverButton);
      }

      getContentPane().add(contents);
      pack();
      Dimension winSize = getSize();
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      setLocation(screenSize.width/2 - (winSize.width/2), screenSize.height/2 - (winSize.height/2));
   }

   private int incrementRegistrationAttempts() {
      UserProfile profile = MMStudio.getInstance().profile();
      int attempts = getNumRegistrationAttempts() + 1;
      profile.setInt(RegistrationDlg.class, REGISTRATION_ATTEMPTS, attempts);
      return attempts;
   }

   public static boolean getHaveRegistered() {
      // HACK: if there's no entry, we also check the 1.4 Preferences.
      Boolean result = MMStudio.getInstance().profile().getBoolean(
            RegistrationDlg.class, HAVE_REGISTERED, null);
      if (result != null) {
         return result;
      }
      Preferences user = LegacyMM1Preferences.getUserRoot();
      Preferences system = LegacyMM1Preferences.getSystemRoot();
      if (user != null) {
         if (user.getBoolean("registered", false)) {
            setHaveRegistered(true);
            return true;
         }
         else if (user.getBoolean("reg_never", false)) {
            setShouldNeverRegister(true);
            return true;
         }
      }
      if (system != null) {
         if (system.getBoolean("registered", false)) {
            setHaveRegistered(true);
            return true;
         }
         else if (system.getBoolean("reg_never", false)) {
            setShouldNeverRegister(true);
            return true;
         }
      }
      // User hasn't registered or opted out of registering.
      return false;
   }

   public static void setHaveRegistered(boolean haveRegistered) {
      MMStudio.getInstance().profile().setBoolean(
            RegistrationDlg.class, HAVE_REGISTERED, haveRegistered);
   }

   public static boolean getShouldNeverRegister() {
      return MMStudio.getInstance().profile().getBoolean(
            RegistrationDlg.class, SHOULD_NEVER_REGISTER, false);
   }

   public static void setShouldNeverRegister(boolean haveRegistered) {
      MMStudio.getInstance().profile().setBoolean(
            RegistrationDlg.class, SHOULD_NEVER_REGISTER, haveRegistered);
   }

   private static int getNumRegistrationAttempts() {
      return MMStudio.getInstance().profile().getInt(
            RegistrationDlg.class, REGISTRATION_ATTEMPTS, 0);
   }
}
