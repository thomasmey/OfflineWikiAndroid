package de.m3y3r.offlinewiki.utility;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.commons.compress.utils.BitInputStream;

import de.m3y3r.offlinewiki.Config;

public class Bzip2BlockInputStream extends InputStream {

	private final ByteBuffer byteBuffer;

	private byte[] piSqrt = new byte[] { 0x17, 0x72, 0x45, 0x38, 0x50, (byte) 0x90};

	public Bzip2BlockInputStream(SplitFile bzip2File, long[] blockPosBits) throws IOException {
		this.byteBuffer = buildBlocks(bzip2File, blockPosBits);
	}

	static private enum State { COPY_HEADER, SEEK_BLOCK, FIN, COPY_BLOCKS, APPEND_END_OF_STREAM_BLOCK};

	private ByteBuffer buildBlocks(final SplitFile bzip2File, final long[] blockPosBits) throws IOException {
		int allocSize = (int) ((blockPosBits[blockPosBits.length - 1] - blockPosBits[0]) / 8) + 4096;
		ByteBuffer bb = ByteBuffer.allocate(allocSize);

		State state = State.COPY_HEADER;
		long readCountBits = 0;
		int crcStream = 0;
		long fromBits = blockPosBits[0];

		BitByteBuffer bbb = new BitByteBuffer(bb);

		try(SplitFileInputStream in = new SplitFileInputStream(bzip2File, Config.getInstance().getSplitSize())) {
			while(state != State.FIN) {
				switch(state) {
				case COPY_HEADER:
				{
					while(readCountBits < (8*4)) {
						int b = in.read();
						if(b >= 0) {
							bb.put((byte) b);
						} else {
							return null;
						}
						readCountBits += 8;
					}
					state = State.SEEK_BLOCK;
				}
				break;

				case SEEK_BLOCK:
				{
					in.seek(fromBits / 8);
					state = State.COPY_BLOCKS;
				}
				break;

				case COPY_BLOCKS:
				{
					readCountBits = fromBits;
					byte skipBits = (byte) (fromBits % 8);
					try(BitInputStream bit = new BitInputStream(in, ByteOrder.BIG_ENDIAN)) {
						bit.readBits(skipBits);

						int currentBlockNo = 0;
						int blockCrc = 0;
						long currentBlockPos = 0;

						while(true) {
							if(Thread.currentThread().isInterrupted())
								return bb;

							int noBits = 8;

							int bitsTooMuch = (int) (blockPosBits[currentBlockNo] - readCountBits);
							if(bitsTooMuch > 0 && bitsTooMuch < 8) {
								noBits = bitsTooMuch;
							}

							// will we run over a block boundary?
							if(readCountBits >= blockPosBits[currentBlockNo]) {

								// read CRC of the current block and add it to the combined CRC
								crcStream = (crcStream << 1) | (crcStream >>> 31);
								crcStream ^= blockCrc;

								currentBlockPos = 0;
								currentBlockNo++;
								blockCrc = 0;

								// did we run over the last block boundary?
								if(currentBlockNo >= blockPosBits.length) {
									state = State.APPEND_END_OF_STREAM_BLOCK;
									break;
								}

							}

							int b = (int) bit.readBits(noBits);
							if(b < 0) { //TODO: eof input stream!
								break;
							}
							readCountBits += noBits;
							currentBlockPos += noBits;

							if(currentBlockPos >= 56 && currentBlockPos <= 80) { //copy crc from current block
								int crcOff = (int) (currentBlockPos - 56);
								blockCrc = (b << (24 - crcOff)) | blockCrc;
							}

							bbb.writeBits(b, noBits);
						}
					}
				}
				break;

				case APPEND_END_OF_STREAM_BLOCK:
				{
					for(byte b: piSqrt)
						bbb.writeBits(b & 0xff, 8);
					for(byte b: toByteArray(crcStream))
						bbb.writeBits(b & 0xff, 8);
					state = State.FIN;
				}
				break;
				}
			}
		}
		bbb.close(); // fill unfinished bytes with 1s
		bb.flip();
		return bb;
	}

	private byte[] toByteArray(int value) {
		return new byte[] {
				(byte)(value >>> 24),
				(byte)(value >>> 16),
				(byte)(value >>> 8),
				(byte)value
		};
	}

	@Override
	public int read() throws IOException {
		if(byteBuffer.hasRemaining()) {
			return byteBuffer.get() & 0xff;
		} else {
			return -1;
		}
	}

	static class BitByteBuffer implements AutoCloseable {

		private final ByteBuffer bb;
		private int bitBuffer;
		private int noBits;

		public BitByteBuffer(ByteBuffer bb) throws IOException {
			this.bb = bb;
		}

		public void writeBits(int bits, int noBits) {
			assert noBits <= 8;

			// check capacity
			int bitsFree = 32 - this.noBits - noBits;
			if(bitsFree < 0) {
				drainByte(noBits);
			}
			addBits(bits, noBits);
		}

		private void drainByte(int noBits) {
			int b = bitBuffer << (32 - this.noBits);
			b >>= 24;
			b = (b & 0xff);
			bb.put((byte)b);
			this.noBits -= 8;
		}

		@Override
		public void close() {
			//drain buffer
			while(this.noBits > 0) {
				drainByte(noBits);
			}
		}

		private void addBits(int nextBits, int bitShift) {
//			int nb = nextBits << (24 - bitShift);
			bitBuffer = (bitBuffer << bitShift | nextBits); // & 0xff_ff_ff_00;
			noBits += bitShift;
		}
	}
}