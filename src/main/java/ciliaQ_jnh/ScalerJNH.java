package ciliaQ_jnh;
/** ===============================================================================
 * CiliaQ, a plugin for imagej - Version 0.0.5
 * 
 * Copyright (C) 2017-2019 Jan Niklas Hansen
 * First version: June 30, 2017  
 * This Version: December 09, 2019
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

/**
 * IMPORTANT NOTE:
 * 
 * THIS PARTICULAR CLASS WAS COPIED AND MODIFIED ON 05.07.2019 FROM IMAGEJ1 from this file:
 * https://github.com/imagej/imagej1/blob/master/ij/plugin/Scaler.java
 * ImageJ1 was released into the Public Domain:
 * https://imagej.net/Licensing
 */

import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import java.awt.*;
import ij.plugin.*;

public class ScalerJNH{
	private ImagePlus imp;
	private static int newWidth, newHeight;
	private int newDepth;
	private boolean doZScaling;
	private static boolean averageWhenDownsizing = true;
	private static boolean newWindow = true;
	private static int interpolationMethod = ImageProcessor.BILINEAR;
	private static boolean processStack = true;
	private double xscale, yscale, zscale;
	private String title = "Untitled";
	private double bgValue;
	private Rectangle r;
	private int oldDepth;

	public ImagePlus getScaled(ImagePlus impToBeScaled, double scaleX, double scaleY, double scaleZ) {
		imp = impToBeScaled;
		Roi roi = imp.getRoi();
		ImageProcessor ip = imp.getProcessor();
		if (roi!=null && !roi.isArea())
			ip.resetRoi();
		oldDepth = imp.getStackSize();
		{
			int stackSize = imp.getStackSize();
			r = ip.getRoi();
			xscale = scaleX;
			yscale = scaleY;
			zscale = scaleZ;	
			title = WindowManager.getUniqueName(imp.getTitle());
			if (xscale>0.0 && yscale>0.0){
				newWidth = (int)Math.round(r.width*xscale);
				newHeight = (int)Math.round(r.height*yscale);
			}
			if (zscale!=1.0 && zscale>0.0){
				newDepth = (int)Math.round(stackSize*zscale);
			}
				
		}
		
		doZScaling = newDepth>0 && newDepth!=oldDepth;
		if (doZScaling) {
			newWindow = true;
			processStack = true;
		}
		if ((ip.getWidth()>1 && ip.getHeight()>1) || newWindow)
			ip.setInterpolationMethod(interpolationMethod);
		else
			ip.setInterpolationMethod(ImageProcessor.NONE);
		ip.setBackgroundValue(bgValue);
		imp.startTiming();
		
//		imp.show();
//		new WaitForUserDialog("Test1").show();
//		imp.hide();
		
		try {
			if (newWindow && imp.getStackSize()>1 && processStack){
				imp = createNewStack(imp, ip);
			}else {
				Overlay overlay = imp.getOverlay();
				if (imp.getHideOverlay())
					overlay = null;
				if (overlay!=null && overlay.size()!=1)
					overlay = null;
				if (overlay!=null)
					overlay = overlay.duplicate();
				imp = scale(ip, overlay);
			}
		}
		catch(OutOfMemoryError o) {
			IJ.outOfMemory("Scale");
		}
		
//		imp.show();
//		new WaitForUserDialog("Test2").show();
//		imp.hide();
		
		imp.updateAndDraw();
		imp.repaintWindow();
		IJ.showProgress(1.0);
		return imp;
	}
	
