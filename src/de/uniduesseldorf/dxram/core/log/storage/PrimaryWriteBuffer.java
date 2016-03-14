
package de.uniduesseldorf.dxram.core.log.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import de.uniduesseldorf.dxram.core.CoreComponentFactory;
import de.uniduesseldorf.dxram.core.api.Core;
import de.uniduesseldorf.dxram.core.api.config.Configuration.ConfigurationConstants;
import de.uniduesseldorf.dxram.core.exceptions.DXRAMException;
import de.uniduesseldorf.dxram.core.log.LogHandler;
import de.uniduesseldorf.dxram.core.log.header.AbstractLogEntryHeader;
import de.uniduesseldorf.dxram.core.util.ChunkID;

/**
 * Primary log write buffer Implemented as a ring buffer in a byte array. The
 * in-memory write-buffer for writing on primary log is cyclic. Similar to a
 * ring buffer all read and write accesses are done by using pointers. All
 * readable bytes are between read and write pointer. Unused bytes between write
 * and read pointer. This class is designed for several producers and one
 * consumer (primary log writer- thread).Therefore the write-buffer is
 * implemented thread-safely. There are two write modes. In default mode the
 * buffer is extended adaptively if a threshold is passed (in (flash page size)
 * steps or doubled). Alternatively the caller can be blocked until the write
 * access is completed.
 * @author Kevin Beineke 06.06.2014
 */
public class PrimaryWriteBuffer {

	// Constants
	private static final int WRITE_BUFFER_SIZE = Core.getConfiguration().getIntValue(ConfigurationConstants.WRITE_BUFFER_SIZE);
	private static final int WRITE_BUFFER_MAX_SIZE = Integer.MAX_VALUE;
	private static final int FLASHPAGE_SIZE = Core.getConfiguration().getIntValue(ConfigurationConstants.FLASHPAGE_SIZE);
	// Must be smaller than 1/2 of WRITE_BUFFER_SIZE
	private static final int SIGNAL_ON_BYTE_COUNT = 12 * 1024 * 1024;
	private static final int SECLOG_SEGMENT_SIZE = Core.getConfiguration().getIntValue(ConfigurationConstants.LOG_SEGMENT_SIZE);
	protected static final boolean USE_CHECKSUM = Core.getConfiguration().getBooleanValue(ConfigurationConstants.LOG_CHECKSUM);

	// Attributes
	private LogHandler m_logHandler;

	private byte[] m_buffer;
	private int m_ringBufferSize;
	private PrimaryLogWriterThread m_writerThread;

	private PrimaryLog m_primaryLog;

	private HashMap<Long, Integer> m_lengthByBackupRange;

	private int m_bufferReadPointer;
	private int m_bufferWritePointer;
	private int m_bytesInWriteBuffer;
	private boolean m_isShuttingDown;

	private boolean m_dataAvailable;
	private boolean m_flushingComplete;

	private boolean m_writerThreadRequestsAccessToBuffer;
	private boolean m_writerThreadAccessesBuffer;

	private ReentrantLock m_exclusiveLock;

	private ArrayList<byte[]> m_segmentPoolLarge = new ArrayList<byte[]>();
	private ArrayList<byte[]> m_segmentPoolMedium = new ArrayList<byte[]>();
	private ArrayList<byte[]> m_segmentPoolSmall = new ArrayList<byte[]>();

