package de.m3y3r.offlinewiki.pagestore.bzip2.index;

import java.io.IOException;
import java.util.Iterator;

import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockController;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockEntry;
import de.m3y3r.offlinewiki.pagestore.bzip2.blocks.BlockEntry.IndexState;
import de.m3y3r.offlinewiki.utility.Bzip2BlockInputStream;
import de.m3y3r.offlinewiki.utility.SplitFile;

public class IndexerController implements Runnable {

	private final SplitFile xmlDumpFile;
	private final IndexerEventListener indexerEventListener;
	private final BlockController blockController;

	public IndexerController(SplitFile xmDumpFile, IndexerEventListener indexEventListener, BlockController blockController) {
		this.xmlDumpFile = xmDumpFile;
		this.indexerEventListener = indexEventListener;
		this.blockController = blockController;
	}

	@Override
	public void run() {
		BlockEntry currentBlock = null;
		BlockEntry nextBlock = null;

		long restartPos = 0;
		Iterator<BlockEntry> blockProvider = blockController.getBlockIterator(restartPos);
		if(blockProvider.hasNext()) {
			currentBlock = blockProvider.next();
		}
		if(blockProvider.hasNext()) {
			nextBlock = blockProvider.next();
		}
		if(currentBlock == null) {
			System.out.println("no more blocks");
			return;
		}
		if(nextBlock == null) {
			//FIXME: use last file position as block data?
		}

		for(;blockProvider.hasNext(); currentBlock = nextBlock, nextBlock = blockProvider.next()) {
			if(Thread.currentThread().isInterrupted())
				return;

			// search first INITAL block, i.e. a block that need indexing
			if(currentBlock.indexState == null) currentBlock.indexState = IndexState.INITIAL;
			if(currentBlock.indexState != IndexState.INITIAL) continue;
			System.out.format("processing block %d %d\n", currentBlock.blockNo, currentBlock.readCountBits);

			long[] blockPositions = new long[] {currentBlock.readCountBits, nextBlock.readCountBits};

			try (Bzip2BlockInputStream stream = new Bzip2BlockInputStream(xmlDumpFile, blockPositions)) {
				final BlockEntry be = currentBlock;
				Indexer indexerJob = new Indexer(stream, be.readCountBits);
				indexerJob.addEventListener(indexerEventListener);
				IndexerEventListener stopper = new IndexerEventListener() {
					@Override
					public void onPageTagEnd(IndexerEvent event, long currentTagEndPos) {}
					@Override
					public void onBeforeStartOfStream(IndexerEvent event) {}
					@Override
					public void onPageStart(IndexerEvent event) {}
					@Override
					public void onNewTitle(IndexerEvent event, String title, long pageTagStartPos) {}
					@Override
					public void onEndOfStream(IndexerEvent event, boolean normalEnd) {
						if(normalEnd) {
							blockController.setBlockFinished(be.blockNo);
						} else {
							blockController.setBlockFailed(be.blockNo);
						}
					}
				};
				indexerJob.addEventListener(stopper);
				indexerJob.run();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}

