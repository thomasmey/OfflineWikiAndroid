package de.m3y3r.offlinewiki.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import androidx.room.Room;
import android.net.Uri;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.m3y3r.offlinewiki.utility.*;
import de.m3y3r.offlinewiki.Config;
import de.m3y3r.offlinewiki.pagestore.room.*;

public class DownloadJob extends JobService implements Runnable {

	private Thread worker;
	private String xmlDumpUrlString;
	private JobParameters jobParameters;

	private File getTargetDirectory(long lenRemote) {
		// choose target directory
		File[] targetDirs = this.getApplicationContext().getExternalFilesDirs(null);
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

	private String getBaseName(String etag, String url) {
		return etag.replaceAll("\"","") + "-" + Uri.parse(url).getLastPathSegment();
	}

	@Override
	public void run() {
//		SearchActivity.updateProgressBar(0, 1);

		AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "title-database").build();
		String path = db.getOpenHelper().getReadableDatabase().getPath();
		System.out.println(path);
		try {
			XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
			if(xmlDumpEntity != null && xmlDumpEntity.isDownloadFinished()) {
				jobFinished(jobParameters, false);
				return;
			}

			//TODO: Buffer size is aligned to be SD card friendly, is 4MB okay?
			int bufferSize = (int) Math.pow(2, 22);
			Downloader downloader = new Downloader(xmlDumpUrlString, null, null, bufferSize);

			if(xmlDumpEntity != null) {
				downloader.setDumpFile(new SplitFile(new File(xmlDumpEntity.getDirectory()), xmlDumpEntity.getBaseName()));
			} else {
				Map<String, List<String>> headers = downloader.doHead();
				long lenRemote = Downloader.getFileSizeFromHeaders(headers);
				final String etag = headers.get("ETag").get(0);
				File targetDirectory = getTargetDirectory(lenRemote);
				downloader.setDumpFile(new SplitFile(targetDirectory.getAbsoluteFile(), getBaseName(etag, xmlDumpUrlString)));
				// create a new xml dump entity
				xmlDumpEntity = new XmlDumpEntity();
				xmlDumpEntity.setEtag(etag);
				xmlDumpEntity.setUrl(downloader.getUrl().toExternalForm());
				xmlDumpEntity.setLength(lenRemote);
				xmlDumpEntity.setDirectory(downloader.getDumpFile().getParentFile().getAbsolutePath());
				xmlDumpEntity.setBaseName(downloader.getDumpFile().getBaseName());
				db.getDao().insertXmlDumpEntity(xmlDumpEntity);
			}

			long currentFileSize = downloader.getDumpFile().length();
			if(currentFileSize == xmlDumpEntity.getLength()) {
				xmlDumpEntity.setDownloadFinished(true);
				db.getDao().updateXmlDumpEntity(xmlDumpEntity);
				jobFinished(jobParameters, false);
				return;
			}

			downloader.setRestartPos(currentFileSize);
			DownloadEventListener listener = new DownloadEventListener() {
				@Override
				public void onDownloadStart(EventObject event) {
				}

				@Override
				public void onDownloadFinished(EventObject event) {
					Downloader d = (Downloader) event.getSource();
					XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(d.getUrl().toExternalForm());
					xmlDumpEntity.setDownloadFinished(true);
					db.getDao().updateXmlDumpEntity(xmlDumpEntity);
				}

				@Override
				public void onProgress(EventObject event, long currentFileSize) {
				}

				@Override
				public void onNewByte(EventObject event, int b) {
				}
			};
			downloader.addEventListener(listener);
			downloader.run();

			long fileSize = downloader.getDumpFile().length();
			System.out.println("Total file size: " + fileSize);
			xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
			jobFinished(jobParameters, !xmlDumpEntity.isDownloadFinished());
		} catch (IOException e) {
			Logger.getLogger(Config.getInstance().getLoggerName()).log(Level.SEVERE, "Background task failed!", e);
		} finally {
//			SearchActivity.updateProgressBar(0, 2);
			db.close();
		}
	}

	@Override
	public boolean onStartJob(JobParameters jobParameters) {
		String xmlDumpUrl = PreferenceManager.getDefaultSharedPreferences(this).getString("xmlDumpUrl", null);
		this.jobParameters = jobParameters;
		this.xmlDumpUrlString = xmlDumpUrl;
		worker = new Thread( this,"OfflineWiki-DownloadJob");
		worker.start();
		return true;
	}

	@Override
	public boolean onStopJob(JobParameters jobParameters) {
		Thread w = worker;
		worker = null;
		if(w != null && w.isAlive()) w.interrupt();
		return true;
	}
}