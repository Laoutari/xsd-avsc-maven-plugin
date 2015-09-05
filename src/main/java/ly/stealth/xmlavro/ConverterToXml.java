package ly.stealth.xmlavro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.generic.GenericRecord;

public class ConverterToXml {
	private static Map<Character, String> ESCAPE = new HashMap<Character, String>();
	static {
		ESCAPE.put('&', "&amp;&");
		ESCAPE.put('<', "&lt;");
		ESCAPE.put('>', "&gt;");
		ESCAPE.put('"', "&quot;");
		ESCAPE.put('\'', "&apos;");
	}

	private Map<String, String> namespace = new HashMap<String, String>();

	public ConverterToXml() {
	}

	public String convert(GenericContainer obj) {
		String prop = obj.getSchema().getProp(Source.SOURCE);
		Source s = Source.build(prop);

		StringBuilder sb = new StringBuilder();
		appendElems(obj, s, obj.getSchema(), sb);

		int i = s.getName().length() + namespace.get(s.getNs()).length() + 2;
		StringBuilder nssb = new StringBuilder();
		for (Map.Entry<String, String> e : namespace.entrySet()) {
			nssb.append(" xmlns:").append(e.getValue()).append("=\"");
			appendEscapeString(nssb, e.getKey());
			nssb.append('"');
		}
		sb.insert(i, nssb.toString());
		return sb.toString();
	}

	private String getNs(String n) {
		if (n == null)
			return null;
		synchronized (namespace) {
			String ns = namespace.get(n);
			if (ns == null) {
				ns = "ns" + namespace.size();
				namespace.put(n, ns);
			}
			return ns;
		}
	}

	private void appendElems(Object o, Source s, Schema schema, StringBuilder sb) {
		String ns = getNs(s.getNs());

		if (o == null) {
			return;
		}

		switch (schema.getType()) {
			case ARRAY:
				schema = schema.getElementType();
				if (o instanceof List) {
					for (Object v : (List<?>) o) {
						appendElems(v, s, schema, sb);
					}
				} else {
					throw new ConverterException("expecting array but found: " + o);
				}
				break;
			case UNION:
				appendElems(o, s, schema.getTypes().get(1), sb);
				break;
			case RECORD:
				GenericRecord r = (GenericRecord) o;
				List<FieldSource> arg = new ArrayList<FieldSource>();
				List<FieldSource> child = new ArrayList<FieldSource>();
				for (Field f : schema.getFields()) {
					Source sf = Source.build(f.getProp(Source.SOURCE));
					if (sf.isAttribute())
						arg.add(new FieldSource(sf, f));
					else
						child.add(new FieldSource(sf, f));
				}
				sb.append('<');
				if (ns != null)
					sb.append(ns).append(':');
				sb.append(s.getName());
				for (FieldSource fs : arg) {
					Object object = r.get(fs.f.pos());
					if (object != null) {
						sb.append(" ");
//						if (fs.s.getNs() != null && !fs.s.getNs().equals(s.getNs()))
//							sb.append(getNs(fs.s.getNs())).append(':');
						sb.append(fs.s.getName()).append("=\"");
						appendEscapeString(sb, object.toString());
						sb.append('"');
					}
				}

				if (!child.isEmpty()) {
					sb.append('>');
					for (FieldSource fs : child) {
						appendElems(r.get(fs.f.pos()), fs.s, fs.f.schema(), sb);
					}
					sb.append("</");
					if (ns != null)
						sb.append(ns).append(':');
					sb.append(s.getName()).append('>');
				} else {
					sb.append("/>");
				}
				break;
			default:
				sb.append('<');
				if (ns != null)
					sb.append(ns).append(':');
				sb.append(s.getName()).append('>');
				appendEscapeString(sb, o.toString());
				sb.append("</");
				if (ns != null)
					sb.append(ns).append(':');
				sb.append(s.getName()).append('>');
				break;
		}
	}

	private void appendEscapeString(StringBuilder sb, CharSequence s) {
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (ESCAPE.containsKey(c))
				sb.append(ESCAPE.get(c));
			else
				sb.append(c);
		}
	}

	private static class FieldSource {
		public Source s;
		public Field f;

		public FieldSource(Source s, Field f) {
			this.s = s;
			this.f = f;
		}
	}
}
