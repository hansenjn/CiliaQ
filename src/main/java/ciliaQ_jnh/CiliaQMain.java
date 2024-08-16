package ciliaQ_jnh;
/** ===============================================================================
 * CiliaQ, a plugin for imagej - Version 0.1.8
 * 
 * Copyright (C) 2017-2023 Jan Niklas Hansen
 * First version: June 30, 2017  
 * This Version: August 2, 2024
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

import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.measure.*;
import ij.plugin.*;
import ij.plugin.frame.Recorder;
import ij.process.ImageConverter;
import ij.process.LUT;

public class CiliaQMain implements PlugIn, Measurements {
	//Name variables
	static final String PLUGINNAME = "CiliaQ";
	static final String PLUGINVERSION = "0.1.8";
	
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
		
	static final String[] nrFormats = {"US (0.00...)", "Germany (0,00...)"};
	static final String[] excludeOptions = {"nothing", "cilia touching x or y borders", "cilia touching x or y or z borders",
			"cilia touching x or y borders or whose thickest part is located at the z border"};
	
	static SimpleDateFormat NameDateFormatter = new SimpleDateFormat("yyMMdd_HHmmss");
	static SimpleDateFormat FullDateFormatter = new SimpleDateFormat("yyyy-MM-dd	HH:mm:ss");
	static SimpleDateFormat FullDateFormatter2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	static SimpleDateFormat YearOnly = new SimpleDateFormat("yyyy");
	
	//Macro-related settings
	boolean record = false;
    boolean showGUIs = true;
	
	//Progress Dialog
	ProgressDialog progress;	
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
		minRestSize = 1;
	boolean increaseRangeCilia = true,
			increaseRangeRegions = true;
	String excludeSelection = excludeOptions [2];
	boolean measureC2 = true,
			measureC3 = true,
			measureBasal = false;
	
	//Skeletons
	boolean skeletonize = true;
	double gXY = 2.0;
	double gZ = 0.0;
	double refDistance = 1.0;
	
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
    if (IJ.macroRunning() 
    		&& Macro.getOptions() != null
    		&& Macro.getOptions().length()>0) {
    	
    	readSettingsFromMacroString(Macro.getOptions(), true);
    			
        showDialog = false;
    }

    record = false;    
    if(showDialog && showGUIs) {
    	if(Recorder.record) {
    		/*
    		 * Deactivate recording tool, since it will not work based on how
    		 * the different settings are named in CiliaQ
    		 * It will be reactivated when all Generic Dialogs for setting
    		 * selection have passed.
    		 */
    		record = true;
    		Recorder.record = false;
    	}


    	//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
    	//-------------------------GenericDialog--------------------------------------
    	//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		
		GenericDialog gd = new GenericDialog(PLUGINNAME + " on " + System.getProperty("os.name") + " - set parameters");
		gd.addHelp("https://github.com/hansenjn/CiliaQ#user-guide--manual");
		//show Dialog-----------------------------------------------------------------
		//Note: .setInsets(top, left, bottom)
		gd.setInsets(0,0,0);	gd.addMessage(PLUGINNAME + ", Version " + PLUGINVERSION + ", \u00a9 2017 - 2023 JN Hansen", SuperHeadingFont);
		gd.setInsets(0,0,0);	gd.addMessage("More information at https://github.com/hansenjn/CiliaQ.", InstructionsFont);
		
		gd.setInsets(0,0,0);		gd.addMessage("This is a new version of CiliaQ (v0.1.7 from May 07, 2023). Length measurements were improved.", InstructionsFont, Color.MAGENTA);
		gd.setInsets(-5,0,0);	gd.addMessage("Do not mix results from this version with results from previous versions.", InstructionsFont, Color.MAGENTA);
		gd.setInsets(-5,0,0);	gd.addMessage("Find out more at the release notes (https://github.com/hansenjn/CiliaQ/releases/tag/v0.1.7).", InstructionsFont, Color.MAGENTA);
			
		
		gd.setInsets(10,0,0);	gd.addChoice("process ", taskVariant, selectedTaskVariant);
		
//    	gd.setInsets(10,0,0);	gd.addMessage("Calibration", HeadingFont);
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
		
    	//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
    	//---------------------end-GenericDialog-end----------------------------------
    	//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
    		
		// LOAD SETTINGS FOR DETECTION
		if(selectedSettingsVariant.equals(settingsMethod [0])){
			if(!enterSettings()) {
				return;
			}
		}else if(!importSettings()) {
			IJ.error("Preferences were not completely loaded ...");
			return;
		}
    	
    	if(record) {
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
    	Recorder.recordString("run(\"" + PLUGINNAME + " (" + PLUGINVERSION + ")\",\"" + recordString + "\");\n");    	
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
			
			od.addWindowListener(new java.awt.event.WindowAdapter() {
		        public void windowClosing(WindowEvent winEvt) {
		        	return;
		        }
		    });
		
			//Waiting for od to be done
			while(od.done==false){
				try{
					Thread.currentThread().sleep(50);
			    }catch(Exception e){
			    }
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
	 	
	//add progressDialog
	if(showGUIs) {
		progress = new ProgressDialog(name, tasks);
		progress.setLocation(0,0);
		progress.setVisible(true);
		progress.addWindowListener(new java.awt.event.WindowAdapter() {
	        public void windowClosing(WindowEvent winEvt) {
	        	if (record) {	
	        		Recorder.record = true;
	        	}
	        	if(processingDone==false){
	        		IJ.error("Script stopped...");
	        	}
	        	continueProcessing = false;	        	
	        	return;
	        }
		});
	}

//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
//------------------------------CELL MEASUREMENT------------------------------
//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
	for(int task = 0; task < tasks; task++){
	running: while(continueProcessing){
		Date startDate = new Date();
		if(showGUIs)	progress.updateBarText("in progress...");
				
		//Check for problems
				if(name[task].substring(name[task].lastIndexOf("."),name[task].length()).equals(".txt")){
					if(showGUIs) {
						progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": File is no image! Could not be processed!", ProgressDialog.ERROR);
						progress.moveTask(task);
					}else {
						System.out.println("File " + name[task] + " is no image! Could not be processed!");
					}	
					break running;
				}
				if(name[task].substring(name[task].lastIndexOf("."),name[task].length()).equals(".zip")){	
					if(showGUIs) {
						progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": File is no image! Could not be processed!", ProgressDialog.ERROR);
						progress.moveTask(task);
					}else {
						System.out.println("File " + name[task] + " is no image! Could not be processed!");
					}
					break running;
				}		
		//Check for problems
				
		//open Image
		   	ImagePlus imp;
		   	try{
		   		if(selectedTaskVariant.equals(taskVariant[1])){
		   			imp = IJ.openImage(""+dir[task]+name[task]+"");			   			
					imp.deleteRoi();
		   		}else if(selectedTaskVariant.equals(taskVariant[0])){
		   			imp = WindowManager.getCurrentImage().duplicate();
		   			imp.deleteRoi();
		   		}else{
		   			imp = allImps[task].duplicate();
		   			imp.deleteRoi();
		   		}
		   	}catch (Exception e) {
		   		if(showGUIs){
			   		progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": file is no image - could not be processed!", ProgressDialog.ERROR);
					progress.moveTask(task);		   			
		   		}else {
		   			System.out.println("File " + name[task] + " is no image - could not be processed!");
		   		}
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
			if(recalibrate){
				pixelWidth = calibration;		imp.getCalibration().pixelWidth = pixelWidth;
				pixelHeight = calibration;		imp.getCalibration().pixelHeight = pixelHeight;
				pixelDepth = voxelDepth;		imp.getCalibration().pixelDepth = pixelDepth;
				unit = calibrationDimension;	imp.getCalibration().setUnit(unit);
				frameInterval = timePerFrame;	imp.getCalibration().frameInterval = frameInterval;
				calTimeUnit = timeUnit; 		imp.getCalibration().setTimeUnit(calTimeUnit);
				if(timeUnit.equals(timeFormats[0])){
					fps = 1.0 / (double)timePerFrame;
					imp.getCalibration().fps = fps;
				}else if(timeUnit.equals(timeFormats[1])){
					fps = 1.0 / (60*(double)timePerFrame);
					imp.getCalibration().fps = fps;
				}else if(timeUnit.equals(timeFormats[2])){
					fps = 1.0 / (60*60*(double)timePerFrame);
					imp.getCalibration().fps = fps;
				}else{
					fps = 1.0 / (24*60*60*(double)timePerFrame);
					imp.getCalibration().fps = fps;
				}
			}else{				
				calibration = pixelWidth;
				calibrationDimension = ""+unit;
				voxelDepth = pixelDepth;
				if(pixelWidth!=pixelHeight){
					if(showGUIs){
						progress.notifyMessage("Task " + (task+1) + "/" + tasks 
								+ ": x and y calibration in metadata differ - used only x metadata calibration for both, x and y!", ProgressDialog.NOTIFICATION);	
					}
				}
				timePerFrame = frameInterval;
				if(timePerFrame==0.0){timePerFrame=1.0;}
				imp.getCalibration().frameInterval = timePerFrame;
				if(fps==0.0){fps = 1.0;}
				imp.getCalibration().fps = fps;
				timeUnit = calTimeUnit;
			}
		//Calibrate
			
		//check for correctness
			if(channelReconstruction>imp.getNChannels()){
				if(showGUIs) {
					progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": reconstruction channel does not exist - analysis cancelled!", ProgressDialog.ERROR);
					progress.moveTask(task);
				}else {
					IJ.error("Image " + name[task] + ": reconstruction channel does not exist - analysis cancelled!");
				}
				break running;
			}
			boolean measureC2local = measureC2;
			if(measureC2 && channelC2>imp.getNChannels()){
				measureC2local = false;
				if(showGUIs) {
					progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": channel A does not exist - skipped channel A parameters!", ProgressDialog.NOTIFICATION);
				}else {
					System.out.println("Image " + name[task] + ": channel A does not exist - skipped channel A parameters!");
				}
			}
			boolean measureC3local = measureC3;
			if(measureC3 && channelC3>imp.getNChannels()){
				measureC3local = false;
				if(showGUIs) {
					progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": channel B does not exist - skipped channel B parameters!", ProgressDialog.NOTIFICATION);
				}else {
					System.out.println("Image " + name[task] + ": channel B does not exist - skipped channel A parameters!");
				}
			}
			boolean measureBasalLocal = measureBasal;
			if(measureBasalLocal && basalStainC>imp.getNChannels()){
				measureBasalLocal = false;
				if(showGUIs) {
					progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": basal channel does not exist - skiped basal channel-based processing!", ProgressDialog.NOTIFICATION);
				}else {
					System.out.println("Image " + name[task] + ": basal channel  does not exist - skipped basal channel-based processing!");
				}
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
			try{
				new File(dir [task] + filePrefix).mkdirs();
			}catch(Exception e){
				if(showGUIs) {
					progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": Failed to create subfolder to save additional plots! Will save plots into origianl folder!",ProgressDialog.NOTIFICATION);
				}else {
					System.out.println("Image " + name[task] + ": Failed to create subfolder to save additional plots! Will save plots into origianl folder!");
				}				
			}
			filePrefix = dir[task] + filePrefix;
		//Define Output File Names
			
		//Check whether exclude selection matches and customize if needed	
			String tempExcludeSelection = excludeSelection;
			if(imp.getNSlices() == 1 && (excludeSelection.equals(excludeOptions [2]) || excludeSelection.equals(excludeOptions [3]))){
				tempExcludeSelection = excludeOptions [1];
				if(showGUIs) {
					progress.notifyMessage("Task " + (task+1) + "/" + tasks + ": This is a 2D image - setting '"
							+ excludeSelection
							+ "' was switched to '"
							+ tempExcludeSelection
							+ "' for this image, to ensure that cilia can be detected.", ProgressDialog.NOTIFICATION);
				}else {
					System.out.println("Image " + name[task] + ": This is a 2D image - setting '"
							+ excludeSelection
							+ "' was switched to '"
							+ tempExcludeSelection
							+ "' for this image, to ensure that cilia can be detected.");
				}
			}
			
		//Get cilia data and save them
			if(imp.getNFrames()!=1){
				//Timelapse Mode
				if(showGUIs) {
					progress.notifyMessage("Timelapse workflow started...", ProgressDialog.LOG);
				}
				this.analyzeCiliaIn4DAndSaveResults(imp, measureC2local, measureC3local, measureBasalLocal, name[task], dir[task], startDate, filePrefix, subfolderPrefix, tempExcludeSelection);
			}else{
				//Single-frame Mode
				if(showGUIs) {
					progress.notifyMessage("Single-timepoint workflow started...", ProgressDialog.LOG);
				}				
				this.analyzeCiliaIn3DAndSaveResults(imp, measureC2local, measureC3local, measureBasalLocal, name[task], dir[task], startDate, filePrefix, subfolderPrefix, tempExcludeSelection);
			}			
		//Get cilia data and save them
	processingDone = true;
	break running;
	}	
	System.gc();
	if(showGUIs) {
		progress.updateBarText("finished!");
		progress.setBar(1.0);
		progress.moveTask(task);		
	}
}
}

