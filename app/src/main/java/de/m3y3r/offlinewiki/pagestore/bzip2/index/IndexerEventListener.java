package de.m3y3r.offlinewiki.pagestore.bzip2.index;

public interface IndexerEventListener {

	public void onBeforeStartOfStream(IndexerEvent event);
	public void onPageStart(IndexerEvent event);
	public void onPageTagEnd(IndexerEvent event, long currentTagEndPos);

	public void onNewTitle(IndexerEvent event, String title, long pageTagStartPos);

	public void onEndOfStream(IndexerEvent event, boolean normalEnd);
}