	// Constructors
	/**
	 * Creates an instance of PrimaryWriteBuffer with user-specific
	 * configuration
	 * @param p_logHandler
	 *            the log Handler
	 * @param p_primaryLog
	 *            Instance of the primary log. Used to write directly to primary log if buffer is full
	 */
	public PrimaryWriteBuffer(final LogHandler p_logHandler, final PrimaryLog p_primaryLog) {
		m_bufferReadPointer = 0;
		m_bufferWritePointer = 0;
		m_bytesInWriteBuffer = 0;
		m_writerThread = null;
		m_flushingComplete = false;
		m_dataAvailable = false;
		m_logHandler = p_logHandler;
		m_primaryLog = p_primaryLog;

		if (WRITE_BUFFER_SIZE < FLASHPAGE_SIZE || WRITE_BUFFER_SIZE > WRITE_BUFFER_MAX_SIZE || Integer.bitCount(WRITE_BUFFER_SIZE) != 1) {
			throw new IllegalArgumentException("Illegal buffer size! Must be 2^x with " + Math.log(FLASHPAGE_SIZE) / Math.log(2) + " <= x <= 31");
		} else {
			m_buffer = new byte[WRITE_BUFFER_SIZE];
			m_ringBufferSize = WRITE_BUFFER_SIZE;
			m_lengthByBackupRange = new HashMap<Long, Integer>();
			m_isShuttingDown = false;
		}

		try {
			m_exclusiveLock = CoreComponentFactory.getNetworkInterface().getExclusiveLock();
		} catch (final DXRAMException e) {
			e.printStackTrace();
		}

		// Creates some buffers for segment pooling (2 * 8 MB, 16 * 1 MB, 32 * 0.5 MB, total: 48 MB)
		// TODO: Configuration
		for (int i = 0; i < 2; i++) {
			m_segmentPoolLarge.add(new byte[SECLOG_SEGMENT_SIZE]);
		}
		for (int i = 0; i < 16; i++) {
			m_segmentPoolMedium.add(new byte[SECLOG_SEGMENT_SIZE / 8]);
		}
		for (int i = 0; i < 32; i++) {
			m_segmentPoolSmall.add(new byte[SECLOG_SEGMENT_SIZE / 16]);
		}

		m_writerThread = new PrimaryLogWriterThread();
		m_writerThread.setName("Logging: Writer Thread");
		m_writerThread.start();
	}

	// Methods
	/**
	 * Cleans the write buffer and resets the pointer
	 * @throws IOException
	 *             if buffer could not be closed
	 * @throws InterruptedException
	 *             if caller is interrupted
	 */
	public final void closeWriteBuffer() throws InterruptedException, IOException {
		// Shutdown primary log writer-thread
		m_flushingComplete = false;
		m_isShuttingDown = true;
		while (!m_flushingComplete) {
			Thread.yield();
		}

		m_writerThread = null;
		m_bufferReadPointer = 0;
		m_bufferWritePointer = 0;
		m_ringBufferSize = 0;
		m_buffer = null;
	}

	/**
	 * Print the throughput statistic
	 */
	public void printThroughput() {
		m_writerThread.printThroughput();
	}

	/**
	 * Grant the writer thread access to buffer meta-data
	 */
	public void grantAccessToWriterThread() {
		m_writerThreadAccessesBuffer = true;
	}

