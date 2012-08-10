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

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileFilter;

public class Util {
	public static String getExt(String filename) {
		int idx = filename.lastIndexOf('.');
		if(idx<=0)
			return "";
		return filename.substring(idx+1);
	}
	
	public static String getNameNoExt(String filename) {
		int idx = filename.lastIndexOf('.');
		if(idx<=0)
			return filename;
		
		return filename.substring(0, idx);
	}
	
	public static String fileExt(File f) {
		return getExt(f.getName()).toLowerCase();
	}
	
	public static FileFilter fileFilterImage = new FileFilter() {
		@Override
		public String getDescription() {
			return "Supported Images (.PNG,.JPG,.GIF,...)";
		}
		
		@Override
		public boolean accept(File f) {
			if(f.isDirectory())
				return true;
			String ext = fileExt(f);

			if(ext.equals("jpg"))
				return true;
			if(ext.equals("jpeg"))
				return true;
			if(ext.equals("png"))
				return true;
			if(ext.equals("gif"))
				return true;
			if(ext.equals("bmp"))
				return true;
			
			return false;
		}
	};
	
	public static FileFilter fileFilterPal = new FileFilter() {
		@Override
		public String getDescription() {
			return "Raw Palette (.PAL)";
		}
		
		@Override
		public boolean accept(File f) {
			if(f.isDirectory())
				return true;
			String ext = fileExt(f);

			if(ext.equals("pal"))
				return true;
			
			return false;
		}
	};

	public static FileFilter fileFilterGPL = new FileFilter() {
		@Override
		public String getDescription() {
			return "GIMP Palette (.GPL)";
		}
		
		@Override
		public boolean accept(File f) {
			if(f.isDirectory())
				return true;
			String ext = fileExt(f);

			if(ext.equals("gpl"))
				return true;
			
			return false;
		}
	};

	public static class PaletteExtractionException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		
		public PaletteExtractionException(String msg) {
			super(msg);
		}
	};

	public static int [] getRawPaletteData(InputStream is, boolean withAlpha) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte [] buffer = new byte [1024];
		int r;
		int read = 0;
		
		while( (r = is.read(buffer)) > 0) {
			baos.write(buffer, 0, r);
			read += r;
			
			if(read > 1024)
				throw new PaletteExtractionException("File contains more data than expected!");
		}
		
		byte [] data = baos.toByteArray();
		
		if( (data.length % (withAlpha ? 4 : 3)) != 0 ) {
			throw new PaletteExtractionException("File can't be used for palette!");
		}

		int entries = data.length / (withAlpha ? 4 : 3);
		
		int [] rawdata = new int [entries];
		
		int bindex = 0;
		for(int i=0; i<entries; i++) {
			int alpha = withAlpha ? (((int)data[bindex++]) & 0xFF) : 0xFF;
			int red = ((int)data[bindex++]) & 0xFF;
			int green = ((int)data[bindex++]) & 0xFF;
			int blue = ((int)data[bindex++]) & 0xFF;
			
			rawdata[i] = (alpha << 24) | (red << 16) | (green << 8) | blue;
		}
		
		return rawdata;
	}
	
	public static int [] getImagePaletteData(BufferedImage bi, boolean [] hasAlpha) {
		ColorModel cm = bi.getColorModel();
		
		if(hasAlpha!=null && hasAlpha.length > 0)
			hasAlpha[0] = cm.hasAlpha();
		
		if(cm instanceof IndexColorModel) {
			IndexColorModel icm = (IndexColorModel)cm;
			int msize = icm.getMapSize();
			
			int [] paldata = new int [msize];
			icm.getRGBs(paldata);
			
			return paldata;
		}

		throw new PaletteExtractionException("Image does not contain palette information!");
	}
	
	public static int [] getGIMPPalette(InputStream is) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		
		String line;
		int state = 0;
		
		ArrayList<Integer> colorList = new ArrayList<Integer>();
		Pattern paletteLine = Pattern.compile("(\\d+)\\s+(\\d+)\\s+(\\d+).*");
		
		while( (line = br.readLine()) != null) {
			line = line.trim().toLowerCase();
			if(line.startsWith("#")) {
				if(state==1)
					state = 2;
				
				continue;
			}
			
			switch(state) {
			case 0:
				if(!line.equals("gimp palette"))
					throw new PaletteExtractionException("Not a valid GIMP Palette");
				state = 1;
				break;
			case 1:
				break;
			default:
				Matcher m = paletteLine.matcher(line);
				if(!m.matches())
					throw new PaletteExtractionException("Not a valid GIMP Palette");
				String rs = m.group(1);
				String gs = m.group(2);
				String bs = m.group(3);
				try {
					int ri = Integer.parseInt(rs);
					int gi = Integer.parseInt(gs);
					int bi = Integer.parseInt(bs);

					for(int ci : new int [] {ri, gi, bi}) {
						if(ci < 0 || ci > 255)
							throw new PaletteExtractionException("Not a valid GIMP Palette");
					}
					
					int rgb = (ri << 16) | (gi << 8) | bi;
					
					colorList.add(rgb);
				} catch(NumberFormatException nfe) {
					throw new PaletteExtractionException("Not a valid GIMP Palette");
				}
			}
		}
		
		if(state != 2)
			throw new PaletteExtractionException("Not a valid GIMP Palette");
		
		if(colorList.size()<2)
			throw new PaletteExtractionException("Palette contains too few colors (" + colorList.size() + ")");
		
		int [] colors = new int [colorList.size()];
		
		for(int i=0; i<colorList.size(); i++)
			colors[i] = colorList.get(i);
		
		return colors;
	}
	
	public static void saveGIMPPalette(int [] rgb, String name, OutputStream os) throws IOException {
		PrintStream ps = new PrintStream(os);
		
		ps.println("GIMP Palette");
		ps.format("Name: %s\n", name);
		ps.println("#");
		
		for(int color : rgb) {
			int r = (color >> 16) & 0xFF;
			int g = (color >>  8) & 0xFF;
			int b = (color >>  0) & 0xFF;
			ps.format("%d %d %d\n", r, g, b);
		}
		
		ps.flush();
	}
	
	public static void saveRawPalette(int [] rgb, boolean hasAlpha, OutputStream os) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		for(int color : rgb) {
			int a = (color >> 24) & 0xFF;
			int r = (color >> 16) & 0xFF;
			int g = (color >>  8) & 0xFF;
			int b = (color >>  0) & 0xFF;

			if(hasAlpha)
				baos.write(a);
			baos.write(r);
			baos.write(g);
			baos.write(b);
		}

		byte [] outdata = baos.toByteArray();
		os.write(outdata);
	}
	
	public static <B extends AbstractButton> B setACAndText(B btn, String actionCommand, String text, Character mnemonic) {
		btn.setActionCommand(actionCommand);
		btn.setText(text);
		if(mnemonic!=null)
			btn.setMnemonic(mnemonic);
		return btn;
	}

	public static <B extends AbstractButton> B setACAndText(B btn, String actionCommand, String text) {
		return setACAndText(btn, actionCommand, text, null);
	}
	
	public static <B extends AbstractButton> B setSelected(B btn, boolean selected) {
		btn.setSelected(selected);
		return btn;
	}
	
	public static <M extends JMenuItem> M withKeyStroke(M jmi, KeyStroke ks) {
		jmi.setAccelerator(ks);
		return jmi;
	}
	
	public static <C extends JComponent> C setToolTip(C c, String text) {
		c.setToolTipText(text);
		return c;
	}
}
