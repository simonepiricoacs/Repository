
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

package it.water.repository.query.parser;

import it.water.core.api.repository.query.Query;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * Security-regression + functional tests for fix H31.
 *
 * <p>H31 fix summary:
 * <ul>
 *   <li>Absolute backstop {@code MAX_ITERATIONS=10000}: every {@code nextToken()} passes through
 *       {@code advance()}, which increments a counter and throws if the cap is exceeded.</li>
 *   <li>Parenthesis-balance tracking: a {@code ')'} without a matching {@code '('} is rejected
 *       immediately (negative {@code parenthesisDepth}); unbalanced open parens are rejected at EOF.</li>
 *   <li>{@code parse()} detects non-advancing iterations (same token-count, non-EOF) and throws.</li>
 *   <li>The symbol-accumulation loop in {@code parseExpression} tests {@code TT_EOF} so an operator
 *       with no right-hand operand ({@code "name="}) terminates immediately with an error.</li>
 *   <li>Constructor caps filter length at {@code MAX_FILTER_LENGTH=4096}.</li>
 *   <li>All malformed-input exceptions carry the opaque message {@code "Invalid query filter"}.</li>
 * </ul>
 *
 * <p>Timeout budget: 1 second per DoS-candidate test. Pre-fix, these inputs looped until JVM kill.
 */
class QueryParserTest {

    // -----------------------------------------------------------------------
    // Constants shared across tests (avoids magic-number repetitions)
    // -----------------------------------------------------------------------

    /** Timeout applied to every DoS-regression test case (milliseconds). */
    private static final long DOS_TIMEOUT_MS = 1_000L;

    /** Value just above MAX_FILTER_LENGTH (4096) to trigger the length cap. */
    private static final int OVER_MAX_FILTER_LENGTH = 4097;

    /** Operator that separates field name from missing right-hand value in H31 input 1. */
    private static final String FILTER_OPERATOR_MISSING_RHS = "name=";

    /** SQL-injection candidate that contains an unbalanced ')' — H31 input 2. */
    private static final String FILTER_SQL_INJECTION_UNBALANCED_PAREN =
            "name='Robert'); DROP TABLE users;--";

    /** Expected error message for all malformed-input rejections (opaque, non-revealing). */
    private static final String PARSE_ERROR_MSG = "Invalid query filter";

    // -----------------------------------------------------------------------
    // H31 DoS-regression: MUST terminate quickly AND throw IllegalArgumentException
    // -----------------------------------------------------------------------

    /**
     * H31-1: {@code "name="} — operator with no right-hand operand.
     *
     * <p>Pre-fix, the symbol-accumulation loop in {@code parseExpression} called
     * {@code nextToken()} past EOF repeatedly, producing a CPU-spin / infinite loop.
     * Post-fix, the loop tests {@code TT_EOF} and throws immediately.
     */
    @Test
    void testParse_operatorMissingRhs_terminatesWithinTimeoutAndThrows() {
        QueryParser parser = new QueryParser(FILTER_OPERATOR_MISSING_RHS);
        Assertions.assertTimeoutPreemptively(Duration.ofMillis(DOS_TIMEOUT_MS), () ->
                Assertions.assertThrows(IllegalArgumentException.class, parser::parse,
                        "Expected IllegalArgumentException for operator without right-hand operand"
                )
        );
    }

    /**
     * H31-2: {@code "name='Robert'); DROP TABLE users;--"} — unbalanced {@code ')'}.
     *
     * <p>Pre-fix, the unmatched {@code ')'} was silently consumed and parsing continued
     * down the SQL-injection payload indefinitely. Post-fix, {@code advance()} detects
     * negative {@code parenthesisDepth} immediately and throws.
     */
    @Test
    void testParse_sqlInjectionUnbalancedCloseParen_terminatesWithinTimeoutAndThrows() {
        QueryParser parser = new QueryParser(FILTER_SQL_INJECTION_UNBALANCED_PAREN);
        Assertions.assertTimeoutPreemptively(Duration.ofMillis(DOS_TIMEOUT_MS), () ->
                Assertions.assertThrows(IllegalArgumentException.class, parser::parse,
                        "Expected IllegalArgumentException for unbalanced close-parenthesis"
                )
        );
    }

    // -----------------------------------------------------------------------
    // Malformed filters — all must throw IllegalArgumentException
    // -----------------------------------------------------------------------

    /**
     * Unclosed parentheses: {@code "((a=1"} leaves depth=2 at EOF.
     * {@code parse()} checks {@code parenthesisDepth != 0} after the loop and throws.
     */
    @Test
    void testParse_unclosedOpenParentheses_throwsIllegalArgumentException() {
        QueryParser parser = new QueryParser("((a=1");
        Assertions.assertThrows(IllegalArgumentException.class, parser::parse,
                "Unclosed parentheses must be rejected at EOF"
        );
    }