	/**
	 * Writes log entries as a whole (max. size: write buffer) Log entry format:
	 * /////// // CID // LEN // CRC// DATA ... ///////
	 * @param p_header
	 *            the log entry's header as a byte array
	 * @param p_buffer
	 *            the message buffer (position is on payload)
	 * @param p_payloadLength
	 *            the payload length
	 * @throws IOException
	 *             if data could not be flushed to primary log
	 * @throws InterruptedException
	 *             if caller is interrupted
	 * @return the number of written bytes
	 */
	public final int putLogData(final byte[] p_header, final ByteBuffer p_buffer, final int p_payloadLength) throws IOException, InterruptedException {
		AbstractLogEntryHeader logEntryHeader;
		int bytesToWrite;
		int bytesUntilEnd = 0;
		int writePointer;
		Integer counter;
		long rangeID;

		logEntryHeader = AbstractLogEntryHeader.getPrimaryHeader(p_header, 0);
		if (logEntryHeader.wasMigrated()) {
			rangeID = ((long) -1 << 48) + logEntryHeader.getRangeID(p_header, 0);
			bytesToWrite = logEntryHeader.getHeaderSize(p_header, 0) + p_payloadLength;
		} else {
			rangeID = m_logHandler.getBackupRange(logEntryHeader.getCID(p_header, 0));
			bytesToWrite = logEntryHeader.getHeaderSize(p_header, 0) + p_payloadLength;
		}

		if (p_payloadLength <= 0) {
			throw new IllegalArgumentException("No payload for log entry!");
		}
		if (bytesToWrite > m_ringBufferSize) {
			throw new IllegalArgumentException("Data to write exceeds buffer size!");
		}
		if (!m_isShuttingDown) {
			// Wait if writer thread accesses the meta-data to flush buffer
			while (m_writerThreadAccessesBuffer) {
				Thread.yield();
			}

			// Wait if write buffer is nearly full
			while (m_bytesInWriteBuffer > WRITE_BUFFER_SIZE * 0.8) {
				m_dataAvailable = true;
				grantAccessToWriterThread();

				Thread.yield();
			}

			// Set buffer write pointer and byte counter before writing to
			// enable multi-threading
			writePointer = m_bufferWritePointer;
			m_bufferWritePointer = writePointer + bytesToWrite;
			if (m_bufferWritePointer >= m_buffer.length) {
				m_bufferWritePointer = bytesToWrite - (m_buffer.length - writePointer);
			}
			// Update byte counters
			m_bytesInWriteBuffer += bytesToWrite;

			counter = m_lengthByBackupRange.get(rangeID);
			if (null == counter) {
				m_lengthByBackupRange.put(rangeID, bytesToWrite);
			} else {
				m_lengthByBackupRange.put(rangeID, counter + bytesToWrite);
			}

			// Determine free space from end of log to end of array
			if (writePointer >= m_bufferReadPointer) {
				bytesUntilEnd = m_ringBufferSize - writePointer;
			} else {
				bytesUntilEnd = m_bufferReadPointer - writePointer;
			}
			if (bytesToWrite <= bytesUntilEnd) {
				// Write header
				System.arraycopy(p_header, 0, m_buffer, writePointer, p_header.length);
				// Write payload
				p_buffer.get(m_buffer, writePointer + p_header.length, p_payloadLength);
			} else {
				// Twofold cyclic write access
				if (bytesUntilEnd < p_header.length) {
					// Write header
					System.arraycopy(p_header, 0, m_buffer, writePointer, bytesUntilEnd);
					System.arraycopy(p_header, bytesUntilEnd, m_buffer, 0, p_header.length - bytesUntilEnd);
					// Write payload
					p_buffer.get(m_buffer, p_header.length - bytesUntilEnd, p_payloadLength);
				} else if (bytesUntilEnd > p_header.length) {
					// Write header
					System.arraycopy(p_header, 0, m_buffer, writePointer, p_header.length);
					// Write payload
					p_buffer.get(m_buffer, writePointer + p_header.length, bytesUntilEnd - p_header.length);
					p_buffer.get(m_buffer, 0, p_payloadLength - (bytesUntilEnd - p_header.length));
				} else {
					// Write header
					System.arraycopy(p_header, 0, m_buffer, writePointer, p_header.length);
					// Write payload
					p_buffer.get(m_buffer, 0, p_payloadLength);
				}
			}
			if (USE_CHECKSUM) {
				AbstractLogEntryHeader.addChecksum(m_buffer, writePointer, bytesToWrite, logEntryHeader, bytesUntilEnd);
			}

			if (m_bytesInWriteBuffer >= SIGNAL_ON_BYTE_COUNT) {
				// "Wake-up" writer thread if more than SIGNAL_ON_BYTE_COUNT is written
				m_dataAvailable = true;
			}

			// Grant the writer thread access to the meta-data
			if (m_writerThreadRequestsAccessToBuffer) {
				grantAccessToWriterThread();
			}
		}
		return bytesToWrite;
	}

	/**
	 * Wakes-up writer thread and flushes data to primary log
	 * @throws InterruptedException
	 *             if caller is interrupted
	 */
	public final void signalWriterThreadAndFlushToPrimLog() throws InterruptedException {
		m_flushingComplete = false;
		m_dataAvailable = true;

		while (!m_flushingComplete) {
			Thread.yield();
		}
	}

	// Classes
	/**
	 * Writer thread The writer thread flushes data from buffer to primary log
	 * after being waked-up (signal or timer)
	 * @author Kevin Beineke 06.06.2014
	 */
	private final class PrimaryLogWriterThread extends Thread {

