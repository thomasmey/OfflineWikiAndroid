package de.m3y3r.offlinewiki.service;


import android.app.job.JobParameters;
import android.app.job.JobService;
import android.preference.PreferenceManager;

import java.io.File;
import java.util.concurrent.TimeUnit;

import androidx.room.Room;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockFinder;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.room.RoomBlockController;
import de.m3y3r.offlinewiki.pagestore.room.AppDatabase;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;
import de.m3y3r.offlinewiki.utility.SplitFile;

public class BlockFinderJob extends JobService implements Runnable {

	private Thread worker;
	private String xmlDumpUrlString;
	private JobParameters jobParameters;

	@Override
	public void run() {

		AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "title-database").build();
		try {
			XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);

			if(xmlDumpEntity != null && xmlDumpEntity.isBlockFinderFinished()) {
				jobFinished(jobParameters, false);
				return;
			}

			// wait for DownloadJob to download the first bits
			while(xmlDumpEntity == null) {
				Thread.sleep(TimeUnit.SECONDS.toMillis(5));
				xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrlString);
			}

			SplitFile dumpFile = new SplitFile(new File(xmlDumpEntity.getDirectory()), xmlDumpEntity.getBaseName());
			RoomBlockController blockController = new RoomBlockController(db, xmlDumpEntity.getId());
			BlockFinder blockFinder = new BlockFinder(dumpFile, blockController);
			blockFinder.addEventListener(blockController);
			blockFinder.run();

			if(xmlDumpEntity.isDownloadFinished() && blockController.isNormalEnd()) {
				xmlDumpEntity.setBlockFinderFinished(true);
				db.getDao().updateXmlDumpEntity(xmlDumpEntity);
			}
		} catch (InterruptedException e) {
			// we were interrupted, okay, we try again next time
		} finally {
			db.close();
		}
	}

	@Override
	public boolean onStartJob(JobParameters jobParameters) {
		String xmlDumpUrl = PreferenceManager.getDefaultSharedPreferences(this).getString("xmlDumpUrl", null);
		this.jobParameters = jobParameters;
		this.xmlDumpUrlString = xmlDumpUrl;
		worker = new Thread( this,"OfflineWiki-BlockFinderJob");
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
