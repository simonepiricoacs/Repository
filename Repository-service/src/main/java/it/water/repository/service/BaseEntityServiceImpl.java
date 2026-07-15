
/*
 * Copyright 2024 Aristide Cittadino
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.water.repository.service;


import it.water.core.api.bundle.Runtime;
import it.water.core.api.entity.owned.OwnedResource;
import it.water.core.api.entity.shared.SharedEntity;
import it.water.core.api.entity.tenant.MultiTenantResource;
import it.water.core.api.entity.tenant.TenantResource;
import it.water.core.api.model.BaseEntity;
import it.water.core.api.model.PaginableResult;
import it.water.core.api.permission.SecurityContext;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.repository.query.Query;
import it.water.core.api.repository.query.QueryBuilder;
import it.water.core.api.repository.query.QueryOrder;
import it.water.core.api.service.BaseEntityApi;
import it.water.core.api.service.BaseEntitySystemApi;
import it.water.core.api.service.integration.SharedEntityIntegrationClient;
import it.water.core.api.service.integration.TenantMembershipResolver;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.permission.action.CrudActions;
import it.water.core.permission.annotations.AllowGenericPermissions;
import it.water.core.permission.annotations.AllowPermissions;
import it.water.core.permission.annotations.AllowPermissionsOnReturn;
import it.water.core.permission.exceptions.UnauthorizedException;
import it.water.core.registry.model.exception.NoComponentRegistryFoundException;
import it.water.core.service.BaseAbstractService;
import it.water.repository.entity.model.exceptions.EntityNotFound;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Set;


/**
 * @param <T> parameter that indicates a generic class.
 * @Author Aristide Cittadino.
 * Abstract Service class for Entity.
 * This class implements the methods for basic CRUD operations.
 * This methods are reusable by all entities in order to interact with the
 * system layer.
 */
public abstract class BaseEntityServiceImpl<T extends BaseEntity> extends BaseAbstractService implements BaseEntityApi<T> {
    private Logger log = LoggerFactory.getLogger(this.getClass().getName());

    @Inject
    @Setter
    @Getter(AccessLevel.PROTECTED)
    private Runtime runtime;

    /**
     * Generic class for  platform
     */
    private final Class<T> type;

    /**
     * Constructor for BaseEntityServiceImpl
     *
     * @param type parameter that indicates a generic entity
     */
    protected BaseEntityServiceImpl(Class<T> type) {
        this.type = type;
    }

    /**
     * Save an entity in datacore
     *
     * @param entity parameter that indicates a generic entity
     * @return entity saved
     */
    @AllowPermissions(actions = {CrudActions.SAVE})
    public T save(T entity) {
        this.log.debug("Service Saving entity {}: {}", this.type.getSimpleName(), entity);
        //automatic setting ownership on entity
        if (entity instanceof OwnedResource ownedResource) {
            Long ownerUserId = (runtime != null) ? runtime.getSecurityContext().getLoggedEntityId() : 0;
            ownedResource.setOwnerUserId(ownerUserId);
        }
        //automatic setting tenancy on entity: only when a company is active on the current session
        //(lenient rule); when no company is active the entity stays global/unassigned, preserving
        //the single-tenant behaviour.
        if (entity instanceof TenantResource tenantResource && runtime != null) {
            Long activeCompanyId = runtime.getSecurityContext().getActiveCompanyId();
            if (activeCompanyId != null) {
                tenantResource.setCompanyId(activeCompanyId);
            }
        }
        return this.getSystemService().save(entity);
    }

    /**
     * Update an existing entity in datacore
     *
     * @param entity parameter that indicates a generic entity
     */

    @AllowPermissions(actions = {CrudActions.UPDATE})
    public T update(T entity) {
        this.log.debug("Service Updating entity entity {}: {} ", this.type.getSimpleName(), entity);
        if (entity.getId() > 0) {
            // #38: on the generic update path the client must never be able to change the owner of an
            // OwnedResource (give it away or null it). Always restore ownerUserId from the currently
            // persisted entity before delegating to the system service. This is intentionally applied
            // to admins too: the generic update is not the place to transfer ownership (a dedicated
            // operation should be used for that). Note this only fixes the persisted field value; the
            // H5 ownership/permission interceptor independently reloads the entity for authorization.
            // Load the persisted entity once and restore server-controlled, non-transferable fields
            // (ownerUserId and companyId) so the client can never hijack ownership or move an entity
            // across tenants via the generic update path. Applied to admins too.
            if (entity instanceof OwnedResource || entity instanceof TenantResource) {
                BaseEntity persisted = this.getSystemService().find(entity.getId());
                if (entity instanceof OwnedResource ownedResource && persisted instanceof OwnedResource persistedOwned) {
                    ownedResource.setOwnerUserId(persistedOwned.getOwnerUserId());
                }
                if (entity instanceof TenantResource tenantResource && persisted instanceof TenantResource persistedTenant) {
                    tenantResource.setCompanyId(persistedTenant.getCompanyId());
                }
            }
            return this.getSystemService().update(entity);
        }
        throw new EntityNotFound();
    }

