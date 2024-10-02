package ciliaQ_jnh;
/** ===============================================================================
 * CiliaQ, a plugin for imagej - Version 0.0.6
 * 
 * Copyright (C) 2017-2024 Jan Niklas Hansen
 * First version: June 30, 2017  
 * This Version: August 31, 2024
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

class Uncalibrated3DPoint{
	double x = Double.NaN, y = Double.NaN, z = Double.NaN;
	
	public Uncalibrated3DPoint(double px, double py, double pz){
		x = px;
		y = py;
		z = pz;
	}

	/**
	 * Duplicating a VoxelPoint = Creating a copy of it
	 * @param p = the voxel Point
	 */
	public Uncalibrated3DPoint(Uncalibrated3DPoint p){
		x = p.x;
		y = p.y;
		z = p.z;
	}
}

