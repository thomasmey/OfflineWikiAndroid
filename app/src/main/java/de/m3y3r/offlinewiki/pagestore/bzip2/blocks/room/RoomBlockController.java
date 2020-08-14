package de.m3y3r.offlinewiki.pagestore.bzip2.blocks.room;


import android.database.Cursor;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockController;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockEntry;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockFinderEventListener;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockIterator;
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
	public RoomBlockIterator getBlockIterator(long startBlockPositionInBits) {
		return new RoomBlockIterator(db, xmlDumpId, startBlockPositionInBits);
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
	public void onNewBlock(EventObject event, long blockNo, long readCountBits, boolean isEndOfStream) {
		BlockEntity entry = new BlockEntity();
		entry.setBlockNo(blockNo);
		entry.setXmlDumpId(xmlDumpId);
		entry.setReadCountBits(readCountBits);
		int indexState;
		if(isEndOfStream) {
			indexState = BlockEntry.IndexState.FINISHED.ordinal();
		} else {
			indexState = BlockEntry.IndexState.INITIAL.ordinal();
		}
		entry.setIndexState(indexState);
		entries.add(entry);
		if(entries.size() > MAX_ENTRIES) {
			flush();
		}
		totalEntries++;
	}

	private void flush() {
		try {
			db.getDao().insertAllBlockEntity(entries.toArray(new BlockEntity[0]));
		} catch (android.database.sqlite.SQLiteConstraintException e) {
			System.err.println("we have a duplicate= " + entries);
			e.printStackTrace();
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

class RoomBlockIterator implements BlockIterator {

	private Cursor cursor;

	public RoomBlockIterator(AppDatabase db, int xmpDumpId, long startBlockPositionInBits) {
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

	@Override
	public void close() {
		cursor.close();
	}
}