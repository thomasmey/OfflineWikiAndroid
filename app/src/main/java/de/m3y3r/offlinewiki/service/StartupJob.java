package de.m3y3r.offlinewiki.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.arch.persistence.room.Room;
import android.content.Context;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.m3y3r.offlinewiki.Config;
import de.m3y3r.offlinewiki.frontend.SearchActivity;
import de.m3y3r.offlinewiki.pagestore.bzip2.Indexer;
import de.m3y3r.offlinewiki.pagestore.room.TitleDatabase;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;
import de.m3y3r.offlinewiki.utility.Downloader;

public class StartupJob extends JobService implements Runnable {

	private Thread worker;

	private String xmlDumpUrlString;
	private Context ctx;

	private JobParameters jobParameters;

	@Override
	public void run() {

		if(xmlDumpUrlString == null) {
			throw new IllegalArgumentException();
		}

		TitleDatabase db = Room.databaseBuilder(getApplicationContext(), TitleDatabase.class, "title-database").build();

		SearchActivity.updateProgressBar(0, 2);

		// get entry from db
		XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
		try {

			if(xmlDumpEntity == null || !xmlDumpEntity.isDownloadFinished()) {
				Downloader downloader = new Downloader(ctx, db, xmlDumpEntity, xmlDumpUrlString);
				downloader.run();
			}

			// re-read data from db
			xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);

			assert xmlDumpEntity != null;

			if(!xmlDumpEntity.isIndexFinished()) {
				Indexer indexer = new Indexer(db, xmlDumpEntity);
				indexer.run();
			}

			jobFinished(jobParameters, false);
			SearchActivity.updateProgressBar(0, 1);
		} catch (IOException e) {
			Logger.getLogger(Config.LOGGER_NAME).log(Level.SEVERE, "Background task failed!", e);
		}

	}

	@Override
	public boolean onStartJob(JobParameters jobParameters) {
		this.jobParameters = jobParameters;

		this.ctx = getApplicationContext();
		this.xmlDumpUrlString = jobParameters.getExtras().getString("xmlDumpUrlString");
		worker = new Thread( this,"OfflineWiki-Background-Worker");
		worker.start();
		return true;
	}

	@Override
	public boolean onStopJob(JobParameters jobParameters) {
		Thread w = worker;
		worker = null;
		if(w != null) {
			if(w.isAlive()) {
				w.interrupt();
				return false;
			}
		}
		return true;
	}
}