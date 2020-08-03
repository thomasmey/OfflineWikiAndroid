package de.m3y3r.offlinewiki.utility;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class SplitFileOutputStream extends OutputStream {
	private final long splitSize;
	private final SplitFile targetFile;

	private FileOutputStream out;

	private int fileCount;
	private int fileSize;

	public SplitFileOutputStream(SplitFile targetFile, long maxFileSize) {
		this.splitSize = maxFileSize;
		this.targetFile = targetFile;
	}

	@Override
	public void write(int i) throws IOException {
		if(out == null) {
			open(fileCount, false);
		}
		out.write(i);

		fileSize++;
		if(fileSize == splitSize) {
			close();

			fileSize = 0;
			fileCount++;
		}
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if(out == null) {
			open(fileCount, false);
		}

		fileSize += len;
		if(fileSize >= splitSize) {
			int l = (int) (len - (fileSize - splitSize));
			out.write(b, off, l);
			close();

			fileSize = 0;
			fileCount++;
			off += l;
			len -= l;
			write(b, off, len);
		} else {
			out.write(b, off, len);
		}
	}

	private FileOutputStream open(int splitNo, boolean append) throws FileNotFoundException {
		File nextFile = new File(targetFile.getParentFile(), targetFile.getBaseName() + '.' + splitNo);
		out = new FileOutputStream(nextFile, append);
		this.fileCount = splitNo;
		return out;
	}

	@Override
	public void close() throws IOException {
		if(out != null) {
			out.getFD().sync();
			out.close();

			out = null;
		}
	}

	public void seek(long pos) throws IOException {
		if(pos < 0) throw new IllegalArgumentException();

		close();
		int splitNo = (int) (pos / splitSize);
		FileOutputStream out = open(splitNo, true);
		pos = pos % splitSize;
		out.getChannel().position(pos);
		fileSize = (int) pos; //FIXME: Is this correct, or -1?
	}

	@Override
	public void flush() throws IOException {
		out.flush();
		out.getFD().sync();
	}
}
