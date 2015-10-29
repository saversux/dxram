
package de.uniduesseldorf.dxram.core.chunk.storage;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;

import de.uniduesseldorf.dxram.core.exceptions.MemoryException;
import de.uniduesseldorf.dxram.utils.Contract;
import de.uniduesseldorf.dxram.utils.Tools;
import de.uniduesseldorf.dxram.utils.locks.SpinLock;

/**
 * Manages a large memory block
 * @author Florian Klein 13.02.2014
 */
public final class RawMemory {

	// Constants
	private static final byte POINTER_SIZE = 5;
	private static final byte SMALL_BLOCK_SIZE = 64;
	private static final byte OCCUPIED_FLAGS_OFFSET = 0x5;
	private static final byte OCCUPIED_FLAGS_OFFSET_MASK = 0x03;
	private static final byte SINGLE_BYTE_MARKER = 0xF;

	private static final int MAX_LENGTH_FIELD = 3;

	// Attributes
	private Storage m_memory;

	private Segment[] m_segments;
	private ArenaManager m_arenaManager;

	private long m_segmentSize = -1;
	private long m_fullSegmentSize = -1;
	private int m_freeBlocksListCountPerSegment = -1;
	private int m_freeBlocksListSizePerSegment = -1;
	
	private long[] m_listSizes;

	// Constructors
	/**
	 * Creates an instance of RawMemory
	 * 
	 * @param p_storageInstance Storage instance used as memory.
	 */
	public RawMemory(final Storage p_storageInstance) 
	{
		m_memory = p_storageInstance;
	}

	// Methods
	/**
	 * Initializes the memory
	 * @param p_size
	 *            the size of the memory
	 * @return the actual size of the memory
	 * @throws MemoryException
	 *             if the memory could not be initialized
	 */
	public long initialize(final long p_size, final long p_segmentSize) throws MemoryException {
		long ret;
		int segmentCount;
		long base;
		long remaining;
		long size;

		Contract.check(p_size >= 0, "invalid size given");
		Contract.check(p_segmentSize >= 0, "invalid segment size given");
		
		// detect highest bit using log2 to have proper segment sizes
		{
			int tmp = (int) (Math.log(p_segmentSize) / Math.log(2));
		
			m_segmentSize = (long) Math.pow(2, tmp);
			// according to segment size, have a proper amount of
			// free memory block lists
			// -1, because we don't need a free block list for the full segment
			m_freeBlocksListCountPerSegment = tmp - 1; 
			m_freeBlocksListSizePerSegment = m_freeBlocksListCountPerSegment * POINTER_SIZE + 3;
			m_fullSegmentSize = m_segmentSize + m_freeBlocksListSizePerSegment;
		}

		segmentCount = (int) (p_size / m_segmentSize);
		if (p_size % m_segmentSize > 0) {
			segmentCount++;
		}

		// Initializes the list sizes
		m_listSizes = new long[m_freeBlocksListCountPerSegment];
		for (int i = 0; i < m_freeBlocksListCountPerSegment; i++) {
			m_listSizes[i] = (long) Math.pow(2, i + 2);
		}
		m_listSizes[0] = 12;
		m_listSizes[1] = 24;
		m_listSizes[2] = 36;
		m_listSizes[3] = 48;

		m_memory.allocate(p_size + segmentCount * (2 + m_freeBlocksListSizePerSegment));
		m_memory.set(0, m_memory.getSize(), (byte) 0);

		// Initialize segments
		base = 0;
		remaining = p_size;
		m_segments = new Segment[segmentCount];
		for (int i = 0; i < segmentCount; i++) {
			size = Math.min(m_segmentSize, remaining);
			m_segments[i] = new Segment(i, base, size);

			base += m_fullSegmentSize;
			remaining -= m_segmentSize;
		}
		m_arenaManager = new ArenaManager();

		MemoryStatistic.getInstance().initMemory(p_size);

		System.out.println(
				"RawMemory: init success (" + m_memory + 
				"), segment size " + m_segmentSize + 
				", full segment size " + m_fullSegmentSize +
				", free blocks list count per segment " + m_freeBlocksListCountPerSegment);

		ret = m_memory.getSize();

		// new Timer(true).schedule(new TimerTask() {
		//
		// @Override
		// public void run() {
		// printDebugInfos();
		// }
		//
		// }, 0, 60000);

		return ret;
	}

	/**
	 * Disengages the memory
	 * @throws MemoryException
	 *             if the memory could not be disengaged
	 */
	public void disengage() throws MemoryException {
		m_memory.free();
		m_memory = null;

		m_segments = null;
		m_arenaManager = null;

		m_listSizes = null;
	}

	/**
	 * Dump a range of memory to a file.
	 * @param p_file
	 *            Destination file to dump to.
	 * @param p_addr
	 *            Start address.
	 * @param p_count
	 *            Number of bytes to dump.
	 * @throws MemoryException
	 *             If dumping memory failed.
	 */
	public void dump(final File p_file, final long p_addr, final long p_count) throws MemoryException {
		m_memory.dump(p_file, p_addr, p_count);
	}

	/**
	 * Allocate a memory block
	 * @param p_size
	 *            the size of the block
	 * @return the offset of the block
	 * @throws MemoryException
	 *             if the memory block could not be allocated
	 */
	public long malloc(final int p_size) throws MemoryException {
		long ret = -1;
		Segment segment = null;
		long threadID;

		threadID = Thread.currentThread().getId();

		segment = m_arenaManager.enterArena(threadID, p_size + MAX_LENGTH_FIELD);
		try {
			// Try to allocate in the current segment
			ret = segment.malloc(p_size);

			// Try to allocate in another segment
			if (ret == -1) {
				segment = m_arenaManager.assignNewSegment(threadID, segment, p_size + MAX_LENGTH_FIELD);

				ret = segment.malloc(p_size);
				if (ret == -1) {
					printDebugInfos();
					throw new MemoryException("Could not allocate memory of size " + p_size);
				}
			}
		} finally {
			m_arenaManager.leaveArena(threadID, segment);
		}

		return ret;
	}

