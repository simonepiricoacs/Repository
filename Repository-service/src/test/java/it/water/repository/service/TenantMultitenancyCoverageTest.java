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
import it.water.core.api.model.PaginableResult;
import it.water.core.api.model.User;
import it.water.core.api.registry.ComponentRegistry;
import it.water.core.api.repository.query.Query;
import it.water.core.api.repository.query.QueryBuilder;
import it.water.core.api.user.UserManager;
import it.water.core.interceptors.annotations.Inject;
import it.water.core.api.service.Service;
import it.water.core.registry.model.ComponentConfigurationFactory;
import it.water.core.testing.utils.bundle.TestRuntimeInitializer;
import it.water.core.testing.utils.junit.WaterTestExtension;
import it.water.core.testing.utils.security.TestSecurityContext;
import it.water.repository.service.api.MultiTenantTestEntityApi;
import it.water.repository.service.api.MultiTenantTestEntityRepository;
import it.water.repository.service.api.MultiTenantTestEntitySystemApi;
import it.water.repository.service.api.TenantTestEntityApi;
import it.water.repository.service.api.TenantTestEntityRepository;
import it.water.repository.service.api.TenantTestEntitySystemApi;
import it.water.repository.service.api.UnresolvedMultiTenantTestEntityApi;
import it.water.repository.service.api.UnresolvedMultiTenantTestEntityRepository;
import it.water.repository.service.api.UnresolvedMultiTenantTestEntitySystemApi;
import it.water.repository.service.entity.MultiTenantTestEntity;
import it.water.repository.service.entity.TenantTestEntity;
import it.water.repository.service.entity.UnresolvedMultiTenantTestEntity;
import it.water.repository.service.repository.MultiTenantTestEntityRepositoryImpl;
import it.water.repository.service.repository.TenantTestEntityRepositoryImpl;
import it.water.repository.service.repository.UnresolvedMultiTenantTestEntityRepositoryImpl;
import lombok.Setter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Coverage tests for the multitenancy enforcement seam added to {@link BaseEntityServiceImpl}
 * (save auto-assign, update restore, and the find/findAll/countAll tenant filter for both
 * {@code TenantResource} and {@code MultiTenantResource}), exercised from Repository-service's OWN
 * test scope so these branches are no longer covered ONLY transitively from other modules.
 * <p>
 * Unlike the JPA/H2-backed tenant filter tests in other modules, the repositories used here
 * ({@link TenantTestEntityRepositoryImpl}, {@link MultiTenantTestEntityRepositoryImpl},
 * {@link UnresolvedMultiTenantTestEntityRepositoryImpl}) are brand-new, private, in-memory stores
 * registered by THIS test class alone: there is no shared-database pollution risk, so assertions
 * use exact sizes/contents rather than the "id IN (...)" scoping technique needed when a table is
 * shared across test classes.
 */
