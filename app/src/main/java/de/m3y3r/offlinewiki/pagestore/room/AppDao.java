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

@android.arch.persistence.room.Dao
public abstract class AppDao {

	@Query("SELECT * FROM TitleEntity WHERE xmlDumpId = :xmlDumpId AND title >= :title ORDER BY title ASC LIMIT :noMaxHits")
	public abstract List<TitleEntity> getTitleEntityByIndexKeyAscending(int xmlDumpId, int noMaxHits, String title);

	@Query("SELECT * FROM TitleEntity WHERE xmlDumpId = :xmlDumpId AND title LIKE :title ORDER BY title ASC LIMIT :noMaxHits")
	public abstract List<TitleEntity> getTitleEntityByIndexKeyAscendingLike(int xmlDumpId, int noMaxHits, String title);

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
		for(TitleEntity t: titleEntity) {
			t.setXmlDumpId(xmlDumpEntity.getId());
		}
		insertAllTitleEntity(titleEntity);
		updateXmlDumpEntity(xmlDumpEntity);
	}
}
