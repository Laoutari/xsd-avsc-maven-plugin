package ly.stealth.xmlavro;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Source {
	public static final String SOURCE = "source";
	public static final String DOCUMENT = "document";
	public static final String WILDCARD = "others";

	private static final Pattern p = Pattern.compile("(@?)(?:\\{(.*)\\})?(.*)");

	// name of element/attribute
	private String name;
	private String ns;
	// element or attribute
	private boolean attribute;

	public Source(String name, String ns) {
		this(name, ns, false);
	}

	public Source(String name, boolean attribute) {
		this(name, null, attribute);
	}

	public Source(String name, String ns, boolean attribute) {
		this.name = name;
		this.ns = ns;
		this.attribute = attribute;
	}

	public String getName() {
		return name;
	}

	public String getNs() {
		return ns;
	}

	public boolean isElement() {
		return !isAttribute();
	}

	public boolean isAttribute() {
		return attribute;
	}

	public int hashCode() {
		return Objects.hash(name, attribute);
	}

	public boolean equals(Object obj) {
		if (!(obj instanceof Source))
			return false;
		Source source = (Source) obj;
		return name.equals(source.name) && attribute == source.attribute;
	}

	public String toString() {
		return (attribute ? "@" : (ns != null ? "{" + ns + "}" : "")) + name;
	}

	public static Source build(String prop) {
		Matcher m = p.matcher(prop);
		if (!m.matches()) {
			return null;
		}
		return new Source(m.group(3), m.group(2), "@".equals(m.group(1)));
	}
}
