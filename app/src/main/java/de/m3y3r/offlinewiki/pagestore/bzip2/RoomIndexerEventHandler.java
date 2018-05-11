package de.m3y3r.offlinewiki.pagestore.bzip2;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.m3y3r.offlinewiki.Config;
import de.m3y3r.offlinewiki.frontend.SearchActivity;
import de.m3y3r.offlinewiki.pagestore.room.AppDatabase;
import de.m3y3r.offlinewiki.pagestore.room.TitleEntity;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;

public class RoomIndexerEventHandler implements IndexerEventListener {

	private static final int MAX_TITLES = 100;
	private final AppDatabase db;
	private final long fileSize;
	private final String xmlDumpUrl;

	private TitleEntity[] titles = new TitleEntity[MAX_TITLES];
	private int titlesIdx;
	private int titleCount = 0;
	private final Logger logger;

	public RoomIndexerEventHandler(AppDatabase db, String xmlDumpUrl) {
		if(db == null || xmlDumpUrl == null) throw new IllegalArgumentException();

		XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrl);
		this.xmlDumpUrl = xmlDumpUrl;

		this.db = db;
		this.fileSize = xmlDumpEntity.getLength();
		this.logger = Logger.getLogger(Config.LOGGER_NAME);
	}

	@Override
	public void onPageStart(IndexerEvent event) {

	}

	@Override
	public void onNewBlock(IndexerEvent event, long blockPositionInBits) {
		int progress = (int) ((blockPositionInBits / 8) / (fileSize / 100));
		SearchActivity.updateProgressBar(progress, 0);

	}

	@Override
	public void onEndOfStream(IndexerEvent event) {
		// store remaining buffer item
		XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrl);
		if (xmlDumpEntity.isIndexFinished())
			xmlDumpEntity.setIndexFinished(true);
		commitTitlesAndXmlDumpEntity(getTitles(), xmlDumpEntity);
	}

	@Override
	public void onNewTitle(IndexerEvent event, String title, long pageTagStartPos) {
		try {
			addToIndex((Indexer) event.getSource(), title, pageTagStartPos);
		} catch (IOException e) {
			e.printStackTrace();
		}
		titleCount++;
	}

	@Override
	public void onPageTagEnd(IndexerEvent event, long currentTagEndPos) {
		if (titleCount > 0 && titleCount % MAX_TITLES == 0) {
			logger.log(Level.FINE, "Processed {0} pages", titleCount);

			try {
				commitTitlesAndXmlDumpEntity(getTitles(), setRestartPosition(currentTagEndPos));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void commitTitlesAndXmlDumpEntity(TitleEntity[] titles, XmlDumpEntity xmlDumpEntity) {
		db.getDao().insertTitlesAndXmlDumpEntity(xmlDumpEntity, titles);
	}

	private TitleEntity[] getTitles() {
		if (titlesIdx < MAX_TITLES) // last commit case, can be smaller
			return Arrays.copyOf(titles, titlesIdx);

		titlesIdx = 0;
		return titles;
	}

	private XmlDumpEntity setRestartPosition(long currentUncompressedPosition) throws IOException {
		long blockUncompressedPosition;
		long blockPositionInBits;
//		synchronized (bzip2Blocks) {
//			Map.Entry<Long, Long> e = bzip2Blocks.floorEntry(currentUncompressedPosition);
//			blockUncompressedPosition = e.getKey();
//			blockPositionInBits = e.getValue();
//
//			// remove all smaller entries from map
//			Long lowerKey;
//			while ((lowerKey = bzip2Blocks.lowerKey(e.getKey())) != null) {
//				bzip2Blocks.remove(lowerKey);
//			}
//		}

		XmlDumpEntity xmlDumpEntity = db.getDao().getXmlDumpEntityByUrl(xmlDumpUrl);
//		xmlDumpEntity.setIndexBlockPositionInBits(blockPositionInBits);
//		xmlDumpEntity.setIndexBlockPositionUncompressed(blockUncompressedPosition);
//		xmlDumpEntity.setIndexPagePositionUncompressed(currentUncompressedPosition);
		return xmlDumpEntity;
	}

	private void addToIndex(Indexer indexer, String pageTitel, long currentTagUncompressedPosition) throws IOException {

		Map.Entry<Long, Long> e = indexer.getBlockStartPosition(currentTagUncompressedPosition);
		long blockUncompressedPosition = e.getKey();
		long blockPositionInBits = e.getValue();

		TitleEntity title = new TitleEntity();
		title.setTitle(pageTitel);
		title.setPageUncompressedPosition(currentTagUncompressedPosition);
		title.setBlockUncompressedPosition(blockUncompressedPosition);
		title.setBlockPositionInBits(blockPositionInBits);
		titles[titlesIdx++] = title;
	}
}
