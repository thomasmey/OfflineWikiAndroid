/*
 * Copyright 2012 Thomas Meyer
 */

package de.m3y3r.offlinewiki;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class PageRetriever implements Closeable {

	private InputStream in;
	private final Map<Integer,QName> levelNameMap;

	private WikiPage.Builder currentPageBuilder;
	private int level;

	private DefaultHandler pageHandler = new DefaultHandler() {

			private StringBuilder currentChars;

			@Override
			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				currentChars = new StringBuilder();

				// add current level to map
				level++;
				QName currentName = new QName(qName);
				levelNameMap.put(level, currentName);

				// process "WikiPage"
				if(currentName.getLocalPart().equals("page")) {
					if(currentPageBuilder == null) {
						// start of page
						currentPageBuilder = new WikiPage.Builder();
					} else {
						// we did hit the next page
						throw new PageReady();
					}
				}
			}

			@Override
			public void endElement(String uri, String localName, String qName) throws SAXException {
				QName levelPage = null;

				if(level >= 2) {
					levelPage = levelNameMap.get(1);
					if(levelPage.getLocalPart().equals("page")) {
						QName levelSubPage;
						if(level == 2) {
							levelSubPage = levelNameMap.get(level);
							if(levelSubPage.getLocalPart().equals("title")) {
								currentPageBuilder.setTitle(currentChars.toString());
							}
							if(levelSubPage.getLocalPart().equals("id")) {
								currentPageBuilder.setId(Long.parseLong(currentChars.toString()));
							}
						} else if (level == 3) {
							levelSubPage = levelNameMap.get(level);
							if(levelSubPage.getLocalPart().equals("id")) {
								currentPageBuilder.setRevisionId(Long.parseLong(currentChars.toString()));
							}
							if(levelSubPage.getLocalPart().equals("comment")) {
								currentPageBuilder.setComment(currentChars.toString());
							}
							if(levelSubPage.getLocalPart().equals("text")) {
								currentPageBuilder.setText(currentChars.toString());
							}
						}
					}
				}

				levelNameMap.remove(level);
				level--;
				if(level == 0) {
					throw new PageReady();
				}
			}

			@Override
			public void characters(char[] ch, int start, int length) throws SAXException {
				currentChars.append(ch, start, length);
			}
		};

	public PageRetriever(InputStream in) throws IOException {
		this.in = in;
		this.levelNameMap = new HashMap<Integer,QName>();
	}

	public WikiPage getNext() throws NumberFormatException, IOException {
		try {
			SAXParserFactory parserFactory = SAXParserFactory.newInstance();
			SAXParser parser = parserFactory.newSAXParser();
			parser.parse(in, pageHandler);
		} catch (PageReady e) {
			return currentPageBuilder.build();
		} catch (ParserConfigurationException | SAXException e) {
			e.printStackTrace();
		}

		return null;
	}

	public void close() throws IOException {
		in.close();
		in = null;
	}

}

class PageReady extends SAXException{
}