package org.highmed.dsf.fhir.webservice.impl;

import java.util.Objects;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.highmed.dsf.fhir.dao.command.CommandFactory;
import org.highmed.dsf.fhir.dao.command.CommandList;
import org.highmed.dsf.fhir.help.ExceptionHandler;
import org.highmed.dsf.fhir.help.ParameterConverter;
import org.highmed.dsf.fhir.help.ResponseGenerator;
import org.highmed.dsf.fhir.service.ReferenceCleaner;
import org.highmed.dsf.fhir.webservice.base.AbstractBasicService;
import org.highmed.dsf.fhir.webservice.specification.RootService;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.springframework.beans.factory.InitializingBean;

public class RootServiceImpl extends AbstractBasicService implements RootService, InitializingBean
{
	private final CommandFactory commandFactory;
	private final ResponseGenerator responseGenerator;
	private final ParameterConverter parameterConverter;
	private final ExceptionHandler exceptionHandler;
	private final ReferenceCleaner referenceCleaner;

	public RootServiceImpl(String path, CommandFactory commandFactory, ResponseGenerator responseGenerator,
			ParameterConverter parameterConverter, ExceptionHandler exceptionHandler, ReferenceCleaner referenceCleaner)
	{
		super(path);

		this.commandFactory = commandFactory;
		this.responseGenerator = responseGenerator;
		this.parameterConverter = parameterConverter;
		this.exceptionHandler = exceptionHandler;
		this.referenceCleaner = referenceCleaner;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		super.afterPropertiesSet();

		Objects.requireNonNull(commandFactory, "commandFactory");
		Objects.requireNonNull(responseGenerator, "responseGenerator");
		Objects.requireNonNull(parameterConverter, "parameterConverter");
		Objects.requireNonNull(exceptionHandler, "exceptionHandler");
	}

	@Override
	public Response root(UriInfo uri, HttpHeaders headers)
	{
		OperationOutcome outcome = responseGenerator.createOutcome(IssueSeverity.ERROR, IssueType.PROCESSING,
				"This is the base URL of the FHIR server. GET method not allowed");
		return responseGenerator
				.response(Status.METHOD_NOT_ALLOWED, outcome, parameterConverter.getMediaTypeThrowIfNotSupported(uri, headers)).build();
	}

	@Override
	public Response handleBundle(Bundle bundle, UriInfo uri, HttpHeaders headers)
	{
		// FIXME hapi parser bug workaround
		referenceCleaner.cleanReferenceResourcesIfBundle(bundle);

		CommandList commands = exceptionHandler.handleBadBundleException(
				() -> commandFactory.createCommands(bundle, getCurrentUser(), parameterConverter.getPrefer(headers)));

		Bundle result = commands.execute(); // throws WebApplicationException

		return responseGenerator.response(Status.OK, result, parameterConverter.getMediaTypeThrowIfNotSupported(uri, headers)).build();
	}
}
