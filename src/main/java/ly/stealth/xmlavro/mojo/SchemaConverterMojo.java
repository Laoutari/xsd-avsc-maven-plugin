package ly.stealth.xmlavro.mojo;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import ly.stealth.xmlavro.BaseDirResolver;
import ly.stealth.xmlavro.SchemaBuilder;

import org.apache.avro.Schema;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

/**
 * XML schema to Avro schema converter Maven plugin
 *
 * @goal schema
 * @phase generate-sources
 */
public class SchemaConverterMojo extends AbstractMojo {

    /**
     * Directory containing XML schema files.
     *
     * @parameter default-value="${project.basedir}/src/main/xsd"
     */
    private File sourceDirectory;

    /**
     * Patterns used to select XML schema file names from the source directory
     * for conversion. The default pattern is {@code **&#47;*.xsd}
     *
     * @parameter
     */
    private String[] includes = new String[] { "**/*.xsd" };

    /**
     * Directory where Avro schema files will be written
     *
     * @parameter
     *         default-value="${project.build.directory}/generated-sources/avsc"
     */
    private File outputDirectory;

    /**
     * Namespace for generated Avro named types
     *
     * @parameter
     * @required
     */
    private String namespace;

    private String[] getIncludedFiles() {
        FileSet files = new FileSet();
        files.setDirectory(sourceDirectory.getAbsolutePath());
        files.setFollowSymlinks(false);
        for (String include : includes) {
            files.addInclude(include);
        }

        FileSetManager fileSetManager = new FileSetManager();
        return fileSetManager.getIncludedFiles(files);
    }

    private Path toTargetFile(Path sourceFile) {
        String name = sourceFile.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        String base = (dotIndex > 0) ? name.substring(0, dotIndex) : name;

        return Paths.get(outputDirectory.getAbsolutePath(), base + ".avsc");
    }

    private void convert(String includedFile) {
        Path xmlSchemaFile =
                Paths.get(sourceDirectory.getAbsolutePath(), includedFile);
        getLog().info("XML schema input file: " + xmlSchemaFile);

        Path avroSchemaFile = toTargetFile(xmlSchemaFile);
        getLog().info("Avro schema output file: " + avroSchemaFile);

        SchemaBuilder schemaBuilder = new SchemaBuilder();
        schemaBuilder.setResolver(
                new BaseDirResolver(xmlSchemaFile.getParent()));
        schemaBuilder.setNamespace(namespace);
        Schema avroSchema = schemaBuilder.createSchema(xmlSchemaFile.toFile());

        try {
            Files.createDirectories(avroSchemaFile.getParent());
            try (Writer writer = Files.newBufferedWriter(
                    avroSchemaFile, StandardCharsets.UTF_8))
            {
                writer.write(avroSchema.toString(true));
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot write file " + avroSchemaFile, e);
        }
    }

    @Override
    public void execute() throws MojoFailureException {
        for (String xmlSchemaFile : getIncludedFiles()) {
            convert(xmlSchemaFile);
        }
    }
}
