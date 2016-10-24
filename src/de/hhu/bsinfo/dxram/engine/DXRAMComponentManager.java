package de.hhu.bsinfo.dxram.engine;

import java.util.HashMap;
import java.util.Map;

import de.hhu.bsinfo.dxram.backup.BackupComponent;
import de.hhu.bsinfo.dxram.boot.ZookeeperBootComponent;
import de.hhu.bsinfo.dxram.chunk.ChunkComponent;
import de.hhu.bsinfo.dxram.event.EventComponent;
import de.hhu.bsinfo.dxram.failure.FailureComponent;
import de.hhu.bsinfo.dxram.lock.PeerLockComponent;
import de.hhu.bsinfo.dxram.log.LogComponent;
import de.hhu.bsinfo.dxram.logger.LoggerComponent;
import de.hhu.bsinfo.dxram.lookup.LookupComponent;
import de.hhu.bsinfo.dxram.mem.MemoryManagerComponent;
import de.hhu.bsinfo.dxram.nameservice.NameserviceComponent;
import de.hhu.bsinfo.dxram.net.NetworkComponent;
import de.hhu.bsinfo.dxram.script.ScriptEngineComponent;
import de.hhu.bsinfo.dxram.stats.StatisticsComponent;
import de.hhu.bsinfo.dxram.term.TerminalComponent;

/**
 * Manager for all components in DXRAM.
 * All components used in DXRAM must be registered here to create a default configuration with all
 * components listed.
 *
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 21.10.16
 */
class DXRAMComponentManager {

	private static Map<String, Class<? extends AbstractDXRAMComponent>> m_registeredComponents = new HashMap<>();

	/**
	 * Static class
	 */
	private DXRAMComponentManager() {

	}

	/**
	 * Register a component
	 *
	 * @param p_class Component class to register
	 */
	public static void register(final Class<? extends AbstractDXRAMComponent> p_class) {
		m_registeredComponents.put(p_class.getName(), p_class);
	}

	/**
	 * Register all DXRAM components
	 */
	static void registerDefault() {
		register(BackupComponent.class);
		register(ZookeeperBootComponent.class);
		register(ChunkComponent.class);
		register(EventComponent.class);
		register(FailureComponent.class);
		register(PeerLockComponent.class);
		register(LogComponent.class);
		register(LoggerComponent.class);
		register(LookupComponent.class);
		register(MemoryManagerComponent.class);
		register(NameserviceComponent.class);
		register(NetworkComponent.class);
		register(NullComponent.class);
		register(ScriptEngineComponent.class);
		register(StatisticsComponent.class);
		register(TerminalComponent.class);
	}

	/**
	 * Create an instance of a component
	 *
	 * @param p_className Fully qualified name of the class (incl. package path)
	 * @return Instance of the component
	 */
	static AbstractDXRAMComponent createInstance(final String p_className) {

		Class<? extends AbstractDXRAMComponent> clazz = m_registeredComponents.get(p_className);

		try {
			return clazz.getConstructor().newInstance();
		} catch (final Exception e) {
			throw new RuntimeException("Cannot create component instance of " + clazz.getName(), e);
		}
	}

	/**
	 * Create instances of all registered components
	 *
	 * @return List of instances of all registered components
	 */
	static AbstractDXRAMComponent[] createAllInstances() {
		AbstractDXRAMComponent[] instances = new AbstractDXRAMComponent[m_registeredComponents.size()];
		int index = 0;

		for (Class<? extends AbstractDXRAMComponent> clazz : m_registeredComponents.values()) {
			try {
				instances[index++] = clazz.getConstructor().newInstance();
			} catch (final Exception e) {
				throw new RuntimeException("Cannot create component instance of " + clazz.getName(), e);
			}
		}

		return instances;
	}
}