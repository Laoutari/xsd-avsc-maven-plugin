package ly.stealth.xmlavro.api;

import com.sun.codemodel.JDefinedClass;

public class Field {
    String methodName;
    boolean isArray;
    String javaDoc;
    ApiType apiType;

    public Field(String methodName, boolean isArray, String javaDoc, ApiType fieldType) {
        this.methodName = methodName;
        this.isArray = isArray;
        this.javaDoc = javaDoc;
        this.apiType = fieldType;
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean isArray() {
        return isArray;
    }

    public String getJavaDoc() {
        return javaDoc;
    }

    public ApiType getApiType() {
        return apiType;
    }
}
