package de.m3y3r.offlinewiki.pagestore.room;

import androidx.room.RoomDatabase;

@androidx.room.Database(entities = {TitleEntity.class, XmlDumpEntity.class, BlockEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
	public abstract AppDao getDao();
}
