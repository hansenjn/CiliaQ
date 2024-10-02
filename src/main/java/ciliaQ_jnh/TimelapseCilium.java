package ciliaQ_jnh;
/** ===============================================================================
 * CiliaQ, a plugin for imagej - Version 0.1.7
 * 
 * Copyright (C) 2017-2019 Jan Niklas Hansen
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

import java.util.ArrayList;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;

class TimelapseCilium{
	boolean excluded = false;
	
	Calibration cal;
	double calibration, voxelDepth, frameRate;
	int bitDepth, frames;
		
	ArrayList<Cilium> cilia;
	
	double xCAvg = 0.0, yCAvg = 0.0, zCAvg = 0.0;
		
	int xMin = Integer.MAX_VALUE,
		yMin = Integer.MAX_VALUE,
		zMin = Integer.MAX_VALUE,
		xMax = 0,
		yMax = 0,
		zMax = 0;
	
	double voxelsAvg = 0.0, 
		surfaceVoxelsAvg = 0.0,
		volumeAvg = 0.0,
		surfaceAvg = 0.0,
		sphereRadiusAvg = 0.0,
		shapeComplexityAvg = 0.0,
		maximumSpanAvg = -1.0; //TODO	- 	method needs to be implemented
	
	double colocalizedVolumeC2Avg = 0.0,
			colocalizedFractionC2Avg = 0.0,
			colocalizedVolumeC3Avg = 0.0,
			colocalizedFractionC3Avg = 0.0,
			
			colocalizedCompToBGVolumeC2Avg = 0.0,
			colocalizedCompToBGFractionC2Avg = 0.0,
			colocalizedCompToBGVolumeC3Avg = 0.0,
			colocalizedCompToBGFractionC3Avg = 0.0,
					
			maxCiliumIntensityAvg = 0.0,
			maxTenPercentCiliumIntensityAvg = 0.0,
			minCiliumIntensityAvg = 0.0,
			averageCiliumIntensityAvg = 0.0,
			SDCiliumIntensityAvg = 0.0,
			
			maxC2IntensityAvg = 0.0,
			maxTenPercentC2IntensityAvg = 0.0,
			minC2IntensityAvg = 0.0,
			averageC2IntensityAvg = 0.0,
			SDC2IntensityAvg = 0.0,
					
			maxC3IntensityAvg = 0.0,
			maxTenPercentC3IntensityAvg = 0.0,
			minC3IntensityAvg = 0.0,
			averageC3IntensityAvg = 0.0,
			SDC3IntensityAvg = 0.0,
			
			foundSklAvg = 0.0,
			branchesAvg = 0.0,
			treeLengthAvg = 0.0,
			largestShortestPathOfLargestAvg,
			bendingIndexAvg = 0.0;
	double [] orientationVectorAvg = {0.0, 0.0, 0.0};
	
	//Basal Body parameters (if applicable)
	double bbXAvg = 0.0, bbYAvg = 0.0, bbZAvg = 0.0;
	double bbCenterIntensityC2Avg = 0.0, bbCenterIntensityC3Avg = 0.0;
	double bbIntensityRadius1C2Avg = 0.0, bbIntensityRadius1C3Avg = 0.0;
	double bbIntensityRadius2C2Avg = 0.0, bbIntensityRadius2C3Avg = 0.0;
	
	public boolean sklAvailableInAllFrames = true;		
				
	public TimelapseCilium(ArrayList<CellPoint> ciliaPoints, ImagePlus imp, 
			boolean measureC2, int channel2, boolean measureC3, int channel3, boolean measureBasalBody, int channelBasalBody,
			int channelReconstruction, double gXY, double gZ, double intensityThresholds [], ProgressDialog progress, 
			boolean skeletonize, boolean segmentedBB,
			boolean showGUIs){
		
		bitDepth = imp.getBitDepth();
		cal = imp.getCalibration().copy();
		
		calibration = cal.pixelWidth; 
		voxelDepth = cal.pixelDepth;
		frameRate = cal.fps;
		frames = imp.getNFrames();
		{
			ArrayList<ArrayList<CellPoint>> kineticList = new ArrayList<ArrayList<CellPoint>>(imp.getNFrames());
			for(int t = 0; t < imp.getNFrames(); t++){
				kineticList.add(new ArrayList<CellPoint>((int)Math.round((double)ciliaPoints.size()/((double)imp.getNFrames()/2.0))));
			}
			for(int p = ciliaPoints.size()-1; p >= 0; p--){
				kineticList.get(ciliaPoints.get(p).t).add(ciliaPoints.get(p));
			}
			for(int t = 0; t < imp.getNFrames(); t++){
				if(showGUIs)	progress.updateBarText("Timepoint generated in timelapse cilium: " 
						+ (t+1) + "/" + imp.getNFrames() + ": " + kineticList.get(t).size() + " points");
			}
			cilia = new ArrayList<Cilium>(imp.getNFrames());
			int counter = 0, bbCounter = 0;
		
			for(int t = 0; t < imp.getNFrames(); t++){
				kineticList.get(t).trimToSize();
				if(kineticList.get(t).size()==0){
					continue;
				}
				
				cilia.add(new Cilium(kineticList.get(t), imp, measureC2, channel2, measureC3, channel3, measureBasalBody, channelBasalBody,
						channelReconstruction, gXY, gZ, intensityThresholds, progress, skeletonize, showGUIs));
				
				//TODO Add basal body if selected - new from version v0.2.0
				
				if(cilia.get(cilia.size()-1).xMax>xMax)	xMax = cilia.get(cilia.size()-1).xMax;
				if(cilia.get(cilia.size()-1).xMin<xMin)	xMin = cilia.get(cilia.size()-1).xMin;
				
				if(cilia.get(cilia.size()-1).yMax>yMax)	yMax = cilia.get(cilia.size()-1).yMax;
				if(cilia.get(cilia.size()-1).yMin<yMin)	yMin = cilia.get(cilia.size()-1).yMin;
				
				if(cilia.get(cilia.size()-1).zMax>zMax)	zMax = cilia.get(cilia.size()-1).zMax;
				if(cilia.get(cilia.size()-1).zMin<zMin)	zMin = cilia.get(cilia.size()-1).zMin;
				
				surfaceVoxelsAvg += (double) cilia.get(cilia.size()-1).surfaceVoxels;
				voxelsAvg += (double) cilia.get(cilia.size()-1).voxels;
				volumeAvg += cilia.get(cilia.size()-1).volume;
				surfaceAvg += cilia.get(cilia.size()-1).surface;
				sphereRadiusAvg += cilia.get(cilia.size()-1).sphereRadius;
				shapeComplexityAvg += cilia.get(cilia.size()-1).shapeComplexity;
				maximumSpanAvg += cilia.get(cilia.size()-1).maximumSpan;	
				xCAvg += cilia.get(cilia.size()-1).xC;
				yCAvg += cilia.get(cilia.size()-1).yC;
				zCAvg += cilia.get(cilia.size()-1).zC;
				
				colocalizedVolumeC2Avg += cilia.get(cilia.size()-1).colocalizedVolumeC2;
				colocalizedFractionC2Avg += cilia.get(cilia.size()-1).colocalizedFractionC2;
				colocalizedVolumeC3Avg += cilia.get(cilia.size()-1).colocalizedVolumeC3;
				colocalizedFractionC3Avg += cilia.get(cilia.size()-1).colocalizedFractionC3;
				
				colocalizedCompToBGVolumeC2Avg += cilia.get(cilia.size()-1).colocalizedCompToBGVolumeC2;
				colocalizedCompToBGFractionC2Avg += cilia.get(cilia.size()-1).colocalizedCompToBGFractionC2;
				colocalizedCompToBGVolumeC3Avg += cilia.get(cilia.size()-1).colocalizedCompToBGVolumeC3;
				colocalizedCompToBGFractionC3Avg += cilia.get(cilia.size()-1).colocalizedCompToBGFractionC3;
						
				maxCiliumIntensityAvg += cilia.get(cilia.size()-1).maxCiliumIntensity;
				maxTenPercentCiliumIntensityAvg += cilia.get(cilia.size()-1).maxTenPercentCiliumIntensity;
				minCiliumIntensityAvg += cilia.get(cilia.size()-1).minCiliumIntensity;
				averageCiliumIntensityAvg += cilia.get(cilia.size()-1).averageCiliumIntensity;
				SDCiliumIntensityAvg += cilia.get(cilia.size()-1).SDCiliumIntensity;
				
				maxC2IntensityAvg += cilia.get(cilia.size()-1).maxC2Intensity;
				maxTenPercentC2IntensityAvg += cilia.get(cilia.size()-1).maxTenPercentC2Intensity;
				minC2IntensityAvg += cilia.get(cilia.size()-1).minC2Intensity;
				averageC2IntensityAvg += cilia.get(cilia.size()-1).averageC2Intensity;
				SDC2IntensityAvg += cilia.get(cilia.size()-1).SDC2Intensity;
						
				maxC3IntensityAvg += cilia.get(cilia.size()-1).maxC3Intensity;
				maxTenPercentC3IntensityAvg += cilia.get(cilia.size()-1).maxTenPercentC3Intensity;
				minC3IntensityAvg += cilia.get(cilia.size()-1).minC3Intensity;
				averageC3IntensityAvg += cilia.get(cilia.size()-1).averageC3Intensity;
				SDC3IntensityAvg += cilia.get(cilia.size()-1).SDC3Intensity;
				
				if(skeletonize){
					foundSklAvg += (double)cilia.get(cilia.size()-1).foundSkl;
					branchesAvg += (double)cilia.get(cilia.size()-1).branches;
					treeLengthAvg += cilia.get(cilia.size()-1).treeLength;
					largestShortestPathOfLargestAvg += cilia.get(cilia.size()-1).largestShortestPathOfLargest;
					orientationVectorAvg [0] += cilia.get(cilia.size()-1).orientationVector[0];
					orientationVectorAvg [1] += cilia.get(cilia.size()-1).orientationVector[1];
					orientationVectorAvg [2] += cilia.get(cilia.size()-1).orientationVector[2];
					bendingIndexAvg += cilia.get(cilia.size()-1).bendingIndex;
				}
				
				if(cilia.get(cilia.size()-1).bbAvailable) {
					bbXAvg += cilia.get(cilia.size()-1).bbX;
					bbYAvg += cilia.get(cilia.size()-1).bbY;;
					bbZAvg += cilia.get(cilia.size()-1).bbZ;;
					bbCenterIntensityC2Avg += cilia.get(cilia.size()-1).bbCenterIntensityC2;
					bbCenterIntensityC3Avg += cilia.get(cilia.size()-1).bbCenterIntensityC3;
					bbIntensityRadius1C2Avg += cilia.get(cilia.size()-1).bbIntensityRadius1C2;
					bbIntensityRadius1C3Avg += cilia.get(cilia.size()-1).bbIntensityRadius1C3;
					bbIntensityRadius2C2Avg += cilia.get(cilia.size()-1).bbIntensityRadius2C2;
					bbIntensityRadius2C3Avg += cilia.get(cilia.size()-1).bbIntensityRadius2C3;
					bbCounter ++;
				}
				
				if(!cilia.get(cilia.size()-1).sklAvailable){
						sklAvailableInAllFrames = false;
				}
				
				counter++;
			}	
			surfaceVoxelsAvg /= (double) counter;
			voxelsAvg /= (double) counter;
			volumeAvg /= (double) counter;
			surfaceAvg /= (double) counter;
			sphereRadiusAvg /= (double) counter;
			shapeComplexityAvg /= (double) counter;
			maximumSpanAvg /= (double) counter;
			xCAvg /= (double) counter;
			yCAvg /= (double) counter;
			zCAvg /= (double) counter;	
			
			colocalizedVolumeC2Avg /= (double) counter;
			colocalizedFractionC2Avg /= (double) counter;
			colocalizedVolumeC3Avg /= (double) counter;
			colocalizedFractionC3Avg /= (double) counter;
			
			colocalizedCompToBGVolumeC2Avg /= (double) counter;
			colocalizedCompToBGFractionC2Avg /= (double) counter;
			colocalizedCompToBGVolumeC3Avg /= (double) counter;
			colocalizedCompToBGFractionC3Avg /= (double) counter;
					
			maxCiliumIntensityAvg /= (double) counter;
			maxTenPercentCiliumIntensityAvg /= (double) counter;
			minCiliumIntensityAvg /= (double) counter;
			averageCiliumIntensityAvg /= (double) counter;
			SDCiliumIntensityAvg /= (double) counter;
			
			maxC2IntensityAvg /= (double) counter;
			maxTenPercentC2IntensityAvg /= (double) counter;
			minC2IntensityAvg /= (double) counter;
			averageC2IntensityAvg /= (double) counter;
			SDC2IntensityAvg /= (double) counter;
					
			maxC3IntensityAvg /= (double) counter;
			maxTenPercentC3IntensityAvg /= (double) counter;
			minC3IntensityAvg /= (double) counter;
			averageC3IntensityAvg /= (double) counter;
			SDC3IntensityAvg /= (double) counter;
			
			if(skeletonize){
				foundSklAvg /= (double) counter;
				branchesAvg /= (double) counter;
				treeLengthAvg /= (double) counter;
				largestShortestPathOfLargestAvg /= (double) counter;
				orientationVectorAvg [0] /= (double) counter;
				orientationVectorAvg [1] /= (double) counter;
				orientationVectorAvg [2] /= (double) counter;
				bendingIndexAvg  /= (double) counter;
			}
			
			if(bbCounter>0) {
				bbXAvg /= (double) bbCounter;
				bbYAvg /= (double) bbCounter;
				bbZAvg /= (double) bbCounter;
				bbCenterIntensityC2Avg /= (double) bbCounter;
				bbCenterIntensityC3Avg /= (double) bbCounter;
				bbIntensityRadius1C2Avg /= (double) bbCounter;
				bbIntensityRadius1C3Avg /= (double) bbCounter;
				bbIntensityRadius2C2Avg /= (double) bbCounter;
				bbIntensityRadius2C3Avg /= (double) bbCounter;
			}
					
			kineticList.clear();
			kineticList = null;
		}
	}
			
	/**
	 * Create cilium image as stack
	 * */
	public ImagePlus getCiliumImp(double intensityThresholds [], int channelC2, int channelC3, ProgressDialog progress){
		int width = xMax - xMin + 1 + 4,
				height = yMax - yMin + 1 + 4,
				slices = zMax - zMin + 1 + 2;
		int xCorr = xMin,
				yCorr = yMin,
				zCorr = zMin;
		
		int nrOfChannels = 4;
		
		ImagePlus imp = IJ.createHyperStack("Cilium", width, height, nrOfChannels, slices, frames, bitDepth);
		imp.setCalibration(cal);
		imp.getCalibration().xOrigin = xCorr-2;
		imp.getCalibration().yOrigin = yCorr-2;
		imp.getCalibration().zOrigin = zCorr-1;
		imp.setDisplayMode(IJ.COMPOSITE);
		
		ImagePlus impTemp;
		int index, indexTemp;
		for(int i = 0; i < cilia.size(); i++){
			impTemp = cilia.get(i).getCiliumImpForTimelapse(intensityThresholds, channelC2, channelC3, 
					xMax, xMin, yMax, yMin, zMax, zMin, progress);
			for(int c = 0; c < imp.getNChannels(); c++){
				for(int z = 0; z < imp.getNSlices(); z++){
					for(int x = 0; x < imp.getWidth(); x++){
						for(int y = 0; y < imp.getHeight(); y++){	
							index = imp.getStackIndex(c+1, z+1, cilia.get(i).t+1)-1;
							indexTemp = impTemp.getStackIndex(c+1, z+1, 1)-1;
							imp.getStack().setVoxel(x, y, index,
									impTemp.getStack().getVoxel(x, y, indexTemp));
						}
					}
				}
			}
			impTemp.changes = false;
			impTemp.close();
		}
		
		imp.setC(1);	
		IJ.run(imp, "Cyan", "");
		imp.setDisplayRange(0, 4095);
				
		imp.setC(2);	
		IJ.run(imp, "Grays", "");
		imp.setDisplayRange(0, 4095);
			
		imp.setC(3);	
		IJ.run(imp, "Green", "");
		imp.setDisplayRange(0, 4095);
	
		imp.setC(4);	
		IJ.run(imp, "Red", "");
		imp.setDisplayRange(0, 4095);		
		
		return imp;
	}
}
