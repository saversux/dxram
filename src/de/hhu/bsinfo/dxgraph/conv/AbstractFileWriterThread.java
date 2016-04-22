
package de.hhu.bsinfo.dxgraph.conv;

/**
 * Thread writing the buffered data and converting it to the output file format.
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 24.02.16
 */
public abstract class AbstractFileWriterThread extends ConverterThread {
	protected String m_outputPath;
	protected int m_id = -1;
	protected long m_idRangeStartIncl = -1;
	protected long m_idRangeEndExcl = -1;
	protected VertexStorage m_storage;

	/**
	 * Constructor
	 * @param p_outputPath
	 *            Output file to write to.
	 * @param p_id
	 *            Id of the writer (0 based index).
	 * @param p_idRangeStartIncl
	 *            Range of vertex ids to write to the file, start.
	 * @param p_idRangeEndExcl
	 *            Range of the vertex ids to write the file, end.
	 * @param p_storage
	 *            Storage to access for vertex data to write to the file.
	 */
	public AbstractFileWriterThread(final String p_outputPath, final int p_id, final long p_idRangeStartIncl,
			final long p_idRangeEndExcl, final VertexStorage p_storage) {
		super("BinaryFileWriter " + p_id);

		m_outputPath = p_outputPath;
		m_id = p_id;
		m_idRangeStartIncl = p_idRangeStartIncl;
		m_idRangeEndExcl = p_idRangeEndExcl;
		m_storage = p_storage;
	}

	@Override
	public abstract void run();
}
