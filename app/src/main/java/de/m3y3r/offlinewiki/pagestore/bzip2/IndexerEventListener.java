package de.m3y3r.offlinewiki.pagestore.bzip2;

public interface IndexerEventListener {

	public void onPageStart(IndexerEvent event);

	public void onNewBlock(IndexerEvent event, long blockPositionInBits);

	public void onEndOfStream(IndexerEvent event);

	public void onNewTitle(IndexerEvent event, String title, long pageTagStartPos);

	public void onPageTagEnd(IndexerEvent event, long currentTagEndPos);
}
