package de.m3y3r.offlinewiki.pagestore.bzip2.blocks.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockController;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockEntry;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockFinderEventListener;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockEntry.IndexState;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockIterator;
import de.m3y3r.offlinewiki.utility.Database;

public class JdbcBlockController implements BlockController, BlockFinderEventListener {

	private static final String SQL_UPDATE = "update blocks set index_state = ? where block_no = ?";
//	private static final String SQL_FETCH_BLOCK_NO = "select block_no, block_start_bits, index_state from blocks where block_start_bits >= ? order by block_no asc";
	        static final String SQL_FETCH_FROM = "select block_no, block_start_bits, index_state from blocks where block_start_bits >= ? order by block_no asc";
	private static final String SQL_FETCH_LAST = "select block_no, block_start_bits, index_state from blocks where block_no = ( select max(block_no) from blocks)";
	private static final String SQL_INSERT = "insert into blocks values (?, ?, ?)";

	public int totalEntries;
	private static final int MAX_ENTRIES = 50;

	private final List<BlockEntry> entries;

	public JdbcBlockController() {
		entries = new ArrayList<>();
		createTables();
	}

	private void createTables() {
		try(Connection c = Database.getConnection()) {
			String sqlCreateBlocks =
					"create table if not exists  blocks (" +
							"    block_no         long not null, " +
							"    block_start_bits long not null, " +
							"    index_state      integer not null default 0," +
							"  primary key (block_no) )";

			c.createStatement().execute(sqlCreateBlocks);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onNewBlock(EventObject event, long blockNo, long readCountBits, boolean isEndOfStream) {
		BlockEntry entry = new BlockEntry(blockNo, readCountBits, null);
		entries.add(entry);
		if(entries.size() > MAX_ENTRIES) {
			flush();
		}
		totalEntries++;
	}

	private void flush() {
		try(Connection c = Database.getConnection()) {
			try(PreparedStatement ps = c.prepareStatement(SQL_INSERT)) {
				for(BlockEntry e: entries) {
					ps.setLong(1, e.blockNo);
					ps.setLong(2, e.readCountBits);
					ps.setInt(3, e.indexState == null ? IndexState.INITIAL.ordinal() : e.indexState.ordinal());
					ps.addBatch();
				}
				ps.executeBatch();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			entries.clear();
		}
	}

	@Override
	public void onEndOfFile(EventObject event, boolean isNormalEnd) {
		flush();
		System.out.format("did process %d blocks %n", totalEntries);
	}

	@Override
	public BlockEntry getLatestEntry() {
		try(Connection c = Database.getConnection()) {
			try(PreparedStatement ps = c.prepareStatement(SQL_FETCH_LAST)) {
				try(ResultSet rs = ps.executeQuery()) {
					if(rs.next()) {
						return new BlockEntry(rs.getLong(1), rs.getLong(2), IndexState.values()[rs.getInt(3)]);
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public BlockIterator getBlockIterator(long startBlockPositionInBits) {
		return new JdbcBlockIterator(startBlockPositionInBits);
	}

	@Override
	public void setBlockFinished(long blockNo) {
		try(Connection c = Database.getConnection()) {
			try(PreparedStatement ps = c.prepareStatement(SQL_UPDATE)) {
				ps.setInt(1, IndexState.FINISHED.ordinal());
				ps.setLong(2, blockNo);
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void setBlockFailed(long blockNo) {
		try(Connection c = Database.getConnection()) {
			try(PreparedStatement ps = c.prepareStatement(SQL_UPDATE)) {
				ps.setInt(1, IndexState.FAILED.ordinal());
				ps.setLong(2, blockNo);
				ps.executeUpdate();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

//	@Override
//	public long[] getBlockPositions(long startReadCountBits, int noBlocks) {
//		try(Connection c = Database.getConnection()) {
//			try(PreparedStatement ps = c.prepareStatement(SQL_FETCH_BLOCKS)) {
//				ps.setFetchSize(noBlocks);
//				ps.setMaxRows(noBlocks);
//				ps.setLong(1, startReadCountBits);
//				try(ResultSet rs = ps.executeQuery()) {
//					long[] blocks = new long[noBlocks];
//					int i = 0;
//					while(rs.next()) {
//						blocks[i++] = rs.getLong(2);
//					}
//					return Arrays.copyOf(blocks, i);
//				}
//			}
//		} catch (SQLException e) {
//			e.printStackTrace();
//		}
//		return null;
//	}
//
//	@Override
//	public int getTotalBlockNo() {
//		try(Connection c = Database.getConnection()) {
//			try(PreparedStatement ps = c.prepareStatement(SQL_FETCH_BLOCK_NO)) {
//				try(ResultSet rs = ps.executeQuery()) {
//					if(rs.next()) {
//						return rs.getInt(1);
//					}
//				}
//			}
//		} catch (SQLException e) {
//			e.printStackTrace();
//		}
//		return 0;
//	}
}

class JdbcBlockIterator implements BlockIterator {

	private final ResultSet rs;

	public JdbcBlockIterator(long startBlockPositionInBits) {
		try {
			Connection c = Database.getConnection();
			PreparedStatement ps = c.prepareStatement(JdbcBlockController.SQL_FETCH_FROM);
			ps.setLong(1, startBlockPositionInBits);
			this.rs = ps.executeQuery();
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean hasNext() {
		try {
			return !rs.isLast();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public BlockEntry next() {
		try {
			boolean hasNext = rs.next();
			if(!hasNext) {
				rs.close();
			} else {
				return new BlockEntry(rs.getLong(1), rs.getLong(2), IndexState.values()[rs.getInt(3)]);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void close() throws Exception {
		rs.close();
	}
}