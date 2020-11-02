/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.jfr;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(value = jdk.jfr.internal.EventWriter.class, onlyWith = JfrEnabled.class)
public final class Target_jdk_jfr_internal_EventWriter {
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = "startPosition", declClass = Target_jdk_jfr_internal_EventWriter.class) //
    static long startPositionOffset;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = "startPositionAddress", declClass = Target_jdk_jfr_internal_EventWriter.class) //
    static long startPositionAddressOffset;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = "currentPosition", declClass = Target_jdk_jfr_internal_EventWriter.class) //
    static long currentPositionOffset;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = "valid", declClass = Target_jdk_jfr_internal_EventWriter.class) //
    static long validOffset;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = "maxPosition", declClass = Target_jdk_jfr_internal_EventWriter.class) //
    static long maxPositionOffset;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, name = "notified", declClass = Target_jdk_jfr_internal_EventWriter.class) //
    static long notifiedOffset;

    @Alias
    @SuppressWarnings("unused")
    Target_jdk_jfr_internal_EventWriter(long startPos, long maxPos, long startPosAddress, long threadID, boolean valid) {
    }
}