		// Constants
		private static final long WRITERTHREAD_TIMEOUTTIME = 100L;

		// Attributes
		private long m_time;
		private long m_amount;
		private double m_throughput;

		// Constructors
		/**
		 * Creates an instance of PrimaryLogWriterThread
		 */
		PrimaryLogWriterThread() {
			m_time = System.currentTimeMillis();
			m_amount = 0;
			m_throughput = 0;
		}

		/**
		 * Print the throughput statistic
		 */
		public void printThroughput() {
			m_throughput = (double) m_amount / (System.currentTimeMillis() - m_time) / 1024 / 1024 * 1000 * 0.9 + m_throughput * 0.1;
			m_amount = 0;
			m_time = System.currentTimeMillis();

			System.out.format("Throughput: %.2f mb/s\n", m_throughput);
		}

		@Override
		public void run() {
			long timeStart;

			for (;;) {
				try {
					if (m_isShuttingDown) {
						// Shutdown signal -> directly flush all data to primary
						// log and shut down
						flushDataToPrimaryLog();
						break;
					}

					timeStart = System.currentTimeMillis();
					while (!m_dataAvailable) {
						m_logHandler.grantReorgThreadAccessToCurrentLog();
						if (System.currentTimeMillis() > timeStart + WRITERTHREAD_TIMEOUTTIME) {
							// Time-out
							break;
						} else {
							// Wait until enough data is available to flush
							Thread.sleep(10);
							// Thread.yield();
						}
					}
					flushDataToPrimaryLog();
					m_logHandler.grantReorgThreadAccessToCurrentLog();
				} catch (final InterruptedException e) {
					System.out.println("Error: Writer thread is interrupted. Directly shuting down!");
					break;
				}
			}
		}

		/**
		 * Flushes all data in write buffer to primary log
		 * @return number of copied bytes
		 * @throws InterruptedException
		 *             if caller is interrupted
		 */
		public int flushDataToPrimaryLog() {
			int writtenBytes = 0;
			int readPointer;
			int bytesInWriteBuffer;
			Set<Entry<Long, Integer>> lengthByBackupRange;

			// 1. Gain exclusive write access
			// 2. Copy read pointer and counter
			// 3. Set read pointer and reset counter
			// 4. Grant access to write buffer
			// 5. Write buffer to hard drive
			// -> During writing to hard drive the next slot in Write Buffer can be filled

			// Gain exclusive write access:
			// Request access and wait for acknowledgement by message handler (lock-free)
			// If after 100ms the access has not been granted (no log message has arrived) -> try to block all message
			// handler
			final long timestamp = System.currentTimeMillis();
			boolean locked = false;
			m_writerThreadRequestsAccessToBuffer = true;
			while (!m_writerThreadAccessesBuffer) {
				m_logHandler.grantReorgThreadAccessToCurrentLog();
				if (System.currentTimeMillis() - timestamp > 100) {
					if (m_exclusiveLock.tryLock()) {
						locked = true;
						break;
					}
				}
				Thread.yield();
			}

			readPointer = m_bufferReadPointer;
			bytesInWriteBuffer = m_bytesInWriteBuffer;
			lengthByBackupRange = m_lengthByBackupRange.entrySet();

			m_bufferReadPointer = m_bufferWritePointer;
			m_bytesInWriteBuffer = 0;
			m_lengthByBackupRange = new HashMap<Long, Integer>();

			m_dataAvailable = false;
			m_flushingComplete = false;

			m_writerThreadRequestsAccessToBuffer = false;
			m_writerThreadAccessesBuffer = false;
			if (locked) {
				m_exclusiveLock.unlock();
			}

			if (bytesInWriteBuffer > 0) {
				// Write data to secondary logs or primary log
				try {
					writtenBytes = bufferAndStore(m_buffer, readPointer, bytesInWriteBuffer, lengthByBackupRange);
				} catch (final IOException | InterruptedException e) {
					System.out.println("Error: Could not write to log");
					e.printStackTrace();
				}
			}
			m_amount += writtenBytes;
			m_flushingComplete = true;

			return writtenBytes;
		}