    /**
     * Remove an entity in datacore
     *
     * @param id parameter that indicates a entity id
     */
    @AllowPermissions(actions = CrudActions.REMOVE, checkById = true)
    public void remove(long id) {
        this.log.debug("Service Removing entity {} with id {}", this.type.getSimpleName(), id);
        BaseEntity entity = this.getSystemService().find(id);
        if (entity != null) {
            this.getSystemService().remove(entity.getId());
            return;
        }
        throw new EntityNotFound();
    }

    /**
     * Find an existing entity in datacore
     *
     * @param id parameter that indicates a entity id
     * @return Entity if found
     */
    @AllowPermissions(actions = CrudActions.FIND, checkById = true)
    public T find(long id) {
        Query queryFilter = getSystemService().getQueryBuilderInstance().createQueryFilter("id=" + id);
        return this.find(queryFilter);
    }

    /**
     * @param filter filter
     * @return
     */
    @Override
    @AllowGenericPermissions(actions = {CrudActions.FIND})
    @AllowPermissionsOnReturn(actions = {CrudActions.FIND})
    public T find(Query filter) {
        this.log.debug("Service Find entity {} with id {}", this.type.getSimpleName(), filter);
        SecurityContext securityContext = runtime.getSecurityContext();
        filter = this.createConditionForOwnedOrSharedResource(filter, securityContext);
        filter = this.createConditionForTenantResource(filter, securityContext);
        return this.getSystemService().find(filter);
    }

    /**
     * @param queryOrder parameters that define order's criteria
     * @param filter     filter
     * @param delta
     * @param page
     * @return
     */
    @Override
    @AllowGenericPermissions(actions = CrudActions.FIND_ALL)
    public PaginableResult<T> findAll(Query filter, int delta, int page, QueryOrder queryOrder) {
        this.log.debug("Service Find all entities {} ", this.type.getSimpleName());
        SecurityContext securityContext = runtime.getSecurityContext();
        filter = this.createConditionForOwnedOrSharedResource(filter, securityContext);
        filter = this.createConditionForTenantResource(filter, securityContext);
        return this.getSystemService().findAll(filter, delta, page, queryOrder);
    }

    /**
     * @param initialFilter
     * @return
     */
    private Query createConditionForOwnedOrSharedResource(Query initialFilter, SecurityContext securityContext) {
        if (OwnedResource.class.isAssignableFrom(this.getEntityType())) {
            if (securityContext == null)
                throw new UnauthorizedException();
            //admins can see everything
            if (!securityContext.isAdmin()) {
                Query ownedResourceFilter = null;

                if (securityContext.getLoggedEntityId() != 0) {
                    ownedResourceFilter = getSystemService().getQueryBuilderInstance().field(OwnedResource.getOwnerUserIdFieldName()).equalTo(securityContext.getLoggedEntityId());
                } else {
                    throw new UnauthorizedException();
                }

                ownedResourceFilter = this.createFilterForOwnedOrSharedResource(ownedResourceFilter, securityContext.getLoggedEntityId());

                if (initialFilter == null) initialFilter = ownedResourceFilter;
                else if (ownedResourceFilter != null) {
                    initialFilter = initialFilter.and(ownedResourceFilter);
                }
            }
        }
        return initialFilter;
    }

    /**
     * Builds the tenant-scoping condition for tenant-aware entities and AND-s it onto the incoming
     * filter. This is independent of (and complementary to) the ownership/shared condition: an entity
     * can be both an OwnedResource and a TenantResource and must satisfy BOTH.
     * <p>
     * Lenient rule (backward compatible): enforcement kicks in ONLY when a company is active on the
     * current SecurityContext. When {@code getActiveCompanyId()} is null (MT off, non-scoped admin,
     * legacy token) no condition is added and the behaviour is identical to single-tenant. There is
     * intentionally NO {@code isAdmin()} special-casing here: admin scoping derives purely from
     * whether a company is active.
     *
     * @param initialFilter   the filter accumulated so far (may be null)
     * @param securityContext the current security context
     * @return the (possibly AND-ed) filter
     */
    private Query createConditionForTenantResource(Query initialFilter, SecurityContext securityContext) {
        //lenient rule: no security context or no active company => no tenant filter => behaves exactly like today
        if (securityContext == null || securityContext.getActiveCompanyId() == null)
            return initialFilter;
        Query tenantCondition = buildTenantCondition(securityContext.getActiveCompanyId());
        if (tenantCondition == null)
            return initialFilter;
        return (initialFilter == null) ? tenantCondition : initialFilter.and(tenantCondition);
    }

