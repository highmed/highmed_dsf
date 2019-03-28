package org.highmed.fhir.search.parameters;

import org.highmed.fhir.search.SearchQueryParameter.SearchParameterDefinition;
import org.highmed.fhir.search.parameters.basic.AbstractIdentifierParameter;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.Enumerations.SearchParamType;

@SearchParameterDefinition(name = AbstractIdentifierParameter.PARAMETER_NAME, definition = "http://hl7.org/fhir/SearchParameter/Endpoint.​identifier", type = SearchParamType.TOKEN, documentation = "Identifies this endpoint across multiple systems")
public class EndpointIdentifier extends AbstractIdentifierParameter<Endpoint>
{
	public static final String RESOURCE_COLUMN = "endpoint";

	public EndpointIdentifier()
	{
		super(RESOURCE_COLUMN);
	}

	@Override
	public boolean matches(DomainResource resource)
	{
		if (!isDefined())
			throw notDefined();

		if (!(resource instanceof Endpoint))
			return false;

		Endpoint e = (Endpoint) resource;

		return identifierMatches(e.getIdentifier());
	}
}
