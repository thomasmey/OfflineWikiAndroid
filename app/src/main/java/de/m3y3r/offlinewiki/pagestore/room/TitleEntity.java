package de.m3y3r.offlinewiki.pagestore.room;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.NonNull;

import java.io.Serializable;

@Entity
public class TitleEntity implements Serializable {

	@PrimaryKey
	@NonNull
	private String title;
	@NonNull
	private long pageUncompressedPosition;
	@NonNull
	private long blockUncompressedPosition;
	@NonNull
	private long blockPositionInBits;

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public long getPageUncompressedPosition() {
		return pageUncompressedPosition;
	}

	public void setPageUncompressedPosition(long pageUncompressedPosition) {
		this.pageUncompressedPosition = pageUncompressedPosition;
	}

	public long getBlockUncompressedPosition() {
		return blockUncompressedPosition;
	}

	public void setBlockUncompressedPosition(long blockUncompressedPosition) {
		this.blockUncompressedPosition = blockUncompressedPosition;
	}

	public long getBlockPositionInBits() {
		return blockPositionInBits;
	}

	public void setBlockPositionInBits(long blockPositionInBits) {
		this.blockPositionInBits = blockPositionInBits;
	}

	@Override
	public String toString() {
		return title;
	}
}
