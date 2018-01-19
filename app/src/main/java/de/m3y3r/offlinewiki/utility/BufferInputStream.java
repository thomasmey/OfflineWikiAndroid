package de.m3y3r.offlinewiki.utility;


import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class BufferInputStream extends InputStream {

	private final static int BUFFER_SIZE = 8192;
	private final ByteBuffer byteBuffer;
	private final InputStream in;
	private boolean byteBufferEOF;

	public BufferInputStream(InputStream inputStream) {
		this.byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
		this.in = inputStream;
		fillBuffer();
	}

	@Override
	public int read() throws IOException {
		return readBuffered();
	}

	private void fillBuffer() {

		byteBuffer.rewind();
		byte[] ba = byteBuffer.array();

		int len = 0;
		try {
			len = in.read(ba);
		} catch (IOException e) {
			e.printStackTrace();
		}

		if(len < 0) {
			byteBufferEOF = true;
		} else {
			byteBuffer.limit(len);
		}
	}

	// we need to do the buffering ourself
	private int readBuffered() throws IOException {

		int rc = -1;
		while(true) {
			if(byteBuffer.hasRemaining()) {
				rc = byteBuffer.get() & 0xff;
				break;
			} else {
				if(!byteBufferEOF)
					fillBuffer();
				else
					return rc;
			}
		}
		return rc;
	}

	public void clearBuffer() {
		byteBuffer.position(byteBuffer.limit());
	}
}
