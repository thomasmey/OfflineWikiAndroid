package de.m3y3r.offlinewiki.pagestore.bzip2.index.room;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.m3y3r.offlinewiki.Config;
import de.m3y3r.offlinewiki.frontend.SearchActivity;
import de.m3y3r.offlinewiki.pagestore.bzip2.index.Indexer;
import de.m3y3r.offlinewiki.pagestore.bzip2.index.IndexerEvent;
import de.m3y3r.offlinewiki.pagestore.bzip2.index.IndexerEventListener;
import de.m3y3r.offlinewiki.pagestore.room.AppDatabase;
import de.m3y3r.offlinewiki.pagestore.room.TitleEntity;

public class RoomIndexerEventHandler implements IndexerEventListener {

	private static final int MAX_TITLES = 100;
	private final AppDatabase db;
	private final int xmlDumpId;

	private TitleEntity[] titles = new TitleEntity[MAX_TITLES];
	private int titlesIdx;
	private int titleCount = 0;
	private final Logger logger;

	public RoomIndexerEventHandler(AppDatabase db, int xmlDumpId) {
		if(db == null) throw new IllegalArgumentException();
		this.db = db;
		this.xmlDumpId = xmlDumpId;
		this.logger = Logger.getLogger(Config.getInstance().getLoggerName());
	}

	@Override
	public void onBeforeStartOfStream(IndexerEvent event) {
		titlesIdx = 0;
		titleCount = 0;
	}

	@Override
	public void onPageStart(IndexerEvent event) {
	}

	@Override
	public void onEndOfStream(IndexerEvent event, boolean normalEnd) {
		// store remaining buffer item
		commitTitles();
		updateProgressBar();
	}

	public void updateProgressBar() {
		int totalCount = db.getDao().getTotalBlockCount(xmlDumpId);
		int processedCount = db.getDao().getProcessedBlockCount(xmlDumpId);
		int progress = 0;
		int perCent = (totalCount / 100);
		if(perCent > 0) {
			progress = (int) (processedCount / perCent);
		}
		SearchActivity.updateProgressBar(progress, 0);
	}

	@Override
	public void onNewTitle(IndexerEvent event, String title, long pageTagStartPos) {
		addToIndex((Indexer) event.getSource(), title, pageTagStartPos);
		titleCount++;
	}

	@Override
	public void onPageTagEnd(IndexerEvent event, long currentTagEndPos) {
		if (titleCount > 0 && titleCount % MAX_TITLES == 0) {
			logger.log(Level.FINE, "Processed {0} pages", titleCount);
			commitTitles();
		}
	}

	private void commitTitles() {
		TitleEntity[] titles = getTitles();
		for(TitleEntity t: titles) {
			t.setXmlDumpId(xmlDumpId);
		}
		db.getDao().insertAllTitleEntity(titles);
	}

	private TitleEntity[] getTitles() {
		if (titlesIdx < MAX_TITLES) // last commit case, can be smaller
			return Arrays.copyOf(titles, titlesIdx);

		titlesIdx = 0;
		return titles;
	}

	private void addToIndex(Indexer indexer, String pageTitle, long currentTagUncompressedPosition) {

		TitleEntity title = new TitleEntity();
		title.setTitle(pageTitle);
		title.setPageUncompressedPosition(currentTagUncompressedPosition);
		title.setBlockUncompressedPosition(0); // TODO
		title.setBlockPositionInBits(indexer.getBlockStartPosition());
		titles[titlesIdx++] = title;
	}
}
