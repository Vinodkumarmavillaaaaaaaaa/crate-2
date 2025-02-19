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

package io.crate.types;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.Map;

import org.elasticsearch.test.ESTestCase;
import org.junit.Test;


public class BooleanTypeTest extends ESTestCase {

    @Test
    public void test_cast_text_to_boolean() {
        assertThat(BooleanType.INSTANCE.implicitCast("t"), is(true));
        assertThat(BooleanType.INSTANCE.implicitCast("false"), is(false));
        assertThat(BooleanType.INSTANCE.implicitCast("FALSE"), is(false));
        assertThat(BooleanType.INSTANCE.implicitCast("f"), is(false));
        assertThat(BooleanType.INSTANCE.implicitCast("F"), is(false));
        assertThat(BooleanType.INSTANCE.implicitCast("true"), is(true));
        assertThat(BooleanType.INSTANCE.implicitCast("TRUE"), is(true));
        assertThat(BooleanType.INSTANCE.implicitCast("t"), is(true));
        assertThat(BooleanType.INSTANCE.implicitCast("T"), is(true));
    }

    @Test
    public void test_sanitize_boolean_value() {
        assertThat(BooleanType.INSTANCE.sanitizeValue(Boolean.FALSE), is(false));
    }

    @Test
    public void test_sanitize_numeric_value() {
        assertThat(BooleanType.INSTANCE.sanitizeValue(1), is(true));
    }

    @Test
    public void test_cast_unsupported_text_to_boolean_throws_exception() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Can't convert \"hello\" to boolean");
        BooleanType.INSTANCE.implicitCast("hello");
    }

    @Test
    public void test_cast_map_to_boolean_throws_exception() {
        expectedException.expect(ClassCastException.class);
        expectedException.expectMessage("Can't cast '{}' to boolean");
        BooleanType.INSTANCE.implicitCast(Map.of());
    }
}
