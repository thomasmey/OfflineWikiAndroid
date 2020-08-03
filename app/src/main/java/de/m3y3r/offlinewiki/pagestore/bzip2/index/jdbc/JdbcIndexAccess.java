package de.m3y3r.offlinewiki.pagestore.bzip2.index.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import de.m3y3r.offlinewiki.pagestore.bzip2.index.IndexAccess;
import de.m3y3r.offlinewiki.utility.Database;

public class JdbcIndexAccess implements IndexAccess {

	private static final String SQL_SELECT_SINGLE = "select title from titles where title >= ? order by title asc";
	private static final String SQL_SELECT_LIKE   = "select title from titles where title like ? order by title asc";
	private static final String SQL_SELECT_TITLE  = "select block_start_bits, title_start_pos from titles where title = ?";

	@Override
	public List<String> getKeyAscending(int noMaxHits, String key) {
		return getTitles(SQL_SELECT_SINGLE, noMaxHits, key);
	}

	@Override
	public List<String> getKeyAscendingLike(int noMaxHits, String key) {
		return getTitles(SQL_SELECT_LIKE, noMaxHits, key);
	}

	private List<String> getTitles(String sql, int noMaxHits, String key) {
		List<String> results = new ArrayList<>();
		try(Connection c = Database.getConnection()) {
			try(PreparedStatement ps = c.prepareStatement(sql)) {
				ps.setString(1, key);
				ps.setMaxRows(noMaxHits);
				try(ResultSet rs = ps.executeQuery()) {
					while(rs.next()) {
						results.add(rs.getString(1));
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return results;
	}

	@Override
	public long[] getKey(String key) {
		try(Connection c = Database.getConnection()) {
			try(PreparedStatement ps = c.prepareStatement(SQL_SELECT_TITLE)) {
				ps.setString(1, key);
				try(ResultSet rs = ps.executeQuery()) {
					if(rs.next()) {
						return new long[] { rs.getLong(1), rs.getLong(2) };
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}
}
