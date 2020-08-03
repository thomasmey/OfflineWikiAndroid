package de.m3y3r.offlinewiki.pagestore.bzip2.blocks.room;


import android.database.Cursor;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.Iterator;
import java.util.List;

import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockController;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockEntry;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockFinderEventListener;
import de.m3y3r.offlinewiki.pagestore.room.AppDatabase;
import de.m3y3r.offlinewiki.pagestore.room.BlockEntity;

public class RoomBlockController implements BlockController, BlockFinderEventListener {

	private final AppDatabase db;
	private final int xmlDumpId;

	private int totalEntries;

	private static final int MAX_ENTRIES = 50;
	private final List<BlockEntity> entries = new ArrayList<>();
	private boolean isNormalEnd;

	public boolean isNormalEnd() {
		return isNormalEnd;
	}

	public RoomBlockController(AppDatabase db, int xmlDumpId) {
		this.db = db;
		this.xmlDumpId = xmlDumpId;
	}

	@Override
	public Iterator<BlockEntry> getBlockIterator(long startBlockPositionInBits) {
		return new BlockIterator(db, xmlDumpId, startBlockPositionInBits);
	}

	@Override
	public BlockEntry getLatestEntry() {
		BlockEntity latestBlock = db.getDao().getLatestBlock(xmlDumpId);
		if(latestBlock == null) return null;
		return new BlockEntry(latestBlock.getBlockNo(), latestBlock.getReadCountBits(), BlockEntry.IndexState.values()[latestBlock.getIndexState()]);
	}

	@Override
	public void setBlockFinished(long blockNo) {
		BlockEntity block = db.getDao().getBlock(xmlDumpId, blockNo);
		block.setIndexState(BlockEntry.IndexState.FINISHED.ordinal());
		db.getDao().updateBlock(block);
	}

	@Override
	public void setBlockFailed(long blockNo) {
		BlockEntity block = db.getDao().getBlock(xmlDumpId, blockNo);
		block.setIndexState(BlockEntry.IndexState.FAILED.ordinal());
		db.getDao().updateBlock(block);
	}

	@Override
	public void onNewBlock(EventObject event, long blockNo, long readCountBits) {
		BlockEntity entry = new BlockEntity();
		entry.setBlockNo(blockNo);
		entry.setXmlDumpId(xmlDumpId);
		entry.setReadCountBits(readCountBits);
		entry.setIndexState(BlockEntry.IndexState.INITIAL.ordinal());
		entries.add(entry);
		if(entries.size() > MAX_ENTRIES) {
			flush();
		}
		totalEntries++;
	}

	private void flush() {
		try {
			db.getDao().insertAllBlockEntity(entries.toArray(new BlockEntity[0]));
		} finally {
			entries.clear();
		}
	}

	@Override
	public void onEndOfFile(EventObject event, boolean isNormalEnd) {
		flush();
		System.out.format("did process %d blocks %n", totalEntries);
		this.isNormalEnd = isNormalEnd;
	}
}

class BlockIterator implements Iterator<BlockEntry> {

	private Cursor cursor;

	public BlockIterator(AppDatabase db, int xmpDumpId, long startBlockPositionInBits) {
		this.cursor = db.getDao().getAllBlocksFrom(xmpDumpId, startBlockPositionInBits);
	}

	@Override
	public boolean hasNext() {
		boolean hasNext = cursor.moveToNext();
		if(!hasNext) {
			cursor.close();
		} else {
			cursor.moveToPrevious();
		}
		return hasNext;
	}

	@Override
	public BlockEntry next() {
		if(cursor.isClosed())
			return null;
		boolean hasNext = cursor.moveToNext();
		if(!hasNext) {
			cursor.close();
			return null;
		} else {
			return new BlockEntry(cursor.getLong(0), cursor.getLong(1), BlockEntry.IndexState.values()[cursor.getInt(2)]);
		}
	}
}