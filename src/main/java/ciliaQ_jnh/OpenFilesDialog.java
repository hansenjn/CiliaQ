package ciliaQ_jnh;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.LinkedList;
import java.util.Stack;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

import ij.gui.GenericDialog;

/**
 * This class wraps the functionality to select multiple files from the system
 * by a gui. It extends the OpenFilesDialog ("Multi-Taks-Manager") by
 * adding an function to select files containing certain pattern (regex or
 * non-regex) in all subdirectories of a chosen root directory.
 * 
 * Parts of the code were inherited from MotiQ (https://github.com/hansenjn/MotiQ).
 * 
 * @author Jan Niklas Hansen and Sebastian Rassmann *
 */
public class OpenFilesDialog extends javax.swing.JFrame implements ActionListener {
	LinkedList<File> filesToOpen = new LinkedList<File>();
	boolean done = false, dirsaved = false;
	File saved;// = new File(getClass().getResource(".").getFile());
	JMenuBar jMenuBar1;
	JMenu jMenu3, jMenu5;
	JSeparator jSeparator2;
	JPanel bgPanel;
	JScrollPane jScrollPane1;
	JList Liste1;
	JButton loadSingleFilesButton, loadByPatternButtom, removeFileButton, goButton;

	public OpenFilesDialog() {
		super();
		System.out.println("Here");
		initGUI();
	}
	

	private void initGUI() {
		int prefXSize = 600, prefYSize = 400;
		this.setMinimumSize(new java.awt.Dimension(prefXSize, prefYSize + 40));
		this.setSize(prefXSize, prefYSize + 40);
		this.setTitle("Multi-File-Manager - by JNH and SR (\u00a9 2016-20)");
//			this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		// Surface
		bgPanel = new JPanel();
		bgPanel.setLayout(new BoxLayout(bgPanel, BoxLayout.Y_AXIS));
		bgPanel.setVisible(true);
		bgPanel.setPreferredSize(new java.awt.Dimension(prefXSize, prefYSize - 20));
		{
			jScrollPane1 = new JScrollPane();
			jScrollPane1.setHorizontalScrollBarPolicy(30);
			jScrollPane1.setVerticalScrollBarPolicy(20);
			jScrollPane1.setPreferredSize(new java.awt.Dimension(prefXSize - 10, prefYSize - 60));
			bgPanel.add(jScrollPane1);
			{
				Liste1 = new JList();
				jScrollPane1.setViewportView(Liste1);
				Liste1.setModel(new DefaultComboBoxModel(new String[] { "" }));
			}
			{
				JPanel spacer = new JPanel();
				spacer.setMaximumSize(new java.awt.Dimension(prefXSize, 10));
				spacer.setVisible(true);
				bgPanel.add(spacer);
			}
			{
				JPanel bottom = new JPanel();
				bottom.setLayout(new BoxLayout(bottom, BoxLayout.X_AXIS));
				bottom.setMaximumSize(new java.awt.Dimension(prefXSize, 10));
				bottom.setVisible(true);
				bgPanel.add(bottom);
				int locHeight = 40;
				int locWidth3 = prefXSize / 4 - 60;
				{
					loadSingleFilesButton = new JButton();
					loadSingleFilesButton.addActionListener(this);
					loadSingleFilesButton.setText("select files individually");
					loadSingleFilesButton.setMinimumSize(new java.awt.Dimension(locWidth3, locHeight));
					loadSingleFilesButton.setVisible(true);
					loadSingleFilesButton.setVerticalAlignment(SwingConstants.BOTTOM);
					bottom.add(loadSingleFilesButton);
				}
					loadByPatternButtom = new JButton();
					loadByPatternButtom.addActionListener(this);
					loadByPatternButtom.setText("select files by pattern");
					loadByPatternButtom.setMinimumSize(new java.awt.Dimension(locWidth3, locHeight));
					loadByPatternButtom.setVisible(true);
					loadByPatternButtom.setVerticalAlignment(SwingConstants.BOTTOM);
					bottom.add(loadByPatternButtom);
				{
					removeFileButton = new JButton();
					removeFileButton.addActionListener(this);
					removeFileButton.setText("remove selected files");
					removeFileButton.setMinimumSize(new java.awt.Dimension(locWidth3, locHeight));
					removeFileButton.setVisible(true);
					removeFileButton.setVerticalAlignment(SwingConstants.BOTTOM);
					bottom.add(removeFileButton);
				}
				{
					goButton = new JButton();
					goButton.addActionListener(this);
					goButton.setText("start processing");
					goButton.setMinimumSize(new java.awt.Dimension(locWidth3, locHeight));
					goButton.setVisible(true);
					goButton.setVerticalAlignment(SwingConstants.BOTTOM);
					bottom.add(goButton);
				}
			}
		}
		getContentPane().add(bgPanel);
	}

