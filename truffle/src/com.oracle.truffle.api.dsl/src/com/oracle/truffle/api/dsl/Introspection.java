/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.dsl.Introspection.SpecializationInfo;
import com.oracle.truffle.api.nodes.Node;

/**
 * Contains introspection utilities for Truffle DSL. The contained utilities are only usable if the
 * operation node is annotated with {@link Introspectable}.
 * <p>
 * Introspection is useful for using testing the node declaration and verifying that particular
 * specializations become active.
 * <p>
 * Example for using introspection in unit testing:
 *
 * {@codesnippet com.oracle.truffle.api.dsl.IntrospectionSnippets.NegateNode}
 *
 * @since 0.22
 * @see Introspectable
 */
public final class Introspection {

    private static final List<List<Object>> EMPTY_CACHED = Collections.unmodifiableList(Arrays.asList(Collections.emptyList()));
    private static final List<List<Object>> NO_CACHED = Collections.emptyList();

    private final Object[] data;

    Introspection(Object[] data) {
        this.data = data;
    }

    /**
     * Returns <code>true</code> if the given node is introspectable. If something is introspectable
     * is determined by if the node is generated by Truffle DSL, if is annotated with
     * {@link Introspectable} and if the DSL implementation supports introspection.
     *
     * @param node a DSL generated node
     * @return true if the given node is introspectable
     * @since 0.22
     */
    public static boolean isIntrospectable(Node node) {
        return node instanceof Provider;
    }

    /**
     * Returns introspection information for the first specialization that matches a given method
     * name. A node must declare at least one specialization and must be annotated with
     * {@link Introspectable} otherwise an {@link IllegalArgumentException} is thrown. If multiple
     * specializations with the same method name are declared then an undefined specialization is
     * going to be returned. In such cases disambiguate them by renaming the specialzation method
     * name. The returned introspection information is not updated when the state of the given
     * operation node is updated. The implementation of this method might be slow, do not use it in
     * performance critical code.
     *
     * @param node a introspectable DSL operation with at least one specialization
     * @param methodName the Java method name of the specialization to introspect
     * @return introspection info for the method
     * @see Introspection example usage
     * @since 0.22
     */
    public static SpecializationInfo getSpecialization(Node node, String methodName) {
        return getIntrospectionData(node).getSpecialization(methodName);
    }

    /**
     * Returns introspection information for all declared specializations as unmodifiable list. A
     * given node must declare at least one specialization and must be annotated with
     * {@link Introspectable} otherwise an {@link IllegalArgumentException} is thrown. The returned
     * introspection information is not updated when the state of the given operation node is
     * updated. The implementation of this method might be slow, do not use it in performance
     * critical code.
     *
     * @param node a introspectable DSL operation with at least one specialization
     * @see Introspection example usage
     * @since 0.22
     */
    public static List<SpecializationInfo> getSpecializations(Node node) {
        return getIntrospectionData(node).getSpecializations();
    }

    private static Introspection getIntrospectionData(Node node) {
        if (!(node instanceof Provider)) {
            throw new IllegalArgumentException(String.format("Provided node is not introspectable. Annotate with @%s to make a node introspectable.", Introspectable.class.getSimpleName()));
        }
        return ((Provider) node).getIntrospectionData();
    }

    /**
     * Represents dynamic introspection information of a specialization of a DSL operation.
     *
     * @since 0.22
     */
    public static final class SpecializationInfo {

        private final String methodName;
        private final byte state; /* 0b000000<excluded><active> */
        private final List<List<Object>> cachedData;

        SpecializationInfo(String methodName, byte state, List<List<Object>> cachedData) {
            this.methodName = methodName;
            this.state = state;
            this.cachedData = cachedData;
        }

        /**
         * Returns the method name of the introspected specialization. Please note that the returned
         * method name might not be unique for a given node.
         *
         * @since 0.22
         */
        public String getMethodName() {
            return methodName;
        }

        /**
         * Returns <code>true</code> if the specialization was active at the time when the
         * introspection was performed.
         *
         * @since 0.22
         */
        public boolean isActive() {
            return (state & 0b1) != 0;
        }

        /**
         * Returns <code>true</code> if the specialization was excluded at the time when the
         * introspection was performed.
         *
         * @since 0.22
         */
        public boolean isExcluded() {
            return (state & 0b10) != 0;
        }

        /**
         * Returns the number of dynamic specialization instances that are active for this
         * specialization.
         *
         * @since 0.22
         */
        public int getInstances() {
            return cachedData.size();
        }

        /**
         * Returns the cached state for a given specialization instance. The provided instance index
         * must be greater or equal <code>0</code> and smaller {@link #getInstances()}. The returned
         * list is unmodifiable and never <code>null</code>.
         *
         * @since 0.22
         */
        public List<Object> getCachedData(int instanceIndex) {
            if (instanceIndex < 0 || instanceIndex >= cachedData.size()) {
                throw new IllegalArgumentException("Invalid specialization index");
            }
            return cachedData.get(instanceIndex);
        }

