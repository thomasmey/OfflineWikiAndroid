package de.m3y3r.offlinewiki.pagestore.bzip2.blocks;

import java.util.EventObject;

public interface BlockFinderEventListener {
	void onNewBlock(EventObject event, long blockNo, long readCountBits, boolean isEndOfStream);
	void onEndOfFile(EventObject event, boolean isNormalEnd);
}
