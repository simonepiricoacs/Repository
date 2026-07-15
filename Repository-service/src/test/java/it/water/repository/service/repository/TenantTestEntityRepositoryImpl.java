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
package it.water.repository.service.repository;

import it.water.repository.service.api.TenantTestEntityRepository;
import it.water.repository.service.entity.TenantTestEntity;

/**
 * Genuinely-filtering in-memory repository for {@link TenantTestEntity}, used by the
 * multitenancy coverage tests. Registered manually (not a {@code @FrameworkComponent}) from the
 * test's {@code @BeforeAll}, exactly like the other hand-registered repository fixtures in this
 * module.
 */
public class TenantTestEntityRepositoryImpl extends AbstractInMemoryRepository<TenantTestEntity> implements TenantTestEntityRepository {

    @Override
    public Class<TenantTestEntity> getEntityType() {
        return TenantTestEntity.class;
    }

    @Override
    protected TenantTestEntity copyOf(TenantTestEntity source) {
        return new TenantTestEntity(source);
    }
}
