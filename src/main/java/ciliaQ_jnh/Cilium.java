package ciliaQ_jnh;
/** ===============================================================================
 * CiliaQ, a plugin for imagej - Version 0.0.7
 * 
 * Copyright (C) 2017-2023 Jan Niklas Hansen
 * First version: June 30, 2017  
 * This Version: May 07, 2023
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 *    
 * For any questions please feel free to contact me (jan.hansen@uni-bonn.de).
* =============================================================================== */

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Locale;

import ciliaQ_skeleton_analysis.AnalyzeSkeleton_;
import ciliaQ_skeleton_analysis.Point;
import ciliaQ_skeleton_analysis.SkeletonResult;
import ciliaQ_skeletonize3D.Skeletonize3D_;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.WaitForUserDialog;
import ij.measure.Calibration;
class Cilium{
	boolean excluded = false;
	
	Calibration cal;
	double calibration, voxelDepth;
	int bitDepth;
		
	double xC = 0.0, yC = 0.0, zC = 0.0;
	int t; //0 <= t < nFrames
	double [][] points;	// [pointID][0=x,1=y,2=z,3=intensity,4=coveredSurface, 5 = C2, 6 = C3]
	
	int voxels = 0, 
		surfaceVoxels = 0,
		xMin = Integer.MAX_VALUE,
		yMin = Integer.MAX_VALUE,
		zMin = Integer.MAX_VALUE,
		xMax = 0,
		yMax = 0,
		zMax = 0;
	
	double volume = 0.0,
		surface = 0.0,
		sphereRadius,
		shapeComplexity,
		maximumSpan = -1.0,	//TODO - method to be implemented
		
		colocalizedVolumeC2 = 0.0,
		colocalizedFractionC2 = 0.0,
		colocalizedVolumeC3 = 0.0,
		colocalizedFractionC3 = 0.0,
		
		colocalizedCompToBGVolumeC2 = 0.0,
		colocalizedCompToBGFractionC2 = 0.0,
		colocalizedCompToBGVolumeC3 = 0.0,
		colocalizedCompToBGFractionC3 = 0.0,
				
		maxCiliumIntensity = 0.0,
		maxTenPercentCiliumIntensity = 0.0,
		minCiliumIntensity = Double.POSITIVE_INFINITY,
		averageCiliumIntensity = 0.0,
		SDCiliumIntensity = 0.0,
		
		maxC2Intensity = 0.0,
		maxTenPercentC2Intensity = 0.0,
		minC2Intensity = Double.POSITIVE_INFINITY,
		averageC2Intensity = 0.0,
		SDC2Intensity = 0.0,
				
		maxC3Intensity = 0.0,
		maxTenPercentC3Intensity = 0.0,
		minC3Intensity = Double.POSITIVE_INFINITY,
		averageC3Intensity = 0.0,
		SDC3Intensity = 0.0;
	
	//Basal Body parameters (if applicable)
	boolean bbAvailable = false;
	int bbX, bbY, bbZ;
	double bbCenterIntensityC2, bbCenterIntensityC3;
	double bbIntensityRadius1C2, bbIntensityRadius1C3;
	double bbIntensityRadius2C2, bbIntensityRadius2C3;
	
	//Skeleton-related parameters
//	double [][] skeletonPoints;
	int foundSkl = 0,
		branches = 0;
	double treeLength = 0.0,
		largestShortestPathOfLargest;
	public boolean sklAvailable = false;
	private ArrayList<SklPoint> sklPointList; 	// point list in calibrated coordinates, referring to the original image
	
	double arcLength [];
	double profileC2 [], profileC3 [];
	double profileC2norm [], profileC3norm [];
	
	double orientationVector [];
	double bendingIndex = Double.NaN;
	
	public Cilium(ArrayList<CellPoint> ciliaPoints, ImagePlus imp, 
			boolean measureC2, int channel2, boolean measureC3, int channel3, boolean measureBasalBody, int channelBasalBody,
			int channelReconstruction, double gXY, double gZ, double intensityThresholds [], ProgressDialog progress,
			boolean skeletonize, boolean showGUIs){
		
		bitDepth = imp.getBitDepth();
		cal = imp.getCalibration().copy();
		
		calibration = cal.pixelWidth; 
		voxelDepth = cal.pixelDepth;
				
		voxels = ciliaPoints.size();
		volume = voxels * calibration * calibration * voxelDepth;
		sphereRadius = Math.pow((double)((volume*3.0)/(4.0*Math.PI)), (double)1/3.0);
		
		points = new double [voxels][7];	
		//[pointID][0=x,1=y,2=z,3=intensity,4=surface, 5= C2 intensity, 6 = C3 intensity]
		
		{
			CellPoint p;
			for(int i = 0; i < voxels; i++){
				Arrays.fill(points [i], 0.0);
				
				p = ciliaPoints.get(i);
				if(i==0){
					t = p.t;
				}else{
					if(t!=p.t){
						if(showGUIs) {
							progress.notifyMessage("Cilium not correctly assembled - inhomogeneous time", ProgressDialog.ERROR);
						}else {
							System.out.println("Cilium not correctly assembled - inhomogeneous time");
						}
					}
				}
				points [i][0] = p.x * calibration;
				xC += points [i][0];
				if(p.x < xMin) xMin = p.x;
				if(p.x > xMax) xMax = p.x;
				
				points [i][1] = p.y * calibration;
				yC += points [i][1];
				if(p.y < yMin) yMin = p.y;
				if(p.y > yMax) yMax = p.y;
				
				points [i][2] = p.z * voxelDepth;
				zC += points [i][2];
				if(p.z < zMin) zMin = p.z;
				if(p.z > zMax) zMax = p.z;
				
				points [i][3] = p.intensity;				
				if(points [i][3] > maxCiliumIntensity) maxCiliumIntensity = points [i][3];
				if(points [i][3] < minCiliumIntensity) minCiliumIntensity = points [i][3];			
				averageCiliumIntensity += points [i][3];
				
				if(measureC2){
					points [i][5] = imp.getStack().getVoxel(p.x, p.y, imp.getStackIndex(channel2, (p.z)+1, (p.t)+1)-1);
					if(points [i][5] > maxC2Intensity) maxC2Intensity = points [i][5];
					if(points [i][5] < minC2Intensity) minC2Intensity = points [i][5];
					averageC2Intensity += points [i][5];
					if(points [i][5] != 0.0){
						colocalizedVolumeC2 += calibration * calibration * voxelDepth;
						if(points [i][5] > intensityThresholds[channel2-1]){
							colocalizedCompToBGVolumeC2 += calibration * calibration * voxelDepth;
						}
					}					
				}
				
				if(measureC3){
					points [i][6] = imp.getStack().getVoxel(p.x, p.y, imp.getStackIndex(channel3, (p.z)+1, (p.t)+1)-1);
					if(points [i][6] > maxC3Intensity) maxC3Intensity = points [i][6];
					if(points [i][6] < minC3Intensity) minC3Intensity = points [i][6];
					averageC3Intensity += points [i][6];
					if(points [i][6] != 0.0){
						colocalizedVolumeC3 += calibration * calibration * voxelDepth;
						if(points [i][6] > intensityThresholds[channel3-1]){
							colocalizedCompToBGVolumeC3 += calibration * calibration * voxelDepth;
						}
					}					
				}
								
				points [i][4] = p.getSurface(calibration, voxelDepth);
				if(points [i][4] > 0.0){
					surfaceVoxels++;
					surface += points [i][4];
				}
			}
		}
		
		xC /= (double) voxels;
		yC /= (double) voxels;
		zC /= (double) voxels;
		averageCiliumIntensity /= (double) voxels;
			
		if(measureC2){
			averageC2Intensity /= (double) voxels;
			colocalizedFractionC2 = colocalizedVolumeC2 / volume;
			colocalizedCompToBGFractionC2 = colocalizedCompToBGVolumeC2 / volume;
		}
		if(measureC3){
			averageC3Intensity /= (double) voxels;
			colocalizedFractionC3 = colocalizedVolumeC3 / volume;
			colocalizedCompToBGFractionC3 = colocalizedCompToBGVolumeC3 / volume;
		}		
		
		//Determine diffusity
		{
			double sphereSurface = (Math.PI * Math.pow(sphereRadius,2) * 4);
			shapeComplexity = (double)surface/sphereSurface;	
		}	
		
		for(int i = 0; i < voxels; i++){
			SDCiliumIntensity += Math.pow(points [i][3] - averageCiliumIntensity, 2.0);			
			if(measureC2){
				SDC2Intensity += Math.pow(points [i][5] - averageC2Intensity, 2.0);
			}
			if(measureC3){
				SDC3Intensity += Math.pow(points [i][6] - averageC3Intensity, 2.0);
			}
		}
		SDCiliumIntensity /= voxels-1.0;
		SDCiliumIntensity = Math.sqrt(SDCiliumIntensity);			
		if(measureC2){
			SDC2Intensity /= voxels-1.0;
			SDC2Intensity = Math.sqrt(SDC2Intensity);
		}					
		if(measureC3){
			SDC3Intensity /= voxels-1.0;
			SDC3Intensity = Math.sqrt(SDC3Intensity);
		}
			
		
		//determine skeletonData
		orientationVector = new double [3];
		Arrays.fill(orientationVector, Double.NaN);
		if(skeletonize){
			this.reconstructSkeleton(gXY, gZ, measureBasalBody, channelBasalBody, imp, 
					measureC2, channel2, measureC3, channel3, channelReconstruction, progress, showGUIs);
		}
		
		//determine maxTenPercent Data
		this.determineMaxTenPercentIntensityValues(measureC2, measureC3);
	}
		
