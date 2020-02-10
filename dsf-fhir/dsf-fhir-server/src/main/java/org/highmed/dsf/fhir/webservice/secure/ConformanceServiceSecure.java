package org.highmed.dsf.fhir.webservice.secure;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.highmed.dsf.fhir.help.ResponseGenerator;
import org.highmed.dsf.fhir.webservice.specification.ConformanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConformanceServiceSecure extends AbstractServiceSecure<ConformanceService> implements ConformanceService
{
	private static final Logger logger = LoggerFactory.getLogger(ConformanceServiceSecure.class);

	public ConformanceServiceSecure(ConformanceService delegate, ResponseGenerator responseGenerator)
	{
		super(delegate, responseGenerator);
	}

	@Override
	public Response getMetadata(String mode, UriInfo uri, HttpHeaders headers)
	{
		logger.debug("Current user '{}', role '{}'", provider.getCurrentUser().getName(),
				provider.getCurrentUser().getRole());

		// get metadata allowed for all authenticated users

		return delegate.getMetadata(mode, uri, headers);
	}
}
