/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.substitutions;

import java.lang.ref.PhantomReference;
import java.lang.ref.PublicFinalReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Wraps host Reference(s).
 */
public interface EspressoReference<T> {
    @Host(Reference/* <T> */.class)
    StaticObject getGuestReference();

    T get();

    void clear();
}

final class EspressoWeakReference extends WeakReference<StaticObject> implements EspressoReference<StaticObject> {

    private final StaticObject guestReference;

    EspressoWeakReference(@Host(WeakReference.class) StaticObject guestReference,
                    @Host(ObjectKlass.class) StaticObject referent, ReferenceQueue<StaticObject> queue) {
        super(referent, queue);
        this.guestReference = guestReference;
    }

    @Override
    public StaticObject getGuestReference() {
        return guestReference;
    }
}

final class EspressoSoftReference extends SoftReference<StaticObject> implements EspressoReference<StaticObject> {

    private final StaticObject guestReference;

    EspressoSoftReference(@Host(SoftReference.class) StaticObject guestReference,
                    @Host(Object.class) StaticObject referent, ReferenceQueue<StaticObject> queue) {
        super(referent, queue);
        this.guestReference = guestReference;
    }

    @Override
    public StaticObject getGuestReference() {
        return guestReference;
    }
}

final class EspressoPhantomReference extends PhantomReference<StaticObject> implements EspressoReference<StaticObject> {

    private final StaticObject guestReference;

    EspressoPhantomReference(@Host(PhantomReference.class) StaticObject guestReference,
                    @Host(Object.class) StaticObject referent, ReferenceQueue<StaticObject> queue) {
        super(referent, queue);
        this.guestReference = guestReference;
    }

    @Override
    public StaticObject getGuestReference() {
        return guestReference;
    }
}

/**
 * Usable version of {@link PublicFinalReference} is injected early via
 * {@link sun.misc.Unsafe#defineClass} on the the boot class loader.
 */
final class EspressoFinalReference extends PublicFinalReference<StaticObject> implements EspressoReference<StaticObject> {

    private final StaticObject guestReference;

    EspressoFinalReference(@Host(typeName = "Ljava/lang/ref/FinalReference;") StaticObject guestReference,
                    @Host(Object.class) StaticObject referent, ReferenceQueue<StaticObject> queue) {
        super(referent, queue);
        this.guestReference = guestReference;
    }

    @Override
    public StaticObject getGuestReference() {
        return guestReference;
    }
}
