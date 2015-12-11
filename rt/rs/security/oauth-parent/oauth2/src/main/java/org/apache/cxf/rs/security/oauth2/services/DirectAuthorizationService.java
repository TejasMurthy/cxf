/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.rs.security.oauth2.services;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.utils.ExceptionUtils;
import org.apache.cxf.rs.security.oauth2.common.AccessTokenRegistration;
import org.apache.cxf.rs.security.oauth2.common.Client;
import org.apache.cxf.rs.security.oauth2.common.ClientAccessToken;
import org.apache.cxf.rs.security.oauth2.common.ServerAccessToken;
import org.apache.cxf.rs.security.oauth2.common.UserSubject;
import org.apache.cxf.rs.security.oauth2.provider.OAuthServiceException;
import org.apache.cxf.rs.security.oauth2.provider.SubjectCreator;
import org.apache.cxf.rs.security.oauth2.utils.OAuthConstants;
import org.apache.cxf.rs.security.oauth2.utils.OAuthUtils;
import org.apache.cxf.security.SecurityContext;


@Path("/authorize-direct")
public class DirectAuthorizationService extends AbstractOAuthService {
    private SubjectCreator subjectCreator;
    private boolean partialMatchScopeValidation;
    @POST
    @Consumes("application/x-www-form-urlencoded")
    @Produces("text/html")
    public Response authorize(MultivaluedMap<String, String> params) {
        SecurityContext sc = getAndValidateSecurityContext(params);
        // Create a UserSubject representing the end user 
        UserSubject userSubject = createUserSubject(sc);
        Client client = getClient(params.getFirst(OAuthConstants.CLIENT_ID), params);
        
        AccessTokenRegistration reg = new AccessTokenRegistration();
        reg.setClient(client);
        reg.setGrantType(OAuthConstants.DIRECT_TOKEN_GRANT);
        reg.setSubject(userSubject);
        
        String providedScope = params.getFirst(OAuthConstants.SCOPE);
        List<String> requestedScope = OAuthUtils.getRequestedScopes(client, 
                                                           providedScope, 
                                                           partialMatchScopeValidation);
        
        reg.setRequestedScope(requestedScope);        
        reg.setApprovedScope(requestedScope);
        ServerAccessToken token = getDataProvider().createAccessToken(reg);
        ClientAccessToken clientToken = OAuthUtils.toClientAccessToken(token, isWriteOptionalParameters());
        return Response.ok(clientToken).build();
    }
    
    protected SecurityContext getAndValidateSecurityContext(MultivaluedMap<String, String> params) {
        SecurityContext securityContext =  
            (SecurityContext)getMessageContext().get(SecurityContext.class.getName());
        if (securityContext == null || securityContext.getUserPrincipal() == null) {
            throw ExceptionUtils.toNotAuthorizedException(null, null);
        }
        checkTransportSecurity();
        return securityContext;
    }
    protected UserSubject createUserSubject(SecurityContext securityContext) {
        UserSubject subject = null;
        if (subjectCreator != null) {
            subject = subjectCreator.createUserSubject(getMessageContext());
            if (subject != null) {
                return subject; 
            }
        }
        
        subject = getMessageContext().getContent(UserSubject.class);
        if (subject != null) {
            return subject;
        } else {
            return OAuthUtils.createSubject(securityContext);
        }
    }
    
    /**
     * Get the {@link Client} reference
     * @param clientId The Client Id
     * @param params request parameters
     * @return Client the client reference 
     * @throws {@link javax.ws.rs.WebApplicationException} if no matching Client is found, 
     *         the error is returned directly to the end user without 
     *         following the redirect URI if any
     */
    protected Client getClient(String clientId, MultivaluedMap<String, String> params) {
        Client client = null;
        String state = null;
        
        if (params != null) {
            state = params.getFirst(OAuthConstants.STATE);
        }
        
        try {
            client = getValidClient(clientId);
        } catch (OAuthServiceException ex) {
            if (ex.getError() != null) {
                ex.getError().setState(state);
                reportInvalidRequestError(ex.getError(), null);
            }
        }
        
        if (client == null) {
            reportInvalidRequestError("Client ID is invalid", state, null);
        }
        return client;
        
    }

    public SubjectCreator getSubjectCreator() {
        return subjectCreator;
    }

    public void setSubjectCreator(SubjectCreator subjectCreator) {
        this.subjectCreator = subjectCreator;
    }

    public boolean isPartialMatchScopeValidation() {
        return partialMatchScopeValidation;
    }

    public void setPartialMatchScopeValidation(boolean partialMatchScopeValidation) {
        this.partialMatchScopeValidation = partialMatchScopeValidation;
    }
}


