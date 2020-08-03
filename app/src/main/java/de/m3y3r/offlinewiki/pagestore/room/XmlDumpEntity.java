package de.m3y3r.offlinewiki.pagestore.room;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity( indices = @Index(value = {"url"}, unique = true))
public class XmlDumpEntity {

	@PrimaryKey(autoGenerate = true)
	private int id;
	@NonNull
	private String url;
	@NonNull
	private String etag;
	@NonNull
	private Long length;
	private String baseName;
	private String directory;

	private boolean downloadFinished;

	private boolean indexFinished;
	private Long blockPositionInBits;
	private Long blockPositionUncompressed;
	private Long pagePositionUncompressed;

	private boolean blockFinderFinished;

	public boolean isBlockFinderFinished() {
		return blockFinderFinished;
	}

	public void setBlockFinderFinished(boolean blockFinderFinished) {
		this.blockFinderFinished = blockFinderFinished;
	}

	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}

	@NonNull
	public String getUrl() {
		return url;
	}

	public void setUrl(@NonNull String url) {
		this.url = url;
	}

	public boolean isIndexFinished() {
		return indexFinished;
	}

	public void setIndexFinished(@NonNull boolean indexFinished) {
		this.indexFinished = indexFinished;
	}

	public boolean isDownloadFinished() {
		return downloadFinished;
	}

	public void setDownloadFinished(boolean downloadFinished) {
		this.downloadFinished = downloadFinished;
	}

	@NonNull
	public String getEtag() {
		return etag;
	}

	public void setEtag(@NonNull String etag) {
		this.etag = etag;
	}

	public void setIndexBlockPositionInBits(long indexBlockPositionInBits) {
		this.blockPositionInBits = indexBlockPositionInBits;
	}

	public Long getBlockPositionInBits() {
		return blockPositionInBits;
	}

	public void setBlockPositionInBits(Long blockPositionInBits) {
		this.blockPositionInBits = blockPositionInBits;
	}

	public void setIndexBlockPositionUncompressed(Long indexBlockPositionUncompressed) {
		this.blockPositionUncompressed = indexBlockPositionUncompressed;
	}

	public Long getBlockPositionUncompressed() {
		return blockPositionUncompressed;
	}

	public void setBlockPositionUncompressed(Long blockPositionUncompressed) {
		this.blockPositionUncompressed = blockPositionUncompressed;
	}

	public void setIndexPagePositionUncompressed(Long indexPagePositionUncompressed) {
		this.pagePositionUncompressed = indexPagePositionUncompressed;
	}

	public Long getPagePositionUncompressed() {
		return pagePositionUncompressed;
	}

	public void setPagePositionUncompressed(Long pagePositionUncompressed) {
		this.pagePositionUncompressed = pagePositionUncompressed;
	}

	public String getBaseName() {
		return baseName;
	}

	public void setBaseName(String baseName) {
		this.baseName = baseName;
	}

	public void setDirectory(String directory) {
		this.directory = directory;
	}

	public String getDirectory() {
		return directory;
	}

	@NonNull
	public Long getLength() {
		return length;
	}
	public void setLength(@NonNull Long length) {
		this.length = length;
	}
}
