package ly.stealth.xmlavro.api;

import com.sun.codemodel.*;
import ly.stealth.xmlavro.SchemaBuilder;
import ly.stealth.xmlavro.SchemaBuilder.ToRename;
import org.apache.xerces.xs.*;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;

public class ApiBase {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ApiBase.class);

    private File outputDirectory;
    private XSModel model;
    private SchemaBuilder sb;
    private JCodeModel cm;

    public ApiBase(SchemaBuilder sb, XSModel xsModel, File directory, JCodeModel cm) throws FileNotFoundException {
        this.sb = sb;
        this.model = xsModel;
        this.outputDirectory = directory;
        this.cm = cm;
    }

    public String getName(XSTypeDefinition xsTypeDefinition) {
        String name = xsTypeDefinition.getName();
        if (name == null) {
            return null;
        }
        name = sb.rename(ToRename.TYPENAME, name);
        if (name.equals("anyType"))
            return null;
        String renamed = sb.rename(ToRename.NAMESPACE_TO_JAVAINTERFACE, xsTypeDefinition.getNamespace());
        return renamed + "." + name;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public XSModel getModel() {
        return model;
    }

    public SchemaBuilder getSb() {
        return sb;
    }

    public JCodeModel getCm() {
        return cm;
    }
}
