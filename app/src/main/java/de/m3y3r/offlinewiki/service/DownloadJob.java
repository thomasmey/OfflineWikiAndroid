package de.m3y3r.offlinewiki.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.arch.persistence.room.Room;
import android.preference.PreferenceManager;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.m3y3r.offlinewiki.Config;
import de.m3y3r.offlinewiki.frontend.SearchActivity;
import de.m3y3r.offlinewiki.pagestore.room.AppDatabase;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;
import de.m3y3r.offlinewiki.utility.Downloader;

public class DownloadJob extends JobService implements Runnable {

	private Thread worker;
	private String xmlDumpUrlString;
	private JobParameters jobParameters;

	@Override
	public void run() {
		SearchActivity.updateProgressBar(0, 1);

		AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "title-database").build();
		try {
			XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
			if(xmlDumpEntity == null || !xmlDumpEntity.isDownloadFinished()) {
				Downloader downloader = new Downloader(getApplicationContext(), db,  xmlDumpUrlString);
				downloader.run();
			}

			xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
			if(xmlDumpEntity.isDownloadFinished())
				jobFinished(jobParameters, false);

		} catch (IOException e) {
			Logger.getLogger(Config.LOGGER_NAME).log(Level.SEVERE, "Background task failed!", e);
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
		worker = new Thread( this,"OfflineWiki-Background-Worker");
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