	@Override
	public void actionPerformed(ActionEvent ae) {
		Object eventQuelle = ae.getSource();
		if (eventQuelle == loadSingleFilesButton) {
			JFileChooser chooser = new JFileChooser();
			chooser.setPreferredSize(new Dimension(600, 400));
			if (dirsaved) {
				chooser.setCurrentDirectory(saved);
			}
			chooser.setMultiSelectionEnabled(true);
			Component frame = null;
			chooser.showOpenDialog(frame);
			File[] files = chooser.getSelectedFiles();
			for (int i = 0; i < files.length; i++) {
//					IJ.log("" + files[i].getPath());
				filesToOpen.add(files[i]);
				saved = files[i];
				dirsaved = true;
			}
			updateDisplay();
		}
		if (eventQuelle == loadByPatternButtom) {
			matchPattern(System.getProperty("user.dir"));
			updateDisplay();
		}
		if (eventQuelle == removeFileButton) {
			int[] indices = Liste1.getSelectedIndices();
			for (int i = indices.length - 1; i >= 0; i--) {
//					IJ.log("remove " + indices[i]);
				filesToOpen.remove(indices[i]);
			}
			updateDisplay();
		}
		if (eventQuelle == goButton) {
			done = true;
			dispose();
		}

	}

	private void matchPattern(String rootPath) {
		String posFilePattern = "", negFilePattern = "", negDirPattern = "";

		JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setMultiSelectionEnabled(true);
		fc.setCurrentDirectory(new File(rootPath));

		if (fc.showDialog(fc, "Choose directory to start pattern matching") != JFileChooser.APPROVE_OPTION) {
			return;
		}

		Stack<File> q = new Stack<File>();
		for (File f : fc.getSelectedFiles()) {
			q.push(f);
		}

		boolean posFileInputAsRegex = false, negFileInputAsRegex = false, negDirInputAsRegex = false;
		GenericDialog gd = new GenericDialog("Insert pattern matching parameters:");

		gd.addCheckbox("Input as Regex", posFileInputAsRegex);
		gd.setInsets(0, 50, 0);
		gd.addStringField("Enter pattern to be matched in filenames", posFilePattern, 16);

		gd.addCheckbox("Input as Regex", negFileInputAsRegex);
		gd.setInsets(0, 50, 0);
		gd.addStringField("Enter pattern in filenames to exclude files", negFilePattern, 16);

		gd.addCheckbox("Input as Regex", negDirInputAsRegex);
		gd.setInsets(0, 50, 0);
		gd.addStringField("Enter pattern in parent directories to exclude files", negDirPattern, 16);

		gd.showDialog();

		posFileInputAsRegex = gd.getNextBoolean();
		posFilePattern = gd.getNextString();
		negFileInputAsRegex = gd.getNextBoolean();
		negFilePattern = gd.getNextString();
		negDirInputAsRegex = gd.getNextBoolean();
		negDirPattern = gd.getNextString();
		if (gd.wasCanceled()) {
			return;
		}

		if (!posFileInputAsRegex) {
			posFilePattern = transformStringToRegex(posFilePattern);
		}
		if (!negFileInputAsRegex) {
			negFilePattern = transformStringToRegex(negFilePattern);
		}
		if (!negDirInputAsRegex) {
			negDirPattern = transformStringToRegex(negDirPattern);
		}

		File[] fid; // Files in Dir
		while (!q.isEmpty()) {
			fid = q.pop().listFiles();
			for (File f : fid) { // loop through files in dir
				if (f.isDirectory() && !f.getName().matches(negDirPattern)) {
					q.push(f); // add to queue if f is a dir and negDirPattern can't be matched
				} else if (f.getName().matches(posFilePattern) && !f.getName().matches(negFilePattern)) {
					// add to file list if posPattern matches and negative Pattern doesn't
					filesToOpen.add(f);
				}
			}
		}		
		return;
	}

	static String transformStringToRegex(String pattern) {
		String s = "";
		if (pattern.length() != 0)
			s = ".*" + pattern.replace(".", "\\.") + ".*";
		return s;
	}

	@SuppressWarnings("unchecked")
	public void updateDisplay() {
		String resultsString[] = new String[filesToOpen.size()];
		for (int i = 0; i < filesToOpen.size(); i++) {
			resultsString[i] = (i + 1) + ": " + filesToOpen.get(i).getName();
		}
		Liste1.setListData(resultsString);
	}
}