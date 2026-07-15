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
import it.water.core.api.repository.query.Query;
import it.water.core.api.repository.query.operands.FieldNameOperand;
import it.water.core.api.repository.query.operands.FieldValueListOperand;
import it.water.core.api.repository.query.operands.FieldValueOperand;
import it.water.core.api.repository.query.operands.ParenthesisNode;
import it.water.core.api.repository.query.operations.AbstractOperation;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Minimal, generic in-memory evaluator that walks the {@link Query} operand tree produced by
 * {@code DefaultQueryBuilder} / {@code BaseEntityServiceImpl} and evaluates it against a plain
 * Java bean via reflection. Used ONLY by the in-memory test repositories in this package to
 * provide genuine (non-stubbed) filtering behaviour for the multitenancy coverage tests, mirroring
 * what a real JPA repository would do against the database.
 * <p>
 * Supports exactly the operator shapes produced by {@code BaseEntityServiceImpl}'s tenant
 * enforcement seam: {@code =} (EqualTo, including a {@code null} right-hand value for IS-NULL
 * semantics), {@code OR}, {@code AND}, and the string-parsed {@code id IN (...)} filter used for
 * {@code MultiTenantResource} scoping. Any other/unsupported operator is treated leniently (not
 * filtered out), since this evaluator only needs to understand what the seam under test actually
 * generates.
 */
final class InMemoryQueryEvaluator {

    private InMemoryQueryEvaluator() {
    }

    static boolean matches(Query filter, BaseEntity entity) {
        if (filter == null)
            return true;
        if (!(filter instanceof AbstractOperation operation))
            return true;
        String operator = operation.operator();
        if ("AND".equalsIgnoreCase(operator))
            return matches(operation.getOperand(0), entity) && matches(operation.getOperand(1), entity);
        if ("OR".equalsIgnoreCase(operator))
            return matches(operation.getOperand(0), entity) || matches(operation.getOperand(1), entity);
        if ("=".equals(operator))
            return evaluateEquals(operation, entity);
        if ("IN".equalsIgnoreCase(operator))
            return evaluateIn(operation, entity);
        return true;
    }

    private static boolean evaluateEquals(AbstractOperation operation, BaseEntity entity) {
        String fieldName = ((FieldNameOperand) operation.getOperand(0)).getValue();
        Object expected = ((FieldValueOperand) operation.getOperand(1)).getValue();
        Object actual = fieldValue(entity, fieldName);
        if (expected == null)
            return actual == null;
        if (expected instanceof Number && actual instanceof Number)
            return ((Number) expected).longValue() == ((Number) actual).longValue();
        return expected.equals(actual);
    }

    private static boolean evaluateIn(AbstractOperation operation, BaseEntity entity) {
        String fieldName = ((FieldNameOperand) operation.getOperand(0)).getValue();
        Object actual = fieldValue(entity, fieldName);
        Query second = operation.getOperand(1);
        List<Object> values;
        if (second instanceof ParenthesisNode parenthesisNode) {
            Query inner = parenthesisNode.getOperand(0);
            if (inner instanceof FieldValueListOperand listOperand)
                values = listOperand.getValue();
            else if (inner instanceof FieldValueOperand singleOperand)
                values = List.of(singleOperand.getValue());
            else
                values = List.of();
        } else {
            values = List.of();
        }
        for (Object value : values) {
            if (valueEquals(actual, value))
                return true;
        }
        return false;
    }

    private static boolean valueEquals(Object actual, Object candidate) {
        if (actual == null || candidate == null)
            return actual == candidate;
        if (actual instanceof Number && candidate instanceof Number)
            return ((Number) actual).longValue() == ((Number) candidate).longValue();
        if (actual instanceof Number actualNumber && candidate instanceof String candidateStr) {
            try {
                return actualNumber.longValue() == Long.parseLong(candidateStr.trim());
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return actual.equals(candidate);
    }

    private static Object fieldValue(BaseEntity entity, String fieldName) {
        try {
            String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            Method getter = entity.getClass().getMethod(getterName);
            return getter.invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to read field '" + fieldName + "' from " + entity.getClass(), e);
        }
    }
}
