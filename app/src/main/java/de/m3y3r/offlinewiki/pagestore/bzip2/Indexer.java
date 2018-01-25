/*
 * Copyright 2012 Thomas Meyer
 */

package de.m3y3r.offlinewiki.pagestore.bzip2;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.m3y3r.offlinewiki.Config;
import de.m3y3r.offlinewiki.Utf8Reader;
import de.m3y3r.offlinewiki.frontend.SearchActivity;
import de.m3y3r.offlinewiki.pagestore.room.TitleDatabase;
import de.m3y3r.offlinewiki.pagestore.room.TitleEntity;
import de.m3y3r.offlinewiki.pagestore.room.XmlDumpEntity;
import de.m3y3r.offlinewiki.utility.BufferInputStream;
import de.m3y3r.offlinewiki.utility.HtmlUtility;
import de.m3y3r.offlinewiki.utility.SplitFile;
import de.m3y3r.offlinewiki.utility.SplitFileInputStream;

import org.apache.commons.compress.compressors.CompressorEvent;
import org.apache.commons.compress.compressors.CompressorEventListener;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

public class Indexer implements Runnable {

	private static final int XML_BUFFER_SIZE = 1024*1024*4;
	private static final int MAX_TITLES = 100;

	private final SplitFile inputFile;
	private final XmlDumpEntity xmlDumpEntity;
	private final Logger logger;
	private final TitleDatabase db;

	/** bzip2 stream mapping: block starts: uncompressed position, position in bits*/
	private TreeMap<Long,Long> bzip2Blocks;

	/** current bzip2 block number */
	private TitleEntity[] titles = new TitleEntity[MAX_TITLES];
	private int titlesIdx;
	private long offsetBlockUncompressedPosition;
	private long offsetBlockPositionInBits;

	public Indexer(TitleDatabase db, XmlDumpEntity xmlDumpEntity) {
		if(db == null || xmlDumpEntity == null) throw new IllegalArgumentException();

		SplitFile dumpFile = new SplitFile(new File(xmlDumpEntity.getDirectory()), xmlDumpEntity.getBaseName());
		this.inputFile = dumpFile;
		this.xmlDumpEntity = xmlDumpEntity;
		this.db = db;

		this.logger = Logger.getLogger(Config.LOGGER_NAME);
		this.bzip2Blocks = new TreeMap<>();
	}

	// we need to do the XML parsing ourselves to get a connection between the current element file offset
	// and the parser state...
	public void run() {

		long fileSize = inputFile.length();

		Map<Integer,StringBuilder> levelNameMap = new HashMap<>();
		int level = 0;

		StringBuilder sbElement = null;
		char[] sbChar = new char[XML_BUFFER_SIZE];
		int sbCharPos = 0;
		long currentTagStartPos = 0;
		long currentTagEndPos = 0;
		long pageTagStartPos = 0;
		int currentMode = 0, nextMode = 0; // FIXME: change to enum
		int titleCount = 0;

		int currentChar;

		try (
				SplitFileInputStream fis = new SplitFileInputStream(inputFile, Config.SPLIT_SIZE);
				BufferInputStream in = new BufferInputStream(fis);
				BZip2CompressorInputStream bZip2In = new BZip2CompressorInputStream(in, false);
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
					int progress = (int) ((blockPositionInBits / 8) / (fileSize / 100));
					SearchActivity.updateProgressBar(progress, 0);
				}
			};
			bZip2In.addCompressorEventListener(listener);

			// read first; the read must happen here, so the bzip2 header is consumed.
			currentChar = utf8Reader.read();

