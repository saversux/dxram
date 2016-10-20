
package de.hhu.bsinfo.dxram.lookup.overlay.storage;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import de.hhu.bsinfo.dxram.backup.BackupRange;
import de.hhu.bsinfo.dxram.data.ChunkID;
import de.hhu.bsinfo.dxram.logger.LoggerComponent;
import de.hhu.bsinfo.dxram.lookup.LookupRange;
import de.hhu.bsinfo.dxram.lookup.overlay.OverlayHelper;
import de.hhu.bsinfo.menet.NodeID;

/**
 * Wrapper class for all data of one superpeer
 * @author Kevin Beineke
 *         06.10.2016
 */
public final class MetadataHandler {

	// Attributes
	private LoggerComponent m_logger;

	private LookupTree[] m_lookupTrees;
	private NameserviceHashTable m_nameservice;
	private SuperpeerStorage m_storage;
	private BarriersTable m_barriers;

	private ArrayList<Short> m_assignedPeersIncludingBackups;

	private ReadWriteLock m_dataLock;

	// Constructors
	/**
	 * Creates an instance of SuperpeerMetadata.
	 * @param p_lookupTrees
	 *            the lookup trees (owners + backups)
	 * @param p_nameservice
	 *            hash table for the nameservice
	 * @param p_storage
	 *            the superpeer storage
	 * @param p_barriers
	 *            the barriers
	 * @param p_assignedPeersIncludingBackups
	 *            reference to all assigned peers including backups
	 * @param p_logger
	 *            the logger component
	 */
	public MetadataHandler(final LookupTree[] p_lookupTrees, final NameserviceHashTable p_nameservice,
			final SuperpeerStorage p_storage, final BarriersTable p_barriers,
			final ArrayList<Short> p_assignedPeersIncludingBackups, final LoggerComponent p_logger) {
		m_lookupTrees = p_lookupTrees;
		m_nameservice = p_nameservice;
		m_storage = p_storage;
		m_barriers = p_barriers;

		m_assignedPeersIncludingBackups = p_assignedPeersIncludingBackups;

		m_dataLock = new ReentrantReadWriteLock(false);

		m_logger = p_logger;
	}

	/**
	 * Serializes all metadata in given area.
	 * @param p_beginOfArea
	 *            the beginning of the area
	 * @param p_endOfArea
	 *            the end of the area
	 * @return all corresponding metadata in a byte array
	 * @lock overlay lock must be read-locked
	 */
	public byte[] receiveMetadataInRange(final short p_beginOfArea, final short p_endOfArea) {
		int size;
		int count = 0;
		int index;
		int startIndex;
		short currentPeer;
		byte[] ret = null;
		byte[] nameserviceEntries = null;
		byte[] storages = null;
		byte[] barriers = null;
		ByteBuffer data;

		m_dataLock.readLock().lock();
		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Serializing metadata of area: "
				+ NodeID.toHexString(p_beginOfArea) + ", " + NodeID.toHexString(p_endOfArea));
		// #endif /* LOGGER == TRACE */

		// Get all corresponding nameservice entries
		nameserviceEntries = m_nameservice.receiveMetadataInRange(p_beginOfArea, p_endOfArea);
		// Get all corresponding storages
		storages = m_storage.receiveMetadataInRange(p_beginOfArea, p_endOfArea);
		// Get all corresponding barriers
		barriers = m_barriers.receiveMetadataInRange(p_beginOfArea, p_endOfArea);

