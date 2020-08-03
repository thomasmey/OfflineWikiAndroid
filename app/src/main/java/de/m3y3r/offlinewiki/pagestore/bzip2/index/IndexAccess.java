package de.m3y3r.offlinewiki.pagestore.bzip2.index;

import java.util.List;

public interface IndexAccess {

	List<String> getKeyAscending(int noMaxHits, String indexKey);
	List<String> getKeyAscendingLike(int maxReturnCount, String likeKey);
	long[] getKey(String title);

}
