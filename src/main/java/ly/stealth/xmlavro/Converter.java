/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ly.stealth.xmlavro;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;

public class Converter {
	public static Schema createSchema(String xsd) {
		return new SchemaBuilder().createSchema(xsd);
	}

	public static Schema createSchema(File file) {
		return new SchemaBuilder().createSchema(file);
	}

	public static Schema createSchema(Reader reader) {
		return new SchemaBuilder().createSchema(reader);
	}

	public static Schema createSchema(InputStream stream) {
		return new SchemaBuilder().createSchema(stream);
	}

	public static <T> T createDatum(Schema schema, File file) {
		return new DatumBuilder().createDatum(schema, file, false);
	}

	public static <T> T createDatum(Schema schema, String xml) {
		return new DatumBuilder().createDatum(schema, xml, false);
	}

	public static <T> T createDatum(Schema schema, Reader reader) {
		return new DatumBuilder().createDatum(schema, reader, false);
	}

	public static <T> T createDatum(Schema schema, InputStream stream) {
		return new DatumBuilder().createDatum(schema, stream, false);
	}

	public static <T> T createSpecificDatum(Schema schema, File file) {
		return new DatumBuilder().createDatum(schema, file, true);
	}

	public static <T> T createSpecificDatum(Schema schema, String xml) {
		return new DatumBuilder().createDatum(schema, xml, true);
	}

	public static <T> T createSpecificDatum(Schema schema, Reader reader) {
		return new DatumBuilder().createDatum(schema, reader, true);
	}

	public static <T> T createSpecificDatum(Schema schema, InputStream stream) {
		return new DatumBuilder().createDatum(schema, stream, true);
	}

	private static class Options {
		static final String USAGE = "{-d|--debug} {-b|--baseDir <baseDir>} <xsdFile> <xmlFile> {<avscFile>} {<avroFile>}";

		File xsdFile;
		File xmlFile;

		File avscFile;
		File avroFile;

		boolean debug;
		String baseDir;

		Options(String... args) {
			List<String> files = new ArrayList<>();

			for (int i = 0; i < args.length; i++) {
				String arg = args[i];

				if (arg.startsWith("-"))
					switch (arg) {
						case "-d":
						case "--debug":
							debug = true;
							break;
						case "-b":
						case "--baseDir":
							if (i == args.length - 1)
								throw new IllegalArgumentException("Base dir required");
							i++;
							baseDir = args[i];
							break;
						default:
							throw new IllegalArgumentException("Unsupported option " + arg);
					}
				else
					files.add(arg);
			}

			if (files.size() < 2 || files.size() > 4)
				throw new IllegalArgumentException("Incorrect number of in/out files. Expected [2..4]");

			xsdFile = replaceBaseDir(files.get(0), baseDir);
			xmlFile = replaceBaseDir(files.get(1), baseDir);

			avscFile = files.size() > 2 ? replaceBaseDir(files.get(2), baseDir) : replaceExtension(xsdFile, "avsc");
			avroFile = files.size() > 3 ? replaceBaseDir(files.get(3), baseDir) : replaceExtension(xmlFile, "avro");
		}

		private static File replaceExtension(File file, String newExtension) {
			String fileName = file.getPath();

			int dotIdx = fileName.lastIndexOf('.');
			if (dotIdx != -1)
				fileName = fileName.substring(0, dotIdx);

			return new File(fileName + "." + newExtension);
		}

		private static File replaceBaseDir(String path, String baseDir2) {
			File file = new File(path);
			if (baseDir2 == null || file.isAbsolute())
				return file;
			return new File(baseDir2, file.getPath());
		}
	}

	public static void main(String... args) throws IOException {
		Options opts;
		try {
			opts = new Options(args);
		} catch (IllegalArgumentException e) {
			System.out.println("XML Avro converter.\nError: " + e.getMessage() + "\n" + "Usage: " + Options.USAGE + "\n");
			System.exit(1);
			return;
		}

		System.out.println("Converting: \n" + opts.xsdFile + " -> " + opts.avscFile + "\n" + opts.xmlFile + " -> " + opts.avroFile);

		SchemaBuilder schemaBuilder = new SchemaBuilder();
		schemaBuilder.setDebug(opts.debug);
		if (opts.baseDir != null)
			schemaBuilder.setResolver(new BaseDirResolver(opts.baseDir));
		Schema schema = schemaBuilder.createSchema(opts.xsdFile);

		try (Writer writer = new FileWriter(opts.avscFile)) {
			writer.write(schema.toString(true));
		}

		DatumBuilder datumBuilder = new DatumBuilder();
		Object datum = datumBuilder.createDatum(schema, opts.xmlFile, false);

		try (OutputStream stream = new FileOutputStream(opts.avroFile)) {
			DatumWriter<Object> datumWriter = new SpecificDatumWriter<>(schema);
			datumWriter.write(datum, EncoderFactory.get().directBinaryEncoder(stream, null));
		}
	}
}