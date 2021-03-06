package de.m3y3r.offlinewiki.pagestore.room;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.annotation.NonNull;

import java.io.Serializable;

@Entity(
	primaryKeys = {"xmlDumpId", "title"},
	foreignKeys = { @ForeignKey(entity = XmlDumpEntity.class, parentColumns = "id", childColumns = "xmlDumpId") }
)
public class TitleEntity implements Serializable {

	private int xmlDumpId;
	@NonNull
	private String title;

	private long pageUncompressedPosition;
	private long blockUncompressedPosition;
	private long blockPositionInBits;

	public int getXmlDumpId() {
		return xmlDumpId;
	}
	public void setXmlDumpId(int xmlDumpId) {
		this.xmlDumpId = xmlDumpId;
	}

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
