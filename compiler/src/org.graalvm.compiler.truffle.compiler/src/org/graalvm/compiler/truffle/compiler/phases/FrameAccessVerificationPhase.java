/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.truffle.compiler.phases;

import static org.graalvm.compiler.truffle.compiler.nodes.frame.NewFrameNode.INITIAL_TYPE_MARKER;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.graph.MergeableState;
import org.graalvm.compiler.phases.graph.PostOrderNodeIterator;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.compiler.PerformanceInformationHandler;
import org.graalvm.compiler.truffle.compiler.nodes.frame.NewFrameNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameAccessType;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameAccessorNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameClearNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameCopyNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameSetNode;
import org.graalvm.compiler.truffle.compiler.nodes.frame.VirtualFrameSwapNode;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

/**
 * This phase performs a pass over the control flow graph and checks whether the frame slot tags
 * match at all merges. If they do not match, then a deoptimization is inserted that invalidates the
 * frame intrinsic speculation.
 *
 * For indexed slots, this analysis can also handle cases where a predecessor of a merge is the
 * initial, unmodified state of the frame slot. In this case, the frame will be initialized properly
 * to allow the merge to succeed.
 */
public final class FrameAccessVerificationPhase extends BasePhase<CoreProviders> {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final CompilableTruffleAST compilable;

    // Cached tag nodes that have already reported a performance warning.
    private final EconomicSet<Node> reported = EconomicSet.create();

    private final EconomicMap<NewFrameNode, HashMap<AbstractEndNode, Integer>> deoptEnds = EconomicMap.create();

    public FrameAccessVerificationPhase(CompilableTruffleAST compilable) {
        this.compilable = compilable;
    }

    @SuppressWarnings("try")
    private void logPerformanceWarningClearIntroducedPhi(Node location, int index) {
        if (PerformanceInformationHandler.isWarningEnabled(PolyglotCompilerOptions.PerformanceWarningKind.FRAME_INCOMPATIBLE_MERGE)) {
            if (reported.contains(location)) {
                return;
            }
            Graph graph = location.graph();
            DebugContext debug = location.getDebug();
            try (DebugContext.Scope s = debug.scope("TrufflePerformanceWarnings", graph)) {
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("location", location);
                properties.put("method", compilable.getName());
                properties.put("index", index);
                PerformanceInformationHandler.logPerformanceWarning(PolyglotCompilerOptions.PerformanceWarningKind.FRAME_INCOMPATIBLE_MERGE, compilable,
                                Collections.emptyList(),
                                "Incompatible frame slot types at merge: this disables the frame intrinsics optimization and potentially causes frames to be materialized. " +
                                                "Ensure that frame slots are cleared before a control flow merge if they don't contain the same type of value.",
                                properties);
                debug.dump(DebugContext.VERBOSE_LEVEL, graph, "perf warn: Incompatible frame slot types for slot %d at %s", index, location);
                reported.add(location);
            } catch (Throwable t) {
                debug.handle(t);
            }
        }
    }

