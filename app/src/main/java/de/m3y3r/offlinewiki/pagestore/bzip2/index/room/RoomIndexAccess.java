package de.m3y3r.offlinewiki.pagestore.bzip2.index.room;


import java.util.List;
import java.util.stream.Collectors;

import de.m3y3r.offlinewiki.pagestore.bzip2.index.IndexAccess;
import de.m3y3r.offlinewiki.pagestore.room.AppDatabase;
import de.m3y3r.offlinewiki.pagestore.room.TitleEntity;

public class RoomIndexAccess implements IndexAccess {

	private final AppDatabase db;
	private final int xmlDumpId;

	public RoomIndexAccess(AppDatabase db, int xmlDumpId) {
		this.db = db;
		this.xmlDumpId = xmlDumpId;
	}

	@Override
	public List<String> getKeyAscending(int noMaxHits, String indexKey) {
		throw new UnsupportedOperationException();
//		return db.getDao().getTitleEntityByIndexKeyAscending(xmlDumpId, noMaxHits, indexKey)
//			.stream().map(TitleEntity::getTitle).collect(Collectors.toList());
	}

	@Override
	public List<String> getKeyAscendingLike(int maxReturnCount, String likeKey) {
		throw new UnsupportedOperationException();
//		return db.getDao().getTitleEntityByIndexKeyAscendingLike(xmlDumpId, maxReturnCount, likeKey)
//			.stream().map(TitleEntity::getTitle).collect(Collectors.toList());
	}

	@Override
	public long[] getKey(String title) {
		TitleEntity titleEntity = db.getDao().getTitle(xmlDumpId, title);
		return new long[] {titleEntity.getBlockPositionInBits(), titleEntity.getPageUncompressedPosition()};
	}
}
