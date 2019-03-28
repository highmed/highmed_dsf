package org.highmed.fhir.search.parameters;

import org.highmed.fhir.search.SearchQueryParameter.SearchParameterDefinition;
import org.highmed.fhir.search.parameters.basic.AbstractIdentifierParameter;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Enumerations.SearchParamType;
import org.hl7.fhir.r4.model.ValueSet;

@SearchParameterDefinition(name = AbstractIdentifierParameter.PARAMETER_NAME, definition = "http://hl7.org/fhir/SearchParameter/ValueSet.​identifier", type = SearchParamType.TOKEN, documentation = "External identifier for the value set")
public class ValueSetIdentifier extends AbstractIdentifierParameter<ValueSet>
{
	public static final String RESOURCE_COLUMN = "value_set";

	public ValueSetIdentifier()
	{
		super(RESOURCE_COLUMN);
	}

	@Override
	public boolean matches(DomainResource resource)
	{
		if (!isDefined())
			throw notDefined();

		if (!(resource instanceof ValueSet))
			return false;

		ValueSet v = (ValueSet) resource;

		return identifierMatches(v.getIdentifier());
	}
}
