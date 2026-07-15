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
package it.water.repository.service.entity;

import it.water.core.api.entity.tenant.TenantResource;

import java.util.Date;

/**
 * Minimal {@link TenantResource} fixture used only to exercise {@code BaseEntityServiceImpl}'s
 * tenant-scoping seam (auto-assign on save, restore-on-update, and the findAll/countAll/find
 * tenant filter) from Repository-service's OWN test scope. Deliberately NOT a
 * {@code ProtectedEntity} (no {@code @AccessControl}): the permission layer treats it as
 * unprotected so these tests can focus purely on the tenant seam, mirroring {@code NotOwnedEntity}
 * in this same package.
 */
public class TenantTestEntity implements TenantResource {
    private long id;
    private String entityField;
    private Long companyId;
    private Date entityCreateDate;
    private Date entityModifyDate;
    private int entityVersion;

    public TenantTestEntity() {
    }

    /**
     * Defensive-copy constructor used by the in-memory repository so that fetched/stored instances
     * never alias the same object.
     */
    public TenantTestEntity(TenantTestEntity source) {
        this.id = source.id;
        this.entityField = source.entityField;
        this.companyId = source.companyId;
        this.entityCreateDate = source.entityCreateDate;
        this.entityModifyDate = source.entityModifyDate;
        this.entityVersion = source.entityVersion;
    }

    @Override
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getEntityField() {
        return entityField;
    }

    public void setEntityField(String entityField) {
        this.entityField = entityField;
    }

    @Override
    public Long getCompanyId() {
        return companyId;
    }

    @Override
    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    @Override
    public Date getEntityCreateDate() {
        return entityCreateDate;
    }

    public void setEntityCreateDate(Date entityCreateDate) {
        this.entityCreateDate = entityCreateDate;
    }

    @Override
    public Date getEntityModifyDate() {
        return entityModifyDate;
    }

    public void setEntityModifyDate(Date entityModifyDate) {
        this.entityModifyDate = entityModifyDate;
    }

    @Override
    public Integer getEntityVersion() {
        return entityVersion;
    }

    @Override
    public void setEntityVersion(Integer integer) {
        //just for test purpose
    }

    public void setEntityVersion(int entityVersion) {
        this.entityVersion = entityVersion;
    }
}