	ImagePlus createNewStack(ImagePlus imp, ImageProcessor ip) {
		int nSlices = imp.getStackSize();
		int w=imp.getWidth(), h=imp.getHeight();
		ImagePlus imp2 = imp.createImagePlus();
		Rectangle r = ip.getRoi();
		boolean crop = r.width!=imp.getWidth() || r.height!=imp.getHeight();
		ImageStack stack1 = imp.getStack();
		ImageStack stack2 = new ImageStack(newWidth, newHeight);
		boolean virtualStack = stack1.isVirtual();
		double min = imp.getDisplayRangeMin();
		double max = imp.getDisplayRangeMax();
		ImageProcessor ip1, ip2;
		int method = interpolationMethod;
		if (w==1 || h==1)
			method = ImageProcessor.NONE;
		for (int i=1; i<=nSlices; i++) {
			IJ.showStatus("Scale: " + i + "/" + nSlices);
			ip1 = stack1.getProcessor(i);
			String label = stack1.getSliceLabel(i);
			if (crop) {
				ip1.setRoi(r);
				ip1 = ip1.crop();
			}
			ip1.setInterpolationMethod(method);
//			IJ.log("n" + newWidth + ".." + newHeight + ".." + averageWhenDownsizing);
			ip2 = ip1.resize(newWidth, newHeight, averageWhenDownsizing);
			if (ip2!=null)
				stack2.addSlice(label, ip2);
			IJ.showProgress(i, nSlices);
		}
		imp2.setStack(title, stack2);
		if (virtualStack)
			imp2.setDisplayRange(min, max);
		Calibration cal = imp2.getCalibration();
		if (cal.scaled()) {
			cal.pixelWidth *= 1.0/xscale;
			cal.pixelHeight *= 1.0/yscale;
		}
		Overlay overlay = imp.getOverlay();
		if (imp.getHideOverlay())
			overlay = null;
		if (overlay!=null) {
			overlay = overlay.duplicate();
			Overlay overlay2 = new Overlay();
			for (int i=0; i<overlay.size(); i++) {
				Roi roi = overlay.get(i);
				Rectangle bounds = roi.getBounds();
				if (roi instanceof ImageRoi && bounds.x==0 && bounds.y==0) {
					ImageRoi iroi = (ImageRoi)roi;
					ImageProcessor processor = iroi.getProcessor();
					processor.setInterpolationMethod(method);
					processor = processor.resize(newWidth, newHeight, averageWhenDownsizing);
					iroi.setProcessor(processor);
					overlay2.add(iroi);
				}
			}
			if (overlay2.size()>0)
				imp2.setOverlay(overlay2);
		}
		IJ.showProgress(1.0);
		int[] dim = imp.getDimensions();
		imp2.setDimensions(dim[2], dim[3], dim[4]);
		if (imp.isComposite()) {
			imp2 = new CompositeImage(imp2, ((CompositeImage)imp).getMode());
			((CompositeImage)imp2).copyLuts(imp);
		}
		if (imp.isHyperStack())
			imp2.setOpenAsHyperStack(true);
		if (doZScaling) {
			Resizer resizer = new Resizer();
			resizer.setAverageWhenDownsizing(averageWhenDownsizing);
			imp2 = resizer.zScale(imp2, newDepth, interpolationMethod);
		}
		imp2.changes = true;
		return imp2;
	}

	private ImagePlus scale(ImageProcessor ip, Overlay overlay) {
		if (newWindow) {
			Rectangle r = ip.getRoi();
			ImagePlus imp2 = imp.createImagePlus();
			imp2.setProcessor(title, ip.resize(newWidth, newHeight, averageWhenDownsizing));
			Calibration cal = imp2.getCalibration();
			if (cal.scaled()) {
				cal.pixelWidth *= 1.0/xscale;
				cal.pixelHeight *= 1.0/yscale;
			}
			if (overlay!=null) {
				Roi roi = overlay.get(0);
				Rectangle bounds = roi.getBounds();
				if (roi instanceof ImageRoi && bounds.x==0 && bounds.y==0) {
					ImageRoi iroi = (ImageRoi)roi;
					ImageProcessor processor = iroi.getProcessor();
					processor.setInterpolationMethod(interpolationMethod);
					processor =processor.resize(newWidth, newHeight, averageWhenDownsizing);
					iroi.setProcessor(processor);
					imp2.setOverlay(new Overlay(iroi));
				}
			}
			imp.trimProcessor();
			imp2.trimProcessor();
			imp2.changes = true;
			return imp2;
		} else {
			if (processStack && imp.getStackSize()>1) {
				Undo.reset();
				StackProcessor sp = new StackProcessor(imp.getStack(), ip);
				sp.scale(xscale, yscale, bgValue);
			} else {
				ip.snapshot();
				Undo.setup(Undo.FILTER, imp);
				ip.setSnapshotCopyMode(true);
				ip.scale(xscale, yscale);
				ip.setSnapshotCopyMode(false);
			}
			imp.deleteRoi();
			imp.updateAndDraw();
			imp.changes = true;
			return imp;
		}
	}
}
