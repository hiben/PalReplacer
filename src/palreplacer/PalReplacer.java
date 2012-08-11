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

import static palreplacer.Util.fileExt;
import static palreplacer.Util.fileFilterGPL;
import static palreplacer.Util.fileFilterImage;
import static palreplacer.Util.fileFilterPal;
import static palreplacer.Util.getExt;
import static palreplacer.Util.getGIMPPalette;
import static palreplacer.Util.getImagePaletteData;
import static palreplacer.Util.getNameNoExt;
import static palreplacer.Util.getRawPaletteData;
import static palreplacer.Util.setACAndText;
import static palreplacer.Util.setToolTip;
import static palreplacer.Util.withKeyStroke;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.MouseInfo;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import palreplacer.Util.PaletteExtractionException;

public class PalReplacer implements Runnable {
	
	public JFrame frame;
	public JMenu fileMenu;
	
	public PaletteDisplay paldisp;
	
	private JFileChooser openpalChooser;
	private JFileChooser savepalChooser;
	private String lastPalName = "PalReplacer";

	private JFileChooser selectOutDirChooser;

	private JFileChooser addFilesChooser;
	
	private JLabel outdirLabel;
	private File outdir;

	private DefaultListModel lm;
	private JList conversionFiles;
	
	private JTextField tfOutFormat;
	private JRadioButton rbOverwriteNo;
	private JRadioButton rbOverwriteAsk;
	private JRadioButton rbOverwriteYes;

	private JCheckBox cbNoDithering;
	
	public static final String acLoadPalette = "loadpalette";
	public static final String acSavePalette = "savepalette";
	public static final String acSelectOutdir = "selectoutdir";

	public static final String acStartConversion = "startconversion";

	public static final String acAddFiles = "addfiles";
	public static final String acRemoveSelected = "removeselected";
	public static final String acClear = "clear";
	public static final String acSelectAll = "selectall";

	public static final String acExit = "exit";
	
