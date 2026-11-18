package ciliaQ_jnh;

/**
 * Everything CiliaQ needs from a progress reporter.
 *
 * Exists so the plugin can communicate under --headless.
 * ProgressDialog implements this for interactive use; ConsoleProgress
 * implements it for headless and macro use.
 * 
 * Added in August 2026 for version v0.2.2.
 * 
 * Copyright (C) @author Jan Niklas Hansen.
 *  */
public interface ProgressReporter {

	public static final int ERROR = 0, NOTIFICATION = 1, LOG = 2;

	void updateBarText(String text);
	void replaceBarText(String text);
	void setBar(double fractionOfTask);
	void addToBar(double addFractionOfTask);
	void moveTask(int task);
	void notifyMessage(String message, int type);
	void notifyMessageWithTaskNr(String message, int type);

	/**
	 * The ERROR and NOTIFICATION messages of one task (1 = the first image), oldest first, each as
	 * "ERROR<tab>message" or "WARNING<tab>message", for that image's log file (<name>_CQ_log.txt).
	 * Each implementation stores next to every message the task during which it came. LOG messages
	 * are left out. As in CiliaQ Preparator.
	 */
	java.util.List<String> getNotes(int task);
	
	/** No-op in the console implementation. */
	void setVisible(boolean visible);

	/** No-op in the console implementation. */
	void setLocation(int x, int y);
}