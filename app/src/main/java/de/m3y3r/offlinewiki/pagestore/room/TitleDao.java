package de.m3y3r.offlinewiki.pagestore.room;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Transaction;
import android.arch.persistence.room.Update;
import android.database.sqlite.SQLiteConstraintException;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import de.m3y3r.offlinewiki.Config;

@Dao
public abstract class TitleDao {

	@Query("SELECT * FROM TitleEntity WHERE title >= :title LIMIT :noMaxHits")
	public abstract List<TitleEntity> getTitleEntityByIndexKeyAscending(int noMaxHits, String title);

	@Query("SELECT * FROM TitleEntity WHERE title LIKE :title LIMIT :noMaxHits")
	public abstract List<TitleEntity> getTitleEntityByIndexKeyAscendingLike(int noMaxHits, String title);

	@Query("SELECT * FROM TitleEntity WHERE title = :indexKey")
	public abstract TitleEntity getTitleEntityByIndexKey(String indexKey);

	@Insert
	public abstract void insertAllTitleEntity(TitleEntity... titleEntity);

	@Query("SELECT * FROM XmlDumpEntity WHERE url = :xmlDumpUrl")
	public abstract XmlDumpEntity getXmlDumpEntityByUrl(String xmlDumpUrl);

	@Insert
	public abstract void insertXmlDumpEntity(XmlDumpEntity xde);

	@Update
	public abstract void updateXmlDumpEntity(XmlDumpEntity xmlDumpEntity);

	@Transaction
	public void insertTitlesAndXmlDumpEntity(XmlDumpEntity xmlDumpEntity, TitleEntity... titleEntity) {
		Log.d(Config.LOGGER_NAME, "About to insert: " + Arrays.toString(titleEntity));
		insertAllTitleEntity(titleEntity);
		updateXmlDumpEntity(xmlDumpEntity);
	}
}
