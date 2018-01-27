package de.m3y3r.offlinewiki.utility;


import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.m3y3r.offlinewiki.Config;
import de.m3y3r.offlinewiki.frontend.SearchActivity;
import de.m3y3r.offlinewiki.pagestore.room.AppDatabase;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;

public class Downloader  implements Runnable {

	private final AppDatabase db;
	private final URL url;
	private final Context ctx;

	private String etag;
	private long lenRemote;

	public Downloader(Context ctx, AppDatabase db, String xmlDumpUrl) throws MalformedURLException {
		this.ctx = ctx;
		this.db = db;
		this.url = new URL(xmlDumpUrl);
	}

	private void getEtagAndSize() {
		// get remote size
		try {
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("HEAD");
			con.connect();

			// process headers
			Map<String, List<String>> headers = con.getHeaderFields();
			List<String> lens = headers.get("Content-Length");

			lenRemote = Long.parseLong(lens.get(0));
			etag = headers.get("ETag").get(0);
		} catch (IOException e) {
			Logger.getLogger(Config.LOGGER_NAME).log(Level.SEVERE, "HEAD failed!", e);
		}
	}

	private File getTargetDirectory(long lenRemote) {
		// choose target directory
		File[] targetDirs = ctx.getExternalFilesDirs(null);
		if(targetDirs.length > 1)
			return targetDirs[1]; // always prefer external storage
		else
			return targetDirs[0];

/*
		return Arrays.stream(targetDirs).filter(f -> {
			StatFs s = new StatFs(f.getAbsolutePath());
			long b = s.getAvailableBytes();
			return b > lenRemote;
		}).findFirst().get();
*/
	}

	private String getBaseName(String etag) {
		return etag.replaceAll("\"","") + "-" + Uri.parse(url.toExternalForm()).getLastPathSegment();
	}

	@Override
	public void run() {

		// get remote size and etag
		getEtagAndSize();

		final XmlDumpEntity xmlDumpEntityOrig = db.getDao().getXmlDumpEntityByUrl(url.toExternalForm());
		XmlDumpEntity xmlDumpEntity = xmlDumpEntityOrig;
		if(xmlDumpEntity == null) {
			// create a new xml dump entity
			File targetDirectory = getTargetDirectory(lenRemote);
			xmlDumpEntity = new XmlDumpEntity();
			xmlDumpEntity.setEtag(etag);
			xmlDumpEntity.setUrl(url.toExternalForm());
			xmlDumpEntity.setLength(lenRemote);
			xmlDumpEntity.setDirectory(targetDirectory.getAbsolutePath());
			xmlDumpEntity.setBaseName(getBaseName(etag));
			db.getDao().insertXmlDumpEntity(xmlDumpEntity);
		} else {
			if(!xmlDumpEntity.getEtag().equals(etag)) {
				throw new IllegalStateException();
			}
		}

		SplitFile dumpFile = new SplitFile(new File(xmlDumpEntity.getDirectory()), xmlDumpEntity.getBaseName());
		if(dumpFile.length() == xmlDumpEntity.getLength()) {
			xmlDumpEntity.setDownloadFinished(true);
			db.getDao().updateXmlDumpEntity(xmlDumpEntity);
			return;
		}

		try(SplitFileOutputStream outputStream = new SplitFileOutputStream(dumpFile, Config.SPLIT_SIZE)) {
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			if(xmlDumpEntityOrig != null) { // restart existing download
				long lenCommited = dumpFile.length();
				con.addRequestProperty("Range", "bytes=" + lenCommited + "-");
				outputStream.seek(lenCommited);
			}
			con.connect();
			download(con, outputStream, xmlDumpEntity.getLength(), dumpFile);
		} catch (IOException e) {
			Logger.getLogger(Config.LOGGER_NAME).log(Level.SEVERE, "GET failed!", e);
		}
	}

	private void download(HttpURLConnection con, OutputStream o, long lenRemote, SplitFile dumpFile) throws IOException {
		long c = 0, size = (long) Math.pow(2, 22);

		try (InputStream conIn = con.getInputStream();
			 BufferedInputStream in = new BufferedInputStream(conIn);
			 BufferedOutputStream out = new BufferedOutputStream(o)) {
			int b = in.read();
			while(b >= 0) {
				if(Thread.interrupted())
					return;

				out.write(b);
				b = in.read();
				c++;

				if(c % size == 0) {
					int progress = (int) ((dumpFile.length()) / (lenRemote / 100));
					SearchActivity.updateProgressBar(progress, 0);
				}
			}
		}
	}
}
