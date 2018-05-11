/*
 * Copyright 2012 Thomas Meyer
 */

package de.m3y3r.offlinewiki.pagestore.bzip2;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.compress.compressors.CompressorEvent;
import org.apache.commons.compress.compressors.CompressorEventListener;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import de.m3y3r.offlinewiki.Config;
import de.m3y3r.offlinewiki.Utf8Reader;
import de.m3y3r.offlinewiki.utility.HtmlUtility;

public class Indexer implements Runnable {

	private static final int XML_BUFFER_SIZE = 1024*1024*4;

	private final Logger logger;
	private final List<IndexerEventListener> eventListeners;

	/** bzip2 stream mapping: block starts: uncompressed position, position in bits*/
	private TreeMap<Long,Long> bzip2Blocks;

	/** current bzip2 block number */
	private long offsetBlockUncompressedPosition;
	private long offsetBlockPositionInBits;

	private InputStream inputStream;

	public Indexer(InputStream InputStream) {

		this.inputStream = InputStream;

		this.logger = Logger.getLogger(Config.LOGGER_NAME);
		this.bzip2Blocks = new TreeMap<>();
		this.eventListeners = new CopyOnWriteArrayList<>();
	}

	// we need to do the XML parsing ourselves to get a connection between the current element file offset
	// and the parser state...
	public void run() {

		Map<Integer,StringBuilder> levelNameMap = new HashMap<>();
		int level = 0;

		StringBuilder sbElement = null;
		char[] sbChar = new char[XML_BUFFER_SIZE];
		int sbCharPos = 0;
		long currentTagStartPos = 0;
		long currentTagEndPos = 0;
		long pageTagStartPos = 0;
		int currentMode = 0, nextMode = 0; // FIXME: change to enum

		int currentChar;

		try (
				BZip2CompressorInputStream bZip2In = new BZip2CompressorInputStream(inputStream, false);
				Utf8Reader utf8Reader = new Utf8Reader(bZip2In)) {

			CompressorEventListener listener = e -> {
				if(e.getEventType() == CompressorEvent.EventType.NEW_BLOCK) {
					long blockUncompressedPosition = ((CompressorInputStream) e.getSource()).getBytesRead() + offsetBlockUncompressedPosition;
					long blockPositionInBits = e.getBitsProcessed() + offsetBlockPositionInBits;
					if(e.getEventCounter() % 100 == 0) {
						logger.log(Level.INFO,"Bzip2 block no. {0} at {1} uncompressed at {2}", new Object[] {e.getEventCounter(), blockPositionInBits / 8, blockUncompressedPosition });
					}
					synchronized (bzip2Blocks) {
						bzip2Blocks.put(blockUncompressedPosition, blockPositionInBits);
					}
					fireEventNewBlock(blockPositionInBits);
				}
			};
			bZip2In.addCompressorEventListener(listener);

			// read first; the read must happen here, so the bzip2 header is consumed.
			currentChar = utf8Reader.read();

//			// restart indexing from the last position
//			if(currentChar >= 0 && restarBlockPositionInBits != null) {
//				long posInBits = restarBlockPositionInBits;
//				fis.seek(posInBits / 8); // position underlying file to the bzip2 block start
//				in.clearBuffer(); // clear buffer content
//				bZip2In.resetBlock((byte) (posInBits % 8)); // consume superfluous bits
//
//				// fix internal state of Bzip2CompressorInputStream
//				offsetBlockPositionInBits = restarBlockPositionInBits / 8 * 8; // throw away superfluous bits
//				offsetBlockUncompressedPosition = restartBlockPositionUncompressed;
//
//				// skip to next page; set uncompressed byte position
//				long nextPagePos = restartPagePositionUncompressed - restartBlockPositionUncompressed;
//				bZip2In.skip(nextPagePos);
//				utf8Reader.setCurrentFilePos(restartPagePositionUncompressed);
//				currentChar = utf8Reader.read(); // read first character from bzip2 block
//				// fix-up levelNameMap, we are at a new <page> now, create fake level 0 entry
//				levelNameMap.put(1, new StringBuilder("mediawiki"));
//				level++;
//			}

			while(currentChar >= 0) {
				if(Thread.interrupted())
					return;

				switch (currentMode) {
				case 0: // characters

					if(currentChar == '<') {
						sbElement = new StringBuilder(32);
						nextMode = 1;
						currentTagStartPos = utf8Reader.getCurrentFilePos() - 1;
						break;
					}
					sbChar[sbCharPos] = (char) currentChar;
					sbCharPos++;
					if(sbCharPos > sbChar.length) {
						logger.log(Level.SEVERE,"XML Buffer full! Clearing buffer.");
						sbCharPos=0;
					}
					break;

				case 1: // element name open
					if(currentChar == '/') {
						nextMode = 2;
						break;
					}
					if(currentChar == ' ') {
						nextMode = 3;
						break;
					}
					if(currentChar == '>') {
						level++;
						levelNameMap.put(level, sbElement);
						sbCharPos = 0;
						nextMode = 0;
						break;
					}
					sbElement.appendCodePoint(currentChar);
					break;
				case 2: // element name close
					if(currentChar == '>') {
						levelNameMap.remove(level);
						level--;
						sbCharPos = 0;
						nextMode = 0;
						currentTagEndPos = utf8Reader.getCurrentFilePos();
						break;
					}
					sbElement.appendCodePoint(currentChar);
					break;
				case 3: // element attributes
					if(currentChar == '"') {
						nextMode = 5;
						break;
					}

					if(currentChar == '/') {
						nextMode = 4;
						break;
					}

					if(currentChar == '>') {
						level++;
						levelNameMap.put(level, sbElement);
						sbCharPos = 0;
						nextMode = 0;
						break;
					}
					break;
				case 4: // single element
					if(currentChar == '>') {
						sbCharPos = 0;
						nextMode = 0;
						break;
					}

				case 5: // attribute assignment
					if(currentChar == '"') {
						nextMode = 3;
						break;
					}
				}

				if(currentMode == 1) { // element/tag name open
					if(nextMode == 0 && level == 2 && levelNameMap.get(2).toString().equals("page")) {
						// start of <page> tag - save this position
						pageTagStartPos = currentTagStartPos;
					}
					if(nextMode == 2 && level == 3 && levelNameMap.get(2).toString().equals("page") && levelNameMap.get(3).toString().equals("title")) {
						StringBuilder sb = new StringBuilder(256);
						for(int i=0; i< sbCharPos; i++) {
							sb.appendCodePoint(sbChar[i]);
						}
						String title = HtmlUtility.decodeEntities(sb);
						fireEventNewTitle(title, pageTagStartPos);
					}
				} else if(currentMode == 2) {
					if(nextMode == 0 && level == 1) {
						fireEventTagEnd(currentTagEndPos);
					}
				}

				currentMode = nextMode;

				// read next
				currentChar = utf8Reader.read();
			}

			// store remaining buffer item
			fireEventEndOfStream();

		} catch (IOException e) {
			logger.log(Level.SEVERE, "failed!", e);
		}
	}

