package org.highmed.dsf.fhir.webservice.specification;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

public interface StaticResourcesService extends BasicService
{
	Response getFile(String fileName, UriInfo uri, HttpHeaders headers);
}