/**
 * A function that sets all settings based on an option String
 * given when calling this plugin from a Macro.
 * If commands are missing, parameters will be left at default.
 * @param macroOptions: String containing commands for setting up the settings
 * @param logDetection: If true, logs what was detected.
 */
private void readSettingsFromMacroString(String macroOptions, boolean logDetection) {
	if(logDetection) IJ.log("Input macro Options: " + macroOptions);

	String temp;
	
	//In macro mode CiliaQ by default will run on the front-most opened image.
	selectedTaskVariant = taskVariant[0];
	
	temp = "";
	if(macroOptions.contains("re-calibrate") || macroOptions.contains("recalibrate")) {
		recalibrate = true;
		
		temp = "";
		if(macroOptions.contains("length=")){
			temp = macroOptions.substring(macroOptions.indexOf("length="));
    		temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
    		calibration = Double.parseDouble(temp);
    		if(logDetection) IJ.log("detected calibration: " + calibration);
		}else if(macroOptions.contains("length-calibration=")){
			temp = macroOptions.substring(macroOptions.indexOf("length-calibration=")); 
			temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
			calibration = Double.parseDouble(temp);
    		if(logDetection) IJ.log("detected calibration: " + calibration);
		}
		
		if(macroOptions.contains("depth=")){
			temp = macroOptions.substring(macroOptions.indexOf("depth="));
    		temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
    		voxelDepth = Double.parseDouble(temp);
    		if(logDetection) IJ.log("detected voxelDepth: " + voxelDepth);
		}else if(macroOptions.contains("depth-calibration=")){
			temp = macroOptions.substring(macroOptions.indexOf("depth-calibration=")); 
			temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
			voxelDepth = Double.parseDouble(temp);
    		if(logDetection) IJ.log("detected voxelDepth: " + voxelDepth);
		}
		
		if(macroOptions.contains("alibration-unit=")){
			temp = macroOptions.substring(macroOptions.indexOf("alibration-unit="));
    		temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
    		calibrationDimension = temp;
    		if(logDetection) IJ.log("detected calibrationDimensions: " + calibrationDimension);
		}
		
		if(macroOptions.contains("ime-interval=")){
			temp = macroOptions.substring(macroOptions.indexOf("ime-interval="));
    		temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
    		timePerFrame = Double.parseDouble(temp);
    		if(timePerFrame==0.0){
        		timePerFrame=1.0;
        	}
    		if(logDetection) IJ.log("detected timePerFrame: " + timePerFrame);
		}
		
		if(macroOptions.contains("ime-unit=")){
			temp = macroOptions.substring(macroOptions.indexOf("ime-unit="));
    		temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
    		timeUnit = temp;
    		if(logDetection) IJ.log("detected timeUnit: " + timeUnit);
		}
	}else {
		recalibrate = false;
	}

	if(macroOptions.contains("include-date")){
		saveDate = true;
		if(logDetection) IJ.log("detected saveDate: " + saveDate);
	}else {
		saveDate = false;
	}
	
	if(macroOptions.contains("save-single-cilia-result-images")){
		saveSingleCiliaTifs = true;
		if(logDetection) IJ.log("detected save-single-cilia-result-images: " + saveSingleCiliaTifs);
	}else {
		saveSingleCiliaTifs = false;
	}

	if(macroOptions.contains("save-3D-individual-cilia")){
		saveSingleCilia3DImages = true;
		if(logDetection) IJ.log("detected saveSingleCilia3DImages: " + saveSingleCilia3DImages);
	}else {
		saveSingleCilia3DImages = false;
	}

	if(macroOptions.contains("save-3D-whole-image")){
		saveOverview3DImages = true;
		if(logDetection) IJ.log("detected saveOverview3DImages: " + saveOverview3DImages);
	}else {
		saveOverview3DImages = false;
	}
	

	if(macroOptions.contains("number-format=")){
		temp = macroOptions.substring(macroOptions.indexOf("number-format="));
		temp = temp.substring(temp.indexOf("=")+1);
		temp = temp.substring(0,temp.indexOf(" "));
		if(temp.toLowerCase().contains("us")) {
			ChosenNumberFormat = nrFormats[0];
		}else {
			ChosenNumberFormat = nrFormats[1];				
		}
		if(logDetection) IJ.log("detected numberFormat: " + ChosenNumberFormat);
	}
	if(ChosenNumberFormat.equals(nrFormats[0])){ //US-Format
		dformat6.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
		dformat3.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
		dformat0.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
	}else if (ChosenNumberFormat.equals(nrFormats[1])){
		dformat6.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.GERMANY));
		dformat3.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.GERMANY));
		dformat0.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.GERMANY));
	}
	

	if(macroOptions.contains("reconstruction-channel=")){
		temp = macroOptions.substring(macroOptions.indexOf("reconstruction-channel="));
		temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
		channelReconstruction = Integer.parseInt(temp);
		if(logDetection) IJ.log("detected reconstruction-channel: " + channelReconstruction);
	}else {
		IJ.error("Macro options missed definition 'reconstruction-channel=<an Integer Number>'");
	}

	if(macroOptions.contains("measure-intensity-A")){
		measureC2 = true;
		if(logDetection) IJ.log("detected measure intensity A: " + measureC2);
		
		if(macroOptions.contains("channel-A=")){
			temp = macroOptions.substring(macroOptions.indexOf("channel-A="));
			temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
			channelC2 = Integer.parseInt(temp);
			if(logDetection) IJ.log("detected channel-A: " + channelC2);
		}else {
			IJ.error("Macro options missed definition 'channel-A=<an Integer Number>'");
		}
	}else {
		measureC2 = false;
	}

	if(macroOptions.contains("measure-intensity-B")){
		measureC3 = true;
		if(logDetection) IJ.log("detected measure intensity B: " + measureC3);
		
		if(macroOptions.contains("channel-B=")){
			temp = macroOptions.substring(macroOptions.indexOf("channel-B="));
			temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
			channelC3 = Integer.parseInt(temp);
			if(logDetection) IJ.log("detected channel-B: " + channelC3);
		}else {
			IJ.error("Macro options missed definition 'channel-B=<an Integer Number>'");
		}
	}else {
		measureC3 = false;
	}

	if(macroOptions.contains("basal-stain-present")){
		measureBasal = true;
		if(logDetection) IJ.log("detected basal stain present: " + measureBasal);
		
		if(macroOptions.contains("channel-basal-stain=")){
			temp = macroOptions.substring(macroOptions.indexOf("channel-basal-stain="));
			temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
			basalStainC = Integer.parseInt(temp);
			if(logDetection) IJ.log("detected channel-basal-stain: " + basalStainC);
		}else {
			IJ.error("Macro options missed definition 'channel-basal-stain=<an Integer Number>'");
		}
	}else {
		measureBasal = false;
	}

	if(macroOptions.contains("minimum-cilium-size=")){
		temp = macroOptions.substring(macroOptions.indexOf("minimum-cilium-size="));
		temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
		minSize = Integer.parseInt(temp);
		if(logDetection) IJ.log("detected minimum-cilium-size: " + minSize);
	}else {
		IJ.error("Macro options missed definition 'minimum-cilium-size=<an Integer Number>'");
	}
	
	if(macroOptions.contains("increase-range-cilia")){
		increaseRangeCilia = true;
		if(logDetection) IJ.log("detected increase-range-cilia: " + increaseRangeCilia);
	}else {
		increaseRangeCilia = false;
	}

	if(macroOptions.contains("exclude=")){
		temp = macroOptions.substring(macroOptions.indexOf("exclude="));
		temp = temp.substring(temp.indexOf("=")+1);
		if(temp.startsWith("[") || temp.startsWith("//[")) {
			if(temp.contains("]")) {
				temp = temp.substring(1,temp.indexOf("]"));
			}else {
				temp = temp.substring(1,temp.indexOf("//]"));
			}			    				
		}else {
			temp = temp.substring(0,temp.indexOf(" "));
		}    			
		excludeSelection = temp;
		
		//Validate excludeSelection
		for(int i = 0; i < excludeOptions.length; i++) {
			if(excludeSelection.equals(excludeOptions [i])) {				
				break;
			}
			if(i == excludeOptions.length-1) {
				String temp2 = "";
				for(int j = 0; j < excludeOptions.length; j++) {
					temp2 += "\n";
					temp2 += excludeOptions[j];
				}
				IJ.error("Macro options did not contain a valid definition for 'exclude='.\n"
						+ "Valid definitions are: " + temp2);
			}
		}
		
		if(logDetection) IJ.log("detected exclude selection: " + excludeSelection);
	}else {
		IJ.error("Macro options missed definition 'exclude=<...>'");
	}

	if(macroOptions.contains("minimum-size-intensity=")){
		temp = macroOptions.substring(macroOptions.indexOf("minimum-size-intensity="));
		temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
		minRestSize = Integer.parseInt(temp);
		if(logDetection) IJ.log("detected minimum-size-intensity: " + minRestSize);
	}else {
		IJ.error("Macro options missed definition 'minimum-size-intensity=<an Integer Number>'");
	}
	
	if(macroOptions.contains("increase-range-intensity")){
		increaseRangeRegions = true;
		if(logDetection) IJ.log("detected increase-range-intensity: " + increaseRangeRegions);
	}else {
		increaseRangeRegions = false;
	}


	if(macroOptions.contains("skeleton")){
		skeletonize = true;
		if(logDetection) IJ.log("detected skeletonize: " + skeletonize);
	}else {
		skeletonize = false;
	}
	

	if(macroOptions.contains("gauss-xy=")){
		temp = macroOptions.substring(macroOptions.indexOf("gauss-xy="));
		temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
		gXY = Double.parseDouble(temp);
		if(logDetection) IJ.log("detected gSigmaXY: " + gXY);
	}else if(skeletonize) {
		IJ.error("Macro options missed definition gauss-xy=<a number>'");
	}
	
	if(macroOptions.contains("gauss-z=")){
		temp = macroOptions.substring(macroOptions.indexOf("gauss-z="));
		temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
		gZ = Double.parseDouble(temp);
		if(logDetection) IJ.log("detected gSigmaZ: " + gZ);
	}else if(skeletonize) {
		IJ.error("Macro options missed definition gauss-z=<a number>'");
	}
	
	if(macroOptions.contains("distance-curvature=")){
		temp = macroOptions.substring(macroOptions.indexOf("distance-curvature="));
		temp = temp.substring(temp.indexOf("=")+1,temp.indexOf(" "));
		refDistance = Double.parseDouble(temp);
		if(logDetection) IJ.log("detected reference distance for tangets and curvature: " + refDistance);
	}else {
		IJ.error("Macro options missed definition distance-curvature=<a number>'");
	}	

	if(macroOptions.contains("showgui") || macroOptions.contains("showGUI") || macroOptions.contains("SHOWGUI")){
		showGUIs = true;
		if(logDetection) IJ.log("detected noGUIs: " + showGUIs);
	}else {
		showGUIs = false;
		if(logDetection) IJ.log("detected noGUIs: " + showGUIs);
	}
}

