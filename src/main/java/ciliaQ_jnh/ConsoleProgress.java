package ciliaQ_jnh;

import ij.IJ;

/**
 * Progress reporting for headless and macro runs: everything goes to the ImageJ log, which
 * --headless --console prints.
 * 
 * Added in August 2026 for version v0.2.2 to implement console-based communication without GUI.
 * 
 * Copyright (C) @author Jan Niklas Hansen.
 */
public class ConsoleProgress implements ProgressReporter {

	public boolean notificationsAvailable = false, errorsAvailable = false;
	private final String [] taskList;
	private final int tasks;
	private int task = 1;
	private String lastText = "";

	public ConsoleProgress(String [] taskList, int tasks) {
		this.taskList = taskList == null ? new String [0] : taskList.clone();
		this.tasks = tasks;
		IJ.log("CiliaQ: " + tasks + " task(s) queued.");
	}

	public void updateBarText(String text) {
		// Suppress repeats: the bar text is updated inside per-cilium and per-slice loops.
		if (text == null || text.equals(lastText)) return;
		lastText = text;
		IJ.log("  [" + task + "/" + tasks + "] " + text);
	}

	public void replaceBarText(String text) {
		updateBarText(text);
	}

	public void setBar(double fractionOfTask) { /* no bar to draw */ }

	public void addToBar(double addFractionOfTask) { /* no bar to draw */ }

	public void moveTask(int task) {
		String name = (task >= 0 && task < taskList.length) ? taskList[task] : "";
		IJ.log("  finished task " + (task + 1) + "/" + tasks + (name.isEmpty() ? "" : ": " + name));
		this.task = Math.min(task + 2, tasks);
	}

	public void notifyMessage(String message, int type) {
		if (type == ERROR) {
			errorsAvailable = true;
			IJ.log("ERROR: " + message);
		} else if (type == NOTIFICATION) {
			notificationsAvailable = true;
			IJ.log("NOTE: " + message);
		} else {
			IJ.log(message);
		}
	}

	public void notifyMessageWithTaskNr(String message, int type) {
		notifyMessage("Task " + task + ": " + message, type);
	}

	public void setVisible(boolean visible) { /* nothing to show */ }

	public void setLocation(int x, int y) { /* nothing to place */ }
}