	/**
	 * Allocate a large memory block for multiple objects
	 * @param p_sizes
	 *            the sizes of the objects
	 * @return the offsets of the objects
	 * @throws MemoryException
	 *             if the memory block could not be allocated
	 */
	public long[] malloc(final int... p_sizes) throws MemoryException {
		long[] ret;
		long address;
		int size;
		int lengthfieldSize;
		byte marker;
		boolean oneByteOverhead = false;

		Contract.checkNotNull(p_sizes, "no sizes given");
		Contract.check(p_sizes.length > 0, "no sizes given");

		ret = new long[p_sizes.length];
		Arrays.fill(ret, 0);

		try {
			size = getRequiredMemory(p_sizes);
			if (size >= 1 << 16) {
				lengthfieldSize = 3;
				if (size - 3 < 1 << 16) {
					oneByteOverhead = true;
				}
			} else if (size >= 1 << 8) {
				lengthfieldSize = 2;
				if (size - 2 < 1 << 8) {
					oneByteOverhead = true;
				}
			} else {
				lengthfieldSize = 1;
			}
			size -= lengthfieldSize;

			address = malloc(size) - 1;

			if (oneByteOverhead) {
				writeRightPartOfMarker(address, SINGLE_BYTE_MARKER);
				address++;
				writeLeftPartOfMarker(address, SINGLE_BYTE_MARKER);
			}

			for (int i = 0; i < p_sizes.length; i++) {
				if (p_sizes[i] > 0) {
					if (p_sizes[i] >= 1 << 16) {
						lengthfieldSize = 3;
					} else if (p_sizes[i] >= 1 << 8) {
						lengthfieldSize = 2;
					} else {
						lengthfieldSize = 1;
					}
					marker = (byte) (OCCUPIED_FLAGS_OFFSET + lengthfieldSize);

					writeRightPartOfMarker(address, marker);
					address += 1;

					ret[i] = address;

					write(address, p_sizes[i], lengthfieldSize);
					address += lengthfieldSize;
					address += p_sizes[i];

					writeLeftPartOfMarker(address, marker);
				}
			}
		} catch (final Throwable e) {
			throw new MemoryException("could not allocate memory");
		}

		return ret;
	}

	/**
	 * Frees a memory block
	 * @param p_address
	 *            the address of the block
	 * @throws MemoryException
	 *             if the block could not be freed
	 */
	public void free(final long p_address) throws MemoryException {
		Segment segment;

		segment = m_segments[(int) (p_address / m_fullSegmentSize)];
		segment.lock();
		try {
			segment.free(p_address);
		} finally {
			segment.unlock();
		}
	}

	/**
	 * Frees multiple memory blocks
	 * @param p_addresses
	 *            the addresses of the blocks
	 * @throws MemoryException
	 *             if the blocks could not be freed
	 */
	public void free(final long... p_addresses) throws MemoryException {
		boolean exceptionOccured = false;

		Contract.checkNotNull(p_addresses, "no addresses given");

		for (final long address : p_addresses) {
			if (address != 0) {
				try {
					free(address);
				} catch (final MemoryException e) {
					exceptionOccured = true;
				}
			}
		}

		if (exceptionOccured) {
			throw new MemoryException("could not free memory");
		}
	}
	
	/** 
	 * Resize a block of memory. This might allocate a new block instead and
	 * copy the old data to the new block.
	 * @param p_address Address of the block to resize.
	 * @param p_newSize New size for the block.
	 * @return
	 * @throws MemoryException
	 */
	public long realloc(final long p_address, final long p_newSize) throws MemoryException
	{
		byte[] data = null;
		int customState = -1;
		long newAddress = -1;
		
		// TODO this can be solved way more sophisticated
		// (check if our neighbor is a free block and expand etc)
		
		// first, try to alloc more space (might fail)
		customState = getCustomState(p_address);
		data = readBytes(p_address);
		
		newAddress = malloc(data.length);
		if (newAddress != -1)
		{
			setCustomState(newAddress, customState);
			writeBytes(newAddress, data);
		}
		
		return newAddress;
	}

	/**
	 * Calculates the required memory for multiple objects
	 * @param p_sizes
	 *            the sizes od the objects
	 * @return the size of the required memory
	 */
	public int getRequiredMemory(final int... p_sizes) {
		int ret;

		Contract.checkNotNull(p_sizes, "no sizes given");
		Contract.check(p_sizes.length > 0, "no sizes given");

		ret = 0;
		for (final int size : p_sizes) {
			if (size > 0) {
				ret += size;

				if (size >= 1 << 16) {
					ret += 3;
				} else if (size >= 1 << 8) {
					ret += 2;
				} else {
					ret += 1;
				}

				ret += 1;
			}
		}
		ret -= 1;

		return ret;
	}

	/**
	 * Overwrites the bytes in the memory with the given value
	 * @param p_address
	 *            the address to start
	 * @param p_size
	 *            the number of bytes to overwrite
	 * @param p_value
	 *            the value to write
	 * @throws MemoryException
	 *             if the memory could not be set
	 */
	public void set(final long p_address, final long p_size, final byte p_value) throws MemoryException {
		
		Segment segment;

		segment = m_segments[(int) (p_address / m_fullSegmentSize)];
		segment.lock();

		try {
			int lengthFieldSize;
			long blockSize;
			
			// skip length byte(s)
			lengthFieldSize = getSizeFromMarker(readRightPartOfMarker(p_address - 1));
			blockSize = (int) read(p_address, lengthFieldSize);
			
			Contract.check(p_size <= blockSize, "size " + p_size + " exceeds blocksize " + blockSize);

			m_memory.set(p_address + lengthFieldSize, p_size, p_value);
		} catch (final Throwable e) {
			throw new MemoryException("Could not set memory, addr " + p_address + 
					", size " + p_size + ", value " + p_value, e);
		} finally {
			segment.unlock();
		}
	}

