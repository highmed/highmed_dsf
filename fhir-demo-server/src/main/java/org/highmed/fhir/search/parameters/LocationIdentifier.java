package org.highmed.fhir.search.parameters;

import org.highmed.fhir.search.SearchQueryParameter.SearchParameterDefinition;
import org.highmed.fhir.search.parameters.basic.AbstractIdentifierParameter;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Enumerations.SearchParamType;
import org.hl7.fhir.r4.model.Location;

@SearchParameterDefinition(name = AbstractIdentifierParameter.PARAMETER_NAME, definition = "http://hl7.org/fhir/SearchParameter/Location.​identifier", type = SearchParamType.TOKEN, documentation = "An identifier for the location")
public class LocationIdentifier extends AbstractIdentifierParameter<Location>
{
	public static final String RESOURCE_COLUMN = "location";

	public LocationIdentifier()
	{
		super(RESOURCE_COLUMN);
	}

	@Override
	public boolean matches(DomainResource resource)
	{
		if (!isDefined())
			throw notDefined();

		if (!(resource instanceof Location))
			return false;

		Location l = (Location) resource;

		return identifierMatches(l.getIdentifier());
	}
}
