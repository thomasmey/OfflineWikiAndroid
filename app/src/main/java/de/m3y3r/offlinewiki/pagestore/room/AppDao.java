package de.m3y3r.offlinewiki.pagestore.room;

import androidx.paging.DataSource;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import android.database.Cursor;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import de.m3y3r.offlinewiki.Config;

@androidx.room.Dao
public abstract class AppDao {

	@Query("SELECT * FROM TitleEntity WHERE xmlDumpId = :xmlDumpId AND title >= :title ORDER BY title ASC LIMIT :noMaxHits")
	public abstract List<TitleEntity> getTitleEntityByIndexKeyInitial(int xmlDumpId, int noMaxHits, String title);

	@Query("SELECT * FROM TitleEntity WHERE xmlDumpId = :xmlDumpId AND title > :title ORDER BY title ASC LIMIT :noMaxHits")
	public abstract List<TitleEntity> getTitleEntityByIndexKeyAfter(int xmlDumpId, int noMaxHits, String title);

	@Query("SELECT * FROM TitleEntity WHERE xmlDumpId = :xmlDumpId AND title < :title ORDER BY title DESC LIMIT :noMaxHits")
	public abstract List<TitleEntity> getTitleEntityByIndexKeyBefore(int xmlDumpId, int noMaxHits, String title);

	@Query("SELECT * FROM TitleEntity WHERE xmlDumpId = :xmlDumpId AND title LIKE :title ORDER BY title ASC LIMIT :noMaxHits")
	public abstract List<TitleEntity> getTitleEntityByIndexKeyLikeInitial(int xmlDumpId, int noMaxHits, String title);

	@Query("SELECT * FROM TitleEntity WHERE xmlDumpId = :xmlDumpId AND title LIKE :title AND title < :restartTitle ORDER BY title DESC LIMIT :noMaxHits")
	public abstract List<TitleEntity> getTitleEntityByIndexKeyLikeBefore(int xmlDumpId, int noMaxHits, String title, String restartTitle);

	@Query("SELECT * FROM TitleEntity WHERE xmlDumpId = :xmlDumpId AND title LIKE :title AND title > :restartTitle ORDER BY title ASC LIMIT :noMaxHits")
	public abstract List<TitleEntity> getTitleEntityByIndexKeyLikeAfter(int xmlDumpId, int noMaxHits, String title, String restartTitle);

	@Query("SELECT * FROM TitleEntity WHERE xmlDumpId = :xmlDumpId AND title = :title")
	public abstract TitleEntity getTitle(int xmlDumpId, String title);

	@Insert
	public abstract void insertAllTitleEntity(TitleEntity... titleEntity);

	@Insert
	public abstract void insertAllBlockEntity(BlockEntity... blockEntity);

	@Query("SELECT * FROM BlockEntity WHERE xmlDumpId = :xmlDumpId AND blockNo = :blockNo")
	public abstract BlockEntity getBlock(int xmlDumpId, long blockNo);

	@Query("SELECT * FROM BlockEntity WHERE xmlDumpId = :xmlDumpId AND blockNo = (select max(blockNo) from BlockEntity)")
	public abstract BlockEntity getLatestBlock(int xmlDumpId);

	@Query("SELECT blockNo, readCountBits, indexState FROM BlockEntity WHERE xmlDumpId = :xmlDumpId AND readCountBits >= :readCountBits ORDER BY blockNo ASC")
	public abstract Cursor getAllBlocksFrom(int xmlDumpId, long readCountBits);

	@Query("SELECT count(*) FROM BlockEntity WHERE xmlDumpId = :xmlDumpId")
	public abstract int getTotalBlockCount(int xmlDumpId);

	@Query("SELECT count(*) FROM BlockEntity WHERE xmlDumpId = :xmlDumpId AND indexState != 0")
	public abstract int getProcessedBlockCount(int xmlDumpId);

	@Update
	public abstract void updateBlock(BlockEntity block);

	@Query("SELECT * FROM XmlDumpEntity WHERE id = :xmlDumpId")
	public abstract XmlDumpEntity getXmlDumpEntityById(int xmlDumpId);

	@Query("SELECT * FROM XmlDumpEntity WHERE url = :xmlDumpUrl")
	public abstract XmlDumpEntity getXmlDumpEntityByUrl(String xmlDumpUrl);

	@Insert
	public abstract void insertXmlDumpEntity(XmlDumpEntity xde);

	@Update
	public abstract void updateXmlDumpEntity(XmlDumpEntity xmlDumpEntity);

	@Transaction
	public void insertTitlesAndXmlDumpEntity(XmlDumpEntity xmlDumpEntity, TitleEntity... titleEntity) {
		Log.d(Config.getInstance().getLoggerName(), "About to insert: " + Arrays.toString(titleEntity));
		for(TitleEntity t: titleEntity) {
			t.setXmlDumpId(xmlDumpEntity.getId());
		}
		insertAllTitleEntity(titleEntity);
		updateXmlDumpEntity(xmlDumpEntity);
	}
}
