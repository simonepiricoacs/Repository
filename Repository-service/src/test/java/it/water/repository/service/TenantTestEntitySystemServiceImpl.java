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

import it.water.core.api.model.Resource;
import it.water.core.api.repository.BaseRepository;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.core.interceptors.annotations.Inject;
import it.water.repository.service.api.TenantTestEntityRepository;
import it.water.repository.service.api.TenantTestEntitySystemApi;
import it.water.repository.service.entity.TenantTestEntity;

@FrameworkComponent
public class TenantTestEntitySystemServiceImpl extends BaseEntitySystemServiceImpl<TenantTestEntity> implements TenantTestEntitySystemApi {

    @Inject
    private TenantTestEntityRepository tenantTestEntityRepository;

    public TenantTestEntitySystemServiceImpl() {
        super(TenantTestEntity.class);
    }

    @Override
    protected void validate(Resource resource) {
        //do nothing: no validation constraints needed for this fixture
    }

    @Override
    protected BaseRepository<TenantTestEntity> getRepository() {
        return tenantTestEntityRepository;
    }

    public void setTenantTestEntityRepository(TenantTestEntityRepository tenantTestEntityRepository) {
        this.tenantTestEntityRepository = tenantTestEntityRepository;
    }
}
