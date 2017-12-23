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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Enum extends ApiBase implements ApiType {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Enum.class);

    private JDefinedClass thisClass;
    private XSSimpleTypeDefinition xsSimpleTypeDefinition;

    public String getFullName() {
        return getName(xsSimpleTypeDefinition);
    }

    public Enum(XSSimpleTypeDefinition xsSimpleTypeDefinition, SchemaBuilder sb, XSModel xsModel, File directory, JCodeModel cm) throws FileNotFoundException {
        super(sb, xsModel, directory, cm);
        this.xsSimpleTypeDefinition = xsSimpleTypeDefinition;
    }
}
