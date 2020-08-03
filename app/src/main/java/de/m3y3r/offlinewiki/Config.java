package de.m3y3r.offlinewiki;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import de.m3y3r.offlinewiki.utility.*;
import de.m3y3r.offlinewiki.pagestore.room.AppDatabase;

public final class Config {

	private final static long SPLIT_SIZE = (long) Math.pow(2, 30);
	private final static String LOGGER_NAME = "de.m3y3r.OfflineWiki";

	private static Config instance = new Config();

	private File configFile;
	private final Properties config;

	private Config() {
		configFile = new File("config.xml");

		Properties configDefaults = new Properties();
		try(InputStream inDefault = this.getClass().getResourceAsStream("/config.xml")) {
			configDefaults.loadFromXML(inDefault);
		} catch (NullPointerException | IOException e) {
			e.printStackTrace();
		}

		config = new Properties(configDefaults);
		try(InputStream inState = new FileInputStream(configFile)) {
			config.loadFromXML(inState);
		} catch (NullPointerException | IOException e) {
			e.printStackTrace();
		}
	}

	public static Config getInstance() {
		return instance;
	}

	public String getLoggerName() {
		return LOGGER_NAME;
	}

	public long getSplitSize() {
		return SPLIT_SIZE;
	}

	public SplitFile getXmlDumpFile() {
		String xmlDumpUrl = config.getProperty("xmlDumpUrl");
		if(xmlDumpUrl == null)
			return null;

		File targetDir = new File(".");
		String baseName = Downloader.getBaseNameFromUrl(xmlDumpUrl);
		SplitFile targetDumpFile = new SplitFile(targetDir, baseName);
		return targetDumpFile;
	}

	public synchronized void commitConfig() {
		try(OutputStream outState = new FileOutputStream(configFile)) {
			config.storeToXML(outState, null);
		} catch (NullPointerException | IOException e) {
			e.printStackTrace();
		}
	}

	public String getProperty(String key) {
		return config.getProperty(key);
	}

	public void setProperty(String key, String value) {
		config.setProperty(key, value);
	}

	public String getProperty(String key, String defaultValue) {
		return config.getProperty(key, defaultValue);
	}

}
