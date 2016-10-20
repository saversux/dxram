
package de.hhu.bsinfo.dxram.lookup.overlay;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import de.hhu.bsinfo.dxram.backup.BackupRange;
import de.hhu.bsinfo.dxram.boot.AbstractBootComponent;
import de.hhu.bsinfo.dxram.data.Chunk;
import de.hhu.bsinfo.dxram.data.ChunkID;
import de.hhu.bsinfo.dxram.event.EventComponent;
import de.hhu.bsinfo.dxram.failure.messages.FailureRequest;
import de.hhu.bsinfo.dxram.logger.LoggerComponent;
import de.hhu.bsinfo.dxram.lookup.LookupComponent;
import de.hhu.bsinfo.dxram.lookup.LookupRange;
import de.hhu.bsinfo.dxram.lookup.LookupRangeWithBackupPeers;
import de.hhu.bsinfo.dxram.lookup.messages.AskAboutBackupsRequest;
import de.hhu.bsinfo.dxram.lookup.messages.AskAboutBackupsResponse;
import de.hhu.bsinfo.dxram.lookup.messages.AskAboutSuccessorRequest;
import de.hhu.bsinfo.dxram.lookup.messages.AskAboutSuccessorResponse;
import de.hhu.bsinfo.dxram.lookup.messages.BarrierAllocRequest;
import de.hhu.bsinfo.dxram.lookup.messages.BarrierAllocResponse;
import de.hhu.bsinfo.dxram.lookup.messages.BarrierChangeSizeRequest;
import de.hhu.bsinfo.dxram.lookup.messages.BarrierChangeSizeResponse;
import de.hhu.bsinfo.dxram.lookup.messages.BarrierFreeRequest;
import de.hhu.bsinfo.dxram.lookup.messages.BarrierFreeResponse;
import de.hhu.bsinfo.dxram.lookup.messages.BarrierGetStatusRequest;
import de.hhu.bsinfo.dxram.lookup.messages.BarrierGetStatusResponse;
import de.hhu.bsinfo.dxram.lookup.messages.BarrierReleaseMessage;
import de.hhu.bsinfo.dxram.lookup.messages.BarrierSignOnRequest;
import de.hhu.bsinfo.dxram.lookup.messages.BarrierSignOnResponse;
import de.hhu.bsinfo.dxram.lookup.messages.GetAllBackupRangesRequest;
import de.hhu.bsinfo.dxram.lookup.messages.GetAllBackupRangesResponse;
import de.hhu.bsinfo.dxram.lookup.messages.GetChunkIDForNameserviceEntryRequest;
import de.hhu.bsinfo.dxram.lookup.messages.GetChunkIDForNameserviceEntryResponse;
import de.hhu.bsinfo.dxram.lookup.messages.GetLookupRangeRequest;
import de.hhu.bsinfo.dxram.lookup.messages.GetLookupRangeResponse;
import de.hhu.bsinfo.dxram.lookup.messages.GetMetadataSummaryRequest;
import de.hhu.bsinfo.dxram.lookup.messages.GetMetadataSummaryResponse;
import de.hhu.bsinfo.dxram.lookup.messages.GetNameserviceEntriesRequest;
import de.hhu.bsinfo.dxram.lookup.messages.GetNameserviceEntriesResponse;
import de.hhu.bsinfo.dxram.lookup.messages.GetNameserviceEntryCountRequest;
import de.hhu.bsinfo.dxram.lookup.messages.GetNameserviceEntryCountResponse;
import de.hhu.bsinfo.dxram.lookup.messages.InitRangeRequest;
import de.hhu.bsinfo.dxram.lookup.messages.InitRangeResponse;
import de.hhu.bsinfo.dxram.lookup.messages.InsertNameserviceEntriesRequest;
import de.hhu.bsinfo.dxram.lookup.messages.InsertNameserviceEntriesResponse;
import de.hhu.bsinfo.dxram.lookup.messages.JoinRequest;
import de.hhu.bsinfo.dxram.lookup.messages.JoinResponse;
import de.hhu.bsinfo.dxram.lookup.messages.LookupMessages;
import de.hhu.bsinfo.dxram.lookup.messages.MigrateRangeRequest;
import de.hhu.bsinfo.dxram.lookup.messages.MigrateRangeResponse;
import de.hhu.bsinfo.dxram.lookup.messages.MigrateRequest;
import de.hhu.bsinfo.dxram.lookup.messages.MigrateResponse;
import de.hhu.bsinfo.dxram.lookup.messages.NameserviceUpdatePeerCachesMessage;
import de.hhu.bsinfo.dxram.lookup.messages.NotifyAboutFailedPeerRequest;
import de.hhu.bsinfo.dxram.lookup.messages.NotifyAboutFailedPeerResponse;
import de.hhu.bsinfo.dxram.lookup.messages.NotifyAboutNewPredecessorMessage;
import de.hhu.bsinfo.dxram.lookup.messages.NotifyAboutNewSuccessorMessage;
import de.hhu.bsinfo.dxram.lookup.messages.PingSuperpeerMessage;
import de.hhu.bsinfo.dxram.lookup.messages.RemoveChunkIDsRequest;
import de.hhu.bsinfo.dxram.lookup.messages.RemoveChunkIDsResponse;
import de.hhu.bsinfo.dxram.lookup.messages.SendBackupsMessage;
import de.hhu.bsinfo.dxram.lookup.messages.SendSuperpeersMessage;
import de.hhu.bsinfo.dxram.lookup.messages.SetRestorerAfterRecoveryMessage;
import de.hhu.bsinfo.dxram.lookup.messages.StartRecoveryMessage;
import de.hhu.bsinfo.dxram.lookup.messages.SuperpeerStorageCreateRequest;
import de.hhu.bsinfo.dxram.lookup.messages.SuperpeerStorageCreateResponse;
import de.hhu.bsinfo.dxram.lookup.messages.SuperpeerStorageGetRequest;
import de.hhu.bsinfo.dxram.lookup.messages.SuperpeerStorageGetResponse;
import de.hhu.bsinfo.dxram.lookup.messages.SuperpeerStoragePutRequest;
import de.hhu.bsinfo.dxram.lookup.messages.SuperpeerStoragePutResponse;
import de.hhu.bsinfo.dxram.lookup.messages.SuperpeerStorageRemoveMessage;
import de.hhu.bsinfo.dxram.lookup.messages.SuperpeerStorageStatusRequest;
import de.hhu.bsinfo.dxram.lookup.messages.SuperpeerStorageStatusResponse;
import de.hhu.bsinfo.dxram.lookup.overlay.storage.BarrierID;
import de.hhu.bsinfo.dxram.lookup.overlay.storage.BarriersTable;
import de.hhu.bsinfo.dxram.lookup.overlay.storage.LookupTree;
import de.hhu.bsinfo.dxram.lookup.overlay.storage.MetadataHandler;
import de.hhu.bsinfo.dxram.lookup.overlay.storage.NameserviceHashTable;
import de.hhu.bsinfo.dxram.lookup.overlay.storage.SuperpeerStorage;
import de.hhu.bsinfo.dxram.net.NetworkComponent;
import de.hhu.bsinfo.dxram.net.NetworkErrorCodes;
import de.hhu.bsinfo.dxram.net.messages.DXRAMMessageTypes;
import de.hhu.bsinfo.dxram.recovery.messages.RecoverBackupRangeRequest;
import de.hhu.bsinfo.dxram.recovery.messages.RecoverBackupRangeResponse;
import de.hhu.bsinfo.dxram.util.NodeRole;
import de.hhu.bsinfo.ethnet.AbstractMessage;
import de.hhu.bsinfo.ethnet.NetworkHandler.MessageReceiver;
import de.hhu.bsinfo.ethnet.NodeID;
import de.hhu.bsinfo.utils.CRC16;

/**
 * Superpeer functionality for overlay
 * @author Kevin Beineke <kevin.beineke@hhu.de> 30.03.16
 */
public class OverlaySuperpeer implements MessageReceiver {

	// Attributes
	private NetworkComponent m_network;
	private AbstractBootComponent m_boot;
	private LoggerComponent m_logger;

	private short m_nodeID = -1;
	private short m_predecessor = -1;
	private short m_successor = -1;
	private int m_initialNumberOfSuperpeers;

	// All known superpeers: New superpeers are added when there is a new predecessor or successor
	// and during fix fingers method (fixSuperpeers) in stabilization thread
	private ArrayList<Short> m_superpeers;
	// All assigned peers (between this superpeer and his predecessor): New peers are added when
	// a peer joins the overlay and this superpeer is responsible and when another superpeer left
	// the overlay and this superpeer takes over
	private ArrayList<Short> m_peers;
	// All assigned peers including the backups (between this superpeer and his fourth predecessor):
	// New peers are added when a peer initializes a backup range for the first time on this superpeer
	// (also backups) and when applying backups from another superpeer and the peer is not yet in the
	// list
	private ArrayList<Short> m_assignedPeersIncludingBackups;

	private MetadataHandler m_metadata;

	private SuperpeerStabilizationThread m_stabilizationThread;

	private CRC16 m_hashGenerator = new CRC16();

	private ReentrantReadWriteLock m_overlayLock;

	/**
	 * Creates an instance of OverlaySuperpeer
	 * @param p_nodeID
	 *            the own NodeID
	 * @param p_contactSuperpeer
	 *            the superpeer to contact for joining
	 * @param p_initialNumberOfSuperpeers
	 *            the number of expeced superpeers
	 * @param p_sleepInterval
	 *            the ping interval
	 * @param p_maxNumOfBarriers
	 *            Max number of barriers
	 * @param p_storageMaxNumEntries
	 *            Max number of entries for the superpeer storage (-1 to disable)
	 * @param p_storageMaxSizeBytes
	 *            Max size for the superpeer storage in bytes
	 * @param p_boot
	 *            the BootComponent
	 * @param p_logger
	 *            the LoggerComponent
	 * @param p_network
	 *            the NetworkComponent
	 * @param p_event
	 *            the EventComponent
	 */
	public OverlaySuperpeer(final short p_nodeID, final short p_contactSuperpeer, final int p_initialNumberOfSuperpeers,
			final int p_sleepInterval, final int p_maxNumOfBarriers, final int p_storageMaxNumEntries,
			final int p_storageMaxSizeBytes, final AbstractBootComponent p_boot, final LoggerComponent p_logger,
			final NetworkComponent p_network, final EventComponent p_event) {
		m_boot = p_boot;
		m_logger = p_logger;
		m_network = p_network;

		m_nodeID = p_nodeID;
		m_initialNumberOfSuperpeers = p_initialNumberOfSuperpeers;

		m_superpeers = new ArrayList<>();
		m_peers = new ArrayList<>();
		m_assignedPeersIncludingBackups = new ArrayList<>();

		m_metadata = new MetadataHandler(new LookupTree[NodeID.MAX_ID],
				new NameserviceHashTable(1000, 0.9f, m_logger, m_hashGenerator),
				new SuperpeerStorage(p_storageMaxNumEntries, p_storageMaxSizeBytes, m_hashGenerator, m_logger),
				new BarriersTable(p_maxNumOfBarriers, m_nodeID, m_logger), m_assignedPeersIncludingBackups, m_logger);

		m_overlayLock = new ReentrantReadWriteLock(false);

		m_initialNumberOfSuperpeers--;

		registerNetworkMessages();
		registerNetworkMessageListener();

		createOrJoinSuperpeerOverlay(p_contactSuperpeer, p_sleepInterval);
	}

