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

import it.water.repository.service.api.UnresolvedMultiTenantTestEntityRepository;
import it.water.repository.service.entity.UnresolvedMultiTenantTestEntity;

/**
 * Genuinely-filtering in-memory repository for {@link UnresolvedMultiTenantTestEntity}.
 * Registered manually from the test's {@code @BeforeAll}.
 */
public class UnresolvedMultiTenantTestEntityRepositoryImpl extends AbstractInMemoryRepository<UnresolvedMultiTenantTestEntity> implements UnresolvedMultiTenantTestEntityRepository {

    @Override
    public Class<UnresolvedMultiTenantTestEntity> getEntityType() {
        return UnresolvedMultiTenantTestEntity.class;
    }

    @Override
    protected UnresolvedMultiTenantTestEntity copyOf(UnresolvedMultiTenantTestEntity source) {
        return new UnresolvedMultiTenantTestEntity(source);
    }
}
