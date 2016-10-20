
package de.hhu.bsinfo.utils.reflect.dt;

/**
 * Implementation of a byte parser.
 * @author Stefan Nothaas <stefan.nothaas@hhu.de> 26.01.16
 */
public class DataTypeParserByte implements DataTypeParser {
	@Override
	public java.lang.String getTypeIdentifer() {
		return "byte";
	}

	@Override
	public Class<?> getClassToConvertTo() {
		return Byte.class;
	}

	@Override
	public Object parse(final java.lang.String p_str) {
		try {
			if (p_str.length() > 1) {
				String tmp = p_str.substring(0, 2);
				if (tmp.equals("0x")) {
					return (byte) Integer.parseInt(p_str.substring(2), 16);
				} else if (tmp.equals("0b")) {
					return (byte) Integer.parseInt(p_str.substring(2), 2);
				} else if (tmp.equals("0o")) {
					return (byte) Integer.parseInt(p_str.substring(2), 8);
				}
			}

			return java.lang.Byte.parseByte(p_str, 10);
		} catch (final NumberFormatException e) {
			return new java.lang.Byte((byte) 0);
		}
	}
}
