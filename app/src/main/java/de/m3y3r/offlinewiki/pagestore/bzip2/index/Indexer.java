/*
 * Copyright 2012 Thomas Meyer
 */

package de.m3y3r.offlinewiki.pagestore.bzip2.index;

import java.io.EOFException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import de.m3y3r.offlinewiki.Config;
import de.m3y3r.offlinewiki.utility.HtmlUtility;
import de.m3y3r.offlinewiki.utility.Utf8Reader;

public class Indexer implements Runnable {

	private static final int XML_BUFFER_SIZE = 1024*1024*4;

	private final Logger logger;
	private final List<IndexerEventListener> eventListeners;

	private InputStream inputStream;

	private long blockStartPosInBits;

	public Indexer(InputStream InputStream, long blockStartPosInBits) {
		this.inputStream = InputStream;
		this.blockStartPosInBits = blockStartPosInBits;
		this.logger = Logger.getLogger(Config.getInstance().getLoggerName());
		this.eventListeners = new CopyOnWriteArrayList<>();
	}

	private static enum ParserMode { CHARACTERS, TAG_NAME_OPEN, TAG_NAME_CLOSE, TAG_ATTRIBUTE, SINGLE_TAG, ATTRIBUTE_ASSIGNMENT };

	// we need to do the XML parsing ourselves to get a connection between the current element file offset
	// and the parser state...
	public void run() {

		StringBuilder[] levelName = new StringBuilder[8];
		int levelSize = 0;

		StringBuilder sbElement = null;
		char[] sbChar = new char[XML_BUFFER_SIZE];
		int sbCharPos = 0;
		long currentTagStartPos = 0;
		long currentTagEndPos = 0;
		String currentTag = null;
		long pageTagStartPos = 0;
		ParserMode currentMode = ParserMode.CHARACTERS, nextMode = ParserMode.CHARACTERS; // FIXME: change to enum
		boolean syncPageTag = true;

		boolean normalEnd = false;

		fireEventBeforeStartOfStream();

		int currentChar;
		try (
				BZip2CompressorInputStream bZip2In = new BZip2CompressorInputStream(inputStream, false);
				Utf8Reader utf8Reader = new Utf8Reader(bZip2In)) {

			currentChar = utf8Reader.read();
			while(currentChar >= 0) {
				if(Thread.currentThread().isInterrupted())
					return;
//				System.out.print((char)currentChar);

				switch (currentMode) {
				case CHARACTERS:

					if(currentChar == '<') {
						sbElement = new StringBuilder(32);
						nextMode = ParserMode.TAG_NAME_OPEN;
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

				case TAG_NAME_OPEN: // element name open
					if(currentChar == '/') {
						nextMode = ParserMode.TAG_NAME_CLOSE;
						break;
					} else if(currentChar == ' ') {
						nextMode = ParserMode.TAG_ATTRIBUTE;
						break;
					} else if(currentChar == '>') {
						currentTag = sbElement.toString();
						levelName[levelSize] = sbElement;
						levelSize++;
						sbCharPos = 0;
						nextMode = ParserMode.CHARACTERS;
						break;
					}
					sbElement.appendCodePoint(currentChar);
					break;
				case TAG_NAME_CLOSE: // element name close
					if(currentChar == '>') {
						levelSize--;
						if(levelSize < 0)
							levelSize = 0;
						sbCharPos = 0;
						nextMode = ParserMode.CHARACTERS;
						currentTagEndPos = utf8Reader.getCurrentFilePos();
						break;
					}
					sbElement.appendCodePoint(currentChar);
					break;
				case TAG_ATTRIBUTE: // element attributes
					if(currentChar == '"') {
						nextMode = ParserMode.ATTRIBUTE_ASSIGNMENT;
						break;
					}

					if(currentChar == '/') {
						nextMode = ParserMode.SINGLE_TAG;
						break;
					}

					if(currentChar == '>') {
						levelName[levelSize] = sbElement;
						levelSize++;
						sbCharPos = 0;
						nextMode = ParserMode.CHARACTERS;
						break;
					}
					break;
				case SINGLE_TAG: // single element
					if(currentChar == '>') {
						sbCharPos = 0;
						nextMode = ParserMode.CHARACTERS;
						break;
					}

				case ATTRIBUTE_ASSIGNMENT: // attribute assignment
					if(currentChar == '"') {
						nextMode = ParserMode.TAG_ATTRIBUTE;
						break;
					}
				}

				if(currentMode == ParserMode.TAG_NAME_OPEN) { // element/tag name open
					if(nextMode == ParserMode.CHARACTERS) {
						if(syncPageTag) {
							if("page".equals(currentTag)) {
								// sync levelNameMap and level with current state
								levelSize = 1;
								levelName[0] = new StringBuilder("page");
								syncPageTag = false;
							}
						}
						if(levelSize == 1 && levelName[0].toString().equals("page")) {
							// start of <page> tag - save this position
							pageTagStartPos = currentTagStartPos;
						}
					}
					if(nextMode == ParserMode.TAG_NAME_CLOSE && levelSize == 2 && levelName[0].toString().equals("page") && levelName[1].toString().equals("title")) {
						StringBuilder sb = new StringBuilder(256);
						for(int i=0; i< sbCharPos; i++) {
							sb.appendCodePoint(sbChar[i]);
						}
						String title = HtmlUtility.decodeEntities(sb);
						fireEventNewTitle(title, pageTagStartPos);
					}
				} else if(currentMode == ParserMode.TAG_NAME_CLOSE) {
					if(!syncPageTag && nextMode == ParserMode.CHARACTERS && levelSize == 1) {
						fireEventTagEnd(currentTagEndPos);
					}
				}

				currentMode = nextMode;

				// read next
				currentChar = utf8Reader.read();
			}

			normalEnd = true;
			System.out.format("Indexer: End mode %s %n Level: ", currentMode);
			for(int i = 0; i < levelSize; i++) {
				System.out.print(levelName[i]);
				System.out.print('.');
			}
			System.out.println();
		} catch (EOFException e) {
			logger.log(Level.WARNING, "Preliminary end of file while reading next UTF-8 character");
			normalEnd = true;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Exception for block config: {0}", inputStream.toString());
			logger.log(Level.SEVERE, "failed!", e);
		} finally {
			// finished execution, give listener the change to clean-up
			fireEventEndOfStream(normalEnd);
		}
	}

	private void fireEventBeforeStartOfStream() {
		IndexerEvent event = new IndexerEvent(this);
		for(IndexerEventListener listener: eventListeners) {
			listener.onBeforeStartOfStream(event);
		}
	}

	private void fireEventEndOfStream(boolean normalEnd) {
		IndexerEvent event = new IndexerEvent(this);
		for(IndexerEventListener listener: eventListeners) {
			try {
				listener.onEndOfStream(event, normalEnd);
			} catch(Exception e) {
				logger.log(Level.SEVERE, "handler failed! continuning", e);
			}
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

	public void addEventListener(IndexerEventListener eventListener) {
		eventListeners.add(eventListener);
	}

	public long getBlockStartPosition() {
		return blockStartPosInBits;
	}
}
