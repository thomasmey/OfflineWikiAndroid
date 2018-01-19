package de.m3y3r.offlinewiki.utility;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class SplitFileInputStream extends InputStream {

	private final SplitFile splitFile;
	private final long splitSize;
	private final long maxSplitCount;
	private int splitCount;

	private FileInputStream in;

	public SplitFileInputStream(SplitFile inputFile, long splitSize) {
		this.splitFile = inputFile;
		this.splitSize = splitSize;
		this.maxSplitCount = inputFile.getSplitCount();
	}

	private FileInputStream open(int splitNo) throws FileNotFoundException {
			File file = new File(splitFile.getParentFile(), splitFile.getBaseName() + '.' + splitNo);
			if(!file.exists() || file.isDirectory())
				return null;
			if(splitCount < maxSplitCount && file.length() != splitSize)
				return null;

			splitCount = splitNo;

			in = new FileInputStream(file);
			return in;
	}

	@Override
	public int read() throws IOException {
		if(in == null) {
			FileInputStream in = open(splitCount);
			if(in == null) return -1;
		}

		int b = in.read();
		if(b == -1) {
			close();
			splitCount++;
			return read();
		}

		return b;
	}

	@Override
	public void close() throws IOException {
		if(in != null) {
			in.close();
			in = null;
		}
	}

	public void seek(long pos) throws IOException {
		if(pos < 0) throw new IllegalArgumentException();

		close();
		int splitNo = (int) (pos / splitSize);
		FileInputStream in = open(splitNo);
		pos = pos % splitSize;
		in.getChannel().position(pos);
	}
}
