package ly.stealth.xmlavro.api;

import com.sun.codemodel.*;
import ly.stealth.xmlavro.SchemaBuilder;
import ly.stealth.xmlavro.SchemaBuilder.ToRename;
import org.apache.avro.Schema;
import org.apache.commons.lang.StringUtils;
import org.apache.xerces.impl.xs.XSComplexTypeDecl;
import org.apache.xerces.xs.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class Clazz extends ApiBase implements ApiType {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Clazz.class);

    private JDefinedClass thisClass;
    private XSComplexTypeDefinition xsComplexTypeDefinition;
    private List<Field> fields;
    private Clazz parent;
    private List<Clazz> children;

    public String getFullName() {
        return getName(xsComplexTypeDefinition);
    }

    public Clazz(XSComplexTypeDefinition xsComplexTypeDefinition, SchemaBuilder sb, XSModel xsModel, File directory, JCodeModel cm) throws FileNotFoundException {
        super(sb, xsModel, directory, cm);
        this.xsComplexTypeDefinition = xsComplexTypeDefinition;
        this.fields = new ArrayList<>();
    }

    private void doMethod(String methodName, boolean isArray, String javaDoc, XSTypeDefinition xsTypeDefinition) {
        ApiType apiType = Api.getApi().getType(xsTypeDefinition);
        doMethod(methodName, isArray, javaDoc, apiType);
    }


    private void doMethod(String methodName, boolean isArray, String javaDoc, XSElementDeclaration xsElementDeclaration) {
        ApiType apiType = Api.getApi().getType(xsElementDeclaration.getTypeDefinition());
        doMethod(methodName, isArray, javaDoc, apiType);
    }

    private void doMethod(String methodName, boolean isArray, String javaDoc, XSAttributeDeclaration xsAttributeDeclaration) {
        doMethod(methodName, isArray, javaDoc, xsAttributeDeclaration.getTypeDefinition());
    }


    private void doMethod(String methodName, boolean isArray, String javaDoc, ApiType fieldType) {
        methodName = getSb().rename(ToRename.FIELDNAME, methodName);

        if (methodName != null) {
            Field field = new Field(methodName, isArray, javaDoc, fieldType);
            addField(field);
        }
    }

    private void addField(Field field) {
        for (Field otherField : fields) {
            if (otherField.methodName.equals(field.getMethodName()))
                return;
        }
        fields.add(field);
    }

    private void writeMethods() {
        Set<String> setWithUniqueValues = new HashSet<>();
        for (Field f : fields) {
            String name = f.getMethodName();
            name = "get" + StringUtils.capitalize(name);
            if (!setWithUniqueValues.contains(name)) {
                // if boolean, then "is"
                ApiType returnType = f.getApiType();
                JType jType = null;
                if (returnType instanceof SimpleType) {
                    jType = ((SimpleType) returnType).getJType();
                } else if (returnType instanceof Clazz) {
                    Clazz returnClazz = (Clazz) returnType;
                    jType = getJModelClass(((Clazz) returnType).getXsComplexTypeDefinition());
                }
                JMethod method = thisClass.method(JMod.PUBLIC, jType, name);
                setWithUniqueValues.add(name);
            }
        }
    }

    private void doMethods(JDefinedClass thisClass, XSModelGroup xsModelGroup, boolean isArray) {
        XSObjectList particles = xsModelGroup.getParticles();
        for (int j = 0; j < particles.getLength(); j++) {
            XSParticle xsParticleInner = (XSParticle) particles.item(j);
            boolean multiple = false;
            if ((isArray) || (xsParticleInner.getMaxOccurs() > 1) || (xsParticleInner.getMaxOccursUnbounded())) {
                multiple = true;
            }

            XSTerm term = xsParticleInner.getTerm();

            switch (term.getType()) {
                case XSConstants.ELEMENT_DECLARATION:
                    XSElementDeclaration el = (XSElementDeclaration) term;

                    final XSObjectList substitutionGroupList = getModel().getSubstitutionGroup(el);
                    if (substitutionGroupList != null && !substitutionGroupList.isEmpty()) {
                        for (int p = 0; p < substitutionGroupList.getLength(); p++) {
                            XSElementDeclaration xsElementDeclaration = (XSElementDeclaration) substitutionGroupList.item(p);
                            doMethod(xsElementDeclaration.getName(), multiple, "Particle.Term.SubsitutionGroup.XsElementDeclation", xsElementDeclaration);
                        }
                    } else {
                        doMethod(el.getName(), multiple, "Particle.Term.Name", el);
                    }
                    break;
                case XSConstants.MODEL_GROUP:
                    doMethods(thisClass, (XSModelGroup) term, multiple);
                    break;
                case XSConstants.WILDCARD: {
                    break;
                }
                default:
            }

            String methodName = xsParticleInner.getName();
            if (methodName != null) {
                thisClass.method(JMod.PUBLIC, getCm().VOID, methodName);
            }
        }
    }

    private void setParent(XSComplexTypeDefinition baseComplexTypeDefinition) {
        this.parent = Api.getApi().get(baseComplexTypeDefinition);
        ApiType apiType = Api.getApi().getType(baseComplexTypeDefinition);
        if (apiType instanceof Clazz) {
            Clazz parent = (Clazz) apiType;
            parent.addChild(this);
        }
    }

    private void addChild(Clazz clazz) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(clazz);
    }

    public void doStruct() {
        final XSTypeDefinition baseType = xsComplexTypeDefinition.getBaseType();
        if (baseType instanceof XSComplexTypeDefinition) {
            if (!(baseType.getNamespace().contains("w3") && baseType.getName().equalsIgnoreCase("AnyType"))) {
                XSComplexTypeDefinition baseComplexTypeDefinition = (XSComplexTypeDefinition) baseType;
                setParent(baseComplexTypeDefinition);
            }
        }
    }

    public void construct() {
        doStruct();
        defineApi();
    }

    private JDefinedClass getJModelClass(XSComplexTypeDefinition xsComplexTypeDefinition) {
        String name = getName(xsComplexTypeDefinition);
        if (name != null) {
            JDefinedClass jClass = getCm()._getClass(name);
            if (jClass != null) {
                return jClass;
            }
            logger.error("Unexpected: Class for {} not found", name);
        } else {
            logger.error("Unexpected: Name for {} not found", xsComplexTypeDefinition);
        }
        return null;
    }

    public void defineApi() {
        String newName = getName(xsComplexTypeDefinition);
        if (newName == null)
            return;
        this.thisClass = getCm()._getClass(newName);
        if (thisClass == null) return;

        if (parent != null) {
            String parentName = getName(parent.getXsComplexTypeDefinition());
            if (parentName != null) {
                JDefinedClass parentClass = getCm()._getClass(parentName);
                if (parentClass == null)
                    logger.error("Unexpected: Parent not found");
                if (parentClass != null) {
                    thisClass._extends(parentClass);
                }
            }
        }


        XSObjectList xsObjectList = xsComplexTypeDefinition.getAttributeUses();
        for (int i = 0; i < xsObjectList.getLength(); i++) {
            XSAttributeUse xsAttributeUse = (XSAttributeUse) xsObjectList.item(i);
            XSAttributeDeclaration xsAttributeDeclaration = xsAttributeUse.getAttrDeclaration();

            String methodName = xsAttributeDeclaration.getName();
            doMethod(methodName, false, "AttrtibuteUses", xsAttributeDeclaration.getTypeDefinition());
        }

        XSParticle xsParticle = xsComplexTypeDefinition.getParticle();
        if (xsParticle != null) {
            XSTerm xsTerm = xsParticle.getTerm();
            if (xsTerm.getType() != XSConstants.MODEL_GROUP) return;

            XSModelGroup xsModelGroup = (XSModelGroup) xsTerm;
            doMethods(thisClass, xsModelGroup, false);
        } else {
            doMethod(SchemaBuilder.KEY_VALUE_FIELD_NAME, false, "?", SimpleType.getString());
        }
        writeMethods();
    }

    public XSComplexTypeDefinition getXsComplexTypeDefinition() {
        return xsComplexTypeDefinition;
    }

    public List<Field> getFields() {
        return fields;
    }

    List<XSComplexTypeDefinition> allExtends = null;
    boolean extendsInit = false;
    boolean childrenInit = false;
    public List<XSComplexTypeDefinition> getAllParents() {
        if (!extendsInit) {
            extendsInit = true;
            allExtends = new ArrayList<>();
            Clazz current = this;
            while (current != null) {
                if (!current.xsComplexTypeDefinition.getAbstract()) {
                    allExtends.add(current.xsComplexTypeDefinition);
                } else {
                    break;
                }
                current = current.parent;
            }
            if (allExtends.size() == 1) allExtends = null;
            if ((allExtends != null) && (allExtends.isEmpty())) allExtends = null;
            List<XSComplexTypeDefinition> children = getAllChildren();
            if (children != null) {
                if (allExtends != null) allExtends.addAll(children);
            }
            if ((allExtends != null) && (allExtends.isEmpty())) allExtends = null;
        }
        return allExtends;
    }

    private List<XSComplexTypeDefinition> empty = new ArrayList<>();
    List<XSComplexTypeDefinition> allChildren = null;
    public List<XSComplexTypeDefinition> getAllChildren() {
        if (!childrenInit) {
            List<XSComplexTypeDefinition> all = new ArrayList<>();
            if (children != null) {
                for (Clazz clazz : children) {
                    all.add(clazz.xsComplexTypeDefinition);
                    List<XSComplexTypeDefinition> childChildren = clazz.getAllChildren();
                    if (childChildren != null) {
                        all.addAll(childChildren);
                    }
                }
                allChildren = all;
                if ((allChildren != null) && (allChildren.isEmpty())) {
                    allChildren = null;
                }
            }
        }
        return allChildren;
    }
}