/**
 * A function to create
 * @return an option String
 * based on the settings applied. This option String will be usable
 * to call this plugin under the same options as when recording it
 * from a Macro
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
	
	if(saveDate) {
    	recordString += "include-date ";    		
	}	
	if(saveSingleCiliaTifs) {
    	recordString += "save-single-cilia-result-images ";    		
	}	
	if(saveSingleCilia3DImages) {
    	recordString += "save-3D-individual-cilia ";    		
	}	
	if(saveOverview3DImages) {
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
	if(measureBasal) {
		recordString += "basal-stain-present ";
		recordString += "channel-basal-stain=" + basalStainC+ " ";  
	}
	
	recordString += "minimum-cilium-size=" + minSize + " ";
	if(increaseRangeCilia) {
    	recordString += "increase-range-cilia ";    		
	}
	
	recordString += "exclude=[" + excludeSelection + "] ";
	
	recordString += "minimum-size-intensity=" + minRestSize + " ";
	if(increaseRangeRegions) {
    	recordString += "increase-range-intensity ";    		
	}
	
	if(skeletonize) {
		recordString += "skeleton ";			
	}
	recordString += "gauss-xy=" + dfDialog.format(gXY) + " ";
	recordString += "gauss-z=" + dfDialog.format(gZ) + " ";
	
	recordString += "distance-curvature=" + dfDialog.format(refDistance) + " ";
	
	recordString = recordString.substring(0,recordString.length()-1);	
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
		int excludedCilia, int totalCilia){
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
	if(measureBasalLocal){tp.append("		basal stain	" + dformat0.format(basalStainC));}else{tp.append("");}
	tp.append("	Minimum cilium size	" + dformat0.format(minSize) + "	Increase range for connecting cilia	" + increaseRangeCilia);
	tp.append("	Additional filtering	" + excludeSelection + " excluded	# excluded:	" + excludedCilia + "	of total #:	" + totalCilia);
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
	tp.append("");
}

int getMaxIntensity(ImagePlus imp, int task, int tasks){
	int maxThreshold = 0;
	if(imp.getBitDepth()==8){
		maxThreshold = 255;
	}else if(imp.getBitDepth()==16){
		maxThreshold = 65535;
	}else if(imp.getBitDepth()==32){
		maxThreshold = 2147483647;
	}else{
		if(showGUIs){
			progress.notifyMessage("Task " + task + "/" + tasks + ": Error! No gray scale image!",ProgressDialog.ERROR);
		}else {
			IJ.error("Error! No gray scale image!");
		}
	}
	return maxThreshold;	
}

/**
 * Filter the indicated channel of the image and remove particles below the @param minSize threshold.
 * @param imp: Hyperstack image where one channel represents the )recording of the volume of interest
 * @param c: defines the channel of the Hyperstack image imp, in which the information for the volume of interest is stored 1 < c < number of channels
 * @param particleLabel: the label for the volume of interest which is displayed in the progress dialog while obtaining object information
 * @param increaseRange: defines whether also diagonal pixels should be allowed while Flood Filling
 * */
