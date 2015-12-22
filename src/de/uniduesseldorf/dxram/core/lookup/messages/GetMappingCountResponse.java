package de.uniduesseldorf.dxram.core.lookup.messages;

import java.nio.ByteBuffer;

import de.uniduesseldorf.menet.AbstractResponse;

/**
 * Response to a GetMappingCountRequest
 * @author klein 26.03.2015
 */
public class GetMappingCountResponse extends AbstractResponse {

	// Attributes
	private long m_count;

	// Constructors
	/**
	 * Creates an instance of GetMappingCountResponse
	 */
	public GetMappingCountResponse() {
		super();

		m_count = 0;
	}

	/**
	 * Creates an instance of GetMappingCountResponse
	 * @param p_request
	 *            the request
	 * @param p_count
	 *            the count
	 */
	public GetMappingCountResponse(final GetMappingCountRequest p_request, final long p_count) {
		super(p_request, LookupMessages.SUBTYPE_GET_MAPPING_COUNT_RESPONSE);

		m_count = p_count;
	}

	// Getters
	/**
	 * Get the count
	 * @return the count
	 */
	public final long getCount() {
		return m_count;
	}

	// Methods
	@Override
	protected final void writePayload(final ByteBuffer p_buffer) {
		p_buffer.putLong(m_count);
	}

	@Override
	protected final void readPayload(final ByteBuffer p_buffer) {
		m_count = p_buffer.getLong();
	}

	@Override
	protected final int getPayloadLengthForWrite() {
		return Long.BYTES;
	}

}