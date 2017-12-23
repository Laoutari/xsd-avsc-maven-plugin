package ly.stealth.xmlavro;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import ly.stealth.xmlavro.api.Api;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.IndexedRecord;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class DatumBuilder {
	public static Element parse(InputSource source) {
		try {
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
			builderFactory.setNamespaceAware(true);

			DocumentBuilder builder = builderFactory.newDocumentBuilder();
			Document doc = builder.parse(source);
			return doc.getDocumentElement();
		} catch (ParserConfigurationException | SAXException | IOException e) {
			throw new ConverterException(e);
		}
	}

	private static final List<Schema.Type> PRIMITIVES;
	static {
		PRIMITIVES = Arrays.asList(Schema.Type.STRING, Schema.Type.INT, Schema.Type.LONG, Schema.Type.FLOAT, Schema.Type.DOUBLE,
				Schema.Type.BOOLEAN, Schema.Type.NULL);
	}

	private static TimeZone defaultTimeZone = TimeZone.getTimeZone("UTC-0");

	public static void setDefaultTimeZone(TimeZone timeZone) {
		defaultTimeZone = timeZone;
	}

	public static TimeZone getDefaultTimeZone() {
		return defaultTimeZone;
	}

	private boolean caseSensitiveNames = true;

	public DatumBuilder() {
	}

	public boolean isCaseSensitiveNames() {
		return caseSensitiveNames;
	}

	public void setCaseSensitiveNames(boolean caseSensitiveNames) {
		this.caseSensitiveNames = caseSensitiveNames;
	}

	public <T> T createDatum(Schema schema, String xml, boolean specific) {
		try {
			return createDatum(schema, new StringReader(xml), specific);
		} catch (ConverterException e) {
			throw e;
		} catch (Exception e) {
			throw new ConverterException(e);
		}
	}

	public <T> T createDatum(Schema schema, File file, boolean specific) {
		try (InputStream stream = new FileInputStream(file)) {
			return createDatum(schema, stream, specific);
		} catch (ConverterException e) {
			throw e;
		} catch (Exception e) {
			throw new ConverterException(e);
		}
	}

	public <T> T createDatum(Schema schema, Reader reader, boolean specific) {
		try {
			return createDatum(schema, new InputSource(reader), specific);
		} catch (ConverterException e) {
			throw e;
		} catch (Exception e) {
			throw new ConverterException(e);
		}
	}

	public <T> T createDatum(Schema schema, InputStream stream, boolean specific) {
		try {
			return createDatum(schema, new InputSource(stream), specific);
		} catch (ConverterException e) {
			throw e;
		} catch (Exception e) {
			throw new ConverterException(e);
		}
	}

	public <T> T createDatum(Schema schema, InputSource source, boolean specific) {
		try {
			return createDatum(schema, parse(source), specific);
		} catch (ConverterException e) {
			throw e;
		} catch (Exception e) {
			throw new ConverterException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T createDatum(Schema schema, Element el, boolean specific) {
		try {
			return (T) createNodeDatum(schema, el, false, specific);
		} catch (ConverterException e) {
			throw e;
		} catch (Exception e) {
			throw new ConverterException(e);
		}
	}

	private Object createNodeDatum(Schema schema, Node source, boolean setRecordFromNode, boolean specific) throws InstantiationException, IllegalAccessException,
			ClassNotFoundException, NoSuchMethodException, SecurityException {
		if (!Arrays.asList(Node.ELEMENT_NODE, Node.ATTRIBUTE_NODE).contains(source.getNodeType()))
			throw new IllegalArgumentException("Unsupported node type " + source.getNodeType());

		if (PRIMITIVES.contains(schema.getType()))
			return createValue(schema.getType(), source.getTextContent());

		if (schema.getType() == Schema.Type.UNION)
			return createUnionDatum(schema, source, specific);

		if (schema.getType() == Schema.Type.RECORD)
			return createRecord(schema, (Element) source, setRecordFromNode, specific);

		if (schema.getType() == Schema.Type.ARRAY)
			return createArray(schema, (Element) source, specific);

		if (schema.getType() == Schema.Type.ENUM)
			return createEnum(schema, source, specific);

		throw new ConverterException("Unsupported schema type " + schema.getType());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object createEnum(Schema schema, Node source, boolean specific) throws ClassNotFoundException, NoSuchMethodException, SecurityException {
		if (specific) {
			return Enum.valueOf((Class<? extends Enum>) getClass(schema), Api.removeSymbols(source));
		} else {
			return new GenericData.EnumSymbol(schema, Api.removeSymbols(source));
		}
	}

	private Object createArray(Schema schema, Element el, boolean specific) throws InstantiationException, IllegalAccessException, ClassNotFoundException,
			NoSuchMethodException, SecurityException {
		NodeList childNodes = el.getChildNodes();
		Schema elementType = schema.getElementType();
		int numElements = childNodes.getLength();
		List<Object> array;
		if (specific) {
			array = new ArrayList<Object>(numElements);
		} else {
			array = new GenericData.Array<Object>(numElements, schema);
		}

		for (int i = 0; i < numElements; i++) {
			Element child = (Element) childNodes.item(i);
			//noinspection unchecked
			array.add(createNodeDatum(elementType, child, true, specific));
		}
		return array;
	}

	private Object createUnionDatum(Schema union, Node source, boolean specific) throws InstantiationException, IllegalAccessException, ClassNotFoundException,
			NoSuchMethodException, SecurityException {
		List<Schema> types = union.getTypes();

		boolean optionalNode = types.size() == 2 && types.get(0).getType() == Schema.Type.NULL;
		if (!optionalNode) {
			GenericData.Record gRecord = null;
			boolean skipFirst = (types.get(0).getType() == Schema.Type.NULL);

			for (Schema schema: types) {
				try {
					if (skipFirst) {
						skipFirst = false;
					} else {
						GenericData.Record datum = (GenericData.Record) createNodeDatum(schema, source, false, false);
						boolean ok = true;
						for (Schema.Field field : schema.getFields()) {
							if ((!((field.schema().getType() == Schema.Type.UNION) && (field.schema().getTypes().get(0).getType() == Schema.Type.NULL))) && (datum.get(field.name()) == null)) {
								ok = false;
							}
						}

						if (ok) gRecord = datum;
					}
				} catch (Throwable t) {
				}
			}

			return gRecord;
		}

		return createNodeDatum(types.get(1), source, false, false);
	}

	private Object createValue(Schema.Type type, String text) {
		if (type == Schema.Type.BOOLEAN)
			return "true".equals(text) || "1".equals(text);

		if (type == Schema.Type.INT)
			return Integer.parseInt(text);

		if (type == Schema.Type.LONG)
			return DateFormat.parseAnything(text);

		if (type == Schema.Type.FLOAT)
			return Float.parseFloat(text);

		if (type == Schema.Type.DOUBLE)
			return Double.parseDouble(text);

		if (type == Schema.Type.STRING)
			return text;

		throw new ConverterException("Unsupported type " + type);
	}

	Map<Schema, Class<?>> classCache = new HashMap<Schema, Class<?>>();

	public Class<?> getClass(Schema schema) throws ClassNotFoundException {
		Class<?> clazz = classCache.get(schema);
		if (clazz != null)
			return clazz;
		String namespace = schema.getNamespace();
		String name = schema.getName();
		return Class.forName(namespace == null ? name : namespace + "." + name);
	}

	private Object createRecord(Schema schema, Element el, boolean setRecordFieldFromNode, boolean specific) throws InstantiationException, IllegalAccessException,
			ClassNotFoundException, NoSuchMethodException, SecurityException {
		IndexedRecord record;
		if (specific) {
			Class<?> clazz = getClass(schema);
			record = (IndexedRecord) clazz.newInstance();
		} else {
			record = new GenericData.Record(schema);
		}

		// initialize arrays and wildcard maps
		for (Schema.Field field : record.getSchema().getFields()) {
			if (field.schema().getType() == Schema.Type.ARRAY)
				record.put(field.pos(), new ArrayList<>());

			if (field.name().equals(Source.WILDCARD))
				record.put(field.pos(), new HashMap<String, Object>());
		}

		boolean rootRecord = Source.DOCUMENT.equals(schema.getProp(Source.SOURCE));

		if (setRecordFieldFromNode) {
			setFieldFromNode(schema, record, el, specific);
		} else {
			NodeList nodes = rootRecord ? el.getOwnerDocument().getChildNodes() : el.getChildNodes();
			for (int i = 0; i < nodes.getLength(); i++) {
				setFieldFromNode(schema, record, nodes.item(i), specific);
			}
		}

		if (!rootRecord) {
			NamedNodeMap attrMap = el.getAttributes();
			for (int i = 0; i < attrMap.getLength(); i++) {
				Attr attr = (Attr) attrMap.item(i);

				List<String> ignoredNamespaces = Arrays.asList("http://www.w3.org/2000/xmlns/", "http://www.w3.org/2001/XMLSchema-instance");
				if (ignoredNamespaces.contains(attr.getNamespaceURI()))
					continue;

				List<String> ignoredNames = Arrays.asList("xml:lang");
				if (ignoredNames.contains(attr.getName()))
					continue;

				if (!setRecordFieldFromNode) {
					Schema.Field field = getFieldBySource(schema, new Source(attr.getName(), attr.getNamespaceURI(), true));
					if (field == null)
						throw new ConverterException("Unsupported attribute " + attr.getName());

					Object datum = createNodeDatum(field.schema(), attr, false, specific);
					record.put(field.pos(), datum);
				}
			}
		}

		// Key value field
		if (!specific) {
			Schema.Field textField = schema.getField(SchemaBuilder.KEY_VALUE_FIELD_NAME);
			if (textField != null) {
				Object datum = createNodeDatum(textField.schema(), el, false, specific);
				GenericData.Record gRecord = (GenericData.Record) record;
				gRecord.put(textField.name(), datum);
			}
		}
		return record;
	}

	private void setFieldFromNode(Schema schema, IndexedRecord record, Node node, boolean specific) throws InstantiationException, IllegalAccessException,
			ClassNotFoundException, NoSuchMethodException, SecurityException {
		if (node.getNodeType() != Node.ELEMENT_NODE)
			return;

		Element child = (Element) node;
		boolean setRecordFromNode = false;
		final String fieldName = child.getLocalName();
		Schema.Field field = getFieldBySource(schema, new Source(fieldName, child.getNamespaceURI(), false));
		if (field == null) {
			field = getNestedFieldBySource(schema, new Source(fieldName, false));
			setRecordFromNode = (field != null);
		}

		if (field != null) {
			GenericData.Record grecord = (GenericData.Record) record;
			boolean array = field.schema().getType() == Schema.Type.ARRAY;
			Object datum = createNodeDatum(!array ? field.schema() : field.schema().getElementType(), child, setRecordFromNode, specific);

			if (!array)
				grecord.put(field.name(), datum);
			else {
				@SuppressWarnings("unchecked")
				List<Object> values = (List<Object>) grecord.get(field.name());
				values.add(datum);
			}
		} else {
			Schema.Field anyField = schema.getField(Source.WILDCARD);
			if (anyField == null)
				throw new ConverterException("Could not find field " + fieldName + " in Avro Schema " + schema.toString(true)
						+ " , neither as specific field nor 'any' element");

			@SuppressWarnings("unchecked")
			Map<String, String> map = (HashMap<String, String>) record.get(anyField.pos());
			map.put(fieldName, getContentAsText(child));
		}
	}

	Schema.Field getFieldBySource(Schema schema, Source source) {
		if (schema.getType() == Schema.Type.UNION) {
			return getFieldBySource(schema.getTypes().get(1), source);
		} else {
		for (Schema.Field field : schema.getFields()) {
			String fieldSource = field.getProp(Source.SOURCE);
			if (caseSensitiveNames && source.toString().equals(fieldSource))
				return field;
			if (!caseSensitiveNames && source.toString().equalsIgnoreCase(fieldSource))
				return field;
		}

		return null;
		}
	}

	Schema.Field getNestedFieldBySource(Schema schema, Source source) {
		if (schema.getType() != Schema.Type.RECORD) {
			return null;
		}

		for (Schema.Field field : schema.getFields()) {
			Schema topSchema = field.schema();

			switch (topSchema.getType()) {
				case ARRAY: {
					Schema.Field fieldBySource = getFieldBySource(topSchema.getElementType(), source);
					if (fieldBySource != null) {
						return field;
					}
				}
					break;
				default:
					break;
			}
		}

		return null;
	}

	private String getContentAsText(Element el) {
		if (el.getTextContent().length() == 0)
			return "";

		StringWriter writer = new StringWriter();
		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.transform(new DOMSource(el), new StreamResult(writer));
		} catch (TransformerException impossible) {
			throw new ConverterException(impossible);
		}

		String result = "" + writer.getBuffer();

		//trim element's start tag
		int startTag = result.indexOf(el.getLocalName());
		startTag = result.indexOf('>', startTag);
		result = result.substring(startTag + 1);

		//trim element's end tag
		int endTag = result.lastIndexOf("</");
		result = result.substring(0, endTag);

		return result;
	}
}