	/**
	 * Determines skeleton data (3D)
	 * */
	private void reconstructSkeleton(double gXY, double gZ, boolean measureBasalBody, int basalBodyC, ImagePlus imp, 
			boolean measureC2, int channel2, boolean measureC3, int channel3, int channelReconstruction,
			ProgressDialog progress, boolean showGUIs){
		int width = xMax - xMin + 1 + 4 + (int) Math.round(gXY*5.0),
			height = yMax - yMin + 1 + 4 + (int) Math.round(gXY*5.0),
			slices = zMax - zMin + 1 + 2 + (int) Math.round(gZ*5.0);
		
		//generate a binary image to calculate skeleton parameters
		//TODO include intensity? currently neglected
		
		ImagePlus particleImp;		
		if(zMax - zMin == 0) {
			particleImp = IJ.createImage("Particle image", "8-bit", width, height, 1, 1, 1);
			particleImp.setCalibration(cal);
			for(int i = 0; i < voxels; i++){
				particleImp.getStack().setVoxel((int)Math.round(points [i][0] / calibration) - xMin + 2 + (int) Math.round(gXY*2.5),
						(int)Math.round(points [i][1] / calibration) - yMin + 2 + (int) Math.round(gXY*2.5),
						0,
						255.0);
			}			
				
//			particleImp.show();
//			new WaitForUserDialog("Test 1!").show();
			
			//Upscale image for higher precision
			{
//				particleImp.show();
//				new WaitForUserDialog("Test").show();
//				particleImp.hide();
				
				ScalerJNH scaler = new ScalerJNH();
				particleImp = scaler.getScaled(particleImp, 3.0, 3.0, 1.0);
				
//				particleImp.show();
//				new WaitForUserDialog("Test").show();
//				particleImp.hide();
			}
			
//			particleImp.show();
//			new WaitForUserDialog("Pre Gauss!").show();
//			particleImp.hide();
			
			//Gaussfilter
				if(!(gXY == 0)){
					particleImp.getProcessor().blurGaussian(3.0*gXY);
//					particleImp.show();
//					new WaitForUserDialog("Post Gauss!").show();
//					particleImp.hide();
				}							
			// Gaussfilter
		}else{
			particleImp = IJ.createImage("Particle image", "8-bit", width, height, 1, slices, 1);
			particleImp.setCalibration(cal);
			for(int i = 0; i < voxels; i++){
				particleImp.getStack().setVoxel((int)Math.round(points [i][0] / calibration) - xMin + 2 + (int) Math.round(gXY*2.5),
						(int)Math.round(points [i][1] / calibration) - yMin + 2 + (int) Math.round(gXY*2.5),
						(int)Math.round(points [i][2] / voxelDepth) - zMin + 1 + (int) Math.round(gZ*2.5),
						255.0);
			}
						
//			particleImp.show();
//			new WaitForUserDialog("Test 1!").show();
			
			//Upscale image for higher precision
			{
//				particleImp.show();
//				new WaitForUserDialog("Test").show();
//				particleImp.hide();
				
				ScalerJNH scaler = new ScalerJNH();
				particleImp = scaler.getScaled(particleImp, 3.0, 3.0, 3.0);
				
//				particleImp.show();
//				new WaitForUserDialog("Test").show();
//				particleImp.hide();
			}
			
//			particleImp.show();
//			new WaitForUserDialog("Pre Gauss!").show();
//			particleImp.hide();
			
			//Gaussfilter
				if(!(gXY == 0 && gZ == 0)){
					DecimalFormat gaussformat = new DecimalFormat("#0.0");
					gaussformat.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));
					IJ.run(particleImp, "Gaussian Blur 3D...", "x=" + gaussformat.format(3*gXY) + " y=" + gaussformat.format(3*gXY) + " z=" + gaussformat.format(3*gZ));
//					particleImp.show();
//					new WaitForUserDialog("Post Gauss!").show();
//					particleImp.hide();
				}							
			// Gaussfilter
		}
			
		Skeletonize3D_ skelProc = new Skeletonize3D_();
		skelProc.setup("", particleImp);
		skelProc.run(particleImp.getProcessor());