	/**
	 * Read a single byte from the specified address.
	 * @param p_address
	 *            Address.
	 * @return Byte read.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public byte readByte(final long p_address) throws MemoryException {
		return readByte(p_address, 0);
	}

	/**
	 * Read a single byte from the specified address + offset.
	 * @param p_address
	 *            Address.
	 * @param p_offset
	 *            Offset to add to the address.
	 * @return Byte read.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public byte readByte(final long p_address, final long p_offset) throws MemoryException {
		try {
			int lengthFieldSize;
			int size;

			// skip length byte(s)
			lengthFieldSize = getSizeFromMarker(readRightPartOfMarker(p_address - 1));
			size = (int) read(p_address, lengthFieldSize);

			Contract.check(p_offset < size, "Offset out of bounds");
			Contract.check(size >= Byte.BYTES && p_offset + Byte.BYTES <= size, "Byte read exceeds bounds");

			return m_memory.readByte(p_address + lengthFieldSize + p_offset);
		} catch (final Throwable e) {
			throw new MemoryException("Could not access memory", e);
		}
	}

	/**
	 * Read a single short from the specified address.
	 * @param p_address
	 *            Address
	 * @return Short read.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public short readShort(final long p_address) throws MemoryException {
		return readShort(p_address, 0);
	}

	/**
	 * Read a single short from the specified address + offset.
	 * @param p_address
	 *            Address.
	 * @param p_offset
	 *            Offset to add to the address.
	 * @return Short read.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public short readShort(final long p_address, final long p_offset) throws MemoryException {
		try {
			int lengthFieldSize;
			int size;

			// skip length byte(s)
			lengthFieldSize = getSizeFromMarker(readRightPartOfMarker(p_address - 1));
			size = (int) read(p_address, lengthFieldSize);

			Contract.check(p_offset < size, "Offset out of bounds");
			Contract.check(size >= Short.BYTES && p_offset + Short.BYTES <= size, "Short read exceeds bounds");

			return m_memory.readShort(p_address + lengthFieldSize + p_offset);
		} catch (final Throwable e) {
			throw new MemoryException("Could not access memory", e);
		}
	}

	/**
	 * Read a single int from the specified address.
	 * @param p_address
	 *            Address.
	 * @return Int read.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public int readInt(final long p_address) throws MemoryException {
		return readInt(p_address, 0);
	}

	/**
	 * Read a single int from the specified address + offset.
	 * @param p_address
	 *            Address.
	 * @param p_offset
	 *            Offset to add to the address.
	 * @return Int read.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public int readInt(final long p_address, final long p_offset) throws MemoryException {
		try {
			int lengthFieldSize;
			int size;

			// skip length byte(s)
			lengthFieldSize = getSizeFromMarker(readRightPartOfMarker(p_address - 1));
			size = (int) read(p_address, lengthFieldSize);

			Contract.check(p_offset < size, "Offset out of bounds");
			Contract.check(size >= Integer.BYTES && p_offset + Integer.BYTES <= size, "Int read exceeds bounds");

			return m_memory.readInt(p_address + lengthFieldSize + p_offset);
		} catch (final Throwable e) {
			throw new MemoryException("Could not access memory", e);
		}
	}

	/**
	 * Read a long from the specified address.
	 * @param p_address
	 *            Address.
	 * @return Long read.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public long readLong(final long p_address) throws MemoryException {
		return readLong(p_address, 0);
	}

	/**
	 * Read a long from the specified address + offset.
	 * @param p_address
	 *            Address.
	 * @param p_offset
	 *            Offset to add to the address.
	 * @return Long read.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public long readLong(final long p_address, final long p_offset) throws MemoryException {
		try {
			int lengthFieldSize;
			int size;

			// skip length byte(s)
			lengthFieldSize = getSizeFromMarker(readRightPartOfMarker(p_address - 1));
			size = (int) read(p_address, lengthFieldSize);

			Contract.check(p_offset < size, "Offset out of bounds");
			Contract.check(size >= Long.BYTES || p_offset + Long.BYTES <= size, "Long read exceeds bounds");

			return m_memory.readLong(p_address + lengthFieldSize + p_offset);
		} catch (final Throwable e) {
			throw new MemoryException("Could not access memory", e);
		}
	}

	/**
	 * Reads a block of bytes from the memory
	 * @param p_address
	 *            the address to start reading
	 * @return the read bytes
	 * @throws MemoryException
	 *             if the bytes could not be read
	 */
	public byte[] readBytes(final long p_address) throws MemoryException {
		return readBytes(p_address, 0);
	}

	/**
	 * Read a block from memory. This will read bytes until the end
	 * of the allocated block, starting address + offset.
	 * @param p_address
	 *            Address of allocated block.
	 * @param p_offset
	 *            Offset added to address for start address to read from.
	 * @return Byte array with read data.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public byte[] readBytes(final long p_address, final long p_offset) throws MemoryException {
		byte[] ret;
		int lengthFieldSize;
		int size;
		long offset;

		lengthFieldSize = getSizeFromMarker(readRightPartOfMarker(p_address - 1));
		size = (int) read(p_address, lengthFieldSize);

		Contract.check(p_offset < size, "Offset out of bounds");

		try {
			ret = new byte[(int) (size - p_offset)];

			offset = p_address + lengthFieldSize + p_offset;
			for (int i = 0; i < size - p_offset; i++) {
				ret[i] = m_memory.readByte(offset + i);
			}
		} catch (final Throwable e) {
			throw new MemoryException("Could not access memory", e);
		}

		return ret;
	}

	/**
	 * Write a single byte to the specified address.
	 * @param p_address
	 *            Address.
	 * @param p_value
	 *            Byte to write.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public void writeByte(final long p_address, final byte p_value) throws MemoryException {
		writeByte(p_address, 0, p_value);
	}

	/**
	 * Write a single byte to the specified address + offset.
	 * @param p_address
	 *            Address.
	 * @param p_offset
	 *            Offset to add to the address.
	 * @param p_value
	 *            Byte to write.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public void writeByte(final long p_address, final long p_offset, final byte p_value)
			throws MemoryException {
		try {
			int lengthFieldSize;
			int size;

			// skip length byte(s)
			lengthFieldSize = getSizeFromMarker(readRightPartOfMarker(p_address - 1));
			size = (int) read(p_address, lengthFieldSize);

			Contract.check(p_offset < size, "Offset out of bounds");
			Contract.check(size >= Byte.BYTES && p_offset + Byte.BYTES <= size, "Byte won't fit into allocated memory");

			m_memory.writeByte(p_address + lengthFieldSize + p_offset, p_value);
		} catch (final Throwable e) {
			throw new MemoryException("Could not access memory", e);
		}
	}

	/**
	 * Write a single short to the specified address.
	 * @param p_address
	 *            Address.
	 * @param p_value
	 *            Short to write.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public void writeShort(final long p_address, final short p_value) throws MemoryException {
		writeShort(p_address, 0, p_value);
	}

	/**
	 * Write a short to the spcified address + offset.
	 * @param p_address
	 *            Address.
	 * @param p_offset
	 *            Offset to add to the address.
	 * @param p_value
	 *            Short to write.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public void writeShort(final long p_address, final long p_offset, final short p_value)
			throws MemoryException {
		try {
			int lengthFieldSize;
			int size;

			// skip length byte(s)
			lengthFieldSize = getSizeFromMarker(readRightPartOfMarker(p_address - 1));
			size = (int) read(p_address, lengthFieldSize);

			Contract.check(p_offset < size, "Offset out of bounds");
			Contract.check(size >= Short.BYTES && p_offset + Short.BYTES <= size,
					"Short won't fit into allocated memory");

			m_memory.writeShort(p_address + lengthFieldSize + p_offset, p_value);
		} catch (final Throwable e) {
			throw new MemoryException("Could not access memory", e);
		}
	}

	/**
	 * Write a single int to the specified address.
	 * @param p_address
	 *            Address.
	 * @param p_value
	 *            Int to write.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public void writeInt(final long p_address, final int p_value) throws MemoryException {
		writeInt(p_address, 0, p_value);
	}

	/**
	 * Write a single int to the specified address + offset.
	 * @param p_address
	 *            Address.
	 * @param p_offset
	 *            Offset to add to the address.
	 * @param p_value
	 *            int to write.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public void writeInt(final long p_address, final long p_offset, final int p_value)
			throws MemoryException {
		try {
			int lengthFieldSize;
			int size;

			// skip length byte(s)
			lengthFieldSize = getSizeFromMarker(readRightPartOfMarker(p_address - 1));
			size = (int) read(p_address, lengthFieldSize);

			Contract.check(p_offset < size, "Offset out of bounds");
			Contract.check(size >= Integer.BYTES && p_offset + Integer.BYTES <= size,
					"Int won't fit into allocated memory");

			m_memory.writeInt(p_address + lengthFieldSize + p_offset, p_value);
		} catch (final Throwable e) {
			throw new MemoryException("Could not access memory", e);
		}
	}

	/**
	 * Write a long value to the specified address.
	 * @param p_address
	 *            Address.
	 * @param p_value
	 *            Long value to write.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public void writeLong(final long p_address, final long p_value) throws MemoryException {
		writeLong(p_address, 0, p_value);
	}

	/**
	 * Write a long value to the specified address + offset.
	 * @param p_address
	 *            Address.
	 * @param p_offset
	 *            Offset to add to the address.
	 * @param p_value
	 *            Long value to write.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public void writeLong(final long p_address, final long p_offset, final long p_value)
			throws MemoryException {
		try {
			int lengthFieldSize;
			int size;

			// skip length byte(s)
			lengthFieldSize = getSizeFromMarker(readRightPartOfMarker(p_address - 1));
			size = (int) read(p_address, lengthFieldSize);

			Contract.check(p_offset < size, "Offset out of bounds");
			Contract.check(size >= Long.BYTES && p_offset + Long.BYTES <= size, "Long won't fit into allocated memory");

			m_memory.writeLong(p_address + lengthFieldSize + p_offset, p_value);
		} catch (final Throwable e) {
			throw new MemoryException("Could not access memory", e);
		}
	}

	/**
	 * Write an array of bytes to the specified address.
	 * @param p_address
	 *            Address.
	 * @param p_value
	 *            Bytes to write.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public void writeBytes(final long p_address, final byte[] p_value) throws MemoryException {
		writeBytes(p_address, 0, p_value);
	}

	/**
	 * Write an array of bytes to the specified address + offset.
	 * @param p_address
	 *            Address.
	 * @param p_offset
	 *            Offset to add to the address.
	 * @param p_value
	 *            Bytes to write.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public void writeBytes(final long p_address, final long p_offset, final byte[] p_value)
			throws MemoryException {
		writeBytes(p_address, p_offset, p_value, p_value.length);
	}
	
	/**
	 * Write an array of bytes to the specified address + offset.
	 * @param p_address
	 *            Address.
	 * @param p_offset
	 *            Offset to add to the address.
	 * @param p_value
	 *            Bytes to write.
	 * @param p_length 
	 * 				Number of bytes to write.
	 * @throws MemoryException
	 *             If accessing memory failed.
	 */
	public void writeBytes(final long p_address, final long p_offset, final byte[] p_value, final int p_length)
			throws MemoryException {
		int lengthFieldSize;
		int size;
		long offset;

		lengthFieldSize = getSizeFromMarker(readRightPartOfMarker(p_address - 1));
		size = (int) read(p_address, lengthFieldSize);

		Contract.check(p_offset < size, "Offset out of bounds");
		Contract.check(p_offset + p_length <= size, "Array won't fit memory");

		try {
			offset = p_address + lengthFieldSize + p_offset;
			for (int i = 0; i < p_length; i++) {
				m_memory.writeByte(offset + i, p_value[i]);
			}
		} catch (final Throwable e) {
			throw new MemoryException("Could not access memory", e);
		}
	}

