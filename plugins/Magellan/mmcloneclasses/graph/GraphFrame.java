///////////////////////////////////////////////////////////////////////////////
//FILE:          GraphFrame.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
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

package mmcloneclasses.graph;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.border.LineBorder;
import org.micromanager.MMStudio;
import org.micromanager.utils.MMFrame;

/**
 * XY Graph window.
 *
 */
public class GraphFrame extends MMFrame {

   /**
    * 
    */
   private static final long serialVersionUID = -4969643705861534777L;
   private JTextField fldYMax;
   private JTextField fldYMin;
   private JTextField fldXMax;
   private JTextField fldXMin;
   private SpringLayout springLayout;
   private GraphPanel panel_;
   
   /*
   public static void main(String args[]) {
      try {
         UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
         GraphFrame frame = new GraphFrame();
         frame.setVisible(true);
      } catch (Exception e) {
         Log.log(e);
      }
   }
   */
   
   private void updateBounds(){
      GraphData.Bounds bounds = panel_.getGraphBounds();
      DecimalFormat fmt = new DecimalFormat("#0.00");
      fldXMin.setText(fmt.format(bounds.xMin));
      fldXMax.setText(fmt.format(bounds.xMax));
      fldYMin.setText(fmt.format(bounds.yMin));
      fldYMax.setText(fmt.format(bounds.yMax));
   }
   
   public void setAutoScale() {
      panel_.setAutoBounds();
      updateBounds();
      refresh();
   }
   public void setData(GraphData data){
      panel_.setData(data);
      refresh();
   }
   
   public void refresh() {
      GraphData.Bounds bounds = panel_.getGraphBounds();
      if (fldXMin.getText().length() > 0 && fldYMin.getText().length() > 0 && 
          fldXMax.getText().length() > 0 && fldYMax.getText().length() > 0 )
      {      
         bounds.xMin = Double.parseDouble(fldXMin.getText());
         bounds.xMax = Double.parseDouble(fldXMax.getText());
         bounds.yMin = Double.parseDouble(fldYMin.getText());
         bounds.yMax = Double.parseDouble(fldYMax.getText());
      }
      panel_.setBounds(bounds);
      panel_.repaint();
   }
   // This is a temporary hack to allow the setting of xlimits manually as the
   // auto identification is buggy. --Prashanth (14th May 2009)
   public void SetXLimits(double xmin, double xmax)
   {
	      GraphData.Bounds bounds = panel_.getGraphBounds();
	      if (fldXMin.getText().length() > 0 && fldYMin.getText().length() > 0 && 
	          fldXMax.getText().length() > 0 && fldYMax.getText().length() > 0 )
	      {      
	         bounds.xMin = xmin;
	         bounds.xMax = xmax;
	         bounds.yMin = Double.parseDouble(fldYMin.getText());
	         bounds.yMax = Double.parseDouble(fldYMax.getText());
	       }
	      panel_.setBounds(bounds);
	      panel_.repaint();
	   
   }

