
package de.hhu.bsinfo.dxram.test;

import de.uniduesseldorf.dxram.core.dxram.Core;

import de.hhu.bsinfo.dxram.data.Chunk;
import de.hhu.bsinfo.dxram.engine.DXRAMException;
import de.hhu.bsinfo.dxram.engine.nodeconfig.NodesConfigurationHandler;
import de.hhu.bsinfo.utils.config.ConfigurationHandler;

/**
 * Test case for the Chunk handling
 * @author Florian Klein
 *         10.07.2013
 */
public final class ChunkTest {

	// Constants
	private static final int CHUNK_SIZE = 1024 * 1024;

	// Constructors
	/**
	 * Creates an instance of ChunkTest
	 */
	private ChunkTest() {}

	// Methods
	/**
	 * Program entry point
	 * @param p_arguments
	 *            The program arguments
	 */
	public static void main(final String[] p_arguments) {
		Chunk putChunk;
		Chunk getChunk;

		// Initialize EPM
		Core.initialize(ConfigurationHandler.getDefaultConfiguration(), NodesConfigurationHandler.getLocalConfiguration());

		try {
			putChunk = Core.createNewChunk(CHUNK_SIZE);
			System.out.println(putChunk);

			Core.put(putChunk);
			System.out.println(putChunk);
			Core.put(putChunk);
			System.out.println(putChunk);

			getChunk = Core.get(putChunk.getChunkID());
			System.out.println(getChunk);

			System.out.println(putChunk.equals(getChunk));

			Core.removeChunk(putChunk.getChunkID());
			System.out.println(Core.get(putChunk.getChunkID()));
		} catch (final DXRAMException e) {
			e.printStackTrace();
		}
	}

}