/*
 * Copyright 2018 Next Century Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.ncc.aif.util;

import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.atlas.lib.SetUtils;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.RDF;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * RIOT (https://jena.apache.org/documentation/io/) writer for Jena to produce
 * ordered model. The model is produced in the following order:
 * 1. Named (uri) nodes clustered with reified statements where node is subject
 * 2. Non-nested blank nodes
 * 3. Dangling reified statements (subject not in model)
 * 4. Duplicate blank or reified nodes
 * 5. Multi-nested blank nodes, ordered by label
 * 6. Nodes that don't fit into any of these categories (hopefully, not often)
 */
public class AIFOrderedTurtleWriter extends AbstractTurtleWriter {
    /**
     * Read an AIF RDF file and write it out in ordered TURTLE
     */
    public static void main(String[] args) throws IOException {
        Model model = ModelFactory.createDefaultModel();

        RDFDataMgr.read(model, "T101/T101_Q001_H001.partially.ttl");

        new AIFOrderedTurtleWriter().write(Files.newOutputStream(Paths.get("ordered.ttl")), model);
    }

    @Override
    protected void output(IndentedWriter iOut, Graph graph, PrefixMap prefixMap, String baseURI, Context context) {
        new OrderedWriter(iOut, prefixMap, baseURI, context).write(graph);
    }

    /**
     * Inner class to write ordered AIF TURTLE
     */
    protected static class OrderedWriter extends OrderedTurtleWriter$ {
        OrderedWriter(IndentedWriter out, PrefixMap pmap, String baseURI, Context context) {
            super(out, pmap, baseURI, context);
        }

        @Override
        protected void writeGraphTTL(Graph graph) {
            new OrderedGraph(graph, null, null).writeGraph();
        }

        /**
         * Inner-Inner class to write single graph. Houses state variables for graph while it's written
         */
        private class OrderedGraph extends BaseGraphWriter {

            // sort order by predicate and object
            private Comparator<Node> predicateSort = Comparator.comparing(node ->
                    graph.find(node, RDF.predicate.asNode(), Node.ANY).next().getObject() +
                            AIFComparableNode.getComparableString(graph,
                                    graph.find(node, RDF.object.asNode(), Node.ANY).next().getObject())
            );
            private Comparator<Node> subjectSort = Comparator.comparing(node ->
                    graph.find(node, RDF.subject.asNode(), Node.ANY).next().getObject().getURI() +
                            graph.find(node, RDF.predicate.asNode(), Node.ANY).next().getObject().getURI()
            );

            private OrderedGraph(Graph graph, Node graphName, DatasetGraph dsg) {
                super(graph, graphName, dsg);
            }

            private void writeGraph() {
                SortedSet<AIFComparableNode> normalSubjects = new TreeSet<>();
                SortedSet<AIFComparableNode> blankSubjects = new TreeSet<>();

                Set<AIFComparableNode> reified = new HashSet<>();
                List<AIFComparableNode> duplicates = new ArrayList<>();

                Set<Node> blankMultiNested = new HashSet<>();

                // mark each distinct subject as normal, blank, reified, duplicate, or multi-nested
                listSubjects().forEachRemaining(subject -> {

                    Set<AIFComparableNode> toAdd = null;
                    if (graph.contains(subject, RDF.type.asNode(), RDF.Statement.asNode())) {
                        // reified can either be blank or named
                        toAdd = reified;

                    } else if (freeBnodes.contains(subject)) {
                        // only add blank node if it's top level (not nested)
                        toAdd = blankSubjects;

                    } else if (subject.isURI()) {
                        toAdd = normalSubjects;

                    } else if (subject.isBlank() && !nestedObjects.contains(subject)) {
                        // capture nested nodes that are referenced multiple times
                        blankMultiNested.add(subject);
                    }

                    if (toAdd != null) {
                        AIFComparableNode beingAdded = new AIFComparableNode(graph, subject);
                        if (toAdd.contains(beingAdded)) {
                            duplicates.add(beingAdded);
                        } else {
                            toAdd.add(beingAdded);
                        }
                    }
                });

                Map<Node, Set<AIFComparableNode>> assertions = new HashMap<>();
                for (AIFComparableNode assertion : reified) {
                    graph.find(assertion.getNode(), RDF.subject.asNode(), Node.ANY)
                            .mapWith(Triple::getObject)
                            .forEachRemaining(subject ->
                                    assertions.computeIfAbsent(subject, key -> new HashSet<>()).add(assertion)
                            );
                }

                boolean somethingWritten = false;

                // write ordered subjects followed by reified statements
                for (AIFComparableNode normal : normalSubjects) {
                    Node subject = normal.getNode();
                    somethingWritten = writeSingleSubject(subject, somethingWritten);
                    if (assertions.containsKey(subject)) {
                        somethingWritten = writeBySubject(getAssertionIterator(assertions.get(subject), predicateSort),
                                somethingWritten);
                        assertions.remove(subject);
                    }
                }

                // write ordered blank nodes
                somethingWritten = writeBySubject(getNodeIterator(blankSubjects), somethingWritten);

                // write dangling reified statements
                if (!assertions.isEmpty()) {
                    writeHeader("Assertions without subjects. Probably incomplete graph", somethingWritten);
                    Set<AIFComparableNode> extraAssertions = new HashSet<>();
                    assertions.values().forEach(extraAssertions::addAll);
                    somethingWritten = writeBySubject(getAssertionIterator(extraAssertions, subjectSort),
                            somethingWritten);
                }

                // write any duplicate blank or reified nodes
                if (!duplicates.isEmpty()) {
                    writeHeader("Duplicates: The equivalent of these nodes is already represented above",
                            somethingWritten);
                    // natural sort
                    duplicates.sort(null);
                    somethingWritten = writeBySubject(getNodeIterator(duplicates), somethingWritten);
                }

                // write multi-nested blank nodes ordered by label. Label assigned when node first referenced/written
                if (!blankMultiNested.isEmpty()) {
                    writeHeader("Blanks: These blank nodes are nested in more than one statement above",
                            somethingWritten);
                    somethingWritten = writeBySubject(blankMultiNested.stream().sorted(labelSort).iterator(),
                            somethingWritten);
                }

                // write anything else we missed. Hopefully, happens infrequently
                if (!nLinkedLists.isEmpty() || !freeLists.isEmpty() ||
                        !SetUtils.difference(nestedObjects, nestedObjectsWritten).isEmpty()) {
                    writeHeader("Error: the following was unexpected", somethingWritten);
                    writeRemainder(somethingWritten);
                }
            }

            @Override
            protected Map<Node, List<Node>> groupByPredicates(Collection<Triple> cluster) {
                Map<Node, List<Node>> ret = super.groupByPredicates(cluster);
                for (List<Node> list : ret.values()) {
                    list.sort(Comparator.comparing(node -> new AIFComparableNode(graph, node)));
                }
                return ret;
            }

            private Iterator<Node> getAssertionIterator(Set<AIFComparableNode> collection, Comparator<Node> comp) {
                return collection.stream().map(AIFComparableNode::getNode).sorted(comp).iterator();
            }

            private Iterator<Node> getNodeIterator(Collection<AIFComparableNode> collection) {
                return collection.stream().map(AIFComparableNode::getNode).iterator();
            }
        }

    }
}