        /**
         * {@inheritDoc}
         * 
         * @since 21.1
         */
        @Override
        public String toString() {
            return "SpecializationInfo[name=" + methodName + ", active=" + isActive() + ", excluded" + isExcluded() + ", instances=" + getInstances() + "]";
        }

    }

    /**
     * Internal marker interface for DSL generated code to access reflection information. A DSL user
     * must not refer to this type manually.
     *
     * @since 0.22
     */
    public interface Provider {

        /**
         * Returns internal reflection data in undefined format. A DSL user must not call this
         * method.
         *
         * @since 0.22
         */
        Introspection getIntrospectionData();

        /**
         * Factory method to create {@link Node} introspection data. The factory is used to create
         * {@link Introspection} data to be returned from the {@link #getIntrospectionData()}
         * method. The format of the <code>data</code> parameters is internal, thus this method
         * shall only be used by the nodes generated by the DSL processor. A DSL user must not call
         * this method.
         *
         * @param data introspection data in an internal format
         * @return wrapped data to be used by
         *         {@link Introspection#getSpecializations(com.oracle.truffle.api.nodes.Node)} and
         *         similar methods
         * @since 0.22
         */
        static Introspection create(Object... data) {
            return new Introspection(data);
        }
    }

    SpecializationInfo getSpecialization(String methodName) {
        checkVersion();
        for (int i = 1; i < data.length; i++) {
            Object[] fieldData = getIntrospectionData(data[i]);
            if (methodName.equals(fieldData[0])) {
                return createSpecialization(fieldData);
            }
        }
        return null;
    }

    List<SpecializationInfo> getSpecializations() {
        checkVersion();
        List<SpecializationInfo> specializations = new ArrayList<>();
        for (int i = 1; i < data.length; i++) {
            specializations.add(createSpecialization(getIntrospectionData(data[i])));
        }
        return Collections.unmodifiableList(specializations);
    }

    private void checkVersion() {
        int version = -1;
        if (data.length > 0 && data[0] instanceof Integer) {
            Object objectVersion = data[0];
            version = (int) objectVersion;
        }
        if (version != 0) {
            throw new IllegalStateException("Unsupported introspection data version: " + version);
        }
    }

    private static Object[] getIntrospectionData(Object specializationData) {
        if (!(specializationData instanceof Object[])) {
            throw new IllegalStateException("Invalid introspection data.");
        }
        Object[] fieldData = (Object[]) specializationData;
        if (fieldData.length < 3 || !(fieldData[0] instanceof String) //
                        || !(fieldData[1] instanceof Byte) //
                        || (fieldData[2] != null && !(fieldData[2] instanceof List))) {
            throw new IllegalStateException("Invalid introspection data.");
        }
        return fieldData;
    }

    @SuppressWarnings("unchecked")
    private static SpecializationInfo createSpecialization(Object[] fieldData) {
        String id = (String) fieldData[0];
        byte state = (byte) fieldData[1];
        List<List<Object>> cachedData = (List<List<Object>>) fieldData[2];
        if (cachedData == null || cachedData.isEmpty()) {
            if ((state & 0b01) != 0) {
                cachedData = EMPTY_CACHED;
            } else {
                cachedData = NO_CACHED;
            }
        } else {
            for (int i = 0; i < cachedData.size(); i++) {
                cachedData.set(i, Collections.unmodifiableList(cachedData.get(i)));
            }
        }
        return new SpecializationInfo(id, state, cachedData);
    }

}

@SuppressWarnings({"null", "unused"})
@SuppressFBWarnings("")
class IntrospectionSnippets {

    // BEGIN: com.oracle.truffle.api.dsl.IntrospectionSnippets.NegateNode
    @Introspectable
    abstract static class NegateNode extends Node {

        abstract Object execute(Object o);

        @Specialization(guards = "cachedvalue == value", limit = "1")
        protected static int doInt(int value,
                        @Cached("value") int cachedvalue) {
            return -cachedvalue;
        }

        @Specialization(replaces = "doInt")
        protected static int doGeneric(int value) {
            return -value;
        }
    }

    public void testUsingIntrospection() {
        NegateNode node = null; // NegateNodeGen.create();
        SpecializationInfo info;

        node.execute(1);
        info = Introspection.getSpecialization(node, "doInt");
        assert info.getInstances() == 1;

        node.execute(1);
        info = Introspection.getSpecialization(node, "doInt");
        assert info.getInstances() == 1;

        node.execute(2);
        info = Introspection.getSpecialization(node, "doInt");
        assert info.getInstances() == 0;

        info = Introspection.getSpecialization(node, "doGeneric");
        assert info.getInstances() == 1;
    }
    // END: com.oracle.truffle.api.dsl.IntrospectionSnippets.NegateNode
}
