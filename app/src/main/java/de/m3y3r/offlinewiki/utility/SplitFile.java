package de.m3y3r.offlinewiki.utility;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import de.m3y3r.offlinewiki.Config;

public class SplitFile /*extends File*/ {

	private static final Logger log = Logger.getLogger(Config.getInstance().getLoggerName());

	private final File baseDir;
	private final String baseName;

	public SplitFile(File targetDir, String baseName) {
		if(!targetDir.exists() || !targetDir.isDirectory()) {
			throw new IllegalArgumentException();
		}
		this.baseName = baseName;
		this.baseDir = targetDir;
	}

	public long length() {
		File[] f = baseDir.listFiles((d, n) -> n.startsWith(baseName + '.'));
		return Arrays.stream(f).mapToLong(File::length).sum();
	}

	public boolean exists() {
		File[] f = baseDir.listFiles((d, n) -> n.startsWith(baseName + '.'));
		return f.length > 0;
	}

	public File getParentFile() {
		return baseDir;
	}

	public String getBaseName() {
		return baseName;
	}

	public int getSplitCount() {
		return baseDir.listFiles((d, n) -> n.startsWith(baseName + '.')).length;
	}

	public void deleteLastSplit() {
		Optional<File> last = Arrays.stream(baseDir.listFiles((d, n) -> n.startsWith(baseName + '.')))
				.sorted(Comparator.comparingInt(ke -> {
					int idx = ke.getName().lastIndexOf('.');
					return Integer.parseInt(ke.getName().substring(idx + 1));
				})).sorted(Comparator.reverseOrder()).findFirst();
		log.log(Level.INFO, "first= {0}", last);
		last.get().delete();
	}
}
