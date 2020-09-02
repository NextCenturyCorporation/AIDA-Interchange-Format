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

 import org.apache.jena.graph.Graph;
 import org.apache.jena.graph.Node;
 import org.apache.jena.vocabulary.RDF;

 import javax.annotation.Nonnull;
 import java.util.ArrayList;
 import java.util.List;
 import java.util.Map;
 import java.util.TreeMap;

 /**
  * Comparable representation of Jena Node
  */
 public class AIFComparableNode implements Comparable<AIFComparableNode> {
     private Node node;
     private String rep;

     /**
      * CTOR
      */
     public AIFComparableNode(@Nonnull Graph graph, @Nonnull Node node) {
         this.node = node;
         rep = getComparableString(graph, node);
     }

     @Override
     public int compareTo(@Nonnull AIFComparableNode other) {
         return this.node == other.node ? 0 : this.rep.compareTo(other.rep);
     }

     /**
      * Return string representation for specified node within specified graph
      */
     public static String getComparableString(@Nonnull Graph graph, @Nonnull Node node) {
         return shouldUseComparable(graph, node) ? getComparableStringForBlankOrReified(graph, node) :
                 node.isLiteral() ? node.getLiteral().toString(false) : node.getURI();
     }

     private static String getComparableStringForBlankOrReified(Graph graph, Node node) {
         Map<String, List<Node>> predicatesToObjects = new TreeMap<>();

         graph.find(node, Node.ANY, Node.ANY).forEachRemaining(triple -> {
             String predicateString = getComparableString(graph, triple.getPredicate());
             predicatesToObjects.computeIfAbsent(predicateString, key -> new ArrayList<>()).add(triple.getObject());
         });

         StringBuilder predicateBuilder = new StringBuilder();
         StringBuilder objectBuilder = new StringBuilder();
         predicatesToObjects.forEach((property, objects) -> {
             predicateBuilder.append(property);

             objects.stream()
                     .map(object -> getComparableString(graph, object))
                     .sorted()
                     .forEachOrdered(objectBuilder::append);
         });
         return predicateBuilder.toString() + objectBuilder.toString();
     }

     private static boolean shouldUseComparable(Graph graph, Node node) {
         return node.isBlank() || graph.contains(node, RDF.type.asNode(), RDF.Statement.asNode());
     }

     /**
      * Return the representation used in comparison
      */
     public String getComparisonRepresentation() {
         return rep;
     }

     /**
      * Return the node that this class represents
      */
     public Node getNode() {
         return node;
     }

     @Override
     public int hashCode() {
         return rep == null ? 0 : rep.hashCode();
     }

     @Override
     public boolean equals(Object obj) {
         if (obj == null || !AIFComparableNode.class.isAssignableFrom(obj.getClass())) {
             return false;
         }

         final AIFComparableNode other = (AIFComparableNode) obj;

         // if not same nullness of members, false
         if ((node == null) != (other.node == null) || (rep == null) != (other.rep == null)) {
             return false;
         }

         if (node != null && rep != null) {
             return node == other.node || rep.equals(other.rep);
         } else if (rep != null) {
             return rep.equals(other.rep);
         } else if (node != null) {
             return node == other.node;
         } else {
             // everything is null, thus equal
             return true;
         }
     }
 }
