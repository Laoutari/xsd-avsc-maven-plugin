package ly.stealth.xmlavro.api;

import com.sun.codemodel.*;
import ly.stealth.xmlavro.SchemaBuilder;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.xerces.impl.xs.XSComplexTypeDecl;
import org.apache.xerces.xs.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import org.w3c.dom.Element;
import org.w3c.dom.TypeInfo;

public class Api extends ApiBase {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Api.class);

    public static Api api = null;

    public static Api getApi() {
        return api;
    }

    private Map<String, Clazz> classes = new HashMap<>();
    private Map<String, Enum> enums = new HashMap<>();

    public Clazz get(XSComplexTypeDefinition xsComplexTypeDefinition) {
        String newName = getName(xsComplexTypeDefinition);
        return classes.get(newName);
    }

    public Enum getEnum(XSSimpleTypeDefinition xsSimpleTypeDefinition) {
        String newName = getName(xsSimpleTypeDefinition);
        return enums.get(newName);
    }

    public Api(SchemaBuilder sb, XSModel xsModel, File directory) throws FileNotFoundException {
        super(sb, xsModel, directory, new JCodeModel());
        api = this;
    }

    private void createApi(XSComplexTypeDefinition xsComplexTypeDefinition) {
        try {
            String newName = getName(xsComplexTypeDefinition);
            if (newName != null) {
                getCm()._class(JMod.PUBLIC, newName, ClassType.INTERFACE);
            }
        } catch (JClassAlreadyExistsException e) {
            logger.error("Trying to create same class twice", e);
        }
    }

    private void inspectApi() {
        final XSNamedMap components = getModel().getComponents( XSTypeDefinition.COMPLEX_TYPE);
        for (Object xsObject: components.values()) {
            if (xsObject instanceof XSComplexTypeDefinition) {
                // that's ok
            } else {
                System.out.println("What is this?");
            }
        }
    }

    private void createApi() {
        final XSNamedMap components = getModel().getComponents(XSTypeDefinition.COMPLEX_TYPE);
        for (Object xsObject: components.values()) {
            if (xsObject instanceof XSComplexTypeDefinition) {
                XSComplexTypeDefinition xsComplexTypeDefinition = (XSComplexTypeDefinition) xsObject;
                createApi(xsComplexTypeDefinition);
            } else {
                System.out.println(xsObject.getClass());
            }
        }
    }

    private void defineApi() throws FileNotFoundException {
        final XSNamedMap components1 = getModel().getComponents( XSTypeDefinition.COMPLEX_TYPE);
        for (Object xsObject: components1.values()) {
            if (xsObject instanceof XSComplexTypeDefinition) {
                XSComplexTypeDefinition xsComplexTypeDefinition = (XSComplexTypeDefinition) xsObject;
                Clazz clazz = new Clazz(xsComplexTypeDefinition, getSb(), getModel(), getOutputDirectory(), getCm());
                classes.put(getName(xsComplexTypeDefinition), clazz);
            }
        }
        final XSNamedMap components2 = getModel().getComponents( XSTypeDefinition.SIMPLE_TYPE);
        for (Object xsObject: components2.values()) {
            if (xsObject instanceof XSSimpleTypeDefinition) {
                XSSimpleTypeDefinition xsSimpleTypeDefinition = (XSSimpleTypeDefinition) xsObject;
                Enum enume = new Enum(xsSimpleTypeDefinition, getSb(), getModel(), getOutputDirectory(), getCm());
                enums.put(getName(xsSimpleTypeDefinition), enume);
            }
        }
    }

    private void construct() throws FileNotFoundException {
        for (Clazz clazz : classes.values()) {
            clazz.construct();
        }
    }

    public void generateApi() throws FileNotFoundException {
        inspectApi();
        createApi();
        defineApi();
        construct();
        try {
            //getCm().build(getOutputDirectory());
        } catch (Throwable t) {
            logger.error("Error while writing the code {}", t.getMessage());
        }
    }

    public ApiType getType(XSTypeDefinition xsTypeDefinition) {
        if (xsTypeDefinition.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) {
            XSSimpleTypeDefinition xsSimpleTypeDefinition = (XSSimpleTypeDefinition) xsTypeDefinition;
            StringList enumList = xsSimpleTypeDefinition.getLexicalEnumeration();
            Schema.Type avroType = getSb().primitives.get(xsSimpleTypeDefinition.getBuiltInKind());
            if ((enumList != null) && !enumList.isEmpty() && (avroType == null)) {
                return getEnum(xsSimpleTypeDefinition);
            } else {
                return SimpleType.getSimpleType(avroType);
            }
        } else if (xsTypeDefinition.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
            XSComplexTypeDefinition xsComplexTypeDefinition = (XSComplexTypeDefinition) xsTypeDefinition;
            return get(xsComplexTypeDefinition);
        } else {
            return null;
        }
    }

    public static List<String> removeSymbols(List<String> strings) {
        return Arrays.asList(removeSymbols(strings.toArray(new String[strings.size()])));
    }

    public static String[] removeSymbols(String... strings) {
        String[] result = new String[strings.length];
        int i = 0;
        for (String string : strings) {
            if (string != null) {
                String replaced = string.replaceAll(":", "_").replaceAll(" ", "_").replaceAll("\\(", "_").replaceAll("\\)", "");
                if (replaced.matches("[0-9].*")) {
                    replaced = "_" + replaced;
                }

                result[i] = replaced;
                i++;
            }
        }
        return result;
    }

    public static String removeSymbols(org.w3c.dom.Node node) {
        String data = node.getTextContent();
        return Api.removeSymbols(data)[0];
    }

}