		/**
		 * Writes given data to secondary log buffers or directly to secondary logs
		 * if longer than a flash page. Merges consecutive log entries of the same
		 * node to limit the number of write accesses
		 * @param p_buffer
		 *            data block
		 * @param p_offset
		 *            offset within the buffer
		 * @param p_length
		 *            length of data
		 * @param p_lengthByBackupRange
		 *            length of data per node
		 * @throws IOException
		 *             if secondary log (buffer) could not be written
		 * @throws InterruptedException
		 *             if caller is interrupted
		 * @return the number of stored bytes
		 */
		private int bufferAndStore(final byte[] p_buffer, final int p_offset, final int p_length,
				final Set<Entry<Long, Integer>> p_lengthByBackupRange) throws InterruptedException, IOException {
			int i = 0;
			int offset;
			int primaryLogBufferOffset = 0;
			int primaryLogBufferSize = 0;
			int bytesRead = 0;
			int logEntrySize;
			int bytesUntilEnd;
			int segmentLength;
			int bufferLength;
			short headerSize;
			short source;
			long rangeID;
			byte[] primaryLogBuffer;
			byte[] header;
			byte[] segment;
			AbstractLogEntryHeader logEntryHeader;
			HashMap<Long, BufferNode> map;
			Iterator<Entry<Long, Integer>> iter;
			Entry<Long, Integer> entry;
			Iterator<Entry<Long, BufferNode>> iter2;
			Entry<Long, BufferNode> entry2;
			BufferNode bufferNode;

			// Sort buffer by backup range
			if (p_buffer.length >= 0) {
				/*
				 * Initialize backup range buffers:
				 * For every NodeID with at least one log entry in this
				 * buffer a hashmap entry will be created. The hashmap entry
				 * contains the RangeID (key), a buffer fitting all log
				 * entries and an offset. The size of the buffer is known
				 * from the monitoring information p_lengthByBackupRange.
				 * The offset is zero if the buffer will be stored in primary
				 * log (complete header) The offset is two if the buffer will be
				 * stored directly in secondary log (header without NodeID).
				 */
				map = new HashMap<Long, BufferNode>();
				iter = p_lengthByBackupRange.iterator();
				while (iter.hasNext()) {
					entry = iter.next();
					rangeID = entry.getKey();
					segmentLength = entry.getValue();
					if (segmentLength < FLASHPAGE_SIZE * 32) {
						// There is less than 4 KB data from this node -> store buffer in primary log (later)
						primaryLogBufferSize += segmentLength;
						bufferNode = new BufferNode(segmentLength, false);
					} else {
						bufferNode = new BufferNode(segmentLength, true);
					}
					map.put(rangeID, bufferNode);
				}

				while (bytesRead < p_length) {
					offset = (p_offset + bytesRead) % p_buffer.length;
					bytesUntilEnd = p_buffer.length - offset;

					logEntryHeader = AbstractLogEntryHeader.getPrimaryHeader(p_buffer, offset);
					/*
					 * Because of the log's wrap around three cases must be distinguished 1. Complete entry fits in
					 * current iteration 2.
					 * Offset pointer is already in next iteration 3. Log entry must be split over two iterations
					 */
					if (logEntryHeader.readable(p_buffer, offset, bytesUntilEnd)) {
						logEntrySize = logEntryHeader.getHeaderSize(p_buffer, offset) + logEntryHeader.getLength(p_buffer, offset);
						if (logEntryHeader.wasMigrated()) {
							rangeID = ((long) -1 << 48) + logEntryHeader.getRangeID(p_buffer, offset);
						} else {
							rangeID = m_logHandler.getBackupRange(logEntryHeader.getCID(p_buffer, offset));
						}

						bufferNode = map.get(rangeID);
						if (logEntryHeader.wasMigrated()) {
							bufferNode.setSource(logEntryHeader.getSource(p_buffer, offset));
						}
						bufferNode.appendToBuffer(p_buffer, offset, logEntrySize, bytesUntilEnd, logEntryHeader.getConversionOffset());
					} else {
						// Buffer overflow -> header is split
						// To get header size only the first byte is necessary
						headerSize = logEntryHeader.getHeaderSize(p_buffer, offset);
						header = new byte[headerSize];
						System.arraycopy(p_buffer, offset, header, 0, bytesUntilEnd);
						System.arraycopy(p_buffer, 0, header, bytesUntilEnd, headerSize - bytesUntilEnd);

						logEntrySize = headerSize + logEntryHeader.getLength(header, 0);
						if (logEntryHeader.wasMigrated()) {
							rangeID = ((long) -1 << 48) + logEntryHeader.getRangeID(header, 0);
						} else {
							rangeID = m_logHandler.getBackupRange(logEntryHeader.getCID(header, 0));
						}

						bufferNode = map.get(rangeID);
						if (logEntryHeader.wasMigrated()) {
							bufferNode.setSource(logEntryHeader.getSource(header, 0));
						}
						bufferNode.appendToBuffer(p_buffer, offset, logEntrySize, bytesUntilEnd, logEntryHeader.getConversionOffset());
					}
					bytesRead += logEntrySize;
				}

				// Write sorted buffers to log
				primaryLogBuffer = new byte[primaryLogBufferSize];

				iter2 = map.entrySet().iterator();
				while (iter2.hasNext()) {
					i = 0;
					entry2 = iter2.next();
					rangeID = entry2.getKey();
					bufferNode = entry2.getValue();
					// bufferNode.trimLastSegment();
					bufferLength = bufferNode.getBufferLength();
					source = bufferNode.getSource();

					segment = bufferNode.getData(i);
					segmentLength = bufferNode.getSegmentLength(i);
					while (segment != null && segmentLength > 0) {
						if (bufferLength < FLASHPAGE_SIZE * 32) {
							// 1. Buffer in secondary log buffer
							if (!bufferLogEntryInSecondaryLogBuffer(segment, 0, segmentLength, rangeID, source)) {
								// 2. Copy log entry/range to write it in primary log subsequently if the buffer was not
								// flushed during appending
								System.arraycopy(segment, 0, primaryLogBuffer, primaryLogBufferOffset, segmentLength);
								primaryLogBufferOffset += segmentLength;
							}
						} else {
							// Segment is larger than one flash page * 32 -> skip primary log
							writeDirectlyToSecondaryLog(segment, 0, segmentLength, rangeID, source);
						}
						// Return byte array to segment pool
						if (segment.length == SECLOG_SEGMENT_SIZE && m_segmentPoolLarge.size() < 2) {
							m_segmentPoolLarge.add(segment);
						} else if (segment.length == SECLOG_SEGMENT_SIZE / 8 && m_segmentPoolMedium.size() < 16) {
							m_segmentPoolMedium.add(segment);
						} else if (segment.length == SECLOG_SEGMENT_SIZE / 16 && m_segmentPoolSmall.size() < 32) {
							m_segmentPoolSmall.add(segment);
						}

						segment = bufferNode.getData(++i);
						segmentLength = bufferNode.getSegmentLength(i);
					}
					iter2.remove();
					bufferNode = null;
				}

				if (primaryLogBufferSize > 0) {
					// Write all log entries, that were not written to secondary log, in primary log with one write
					// access
					m_primaryLog.appendData(primaryLogBuffer, 0, primaryLogBufferOffset);
				}
			}

			return bytesRead;
		}

