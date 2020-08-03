package de.m3y3r.offlinewiki.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import androidx.room.Room;
import android.preference.PreferenceManager;

import java.io.File;
import java.util.concurrent.TimeUnit;

import de.m3y3r.offlinewiki.frontend.SearchActivity;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.room.RoomBlockController;
import de.m3y3r.offlinewiki.pagestore.bzip2.index.IndexerController;
import de.m3y3r.offlinewiki.pagestore.bzip2.index.room.RoomIndexerEventHandler;
import de.m3y3r.offlinewiki.pagestore.room.AppDatabase;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;
import de.m3y3r.offlinewiki.utility.SplitFile;

public class IndexerJob extends JobService implements Runnable {

	private Thread worker;
	private String xmlDumpUrlString;
	private JobParameters jobParameters;

	@Override
	public void run() {

		AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "title-database").build();
		try {
			XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);

			if(xmlDumpEntity != null && xmlDumpEntity.isIndexFinished()) {
				jobFinished(jobParameters, false);
				return;
			}

			// wait for DownloadJob and BLockFinderJob to at least process something
			while(xmlDumpEntity == null) {
				Thread.sleep(TimeUnit.SECONDS.toMillis(60));
				xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
			}

			RoomIndexerEventHandler eventHandler = new RoomIndexerEventHandler(db, xmlDumpEntity.getId());
			SearchActivity.updateProgressBar(0, 1);
			eventHandler.updateProgressBar();

			SplitFile dumpFile = new SplitFile(new File(xmlDumpEntity.getDirectory()), xmlDumpEntity.getBaseName());
			RoomBlockController blockController = new RoomBlockController(db, xmlDumpEntity.getId());
			IndexerController indexerController = new IndexerController(dumpFile, eventHandler, blockController);
			indexerController.run();

			if(xmlDumpEntity.isDownloadFinished() && xmlDumpEntity.isBlockFinderFinished() &&
				db.getDao().getTotalBlockCount(xmlDumpEntity.getId()) -
				db.getDao().getProcessedBlockCount(xmlDumpEntity.getId()) == 0) {
				xmlDumpEntity.setIndexFinished(true);
				db.getDao().updateXmlDumpEntity(xmlDumpEntity);
			}

			jobFinished(jobParameters, !xmlDumpEntity.isIndexFinished());
		} catch (InterruptedException e) {
			// we were interrupted, okay, we try again next time
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
		worker = new Thread( this,"OfflineWiki-IndexerJob");
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