		// Get all corresponding lookup trees
		size = nameserviceEntries.length + storages.length + barriers.length + Integer.BYTES * 4;
		if (0 != m_assignedPeersIncludingBackups.size()) {
			// Find beginning
			index = Collections.binarySearch(m_assignedPeersIncludingBackups, p_beginOfArea);
			if (0 > index) {
				index = index * -1 - 1;
				if (index == m_assignedPeersIncludingBackups.size()) {
					index = 0;
				}
			}

			// Iterate over assigned peers and count lookup tree sizes
			startIndex = index;
			currentPeer = m_assignedPeersIncludingBackups.get(index++);
			while (OverlayHelper.isPeerInSuperpeerRange(currentPeer, p_beginOfArea, p_endOfArea)) {
				size += LookupTree.getLookupTreeWriteLength(getLookupTreeLocal(currentPeer)) + Short.BYTES;
				count++;

				if (index == m_assignedPeersIncludingBackups.size()) {
					index = 0;
				}
				if (index == startIndex) {
					break;
				}
				currentPeer = m_assignedPeersIncludingBackups.get(index++);
			}

			// Allocate array for all data and copy already gathered data
			ret = new byte[size];
			data = ByteBuffer.wrap(ret);
			data.putInt(nameserviceEntries.length);
			data.put(nameserviceEntries);
			data.putInt(storages.length);
			data.put(storages);
			data.putInt(barriers.length);
			data.put(barriers);

			// Iterate over assigned peers and write lookup trees
			data.putInt(count);
			index = startIndex;
			currentPeer = m_assignedPeersIncludingBackups.get(index++);
			while (OverlayHelper.isPeerInSuperpeerRange(currentPeer, p_beginOfArea, p_endOfArea)) {
				// #if LOGGER == TRACE
				m_logger.trace(getClass(), " Including LookupTree of " + NodeID.toHexString(currentPeer));
				// #endif /* LOGGER == TRACE */

				data.putShort(currentPeer);
				LookupTree.writeLookupTree(data, getLookupTreeLocal(currentPeer));

				if (index == m_assignedPeersIncludingBackups.size()) {
					index = 0;
				}
				if (index == startIndex) {
					break;
				}
				currentPeer = m_assignedPeersIncludingBackups.get(index++);
			}
		} else {
			// There might be data in given area even without any assigned peer

			// Allocate array for all data and copy gathered data
			ret = new byte[size];
			data = ByteBuffer.wrap(ret);
			data.putInt(nameserviceEntries.length);
			data.put(nameserviceEntries);
			data.putInt(storages.length);
			data.put(storages);
			data.putInt(barriers.length);
			data.put(barriers);
		}
		m_dataLock.readLock().unlock();

		// If there is no metadata in given area, return an empty array
		if (ret.length == Integer.BYTES * 4) {
			ret = new byte[0];
		}

