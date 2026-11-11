/*
This file is part of FreeJ2ME.

FreeJ2ME is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
*/
package javax.microedition.io;

import java.io.IOException;
import java.net.URL;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;

final class HttpsConnectionImpl extends HttpConnectionImpl implements HttpsConnection
{
HttpsConnectionImpl(URL url, int mode, boolean timeouts) throws IOException
{
super(url, mode, timeouts);
if(!(connection instanceof HttpsURLConnection))
{
throw new IOException("HTTPS is not available for " + url);
}
}

protected int defaultPort() { return 443; }

public SecurityInfo getSecurityInfo()
{
try
{
getResponseCode(); // Complete TLS negotiation before reading the peer certificate.
final HttpsURLConnection https = (HttpsURLConnection)connection;
final X509Certificate certificate = https.getServerCertificates().length == 0 ? null
        : (https.getServerCertificates()[0] instanceof X509Certificate
            ? (X509Certificate)https.getServerCertificates()[0] : null);
return new SecurityInfo()
{
public String getCipherSuite() { return https.getCipherSuite(); }
public String getProtocolName() { return "TLS"; }
public String getProtocolVersion() { return null; }
public javax.microedition.pki.Certificate getServerCertificate()
{
if(certificate == null) { return null; }
return new javax.microedition.pki.Certificate()
{
public String getSubject() { return certificate.getSubjectX500Principal().getName(); }
public String getIssuer() { return certificate.getIssuerX500Principal().getName(); }
public String getType() { return certificate.getType(); }
public String getVersion() { return Integer.toString(certificate.getVersion()); }
public String getSigAlgName() { return certificate.getSigAlgName(); }
public long getNotBefore() { return certificate.getNotBefore().getTime(); }
public long getNotAfter() { return certificate.getNotAfter().getTime(); }
public String getSerialNumber() { return certificate.getSerialNumber().toString(16); }
};
}
};
}
catch(IOException error) { throw new IllegalStateException("TLS negotiation failed for " + url, error); }
}
}
