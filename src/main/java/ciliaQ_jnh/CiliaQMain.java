package ciliaQ_jnh;
/** ===============================================================================
 * CiliaQ, a plugin for imagej - Version 0.2.2
 * 
 * Copyright (C) 2017-2023 Jan Niklas Hansen
 * First version: June 30, 2017  
 * This Version: November 18, 2026
 * 
 * Parts of the code were inherited from MotiQ
 * (https://github.com/hansenjn/MotiQ).
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * 
 * For any questions please feel free to contact me (jan.hansen@uni-bonn.de).
* =============================================================================== */

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.text.*;
import javax.swing.UIManager;

import ciliaQ_jnh.stern_library.optimization.HungarianAlgorithm;
import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.measure.*;
import ij.plugin.*;
import ij.plugin.frame.Recorder;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.Prefs;

public class CiliaQMain implements PlugIn, Measurements {
	//Name variables
	static final String PLUGINNAME = "CiliaQ";
	static final String PLUGINVERSION = "0.2.2";
	
	/**
	 * ImageJ pref that holds the last version whose "What's new" dialog was silenced.
	 * And Texts and URLs for an opening information dialog 
	 * */
	static final String WHATS_NEW_PREF = "ciliaQ.whatsNewSeen";
	static final String RELEASE_URL = "https://github.com/hansenjn/CiliaQ/releases/tag/v" + PLUGINVERSION;
	static final String WHATS_NEW_TEXT =
				"DETECTION:\n"
			+	" - Handle label images (intensity = cilium/basal body ID)\n"
			+	" - Join the pieces of a cilium closer than a gap (0 = off); the skeleton is bridged across gaps\n"
			+	"TIME-LAPSE:\n"
			+	" - Link cilia across time by linear assignment or by label ID, besides classic overlap\n"
			+	" - Max frame gap: a cilium missing in some frames stays one object (0 = as before)\n"
			+	" - Basal body linking and tracking in time-lapse images\n"
			+	"MACROS AND HEADLESS:\n"
			+	" - Improved reporting from macro and headless running mode\n"
			+	" - Improved safety: missing macro keys are named and defaults are used\n"
			+	"OUTPUT:\n"
			+	" - The notifications of each image in <name>_CQ_log.txt\n"
			+	" - Empty cells instead of NaN or Infinity\n"
			+	"FIXED:\n"
			+	" - Time-lapse 3D overview is saved as TIFF\n"
			+	" - Basal bodies: z position with voxel depth, minimum size applied, columns shown in every mode\n"
			+	" - Kinetics output: hang and crash with objects that are basal bodies only\n"
			+	" - Images without calibration: skeleton lengths 3x too long, first point misplaced (GitHub issue #15)\n"
			+	" - 'of total #' in _CQ.txt also without segmented basal bodies\n"
			+	" - '# excluded' no longer includes cilia removed by the basal-body filter (own count)\n"
			+	" - Time-lapse: 'Minimum size (A/B)' was applied to the first frame only\n"
			+	" - Basal-body columns without skeleton results; 3D overviews of 32-bit images; crashes\n"
			+ "\n"
			+ "As before: do not mix skeleton results (e.g. length) with versions before v0.1.7.";


	/**	True if the dialog of this version has not been silenced: the pref holds another version, or none. */
	static boolean whatsNewDue(String seenVersion){
		return seenVersion == null || !seenVersion.equals(PLUGINVERSION);
	}

	/**	
	 * Shows the "What's new" dialog once per version (dialog runs only, see run()).
	 * New in v0.2.2: replaces the beta warning and the v0.2.0 notice.
	 **/
	private static void showWhatsNew(){
		if(!whatsNewDue(Prefs.get(WHATS_NEW_PREF, null)))	return;
		GenericDialog gd = new GenericDialog(PLUGINNAME + " v" + PLUGINVERSION + " - What's new");
		gd.setInsets(0,0,0);	gd.addMessage("What's new in " + PLUGINNAME + " v" + PLUGINVERSION, HeadingFont);
		gd.setInsets(5,0,0);	gd.addTextAreas(WHATS_NEW_TEXT, null, 25, 100);
		gd.getTextArea1().setEditable(false);
		gd.setInsets(5,0,0);	gd.addMessage("All detailed changes: release notes button below.", InstructionsFont);
		gd.setInsets(5,0,0);	gd.addCheckbox("Do not show this again until the next update", false);
		gd.addHelp(RELEASE_URL);
		gd.setHelpLabel("Release notes");
		gd.hideCancelButton();
		gd.showDialog();
		if(gd.getNextBoolean()){
			Prefs.set(WHATS_NEW_PREF, PLUGINVERSION);
			Prefs.savePreferences();
		}
	}
	
	/**	Dialogs for help links */
	static final String BB_HELP_URL = "https://github.com/hansenjn/CiliaQ/wiki/Basal-body-detection-in-CiliaQ";
	
	//Fix fonts
	static final Font SuperHeadingFont = new Font("Sansserif", Font.BOLD, 16);
	static final Font HeadingFont = new Font("Sansserif", Font.BOLD, 14);
	static final Font SubHeadingFont = new Font("Sansserif", Font.BOLD, 12);
	static final Font TextFont = new Font("Sansserif", Font.PLAIN, 12);
	static final Font InstructionsFont = new Font("Sansserif", 2, 12);
	static final Font RoiFont = new Font("Sansserif", Font.PLAIN, 12);

	//Fix formats
	DecimalFormat dformat6 = new DecimalFormat("#0.000000");
	DecimalFormat dformat3 = new DecimalFormat("#0.000");
	DecimalFormat dformat0 = new DecimalFormat("#0");
	DecimalFormat dfDialog = new DecimalFormat("#0.000000");	
	
	//Dialogs
	
	static final String[] nrFormats = {"US (0.00...)", "Germany (0,00...)"};
	static final String[] excludeOptions = {"nothing", "cilia touching x or y borders", "cilia touching x or y or z borders",
			"cilia touching x or y borders or whose thickest part is located at the z border"};
	static final String[] bbCOptions = {"not available - ignore channel ID specified below", "available - use channel to orient cilia", "segmented - detect and quantify BBs from channel (set-up detection in next dialog)"};
	static final String[] bbAssignmentOptions = {"each BB to closest cilium"};
	static final String[] bbCiliaFilterOptions = {"nothing based on BB-cilia-linking", "BBs without cilia", "cilia without BBs", "cilia without BBs and BBs without cilia"};
	
	static SimpleDateFormat NameDateFormatter = new SimpleDateFormat("yyMMdd_HHmmss");
	static SimpleDateFormat FullDateFormatter = new SimpleDateFormat("yyyy-MM-dd	HH:mm:ss");
	static SimpleDateFormat FullDateFormatter2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	static SimpleDateFormat YearOnly = new SimpleDateFormat("yyyy");
	
	//Macro-related settings
	boolean record = false;
	boolean showGUIs = true;
	
	//Progress Dialog
	ProgressReporter progress;	
	boolean processingDone = false;	
	boolean continueProcessing = true;
	
	// Dialog parameters
	static final String[] taskVariant = {"active image in FIJI","multiple images (open multi-task manager)", "all images open in FIJI"};
	String selectedTaskVariant = taskVariant[1];
	int tasks = 1;
	
	boolean recalibrate = false;
	double calibration = 0.21, fps = 0.5;
	String calibrationDimension = "µm";
	double voxelDepth = 1.0;
	double timePerFrame = 0.5;
	static final String[] timeFormats = {"sec", "min", "hr", "d"};
	String timeUnit = timeFormats[1];
	
	final static String[] settingsMethod = {"manually enter preferences", "load preferences from existing CiliaQ results file ('...CQ.txt')"};	
	String selectedSettingsVariant = settingsMethod [0];
	
	int channelReconstruction = 1, channelC2 = 2, channelC3 = 3, basalStainC = 4;
	int minSize = 10,
		minRestSize = 1,
		minBBSize = 1; // New function in v0.2.0
	boolean increaseRangeCilia = true,
			increaseRangeRegions = true,
			increaseRangeBB = true;
	
	/**	New in v0.2.2: Reading a channel that holds LABEL IDs (one value per object), not intensities - as written
	 *	by CiliaQ Preparator V0.2.1's new ridge detection and expansion tools, or by external tools. One switch per
	 *	channel: cilia (the reconstruction channel) and basal bodies (the segmented basal-body channel).
	 *	Off: any non-zero voxel belongs to an object, as before.
	 **/
	boolean labelModeCilia = false, labelModeBB = false;

	/**	New in v0.2.2: join pieces of one cilium that lie closer than this gap,
	 *	in the image's calibrated unit (joinParticles): every piece of a mask,
	 *	in label mode only pieces with the same label. 0 = off (the default).
	 *	A gappy stain splits a cilium; ridge detection splits a label where another
	 *	filament passes over it. */
	double joinGap = 0.0;


	/**	New in v0.2.2: the macro had no max-frame-gap= (written before v0.2.2),
	 *	so the frame gap is 0, as then. */
	boolean frameGapKeyMissing = false;
	
	/**	New in v0.2.2: the macro keys that were missing (or empty) and got their default, key -> default, in reading
	 *	order (readSettingsFromMacroString; named for each image, see the task loop in run()). */
	LinkedHashMap<String, String> macroDefaults = new LinkedHashMap<String, String>();

	/**	New in v0.2.2: the unit of a loaded settings file's distances (its calibration line),
	 *	null otherwise; each image compares it with its own unit (no conversion). */
	String settingsFileUnit = null;

	/**	New in v0.2.2: the task (image) being analysed, 1 = the first;
	 * its notifications are progress.getNotes(currentTask). */
	int currentTask = 1;
	
	String excludeSelection = excludeOptions [2];
	boolean measureC2 = true,
			measureC3 = true,
			measureBasal = false,
			segmentedBB = false; // New function in v0.2.0

	//Basal body settings
	String bbCSelection = bbCOptions [0];
	String bbAssignmentSelection = bbAssignmentOptions [0];
	String bbCiliaFilterSelection = bbCiliaFilterOptions [3];
		
	//Skeletons
	boolean skeletonize = true;
	double gXY = 2.0;
	double gZ = 0.0;
	double refDistance = 1.0;
	
	//Basal body segmentation based
	double maxDistanceBBCiliumEnd = 2.0;
	static final String[] timelapseTrackingOptions = {"classic pixel-based frame connection",
			"linear assignment (frame-to-frame)",
			"by label ID (the same label in every frame)"};	// New in v0.2.2: the third connection needs label mode and unique labels
	String timelapseTrackingSelection = timelapseTrackingOptions[0];
	double maxTrackingDistance = 5.0;	// calibrated units
	int maxFrameGap = 0;				// frames a cilium may vanish and rejoin (classic and linear assignment); 0 = as before v0.2.2
	
	//Saving
	boolean saveDate = false;	
	boolean saveSingleCiliaTifs = false, saveSingleCilia3DImages = false, saveOverview3DImages = true;
	
	String ChosenNumberFormat = nrFormats[0];
	
public void run(String arg) {
	dfDialog.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
	try  
	  { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
	catch (Exception e)
	  { e.printStackTrace(); }

	//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
	//-----------Eventually read settings from macro input------------------------
	//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
	boolean showDialog = true;
	if (IJ.macroRunning() && Macro.getOptions() != null && Macro.getOptions().length() > 0) {
		readSettingsFromMacroString(Macro.getOptions(), false);
		showDialog = false;
	}

	record = false;
	if (showDialog && showGUIs) {
		if (Recorder.record) {
			/*
			 * Deactivate recording tool, since it will not work based on how the different
			 * settings are named in CiliaQ It will be reactivated when all Generic Dialogs
			 * for setting selection have passed.
			 */
			record = true;
			Recorder.record = false;
		}

		// New in v0.2.2: instead of the beta warning and the v0.2.0 notice
		showWhatsNew();

		// &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		// -------------------------GenericDialog--------------------------------------
		// &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&

		GenericDialog gd = new GenericDialog(PLUGINNAME + " on " + System.getProperty("os.name") + " - set parameters");
		gd.addHelp("https://github.com/hansenjn/CiliaQ#user-guide--manual");
		//show Dialog-----------------------------------------------------------------
		//Note: .setInsets(top, left, bottom)
		gd.setInsets(0,0,0);	gd.addMessage(PLUGINNAME + ", Version " + PLUGINVERSION + ", \u00a9 2017 - 2026 JN Hansen", SuperHeadingFont);
		gd.setInsets(0,0,0);	gd.addMessage("More information at https://github.com/hansenjn/CiliaQ.", InstructionsFont);
			
		gd.setInsets(10,0,0);	gd.addChoice("process ", taskVariant, selectedTaskVariant);
		
		gd.setInsets(10,0,0);	gd.addMessage("Calibration settings", SubHeadingFont);
		gd.setInsets(5,0,0);	gd.addCheckbox("manually calibrate image", recalibrate);
		gd.setInsets(0,0,0);	gd.addNumericField("    -> calibration [unit/px]: ", calibration, 4);
		gd.setInsets(0,0,0);	gd.addNumericField("    -> voxel depth [unit/voxel]: ",  voxelDepth, 6);
		gd.setInsets(0,0,0);	gd.addStringField("    -> calibration unit: ", calibrationDimension);
		gd.setInsets(5,0,0);	gd.addNumericField("Time interval: [time unit]: ", timePerFrame, 2);
		gd.setInsets(0,0,0);	gd.addChoice("Time unit: ", timeFormats, timeUnit);
		
		gd.setInsets(10,0,0);	gd.addMessage("Detection settings", SubHeadingFont);
		gd.setInsets(10,0,0);	gd.addChoice("Preferences: ", settingsMethod, selectedSettingsVariant);
		gd.setInsets(0,0,0);	gd.addMessage("Note: You may only load preferences from files generated by CiliaQ version v0.0.6 or higher.", InstructionsFont);
			
		gd.setInsets(10,0,0);	gd.addMessage("Output settings", SubHeadingFont);
		gd.setInsets(0,0,0);	gd.addCheckbox("save date in output file names", saveDate);
		gd.setInsets(0,0,0);	gd.addCheckbox("save result image for each individual cilium", saveSingleCiliaTifs);
		gd.setInsets(0,0,0);	gd.addCheckbox("save 3D visualizations for each individual cilium", saveSingleCilia3DImages);
		gd.setInsets(0,0,0);	gd.addCheckbox("save 3D visualizations for whole image", saveOverview3DImages);
		gd.setInsets(0,0,0);	gd.addChoice("output number format", nrFormats, nrFormats[0]);
		gd.showDialog();
		//show Dialog-----------------------------------------------------------------

		//read and process variables--------------------------------------------------	
		selectedTaskVariant = gd.getNextChoice();
			
		recalibrate = gd.getNextBoolean();
		calibration = (double) gd.getNextNumber();
		voxelDepth = (double) gd.getNextNumber();
		calibrationDimension = gd.getNextString();
		timePerFrame = (double) gd.getNextNumber();
		if(timePerFrame==0.0){
			timePerFrame=1.0;
		}
		timeUnit = gd.getNextChoice();
		
		selectedSettingsVariant = gd.getNextChoice();
		
		saveDate = gd.getNextBoolean();
		saveSingleCiliaTifs = gd.getNextBoolean();
		saveSingleCilia3DImages = gd.getNextBoolean();
		saveOverview3DImages = gd.getNextBoolean();
		
		ChosenNumberFormat = gd.getNextChoice();
		if(ChosenNumberFormat.equals(nrFormats[0])){ //US-Format
			dformat6.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
			dformat3.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
			dformat0.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
		}else if (ChosenNumberFormat.equals(nrFormats[1])){
			dformat6.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.GERMANY));
			dformat3.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.GERMANY));
			dformat0.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.GERMANY));
		}	
		//read and process variables--------------------------------------------------
		if (gd.wasCanceled()) return;
		
		// &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		// ---------------------end-GenericDialog-end----------------------------------
		// &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&

		// LOAD SETTINGS FOR DETECTION
		if(selectedSettingsVariant.equals(settingsMethod [0])){
			if(!enterSettings()) {
				return;
			}
		}else if(!importSettings()) {
			IJ.error("Preferences were not completely loaded ...");
			return;
		}
	
		if (record) {
			/*
			 * Reactivate recording.
			 */
			Recorder.record = true;
		}
	}

	/*
	 * Create macro recording string if macro recording activated:
	 */
	if (record) {
		String recordString = createRecordString();
		Recorder.record = true;

		/*
		 * Cancel ImageJ's own pending auto-record of the menu command. Without this the
		 * Recorder emits BOTH our option string and a bare run("CiliaQ v0.2.2");
		 * replaying that macro runs CiliaQ a second time with no options, so it falls
		 * back to defaults on whatever image is in front.
		 */
		if (Recorder.getInstance() != null) {
			Recorder.setCommand(null);
		}

		Recorder.recordString("run(\"" + PLUGINNAME + " v" + PLUGINVERSION + "\",\"" + recordString + "\");\n");
	}

	// LOAD FILES
	String name [] = {"",""};
	String dir [] = {"",""};
	ImagePlus allImps [] = new ImagePlus [2];
	{
		//Improved file selector
		try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception e){}
		if(selectedTaskVariant.equals(taskVariant[1])){
			OpenFilesDialog od = new OpenFilesDialog ();
			od.setLocation(0,0);
			od.setVisible(true);
			
			final boolean [] fileListClosed = {false};	// v0.2.2: closing the window ends the run (before, CiliaQ waited forever)
			od.addWindowListener(new java.awt.event.WindowAdapter() {
				public void windowClosing(WindowEvent winEvt) {
					fileListClosed[0] = true;
				}
			});

			// Waiting for od to be done
			while (od.done == false && !fileListClosed[0]) {
				try {
					Thread.currentThread().sleep(50);
				} catch (Exception e) {
				}
			}
			if(!od.done) {
				od.dispose();	// new in v0.2.2: before, closing only hid the window
				return;			// the file list was closed without starting
			}

			tasks = od.filesToOpen.size();
			name = new String [tasks];
			dir = new String [tasks];
			for(int task = 0; task < tasks; task++){
				name[task] = od.filesToOpen.get(task).getName();
				dir[task] = od.filesToOpen.get(task).getParent() + System.getProperty("file.separator");
			}		
		}else if(selectedTaskVariant.equals(taskVariant[0])){
			try {
				if(WindowManager.getIDList()==null){
					IJ.error("Plugin canceled - no image open in FIJI!");
					return;
				}
				FileInfo info = WindowManager.getCurrentImage().getOriginalFileInfo();
				name [0] = info.fileName;	//get name
				dir [0] = info.directory;	//get directory
				tasks = 1;
			}catch(Exception e) {
				IJ.error("Error when loading the active image!\n"
						+ "Likely the image you tried to process was not saved or opened in Virtual Stack mode.\n"
						+ "Thus CiliaQ could not determine the output path!\n"
						+ "Save the image or make sure it is not in Virtual Stack mode and retry...");
				return;
			}			
		}else if(selectedTaskVariant.equals(taskVariant[2])){	// all open images
			try {
				if(WindowManager.getIDList()==null){
					IJ.error("Plugin canceled - no image open in FIJI!");
					return;
				}
				int IDlist [] = WindowManager.getIDList();
				tasks = IDlist.length;	
				if(tasks == 1){
					selectedTaskVariant=taskVariant[0];
					FileInfo info = WindowManager.getCurrentImage().getOriginalFileInfo();
					name [0] = info.fileName;	//get name
					dir [0] = info.directory;	//get directory
				}else{
					name = new String [tasks];
					dir = new String [tasks];
					allImps = new ImagePlus [tasks];
					for(int i = 0; i < tasks; i++){
						allImps[i] = WindowManager.getImage(IDlist[i]); 
						FileInfo info = allImps[i].getOriginalFileInfo();
						name [i] = info.fileName;	//get name
						dir [i] = info.directory;	//get directory
					}		
				}
			}catch(Exception e) {
				IJ.error("Error when loading the active images!\nLikely an active image you tried to process was not saved or opened in Virtual Stack mode.\n"
						+ "Thus CiliaQ could not determine the output path!\n"
						+ "Make sure all images are saved and not in Virtual Stack mode and retry...");
				return;
			}	
		}
	}
	 	
	//add progress reporter: a dialog interactively, the log when headless
	if(showGUIs) {
		ProgressDialog dialog = new ProgressDialog(name, tasks);
		dialog.setLocation(0,0);
		dialog.setVisible(true);
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			public void windowClosing(WindowEvent winEvt) {
				if (record) {
					Recorder.record = true;
				}
				if (processingDone == false) {
					IJ.error("Script stopped...");
				}
				continueProcessing = false;
				return;
			}
		});
		progress = dialog;
	}else {
		progress = new ConsoleProgress(name, tasks);
	}

//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
//------------------------------CELL MEASUREMENT------------------------------
//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
	
	// v0.2.2: a minimum size below 1 voxel means nothing (0 gave a crash), so it is raised to 1, with a note per image
	String raisedSizes = "";
	if(minSize < 1){
		raisedSizes = "minimum cilium size " + minSize + " -> 1";
		minSize = 1;
	}
	if(segmentedBB && minBBSize < 1){
		raisedSizes += (raisedSizes.isEmpty() ? "" : ", ") + "minimum basal body size " + minBBSize + " -> 1";
		minBBSize = 1;
	}

	for(int task = 0; task < tasks; task++){
	currentTask = task + 1;	// New in v0.2.2: this image's notes are those of its task (getNotes)

	if(!raisedSizes.isEmpty()){
		progress.notifyMessage("A minimum size below 1 voxel means nothing, so it was raised to 1: " + raisedSizes + "."
				+ " (File " + name[task] + ", Dir: " + dir[task] + ")", ProgressReporter.NOTIFICATION);
	}

	if(!macroDefaults.isEmpty()){
		// New in v0.2.2: the macro keys that got their default, once per image
		String used = "";
		for(Map.Entry<String, String> e : macroDefaults.entrySet()){
			used += (used.isEmpty() ? "" : ", ") + e.getKey() + "="
					+ (e.getValue().contains(" ") ? "[" + e.getValue() + "]" : e.getValue());
		}
		progress.notifyMessage("The macro does not set these keys, so their defaults were used: " + used + "."
				+ " (File " + name[task] + ", Dir: " + dir[task] + ")", ProgressReporter.NOTIFICATION);
	}
	
	String logPrefix = null;	// New in v0.2.2: <dir><name>_CQ[_<date>], as filePrefix below
	running: while(continueProcessing){
		Date startDate = new Date();
		logPrefix = dir[task] + (name[task].contains(".") ? name[task].substring(0, name[task].lastIndexOf(".")) : name[task])
				+ "_CQ" + (saveDate ? "_" + NameDateFormatter.format(startDate) : "");	// New in v0.2.2
		progress.updateBarText("in progress...");
				
		//Check for problems
		if(name[task].toLowerCase(Locale.ROOT).endsWith(".txt")){
			progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": File " + name[task] + " is no image! Could not be processed!", ProgressReporter.ERROR);
			break running;
		}
		if(name[task].toLowerCase(Locale.ROOT).endsWith(".zip")){
			progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": File " + name[task] + " is no image! Could not be processed!", ProgressReporter.ERROR);
			break running;
		}
		//Check for problems
				
		//open Image
			ImagePlus imp;
			try {
				if (selectedTaskVariant.equals(taskVariant[1])) {
					imp = IJ.openImage("" + dir[task] + name[task] + "");
					imp.deleteRoi();
				} else if (selectedTaskVariant.equals(taskVariant[0])) {
					imp = WindowManager.getCurrentImage().duplicate();
					imp.deleteRoi();
				} else {
					imp = allImps[task].duplicate();
					imp.deleteRoi();
				}
			} catch (Exception e) {
				progress.notifyMessage("Task " + (task + 1) + "/" + tasks + ": file " + name[task]
						+ " is no image - could not be processed!", ProgressReporter.ERROR);
				break running;
			}
			
		//Calibrate
			imp.hide();
			double pixelWidth = imp.getCalibration().pixelWidth;
			double pixelHeight = imp.getCalibration().pixelHeight;
			double pixelDepth = imp.getCalibration().pixelDepth;
			fps = imp.getCalibration().fps;
			double frameInterval = imp.getCalibration().frameInterval;
			String unit = imp.getCalibration().getUnit();
			String calTimeUnit = imp.getCalibration().getTimeUnit();
			if (recalibrate) {
				pixelWidth = calibration;
				imp.getCalibration().pixelWidth = pixelWidth;
				pixelHeight = calibration;
				imp.getCalibration().pixelHeight = pixelHeight;
				pixelDepth = voxelDepth;
				imp.getCalibration().pixelDepth = pixelDepth;
				unit = calibrationDimension;
				imp.getCalibration().setUnit(unit);
				frameInterval = timePerFrame;
				imp.getCalibration().frameInterval = frameInterval;
				calTimeUnit = timeUnit;
				imp.getCalibration().setTimeUnit(calTimeUnit);
				if (timeUnit.equals(timeFormats[0])) {
					fps = 1.0 / (double) timePerFrame;
					imp.getCalibration().fps = fps;
				} else if (timeUnit.equals(timeFormats[1])) {
					fps = 1.0 / (60 * (double) timePerFrame);
					imp.getCalibration().fps = fps;
				} else if (timeUnit.equals(timeFormats[2])) {
					fps = 1.0 / (60 * 60 * (double) timePerFrame);
					imp.getCalibration().fps = fps;
				} else {
					fps = 1.0 / (24 * 60 * 60 * (double) timePerFrame);
					imp.getCalibration().fps = fps;
				}
			} else {
				calibration = pixelWidth;
				calibrationDimension = "" + unit;
				voxelDepth = pixelDepth;
				if (pixelWidth != pixelHeight) {
					progress.notifyMessage("Task " + (task + 1) + "/" + tasks
							+ ": x and y calibration in metadata differ - used only x metadata calibration for both, x and y!",
							ProgressReporter.NOTIFICATION);
					imp.getCalibration().pixelHeight = pixelWidth;	// v0.2.2: as the note says (before: the height was kept, so the skeleton's y did not match)
				}
				timePerFrame = frameInterval;
				if (timePerFrame == 0.0) {
					timePerFrame = 1.0;
				}
				imp.getCalibration().frameInterval = timePerFrame;
				if (fps == 0.0) {
					fps = 1.0;
				}
				imp.getCalibration().fps = fps;
				timeUnit = calTimeUnit;
			}

			//check for correctness
			if(settingsFileUnit != null && !sameUnit(settingsFileUnit, calibrationDimension)){
				// New in v0.2.2
				progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": the settings file's distances are in [" + settingsFileUnit
						+ "], this image is in [" + calibrationDimension + "]: they are used as numbers, not converted (join gap,"
						+ " max tracking distance, reference distance).", ProgressReporter.NOTIFICATION);
			}
			if(channelReconstruction>imp.getNChannels()){
				progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": reconstruction channel does not exist - analysis cancelled!", ProgressReporter.ERROR);
				break running;
			}
			if(segmentedBB && basalStainC>imp.getNChannels()){
				progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": basal body channel does not exist - analysis cancelled!", ProgressReporter.ERROR);
				break running;
			}
			if(segmentedBB && allForeground(imp, basalStainC)){
				// v0.2.2: e.g. an intensity channel chosen as the segmented one (v0.2.1 stopped the run)
				progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": basal body channel " + basalStainC + " is not segmented"
						+ " (every voxel is foreground) - analysis cancelled! Check the basal body channel in the settings.", ProgressReporter.ERROR);
				break running;
			}
			boolean measureC2local = measureC2;
			if(measureC2 && channelC2>imp.getNChannels()){
				measureC2local = false;
				progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": channel A does not exist - skipped channel A parameters!", ProgressReporter.NOTIFICATION);
			}
			boolean measureC3local = measureC3;
			if(measureC3 && channelC3>imp.getNChannels()){
				measureC3local = false;
				progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": channel B does not exist - skipped channel B parameters!", ProgressReporter.NOTIFICATION);				
			}
			boolean measureBasalLocal = measureBasal;
			if(measureBasalLocal && basalStainC>imp.getNChannels()){
				measureBasalLocal = false;
				progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": basal channel does not exist - skiped basal channel-based processing!", ProgressReporter.NOTIFICATION);
			}
			
		//size-filter images
			if(measureC2local && minRestSize > 1){
				this.filterChannel(imp, channelC2, "intensity A", minRestSize, increaseRangeRegions);
			}			
			if(measureC3local && minRestSize > 1){
				this.filterChannel(imp, channelC3, "intensity B", minRestSize, increaseRangeRegions);
			}
		//size-filter images
			
		//Define Output File Names			
			String filePrefix;
			if(name[task].contains(".")){
				filePrefix = name[task].substring(0,name[task].lastIndexOf(".")) + "_CQ";
			}else{
				filePrefix = name[task] + "_CQ";
			}
			
			if(saveDate){
				filePrefix += "_" + NameDateFormatter.format(startDate);
			}
			
			//Create subfolder to save additional files
			String subfolderPrefix = "" + dir [task] + filePrefix + System.getProperty("file.separator") + "CQ";
			if(this.saveSingleCilia3DImages || this.saveSingleCiliaTifs || imp.getNFrames() != 1) {
				//Create the directory only if really needed
				try{
					new File(dir [task] + filePrefix).mkdirs();						
				}catch(Exception e){
					progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": Failed to create subfolder ["
							+ (dir [task] + filePrefix)
							+ "] to save additional plots! Will save plots into original folder!",ProgressReporter.NOTIFICATION);
				}
			}
			
			filePrefix = dir[task] + filePrefix;
		//Define Output File Names
			
		//Check whether exclude selection matches and customize if needed	
			String tempExcludeSelection = excludeSelection;
			if(imp.getNSlices() == 1 && (excludeSelection.equals(excludeOptions [2]) || excludeSelection.equals(excludeOptions [3]))){
				tempExcludeSelection = excludeOptions [1];
				progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": File "
					+ name [task]
					+ " is a 2D image - setting '"
					+ excludeSelection
					+ "' was switched to '"
					+ tempExcludeSelection
					+ "' for this image, to ensure that cilia can be detected.", ProgressReporter.NOTIFICATION);
			}
		
		// Run start-up checks
			runStartupChecks(imp, channelReconstruction, segmentedBB ? basalStainC : -1,
					name [task], dir [task], progress);
			
		// Check for the basal body radius being to small, once per image
			if(segmentedBB && Cilium.bbRadiusTooSmall(imp.getCalibration())){				
				progress.notifyMessage("Basal body measurement radius (" + tools.micronToImageUnit(imp.getCalibration(), 1.0)
						+ " " + imp.getCalibration().getUnit() + ") spans fewer than 2 voxels - the reported basal body"
						+ " intensities are unreliable. Check the image calibration. (File " + name[task] + ", Dir: "
						+ dir[task] + ")", ProgressReporter.NOTIFICATION);
			}
				
		//Get cilia data and save them
			if(imp.getNFrames()!=1){
				//Timelapse Mode
				progress.notifyMessage("Timelapse workflow started...", ProgressReporter.LOG);				
				this.analyzeCiliaIn4DAndSaveResults(imp, measureC2local, measureC3local, measureBasalLocal, name[task], dir[task], startDate, filePrefix, subfolderPrefix, tempExcludeSelection);
			}else{
				//Single-frame Mode
				progress.notifyMessage("Single-timepoint workflow started...", ProgressReporter.LOG);
				this.analyzeCiliaIn3DAndSaveResults(imp, measureC2local, measureC3local, measureBasalLocal, name[task], dir[task], startDate, filePrefix, subfolderPrefix, tempExcludeSelection);
			}
			
		//Get cilia data and save them
	processingDone = true;
	break running;
	}	
	writeImageLog(logPrefix, name[task]);	// New in v0.2.2: also after an error
	System.gc();
	progress.updateBarText("finished!");
	progress.setBar(1.0);
	progress.moveTask(task);		
}
}

/**
 * A function that sets all settings based on an option String
 * given when calling this plugin from a Macro.
 * If commands are missing, parameters will be left at default.
 * New function in v0.2.2 using helpers for better parsing.
 * @param macroOptions: String containing commands for setting up the settings
 * @param logDetection: If true, logs what was detected.
 */
private void readSettingsFromMacroString(String macroOptions, boolean logDetection) {
	if(logDetection) IJ.log("Input macro Options: " + macroOptions);

	String o = macroOptions;

	//In macro mode CiliaQ by default will run on the front-most opened image.
	selectedTaskVariant = taskVariant[0];

	recalibrate = macroFlag(o, "re-calibrate") || macroFlag(o, "recalibrate");
	if(recalibrate) {
		calibration = macroDouble(o, "length", macroDouble(o, "length-calibration", 0.21, logDetection), logDetection);
		aliasDefault(o, "length", "length-calibration");
		if(logDetection) IJ.log("detected calibration: " + calibration);

		voxelDepth = macroDouble(o, "depth", macroDouble(o, "depth-calibration", 1.0, logDetection), logDetection);
		aliasDefault(o, "depth", "depth-calibration");
		if(logDetection) IJ.log("detected voxelDepth: " + voxelDepth);

		calibrationDimension = macroString(o, "calibration-unit", "µm");
		if(logDetection) IJ.log("detected calibrationDimensions: " + calibrationDimension);

		timePerFrame = macroDouble(o, "time-interval", 0.5, logDetection);
		if(timePerFrame==0.0){
			timePerFrame=1.0;
		}
		if(logDetection) IJ.log("detected timePerFrame: " + timePerFrame);

		timeUnit = macroChoice(o, "time-unit", timeFormats, timeFormats[1]);
		if(logDetection) IJ.log("detected timeUnit: " + timeUnit);
	}

	saveDate = macroFlag(o, "include-date");
	if(logDetection) IJ.log("detected saveDate: " + saveDate);

	saveSingleCiliaTifs = macroFlag(o, "save-single-cilia-result-images");
	if(logDetection) IJ.log("detected save-single-cilia-result-images: " + saveSingleCiliaTifs);

	saveSingleCilia3DImages = macroFlag(o, "save-3D-individual-cilia");
	if(logDetection) IJ.log("detected saveSingleCilia3DImages: " + saveSingleCilia3DImages);

	saveOverview3DImages = macroFlag(o, "save-3D-whole-image");
	if(logDetection) IJ.log("detected saveOverview3DImages: " + saveOverview3DImages);

	ChosenNumberFormat = macroChoice(o, "number-format", nrFormats, nrFormats[0]);
	if(logDetection) IJ.log("detected numberFormat: " + ChosenNumberFormat);
	if(ChosenNumberFormat.equals(nrFormats[0])){ //US-Format
		dformat6.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
		dformat3.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
		dformat0.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
	}else if (ChosenNumberFormat.equals(nrFormats[1])){
		dformat6.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.GERMANY));
		dformat3.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.GERMANY));
		dformat0.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.GERMANY));
	}

	channelReconstruction = (int) macroDouble(o, "reconstruction-channel", 1, logDetection);
	if(logDetection) IJ.log("detected reconstruction-channel: " + channelReconstruction);

	measureC2 = macroFlag(o, "measure-intensity-A");
	if(measureC2){
		if(logDetection) IJ.log("detected measure intensity A: " + measureC2);
		channelC2 = (int) macroDouble(o, "channel-A", 2, logDetection);
		if(logDetection) IJ.log("detected channel-A: " + channelC2);
	}

	measureC3 = macroFlag(o, "measure-intensity-B");
	if(measureC3){
		if(logDetection) IJ.log("detected measure intensity B: " + measureC3);
		channelC3 = (int) macroDouble(o, "channel-B", 3, logDetection);
		if(logDetection) IJ.log("detected channel-B: " + channelC3);
	}

	if(macroFlag(o, "basal-stain-present")){
		measureBasal = true;
		if(logDetection) IJ.log("detected basal stain present: " + measureBasal);

		basalStainC = (int) macroDouble(o, "channel-basal-stain", 4, logDetection);
		if(logDetection) IJ.log("detected channel-basal-stain: " + basalStainC);

	} else if (macroFlag(o, "basal-stain-segmented")){
		measureBasal = true;
		segmentedBB = true;
		if(logDetection) IJ.log("detected basal stain present: " + measureBasal);
		if(logDetection) IJ.log("detected basal stain segmented: " + segmentedBB);

		basalStainC = (int) macroDouble(o, "channel-basal-stain", 4, logDetection);
		if(logDetection) IJ.log("detected channel-basal-stain: " + basalStainC);

		minBBSize = (int) macroDouble(o, "minimum-basal-body-size", 1, logDetection);
		if(logDetection) IJ.log("detected minimum-basal-body-size: " + minBBSize);

		increaseRangeBB = macroFlag(o, "increase-range-basal-body");
		if(logDetection) IJ.log("detected basal stain segmented: " + increaseRangeBB);

		labelModeBB = macroFlag(o, "label-mode-basal-body");	// New in v0.2.2
		if(logDetection) IJ.log("detected label-mode-basal-body: " + labelModeBB);

		bbAssignmentSelection = macroChoice(o, "assign", bbAssignmentOptions, bbAssignmentOptions[0]);
		if(logDetection) IJ.log("detected exclude selection: " + bbAssignmentSelection);

		maxDistanceBBCiliumEnd = macroDouble(o, "max-bb-to-cilium-distance", 2.0, logDetection);
		if(logDetection) IJ.log("detected max-bb-to-cilium-distance: " + maxDistanceBBCiliumEnd);

		bbCiliaFilterSelection = macroChoice(o, "remove", bbCiliaFilterOptions, bbCiliaFilterOptions[3]);
		if(logDetection) IJ.log("detected exclude selection: " + bbCiliaFilterSelection);
	}else {
		measureBasal = false;
	}

	minSize = (int) macroDouble(o, "minimum-cilium-size", 10, logDetection);
	if(logDetection) IJ.log("detected minimum-cilium-size: " + minSize);

	increaseRangeCilia = macroFlag(o, "increase-range-cilia");
	if(logDetection) IJ.log("detected increase-range-cilia: " + increaseRangeCilia);
	
	labelModeCilia = macroFlag(o, "label-mode-cilia");
	joinGap = macroDouble(o, "join-gap", 0.0, logDetection);
	joinGap = checkedJoinGap(joinGap, "macro");
	if(logDetection) IJ.log("detected label-mode-cilia: " + labelModeCilia + ", join-gap: " + joinGap);

	excludeSelection = macroChoice(o, "exclude", excludeOptions, excludeOptions[2]);
	if(logDetection) IJ.log("detected exclude selection: " + excludeSelection);

	minRestSize = (int) macroDouble(o, "minimum-size-intensity", 1, logDetection);
	if(logDetection) IJ.log("detected minimum-size-intensity: " + minRestSize);

	increaseRangeRegions = macroFlag(o, "increase-range-intensity");
	if(logDetection) IJ.log("detected increase-range-intensity: " + increaseRangeRegions);

	skeletonize = macroFlag(o, "skeleton");
	if(logDetection) IJ.log("detected skeletonize: " + skeletonize);

	gXY = macroDouble(o, "gauss-xy", 2.0, logDetection);
	if(logDetection) IJ.log("detected gSigmaXY: " + gXY);

	gZ = macroDouble(o, "gauss-z", 0.0, logDetection);
	if(logDetection) IJ.log("detected gSigmaZ: " + gZ);

	refDistance = macroDouble(o, "distance-curvature", 1.0, logDetection);
	if(logDetection) IJ.log("detected reference distance for tangets and curvature: " + refDistance);

	//NEW from v0.2.2 on: timelapse cilium bb tracking
	timelapseTrackingSelection = macroChoice(o, "tl-tracking", timelapseTrackingOptions, timelapseTrackingOptions[0]);
	if(logDetection) IJ.log("detected tl-tracking: " + timelapseTrackingSelection);

	maxTrackingDistance = macroDouble(o, "max-tracking-distance", 5.0, logDetection);
	if(logDetection) IJ.log("detected max-tracking-distance: " + maxTrackingDistance);

	frameGapKeyMissing = macroValue(o, "max-frame-gap") == null;
	maxFrameGap = frameGapKeyMissing ? 0 : (int) macroDouble(o, "max-frame-gap", 0, logDetection);	// New in v0.2.2: missing = 0 (macros before v0.2.2)
	if(logDetection) IJ.log("detected max-frame-gap: " + maxFrameGap + (frameGapKeyMissing ? " (key missing: 0, as before v0.2.2)" : ""));

	showGUIs = macroFlag(o, "showgui");
	if(logDetection) IJ.log("detected show GUIs: " + showGUIs);
}

/**
 * A function to create
 * @return an option String based on the settings applied.
 * This option String will be usable to call this plugin under
 * the same options as when recording it from a Macro
 */
private String createRecordString() {
	String recordString = "";

	if(recalibrate) {
		recordString += "re-calibrate"
			+ " length=" + dfDialog.format(calibration) 
			+ " depth=" + dfDialog.format(voxelDepth)
			+ " calibration-unit=" + calibrationDimension
			+ " time-interval=" + dfDialog.format(timePerFrame)
			+ " time-unit=" + timeUnit 
			+ " ";
	}
	
	if (saveDate) {
		recordString += "include-date ";
	}
	if (saveSingleCiliaTifs) {
		recordString += "save-single-cilia-result-images ";
	}
	if (saveSingleCilia3DImages) {
		recordString += "save-3D-individual-cilia ";
	}
	if (saveOverview3DImages) {
		recordString += "save-3D-whole-image ";
	}
	
	recordString += "number-format=[" + ChosenNumberFormat + "] ";
	
	recordString += "reconstruction-channel=" + (int)channelReconstruction+ " ";
	
	if(measureC2) {
		recordString += "measure-intensity-A ";
		recordString += "channel-A=" + channelC2+ " ";
	}
	if(measureC3) {
		recordString += "measure-intensity-B ";
		recordString += "channel-B=" + channelC3+ " "; 	
	}
	if (measureBasal) {
		if (segmentedBB) {
			recordString += "basal-stain-segmented ";
			recordString += "channel-basal-stain=" + basalStainC + " ";
			recordString += "minimum-basal-body-size=" + minBBSize + " ";
			if (increaseRangeBB) {
				recordString += "increase-range-basal-body ";
			}
			if (labelModeBB) {	// New in v0.2.2
				recordString += "label-mode-basal-body ";
			}
			recordString += "assign=[" + bbAssignmentSelection + "] ";
			recordString += "max-bb-to-cilium-distance=" + maxDistanceBBCiliumEnd + " ";
			recordString += "remove=[" + bbCiliaFilterSelection + "] ";
		} else {
			recordString += "basal-stain-present ";
			recordString += "channel-basal-stain=" + basalStainC + " ";
		}
	}
	recordString += "minimum-cilium-size=" + minSize + " ";
	if (increaseRangeCilia) {
		recordString += "increase-range-cilia ";
	}
	if (labelModeCilia) {
		recordString += "label-mode-cilia ";
	}
	
	recordString += "join-gap=" + joinGap + " ";

	recordString += "exclude=[" + excludeSelection + "] ";

	recordString += "minimum-size-intensity=" + minRestSize + " ";
	if (increaseRangeRegions) {
		recordString += "increase-range-intensity ";
	}

	if (skeletonize) {
		recordString += "skeleton ";
	}
	recordString += "gauss-xy=" + dfDialog.format(gXY) + " ";
	recordString += "gauss-z=" + dfDialog.format(gZ) + " ";

	recordString += "distance-curvature=" + dfDialog.format(refDistance) + " ";

	// NEW from v0.2.2 on: Time-lapse BB tracking
	recordString += "tl-tracking=[" + timelapseTrackingSelection + "] ";
	recordString += "max-tracking-distance=" + dfDialog.format(maxTrackingDistance) + " ";
	recordString += "max-frame-gap=" + maxFrameGap + " ";

	if (showGUIs) {
		recordString += "showgui" + " ";
	}

	recordString = recordString.substring(0, recordString.length() - 1);
	return recordString;
}

public void addFooter(OutputTextFile tp, Date currentDate){
	tp.append("");
	tp.append("Datafile was generated on " + FullDateFormatter2.format(currentDate) + " by the imagej plug-in '"+PLUGINNAME+"', " 
			+ "\u00a9 2017 - " + YearOnly.format(new Date()) + " Jan Niklas Hansen (jan.hansen@uni-bonn.de).");
	tp.append("The plug-in '"+PLUGINNAME+"' is distributed in the hope that it will be useful,"
			+ " but WITHOUT ANY WARRANTY; without even the implied warranty of"
			+ " MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.");
	tp.append("Plug-in version:	V"+PLUGINVERSION);	
}

public String getOneRowFooter(Date currentDate){
	String appendTxt = "		" + ("Datafile was generated on " + FullDateFormatter2.format(currentDate) + " by the imagej plug-in '"+PLUGINNAME+"', " 
			+ "\u00a9 2017 - " + YearOnly.format(new Date()) + " Jan Niklas Hansen (jan.hansen@uni-bonn.de).");
	appendTxt += "	" + ("The plug-in '"+PLUGINNAME+"' is distributed in the hope that it will be useful,"
			+ " but WITHOUT ANY WARRANTY; without even the implied warranty of"
			+ " MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.");
	appendTxt += "	" + ("Plug-in version: V"+PLUGINVERSION);
	return appendTxt;
}

private void addSettingsBlockToPanel(OutputTextFile tp, Date currentDate, Date startDate, String name, ImagePlus imp, 
		boolean measureC2local, boolean measureC3local, boolean measureBasalLocal, double [] intensityThresholds,
		int excludedCilia, int totalCilia, int excludedBBs, int totalBBs, int excludedCiliaBB){
	tp.append("Saving date:	" + FullDateFormatter.format(currentDate)
	+ "	Starting date:	" + FullDateFormatter.format(startDate));
	tp.append("image name:	" + name);
	tp.append("Image properties:	");
	tp.append("	Width:	" + dformat0.format(imp.getWidth()));
	tp.append("	Height:	" + dformat0.format(imp.getHeight())); 
	tp.append("	# slices:	" + dformat0.format(imp.getNSlices()));
	tp.append("");
	tp.append("Settings:	");
	tp.append("	Calibration [" + calibrationDimension + "/px]	" + dformat6.format(calibration) +	"	Voxel depth [" + calibrationDimension + "]	" + dformat6.format(voxelDepth));
	tp.append("	Time interval [" + timeUnit + "]:	" + dformat6.format(timePerFrame) + "	Frames per second:	" + dformat6.format(fps));
	tp.append("	Measure intensity A	" + measureC2local);
	tp.append("	Measure intensity B	" + measureC3local);
	tp.append("	Basal stain present	" + measureBasalLocal);
	tp.append("	Channels:	Reconstruction	" + dformat0.format(channelReconstruction));
	if(measureC2local){tp.append("		A	" + dformat0.format(channelC2));}else{tp.append("");}
	if(measureC3local){tp.append("		B	" + dformat0.format(channelC3));}else{tp.append("");}
	if(measureBasalLocal){
		//From v0.2.0 on more information is stored here
		String temp = "		basal stain	" + dformat0.format(basalStainC);
		if(segmentedBB) {
			temp += "	(segmented)	Assignment:	" + bbAssignmentSelection 
					+ "	max BB-Cilium-distance ["+ calibrationDimension +"]:	" + dformat6.format(maxDistanceBBCiliumEnd);
		}		
		tp.append(temp);
	}else{
		tp.append("");
	}
	if(measureBasalLocal && segmentedBB) {
		//New in V0.2.0 - BB detection requires more settings here.
		tp.append("	Minimum cilium size	" + dformat0.format(minSize) + "	Increase range for connecting cilia	" + increaseRangeCilia
				+ "	Minimum basal body size	" + dformat0.format(minBBSize) + "	Increase range for connecting basal bodies	" + increaseRangeBB);
		tp.append("	Label images (one value per object)	cilia	" + labelModeCilia 
				+ "	join pieces closer than [" + calibrationDimension + "]	" + dformat6.format(joinGap)
				+ "	basal bodies	" + labelModeBB);
		tp.append("	Additional filtering	" + excludeSelection + " excluded	# excluded:	" + excludedCilia + "	of total #:	" + totalCilia
				+ "	" + bbCiliaFilterSelection + " excluded	#BBs excluded:	" + excludedBBs + "	of total #BB:	" + totalBBs
				+ "	# cilia excluded by the BB filter:	" + excludedCiliaBB);	// New '# cilia excluded by the BB filter' in v0.2.2: at the end, so the importer reads both options as before
		}else{
		tp.append("	Minimum cilium size	" + dformat0.format(minSize) + "	Increase range for connecting cilia	" + increaseRangeCilia);
		tp.append("	Label images (one value per object)	cilia	" + labelModeCilia 
					+ "	join pieces closer than [" + calibrationDimension + "]	" + dformat6.format(joinGap));
		tp.append("	Additional filtering	" + excludeSelection + " excluded	# excluded:	" + excludedCilia + "	of total #:	" + totalCilia);
	}
	tp.append("	Minimum size of particles in A or B	" + dformat0.format(minRestSize) 
		+ "	Increase range for connecting particles in intensity regions	" + increaseRangeRegions);
	if(skeletonize) {
		tp.append("	Skeleton analysis - Gauss XY sigma	" + dformat6.format(gXY) 
		+ "	Skeleton analysis - reference distance [" + calibrationDimension + "]	" + dformat6.format(refDistance) );
	}else{tp.append("");}
	if(skeletonize) {tp.append("	Skeleton analysis - Gauss Z sigma	" + dformat6.format(gZ));}else{tp.append("");}
	tp.append("	Determined intensity thresholds:");
	if(measureC2local){tp.append("		A	" + dformat6.format(intensityThresholds[channelC2-1]));}else{tp.append("");}
	if(measureC3local){tp.append("		B	" + dformat6.format(intensityThresholds[channelC3-1]));}else{tp.append("");}
	if(measureBasalLocal){tp.append("		basal stain	" + dformat6.format(intensityThresholds[basalStainC-1]));}else{tp.append("");}
	tp.append("	Time-lapse cilia connection	" + timelapseTrackingSelection
			+ "	max tracking distance [" + calibrationDimension + "]	" + dformat6.format(maxTrackingDistance)
			+ "	max frame gap [frames]	" + dformat0.format(maxFrameGap)); //NEW from v0.2.2 on.
	tp.append("");
}

/**
 * Filter the indicated channel of the image and remove particles below the @param minSize threshold.
 * @param imp: Hyperstack image where one channel represents the )recording of the volume of interest
 * @param c: defines the channel of the Hyperstack image imp, in which the information for the volume of interest is stored 1 < c < number of channels
 * @param particleLabel: the label for the volume of interest which is displayed in the progress dialog while obtaining object information
 * @param increaseRange: defines whether also diagonal pixels should be allowed while Flood Filling
 * */
void filterChannel(ImagePlus imp, int c, String particleLabel, int minSize, boolean increaseRange){	
	double seedValue = 0.0;	// New in v0.2.2: label value of the object being filled
	boolean labelsHere = false;	// New in v0.2.2: the intensity channels A and B never hold labels

	ImagePlus refImp = imp.duplicate();

	int nrOfPoints = 0;
	for(int z = 0; z < imp.getNSlices(); z++){
		for(int t = 0; t < imp.getNFrames(); t++){
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){	
					if(imp.getStack().getVoxel(x, y, imp.getStackIndex(c, z+1, t+1)-1) > 0.0){
						nrOfPoints++;
					}
				}
			}
		}		
	}
		
	ArrayList<ArrayList<PartPoint>> particles = new ArrayList<ArrayList<PartPoint>>((int)Math.round((double)nrOfPoints/(double)minSize));
	
	int pc100 = nrOfPoints/100; if (pc100==0){pc100 = 1;}
	int pc1000 = nrOfPoints/1000; if (pc1000==0){pc1000 = 1;}
	int floodFilledPc = 0, floodFilledPcOld = 0;
	int[][] floodNodes = new int[nrOfPoints][3];
	int floodNodeX, floodNodeY, floodNodeZ, floodNodeT, index = 0;
	ArrayList<PartPoint> preliminaryParticle;
	
	searchCells: for(int t = 0; t < imp.getNFrames(); t++){
		for(int z = 0; z < imp.getNSlices(); z++){
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){		
					if(imp.getStack().getVoxel(x, y, imp.getStackIndex(c, z+1, t+1)-1) > 0.0){	// v0.2.2: fix to frame t (was frame 1 for every t, so only frame 1 was filtered)
						preliminaryParticle = new ArrayList<PartPoint>();	// New in v0.2.2: no capacity (was every voxel left, per piece)
						preliminaryParticle.add(new PartPoint(x, y, z, t, refImp, c));

						imp.getStack().setVoxel(x, y, imp.getStackIndex(c, z+1, t+1)-1, 0.0);
						floodFilledPc++;
						
						//Floodfiller					
						floodNodeX = x;
						floodNodeY = y;
						floodNodeZ = z;
						floodNodeT = t;
						 
						index = 0;
						 
						floodNodes[0][0] = floodNodeX;
						floodNodes[0][1] = floodNodeY;
						floodNodes[0][2] = floodNodeZ;
	
						while (index >= 0){
							floodNodeX = floodNodes[index][0];
							floodNodeY = floodNodes[index][1];
							floodNodeZ = floodNodes[index][2];						
							index--;
							if ((floodNodeX > 0) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX-1;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ;
							}
							if ((floodNodeX < (imp.getWidth()-1)) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX+1;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ;
							}
							if ((floodNodeY > 0) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY-1,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY-1;
								floodNodes[index][2] = floodNodeZ;
							}
							if ((floodNodeY < (imp.getHeight()-1)) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY+1,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY+1;
								floodNodes[index][2] = floodNodeZ;
							}
							if ((floodNodeZ > 0) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY,floodNodeZ-1,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ-1;
							}
							if ((floodNodeZ < (imp.getNSlices()-1)) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY,floodNodeZ+1,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ+1;
							}
							if(increaseRange){
								// X, Y
								if ((floodNodeX > 0) && (floodNodeY > 0)  
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY-1,floodNodeZ,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY < (imp.getHeight()-1))
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY+1,floodNodeZ,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY > 0) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY-1,floodNodeZ,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ;
								}
								if ((floodNodeX > 0) && (floodNodeY < (imp.getHeight()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY+1,floodNodeZ,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ;
								}
								// Z-X
								if ((floodNodeX > 0) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ-1;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ-1;
								}
								if ((floodNodeX > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ+1;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ+1;
								} 
								// Z-Y
								if ((floodNodeY > 0) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY-1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ-1;
								}
								if ((floodNodeY < (imp.getHeight()-1)) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY+1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ-1;
								}
								if ((floodNodeY > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY-1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ+1;
								} 
								if ((floodNodeY < (imp.getHeight()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY+1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ+1;
								} 
								// X, Y - Z down
								if ((floodNodeX > 0) && (floodNodeY > 0) && (floodNodeZ > 0)  
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY-1,floodNodeZ-1,floodNodeT,
											refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ-1;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY+1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ-1;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY > 0) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY-1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ-1;
								}
								if ((floodNodeX > 0) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY+1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ-1;
								}
								// X, Y - Z up
								if ((floodNodeX > 0) && (floodNodeY > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY-1,floodNodeZ+1,floodNodeT,
											refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ+1;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY+1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ+1;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY-1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ+1;
								}
								if ((floodNodeX > 0) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY+1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ+1;
								}
							}
						}					
						//Floodfiller	
						preliminaryParticle.trimToSize();
						if(preliminaryParticle.size() >= minSize){
							particles.add(preliminaryParticle);						
						}else{
							preliminaryParticle.clear();
							preliminaryParticle.trimToSize();
						}
						
						if(floodFilledPc%(pc100)<pc1000){
							progress.updateBarText("Connecting " + particleLabel + " complete: " + dformat3.format(((double)(floodFilledPc)/(double)(nrOfPoints))*100) + "%");
							progress.addToBar(0.2*((double)(floodFilledPc-floodFilledPcOld)/(double)(nrOfPoints)));								
							floodFilledPcOld = floodFilledPc;
						}	
					}				
				}	
			}
			if(floodFilledPc==nrOfPoints){					
				break searchCells;
			}
		}
	}
	progress.updateBarText("Connecting " + particleLabel + " complete: " + dformat3.format(((double)(floodFilledPc)/(double)(nrOfPoints))*100) + "%");
	progress.addToBar(0.2*((double)(floodFilledPc-floodFilledPcOld)/(double)(nrOfPoints)));
	particles.trimToSize();
	
	refImp.changes = false;
	refImp.close();

	//write back to image
	{
		for(int j = 0; j < particles.size();j++){
			for(int i = 0; i < particles.get(j).size();i++){
				imp.getStack().setVoxel(particles.get(j).get(i).x,
						particles.get(j).get(i).y,
						imp.getStackIndex(c, particles.get(j).get(i).z+1, particles.get(j).get(i).t+1)-1,	// v0.2.2: fix, write-back to particle's frame (was 1)
						particles.get(j).get(i).intensity);
			}
		}
	}
}

/**
 * @deprecated since 23.04.2019
 * @return a container that contains lists, which each contain the points belonging to an individual cilia object
 * @param imp: Hyperstack image where one channel represents the recording of cilia
 * @param c: defines the channel of the Hyperstack image imp, in which the ciliary information is stored 1 < c < number of channels
 * */
ArrayList<ArrayList<CellPoint>> getCiliaObjects (ImagePlus imp, int c){
	double seedValue = 0.0;					// New in v0.2.2: label value of the object being filled
	boolean labelsHere = labelModeCilia;	// New in v0.2.2: one value per object in this channel?

	ImagePlus refImp = imp.duplicate();
	int nrOfPoints = 0;
	for(int z = 0; z < imp.getNSlices(); z++){
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){	
				if(imp.getStack().getVoxel(x, y, imp.getStackIndex(c, z+1, 1)-1) > 0.0){
					nrOfPoints++;
				}
			}
		}
	}
	
	ArrayList<ArrayList<CellPoint>> particles = new ArrayList<ArrayList<CellPoint>>((int)Math.round((double)nrOfPoints/(double)minSize));
	
	int pc100 = nrOfPoints/100; if (pc100==0){pc100 = 1;}
	int pc1000 = nrOfPoints/1000; if (pc1000==0){pc1000 = 1;}
	int floodFilledPc = 0, floodFilledPcOld = 0;
	int[][] floodNodes = new int[nrOfPoints][3];
	int floodNodeX, floodNodeY, floodNodeZ, index = 0;
//	boolean touchesXY, touchesZ;
	ArrayList<CellPoint> preliminaryParticle;
//	int [] sliceCounter = new int [imp.getNSlices()];
	
	searchCells: for(int z = 0; z < imp.getNSlices(); z++){
		for(int x = 0; x < imp.getWidth(); x++){
			for(int y = 0; y < imp.getHeight(); y++){		
				if(imp.getStack().getVoxel(x, y, imp.getStackIndex(c, z+1, 1)-1) > 0.0){
					seedValue = imp.getStack().getVoxel(x, y, imp.getStackIndex(c, z+1, 1)-1);	// New in v0.2.2: read BEFORE the fill zeroes this voxel
					
//					touchesXY = false;
//					touchesZ = false;
					
					preliminaryParticle = new ArrayList<CellPoint>();	// New in v0.2.2: no capacity to speed up (was every voxel left, per piece)
					preliminaryParticle.add(new CellPoint(x, y, z, 0, refImp, c));
					
					imp.getStack().setVoxel(x, y, imp.getStackIndex(c, z+1, 1)-1, 0.0);
					
//					if(x == 0 || x == imp.getWidth()-1)	touchesXY = true;
//					if(y == 0 || y == imp.getHeight()-1)	touchesXY = true;
//					if(z == 0 || z == imp.getNSlices()-1)	touchesZ = true;
					
					floodFilledPc++;
					
					//Floodfiller					
					floodNodeX = x;
					floodNodeY = y;
					floodNodeZ = z;
					 
					index = 0;
					 
					floodNodes[0][0] = floodNodeX;
					floodNodes[0][1] = floodNodeY;
					floodNodes[0][2] = floodNodeZ;
					
					while (index >= 0){
						floodNodeX = floodNodes[index][0];
						floodNodeY = floodNodes[index][1];
						floodNodeZ = floodNodes[index][2];						
						index--;
						if ((floodNodeX > 0) 
								&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, 1)-1), seedValue, labelsHere)){
							
							preliminaryParticle.add(new CellPoint(floodNodeX-1,floodNodeY,floodNodeZ,0,
									refImp, c));
							imp.getStack().setVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, 1)-1, 0.0);
							
//							if(floodNodeX-1 == 0 || floodNodeX-1 == imp.getWidth()-1)		touchesXY = true;
//							if(floodNodeY == 0 || 	floodNodeY == imp.getHeight()-1)	touchesXY = true;
//							if(floodNodeZ == 0 || 	floodNodeZ == imp.getNSlices()-1)	touchesZ = true;
							
							index++;
							floodFilledPc++;
							
							floodNodes[index][0] = floodNodeX-1;
							floodNodes[index][1] = floodNodeY;
							floodNodes[index][2] = floodNodeZ;
						}
						if ((floodNodeX < (imp.getWidth()-1)) 
								&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, 1)-1), seedValue, labelsHere)){
							
							preliminaryParticle.add(new CellPoint(floodNodeX+1,floodNodeY,floodNodeZ,0, refImp, c));
							imp.getStack().setVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, 1)-1, 0.0);
							
//							if(floodNodeX+1 == 0 || floodNodeX+1 == imp.getWidth()-1)	touchesXY = true;
//							if(floodNodeY == 0 || 	floodNodeY == imp.getHeight()-1)	touchesXY = true;
//							if(floodNodeZ == 0 || 	floodNodeZ == imp.getNSlices()-1)	touchesZ = true;
							
							index++;
							floodFilledPc++;
							
							floodNodes[index][0] = floodNodeX+1;
							floodNodes[index][1] = floodNodeY;
							floodNodes[index][2] = floodNodeZ;
						}
						if ((floodNodeY > 0) 
								&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, 1)-1), seedValue, labelsHere)){
							
							preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY-1,floodNodeZ,0, refImp, c));
							imp.getStack().setVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, 1)-1, 0.0);
							
//							if(floodNodeX == 0 || 	floodNodeX == imp.getWidth()-1)	touchesXY = true;
//							if(floodNodeY-1 == 0 || floodNodeY-1 == imp.getHeight()-1)	touchesXY = true;
//							if(floodNodeZ == 0 || 	floodNodeZ == imp.getNSlices()-1)	touchesZ = true;
							
							index++;
							floodFilledPc++;
							
							floodNodes[index][0] = floodNodeX;
							floodNodes[index][1] = floodNodeY-1;
							floodNodes[index][2] = floodNodeZ;
						}
						if ((floodNodeY < (imp.getHeight()-1)) 
								&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, 1)-1), seedValue, labelsHere)){
							
							preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY+1,floodNodeZ,0, refImp, c));
							imp.getStack().setVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, 1)-1, 0.0);
							
//							if(floodNodeX == 0 || 	floodNodeX == imp.getWidth()-1)	touchesXY = true;
//							if(floodNodeY+1 == 0 || floodNodeY+1 == imp.getHeight()-1)	touchesXY = true;
//							if(floodNodeZ == 0 ||	floodNodeZ == imp.getNSlices()-1)	touchesZ = true;
							
							index++;
							floodFilledPc++;
							
							floodNodes[index][0] = floodNodeX;
							floodNodes[index][1] = floodNodeY+1;
							floodNodes[index][2] = floodNodeZ;
						}
						if ((floodNodeZ > 0) 
								&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, 1)-1), seedValue, labelsHere)){
							
							preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY,floodNodeZ-1,0, refImp, c));
							imp.getStack().setVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, 1)-1, 0.0);
							
//							if(floodNodeX == 0 || 	floodNodeX == imp.getWidth()-1)	touchesXY = true;
//							if(floodNodeY == 0 || 	floodNodeY == imp.getHeight()-1)	touchesXY = true;
//							if(floodNodeZ-1 == 0 || floodNodeZ-1 == imp.getNSlices()-1)	touchesZ = true;
							
							index++;
							floodFilledPc++;
							
							floodNodes[index][0] = floodNodeX;
							floodNodes[index][1] = floodNodeY;
							floodNodes[index][2] = floodNodeZ-1;
						}
						if ((floodNodeZ < (imp.getNSlices()-1)) 
								&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, 1)-1), seedValue, labelsHere)){
							
							preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY,floodNodeZ+1,0, refImp, c));
							imp.getStack().setVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, 1)-1, 0.0);
							
//							if(floodNodeX == 0 || 	floodNodeX == imp.getWidth()-1)		touchesXY = true;
//							if(floodNodeY == 0 || 	floodNodeY == imp.getHeight()-1)	touchesXY = true;
//							if(floodNodeZ+1 == 0 || floodNodeZ+1 == imp.getNSlices()-1)	touchesZ = true;
							
							index++;
							floodFilledPc++;
							
							floodNodes[index][0] = floodNodeX;
							floodNodes[index][1] = floodNodeY;
							floodNodes[index][2] = floodNodeZ+1;
						}  
					}					
					//Floodfiller
					preliminaryParticle.trimToSize();
//					saveParticle = true;
//					if(excludeSelection.equals(excludeOptions[1])){
//						if(touchesXY)	saveParticle = false;
//					}else if(excludeSelection.equals(excludeOptions[2])){
//						if(touchesXY)	saveParticle = false;
//						if(touchesZ)	saveParticle = false;
//					}else if(excludeSelection.equals(excludeOptions[3])){
//						if(touchesXY)	saveParticle = false;
//						
//						//check where most points are located
//						Arrays.fill(sliceCounter, 0);
//						for(int i = 0; i < preliminaryParticle.size(); i++){
//							sliceCounter [preliminaryParticle.get(i).z] ++;
//						}
//						if(tools.getMaximumIndex(sliceCounter) == 0 ||
//								tools.getMaximumIndex(sliceCounter) == imp.getNSlices()-1){
//							saveParticle = false;
////							IJ.log("touches xy: " + (particles.size()+1));
//						}
//						
//					}
					
//					if(saveParticle 
//							&& preliminaryParticle.size() >= minPlaqueSize 
//							&& preliminaryParticle.size() <= maxPlaqueSize){
					if(preliminaryParticle.size() >= minSize){
//							&& preliminaryParticle.size() <= maxSize){
						particles.add(preliminaryParticle);
						
						//write back to image
//						for(int i = 0; i < preliminaryParticle.size();i++){
//							imp.getStack().setVoxel(preliminaryParticle.get(i).x,
//									preliminaryParticle.get(i).y, 
//									imp.getStackIndex(cPl, preliminaryParticle.get(i).z+1, 1)-1, 
//									preliminaryParticle.get(i).intensity);
//						}
					}else{
						preliminaryParticle.clear();
						preliminaryParticle.trimToSize();
					}

					if(floodFilledPc%(pc100)<pc1000){
						progress.updateBarText("Reconstruction of ciliary structures complete: " + dformat3.format(((double)(floodFilledPc)/(double)(nrOfPoints))*100) + "%");
						progress.addToBar(0.2*((double)(floodFilledPc-floodFilledPcOld)/(double)(nrOfPoints)));							
						floodFilledPcOld = floodFilledPc;
					}	
				}				
			}	
		}
		if(floodFilledPc==nrOfPoints){					
			break searchCells;
		}
	}				
	
	refImp.changes = false;
	refImp.close();
	
	progress.updateBarText("Reconstruction of ciliary structures complete: " + dformat3.format(((double)(floodFilledPc)/(double)(nrOfPoints))*100) + "%");
	progress.addToBar(0.2*((double)(floodFilledPc-floodFilledPcOld)/(double)(nrOfPoints)));
	
	particles.trimToSize();
	
	//write back to image
		{
			for(int j = 0; j < particles.size(); j++){
				for(int i = 0; i < particles.get(j).size(); i++){
					imp.getStack().setVoxel(particles.get(j).get(i).x,
						particles.get(j).get(i).y, 
						imp.getStackIndex(c, particles.get(j).get(i).z+1, 1)-1, 
						particles.get(j).get(i).intensity);
				}
			}
		}
	//write back to image
	return particles;
}//end getCiliaObjects

/**	joinParticles notes when one object holds more than this many pieces */
static final int JOIN_MANY_PIECES = 20;

/**	joinParticles notes when joining runs longer than this (ms) */
static final long JOIN_SLOW_MS = 60000;

/**
 *	Join pieces of one cilium that a gap splits, within one frame: two pieces join when their closest voxels lie within
 *	the gap (in the image's calibrated unit). In label mode only pieces with the same label join, otherwise any two.
 *	Pieces join in clusters (A joins C when both are close to B), and every piece can start a cluster, so pieces far
 *	from the first piece of a label still join each other (label values that repeat in an image).
 *
 *	The label-aware flood fill is a connectivity fill, so one label can come out in pieces, e.g. where ridge detection
 *	(CiliaQ Preparator V0.2.1) lets one filament pass under another and both halves keep their label. A gappy stain
 *	splits a cilium of a mask the same way.
 *
 *	A piece below the minimum cilium size joins only a piece at or above it: in a noisy mask, specks
 *	would otherwise chain into a few huge objects, whose skeletons can take hours. Small pieces still join a cilium,
 *	and through it each other. Users who want small pieces to join each other lower the minimum size.
 *	Two notes come during joining, so users can stop the run early: when one object holds more than JOIN_MANY_PIECES
 *	pieces, and when joining runs longer than JOIN_SLOW_MS.
 *	
 *	New in v0.2.2
 *
 *	@param particles	single-frame pieces, MODIFIED IN PLACE: joined pieces merged into the first piece of their cluster
 *	@param labels		one label value per piece, kept in step with particles
 *	@param byLabel		join only pieces with the same label (label mode)
 *	@param gap			the gap in the calibrated unit, above 0
 *	@param cal			the calibration, to convert distances in voxels into the gap's unit (1 per voxel where it is missing)
 *	@param minSize		the minimum cilium size in voxels: two pieces below it do not join each other
 *	@return				the number of pieces merged into another
 */
private int joinParticles(ArrayList<ArrayList<CellPoint>> particles, ArrayList<Double> labels,
		boolean byLabel, double gap, Calibration cal, int minSize){
	if(particles == null || labels == null || particles.size() != labels.size() || !(gap > 0.0)) {
		// nothing to join (or the lists out of step, which the fill never produces)
		return 0;
	}
	
	// Check for no calibration: 1 unit per pixel
	double pw = (cal == null || cal.pixelWidth <= 0) ? 1.0 : cal.pixelWidth;
	double ph = (cal == null || cal.pixelHeight <= 0) ? 1.0 : cal.pixelHeight;
	double pd = (cal == null || cal.pixelDepth <= 0) ? 1.0 : cal.pixelDepth;

	// Group by frame (and label): only pieces of one group are compared, e.g. key "0|17" = label 17 in the first frame.
	// Since pieces of different groups never meet, joining never crosses frames, and in label mode two cilia with
	// different labels never join, however close.
	HashMap<String, ArrayList<Integer>> groups = new HashMap<String, ArrayList<Integer>>();
	for(int i = 0; i < particles.size(); i++){
		// get(0) is safe: the fill keeps no empty piece; each piece lies in one frame, so its first voxel gives the frame
		String key = particles.get(i).get(0).t + (byLabel ? "|" + labels.get(i) : "");
		if(!groups.containsKey(key)) {
			groups.put(key, new ArrayList<Integer>());
		}
		groups.get(key).add(Integer.valueOf(i));
	}
	
	// Each piece's bounding box once, in voxels: {xMin, xMax, yMin, yMax, zMin, zMax}, for the two quick tests below
	// (final: the sort reads it in a lambda)
	final int [][] box = new int [particles.size()][];
	for(int i = 0; i < particles.size(); i++) {
		box [i] = boundingBox(particles.get(i));
	}
	
	// Union-find: each piece starts as its own cluster. findRoot gives a cluster's representative; unionRoots joins two
	// clusters and keeps the smaller index as root, so a joined cilium keeps the place (and the ID) of its first piece.
	int [] parent = new int [particles.size()];
	for(int i = 0; i < parent.length; i++) {
		parent [i] = i;
	}	
	double gap2 = gap * gap;	// distances are compared squared: no square root needed
	
	// the pieces per cluster (at its root), and the two notes for the user, each given once
	int [] pieces = new int [particles.size()];
	Arrays.fill(pieces, 1);
	boolean manyNoted = false, slowNoted = false;
	long start = System.currentTimeMillis(), pairs = 0;
	String unit = (cal == null) ? "" : " " + cal.getUnit();
	String gapText = dfDialog.format(gap).replaceAll("0+$", "").replaceAll("\\.$", "");	// as entered: 0.3, not 0.300000
	String lower = " If that is not intended, lower 'Join pieces closer than'"
			+ (byLabel ? " or check that the channel holds labels (one value per cilium)" : "")
			+ " and run again; closing the progress window stops the run after this image.";
	
	// Per group, every pair passes the tests from cheap to expensive:
	//	- sorted by xMin, the comparison of a piece stops at the first piece that starts beyond the gap in x (break);
	//	- the gap between the two bounding boxes (continue);
	//	- the closest voxel pair (withinGap), only for pairs not yet in one cluster.
	// Since the first two tests only exclude pairs that cannot lie within the gap, the result is the same as comparing
	// every voxel pair, only faster.
	for(ArrayList<Integer> g : groups.values()){
		// sorts the indices only: particles keeps its order, so the IDs do not change
		Collections.sort(g, (u, v) -> Integer.compare(box [u.intValue()][0], box [v.intValue()][0]));
		
		for(int a = 0; a < g.size(); a++){
			int i = g.get(a).intValue();
			for(int b = a + 1; b < g.size(); b++){
				// pieces before a were compared with i when they were i
				int j = g.get(b).intValue();
				
				// j starts (xMin) beyond the gap from i's right edge (xMax);
				// since g is sorted by xMin, so does every later piece
				if((box [j][0] - box [i][1]) * pw > gap) {
					// this and every later piece start beyond the gap in x
					break;
				}
				
				// the watchdog: joining runs unusually long (checked every 65536 pairs, skipped pairs included)
				if(!slowNoted && (++pairs & 0xFFFF) == 0 && System.currentTimeMillis() - start > JOIN_SLOW_MS) {
					progress.notifyMessageWithTaskNr("Joining pieces closer than " + gapText + unit + " is taking unusually long ("
							+ (System.currentTimeMillis() - start) / 1000 + " s so far, " + particles.size() + " pieces)." + lower,
							ProgressReporter.NOTIFICATION);
					slowNoted = true;
				}
				
				// two pieces below the minimum size do not join each other (see above)
				if(particles.get(i).size() < minSize && particles.get(j).size() < minSize) {
					continue;
				}
				
				// the gap between the boxes per axis (0 where they overlap), in the calibrated unit; since no voxel lies
				// outside its box, the closest voxels are at least this far apart
				double dx = axisGap(box [i][0], box [i][1], box [j][0], box [j][1]) * pw;
				double dy = axisGap(box [i][2], box [i][3], box [j][2], box [j][3]) * ph;
				double dz = axisGap(box [i][4], box [i][5], box [j][4], box [j][5]) * pd;
				
				if(dx*dx + dy*dy + dz*dz > gap2) {
					// the boxes are already too far apart
					continue;
				}
				
				// already in one cluster (e.g. both joined to a third piece): no need to compare voxels. Otherwise the
				// closest voxel pair decides; joining is transitive, so A and C join when both lie close to B.
				int ri = findRoot(parent, i), rj = findRoot(parent, j);
				if(ri != rj && withinGap(particles.get(i), particles.get(j), gap2, pw, ph, pd)){
					unionRoots(parent, i, j);
					int r = findRoot(parent, i);
					pieces [r] = pieces [ri] + pieces [rj];
					if(!manyNoted && pieces [r] > JOIN_MANY_PIECES) {
						progress.notifyMessageWithTaskNr("Joining pieces closer than " + gapText + unit + ": one object already holds"
								+ " more than " + JOIN_MANY_PIECES + " pieces. In a noisy image, joining can chain pieces into a few huge"
								+ " objects, whose skeletons can take very long." + lower, ProgressReporter.NOTIFICATION);
						manyNoted = true;
					}
				}
			}
		}
	}
	
	// merge each cluster into its root (its first piece in scan order) and drop the emptied entries
	return mergeByRoots(particles, labels, parent);
}

/**
 *	Link single-frame pieces across time by overlap (the classic connection, with the frame gap): a piece of frame t
 *	joins an earlier piece that covered one of its voxel positions (x, y, z) at most maxGap missing frames back, i.e. in
 *	frame t-1 ... t-1-maxGap. With gap 0 this is the classic fill across time (on the test time-lapse, _CQ.txt identical
 *	to the version before); with a gap, a cilium that vanishes for up to maxGap frames stays one object (the test cilium
 *	missing in one frame: 6 objects with gap 0, 5 with gap 1).
 *
 *	Overlap is checked at the same position only: a cilium that moves by more than its own width while it is missing
 *	is not linked, whatever the gap. Labels play no part, so labels numbered per frame (CiliaQ Preparator) need not
 *	match between frames.
 *
 *	New in v0.2.2
 *
 *	@param particles	single-frame pieces, MODIFIED IN PLACE: linked pieces merged into the first piece of their cilium
 *	@param labels		one label value per piece, only kept in step with particles
 *	@param maxGap		the number of frames a cilium may be missing and still be linked; 0 = neighbouring frames only
 *	@param imp			the image, for the size of one frame
 *	@return				the number of pieces merged into another
 */
private int linkFramesByOverlap(ArrayList<ArrayList<CellPoint>> particles, ArrayList<Double> labels,
		int maxGap, ImagePlus imp){
	int W = imp.getWidth(), H = imp.getHeight(), nFrames = imp.getNFrames();
	
	// The frame of each piece, and the pieces of each frame, so the frames can be handled in order.
	// get(0) gives the frame, since each piece lies in one frame (the fill runs per frame when linking follows)
	int [] frameOf = new int [particles.size()];
	ArrayList<ArrayList<Integer>> perFrame = new ArrayList<ArrayList<Integer>>(nFrames);
	for(int t = 0; t < nFrames; t++) {
		perFrame.add(new ArrayList<Integer>());
	}
	for(int i = 0; i < particles.size(); i++){
		frameOf [i] = particles.get(i).get(0).t;
		perFrame.get(frameOf [i]).add(Integer.valueOf(i));
	}
	
	// Per voxel position of one frame (x, y, z, without time): 1 + the piece that covered it last, 0 = never covered
	// (+1 since piece 0 is a valid index).The last piece is enough: since it was itself linked to every earlier piece
	// that position within the window, linking to it reaches them too; and if it lies outside the window, every earlier
	// one does as well.
	int [] lastPiece = new int [W * H * imp.getNSlices()];
	
	// Union-find, as in joinParticles: the smaller index stays the root, so a cilium keeps the ID of its first piece
	int [] parent = new int [particles.size()];
	for(int i = 0; i < parent.length; i++) {
		parent [i] = i;
	}
	
	for(int t = 0; t < nFrames; t++){
		// 1) Link: each voxel looks up the piece that covered its position last. Since the map still holds earlier
		//    frames only (this frame is marked in step 2), a piece never links to a piece of its own frame.
		for(Integer ii : perFrame.get(t)){
			int i = ii.intValue();
			for(CellPoint p : particles.get(i)){
				int last = lastPiece [(p.z * H + p.y) * W + p.x] - 1;	// -1: back to the piece index, -1 = none
				
				// within the window: gap 0 = the previous frame only; gap 1 = also two frames back (one frame missing)
				if(last >= 0 && t - frameOf [last] <= maxGap + 1) {
					unionRoots(parent, i, last);
				}
			}
		}
		
		// 2) Mark: this frame's pieces become the last piece at their positions, for the frames that follow.
		//    Positions this frame does not cover keep their older piece, so a cilium missing in this frame
		//    can still be found by a later frame (within the gap).
		for(Integer ii : perFrame.get(t)){
			int i = ii.intValue();
			for(CellPoint p : particles.get(i)) {
				lastPiece [(p.z * H + p.y) * W + p.x] = i + 1;
			}
		}
	}
	
	// merge each cilium into its root (its first piece in scan order) and drop the emptied entries
	return mergeByRoots(particles, labels, parent);
}

/**
 *	Link pieces by their label value ("by label ID"): all pieces with the same label are one cilium, in every frame
 *	and whatever the distance. For label images whose IDs stay the same over time (e.g. from a tracking tool).
 *
 *	Since only the label counts:
 *		- the frame gap does not apply: a label missing for some frames and back later is still one cilium;
 *		- pieces of one label in the same frame are pooled into its cilium too (labelSplitNotes
 *		  notifies when they lie beyond the join gap, since their skeleton is then not bridged);
 *		- two different cilia with the same label value are merged, however far apart. Labels numbered per frame, as
 *		  CiliaQ Preparator writes them (every frame starts at 1), therefore need classic or linear assignment.
 *
 *	Called in label mode only (getCiliaObjectsTimelapse); without it the labels hold no IDs, and the frames are linked
 *	by overlap instead.
 *
 *	New in v0.2.2
 *
 *	@param particles	single-frame pieces, MODIFIED IN PLACE: pieces with the same label merged into the first of them
 *	@param labels		one label value per piece: decides which pieces are linked; kept in step with particles
 *	@return				the number of pieces merged into another
 */
private int linkFramesByLabel(ArrayList<ArrayList<CellPoint>> particles, ArrayList<Double> labels){
	// Per label value, the first piece that carries it. Since particles is in scan order, frame by frame, this is the
	// piece that appears earliest; the cilium keeps its place, so the IDs follow the first appearance of each label.
	HashMap<Double, Integer> first = new HashMap<Double, Integer>();
	
	// Union-find, as in joinParticles. Every piece joins the first piece of its label directly, so each cluster is one
	// step deep; the union-find is used to share mergeByRoots with the other two methods.
	int [] parent = new int [particles.size()];
	for(int i = 0; i < parent.length; i++){
		parent [i] = i;
		Integer f = first.get(labels.get(i));
		if(f == null) {
			// a new label: this piece is its first
			first.put(labels.get(i), Integer.valueOf(i));
		}else{
			// a known label: join its first piece; since f < i, f stays the root
			unionRoots(parent, i, f.intValue());
		}
	}
	// merge each label into its first piece and drop the emptied entries
	return mergeByRoots(particles, labels, parent);
}

/**
 *	Union-find: the root of i, i.e. the representative of the cluster (cilium) that piece i belongs to.
 *	With path halving: on the way up, each visited piece is pointed to its grandparent. Since this only shortcuts to a
 *	piece higher up in the same cluster, the root stays the same, but the chains get shorter with every call, so even
 *	hundreds of specks joined into one object stay fast.
 *
 *	New in v0.2.2
 */
private int findRoot(int [] parent, int i){
	while(parent [i] != i){				// a root points to itself
		parent [i] = parent [parent [i]];	// shortcut: point i to its grandparent
		i = parent [i];					// and continue from there
	}
	return i;
}

/**
 *	Union-find: join the clusters of i and j. The smaller root becomes the root of both, so:
 *		- the root is always the smallest index of its cluster, i.e. its first piece in scan order;
 *		- a joined object keeps the place, and so the ID, of its first piece (on the test time-lapse with frame gap 0,
 *		  _CQ.txt identical to the version before, IDs included);
 *		- every pointer points to a smaller or equal index.
 *	If i and j are already in one cluster, nothing changes.
 *
 *	New in v0.2.2
 */
private void unionRoots(int [] parent, int i, int j){
	int ri = findRoot(parent, i), rj = findRoot(parent, j);
	if(ri < rj) {
		parent [rj] = ri;		// the larger root points to the smaller one
	}else if(rj < ri) {
		parent [ri] = rj;
	}
	// ri == rj: already one cluster
}

/**
 *	Merge every piece into the root of its cluster, then drop the emptied entries. The root is the smallest index of its
 *	cluster, so it comes first and is never emptied; the surviving cilia keep the scan order, and so their IDs. A merged
 *	cilium keeps the label of its first piece.
 *
 *	New in v0.2.2
 *
 *	@param particles	the pieces, MODIFIED IN PLACE: merged into their roots, emptied entries removed
 *	@param labels		one label value per piece, kept in step with particles
 *	@param parent		the union-find of the pieces (findRoot, unionRoots)
 *	@return				the number of pieces merged into another
 */
private int mergeByRoots(ArrayList<ArrayList<CellPoint>> particles, ArrayList<Double> labels, int [] parent){
	int merged = 0;
	for(int i = 0; i < particles.size(); i++){
		int r = findRoot(parent, i);
		if(r != i){	// not a root: its voxels go to the root (a root's findRoot is itself, so a root is never emptied)
			particles.get(r).addAll(particles.get(i));
			particles.get(i).clear();
			merged++;
		}
	}
	// drop the emptied entries back to front: removing entry i shifts only the entries after it, which were already
	// checked. Only merged pieces are empty, since the fill keeps no empty piece.
	for(int i = particles.size() - 1; i >= 0; i--){
		if(particles.get(i).isEmpty()){
			particles.remove(i);
			labels.remove(i);
		}
	}
	particles.trimToSize();
	return merged;
}


/**
 *	The size rule of the cilia, as in the fill: an object is removed when one of the frames where it is present holds
 *	fewer than minSize voxels. Frames where it is absent do not count. In a single-frame image: an object below the
 *	minimum size is removed.
 *
 *	Applied after joining and linking, so a cilium is judged by all its pieces together: a cilium that a gappy stain
 *	splits into pieces each below the minimum size is kept when the joined cilium is large enough.
 *
 *	The rule removes the whole object, also when only one frame is too small: e.g. a speck that links to a cilium where
 *	the cilium is missing (within the frame gap) removes that cilium from the results.
 *
 *	New in v0.2.2
 *
 *	@param particles	the objects, MODIFIED IN PLACE: objects below the minimum size in a frame removed
 *	@param labels		one label value per object, kept in step with particles
 *	@param frameCounter	an array of length (number of frames), reused for the counts; its content is overwritten
 *	@return				the number of objects removed
 */
private int applySizeRule(ArrayList<ArrayList<CellPoint>> particles, ArrayList<Double> labels, int [] frameCounter){
	int nDropped = 0;
	// back to front: removing object i shifts only the objects after it, which were already checked
	for(int i = particles.size() - 1; i >= 0; i--){
		// the voxels of this object per frame
		Arrays.fill(frameCounter, 0);
		for(int p = 0; p < particles.get(i).size(); p++) {
			frameCounter [particles.get(i).get(p).t]++;
		}
		
		for(int t = 0; t < frameCounter.length; t++){
			// too small in a frame where it is present (0 = absent there, which does not count): remove the whole object
			if(frameCounter [t] < minSize && frameCounter [t] != 0){
				particles.remove(i);
				labels.remove(i);
				nDropped++;
				break;	// removed: no need to check its other frames
			}
		}
	}
	particles.trimToSize();
	return nDropped;
}

/**
 *	Notification when label mode is on for the cilia but the reconstruction channel likely holds intensities. Label mode
 *	joins only neighbouring voxels with the same value; since neighbouring voxels of an intensity image almost always
 *	differ, the fill stops after one or a few voxels, and most pieces are below the minimum size. A label image gives
 *	one piece of normal size per cilium. Without this note the user gets almost no cilia and no error: the size rule
 *	removes the single voxels silently.
 *
 *	A label image whose objects are mostly tiny (e.g. a thresholded and labelled image with noise, one label per speck)
 *	also triggers the note; it says "may", and the user can ignore it then.
 *
 *	Works on counts only, so the fill need not keep the pieces: an intensity stack of 256 x 256 x 10 in label mode
 *	(about 260,000 single-voxel pieces) gave the note at a peak of 389 MB, where keeping every piece needed 404 MB.
 *
 *	New in v0.2.2
 *
 *	@param nPieces	all pieces of the fill, counted during the fill (before joining and the size rule)
 *	@param nSmall	those of them below the minimum cilium size
 *	@return			true when it notified; the caller then skips labelSplitNotes, since in an intensity image every grey
 *					value acts as a label and occurs in many places, so "labels in several pieces" would be wrong advice
 */
private boolean labelPieceNotes(int nPieces, int nSmall){
	// at least 10 pieces: with very few, the share says little (e.g. three cilia and two small fragments are 40 % small).
	// More than half small: 2 * nSmall > nPieces, written without division.
	if(nPieces >= 10 && 2 * nSmall > nPieces){
		progress.notifyMessageWithTaskNr("Label mode is on for the cilia, but " + nSmall + " of the " + nPieces
				+ " pieces are smaller than the minimum cilium size: the reconstruction channel may hold intensities, not"
				+ " labels. Label mode joins only voxels of the same value, so it splits an intensity image into single"
				+ " voxels. Turn label mode off unless the channel in fact holds labels.",
		ProgressReporter.NOTIFICATION);
		return true;
	}
	return false;
}

/**
 *	Notification when a label lies in several pieces in one frame, since such a cilium is measured differently than
 *	the user may expect:
 *		- "by label ID": the pieces of a label are pooled into its cilium, but pieces beyond the join
 *		  gap get no skeleton bridges, so the cilium may get no length or profiles in that frame (test: a label cut in two,
 *		  pieces 2 um apart, join gap 1.0: no length in frame 1 and this note; join gap 3.0: joined, length, no note);
 *		- otherwise, with joining off: the pieces are analysed as separate cilia, and the note suggests the join gap
 *		  (test: a label in two pieces 50 um apart, single frame: "1 label(s) lie in several pieces ...").
 *	With joining on and without "by label ID", nothing is notified: the pieces still apart lie beyond the gap the user
 *	chose, so they stay separate on purpose (e.g. one label value on two cilia far apart).
 *
 *	The count is per label and frame: a label split in three frames counts three times. Label values that repeat on
 *	different cilia (e.g. CiliaQ Preparator's 32-bit labels wrap at 2^24) count too; following the advice is still safe,
 *	since the join gap joins only pieces within the gap.
 *
 *	New in v0.2.2
 *
 *	@param particles	the pieces after joining, each in one frame (both calls run before linking)
 *	@param labels		one label value per piece, in step with particles
 *	@param byLabelLinks	the frames are linked by label ID ("by label ID" in label mode)
 */
private void labelSplitNotes(ArrayList<ArrayList<CellPoint>> particles, ArrayList<Double> labels, boolean byLabelLinks){
	// joining on, not by label ID: pieces of a label still apart lie beyond the chosen gap, so they stay apart on purpose
	if(!byLabelLinks && joinGap > 0.0) {
		return;
	}
	
	// pieces per label and frame, e.g. key "1|5.0" = label 5 in frame 1. get(0) gives the frame, since every piece lies
	// in one frame here (called before linking)
	HashMap<String, Integer> piecesPerLabel = new HashMap<String, Integer>();
	for(int i = 0; i < particles.size(); i++){
		String key = particles.get(i).get(0).t + "|" + labels.get(i);
		Integer n = piecesPerLabel.get(key);
		piecesPerLabel.put(key, n == null ? 1 : n.intValue() + 1);
	}
	
	// the labels in several pieces, counted per frame
	int nSplit = 0;
	for(Integer n : piecesPerLabel.values())	if(n.intValue() > 1)	nSplit++;
	if(nSplit == 0)	return;
	if(byLabelLinks){
		// counted after joining, so the pieces still apart lie beyond the join gap. The skeleton bridges
		// measure differently (the part of a line between skeleton end points outside the upscaled object), so a pair
		// within about one voxel beyond the gap can still be bridged: hence "may"
		progress.notifyMessageWithTaskNr("'" + timelapseTrackingOptions[2] + "': " + nSplit + " label(s) lie in several"
				+ " pieces in a frame, farther apart than 'Join pieces closer than' (now " + joinGap + "). Each label is"
				+ " pooled into one cilium, but pieces beyond the join gap are not bridged in its skeleton, so these cilia"
				+ " may get no length or profiles in those frames. Raise the join gap to bridge them.", ProgressReporter.NOTIFICATION);
	}else{
		// joining off: each piece becomes its own cilium (single frame, linear assignment); the join gap would join them
		progress.notifyMessageWithTaskNr(nSplit + " label(s) lie in several pieces and are analysed as separate cilia"
				+ " (e.g. CiliaQ Preparator's ridge detection splits a filament where another passes over it). To join"
				+ " them, set 'Join pieces closer than' (e.g. 20 in a micron-calibrated image).", ProgressReporter.NOTIFICATION);
	}
}

/**
 *	Is the closest pair of voxels of two pieces within the tolerance?
 *	The bounding boxes are tested first: they reject "same label, far apart"
 *	at once, while the exact test is O(n*m) in voxels.
 *	
 *	New in v0.2.2
 */
private boolean withinGap(ArrayList<CellPoint> a, ArrayList<CellPoint> b, double gap2, double pw, double ph, double pd){
	int [] ba = boundingBox(a), bb = boundingBox(b);
	double dx = axisGap(ba [0], ba [1], bb [0], bb [1]) * pw;
	double dy = axisGap(ba [2], ba [3], bb [2], bb [3]) * ph;
	double dz = axisGap(ba [4], ba [5], bb [4], bb [5]) * pd;
	if(dx*dx + dy*dy + dz*dz > gap2)	return false;	// the boxes are already too far apart
	for(int i = 0; i < a.size(); i++){
		CellPoint q = a.get(i);
		for(int j = 0; j < b.size(); j++){
			CellPoint r = b.get(j);
			double ex = (q.x - r.x) * pw, ey = (q.y - r.y) * ph, ez = (q.z - r.z) * pd;
			if(ex*ex + ey*ey + ez*ez <= gap2)	return true;
		}
	}
	return false;
}

/**	{ minX, maxX, minY, maxY, minZ, maxZ } of a piece.
 * 
 * 	New in v0.2.2
 */
private int [] boundingBox(ArrayList<CellPoint> a){
	int[] b = new int[] { Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE,
			Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE };
	for (int i = 0; i < a.size(); i++) {
		CellPoint q = a.get(i);
		if (q.x < b[0]) b[0] = q.x;
		if (q.x > b[1]) b[1] = q.x;
		if (q.y < b[2]) b[2] = q.y;
		if (q.y > b[3]) b[3] = q.y;
		if (q.z < b[4]) b[4] = q.z;
		if (q.z > b[5]) b[5] = q.z;
	}
	return b;
}

/**	
 *	The gap between two closed intervals in voxels, 0 when they overlap.
 *
 *	New in v0.2.2 
 */
private int axisGap(int aMin, int aMax, int bMin, int bMax){
	if(bMin > aMax)	return bMin - aMax;
	if(aMin > bMax)	return aMin - bMax;
	return 0;
}

/**
 *	New in v0.2.2: one line in _CQ.txt when the image had notifications or errors, naming its log file, e.g.
 *	"Notifications for this image:	at least 2	see	sep5_CQ_log.txt". Since a user who opens only _CQ.txt would
 *	otherwise not know that the image has notes, this line points to them.
 *
 *	The line comes after the results table and a blank line: since the R scripts read the table up to the first blank
 *	line, they are not affected.
 *
 *	"At least": since _CQ.txt is saved before the other output files, a note that comes later (e.g. an IO error on
 *	saving another file) is in the log file only.
 *
 *	@param tp			the _CQ.txt being written
 *	@param filePrefix	<dir><name>_CQ[_<date>]; the log file is filePrefix + "_log.txt"
 */
private void appendNotesLine(OutputTextFile tp, String filePrefix){
	int n = progress.getNotes(currentTask).size();
	if(n == 0)	return;
	tp.append("");
	tp.append("Notifications for this image:	at least " + n + "	see	" + new File(filePrefix + "_log.txt").getName());
}

/**
 *	New in v0.2.2: the notifications and errors of this image in <name>_CQ_log.txt, next to its _CQ.txt, with the text
 *	as the progress window and the console show it. Written only when there is at least one, so an image without
 *	notes gets no extra file (tested).
 *
 *	The file holds:
 *		- a header line with the image name;
 *		- one line per note, oldest first: "ERROR<tab>message" or "WARNING<tab>message", as in CiliaQ Preparator;
 *		- the usual footer.
 *
 *	@param filePrefix	<dir><name>_CQ[_<date>] (logPrefix, S8); the file is filePrefix + "_log.txt"
 *	@param imageName	the image's file name, for the header line
 */
private void writeImageLog(String filePrefix, String imageName){
	java.util.List<String> notes = progress.getNotes(currentTask);
	
	if(filePrefix == null || notes.isEmpty()) {
		// no notes to write
		return;
	}
	
	OutputTextFile tl = new OutputTextFile("");
	
	tl.append("Notifications and errors for image:	" + imageName);
	
	for(String n : notes) {
		tl.append(n);
	}
	
	addFooter(tl, new Date());
	
	if(!tl.saveAndFinish(filePrefix + "_log.txt")){
		progress.notifyMessage("IO ERROR when saving file " + filePrefix + "_log.txt", ProgressReporter.ERROR);
	}
}

/**
 *	New in v0.2.2: the join gap must be a finite number of 0 or more; anything else (NaN, infinite, negative) switches
 *	joining off, with a log line naming the source and the value. Since a typo in a macro then does not stop a batch
 *	run, the other images are still analysed; the settings line in _CQ.txt shows join gap 0, so the run stays
 *	traceable (tested: join-gap=NaN and join-gap=-3 in a macro: off, with the log line).
 *
 *	@param gap		the value as read
 *	@param source	where it came from, for the log line: "dialog", "macro" or "settings file"
 *	@return			gap, or 0 (off)
 */
private double checkedJoinGap(double gap, String source){
	if(gap >= 0.0 && !Double.isInfinite(gap)) {
		return gap;
	}
	IJ.log(PLUGINNAME + ": the join gap from the " + source + " (" + gap + ") is not a number of 0 or more - joining is off.");
	return 0.0;
}

/**
 *	New in v0.2.2: the join gap from its cell in a settings file. A decimal comma is read as a point, so a file written
 *	on a system with a decimal comma still loads (e.g. "1,5" = 1.5). A cell that is not a valid gap switches joining
 *	off, with a log line, instead of stopping the import (tested: "20.000000" and "1,5" read; empty, "abc" and "NaN":
 *	off, with the log line).
 *
 *	@param cell	the cell as read
 *	@return		the gap, or 0 (off)
 */
private double parseJoinGap(String cell){
	try{
		return checkedJoinGap(Double.parseDouble(cell.trim().replace(",", ".")),
				"settings file");
	}catch(NumberFormatException e){
		IJ.log(PLUGINNAME + ": the join gap in the settings file ('" + cell + "') is not a number - joining is off.");
		return 0.0;
	}
}

/**
 *	New in v0.2.2: read the settings line "Label images (one value per object)" of a settings file: label mode of the
 *	cilia and the basal bodies, and the join gap. The line holds tab-separated pairs of a key and its value, e.g.
 *	"Label images (one value per object)	cilia	true	join pieces closer than [micron]	20.000000	basal bodies	false".
 *		- The join gap's key carries the file's unit, so it is matched by its start. The number is used as it is,
 *		  without conversion; a different unit is notified per image, from the file's calibration line.
 *		- A key missing from the line keeps its default: a file written without a basal-body channel has no
 *		  "basal bodies". Files before v0.2.2 have no such line at all, so label mode and joining stay off, as then.
 *
 *	@param line	the whole line as read from the file
 */
void readLabelLine(String line){
	String [] lv = line.split("	");
	for(int li = 0; li + 1 < lv.length; li++){
		if(lv [li].equals("cilia"))	labelModeCilia = lv [li+1].contains("true");
		if(lv [li].startsWith("join pieces closer than"))	joinGap = parseJoinGap(lv [li+1]);
		if(lv [li].equals("basal bodies"))	labelModeBB = lv [li+1].contains("true");
	}
	IJ.log("Label mode cilia = " + labelModeCilia + ", join gap = " + joinGap + ", basal bodies = " + labelModeBB);
}

/**
 *	New in v0.2.2: the same length unit, whatever the spelling. Since ImageJ and microscope files spell micron in
 *	several ways, a settings file from an image calibrated in micro sign + m applied to images in "micron" would
 *	otherwise notify a different unit on every image of the batch, although the distances fit.
 *		- The same: micron, microns, um, and "m" after the micro sign (U+00B5) or the Greek mu (U+03BC), in any case.
 *		- Different: any other unit, e.g. nm.
 *	Tested: micron = micro sign + m = um = microns; micron and nm differ.
 *
 *	@return	true if both name the same unit
 */
static boolean sameUnit(String a, String b){
	if(a == null || b == null) {
		return a == b;
	}
	return normalUnit(a).equals(normalUnit(b));
}

/**	New in v0.2.2: the unit in lower case, with every spelling of micron as "micron" (see sameUnit). */
private static String normalUnit(String u){
	String s = u.trim().toLowerCase(Locale.ROOT);
	String micro = String.valueOf((char) 0x00B5) + "m", mu = String.valueOf((char) 0x03BC) + "m";	// by code point: the source stays ASCII
	if(s.equals("micron") || s.equals("microns") || s.equals(micro) || s.equals(mu) || s.equals("um")) {
		return "micron";
	}
	return s;
}

/**
 * used since 23.04.2019
 * @return a container that contains lists, which each contain the points belonging to an individual plaque object
 * @param imp: Hyperstack image where one channel represents the recording of cilia
 * @param c: defines the channel of the Hyperstack image imp, in which the ciliary information is stored 1 < c < number of channels
 * @param increaseRange: defines whether also diagonal pixels should be allowed while Flood Filling
 * @param connectAcrossT: if true, objects are connected across time (classic mode); if false, each object stays within one frame and
 * can later be connected through other means. 
 * */
ArrayList<ArrayList<CellPoint>> getCiliaObjectsTimelapse (ImagePlus imp, int c, boolean increaseRange, boolean connectAcrossT){
	double seedValue = 0.0;	// New in v0.2.2: label value of the object being filled
	boolean labelsHere = labelModeCilia;	// New in v0.2.2: one value per object in this channel?
	
	/*	New in v0.2.2: a time-lapse connected across time is filled frame by frame and linked afterwards
	 *	(linkFramesByOverlap, linkFramesByLabel) when labels, a frame gap, joining or "by label ID" are used; otherwise
	 *	the classic fill across time, as before. The size rule waits until after joining and linking. */
	boolean byLabelId = timelapseTrackingSelection.equals(timelapseTrackingOptions[2]);
	boolean linkLater = connectAcrossT && imp.getNFrames() > 1
			&& (labelModeCilia || maxFrameGap > 0 || joinGap > 0.0 || byLabelId);
	boolean fillAcrossT = connectAcrossT && !linkLater;
	boolean deferSizeRule = linkLater || joinGap > 0.0;
	int labelPieces = 0, labelSmallPieces = 0;	// New in v0.2.2: counted during the fill for the label notes (labelPieceNotes)

	ImagePlus refImp = imp.duplicate();
	int nrOfPoints = 0;
	for(int z = 0; z < imp.getNSlices(); z++){
		for(int t = 0; t < imp.getNFrames(); t++){
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){	
					if(imp.getStack().getVoxel(x, y, imp.getStackIndex(c, z+1, t+1)-1) > 0.0){
						nrOfPoints++;
					}
				}
			}
		}		
	}
	
	if(nrOfPoints == imp.getNSlices()*imp.getNFrames()*imp.getWidth()*imp.getHeight()) {
		progress.notifyMessageWithTaskNr("ERROR - The channel set for reconstruction (" + c 
				+ ") is not segmented - make sure to set the correct channel nr for reconstruction in the preferences!", ProgressReporter.ERROR);
		return new ArrayList<ArrayList<CellPoint>>(0);
	}else if(nrOfPoints == 0) {
		progress.notifyMessageWithTaskNr("ERROR - The channel for reconstruction (" + c 
				+ ") is empty - no cilia found!", ProgressReporter.ERROR);
		return new ArrayList<ArrayList<CellPoint>>(0);
	}
		
	ArrayList<ArrayList<CellPoint>> particles = new ArrayList<ArrayList<CellPoint>>((int)Math.round((double)nrOfPoints/(double)minSize));
	ArrayList<Double> particleLabels = new ArrayList<Double>();	// New in v0.2.2: the label value of each particle, in step with particles

	
	int pc100 = nrOfPoints/100; if (pc100==0){pc100 = 1;}
	int pc1000 = nrOfPoints/1000; if (pc1000==0){pc1000 = 1;}
	int floodFilledPc = 0, floodFilledPcOld = 0;
	int[][] floodNodes = new int[nrOfPoints][4];
	int floodNodeX, floodNodeY, floodNodeZ, floodNodeT, index = 0;
//	boolean touchesXY, touchesZ;
	ArrayList<CellPoint> preliminaryParticle;
//	int [] sliceCounter = new int [imp.getNSlices()];
	int [] frameCounter = new int [imp.getNFrames()];
	boolean keep;
	
	searchCells: for(int t = 0; t < imp.getNFrames(); t++){
		for(int z = 0; z < imp.getNSlices(); z++){
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){		
					if(imp.getStack().getVoxel(x, y, imp.getStackIndex(c, z+1, t+1)-1) > 0.0){
						seedValue = imp.getStack().getVoxel(x, y, imp.getStackIndex(c, z+1, t+1)-1);	// New in v0.2.2: read BEFORE the fill zeroes this voxel

//						touchesXY = false;
//						touchesZ = false;
						
						preliminaryParticle = new ArrayList<CellPoint>();	// New in v0.2.2: no capacity to speed up (was every voxel left, per piece)
						preliminaryParticle.add(new CellPoint(x, y, z, t, refImp, c));
						
						imp.getStack().setVoxel(x, y, imp.getStackIndex(c, z+1, t+1)-1, 0.0);
						
//						if(x == 0 || x == imp.getWidth()-1)		touchesXY = true;
//						if(y == 0 || y == imp.getHeight()-1)	touchesXY = true;
//						if(z == 0 || z == imp.getNSlices()-1)	touchesZ = true;
						
						floodFilledPc++;
						
						//Floodfiller					
						floodNodeX = x;
						floodNodeY = y;
						floodNodeZ = z;
						floodNodeT = t;
						 
						index = 0;
						 
						floodNodes[0][0] = floodNodeX;
						floodNodes[0][1] = floodNodeY;
						floodNodes[0][2] = floodNodeZ;
						floodNodes[0][3] = floodNodeT;
						
						while (index >= 0){
							floodNodeX = floodNodes[index][0];
							floodNodeY = floodNodes[index][1];
							floodNodeZ = floodNodes[index][2];		
							floodNodeT = floodNodes[index][3];
							index--;
							if ((floodNodeX > 0) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new CellPoint(floodNodeX-1,floodNodeY,floodNodeZ,floodNodeT,
										refImp, c));
								imp.getStack().setVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
//								if(floodNodeX-1 == 0 || floodNodeX-1 == imp.getWidth()-1)		touchesXY = true;
//								if(floodNodeY == 0 || 	floodNodeY == imp.getHeight()-1)	touchesXY = true;
//								if(floodNodeZ == 0 || 	floodNodeZ == imp.getNSlices()-1)	touchesZ = true;
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX-1;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ;
								floodNodes[index][3] = floodNodeT;
							}
							if ((floodNodeX < (imp.getWidth()-1)) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new CellPoint(floodNodeX+1,floodNodeY,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
//								if(floodNodeX+1 == 0 || floodNodeX+1 == imp.getWidth()-1)	touchesXY = true;
//								if(floodNodeY == 0 || 	floodNodeY == imp.getHeight()-1)	touchesXY = true;
//								if(floodNodeZ == 0 || 	floodNodeZ == imp.getNSlices()-1)	touchesZ = true;
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX+1;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ;
								floodNodes[index][3] = floodNodeT;
							}
							if ((floodNodeY > 0) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY-1,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
//								if(floodNodeX == 0 || 	floodNodeX == imp.getWidth()-1)	touchesXY = true;
//								if(floodNodeY-1 == 0 || floodNodeY-1 == imp.getHeight()-1)	touchesXY = true;
//								if(floodNodeZ == 0 || 	floodNodeZ == imp.getNSlices()-1)	touchesZ = true;
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY-1;
								floodNodes[index][2] = floodNodeZ;
								floodNodes[index][3] = floodNodeT;
							}
							if ((floodNodeY < (imp.getHeight()-1)) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY+1,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
//								if(floodNodeX == 0 || 	floodNodeX == imp.getWidth()-1)	touchesXY = true;
//								if(floodNodeY+1 == 0 || floodNodeY+1 == imp.getHeight()-1)	touchesXY = true;
//								if(floodNodeZ == 0 ||	floodNodeZ == imp.getNSlices()-1)	touchesZ = true;
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY+1;
								floodNodes[index][2] = floodNodeZ;
								floodNodes[index][3] = floodNodeT;
							}
							if ((floodNodeZ > 0) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY,floodNodeZ-1,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
								
//								if(floodNodeX == 0 || 	floodNodeX == imp.getWidth()-1)	touchesXY = true;
//								if(floodNodeY == 0 || 	floodNodeY == imp.getHeight()-1)	touchesXY = true;
//								if(floodNodeZ-1 == 0 || floodNodeZ-1 == imp.getNSlices()-1)	touchesZ = true;
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ-1;
								floodNodes[index][3] = floodNodeT;
							}
							if ((floodNodeZ < (imp.getNSlices()-1)) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY,floodNodeZ+1,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
								
//								if(floodNodeX == 0 || 	floodNodeX == imp.getWidth()-1)		touchesXY = true;
//								if(floodNodeY == 0 || 	floodNodeY == imp.getHeight()-1)	touchesXY = true;
//								if(floodNodeZ+1 == 0 || floodNodeZ+1 == imp.getNSlices()-1)	touchesZ = true;
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ+1;
								floodNodes[index][3] = floodNodeT;
							}
							if(fillAcrossT){	// New in v0.2.2: otherwise the frames are linked after the fill
								if ((floodNodeT > 0) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, floodNodeZ+1, (floodNodeT-1)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY,floodNodeZ,floodNodeT-1, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, floodNodeZ+1, (floodNodeT-1)+1)-1, 0.0);
									
	//								if(floodNodeX == 0 || 	floodNodeX == imp.getWidth()-1)	touchesXY = true;
	//								if(floodNodeY == 0 || 	floodNodeY == imp.getHeight()-1)	touchesXY = true;
	//								if(floodNodeZ-1 == 0 || floodNodeZ-1 == imp.getNSlices()-1)	touchesZ = true;
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ;
									floodNodes[index][3] = floodNodeT-1;
								}
								if ((floodNodeT < (imp.getNFrames()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, floodNodeZ+1, ((floodNodeT)+1)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY,floodNodeZ,(floodNodeT)+1, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, floodNodeZ+1, ((floodNodeT)+1)+1)-1, 0.0);
									
	//								if(floodNodeX == 0 || 	floodNodeX == imp.getWidth()-1)		touchesXY = true;
	//								if(floodNodeY == 0 || 	floodNodeY == imp.getHeight()-1)	touchesXY = true;
	//								if(floodNodeZ+1 == 0 || floodNodeZ+1 == imp.getNSlices()-1)	touchesZ = true;
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ;
									floodNodes[index][3] = (floodNodeT)+1;
								}								
							}
							if(increaseRange){
								// X, Y
								if ((floodNodeX > 0) && (floodNodeY > 0)  
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									preliminaryParticle.add(new CellPoint(floodNodeX-1,floodNodeY-1,floodNodeZ,floodNodeT,
											refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY < (imp.getHeight()-1))
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX+1,floodNodeY+1,floodNodeZ,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY > 0) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX+1,floodNodeY-1,floodNodeZ,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX > 0) && (floodNodeY < (imp.getHeight()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX-1,floodNodeY+1,floodNodeZ,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ;
									floodNodes[index][3] = floodNodeT;
								}
								// Z-X
								if ((floodNodeX > 0) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX-1,floodNodeY,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX+1,floodNodeY,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX-1,floodNodeY,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX+1,floodNodeY,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								} 
								// Z-Y
								if ((floodNodeY > 0) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY-1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeY < (imp.getHeight()-1)) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY+1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeY > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY-1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeY < (imp.getHeight()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX,floodNodeY+1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								} 
								// X, Y - Z down
								if ((floodNodeX > 0) && (floodNodeY > 0) && (floodNodeZ > 0)  
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									preliminaryParticle.add(new CellPoint(floodNodeX-1,floodNodeY-1,floodNodeZ-1,floodNodeT,
											refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX+1,floodNodeY+1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY > 0) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX+1,floodNodeY-1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX > 0) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX-1,floodNodeY+1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								// X, Y - Z up
								if ((floodNodeX > 0) && (floodNodeY > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									preliminaryParticle.add(new CellPoint(floodNodeX-1,floodNodeY-1,floodNodeZ+1,floodNodeT,
											refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX+1,floodNodeY+1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX+1,floodNodeY-1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX > 0) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new CellPoint(floodNodeX-1,floodNodeY+1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								}
							}
						}					
						//Floodfiller
						preliminaryParticle.trimToSize();
//						saveParticle = true;
//						if(excludeSelection.equals(excludeOptions[1])){
//							if(touchesXY)	saveParticle = false;
//						}else if(excludeSelection.equals(excludeOptions[2])){
//							if(touchesXY)	saveParticle = false;
//							if(touchesZ)	saveParticle = false;
//						}else if(excludeSelection.equals(excludeOptions[3])){
//							if(touchesXY)	saveParticle = false;
//							
//							//check where most points are located
//							Arrays.fill(sliceCounter, 0);
//							for(int i = 0; i < preliminaryParticle.size(); i++){
//								sliceCounter [preliminaryParticle.get(i).z] ++;
//							}
//							if(tools.getMaximumIndex(sliceCounter) == 0 ||
//									tools.getMaximumIndex(sliceCounter) == imp.getNSlices()-1){
//								saveParticle = false;
////								IJ.log("touches xy: " + (particles.size()+1));
//							}
//							
//						}
						
//						if(saveParticle 
//								&& preliminaryParticle.size() >= minSize 
//								&& preliminaryParticle.size() <= maxSize){
						/**
						 * Test size of particle in all z
						 * */
						Arrays.fill(frameCounter, 0);
						for(int p = 0; p < preliminaryParticle.size(); p++){
							frameCounter[preliminaryParticle.get(p).t]++;
						}
						
						keep = true;
						for(int ti = 0; ti < frameCounter.length; ti++){
							if(frameCounter[ti]<minSize&&frameCounter[ti]!=0){
								keep = false;
								break;
							}
						}
						if(labelsHere){	// New in v0.2.2: counted for the label notes; the pieces themselves need not be kept
							labelPieces++;
							if(preliminaryParticle.size() < minSize)	labelSmallPieces++;
						}
						if(keep
								|| deferSizeRule){	// New in v0.2.2: the size rule waits until after joining and linking
							particles.add(preliminaryParticle);
							particleLabels.add(Double.valueOf(seedValue));	// New in v0.2.2
							
						}else{
							preliminaryParticle.clear();
							preliminaryParticle.trimToSize();
						}

						if(floodFilledPc%(pc100)<pc1000){
							progress.updateBarText("Reconstruction of ciliary structures complete: " + dformat3.format(((double)(floodFilledPc)/(double)(nrOfPoints))*100) + "%");
							progress.addToBar(0.2*((double)(floodFilledPc-floodFilledPcOld)/(double)(nrOfPoints)));								
							floodFilledPcOld = floodFilledPc;
						}	
					}				
				}	
			}
			if(floodFilledPc==nrOfPoints){					
				break searchCells;
			}
		}	
	}
				
	if(labelsHere){
		// New in v0.2.2: the surface counts faces to touching cilia with another label too
		for(int j = 0; j < particles.size(); j++)
			for(int i = 0; i < particles.get(j).size(); i++)
				particles.get(j).get(i).recountSurfaceForLabels(refImp, c);
	}
	
	refImp.changes = false;
	refImp.close();

	progress.updateBarText("Reconstruction of ciliary structures complete: " + dformat3.format(((double)(floodFilledPc)/(double)(nrOfPoints))*100) + "%");
	progress.addToBar(0.2*((double)(floodFilledPc-floodFilledPcOld)/(double)(nrOfPoints)));
	particles.trimToSize();

	// New in v0.2.2: join the pieces within the gap (per frame), link the frames, then the size rule
	boolean byLabelLinks = linkLater && byLabelId && labelModeCilia;
	boolean intensityLike = labelModeCilia && labelPieceNotes(labelPieces, labelSmallPieces);
	if(labelModeCilia && !intensityLike && !deferSizeRule) {
		labelSplitNotes(particles, particleLabels, false);	// nothing joins or links
	}	
	if(deferSizeRule){
		int nJoined = 0, nLinked = 0;
		if(joinGap > 0.0){
			progress.updateBarText("Joining pieces closer than " + plainNumber(joinGap) + " " + imp.getCalibration().getUnit() + " ...");
			nJoined = joinParticles(particles, particleLabels, labelModeCilia, joinGap, imp.getCalibration(), minSize);
		}		
		if(labelModeCilia && !intensityLike) {
			labelSplitNotes(particles, particleLabels, byLabelLinks);
		}		
		if(linkLater){
			progress.updateBarText("Connecting cilia across time ...");
			if(byLabelId && !labelModeCilia){
				progress.notifyMessageWithTaskNr("'" + timelapseTrackingOptions[2] + "' needs label mode for the cilia;"
						+ " the cilia were connected across time by overlap instead.", ProgressReporter.NOTIFICATION);
			}
			nLinked = byLabelLinks ? linkFramesByLabel(particles, particleLabels)
					: linkFramesByOverlap(particles, particleLabels, maxFrameGap, imp);
		}
		int nDropped = applySizeRule(particles, particleLabels, frameCounter);
		progress.notifyMessage("Cilia: " + nJoined + " piece(s) joined within the gap, " + nLinked + " linked across time, "
				+ nDropped + " object(s) below the minimum size removed.", ProgressReporter.LOG);
	}
	
	//write back to image
	{
		for(int j = 0; j < particles.size(); j++){
			for(int i = 0; i < particles.get(j).size(); i++){
				imp.getStack().setVoxel(particles.get(j).get(i).x,
					particles.get(j).get(i).y, 
					imp.getStackIndex(c, particles.get(j).get(i).z+1, 
					particles.get(j).get(i).t+1)-1, 
					particles.get(j).get(i).intensity);
			}
		}
	}

	return particles;
}//end getCiliaObjects


/**
 * A function to retrieve basal body center coordinates from a segmented basal body channel
 * New function, created August 31, 2024.
 * @return a container that contains a list for each time point, and in each of that list it contains points for basal body centers detected
 * at that time point
 * @param imp: Hyperstack image where one channel represents the recording of BBs
 * @param c: defines the channel of the Hyperstack image imp, in which the basal body information is stored 1 <= c <= number of channels
 * @param increaseRange: defines whether also diagonal pixels should be allowed while Flood Filling
 * */
ArrayList<ArrayList<Uncalibrated3DPoint>> getBBObjectsTimelapse (ImagePlus imp, int c, boolean increaseRange){
	double seedValue = 0.0;	// New in v0.2.2: label value of the object being filled
	boolean labelsHere = labelModeBB;	// New in v0.2.2: one value per object in this channel?

	ImagePlus refImp = imp.duplicate();
	int nrOfPoints [] = new int [imp.getNFrames()];
	int totalNrOfPoints = 0;
	Arrays.fill(nrOfPoints, 0);

	for(int t = 0; t < imp.getNFrames(); t++){
		for(int z = 0; z < imp.getNSlices(); z++){
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){	
					if(imp.getStack().getVoxel(x, y, imp.getStackIndex(c, z+1, t+1)-1) > 0.0){
						nrOfPoints[t] ++;
					}
				}
			}
		}
		totalNrOfPoints += nrOfPoints[t];
	}
	
	if(totalNrOfPoints == 0 || totalNrOfPoints == imp.getNSlices()*imp.getNFrames()*imp.getWidth()*imp.getHeight()) {
		// v0.2.2: one empty list per frame, so the analysis goes on without basal bodies (an empty list crashed the
		// single-frame workflow). The callers note "No BBs found"; a channel where every voxel is foreground stops the
		// image before (allForeground, in the checks of the task loop)
		ArrayList<ArrayList<Uncalibrated3DPoint>> none = new ArrayList<ArrayList<Uncalibrated3DPoint>>(imp.getNFrames());
		for(int t = 0; t < imp.getNFrames(); t++) {
			none.add(new ArrayList<Uncalibrated3DPoint>(0));
		}
		return none;
	}
	
	ArrayList<ArrayList<ArrayList<PartPoint>>> particles = new ArrayList<ArrayList<ArrayList<PartPoint>>>(imp.getNFrames());
	for(int t = 0; t < imp.getNFrames(); t++){
		particles.add(new ArrayList<ArrayList<PartPoint>>((int)Math.round((double)nrOfPoints [t]/(double)minBBSize)));
	}

	int floodFilledPc [] = new int [imp.getNFrames()];	
	int floodNodeX, floodNodeY, floodNodeZ, floodNodeT, index = 0;
	int[][] floodNodes;
//	boolean touchesXY, touchesZ;
	ArrayList<PartPoint> preliminaryParticle;
	
	searchBBs: for(int t = 0; t < imp.getNFrames(); t++){
		floodNodes = new int[nrOfPoints[t]][4];
		
		for(int z = 0; z < imp.getNSlices(); z++){
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){		
					if(imp.getStack().getVoxel(x, y, imp.getStackIndex(c, z+1, t+1)-1) > 0.0){
						seedValue = imp.getStack().getVoxel(x, y, imp.getStackIndex(c, z+1, t+1)-1);	// New in v0.2.2: read BEFORE the fill zeroes this voxel
						preliminaryParticle = new ArrayList<PartPoint>();	// New in v0.2.2: no capacity to speed up (was every voxel left, per piece)

						preliminaryParticle.add(new PartPoint(x, y, z, t, refImp, c));
						
						imp.getStack().setVoxel(x, y, imp.getStackIndex(c, z+1, t+1)-1, 0.0);
						
						floodFilledPc [t]++;
						
						//Floodfiller					
						floodNodeX = x;
						floodNodeY = y;
						floodNodeZ = z;
						floodNodeT = t;
						 
						index = 0;
						 
						floodNodes[0][0] = floodNodeX;
						floodNodes[0][1] = floodNodeY;
						floodNodes[0][2] = floodNodeZ;
						floodNodes[0][3] = floodNodeT;
						
						while (index >= 0){
							floodNodeX = floodNodes[index][0];
							floodNodeY = floodNodes[index][1];
							floodNodeZ = floodNodes[index][2];		
							floodNodeT = floodNodes[index][3];
							index--;
							if ((floodNodeX > 0) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY,floodNodeZ,floodNodeT,
										refImp, c));
								imp.getStack().setVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc [t]++;
								
								floodNodes[index][0] = floodNodeX-1;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ;
								floodNodes[index][3] = floodNodeT;
							}
							if ((floodNodeX < (imp.getWidth()-1)) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc [t]++;
								
								floodNodes[index][0] = floodNodeX+1;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ;
								floodNodes[index][3] = floodNodeT;
							}
							if ((floodNodeY > 0) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY-1,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc [t]++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY-1;
								floodNodes[index][2] = floodNodeZ;
								floodNodes[index][3] = floodNodeT;
							}
							if ((floodNodeY < (imp.getHeight()-1)) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY+1,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc [t]++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY+1;
								floodNodes[index][2] = floodNodeZ;
								floodNodes[index][3] = floodNodeT;
							}
							if ((floodNodeZ > 0) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY,floodNodeZ-1,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc [t]++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ-1;
								floodNodes[index][3] = floodNodeT;
							}
							if ((floodNodeZ < (imp.getNSlices()-1)) 
									&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
								
								preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY,floodNodeZ+1,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc [t]++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ+1;
								floodNodes[index][3] = floodNodeT;
							}
							
							if(increaseRange){
								// X, Y
								if ((floodNodeX > 0) && (floodNodeY > 0)  
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY-1,floodNodeZ,floodNodeT,
											refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY < (imp.getHeight()-1))
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY+1,floodNodeZ,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY > 0) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY-1,floodNodeZ,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX > 0) && (floodNodeY < (imp.getHeight()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY+1,floodNodeZ,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ;
									floodNodes[index][3] = floodNodeT;
								}
								// Z-X
								if ((floodNodeX > 0) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								} 
								// Z-Y
								if ((floodNodeY > 0) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY-1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeY < (imp.getHeight()-1)) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY+1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeY > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY-1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								} 
								if ((floodNodeY < (imp.getHeight()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY+1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								} 
								// X, Y - Z down
								if ((floodNodeX > 0) && (floodNodeY > 0) && (floodNodeZ > 0)  
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY-1,floodNodeZ-1,floodNodeT,
											refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY+1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY > 0) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY-1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX > 0) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ > 0)
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY+1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ-1;
									floodNodes[index][3] = floodNodeT;
								}
								// X, Y - Z up
								if ((floodNodeX > 0) && (floodNodeY > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY-1,floodNodeZ+1,floodNodeT,
											refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY+1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY-1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								}
								if ((floodNodeX > 0) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& sameObject(imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1), seedValue, labelsHere)){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY+1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc [t]++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ+1;
									floodNodes[index][3] = floodNodeT;
								}
							}
						}					
						//Floodfiller
						preliminaryParticle.trimToSize();

						if(preliminaryParticle.size() >= minBBSize){
							particles.get(t).add(preliminaryParticle);
						}else{
							preliminaryParticle.clear();
							preliminaryParticle.trimToSize();
						}
					}				
				}	
			}			
		}
		
		progress.updateBarText("Reconstruction basal bodies in frame " 
				+ (t+1) + " of " + imp.getNFrames()
				+ " complete.");
		progress.addToBar(0.2/(double)imp.getNFrames());
	}
				
	refImp.changes = false;
	refImp.close();
	
	particles.trimToSize();
	
	//write back to image
	for(int t = 0; t < particles.size(); t++){
		for(int j = 0; j < particles.get(t).size(); j++){
			for(int i = 0; i < particles.get(t).get(j).size(); i++){
				imp.getStack().setVoxel(particles.get(t).get(j).get(i).x,
					particles.get(t).get(j).get(i).y, 
					imp.getStackIndex(c, particles.get(t).get(j).get(i).z+1, 
					particles.get(t).get(j).get(i).t+1)-1, 
					particles.get(t).get(j).get(i).intensity);
			}
		}
	}
	//write back to image
	
	int [][] track = linkBBsAcrossTime(particles, imp);	// New in v0.2.2

	/*
	 * Reduce basal bodies to center points
	 */
	double centerX, centerY, centerZ;	
	ArrayList<ArrayList<Uncalibrated3DPoint>> bbs = new ArrayList<ArrayList<Uncalibrated3DPoint>> (particles.size());	
	for(int t = 0; t < particles.size(); t++){
		bbs.add(new ArrayList<Uncalibrated3DPoint> (particles.get(t).size()));		
		for(int j = 0; j < particles.get(t).size(); j++){
			centerX = 0.0;
			centerY = 0.0; 
			centerZ = 0.0;
			
			for(int i = 0; i < particles.get(t).get(j).size(); i++){
				centerX += particles.get(t).get(j).get(i).x;
				centerY += particles.get(t).get(j).get(i).y; 
				centerZ += particles.get(t).get(j).get(i).z; 
			}
			
			centerX /= (double) particles.get(t).get(j).size();
			centerY /= (double) particles.get(t).get(j).size();
			centerZ /= (double) particles.get(t).get(j).size();
			
			Uncalibrated3DPoint bb = new Uncalibrated3DPoint(centerX,centerY,centerZ);
			bb.track = track [t][j];	// New in v0.2.2
			bbs.get(t).add(bb);
		}
	}
		
	return bbs;
}//end getBBObjects

/**
 * The track of each basal-body piece (per frame, as in particles): the classic connection of the cilia, for the basal
 * bodies. Pieces that overlap in x, y and z in consecutive frames, or across up to maxFrameGap frames without them, get
 * one track. As linkFramesByOverlap: each voxel position remembers the piece that covered it last, so a piece links to
 * the last piece at each of its positions, if that lies within the window. The map holds the basal-body voxels only.
 * @return for each frame and piece, its track (the index of the track's first piece, counted over all frames)
 *
 * New in v0.2.2
 */
private int [][] linkBBsAcrossTime(ArrayList<ArrayList<ArrayList<PartPoint>>> particles, ImagePlus imp){
	int W = imp.getWidth(), H = imp.getHeight();
	int[] first = new int[particles.size()]; // the index of each frame's first piece
	int n = 0;
	for (int t = 0; t < particles.size(); t++) {
		first[t] = n;
		n += particles.get(t).size();
	}
	int[] frameOf = new int[n];
	for (int t = 0; t < particles.size(); t++) {
		for (int j = 0; j < particles.get(t).size(); j++)
			frameOf[first[t] + j] = t;
	}
	int[] parent = new int[n];
	for (int i = 0; i < n; i++)
		parent[i] = i;

	HashMap<Long, Integer> lastPiece = new HashMap<Long, Integer>();
	// voxel position (x, y, z) -> the piece that covered it last (long: large stacks)
	
	for (int t = 0; t < particles.size(); t++) {
		// 1) link to the pieces of earlier frames (this frame is marked in step 2, so a
		// piece never links within its frame)
		for (int j = 0; j < particles.get(t).size(); j++) {
			for (PartPoint p : particles.get(t).get(j)) {
				Integer last = lastPiece.get(((long) p.z * H + p.y) * W + p.x);
				if (last != null && t - frameOf[last.intValue()] <= maxFrameGap + 1) {
					unionRoots(parent, first[t] + j, last.intValue());
				}
			}
		}
		// 2) mark: this frame's pieces become the last piece at their positions
		for (int j = 0; j < particles.get(t).size(); j++) {
			for (PartPoint p : particles.get(t).get(j)) {
				lastPiece.put(((long) p.z * H + p.y) * W + p.x, first[t] + j);
			}
		}
	}

	int[][] track = new int[particles.size()][];
	for (int t = 0; t < particles.size(); t++) {
		track[t] = new int[particles.get(t).size()];
		for (int j = 0; j < track[t].length; j++)
			track[t][j] = findRoot(parent, first[t] + j);
	}
	return track;
}

/**
 * Builds the time-lapse objects (one ID and one row in the results each) when the cilia are connected by overlap
 * (classic connection) or by label ID and the basal bodies are segmented. Before this method runs, each cilium object
 * is one cilium followed over time, and in each frame every basal body was matched to at most one cilium (the nearest,
 * within the maximum distance). This method also follows each basal body over time (its track, see linkBBsAcrossTime)
 * and uses it to decide which frames belong to which object:
 *	- A cilium that disappears and comes back stays one object, since its basal body is followed through the frames
 *	  without the cilium. For example, a cilium resorbed in frames 3 and 4 and grown again in frame 5 keeps its ID, and
 *	  frames 3 and 4 hold its basal body only.
 *	- A basal body that never has a cilium is one object over all its frames, not one object per frame.
 *	- Two cilium objects that share a basal body become one object if they never have a cilium in the same frame. For
 *	  example, a cilium that changed its direction between two frames, so that its overlap broke, is joined again.
 *	  Two cilium objects with a cilium in the same frame stay two objects, since they are two cilia (cilia that touch
 *	  are one object already).
 *	- With the connection by label ID, the labels decide which cilium is which, so cilium objects are never joined.
 *	  Basal bodies only add frames to an object.
 *
 * In each frame, an object holds at most one basal body. In a frame with the cilium, this is the basal body matched to
 * the cilium. In a frame without the cilium, it is the basal body of the object's track that lies nearest to the
 * object's basal body in the closest frame. Any further basal body of the object's track in that frame becomes an
 * object of one frame. For example, a daughter centriole that touched the basal body in one frame joined its track,
 * but is not counted as the object's basal body.
 *
 * The basal-body filter looks at whole objects, as in linear assignment:
 *	- "cilia without BBs" removes an object whose cilium has a basal body in no frame;
 *	- "BBs without cilia" removes every object that never has a cilium, and counts its basal bodies as excluded.
 * Since the filter looks at whole objects, a cilium whose basal body is missed in a few frames (e.g. where the basal
 * body stain is faint) stays in the results.
 *
 * @param timelapseCilia	the cilium objects, MODIFIED IN PLACE: afterwards the final objects, first the cilium objects
 *							in their order, then the objects of basal bodies only
 * @param bbParticles		per frame, the basal bodies (centre points, each with its track from linkBBsAcrossTime)
 * @param bbObject			per frame and basal body, the index in timelapseCilia of the cilium object it was matched to
 *							(-1 = not matched)
 * @return					the number of basal bodies removed by the filter "BBs without cilia"
 *
 * New in v0.2.2
 */
private int addBasalBodiesAcrossTime(ArrayList<TimelapseCilium> timelapseCilia,
		ArrayList<ArrayList<Uncalibrated3DPoint>> bbParticles, int [][] bbObject, ImagePlus imp,
		boolean measureC2local, boolean measureC3local, boolean measureBasalLocal, double [] intensityThresholds){
	final int nC = timelapseCilia.size();
	final double pw = imp.getCalibration().pixelWidth, pd = imp.getCalibration().pixelDepth;
	boolean dropBBsWithoutCilia = bbCiliaFilterSelection.equals(bbCiliaFilterOptions[1]) || bbCiliaFilterSelection.equals(bbCiliaFilterOptions[3]);
	boolean dropCiliaWithoutBBs = bbCiliaFilterSelection.equals(bbCiliaFilterOptions[2]) || bbCiliaFilterSelection.equals(bbCiliaFilterOptions[3]);

	// 1) Decide which cilium objects and basal-body tracks belong together.
	//    Each cilium object and each basal-body track is a node: the cilium objects are nodes 0 to nC-1, the tracks are
	//    nodes from nC on (trackNode gives the node of a track). Nodes that belong together form a group. The groups are
	//    kept as a union-find: each node points to a parent, and the node that points to itself (the root) stands for its group.
	//    A basal body matched to a cilium puts the cilium object and the basal body's track into one group. Since one
	//    track can be matched to different cilium objects over time (e.g. to a cilium before and after its overlap
	//    broke), this is how two cilium objects come into one group.
	HashMap<Integer, Integer> trackNode = new HashMap<Integer, Integer>();
	for (ArrayList<Uncalibrated3DPoint> frame : bbParticles) {
		for (Uncalibrated3DPoint bb : frame) {
			if (!trackNode.containsKey(bb.track))
				trackNode.put(bb.track, nC + trackNode.size());
		}
	}
	
	int[] parent = new int[nC + trackNode.size()];
	BitSet[] ciliumFrames = new BitSet[parent.length];
		// per root: the frames in which its group has a cilium (to refuse joining two cilia of one frame)
	
	for (int i = 0; i < parent.length; i++) {
		parent[i] = i;
		ciliumFrames[i] = new BitSet();
	}
	for (int c = 0; c < nC; c++) {
		for (Cilium cil : timelapseCilia.get(c).cilia) {
			if (cil.ciliumAvailable)
				ciliumFrames[c].set(cil.t);
		}
	}
	boolean byLabel = timelapseTrackingSelection.equals(timelapseTrackingOptions[2]);
	for (int t = 0; t < bbParticles.size(); t++) {
		for (int b = 0; b < bbParticles.get(t).size(); b++) {
			if (bbObject[t][b] < 0) {
				 // not matched to a cilium in this frame: links nothing
				continue;
			}
			int ri = findRoot(parent, bbObject[t][b]),
					rj = findRoot(parent, trackNode.get(bbParticles.get(t).get(b).track));
			
			if (ri == rj || ciliumFrames[ri].intersects(ciliumFrames[rj])) {
				// Already one group: nothing to do. Both groups have a cilium in the same frame: they are two cilia (e.g. two
				// cilia whose basal bodies touched once, so that their tracks became one), so they stay apart. The basal body
				// stays matched to its cilium in this frame either way (bbObject is not changed).			
				continue;
			}
			
			if (byLabel && !ciliumFrames[ri].isEmpty() && !ciliumFrames[rj].isEmpty()) {
				// Label ID: two groups that both have a cilium would join two labels, so they stay apart. A group without
				// cilium (a track of basal bodies only) may still join, so that it adds its frames to the cilium.
				continue;
			}
			BitSet both = (BitSet) ciliumFrames[ri].clone();
			both.or(ciliumFrames[rj]);
			unionRoots(parent, ri, rj);
			ciliumFrames[findRoot(parent, ri)] = both;		// the joined group has the cilium frames of both
		}
	}
	
	// 2) Join the cilium objects of one group into one object.
	//    firstOf gives each group its cilium object with the lowest index (-1: the group has no cilium object, only basal
	//    bodies). The joined object takes the place of that first object, so the other objects keep their order. Since
	//    step 1 never joins cilium objects that have a cilium in the same frame, their frames can simply be put together
	//    in time order. Each frame keeps the basal body matched to its cilium.
	int[] firstOf = new int[parent.length]; // root -> its first cilium object (-1 = none)
	Arrays.fill(firstOf, -1);
	TreeMap<Integer, ArrayList<Integer>> groups = new TreeMap<Integer, ArrayList<Integer>>(); // root -> its cilium objects
	for (int c = 0; c < nC; c++) {
		int r = findRoot(parent, c);
		if (firstOf[r] < 0)
			firstOf[r] = c;
		if (!groups.containsKey(r))
			groups.put(r, new ArrayList<Integer>());
		groups.get(r).add(c);
	}
	for (ArrayList<Integer> g : groups.values()) {
		if (g.size() == 1) {
			 // a single cilium object: nothing to join
			continue;
		}
		ArrayList<Cilium> perFrame = new ArrayList<Cilium>();
		for (int c : g)
			perFrame.addAll(timelapseCilia.get(c).cilia);
		Collections.sort(perFrame, (u, v) -> Integer.compare(u.t, v.t));
		timelapseCilia.set(g.get(0),
				new TimelapseCilium(perFrame, imp, measureC2local, measureC3local, skeletonize, progress));
		for (int k = 1; k < g.size(); k++){
			 // joined into the first; left out in step 4
			timelapseCilia.set(g.get(k), null);
		}
	}
	
	// 3) Give each object its basal body in the frames without its cilium, and place every other basal body.
	//    chosen holds, per group and frame, the basal body the object holds in that frame. It starts with the basal
	//    bodies matched to a cilium (below), and is filled frame by frame with the basal bodies picked in frames
	//    without the cilium. So in a frame without the cilium, the object's basal body can be found by comparing with
	//    its basal body in the frames around it.
	HashMap<Integer, TreeMap<Integer, Uncalibrated3DPoint>> chosen = new HashMap<Integer, TreeMap<Integer, Uncalibrated3DPoint>>();	// root -> frame -> its basal body
	for (int t = 0; t < bbParticles.size(); t++) {
		for (int b = 0; b < bbParticles.get(t).size(); b++) {
			if (bbObject[t][b] < 0)
				continue;
			int r = findRoot(parent, bbObject[t][b]);
			if (!chosen.containsKey(r))
				chosen.put(r, new TreeMap<Integer, Uncalibrated3DPoint>());
			chosen.get(r).put(t, bbParticles.get(t).get(b));
		}
	}
	
	HashMap<Integer, ArrayList<Cilium>> bbFramesOf = new HashMap<Integer, ArrayList<Cilium>>();	// cilium object -> its frames with the basal body only
	ArrayList<ArrayList<Cilium>> bbOnly = new ArrayList<ArrayList<Cilium>>();	// the objects of basal bodies only, in the order they appear
	HashMap<Integer, Integer> bbOnlyOf = new HashMap<Integer, Integer>();		// root of a group without cilium -> its index in bbOnly
	int excludedBBs = 0;
	for(int t = 0; t < bbParticles.size(); t++){
		// this frame's basal bodies that were not matched to a cilium, per group, in their order
		LinkedHashMap<Integer, ArrayList<Integer>> candidates = new LinkedHashMap<Integer, ArrayList<Integer>>();
		for (int b = 0; b < bbParticles.get(t).size(); b++) {
			if (bbObject[t][b] >= 0) {
				 // matched to a cilium: already the basal body of that cilium's frame
				continue;
			}
			int r = findRoot(parent, trackNode.get(bbParticles.get(t).get(b).track));
			if (!candidates.containsKey(r))
				candidates.put(r, new ArrayList<Integer>());
			candidates.get(r).add(b);
		}
		for (Map.Entry<Integer, ArrayList<Integer>> e : candidates.entrySet()) {
			int r = e.getKey();
			int first = firstOf[r];
			boolean withCilium = first >= 0;
			int pick = -1; // stays -1 if the object has a cilium in this frame: its basal body is the matched one
			if (!withCilium || !hasCiliumInFrame(timelapseCilia.get(first), t)) {
				pick = nearestToObject(chosen.get(r), t, e.getValue(), bbParticles.get(t), pw, pd);
				if (!chosen.containsKey(r))
					chosen.put(r, new TreeMap<Integer, Uncalibrated3DPoint>());
				chosen.get(r).put(t, bbParticles.get(t).get(pick));
			}
			for (int b : e.getValue()) {
				if (b == pick && withCilium) {
					// the cilium object's basal body in a frame without its cilium
					if (!bbFramesOf.containsKey(first))
						bbFramesOf.put(first, new ArrayList<Cilium>());
					bbFramesOf.get(first).add(bbOnlyCilium(bbParticles.get(t).get(b), t, imp, measureC2local,
							measureC3local, measureBasalLocal, intensityThresholds));
					continue;
				}
				if (dropBBsWithoutCilia) {
					// every basal body that gets here ends up in an object without cilium: the picked one of a group
					// without cilium object, or a further one (an object of one frame)
					excludedBBs++;	// in an object without cilium
					continue;
				}
				ArrayList<Cilium> object;
				if(b == pick){
					// the basal body of a group without cilium object: one more frame of its object of basal bodies only
					if(!bbOnlyOf.containsKey(r)){
						bbOnlyOf.put(r, bbOnly.size());
						bbOnly.add(new ArrayList<Cilium>());
					}
					object = bbOnly.get(bbOnlyOf.get(r));
				}else{
					// a further basal body of the track in this frame: an object of one frame
					object = new ArrayList<Cilium>(1);
					bbOnly.add(object);
				}
				object.add(bbOnlyCilium(bbParticles.get(t).get(b), t, imp, measureC2local, measureC3local, measureBasalLocal,
						intensityThresholds));
			}
		}
	}

	// 4) Put the final list together: first the cilium objects in their order (joined ones once, at the place of the
	//    first), each with its frames of the basal body only added in time order; then the objects of basal bodies only.
	//    The filter "cilia without BBs" is applied here, since only now does each object hold all its frames.
	ArrayList<TimelapseCilium> objects = new ArrayList<TimelapseCilium>(nC + bbOnly.size());
	for (int c = 0; c < nC; c++) {
		TimelapseCilium tc = timelapseCilia.get(c);
		if (tc == null) {
			// joined into another cilium object in step 2
			continue;
		}
		if (bbFramesOf.containsKey(c)) {
			// built again from all its frames, so that the frames with the basal body only are part of the object
			ArrayList<Cilium> perFrame = new ArrayList<Cilium>(tc.cilia);
			perFrame.addAll(bbFramesOf.get(c));
			Collections.sort(perFrame, (u, v) -> Integer.compare(u.t, v.t));
			tc = new TimelapseCilium(perFrame, imp, measureC2local, measureC3local, skeletonize, progress);
		}
		if (dropCiliaWithoutBBs) {
			// excluded only if no frame of the object holds a basal body
			boolean withBB = false;
			for (Cilium cil : tc.cilia)
				if (cil.bbAvailable)
					withBB = true;
			if (!withBB)
				tc.excluded = true;
		}
		objects.add(tc);
	}
	for (ArrayList<Cilium> frames : bbOnly) {
		// an object of one frame has its own constructor
		objects.add(frames.size() == 1 ? new TimelapseCilium(frames.get(0), imp, progress)
				: new TimelapseCilium(frames, imp, measureC2local, measureC3local, skeletonize, progress));
	}
	timelapseCilia.clear();
	timelapseCilia.addAll(objects);
	return excludedBBs;
}

/**
 * A frame of an object that holds its basal body only.
 *
 * New in v0.2.2
 */
private Cilium bbOnlyCilium(Uncalibrated3DPoint bb, int t, ImagePlus imp, boolean measureC2local, boolean measureC3local,
		boolean measureBasalLocal, double [] intensityThresholds){
	return new Cilium(bb, t, imp, measureC2local, channelC2, measureC3local, channelC3, measureBasalLocal, basalStainC,
			channelReconstruction, gXY, gZ, intensityThresholds, progress, skeletonize);
}

/**
 * Does the object hold a cilium in frame t?
 *
 * New in v0.2.2
 */
private static boolean hasCiliumInFrame(TimelapseCilium tc, int t){
	for(Cilium cil : tc.cilia){
		if(cil.t == t && cil.ciliumAvailable)	return true;
	}
	return false;
}

/**
 * Of the basal bodies bs (indices in frame), the one nearest the object's basal body in the frame closest to t (the
 * earlier one on a tie); the first if the object has none yet.
 *
 * New in v0.2.2
 */
private int nearestToObject(TreeMap<Integer, Uncalibrated3DPoint> chosen, int t, ArrayList<Integer> bs,
		ArrayList<Uncalibrated3DPoint> frame, double pw, double pd){
	if(chosen == null || chosen.isEmpty())	return bs.get(0);
	Map.Entry<Integer, Uncalibrated3DPoint> before = chosen.lowerEntry(t), after = chosen.higherEntry(t);
	Uncalibrated3DPoint ref = (after == null || (before != null && t - before.getKey() <= after.getKey() - t))
			? before.getValue()
			: after.getValue();
	int best = bs.get(0);
	double bestDist = Double.POSITIVE_INFINITY;
	for (int b : bs) {
		Uncalibrated3DPoint p = frame.get(b);
		double d = getDistance(p.x * pw, p.y * pw, p.z * pd, ref.x * pw, ref.y * pw, ref.z * pd); // pixel height = width
		if (d < bestDist) {
			best = b;
			bestDist = d;
		}
	}
	return best;
}

/**
 * The distance from a basal body (voxel coordinates) to a cilium, in the calibrated unit: to the cilium's nearest
 * skeleton point, or to its centre if it has no skeleton. The measure of the basal-body assignment in time-lapses.
 *
 * New in v0.2.2
 */
private double bbToCiliumDistance(Cilium theCilium, Uncalibrated3DPoint bb){
	if(theCilium.sklAvailable) {
		double minDist = Double.POSITIVE_INFINITY;
		double sklPoints [][] = theCilium.getSkeletonPointsForOriginalImage();
		for(int skl = 0; skl < sklPoints.length; skl++) {
			double dist = getDistance(bb.x * theCilium.calibration, bb.y * theCilium.calibration, bb.z * theCilium.voxelDepth,
					sklPoints[skl][0], sklPoints[skl][1], sklPoints[skl][2]);
			if(dist < minDist) {
				minDist = dist;
			}
		}
		return minDist;
	}
	return getDistance(bb.x * theCilium.calibration, bb.y * theCilium.calibration, bb.z * theCilium.voxelDepth,
			theCilium.xC, theCilium.yC, theCilium.zC);
}

/**
 * added time lapse mode on 23.04.2019 (JNH)
 * */
private double [] getIntensityThresholds(ImagePlus imp, ArrayList<ArrayList<CellPoint>> ciliaParticles){
	double intensityThresholds [] = new double[imp.getNChannels()]; //for each channel individual
	Arrays.fill(intensityThresholds, -1.0);
	//Determine intensity thresholds for each other channel
	{
		ImagePlus tempImp;
		ArrayList<double[]> intensities = new ArrayList<double[]>(25);
		for(int i = 0; i < 25; i ++){
			intensities.add(new double [((int)(imp.getWidth()/5.0)+1)*((int)(imp.getHeight()/5.0)+1)*imp.getNSlices()*imp.getNFrames()]);
		}
		CellPoint p;
		int listIndex = 0;
		int counter [] = new int [25];
		double allIntensities [] = new double [(int)(25*(((int)(imp.getWidth()/5.0)+1)*((int)(imp.getHeight()/5.0)+1)*imp.getNSlices()*imp.getNFrames())*0.1)+1];
		int allCounter;
		for(int c = 1; c <= imp.getNChannels(); c++){
			if((measureC2 && c == channelC2)
					|| (measureC3 && c == channelC3)
					|| (measureBasal && c == basalStainC)){
				//get image
				tempImp = getChannel(imp, c);
				for(int i = 0; i < ciliaParticles.size(); i ++){
					for(int j = 0; j < ciliaParticles.get(i).size(); j++){
						p = ciliaParticles.get(i).get(j);
						tempImp.getStack().setVoxel(p.x, p.y, tempImp.getStackIndex(1, p.z+1, p.t+1)-1, 0.0);
					}
				}
				
				//initialize intensity arrays
				for(int i = 0; i < 25; i ++){
					Arrays.fill(intensities.get(i), Double.MIN_VALUE);
					counter [i] = 0;
				}
				Arrays.fill(allIntensities, 0.0);
				allCounter = 0;
								
				//obtain pixel intensities
				for(int x = 0; x < tempImp.getWidth(); x++){
					for(int y = 0; y < tempImp.getHeight(); y++){
						for(int z = 0; z < tempImp.getNSlices(); z++){
							for(int t = 0; t < tempImp.getNFrames(); t++){
								if(tempImp.getStack().getVoxel(x, y, tempImp.getStackIndex(1, z+1, t+1)-1)>0.0){
									listIndex = 1+(int)((double)x/(double)tempImp.getWidth()*5.0);
									listIndex += 5*(int)((double)y/(double)tempImp.getHeight()*5.0);
									listIndex -=1;

									intensities.get(listIndex)[counter[listIndex]] 
											= tempImp.getStack().getVoxel(x, y, tempImp.getStackIndex(1, z+1, t+1)-1);
									counter [listIndex] += 1;
								}
							}							
						}						
					}
				}
				tempImp.close();				
				
				// calculate thresholds
				int noOfValues;
				for(int i = 0; i < 25; i ++){
					//get nr of values included in top 10%
					noOfValues = (int)(counter[i]*0.1);
					if(noOfValues>0){
						Arrays.sort(intensities.get(i));
						for(int j = 0; j < noOfValues; j++){
							allIntensities[allCounter] = intensities.get(i)[counter[i]-1-j];
							allCounter++;
						}
					}					
				}
				if(allCounter>1){
//					IJ.log("avg: " + tools.getAverageOfRange(allIntensities, 0, allCounter-1));
//					IJ.log("sd: " + tools.getSDOfRange(allIntensities, 0, allCounter-1));
					intensityThresholds [c-1] = tools.getAverageOfRange(allIntensities, 0, allCounter-1) + 1.5*tools.getSDOfRange(allIntensities, 0, allCounter-1);
				}else if(allCounter == 1){
					intensityThresholds [c-1] = allIntensities[0];
				}else{
					intensityThresholds [c-1] = 0.0;
				}		
			}
		}
		//Clean up
		intensities.clear();
		intensities = null;
		allIntensities = null;
		p = null;
		counter = null;
	}	
	return intensityThresholds;
}

/**
 * @param channel: 1 <= channel <= # channels
 * */
private static ImagePlus getChannel(ImagePlus imp, int channel){
	ImagePlus impNew = IJ.createHyperStack("channel image", imp.getWidth(), imp.getHeight(), 1, imp.getNSlices(), imp.getNFrames(), imp.getBitDepth());
	int index = 0, indexNew = 0;
	
	for(int x = 0; x < imp.getWidth(); x++){
		for(int y = 0; y < imp.getHeight(); y++){
			for(int s = 0; s < imp.getNSlices(); s++){
				for(int f = 0; f < imp.getNFrames(); f++){
					index = imp.getStackIndex(channel, s+1, f+1)-1;
					indexNew = impNew.getStackIndex(1, s+1, f+1)-1;
					impNew.getStack().setVoxel(x, y, indexNew, imp.getStack().getVoxel(x, y, index));
				}					
			}
		}
	}
	tools.autoscaleDisplayRange(imp, 0.99);
	
	impNew.setCalibration(imp.getCalibration());
	return impNew;
}

/**
 * Workflow for non-timelapse analysis
 * */
private void analyzeCiliaIn3DAndSaveResults(ImagePlus imp, boolean measureC2local, boolean measureC3local, boolean measureBasalLocal, 
		String name, String dir, Date startDate, String filePrefix, String subfolderPrefix, String tempExcludeSelection){
	ArrayList<Cilium> cilia = new ArrayList<Cilium>();
	double intensityThresholds [] = new double[imp.getNChannels()]; //for each channel individual
	
	TextRoi txtID;
	imp.setOverlay(new Overlay());
	imp.setHideOverlay(false);
	
	int excludedCilia = 0;
	int excludedCiliaBB = 0;	// New in v0.2.2: the cilia the basal-body filter removed, counted apart from the border option
	int excludedBBs = 0;
	int nrOfBBs = 0;
	int totalNrOfDetectedCilia = 0;
	
	{
		//Retrieving ciliary objects from the image
		ArrayList<ArrayList<CellPoint>> ciliaParticles = getCiliaObjectsTimelapse(imp, channelReconstruction, increaseRangeCilia, true);	//Method changed on 23.04.2019
		if(ciliaParticles.size() == 0) {
			progress.notifyMessage("No cilia found in image " + name + " (Dir: " + dir + ") - skipped analysis",
					ProgressReporter.NOTIFICATION);	// New in v0.2.2: as in time-lapses (before: no output and no note)
			return;
		}
		progress.updateBarText("Structure reconstruction completed.");
		
		//Determine intensity thresholds for each other channel
		progress.updateBarText("Determining intensity thresholds ...");
		intensityThresholds = getIntensityThresholds(imp, ciliaParticles);
		
		
		boolean touchesXY, touchesZ;
		int [] sliceCounter = new int [imp.getNSlices()];
		
		cilia.ensureCapacity(ciliaParticles.size());
		for(int i = 0; i < ciliaParticles.size(); i++){
			progress.updateBarText("Quantifying cilia objects (" + i + "/" + ciliaParticles.size() + " done)");
			cilia.add(new Cilium(ciliaParticles.get(i), imp, measureC2local, channelC2, measureC3local, channelC3, measureBasalLocal, basalStainC,
					channelReconstruction, gXY, gZ, intensityThresholds, progress, skeletonize, joinGap));
			if(cilia.size()!=i+1) {
				progress.notifyMessage("Error while measuring cilium " + (i+1), ProgressReporter.NOTIFICATION);
			}
			
			progress.addToBar(0.1/cilia.size());
			progress.updateBarText("reconstructing cilia... (" + (i+1) + "/" + cilia.size() + ")");	
		}
		
		ciliaParticles.clear();
		ciliaParticles.trimToSize();
		totalNrOfDetectedCilia = cilia.size();	// New in v0.2.2: for every image ('of total #' was 0 without segmented basal bodies)
		
		/*
		 * Add basal bodies if selected
		 */
		if(segmentedBB){			
			//Detect basal bodies
			ArrayList<ArrayList<Uncalibrated3DPoint>> bbTemp = getBBObjectsTimelapse(imp, basalStainC, increaseRangeBB);
			if(bbTemp.size()!=1) {
				//TODO Evtl remove warning
				IJ.error("PROBLEM: Multiple time points for basal bodies derived from single-timepoint image!");
			}
			ArrayList<Uncalibrated3DPoint> bbParticles = bbTemp.get(0);
			
			nrOfBBs = bbParticles.size();
			if(nrOfBBs == 0) {
				// v0.2.2: the note of time-lapses, also when every basal body is below the minimum size
				progress.notifyMessage("No BBs found in image - no BBs added. File " + name
						+ " (Dir: " + dir + ")", ProgressReporter.NOTIFICATION);
			}
			
			//Assign cilia and basal bodies next:			
			double minDist, dist;
			double sklPoints [][];
			
			double matrixDist [][] = new double [cilia.size()][bbParticles.size()];
			double matrixDistSquared [][] = new double [cilia.size()][bbParticles.size()];
			Cilium theCilium;

			progress.updateBarText("assigning basal bodies to cilia... compute distances");
			for(int bb = 0; bb < bbParticles.size(); bb++){
				for(int cil = 0; cil < cilia.size(); cil++){
					theCilium = cilia.get(cil);
					if(theCilium.sklAvailable) {
						minDist = Double.POSITIVE_INFINITY;
						sklPoints = theCilium.getSkeletonPointsForOriginalImage();
						for(int skl = 0; skl < sklPoints.length; skl++) {
							dist = getDistance(bbParticles.get(bb).x * theCilium.calibration,
									bbParticles.get(bb).y * theCilium.calibration,
									bbParticles.get(bb).z * theCilium.voxelDepth,
									sklPoints[skl][0],
									sklPoints[skl][1],
									sklPoints[skl][2]);
							if(dist < minDist) {
								minDist = dist;
							}
						}
						matrixDist [cil][bb] =  minDist;
					}else {
						matrixDist [cil][bb] =  getDistance(bbParticles.get(bb).x * theCilium.calibration,
								bbParticles.get(bb).y * theCilium.calibration,
								bbParticles.get(bb).z * theCilium.voxelDepth,
								theCilium.xC,
								theCilium.yC,
								theCilium.zC);
					}
					
					// If out of range reset to border to out of range to let out of range connections not matter
					matrixDistSquared [cil][bb] = matrixDist [cil][bb];
					if(matrixDistSquared [cil][bb] > maxDistanceBBCiliumEnd) {
						matrixDistSquared [cil][bb] = maxDistanceBBCiliumEnd;
					}
					
					// Square the distance to push for closer connection // BUG FIXED FOR v0.2.2 Used matrixDist before.
					matrixDistSquared [cil][bb] = matrixDistSquared [cil][bb] * matrixDistSquared [cil][bb]; 
				}
			}
			
			//Applying HungarianAlgorithm = linear_sum_assignment in python to get best solution
			progress.updateBarText("assigning basal bodies to cilia... assigning by minimizing linear sum");
			int [] bbForCilium = new HungarianAlgorithm(matrixDistSquared).execute();
			

			//Now assign BBs to cilia
			int nrOfCiliaWithoutBBs = 0;
			boolean bbUsed [] = new boolean [bbParticles.size()];
			Arrays.fill(bbUsed,false);
			for(int cil = 0; cil < cilia.size(); cil++){
				progress.updateBarText("assigning basal bodies to cilia... adding basal body to cilium " + (cil+1) + " of " + cilia.size() + "!");
				if(bbForCilium[cil] > -1 && matrixDist[cil][bbForCilium[cil]] <= maxDistanceBBCiliumEnd) {
					cilia.get(cil).addBasalBody(bbParticles.get(bbForCilium[cil]), imp, measureC2local, channelC2, measureC3local, channelC3,
							channelReconstruction, progress);
					bbUsed[bbForCilium[cil]] = true;
				}else {
					nrOfCiliaWithoutBBs ++;
				}
			}

			int nrOfBBsWithoutCilia = 0;	// V0.2.2: Counted directly; the old arithmetic could go negative
			for(int bb = 0; bb < bbUsed.length; bb++) {
				if(!bbUsed[bb]) {
					nrOfBBsWithoutCilia++;
				}
			}
			
			// Exclusion or inclusion of BBs without cilia / BBs with cilia
			// "nothing based on BB-cilia-linking", "BBs without cilia", "cilia without BBs", "cilia without BBs and BBs without cilia"
			{
				if (bbCiliaFilterSelection.equals(bbCiliaFilterOptions[1]) || bbCiliaFilterSelection.equals(bbCiliaFilterOptions[3])) { // "BBs without cilia" or "cilia without BBs and BBs without cilia"
					// Exclude BBs without cilia - if selected
					for(int bb = 0; bb < bbParticles.size(); bb++) {
						if(!bbUsed[bb]) {
							excludedBBs ++;
						}
					}
				}
				
				if (bbCiliaFilterSelection.equals(bbCiliaFilterOptions[0]) || bbCiliaFilterSelection.equals(bbCiliaFilterOptions[2])) { //  "nothing based on BB-cilia-linking" or "cilia without BBs",
					// Add BBs as cilia object without cilium
					cilia.ensureCapacity(cilia.size() + nrOfBBsWithoutCilia);
					for(int bb = 0; bb < bbParticles.size(); bb++) {
						if(bbUsed[bb]) {
							continue;
						}
						// ADD the BB without cilium
						cilia.add(new Cilium(bbParticles.get(bb), 0, imp, measureC2local, channelC2, measureC3local, channelC3, measureBasalLocal, basalStainC, 
								channelReconstruction, gXY, gZ, intensityThresholds, progress, skeletonize));
					}
				}
				
				//Exclude cilia without BBs - if selected
				if (bbCiliaFilterSelection.equals(bbCiliaFilterOptions[2]) || bbCiliaFilterSelection.equals(bbCiliaFilterOptions[3])) { //  "cilia without BBs" or "cilia without BBs and BBs without cilia"
					for(int cil = 0; cil < cilia.size(); cil++){
						if(!cilia.get(cil).bbAvailable) {
							cilia.get(cil).excluded = true;
							excludedCiliaBB++;
						}
					}
				}
			}
		}
				
		{	
			// New in v0.2.2: does the reconstruction channel carry intensity information, and does it look like labels?
			int nObj = 0, nSingle = 0, nConst = 0, nCiliaMin2 = 0;
			java.util.HashSet<Double> constValues = new java.util.HashSet<Double>();
			for (int i = 0; i < cilia.size(); i++) {
				if (!cilia.get(i).ciliumAvailable || cilia.get(i).excluded)
					continue;
				nObj++;
				boolean single = cilia.get(i).minCiliumIntensity == cilia.get(i).maxCiliumIntensity;
				if (single)
					nSingle++;
				if (cilia.get(i).voxels < 2)
					continue; // a one-voxel cilium holds one value anyway
				nCiliaMin2++;
				if (single) {
					nConst++;
					constValues.add(Double.valueOf(cilia.get(i).minCiliumIntensity));
				}
			}
			noteSingleValuedObjects(nObj, nSingle, " (File " + name + ", Dir: " + dir + ")", progress);	// New in v0.2.2
			if (!labelModeCilia && nConst >= 3 && 2 * nConst >= nCiliaMin2 && constValues.size() >= 2) {	// New in v0.2.2: per image
				progress.notifyMessage("Label image without label mode? Most cilia hold one constant value each, and the"
						+ " values differ between cilia - the pattern of a label image (one value per object) analysed without"
						+ " label mode: touching cilia merge and label values are reported as intensities. If this is a label"
						+ " image, re-run with 'Contains labels' checked under Cilia (macro: label-mode-cilia)."
						+ " (File " + name + ", Dir: " + dir + ")", ProgressReporter.NOTIFICATION);
			}
		}
		
		for(int i = 0; i < cilia.size(); i++){
			if(cilia.get(i).excluded) {
				if(cilia.get(i).ciliumAvailable) {
					//write ID into image
					txtID = new TextRoi((int)Math.round(cilia.get(i).xC/imp.getCalibration().pixelWidth), 
							(int)Math.round(cilia.get(i).yC/imp.getCalibration().pixelHeight),
							dformat0.format(i+1), RoiFont);
					txtID.setStrokeColor(Color.YELLOW);					
					imp.getOverlay().add(txtID);
				}else if(cilia.get(i).bbAvailable) {
					//write ID into image
					txtID = new TextRoi((int)Math.round(cilia.get(i).bbX), 
							(int)Math.round(cilia.get(i).bbY),
							dformat0.format(i+1), RoiFont);
					txtID.setStrokeColor(Color.YELLOW);					
					imp.getOverlay().add(txtID);
				}
				continue;
			}
			if(!cilia.get(i).ciliumAvailable) {
				if(cilia.get(i).bbAvailable) {
					//write ID into image
					txtID = new TextRoi((int)Math.round(cilia.get(i).bbX), 
							(int)Math.round(cilia.get(i).bbY),
							dformat0.format(i+1), RoiFont);
					txtID.setStrokeColor(Color.WHITE);					
					imp.getOverlay().add(txtID);
				}			
				continue;
			}
			//Exclude cilia
			if(!tempExcludeSelection.equals(excludeOptions[0])){
				progress.updateBarText("Quantifying cilia objects (" + i + "/" + cilia.size() + " done): checking xyz borders...");
				touchesXY = false; touchesZ = false;
				Arrays.fill(sliceCounter, 0);
				
				for(int j = 0; j < cilia.get(i).points.length; j++){
					if(cilia.get(i).points [j][0] == 0.0 
							|| (int)Math.round(cilia.get(i).points [j][0] / imp.getCalibration().pixelWidth) == imp.getWidth()-1){
						touchesXY = true;
						break;
					}
					if(cilia.get(i).points [j][1] == 0.0 
							|| (int)Math.round(cilia.get(i).points [j][1] / imp.getCalibration().pixelHeight) == imp.getHeight()-1){
						touchesXY = true;
						break;
					}
					if(tempExcludeSelection.equals(excludeOptions[2])
							&& (cilia.get(i).points [j][2] == 0 
							|| (int)Math.round(cilia.get(i).points [j][2] / imp.getCalibration().pixelDepth) == imp.getNSlices()-1)){
						touchesZ = true;
						break;
					}
					
					sliceCounter [(int)Math.round(cilia.get(i).points [j][2] / imp.getCalibration().pixelDepth)]++;
				}
				
				if(touchesXY){
					cilia.get(i).excluded = true;
				}else if(tempExcludeSelection.equals(excludeOptions[2])){
					if(touchesZ){
						cilia.get(i).excluded = true;
					}
				}else if(tempExcludeSelection.equals(excludeOptions[3])){
					//check where most points are located
					if(tools.getMaximumIndex(sliceCounter) == 0 ||
							tools.getMaximumIndex(sliceCounter) == imp.getNSlices()-1){
						cilia.get(i).excluded = true;
					}							
				}
				
				if(cilia.get(i).excluded)	excludedCilia++;
				
				//write ID into image
				txtID = new TextRoi((int)Math.round(cilia.get(i).xC/imp.getCalibration().pixelWidth), 
						(int)Math.round(cilia.get(i).yC/imp.getCalibration().pixelHeight),
						dformat0.format(i+1), RoiFont);
				if(cilia.get(i).excluded){
					txtID.setStrokeColor(Color.YELLOW);
				}else{
					txtID.setStrokeColor(Color.WHITE);
				}
				imp.getOverlay().add(txtID);
			}else{
				//write ID into image
				txtID = new TextRoi((int)Math.round(cilia.get(i).xC/imp.getCalibration().pixelWidth), 
						(int)Math.round(cilia.get(i).yC/imp.getCalibration().pixelHeight),
						dformat0.format(i+1), RoiFont);
				txtID.setStrokeColor(Color.WHITE);
				imp.getOverlay().add(txtID);				
			}
								
			progress.addToBar(0.1/cilia.size());		
		}
	}		
	progress.updateBarText("" + cilia.size() + " cilia reconstructed!");
	

	double maximumArcLength = 0.0;
	int nrOfProfileCols = 0;
	if(measureC2local || measureC3local){
		for(int i = 0; i < cilia.size(); i++){
			if(cilia.get(i).excluded){
				continue;
			}
			if(cilia.get(i).sklAvailable && cilia.get(i).arcLength[cilia.get(i).arcLength.length-1] > maximumArcLength){
				maximumArcLength = cilia.get(i).arcLength[cilia.get(i).arcLength.length-1];
			}
		}
		nrOfProfileCols = (int) Math.round(maximumArcLength / calibration) + 1;
	}
	
	// SAVING
	
	Date currentDate = new Date();
	
	//Output images			
	if(saveSingleCiliaTifs){
		//save cilium-specific images
		for(int i = 0; i < cilia.size(); i++){
			if(cilia.get(i).excluded) continue;
			if(!cilia.get(i).ciliumAvailable) continue;
			progress.updateBarText("Writing single cilia images (" + i + "/" + cilia.size() + " done)");
			//save channel information
			OutputTextFile tp = new OutputTextFile("");
			tp.append("Channel information for results images obtained by analysis of:	" + name);
			tp.append("Saving date:	" + FullDateFormatter.format(currentDate)
			+ "	Starting date:	" + FullDateFormatter.format(startDate));
			tp.append("");
			
			tp.append("Results image:	" + filePrefix.substring(dir.length()) + "_C" + dformat0.format(i+1) + ".tif");
			tp.append("	channel 1 (cyan):	" + "reconstructed cilium");
			tp.append("	channel 2 (white):	" + "cell skeleton");
			tp.append("	channel 3 (green):	" + "intensity A");
			tp.append("	channel 4 (red):	" + "intensity B");
			
			addFooter(tp, currentDate);					
			if(!tp.saveAndFinish(subfolderPrefix + "_P" + dformat0.format(i+1) + "_ci.txt")) {
				progress.notifyMessage("IO ERROR when saving file " + (subfolderPrefix + "_P" + dformat0.format(i+1) + "_ci.txt"), ProgressReporter.ERROR);
			}
			
			ImagePlus impC = cilia.get(i).getCiliumImp(intensityThresholds, channelC2, channelC3);
			IJ.saveAsTiff(impC, subfolderPrefix + "_C" + dformat0.format(i+1) + ".tif"); 
			impC.changes = false;
			impC.close();					
		}					
	}
	
	progress.updateBarText("saving results...");
	progress.setBar(0.8);		
	
	//Output images
			
	//Save Textfile--------------------------------------	
	OutputTextFile tw1 = new OutputTextFile("");
	OutputTextFile tw2 = new OutputTextFile("");
	
	addSettingsBlockToPanel(tw1, currentDate, startDate, name, imp, 
			measureC2local, measureC3local, measureBasalLocal, intensityThresholds, excludedCilia,
			totalNrOfDetectedCilia, excludedBBs,nrOfBBs, excludedCiliaBB);
	
	tw1.append("Results:");				
	String appendTxt = "File name	";	
	appendTxt += "	" + "ID";
	appendTxt += "	" + "x center ["+calibrationDimension+"]";
	appendTxt += "	" + "y center ["+calibrationDimension+"]";
	appendTxt += "	" + "z center ["+calibrationDimension+"]";
	appendTxt += "	" + "Volume [voxel]";
	appendTxt += "	" + "Volume ["+calibrationDimension+"^3]";
	appendTxt += "	" + "# surface voxels";
	appendTxt += "	" + "Surface ["+calibrationDimension+"^2]";
	appendTxt += "	" + "Shape complexity index";
	appendTxt += "	" + "Sphere radius ["+calibrationDimension+"]";
	appendTxt += "	" + "Maximum span ["+calibrationDimension+"]";		// NEWly implemented in V0.2.2
	
	appendTxt += "	"; if(measureC2local){appendTxt += "A: Colocalized volume [" + calibrationDimension + "^3] (if channel in input image was background-removed)";}
	appendTxt += "	"; if(measureC2local){appendTxt += "A: Colocalized volume [% total volume] (if channel in input image was background-removed)";}
	appendTxt += "	"; if(measureC3local){appendTxt += "B: Colocalized volume [" + calibrationDimension + "^3] (if channel in input image was background-removed)";}
	appendTxt += "	"; if(measureC3local){appendTxt += "B: Colocalized volume [% total volume] (if channel in input image was background-removed)";}
	appendTxt += "	"; if(measureC2local){appendTxt += "A: Colocalized compared to BG volume [" + calibrationDimension + "^3]";}
	appendTxt += "	"; if(measureC2local){appendTxt += "A: Colocalized compared to BG volume [% total volume]";}
	appendTxt += "	"; if(measureC3local){appendTxt += "B: Colocalized compared to BG volume [" + calibrationDimension + "^3]";}
	appendTxt += "	"; if(measureC3local){appendTxt += "B: Colocalized compared to BG volume [% total volume]";}
	
	appendTxt += "	" + "minimum intensity (in reconstruction channel)";
	appendTxt += "	" + "maximum intensity (in reconstruction channel)";
	appendTxt += "	" + "average intensity of the 10% of voxels with highest intensity (in reconstruction channel)";
	appendTxt += "	" + "average intensity (in reconstruction channel)";
	appendTxt += "	" + "SD of intensity (in reconstruction channel)";
	
	appendTxt += "	"; if(measureC2local){appendTxt += "minimum A intensity";}
	appendTxt += "	"; if(measureC2local){appendTxt += "maximum A intensity";}
	appendTxt += "	"; if(measureC2local){appendTxt += "average A intensity of the 10% of voxels with highest A intensity";}
	appendTxt += "	"; if(measureC2local){appendTxt += "average A intensity";}
	appendTxt += "	"; if(measureC2local){appendTxt += "SD of A intensity";}
	
	appendTxt += "	"; if(measureC3local){appendTxt += "minimum B intensity";}
	appendTxt += "	"; if(measureC3local){appendTxt += "maximum B intensity";}
	appendTxt += "	"; if(measureC3local){appendTxt += "average B intensity of the 10% of voxels with highest B intensity";}
	appendTxt += "	"; if(measureC3local){appendTxt += "average B intensity";}
	appendTxt += "	"; if(measureC3local){appendTxt += "SD of B intensity";}
	
	appendTxt += "	"; if(skeletonize) appendTxt += "# of found skeletons (quality parameter)";
	appendTxt += "	"; if(skeletonize) appendTxt += "# branches (quality parameter)";
	appendTxt += "	"; if(skeletonize) appendTxt += "tree length [" + calibrationDimension + "] (quality parameter)";
	appendTxt += "	"; if(skeletonize) appendTxt += "cilia length [" + calibrationDimension + "] (largest shortest path of largest skeleton)";
	appendTxt += "	"; if(skeletonize) appendTxt += "orientation vector x [" + calibrationDimension + "] (vector from first to last skeleton point)";
	appendTxt += "	"; if(skeletonize) appendTxt += "orientation vector y [" + calibrationDimension + "] (vector from first to last skeleton point)";
	appendTxt += "	"; if(skeletonize) appendTxt += "orientation vector z [" + calibrationDimension + "] (vector from first to last skeleton point)";
	appendTxt += "	"; if(skeletonize) appendTxt += "cilia bending index (arc length of cilium / eucledian distance of first and last skeleton point)";

	appendTxt += "	"; if(measureC2local){appendTxt += "Intensity threshold A";}
	appendTxt += "	"; if(measureC3local){appendTxt += "Intensity threshold B";}
	appendTxt += "	"; if(measureBasalLocal){appendTxt += "Intensity threshold Basal Stain";}
	
	appendTxt += "	"; if(skeletonize && measureC2local) appendTxt += "Integrated A intensity";
	appendTxt += "	"; if(skeletonize && measureC2local) appendTxt += "Average A intensity on center line";
	appendTxt += "	"; if(skeletonize && measureC3local) appendTxt += "Integrated B intensity";
	appendTxt += "	"; if(skeletonize && measureC3local) appendTxt += "Average B intensity on center line";
	
	appendTxt += "	"; if(skeletonize && measureC2local) appendTxt += "A: Colocalized on centerline compared to BG volume [" + calibrationDimension + "]";
	appendTxt += "	"; if(skeletonize && measureC2local) appendTxt += "A: Colocalized on centerline compared to BG volume [% total length]";
	appendTxt += "	"; if(skeletonize && measureC3local) appendTxt += "B: Colocalized on centerline compared to BG volume [" + calibrationDimension + "]";
	appendTxt += "	"; if(skeletonize && measureC3local) appendTxt += "B: Colocalized on centerline compared to BG volume [% total length]";
	
	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody x center ["+calibrationDimension+"]";;
	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody y center ["+calibrationDimension+"]";;
	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody z center ["+calibrationDimension+"]";;

	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody center A intensity";
	appendTxt += "	"; if(segmentedBB) appendTxt += "A intensity within 1 "+calibrationDimension+" to basal body";
	appendTxt += "	"; if(segmentedBB) appendTxt += "A intensity 1-2 "+calibrationDimension+" to basal body";
	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody center B intensity";
	appendTxt += "	"; if(segmentedBB) appendTxt += "B intensity within 1 "+calibrationDimension+" to basal body";
	appendTxt += "	"; if(segmentedBB) appendTxt += "B intensity 1-2 "+calibrationDimension+" to basal body";
	
	//From v0.2.0 we do no longer print the profiles in the main file, facilitating analysis
	//Profiles can instead be loaded and plotted programmatically from another file
	//TODO think about output separately
//	if(skeletonize){
//		//profiles
//		if(measureC2local){
//			appendTxt += "	" + "Profile A (arc length step: " + dformat6.format(calibration) + ")";
//			for(int i = 0; i < nrOfProfileCols-1; i++){
//				appendTxt += "	";
//			}
//			appendTxt += "	" + "Profile A (normalized to reconstruction channel) (arc length step: " + dformat6.format(calibration) + ")";
//			for(int i = 0; i < nrOfProfileCols-1; i++){
//				appendTxt += "	";
//			}
//		}
//			
//		if(measureC3local){
//			appendTxt += "	" + "Profile B (arc length step: " + dformat6.format(calibration) + ")";
//			for(int i = 0; i < nrOfProfileCols-1; i++){
//				appendTxt += "	";
//			}
//			appendTxt += "	" + "Profile B (normalized to reconstruction channel) (arc length step: " + dformat6.format(calibration) + ")";
//			for(int i = 0; i < nrOfProfileCols-1; i++){
//				appendTxt += "	";
//			}
//		}				
//	}
						
	tw1.append(""+appendTxt);
	
	double [] coloc, iProfileC2, iProfileC3;
	for(int i = 0; i < cilia.size(); i++){
		if(cilia.get(i).excluded){
			continue;
		}
		appendTxt = "	";	
		appendTxt += "	"; appendTxt += dformat0.format(i+1); //"ID";
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).xC);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).yC);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).zC);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat0.format(cilia.get(i).voxels);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).volume);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat0.format(cilia.get(i).surfaceVoxels);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).surface);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).shapeComplexity);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).sphereRadius);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).maximumSpan);}		// Implemented in v0.2.2
		
		appendTxt += "	"; if(measureC2local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).colocalizedVolumeC2);}
		appendTxt += "	"; if(measureC2local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(100.0*cilia.get(i).colocalizedFractionC2);}
		appendTxt += "	"; if(measureC3local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).colocalizedVolumeC3);}
		appendTxt += "	"; if(measureC3local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(100.0*cilia.get(i).colocalizedFractionC3);}
		appendTxt += "	"; if(measureC2local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).colocalizedCompToBGVolumeC2);}
		appendTxt += "	"; if(measureC2local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(100.0*cilia.get(i).colocalizedCompToBGFractionC2);}
		appendTxt += "	"; if(measureC3local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).colocalizedCompToBGVolumeC3);}
		appendTxt += "	"; if(measureC3local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(100.0*cilia.get(i).colocalizedCompToBGFractionC3);}
		
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).minCiliumIntensity);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).maxCiliumIntensity);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).maxTenPercentCiliumIntensity);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).averageCiliumIntensity);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable){appendTxt += fmt6(cilia.get(i).SDCiliumIntensity);}
		
		appendTxt += "	"; if(measureC2local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).minC2Intensity);}
		appendTxt += "	"; if(measureC2local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).maxC2Intensity);}
		appendTxt += "	"; if(measureC2local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).maxTenPercentC2Intensity);}
		appendTxt += "	"; if(measureC2local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).averageC2Intensity);}
		appendTxt += "	"; if(measureC2local && cilia.get(i).ciliumAvailable){appendTxt += fmt6(cilia.get(i).SDC2Intensity);}
		
		appendTxt += "	"; if(measureC3local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).minC3Intensity);}
		appendTxt += "	"; if(measureC3local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).maxC3Intensity);}
		appendTxt += "	"; if(measureC3local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).maxTenPercentC3Intensity);}
		appendTxt += "	"; if(measureC3local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilia.get(i).averageC3Intensity);}
		appendTxt += "	"; if(measureC3local && cilia.get(i).ciliumAvailable){appendTxt += fmt6(cilia.get(i).SDC3Intensity);}
		
		appendTxt += "	"; if(skeletonize && cilia.get(i).ciliumAvailable) appendTxt += dformat0.format(cilia.get(i).foundSkl);
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable && cilia.get(i).sklAvailable){appendTxt += dformat0.format(cilia.get(i).branches);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable && cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilia.get(i).treeLength);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable && cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilia.get(i).largestShortestPathOfLargest);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable && cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilia.get(i).orientationVector[0]);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable && cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilia.get(i).orientationVector[1]);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable && cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilia.get(i).orientationVector[2]);}
		appendTxt += "	"; if(cilia.get(i).ciliumAvailable && cilia.get(i).sklAvailable){appendTxt += fmt6(cilia.get(i).bendingIndex);}
		
		appendTxt += "	"; if(measureC2local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(intensityThresholds[channelC2-1]);}
		appendTxt += "	"; if(measureC3local && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(intensityThresholds[channelC3-1]);}
		appendTxt += "	"; if(measureBasalLocal && cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(intensityThresholds[basalStainC-1]);}
				
		//profiles
		if(skeletonize){
			if(measureC2local && cilia.get(i).ciliumAvailable){
				iProfileC2 = cilia.get(i).getIntensityProfile(2, calibration, false);
				appendTxt += "	"; 
				if(iProfileC2!=null && cilia.get(i).sklAvailable)	appendTxt += fmt6(tools.getSum(iProfileC2));
				appendTxt += "	"; 
				if(iProfileC2!=null && cilia.get(i).sklAvailable)	appendTxt += fmt6(tools.getAverage(iProfileC2));
			}else{
				iProfileC2  = null;
				appendTxt += "		";
			}
			
			if(measureC3local && cilia.get(i).ciliumAvailable){
				iProfileC3 = cilia.get(i).getIntensityProfile(3, calibration, false);
				appendTxt += "	";
				if(iProfileC3!=null && cilia.get(i).sklAvailable)	appendTxt += fmt6(tools.getSum(iProfileC3));
				appendTxt += "	"; 
				if(iProfileC3!=null && cilia.get(i).sklAvailable)	appendTxt += fmt6(tools.getAverage(iProfileC3));
			}else{
				iProfileC3  = null;
				appendTxt += "		";
			}					

			if(measureC2local && iProfileC2 != null && cilia.get(i).ciliumAvailable){
				//Colocalized length
				appendTxt += "	";
				coloc = getColocalizedLengthsOfProfile(iProfileC2, intensityThresholds[channelC2-1], calibration);
				appendTxt += dformat6.format(coloc[0]);
				appendTxt += "	";
				appendTxt += dformat6.format(coloc[1]);					
			}else{
				appendTxt += "		";
			}	
			
			if(measureC3local && iProfileC3 != null && cilia.get(i).ciliumAvailable){
				//Colocalized length
				appendTxt += "	";
				coloc = getColocalizedLengthsOfProfile(iProfileC3, intensityThresholds[channelC3-1], calibration);
				appendTxt += dformat6.format(coloc[0]);
				appendTxt += "	";
				appendTxt += dformat6.format(coloc[1]);
			}else{
				appendTxt += "		";
			}
		}else{
			appendTxt += "								";	// v0.2.2: the 8 centre-line cells stay empty, so the basal-body cells below keep their columns
		}
		
		// v0.2.2: the basal-body cells are also written without skeleton results (before were written within the if(skeletonize) clause above)
		appendTxt += "	"; if(segmentedBB && cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilia.get(i).bbX * calibration);
		appendTxt += "	"; if(segmentedBB && cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilia.get(i).bbY * calibration);
		appendTxt += "	"; if(segmentedBB && cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilia.get(i).bbZ * voxelDepth);

		appendTxt += "	"; if(segmentedBB && cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilia.get(i).bbCenterIntensityC2);
		appendTxt += "	"; if(segmentedBB && cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilia.get(i).bbIntensityRadius1C2);
		appendTxt += "	"; if(segmentedBB && cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilia.get(i).bbIntensityRadius2C2);
		appendTxt += "	"; if(segmentedBB && cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilia.get(i).bbCenterIntensityC3);
		appendTxt += "	"; if(segmentedBB && cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilia.get(i).bbIntensityRadius1C3);
		appendTxt += "	"; if(segmentedBB && cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilia.get(i).bbIntensityRadius2C3);
				
		appendTxt = name + appendTxt;
		tw1.append(""+appendTxt);
		appendTxt += getOneRowFooter(currentDate);
		tw2.append(""+appendTxt);
	}

	appendNotesLine(tw1, filePrefix);
	addFooter(tw1, currentDate);
	if(!tw1.saveAndFinish(filePrefix + ".txt")) {
		progress.notifyMessage("IO ERROR when saving file " + filePrefix + ".txt", ProgressReporter.ERROR);
	}
	
	//save one row results file
	if(!tw2.saveAndFinish(filePrefix + "s.txt")) {
		progress.notifyMessage("Did not save file " + (filePrefix + "s.txt because empty"), ProgressReporter.NOTIFICATION);
	}
	
	//save profile text file
	saveSkeletonPoints(cilia, dir, name, filePrefix + "l.txt", currentDate);
	
	//save RP image
	progress.updateBarText("saving images...");
	progress.setBar(0.85);
		
	IJ.saveAsTiff(imp, filePrefix + "_RP"); 

	//save 3D images
	if(saveSingleCilia3DImages){				
		ImagePlus impTemp;
		for(int i = 0; i < cilia.size(); i++){
			if(!cilia.get(i).ciliumAvailable) {
				continue;
			}
			progress.updateBarText("Producing 3D images of individual cilia (" + i + "/" + cilia.size() + " done)");
			impTemp = cilia.get(i).getCiliumImp(intensityThresholds, channelC2, channelC3);
			saveImageAs3D(subfolderPrefix + "_C" + (i+1) + "",impTemp, false, true, false);
			impTemp.changes = false;
			impTemp.close();
		}
	}		
	{		
		progress.updateBarText("Producing Overview Images");
		if(skeletonize){
			saveSkeletonOverviewImageNonTimeLapse(filePrefix + "_SKL", imp, cilia,
					name, dir, currentDate, startDate);
		}
		if(saveOverview3DImages){
			progress.updateBarText("Producing Overview 3D Image");	
			saveImageAs3D(filePrefix + "_RP", reduceToMaskChannel(imp, channelReconstruction), false, true, false);
		}		
	}	
	
	if(selectedTaskVariant.equals(taskVariant[1])) {
		imp.changes = false;
		imp.close();
	}
	progress.setBar(0.95);
}

/**
 * Workflow for TIMELAPSE analysis
 * */
private void analyzeCiliaIn4DAndSaveResults(ImagePlus imp, boolean measureC2local, boolean measureC3local, boolean measureBasalLocal, 
		String name, String dir, Date startDate, String filePrefix, String subfolderPrefix, String tempExcludeSelection){

	if(frameGapKeyMissing){
		progress.notifyMessage("Frame gap 0: the macro has no max-frame-gap= (written before v0.2.2, when the classic"
				+ " connection had no frame gap; 0 reproduces it). (File " + name + ", Dir: " + dir + ")", ProgressReporter.NOTIFICATION);
	}

	ArrayList<TimelapseCilium> timelapseCilia = new ArrayList<TimelapseCilium>();
	double intensityThresholds [] = new double[imp.getNChannels()]; //for each channel individual
	
	TextRoi txtID;
	imp.setOverlay(new Overlay());
	imp.setHideOverlay(false);
	
	int excludedCilia = 0;
	int excludedCiliaBB = 0;	// New in v0.2.2: the cilia the basal-body filter removed, counted apart from the border option
	int excludedBBs = 0;
	int totalNrOfBBs = 0;
	
	if(timelapseTrackingSelection.equals(timelapseTrackingOptions[0])
			|| timelapseTrackingSelection.equals(timelapseTrackingOptions[2])) { 
		//Classic tracking (connecting pixel by pixel over time), same for labelID tracking
		//Retrieving ciliary objects from the image
		ArrayList<ArrayList<CellPoint>> ciliaParticles = getCiliaObjectsTimelapse(imp, channelReconstruction, increaseRangeCilia, true);	//Method changed on 23.04.2019
		if(ciliaParticles.size() == 0) {
			progress.notifyMessage("No cilia found in image " + name 
					+ " (Dir: " + dir + ") - skipped analysis", ProgressReporter.NOTIFICATION);
			return;
		}
		
		progress.updateBarText("Cilia reconstruction completed.");
				
		//Determine intensity thresholds for each other channel
		progress.updateBarText("Determining intensity thresholds...");
		intensityThresholds = getIntensityThresholds(imp, ciliaParticles);
		
		//Create timelapse cilia objects
		timelapseCilia.ensureCapacity(ciliaParticles.size());
		for(int i = 0; i < ciliaParticles.size(); i++){
			progress.updateBarText("Quantifying cilia (" + i + "/" + ciliaParticles.size() + " done)");
			timelapseCilia.add(new TimelapseCilium(ciliaParticles.get(i), imp, measureC2local, channelC2, measureC3local, channelC3, measureBasalLocal, basalStainC,
					channelReconstruction, gXY, gZ, intensityThresholds, progress, skeletonize, segmentedBB, joinGap));
			if(timelapseCilia.size()!=i+1) {
				progress.notifyMessage("Error while measuring cilium " + (i+1)
						+ " for file " + name + " (Dir: " + dir + ").", ProgressReporter.NOTIFICATION);
				return;
			}
		}
		
		/*
		 * Add basal bodies if selected
		 */		
		if(segmentedBB){			
			//Detect basal bodies
			ArrayList<ArrayList<Uncalibrated3DPoint>> bbParticles = getBBObjectsTimelapse(imp, basalStainC, increaseRangeBB);
			for(int t = 0; t < bbParticles.size(); t++) {
				totalNrOfBBs += bbParticles.get(t).size();
			}
			
			if(totalNrOfBBs == 0) {
				progress.notifyMessage("No BBs found in image - no BBs added. File " + name
						+ " (Dir: " + dir + ")", ProgressReporter.NOTIFICATION);	// the analysis goes on (as in linear assignment)
				if (bbCiliaFilterSelection.equals(bbCiliaFilterOptions[2]) || bbCiliaFilterSelection.equals(bbCiliaFilterOptions[3])) { //  "cilia without BBs" or "cilia without BBs and BBs without cilia"
					// Exclude Cilia without BBs. If no BBs detected, need to cancel whole image.
					progress.notifyMessage("Since no BBs found and cilia without BBs excluded, no cilia remaining in file " + name 
							+ " (Dir: " + dir + ") - skipped analysis", ProgressReporter.NOTIFICATION);
					return;
				}
			}else {
				//Iterate over all time points and within each time point assign bbs and cilia
				int [][] bbObject = new int [bbParticles.size()][];	// v0.2.2: per frame and basal body, the object whose cilium it was matched to (-1 = none)
				for(int t = 0; t < bbParticles.size(); t++) {
					bbObject [t] = new int [bbParticles.get(t).size()];
					Arrays.fill(bbObject [t], -1);
					
					//Create a list of cilia with IDs for this timepoint
					ArrayList <Cilium> cilia = new ArrayList <Cilium>(bbParticles.get(t).size());
					ArrayList <Integer> ciliaIDs = new ArrayList <Integer>(bbParticles.get(t).size());
					
					for(int tc = 0; tc < timelapseCilia.size(); tc++) {
						for(int cil = 0; cil < timelapseCilia.get(tc).cilia.size(); cil++) {
							if(timelapseCilia.get(tc).cilia.get(cil).t == t) {
								cilia.add(timelapseCilia.get(tc).cilia.get(cil));
								ciliaIDs.add(tc);
							}
						}
					}
					
					//Assign cilia and basal bodies next:			
					double matrixDist [][] = new double [cilia.size()][bbParticles.get(t).size()];
					double matrixDistSquared [][] = new double [cilia.size()][bbParticles.get(t).size()];

					progress.updateBarText("assigning basal bodies to cilia (t "
							+ t + ")... compute distances");
					
					for(int bb = 0; bb < bbParticles.get(t).size(); bb++){
						for(int cil = 0; cil < cilia.size(); cil++){
							matrixDist [cil][bb] = bbToCiliumDistance(cilia.get(cil), bbParticles.get(t).get(bb));	// v0.2.2: a helper
							
							// If out of range reset to border to out of range to let out of range connections not matter
							matrixDistSquared [cil][bb] = matrixDist [cil][bb];
							if(matrixDistSquared [cil][bb] > maxDistanceBBCiliumEnd) {
								matrixDistSquared [cil][bb] = maxDistanceBBCiliumEnd;
							}
							
							// Square the distance to push for closer connection.
							// Bug fixed in v0.2.2 - before multiplied matrixDist and not the corrected matrixDistSquared
							matrixDistSquared [cil][bb] = matrixDistSquared [cil][bb] * matrixDistSquared [cil][bb]; 
						}
					}
					
					//Applying HungarianAlgorithm = linear_sum_assignment in python to get best solution
					progress.updateBarText("assigning basal bodies to cilia... assigning by minimizing linear sum");
					
					// a frame without cilia: nothing to assign, its basal bodies stay unassigned (the assignment needs a
					// row: it crashed on an empty matrix)
					int [] bbForCilium = cilia.size() == 0 ? new int [0] : new HungarianAlgorithm(matrixDistSquared).execute();

					//Now assign BBs to cilia
					for(int cil = 0; cil < cilia.size(); cil++){
						progress.updateBarText("assigning basal bodies to cilia... adding basal body to cilium " + (cil+1) + " of " + cilia.size() + "!");
						if(bbForCilium[cil] > -1 && matrixDist[cil][bbForCilium[cil]] <= maxDistanceBBCiliumEnd) {
							cilia.get(cil).addBasalBody(bbParticles.get(t).get(bbForCilium[cil]), imp, measureC2local, channelC2, measureC3local, channelC3,
									channelReconstruction, progress);
							bbObject [t][bbForCilium[cil]] = ciliaIDs.get(cil);
						}
					}
				}

				// v0.2.2: the basal bodies across time, the objects of basal bodies only and the basal-body filter
				progress.updateBarText("linking basal bodies across time...");
				excludedBBs += addBasalBodiesAcrossTime(timelapseCilia, bbParticles, bbObject, imp,
						measureC2local, measureC3local, measureBasalLocal, intensityThresholds);
			}

		}
				
		// Compute timelapse parameters
		for(int i = 0; i < timelapseCilia.size(); i++){
			timelapseCilia.get(i).computeBBParameters();
		}
		
		ciliaParticles.clear();
		ciliaParticles.trimToSize();
	}else {
		// NEW in v0.2.2: linear-assignment branch
		timelapseCilia = trackCiliaLinearAssignment(imp, measureC2local, measureC3local,
				measureBasalLocal, intensityThresholds, dir, name);
		if(timelapseCilia.size() == 0){
			progress.notifyMessage("No cilia found in image " + name + " (Dir: " + dir + ") - skipped analysis", ProgressReporter.NOTIFICATION);
			return;
		}
		
		// the basal-body counts of the settings block: the tracking keeps every basal body in one track, with its
		// cilium or in a track of basal bodies only (those the filter removed count as excluded)
		if(segmentedBB){
			for(int i = 0; i < timelapseCilia.size(); i++){
				int bbFrames = 0;
				for(int k = 0; k < timelapseCilia.get(i).cilia.size(); k++){
					if(timelapseCilia.get(i).cilia.get(k).bbAvailable)	bbFrames++;
				}
				totalNrOfBBs += bbFrames;
				if(timelapseCilia.get(i).excluded && !timelapseCilia.get(i).ciliumAvailable)	excludedBBs += bbFrames;
			}
		}
	}

	/*
	 * The basal-body filter count, the border exclusion and the IDs, for every connection mode
	 */
	
	// New in v0.2.2: the cilia the basal-body filter removed, each once (counted per frame before, under the border option)
	for(int i = 0; i < timelapseCilia.size(); i++) {
		if(timelapseCilia.get(i).excluded && timelapseCilia.get(i).ciliumAvailable)	excludedCiliaBB++;	// cilia only: linear assignment also removes tracks of basal bodies only
	}

	boolean touchesXY, touchesZ;
	int [] sliceCounter = new int [imp.getNSlices()];
	
	// Exclude cilia if touching borders TODO IMPROVE
	for(int i = 0; i < timelapseCilia.size(); i++){
		if(timelapseCilia.get(i).excluded) {
			if(timelapseCilia.get(i).ciliumAvailable) {
				//write ID into image
				txtID = new TextRoi((int)Math.round(timelapseCilia.get(i).xCAvg/imp.getCalibration().pixelWidth), 
						(int)Math.round(timelapseCilia.get(i).yCAvg/imp.getCalibration().pixelHeight),
						dformat0.format(i+1), RoiFont);
				txtID.setStrokeColor(Color.YELLOW);					
				imp.getOverlay().add(txtID);
			}else if(timelapseCilia.get(i).bbAvailable) {
				//write ID into image
				txtID = new TextRoi((int)Math.round(timelapseCilia.get(i).bbXAvg), 
						(int)Math.round(timelapseCilia.get(i).bbYAvg),
						dformat0.format(i+1), RoiFont);
				txtID.setStrokeColor(Color.YELLOW);					
				imp.getOverlay().add(txtID);
			}
			continue;
		}
		if(!timelapseCilia.get(i).ciliumAvailable) {
			if(timelapseCilia.get(i).bbAvailable) {
				//write ID into image
				txtID = new TextRoi((int)Math.round(timelapseCilia.get(i).bbXAvg), 
						(int)Math.round(timelapseCilia.get(i).bbYAvg),
						dformat0.format(i+1), RoiFont);
				txtID.setStrokeColor(Color.WHITE);					
				imp.getOverlay().add(txtID);
			}			
			continue;
		}
		
		//Exclude cilia if selected
		if(!timelapseCilia.get(i).excluded && !tempExcludeSelection.equals(excludeOptions[0])){
			progress.updateBarText("Quantifying cilia (" + i + "/" + timelapseCilia.size() + " done): checking xyz borders...");
			touchesXY = false; touchesZ = false;
			Arrays.fill(sliceCounter, 0);
			for(int k = 0; k < timelapseCilia.get(i).cilia.size(); k++){
				if(!timelapseCilia.get(i).cilia.get(k).ciliumAvailable)	continue;	// a frame with the basal body only (linear assignment) has no voxels
				for(int j = 0; j < timelapseCilia.get(i).cilia.get(k).points.length; j++){
					if(timelapseCilia.get(i).cilia.get(k).points [j][0] == 0.0 
							|| (int)Math.round(timelapseCilia.get(i).cilia.get(k).points [j][0] / imp.getCalibration().pixelWidth) == imp.getWidth()-1){
						touchesXY = true;
						break;
					}
					if(timelapseCilia.get(i).cilia.get(k).points [j][1] == 0.0 
							|| (int)Math.round(timelapseCilia.get(i).cilia.get(k).points [j][1] / imp.getCalibration().pixelHeight) == imp.getHeight()-1){
						touchesXY = true;
						break;
					}
					if(tempExcludeSelection.equals(excludeOptions[2])
							&& (timelapseCilia.get(i).cilia.get(k).points [j][2] == 0 
							|| (int)Math.round(timelapseCilia.get(i).cilia.get(k).points [j][2] / imp.getCalibration().pixelDepth) == imp.getNSlices()-1)){
						touchesZ = true;
						break;
					}						
					sliceCounter [(int)Math.round(timelapseCilia.get(i).cilia.get(k).points [j][2] / imp.getCalibration().pixelDepth)]++;
				}
			}
							
			if(touchesXY){
				timelapseCilia.get(i).excluded = true;
			}else if(tempExcludeSelection.equals(excludeOptions[2])){
				if(touchesZ){
					timelapseCilia.get(i).excluded = true;
				}
			}else if(tempExcludeSelection.equals(excludeOptions[3])){
				//check where most points are located
				if(tools.getMaximumIndex(sliceCounter) == 0 ||
						tools.getMaximumIndex(sliceCounter) == imp.getNSlices()-1){
					timelapseCilia.get(i).excluded = true;
				}							
			}
			
			if(timelapseCilia.get(i).excluded)	excludedCilia++;
			
			progress.updateBarText("filter cilia... (" + (i+1) + "/" + timelapseCilia.size() + ")");
		}
		
		//write ID into image
		txtID = new TextRoi((int)Math.round(timelapseCilia.get(i).xCAvg/imp.getCalibration().pixelWidth), 
				(int)Math.round(timelapseCilia.get(i).yCAvg/imp.getCalibration().pixelHeight),
				dformat0.format(i+1), RoiFont);
		if(timelapseCilia.get(i).excluded){
			txtID.setStrokeColor(Color.YELLOW);
		}else{
			txtID.setStrokeColor(Color.WHITE);
		}
		imp.getOverlay().add(txtID);
							
		progress.addToBar(0.2/timelapseCilia.size());
		progress.updateBarText("reconstructing cilia... (" + (i+1) + "/" + timelapseCilia.size() + ")");
	}
	progress.updateBarText("" + timelapseCilia.size() + " cilia reconstructed!");
	
//	for saving profiles
	double maximumArcLength = 0.0;
	int nrOfProfileCols = 0;
	if(measureC2local || measureC3local){
		for(int i = 0; i < timelapseCilia.size(); i++){
			if(timelapseCilia.get(i).excluded || !timelapseCilia.get(i).ciliumAvailable){
				continue;
			}
			for(int k = 0; k < timelapseCilia.get(i).cilia.size(); k++){
				if(timelapseCilia.get(i).cilia.get(k).excluded){
					continue;
				}				
				if(timelapseCilia.get(i).cilia.get(k).sklAvailable
						&& timelapseCilia.get(i).cilia.get(k).arcLength[timelapseCilia.get(i).cilia.get(k).arcLength.length-1] > maximumArcLength){
					maximumArcLength = timelapseCilia.get(i).cilia.get(k).arcLength[timelapseCilia.get(i).cilia.get(k).arcLength.length-1];
				}
			}			
		}
		nrOfProfileCols = (int) Math.round(maximumArcLength / calibration) + 1;
	}
	// SAVING
	
	Date currentDate = new Date();
		
	progress.updateBarText("saving results...");
	progress.setBar(0.9);
	
	//Output images
			
	//Save Textfile--------------------------------------	
	OutputTextFile tw1 = new OutputTextFile ("");
	OutputTextFile tw2 = new OutputTextFile ("");

	// 'of total #': the objects with a cilium, as in single images (objects of basal bodies only do not count)
	int totalNrOfDetectedCilia = 0;
	for(int i = 0; i < timelapseCilia.size(); i++){
		if(timelapseCilia.get(i).ciliumAvailable)	totalNrOfDetectedCilia++;
	}

	addSettingsBlockToPanel(tw1, currentDate, startDate, name, imp,
			measureC2local, measureC3local, measureBasalLocal, intensityThresholds, excludedCilia, totalNrOfDetectedCilia, excludedBBs, totalNrOfBBs, excludedCiliaBB);	// New in v0.2.2: the basal-body counts (were 0, 0)
	

	
	tw1.append("Averaged results of the time-course analysis:");				
	String appendTxt = "	";	
	appendTxt += "	" + "ID";
	appendTxt += "	" + "x center (time-averaged) ["+calibrationDimension+"]";
	appendTxt += "	" + "y center (time-averaged) ["+calibrationDimension+"]";
	appendTxt += "	" + "z center (time-averaged) ["+calibrationDimension+"]";
	appendTxt += "	" + "volume (time-averaged) [voxel]";
	appendTxt += "	" + "volume (time-averaged) ["+calibrationDimension+"^3]";
	appendTxt += "	" + "# surface voxels (time-averaged)";
	appendTxt += "	" + "surface (time-averaged) ["+calibrationDimension+"^2]";
	appendTxt += "	" + "shape complexity index (time-averaged)";
	appendTxt += "	" + "sphere radius (time-averaged) ["+calibrationDimension+"]";
	appendTxt += "	" + "Maximum span (time-averaged) ["+calibrationDimension+"]";		// Implemented in V0.2.2
	
	
	appendTxt += "	"; if(measureC2local){appendTxt += "A: Colocalized volume (time-averaged) [" + calibrationDimension + "^3] (if channel in input image was background-removed)";}
	appendTxt += "	"; if(measureC2local){appendTxt += "A: Colocalized volume (time-averaged) [% total volume] (if channel in input image was background-removed)";}
	appendTxt += "	"; if(measureC3local){appendTxt += "B: Colocalized volume (time-averaged) [" + calibrationDimension + "^3] (if channel in input image was background-removed)";}
	appendTxt += "	"; if(measureC3local){appendTxt += "B: Colocalized volume (time-averaged) [% total volume] (if channel in input image was background-removed)";}
	appendTxt += "	"; if(measureC2local){appendTxt += "A: Colocalized compared to BG volume (time-averaged) [" + calibrationDimension + "^3]";}
	appendTxt += "	"; if(measureC2local){appendTxt += "A: Colocalized compared to BG volume (time-averaged) [% total volume]";}
	appendTxt += "	"; if(measureC3local){appendTxt += "B: Colocalized compared to BG volume (time-averaged) [" + calibrationDimension + "^3]";}
	appendTxt += "	"; if(measureC3local){appendTxt += "B: Colocalized compared to BG volume (time-averaged) [% total volume]";}
	
	appendTxt += "	" + "minimum intensity (in reconstruction channel) (time-averaged)";
	appendTxt += "	" + "maximum intensity (in reconstruction channel) (time-averaged)";
	appendTxt += "	" + "average intensity of the 10% of voxels with highest intensity (in reconstruction channel) (time-averaged)";
	appendTxt += "	" + "average intensity (in reconstruction channel) (time-averaged)";
	appendTxt += "	" + "SD of intensity (in reconstruction channel) (time-averaged)";
	
	appendTxt += "	"; if(measureC2local){appendTxt += "minimum A intensity (time-averaged)";}
	appendTxt += "	"; if(measureC2local){appendTxt += "maximum A intensity (time-averaged)";}
	appendTxt += "	"; if(measureC2local){appendTxt += "average A intensity of the 10% of voxels with highest A intensity (time-averaged)";}
	appendTxt += "	"; if(measureC2local){appendTxt += "average A intensity (time-averaged)";}
	appendTxt += "	"; if(measureC2local){appendTxt += "SD of A intensity (time-averaged)";}
	
	appendTxt += "	"; if(measureC3local){appendTxt += "minimum B intensity (time-averaged)";}
	appendTxt += "	"; if(measureC3local){appendTxt += "maximum B intensity (time-averaged)";}
	appendTxt += "	"; if(measureC3local){appendTxt += "average B intensity of the 10% of voxels with highest B intensity (time-averaged)";}
	appendTxt += "	"; if(measureC3local){appendTxt += "average B intensity (time-averaged)";}
	appendTxt += "	"; if(measureC3local){appendTxt += "SD of B intensity (time-averaged)";}
	
	appendTxt += "	"; if(skeletonize) appendTxt += "# of found skeletons (quality parameter) (time-averaged)";
	appendTxt += "	"; if(skeletonize) appendTxt += "Skl available in all frames?";
	appendTxt += "	"; if(skeletonize) appendTxt += "# branches (quality parameter) (time-averaged)";
	appendTxt += "	"; if(skeletonize) appendTxt += "tree length [" + calibrationDimension + "] (quality parameter) (time-averaged)";
	appendTxt += "	"; if(skeletonize) appendTxt += "cilia length [" + calibrationDimension + "] (largest shortest path of largest skeleton) (time-averaged)";
	appendTxt += "	"; if(skeletonize) appendTxt += "orientation vector x [" + calibrationDimension + "] (vector from first to last skeleton point) (time-averaged)";
	appendTxt += "	"; if(skeletonize) appendTxt += "orientation vector y [" + calibrationDimension + "] (vector from first to last skeleton point) (time-averaged)";
	appendTxt += "	"; if(skeletonize) appendTxt += "orientation vector z [" + calibrationDimension + "] (vector from first to last skeleton point) (time-averaged)";
	appendTxt += "	"; if(skeletonize) appendTxt += "cilia bending index (arc length of cilium / eucledian distance of first and last skeleton point) (time-averaged)";
	
	appendTxt += "	"; if(measureC2local){appendTxt += "Intensity threshold A";}
	appendTxt += "	"; if(measureC3local){appendTxt += "Intensity threshold B";}
	appendTxt += "	"; if(measureBasalLocal){appendTxt += "Intensity threshold Basal Stain";}
	
	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody x center (time-averaged) ["+calibrationDimension+"]";
	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody y center (time-averaged) ["+calibrationDimension+"]";
	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody z center (time-averaged) ["+calibrationDimension+"]";

	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody center A intensity (time-averaged)";
	appendTxt += "	"; if(segmentedBB) appendTxt += "A intensity within 1 "+calibrationDimension+" to basal body (time-averaged)";
	appendTxt += "	"; if(segmentedBB) appendTxt += "A intensity 1-2 "+calibrationDimension+" to basal body (time-averaged)";
	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody center B intensity (time-averaged)";
	appendTxt += "	"; if(segmentedBB) appendTxt += "B intensity within 1 "+calibrationDimension+" to basal body (time-averaged)";
	appendTxt += "	"; if(segmentedBB) appendTxt += "B intensity 1-2 "+calibrationDimension+" to basal body (time-averaged)";

	tw1.append(""+appendTxt);
				
	for(int i = 0; i < timelapseCilia.size(); i++){
		progress.updateBarText("Saving individual cilia averages (" + i + "/" + timelapseCilia.size() + " done)");
		if(timelapseCilia.get(i).excluded){
			continue;
		}
		appendTxt = "	";	
		appendTxt += "	" + dformat0.format(i+1); //"ID";
		
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).xCAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).yCAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).zCAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).voxelsAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).volumeAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).surfaceVoxelsAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).surfaceAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).shapeComplexityAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).sphereRadiusAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).maximumSpanAvg);	// Implemented in V0.2.2
		appendTxt += "	"; if(measureC2local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(timelapseCilia.get(i).colocalizedVolumeC2Avg);}
		appendTxt += "	"; if(measureC2local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(100.0*timelapseCilia.get(i).colocalizedFractionC2Avg);}
		appendTxt += "	"; if(measureC3local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(timelapseCilia.get(i).colocalizedVolumeC3Avg);}
		appendTxt += "	"; if(measureC3local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(100.0*timelapseCilia.get(i).colocalizedFractionC3Avg);}
		appendTxt += "	"; if(measureC2local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(timelapseCilia.get(i).colocalizedCompToBGVolumeC2Avg);}
		appendTxt += "	"; if(measureC2local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(100.0*timelapseCilia.get(i).colocalizedCompToBGFractionC2Avg);}
		appendTxt += "	"; if(measureC3local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(timelapseCilia.get(i).colocalizedCompToBGVolumeC3Avg);}
		appendTxt += "	"; if(measureC3local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(100.0*timelapseCilia.get(i).colocalizedCompToBGFractionC3Avg);}
		
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).minCiliumIntensityAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).maxCiliumIntensityAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).maxTenPercentCiliumIntensityAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).averageCiliumIntensityAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).ciliumAvailable) appendTxt += fmt6(timelapseCilia.get(i).SDCiliumIntensityAvg);
		
		appendTxt += "	"; if(measureC2local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(timelapseCilia.get(i).minC2IntensityAvg);}
		appendTxt += "	"; if(measureC2local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(timelapseCilia.get(i).maxC2IntensityAvg);}
		appendTxt += "	"; if(measureC2local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(timelapseCilia.get(i).maxTenPercentC2IntensityAvg);}
		appendTxt += "	"; if(measureC2local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(timelapseCilia.get(i).averageC2IntensityAvg);}
		appendTxt += "	"; if(measureC2local && timelapseCilia.get(i).ciliumAvailable){appendTxt += fmt6(timelapseCilia.get(i).SDC2IntensityAvg);}
		
		appendTxt += "	"; if(measureC3local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(timelapseCilia.get(i).minC3IntensityAvg);}
		appendTxt += "	"; if(measureC3local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(timelapseCilia.get(i).maxC3IntensityAvg);}
		appendTxt += "	"; if(measureC3local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(timelapseCilia.get(i).maxTenPercentC3IntensityAvg);}
		appendTxt += "	"; if(measureC3local && timelapseCilia.get(i).ciliumAvailable){appendTxt += dformat6.format(timelapseCilia.get(i).averageC3IntensityAvg);}
		appendTxt += "	"; if(measureC3local && timelapseCilia.get(i).ciliumAvailable){appendTxt += fmt6(timelapseCilia.get(i).SDC3IntensityAvg);}
		
		appendTxt += "	"; if(skeletonize) appendTxt += dformat6.format(timelapseCilia.get(i).foundSklAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += "yes";}else if(skeletonize){appendTxt += "no";}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += dformat6.format(timelapseCilia.get(i).branchesAvg);}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += dformat6.format(timelapseCilia.get(i).treeLengthAvg);}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += dformat6.format(timelapseCilia.get(i).largestShortestPathOfLargestAvg);}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += dformat6.format(timelapseCilia.get(i).orientationVectorAvg[0]);}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += dformat6.format(timelapseCilia.get(i).orientationVectorAvg[1]);}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += dformat6.format(timelapseCilia.get(i).orientationVectorAvg[2]);}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += fmt6(timelapseCilia.get(i).bendingIndexAvg);}
		
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(intensityThresholds[channelC2-1]);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(intensityThresholds[channelC3-1]);}
		appendTxt += "	"; if(measureBasalLocal){appendTxt += dformat6.format(intensityThresholds[basalStainC-1]);}

		appendTxt += "	"; if(segmentedBB && timelapseCilia.get(i).bbAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).bbXAvg * calibration);
		appendTxt += "	"; if(segmentedBB && timelapseCilia.get(i).bbAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).bbYAvg * calibration);
		appendTxt += "	"; if(segmentedBB && timelapseCilia.get(i).bbAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).bbZAvg * voxelDepth);
		
		appendTxt += "	"; if(segmentedBB && timelapseCilia.get(i).bbAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).bbCenterIntensityC2Avg);
		appendTxt += "	"; if(segmentedBB && timelapseCilia.get(i).bbAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).bbIntensityRadius1C2Avg);
		appendTxt += "	"; if(segmentedBB && timelapseCilia.get(i).bbAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).bbIntensityRadius2C2Avg);
		appendTxt += "	"; if(segmentedBB && timelapseCilia.get(i).bbAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).bbCenterIntensityC3Avg);
		appendTxt += "	"; if(segmentedBB && timelapseCilia.get(i).bbAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).bbIntensityRadius1C3Avg);
		appendTxt += "	"; if(segmentedBB && timelapseCilia.get(i).bbAvailable) appendTxt += dformat6.format(timelapseCilia.get(i).bbIntensityRadius2C3Avg);
	
					
		tw1.append(""+appendTxt);
		
		appendTxt = name + appendTxt;
		appendTxt += getOneRowFooter(currentDate);
		tw2.append(""+appendTxt);
		
		progress.updateBarText("Saving individual cilium kinetics (" + i + "/" + timelapseCilia.size() + " done)");
		saveIndividualCiliumKinetics(timelapseCilia.get(i), ""+(i+1), imp, 
				measureC2local, measureC3local, measureBasalLocal, 
				nrOfProfileCols, name, dir, currentDate, startDate,
				filePrefix + "_C" + dformat0.format(i+1), subfolderPrefix + "_C" + dformat0.format(i+1), intensityThresholds,
				excludedCilia, totalNrOfDetectedCilia, excludedBBs, totalNrOfBBs, excludedCiliaBB);
	}

	appendNotesLine(tw1, filePrefix);	// New in v0.2.2
	addFooter(tw1, currentDate);				
	if(!tw1.saveAndFinish(filePrefix + ".txt")) {
		progress.notifyMessage("IO ERROR when saving file " + filePrefix + ".txt", ProgressReporter.ERROR);
	}
	
	//save one row results file
	if(!tw2.saveAndFinish(filePrefix + "s.txt")) {
		progress.notifyMessage("IO ERROR when saving file " + (filePrefix + "s.txt"), ProgressReporter.ERROR);
	}
	//Save Textfile--------------------------------------
	
	//save profile text file
	saveTimelapseSkeletonPoints(timelapseCilia, dir, name, filePrefix + "l.txt", currentDate);
	
	//Output images		
	IJ.saveAsTiff(imp, filePrefix + "_RP");
	
	if(saveSingleCiliaTifs){
		ImagePlus impC;
		//save cilium-specific images
		for(int i = 0; i < timelapseCilia.size(); i++){
			progress.updateBarText("Saving cilium-specific images (" + i + "/" + timelapseCilia.size() + " done)");
			if(timelapseCilia.get(i).excluded) continue;
			
			//save channel information
			OutputTextFile tp = new OutputTextFile ("");
			tp.append("Channel information for results images obtained by analysis of:	" + name);
			tp.append("Cilium:	" + dformat0.format(i+1));
			tp.append("Saving date:	" + FullDateFormatter.format(currentDate)
			+ "	Starting date:	" + FullDateFormatter.format(startDate));
			tp.append("");
			
			tp.append("Results image:	" + filePrefix.substring(dir.length()) + "_C" + dformat0.format(i+1) + ".tif");
			tp.append("	channel 1 (cyan):	" + "reconstructed cilium");
			tp.append("	channel 2 (white):	" + "cell skeleton");
			tp.append("	channel 3 (green):	" + "intensity A");
			tp.append("	channel 4 (red):	" + "intensity B");
			
			addFooter(tp, currentDate);			
			if(!tp.saveAndFinish(subfolderPrefix + "_C" + dformat0.format(i+1) + "_img.txt")) {
				progress.notifyMessage("IO ERROR when saving file " + (subfolderPrefix + "_C" + dformat0.format(i+1) + "_img.txt"), ProgressReporter.ERROR);
			}
			
			
			impC = timelapseCilia.get(i).getCiliumImp(intensityThresholds, channelC2, channelC3, progress);
			IJ.saveAsTiff(impC, subfolderPrefix + "_C" + dformat0.format(i+1) + ".tif"); 
			impC.changes = false;
			impC.close();			
		}					
	}
	
	//save 3D images
	{		
		progress.updateBarText("Producing Overview Skeleton Image");
		saveSkeletonOverviewImagesTimelapse(filePrefix + "_SKL", imp, timelapseCilia,
				name, dir, currentDate, startDate);
		if(this.saveOverview3DImages){			
			progress.updateBarText("Producing Overview 3D Image");		
			saveImageAs3D(filePrefix + "_RP", reduceToMaskChannel(imp, channelReconstruction), false, true, false);
		}		
	}
	
	if(saveSingleCilia3DImages){
		for(int i = 0; i < timelapseCilia.size(); i++){
			progress.updateBarText("Producing 3D images of individual cilia (" + i + "/" + timelapseCilia.size() + " done)");
			saveImageAs3D(subfolderPrefix + "_C" + (i+1), timelapseCilia.get(i).getCiliumImp(intensityThresholds, channelC2, channelC3, progress),
					false, true, false);
		}
	}
	if(selectedTaskVariant.equals(taskVariant[1])) {
		imp.changes = false;
		imp.close();
	}
	progress.setBar(0.95);
}

public void saveIndividualCiliumKinetics(TimelapseCilium cilium, String ciliumID, ImagePlus imp,
	boolean measureC2local, boolean measureC3local, boolean measureBasalLocal, 
	int nrOfProfileCols, String name, String dir, Date currentDate, Date startDate, 
	String filePrefix, String subfolderPrefix, 
	double [] intensityThresholds,
	int excludedCilia, int totalCilia, 
	int excludedBBs, int totalBBs, int excludedCiliaBB){
	//Save Textfile--------------------------------------	
	OutputTextFile tw1 = new OutputTextFile ("");
	OutputTextFile tw2 = new OutputTextFile ("");
	
	addSettingsBlockToPanel(tw1, currentDate, startDate, name, imp, 
			measureC2local, measureC3local, measureBasalLocal, intensityThresholds,
			excludedCilia, totalCilia,excludedBBs,totalBBs, excludedCiliaBB);
	
	tw1.append("Results for Cilium " + ciliumID + ":");				
	String appendTxt = "		ID";	
	appendTxt += "	" + "time";
	appendTxt += "	" + "x center ["+calibrationDimension+"]";
	appendTxt += "	" + "y center ["+calibrationDimension+"]";
	appendTxt += "	" + "z center ["+calibrationDimension+"]";
	appendTxt += "	" + "volume [voxel]";
	appendTxt += "	" + "volume ["+calibrationDimension+"^3]";
	appendTxt += "	" + "# surface voxels";
	appendTxt += "	" + "surface ["+calibrationDimension+"^2]";
	appendTxt += "	" + "shape complexity index";
	appendTxt += "	" + "sphere radius ["+calibrationDimension+"]";
	appendTxt += "	" + "Maximum span ["+calibrationDimension+"]";		// Implemented in V0.2.2
	
	appendTxt += "	"; if(measureC2local){appendTxt += "A: Colocalized volume [" + calibrationDimension + "^3] (if channel in input image was background-removed)";}
	appendTxt += "	"; if(measureC2local){appendTxt += "A: Colocalized volume [% total volume] (if channel in input image was background-removed)";}
	appendTxt += "	"; if(measureC3local){appendTxt += "B: Colocalized volume [" + calibrationDimension + "^3] (if channel in input image was background-removed)";}
	appendTxt += "	"; if(measureC3local){appendTxt += "B: Colocalized volume [% total volume] (if channel in input image was background-removed)";}
	appendTxt += "	"; if(measureC2local){appendTxt += "A: Colocalized compared to BG volume [" + calibrationDimension + "^3]";}
	appendTxt += "	"; if(measureC2local){appendTxt += "A: Colocalized compared to BG volume [% total volume]";}
	appendTxt += "	"; if(measureC3local){appendTxt += "B: Colocalized compared to BG volume [" + calibrationDimension + "^3]";}
	appendTxt += "	"; if(measureC3local){appendTxt += "B: Colocalized compared to BG volume [% total volume]";}
	
	appendTxt += "	" + "minimum intensity (in reconstruction channel)";
	appendTxt += "	" + "maximum intensity (in reconstruction channel)";
	appendTxt += "	" + "average intensity of the 10% of voxels with highest intensity (in reconstruction channel)";
	appendTxt += "	" + "average intensity (in reconstruction channel)";
	appendTxt += "	" + "SD of intensity (in reconstruction channel)";
	
	appendTxt += "	"; if(measureC2local){appendTxt += "minimum A intensity";}
	appendTxt += "	"; if(measureC2local){appendTxt += "maximum A intensity";}
	appendTxt += "	"; if(measureC2local){appendTxt += "average A intensity of the 10% of voxels with highest A intensity";}
	appendTxt += "	"; if(measureC2local){appendTxt += "average A intensity";}
	appendTxt += "	"; if(measureC2local){appendTxt += "SD of A intensity";}
	
	appendTxt += "	"; if(measureC3local){appendTxt += "minimum B intensity";}
	appendTxt += "	"; if(measureC3local){appendTxt += "maximum B intensity";}
	appendTxt += "	"; if(measureC3local){appendTxt += "average B intensity of the 10% of voxels with highest B intensity";}
	appendTxt += "	"; if(measureC3local){appendTxt += "average B intensity";}
	appendTxt += "	"; if(measureC3local){appendTxt += "SD of B intensity";}
	
	appendTxt += "	"; if(skeletonize) appendTxt += "# of found skeletons (quality parameter)";
	appendTxt += "	"; if(skeletonize) appendTxt += "Skl available in all frames?";
	appendTxt += "	"; if(skeletonize) appendTxt += "# branches (quality parameter)";
	appendTxt += "	"; if(skeletonize) appendTxt += "tree length [" + calibrationDimension + "] (quality parameter)";
	appendTxt += "	"; if(skeletonize) appendTxt += "cilia length [" + calibrationDimension + "] (largest shortest path of largest skeleton)";
	appendTxt += "	"; if(skeletonize) appendTxt += "orientation vector x [" + calibrationDimension + "] (vector from first to last skeleton point)";
	appendTxt += "	"; if(skeletonize) appendTxt += "orientation vector y [" + calibrationDimension + "] (vector from first to last skeleton point)";
	appendTxt += "	"; if(skeletonize) appendTxt += "orientation vector z [" + calibrationDimension + "] (vector from first to last skeleton point)";
	appendTxt += "	"; if(skeletonize) appendTxt += "cilia bending index (arc length of cilium / eucledian distance of first and last skeleton point)";
	
	appendTxt += "	"; if(measureC2local){appendTxt += "Intensity threshold A";}
	appendTxt += "	"; if(measureC3local){appendTxt += "Intensity threshold B";}
	appendTxt += "	"; if(measureBasalLocal){appendTxt += "Intensity threshold Basal Stain";}
		
	appendTxt += "	"; if(skeletonize && measureC2local) appendTxt += "Integrated A intensity";
	appendTxt += "	"; if(skeletonize && measureC2local) appendTxt += "Average A intensity on center line";
	appendTxt += "	"; if(skeletonize && measureC3local) appendTxt += "Integrated B intensity";
	appendTxt += "	"; if(skeletonize && measureC3local) appendTxt += "Average B intensity on center line";
	
	appendTxt += "	"; if(skeletonize && measureC2local) appendTxt += "A: Colocalized on centerline compared to BG volume [" + calibrationDimension + "]";
	appendTxt += "	"; if(skeletonize && measureC2local) appendTxt += "A: Colocalized on centerline compared to BG volume [% total length]";
	appendTxt += "	"; if(skeletonize && measureC3local) appendTxt += "B: Colocalized on centerline compared to BG volume [" + calibrationDimension + "]";
	appendTxt += "	"; if(skeletonize && measureC3local) appendTxt += "B: Colocalized on centerline compared to BG volume [% total length]";
	
	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody x center ["+calibrationDimension+"]";;
	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody y center ["+calibrationDimension+"]";;
	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody z center ["+calibrationDimension+"]";;

	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody center A intensity";
	appendTxt += "	"; if(segmentedBB) appendTxt += "A intensity within 1 "+calibrationDimension+" to basal body";
	appendTxt += "	"; if(segmentedBB) appendTxt += "A intensity 1-2 "+calibrationDimension+" to basal body";
	appendTxt += "	"; if(segmentedBB) appendTxt += "basalbody center B intensity";
	appendTxt += "	"; if(segmentedBB) appendTxt += "B intensity within 1 "+calibrationDimension+" to basal body";
	appendTxt += "	"; if(segmentedBB) appendTxt += "B intensity 1-2 "+calibrationDimension+" to basal body";
	
	//From v0.2.2 we do no longer print the profiles in the main file, facilitating analysis
	//Profiles can instead be loaded and plotted programmatically from another file
//	if(measureC2local && skeletonize){
//		appendTxt += "	" + "Profile A (arc length step: " + dformat6.format(calibration) + ")";
//		for(int i = 0; i < nrOfProfileCols-1; i++){
//			appendTxt += "	";
//		}
//		appendTxt += "	" + "Profile A (normalized to reconstruction channel) (arc length step: " + dformat6.format(calibration) + ")";
//		for(int i = 0; i < nrOfProfileCols-1; i++){
//			appendTxt += "	";
//		}
//	}
		
//	if(measureC3local && skeletonize){
//		appendTxt += "	" + "Profile B (arc length step: " + dformat6.format(calibration) + ")";
//		for(int i = 0; i < nrOfProfileCols-1; i++){
//			appendTxt += "	";
//		}
//		appendTxt += "	" + "Profile B (normalized to reconstruction channel) (arc length step: " + dformat6.format(calibration) + ")";
//		for(int i = 0; i < nrOfProfileCols-1; i++){
//			appendTxt += "	";
//		}
//	}				
					
	tw1.append(""+appendTxt);
				
	int time = 0;
	double [] iProfileC2, iProfileC3, coloc;
	for(int i = 0; i < cilium.cilia.size(); i++){			
		while(cilium.cilia.get(i).t > time){
			progress.updateBarText("Skip frame for cilium " + ciliumID + ": " + time + " of " + cilium.cilia.size());
			appendTxt = "		" + ciliumID + "	" + dformat0.format(time);
			tw1.append(""+appendTxt);
			
			appendTxt = name + appendTxt;
			appendTxt += getOneRowFooter(currentDate);
			tw2.append(""+appendTxt);
				
			time++;
		}
		if(cilium.cilia.get(i).t < time){		// NEW in V0.2.2: never pad backwards
			progress.notifyMessage("Cilium " + ciliumID + ": frame " + cilium.cilia.get(i).t
					+ " arrived after frame " + (time-1) + " - track not in frame order",
					ProgressReporter.NOTIFICATION);
			time = cilium.cilia.get(i).t;
		}
		time++;
		
		if(cilium.cilia.get(i).excluded){
			progress.updateBarText("Skip frame for cilium " + ciliumID + " because excluded: " + time);
			appendTxt = "		" + ciliumID + "	" + dformat0.format(cilium.cilia.get(i).t);
			tw1.append(""+appendTxt);
			
			appendTxt = name + appendTxt;
			appendTxt += getOneRowFooter(currentDate);
			tw2.append(""+appendTxt);
			continue;
		}
		
		appendTxt = "		" + ciliumID;	
		appendTxt += "	" + dformat0.format(cilium.cilia.get(i).t); //"frame"; v0.2.2: print also in a frame with the basal body only
		
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).xC);
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).yC);
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).zC);
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat0.format(cilium.cilia.get(i).voxels);
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).volume);
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat0.format(cilium.cilia.get(i).surfaceVoxels);
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).surface);
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).shapeComplexity);
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).sphereRadius);
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).maximumSpan);		// Implemented in V0.2.2

		appendTxt += "	"; if(measureC2local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).colocalizedVolumeC2);}
		appendTxt += "	"; if(measureC2local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(100.0*cilium.cilia.get(i).colocalizedFractionC2);}
		appendTxt += "	"; if(measureC3local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).colocalizedVolumeC3);}
		appendTxt += "	"; if(measureC3local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(100.0*cilium.cilia.get(i).colocalizedFractionC3);}
		appendTxt += "	"; if(measureC2local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).colocalizedCompToBGVolumeC2);}
		appendTxt += "	"; if(measureC2local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(100.0*cilium.cilia.get(i).colocalizedCompToBGFractionC2);}
		appendTxt += "	"; if(measureC3local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).colocalizedCompToBGVolumeC3);}
		appendTxt += "	"; if(measureC3local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(100.0*cilium.cilia.get(i).colocalizedCompToBGFractionC3);}
		
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).minCiliumIntensity);
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).maxCiliumIntensity);
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).maxTenPercentCiliumIntensity);
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).averageCiliumIntensity);
		appendTxt += "	"; if(cilium.cilia.get(i).ciliumAvailable) appendTxt += fmt6(cilium.cilia.get(i).SDCiliumIntensity);
		
		appendTxt += "	"; if(measureC2local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).minC2Intensity);}
		appendTxt += "	"; if(measureC2local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).maxC2Intensity);}
		appendTxt += "	"; if(measureC2local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).maxTenPercentC2Intensity);}
		appendTxt += "	"; if(measureC2local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).averageC2Intensity);}
		appendTxt += "	"; if(measureC2local && cilium.cilia.get(i).ciliumAvailable){appendTxt += fmt6(cilium.cilia.get(i).SDC2Intensity);}
		
		appendTxt += "	"; if(measureC3local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).minC3Intensity);}
		appendTxt += "	"; if(measureC3local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).maxC3Intensity);}
		appendTxt += "	"; if(measureC3local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).maxTenPercentC3Intensity);}
		appendTxt += "	"; if(measureC3local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).averageC3Intensity);}
		appendTxt += "	"; if(measureC3local && cilium.cilia.get(i).ciliumAvailable){appendTxt += fmt6(cilium.cilia.get(i).SDC3Intensity);}
		
		appendTxt += "	"; if(skeletonize && cilium.cilia.get(i).ciliumAvailable) appendTxt += dformat0.format(cilium.cilia.get(i).foundSkl);
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += "yes";}else if(skeletonize){appendTxt += "no";}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += dformat0.format(cilium.cilia.get(i).branches);}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).treeLength);}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).largestShortestPathOfLargest);}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).orientationVector[0]);}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).orientationVector[1]);}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).orientationVector[2]);}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += fmt6(cilium.cilia.get(i).bendingIndex);}
		
		appendTxt += "	"; if(measureC2local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(intensityThresholds[channelC2-1]);}
		appendTxt += "	"; if(measureC3local && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(intensityThresholds[channelC3-1]);}
		appendTxt += "	"; if(measureBasalLocal && cilium.cilia.get(i).ciliumAvailable){appendTxt += dformat6.format(intensityThresholds[basalStainC-1]);}
		
		//profiles
		if(skeletonize){
			progress.updateBarText("Determine intensity profiles for cilium " + ciliumID + " - time " + time);
			if(measureC2local){
				iProfileC2 = cilium.cilia.get(i).getIntensityProfile(2, calibration, false);
				//Note: GetIntensityProfile returns NULL when foundSkl == 0 || sklPointList == null, checked for below from V0.2.2 on				
				appendTxt += "	";
				if(iProfileC2 != null && cilium.cilia.get(i).sklAvailable)	appendTxt += fmt6(tools.getSum(iProfileC2));
				appendTxt += "	";
				if(iProfileC2 != null && cilium.cilia.get(i).sklAvailable)	appendTxt += fmt6(tools.getAverage(iProfileC2));
			}else{
				iProfileC2  = null;
				appendTxt += "		";
			}
						
			if(measureC3local){
				iProfileC3 = cilium.cilia.get(i).getIntensityProfile(3, calibration, false);
				//Note: GetIntensityProfile returns NULL when foundSkl == 0 || sklPointList == null, checked for below from V0.2.2 on
				appendTxt += "	"; 
				if(iProfileC3 != null && cilium.cilia.get(i).sklAvailable)	appendTxt += fmt6(tools.getSum(iProfileC3));
				appendTxt += "	";
				if(iProfileC3 != null && cilium.cilia.get(i).sklAvailable)	appendTxt += fmt6(tools.getAverage(iProfileC3));
			}else{
				iProfileC3  = null;
				appendTxt += "		";
			}
			
			if(measureC2local && iProfileC2 != null){
				//Colocalized length
				appendTxt += "	";
				coloc = getColocalizedLengthsOfProfile(iProfileC2, intensityThresholds[channelC2-1], calibration);
				appendTxt += dformat6.format(coloc[0]);
				appendTxt += "	";
				appendTxt += dformat6.format(coloc[1]);					
			}else{
				appendTxt += "		";
			}	
			
			if(measureC3local && iProfileC3 != null){
				//Colocalized length
				appendTxt += "	";
				coloc = getColocalizedLengthsOfProfile(iProfileC3, intensityThresholds[channelC3-1], calibration);
				appendTxt += dformat6.format(coloc[0]);
				appendTxt += "	";
				appendTxt += dformat6.format(coloc[1]);
			}else{
				appendTxt += "		";
			}
		}else{
			appendTxt += "								";	// v0.2.2: the 8 centre-line cells stay empty, so the basal-body cells below keep their columns
		}
		
		// v0.2.2: the basal-body cells also without skeleton results (inside if(skeletonize) they were missing then)
		appendTxt += "	"; if(segmentedBB && cilium.cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).bbX * calibration);
		appendTxt += "	"; if(segmentedBB && cilium.cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).bbY * calibration);
		appendTxt += "	"; if(segmentedBB && cilium.cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).bbZ * voxelDepth);

		appendTxt += "	"; if(segmentedBB && cilium.cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).bbCenterIntensityC2);
		appendTxt += "	"; if(segmentedBB && cilium.cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).bbIntensityRadius1C2);
		appendTxt += "	"; if(segmentedBB && cilium.cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).bbIntensityRadius2C2);
		appendTxt += "	"; if(segmentedBB && cilium.cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).bbCenterIntensityC3);
		appendTxt += "	"; if(segmentedBB && cilium.cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).bbIntensityRadius1C3);
		appendTxt += "	"; if(segmentedBB && cilium.cilia.get(i).bbAvailable) appendTxt += dformat6.format(cilium.cilia.get(i).bbIntensityRadius2C3);
		tw1.append(""+appendTxt);
		
		appendTxt = name + appendTxt;
		appendTxt += getOneRowFooter(currentDate);
		tw2.append(""+appendTxt);			
	}	
	
	progress.updateBarText("Save text file cilium " + ciliumID);	
	
	addFooter(tw1, currentDate);				
	
	if(!tw1.saveAndFinish(subfolderPrefix + ".txt")) {
		progress.notifyMessage("IO ERROR when saving file " + subfolderPrefix + ".txt", ProgressReporter.ERROR);		
	}
	
	//save one row results file
	if(!tw2.saveAndFinish(subfolderPrefix + "s.txt")) {
		progress.notifyMessage("IO ERROR when saving file " + subfolderPrefix + "s.txt", ProgressReporter.ERROR);
	}
}

/**
 * save overview image of all detected skeletons 
 * */
private void saveSkeletonOverviewImagesTimelapse(String savePath, ImagePlus imp, ArrayList<TimelapseCilium> cilia, 
		String name, String dir, Date currentDate, Date startDate){
	ImagePlus impOut = IJ.createHyperStack(imp.getTitle() + " skl", imp.getWidth(), imp.getHeight(), 
			2, imp.getNSlices(), imp.getNFrames(), 8);
	impOut.setOverlay(imp.getOverlay());
	impOut.setCalibration(imp.getCalibration());
	
	
	double points [][];
	int sklBaseX, sklBaseY, sklBaseZ;
	for(int i = 0; i < cilia.size(); i++){
		progress.updateBarText("Producing timelapse skeleton overview image (" + (i+1) + "/" + cilia.size() + ")");
		for(int j = 0; j < cilia.get(i).cilia.size(); j++){
			if(!cilia.get(i).cilia.get(j).sklAvailable) continue;
			points = cilia.get(i).cilia.get(j).getSkeletonPointsForOriginalImage();
			
			/*
			 * Determining skeleton base position
			 */
			sklBaseX = (int)Math.round(points[0][0]/impOut.getCalibration().pixelWidth);
			sklBaseY = (int)Math.round(points[0][1]/impOut.getCalibration().pixelHeight);
			sklBaseZ = impOut.getStackIndex(2, 
					(int)Math.round(points[0][2]/impOut.getCalibration().pixelDepth)+1, 
					cilia.get(i).cilia.get(j).t+1)-1;

			/*
			 * Writing base intoimage
			 */
			impOut.getStack().setVoxel(sklBaseX,sklBaseY,sklBaseZ,255.0);
			if(sklBaseX>0) 						impOut.getStack().setVoxel(sklBaseX-1,sklBaseY,sklBaseZ,255.0);
			if(sklBaseX<impOut.getWidth()-1)	impOut.getStack().setVoxel(sklBaseX+1,sklBaseY,sklBaseZ,255.0);
			if(sklBaseY>0) 						impOut.getStack().setVoxel(sklBaseX,sklBaseY-1,sklBaseZ,255.0);
			if(sklBaseY<impOut.getHeight()-1)	impOut.getStack().setVoxel(sklBaseX,sklBaseY+1,sklBaseZ,255.0);
			if((int)Math.round(points[0][2]/impOut.getCalibration().pixelDepth)>0){
				impOut.getStack().setVoxel(sklBaseX,sklBaseY,
						impOut.getStackIndex(2, 
								(int)Math.round(points[0][2]/impOut.getCalibration().pixelDepth)+1-1, 
								cilia.get(i).cilia.get(j).t+1)-1,
						255.0);
			}
			if((int)Math.round(points[0][2]/impOut.getCalibration().pixelDepth)<impOut.getNSlices()-1){
				impOut.getStack().setVoxel(sklBaseX,sklBaseY,
						impOut.getStackIndex(2, 
						(int)Math.round(points[0][2]/impOut.getCalibration().pixelDepth)+1+1, 
						cilia.get(i).cilia.get(j).t+1)-1,
				255.0);
			}
			
			/*
			 * Write skeleton into image
			 */
			for(int k = 0; k < points.length; k++){
				impOut.getStack().setVoxel((int)Math.round(points[k][0]/impOut.getCalibration().pixelWidth),
											(int)Math.round(points[k][1]/impOut.getCalibration().pixelHeight),
											impOut.getStackIndex(1, 
													(int)Math.round(points[k][2]/impOut.getCalibration().pixelDepth)+1, 
													cilia.get(i).cilia.get(j).t+1)-1,
											255.0);
			}
		}
	}
	
	impOut.setDisplayMode(IJ.COMPOSITE);
	impOut.setC(1);	
	IJ.run(impOut, "Red", "");			
	impOut.setC(2);	
	IJ.run(impOut, "Cyan", "");	
	
	impOut.setOverlay(imp.getOverlay());
	impOut.setCalibration(imp.getCalibration());
	
	IJ.saveAsTiff(impOut, savePath + ".tif"); 
		
	//save channel information
	OutputTextFile tp = new OutputTextFile ("");
	tp.append("Channel information for skeleton results image - analysis of:	" + name);
	tp.append("Saving date:	" + FullDateFormatter.format(currentDate)
	+ "	Starting date:	" + FullDateFormatter.format(startDate));
	tp.append("");
	
	tp.append("Results image:	" + new File(savePath).getName() + ".tif");	// New in v0.2.2: the name it is saved under
	tp.append("	channel 1:	red	" + "ciliary skeleton");
	tp.append("	channel 2:	white	" + "detected base");
	
	addFooter(tp, currentDate);
	if(!tp.saveAndFinish(savePath + "_info.txt")) {
		progress.notifyMessage("IO ERROR when saving file " + savePath + "_info.txt", ProgressReporter.ERROR);
	}
	
	// make 3D visualization
	if(saveOverview3DImages){
		ImagePlus impCal, imp3D;
		Visualizer3D v3D;	
		try {
			progress.updateBarText("Producing 3D TL skeleton overview image...launch 3Dvis");
			v3D = new Visualizer3D(imp, 3.0f);
			v3D.setAngle(10.0f, -10.0f, 0.0f);
			v3D.setImage(getTimePointFor3D(impOut, 1));
			int width = v3D.getWidth();
			int height = v3D.getHeight();
			v3D.setObjectLightValue(1.2f);
			v3D.setLightPosX(-0.25f);
			v3D.setAlphaOffset1(0);
			
			ImageStack stackOut = new ImageStack(10,10);	//Random values for initialization because intialized later	
			TextRoi txtR;
			Color txtc;
			for(int i = 0; i < impOut.getNFrames(); i++){
				progress.updateBarText("Producing timelapse skeleton overview 3D image (" + (i+1) + "/" + impOut.getNFrames() + ")");
				impCal = getTimePointFor3D(impOut, i+1);
				progress.updateBarText("Producing timelapse skeleton overview 3D image (" + (i+1) + "/" + impOut.getNFrames() + ") ... add scale bar");
				double calBarLength = this.addContainedScalerBarAndGetBarValue(impCal);
				progress.updateBarText("Producing timelapse skeleton overview 3D image (" + (i+1) + "/" + impOut.getNFrames() + ") ... convert to RGB");
				convertToRGB(impCal);			
				progress.updateBarText("Producing timelapse skeleton overview 3D image (" + (i+1) + "/" + impOut.getNFrames() + ") ... write IDs");
				if(impOut.getOverlay()!=null){
					impCal.setSlice(1);
					for(int j = 0; j < impOut.getOverlay().size(); j++){
						txtR = (TextRoi) impOut.getOverlay().get(j);
						txtc = txtR.getStrokeColor();
						txtR.setStrokeColor(Color.GREEN);
						if(txtc.equals(Color.YELLOW)){
							txtR.setStrokeColor(Color.WHITE);
						}
						impCal.getProcessor().drawRoi(txtR);
						txtR.setStrokeColor(txtc);											
	//					progress.notifyMessage("Producing 3D NTL skeleton overview image...write ID " 
	//							+ j + "/" + impOut.getOverlay().size() + "", ProgressReporter.LOG);
					}
				}else{
					progress.notifyMessage("no overlay in TL skeleton overview", ProgressReporter.LOG);
				}
				
	//			if(impCal == null) impCal = particleImp.duplicate();
				
				progress.updateBarText("Producing timelapse skeleton overview 3D image (" + (i+1) + "/" + impOut.getNFrames() + ") ... create 3D vis");
				v3D.setImage(impCal);
				imp3D = v3D.get3DVisualization(false);
				
				writeBarText(imp3D, calBarLength, imp.getCalibration().getUnit(), 
						true, ((double)i*imp.getCalibration().frameInterval), imp.getCalibration().getTimeUnit());				
				
				impCal.changes = false;
				impCal.close();
				
				if(i == 0){
					stackOut = new ImageStack(imp3D.getWidth(),imp3D.getHeight());
				}
				stackOut.addSlice(imp3D.getProcessor());
				
				imp3D.changes = false;
				imp3D.close();
			}
			
			progress.updateBarText("Saving timelapse skeleton overview 3D image...");		
			imp3D = IJ.createImage("3D", width, height, impOut.getNFrames(), 24);
			imp3D.setStack(stackOut);
			IJ.saveAsTiff(imp3D, savePath + "_3D.tif"); 
			
			imp3D.changes = false;
			imp3D.close();		
		}catch(Exception e){
			String out = "";
			for(int err = 0; err < e.getStackTrace().length; err++){
				out += " \n " + e.getStackTrace()[err].toString();
			}			
			progress.notifyMessage("Image: " + savePath.substring(savePath.lastIndexOf(System.getProperty("file.separator"))) 
				+ "_3D.tif: Producing timelapse skeleton overview 3D image failed - error message: \n"
				+ "" + out,ProgressReporter.ERROR);
		}
		v3D = null;
		
		impOut.changes = false;
		impOut.close();
	}	
}

/**
 * save overview image of all detected skeletons (non timelapse)
 * */
private void saveSkeletonOverviewImageNonTimeLapse(String savePath, ImagePlus imp, ArrayList<Cilium> cilia, 
		String name, String dir, Date currentDate, Date startDate){
	ImagePlus impOut = IJ.createHyperStack(imp.getTitle() + " skl", imp.getWidth(), imp.getHeight(), 
			2, imp.getNSlices(), imp.getNFrames(), 8);
	impOut.setOverlay(imp.getOverlay());
	impOut.setCalibration(imp.getCalibration());
	
	int points [][];
	int sklBaseX, sklBaseY, sklBaseZ;
	for(int j = 0; j < cilia.size(); j++){
		if(!cilia.get(j).sklAvailable) continue;
		progress.updateBarText("Producing non-timelapse skeleton overview image (" + (j+1) + "/" + cilia.size() + ")");
		points = cilia.get(j).getSkeletonPointsForOriginalImageInPixel();
		
		/*
		 * Calculating ciliary base position
		 */
		//Determining base position
		sklBaseX = points[0][0];
		sklBaseY = points[0][1];
		sklBaseZ = impOut.getStackIndex(2,points[0][2]+1,1)-1;

		/*
		 * Writing ciliary base into image
		 */
		impOut.getStack().setVoxel(sklBaseX,sklBaseY,sklBaseZ,255.0);
		if(sklBaseX>0) 						impOut.getStack().setVoxel(sklBaseX-1,sklBaseY,sklBaseZ,255.0);
		if(sklBaseX<impOut.getWidth()-1)	impOut.getStack().setVoxel(sklBaseX+1,sklBaseY,sklBaseZ,255.0);
		if(sklBaseY>0) 						impOut.getStack().setVoxel(sklBaseX,sklBaseY-1,sklBaseZ,255.0);
		if(sklBaseY<impOut.getHeight()-1)	impOut.getStack().setVoxel(sklBaseX,sklBaseY+1,sklBaseZ,255.0);
		
		if(points[0][2]>0){
			impOut.getStack().setVoxel(sklBaseX,sklBaseY,
					impOut.getStackIndex(2, points[0][2]+1-1,1)-1,
					255.0);
		}
		if(points[0][2]<impOut.getNSlices()-1){
			impOut.getStack().setVoxel(sklBaseX,sklBaseY,
					impOut.getStackIndex(2,points[0][2]+1+1,1)-1,
			255.0);
		}		

		/*
		 * Writing skeleton into image
		 */		
		for(int k = 0; k < points.length; k++){
			impOut.getStack().setVoxel(points[k][0],
										points[k][1],
										impOut.getStackIndex(1,points[k][2]+1,1)-1,
										255.0);
		}
	}
	
	impOut.setDisplayMode(IJ.COMPOSITE);
	impOut.setC(1);	
	IJ.run(impOut, "Red", "");			
	impOut.setC(2);	
	IJ.run(impOut, "Cyan", "");	
	
//	impOut.show();
//	new WaitForUserDialog("impOut").show();
//	impOut.hide();
	
	IJ.saveAsTiff(impOut, savePath + ".tif");
			
	/*
	 * save channel information in a text file
	 */
	progress.updateBarText("Writing metadata text file for non-timelapse skeleton overview image...");
	OutputTextFile tp =new OutputTextFile ("");
	tp.append("Channel information for skeleton results image - analysis of:	" + name);
	tp.append("Saving date:	" + FullDateFormatter.format(currentDate)
	+ "	Starting date:	" + FullDateFormatter.format(startDate));
	tp.append("");
	
	tp.append("Results image:	" + new File(savePath).getName() + ".tif");	// New in v0.2.2: the name it is saved under
	tp.append("	channel 1:	red	" + "ciliary skeleton");
	tp.append("	channel 2:	white	" + "detected base");
	
	addFooter(tp, currentDate);
	if(!tp.saveAndFinish(savePath + "_info.txt")) {
		progress.notifyMessage("IO ERROR when saving file " + savePath + "_info.txt", ProgressReporter.ERROR);
	}
	
	// make 3D visualization
	if(saveOverview3DImages){
		progress.updateBarText("Producing 3D NTL skeleton overview image...make scale bar");
		double calBarLength = this.addContainedScalerBarAndGetBarValue(impOut);
		progress.updateBarText("Producing 3D NTL skeleton overview image...covert to RGB");
		convertToRGB(impOut);
		progress.updateBarText("Producing 3D NTL skeleton overview image...write IDs");	
		
		if(impOut.getOverlay()!=null){
			TextRoi txtR;
			Color txtc;
			impOut.setSlice(1);
			for(int j = 0; j < impOut.getOverlay().size(); j++){
				txtR = (TextRoi) impOut.getOverlay().get(j);
				txtc = txtR.getStrokeColor();
				txtR.setStrokeColor(Color.GREEN);
				if(txtc.equals(Color.YELLOW)){
					txtR.setStrokeColor(Color.WHITE);
				}
				impOut.getProcessor().drawRoi(txtR);
				txtR.setStrokeColor(txtc);				
				
//				progress.notifyMessage("Producing 3D NTL skeleton overview image...write ID " 
//						+ j + "/" + impOut.getOverlay().size() + "", ProgressReporter.LOG);
			}
		}else{
			progress.notifyMessage("no overlay in NTL skeleton overview", ProgressReporter.LOG);
		}
//		
//		impOut.show();
//		new WaitForUserDialog("imp out").show();
//		impOut.hide();
		
		Visualizer3D v3D;
		try {
			ImagePlus imp3D;	
			progress.updateBarText("Producing 3D NTL skeleton overview image...launch 3Dvis");
			v3D = new Visualizer3D(impOut, 3.0f);
			v3D.setImage(impOut);
			v3D.setAngle(10.0f, -10.0f, 0.0f);
			v3D.setObjectLightValue(1.2f);
			v3D.setLightPosX(-0.25f);
			v3D.setAlphaOffset1(0);
			progress.updateBarText("Producing 3D NTL skeleton overview image...get 3Dvis");
			imp3D = v3D.get3DVisualization(false);
			
			writeBarText(imp3D, calBarLength, imp.getCalibration().getUnit(), false, 0.0, "");	
	
//			imp3D.show();
//			new WaitForUserDialog("imp3D").show();
//			imp3D.hide();
			
			progress.updateBarText("Producing 3D NTL skeleton overview image...save");								
			IJ.saveAs(imp3D, "PNG", savePath + "_3D.png"); 
	
			imp3D.changes = false;
			imp3D.close();			
			
		}catch(Exception e){
			String out = "";
			for(int err = 0; err < e.getStackTrace().length; err++){
				out += " \n " + e.getStackTrace()[err].toString();
			}			
			progress.notifyMessage("Image: " + savePath.substring(savePath.lastIndexOf(System.getProperty("file.separator")))
				+ ": Producing 3D NTL skeleton overview image failed - error message: \n"
				+ "" + out,ProgressReporter.ERROR);		
		}		
		v3D = null;
		impOut.changes = false;
		impOut.close();
		

	}	
}

private void writeBarText(ImagePlus imp, double calBarLength, String unit, boolean writeTime, double time, String timeUnit){
	//write bar text:
		progress.updateBarText("Make cal bar text to image");
		TextRoi txtROI = new TextRoi(5,5,
				"scale bars: " + dformat3.format(calBarLength) + " " + unit, RoiFont);
		progress.updateBarText("Writing cal bar text to image");
		for(int c = 0; c < imp.getNChannels(); c++){
			txtROI.drawPixels(imp.getStack().getProcessor(imp.getStackIndex(c+1, 1, 1)));		
		}
		
		if(writeTime){
			progress.updateBarText("Make time bar text to image");
			txtROI = new TextRoi(5,20,
					"time: " + dformat3.format(time) + " " + timeUnit, RoiFont);
			progress.updateBarText("Writing time bar text to image");
			for(int c = 0; c < imp.getNChannels(); c++){
				txtROI.drawPixels(imp.getStack().getProcessor(imp.getStackIndex(c+1, 1, 1)));		
			}
		}
		
//		imp.show();
//		new WaitForUserDialog("imp bar").show();
//		imp.hide();
		progress.updateBarText("Cal bar done");
}

/**
 * save image as 3D
 * */
private void saveImageAs3D(String savePath, ImagePlus imp, boolean transparent, boolean useLight, boolean autoAlpha){
	ImagePlus impOut = imp.duplicate();
	
	impOut.setDisplayMode(IJ.COMPOSITE);
	for(int c = 0; c < impOut.getNChannels(); c++){
		impOut.setC(c+1);	
		//To save time we just adjust the image to min max to ensure optimal display from Version v0.2.0 on
//		if(impOut.getBitDepth()==8){
//			impOut.setDisplayRange(0, 255);
//		}else if(impOut.getBitDepth()==16){
//			if(is12Bit(impOut)) {
//				impOut.setDisplayRange(0, 4096);
//			}
//			impOut.setDisplayRange(0, 65535);
//		}
		impOut.resetDisplayRange();
	}	
	impOut.setOverlay(imp.getOverlay());
	impOut.setCalibration(imp.getCalibration());
	
	// make 3D visualization
	{
		ImagePlus impCal, imp3D;	
		int width = 10;
		int height = 10;
		Visualizer3D v3D;
		double calBarLength;
		try {
			v3D = new Visualizer3D(imp, 3.0f);
			v3D.setAngle(10.0f, -10.0f, 0.0f);
			
			if(useLight){
				v3D.useLight(true);
				if(transparent){
					v3D.setObjectLightValue(1.8f);
					v3D.setLightPosX(0.0f);
				}else{
					v3D.setObjectLightValue(1.8f);
					v3D.setLightPosX(-0.25f);		
				}			
			}else{
				v3D.useLight(false);
			}
			
			if(transparent){
				v3D.setAlphaOffset1(-45);
			}else{
				v3D.setAlphaOffset1(0);			
			}
			
			ImageStack stackOut = new ImageStack(10,10);	//Random values for initialization because initialized later	
			
			TextRoi txtR;
			Color txtc;
			for(int i = 0; i < impOut.getNFrames(); i++){
				impCal = getTimePointFor3D(impOut, i+1);
				calBarLength = addContainedScalerBarAndGetBarValue(impCal);
				convertToRGB(impCal);
				if(impOut.getOverlay()!=null){
	//				impCal.setSlice(impCal.getNSlices());
					impCal.setSlice(1);
					for(int j = 0; j < impOut.getOverlay().size(); j++){
						txtR = (TextRoi) impOut.getOverlay().get(j);
						txtc = txtR.getStrokeColor();
						txtR.setStrokeColor(Color.GREEN);
						if(txtc.equals(Color.YELLOW)){
							txtR.setStrokeColor(Color.WHITE);
						}
						impCal.getProcessor().drawRoi(txtR);
						txtR.setStrokeColor(txtc);
					}	
				}		
				
	//			if(impCal == null) impCal = particleImp.duplicate();
				
				v3D.setImage(impCal);
				imp3D = v3D.get3DVisualization(autoAlpha);
				
				writeBarText(imp3D, calBarLength, imp.getCalibration().getUnit(), 
						impOut.getNFrames()!=1, ((double)i*imp.getCalibration().frameInterval), imp.getCalibration().getTimeUnit());
							
				impCal.changes = false;
				impCal.close();
				
				if(i == 0){
					width = imp3D.getWidth();
					height = imp3D.getHeight();
					stackOut = new ImageStack(imp3D.getWidth(),imp3D.getHeight());
				}
				stackOut.addSlice(imp3D.getProcessor());
				
				imp3D.changes = false;
				imp3D.close();
			}
						
			imp3D = IJ.createImage("3D", width, height, impOut.getNFrames(), 24);
			imp3D.setStack(stackOut); 
			
			if(impOut.getNFrames() == 1) {
				IJ.saveAs(imp3D, "PNG", savePath + "_3D.png");
			}else {
				IJ.saveAsTiff(imp3D, savePath + "_3D.tif");				
			}
			
			imp3D.changes = false;
			imp3D.close();
		
		}catch(Exception e){
			String out = "";
			for(int err = 0; err < e.getStackTrace().length; err++){
				out += " \n " + e.getStackTrace()[err].toString();
			}
			
			progress.notifyMessage("Image: " + savePath.substring(savePath.lastIndexOf(System.getProperty("file.separator"))) 
				+ "_3D.tif: Producing 3D image image failed - error message: \n"
				+ "" + out,ProgressReporter.ERROR);
		}
		v3D = null;		
		impOut.changes = false;
		impOut.close();
	}	
}

/**
 * This method is not applicable to ImagePlus objects with number of frames > 1!
 * 
 * This methods contains parts of the code published in "MotiQ_3D", plugin for ImageJ, Version v0.1.5,
 * 	Downloaded from https://github.com/hansenjn/MotiQ/releases/tag/v0.1.5 
 * 	on 23rd of April 2019.
 * */
private double addContainedScalerBarAndGetBarValue(ImagePlus imp){
	int pxNrX, pxNrY, pxNrZ;
	double calBarLength = 100000.0;
	progress.updateBarText("Finding perfect scale bar...");
	boolean two = false;
	selecting: while(true){
		pxNrX = (int)Math.round(calBarLength / imp.getCalibration().pixelHeight);
		pxNrY = (int)Math.round(calBarLength / imp.getCalibration().pixelWidth);
		pxNrZ = (int)Math.round(calBarLength / imp.getCalibration().pixelDepth);
		if(pxNrX > imp.getWidth()
				|| pxNrY > imp.getHeight()
				|| pxNrZ > imp.getNSlices()){			
			if(two){
				calBarLength /= 2.0;
				two = false;
			}else{
				calBarLength /= 5.0;
				two = true;
			}		
		}else{
			break selecting;
		}
	}
	progress.updateBarText("Perfect scale bar found");
	
	progress.updateBarText("Calculating bar thickness");
	int orX = 0, orY = imp.getHeight()-1, orZ = imp.getNSlices()-1;
	double maxValue = Math.pow(2.0, imp.getBitDepth())-1.0;
	
	double thicknessCal = imp.getCalibration().pixelHeight;
	if(thicknessCal < imp.getCalibration().pixelWidth) thicknessCal = imp.getCalibration().pixelWidth;
	if(thicknessCal < imp.getCalibration().pixelDepth) thicknessCal = imp.getCalibration().pixelDepth;
	
	int thicknessX = (int)Math.round(thicknessCal / imp.getCalibration().pixelHeight), 
		thicknessY = (int)Math.round(thicknessCal / imp.getCalibration().pixelWidth), 
		thicknessZ = (int)Math.round(thicknessCal / imp.getCalibration().pixelDepth);
	if(thicknessX < 1)	thicknessX = 1;
	if(thicknessY < 1)	thicknessY = 1;
	if(thicknessZ < 1)	thicknessZ = 1;
	
	progress.updateBarText("Writing bar");
	try{
		for(int i = 0; i < pxNrX; i++){
			for(int j = 0; j < thicknessY; j++){
				for(int k = 0; k < thicknessZ; k++){
					for(int c = 0; c < imp.getNChannels(); c++){
						imp.getStack().setVoxel(orX + i, orY - j,imp.getStackIndex(c+1, orZ-k+1, 1)-1, maxValue);
					}						
				}
			}		
		}
		for(int i = 0; i < pxNrY; i++){
			for(int j = 0; j < thicknessX; j++){
				for(int k = 0; k < thicknessZ; k++){
					for(int c = 0; c < imp.getNChannels(); c++){
						imp.getStack().setVoxel(orX + j, orY - i, imp.getStackIndex(c+1, orZ-k+1, 1)-1, maxValue);
					}						
				}
			}
		}
		for(int i = 0; i < pxNrZ; i++){
			for(int j = 0; j < thicknessX; j++){
				for(int k = 0; k < thicknessY; k++){
					for(int c = 0; c < imp.getNChannels(); c++){
						imp.getStack().setVoxel(orX + j, orY - k, imp.getStackIndex(c+1, orZ-i+1, 1)-1, maxValue);
					}						
				}
			}
		}
	}catch(Exception e){
		progress.notifyMessage("Failed to produce scale bar in an output image", ProgressReporter.ERROR);
		return Double.NaN;
	}	
	return calBarLength;	
}

/**
 * @param imp = ImagePlus where to derive time-point from
 * @param t = frame index, one-based (1 <= t <= imp.getNFrames())
 * Method adapted from the plugin "MotiQ_3D", plugin for ImageJ, Version v0.1.5,
 * 	Downloaded from https://github.com/hansenjn/MotiQ/releases/tag/v0.1.5 
 * 	on 23rd of April 2019.
 * */
private static ImagePlus getTimePointFor3D(ImagePlus imp, int t){
	ImagePlus impOut = IJ.createHyperStack(imp.getTitle() + " t" + t, 
//			imp.getWidth()+10, imp.getHeight()+10, imp.getNChannels(), imp.getNSlices()+2, 1, 8);
			imp.getWidth(), imp.getHeight(), imp.getNChannels(), imp.getNSlices()+2, 1, imp.getBitDepth());
	
	/*
	 *	Changed from int to double in V0.2.2.
	 *	32-bit ImageJ images are FLOAT. Math.pow(2,32)-1 = 4294967295 does not fit in an int, so
	 *	the cast overflowed to -1 and the scale bar below was painted at the image MINIMUM instead
	 *	of its maximum for 32-bit images. Keep the value a double. For float images use the maximum actually present.
	 *	Matters since Cilia Preparator V0.2.1, where label images may be created in 32-bit format.
	 */
	double iMax = impOut.getBitDepth() == 32
			? maxOfFrame(imp, t)	// of the input: impOut is still empty here (its maximum, 0, painted every object 0)
			: Math.pow(2.0, impOut.getBitDepth()) - 1.0;
	
	impOut.setCalibration(imp.getCalibration());
	impOut.setOverlay(imp.getOverlay());
	for(int c = 0; c < imp.getNChannels(); c++){
		for(int s = 0; s < imp.getNSlices(); s++){
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){
					if(imp.getStack().getVoxel(x, y, imp.getStackIndex(c+1, s+1, t)-1)>0.0){
//						impOut.getStack().setVoxel(x+5, y+5, impOut.getStackIndex(c+1,s+2,1)-1, iMax);
						impOut.getStack().setVoxel(x, y, impOut.getStackIndex(c+1,s+2,1)-1, iMax);
					}						
				}
			}
		}
	}
	
	//copy colors
	if (imp.isComposite()) {
		int mode = ((CompositeImage) imp).getMode();
		if (imp.getNChannels() != 1) {
			impOut = new CompositeImage(impOut, mode);
			for (int c = 1; c <= imp.getNChannels(); c++) {
				LUT lut = ((CompositeImage) imp).getChannelLut(c);
				((CompositeImage) impOut).setChannelLut(lut, c);
			}
		} else {
			LUT lut = ((CompositeImage) imp).getChannelLut(1);
			impOut.getProcessor().setColorModel(lut);
			impOut.setDisplayRange(lut.min, lut.max);
		}
	}
	impOut.setOpenAsHyperStack(true);

//	imp.show();
//	impOut.show();
//	new WaitForUserDialog("Test").show();
//	imp.hide();
//	impOut.hide();
	
	return impOut;
}

/**
 * Is every voxel of channel c (one-based) above 0, in every slice and frame? Then the channel is not segmented (e.g.
 * an intensity channel set as the segmented basal-body channel).
 */
private static boolean allForeground(ImagePlus imp, int c){
	for(int t = 1; t <= imp.getNFrames(); t++){
		for(int s = 1; s <= imp.getNSlices(); s++){
			ImageProcessor ip = imp.getStack().getProcessor(imp.getStackIndex(c, s, t));
			for(int i = 0; i < ip.getPixelCount(); i++){
				if(ip.getf(i) <= 0.0f) {
					return false;	// background found: stops at the first, so a segmented channel costs little
				}
			}
		}
	}
	return true;
}

/**
 * The largest value in frame t (one-based) of imp, over every channel and slice; 1 if there is none above 0. The
 * value getTimePointFor3D paints the objects of a 32-bit image with, so they show at the top of the display range.
 */
private static double maxOfFrame(ImagePlus imp, int t){
	double max = 0.0;
	for(int c = 1; c <= imp.getNChannels(); c++){
		for(int s = 1; s <= imp.getNSlices(); s++){
			ImageProcessor ip = imp.getStack().getProcessor(imp.getStackIndex(c, s, t));
			for(int i = 0; i < ip.getPixelCount(); i++){
				if(ip.getf(i) > max) {
					max = ip.getf(i);
				}
			}
		}
	}
	return max > 0.0 ? max : 1.0;
}

/**
 * automatic conversion to RGB
 *
 * Method copied from the plugin "MotiQ_3D", plugin for ImageJ, Version v0.1.5,
 * 	Downloaded from https://github.com/hansenjn/MotiQ/releases/tag/v0.1.5 
 * 	on 23rd of April 2019.
 * */
private void convertToRGB(ImagePlus imp){
	if(imp.isComposite()){
		Calibration cal = imp.getCalibration();
		RGBStackConverter.convertToRGB(imp);
		imp.setCalibration(cal);
	}else{
		ImageConverter iCv = new ImageConverter(imp);
		iCv.convertToRGB();
	}
}

/**
 * @param maskChannel: 0 < maskChannel <= nChannels
 * */
public ImagePlus reduceToMaskChannel(ImagePlus impIn, int maskChannel){
	ImagePlus imp = impIn.duplicate();	
	imp.setDisplayMode(IJ.COMPOSITE);
	imp.setCalibration(impIn.getCalibration());
	imp.setOverlay(impIn.getOverlay());
	for(int t = 0; t < imp.getNFrames(); t++){
		for(int s = 0; s < imp.getNSlices(); s++){
			for(int x = 0; x < imp.getWidth(); x++){
				for(int y = 0; y < imp.getHeight(); y++){
					if(imp.getStack().getVoxel(x, y, imp.getStackIndex(maskChannel, s+1, t+1)-1)==0.0){
						for(int c = 0; c < imp.getNChannels(); c++){
							if((c+1) == maskChannel) continue;
							imp.getStack().setVoxel(x, y, imp.getStackIndex(c+1, s+1, t+1)-1, 0.0);
						}
					}
				}
			}
		}
	}
	return imp;
}

/**
 * @param intensityProfile
 * @param threshold
 * @param stepSize: length in µm corresponding to one step in the profile
 * @return array with first entry containing the absolute colocalized length and second entry containing the colocalized length as percentage of total length
 * */
private static double [] getColocalizedLengthsOfProfile(double [] intensityProfile, double threshold, double stepSize){
	double coloc [] = new double [] {0.0,0.0};
	for(int i = 0; i < intensityProfile.length; i++){
		if(intensityProfile [i] > threshold){
			coloc [0] ++;
		}		
	}
	coloc [1] = coloc [0] / (double) intensityProfile.length * 100;
	coloc [0] *= stepSize;
	return coloc;
}


	/**
	 * Shows a Generic Dialog allowing to enter the detection settings.
	 * 
	 * Modified in v0.2.0 to provide also a dialog for detecting and assigning basal bodies.
	 * Restructured in v0.2.2 to simplify (grouped dialog into sections).
	 * 
	 * @returns true if settings were successfully entered and false if the user has canceled the dialog.
	 * */
	private boolean enterSettings() {
		//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		//-------------------------GenericDialog--------------------------------------
		//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		{			
			GenericDialog gd = new GenericDialog(PLUGINNAME + " on " + System.getProperty("os.name") + " - detection preferences");
			gd.addHelp("https://github.com/hansenjn/CiliaQ#user-guide--manual");
			
			//show Dialog-----------------------------------------------------------------
			//.setInsets(top, left, bottom)
			gd.setInsets(0,0,0);	gd.addMessage("Cilia (mask, reconstruction channel)", SubHeadingFont);
			gd.setInsets(0,0,0);	gd.addNumericField("Channel: ", channelReconstruction, 0);
			gd.setInsets(0,0,0);	gd.addCheckbox("Contains labels (intensity value = cilium ID)", labelModeCilia);
			gd.setInsets(0,0,0);	gd.addNumericField("Minimum cilium size [voxel]: ", minSize, 0);
			gd.setInsets(0,0,0);	gd.addCheckbox("Increase range for connecting", increaseRangeCilia);
			gd.setInsets(0,0,0);	gd.addNumericField("Join pieces closer than [calibrated unit] (0 = off): ", joinGap, 2);
			gd.setInsets(0,0,0);	gd.addMessage("(pieces below the minimum size join only pieces of at least that size)"
					+ "\n(with labels: only pieces of the same label)", InstructionsFont);
			gd.setInsets(0,0,0);	gd.addChoice("Additionally exclude...", excludeOptions, excludeSelection);
			
			gd.setInsets(10,0,0);	gd.addMessage("Intensity measurements", SubHeadingFont);
			gd.setInsets(0,0,0);	gd.addCheckboxGroup(1, 2,
					new String[]{"Measure A", "Measure B"},
					new boolean[]{measureC2, measureC3});
			gd.setInsets(0,0,0);	gd.addNumericField("Channels A | B: ", channelC2, 0);
			gd.setInsets(-23,55,0);	gd.addNumericField("", channelC3, 0);
			gd.setInsets(0,0,0);	gd.addNumericField("Minimum size [voxel] (relevant if segmented, 1 = off): ", minRestSize, 0);
			gd.setInsets(0, 0, 0); gd.addCheckbox("Increase range for connecting (relevant if segmented)", increaseRangeRegions);
			
			gd.setInsets(10,0,0);	gd.addMessage("Basal bodies (BBs)", SubHeadingFont);
			gd.setInsets(0,0,0);	gd.addChoice("BB stain", bbCOptions, bbCSelection);
			gd.setInsets(0,0,0);	gd.addNumericField("Channel: ", basalStainC, 0);
			
			gd.setInsets(10,0,0);	gd.addMessage("Skeleton-based results (e.g. length, intensity profiles)", SubHeadingFont);
			gd.setInsets(0,0,0);	gd.addCheckbox("Determine skeleton-based results", skeletonize);
			gd.setInsets(0,0,0);	gd.addNumericField("Gauss sigma before skeletonization XY | Z (0 = off): ", gXY, 2);
			gd.setInsets(-23,55,0);	gd.addNumericField("", gZ, 2);
			gd.setInsets(0,0,0);	gd.addNumericField("Reference distance (for curvature) [calibrated unit]: ", refDistance, 2);
			
			gd.setInsets(10,0,0);	gd.addMessage("Time-lapse (leave at default for non-timelapse images)", SubHeadingFont);
			gd.setInsets(0,0,0);	gd.addChoice("Link cilia across time by", timelapseTrackingOptions, timelapseTrackingSelection);
			gd.setInsets(0,0,0);	gd.addMessage("Classic: set max frame gap; "
					+ "By linear assignment: set max tracking distance + max frame gap; "
					+ "By label ID: max frame gap for BBs only.", InstructionsFont);
			gd.setInsets(0,0,0);	gd.addNumericField("-> Max tracking distance [calibrated unit]: ", maxTrackingDistance, 2);
			gd.setInsets(0,0,0);	gd.addNumericField("-> Max frame gap [frames]: ", maxFrameGap, 0);
			
			gd.showDialog();
			//show Dialog-----------------------------------------------------------------

			if (gd.wasCanceled()) return false;
			
			//read and process variables--------------------------------------------------
			// Cilia
			channelReconstruction = (int) gd.getNextNumber();
			labelModeCilia = gd.getNextBoolean();
			minSize = (int) gd.getNextNumber();
			increaseRangeCilia = gd.getNextBoolean();
			joinGap = checkedJoinGap(gd.getNextNumber(), "dialog");
			excludeSelection = gd.getNextChoice();
			
			// Intensity measurements
			measureC2 = gd.getNextBoolean();
			measureC3 = gd.getNextBoolean();
			channelC2 = (int) gd.getNextNumber();
			channelC3 = (int) gd.getNextNumber();
			minRestSize = (int) gd.getNextNumber();
			increaseRangeRegions = gd.getNextBoolean();
			
			// Basal bodies
			bbCSelection = gd.getNextChoice();
			if(bbCSelection.equals(bbCOptions[0])) {
				measureBasal = false;
				segmentedBB = false;
			}else if(bbCSelection.equals(bbCOptions[1])) {
				measureBasal = true;
				segmentedBB = false;
			}else if(bbCSelection.equals(bbCOptions[2])) {
				measureBasal = true;
				segmentedBB = true;
			}
			basalStainC = (int) gd.getNextNumber();
			
			// Skeleton
			skeletonize = gd.getNextBoolean();
			gXY = (double) gd.getNextNumber();
			gZ = (double) gd.getNextNumber();
			refDistance = (double) gd.getNextNumber();
			
			// Time-lapse
			timelapseTrackingSelection = gd.getNextChoice();
			maxTrackingDistance = (double) gd.getNextNumber();
			maxFrameGap = (int) gd.getNextNumber();
			
			// Label ID tracking without label mode would treat the binary mask value as one single ID,
			// so all cilia of a frame would be linked to each other
			if(timelapseTrackingSelection.equals(timelapseTrackingOptions[2]) && !labelModeCilia) {
				IJ.error(PLUGINNAME, "'By label ID' requires 'Contains labels' to be checked.");
				return false;
			}
			
			//read and process variables--------------------------------------------------
			
		}
		if(bbCSelection.equals(bbCOptions[2])) {
			// READ what had been applied last time CiliaQ was used. TODO Need to rethink whether this would be useful...
//			minBBSize = Prefs.getInt("ciliaQ.minBBSize", minBBSize);
//			increaseRangeBB = Prefs.getBoolean("ciliaQ.increaseRangeBB", increaseRangeBB);
//			bbAssignmentSelection = Prefs.getString("ciliaQ.bbAssignmentSelection", bbAssignmentSelection);
//			maxDistanceBBCiliumEnd = Prefs.getDouble("ciliaQ.maxDistanceBBCiliumEnd", maxDistanceBBCiliumEnd);
//			bbCiliaFilterSelection = Prefs.getString("ciliaQ.bbCiliaFilterSelection", bbCiliaFilterSelection);
			
			GenericDialog gdB = new GenericDialog(PLUGINNAME + " on " + System.getProperty("os.name") + " - BB detection");
	
			gdB.addHelp(BB_HELP_URL);
			
			gdB.addMessage("Basal Body (BB) detection preferences",HeadingFont);
			
			gdB.setInsets(0,0,0);	gdB.addMessage("You selected to detect basal bodies from channel " + basalStainC +"."
					+ "\nThis requires that this channel is segmented and labels basal bodies."
					+ "\nPlease determine how basal bodies shall be detected from the segmented channel,"
					+ "\nhow they shall be assigned to cilia, and whether BBs and cilia matching shall be"
					+ "\nused to remove cilia.", InstructionsFont);
				
			gdB.setInsets(5,0,0);	gdB.addNumericField("minimum BB object size [voxel]: ", minBBSize, 0);
			gdB.setInsets(0,0,0);	gdB.addCheckbox("Increase range for connecting pixels to form a BB object", increaseRangeBB);
			gdB.setInsets(0,0,0);	gdB.addCheckbox("BB channel contains labels (one value per object)", labelModeBB);
	
			gdB.setInsets(5,0,0);	gdB.addChoice("Assign", bbAssignmentOptions, bbAssignmentSelection);
			gdB.setInsets(5,0,0);	gdB.addNumericField("Max allowed BB-center-to-cilium-skeleton-end distance (calibrated units): ", maxDistanceBBCiliumEnd, 2);
			gdB.setInsets(5,0,0);	gdB.addChoice("Remove", bbCiliaFilterOptions, bbCiliaFilterSelection);
			
			gdB.showDialog();
			
			// Read variables
			
			minBBSize = (int) gdB.getNextNumber();
			increaseRangeBB = gdB.getNextBoolean();
			labelModeBB = gdB.getNextBoolean();	// New in v0.2.2
			bbAssignmentSelection = gdB.getNextChoice();
			maxDistanceBBCiliumEnd = gdB.getNextNumber();
			bbCiliaFilterSelection = gdB.getNextChoice();
			
			if (gdB.wasCanceled()) return false;
			
			// SAVE what had been applied this time in CiliaQ. TODO Need to rethink whether this would be useful...
//			Prefs.set("ciliaQ.minBBSize", minBBSize);
//			Prefs.set("ciliaQ.increaseRangeBB", increaseRangeBB);
//			Prefs.set("ciliaQ.bbAssignmentSelection", bbAssignmentSelection);
//			Prefs.set("ciliaQ.maxDistanceBBCiliumEnd", maxDistanceBBCiliumEnd);
//			Prefs.set("ciliaQ.bbCiliaFilterSelection", bbCiliaFilterSelection);
//			Prefs.savePreferences();
		}
		
		return true;
	}

	/**
	 * Checking for plausible input: is this plausibly a segmented channel?
	 *
	 * Runs for EVERY image, including ones segmented outside the Preparator, and never errors,
	 * but only notifies.
	 *
	 * New in v0.2.2
	 */
	void runStartupChecks(ImagePlus imp, int cReconstruction, int cBasalStain, String fileName,
			String fileDir, ProgressReporter progress){
		String where = " (File " + fileName + ", Dir: " + fileDir + ")";

		/*
		 *	Check 1: TIFF slice label.
		 *	The Preparator marks segmented channels in the TIFF slice label, and that survives the
		 *	round-trip, e.g.:
		 *		ch3 -> "segm Channel 3 S1/6 T1/1"	(segmented)
		 *		ch4 -> "Channel 3 S1/6 T1/1"		(the unsegmented duplicate)
		 *
		 *	Three cases, and only the middle one warns:
		 *		label starts with "segm "
		 *			-> correct channel, silent
		 *		no "segm " here BUT another channel has it
		 *			-> Preparator output, wrong channel picked: warn and name the likely one
		 *		no Preparator-style label anywhere
		 *			-> segmented elsewhere: SAY NOTHING
		 */
		int segmChannel = -1;
		boolean chosenIsSegm = false;
		for(int c = 1; c <= imp.getNChannels(); c++){
			String label = imp.getStack().getSliceLabel(imp.getStackIndex(c, 1, 1));
			if(label == null || !label.startsWith("segm ")){
				continue;
			}
			if(c == cReconstruction){
				chosenIsSegm = true;
			}else if(segmChannel == -1){
				segmChannel = c;
			}
		}
		
		/*
		 *	A channel CiliaQ Preparator marked as LABELS (ridge detection, expansion from seeds)
		 *	analysed without label mode (Only a warning, since images from other tools than
		 *	CiliaQ Preparator carry no mark)
		 *
		 *	New in v0.2.2:
		 */
		if(!labelModeCilia && isMarkedLabelChannel(imp, cReconstruction)){
			progress.notifyMessage("Channel " + cReconstruction + " is marked as a label image by CiliaQ Preparator (one value per"
					+ " object), but label mode is off for the cilia: touching cilia merge into one object and the label values"
					+ " are read as intensities. If that is not intended, turn on 'Reconstruction channel contains labels'"
					+ " (macro: label-mode-cilia)." + where, ProgressReporter.NOTIFICATION);
		}
		if(!labelModeBB && cBasalStain > 0 && isMarkedLabelChannel(imp, cBasalStain)){
			progress.notifyMessage("Channel " + cBasalStain + " is marked as a label image by CiliaQ Preparator (one value per"
					+ " object), but label mode is off for the basal bodies: touching basal bodies merge into one. If that is"
					+ " not intended, turn on 'BB channel contains labels' (macro: label-mode-basal-body)." + where,
					ProgressReporter.NOTIFICATION);
		}
		if(labelModeCilia && isMarkedSegmentedChannel(imp, cReconstruction) && !isMarkedLabelChannel(imp, cReconstruction)){	// New in v0.2.2
			progress.notifyMessage("Label mode is on for the cilia, but CiliaQ Preparator marked channel " + cReconstruction
					+ " as a segmented intensity channel, not as labels: label mode joins only voxels of the same value, so it"
					+ " splits the cilia. Turn label mode off unless the channel holds labels." + where, ProgressReporter.NOTIFICATION);
		}
		if(labelModeBB && cBasalStain > 0 && isMarkedSegmentedChannel(imp, cBasalStain) && !isMarkedLabelChannel(imp, cBasalStain)){	// New in v0.2.2
			progress.notifyMessage("Label mode is on for the basal bodies, but CiliaQ Preparator marked channel " + cBasalStain
					+ " as a segmented intensity channel, not as labels: label mode joins only voxels of the same value, so it"
					+ " splits the basal bodies. Turn label mode off unless the channel holds labels." + where,
					ProgressReporter.NOTIFICATION);
		}		
		if(!chosenIsSegm && segmChannel != -1){
			progress.notifyMessage("Channel " + cReconstruction + " is not labeled as segmented channel"
					+ " - did you mean channel " + segmChannel + "? The input image likely came from CiliaQ"
					+ " Preparator, which marked channel " + segmChannel + " as segmented."
					+ where, ProgressReporter.NOTIFICATION);
		}

		/*
		 * Check 2: a channel byte-identical to the FOLLOWING channel means segmentation removed
		 * nothing -- the threshold fell below the lowest non-zero intensity, so the "segmented"
		 * channel equals the unsegmented duplicate beside it.
		 *
		 * The Preparator reports the CAUSE; this reports the SYMPTOM, and it also covers images that
		 * never went through the Preparator.
		 */
		if(cReconstruction + 1 <= imp.getNChannels()
				&& channelsIdentical(imp, cReconstruction, cReconstruction + 1)){
			progress.notifyMessage("Channel " + cReconstruction + " is identical to channel "
					+ (cReconstruction + 1) + " suggesting segmentation removed nothing, so a threshold, "
					+ "if any intensity threshold was used, was probably below the lowest non-zero intensity."
					+ where,
					ProgressReporter.NOTIFICATION);
		}

		/*
		 * Check 3: the reconstruction channel holds exactly ONE non-zero value, so it
		 * carries no intensity information and every "... (in reconstruction channel)" column is
		 * that constant -- e.g. 65535 for the Preparator's "set to maximum" style.
		 */
		double single = singleNonZeroValue(imp, cReconstruction);
		if(!Double.isNaN(single)){
			progress.notifyMessage("Channel " + cReconstruction + " holds a single non-zero value ("
					+ single + "), so it is a binary mask: every intensity column measured in the"
					+ " reconstruction channel will report that constant and means nothing."
					+ where, ProgressReporter.NOTIFICATION);
		}
		if(cBasalStain > 0){
			double singleBB = singleNonZeroValue(imp, cBasalStain);
			if(!Double.isNaN(singleBB)){
				progress.notifyMessage("Basal stain channel " + cBasalStain + " holds a single"
					+ " non-zero value (" + singleBB + "), so the basal body intensity columns"
					+ " report that constant and mean nothing."
					+ where, ProgressReporter.NOTIFICATION);
			}
		}
	}

	/** 
	 * True if two channels are pixel-identical across every slice and frame.
	 * New in v0.2.2
	 * */
	boolean channelsIdentical(ImagePlus imp, int cA, int cB){
		for(int z = 1; z <= imp.getNSlices(); z++){
			for(int f = 1; f <= imp.getNFrames(); f++){
				ImageProcessor a = imp.getStack().getProcessor(imp.getStackIndex(cA, z, f));
				ImageProcessor b = imp.getStack().getProcessor(imp.getStackIndex(cB, z, f));
				int n = a.getWidth() * a.getHeight();
				for(int i = 0; i < n; i++){
					if(a.getf(i) != b.getf(i)){
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * The single non-zero value in a channel, or NaN if there are none or more than one.
	 * Returns early on the second distinct value, for efficiency on a normal intensity channel.
	 * New in v0.2.2
	 */
	double singleNonZeroValue(ImagePlus imp, int c){
		double found = Double.NaN;
		for(int z = 1; z <= imp.getNSlices(); z++){
			for(int f = 1; f <= imp.getNFrames(); f++){
				ImageProcessor ip = imp.getStack().getProcessor(imp.getStackIndex(c, z, f));
				int n = ip.getWidth() * ip.getHeight();
				for(int i = 0; i < n; i++){
					double v = ip.getf(i);
					if(v == 0.0){
						continue;
					}
					if(Double.isNaN(found)){
						found = v;
					}else if(v != found){
						return Double.NaN;
					}
				}
			}
		}
		return found;
	}

	/**
	 * Is this voxel part of the SAME object as the seed voxel of the fill?
	 * 
	 * Without labels (labels = false): any non-zero neighbour, the criterion CiliaQ always used, so nothing changes.
	 * With labels: only a neighbour with the same label value, which separates TOUCHING objects. Exact == is safe,
	 * label images hold integer values; it stays a connectivity fill, so a label only needs to be unique locally.
	 * 
	 * New in v0.2.2
	 */
	boolean sameObject(double v, double seedValue, boolean labels){
		return v > 0.0 && (!labels || v == seedValue);
	}

	/**
	 * Did CiliaQ Preparator mark channel c (1-based) as a LABEL channel? It writes "segm labels ..."
	 * into the slice labels of a label channel (ridge detection, expansion from seeds) and "segm ..." into those of
	 * any other segmented channel.
	 * 
	 * New in v0.2.2
	 */
	static boolean isMarkedLabelChannel(ImagePlus imp, int c){
		if(c < 1 || c > imp.getNChannels())	return false;
		String label = imp.getStack().getSliceLabel(imp.getStackIndex(c, 1, 1));
		return label != null && label.startsWith("segm labels ");
	}

	/**
	 * Did CiliaQ Preparator mark channel c (1-based) as segmented ("segm ..." in its slice labels, label channels
	 * included: "segm labels ...")?
	 *
	 * New in v0.2.2
	 */
	static boolean isMarkedSegmentedChannel(ImagePlus imp, int c){
		if(c < 1 || c > imp.getNChannels()) {
			return false;
		}
		String label = imp.getStack().getSliceLabel(imp.getStackIndex(c, 1, 1));
		return label != null && label.startsWith("segm ");
	}

	/**
	 * New in v0.2.2: a log line saying what the intensity columns of the reconstruction channel hold when they hold no
	 * intensities. Since the columns are written in every case (the values can link the objects to other data),
	 * a user reading them needs to know what the numbers are:
	 *		- label mode: the label values; minimum = maximum = average = the label of the cilium (tested: the columns
	 *		  hold the labels 1 and 2);
	 *		- without label mode, when every object holds a single value (minimum = maximum, exact): label IDs or a
	 *		  binary mask, so no intensity (e.g. 255 for every cilium of a mask).
	 *
	 * Every object, not one: since a one-voxel cilium in an intensity image holds one value too, a single such cilium
	 * does not trigger the line.
	 *
	 * A log line, not a notification: since the start-up check already notifies about a binary mask, and the label
	 * hint about a label image read without label mode, a notification would repeat them; in label mode the user chose
	 * labels, so the line only confirms it. With it, a user can match a cilium in _CQ.txt to its label in the label image.
	 *
	 * @param nObjects		the cilia of the image
	 * @param nSingleValued	those whose voxels all hold the same value in the reconstruction channel
	 * @param where			" (File ..., Dir: ...)", appended to the line
	 * @param progress		the progress reporter (as the field of the same name; the parameter is kept from A7)
	 */
	void noteSingleValuedObjects(int nObjects, int nSingleValued, String where, ProgressReporter progress){
		if(labelModeCilia){
			progress.notifyMessage("Label mode: the intensity columns of the reconstruction channel give the label values"
					+ " (per frame: minimum = maximum = average = the label of the cilium; over time, labels that differ"
					+ " between frames are averaged), not intensities." + where, ProgressReporter.LOG);
		}else if(nObjects > 0 && nSingleValued == nObjects){
			progress.notifyMessage("Each of the " + nObjects + " objects holds a single value in the reconstruction channel"
					+ " (label IDs or a binary mask), so its intensity columns carry no intensity information." + where,
					ProgressReporter.LOG);
		}
	}

	/**
	 * If the user selected an output IMAGE instead of a settings file, load CiliaQ's settings file of that image when
	 * it exists: "X_CQP.tif" (CiliaQ Preparator's output) -> "X_CQP_CQ.txt"; a CiliaQ output "X_CQ_<part>.tif" (also
	 * with a date) -> the "X_CQ.txt" (or "X_CQ_<date>.txt") it belongs to, cutting "_<part>" from the end of the file
	 * name. "X_CQP.txt" is the Preparator's settings file, not CiliaQ's, so it is never loaded. Otherwise the file is
	 * returned unchanged.
	 *
	 * New in V0.2.2
	 */
	private File redirectTifToTxt(File chosen){
		if(chosen == null) {
			return chosen;
		}
		String fileName = chosen.getName(), lower = fileName.toLowerCase();
		if(!(lower.endsWith(".tif") || lower.endsWith(".tiff"))) {
			return chosen;
		}
		String stem = fileName.substring(0, fileName.lastIndexOf("."));
		if(stem.endsWith("_CQP"))	stem = stem + "_CQ";	// the Preparator's output: CiliaQ's settings file of it
		int cq = stem.lastIndexOf("_CQ");
		if(cq < 0 || stem.endsWith("_CQP")) {
			cq = -1;
		}
		
		// never cut below the run's own "_CQ" or "_CQ_<date>" (yyMMdd_HHmmss): that would be another run's settings file
		java.util.regex.Matcher dated = java.util.regex.Pattern.compile("_CQ_\\d{6}_\\d{6}").matcher(stem);
		int shortest = cq < 0 ? Integer.MAX_VALUE : (dated.find(cq) ? dated.end() : cq + 3);
		while(stem.length() >= shortest){
			File txt = new File(chosen.getParentFile(), stem + ".txt");
			if(txt.canRead()){
				IJ.log(PLUGINNAME + ": you selected an image (" + chosen.getName()
					+ "); loading CiliaQ's settings file of it instead (" + txt.getName() + ").");
				return txt;
			}
			int cut = stem.lastIndexOf("_");
			if(cut < 0)	break;
			stem = stem.substring(0, cut);
		}
		IJ.log(PLUGINNAME + ": you selected an image (" + chosen.getName() + "); no CiliaQ settings file ('..._CQ.txt') of it was found next to it.");
		return chosen;
	}

	/**
	 * Import settings from existing "...CQ.txt" file
	 */
	private boolean importSettings() {
		java.awt.FileDialog fd = new java.awt.FileDialog((Frame) null, "Select '..._CQ.txt' file to load settings!");
		fd.setDirectory(System.getProperty("user.dir", "."));
		fd.setMultipleMode(false);
		fd.setMode(FileDialog.LOAD);
		fd.setVisible(true);
		
		if(fd.getFiles() == null || fd.getFiles().length == 0) {
			return false;
		}
		File settingsFile = redirectTifToTxt(fd.getFiles()[0]);	// New in V0.2.2: automatically find related txt file if tif(f) selected
		
		//read preferences
		boolean readPrefs = false;
		boolean readToEnd = false;
		boolean versionOlderThanV014 = false;
		boolean versionOlderThanV022 = false;
		String tempString;
		IJ.log("READING PREFERENCES:");
		try {
			FileReader fr = new FileReader(settingsFile);
			BufferedReader br = new BufferedReader(fr);
			String line = "";							
			reading: while(true){
				try{
					line = br.readLine();
					if(line == null)	break reading;		// NEW in V0.2.2: actual EOF test
				}catch(IOException e){						// NEW in V0.2.2: report, do not swallow silently
					IJ.error("Settings file " + settingsFile + " could not be read to the end - "
							+ "settings are only partially applied: " + e.getMessage());
					break reading;
				}
				
				if(line.contains("Settings")) {
					readPrefs = true;
				}
				
				if(readPrefs){
					if(line.contains("	Calibration [") && line.contains("/px]")){
						// New in v0.2.2: the unit of the file's distances
						settingsFileUnit = line.substring(line.indexOf("Calibration [") + "Calibration [".length(), line.indexOf("/px]"));
					}
					if(line.contains("Measure intensity A")){
						if(line.contains("true")) {
							measureC2 = true;
						}else if(line.contains("false")) {
							measureC2 = false;
						}else {
							IJ.error("Reading problem - code rp01"); break reading;
						}
						IJ.log("Measure A = " + measureC2);
						
						line = br.readLine();
						if(line.contains("Measure intensity B")){
							if(line.contains("true")) {
								measureC3 = true;
							}else if(line.contains("false")) {
								measureC3 = false;
							}else {
								IJ.error("Reading problem - code rp02"); break reading;
							}
							IJ.log("Measure B = " + measureC3);
						} else {
							IJ.error("Reading problem - code rp03"); break reading;
						}
						
						line = br.readLine();
						if(line.contains("Basal stain present")){
							if(line.contains("true")) {
								measureBasal = true;
							}else if(line.contains("false")) {
								measureBasal = false;
							}else {
								IJ.error("Reading problem - code rp04"); break reading;
							}
							IJ.log("Basal stain = " + measureBasal);
						} else {
							IJ.error("Reading problem - code rp05"); break reading;
						}
																		
						line = br.readLine();
						if(line.contains("	Reconstruction")){
							tempString = line.substring(line.lastIndexOf("	")+1);
							if(tempString.contains(",") && !tempString.contains("."))	tempString = tempString.replace(",", ".");
							channelReconstruction = Integer.parseInt(tempString);	
							IJ.log("Reconstruction channel = " + channelReconstruction);
						} else {
							IJ.error("Reading problem - rp05r"); break reading;
						}
						
						line = br.readLine();
						if(measureC2 && line.contains("	A	")){
							tempString = line.substring(line.lastIndexOf("	")+1);
							if(tempString.contains(",") && !tempString.contains("."))	tempString = tempString.replace(",", ".");
							channelC2 = Integer.parseInt(tempString);	
							IJ.log("channel A = " + channelC2);
						}else if(!measureC2 && line.contains("")) {
							
						}else {
							IJ.error("Reading problem - rp05a"); break reading;
						}
						
						line = br.readLine();
						if(measureC3 && line.contains("	B	")){
							tempString = line.substring(line.lastIndexOf("	")+1);
							if(tempString.contains(",") && !tempString.contains("."))	tempString = tempString.replace(",", ".");
							channelC3 = Integer.parseInt(tempString);	
							IJ.log("channel B = " + channelC3);
						}else if(!measureC3 && line.contains("")) {
							
						}else {
							IJ.error("Reading problem - rp05b"); break reading;
						}
						
						line = br.readLine();
						if(measureBasal && line.contains("	basal stain	")){
							tempString = line;
							if(line.contains("segmented")) {
								//New in v0.2.0 - reading segmented BB channels
								segmentedBB = true;
								IJ.log("Segmented BB = " + segmentedBB);
								tempString = line;
								tempString = tempString.substring(tempString.lastIndexOf("	")+1);								
								if(tempString.contains(",") && !tempString.contains("."))	tempString = tempString.replace(",", ".");
								maxDistanceBBCiliumEnd = Double.parseDouble(tempString);
								IJ.log("Maximum BB Cilium Distance = " + maxDistanceBBCiliumEnd);

								tempString = line;
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));								
								tempString = tempString.substring(tempString.lastIndexOf("	")+1);
								bbAssignmentSelection = "unknown";
								for(int eop = 0; eop < bbAssignmentOptions.length; eop++) {
									if(tempString.equals(bbAssignmentOptions [eop])) {
										bbAssignmentSelection = bbAssignmentOptions [eop];
										break;
									}
								}
								if(bbAssignmentSelection.equals("unknown")) {
									IJ.error("Reading problem - code rp05bbAs"); break reading;
								}
								IJ.log("BB Asignment option = " + bbAssignmentSelection + "");								
								
								//Remove the part on segmentation and assignment to proceed with reading BB channel
								tempString = line;
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
							}
							tempString = tempString.substring(tempString.lastIndexOf("	")+1);
							if(tempString.contains(",") && !tempString.contains("."))	tempString = tempString.replace(",", ".");
							basalStainC = Integer.parseInt(tempString);	
							IJ.log("channel basal stain = " + basalStainC);
						}else if(!measureBasal && line.contains("")) {
							
						}else {
							IJ.error("Reading problem - rp05bs"); break reading;
						}
						
						line = br.readLine();
						if(line.contains("Minimum cilium size") && line.contains("Increase range for connecting cilia")){
							if(line.contains("Minimum basal body size") && line.contains("Increase range for connecting basal bodies")){
								//New in v0.2.0 - reading segmented BB channels
								tempString = line.substring(line.lastIndexOf("	")+1);
								if(tempString.contains("false")) {
									increaseRangeBB = false;
								}else if(tempString.contains("true")) {
									increaseRangeBB = true;
								}else {
									IJ.error("Reading problem - code rp06BB"); break reading;
								}
								IJ.log("Increase Range BB = " + increaseRangeBB);
								
								tempString = line.substring(0,line.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(tempString.lastIndexOf("	")+1);
								
								if(tempString.contains(",") && !tempString.contains("."))	tempString = tempString.replace(",", ".");
								minBBSize = Integer.parseInt(tempString);
								IJ.log("Minimum BB Size = " + minBBSize);
								
								tempString = line.substring(0,line.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(tempString.lastIndexOf("	")+1);
								if(tempString.contains("false")) {
									increaseRangeCilia = false;
								}else if(tempString.contains("true")) {
									increaseRangeCilia = true;
								}else {
									IJ.error("Reading problem - code rp06BBC"); break reading;
								}
								IJ.log("Increase Range Cilia = " + increaseRangeCilia);

								tempString = line.substring(0,line.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(tempString.lastIndexOf("	")+1);
								
								if(tempString.contains(",") && !tempString.contains("."))	tempString = tempString.replace(",", ".");
								minSize = Integer.parseInt(tempString);
								IJ.log("Minimum Cilium Size = " + minSize);
							}else {
								tempString = line.substring(line.lastIndexOf("	")+1);
								if(tempString.contains("false")) {
									increaseRangeCilia = false;
								}else if(tempString.contains("true")) {
									increaseRangeCilia = true;
								}else {
									IJ.error("Reading problem - code rp06"); break reading;
								}
								IJ.log("Increase Range Cilia = " + increaseRangeCilia);
								
								tempString = line.substring(0,line.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(tempString.lastIndexOf("	")+1);
								
								if(tempString.contains(",") && !tempString.contains("."))	tempString = tempString.replace(",", ".");
								minSize = Integer.parseInt(tempString);
								IJ.log("Minimum Cilium Size = " + minSize);
							}						
						}else {
							IJ.error("Reading problem - code rp07"); break reading;
						}

						line = br.readLine();
						if(line.contains("Label images (one value per object)")){
							readLabelLine(line);
							line = br.readLine();
						}
						if(line.contains("Additional filtering")){
							if(line.contains("	# cilia excluded by the BB filter:")) {
								// New in v0.2.2: a count, not a setting
								line = line.substring(0, line.indexOf("	# cilia excluded by the BB filter:"));
							}
							if(line.contains("BBs excluded:")) {
								//New from v0.2.0 on
//								tp.append("	Additional filtering	" + excludeSelection + " excluded	# excluded:	" + excludedCilia + "	of total #:	" + totalCilia
//								+ "	" + bbCiliaFilterSelection + " excluded	#BBs excluded:	" + excludedBBs + "	of total #BB:	" + totalBBs);
								tempString = line.substring(0,line.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(tempString.lastIndexOf("	")+1);
								
								bbCiliaFilterSelection = "unknown";
								for(int eop = 0; eop < bbCiliaFilterOptions.length; eop++) {
									if(tempString.equals(bbCiliaFilterOptions [eop] + " excluded")) {
										bbCiliaFilterSelection = bbCiliaFilterOptions [eop];
										break;
									}
								}
								if(bbCiliaFilterSelection.equals("unknown")) {
									IJ.error("Reading problem - code rp08BB"); break reading;
								}
								IJ.log("BB-Cilia-Excluding option = " + bbCiliaFilterSelection + " excluded");
								
								tempString = line.substring(0,line.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(tempString.lastIndexOf("	")+1);
								
								excludeSelection = "unknown";
								for(int eop = 0; eop < excludeOptions.length; eop++) {
									if(tempString.equals(excludeOptions [eop] + " excluded")) {
										excludeSelection = excludeOptions [eop];
										break;
									}
								}
								if(excludeSelection.equals("unknown")) {
									IJ.error("Reading problem - code rp08BBC"); break reading;
								}
								IJ.log("Exclude option = " + excludeSelection + " excluded");
							}else {
								tempString = line.substring(0,line.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(0,tempString.lastIndexOf("	"));
								tempString = tempString.substring(tempString.lastIndexOf("	")+1);
								
								excludeSelection = "unknown";
								for(int eop = 0; eop < excludeOptions.length; eop++) {
									if(tempString.equals(excludeOptions [eop] + " excluded")) {
										excludeSelection = excludeOptions [eop];
										break;
									}
								}
								if(excludeSelection.equals("unknown")) {
									IJ.error("Reading problem - code rp08"); break reading;
								}
								IJ.log("Exclude option = " + excludeSelection + " excluded");
							}
						}else {
							IJ.error("Reading problem - code rp09"); break reading;
						}
						
						line = br.readLine();
						if(line.contains("Minimum size of particles in A or B") && line.contains("Increase range for connecting particles in intensity regions")){
							tempString = line.substring(line.lastIndexOf("	")+1);
							if(tempString.contains("false")) {
								increaseRangeRegions = false;
							}else if(tempString.contains("true")) {
								increaseRangeRegions = true;
							}else {
								IJ.error("Reading problem - code rp10"); break reading;
							}
							IJ.log("Increase Range Particles A B = " + increaseRangeRegions);
							
							tempString = line.substring(0,line.lastIndexOf("	"));
							tempString = tempString.substring(0,tempString.lastIndexOf("	"));
							tempString = tempString.substring(tempString.lastIndexOf("	")+1);
							
							if(tempString.contains(",") && !tempString.contains("."))	tempString = tempString.replace(",", ".");
							minRestSize = Integer.parseInt(tempString);
							IJ.log("Min size particles A B = " + minRestSize);
						}else {
							IJ.error("Reading problem - code rp11"); break reading;
						}
						
						line = br.readLine();
						if(line.contains("Skeleton analysis - Gauss XY sigma")){
							skeletonize = true;
							if(line.contains("Skeleton analysis - reference distance")) {
								tempString = line.substring(line.lastIndexOf("	")+1);
								if(tempString.contains(",") && !tempString.contains("."))	tempString = tempString.replace(",", ".");
								refDistance = Double.parseDouble(tempString);	
								IJ.log("Reference distance = " + refDistance);
								line = line.substring(0,line.lastIndexOf("	"));
								line = line.substring(0,line.lastIndexOf("	"));
							}else {
								versionOlderThanV014 = true;
							}
							tempString = line.substring(line.lastIndexOf("	")+1);
							if(tempString.contains(",") && !tempString.contains("."))	tempString = tempString.replace(",", ".");
							gXY = Double.parseDouble(tempString);	
							IJ.log("Gauss XY sigma = " + gXY);
							
							line = br.readLine();
							if(!line.contains("Skeleton analysis - Gauss Z sigma")){
								IJ.error("Reading problem - code rp12"); break reading;
							}
							tempString = line.substring(line.lastIndexOf("	")+1);
							if(tempString.contains(",") && !tempString.contains("."))	tempString = tempString.replace(",", ".");
							gZ = Double.parseDouble(tempString);	
							IJ.log("Gauss Z sigma = " + gZ);
						} else {
							skeletonize = false;
							line = br.readLine();
						}
						
						line = br.readLine();
						if(!line.contains("Determined intensity thresholds")) {
							IJ.error("Reading problem - code rp13"); break reading;
						}

						line = br.readLine();	// skip A threshold line
						line = br.readLine();	// skip B threshold line
						line = br.readLine();	// skip basal threshold line
						
						line = br.readLine();	// NEW: This is now the "Time-lapse cilia connection" line (v0.2.2+) OR the blank (older files)
						
						// NEW: Optionally read time-lapse tracking settings (files v0.2.2+)
						if(line.contains("Time-lapse cilia connection")){
							String ts;
							// layout: \tTime-lapse cilia connection\t<sel>\tmax tracking distance [unit]\t<dist>\tmax frame gap [frames]\t<gap>
							ts = line.substring(line.lastIndexOf("	")+1);					// gap value
							if(ts.contains(",") && !ts.contains(".")) ts = ts.replace(",", ".");
							maxFrameGap = Integer.parseInt(ts);
							
							ts = line.substring(0,line.lastIndexOf("	"));					// drop gap value
							ts = ts.substring(0,ts.lastIndexOf("	"));						// drop "max frame gap [frames]" label
							ts = ts.substring(ts.lastIndexOf("	")+1);						// distance value
							if(ts.contains(",") && !ts.contains(".")) ts = ts.replace(",", ".");
							maxTrackingDistance = Double.parseDouble(ts);
							
							ts = line.substring(0,line.lastIndexOf("	"));					// drop gap value
							ts = ts.substring(0,ts.lastIndexOf("	"));						// drop gap label
							ts = ts.substring(0,ts.lastIndexOf("	"));						// drop dist value
							ts = ts.substring(0,ts.lastIndexOf("	"));						// drop dist label
							ts = ts.substring(ts.lastIndexOf("	")+1);						// selection value
							timelapseTrackingSelection = "unknown";
							for(int i = 0; i < timelapseTrackingOptions.length; i++){
								if(ts.equals(timelapseTrackingOptions[i])){ timelapseTrackingSelection = timelapseTrackingOptions[i]; break; }
							}
							if(timelapseTrackingSelection.equals("unknown")){ IJ.error("Reading problem - code rpTL"); break reading; }
							IJ.log("Time-lapse tracking = " + timelapseTrackingSelection + ", dist " + maxTrackingDistance + ", gap " + maxFrameGap);
							
							line = br.readLine();	// skip the blank line after tracking settings
							line = br.readLine();	// advance to the Results line
						}else{
							// older file (pre-v0.2.2): 'line' currently holds the blank line, so advance once to Results
							versionOlderThanV022 = true;
							line = br.readLine();	// advance to the Results line
						}
						
						if(line.contains("Results") || line.contains("Averaged results of the time-course analysis:")) {
							// For compatibility with previous settings dialogs check whether there is skeleton columns in the results data
							line = br.readLine();
							if((line.contains("# of found skeletons (quality parameter)") && line.contains("# branches (quality parameter)"))) {
								skeletonize = true;
							}else if(line.contains("ID	x")){
								skeletonize = false;
							}else {
								IJ.error("Reading problem"); break reading;
							}
							IJ.log("Skeletonize = " + skeletonize);
							readToEnd = true;
							break reading;
						}else {
							IJ.error("Reading problem"); break reading;
						}
					}
				}
			}
			if(readToEnd && versionOlderThanV014 && skeletonize) {
				GenericDialog gd = new GenericDialog(PLUGINNAME + " on " + System.getProperty("os.name") + " - missing detection preferences");	
				//show Dialog-----------------------------------------------------------------
				//.setInsets(top, left, bottom)
				gd.setInsets(0,0,0);	gd.addMessage("ATTENTION", SubHeadingFont);
				gd.setInsets(0,0,0);	gd.addMessage("You loaded settings from a file created with a CiliaQ version before v0.1.4.", InstructionsFont);
				gd.setInsets(0,0,0);	gd.addMessage("From v0.1.4 on, a new analysis parameter needs to be defined. Define it here.", InstructionsFont);
				gd.setInsets(0,0,0);	gd.addMessage("Note: the parameter does not affect any output parameters that have been also", InstructionsFont);
				gd.setInsets(0,0,0);	gd.addMessage("output by versions before v0.1.4.", InstructionsFont);
				gd.setInsets(5,0,0);	gd.addMessage("More information: https://github.com/hansenjn/CiliaQ/releases/tag/v0.1.4", InstructionsFont);

				gd.setInsets(20,0,0);	gd.addMessage("Define missing parameter:", SubHeadingFont);
				gd.setInsets(5,0,0);	gd.addNumericField("Reference distance for tangents and curvature [calibration unit (e.g. µm)]: ", refDistance, 2);
				
				gd.showDialog();
				//show Dialog-----------------------------------------------------------------

				//read and process variables--------------------------------------------------
				refDistance = (double) gd.getNextNumber();
					
				//read and process variables--------------------------------------------------
				if (gd.wasCanceled()) {
					br.close();
					fr.close();
					return false;
				}
			}
			if(readToEnd && versionOlderThanV022) {
				GenericDialog gd = new GenericDialog(PLUGINNAME + " on " + System.getProperty("os.name") + " - missing detection preferences");	
				//show Dialog-----------------------------------------------------------------
				//.setInsets(top, left, bottom)
				gd.setInsets(0,0,0);	gd.addMessage("ATTENTION", SubHeadingFont);
				gd.setInsets(0,0,0);	gd.addMessage("You loaded settings from a file created with a CiliaQ version before v0.2.2."
						+ "\nFrom v0.2.2 on, a new analysis parameter needs to be defined. Define it here."
						+ "\nNote: the parameters are just relevant for time-lapse tracking and set to the"
						+ "\ndefault from before v0.2.2 (pixel-wise connection)."
						+ "\nIf you don't have time-lapse data just leave at default and confirm.", InstructionsFont);
				gd.setInsets(5,0,0);	gd.addMessage("More information: https://github.com/hansenjn/CiliaQ/releases/tag/v0.2.2", InstructionsFont);

				gd.setInsets(20,0,0);	gd.addMessage("Define missing parameter:", SubHeadingFont);
				gd.setInsets(0,0,0);	gd.addChoice("Time-lapse cilia connection: ", timelapseTrackingOptions, timelapseTrackingSelection);
				gd.setInsets(0,0,0);	gd.addMessage("Max tracking distance: linear assignment only. "
						+ "Max frame gap: classic and linear assignment; "
						+ "0 = as before v0.2.2.", InstructionsFont);	// New in v0.2.2
				gd.setInsets(0,0,0);	gd.addNumericField("    -> max tracking distance [calibrated unit]: ", maxTrackingDistance, 2);
				gd.setInsets(0,0,0);	gd.addNumericField("    -> max frame gap [frames]: ", 0, 0);	// New in v0.2.2: 0 = the connection before v0.2.2
								
				gd.showDialog();
				//show Dialog-----------------------------------------------------------------
		
				//read and process variables--------------------------------------------------				
				timelapseTrackingSelection = gd.getNextChoice();
				maxTrackingDistance = (double) gd.getNextNumber();
				maxFrameGap = (int) gd.getNextNumber();					
				//read and process variables--------------------------------------------------
				
				if (gd.wasCanceled()) {
					br.close();
					fr.close();
					return false;
				}
			}
			br.close();
			fr.close();
		}catch (IOException e) {
			IJ.error("Problem with loading preferences - code rp00");
			e.printStackTrace();
			return false;
		}
		if(readPrefs && readToEnd) {
			return true;
		}else {
			IJ.error("File did not contain CiliaQ preferences information.");
			return false;
		}
	}
	
	/**
	 * Returns the tracking anchor for a cilium: BB center (calibrated) if available,
	 * otherwise the cilium centroid. New in v0.2.2
	 */
	private double[] getAnchor(Cilium c){	// NEW
		if(c.bbAvailable){
			return new double[]{c.bbX * c.calibration, c.bbY * c.calibration, c.bbZ * c.voxelDepth};
		}
		return new double[]{c.xC, c.yC, c.zC};
	}

	/**
	 * Per-frame BB-to-cilium assignment (Option X). Runs the same Hungarian
	 * assignment used in the classic 4D block, but on a single frame's cilia list.
	 * Returns the boolean[] of which BBs were used. New in v0.2.2
	 */
	private boolean[] assignBBsForFrame(ArrayList<Cilium> frameCilia, ArrayList<Uncalibrated3DPoint> frameBBs,
			ImagePlus imp, boolean measureC2local, boolean measureC3local){	// NEW
		boolean bbUsed [] = new boolean [frameBBs.size()];
		Arrays.fill(bbUsed,false);
		if(frameCilia.size() == 0 || frameBBs.size() == 0){
			return bbUsed;
		}
		
		double minDist, dist;
		double sklPoints [][];
		double matrixDist [][] = new double [frameCilia.size()][frameBBs.size()];
		double matrixDistSquared [][] = new double [frameCilia.size()][frameBBs.size()];
		Cilium theCilium;
		
		for(int bb = 0; bb < frameBBs.size(); bb++){
			for(int cil = 0; cil < frameCilia.size(); cil++){
				theCilium = frameCilia.get(cil);
				if(theCilium.sklAvailable) {
					minDist = Double.POSITIVE_INFINITY;
					sklPoints = theCilium.getSkeletonPointsForOriginalImage();
					for(int skl = 0; skl < sklPoints.length; skl++) {
						dist = getDistance(frameBBs.get(bb).x * theCilium.calibration,
								frameBBs.get(bb).y * theCilium.calibration,
								frameBBs.get(bb).z * theCilium.voxelDepth,
								sklPoints[skl][0], sklPoints[skl][1], sklPoints[skl][2]);
						if(dist < minDist) minDist = dist;
					}
					matrixDist [cil][bb] = minDist;
				}else {
					matrixDist [cil][bb] = getDistance(frameBBs.get(bb).x * theCilium.calibration,
							frameBBs.get(bb).y * theCilium.calibration,
							frameBBs.get(bb).z * theCilium.voxelDepth,
							theCilium.xC, theCilium.yC, theCilium.zC);
				}
				matrixDistSquared [cil][bb] = matrixDist [cil][bb];
				if(matrixDistSquared [cil][bb] > maxDistanceBBCiliumEnd) {
					matrixDistSquared [cil][bb] = maxDistanceBBCiliumEnd;
				}
				matrixDistSquared [cil][bb] = matrixDistSquared [cil][bb] * matrixDistSquared [cil][bb];
			}
		}
		
		int [] bbForCilium = new HungarianAlgorithm(matrixDistSquared).execute();
		for(int cil = 0; cil < frameCilia.size(); cil++){
			if(bbForCilium[cil] > -1 && matrixDist[cil][bbForCilium[cil]] <= maxDistanceBBCiliumEnd) {
				frameCilia.get(cil).addBasalBody(frameBBs.get(bbForCilium[cil]), imp,
						measureC2local, channelC2, measureC3local, channelC3,
						channelReconstruction, progress);
				bbUsed[bbForCilium[cil]] = true;
			}
		}
		return bbUsed;
	}
	
	/**
	 * Builds TimelapseCilium objects by detecting cilia per frame and linking them
	 * frame-to-frame via linear-sum assignment (Hungarian). Anchored on BB centers
	 * (fallback: cilium centroid if no BB). Allows up to maxFrameGap missed frames.
	 * New in v0.2.2
	 */
	private ArrayList<TimelapseCilium> trackCiliaLinearAssignment(ImagePlus imp,
			boolean measureC2local, boolean measureC3local, boolean measureBasalLocal,
			double[] intensityThresholds, String dir, String name){
		
		// 1) Per-frame cilia particles (T-connection disabled)
		ArrayList<ArrayList<CellPoint>> flat = getCiliaObjectsTimelapse(imp, channelReconstruction, increaseRangeCilia, false);
		if(flat.size() == 0){
			return new ArrayList<TimelapseCilium>(0);
		}
		
		// compute intensity thresholds from the flat per-frame particles (numerically identical
		// to classic mode since it's the same voxels). Fills the passed-in array in place.
		double[] thr = getIntensityThresholds(imp, flat);
		System.arraycopy(thr, 0, intensityThresholds, 0, thr.length);
		
		// Bucket per frame
		ArrayList<ArrayList<ArrayList<CellPoint>>> perFrameParticles = new ArrayList<ArrayList<ArrayList<CellPoint>>>(imp.getNFrames());
		for(int t = 0; t < imp.getNFrames(); t++){
			perFrameParticles.add(new ArrayList<ArrayList<CellPoint>>());
		}
		for(int i = 0; i < flat.size(); i++){
			// every object is single-frame now, so its t is that of its first point
			perFrameParticles.get(flat.get(i).get(0).t).add(flat.get(i));
		}
		flat.clear();
		flat.trimToSize();
		
		// 2) Build per-frame Cilium objects
		ArrayList<ArrayList<Cilium>> perFrameCilia = new ArrayList<ArrayList<Cilium>>(imp.getNFrames());
		for(int t = 0; t < imp.getNFrames(); t++){
			perFrameCilia.add(new ArrayList<Cilium>(perFrameParticles.get(t).size()));
			for(int i = 0; i < perFrameParticles.get(t).size(); i++){
				progress.updateBarText("Detecting cilia per frame (t " + (t+1) + "/" + imp.getNFrames() + ")");
				perFrameCilia.get(t).add(new Cilium(perFrameParticles.get(t).get(i), imp,
						measureC2local, channelC2, measureC3local, channelC3, measureBasalLocal, basalStainC,
						channelReconstruction, gXY, gZ, intensityThresholds, progress, skeletonize, joinGap));
			}
		}
		perFrameParticles.clear();
		perFrameParticles.trimToSize();
		
		// 3) Per-frame BB assignment BEFORE tracking so anchors can use BB centers
		if(segmentedBB){
			ArrayList<ArrayList<Uncalibrated3DPoint>> bbParticles = getBBObjectsTimelapse(imp, basalStainC, increaseRangeBB);
			
			/*
			 * Similar to classic mode, we notify here in linear mode also when
			 * no BB is found and empty BB columns are produced.
			 */
			int totalNrOfBBsLinear = 0;
			for(int t = 0; t < bbParticles.size(); t++){
				totalNrOfBBsLinear += bbParticles.get(t).size();
			}
			if(totalNrOfBBsLinear == 0){
				progress.notifyMessage("No BBs found in image - no BBs added. File " + name
						+ " (Dir: " + dir + ")", ProgressReporter.NOTIFICATION);
			}
			
			for(int t = 0; t < imp.getNFrames() && t < bbParticles.size(); t++){
				progress.updateBarText("Assigning BBs per frame (t " + (t+1) + "/" + imp.getNFrames() + ")");
				
				boolean[] bbUsed = assignBBsForFrame(perFrameCilia.get(t), bbParticles.get(t), imp, measureC2local, measureC3local);
				
				// turn unused BBs into BB-only Cilium objects and add them so they get tracked too (answer #3)
				for(int bb = 0; bb < bbParticles.get(t).size(); bb++){
					if(bbUsed[bb]) continue;
					perFrameCilia.get(t).add(new Cilium(bbParticles.get(t).get(bb), t, imp,
							measureC2local, channelC2, measureC3local, channelC3, measureBasalLocal, basalStainC,
							channelReconstruction, gXY, gZ, intensityThresholds, progress, skeletonize));
				}
			}
		}
		
		// 4) Link across frames via Hungarian.
		// A "track" is an ArrayList<Cilium>. We keep a list of open tracks with their last frame index.
		ArrayList<ArrayList<Cilium>> tracks = new ArrayList<ArrayList<Cilium>>();
		ArrayList<Integer> trackLastFrame = new ArrayList<Integer>();
		
		// seed tracks from first non-empty frame handling; simply iterate frames
		for(int t = 0; t < imp.getNFrames(); t++){
			ArrayList<Cilium> current = perFrameCilia.get(t);
			
			// Open tracks eligible to connect to frame t: last frame within [t-1-maxFrameGap, t-1]
			ArrayList<Integer> openIdx = new ArrayList<Integer>();
			for(int tr = 0; tr < tracks.size(); tr++){
				int gap = t - trackLastFrame.get(tr) - 1; // 0 = consecutive
				if(gap >= 0 && gap <= maxFrameGap){
					openIdx.add(tr);
				}
			}
			
			if(current.size() == 0){
				continue; // nothing to assign in this frame; open tracks may still catch a later frame within gap
			}
			
			if(openIdx.size() == 0){
				// no open tracks -> every current cilium starts a new track
				for(int i = 0; i < current.size(); i++){
					ArrayList<Cilium> nt = new ArrayList<Cilium>();
					nt.add(current.get(i));
					tracks.add(nt);
					trackLastFrame.add(t);
				}
				continue;
			}
			
			// Cost matrix: rows = open tracks, cols = current cilia
			double cost [][] = new double [openIdx.size()][current.size()];
			double rawDist [][] = new double [openIdx.size()][current.size()];
			for(int r = 0; r < openIdx.size(); r++){
				Cilium last = tracks.get(openIdx.get(r)).get(tracks.get(openIdx.get(r)).size()-1);
				double [] aLast = getAnchor(last);
				for(int col = 0; col < current.size(); col++){
					double [] aCur = getAnchor(current.get(col));
					double d = getDistance(aLast[0],aLast[1],aLast[2], aCur[0],aCur[1],aCur[2]);
					rawDist[r][col] = d;
					// gate: beyond maxTrackingDistance -> clamp so it won't be preferred, reject later
					cost[r][col] = (d > maxTrackingDistance) ? maxTrackingDistance : d;
					cost[r][col] = cost[r][col] * cost[r][col];
				}
			}
			
			progress.updateBarText("Linking cilia across frames (t " + (t+1) + "/" + imp.getNFrames() + ")");
			int [] curForTrack = new HungarianAlgorithm(cost).execute();
			
			boolean curUsed [] = new boolean [current.size()];
			Arrays.fill(curUsed, false);
			
			for(int r = 0; r < openIdx.size(); r++){
				int col = curForTrack[r];
				if(col > -1 && rawDist[r][col] <= maxTrackingDistance){
					tracks.get(openIdx.get(r)).add(current.get(col));
					trackLastFrame.set(openIdx.get(r), t);
					curUsed[col] = true;
				}
			}
			
			// Unmatched current cilia -> new tracks
			for(int col = 0; col < current.size(); col++){
				if(!curUsed[col]){
					ArrayList<Cilium> nt = new ArrayList<Cilium>();
					nt.add(current.get(col));
					tracks.add(nt);
					trackLastFrame.add(t);
				}
			}
		}
		
		// 5) Emit TimelapseCilium objects (tracks are already time-ordered by construction)
		ArrayList<TimelapseCilium> out = new ArrayList<TimelapseCilium>(tracks.size());
		for(int tr = 0; tr < tracks.size(); tr++){
			progress.updateBarText("Assembling tracked cilia (" + (tr+1) + "/" + tracks.size() + ")");
			out.add(new TimelapseCilium(tracks.get(tr), imp, measureC2local, measureC3local, skeletonize, progress));
			
			// compute the basal body parameters
			out.get(out.size() - 1).computeBBParameters();
		}
		
		// Track-level BB-cilia filtering. Applied per whole track.
		// bbCiliaFilterOptions: 0 "nothing", 1 "BBs without cilia", 2 "cilia without BBs", 3 "both"
			
		if(segmentedBB){
			for(int tr = 0; tr < out.size(); tr++){
				boolean trackHasBB = false, trackHasCilium = false;
				for(int cil = 0; cil < out.get(tr).cilia.size(); cil++){
					if(out.get(tr).cilia.get(cil).bbAvailable)		trackHasBB = true;
					if(out.get(tr).cilia.get(cil).ciliumAvailable)	trackHasCilium = true;
				}
				// "BBs without cilia" excluded (a BB-only track = has BB but never a cilium)
				if((bbCiliaFilterSelection.equals(bbCiliaFilterOptions[1]) || bbCiliaFilterSelection.equals(bbCiliaFilterOptions[3]))
						&& trackHasBB && !trackHasCilium){
					out.get(tr).excluded = true;
				}
				// "cilia without BBs" excluded (a cilium track that never gets a BB)
				if((bbCiliaFilterSelection.equals(bbCiliaFilterOptions[2]) || bbCiliaFilterSelection.equals(bbCiliaFilterOptions[3]))
						&& trackHasCilium && !trackHasBB){
					out.get(tr).excluded = true;
				}
			}
		}
		
		return out;
	}
	
	/**
	 * Saves a list of all Skeletonpoints of all cilia with XYZT coordinates
	 * */
	private void saveSkeletonPoints(ArrayList<Cilium> cilia, String dir, String name, String path, Date date) {
		// create file
		OutputTextFile tp =new OutputTextFile("");
		
		// write file
		{
			// write column names
			String addStr = "Directory";
			addStr += "	" + "File name"
					+ "	" + "ID"
					+ "	" + "Point-ID"
					+ "	" + "T [frames]"
					+ "	" + "X [" + calibrationDimension + "]"
					+ "	" + "Y [" + calibrationDimension + "]"
					+ "	" + "Z [" + calibrationDimension + "]"
					+ "	" + "Arc length  [" + calibrationDimension + "]"
					+ "	" + "Tangent vector X [" + calibrationDimension + "]"
					+ "	" + "Tangent vector Y [" + calibrationDimension + "]"
					+ "	" + "Tangent vector Z [" + calibrationDimension + "]"
					+ "	" + "Curvature [" + calibrationDimension + "^(-1)]"
					+ "	" + "Intensity A"
					+ "	" + "Intensity B"
					+ "	" + "Intensity A (normalized to reconstruction channel)"
					+ "	" + "Intensity B (normalized to reconstruction channel)";
			tp.append(addStr + this.getOneRowFooter(date));
			
			// write content
			int ciliumID;
			double points [][], tangents [][], curvatures [];

			double arclength = 0.0;
			
			for(int cil = 0; cil < cilia.size(); cil++) {
				if(cilia.get(cil).excluded){
					continue;
				}
				
				if(!cilia.get(cil).sklAvailable) {
					continue;
				}
				
				ciliumID = cil + 1;
				progress.updateBarText("Saving individual cilia points (" + cil + "/" + cilia.size() + " done)");
				
				points = cilia.get(cil).getSkeletonPointsForOriginalImage();
				tangents = getTangents(points);
				curvatures = getCurvatures(points, tangents);
				
				arclength = 0.0;
				
				for(int p = 0; p < points.length; p++) {
					addStr = dir 
							+ "	" + name 
							+ "	" + ciliumID 
							+ "	" + (p+1);
					addStr += "	" + cilia.get(cil).t;	//T
					addStr += "	" + dformat6.format(points [p][0]);	//X
					addStr += "	" + dformat6.format(points [p][1]);	//Y
					addStr += "	" + dformat6.format(points [p][2]);	//Z
					if(p == 0) {
						addStr += "	" + dformat6.format(0.0);	//Arc length
					}else {
						arclength += getDistance(points [p][0],points [p][1],points [p][2],
								points [p-1][0],points [p-1][1],points [p-1][2]);
						addStr += "	" + dformat6.format(arclength);	//Arc length
					}
					addStr += "	";
					if(!Double.isNaN(tangents[p][0])) addStr += dformat6.format(tangents [p][0]);	// Tangent X
					addStr += "	";
					if(!Double.isNaN(tangents[p][1])) addStr += dformat6.format(tangents [p][1]);	// Tangent Y
					addStr += "	";
					if(!Double.isNaN(tangents[p][2])) addStr += dformat6.format(tangents [p][2]);	// Tangent Z
					addStr += "	";
					if(!Double.isNaN(curvatures[p])) addStr += dformat6.format(curvatures [p]);	//Curvature
					addStr += "	";
					if(measureC2) addStr += fmt6(cilia.get(cil).profileC2[p]);	//Intensity A
					addStr += "	";
					if(measureC3) addStr += fmt6(cilia.get(cil).profileC3[p]);	//Intensity B
					addStr += "	";
					if(measureC2) addStr += fmt6(cilia.get(cil).profileC2norm[p]);	//Intensity A Norm
					addStr += "	";
					if(measureC3) addStr += fmt6(cilia.get(cil).profileC3norm[p]);	//Intensity B Norm
					tp.append(addStr);
				}
			}
		}
						
		// save file
		if(!tp.saveAndFinish(path)) {
			progress.notifyMessage("IO ERROR when saving file " + path, ProgressReporter.ERROR);
		}
	}
	
	/**
	 * Saves a list of all Skeletonpoints of all cilia with XYZT coordinates
	 * Included from version v0.1.4 on
	 * Outputs additional parameters: tangent angle, arc length, curvature
	 * */
	private void saveTimelapseSkeletonPoints(ArrayList<TimelapseCilium> tlCilia, String dir, String name, String path, Date date) {
		// create file
		OutputTextFile tp =new OutputTextFile("");
		
		// write file
		{
			// write column names
			String addStr = "Directory";
			addStr += "	" + "File name"
					+ "	" + "ID"
					+ "	" + "Point-ID"
					+ "	" + "T [frames]"
					+ "	" + "X [" + calibrationDimension + "]"
					+ "	" + "Y [" + calibrationDimension + "]"
					+ "	" + "Z [" + calibrationDimension + "]"
					+ "	" + "Arc length  [" + calibrationDimension + "]"
					+ "	" + "Tangent vector X [" + calibrationDimension + "]"
					+ "	" + "Tangent vector Y [" + calibrationDimension + "]"
					+ "	" + "Tangent vector Z [" + calibrationDimension + "]"
					+ "	" + "Curvature [" + calibrationDimension + "^(-1)]"
					+ "	" + "Intensity A"
					+ "	" + "Intensity B"
					+ "	" + "Intensity A (normalized to reconstruction channel)"
					+ "	" + "Intensity B (normalized to reconstruction channel)";
			tp.append(addStr + this.getOneRowFooter(date));
			
			// write content
			ArrayList<Cilium> cilia;
			int ciliumID;
			double points [][], tangents [][], curvatures [];
			double arclength = 0.0;
			
			for(int tlc = 0; tlc < tlCilia.size(); tlc++) {
				if(tlCilia.get(tlc).excluded){
					continue;
				}
				
				ciliumID = tlc + 1;
				progress.updateBarText("Saving individual cilia points (" + tlc + "/" + tlCilia.size() + " done)");
				
				cilia = tlCilia.get(tlc).cilia;
				
				for(int cil = 0; cil < cilia.size(); cil++) {
					if(!cilia.get(cil).sklAvailable) {
						continue;
					}
					points = cilia.get(cil).getSkeletonPointsForOriginalImage();
					tangents = getTangents(points);
					curvatures = getCurvatures(points, tangents);

					arclength = 0.0;
					
					for(int p = 0; p < points.length; p++) {
						addStr = dir 
								+ "	" + name 
								+ "	" + ciliumID 
								+ "	" + (p+1);
						addStr += "	" + cilia.get(cil).t;	//T
						addStr += "	" + dformat6.format(points [p][0]);	//X
						addStr += "	" + dformat6.format(points [p][1]);	//Y
						addStr += "	" + dformat6.format(points [p][2]);	//Z
						if(p == 0) {
							addStr += "	" + dformat6.format(0.0);	//Arc length
						}else {
							arclength += getDistance(points [p][0],points [p][1],points [p][2],
									points [p-1][0],points [p-1][1],points [p-1][2]);
							addStr += "	" + dformat6.format(arclength);	//Arc length
						}
						
						addStr += "	";
						if(!Double.isNaN(tangents[p][0])) addStr += dformat6.format(tangents [p][0]);	// Tangent X
						addStr += "	";
						if(!Double.isNaN(tangents[p][1])) addStr += dformat6.format(tangents [p][1]);	// Tangent Y
						addStr += "	";
						if(!Double.isNaN(tangents[p][2])) addStr += dformat6.format(tangents [p][2]);	// Tangent Z
						addStr += "	";
						if(!Double.isNaN(curvatures[p])) addStr += dformat6.format(curvatures [p]);	//Curvature
						addStr += "	";
						if(measureC2) addStr += fmt6(cilia.get(cil).profileC2[p]);	//Intensity A
						addStr += "	";
						if(measureC3) addStr += fmt6(cilia.get(cil).profileC3[p]);	//Intensity B
						addStr += "	";
						if(measureC2) addStr += fmt6(cilia.get(cil).profileC2norm[p]);	//Intensity A Norm
						addStr += "	";
						if(measureC3) addStr += fmt6(cilia.get(cil).profileC3norm[p]);	//Intensity B Norm
						tp.append(addStr);
						
					}
				}
			}
		}
						
		// save file
		if(!tp.saveAndFinish(path)) {
			progress.notifyMessage("IO ERROR when saving file " + path, ProgressReporter.ERROR);
		}
	}
	
	/**
	 * For a list of @param points (double array with first dimension point ID and second dimensions X(0), Y(1), or Z(2) coordinate
	 * @return the tangent vectors.
	 * Included from version v0.1.4 on.
	 * */
	double [][] getTangents (double [][] points) {
		double tangents [][] = new double [points.length][3];
		double p1 [] = new double [3];
		double p2 [] = new double [3];
		
		for(int p = 0; p < points.length; p++) {
			p1 = points[p];
			p2 = points[p];
			
			searchUpstream: for(int vu = points.length-1; vu >= p; vu--){
				if(getArclengthDistance(points,p,vu) <= refDistance){
					p2 = points [vu];
//					progress.notifyMessage("boundary: " + getArclengthDistance(points,p,vu) , ProgressReporter.LOG);
					break searchUpstream;
				}else {
//					progress.notifyMessage("not in boundary", ProgressReporter.ERROR);
				}
			}
			
			searchDownstream: for(int vd = 0; vd <= p; vd++){
				if(getArclengthDistance(points,vd,p) <= refDistance){
//					progress.notifyMessage("boundary: " + getArclengthDistance(points,p,vd) , ProgressReporter.LOG);
					p1 = points [vd];
					break searchDownstream;
				}else {
//					progress.notifyMessage("not in boundary", ProgressReporter.ERROR);
				}
			}
			tangents [p][0] = p2 [0] - p1 [0];
			tangents [p][1] = p2 [1] - p1 [1];
			tangents [p][2] = p2 [2] - p1 [2];
			
			//normalize vector
			tangents [p] = tools.getNormalizedVector(tangents[p]);
		}
		
		return tangents;
	}
	
	/**
	 * For a list of @param tangent vectors (double array with first dimension point ID and second dimensions X(0), Y(1), or Z(2) coordinate
	 * @return the curvature.
	 * Included from version v0.1.4 on.
	 * */
	double [] getCurvatures (double [][] points, double [][] tangents) {
		double curvatures [] = new double [tangents.length];
		double t1 [] = new double [3];
		double t2 [] = new double [3];
		double al = 0.0;
		
		for(int t = 0; t < tangents.length; t++) {
			t1 = tangents[t];
			t2 = tangents[t];
			
			searchUpstream: for(int vu = tangents.length-1; vu >= t; vu--){
				if(getArclengthDistance(points,t,vu)<= refDistance){
					al = getArclengthDistance(points,t,vu);
					t2 = tangents [vu];
					break searchUpstream;
				}
			}
			
			searchDownstream: for(int vd = 0; vd <= t; vd++){
				if(getArclengthDistance(points,vd,t)<= refDistance){
					al = Math.abs(al)+Math.abs(getArclengthDistance(points,vd,t));
					t1 = tangents [vd];
					break searchDownstream;
				}
			}
			curvatures [t] = getSignedCurvature(t1,t2,al);;
		}
		
		return curvatures;
	}
	
	/**
	 * @return the curvature between two tangent vectors @param t1 (3D vector) and @param t2 (3D vector)
	 * The curvature is defined as |0.5 * (t2 - t1)| / @param arclength distance
	 * Multiply by crossproduct / |crossproduct| to get a sign
	 * */
	public static double getSignedCurvature(double [] t1, double t2 [], double arclength){
		if(arclength == 0.0)	return 0.0;		
		return Math.signum(tools.crossProduct(t2, t1)[2]) 
				* Math.sqrt(Math.pow(t2[0]-t1[0], 2.0)+Math.pow(t2[1]-t1[1], 2.0)+Math.pow(t2[2]-t1[2], 2.0)) 
				/ 2.0 / arclength;
	}
	
	double getArclengthDistance(double points [][], int indexA, int indexB) {
		int start = indexA, end = indexB;
		if(indexA > indexB) {
			start = indexB;
			end = indexA;
		}
		
		double al = 0.0;
		for(int p = start+1; p <= end; p++) {
			al += getDistance(points [p][0],points [p][1],points [p][2],
					points [p-1][0],points [p-1][1],points [p-1][2]);
		}
		return al;
	}
	
	/**
	 * @return 3D distance between two points with coordinates (x,y,z)
	 * @param pX, pY, pZ (double values for x,y,z)
	 * @param qX, qY, qZ (double values for x,y,z)
	 * */
	private double getDistance(double pX, double pY, double pZ, double qX, double qY, double qZ) {
		return Math.sqrt(Math.pow(pX-qX,2.0)+Math.pow(pY-qY,2.0)+Math.pow(pZ-qZ,2.0));
	}
	
	/** 
	 * Fix format in V0.2.2:
	 * Before DecimalFormat rendered NaN as U+FFFD and Infinity as U+221E on Java 8, which Fiji
	 *  runs. Neither is parseable as a number, so emit an empty cell instead. */
	private String fmt6(double value){
		return Double.isFinite(value) ? dformat6.format(value) : "";
	}
	
	/**
	 * Add helpers for macro string reading
	 * Added in version v0.2.2
	 */
	
	/** Case-insensitive, index-safe search. regionMatches avoids the length changes toLowerCase
	 *  can introduce, so the returned index always aligns with the original string. 
	 *  Added in version v0.2.2
	 *  */
	private static int indexOfIgnoreCase(String haystack, String needle) {
		int n = needle.length(), max = haystack.length() - n;
		for (int i = 0; i <= max; i++) {
			if (haystack.regionMatches(true, i, needle, 0, n))
				return i;
		}
		return -1;
	}

	/**
	 * Value of `key=`, or null when absent. Replaces ij.Macro.getValue, which
	 * lowercases the key and so never finds one containing an uppercase letter --
	 * `channel-A=1` was unreadable. The KEY matches case-insensitively (channel-A,
	 * channel-a, CHANNEL-A all work); the VALUE is returned verbatim, so [each BB
	 * to closest cilium] keeps its capitals. Requiring a preceding space also stops
	 * a key matching inside a longer one -- Macro.getValue accepts a match preceded
	 * by '-', so getValue(o,"channel") returned 3 from "reconstruction-channel=3".
	 * 
	 * New in V0.2.2
	 */
	private String macroValue(String options, String key) {
		if (options == null || key == null)
			return null;
		String hay = " " + options;
		String needle = " " + key + "=";
		int i = indexOfIgnoreCase(hay, needle);
		if (i < 0)
			return null;
		String rest = hay.substring(i + needle.length());
		if (rest.startsWith("[")) {
			int end = rest.indexOf(']');
			return end < 0 ? rest.substring(1) : rest.substring(1, end);
		}
		int sp = rest.indexOf(' ');
		return sp < 0 ? rest : rest.substring(0, sp);
	}
	
	/**
	 * String macro option that returns fallback when absent or empty. Added in
	 * version v0.2.2
	 */
	private String macroString(String options, String key, String fallback) {
		String raw = macroValue(options, key);
		if (raw == null || raw.isEmpty()) {
			macroDefaults.put(key, fallback);
			return fallback;
		}
		return raw;
	}

	/**
	 * Numeric macro option. Returns fallback and warns if absent or unparseable.
	 * Added in version v0.2.2
	 */
	private double macroDouble(String options, String key, double fallback, boolean logDetection) {
		String raw = macroValue(options, key);
		if (raw == null || raw.isEmpty()) {
			if (logDetection)
				IJ.log("macro option '" + key + "' absent; using " + fallback);
			macroDefaults.put(key, plainNumber(fallback));
			return fallback;
		}
		try {
			return Double.parseDouble(raw.replace(",", "."));
		} catch (NumberFormatException e) {
			IJ.log("CiliaQ WARNING: macro option '" + key + "' is not a number ('" + raw + "'); using " + fallback
					+ ".");
			return fallback;
		}
	}

	/**
	 * Choice macro option, matched exactly against the allowed values. Added in
	 * version v0.2.2
	 */
	private String macroChoice(String options, String key, String[] choices, String fallback) {
		String raw = macroValue(options, key);
		if (raw == null || raw.isEmpty()) {
			macroDefaults.put(key, fallback);
			return fallback;
		}
		for (int i = 0; i < choices.length; i++) {
			if (choices[i].equals(raw))
				return choices[i];
		}
		String valid = "";
		for (int i = 0; i < choices.length; i++)
			valid += "\n" + choices[i];
		IJ.error("CiliaQ: macro option '" + key + "' value '" + raw + "' is not valid. Valid values are:" + valid);
		return fallback;
	}

	/**
	 * Boolean flag: present means true, absent means false. Matches whole tokens
	 * only.
	 * 
	 * Added in version v0.2.2
	 */
	private boolean macroFlag(String options, String key) {
		if (options == null)
			return false;
		return indexOfIgnoreCase(" " + options + " ", " " + key + " ") >= 0;
	}
	
	/**
	 * For a key read with an older name as its fallback: a default is noted only if neither is given, and
	 * under the key.
	 * 
	 * Added in version v0.2.2
	 */
	private void aliasDefault(String options, String key, String alias) {
		macroDefaults.remove(alias);
		String k = macroValue(options, key), a = macroValue(options, alias);
		if ((k != null && !k.isEmpty()) || (a != null && !a.isEmpty()))
			macroDefaults.remove(key);
	}

	/**
	 * A number as a macro value: whole numbers without decimals (10, not 10.0).
	 * 
	 * Added in version v0.2.2
	 **/
	private static String plainNumber(double v) {
		return v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : String.valueOf(v);
	}
}//end main class