	/**
	 * Get the user definable state of a specified address referring
	 * a malloc'd block of memory.
	 * @param p_address
	 *            Address of malloc'd block of memory.
	 * @return User definable state stored for that block (valid values: 0, 1, 2. invalid: -1)
	 * @throws MemoryException If reading memory fails.
	 */
	public int getCustomState(final long p_address) throws MemoryException {
		int marker;
		int ret;

		marker = readRightPartOfMarker(p_address - 1);
		if (marker == SINGLE_BYTE_MARKER || marker <= OCCUPIED_FLAGS_OFFSET) {
			// invalid
			ret = -1;
		} else {
			ret = (byte) ((marker - OCCUPIED_FLAGS_OFFSET - 1) / OCCUPIED_FLAGS_OFFSET_MASK);
		}

		return ret;
	}

	/**
	 * Set the user definable state for a specified address referring
	 * a malloc'd block of memory.
	 * @param p_address
	 *            Address of malloc'd block of memory.
	 * @param p_customState
	 *            State to set for that block of memory (valid values: 0, 1, 2.
	 *            all other values invalid).
	 * @throws MemoryException If reading or writing memory fails.
	 */
	public void setCustomState(final long p_address, final int p_customState) throws MemoryException {
		int marker;
		int lengthFieldSize;
		int size;

		Contract.check(p_customState >= 0 && p_customState < 3, 
				"Custom state (" + p_customState + ") out of range, addr: " + p_address);

		marker = readRightPartOfMarker(p_address - 1);
		Contract.check(marker != SINGLE_BYTE_MARKER, 
				"Single byte marker not valid, addr " + p_address + ", marker: " + marker);
		Contract.check(marker > OCCUPIED_FLAGS_OFFSET,
				"Invalid marker " + marker + " at address " + p_address);

		lengthFieldSize = getSizeFromMarker(marker);
		size = (int) read(p_address, lengthFieldSize);
		marker = (OCCUPIED_FLAGS_OFFSET + lengthFieldSize) + (p_customState * 3);

		writeRightPartOfMarker(p_address - 1, marker);
		writeLeftPartOfMarker(p_address + lengthFieldSize + size, marker);
	}

	/**
	 * Get the size of the allocated or free'd block of memory specified
	 * by the given address.
	 * @param p_address
	 *            Address of block to get the size of.
	 * @return Size of memory block at specified address.
	 * @throws MemoryException If reading memory fails.
	 */
	public int getSize(final long p_address) throws MemoryException {
		int lengthFieldSize;

		lengthFieldSize = getSizeFromMarker(readRightPartOfMarker(p_address - 1));
		return (int) read(p_address, lengthFieldSize);
	}

	/**
	 * Gets the current fragmentation of all segments
	 * @return the fragmentation
	 */
	public double[] getFragmentation() {
		double[] ret;

		ret = new double[m_segments.length];
		for (int i = 0; i < m_segments.length; i++) {
			ret[i] = m_segments[i].getFragmentation();
		}

		return ret;
	}

	/**
	 * Gets the segment for the given address
	 * @param p_address
	 *            the address
	 * @return the segment
	 */
	protected int getSegment(final long p_address) {
		return (int) (p_address / m_fullSegmentSize);
	}

