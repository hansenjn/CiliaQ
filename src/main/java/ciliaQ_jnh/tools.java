package ciliaQ_jnh;
/** ===============================================================================
 * CiliaQ, a plugin for imagej - Version 0.0.6
 * 
 * Copyright (C) 2017-2020 Jan Niklas Hansen
 * First version: June 30, 2017  
 * This Version: February 21, 2020
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

import java.awt.Color;
import java.util.Arrays;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.WaitForUserDialog;

public class tools{
	/**
	 * For User Control And Functionality Checks
	 * */	
	private static void showAsPlot(double [] xV, double [] yV){
		PlotWindow.noGridLines = true; // draw grid lines
		Plot plot = new Plot("histogram","plot" ,"plotted",xV,yV);
		
	    plot.setLineWidth(1);
	    plot.setColor(Color.red);
		
	    PlotWindow pl = plot.show();		
	    
	    new WaitForUserDialog("check!").show();
	    
	    pl.close();
	}

	public static void showAsPlot(double [] values){
		double [] x = new double [values.length];
		for(int i = 0; i < values.length; i++){
			x [i] = (double)i;
		}
		
		PlotWindow.noGridLines = true; // draw grid lines
		Plot plot = new Plot("histogram","plot" ,"plotted",x,values);
		
	    plot.setLineWidth(1);
	    plot.setColor(Color.red);
		
	    PlotWindow pl = plot.show();		
	    
	    new WaitForUserDialog("check!").show();
	    
	    pl.close();
	}

	private static void userCheckImage(ImagePlus imp){
		imp.show();
		imp.updateAndDraw();
		new WaitForUserDialog("check!").show();
		imp.hide();
	}

	/*
	 * Mathematics
	 * */	
	/**
	 * @returns the absolute value of the @param value
	 * */
	public static double mathAbs(double value){
		return Math.sqrt(Math.pow(value, 2.0));
	}
	
	public static double getNormalizedValue (double value, double min, double max){
		return ((value - min) / (max - min));
	}
		
	/*
	 * Vectors and Linear Algebra
	 * */
	public static double getVectorLength(double [] vector){
		double length = 0.0;
		for(int i = 0; i < vector.length; i++){
			length += Math.pow(vector [i],2.0);
		}				
		return Math.sqrt(length);
	}

	public static double [] crossProduct (double [] u, double [] v){
		double [] crossProduct = new double [3];
		crossProduct [0] = u[1]*v[2]-u[2]*v[1];
		crossProduct [1] = u[2]*v[0]-u[0]*v[2];
		crossProduct [2] = u[0]*v[1]-u[1]*v[0];
		return crossProduct;
	}
	
	public static double getCrossProductZComponent (double [] u, double [] v){
		return u[0] * v[1] - u[1] * v[0];
	}
		
	/**
	 * Array filtering
	 * */	
	public static double [] getArrayColumn(double [][] values, int index){
		/**
		 * returns a column of a 2D array at the index position
		 * */
		double [] results = new double [values.length];
		for(int i = 0; i < values.length; i++){
			results [i] = values [i][index];
		}
		return results;
	}

	public static double getAverage(double [] values){
		double average = 0.0;	
		for(int x = 0; x < values.length; x++){
			average += values [x];
		}	
		return (average / (double) values.length);		
	}
	
	public static double getSum (double [] values){
		double sum = 0.0;	
		for(int x = 0; x < values.length; x++){
			sum += values [x];
		}	
		return sum;		
	}
	
	public static double getSD(double [] values){
		double average = tools.getAverage(values);
		double SD = 0.0;
		for(int x = 0; x < values.length; x++){
			SD += Math.pow(values [x] - average, 2.0);
		}
		return Math.sqrt(SD / (double)(values.length-1));					
	}
	
	public static double getMedian(double [] values){
		double [] medians = new double [values.length];
		for(int i = 0; i < values.length; i++){
			medians [i] = values [i];
		}
		
		Arrays.sort(medians);
		
		if(medians.length%2==0){
			return (medians[(int)((double)(medians.length)/2.0)-1]+medians[(int)((double)(medians.length)/2.0)])/2.0;
		}else{
			return medians[(int)((double)(medians.length)/2.0)];
		}		
	}
	
	public static double getMaximum(double [] values){
		double maximum = Double.NEGATIVE_INFINITY;	
		for(int x = 0; x < values.length; x++){
			if(values [x] > maximum)	maximum = values [x];
		}	
		return maximum;		
	}
	
	public static int getMaximum(int [] values){
		int maximum = Integer.MIN_VALUE;	
		for(int x = 0; x < values.length; x++){
			if(values [x] > maximum)	maximum = values [x];
		}	
		return maximum;		
	}
	
	public static int getMinimumIndex(double [] values){
		double value = Double.POSITIVE_INFINITY;
		int index = -1; 
		for(int i = 0; i < values.length; i++){
			if(values [i] < value){
				value = values [i];
				index = i;
			}
		}
		return index;
	}

	public static int getMaximumIndex(double [] values){
		int index = -1; double value = Double.NEGATIVE_INFINITY;
		for(int i = 0; i < values.length; i++){
			if(values [i] > value){
				value = values [i];
				index = i;
			}
		}
		return index;
	}
	
	public static int getMaximumIndex(int [] values){
		int index = -1; int value = Integer.MIN_VALUE;
		for(int i = 0; i < values.length; i++){
			if(values [i] > value){
				value = values [i];
				index = i;
			}
		}
		return index;
	}

	public static int getIndexOfClosestValue(double [] values, double value){
		if(values.length == 0)	return -2;
		int index = -1; double foundDist = Double.MAX_VALUE;
		for(int i = 0; i < values.length; i++){
			if(Math.sqrt(Math.pow(values [i] - value,2.0)) < foundDist){
				foundDist = Math.sqrt(Math.pow(values [i] - value,2.0));
				index = i;
			}
		}
		return index;
	}

	// RANGE
	public static double getAverageOfRange(double [] values, int startIndex, int endIndex){
		double average = 0.0;	
		for(int x = startIndex; x <= endIndex; x++){
			average += values [x];
		}	
		return (average / (double)(endIndex-startIndex+1));		
	}
	
	public static double getSDOfRange(double [] values, int startIndex, int endIndex){
		double average = tools.getAverageOfRange(values, startIndex, endIndex);
		double SD = 0.0;
		for(int x = startIndex; x <= endIndex; x++){
			SD += Math.pow(values [x] - average, 2.0);
		}
		return Math.sqrt(SD / (double)(endIndex-startIndex+1-1));					
	}
	
	public static double getMinimumWithinRange(double [] values, int firstIndex, int lastIndex){
		double minimum = Double.POSITIVE_INFINITY;	
		for(int x = firstIndex; x <= lastIndex && x < values.length; x++){
			if(values [x] < minimum)	minimum = values [x];
		}	
		return minimum;		
	}
	
	public static int getMinimumWithinRange(int [] values, int firstIndex, int lastIndex){
		int minimum = Integer.MAX_VALUE;	
		for(int x = firstIndex; x <= lastIndex && x < values.length; x++){
			if(values [x] < minimum)	minimum = values [x];
		}	
		return minimum;		
	}

	public static double getMedianOfRange(double [] values, int firstIndex, int lastIndex){
		double [] medians = new double [lastIndex - firstIndex + 1];
		for(int i = firstIndex; i <= lastIndex; i++){
			medians [i-firstIndex] = values [i];
		}
		
		Arrays.sort(medians);
		
		if(medians.length%2==0){
			return (medians[(int)((double)(medians.length)/2.0)-1]+medians[(int)((double)(medians.length)/2.0)])/2.0;
		}else{
			return medians[(int)((double)(medians.length)/2.0)];
		}		
	}
	
	public static double getMaximumWithinRange(double [] values, int firstIndex, int lastIndex){
		double maximum = Double.NEGATIVE_INFINITY;	
		for(int x = firstIndex; x <= lastIndex && x < values.length; x++){
			if(values [x] > maximum)	maximum = values [x];
		}	
		return maximum;		
	}

	public static double [] get2MaximaWithinRange(double [] values, int firstIndex, int lastIndex){
		double [] maxima = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		int index = -1;
		for(int x = firstIndex; x <= lastIndex && x < values.length; x++){
			if(values [x] > maxima [0]){
				maxima [0] = values [x];
				index = x;
			}
		}	
		
		for(int x = firstIndex; x <= lastIndex && x < values.length; x++){
			if(x != index && x-1 != index && x+1 != index && values [x] > maxima [1]){
				maxima [1] = values [x];
			}
		}
		
		return maxima;		
	}

	public static int getMaximumIndexWithinRange(double [] values, int firstIndex, int lastIndex){
		double maximum = Double.NEGATIVE_INFINITY;
		int index = -1;
		
		//find first Maximum
		for(int x = firstIndex; x <= lastIndex && x < values.length; x++){
			if(values [x] > maximum){
				maximum = values [x];
				index = x;
			}
		}	
		
		return index;		
	}

	public static int [] get2MaximaIndicesWithinRange(double [] values, int firstIndex, int lastIndex){
		double [] maxima = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		int [] index = {-1, -1};
		
		//find first Maximum
		for(int x = firstIndex; x <= lastIndex && x < values.length; x++){
			if(values [x] > maxima [0]){
				maxima [0] = values [x];
				index [0] = x;
			}
		}	
		
		//find  Maximum
		for(int x = firstIndex; x <= lastIndex && x < values.length; x++){
			if(x != index [0] && x-1 != index [0] && x+1 != index [0] && values [x] > maxima [1]){
				maxima [1] = values [x];
				index [1] = x;
			}
		}
		
		return index;		
	}
	
	/**
	 * @returns an int array. int [0] contains the array position of the highest maximum, int [1] the position
	 * of the second highest maximum
	 * A maximum is defined as an array position where the neighboring position contain values below the position
	 * @param values: the array to be investigated
	 * @param firstIndex and @param lastIndex specify the range of the array, which is investigated
	 * */
	public static int [] get2HighestMaximaIndicesWithinRange(double [] values, int firstIndex, int lastIndex){
		double [] maxima = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		int [] index = {-1, -1};
		
		//find first Maximum
		for(int x = firstIndex; x <= lastIndex && x < values.length; x++){
			if(x > 0 && x < (values.length-1)
					&& values [x-1] < values [x]
					&& values [x+1] < values [x]
					&& values [x] > maxima [0]){
				maxima [0] = values [x];
				index [0] = x;
			}			
		}	
		
		//find  Maximum
		for(int x = firstIndex; x <= lastIndex && x < values.length; x++){
			if(x > 0 && x < (values.length-1)
					&& values [x-1] < values [x]
					&& values [x+1] < values [x]
					&& x != index [0] 
					&& values [x] > maxima [1]){
				maxima [1] = values [x];
				index [1] = x;
			}
		}
		
		return index;		
	}
	
	/**
	 * Saving Data into Images
	 * */
	public static double getEncodedIntensity8bit (double intensity, double min, double max){
		// zMin -> intensity = 1.0, zMax -> intensity = 253.0
		double corr = intensity - min;
		
		if(corr < 0.0) return 0.0;
		if(corr > (max-min)) return 255.0;
		
		corr = 1.0 + 253.0 * (corr / (max-min));		
		return corr;
	}
	
	public static double getValueFromEncodedIntensity8bit (double min, double max, double intensity){
		return min + ((intensity-1.0)/253.0) * (max-min);
	}
	
	public static double getEncodedIntensity16bit (double intensity, double min, double max){
		// zMin -> intensity = 1.0, zMax -> intensity = 65534.0
		double corr = intensity - min;
		
		if(corr < 0.0) return 0.0;
		if(corr > (max-min)) return 65535.0;
		
		corr = 1.0 + 65533.0 * (corr / (max-min));		
		return corr;
	}

	public static double getValueFromEncodedIntensity16bit (double intensity, double min, double max){
		return min + ((intensity-1.0)/65533.0) * (max-min);
	}
	
	public static double get2DAngle (double [] u, double [] v){			
		double skp = u [0] * v[0] + u [1] * v [1];
		double lp = Math.sqrt(Math.pow(u[0],2.0)+Math.pow(u[1],2.0))
					* Math.sqrt(Math.pow(v[0],2.0)+Math.pow(v[1],2.0));
				
		double theta = (skp/lp);
		
		if((theta)>1.0){
//			IJ.log("Theta determination: skp/lp > 1.0! " + "skp: " + skp + " lp: " + lp);
			theta = 1.0;
		}			
		theta = Math.acos(theta);
		
		if(getCrossProductZComponent(u, v) < 0.0)	theta *= -1;
		if(getCrossProductZComponent(u, v) == 0.0){
			//if crossprod = 0 -> angle either 0 or 180
			if(u [0] == v [0] && u [1] == v [1]){
				theta = 0.0;
			}else{
				theta = Math.PI;
			}
		}
		
		if(Double.isNaN(theta)){
			if(lp==0.0) IJ.log("lp=0 u" + u[0] + "-" + u[1] + "-" + u[2] + " v" + v[0] + "-" + v[1] + "-" + v[2]);
			if(Double.isNaN(u [0]))	IJ.log("u0 NaN");
			if(Double.isNaN(u [1]))	IJ.log("u1 NaN");
			if(Double.isNaN(v [0]))	IJ.log("v0 NaN");
			if(Double.isNaN(v [1]))	IJ.log("v1 NaN");
		}			
		return theta;
	}

	public static double getAbsoluteAngle (double [] u, double [] v){			
		double skp = u [0] * v[0] + u [1] * v [1];
		double lp = 0.0;
		if(u.length > 2 && v.length > 2){
			skp += u [2] * v [2];
			lp = Math.sqrt(Math.pow(u[0],2.0)+Math.pow(u[1],2.0)+Math.pow(u[2],2.0))
					* Math.sqrt(Math.pow(v[0],2.0)+Math.pow(v[1],2.0)+Math.pow(v[2],2.0));
		}else{
			lp = Math.sqrt(Math.pow(u[0],2.0)+Math.pow(u[1],2.0))
					* Math.sqrt(Math.pow(v[0],2.0)+Math.pow(v[1],2.0));
		}
				
		double theta = (skp/lp);
		
		//check
		if((theta)>1.0){
//			IJ.log("Theta determination: skp/lp > 1.0! " + "skp: " + skp + " lp: " + lp);
			theta = 1.0;
		}
		
		theta = Math.acos(theta);
		
		//check function for orientation in 2D
		if((u.length == 2 || u [2] == 0.0)&&(v.length == 2 || v [2] == 0.0)){
			if(v == constants.X_AXIS){
//				IJ.log("XAXIS");
				if(u [0] < 0.0){
					if(u [1] < 0.0){
						if(theta < constants.halfPI){
//							IJ.log("1 " + theta); 
							return Math.PI + theta;
						}else if(theta > constants.halfPI && theta < Math.PI){
//							IJ.log("2 " + theta); 
							return 2*Math.PI - theta;
						}
					}else if(u [1] > 0.0){
						if(theta < constants.halfPI){
//							IJ.log("3 " + theta); 
							return Math.PI - theta;
						}else if(theta > constants.halfPI && theta < Math.PI){
//							IJ.log("4 " + theta); 
							return theta;
						}
					}else{
//						IJ.log("5 " + theta); 
						return Math.PI;
					}
				}else if(u [0] > 0.0){
					if(u [1] < 0.0){
						if(theta < constants.halfPI){
//							IJ.log("6 " + theta); 
							return 2*Math.PI - theta;
						}else if(theta > constants.halfPI && theta < Math.PI){
//							IJ.log("7 " + theta); 
							return Math.PI + theta;
						}
					}else if(u [1] > 0.0){
						if(theta < constants.halfPI){
//							IJ.log("8 " + theta); 
							return theta;
						}else if(theta > constants.halfPI && theta < Math.PI){
//							IJ.log("9 " + theta); 
							return Math.PI-theta;
						}
					}else{
//						IJ.log("10 " + theta); 
						return 0.0;
					}
				}else{
					if(u [1] < 0.0){
//						IJ.log("11 " + theta); 
						return 1.5 * theta;
					}else if(u [1] > 0.0){
//						IJ.log("12 " + theta); 
						return constants.halfPI;
					}
				}
			}
		}	
		
		if(Double.isNaN(theta)){
			if(lp==0.0) IJ.log("lp=0 u" + u[0] + "-" + u[1] + "-" + u[2] + " v" + v[0] + "-" + v[1] + "-" + v[2]);
			if(Double.isNaN(u [0]))	IJ.log("u0 NaN");
			if(Double.isNaN(u [1]))	IJ.log("u1 NaN");
			if(u.length > 2 && Double.isNaN(u [2]))	IJ.log("u2 NaN");
			if(Double.isNaN(v [0]))	IJ.log("v0 NaN");
			if(Double.isNaN(v [1]))	IJ.log("v1 NaN");
			if(v.length > 2 && Double.isNaN(v [2]))	IJ.log("v2 NaN");
		}
		
		return theta;
	}
	
	public static double [] getNormalizedVector(double [] vector){
		double vectorLength = getVectorLength(vector);
		double normVector [] = new double [vector.length];
		for(int i = 0; i < vector.length; i++){
			normVector [i] = vector [i] / vectorLength;
		}		
		return normVector;
	}

	public static double [] getNormalVectorXY (double [] vector){
		double [] nVector = new double [2];
		nVector [0] = vector [1] * -1.0;
		nVector [1] = vector [0];
		if(getCrossProductZComponent(vector,nVector) < 0.0){
			nVector [0] = nVector [0] * -1.0;
			nVector [1] = nVector [1] * -1.0;
		}
		return nVector;
	}
	
	public static double [] projectPointToLine2D (double px, double py, double lx1, double ly1, double lx2, double ly2){
			if(ly2-ly1 == 0.0){
				return new double [] {px, ly1};
			}
			
			if(lx2-lx1 == 0.0){
				return new double [] {lx1, py};
			}
			
			//calculate slope of line
			double m = (ly2-ly1)/(lx2-lx1);
					
			//calculate slope of normal line
			double mNormal = -1.0 / m;
	//		if(Double.isNaN(mNormal)){
	//			IJ.log("mNormal NAN" + "m = " + m);
	//			return new double [] {lx1 + (lx2-lx1)/2.0, py};
	//		}
			
			//find dislocation of normal line (m*x+b=y)
			double bNormal = py - (mNormal * px);
			
			//calculate new x
			double x = ((ly1 - (m * lx1))-bNormal)/(mNormal-m);
			
			return new double [] {x,mNormal * x + bNormal};
			
			//CALCULATION IN STEPS
			//calculate slope of line
	//		double m = (ly2-ly1)/(lx2-lx1);
	//		//find dislocation of line (m*x+b=y)
	//		double b = ly1 - (m * lx1);
	//				
	//		//calculate slope of normal line
	//		double mNormal = 1.0/m;
	//		//find dislocation of normal line (m*x+b=y)
	//		double bNormal = py - (mNormal * px);
	//		
	//		//calculate new x
	//		double x = (b-bNormal)/(mNormal-m);
	//		double y = mNormal * x + bNormal;
		}

	public static double getInterpolatedValue1D (double x, double x1, double x2, double i1, double i2){
			return i1 + (((i2-i1)/(x2-x1)) * (x-x1));
			//STEPWISE CALCULATION
	//		double m = (i2-i1)/(x2-x1);
	//		return i1 + (m * (x-x1));
		}
}