		/**
		 * Buffers an log entry or log entry range in corresponding secondary log
		 * buffer
		 * @param p_buffer
		 *            data block
		 * @param p_bufferOffset
		 *            position of log entry/range in data block
		 * @param p_logEntrySize
		 *            size of log entry/range
		 * @param p_chunkID
		 *            ChunkID of log entry/range
		 * @param p_source
		 *            the source NodeID
		 * @throws IOException
		 *             if secondary log buffer could not be written
		 * @throws InterruptedException
		 *             if caller is interrupted
		 * @return whether the buffer was flushed or not
		 */
		private boolean bufferLogEntryInSecondaryLogBuffer(final byte[] p_buffer, final int p_bufferOffset, final int p_logEntrySize, final long p_chunkID,
				final short p_source) throws IOException, InterruptedException {
			boolean ret;

			if (ChunkID.getCreatorID(p_chunkID) == -1) {
				ret = m_logHandler.getSecondaryLogBuffer(p_chunkID, p_source, (byte) p_chunkID).bufferData(p_buffer, p_bufferOffset, p_logEntrySize);
			} else {
				ret = m_logHandler.getSecondaryLogBuffer(p_chunkID, p_source, (byte) -1).bufferData(p_buffer, p_bufferOffset, p_logEntrySize);
			}
			return ret;
		}

