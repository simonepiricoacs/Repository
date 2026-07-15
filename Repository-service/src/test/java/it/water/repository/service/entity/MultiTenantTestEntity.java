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

import it.water.core.api.entity.tenant.MultiTenantResource;

import java.util.Date;

/**
 * Minimal {@link MultiTenantResource} fixture (M:N tenancy, no companyId column) used to exercise
 * {@code BaseEntityServiceImpl}'s {@code buildMultiTenantCondition} branch via
 * {@link TestTenantMembershipResolver}. Deliberately NOT a {@code ProtectedEntity}.
 */
public class MultiTenantTestEntity implements MultiTenantResource {
    private long id;
    private String entityField;
    private Date entityCreateDate;
    private Date entityModifyDate;
    private int entityVersion;

    public MultiTenantTestEntity() {
    }

    public MultiTenantTestEntity(MultiTenantTestEntity source) {
        this.id = source.id;
        this.entityField = source.entityField;
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