    /**
     * Bare close-paren: {@code ")"} has no matching open-paren.
     * {@code advance()} detects {@code parenthesisDepth < 0} and throws immediately.
     */
    @Test
    void testParse_bareCloseParen_throwsIllegalArgumentException() {
        QueryParser parser = new QueryParser(")");
        Assertions.assertThrows(IllegalArgumentException.class, parser::parse,
                "A lone ')' must be rejected as unbalanced"
        );
    }

    /**
     * Concatenated symbolic operators: {@code "a==b"}.
     * The first {@code '='} is parsed as the operator token; after consuming the RHS,
     * the second {@code '='} is an unexpected symbol with no preceding field name,
     * triggering an unknown-operator error.
     */
    @Test
    void testParse_concatenatedSymbolOperators_throwsIllegalArgumentException() {
        QueryParser parser = new QueryParser("a==b");
        Assertions.assertThrows(IllegalArgumentException.class, parser::parse,
                "Double-equals operator must be rejected"
        );
    }

    /**
     * Filter length exactly at the cap boundary: length == MAX_FILTER_LENGTH (4096) is accepted.
     * This verifies the boundary condition of the length check (inclusive upper bound).
     */
    @Test
    void testConstructor_filterAtMaxLength_doesNotThrow() {
        String atLimit = "a".repeat(4096);
        // Constructor must not throw; parsing may throw (filter is not syntactically valid),
        // but the length check alone must not reject it.
        Assertions.assertDoesNotThrow(() -> new QueryParser(atLimit),
                "Filter at exactly MAX_FILTER_LENGTH must pass the length guard");
    }