		/**
		 * Writes a log entry/range directly to secondary log buffer if longer than
		 * one flash page * 32 Has to flush the corresponding secondary log buffer if not
		 * empty to maintain order
		 * @param p_buffer
		 *            data block
		 * @param p_bufferOffset
		 *            position of log entry/range in data block
		 * @param p_logEntrySize
		 *            size of log entry/range
		 * @param p_chunkID
		 *            ChunkID of log entry/range
		 * @param p_source
		 *            the source NodeID
		 * @throws IOException
		 *             if secondary log could not be written
		 * @throws InterruptedException
		 *             if caller is interrupted
		 */
		private void writeDirectlyToSecondaryLog(final byte[] p_buffer, final int p_bufferOffset, final int p_logEntrySize, final long p_chunkID,
				final short p_source) throws IOException, InterruptedException {

			if (ChunkID.getCreatorID(p_chunkID) == -1) {
				m_logHandler.getSecondaryLogBuffer(p_chunkID, p_source, (byte) p_chunkID).flushAllDataToSecLog(p_buffer, p_bufferOffset, p_logEntrySize);
			} else {
				m_logHandler.getSecondaryLogBuffer(p_chunkID, p_source, (byte) -1).flushAllDataToSecLog(p_buffer, p_bufferOffset, p_logEntrySize);
			}
		}
	}

	/**
	 * BufferNode
	 * @author Kevin Beineke 11.08.2014
	 */
	private final class BufferNode {

		// Attributes
		private short m_source;
		private int m_numberOfSegments;
		private int m_currentSegment;
		private int m_bufferLength;
		private boolean m_convert;
		private int[] m_writtenBytesPerSegment;
		private byte[][] m_segments;

		// Constructors
		/**
		 * Creates an instance of BufferNode
		 * @param p_length
		 *            the buffer length (the length might change after converting the headers and fitting the data into
		 *            segments)
		 * @param p_convert
		 *            wether the log entry headers have to be converted or not
		 */
		private BufferNode(final int p_length, final boolean p_convert) {
			int length = p_length;

			m_source = -1;

			m_numberOfSegments = (int) Math.ceil((double) p_length / SECLOG_SEGMENT_SIZE);

			m_currentSegment = 0;
			m_bufferLength = p_length;
			m_convert = p_convert;

			m_writtenBytesPerSegment = new int[m_numberOfSegments];
			m_segments = new byte[m_numberOfSegments][];

			for (int i = 0; i < m_segments.length; i++) {
				if (length > SECLOG_SEGMENT_SIZE / 8) {
					if (m_segmentPoolLarge.size() > 0) {
						m_segments[i] = m_segmentPoolLarge.remove(m_segmentPoolLarge.size() - 1);
						length -= SECLOG_SEGMENT_SIZE;
					} else {
						m_segments[i] = new byte[Math.min(length, SECLOG_SEGMENT_SIZE)];
						length -= m_segments[i].length;
					}
				} else if (length > SECLOG_SEGMENT_SIZE / 16) {
					if (m_segmentPoolMedium.size() > 0) {
						m_segments[i] = m_segmentPoolMedium.remove(m_segmentPoolMedium.size() - 1);
						length -= SECLOG_SEGMENT_SIZE / 8;
					} else {
						m_segments[i] = new byte[length];
						length -= m_segments[i].length;
					}
				} else {
					if (m_segmentPoolSmall.size() > 0) {
						m_segments[i] = m_segmentPoolSmall.remove(m_segmentPoolSmall.size() - 1);
						length -= SECLOG_SEGMENT_SIZE / 16;
					} else if (m_segmentPoolMedium.size() > 0) {
						m_segments[i] = m_segmentPoolMedium.remove(m_segmentPoolMedium.size() - 1);
						length -= SECLOG_SEGMENT_SIZE / 8;
					} else {
						m_segments[i] = new byte[length];
						length -= m_segments[i].length;
					}
				}
			}
		}