	/**
	 * Gets the current segment status
	 * @return the segment status
	 */
	public Segment.Status[] getSegmentStatus() {
		Segment.Status[] ret;

		ret = new Segment.Status[m_segments.length];
		for (int i = 0; i < m_segments.length; i++) {
			ret[i] = m_segments[i].getStatus();
		}

		return ret;
	}

	/**
	 * Prints debug infos
	 */
	public void printDebugInfos() {
		StringBuilder output;
		Segment.Status[] stati;
		long freeSpace;
		long freeBlocks;

		stati = getSegmentStatus();
		freeSpace = 0;
		freeBlocks = 0;
		for (Segment.Status status : stati) {
			freeSpace += status.m_freeSpace;
			freeBlocks += status.m_freeBlocks;
		}

		output = new StringBuilder();
		output.append("\nRawMemory (" + m_memory + "), segment size " + m_segmentSize + 
				", full segment size " + m_fullSegmentSize +
				", free blocks list count per segment " + m_freeBlocksListCountPerSegment);
		output.append("\nSegment Count: " + m_segments.length + " at " + Tools.readableSize(m_fullSegmentSize));
		output.append("\nFree Space: " + Tools.readableSize(freeSpace) + " in " + freeBlocks + " blocks");
		for (int i = 0; i < stati.length; i++) {
			output.append("\n\t" + m_segments[i]);
		}
		output.append("\n");

		System.out.println(output);
	}

	/**
	 * Gets the suitable list for the given size
	 * @param p_size
	 *            the size
	 * @return the suitable list
	 */
	private int getList(final long p_size) {
		int ret = 0;

		while (ret + 1 < m_listSizes.length && m_listSizes[ret + 1] <= p_size) {
			ret++;
		}

		return ret;
	}

	/**
	 * Read the right part of a marker byte
	 * @param p_address
	 *            the address
	 * @return the right part of a marker byte
	 * @throws MemoryException If reading fails.
	 */
	private int readRightPartOfMarker(final long p_address) throws MemoryException {
		return m_memory.readByte(p_address) & 0xF;
	}

	/**
	 * Read the left part of a marker byte
	 * @param p_address
	 *            the address
	 * @return the left part of a marker byte
	 * @throws MemoryException If reading fails.
	 */
	private int readLeftPartOfMarker(final long p_address) throws MemoryException {
		return (m_memory.readByte(p_address) & 0xF0) >> 4;
	}

	/**
	 * Extract the size of the length field of the allocated or free area
	 * from the marker byte.
	 *
	 * @param p_marker Marker byte.
	 * @return Size of the length field of block with specified marker byte.
	 */
	private int getSizeFromMarker(final int p_marker) {
		int ret;

		if (p_marker <= OCCUPIED_FLAGS_OFFSET) {
			// free block size
			ret = p_marker;
		} else {
			ret = ((p_marker - OCCUPIED_FLAGS_OFFSET - 1) % OCCUPIED_FLAGS_OFFSET_MASK) + 1;
		}

		return ret;
	}

	/**
	 * Writes a marker byte
	 * @param p_address
	 *            the address
	 * @param p_right
	 *            the right part
	 * @throws MemoryException If reading fails.
	 */
	private void writeRightPartOfMarker(final long p_address, final int p_right) throws MemoryException {
		byte marker;

		marker = (byte) ((m_memory.readByte(p_address) & 0xF0) + (p_right & 0xF));
		m_memory.writeByte(p_address, marker);
	}

	/**
	 * Writes a marker byte
	 * @param p_address
	 *            the address
	 * @param p_left
	 *            the left part
	 * @throws MemoryException If reading fails.
	 */
	private void writeLeftPartOfMarker(final long p_address, final int p_left) throws MemoryException {
		byte marker;

		marker = (byte) (((p_left & 0xF) << 4) + (m_memory.readByte(p_address) & 0xF));
		m_memory.writeByte(p_address, marker);
	}

	/**
	 * Reads a pointer
	 * @param p_address
	 *            the address
	 * @return the pointer
	 * @throws MemoryException If reading pointer failed.
	 */
	private long readPointer(final long p_address) throws MemoryException {
		return read(p_address, POINTER_SIZE);
	}

	/**
	 * Writes a pointer
	 * @param p_address
	 *            the address
	 * @param p_pointer
	 *            the pointer
	 * @throws MemoryException If writing pointer failed.
	 */
	private void writePointer(final long p_address, final long p_pointer) throws MemoryException {
		write(p_address, p_pointer, POINTER_SIZE);
	}

	/**
	 * Reads up to 8 bytes combined in a long
	 * @param p_address
	 *            the address
	 * @param p_count
	 *            the number of bytes
	 * @return the combined bytes
	 * @throws MemoryException If reading failed.
	 */
	private long read(final long p_address, final int p_count) throws MemoryException {
		return m_memory.readVal(p_address, p_count);
	}

	/**
	 * Writes up to 8 bytes combined in a long
	 * @param p_address
	 *            the address
	 * @param p_bytes
	 *            the combined bytes
	 * @param p_count
	 *            the number of bytes
	 * @throws MemoryException If writing failed.
	 */
	private void write(final long p_address, final long p_bytes, final int p_count) throws MemoryException {
		m_memory.writeVal(p_address, p_bytes, p_count);
	}

	/**
	 * Locks the read lock
	 * @param p_address
	 *            the address of the lock
	 */
	protected void readLock(final long p_address) {
		m_memory.readLock(p_address);
	}

	/**
	 * Unlocks the read lock
	 * @param p_address
	 *            the address of the lock
	 */
	protected void readUnlock(final long p_address) {
		m_memory.readUnlock(p_address);
	}

	/**
	 * Locks the write lock
	 * @param p_address
	 *            the address of the lock
	 */
	protected void writeLock(final long p_address) {
		m_memory.writeLock(p_address);
	}

	/**
	 * Unlocks the write lock
	 * @param p_address
	 *            the address of the lock
	 */
	protected void writeUnlock(final long p_address) {
		m_memory.writeUnlock(p_address);
	}

	// Classes
	/**
	 * Represents a segment of the memory
	 * @author Florian Klein 04.04.2014
	 */
	public final class Segment {

		// Attributes
		private int m_segmentID;
		private long m_pointerOffset;
		private long m_base;
		private long m_size;
		private Status m_status;
		private long m_assignedThread;

		private Lock m_lock;

