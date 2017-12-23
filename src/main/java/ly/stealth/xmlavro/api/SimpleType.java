package ly.stealth.xmlavro.api;

import com.sun.codemodel.JType;
//import com.sun.codemodel.internal.JType;
import org.apache.avro.Schema;
import org.apache.xerces.impl.xs.XSComplexTypeDecl;

import java.util.HashMap;
import java.util.Map;

public class SimpleType implements ApiType {
    private Schema.Type type;
    private static Map<Schema.Type, SimpleType> simpleTypes = new HashMap<>();

    public SimpleType(Schema.Type type) {
        this.type = type;
    }

    public Schema.Type getType() {
        return type;
    }

    public void setType(Schema.Type type) {
        this.type = type;
    }

    public static SimpleType getSimpleType(Schema.Type type) {
        SimpleType stype = simpleTypes.get(type);
        if (stype == null) {
            stype = new SimpleType(type);
            simpleTypes.put(type, stype);
        }
        return stype;
    }

    public JType getJType() {
        return Api.getApi().getCm().VOID;
    }

    public static SimpleType getString() {
        return simpleTypes.values().iterator().next();
    }
}
