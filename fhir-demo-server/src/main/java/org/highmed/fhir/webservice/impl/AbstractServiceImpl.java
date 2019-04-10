package org.highmed.fhir.webservice.impl;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.highmed.fhir.authentication.UserProvider;
import org.highmed.fhir.dao.AbstractDomainResourceDao;
import org.highmed.fhir.event.EventGenerator;
import org.highmed.fhir.event.EventManager;
import org.highmed.fhir.help.ExceptionHandler;
import org.highmed.fhir.help.ParameterConverter;
import org.highmed.fhir.help.ResponseGenerator;
import org.highmed.fhir.search.PartialResult;
import org.highmed.fhir.search.SearchQuery;
import org.highmed.fhir.search.SearchQueryParameterError;
import org.highmed.fhir.service.ResourceValidator;
import org.highmed.fhir.webservice.specification.BasicService;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.UriType;
import org.hl7.fhir.r4.model.UrlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;

public abstract class AbstractServiceImpl<D extends AbstractDomainResourceDao<R>, R extends DomainResource>
		implements BasicService<R>, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(AbstractServiceImpl.class);

	protected final String resourceTypeName;
	protected final String serverBase;
	protected final int defaultPageCount;
	protected final D dao;
	protected final ResourceValidator validator;
	protected final EventManager eventManager;
	protected final ExceptionHandler exceptionHandler;
	protected final EventGenerator<R> eventGenerator;
	protected final ResponseGenerator responseGenerator;
	protected final ParameterConverter parameterConverter;

	public AbstractServiceImpl(String resourceTypeName, String serverBase, int defaultPageCount, D dao,
			ResourceValidator validator, EventManager eventManager, ExceptionHandler exceptionHandler,
			EventGenerator<R> eventGenerator, ResponseGenerator responseGenerator,
			ParameterConverter parameterConverter)
	{
		this.resourceTypeName = resourceTypeName;
		this.serverBase = serverBase;
		this.defaultPageCount = defaultPageCount;
		this.dao = dao;
		this.validator = validator;
		this.eventManager = eventManager;
		this.exceptionHandler = exceptionHandler;
		this.eventGenerator = eventGenerator;
		this.responseGenerator = responseGenerator;
		this.parameterConverter = parameterConverter;
	}

	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(resourceTypeName, "resourceTypeName");
		Objects.requireNonNull(serverBase, "serverBase");
		Objects.requireNonNull(defaultPageCount, "defaultPageCount");
		Objects.requireNonNull(dao, "dao");
		Objects.requireNonNull(validator, "validator");
		Objects.requireNonNull(eventManager, "eventManager");
		Objects.requireNonNull(exceptionHandler, "exceptionHandler");
		Objects.requireNonNull(eventGenerator, "eventGenerator");
		Objects.requireNonNull(responseGenerator, "responseGenerator");
		Objects.requireNonNull(parameterConverter, "parameterConverter");
	}

	@Override
	public void setUserProvider(UserProvider provider)
	{
	}

	@Override
	public String getPath()
	{
		throw new UnsupportedOperationException("implemented by jaxrs service layer");
	}

	@Override
	public Response create(R resource, UriInfo uri, HttpHeaders headers)
	{
		checkAlreadyExists(uri, headers); // might throw errors

		Consumer<R> postCreate = preCreate(resource);

		R createdResource = exceptionHandler.handleSqlException(() -> dao.create(resource));

		eventManager.handleEvent(eventGenerator.newResourceCreatedEvent(createdResource));

		if (postCreate != null)
			postCreate.accept(createdResource);

		URI location = uri.getAbsolutePathBuilder().path("/{id}/_history/{vid}")
				.build(createdResource.getIdElement().getIdPart(), createdResource.getIdElement().getVersionIdPart());

		return responseGenerator
				.response(Status.CREATED, createdResource, parameterConverter.getMediaType(uri, headers))
				.location(location).build();
	}

	private void checkAlreadyExists(UriInfo uri, HttpHeaders headers) throws WebApplicationException
	{
		Optional<String> ifNoneExistsHeader = getHeaderString(headers, Constants.HEADER_IF_NONE_EXIST,
				Constants.HEADER_IF_NONE_EXIST_LC);

		if (ifNoneExistsHeader.isEmpty())
			return; // header not found, nothing to check against

		if (ifNoneExistsHeader.get().isBlank())
			throw exceptionHandler.badIfNoneExistHeaderValue(ifNoneExistsHeader.get());

		String ifNoneExistsHeaderValue = ifNoneExistsHeader.get();
		if (!ifNoneExistsHeaderValue.contains("?"))
			ifNoneExistsHeaderValue = '?' + ifNoneExistsHeaderValue;

		UriComponents componentes = UriComponentsBuilder.fromUriString(ifNoneExistsHeaderValue).build();
		String path = componentes.getPath();
		if (path != null && !path.isBlank())
			throw exceptionHandler.badIfNoneExistHeaderValue(ifNoneExistsHeader.get());

		Map<String, List<String>> queryParameters = componentes.getQueryParams();
		if (Arrays.stream(SearchQuery.STANDARD_PARAMETERS).anyMatch(queryParameters::containsKey))
		{
			logger.warn(
					"{} Header contains query parameter not applicable in this conditional create context: '{}', parameters {} will be ignored",
					Constants.HEADER_IF_NONE_EXIST, ifNoneExistsHeader.get(),
					Arrays.toString(SearchQuery.STANDARD_PARAMETERS));

			queryParameters = queryParameters.entrySet().stream()
					.filter(e -> !Arrays.stream(SearchQuery.STANDARD_PARAMETERS).anyMatch(p -> p.equals(e.getKey())))
					.collect(Collectors.toMap(Entry::getKey, Entry::getValue));
		}

		SearchQuery<R> query = dao.createSearchQuery(0, 0);
		query.configureParameters(queryParameters);

		List<SearchQueryParameterError> unsupportedQueryParameters = query
				.getUnsupportedQueryParameters(queryParameters);
		if (!unsupportedQueryParameters.isEmpty())
			throw exceptionHandler.badIfNoneExistHeaderValue(ifNoneExistsHeader.get(), unsupportedQueryParameters);

		PartialResult<R> result = exceptionHandler.handleSqlException(() -> dao.search(query));
		if (result.getOverallCount() == 1)
			throw exceptionHandler.oneExists(resourceTypeName, ifNoneExistsHeader.get(), uri);
		else if (result.getOverallCount() > 1)
			throw exceptionHandler.multipleExists(resourceTypeName, ifNoneExistsHeader.get());
	}

	private Optional<String> getHeaderString(HttpHeaders headers, String... headerNames)
	{
		return Arrays.stream(headerNames).map(name -> headers.getHeaderString(name)).filter(h -> h != null).findFirst();
	}

	/**
	 * Override to modify the given resource before db insert, throw {@link WebApplicationException} to interrupt the
	 * normal flow
	 * 
	 * @param resource
	 *            not <code>null</code>
	 * @return if not null, the returned {@link Consumer} will be called after the create operation and before returning
	 *         to the client, the {@link Consumer} can throw a {@link WebApplicationException} to interrupt the normal
	 *         flow, the {@link Consumer} will be called with the created resource
	 * @throws WebApplicationException
	 *             if the normal flow should be interrupted
	 */
	protected Consumer<R> preCreate(R resource) throws WebApplicationException
	{
		return null;
	}

	@Override
	public Response read(String id, UriInfo uri, HttpHeaders headers)
	{
		Optional<R> read = exceptionHandler.handleSqlAndResourceDeletedException(resourceTypeName,
				() -> dao.read(parameterConverter.toUuid(resourceTypeName, id)));

		if (read.isPresent())
		{
			return responseGenerator.response(Status.OK, read.get(), parameterConverter.getMediaType(uri, headers))
					.build();
		}
		else
			return Response.status(Status.NOT_FOUND).build(); // TODO return OperationOutcome
	}

	@Override
	public Response vread(String id, long version, UriInfo uri, HttpHeaders headers)
	{
		Optional<R> read = exceptionHandler
				.handleSqlException(() -> dao.readVersion(parameterConverter.toUuid(resourceTypeName, id), version));

		if (read.isPresent())
		{
			return responseGenerator.response(Status.OK, read.get(), parameterConverter.getMediaType(uri, headers))
					.build();
		}
		else
			return Response.status(Status.NOT_FOUND).build(); // TODO return OperationOutcome
	}

	@Override
	public Response update(String id, R resource, UriInfo uri, HttpHeaders headers)
	{
		IdType resourceId = resource.getIdElement();

		if (!Objects.equals(id, resourceId.getIdPart()))
			return responseGenerator.createPathVsElementIdResponse(resourceTypeName, id, resourceId);
		if (resourceId.getBaseUrl() != null && !serverBase.equals(resourceId.getBaseUrl()))
			return responseGenerator.createInvalidBaseUrlResponse(resourceTypeName, resourceId);

		Consumer<R> postUpdate = preUpdate(resource);

		R updatedResource = exceptionHandler.handleSqlAndResourceNotFoundException(resourceTypeName,
				() -> dao.update(resource));

		eventManager.handleEvent(eventGenerator.newResourceUpdatedEvent(updatedResource));

		if (postUpdate != null)
			postUpdate.accept(updatedResource);

		return responseGenerator.response(Status.OK, updatedResource, parameterConverter.getMediaType(uri, headers))
				.build();
	}

	/**
	 * Override to modify the given resource before db update, throw {@link WebApplicationException} to interrupt the
	 * normal flow. Path id vs. resource.id.idPart is checked before this method is called
	 * 
	 * @param resource
	 *            not <code>null</code>
	 * @return if not null, the returned {@link Consumer} will be called after the update operation and before returning
	 *         to the client, the {@link Consumer} can throw a {@link WebApplicationException} to interrupt the normal
	 *         flow, the {@link Consumer} will be called with the updated resource
	 * @throws WebApplicationException
	 *             if the normal flow should be interrupted
	 */
	protected Consumer<R> preUpdate(R resource)
	{
		return null;
	}

	/*
	 * No matches, no id provided: The server creates the resource.
	 * 
	 * No matches, id provided: The server treats the interaction as an Update as Create interaction (or rejects it, if
	 * it does not support Update as Create) -> reject
	 * 
	 * One Match, no resource id provided OR (resource id provided and it matches the found resource): The server
	 * performs the update against the matching resource
	 * 
	 * One Match, resource id provided but does not match resource found: The server returns a 400 Bad Request error
	 * indicating the client id specification was a problem preferably with an OperationOutcome
	 * 
	 * Multiple matches: The server returns a 412 Precondition Failed error indicating the client's criteria were not
	 * selective enough preferably with an OperationOutcome
	 */
	@Override
	public Response update(R resource, UriInfo uri, HttpHeaders headers)
	{
		Map<String, List<String>> queryParameters = uri.getQueryParameters();
		if (Arrays.stream(SearchQuery.STANDARD_PARAMETERS).anyMatch(queryParameters::containsKey))
		{
			logger.warn(
					"Query contains parameter not applicable in this conditional update context: '{}', parameters {} will be ignored",
					UriComponentsBuilder.newInstance()
							.replaceQueryParams(CollectionUtils.toMultiValueMap(queryParameters)).toUriString(),
					Arrays.toString(SearchQuery.STANDARD_PARAMETERS));

			queryParameters = queryParameters.entrySet().stream()
					.filter(e -> !Arrays.stream(SearchQuery.STANDARD_PARAMETERS).anyMatch(p -> p.equals(e.getKey())))
					.collect(Collectors.toMap(Entry::getKey, Entry::getValue));
		}

		SearchQuery<R> query = dao.createSearchQuery(1, 1);
		query.configureParameters(queryParameters);

		List<SearchQueryParameterError> unsupportedQueryParameters = query
				.getUnsupportedQueryParameters(queryParameters);
		if (!unsupportedQueryParameters.isEmpty())
			throw exceptionHandler.badRequest(
					UriComponentsBuilder.newInstance()
							.replaceQueryParams(CollectionUtils.toMultiValueMap(queryParameters)).toUriString(),
					unsupportedQueryParameters);

		PartialResult<R> result = exceptionHandler.handleSqlException(() -> dao.search(query));

		// No matches, no id provided: The server creates the resource.
		if (result.getOverallCount() <= 0 && !resource.hasId())
			return create(resource, uri, headers);

		// No matches, id provided: The server treats the interaction as an Update as Create interaction (or rejects it,
		// if it does not support Update as Create) -> reject
		else if (result.getOverallCount() <= 0 && resource.hasId())
			throw exceptionHandler.methodNotAllowed(resourceTypeName, resource.getId());

		// One Match, no resource id provided OR (resource id provided and it matches the found resource):
		// The server performs the update against the matching resource
		else if (result.getOverallCount() == 1)
		{
			R dbResource = result.getPartialResult().get(0);
			IdType dbResourceId = dbResource.getIdElement();
			IdType resourceId = resource.getIdElement();

			// BaseUrl - The server base URL (e.g. "http://example.com/fhir")
			// ResourceType - The resource type (e.g. "Patient")
			// IdPart - The ID (e.g. "123")
			// Version - The version ID ("e.g. "456")
			if (!resource.hasId())
				return update(dbResource.getId(), resource, uri, headers);
			else if (resource.hasId() && (!resourceId.hasBaseUrl() || serverBase.equals(resourceId.getBaseUrl()))
					&& (!resourceId.hasResourceType() || resourceTypeName.equals(resourceId.getResourceType()))
					&& (dbResourceId.getIdPart().equals(resourceId.getIdPart())))
				return update(dbResource.getId(), resource, uri, headers);
			else
				throw exceptionHandler.badRequestIdsNotMatching(
						dbResourceId.withServerBase(serverBase, resourceTypeName),
						resourceId.hasBaseUrl() && resourceId.hasResourceType() ? resourceId
								: resourceId.withServerBase(serverBase, resourceTypeName));
		}
		// Multiple matches: The server returns a 412 Precondition Failed error indicating the client's criteria were
		// not selective enough preferably with an OperationOutcome
		else // if (result.getOverallCount() > 1)
			throw exceptionHandler.multipleExists(resourceTypeName, UriComponentsBuilder.newInstance()
					.replaceQueryParams(CollectionUtils.toMultiValueMap(queryParameters)).toUriString());
	}

	@Override
	public Response delete(String id, UriInfo uri, HttpHeaders headers)
	{
		Consumer<String> afterDelete = preDelete(id);

		exceptionHandler.handleSqlException(() -> dao.delete(parameterConverter.toUuid(resourceTypeName, id)));

		eventManager.handleEvent(eventGenerator.newResourceDeletedEvent(id));

		afterDelete.accept(id);

		return Response.ok().build(); // TODO return OperationOutcome
	}

	/**
	 * Override to perform actions pre delete, throw {@link WebApplicationException} to interrupt the normal flow.
	 * 
	 * @param id
	 *            of the resource to be deleted
	 * @return if not null, the returned {@link Consumer} will be called after the create operation and before returning
	 *         to the client, the {@link Consumer} can throw a {@link WebApplicationException} to interrupt the normal
	 *         flow, the {@link Consumer} will be called with the id ({@link IdType#getIdPart()}) of the deleted
	 *         resource
	 * @throws WebApplicationException
	 *             if the normal flow should be interrupted
	 */
	protected Consumer<String> preDelete(String id)
	{
		return null;
	}

	@Override
	public Response delete(UriInfo uri, HttpHeaders headers)
	{
		// MultivaluedMap<String, String> queryParameters = uri.getQueryParameters();
		//
		// Integer page = parameterConverter.getFirstInt(queryParameters, "_page");
		// int effectivePage = page == null ? 1 : page;
		//
		// Integer count = parameterConverter.getFirstInt(queryParameters, "_count");
		// int effectiveCount = (count == null || count < 0) ? defaultPageCount : count;
		//
		// SearchQuery<R> query = dao.createSearchQuery(effectivePage, effectiveCount);
		// query.configureParameters(queryParameters);
		//
		// PartialResult<R> result = exceptionHandler.handleSqlException(() -> dao.search(query));
		//
		// UriBuilder bundleUri = query.configureBundleUri(uri.getAbsolutePathBuilder());
		//
		// String format = queryParameters.getFirst("_format");
		// String pretty = queryParameters.getFirst("_pretty");
		// Bundle searchSet = responseGenerator.createSearchSet(result, bundleUri, format, pretty);
		//
		// return responseGenerator.response(Status.OK, searchSet, parameterConverter.getMediaType(uri,
		// headers)).build();

		return null; // TODO conditional delete
	}

	@Override
	public Response search(UriInfo uri, HttpHeaders headers)
	{
		MultivaluedMap<String, String> queryParameters = uri.getQueryParameters();

		Integer page = parameterConverter.getFirstInt(queryParameters, SearchQuery.PARAMETER_PAGE);
		int effectivePage = page == null ? 1 : page;

		Integer count = parameterConverter.getFirstInt(queryParameters, SearchQuery.PARAMETER_COUNT);
		int effectiveCount = (count == null || count < 0) ? defaultPageCount : count;

		SearchQuery<R> query = dao.createSearchQuery(effectivePage, effectiveCount);
		query.configureParameters(queryParameters);
		List<SearchQueryParameterError> errors = query.getUnsupportedQueryParameters(queryParameters);
		// TODO throw error if strict param handling is configured, include warning else

		PartialResult<R> result = exceptionHandler.handleSqlException(() -> dao.search(query));

		UriBuilder bundleUri = query.configureBundleUri(uri.getAbsolutePathBuilder());

		String format = queryParameters.getFirst(SearchQuery.PARAMETER_FORMAT);
		String pretty = queryParameters.getFirst(SearchQuery.PARAMETER_PRETTY);
		Bundle searchSet = responseGenerator.createSearchSet(result, errors, bundleUri, format, pretty);

		return responseGenerator.response(Status.OK, searchSet, parameterConverter.getMediaType(uri, headers)).build();
	}

	private Optional<Resource> getResource(Parameters parameters, String parameterName)
	{
		return parameters.getParameter().stream().filter(p -> parameterName.equals(p.getName())).findFirst()
				.map(ParametersParameterComponent::getResource);
	}

	private OperationOutcome createValidationOutcomeError(List<SingleValidationMessage> messages)
	{
		OperationOutcome outcome = new OperationOutcome();
		List<OperationOutcomeIssueComponent> issues = messages.stream().map(vm -> new OperationOutcomeIssueComponent()
				.setSeverity(toSeverity(vm.getSeverity())).setCode(IssueType.STRUCTURE).setDiagnostics(vm.getMessage()))
				.collect(Collectors.toList());
		outcome.setIssue(issues);
		return outcome;
	}

	private IssueSeverity toSeverity(ResultSeverityEnum resultSeverity)
	{
		switch (resultSeverity)
		{
			case ERROR:
				return IssueSeverity.ERROR;
			case FATAL:
				return IssueSeverity.FATAL;
			case INFORMATION:
				return IssueSeverity.INFORMATION;
			case WARNING:
				return IssueSeverity.WARNING;
			default:
				return IssueSeverity.NULL;
		}
	}

	private OperationOutcome createValidationOutcomeOk(List<SingleValidationMessage> messages, List<String> profiles)
	{
		OperationOutcome outcome = new OperationOutcome();

		List<OperationOutcomeIssueComponent> issues = messages.stream().map(vm -> new OperationOutcomeIssueComponent()
				.setSeverity(toSeverity(vm.getSeverity())).setCode(IssueType.STRUCTURE).setDiagnostics(vm.getMessage()))
				.collect(Collectors.toList());
		outcome.setIssue(issues);

		OperationOutcomeIssueComponent ok = new OperationOutcomeIssueComponent().setSeverity(IssueSeverity.INFORMATION)
				.setCode(IssueType.INFORMATIONAL)
				.setDiagnostics("Resource validated" + (profiles.isEmpty() ? ""
						: " with profile" + (profiles.size() > 1 ? "s " : " ")
								+ profiles.stream().collect(Collectors.joining(", "))));
		outcome.addIssue(ok);

		return outcome;
	}

	@Override
	public Response postValidateNew(String validate, Parameters parameters, UriInfo uri, HttpHeaders headers)
	{
		Optional<Resource> resource = getResource(parameters, "resource");
		if (resource.isEmpty())
			return Response.status(Status.BAD_REQUEST).build(); // TODO return OperationOutcome, hint post with id url?

		Type mode = parameters.getParameter("mode");
		if (!(mode instanceof CodeType))
			return Response.status(Status.BAD_REQUEST).build(); // TODO return OperationOutcome

		Type profile = parameters.getParameter("profile");
		if (!(profile instanceof UriType))
			return Response.status(Status.BAD_REQUEST).build(); // TODO return OperationOutcome

		// TODO handle mode and profile parameters

		// ValidationResult validationResult = validator.validate(resource.get());

		// TODO create return values

		return Response.ok().build();
	}

	@Override
	public Response getValidateNew(String validate, UriInfo uri, HttpHeaders headers)
	{
		// MultivaluedMap<String, String> queryParameters = uri.getQueryParameters();
		//
		// String mode = queryParameters.getFirst("mode");
		// String profile = queryParameters.getFirst("profile");

		// mode = create

		// TODO Auto-generated method stub
		return Response.ok().build();
	}

	@Override
	public Response postValidateExisting(String validate, String id, Parameters parameters, UriInfo uri,
			HttpHeaders headers)
	{
		if (getResource(parameters, "resource").isPresent())
			return Response.status(Status.BAD_REQUEST).build(); // TODO return OperationOutcome

		Type mode = parameters.getParameter("mode");
		if (!(mode instanceof CodeType) || !(mode instanceof StringType))
			return Response.status(Status.BAD_REQUEST).build(); // TODO return OperationOutcome
		if (!"profile".equals(((StringType) mode).getValue()))
			return Response.status(Status.BAD_REQUEST).build(); // TODO return OperationOutcome

		Type profile = parameters.getParameter("profile");
		if (!(profile instanceof UriType) || !(mode instanceof UrlType) || !(mode instanceof CanonicalType))
			return Response.status(Status.BAD_REQUEST).build(); // TODO return OperationOutcome

		UriType profileUri = (UriType) profile;

		Optional<R> read = exceptionHandler.handleSqlAndResourceDeletedException(resourceTypeName,
				() -> dao.read(parameterConverter.toUuid(resourceTypeName, id)));

		R resource = read.get();
		resource.getMeta().setProfile(Collections.singletonList(new CanonicalType(profileUri.getValue())));

		ValidationResult result = validator.validate(resource);

		if (result.isSuccessful())
			return responseGenerator.response(Status.OK,
					createValidationOutcomeOk(result.getMessages(), Collections.singletonList(profileUri.getValue())),
					parameterConverter.getMediaType(uri, headers)).build();
		else
			return responseGenerator.response(Status.OK, createValidationOutcomeError(result.getMessages()),
					parameterConverter.getMediaType(uri, headers)).build();
	}

	@Override
	public Response getValidateExisting(String validate, String id, UriInfo uri, HttpHeaders headers)
	{
		MultivaluedMap<String, String> queryParameters = uri.getQueryParameters();

		String mode = queryParameters.getFirst("mode");
		if (mode == null)
			mode = "profile";
		String profile = queryParameters.getFirst("profile");

		if ("profile".equals(mode))
		{
			Optional<R> read = exceptionHandler.handleSqlAndResourceDeletedException(resourceTypeName,
					() -> dao.read(parameterConverter.toUuid(resourceTypeName, id)));

			R resource = read.get();
			if (profile != null)
				resource.getMeta().setProfile(Collections.singletonList(new CanonicalType(profile)));

			ValidationResult result = validator.validate(resource);

			if (result.isSuccessful())
				return responseGenerator.response(Status.OK,
						createValidationOutcomeOk(result.getMessages(),
								resource.getMeta().getProfile().stream().map(t -> t.getValue())
										.collect(Collectors.toList())),
						parameterConverter.getMediaType(uri, headers)).build();
			else
				return responseGenerator.response(Status.OK, createValidationOutcomeError(result.getMessages()),
						parameterConverter.getMediaType(uri, headers)).build();
		}
		else if ("delete".equals(mode))
			return Response.status(Status.METHOD_NOT_ALLOWED).build(); // TODO mode = delete
		else
			return Response.status(Status.METHOD_NOT_ALLOWED).build(); // TODO return OperationOutcome
	}
}
