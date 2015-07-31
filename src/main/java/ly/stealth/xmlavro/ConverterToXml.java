package ly.stealth.xmlavro;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.avro.generic.GenericContainer;
import org.apache.avro.generic.GenericRecord;
import org.apache.xerces.dom.DOMInputImpl;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.xs.XMLSchemaLoader;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLErrorHandler;
import org.apache.xerces.xni.parser.XMLParseException;
import org.apache.xerces.xs.StringList;
import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSAttributeUse;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSModelGroup;
import org.apache.xerces.xs.XSNamedMap;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSParticle;
import org.apache.xerces.xs.XSTerm;
import org.apache.xerces.xs.XSTypeDefinition;
import org.w3c.dom.DOMError;
import org.w3c.dom.DOMErrorHandler;
import org.w3c.dom.DOMLocator;
import org.w3c.dom.ls.LSInput;

public class ConverterToXml {
	private XSModel model;

	private static Map<Character, String> ESCAPE = new HashMap<Character, String>();
	static {
		ESCAPE.put('&', "&amp;&");
		ESCAPE.put('<', "&lt;");
		ESCAPE.put('>', "&gt;");
		ESCAPE.put('"', "&quot;");
		ESCAPE.put('\'', "&apos;");
	}

	private Map<String, String> namespace = new HashMap<String, String>();

	public ConverterToXml(String xsd) {
		createSchema(new StringReader(xsd));
	}

	public ConverterToXml(File file) throws ConverterException {
		try (InputStream stream = new FileInputStream(file)) {
			createSchema(stream);
		} catch (IOException e) {
			throw new ConverterException(e);
		}
	}

	public ConverterToXml(Reader reader) {
		DOMInputImpl input = new DOMInputImpl();
		input.setCharacterStream(reader);
		createSchema(input);
	}

	public ConverterToXml(InputStream stream) {
		DOMInputImpl input = new DOMInputImpl();
		input.setByteStream(stream);
		createSchema(input);
	}

	public ConverterToXml(LSInput input) {
		createSchema(input);
	}

	public void createSchema(InputStream stream) {
		DOMInputImpl input = new DOMInputImpl();
		input.setByteStream(stream);
		createSchema(input);
	}

	public void createSchema(Reader reader) {
		DOMInputImpl input = new DOMInputImpl();
		input.setCharacterStream(reader);
		createSchema(input);
	}

	public void createSchema(LSInput input) {
		ErrorHandler errorHandler = new ErrorHandler();

		XMLSchemaLoader loader = new XMLSchemaLoader();
//		if (resolver != null)
		loader.setEntityResolver(new EntityResolver(new BaseDirResolver("/home/simon/workspace/hobbes/hobbesXml/schema/")));

		loader.setErrorHandler(errorHandler);
		loader.setParameter(Constants.DOM_ERROR_HANDLER, errorHandler);

		model = loader.load(input);

		errorHandler.throwExceptionIfHasError();
		
		StringList namespaces = model.getNamespaces();
		for (int i = 0; i < namespaces.size(); i++) {
			namespace.put(namespaces.get(i).toString(), "ns" + i++);
		}
	}

	Pattern p = Pattern.compile("(?:\\{(.*)\\}|@)?(.*)");

	public String convert(GenericContainer obj) {
		XSNamedMap components = model.getComponents(XSConstants.ELEMENT_DECLARATION);
		String prop = obj.getSchema().getProp(Source.SOURCE);
		System.out.println(prop);
		Matcher m = p.matcher(prop);
		if (m.matches()) {
			String ns = null;
			if (m.groupCount() > 1) {
				if ("@".equals(m.group(1))) {
				} // TODO
				ns = m.group(1);
			}
			String name = m.group(m.groupCount());

			System.out.println("name: '" + name + "' ns: '" + ns + "'");
			XSElementDeclaration t = (XSElementDeclaration) components.itemByName(ns, name);

			StringBuilder sb = new StringBuilder();
			appendElems(obj, t, sb);

			int i = t.getName().length() + namespace.get(t.getNamespace()).length() + 2;
			StringBuilder nssb = new StringBuilder();
			for (Map.Entry<String, String> e : namespace.entrySet()) {
				nssb.append(" xmlns:").append(e.getValue()).append("=\"");
				appendEscapeString(nssb, e.getKey());
				nssb.append('"');
			}
			sb.insert(i, nssb.toString());
			return sb.toString();
		}
		return null;
	}