    public void insertDeopts() {
        MapCursor<NewFrameNode, HashMap<AbstractEndNode, Integer>> iterator = deoptEnds.getEntries();
        while (iterator.advance()) {
            NewFrameNode frame = iterator.getKey();
            for (Map.Entry<AbstractEndNode, Integer> entry : iterator.getValue().entrySet()) {
                AbstractEndNode node = entry.getKey();
                if (node.isAlive()) {
                    logPerformanceWarningClearIntroducedPhi(node.predecessor(), entry.getValue());
                    FixedWithNextNode predecessor = (FixedWithNextNode) node.predecessor();
                    predecessor.setNext(null);
                    GraphUtil.killCFG(node);
                    if (predecessor.isAlive()) {
                        Speculation speculation = node.graph().getSpeculationLog().speculate(frame.getIntrinsifyAccessorsSpeculation());
                        DeoptimizeNode deopt = new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.RuntimeConstraint, speculation);
                        predecessor.setNext(node.graph().add(deopt));
                    }
                }
            }
        }
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (graph.getNodes(NewFrameNode.TYPE).isNotEmpty()) {
            new Iterator(graph.start(), new State()).apply();
            insertDeopts();
        }
    }

    private final class State extends MergeableState<State> implements Cloneable {

        private final HashMap<NewFrameNode, byte[]> states = new HashMap<>();
        private final HashMap<NewFrameNode, byte[]> indexedStates = new HashMap<>();

        @Override
        public State clone() {
            State newState = new State();
            copy(states, newState.states);
            copy(indexedStates, newState.indexedStates);
            return newState;
        }

        private void copy(HashMap<NewFrameNode, byte[]> from, HashMap<NewFrameNode, byte[]> to) {
            for (Map.Entry<NewFrameNode, byte[]> entry : from.entrySet()) {
                to.put(entry.getKey(), entry.getValue().clone());
            }
        }

        @Override
        public boolean merge(AbstractMergeNode merge, List<State> withStates) {
            // determine the set of frames that are alive after this merge
            HashSet<NewFrameNode> frames = new HashSet<>(states.keySet());
            for (State other : withStates) {
                frames.retainAll(other.states.keySet());
            }

            for (NewFrameNode frame : frames) {
                byte[] entries = states.get(frame);
                byte[] indexedEntries = indexedStates.get(frame);
                byte[] frameEntries = frame.getFrameSlotKinds();
                byte[] frameIndexedEntries = frame.getIndexedFrameSlotKinds();
                int state = 1;
                for (State other : withStates) {
                    mergeEntries(merge, frame, entries, other.states.get(frame), frameEntries, state);
                    mergeEntries(merge, frame, indexedEntries, other.indexedStates.get(frame), frameIndexedEntries, state);
                    state++;
                }
            }

            states.keySet().retainAll(frames);
            indexedStates.keySet().retainAll(frames);

            return true;
        }

        /*
         * Compares "entries" and "otherEntries", taking into account that entries might be
         * uninitialized (in which case it is taken from the frame itself).
         */
        private void mergeEntries(AbstractMergeNode merge, NewFrameNode frame, byte[] entries, byte[] otherEntries, byte[] frameEntries, int state) {
            for (int i = 0; i < entries.length; i++) {
                if (entries[i] != otherEntries[i]) {
                    if (entries[i] == INITIAL_TYPE_MARKER) {
                        if (frameEntries[i] == INITIAL_TYPE_MARKER) {
                            frameEntries[i] = otherEntries[i];
                            continue;
                        } else if (frameEntries[i] == otherEntries[i]) {
                            continue;
                        }
                    } else if (otherEntries[i] == INITIAL_TYPE_MARKER) {
                        if (frameEntries[i] == INITIAL_TYPE_MARKER) {
                            frameEntries[i] = entries[i];
                            continue;
                        } else if (frameEntries[i] == entries[i]) {
                            continue;
                        }
                    }
                    deoptAt(merge, frame, state, i);
                    break;
                }
            }
        }

        private void deoptAt(AbstractMergeNode merge, NewFrameNode frame, int state, int index) {
            HashMap<AbstractEndNode, Integer> set = deoptEnds.get(frame);
            if (set == null) {
                deoptEnds.put(frame, set = new HashMap<>());
            }
            set.put(merge.phiPredecessorAt(state), index);
        }

        @Override
        public void loopEnds(LoopBeginNode loopBegin, List<State> loopEndStates) {
            merge(loopBegin, loopEndStates);
        }

        public void add(NewFrameNode frame) {
            assert !states.containsKey(frame) && !indexedStates.containsKey(frame);
            byte[] entries = frame.getFrameSize() == 0 ? EMPTY_BYTE_ARRAY : frame.getFrameSlotKinds().clone();
            states.put(frame, entries);
            byte[] indexedEntries = frame.getIndexedFrameSize() == 0 ? EMPTY_BYTE_ARRAY : frame.getIndexedFrameSlotKinds().clone();
            indexedStates.put(frame, indexedEntries);
        }

        public byte[] get(VirtualFrameAccessorNode accessor) {
            boolean isLegacy = accessor.getType() == VirtualFrameAccessType.Legacy;
            HashMap<NewFrameNode, byte[]> map = isLegacy ? states : indexedStates;
            return map.get(accessor.getFrame());
        }
    }

    private static final class Iterator extends PostOrderNodeIterator<State> {

        Iterator(FixedNode start, State initialState) {
            super(start, initialState);
        }

        private static boolean inRange(byte[] array, int index) {
            return index >= 0 && index < array.length;
        }

        @Override
        protected void node(FixedNode node) {
            if (node instanceof NewFrameNode) {
                state.add((NewFrameNode) node);
            } else if (node instanceof VirtualFrameAccessorNode) {
                VirtualFrameAccessorNode accessor = (VirtualFrameAccessorNode) node;
                VirtualFrameAccessType type = accessor.getType();
                if (type != VirtualFrameAccessType.Auxiliary) {
                    /*
                     * Ignore operations with invalid indexes - these will be handled during PEA.
                     */
                    if (node instanceof VirtualFrameSetNode) {
                        byte[] entries = state.get(accessor);
                        if (inRange(entries, accessor.getFrameSlotIndex()) && accessor.getAccessTag() != NewFrameNode.FrameSlotKindObjectTag) {
                            entries[accessor.getFrameSlotIndex()] = (byte) accessor.getAccessTag();
                        }
                    } else if (node instanceof VirtualFrameClearNode) {
                        byte[] entries = state.get(accessor);
                        if (inRange(entries, accessor.getFrameSlotIndex())) {
                            entries[accessor.getFrameSlotIndex()] = NewFrameNode.FrameSlotKindLongTag;
                        }
                    } else if (node instanceof VirtualFrameCopyNode) {
                        VirtualFrameCopyNode copy = (VirtualFrameCopyNode) node;
                        byte[] entries = state.get(accessor);
                        if (inRange(entries, copy.getTargetSlotIndex()) && inRange(entries, copy.getFrameSlotIndex())) {
                            entries[copy.getTargetSlotIndex()] = entries[copy.getFrameSlotIndex()];
                        }
                    } else if (node instanceof VirtualFrameSwapNode) {
                        VirtualFrameSwapNode swap = (VirtualFrameSwapNode) node;
                        byte[] entries = state.get(accessor);
                        byte temp = entries[swap.getTargetSlotIndex()];
                        if (inRange(entries, swap.getTargetSlotIndex()) && inRange(entries, swap.getFrameSlotIndex())) {
                            entries[swap.getTargetSlotIndex()] = entries[swap.getFrameSlotIndex()];
                            entries[swap.getFrameSlotIndex()] = temp;
                        }
                    }
                }
            }
        }
    }
}
