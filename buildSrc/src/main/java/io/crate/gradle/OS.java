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

package io.crate.gradle;

public enum OS {
    WINDOWS,
    MAC,
    LINUX;

    @SuppressWarnings("unused")
    public static OS current() {
        String os = System.getProperty("os.name", "");
        if (os.startsWith("Windows")) {
            return OS.WINDOWS;
        }
        if (os.startsWith("Linux") || os.startsWith("LINUX")) {
            return OS.LINUX;
        }
        if (os.startsWith("Mac")) {
            return OS.MAC;
        }
        throw new IllegalStateException("Can't determine OS from: " + os);
    }
}
