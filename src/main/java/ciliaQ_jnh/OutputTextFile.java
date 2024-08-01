/***===============================================================================
 *  
 * CiliaQ, a plugin for imagej - Version 0.1.8
 * 
 * Copyright (C) 2017-2023 Jan Niklas Hansen
 * First version: June 30, 2017  
 * This Version: September 18, 2023
 * 
 * Parts of the code in this class were inherited 
 * from MotiQ 3D Analyzer Version v0.3.4
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
 * 
 * =============================================================================**/

package ciliaQ_jnh;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

class OutputTextFile {
	String path;	
	FileWriter writer = null;
	String output;
	
	public OutputTextFile(String outputPath) {
		output = "";
		path = outputPath;		
	}
	
	public void append(String text) {
		output += (text + "\n");
	}
	
	public boolean finish() {
		if(output.length() == 0) return false;
		if(path.length() == 0) return false;
		
		output = output.replaceAll("\n$", "");
				
		try {
			writer = new FileWriter(new File(path));
			writer.write(output);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		output = "";
		path = "";
		return true;
	}
	
	public boolean saveAndFinish(String outputPath) {
		path = outputPath;
		return this.finish();
	}
	
	public void changePath(String newOutputPath) {
		path = newOutputPath;
	}
}
