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

import ij.ImagePlus;

class CellPoint{
	int x = 0; 
	int y = 0; 
	int z = 0; 
	int t = 0;
	double intensity = 0.0;
	private int xySurface = 0;
	private int xzyzSurface = 0;
	
	/**
	 * pz >= 0 && pz < number of slices
	 * pt >= 0 && pt < number of frames
	 * */
	public CellPoint(int px, int py, int pz, int pt, ImagePlus imp, int channel){
		intensity = imp.getStack().getVoxel(px, py, imp.getStackIndex(channel, pz+1, pt+1)-1);
		x = px;
		y = py;
		z = pz;
		t = pt;
		
		if(pz > 0
				&& imp.getStack().getVoxel(px, py, imp.getStackIndex(channel, (pz-1)+1, pt+1)-1) == 0.0){	
			xySurface++;
		}
		
		if(pz < imp.getNSlices() - 1
				&& imp.getStack().getVoxel(px, py, imp.getStackIndex(channel, (pz+1)+1, pt+1)-1) == 0.0){	
			xySurface++;
		}

		if(px > 0
				&& imp.getStack().getVoxel(px-1, py, imp.getStackIndex(channel, (pz)+1, pt+1)-1) == 0.0){	
			xzyzSurface++;
		}

		if(px < imp.getWidth() - 1
				&& imp.getStack().getVoxel(px+1, py, imp.getStackIndex(channel, (pz)+1, pt+1)-1) == 0.0){	
			xzyzSurface++;
		}

		if(py > 0
				&& imp.getStack().getVoxel(px, py-1, imp.getStackIndex(channel, (pz)+1, pt+1)-1) == 0.0){	
			xzyzSurface++;
		}
		
		if(py < imp.getHeight() - 1
				&& imp.getStack().getVoxel(px, py+1, imp.getStackIndex(channel, (pz)+1, pt+1)-1) == 0.0){	
			xzyzSurface++;
		}
	}
	
	double getSurface(double cal, double zcal){
		return (cal*cal*(double)xySurface + cal*zcal*(double)xzyzSurface); 
	}
}

