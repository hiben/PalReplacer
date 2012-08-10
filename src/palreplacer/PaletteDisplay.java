/*
PalReplacer - Copyright (c) 2012 Hendrik Iben - hendrik [dot] iben <at> googlemail [dot] com

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package palreplacer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;

public class PaletteDisplay extends JPanel {
	private static final long serialVersionUID = 1L;
	
	private boolean hasAlpha;
	private int [] paletteEntries;
	private Color [] paletteColors;
	
	private int rSize;
	
	private Object drawLock = new Object();
	
	public PaletteDisplay() {
		setPaletteEntries(new int [] { 0x000000, 0xFFFFFF }, false);
	}
	
	public void setPaletteEntries(int [] entries, boolean hasAlpha) {
		if(entries == null || entries.length < 2)
			throw new RuntimeException();
		
		synchronized (drawLock) {
			paletteEntries = entries;
			this.hasAlpha = hasAlpha;
			
			calculateColors();
			
			rSize = (int)Math.ceil(Math.sqrt(paletteEntries.length));
		}
		
		repaint();
	}
	
	private void calculateColors() {
		paletteColors = new Color [paletteEntries.length];
		
		for(int i=0; i<paletteEntries.length; i++)
			paletteColors[i] = new Color(paletteEntries[i], hasAlpha);
	}
	
	public int [] getEntries() {
		return paletteEntries;
	}
	
	public boolean hasAlpha() {
		return hasAlpha;
	}
	
	@Override
	public Dimension getMinimumSize() {
		synchronized (drawLock) {
			return new Dimension(2*rSize+1, 2*rSize+1);
		}
	}
	
	@Override
	public Dimension getPreferredSize() {
		return new Dimension(64, 64);
	}
	
	
	@Override
	public void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D)g;
		int w = getWidth();
		int h = getHeight();
		g.setColor(getBackground());
		g.fillRect(0,0,w,h);
		synchronized (drawLock) {
			int hindex = 0;
			int vindex = 0;
			Rectangle2D.Double rect = new Rectangle2D.Double();
			int hgoal = rSize;
			if(rSize == 1)
				hgoal = 2;
			rect.width = (double)w / (double)hgoal;
			rect.height = (double)h / (double)rSize;
			for(Color c : paletteColors) {
				rect.x = (hindex * w) / hgoal; 
				rect.y = (vindex * h) / rSize;
				g2d.setColor(c);
				g2d.fill(rect);
				g2d.setColor(Color.black);
				g2d.draw(rect);
				hindex++;
				if(hindex>=hgoal) {
					hindex = 0;
					vindex++;
				}
			}
		}
	}
	
	
}