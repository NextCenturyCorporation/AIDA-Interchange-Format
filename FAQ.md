# Frequently Asked Questions

### How are conjunction and conjunctions in hypotheses represented?

Currently everything in the sub-graph associated with a hypothesis is understood to be
asserted as part of the hypothesis.  For example:

```
<http://www.test.edu/hypotheses/1>
        a                       aida:Hypothesis ;
        aida:hypothesisContent  [ a                      aida:Subgraph ;
                                  aida:subgraphContains  <http://www.test.edu/relations/1> ;
                                  aida:subgraphContains  <http://www.test.edu/relations/2> ;
                                  aida:subgraphContains  <http://www.test.edu/relations/3>
                                ] ;
        aida:system             <http://www.test.edu/testSystem> .
```

asserts the hypothesis that relations 1, 2, and 3 are all true.

An example of constructing two distinct hypotheses is given
in the `twoSeedlingHypotheses` test in `src/test/java/com/ncc/aif/ExamplesAndValidationTest.java`.

Currently a hypothesis cannot express disjunctions (e.g. "relation 1 holds true and either
relation 2 or relation 3") since we assume that such cases should expressed as two (or more)
distinct hypotheses (e.g. one hypothesis for "relation 1 and relation 2" and another for
"relation 1 and relation 3").  However, if expressing such disjunctions is desired, it can be
added.