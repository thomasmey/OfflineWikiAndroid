package de.m3y3r.offlinewiki.pagestore.bzip2.index.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import de.m3y3r.offlinewiki.pagestore.bzip2.index.Indexer;
import de.m3y3r.offlinewiki.pagestore.bzip2.index.IndexerEvent;
import de.m3y3r.offlinewiki.pagestore.bzip2.index.IndexerEventListener;
import de.m3y3r.offlinewiki.utility.Database;

public class JdbcIndexerEventHandler implements IndexerEventListener {

	private final List<Object[]> entries;
	private static final String SQL_INSERT = "insert into titles values (?, ?, ?)";

	public JdbcIndexerEventHandler() {
		entries = new ArrayList<>();
		createTables();
	}

	private void createTables() {
		try(Connection c = Database.getConnection()) {
			String sqlCreateTitles =
					"create table if not exists   titles (" +
							"    block_start_bits long not null, " +
							"    title_start_pos  long not null, " + // in bytes from block start!?
							"    title            varchar(4096)," +
							"  primary key (title) )";
			c.createStatement().execute(sqlCreateTitles);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onBeforeStartOfStream(IndexerEvent event) {
	}

	@Override
	public void onPageStart(IndexerEvent event) {
	}

	@Override
	public void onPageTagEnd(IndexerEvent event, long currentTagEndPos) {
	}

	@Override
	public void onNewTitle(IndexerEvent event, String title, long pageTagStartPos) {
		Indexer indexer = (Indexer) event.getSource();
		long blockStartPosInBits = indexer.getBlockStartPosition();

		Object[] record = new Object[] {blockStartPosInBits, pageTagStartPos, title};
		entries.add(record);
	}

	private void flush() {
		try(Connection c = Database.getConnection()) {
			try(PreparedStatement ps = c.prepareStatement(SQL_INSERT)) {
				for(Object[] e: entries) {
					ps.setLong(1, (long) e[0]);
					ps.setLong(2, (long) e[1]);
					ps.setString(3, (String) e[2]);
					ps.addBatch();
				}
				int[] result = ps.executeBatch();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			entries.clear();
		}
	}

	@Override
	public void onEndOfStream(IndexerEvent event, boolean filePos) {
		flush();
	}
}
