package org.highmed.fhir.search.parameters;

import org.highmed.fhir.search.SearchQueryParameter.SearchParameterDefinition;
import org.highmed.fhir.search.parameters.basic.AbstractIdentifierParameter;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Enumerations.SearchParamType;
import org.hl7.fhir.r4.model.PractitionerRole;

@SearchParameterDefinition(name = AbstractIdentifierParameter.PARAMETER_NAME, definition = "http://hl7.org/fhir/SearchParameter/PractitionerRole.​identifier", type = SearchParamType.TOKEN, documentation = "A practitioner's Identifier")
public class PractitionerRoleIdentifier extends AbstractIdentifierParameter<PractitionerRole>
{
	public static final String RESOURCE_COLUMN = "practitioner_role";

	public PractitionerRoleIdentifier()
	{
		super(RESOURCE_COLUMN);
	}

	@Override
	public boolean matches(DomainResource resource)
	{
		if (!isDefined())
			throw notDefined();

		if (!(resource instanceof PractitionerRole))
			return false;

		PractitionerRole p = (PractitionerRole) resource;

		return identifierMatches(p.getIdentifier());
	}
}