@ExtendWith(WaterTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TenantMultitenancyCoverageTest implements Service {

    private static final Long COMPANY_A = 9701L;
    private static final Long COMPANY_B = 9702L;

    private static final long TENANT_ROW_A_ID = 101L;
    private static final long TENANT_ROW_B_ID = 102L;
    private static final long TENANT_ROW_GLOBAL_ID = 103L;
    private static final long TENANT_NEW_ROW_ID = 110L;

    private static final long MT_ROW_1_ID = 201L;
    private static final long MT_ROW_2_ID = 202L;
    private static final long MT_ROW_3_ID = 203L;

    private static final long UR_ROW_1_ID = 301L;
    private static final long UR_ROW_2_ID = 302L;

    @Inject
    @Setter
    private UserManager userManager;

    @Inject
    @Setter
    private Runtime runtime;

    private User tenantAdmin;

    private TenantTestEntityApi tenantApi;
    private TenantTestEntitySystemApi tenantSystemApi;
    private MultiTenantTestEntityApi multiTenantApi;
    private MultiTenantTestEntitySystemApi multiTenantSystemApi;
    private UnresolvedMultiTenantTestEntityApi unresolvedApi;
    private UnresolvedMultiTenantTestEntitySystemApi unresolvedSystemApi;

    @BeforeAll
    void initializeFixtures() {
        tenantAdmin = userManager.addUser("tenantCoverageAdmin", "Tenant", "Admin",
                "tenant.coverage.admin@mail.com", "Password1_", "salt", true);

        ComponentRegistry registry = TestRuntimeInitializer.getInstance().getComponentRegistry();
        ComponentConfigurationFactory<TenantTestEntityRepositoryImpl> tenantFactory = new ComponentConfigurationFactory<>();
        ComponentConfigurationFactory<MultiTenantTestEntityRepositoryImpl> multiTenantFactory = new ComponentConfigurationFactory<>();
        ComponentConfigurationFactory<UnresolvedMultiTenantTestEntityRepositoryImpl> unresolvedFactory = new ComponentConfigurationFactory<>();

        registry.registerComponent(TenantTestEntityRepository.class, new TenantTestEntityRepositoryImpl(), tenantFactory.build());
        registry.registerComponent(MultiTenantTestEntityRepository.class, new MultiTenantTestEntityRepositoryImpl(), multiTenantFactory.build());
        registry.registerComponent(UnresolvedMultiTenantTestEntityRepository.class, new UnresolvedMultiTenantTestEntityRepositoryImpl(), unresolvedFactory.build());

        tenantApi = registry.findComponent(TenantTestEntityApi.class, null);
        tenantSystemApi = registry.findComponent(TenantTestEntitySystemApi.class, null);
        multiTenantApi = registry.findComponent(MultiTenantTestEntityApi.class, null);
        multiTenantSystemApi = registry.findComponent(MultiTenantTestEntitySystemApi.class, null);
        unresolvedApi = registry.findComponent(UnresolvedMultiTenantTestEntityApi.class, null);
        unresolvedSystemApi = registry.findComponent(UnresolvedMultiTenantTestEntitySystemApi.class, null);
    }

    @AfterAll
    void restoreCleanContext() {
        //restores a non-scoped admin context (activeCompanyId=null) so no later test class in the
        //shared JVM/registry inherits an active company from this one.
        TestRuntimeInitializer.getInstance().impersonate(tenantAdmin, runtime);
    }

    private void activateCompany(Long companyId) {
        runtime.fillSecurityContext(TestSecurityContext.createContext(tenantAdmin.getId(), tenantAdmin.getUsername(), true, companyId));
    }

    private void clearActiveCompany() {
        TestRuntimeInitializer.getInstance().impersonate(tenantAdmin, runtime);
    }

    // -----------------------------------------------------------------------
    // TenantResource: seed + findAll/countAll/find scoping
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    void seedTenantResourceFixtures_viaSystemApi_bypassesAutoAssign() {
        TenantTestEntity rowA = new TenantTestEntity();
        rowA.setId(TENANT_ROW_A_ID);
        rowA.setEntityField("rowA");
        rowA.setCompanyId(COMPANY_A);

        TenantTestEntity rowB = new TenantTestEntity();
        rowB.setId(TENANT_ROW_B_ID);
        rowB.setEntityField("rowB");
        rowB.setCompanyId(COMPANY_B);

        TenantTestEntity rowGlobal = new TenantTestEntity();
        rowGlobal.setId(TENANT_ROW_GLOBAL_ID);
        rowGlobal.setEntityField("rowGlobal");
        rowGlobal.setCompanyId(null);

        Assertions.assertNotNull(tenantSystemApi.save(rowA));
        Assertions.assertNotNull(tenantSystemApi.save(rowB));
        Assertions.assertNotNull(tenantSystemApi.save(rowGlobal));

        Assertions.assertEquals(COMPANY_B, tenantSystemApi.find(TENANT_ROW_B_ID).getCompanyId(),
                "SystemApi.save must bypass the Api-layer auto-assign so the seeded companyId is preserved as-is");
    }

    @Test
    @Order(2)
    void findAll_scopedToCompanyA_returnsCompanyAAndGlobal_excludesCompanyB() {
        activateCompany(COMPANY_A);

        PaginableResult<TenantTestEntity> result = tenantApi.findAll(null, 10, 1, null);
        Set<Long> ids = result.getResults().stream().map(TenantTestEntity::getId).collect(Collectors.toSet());

        Assertions.assertEquals(2, result.getResults().size());
        Assertions.assertTrue(ids.contains(TENANT_ROW_A_ID), "own-company row must be visible");
        Assertions.assertTrue(ids.contains(TENANT_ROW_GLOBAL_ID), "global (null companyId) row must be visible cross-tenant");
        Assertions.assertFalse(ids.contains(TENANT_ROW_B_ID), "other-company row must be excluded");
    }

    @Test
    @Order(3)
    void countAll_scopedToCompanyA_excludesCompanyB() {
        activateCompany(COMPANY_A);

        Assertions.assertEquals(2, tenantApi.countAll(null));
    }

    @Test
    @Order(4)
    void find_singleEntity_scopedToCompanyA_ownAndGlobalVisible_otherCompanyDenied() {
        activateCompany(COMPANY_A);
        QueryBuilder queryBuilder = tenantSystemApi.getQueryBuilderInstance();

        //own-company row: visible. Passing a non-null base filter also exercises the AND-combination
        //branch of createConditionForTenantResource (initialFilter.and(tenantCondition)).
        Query filterRowA = queryBuilder.field("entityField").equalTo("rowA");
        TenantTestEntity foundOwn = tenantApi.find(filterRowA);
        Assertions.assertNotNull(foundOwn);
        Assertions.assertEquals(TENANT_ROW_A_ID, foundOwn.getId());

        //global row: visible cross-tenant
        Query filterGlobal = queryBuilder.field("entityField").equalTo("rowGlobal");
        TenantTestEntity foundGlobal = tenantApi.find(filterGlobal);
        Assertions.assertNotNull(foundGlobal);
        Assertions.assertEquals(TENANT_ROW_GLOBAL_ID, foundGlobal.getId());

        //other-company row: must be denied. Tolerant of either a null return or a thrown
        //RuntimeException, since the exact "not found" signalling contract of the permission
        //interceptor chain is not the object under test here.
        TenantTestEntity foundOther;
        try {
            Query filterRowB = queryBuilder.field("entityField").equalTo("rowB");
            foundOther = tenantApi.find(filterRowB);
        } catch (RuntimeException expectedDenied) {
            foundOther = null;
        }
        Assertions.assertNull(foundOther, "row belonging to another company must not be visible when scoped to company A");
    }

    @Test
    @Order(5)
    void findAll_noActiveCompany_backwardCompatibleReturnsAllRows() {
        clearActiveCompany();

        PaginableResult<TenantTestEntity> result = tenantApi.findAll(null, 10, 1, null);

        Assertions.assertEquals(3, result.getResults().size(),
                "with no active company the tenant filter must not be applied (lenient/backward-compatible)");
    }

    @Test
    @Order(6)
    void save_underActiveCompanyA_autoAssignsCompanyId() {
        activateCompany(COMPANY_A);

        TenantTestEntity newEntity = new TenantTestEntity();
        newEntity.setId(TENANT_NEW_ROW_ID);
        newEntity.setEntityField("autoAssignRow");
        //explicitly NOT setting companyId: the Api layer must assign it from the active company

        TenantTestEntity saved = tenantApi.save(newEntity);

        Assertions.assertEquals(COMPANY_A, saved.getCompanyId());
        Assertions.assertEquals(COMPANY_A, tenantSystemApi.find(TENANT_NEW_ROW_ID).getCompanyId(),
                "auto-assigned companyId must also be the one actually persisted");
    }

    @Test
    @Order(7)
    void update_cannotChangeCompanyId_restoredFromPersisted() {
        activateCompany(COMPANY_A);

        TenantTestEntity maliciousUpdate = new TenantTestEntity();
        maliciousUpdate.setId(TENANT_ROW_A_ID);
        maliciousUpdate.setCompanyId(COMPANY_B);
        maliciousUpdate.setEntityField("attemptedHijack");

        TenantTestEntity updated = tenantApi.update(maliciousUpdate);

        Assertions.assertEquals(COMPANY_A, updated.getCompanyId(),
                "companyId must be restored from the persisted entity, never taken from the client-supplied entity");
        Assertions.assertEquals("attemptedHijack", updated.getEntityField(),
                "non-tenant fields must still be updated normally");
        Assertions.assertEquals(COMPANY_A, tenantSystemApi.find(TENANT_ROW_A_ID).getCompanyId());
    }

    // -----------------------------------------------------------------------
    // MultiTenantResource: resolver-backed M:N scoping
    // -----------------------------------------------------------------------

    @Test
    @Order(8)
    void seedMultiTenantResourceFixtures_andConfigureResolverMembership() {
        MultiTenantTestEntity row1 = new MultiTenantTestEntity();
        row1.setId(MT_ROW_1_ID);
        row1.setEntityField("mt1");

        MultiTenantTestEntity row2 = new MultiTenantTestEntity();
        row2.setId(MT_ROW_2_ID);
        row2.setEntityField("mt2");

        MultiTenantTestEntity row3 = new MultiTenantTestEntity();
        row3.setId(MT_ROW_3_ID);
        row3.setEntityField("mt3");

        Assertions.assertNotNull(multiTenantSystemApi.save(row1));
        Assertions.assertNotNull(multiTenantSystemApi.save(row2));
        Assertions.assertNotNull(multiTenantSystemApi.save(row3));

        //company A has 2 members (row1, row2); company B intentionally has none configured yet
        TestTenantMembershipResolver.setMembership(COMPANY_A, Set.of(MT_ROW_1_ID, MT_ROW_2_ID));
    }

    @Test
    @Order(9)
    void findAll_multiTenant_resolverReturnsIds_onlyThoseVisible() {
        activateCompany(COMPANY_A);

        PaginableResult<MultiTenantTestEntity> result = multiTenantApi.findAll(null, 10, 1, null);
        Set<Long> ids = result.getResults().stream().map(MultiTenantTestEntity::getId).collect(Collectors.toSet());

        Assertions.assertEquals(Set.of(MT_ROW_1_ID, MT_ROW_2_ID), ids);
    }

    @Test
    @Order(10)
    void findAll_multiTenant_resolverReturnsEmpty_noneVisible() {
        TestTenantMembershipResolver.setMembership(COMPANY_B, Set.of());
        activateCompany(COMPANY_B);

        PaginableResult<MultiTenantTestEntity> result = multiTenantApi.findAll(null, 10, 1, null);

        Assertions.assertEquals(0, result.getResults().size(),
                "an empty membership set must translate to the never-true id=-1 fallback (zero rows), not fail-open");
    }

    @Test
    @Order(11)
    void findAll_multiTenant_noActiveCompany_backwardCompatibleReturnsAllRows() {
        clearActiveCompany();

        PaginableResult<MultiTenantTestEntity> result = multiTenantApi.findAll(null, 10, 1, null);

        Assertions.assertEquals(3, result.getResults().size());
    }

    @Test
    @Order(12)
    void findAll_multiTenant_noResolverRegisteredForType_notFiltered() {
        UnresolvedMultiTenantTestEntity row1 = new UnresolvedMultiTenantTestEntity();
        row1.setId(UR_ROW_1_ID);
        row1.setEntityField("ur1");
        UnresolvedMultiTenantTestEntity row2 = new UnresolvedMultiTenantTestEntity();
        row2.setId(UR_ROW_2_ID);
        row2.setEntityField("ur2");
        Assertions.assertNotNull(unresolvedSystemApi.save(row1));
        Assertions.assertNotNull(unresolvedSystemApi.save(row2));

        //a company IS active, but TestTenantMembershipResolver.supports() returns false for this
        //entity type, so buildMultiTenantCondition must log a warning and leave the query unfiltered.
        activateCompany(COMPANY_A);

        PaginableResult<UnresolvedMultiTenantTestEntity> result = unresolvedApi.findAll(null, 10, 1, null);

        Assertions.assertEquals(2, result.getResults().size(),
                "with no registered resolver for this MultiTenantResource type, results must NOT be filtered");
    }
}