	/**
	 * Shuts down the stabilization thread
	 */
	public void shutdown() {
		m_stabilizationThread.interrupt();
		m_stabilizationThread.shutdown();
		try {
			m_stabilizationThread.join();
			// #if LOGGER >= INFO
			m_logger.info(getClass(), "Shutdown of StabilizationThread successful.");
			// #endif /* LOGGER >= INFO */
		} catch (final InterruptedException e) {
			// #if LOGGER >= WARN
			m_logger.warn(getClass(), "Could not wait for stabilization thread to finish. Interrupted.");
			// #endif /* LOGGER >= WARN */
		}
	}

	/**
	 * Returns whether this superpeer is last in overlay or not
	 * @return whether this superpeer is last in overlay or not
	 */
	public boolean isLastSuperpeer() {
		boolean ret = true;
		short superpeer;
		int i = 0;

		m_overlayLock.readLock().lock();
		if (!m_superpeers.isEmpty()) {
			while (i < m_superpeers.size()) {
				superpeer = m_superpeers.get(i++);
				if (m_network.sendMessage(new PingSuperpeerMessage(superpeer)) != NetworkErrorCodes.SUCCESS) {
					continue;
				}

				ret = false;
				break;
			}
		}
		m_overlayLock.readLock().unlock();

		return ret;
	}

	/**
	 * Returns the corresponding lookup tree
	 * @param p_nodeID
	 *            the NodeID
	 * @return the lookup tree
	 */
	public LookupTree getLookupTree(final short p_nodeID) {
		return m_metadata.getLookupTree(p_nodeID);
	}

	/**
	 * Returns current predecessor
	 * @return the predecessor
	 * @lock overlay lock must be write-locked
	 */
	protected short getPredecessor() {
		return m_predecessor;
	}

	/**
	 * Sets the predecessor for the current superpeer
	 * @param p_nodeID
	 *            NodeID of the predecessor
	 * @lock overlay lock must be write-locked
	 */
	protected void setPredecessor(final short p_nodeID) {
		m_predecessor = p_nodeID;
		if (m_predecessor != m_successor) {
			OverlayHelper.insertSuperpeer(m_predecessor, m_superpeers);
		}
	}

	/**
	 * Returns current successor
	 * @return the sucessor
	 * @lock overlay lock must be write-locked
	 */
	protected short getSuccessor() {
		return m_successor;
	}

	/**
	 * Sets the successor for the current superpeer
	 * @param p_nodeID
	 *            NodeID of the successor
	 * @lock overlay lock must be write-locked
	 */
	protected void setSuccessor(final short p_nodeID) {
		m_successor = p_nodeID;
		if (-1 != m_successor && m_nodeID != m_successor) {
			OverlayHelper.insertSuperpeer(m_successor, m_superpeers);
		}
	}

	/**
	 * Returns all peers
	 * @return all peers
	 * @lock overlay lock must be write-locked
	 */
	protected ArrayList<Short> getPeers() {
		return m_peers;
	}

	/**
	 * Determines all peers that are in the responsible area
	 * @param p_firstSuperpeer
	 *            the first superpeer
	 * @param p_lastSuperpeer
	 *            the last superpeer
	 * @return all peers in responsible area
	 * @lock overlay lock must be read-locked
	 */
	ArrayList<Short> getPeersInResponsibleArea(final short p_firstSuperpeer, final short p_lastSuperpeer) {
		short currentPeer;
		int index;
		int startIndex;
		ArrayList<Short> peers;

		peers = new ArrayList<Short>();
		if (0 != m_assignedPeersIncludingBackups.size()) {
			// Search for the first superpeer in list of all assigned peers
			index = Collections.binarySearch(m_assignedPeersIncludingBackups, p_firstSuperpeer);
			// Result must be negative because there is no peer with NodeID of a superpeer
			// Get the index where the superpeer would be in the list to get first peer with higher NodeID
			index = index * -1 - 1;
			if (index == m_assignedPeersIncludingBackups.size()) {
				// There is no peer with higher NodeID -> take the one with lowest NodeID
				index = 0;
			}

			startIndex = index;
			currentPeer = m_assignedPeersIncludingBackups.get(index++);
			while (OverlayHelper.isPeerInSuperpeerRange(currentPeer, p_firstSuperpeer, p_lastSuperpeer)) {
				// Add current peer to peer list
				peers.add(Collections.binarySearch(peers, currentPeer) * -1 - 1, currentPeer);
				if (index == m_assignedPeersIncludingBackups.size()) {
					index = 0;
				}
				if (index == startIndex) {
					break;
				}
				currentPeer = m_assignedPeersIncludingBackups.get(index++);
			}
		}

		return peers;
	}

	/**
	 * Adds given NodeID to the list of assigned peers
	 * @param p_nodeID
	 *            the NodeID
	 * @lock overlay lock must be write-locked
	 */
	private void addToAssignedPeers(final short p_nodeID) {
		int index;
		index = Collections.binarySearch(m_assignedPeersIncludingBackups, m_nodeID);
		if (0 > index) {
			index = index * -1 - 1;
			m_assignedPeersIncludingBackups.add(index, p_nodeID);
		}
	}

	/**
	 * Removes given NodeID from the list of assigned peers
	 * @param p_nodeID
	 *            the NodeID
	 * @lock overlay lock must be write-locked
	 */
	private void removeFromAssignedPeers(final short p_nodeID) {
		m_assignedPeersIncludingBackups.remove(new Short(p_nodeID));
	}

	/**
	 * Returns the number of nameservice entries in given area
	 * @param p_responsibleArea
	 *            the area
	 * @return the number of nameservice entries
	 */
	int getNumberOfNameserviceEntries(final short[] p_responsibleArea) {
		return m_metadata.getNumberOfNameserviceEntries(p_responsibleArea);
	}

	/**
	 * Returns the number of storages in given area
	 * @param p_responsibleArea
	 *            the area
	 * @return the number of storages
	 */
	int getNumberOfStorages(final short[] p_responsibleArea) {
		return m_metadata.getNumberOfStorages(p_responsibleArea);
	}

	/**
	 * Returns the number of barriers in given area
	 * @param p_responsibleArea
	 *            the area
	 * @return the number of barriers
	 */
	int getNumberOfBarriers(final short[] p_responsibleArea) {
		return m_metadata.getNumberOfBarriers(p_responsibleArea);
	}

	/**
	 * Compares given peer list with local list and returns all missing backup data
	 * @param p_peers
	 *            all peers the requesting superpeer stores backups for
	 * @param p_numberOfNameserviceEntries
	 *            the number of expected nameservice entries
	 * @param p_numberOfStorages
	 *            the number of expected storages
	 * @param p_numberOfBarriers
	 *            the number of expected barriers
	 * @return the backup data of missing peers in given peer list
	 * @lock overlay lock must be write-locked
	 */
	byte[] compareAndReturnBackups(final ArrayList<Short> p_peers, final int p_numberOfNameserviceEntries,
			final int p_numberOfStorages, final int p_numberOfBarriers) {
		return m_metadata.compareAndReturnBackups(p_peers, p_numberOfNameserviceEntries, p_numberOfStorages,
				p_numberOfBarriers, m_predecessor, m_nodeID);
	}

	/**
	 * Stores given backups
	 * @param p_missingMetadata
	 *            the new metadata in a byte array
	 * @lock overlay lock must be write-locked
	 */
	void storeIncomingBackups(final byte[] p_missingMetadata) {
		short[] newPeers;

		newPeers = m_metadata.storeMetadata(p_missingMetadata);
		if (newPeers != null) {
			for (short peer : newPeers) {
				addToAssignedPeers(peer);
			}
		}
	}

	/**
	 * Deletes all metadata of peers and superpeers that are not in the responsible area
	 * @param p_responsibleArea
	 *            the responsible area
	 * @lock overlay lock must be write-locked
	 */
	void deleteUnnecessaryBackups(final short[] p_responsibleArea) {
		short[] peersToRemove;

		peersToRemove = m_metadata.deleteUnnecessaryBackups(p_responsibleArea);
		if (peersToRemove != null) {
			for (short peer : peersToRemove) {
				removeFromAssignedPeers(peer);
			}
		}
	}

	/**
	 * Takes over failed superpeers peers
	 * @param p_nodeID
	 *            the NodeID
	 * @lock overlay lock must be write-locked
	 */
	void takeOverPeers(final short p_nodeID) {
		short predecessor;
		short firstPeer;
		short currentPeer;
		int index;
		int startIndex;

		if (m_superpeers.isEmpty()) {
			firstPeer = (short) (m_nodeID + 1);
		} else {
			index = Collections.binarySearch(m_superpeers, p_nodeID);
			if (0 > index) {
				index = index * -1 - 1;
			}
			if (0 == index) {
				predecessor = m_superpeers.get(m_superpeers.size() - 1);
			} else {
				predecessor = m_superpeers.get(index - 1);
			}
			if (predecessor == p_nodeID) {
				firstPeer = (short) (m_nodeID + 1);
			} else {
				firstPeer = predecessor;
			}
		}

		if (0 != m_assignedPeersIncludingBackups.size()) {
			index = Collections.binarySearch(m_assignedPeersIncludingBackups, firstPeer);
			if (0 > index) {
				index = index * -1 - 1;
				if (index == m_assignedPeersIncludingBackups.size()) {
					index = 0;
				}
			}
			startIndex = index;
			currentPeer = m_assignedPeersIncludingBackups.get(index++);
			while (OverlayHelper.isPeerInSuperpeerRange(currentPeer, firstPeer, p_nodeID)) {
				if (m_metadata.getLookupTree(currentPeer).getStatus()) {
					if (0 > Collections.binarySearch(m_peers, currentPeer)
							&& 0 > Collections.binarySearch(m_superpeers, currentPeer)) {
						// #if LOGGER >= INFO
						m_logger.info(getClass(), "** Taking over " + NodeID.toHexString(currentPeer));
						// #endif /* LOGGER >= INFO */
						OverlayHelper.insertPeer(currentPeer, m_peers);
						addToAssignedPeers(currentPeer);
					}
				}
				if (index == m_assignedPeersIncludingBackups.size()) {
					index = 0;
				}
				if (index == startIndex) {
					break;
				}
				currentPeer = m_assignedPeersIncludingBackups.get(index++);
			}
		}
	}

	/**
	 * Determines if this superpeer is responsible for failure handling
	 * @param p_failedNode
	 *            NodeID of failed node
	 * @return true if superpeer is responsible for failed node, false otherwise
	 */
	public boolean isResponsibleForFailureHandling(final short p_failedNode) {
		boolean ret = false;

		m_overlayLock.readLock().lock();

		if (0 <= Collections.binarySearch(m_superpeers, p_failedNode)) {
			ret = true;
		} else if (0 <= Collections.binarySearch(m_peers, p_failedNode)) {
			ret = true;
		}

		m_overlayLock.readLock().unlock();

		return ret;
	}

