package de.m3y3r.offlinewiki.pagestore.room;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

@Database(entities = {TitleEntity.class, XmlDumpEntity.class}, version = 1, exportSchema = false)
public abstract class TitleDatabase extends RoomDatabase {
	public abstract TitleDao getDao();
}