void filterChannel(ImagePlus imp, int c, String particleLabel, int minSize, boolean increaseRange){	
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
					if(imp.getStack().getVoxel(x, y, imp.getStackIndex(c, z+1, 1)-1) > 0.0){
						preliminaryParticle = new ArrayList<PartPoint>(nrOfPoints-floodFilledPc);
						preliminaryParticle.add(new PartPoint(x, y, z, t, refImp, c));
						
						imp.getStack().setVoxel(x, y, imp.getStackIndex(c, z+1, 1)-1, 0.0);
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
									&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
								
								preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX-1;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ;
							}
							if ((floodNodeX < (imp.getWidth()-1)) 
									&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
								
								preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX+1;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ;
							}
							if ((floodNodeY > 0) 
									&& imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
								
								preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY-1,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY-1;
								floodNodes[index][2] = floodNodeZ;
							}                
							if ((floodNodeY < (imp.getHeight()-1)) 
									&& imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
								
								preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY+1,floodNodeZ,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY+1;
								floodNodes[index][2] = floodNodeZ;
							}
							if ((floodNodeZ > 0) 
									&& imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
								
								preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY,floodNodeZ-1,floodNodeT, refImp, c));
								imp.getStack().setVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
								
								index++;
								floodFilledPc++;
								
								floodNodes[index][0] = floodNodeX;
								floodNodes[index][1] = floodNodeY;
								floodNodes[index][2] = floodNodeZ-1;
							}                
							if ((floodNodeZ < (imp.getNSlices()-1)) 
									&& imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
								
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
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY-1,floodNodeZ,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY < (imp.getHeight()-1))
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY+1,floodNodeZ,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY > 0) 
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY-1,floodNodeZ,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ;
								}                
								if ((floodNodeX > 0) && (floodNodeY < (imp.getHeight()-1)) 
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ-1;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeZ > 0)
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ-1;
								}
								if ((floodNodeX > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
									preliminaryParticle.add(new PartPoint(floodNodeX-1,floodNodeY,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX-1;
									floodNodes[index][1] = floodNodeY;
									floodNodes[index][2] = floodNodeZ+1;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
									preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY-1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ-1;
								}
								if ((floodNodeY < (imp.getHeight()-1)) && (floodNodeZ > 0)
										&& imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
									preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY+1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ-1;
								}
								if ((floodNodeY > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
									preliminaryParticle.add(new PartPoint(floodNodeX,floodNodeY-1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ+1;
								} 
								if ((floodNodeY < (imp.getHeight()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
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
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY+1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ-1;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY > 0) && (floodNodeZ > 0)
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY-1,floodNodeZ-1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ-1;
								}                
								if ((floodNodeX > 0) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ > 0)
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
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
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY+1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY+1;
									floodNodes[index][2] = floodNodeZ+1;
								}
								if ((floodNodeX < (imp.getWidth()-1)) && (floodNodeY > 0) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
									preliminaryParticle.add(new PartPoint(floodNodeX+1,floodNodeY-1,floodNodeZ+1,floodNodeT, refImp, c));
									imp.getStack().setVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1, 0.0);
									
									index++;
									floodFilledPc++;
									
									floodNodes[index][0] = floodNodeX+1;
									floodNodes[index][1] = floodNodeY-1;
									floodNodes[index][2] = floodNodeZ+1;
								}                
								if ((floodNodeX > 0) && (floodNodeY < (imp.getHeight()-1)) && (floodNodeZ < (imp.getNSlices()-1)) 
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
							if(showGUIs) {
								progress.updateBarText("Connecting " + particleLabel + " complete: " + dformat3.format(((double)(floodFilledPc)/(double)(nrOfPoints))*100) + "%");
								progress.addToBar(0.2*((double)(floodFilledPc-floodFilledPcOld)/(double)(nrOfPoints)));								
							}
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
	if(showGUIs) {
		progress.updateBarText("Connecting " + particleLabel + " complete: " + dformat3.format(((double)(floodFilledPc)/(double)(nrOfPoints))*100) + "%");
		progress.addToBar(0.2*((double)(floodFilledPc-floodFilledPcOld)/(double)(nrOfPoints)));
	}	
	particles.trimToSize();
	
	refImp.changes = false;
	refImp.close();
	
	//write back to image
	{
		for(int j = 0; j < particles.size();j++){
			for(int i = 0; i < particles.get(j).size();i++){
				imp.getStack().setVoxel(particles.get(j).get(i).x,
						particles.get(j).get(i).y, 
						imp.getStackIndex(c, particles.get(j).get(i).z+1, 1)-1, 
						particles.get(j).get(i).intensity);
			}
		}
	}
}

/**
 * @deprecated since 23.04.2019
 * @return a container that contains lists, which each contain the points belonging to an individual plaque object
 * @param imp: Hyperstack image where one channel represents the recording of plaques
 * @param c: defines the channel of the Hyperstack image imp, in which the ciliary information is stored 1 < c < number of channels
 * */
ArrayList<ArrayList<CellPoint>> getCiliaObjects (ImagePlus imp, int c){
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
//					touchesXY = false;
//					touchesZ = false;
					
					preliminaryParticle = new ArrayList<CellPoint>(nrOfPoints-floodFilledPc);
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
								&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, 1)-1) > 0.0){
							
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
								&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, 1)-1) > 0.0){
							
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
								&& imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, 1)-1) > 0.0){
							
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
								&& imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, 1)-1) > 0.0){
							
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
								&& imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, 1)-1) > 0.0){
							
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
								&& imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, 1)-1) > 0.0){
							
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
						if(showGUIs) {
							progress.updateBarText("Reconstruction of ciliary structures complete: " + dformat3.format(((double)(floodFilledPc)/(double)(nrOfPoints))*100) + "%");
							progress.addToBar(0.2*((double)(floodFilledPc-floodFilledPcOld)/(double)(nrOfPoints)));							
						}
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
	
	if(showGUIs) {
		progress.updateBarText("Reconstruction of ciliary structures complete: " + dformat3.format(((double)(floodFilledPc)/(double)(nrOfPoints))*100) + "%");
		progress.addToBar(0.2*((double)(floodFilledPc-floodFilledPcOld)/(double)(nrOfPoints)));
	}
	
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

/**
 * used since 23.04.2019
 * @return a container that contains lists, which each contain the points belonging to an individual plaque object
 * @param imp: Hyperstack image where one channel represents the recording of plaques
 * @param c: defines the channel of the Hyperstack image imp, in which the ciliary information is stored 1 < c < number of channels
 * @param increaseRange: defines whether also diagonal pixels should be allowed while Flood Filling
 * */
ArrayList<ArrayList<CellPoint>> getCiliaObjectsTimelapse (ImagePlus imp, int c, boolean increaseRange){
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
		if(showGUIs) {
			progress.notifyMessageWithTaskNr("ERROR - The channel set for reconstruction is not segmented - make sure to set the correct channel nr for reconstruction in the preferences!", ProgressDialog.ERROR);
		}else {
			System.out.println("ERROR - The channel set for reconstruction is not segmented - make sure to set the correct channel nr for reconstruction in the preferences!");
		}
		return new ArrayList<ArrayList<CellPoint>>(0);
	}else if(nrOfPoints == 0) {
		if(showGUIs) {
			progress.notifyMessageWithTaskNr("ERROR - The channel for reconstruction is empty - no cilia found!", ProgressDialog.ERROR);
		}else {
			System.out.println("ERROR - The channel for reconstruction is empty - no cilia found!");
		}
		return new ArrayList<ArrayList<CellPoint>>(0);
	}
		
	ArrayList<ArrayList<CellPoint>> particles = new ArrayList<ArrayList<CellPoint>>((int)Math.round((double)nrOfPoints/(double)minSize));
	
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
//						touchesXY = false;
//						touchesZ = false;
						
						preliminaryParticle = new ArrayList<CellPoint>(nrOfPoints-floodFilledPc);
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
									&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
								
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
									&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
								
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
									&& imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
								
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
									&& imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
								
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
									&& imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
								
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
									&& imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
								
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
							if ((floodNodeT > 0) 
									&& imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, floodNodeZ+1, (floodNodeT-1)+1)-1) > 0.0){
								
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
									&& imp.getStack().getVoxel(floodNodeX, floodNodeY, imp.getStackIndex(c, floodNodeZ+1, ((floodNodeT)+1)+1)-1) > 0.0){
								
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
							if(increaseRange){
								// X, Y
								if ((floodNodeX > 0) && (floodNodeY > 0)  
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
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
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
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
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ-1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
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
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX+1, floodNodeY-1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
										&& imp.getStack().getVoxel(floodNodeX-1, floodNodeY+1, imp.getStackIndex(c, (floodNodeZ+1)+1, (floodNodeT)+1)-1) > 0.0){
									
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
//								&& preliminaryParticle.size() >= minPlaqueSize 
//								&& preliminaryParticle.size() <= maxPlaqueSize){
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
						if(keep){
							particles.add(preliminaryParticle);							
						}else{
							preliminaryParticle.clear();
							preliminaryParticle.trimToSize();
						}

						if(floodFilledPc%(pc100)<pc1000){
							if(showGUIs) {
								progress.updateBarText("Reconstruction of ciliary structures complete: " + dformat3.format(((double)(floodFilledPc)/(double)(nrOfPoints))*100) + "%");
								progress.addToBar(0.2*((double)(floodFilledPc-floodFilledPcOld)/(double)(nrOfPoints)));								
							}
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
				
	refImp.changes = false;
	refImp.close();
	
	if(showGUIs) {
		progress.updateBarText("Reconstruction of ciliary structures complete: " + dformat3.format(((double)(floodFilledPc)/(double)(nrOfPoints))*100) + "%");
		progress.addToBar(0.2*((double)(floodFilledPc-floodFilledPcOld)/(double)(nrOfPoints)));
	}
	particles.trimToSize();
	
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
	//write back to image
	return particles;
}//end getCiliaObjects

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
	imp.setDisplayRange(0, 4095);
	
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
	{
		//Retrieving ciliary objects from the image
		ArrayList<ArrayList<CellPoint>> ciliaParticles = getCiliaObjectsTimelapse(imp, channelReconstruction, increaseRangeCilia);	//Method changed on 23.04.2019
		if(ciliaParticles.size() == 0) {
			return;
		}
		if(showGUIs) {
			progress.updateBarText("Structure reconstruction completed.");
		}
		//TODO implement method to move on if no cells detected
		
		//Determine intensity thresholds for each other channel
		if(showGUIs) {
			progress.updateBarText("Determining intensity thresholds ...");
		}
		intensityThresholds = getIntensityThresholds(imp, ciliaParticles);
		
		
		boolean touchesXY, touchesZ;
		int [] sliceCounter = new int [imp.getNSlices()];
		
		cilia.ensureCapacity(ciliaParticles.size());
		for(int i = 0; i < ciliaParticles.size(); i++){
			if(showGUIs) {
				progress.updateBarText("Quantifying cilia objects (" + i + "/" + ciliaParticles.size() + " done)");
			}
			cilia.add(new Cilium(ciliaParticles.get(i), imp, measureC2local, channelC2, measureC3local, channelC3, measureBasalLocal, basalStainC, 
					channelReconstruction, gXY, gZ, intensityThresholds, progress, skeletonize));
			if(cilia.size()!=i+1) {
				if(showGUIs) {
					progress.notifyMessage("Error while measuring cilium " + (i+1), ProgressDialog.NOTIFICATION);
				}
			}
			
			//Exclude cilia if selected
			if(!tempExcludeSelection.equals(excludeOptions[0])){
				if(showGUIs) {
					progress.updateBarText("Quantifying cilia objects (" + i + "/" + ciliaParticles.size() + " done): checking xyz borders...");
				}
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
								
			if(showGUIs) {
				progress.addToBar(0.2/ciliaParticles.size());
				progress.updateBarText("reconstructing cilia... (" + (i+1) + "/" + ciliaParticles.size() + ")");
			}			
		}
		
		ciliaParticles.clear();
		ciliaParticles.trimToSize();
	}		
	if(showGUIs) {
		progress.updateBarText("" + cilia.size() + " cilia reconstructed!");
	}
	

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
		//save plaque-specific images
		for(int i = 0; i < cilia.size(); i++){
			if(cilia.get(i).excluded) continue;
			if(showGUIs) {
				progress.updateBarText("Writing single cilia images (" + i + "/" + cilia.size() + " done)");
			}
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
				if(showGUIs) {
					progress.notifyMessage("IO ERROR when saving file " + (subfolderPrefix + "_P" + dformat0.format(i+1) + "_ci.txt"), ProgressDialog.ERROR);
				}else {
					System.out.println("IO ERROR when saving file " + (subfolderPrefix + "_P" + dformat0.format(i+1) + "_ci.txt"));
				}
			}
			
			ImagePlus impC = cilia.get(i).getCiliumImp(intensityThresholds, channelC2, channelC3);
			IJ.saveAsTiff(impC, subfolderPrefix + "_C" + dformat0.format(i+1) + ".tif"); 
			impC.changes = false;
			impC.close();					
		}					
	}
	
	if(showGUIs) {
		progress.updateBarText("saving results...");
		progress.setBar(0.8);		
	}

	//Output images
			
	//Save Textfile--------------------------------------	
	OutputTextFile tw1 = new OutputTextFile("");
	OutputTextFile tw2 = new OutputTextFile("");
	
	addSettingsBlockToPanel(tw1, currentDate, startDate, name, imp, 
			measureC2local, measureC3local, measureBasalLocal, intensityThresholds, excludedCilia, cilia.size());
	
	tw1.append("Results:");				
	String appendTxt = "	";	
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
	appendTxt += "	" + "Maximum span ["+calibrationDimension+"]";	//TODO - method to be implemented
	
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
	
			
	if(skeletonize){
		//profiles
		if(measureC2local){
			appendTxt += "	" + "Profile A (arc length step: " + dformat6.format(calibration) + ")";
			for(int i = 0; i < nrOfProfileCols-1; i++){
				appendTxt += "	";
			}
			appendTxt += "	" + "Profile A (normalized to reconstruction channel) (arc length step: " + dformat6.format(calibration) + ")";
			for(int i = 0; i < nrOfProfileCols-1; i++){
				appendTxt += "	";
			}
		}
			
		if(measureC3local){
			appendTxt += "	" + "Profile B (arc length step: " + dformat6.format(calibration) + ")";
			for(int i = 0; i < nrOfProfileCols-1; i++){
				appendTxt += "	";
			}
			appendTxt += "	" + "Profile B (normalized to reconstruction channel) (arc length step: " + dformat6.format(calibration) + ")";
			for(int i = 0; i < nrOfProfileCols-1; i++){
				appendTxt += "	";
			}
		}				
	}
	
					
	tw1.append(""+appendTxt);
	
	double [] coloc, iProfileC2, iProfileC3;
	for(int i = 0; i < cilia.size(); i++){
		if(cilia.get(i).excluded){
			continue;
		}
		appendTxt = "	";	
		appendTxt += "	" + dformat0.format(i+1); //"ID";
		appendTxt += "	" + dformat6.format(cilia.get(i).xC);
		appendTxt += "	" + dformat6.format(cilia.get(i).yC);
		appendTxt += "	" + dformat6.format(cilia.get(i).zC);
		appendTxt += "	" + dformat0.format(cilia.get(i).voxels);
		appendTxt += "	" + dformat6.format(cilia.get(i).volume);
		appendTxt += "	" + dformat0.format(cilia.get(i).surfaceVoxels);
		appendTxt += "	" + dformat6.format(cilia.get(i).surface);
		appendTxt += "	" + dformat6.format(cilia.get(i).shapeComplexity);
		appendTxt += "	" + dformat6.format(cilia.get(i).sphereRadius);
		appendTxt += "	" + dformat6.format(cilia.get(i).maximumSpan);	//maximum span TODO - method to be implemented
		
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilia.get(i).colocalizedVolumeC2);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(100.0*cilia.get(i).colocalizedFractionC2);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilia.get(i).colocalizedVolumeC3);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(100.0*cilia.get(i).colocalizedFractionC3);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilia.get(i).colocalizedCompToBGVolumeC2);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(100.0*cilia.get(i).colocalizedCompToBGFractionC2);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilia.get(i).colocalizedCompToBGVolumeC3);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(100.0*cilia.get(i).colocalizedCompToBGFractionC3);}
		
		appendTxt += "	" + dformat6.format(cilia.get(i).minCiliumIntensity);
		appendTxt += "	" + dformat6.format(cilia.get(i).maxCiliumIntensity);
		appendTxt += "	" + dformat6.format(cilia.get(i).maxTenPercentCiliumIntensity);
		appendTxt += "	" + dformat6.format(cilia.get(i).averageCiliumIntensity);
		appendTxt += "	" + dformat6.format(cilia.get(i).SDCiliumIntensity);
		
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilia.get(i).minC2Intensity);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilia.get(i).maxC2Intensity);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilia.get(i).maxTenPercentC2Intensity);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilia.get(i).averageC2Intensity);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilia.get(i).SDC2Intensity);}
		
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilia.get(i).minC3Intensity);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilia.get(i).maxC3Intensity);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilia.get(i).maxTenPercentC3Intensity);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilia.get(i).averageC3Intensity);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilia.get(i).SDC3Intensity);}
		
		appendTxt += "	"; if(skeletonize) appendTxt += dformat0.format(cilia.get(i).foundSkl);
		appendTxt += "	"; if(cilia.get(i).sklAvailable){appendTxt += dformat0.format(cilia.get(i).branches);}
		appendTxt += "	"; if(cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilia.get(i).treeLength);}
		appendTxt += "	"; if(cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilia.get(i).largestShortestPathOfLargest);}
		appendTxt += "	"; if(cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilia.get(i).orientationVector[0]);}
		appendTxt += "	"; if(cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilia.get(i).orientationVector[1]);}
		appendTxt += "	"; if(cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilia.get(i).orientationVector[2]);}
		appendTxt += "	"; if(cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilia.get(i).bendingIndex);}
		
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(intensityThresholds[channelC2-1]);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(intensityThresholds[channelC3-1]);}
		appendTxt += "	"; if(measureBasalLocal){appendTxt += dformat6.format(intensityThresholds[basalStainC-1]);}
		
		//profiles
		if(skeletonize){
			if(measureC2local){
				iProfileC2 = cilia.get(i).getIntensityProfile(2, calibration, false);
				appendTxt += "	"; 
				if(cilia.get(i).sklAvailable)	appendTxt += dformat6.format(tools.getSum(iProfileC2));
				appendTxt += "	"; 
				if(cilia.get(i).sklAvailable)	appendTxt += dformat6.format(tools.getAverage(iProfileC2));
			}else{
				iProfileC2  = null;
				appendTxt += "		";
			}
			
			if(measureC3local){
				iProfileC3 = cilia.get(i).getIntensityProfile(3, calibration, false);
				appendTxt += "	";
				if(cilia.get(i).sklAvailable)	appendTxt += dformat6.format(tools.getSum(iProfileC3));
				appendTxt += "	"; 
				if(cilia.get(i).sklAvailable)	appendTxt += dformat6.format(tools.getAverage(iProfileC3));
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
			
			if(measureC2local){
				if(iProfileC2 != null){
					for(int j = 0; j < nrOfProfileCols; j++){
						appendTxt += "	";
						if(cilia.get(i).sklAvailable && j < iProfileC2.length){
							appendTxt += dformat6.format(iProfileC2[j]);
						}						
					}
					iProfileC2 = cilia.get(i).getIntensityProfile(2, calibration, true);
					for(int j = 0; j < nrOfProfileCols; j++){
						appendTxt += "	";
						if(cilia.get(i).sklAvailable && j < iProfileC2.length){
							appendTxt += dformat6.format(iProfileC2[j]);
						}						
					}
				}else{
					for(int j = 0; j < nrOfProfileCols; j++){
						appendTxt += "		";
					}
				}				
			}					
			if(measureC3local){
				if(iProfileC3 != null){
					for(int j = 0; j < nrOfProfileCols; j++){
						appendTxt += "	";
						if(cilia.get(i).sklAvailable && j < iProfileC3.length){
							appendTxt += dformat6.format(iProfileC3[j]);
						}						
					}
					iProfileC3 = cilia.get(i).getIntensityProfile(3, calibration, true);
					for(int j = 0; j < nrOfProfileCols; j++){
						appendTxt += "	";
						if(cilia.get(i).sklAvailable && j < iProfileC3.length){
							appendTxt += dformat6.format(iProfileC3[j]);
						}
					}
				}else{
					for(int j = 0; j < nrOfProfileCols; j++){
						appendTxt += "		";
					}
				}					
			}
		}							
		tw1.append(""+appendTxt);
		
		appendTxt = name + appendTxt;
		appendTxt += getOneRowFooter(currentDate);
		tw2.append(""+appendTxt);
	}
	
	addFooter(tw1, currentDate);
	if(!tw1.saveAndFinish(filePrefix + ".txt")) {
		progress.notifyMessage("IO ERROR when saving file " + filePrefix + ".txt", ProgressDialog.ERROR);
	}
	
	//save one row results file
	if(!tw2.saveAndFinish(filePrefix + "s.txt")) {
		progress.notifyMessage("IO ERROR when saving file " + (filePrefix + "s.txt"), ProgressDialog.ERROR);
	}
	
	//save profile text file
	saveSkeletonPoints(cilia, dir, name, filePrefix + "l.txt", currentDate);
	
	//save RP image
	if(showGUIs) {
		progress.updateBarText("saving images...");
		progress.setBar(0.85);
	}		
	
	IJ.saveAsTiff(imp, filePrefix + "_RP"); 

	//save 3D images
	if(saveSingleCilia3DImages){				
		ImagePlus impTemp;
		for(int i = 0; i < cilia.size(); i++){
			if(showGUIs) {
				progress.updateBarText("Producing 3D images of individual cilia (" + i + "/" + cilia.size() + " done)");
			}
			impTemp = cilia.get(i).getCiliumImp(intensityThresholds, channelC2, channelC3);
			saveImageAs3D(subfolderPrefix + "_C" + (i+1) + "",impTemp, false, true, false);
			impTemp.changes = false;
			impTemp.close();
		}
	}		
	{		
		if(showGUIs) {
			progress.updateBarText("Producing Overview Images");
		}
		if(skeletonize){
			saveSkeletonOverviewImageNonTimeLapse(filePrefix + "_SKL", imp, cilia,
					name, dir, currentDate, startDate);
		}
		if(saveOverview3DImages){
			if(showGUIs) {
				progress.updateBarText("Producing Overview 3D Image");	
			}
			saveImageAs3D(filePrefix + "_RP", reduceToMaskChannel(imp, channelReconstruction), false, true, false);
		}		
	}	
	
	if(selectedTaskVariant.equals(taskVariant[1])) {
		imp.changes = false;
		imp.close();
	}
	if(showGUIs) {
		progress.setBar(0.95);
	}
}