	/**
	 * Handles a node failure for the superpeer overlay: Repairs the overlay, recovers meta-data, spreads meta-data, ...
	 * @param p_failedNode
	 *            the failed node's NodeID
	 * @param p_role
	 *            the failed node's role
	 */
	public void failureHandling(final short p_failedNode, final NodeRole p_role) {
		short[] responsibleArea;
		short[] backupSuperpeers;
		int i = 0;
		int counter;
		long firstChunkIDOrRangeID;
		boolean existsInZooKeeper = false;
		BackupRange[] backupRanges;
		short[] backupPeers;

		boolean finished = false;

		m_overlayLock.writeLock().lock();

		if (p_role == NodeRole.SUPERPEER) {
			if (OverlayHelper.containsSuperpeer(p_failedNode, m_superpeers)) {
				// Inform all other superpeers actively and take over peers if failed superpeer was the predecessor
				if (p_failedNode == m_predecessor) {
					// #if LOGGER >= DEBUG
					m_logger.debug(getClass(), "Failed node " + NodeID.toHexString(p_failedNode)
							+ " was my predecessor -> informing all other superpeers and taking over all peers");
					// #endif /* LOGGER >= INFO */

					// Inform all superpeers
					for (short superpeer : m_superpeers) {
						if (superpeer != p_failedNode) {
							FailureRequest request = new FailureRequest(superpeer, p_failedNode);
							if (m_network.sendSync(request) != NetworkErrorCodes.SUCCESS) {
								// Ignore, failure is detected by network module
							}
						}
					}

					// Take over failed superpeer's peers
					takeOverPeers(m_predecessor);
				}

				// Send failed superpeer's metadata to this superpeers successor if it is the last backup superpeer
				// of the failed superpeer
				responsibleArea = OverlayHelper.getResponsibleArea(m_nodeID, m_predecessor, m_superpeers);
				if (m_superpeers.size() > 3
						&& OverlayHelper.getResponsibleSuperpeer((short) (responsibleArea[0] + 1), m_superpeers,
								m_logger) == p_failedNode) {
					// #if LOGGER >= DEBUG
					m_logger.debug(getClass(), "Failed node " + NodeID.toHexString(p_failedNode)
							+ " was in my responsible area -> spreading his data");
					// #endif /* LOGGER >= DEBUG */
					spreadDataOfFailedSuperpeer(p_failedNode, responsibleArea);
				}

				// Send this superpeer's metadata to new backup superpeer if failed superpeer was one of its backup
				// superpeers
				backupSuperpeers = OverlayHelper.getBackupSuperpeers(m_nodeID, m_superpeers);
				if (m_superpeers.size() > 3
						&& OverlayHelper.isSuperpeerInRange(p_failedNode, backupSuperpeers[0], backupSuperpeers[2])) {
					// #if LOGGER >= DEBUG
					m_logger.debug(getClass(), "Failed node " + NodeID.toHexString(p_failedNode)
							+ " was one of my backup nodes -> spreading my data");
					// #endif /* LOGGER >= DEBUG */
					spreadBackupsOfThisSuperpeer(backupSuperpeers[2]);
				}

				// Remove superpeer
				final int index = OverlayHelper.removeSuperpeer(p_failedNode, m_superpeers);
				if (index >= 0) {
					// Set new predecessor/successor if failed superpeer was pre-/succeeding
					if (p_failedNode == m_successor) {
						if (m_superpeers.size() != 0) {
							if (index < m_superpeers.size()) {
								m_successor = m_superpeers.get(index);
							} else {
								m_successor = m_superpeers.get(0);
							}
						} else {
							m_successor = (short) -1;
						}
					}

					if (p_failedNode == m_predecessor) {
						if (m_superpeers.size() != 0) {
							if (index > 0) {
								m_predecessor = m_superpeers.get(index - 1);
							} else {
								m_predecessor = m_superpeers.get(m_superpeers.size() - 1);
							}
						} else {
							m_predecessor = (short) -1;
						}
					}
				}
				// #if LOGGER >= DEBUG
				m_logger.debug(getClass(), "Removed failed node " + NodeID.toHexString(p_failedNode));
				// #endif /* LOGGER >= DEBUG */
			} else {
				// Failed superpeer was already removed because failure was detected more than once
			}
		} else if (p_role == NodeRole.PEER) {
			// Only the responsible superpeer executes the following

			// Inform all other superpeers about failed peer
			for (short superpeer : m_superpeers) {
				// #if LOGGER >= DEBUG
				m_logger.debug(getClass(), "Informing " + NodeID.toHexString(superpeer)
						+ " to remove " + NodeID.toHexString(p_failedNode) + " from meta-data");
				// #endif /* LOGGER >= DEBUG */

				NotifyAboutFailedPeerRequest request = new NotifyAboutFailedPeerRequest(superpeer, p_failedNode);
				if (m_network.sendSync(request) != NetworkErrorCodes.SUCCESS) {
					// Ignore, failure is detected by network module
				}
				request.getResponse(RecoverBackupRangeResponse.class);
			}

			// Start recovery
			// #if LOGGER >= INFO
			m_logger.info(getClass(), "Starting recovery for failed node " + NodeID.toHexString(p_failedNode));
			// #endif /* LOGGER >= INFO */
			RecoverBackupRangeRequest request;
			RecoverBackupRangeResponse response;
			while (!finished) {
				finished = true;

				backupRanges = m_metadata.getAllBackupRangesFromLookupTree(p_failedNode);
				if (backupRanges != null) {
					for (BackupRange backupRange : backupRanges) {
						counter = 0;
						backupPeers = backupRange.getBackupPeers();
						firstChunkIDOrRangeID = backupRange.getRangeID();

						for (i = 0; i < backupPeers.length; i++) {
							if (backupPeers[i] != 0) {
								// #if LOGGER >= INFO
								m_logger.info(getClass(),
										"Initiating recovery of range " + ChunkID.toHexString(firstChunkIDOrRangeID)
										+ " on peer " + backupPeers[i]);
								// #endif /* LOGGER >= INFO */

								request = new RecoverBackupRangeRequest(backupPeers[i], p_failedNode,
										firstChunkIDOrRangeID);
								if (m_network.sendSync(request) == NetworkErrorCodes.SUCCESS) {
									response = request.getResponse(RecoverBackupRangeResponse.class);
									if (response.getNumberOfRecoveredChunks() > 0) {
										counter += response.getNumberOfRecoveredChunks();
										break;
									}
								}
							}

							// Backup peer could not recover backup range
							if (i == backupPeers.length - 1) {
								// #if LOGGER >= ERROR
								m_logger.info(getClass(), "Range " + ChunkID.toHexString(firstChunkIDOrRangeID)
										+ " could not be recovered!");
								// #endif /* LOGGER >= ERROR */
							}
						}

						// #if LOGGER >= INFO
						m_logger.info(getClass(), "Recovered " + counter + " chunks of range "
								+ ChunkID.toHexString(firstChunkIDOrRangeID));
						// #endif /* LOGGER >= INFO */
					}
				}
			}

			// Inform all peers
			for (short peer : m_peers) {
				if (peer != p_failedNode) {
					FailureRequest failureRequest = new FailureRequest(peer, p_failedNode);
					if (m_network.sendSync(failureRequest) != NetworkErrorCodes.SUCCESS) {
						// Ignore, failure is detected by network module
					}
				}
			}

			// Remove peer
			OverlayHelper.removePeer(p_failedNode, m_peers);

			// #if LOGGER >= INFO
			m_logger.info(getClass(),
					"Recovery of failed node " + NodeID.toHexString(p_failedNode) + " complete.");
			// #endif /* LOGGER >= INFO */
		} else {
			// Failed node was a terminal -> remove it
			OverlayHelper.removePeer(p_failedNode, m_peers);
		}

		m_overlayLock.writeLock().unlock();

	}

	/**
	 * Joins the superpeer overlay through contactSuperpeer
	 * @param p_contactSuperpeer
	 *            NodeID of a known superpeer
	 * @param p_sleepInterval
	 *            the ping interval
	 * @return whether the joining was successful
	 * @lock no need for acquiring overlay lock in this method
	 */
	private boolean createOrJoinSuperpeerOverlay(final short p_contactSuperpeer, final int p_sleepInterval) {
		short contactSuperpeer;
		JoinRequest joinRequest;
		JoinResponse joinResponse = null;
		short[] newPeers;

		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Entering createOrJoinSuperpeerOverlay with: p_contactSuperpeer="
				+ NodeID.toHexString(p_contactSuperpeer));
		// #endif /* LOGGER == TRACE */

		contactSuperpeer = p_contactSuperpeer;

		if (p_contactSuperpeer == NodeID.INVALID_ID) {
			// #if LOGGER >= ERROR
			m_logger.error(getClass(), "Cannot join superpeer overlay, no bootstrap superpeer available to contact.");
			// #endif /* LOGGER >= ERROR */
			return false;
		}

		if (m_nodeID == contactSuperpeer) {
			if (m_boot.getNodeRole().equals(NodeRole.SUPERPEER)) {
				// #if LOGGER == TRACE
				m_logger.trace(getClass(), "Setting up new ring, I am " + NodeID.toHexString(m_nodeID));
				// #endif /* LOGGER == TRACE */
				setSuccessor(m_nodeID);
			} else {
				// #if LOGGER >= ERROR
				m_logger.error(getClass(), "Bootstrap has to be a superpeer, exiting now.");
				// #endif /* LOGGER >= ERROR */
				return false;
			}
		} else {
			while (-1 != contactSuperpeer) {
				// #if LOGGER == TRACE
				m_logger.trace(getClass(), "Contacting " + NodeID.toHexString(contactSuperpeer)
						+ " to join the ring, I am " + NodeID.toHexString(m_nodeID));
				// #endif /* LOGGER == TRACE */

				joinRequest = new JoinRequest(contactSuperpeer, m_nodeID, true);
				if (m_network.sendSync(joinRequest) != NetworkErrorCodes.SUCCESS) {
					// Contact superpeer is not available, get a new contact superpeer
					contactSuperpeer = m_boot.getNodeIDBootstrap();
					continue;
				}

				joinResponse = joinRequest.getResponse(JoinResponse.class);
				contactSuperpeer = joinResponse.getNewContactSuperpeer();
			}

			m_superpeers = joinResponse.getSuperpeers();

			m_peers = joinResponse.getPeers();

			newPeers = m_metadata.storeMetadata(joinResponse.getMetadata());
			if (newPeers != null) {
				for (short peer : newPeers) {
					addToAssignedPeers(peer);
				}
			}

			setSuccessor(joinResponse.getSuccessor());
			setPredecessor(joinResponse.getPredecessor());
		}

		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Starting stabilization thread");
		// #endif /* LOGGER == TRACE */
		m_stabilizationThread =
				new SuperpeerStabilizationThread(this, m_nodeID, m_overlayLock, m_initialNumberOfSuperpeers,
						m_superpeers, p_sleepInterval, m_logger, m_network);
		m_stabilizationThread.setName(
				SuperpeerStabilizationThread.class.getSimpleName() + " for " + LookupComponent.class.getSimpleName());
		m_stabilizationThread.setDaemon(true);
		m_stabilizationThread.start();

		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Exiting createOrJoinSuperpeerOverlay");
		// #endif /* LOGGER == TRACE */

