package ly.stealth.xmlavro;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BaseDirResolver implements EntityResolver.Resolver {
	private Path baseDir;

	public BaseDirResolver(Path baseDir) {
		this.baseDir = baseDir;
	}

	public BaseDirResolver(String baseDir) {
		this.baseDir = Paths.get(baseDir);
	}

	@Override
	public InputStream getStream(String systemId) {
		Path file = baseDir.resolve(systemId);
		try {
			return Files.newInputStream(file);
		} catch (IOException e) {
			return null;
		}
	}
}