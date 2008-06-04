import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;


public class TrackerControlDlg extends JDialog {
   
   private JTextField resField_;
   private JTextField offsetField_;
   private JTextField pixelSizeField_;
   private JTextField intervalField_;
   boolean track_ = false;
   int intervalMs_ = 1000;
   double pixelSizeUm_ = 1.0;
   int resolutionPix_ = 5;
   int offsetPix_ = 100;

   /**
    * Create the dialog
    */
   public TrackerControlDlg() {
      super();
      addWindowListener(new WindowAdapter() {
         public void windowOpened(WindowEvent e) {
            resField_.setText(Integer.toString(resolutionPix_));
            offsetField_.setText(Integer.toString(offsetPix_));
            pixelSizeField_.setText(Double.toString(pixelSizeUm_));
            intervalField_.setText(Integer.toString(intervalMs_));
         }
      });
      setModal(true);
      setTitle("MM Tracker Window");
      setResizable(false);
      getContentPane().setLayout(null);
      setBounds(100, 100, 412, 181);

      final JLabel intervalmsLabel = new JLabel();
      intervalmsLabel.setText("Interval [ms]");
      intervalmsLabel.setBounds(10, 10, 76, 14);
      getContentPane().add(intervalmsLabel);

      intervalField_ = new JTextField();
      intervalField_.setBounds(10, 30, 84, 19);
      getContentPane().add(intervalField_);

      final JButton trackButton = new JButton();
      trackButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            track_ = true;
            intervalMs_ = Integer.parseInt(intervalField_.getText());
            pixelSizeUm_ = Double.parseDouble(pixelSizeField_.getText());
            offsetPix_ = Integer.parseInt(offsetField_.getText());
            resolutionPix_ = Integer.parseInt(resField_.getText());
            setVisible(false);
            dispose();
         }
      });
      trackButton.setText("Track!");
      trackButton.setBounds(303, 10, 93, 23);
      getContentPane().add(trackButton);

      final JButton cancelButton = new JButton();
      cancelButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            track_ = false;
            setVisible(false);
            dispose();
         }
      });
      cancelButton.setText("Cancel");
      cancelButton.setBounds(303, 41, 93, 23);
      getContentPane().add(cancelButton);

      final JLabel pixelSizeumLabel = new JLabel();
      pixelSizeumLabel.setText("Pixel size [um]");
      pixelSizeumLabel.setBounds(10, 71, 110, 14);
      getContentPane().add(pixelSizeumLabel);

      pixelSizeField_ = new JTextField();
      pixelSizeField_.setBounds(10, 90, 84, 19);
      getContentPane().add(pixelSizeField_);

      final JLabel offsetLabel = new JLabel();
      offsetLabel.setText("Offset [pixels]");
      offsetLabel.setBounds(140, 10, 122, 14);
      getContentPane().add(offsetLabel);

      offsetField_ = new JTextField();
      offsetField_.setBounds(140, 30, 93, 19);
      getContentPane().add(offsetField_);

      final JLabel resolutionpixelsLabel = new JLabel();
      resolutionpixelsLabel.setText("Resolution [pixels]");
      resolutionpixelsLabel.setBounds(141, 71, 92, 14);
      getContentPane().add(resolutionpixelsLabel);

      resField_ = new JTextField();
      resField_.setBounds(140, 90, 93, 19);
      getContentPane().add(resField_);
      //
   }

}
