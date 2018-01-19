package de.m3y3r.offlinewiki.pagestore.room;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface TitleDao {

	@Query("SELECT * FROM TitleEntity WHERE title >= :title LIMIT :noMaxHits")
	List<TitleEntity> getTitleEntityByIndexKeyAscending(int noMaxHits, String title);

	@Query("SELECT * FROM TitleEntity WHERE title LIKE :title LIMIT :noMaxHits")
	List<TitleEntity> getTitleEntityByIndexKeyAscendingLike(int noMaxHits, String title);

	@Query("SELECT * FROM TitleEntity WHERE title = :indexKey")
	TitleEntity getTitleEntityByIndexKey(String indexKey);

	@Insert
	void insertAllTitleEntity(TitleEntity... titleEntity);

	@Query("SELECT * FROM XmlDumpEntity WHERE url = :xmlDumpUrl")
	XmlDumpEntity getXmlDumpEntityByUrl(String xmlDumpUrl);

	@Insert
	void insertXmlDumpEntity(XmlDumpEntity xde);

	@Update
	void updateXmlDumpEntity(XmlDumpEntity xmlDumpEntity);
}