/**
 * Workflow for TIMELAPSE analysis
 * */
private void analyzeCiliaIn4DAndSaveResults(ImagePlus imp, boolean measureC2local, boolean measureC3local, boolean measureBasalLocal, 
		String name, String dir, Date startDate, String filePrefix, String subfolderPrefix, String tempExcludeSelection){
	ArrayList<TimelapseCilium> timelapseCilia = new ArrayList<TimelapseCilium>();
	double intensityThresholds [] = new double[imp.getNChannels()]; //for each channel individual
	
	TextRoi txtID;
	imp.setOverlay(new Overlay());
	imp.setHideOverlay(false);
	
	int excludedCilia = 0;
	{
		//Retrieving ciliary objects from the image
		ArrayList<ArrayList<CellPoint>> ciliaParticles = getCiliaObjectsTimelapse(imp, channelReconstruction, increaseRangeCilia);	//Method changed on 23.04.2019
		if(ciliaParticles.size() == 0) {
			return;
		}
		
		if(showGUIs)	progress.updateBarText("Cilia reconstruction completed.");
		//TODO implement method to move on if no cells detected
		
		//Determine intensity thresholds for each other channel
		if(showGUIs)	progress.updateBarText("Determining intensity thresholds...");
		intensityThresholds = getIntensityThresholds(imp, ciliaParticles);
		
		
		boolean touchesXY, touchesZ;
		int [] sliceCounter = new int [imp.getNSlices()];
		
		timelapseCilia.ensureCapacity(ciliaParticles.size());
		for(int i = 0; i < ciliaParticles.size(); i++){
			if(showGUIs)	progress.updateBarText("Quantifying cilia (" + i + "/" + ciliaParticles.size() + " done)");
			timelapseCilia.add(new TimelapseCilium(ciliaParticles.get(i), imp, measureC2local, channelC2, measureC3local, channelC3, measureBasalLocal, basalStainC, 
					channelReconstruction, gXY, gZ, intensityThresholds, progress, skeletonize));
			if(timelapseCilia.size()!=i+1) {
				if(showGUIs) {
					progress.notifyMessage("Error while measuring cilium " + (i+1), ProgressDialog.NOTIFICATION);
				}else {
					System.out.println("Error while measuring cilium " + (i+1));
				}
			}
			
			//Exclude cilia if selected
			if(!tempExcludeSelection.equals(excludeOptions[0])){
				if(showGUIs)	progress.updateBarText("Quantifying cilia (" + i + "/" + ciliaParticles.size() + " done): checking xyz borders...");
				touchesXY = false; touchesZ = false;
				Arrays.fill(sliceCounter, 0);
				for(int k = 0; k < timelapseCilia.get(i).cilia.size(); k++){
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
				
				if(showGUIs)	progress.updateBarText("filter cilia... (" + (i+1) + "/" + timelapseCilia.size() + ")");
			}else{
				//write ID into image
				txtID = new TextRoi((int)Math.round(timelapseCilia.get(i).xCAvg/imp.getCalibration().pixelWidth), 
						(int)Math.round(timelapseCilia.get(i).yCAvg/imp.getCalibration().pixelHeight),
						dformat0.format(i+1), RoiFont);
				txtID.setStrokeColor(Color.WHITE);
				imp.getOverlay().add(txtID);				
			}
								
			if(showGUIs) {
				progress.addToBar(0.2/ciliaParticles.size());
				progress.updateBarText("reconstructing cilia... (" + (i+1) + "/" + ciliaParticles.size() + ")");
			}
		}
		
		ciliaParticles.clear();
		ciliaParticles.trimToSize();
	}		
	if(showGUIs)	progress.updateBarText("" + timelapseCilia.size() + " cilia reconstructed!");
	
//	for saving profiles
	double maximumArcLength = 0.0;
	int nrOfProfileCols = 0;
	if(measureC2local || measureC3local){
		for(int i = 0; i < timelapseCilia.size(); i++){
			if(timelapseCilia.get(i).excluded){
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
		
	if(showGUIs) {
		progress.updateBarText("saving results...");
		progress.setBar(0.9);
	}

	//Output images
			
	//Save Textfile--------------------------------------	
	OutputTextFile tw1 = new OutputTextFile ("");
	OutputTextFile tw2 = new OutputTextFile ("");
	
	addSettingsBlockToPanel(tw1, currentDate, startDate, name, imp, 
			measureC2local, measureC3local, measureBasalLocal, intensityThresholds, excludedCilia, timelapseCilia.size());
	
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
	appendTxt += "	" + "Maximum span (time-averaged) ["+calibrationDimension+"]";	//TODO - to be implemented
	
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
		
	tw1.append(""+appendTxt);
				
	for(int i = 0; i < timelapseCilia.size(); i++){
		if(showGUIs)	progress.updateBarText("Saving individual cilia averages (" + i + "/" + timelapseCilia.size() + " done)");
		if(timelapseCilia.get(i).excluded){
			continue;
		}
		appendTxt = "	";	
		appendTxt += "	" + dformat0.format(i+1); //"ID";
		
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).xCAvg);
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).yCAvg);
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).zCAvg);
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).voxelsAvg);
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).volumeAvg);
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).surfaceVoxelsAvg);
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).surfaceAvg);
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).shapeComplexityAvg);
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).sphereRadiusAvg);
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).maximumSpanAvg);	//maximum span TODO - to be implemented
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(timelapseCilia.get(i).colocalizedVolumeC2Avg);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(100.0*timelapseCilia.get(i).colocalizedFractionC2Avg);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(timelapseCilia.get(i).colocalizedVolumeC3Avg);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(100.0*timelapseCilia.get(i).colocalizedFractionC3Avg);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(timelapseCilia.get(i).colocalizedCompToBGVolumeC2Avg);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(100.0*timelapseCilia.get(i).colocalizedCompToBGFractionC2Avg);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(timelapseCilia.get(i).colocalizedCompToBGVolumeC3Avg);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(100.0*timelapseCilia.get(i).colocalizedCompToBGFractionC3Avg);}
		
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).minCiliumIntensityAvg);
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).maxCiliumIntensityAvg);
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).maxTenPercentCiliumIntensityAvg);
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).averageCiliumIntensityAvg);
		appendTxt += "	" + dformat6.format(timelapseCilia.get(i).SDCiliumIntensityAvg);
		
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(timelapseCilia.get(i).minC2IntensityAvg);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(timelapseCilia.get(i).maxC2IntensityAvg);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(timelapseCilia.get(i).maxTenPercentC2IntensityAvg);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(timelapseCilia.get(i).averageC2IntensityAvg);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(timelapseCilia.get(i).SDC2IntensityAvg);}
		
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(timelapseCilia.get(i).minC3IntensityAvg);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(timelapseCilia.get(i).maxC3IntensityAvg);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(timelapseCilia.get(i).maxTenPercentC3IntensityAvg);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(timelapseCilia.get(i).averageC3IntensityAvg);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(timelapseCilia.get(i).SDC3IntensityAvg);}
		
		appendTxt += "	"; if(skeletonize) appendTxt += dformat6.format(timelapseCilia.get(i).foundSklAvg);
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += "yes";}else if(skeletonize){appendTxt += "no";}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += dformat6.format(timelapseCilia.get(i).branchesAvg);}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += dformat6.format(timelapseCilia.get(i).treeLengthAvg);}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += dformat6.format(timelapseCilia.get(i).largestShortestPathOfLargestAvg);}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += dformat6.format(timelapseCilia.get(i).orientationVectorAvg[0]);}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += dformat6.format(timelapseCilia.get(i).orientationVectorAvg[1]);}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += dformat6.format(timelapseCilia.get(i).orientationVectorAvg[2]);}
		appendTxt += "	"; if(timelapseCilia.get(i).sklAvailableInAllFrames){appendTxt += dformat6.format(timelapseCilia.get(i).bendingIndexAvg);}
		
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(intensityThresholds[channelC2-1]);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(intensityThresholds[channelC3-1]);}
		appendTxt += "	"; if(measureBasalLocal){appendTxt += dformat6.format(intensityThresholds[basalStainC-1]);}
		
		//TODO Averages of profile based parameters to be implemented
							
		tw1.append(""+appendTxt);
		
		appendTxt = name + appendTxt;
		appendTxt += getOneRowFooter(currentDate);
		tw2.append(""+appendTxt);
		
		if(showGUIs)	progress.updateBarText("Saving individual cilium kinetics (" + i + "/" + timelapseCilia.size() + " done)");
		saveIndividualCiliumKinetics(timelapseCilia.get(i), ""+(i+1), imp, 
				measureC2local, measureC3local, measureBasalLocal, 
				nrOfProfileCols, name, dir, currentDate, startDate,
				filePrefix + "_C" + dformat0.format(i+1), subfolderPrefix + "_C" + dformat0.format(i+1), 
				intensityThresholds, excludedCilia, timelapseCilia.size());
	}
	
	addFooter(tw1, currentDate);				
	if(!tw1.saveAndFinish(filePrefix + ".txt")) {
		if(showGUIs) {
			progress.notifyMessage("IO ERROR when saving file " + filePrefix + ".txt", ProgressDialog.ERROR);
		}else {
			System.out.println("IO ERROR when saving file " + filePrefix + ".txt");
		}
	}
	
	//save one row results file
	if(!tw2.saveAndFinish(filePrefix + "s.txt")) {
		if(showGUIs) {
			progress.notifyMessage("IO ERROR when saving file " + (filePrefix + "s.txt"), ProgressDialog.ERROR);
		}else {
			System.out.println("IO ERROR when saving file " + (filePrefix + "s.txt"));
		}
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
			if(showGUIs)	progress.updateBarText("Saving cilium-specific images (" + i + "/" + timelapseCilia.size() + " done)");
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
				if(showGUIs) {
					progress.notifyMessage("IO ERROR when saving file " + (subfolderPrefix + "_C" + dformat0.format(i+1) + "_img.txt"), ProgressDialog.ERROR);
				}else {
					System.out.println("IO ERROR when saving file " + (subfolderPrefix + "_C" + dformat0.format(i+1) + "_img.txt"));
				}
			}
			
			
			impC = timelapseCilia.get(i).getCiliumImp(intensityThresholds, channelC2, channelC3, progress);
			IJ.saveAsTiff(impC, subfolderPrefix + "_C" + dformat0.format(i+1) + ".tif"); 
			impC.changes = false;
			impC.close();			
		}					
	}
	
	//save 3D images
	{		
		if(showGUIs)	progress.updateBarText("Producing Overview Skeleton Image");
		saveSkeletonOverviewImagesTimelapse(filePrefix + "_SKL", imp, timelapseCilia,
				name, dir, currentDate, startDate);
		if(this.saveOverview3DImages){			
			if(showGUIs)	progress.updateBarText("Producing Overview 3D Image");		
			saveImageAs3D(filePrefix + "_RP", reduceToMaskChannel(imp, channelReconstruction), false, true, false);
		}		
	}
	
	if(saveSingleCilia3DImages){
		for(int i = 0; i < timelapseCilia.size(); i++){
			if(showGUIs)	progress.updateBarText("Producing 3D images of individual cilia (" + i + "/" + timelapseCilia.size() + " done)");
			saveImageAs3D(subfolderPrefix + "_C" + (i+1), timelapseCilia.get(i).getCiliumImp(intensityThresholds, channelC2, channelC3, progress),
					false, true, false);
		}
	}
	if(selectedTaskVariant.equals(taskVariant[1])) {
		imp.changes = false;
		imp.close();
	}
	if(showGUIs)	progress.setBar(0.95);
}