	private void appendElems(Object o, XSElementDeclaration el, StringBuilder sb) {
		if (o instanceof List) {
			for (Object v : (List<?>) o) {
				appendElems(v, el, sb);
			}
		} else {
			XSTypeDefinition type = el.getTypeDefinition();

			String ns = namespace.get(el.getNamespace());

			sb.append('<').append(ns).append(':').append(el.getName());
			if (type.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) {
				if (o == null)
					sb.append("/>");
				else
					sb.append("><![CDATA[").append(o).append("]]></").append(ns).append(':').append(el.getName()).append('>');
			} else {
				if (appendComplexType((GenericRecord) o, (XSComplexTypeDefinition) type, sb)) {
					sb.append("</").append(ns).append(':').append(el.getName()).append('>');
				}
			}
		}
	}

	private boolean appendComplexType(GenericRecord rec, XSComplexTypeDefinition type, StringBuilder sb) {
		XSObjectList attrUses = type.getAttributeUses();
		for (int i = 0; i < attrUses.getLength(); i++) {
			XSAttributeUse attrUse = (XSAttributeUse) attrUses.item(i);
			XSAttributeDeclaration decl = attrUse.getAttrDeclaration();
			Object v = rec.get(decl.getName());
			if (v != null) {
				sb.append(' ').append(decl.getName()).append("=\"");
				appendEscapeString(sb, String.valueOf(v));
				sb.append('"');
			} // TODO xsi:nil
		}

		XSParticle particle = type.getParticle();
		if (particle != null) {
			sb.append(">");
			XSTerm term = particle.getTerm();
			if (term.getType() != XSConstants.MODEL_GROUP)
				throw new ConverterException("Unsupported term type " + term.getType());

			XSModelGroup group = (XSModelGroup) term;
			appendGroup(rec, group, sb);
			return true;
		} else {
			sb.append("/>");
			return false;
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

	private void appendGroup(GenericRecord rec, XSModelGroup group, StringBuilder sb) {
		XSObjectList particles = group.getParticles();

		for (int j = 0; j < particles.getLength(); j++) {
			XSParticle particle = (XSParticle) particles.item(j);
			XSTerm term = particle.getTerm();

			switch (term.getType()) {
			case XSConstants.ELEMENT_DECLARATION:
				XSElementDeclaration el = (XSElementDeclaration) term;
				Object v = rec.get(el.getName());
				if (v != null) {
					appendElems(v, el, sb);
				}
				break;
			case XSConstants.MODEL_GROUP:
				XSModelGroup subGroup = (XSModelGroup) term;
				appendGroup(rec, subGroup, sb);
				break;
			case XSConstants.WILDCARD:
				// XXX ???
				break;
			default:
				throw new ConverterException("Unsupported term type " + term.getType());
			}
		}
	}

	private void appendArray(GenericContainer v, XSElementDeclaration el, StringBuilder sb) {

	}

	private static class ErrorHandler implements XMLErrorHandler, DOMErrorHandler {
		XMLParseException exception;
		DOMError error;

		@Override
		public void warning(String domain, String key, XMLParseException exception) throws XNIException {
			if (this.exception == null)
				this.exception = exception;
		}

		@Override
		public void error(String domain, String key, XMLParseException exception) throws XNIException {
			if (this.exception == null)
				this.exception = exception;
		}

		@Override
		public void fatalError(String domain, String key, XMLParseException exception) throws XNIException {
			if (this.exception == null)
				this.exception = exception;
		}

		@Override
		public boolean handleError(DOMError error) {
			if (this.error == null)
				this.error = error;
			return false;
		}

		void throwExceptionIfHasError() {
			if (exception != null)
				throw new ConverterException(exception);

			if (error != null) {
				if (error.getRelatedException() instanceof Throwable)
					throw new ConverterException((Throwable) error.getRelatedException());

				DOMLocator locator = error.getLocation();
				String location = "at:" + locator.getUri() + ", line:" + locator.getLineNumber() + ", char:" + locator.getColumnNumber();
				throw new ConverterException(location + " " + error.getMessage());
			}
		}
	}
}
