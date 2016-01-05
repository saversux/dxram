package de.uniduesseldorf.dxram.core.lock.messages;

import java.nio.ByteBuffer;

import de.uniduesseldorf.dxram.core.data.DataStructure;
import de.uniduesseldorf.dxram.core.util.ChunkLockOperation;
import de.uniduesseldorf.dxram.core.util.ChunkMessagesMetadataUtils;

import de.uniduesseldorf.menet.AbstractMessage;

/**
 * Request for unlocking Chunks on a remote node
 * @author Florian Klein 09.03.2012
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 5.1.16
 */
public class UnlockMessage extends AbstractMessage {

	// Attributes
	// used when sending to chunk IDs to avoid copying
	private DataStructure[] m_dataStructures = null;
	// used when receiving the request
	private long[] m_chunkIDs = null;

	// Constructors
	/**
	 * Creates an instance of UnlockRequest as a receiver.
	 */
	public UnlockMessage() {
		super();
	}

	/**
	 * Creates an instance of UnlockRequest as a sender
	 * @param p_destination
	 *            the destination node ID.
	 * @param m_lockOperation
	 *            The unlock operation to execute.
	 * @param p_dataStructures
	 * 			  Data structures to be unlocked.
	 */
	public UnlockMessage(final short p_destination, final ChunkLockOperation m_lockOperation, final DataStructure... p_dataStructures) {
		super(p_destination, LockMessages.TYPE, LockMessages.SUBTYPE_UNLOCK_MESSAGE);

		m_dataStructures = p_dataStructures;
		
		byte tmpCode = getStatusCode();
		switch (m_lockOperation)
		{
			case NO_LOCK_OPERATION:
				break;
			case READ_LOCK:
				ChunkMessagesMetadataUtils.setReadLockFlag(tmpCode, true);
			case WRITE_LOCK:
				ChunkMessagesMetadataUtils.setWriteLockFlag(tmpCode, true);
			default:
				assert 1 == 2;
		}

		ChunkMessagesMetadataUtils.setNumberOfItemsToSend(tmpCode, p_dataStructures.length);
		setStatusCode(tmpCode);
	}

	/**
	 * Get the chunk IDs to unlock (when receiving).
	 * @return Chunk IDs to unlock.
	 */
	public long[] getChunkIDs() {
		return m_chunkIDs;
	}
	
	/**
	 * Get the unlock operation to execute.
	 * @return Unlock operation.
	 */
	public ChunkLockOperation getUnlockOperation() {
		if (ChunkMessagesMetadataUtils.isLockAcquireFlagSet(getStatusCode())) {
			if (ChunkMessagesMetadataUtils.isReadLockFlagSet(getStatusCode())) {
				return ChunkLockOperation.READ_LOCK;
			} else {
				return ChunkLockOperation.WRITE_LOCK;
			}
		} else {
			return ChunkLockOperation.NO_LOCK_OPERATION;
		}
	}

	// Methods
	@Override
	protected final void writePayload(final ByteBuffer p_buffer) {
		ChunkMessagesMetadataUtils.setNumberOfItemsInMessageBuffer(getStatusCode(), p_buffer, m_dataStructures.length);
		
		for (DataStructure dataStructure : m_dataStructures) {
			p_buffer.putLong(dataStructure.getID());
		}
	}

	@Override
	protected final void readPayload(final ByteBuffer p_buffer) {
		int numChunks = ChunkMessagesMetadataUtils.getNumberOfItemsFromMessageBuffer(getStatusCode(), p_buffer);
		
		m_chunkIDs = new long[numChunks];
		for (int i = 0; i < m_chunkIDs.length; i++) {
			m_chunkIDs[i] = p_buffer.getLong();
		}
	}

	@Override
	protected final int getPayloadLengthForWrite() {
		return ChunkMessagesMetadataUtils.getSizeOfAdditionalLengthField(getStatusCode()) + Long.BYTES * m_dataStructures.length;
	}

}