   public GraphFrame() {
      super();
     
      setFont(new Font("Arial", Font.PLAIN, 10));
      
      setTitle("Graph");
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      loadAndRestorePosition(100, 100, 542, 298);
      setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

      panel_ = new GraphPanel();
      panel_.setBorder(new LineBorder(Color.black, 1, false));
      getContentPane().add(panel_);
      springLayout.putConstraint(SpringLayout.SOUTH, panel_, -9, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, panel_, -9, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, panel_, 10, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, panel_, 120, SpringLayout.WEST, getContentPane());

      final JLabel xMinLabel = new JLabel();
      xMinLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      xMinLabel.setText("X Min");
      getContentPane().add(xMinLabel);
      springLayout.putConstraint(SpringLayout.EAST, xMinLabel, 44, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, xMinLabel, 9, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, xMinLabel, 15, SpringLayout.NORTH, getContentPane());

      fldXMin = new JTextField();
      getContentPane().add(fldXMin);
      springLayout.putConstraint(SpringLayout.SOUTH, fldXMin, 32, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, fldXMin, -5, SpringLayout.WEST, panel_);
      springLayout.putConstraint(SpringLayout.NORTH, fldXMin, 15, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, fldXMin, 50, SpringLayout.WEST, getContentPane());

      final JLabel xMinLabel_1 = new JLabel();
      xMinLabel_1.setFont(new Font("Arial", Font.PLAIN, 10));
      xMinLabel_1.setText("X Max");
      getContentPane().add(xMinLabel_1);
      springLayout.putConstraint(SpringLayout.SOUTH, xMinLabel_1, 50, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, xMinLabel_1, 36, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, xMinLabel_1, 49, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, xMinLabel_1, 9, SpringLayout.WEST, getContentPane());

      fldXMax = new JTextField();
      getContentPane().add(fldXMax);
      springLayout.putConstraint(SpringLayout.SOUTH, fldXMax, 53, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, fldXMax, 36, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, fldXMax, 0, SpringLayout.EAST, fldXMin);
      springLayout.putConstraint(SpringLayout.WEST, fldXMax, 50, SpringLayout.WEST, getContentPane());

      final JLabel xMinLabel_2 = new JLabel();
      xMinLabel_2.setFont(new Font("Arial", Font.PLAIN, 10));
      xMinLabel_2.setText("Y Min");
      getContentPane().add(xMinLabel_2);
      springLayout.putConstraint(SpringLayout.SOUTH, xMinLabel_2, 79, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, xMinLabel_2, 65, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, xMinLabel_2, 49, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, xMinLabel_2, 9, SpringLayout.WEST, getContentPane());

      fldYMin = new JTextField();
      getContentPane().add(fldYMin);
      springLayout.putConstraint(SpringLayout.SOUTH, fldYMin, 82, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, fldYMin, 65, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, fldYMin, 0, SpringLayout.EAST, fldXMax);
      springLayout.putConstraint(SpringLayout.WEST, fldYMin, 50, SpringLayout.WEST, getContentPane());

      fldYMax = new JTextField();
      getContentPane().add(fldYMax);
      springLayout.putConstraint(SpringLayout.SOUTH, fldYMax, 103, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, fldYMax, 86, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, fldYMax, 0, SpringLayout.EAST, fldYMin);
      springLayout.putConstraint(SpringLayout.WEST, fldYMax, 50, SpringLayout.WEST, getContentPane());

      final JLabel xMinLabel_1_2 = new JLabel();
      xMinLabel_1_2.setFont(new Font("Arial", Font.PLAIN, 10));
      xMinLabel_1_2.setText("Y Max");
      getContentPane().add(xMinLabel_1_2);
      springLayout.putConstraint(SpringLayout.SOUTH, xMinLabel_1_2, 100, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, xMinLabel_1_2, 86, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, xMinLabel_1_2, 55, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, xMinLabel_1_2, 9, SpringLayout.WEST, getContentPane());

      final JButton btnAutoscale = new JButton();
      btnAutoscale.setFont(new Font("Arial", Font.PLAIN, 10));
      btnAutoscale.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            setAutoScale();
         }
      });
      btnAutoscale.setText("Autoscale");
      getContentPane().add(btnAutoscale);
      springLayout.putConstraint(SpringLayout.EAST, btnAutoscale, 116, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, btnAutoscale, 115, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, btnAutoscale, 25, SpringLayout.WEST, getContentPane());

      final JButton btnRefresh = new JButton();
      btnRefresh.setFont(new Font("Arial", Font.PLAIN, 10));
      btnRefresh.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            MMStudio.getInstance().updateLineProfile();
         }
      });
      btnRefresh.setText("Refresh");
      getContentPane().add(btnRefresh);
      springLayout.putConstraint(SpringLayout.NORTH, btnRefresh, 137, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, btnRefresh, 116, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, btnRefresh, 25, SpringLayout.WEST, getContentPane());
      //
   }

}