		// Constructors
		/**
		 * Creates an instance of Segment
		 * @param p_segmentID
		 *            the ID of the segment
		 * @param p_base
		 *            the base address of the segment
		 * @param p_size
		 *            the size of the segment
		 * @throws MemoryException If creating segement block failed.
		 */
		private Segment(final int p_segmentID, final long p_base, final long p_size) throws MemoryException {
			m_segmentID = p_segmentID;
			m_pointerOffset = p_base + p_size;
			m_status = new Status(p_size);
			m_assignedThread = 0;
			m_base = p_base;
			m_size = p_size;

			m_lock = new SpinLock();

			// Create a free block in the complete memory
			createFreeBlock(p_base + 1, p_size);
		}

		// Getters
		/**
		 * Gets the status
		 * @return the status
		 */
		private Status getStatus() {
			return m_status.copy(m_pointerOffset);
		}

		/**
		 * Checks if the Segment is assigned
		 * @return true if the Segment is assigned, false otherwise
		 */
		private boolean isAssigned() {
			return m_assignedThread != 0;
		}

		// Methods
		/**
		 * Assigns the Segment
		 * @param p_threadID
		 *            the assigned thread
		 * @return the previous assigned thread
		 */
		private long assign(final long p_threadID) {
			long ret;

			ret = m_assignedThread;
			m_assignedThread = p_threadID;

			return ret;
		}

		/**
		 * Unassigns the Segment
		 */
		private void unassign() {
			m_assignedThread = 0;
		}

		/**
		 * Locks the Segment
		 */
		private void lock() {
			m_lock.lock();
		}

		/**
		 * Tries to lock the Segment
		 * @return true if the lock is aquired, false otherwise
		 */
		private boolean tryLock() {
			return m_lock.tryLock();
		}

		/**
		 * Unlocks the Segment
		 */
		private void unlock() {
			m_lock.unlock();
		}

		/**
		 * Gets the current fragmentation
		 * @return the fragmentation
		 */
		private double getFragmentation() {
			double ret = 0;
			int free;
			int small;

			free = m_status.getFreeBlocks();
			small = m_status.getSmallBlocks();

			if (small >= 1 || free >= 1) {
				ret = (double) small / free;
			}

			return ret;
		}

		/**
		 * Allocate a memory block
		 * @param p_size
		 *            the size of the block
		 * @return the offset of the block
		 * @throws MemoryException If allocating memory in segment failed.
		 */
		private long malloc(final int p_size) throws MemoryException {
			long ret = -1;
			int list;
			long address;
			long size;
			int lengthFieldSize;
			long freeSize;
			int freeLengthFieldSize;
			byte marker;

			if (p_size <= 0)
				return -1;
			
			if (p_size >= 1 << 16) {
				lengthFieldSize = 3;
			} else if (p_size >= 1 << 8) {
				lengthFieldSize = 2;
			} else {
				lengthFieldSize = 1;
			}
			size = p_size + lengthFieldSize;

			marker = (byte) (OCCUPIED_FLAGS_OFFSET + lengthFieldSize);

			// Get the list with a free block which is big enough
			list = getList(size) + 1;
			while (list < m_freeBlocksListCountPerSegment && readPointer(m_pointerOffset + list * POINTER_SIZE) == 0) {
				list++;
			}
			if (list < m_freeBlocksListCountPerSegment) {
				// A list is found
				address = readPointer(m_pointerOffset + list * POINTER_SIZE);
			} else {
				// Traverse through the lower list
				list = getList(size);
				address = readPointer(m_pointerOffset + list * POINTER_SIZE);
				if (address != 0) {
					freeLengthFieldSize = readRightPartOfMarker(address - 1);
					freeSize = read(address, freeLengthFieldSize);
					while (freeSize < size && address != 0) {
						address = readPointer(address + freeLengthFieldSize + POINTER_SIZE);
						if (address != 0) {
							freeLengthFieldSize = readRightPartOfMarker(address - 1);
							freeSize = read(address, freeLengthFieldSize);
						}
					}
				}
			}

			if (address != 0) {
				// Unhook the free block
				unhookFreeBlock(address);

				freeLengthFieldSize = readRightPartOfMarker(address - 1);
				freeSize = read(address, freeLengthFieldSize);
				if (freeSize == size) {
					m_status.m_freeSpace -= size;
					m_status.m_freeBlocks--;
					if (freeSize < SMALL_BLOCK_SIZE) {
						m_status.m_smallBlocks--;
					}
				} else if (freeSize == size + 1) {
					// 1 Byte to big -> write two markers on the right
					writeRightPartOfMarker(address + size, SINGLE_BYTE_MARKER);
					writeLeftPartOfMarker(address + size + 1, SINGLE_BYTE_MARKER);

					m_status.m_freeSpace -= size + 1;
					m_status.m_freeBlocks--;
					if (freeSize + 1 < SMALL_BLOCK_SIZE) {
						m_status.m_smallBlocks--;
					}
				} else {
					// Block is to big -> create a new free block with the remaining size
					createFreeBlock(address + size + 1, freeSize - size - 1);

					m_status.m_freeSpace -= size + 1;
					if (freeSize >= SMALL_BLOCK_SIZE && freeSize - size - 1 < SMALL_BLOCK_SIZE) {
						m_status.m_smallBlocks++;
					}
				}
				// Write marker
				writeLeftPartOfMarker(address + size, marker);
				writeRightPartOfMarker(address - 1, marker);

				// Write block size
				write(address, p_size, lengthFieldSize);

				MemoryStatistic.getInstance().malloc(size);

				ret = address;
			}

			return ret;
		}

