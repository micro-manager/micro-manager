/****************************************************
 Thomas Boudier, MCU Université Paris 6,
 UMR 7101 / IFR 83. Bat A 328, Jussieu.
 Tel : 0144273578/2013  Fax : 01 44 27 25 08
****************************************************/ 
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;

/**
*  Description of the Class
*
*@author     thomas
*@created    30 mai 2008
*/
public class AFOptionsDlgTB extends JDialog {
   private final static long serialVersionUID = 0L;

   private JTextField sizeFirstField_;
   private JTextField numFirstField_;
   private JTextField sizeSecondField_;
   private JTextField numSecondField_;
   private JTextField cropSizeField_;
   private JTextField thresField_;
   private JTextField channelField1_;
   private JTextField channelField2_;

   private AutofocusTB_ af_;


   /**
    *  Create the dialog
    *
    *@param  af  Description of the Parameter
    */
   public AFOptionsDlgTB(AutofocusTB_ af) {
      //constructor
      super();
      System.out.println("AF Dialog");
      setModal(true);
      af_ = af;
      getContentPane().setLayout(null);
      setTitle("Autofocus Options");
      setBounds(100, 100, 353, 260);

      channelField1_ = new JTextField();
      channelField1_.setBounds(136, 9, 90, 20);
      getContentPane().add(channelField1_);

      final JLabel autofocusChannel1Label = new JLabel();
      autofocusChannel1Label.setText("Auto-focus channel 1");
      autofocusChannel1Label.setBounds(10, 13, 108, 14);
      getContentPane().add(autofocusChannel1Label);

      sizeFirstField_ = new JTextField();
      sizeFirstField_.setBounds(136, 36, 90, 20);
      getContentPane().add(sizeFirstField_);

      final JLabel sizeFirstLabel = new JLabel();
      sizeFirstLabel.setText("1st step size [um]");
      sizeFirstLabel.setBounds(10, 40, 91, 14);
      getContentPane().add(sizeFirstLabel);

      numFirstField_ = new JTextField();
      numFirstField_.setBounds(136, 63, 90, 20);
      getContentPane().add(numFirstField_);

      final JLabel numFirstLabel = new JLabel();
      numFirstLabel.setText("1st step number");
      numFirstLabel.setBounds(10, 67, 91, 14);
      getContentPane().add(numFirstLabel);

      final JLabel autofocusChannel2Label = new JLabel();
      autofocusChannel2Label.setText("Auto-focus channel 2");
      autofocusChannel2Label.setBounds(10, 94, 108, 14);
      getContentPane().add(autofocusChannel2Label);

      channelField2_ = new JTextField();
      channelField2_.setBounds(136, 90, 90, 20);
      getContentPane().add(channelField2_);

      sizeSecondField_ = new JTextField();
      sizeSecondField_.setBounds(136, 117, 90, 20);
      getContentPane().add(sizeSecondField_);

      final JLabel sizeSecondLabel = new JLabel();
      sizeSecondLabel.setText("2nd step size[um]");
      sizeSecondLabel.setBounds(10, 121, 91, 14);
      getContentPane().add(sizeSecondLabel);

      numSecondField_ = new JTextField();
      numSecondField_.setBounds(136, 144, 90, 20);
      getContentPane().add(numSecondField_);

      final JLabel numSecondLabel = new JLabel();
      numSecondLabel.setText("2nd step number");
      numSecondLabel.setBounds(10, 148, 91, 14);
      getContentPane().add(numSecondLabel);

      cropSizeField_ = new JTextField();
      cropSizeField_.setBounds(136, 171, 90, 20);
      getContentPane().add(cropSizeField_);

      final JLabel cropSizeLabel = new JLabel();
      cropSizeLabel.setText("Crop ratio");
      cropSizeLabel.setBounds(10, 174, 91, 14);
      getContentPane().add(cropSizeLabel);

      thresField_ = new JTextField();
      thresField_.setBounds(136, 197, 90, 20);
      getContentPane().add(thresField_);

      final JLabel thresLabel = new JLabel();
      thresLabel.setText("Threshold");
      thresLabel.setBounds(10, 200, 91, 14);
      getContentPane().add(thresLabel);

      final JButton okButton = new JButton();
      okButton.addActionListener(
         new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
               applySettings();
               dispose();
            }
         });
      okButton.setText("OK");
      okButton.setBounds(245, 13, 93, 23);
      getContentPane().add(okButton);

      final JButton cancelButton = new JButton();
      cancelButton.addActionListener(
         new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               dispose();
            }
         });
      cancelButton.setText("Cancel");
      cancelButton.setBounds(245, 39, 93, 23);
      getContentPane().add(cancelButton);

      setupDisplay();
   }


   /**
    *  Description of the Method
    */
   private void setupDisplay() {
      sizeFirstField_.setText(Double.toString(af_.SIZE_FIRST));
      numFirstField_.setText(Double.toString(af_.NUM_FIRST));
      sizeSecondField_.setText(Double.toString(af_.SIZE_SECOND));
      numSecondField_.setText(Double.toString(af_.NUM_SECOND));
      cropSizeField_.setText(Double.toString(af_.CROP_SIZE));
      thresField_.setText(Double.toString(af_.THRES));
      channelField1_.setText(af_.CHANNEL1);
      channelField2_.setText(af_.CHANNEL2);
   }


   /**
    *  Description of the Method
    */
   protected void applySettings() {
      af_.SIZE_FIRST = Double.parseDouble(sizeFirstField_.getText());
      af_.NUM_FIRST = Double.parseDouble(numFirstField_.getText());
      af_.SIZE_SECOND = Double.parseDouble(sizeSecondField_.getText());
      af_.NUM_SECOND = Double.parseDouble(numSecondField_.getText());
      af_.CROP_SIZE = Double.parseDouble(cropSizeField_.getText());
      af_.THRES = Double.parseDouble(thresField_.getText());
      af_.CHANNEL1 = channelField1_.getText();
      af_.CHANNEL2 = channelField2_.getText();
   }

}