		return ret;
	}

	/**
	 * Serializes all metadata.
	 * @return all metadata in a byte array
	 */
	public byte[] receiveAllMetadata() {
		int size;
		int count = 0;
		byte[] ret = null;
		byte[] nameserviceEntries = null;
		byte[] storages = null;
		byte[] barriers = null;
		LookupTree tree;
		ByteBuffer data;

		m_dataLock.readLock().lock();
		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Serializing all metadata");
		// #endif /* LOGGER == TRACE */

		// Get all nameservice entries
		nameserviceEntries = m_nameservice.receiveAllMetadata();
		// Get all storages
		storages = m_storage.receiveAllMetadata();
		// Get all barriers
		barriers = m_barriers.receiveAllMetadata();

		// Get all lookup trees
		size = nameserviceEntries.length + storages.length + barriers.length + Integer.BYTES * 4;

		// Iterate over all peers and count lookup tree sizes
		for (int i = 0; i < Short.MAX_VALUE * 2; i++) {
			tree = getLookupTreeLocal((short) i);
			if (tree != null) {
				size += LookupTree.getLookupTreeWriteLength(tree) + Short.BYTES;
				count++;
			}
		}

		// Allocate array for all data and copy already gathered data
		ret = new byte[size];
		data = ByteBuffer.wrap(ret);
		data.putInt(nameserviceEntries.length);
		data.put(nameserviceEntries);
		data.putInt(storages.length);
		data.put(storages);
		data.putInt(barriers.length);
		data.put(barriers);

		// Iterate over all peers and write lookup trees
		data.putInt(count);
		for (int i = 0; i < Short.MAX_VALUE * 2; i++) {
			tree = getLookupTreeLocal((short) i);
			if (tree != null) {
				// #if LOGGER == TRACE
				m_logger.trace(getClass(), " Including LookupTree of " + NodeID.toHexString((short) i));
				// #endif /* LOGGER == TRACE */

				data.putShort((short) i);
				LookupTree.writeLookupTree(data, tree);
			}
		}
		m_dataLock.readLock().unlock();

		// If there is no metadata, return an empty array
		if (ret.length == Integer.BYTES * 4) {
			ret = new byte[0];
		}

		return ret;
	}

	/**
	 * Returns the number of nameservice entries in given area
	 * @param p_responsibleArea
	 *            the area
	 * @return the number of nameservice entries
	 */
	public int getNumberOfNameserviceEntries(final short[] p_responsibleArea) {
		int ret;

		m_dataLock.readLock().lock();
		ret = m_nameservice.quantifyMetadata(p_responsibleArea[0], p_responsibleArea[1]);
		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Returns the number of storages in given area
	 * @param p_responsibleArea
	 *            the area
	 * @return the number of storages
	 */
	public int getNumberOfStorages(final short[] p_responsibleArea) {
		int ret;

		m_dataLock.readLock().lock();
		ret = m_storage.quantifyMetadata(p_responsibleArea[0], p_responsibleArea[1]);
		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Returns the number of barriers in given area
	 * @param p_responsibleArea
	 *            the area
	 * @return the number of barriers
	 */
	public int getNumberOfBarriers(final short[] p_responsibleArea) {
		int ret;

		m_dataLock.readLock().lock();
		ret = m_barriers.quantifyMetadata(p_responsibleArea[0], p_responsibleArea[1]);
		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Get a summary of this superpeer's metadata
	 * @param p_nodeID
	 *            this superpeer's NodeID
	 * @param p_predecessor
	 *            the predecessor's NodeID
	 * @return the metadata summary
	 * @lock overlay lock must be read-locked
	 */
	public String getSummary(final short p_nodeID, final short p_predecessor) {
		String ret = "";
		LookupTree tree;

		m_dataLock.readLock().lock();
		ret += "Number of nameservice entries: " + m_nameservice.quantifyMetadata(p_nodeID, p_nodeID)
				+ ", assigned: " + m_nameservice.quantifyMetadata(p_predecessor, p_nodeID) + "\n";

		ret += "Number of storages: \t\t " + m_storage.quantifyMetadata(p_nodeID, p_nodeID)
				+ ", assigned: " + m_storage.quantifyMetadata(p_predecessor, p_nodeID) + "\n";

		ret += "Number of barriers: \t\t " + m_barriers.quantifyMetadata(p_nodeID, p_nodeID)
				+ ", assigned: " + m_barriers.quantifyMetadata(p_predecessor, p_nodeID) + "\n";

		ret += "Storing LookupTrees of following peers:\n";
		for (int i = 0; i < Short.MAX_VALUE * 2; i++) {
			tree = getLookupTreeLocal((short) i);
			if (tree != null) {
				if (OverlayHelper.isPeerInSuperpeerRange((short) i, p_predecessor, p_nodeID)) {
					ret += "--> " + NodeID.toHexString((short) i);
				} else {
					ret += "--> " + NodeID.toHexString((short) i) + " (Backup)";
				}
			}
		}
		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Compares given peer list with local list and returns all missing backup data between this superpeer and his
	 * predecessor.
	 * @param p_peers
	 *            all peers the requesting superpeer stores backups for
	 * @param p_numberOfNameserviceEntries
	 *            the number of expected nameservice entries
	 * @param p_numberOfStorages
	 *            the number of expected storages
	 * @param p_numberOfBarriers
	 *            the number of expected barriers
	 * @param p_predecessor
	 *            the predecessor
	 * @param p_nodeID
	 *            the own NodeID
	 * @return the backup data of missing peers in given peer list
	 * @lock overlay lock must be read-locked
	 */
	public byte[] compareAndReturnBackups(final ArrayList<Short> p_peers, final int p_numberOfNameserviceEntries,
			final int p_numberOfStorages,
			final int p_numberOfBarriers, final short p_predecessor, final short p_nodeID) {
		int size;
		int count = 0;
		int index;
		int startIndex;
		short currentPeer;
		byte[] ret = null;
		byte[] nameserviceEntries = null;
		byte[] storages = null;
		byte[] barriers = null;
		ByteBuffer data;

		m_dataLock.readLock().lock();
		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Compare and return metadata of area: "
				+ NodeID.toHexString(p_predecessor) + ", " + NodeID.toHexString(p_nodeID));
		// #endif /* LOGGER == TRACE */

		// TODO: Inefficient to send all data (nameservice, storages, barriers) in corresponding area if quantity
		// differs
		size = 4 * Integer.BYTES;
		// Compare number of actual nameservice entries with expected number
		if (m_nameservice.quantifyMetadata(p_predecessor, p_nodeID) != p_numberOfNameserviceEntries) {
			// Get all corresponding nameservice entries
			nameserviceEntries = m_nameservice.receiveMetadataInRange(p_predecessor, p_nodeID);
			size += nameserviceEntries.length;
		}
		// Compare number of actual storages with expected number
		if (m_storage.quantifyMetadata(p_predecessor, p_nodeID) != p_numberOfStorages) {
			// Get all corresponding storages
			storages = m_storage.receiveMetadataInRange(p_predecessor, p_nodeID);
			size += storages.length;
		}
		// Compare number of actual barriers with expected number
		if (m_barriers.quantifyMetadata(p_predecessor, p_nodeID) != p_numberOfBarriers) {
			// Get all corresponding barriers
			barriers = m_barriers.receiveMetadataInRange(p_predecessor, p_nodeID);
			size += barriers.length;
		}

		// Get all corresponding lookup trees
		if (0 != m_assignedPeersIncludingBackups.size()) {
			// Find beginning
			index = Collections.binarySearch(m_assignedPeersIncludingBackups, p_predecessor);
			if (0 > index) {
				index = index * -1 - 1;
				if (index == m_assignedPeersIncludingBackups.size()) {
					index = 0;
				}
			}

			// Iterate over assigned peers and count lookup tree sizes
			startIndex = index;
			currentPeer = m_assignedPeersIncludingBackups.get(index++);
			while (OverlayHelper.isPeerInSuperpeerRange(currentPeer, p_predecessor, p_nodeID)) {
				if (0 > Collections.binarySearch(p_peers, currentPeer)) {
					size += LookupTree.getLookupTreeWriteLength(getLookupTreeLocal(currentPeer)) + Short.BYTES;
					count++;
				}

				if (index == m_assignedPeersIncludingBackups.size()) {
					index = 0;
				}
				if (index == startIndex) {
					break;
				}
				currentPeer = m_assignedPeersIncludingBackups.get(index++);
			}

			// Allocate array for all data and copy already gathered data
			ret = new byte[size];
			data = ByteBuffer.wrap(ret);
			if (nameserviceEntries != null) {
				data.putInt(nameserviceEntries.length);
				data.put(nameserviceEntries);
			} else {
				data.putInt(0);
			}
			if (storages != null) {
				data.putInt(storages.length);
				data.put(storages);
			} else {
				data.putInt(0);
			}
			if (barriers != null) {
				data.putInt(barriers.length);
				data.put(barriers);
			} else {
				data.putInt(0);
			}

			// Iterate over assigned peers and write lookup trees
			data.putInt(count);
			index = startIndex;
			currentPeer = m_assignedPeersIncludingBackups.get(index++);
			while (OverlayHelper.isPeerInSuperpeerRange(currentPeer, p_predecessor, p_nodeID)) {
				if (0 > Collections.binarySearch(p_peers, currentPeer)) {
					// #if LOGGER == TRACE
					m_logger.trace(getClass(), " Including LookupTree of " + NodeID.toHexString(currentPeer));
					// #endif /* LOGGER == TRACE */

					data.putShort(currentPeer);
					LookupTree.writeLookupTree(data, getLookupTreeLocal(currentPeer));
				}

				if (index == m_assignedPeersIncludingBackups.size()) {
					index = 0;
				}
				if (index == startIndex) {
					break;
				}
				currentPeer = m_assignedPeersIncludingBackups.get(index++);
			}
		} else {
			// There might be data in given area even without any assigned peer

			// Allocate array for all data and copy gathered data
			ret = new byte[size];
			data = ByteBuffer.wrap(ret);
			if (nameserviceEntries != null) {
				data.putInt(nameserviceEntries.length);
				data.put(nameserviceEntries);
			} else {
				data.putInt(0);
			}
			if (storages != null) {
				data.putInt(storages.length);
				data.put(storages);
			} else {
				data.putInt(0);
			}
			if (barriers != null) {
				data.putInt(barriers.length);
				data.put(barriers);
			} else {
				data.putInt(0);
			}
		}
		m_dataLock.readLock().unlock();

		// If there is no missing metadata, return an empty array
		if (ret.length == Integer.BYTES * 4) {
			ret = new byte[0];
		}

		return ret;
	}

	/**
	 * Deletes all metadata that is not in the responsible area.
	 * @param p_responsibleArea
	 *            the responsible area
	 * @return all unnecessary peers in assigned peer list
	 * @lock overlay lock must be read-locked
	 */
	public short[] deleteUnnecessaryBackups(final short[] p_responsibleArea) {
		short[] ret = null;
		short currentPeer;
		int index;
		int startIndex;
		int count = 0;

		m_dataLock.writeLock().lock();
		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Deleting all uneccessary metadata outside of area: "
				+ NodeID.toHexString(p_responsibleArea[0]) + ", " + NodeID.toHexString(p_responsibleArea[1]));
		// #endif /* LOGGER == TRACE */

		if (0 != m_assignedPeersIncludingBackups.size()) {
			ret = new short[m_assignedPeersIncludingBackups.size()];
			index = Collections.binarySearch(m_assignedPeersIncludingBackups, p_responsibleArea[1]);
			if (0 > index) {
				index = index * -1 - 1;
				if (index == m_assignedPeersIncludingBackups.size()) {
					index = 0;
				}
			}
			startIndex = index;
			currentPeer = m_assignedPeersIncludingBackups.get(index);
			while (!OverlayHelper.isPeerInSuperpeerRange(currentPeer, p_responsibleArea[0], p_responsibleArea[1])) {
				// Remove lookup tree
				// #if LOGGER == TRACE
				m_logger.trace(getClass(), " Removing LookupTree of " + NodeID.toHexString(currentPeer));
				// #endif /* LOGGER == TRACE */

				m_lookupTrees[currentPeer & 0xFFFF] = null;
				ret[count++] = currentPeer;

				if (++index == m_assignedPeersIncludingBackups.size()) {
					index = 0;
				}
				if (index == startIndex) {
					break;
				}
				currentPeer = m_assignedPeersIncludingBackups.get(index);
			}
			ret = Arrays.copyOf(ret, count);
		}
		// Remove nameservice entries
		m_nameservice.removeMetadataOutsideOfRange(p_responsibleArea[0], p_responsibleArea[1]);
		// Remove storages
		m_storage.removeMetadataOutsideOfRange(p_responsibleArea[0], p_responsibleArea[1]);
		// Remove barriers
		m_barriers.removeMetadataOutsideOfRange(p_responsibleArea[0], p_responsibleArea[1]);

		m_dataLock.writeLock().unlock();

		return ret;
	}

	/**
	 * Stores given metadata.
	 * @param p_metadata
	 *            the new metadata in an byte array
	 * @return new peers to assign
	 */
	public short[] storeMetadata(final byte[] p_metadata) {
		short nodeID;
		int size;
		int pos;
		short[] ret = null;
		ByteBuffer data;

		if (p_metadata != null && p_metadata.length != 0) {
			data = ByteBuffer.wrap(p_metadata);

			m_dataLock.writeLock().lock();
			// #if LOGGER == TRACE
			m_logger.trace(getClass(), "Storing metadata. Length: " + p_metadata.length);
			// #endif /* LOGGER == TRACE */

			// Put all nameservice entries
			size = data.getInt();
			pos = data.position();
			// #if LOGGER == TRACE
			m_logger.trace(getClass(), "Storing nameservice entries. Length: " + size);
			// #endif /* LOGGER == TRACE */
			m_nameservice.storeMetadata(p_metadata, pos, size);
			data.position(pos + size);

			// Put all storages
			size = data.getInt();
			pos = data.position();
			// #if LOGGER == TRACE
			m_logger.trace(getClass(), "Storing superpeer storages. Length: " + size);
			// #endif /* LOGGER == TRACE */
			m_storage.storeMetadata(p_metadata, pos, size);
			data.position(pos + size);

			// Put all barriers
			size = data.getInt();
			pos = data.position();
			// #if LOGGER == TRACE
			m_logger.trace(getClass(), "Storing barriers. Length: " + size);
			// #endif /* LOGGER == TRACE */
			m_barriers.storeMetadata(p_metadata, pos, size);
			data.position(pos + size);

			// Put all lookup trees
			size = data.getInt();
			ret = new short[size];
			// #if LOGGER == TRACE
			m_logger.trace(getClass(), "Storing lookup trees. Length: " + size);
			// #endif /* LOGGER == TRACE */
			for (int i = 0; i < size; i++) {
				nodeID = data.getShort();
				// #if LOGGER == TRACE
				m_logger.trace(getClass(), " Storing lookup tree of " + NodeID.toHexString((short) nodeID));
				// #endif /* LOGGER == TRACE */

				m_lookupTrees[nodeID & 0xFFFF] = LookupTree.readLookupTree(data);
				ret[i] = nodeID;
			}
			m_dataLock.writeLock().unlock();
		}

		return ret;
	}

	/* LookupTrees */
	/**
	 * Gets corresponding lookup tree.
	 * @param p_nodeID
	 *            lookup tree's creator
	 * @return the lookup tree
	 */
	public LookupTree getLookupTree(final short p_nodeID) {
		LookupTree ret;

		m_dataLock.readLock().lock();
		ret = m_lookupTrees[p_nodeID & 0xFFFF];
		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Gets corresponding lookup tree.
	 * @param p_nodeID
	 *            lookup tree's creator
	 * @return the lookup tree
	 */
	private LookupTree getLookupTreeLocal(final short p_nodeID) {
		return m_lookupTrees[p_nodeID & 0xFFFF];
	}

	/**
	 * Gets corresponding lookup range.
	 * @param p_chunkID
	 *            the ChunkID
	 * @return the lookup range
	 */
	public LookupRange getLookupRangeFromLookupTree(final long p_chunkID) {
		LookupRange ret = null;

		m_dataLock.readLock().lock();
		LookupTree tree = getLookupTreeLocal(ChunkID.getCreatorID(p_chunkID));
		if (null != tree) {
			ret = tree.getMetadata(p_chunkID);
		}
		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Puts a ChunkID.
	 * @param p_chunkID
	 *            the ChunkID
	 * @param p_owner
	 *            the NodeID of the new owner
	 * @return whether the ChunkID could be put or not
	 */
	public boolean putChunkIDInLookupTree(final long p_chunkID, final short p_owner) {
		boolean ret;
		LookupTree tree;

		m_dataLock.writeLock().lock();
		tree = getLookupTreeLocal(ChunkID.getCreatorID(p_chunkID));
		if (null == tree) {
			m_dataLock.writeLock().unlock();

			return false;
		} else {
			ret = tree.migrateObject(p_chunkID, p_owner);
			m_dataLock.writeLock().unlock();

			return ret;
		}
	}

	/**
	 * Puts a ChunkID.
	 * @param p_firstChunkID
	 *            the first ChunkID
	 * @param p_lastChunkID
	 *            the last ChunkID
	 * @param p_owner
	 *            the NodeID of the new owner
	 * @return whether the ChunkID could be put or not
	 */
	public boolean putChunkIDRangeInLookupTree(final long p_firstChunkID, final long p_lastChunkID,
			final short p_owner) {
		boolean ret;
		LookupTree tree;

		m_dataLock.writeLock().lock();
		tree = getLookupTreeLocal(ChunkID.getCreatorID(p_firstChunkID));
		if (null == tree) {
			m_dataLock.writeLock().unlock();

			return false;
		} else {
			ret = tree.migrateRange(p_firstChunkID, p_lastChunkID, p_owner);
			m_dataLock.writeLock().unlock();

			return ret;
		}
	}

	/**
	 * Removes a ChunkID.
	 * @param p_chunkID
	 *            the ChunkID
	 * @return whether the ChunkID could be removed or not
	 */
	public boolean removeChunkIDFromLookupTree(final long p_chunkID) {
		LookupTree tree;

		m_dataLock.writeLock().lock();
		tree = getLookupTreeLocal(ChunkID.getCreatorID(p_chunkID));
		if (null == tree) {
			m_dataLock.writeLock().unlock();

			return false;
		} else {
			tree.removeObject(p_chunkID);
			m_dataLock.writeLock().unlock();

			return true;
		}
	}

	/**
	 * Initializes a new backup range.
	 * @param p_creator
	 *            the creator of the backup range
	 * @param p_backupPeers
	 *            the assigned backup peers for the backup range
	 * @param p_startChunkIDOrRangeID
	 *            the first ChunkID of the range or the RangeID (for migrations)
	 * @return whether a new LookupTree was added or not
	 */
	public boolean initBackupRangeInLookupTree(final short p_creator, final short[] p_backupPeers,
			final long p_startChunkIDOrRangeID) {
		boolean ret = false;
		LookupTree tree;

		m_dataLock.writeLock().lock();
		tree = getLookupTreeLocal(p_creator);
		if (null == tree) {
			tree = new LookupTree(OverlayHelper.ORDER);
			m_lookupTrees[p_creator & 0xFFFF] = tree;
			ret = true;
		}
		if (ChunkID.getCreatorID(p_startChunkIDOrRangeID) != -1) {
			tree.initRange(p_startChunkIDOrRangeID, p_creator, p_backupPeers);
		} else {
			tree.initMigrationRange((int) p_startChunkIDOrRangeID, p_backupPeers);
		}
		m_dataLock.writeLock().unlock();

		return ret;
	}

	/**
	 * Gets corresponding lookup tree.
	 * @param p_nodeID
	 *            lookup tree's creator
	 * @return the lookup tree
	 */
	public ArrayList<long[]> getBackupRangesFromLookupTree(final short p_nodeID) {
		ArrayList<long[]> ret;
		LookupTree tree;

		m_dataLock.readLock().lock();
		tree = getLookupTreeLocal(p_nodeID);
		// no tree available -> no chunks were created
		if (tree == null) {
			ret = null;
		} else {
			ret = tree.getAllBackupRanges();
		}

		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Returns all backup ranges for given node
	 * @param p_nodeID
	 *            the NodeID
	 * @return all backup ranges
	 */
	public BackupRange[] getAllBackupRangesFromLookupTree(final short p_nodeID) {
		BackupRange[] ret = null;
		int counter = 0;
		LookupTree tree;
		ArrayList<long[]> ownBackupRanges;
		ArrayList<Long> migrationBackupRanges;

		m_dataLock.readLock().lock();
		tree = getLookupTreeLocal(p_nodeID);
		if (tree != null) {
			ownBackupRanges = tree.getAllBackupRanges();
			migrationBackupRanges = tree.getAllMigratedBackupRanges();

			ret = new BackupRange[ownBackupRanges.size() + migrationBackupRanges.size()];
			for (long[] backupRange : ownBackupRanges) {
				ret[counter++] = new BackupRange(backupRange[0], backupRange[1]);
			}
			counter = 0;
			for (long backupRange : migrationBackupRanges) {
				ret[counter + ownBackupRanges.size()] = new BackupRange(counter, backupRange);
				counter++;
			}
		}
		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Sets the restorer after a recovery.
	 * @param p_owner
	 *            the previous owner of the data
	 * @param p_source
	 *            the new owner
	 */
	public void setRestorerAfterRecoveryInLookupTree(final short p_owner, final short p_source) {
		LookupTree tree;

		m_dataLock.writeLock().lock();
		tree = getLookupTreeLocal(p_owner);
		if (tree != null) {
			tree.setRestorer(p_source);
		}
		m_dataLock.writeLock().unlock();
	}

	/**
	 * Removes given peer from all backup ranges as backup peer
	 * @param p_failedPeer
	 *            the failed peer
	 */
	public void removeFailedPeerFromBackupListsInLookupTrees(final short p_failedPeer) {
		LookupTree tree;
		Iterator<Short> iter;

		m_dataLock.writeLock().lock();
		// Remove failedPeer from all backup peer lists
		iter = m_assignedPeersIncludingBackups.iterator();
		while (iter.hasNext()) {
			getLookupTreeLocal(iter.next()).removeBackupPeer(p_failedPeer, (short) -1);
		}

		tree = getLookupTreeLocal(p_failedPeer);
		if (null != tree) {
			tree.setStatus(false);
		}
		m_dataLock.writeLock().unlock();
	}

	/* Nameservice */

	/**
	 * Gets nameservice entry.
	 * @param p_nameserviceID
	 *            the nameservice ID
	 * @return the ChunkID
	 */
	public long getNameserviceEntry(final int p_nameserviceID) {
		long ret;

		m_dataLock.readLock().lock();
		ret = m_nameservice.get(p_nameserviceID);
		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Gets all nameservice entries of this superpeer.
	 * @return all nameservice entries in a byte array
	 */
	public byte[] getAllNameserviceEntries() {
		byte[] ret;

		m_dataLock.readLock().lock();
		ret = m_nameservice.receiveAllMetadata();
		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Puts a single nameservice entry.
	 * @param p_nameserviceID
	 *            the nameservice ID
	 * @param p_chunkID
	 *            the ChunkID
	 */
	public void putNameserviceEntry(final int p_nameserviceID, final long p_chunkID) {
		m_dataLock.writeLock().lock();
		m_nameservice.put(p_nameserviceID, p_chunkID);
		m_dataLock.writeLock().unlock();
	}

	/**
	 * Counts nameservice entries within range.
	 * @param p_bound1
	 *            lowest NodeID
	 * @param p_bound2
	 *            highest NodeID (might be smaller than p_bound1)
	 * @return the number of nameservice entries in given NodeID range
	 */
	public int countNameserviceEntries(final short p_bound1, final short p_bound2) {
		int ret;

		m_dataLock.readLock().lock();
		ret = m_nameservice.quantifyMetadata(p_bound1, p_bound2);
		m_dataLock.readLock().unlock();

		return ret;
	}

	/* Superpeer Storage */
	/**
	 * Creates a metadata storage.
	 * @param p_storageID
	 *            the storage ID
	 * @param p_size
	 *            the size of metadata storage
	 * @return 0 on success, -1 if quota reached, -2 if max num entries reached, -3 if id already in use
	 */
	public int createStorage(final int p_storageID, final int p_size) {
		int ret;

		m_dataLock.writeLock().lock();
		ret = m_storage.create(p_storageID, p_size);
		m_dataLock.writeLock().unlock();

		return ret;
	}

	/**
	 * Gets a metadata storage.
	 * @param p_storageID
	 *            the storage ID
	 * @return the data
	 */
	public byte[] getStorage(final int p_storageID) {
		byte[] ret;

		m_dataLock.readLock().lock();
		ret = m_storage.get(p_storageID);
		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Gets status of whole metadata storage.
	 * @return the status
	 */
	public SuperpeerStorage.Status getStorageStatus() {
		SuperpeerStorage.Status ret;

		m_dataLock.readLock().lock();
		ret = m_storage.getStatus();
		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Puts data in metadata storage.
	 * @param p_storageID
	 *            the storage ID
	 * @param p_data
	 *            the data
	 * @return number of bytes written to the block or -1 if the block does not exist
	 */
	public int putStorage(final int p_storageID, final byte[] p_data) {
		int ret;

		m_dataLock.readLock().lock();
		ret = m_storage.put(p_storageID, p_data);
		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Removes metadata storage.
	 * @param p_storageID
	 *            the storage ID
	 * @return false if the block does not exist, true on success
	 */
	public boolean removeStorage(final int p_storageID) {
		boolean ret;

		m_dataLock.writeLock().lock();
		ret = m_storage.remove(p_storageID);
		m_dataLock.writeLock().unlock();

		return ret;
	}

	/* Barriers */
	/**
	 * Creates a barrier.
	 * @param p_nodeID
	 *            the creator
	 * @param p_size
	 *            the number of peers to sign on
	 * @return barrier ID on success, -1 on failure
	 */
	public int createBarrier(final short p_nodeID, final int p_size) {
		int ret;

		m_dataLock.writeLock().lock();
		ret = m_barriers.allocateBarrier(p_nodeID, p_size);
		m_dataLock.writeLock().unlock();

		return ret;
	}

	/**
	 * Changes size of a barrier.
	 * @param p_nodeID
	 *            the creator
	 * @param p_barrierID
	 *            the barrier ID
	 * @param p_newSize
	 *            the new size of the barrier
	 * @return true if changing size was successful, false otherwise
	 */
	public boolean changeSizeOfBarrier(final short p_nodeID, final int p_barrierID, final int p_newSize) {
		boolean ret;

		m_dataLock.writeLock().lock();
		ret = m_barriers.changeBarrierSize(p_nodeID, p_barrierID, p_newSize);
		m_dataLock.writeLock().unlock();

		return ret;
	}

	/**
	 * Removes a barrier.
	 * @param p_nodeID
	 *            the creator
	 * @param p_barrierID
	 *            the barrier ID
	 * @return true if successful, false on failure
	 */
	public boolean removeBarrier(final short p_nodeID, final int p_barrierID) {
		boolean ret;

		m_dataLock.writeLock().lock();
		ret = m_barriers.freeBarrier(p_nodeID, p_barrierID);
		m_dataLock.writeLock().unlock();

		return ret;
	}

	/**
	 * Resets a barrier.
	 * @param p_nodeID
	 *            the creator
	 * @param p_barrierID
	 *            the barrier ID
	 * @return true if successful, false otherwise
	 */
	public boolean resetBarrier(final short p_nodeID, final int p_barrierID) {
		boolean ret;

		m_dataLock.writeLock().lock();
		ret = m_barriers.reset(p_nodeID, p_barrierID);
		m_dataLock.writeLock().unlock();

		return ret;
	}

	/**
	 * Signs-on on barrier.
	 * @param p_nodeID
	 *            the creator
	 * @param p_barrierID
	 *            the barrier ID
	 * @param p_nodeIDToSignOn
	 *            the NodeID
	 * @param p_barrierData
	 *            the barrier data
	 * @return the number of peers left to sign on, -1 on failure
	 */
	public int signOnBarrier(final short p_nodeID, final int p_barrierID, final short p_nodeIDToSignOn,
			final long p_barrierData) {
		int ret;

		m_dataLock.writeLock().lock();
		ret = m_barriers.signOn(p_nodeID, p_barrierID, p_nodeIDToSignOn, p_barrierData);
		m_dataLock.writeLock().unlock();

		return ret;
	}

	/**
	 * Returns the number of peers signed-on on this barrier.
	 * @param p_nodeID
	 *            the creator
	 * @param p_barrierID
	 *            the barrier ID
	 * @return array with NodeIDs that already signed on. First index element is the count of signed on peers
	 */
	public short[] getSignedOnPeersOfBarrier(final short p_nodeID, final int p_barrierID) {
		short[] ret;

		m_dataLock.readLock().lock();
		ret = m_barriers.getSignedOnPeers(p_nodeID, p_barrierID);
		m_dataLock.readLock().unlock();

		return ret;
	}

	/**
	 * Returns the custom data of all signed-on peers.
	 * @param p_nodeID
	 *            the creator
	 * @param p_barrierID
	 *            the barrier ID
	 * @return on success an array with the currently available custom data (sorted by order the peers logged in)
	 */
	public long[] getCustomDataOfBarrier(final short p_nodeID, final int p_barrierID) {
		long[] ret;

		m_dataLock.readLock().lock();
		ret = m_barriers.getBarrierCustomData(p_nodeID, p_barrierID);
		m_dataLock.readLock().unlock();

		return ret;
	}

}