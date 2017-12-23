package ly.stealth.xmlavro;

import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;

import java.io.IOException;
import java.io.InputStream;

class EntityResolver implements XMLEntityResolver {
	private Resolver resolver;

	EntityResolver(Resolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public XMLInputSource resolveEntity(XMLResourceIdentifier id) throws XNIException, IOException {
		String systemId = id.getLiteralSystemId();

		XMLInputSource source = new XMLInputSource(id);
		source.setByteStream(resolver.getStream(systemId));
		return source;
	}

	public static interface Resolver {
		InputStream getStream(String systemId);
	}
}