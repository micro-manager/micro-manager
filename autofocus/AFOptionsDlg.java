import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;


public class AFOptionsDlg extends JDialog {
   private static final long serialVersionUID = 0L;
   
   private JTextField sizeFirstField_;
   private JTextField numFirstField_;
   private JTextField sizeSecondField_;
   private JTextField numSecondField_;
   private JTextField cropSizeField_;
   private JTextField thresField_;
    private JTextField channelField_;
   
   private Autofocus_ af_;

   /**
    * Create the dialog
    */
    public AFOptionsDlg(Autofocus_ af) {//constructor
      super();
      setModal(true);
      af_ = af;
      getContentPane().setLayout(null);
      setTitle("Autofocus Options");
      setBounds(100, 100, 353, 260);

      channelField_ = new JTextField();
      channelField_.setBounds(136, 9, 90, 20);
      getContentPane().add(channelField_);
      
      final JLabel autofocusChannelLabel = new JLabel();
      autofocusChannelLabel.setText("Auto-focus channel");
      autofocusChannelLabel.setBounds(10, 13, 108, 14);
      getContentPane().add(autofocusChannelLabel);

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

      sizeSecondField_ = new JTextField();
      sizeSecondField_.setBounds(136, 90, 90, 20);
      getContentPane().add(sizeSecondField_);

      final JLabel sizeSecondLabel = new JLabel();
      sizeSecondLabel.setText("2nd step size[um]");
      sizeSecondLabel.setBounds(10, 94, 91, 14);
      getContentPane().add(sizeSecondLabel);

      numSecondField_ = new JTextField();
      numSecondField_.setBounds(136, 117, 90, 20);
      getContentPane().add(numSecondField_);

      final JLabel numSecondLabel = new JLabel();
      numSecondLabel.setText("2nd step number");
      numSecondLabel.setBounds(10, 121, 91, 14);
      getContentPane().add(numSecondLabel);

      cropSizeField_ = new JTextField();
      cropSizeField_.setBounds(136, 144, 90, 20);
      getContentPane().add(cropSizeField_);

      final JLabel cropSizeLabel = new JLabel();
      cropSizeLabel.setText("Crop ratio");
      cropSizeLabel.setBounds(10, 148, 91, 14);
      getContentPane().add(cropSizeLabel);


      thresField_ = new JTextField();
      thresField_.setBounds(136, 171, 90, 20);
      getContentPane().add(thresField_);

      final JLabel thresLabel = new JLabel();
      thresLabel.setText("Threshold");
      thresLabel.setBounds(10, 174, 91, 14);
      getContentPane().add(thresLabel);

  

      final JButton okButton = new JButton();
      okButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            applySettings();
            dispose();
         }
      });
      okButton.setText("OK");
      okButton.setBounds(245, 13, 93, 23);
      getContentPane().add(okButton);

      final JButton cancelButton = new JButton();
      cancelButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            dispose();
         }
      });
      cancelButton.setText("Cancel");
      cancelButton.setBounds(245, 39, 93, 23);
      getContentPane().add(cancelButton);

      
      setupDisplay();
   }

   private void setupDisplay() {
      sizeFirstField_.setText(Double.toString(af_.SIZE_FIRST));
      numFirstField_.setText(Double.toString(af_.NUM_FIRST));
      sizeSecondField_.setText(Double.toString(af_.SIZE_SECOND));
      numSecondField_.setText(Double.toString(af_.NUM_SECOND));
      cropSizeField_.setText(Double.toString(af_.CROP_SIZE));
      thresField_.setText(Double.toString(af_.THRES));
      channelField_.setText(af_.CHANNEL);
   }

   protected void applySettings() {
      af_.SIZE_FIRST = Double.parseDouble(sizeFirstField_.getText());
      af_.NUM_FIRST = Integer.parseInt(numFirstField_.getText());
      af_.SIZE_SECOND = Double.parseDouble(sizeSecondField_.getText());
      af_.NUM_SECOND = Integer.parseInt(numSecondField_.getText());
      af_.CROP_SIZE = Double.parseDouble(cropSizeField_.getText());
      af_.THRES = Double.parseDouble(thresField_.getText());
      af_.CHANNEL = channelField_.getText();
   }

}
