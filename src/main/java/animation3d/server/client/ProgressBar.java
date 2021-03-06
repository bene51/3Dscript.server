package animation3d.server.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.RenderingHints;

import animation3d.renderer3d.Progress;
import ij.gui.GenericDialog;

public class ProgressBar extends ij.gui.ProgressBar implements Progress {

	private String state = "";

	public ProgressBar() {
		super(400, 30);
	}

	public void setState(String state) {
		this.state = state;
		repaint();
	}

	@Override
	public void show(double progress) {
		super.show(progress);
		repaint();
	}

	// Progress interface
	@Override
	public void setProgress(double progress) {
		show(progress);
	}

	@Override
	public void paint(Graphics g) {
		super.paint(g);
		Graphics2D g2d = (Graphics2D)g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
		Font font = g.getFont();
		g.setFont(new Font(font.getName(), Font.BOLD, 14));
		Color color = new Color(0, 0, 40);
		g.setColor(color);
		g.drawString(state, 5, 21);
	}

	public static void main(String[] args) {
		GenericDialog progressGD = new GenericDialog("");
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		Panel p = new Panel(gridbag);
		c.insets = new Insets(5, 5, 5, 5);
		c.anchor = GridBagConstraints.WEST;
		c.gridy = 0;

		ProgressBar pb1 = new ProgressBar();
		ProgressBar pb2 = new ProgressBar();
		pb1.setState("bla bla bla");
		pb1.show(0, 10);
		pb2.setState("blubb");
		pb2.show(7, 10);

		c.gridx = 0;
		p.add(new Label("localhost"), c);
		c.gridx++;
		p.add(pb1, c);
		c.gridy++;

		c.gridx = 0;
		p.add(new Label("10.210.16.16"), c);
		c.gridx++;
		p.add(pb2, c);
		c.gridy++;


		progressGD.addPanel(p);
		progressGD.setModal(false);
		progressGD.showDialog();
		progressGD.getButtons()[1].setVisible(false);
	}
}
