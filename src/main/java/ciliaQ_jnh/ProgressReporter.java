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

	/** No-op in the console implementation. */
	void setVisible(boolean visible);

	/** No-op in the console implementation. */
	void setLocation(int x, int y);
}