    /**
     * Filter length exceeds MAX_FILTER_LENGTH (4096).
     * The constructor rejects it immediately, before tokenisation begins.
     */
    @Test
    void testConstructor_filterExceedsMaxLength_throwsIllegalArgumentException() {
        String oversized = "a".repeat(OVER_MAX_FILTER_LENGTH);
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () ->
                new QueryParser(oversized),
                "Filter longer than MAX_FILTER_LENGTH must be rejected by the constructor"
        );
        Assertions.assertEquals(PARSE_ERROR_MSG, ex.getMessage(),
                "Error message must be the opaque sentinel, not a detail-leaking string");
    }

    /**
     * A filter containing only whitespace is syntactically empty.
     * Parsing returns null (no filter) without throwing.
     */
    @Test
    void testParse_whitespaceOnlyFilter_returnsNull() throws Exception {
        Query result = new QueryParser("   ").parse();
        Assertions.assertNull(result, "Whitespace-only filter must produce a null Query");
    }

    // -----------------------------------------------------------------------
    // Regression tests — valid filters must parse correctly
    // -----------------------------------------------------------------------

    /**
     * Simple equality: {@code "name=John"}.
     * Produces a Query whose definition contains the field name, operator, and value.
     */
    @Test
    void testParse_simpleEquality_returnsQueryWithExpectedDefinition() throws Exception {
        Query result = new QueryParser("name=John").parse();

        Assertions.assertNotNull(result, "Valid equality filter must produce a non-null Query");
        String def = result.getDefinition();
        Assertions.assertTrue(def.contains("name"),
                "Query definition must contain the field name: " + def);
        Assertions.assertTrue(def.contains("="),
                "Query definition must contain the equality operator: " + def);
        Assertions.assertTrue(def.contains("John"),
                "Query definition must contain the field value: " + def);
    }

    /**
     * LIKE condition: {@code "name LIKE 'John%'"}.
     * The LIKE operator is a word-token operation; verifies the full token path.
     */
    @Test
    void testParse_likeCondition_returnsQueryContainingLikeAndValue() throws Exception {
        Query result = new QueryParser("name LIKE 'John%'").parse();

        Assertions.assertNotNull(result, "LIKE filter must produce a non-null Query");
        String def = result.getDefinition();
        Assertions.assertTrue(def.toUpperCase().contains("LIKE"),
                "Query definition must contain the LIKE operator: " + def);
        Assertions.assertTrue(def.contains("name"),
                "Query definition must contain the field name: " + def);
        Assertions.assertTrue(def.contains("John%"),
                "Query definition must contain the pattern value: " + def);
    }

    /**
     * IN condition with a numeric list: {@code "age IN (1,2,3)"}.
     * Verifies that the parenthesised value list is parsed and the depth counter is balanced.
     */
    @Test
    void testParse_inConditionWithNumericList_returnsQueryContainingIn() throws Exception {
        Query result = new QueryParser("age IN (1,2,3)").parse();

        Assertions.assertNotNull(result, "IN filter must produce a non-null Query");
        String def = result.getDefinition().toUpperCase();
        Assertions.assertTrue(def.contains("IN"),
                "Query definition must contain the IN operator: " + def);
        Assertions.assertTrue(def.contains("AGE"),
                "Query definition must contain the field name: " + def);
    }

    /**
     * AND expression with balanced parentheses: {@code "(name=John) AND (age>25)"}.
     * Verifies that nested parentheses increment/decrement depth correctly (final depth=0).
     */
    @Test
    void testParse_andWithBalancedParentheses_returnsQueryContainingAnd() throws Exception {
        Query result = new QueryParser("(name=John) AND (age>25)").parse();

        Assertions.assertNotNull(result, "AND filter with balanced parentheses must produce a non-null Query");
        String def = result.getDefinition().toUpperCase();
        Assertions.assertTrue(def.contains("AND"),
                "Query definition must contain the AND operator: " + def);
        Assertions.assertTrue(def.contains("NAME"),
                "Query definition must contain 'name': " + def);
        Assertions.assertTrue(def.contains("AGE"),
                "Query definition must contain 'age': " + def);
    }

    /**
     * OR expression without parentheses: {@code "name=John OR status=ACTIVE"}.
     * Verifies the word-token OR path (needsExpr=true) in the parser.
     */
    @Test
    void testParse_orExpressionWithoutParentheses_returnsQueryContainingOr() throws Exception {
        Query result = new QueryParser("name=John OR status=ACTIVE").parse();

        Assertions.assertNotNull(result, "OR filter must produce a non-null Query");
        String def = result.getDefinition().toUpperCase();
        Assertions.assertTrue(def.contains("OR"),
                "Query definition must contain the OR operator: " + def);
        Assertions.assertTrue(def.contains("NAME"),
                "Query definition must contain 'name': " + def);
        Assertions.assertTrue(def.contains("STATUS"),
                "Query definition must contain 'status': " + def);
    }

    /**
     * Greater-than comparison: {@code "age>25"}.
     * Exercises the symbolic operator path with a single-character operator.
     */
    @Test
    void testParse_greaterThanComparison_returnsQueryContainingOperator() throws Exception {
        Query result = new QueryParser("age>25").parse();

        Assertions.assertNotNull(result, "Greater-than filter must produce a non-null Query");
        String def = result.getDefinition();
        Assertions.assertTrue(def.contains(">"),
                "Query definition must contain '>': " + def);
        Assertions.assertTrue(def.contains("age"),
                "Query definition must contain the field name: " + def);
    }

    /**
     * Quoted string value: {@code "name='John Doe'"}.
     * Verifies that the quote-char paths in the tokenizer produce the full value including spaces.
     */
    @Test
    void testParse_quotedStringValue_returnsQueryContainingQuotedValue() throws Exception {
        Query result = new QueryParser("name='John Doe'").parse();

        Assertions.assertNotNull(result, "Filter with quoted string value must produce a non-null Query");
        String def = result.getDefinition();
        Assertions.assertTrue(def.contains("John Doe"),
                "Query definition must contain the quoted value: " + def);
    }

    /**
     * NOT operator: {@code "NOT name=John"}.
     * Exercises the {@code QueryFilterOperator} / {@code checkParseOperator} code path.
     */
    @Test
    void testParse_notOperator_returnsQueryContainingNot() throws Exception {
        Query result = new QueryParser("NOT name=John").parse();

        Assertions.assertNotNull(result, "NOT filter must produce a non-null Query");
        String def = result.getDefinition().toUpperCase();
        Assertions.assertTrue(def.contains("NOT"),
                "Query definition must contain the NOT operator: " + def);
    }

    /**
     * NOT-equal operator ({@code "<>"}): {@code "status<>INACTIVE"}.
     * Exercises the multi-character symbolic operator accumulation loop.
     */
    @Test
    void testParse_notEqualOperator_returnsQueryContainingNotEqualOperator() throws Exception {
        Query result = new QueryParser("status<>INACTIVE").parse();

        Assertions.assertNotNull(result, "Not-equal filter must produce a non-null Query");
        String def = result.getDefinition();
        Assertions.assertTrue(def.contains("<>"),
                "Query definition must contain '<>': " + def);
        Assertions.assertTrue(def.contains("status"),
                "Query definition must contain the field name: " + def);
    }

    /**
     * Greater-or-equal operator ({@code ">="}): {@code "age>=18"}.
     * Ensures two-character symbolic operators are correctly accumulated.
     */
    @Test
    void testParse_greaterOrEqualOperator_returnsQueryContainingOperator() throws Exception {
        Query result = new QueryParser("age>=18").parse();

        Assertions.assertNotNull(result, "Greater-or-equal filter must produce a non-null Query");
        String def = result.getDefinition();
        Assertions.assertTrue(def.contains(">="),
                "Query definition must contain '>=': " + def);
    }

    /**
     * Complex AND/OR expression: {@code "(a=1 AND b=2) OR (c=3 AND d=4)"}.
     * Verifies that multi-level parenthesis tracking reaches depth=0 at EOF.
     */
    @Test
    void testParse_complexAndOrWithNestedParentheses_returnsNonNullQuery() throws Exception {
        Query result = new QueryParser("(a=1 AND b=2) OR (c=3 AND d=4)").parse();

        Assertions.assertNotNull(result,
                "Complex AND/OR expression with balanced parens must produce a non-null Query");
        String def = result.getDefinition().toUpperCase();
        Assertions.assertTrue(def.contains("AND"), "Definition must contain AND: " + def);
        Assertions.assertTrue(def.contains("OR"), "Definition must contain OR: " + def);
    }

    /**
     * IN condition with string values: {@code "name IN ('Alice','Bob')"}.
     * Verifies quoted-string handling inside a value-list parenthesis node.
     */
    @Test
    void testParse_inConditionWithStringValues_returnsQueryContainingIn() throws Exception {
        Query result = new QueryParser("name IN ('Alice','Bob')").parse();

        Assertions.assertNotNull(result, "IN filter with string values must produce a non-null Query");
        String def = result.getDefinition().toUpperCase();
        Assertions.assertTrue(def.contains("IN"),
                "Query definition must contain the IN operator: " + def);
    }

    // -----------------------------------------------------------------------
    // DefaultQueryBuilder integration — wraps QueryParser and swallows exceptions
    // -----------------------------------------------------------------------

    /**
     * {@link it.water.repository.query.DefaultQueryBuilder#createQueryFilter} with a valid filter
     * must return a non-null Query (integration smoke-test, no mock needed).
     */
    @Test
    void testDefaultQueryBuilder_validFilter_returnsNonNullQuery() {
        it.water.repository.query.DefaultQueryBuilder builder =
                new it.water.repository.query.DefaultQueryBuilder();
        Query result = builder.createQueryFilter("name=John");
        Assertions.assertNotNull(result,
                "DefaultQueryBuilder must return a non-null Query for a valid filter");
    }

    /**
     * {@link it.water.repository.query.DefaultQueryBuilder#createQueryFilter} with a malformed
     * filter must return {@code null} (the builder swallows {@link IllegalArgumentException}
     * and logs it, returning null to callers).
     */
    @Test
    void testDefaultQueryBuilder_malformedFilter_returnsNull() {
        it.water.repository.query.DefaultQueryBuilder builder =
                new it.water.repository.query.DefaultQueryBuilder();
        Query result = builder.createQueryFilter("name=");
        Assertions.assertNull(result,
                "DefaultQueryBuilder must return null for a malformed filter (exception is swallowed)");
    }

    /**
     * {@link it.water.repository.query.DefaultQueryBuilder#createQueryFilter} with an oversized
     * filter (> 4096 chars) must return {@code null} without hanging.
     */
    @Test
    void testDefaultQueryBuilder_oversizedFilter_returnsNullWithinTimeout() {
        it.water.repository.query.DefaultQueryBuilder builder =
                new it.water.repository.query.DefaultQueryBuilder();
        String oversized = "a".repeat(OVER_MAX_FILTER_LENGTH);
        Assertions.assertTimeoutPreemptively(Duration.ofMillis(DOS_TIMEOUT_MS), () -> {
            Query result = builder.createQueryFilter(oversized);
            Assertions.assertNull(result,
                    "DefaultQueryBuilder must return null for an oversized filter");
        });
    }

    /**
     * {@link it.water.repository.query.DefaultQueryBuilder#field} must return a non-null
     * {@code FieldNameOperand} whose definition matches the supplied name.
     */
    @Test
    void testDefaultQueryBuilder_field_returnsFieldNameOperandWithCorrectDefinition() {
        it.water.repository.query.DefaultQueryBuilder builder =
                new it.water.repository.query.DefaultQueryBuilder();
        it.water.core.api.repository.query.operands.FieldNameOperand operand =
                builder.field("email");
        Assertions.assertNotNull(operand, "field() must return a non-null FieldNameOperand");
        Assertions.assertEquals("email", operand.getDefinition(),
                "FieldNameOperand definition must match the supplied field name");
    }
}
