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

### How do I migrate from the GAIA/Kotlin version of AIF to the Java version?

First, if you haven't already, you'll need to change your build script or tool to import
`aida-interchange-1.0.5.jar` (changed from `gaia-interchange-kotlin-1.0-SNAPSHOT.jar`).
For gradle, for example, include the following in the dependencies in your `build.gradle` file:

`dependencies {
    compile 'com.ncc:aida-interchange:1.0.5'
}`

Next, update any source files that import the `edu.isi.gaia` package to use the `com.ncc.aif` package instead.
For example, change:
```
import edu.isi.gaia.AIFUtils;
```
to
```
import com.ncc.aif.AIFUtils;
```
You may also wish to clean out any vestiges of the Kotlin version (class files, libraries) from build
tool caches (e.g., the `edu/isi/gaia-interchange-kotlin/` directory in Maven's repository).
