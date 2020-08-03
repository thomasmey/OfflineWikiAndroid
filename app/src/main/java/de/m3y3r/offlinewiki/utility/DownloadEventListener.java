package de.m3y3r.offlinewiki.utility;

import java.util.EventObject;

public interface DownloadEventListener {

	void onDownloadStart(EventObject event);
	void onDownloadFinished(EventObject event);

	/**
	 * fired for each new downloaded blocksize block 
	 * @param event
	 * @param currentFileSize
	 */
	void onProgress(EventObject event, long currentFileSize);

	void onNewByte(EventObject event, int b);

}