			// restart indexing from the last position
			if(currentChar >= 0 && xmlDumpEntity.getBlockPositionInBits() != null) {
				long posInBits = xmlDumpEntity.getBlockPositionInBits();
				fis.seek(posInBits / 8); // position underlying file to the bzip2 block start
				in.clearBuffer(); // clear buffer content
				bZip2In.resetBlock((byte) (posInBits % 8)); // consume superfluous bits

				// fix internal state of Bzip2CompressorInputStream
				offsetBlockPositionInBits = xmlDumpEntity.getBlockPositionInBits() / 8 * 8; // throw away superfluous bits
				offsetBlockUncompressedPosition = xmlDumpEntity.getBlockPositionUncompressed();

				// skip to next page; set uncompressed byte position
				long nextPagePos = xmlDumpEntity.getPagePositionUncompressed() - xmlDumpEntity.getBlockPositionUncompressed();
				bZip2In.skip(nextPagePos);
				utf8Reader.setCurrentFilePos(xmlDumpEntity.getPagePositionUncompressed());
				currentChar = utf8Reader.read(); // read first character from bzip2 block
				// fix-up levelNameMap, we are at a new <page> now, create fake level 0 entry
				levelNameMap.put(1, new StringBuilder("mediawiki"));
				level++;
			}

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
						addToIndex(title, pageTagStartPos);
						titleCount++;
					}
				} else if(currentMode == 2) {
					if(nextMode == 0 && level == 1) {
						if(titleCount > 0 && titleCount % MAX_TITLES == 0) {
							logger.log(Level.FINE,"Processed {0} pages", titleCount);
							commitTitlesAndXmlDumpEntity(getTitles(), setRestartPosition(currentTagEndPos));
						}
					}
				}

				currentMode = nextMode;

				// read next
				currentChar = utf8Reader.read();
			}

			// store remaining buffer item
			xmlDumpEntity.setIndexFinished(true);
			commitTitlesAndXmlDumpEntity(getTitles(), xmlDumpEntity);
		} catch (IOException e) {
			logger.log(Level.SEVERE, "failed!", e);
		}
	}

	private void commitTitlesAndXmlDumpEntity(TitleEntity[] titles, XmlDumpEntity xmlDumpEntity) {
		db.getDao().insertTitlesAndXmlDumpEntity(xmlDumpEntity, titles);
	}

	private TitleEntity[] getTitles() {
		if(titlesIdx < MAX_TITLES) // last commit case, can be smaller
			return Arrays.copyOf(titles, titlesIdx);

		titlesIdx = 0;
		return titles;
	}

	private XmlDumpEntity setRestartPosition(long currentUncompressedPosition) throws IOException {
		long blockUncompressedPosition;
		long blockPositionInBits;
		synchronized (bzip2Blocks) {
			Entry<Long, Long> e = bzip2Blocks.floorEntry(currentUncompressedPosition);
			blockUncompressedPosition = e.getKey();
			blockPositionInBits = e.getValue();

			// remove all smaller entries from map
			Long lowerKey;
			while ((lowerKey = bzip2Blocks.lowerKey(e.getKey())) != null) {
				bzip2Blocks.remove(lowerKey);
			}
		}

		xmlDumpEntity.setIndexBlockPositionInBits(blockPositionInBits);
		xmlDumpEntity.setIndexBlockPositionUncompressed(blockUncompressedPosition);
		xmlDumpEntity.setIndexPagePositionUncompressed(currentUncompressedPosition);
		return xmlDumpEntity;
	}

	private void addToIndex(String pageTitel, long currentTagUncompressedPosition) throws IOException {

		long blockUncompressedPosition;
		long blockPositionInBits;
		synchronized (bzip2Blocks) {
			Entry<Long, Long> e = bzip2Blocks.floorEntry(currentTagUncompressedPosition);
			blockUncompressedPosition = e.getKey();
			blockPositionInBits = e.getValue();

			// remove all smaller entries from map
			Long lowerKey;
			while ((lowerKey = bzip2Blocks.lowerKey(e.getKey())) != null) {
				bzip2Blocks.remove(lowerKey);
			}
		}

		TitleEntity title = new TitleEntity();
		title.setTitle(pageTitel);
		title.setPageUncompressedPosition(currentTagUncompressedPosition);
		title.setBlockUncompressedPosition(blockUncompressedPosition);
		title.setBlockPositionInBits(blockPositionInBits);
		titles[titlesIdx++] = title;
	}
}
