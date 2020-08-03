package de.m3y3r.offlinewiki.pagestore.bzip2.blocks;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

public class BlockEntry implements Serializable {

	public enum IndexState {INITIAL, FAILED, FINISHED}

	public final long blockNo;
	public final long readCountBits;
	public IndexState indexState;

	public BlockEntry(long blockNo, long readCountBits, IndexState indexState) {
		this.blockNo = blockNo;
		this.readCountBits = readCountBits;
		this.indexState = indexState;
	}

	private static final long serialVersionUID = 1L;

	public static final int BLOCK_ENTRY_LEN = 20; // 8 + 8 + 4

	public static void writeObject(DataOutputStream out, BlockEntry be) 
			throws IOException {
		out.writeLong(be.blockNo);
		out.writeLong(be.readCountBits);
		out.writeInt(be.indexState.ordinal());
	}

	public static BlockEntry readObject(DataInputStream in) throws IOException { 
		long blockNo = in.readLong();
		long readCountBits = in.readLong();
		int indexState = in.readInt();

		BlockEntry be = new BlockEntry(blockNo, readCountBits, IndexState.values()[indexState]);
		be.indexState = IndexState.values()[indexState];
		return be;
	}
}