	// palette loading dialog
	public void loadPaletteDialog() {
		if(openpalChooser==null) {
			openpalChooser = new JFileChooser();
			openpalChooser.setAcceptAllFileFilterUsed(true);
			openpalChooser.addChoosableFileFilter(fileFilterImage);
			openpalChooser.addChoosableFileFilter(fileFilterGPL);
			openpalChooser.addChoosableFileFilter(fileFilterPal);
			openpalChooser.setAccessory(new JCheckBox("palette-data contains alpha", false));
			openpalChooser.addPropertyChangeListener(new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if(JFileChooser.FILE_FILTER_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
						JComponent acc = openpalChooser.getAccessory();
						if(openpalChooser.getFileFilter() == fileFilterPal) {
							acc.setEnabled(true);
						} else {
							acc.setEnabled(false);
						}
					}
				}
			});
		}
		
		while(openpalChooser.showDialog(frame, "Load palette") == JFileChooser.APPROVE_OPTION) {
			JCheckBox alphaCheck = (JCheckBox)openpalChooser.getAccessory();
			File f = openpalChooser.getSelectedFile();
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(f);
				boolean [] alphaPal = new boolean [] { alphaCheck.isSelected() };
				int [] paldata = null;
				if(openpalChooser.getFileFilter() == fileFilterPal || fileExt(f).equals("pal")) {
					paldata = getRawPaletteData(fis, alphaPal[0]);
				} else {
					if(openpalChooser.getFileFilter() == fileFilterGPL || fileExt(f).equals("gpl")) {
						alphaPal[0] = false;
						paldata = getGIMPPalette(fis);
					} else {
						BufferedImage bi = ImageIO.read(fis);
						if(bi == null)
							throw new PaletteExtractionException("Unable to read image!");
						paldata = getImagePaletteData(bi, alphaPal);
					}
				}
				assert(paldata!=null);
				paldisp.setPaletteEntries(paldata, alphaPal[0]);
			} catch(PaletteExtractionException pee) {
				JOptionPane.showMessageDialog(frame, "Could not extract palette:\n"+pee.getMessage(), "Palette-Error", JOptionPane.ERROR_MESSAGE);
				continue;
			} catch(IOException ioe) {
				JOptionPane.showMessageDialog(frame, "There was an error opening/reading the file:\n"+ioe.getMessage(), "IO-Error", JOptionPane.ERROR_MESSAGE);
				// break after error to give user time to think...
			} finally {
				if(fis!=null)
					try {
						fis.close();
					} catch (IOException e) {
					}
			}
			
			break;
		}
	}
	
	// palette saving dialog
	public void savePaletteDialog() {
		if(savepalChooser==null) {
			savepalChooser = new JFileChooser();
			savepalChooser.setAcceptAllFileFilterUsed(false);
			savepalChooser.addChoosableFileFilter(fileFilterPal);
			savepalChooser.addChoosableFileFilter(fileFilterGPL);
			savepalChooser.setAccessory(new JCheckBox("save alpha (ARGB)", false));
			savepalChooser.getAccessory().setEnabled(false);
			savepalChooser.addPropertyChangeListener(new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if(JFileChooser.FILE_FILTER_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
						JComponent acc = savepalChooser.getAccessory();
						if(savepalChooser.getFileFilter() == fileFilterPal) {
							acc.setEnabled(true);
						} else {
							acc.setEnabled(false);
						}
					}
				}
			});
		}
		
		while(savepalChooser.showDialog(frame, "Save palette") == JFileChooser.APPROVE_OPTION) {
			File f = savepalChooser.getSelectedFile();
			
			if(f.exists()) {
				int response;
				response = JOptionPane.showConfirmDialog(frame, "File '" + f.getName() + "' exists.\nOverwrite ?", "File exists...", JOptionPane.YES_NO_CANCEL_OPTION);
				if(response==JOptionPane.CANCEL_OPTION)
					break;
				if(response==JOptionPane.NO_OPTION)
					continue;
			}
			
			boolean writeGPL = savepalChooser.getFileFilter() == fileFilterGPL || fileExt(f).equals("gpl");
			
			if(writeGPL) {
				String newName = JOptionPane.showInputDialog(frame, "Palette name", lastPalName);
				if(newName == null)
					break;
				lastPalName = newName;
			}
			
			JCheckBox alphaCheck = (JCheckBox)savepalChooser.getAccessory();
			
			try {
				FileOutputStream fos = new FileOutputStream(f);
				
				boolean saveAlpha = paldisp.hasAlpha() && alphaCheck.isSelected();
				int [] rgbdata = paldisp.getEntries();
				
				if(writeGPL) {
					Util.saveGIMPPalette(rgbdata, lastPalName, fos);
				} else {
					Util.saveRawPalette(rgbdata, saveAlpha, fos);
				}
				fos.close();
			} catch (IOException e) {
				JOptionPane.showMessageDialog(frame, "There was an error while writing palette data!\n" + e.getMessage(), "IO-Error", JOptionPane.ERROR_MESSAGE);
			}
			
			break;
		}
	}
	
	// output directory selection
	public void selectOutDirDialog() {
		if(selectOutDirChooser==null) {
			selectOutDirChooser = new JFileChooser();
			selectOutDirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		}

		if(selectOutDirChooser.showDialog(frame, "Select output directory") == JFileChooser.APPROVE_OPTION) {
			File od = selectOutDirChooser.getSelectedFile();
			try {
				outdirLabel.setText(od.getCanonicalPath());
				outdirLabel.setToolTipText(outdirLabel.getText());
				outdir = od;
			} catch (IOException e) {
				JOptionPane.showMessageDialog(frame, "There was an error using this directory!\n" + e.getMessage(), "IO-Error", JOptionPane.ERROR_MESSAGE);
			}
		}

	}
	
	// key object for storing property
	private static final String dConvThread = "convthread";
	
	// conversion cancel action
	private AbstractAction cancelAction = new AbstractAction("Cancel") {
		private static final long serialVersionUID = 1L;

		public void actionPerformed(ActionEvent ae) {
			JButton source = (JButton)ae.getSource();
			ConversionThread ct = (ConversionThread)source.getClientProperty(dConvThread);
			ct.stopConversion = true;
		}
	};
	
	private static class DialogSwitch extends Thread {
		private JDialog d;
		
		public DialogSwitch(JDialog d) {
			this.d = d;
		}
		
		public void run() {
			d.setVisible(true);
		}
	}

	// this thread handles the actual file conversion
	// on start, it sets the cancel-dialog visible and blocks the application UI
	private class ConversionThread extends Thread {
		private JDialog dialog;
		private JProgressBar progress;
		private boolean stopConversion = false;
		
		public ConversionThread(JDialog dialog, JProgressBar progressBar) {
			this.dialog = dialog;
			this.progress = progressBar;
		}
		
		public void run() {
			new DialogSwitch(dialog).start();
			yield();
			
			boolean hadErrors = false;
			
			boolean neverOverwrite = rbOverwriteNo.isSelected();
			boolean overwriteAsk = rbOverwriteAsk.isSelected();
			
			boolean noDither = cbNoDithering.isSelected();
			
			synchronized(lm) {
				int [] rgbdata = paldisp.getEntries();
				int bits = (int)Math.ceil( Math.log(rgbdata.length) / Math.log(2) );
				int type = bits > 8 ? DataBuffer.TYPE_USHORT : DataBuffer.TYPE_BYTE;
				IndexColorModel icm = new IndexColorModel(bits, rgbdata.length, rgbdata, 0, paldisp.hasAlpha(), -1, type);
				int size = lm.getSize();
				for(int file_index=0; file_index<size; file_index++) {
					String s = (String)lm.get(file_index);
					File f = new File(s);
					
					try {
						if(stopConversion)
							break;
						String fname = f.getName();
						String ext = getExt(fname);
						String name = getNameNoExt(fname);

						String out =  String.format(tfOutFormat.getText(), name, ext);
						if(out.length() == 0) {
							out = fname;
						}

						File outFile = new File(outdir, out);

						boolean doWrite = true;

						if(neverOverwrite || overwriteAsk) {
							if(outFile.exists()) {
								if(neverOverwrite) {
									doWrite = false;
								} else {
									doWrite = false;
									JCheckBox overrideAsk = new JCheckBox("Ask again", true);
									int res = JOptionPane.showConfirmDialog(frame, new Object [] { "File '" + out + "' exist. Overwrite ?", overrideAsk }, "File exists...", JOptionPane.YES_NO_CANCEL_OPTION);
									if(res == JOptionPane.CANCEL_OPTION) {
										stopConversion = true;
									}
									if(res == JOptionPane.YES_OPTION) {
										doWrite = true;
									}
									if(!overrideAsk.isSelected()) {
										overwriteAsk = false;
										neverOverwrite = !doWrite;	
									}
								}
							}
						}
						if(doWrite) {
							BufferedImage bi = ImageIO.read(f);
							if(bi!=null) {
								BufferedImage target = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, icm);
								
								if(noDither) {
									ColorModel cm = bi.getColorModel();
									int w = bi.getWidth();
									int h = bi.getHeight();
									
									if(bi.getType() == BufferedImage.TYPE_BYTE_INDEXED && cm instanceof IndexColorModel) {
										// converting byte index
										IndexColorModel bi_icm = (IndexColorModel)cm;
										
										byte [] indices = (byte [])bi.getRaster().getDataElements(0, 0, w, h, null);
										
										for(int i=0; i<indices.length; i++) {
											int bi_color = bi_icm.getRGB(indices[i]);
											int best_index = Util.getBestColorIndex(rgbdata, indices[i], bi_color);
											indices[i] = (byte)best_index;
										}
										
										target.getRaster().setDataElements(0, 0, w, h, indices);																				
									} else {
										// converting by rgb
										int [] rgb_line = new int [w];
										byte [] indices = new byte [w];
										
										for(int j=0; j<h; j++) {
											bi.getRGB(0, j, w, 1, rgb_line, 0, w);
											
											for(int i=0; i<w; i++) {
												indices[i] = (byte)Util.getBestColorIndex(rgbdata, -1, rgb_line[i]);
											}
											
											target.getRaster().setDataElements(0, j, w, 1, indices);
										}
									}
								} else {
									Graphics2D g2d = target.createGraphics();
									g2d.drawImage(bi, null, 0, 0);
								}

								String outExt = getExt(out);
								if(outExt.length() == 0)
									outExt = ext;
								if(outExt.length() == 0)
									outExt = "png";

								ImageIO.write(target, outExt, outFile);
							} else {
								hadErrors = true;
							}
						}
						if(stopConversion)
							break;
					} catch (IOException e) {
						hadErrors = true;
					}
					
					progress.setValue(file_index);
				}
			}
			
			dialog.setVisible(false);
			
			if(!stopConversion && hadErrors) {
				JOptionPane.showMessageDialog(frame, "There were some errors while converting...", "Errors...", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	
	// starts the conversion with a thread and blocks with a dialog
	private void convert() {
		int size = lm.getSize();
		if(size==0)
			return;
		
		JDialog dialog = new JDialog(frame, "Conversion...");
		dialog.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		dialog.setLocationByPlatform(true);
		dialog.setLocationRelativeTo(frame);
		dialog.setModal(true);
		JButton cancel = new JButton(cancelAction);
		
		JPanel p = new JPanel();
		p.setLayout(new BorderLayout());
		JProgressBar progress = new JProgressBar(0, size);
		ConversionThread ct = new ConversionThread(dialog, progress);
		cancel.putClientProperty(dConvThread, ct);
		
		p.add(progress, BorderLayout.CENTER);
		
		JPanel bp = new JPanel();
		bp.add(cancel);
		p.add(bp, BorderLayout.SOUTH);
		
		dialog.setContentPane(p);
		dialog.pack();
		
		ct.start();
	}
	
	// actions for the file-menu
	private AbstractAction fileAction = new AbstractAction() {
		private static final long serialVersionUID = 1L;

		public void actionPerformed(ActionEvent ae) {
			if(acLoadPalette.equals(ae.getActionCommand())) {
				loadPaletteDialog();
			}
			if(acSavePalette.equals(ae.getActionCommand())) {
				savePaletteDialog();
			}
			if(acSelectOutdir.equals(ae.getActionCommand())) {
				selectOutDirDialog();
			}
			if(acStartConversion.equals(ae.getActionCommand())) {
				convert();
			} 
			if(acExit.equals(ae.getActionCommand())) {
				frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
			}
		}
	};
	
	// dialog for adding files to the list
	private void addFilesDialog() {
		if(addFilesChooser==null) {
			addFilesChooser = new JFileChooser();
			addFilesChooser.setAcceptAllFileFilterUsed(true);
			addFilesChooser.addChoosableFileFilter(fileFilterImage);
			addFilesChooser.setMultiSelectionEnabled(true);
		}
		
		if(addFilesChooser.showDialog(frame, "Add files for conversion...") == JFileChooser.APPROVE_OPTION) {
			boolean hadErrors = false;
			for(File f : addFilesChooser.getSelectedFiles()) {
				try {
					String path = f.getCanonicalPath();
					
					if(!lm.contains(path))
						lm.addElement(path);
					
				} catch (IOException e) {
					hadErrors = true;
				}
			}
			
			if(hadErrors) {
				JOptionPane.showMessageDialog(frame, "Some files could not be added to the list...", "Errors", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	// actions on the file list
	private AbstractAction fileListAction = new AbstractAction() {
		private static final long serialVersionUID = 1L;

		public void actionPerformed(ActionEvent ae) {
			if(acClear.equals(ae.getActionCommand())) {
				lm.clear();
			}
			if(acSelectAll.equals(ae.getActionCommand())) {
				int size = lm.getSize();
				if(size>0)
					conversionFiles.getSelectionModel().setSelectionInterval(0, size-1);
			}
			if(acRemoveSelected.equals(ae.getActionCommand())) {
				int [] seli = conversionFiles.getSelectedIndices();
				for(int i=seli.length-1; i>=0; i--) {
					lm.remove(seli[i]);
				}
			}
			if(acAddFiles.equals(ae.getActionCommand())) {
				addFilesDialog();
			}
		}
	};
	
	private WindowAdapter windowAdapter = new WindowAdapter() {
		
		@Override
		public void windowClosing(WindowEvent we) {
			frame.dispose();
		}
	};
	
	private DropTarget fileDropTarget = new DropTarget() {
		private static final long serialVersionUID = 1L;

		@Override
		public synchronized void drop(DropTargetDropEvent dtde) {
			try {
				DataFlavor df = null;
				
				for(DataFlavor adf : dtde.getCurrentDataFlavors()) {
					if(adf.getPrimaryType().toLowerCase().equals("text") && adf.getSubType().toLowerCase().equals("uri-list") && adf.getDefaultRepresentationClassAsString().equals("java.io.InputStream")) {
						df = adf;
						break;
					}
				}
				
				if(df==null)
					df = new DataFlavor("text/uri-list;class=java.io.InputStream");
				
				if(dtde.isDataFlavorSupported(df)) {
					dtde.acceptDrop(DnDConstants.ACTION_COPY);
					
					Transferable t = dtde.getTransferable();
					Object o = t.getTransferData(df);
					
					Reader r = null;
					if(o instanceof Reader) {
						r = (Reader)o;
					}
					if(o instanceof InputStream) {
						r = new InputStreamReader((InputStream)o);
					}
					
					if(r!=null) {
						BufferedReader br = new BufferedReader(r);
						
						String line;
						while((line = br.readLine())!=null) {
							if(!lm.contains(line))
									lm.addElement(line);
						}
					} else {
					}
				} else {
				}
				
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (UnsupportedFlavorException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	};
	
	public void run() {
		// create frame
		GraphicsDevice here = MouseInfo.getPointerInfo().getDevice();
		frame = new JFrame("PalReplacer", here.getDefaultConfiguration());
		frame.setLocationByPlatform(true);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(windowAdapter);
		
		// create MenuBar
		JMenuBar mbar;
		frame.setJMenuBar(mbar = new JMenuBar());
		
		fileMenu = new JMenu("File");
		fileMenu.add(withKeyStroke(setACAndText(new JMenuItem(fileAction), acLoadPalette, "Load Palette...", 'P'), KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_MASK)));
		fileMenu.add(withKeyStroke(setACAndText(new JMenuItem(fileAction), acSavePalette, "Save Palette...", 'S'), KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_MASK)));
		fileMenu.add(withKeyStroke(setACAndText(new JMenuItem(fileListAction), acAddFiles, "Add Files...", 'F'), KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_MASK)));
		fileMenu.add(withKeyStroke(setACAndText(new JMenuItem(fileAction), acSelectOutdir, "Select output directory...", 'O'), KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_MASK)));
		fileMenu.add(withKeyStroke(setACAndText(new JMenuItem(fileAction), acStartConversion, "Start conversion", 'C'), KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_MASK)));
		fileMenu.addSeparator();
		fileMenu.add(withKeyStroke(setACAndText(new JMenuItem(fileAction), acExit, "Exit", 'X'), KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_MASK)));;

		mbar.add(fileMenu);

		// layout objects
		GridBagLayout gbl = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();

		gbc.insets.right = 5;
		gbc.insets.bottom = 2;

		// panels on the right side (palette, file actions)
		JPanel eastPanel = new JPanel();
		eastPanel.setLayout(gbl);
		
		// palette display
		JPanel palettePanel = new JPanel();
		palettePanel.setLayout(new BorderLayout());
		palettePanel.setBorder(BorderFactory.createTitledBorder("Palette"));
		palettePanel.add(paldisp = new PaletteDisplay());
		
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0.0;
		gbc.weighty = 0.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.PAGE_START;
		gbl.setConstraints(palettePanel, gbc);
		eastPanel.add(palettePanel);

		// palette buttons
		JPanel paletteBtnPanel = new JPanel();
		paletteBtnPanel.setLayout(new GridLayout(0, 1));
		paletteBtnPanel.setBorder(BorderFactory.createTitledBorder("Palette-Ops"));
		paletteBtnPanel.add(setACAndText(new JButton(fileAction), acLoadPalette, "Load..."));
		paletteBtnPanel.add(setACAndText(new JButton(fileAction), acSavePalette, "Save..."));
		paletteBtnPanel.add(setToolTip(cbNoDithering = new JCheckBox("no dithering", true), "process each pixel individually"));
		
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.weightx = 0.0;
		gbc.weighty = 0.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.PAGE_START;
		gbl.setConstraints(paletteBtnPanel, gbc);
		eastPanel.add(paletteBtnPanel);
		
		// file actions
		JPanel fileButtonPanel = new JPanel();
		fileButtonPanel.setLayout(new GridLayout(0, 1));
		fileButtonPanel.setBorder(BorderFactory.createTitledBorder("File Actions"));
		fileButtonPanel.add(setACAndText(new JButton(fileListAction), acAddFiles, "Add Files..."));
		fileButtonPanel.add(setACAndText(new JButton(fileListAction), acClear, "Clear List"));
		fileButtonPanel.add(setACAndText(new JButton(fileListAction), acSelectAll, "Select All"));
		fileButtonPanel.add(setACAndText(new JButton(fileListAction), acRemoveSelected, "Remove Selected"));
		fileButtonPanel.add(setACAndText(new JButton(fileAction), acStartConversion, "Start Conversion"));
		
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.weightx = 0.0;
		gbc.weighty = 0.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.PAGE_START;
		gbl.setConstraints(fileButtonPanel, gbc);
		eastPanel.add(fileButtonPanel);

		// glue for nice layout
		Component glue = Box.createGlue();

		gbc.gridx = 0;
		gbc.gridy = 3;
		gbc.weightx = 0.0;
		gbc.weighty = 1.0;
		gbc.fill = GridBagConstraints.VERTICAL;
		gbc.anchor = GridBagConstraints.PAGE_START;
		
		gbl.setConstraints(glue, gbc);
		eastPanel.add(glue);

		// lile list in the center
		JPanel filesPanel = new JPanel();
		filesPanel.setBorder(BorderFactory.createTitledBorder("Files for conversion"));
		filesPanel.setLayout(new BorderLayout());
		
		lm = new DefaultListModel();
		filesPanel.add(new JScrollPane(conversionFiles = new JList(lm)));
		
		// support dragging files into the list
		conversionFiles.setDropTarget(fileDropTarget);
		
		// output options at the bottom
		gbl = new GridBagLayout();
		gbc = new GridBagConstraints();
		
		gbc.insets.right = 5;
		gbc.insets.bottom = 2;
		
		JPanel outoptsPanel = new JPanel();
		outoptsPanel.setBorder(BorderFactory.createTitledBorder("Output Options"));
		outoptsPanel.setLayout(gbl);
		
		// output directory (default to home)
		String userHome = System.getProperty("user.home", ".");
		outdir = new File(userHome);
		try {
			outdirLabel = new JLabel(outdir.getCanonicalPath());
		} catch (IOException e) {
			outdirLabel = new JLabel(".");
		}
		outdirLabel.setToolTipText(outdirLabel.getText());
		
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0.0;
		gbc.weighty = 0.0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.LINE_START;
		JLabel odl = new JLabel("Directory:");
		gbl.setConstraints(odl, gbc);
		outoptsPanel.add(odl);
		
		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.LINE_START;

		gbl.setConstraints(outdirLabel, gbc);
		outoptsPanel.add(outdirLabel);

		JButton selectOutDirButton = new JButton(fileAction);
		gbc.gridx = 2;
		gbc.gridy = 0;
		gbc.weightx = 0.0;
		gbc.weighty = 0.0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.LINE_END;
		
		gbl.setConstraints(selectOutDirButton, gbc);
		outoptsPanel.add(setToolTip(setACAndText(selectOutDirButton, acSelectOutdir, "Choose"), "Choose directory for file output"));

		// output format
		JLabel outFormatLabel = new JLabel("Output Format:");
		
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.weightx = 0.0;
		gbc.weighty = 0.0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.LINE_START;
		
		gbl.setConstraints(outFormatLabel, gbc);
		outoptsPanel.add(outFormatLabel);
		
		tfOutFormat = new JTextField("%s.%s");
		tfOutFormat.setToolTipText("Specify a scheme for file output. First parameter is file name, second is file extension (without '.')");

		gbc.gridx = 1;
		gbc.gridy = 1;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.LINE_START;

		gbl.setConstraints(tfOutFormat, gbc);
		outoptsPanel.add(tfOutFormat);

		// overwrite settings
		JLabel overwriteLabel = new JLabel("Overwrite:");
		
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.weightx = 0.0;
		gbc.weighty = 0.0;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.LINE_START;
		gbl.setConstraints(overwriteLabel, gbc);
		outoptsPanel.add(overwriteLabel);

		ButtonGroup bg = new ButtonGroup();
		
		rbOverwriteAsk = new JRadioButton("Ask", true);
		rbOverwriteAsk.setToolTipText("Ask before overwriting an existing file");
		
		rbOverwriteNo = new JRadioButton("No", false);
		rbOverwriteNo.setToolTipText("Do not overwrite an existing file");

		rbOverwriteYes = new JRadioButton("Yes", false);
		rbOverwriteYes.setToolTipText("Overwriting an existing file without asking");
		
		bg.add(rbOverwriteAsk);
		bg.add(rbOverwriteNo);
		bg.add(rbOverwriteYes);
		
		JPanel buttonBag = new JPanel();
		
		buttonBag.add(rbOverwriteNo);
		buttonBag.add(rbOverwriteAsk);
		buttonBag.add(rbOverwriteYes);
		
		gbc.gridx = 1;
		gbc.gridy = 2;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.NONE;
		gbc.anchor = GridBagConstraints.LINE_START;
		
		gbl.setConstraints(buttonBag, gbc);
		outoptsPanel.add(buttonBag);
		
		// Add containers to frame
		frame.add(eastPanel, BorderLayout.EAST);
		frame.add(filesPanel, BorderLayout.CENTER);
		frame.add(outoptsPanel, BorderLayout.SOUTH);
		frame.pack();
		frame.setVisible(true);
	}

	public static void main(String...args) {
		EventQueue.invokeLater(new PalReplacer());
	}
}