public void saveIndividualCiliumKinetics(TimelapseCilium cilium, String ciliumID, ImagePlus imp,
	boolean measureC2local, boolean measureC3local, boolean measureBasalLocal, 
	int nrOfProfileCols, String name, String dir, Date currentDate, Date startDate, 
	String filePrefix, String subfolderPrefix, 
	double [] intensityThresholds, int excludedCilia, int totalCilia){
	//Save Textfile--------------------------------------	
	OutputTextFile tw1 = new OutputTextFile ("");
	OutputTextFile tw2 = new OutputTextFile ("");
	
	addSettingsBlockToPanel(tw1, currentDate, startDate, name, imp, 
			measureC2local, measureC3local, measureBasalLocal, intensityThresholds, excludedCilia, totalCilia);
	
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
	appendTxt += "	" + "Maximum span ["+calibrationDimension+"]";	//TODO - method to be implemented
	
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
	appendTxt += "	"; if(skeletonize && measureC2local) appendTxt += "Average B intensity on center line";
	
	appendTxt += "	"; if(skeletonize && measureC2local) appendTxt += "A: Colocalized on centerline compared to BG volume [" + calibrationDimension + "]";
	appendTxt += "	"; if(skeletonize && measureC2local) appendTxt += "A: Colocalized on centerline compared to BG volume [% total length]";
	appendTxt += "	"; if(skeletonize && measureC3local) appendTxt += "B: Colocalized on centerline compared to BG volume [" + calibrationDimension + "]";
	appendTxt += "	"; if(skeletonize && measureC3local) appendTxt += "B: Colocalized on centerline compared to BG volume [% total length]";
		
	//profiles
	if(measureC2local && skeletonize){
		appendTxt += "	" + "Profile A (arc length step: " + dformat6.format(calibration) + ")";
		for(int i = 0; i < nrOfProfileCols-1; i++){
			appendTxt += "	";
		}
		appendTxt += "	" + "Profile A (normalized to reconstruction channel) (arc length step: " + dformat6.format(calibration) + ")";
		for(int i = 0; i < nrOfProfileCols-1; i++){
			appendTxt += "	";
		}
	}
		
	if(measureC3local && skeletonize){
		appendTxt += "	" + "Profile B (arc length step: " + dformat6.format(calibration) + ")";
		for(int i = 0; i < nrOfProfileCols-1; i++){
			appendTxt += "	";
		}
		appendTxt += "	" + "Profile B (normalized to reconstruction channel) (arc length step: " + dformat6.format(calibration) + ")";
		for(int i = 0; i < nrOfProfileCols-1; i++){
			appendTxt += "	";
		}
	}				
					
	tw1.append(""+appendTxt);
				
	int time = 0;
	double [] iProfileC2, iProfileC3, coloc;
	scanningTimePoints: for(int i = 0; i < cilium.cilia.size(); i++){			
		while(true){
			if (cilium.cilia.get(i).t!=time){
				if(showGUIs)	progress.updateBarText("Skip frame for cilium " + ciliumID + ": " + time + " of " + cilium.cilia.size());
				appendTxt = "		" + ciliumID + "	" + dformat0.format(time);
				tw1.append(""+appendTxt);
				
				appendTxt = name + appendTxt;
				appendTxt += getOneRowFooter(currentDate);
				tw2.append(""+appendTxt);
				
				time++;
			}else if(time>cilium.cilia.get(cilium.cilia.size()-1).t){
				break scanningTimePoints;
			}else{
				break;
			}
		}
		time++;
		
		if(cilium.cilia.get(i).excluded){
			if(showGUIs)	progress.updateBarText("Skip frame for cilium " + ciliumID + " because excluded: " + time);
			appendTxt = "		" + ciliumID + "	" + dformat0.format(cilium.cilia.get(i).t);
			tw1.append(""+appendTxt);
			
			appendTxt = name + appendTxt;
			appendTxt += getOneRowFooter(currentDate);
			tw2.append(""+appendTxt);
			continue;
		}
		
		appendTxt = "		" + ciliumID;	
		appendTxt += "	" + dformat0.format(cilium.cilia.get(i).t); //"frame";
		
		appendTxt += "	" + dformat6.format(cilium.cilia.get(i).xC);
		appendTxt += "	" + dformat6.format(cilium.cilia.get(i).yC);
		appendTxt += "	" + dformat6.format(cilium.cilia.get(i).zC);
		appendTxt += "	" + dformat0.format(cilium.cilia.get(i).voxels);
		appendTxt += "	" + dformat6.format(cilium.cilia.get(i).volume);
		appendTxt += "	" + dformat0.format(cilium.cilia.get(i).surfaceVoxels);
		appendTxt += "	" + dformat6.format(cilium.cilia.get(i).surface);
		appendTxt += "	" + dformat6.format(cilium.cilia.get(i).shapeComplexity);
		appendTxt += "	" + dformat6.format(cilium.cilia.get(i).sphereRadius);
		appendTxt += "	" + dformat6.format(cilium.cilia.get(i).maximumSpan);	//maximum span TODO - method to be implemented

		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilium.cilia.get(i).colocalizedVolumeC2);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(100.0*cilium.cilia.get(i).colocalizedFractionC2);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilium.cilia.get(i).colocalizedVolumeC3);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(100.0*cilium.cilia.get(i).colocalizedFractionC3);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilium.cilia.get(i).colocalizedCompToBGVolumeC2);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(100.0*cilium.cilia.get(i).colocalizedCompToBGFractionC2);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilium.cilia.get(i).colocalizedCompToBGVolumeC3);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(100.0*cilium.cilia.get(i).colocalizedCompToBGFractionC3);}
		
		appendTxt += "	" + dformat6.format(cilium.cilia.get(i).minCiliumIntensity);
		appendTxt += "	" + dformat6.format(cilium.cilia.get(i).maxCiliumIntensity);
		appendTxt += "	" + dformat6.format(cilium.cilia.get(i).maxTenPercentCiliumIntensity);
		appendTxt += "	" + dformat6.format(cilium.cilia.get(i).averageCiliumIntensity);
		appendTxt += "	" + dformat6.format(cilium.cilia.get(i).SDCiliumIntensity);
		
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilium.cilia.get(i).minC2Intensity);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilium.cilia.get(i).maxC2Intensity);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilium.cilia.get(i).maxTenPercentC2Intensity);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilium.cilia.get(i).averageC2Intensity);}
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(cilium.cilia.get(i).SDC2Intensity);}
		
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilium.cilia.get(i).minC3Intensity);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilium.cilia.get(i).maxC3Intensity);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilium.cilia.get(i).maxTenPercentC3Intensity);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilium.cilia.get(i).averageC3Intensity);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(cilium.cilia.get(i).SDC3Intensity);}
		
		appendTxt += "	"; if(skeletonize) appendTxt += dformat0.format(cilium.cilia.get(i).foundSkl);
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += "yes";}else if(skeletonize){appendTxt += "no";}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += dformat0.format(cilium.cilia.get(i).branches);}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).treeLength);}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).largestShortestPathOfLargest);}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).orientationVector[0]);}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).orientationVector[1]);}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).orientationVector[2]);}
		appendTxt += "	"; if(cilium.cilia.get(i).sklAvailable){appendTxt += dformat6.format(cilium.cilia.get(i).bendingIndex);}
		
		appendTxt += "	"; if(measureC2local){appendTxt += dformat6.format(intensityThresholds[channelC2-1]);}
		appendTxt += "	"; if(measureC3local){appendTxt += dformat6.format(intensityThresholds[channelC3-1]);}
		appendTxt += "	"; if(measureBasalLocal){appendTxt += dformat6.format(intensityThresholds[basalStainC-1]);}
		
		//profiles
		if(skeletonize){
			if(showGUIs)	progress.updateBarText("Determine intensity profiles for cilium " + ciliumID + " - time " + time);
			if(measureC2local){
				iProfileC2 = cilium.cilia.get(i).getIntensityProfile(2, calibration, false);
				appendTxt += "	";
				if(cilium.cilia.get(i).sklAvailable)	appendTxt += dformat6.format(tools.getSum(iProfileC2));
				appendTxt += "	";
				if(cilium.cilia.get(i).sklAvailable)	appendTxt += dformat6.format(tools.getAverage(iProfileC2));
			}else{
				iProfileC2  = null;
				appendTxt += "		";
			}
						
			if(measureC3local){
				iProfileC3 = cilium.cilia.get(i).getIntensityProfile(3, calibration, false);
				appendTxt += "	"; 
				if(cilium.cilia.get(i).sklAvailable)	appendTxt += dformat6.format(tools.getSum(iProfileC3));
				appendTxt += "	"; 
				if(cilium.cilia.get(i).sklAvailable)	appendTxt += dformat6.format(tools.getAverage(iProfileC3));
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
			
			if(measureC2local){
				if(iProfileC2 != null){
					for(int j = 0; j < nrOfProfileCols; j++){
						appendTxt += "	";
						if(cilium.cilia.get(i).sklAvailable && j < iProfileC2.length){
							appendTxt += dformat6.format(iProfileC2[j]);
						}						
					}
					iProfileC2 = cilium.cilia.get(i).getIntensityProfile(2, calibration, true);
					for(int j = 0; j < nrOfProfileCols; j++){
						appendTxt += "	";
						if(cilium.cilia.get(i).sklAvailable && j < iProfileC2.length){
							appendTxt += dformat6.format(iProfileC2[j]);
						}						
					}
				}else{
					for(int j = 0; j < nrOfProfileCols; j++){
						appendTxt += "		";
					}
				}
			}					
			if(measureC3local){
				if(iProfileC3 != null){
					for(int j = 0; j < nrOfProfileCols; j++){
						appendTxt += "	";
						if(cilium.cilia.get(i).sklAvailable && j < iProfileC3.length){
							appendTxt += dformat6.format(iProfileC3[j]);
						}						
					}
					iProfileC3 = cilium.cilia.get(i).getIntensityProfile(3, calibration, true);
					for(int j = 0; j < nrOfProfileCols; j++){
						appendTxt += "	";
						if(cilium.cilia.get(i).sklAvailable && j < iProfileC3.length){
							appendTxt += dformat6.format(iProfileC3[j]);
						}
					}
				}else{
					for(int j = 0; j < nrOfProfileCols; j++){
						appendTxt += "		";
					}
				}
			}
		}
		tw1.append(""+appendTxt);
		
		appendTxt = name + appendTxt;
		appendTxt += getOneRowFooter(currentDate);
		tw2.append(""+appendTxt);			
	}	
	
	if(showGUIs)	progress.updateBarText("Save text file cilium " + ciliumID);	
	
	addFooter(tw1, currentDate);				
	
	if(!tw1.saveAndFinish(subfolderPrefix + ".txt")) {
		if(showGUIs) {
			progress.notifyMessage("IO ERROR when saving file " + subfolderPrefix + ".txt", ProgressDialog.ERROR);
		}else {
			System.out.println("IO ERROR when saving file " + subfolderPrefix + ".txt");
		}
	}
	
	//save one row results file
	if(!tw2.saveAndFinish(subfolderPrefix + "s.txt")) {
		if(showGUIs) {
			progress.notifyMessage("IO ERROR when saving file " + subfolderPrefix + "s.txt", ProgressDialog.ERROR);
		}else {
			System.out.println("IO ERROR when saving file " + subfolderPrefix + "s.txt");
		}
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
		if(showGUIs)	progress.updateBarText("Producing timelapse skeleton overview image (" + (i+1) + "/" + cilia.size() + ")");
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
	
	tp.append("Results image:	" + savePath.substring(savePath.lastIndexOf(System.getProperty("file.separator"))) + "_Skl.tif");
	tp.append("	channel 1:	red	" + "ciliary skeleton");
	tp.append("	channel 2:	white	" + "detected base");
	
	addFooter(tp, currentDate);
	if(!tp.saveAndFinish(savePath + "_info.txt")) {
		if(showGUIs) {
			progress.notifyMessage("IO ERROR when saving file " + savePath + "_info.txt", ProgressDialog.ERROR);
		}else {
			System.out.println("IO ERROR when saving file " + savePath + "_info.txt");
		}
	}
	
	// make 3D visualization
	if(saveOverview3DImages){
		ImagePlus impCal, imp3D;
		Visualizer3D v3D;	
		try {
			if(showGUIs)	progress.updateBarText("Producing 3D TL skeleton overview image...launch 3Dvis");
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
				if(showGUIs)	progress.updateBarText("Producing timelapse skeleton overview 3D image (" + (i+1) + "/" + impOut.getNFrames() + ")");
				impCal = getTimePointFor3D(impOut, i+1);
				if(showGUIs)	progress.updateBarText("Producing timelapse skeleton overview 3D image (" + (i+1) + "/" + impOut.getNFrames() + ") ... add scale bar");
				double calBarLength = this.addContainedScalerBarAndGetBarValue(impCal);
				if(showGUIs)	progress.updateBarText("Producing timelapse skeleton overview 3D image (" + (i+1) + "/" + impOut.getNFrames() + ") ... convert to RGB");
				convertToRGB(impCal);			
				if(showGUIs)	progress.updateBarText("Producing timelapse skeleton overview 3D image (" + (i+1) + "/" + impOut.getNFrames() + ") ... write IDs");
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
	//							+ j + "/" + impOut.getOverlay().size() + "", ProgressDialog.LOG);
					}
				}else{
					if(showGUIs) {
						progress.notifyMessage("no overlay in TL skeleton overview", ProgressDialog.LOG);
					}else {
						System.out.println("no overlay in TL skeleton overview");
					}
				}
				
	//			if(impCal == null) impCal = particleImp.duplicate();
				
				if(showGUIs)	progress.updateBarText("Producing timelapse skeleton overview 3D image (" + (i+1) + "/" + impOut.getNFrames() + ") ... create 3D vis");
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
			
			if(showGUIs)	progress.updateBarText("Saving timelapse skeleton overview 3D image...");		
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
			if(showGUIs)	progress.notifyMessage("Image: " + savePath.substring(savePath.lastIndexOf(System.getProperty("file.separator"))) 
				+ "_3D.tif: Producing timelapse skeleton overview 3D image failed - error message: \n"
				+ "" + out,ProgressDialog.ERROR);
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
		if(showGUIs)	progress.updateBarText("Producing non-timelapse skeleton overview image (" + (j+1) + "/" + cilia.size() + ")");
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
	if(showGUIs)	progress.updateBarText("Writing metadata text file for non-timelapse skeleton overview image...");
	OutputTextFile tp =new OutputTextFile ("");
	tp.append("Channel information for skeleton results image - analysis of:	" + name);
	tp.append("Saving date:	" + FullDateFormatter.format(currentDate)
	+ "	Starting date:	" + FullDateFormatter.format(startDate));
	tp.append("");
	
	tp.append("Results image:	" + savePath.substring(savePath.lastIndexOf(System.getProperty("file.separator"))) + "_Skl.tif");
	tp.append("	channel 1:	red	" + "ciliary skeleton");
	tp.append("	channel 2:	white	" + "detected base");
	
	addFooter(tp, currentDate);
	if(!tp.saveAndFinish(savePath + "_info.txt")) {
		if(showGUIs) {
			progress.notifyMessage("IO ERROR when saving file " + savePath + "_info.txt", ProgressDialog.ERROR);
		}else {
			System.out.println("IO ERROR when saving file " + savePath + "_info.txt");
		}
	}
	
	// make 3D visualization
	if(saveOverview3DImages){
		if(showGUIs)	progress.updateBarText("Producing 3D NTL skeleton overview image...make scale bar");
		double calBarLength = this.addContainedScalerBarAndGetBarValue(impOut);
		if(showGUIs)	progress.updateBarText("Producing 3D NTL skeleton overview image...covert to RGB");
		convertToRGB(impOut);
		if(showGUIs)	progress.updateBarText("Producing 3D NTL skeleton overview image...write IDs");	
		
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
//						+ j + "/" + impOut.getOverlay().size() + "", ProgressDialog.LOG);
			}
		}else{
			if(showGUIs) {
				progress.notifyMessage("no overlay in NTL skeleton overview", ProgressDialog.LOG);
			}else {
				System.out.println("no overlay in NTL skeleton overview");
			}
		}
