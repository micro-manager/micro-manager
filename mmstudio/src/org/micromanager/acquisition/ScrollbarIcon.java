/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.awt.BasicStroke;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.GeneralPath;

/**
 *
 * @author Henry
 */
public class ScrollbarIcon extends Canvas implements MouseListener {
		private static final int WIDTH = 24, HEIGHT=14;
		private BasicStroke stroke = new BasicStroke(2f);
		private char type;
      private VirtualAcquisitionDisplay virtAcq_;

		public ScrollbarIcon(char type, VirtualAcquisitionDisplay vad) {
			virtAcq_ = vad;
         addMouseListener(this);
			setSize(WIDTH, HEIGHT);
			this.type = type;
		}
		
		/** Overrides Component getPreferredSize(). */
		public Dimension getPreferredSize() {
			return new Dimension(WIDTH, HEIGHT);
		}
		
		public void paint(Graphics g) {
			g.setColor(Color.white);
			g.fillRect(0, 0, WIDTH, HEIGHT);
			Graphics2D g2d = (Graphics2D)g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         if (type == 'z' || type == 't') {
			drawPlayPauseButton(g2d);
			drawLetter(g);
         } else {
            drawCenteredLetter(g);
         }
		}
      
      private void drawCenteredLetter(Graphics g) {
			g.setFont(new Font("SansSerif", Font.PLAIN, 14));
			g.setColor(Color.black);
			g.drawString(String.valueOf(type), 8, 12);
		}
		
		private void drawLetter(Graphics g) {
			g.setFont(new Font("SansSerif", Font.PLAIN, 14));
			g.setColor(Color.black);
			g.drawString(String.valueOf(type), 4, 12);
		}

		private void drawPlayPauseButton(Graphics2D g) {
			if ( (type == 't' && virtAcq_.isTAnimated()) ||
                 (type == 'z' && virtAcq_.isZAnimated())) { //draw pause
				g.setColor(Color.red);
				g.setStroke(stroke);
				g.drawLine(15, 3, 15, 11);
				g.drawLine(20, 3, 20, 11);
			} else { //draw play
				g.setColor(new Color(0,150,0));
				GeneralPath path = new GeneralPath();
				path.moveTo(15f, 2f);
				path.lineTo(22f, 7f);
				path.lineTo(15f, 12f);
				path.lineTo(15f, 2f);
				g.fill(path);
			}
		}
		
		public void mousePressed(MouseEvent e) {
			
		}
		
		public void mouseReleased(MouseEvent e) {}
		public void mouseExited(MouseEvent e) {}
		public void mouseClicked(MouseEvent e) {}
		public void mouseEntered(MouseEvent e) {}
	
	}

