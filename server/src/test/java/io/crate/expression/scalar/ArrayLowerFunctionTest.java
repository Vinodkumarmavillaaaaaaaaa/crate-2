/*
 * Licensed to Crate.io GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.expression.scalar;

import org.junit.Test;

public class ArrayLowerFunctionTest extends ScalarTestCase {

    @Test
    public void testSecondArgumentIsNull() {
        assertEvaluate("array_lower([1, 2], null)", null);
    }

    @Test
    public void test_not_null_but_invalid_dimension() {
        assertEvaluate("array_lower([1], 0)", null);
        assertEvaluate("array_lower([1], -1)", null);
    }

    @Test
    public void test_at_least_one_empty_or_null_on_dimension_returns_null() {
        assertEvaluate("array_lower([[1, 4], [3], []], 2)", null);
        assertEvaluate("array_lower([[1, 4], null, [1, 2]], 2)", null);
    }

    @Test
    public void test_3d_array_with_mixed_sizes_within_dimension() {
        // arr[3][2-3][2-3]
        // This test shows that we have to traverse all arrays on each dimension.
        // Second 2D consists of 2 long 1D arrays and third 2D array consists of 3 empty arrays, so lower bound
        // Also testing null on each dimension.
        String array =
            "[null," +           // first 2D array is null
                "[null, [null, 1, 1]], " + // second 2D array's first row is null
                "[[], [], []]]";

        assertEvaluate("array_lower(" + array + ", 1)", 1);
        assertEvaluate("array_lower(" + array + ", 2)", null); // because of first null 2D array.
        assertEvaluate("array_lower(" + array + ", 3)", null);
        assertEvaluate("array_lower(" + array + ", 4)", null);
    }

    @Test
    public void testSingleDimensionArrayValidDimension() {
        assertEvaluate("array_lower([4, 5], 1)", 1);
    }

    @Test
    public void testSingleDimensionArrayInvalidDimension() {
        assertEvaluate("array_lower([4, 5], 3)", null);
    }

    @Test
    public void testMultiDimensionArrayValidDimension() {
        assertEvaluate("array_lower([[1, 2, 3], [3, 4]], 2)", 1);
    }

    @Test
    public void testMultiDimensionArrayInvalidDimension() {
        assertEvaluate("array_lower([[1, 2, 3], [3, 4]], 3)", null);
    }

    @Test
    public void testEmptyArray() {
        assertEvaluate("array_lower(cast([] as array(integer)), 1)", null);
    }

    @Test
    public void testZeroArguments() {
        expectedException.expect(UnsupportedOperationException.class);
        expectedException.expectMessage("Unknown function: array_lower()." +
                                        " Possible candidates: array_lower(array(E), integer):integer");
        assertEvaluate("array_lower()", null);
    }

    @Test
    public void testOneArgument() {
        expectedException.expect(UnsupportedOperationException.class);
        expectedException.expectMessage("Unknown function: array_lower(_array(1))," +
                                        " no overload found for matching argument types: (integer_array).");
        assertEvaluate("array_lower([1])", null);
    }

    @Test
    public void testThreeArguments() {
        expectedException.expect(UnsupportedOperationException.class);
        expectedException.expectMessage("Unknown function: array_lower(_array(1), 2, _array(3))," +
                                        " no overload found for matching argument types: (integer_array, integer, integer_array).");
        assertEvaluate("array_lower([1], 2, [3])", null);
    }

    @Test
    public void testSecondArgumentNotANumber() {
        expectedException.expect(UnsupportedOperationException.class);
        expectedException.expectMessage("Unknown function: array_lower(_array(1), _array(2))," +
                                        " no overload found for matching argument types: (integer_array, integer_array).");
        assertEvaluate("array_lower([1], [2])", null);
    }

    @Test
    public void testFirstArgumentIsNull() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(
            "The inner type of the array argument `array_lower` function cannot be undefined");
        assertEvaluate("array_lower(null, 1)", null);
    }
}
