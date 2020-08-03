package de.m3y3r.offlinewiki.utility;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.m3y3r.offlinewiki.Config;

public class Downloader implements Runnable {

	private final URL url;
	private SplitFile dumpFile;
	private Long restartPos;
	private final List<DownloadEventListener> eventListeners;
	private final int bufferSize;

	public Downloader(String xmlDumpUrl, SplitFile targetDumpFile, Long restartPos, int bufferSize) throws MalformedURLException {
		this.url = new URL(xmlDumpUrl);
		this.dumpFile = targetDumpFile;
		this.bufferSize = bufferSize;
		this.restartPos = restartPos;
		this.eventListeners = new CopyOnWriteArrayList<>();
	}

	public void addEventListener(DownloadEventListener el) {
		eventListeners.add(el);
	}

	/**
	 * get HTTP headers only
	 * @return
	 */
	public Map<String, List<String>> doHead() {
		try {
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("HEAD");
			con.connect();

			// process headers
			Map<String, List<String>> headers = con.getHeaderFields();
//			etag = headers.get("ETag").get(0);
			return headers;
		} catch (IOException e) {
			Logger.getLogger(Config.getInstance().getLoggerName()).log(Level.SEVERE, "HEAD failed!", e);
		}
		return null;
	}

	public static long getFileSizeFromHeaders(Map<String, List<String>> headers) {
		List<String> lens = headers.get("Content-Length");
		// get remote size
		return Long.parseLong(lens.get(0));
	}

	@Override
	public void run() {
		fireEventDownloadStart();

		try(SplitFileOutputStream outputStream = new SplitFileOutputStream(dumpFile, Config.getInstance().getSplitSize())) {
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			if(restartPos != null) { // restart existing download
				if(restartPos != dumpFile.length()) {
					throw new IllegalStateException();
				}
				con.addRequestProperty("Range", "bytes=" + restartPos + "-");
				outputStream.seek(restartPos);
			}
			con.connect();
			boolean normalEnd;
			try (InputStream in = con.getInputStream()) {
				normalEnd = download(in, outputStream);
			}
			if(normalEnd)
				fireEventDownloadFinished();
		} catch (IOException e) {
			Logger.getLogger(Config.getInstance().getLoggerName()).log(Level.SEVERE, "GET failed!", e);
		}
	}

	private boolean download(InputStream in, OutputStream out) throws IOException {
		long c = 0;
		if(restartPos != null) c = restartPos;

		try (
				BufferedInputStream bin = new BufferedInputStream(in, bufferSize);
				BufferedOutputStream bout = new BufferedOutputStream(out, bufferSize)
			) {
			int b = bin.read();

			while(b >= 0) {
				if(Thread.interrupted())
					return false;

				fireEventNewByte(b);
				bout.write(b);
				b = bin.read();

				c++;
				if(c % bufferSize == 0) {
					//FIXME:
					bout.flush();
					fireEventProgress(c);
				}
			}
			return true;
		}
	}

	private void fireEventNewByte(int b) {
		for(DownloadEventListener e: eventListeners) {
			e.onNewByte(null, b);
		}
	}

	private void fireEventProgress(long currentFileSize) {
		for(DownloadEventListener e: eventListeners) {
			EventObject event = new EventObject(this);
			e.onProgress(event,currentFileSize);
		}
	}

	private void fireEventDownloadFinished() {
		for(DownloadEventListener e: eventListeners) {
			EventObject event = new EventObject(this);
			e.onDownloadFinished(event);
		}
	}

	private void fireEventDownloadStart() {
		for(DownloadEventListener e: eventListeners) {
			EventObject event = new EventObject(this);
			e.onDownloadStart(event);
		}
	}

	public static String getBaseNameFromUrl(String xmlDumpUrl) {
		int i = xmlDumpUrl.lastIndexOf('/');
		if(i < 0)
			return null;

		return xmlDumpUrl.substring(i + 1, xmlDumpUrl.length());
	}

	public URL getUrl() {
		return url;
	}

	public SplitFile getDumpFile() {
		return dumpFile;
	}

	public void setDumpFile(SplitFile splitFile) {
		this.dumpFile = splitFile;
	}

	public void setRestartPos(Long restartPos) {
		this.restartPos = restartPos;
	}
}
