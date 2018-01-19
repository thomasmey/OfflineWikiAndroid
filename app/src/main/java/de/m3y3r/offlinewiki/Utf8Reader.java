/*
 * Copyright 2012 Thomas Meyer
 */

package de.m3y3r.offlinewiki;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

public class Utf8Reader extends Reader {

	private final InputStream in;

	private long currentFilePos;

	public Utf8Reader(InputStream in) throws IOException {
		this.in = in;
	}

	public long getCurrentFilePos() {
		return currentFilePos;
	}
	public void setCurrentFilePos(long currentFilePos) { this.currentFilePos = currentFilePos; }

	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void close() throws IOException {
		in.close();
	}

	// this implements an easy method to get the next UTF-8 char...
	// JAVA Y U N PROVIDE THIS?
	public int read() throws IOException {

		int rc;
		int b = readInternal();
		if(b < 0)
			return b;

		if((b >> 7 ) == 0) {
			// single byte UTF-8
			rc = (b & 0x7f);
		} else {
			// multi byte
			int mask = 0;
			int count = 0;

			if((b >> 5) == 0x06) {
				mask = 0x1f;
				count = 1;
			} else if((b >> 4) == 0xe) {
				mask = 0x0f;
				count = 2;
			} else if((b >> 3) == 0x1e) {
				mask = 0x07;
				count = 3;
			}

			rc = b & mask;
			for(int i = 0; i < count; i++) {
				rc = rc << 6;
				b = readInternal();
				if(b < 0)
					throw new EOFException();
				if((b >> 6) == 2) {
					rc = (rc | (b & 0x3f));
				} else
					throw new IllegalArgumentException("Invalid UTF-8 sequence!");
			}

		}
		return rc;
	}

	private int readInternal() throws IOException {

		int b = in.read();
		currentFilePos++;
		return b;
	}
}