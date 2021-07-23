package org.highmed.dsf.fhir.authorization;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.highmed.dsf.fhir.authentication.OrganizationProvider;
import org.highmed.dsf.fhir.authentication.User;
import org.highmed.dsf.fhir.authorization.read.ReadAccessHelper;
import org.highmed.dsf.fhir.dao.BundleDao;
import org.highmed.dsf.fhir.dao.provider.DaoProvider;
import org.highmed.dsf.fhir.service.ReferenceResolver;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BundleAuthorizationRule extends AbstractMetaTagAuthorizationRule<Bundle, BundleDao>
{
	private static final Logger logger = LoggerFactory.getLogger(BundleAuthorizationRule.class);

	public BundleAuthorizationRule(DaoProvider daoProvider, String serverBase, ReferenceResolver referenceResolver,
			OrganizationProvider organizationProvider, ReadAccessHelper readAccessHelper)
	{
		super(Bundle.class, daoProvider, serverBase, referenceResolver, organizationProvider, readAccessHelper);
	}

	protected Optional<String> newResourceOk(Connection connection, User user, Bundle newResource)
	{
		List<String> errors = new ArrayList<String>();

		if (!hasValidReadAccessTag(connection, newResource))
		{
			errors.add("Bundle is missing valid read access tag");
		}

		if (errors.isEmpty())
			return Optional.empty();
		else
			return Optional.of(errors.stream().collect(Collectors.joining(", ")));
	}

	@Override
	protected boolean resourceExists(Connection connection, Bundle newResource)
	{
		// no unique criteria for Bundle
		return false;
	}

	@Override
	protected boolean modificationsOk(Connection connection, Bundle oldResource, Bundle newResource)
	{
		// no unique criteria for Bundle
		return true;
	}

	@Override
	public Optional<String> reasonExpungeAllowed(Connection connection, User user, Bundle oldResource)
	{
		if (isLocalUser(user))
		{
			logger.info("Expunge of Bundle authorized for local user '{}'", user.getName());
			return Optional.of("local user");
		}
		else
		{
			logger.warn("Expunge of Bundle unauthorized, not a local user");
			return Optional.empty();
		}
	}
}