		/**
		 * Frees a memory block
		 * @param p_address
		 *            the address of the block
		 * @throws MemoryException If free'ing memory in segment failed.
		 */
		private void free(final long p_address) throws MemoryException {
			long size;
			long freeSize;
			long address;
			int lengthFieldSize;
			int leftMarker;
			int rightMarker;
			boolean leftFree;
			long leftSize;
			boolean rightFree;
			long rightSize;

			lengthFieldSize = getSizeFromMarker(readRightPartOfMarker(p_address - 1));
			size = getSize(p_address);

			freeSize = size;
			address = p_address;

			// Only merge if left neighbor within same segment
			if (address - 1 != m_base) {
				// Read left part of the marker on the left
				leftMarker = readLeftPartOfMarker(address - 1);
				leftFree = true;
				switch (leftMarker) {
				case 0:
					// Left neighbor block (<= 12 byte) is free -> merge free blocks
					leftSize = read(address - 2, 1) + 1;
					break;
				case 1:
				case 2:
				case 3:
				case 4:
				case 5:
					// Left neighbor block is free -> merge free blocks
					leftSize = read(address - 1 - leftMarker, leftMarker) + 1;

					unhookFreeBlock(address - leftSize);
					break;
				case SINGLE_BYTE_MARKER:
					// Left byte is free -> merge free blocks
					leftSize = 1;
					break;
				default:
					leftSize = 0;
					leftFree = false;
					break;
				}
			} else {
				// Do not merge across segments
				leftSize = 0;
				leftFree = false;
			}

			address -= leftSize;
			freeSize += leftSize;

			// Only merge if right neighbor within same segment
			if (address + size + 1 != m_base + m_size) {
				// Read right part of the marker on the right
				rightMarker = readRightPartOfMarker(p_address + lengthFieldSize + size);
				rightFree = true;
				switch (rightMarker) {
				case 0:
					// Right neighbor block (<= 12 byte) is free -> merge free blocks
					rightSize = getSize(p_address + lengthFieldSize + size + 1);
					break;
				case 1:
				case 2:
				case 3:
				case 4:
				case 5:
					// Right neighbor block is free -> merge free blocks
					rightSize = getSize(p_address + lengthFieldSize + size + 1);

					unhookFreeBlock(p_address + lengthFieldSize + size + 1);
					break;
				case 15:
					// Right byte is free -> merge free blocks
					rightSize = 1;
					break;
				default:
					rightSize = 0;
					rightFree = false;
					break;
				}
			} else {
				// Do not merge across segments
				rightSize = 0;
				rightFree = false;
			}

			freeSize += rightSize;

			// Create a free block
			createFreeBlock(address, freeSize);

			leftSize--;
			rightSize--;

			if (!leftFree && !rightFree) {
				m_status.m_freeSpace += size;
				m_status.m_freeBlocks++;
				if (size < SMALL_BLOCK_SIZE) {
					m_status.m_smallBlocks++;
				}
			} else if (leftFree && !rightFree) {
				m_status.m_freeSpace += size + 1;
				if (size + leftSize >= SMALL_BLOCK_SIZE && leftSize < SMALL_BLOCK_SIZE) {
					m_status.m_smallBlocks--;
				}
			} else if (!leftFree && rightFree) {
				m_status.m_freeSpace += size + 1;
				if (size + rightSize >= SMALL_BLOCK_SIZE && rightSize < SMALL_BLOCK_SIZE) {
					m_status.m_smallBlocks--;
				}
			} else if (leftFree && rightFree) {
				m_status.m_freeSpace += size + 2;
				m_status.m_freeBlocks--;
				m_status.m_smallBlocks--;
				if (size + leftSize + rightSize >= SMALL_BLOCK_SIZE) {
					if (rightSize < SMALL_BLOCK_SIZE && leftSize < SMALL_BLOCK_SIZE) {
						m_status.m_smallBlocks--;
					} else if (rightSize >= SMALL_BLOCK_SIZE && leftSize >= SMALL_BLOCK_SIZE) {
						m_status.m_smallBlocks++;
					}
				}
			}

			MemoryStatistic.getInstance().free(size);
		}

		/**
		 * Creates a free block
		 * @param p_address
		 *            the address
		 * @param p_size
		 *            the size
		 * @throws MemoryException If creating free block failed.
		 */
		private void createFreeBlock(final long p_address, final long p_size) throws MemoryException {
			long listOffset;
			int lengthFieldSize;
			long anchor;
			long size;

			if (p_size < 12) {
				// If size < 12 -> the block will not be hook in the lists
				lengthFieldSize = 0;

				write(p_address, p_size, 1);
				write(p_address + p_size - 1, p_size, 1);
			} else {
				lengthFieldSize = 1;

				// Calculate the number of bytes for the length field
				size = p_size >> 8;
			while (size > 0) {
				lengthFieldSize++;

				size = size >> 8;
			}

			// Get the corresponding list
			listOffset = m_pointerOffset + getList(p_size) * POINTER_SIZE;

			// Hook block in list
			anchor = readPointer(listOffset);

			// Write pointer to list and successor
			writePointer(p_address + lengthFieldSize, listOffset);
			writePointer(p_address + lengthFieldSize + POINTER_SIZE, anchor);
			if (anchor != 0) {
				// Write pointer of successor
				writePointer(anchor + readRightPartOfMarker(anchor - 1), p_address);
			}
			// Write pointer of list
			writePointer(listOffset, p_address);

			// Write length
			write(p_address, p_size, lengthFieldSize);
			write(p_address + p_size - lengthFieldSize, p_size, lengthFieldSize);
			}

			// Write right and left marker
			writeRightPartOfMarker(p_address - 1, lengthFieldSize);
			writeLeftPartOfMarker(p_address + p_size, lengthFieldSize);
		}

		/**
		 * Unhooks a free block
		 * @param p_address
		 *            the address
		 * @throws MemoryException If unhooking free block failed.
		 */
		private void unhookFreeBlock(final long p_address) throws MemoryException {
			int lengthFieldSize;
			long prevPointer;
			long nextPointer;

			// Read size of length field
			lengthFieldSize = readRightPartOfMarker(p_address - 1);

			// Read pointers
			prevPointer = readPointer(p_address + lengthFieldSize);
			nextPointer = readPointer(p_address + lengthFieldSize + POINTER_SIZE);

			if (prevPointer >= m_pointerOffset) {
				// Write Pointer of list
				writePointer(prevPointer, nextPointer);
			} else {
				// Write Pointer of predecessor
				writePointer(prevPointer + lengthFieldSize + POINTER_SIZE, nextPointer);
			}

			if (nextPointer != 0) {
				// Write pointer of successor
				writePointer(nextPointer + lengthFieldSize, prevPointer);
			}
		}

		@Override
		public String toString() {
			return "Segment " + m_segmentID + " (assigned thread: " + m_assignedThread + "): " +
					Tools.readableSize(getStatus().m_freeSpace) + " in "
					+ getStatus().m_freeBlocks + " blocks, borders: " + m_base + "|" + (m_base + m_size) +
					" free blocks pointers offset: " + m_pointerOffset;
		}

		// Classes
		/**
		 * Holds fragmentation information of a segment
		 * @author Florian Klein 10.04.2014
		 */
		public final class Status {

			// Attributes
			private long m_freeSpace;
			private int m_freeBlocks;
			private int m_smallBlocks;
			private long[] m_sizes;
			private long[] m_blocks;

			// Constructors
			/**
			 * Creates an instance of Status
			 * @param p_freeSpace
			 *            the free space
			 */
			private Status(final long p_freeSpace) {
				m_freeSpace = p_freeSpace;
				m_freeBlocks = 1;
				m_smallBlocks = 0;
			}

			// Getters
			/**
			 * Gets the free space
			 * @return the freeSpace
			 */
			public long getFreeSpace() {
				return m_freeSpace;
			}

			/**
			 * Gets the number of free blocks
			 * @return the freeBlocks
			 */
			public int getFreeBlocks() {
				return m_freeBlocks;
			}

			/**
			 * Gets the number of small blocks
			 * @return the smallBlocks
			 */
			public int getSmallBlocks() {
				return m_smallBlocks;
			}

