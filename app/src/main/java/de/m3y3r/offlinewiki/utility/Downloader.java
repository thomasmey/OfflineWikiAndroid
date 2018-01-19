package de.m3y3r.offlinewiki.utility;


import android.content.Context;
import android.net.Uri;
import android.os.StatFs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.m3y3r.offlinewiki.Config;
import de.m3y3r.offlinewiki.pagestore.room.TitleDatabase;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;

public class Downloader  implements Runnable {

	private final TitleDatabase db;
	private final URL url;
	private final XmlDumpEntity xmlDumpEntity;
	private final Context ctx;

	private String etag;
	private long lenRemote;

	public Downloader(Context ctx, TitleDatabase db, XmlDumpEntity xmlDumpEntity, String xmlDumpUrl) throws MalformedURLException {
		this.ctx = ctx;
		this.db = db;
		this.xmlDumpEntity = xmlDumpEntity;
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
			e.printStackTrace();
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
		String baseName = etag.replaceAll("\"","") + "-" + Uri.parse(url.toExternalForm()).getLastPathSegment();
		return baseName;
	}

	@Override
	public void run() {
			if(this.xmlDumpEntity != null && this.xmlDumpEntity.isDownloadFinished())
				return;

			// get remote size and etag
			getEtagAndSize();

			XmlDumpEntity xmlDumpEntity = this.xmlDumpEntity;
			if(xmlDumpEntity == null) {
				// create a new xml dump entity
				File targetDirectory = getTargetDirectory(lenRemote);
				xmlDumpEntity = new XmlDumpEntity();
				xmlDumpEntity.setEtag(etag);
				xmlDumpEntity.setUrl(url.toExternalForm());
				xmlDumpEntity.setDirectory(targetDirectory.getAbsolutePath());
				xmlDumpEntity.setBaseName(getBaseName(etag));
				db.getDao().insertXmlDumpEntity(xmlDumpEntity);
			} else {
				if(!xmlDumpEntity.getEtag().equals(etag)) {
					throw new IllegalStateException();
				}
			}

		SplitFile dumpFile = new SplitFile(new File(xmlDumpEntity.getDirectory()), xmlDumpEntity.getBaseName());
		if(dumpFile.length() == lenRemote) {
			xmlDumpEntity.setDownloadFinished(true);
			db.getDao().updateXmlDumpEntity(xmlDumpEntity);
			return;
		}

		try(SplitFileOutputStream outputStream = new SplitFileOutputStream(dumpFile, Config.SPLIT_SIZE)) {
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			if(this.xmlDumpEntity != null) { // restart existing download
				long lenCommited = dumpFile.length();
				con.addRequestProperty("Range", "bytes=" + lenCommited + "-");
				outputStream.seek(lenCommited);
			}
			con.connect();
			download(con, outputStream);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void download(HttpURLConnection con, OutputStream o) throws IOException {
		try (BufferedInputStream in = new BufferedInputStream(con.getInputStream());
			 BufferedOutputStream out = new BufferedOutputStream(o)) {
			int b = in.read();
			while(b >= 0) {
				out.write(b);
				b = in.read();
			}
		}
	}
}
