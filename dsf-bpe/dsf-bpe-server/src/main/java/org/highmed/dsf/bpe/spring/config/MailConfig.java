package org.highmed.dsf.bpe.spring.config;

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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pkcs.PKCSException;
import org.highmed.dsf.bpe.service.LoggingMailService;
import org.highmed.dsf.bpe.service.MailService;
import org.highmed.dsf.bpe.service.SmtpMailService;
import org.highmed.dsf.tools.build.BuildInfoReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import de.rwh.utils.crypto.CertificateHelper;
import de.rwh.utils.crypto.io.CertificateReader;
import de.rwh.utils.crypto.io.PemIo;

@Configuration
public class MailConfig implements InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(MailConfig.class);

	private static final BouncyCastleProvider provider = new BouncyCastleProvider();

	@Autowired
	private PropertiesConfig propertiesConfig;

	@Autowired
	BuildInfoReaderConfig buildInfoReaderConfig;

	@Bean
	public MailService mailService()
	{
		if (isConfigured())
		{
			try
			{
				return newSmptMailService();
			}
			catch (IOException | CertificateException | KeyStoreException | NoSuchAlgorithmException | PKCSException e)
			{
				throw new RuntimeException(e);
			}
		}
		else
			return new LoggingMailService();
	}

	private boolean isConfigured()
	{
		return propertiesConfig.getMailServerHostname() != null && propertiesConfig.getMailServerPort() > 0;
	}

	private MailService newSmptMailService()
			throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException, PKCSException
	{
		String fromAddress = propertiesConfig.getMailFromAddress();
		List<String> toAddresses = propertiesConfig.getMailToAddresses();
		List<String> toAddressesCc = propertiesConfig.getMailToAddressesCc();
		List<String> replyToAddresses = propertiesConfig.getMailReplyToAddresses();

		boolean useSmtps = propertiesConfig.getMailUseSmtps();

		String mailServerHostname = propertiesConfig.getMailServerHostname();
		int mailServerPort = propertiesConfig.getMailServerPort();

		String mailServerUsername = propertiesConfig.getMailServerUsername();
		char[] mailServerPassword = propertiesConfig.getMailServerPassword();

		KeyStore trustStore = toTrustStore(propertiesConfig.getMailServerTrustStoreFile());
		char[] keyStorePassword = UUID.randomUUID().toString().toCharArray();
		KeyStore keyStore = toKeyStore(propertiesConfig.getMailServerClientCertificateFile(),
				propertiesConfig.getMailServerClientCertificatePrivateKeyFile(),
				propertiesConfig.getMailServerClientCertificatePrivateKeyFilePassword(), keyStorePassword);

		KeyStore signStore = toSmimeSigningStore(propertiesConfig.getMailSmimeSigingKeyStoreFile(),
				propertiesConfig.getMailSmimeSigingKeyStorePassword());

		return new SmtpMailService(fromAddress, toAddresses, toAddressesCc, replyToAddresses, useSmtps,
				mailServerHostname, mailServerPort, mailServerUsername, mailServerPassword, trustStore, keyStore,
				keyStorePassword, signStore, propertiesConfig.getMailSmimeSigingKeyStorePassword());
	}

	private KeyStore toTrustStore(String trustStoreFile)
			throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException
	{
		if (trustStoreFile == null)
			return null;

		Path trustStorePath = Paths.get(trustStoreFile);

		if (!Files.isReadable(trustStorePath))
			throw new IOException("Mail server trust store file '" + trustStorePath.toString() + "' not readable");

		return CertificateReader.allFromCer(trustStorePath);
	}

	private KeyStore toKeyStore(String certificateFile, String privateKeyFile, char[] privateKeyPassword,
			char[] keyStorePassword)
			throws IOException, CertificateException, PKCSException, KeyStoreException, NoSuchAlgorithmException
	{
		if (certificateFile == null && privateKeyFile == null)
			return null;

		Path certificatePath = Paths.get(certificateFile);
		Path privateKeyPath = Paths.get(privateKeyFile);

		if (!Files.isReadable(certificatePath))
			throw new IOException(
					"Mail server client certificate file '" + certificatePath.toString() + "' not readable");
		if (!Files.isReadable(certificatePath))
			throw new IOException(
					"Mail server client certificate private key file '" + privateKeyPath.toString() + "' not readable");

		X509Certificate certificate = PemIo.readX509CertificateFromPem(certificatePath);
		PrivateKey privateKey = PemIo.readPrivateKeyFromPem(provider, privateKeyPath, privateKeyPassword);

		String subjectCommonName = CertificateHelper.getSubjectCommonName(certificate);
		return CertificateHelper.toJksKeyStore(privateKey, new Certificate[] { certificate }, subjectCommonName,
				keyStorePassword);
	}

	private KeyStore toSmimeSigningStore(String mailSmimeSigingKeyStoreFile, char[] mailSmimeSigingKeyStorePassword)
			throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException
	{
		if (mailSmimeSigingKeyStoreFile == null)
			return null;

		Path keyStorePath = Paths.get(mailSmimeSigingKeyStoreFile);

		if (!Files.isReadable(keyStorePath))
			throw new IOException(
					"S/MIME mail signing certificate file '" + keyStorePath.toString() + "' not readable");

		return CertificateReader.fromPkcs12(keyStorePath, mailSmimeSigingKeyStorePassword);
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		if (isConfigured())
		{
			logger.info(
					"Mail client config: {fromAddress: {}, toAddresses: {}, toAddressesCc: {}, replyToAddresses: {},"
							+ " useSmtps {}, mailServerHostname {}, mailServerPort {}, mailServerUsername {},"
							+ " mailServerPassword {}, trustStore {}, clientCertificate {}, clientCertificatePrivateKey {},"
							+ " clientCertificatePrivateKeyPassword {}, smimeSigingKeyStore {}, smimeSigingKeyStorePassword{},"
							+ " sendTestMailOnStartup {}}",
					propertiesConfig.getMailFromAddress(), propertiesConfig.getMailToAddresses(),
					propertiesConfig.getMailToAddressesCc(), propertiesConfig.getMailReplyToAddresses(),
					propertiesConfig.getMailUseSmtps(), propertiesConfig.getMailServerHostname(),
					propertiesConfig.getMailServerPort(), propertiesConfig.getMailServerUsername(),
					propertiesConfig.getMailServerPassword() != null ? "***" : "null",
					propertiesConfig.getMailServerTrustStoreFile(),
					propertiesConfig.getMailServerClientCertificateFile(),
					propertiesConfig.getMailServerClientCertificatePrivateKeyFile(),
					propertiesConfig.getMailServerClientCertificatePrivateKeyFilePassword() != null ? "***" : "null",
					propertiesConfig.getMailSmimeSigingKeyStoreFile(),
					propertiesConfig.getMailSmimeSigingKeyStorePassword() != null ? "***" : "null",
					propertiesConfig.getMailSendTestMailOnStartup());
		}
		else
		{
			logger.info(
					"Mail client config: SMTP client not configured, sending mails to debug log, configure at least SMTP server host and port");
		}

	}

	@EventListener({ ContextRefreshedEvent.class })
	public void onContextRefreshedEvent(ContextRefreshedEvent event) throws IOException
	{
		if (propertiesConfig.getMailSendTestMailOnStartup())
		{
			BuildInfoReader buildInfoReader = buildInfoReaderConfig.buildInfoReader();
			mailService().send("DSF Test Mail",
					"DSF startup test mail from BPE {" + "artifact: " + buildInfoReader.getProjectArtifact()
							+ ", version: " + buildInfoReader.getProjectVersion() + ", build: "
							+ buildInfoReader.getBuildDate().withZoneSameInstant(ZoneId.systemDefault())
									.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
							+ ", branch: " + buildInfoReader.getBuildBranch() + ", commit: "
							+ buildInfoReader.getBuildNumber() + "} send on "
							+ ZonedDateTime.now().withZoneSameInstant(ZoneId.systemDefault())
									.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
							+ ".");
		}
	}
}
