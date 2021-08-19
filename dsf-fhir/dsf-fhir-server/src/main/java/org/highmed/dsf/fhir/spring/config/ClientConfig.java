package org.highmed.dsf.fhir.spring.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pkcs.PKCSException;
import org.highmed.dsf.fhir.client.ClientProvider;
import org.highmed.dsf.fhir.client.ClientProviderImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.rwh.utils.crypto.CertificateHelper;
import de.rwh.utils.crypto.io.CertificateReader;
import de.rwh.utils.crypto.io.PemIo;

@Configuration
public class ClientConfig
{
	private static final BouncyCastleProvider provider = new BouncyCastleProvider();

	@Autowired
	private PropertiesConfig propertiesConfig;

	@Autowired
	private FhirConfig fhirConfig;

	@Autowired
	private DaoConfig daoConfig;

	@Autowired
	private HelperConfig helperConfig;

	@Autowired
	private ReferenceConfig referenceConfig;

	@Bean
	public ClientProvider clientProvider()
	{
		try
		{
			KeyStore webserviceKeyStore = createKeyStore(propertiesConfig.getWebserviceClientCertificateFile(),
					propertiesConfig.getWebserviceClientCertificatePrivateKeyFile(),
					propertiesConfig.getWebserviceClientCertificatePrivateKeyFilePassword());
			KeyStore webserviceTrustStore = createTrustStore(
					propertiesConfig.getWebserviceClientCertificateTrustCertificatesFile());

			return new ClientProviderImpl(webserviceTrustStore, webserviceKeyStore,
					propertiesConfig.getWebserviceClientCertificatePrivateKeyFilePassword(),
					propertiesConfig.getWebserviceClientReadTimeout(),
					propertiesConfig.getWebserviceClientConnectTimeout(),
					propertiesConfig.getWebserviceClientProxyUrl(), propertiesConfig.getWebserviceClientProxyUsername(),
					propertiesConfig.getWebserviceClientProxyPassword(), fhirConfig.fhirContext(),
					referenceConfig.referenceCleaner(), daoConfig.endpointDao(), helperConfig.exceptionHandler());
		}
		catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException | PKCSException e)
		{
			throw new RuntimeException(e);
		}
	}

	private KeyStore createTrustStore(String trustStoreFile)
			throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException
	{
		Path trustStorePath = Paths.get(trustStoreFile);

		if (!Files.isReadable(trustStorePath))
			throw new IOException("Trust store file '" + trustStorePath.toString() + "' not readable");

		return CertificateReader.allFromCer(trustStorePath);
	}

	private KeyStore createKeyStore(String certificateFile, String privateKeyFile, char[] privateKeyPassword)
			throws IOException, PKCSException, CertificateException, KeyStoreException, NoSuchAlgorithmException
	{
		Path certificatePath = Paths.get(certificateFile);
		Path privateKeyPath = Paths.get(privateKeyFile);

		if (!Files.isReadable(certificatePath))
			throw new IOException("Certificate file '" + certificatePath.toString() + "' not readable");
		if (!Files.isReadable(certificatePath))
			throw new IOException("Private key file '" + privateKeyPath.toString() + "' not readable");

		X509Certificate certificate = PemIo.readX509CertificateFromPem(certificatePath);
		PrivateKey privateKey = PemIo.readPrivateKeyFromPem(provider, privateKeyPath, privateKeyPassword);

		String subjectCommonName = CertificateHelper.getSubjectCommonName(certificate);
		return CertificateHelper.toJksKeyStore(privateKey, new Certificate[] { certificate }, subjectCommonName,
				privateKeyPassword);
	}
}