    /**
     * Builds the tenant condition for the current entity type, or null if the entity is not tenant-aware
     * (or has no resolver for the M:N case).
     */
    private Query buildTenantCondition(long activeCompanyId) {
        QueryBuilder queryBuilder = getSystemService().getQueryBuilderInstance();
        if (TenantResource.class.isAssignableFrom(this.getEntityType())) {
            //single-company entity: visible if it belongs to the active company OR is a global
            //(null companyId) / unassigned instance. equalTo(null) maps to an IS NULL predicate.
            return queryBuilder.field(TenantResource.COMPANY_ID_FIELD_NAME).equalTo(activeCompanyId)
                    .or(queryBuilder.field(TenantResource.COMPANY_ID_FIELD_NAME).equalTo(null));
        }
        if (MultiTenantResource.class.isAssignableFrom(this.getEntityType())) {
            return buildMultiTenantCondition(queryBuilder, activeCompanyId);
        }
        return null;
    }

    /**
     * M:N entity: scope by the set of instance ids that belong to the active company, resolved by the
     * entity module's TenantMembershipResolver. Returns a never-true condition when the company has no
     * members, or null (not tenant-filtered, logged) when no resolver is registered for the type.
     */
    private Query buildMultiTenantCondition(QueryBuilder queryBuilder, long activeCompanyId) {
        TenantMembershipResolver resolver = findTenantMembershipResolver();
        if (resolver == null) {
            getLog().warn("No TenantMembershipResolver found for MultiTenantResource {}: tenant filter NOT applied", this.getEntityType().getName());
            return null;
        }
        Set<Long> ids = resolver.getEntityIdsInCompany(this.getEntityType().getName(), activeCompanyId);
        if (ids == null || ids.isEmpty())
            return queryBuilder.field("id").equalTo(-1L);
        return queryBuilder.createQueryFilter("id IN (" + joinIds(ids) + ")");
    }

    /**
     * Joins server-controlled numeric ids into a CSV for an IN string filter. Building the IN this way
     * avoids the field(...).in(list) form, whose In operation is bounded to two operands.
     */
    private static String joinIds(Set<Long> ids) {
        StringBuilder csv = new StringBuilder();
        for (Long id : ids) {
            if (csv.length() > 0)
                csv.append(",");
            csv.append(id.longValue());
        }
        return csv.toString();
    }

    /**
     * Looks up the TenantMembershipResolver that supports the current entity type, if any.
     *
     * @return the matching resolver or null if none is registered
     */
    private TenantMembershipResolver findTenantMembershipResolver() {
        List<TenantMembershipResolver> resolvers = getComponentRegistry().findComponents(TenantMembershipResolver.class, null);
        if (resolvers != null) {
            for (TenantMembershipResolver resolver : resolvers) {
                if (resolver.supports(this.getEntityType().getName()))
                    return resolver;
            }
        }
        return null;
    }

    protected abstract BaseEntitySystemApi<T> getSystemService();

    /**
     * @return Component registry cored on the current technology or framework
     */
    protected abstract ComponentRegistry getComponentRegistry();

    /**
     * Return current entity type
     */
    @Override
    public Class<T> getEntityType() {
        return this.type;
    }

    /**
     * @param filter filter
     * @return
     */
    @Override
    @AllowGenericPermissions(actions = CrudActions.FIND)
    public long countAll(Query filter) {
        this.log.debug("Service countAll entities {}", this.type.getSimpleName());
        SecurityContext securityContext = runtime.getSecurityContext();
        filter = this.createConditionForOwnedOrSharedResource(filter, securityContext);
        filter = this.createConditionForTenantResource(filter, securityContext);
        return this.getSystemService().countAll(filter);
    }

    /**
     * Retrieve the SharedEntityIntegrationClient
     *
     * @return the SharedEntitySystemApi
     */
    protected SharedEntityIntegrationClient getSharedEntityIntegrationClient() {
        try {
            return getComponentRegistry().findComponent(SharedEntityIntegrationClient.class, null);
        } catch (NoComponentRegistryFoundException e) {
            getLog().warn("No shared entity integration client found!");
            return null;
        }
    }

    protected Query createFilterForOwnedOrSharedResource(Query ownedResourceFilter, long loggedEntityId) {
        if (SharedEntity.class.isAssignableFrom(this.getEntityType())) {
            SharedEntityIntegrationClient sharedEntityIntegrationClient = getSharedEntityIntegrationClient();
            if (sharedEntityIntegrationClient != null) {
                //forcing the condition that user must own the entity or is shared with him
                Collection<Long> entityIds = sharedEntityIntegrationClient.fetchSharingUsersIds(type.getName(), loggedEntityId);
                if (!entityIds.isEmpty()) {
                    // H7: parse only server-controlled numeric ids and combine via or(),
                    // never re-serialize the existing filter (injection risk).
                    // NOTE: field("id").in(list) is unusable here — In is capped at 2 operands
                    // and the JPA PredicateBuilder expects a FieldValueListOperand.
                    StringBuilder ids = new StringBuilder();
                    for (Long sharedId : entityIds) {
                        if (ids.length() > 0) {
                            ids.append(",");
                        }
                        ids.append(sharedId.longValue());
                    }
                    Query sharedByIdsFilter = getSystemService().getQueryBuilderInstance().createQueryFilter("id IN (" + ids + ")");
                    ownedResourceFilter = ownedResourceFilter.or(sharedByIdsFilter);
                }
            }
        }
        return ownedResourceFilter;
    }
}
