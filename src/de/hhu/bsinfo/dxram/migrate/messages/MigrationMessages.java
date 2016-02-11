package de.hhu.bsinfo.dxram.migrate.messages;

/**
 * Different migration message types.
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 26.01.16
 */
public class MigrationMessages {
	public static final byte TYPE = 6;
	public static final byte SUBTYPE_MIGRATION_REQUEST = 1;
	public static final byte SUBTYPE_MIGRATION_RESPONSE = 2;
	public static final byte SUBTYPE_MIGRATION_MESSAGE = 3;
}
