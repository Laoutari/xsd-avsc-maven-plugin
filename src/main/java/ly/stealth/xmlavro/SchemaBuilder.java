package ly.stealth.xmlavro;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ly.stealth.xmlavro.EntityResolver.Resolver;

import ly.stealth.xmlavro.api.ApiBase;
import org.apache.avro.Schema;
import org.apache.xerces.dom.DOMInputImpl;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.xs.XMLSchemaLoader;
import org.apache.xerces.impl.xs.XSComplexTypeDecl;
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
import org.apache.xerces.xs.XSObject;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSParticle;
import org.apache.xerces.xs.XSSimpleTypeDefinition;
import org.apache.xerces.xs.XSTerm;
import org.apache.xerces.xs.XSTypeDefinition;
import org.w3c.dom.DOMError;
import org.w3c.dom.DOMErrorHandler;
import org.w3c.dom.DOMLocator;
import org.w3c.dom.TypeInfo;
import org.w3c.dom.ls.LSInput;

import java.io.*;
import java.util.*;

import ly.stealth.xmlavro.api.Api;
import ly.stealth.xmlavro.api.Clazz;
import org.apache.xerces.impl.dv.xs.*;

public abstract class SchemaBuilder {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SchemaBuilder.class);

	private boolean debug;
	private Resolver resolver;
	private String namespace = null;

	public static Map<Short, Schema.Type> primitives = new HashMap<>();
	static {
		primitives.put(XSConstants.BOOLEAN_DT, Schema.Type.BOOLEAN);

		primitives.put(XSConstants.INT_DT, Schema.Type.INT);
		primitives.put(XSConstants.BYTE_DT, Schema.Type.INT);
		primitives.put(XSConstants.SHORT_DT, Schema.Type.INT);
		primitives.put(XSConstants.UNSIGNEDBYTE_DT, Schema.Type.INT);
		primitives.put(XSConstants.UNSIGNEDSHORT_DT, Schema.Type.INT);

		primitives.put(XSConstants.INTEGER_DT, Schema.Type.INT);
		primitives.put(XSConstants.NEGATIVEINTEGER_DT, Schema.Type.INT);
		primitives.put(XSConstants.NONNEGATIVEINTEGER_DT, Schema.Type.INT);
		primitives.put(XSConstants.POSITIVEINTEGER_DT, Schema.Type.INT);
		primitives.put(XSConstants.NONPOSITIVEINTEGER_DT, Schema.Type.INT);

		primitives.put(XSConstants.LONG_DT, Schema.Type.LONG);
		primitives.put(XSConstants.UNSIGNEDINT_DT, Schema.Type.LONG);

		primitives.put(XSConstants.FLOAT_DT, Schema.Type.FLOAT);

		primitives.put(XSConstants.DOUBLE_DT, Schema.Type.DOUBLE);
		primitives.put(XSConstants.DECIMAL_DT, Schema.Type.DOUBLE);

		primitives.put(XSConstants.DATETIME_DT, Schema.Type.LONG);
	}

	private Map<String, Schema> schemas = new LinkedHashMap<>();

	public boolean getDebug() {
		return debug;
	}

	public void setDebug(boolean debug) { this.debug = debug; }

	public Resolver getResolver() {
		return resolver;
	}

	public void setResolver(Resolver resolver) {
		this.resolver = resolver;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public Schema createSchema(String xsd) throws FileNotFoundException {
		return createSchema(new StringReader(xsd));
	}

	public Schema createSchema(File file) throws ConverterException {
		try (InputStream stream = new FileInputStream(file)) {
			return createSchema(stream);
		} catch (IOException e) {
			throw new ConverterException(e);
		}
	}

	public Schema createSchema(Reader reader) throws FileNotFoundException {
		DOMInputImpl input = new DOMInputImpl();
		input.setCharacterStream(reader);
		return createSchema(input);
	}

	public Schema createSchema(InputStream stream) throws FileNotFoundException {
		DOMInputImpl input = new DOMInputImpl();
		input.setByteStream(stream);
		return createSchema(input);
	}

	public Schema createSchema(LSInput input) throws FileNotFoundException {
		ErrorHandler errorHandler = new ErrorHandler();

		XMLSchemaLoader loader = new XMLSchemaLoader();
		if (this.resolver != null)
			loader.setEntityResolver(new EntityResolver(resolver));

		loader.setErrorHandler(errorHandler);
		loader.setParameter(Constants.DOM_ERROR_HANDLER, errorHandler);

		XSModel model = loader.load(input);

		errorHandler.throwExceptionIfHasError();
		createInterface(model);
		return createSchema(model);
	}

	public Schema createSchema(XSModel model) {
		schemas.clear();
		this.model = model;

		Map<Source, Schema> schemas = new LinkedHashMap<>();
		XSNamedMap rootEls = model.getComponentsByNamespace(XSConstants.ELEMENT_DECLARATION, targetNamespace);

		for (int i = 0; i < rootEls.getLength(); i++) {
			XSElementDeclaration el = (XSElementDeclaration) rootEls.item(i);
			XSTypeDefinition type = el.getTypeDefinition();

			Schema schema = createTypeSchema(type, true, false);
			schemas.put(new Source(el.getName()), schema);
		}

		if (schemas.size() == 0)
			throw new ConverterException("No root element declaration");
		if (schemas.size() == 1)
			return schemas.values().iterator().next();

		return createRootRecordSchema(schemas);
	}


	private Schema createRootRecordSchema(Map<Source, Schema> schemas) {
		Schema nullSchema = Schema.create(Schema.Type.NULL);
		List<Schema.Field> fields = new ArrayList<Schema.Field>(schemas.size());

		// TODO: Consider "Root" instead of "root"
		Schema root = Schema.createRecord("root", "", namespace, false);

		for (Source source : schemas.keySet()) {
			Schema schema = schemas.get(source);
			Schema.Field f = new Schema.Field(rename(ToRename.FIELDNAME, source.getName()), schema, null, null);
			f.addProp(Source.SOURCE, "" + source);
			fields.add(f);
		}

		root.setFields(fields);
		root.addProp(Source.SOURCE, Source.DOCUMENT);
		return root;
	}

	@SuppressWarnings("unchecked")
	private Schema createTypeSchema(XSTypeDefinition type, boolean optional, boolean array) {
		if (type.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) {
			XSSimpleTypeDefinition stype = (XSSimpleTypeDefinition) type;
			return createTypeSchema(stype, optional, array);
		} else if (type.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
			XSComplexTypeDefinition ctype = (XSComplexTypeDefinition) type;
			return createTypeSchema(ctype, optional, array);
		} else {
			logger.error("Unsupported type {}." + type.getTypeCategory());
			return null;
		}
	}

	private Schema createTypeSchema(XSSimpleTypeDefinition stype, boolean optional, boolean array) {
		Schema schema = null;
		StringList enumList = stype.getLexicalEnumeration();
		if (enumList != null && !enumList.isEmpty() && !primitives.containsKey(stype.getBuiltInKind())) {
			String name = validName(stype.getName());
			if (name == null)
				name = nextTypeName();
			schema = schemas.get(name);
			if (schema == null) {
				try {
					schema = Schema.createEnum(name((TypeInfo) stype), null, rename(ToRename.NAMESPACE, stype.getNamespace()), Api.removeSymbols(enumList));
					schemas.put(Api.getApi().getName(stype), schema);
				} catch (Exception e) {
					logger.error("Warning: failed to convert '{} to enum: {}", stype.getName(), e.getMessage());
				}
			}
		}
		if (schema == null)
			schema = Schema.create(getPrimitiveType(stype));

		if (array || isGroupTypeWithMultipleOccurs(stype))
			schema = Schema.createArray(schema);
		else if (optional) {
			Schema nullSchema = Schema.create(Schema.Type.NULL);
			schema = Schema.createUnion(Arrays.asList(nullSchema, schema));
		}

		return schema;
	}

	private boolean isGroupTypeWithMultipleOccurs(XSTypeDefinition type) {
		return type instanceof XSComplexTypeDefinition && isGroupTypeWithMultipleOccurs(((XSComplexTypeDefinition) type).getParticle());
	}

	private boolean isGroupTypeWithMultipleOccurs(XSParticle particle) {
		if (particle == null) return false;

		XSTerm term = particle.getTerm();
		if (term.getType() != XSConstants.MODEL_GROUP) return false;

		XSModelGroup group = (XSModelGroup) term;
		final short compositor = group.getCompositor();
		switch (compositor) {
			case XSModelGroup.COMPOSITOR_CHOICE:
			case XSModelGroup.COMPOSITOR_SEQUENCE:
				return particle.getMaxOccurs() > 1 || particle.getMaxOccursUnbounded();
			default:
				return false;
		}
	}

	private Schema createGroupSchema(String name, XSModelGroup groupTerm) {
		Schema record = Schema.createRecord(name, null, rename(ToRename.NAMESPACE, groupTerm.getNamespace()), false);
		schemas.put(name, record);

		Map<String, Schema.Field> fields = new LinkedHashMap<>();
		createGroupFields(groupTerm, fields, false, false);
		record.setFields(new ArrayList<>(fields.values()));

		return Schema.createArray(record);
	}

	private Schema createRecordSchema(String name, XSComplexTypeDefinition type) {
		Schema record = Schema.createRecord(name, null, rename(ToRename.NAMESPACE, type.getNamespace()), false);
		schemas.put(Api.getApi().getName(type), record);

		record.setFields(createFields(type));
		return record;
	}

	public static String KEY_VALUE_FIELD_NAME = "keyValue";

	private List<Schema.Field> createFields(XSComplexTypeDefinition type) {
		final Map<String, Schema.Field> fields = new LinkedHashMap<>();

		XSObjectList attrUses = type.getAttributeUses();
		for (int i = 0; i < attrUses.getLength(); i++) {
			XSAttributeUse attrUse = (XSAttributeUse) attrUses.item(i);
			XSAttributeDeclaration attrDecl = attrUse.getAttrDeclaration();

			boolean optional = !attrUse.getRequired();
			Schema.Field field = createField(attrDecl, attrDecl.getTypeDefinition(), optional, false);
			fields.put(field.getProp(Source.SOURCE), field);
		}

		XSParticle particle = type.getParticle();
		if (particle == null) {
			fields.put(KEY_VALUE_FIELD_NAME, new Schema.Field(KEY_VALUE_FIELD_NAME, Schema.create(Schema.Type.STRING),null,null));
			return new ArrayList<>(fields.values());
		}

		XSTerm term = particle.getTerm();
		if (term.getType() != XSConstants.MODEL_GROUP)
			throw new ConverterException("Unsupported term type " + term.getType());

		XSModelGroup group = (XSModelGroup) term;
		createGroupFields(group, fields, particle.getMinOccurs() == 0, false);

		return new ArrayList<>(fields.values());
	}

	private void createGroupFields(XSModelGroup group, Map<String, Schema.Field> fields, boolean forceOptional, boolean forceArray) {
		XSObjectList particles = group.getParticles();

		for (int j = 0; j < particles.getLength(); j++) {
			XSParticle particle = (XSParticle) particles.item(j);
			boolean insideChoice = group.getCompositor() == XSModelGroup.COMPOSITOR_CHOICE;

			boolean optional = insideChoice || particle.getMinOccurs() == 0;
			boolean array = particle.getMaxOccurs() > 1 || particle.getMaxOccursUnbounded() || forceArray;

			XSTerm term = particle.getTerm();

			switch (term.getType()) {
				case XSConstants.ELEMENT_DECLARATION:
					XSElementDeclaration el = (XSElementDeclaration) term;
					final XSObjectList substitutionGroupList = model.getSubstitutionGroup(el);
					if (substitutionGroupList != null && ! substitutionGroupList.isEmpty()) {
						if (! el.getAbstract()) {
							Schema.Field field = createField(el, el.getTypeDefinition(), true, array);
							fields.put(field.getProp(Source.SOURCE), field);
						}

						for (int p = 0; p < substitutionGroupList.getLength(); p++) {
							XSElementDeclaration sgEL = (XSElementDeclaration) substitutionGroupList.item(p);
							Schema.Field field = createField(sgEL, sgEL.getTypeDefinition(), true, array);
							fields.put(field.getProp(Source.SOURCE), field);
						}
					} else {
						Schema.Field field = createField(el, el.getTypeDefinition(), forceOptional || optional, array);
						final Schema.Field cachedField = fields.get(field.getProp(Source.SOURCE));
						if (cachedField == null) {
							fields.put(field.getProp(Source.SOURCE), field);
						} else {
							if (field.schema().getType() == Schema.Type.ARRAY
									&& cachedField.schema().getType() != Schema.Type.ARRAY) {
								fields.put(field.getProp(Source.SOURCE), field);
							}
						}
					}
					break;
				case XSConstants.MODEL_GROUP:
					XSModelGroup subGroup = (XSModelGroup) term;
					if (subGroup.getNamespace() == null) {
						if (particle.getMaxOccurs() <= 1 && !particle.getMaxOccursUnbounded()) {
							createGroupFields(subGroup, fields, forceOptional || insideChoice || particle.getMinOccurs() == 0, false);
						} else {
							createGroupFields(subGroup, fields, forceOptional || insideChoice || particle.getMinOccurs() == 0, true);
						}
					} else if (particle.getMaxOccurs() <= 1 && !particle.getMaxOccursUnbounded()) {
						createGroupFields(subGroup, fields, forceOptional || insideChoice || particle.getMinOccurs() == 0, false);
					} else {
						String fieldName = nextTypeName();
						fields.put(fieldName, new Schema.Field(rename(ToRename.FIELDNAME, fieldName), createGroupSchema(nextTypeName(), subGroup), null, null));
					}
					break;
				case XSConstants.WILDCARD:
				{
					Schema.Field field = createField(term, null, forceOptional || optional, array);
					fields.put(field.getProp(Source.SOURCE), field);
					break;
				}
				default:
					throw new ConverterException("Unsupported term type " + term.getType());
			}
		}
	}

	private Schema.Field createField(XSObject source, XSTypeDefinition type, boolean optional, boolean array) {
		List<Short> supportedTypes = Arrays.asList(XSConstants.ELEMENT_DECLARATION, XSConstants.ATTRIBUTE_DECLARATION, XSConstants.WILDCARD);
		if (!supportedTypes.contains(source.getType()))
			throw new ConverterException("Invalid source object type " + source.getType());

		boolean wildcard = source.getType() == XSConstants.WILDCARD;
		if (wildcard) {
			Schema map = Schema.createMap(Schema.create(Schema.Type.STRING));
			return new Schema.Field(Source.WILDCARD, map, null, null);
		}

		Schema fieldSchema = createTypeSchema(type, optional, array);

		String name = validName(source.getName());

		Schema.Field field = new Schema.Field(name, fieldSchema, null, null);

		boolean attribute = source.getType() == XSConstants.ATTRIBUTE_DECLARATION;
		field.addProp(Source.SOURCE, "" + new Source(source.getName(), attribute));

		return field;
	}

	public Schema.Type getPrimitiveType(XSSimpleTypeDefinition type) {
		Schema.Type avroType = primitives.get(type.getBuiltInKind());
		return avroType == null ? Schema.Type.STRING : avroType;
	}

	private String name(TypeInfo type) {
		String name = validName(type.getTypeName());
		if (name == null) {
			return nextTypeName();
		}

		String typeName = name.substring(0, 1).toUpperCase() + name.substring(1);

		return rename(ToRename.TYPENAME, typeName);
	}

	private String validName(String name) {
		if (name == null)
			return null;
		name = rename(ToRename.FIELDNAME, name);

		char[] chars = name.toCharArray();
		char[] result = new char[chars.length];

		int p = 0;
		for (char c : chars) {
			boolean valid = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'z' || p > 0 && c >= '0' && c <= '9' || c == '_';

			boolean separator = c == '.' || c == '-';

			if (valid) {
				result[p] = c;
				p++;
			} else if (separator) {
				result[p] = '_';
				p++;
			}
		}

		String s = new String(result, 0, p);

		// handle built-in types
		try {
			Schema.Type.valueOf(s.toUpperCase());
			s += typeName++;
		} catch (IllegalArgumentException ignore) {
		}

		return s;
	}

	private int typeName;
	private String nextTypeName() { return "type" + typeName++; }

	public class ErrorHandler implements XMLErrorHandler, DOMErrorHandler {
		XMLParseException exception;
		DOMError error;

		@Override
		public void warning(String domain, String key, XMLParseException exception) throws XNIException {
			if (this.exception == null) this.exception = exception;
		}

		@Override
		public void error(String domain, String key, XMLParseException exception) throws XNIException {
			if (this.exception == null) this.exception = exception;
		}

		@Override
		public void fatalError(String domain, String key, XMLParseException exception) throws XNIException {
			if (this.exception == null) this.exception = exception;
		}

		@Override
		public boolean handleError(DOMError error) {
			if (this.error == null) this.error = error;
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

	public enum ToRename {NAMESPACE, NAMESPACE_TO_JAVAINTERFACE, TYPENAME, FIELDNAME};
	public abstract String rename(ToRename tr, String renameThis);

	private File javaWrapperDirectory = null;
	private Api api = null;

	private String xmlns = null;
	private String targetNamespace = null;
	private XSModel model = null;

	public SchemaBuilder(String namespace, String xmlns, String targetNamespace, File javaWrapperDirectory) {
		this.namespace = namespace;
		this.xmlns = xmlns;
		this.targetNamespace = targetNamespace;
		this.javaWrapperDirectory = javaWrapperDirectory;
	}

	public void createInterface(XSModel xsModel) throws FileNotFoundException {
		api = new Api(this, xsModel, javaWrapperDirectory);
		api.generateApi();
	}

	private List<Schema> getParentsAsSchemaList(List<XSComplexTypeDefinition> allParents) {
		List<Schema> schemaLinkedList = new LinkedList<>();
		for (XSComplexTypeDefinition superComplexType : allParents) {
			Schema mySchema = schemas.get(Api.getApi().getName(superComplexType));
			if (mySchema == null) {
				schemaLinkedList.add(createRecordSchema(name((TypeInfo) superComplexType), superComplexType));
			} else {
				schemaLinkedList.add(mySchema);
			}
		}
		return schemaLinkedList;
	}

	private Schema mergeSchema(XSComplexTypeDefinition xsComplexTypeDefinition, Schema schema, List<Schema> schemaList, boolean optional, boolean array) {
		if (array || isGroupTypeWithMultipleOccurs(xsComplexTypeDefinition)) {
			return Schema.createArray(schemaList.isEmpty() ? schema : Schema.createUnion(schemaList));
		} else if (optional) {
			Schema nullSchema = Schema.create(Schema.Type.NULL);
			if (schemaList.isEmpty() && schema != null) {
				return Schema.createUnion(Arrays.asList(nullSchema, schema));
			} else {
				schemaList.add(0, nullSchema);
				return Schema.createUnion(schemaList);
			}
		} else if (!schemaList.isEmpty()) {
			return Schema.createUnion(schemaList);
		} else {
			return schema;
		}
	}

	private Schema createOrUse(Schema schema, String name, XSComplexTypeDefinition xsComplexTypeDefinition) {
		if (schema == null) {
			schema = createRecordSchema(name, xsComplexTypeDefinition);
		}
		return schema;
	}

	private Schema createTypeSchema(XSComplexTypeDefinition xsComplexTypeDefinition, boolean optional, boolean array) {
		String name = name((TypeInfo) xsComplexTypeDefinition);

		Clazz clazz = api.get(xsComplexTypeDefinition);
		final List<XSComplexTypeDefinition> allParents = clazz.getAllParents();
		final List<XSComplexTypeDefinition> allChildren = clazz.getAllChildren();

		if (allParents != null) {
			return mergeSchema(xsComplexTypeDefinition, null, getParentsAsSchemaList(allParents), optional, array);
		} else {
			Schema schema = schemas.get(Api.getApi().getName(xsComplexTypeDefinition));
			List<Schema> schemaList = new LinkedList<>();
			if (allChildren == null) {
				schema = createOrUse(schema, name, xsComplexTypeDefinition);
			} else if (allChildren.size() == 1 && (xsComplexTypeDefinition.getAbstract())) {
				XSComplexTypeDefinition subComplexType = allChildren.iterator().next();
				schema = createOrUse(schemas.get(Api.getApi().getName(subComplexType)), name((TypeInfo) subComplexType), subComplexType);
			} else {
				if (!xsComplexTypeDefinition.getAbstract()) {
					schemaList.add(createOrUse(schema, name, xsComplexTypeDefinition));
				}
				for (XSComplexTypeDefinition subComplexType : allChildren) {
					schemaList.add(createOrUse(schemas.get(Api.getApi().getName(subComplexType)), name((TypeInfo) subComplexType), subComplexType));
				}
			}
			return mergeSchema(xsComplexTypeDefinition, schema, schemaList, optional, array);
		}
	}
}
