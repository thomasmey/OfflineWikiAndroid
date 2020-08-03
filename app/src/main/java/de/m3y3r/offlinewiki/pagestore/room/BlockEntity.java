package de.m3y3r.offlinewiki.pagestore.room;

import java.io.Serializable;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;

@Entity(
	primaryKeys = {"xmlDumpId", "blockNo"},
	foreignKeys = { @ForeignKey(entity = XmlDumpEntity.class, parentColumns = "id", childColumns = "xmlDumpId") }
)
public class BlockEntity implements Serializable {

	private int xmlDumpId;
	private long blockNo;
	private long readCountBits;
	private int indexState;

	public int getXmlDumpId() {
		return xmlDumpId;
	}

	public void setXmlDumpId(int xmlDumpId) {
		this.xmlDumpId = xmlDumpId;
	}

	public long getBlockNo() {
		return blockNo;
	}

	public void setBlockNo(long blockNo) {
		this.blockNo = blockNo;
	}

	public long getReadCountBits() {
		return readCountBits;
	}

	public void setReadCountBits(long readCountBits) {
		this.readCountBits = readCountBits;
	}

	public int getIndexState() {
		return indexState;
	}

	public void setIndexState(int indexState) {
		this.indexState = indexState;
	}
}
