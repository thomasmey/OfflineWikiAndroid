package de.m3y3r.offlinewiki.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.arch.persistence.room.Room;
import android.preference.PreferenceManager;

import java.util.concurrent.TimeUnit;

import de.m3y3r.offlinewiki.frontend.SearchActivity;
import de.m3y3r.offlinewiki.pagestore.bzip2.Indexer;
import de.m3y3r.offlinewiki.pagestore.room.AppDatabase;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;

public class IndexerJob extends JobService implements Runnable {

	private Thread worker;
	private String xmlDumpUrlString;
	private JobParameters jobParameters;

	@Override
	public void run() {

		AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "title-database").build();
		try {
			XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);

			// wait for download job to download the first bits
			while(xmlDumpEntity == null) {
				Thread.sleep(TimeUnit.SECONDS.toMillis(5));
				xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
			}

			SearchActivity.updateProgressBar(0, 1);
			if(!xmlDumpEntity.isIndexFinished()) {
				Indexer indexer = new Indexer(db, xmlDumpUrlString);
				indexer.run();
			}

			xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
			if(xmlDumpEntity.isIndexFinished())
				jobFinished(jobParameters, false);

		} catch (InterruptedException e) {
			// we were interrupted, okay, we try again next time
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
		if(w != null) w.interrupt();
		return true;
	}
}