//		
//		impOut.show();
//		new WaitForUserDialog("imp out").show();
//		impOut.hide();
		
		Visualizer3D v3D;
		try {
			ImagePlus imp3D;	
			if(showGUIs)	progress.updateBarText("Producing 3D NTL skeleton overview image...launch 3Dvis");
			v3D = new Visualizer3D(impOut, 3.0f);
			v3D.setImage(impOut);
			v3D.setAngle(10.0f, -10.0f, 0.0f);
			v3D.setObjectLightValue(1.2f);
			v3D.setLightPosX(-0.25f);
			v3D.setAlphaOffset1(0);
			if(showGUIs)	progress.updateBarText("Producing 3D NTL skeleton overview image...get 3Dvis");
			imp3D = v3D.get3DVisualization(false);
			
			writeBarText(imp3D, calBarLength, imp.getCalibration().getUnit(), false, 0.0, "");	
	
//			imp3D.show();
//			new WaitForUserDialog("imp3D").show();
//			imp3D.hide();
			
			if(showGUIs)	progress.updateBarText("Producing 3D NTL skeleton overview image...save");								
			IJ.saveAs(imp3D, "PNG", savePath + "_3D.png"); 
	
			imp3D.changes = false;
			imp3D.close();			
			
		}catch(Exception e){
			String out = "";
			for(int err = 0; err < e.getStackTrace().length; err++){
				out += " \n " + e.getStackTrace()[err].toString();
			}			
			if(showGUIs) {
				progress.notifyMessage("Image: " + savePath.substring(savePath.lastIndexOf(System.getProperty("file.separator")))
					+ ": Producing 3D NTL skeleton overview image failed - error message: \n"
					+ "" + out,ProgressDialog.ERROR); 
			}else {
				System.out.println("Image: " + savePath.substring(savePath.lastIndexOf(System.getProperty("file.separator")))
					+ ": Producing 3D NTL skeleton overview image failed - error message: \n"
					+ "" + out);
			}
		}		
		v3D = null;
		impOut.changes = false;
		impOut.close();
		

	}	
}

