package de.m3y3r.offlinewiki.pagestore.bzip2.blocks.file;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.Iterator;
import java.util.List;

import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockController;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockEntry;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockFinderEventListener;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockEntry.IndexState;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockIterator;

public class FileBlockController implements BlockController, BlockFinderEventListener, Flushable, Closeable {

	public static class FileBasedBlockIterator implements BlockIterator {
		private DataInputStream in;
		private BlockEntry next;

		private final File blockFile;

		public FileBasedBlockIterator(File blockFile) {
			this.blockFile = blockFile;
		}

		@Override
		public boolean hasNext() {
			if(in == null) {
				try {
//					System.out.println("bf len=" + blockFile.length());
					FileInputStream fis = new FileInputStream(blockFile);
					in = new DataInputStream(fis);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if(in != null)
				try {
					next = BlockEntry.readObject(in);
					return true;
				} catch (IOException e) {
					in = null;
					e.printStackTrace();
				}
			return false;
		}

		@Override
		public BlockEntry next() {
			return next;
		}

		public void setBlockFinished(long blockNo) {
			setBlockState(blockNo, IndexState.FINISHED);
		}

		private void setBlockState(long blockNo, IndexState state) {
			try(FileInputStream fis = new FileInputStream(blockFile);
				DataInputStream in = new DataInputStream(fis)) {
				long pos = blockNo * BlockEntry.BLOCK_ENTRY_LEN;
				fis.getChannel().position(pos);
				BlockEntry be = BlockEntry.readObject(in);
				try(FileOutputStream fos = new FileOutputStream(blockFile, true);
					DataOutputStream out = new DataOutputStream(fos)) {
					fos.getChannel().position(pos);
					be.indexState = state;
					BlockEntry.writeObject(out, be);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		@Override
		public void close() throws IOException {
			if (in != null) {
				in.close();
				in = null;
			}
		}
	}

	private final List<BlockEntry> entries;
	private final File blockFile;

	public FileBlockController(File blockFile) {
		this.blockFile = blockFile;
		this.entries = new ArrayList<>(32);
	}

	@Override
	public void onNewBlock(EventObject event, long blockNo, long readCountBits, boolean isEndOfStream) {
		BlockEntry entry = new BlockEntry(blockNo, readCountBits, null);
		entries.add(entry);
	}

	@Override
	public void flush() throws IOException {
		//TODO/FIXME: file is truncated in restart of BlockFinder, this should be okay
		try(DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(blockFile, true)))) {
			for(BlockEntry e : entries) {
				e.indexState = IndexState.INITIAL;
				BlockEntry.writeObject(out, e);
			}
		}
		entries.clear();
	}

	@Override
	public void close() throws IOException {
		flush();
	}

	@Override
	public void onEndOfFile(EventObject event, boolean isNormalEnd) {
		try {
			close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public BlockIterator getBlockIterator(long startBlockPosInBits) {
		return new FileBasedBlockIterator(blockFile);
	}

	@Override
	public BlockEntry getLatestEntry() {
		if(blockFile == null || !blockFile.exists() || !blockFile.isFile() || blockFile.length() < BlockEntry.BLOCK_ENTRY_LEN) return null;

		long lastCompleteEntry = blockFile.length() / BlockEntry.BLOCK_ENTRY_LEN * BlockEntry.BLOCK_ENTRY_LEN;

		try {
			FileChannel fc = FileChannel.open(blockFile.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE);
			fc.truncate(lastCompleteEntry);
			try(FileInputStream fis = new FileInputStream(blockFile)) {
				fis.getChannel().position(lastCompleteEntry - BlockEntry.BLOCK_ENTRY_LEN);
				try(DataInputStream in = new DataInputStream(fis)) {
					return BlockEntry.readObject(in);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public void setBlockFinished(long blockNo) {
	}

	@Override
	public void setBlockFailed(long blockNo) {
	}
}