//		
//		particleImp.show();
//		new WaitForUserDialog("Test 3!").show();
//		particleImp.hide();
		
		AnalyzeSkeleton_ skel = new AnalyzeSkeleton_();
		skel.calculateShortestPath = true;
		skel.setup("", particleImp);
		
		//Hints for programming: run(int pruneIndex, boolean pruneEnds, boolean shortPath, ImagePlus origIP, boolean silent, boolean verbose)
		SkeletonResult sklRes = skel.run(AnalyzeSkeleton_.NONE, false, true, null, true, false);
		foundSkl = sklRes.getNumOfTrees();
		if(foundSkl == 1){
			ArrayList<ciliaQ_skeleton_analysis.Point>[] shortestPath = skel.getShortestPathPoints();	//Skl Points are integers
			
			//From v0.1.6 on, for simplicity, this command gives the coordinates matching to the original image
			sklPointList = getSortedList(shortestPath, sklRes, measureBasalBody, basalBodyC, imp, progress, particleImp, gXY, gZ);
		}		
		if(foundSkl == 1 && sklPointList != null){
			sklAvailable = true;
			sklPointList.trimToSize();	
			
			arcLength = new double [sklPointList.size()];
			profileC2 = new double [sklPointList.size()];
			profileC3 = new double [sklPointList.size()];
			profileC2norm = new double [sklPointList.size()];
			profileC3norm = new double [sklPointList.size()];
								
			arcLength [0] = 0.0;
			for(int i = 0; i < sklPointList.size(); i++){				
				if(i!=0){
					arcLength [i] = arcLength [i-1] + getDistance(sklPointList.get(i),sklPointList.get(i-1));					
				}
//				if(showGUIs)	progress.notifyMessage("x	"+ sklPointList.get(i).x + "	y	" + sklPointList.get(i).y + "	z	" + sklPointList.get(i).z 
//					+ "	al	" + i + ":	" + arcLength [i], ProgressDialog.LOG);
				
				if(measureC2){
					profileC2 [i] = this.getInterpolatedIntensity2D(imp, sklPointList.get(i), channel2, progress);
//					if(showGUIs)	progress.notifyMessage("C2: " + profileC2[i] + "; imp " + imp.getCalibration().pixelWidth 
//							+ " - " + imp.getCalibration().pixelHeight + " - " + imp.getCalibration().pixelDepth, ProgressDialog.LOG);
					if(profileC2 [i] >= Double.NaN){
						profileC2norm [i] = profileC2 [i] / this.getInterpolatedIntensity2D(imp, sklPointList.get(i), channelReconstruction, progress);						
					}
//					if(showGUIs)	progress.notifyMessage("C2: " + profileC2[i] + "; imp " + imp.getCalibration().pixelWidth 
//							+ " - " + imp.getCalibration().pixelHeight + " - " + imp.getCalibration().pixelDepth, ProgressDialog.LOG);
					
				}
				if(measureC3){
					profileC3 [i] = this.getInterpolatedIntensity2D(imp, sklPointList.get(i), channel3, progress);
//					if(showGUIs)	progress.notifyMessage("C3: " + profileC3[i] + "; imp " + imp.getCalibration().pixelWidth 
//							+ " - " + imp.getCalibration().pixelHeight + " - " + imp.getCalibration().pixelDepth, ProgressDialog.LOG);
					if(profileC3 [i] >= Double.NaN){
						profileC3norm [i] = profileC3 [i] / this.getInterpolatedIntensity2D(imp, sklPointList.get(i), channelReconstruction, progress);
					}
//					if(showGUIs)	progress.notifyMessage("C3: " + profileC3[i] + "; imp " + imp.getCalibration().pixelWidth 
//							+ " - " + imp.getCalibration().pixelHeight + " - " + imp.getCalibration().pixelDepth, ProgressDialog.LOG);
				}
			}
			
			int [] sBranches = sklRes.getBranches();
			double [] sAvBrL = sklRes.getAverageBranchLength();
			ArrayList<Double> lst = sklRes.getShortestPathList();
			
			double maxValue = 0.0;
			int largestSklID = 0;
			for(int i = 0; i < foundSkl; i++){
				if(lst.get(i)>maxValue){
					maxValue = lst.get(i);
					largestSklID = i;
				}
			}
			branches += sBranches [largestSklID];
			treeLength += sAvBrL[largestSklID] * sBranches[largestSklID];
			largestShortestPathOfLargest += lst.get(largestSklID);
			
			orientationVector [0] = sklPointList.get(sklPointList.size()-1).x - sklPointList.get(0).x;
			orientationVector [1] = sklPointList.get(sklPointList.size()-1).y - sklPointList.get(0).y;
			orientationVector [2] = sklPointList.get(sklPointList.size()-1).z - sklPointList.get(0).z;
			
			//arcLength / eucledian distance of first to last point
			bendingIndex = arcLength[arcLength.length-1] 
					/ Math.sqrt(Math.pow(orientationVector [0], 2.0) + Math.pow(orientationVector [1], 2.0) + Math.pow(orientationVector [2], 2.0));
		}
		
		particleImp.changes = false;
		particleImp.close();
	}
	
	/**
	 * Adds a basal body to cilium, compute BB parameters, readjust the cilium length accordingly
	 * In turn the length and profile is going to be adjusted based on the BB coordinate
	 * @param pointBB: The PartPoint describing the center of the basal body in the original image, in Pixel/Voxel
	 * @param imp: The input image
	 * @param measureC2: from user settings, referring to whether intensity in channel 2 (= "A") shall be measured
	 * @param channel2: from user settings, describing the channel ID for channel 2 = "channel A" (>=1, <= number of channels)
	 * @param measureC3: from user settings, referring to whether intensity in channel 3 (= "B") shall be measured
	 * @param channel3: from user settings, describing the channel ID for channel 3 = "channel B" (>=1, <= number of channels)
	 * @param channelReconstruction: from user settings, describing the channel ID for the channel with the segmented cilia
	 * @param progress: The ProgressDialog attached to this
	 * @param showGUIs: Whether ProgressDialog is used or not (boolean)
	 * @return true if successful, otherwise returns false
	 * 
	 * New in CiliaQ v0.2.0
	 */
	public boolean addBasalBody(PartPoint pointBB,
			ImagePlus imp, 
			boolean measureC2, int channel2, 
			boolean measureC3, int channel3, 
			int channelReconstruction,
			ProgressDialog progress,
			boolean showGUIs) {
		
		this.bbX = pointBB.x;
		this.bbY = pointBB.y;
		this.bbZ = pointBB.z;
		
		if(!this.computeBBIntensityParameters(pointBB, imp, measureC2, channel2, measureC3, channel3, channelReconstruction, progress, showGUIs)) {
			System.out.println("BB Intensity parameters incomplete.");
		}
		
		if(!this.addBBToSkeleton(pointBB, imp, measureC2, channel2, measureC3, channel3, channelReconstruction, progress, showGUIs)) {
			System.out.println("BB Skeleton parameters incomplete.");
		}
		
		bbAvailable = true;
		
		return true;
	}
	
	/**
	 * Determine basal body Intensity parameters
	 * @param pointBB: The PartPoint describing the center of the basal body in the original image, in Pixel/Voxel
	 * @param imp: The input image
	 * @param measureC2: from user settings, referring to whether intensity in channel 2 (= "A") shall be measured
	 * @param channel2: from user settings, describing the channel ID for channel 2 = "channel A" (>=1, <= number of channels)
	 * @param measureC3: from user settings, referring to whether intensity in channel 3 (= "B") shall be measured
	 * @param channel3: from user settings, describing the channel ID for channel 3 = "channel B" (>=1, <= number of channels)
	 * @param channelReconstruction: from user settings, describing the channel ID for the channel with the segmented cilia
	 * @param progress: The ProgressDialog attached to this
	 * @param showGUIs: Whether ProgressDialog is used or not (boolean)
	 * @return true if successful, otherwise returns false 
	 * 
	 * New in CiliaQ v0.2.0
	 */
	private boolean computeBBIntensityParameters(PartPoint pointBB,
			ImagePlus imp, 
			boolean measureC2, int channelC2, 
			boolean measureC3, int channelC3, 
			int channelReconstruction,
			ProgressDialog progress,
			boolean showGUIs) {
		
		if(measureC2) {
			this.bbCenterIntensityC2 = getInterpolatedIntensity2D(imp,
					new SklPoint (pointBB.x*calibration, pointBB.y*calibration, pointBB.z*calibration),
					channelC2, 
					progress);
			this.bbIntensityRadius1C2 = this.getIntensityWithinRadius(imp,
					channelC2, 
					pointBB.x*calibration, pointBB.y*calibration, pointBB.z*calibration,
					1.0);			
			this.bbIntensityRadius2C2 = this.getIntensityInRing(imp,
					channelC2, 
					pointBB.x*calibration, pointBB.y*calibration, pointBB.z*calibration,
					1.0, 2.0);
		}
		if(measureC3) {
			this.bbCenterIntensityC3 = getInterpolatedIntensity2D(imp,
					new SklPoint (pointBB.x*calibration, pointBB.y*calibration, pointBB.z*calibration),
					channelC3, 
					progress);
			this.bbIntensityRadius1C3 = this.getIntensityWithinRadius(imp,
					channelC3, 
					pointBB.x*calibration, pointBB.y*calibration, pointBB.z*calibration,
					1.0);
			this.bbIntensityRadius2C3 = this.getIntensityInRing(imp,
					channelC3, 
					pointBB.x*calibration, pointBB.y*calibration, pointBB.z*calibration,
					1.0, 2.0);
		}	
		
		return true;
	}
	
	/**
	 * Measure the average intensity within a circle around a point of interest
	 * New: August 20, 2024
	 * @param imp: The image to measure in.
	 * @param channel: the channel to measure in. 1 <= channel <= nr of channels.
	 * @param pointX: the x coordinate of the point to measure around, specified in calibrated units.
	 * @param pointY: the y coordinate of the point to measure around, specified in calibrated units.
	 * @param pointZ: the z/slice coordinate of the point to measure around, specified in calibrated units.
	 * @param radius: the radius around the point to measure within, specified in calibrated units.
	 * @return the average pixel intensity within the radius specified
	 * 
	 * New in CiliaQ v0.2.0
	 */
	private double getIntensityWithinRadius(ImagePlus imp, int channel, double pointX, double pointY, double pointZ, double radius) {
		double intensity = 0.0;
		int ct = 0;
		for(int iz = (int) Math.round((pointZ - radius)/ voxelDepth); iz <= (int) Math.round((pointZ + radius)/ voxelDepth); iz++){
			for(int ix = (int) Math.round((pointX - radius)/ calibration); ix <= (int) Math.round((pointX + radius)/ calibration); ix++){
				for(int iy = (int) Math.round((pointY - radius)/ calibration); iy <= (int) Math.round((pointY + radius)/ calibration); iy++){
					if(getDistance(new SklPoint(ix*calibration,iy*calibration,iz*voxelDepth), new SklPoint(pointX,pointY,pointZ)) <= radius){
						if(ix >= 0 && ix < imp.getWidth() && iy >= 0 && iy < imp.getHeight() 
								&& imp.getStackIndex(channel, iz+1, t+1) >= 0 
								&& imp.getStackIndex(channel, iz+1, t+1) < imp.getStackSize()){
							
							// TEMPORARY LOG FOR CHECKING - TODO REMOVE
							IJ.log("Measure around [" + pointX + "," + pointY + "," + pointZ 
									+ "]: Add coordinate [" + ix + "," + iy + "," + iz 
									+ "] = [" + ix*calibration + "," + iy*calibration + "," + iz*voxelDepth + "] with intensity " 
									+ imp.getStack().getVoxel(ix,iy,imp.getStackIndex(channel, iz+1, t+1)-1)
									+ " (Radius: " + radius + ")");
							
							intensity += imp.getStack().getVoxel(ix,iy,imp.getStackIndex(channel, iz+1, t+1)-1);
							ct++;
						}							
					}
				}
			}
		}
		intensity /= (double)ct;
	
		return intensity;
	}
	
	/**
	 * Measure the average intensity within a circular ring around a point of interest
	 * New: August 20, 2024
	 * @param imp: The image to measure in.
	 * @param channel: the channel to measure in. 1 <= channel <= nr of channels.
	 * @param pointX: the x coordinate of the point to measure around, specified in calibrated units.
	 * @param pointY: the y coordinate of the point to measure around, specified in calibrated units.
	 * @param pointZ: the z/slice coordinate of the point to measure around, specified in calibrated units.
	 * @param innerRadius: the minimum radius for pixels to be included in average, specified in calibrated units.
	 * @param outerRadius: the maximum radius for pixels to be included in average, specified in calibrated units.
	 * @return the average pixel intensity within the radius specified
	 * 
	 * New in CiliaQ v0.2.0
	 */
	private double getIntensityInRing(ImagePlus imp, int channel, 
			double pointX, double pointY, double pointZ,
			double innerRadius, double outerRadius) {
		double intensity = 0.0;
		int ct = 0;
		for(int iz = (int) Math.round((pointZ - outerRadius)/ voxelDepth); iz <= (int) Math.round((pointZ + outerRadius)/ voxelDepth); iz++){
			for(int ix = (int) Math.round((pointX - outerRadius)/ calibration); ix <= (int) Math.round((pointX + outerRadius)/ calibration); ix++){
				for(int iy = (int) Math.round((pointY - outerRadius)/ calibration); iy <= (int) Math.round((pointY + outerRadius)/ calibration); iy++){
					if(getDistance(new SklPoint(ix*calibration,iy*calibration,iz*voxelDepth), new SklPoint(pointX,pointY,pointZ)) >= innerRadius){
						if(getDistance(new SklPoint(ix*calibration,iy*calibration,iz*voxelDepth), new SklPoint(pointX,pointY,pointZ)) <= outerRadius){
							if(ix >= 0 && ix < imp.getWidth() && iy >= 0 && iy < imp.getHeight() 
									&& imp.getStackIndex(channel, iz+1, t+1) >= 0 
									&& imp.getStackIndex(channel, iz+1, t+1) < imp.getStackSize()){
								
								// TEMPORARY LOG FOR CHECKING - TODO REMOVE
								IJ.log("Measure in ring around [" + pointX + "," + pointY + "," + pointZ 
										+ "]: Add coordinate [" + ix + "," + iy + "," + iz 
										+ "] = [" + ix*calibration + "," + iy*calibration + "," + iz*voxelDepth + "] with intensity " 
										+ imp.getStack().getVoxel(ix,iy,imp.getStackIndex(channel, iz+1, t+1)-1)
										+" (Radii: " + innerRadius + ", " + outerRadius + ")");
								
								intensity += imp.getStack().getVoxel(ix,iy,imp.getStackIndex(channel, iz+1, t+1)-1);
								ct++;
							}
						}							
					}
				}
			}
		}
		intensity /= (double)ct;
	
		return intensity;
	}
	
	/**
	 * Adds the basal body to the length skeleton and recalculates results.
	 * @param pointBB: The PartPoint describing the center of the basal body in the original image, in Pixel/Voxel
	 * @param imp: The input image
	 * @param measureC2: from user settings, referring to whether intensity in channel 2 (= "A") shall be measured
	 * @param channel2: from user settings, describing the channel ID for channel 2 = "channel A" (>=1, <= number of channels)
	 * @param measureC3: from user settings, referring to whether intensity in channel 3 (= "B") shall be measured
	 * @param channel3: from user settings, describing the channel ID for channel 3 = "channel B" (>=1, <= number of channels)
	 * @param channelReconstruction: from user settings, describing the channel ID for the channel with the segmented cilia
	 * @param progress: The ProgressDialog attached to this
	 * @param showGUIs: Whether ProgressDialog is used or not (boolean)
	 * @return true if successful, otherwise returns false
	 * 
	 * New in CiliaQ v0.2.0
	 */
	private boolean addBBToSkeleton(PartPoint pointBB, 
			ImagePlus imp, 
			boolean measureC2, int channel2, 
			boolean measureC3, int channel3, 
			int channelReconstruction,
			ProgressDialog progress,
			boolean showGUIs){
		if(foundSkl !=1) {
			/*
			 * No skeleton available. Thus, we create one based on the basal body coordinate and the center of the cilium.
			 */
			double [] centerCoord = new double [] {xC*calibration, yC*calibration, zC*voxelDepth};
			double sklBBDistance = getDistanceInPx(pointBB,centerCoord);
			
			sklPointList = new ArrayList<SklPoint>((int)Math.round(sklBBDistance*3.0)+1);
			for(int p = 0; p < sklBBDistance*3; p++) {
				sklPointList.add(new SklPoint(centerCoord[0] + p / (sklBBDistance*3.0) * (pointBB.x*calibration-centerCoord[0]),
						centerCoord[1] + p / (sklBBDistance*3.0) * (pointBB.y*calibration-centerCoord[1]),
						centerCoord[2] + p / (sklBBDistance*3.0) * (pointBB.z*voxelDepth-centerCoord[2])));
			}
			sklPointList.add(new SklPoint(pointBB.x * calibration,pointBB.y * calibration,pointBB.z * voxelDepth));			
			Collections.reverse(sklPointList);
			sklAvailable = true;
			
			largestShortestPathOfLargest = getRealDistance(pointBB,centerCoord); // This corresponds to the parameter called length in output file
		}else if(foundSkl == 1 && sklPointList != null){
			/*
			 * Skeleton is available, so we add basal body to it.
			 */
			sklPointList.trimToSize();
			
			/*
			 *  Check which point is closer to BB, begin or end.
			 *  If end closer then we do not need to reverse it now that we are adding the BB to the end.
			 *  Otherwise we need to reverse here.
			 *  The list is reversed back at the end when BB has been added to end.
			 */
			if(getRealDistance(pointBB,this.getFirstSkeletonCoordinate()) < getRealDistance(pointBB,this.getLastSkeletonCoordinate()) ){
				Collections.reverse(sklPointList);
			}
									
			/**
			 * Add to the end of the point list points between first skeleton point and basal body
			 */
			double [] lastCoord = getLastSkeletonCoordinate();
			double sklBBDistance = getDistanceInPx(pointBB,lastCoord);
			sklPointList.ensureCapacity(sklPointList.size()+(int)Math.round(sklBBDistance*3.0));
			for(int p = 1; p < sklBBDistance*3; p++) {
				sklPointList.add(new SklPoint(lastCoord[0] + p / (sklBBDistance*3.0) * (pointBB.x*calibration-lastCoord[0]),
						lastCoord[1] + p / (sklBBDistance*3.0) * (pointBB.y*calibration-lastCoord[1]),
						lastCoord[2] + p / (sklBBDistance*3.0) * (pointBB.z*voxelDepth-lastCoord[2])));
			}
			sklPointList.add(new SklPoint(pointBB.x * calibration,pointBB.y * calibration,pointBB.z * voxelDepth));		
			sklPointList.trimToSize();	
			Collections.reverse(sklPointList);
			sklAvailable = true;
			
			largestShortestPathOfLargest += getRealDistance(pointBB,lastCoord); // This corresponds to the parameter called length in output file
		}else {
			return false;
		}
				
		/**
		 * Recalculating the skeleton results
		 */		
		arcLength = new double [sklPointList.size()];
		profileC2 = new double [sklPointList.size()];
		profileC3 = new double [sklPointList.size()];
		profileC2norm = new double [sklPointList.size()];
		profileC3norm = new double [sklPointList.size()];
							
		arcLength [0] = 0.0;
		for(int i = 0; i < sklPointList.size(); i++){				
			if(i!=0){
				arcLength [i] = arcLength [i-1] + getDistance(sklPointList.get(i),sklPointList.get(i-1));					
			}
//				if(showGUIs)	progress.notifyMessage("x	"+ sklPointList.get(i).x + "	y	" + sklPointList.get(i).y + "	z	" + sklPointList.get(i).z 
//					+ "	al	" + i + ":	" + arcLength [i], ProgressDialog.LOG);
			
			if(measureC2){
				profileC2 [i] = this.getInterpolatedIntensity2D(imp, sklPointList.get(i), channel2, progress);
//					if(showGUIs)	progress.notifyMessage("C2: " + profileC2[i] + "; imp " + imp.getCalibration().pixelWidth 
//							+ " - " + imp.getCalibration().pixelHeight + " - " + imp.getCalibration().pixelDepth, ProgressDialog.LOG);
				if(profileC2 [i] >= Double.NaN){
					profileC2norm [i] = profileC2 [i] / this.getInterpolatedIntensity2D(imp, sklPointList.get(i), channelReconstruction, progress);						
				}
//					if(showGUIs)	progress.notifyMessage("C2: " + profileC2[i] + "; imp " + imp.getCalibration().pixelWidth 
//							+ " - " + imp.getCalibration().pixelHeight + " - " + imp.getCalibration().pixelDepth, ProgressDialog.LOG);
				
			}
			if(measureC3){
				profileC3 [i] = this.getInterpolatedIntensity2D(imp, sklPointList.get(i), channel3, progress);
//					if(showGUIs)	progress.notifyMessage("C3: " + profileC3[i] + "; imp " + imp.getCalibration().pixelWidth 
//							+ " - " + imp.getCalibration().pixelHeight + " - " + imp.getCalibration().pixelDepth, ProgressDialog.LOG);
				if(profileC3 [i] >= Double.NaN){
					profileC3norm [i] = profileC3 [i] / this.getInterpolatedIntensity2D(imp, sklPointList.get(i), channelReconstruction, progress);
				}
//					if(showGUIs)	progress.notifyMessage("C3: " + profileC3[i] + "; imp " + imp.getCalibration().pixelWidth 
//							+ " - " + imp.getCalibration().pixelHeight + " - " + imp.getCalibration().pixelDepth, ProgressDialog.LOG);
			}
		}
				
		orientationVector [0] = sklPointList.get(sklPointList.size()-1).x - sklPointList.get(0).x;
		orientationVector [1] = sklPointList.get(sklPointList.size()-1).y - sklPointList.get(0).y;
		orientationVector [2] = sklPointList.get(sklPointList.size()-1).z - sklPointList.get(0).z;
		
		//arcLength / eucledian distance of first to last point
		bendingIndex = arcLength[arcLength.length-1] 
				/ Math.sqrt(Math.pow(orientationVector [0], 2.0) + Math.pow(orientationVector [1], 2.0) + Math.pow(orientationVector [2], 2.0));
		
		return true;
	}
	
	
	/**
	 * @return a double array containing the x,y,z,t coordinate in of the first length/profile/skeleton point stored 
	 * x,y,z will be in metric units (e.g. micron)
	 * t will be in frames (integer)
	 * 
	 * New in CiliaQ v0.2.0
	 */
	public double [] getFirstSkeletonCoordinate() {
		return new double [] {sklPointList.get(0).x,sklPointList.get(0).y,sklPointList.get(0).z,t};
	}

	/**
	 * @return a double array containing the x,y,z,t coordinate of the last length/profile/skeleton point stored  
	 * x,y,z will be in metric units (e.g. micron)
	 * t will be in frames (integer)
	 * 
	 * New in CiliaQ v0.2.0
	 */
	public double [] getLastSkeletonCoordinate() {
		return new double [] {sklPointList.get((sklPointList.size()-1)).x,sklPointList.get((sklPointList.size()-1)).y,sklPointList.get((sklPointList.size()-1)).z,t};
	}
	
	/**
	 * @return first array dimension: points; 
	 * second array dimension: 0 = x, 1 = y, 2 = z
	 * */
	public double [][] getSkeletonPointsForOriginalImage (){
		double [][] output = new double [sklPointList.size()][3];
		for(int i = 0; i < sklPointList.size(); i++){
			output [i][0] = sklPointList.get(i).x;
			output [i][1] = sklPointList.get(i).y;
			output [i][2] = sklPointList.get(i).z;
		}
		return output;
	}
	
	/**
	 * @return first array dimension: points; 
	 * second array dimension: 0 = x, 1 = y, 2 = z
	 * The values will be in voxel units and not in the calibrated units 
	 * */
	public int [][] getSkeletonPointsForOriginalImageInPixel (){
		int [][] output = new int [sklPointList.size()][3];
		for(int i = 0; i < sklPointList.size(); i++){
			output [i][0] = (int)Math.round(sklPointList.get(i).x / calibration);
			output [i][1] = (int)Math.round(sklPointList.get(i).y / calibration);
			output [i][2] = (int)Math.round(sklPointList.get(i).z / voxelDepth);
		}
		return output;
	}
	
	/**
	 * Determine the Intensity of the Ten Percent of voxels with Max Intensity
	 * */
	private void determineMaxTenPercentIntensityValues(boolean measureC2, boolean measureC3){
		double values [] = getColumnOf2DArray(points, 3);
		maxTenPercentCiliumIntensity = getMaxTenPercent(values);
		if(measureC2){
			values = getColumnOf2DArray(points, 5);
			maxTenPercentC2Intensity = getMaxTenPercent(values);
		}
		if(measureC3){
			values = getColumnOf2DArray(points, 6);
			maxTenPercentC3Intensity = getMaxTenPercent(values);
		}		
		values = null;
	}	
	
	
	/**
	 * @return List of SklPoints on the largest shortest path, sorted from the start to the end
	 * (x,y,z coordinates of the SklPoints are calibrated, so indicated in the calibration Unit)
	 * (Since v0.1.6 on, for simplicity, this command gives the coordinates matching to the original image - before it referred to the particle image coordinates)
	 * */
	private ArrayList<SklPoint> getSortedList(ArrayList<Point>[] shortestPath, SkeletonResult sklRes, boolean measureBasalBody, int basalBodyC, ImagePlus imp,
			ProgressDialog progress, ImagePlus sklImp, double gXY, double gZ){
		if(shortestPath.equals(null) || shortestPath.length == 0){
			return null;
		}
		
		//find longest Skeleton
		int chosenShortestPath = 0;
		if(shortestPath.length!=1){
			double maxLength = 0.0;
			for(int i = 0; i < sklRes.getNumOfTrees(); i++){
				if(sklRes.getAverageBranchLength()[i]*sklRes.getBranches()[i] > maxLength){
					maxLength = sklRes.getAverageBranchLength()[i]*sklRes.getBranches()[i];
					chosenShortestPath = i;
				}
			}
		}
		
		//count points 
		int nPoints = shortestPath[chosenShortestPath].size();
		SklPoint startEnd = new SklPoint(Math.round(sklRes.getSpStartPosition()[chosenShortestPath][0] / calibration * 3.0),
				Math.round(sklRes.getSpStartPosition()[chosenShortestPath][1] / calibration * 3.0),
				Math.round(sklRes.getSpStartPosition()[chosenShortestPath][2] / voxelDepth * 3.0));
//		if(showGUIs)	progress.notifyMessage("start " + startEnd.x + " " + startEnd.y + " " + startEnd.z, ProgressDialog.LOG);
		if(nPoints == 0 || startEnd.equals(null)){
			return null;
		}
		
		//get trace list -  new method
		ArrayList<SklPoint> list = new ArrayList<SklPoint>(nPoints);
		ArrayList<SklPoint> unsortedList = new ArrayList<SklPoint>(nPoints);
		
		
		//save unsorted list
		SklPoint tempPoint;
		for(int i = 0; i < nPoints; i++){
			tempPoint = new SklPoint(shortestPath[chosenShortestPath].get(i));
			if(!(tempPoint.x == startEnd.x && tempPoint.y == startEnd.y && tempPoint.z == startEnd.z)){
				unsortedList.add(tempPoint);
			}
		}
		
		//create list of junction voxels
		ArrayList<SklPoint> junctions = new ArrayList<SklPoint>(sklRes.getListOfJunctionVoxels().size());
		for(int i = 0; i < sklRes.getListOfJunctionVoxels().size(); i++){
			junctions.add(new SklPoint(sklRes.getListOfJunctionVoxels().get(i)));
		}

		//create sortedList (list)
		{
			list.add(startEnd);
			int index = 0;
			double distance;
			SklPoint p;
			int pIndex;
			while(!unsortedList.isEmpty()){
				distance = Double.POSITIVE_INFINITY; 
				p = null;
				pIndex = -1;
				for(int i = 0; i < unsortedList.size(); i++){
					if(getDistance(unsortedList.get(i),list.get(index)) < distance){
						p = unsortedList.get(i);
						pIndex = i;
						distance = getDistance(unsortedList.get(i),list.get(index));
					}
				}
				if(distance <= constants.sqrt3){
					list.add(p);
					index++;
				}else{
					boolean check = true;
					for(int rest = 0; rest < unsortedList.size(); rest++){
						check = false;
						for(int jnc = 0; jnc < junctions.size(); jnc++){
							if(junctions.get(jnc).x == unsortedList.get(rest).x 
									&& junctions.get(jnc).y == unsortedList.get(rest).y 
									&& junctions.get(jnc).z == unsortedList.get(rest).z){
								check = true;
								break;
							}
						}
						if(check == false){
							break;
						}
					}
					if(check){
						//all remaining points not included are junction voxels and can be removed
//						if(showGUIs)	progress.notifyMessage("Info for expert users: rmp " + unsortedList.size() + " from profile.", ProgressDialog.LOG);
//						sklImp.show();
//						for(int uI = 0; uI < unsortedList.size(); uI++){
//							if(showGUIs)	progress.notifyMessage("Info for expert users: uL x " + unsortedList.get(uI).x 
//									+ " y " + unsortedList.get(uI).y
//									+ " z " + unsortedList.get(uI).z, ProgressDialog.LOG);
//						}
//						for(int uI = 0; uI < junctions.size(); uI++){
//							if(showGUIs)	progress.notifyMessage("Info for expert users: jnc x " + junctions.get(uI).x 
//									+ " y " + junctions.get(uI).y
//									+ " z " + junctions.get(uI).z, ProgressDialog.LOG);
//						}
//						for(int uI = 0; uI < list.size(); uI++){
//							if(showGUIs)	progress.notifyMessage("Info for expert users: sL x " + list.get(uI).x 
//									+ " y " + list.get(uI).y
//									+ " z " + list.get(uI).z, ProgressDialog.LOG);
//						}
//						new WaitForUserDialog("Check error").show();
//						sklImp.hide();
						break;
					}else{
						//listing needs to go on because there are still non junction voxels in the queue
						list.add(p);
						index++;
					}				
				}
				unsortedList.remove(pIndex);				
			}
		}
		unsortedList.clear();
		unsortedList = null;
		list.trimToSize();				

		//remove modification by upscaling
		for(int i = 0; i < list.size(); i++){
			list.get(i).x /= 3.0;
			list.get(i).y /= 3.0;
			list.get(i).z /= 3.0;			
			
			//Correct coordinates (remove extra added space in particle image)
			list.get(i).x += (xMin - 2 - (int) Math.round(gXY*2.5));
			list.get(i).y += (yMin - 2 - (int) Math.round(gXY*2.5));
			if(zMax - zMin == 0) {
				list.get(i).z += (zMin);		
			}else {
				list.get(i).z += (zMin - 1 - (int) Math.round(gZ*2.5));
			}
			
			//Calibrate coordinates
			list.get(i).x *= calibration;
			list.get(i).y *= calibration;
			list.get(i).z *= voxelDepth;			
		}
		
		SklPoint pFirst = new SklPoint(list.get(0));
		SklPoint pLast = new SklPoint(list.get(list.size()-1));
				
		if(measureBasalBody){
			//get statistics for first point - method changed in v0.2.0 from 3 to 1 micron distance
			double intensitySumStart = this.getIntensityWithinRadius(imp, basalBodyC, 
					pFirst.x, pFirst.y, pFirst.z, 1.0);
												
			//get statistics for last point
			double intensitySumEnd =  this.getIntensityWithinRadius(imp, basalBodyC, 
					pLast.x, pLast.y, pLast.z, 1.0);
			
			//if first point is actually last point reverse list
			if(intensitySumStart < intensitySumEnd){
				ArrayList <SklPoint> newList = new ArrayList <SklPoint>(list.size());
				//invert list
				for(int i = list.size()-1; i >= 0; i--){	
					newList.add(list.get(i));	
				}					
				newList.trimToSize();
				list.clear();
				list = newList;
			}				
		}			
		return list;
	}

	/**
	 * @return 3D distance between two SklPoints with coordinates (x,y,z)
	 * @param p, SklPoint 1 (integer values for x,y,z)
	 * @param q, SklPoint 2	(integer values for x,y,z)
	 * */
	private double getDistance(SklPoint p, SklPoint q) {
		return Math.sqrt(Math.pow(p.x-q.x,2.0)+Math.pow(p.y-q.y,2.0)+Math.pow(p.z-q.z,2.0));
	}
		
	/**
	 * @return an intensity profile along the cilium with a defined spatial resolution
	 * @param channel (int, either 2 or 3)
	 * @param resolution (double, in micron)
	 * @param normalized (intensity FC normalized to the reconstruction channel)
	 * */
	public double [] getIntensityProfile(int channel, double resolution, boolean normalized){
		if(foundSkl == 0 || sklPointList == null){
			return null;
		}
		int arrayLength = 1+(int)Math.round(arcLength[arcLength.length-1]/resolution);
//		IJ.log("al " + arrayLength);
		double [] profile = new double [arrayLength];
		double [] counter = new double [arrayLength];
		Arrays.fill(profile, 0.0);
		Arrays.fill(counter, 0.0);
		
//		IJ.log("profl " + profile.length);
//		IJ.log("arcL old " + arcLength.length);
		for(int i = 0; i < arcLength.length; i++){
			int newI = (int)(arcLength[i]/resolution);
			double dist = (arcLength[i]/resolution) - ((double)((int)(arcLength[i]/resolution)));
			
			if(channel == 2){
//				IJ.log("pc2 " + profileC2.length);
				if(normalized){
					profile [newI] += profileC2norm [i] * (1-dist);
					counter [newI] += (1-dist);
					if(newI != arrayLength-1){
						profile [newI+1] += profileC2norm [i] * dist;
						counter [newI+1] += dist;
					}
				}else{
					profile [newI] += profileC2 [i] * (1-dist);
					counter [newI] += (1-dist);
					if(newI != arrayLength-1){
						profile [newI+1] += profileC2 [i] * dist;
						counter [newI+1] += dist;
					}
				}					
			}else if (channel == 3){
				if(normalized){
					profile [newI] += profileC3norm [i] * (1-dist);
					counter [newI] += (1-dist);
					if(newI != arrayLength-1){
						profile [newI+1] += profileC3norm [i] * dist;
						counter [newI+1] += dist;
					}
				}else{
					profile [newI] += profileC3 [i] * (1-dist);
					counter [newI] += (1-dist);
					if(newI != arrayLength-1){
						profile [newI+1] += profileC3 [i] * dist;
						counter [newI+1] += dist;
					}
				}
			}else{
				IJ.log("ERROR: Profile incorrectly returned...");
				return null;
			}				
		}
		
		for(int i = 0; i < profile.length; i++){
			if(counter[i] != 0.0){
				profile [i] /= counter[i];
			}		
//			IJ.log("profile " + i + ": " + profile [i]);
		}
		
		return profile;
	}
	
	private double getInterpolatedIntensity2D (ImagePlus imp, SklPoint p, int channel, ProgressDialog progress){
			if(channel>imp.getNChannels())	return -1.0;
				
	//		IJ.log("pos " + x + " " + y + " " + z);
			double x = (p.x / imp.getCalibration().pixelWidth);
			double y = (p.y / imp.getCalibration().pixelHeight);
			int z = imp.getStackIndex(channel, (int)Math.round(p.z / imp.getCalibration().pixelDepth)+1,t+1)-1;
			double intensity = 0.0;
			double leftComponent = 1-(x-(int)x), topComponent = 1-(y-(int)y);
			try{
				intensity += imp.getStack().getVoxel((int)x,(int)y,z) * leftComponent * topComponent;		//top left pixel
//				progress.notifyMessage("i1 " + intensity + " "
//						+ " + " + imp.getStack().getVoxel((int)x,(int)y,z)
//						+ "weight " + (leftComponent * topComponent), ProgressDialog.LOG);
				intensity += imp.getStack().getVoxel((int)x+1,(int)y,z) * (1.0-leftComponent) * topComponent;	//top right pixel
//				progress.notifyMessage("i2 " + intensity 
//						+ " + " + imp.getStack().getVoxel((int)x+1,(int)y,z)
//						+ " weight " + ((1.0-leftComponent) * topComponent), ProgressDialog.LOG);
				intensity += imp.getStack().getVoxel((int)x,(int)y+1,z) * leftComponent * (1.0-topComponent);	//bottom left pixel
//				progress.notifyMessage("i3 " + intensity 
//						+ " + " + imp.getStack().getVoxel((int)x,(int)y+1,z)
//						+ " weight " + (leftComponent * (1.0-topComponent)), ProgressDialog.LOG);
				intensity += imp.getStack().getVoxel((int)x+1,(int)y+1,z) * (1.0-leftComponent) * (1.0-topComponent);	//bottom right pixel
//				progress.notifyMessage("i4 " + intensity 
//						+ " + " + imp.getStack().getVoxel((int)x+1,(int)y+1,z)
//						+ " weight " + ((1.0-leftComponent) * (1.0-topComponent)), ProgressDialog.LOG);
			}catch (Exception e) {
//				progress.notifyMessage("Alternative voxel acquisition", ProgressDialog.LOG);
				try{
					intensity = imp.getStack().getVoxel((int)Math.round(x),(int)Math.round(y),z);
				}catch(Exception e2){
					try{
						if((int)Math.round(x)>= imp.getWidth()-1){
							intensity = imp.getStack().getVoxel((int)Math.round(x)-1,(int)Math.round(y),z);
						}else if((int)Math.round(x)<=0){
							intensity = imp.getStack().getVoxel((int)Math.round(x)+1,(int)Math.round(y),z);
						}else if((int)Math.round(y)>= imp.getHeight()-1){
							intensity = imp.getStack().getVoxel((int)Math.round(x),(int)Math.round(y)-1,z);
						}else if((int)Math.round(y)<=0){
							intensity = imp.getStack().getVoxel((int)Math.round(x),(int)Math.round(y)+1,z);						
						}else{
							intensity = Double.NaN;
						}
					}catch(Exception e3){
						intensity = Double.NaN;
					}					
				}
				
			}	
//			progress.notifyMessage("Comp " + leftComponent + " " + topComponent, ProgressDialog.LOG);
//			progress.notifyMessage("sum " + (leftComponent * topComponent+(1.0-leftComponent) * topComponent+leftComponent * (1.0-topComponent)+(1.0-leftComponent) * (1.0-topComponent)), ProgressDialog.LOG);
//			progress.notifyMessage("intensity " + intensity, ProgressDialog.LOG);
			return intensity;	
		}
	
	/**
	 * Create Image in non-timelapse mode
	 * */
	public ImagePlus getCiliumImp(double intensityThresholds [], int channelC2, int channelC3){
		int width = xMax - xMin + 1 + 4,
			height = yMax - yMin + 1 + 4,
			slices = zMax - zMin + 1 + 2;
		int xCorr = xMin,
			yCorr = yMin,
			zCorr = zMin;
		
		double maxValue = Math.pow(2.0,bitDepth);
		
		int nrOfChannels = 4;
		
		ImagePlus imp = IJ.createHyperStack("Cilium Image", width, height, nrOfChannels, slices, 1, bitDepth);
		imp.setCalibration(cal);
		imp.getCalibration().xOrigin = xCorr-2;
		imp.getCalibration().yOrigin = yCorr-2;
		imp.getCalibration().zOrigin = zCorr-1;
		imp.setDisplayMode(IJ.COMPOSITE);
		
		//draw cilium
		for(int i = 0; i < voxels; i++){
			//[pointID][0=x,1=y,2=z,3=intensity,4=surface, 5=coveredSurface, 6 = coloc (if so 1, else 0)]
			imp.getStack().setVoxel((int)Math.round(points [i][0] / calibration)+2-xCorr,
					(int)Math.round(points [i][1] / calibration)+2-yCorr, 
					imp.getStackIndex(1,(int)Math.round(points [i][2] / voxelDepth)+1-zCorr+1,1)-1,	//getStackIndex(int channel,int slice,int frame);
					points [i][3]);
		}
		imp.setC(1);	
		IJ.run(imp, "Cyan", "");
		imp.setDisplayRange(0, 4095);
		
		//draw skeleton
		if(foundSkl != 0 && sklPointList != null){
			for(int i = 0; i < sklPointList.size(); i++){
				imp.getStack().setVoxel((int)Math.round(sklPointList.get(i).x / calibration)+2-xCorr,
						(int)Math.round(sklPointList.get(i).y / calibration)+2-yCorr, 
						imp.getStackIndex(2,(int)Math.round(sklPointList.get(i).z / voxelDepth)+1-zCorr+1,1)-1,
						maxValue);
//				IJ.log("p" + sklPointList.get(i).x / calibration + "-" + sklPointList.get(i).y / calibration + "-" + sklPointList.get(i).z / voxelDepth);
			}
			imp.setC(2);	
			IJ.run(imp, "Grays", "");
			imp.setDisplayRange(0, 4095);
		}
		
		int cIndex = 3;
		if(minC2Intensity != Double.POSITIVE_INFINITY){
			for(int i = 0; i < voxels; i++){
				//[pointID][0=x,1=y,2=z,3=intensity,4=surface, 5=coveredSurface, 6 = coloc (if so 1, else 0)]
				imp.getStack().setVoxel((int)Math.round(points [i][0] / calibration)+2-xCorr,
						(int)Math.round(points [i][1] / calibration)+2-yCorr, 
						imp.getStackIndex(cIndex,(int)Math.round(points [i][2] / voxelDepth)+1-zCorr+1,1)-1,	//getStackIndex(int channel,int slice,int frame);
						points [i][5]);
			}
			imp.setC(cIndex);	
			IJ.run(imp, "Green", "");
			imp.setDisplayRange(0, 4095);
		}
		
		cIndex = 4;
		if(minC3Intensity != Double.POSITIVE_INFINITY){
			for(int i = 0; i < voxels; i++){
				//[pointID][0=x,1=y,2=z,3=intensity,4=surface, 5=coveredSurface, 6 = coloc (if so 1, else 0)]	
				int z = imp.getStackIndex(cIndex,(int)Math.round(points [i][2] / voxelDepth)+1-zCorr+1,1)-1;	//getStackIndex(int channel,int slice,int frame);
				imp.getStack().setVoxel((int)Math.round(points [i][0] / calibration)+2-xCorr,
						(int)Math.round(points [i][1] / calibration)+2-yCorr, z, points [i][6]);
			}
			imp.setC(cIndex);	
			IJ.run(imp, "Red", "");
			imp.setDisplayRange(0, 4095);
			cIndex++;
		}		
		
		//Possible colors
//			IJ.run(imp, "Red", "");
//			IJ.run(imp, "Green", "");
//			IJ.run(imp, "Blue", "");
//			IJ.run(imp, "Cyan", "");
//			IJ.run(imp, "Magenta", "");
//			IJ.run(imp, "Yellow", "");
//			IJ.run(imp, "Grays", "");
									
		String active = "11";
		for(int i = 3; i < cIndex; i++){
			active += "1";
		}
		imp.setActiveChannels(active);	//display all channels		
		return imp;
	}
	
	private static double getMaxTenPercent(double [] list){			
		double [] array = Arrays.copyOf(list, list.length);
		Arrays.sort(array);
		double maxTenPercent = getAverageOfRange(array,list.length-(int)Math.round(list.length/10.0), list.length-1);
		array = null;
		return maxTenPercent;
	}
	
	private static double getAverageOfRange(double [] values, int startIndex, int endIndex){
		double average = 0.0;	
		for(int x = startIndex; x <= endIndex; x++){
			average += values [x];
		}	
		return (average / (double)(endIndex-startIndex+1));		
	}
	
	private static double [] getColumnOf2DArray (double [][] array, int column){
		double [] colArray = new double [array.length];
		for(int i = 0; i < array.length; i++){
			colArray [i] = array [i][column];
		}
		return colArray;
	}
	
	/**
	 * Create Image
	 * used from 23.04.2019 in time-lapse mode
	 * */
	public ImagePlus getCiliumImpForTimelapse(double intensityThresholds [], int channelC2, int channelC3, 
			int xMaxT, int xMinT, int yMaxT, int yMinT, int zMaxT, int zMinT, ProgressDialog progress){
		int width = xMaxT - xMinT + 1 + 4,
			height = yMaxT - yMinT + 1 + 4,
			slices = zMaxT - zMinT + 1 + 2;
		int xCorr = xMinT,
			yCorr = yMinT,
			zCorr = zMinT;
		
//			int xCorrSkl = xMin - xMinT,
//				yCorrSkl = yMin - yMinT,
//				zCorrSkl = zMin - zMinT;
//			progress.notifyMessage("LOG: Corrections for Skeleton: t " + t + " x " + xCorrSkl + " y" + yCorrSkl + " z" + zCorrSkl, ProgressDialog.LOG);
		
		double maxValue = Math.pow(2.0,bitDepth);
		if(bitDepth==16) maxValue = Math.pow(2.0,12.0);
		
		int nrOfChannels = 4;
		
		ImagePlus imp = IJ.createHyperStack("Cilium Image", width, height, nrOfChannels, slices, 1, bitDepth);
		imp.setCalibration(cal);
		imp.getCalibration().xOrigin = xCorr-2;
		imp.getCalibration().yOrigin = yCorr-2;
		imp.getCalibration().zOrigin = zCorr-1;
		imp.setDisplayMode(IJ.COMPOSITE);
		
		//draw cilium
		for(int i = 0; i < voxels; i++){
			//[pointID][0=x,1=y,2=z,3=intensity,4=surface, 5=coveredSurface, 6 = coloc (if so 1, else 0)]
			imp.getStack().setVoxel((int)Math.round(points [i][0] / calibration)+2-xCorr,
					(int)Math.round(points [i][1] / calibration)+2-yCorr, 
					imp.getStackIndex(1,(int)Math.round(points [i][2] / voxelDepth)+1-zCorr+1,1)-1,		//getStackIndex(int channel,int slice,int frame);
					points [i][3]);
		}
		imp.setC(1);	
		IJ.run(imp, "Cyan", "");
		imp.setDisplayRange(0, 4095);
		
		//draw skeleton
		if(foundSkl != 0 && sklPointList != null){
			for(int i = 0; i < sklPointList.size(); i++){
				imp.getStack().setVoxel((int)Math.round(sklPointList.get(i).x / calibration)+2-xCorr,
						(int)Math.round(sklPointList.get(i).y / calibration)+2-yCorr, 
						imp.getStackIndex(2,(int)Math.round(sklPointList.get(i).z / voxelDepth)+1-zCorr+1,1)-1, 	//getStackIndex(int channel,int slice,int frame);
						maxValue);				
			}				
		}
		imp.setC(2);	
		IJ.run(imp, "Grays", "");
		imp.setDisplayRange(0, maxValue);
		
		int cIndex = 3;
		if(minC2Intensity != Double.POSITIVE_INFINITY){
			for(int i = 0; i < voxels; i++){
				//[pointID][0=x,1=y,2=z,3=intensity,4=surface, 5=coveredSurface, 6 = coloc (if so 1, else 0)]	
				int z = imp.getStackIndex(cIndex,(int)Math.round(points [i][2] / voxelDepth)+1-zCorr+1,1)-1;	//getStackIndex(int channel,int slice,int frame);
				imp.getStack().setVoxel((int)Math.round(points [i][0] / calibration)+2-xCorr,
						(int)Math.round(points [i][1] / calibration)+2-yCorr, z, points [i][5]);
			}
			imp.setC(cIndex);	
			IJ.run(imp, "Green", "");
			imp.setDisplayRange(0, 4095);
		}
		
		cIndex = 4;			
		if(minC3Intensity != Double.POSITIVE_INFINITY){
			for(int i = 0; i < voxels; i++){
				//[pointID][0=x,1=y,2=z,3=intensity,4=surface, 5=coveredSurface, 6 = coloc (if so 1, else 0)]	
				int z = imp.getStackIndex(cIndex,(int)Math.round(points [i][2] / voxelDepth)+1-zCorr+1,1)-1;	//getStackIndex(int channel,int slice,int frame);
				imp.getStack().setVoxel((int)Math.round(points [i][0] / calibration)+2-xCorr,
						(int)Math.round(points [i][1] / calibration)+2-yCorr, z, points [i][6]);
			}
			imp.setC(cIndex);	
			IJ.run(imp, "Red", "");
			//Possible colors
//			IJ.run(imp, "Red", "");
//			IJ.run(imp, "Green", "");
//			IJ.run(imp, "Blue", "");
//			IJ.run(imp, "Cyan", "");
//			IJ.run(imp, "Magenta", "");
//			IJ.run(imp, "Yellow", "");
//			IJ.run(imp, "Grays", "");
			imp.setDisplayRange(0, 4095);
		}								
									
		String active = "11";
		for(int i = 3; i < cIndex; i++){
			active += "1";
		}
		imp.setActiveChannels(active);	//display all channels		
		return imp;
	}
	
	/**
	 * @return 3D distance between two SklPoints with coordinates (x,y,z)
	 * @param p, PartPoint (integer values for x,y,z)
	 * @param q, a double array with the metric coordinates (e.g. in micron) in order x,y,z
	 * */
	private double getDistanceInPx(PartPoint p, double q []) {
		return Math.sqrt(Math.pow(p.x-(int)Math.round(q[0] / calibration),2.0)+Math.pow(p.y-(int)Math.round(q[1] / calibration),2.0)+Math.pow(p.z-(int)Math.round(q[2] / voxelDepth),2.0));
	}
	
	/**
	 * @return 3D distance between two SklPoints with coordinates (x,y,z)
	 * @param p, PartPoint (integer values for x,y,z)
	 * @param q, a double array with the metric coordinates (e.g. in micron) in order x,y,z
	 * */
	private double getDistanceInPxNoCalib(PartPoint p, double q []) {
		return Math.sqrt(Math.pow(p.x-(int)Math.round(q[0]),2.0)+Math.pow(p.y-(int)Math.round(q[1]),2.0)+Math.pow(p.z-(int)Math.round(q[2]),2.0));
	}
	
	/**
	 * @return 3D distance between two SklPoints with coordinates (x,y,z)
	 * @param p, PartPoint (integer values for x,y,z)
	 * @param q, a double array with the metric coordinates (e.g. in micron) in order x,y,z
	 * */
	private double getRealDistance(PartPoint p, double q []) {
		return Math.sqrt(Math.pow(p.x*calibration-q[0],2.0)+Math.pow(p.y*calibration-q[1],2.0)+Math.pow(p.z*voxelDepth-q[2],2.0));
	}
				
//	private double getDistance(ciliaQ_skeleton_analysis.Point p, ciliaQ_skeleton_analysis.Point q) {
//		return Math.sqrt(Math.pow(p.x-q.x,2.0)+Math.pow(p.y-q.y,2.0)+Math.pow(p.z-q.z,2.0));
//	}	
}
