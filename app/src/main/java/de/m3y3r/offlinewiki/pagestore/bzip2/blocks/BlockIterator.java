package de.m3y3r.offlinewiki.pagestore.bzip2.blocks;


import java.io.IOException;
import java.util.Iterator;

public interface BlockIterator extends Iterator<BlockEntry>, AutoCloseable {
}
