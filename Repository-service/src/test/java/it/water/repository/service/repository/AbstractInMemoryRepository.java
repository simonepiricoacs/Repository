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

import it.water.core.api.model.BaseEntity;
import it.water.core.api.model.PaginableResult;
import it.water.core.api.repository.BaseRepository;
import it.water.core.api.repository.query.Query;
import it.water.core.api.repository.query.QueryBuilder;
import it.water.core.api.repository.query.QueryOrder;
import it.water.repository.entity.model.PaginatedResult;
import it.water.repository.query.DefaultQueryBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal, genuinely-filtering in-memory {@link BaseRepository} used ONLY by the multitenancy
 * coverage test fixtures in this package. Unlike the plain stub repositories elsewhere in this
 * test module (which always return a fixed entity regardless of the incoming filter), this base
 * class really applies the {@link Query} tree via {@link InMemoryQueryEvaluator}, so tests that
 * seed multiple rows across companies can assert on the ACTUAL filtered subset returned by
 * {@code BaseEntityServiceImpl}'s tenant-scoping seam.
 * <p>
 * Ids must be explicitly assigned by the caller before {@link #persist(BaseEntity)} (mirrors the
 * convention already used by the other test fixtures in this module).
 *
 * @param <T> the concrete test entity type
 */
public abstract class AbstractInMemoryRepository<T extends BaseEntity> implements BaseRepository<T> {

    protected final Map<Long, T> store = new ConcurrentHashMap<>();

    /**
     * Returns a defensive copy of {@code source}, so that mutating the object returned by a
     * find/findAll call (or the object passed to persist/update) never silently leaks into the
     * store - mirroring how a real JPA repository returns detached, independent instances.
     */
    protected abstract T copyOf(T source);

    @Override
    public T persist(T entity, Runnable postPersistAction) {
        T result = persist(entity);
        if (postPersistAction != null)
            postPersistAction.run();
        return result;
    }

    @Override
    public T update(T entity, Runnable postUpdateAction) {
        T result = update(entity);
        if (postUpdateAction != null)
            postUpdateAction.run();
        return result;
    }

    @Override
    public void remove(long id, Runnable postRemoveAction) {
        remove(id);
        if (postRemoveAction != null)
            postRemoveAction.run();
    }

    @Override
    public T persist(T entity) {
        store.put(entity.getId(), copyOf(entity));
        return entity;
    }

    @Override
    public T update(T entity) {
        store.put(entity.getId(), copyOf(entity));
        return entity;
    }

    @Override
    public void remove(long id) {
        store.remove(id);
    }

    @Override
    public void remove(T entity) {
        store.remove(entity.getId());
    }

    @Override
    public void removeAllByIds(Iterable<Long> ids) {
        ids.forEach(store::remove);
    }

    @Override
    public void removeAll(Iterable<T> entities) {
        entities.forEach(e -> store.remove(e.getId()));
    }

    @Override
    public void removeAll() {
        store.clear();
    }

    @Override
    public T find(long id) {
        T stored = store.get(id);
        return stored == null ? null : copyOf(stored);
    }

    @Override
    public T find(Query filter) {
        for (T candidate : store.values()) {
            if (InMemoryQueryEvaluator.matches(filter, candidate))
                return copyOf(candidate);
        }
        return null;
    }

    @Override
    public T find(String filterStr) {
        return find(getQueryBuilderInstance().createQueryFilter(filterStr));
    }

    @Override
    public long countAll(Query filter) {
        long count = 0;
        for (T candidate : store.values()) {
            if (InMemoryQueryEvaluator.matches(filter, candidate))
                count++;
        }
        return count;
    }

    @Override
    public PaginableResult<T> findAll(int delta, int page, Query filter, QueryOrder queryOrder) {
        List<T> matched = new ArrayList<>();
        for (T candidate : store.values()) {
            if (InMemoryQueryEvaluator.matches(filter, candidate))
                matched.add(copyOf(candidate));
        }
        int effectiveDelta = delta > 0 ? delta : Math.max(matched.size(), 1);
        int numPages = (int) Math.ceil((double) matched.size() / effectiveDelta);
        return new PaginatedResult<>(Math.max(numPages, 1), page, page, effectiveDelta, matched);
    }

    @Override
    public QueryBuilder getQueryBuilderInstance() {
        return new DefaultQueryBuilder();
    }
}