		// Getter
		/**
		 * Returns the size of the unprocessed data
		 * @return the size of the unprocessed data
		 */
		private int getBufferLength() {
			return m_bufferLength;
		}

		/**
		 * Returns the number of written bytes per segment
		 * @param p_index
		 *            the index
		 * @return the number of written bytes per segment
		 */
		private int getSegmentLength(final int p_index) {
			int ret = 0;

			if (p_index < m_numberOfSegments) {
				ret = m_writtenBytesPerSegment[p_index];
			}

			return ret;
		}

		/**
		 * Returns the buffer
		 * @param p_index
		 *            the index
		 * @return the buffer
		 */
		private byte[] getData(final int p_index) {
			byte[] ret = null;

			if (p_index < m_numberOfSegments) {
				ret = m_segments[p_index];
			}

			return ret;
		}

		/**
		 * Returns the source
		 * @return the NodeID
		 */
		private short getSource() {
			return m_source;
		}

		// Setter
		/**
		 * Puts the source
		 * @param p_source
		 *            the NodeID
		 */
		private void setSource(final short p_source) {
			m_source = p_source;
		}

		// Methods
		/**
		 * Appends data to node buffer
		 * @param p_buffer
		 *            the buffer
		 * @param p_offset
		 *            the offset within the buffer
		 * @param p_logEntrySize
		 *            the log entry size
		 * @param p_bytesUntilEnd
		 *            the number of bytes until end
		 * @param p_conversionOffset
		 *            the conversion offset
		 */
		private void appendToBuffer(final byte[] p_buffer, final int p_offset,
				final int p_logEntrySize, final int p_bytesUntilEnd, final short p_conversionOffset) {
			int index = -1;
			int futureLogEntrySize;

			if (m_convert) {
				futureLogEntrySize = p_logEntrySize - (p_conversionOffset - 1);
			} else {
				futureLogEntrySize = p_logEntrySize;
			}

			for (int i = 0; i <= m_currentSegment; i++) {
				if (futureLogEntrySize <= SECLOG_SEGMENT_SIZE - m_writtenBytesPerSegment[i]) {
					// A partly used segment has enough free space to store this entry
					index = i;
					break;
				}
			}
			if (index == -1) {
				index = ++m_currentSegment;
				if (m_currentSegment == m_numberOfSegments) {
					// Current entry does not fit in any segment, because there is some fragmentation -> Add a segment
					m_segments = Arrays.copyOf(m_segments, ++m_numberOfSegments);
					m_writtenBytesPerSegment = Arrays.copyOf(m_writtenBytesPerSegment, m_numberOfSegments);
					m_segments[m_currentSegment] = new byte[SECLOG_SEGMENT_SIZE];
				}
			}

			if (m_convert) {
				// More than 32 pages for this node: Convert primary log entry header to secondary log header and append
				// entry to node buffer
				m_writtenBytesPerSegment[index] += AbstractLogEntryHeader.convertAndPut(p_buffer, p_offset, m_segments[index],
						m_writtenBytesPerSegment[index], p_logEntrySize, p_bytesUntilEnd, p_conversionOffset);
			} else {
				// Less than 32 pages for this node: Just append entry to node buffer without converting the log entry
				// header
				if (p_logEntrySize <= p_bytesUntilEnd) {
					System.arraycopy(p_buffer, p_offset, m_segments[index], m_writtenBytesPerSegment[index], p_logEntrySize);
				} else {
					System.arraycopy(p_buffer, p_offset, m_segments[index], m_writtenBytesPerSegment[index], p_bytesUntilEnd);
					System.arraycopy(p_buffer, 0, m_segments[index], m_writtenBytesPerSegment[index] + p_bytesUntilEnd, p_logEntrySize - p_bytesUntilEnd);
				}
				m_writtenBytesPerSegment[index] += p_logEntrySize;
			}

		}
	}
}
