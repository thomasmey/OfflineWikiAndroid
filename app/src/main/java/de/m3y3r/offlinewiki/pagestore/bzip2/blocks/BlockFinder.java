package de.m3y3r.offlinewiki.pagestore.bzip2.blocks;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.util.EventObject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import de.m3y3r.offlinewiki.Config;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.jdbc.JdbcBlockController;
import de.m3y3r.offlinewiki.utility.SplitFile;
import de.m3y3r.offlinewiki.utility.SplitFileInputStream;

/**
 * Scans a BZip2 file for block markers
 *
 * @author thomas
 *
 */
public class BlockFinder implements Runnable {

	private static final long COMPRESSED_MAGIC = 0x314159265359l;
	private static final long EOS_MAGIC = 0x177245385090l;

	private long currentMagic;
	private long readCountBits;
	private long blockNo;
	private long restartBlockNo = -1;

	private final List<BlockFinderEventListener> eventListeners;
	private final BlockController blockController;
	private final SplitFile fileToScan;
	private int blockCount;

	public static void main(String[] args) throws IOException {
//		String baseName = args[0];

		String baseName = "dewiki-latest-pages-articles.xml.bz2";

		SplitFile fileToScan = new SplitFile(new File("."), baseName);

//		File blockFile = new File(baseName + ".blocks");
//		FileBlockController blockController = new FileBlockController(blockFile);

		JdbcBlockController blockController = new JdbcBlockController();
		BlockFinder blockFinder = new BlockFinder(fileToScan, blockController);
		blockFinder.addEventListener(blockController);
		blockFinder.run();
	}

	public BlockFinder(SplitFile fileToScan, BlockController blockController) {
		this.blockController = blockController;
		this.eventListeners = new CopyOnWriteArrayList<>();

		BlockEntry restart = blockController.getLatestEntry();
		if(restart != null) {
			// be careful to not re-process the last found and commited block
			this.blockNo = restart.blockNo;
			this.readCountBits = restart.readCountBits;
			this.restartBlockNo = restart.blockNo;
		}
		this.fileToScan = fileToScan;
	}

	public void run() {
		long startTime = System.currentTimeMillis();

		try {
			boolean isNormalEnd = findBlocksStream();
//			findBlocksMemMapped();
			fireEventEndOfFile(isNormalEnd);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			long endTime = System.currentTimeMillis();
			long diffTime = endTime - startTime;
			long diffPos = readCountBits / 8;
			System.out.println("bytes/second=" + diffPos/(diffTime/1000));
			System.out.println("total time=" + TimeUnit.MILLISECONDS.toSeconds(diffTime));
		}
	}

	private boolean findBlocksStream() throws IOException {
		long byteCount = 0;
		try (SplitFileInputStream in = new SplitFileInputStream(fileToScan, Config.getInstance().getSplitSize())){
			in.seek(readCountBits / 8);
			try(BufferedInputStream bin = new BufferedInputStream(in, (int) Math.pow(2, 22))) {
				int b = bin.read();
				while(b >= 0) {
					if(Thread.interrupted())
						return false;

					update(b);
					b = bin.read();
					byteCount++;
					if(byteCount % 16_000_000 == 0) {
						System.out.println("blockCount="+ blockCount);
					}
				}
			}
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	private void findBlocksMemMapped() throws IOException {
		long restartMapPosInBytes = readCountBits / 8;
		while(true) {
			int splitNo = (int) (readCountBits / 8 / Config.getInstance().getSplitSize());
			File splitFile = new File(fileToScan.getParentFile(),fileToScan.getBaseName() + "." + splitNo);
			if(!splitFile.exists()) {
				fireEventEndOfFile(false);
				break;
			}
			findBlocksMemMappedOneFile(splitFile.toPath(), splitNo, restartMapPosInBytes);
			restartMapPosInBytes = 0;
		}
	}

	private void findBlocksMemMappedOneFile(Path path, int splitNo, long restartMapPosInBytes) throws IOException {
		long startTime = System.currentTimeMillis();

		int mapSize = (int) Math.pow(2,30);
		FileChannel fc = FileChannel.open(path);
		long totalSize = fc.size();
		System.out.format("split no %d, fileSize %d, restart %d\n", splitNo, totalSize, restartMapPosInBytes);

		long currentMapPos = restartMapPosInBytes;
		totalSize -= currentMapPos;

		long size = Math.min(totalSize, mapSize);
		while(size > 0) {
			System.out.format("split no %d, currentMapPos %d, totalSize %d\n", splitNo, currentMapPos, totalSize);
			MappedByteBuffer mbb = fc.map(MapMode.READ_ONLY, currentMapPos, size);
			while(mbb.hasRemaining()) {
				byte b = mbb.get();
				update(b);
			}
			currentMapPos += size;
			totalSize -= size;
			size = Math.min(totalSize, mapSize);
		}
		long endTime = System.currentTimeMillis();
		long diffTime = endTime - startTime;
		long diffPos = readCountBits / 8;
		if(diffTime/1000 > 0)
			System.out.format("bytes/second=%d\n", diffPos/(diffTime/1000));
		System.out.format("total time=%ds\n", TimeUnit.MILLISECONDS.toSeconds(diffTime));
		System.out.format("blocks processed %d %n", ((JdbcBlockController)blockController).totalEntries);
	}

	private void fireEventEndOfFile(boolean isNormalEnd) {
		for(BlockFinderEventListener el: eventListeners) {
			EventObject event = new EventObject(this);
			el.onEndOfFile(event, isNormalEnd);
		}
	}

	private void fireEventNewBlock(long blockNo, long readCountBits, boolean isEndOfStream) {
		blockCount++;
		for(BlockFinderEventListener el: eventListeners) {
			EventObject event = new EventObject(this);
			el.onNewBlock(event, blockNo, readCountBits, isEndOfStream);
		}
	}

	public void update(int b) {
		for(byte bi = 7; bi >= 0; bi--) {
			readCountBits++;
			int cb = (b >> bi) & 1;
			currentMagic = currentMagic << 1 | cb;
			if((currentMagic & 0xff_ff_ff_ff_ff_ffl) == COMPRESSED_MAGIC ||
				(currentMagic & 0xff_ff_ff_ff_ff_ffl) == EOS_MAGIC) {
				if(blockNo != restartBlockNo) {
					fireEventNewBlock(blockNo, readCountBits - 48, ((currentMagic & 0xff_ff_ff_ff_ff_ffl) == EOS_MAGIC));
				}
				blockNo++;
			}
		}
	}

	public void addEventListener(BlockFinderEventListener el) {
		eventListeners.add(el);
	}
}