private void writeBarText(ImagePlus imp, double calBarLength, String unit, boolean writeTime, double time, String timeUnit){
	//write bar text:
		if(showGUIs)	progress.updateBarText("Make cal bar text to image");
		TextRoi txtROI = new TextRoi(5,5,
				"scale bars: " + dformat3.format(calBarLength) + " " + unit, RoiFont);
		if(showGUIs)	progress.updateBarText("Writing cal bar text to image");
		for(int c = 0; c < imp.getNChannels(); c++){
			txtROI.drawPixels(imp.getStack().getProcessor(imp.getStackIndex(c+1, 1, 1)));		
		}
		
		if(writeTime){
			if(showGUIs)	progress.updateBarText("Make time bar text to image");
			txtROI = new TextRoi(5,20,
					"time: " + dformat3.format(time) + " " + timeUnit, RoiFont);
			if(showGUIs)	progress.updateBarText("Writing time bar text to image");
			for(int c = 0; c < imp.getNChannels(); c++){
				txtROI.drawPixels(imp.getStack().getProcessor(imp.getStackIndex(c+1, 1, 1)));		
			}
		}
		
//		imp.show();
//		new WaitForUserDialog("imp bar").show();
//		imp.hide();
		if(showGUIs)	progress.updateBarText("Cal bar done");
}

/**
 * save image as 3D
 * */
private void saveImageAs3D(String savePath, ImagePlus imp, boolean transparent, boolean useLight, boolean autoAlpha){
	ImagePlus impOut = imp.duplicate();
	
	impOut.setDisplayMode(IJ.COMPOSITE);
	for(int c = 0; c < impOut.getNChannels(); c++){
		impOut.setC(c+1);	
		if(impOut.getBitDepth()==8){
			impOut.setDisplayRange(0, 255);
		}else if(impOut.getBitDepth()==16){
			//TODO check which range applies
			impOut.setDisplayRange(0, 4096);
		}		
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
			
			if(imp3D.getNFrames() == 1) {
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
			if(showGUIs) {
				progress.notifyMessage("Image: " + savePath.substring(savePath.lastIndexOf(System.getProperty("file.separator"))) 
					+ "_3D.tif: Producing 3D image image failed - error message: \n"
					+ "" + out,ProgressDialog.ERROR);
			}else {
				System.out.println("Image: " + savePath.substring(savePath.lastIndexOf(System.getProperty("file.separator"))) 
					+ "_3D.tif: Producing 3D image image failed - error message: \n"
					+ "" + out);
			}
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
	if(showGUIs)	progress.updateBarText("Finding perfect scale bar...");
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
	if(showGUIs)	progress.updateBarText("Perfect scale bar found");
	
	if(showGUIs)	progress.updateBarText("Calculating bar thickness");
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
	
	if(showGUIs)	progress.updateBarText("Writing bar");
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
		if(showGUIs) {
			progress.notifyMessage("Failed to produce scale bar in an output image", ProgressDialog.ERROR);
		}else {
			System.out.println("Failed to produce scale bar in an output image");
		}
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
	int iMax = (int)Math.round(Math.pow(2.0,impOut.getBitDepth())-1.0);
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
        int mode = ((CompositeImage)imp).getMode();
        if (imp.getNChannels()!=1) {
            impOut = new CompositeImage(impOut, mode);
            for (int c=1; c<=imp.getNChannels(); c++) {
                LUT lut = ((CompositeImage)imp).getChannelLut(c);
                ((CompositeImage)impOut).setChannelLut(lut, c);
            }
        }else {
            LUT lut = ((CompositeImage)imp).getChannelLut(1);
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
 * @returns true if settings were successfully entered and false if the user has canceled the dialog.
 * */
private boolean enterSettings() {
	//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
	//-------------------------GenericDialog-------------------------------------- TODO
	//&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
		
		GenericDialog gd = new GenericDialog(PLUGINNAME + " on " + System.getProperty("os.name") + " - detection preferences");	
		//show Dialog-----------------------------------------------------------------
		//.setInsets(top, left, bottom)
		gd.setInsets(0,0,0);	gd.addMessage("Detection", SubHeadingFont);
		gd.setInsets(5,0,0);	gd.addCheckbox("measure intensity A: ", measureC2);
		gd.setInsets(5,0,0);	gd.addCheckbox("measure intensity B: ", measureC3);
		gd.setInsets(5,0,0);	gd.addCheckbox("basal stain present", measureBasal);
		gd.setInsets(0,0,0);	gd.addNumericField("channels (Reconstruction / (opt. intensity A) / (opt. intensity B) / (basal stain)): ", channelReconstruction, 0);
		gd.setInsets(-23,55,0);	gd.addNumericField("", channelC2, 0);
		gd.setInsets(-23,110,0);	gd.addNumericField("", channelC3, 0);
		gd.setInsets(-23,165,0);gd.addNumericField("", basalStainC, 0);	
		gd.setInsets(5,0,0);	gd.addNumericField("minimum cilium size [voxel]: ", minSize, 0);	
		gd.setInsets(0,0,0);	gd.addCheckbox("Increase range for connecting cilia", increaseRangeCilia);	
		gd.setInsets(0,0,0);	gd.addChoice("additionally exclude...", excludeOptions, excludeSelection);
		gd.setInsets(0,0,0);	gd.addNumericField("minimum size of intensity regions (for A and B) [voxel]: ", minRestSize, 0);
		gd.setInsets(0,0,0);	gd.addCheckbox("Increase range for connecting intensity regions", increaseRangeRegions);	
				
		gd.setInsets(10,0,0);	gd.addCheckbox("Determine skeleton-based results (e.g. length)", skeletonize);
		gd.setInsets(0,0,0);	gd.addNumericField("before skeletonization: gauss filter XY and Z sigma: ", gXY, 2);
		gd.setInsets(-23,55,0);	gd.addNumericField("", gZ, 2);
		gd.setInsets(0,0,0);	gd.addNumericField("Reference distance for tangents and curvature [calibration unit (e.g. µm)]: ", refDistance, 2);
		
		
		gd.showDialog();
		//show Dialog-----------------------------------------------------------------

		//read and process variables--------------------------------------------------
		measureC2 = gd.getNextBoolean();
		measureC3 = gd.getNextBoolean();
		measureBasal = gd.getNextBoolean();
		channelReconstruction = (int) gd.getNextNumber();
		channelC2 = (int) gd.getNextNumber();
		channelC3 = (int) gd.getNextNumber();
		basalStainC = (int) gd.getNextNumber();
		
		minSize = (int) gd.getNextNumber();
		increaseRangeCilia = gd.getNextBoolean();
		excludeSelection = gd.getNextChoice();
		minRestSize = (int) gd.getNextNumber();
		increaseRangeRegions = gd.getNextBoolean();
		
		skeletonize = gd.getNextBoolean();
		gXY = (double) gd.getNextNumber();
		gZ = (double) gd.getNextNumber();
		refDistance = (double) gd.getNextNumber();
			
		//read and process variables--------------------------------------------------
		if (gd.wasCanceled()) return false;
		
		return true;
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
		File settingsFile = fd.getFiles()[0];
		
		if(settingsFile.equals(null)) {
			return false;
		}	
		
		//read preferences
		boolean readPrefs = false;
		boolean readToEnd = false;
		boolean versionOlderThanV014 = false;;
		String tempString;
		IJ.log("READING PREFERENCES:");
		try {
			FileReader fr = new FileReader(settingsFile);
			BufferedReader br = new BufferedReader(fr);
			String line = "";							
			reading: while(true){
				try{
					line = br.readLine();	
					if(!line.equals("") && line.equals(null)){
						break reading;
					}
				}catch(Exception e){
					break reading;
				}					
				
				if(line.contains("Settings")) {
					readPrefs = true;
				}
				
				if(readPrefs){
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
							tempString = line.substring(line.lastIndexOf("	")+1);
							if(tempString.contains(",") && !tempString.contains("."))	tempString = tempString.replace(",", ".");
							basalStainC = Integer.parseInt(tempString);	
							IJ.log("channel basal stain = " + basalStainC);
						}else if(!measureBasal && line.contains("")) {
							
						}else {
							IJ.error("Reading problem - rp05bs"); break reading;
						}
						
						line = br.readLine();
						if(line.contains("Minimum cilium size") && line.contains("Increase range for connecting cilia")){
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
						}else {
							IJ.error("Reading problem - code rp07"); break reading;
						}

						line = br.readLine();
						if(line.contains("Additional filtering")){
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
						line = br.readLine();
						line = br.readLine();
						line = br.readLine();
						line = br.readLine();
						line = br.readLine();
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
				if(showGUIs) progress.updateBarText("Saving individual cilia points (" + cil + "/" + cilia.size() + " done)");
				
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
					if(measureC2) addStr += dformat6.format(cilia.get(cil).profileC2[p]);	//Intensity A
					addStr += "	";
					if(measureC3) addStr += dformat6.format(cilia.get(cil).profileC3[p]);	//Intensity B
					addStr += "	";
					if(measureC2) addStr += dformat6.format(cilia.get(cil).profileC2norm[p]);	//Intensity A Norm
					addStr += "	";
					if(measureC3) addStr += dformat6.format(cilia.get(cil).profileC3norm[p]);	//Intensity B Norm
					tp.append(addStr);
				}
			}
		}
						
		// save file
		if(!tp.saveAndFinish(path)) {
			if(showGUIs) {
				progress.notifyMessage("IO ERROR when saving file " + path, ProgressDialog.ERROR);
			}else {
				System.out.println("IO ERROR when saving file " + path);
			}
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
				if(showGUIs)	progress.updateBarText("Saving individual cilia points (" + tlc + "/" + tlCilia.size() + " done)");
				
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
						if(measureC2) addStr += dformat6.format(cilia.get(cil).profileC2[p]);	//Intensity A
						addStr += "	";
						if(measureC3) addStr += dformat6.format(cilia.get(cil).profileC3[p]);	//Intensity B
						addStr += "	";
						if(measureC2) addStr += dformat6.format(cilia.get(cil).profileC2norm[p]);	//Intensity A Norm
						addStr += "	";
						if(measureC3) addStr += dformat6.format(cilia.get(cil).profileC3norm[p]);	//Intensity B Norm
						tp.append(addStr);
						
					}
				}
			}
		}
						
		// save file
		if(!tp.saveAndFinish(path)) {
			if(showGUIs)	progress.notifyMessage("IO ERROR when saving file " + path, ProgressDialog.ERROR);
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
//					if(showGUIs)	progress.notifyMessage("boundary: " + getArclengthDistance(points,p,vu) , ProgressDialog.LOG);
					break searchUpstream;
				}else {
//					if(showGUIs)	progress.notifyMessage("not in boundary", ProgressDialog.ERROR);
				}
			}
			
			searchDownstream: for(int vd = 0; vd <= p; vd++){
				if(getArclengthDistance(points,vd,p) <= refDistance){
//					progress.notifyMessage("boundary: " + getArclengthDistance(points,p,vd) , ProgressDialog.LOG);
					p1 = points [vd];
					break searchDownstream;
				}else {
//					if(showGUIs)	progress.notifyMessage("not in boundary", ProgressDialog.ERROR);
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
}//end main class