			/**
			 * Gets the sizes
			 * @return the sizes
			 */
			public long[] getSizes() {
				return m_sizes;
			}

			/**
			 * Gets the blocks
			 * @return the blocks
			 */
			public long[] getBlocks() {
				return m_blocks;
			}

			// Methods
			/**
			 * Creates a copy
			 * @param p_pointerOffset
			 *            the pointer offset
			 * @return the copy
			 */
			private Status copy(final long p_pointerOffset) {
				Status ret;

				ret = new Status(0);
				ret.m_freeSpace = m_freeSpace;
				ret.m_freeBlocks = m_freeBlocks;
				ret.m_smallBlocks = m_smallBlocks;

				ret.m_sizes = m_listSizes;
				// ret.m_blocks = new long[0];
				// ret.m_blocks = getBlocks(p_pointerOffset);

				return ret;
			}

			// /**
			// * Gets all free blocks
			// * @param p_pointerOffset
			// * the pointer offset
			// * @return all free blocks
			// */
			// private long[] getBlocks(final long p_pointerOffset) {
			// long[] ret;
			// long address;
			// int lengthFieldSize;
			// long count;
			//
			// ret = new long[m_listSizes.length];
			// for (int i = 0;i < m_listSizes.length;i++) {
			// count = 0;
			//
			// address = readPointer(p_pointerOffset + i * POINTER_SIZE);
			// while (address != 0) {
			// count++;
			//
			// lengthFieldSize = readRightPartOfMarker(address - 1);
			// address = readPointer(address + lengthFieldSize + POINTER_SIZE);
			// }
			//
			// ret[i] = count;
			// }
			//
			// return ret;
			// }

			@Override
			public String toString() {
				return "Status [m_freeSpace=" + m_freeSpace + ", m_freeBlocks=" + m_freeBlocks + ", m_smallBlocks=" + m_smallBlocks + "]";
			}

		}

	}

	/**
	 * Manages the Thread Arenas
	 * @author Florian Klein 28.08.2014
	 */
	private final class ArenaManager {

		// Attributes
		private Map<Long, Segment> m_arenas;

		private Lock m_segmentLock;

		// Constructors
		/**
		 * Creates an instance of ArenaManager
		 */
		private ArenaManager() {
			m_arenas = new HashMap<>();

			m_segmentLock = new SpinLock();
		}

		// Methods
		/**
		 * Gets the assigned Segment of the given Thread and enters the arena
		 * @param p_threadID
		 *            the ID of the Thread
		 * @param p_minSize
		 *            the minimum size of the Segment
		 * @return the assigned Segment
		 * @throws MemoryException
		 *             if no Segment could be assigned
		 */
		private Segment enterArena(final long p_threadID, final int p_minSize) throws MemoryException {
			Segment ret;

			ret = m_arenas.get(p_threadID);
			if (ret == null || !ret.tryLock()) {
				ret = assignNewSegment(p_threadID, null, p_minSize);
			}

			return ret;
		}

		/**
		 * Releases the assigned Segment of the given Thread and leaves the arena
		 * @param p_threadID
		 *            the ID of the Thread
		 * @param p_segment
		 *            the assigned Segment
		 */
		private void leaveArena(final long p_threadID, final Segment p_segment) {
			p_segment.unlock();
		}

		/**
		 * Assigns a new Segment to the Thread
		 * @param p_threadID
		 *            the ID of the Thread
		 * @param p_current
		 *            the current assigned Segment
		 * @param p_minSize
		 *            the minimum size of the new Segment
		 * @return the new assigned Segment
		 * @throws MemoryException
		 *             if no Segment could be assigned
		 */
		private Segment assignNewSegment(final long p_threadID, final Segment p_current, final int p_minSize) throws MemoryException {
			Segment ret = null;
			Segment tempUnassigned;
			double fragmentationUnassigned;
			long freeUnassigned;
			Segment tempAssigned;
			double fragmentationAssigned;
			long freeAssigned;
			double fragmentationTemp;
			long freeTemp;
			long previousThreadID;

			m_segmentLock.lock();
			
			if (p_current != null) {
				m_arenas.put(p_threadID, null);
				p_current.unassign();
				p_current.unlock();
			}

			tempUnassigned = null;
			fragmentationUnassigned = 1;
			freeUnassigned = 0;
			tempAssigned = null;
			fragmentationAssigned = 1;
			freeAssigned = 0;
			// we got trouble assigning segments in multithreaded scenarios
			// the try counter (default was 10) is way too low and causes exceptions
			// that the segment could not be assigned
			// however, 100 is still too low and causes the same trouble
			// all operations on memory have to terminate i.e.
			// we force wait here until a segment is available for stealing
			// old code:
			//for (int tries = 0; tries < 100 && tempUnassigned == null && tempAssigned == null; tries++) {
			while (tempUnassigned == null && tempAssigned == null) {
				for (int i = 0; i < m_segments.length; i++) {
					if (m_segments[i].m_status.m_freeSpace > p_minSize && m_segments[i].tryLock()) {
						if (m_segments[i].isAssigned()) {
							fragmentationTemp = m_segments[i].getFragmentation();
							freeTemp = m_segments[i].m_status.getFreeSpace();

							if (fragmentationTemp < fragmentationAssigned || fragmentationTemp == fragmentationAssigned && freeTemp > freeAssigned) {
								if (tempAssigned != null) {
									tempAssigned.unlock();
								}
								tempAssigned = m_segments[i];
								fragmentationAssigned = fragmentationTemp;
								freeAssigned = freeTemp;
							} else {
								m_segments[i].unlock();
							}
						} else {
							fragmentationTemp = m_segments[i].getFragmentation();
							freeTemp = m_segments[i].m_status.getFreeSpace();

							if (fragmentationTemp < fragmentationUnassigned || fragmentationTemp == fragmentationUnassigned && freeTemp > freeUnassigned) {
								if (tempUnassigned != null) {
									tempUnassigned.unlock();
								}
								tempUnassigned = m_segments[i];
								fragmentationUnassigned = fragmentationTemp;
								freeUnassigned = freeTemp;
							} else {
								m_segments[i].unlock();
							}
						}
					}
				}
			}

			if (tempUnassigned != null) {
				ret = tempUnassigned;
				ret.assign(p_threadID);
				if (tempAssigned != null) {
					tempAssigned.unlock();
				}
			} else if (tempAssigned != null) {
				ret = tempAssigned;
				previousThreadID = ret.assign(p_threadID);
				m_arenas.put(previousThreadID, null);
			} else {
				printDebugInfos();
				throw new MemoryException("Could not assign new segment");
			}
			m_arenas.put(p_threadID, ret);

			m_segmentLock.unlock();

			return ret;
		}

	}

}
