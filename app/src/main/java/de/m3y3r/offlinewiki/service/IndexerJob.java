package de.m3y3r.offlinewiki.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.arch.persistence.room.Room;
import android.preference.PreferenceManager;

import de.m3y3r.offlinewiki.frontend.SearchActivity;
import de.m3y3r.offlinewiki.pagestore.bzip2.Indexer;
import de.m3y3r.offlinewiki.pagestore.room.TitleDatabase;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;

public class IndexerJob extends JobService implements Runnable {

	private Thread worker;
	private String xmlDumpUrlString;
	private JobParameters jobParameters;

	@Override
	public void run() {
		SearchActivity.updateProgressBar(0, 1);

		TitleDatabase db = Room.databaseBuilder(getApplicationContext(), TitleDatabase.class, "title-database").build();
		XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
		try {
			assert xmlDumpEntity != null;

			if(!xmlDumpEntity.isIndexFinished()) {
				Indexer indexer = new Indexer(db, xmlDumpEntity);
				indexer.run();
			}
			jobFinished(jobParameters, false);
		} finally {
			SearchActivity.updateProgressBar(0, 2);
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