		return true;
	}

	/**
	 * Spread data of failed superpeer
	 * @param p_nodeID
	 *            the NodeID
	 * @param p_responsibleArea
	 *            the responsible area
	 * @lock overlay lock must be read-locked
	 */
	private void spreadDataOfFailedSuperpeer(final short p_nodeID, final short[] p_responsibleArea) {
		byte[] metadata = null;

		metadata = m_metadata.receiveMetadataInRange(p_responsibleArea[0], p_responsibleArea[1]);

		while (!m_superpeers.isEmpty()) {
			// #if LOGGER >= DEBUG
			m_logger.debug(getClass(), "Spreading superpeer's meta-data to " + NodeID.toHexString(m_successor));
			// #endif /* LOGGER >= DEBUG */
			if (m_network.sendMessage(new SendBackupsMessage(m_successor, metadata)) != NetworkErrorCodes.SUCCESS) {
				// Successor is not available anymore, remove from superpeer array and try next superpeer
				// #if LOGGER >= ERROR
				m_logger.error(getClass(), "successor failed, too");
				// #endif /* LOGGER >= ERROR */
				continue;
			}
			break;
		}
	}

	/**
	 * Spread backups of failed superpeer
	 * @param p_lastBackupSuperpeer
	 *            the last backup superpeer
	 * @lock overlay lock must be read-locked
	 */
	private void spreadBackupsOfThisSuperpeer(final short p_lastBackupSuperpeer) {
		short newBackupSuperpeer;
		int index;
		boolean superpeerToSendData = false;
		byte[] metadata;
		String str = "Spreaded data of ";

		metadata = m_metadata.receiveMetadataInRange(m_predecessor, m_nodeID);

		while (!m_superpeers.isEmpty()) {
			// Determine successor of last backup superpeer
			index = (short) Collections.binarySearch(m_superpeers, (short) (p_lastBackupSuperpeer + 1));
			if (0 > index) {
				index = index * -1 - 1;
				if (index == m_superpeers.size()) {
					index = 0;
				}
			}
			newBackupSuperpeer = m_superpeers.get(index);

			superpeerToSendData = true;
			str += " to " + NodeID.toHexString(newBackupSuperpeer);

			if (m_network
					.sendMessage(new SendBackupsMessage(newBackupSuperpeer, metadata)) != NetworkErrorCodes.SUCCESS) {
				// Superpeer is not available anymore, remove from superpeer array and try next superpeer
				// #if LOGGER >= ERROR
				m_logger.error(getClass(),
						"new backup superpeer (" + NodeID.toHexString(newBackupSuperpeer) + ") failed, too");
				// #endif /* LOGGER >= ERROR */
				continue;
			}
			break;
		}
		// #if LOGGER >= DEBUG
		if (metadata.length > 0 && superpeerToSendData) {
			m_logger.debug(getClass(), str);
		} else {
			m_logger.debug(getClass(), "No need to spread data");
		}
		// #endif /* LOGGER >= DEBUG */
	}

	/**
	 * Handles an incoming JoinRequest
	 * @param p_joinRequest
	 *            the JoinRequest
	 */
	private void incomingJoinRequest(final JoinRequest p_joinRequest) {
		short joiningNode;
		short currentPeer;
		Iterator<Short> iter;
		ArrayList<Short> peers;

		byte[] metadata;
		short joiningNodesPredecessor;
		short superpeer;
		short[] responsibleArea;

		boolean newNodeisSuperpeer;

		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Got request: JOIN_REQUEST from " + NodeID.toHexString(p_joinRequest.getSource()));
		// #endif /* LOGGER == TRACE */

		joiningNode = p_joinRequest.getNewNode();
		newNodeisSuperpeer = p_joinRequest.nodeIsSuperpeer();

		if (newNodeisSuperpeer) {
			if (OverlayHelper.isSuperpeerInRange(joiningNode, m_predecessor, m_nodeID)) {
				m_overlayLock.writeLock().lock();
				// Send the joining node not only the successor, but the predecessor, superpeers
				// and all metadata
				if (m_superpeers.isEmpty()) {
					joiningNodesPredecessor = m_nodeID;
				} else {
					joiningNodesPredecessor = m_predecessor;
				}

				iter = m_peers.iterator();
				peers = new ArrayList<>();
				while (iter.hasNext()) {
					currentPeer = iter.next();
					if (OverlayHelper.isPeerInSuperpeerRange(currentPeer, joiningNodesPredecessor, joiningNode)) {
						peers.add(currentPeer);
					}
				}

				responsibleArea = OverlayHelper.getResponsibleArea(joiningNode, m_predecessor, m_superpeers);
				metadata = m_metadata.receiveMetadataInRange(responsibleArea[0], responsibleArea[1]);

				if (m_network.sendMessage(new JoinResponse(p_joinRequest, (short) -1, joiningNodesPredecessor, m_nodeID,
						m_superpeers, peers, metadata)) != NetworkErrorCodes.SUCCESS) {
					// Joining node is not available anymore -> ignore request and return directly
					return;
				}

				for (Short peer : peers) {
					OverlayHelper.removePeer(peer, m_peers);
				}

				// Notify predecessor about the joining node
				if (m_superpeers.isEmpty()) {
					setSuccessor(joiningNode);
					setPredecessor(joiningNode);
				} else {
					setPredecessor(joiningNode);

					if (m_network.sendMessage(new NotifyAboutNewSuccessorMessage(joiningNodesPredecessor,
							m_predecessor)) != NetworkErrorCodes.SUCCESS) {
						// Old predecessor is not available anymore, ignore it
					}
				}
				m_overlayLock.writeLock().unlock();
			} else {
				m_overlayLock.readLock().lock();
				superpeer = OverlayHelper.getResponsibleSuperpeer(joiningNode, m_superpeers, m_logger);
				m_overlayLock.readLock().unlock();

				if (m_network.sendMessage(new JoinResponse(p_joinRequest, superpeer, (short) -1, (short) -1,
						null, null, null)) != NetworkErrorCodes.SUCCESS) {
					// Joining node is not available anymore, ignore request
				}
			}
		} else {
			if (OverlayHelper.isPeerInSuperpeerRange(joiningNode, m_predecessor, m_nodeID)) {
				m_overlayLock.writeLock().lock();
				OverlayHelper.insertPeer(joiningNode, m_peers);
				// Lock downgrade
				m_overlayLock.readLock().lock();
				m_overlayLock.writeLock().unlock();
				if (m_network.sendMessage(
						new JoinResponse(p_joinRequest, (short) -1, (short) -1, (short) -1, m_superpeers, null,
								null)) != NetworkErrorCodes.SUCCESS) {
					// Joining node is not available anymore, ignore request
				}
				m_overlayLock.readLock().unlock();
			} else {
				m_overlayLock.readLock().lock();
				superpeer = OverlayHelper.getResponsibleSuperpeer(joiningNode, m_superpeers, m_logger);
				m_overlayLock.readLock().unlock();
				if (m_network.sendMessage(new JoinResponse(p_joinRequest, superpeer, (short) -1, (short) -1,
						null, null, null)) != NetworkErrorCodes.SUCCESS) {
					// Joining node is not available anymore, ignore request
				}
			}
		}
	}

	/**
	 * Handles an incoming GetLookupRangeRequest
	 * @param p_getLookupRangeRequest
	 *            the GetLookupRangeRequest
	 */
	private void incomingGetLookupRangeRequest(final GetLookupRangeRequest p_getLookupRangeRequest) {
		long chunkID;
		LookupRange result = null;

		chunkID = p_getLookupRangeRequest.getChunkID();
		// #if LOGGER == TRACE
		m_logger.trace(getClass(),
				"Got request: GET_LOOKUP_RANGE_REQUEST " + NodeID.toHexString(p_getLookupRangeRequest.getSource())
				+ " chunkID: " + ChunkID.toHexString(chunkID));
		// #endif /* LOGGER == TRACE */

		result = m_metadata.getLookupRangeFromLookupTree(chunkID);

		// #if LOGGER == TRACE
		m_logger.trace(getClass(),
				"GET_LOOKUP_RANGE_REQUEST " + NodeID.toHexString(p_getLookupRangeRequest.getSource()) + " chunkID "
						+ ChunkID.toHexString(chunkID) + " reply location: " + result);
		// #endif /* LOGGER == TRACE */

		if (m_network.sendMessage(
				new GetLookupRangeResponse(p_getLookupRangeRequest, result)) != NetworkErrorCodes.SUCCESS) {
			// Requesting peer is not available anymore, ignore it
		}
	}

	/**
	 * Handles an incoming RemoveChunkIDsRequest
	 * @param p_removeChunkIDsRequest
	 *            the RemoveChunkIDsRequest
	 */
	private void incomingRemoveChunkIDsRequest(final RemoveChunkIDsRequest p_removeChunkIDsRequest) {
		long[] chunkIDs;
		short creator;
		short[] backupSuperpeers;
		boolean isBackup;

		// #if LOGGER == TRACE
		m_logger.trace(getClass(),
				"Got Message: REMOVE_CHUNKIDS_REQUEST from " + NodeID.toHexString(p_removeChunkIDsRequest.getSource()));
		// #endif /* LOGGER == TRACE */

		chunkIDs = p_removeChunkIDsRequest.getChunkIDs();
		isBackup = p_removeChunkIDsRequest.isBackup();

		for (long chunkID : chunkIDs) {
			creator = ChunkID.getCreatorID(chunkID);
			if (OverlayHelper.isPeerInSuperpeerRange(creator, m_predecessor, m_nodeID)) {
				if (m_metadata.removeChunkIDFromLookupTree(chunkID)) {
					m_overlayLock.readLock().lock();
					backupSuperpeers = OverlayHelper.getBackupSuperpeers(m_nodeID, m_superpeers);
					m_overlayLock.readLock().unlock();
					if (m_network.sendMessage(
							new RemoveChunkIDsResponse(p_removeChunkIDsRequest,
									backupSuperpeers)) != NetworkErrorCodes.SUCCESS) {
						// Requesting peer is not available anymore, ignore it
					}
				} else {
					// #if LOGGER >= ERROR
					m_logger.error(getClass(),
							"CIDTree range not initialized on responsible superpeer " + NodeID.toHexString(m_nodeID));
					// #endif /* LOGGER >= ERROR */
					if (m_network.sendMessage(new RemoveChunkIDsResponse(p_removeChunkIDsRequest,
							new short[] {-1})) != NetworkErrorCodes.SUCCESS) {
						// Requesting peer is not available anymore, ignore it
					}
				}
			} else if (isBackup) {
				if (!m_metadata.removeChunkIDFromLookupTree(chunkID)) {
					// #if LOGGER >= WARN
					m_logger.warn(getClass(),
							"CIDTree range not initialized on backup superpeer " + NodeID.toHexString(m_nodeID));
					// #endif /* LOGGER >= WARN */
				}

				if (m_network.sendMessage(
						new RemoveChunkIDsResponse(p_removeChunkIDsRequest, null)) != NetworkErrorCodes.SUCCESS) {
					// Requesting peer is not available anymore, ignore it
				}
			} else {
				// Not responsible for requesting peer
				if (m_network.sendMessage(
						new RemoveChunkIDsResponse(p_removeChunkIDsRequest, null)) != NetworkErrorCodes.SUCCESS) {
					// Requesting peer is not available anymore, ignore it
				}
			}
		}
	}

	/**
	 * Handles an incoming InsertIDRequest
	 * @param p_insertIDRequest
	 *            the InsertIDRequest
	 */
	private void incomingInsertNameserviceEntriesRequest(final InsertNameserviceEntriesRequest p_insertIDRequest) {
		int id;
		short[] backupSuperpeers;

		id = p_insertIDRequest.getID();
		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Got request: INSERT_ID_REQUEST from "
				+ NodeID.toHexString(p_insertIDRequest.getSource()) + ", id " + id);
		// #endif /* LOGGER == TRACE */

		m_overlayLock.readLock().lock();
		if (OverlayHelper.isHashInSuperpeerRange(m_hashGenerator.hash(id), m_predecessor, m_nodeID)) {
			m_metadata.putNameserviceEntry(id, p_insertIDRequest.getChunkID());

			backupSuperpeers = OverlayHelper.getBackupSuperpeers(m_nodeID, m_superpeers);
			if (m_network.sendMessage(
					new InsertNameserviceEntriesResponse(p_insertIDRequest,
							backupSuperpeers)) != NetworkErrorCodes.SUCCESS) {
				// Requesting peer is not available anymore, ignore it
			}

			ArrayList<Short> peers = getPeers();
			// notify peers about this to update caches
			for (short peer : peers) {
				NameserviceUpdatePeerCachesMessage message =
						new NameserviceUpdatePeerCachesMessage(peer, id, p_insertIDRequest.getChunkID());
				if (m_network.sendMessage(message) != NetworkErrorCodes.SUCCESS) {
					// peer is not available anymore, ignore it
				}
			}
		} else if (p_insertIDRequest.isBackup()) {
			m_metadata.putNameserviceEntry(id, p_insertIDRequest.getChunkID());

			if (m_network.sendMessage(
					new InsertNameserviceEntriesResponse(p_insertIDRequest, null)) != NetworkErrorCodes.SUCCESS) {
				// Requesting peer is not available anymore, ignore it
			}
		} else {
			// Not responsible for that chunk
			if (m_network.sendMessage(
					new InsertNameserviceEntriesResponse(p_insertIDRequest, null)) != NetworkErrorCodes.SUCCESS) {
				// Requesting peer is not available anymore, ignore it
			}
		}
		m_overlayLock.readLock().unlock();
	}

	/**
	 * Handles an incoming GetChunkIDForNameserviceEntryRequest
	 * @param p_getChunkIDForNameserviceEntryRequest
	 *            the GetChunkIDForNameserviceEntryRequest
	 */
	private void incomingGetChunkIDForNameserviceEntryRequest(
			final GetChunkIDForNameserviceEntryRequest p_getChunkIDForNameserviceEntryRequest) {
		int id;
		long chunkID = -1;

		id = p_getChunkIDForNameserviceEntryRequest.getID();
		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Got request: GET_CHUNKID_FOR_NAMESERVICE_ENTRY_REQUEST from "
				+ NodeID.toHexString(p_getChunkIDForNameserviceEntryRequest.getSource()) + ", id " + id);
		// #endif /* LOGGER == TRACE */

		if (OverlayHelper.isHashInSuperpeerRange(m_hashGenerator.hash(id), m_predecessor, m_nodeID)) {
			m_metadata.getNameserviceEntry(id);

			// #if LOGGER == TRACE
			m_logger.trace(getClass(),
					"GET_CHUNKID_REQUEST from " + NodeID.toHexString(p_getChunkIDForNameserviceEntryRequest.getSource())
					+ ", id " + id + ", reply chunkID " + ChunkID.toHexString(chunkID));
			// #endif /* LOGGER == TRACE */
		}
		if (m_network.sendMessage(
				new GetChunkIDForNameserviceEntryResponse(p_getChunkIDForNameserviceEntryRequest,
						chunkID)) != NetworkErrorCodes.SUCCESS) {
			// Requesting peer is not available anymore, ignore it
		}
	}

	/**
	 * Handles an incoming GetNameserviceEntryCountRequest
	 * @param p_getNameserviceEntryCountRequest
	 *            the GetNameserviceEntryCountRequest
	 */
	private void incomingGetNameserviceEntryCountRequest(
			final GetNameserviceEntryCountRequest p_getNameserviceEntryCountRequest) {
		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Got request: GET_CHUNKID_FOR_NAMESERVICE_ENTRY_REQUEST from "
				+ NodeID.toHexString(p_getNameserviceEntryCountRequest.getSource()));
		// #endif /* LOGGER == TRACE */

		if (m_network.sendMessage(new GetNameserviceEntryCountResponse(p_getNameserviceEntryCountRequest,
				m_metadata.countNameserviceEntries(m_predecessor, m_nodeID))) != NetworkErrorCodes.SUCCESS) {
			// Requesting peer is not available anymore, ignore it
		}
	}

	/**
	 * Handles an incoming GetNameserviceEntriesRequest
	 * @param p_getNameserviceEntriesRequest
	 *            the GetNameserviceEntriesRequest
	 */
	private void incomingGetNameserviceEntriesRequest(
			final GetNameserviceEntriesRequest p_getNameserviceEntriesRequest) {
		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Got request: GET_NAMESERVICE_ENTRIES from "
				+ p_getNameserviceEntriesRequest.getSource());
		// #endif /* LOGGER == TRACE */

		if (m_network.sendMessage(new GetNameserviceEntriesResponse(p_getNameserviceEntriesRequest,
				m_metadata.getAllNameserviceEntries())) != NetworkErrorCodes.SUCCESS) {
			// Requesting peer is not available anymore, ignore it
		}
	}

	/**
	 * Handles an incoming MigrateRequest
	 * @param p_migrateRequest
	 *            the MigrateRequest
	 */
	private void incomingMigrateRequest(final MigrateRequest p_migrateRequest) {
		short nodeID;
		long chunkID;
		short creator;
		short[] backupSuperpeers;
		boolean isBackup;

		// #if LOGGER == TRACE
		m_logger.trace(getClass(),
				"Got Message: MIGRATE_REQUEST from " + NodeID.toHexString(p_migrateRequest.getSource()));
		// #endif /* LOGGER == TRACE */

		nodeID = p_migrateRequest.getNodeID();
		chunkID = p_migrateRequest.getChunkID();
		creator = ChunkID.getCreatorID(chunkID);
		isBackup = p_migrateRequest.isBackup();

		m_overlayLock.readLock().lock();
		if (OverlayHelper.isPeerInSuperpeerRange(creator, m_predecessor, m_nodeID)) {
			if (m_metadata.putChunkIDInLookupTree(chunkID, nodeID)) {
				backupSuperpeers = OverlayHelper.getBackupSuperpeers(m_nodeID, m_superpeers);
				m_overlayLock.readLock().unlock();
				if (-1 != backupSuperpeers[0]) {
					// Outsource informing backups to another thread to avoid blocking a message handler
					Runnable task = () -> {
						// Send backups
							for (short backupSuperpeer : backupSuperpeers) {
								MigrateRequest request = new MigrateRequest(backupSuperpeer, chunkID, nodeID, true);
								if (m_network.sendSync(request) != NetworkErrorCodes.SUCCESS) {
									// Ignore superpeer failure, superpeer will fix this later
								}
							}
						};
					new Thread(task).start();
				}

				if (m_network.sendMessage(new MigrateResponse(p_migrateRequest, true)) != NetworkErrorCodes.SUCCESS) {
					// Requesting peer is not available anymore, ignore it
				}
			} else {
				m_overlayLock.readLock().unlock();
				// #if LOGGER >= ERROR
				m_logger.error(getClass(),
						"CIDTree range not initialized on responsible superpeer " + NodeID.toHexString(m_nodeID));
				// #endif /* LOGGER >= ERROR */
				if (m_network.sendMessage(new MigrateResponse(p_migrateRequest, false)) != NetworkErrorCodes.SUCCESS) {
					// Requesting peer is not available anymore, ignore request it
				}
			}
		} else if (isBackup) {
			if (!m_metadata.putChunkIDInLookupTree(chunkID, nodeID)) {
				// #if LOGGER >= WARN
				m_logger.warn(getClass(),
						"CIDTree range not initialized on backup superpeer " + NodeID.toHexString(m_nodeID));
				// #endif /* LOGGER >= WARN */
			}
			m_overlayLock.readLock().unlock();

			if (m_network.sendMessage(
					new MigrateResponse(p_migrateRequest, true)) != NetworkErrorCodes.SUCCESS) {
				// Requesting peer is not available anymore, ignore it
			}
		} else {
			m_overlayLock.readLock().unlock();
			// Not responsible for requesting peer
			if (m_network.sendMessage(
					new MigrateResponse(p_migrateRequest, false)) != NetworkErrorCodes.SUCCESS) {
				// Requesting peer is not available anymore, ignore it
			}
		}
	}

	/**
	 * Handles an incoming MigrateRangeRequest
	 * @param p_migrateRangeRequest
	 *            the MigrateRangeRequest
	 */
	private void incomingMigrateRangeRequest(final MigrateRangeRequest p_migrateRangeRequest) {
		short nodeID;
		long startChunkID;
		long endChunkID;
		short creator;
		short[] backupSuperpeers;
		boolean isBackup;

		// #if LOGGER == TRACE
		m_logger.trace(getClass(),
				"Got Message: MIGRATE_RANGE_REQUEST from " + NodeID.toHexString(p_migrateRangeRequest.getSource()));
		// #endif /* LOGGER == TRACE */

		nodeID = p_migrateRangeRequest.getNodeID();
		startChunkID = p_migrateRangeRequest.getStartChunkID();
		endChunkID = p_migrateRangeRequest.getEndChunkID();
		creator = ChunkID.getCreatorID(startChunkID);
		isBackup = p_migrateRangeRequest.isBackup();

		if (creator != ChunkID.getCreatorID(endChunkID)) {
			// #if LOGGER >= ERROR
			m_logger.error(getClass(), "start and end objects creators not equal");
			// #endif /* LOGGER >= ERROR */
			return;
		}

		m_overlayLock.readLock().lock();
		if (OverlayHelper.isPeerInSuperpeerRange(creator, m_predecessor, m_nodeID)) {
			if (m_metadata.putChunkIDRangeInLookupTree(startChunkID, endChunkID, nodeID)) {
				backupSuperpeers = OverlayHelper.getBackupSuperpeers(m_nodeID, m_superpeers);
				m_overlayLock.readLock().unlock();
				if (-1 != backupSuperpeers[0]) {
					// Outsource informing backups to another thread to avoid blocking a message handler
					Runnable task = () -> {
						// Send backups
							for (short backupSuperpeer : backupSuperpeers) {
								MigrateRangeRequest request =
										new MigrateRangeRequest(backupSuperpeer, startChunkID, endChunkID, nodeID, true);
								if (m_network.sendSync(request) != NetworkErrorCodes.SUCCESS) {
									// Ignore superpeer failure, superpeer will fix this later
								}
							}
						};
					new Thread(task).start();
				}

				if (m_network.sendMessage(
						new MigrateRangeResponse(p_migrateRangeRequest, true)) != NetworkErrorCodes.SUCCESS) {
					// Requesting peer is not available anymore, ignore it
				}
			} else {
				m_overlayLock.readLock().unlock();
				// #if LOGGER >= ERROR
				m_logger.error(getClass(),
						"CIDTree range not initialized on responsible superpeer " + NodeID.toHexString(m_nodeID));
				// #endif /* LOGGER >= ERROR */
				if (m_network.sendMessage(
						new MigrateRangeResponse(p_migrateRangeRequest, false)) != NetworkErrorCodes.SUCCESS) {
					// Requesting peer is not available anymore, ignore it
				}
			}
		} else if (isBackup) {
			if (!m_metadata.putChunkIDRangeInLookupTree(startChunkID, endChunkID, nodeID)) {
				// #if LOGGER >= WARN
				m_logger.warn(getClass(),
						"CIDTree range not initialized on backup superpeer " + NodeID.toHexString(m_nodeID));
				// #endif /* LOGGER >= WARN */
			}
			m_overlayLock.readLock().unlock();

			if (m_network.sendMessage(
					new MigrateRangeResponse(p_migrateRangeRequest, true)) != NetworkErrorCodes.SUCCESS) {
				// Requesting peer is not available anymore, ignore it
			}
		} else {
			m_overlayLock.readLock().unlock();
			// Not responsible for requesting peer
			if (m_network.sendMessage(
					new MigrateRangeResponse(p_migrateRangeRequest, false)) != NetworkErrorCodes.SUCCESS) {
				// Requesting peer is not available anymore, ignore request it
			}
		}
	}

	/**
	 * Handles an incoming InitRangeRequest
	 * @param p_initRangeRequest
	 *            the InitRangeRequest
	 */
	private void incomingInitRangeRequest(final InitRangeRequest p_initRangeRequest) {
		LookupRangeWithBackupPeers primaryAndBackupPeers;
		long startChunkIDOrRangeID;
		short creator;
		short[] backupSuperpeers;
		boolean isBackup;

		// #if LOGGER == TRACE
		m_logger.trace(getClass(),
				"Got Message: INIT_RANGE_REQUEST from " + NodeID.toHexString(p_initRangeRequest.getSource()));
		// #endif /* LOGGER == TRACE */

		primaryAndBackupPeers = new LookupRangeWithBackupPeers(p_initRangeRequest.getLookupRange());
		startChunkIDOrRangeID = p_initRangeRequest.getStartChunkIDOrRangeID();
		creator = primaryAndBackupPeers.getPrimaryPeer();
		isBackup = p_initRangeRequest.isBackup();

		m_overlayLock.writeLock().lock();
		if (OverlayHelper.isPeerInSuperpeerRange(creator, m_predecessor, m_nodeID)) {
			if (m_metadata.initBackupRangeInLookupTree(creator, primaryAndBackupPeers.getBackupPeers(),
					startChunkIDOrRangeID)) {
				addToAssignedPeers(creator);
			}

			backupSuperpeers = OverlayHelper.getBackupSuperpeers(m_nodeID, m_superpeers);
			m_overlayLock.writeLock().unlock();
			if (-1 != backupSuperpeers[0]) {
				// Outsource informing backups to another thread to avoid blocking a message handler
				Runnable task = () -> {
					// Send backups
						for (short backupSuperpeer : backupSuperpeers) {
							InitRangeRequest request = new InitRangeRequest(backupSuperpeer, startChunkIDOrRangeID,
									primaryAndBackupPeers.convertToLong(), true);
							if (m_network.sendSync(request) != NetworkErrorCodes.SUCCESS) {
								// Ignore superpeer failure, superpeer will fix this later
							}
						}
					};
				new Thread(task).start();
			}

			if (m_network.sendMessage(new InitRangeResponse(p_initRangeRequest, true)) != NetworkErrorCodes.SUCCESS) {
				// Requesting peer is not available anymore, ignore it
			}
		} else if (isBackup) {
			if (m_metadata.initBackupRangeInLookupTree(creator, primaryAndBackupPeers.getBackupPeers(),
					startChunkIDOrRangeID)) {
				addToAssignedPeers(creator);
			}
			m_overlayLock.writeLock().unlock();

			if (m_network.sendMessage(new InitRangeResponse(p_initRangeRequest, true)) != NetworkErrorCodes.SUCCESS) {
				// Requesting peer is not available anymore, ignore it
			}
		} else {
			m_overlayLock.writeLock().unlock();
			// Not responsible for requesting peer
			if (m_network.sendMessage(new InitRangeResponse(p_initRangeRequest, false)) != NetworkErrorCodes.SUCCESS) {
				// Requesting node is not available anymore, ignore it
			}
		}
	}

	/**
	 * Handles an incoming GetAllBackupRangesRequest
	 * @param p_getAllBackupRangesRequest
	 *            the GetAllBackupRangesRequest
	 */
	private void incomingGetAllBackupRangesRequest(final GetAllBackupRangesRequest p_getAllBackupRangesRequest) {
		BackupRange[] result = null;

		// #if LOGGER == TRACE
		m_logger.trace(getClass(),
				"Got request: GET_ALL_BACKUP_RANGES_REQUEST "
						+ NodeID.toHexString(p_getAllBackupRangesRequest.getSource()));
		// #endif /* LOGGER == TRACE */

		result = m_metadata.getAllBackupRangesFromLookupTree(p_getAllBackupRangesRequest.getNodeID());
		if (m_network.sendMessage(
				new GetAllBackupRangesResponse(p_getAllBackupRangesRequest, result)) != NetworkErrorCodes.SUCCESS) {
			// Requesting peer is not available anymore, ignore it
		}
	}

	/**
	 * Handles an incoming SetRestorerAfterRecoveryMessage
	 * @param p_setRestorerAfterRecoveryMessage
	 *            the SetRestorerAfterRecoveryMessage
	 */
	private void incomingSetRestorerAfterRecoveryMessage(
			final SetRestorerAfterRecoveryMessage p_setRestorerAfterRecoveryMessage) {

		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Got request: SET_RESTORER_AFTER_RECOVERY_MESSAGE "
				+ NodeID.toHexString(p_setRestorerAfterRecoveryMessage.getSource()));
		// #endif /* LOGGER == TRACE */

		m_metadata.setRestorerAfterRecoveryInLookupTree(p_setRestorerAfterRecoveryMessage.getOwner(),
				p_setRestorerAfterRecoveryMessage.getSource());
	}

	/**
	 * Handles an incoming BarrierAllocRequest
	 * @param p_request
	 *            the BarrierAllocRequest
	 */
	private void incomingBarrierAllocRequest(final BarrierAllocRequest p_request) {
		int barrierId;
		NetworkErrorCodes err;

		if (!p_request.isReplicate()) {
			barrierId = m_metadata.createBarrier(m_nodeID, p_request.getBarrierSize());
		} else {
			barrierId = m_metadata.createBarrier(p_request.getSource(), p_request.getBarrierSize());
		}

		// #if LOGGER >= ERROR
		if (barrierId == BarrierID.INVALID_ID) {
			m_logger.error(getClass(), "Creating barrier for size " + p_request.getBarrierSize() + " failed.");
		}
		// #endif /* LOGGER >= ERROR */

		BarrierAllocResponse response = new BarrierAllocResponse(p_request, barrierId);
		err = m_network.sendMessage(response);
		// #if LOGGER >= ERROR
		if (err != NetworkErrorCodes.SUCCESS) {
			m_logger.error(getClass(), "Sending response to barrier request " + p_request + " failed: " + err);
		}
		// #endif /* LOGGER >= ERROR */

		if (!p_request.isReplicate()) {
			// Outsource informing backups to another thread to avoid blocking a message handler
			Runnable task = () -> {
				m_overlayLock.readLock().lock();
				short[] backupSuperpeers = OverlayHelper.getBackupSuperpeers(m_nodeID, m_superpeers);
				m_overlayLock.readLock().unlock();

				for (short backupSuperpeer : backupSuperpeers) {
					if (backupSuperpeer == NodeID.INVALID_ID) {
						continue;
					}

					BarrierAllocRequest request =
							new BarrierAllocRequest(backupSuperpeer, p_request.getBarrierSize(), true);
					// send as message, only
					NetworkErrorCodes error = m_network.sendMessage(request);
					if (error != NetworkErrorCodes.SUCCESS) {
						// ignore result
					}
				}
			};
			new Thread(task).start();
		}
	}

	/**
	 * Handles an incoming BarrierFreeRequest
	 * @param p_request
	 *            the BarrierFreeRequest
	 */
	private void incomingBarrierFreeRequest(final BarrierFreeRequest p_request) {
		short creator;
		NetworkErrorCodes err;

		if (!p_request.isReplicate()) {
			creator = m_nodeID;
		} else {
			creator = p_request.getSource();
		}

		BarrierFreeResponse response = new BarrierFreeResponse(p_request);
		if (!m_metadata.removeBarrier(creator, p_request.getBarrierId())) {
			// #if LOGGER >= ERROR
			m_logger.error(getClass(),
					"Free'ing barrier " + BarrierID.toHexString(p_request.getBarrierId()) + " failed.");
			// #endif /* LOGGER >= ERROR */
			response.setStatusCode((byte) -1);
		} else {
			response.setStatusCode((byte) 0);
		}

		err = m_network.sendMessage(response);
		// #if LOGGER >= ERROR
		if (err != NetworkErrorCodes.SUCCESS) {
			m_logger.error(getClass(),
					"Sending back response for barrier free message " + p_request + " failed: " + err);
		}
		// #endif /* LOGGER >= ERROR */

		if (!p_request.isReplicate()) {
			// Outsource informing backups to another thread to avoid blocking a message handler
			Runnable task = () -> {
				m_overlayLock.readLock().lock();
				short[] backupSuperpeers = OverlayHelper.getBackupSuperpeers(m_nodeID, m_superpeers);
				m_overlayLock.readLock().unlock();

				for (short backupSuperpeer : backupSuperpeers) {
					if (backupSuperpeer == NodeID.INVALID_ID) {
						continue;
					}

					BarrierFreeRequest request =
							new BarrierFreeRequest(backupSuperpeer, p_request.getBarrierId(), true);
					// send as message, only
					NetworkErrorCodes error = m_network.sendMessage(request);
					if (error != NetworkErrorCodes.SUCCESS) {
						// ignore result
					}
				}
			};
			new Thread(task).start();
		}
	}

	/**
	 * Handles an incoming BarrierSignOnRequest
	 * @param p_request
	 *            the BarrierSignOnRequest
	 */
	private void incomingBarrierSignOnRequest(final BarrierSignOnRequest p_request) {
		int barrierId = p_request.getBarrierId();
		int res = m_metadata.signOnBarrier(m_nodeID, barrierId, p_request.getSource(), p_request.getCustomData());
		BarrierSignOnResponse response = new BarrierSignOnResponse(p_request, (byte) (res >= 0 ? 0 : -1));
		NetworkErrorCodes err = m_network.sendMessage(response);
		// #if LOGGER >= ERROR
		if (err != NetworkErrorCodes.SUCCESS) {
			m_logger.error(getClass(), "Sending response to sign on request " + p_request + " failed: " + err);
		}
		// #endif /* LOGGER >= ERROR */

		// release all if this was the last sign on
		if (res == 0) {
			short[] signedOnPeers = m_metadata.getSignedOnPeersOfBarrier(m_nodeID, barrierId);
			long[] customData = m_metadata.getCustomDataOfBarrier(m_nodeID, barrierId);
			for (int i = 1; i < signedOnPeers.length; i++) {
				BarrierReleaseMessage message =
						new BarrierReleaseMessage(signedOnPeers[i], barrierId, signedOnPeers, customData);
				err = m_network.sendMessage(message);
				// #if LOGGER >= ERROR
				if (err != NetworkErrorCodes.SUCCESS) {
					m_logger.error(getClass(),
							"Releasing peer " + NodeID.toHexString(signedOnPeers[i]) + " of barrier " + BarrierID
							.toHexString(barrierId) + " failed: " + err);
				}
				// #endif /* LOGGER >= ERROR */
			}
			// reset for reuse
			m_metadata.resetBarrier(m_nodeID, barrierId);
		}
	}

	/**
	 * Handles an incoming BarrierGetStatusRequest
	 * @param p_request
	 *            the BarrierGetStatusRequest
	 */
	private void incomingBarrierGetStatusRequest(final BarrierGetStatusRequest p_request) {
		short[] signedOnPeers = m_metadata.getSignedOnPeersOfBarrier(m_nodeID, p_request.getBarrierId());
		BarrierGetStatusResponse response;
		if (signedOnPeers == null) {
			// barrier does not exist
			response = new BarrierGetStatusResponse(p_request, new short[0]);
			response.setStatusCode((byte) -1);
		} else {
			response = new BarrierGetStatusResponse(p_request, signedOnPeers);
		}

		NetworkErrorCodes err = m_network.sendMessage(response);
		// #if LOGGER >= ERROR
		if (err != NetworkErrorCodes.SUCCESS) {
			m_logger.error(getClass(), "Sending response to status request " + p_request + " failed: " + err);
		}
		// #endif /* LOGGER >= ERROR */
	}

	/**
	 * Handles an incoming BarrierChangeSizeRequest
	 * @param p_request
	 *            the BarrierChangeSizeRequest
	 */
	private void incomingBarrierChangeSizeRequest(final BarrierChangeSizeRequest p_request) {
		short creator;
		NetworkErrorCodes err;

		if (!p_request.isReplicate()) {
			creator = m_nodeID;
		} else {
			creator = p_request.getSource();
		}

		BarrierChangeSizeResponse response = new BarrierChangeSizeResponse(p_request);
		if (!m_metadata.changeSizeOfBarrier(creator, p_request.getBarrierId(), p_request.getBarrierSize())) {
			response.setStatusCode((byte) -1);
		} else {
			response.setStatusCode((byte) 0);
		}

		err = m_network.sendMessage(response);
		// #if LOGGER >= ERROR
		if (err != NetworkErrorCodes.SUCCESS) {
			m_logger.error(getClass(),
					"Sending response for barrier change size request " + p_request + " failed: " + err);
		}
		// #endif /* LOGGER >= ERROR */

		if (!p_request.isReplicate()) {
			// Outsource informing backups to another thread to avoid blocking a message handler
			Runnable task = () -> {
				m_overlayLock.readLock().lock();
				short[] backupSuperpeers = OverlayHelper.getBackupSuperpeers(m_nodeID, m_superpeers);
				m_overlayLock.readLock().unlock();

				for (short backupSuperpeer : backupSuperpeers) {
					if (backupSuperpeer == NodeID.INVALID_ID) {
						continue;
					}

					BarrierChangeSizeRequest request = new BarrierChangeSizeRequest(backupSuperpeer,
							p_request.getBarrierId(), p_request.getBarrierSize(), true);
					// send as message, only
					NetworkErrorCodes error = m_network.sendMessage(request);
					if (error != NetworkErrorCodes.SUCCESS) {
						// ignore result
					}
				}
			};
			new Thread(task).start();
		}
	}

	/**
	 * Handles an incoming SuperpeerStorageCreateRequest
	 * @param p_request
	 *            the SuperpeerStorageCreateRequest
	 */
	private void incomingSuperpeerStorageCreateRequest(final SuperpeerStorageCreateRequest p_request) {
		int ret = m_metadata.createStorage(p_request.getStorageId(), p_request.getSize());

		if (!p_request.isReplicate()) {
			SuperpeerStorageCreateResponse response = new SuperpeerStorageCreateResponse(p_request);
			response.setStatusCode((byte) ret);
			NetworkErrorCodes err = m_network.sendMessage(response);
			// #if LOGGER >= ERROR
			if (err != NetworkErrorCodes.SUCCESS) {
				m_logger.error(getClass(),
						"Sending response to storage create with size " + p_request.getSize() + " failed: " + err);
				return;
			}
			// #endif /* LOGGER >= ERROR */
		}

		// replicate to next 3 superpeers
		if (ret != 0 && !p_request.isReplicate()) {
			// Outsource informing backups to another thread to avoid blocking a message handler
			Runnable task = () -> {
				m_overlayLock.readLock().lock();
				short[] backupSuperpeers = OverlayHelper.getBackupSuperpeers(m_nodeID, m_superpeers);
				m_overlayLock.readLock().unlock();

				for (short backupSuperpeer : backupSuperpeers) {
					if (backupSuperpeer == NodeID.INVALID_ID) {
						continue;
					}

					SuperpeerStorageCreateRequest request =
							new SuperpeerStorageCreateRequest(backupSuperpeer, p_request.getStorageId(),
									p_request.getSize(), true);
					// send as message, only
					NetworkErrorCodes err = m_network.sendMessage(request);
					if (err != NetworkErrorCodes.SUCCESS) {
						// ignore result
					}
				}
			};
			new Thread(task).start();
		}
	}

	/**
	 * Handles an incoming SuperpeerStorageGetRequest
	 * @param p_request
	 *            the SuperpeerStorageGetRequest
	 */
	private void incomingSuperpeerStorageGetRequest(final SuperpeerStorageGetRequest p_request) {
		byte[] data = m_metadata.getStorage(p_request.getStorageID());

		Chunk chunk;
		if (data == null) {
			// create invalid entry
			chunk = new Chunk();
		} else {
			chunk = new Chunk(p_request.getStorageID(), ByteBuffer.wrap(data));
		}

		SuperpeerStorageGetResponse response = new SuperpeerStorageGetResponse(p_request, chunk);
		if (chunk.getID() == ChunkID.INVALID_ID) {
			response.setStatusCode((byte) -1);
		}

		NetworkErrorCodes err = m_network.sendMessage(response);
		// #if LOGGER >= ERROR
		if (err != NetworkErrorCodes.SUCCESS) {
			m_logger.error(getClass(), "Sending response to storage get failed: " + err);
		}
		// #endif /* LOGGER >= ERROR */
	}

	/**
	 * Handles an incoming SuperpeerStoragePutRequest
	 * @param p_request
	 *            the SuperpeerStoragePutRequest
	 */
	private void incomingSuperpeerStoragePutRequest(final SuperpeerStoragePutRequest p_request) {
		Chunk chunk = p_request.getChunk();

		int res = m_metadata.putStorage((int) chunk.getID(), chunk.getData().array());
		if (!p_request.isReplicate()) {
			SuperpeerStoragePutResponse response = new SuperpeerStoragePutResponse(p_request);
			if (res != chunk.getDataSize()) {
				response.setStatusCode((byte) -1);
			}
			NetworkErrorCodes err = m_network.sendMessage(response);
			// #if LOGGER >= ERROR
			if (err != NetworkErrorCodes.SUCCESS) {
				m_logger.error(getClass(), "Sending response to put request to superpeer storage failed: " + err);
			}
			// #endif /* LOGGER >= ERROR */
		}

		// replicate to next 3 superpeers
		if (res != 0 && !p_request.isReplicate()) {
			// Outsource informing backups to another thread to avoid blocking a message handler
			Runnable task = () -> {
				m_overlayLock.readLock().lock();
				short[] backupSuperpeers = OverlayHelper.getBackupSuperpeers(m_nodeID, m_superpeers);
				m_overlayLock.readLock().unlock();

				for (short backupSuperpeer : backupSuperpeers) {
					if (backupSuperpeer == NodeID.INVALID_ID) {
						continue;
					}

					SuperpeerStoragePutRequest request =
							new SuperpeerStoragePutRequest(backupSuperpeer, p_request.getChunk(), true);
					// send as message, only
					NetworkErrorCodes err = m_network.sendMessage(request);
					if (err != NetworkErrorCodes.SUCCESS) {
						// ignore result
					}
				}
			};
			new Thread(task).start();
		}
	}

	/**
	 * Handles an incoming SuperpeerStorageRemoveMessage
	 * @param p_request
	 *            the SuperpeerStorageRemoveMessage
	 */
	private void incomingSuperpeerStorageRemoveMessage(final SuperpeerStorageRemoveMessage p_request) {
		boolean res = m_metadata.removeStorage(p_request.getStorageId());
		// #if LOGGER >= ERROR
		if (!res) {
			m_logger.error(getClass(),
					"Removing object " + p_request.getStorageId() + " from superpeer storage failed.");
		}
		// #endif /* LOGGER >= ERROR */

		// replicate to next 3 superpeers
		if (res && !p_request.isReplicate()) {
			// Outsource informing backups to another thread to avoid blocking a message handler
			Runnable task = () -> {
				m_overlayLock.readLock().lock();
				short[] backupSuperpeers = OverlayHelper.getBackupSuperpeers(m_nodeID, m_superpeers);
				m_overlayLock.readLock().unlock();

				for (short backupSuperpeer : backupSuperpeers) {
					if (backupSuperpeer == NodeID.INVALID_ID) {
						continue;
					}

					SuperpeerStorageRemoveMessage request =
							new SuperpeerStorageRemoveMessage(backupSuperpeer, p_request.getStorageId(), true);
					// send as message, only
					NetworkErrorCodes err = m_network.sendMessage(request);
					if (err != NetworkErrorCodes.SUCCESS) {
						// ignore result
					}
				}
			};
			new Thread(task).start();
		}
	}

	/**
	 * Handles an incoming SuperpeerStorageStatusRequest
	 * @param p_request
	 *            the SuperpeerStorageStatusRequest
	 */
	private void incomingSuperpeerStorageStatusRequest(final SuperpeerStorageStatusRequest p_request) {
		SuperpeerStorage.Status status = m_metadata.getStorageStatus();

		SuperpeerStorageStatusResponse response = new SuperpeerStorageStatusResponse(p_request, status);
		NetworkErrorCodes err = m_network.sendMessage(response);
		// #if LOGGER >= ERROR
		if (err != NetworkErrorCodes.SUCCESS) {
			m_logger.error(getClass(), "Sending response to superpeer storage get status message failed: " + err);
		}
		// #endif /* LOGGER >= ERROR */
	}

	/**
	 * Handles an incoming GetMetadataSummaryRequest
	 * @param p_request
	 *            the GetMetadataSummaryRequest
	 */
	private void incomingGetMetadataSummaryRequest(final GetMetadataSummaryRequest p_request) {
		m_overlayLock.readLock().lock();
		GetMetadataSummaryResponse response =
				new GetMetadataSummaryResponse(p_request, m_metadata.getSummary(m_nodeID, m_predecessor));
		m_overlayLock.readLock().unlock();
		NetworkErrorCodes err = m_network.sendMessage(response);
		// #if LOGGER >= ERROR
		if (err != NetworkErrorCodes.SUCCESS) {
			m_logger.error(getClass(), "Sending response to get metadata summary request failed: " + err);
		}
		// #endif /* LOGGER >= ERROR */
	}

	/**
	 * Handles an incoming NotifyAboutFailedPeerRequest
	 * @param p_notifyAboutFailedPeerRequest
	 *            the NotifyAboutFailedPeerRequest
	 */
	private void
	incomingNotifyAboutFailedPeerRequest(final NotifyAboutFailedPeerRequest p_notifyAboutFailedPeerRequest) {
		short failedPeer;

		// #if LOGGER == TRACE
		m_logger.trace(getClass(), "Got message: NOTIFY_ABOUT_FAILED_PEER_MESSAGE from "
				+ NodeID.toHexString(p_notifyAboutFailedPeerRequest.getSource()));
		// #endif /* LOGGER == TRACE */

		m_network.sendMessage(new NotifyAboutFailedPeerResponse(p_notifyAboutFailedPeerRequest));

		failedPeer = p_notifyAboutFailedPeerRequest.getFailedPeer();

		// Outsource informing all peers to another thread to avoid blocking a message handler
		Runnable task = () -> {
			// Inform all peers
				for (short peer : m_peers) {
					FailureRequest failureRequest = new FailureRequest(peer, failedPeer);
					if (m_network.sendSync(failureRequest) != NetworkErrorCodes.SUCCESS) {
						// Ignore, failure is detected by network module
					}
				}
			};
		new Thread(task).start();
	}

	/**
	 * Handles an incoming Message
	 * @param p_message
	 *            the Message
	 */
	@Override
	public void onIncomingMessage(final AbstractMessage p_message) {
		if (p_message != null) {
			if (p_message.getType() == DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE) {
				switch (p_message.getSubtype()) {
				case LookupMessages.SUBTYPE_JOIN_REQUEST:
					incomingJoinRequest((JoinRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_GET_LOOKUP_RANGE_REQUEST:
					incomingGetLookupRangeRequest((GetLookupRangeRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_REMOVE_CHUNKIDS_REQUEST:
					incomingRemoveChunkIDsRequest((RemoveChunkIDsRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_INSERT_NAMESERVICE_ENTRIES_REQUEST:
					incomingInsertNameserviceEntriesRequest((InsertNameserviceEntriesRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_GET_CHUNKID_FOR_NAMESERVICE_ENTRY_REQUEST:
					incomingGetChunkIDForNameserviceEntryRequest((GetChunkIDForNameserviceEntryRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_GET_NAMESERVICE_ENTRY_COUNT_REQUEST:
					incomingGetNameserviceEntryCountRequest((GetNameserviceEntryCountRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_GET_NAMESERVICE_ENTRIES_REQUEST:
					incomingGetNameserviceEntriesRequest((GetNameserviceEntriesRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_MIGRATE_REQUEST:
					incomingMigrateRequest((MigrateRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_MIGRATE_RANGE_REQUEST:
					incomingMigrateRangeRequest((MigrateRangeRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_INIT_RANGE_REQUEST:
					incomingInitRangeRequest((InitRangeRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_GET_ALL_BACKUP_RANGES_REQUEST:
					incomingGetAllBackupRangesRequest((GetAllBackupRangesRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_SET_RESTORER_AFTER_RECOVERY_MESSAGE:
					incomingSetRestorerAfterRecoveryMessage((SetRestorerAfterRecoveryMessage) p_message);
					break;
				case LookupMessages.SUBTYPE_BARRIER_ALLOC_REQUEST:
					incomingBarrierAllocRequest((BarrierAllocRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_BARRIER_FREE_REQUEST:
					incomingBarrierFreeRequest((BarrierFreeRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_BARRIER_SIGN_ON_REQUEST:
					incomingBarrierSignOnRequest((BarrierSignOnRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_BARRIER_STATUS_REQUEST:
					incomingBarrierGetStatusRequest((BarrierGetStatusRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_BARRIER_CHANGE_SIZE_REQUEST:
					incomingBarrierChangeSizeRequest((BarrierChangeSizeRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_SUPERPEER_STORAGE_CREATE_REQUEST:
					incomingSuperpeerStorageCreateRequest((SuperpeerStorageCreateRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_SUPERPEER_STORAGE_GET_REQUEST:
					incomingSuperpeerStorageGetRequest((SuperpeerStorageGetRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_SUPERPEER_STORAGE_PUT_REQUEST:
					incomingSuperpeerStoragePutRequest((SuperpeerStoragePutRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_SUPERPEER_STORAGE_REMOVE_MESSAGE:
					incomingSuperpeerStorageRemoveMessage((SuperpeerStorageRemoveMessage) p_message);
					break;
				case LookupMessages.SUBTYPE_SUPERPEER_STORAGE_STATUS_REQUEST:
					incomingSuperpeerStorageStatusRequest((SuperpeerStorageStatusRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_GET_METADATA_SUMMARY_REQUEST:
					incomingGetMetadataSummaryRequest((GetMetadataSummaryRequest) p_message);
					break;
				case LookupMessages.SUBTYPE_NOTIFY_ABOUT_FAILED_PEER_REQUEST:
					incomingNotifyAboutFailedPeerRequest((NotifyAboutFailedPeerRequest) p_message);
					break;
				default:
					break;
				}
			}
		}
	}

	// -----------------------------------------------------------------------------------

	/**
	 * Register network messages we use in here.
	 */
	private void registerNetworkMessages() {
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE, LookupMessages.SUBTYPE_JOIN_REQUEST,
				JoinRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE, LookupMessages.SUBTYPE_JOIN_RESPONSE,
				JoinResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_LOOKUP_RANGE_REQUEST,
				GetLookupRangeRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_LOOKUP_RANGE_RESPONSE,
				GetLookupRangeResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_REMOVE_CHUNKIDS_REQUEST,
				RemoveChunkIDsRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_REMOVE_CHUNKIDS_RESPONSE,
				RemoveChunkIDsResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_INSERT_NAMESERVICE_ENTRIES_REQUEST,
				InsertNameserviceEntriesRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_INSERT_NAMESERVICE_ENTRIES_RESPONSE,
				InsertNameserviceEntriesResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_CHUNKID_FOR_NAMESERVICE_ENTRY_REQUEST,
				GetChunkIDForNameserviceEntryRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_CHUNKID_FOR_NAMESERVICE_ENTRY_RESPONSE,
				GetChunkIDForNameserviceEntryResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_NAMESERVICE_ENTRY_COUNT_REQUEST,
				GetNameserviceEntryCountRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_NAMESERVICE_ENTRY_COUNT_RESPONSE,
				GetNameserviceEntryCountResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_NAMESERVICE_ENTRIES_REQUEST,
				GetNameserviceEntriesRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_NAMESERVICE_ENTRIES_RESPONSE,
				GetNameserviceEntriesResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_NAMESERVICE_UPDATE_PEER_CACHES_MESSAGE,
				NameserviceUpdatePeerCachesMessage.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE, LookupMessages.SUBTYPE_MIGRATE_REQUEST,
				MigrateRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE, LookupMessages.SUBTYPE_MIGRATE_RESPONSE,
				MigrateResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_MIGRATE_RANGE_REQUEST,
				MigrateRangeRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_MIGRATE_RANGE_RESPONSE,
				MigrateRangeResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE, LookupMessages.SUBTYPE_INIT_RANGE_REQUEST,
				InitRangeRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_INIT_RANGE_RESPONSE,
				InitRangeResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_ALL_BACKUP_RANGES_REQUEST,
				GetAllBackupRangesRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_GET_ALL_BACKUP_RANGES_RESPONSE,
				GetAllBackupRangesResponse.class);

		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_SEND_BACKUPS_MESSAGE,
				SendBackupsMessage.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_NOTIFY_ABOUT_FAILED_PEER_REQUEST,
				NotifyAboutFailedPeerRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_NOTIFY_ABOUT_FAILED_PEER_RESPONSE,
				NotifyAboutFailedPeerResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_START_RECOVERY_MESSAGE,
				StartRecoveryMessage.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_SET_RESTORER_AFTER_RECOVERY_MESSAGE,
				SetRestorerAfterRecoveryMessage.class);

		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_PING_SUPERPEER_MESSAGE,
				PingSuperpeerMessage.class);

		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_SEND_SUPERPEERS_MESSAGE,
				SendSuperpeersMessage.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_ASK_ABOUT_BACKUPS_REQUEST,
				AskAboutBackupsRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_ASK_ABOUT_BACKUPS_RESPONSE,
				AskAboutBackupsResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_ASK_ABOUT_SUCCESSOR_REQUEST,
				AskAboutSuccessorRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_ASK_ABOUT_SUCCESSOR_RESPONSE,
				AskAboutSuccessorResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_NOTIFY_ABOUT_NEW_PREDECESSOR_MESSAGE,
				NotifyAboutNewPredecessorMessage.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_NOTIFY_ABOUT_NEW_SUCCESSOR_MESSAGE,
				NotifyAboutNewSuccessorMessage.class);

		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_BARRIER_ALLOC_REQUEST,
				BarrierAllocRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_BARRIER_ALLOC_RESPONSE,
				BarrierAllocResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_BARRIER_FREE_REQUEST,
				BarrierFreeRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_BARRIER_FREE_RESPONSE,
				BarrierFreeResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_BARRIER_SIGN_ON_REQUEST,
				BarrierSignOnRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_BARRIER_SIGN_ON_RESPONSE,
				BarrierSignOnResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_BARRIER_RELEASE_MESSAGE,
				BarrierReleaseMessage.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_BARRIER_STATUS_REQUEST,
				BarrierGetStatusRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_BARRIER_STATUS_RESPONSE,
				BarrierGetStatusResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_BARRIER_CHANGE_SIZE_REQUEST,
				BarrierChangeSizeRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_BARRIER_CHANGE_SIZE_RESPONSE,
				BarrierChangeSizeResponse.class);

		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_SUPERPEER_STORAGE_CREATE_REQUEST,
				SuperpeerStorageCreateRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_SUPERPEER_STORAGE_CREATE_RESPONSE,
				SuperpeerStorageCreateResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_SUPERPEER_STORAGE_GET_REQUEST,
				SuperpeerStorageGetRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_SUPERPEER_STORAGE_GET_RESPONSE,
				SuperpeerStorageGetResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_SUPERPEER_STORAGE_PUT_REQUEST,
				SuperpeerStoragePutRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_SUPERPEER_STORAGE_PUT_RESPONSE,
				SuperpeerStoragePutResponse.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_SUPERPEER_STORAGE_REMOVE_MESSAGE,
				SuperpeerStorageRemoveMessage.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_SUPERPEER_STORAGE_STATUS_REQUEST,
				SuperpeerStorageStatusRequest.class);
		m_network.registerMessageType(DXRAMMessageTypes.LOOKUP_MESSAGES_TYPE,
				LookupMessages.SUBTYPE_SUPERPEER_STORAGE_STATUS_RESPONSE,
				SuperpeerStorageStatusResponse.class);
	}

	/**
	 * Register network messages we want to listen to in here.
	 */
	private void registerNetworkMessageListener() {
		m_network.register(JoinRequest.class, this);
		m_network.register(GetLookupRangeRequest.class, this);
		m_network.register(RemoveChunkIDsRequest.class, this);
		m_network.register(InsertNameserviceEntriesRequest.class, this);
		m_network.register(GetChunkIDForNameserviceEntryRequest.class, this);
		m_network.register(GetNameserviceEntryCountRequest.class, this);
		m_network.register(GetNameserviceEntriesRequest.class, this);
		m_network.register(MigrateRequest.class, this);
		m_network.register(MigrateRangeRequest.class, this);
		m_network.register(InitRangeRequest.class, this);
		m_network.register(GetAllBackupRangesRequest.class, this);
		m_network.register(SetRestorerAfterRecoveryMessage.class, this);

		m_network.register(PingSuperpeerMessage.class, this);

		m_network.register(BarrierAllocRequest.class, this);
		m_network.register(BarrierFreeRequest.class, this);
		m_network.register(BarrierSignOnRequest.class, this);
		m_network.register(BarrierGetStatusRequest.class, this);
		m_network.register(BarrierChangeSizeRequest.class, this);

		m_network.register(SuperpeerStorageCreateRequest.class, this);
		m_network.register(SuperpeerStorageGetRequest.class, this);
		m_network.register(SuperpeerStoragePutRequest.class, this);
		m_network.register(SuperpeerStorageRemoveMessage.class, this);
		m_network.register(SuperpeerStorageStatusRequest.class, this);

		m_network.register(GetMetadataSummaryRequest.class, this);

		m_network.register(NotifyAboutFailedPeerRequest.class, this);
	}
}
