package de.hhu.bsinfo.dxram.lock;

import com.google.gson.annotations.Expose;

import de.hhu.bsinfo.dxram.engine.DXRAMContext;
import de.hhu.bsinfo.dxram.engine.DXRAMServiceConfig;
import de.hhu.bsinfo.utils.unit.TimeUnit;

/**
 * Config for the PeerLockService
 *
 * @author Stefan Nothaas, stefan.nothaas@hhu.de, 24.05.2017
 */
public class PeerLockServiceConfig extends DXRAMServiceConfig {
    @Expose
    private TimeUnit m_remoteLockSendInterval = new TimeUnit(10, TimeUnit.MS);

    @Expose
    private TimeUnit m_remoteLockTryTimeout = new TimeUnit(100, TimeUnit.MS);

    /**
     * Constructor
     */
    public PeerLockServiceConfig() {
        super(PeerLockService.class, false, true);
    }

    /**
     * Message frequency for acquiring a remote lock
     */
    public TimeUnit getRemoteLockSendInterval() {
        return m_remoteLockSendInterval;
    }

    /**
     * Timeout when trying to acquire a remote lock
     */
    public TimeUnit getRemoteLockTryTimeout() {
        return m_remoteLockTryTimeout;
    }

    @Override
    protected boolean verify(final DXRAMContext.Config p_config) {
        return true;
    }
}
