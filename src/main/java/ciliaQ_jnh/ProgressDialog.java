package ciliaQ_jnh;
/** ===============================================================================
 * CiliaQ, a plugin for imagej - Version 0.2.2
 * 
 * Copyright (C) 2017-2026 Jan Niklas Hansen
 * First version: June 30, 2017  
 * This Version: August 23, 2016 (implementing ProgressReporter)
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


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.SwingConstants;

import ij.IJ;

public class ProgressDialog extends javax.swing.JFrame implements ActionListener, ProgressReporter{
	// From V0.2.2 on: use LinkedLists instead of arrays for better performance
	private LinkedList<String> dataLeft = new LinkedList<>();
	private LinkedList<String> dataRight = new LinkedList<>();
	private LinkedList<String> notifications = new LinkedList<>();
	
	public boolean notificationsAvailable = false, errorsAvailable = false;
	int task, tasks;
	
	JPanel bgPanel;
	JScrollPane jScrollPaneLeft, jScrollPaneRight, jScrollPaneBottom;
	JList<String> ListeLeft, ListeRight, ListeBottom;
	
	private JProgressBar progressBar = new JProgressBar();
	private double taskFraction = 0.0;
	
	// Cache for UI updates to reduce frequency
	private long lastUIUpdate = 0;
	private boolean notificationUpdatePending = false;	// For tracking if notifications need updating
	private static final long UI_UPDATE_INTERVAL_MS = 1000; // Update UI max every 1000 ms

	private long startTime = 0;
	private JLabel timeLabel;
	
	// Constructor for CiliaQ calls
	public ProgressDialog(String [] taskList, int newTasks) {
		super();
		initGUI();
		tasks = newTasks;
		for(int i = 0; i < taskList.length; i++){
			if(taskList[i] != null && !taskList[i].isEmpty()){
				dataLeft.add((i+1) + ": " + taskList[i]);
			}
		}
		updateLeftList();
		taskFraction = 0.0;
		task = 1;
		startTiming();          // NEW: without this the time label never starts
	}
	
	public ProgressDialog(String [] taskList) {
		super();
		initGUI();
		tasks = taskList.length;
		for(int i = 0; i < tasks; i++){
			if(taskList[i] != null && !taskList[i].isEmpty()){
				dataLeft.add((i+1) + ": " + taskList[i]); 
			}			
		}
		updateLeftList();
		taskFraction = 0.0;
		task = 1;
	}
	
	public ProgressDialog(String [] taskList, int [] seriesList, int addToSeriesNumber) {
		super();
		initGUI();
		tasks = taskList.length;
		for(int i = 0; i < tasks; i++){
			if(taskList[i] != null && !taskList[i].isEmpty()){
				dataLeft.add((i+1) + ": " + taskList[i] + ", series " + (seriesList[i] + addToSeriesNumber)); 
			}
		}
		updateLeftList();
		taskFraction = 0.0;
		task = 1;
	}
	
	public ProgressDialog(String [] taskList, String [] seriesList) {
		super();
		if(taskList.length != seriesList.length) {
			IJ.error("File loading error... nSeries != nTasks");
		}
		initGUI();
		tasks = taskList.length;
		for(int i = 0; i < tasks; i++){
			if(taskList[i] != null && !taskList[i].isEmpty()){
				dataLeft.add((i+1) + ": " + taskList[i] + ", Series: " + seriesList[i]); 
			}			
		}
		updateLeftList();
		taskFraction = 0.0;
		task = 1;
	}
	
	private void initGUI() {
		int prefXSize = 600, prefYSize = 500;
		this.setMinimumSize(new java.awt.Dimension(prefXSize, prefYSize+40));
		this.setSize(prefXSize, prefYSize+40);			
		this.setTitle("Multi-Task-Manager - by JN Hansen (\u00a9 2016)");
		
		//Surface
		bgPanel = new JPanel();
		bgPanel.setLayout(new BoxLayout(bgPanel, BoxLayout.Y_AXIS));
		bgPanel.setVisible(true);
		bgPanel.setPreferredSize(new java.awt.Dimension(prefXSize,prefYSize-20));
		
		{//TOP: Display tasks left, and tasks that were run right
			int subXSize = prefXSize, subYSize = 200;
			JPanel topPanel = new JPanel();
			topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
			topPanel.setVisible(true);
			topPanel.setPreferredSize(new java.awt.Dimension(subXSize,subYSize));
			{
				JPanel imPanel = new JPanel();
				imPanel.setLayout(new BorderLayout());
				imPanel.setVisible(true);
				imPanel.setPreferredSize(new java.awt.Dimension((int)((double)(subXSize/2.0)),subYSize));
				{
					JLabel spacer = new JLabel("Remaining files to process:",SwingConstants.LEFT);
					spacer.setMinimumSize(new java.awt.Dimension((int)((double)(subXSize/2.0)-20),60));
					spacer.setVisible(true);
					imPanel.add(spacer,BorderLayout.NORTH); 
				}
				{
					jScrollPaneLeft = new JScrollPane();
					jScrollPaneLeft.setHorizontalScrollBarPolicy(30);
					jScrollPaneLeft.setVerticalScrollBarPolicy(20);
					jScrollPaneLeft.setPreferredSize(new java.awt.Dimension((int)((double)(subXSize/2.0)-10), subYSize-60));
					imPanel.add(jScrollPaneLeft,BorderLayout.CENTER); 
					{
						ListModel<String> ListeModel = new DefaultComboBoxModel<>(new String[] { "" });
						ListeLeft = new JList<String>();
						jScrollPaneLeft.setViewportView(ListeLeft);
						ListeLeft.setModel(ListeModel);
					}
				}	
				topPanel.add(imPanel);
			}
			{
				JPanel imPanel = new JPanel();
				imPanel.setLayout(new BorderLayout());
				imPanel.setVisible(true);
				imPanel.setPreferredSize(new java.awt.Dimension((int)((double)(subXSize/2.0)),subYSize));
				{
					JLabel spacer = new JLabel("Processed files:",SwingConstants.LEFT);
					spacer.setMinimumSize(new java.awt.Dimension((int)((double)(subXSize/2.0)-20),60));
					spacer.setVisible(true);
					imPanel.add(spacer,BorderLayout.NORTH); 
				}
				{	
					jScrollPaneRight = new JScrollPane();
					jScrollPaneRight.setHorizontalScrollBarPolicy(30);
					jScrollPaneRight.setVerticalScrollBarPolicy(20);
					jScrollPaneRight.setPreferredSize(new java.awt.Dimension((int)((double)(subXSize/2.0)-10), subYSize-60));
					imPanel.add(jScrollPaneRight,BorderLayout.CENTER); 
					{
						ListModel<String> ListeModel = new DefaultComboBoxModel<>(new String[] { "" });
						ListeRight = new JList<String>();
						jScrollPaneRight.setViewportView(ListeRight);
						ListeRight.setModel(ListeModel);
					}
				}
				topPanel.add(imPanel);
			}				
			bgPanel.add(topPanel);
		}
		{
			JPanel spacer = new JPanel();
			spacer.setMaximumSize(new java.awt.Dimension(prefXSize,10));
			spacer.setVisible(true);
			bgPanel.add(spacer);
		}
		{
			progressBar = new JProgressBar();
			progressBar = new JProgressBar(0, 100);
			progressBar.setPreferredSize(new java.awt.Dimension(prefXSize,40));
			progressBar.setStringPainted(true);
			progressBar.setValue(0);
			progressBar.setString("no analysis started!");
			bgPanel.add(progressBar);	
		}
		{
			// Time label
			timeLabel = new JLabel("Elapsed: 00:00:00 | Estimated remaining: --:--:--");
			timeLabel.setHorizontalAlignment(SwingConstants.CENTER);
			timeLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
			timeLabel.setPreferredSize(new java.awt.Dimension(prefXSize, 20));
			timeLabel.setFont(new java.awt.Font("Sansserif", java.awt.Font.PLAIN, 10));
			bgPanel.add(timeLabel);
		}
		{
			JPanel spacer = new JPanel();
			spacer.setMaximumSize(new java.awt.Dimension(prefXSize,10));
			spacer.setVisible(true);
			bgPanel.add(spacer);
		}
		{
			JPanel imPanel = new JPanel();
			imPanel.setLayout(new BorderLayout());
			imPanel.setVisible(true);
			imPanel.setPreferredSize(new java.awt.Dimension(prefXSize,140));
			{
				JLabel spacer = new JLabel("Notifications:", SwingConstants.LEFT);
				spacer.setMinimumSize(new java.awt.Dimension(prefXSize,40));
				spacer.setVisible(true);
				imPanel.add(spacer, BorderLayout.NORTH);
			}
			{	
				jScrollPaneBottom = new JScrollPane();
				jScrollPaneBottom.setHorizontalScrollBarPolicy(30);
				jScrollPaneBottom.setVerticalScrollBarPolicy(20);
				jScrollPaneBottom.setPreferredSize(new java.awt.Dimension(prefXSize, 100));
				imPanel.add(jScrollPaneBottom, BorderLayout.CENTER);
				{
					ListModel<String> ListeModel = new DefaultComboBoxModel<>(new String[] { "" });
					ListeBottom = new JList<String>();
					jScrollPaneBottom.setViewportView(ListeBottom);
					ListeBottom.setModel(ListeModel);
				}
			}
			bgPanel.add(imPanel);
		}
		getContentPane().add(bgPanel);		
	}
	
	@Override
	public void actionPerformed(ActionEvent ae) {
		// Future use
	}
	
	public void startTiming() {
		startTime = System.currentTimeMillis();
		updateTimeDisplay();
	}
	
	private void updateTimeDisplay() {
		if(startTime == 0) return;
		
		long currentTime = System.currentTimeMillis();
		long elapsed = currentTime - startTime;
		
		// Calculate elapsed time
		long elapsedSeconds = elapsed / 1000;
		long hours = elapsedSeconds / 3600;
		long minutes = (elapsedSeconds % 3600) / 60;
		long seconds = elapsedSeconds % 60;
		
		String elapsedStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);
		
		// Calculate estimated remaining time
		String remainingStr = "--:--:--";
		if(task > 0 && progressBar.getValue() > 0) {
			double percentComplete = progressBar.getValue() / 100.0;
			if(percentComplete > 0.01) { // Only estimate after 1% complete
				long estimatedTotal = (long)(elapsed / percentComplete);
				long remaining = estimatedTotal - elapsed;
				
				if(remaining > 0) {
					long remainingSeconds = remaining / 1000;
					long remHours = remainingSeconds / 3600;
					long remMinutes = (remainingSeconds % 3600) / 60;
					long remSeconds = remainingSeconds % 60;
					remainingStr = String.format("%02d:%02d:%02d", remHours, remMinutes, remSeconds);
				} else {
					remainingStr = "00:00:00";
				}
			}
		}
		
		timeLabel.setText("Elapsed: " + elapsedStr + " | Estimated remaining: " + remainingStr);
	}
	
	public void moveTask(int i){
		if(!dataLeft.isEmpty()) {
			String completedTask = dataLeft.removeFirst();
			dataRight.addFirst(completedTask);
			
			updateLeftList();
			updateRightList();
		}
		
		if(task == tasks){
			updateTimeDisplay();
			if(errorsAvailable){
				replaceBarText("analysis done but some tasks failed (see notifications)!");
				progressBar.setValue(100); 		
				progressBar.setStringPainted(true);
				progressBar.setForeground(Color.red);
			}else if(notificationsAvailable){
				replaceBarText("analysis done, but some notifications are available!");
				progressBar.setValue(100); 
				progressBar.setStringPainted(true);
				progressBar.setForeground(new Color(255,130,0));
			}else{
				replaceBarText("analysis done!");
				progressBar.setStringPainted(true);
				progressBar.setForeground(new Color(0,140,0));
			}
			progressBar.setValue(100);
			forceUIUpdate(); // Force final update
		}else{
			taskFraction = 0.0;
			task++;
		}
	}
	
	// New in v0.2.2 - throttle messages for speed up during heavy logging
	public void notifyMessage(String message, int type){
		if(type == ProgressReporter.ERROR){
			errorsAvailable = true;
		}else if(type == ProgressReporter.NOTIFICATION){
			notificationsAvailable = true;
		}
		notifications.addFirst(message);
		scheduleNotificationUpdate();
		if(type == ProgressReporter.ERROR)	forceUIUpdate();      // never throttle errors
	}
	
	public void notifyMessageWithTaskNr(String message, int type){
		if(dataRight == null){
			notifyMessage("Task 1: " + message, type);
		}else{
			notifyMessage("Task " + dataRight.size() + ": " + message, type);
		}
	}
	
	public void addToBar(double addFractionOfTask){
		taskFraction += addFractionOfTask;
		if(taskFraction >= 1.0){
			taskFraction = 0.9;
		}
		int newValue = (int)Math.round(((double)(task-1)/tasks)*100.0+taskFraction*(100/tasks));
		progressBar.setValue(newValue);
		updateUITimed();
	}
	
	public void setBar(double fractionOfTask){
		taskFraction = fractionOfTask;
		if(taskFraction > 1.0){
			taskFraction = 0.9;
		}
		int newValue = (int)Math.round(((double)(task-1)/tasks)*100.0+taskFraction*(100/tasks));
		progressBar.setValue(newValue);
		updateUITimed();
	}
	
	public void updateBarText(String text){
		progressBar.setString("Task " + task + "/" + tasks + ": " + text);
		updateUITimed();
	}
	
	public void replaceBarText(String text){			
		progressBar.setString(text);
		updateUITimed();
	}
	
	// Helper methods to update lists efficiently
	private void updateLeftList() {
		ListeLeft.setListData(dataLeft.toArray(new String[0]));
	}
	
	private void updateRightList() {
		ListeRight.setListData(dataRight.toArray(new String[0]));
	}
	
	private void updateNotificationsList() {
		ListeBottom.setListData(notifications.toArray(new String[0]));
		notificationUpdatePending = false;
	}

	// Mark that notifications need updating
	private void scheduleNotificationUpdate() {
		notificationUpdatePending = true;
		updateUITimed();
	}

	// Throttle UI updates to prevent slowdown
	private void updateUITimed() {
		long now = System.currentTimeMillis();
		if (now - lastUIUpdate > UI_UPDATE_INTERVAL_MS) {
			forceUIUpdate();
		}
	}

	private void forceUIUpdate() {
		try {
			// Update notifications list only if there are pending updates
			if(notificationUpdatePending) {
				updateNotificationsList();
			}
			updateTimeDisplay();
			bgPanel.updateUI();
			lastUIUpdate = System.currentTimeMillis();
		} catch(Exception e) {
			// Ignore UI update errors
		}
	}
}