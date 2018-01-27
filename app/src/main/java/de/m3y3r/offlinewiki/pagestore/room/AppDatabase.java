package de.m3y3r.offlinewiki.pagestore.room;

import android.arch.persistence.room.RoomDatabase;

@android.arch.persistence.room.Database(entities = {TitleEntity.class, XmlDumpEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
	public abstract AppDao getDao();
}