	private void fireEventNewBlock(long blockPositionInBits) {
		IndexerEvent event = new IndexerEvent(this);
		for(IndexerEventListener listener: eventListeners) {
			listener.onNewBlock(event, blockPositionInBits);
		}
	}

	private void fireEventEndOfStream() {
		IndexerEvent event = new IndexerEvent(this);
		for(IndexerEventListener listener: eventListeners) {
			listener.onEndOfStream(event);
		}
	}

	private void fireEventNewTitle(String title, long pageTagStartPos) {
		IndexerEvent event = new IndexerEvent(this);
		for(IndexerEventListener listener: eventListeners) {
			listener.onNewTitle(event, title, pageTagStartPos);
		}
	}

	private void fireEventTagEnd(long currentTagEndPos) {
		IndexerEvent event = new IndexerEvent(this);
		for(IndexerEventListener listener: eventListeners) {
			listener.onPageTagEnd(event, currentTagEndPos);
		}
	}

	public Entry<Long, Long> getBlockStartPosition(long currentTagUncompressedPosition) {
		synchronized (bzip2Blocks) {
			Entry<Long, Long> e = bzip2Blocks.floorEntry(currentTagUncompressedPosition);

			// remove all smaller entries from map
			Long lowerKey;
			while ((lowerKey = bzip2Blocks.lowerKey(e.getKey())) != null) {
				bzip2Blocks.remove(lowerKey);
			}
			return e;
		}
	}

	public void addEventListener(IndexerEventListener eventListener) {
		eventListeners.add(eventListener);
	}
}
